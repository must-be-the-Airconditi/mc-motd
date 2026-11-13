'use strict';

/**
 * Minecraft Java Edition - Server List Ping (SLP)
 *  - 现代协议 (1.7+)：Handshake(0x00) + Status Request(0x00)
 *  - 旧版协议 (Beta 1.8 ~ 1.6)：0xFE 0x01
 *  - 自动 SRV 解析：_minecraft._tcp.<host>
 *
 * 只依赖 Node 内置模块。
 */

const net = require('net');
const dns = require('dns');
const { connectTunnel, normalizeProxy } = require('./proxy');

const DEFAULT_PORT = 25565;
const HANDSHAKE_PROTOCOL = 769; // 1.21.4；状态查询不校验版本，取个较新的值即可
const MAX_PACKET = 2 * 1024 * 1024;

/** 把超时钳制到合理区间，避免用户传入离谱值 */
function clampTimeout(ms) {
  const value = Number(ms);
  if (!Number.isFinite(value)) return 5000;
  return Math.min(Math.max(value, 100), 60000);
}

/* ---------------------------------------------------------------- VarInt */

function writeVarInt(value) {
  const bytes = [];
  let v = value >>> 0;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v !== 0) b |= 0x80;
    bytes.push(b);
  } while (v !== 0);
  return Buffer.from(bytes);
}

/**
 * 从 buf 的 offset 处读一个 VarInt。
 * 数据不完整时返回 null，调用方可以等待更多数据。
 */
function readVarInt(buf, offset = 0) {
  let value = 0;
  let size = 0;
  let byte;
  do {
    if (offset + size >= buf.length) return null;
    byte = buf[offset + size];
    value += (byte & 0x7f) * 2 ** (7 * size);
    size += 1;
    if (size > 5) throw new Error('VarInt 过长（数据损坏）');
  } while (byte & 0x80);
  return { value: value >>> 0, size };
}

function writeString(str) {
  const body = Buffer.from(str, 'utf8');
  return Buffer.concat([writeVarInt(body.length), body]);
}

/* -------------------------------------------------------------- 分帧读取 */

/** 按「VarInt 长度前缀 + 包体」从 socket 里拆包 */
class FrameReader {
  constructor(socket) {
    this.socket = socket;
    this.buf = Buffer.alloc(0);
    this.queue = [];
    this.waiters = [];
    this.closed = false;
    this.error = null;

    socket.on('data', (chunk) => {
      this.buf = this.buf.length ? Buffer.concat([this.buf, chunk]) : chunk;
      this.pump();
    });
    socket.on('end', () => { this.closed = true; this.pump(); });
    socket.on('close', () => { this.closed = true; this.pump(); });
    socket.on('error', (err) => { this.error = err; this.pump(); });
  }

  pump() {
    // 尽量把缓冲区里完整的包都取出来
    for (;;) {
      let head;
      try {
        head = readVarInt(this.buf, 0);
      } catch (err) {
        this.error = err;
        break;
      }
      if (!head || head.size + head.value > this.buf.length) break;
      if (head.value > MAX_PACKET) {
        this.error = new Error('服务器返回的包过大');
        break;
      }
      const payload = this.buf.subarray(head.size, head.size + head.value);
      this.buf = this.buf.subarray(head.size + head.value);
      const waiter = this.waiters.shift();
      if (waiter) this.settle(waiter, payload);
      else this.queue.push(payload);
    }

    // 连接结束/出错时，把剩下等待的读取都 reject 掉，避免悬挂
    if (this.error || this.closed) {
      while (this.waiters.length) {
        const waiter = this.waiters.shift();
        this.settleError(waiter, this.error || new Error('服务器关闭了连接'));
      }
    }
  }

  settle(waiter, payload) {
    if (waiter.timer) clearTimeout(waiter.timer);
    waiter.resolve(payload);
  }

  settleError(waiter, err) {
    if (waiter.timer) clearTimeout(waiter.timer);
    waiter.reject(err);
  }

  read(timeoutMs = 0) {
    if (this.queue.length) return Promise.resolve(this.queue.shift());
    if (this.error) return Promise.reject(this.error);
    if (this.closed) return Promise.reject(new Error('服务器关闭了连接'));

    return new Promise((resolve, reject) => {
      const waiter = { resolve, reject, timer: null };
      if (timeoutMs > 0) {
        waiter.timer = setTimeout(() => {
          this.waiters = this.waiters.filter((w) => w !== waiter);
          reject(new Error('等待服务器响应超时'));
        }, clampTimeout(timeoutMs));
      }
      this.waiters.push(waiter);
      this.pump(); // 可能刚巧数据已经到了
    });
  }
}

/* ------------------------------------------------------------ 现代协议 */

