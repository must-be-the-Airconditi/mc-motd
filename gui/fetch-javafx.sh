#!/usr/bin/env bash
# 下载当前平台的 JavaFX jar（Maven 分类器版本，内含 classes + 原生库）
# 用法：./fetch-javafx.sh            默认 21.0.7，阿里云镜像
#       JAVAFX_VERSION=21.0.7 JAVAFX_MIRROR=https://repo1.maven.org/maven2/org/openjfx ./fetch-javafx.sh
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${JAVAFX_VERSION:-21.0.7}"
MIRROR="${JAVAFX_MIRROR:-https://maven.aliyun.com/repository/public/org/openjfx}"

os=$(uname -s)
arch=$(uname -m)
case "$os" in
    Linux*)  [ "$arch" = "aarch64" ] && classifier=linux-aarch64 || classifier=linux ;;
    Darwin*) [ "$arch" = "arm64" ] && classifier=mac-aarch64 || classifier=mac ;;
    MINGW*|MSYS*|CYGWIN*) classifier=win ;;
    *) echo "不支持的平台：$os $arch" >&2; exit 1 ;;
esac

mkdir -p .javafx/lib
for module in base graphics controls; do
    file="javafx-$module-$VERSION-$classifier.jar"
    echo "下载 $file"
    curl -fsSL --retry 3 -o ".javafx/lib/$file" "$MIRROR/javafx-$module/$VERSION/$file" \
        || { echo "下载失败：$file" >&2; exit 1; }
done

echo "JavaFX $VERSION ($classifier) -> gui/.javafx/lib"
