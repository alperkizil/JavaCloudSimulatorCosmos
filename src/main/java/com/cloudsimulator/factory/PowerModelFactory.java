package com.cloudsimulator.factory;

import com.cloudsimulator.model.PowerModel;

/**
 * Factory for creating PowerModel instances by name.
 * Supports different predefined power models for various hardware types.
 */
public class PowerModelFactory {

    /**
     * Creates a PowerModel instance by name.
     *
     * @param modelName Name of the power model
     * @return PowerModel instance
     */
    public static PowerModel createPowerModel(String modelName) {
        switch (modelName) {
            case "StandardPowerModel":
                return new PowerModel("StandardPowerModel", 300.0, 250.0, 50.0, 30.0, 100.0);

            case "HighPerformancePowerModel":
                return new PowerModel("HighPerformancePowerModel", 500.0, 400.0, 80.0, 50.0, 150.0);

            case "LowPowerModel":
                return new PowerModel("LowPowerModel", 200.0, 150.0, 30.0, 20.0, 60.0);

            case "EfficientPowerModel":
                return new PowerModel("EfficientPowerModel", 250.0, 200.0, 35.0, 25.0, 70.0);

            case "ServerPowerModel":
                return new PowerModel("ServerPowerModel", 400.0, 350.0, 70.0, 40.0, 120.0);

            default:
                // If unknown model name, return standard model
                System.err.println("Unknown power model: " + modelName + ". Using StandardPowerModel.");
                return new PowerModel("StandardPowerModel", 300.0, 250.0, 50.0, 30.0, 100.0);
        }
    }

    /**
     * Creates a custom PowerModel with specific parameters.
     */
    public static PowerModel createCustomPowerModel(String name, double maxCpuPower, double maxGpuPower,
                                                    double idleCpuPower, double idleGpuPower,
                                                    double otherComponentsPower) {
        return new PowerModel(name, maxCpuPower, maxGpuPower, idleCpuPower, idleGpuPower, otherComponentsPower);
    }
}
