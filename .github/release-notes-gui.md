## 桌面版 GUI（预发布）

Java Swing 写的原生窗口程序，不是网页。同一套代码跑 macOS / Windows / Linux / BSD。

### 下载哪个

| 平台 | 文件 |
|---|---|
| Windows x64 | `mc-motd-1.1.0-windows-x64.zip`（解压运行 `mc-motd.exe`） |
| Linux x64 | `mc-motd-1.1.0-linux-x64.tar.gz` |
| macOS / BSD / 其他 | `mc-motd-1.1.0-portable.zip` 或 `mc-motd-gui.jar`（需 Java 11+） |

macOS 没有单独的 dmg：GitHub 这台仓库的 macOS runner 暂不可用，所以 macOS 用通用 jar / 便携包，
解压后 `./mc-motd` 或直接 `java -jar mc-motd-gui.jar` 即可。BSD 同理。

### 未签名提示

预发布包没有代码签名：

- **macOS**：直接运行 jar，或对解压出的脚本用 `xattr -dr com.apple.quarantine` 放行
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
