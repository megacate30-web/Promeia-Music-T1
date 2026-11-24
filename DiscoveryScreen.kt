package com.promeia.music.discovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.promeia.music.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun DiscoveryScreen(songs:List<Song>,importAudio:()->Unit,play:(Song)->Unit) {
    val c=LocalContext.current;val scope=rememberCoroutineScope();val prefs=remember{c.getSharedPreferences("discovery",0)}
    var key by remember{mutableStateOf(prefs.getString("youtube_key","").orEmpty())}
    var query by remember{mutableStateOf("")};var hits by remember{mutableStateOf(emptyList<VideoHit>())}
    var link by remember{mutableStateOf("")};var permission by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var downloading by remember{mutableStateOf(false)}
    var progress by remember{mutableStateOf<Float?>(null)};var job by remember{mutableStateOf<Job?>(null)};var saved by remember{mutableStateOf<Song?>(null)}
    val occupied=busy||downloading
    fun open(url:String){runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{status="No hay aplicación para abrir este enlace"}}
    Column(verticalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Text("Promeia Download Center",style=MaterialTheme.typography.headlineMedium)
        Text("Busca en YouTube, encuentra música y agrega archivos de audio autorizados a tu biblioteca local.")
        Text("Búsqueda en YouTube",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(query,{query=it},label={Text("Canción o artista")},singleLine=true,enabled=!occupied,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(key,{key=it.trim();prefs.edit().putString("youtube_key",key).apply()},label={Text("Clave YouTube Data API (opcional)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!occupied,modifier=Modifier.fillMaxWidth())
        Text("Para mostrar resultados dentro de Promeia, activa YouTube Data API v3 en Google Cloud y configura tu clave. Sin clave, puedes abrir la búsqueda oficial.",style=MaterialTheme.typography.bodySmall)
        Button(enabled=!occupied&&key.isNotBlank()&&query.isNotBlank(),onClick={busy=true;status="Buscando en YouTube…";hits=emptyList();job=scope.launch{try{hits=DownloadCenter.search(key,query);status=if(hits.isEmpty())"Sin resultados" else "${hits.size} resultados de YouTube"}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Error de búsqueda"}finally{busy=false}}}){Text("Buscar dentro de Promeia")}
        TextButton(enabled=query.isNotBlank(),onClick={open(Uri.parse("https://www.youtube.com/results").buildUpon().appendQueryParameter("search_query",query).build().toString())}){Text("Buscar en YouTube sin clave")}
        hits.forEach{v->ElevatedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){AsyncImage(model=v.image,contentDescription=null,modifier=Modifier.width(100.dp).height(62.dp));Column(Modifier.weight(1f)){Text(v.title,style=MaterialTheme.typography.titleMedium);Text("YouTube · ${v.channel}",style=MaterialTheme.typography.bodySmall);TextButton(onClick={open("https://www.youtube.com/watch?v=${v.id}")}){Text("Ver en YouTube")}}}}}
        HorizontalDivider()
        Text("Descargar archivo de audio",style=MaterialTheme.typography.titleLarge)
        Text("Pega un enlace HTTPS directo a un archivo de audio que tengas permiso de descargar. No se convierten videos de YouTube ni páginas de streaming.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(link,{link=it;permission=false;saved=null},label={Text("Enlace directo al audio")},singleLine=true,enabled=!occupied,modifier=Modifier.fillMaxWidth())
        Row{Checkbox(permission,{permission=it},enabled=!occupied);Text("Tengo autorización para descargar este archivo",modifier=Modifier.padding(top=12.dp))}
        Button(enabled=!occupied&&permission&&link.isNotBlank(),onClick={downloading=true;progress=null;saved=null;status="Descargando…";job=scope.launch{try{val s=DownloadCenter.download(c,link){p->withContext(Dispatchers.Main){progress=p}};saved=s;status="Audio guardado: ${s.title}"}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Error de descarga"}finally{downloading=false;progress=null}}}){Text("Descargar a mi biblioteca")}
        if(occupied){if(progress==null)LinearProgressIndicator(Modifier.fillMaxWidth())else LinearProgressIndicator(progress={progress?:0f},modifier=Modifier.fillMaxWidth());TextButton(onClick={job?.cancel();status="Operación cancelada"}){Text("Cancelar")}}
        if(status.isNotBlank())Text(status,color=MaterialTheme.colorScheme.onSurfaceVariant)
        saved?.let{s->Button(onClick={play(s)}){Text("Reproducir ${s.title}")}}
        TextButton(enabled=!occupied,onClick=importAudio){Text("Importar archivos del celular")}
        Text("Formatos: MP3, FLAC, WAV, AAC, M4A, OGG, OPUS y otros según dispositivo. Límite: 250 MB. Mantén abierta esta pantalla; los archivos privados se conservan hasta desinstalar la app. Puedes copiarlos desde Biblioteca.",style=MaterialTheme.typography.bodySmall)
        val recent=songs.filter{it.folder.startsWith("Descargas Promeia")}.sortedByDescending{it.added}.take(5)
        if(recent.isNotEmpty())Text("Descargas recientes",style=MaterialTheme.typography.titleMedium)
        recent.forEach{s->TextButton(onClick={play(s)}){Text("${s.title} — ${s.artist}")}}
    }
}
