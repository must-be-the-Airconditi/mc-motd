# Changelog

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

改动先记在 `Unreleased` 下，发版时再切到具体版本。

## [Unreleased]

### Added

- 基岩版（Bedrock Edition）支持：RakNet Unconnected Ping，CLI 加 `--bedrock`，网页版可选 Java / Bedrock，接口参数 `edition=bedrock`
- 桌面 GUI 也支持基岩版：地址框右侧可切 Java / Bedrock，基岩版显示 RakNet 协议、安全聊天标为“不适用”

### Changed

- 网页版、CLI、GUI 和文档里的 “Java Edition” 文案统一改为 “Java / Bedrock”

### Fixed

- 旧版 Ping 改用 socket 空闲超时，去掉用户可控时长的 JS 定时器（CodeQL #6）
- 网页版没有历史记录时，「最近」栏仍然显示（`hidden` 被 CSS `display:flex` 覆盖）
- `https://` 代理未写端口时默认成了 1080，正确应为 443
- 截图预载路径会把空地址写进历史记录
- Java 旧版协议响应读到 EOF 时 `chars` 可能为负，导致 `NegativeArraySizeException`
- `motd --web --lang en` 的启动横幅仍是中文

### Tests

- 补充 i18n 插值、语言判断和代理默认端口的用例
- 新增基岩版解析与本地 UDP 假服务器测试（22 → 27）

## [cli-v1.2.0] - 2026-11-13

### Added

- CLI 支持中英文输出：`--lang zh|en`、`MC_MOTD_LANG`，默认跟随系统语言
- CLI 的帮助、进度、卡片、参数错误和失败信息全部本地化

## [cli-v1.1.0] - 2026-11-13

### Added

- 网页版界面支持中英文切换（右上角），默认跟随浏览器语言，选择记在 localStorage
- 服务端错误信息按 `lang` 参数本地化（`slp.js` / `proxy.js` / `web.js`）
- 新增 `src/i18n.js` 消息表

## [gui-v1.2.0] - 2026-11-13

### Added

- 桌面 GUI 支持中文 / English：右上角切换，默认跟随系统语言，选择会被记住
- 也可用 `-Dmcmotd.lang=en` 或 `MC_MOTD_LANG=en` 强制指定

## [v1.1.0] - 2026-11-08

### Added

- 桌面 GUI（Java Swing），一套源码跑 macOS / Windows / Linux / BSD
- GitHub Actions 打包 Windows / Linux 安装包 + 通用 jar + portable 包
- 服务器卡片、详情、玩家列表、原始 JSON、查询历史、代理输入框
- 无头截图模式（`--shot`）

## [v1.0.0] - 2026-11-08

### Added

- 命令行版：终端彩色 MOTD 卡片、`--json`、多地址查询
- 本地网页版：历史记录、玩家列表、原始 JSON、`/api/ping`
- SLP 协议：现代协议 + 旧版回退、SRV 自动解析
- 代理支持：SOCKS5 / SOCKS4 / HTTP CONNECT
- 假服务器与 17 个测试用例
