package com.promeia.music.discovery
import org.junit.Assert.*
import org.junit.Test
import java.net.URL
class DownloadRulesTest {
    @Test fun urls(){assertTrue(DownloadRules.allowed(URL("https://example.org/music.mp3")));assertFalse(DownloadRules.allowed(URL("http://example.org/music.mp3")));assertFalse(DownloadRules.allowed(URL("https://www.youtube.com/watch?v=abc")));assertFalse(DownloadRules.allowed(URL("https://rr1.googlevideo.com/audio")));assertFalse(DownloadRules.allowed(URL("https://user@example.org/a.mp3")))}
    @Test fun formats(){assertEquals("mp3",DownloadRules.extension("audio/mpeg; charset=binary","/file"));assertEquals("flac",DownloadRules.extension("application/octet-stream","/file.FLAC"));assertNull(DownloadRules.extension("text/html","/file.mp3"));assertNull(DownloadRules.extension("video/mp4","/file.m4a"))}
    @Test fun identifiers(){assertEquals(64,DownloadRules.key("https://example.org/a").length);assertEquals(DownloadRules.key("a"),DownloadRules.key("a"));assertNotEquals(DownloadRules.key("a"),DownloadRules.key("b"))}
}
