package com.layar.live

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen() } }
    }
}

// ---------- Liquid glass ----------
fun Modifier.liquidGlass(shape: Shape) = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(Color.White.copy(.20f), Color.White.copy(.05f))))
    .drawBehind { // kilau (specular) di bagian atas
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.Transparent), endY = size.height * .5f))
    }
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.75f), Color.White.copy(.08f), Color.White.copy(.4f))), shape)

/** Warna dari sampul dibuat cukup hidup (tidak pucat / terlalu gelap) supaya latar terlihat bagus; sampul abu-abu tidak dipaksa berwarna. */
private fun vivid(argb: Int, minS: Float = .5f, lMin: Float = .34f, lMax: Float = .56f): Color {
    val h = FloatArray(3); ColorUtils.colorToHSL(argb, h)
    if (h[1] > .08f) h[1] = h[1].coerceIn(minS, .95f)
    h[2] = h[2].coerceIn(lMin, lMax)
    return Color(ColorUtils.HSLToColor(h))
}

private fun fmt(ms: Long) = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val track by NowPlaying.state.collectAsState()
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    var user by remember { mutableStateOf(prefs.getString("user", "")!!) }
    var relay by remember { mutableStateOf(prefs.getString("relay", "")!!) }
    var dialog by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(true) }
    var lines by remember { mutableStateOf(emptyList<Line>()) }
    var msg by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<Comment>() }
    val pending = remember { ArrayDeque<Comment>() }   // antrean: komentar ditampilkan satu per satu supaya transisinya terlihat
    var colors by remember { mutableStateOf(Color(0xFF8A4A2B) to Color(0xFF2A3560)) }
    var angle by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    var pos by remember { mutableLongStateOf(0L) }
    var bg by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            allowed = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            delay(1000)
        }
    }
    LaunchedEffect(track.title, track.artist) {
        lines = emptyList()
        if (track.title.isBlank()) { msg = "Putar lagu di Spotify / YT Music"; return@LaunchedEffect }
        msg = "Mencari lirik…"
        lines = Lyrics.fetch(track.title, track.artist, track.dur)
        msg = if (lines.isEmpty()) "Lirik tidak ditemukan" else ""
    }
    LaunchedEffect(track.cover) {
        track.cover?.let { b ->
            withContext(Dispatchers.Default) {
                val p = Palette.from(b).generate()
                val v = p.getVibrantColor(p.getDominantColor(0xFF8A4A2B.toInt()))
                val m = p.getDarkVibrantColor(p.getMutedColor(v))
                colors = vivid(v) to vivid(m, minS = .4f, lMin = .12f, lMax = .28f)   // atas: terang hidup, bawah: gelap senada
                // sampul tetap cukup tajam supaya fotonya terlihat; blur halus ditambah di Composable (Android 12+)
                runCatching { Bitmap.createScaledBitmap(b, 320, 320, true) }.getOrNull()?.let { bg = it }
            }
        }
    }
    DisposableEffect(user, relay) {
        var c: TikTokLive? = null
        if (user.isNotBlank()) {
            c = TikTokLive(relay, user) { n ->
                if (n.user == "sistem") {            // pesan status langsung tampil
                    comments.removeAll { it.user == "sistem" }
                    comments.add(n)
                    while (comments.size > 4) comments.removeAt(0)
                } else {
                    pending.addLast(n)
                    while (pending.size > 8) pending.removeFirst()   // live ramai: buang yang paling lama menunggu
                }
            }.also { it.start() }
        }
        onDispose { c?.stop() }
    }

    // keluarkan antrean satu per satu: santai kalau sepi, lebih cepat kalau ramai
    LaunchedEffect(Unit) {
        while (true) {
            val n = pending.removeFirstOrNull()
            if (n == null) { delay(80); continue }
            comments.removeAll { it.user == "sistem" }   // pesan status diganti komentar asli
            comments.add(n)
            while (comments.size > 4) comments.removeAt(0)
            delay(when { pending.size > 5 -> 380L; pending.size > 2 -> 600L; else -> 850L })
        }
    }

    // komentar yang sudah lama keluar sendiri dengan animasi
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = SystemClock.elapsedRealtime()
            comments.removeAll { it.user != "sistem" && now - it.at > 20_000 }
        }
    }

    val spin by animateFloatAsState(if (track.playing) 1f else 0f, tween(1500), label = "spin")
    val spinNow by rememberUpdatedState(spin)
    val trackNow by rememberUpdatedState(track)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n ->
            val dt = if (last == 0L) 0f else (n - last) / 1e9f
            last = n
            angle = (angle + spinNow * 90f * dt) % 360f   // 1 putaran / 4 detik, ada efek inersia
            phase += dt * 4f
            val t = trackNow
            pos = if (t.playing) t.pos + ((SystemClock.elapsedRealtime() - t.stamp) * t.speed).toLong() else t.pos
            if (t.dur > 0) pos = pos.coerceIn(0, t.dur)
        }
    }

    val top by animateColorAsState(colors.first, tween(1000), label = "top")
    val accent by animateColorAsState(colors.second, tween(1000), label = "acc")

    // gerak zoom sangat pelan pada foto latar supaya terasa hidup
    val kb by rememberInfiniteTransition(label = "kb").animateFloat(1.12f, 1.26f, infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "kbs")

    Box(
        Modifier.fillMaxSize()
            // dasar: gradasi warna dari sampul (berganti halus tiap lagu)
            .background(Brush.verticalGradient(listOf(top, Color(ColorUtils.blendARGB(top.toArgb(), accent.toArgb(), .55f)), accent)))
    ) {
        // foto sampul semi-transparan di atas warna dasar: warna sampul tetap tembus, foto tetap terlihat
        Crossfade(targetState = bg, modifier = Modifier.fillMaxSize(), animationSpec = tween(1400), label = "bg") { b ->
            if (b != null) Image(b.asImageBitmap(), null,
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = kb; scaleY = kb }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                contentScale = ContentScale.Crop, alpha = .72f, filterQuality = FilterQuality.High)
        }
        // kilau kaca lembut di atas, dan penggelap halus di bawah supaya komentar tetap terbaca
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.White.copy(.10f), .28f to Color.Transparent, .6f to Color.Black.copy(.10f), 1f to Color.Black.copy(.50f))))

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(20.dp))
                val dot by rememberInfiniteTransition(label = "dot").animateFloat(1f, .3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "d")
                Row(
                    Modifier.align(Alignment.CenterHorizontally).liquidGlass(RoundedCornerShape(50))
                        .clickable { dialog = true }.padding(horizontal = 26.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF3B5C).copy(alpha = dot)))
                    Spacer(Modifier.width(10.dp))
                    Text("SEDANG DIPUTAR", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                }
                Spacer(Modifier.height(28.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = maxWidth
                    val cover = w * .46f                       // sampul lebih besar, ikut lebar layar
                    val peek by animateDpAsState(if (track.playing) w * .08f else w * .035f, tween(900), label = "peek")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(cover + w * .08f).height(cover)) {
                            Vinyl(angle, accent, Modifier.size(cover).offset(x = peek))
                            Cover(track.cover, Modifier.size(cover))
                        }
                        Spacer(Modifier.width(10.dp))
                        LyricsView(lines, pos, msg, Modifier.weight(1f).height(cover))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(track.title.ifBlank { "Judul Lagu" }, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.White,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist.ifBlank { "Nama Artis" }, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 17.sp, color = Color.White.copy(.85f), maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Waveform(track.title, if (track.dur > 0) pos.toFloat() / track.dur else 0f, track.playing, phase)
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), fontSize = 13.sp, color = Color.White.copy(.85f))
                    Text(fmt(track.dur), fontSize = 13.sp, color = Color.White.copy(.85f))
                }
                Spacer(Modifier.height(4.dp))
                if (!allowed) {
                    Column(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(28.dp)).padding(18.dp)) {
                        Text("Aplikasi butuh izin Notification Access untuk membaca lagu yang sedang diputar.", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Buka Pengaturan") }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // komentar: menempel di bawah; komentar baru masuk dari bawah, yang lama naik lalu memudar
                val shown = if (comments.isEmpty())
                    listOf(Comment(-1, "sistem", if (user.isBlank()) "Ketuk “SEDANG DIPUTAR” untuk menghubungkan akun TikTok" else "Menunggu komentar dari @$user…"))
                else comments.reversed()
                LazyColumn(Modifier.weight(1f).fillMaxWidth().edgeFade(.22f, 1f), reverseLayout = true, userScrollEnabled = false) {
                    itemsIndexed(shown, key = { _, c -> c.id }) { i, c ->
                        GlassComment(c.user, c.text, i, Modifier.animateItem(
                            fadeInSpec = null,
                            // komentar lama naik dengan pegas halus saat ada yang baru masuk
                            placementSpec = spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                            fadeOutSpec = tween(500)))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (dialog) {
        var u by remember { mutableStateOf(user) }; var r by remember { mutableStateOf(relay) }
        AlertDialog(onDismissRequest = { dialog = false },
            title = { Text("Sinkron akun TikTok") },
            text = {
                Column {
                    OutlinedTextField(u, { u = it }, label = { Text("@username yang sedang live") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(r, { r = it }, label = { Text("Server relay (kosongkan = langsung dari HP)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    user = u.trim(); relay = r.trim(); comments.clear()
                    prefs.edit().putString("user", user).putString("relay", relay).apply(); dialog = false
                }) { Text("Hubungkan") }
            })
    }
}

// pudar di tepi atas/bawah (dipakai lirik dan daftar komentar)
fun Modifier.edgeFade(topEnd: Float, bottomStart: Float) = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, topEnd to Color.Black, bottomStart to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

@Composable
fun GlassComment(user: String, text: String, age: Int, modifier: Modifier = Modifier) {
    // masuk: naik dari bawah dengan pegas (sedikit memantul) + membesar + muncul
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = .68f, stiffness = Spring.StiffnessLow)) }
    // makin lama komentar, makin redup dan kecil (menuju keluar)
    // kilau cahaya menyapu kartu satu kali saat baru masuk
    val sh = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(120); sh.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val ageA by animateFloatAsState(when (age) { 0 -> 1f; 1 -> .88f; 2 -> .7f; else -> .5f }, tween(500), label = "ca")
    val ageS by animateFloatAsState(when (age) { 0 -> 1f; 1 -> .97f; 2 -> .94f; else -> .91f }, tween(500), label = "cs")
    Row(
        modifier.padding(top = 10.dp).fillMaxWidth()
            .graphicsLayer {
                val e = p.value
                alpha = (e * 1.8f).coerceIn(0f, 1f) * ageA
                translationY = (1f - e) * 70.dp.toPx()
                val sc = (.82f + .18f * e) * ageS
                scaleX = sc; scaleY = sc
            }
            .liquidGlass(RoundedCornerShape(50))
            .drawWithContent {
                drawContent()
                if (sh.value in 0.01f..0.99f) {
                    val x = size.width * (sh.value * 1.5f - .25f)
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(.38f), Color.Transparent), startX = x - 140f, endX = x + 140f))
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(.35f)), contentAlignment = Alignment.Center) {
            Text(user.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("@$user") }
            append(" $text")
        }, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Cover(bmp: Bitmap?, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.shadow(16.dp, shape).clip(shape)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFC0623A), Color(0xFF3A4A8A)))))
    }
}

