package com.promeia.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.media.audiofx.Equalizer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.promeia.music.MainActivity
import com.promeia.music.data.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

fun Song.item(edit: Edit? = null): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).apply { edit?.cover?.takeUnless { it=="-" }?.let { setArtworkUri(Uri.parse(it)) } }.build()).build()
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val handler=Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("playback",MODE_PRIVATE) }
    private var counted: String?=null
    private var equalizer: Equalizer?=null
    private var eqKey=""
    private var lastTick=0L
    private val tick=object:Runnable { override fun run() {
        val now=android.os.SystemClock.elapsedRealtime(); val delta=(now-lastTick).coerceIn(0,5500); lastTick=now
        val id=player.currentMediaItem?.mediaId
        if(player.isPlaying && id!=null) { val first=counted!=id; counted=id; scope.launch { MusicDb.get(this@PlaybackService).dao().listen(Listen(uri=id,at=System.currentTimeMillis(),millis=delta,start=first)) } }
        val deadline=prefs.getLong("sleep",0); if(deadline>0 && System.currentTimeMillis()>=deadline) { player.pause(); prefs.edit().remove("sleep").apply() }
        applyEq(); save(); handler.postDelayed(this,5000)
    } }
    override fun onCreate() { super.onCreate()
        player=ExoPlayer.Builder(this).build().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true); setHandleAudioBecomingNoisy(true) }
        val savedQueue=prefs.getString("queue","[]") ?: "[]"
        val savedIndex=prefs.getInt("index",0)
        val savedPosition=prefs.getLong("position",0)
        val savedRepeat=prefs.getInt("repeat",0)
        val savedShuffle=prefs.getBoolean("shuffle",false)
        scope.launch {
            val allowed=runCatching { MusicDb.get(this@PlaybackService).dao().allSongs().filter { MusicStorage.owned(this@PlaybackService,Uri.parse(it.uri)) }.map { it.uri }.toSet() }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { if(player.mediaItemCount==0) restore(savedQueue,savedIndex,savedPosition,savedRepeat,savedShuffle,allowed) }
        }
        player.addListener(object:Player.Listener {
            override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int) { counted=null; save() }
            override fun onIsPlayingChanged(isPlaying:Boolean) { save() }
            override fun onPlayerError(error:PlaybackException) { player.pause() }
        })
        val launch=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session=MediaSession.Builder(this,player).setSessionActivity(launch).setCallback(object: MediaSession.Callback {
            override fun onAddMediaItems(mediaSession:MediaSession, controller:MediaSession.ControllerInfo, mediaItems:List<MediaItem>):ListenableFuture<List<MediaItem>> {
                if(controller.packageName != packageName) return Futures.immediateFuture(emptyList())
                val items=mediaItems.mapNotNull { m -> val u=Uri.parse(m.mediaId); if(!MusicStorage.owned(this@PlaybackService,u)) null else m.buildUpon().setUri(u).build() }
                return Futures.immediateFuture(items)
            }
        }).build()
        lastTick=android.os.SystemClock.elapsedRealtime(); handler.postDelayed(tick,5000)
    }
    private fun applyEq() {
        val settings=getSharedPreferences("settings",MODE_PRIVATE)
        val values=settings.getString("eqBands","0,0,0,0,0").orEmpty()
        val key="${player.audioSessionId}:${settings.getString("eqEnabled","No")}:$values"
        if(eqKey==key || player.audioSessionId==C.AUDIO_SESSION_ID_UNSET) return
        eqKey=key
        runCatching { equalizer?.release(); equalizer=null; if(settings.getString("eqEnabled","No")=="Sí") {
            val eq=Equalizer(0,player.audioSessionId); equalizer=eq
            val bands=values.split(',').map { it.toIntOrNull()?:0 }; val range=eq.bandLevelRange
            for(i in 0 until eq.numberOfBands.toInt()) { val value=bands.getOrElse(i*bands.size/eq.numberOfBands.toInt()){0}*100; eq.setBandLevel(i.toShort(),value.coerceIn(range[0].toInt(),range[1].toInt()).toShort()) }; eq.enabled=true
        } }
    }
    private fun save() { if(player.mediaItemCount==0) { prefs.edit().remove("queue").apply(); return }; val arr=JSONArray(); for(i in 0 until player.mediaItemCount) { val item=player.getMediaItemAt(i); val m=item.mediaMetadata; arr.put(JSONObject().put("id",item.mediaId).put("title",m.title?.toString()).put("artist",m.artist?.toString()).put("album",m.albumTitle?.toString()).put("cover",m.artworkUri?.toString())) }; prefs.edit().putString("queue",arr.toString()).putInt("index",player.currentMediaItemIndex).putLong("position",player.currentPosition).putInt("repeat",player.repeatMode).putBoolean("shuffle",player.shuffleModeEnabled).apply() }
    private fun restore(json:String,index:Int,position:Long,repeat:Int,shuffle:Boolean,allowed:Set<String>) { runCatching {
        val arr=JSONArray(json)
        val current=arr.optJSONObject(index)?.optString("id")
        val items=(0 until arr.length()).mapNotNull { i ->
            val o=arr.getJSONObject(i); val id=o.getString("id")
            if(id !in allowed) return@mapNotNull null
            MediaItem.Builder().setMediaId(id).setUri(id).setMediaMetadata(MediaMetadata.Builder().setTitle(o.optString("title")).setArtist(o.optString("artist")).setAlbumTitle(o.optString("album")).apply { if(!o.isNull("cover")) setArtworkUri(Uri.parse(o.getString("cover"))) }.build()).build()
        }
        if(items.isNotEmpty()) { val at=items.indexOfFirst { it.mediaId==current }; player.setMediaItems(items,at.coerceAtLeast(0),if(at>=0)position.coerceAtLeast(0) else 0L); player.repeatMode=repeat; player.shuffleModeEnabled=shuffle; player.prepare() } else prefs.edit().remove("queue").apply()
    } }
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession?=session
    override fun onDestroy() { handler.removeCallbacks(tick); save(); equalizer?.release(); session?.release(); player.release(); scope.cancel(); super.onDestroy() }
}
