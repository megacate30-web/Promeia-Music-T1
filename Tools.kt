package com.promeia.music.data

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.UUID

data class CoverResult(val name:String,val artist:String,val url:String,val source:String)
data class TransferTrack(val title:String,val artist:String="",val uri:String="")
data class MatchReport(val matched:List<Song>,val missing:List<TransferTrack>)
fun matchTracks(tracks:List<TransferTrack>, songs:List<Song>):MatchReport { val found=mutableListOf<Song>(); val missing=mutableListOf<TransferTrack>(); tracks.forEach { t -> (songs.firstOrNull { t.uri.isNotBlank() && it.uri==t.uri } ?: matchSong(t.title,t.artist,songs))?.let { found.add(it) } ?: missing.add(t) }; return MatchReport(found,missing) }
object Tools {
    private fun fetch(url:String):ByteArray { val parsed=URL(url); require(parsed.protocol=="https"); val c=parsed.openConnection() as HttpURLConnection; c.connectTimeout=10000; c.readTimeout=15000; c.instanceFollowRedirects=false; c.setRequestProperty("User-Agent","PromeiaMusic/0.1 (personal music library)"); try { require(c.responseCode==200) { "HTTP ${c.responseCode}" }; return c.inputStream.use { input -> val output=java.io.ByteArrayOutputStream(); val buf=ByteArray(8192); var total=0; while(true) { val n=input.read(buf); if(n<0) break; total+=n; require(total<=8*1024*1024) { "Archivo demasiado grande" }; output.write(buf,0,n) }; output.toByteArray() } } finally { c.disconnect() } }
    suspend fun searchCover(query:String):List<CoverResult> = withContext(Dispatchers.IO) {
        val encoded=URLEncoder.encode(query,"UTF-8"); val arr=JSONObject(String(fetch("https://itunes.apple.com/search?entity=album&limit=20&term=$encoded"),Charsets.UTF_8)).getJSONArray("results")
        (0 until arr.length()).mapNotNull { i -> val o=arr.getJSONObject(i); val u=o.optString("artworkUrl100"); if(u.startsWith("https://")) CoverResult(o.optString("collectionName"),o.optString("artistName"),u.replace("100x100bb","600x600bb"),o.optString("collectionViewUrl")) else null }
    }
    private fun writeCover(c:Context,bytes:ByteArray):String { require(bytes.size<=8*1024*1024); val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts); require(opts.outWidth in 1..10000 && opts.outHeight in 1..10000) { "Imagen inválida" }; val dir=MusicStorage.folder(c,"Covers"); val f=File(dir,"${UUID.randomUUID()}.img"); f.writeBytes(bytes); return Uri.fromFile(f).toString() }
    suspend fun downloadCover(c:Context,url:String):String=withContext(Dispatchers.IO) { writeCover(c,fetch(url)) }
    suspend fun importCover(c:Context,uri:Uri):String=withContext(Dispatchers.IO) { val bytes=c.contentResolver.openInputStream(uri)?.use { val out=java.io.ByteArrayOutputStream(); val buf=ByteArray(8192); while(true) { val n=it.read(buf); if(n<0) break; require(out.size()+n<=8*1024*1024); out.write(buf,0,n) }; out.toByteArray() } ?: error("No se pudo leer imagen"); writeCover(c,bytes) }
    suspend fun embedded(c:Context,s:Song):ByteArray?=withContext(Dispatchers.IO) { runCatching { val saved=MusicStorage.embeddedFile(c,s.uri); if(saved.isFile)return@runCatching saved.readBytes(); val r=MediaMetadataRetriever(); try { r.setDataSource(c,Uri.parse(s.uri)); r.embeddedPicture } finally { r.release() } }.getOrNull() }
    suspend fun exportMusicFolder(c:Context,destination:Uri)=withContext(Dispatchers.IO){
        val dao=MusicDb.get(c).dao();val songs=dao.allSongs();val edits=dao.allEdits().associateBy{it.uri};songs.forEach{MusicStorage.writeSong(c,it,edits[it.uri])}
        val root=MusicStorage.root(c);val data=File(MusicStorage.folder(c,"Metadata"),"library-backup.json");MusicStorage.atomic(data,backup(c).toByteArray(Charsets.UTF_8))
        c.contentResolver.openOutputStream(destination)?.use{stream->java.util.zip.ZipOutputStream(stream.buffered()).use{zip->root.walkTopDown().filter{it.isFile&&!it.name.endsWith(".part")&&!it.name.endsWith(".bak")&&!it.name.endsWith(".new")}.forEach{file->kotlinx.coroutines.currentCoroutineContext().ensureActive();zip.putNextEntry(java.util.zip.ZipEntry("Promeia Music/Music/"+file.relativeTo(root).invariantSeparatorsPath));file.inputStream().use{it.copyTo(zip)};zip.closeEntry()}}}?:error("No se pudo abrir destino")
    }
    fun exportPlaylist(p:Playlist,songs:List<Song>):String = "#EXTM3U\n"+p.songs.mapNotNull { id -> songs.find { it.uri==id } }.joinToString("\n") { "#EXTINF:${it.duration/1000},${it.artist} - ${it.title}\n${it.uri}" }
    fun parsePlaylist(text:String):List<TransferTrack> { var pending:TransferTrack?=null; val out=mutableListOf<TransferTrack>(); text.lineSequence().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() }.forEach { line -> when { line.startsWith("#EXTINF:") -> { val name=line.substringAfter(','); pending=TransferTrack(name.substringAfter(" - "),name.substringBefore(" - ","")) }; line.startsWith("#") -> Unit; pending!=null -> { out+=pending!!.copy(uri=line); pending=null }; line.contains('\t') -> { val p=line.split('\t'); if(!p[0].equals("title",true)) out+=TransferTrack(p[0],p.getOrElse(1){""}) }; else -> out+=TransferTrack(line.substringAfterLast('/').substringBeforeLast('.'),uri=line) } }; return out }
    data class Backup(val edits:List<Edit>,val playlists:List<Playlist>,val listens:List<Listen>,val covers:Map<String,String>,val settings:Map<String,String>)
    suspend fun backup(c:Context):String=withContext(Dispatchers.IO) { val d=MusicDb.get(c).dao(); val e=d.allEdits(); val p=d.allPlaylists(); val covers=(e.mapNotNull { it.cover }+p.mapNotNull { it.cover }).distinct().mapNotNull { u -> runCatching { val f=File(Uri.parse(u).path!!); if(f.canonicalFile.parentFile!=MusicStorage.folder(c,"Covers").canonicalFile && f.canonicalFile.parentFile!=File(c.filesDir,"covers").canonicalFile) null else u to android.util.Base64.encodeToString(f.readBytes(),android.util.Base64.NO_WRAP) }.getOrNull() }.toMap(); val prefs=c.getSharedPreferences("settings",0); Gson().toJson(Backup(e,p,d.allListens(),covers,prefs.all.mapValues { it.value.toString() })) }
    suspend fun restore(c:Context,json:String)=withContext(Dispatchers.IO) { val b=Gson().fromJson(json,Backup::class.java); val coverMap=b.covers.mapValues { writeCover(c,android.util.Base64.decode(it.value,android.util.Base64.DEFAULT)) }; val db=MusicDb.get(c); val d=db.dao(); db.withTransaction { b.edits.forEach { d.putEdit(it.copy(cover=coverMap[it.cover]?:it.cover?.takeIf { v->v=="-" })) }; b.playlists.forEach { d.putPlaylist(it.copy(cover=coverMap[it.cover])) }; d.putListens(b.listens) }; val prefs=c.getSharedPreferences("settings",0).edit(); b.settings.forEach { (k,v)->prefs.putString(k,v) }; prefs.apply() }
}
