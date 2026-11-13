#!/usr/bin/env node
'use strict';

/**
 * mc-motd —— 查看 Minecraft Java 版服务器 MOTD
 *
 * 用法：
 *   motd play.example.com
 *   motd play.example.com:25565 -v
 *   motd --web
 */

const path = require('path');
const { ping } = require('./src/slp');
const { pingBedrock } = require('./src/bedrock');
const { buildResult, toAnsi } = require('./src/motd');

const pkg = require('./package.json');

let lang = detectLang();

const TEXT = {
  zh: {
    'help': `mc-motd {0} — 看 Minecraft Java 版服务器的 MOTD

用法:
  motd <地址...> [选项]        查询服务器（可一次查多个）
  motd --web                  启动网页版

地址:
  play.example.com           自动解析 SRV，默认端口 25565
  play.example.com:25565
  [::1]:25565                IPv6

选项:
  --json           输出 JSON
  --timeout <ms>   超时时间，默认 5000
  --proxy <url>    走代理，如 http://127.0.0.1:31181 / socks5://127.0.0.1:7890
  --bedrock        查询基岩版服务器（UDP 19132）
  --no-srv         不解析 SRV 记录
  --no-legacy      现代协议失败时不回退旧版 Ping
  --no-color       关闭颜色（也支持 NO_COLOR 环境变量）
  --lang <zh|en>   输出语言，默认跟随系统
  --web            启动网页版
  --port <n>       网页端口，默认 8720
  --host <h>       网页监听地址，默认 127.0.0.1
  -h, --help       显示帮助
  -v, --version    显示版本

环境变量:
  MC_MOTD_PROXY    默认代理（--proxy 优先）
  MC_MOTD_LANG     输出语言，zh 或 en
  NO_COLOR         任意值即关闭颜色

示例:
  motd play.cubecraft.net
  motd mc.hypixel.net --timeout 8000
  motd 2b2t.org --proxy socks5://127.0.0.1:7890
  motd --web --port 8720`,
    'err.optionNeedsValue': '选项 {0} 需要一个值',
    'err.unknownOption': '未知选项：{0}',
    'err.number': '{0} 必须是数字',
    'err.args': '参数错误：{0}',
    'err.fatal': '出错了：{0}',
    'progress': '正在 Ping {0} …',
    'fail': '✖ {0}：{1}',
    'card.online': '在线',
    'card.target': '目标',
    'card.players': '玩家',
  },
  en: {
    'help': `mc-motd {0} — view Minecraft Java Edition server MOTDs

Usage:
  motd <address...> [options]   query one or more servers
  motd --web                    start the web UI

Address:
  play.example.com           auto SRV lookup, default port 25565
  play.example.com:25565
  [::1]:25565                IPv6

Options:
  --json           JSON output
  --timeout <ms>   timeout, default 5000
  --proxy <url>    proxy, e.g. http://127.0.0.1:31181 / socks5://127.0.0.1:7890
  --bedrock        query a Bedrock Edition server (UDP 19132)
  --no-srv         skip SRV lookup
  --no-legacy      don't fall back to the legacy ping
  --no-color       disable colors (also honors NO_COLOR)
  --lang <zh|en>   output language, defaults to the system locale
  --web            start the web UI
  --port <n>       web UI port, default 8720
  --host <h>       bind address, default 127.0.0.1
  -h, --help       show help
  -v, --version    show version

Environment:
  MC_MOTD_PROXY    default proxy (--proxy wins)
  MC_MOTD_LANG     output language, zh or en
  NO_COLOR         any value disables colors

Examples:
  motd play.cubecraft.net
  motd mc.hypixel.net --timeout 8000
  motd 2b2t.org --proxy socks5://127.0.0.1:7890
  motd --web --port 8720`,
    'err.optionNeedsValue': 'Option {0} requires a value',
    'err.unknownOption': 'Unknown option: {0}',
    'err.number': '{0} must be a number',
    'err.args': 'Invalid arguments: {0}',
    'err.fatal': 'Error: {0}',
    'progress': 'Pinging {0} …',
    'fail': '✖ {0}: {1}',
    'card.online': 'online',
    'card.target': 'Target',
    'card.players': 'Players',
  },
};

function normalizeLang(value) {
  return String(value || '').toLowerCase().startsWith('en') ? 'en' : 'zh';
}

function detectLang() {
  const env = process.env.MC_MOTD_LANG;
  if (env) return normalizeLang(env);
  const locale = process.env.LC_ALL || process.env.LANG || '';
  if (!locale) return 'zh';
  return normalizeLang(locale);
}

function t(key, ...args) {
  let text = TEXT[lang][key];
  if (text === undefined) text = TEXT.zh[key] !== undefined ? TEXT.zh[key] : key;
  if (!args.length) return text;
  return text.replace(/\{(\d+)\}/g, (match, index) => {
    const value = args[Number(index)];
    return value === undefined ? match : String(value);
  });
}

/* ------------------------------------------------------------ 参数解析 */

