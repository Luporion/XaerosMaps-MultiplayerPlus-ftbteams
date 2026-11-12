@echo off
setlocal
cd /d "%~dp0"
where python >nul 2>nul
if errorlevel 1 (echo ERROR: Python is required & exit /b 1)
python scripts\verify-provenance.py
if errorlevel 1 exit /b 1
python scripts\verify-v040-settings.py
if errorlevel 1 exit /b 1
python scripts\verify-v041-warning.py
if errorlevel 1 exit /b 1
python scripts\verify-v042-sync.py
if errorlevel 1 exit /b 1
where kotlinc >nul 2>nul
if not errorlevel 1 (
  echo Kotlin policy checks are handled by GitHub Actions Gradle build; run scripts/verify-sharing.sh on Linux for standalone CLI tests.
)
python scripts\verify-test-discovery.py
if errorlevel 1 exit /b 1
python scripts\test-build-monitor.py
if errorlevel 1 exit /b 1
echo v0.4.3 sync retry, ACL, diagnostics and PvP warning source checks OK. NeoForge build and multiplayer test remain required.
