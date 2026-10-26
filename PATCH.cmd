@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (echo Usage: PATCH.cmd "path-to-update.patch" & exit /b 2)
git diff --quiet
if errorlevel 1 (echo ERROR: Uncommitted changes & exit /b 1)
git diff --cached --quiet
if errorlevel 1 (echo ERROR: Staged changes & exit /b 1)
git am "%~1"
if errorlevel 1 (echo ERROR: Patch failed. Inspect git status; to abort use git am --abort & exit /b 1)
call VERIFY.cmd
if errorlevel 1 exit /b 1
echo Patch applied and verification OK. Push manually after review.
