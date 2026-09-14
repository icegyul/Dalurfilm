@echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.\
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
if defined JAVA_HOME goto findJava
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
:findJava
if exist "D:\tools\gradle-8.7\bin\gradle.bat" (
  call "D:\tools\gradle-8.7\bin\gradle.bat" %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  call gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle 8.7 not found. Install from https://services.gradle.org/distributions/gradle-8.7-bin.zip 1>&2
exit /b 1
