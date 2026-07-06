@echo off
REM Doppio click qui per compilare l'APK di Baby Cry Translator.
REM Lancia lo script PowerShell aggirando le restrizioni di esecuzione.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_apk.ps1"
pause
