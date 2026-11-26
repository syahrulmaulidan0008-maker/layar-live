package com.layar.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class Line(val t: Long, val s: String)

/** Lirik tersinkron (format LRC) dari LRCLIB. */
object Lyrics {
    private val http = OkHttpClient()
    private val re = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?](.*)""")

    suspend fun fetch(title: String, artist: String, durMs: Long): List<Line> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist)
                .apply { if (durMs > 0) addQueryParameter("duration", (durMs / 1000).toString()) }.build()
            val req = Request.Builder().url(url).header("User-Agent", "LayarLive/1.0").build()
            val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                ?: return@runCatching emptyList<Line>()
            parse(JSONObject(body).optString("syncedLyrics"))
        }.getOrDefault(emptyList())
    }

    fun parse(s: String): List<Line> = re.findAll(s).map {
        val g = it.groupValues
        val ms = if (g[3].isEmpty()) 0L else g[3].padEnd(3, '0').take(3).toLong()
        Line(g[1].toLong() * 60000 + g[2].toLong() * 1000 + ms, g[4].trim())
    }.filter { it.s.isNotEmpty() }.toList()
}
