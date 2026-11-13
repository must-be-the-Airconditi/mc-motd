'use strict';

/**
 * 网页版：本地 HTTP 服务
 *  - GET /            前端页面
 *  - GET /api/ping    查询接口（服务端实现 TCP Ping，浏览器没有裸 socket）
 *
 * 只监听 127.0.0.1，不对外暴露；带简单的限流和缓存。
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { ping } = require('./slp');
const { t, normalizeLang } = require('./i18n');
const { buildResult } = require('./motd');

const PUBLIC_DIR = path.join(__dirname, '..', 'public');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

const CACHE_TTL = 20 * 1000;
const CACHE_MAX = 300;
const RATE_LIMIT = 40;      // 每个 IP 每分钟
const RATE_WINDOW = 60 * 1000;
const MAX_CONCURRENT = 8;

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  res.end(body);
}

function sendText(res, status, text) {
  res.writeHead(status, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Content-Length': Buffer.byteLength(text),
  });
  res.end(text);
}

function createWebServer(options = {}) {
  const defaultProxy = options.proxy || process.env.MC_MOTD_PROXY || null;
  const defaultTimeout = clamp(Number(options.timeout) || 5000, 500, 20000);

  const cache = new Map();
  const inflight = new Map();
  const rateBuckets = new Map();
  let concurrent = 0;

  function allowedProxy(value) {
    if (!value) return null;
    const text = String(value).trim();
    if (!/^(socks5h?|socks4a?|https?):\/\//i.test(text)) return null;
    if (text.length > 300) return null;
    return text;
  }

  function rateLimited(ip) {
    const now = Date.now();
    const bucket = rateBuckets.get(ip) || [];
    const fresh = bucket.filter((ts) => now - ts < RATE_WINDOW);
    fresh.push(now);
    rateBuckets.set(ip, fresh);
    if (rateBuckets.size > 500) rateBuckets.clear();
    return fresh.length > RATE_LIMIT;
  }

  async function handlePing(req, res, url) {
    const lang = normalizeLang(url.searchParams.get('lang'));
    const ip = req.socket.remoteAddress || 'local';
    if (rateLimited(ip)) {
      sendJson(res, 429, { ok: false, error: t(lang, 'web.tooFrequent') });
      return;
    }

    const address = (url.searchParams.get('address') || '').trim().slice(0, 253);
    if (!address) {
      sendJson(res, 400, { ok: false, error: t(lang, 'web.noAddress') });
      return;
    }

    const timeoutParam = Number(url.searchParams.get('timeout'));
    const timeout = clamp(Number.isFinite(timeoutParam) ? timeoutParam : defaultTimeout, 1000, 20000);
    const proxy = allowedProxy(url.searchParams.get('proxy')) || defaultProxy;
    const allowLegacy = url.searchParams.get('legacy') !== '0';

    const key = `${address}|${timeout}|${proxy || ''}`;
    const now = Date.now();
    const cached = cache.get(key);
    if (cached && now - cached.time < CACHE_TTL) {
      sendJson(res, 200, { ok: true, result: cached.result, cached: true });
      return;
    }

    if (inflight.has(key)) {
      try {
        const result = await inflight.get(key);
        sendJson(res, 200, { ok: true, result, cached: false });
      } catch (err) {
        sendJson(res, 502, { ok: false, error: err.message });
      }
      return;
    }

    if (concurrent >= MAX_CONCURRENT) {
      sendJson(res, 429, { ok: false, error: t(lang, 'web.tooMany') });
      return;
    }

    concurrent += 1;
    const task = ping(address, { timeout, proxy, legacy: allowLegacy, lang })
      .then((raw) => buildResult(raw))
      .finally(() => {
        concurrent -= 1;
        inflight.delete(key);
      });

    inflight.set(key, task);

    try {
      const result = await task;
      cache.set(key, { time: Date.now(), result });
      if (cache.size > CACHE_MAX) cache.delete(cache.keys().next().value);
      sendJson(res, 200, { ok: true, result, cached: false });
    } catch (err) {
      const isTimeout = /超时|timeout|timed out/i.test(err.message);
      sendJson(res, isTimeout ? 504 : 502, { ok: false, error: err.message });
    }
  }

  function serveStatic(req, res, pathname) {
    const relative = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '');
    const filePath = path.resolve(PUBLIC_DIR, relative);
    if (!filePath.startsWith(PUBLIC_DIR + path.sep) && filePath !== PUBLIC_DIR) {
      sendText(res, 403, 'Forbidden');
      return;
    }

    fs.readFile(filePath, (err, data) => {
      if (err) {
        sendText(res, 404, 'Not Found');
        return;
      }
      const type = MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream';
      res.writeHead(200, {
        'Content-Type': type,
        'Content-Length': data.length,
        'Cache-Control': 'no-store',
      });
      res.end(data);
    });
  }

  const server = http.createServer((req, res) => {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

    if (url.pathname === '/api/ping') {
      if (req.method !== 'GET') {
        sendJson(res, 405, { ok: false, error: t(normalizeLang(url.searchParams.get('lang')), 'web.getOnly') });
        return;
      }
      handlePing(req, res, url).catch((err) => {
        sendJson(res, 500, { ok: false, error: err.message || t(normalizeLang(url.searchParams.get('lang')), 'web.internal') });
      });
      return;
    }

    if (url.pathname === '/api/health') {
      sendJson(res, 200, { ok: true, proxy: Boolean(defaultProxy) });
      return;
    }

    if (req.method !== 'GET') {
      sendText(res, 405, 'Method Not Allowed');
      return;
    }

    serveStatic(req, res, url.pathname);
  });

  return server;
}

function startWeb(options = {}) {
  const host = options.host || '127.0.0.1';
  const port = Number(options.port) || 8720;
  const server = createWebServer(options);

  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      process.stderr.write(`\n  端口 ${port} 已被占用，换一个：motd --web --port 8721\n\n`);
      process.exit(1);
    }
    process.stderr.write(`\n  启动失败：${err.message}\n`);
    process.exit(1);
  });

  server.listen(port, host, () => {
    const shownHost = host === '0.0.0.0' || host === '::' ? '127.0.0.1' : host;
    const lines = [
      '',
      '  \x1b[38;2;85;255;85m▌\x1b[0m MOTD 查看器已启动',
      `  \x1b[2m➜\x1b[0m  http://${shownHost}:${port}`,
    ];
    if (options.proxy) lines.push(`  \x1b[2m代理 ${options.proxy}\x1b[0m`);
    lines.push('  \x1b[2mCtrl+C 退出\x1b[0m', '');
    process.stdout.write(`${lines.join('\n')}\n`);
  });

  const shutdown = () => {
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 500).unref();
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  return server;
}

module.exports = { createWebServer, startWeb };
