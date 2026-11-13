'use strict';

/* 前端：调用 /api/ping，渲染服务器列表卡片 */

const form = document.getElementById('form');
const input = document.getElementById('address');
const submit = document.getElementById('submit');
const output = document.getElementById('output');
const historyEl = document.getElementById('history');
const chipsEl = document.getElementById('chips');
const field = document.getElementById('field');
const langBtn = document.getElementById('lang');
const addrLabel = document.getElementById('addr-label');
const historyLabel = document.getElementById('history-label');
const foot1 = document.getElementById('foot-1');
const foot2 = document.getElementById('foot-2');
const metaDesc = document.querySelector('meta[name="description"]');
const brandTitle = document.querySelector('.brand-text h1');

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


/* ------------------------------------------------------------- 国际化 */

const LANG_KEY = 'mc-motd:lang';

const I18N = {
  zh: {
    'page.title': 'MOTD 查看器 · Minecraft Java',
    'page.desc': '在线查看 Minecraft Java 版服务器的 MOTD、版本、在线人数和延迟。直连服务器列表 Ping 协议。',
    'brand.suffix': '查看器',
    'placeholder': 'play.example.com  或  host:25565',
    'form.label': '服务器地址',
    'history.label': '最近',
    'empty.title': '输入服务器地址，直接读取它的 MOTD',
    'empty.sub': '服务端直连 TCP 25565 · 自动 SRV 解析 · 不经过第三方 API',
    'loading': '正在连接 {0} …',
    'error.title': '连不上 {0}',
    'error.title.empty': '查询失败',
    'error.hint1': '确认服务器在线、端口没写错（默认 25565）。',
    'error.hint2': '群组服 / 代理服可能只开放了转发端口，试试问服主。',
    'error.hint3': '本机网络连不上的话，可以用代理：motd --web --proxy http://127.0.0.1:31181',
    'error.hint4': '这个工具只支持 Java 版；基岩版是 UDP 19132，协议不同。',
    'img.icon': '服务器图标',
    'motd.empty': '（服务器没有设置 MOTD）',
    'version.unknown': '未知版本',
    'facts.title': '服务器信息',
    'badge.cached': '缓存命中',
    'fact.target': '目标',
    'fact.srvFrom': 'SRV 解析自 {0}',
    'fact.version': '版本',
    'fact.players': '在线玩家',
    'fact.latency': '延迟',
    'fact.protocol': '协议',
    'fact.secure': '安全聊天',
    'ping.legacy': '旧版 Ping',
    'ping.modern': '现代 Ping',
    'secure.on': '强制开启',
    'secure.off': '未强制',
    'players.title': '玩家列表',
    'players.visible': '可见 {0} 人',
    'players.more': '等 {0} 人',
    'raw.title': '原始状态 JSON',
    'raw.fromServer': '服务器返回',
    'raw.copy': '复制 JSON',
    'raw.copied': '已复制 ✓',
    'raw.copyFailed': '复制失败',
    'error.http': '请求失败（HTTP {0}）',
    'error.unknown': '未知错误',
    'foot.line1': '纯 Node.js，零依赖',
    'foot.cli': 'CLI 同源：',
    'foot.cmd': 'motd <地址>',
  },
  en: {
    'page.title': 'MOTD Viewer · Minecraft Java',
    'page.desc': 'View a Minecraft Java Edition server MOTD, version, player count and latency in your browser. Talks the server list ping protocol directly.',
    'brand.suffix': 'Viewer',
    'placeholder': 'play.example.com  or  host:25565',
    'form.label': 'Server address',
    'history.label': 'Recent',
    'empty.title': 'Enter a server address to read its MOTD',
    'empty.sub': 'Direct TCP 25565 · automatic SRV · no third-party API',
    'loading': 'Connecting to {0} …',
    'error.title': 'Can not reach {0}',
    'error.title.empty': 'Query failed',
    'error.hint1': 'Make sure the server is online and the port is right (default 25565).',
    'error.hint2': 'Proxy networks may only expose a forwarding port — ask the owner.',
    'error.hint3': 'If your network cannot reach it, use a proxy: motd --web --proxy http://127.0.0.1:31181',
    'error.hint4': 'Java Edition only; Bedrock uses UDP 19132 and a different protocol.',
    'img.icon': 'Server icon',
    'motd.empty': '(no MOTD set)',
    'version.unknown': 'Unknown version',
    'facts.title': 'Server info',
    'badge.cached': 'cached',
    'fact.target': 'Target',
    'fact.srvFrom': 'Resolved via SRV from {0}',
    'fact.version': 'Version',
    'fact.players': 'Players',
    'fact.latency': 'Latency',
    'fact.protocol': 'Protocol',
    'fact.secure': 'Secure chat',
    'ping.legacy': 'Legacy ping',
    'ping.modern': 'Modern ping',
    'secure.on': 'Enforced',
    'secure.off': 'Not enforced',
    'players.title': 'Players',
    'players.visible': '{0} visible',
    'players.more': '+{0} more',
    'raw.title': 'Raw status JSON',
    'raw.fromServer': 'from server',
    'raw.copy': 'Copy JSON',
    'raw.copied': 'Copied ✓',
    'raw.copyFailed': 'Copy failed',
    'error.http': 'Request failed (HTTP {0})',
    'error.unknown': 'Unknown error',
    'foot.line1': 'Pure Node.js, zero dependencies',
    'foot.cli': 'CLI: ',
    'foot.cmd': 'motd <address>',
  },
};

