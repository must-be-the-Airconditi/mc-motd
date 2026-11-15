@echo off
rem mc-motd GUI 启动脚本（Windows，需要 Java 17+，JavaFX 库放同目录 javafx\ 下）
cd /d "%~dp0"
if exist "javafx\" (
    java --module-path "javafx" --add-modules javafx.controls -jar mc-motd-gui.jar %*
) else (
    java -jar mc-motd-gui.jar %*
)
