## 桌面版 GUI（预发布）

Java Swing 写的原生窗口程序，不是网页。同一套代码跑 macOS / Windows / Linux / BSD。

### 下载哪个

| 平台 | 文件 |
|---|---|
| Windows x64 | `mc-motd-1.1.0-windows-x64.zip`（解压后运行 `mc-motd.exe`） |
| macOS Intel | `mc-motd-1.1.0-macos-x86_64.dmg` |
| macOS Apple Silicon | `mc-motd-1.1.0-macos-arm64.dmg` |
| Linux x64 | `mc-motd-1.1.0-linux-x64.tar.gz` |
| 任意系统（需 Java 11+） | `mc-motd-gui.jar` |

BSD 用户直接跑 jar：`pkg install openjdk` 之后 `java -jar mc-motd-gui.jar`。

### 未签名提示

预发布包没有代码签名：

- **macOS**：右键 → 打开；或 `xattr -dr com.apple.quarantine "/Applications/MOTD Viewer.app"`
- **Windows**：SmartScreen 里点“更多信息 → 仍要运行”

### 功能

- 输入 `host[:port]`，回车或点 PING
- 服务器列表卡片：favicon、两行 MOTD、人数、延迟信号条
- 详情：目标 / 版本 / 在线 / 延迟 / 协议 / 安全聊天
- 玩家列表、原始 JSON（可展开、可复制）
- 支持代理（`http://` / `socks5://`），查询历史

### 已知限制

- 只支持 Java 版
- 需要能直连目标端口；海外服请填代理
- 未签名、未公证，首次打开需要手动放行
