(function () {
  var dict = {
    en: {
      'brand': 'MOTD Viewer',
      'nav.features': 'Features', 'nav.shots': 'Screenshots', 'nav.install': 'Install',
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
      'footer.made': 'MIT licensed · built with Node.js / JavaFX / Kotlin'
    }
  };
  var zh = {};  // captured from the initial DOM
  document.querySelectorAll('[data-i18n]').forEach(function (el) {
    zh[el.dataset.i18n] = el.textContent;
  });

  function apply(lang) {
    document.documentElement.lang = lang === 'en' ? 'en' : 'zh-CN';
    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var key = el.dataset.i18n;
      el.textContent = (lang === 'en' && dict.en[key]) || zh[key];
    });
    var btn = document.getElementById('lang');
    btn.textContent = lang === 'en' ? '中文' : 'EN';
    document.title = lang === 'en'
      ? 'MOTD Viewer — Minecraft server MOTD viewer'
      : '服务器招牌 · MOTD Viewer — Minecraft 服务器 MOTD 查看器';
  }

  var lang = localStorage.getItem('site:lang') || (navigator.language.startsWith('zh') ? 'zh' : 'en');
  apply(lang);
  document.getElementById('lang').addEventListener('click', function () {
    lang = lang === 'en' ? 'zh' : 'en';
    localStorage.setItem('site:lang', lang);
    apply(lang);
  });

  var themes = ['mocha', 'latte'];
  var theme = document.documentElement.dataset.theme || 'mocha';
  var tbtn = document.getElementById('theme');
  tbtn.textContent = theme === 'mocha' ? '☾' : '☀';
  tbtn.addEventListener('click', function () {
    theme = themes[(themes.indexOf(theme) + 1) % themes.length];
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('site:theme', theme);
    tbtn.textContent = theme === 'mocha' ? '☾' : '☀';
  });
})();
