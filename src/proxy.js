'use strict';

/**
 * 极简代理隧道实现（零依赖）：
 *   socks5://user:pass@host:port
 *   socks4://host:port
 *   http://user:pass@host:port        （HTTP CONNECT）
 *   https://...                        （TLS 到代理，再 CONNECT）
 *
 * 只实现「穿透到目标 host:port」这一件事，够 Ping 用。
 */

const net = require('net');
const tls = require('tls');
const { t } = require('./i18n');

const PROXY_TYPES = new Set(['socks5', 'socks5h', 'socks4', 'socks4a', 'http', 'https']);

function safeDecode(value, lang = 'zh') {
  if (!value) return null;
  try {
    return decodeURIComponent(value);
  } catch {
    throw new Error(lang === 'en' ? 'invalid percent-encoding in proxy auth' : '代理认证信息含非法百分号编码');
  }
}

function normalizeProxy(input, lang = 'zh') {
  if (!input) return null;
  let raw = String(input).trim();
  if (!/^[a-z0-9]+:\/\//i.test(raw)) raw = `socks5://${raw}`;

  const url = new URL(raw);
  const type = url.protocol.replace(':', '').toLowerCase();
  if (!PROXY_TYPES.has(type)) throw new Error(t(lang, 'proxy.unsupported', type));

  const port = Number(url.port || (type === 'http' ? 80 : type === 'https' ? 443 : 1080));
  if (!url.hostname) throw new Error(t(lang, 'proxy.missingHost'));
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error(t(lang, 'proxy.badPort'));

  return {
    type,
    // URL 对 IPv6 会返回带方括号的 "[::1]"，net.connect 不认识这种写法
    host: url.hostname.replace(/^\[(.*)\]$/, '$1'),
    port,
    username: safeDecode(url.username, lang),
    password: safeDecode(url.password, lang),
  };
}

/** 带缓冲的 socket 读取器 */
class ByteReader {
  constructor(socket, lang = 'zh') {
    this.socket = socket;
    this.lang = lang;
    this.buf = Buffer.alloc(0);
    this.error = null;
    this.ended = false;
    this._detached = false;
    this._onData = (chunk) => {
      this.buf = this.buf.length ? Buffer.concat([this.buf, chunk]) : chunk;
    };
    this._onError = (err) => { this.error = err; };
    this._onEnd = () => { this.ended = true; };
    this._onClose = () => { this.ended = true; };
    socket.on('data', this._onData);
    socket.on('error', this._onError);
    socket.on('end', this._onEnd);
    socket.on('close', this._onClose);
  }

  detach() {
    if (this._detached) return this.buf;
    this._detached = true;
    this.socket.removeListener('data', this._onData);
    this.socket.removeListener('error', this._onError);
    this.socket.removeListener('end', this._onEnd);
    this.socket.removeListener('close', this._onClose);
    const leftover = this.buf;
    this.buf = Buffer.alloc(0);
    return leftover;
  }

  async read(size, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    while (this.buf.length < size) {
      if (this.error) throw this.error;
      if (this.ended) throw new Error(t(this.lang, 'proxy.closed'));
      const left = deadline - Date.now();
      if (left <= 0) throw new Error(t(this.lang, 'proxy.timeout'));
      await sleep(Math.min(10, left));
    }
    const out = this.buf.subarray(0, size);
    this.buf = this.buf.subarray(size);
    return out;
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function openSocket({ host, port, tls: useTls }, timeoutMs, lang = 'zh') {
  return new Promise((resolve, reject) => {
    const opts = { host, port };
    if (useTls) opts.servername = host;
    const socket = useTls ? tls.connect(opts) : net.createConnection(opts);
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(t(lang, 'proxy.connectTimeout', host, port)));
    }, timeoutMs);
    const onReady = () => {
      clearTimeout(timer);
      socket.removeListener('error', onError);
      resolve(socket);
    };
    const onError = (err) => {
      clearTimeout(timer);
      socket.destroy();
      reject(new Error(t(lang, 'proxy.connectFail', host, port, err.code || err.message)));
    };
    socket.once(useTls ? 'secureConnect' : 'connect', onReady);
    socket.once('error', onError);
  });
}

