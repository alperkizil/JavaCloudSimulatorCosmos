@echo off
REM Run the application - no Maven required

set LIB_DIR=lib
set OUT_DIR=target\classes
set MAIN_CLASS=com.cloudsimulator.gui.Launcher

java --module-path %LIB_DIR% --add-modules javafx.controls,javafx.fxml -cp "%OUT_DIR%" %MAIN_CLASS%
