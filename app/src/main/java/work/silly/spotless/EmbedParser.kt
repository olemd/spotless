package work.silly.spotless

import org.json.JSONObject

data class SourceTrack(
    val spotifyId: String?,
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
) {
    val query get() = (artists.take(2) + title).joinToString(" ")
}

data class SpotifyEntity(
    val type: String,
    val name: String,
    val subtitle: String?,
    val tracks: List<SourceTrack>,
)

/**
 * Leser den offentlige embed-siden (open.spotify.com/embed/...).
 * Den er en Next.js-side med hele datamodellen i <script id="__NEXT_DATA__">.
 * Uoffisielt: hvis Spotify endrer formatet, er det her det brekker.
 * Kjent begrensning: spillelister kuttes på 100 spor.
 */
object EmbedParser {
    private val nextData = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    class FormatChanged(msg: String) : Exception(msg)

    fun parse(html: String): SpotifyEntity {
        val raw = nextData.find(html)?.groupValues?.get(1)
            ?: throw FormatChanged("Fant ikke __NEXT_DATA__ i embed-siden")
        val entity = JSONObject(raw)
            .optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            ?: throw FormatChanged("Fant ikke entity i __NEXT_DATA__")

        val type = entity.optString("type")
        val name = entity.optString("name").ifEmpty { entity.optString("title") }
        val subtitle = entity.optString("subtitle").ifEmpty { null }

        val tracks = when {
            entity.has("trackList") -> {
                val list = entity.getJSONArray("trackList")
                (0 until list.length()).mapNotNull { i ->
                    val t = list.getJSONObject(i)
                    // Lokale filer o.l. har ingen spilbar track-URI; hopp over.
                    val uri = t.optString("uri")
                    if (!uri.startsWith("spotify:track:")) return@mapNotNull null
                    SourceTrack(
                        spotifyId = uri.removePrefix("spotify:track:"),
                        title = t.optString("title"),
                        artists = splitArtists(t.optString("subtitle")),
                        durationMs = t.optLong("duration"),
                    )
                }
            }
            type == "track" -> {
                val arr = entity.optJSONArray("artists")
                val artists = if (arr != null) (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }
                else splitArtists(subtitle.orEmpty())
                listOf(SourceTrack(entity.optString("id"), name, artists, entity.optLong("duration")))
            }
            else -> emptyList()
        }
        return SpotifyEntity(type, name, subtitle, tracks)
    }

    // Embed bruker ", " mellom artister. Artistnavn med komma blir splittet feil,
    // men det påvirker bare søkestrengen marginalt.
    private fun splitArtists(s: String) =
        s.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
}
