@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   English APK Build and Upload Script
echo ============================================
echo.

REM 1. Extract versionName from app/build.gradle.kts
echo [1/4] Reading version from app\build.gradle.kts ...

for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "(Select-String -Path 'app\build.gradle.kts' -Pattern 'versionName\s*=\s*""([^""]+)""').Matches.Groups[1].Value"`) do set VERSION=%%i

if "%VERSION%"=="" (
    echo ERROR: Could not extract versionName from app\build.gradle.kts
    exit /b 1
)
echo        Version: %VERSION%

REM 2. Build APK
echo.
echo [2/4] Building APK ...
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    exit /b 1
)
echo        Build successful

REM 3. Copy and rename APK
echo.
echo [3/4] Copying APK ...

set APK_NAME=app.english.v%VERSION%.apk
set SRC_APK=app\build\outputs\apk\debug\app-debug.apk
set OUT_DIR=output
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
set DST_APK=%OUT_DIR%\%APK_NAME%

copy /y "%SRC_APK%" "%DST_APK%"
if %ERRORLEVEL% neq 0 (
    echo ERROR: Copy failed
    exit /b 1
)
echo        %DST_APK%

REM 4. Upload to server
echo.
echo [4/4] Uploading to aibangxuexi server ...
scp "%DST_APK%" aibangxuexi:/var/www/aibangxuexi-web/apk/english/
if %ERRORLEVEL% neq 0 (
    echo ERROR: Upload failed
    exit /b 1
)

echo.
echo ============================================
echo   Done! %APK_NAME% uploaded successfully
echo   Download: https://www.aibangxuexi.com/apk/english/%APK_NAME%
echo ============================================
