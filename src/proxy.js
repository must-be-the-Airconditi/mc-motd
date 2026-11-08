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

const PROXY_TYPES = new Set(['socks5', 'socks5h', 'socks4', 'socks4a', 'http', 'https']);

function normalizeProxy(input) {
  if (!input) return null;
  let raw = String(input).trim();
  if (!/^[a-z0-9]+:\/\//i.test(raw)) raw = `socks5://${raw}`;

  const url = new URL(raw);
  const type = url.protocol.replace(':', '').toLowerCase();
  if (!PROXY_TYPES.has(type)) throw new Error(`不支持的代理类型：${type}`);

  const port = Number(url.port || (type === 'http' ? 80 : 1080));
  if (!url.hostname) throw new Error('代理地址缺少主机名');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('代理端口不正确');

  return {
    type,
    host: url.hostname,
    port,
    username: url.username ? decodeURIComponent(url.username) : null,
    password: url.password ? decodeURIComponent(url.password) : null,
  };
}

/** 带缓冲的 socket 读取器 */
class ByteReader {
  constructor(socket) {
    this.socket = socket;
    this.buf = Buffer.alloc(0);
    this.error = null;
    this.ended = false;
    socket.on('data', (chunk) => {
      this.buf = this.buf.length ? Buffer.concat([this.buf, chunk]) : chunk;
    });
    socket.on('error', (err) => { this.error = err; });
    socket.on('end', () => { this.ended = true; });
    socket.on('close', () => { this.ended = true; });
  }

  async read(size, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    while (this.buf.length < size) {
      if (this.error) throw this.error;
      if (this.ended) throw new Error('代理关闭了连接');
      const left = deadline - Date.now();
      if (left <= 0) throw new Error('代理响应超时');
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

function openSocket({ host, port, tls: useTls }, timeoutMs) {
  return new Promise((resolve, reject) => {
    const opts = { host, port };
    if (useTls) opts.servername = host;
    const socket = useTls ? tls.connect(opts) : net.createConnection(opts);
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`连接代理 ${host}:${port} 超时`));
    }, timeoutMs);
    const onReady = () => {
      clearTimeout(timer);
      socket.removeListener('error', onError);
      resolve(socket);
    };
    const onError = (err) => {
      clearTimeout(timer);
      reject(new Error(`无法连接代理 ${host}:${port}：${err.code || err.message}`));
    };
    socket.once(useTls ? 'secureConnect' : 'connect', onReady);
    socket.once('error', onError);
  });
}

async function handshakeSocks5(socket, proxy, host, port, timeoutMs) {
  const reader = new ByteReader(socket);
  const withAuth = Boolean(proxy.username);

  socket.write(Buffer.from(withAuth ? [0x05, 0x02, 0x00, 0x02] : [0x05, 0x01, 0x00]));
  let resp = await reader.read(2, timeoutMs);
  if (resp[0] !== 0x05) throw new Error('SOCKS5 代理响应异常');
  let method = resp[1];

  if (method === 0x00) {
    // 无需认证
  } else if (method === 0x02) {
    if (!proxy.username) throw new Error('代理要求用户名密码认证，请在地址里加上 user:pass@');
    const user = Buffer.from(proxy.username, 'utf8');
    const pass = Buffer.from(proxy.password || '', 'utf8');
    socket.write(Buffer.concat([Buffer.from([0x01, user.length]), user, Buffer.from([pass.length]), pass]));
    resp = await reader.read(2, timeoutMs);
    if (resp[1] !== 0x00) throw new Error('SOCKS5 认证失败');
  } else {
    throw new Error('代理不支持可用的认证方式');
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
  if (resp[1] !== 0x00) {
    const reasons = {
      1: '代理内部错误', 2: '规则不允许', 3: '网络不可达',
      4: '主机不可达', 5: '连接被拒绝', 6: 'TTL 过期', 7: '不支持的命令', 8: '不支持的地址类型',
    };
    throw new Error(`SOCKS5 连接目标失败：${reasons[resp[1]] || `错误码 ${resp[1]}`}`);
  }
  // 把 BND.ADDR + BND.PORT 读完
  const atyp = resp[3];
  const addrLen = atyp === 0x01 ? 4 : atyp === 0x04 ? 16 : (await reader.read(1, timeoutMs))[0];
  await reader.read(addrLen + 2, timeoutMs);
  return reader.buf;
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

async function handshakeSocks4(socket, proxy, host, port, timeoutMs) {
  const reader = new ByteReader(socket);
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
  if (resp[1] !== 0x5a) throw new Error(`SOCKS4 连接目标失败（代码 ${resp[1]}）`);
  return reader.buf;
}

async function handshakeHttp(socket, proxy, host, port, timeoutMs) {
  const reader = new ByteReader(socket);
  const lines = [`CONNECT ${host}:${port} HTTP/1.1`, `Host: ${host}:${port}`, 'Proxy-Connection: Keep-Alive'];
  if (proxy.username) {
    const token = Buffer.from(`${proxy.username}:${proxy.password || ''}`).toString('base64');
    lines.push(`Proxy-Authorization: Basic ${token}`);
  }
  socket.write(`${lines.join('\r\n')}\r\n\r\n`);

  // 只读到响应头结束
  const deadline = Date.now() + timeoutMs;
  let text = '';
  while (!text.includes('\r\n\r\n')) {
    if (Date.now() > deadline) throw new Error('代理响应超时');
    const chunk = await reader.read(1, Math.max(1, deadline - Date.now()));
    text += chunk.toString('latin1');
    if (text.length > 8192) throw new Error('代理响应头过大');
  }
  const statusLine = text.split('\r\n')[0] || '';
  const match = /^HTTP\/\d\.\d\s+(\d{3})/.exec(statusLine);
  if (!match || Number(match[1]) !== 200) {
    throw new Error(`HTTP 代理拒绝 CONNECT：${statusLine || '无响应'}`);
  }
  return reader.buf;
}

/**
 * 通过代理建立到 host:port 的 TCP 隧道，返回可直接读写的 socket。
 * 隧道里如果已经有多余字节，会 unshift 回 socket，调用方不会丢数据。
 */
async function connectTunnel(proxy, host, port, timeoutMs = 8000) {
  const socket = await openSocket(proxy, timeoutMs);
  let leftover = Buffer.alloc(0);
  try {
    switch (proxy.type) {
      case 'socks5':
      case 'socks5h':
        leftover = await handshakeSocks5(socket, proxy, host, port, timeoutMs);
        break;
      case 'socks4':
      case 'socks4a':
        leftover = await handshakeSocks4(socket, proxy, host, port, timeoutMs);
        break;
      case 'http':
      case 'https':
        leftover = await handshakeHttp(socket, proxy, host, port, timeoutMs);
        break;
      default:
        throw new Error(`不支持的代理类型：${proxy.type}`);
    }
  } catch (err) {
    socket.destroy();
    throw err;
  }

  // 交给调用方前，清掉我们加的监听，避免重复处理
  socket.removeAllListeners('data');
  if (leftover && leftover.length) socket.unshift(leftover);
  return socket;
}

module.exports = { connectTunnel, normalizeProxy };
