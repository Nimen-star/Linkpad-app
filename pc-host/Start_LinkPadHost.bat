@echo off
setlocal
cd /d "%~dp0"

if exist "%~dp0LinkPadHost.exe" (
    powershell -NoProfile -Command "if ((Get-Item '%~dp0LinkPadHost.cs').LastWriteTimeUtc -le (Get-Item '%~dp0LinkPadHost.exe').LastWriteTimeUtc) { exit 0 } else { exit 1 }" >nul 2>&1
    if %errorlevel%==0 (
        start "" "%~dp0LinkPadHost.exe"
        exit /b 0
    )
    taskkill /IM LinkPadHost.exe /F >nul 2>&1
)

set "CSC="
if exist "%SystemRoot%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" set "CSC=%SystemRoot%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not defined CSC if exist "%SystemRoot%\Microsoft.NET\Framework\v4.0.30319\csc.exe" set "CSC=%SystemRoot%\Microsoft.NET\Framework\v4.0.30319\csc.exe"

if not defined CSC (
    echo Microsoft .NET Framework 4.x compiler not found.
    pause
    exit /b 1
)

set "MANIFEST_OPT="
if exist "%~dp0app.manifest" set "MANIFEST_OPT=/win32manifest:\"%~dp0app.manifest\""

echo Building LinkPadHost.exe (one-time setup)...
"%CSC%" /nologo /target:winexe /optimize+ %MANIFEST_OPT% /out:"%~dp0LinkPadHost.exe" ^
    /r:System.dll ^
    /r:System.Drawing.dll ^
    /r:System.Windows.Forms.dll ^
    /r:System.IO.Compression.dll ^
    /r:System.IO.Compression.FileSystem.dll ^
    "%~dp0LinkPadHost.cs"

if not %errorlevel%==0 (
    echo Build failed.
    pause
    exit /b 1
)

start "" "%~dp0LinkPadHost.exe"
exit /b 0
