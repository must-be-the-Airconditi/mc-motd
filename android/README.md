# mc-motd for Android

原生 Kotlin 小应用（无 AndroidX、无 Compose），复刻 CLI / 网页 / 桌面版的能力：

- 支持 **Java 版**（TCP 25565，Server List Ping）与 **基岩版**（UDP 19132，RakNet unconnected ping）
- 读取 MOTD（含颜色/粗体/斜体/下划线/删除线）、在线人数、版本、协议号、延迟
- 纯代码 UI，Catppuccin Mocha 配色，minSdk 26（Android 8.0+）

## 构建

需要 JDK 17+、Android SDK（platform 35 + build-tools 35.0.1）和 Gradle 8.9+：

```bash
# 1) 指定 SDK 路径
echo "sdk.dir=$HOME/android-sdk" > local.properties

# 2) 构建 debug APK
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

国内网络建议在 `settings.gradle.kts` 里把 `google()` / `mavenCentral()` 换成阿里云镜像
（`https://maven.aliyun.com/repository/google`、`.../public`），Android SDK 包可从
`https://mirrors.cloud.tencent.com/AndroidSDK/` 手动下载：

- `commandlinetools-linux-11076708_latest.zip` → `$ANDROID_SDK/cmdline-tools/latest`
- `platform-35_r02.zip` → `$ANDROID_SDK/platforms/android-35`
- `build-tools_r35.0.1_linux.zip` → `$ANDROID_SDK/build-tools/35.0.1`
- `platform-tools_r37.0.3-linux.zip` → `$ANDROID_SDK/platform-tools`

## 结构

```
app/src/main/java/dev/mcmotd/app/
  MainActivity.kt   # 界面与状态
  Slp.kt            # Java 版 SLP（握手、VarInt、状态 JSON）
  Bedrock.kt        # 基岩版 RakNet ping
  Motd.kt           # chat component → Spannable
```
