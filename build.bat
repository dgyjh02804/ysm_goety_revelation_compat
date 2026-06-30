@echo off
REM YSM GoetyRevelation Compat - Build Script
REM Requires: JDK 17 and internet connection (for first build only)

echo ============================================
echo YSM GoetyRevelation Compat Mod Build Script
echo ============================================
echo.

REM Try to find JDK 17
set JAVA_HOME=
if exist "C:\Users\%USERNAME%\AppData\Roaming\RIP\JavaRuntime\jre-v64-220420\jdk17" (
    set "JAVA_HOME=C:\Users\%USERNAME%\AppData\Roaming\RIP\JavaRuntime\jre-v64-220420\jdk17"
    echo Using Minecraft bundled JDK 17: %JAVA_HOME%
) else (
    echo WARNING: JDK 17 not found at default Minecraft launcher location.
    echo Please install JDK 17 or set JAVA_HOME manually.
    echo.
)

if not defined JAVA_HOME (
    echo Checking for JDK 17 in PATH...
    for /f "tokens=*" %%i in ('java -version 2^>^&1 ^| findstr /i "17"') do (
        set "JAVA_HOME="
        echo Found JDK 17 in PATH
    )
)

echo.
echo Building mod...
echo.

REM Run Gradle build
call gradlew.bat build

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo BUILD FAILED! Check errors above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo BUILD SUCCESS!
echo Output: build\libs\ysm_goety_revelation_compat-1.0.0.jar
echo ============================================
echo.
echo To use this mod:
echo 1. Place ysm_goety_revelation_compat-1.0.0.jar in your Minecraft mods folder
echo 2. Ensure these mods are also in mods folder:
echo    - Goety (required by GoetyRevelation)
echo    - GoetyRevelation-2.3.1.jar
echo    - ysm-2.6.5-forge+mc1.20.1-release.jar
echo 3. Launch Minecraft 1.20.1 Forge
echo.
pause
