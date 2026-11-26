// npm i && node server.js
// Untuk tiktok-live-connector 2.x. Jika kena rate limit, buat API key di eulerstream.com
// lalu jalankan: set SIGN_API_KEY=kunci  (Windows cmd) sebelum node server.js
const { WebSocketServer } = require('ws');
const { TikTokLiveConnection, WebcastEvent } = require('tiktok-live-connector');

process.on('uncaughtException', e => console.log('Error:', e && e.message ? e.message : e));
process.on('unhandledRejection', e => console.log('Ditolak:', e && e.message ? e.message : e));

const wss = new WebSocketServer({ port: 8080 });

wss.on('connection', (ws, req) => {
  const user = new URL(req.url, 'http://x').searchParams.get('user');
  console.log('APK tersambung, username:', user);
  const opts = process.env.SIGN_API_KEY ? { signApiKey: process.env.SIGN_API_KEY } : {};
  const c = new TikTokLiveConnection(user, opts);
  const send = (u, t) => ws.readyState === 1 && ws.send(JSON.stringify({ user: u, text: t }));

  c.connect()
    .then(() => { console.log('Tersambung ke live TikTok:', user); send('sistem', 'Tersambung ke live ' + user); })
    .catch(e => {
      const m = e && e.message ? e.message : String(e);
      console.log('Gagal TikTok:', m);
      send('sistem', 'Gagal tersambung: ' + m);
    });

  c.on(WebcastEvent.CHAT, d => send((d.user && d.user.uniqueId) || d.uniqueId || '?', d.comment));
  ws.on('close', () => { console.log('APK putus'); try { c.disconnect(); } catch (e) {} });
});

console.log('Relay jalan di ws://0.0.0.0:8080');
