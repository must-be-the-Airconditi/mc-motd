'use strict';

/* 前端：调用 /api/ping，渲染服务器列表卡片 */

const form = document.getElementById('form');
const input = document.getElementById('address');
const submit = document.getElementById('submit');
const output = document.getElementById('output');
const historyEl = document.getElementById('history');
const chipsEl = document.getElementById('chips');
const field = document.getElementById('field');

const HISTORY_KEY = 'mc-motd:history';
const OBF_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+-=/\\|<>';
const GRASS_SVG = `
<svg viewBox="0 0 32 32" width="100%" height="100%" role="img" aria-label="服务器图标">
  <rect width="32" height="32" fill="#0b120f"/>
  <path d="M4 11h24v17H4z" fill="#6b4b2f"/>
  <path d="M4 11h24v5H4z" fill="#4fd94f"/>
  <path d="M4 15h24v1.5H4z" fill="#2f8f2f"/>
  <path d="M8 19v3M14 20v4M20 19v3M25 21v2" stroke="#3a2a1c" stroke-width="1.2"/>
  <path d="M7 12v3M13 12v2M19 12v3M26 12v2" stroke="#88ff88" stroke-width="1.2"/>
</svg>`;

let obfTimer = null;
let busy = false;
let history = loadHistory();

/* --------------------------------------------------------------- 工具 */

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function loadHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
    return Array.isArray(raw) ? raw.filter((x) => typeof x === 'string').slice(0, 8) : [];
  } catch {
    return [];
  }
}

function saveHistory(address) {
  history = [address, ...history.filter((x) => x !== address)].slice(0, 8);
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
  } catch {
    /* 隐私模式下写不了，忽略 */
  }
  renderHistory();
}

function renderHistory() {
  if (!history.length) {
    historyEl.hidden = true;
    return;
  }
  historyEl.hidden = false;
  chipsEl.replaceChildren();
  for (const address of history) {
    const chip = el('button', 'chip', address);
    chip.type = 'button';
    chip.addEventListener('click', () => {
      input.value = address;
      query(address);
    });
    chipsEl.appendChild(chip);
  }
}

function latencyLevel(ms) {
  if (ms < 60) return 5;
  if (ms < 120) return 4;
  if (ms < 250) return 3;
  if (ms < 500) return 2;
  return 1;
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN');
}

/* ------------------------------------------------------------- 渲染片段 */

function renderSegments(segments) {
  const frag = document.createDocumentFragment();
  for (const seg of segments) {
    const span = document.createElement('span');
    span.textContent = seg.text;
    if (seg.color) span.style.color = seg.color;
    if (seg.bold) span.style.fontWeight = '700';
    if (seg.italic) span.style.fontStyle = 'italic';
    const deco = [];
    if (seg.underlined) deco.push('underline');
    if (seg.strikethrough) deco.push('line-through');
    if (deco.length) span.style.textDecoration = deco.join(' ');
    if (seg.obfuscated) span.dataset.obf = '';
    frag.appendChild(span);
  }
  return frag;
}

function stopObfuscation() {
  if (obfTimer) {
    clearInterval(obfTimer);
    obfTimer = null;
  }
}

function startObfuscation() {
  stopObfuscation();
  const nodes = Array.from(document.querySelectorAll('[data-obf]'));
  if (!nodes.length) return;

  const original = new Map();
  for (const node of nodes) original.set(node, node.textContent);
  obfTimer = setInterval(() => {
    for (const [node, text] of original) {
      if (!node.isConnected) continue;
      let shuffled = '';
      for (let i = 0; i < text.length; i += 1) {
        shuffled += OBF_CHARS[(Math.random() * OBF_CHARS.length) | 0];
      }
      node.textContent = shuffled;
    }
  }, 90);
}

/* ------------------------------------------------------------- 各种状态 */

function renderLoading(address) {
  stopObfuscation();
  output.replaceChildren();
  const wrap = el('div', 'result');

  const tile = el('div', 'tile loading');
  const icon = el('div', 'tile-icon');
  icon.appendChild(el('div', 'skeleton icon'));
  const body = el('div', 'tile-body');
  body.appendChild(el('div', 'skeleton line'));
  body.appendChild(el('div', 'skeleton line w1'));
  body.appendChild(el('div', 'skeleton line w2'));
  const ping = el('div', 'tile-ping');
  ping.appendChild(el('span', 'ms', 'PING…'));

  tile.append(icon, body, ping);
  wrap.appendChild(tile);

  const tip = el('p', 'empty-sub');
  tip.style.textAlign = 'left';
  tip.style.padding = '10px 2px';
  tip.textContent = `正在连接 ${address} …`;
  wrap.appendChild(tip);

  output.appendChild(wrap);
}

