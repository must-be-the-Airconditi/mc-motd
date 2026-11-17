#!/usr/bin/env bash
# 把桌面版装进 XDG 应用菜单（fuzzel / rofi / GNOME 概览都能搜到）
#
#   bash gui/tools/install-desktop.sh            # 安装
#   bash gui/tools/install-desktop.sh --uninstall # 卸载
#
# 依赖：gui/dist/mc-motd-gui-fat.jar（自带 JavaFX，双击即开）和 Java 17+。
# 没有 fat jar 时会先跑一次 gui/build.sh --fat。
set -euo pipefail
cd "$(dirname "$0")"
GUI_DIR=$(cd .. && pwd)
REPO_DIR=$(cd "$GUI_DIR/.." && pwd)

APP_ID=mc-motd
APP_NAME="MOTD 查看器"
FAT_JAR="$GUI_DIR/dist/mc-motd-gui-fat.jar"
ICON_SRC="$GUI_DIR/icon.png"
DESKTOP_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICON_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/256x256/apps"
DESKTOP_FILE="$DESKTOP_DIR/$APP_ID.desktop"
LAUNCH_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/mc-motd"
LAUNCHER="$LAUNCH_DIR/launch.sh"

uninstall() {
    rm -f "$DESKTOP_FILE" "$ICON_DIR/$APP_ID.png" "$LAUNCHER"
    command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DESKTOP_DIR" || true
    echo "已卸载：$DESKTOP_FILE"
}

if [ "${1:-}" = "--uninstall" ]; then
    uninstall
    exit 0
fi

# 找一个 ≥17 的 java（默认 PATH 上可能还是 11）
java_major() {
    "$1" -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p'
}
JAVA=""
for candidate in "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
                 /usr/lib/jvm/java-21-openjdk/bin/java \
                 /usr/lib/jvm/java-17-openjdk/bin/java \
                 /usr/lib/jvm/java-25-openjdk/bin/java \
                 "$(command -v java || true)"; do
    [ -n "$candidate" ] && [ -x "$candidate" ] || continue
    if [ "$(java_major "$candidate")" -ge 17 ] 2>/dev/null; then
        JAVA="$candidate"
        break
    fi
done
if [ -z "$JAVA" ]; then
    echo "找不到 Java 17+。装一个（例如 sudo pacman -S jdk21-openjdk）再试。" >&2
    exit 1
fi

if [ ! -f "$FAT_JAR" ]; then
    echo "还没有 $FAT_JAR，先构建…"
    bash "$GUI_DIR/build.sh" --fat
fi

mkdir -p "$DESKTOP_DIR" "$ICON_DIR" "$LAUNCH_DIR"
cp "$ICON_SRC" "$ICON_DIR/$APP_ID.png"

# 启动器：fat jar 被普通 build.sh 删掉时自动重建，而不是点了没反应
cat > "$LAUNCHER" <<LAUNCH
#!/usr/bin/env bash
# 由 gui/tools/install-desktop.sh 生成
set -euo pipefail
JAVA="$JAVA"
JAR="$FAT_JAR"
REPO="$REPO_DIR"
if [ ! -f "\$JAR" ] && [ -x "\$REPO/gui/build.sh" ]; then
    (cd "\$REPO" && bash gui/build.sh --fat) || true
fi
if [ ! -f "\$JAR" ]; then
    command -v notify-send >/dev/null 2>&1 && notify-send "mc-motd" "找不到 \$JAR，先跑 bash gui/build.sh --fat"
    echo "mc-motd: missing \$JAR" >&2
    exit 1
fi
exec "\$JAVA" -jar "\$JAR" "\$@"
LAUNCH
chmod +x "$LAUNCHER"

cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Name=$APP_ID
Name[zh_CN]=$APP_NAME
GenericName=Minecraft Server MOTD Viewer
GenericName[zh_CN]=Minecraft 服务器 MOTD 查看器
Comment=Read Java and Bedrock server MOTDs
Comment[zh_CN]=查看 Minecraft Java / 基岩版服务器 MOTD
Exec="$LAUNCHER" %U
Icon=$APP_ID
Terminal=false
Categories=Utility;
Keywords=minecraft;motd;server;ping;
StartupNotify=true
EOF

chmod +x "$DESKTOP_FILE"
command -v desktop-file-validate >/dev/null 2>&1 && desktop-file-validate "$DESKTOP_FILE" || true
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DESKTOP_DIR" || true

echo "已安装：$DESKTOP_FILE"
echo "  Java：$JAVA"
echo "  Jar ：$FAT_JAR"
echo "  启动器：$LAUNCHER"
echo "在 fuzzel / rofi 里搜 “$APP_ID” 或 “MOTD” 即可启动。"