function detectLang() {
  const query = new URLSearchParams(location.search).get('lang');
  if (query) return query.toLowerCase().startsWith('en') ? 'en' : 'zh';
  try {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved === 'zh' || saved === 'en') return saved;
  } catch {
    /* 忽略 */
  }
  return (navigator.language || '').toLowerCase().startsWith('zh') ? 'zh' : 'en';
}

let lang = detectLang();

function t(key, ...args) {
  const dict = I18N[lang] || I18N.zh;
  let text = dict[key];
  if (text === undefined) text = I18N.zh[key] !== undefined ? I18N.zh[key] : key;
  if (!args.length) return text;
  return text.replace(/\{(\d+)\}/g, (match, index) => {
    const value = args[Number(index)];
    return value === undefined ? match : String(value);
  });
}

function escapeHtml(text) {
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

let obfTimer = null;
let busy = false;
let history = loadHistory();
let lastAddress = '';
let lastResult = null;
let lastMeta = null;
let lastError = null;

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
  return Number(value || 0).toLocaleString(lang === 'en' ? 'en-US' : 'zh-CN');
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
  tip.textContent = t('loading', address);
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
  body.appendChild(el('p', 'err-title', address ? t('error.title', address) : t('error.title.empty')));
  body.appendChild(el('p', 'err-detail', message));

  const ping = el('div', 'tile-ping');
  ping.appendChild(el('span', 'ms', 'FAILED'));

  tile.append(icon, body, ping);

  const panel = el('div', 'panel players-panel');
  const hints = el('ul', 'hints');
  [t('error.hint1'), t('error.hint2'), t('error.hint3'), t('error.hint4')]
    .forEach((text) => hints.appendChild(el('li', '', text)));
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
    img.alt = t('img.icon');
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
    motd.appendChild(el('span', 'motd-empty', t('motd.empty')));
  } else {
    visible.forEach((line, index) => {
      if (index > 0) motd.appendChild(document.createElement('br'));
      motd.appendChild(renderSegments(line));
    });
  }

  const sub = el('div', 'tile-sub');
  sub.appendChild(el('span', 'players', `${formatNumber(result.players.online)}/${formatNumber(result.players.max)}`));
  sub.appendChild(el('span', 'dot', '·'));
  sub.appendChild(el('span', 'version', result.version.name || t('version.unknown')));
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
  head.appendChild(el('span', '', t('facts.title')));
  const badge = el('span', 'badge', cached ? t('badge.cached') : `${elapsedMs} ms`);
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
  const targetDd = addFact(t('fact.target'), target);
  if (result.srv) targetDd.title = t('fact.srvFrom', result.requestedHost);

  addFact(t('fact.version'), `${result.version.name}${result.version.protocol ? ` · ${result.version.protocol}` : ''}`);
  addFact(t('fact.players'), `${formatNumber(result.players.online)} / ${formatNumber(result.players.max)}`);
  addFact(t('fact.latency'), `${result.latencyMs} ms`, result.latencyMs < 120 ? 'good' : result.latencyMs < 300 ? '' : 'bad');
  addFact(t('fact.protocol'), result.protocolUsed === 'legacy' ? t('ping.legacy') : t('ping.modern'));
  addFact(t('fact.secure'), result.checks && result.checks.secureChat ? t('secure.on') : t('secure.off'));

  panel.append(head, facts);
  return panel;
}

function buildPlayers(result) {
  const sample = (result.players && result.players.sample) || [];
  if (!sample.length) return null;

  const panel = el('div', 'panel');
  const head = el('div', 'panel-head');
  head.appendChild(el('span', '', t('players.title')));
  head.appendChild(el('span', 'badge', t('players.visible', sample.length)));

  const body = el('div', 'players-panel');
  const list = el('ul', 'player-chips');
  sample.slice(0, 20).forEach((player) => {
    list.appendChild(el('li', '', player.name));
  });
  if (sample.length > 20) {
    list.appendChild(el('li', '', t('players.more', sample.length)));
  }
  body.appendChild(list);
  panel.append(head, body);
  return panel;
}

