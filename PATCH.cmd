@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (echo Usage: PATCH.cmd "path-to-update.patch" [--no-push] & exit /b 2)
for /f "delims=" %%B in ('git branch --show-current') do set "CURRENT_BRANCH=%%B"
if not defined CURRENT_BRANCH (echo ERROR: Detached HEAD oder kein Git-Repository. & exit /b 1)
if /I "%CURRENT_BRANCH%"=="main" (echo ERROR: Bitte zuerst neuen Feature-Branch erstellen. & exit /b 1)
for /f "delims=" %%R in ('git status --porcelain') do set "DIRTY=1"
if defined DIRTY (echo ERROR: Uncommitted changes. Erst pruefen und committen. & exit /b 1)
git apply --check "%~1"
if errorlevel 1 (echo ERROR: Patch passt nicht zum Ausgangsstand. & exit /b 1)
git am --no-3way "%~1"
if errorlevel 1 (echo ERROR: Patch fehlgeschlagen. Status pruefen, ggf. git am --abort. & exit /b 1)
call "%~dp0VERIFY.cmd"
if errorlevel 1 (echo ERROR: Verifikation fehlgeschlagen, nicht pushen. & exit /b 1)
if /I "%~2"=="--no-push" (
  echo Patch angewendet und geprueft. Push wurde mit --no-push uebersprungen.
  exit /b 0
)
call "%~dp0PUSH-AND-WATCH.cmd"
exit /b %ERRORLEVEL%
