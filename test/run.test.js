'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { parseAddress, readVarInt, writeVarInt, ping } = require('../src/slp');
const { parseMOTD, buildResult, toAnsi } = require('../src/motd');
const { normalizeProxy } = require('../src/proxy');
const { createMockServer } = require('../tools/mock-server');
const { createWebServer } = require('../src/web');

/* --------------------------------------------------------------- 工具 */

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve(server.address().port));
  });
}

function close(server) {
  return new Promise((resolve) => {
    if (typeof server.closeAllConnections === 'function') server.closeAllConnections();
    server.close(resolve);
  });
}

/* ------------------------------------------------------------ 地址解析 */

test('parseAddress 处理常见写法', () => {
  assert.deepEqual(parseAddress('play.example.com'), { host: 'play.example.com', port: null });
  assert.deepEqual(parseAddress('Play.Example.COM:25565'), { host: 'play.example.com', port: 25565 });
  assert.deepEqual(parseAddress('  mc.example.com  '), { host: 'mc.example.com', port: null });
  assert.deepEqual(parseAddress('[::1]:25565'), { host: '::1', port: 25565 });
  assert.deepEqual(parseAddress('2001:db8::1'), { host: '2001:db8::1', port: null });
  assert.deepEqual(parseAddress('minecraft://mc.example.com'), { host: 'mc.example.com', port: null });
});

test('parseAddress 拒绝非法输入', () => {
  assert.throws(() => parseAddress(''), /不能为空/);
  assert.throws(() => parseAddress('a:b'), /端口/);
  assert.throws(() => parseAddress('host:99999'), /端口超出范围/);
  assert.throws(() => parseAddress('bad host'), /非法字符/);
});

/* ------------------------------------------------------------ VarInt */

test('VarInt 编解码往返', () => {
  for (const value of [0, 1, 2, 127, 128, 255, 300, 2097151, 2147483647, 0xffffffff]) {
    const encoded = writeVarInt(value);
    const decoded = readVarInt(encoded, 0);
    assert.equal(decoded.value, value >>> 0);
    assert.equal(decoded.size, encoded.length);
  }
});

test('VarInt 数据不完整时返回 null', () => {
  const encoded = writeVarInt(300); // 两个字节
  assert.equal(readVarInt(encoded.subarray(0, 1), 0), null);
  assert.equal(readVarInt(encoded, 0).value, 300);
});

/* -------------------------------------------------------------- MOTD */

test('传统 § 颜色码', () => {
  const motd = parseMOTD('§aHello §lWorld');
  assert.equal(motd.plain, 'Hello World');
  assert.equal(motd.segments[0].color, '#55FF55');
  assert.equal(motd.segments[0].text, 'Hello ');
  assert.equal(motd.segments[1].color, '#55FF55');
  assert.equal(motd.segments[1].bold, true);
  assert.equal(motd.segments[1].text, 'World');
});

test('§r 重置样式', () => {
  const motd = parseMOTD('§a§lAAA§rBBB');
  assert.equal(motd.segments[0].bold, true);
  assert.equal(motd.segments[1].color, '#FFFFFF');
  assert.equal(motd.segments[1].bold, false);
});

test('§x 十六进制颜色', () => {
  const motd = parseMOTD('§x§f§f§8§8§0§0橙色');
  assert.equal(motd.segments[0].color, '#ff8800');
  assert.equal(motd.segments[0].text, '橙色');
});

test('JSON 组件继承与 extra', () => {
  const motd = parseMOTD({
    text: 'A',
    color: 'red',
    bold: true,
    extra: [{ text: 'B' }, { text: 'C', bold: false }, { text: 'D', color: '#123456' }],
  });
  // 相邻同样式片段会被合并
  assert.deepEqual(
    motd.segments.map((seg) => [seg.text, seg.color, seg.bold]),
    [
      ['AB', '#FF5555', true],
      ['C', '#FF5555', false],
      ['D', '#123456', true],
    ],
  );
});

test('MOTD 拆行', () => {
  const motd = parseMOTD('第一行\n§a第二行');
  assert.equal(motd.lines.length, 2);
  assert.equal(motd.plainLines[0], '第一行');
  assert.equal(motd.plainLines[1], '第二行');
});

test('空 MOTD 不报错', () => {
  assert.deepEqual(parseMOTD(undefined).plain, '');
  assert.deepEqual(parseMOTD('').lines, [[]]);
  assert.equal(toAnsi([], { color: false }), '');
});