function handshakePacket(host, port, protocol) {
  const portBuf = Buffer.alloc(2);
  portBuf.writeUInt16BE(port);
  const body = Buffer.concat([
    writeVarInt(0x00),
    writeVarInt(protocol),
    writeString(host),
    portBuf,
    writeVarInt(1), // next state: status
  ]);
  return Buffer.concat([writeVarInt(body.length), body]);
}

async function pingModern({ host, port, protocol = HANDSHAKE_PROTOCOL, timeout = 5000, connect, hostHeader }) {
  const socket = await connect(host, port, timeout);
  socket.setNoDelay(true);

  try {
    const reader = new FrameReader(socket);
    socket.write(handshakePacket(hostHeader || host, port, protocol));
    socket.write(Buffer.from([0x01, 0x00])); // Status Request：长度 1，包 id 0x00

    const started = process.hrtime.bigint();
    const packet = await reader.read(timeout);
    const latencyMs = Number(process.hrtime.bigint() - started) / 1e6;

    const packetId = readVarInt(packet, 0);
    if (!packetId || packetId.value !== 0x00) throw new Error('状态响应格式不正确');

    const len = readVarInt(packet, packetId.size);
    if (!len) throw new Error('状态响应长度不完整');
    const jsonStart = packetId.size + len.size;
    const text = packet.subarray(jsonStart, jsonStart + len.value).toString('utf8');

    let status;
    try {
      status = JSON.parse(text);
    } catch (err) {
      throw new Error('服务器返回的 JSON 无法解析');
    }
    if (!status || typeof status !== 'object') throw new Error('状态数据为空');
    return { status, latencyMs, protocolUsed: 'modern' };
  } finally {
    socket.destroy();
  }
}

/* ------------------------------------------------------------ 旧版协议 */

function pingLegacy({ host, port, timeout = 5000, connect }) {
  return new Promise((resolve, reject) => {
    let socket = null;
    let done = false;
    let buf = Buffer.alloc(0);
    const started = process.hrtime.bigint();

    const finish = (err, value) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      if (socket) socket.destroy();
      if (err) reject(err); else resolve(value);
    };

    const timer = setTimeout(() => finish(new Error('旧版 Ping 超时')), clampTimeout(timeout));

    connect(host, port, timeout)
      .then((sock) => {
        if (done) { sock.destroy(); return; }
        socket = sock;
        socket.setNoDelay(true);
        socket.on('error', (err) => finish(err));
        socket.on('close', () => finish(new Error('服务器在响应前关闭了连接')));
        socket.write(Buffer.from([0xfe, 0x01]));

        socket.on('data', (chunk) => {
          buf = Buffer.concat([buf, chunk]);
          if (buf.length < 3) return;
          if (buf[0] !== 0xff) return finish(new Error('旧版 Ping 响应格式不正确'));
          const chars = buf.readUInt16BE(1);
          const total = 3 + chars * 2;
          if (buf.length < total) return;
          const raw = Buffer.from(buf.subarray(3, total)).swap16().toString('utf16le');
          const fields = raw.split('\u0000');
          const latencyMs = Number(process.hrtime.bigint() - started) / 1e6;
          finish(null, {
            latencyMs,
            protocolUsed: 'legacy',
            status: {
              version: { name: fields[2] || '', protocol: Number(fields[1]) || 0 },
              players: { online: Number(fields[4]) || 0, max: Number(fields[5]) || 0 },
              description: fields[3] || '',
            },
          });
        });
      })
      .catch((err) => finish(err));
  });
}

/* ------------------------------------------------------------- 地址解析 */

function isIp(host) {
  return net.isIP(host) !== 0;
}

/** 把用户输入拆成 {host, port|null}，支持 IPv6 的 [::1]:25565 写法 */
function parseAddress(input) {
  let raw = String(input || '').trim();
  if (!raw) throw new Error('地址不能为空');
  raw = raw.replace(/^minecraft:\/\//i, '');
  while (raw.endsWith('/')) raw = raw.slice(0, -1);

  let host;
  let port = null;

  if (raw.startsWith('[')) {
    const end = raw.indexOf(']');
    if (end === -1) throw new Error('IPv6 地址缺少 ]');
    host = raw.slice(1, end);
    const rest = raw.slice(end + 1);
    if (rest.startsWith(':')) port = Number(rest.slice(1));
    else if (rest) throw new Error('地址格式不正确');
  } else {
    const colons = (raw.match(/:/g) || []).length;
    if (colons > 1) {
      host = raw; // 裸 IPv6
    } else if (colons === 1) {
      const idx = raw.lastIndexOf(':');
      host = raw.slice(0, idx);
      const p = raw.slice(idx + 1);
      if (!/^\d+$/.test(p)) throw new Error('端口必须是数字');
      port = Number(p);
    } else {
      host = raw;
    }
  }

  host = host.trim();
  if (!host) throw new Error('缺少主机名');
  if (host.length > 253) throw new Error('主机名过长');
  if (!isIp(host) && !/^[a-zA-Z0-9._-]+$/.test(host)) throw new Error('主机名包含非法字符');
  if (port !== null && (!Number.isInteger(port) || port < 1 || port > 65535)) throw new Error('端口超出范围 (1-65535)');

  return { host: host.toLowerCase(), port };
}

/** SRV 查询：_minecraft._tcp.<host> */
function lookupSrv(host, timeoutMs = 2000) {
  return new Promise((resolve) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (!settled) { settled = true; resolve(null); }
    }, timeoutMs);
    dns.resolveSrv(`_minecraft._tcp.${host}`, (err, records) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (err || !records || !records.length) return resolve(null);
      const sorted = [...records].sort((a, b) => a.priority - b.priority);
      const target = sorted[0].name.replace(/\.$/, '');
      resolve({ host: target, port: sorted[0].port });
    });
  });
}

