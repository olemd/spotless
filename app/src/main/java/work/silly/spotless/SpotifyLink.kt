package work.silly.spotless

/** Hva en delt Spotify-lenke peker på. */
data class SpotifyRef(val type: String, val id: String) {
    val embedUrl get() = "https://open.spotify.com/embed/$type/$id"
    val isCollection get() = type == "playlist" || type == "album"
}

sealed interface ParsedLink {
    data class Direct(val ref: SpotifyRef) : ParsedLink
    /** spotify.link / spoti.fi — må følges for å finne målet. */
    data class Short(val url: String) : ParsedLink
}

object SpotifyLink {
    private const val ID = "([A-Za-z0-9]{22})"
    private const val TYPES = "(track|album|playlist|artist|episode|show)"

    // open.spotify.com/intl-no/track/<id>?si=... og spotify:track:<id>
    private val webRe = Regex("""open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?$TYPES/$ID""")
    private val uriRe = Regex("""spotify:$TYPES:$ID""")
    private val shortRe = Regex("""https?://(?:spotify\.link|spoti\.fi)/[A-Za-z0-9_-]+""")

    /** Finner første Spotify-lenke i vilkårlig tekst (delingstekst inneholder ofte mer enn lenken). */
    fun parse(text: String): ParsedLink? {
        webRe.find(text)?.let { return ParsedLink.Direct(SpotifyRef(it.groupValues[1], it.groupValues[2])) }
        uriRe.find(text)?.let { return ParsedLink.Direct(SpotifyRef(it.groupValues[1], it.groupValues[2])) }
        shortRe.find(text)?.let { return ParsedLink.Short(it.value) }
        return null
    }

    /** Brukes på HTML/URL etter å ha fulgt en kortlenke. */
    fun findRef(text: String): SpotifyRef? =
        (webRe.find(text) ?: uriRe.find(text))?.let { SpotifyRef(it.groupValues[1], it.groupValues[2]) }
}
