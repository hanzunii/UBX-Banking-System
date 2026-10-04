@echo off
rem Build and run the Admin Desktop app (Windows).
rem Usage: run-desktop.bat [server-url]
rem
rem The server URL defaults to http://localhost:8080 (the API Gateway).
rem Pass a URL to override, e.g.:  run-desktop.bat http://192.168.1.108:8080

setlocal
cd /d "%~dp0"

set SERVER_URL=%1
if "%SERVER_URL%"=="" set SERVER_URL=http://localhost:8080

rem Find java (prefer JAVA_HOME, fall back to PATH)
set JAVA_EXE=java.exe
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -version >NUL 2>&1
if errorlevel 1 (
    echo ERROR: Java 17+ was not found. Install Java or set JAVA_HOME.
    exit /b 1
)

echo Building Admin Desktop...
call gradle build -x test >NUL
if errorlevel 1 (
    rem If gradle isn't on the PATH, fall back to the gradle wrapper
    call gradlew build -x test >NUL
)
if errorlevel 1 (
    echo ERROR: Build failed. Make sure gradle is installed and Java 17 is on the PATH/JAVA_HOME.
    exit /b 1
)

echo Starting Admin Desktop -^> %SERVER_URL%
"%JAVA_EXE%" -Datm.server="%SERVER_URL%" -jar build\libs\admin-desktop-1.0.0.jar
endlocal