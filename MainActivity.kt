package com.promeia.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.promeia.music.data.ScanWorker
import com.promeia.music.data.LibraryScanner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import com.promeia.music.playback.PlaybackService
import com.promeia.music.ui.Promeia
import androidx.compose.runtime.mutableStateOf

class MainActivity:ComponentActivity() {
    private lateinit var ready:Deferred<Unit>
    private var connection:Job?=null
    private var future:ListenableFuture<MediaController>?=null
    private val controller=mutableStateOf<MediaController?>(null)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        ScanWorker.cancel(this)
        ready=lifecycleScope.async { LibraryScanner.migrateLegacy(this@MainActivity); Unit }
        val loaded=mutableStateOf(false)
        lifecycleScope.launch { try { ready.await() } finally { loaded.value=true } }
        setContent { if(loaded.value)Promeia(controller.value) else androidx.compose.material3.Text("Preparando tu biblioteca privada…") }
    }
    override fun onStart() { super.onStart(); connection=lifecycleScope.launch { ready.await(); if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))return@launch; val f=MediaController.Builder(this@MainActivity,SessionToken(this@MainActivity,ComponentName(this@MainActivity,PlaybackService::class.java))).buildAsync(); future=f; f.addListener({ if(future==f) controller.value=runCatching { f.get() }.getOrNull() },ContextCompat.getMainExecutor(this@MainActivity)) } }
    override fun onStop() { connection?.cancel(); connection=null; controller.value=null; future?.let { MediaController.releaseFuture(it) }; future=null; super.onStop() }
}
