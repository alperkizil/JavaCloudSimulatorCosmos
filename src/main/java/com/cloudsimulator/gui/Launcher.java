package com.cloudsimulator.gui;

/**
 * Launcher class for the ConfigGeneratorApp.
 *
 * This class exists to work around a JavaFX limitation where the main class
 * cannot directly extend Application when JavaFX is not on the module path.
 * IDEs like NetBeans may use exec:java which doesn't set up modules correctly.
 *
 * Use this class as the main entry point in IDE run configurations.
 */
public class Launcher {

    public static void main(String[] args) {
        ConfigGeneratorApp.main(args);
    }
}
