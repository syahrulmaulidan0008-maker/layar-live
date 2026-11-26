package com.layar.live

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
                val d = p.getDominantColor(0xFF8A4A2B.toInt())
                val m = p.getMutedColor(p.getVibrantColor(0xFF2A3560.toInt()))
                colors = Color(ColorUtils.blendARGB(d, 0, .2f)) to Color(m)
                // sampul diperkecil lalu diperbesar lagi = efek blur yang jalan di semua versi Android
                runCatching { Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, 96, 96, true), 32, 32, true) }.getOrNull()?.let { bg = it }
            }
        }
    }
    DisposableEffect(user, relay) {
        var c: TikTokLive? = null
        if (user.isNotBlank()) {
            c = TikTokLive(relay, user) { comments.add(it); if (comments.size > 4) comments.removeAt(0) }.also { it.start() }
        }
        onDispose { c?.stop() }
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
    val top by animateColorAsState(colors.first, tween(1000), label = "top")
    val accent by animateColorAsState(colors.second, tween(1000), label = "acc")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, Color(ColorUtils.blendARGB(top.toArgb(), 0, .6f)), Color(0xFF05070D)), endY = 2200f))
            .background(Brush.radialGradient(listOf(accent.copy(.35f), Color.Transparent), center = Offset(900f, 1700f), radius = 900f))
    ) {
        // latar: sampul lagu yang diburamkan, berganti halus (crossfade) tiap ganti lagu
        Crossfade(targetState = bg, modifier = Modifier.fillMaxSize(), animationSpec = tween(1200), label = "bg") { b ->
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .85f, filterQuality = FilterQuality.High)
        }
        // lapisan gelap tipis supaya teks putih tetap terbaca
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.25f), Color.Black.copy(.40f), Color.Black.copy(.75f)))))

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
                    val cover = maxWidth * .46f                       // sampul lebih besar, ikut lebar layar
                    val peek by animateDpAsState(if (track.playing) maxWidth * .08f else maxWidth * .035f, tween(900), label = "peek")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(cover + maxWidth * .08f).height(cover)) {
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
                    items(shown, key = { it.id }) { c ->
                        GlassComment(c.user, c.text, Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(stiffness = Spring.StiffnessLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                            fadeOutSpec = tween(400)))
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
fun GlassComment(user: String, text: String, modifier: Modifier = Modifier) {
    // animasi masuk: naik dari bawah + membesar halus + muncul
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    Row(
        modifier.padding(top = 10.dp).fillMaxWidth()
            .graphicsLayer {
                alpha = p.value
                translationY = (1f - p.value) * 80.dp.toPx()
                val sc = .9f + .1f * p.value
                scaleX = sc; scaleY = sc
            }
            .liquidGlass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 12.dp),
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
    LaunchedEffect(lines, idx) {
        if (shownLines !== lines) { shownLines = lines; state.scrollToItem(idx) }   // lagu baru: langsung ke posisi
        else state.animateScrollToItem(idx)                                          // ganti baris: menggulir halus
    }
    BoxWithConstraints(modifier.edgeFade(.14f, .86f)) {
        LazyColumn(Modifier.fillMaxSize(), state = state, userScrollEnabled = false,
            contentPadding = PaddingValues(top = maxHeight * .36f, bottom = maxHeight)) {
            itemsIndexed(lines) { i, l ->
                val d = abs(i - idx)
                val a by animateFloatAsState(when (d) { 0 -> 1f; 1 -> .5f; 2 -> .32f; else -> .18f }, tween(600), label = "la")
                val sc by animateFloatAsState(if (d == 0) 1f else .8f, tween(600), label = "ls")
                Text(l.s, color = Color.White, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp).graphicsLayer {
                        alpha = a; scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(0f, .5f)
                    })
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
