package com.promeia.music.lyrics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.promeia.music.data.Song
import com.promeia.music.data.formatTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val languages=linkedMapOf("auto" to "Detectar automáticamente","en" to "Inglés","es" to "Español","fr" to "Francés","pt" to "Portugués","it" to "Italiano","de" to "Alemán","ja" to "Japonés","ko" to "Coreano","zh" to "Chino","ru" to "Ruso","ar" to "Árabe","hi" to "Hindi")

@Composable fun LyricsScreen(song: Song, position: Long, duration: Long, playing: Boolean, seek: (Long)->Unit, toggle: ()->Unit, previous: ()->Unit, next: ()->Unit, close: ()->Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("lyrics_settings",0) }
    var autoOnline by remember { mutableStateOf(prefs.getBoolean("online",false)) }
    var showTranslation by remember { mutableStateOf(prefs.getBoolean("translate",false)) }
    var target by remember { mutableStateOf(prefs.getString("target","es") ?: "es") }
    // Source language resets for each track; target preference remains.
    key(song.uri) {
        val scope=rememberCoroutineScope()
        var sheet by remember { mutableStateOf<LyricSheet?>(null) }
        var source by remember { mutableStateOf("auto") }
        var origin by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(true) }
        var status by remember { mutableStateOf("Buscando letra local…") }
        var translationStatus by remember { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }
        var offset by remember { mutableLongStateOf(prefs.getLong("offset:${song.uri}",0)) }
        var follow by remember { mutableStateOf(true) }
        var lookupJob by remember { mutableStateOf<Job?>(null) }
        val translations=remember { mutableStateMapOf<Int,String>() }
        val listState=rememberLazyListState()
        fun load(online: Boolean, preferLocal: Boolean = true) {
            lookupJob?.cancel()
            lookupJob=scope.launch {
                busy=true; status="Buscando letra…"
                try {
                    val local=if(preferLocal) LyricSources.load(context,song.uri) else null
                    val result=local ?: if(online) LyricSources.online(song) else null
                    sheet=result; origin=if(local!=null) "Archivo local" else if(result!=null) "LRCLIB · fuente externa" else ""
                    status=when { result?.instrumental==true -> "Pista instrumental"; result!=null -> ""; online -> "No se encontró una letra para esta grabación."; else -> "Importa un archivo o activa la búsqueda en línea." }
                } catch(e:CancellationException) { throw e } catch(e:Exception) { status=e.message ?: "No se pudo obtener la letra." }
                finally { busy=false }
            }
        }
        val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if(uri!=null) {
                lookupJob?.cancel()
                lookupJob=scope.launch {
                    busy=true
                    try { sheet=LyricSources.importFile(context,song.uri,uri); origin="Archivo local"; status=""; revision++ }
                    catch(e:CancellationException) { throw e } catch(e:Exception) { status=e.message ?: "No se pudo importar." }
                    finally { busy=false }
                }
            }
        }
        LaunchedEffect(autoOnline) { load(autoOnline) }
        val currentSheet=sheet
        val active=currentSheet?.let { LyricParser.active(it,position,offset) } ?: -1
        LaunchedEffect(active,follow,currentSheet) { if(follow && active>=0) listState.animateScrollToItem((active-1).coerceAtLeast(0)) }
        LaunchedEffect(currentSheet,source,target,showTranslation,revision) {
            translations.clear(); translationStatus=""
            if(showTranslation && currentSheet!=null && currentSheet.lines.isNotEmpty()) {
                try { LyricTranslation.translate(currentSheet,source,target,{ translationStatus=it },{ i,t -> translations[i]=t },active) }
                catch(e:CancellationException) { throw e } catch(e:Exception) { translationStatus=e.message ?: "No se pudo traducir. Revisa el idioma o la conexión." }
            }
        }
        Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick=close){Icon(Icons.Default.Close,"Cerrar letras")}
                        Column(Modifier.weight(1f)) { Text("Promeia Live Lyrics",style=MaterialTheme.typography.titleLarge); Text(song.title,style=MaterialTheme.typography.titleMedium,maxLines=1); Text(song.artist,style=MaterialTheme.typography.bodySmall,maxLines=1) }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Switch(checked=autoOnline,onCheckedChange={ autoOnline=it; prefs.edit().putBoolean("online",it).apply() })
                        Text(" Búsqueda automática en LRCLIB",style=MaterialTheme.typography.bodySmall)
                    }
                    Text("Al activarla, se envían título, artista, álbum y duración al proveedor; no el audio.",style=MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedButton(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text("Importar LRC/TXT")}
                        TextButton(enabled=!busy,onClick={load(autoOnline)}){Text("Actualizar")}
                        if(origin=="Archivo local") TextButton(enabled=!busy,onClick={lookupJob?.cancel(); lookupJob=scope.launch { LyricSources.remove(context,song.uri); sheet=null; origin=""; load(autoOnline) }}){Text("Quitar")}
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Switch(checked=showTranslation,onCheckedChange={showTranslation=it; prefs.edit().putBoolean("translate",it).apply()})
                        Text(" Traducción automática",style=MaterialTheme.typography.bodyMedium)
                    }
                    if(showTranslation) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            LanguageMenu("Origen",source,languages,Modifier.weight(1f)){source=it}
                            LanguageMenu("Destino",target,languages.filterKeys{it!="auto"},Modifier.weight(1f)){target=it;prefs.edit().putString("target",it).apply()}
                        }
                        Text("Traducido por Google · traducción automática, puede contener errores.",style=MaterialTheme.typography.labelSmall)
                        if(translationStatus.isNotBlank()) Text(translationStatus,style=MaterialTheme.typography.labelSmall)
                        if(currentSheet?.lines?.isNotEmpty()==true) TextButton(onClick={revision++}){Text("Reintentar traducción")}
                    }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
                    if(currentSheet?.lines?.isNotEmpty()==true) {
                        Text("$origin · ${if(currentSheet.synced) "Sincronizada" else "Sin tiempos: lectura manual"}",style=MaterialTheme.typography.labelMedium)
                        if(currentSheet.synced) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
                                TextButton(onClick={offset=(offset-500).coerceAtLeast(-60000);prefs.edit().putLong("offset:${song.uri}",offset).apply()}){Text("−0,5 s")}
                                TextButton(onClick={offset=0;prefs.edit().putLong("offset:${song.uri}",0).apply()}){Text("${offset/1000.0}s · Reset")}
                                TextButton(onClick={offset=(offset+500).coerceAtMost(60000);prefs.edit().putLong("offset:${song.uri}",offset).apply()}){Text("+0,5 s")}
                                TextButton(onClick={follow=!follow}){Text(if(follow) "Auto ✓" else "Auto")}
                            }
                        }
                        LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(currentSheet.lines){i,line ->
                                val highlighted=i==active
                                Column(Modifier.fillMaxWidth().then(if(line.timeMs!=null) Modifier.clickable { seek((line.timeMs-offset).coerceAtLeast(0)) } else Modifier).padding(vertical=6.dp,horizontal=8.dp)) {
                                    Text(line.text.ifBlank { "♪" },style=if(highlighted)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,fontWeight=if(highlighted)FontWeight.Bold else FontWeight.Normal,color=if(highlighted)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(showTranslation) translations[i]?.takeIf{it.isNotBlank()&&it!=line.text}?.let{Text(it,style=MaterialTheme.typography.bodyLarge,color=if(highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)}
                                }
                            }
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Text(if(busy) "Cargando…" else if(currentSheet?.instrumental==true) "♪" else "No hay letra disponible.\nPuedes añadir un archivo de texto propio o autorizado.",textAlign=TextAlign.Center) }
                    Slider(value=if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f,onValueChange={seek((it*duration).toLong())},enabled=duration>0)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatTime(position));Text(formatTime(duration))}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){IconButton(onClick=previous){Icon(Icons.Default.SkipPrevious,"Anterior")};FilledIconButton(onClick=toggle){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproducir o pausar")};IconButton(onClick=next){Icon(Icons.Default.SkipNext,"Siguiente")}}
                }
            }
        }
    }
}
@Composable private fun LanguageMenu(label:String,value:String,options:Map<String,String>,modifier:Modifier=Modifier,onSelect:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text("$label: ${options[value]?:value}",maxLines=2)}
        DropdownMenu(expanded=open,onDismissRequest={open=false}) { options.forEach { (code,name)->DropdownMenuItem(text={Text(name)},onClick={open=false;onSelect(code)}) } }
    }
}
