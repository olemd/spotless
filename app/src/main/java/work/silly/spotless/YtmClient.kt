package work.silly.spotless

import org.json.JSONObject

/**
 * Anonymous search against YT Music's internal API (InnerTube), the same one the website uses.
 * Unofficial: Google can change it. The client version is scraped from music.youtube.com and
 * cached by the caller; a hard-coded version is used if that fails.
 */
class YtmClient(
    private val http: Http,
    private val versionProvider: () -> String? = { null },
    private val gl: String = "US",
) {
    fun search(query: String): List<Candidate> {
        val version = versionProvider() ?: FALLBACK_VERSION
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", version)
                .put("hl", "en")
                .put("gl", gl)))
            .put("query", query)
            .toString()
        val r = http.post(
            "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
            body,
            mapOf(
                "Content-Type" to "application/json",
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "Cookie" to CONSENT_COOKIE,
            ),
        )
        if (r.code != 200) throw IllegalStateException("YT Music search failed: HTTP ${r.code}")
        return YtmParser.parse(r.body)
    }

    companion object {
        /** Verified 2026-10-11. Refreshed automatically from the website at runtime. */
        const val FALLBACK_VERSION = "1.20261006.10.00"
        /** "Reject all" on Google's consent page; without it EEA users get a consent page instead of HTML. */
        const val CONSENT_COOKIE = "SOCS=CAI"
        private val versionRe = Regex(""""INNERTUBE_CLIENT_VERSION":"([^"]+)"""")

        fun fetchCurrentVersion(http: Http): String? = runCatching {
            val html = http.get("https://music.youtube.com/", mapOf("Cookie" to CONSENT_COOKIE)).body
            versionRe.find(html)?.groupValues?.get(1)
        }.getOrNull()
    }
}
