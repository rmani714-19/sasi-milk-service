#!/usr/bin/env node
'use strict';
const http = require('http');
const { WebSocketServer } = require('ws');
const config = require('./config');
const { Scale } = require('./scale');

const LEVELS = { debug: 10, info: 20, warn: 30, error: 40 };
const min = LEVELS[config.logLevel] || 20;
const log = {
  debug: (m) => min <= 10 && console.log(ts(), 'DEBUG', m),
  info: (m) => min <= 20 && console.log(ts(), 'INFO ', m),
  warn: (m) => min <= 30 && console.warn(ts(), 'WARN ', m),
  error: (m) => console.error(ts(), 'ERROR', m),
};
function ts() {
  return new Date().toISOString();
}

const scale = new Scale(config, log);

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  };
}
function json(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json', ...corsHeaders() });
  res.end(JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders());
    return res.end();
  }
  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname.replace(/\/+$/, '') || '/';

  if (req.method === 'GET' && (path === '/' || path === '/health')) {
    const s = scale.status();
    return json(res, 200, {
      ok: true,
      service: 'sasi-weight-bridge',
      version: require('../package.json').version,
      mock: s.mock,
      open: s.open,
      connected: s.connected,
      lastFrameAgeMs: scale.currentWeight().at ? Date.now() - scale.currentWeight().at : null,
    });
  }

  if (req.method === 'GET' && path === '/weight') {
    const w = scale.currentWeight();
    if (w.at === 0) return json(res, 200, { weightKg: null, unit: w.unit, stable: false, raw: '', at: 0 });
    return json(res, 200, w);
  }

  if (req.method === 'GET' && path === '/status') {
    return json(res, 200, scale.status());
  }

  if (req.method === 'POST' && path === '/tare') {
    const r = scale.tare();
    if (r.ok) return json(res, 200, { ok: true, sent: r.sent });
    return json(res, 501, {
      ok: false,
      reason: r.reason,
      instruction: 'Remote tare is not available for this scale — press the ZERO / TARE button on the weighing machine.',
    });
  }

  json(res, 404, { ok: false, error: 'not_found' });
});

// --- WebSocket: push weight + status frames -------------------------------
const wss = new WebSocketServer({ server });
wss.on('connection', (ws) => {
  log.debug('ws client connected (' + wss.clients.size + ' total)');
  send(ws, { type: 'status', ...scale.status() });
  send(ws, { type: 'weight', ...scale.currentWeight() });
  ws.on('error', () => {});
});
function send(ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}
function broadcast(obj) {
  const data = JSON.stringify(obj);
  for (const ws of wss.clients) if (ws.readyState === ws.OPEN) ws.send(data);
}
scale.on('frame', (w) => broadcast({ type: 'weight', ...w }));
scale.on('status', (s) => broadcast({ type: 'status', ...s }));

// heartbeat so dead sockets are dropped
setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.readyState === ws.OPEN) ws.ping();
  }
}, 15000).unref();

server.listen(config.httpPort, () => {
  log.info(`weight bridge listening on http://localhost:${config.httpPort}  (ws on same port)`);
  if (!config.mock && !config.serial.path) {
    log.warn('SCALE_PORT is not set — the bridge is up but has no scale. Set it in .env (see `npm run ports`).');
  }
  scale.start();
});

function shutdown() {
  log.info('shutting down');
  scale.stop();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
