#!/usr/bin/env node
'use strict';

/**
 * 从 CHANGELOG.md 里抽出某个 tag 对应的版本小节，生成 release notes。
 *
 *   node tools/release-notes.js --tag cli-v1.3.0 --footer .github/release-footer-cli.md
 *
 * 输出：更新内容（CHANGELOG 小节）+ 尾部下载/安装说明。
 * 找不到对应小节时回落到 Unreleased，避免发版失败。
 */

const fs = require('fs');

function arg(flag, fallback) {
  const index = process.argv.indexOf(flag);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function sectionBody(changelog, tag) {
  const header = new RegExp(`^## \\[${escapeRegExp(tag)}\\][^\\n]*$`, 'm');
  const match = changelog.match(header);
  if (!match) return '';
  const rest = changelog.slice(match.index + match[0].length);
  const next = rest.search(/^## \[/m);
  return (next >= 0 ? rest.slice(0, next) : rest).trim();
}

const tag = arg('--tag', process.env.GITHUB_REF_NAME || '');
const changelogPath = arg('--changelog', 'CHANGELOG.md');
const footerPath = arg('--footer', '');

const changelog = fs.readFileSync(changelogPath, 'utf8');
let body = tag ? sectionBody(changelog, tag) : '';

if (!body) {
  body = sectionBody(changelog, 'Unreleased');
  if (!body) {
    body = '（本次没有单独的更新记录，详见提交历史。）';
  }
}

let output = `## 更新内容\n\n${body}\n`;

if (footerPath && fs.existsSync(footerPath)) {
  output += `\n---\n\n${fs.readFileSync(footerPath, 'utf8').trim()}\n`;
}

process.stdout.write(output);