/* -------------------------------------------------------------- 默认连接 */

function directConnect(host, port, timeoutMs) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port });
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`连接 ${host}:${port} 超时`));
    }, timeoutMs);

    socket.once('connect', () => {
      clearTimeout(timer);
      resolve(socket);
    });
    socket.once('error', (err) => {
      clearTimeout(timer);
      reject(friendlyError(err, host, port));
    });
  });
}

function friendlyError(err, host, port) {
  const code = err && err.code;
  const map = {
    ECONNREFUSED: `连接被拒绝：${host}:${port} 上没有服务在监听，或端口写错了`,
    ENOTFOUND: `找不到主机：${host}（DNS 解析失败）`,
    EAI_AGAIN: `DNS 查询超时：${host}`,
    ECONNRESET: `连接被服务器重置：${host}:${port}`,
    ETIMEDOUT: `连接超时：${host}:${port}`,
    EHOSTUNREACH: `主机不可达：${host}`,
    ENETUNREACH: `网络不可达：${host}`,
  };
  const message = map[code] || (err && err.message) || String(err);
  const out = new Error(message);
  out.code = code;
  return out;
}

/** 组合：直连优先，配置了代理就走代理 */
function makeConnector(proxyUrl) {
  const proxy = proxyUrl ? normalizeProxy(proxyUrl) : null;
  return (host, port, timeout) =>
    proxy
      ? connectTunnel(proxy, host, port, timeout)
      : directConnect(host, port, timeout);
}

/* ------------------------------------------------------------------ 主函数 */

/**
 * Ping 一个 Java 版服务器。
 * @param {string} address 例如 "play.example.com" 或 "host:25565"
 * @param {object} options
 *   timeout   总超时毫秒，默认 5000
 *   protocol  握手用协议号，默认 769
 *   srv       是否解析 SRV，默认 true
 *   legacy    现代协议失败后是否回退旧版 Ping，默认 true
 *   proxy     代理 URL，例如 socks5://127.0.0.1:7890 / http://127.0.0.1:8080
 */
async function ping(address, options = {}) {
  const {
    timeout = 5000,
    protocol = HANDSHAKE_PROTOCOL,
    srv = true,
    legacy = true,
    proxy = null,
  } = options;

  const parsed = parseAddress(address);
  const connect = makeConnector(proxy || process.env.MC_MOTD_PROXY || null);

  let host = parsed.host;
  let port = parsed.port;
  let srvInfo = null;

  if (srv && port === null && !isIp(host)) {
    srvInfo = await lookupSrv(host);
    if (srvInfo) {
      host = srvInfo.host;
      port = srvInfo.port;
    }
  }
  if (port == null) port = DEFAULT_PORT;

  const base = {
    query: address,
    requestedHost: parsed.host,
    requestedPort: parsed.port,
    host,
    port,
    srv: srvInfo,
    proxy: proxy ? 'configured' : null,
  };

  try {
    const result = await pingModern({
      host,
      port,
      protocol,
      timeout,
      connect,
      hostHeader: parsed.host,
    });
    return { ...base, ...result };
  } catch (modernErr) {
    if (!legacy) {
      modernErr.message = `${modernErr.message}`;
      throw modernErr;
    }
    try {
      const result = await pingLegacy({ host, port, timeout, connect });
      return { ...base, ...result, modernError: modernErr.message };
    } catch (legacyErr) {
      const err = new Error(`Ping 失败：${modernErr.message}`);
      err.cause = legacyErr;
      throw err;
    }
  }
}

module.exports = {
  ping,
  parseAddress,
  lookupSrv,
  readVarInt,
  writeVarInt,
  writeString,
  FrameReader,
  makeConnector,
  DEFAULT_PORT,
  HANDSHAKE_PROTOCOL,
};
