---
name: layarlive
description: Konteks lengkap dan panduan melanjutkan proyek aplikasi Android "Layar Live" (pemutar musik sinkron berbasis Kotlin + Jetpack Compose dengan lirik tersinkron, vinyl berputar, latar dari sampul album, dan komentar TikTok Live lewat relay Node.js di PC). Gunakan skill ini setiap kali pengguna menyebut Layar Live, LayarLive, apk musik sinkron, lirik LRCLIB, komentar TikTok Live di APK, relay/server Node.js, ikon/vinyl, atau meminta melanjutkan, memperbaiki, membuild, atau merilis APK proyek ini lewat GitHub Actions, walaupun tidak menyebut nama proyek secara eksplisit.
---

# Layar Live: konteks proyek dan cara melanjutkan
Bahasa dengan pengguna: **Bahasa Indonesia**, nada santai dan jelas.
**Aturan: setiap selesai memperbaiki/mengubah sesuatu, tambahkan satu entri di bagian "Catatan perubahan" paling bawah file ini (dan commit ke repo), supaya pekerjaan bisa dilanjutkan dari chat mana pun.**

## 1. Tujuan
APK penampil musik untuk TikTok Live (layar HP di-share). Yang terpenting: lirik muncul dan sinkron. Aplikasi tidak memutar musik; ia membaca lagu dari Spotify / YT Music lewat `NotificationListenerService` + `MediaSessionManager` (izin Notification Access).

## 2. Desain UI
- Latar = **sampul lagu (foto) yang diburamkan halus**, ganti dengan crossfade 1,2 dtk tiap ganti lagu; lapisan gelap sangat tipis (hanya menebal di bawah). Blur Compose hanya Android 12+; di bawahnya foto tetap tampil tanpa blur ekstra.
- Badge "SEDANG DIPUTAR" (ketuk untuk isi @username dan alamat relay), sampul besar + vinyl digambar lewat kode (berputar dengan inersia), lirik tersinkron (gulir mulus ease-out, baris aktif membesar, baris jauh redup + blur), judul + artis, waveform animasi, komentar maksimal 4 kartu liquid glass.
- Komentar: masuk dengan pegas dari bawah, naik saat ada baru, makin redup, hilang otomatis setelah 20 dtk dengan fade. Pesan status relay memakai user `sistem` dan otomatis diganti komentar asli.
- Teks semuanya putih, dengan bayangan tipis di lirik.

## 3. Arsitektur
Repo: **`syahrulmaulidan0008-maker/layar-live`** (publik, branch `main`).
```
.github/workflows/build.yml   # build APK + rilis otomatis v1.<nomor run>
SKILL.md                      # file ini
app/src/main/java/com/layar/live/
  MainActivity.kt   # seluruh UI Compose
  MusicListener.kt  # NotificationListenerService + MediaSession
  Lyrics.kt         # lirik LRC dari LRCLIB
  TikTokLive.kt     # komentar: mode relay (WebSocket) atau mode langsung (TikTokLiveJava, TIDAK andal)
server/server.js, package.json   # relay Node.js v2.1 (jalan di PC)
```
Stack: Kotlin 2.0.20, AGP 8.5.2, Gradle 8.9, Java 17, compile/target SDK 34, min 26, package `com.layar.live`, Compose BOM 2024.09.00. Manifest sudah `usesCleartextTraffic="true"` (perlu untuk `ws://`).

## 4. Relay komentar (cara yang dipakai)
Mode langsung di HP (library Java tidak resmi) tidak andal, jadi **pakai relay di PC**:
1. PC dan HP satu Wi-Fi. Pasang Node.js >= 20.
2. Di folder `server`: `npm install` lalu `node server.js` (Windows: `start.bat` dari paket zip). Terminal mencetak alamat `ws://192.168.x.x:8080`.
3. APK: ketuk "SEDANG DIPUTAR" -> isi @username (yang sedang live) dan kolom relay `ws://IP-PC:8080`.
4. Tes jalur PC -> APK tanpa live: `node server.js --demo` (komentar palsu tiap 3 dtk).
Opsional kalau kena rate limit: `set SIGN_API_KEY=kunci` (API key dari eulerstream.com) sebelum menjalankan server.

Desain server v2.1: satu koneksi TikTok per username dibagi ke semua APK; koneksi dipertahankan 90 dtk setelah APK putus (sambung ulang APK tidak memicu rate limit); sambung ulang otomatis ke TikTok dengan jeda bertambah; ping 15 dtk; pesan status dikirim sebagai user `sistem`. `new TikTokLiveConnection(user, {})` WAJIB diberi objek opsi (v2.5.0 crash kalau undefined).

