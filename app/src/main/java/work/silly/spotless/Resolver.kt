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
    /** [url] is opened in YT Music (or a browser). [note] is shown briefly to the user. */
    data class Open(val url: String, val note: String) : Outcome
    data class Fail(val message: String) : Outcome
}

class Resolver(
    private val http: Http,
    private val ytm: YtmClient,
    private val cache: MatchCache = NoCache,
    /** YouTube's anonymous queue (watch_videos) takes at most 50 videos. */
    private val maxQueue: Int = 50,
    private val parallelism: Int = 6,
) {
    fun resolve(text: String, progress: (String) -> Unit = {}): Outcome {
        val ref = when (val p = SpotifyLink.parse(text)) {
            null -> return Outcome.Fail("No Spotify link found")
            is ParsedLink.Direct -> p.ref
            is ParsedLink.Short -> {
                progress("Following short link …")
                val r = http.get(p.url)
                SpotifyLink.findRef(r.finalUrl) ?: SpotifyLink.findRef(r.body)
                    ?: return Outcome.Fail("The short link didn't point to any Spotify content")
            }
        }

        progress("Fetching from Spotify …")
        val page = http.get(ref.embedUrl)
        if (page.code != 200) return Outcome.Fail("Spotify returned HTTP ${page.code} (private or deleted?)")
        val entity = EmbedParser.parse(page.body)

        return when {
            ref.type == "track" -> resolveSingle(entity)
            ref.isCollection -> resolveCollection(entity, progress)
            // Artists, podcasts etc.: no 1:1 translation, but a search beats nothing.
            else -> Outcome.Open(searchUrl(entity.name), "Searching for “${entity.name}”")
        }
    }

    private fun resolveSingle(e: SpotifyEntity): Outcome {
        val t = e.tracks.firstOrNull() ?: return Outcome.Open(searchUrl(e.name), "Searching for “${e.name}”")
        val id = matchTrack(t)
        return if (id != null) Outcome.Open(watchUrl(id), "${t.artists.firstOrNull().orEmpty()} – ${t.title}")
        else Outcome.Open(searchUrl(t.query), "No confident match, showing search")
    }

    private fun resolveCollection(e: SpotifyEntity, progress: (String) -> Unit): Outcome {
        if (e.tracks.isEmpty()) return Outcome.Fail("“${e.name}” is empty or unavailable")
        val tracks = e.tracks.take(maxQueue)
        val done = AtomicInteger()
        progress("Matching 0/${tracks.size} …")

        val pool = Executors.newFixedThreadPool(parallelism)
        val ids = try {
            tracks.map { t ->
                pool.submit<String?> {
                    runCatching { matchTrack(t) }.getOrNull()
                        .also { progress("Matching ${done.incrementAndGet()}/${tracks.size} …") }
                }
            }.map { it.get() } // order is preserved
        } finally {
            pool.shutdown()
        }

        val hits = ids.filterNotNull().distinct()
        if (hits.isEmpty()) return Outcome.Open(searchUrl(e.name), "No tracks found, searching for “${e.name}”")

        val note = buildString {
            append("${e.name}: ${hits.size}/${tracks.size} tracks")
            if (e.tracks.size > maxQueue) append(" (truncated from ${e.tracks.size})")
        }
        if (hits.size == 1) return Outcome.Open(watchUrl(hits[0]), note)

        progress("Building queue …")
        val queueUrl = "https://www.youtube.com/watch_videos?video_ids=" + hits.joinToString(",")
        val list = runCatching { anonymousListId(queueUrl) }.getOrNull()
        return if (list != null) Outcome.Open("https://music.youtube.com/watch?v=${hits[0]}&list=$list", note)
        // Fallback: let the YouTube app/browser handle watch_videos itself.
        else Outcome.Open(queueUrl, "$note (opened via YouTube)")
    }

    /** watch_videos replies with a 303 to watch?v=…&list=TLGG…; that list ID also works in YT Music. */
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