function buildRaw(result) {
  const details = el('details', 'panel raw');
  const summary = document.createElement('summary');
  summary.appendChild(document.createTextNode(t('raw.title')));
  const hint = el('span', 'badge', t('raw.fromServer'));
  summary.appendChild(hint);

  const tools = el('div', 'raw-tools');
  const copy = el('button', 'copy-btn', t('raw.copy'));
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
      copy.textContent = t('raw.copied');
    } catch {
      copy.textContent = t('raw.copyFailed');
    }
    setTimeout(() => { copy.textContent = t('raw.copy'); }, 1600);
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

function renderEmpty() {
  stopObfuscation();
  output.innerHTML = `
    <div class="empty">
      <div class="empty-lines" aria-hidden="true"></div>
      <p class="empty-title">${escapeHtml(t('empty.title'))}</p>
      <p class="empty-sub">${escapeHtml(t('empty.sub'))}</p>
      <div class="examples">
        <button type="button" class="example" data-address="play.cubecraft.net">play.cubecraft.net</button>
        <button type="button" class="example" data-address="mc.hypixel.net">mc.hypixel.net</button>
        <button type="button" class="example" data-address="2b2t.org">2b2t.org</button>
      </div>
    </div>`;
}

/** 应用语言：静态文案 + 按当前状态重渲染 */
function applyLanguage() {
  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
  try {
    localStorage.setItem(LANG_KEY, lang);
  } catch {
    /* 忽略 */
  }
  if (langBtn) langBtn.textContent = lang === 'zh' ? 'EN' : '中文';
  if (metaDesc) metaDesc.content = t('page.desc');
  if (brandTitle) brandTitle.innerHTML = '<span class="pixel">MOTD</span> ' + escapeHtml(t('brand.suffix'));
  if (input) input.placeholder = t('placeholder');
  if (addrLabel) addrLabel.textContent = t('form.label');
  if (historyLabel) historyLabel.textContent = t('history.label');
  if (foot1) foot1.textContent = t('foot.line1');
  if (foot2) foot2.innerHTML = escapeHtml(t('foot.cli')) + '<code>' + escapeHtml(t('foot.cmd')) + '</code>';

  if (lastResult) {
    const plain = (lastResult.motd && lastResult.motd.plain) || '';
    document.title = `${plain.split('\n')[0].trim() || lastAddress} · MOTD`;
  } else {
    document.title = t('page.title');
  }

  if (busy) {
    renderLoading(lastAddress);
  } else if (lastResult) {
    renderResult(lastResult, lastMeta || { elapsedMs: 0, cached: false });
  } else if (lastError) {
    renderError(lastAddress, lastError);
  } else {
    renderEmpty();
  }
  renderHistory();
}

/* ---------------------------------------------------------------- 查询 */

async function query(address) {
  const target = String(address || '').trim();
  if (!target || busy) return;

  busy = true;
  submit.disabled = true;
  submit.querySelector('.pixel').textContent = '…';
  lastAddress = target;
  lastError = null;
  const started = performance.now();
  renderLoading(target);

  try {
    const response = await fetch(`/api/ping?address=${encodeURIComponent(target)}&lang=${lang}`);
    const payload = await response.json();
    if (!payload.ok) throw new Error(payload.error || t('error.http', response.status));

    const elapsedMs = Math.round(performance.now() - started);
    const meta = { elapsedMs, cached: payload.cached };
    lastResult = payload.result;
    lastMeta = meta;
    lastError = null;
    renderResult(payload.result, meta);
    saveHistory(target);
    document.title = `${payload.result.motd.plain.split('\n')[0].trim() || target} · MOTD`;
  } catch (err) {
    lastResult = null;
    lastError = err.message || t('error.unknown');
    renderError(target, lastError);
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

output.addEventListener('click', (event) => {
  const button = event.target && event.target.closest ? event.target.closest('.example') : null;
  if (!button) return;
  const address = button.dataset.address;
  input.value = address;
  query(address);
});

if (langBtn) {
  langBtn.addEventListener('click', () => {
    lang = lang === 'zh' ? 'en' : 'zh';
    applyLanguage();
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

// 截图/测试用：外部可预置查询结果，直接同步渲染
const preloaded = window.__MC_MOTD_PRELOAD__;
if (preloaded && preloaded.result) {
  lastResult = preloaded.result;
  lastMeta = { elapsedMs: preloaded.elapsedMs || 0, cached: Boolean(preloaded.cached) };
  lastAddress = preloaded.result.query || '';
  input.value = lastAddress;
  saveHistory(lastAddress);
}

applyLanguage();

const params = new URLSearchParams(location.search);
const initial = params.get('address');
if (initial && !preloaded) {
  input.value = initial;
  query(initial);
} else if (!preloaded) {
  input.focus();
}
