package com.promeia.music.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Private library; no global MediaStore scan or broad storage permissions. */
object MusicStorage {
    val lock = Mutex()
    fun root(c:Context)=File(c.filesDir,"Promeia Music/Music").apply { check(mkdirs() || isDirectory) }
    fun folder(c:Context,name:String)=File(root(c),name).apply { check(mkdirs() || isDirectory) }
    fun uri(c:Context,file:File)=FileProvider.getUriForFile(c,"${c.packageName}.files",file)
    fun owned(c:Context,uri:Uri)=uri.authority=="${c.packageName}.files" && uri.path?.startsWith("/music/Audio/")==true
    fun key(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
    fun atomic(file:File,bytes:ByteArray) { val a=AtomicFile(file);val out=a.startWrite();try{out.write(bytes);a.finishWrite(out)}catch(e:Exception){a.failWrite(out);throw e} }
    suspend fun copyAudio(input:InputStream,tmp:File):String { val digest=MessageDigest.getInstance("SHA-256");input.use{src->tmp.outputStream().buffered().use{out->val buffer=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=src.read(buffer);if(n<0)break;digest.update(buffer,0,n);out.write(buffer,0,n)}}};return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)} }
    fun embeddedFile(c:Context,id:String)=File(folder(c,"Covers"),"${key(id)}-original.img")
    fun writeSong(c:Context,s:Song,e:Edit?) { val file=File(folder(c,"Metadata"),"${key(s.uri)}.json"); val data=mapOf("original" to s,"current" to s.resolved(e),"edit" to e,"originalCover" to embeddedFile(c,s.uri).takeIf{it.isFile}?.name,"customCover" to e?.cover); val bytes=GsonBuilder().setPrettyPrinting().create().toJson(data).toByteArray(Charsets.UTF_8); if(!file.isFile||!file.readBytes().contentEquals(bytes))atomic(file,bytes) }
    fun removeSidecars(c:Context,id:String) { embeddedFile(c,id).delete();File(folder(c,"Metadata"),"${key(id)}.json").delete();File(folder(c,"Lyrics"),"${key(id)}.lrc").delete() }
}