async function handshakeSocks5(socket, proxy, host, port, timeoutMs, lang = 'zh') {
  const reader = new ByteReader(socket, lang);
  const withAuth = Boolean(proxy.username);

  socket.write(Buffer.from(withAuth ? [0x05, 0x02, 0x00, 0x02] : [0x05, 0x01, 0x00]));
  let resp = await reader.read(2, timeoutMs);
  if (resp[0] !== 0x05) throw new Error(t(lang, 'proxy.badSocks5'));
  let method = resp[1];

  if (method === 0x00) {
    // 无需认证
  } else if (method === 0x02) {
    if (!proxy.username) throw new Error(t(lang, 'proxy.needAuth'));
    const user = Buffer.from(proxy.username, 'utf8');
    const pass = Buffer.from(proxy.password || '', 'utf8');
    if (user.length > 255 || pass.length > 255) {
      throw new Error(lang === 'en'
        ? 'SOCKS5 credentials too long (max 255 bytes each)'
        : 'SOCKS5 认证信息过长（各最多 255 字节）');
    }
    socket.write(Buffer.concat([Buffer.from([0x01, user.length]), user, Buffer.from([pass.length]), pass]));
    resp = await reader.read(2, timeoutMs);
    if (resp[1] !== 0x00) throw new Error(t(lang, 'proxy.authFailed'));
  } else {
    throw new Error(t(lang, 'proxy.noMethod'));
  }

  const isV4 = net.isIPv4(host);
  const isV6 = net.isIPv6(host);
  const portBuf = Buffer.alloc(2);
  portBuf.writeUInt16BE(port);

  let addrPart;
  if (isV4) {
    addrPart = Buffer.concat([Buffer.from([0x01]), Buffer.from(host.split('.').map(Number))]);
  } else if (isV6) {
    addrPart = Buffer.concat([Buffer.from([0x04]), ipv6ToBuffer(host)]);
  } else {
    const name = Buffer.from(host, 'utf8');
    addrPart = Buffer.concat([Buffer.from([0x03, name.length]), name]);
  }

  socket.write(Buffer.concat([Buffer.from([0x05, 0x01, 0x00]), addrPart, portBuf]));
  resp = await reader.read(4, timeoutMs);
  if (resp[0] !== 0x05) throw new Error(t(lang, 'proxy.badSocks5'));
  if (resp[1] !== 0x00) {
    const reasons = lang === 'en'
      ? {
        1: 'internal error', 2: 'not allowed by ruleset', 3: 'network unreachable',
        4: 'host unreachable', 5: 'connection refused', 6: 'TTL expired',
        7: 'command not supported', 8: 'address type not supported',
      }
      : {
        1: '代理内部错误', 2: '规则不允许', 3: '网络不可达',
        4: '主机不可达', 5: '连接被拒绝', 6: 'TTL 过期', 7: '不支持的命令', 8: '不支持的地址类型',
      };
    const reason = reasons[resp[1]] || (lang === 'en' ? `error code ${resp[1]}` : `错误码 ${resp[1]}`);
    throw new Error(t(lang, 'proxy.socks5Failed', reason));
  }
  // 把 BND.ADDR + BND.PORT 读完
  const atyp = resp[3];
  const addrLen = atyp === 0x01 ? 4 : atyp === 0x04 ? 16 : (await reader.read(1, timeoutMs))[0];
  await reader.read(addrLen + 2, timeoutMs);
  return reader.detach();
}

function ipv6ToBuffer(ip) {
  // 处理 :: 简写
  const [head, tail] = ip.split('::');
  const left = head ? head.split(':') : [];
  const right = tail ? tail.split(':') : [];
  const fill = Array(8 - left.length - right.length).fill('0');
  const parts = [...left, ...fill, ...right];
  const buf = Buffer.alloc(16);
  parts.forEach((part, i) => buf.writeUInt16BE(parseInt(part || '0', 16), i * 2));
  return buf;
}

