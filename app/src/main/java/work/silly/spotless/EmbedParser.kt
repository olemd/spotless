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
 * Reads the public embed page (open.spotify.com/embed/...).
 * It is a Next.js page with the whole data model in <script id="__NEXT_DATA__">.
 * Unofficial: if Spotify changes the format, this is where it breaks.
 * Known limitation: playlists are truncated at 100 tracks.
 */
object EmbedParser {
    private val nextData = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    class FormatChanged(msg: String) : Exception(msg)

    fun parse(html: String): SpotifyEntity {
        val raw = nextData.find(html)?.groupValues?.get(1)
            ?: throw FormatChanged("Could not find __NEXT_DATA__ in the embed page")
        val entity = JSONObject(raw)
            .optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            ?: throw FormatChanged("Could not find entity in __NEXT_DATA__")

        val type = entity.optString("type")
        val name = entity.optString("name").ifEmpty { entity.optString("title") }
        val subtitle = entity.optString("subtitle").ifEmpty { null }

        val tracks = when {
            entity.has("trackList") -> {
                val list = entity.getJSONArray("trackList")
                (0 until list.length()).mapNotNull { i ->
                    val t = list.getJSONObject(i)
                    // Local files etc. have no playable track URI; skip them.
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

    // The embed uses ", " between artists. Artist names containing a comma get split
    // incorrectly, but that only affects the search string marginally.
    private fun splitArtists(s: String) =
        s.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
}
