package com.promeia.music.discovery

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Html
import androidx.core.content.FileProvider
import com.promeia.music.data.MusicDb
import com.promeia.music.data.Song
import com.promeia.music.data.LibraryScanner
import com.promeia.music.data.MusicStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class VideoHit(val id:String,val title:String,val channel:String,val image:String)
object DownloadRules {
    fun allowed(url:URL):Boolean {
        val h=url.host.lowercase(Locale.ROOT).trimEnd('.')
        val blocked=listOf("youtube.com","youtu.be","googlevideo.com","youtube-nocookie.com")
        return url.protocol=="https" && h.isNotBlank() && url.userInfo==null && (url.port==-1||url.port==443) && blocked.none{h==it||h.endsWith(".$it")}
    }
    fun extension(mime:String,path:String):String? {
        val types=mapOf("audio/mpeg" to "mp3","audio/mp3" to "mp3","audio/flac" to "flac","audio/x-flac" to "flac","audio/wav" to "wav","audio/x-wav" to "wav","audio/wave" to "wav","audio/vnd.wave" to "wav","audio/aac" to "aac","audio/mp4" to "m4a","audio/x-m4a" to "m4a","audio/ogg" to "ogg","audio/opus" to "opus","application/ogg" to "ogg","audio/x-ms-wma" to "wma","audio/x-aiff" to "aiff")
        val m=mime.substringBefore(';').trim().lowercase(Locale.ROOT)
        if(m in types)return types[m]
        if(m!="application/octet-stream" && m.isNotBlank())return null
        return path.substringAfterLast('.',"").lowercase(Locale.ROOT).takeIf{it in setOf("mp3","flac","wav","aac","m4a","ogg","opus","wma","aiff")}
    }
    fun key(url:String)=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
}
object DownloadCenter {
    private const val maxBytes=250L*1024*1024
    private fun connect(initial:String,permit:(URL)->Boolean):HttpURLConnection {
        var url=URL(initial)
        repeat(6){
            require(permit(url)){"Enlace no compatible. Usa HTTPS y un archivo de audio directo autorizado, no un video de YouTube."}
            val c=url.openConnection() as HttpURLConnection
            c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=false;c.setRequestProperty("User-Agent","PromeiaMusic/0.4.0")
            try { val code=c.responseCode;if(code==200)return c
                if(code in listOf(301,302,303,307,308))url=URL(url,c.getHeaderField("Location")?:error("Redirección inválida")) else error("Servidor: HTTP $code")
            }catch(e:Exception){c.disconnect();throw e};c.disconnect()
        };error("Demasiadas redirecciones")
    }
    suspend fun search(key:String,query:String):List<VideoHit> = withContext(Dispatchers.IO){
        require(key.isNotBlank()&&query.isNotBlank()){ "Introduce la clave de YouTube Data API y una búsqueda." }
        val url=Uri.parse("https://www.googleapis.com/youtube/v3/search").buildUpon().appendQueryParameter("part","snippet").appendQueryParameter("type","video").appendQueryParameter("maxResults","20").appendQueryParameter("q",query).appendQueryParameter("key",key).build().toString()
        val c=connect(url){it.protocol=="https"&&it.host=="www.googleapis.com"}
        try{
            val out=java.io.ByteArrayOutputStream();c.inputStream.use{input->val b=ByteArray(8192);while(true){currentCoroutineContext().ensureActive();val n=input.read(b);if(n<0)break;require(out.size()+n<2*1024*1024){"Respuesta demasiado grande"};out.write(b,0,n)}}
            val items=JSONObject(out.toString("UTF-8")).getJSONArray("items")
            (0 until items.length()).mapNotNull{i->val item=items.getJSONObject(i);val id=item.optJSONObject("id")?.optString("videoId").orEmpty();val s=item.optJSONObject("snippet")?:return@mapNotNull null;if(!id.matches(Regex("[A-Za-z0-9_-]{11}")))null else VideoHit(id,Html.fromHtml(s.optString("title"),Html.FROM_HTML_MODE_LEGACY).toString(),Html.fromHtml(s.optString("channelTitle"),Html.FROM_HTML_MODE_LEGACY).toString(),s.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty())}
        }finally{c.disconnect()}
    }
    suspend fun download(context:Context,link:String,progress:suspend(Float?)->Unit):Song=withContext(Dispatchers.IO){
        val url=link.trim();require(DownloadRules.allowed(URL(url))){"No es un enlace HTTPS directo de audio compatible."}
        val dir=File(context.cacheDir,"incoming").apply{mkdirs()};val id=DownloadRules.key(url);val dao=MusicDb.get(context).dao()
        val prefs=context.getSharedPreferences("discovery",0)
        prefs.getString("source:$id",null)?.let{old->dao.allSongs().firstOrNull{it.uri==old}?.let{return@withContext it}}
        val tmp=File.createTempFile("audio-",".part",dir)
        try{
            val conn=connect(url,DownloadRules::allowed)
            val ext=try{
                val e=DownloadRules.extension(conn.contentType.orEmpty(),conn.url.path)?:error("El enlace no devuelve un formato de audio compatible. No uses una página web de reproducción.")
                val total=conn.contentLengthLong;require(total<=maxBytes){"El archivo supera 250 MB"};var bytes=0L;var last=0L;progress(if(total>0)0f else null)
                conn.inputStream.use{input->tmp.outputStream().buffered().use{out->val b=ByteArray(32768);while(true){currentCoroutineContext().ensureActive();val n=input.read(b);if(n<0)break;bytes+=n;require(bytes<=maxBytes){"El archivo supera 250 MB"};out.write(b,0,n);if(bytes-last>=262144){last=bytes;progress(if(total>0)(bytes.toFloat()/total).coerceIn(0f,1f) else null)}}}}
                require(bytes>0&&(total<=0||bytes==total)){"Descarga incompleta"};e
            }finally{conn.disconnect()}
            val final=File(dir,"${id}.$ext")
            require(tmp.renameTo(final)){"No se pudo preparar el audio"}
            try {
                val source=FileProvider.getUriForFile(context,"${context.packageName}.files",final)
                val song=LibraryScanner.importFile(context,source,"Descargas Promeia") ?: error("El audio ya está en la biblioteca o no es válido")
                prefs.edit().putString("source:$id",song.uri).apply();progress(1f);song
            } finally { final.delete() }
        }finally{tmp.delete()}
    }
}
