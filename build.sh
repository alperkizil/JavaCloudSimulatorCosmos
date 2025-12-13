#!/bin/bash
# Simple build script - no Maven required
# Just uses javac with the JARs in lib folder

LIB_DIR="lib"
SRC_DIR="src/main/java"
OUT_DIR="target/classes"

# Build classpath from all JARs in lib folder
CLASSPATH=""
for jar in $LIB_DIR/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done
CLASSPATH="${CLASSPATH:1}"  # Remove leading colon

# Create output directory
mkdir -p $OUT_DIR

echo "Compiling Java sources..."
find $SRC_DIR -name "*.java" > sources.txt
javac -d $OUT_DIR -cp "$CLASSPATH" --module-path $LIB_DIR --add-modules javafx.controls,javafx.fxml @sources.txt
RESULT=$?
rm sources.txt

if [ $RESULT -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo ""
echo "Build successful! Output in $OUT_DIR"
echo ""
echo "To run the application:"
echo "  ./run.sh"
