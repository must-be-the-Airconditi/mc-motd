'use strict';

/**
 * MOTD / 聊天组件解析
 *  - 把服务器返回的 description（字符串 / JSON 聊天组件）拍平成带样式的片段
 *  - 处理 § 传统颜色码（包括 §x§R§R§G§G§B§B 十六进制）
 *  - 提供终端 ANSI 渲染
 */

const MINECRAFT_COLORS = {
  black: '#000000',
  dark_blue: '#0000AA',
  dark_green: '#00AA00',
  dark_aqua: '#00AAAA',
  dark_red: '#AA0000',
  dark_purple: '#AA00AA',
  gold: '#FFAA00',
  gray: '#AAAAAA',
  dark_gray: '#555555',
  blue: '#5555FF',
  green: '#55FF55',
  aqua: '#55FFFF',
  red: '#FF5555',
  light_purple: '#FF55FF',
  yellow: '#FFFF55',
  white: '#FFFFFF',
};

const CODE_TO_COLOR = {
  0: '#000000', 1: '#0000AA', 2: '#00AA00', 3: '#00AAAA',
  4: '#AA0000', 5: '#AA00AA', 6: '#FFAA00', 7: '#AAAAAA',
  8: '#555555', 9: '#5555FF', a: '#55FF55', b: '#55FFFF',
  c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF',
};

const DEFAULT_COLOR = '#FFFFFF';
const DEFAULT_STYLE = Object.freeze({
  color: DEFAULT_COLOR,
  bold: false,
  italic: false,
  underlined: false,
  strikethrough: false,
  obfuscated: false,
});

const STYLE_KEYS = ['bold', 'italic', 'underlined', 'strikethrough', 'obfuscated'];

/* ------------------------------------------------------------ 颜色解析 */

function normalizeColor(value) {
  if (typeof value !== 'string') return null;
  const name = value.toLowerCase();
  if (MINECRAFT_COLORS[name]) return MINECRAFT_COLORS[name];
  if (/^#[0-9a-f]{6}$/.test(name)) return name;
  if (/^#[0-9a-f]{3}$/.test(name)) {
    return `#${name[1]}${name[1]}${name[2]}${name[2]}${name[3]}${name[3]}`;
  }
  return null;
}

/* ------------------------------------------------------- § 传统颜色码 */

function codeToStyle(code, style) {
  const next = { ...style };
  if (code === 'r') return { ...DEFAULT_STYLE };
  if (CODE_TO_COLOR[code]) {
    next.color = CODE_TO_COLOR[code];
    return next;
  }
  switch (code) {
    case 'k': next.obfuscated = true; break;
    case 'l': next.bold = true; break;
    case 'm': next.strikethrough = true; break;
    case 'n': next.underlined = true; break;
    case 'o': next.italic = true; break;
    default: return null;
  }
  return next;
}

/** 解析一段可能含 § 码的文本，追加到 segments */
function pushStyledText(text, style, segments) {
  if (!text) return;
  let current = { ...style };
  let buffer = '';

  const flush = () => {
    if (!buffer) return;
    segments.push({ ...current, text: buffer });
    buffer = '';
  };

  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (ch !== '\u00a7' || i + 1 >= text.length) {
      buffer += ch;
      continue;
    }

    const code = text[i + 1].toLowerCase();
    if (!/[0-9a-fk-orx]/.test(code)) {
      buffer += ch;
      continue;
    }

    // §x§f§f§8§8§0§0 形式的十六进制颜色
    if (code === 'x') {
      const hex = [];
      let j = i + 2;
      while (hex.length < 6 && j + 1 < text.length && text[j] === '\u00a7') {
        hex.push(text[j + 1]);
        j += 2;
      }
      if (hex.length === 6 && /^[0-9a-f]{6}$/i.test(hex.join(''))) {
        flush();
        current.color = `#${hex.join('').toLowerCase()}`;
        i = j - 1;
        continue;
      }
    }

    const next = codeToStyle(code, current);
    if (next) {
      flush();
      current = next;
      i += 1;
    } else {
      buffer += ch;
    }
  }
  flush();
}

/* --------------------------------------------------------- 组件拍平 */

function walk(node, style, segments) {
  if (node === null || node === undefined) return;

  if (typeof node === 'string' || typeof node === 'number') {
    pushStyledText(String(node), style, segments);
    return;
  }

  if (Array.isArray(node)) {
    for (const child of node) walk(child, style, segments);
    return;
  }

  if (typeof node !== 'object') return;

  let next = { ...style };

  if (node.color !== undefined) {
    const color = normalizeColor(node.color);
    if (node.color === 'reset') next = { ...DEFAULT_STYLE };
    else if (color) next.color = color;
  }
  for (const key of STYLE_KEYS) {
    if (typeof node[key] === 'boolean') next[key] = node[key];
  }

  let text = null;
  if (node.text !== undefined && node.text !== null) text = String(node.text);
  else if (typeof node.translate === 'string') {
    text = node.translate;
    if (Array.isArray(node.with)) {
      text += ` ${node.with.map((child) => toPlain([], child)).join(' ')}`;
    }
  } else if (node.score && typeof node.score.value === 'string') text = node.score.value;
  else if (typeof node.selector === 'string') text = node.selector;
  else if (typeof node.keybind === 'string') text = node.keybind;
  else if (typeof node.insertion === 'string') text = '';

  if (text) pushStyledText(text, next, segments);
  if (node.extra !== undefined) walk(node.extra, next, segments);
}

