// npm i ws tiktok-live-connector && node server.js
const { WebSocketServer } = require('ws');
const { WebcastPushConnection } = require('tiktok-live-connector');
const wss = new WebSocketServer({ port: 8080 });
wss.on('connection', (ws, req) => {
  const user = new URL(req.url, 'http://x').searchParams.get('user');
  const c = new WebcastPushConnection(user);
  const send = (u, t) => ws.readyState === 1 && ws.send(JSON.stringify({ user: u, text: t }));
  c.connect().catch(e => send('sistem', 'Gagal tersambung: ' + e));
  c.on('chat', d => send(d.uniqueId, d.comment));
  ws.on('close', () => c.disconnect());
});
console.log('Relay jalan di ws://0.0.0.0:8080');
