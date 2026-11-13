@echo off
rem ==========================================================================
rem  Inhabited World - export a town into the mod (dev only)
rem
rem    export-town.bat               D1 Harbour
rem    export-town.bat d1_harbour    the same, by name
rem
rem  Reads the town's workshop world (or, before it exists, the original
rem  map) and rewrites the town's structure templates and manifest in
rem  inhabited_world_mod\src\main\resources. Close the world in the game
rem  first (Save and Quit to Title). Needs Python 3.11+ with numpy, scipy
rem  and Pillow. Steps: tools\buildimport\README.md, "Editing a town".
rem ==========================================================================
setlocal
title Inhabited World - export town
set "TOWN=d1_harbour"
if not "%~1"=="" set "TOWN=%~1"
cd /d "%~dp0tools\buildimport" || goto :failed
python -m buildimport export %TOWN% %2 %3 %4 %5 %6
if errorlevel 1 goto :failed
echo.
echo Done. To see it: open any test world and use /inhabited town paste %TOWN% ^<x y z^>,
echo or commit the changed files so the next build carries them.
pause
exit /b 0

:failed
echo.
echo ==========================================================================
echo  The export stopped with an error; the details are above. Press any key.
echo ==========================================================================
pause >nul
exit /b 1