/** 小工具：把任意组件转成纯文本，用于 translate 的 with 参数 */
function toPlain(segments, node) {
  walk(node, { ...DEFAULT_STYLE }, segments);
  return segments.map((seg) => seg.text).join('');
}

/* ------------------------------------------------------------- 导出对象 */

function mergeSegments(segments) {
  const out = [];
  for (const seg of segments) {
    const prev = out[out.length - 1];
    const sameStyle =
      prev &&
      prev.color === seg.color &&
      STYLE_KEYS.every((key) => prev[key] === seg[key]);
    if (sameStyle) prev.text += seg.text;
    else out.push({ ...seg });
  }
  return out;
}

function splitLines(segments) {
  const lines = [[]];
  for (const seg of segments) {
    const parts = seg.text.split('\n');
    parts.forEach((part, index) => {
      if (index > 0) lines.push([]);
      if (part) lines[lines.length - 1].push({ ...seg, text: part });
    });
  }
  return lines;
}

/**
 * 解析 description 字段。
 * @returns {{segments: object[], lines: object[][], plain: string, plainLines: string[]}}
 */
function parseMOTD(description) {
  const segments = [];
  if (typeof description === 'string') {
    pushStyledText(description, { ...DEFAULT_STYLE }, segments);
  } else if (description && typeof description === 'object') {
    walk(description, { ...DEFAULT_STYLE }, segments);
  }
  const merged = mergeSegments(segments);
  const lines = splitLines(merged);
  return {
    segments: merged,
    lines,
    plain: merged.map((seg) => seg.text).join(''),
    plainLines: lines.map((line) => line.map((seg) => seg.text).join('')),
  };
}

/* ---------------------------------------------------------- ANSI 渲染 */

function hexToRgb(hex) {
  const value = hex.replace('#', '');
  return {
    r: parseInt(value.slice(0, 2), 16),
    g: parseInt(value.slice(2, 4), 16),
    b: parseInt(value.slice(4, 6), 16),
  };
}

function ansiFor(segment) {
  const { r, g, b } = hexToRgb(segment.color || DEFAULT_COLOR);
  let prefix = `\x1b[38;2;${r};${g};${b}m`;
  if (segment.bold) prefix += '\x1b[1m';
  if (segment.italic) prefix += '\x1b[3m';
  if (segment.underlined) prefix += '\x1b[4m';
  if (segment.strikethrough) prefix += '\x1b[9m';
  return prefix;
}

const RESET = '\x1b[0m';

/** 把 segments 渲染成带 ANSI 颜色的单行文本 */
function toAnsi(segments, { color = true } = {}) {
  if (!color) return segments.map((seg) => seg.text).join('');
  return segments
    .map((seg) => `${ansiFor(seg)}${seg.text}${RESET}`)
    .join('');
}

/* --------------------------------------------------------- 状态规范化 */

/** 去掉 § 颜色/格式码，用于玩家名等纯文本场景 */
function stripFormatting(text) {
  return String(text).replace(/\u00a7[0-9a-fk-orx]/gi, '');
}

function normalizeFavicon(favicon) {
  if (typeof favicon !== 'string' || !favicon.trim()) return null;
  const value = favicon.trim();
  if (value.startsWith('data:image/')) return value;
  return `data:image/png;base64,${value}`;
}

/**
 * 把 ping 结果整理成前端/CLI 通用的结构。
 */
function buildResult(pingResult) {
  const { status = {} } = pingResult;
  const motd = parseMOTD(status.description ?? status.text ?? '');
  const sample = Array.isArray(status.players && status.players.sample)
    ? status.players.sample
        .slice(0, 100)
        .map((p) => ({
        name: stripFormatting(p && p.name ? p.name : ''),
        id: (p && p.id) || null,
      }))
        .filter((p) => p.name)
    : [];

  return {
    online: true,
    edition: pingResult.edition || 'java',
    query: pingResult.query,
    host: pingResult.host,
    port: pingResult.port,
    requestedHost: pingResult.requestedHost,
    requestedPort: pingResult.requestedPort,
    srv: pingResult.srv,
    protocolUsed: pingResult.protocolUsed,
    latencyMs: Math.round(pingResult.latencyMs * 10) / 10,
    version: {
      name: (status.version && status.version.name) || '未知',
      protocol: status.version && Number.isFinite(status.version.protocol)
        ? status.version.protocol
        : null,
    },
    players: {
      online: (status.players && status.players.online) || 0,
      max: (status.players && status.players.max) || 0,
      sample,
    },
    motd,
    favicon: normalizeFavicon(status.favicon),
    raw: JSON.stringify(status),
    checks: {
      secureChat: status.enforcesSecureChat === true,
      previewsChat: status.previewsChat === true,
    },
  };
}

module.exports = {
  parseMOTD,
  stripFormatting,
  buildResult,
  toAnsi,
  normalizeColor,
  MINECRAFT_COLORS,
  DEFAULT_COLOR,
};
