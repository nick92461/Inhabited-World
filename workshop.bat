@echo off
rem ==========================================================================
rem  Inhabited World - open a town's WORKSHOP WORLD (dev only)
rem
rem    workshop.bat               the D1 Harbour workshop
rem    workshop.bat d1_harbour    the same, by name
rem
rem  First run: builds the mod, starts the game and creates the flat world
rem  "workshop-<town>" with the town pasted in (creative mode, sea to the
rem  east, a sign on every marker). Later runs open that same world again;
rem  it is never deleted or recreated, because after the first export it is
rem  the town's source of truth.
rem
rem  When you are done editing: Save and Quit to Title, then run
rem  export-town.bat to write your changes into the mod.
rem  Steps: tools\buildimport\README.md, "Editing a town".
rem ==========================================================================
setlocal
title Inhabited World - town workshop
set "TOWN=d1_harbour"
if not "%~1"=="" set "TOWN=%~1"

call :use_java25 "%JAVA_HOME%"
if not defined JDK25 for /d %%D in ("%USERPROFILE%\.jdks\*25*") do call :use_java25 "%%~D"
if not defined JDK25 for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do call :use_java25 "%%~D"
if not defined JDK25 (
    echo Java 25 was not found. Install a Java 25 JDK, for example in
    echo %USERPROFILE%\.jdks\, or set JAVA_HOME to one.
    goto :failed
)
set "JAVA_HOME=%JDK25%"

cd /d "%~dp0inhabited_world_mod" || goto :failed
echo Opening the workshop for %TOWN%. The first run builds the mod and can take a few minutes...
echo.
call "%~dp0inhabited_world_mod\gradlew.bat" runWorkshop -Ptown=%TOWN%
if errorlevel 1 goto :failed
exit /b 0

:failed
echo.
echo ==========================================================================
echo  The workshop did not start. The details are above. Press any key to close.
echo ==========================================================================
pause >nul
exit /b 1

:use_java25
if defined JDK25 exit /b 0
if "%~1"=="" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
if not exist "%~1\release" exit /b 0
findstr /r /c:"^JAVA_VERSION=\"25[.\"]" "%~1\release" >nul 2>&1 && set "JDK25=%~1"
exit /b 0
