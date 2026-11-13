'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const dgram = require('dgram');

const { pingBedrock, parsePongString } = require('../src/bedrock');

const MAGIC = Buffer.from('00ffff00fefefefefdfdfdfd12345678', 'hex');

/* --------------------------------------------------------------- 单元 */

test('parsePongString 解析基岩版分号字段', () => {
  const parsed = parsePongString(
    'MCPE;§aHello;671;1.21.40;12;100;1234567890;§bSecond line;Survival;1;19132;19133',
  );
  assert.equal(parsed.edition, 'MCPE');
  assert.equal(parsed.version.name, '1.21.40');
  assert.equal(parsed.version.protocol, 671);
  assert.equal(parsed.players.online, 12);
  assert.equal(parsed.players.max, 100);
  assert.equal(parsed.description, '§aHello\n§bSecond line');
  assert.equal(parsed.gameMode, 'Survival');
});

test('parsePongString 没有副标题时只有一行', () => {
  const parsed = parsePongString('MCPE;Only one line;671;1.21.40;1;10;1;;Survival;1');
  assert.equal(parsed.description, 'Only one line');
});

test('parsePongString 字段缺失时不抛异常', () => {
  const parsed = parsePongString('MCPE');
  assert.equal(parsed.players.online, 0);
  assert.equal(parsed.players.max, 0);
  assert.equal(parsed.version.name, 'Bedrock');
});

/* --------------------------------------------------------------- 集成 */

function startMockBedrock(payload) {
  return new Promise((resolve) => {
    const socket = dgram.createSocket('udp4');
    socket.on('message', (msg, rinfo) => {
      if (!msg || msg[0] !== 0x01) return;
      const body = Buffer.from(payload, 'utf8');
      const pong = Buffer.alloc(35 + body.length);
      pong[0] = 0x1c;
      msg.copy(pong, 1, 1, 9); // 回显时间戳
      MAGIC.copy(pong, 17);
      pong.writeUInt16BE(body.length, 33);
      body.copy(pong, 35);
      socket.send(pong, rinfo.port, rinfo.address);
    });
    socket.bind(0, '127.0.0.1', () => {
      resolve({ socket, port: socket.address().port });
    });
  });
}

test('pingBedrock 能查询本地假服务器', async () => {
  const payload =
    'MCPE;§aDemo Bedrock;671;1.21.40;7;20;1234567890;§bSecond line;Survival;1;19132;19133';
  const { socket, port } = await startMockBedrock(payload);
  try {
    const result = await pingBedrock(`127.0.0.1:${port}`, { timeout: 3000 });
    assert.equal(result.protocolUsed, 'bedrock');
    assert.equal(result.edition, 'bedrock');
    assert.equal(result.status.version.name, '1.21.40');
    assert.equal(result.status.players.online, 7);
    assert.equal(result.status.players.max, 20);
    assert.match(result.status.description, /Demo Bedrock/);
    assert.ok(result.latencyMs >= 0);
  } finally {
    await new Promise((resolve) => socket.close(resolve));
  }
});

test('pingBedrock 超时给出可读错误', async () => {
  // 绑定一个不回应任何数据包的 UDP 端口
  const silent = dgram.createSocket('udp4');
  const port = await new Promise((resolve) => {
    silent.bind(0, '127.0.0.1', () => resolve(silent.address().port));
  });
  try {
    await assert.rejects(
      pingBedrock(`127.0.0.1:${port}`, { timeout: 400 }),
      /无响应|No response/,
    );
  } finally {
    await new Promise((resolve) => silent.close(resolve));
  }
});
