package com.promeia.music.lyrics

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.waitFor(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if(cont.isActive) {
            if(task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: IllegalStateException("Operación de idioma cancelada."))
        }
    }
}
object LyricTranslation {
    suspend fun translate(sheet: LyricSheet, language: String, target: String, status: (String)->Unit, line: (Int,String)->Unit, priority: Int = 0) {
        val detected = if(language=="auto") {
            val identifier=LanguageIdentification.getClient()
            try { identifier.identifyLanguage(sheet.lines.joinToString("\n"){it.text}.take(4000)).waitFor() }
            finally { identifier.close() }
        } else language
        val source=TranslateLanguage.fromLanguageTag(detected) ?: error("Idioma de origen no reconocido o no compatible. Selecciónalo manualmente.")
        val dest=TranslateLanguage.fromLanguageTag(target) ?: error("Idioma de destino no compatible.")
        if(source==dest) { sheet.lines.forEachIndexed { i,l -> line(i,l.text) }; status("La letra ya está en el idioma seleccionado."); return }
        val translator=Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(dest).build())
        try {
            status("Preparando modelo de idioma…")
            translator.downloadModelIfNeeded().waitFor()
            val memo=mutableMapOf<String,String>()
            val start=priority.coerceIn(0,sheet.lines.lastIndex.coerceAtLeast(0))
            val order=(start..sheet.lines.lastIndex).toList()+(0 until start).toList()
            order.forEachIndexed { n,i ->
                coroutineContext.ensureActive()
                val text=sheet.lines[i].text
                val translated=if(text.isBlank()) "" else memo[text] ?: translator.translate(text).waitFor().also { memo[text]=it }
                line(i,translated); status("Traduciendo ${n+1}/${sheet.lines.size}…")
            }
            status("Traducción automática disponible")
        } finally { translator.close() }
    }
}