async function handshakeSocks4(socket, proxy, host, port, timeoutMs, lang = 'zh') {
  const reader = new ByteReader(socket, lang);
  const portBuf = Buffer.alloc(2);
  portBuf.writeUInt16BE(port);
  const user = Buffer.from(proxy.username || '', 'utf8');

  let packet;
  if (net.isIPv4(host)) {
    const ip = Buffer.from(host.split('.').map(Number));
    packet = Buffer.concat([Buffer.from([0x04, 0x01]), portBuf, ip, user, Buffer.from([0x00])]);
  } else {
    // SOCKS4a：0.0.0.x 表示后面跟域名
    packet = Buffer.concat([
      Buffer.from([0x04, 0x01]), portBuf, Buffer.from([0, 0, 0, 1]), user,
      Buffer.from([0x00]), Buffer.from(host, 'utf8'), Buffer.from([0x00]),
    ]);
  }
  socket.write(packet);
  const resp = await reader.read(8, timeoutMs);
  if (resp[1] !== 0x5a) throw new Error(t(lang, 'proxy.socks4Failed', resp[1]));
  return reader.detach();
}

async function handshakeHttp(socket, proxy, host, port, timeoutMs, lang = 'zh') {
  const reader = new ByteReader(socket, lang);
  // IPv6 目标在 CONNECT 请求行里必须带方括号
  const authority = `${net.isIPv6(host) ? `[${host}]` : host}:${port}`;
  const lines = [`CONNECT ${authority} HTTP/1.1`, `Host: ${authority}`, 'Proxy-Connection: Keep-Alive'];
  if (proxy.username) {
    const token = Buffer.from(`${proxy.username}:${proxy.password || ''}`).toString('base64');
    lines.push(`Proxy-Authorization: Basic ${token}`);
  }
  socket.write(`${lines.join('\r\n')}\r\n\r\n`);

  // 只读到响应头结束
  const deadline = Date.now() + timeoutMs;
  let text = '';
  while (!text.includes('\r\n\r\n')) {
    if (Date.now() > deadline) throw new Error(t(lang, 'proxy.timeout'));
    const chunk = await reader.read(1, Math.max(1, deadline - Date.now()));
    text += chunk.toString('latin1');
    if (text.length > 8192) throw new Error(t(lang, 'proxy.headerTooBig'));
  }
  const statusLine = text.split('\r\n')[0] || '';
  const match = /^HTTP\/\d\.\d\s+(\d{3})/.exec(statusLine);
  if (!match || Number(match[1]) !== 200) {
    throw new Error(t(lang, 'proxy.httpRefused', statusLine || (lang === 'en' ? 'no response' : '无响应')));
  }
  return reader.detach();
}

/**
 * 通过代理建立到 host:port 的 TCP 隧道，返回可直接读写的 socket。
 * 隧道里如果已经有多余字节，会 unshift 回 socket，调用方不会丢数据。
 */
async function connectTunnel(proxy, host, port, timeoutMs = 8000, lang = 'zh') {
  const socket = await openSocket(proxy, timeoutMs, lang);
  let leftover = Buffer.alloc(0);
  try {
    switch (proxy.type) {
      case 'socks5':
      case 'socks5h':
        leftover = await handshakeSocks5(socket, proxy, host, port, timeoutMs, lang);
        break;
      case 'socks4':
      case 'socks4a':
        leftover = await handshakeSocks4(socket, proxy, host, port, timeoutMs, lang);
        break;
      case 'http':
      case 'https':
        leftover = await handshakeHttp(socket, proxy, host, port, timeoutMs, lang);
        break;
      default:
        throw new Error(t(lang, 'proxy.unsupported', proxy.type));
    }
  } catch (err) {
    socket.destroy();
    throw err;
  }

  if (leftover && leftover.length) socket.unshift(leftover);
  return socket;
}

module.exports = { connectTunnel, normalizeProxy };