function parseArgs(argv) {
  const options = {
    addresses: [],
    json: false,
    timeout: 5000,
    proxy: process.env.MC_MOTD_PROXY || null,
    srv: true,
    legacy: true,
    bedrock: false,
    color: !process.env.NO_COLOR,
    web: false,
    port: 8720,
    host: '127.0.0.1',
    lang,
    help: false,
    version: false,
  };

  const takesValue = new Set(['--timeout', '--proxy', '--port', '--host', '--lang']);

  for (let i = 0; i < argv.length; i += 1) {
    let arg = argv[i];
    let value = null;

    if (arg.startsWith('--') && arg.includes('=')) {
      const idx = arg.indexOf('=');
      value = arg.slice(idx + 1);
      arg = arg.slice(0, idx);
    }

    if (takesValue.has(arg)) {
      if (value === null) {
        value = argv[i + 1];
        i += 1;
      }
      if (value === undefined) throw new Error(t('err.optionNeedsValue', arg));
      if (arg === '--timeout') options.timeout = clampNumber(Number(value), 500, 60000, '--timeout');
      else if (arg === '--port') options.port = clampNumber(Number(value), 1, 65535, '--port');
      else if (arg === '--host') options.host = String(value);
      else if (arg === '--proxy') options.proxy = String(value);
      else if (arg === '--lang') {
        lang = normalizeLang(value);
        options.lang = lang;
      }
      continue;
    }

    switch (arg) {
      case '-h':
      case '--help': options.help = true; break;
      case '-v':
      case '--version': options.version = true; break;
      case '--json': options.json = true; break;
      case '--bedrock': options.bedrock = true; break;
      case '--no-srv': options.srv = false; break;
      case '--no-legacy': options.legacy = false; break;
      case '--no-color': options.color = false; break;
      case '--color': options.color = true; break;
      case '--web': options.web = true; break;
      default:
        if (arg.startsWith('-')) throw new Error(t('err.unknownOption', arg));
        options.addresses.push(arg);
    }
  }

  return options;
}

function clampNumber(num, min, max, name) {
  if (!Number.isFinite(num)) throw new Error(t('err.number', name));
  return Math.min(max, Math.max(min, Math.round(num)));
}

/* ------------------------------------------------------- 终端宽度处理 */

