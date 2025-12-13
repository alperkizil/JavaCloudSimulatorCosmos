#!/bin/bash
# One-time script to install JavaFX JARs from lib/ to local Maven repository
# Run this once: ./install-javafx-local.sh

JAVAFX_VERSION="21.0.9"
LIB_DIR="$(dirname "$0")/lib"

echo "Installing JavaFX $JAVAFX_VERSION JARs to local Maven repository..."

mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-base -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.base.jar"
mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-graphics -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.graphics.jar"
mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-controls -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.controls.jar"
mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-fxml -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.fxml.jar"
mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-web -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.web.jar"
mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-media -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.media.jar"
mvn install:install-file -DgroupId=org.openjfx -DartifactId=javafx-swing -Dversion=$JAVAFX_VERSION -Dpackaging=jar -Dfile="$LIB_DIR/javafx.swing.jar"

echo "Done! JavaFX JARs installed to ~/.m2/repository/org/openjfx/"
