@echo off
rem ==========================================================================
rem  Inhabited World - one-click test launch (canon 10 section 60, Testing Workflow)
rem
rem  Builds the checkout this file lives in and starts the development client
rem  (player "Dev") straight into a fresh test world: fixed seed, creative mode,
rem  cheats on.
rem
rem    test-launch.bat           Inhabited World test world ("Inhabited Test")
rem    test-launch.bat default   same seed as a Default world ("Default Test"),
rem                              for comparisons
rem
rem  Each launch deletes and recreates the test world of that kind. Worlds you
rem  create yourself in the menu are never touched.
rem ==========================================================================
setlocal
title Inhabited World - test launch

set "WORLD=rpg"
if /i "%~1"=="default" set "WORLD=default"
if not "%~1"=="" if /i not "%~1"=="default" (
    echo Unknown option "%~1". Run it with no option, or with "default".
    goto :failed
)

rem --- Gradle and the game need Java 25 -------------------------------------
call :use_java25 "%JAVA_HOME%"
if not defined JDK25 for /d %%D in ("%USERPROFILE%\.jdks\*25*") do call :use_java25 "%%~D"
if not defined JDK25 for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do call :use_java25 "%%~D"
if not defined JDK25 (
    echo Java 25 was not found. Install a Java 25 JDK, for example in
    echo %USERPROFILE%\.jdks\, or set JAVA_HOME to one.
    goto :failed
)
set "JAVA_HOME=%JDK25%"
echo Using Java: %JAVA_HOME%

rem --- Build and run ---------------------------------------------------------
cd /d "%~dp0inhabited_world_mod" || goto :failed
echo Building and starting the game. The first run can take a few minutes...
echo.
rem Full path: some Windows setups don't run commands from the current folder.
call "%~dp0inhabited_world_mod\gradlew.bat" runTestClient -PtestWorld=%WORLD%
if errorlevel 1 goto :failed
exit /b 0

:failed
echo.
echo ==========================================================================
echo  Test launch failed. The build or the game stopped with an error; the
echo  details are above. Press any key to close this window.
echo ==========================================================================
pause >nul
exit /b 1

rem Sets JDK25 to the folder given if it holds a Java 25 JDK.
:use_java25
if defined JDK25 exit /b 0
if "%~1"=="" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
if not exist "%~1\release" exit /b 0
findstr /r /c:"^JAVA_VERSION=\"25[.\"]" "%~1\release" >nul 2>&1 && set "JDK25=%~1"
exit /b 0
