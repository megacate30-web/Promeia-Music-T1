package com.promeia.music
import com.promeia.music.data.*
import org.junit.Assert.*
import org.junit.Test
class LibraryTest {
    private fun s(id:String,title:String,artist:String)=Song(id,title,artist,"Album","Music",200000,1,"file.mp3")
    @Test fun normalizesAccentsAndPunctuation(){assertEquals("cancion de amor",normalized(" Canción de AMOR! "))}
    @Test fun avoidsAmbiguousMatches(){val a=s("a","Halo","Beyoncé");assertEquals(a,matchSong("halo","Beyonce",listOf(a)));assertNull(matchSong("halo","Beyonce",listOf(a,a.copy(uri="b"))))}
    @Test fun matchesExportedPlaylist(){val a=s("content://media/a","Halo","Beyoncé");val list=Tools.parsePlaylist("#EXTM3U\n#EXTINF:200,Beyoncé - Halo\ncontent://media/a\n#EXTINF:100,Other - Missing\nfile:///missing.mp3");val r=matchTracks(list,listOf(a));assertEquals(listOf(a),r.matched);assertEquals("Missing",r.missing.single().title)}
    @Test fun parsesTabSeparatedTracks(){assertEquals(listOf(TransferTrack("Halo","Beyoncé")),Tools.parsePlaylist("title\tartist\nHalo\tBeyoncé"))}
    @Test fun favoriteAndHiddenFlagsAreStoredSeparately(){val edit=Edit("a",favorite=true,hidden=true);assertTrue(edit.favorite);assertTrue(edit.hidden)}
    @Test fun playbackTime(){assertEquals("3:20",formatTime(200000));assertEquals("0:00",formatTime(-1))}
}
