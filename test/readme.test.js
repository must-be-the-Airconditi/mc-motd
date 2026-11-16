'use strict';

/**
 * README 里的「命令行」帮助片段必须和真实的 `motd --help` 首行一致，
 * 版本号升级后忘了同步 README 时，这条测试会直接失败。
 */

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.join(__dirname, '..');

function helpFirstLine(lang) {
  const env = { ...process.env, MC_MOTD_LANG: lang };
  const out = spawnSync(process.execPath, [path.join(root, 'motd.js'), '--help'], {
    cwd: root,
    env,
    encoding: 'utf8',
  });
  return out.stdout.split('\n')[0].trim();
}

/** 取 `## 命令行` / `## CLI` 之后第一个代码块的首行 */
function readmeSnippet(file) {
  const text = fs.readFileSync(path.join(root, file), 'utf8');
  const heading = file === 'README.md' ? '## 命令行' : '## CLI';
  const index = text.indexOf(heading);
  assert.notStrictEqual(index, -1, `${file} 缺少 ${heading} 小节`);
  const fence = text.indexOf('```', index);
  const start = text.indexOf('\n', fence) + 1;
  return text.slice(start, text.indexOf('\n', start)).trim();
}

test('README 的帮助片段与 motd --help 首行一致', () => {
  assert.strictEqual(readmeSnippet('README.md'), helpFirstLine('zh'));
  assert.strictEqual(readmeSnippet('README.en.md'), helpFirstLine('en'));
});