## 5. Keterbatasan
- Sandbox Claude: tanpa Android SDK dan tanpa akses `tiktok.com`, jadi APK hanya dibuild lewat GitHub Actions dan koneksi TikTok tidak bisa diuji dari sana (relay hanya bisa diuji dengan `--demo`).
- Library TikTok tidak resmi: bisa rusak kapan saja dan melanggar ketentuan TikTok.
- Kunci debug berbeda tiap build CI: update APK harus uninstall dulu, dan itu **menghapus username/relay yang tersimpan** (isi ulang).
- Jangan jalankan `pkill -f "node server.js"` dari bash sandbox (mematikan shell sendiri).
- Log job lengkap tidak bisa diunduh; baca error lewat anotasi: `GET /repos/{owner}/{repo}/check-runs/{job_id}/annotations`, filter `failure`.
- Di `BoxWithConstraints`, jangan pakai `maxWidth` langsung di dalam Row/Column bersarang; simpan dulu ke `val w = maxWidth`.

## 6. Alur kerja
1. Minta token GitHub baru; jangan simpan ke file. Pakai tanpa menyimpannya di config:
   `AUTH=$(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)` lalu `git -c http.extraheader="Authorization: Basic $AUTH" clone/pull/push ...`
2. Selalu `pull --rebase` dulu (pengguna kadang mengubah kode dari chat lain).
3. Ubah kode, commit, push -> build otomatis. Pantau `GET /actions/runs?head_sha=<sha>` sampai `completed`.
4. Gagal: baca anotasi, perbaiki, ulangi. Berhasil: ambil link dari `/releases/latest` -> `assets[].browser_download_url`.
5. Tulis di balasan: build sukses tidak berarti teruji di HP. Minta pengguna mencabut token setelah selesai.
6. Tambahkan entri di "Catatan perubahan" di bawah.

## 7. Ide lanjutan
- Foto avatar asli penonton; efek kaca dengan refraksi; APK rilis bertanda tangan tetap (supaya update tidak menghapus pengaturan); layar tetap menyala saat live (`FLAG_KEEP_SCREEN_ON`); fallback lirik Musixmatch; mode landscape/overlay.

## Catatan perubahan
- v1.3: build pertama sukses, ikon, rilis otomatis.
- v1.4-1.5: UI putih, sampul besar, transisi lirik/komentar; perbaikan `onFailure` dan deklarasi ganda `top`/`accent`.
- v1.6: transisi komentar (pegas masuk, redup menurut umur, hilang 20 dtk), transisi lirik ease-out + blur jarak, status koneksi TikTok; fix `maxWidth`.
- (sesi berikut, commit `55e0a91`) relay dipindah ke `tiktok-live-connector` 2.x.
- Sesi terbaru: **penyebab relay sering putus** = OkHttp readTimeout default 10 dtk memutus WebSocket yang sepi, dan tiap sambung ulang APK membuat koneksi TikTok baru di server (kena rate limit, komentar tidak datang). Perbaikan APK: `readTimeout(0)`, `pingInterval(15s)`, tangani `onClosed`, backoff, abaikan socket lama. Perbaikan server: v2.1 (koneksi dibagi per username, grace 90 dtk, auto-reconnect, ping, `--demo`). Latar kini foto sampul 240px (bukan bercak warna 32px) + blur 14dp (Android 12+), lapisan gelap tipis. Belum diuji di HP.
- Sesi berikut: log PC pengguna menunjukkan relay menerima ratusan event chat TikTok tapi `d.comment` = `undefined` (JSON.stringify membuang field itu, APK `getString("text")` melempar exception lalu ditelan `runCatching`, jadi komentar tidak tampil). Perbaikan: server memakai `pickText()` (coba `comment/content/text/message/msg` lalu field apa pun yang namanya mirip) dan `pickName()` (uniqueId/displayId/nickname); kalau teks tetap kosong, server mencetak data mentah event sekali (`PERINGATAN: teks komentar kosong. Data mentah: ...`) dan mengirim pesan status ke APK. APK memakai `optString` dan mengabaikan pesan kosong. Jika masih kosong, baca "Data mentah" di log PC untuk tahu nama field yang benar lalu tambahkan ke `TEXT_KEYS`. Belum diuji di HP.

