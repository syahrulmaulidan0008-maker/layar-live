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
- Latar (transparan, mengikuti sampul): dasar gradasi warna dari Palette sampul (fungsi `vivid()` menjaga warna tetap hidup, atas terang, bawah gelap senada) + **foto sampul semi-transparan (alpha .72, blur 18dp di Android 12+, zoom pelan 1.12-1.26 selama 22 dtk)** + kilau putih tipis di atas + penggelap halus hanya di bawah. Ganti lagu = warna dan foto crossfade 1,4 dtk.
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

## 8. Bug yang sudah ditemukan dan diperbaiki (jangan terulang)
| Gejala | Penyebab | Perbaikan |
|---|---|---|
| Build gagal `Conflicting declarations` | `val top` dan `val accent` ditulis dua kali di `Screen()` | Hanya satu deklarasi masing-masing |
| Build gagal `'val maxWidth' cannot be called in this context with an implicit receiver` | `maxWidth` dipakai di dalam Row/Column bersarang di `BoxWithConstraints` | `val w = maxWidth` lalu pakai `w` |
| Build gagal bentrok `onFailure` | `post()` bertipe Boolean (`= main.post{}`) dipakai sebagai body `override fun onFailure(...) = post(...)` | Body pakai blok `{ ... }`, bukan `=` |
| Relay APK sering putus | `OkHttpClient()` default readTimeout 10 dtk memutus WebSocket yang sepi; `onClosed` tidak ditangani | `readTimeout(0)`, `pingInterval(15s)`, tangani `onClosed`, backoff, abaikan socket lama |
| Komentar tidak masuk walau relay hidup | Server membuat koneksi TikTok baru tiap APK sambung ulang (rate limit) | v2.1: satu koneksi per username dibagi, grace 90 dtk, auto-reconnect |
| Relay crash saat APK tersambung | `new TikTokLiveConnection(user)` tanpa opsi (v2.5.0 butuh objek) | Selalu `new TikTokLiveConnection(user, {})` |
| Log PC penuh komentar tapi APK kosong | `d.comment` = `undefined`; JSON membuang field itu; `getString("text")` melempar lalu ditelan `runCatching` | `pickText()`/`pickName()`, dump data mentah, `optString` di APK |
| Latar hanya bercak warna | Sampul diperkecil ke 32x32 lalu ditimpa gradasi + lapisan gelap tebal | Sampul 240px, blur 14dp (Android 12+), lapisan gelap tipis |
| Username/relay hilang setelah update APK | Kunci debug CI berbeda tiap build, harus uninstall | Isi ulang; solusi tetap: keystore sendiri (belum) |
| Perintah `pkill -f "node server.js"` mematikan shell sandbox | Pola cocok dengan perintah itu sendiri | Jangan dipakai di sandbox |

## 9. Kode yang JANGAN diubah (sudah terbukti / rapuh)
Ubah hanya jika benar-benar perlu, dan uji di CI setelahnya.
- **`TikTokLive.kt`**: `OkHttpClient.Builder().readTimeout(0).pingInterval(15s)` (jangan kembali ke `OkHttpClient()`); `private fun post(...) = main.post{...}` bertipe Boolean, jadi semua override listener (`onFailure`, `onClosed`, `onClosing`) harus berbody blok `{ }`; logika `lost()` yang mengabaikan `webSocket !== ws`; `optString` + cek `isNotBlank` pada pesan relay; `nextId` untuk key komentar.
- **`MainActivity.kt`**: `val w = maxWidth` di `BoxWithConstraints`; satu `top`/`accent`; scroll lirik `animateScrollBy(item.offset)` lalu `scrollToItem(idx)` (koreksi posisi) dan `contentPadding(top = maxHeight*.36f, bottom = maxHeight)`; `Modifier.blur` selalu dibungkus `Build.VERSION.SDK_INT >= 31`; komentar: `LazyColumn(reverseLayout = true, userScrollEnabled = false)` + `animateItem(...)` + `GlassComment` (Animatable pegas, alpha per umur); pesan `sistem` dihapus saat komentar asli masuk dan tidak kedaluwarsa; vinyl digambar lewat kode (keputusan Opsi B, jangan pakai PNG).
- **`AndroidManifest.xml`**: `usesCleartextTraffic="true"` (wajib untuk `ws://`), izin INTERNET, deklarasi `MusicListener` dengan izin Notification Listener.
- **`server/server.js`**: `new TikTokLiveConnection(user, opts)` dengan objek opsi; peta `rooms` per username (huruf kecil) + `GRACE_MS`; ping 15 dtk + `terminate()` klien mati; `pickText/pickName` (tambahkan field baru ke `TEXT_KEYS`, jangan hapus); mode `--demo`; pesan status memakai user `sistem`.
- **Build/CI**: Kotlin 2.0.20, AGP 8.5.2, Gradle 8.9, Java 17, Compose BOM 2024.09.00, compile/target SDK 34 (CI satu-satunya cara build, jangan naikkan versi tanpa alasan); `permissions: contents: write`; langkah "Ringkasan error".
- **Keamanan**: jangan tulis token ke file/config (pakai `http.extraheader`); minta pengguna mencabut token setelah selesai.

