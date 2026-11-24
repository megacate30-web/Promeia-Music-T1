package com.promeia.music.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.*
import androidx.room.migration.Migration
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.io.File
import kotlinx.coroutines.sync.withLock

@Entity data class Song(@PrimaryKey val uri: String, val title: String, val artist: String, val album: String, val folder: String, val duration: Long, val size: Long, val added: Long, val name: String, val genre: String = "", val year: String = "", val track: String = "", val disc: String = "", val albumArtist: String = "", val composer: String = "")
@Entity data class Edit(@PrimaryKey val uri: String, val title: String? = null, val artist: String? = null, val album: String? = null, val genre: String? = null, val year: String? = null, val track: String? = null, val disc: String? = null, val albumArtist: String? = null, val composer: String? = null, val cover: String? = null, val favorite: Boolean = false, val hidden: Boolean = false)
@Entity data class Playlist(@PrimaryKey val id: String, val name: String, val description: String = "", val cover: String? = null, val favorite: Boolean = false, val songs: List<String> = emptyList())
@Entity data class Listen(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uri: String, val at: Long, val millis: Long, val start: Boolean)
class Converters { @TypeConverter fun encode(value: List<String>): String = com.google.gson.Gson().toJson(value); @TypeConverter fun decode(value: String): List<String> = com.google.gson.Gson().fromJson(value, Array<String>::class.java).toList() }
@Dao interface LibraryDao {
    @Query("SELECT * FROM Song") fun songs(): Flow<List<Song>>
    @Query("SELECT * FROM Song") suspend fun allSongs(): List<Song>
    @Query("SELECT * FROM Edit") fun edits(): Flow<List<Edit>>
    @Query("SELECT * FROM Edit") suspend fun allEdits(): List<Edit>
    @Query("SELECT * FROM Playlist") fun playlists(): Flow<List<Playlist>>
    @Query("SELECT * FROM Playlist") suspend fun allPlaylists(): List<Playlist>
    @Query("SELECT * FROM Listen ORDER BY at DESC") fun listens(): Flow<List<Listen>>
    @Query("SELECT * FROM Listen ORDER BY at DESC") suspend fun allListens(): List<Listen>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSongs(songs: List<Song>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEdit(edit: Edit)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlaylist(playlist: Playlist)
    @Insert suspend fun listen(event: Listen)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putListens(events: List<Listen>)
    @Query("DELETE FROM Song WHERE uri = :uri") suspend fun removeSong(uri: String)
    @Query("DELETE FROM Edit WHERE uri = :uri") suspend fun removeEdit(uri:String)
    @Query("UPDATE Listen SET uri = :newUri WHERE uri = :oldUri") suspend fun remapListens(oldUri:String,newUri:String)
    @Query("DELETE FROM Listen WHERE uri = :uri") suspend fun removeListens(uri:String)
    @Delete suspend fun removePlaylist(playlist: Playlist)
}
@Database(entities = [Song::class, Edit::class, Playlist::class, Listen::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class MusicDb : RoomDatabase() {
    abstract fun dao(): LibraryDao
    companion object { @Volatile private var instance: MusicDb? = null
        private val manualLibraryMigration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // V1 did not distinguish explicitly selected MediaStore URIs from automatic scans.
                db.execSQL("DELETE FROM Edit WHERE uri LIKE 'content://media/%'")
                db.execSQL("DELETE FROM Listen WHERE uri LIKE 'content://media/%'")
                db.execSQL("DELETE FROM Song WHERE uri LIKE 'content://media/%'")
            }
        }
        fun get(c: Context): MusicDb = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(c.applicationContext, MusicDb::class.java, "promeia.db").addMigrations(manualLibraryMigration).build().also { instance = it } }
    }
}
fun Song.resolved(e: Edit?): Song = if(e == null) this else copy(title=e.title?:title, artist=e.artist?:artist, album=e.album?:album, genre=e.genre?:genre, year=e.year?:year, track=e.track?:track, disc=e.disc?:disc, albumArtist=e.albumArtist?:albumArtist, composer=e.composer?:composer)
fun normalized(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
fun matchSong(title: String, artist: String, songs: List<Song>): Song? = songs.filter { normalized(it.title)==normalized(title) && (artist.isBlank() || normalized(it.artist)==normalized(artist)) }.singleOrNull()
fun formatTime(ms: Long): String { val seconds = ms.coerceAtLeast(0)/1000; return "%d:%02d".format(Locale.ROOT, seconds/60, seconds%60) }
object LibraryScanner {
    private val migrationLock=Mutex()
    val formats = setOf("mp3","flac","wav","aac","m4a","ogg","opus","wma","aiff","amr")
    suspend fun importFile(context: Context, uri: Uri, folder: String = "Importados"): Song? = withContext(Dispatchers.IO) {
        MusicStorage.lock.withLock {
            val dao=MusicDb.get(context).dao()
            val doc=DocumentFile.fromSingleUri(context,uri); val name=doc?.name?:uri.lastPathSegment?:"Audio"
            val mime=context.contentResolver.getType(uri).orEmpty()
            val ext=name.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf{it in formats} ?: when(mime){"audio/mpeg"->"mp3";"audio/mp4","audio/x-m4a"->"m4a";"audio/flac"->"flac";"audio/wav","audio/x-wav"->"wav";"audio/aac"->"aac";"audio/ogg"->"ogg";"audio/opus"->"opus";else->return@withLock null}
            val dir=MusicStorage.folder(context,"Audio")
            val tmp=File.createTempFile("import-",".part",dir)
            var created:File?=null
            try {
                val input=context.contentResolver.openInputStream(uri)?:error("No se pudo abrir $name")
                val hash=MusicStorage.copyAudio(input,tmp)
                val existing=dir.listFiles()?.firstOrNull{it.isFile && it.name.startsWith("$hash.")}
                if(existing!=null && dao.allSongs().any{it.uri==MusicStorage.uri(context,existing).toString()})return@withLock null
                val r=MediaMetadataRetriever()
                var picture:ByteArray?=null
                val values=try { r.setDataSource(tmp.absolutePath)
                    if(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)!="yes" || r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes")return@withLock null
                    picture=r.embeddedPicture
                    listOf(MediaMetadataRetriever.METADATA_KEY_TITLE,MediaMetadataRetriever.METADATA_KEY_ARTIST,MediaMetadataRetriever.METADATA_KEY_ALBUM,MediaMetadataRetriever.METADATA_KEY_DURATION,MediaMetadataRetriever.METADATA_KEY_GENRE,MediaMetadataRetriever.METADATA_KEY_YEAR,MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,MediaMetadataRetriever.METADATA_KEY_COMPOSER).map{r.extractMetadata(it).orEmpty()}
                }finally{r.release()}
                val duration=values[3].toLongOrNull()?:0L;if(duration<=0)return@withLock null
                val final=existing?:File(dir,"$hash.$ext").also{check(tmp.renameTo(it)){"No se pudo guardar el audio"};created=it}
                val id=MusicStorage.uri(context,final).toString()
                val song=Song(id,values[0].ifBlank{name.substringBeforeLast('.')},values[1].ifBlank{"Desconocido"},values[2].ifBlank{"Sin álbum"},folder,duration,final.length(),System.currentTimeMillis(),name,values[4],values[5],values[6],values[7],values[8],values[9])
                picture?.takeIf{it.size<=8*1024*1024}?.let{MusicStorage.atomic(MusicStorage.embeddedFile(context,id),it)}
                MusicStorage.writeSong(context,song,null)
                dao.putSongs(listOf(song));created=null;song
            }catch(e:Exception){created?.let{MusicStorage.removeSidecars(context,MusicStorage.uri(context,it).toString());it.delete()};if(e is CancellationException)throw e;throw e}finally{tmp.delete()}
        }
    }
    // Copies only previously registered explicit imports/downloads, never scans the phone.
    suspend fun migrateLegacy(context:Context) = withContext(Dispatchers.IO) {
        migrationLock.withLock {
            val db=MusicDb.get(context);val dao=db.dao();val prefs=context.getSharedPreferences("storage",0)
            if(prefs.getBoolean("v5_migrated",false))return@withLock
            val mapping=mutableMapOf<String,String>()
            for(old in dao.allSongs().filter{!MusicStorage.owned(context,Uri.parse(it.uri))}) {
                try {
                    val copied=importFile(context,Uri.parse(old.uri),old.folder) ?: continue
                    val edit=dao.allEdits().find{it.uri==old.uri}
                    val oldCover=edit?.cover?.takeUnless{it=="-"}?.let{Uri.parse(it).path}?.let{File(it)}
                    val newCover=oldCover?.takeIf{it.isFile}?.let{f->File(MusicStorage.folder(context,"Covers"),"${MusicStorage.key(old.uri)}-custom.img").also{f.copyTo(it,overwrite=true)}}
                    val oldLyrics=File(File(context.filesDir,"lyrics"),"${MusicStorage.key(old.uri)}.lrc")
                    if(oldLyrics.isFile)oldLyrics.copyTo(File(MusicStorage.folder(context,"Lyrics"),"${MusicStorage.key(copied.uri)}.lrc"),overwrite=true)
                    val oldMeta=copied.copy(added=old.added,name=old.name)
                    val newEdit=edit?.copy(uri=copied.uri,cover=newCover?.let{Uri.fromFile(it).toString()}?:edit.cover)
                    db.withTransaction {
                        dao.putSongs(listOf(oldMeta));if(newEdit!=null)dao.putEdit(newEdit)
                        dao.allPlaylists().filter{old.uri in it.songs}.forEach{p->dao.putPlaylist(p.copy(songs=p.songs.map{if(it==old.uri)copied.uri else it}.distinct()))}
                        dao.remapListens(old.uri,copied.uri);dao.removeEdit(old.uri);dao.removeSong(old.uri)
                    }
                    mapping[old.uri]=copied.uri;MusicStorage.writeSong(context,oldMeta,newEdit)
                }catch(e:Exception){if(e is CancellationException)throw e;android.util.Log.w("Promeia","No se pudo migrar un audio anterior",e)}
            }
            // Keep unavailable legacy records untouched for manual removal; no broad permission is requested.
            val valid=dao.allSongs().map{it.uri}.toSet()
            dao.allPlaylists().forEach{p->val ids=p.songs.filter{it in valid};if(ids!=p.songs)dao.putPlaylist(p.copy(songs=ids))}
            val playback=context.getSharedPreferences("playback",0);runCatching{val queue=org.json.JSONArray(playback.getString("queue","[]"));for(i in 0 until queue.length()){val o=queue.getJSONObject(i);mapping[o.optString("id")]?.let{o.put("id",it)}};playback.edit().putString("queue",queue.toString()).apply()}
            prefs.edit().putBoolean("v5_migrated",true).apply()
        }
    }
    suspend fun importTree(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var count=0
        val root=DocumentFile.fromTreeUri(context,uri) ?: return@withContext 0
        suspend fun visit(dir:DocumentFile, depth:Int) {
            if(depth>20) return
            for(file in dir.listFiles()) {
                currentCoroutineContext().ensureActive()
                if(file.isDirectory) visit(file,depth+1)
                else try { if(importFile(context,file.uri,root.name?:"Carpeta importada")!=null) count++ } catch(e:Exception) { if(e is CancellationException) throw e }
            }
        }
        visit(root,0)
        count
    }
}
// Keep the class for any jobs enqueued by older versions. It never reads storage.
class ScanWorker(c:Context,p:WorkerParameters): CoroutineWorker(c,p) {
    override suspend fun doWork(): Result = Result.success()
    companion object { fun cancel(c:Context) { WorkManager.getInstance(c).cancelUniqueWork("library-scan") } }
}
