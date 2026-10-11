package work.silly.spotless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CoreTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun parsesLinkVariants() {
        val id = "4cOdK2wGLETKBW3PvgPWqT"
        for (s in listOf(
            "https://open.spotify.com/track/$id?si=abc",
            "https://open.spotify.com/intl-no/track/$id",
            "Hør på denne! https://open.spotify.com/track/$id?si=x&utm_source=copy-link",
            "spotify:track:$id",
        )) {
            val p = SpotifyLink.parse(s) as ParsedLink.Direct
            assertEquals(SpotifyRef("track", id), p.ref)
        }
        assertTrue(SpotifyLink.parse("https://spotify.link/AbC123xyz") is ParsedLink.Short)
        assertNull(SpotifyLink.parse("https://example.com/"))
    }

    @Test fun parsesEmbedPlaylist() {
        val e = EmbedParser.parse(res("embed_playlist.html"))
        assertEquals("playlist", e.type)
        assertEquals(50, e.tracks.size)
        val t = e.tracks[0]
        assertEquals("Stop The Wedding!", t.title)
        assertEquals(listOf("Ashe"), t.artists)
        assertEquals(198840L, t.durationMs)
    }

    @Test fun parsesEmbedTrackAndAlbum() {
        val t = EmbedParser.parse(res("embed_track.html"))
        assertEquals("track", t.type)
        assertEquals("Never Gonna Give You Up", t.tracks.single().title)
        assertEquals(listOf("Rick Astley"), t.tracks.single().artists)

        val a = EmbedParser.parse(res("embed_album.html"))
        assertEquals("album", a.type)
        assertEquals(10, a.tracks.size)
        assertTrue(a.tracks.all { it.artists.isNotEmpty() })
    }

    @Test fun parsesYtmSearch() {
        val c = YtmParser.parse(res("ytm_rick.json"))
        assertTrue(c.any { it.videoId == "dQw4w9WgXcQ" && it.videoType == "MUSIC_VIDEO_TYPE_OMV" })
        // Toppkortet ("Sped up") skal også parses, med varighet fra undertittelen.
        val card = c.first { it.videoId == "nkdwbbA_JFs" }
        assertEquals(164, card.durationSec)
    }

    @Test fun picksOfficialOverSpedUpAndUgc() {
        val src = SourceTrack("x", "Never Gonna Give You Up", listOf("Rick Astley"), 213573)
        val m = Matcher.best(src, YtmParser.parse(res("ytm_rick.json")))
        assertEquals("dQw4w9WgXcQ", m?.candidate?.videoId)
    }

    @Test fun matchesAshe() {
        val src = SourceTrack("x", "Stop The Wedding!", listOf("Ashe"), 198840)
        val cands = YtmParser.parse(res("ytm_ashe.json"))
        val m = Matcher.best(src, cands)
        assertNotNull("ingen match blant: " + cands.joinToString { "${it.title} | ${it.subtitle}" }, m)
        println("Ashe -> ${m!!.candidate} score=${m.score}")
    }

    @Test fun normalisation() {
        assertEquals("beyonce halo", Matcher.norm("Beyoncé – Halo!"))
        assertEquals("Song", Matcher.stripDecorations("Song - 2011 Remaster"))
        assertEquals("Song", Matcher.stripDecorations("Song (feat. X)"))
    }

    @Test fun rejectsUnrelated() {
        val src = SourceTrack("x", "Bohemian Rhapsody", listOf("Queen"), 354000)
        assertNull(Matcher.best(src, YtmParser.parse(res("ytm_rick.json"))))
    }

    @Test fun livePlaylist() {
        assumeTrue(System.getProperty("live") == "true")
        val http = Http()
        val r = Resolver(http, YtmClient(http, { YtmClient.fetchCurrentVersion(http) }))
        val t0 = System.currentTimeMillis()
        val out = r.resolve("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        println("$out på ${System.currentTimeMillis() - t0} ms")
        assertTrue(out is Outcome.Open)
    }

    /** Ende-til-ende mot ekte tjenester: ./gradlew test -Dlive=true */
    @Test fun liveAlbum() {
        assumeTrue(System.getProperty("live") == "true")
        val http = Http()
        val r = Resolver(http, YtmClient(http, { YtmClient.fetchCurrentVersion(http) }))
        val ytm = YtmClient(http, { YtmClient.fetchCurrentVersion(http) })
        val e = EmbedParser.parse(http.get(SpotifyRef("album", "6N9PS4QXF1D0OWPk0Sxtb4").embedUrl).body)
        for (t in e.tracks) {
            val m = Matcher.best(t, ytm.search(t.query))
            println("%-40s -> %s".format(t.title.take(40), m?.let { "${it.candidate.title} | ${it.candidate.subtitle} [%.1f]".format(it.score) } ?: "INGEN"))
        }
        val out = r.resolve("https://open.spotify.com/album/6N9PS4QXF1D0OWPk0Sxtb4") { println(it) }
        println(out)
        assertTrue(out is Outcome.Open)
    }
}
