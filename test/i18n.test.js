'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { t, normalizeLang } = require('../src/i18n');
const { normalizeProxy } = require('../src/proxy');

test('i18n 中文 / 英文文案', () => {
  assert.equal(t('zh', 'web.noAddress'), '请输入服务器地址');
  assert.equal(t('en', 'web.noAddress'), 'Please enter a server address');
  // 未知语言回落到中文
  assert.equal(t('fr', 'web.noAddress'), '请输入服务器地址');
  // 未知 key 原样返回
  assert.equal(t('en', 'nope.key'), 'nope.key');
});

test('i18n 占位符插值', () => {
  assert.equal(t('zh', 'conn.timeout', 'a.com', 25565), '连接超时：a.com:25565');
  assert.equal(t('en', 'conn.timeout', 'a.com', 25565), 'Connection timed out: a.com:25565');
  // 缺参数时保留占位符，不抛异常
  assert.equal(t('en', 'conn.timeout', 'a.com'), 'Connection timed out: a.com:{1}');
});

test('normalizeLang 判断', () => {
  assert.equal(normalizeLang('en'), 'en');
  assert.equal(normalizeLang('en-US'), 'en');
  assert.equal(normalizeLang('zh-CN'), 'zh');
  assert.equal(normalizeLang(undefined), 'zh');
});

test('代理默认端口：http 80 / https 443 / socks 1080', () => {
  assert.equal(normalizeProxy('http://127.0.0.1').port, 80);
  assert.equal(normalizeProxy('https://127.0.0.1').port, 443);
  assert.equal(normalizeProxy('socks5://127.0.0.1').port, 1080);
  assert.equal(normalizeProxy('127.0.0.1:7890').port, 7890);
});
