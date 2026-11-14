# 发版流程

## 原则

- `main` 是开发线：每次 push 只跑 CI，不发版
- **攒一批改动再发**，不按提交发
- 版本语义：新功能 → 小版本（`1.3.0`）；只修 bug → 补丁（`1.2.1`）；不兼容 → 大版本

## 两条独立的线

| 线路 | tag | 触发的工作流 | 产物 |
|---|---|---|---|
| CLI / 网页版（Node） | `cli-vX.Y.Z` | `.github/workflows/cli.yml` | `mc-motd-X.Y.Z.tgz` |
| 桌面 GUI（Java） | `gui-vX.Y.Z` | `.github/workflows/gui.yml` | Windows zip、Linux tar.gz、macOS dmg + app zip、portable zip、通用 jar、SHA256SUMS |

两条线各发各的，互不牵连。

## 发版步骤

1. 确认要发的改动都已合到 `main`，最近一次 CI 是绿的
2. 更新 `CHANGELOG.md`：把 `Unreleased` 的内容移进新版本小节，写上日期。
   小节标题要和 tag 完全对应（`## [cli-v1.3.0]` / `## [gui-v1.3.0]`），
   release notes 会自动从这个小节生成，更新内容是主体，下载/安装说明只是尾部附录
3. 改版本号：
   - CLI / 网页版：`package.json` 的 `version`
   - 桌面 GUI：`gui/src/mcmotd/Main.java` 的 `VERSION`
4. 本地验证：

   ```bash
   npm test
   bash gui/build.sh          # 只改了 Java 时跑
   tool="$PWD/gui/dist/mc-motd-gui.jar"; java -jar "$tool" --version
   ```

5. 提交：

   ```bash
   git commit -am "release: cli-v1.3.0"
   ```

6. 打 tag 并推送（**推送 tag 才会发版**）：

   ```bash
   git tag -a cli-v1.3.0 -m "cli-v1.3.0"
   git push origin main cli-v1.3.0
   ```

7. 等工作流跑完，检查 Release 的附件和 checksum

## 工作流做了什么

- `cli`：Node 20 / 22 跑测试 → `npm pack` → 发布 release（附件是 tgz）
- `gui`：编译 → 在 Linux / Windows runner 上 `jpackage` → 发布 release（版本号从 tag 里取）
- 两个工作流都只在 `cli-v*` / `gui-v*` tag 上才发布；普通 push 只跑测试

## 发版后

- 用 `motd --version` 和 `motd <地址>` 做一次冒烟
- 桌面版至少在一个平台上解压跑一次
- 有问题的版本不要删 tag，直接发下一个补丁版本
