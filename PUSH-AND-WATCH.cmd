@echo off
setlocal
cd /d "%~dp0"
for /f "delims=" %%B in ('git branch --show-current') do set "CURRENT_BRANCH=%%B"
if not defined CURRENT_BRANCH (echo ERROR: Detached HEAD oder kein Git-Repository. & exit /b 1)
if /I "%CURRENT_BRANCH%"=="main" (echo ERROR: Kein automatischer Push von main. & exit /b 1)
for /f "delims=" %%R in ('git status --porcelain') do set "DIRTY=1"
if defined DIRTY (echo ERROR: Bitte lokale Aenderungen zuerst committen. & exit /b 1)
git remote get-url origin 2>nul | findstr /I /C:"Luporion/XaerosMaps-MultiplayerPlus-ftbteams" >nul
if errorlevel 1 (echo ERROR: origin zeigt nicht auf deinen Luporion-XMMP-Fork. Kein Push. & exit /b 1)
git push -u origin "%CURRENT_BRANCH%"
if errorlevel 1 (echo ERROR: Git-Push fehlgeschlagen. & exit /b 1)
call "%~dp0WATCH.cmd"
exit /b %ERRORLEVEL%