const ANSI_RE = /\x1b\[[0-9;]*m/g;

function charWidth(ch) {
  const code = ch.codePointAt(0);
  if (code < 32 || (code >= 0x7f && code < 0xa0)) return 0;
  if (code >= 0x0300 && code <= 0x036f) return 0; // 组合字符
  const wide =
    (code >= 0x1100 && code <= 0x115f) ||
    (code >= 0x2e80 && code <= 0xa4cf) ||
    (code >= 0xac00 && code <= 0xd7a3) ||
    (code >= 0xf900 && code <= 0xfaff) ||
    (code >= 0xfe30 && code <= 0xfe6f) ||
    (code >= 0xff00 && code <= 0xff60) ||
    (code >= 0xffe0 && code <= 0xffe6) ||
    (code >= 0x1f300 && code <= 0x1faff) ||
    (code >= 0x20000 && code <= 0x3fffd);
  return wide ? 2 : 1;
}

function displayWidth(text) {
  let width = 0;
  for (const ch of text.replace(ANSI_RE, '')) width += charWidth(ch);
  return width;
}

/** 按显示宽度截断纯文本，超出部分用 … 结尾 */
function clipPlain(text, maxWidth) {
  let width = 0;
  let out = '';
  for (const ch of text) {
    const w = charWidth(ch);
    if (width + w > maxWidth) return `${out}…`;
    width += w;
    out += ch;
  }
  return out;
}

/** 按显示宽度截断 MOTD 片段（保留样式） */
function clipSegments(segments, maxWidth) {
  const out = [];
  let width = 0;
  for (const seg of segments) {
    let text = '';
    for (const ch of seg.text) {
      const w = charWidth(ch);
      if (width + w > maxWidth) {
        if (text) out.push({ ...seg, text });
        if (out.length) out[out.length - 1].text += '…';
        return out;
      }
      width += w;
      text += ch;
    }
    if (text) out.push({ ...seg, text });
  }
  return out;
}

function padAnsi(text, width, align = 'left') {
  const pad = Math.max(0, width - displayWidth(text));
  if (align === 'right') return ' '.repeat(pad) + text;
  if (align === 'center') {
    const left = Math.floor(pad / 2);
    return ' '.repeat(left) + text + ' '.repeat(pad - left);
  }
  return text + ' '.repeat(pad);
}

/* ------------------------------------------------------------ 颜色工具 */

function makePalette(enabled) {
  const wrap = (open) => (text) => (enabled ? `${open}${text}\x1b[0m` : text);
  return {
    dim: wrap('\x1b[2m'),
    bold: wrap('\x1b[1m'),
    green: wrap('\x1b[38;2;85;255;85m'),
    yellow: wrap('\x1b[38;2;255;255;85m'),
    red: wrap('\x1b[38;2;255;85;85m'),
    cyan: wrap('\x1b[38;2;85;255;255m'),
    gray: wrap('\x1b[38;2;170;170;170m'),
  };
}

function latencyColor(ms) {
  if (ms < 80) return 'green';
  if (ms < 200) return 'yellow';
  return 'red';
}

function signalBars(ms) {
  if (ms < 60) return '▂▄▆█';
  if (ms < 120) return '▂▄▆';
  if (ms < 250) return '▂▄';
  if (ms < 500) return '▂';
  return '·';
}

/* ------------------------------------------------------------ 渲染卡片 */

function renderResult(result, options) {
  const c = makePalette(options.color);
  const inner = 62;
  const top = c.dim(`╭${'─'.repeat(inner)}╮`);
  const bottom = c.dim(`╰${'─'.repeat(inner)}╯`);
  const lines = [];

  const right = `${signalBars(result.latencyMs)} ${c[latencyColor(result.latencyMs)](`${result.latencyMs} ms`)}`;
  const headRoom = inner - displayWidth(right) - 4;
  const title = c.bold(clipPlain(result.query, headRoom));
  const headPad = Math.max(1, inner - displayWidth(title) - displayWidth(right) - 2);
  lines.push(`│ ${title}${' '.repeat(headPad)}${right} │`);

  lines.push(c.dim(`│ ${'·'.repeat(inner - 2)} │`));

  // MOTD：最多两行，跟游戏里一致
  const motdLines = result.motd.lines.slice(0, 2);
  if (motdLines.length === 0) motdLines.push([]);
  for (const line of motdLines) {
    const clipped = clipSegments(line, inner - 3);
    lines.push(`│  ${padAnsi(toAnsi(clipped, { color: options.color }), inner - 3)} │`);
  }

  lines.push(c.dim(`│ ${' '.repeat(inner - 2)} │`));

  const versionText = `${result.version.name}${result.version.protocol ? ` (${result.version.protocol})` : ''}`;
  const playerText = `${result.players.online}/${result.players.max} ${t('card.online')}`;
  const editionTag = result.edition === 'bedrock' ? '  ·  BEDROCK' : '';
  const meta = clipPlain(`${playerText}  ·  ${versionText}${editionTag}`, inner - 3);
  lines.push(`│  ${padAnsi(meta, inner - 3)} │`);

  const target = result.srv
    ? `${result.srv.host}:${result.srv.port}  (SRV)`
    : `${result.host}:${result.port}`;
  const targetLine = c.dim(clipPlain(`${t('card.target')} ${target}`, inner - 3));
  lines.push(`│  ${padAnsi(targetLine, inner - 3)} │`);

  if (result.players.sample.length) {
    const names = result.players.sample.map((p) => p.name).join(', ');
    const sampleLine = c.gray(clipPlain(`${t('card.players')} ${names}`, inner - 3));
    lines.push(`│  ${padAnsi(sampleLine, inner - 3)} │`);
  }

  return [top, ...lines, bottom].join('\n');
}

/* ---------------------------------------------------------------- 主流程 */

async function pingOne(address, options) {
  const started = Date.now();
  const raw = options.bedrock
    ? await pingBedrock(address, { timeout: options.timeout, lang })
    : await ping(address, {
        timeout: options.timeout,
        srv: options.srv,
        legacy: options.legacy,
        proxy: options.proxy,
        lang,
      });
  const result = buildResult(raw);
  result.elapsedMs = Date.now() - started;
  return result;
}

async function runQuery(options) {
  const results = [];
  const failures = [];

  for (const address of options.addresses) {
    if (!options.json) process.stdout.write(`${makePalette(options.color).dim(t('progress', address))}\n`);
    try {
      const result = await pingOne(address, options);
      results.push(result);
      if (!options.json) {
        process.stdout.write(`${options.addresses.length > 1 ? '\n' : ''}${renderResult(result, options)}\n`);
      }
    } catch (err) {
      failures.push({ address, error: err.message });
      if (!options.json) {
        process.stderr.write(`${makePalette(options.color).red(t('fail', address, err.message))}\n`);
      }
    }
  }

  if (options.json) {
    process.stdout.write(`${JSON.stringify({ ok: failures.length === 0, results, failures }, null, 2)}\n`);
  }

  if (failures.length) process.exitCode = 1;
}

async function main() {
  let options;
  try {
    options = parseArgs(process.argv.slice(2));
  } catch (err) {
    process.stderr.write(`${t('err.args', err.message)}\n\n${t('help', pkg.version)}\n`);
    process.exit(2);
  }

  lang = options.lang || lang;

  if (options.help) {
    process.stdout.write(`${t('help', pkg.version)}\n`);
    return;
  }
  if (options.version) {
    process.stdout.write(`${pkg.version}\n`);
    return;
  }

  if (options.web) {
    // 延迟加载，避免 CLI 模式多做无用功
    const { startWeb } = require('./src/web');
    startWeb(options);
    return;
  }

  if (!options.addresses.length) {
    process.stdout.write(`${t('help', pkg.version)}\n`);
    process.exit(2);
  }

  await runQuery(options);
}

main().catch((err) => {
  process.stderr.write(`${t('err.fatal', err.message)}\n`);
  process.exit(1);
});
