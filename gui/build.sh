#!/usr/bin/env bash
# 编译并打包 JavaFX GUI
# 依赖：JDK 17+ 与 gui/.javafx/lib 下的 JavaFX jar（先跑 ./fetch-javafx.sh）
set -euo pipefail
cd "$(dirname "$0")"

# 找 JDK：优先 JAVA_HOME，其次本机常见的 JDK 21 路径，最后 PATH 上的 javac
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/lib/jvm/java-21-openjdk/bin/javac ]; then
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JC="$JAVA_HOME/bin/javac"
    JP="$JAVA_HOME/bin/jar"
else
    JC=javac
    JP=jar
fi

version_line=$("$JC" -version 2>&1 | head -1)
java_major=$(printf '%s' "$version_line" | sed -n -e 's/^javac \([0-9][0-9]*\).*/\1/p' -e 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)
if [ "${java_major:-0}" -lt 17 ]; then
    echo "需要 JDK 17+，当前：$version_line" >&2
    echo "提示：export JAVA_HOME=/usr/lib/jvm/java-21-openjdk" >&2
    exit 1
fi

JAVAFX_LIB="${JAVAFX_LIB:-$PWD/.javafx/lib}"
if [ ! -d "$JAVAFX_LIB" ]; then
    echo "缺少 JavaFX：先运行 ./fetch-javafx.sh" >&2
    exit 1
fi

rm -rf out dist
mkdir -p out dist

"$JC" -encoding UTF-8 --release 17 \
    --module-path "$JAVAFX_LIB" --add-modules javafx.controls \
    -d out src/mcmotd/*.java
cp src/mcmotd/app.css out/app.css
"$JP" --create --file dist/mc-motd-gui.jar --main-class mcmotd.Main -C out .

echo "已生成 gui/dist/mc-motd-gui.jar"

# --fat：把 JavaFX 的类和原生库一起打进来，双击 jar 就能开（不用 --module-path）。
# 只对本机平台有效，所以不作为发布资产。
if [ "${1:-}" = "--fat" ]; then
    fat_dir=$(mktemp -d)
    trap 'rm -rf "$fat_dir"' EXIT

    for lib in "$JAVAFX_LIB"/javafx-*.jar; do
        case "$lib" in
            *-linux.jar|*-mac.jar|*-win.jar|*-linux-aarch64.jar|*-mac-aarch64.jar|*-win-x86.jar) ;;
            *) continue ;;
        esac
        unzip -qo "$lib" -d "$fat_dir"
    done
    unzip -qo dist/mc-motd-gui.jar -d "$fat_dir"

    # 合并后的 jar 不能带 module-info，否则 jar 工具会当成模块描述符解析
    find "$fat_dir" -name module-info.class -delete
    rm -f "$fat_dir/META-INF/MANIFEST.MF"

    cat > "$fat_dir/manifest.txt" <<'MANIFEST'
Main-Class: mcmotd.Main
Enable-Native-Access: ALL-UNNAMED
MANIFEST

    "$JP" --create --file dist/mc-motd-gui-fat.jar --manifest "$fat_dir/manifest.txt" -C "$fat_dir" .
    echo "已生成 gui/dist/mc-motd-gui-fat.jar（自带 JavaFX，可双击运行）"
fi
