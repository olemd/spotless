package work.silly.spotless

/** What a shared Spotify link points to. */
data class SpotifyRef(val type: String, val id: String) {
    val embedUrl get() = "https://open.spotify.com/embed/$type/$id"
    val isCollection get() = type == "playlist" || type == "album"
}

sealed interface ParsedLink {
    data class Direct(val ref: SpotifyRef) : ParsedLink
    /** spotify.link / spoti.fi — must be followed to find the target. */
    data class Short(val url: String) : ParsedLink
}

object SpotifyLink {
    private const val ID = "([A-Za-z0-9]{22})"
    private const val TYPES = "(track|album|playlist|artist|episode|show)"

    // open.spotify.com/intl-no/track/<id>?si=... and spotify:track:<id>
    private val webRe = Regex("""open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?$TYPES/$ID""")
    private val uriRe = Regex("""spotify:$TYPES:$ID""")
    private val shortRe = Regex("""https?://(?:spotify\.link|spoti\.fi)/[A-Za-z0-9_-]+""")

    /** Finds the first Spotify link in arbitrary text (share text often contains more than the link). */
    fun parse(text: String): ParsedLink? {
        webRe.find(text)?.let { return ParsedLink.Direct(SpotifyRef(it.groupValues[1], it.groupValues[2])) }
        uriRe.find(text)?.let { return ParsedLink.Direct(SpotifyRef(it.groupValues[1], it.groupValues[2])) }
        shortRe.find(text)?.let { return ParsedLink.Short(it.value) }
        return null
    }

    /** Used on the HTML/URL after following a short link. */
    fun findRef(text: String): SpotifyRef? =
        (webRe.find(text) ?: uriRe.find(text))?.let { SpotifyRef(it.groupValues[1], it.groupValues[2]) }
}
