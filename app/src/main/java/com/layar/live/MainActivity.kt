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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
    val vinylX by animateDpAsState(if (track.playing) 65.dp else 45.dp, tween(900), label = "vx")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, Color(ColorUtils.blendARGB(top.toArgb(), 0, .6f)), Color(0xFF05070D)), endY = 2200f))
            .background(Brush.radialGradient(listOf(accent.copy(.35f), Color.Transparent), center = Offset(900f, 1700f), radius = 900f))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            val dot by rememberInfiniteTransition(label = "dot").animateFloat(1f, .3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "d")
            Row(
                Modifier.align(Alignment.CenterHorizontally).liquidGlass(RoundedCornerShape(50))
                    .clickable { dialog = true }.padding(horizontal = 26.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF3B5C).copy(alpha = dot)))
                Spacer(Modifier.width(10.dp))
                Text("SEDANG DIPUTAR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
            }
            Spacer(Modifier.height(44.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(195.dp).height(130.dp)) {
                    Vinyl(angle, accent, Modifier.size(130.dp).offset(x = vinylX))
                    Cover(track.cover, Modifier.size(130.dp))
                }
                Spacer(Modifier.width(10.dp))
                LyricsView(lines, pos, msg, Modifier.weight(1f))
            }
            Spacer(Modifier.height(36.dp))
            Text(track.title.ifBlank { "Judul Lagu" }, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { "Nama Artis" }, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 18.sp, color = Color.White.copy(.8f), maxLines = 1)
            Spacer(Modifier.height(28.dp))
            Waveform(track.title, if (track.dur > 0) pos.toFloat() / track.dur else 0f, track.playing, phase)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), fontSize = 13.sp, color = Color.White.copy(.8f))
                Text(fmt(track.dur), fontSize = 13.sp, color = Color.White.copy(.8f))
            }
            Spacer(Modifier.weight(1f))
            if (!allowed) {
                Column(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(28.dp)).padding(18.dp)) {
                    Text("Aplikasi butuh izin Notification Access untuk membaca lagu yang sedang diputar.", fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Buka Pengaturan") }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (comments.isEmpty()) GlassComment("sistem", "Ketuk “SEDANG DIPUTAR” untuk menghubungkan akun TikTok")
            comments.forEach { c -> key(c.id) { GlassComment(c.user, c.text, animate = true) } }
            Spacer(Modifier.height(12.dp))
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

@Composable
fun GlassComment(user: String, text: String, animate: Boolean = false) {
    val vis = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(vis, enter = fadeIn() + slideInVertically { it }) {
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(.35f)), contentAlignment = Alignment.Center) {
                Text(user.take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("@$user") }
                append(" $text")
            }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    val idx = lines.indexOfLast { it.t <= pos }.coerceAtLeast(0)
    AnimatedContent(idx, modifier, label = "lyric", transitionSpec = {
        (slideInVertically { it / 5 } + fadeIn()) togetherWith (slideOutVertically { -it / 5 } + fadeOut())
    }) { i ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in -2..2) {
                val s = lines.getOrNull(i + o)?.s ?: if (o == 0) msg else ""
                Text(s, color = Color.White.copy(when (abs(o)) { 0 -> 1f; 1 -> .55f; else -> .3f }),
                    fontSize = if (o == 0) 19.sp else 14.sp, fontWeight = if (o == 0) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = if (o == 0) 24.sp else 18.sp, maxLines = if (o == 0) 4 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun Waveform(seed: String, progress: Float, playing: Boolean, phase: Float) {
    val n = 27
    val base = remember(seed) { Random(seed.hashCode()).let { r -> List(n) { .25f + .75f * r.nextFloat() } } }
    Canvas(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 8.dp)) {
        val gap = size.width / n
        for (i in 0 until n) {
            val h = size.height * base[i] * if (playing) .88f + .12f * sin(phase + i * .7f) else 1f
            val c = if ((i + 1f) / n <= progress) Color.White else Color.White.copy(.35f)
            drawLine(c, Offset(gap * (i + .5f), size.height / 2 - h / 2), Offset(gap * (i + .5f), size.height / 2 + h / 2),
                strokeWidth = gap * .45f, cap = StrokeCap.Round)
        }
    }
}
