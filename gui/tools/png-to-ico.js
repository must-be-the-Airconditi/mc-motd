#!/usr/bin/env node
'use strict';

/**
 * 把一张 256×256 PNG 包成单帧 ICO（Windows 图标）。
 * ICO 允许直接内嵌 PNG 数据，所以只需要拼文件头和目录项。
 *
 *   node gui/tools/png-to-ico.js gui/icon.png icon.ico
 */

const fs = require('fs');

const [, , input, output] = process.argv;
if (!input || !output) {
  console.error('用法: node png-to-ico.js <in.png> <out.ico>');
  process.exit(1);
}

const png = fs.readFileSync(input);
const header = Buffer.alloc(6);
header.writeUInt16LE(0, 0); // reserved
header.writeUInt16LE(1, 2); // type: icon
header.writeUInt16LE(1, 4); // count

const entry = Buffer.alloc(16);
entry[0] = 0; // width 256
entry[1] = 0; // height 256
entry[2] = 0; // palette
entry[3] = 0; // reserved
entry.writeUInt16LE(1, 4);  // color planes
entry.writeUInt16LE(32, 6); // bits per pixel
entry.writeUInt32LE(png.length, 8);
entry.writeUInt32LE(22, 12); // data offset

fs.writeFileSync(output, Buffer.concat([header, entry, png]));
console.log(`已生成 ${output}（${png.length} 字节 PNG）`);