test('toAnsi 关闭颜色时输出纯文本', () => {
  const motd = parseMOTD('§aHi');
  assert.equal(toAnsi(motd.segments, { color: false }), 'Hi');
  assert.match(toAnsi(motd.segments, { color: true }), /\x1b\[38;2;85;255;85m/);
});

/* ------------------------------------------------------- buildResult */

test('buildResult 规范化字段', () => {
  const result = buildResult({
    query: 'demo:25565',
    host: '127.0.0.1',
    port: 25565,
    protocolUsed: 'modern',
    latencyMs: 12.34,
    status: {
      version: { name: '1.21.4', protocol: 769 },
      players: { online: 3, max: 10, sample: [{ name: 'Alex', id: 'x' }] },
      description: '§a你好',
      favicon: 'AAA',
      enforcesSecureChat: true,
    },
  });

  assert.equal(result.latencyMs, 12.3);
  assert.equal(result.players.online, 3);
  assert.deepEqual(result.players.sample, [{ name: 'Alex', id: 'x' }]);
  assert.equal(result.favicon, 'data:image/png;base64,AAA');
  assert.equal(result.motd.plain, '你好');
  assert.equal(result.checks.secureChat, true);
  assert.equal(JSON.parse(result.raw).version.name, '1.21.4');
});

/* ------------------------------------------------------------ 协议层 */

test('ping 现代协议（本地 mock）', async () => {
  const server = createMockServer({ mode: 'modern' });
  const port = await listen(server);
  try {
    const result = await ping(`127.0.0.1:${port}`, { timeout: 3000 });
    assert.equal(result.protocolUsed, 'modern');
    assert.equal(result.status.version.name, '1.21.4');
    assert.equal(result.status.players.online, 7);
    assert.match(result.status.favicon, /^data:image\/png;base64,/);
    assert.ok(result.latencyMs >= 0);

    const normal = buildResult(result);
    assert.equal(normal.players.sample.length, 4);
    assert.ok(normal.motd.plain.includes('DEMO SERVER'));
    assert.ok(normal.motd.lines.length >= 2);
  } finally {
    await close(server);
  }
});

test('ping 旧版协议（本地 mock）', async () => {
  const server = createMockServer({ mode: 'legacy' });
  const port = await listen(server);
  try {
    const result = await ping(`127.0.0.1:${port}`, { timeout: 1200 });
    assert.equal(result.protocolUsed, 'legacy');
    assert.equal(result.status.version.name, '1.6.4');
    assert.equal(result.status.players.online, 3);
    assert.ok(result.status.description.includes('Demo Legacy'));
  } finally {
    await close(server);
  }
});

test('ping 失败时给出可读错误', async () => {
  const server = createMockServer({ mode: 'modern' });
  const port = await listen(server);
  await close(server);
  await assert.rejects(ping(`127.0.0.1:${port}`, { timeout: 1500 }), /拒绝|超时|失败/);
});

/* -------------------------------------------------------------- 代理 */

test('normalizeProxy 解析各种代理地址', () => {
  assert.deepEqual(normalizeProxy('http://127.0.0.1:31181'), {
    type: 'http', host: '127.0.0.1', port: 31181, username: null, password: null,
  });
  assert.deepEqual(normalizeProxy('socks5://u:p@127.0.0.1:7890'), {
    type: 'socks5', host: '127.0.0.1', port: 7890, username: 'u', password: 'p',
  });
  assert.equal(normalizeProxy('127.0.0.1:1080').type, 'socks5');
  assert.throws(() => normalizeProxy('ftp://127.0.0.1:21'), /不支持/);
});

/* -------------------------------------------------------------- 网页 */

test('网页 API 能查询并返回结构化结果', async () => {
  const mock = createMockServer({ mode: 'modern' });
  const mockPort = await listen(mock);
  const web = createWebServer({ timeout: 3000 });
  const webPort = await listen(web);

  try {
    const response = await fetch(
      `http://127.0.0.1:${webPort}/api/ping?address=${encodeURIComponent(`127.0.0.1:${mockPort}`)}`,
    );
    assert.equal(response.status, 200);
    const payload = await response.json();
    assert.equal(payload.ok, true);
    assert.equal(payload.result.version.name, '1.21.4');
    assert.equal(payload.result.players.online, 7);
    assert.equal(payload.result.motd.lines.length, 2);

    const bad = await fetch(`http://127.0.0.1:${webPort}/api/ping?address=`);
    assert.equal(bad.status, 400);

    const home = await fetch(`http://127.0.0.1:${webPort}/`);
    assert.equal(home.status, 200);
    assert.match(await home.text(), /MOTD/);

    const missing = await fetch(`http://127.0.0.1:${webPort}/nope.html`);
    assert.equal(missing.status, 404);

    const traversal = await fetch(`http://127.0.0.1:${webPort}/../package.json`);
    assert.equal(traversal.status, 404);
  } finally {
    await close(web);
    await close(mock);
  }
});
