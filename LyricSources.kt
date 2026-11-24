package com.promeia.music.lyrics

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.promeia.music.data.Song
import com.promeia.music.data.MusicStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object LyricSources {
    private const val LIMIT = 512 * 1024
    private fun readLimited(stream: InputStream): String = stream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) { val n = input.read(buf); if(n < 0) break; require(out.size()+n <= LIMIT) { "Archivo de letras demasiado grande." }; out.write(buf,0,n) }
        out.toString("UTF-8")
    }
    private fun local(c: Context, id: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return AtomicFile(File(MusicStorage.folder(c,"Lyrics"), "$hash.lrc"))
    }
    suspend fun load(c: Context, id: String): LyricSheet? = withContext(Dispatchers.IO) {
        val file = local(c,id)
        try { LyricParser.parse(readLimited(file.openRead())).takeIf { it.lines.isNotEmpty() } } catch (_: java.io.FileNotFoundException) { null }
    }
    suspend fun importFile(c: Context,id: String,uri: Uri): LyricSheet = withContext(Dispatchers.IO) {
        val raw = readLimited(c.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo."))
        val sheet = LyricParser.parse(raw)
        require(sheet.lines.isNotEmpty()) { "El archivo no contiene letras válidas." }
        val file = local(c,id); val stream = file.startWrite()
        try { stream.write(raw.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) } catch(e: Exception) { file.failWrite(stream); throw e }
        sheet
    }
    suspend fun remove(c: Context,id: String) = withContext(Dispatchers.IO) { local(c,id).delete() }
    /** Optional remote lookup. No audio sent; remote lyrics are not persisted. */
    suspend fun online(song: Song): LyricSheet? = withContext(Dispatchers.IO) {
        val uri = Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name",song.title).appendQueryParameter("artist_name",song.artist)
            .appendQueryParameter("album_name",song.album)
        if(song.duration > 0) uri.appendQueryParameter("duration",((song.duration+500)/1000).toString())
        val connection = URL(uri.build().toString()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=10000; connection.readTimeout=10000; connection.instanceFollowRedirects=false
            connection.setRequestProperty("User-Agent","PromeiaMusic/0.3.0 (Android)")
            connection.setRequestProperty("Accept","application/json")
            when(val status=connection.responseCode) {
                404 -> null
                200 -> {
                    val data=JSONObject(readLimited(connection.inputStream))
                    if(data.optBoolean("instrumental")) LyricSheet(emptyList(),true) else {
                        val synced=if(data.isNull("syncedLyrics")) "" else data.optString("syncedLyrics")
                        val plain=if(data.isNull("plainLyrics")) "" else data.optString("plainLyrics")
                        val sheet=LyricParser.parse(synced)
                        (if(sheet.synced) sheet else LyricParser.parse(plain.ifBlank { synced })).takeIf { it.lines.isNotEmpty() }
                    }
                }
                else -> error("Proveedor de letras no disponible (HTTP $status).")
            }
        } finally { connection.disconnect() }
    }
}
