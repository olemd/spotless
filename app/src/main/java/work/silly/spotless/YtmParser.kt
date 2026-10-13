package work.silly.spotless

import org.json.JSONArray
import org.json.JSONObject

data class Candidate(
    val videoId: String,
    val title: String,
    /** Everything after the title, e.g. "Song • Rick Astley • Whenever You Need Somebody • 3:34". */
    val subtitle: String,
    /** MUSIC_VIDEO_TYPE_ATV (audio track), _OMV (official video), _UGC, _PODCAST_EPISODE ... */
    val videoType: String?,
) {
    val durationSec: Int? get() = durationRe.findAll(subtitle).lastOrNull()?.let {
        val (h, m, s) = Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        (h.toIntOrNull() ?: 0) * 3600 + m.toInt() * 60 + s.toInt()
    }

    private companion object {
        val durationRe = Regex("""(?:^|\s)(?:(\d+):)?(\d{1,2}):(\d{2})(?:\s|$)""")
    }
}

/**
 * Parses the response from music.youtube.com/youtubei/v1/search (InnerTube, WEB_REMIX).
 * Walks the response recursively instead of assuming a fixed path, because
 * Google moves sections around more often than it changes the item renderers themselves.
 */
object YtmParser {
    private val videoTypeRe = Regex("MUSIC_VIDEO_TYPE_[A-Z_]+")

    fun parse(json: String): List<Candidate> {
        val out = LinkedHashMap<String, Candidate>()
        walk(JSONObject(json)) { key, obj ->
            val c = when (key) {
                "musicResponsiveListItemRenderer" -> fromListItem(obj)
                "musicCardShelfRenderer" -> fromCard(obj)
                else -> null
            }
            if (c != null) out.putIfAbsent(c.videoId, c)
        }
        return out.values.toList()
    }

    private fun fromListItem(r: JSONObject): Candidate? {
        val cols = r.optJSONArray("flexColumns") ?: return null
        val texts = (0 until cols.length()).map {
            runsText(cols.getJSONObject(it).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs"))
        }
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.ifEmpty { null }
            ?: findWatchVideoId(r)
            ?: return null
        return Candidate(videoId, texts.firstOrNull().orEmpty(), texts.drop(1).joinToString(" • "), videoType(r))
    }

    private fun fromCard(r: JSONObject): Candidate? {
        val titleRuns = r.optJSONObject("title")?.optJSONArray("runs") ?: return null
        val videoId = titleRuns.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")?.optString("videoId")?.ifEmpty { null } ?: return null
        return Candidate(
            videoId,
            runsText(titleRuns),
            runsText(r.optJSONObject("subtitle")?.optJSONArray("runs")),
            videoType(r.optJSONObject("title") ?: r),
        )
    }

    private fun videoType(o: JSONObject) = videoTypeRe.find(o.toString())?.value

    private fun findWatchVideoId(o: JSONObject): String? {
        var found: String? = null
        walk(o) { key, obj ->
            if (found == null && key == "watchEndpoint") found = obj.optString("videoId").ifEmpty { null }
        }
        return found
    }

    private fun runsText(runs: JSONArray?): String =
        if (runs == null) "" else (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") }

    private fun walk(node: Any?, visit: (String, JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (v is JSONObject) visit(k, v)
                walk(v, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
