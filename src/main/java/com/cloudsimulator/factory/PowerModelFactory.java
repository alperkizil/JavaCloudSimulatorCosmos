package com.cloudsimulator.factory;

import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.PowerModel;

/**
 * Factory for creating PowerModel instances by name.
 * Supports different predefined power models for various hardware types.
 *
 * Available Models:
 * - StandardPowerModel: Default utilization-based model for typical servers
 * - HighPerformancePowerModel: For high-end server hardware
 * - LowPowerModel: For low-power/edge devices
 * - EfficientPowerModel: For energy-efficient servers
 * - ServerPowerModel: For typical datacenter servers
 * - MeasurementBasedPowerModel: Empirical model based on wall-plug measurements
 */
public class PowerModelFactory {

    // Singleton instance of the measurement-based model (reusable since it's stateless)
    private static MeasurementBasedPowerModel measurementBasedModel = null;

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

            case "MeasurementBasedPowerModel":
                // Return a utilization-based wrapper that uses measurement data
                // This maintains backward compatibility with the PowerModel interface
                return createMeasurementBasedWrapper(1.0);

            default:
                // Check if it's a scaled measurement-based model (e.g., "MeasurementBasedPowerModel:1.5")
                if (modelName.startsWith("MeasurementBasedPowerModel:")) {
                    try {
                        double scaleFactor = Double.parseDouble(modelName.split(":")[1]);
                        return createMeasurementBasedWrapper(scaleFactor);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid scale factor in model name: " + modelName);
                    }
                }
                // If unknown model name, return MeasurementBasedPowerModel as default
                System.err.println("Unknown power model: " + modelName + ". Using MeasurementBasedPowerModel.");
                return createMeasurementBasedWrapper(1.0);
        }
    }

    /**
     * Creates a MeasurementBasedPowerModel with default settings.
     * Uses empirical data from Dell Precision 7920 + Nvidia 5080 GPU.
     *
     * @return MeasurementBasedPowerModel instance
     */
    public static MeasurementBasedPowerModel createMeasurementBasedPowerModel() {
        return createMeasurementBasedPowerModel(1.0);
    }

    /**
     * Creates a MeasurementBasedPowerModel with a hardware scale factor.
     *
     * Scale factor guidelines:
     * - 0.5: Low-power edge device or laptop
     * - 0.75: Entry-level server
     * - 1.0: Reference system (Dell Precision 7920 + Nvidia 5080)
     * - 1.25: High-end workstation
     * - 1.5: Dual-socket server
     * - 2.0: High-density compute node
     *
     * @param hardwareScaleFactor Scale factor for different hardware (1.0 = reference)
     * @return MeasurementBasedPowerModel instance
     */
    public static MeasurementBasedPowerModel createMeasurementBasedPowerModel(double hardwareScaleFactor) {
        return new MeasurementBasedPowerModel(hardwareScaleFactor);
    }

    /**
     * Gets or creates a shared MeasurementBasedPowerModel instance.
     * The shared instance uses default scale factor (1.0).
     *
     * @return Shared MeasurementBasedPowerModel instance
     */
    public static MeasurementBasedPowerModel getSharedMeasurementBasedModel() {
        if (measurementBasedModel == null) {
            measurementBasedModel = new MeasurementBasedPowerModel();
        }
        return measurementBasedModel;
    }

    /**
     * Creates a PowerModel wrapper that uses measurement-based calculations.
     * This provides backward compatibility with code expecting PowerModel interface.
     *
     * The wrapper extracts typical power values from the measurement data
     * and creates a utilization-based PowerModel that approximates the
     * empirical measurements.
     *
     * @param scaleFactor Hardware scale factor
     * @return PowerModel configured with measurement-derived values
     */
    private static PowerModel createMeasurementBasedWrapper(double scaleFactor) {
        // Extract typical values from measurement data:
        // - Max CPU power: Based on CINEBENCH (highest CPU workload) = 133.76W incremental
        // - Max GPU power: Based on FURMARK (highest GPU workload) = 352.18W incremental
        // - Idle CPU power: Part of system idle (75.79W total idle)
        // - Idle GPU power: Part of system idle
        // - Other components: Part of system idle

        // Distribute the 75.79W idle across components (estimated breakdown)
        double idleCpu = 25.0 * scaleFactor;   // ~33% of idle to CPU
        double idleGpu = 15.0 * scaleFactor;   // ~20% of idle to GPU
        double other = 35.79 * scaleFactor;    // ~47% to other components

        // Max power = idle + max incremental from measurements
        double maxCpu = (25.0 + 133.76) * scaleFactor;  // ~158.76W
        double maxGpu = (15.0 + 352.18) * scaleFactor;  // ~367.18W

        return new PowerModel("MeasurementBasedPowerModel", maxCpu, maxGpu, idleCpu, idleGpu, other);
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
