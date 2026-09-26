package dev.terminox

import dev.terminox.music.Lyrics
import dev.terminox.music.Music
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LyricsTest {
    @Test fun cleansNames() {
        assertEquals("Get Lucky", Lyrics.cleanTitle("Get Lucky (feat. Pharrell Williams & Nile Rodgers)"))
        assertEquals("Heroes", Lyrics.cleanTitle("Heroes - 2017 Remaster"))
        assertEquals("Daft Punk", Lyrics.mainArtist("Daft Punk, Pharrell Williams & Nile Rodgers"))
        assertEquals("The Weeknd", Lyrics.mainArtist("The Weeknd & ROSALÍA"))
    }

    /** Hits the real APIs; skipped when LYRICS_ONLINE isn't set. */
    @Test fun findsRealSongs() {
        assumeTrue(System.getenv("LYRICS_ONLINE") != null)
        val songs = listOf(
            Music.Track("Get Lucky (feat. Pharrell Williams & Nile Rodgers)", "Daft Punk, Pharrell Williams & Nile Rodgers", "Random Access Memories", 369, ""),
            Music.Track("Blinding Lights", "The Weeknd", "After Hours", 200, ""),
            Music.Track("Bohemian Rhapsody", "Queen", "A Night at the Opera", 355, ""),
            Music.Track("Smells Like Teen Spirit", "Nirvana", "Nevermind (Remastered)", 301, ""),
        )
        for (s in songs) {
            val r = Lyrics.find(s)
            println("LYRICS ${s.title} -> ${r?.source} synced=${r?.synced} lines=${r?.lines?.size} first='${r?.lines?.firstOrNull { it.text.isNotBlank() }?.let { "${it.timeMs}ms ${it.text}" }}'")
            assert(r != null && r.lines.size > 5)
        }
    }
}
