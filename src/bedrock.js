'use strict';

/**
 * Minecraft Bedrock Edition 服务器状态查询（RakNet 协议）
 *
 *  - 发送 Unconnected Ping（0x01 + 时间戳 + magic + GUID）
 *  - 服务器回 Unconnected Pong（0x1c + 时间戳 + GUID + magic + 长度 + 字符串）
 *  - 字符串格式：MCPE;MOTD;协议;版本;在线;上限;服务器ID;副标题;游戏模式;...
 *
 * 只依赖 Node 内置模块。
 */

const dgram = require('dgram');
const { t } = require('./i18n');
const { parseAddress } = require('./slp');

const DEFAULT_PORT = 19132;
const MAGIC = Buffer.from('00ffff00fefefefefdfdfdfd12345678', 'hex');

function clampTimeout(ms) {
  const value = Number(ms);
  if (!Number.isFinite(value)) return 5000;
  return Math.min(Math.max(value, 100), 60000);
}

/** 解析 pong 里的分号字符串 */
function parsePongString(text) {
  const fields = String(text).split(';');
  const online = Number(fields[4]);
  const max = Number(fields[5]);
  const line1 = fields[1] || '';
  const line2 = fields[7] || '';
  return {
    edition: fields[0] || 'MCPE',
    version: { name: fields[3] || 'Bedrock', protocol: Number(fields[2]) || 0 },
    players: {
      online: Number.isFinite(online) ? online : 0,
      max: Number.isFinite(max) ? max : 0,
      sample: [],
    },
    description: line2 ? `${line1}\n${line2}` : line1,
    gameMode: fields[8] || null,
    serverId: fields[6] || null,
  };
}

/**
 * 查询一个基岩版服务器。
 * @param {string} address 例如 "play.example.com:19132"
 * @param {object} options timeout / lang
 */
function pingBedrock(address, options = {}) {
  const { timeout = 5000, lang = 'zh' } = options;
  const parsed = parseAddress(address, lang);
  const host = parsed.host;
  const port = parsed.port === null ? DEFAULT_PORT : parsed.port;
  const timeoutMs = clampTimeout(timeout);

  return new Promise((resolve, reject) => {
    const socket = dgram.createSocket(host.includes(':') ? 'udp6' : 'udp4');
    const started = process.hrtime.bigint();
    let settled = false;

    const finish = (err, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        socket.close();
      } catch {
        /* 忽略 */
      }
      if (err) reject(err);
      else resolve(value);
    };

    const timer = setTimeout(() => {
      finish(new Error(t(lang, 'bedrock.timeout', host, port)));
    }, timeoutMs);

    socket.on('error', (err) => finish(err));

    socket.on('message', (msg) => {
      if (!msg || msg[0] !== 0x1c || msg.length < 35) return;
      // 校验 magic，避免把别的 UDP 流量当成响应
      if (!msg.subarray(17, 33).equals(MAGIC)) return;

      const latencyMs = Number(process.hrtime.bigint() - started) / 1e6;
      const text = msg.subarray(35, msg.length).toString('utf8');
      const parsedStatus = parsePongString(text);

      finish(null, {
        query: address,
        host,
        port,
        srv: null,
        protocolUsed: 'bedrock',
        edition: 'bedrock',
        latencyMs,
        status: {
          version: parsedStatus.version,
          players: parsedStatus.players,
          description: parsedStatus.description,
        },
        bedrock: {
          edition: parsedStatus.edition,
          gameMode: parsedStatus.gameMode,
          serverId: parsedStatus.serverId,
        },
      });
    });

    const ping = Buffer.alloc(1 + 8 + 16 + 8);
    ping[0] = 0x01;
    // 时间戳（8 字节，BigInt 写） + GUID（8 字节随机）
    const ts = BigInt(Date.now());
    for (let i = 0; i < 8; i += 1) {
      ping[8 - i] = Number((ts >> BigInt(i * 8)) & 0xffn);
    }
    MAGIC.copy(ping, 9);
    for (let i = 25; i < 33; i += 1) {
      ping[i] = Math.floor(Math.random() * 256);
    }

    socket.send(ping, port, host, (err) => {
      if (err) finish(err);
    });
  });
}

module.exports = { pingBedrock, parsePongString, DEFAULT_PORT };
