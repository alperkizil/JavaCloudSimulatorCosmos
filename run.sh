#!/bin/bash
# Run the application - no Maven required

LIB_DIR="lib"
OUT_DIR="target/classes"
MAIN_CLASS="com.cloudsimulator.gui.Launcher"

java --module-path $LIB_DIR --add-modules javafx.controls,javafx.fxml -cp "$OUT_DIR" $MAIN_CLASS
