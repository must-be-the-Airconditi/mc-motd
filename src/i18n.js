'use strict';

/**
 * 极简国际化：中文 / English。
 * 支持 {0} {1} 占位符，避免 printf 风格在中文里的坑。
 */

const MESSAGES = {
  /* ---- 协议 / 读取 ---- */
  'err.varintTooLong': { zh: 'VarInt 过长（数据损坏）', en: 'Malformed VarInt (corrupted data)' },
  'err.packetTooLarge': { zh: '服务器返回的包过大', en: 'Response packet is too large' },
  'err.connClosed': { zh: '服务器关闭了连接', en: 'Server closed the connection' },
  'err.readTimeout': { zh: '等待服务器响应超时', en: 'Timed out waiting for the server' },
  'err.badStatus': { zh: '状态响应格式不正确', en: 'Malformed status response' },
  'err.statusIncomplete': { zh: '状态响应长度不完整', en: 'Incomplete status response' },
  'err.badJson': { zh: '服务器返回的 JSON 无法解析', en: 'Server returned unparsable JSON' },
  'err.emptyStatus': { zh: '状态数据为空', en: 'Status data is empty' },
  'err.legacyTimeout': { zh: '旧版 Ping 超时', en: 'Legacy ping timed out' },
  'err.closedBeforeResponse': {
    zh: '服务器在响应前关闭了连接',
    en: 'Server closed the connection before responding',
  },
  'err.badLegacy': { zh: '旧版 Ping 响应格式不正确', en: 'Malformed legacy ping response' },
  'err.pingFailed': { zh: 'Ping 失败：{0}', en: 'Ping failed: {0}' },

  /* ---- 地址解析 ---- */
  'addr.empty': { zh: '地址不能为空', en: 'Address must not be empty' },
  'addr.ipv6Bracket': { zh: 'IPv6 地址缺少 ]', en: 'IPv6 address is missing a closing ]' },
  'addr.badFormat': { zh: '地址格式不正确', en: 'Invalid address format' },
  'addr.badPort': { zh: '端口必须是数字', en: 'Port must be a number' },
  'addr.noHost': { zh: '缺少主机名', en: 'Missing hostname' },
  'addr.hostTooLong': { zh: '主机名过长', en: 'Hostname is too long' },
  'addr.badHost': { zh: '主机名包含非法字符', en: 'Hostname contains invalid characters' },
  'addr.portRange': { zh: '端口超出范围 (1-65535)', en: 'Port out of range (1-65535)' },

  /* ---- 连接错误 ---- */
  'conn.refused': {
    zh: '连接被拒绝：{0}:{1} 上没有服务在监听，或端口写错了',
    en: 'Connection refused: nothing is listening on {0}:{1}, or the port is wrong',
  },
  'conn.timeout': { zh: '连接超时：{0}:{1}', en: 'Connection timed out: {0}:{1}' },
  'conn.dns': { zh: '找不到主机：{0}（DNS 解析失败）', en: 'Unknown host: {0} (DNS lookup failed)' },
  'conn.proxyRefused': { zh: 'HTTP 代理拒绝 CONNECT：{0}', en: 'HTTP proxy refused CONNECT: {0}' },
  'conn.dnsTimeout': { zh: 'DNS 查询超时：{0}', en: 'DNS lookup timed out: {0}' },
  'conn.reset': { zh: '连接被服务器重置：{0}:{1}', en: 'Connection reset by server: {0}:{1}' },
  'conn.hostUnreachable': { zh: '主机不可达：{0}', en: 'Host unreachable: {0}' },
  'conn.netUnreachable': { zh: '网络不可达：{0}', en: 'Network unreachable: {0}' },

  /* ---- 代理 ---- */
  'proxy.unsupported': { zh: '不支持的代理类型：{0}', en: 'Unsupported proxy type: {0}' },
  'proxy.missingHost': { zh: '代理地址缺少主机名', en: 'Proxy address is missing a hostname' },
  'proxy.badPort': { zh: '代理端口不正确', en: 'Invalid proxy port' },
  'proxy.connectTimeout': { zh: '连接代理 {0}:{1} 超时', en: 'Timed out connecting to proxy {0}:{1}' },
  'proxy.connectFail': {
    zh: '无法连接代理 {0}:{1}：{2}',
    en: 'Cannot connect to proxy {0}:{1}: {2}',
  },
  'proxy.closed': { zh: '代理关闭了连接', en: 'Proxy closed the connection' },
  'proxy.timeout': { zh: '代理响应超时', en: 'Proxy response timed out' },
  'proxy.badSocks5': { zh: 'SOCKS5 代理响应异常', en: 'Bad SOCKS5 proxy response' },
  'proxy.needAuth': {
    zh: '代理要求用户名密码认证，请在地址里加上 user:pass@',
    en: 'Proxy requires authentication; add user:pass@ to the address',
  },
  'proxy.authFailed': { zh: 'SOCKS5 认证失败', en: 'SOCKS5 authentication failed' },
  'proxy.noMethod': {
    zh: '代理不支持可用的认证方式',
    en: 'Proxy offers no supported authentication method',
  },
  'proxy.socks5Failed': { zh: 'SOCKS5 连接目标失败：{0}', en: 'SOCKS5 failed to connect to target: {0}' },
  'proxy.socks4Failed': {
    zh: 'SOCKS4 连接目标失败（代码 {0}）',
    en: 'SOCKS4 failed to connect to target (code {0})',
  },
  'proxy.httpRefused': { zh: 'HTTP 代理拒绝 CONNECT：{0}', en: 'HTTP proxy refused CONNECT: {0}' },
  'proxy.headerTooBig': { zh: '代理响应头过大', en: 'Proxy response header is too large' },

  /* ---- 网页服务 ---- */
  'web.tooFrequent': { zh: '请求太频繁了，稍等一下再试', en: 'Too many requests — please slow down' },
  'web.noAddress': { zh: '请输入服务器地址', en: 'Please enter a server address' },
  'web.tooMany': {
    zh: '同时查询的服务器太多，请稍后再试',
    en: 'Too many concurrent queries — try again later',
  },
  'web.getOnly': { zh: '只支持 GET', en: 'GET only' },
  'web.internal': { zh: '服务器内部错误', en: 'Internal server error' },
};

function normalizeLang(lang) {
  return String(lang || '').toLowerCase().startsWith('en') ? 'en' : 'zh';
}

/**
 * 取一条本地化文案。
 * @param {string} lang 'zh' | 'en'
 * @param {string} key  消息 key
 * @param {...*} args   {0} {1} 占位参数
 */
function t(lang, key, ...args) {
  const entry = MESSAGES[key];
  if (!entry) return key;
  const text = entry[normalizeLang(lang)];
  if (!args.length) return text;
  return text.replace(/\{(\d+)\}/g, (match, index) => {
    const value = args[Number(index)];
    return value === undefined ? match : String(value);
  });
}

module.exports = { t, normalizeLang, MESSAGES };
