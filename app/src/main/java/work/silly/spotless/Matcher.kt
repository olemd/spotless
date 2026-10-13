package work.silly.spotless

import java.text.Normalizer

data class Match(val candidate: Candidate, val score: Double)

/**
 * Picks the best YT Music hit for a Spotify track.
 *
 * Without ISRC (Spotify now hides it) this is heuristic:
 *  - title similarity (token overlap, after normalisation)  — main signal
 *  - artist mentioned in the subtitle                        — strong signal
 *  - duration within a few seconds                           — separates remaster/live/edit
 *  - type: ATV > OMV > UGC; podcasts are discarded
 *  - penalty for "sped up", "live", "cover" etc. not present in the original title
 */
object Matcher {
    /** Below this it counts as no match, and we fall back to a plain search. */
    const val MIN_SCORE = 3.0

    private val noiseWords = listOf(
        "sped up", "speed up", "slowed", "reverb", "nightcore", "8d", "karaoke",
        "instrumental", "cover", "live", "remix", "acoustic", "bass boosted", "tiktok", "1 hour",
    )

    fun best(src: SourceTrack, cands: List<Candidate>): Match? =
        cands.asSequence()
            .filterNot { it.videoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" }
            .map { Match(it, score(src, it)) }
            .filter { it.score >= MIN_SCORE }
            .maxByOrNull { it.score }

    fun score(src: SourceTrack, c: Candidate): Double {
        val srcTitle = norm(stripDecorations(src.title))
        val candTitle = norm(c.title)
        val candSub = norm(c.subtitle)
        val candAll = "$candTitle $candSub"

        // Both with and without decorations: UGC titles like "Artist - Song (1989)" lose the song when stripped.
        val titleSim = maxOf(tokenSim(srcTitle, candTitle), tokenSim(srcTitle, norm(stripDecorations(c.title))))
        if (titleSim < 0.5) return 0.0
        var s = titleSim * 4 // 0–4

        val artistHit = src.artists.map(::norm).filter { it.isNotEmpty() }.count { it in candAll }
        s += when {
            artistHit > 0 -> 3.0
            src.artists.isEmpty() -> 0.0
            else -> -2.0
        }

        val srcSec = (src.durationMs / 1000).toInt()
        c.durationSec?.let { d ->
            val diff = kotlin.math.abs(d - srcSec)
            s += when {
                srcSec == 0 -> 0.0
                diff <= 3 -> 2.0
                diff <= 10 -> 1.0
                diff <= 30 -> 0.0
                else -> -3.0
            }
        }

        s += when (c.videoType) {
            "MUSIC_VIDEO_TYPE_ATV" -> 1.5
            "MUSIC_VIDEO_TYPE_OMV" -> 1.0
            "MUSIC_VIDEO_TYPE_UGC" -> -0.5
            else -> 0.0
        }

        val srcRaw = norm(src.title)
        for (w in noiseWords) if (w in candTitle && w !in srcRaw) s -= 3.0
        return s
    }

    // "Song - 2011 Remaster", "Song (feat. X)", "Song [Live]" -> "Song"
    private val decoration = Regex("""\s*(\(.*?\)|\[.*?]|\s-\s.*$)""")
    internal fun stripDecorations(s: String) = decoration.replace(s, "").ifBlank { s }

    internal fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('&', ' ').replace(" and ", " ")
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Share of the source tokens found in the candidate, slightly penalised if the candidate has a lot extra. */
    internal fun tokenSim(a: String, b: String): Double {
        val ta = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val tb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        val recall = inter / ta.size
        val precision = inter / tb.size
        return 0.75 * recall + 0.25 * precision
    }
}
