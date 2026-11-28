export type Lang = 'zh' | 'en';

const zh = {
  brand: '服务器招牌',
  'nav.features': '功能', 'nav.shots': '截图', 'nav.install': '安装', 'nav.source': '源码',
  'hero.pill': '纯 Node.js · JavaFX · Kotlin · 零运行时依赖',
  'hero.title': '一秒看清服务器的 MOTD',
  'hero.lead': '直接读 Minecraft 服务器的 Server List Ping：MOTD、在线人数、版本、延迟，Java 与基岩版都行。四种形态共用一套协议实现：命令行、本地网页、桌面应用、Android。',
  'hero.cta1': '立即安装', 'hero.cta2': '下载桌面版 / APK',
  'features.title': '能做什么',
  'f1.t': '读 MOTD', 'f1.d': '解析 chat component 与 § 颜色码，粗体/斜体/下划线/删除线/乱码都还原。',
  'f2.t': '双版本', 'f2.d': 'Java 走 TCP 25565（含 SRV 与 DoH 兜底），基岩版走 UDP 19132 的 RakNet。',
  'f3.t': '四种形态', 'f3.d': 'CLI、本地网页、JavaFX 桌面、Android 客户端，结果一致。',
  'f4.t': '零依赖', 'f4.d': 'CLI 和网页只用 Node 内置模块，不需要 npm install；桌面版只带 JavaFX。',
  'f5.t': '代理', 'f5.d': '支持 socks5 / socks4 / http(s) 代理，连不上外服时走本地代理。',
  'f6.t': '主题', 'f6.d': '跟随系统明暗，或手动切浅色 / 深色 / Catppuccin Mocha / Latte。',
  'shots.title': '长什么样',
  'shots.web': '网页版 · Catppuccin Mocha', 'shots.gui': '桌面版 · JavaFX',
  'install.title': '安装',
  'i1.t': '命令行 / 网页版', 'i1.d': '需要 Node.js 18+，不用装依赖。',
  'i2.t': '桌面版', 'i2.d': 'Windows / Linux / macOS(arm64) 安装包，自带 JavaFX，解压即用。',
  'i3.t': 'Android', 'i3.d': 'Android 8.0+，下载 APK 侧载即可，中文名「服务器招牌」。',
  'i4.t': '从源码跑', 'i4.d': '网页版一条命令；桌面版需要 JDK 17+。',
  'themes.title': '主题', 'themes.auto': '跟随系统',
  'footer.made': 'MIT 许可 · 用 Node.js / JavaFX / Kotlin 写的',
};

const en: typeof zh = {
  brand: 'MOTD Viewer',
  'nav.features': 'Features', 'nav.shots': 'Screenshots', 'nav.install': 'Install', 'nav.source': 'Source',
  'hero.pill': 'Pure Node.js · JavaFX · Kotlin · zero runtime deps',
  'hero.title': 'Read a server MOTD in one second',
  'hero.lead': 'Speaks Server List Ping directly: MOTD, player count, version and latency for Java and Bedrock servers. Four shapes share one protocol implementation — CLI, local web UI, desktop app and Android.',
  'hero.cta1': 'Get started', 'hero.cta2': 'Download desktop / APK',
  'features.title': 'What it does',
  'f1.t': 'Parse MOTD', 'f1.d': 'Chat components and § codes: bold, italic, underline, strikethrough and obfuscated text.',
  'f2.t': 'Both editions', 'f2.d': 'Java over TCP 25565 (SRV + DoH fallback), Bedrock over UDP 19132 RakNet.',
  'f3.t': 'Four shapes', 'f3.d': 'CLI, local web UI, JavaFX desktop and Android — same results everywhere.',
  'f4.t': 'Zero deps', 'f4.d': 'CLI and web use Node built-ins only, no npm install; desktop only bundles JavaFX.',
  'f5.t': 'Proxies', 'f5.d': 'socks5 / socks4 / http(s) proxies for servers you cannot reach directly.',
  'f6.t': 'Themes', 'f6.d': 'Follows the system, or pick light / dark / Catppuccin Mocha / Latte.',
  'shots.title': 'How it looks',
  'shots.web': 'Web UI · Catppuccin Mocha', 'shots.gui': 'Desktop · JavaFX',
  'install.title': 'Install',
  'i1.t': 'CLI / web', 'i1.d': 'Needs Node.js 18+, nothing to install.',
  'i2.t': 'Desktop', 'i2.d': 'Installers for Windows, Linux and macOS arm64; JavaFX bundled.',
  'i3.t': 'Android', 'i3.d': 'Android 8.0+, sideload the signed APK.',
  'i4.t': 'From source', 'i4.d': 'One command for the web UI; desktop needs JDK 17+.',
  'themes.title': 'Themes', 'themes.auto': 'Follow system',
  'footer.made': 'MIT licensed · built with Node.js / JavaFX / Kotlin',
};

export const dict = { zh, en } as const;
export const t = (lang: Lang) => (key: keyof typeof zh) => dict[lang][key];