## 10. Bug/risiko yang MASIH ada (belum diperbaiki)
1. **Layar HP bisa mati saat live** (tidak ada `FLAG_KEEP_SCREEN_ON`). Usul: tambahkan di `MainActivity`.
2. **Lirik hanya `/api/get` (cocok persis)**. Judul seperti "(feat. X)" atau "- Remastered" gagal ditemukan. Usul: fallback `/api/search` lalu pilih hasil dengan durasi terdekat.
3. **`MusicListener` memilih sesi media pertama dari aplikasi apa pun** (bisa YouTube/Chrome, bukan musik); tidak ada filter paket. Listener `addOnActiveSessionsChangedListener` didaftarkan tiap `onListenerConnected` tanpa dilepas (bocor). `publish()` berhenti saat `ctrl == null` sehingga data lagu lama tertinggal setelah pemutar ditutup.
4. **Library TikTok Java (`jwdeveloper ... 1.8.5-Release`) masih jadi dependensi** padahal mode langsung tidak andal; memperbesar APK. Boleh dihapus kalau relay sudah dipakai penuh (hapus juga `startDirect()`).
5. **`versionCode = 1` tetap + kunci debug berubah tiap build** -> update harus uninstall. Usul: keystore tetap + naikkan versionCode otomatis.
6. **Workflow membuat rilis tiap push**, termasuk commit yang hanya mengubah `SKILL.md`/`server/`. Nomor rilis (`v1.<run_number>`) melompat karena run gagal ikut dihitung. Usul: `paths-ignore` untuk `SKILL.md` dan `server/**`, atau tulis `[skip ci]` di pesan commit.
7. **Relay tanpa autentikasi dan memakai `ws://` tanpa enkripsi**: aman hanya di Wi-Fi rumah. Server tidak membatasi jumlah username per PC.
8. **Rate limit Euler Stream** (tanpa API key) bisa membuat koneksi TikTok gagal; kalau terjadi, buat API key dan `set SIGN_API_KEY=...`.
9. **Efek kaca belum membiaskan latar** dan avatar komentar masih huruf pertama.
10. **Semua perubahan terbaru (v1.8 dan v1.9) belum diuji di HP**; yang sudah terbukti hanya: relay PC menerima komentar TikTok (log pengguna).

## Catatan perubahan
- v1.3: build pertama sukses, ikon, rilis otomatis.
- v1.4-1.5: UI putih, sampul besar, transisi lirik/komentar; perbaikan `onFailure` dan deklarasi ganda `top`/`accent`.
- v1.6: transisi komentar (pegas masuk, redup menurut umur, hilang 20 dtk), transisi lirik ease-out + blur jarak, status koneksi TikTok; fix `maxWidth`.
- (sesi berikut, commit `55e0a91`) relay dipindah ke `tiktok-live-connector` 2.x.
- Sesi terbaru: **penyebab relay sering putus** = OkHttp readTimeout default 10 dtk memutus WebSocket yang sepi, dan tiap sambung ulang APK membuat koneksi TikTok baru di server (kena rate limit, komentar tidak datang). Perbaikan APK: `readTimeout(0)`, `pingInterval(15s)`, tangani `onClosed`, backoff, abaikan socket lama. Perbaikan server: v2.1 (koneksi dibagi per username, grace 90 dtk, auto-reconnect, ping, `--demo`). Latar kini foto sampul 240px (bukan bercak warna 32px) + blur 14dp (Android 12+), lapisan gelap tipis. Belum diuji di HP.
- Sesi berikut: log PC pengguna menunjukkan relay menerima ratusan event chat TikTok tapi `d.comment` = `undefined` (JSON.stringify membuang field itu, APK `getString("text")` melempar exception lalu ditelan `runCatching`, jadi komentar tidak tampil). Perbaikan: server memakai `pickText()` (coba `comment/content/text/message/msg` lalu field apa pun yang namanya mirip) dan `pickName()` (uniqueId/displayId/nickname); kalau teks tetap kosong, server mencetak data mentah event sekali (`PERINGATAN: teks komentar kosong. Data mentah: ...`) dan mengirim pesan status ke APK. APK memakai `optString` dan mengabaikan pesan kosong. Jika masih kosong, baca "Data mentah" di log PC untuk tahu nama field yang benar lalu tambahkan ke `TEXT_KEYS`. Belum diuji di HP.
- Sesi terbaru: tambah bagian 8 (riwayat bug), 9 (kode yang jangan diubah), 10 (bug/risiko yang masih ada) setelah peninjauan `MusicListener.kt`, `Lyrics.kt`, workflow, dan manifest. Tidak ada perubahan kode.
- Sesi terbaru: latar dibuat transparan dan mengikuti warna/gambar sampul tiap lagu (lihat bagian 2). Perubahan hanya di `MainActivity.kt` (`vivid()`, blok palet, blok latar). Belum diuji di HP; kalau warna terlalu terang/gelap, atur `vivid()` (minS, lMin, lMax) dan alpha foto (.72) lebih dulu.
- Sesi terbaru: transisi komentar masuk dibuat lebih terlihat. Komentar asli sekarang lewat **antrean** (`pending`, maks 8; dikeluarkan satu per satu tiap 850 dtk saat sepi, 600/380 dtk saat ramai) supaya animasi tidak bertumpuk saat live ramai (log pengguna ~3-5 komentar/dtk); pesan `sistem` tetap langsung tampil. Kartu baru mendapat **kilau cahaya yang menyapu sekali** (`sh`, `drawWithContent`) selain animasi pegas yang sudah ada. Jangan hapus antrean: tanpanya komentar ramai terlihat seperti berkedip. Belum diuji di HP.
