package work.silly.spotless

import org.json.JSONObject

/**
 * Anonymt søk mot YT Music sitt interne API (InnerTube), samme som nettsiden bruker.
 * Uoffisielt: Google kan endre det. Klientversjonen hentes fra music.youtube.com og
 * caches av kalleren; en hardkodet versjon brukes hvis det feiler.
 */
class YtmClient(
    private val http: Http,
    private val versionProvider: () -> String? = { null },
    private val gl: String = "NO",
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
        if (r.code != 200) throw IllegalStateException("YT Music-søk feilet: HTTP ${r.code}")
        return YtmParser.parse(r.body)
    }

    companion object {
        /** Verifisert 2026-10-11. Oppdateres automatisk fra nettsiden ved oppstart. */
        const val FALLBACK_VERSION = "1.20261006.10.00"
        /** "Avslå alle" på Googles samtykkeside; uten den får EØS-brukere en consent-side i stedet for HTML. */
        const val CONSENT_COOKIE = "SOCS=CAI"
        private val versionRe = Regex(""""INNERTUBE_CLIENT_VERSION":"([^"]+)"""")

        fun fetchCurrentVersion(http: Http): String? = runCatching {
            val html = http.get("https://music.youtube.com/", mapOf("Cookie" to CONSENT_COOKIE)).body
            versionRe.find(html)?.groupValues?.get(1)
        }.getOrNull()
    }
}
