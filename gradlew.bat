@echo off
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
cd /d "%DIRNAME%android"
call gradlew.bat %*
