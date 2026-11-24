package com.promeia.music.lyrics
import org.junit.Assert.*
import org.junit.Test
class LyricParserTest {
    @Test fun timestampsAndOffset() {
        val s=LyricParser.parse("[ar:Test]\n[offset:100]\n[00:03.2][00:09.25]A\n[00:05.123]B")
        assertTrue(s.synced); assertEquals(listOf(3300L,5223L,9350L),s.lines.map{it.timeMs})
        assertEquals(-1,LyricParser.active(s,3299));assertEquals(0,LyricParser.active(s,3300));assertEquals(1,LyricParser.active(s,5300));assertEquals(2,LyricParser.active(s,9999))
    }
    @Test fun plainAndInvalid() { val s=LyricParser.parse("One\nTwo");assertFalse(s.synced);assertEquals(-1,LyricParser.active(s,9000));assertEquals(2,s.lines.size);assertTrue(LyricParser.parse("[ar:Test]").lines.isEmpty()) }
    @Test fun adjustmentAndBlank() { val s=LyricParser.parse("[00:01]A\n[00:02]\n[00:03]B");assertEquals(1,LyricParser.active(s,1500,500));assertEquals("",s.lines[1].text);assertEquals(0,LyricParser.active(s,1500)) }
    @Test fun sortedAndClamped() { val s=LyricParser.parse("[offset:-2000]\n[00:03.01]B\n[00:01]A");assertEquals(listOf(0L,1010L),s.lines.map{it.timeMs});assertEquals("A",s.lines.first().text) }
}
