@echo off
REM ===========================================================
REM  LoginMod - 服务器端登录/注册模组构建脚本 (Forge 1.20.1)
REM  依赖：Java 17 (已配置为 I:\我的世界\java环境\Java1)
REM       Gradle 8.5  (已在 tools\gradle\gradle-8.5 中)
REM  用法：双击运行 或 在命令行中执行 build.bat
REM ===========================================================

setlocal

set "JAVA_HOME=I:\我的世界\java环境\Java1"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ============================================
echo  LoginMod 构建工具  (Minecraft Forge 1.20.1)
echo  JAVA_HOME: %JAVA_HOME%
echo ============================================
echo.

set "GRADLE_BIN=C:\Users\%USERNAME%\.trae-cn\tools\gradle\gradle-8.5\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
    echo [错误] 找不到 Gradle 8.5: %GRADLE_BIN%
    echo 请确认已从 https://services.gradle.org/distributions/gradle-8.5-bin.zip 下载并解压到上述目录
    pause
    exit /b 1
)

if "%~1"=="" (
    echo [提示] 未指定目标，默认执行: clean build
    echo.
    "%GRADLE_BIN%" clean build --no-daemon --console=plain
) else (
    echo [提示] 执行目标: %*
    echo.
    "%GRADLE_BIN%" %*
)

set "RC=%ERRORLEVEL%"
if %RC%==0 (
    echo.
    echo ============================================
    echo  构建成功！模组位于: build\libs\loginmod-1.0.0.jar
    echo  请将该 JAR 文件放入 Forge 服务端的 mods 目录
    echo ============================================
) else (
    echo.
    echo [错误] 构建失败 (退出码 %RC%)，详见上方日志或 build_output.log
)

endlocal
pause
