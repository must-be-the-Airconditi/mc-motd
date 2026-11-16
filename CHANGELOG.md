# Changelog

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

改动先记在 `Unreleased` 下，发版时再切到具体版本。

## [Unreleased]

### Added

- 网页版与桌面版新增 Catppuccin 配色：主题切换增加 Mocha / Latte 两档，跟随系统时仍按系统明暗自动切换

## [cli-v1.4.1] - 2026-11-16

### Fixed

- 网页版：基岩版查询遇到非法地址时会泄漏并发计数，连续几次后整个服务一直返回 429；非法地址现在直接返回 400
- IPv6：SOCKS5 / HTTP 代理地址里的方括号、HTTP CONNECT 请求行的 IPv6 目标都按规范处理（之前会连不上）
- SRV 查询：忽略 weight 和 target 为 `.`（服务不可用）的记录，避免连到不存在的主机
- 版本名缺失时跟随语言显示（英文界面不再出现中文「未知」）

## [gui-v1.4.1] - 2026-11-16

### Fixed

- IPv6：SOCKS5 / HTTP 代理地址里的方括号、HTTP CONNECT 请求行的 IPv6 目标都按规范处理
- SRV 查询：忽略 weight 和 target 为 `.` 的记录
- 版本名缺失时跟随语言显示（`Unknown version` / `未知版本`）
- 查询失败后切换主题/语言会重新显示上一次的旧结果
- 探测系统明暗时命令超时不再卡住，Windows 查询失败按浅色处理

## [cli-v1.4.0] - 2026-11-15

### Changed

- 网页版重做 Material 3：明暗双套颜色、M3 字阶、搜索栏 / 填充按钮 / assist chip / outlined 卡片，去掉像素字体和装饰
- 浅色主题下把 Minecraft 的高亮色（白、亮绿、亮黄）压暗，切换明暗时重渲染
- zsh / fish 补全的说明文字跟随语言（`MC_MOTD_LANG` > `LC_ALL` > `LANG`，`en*` 英文，其余中文），和 CLI 使用同一套规则

### Removed

- 网页版像素字体 Press Start 2P（`public/fonts/`）

## [gui-v1.4.0] - 2026-11-15

### Added

- 桌面版改用 JavaFX + Material 3 重做：跟随系统明暗、草绿主色、主题按钮（跟随系统 / 浅色 / 深色），窗口、搜索栏、卡片、玩家列表和原始 JSON 全部重写
- `gui/fetch-javafx.sh`：按平台自动下载 JavaFX 21.0.7（默认阿里云镜像，`JAVAFX_MIRROR` 可换源）
- 桌面版 `--shot` 离屏截图：JavaFX 直接渲染 PNG，不用开窗口

### Changed

- 桌面版需要 Java 17+ 与 JavaFX 21；`portable.zip` 改为自带 `javafx/` 目录和启动脚本，安装包体积相应增大
- 浅色主题下把 Minecraft 的高亮色压暗，切换明暗时重渲染

### Removed

- 桌面版 Swing 界面（`Ui.java` / `Theme.java`，git 历史保留）

## [cli-v1.3.0] - 2026-11-14

### Added

- 基岩版（Bedrock Edition）支持：RakNet Unconnected Ping，CLI 加 `--bedrock`，网页版可选 Java / Bedrock，接口参数 `edition=bedrock`
- Shell 补全：`completions/_motd`（zsh，oh-my-zsh 可直接用）、`completions/motd.bash`、`completions/motd.fish`
- `completions/install.sh`：一条命令装好补全，自动识别 oh-my-zsh / 原生 zsh / bash / fish，支持 `--shell`、`--bashrc` 和 `--uninstall`，npm 全局安装的用户也能用

### Changed

- 网页版、CLI 和文档里的 “Java Edition” 文案统一改为 “Java / Bedrock”
- release notes 改为从 `CHANGELOG.md` 的对应版本小节自动生成，更新内容为主，下载/安装说明放尾部
- CI 测试矩阵加上 macOS；补全测试在 CI 里真的装 fish 跑，不再只是语法检查

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
- 新增补全冒烟测试 `test/completion.test.sh`（40 项，含 zsh / bash / fish 实际补全验证和配置块安全用例）

## [gui-v1.3.0] - 2026-11-14

### Added

- 桌面 GUI 支持基岩版：地址框右侧可切 Java / Bedrock，基岩版显示 RakNet 协议、安全聊天标为“不适用”
- macOS 安装包：Apple Silicon 的 `.dmg` 和 `.app.zip`（内置运行时，无需装 Java），CI 增加 macOS 打包矩阵
- `gui` 工作流支持 `dry_run` 手动试打包，不发布也能验证三平台产物

### Changed

- 桌面 GUI 文案统一为 “Java / Bedrock”

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
