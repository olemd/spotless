package work.silly.spotless

import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String, val finalUrl: String, val location: String?)

/** Thin layer over HttpURLConnection. No dependencies; works on both Android and the JVM (tests). */
open class Http(
    private val userAgent: String = DEFAULT_UA,
    private val timeoutMs: Int = 10_000,
) {
    open fun get(url: String, headers: Map<String, String> = emptyMap(), followRedirects: Boolean = true) =
        request("GET", url, headers, null, followRedirects)

    open fun post(url: String, body: String, headers: Map<String, String> = emptyMap()) =
        request("POST", url, headers, body, true)

    private fun request(
        method: String, url: String, headers: Map<String, String>, body: String?, follow: Boolean,
    ): HttpResponse {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.instanceFollowRedirects = follow
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept-Language", "en")
            headers.forEach(c::setRequestProperty)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text, c.url.toString(), c.getHeaderField("Location"))
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
    }
}
