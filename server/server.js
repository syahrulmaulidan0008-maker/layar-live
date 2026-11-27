// Relay komentar TikTok Live -> APK Layar Live  (v2.1)
//   node server.js          -> mode normal (butuh akun yang sedang live)
//   node server.js --demo   -> komentar palsu tiap 3 detik, untuk tes jalur PC -> APK
// Opsional: set SIGN_API_KEY=kunci  (kunci dari eulerstream.com, kalau kena rate limit)
const os = require('os');
const { EventEmitter } = require('events');
const { WebSocketServer } = require('ws');
const { TikTokLiveConnection, WebcastEvent, ControlEvent } = require('tiktok-live-connector');

const PORT = Number(process.env.PORT) || 8080;
const DEMO = process.argv.includes('--demo');
const GRACE_MS = 90_000;                  // koneksi TikTok dipertahankan setelah APK putus, supaya sambung ulang APK tidak memicu rate limit
const stamp = () => new Date().toLocaleTimeString('id-ID');
const log = (...a) => console.log(`[${stamp()}]`, ...a);
process.on('uncaughtException', (e) => log('Error:', e?.message || e));
process.on('unhandledRejection', (e) => log('Ditolak:', e?.message || e));

// <pick>
// Ambil teks & nama dari event chat dengan beberapa kemungkinan nama field (format TikTok bisa berubah)
const TEXT_KEYS = ['comment', 'content', 'text', 'message', 'msg'];
function pickText(d) {
  for (const k of TEXT_KEYS) if (typeof d?.[k] === 'string' && d[k].trim()) return d[k].trim();
  for (const [k, v] of Object.entries(d || {})) if (typeof v === 'string' && v.trim() && /comment|content|text|message|msg/i.test(k)) return v.trim();
  return '';
}
function pickName(d) {
  const u = d?.user || d?.sender || {};
  return u.uniqueId || u.displayId || u.nickname || d?.uniqueId || d?.nickname || 'penonton';
}
const dump = (o) => JSON.stringify(o, (k, v) => (typeof v === 'bigint' ? String(v) : v)).slice(0, 1500);
// </pick>

// ---- satu koneksi TikTok per username, dibagi ke semua APK yang memantau username itu ----
const rooms = new Map();

function makeConn(user) {
  if (!DEMO) return new TikTokLiveConnection(user, process.env.SIGN_API_KEY ? { signApiKey: process.env.SIGN_API_KEY } : {});
  const f = new EventEmitter(); let t;           // koneksi palsu untuk --demo
  f.connect = async () => { let n = 0; t = setInterval(() => f.emit(WebcastEvent.CHAT, { user: { uniqueId: 'penonton' + (n % 5 + 1) }, comment: 'komentar tes ke-' + (++n) }), 3000); return { roomId: 'demo' }; };
  f.disconnect = () => clearInterval(t);
  return f;
}

function getRoom(user) {
  let r = rooms.get(user);
  if (r) { clearTimeout(r.idle); r.idle = null; return r; }
  r = { user, clients: new Set(), conn: null, retry: null, idle: null, fails: 0, status: 'Menghubungkan ke TikTok…', dead: false };
  rooms.set(user, r);
  connectRoom(r);
  return r;
}

const broadcast = (r, u, t) => { const m = JSON.stringify({ user: u, text: t }); r.clients.forEach((ws) => ws.readyState === 1 && ws.send(m)); };
const info = (r, t) => { r.status = t; broadcast(r, 'sistem', t); };

function scheduleRetry(r, ms) {
  if (r.dead) return;
  try { r.conn?.disconnect(); } catch {}
  clearTimeout(r.retry);
  r.retry = setTimeout(() => connectRoom(r), ms);
}

async function connectRoom(r) {
  if (r.dead) return;
  const conn = (r.conn = makeConn(r.user));
  conn.on(WebcastEvent.CHAT, (d) => {
    const name = pickName(d), text = pickText(d);
    if (!text) {                                   // isi komentar tidak terbaca: tampilkan data mentah sekali supaya bisa diperbaiki
      if (!r.warned) { r.warned = true; log('PERINGATAN: teks komentar kosong. Data mentah:', dump(d)); info(r, 'Komentar masuk tapi isinya tidak terbaca (format TikTok berubah).'); }
      return;
    }
    log(`@${name}: ${text}`);
    broadcast(r, name, text);
  });
  conn.on(WebcastEvent.STREAM_END, () => { info(r, `Live @${r.user} berakhir. Menunggu live lagi…`); scheduleRetry(r, 15000); });
  conn.on(ControlEvent.DISCONNECTED, () => {
    if (r.dead || r.conn !== conn) return;
    info(r, 'Koneksi TikTok terputus, menyambung ulang…');
    scheduleRetry(r, 5000);
  });
  conn.on(ControlEvent.ERROR, (e) => log('error TikTok:', e?.message || e));
  try {
    const st = await conn.connect();
    r.fails = 0;
    log(`Tersambung ke live @${r.user} (room ${st?.roomId})`);
    info(r, `Tersambung ke @${r.user}, menunggu komentar…`);
  } catch (e) {
    const msg = String(e?.message || e);
    r.fails++;
    log('Gagal TikTok:', msg);
    info(r, /offline|not live/i.test((e?.name || '') + msg)
      ? `@${r.user} sedang tidak live. Mencoba lagi…`
      : 'Gagal ke TikTok: ' + msg.slice(0, 70));
    scheduleRetry(r, Math.min(8000 * r.fails, 60000));   // jeda makin lama supaya tidak kena rate limit
  }
}

// ---- server WebSocket untuk APK ----
const wss = new WebSocketServer({ port: PORT });
wss.on('connection', (ws, req) => {
  const user = (new URL(req.url, 'http://x').searchParams.get('user') || '').replace(/^@/, '').trim().toLowerCase();
  if (!user) { ws.send(JSON.stringify({ user: 'sistem', text: 'Username kosong, isi @username yang sedang live.' })); return ws.close(); }
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });
  ws.on('error', () => {});
  const r = getRoom(user);
  r.clients.add(ws);
  log(`APK tersambung (@${user}), jumlah APK: ${r.clients.size}`);
  ws.send(JSON.stringify({ user: 'sistem', text: r.status }));
  ws.on('close', () => {
    r.clients.delete(ws);
    log(`APK putus (@${user})`);
    if (r.clients.size === 0) r.idle = setTimeout(() => {   // tutup koneksi TikTok kalau tidak ada APK lagi
      r.dead = true; clearTimeout(r.retry); try { r.conn?.disconnect(); } catch {}
      rooms.delete(user); log(`Koneksi @${user} ditutup (tidak ada APK)`);
    }, GRACE_MS);
  });
});

// ping tiap 15 detik, buang klien yang mati (APK ditutup paksa / Wi-Fi putus)
setInterval(() => wss.clients.forEach((ws) => {
  if (!ws.isAlive) return ws.terminate();
  ws.isAlive = false; ws.ping();
}), 15000);

console.log('=========================================');
console.log(` Relay Layar Live berjalan${DEMO ? '  [MODE DEMO: komentar palsu]' : ''}`);
console.log(' Isi kolom "Server relay" di APK dengan salah satu:');
for (const list of Object.values(os.networkInterfaces()))
  for (const n of list || []) if (n.family === 'IPv4' && !n.internal) console.log(`   ws://${n.address}:${PORT}`);
console.log(' (pilih yang sejaringan Wi-Fi dengan HP, biasanya 192.168.x.x)');
console.log('=========================================');