@Composable
fun Vinyl(angle: Float, label: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        rotate(angle) {
            drawCircle(Color(0xFF0B0B0B), r)
            for (i in 0..14) drawCircle(Color.White.copy(if (i % 2 == 0) .07f else .03f), r * (.42f + i * .038f), style = Stroke(1f))
            drawCircle(Brush.linearGradient(listOf(lerp(label, Color.White, .25f), label)), r * .36f)
            drawCircle(Color(0xFF111111), r * .04f)
        }
        // pantulan cahaya statis (tidak ikut berputar)
        val t = Color.Transparent; val w = Color.White
        drawCircle(Brush.sweepGradient(listOf(t, w.copy(.22f), t, t, w.copy(.14f), t, t)), r)
    }
}

@Composable
fun LyricsView(lines: List<Line>, pos: Long, msg: String, modifier: Modifier) {
    if (lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text(msg, color = Color.White.copy(.8f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val idx = lines.indexOfLast { it.t <= pos }.coerceAtLeast(0)
    val state = rememberLazyListState()
    var shownLines by remember { mutableStateOf<List<Line>?>(null) }
    var prevIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines, idx) {
        if (shownLines !== lines) {            // lagu baru: langsung ke posisi
            shownLines = lines; state.scrollToItem(idx); prevIdx = idx; return@LaunchedEffect
        }
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        if (item != null && abs(idx - prevIdx) <= 3) {
            // gulir mulus dengan kurva ease-out, lalu koreksi posisi tepat
            state.animateScrollBy(item.offset.toFloat(), tween(800, easing = CubicBezierEasing(.22f, 1f, .36f, 1f)))
            state.scrollToItem(idx)
        } else state.animateScrollToItem(idx)
        prevIdx = idx
    }
    BoxWithConstraints(modifier.edgeFade(.14f, .86f)) {
        LazyColumn(Modifier.fillMaxSize(), state = state, userScrollEnabled = false,
            contentPadding = PaddingValues(top = maxHeight * .36f, bottom = maxHeight)) {
            itemsIndexed(lines) { i, l ->
                val d = abs(i - idx)
                val a by animateFloatAsState(when (d) { 0 -> 1f; 1 -> .55f; 2 -> .34f; else -> .2f }, spring(stiffness = Spring.StiffnessLow), label = "la")
                val sc by animateFloatAsState(if (d == 0) 1f else .82f, spring(dampingRatio = .8f, stiffness = Spring.StiffnessLow), label = "ls")
                val bl by animateDpAsState(when (d) { 0 -> 0.dp; 1 -> 0.4.dp; 2 -> 1.2.dp; else -> 2.dp }, tween(600), label = "lb")
                Text(l.s, color = Color.White, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(.4f), Offset(0f, 2f), 8f)),
                    modifier = Modifier.padding(vertical = 6.dp)
                        .graphicsLayer { alpha = a; scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(0f, .5f) }
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(bl, BlurredEdgeTreatment.Unbounded) else Modifier))
            }
        }
    }
}

@Composable
fun Waveform(seed: String, progress: Float, playing: Boolean, phase: Float) {
    val n = 27
    val base = remember(seed) { Random(seed.hashCode()).let { r -> List(n) { .25f + .75f * r.nextFloat() } } }
    Canvas(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 8.dp)) {
        val gap = size.width / n
        for (i in 0 until n) {
            val h = size.height * base[i] * if (playing) .88f + .12f * sin(phase + i * .7f) else 1f
            val c = if ((i + 1f) / n <= progress) Color.White else Color.White.copy(.35f)
            drawLine(c, Offset(gap * (i + .5f), size.height / 2 - h / 2), Offset(gap * (i + .5f), size.height / 2 + h / 2),
                strokeWidth = gap * .45f, cap = StrokeCap.Round)
        }
    }
}
