@echo off
REM Simple build script - no Maven required
REM Just uses javac with the JARs in lib folder

setlocal enabledelayedexpansion

set LIB_DIR=lib
set SRC_DIR=src\main\java
set OUT_DIR=target\classes
set MAIN_CLASS=com.cloudsimulator.gui.Launcher

REM Build classpath from all JARs in lib folder
set CLASSPATH=
for %%f in (%LIB_DIR%\*.jar) do (
    set CLASSPATH=!CLASSPATH!%%f;
)

REM Create output directory
if not exist %OUT_DIR% mkdir %OUT_DIR%

echo Compiling Java sources...
dir /s /b %SRC_DIR%\*.java > sources.txt
javac -d %OUT_DIR% -cp "%CLASSPATH%" --module-path %LIB_DIR% --add-modules javafx.controls,javafx.fxml @sources.txt
del sources.txt

if %errorlevel% neq 0 (
    echo Compilation failed!
    exit /b 1
)

echo.
echo Build successful! Output in %OUT_DIR%
echo.
echo To run the application:
echo   run.bat
