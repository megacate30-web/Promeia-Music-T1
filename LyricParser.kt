package com.promeia.music.lyrics

data class LyricLine(val timeMs: Long?, val text: String)
data class LyricSheet(val lines: List<LyricLine>, val instrumental: Boolean = false) {
    val synced get() = lines.isNotEmpty() && lines.all { it.timeMs != null }
}

/** LRC: multiple timestamps, 1–3 fractional digits, global offset, sorted lines. */
object LyricParser {
    private val stamp = Regex("\\[(\\d{1,5}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("(?im)^\\s*\\[offset:([+-]?\\d+)]\\s*$")
    private val metadata = Regex("^\\s*\\[[a-zA-Z]+:.*]\\s*$")
    fun parse(raw: String): LyricSheet {
        val text = raw.removePrefix("\uFEFF")
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-3600000, 3600000) ?: 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        text.lineSequence().forEach { line ->
            val matches = stamp.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val words = stamp.replace(line, "").trim()
                for (m in matches) {
                    val sec = m.groupValues[2].toInt()
                    if (sec >= 60) continue
                    val ms = m.groupValues[1].toLong()*60000 + sec*1000L + m.groupValues[3].padEnd(3,'0').toLong()
                    timed.add(LyricLine((ms + shift).coerceAtLeast(0), words))
                }
            } else if (line.isNotBlank() && !metadata.matches(line)) plain.add(LyricLine(null,line.trim()))
        }
        return LyricSheet(if (timed.isNotEmpty()) timed.sortedBy { it.timeMs }.distinct() else plain)
    }
    /** Returns -1 before the first line, or when the sheet is not timed. */
    fun active(sheet: LyricSheet, positionMs: Long, adjustmentMs: Long = 0): Int {
        if (!sheet.synced) return -1
        val position = positionMs + adjustmentMs
        var low = 0; var high = sheet.lines.lastIndex; var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (sheet.lines[mid].timeMs!! <= position) { found = mid; low = mid+1 } else high = mid-1
        }
        return found
    }
}
