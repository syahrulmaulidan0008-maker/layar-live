package com.layar.live

import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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

    // Penyebab relay sering putus sebelumnya: OkHttp default readTimeout 10 detik, WebSocket yang sepi
    // otomatis dianggap gagal. Sekarang tanpa batas waktu baca + ping tiap 15 detik.
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var attempt = 0

    private fun startRelay() {
        if (stopped) return
        val url = relay.trim().trimEnd('/') + "/?user=" + Uri.encode(name)
        val req = try { Request.Builder().url(url).build() } catch (e: Exception) {
            post("sistem", "Alamat relay tidak valid. Contoh: ws://192.168.1.5:8080"); return
        }
        ws?.cancel()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            private fun lost(webSocket: WebSocket, why: String) {
                if (stopped || webSocket !== ws) return          // abaikan socket lama
                attempt++
                post("sistem", "Relay putus ($why), menyambung ulang…")
                main.postDelayed({ startRelay() }, minOf(1500L * attempt, 8000L))
            }
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                attempt = 0
                post("sistem", "Tersambung ke relay, menunggu komentar…")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text).let { j -> val msg = j.optString("text"); if (msg.isNotBlank()) post(j.optString("user", "?"), msg) } }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { lost(webSocket, "ditutup server") }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                lost(webSocket, t.javaClass.simpleName)
            }
        })
    }

    fun stop() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        ws?.close(1000, null)
        runCatching { direct?.let { it.javaClass.getMethod("disconnect").invoke(it) } }
    }
}
