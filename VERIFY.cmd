@echo off
setlocal
cd /d "%~dp0"
where python >nul 2>nul
if errorlevel 1 (echo ERROR: Python is required & exit /b 1)
python scripts\verify-scoped-sharing.py
if errorlevel 1 exit /b 1
where kotlinc >nul 2>nul
if not errorlevel 1 (
  echo Kotlin policy checks are handled by GitHub Actions Gradle build; run scripts/verify-sharing.sh on Linux for standalone CLI tests.
)
python scripts\test-build-monitor.py
if errorlevel 1 exit /b 1
echo v0.2.0 scoped sharing source checks OK. NeoForge build and multiplayer test remain required.
