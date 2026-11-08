#!/usr/bin/env bash
# 编译并打包 GUI（只依赖 JDK 11+，不需要下载任何东西）
set -euo pipefail
cd "$(dirname "$0")"

JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

rm -rf out dist
mkdir -p out dist

"$JAVAC" -encoding UTF-8 --release 11 -d out src/mcmotd/*.java
"$JAR" --create --file dist/mc-motd-gui.jar --main-class mcmotd.Main -C out .

echo "已生成 gui/dist/mc-motd-gui.jar"
