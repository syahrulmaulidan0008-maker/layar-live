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

data class Comment(val id: Long, val user: String, val text: String, val at: Long = android.os.SystemClock.elapsedRealtime())

private var nextId = 0L   // ID unik di seluruh aplikasi (dipakai sebagai key daftar komentar)

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
    @Volatile private var stopped = false
    private fun post(u: String, t: String) = main.post { if (!stopped) onComment(Comment(nextId++, u, t)) }

    fun start() = if (relay.isBlank()) startDirect() else startRelay()

    private fun startDirect() {
        thread {
            try {
                direct = io.github.jwdeveloper.tiktok.TikTokLive.newClient(name)
                    .onConnected { _, _ -> post("sistem", "Tersambung ke @$name, menunggu komentar…") }
                    .onComment { _, e -> post(e.user.profileName, e.text) }
                    .onError { _, e -> post("sistem", "Error: ${e.exception.message}") }
                    .buildAndConnect()
            } catch (t: Throwable) { // Throwable: NoClassDefFoundError juga tertangkap, aplikasi tidak crash
                post("sistem", "Mode langsung gagal di Android (${t.javaClass.simpleName}). Isi alamat relay.")
            }
        }
    }

    private fun startRelay() {
        if (stopped) return
        val url = relay.trimEnd('/') + "/?user=" + Uri.encode(name)
        ws = OkHttpClient().newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { post("sistem", "Tersambung ke relay, menunggu komentar…") }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text).let { post(it.getString("user"), it.getString("text")) } }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                post("sistem", "Relay putus, mencoba lagi…")
                main.postDelayed({ startRelay() }, 4000)   // sambung ulang otomatis
            }
        })
    }

    fun stop() {
        stopped = true
        ws?.close(1000, null)
        runCatching { direct?.let { it.javaClass.getMethod("disconnect").invoke(it) } }
    }
}
