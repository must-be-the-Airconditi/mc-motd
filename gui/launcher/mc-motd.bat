@echo off
rem mc-motd GUI 启动脚本（Windows，需要 Java 17+，JavaFX 库放同目录 javafx\ 下）
cd /d "%~dp0"

set "MAJOR="
for /f "tokens=2 delims= " %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JV=%%v"
if defined JV (
  set "JV=%JV:"=%"
  for /f "delims=." %%a in ("%JV%") do set "MAJOR=%%a"
)
if "%MAJOR%"=="1" (
  echo mc-motd 需要 Java 17 或更高版本，请安装 JDK 21 后重试。 1>&2
  exit /b 1
)
if defined MAJOR if %MAJOR% LSS 17 (
  echo mc-motd 需要 Java 17 或更高版本，请安装 JDK 21 后重试。 1>&2
  exit /b 1
)

if exist "javafx\" (
  java --module-path "javafx" --add-modules javafx.controls -jar mc-motd-gui.jar %*
) else (
  java -jar mc-motd-gui.jar %*
)
