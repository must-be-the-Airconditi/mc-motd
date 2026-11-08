#!/usr/bin/env node
'use strict';

/**
 * 假的 Minecraft Java 服务器 —— 用来离线测试 / 演示 mc-motd
 *
 *   node tools/mock-server.js                 # 现代协议，25565 端口
 *   node tools/mock-server.js --legacy        # 只回旧版 Ping（1.6 风格）
 *   node tools/mock-server.js --port 25566
 *   node tools/mock-server.js --bot           # 现代 + 旧版都回
 */

const net = require('net');
const zlib = require('zlib');

/* ------------------------------------------------------------- 小工具 */

function varint(value) {
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

function readVarInt(buf, offset = 0) {
  let value = 0;
  let size = 0;
  let byte;
  do {
    if (offset + size >= buf.length) return null;
    byte = buf[offset + size];
    value += (byte & 0x7f) * 2 ** (7 * size);
    size += 1;
    if (size > 5) return null;
  } while (byte & 0x80);
  return { value: value >>> 0, size };
}

function readString(buf, offset) {
  const len = readVarInt(buf, offset);
  if (!len) return null;
  const start = offset + len.size;
  const end = start + len.value;
  if (end > buf.length) return null;
  return { value: buf.subarray(start, end).toString('utf8'), size: len.size + len.value };
}

/* --------------------------------------------------------------- PNG */

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let crc = 0xffffffff;
  for (const byte of buf) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([length, typeBuf, data, crc]);
}

/** 画一个 64×64 的草方块图标，顺便验证 favicon 显示 */
function buildFavicon(size = 64) {
  let seed = 20260101;
  const random = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 0xffffffff;
  };

  const pixels = Buffer.alloc(size * size * 4);
  const put = (x, y, r, g, b, a = 255) => {
    const idx = (y * size + x) * 4;
    pixels[idx] = r;
    pixels[idx + 1] = g;
    pixels[idx + 2] = b;
    pixels[idx + 3] = a;
  };

  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const block = Math.floor(y / 4);
      const noise = (random() - 0.5) * 26;
      if (block < 5) {
        // 草皮
        const base = 120 + noise;
        put(x, y, Math.max(0, base * 0.36), Math.min(255, base * 1.55), Math.max(0, base * 0.4));
      } else if (block === 5) {
        // 草和土的过渡
        const base = 90 + noise;
        put(x, y, Math.max(0, base * 0.55), Math.min(255, base * 1.2), Math.max(0, base * 0.5));
      } else {
        // 泥土
        const base = 110 + noise;
        put(x, y, Math.min(255, base * 1.08), Math.min(255, base * 0.72), Math.min(255, base * 0.45));
      }
    }
  }

  const raw = Buffer.alloc((size * 4 + 1) * size);
  for (let y = 0; y < size; y += 1) {
    raw[y * (size * 4 + 1)] = 0; // filter: none
    pixels.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);

  return `data:image/png;base64,${png.toString('base64')}`;
}

/* --------------------------------------------------------- 状态数据 */

function buildStatus() {
  return {
    version: { name: '1.21.4', protocol: 769 },
    players: {
      online: 7,
      max: 20,
      sample: [
        { id: '00000000-0000-0000-0000-000000000001', name: 'Alex' },
        { id: '00000000-0000-0000-0000-000000000002', name: 'Steve' },
        { id: '00000000-0000-0000-0000-000000000003', name: 'Notch' },
        { id: '00000000-0000-0000-0000-000000000004', name: 'Herobrine' },
      ],
    },
    description: [
      { text: '  ◆ ', color: '#55ff55', bold: true },
      { text: 'DEMO SERVER', color: 'gold', bold: true },
      { text: '  ', color: 'white' },
      { text: '§7[1.21.4]', color: 'white' },
      { text: '\n  ' },
      { text: '第二条 MOTD：', color: 'gray' },
      { text: '§a绿色 §c红色 §l粗体 §o斜体 §n下划线 §m删除线 §r默认', color: 'white' },
      { text: ' §k乱码', color: 'white' },
    ],
    favicon: buildFavicon(64),
    enforcesSecureChat: false,
    previewsChat: false,
  };
}

const LEGACY_STATUS = {
  version: { name: '1.6.4', protocol: 78 },
  players: { online: 3, max: 10 },
  description: '§6Demo Legacy §7(1.6)\n§a第二行 §n也能看',
};

