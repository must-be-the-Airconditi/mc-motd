@echo off
rem mc-motd GUI 启动脚本（Windows，需要 Java 11+）
cd /d "%~dp0"
java -jar mc-motd-gui.jar %*
