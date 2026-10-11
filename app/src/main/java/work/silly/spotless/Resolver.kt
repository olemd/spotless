package work.silly.spotless

import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

interface MatchCache {
    fun get(spotifyId: String): String?
    fun put(spotifyId: String, videoId: String)
}

object NoCache : MatchCache {
    override fun get(spotifyId: String): String? = null
    override fun put(spotifyId: String, videoId: String) {}
}

sealed interface Outcome {
    /** [url] åpnes i YT Music (eller nettleser). [note] vises kort til brukeren. */
    data class Open(val url: String, val note: String) : Outcome
    data class Fail(val message: String) : Outcome
}

class Resolver(
    private val http: Http,
    private val ytm: YtmClient,
    private val cache: MatchCache = NoCache,
    /** YouTube sin anonyme kø (watch_videos) tar maks 50 videoer. */
    private val maxQueue: Int = 50,
    private val parallelism: Int = 6,
) {
    fun resolve(text: String, progress: (String) -> Unit = {}): Outcome {
        val ref = when (val p = SpotifyLink.parse(text)) {
            null -> return Outcome.Fail("Fant ingen Spotify-lenke")
            is ParsedLink.Direct -> p.ref
            is ParsedLink.Short -> {
                progress("Følger kortlenke …")
                val r = http.get(p.url)
                SpotifyLink.findRef(r.finalUrl) ?: SpotifyLink.findRef(r.body)
                    ?: return Outcome.Fail("Kortlenken pekte ikke på noe Spotify-innhold")
            }
        }

        progress("Henter fra Spotify …")
        val page = http.get(ref.embedUrl)
        if (page.code != 200) return Outcome.Fail("Spotify svarte HTTP ${page.code} (privat eller slettet?)")
        val entity = EmbedParser.parse(page.body)

        return when {
            ref.type == "track" -> resolveSingle(entity)
            ref.isCollection -> resolveCollection(entity, progress)
            // Artist, podkast o.l.: ingen 1:1-oversettelse, men et søk er bedre enn ingenting.
            else -> Outcome.Open(searchUrl(entity.name), "Søker etter «${entity.name}»")
        }
    }

    private fun resolveSingle(e: SpotifyEntity): Outcome {
        val t = e.tracks.firstOrNull() ?: return Outcome.Open(searchUrl(e.name), "Søker etter «${e.name}»")
        val id = matchTrack(t)
        return if (id != null) Outcome.Open(watchUrl(id), "${t.artists.firstOrNull().orEmpty()} – ${t.title}")
        else Outcome.Open(searchUrl(t.query), "Fant ikke sikker match, viser søk")
    }

    private fun resolveCollection(e: SpotifyEntity, progress: (String) -> Unit): Outcome {
        if (e.tracks.isEmpty()) return Outcome.Fail("«${e.name}» er tom eller utilgjengelig")
        val tracks = e.tracks.take(maxQueue)
        val done = AtomicInteger()
        progress("Matcher 0/${tracks.size} …")

        val pool = Executors.newFixedThreadPool(parallelism)
        val ids = try {
            tracks.map { t ->
                pool.submit<String?> {
                    runCatching { matchTrack(t) }.getOrNull()
                        .also { progress("Matcher ${done.incrementAndGet()}/${tracks.size} …") }
                }
            }.map { it.get() } // rekkefølgen bevares
        } finally {
            pool.shutdown()
        }

        val hits = ids.filterNotNull().distinct()
        if (hits.isEmpty()) return Outcome.Open(searchUrl(e.name), "Fant ingen spor, søker etter «${e.name}»")

        val note = buildString {
            append("${e.name}: ${hits.size}/${tracks.size} spor")
            if (e.tracks.size > maxQueue) append(" (kuttet fra ${e.tracks.size})")
        }
        if (hits.size == 1) return Outcome.Open(watchUrl(hits[0]), note)

        progress("Lager kø …")
        val queueUrl = "https://www.youtube.com/watch_videos?video_ids=" + hits.joinToString(",")
        val list = runCatching { anonymousListId(queueUrl) }.getOrNull()
        return if (list != null) Outcome.Open("https://music.youtube.com/watch?v=${hits[0]}&list=$list", note)
        // Fallback: la YouTube-appen/nettleseren håndtere watch_videos selv.
        else Outcome.Open(queueUrl, "$note (åpnes via YouTube)")
    }

    /** watch_videos svarer med 303 til watch?v=…&list=TLGG…; den list-IDen fungerer også i YT Music. */
    private fun anonymousListId(queueUrl: String): String? {
        val r = http.get(queueUrl, mapOf("Cookie" to YtmClient.CONSENT_COOKIE), followRedirects = false)
        return r.location?.let { Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(it)?.groupValues?.get(1) }
    }

    fun matchTrack(t: SourceTrack): String? {
        t.spotifyId?.let { id -> cache.get(id)?.let { return it } }
        val best = Matcher.best(t, ytm.search(t.query)) ?: return null
        t.spotifyId?.let { cache.put(it, best.candidate.videoId) }
        return best.candidate.videoId
    }

    companion object {
        fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"
        fun searchUrl(q: String) = "https://music.youtube.com/search?q=" + URLEncoder.encode(q, "UTF-8")
    }
}