function renderError(address, message) {
  stopObfuscation();
  output.replaceChildren();
  const wrap = el('div', 'result');

  const tile = el('div', 'tile error');
  const icon = el('div', 'tile-icon');
  icon.appendChild(el('div', 'err-icon', '!'));

  const body = el('div', 'tile-body');
  body.appendChild(el('p', 'err-title', `连不上 ${address}`));
  body.appendChild(el('p', 'err-detail', message));

  const ping = el('div', 'tile-ping');
  ping.appendChild(el('span', 'ms', 'FAILED'));

  tile.append(icon, body, ping);

  const panel = el('div', 'panel players-panel');
  const hints = el('ul', 'hints');
  [
    '确认服务器在线、端口没写错（默认 25565）。',
    '群组服 / 代理服可能只开放了转发端口，试试问服主。',
    '本机网络连不上的话，可以用代理：motd --web --proxy http://127.0.0.1:31181',
    '这个工具只支持 Java 版；基岩版是 UDP 19132，协议不同。',
  ].forEach((text) => hints.appendChild(el('li', '', text)));
  panel.appendChild(hints);

  wrap.append(tile, panel);
  output.appendChild(wrap);
}

function buildTile(result) {
  const tile = el('div', 'tile');

  /* 图标 */
  const iconWrap = el('div', 'tile-icon');
  if (result.favicon) {
    const img = document.createElement('img');
    img.className = 'favicon';
    img.src = result.favicon;
    img.alt = '服务器图标';
    img.addEventListener('error', () => {
      iconWrap.replaceChildren();
      const fallback = el('div', 'favicon-fallback');
      fallback.innerHTML = GRASS_SVG;
      iconWrap.appendChild(fallback);
    });
    iconWrap.appendChild(img);
  } else {
    const fallback = el('div', 'favicon-fallback');
    fallback.innerHTML = GRASS_SVG;
    iconWrap.appendChild(fallback);
  }

  /* MOTD + 副信息 */
  const body = el('div', 'tile-body');
  const motd = el('div', 'motd');
  const lines = (result.motd && result.motd.lines) || [];
  const visible = lines.slice(0, 2).filter((line) => line.length);
  if (!visible.length) {
    motd.appendChild(el('span', 'motd-empty', '（服务器没有设置 MOTD）'));
  } else {
    visible.forEach((line, index) => {
      if (index > 0) motd.appendChild(document.createElement('br'));
      motd.appendChild(renderSegments(line));
    });
  }

  const sub = el('div', 'tile-sub');
  sub.appendChild(el('span', 'players', `${formatNumber(result.players.online)}/${formatNumber(result.players.max)}`));
  sub.appendChild(el('span', 'dot', '·'));
  sub.appendChild(el('span', 'version', result.version.name || '未知版本'));
  if (result.srv) {
    const tag = el('span', 'tag', 'SRV');
    tag.title = `${result.srv.host}:${result.srv.port}`;
    sub.appendChild(tag);
  }
  if (result.protocolUsed === 'legacy') {
    sub.appendChild(el('span', 'tag', 'LEGACY'));
  }

  body.append(motd, sub);

  /* 延迟 */
  const ping = el('div', 'tile-ping');
  const bars = el('div', 'bars');
  bars.dataset.level = String(latencyLevel(result.latencyMs));
  for (let i = 0; i < 5; i += 1) bars.appendChild(document.createElement('i'));
  ping.append(bars, el('span', 'ms', `${result.latencyMs} ms`));

  tile.append(iconWrap, body, ping);
  return tile;
}

function buildFacts(result, elapsedMs, cached) {
  const panel = el('div', 'panel');
  const head = el('div', 'panel-head');
  head.appendChild(el('span', '', '服务器信息'));
  const badge = el('span', 'badge', cached ? '缓存命中' : `${elapsedMs} ms`);
  head.appendChild(badge);

  const facts = el('dl', 'facts');

  const addFact = (label, value, className) => {
    const item = el('div', 'fact');
    item.appendChild(el('dt', '', label));
    const dd = el('dd', className || '', value);
    item.appendChild(dd);
    facts.appendChild(item);
    return dd;
  };

  const target = result.srv
    ? `${result.srv.host}:${result.srv.port}`
    : `${result.host}:${result.port}`;
  const targetDd = addFact('目标', target);
  if (result.srv) targetDd.title = `SRV 解析自 ${result.requestedHost}`;

  addFact('版本', `${result.version.name}${result.version.protocol ? ` · ${result.version.protocol}` : ''}`);
  addFact('在线玩家', `${formatNumber(result.players.online)} / ${formatNumber(result.players.max)}`);
  addFact('延迟', `${result.latencyMs} ms`, result.latencyMs < 120 ? 'good' : result.latencyMs < 300 ? '' : 'bad');
  addFact('协议', result.protocolUsed === 'legacy' ? '旧版 Ping' : '现代 Ping');
  addFact('安全聊天', result.checks && result.checks.secureChat ? '强制开启' : '未强制');

  panel.append(head, facts);
  return panel;
}

