package com.layar.live

import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.concurrent.thread

data class Comment(val id: Long, val user: String, val text: String)

/**
 * Komentar TikTok Live.
 * - relay kosong  -> tersambung langsung dari dalam APK (library TikTokLiveJava, tanpa server).
 * - relay terisi  -> lewat server Node.js (folder /server), cadangan kalau mode langsung gagal.
 */
class TikTokLive(private val relay: String, user: String, private val onComment: (Comment) -> Unit) {
    private val name = user.removePrefix("@").trim()
    private var ws: WebSocket? = null
    private var direct: Any? = null
    private val main = Handler(Looper.getMainLooper())
    private var n = 0L
    private fun post(u: String, t: String) = main.post { onComment(Comment(n++, u, t)) }

    fun start() = if (relay.isBlank()) startDirect() else startRelay()

    private fun startDirect() {
        thread {
            runCatching {
                direct = io.github.jwdeveloper.tiktok.TikTokLive.newClient(name)
                    .onComment { _, e -> post(e.user.profileName, e.text) }
                    .onError { _, e -> post("sistem", "Error: ${e.exception.message}") }
                    .buildAndConnect()
            }.onFailure { post("sistem", "Gagal tersambung: ${it.message}") }
        }
    }

    private fun startRelay() {
        val url = relay.trimEnd('/') + "/?user=" + Uri.encode(name)
        ws = OkHttpClient().newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text).let { post(it.getString("user"), it.getString("text")) } }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) = post("sistem", "Relay putus: ${t.message}")
        })
    }

    fun stop() {
        ws?.close(1000, null)
        runCatching { direct?.let { it.javaClass.getMethod("disconnect").invoke(it) } }
    }
}
