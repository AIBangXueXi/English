@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   English APK Build and Upload Script
echo ============================================
echo.

REM 1. Bump versionCode (+1) and middle digit of versionName (+1) in app/build.gradle.kts
echo [1/5] Updating version in app\build.gradle.kts ...

powershell -NoProfile -Command ^
  "$f = 'app\build.gradle.kts'; " ^
  "$c = Get-Content -Raw $f; " ^
  "$c = [regex]::Replace($c, 'versionCode\s*=\s*\d+', { param($m) 'versionCode = ' + (([int]($m.Value -replace '\D','')) + 1) }); " ^
  "$c = [regex]::Replace($c, 'versionName\s*=\s*\x22(\d+)\.(\d+)\.(\d+)\x22', { param($m) 'versionName = ' + [char]0x22 + $m.Groups[1].Value + '.' + ([int]$m.Groups[2].Value + 1) + '.' + $m.Groups[3].Value + [char]0x22 }); " ^
  "Set-Content -Path $f -Value $c -Encoding UTF8 -NoNewline"
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to update version in app\build.gradle.kts
    exit /b 1
)

REM 2. Extract versionName from app/build.gradle.kts
echo [2/5] Reading version from app\build.gradle.kts ...

set TEMP_FILE=%TEMP%\ver_%RANDOM%.txt
powershell -NoProfile -Command "(Select-String -Path 'app\build.gradle.kts' -Pattern 'versionName\s*=\s*\x22([^\x22]+)\x22').Matches[0].Groups[1].Value | Out-File -FilePath '%TEMP_FILE%' -Encoding ASCII -NoNewline"
set /p VERSION=<"%TEMP_FILE%"
del "%TEMP_FILE%"

if "%VERSION%"=="" (
    echo ERROR: Could not extract versionName from app\build.gradle.kts
    exit /b 1
)
echo        Version: %VERSION%

REM 3. Build APK
echo.
echo [3/5] Building APK ...
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    exit /b 1
)
echo        Build successful

REM 4. Copy and rename APK
echo.
echo [4/5] Copying APK ...

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

REM 5. Upload to server
echo.
echo [5/5] Uploading to aibangxuexi server ...
scp "%DST_APK%" aibangxuexi:/var/www/aibangxuexi-web/apk/english/
if %ERRORLEVEL% neq 0 (
    echo ERROR: Upload failed
    exit /b 1
)

REM 6. Commit version bump
echo.
echo [6/5] Committing version bump ...
git add .
if %ERRORLEVEL% neq 0 (
    echo ERROR: git add failed
    exit /b 1
)
git commit -m "bump version to %VERSION%"
if %ERRORLEVEL% neq 0 (
    echo ERROR: git commit failed
    exit /b 1
)

echo.
echo ============================================
echo   Done! %APK_NAME% uploaded successfully
echo   Download: https://www.aibangxuexi.com/apk/english/%APK_NAME%
echo ============================================