function buildPlayers(result) {
  const sample = (result.players && result.players.sample) || [];
  if (!sample.length) return null;

  const panel = el('div', 'panel');
  const head = el('div', 'panel-head');
  head.appendChild(el('span', '', '玩家列表'));
  head.appendChild(el('span', 'badge', `可见 ${sample.length} 人`));

  const body = el('div', 'players-panel');
  const list = el('ul', 'player-chips');
  sample.slice(0, 20).forEach((player) => {
    list.appendChild(el('li', '', player.name));
  });
  if (sample.length > 20) {
    list.appendChild(el('li', '', `等 ${sample.length} 人`));
  }
  body.appendChild(list);
  panel.append(head, body);
  return panel;
}

function buildRaw(result) {
  const details = el('details', 'panel raw');
  const summary = document.createElement('summary');
  summary.appendChild(document.createTextNode('原始状态 JSON'));
  const hint = el('span', 'badge', '服务器返回');
  summary.appendChild(hint);

  const tools = el('div', 'raw-tools');
  const copy = el('button', 'copy-btn', '复制 JSON');
  copy.type = 'button';
  tools.appendChild(copy);

  const pre = document.createElement('pre');
  const code = document.createElement('code');
  let text = result.raw;
  try {
    text = JSON.stringify(JSON.parse(result.raw), null, 2);
  } catch {
    /* 保持原样 */
  }
  code.textContent = text;
  pre.appendChild(code);

  copy.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(text);
      copy.textContent = '已复制 ✓';
    } catch {
      copy.textContent = '复制失败';
    }
    setTimeout(() => { copy.textContent = '复制 JSON'; }, 1600);
  });

  details.append(summary, tools, pre);
  return details;
}

function renderResult(result, meta) {
  stopObfuscation();
  output.replaceChildren();

  const wrap = el('div', 'result');
  wrap.appendChild(buildTile(result));
  wrap.appendChild(buildFacts(result, meta.elapsedMs, meta.cached));

  const players = buildPlayers(result);
  if (players) wrap.appendChild(players);
  wrap.appendChild(buildRaw(result));

  output.appendChild(wrap);
  startObfuscation();
}

/* ---------------------------------------------------------------- 查询 */

async function query(address) {
  const target = String(address || '').trim();
  if (!target || busy) return;

  busy = true;
  submit.disabled = true;
  submit.querySelector('.pixel').textContent = '…';
  const started = performance.now();
  renderLoading(target);

  try {
    const response = await fetch(`/api/ping?address=${encodeURIComponent(target)}`);
    const payload = await response.json();
    if (!payload.ok) throw new Error(payload.error || `请求失败（HTTP ${response.status}）`);

    const elapsedMs = Math.round(performance.now() - started);
    renderResult(payload.result, { elapsedMs, cached: payload.cached });
    saveHistory(target);
    document.title = `${payload.result.motd.plain.split('\n')[0].trim() || target} · MOTD`;
  } catch (err) {
    renderError(target, err.message || '未知错误');
  } finally {
    busy = false;
    submit.disabled = false;
    submit.querySelector('.pixel').textContent = 'PING';
  }
}

/* ---------------------------------------------------------------- 事件 */

form.addEventListener('submit', (event) => {
  event.preventDefault();
  query(input.value);
});

for (const button of document.querySelectorAll('.example')) {
  button.addEventListener('click', () => {
    const address = button.dataset.address;
    input.value = address;
    query(address);
  });
}

input.addEventListener('input', () => {
  if (input.value.length > 253) input.value = input.value.slice(0, 253);
});

document.addEventListener('keydown', (event) => {
  if (event.key === '/' && document.activeElement !== input) {
    event.preventDefault();
    input.focus();
  }
});

renderHistory();

const params = new URLSearchParams(location.search);
const initial = params.get('address');
if (initial) {
  input.value = initial;
  query(initial);
} else {
  input.focus();
}
