## CLI / 网页版

命令行 + 本地网页版，零运行时依赖。

### 安装

```bash
# 源码
git clone https://github.com/must-be-the-Airconditi/mc-motd
cd mc-motd && npm link

# 或直接用附件里的 tarball
npm install -g mc-motd-*.tgz
```

### 使用

```bash
motd play.cubecraft.net
motd play.cubecraft.net:25565 --timeout 8000
motd localhost --proxy http://127.0.0.1:31181
motd --web                 # 网页版 → http://127.0.0.1:8720
motd --json <地址>          # 机器可读输出
```

### 说明

- 附件 `mc-motd-*.tgz` 是 `npm pack` 打的包，`npm install -g` 后可直接用 `motd` 命令
- 也支持 `node motd.js` 直接跑，不需要安装
- 网页版界面支持中文 / English：右上角切换，默认跟随浏览器语言；报错也会跟着语言走
- CLI 终端输出目前仍是中文
- 桌面 GUI 请下 `gui-v*` 那条 tag 的 release