/* --------------------------------------------------------- 协议处理 */

function modernReply(socket, status) {
  const json = Buffer.from(JSON.stringify(status), 'utf8');
  const body = Buffer.concat([varint(0x00), varint(json.length), json]);
  socket.write(Buffer.concat([varint(body.length), body]));
}

function legacyReply(socket, status) {
  const text = `§1\u0000${status.version.protocol}\u0000${status.version.name}\u0000` +
    `${status.description}\u0000${status.players.online}\u0000${status.players.max}`;
  const chars = Buffer.from(text, 'utf16le');
  const payload = Buffer.concat([
    Buffer.from([0xff]),
    (() => { const b = Buffer.alloc(2); b.writeUInt16BE(text.length); return b; })(),
    Buffer.from(chars).swap16(),
  ]);
  socket.write(payload);
}

function handleModern(socket, buf, status) {
  const head = readVarInt(buf, 0);
  if (!head || head.size + head.value > buf.length) return false; // 等更多数据

  const packet = buf.subarray(head.size, head.size + head.value);
  let offset = 0;
  const id = readVarInt(packet, offset);
  if (!id) return false;
  offset += id.size;

  if (id.value === 0x00) {
    // Handshake
    const protocol = readVarInt(packet, offset);
    if (!protocol) return false;
    offset += protocol.size;
    const address = readString(packet, offset);
    if (!address) return false;
    offset += address.size;
    const port = packet.readUInt16BE(offset);
    offset += 2;
    const nextState = readVarInt(packet, offset);

    log(`handshake 协议 ${protocol.value} → ${address.value}:${port} 状态 ${nextState ? nextState.value : '?'}`);
    // nextState = 1 就是来查状态的，直接回；客户端紧接着发的 Status Request 忽略即可
    if (!nextState || nextState.value === 1) {
      modernReply(socket, status);
      return true;
    }
    return true;
  }

  if (id.value === 0x01) {
    // Ping (0x01) —— 原样回显
    socket.write(Buffer.concat([varint(packet.length), packet]));
    return false;
  }

  return false;
}

function createMockServer(options = {}) {
  const status = options.status || buildStatus();
  const mode = options.mode || 'modern'; // modern | legacy | both

  const server = net.createServer((socket) => {
    let buf = Buffer.alloc(0);
    socket.on('error', () => {});
    socket.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);

      if (mode === 'legacy') {
        if (buf.length >= 2 && buf[0] === 0xfe) legacyReply(socket, LEGACY_STATUS);
        return;
      }

      if (buf[0] === 0xfe && (mode === 'both')) {
        if (buf.length >= 2) legacyReply(socket, LEGACY_STATUS);
        return;
      }

      try {
        if (handleModern(socket, buf, status)) buf = Buffer.alloc(0);
      } catch (err) {
        log(`处理失败：${err.message}`);
      }
    });
  });

  return server;
}

function log(message) {
  process.stdout.write(`\x1b[2m[mock] ${message}\x1b[0m\n`);
}

function startMock(options = {}) {
  const port = Number(options.port) || 25565;
  const host = options.host || '127.0.0.1';
  const server = createMockServer(options);

  server.on('error', (err) => {
    process.stderr.write(`模拟服务器启动失败：${err.message}\n`);
    process.exit(1);
  });

  server.listen(port, host, () => {
    process.stdout.write(
      `\n  \x1b[38;2;85;255;85m▌\x1b[0m 假 Minecraft 服务器已启动 · ${options.mode || 'modern'} 模式\n` +
      `  \x1b[2m➜\x1b[0m ${host}:${port}\n` +
      `  \x1b[2m试试：\x1b[0m motd ${host === '127.0.0.1' ? 'localhost' : host}:${port}\n\n`,
    );
  });

  return server;
}

if (require.main === module) {
  const args = process.argv.slice(2);
  const get = (flag, fallback) => {
    const idx = args.indexOf(flag);
    return idx >= 0 ? args[idx + 1] : fallback;
  };
  const mode = args.includes('--legacy') ? 'legacy' : args.includes('--bot') ? 'both' : 'modern';
  startMock({ port: get('--port', 25565), host: get('--host', '127.0.0.1'), mode });
}

module.exports = { createMockServer, buildStatus, buildFavicon, startMock, LEGACY_STATUS };
