package com.cloudsimulator.gui;

import com.cloudsimulator.config.DatacenterConfig;
import com.cloudsimulator.config.HostConfig;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel displaying a summary of the experiment configuration and controls
 * for generating configuration files.
 */
public class SummaryPanel extends VBox {

    private final ExperimentTemplate template;
    private final CosmosConfigWriter writer;

    private TextArea summaryArea;
    private TextField startSeedField;
    private TextField endSeedField;
    private TextField outputDirField;
    private Label statusLabel;

    public SummaryPanel(ExperimentTemplate template) {
        this.template = template;
        this.writer = new CosmosConfigWriter();

        setSpacing(15);
        setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("Configuration Summary & Export");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Summary display
        VBox summaryBox = createSummaryBox();

        // Seed range input
        HBox seedRangeBox = createSeedRangeBox();

        // Output directory selection
        HBox outputDirBox = createOutputDirBox();

        // Generate button
        HBox buttonBox = createButtonBox();

        // Status label
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #666;");

        getChildren().addAll(titleLabel, summaryBox, seedRangeBox, outputDirBox, buttonBox, statusLabel);
    }

    private VBox createSummaryBox() {
        VBox box = new VBox(5);

        Label label = new Label("Configuration Overview:");
        label.setStyle("-fx-font-weight: bold;");

        summaryArea = new TextArea();
        summaryArea.setEditable(false);
        summaryArea.setPrefHeight(300);
        summaryArea.setStyle("-fx-font-family: monospace;");
        VBox.setVgrow(summaryArea, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Summary");
        refreshBtn.setOnAction(e -> refreshSummary());

        box.getChildren().addAll(label, summaryArea, refreshBtn);
        return box;
    }

    private HBox createSeedRangeBox() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 5;");

        Label titleLabel = new Label("Seed Range:");
        titleLabel.setStyle("-fx-font-weight: bold;");

        startSeedField = new TextField("1");
        startSeedField.setPrefWidth(100);

        Label toLabel = new Label("to");

        endSeedField = new TextField("10");
        endSeedField.setPrefWidth(100);

        Label hintLabel = new Label("(Generates files for each seed in range)");
        hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        box.getChildren().addAll(titleLabel, startSeedField, toLabel, endSeedField, hintLabel);
        return box;
    }

    private HBox createOutputDirBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Output Directory:");

        outputDirField = new TextField(System.getProperty("user.dir") + "/configs");
        outputDirField.setPrefWidth(400);

        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Output Directory");
            chooser.setInitialDirectory(new File(System.getProperty("user.dir")));
            File dir = chooser.showDialog(getScene().getWindow());
            if (dir != null) {
                outputDirField.setText(dir.getAbsolutePath());
            }
        });

        box.getChildren().addAll(label, outputDirField, browseBtn);
        return box;
    }

    private HBox createButtonBox() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Button generateBtn = new Button("Generate Configuration Files");
        generateBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 20;");
        generateBtn.setOnAction(e -> generateConfigs());

        Button previewBtn = new Button("Preview Single File");
        previewBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        previewBtn.setOnAction(e -> previewConfig());

        box.getChildren().addAll(generateBtn, previewBtn);
        return box;
    }

    public void refreshSummary() {
        StringBuilder sb = new StringBuilder();

        sb.append("=".repeat(60)).append("\n");
        sb.append("EXPERIMENT CONFIGURATION SUMMARY\n");
        sb.append("=".repeat(60)).append("\n\n");

        // Datacenters
        sb.append("DATACENTERS (").append(template.getDatacenterConfigs().size()).append(")\n");
        sb.append("-".repeat(40)).append("\n");
        for (DatacenterConfig dc : template.getDatacenterConfigs()) {
            sb.append(String.format("  %-15s | Capacity: %3d hosts | Max Power: %.0f W\n",
                dc.getName(), dc.getMaxHostCapacity(), dc.getTotalMaxPowerDraw()));
        }
        sb.append("\n");

        // Hosts
        sb.append("HOSTS (").append(template.getHostConfigs().size()).append(")\n");
        sb.append("-".repeat(40)).append("\n");
        for (int i = 0; i < template.getHostConfigs().size(); i++) {
            HostConfig h = template.getHostConfigs().get(i);
            sb.append(String.format("  Host %d: %d cores, %d GPUs, %s, %s\n",
                i + 1, h.getNumberOfCpuCores(), h.getNumberOfGpus(),
                h.getComputeType().name(), h.getPowerModelName()));
        }
        sb.append("\n");

        // Users
        sb.append("USERS (").append(template.getUserTemplates().size()).append(")\n");
        sb.append("-".repeat(40)).append("\n");
        for (UserTemplate user : template.getUserTemplates()) {
            sb.append(String.format("  %-15s\n", user.getName()));
            sb.append(String.format("    Datacenters: %s\n",
                user.getSelectedDatacenterNames().isEmpty() ? "(none)" :
                    String.join(", ", user.getSelectedDatacenterNames())));

            // VM summary
            Map<ComputeType, Integer> vmCounts = new HashMap<>();
            for (VMTemplate vm : user.getVmTemplates()) {
                vmCounts.merge(vm.getComputeType(), 1, Integer::sum);
            }
            sb.append(String.format("    VMs: %d total (CPU: %d, GPU: %d, Mixed: %d)\n",
                user.getTotalVMCount(),
                vmCounts.getOrDefault(ComputeType.CPU_ONLY, 0),
                vmCounts.getOrDefault(ComputeType.GPU_ONLY, 0),
                vmCounts.getOrDefault(ComputeType.CPU_GPU_MIXED, 0)));

            // Task summary
            Map<WorkloadType, Integer> taskCounts = new HashMap<>();
            for (TaskTemplate task : user.getTaskTemplates()) {
                taskCounts.merge(task.getWorkloadType(), task.getCount(), Integer::sum);
            }
            sb.append(String.format("    Tasks: %d total\n", user.getTotalTaskCount()));
            for (Map.Entry<WorkloadType, Integer> entry : taskCounts.entrySet()) {
                sb.append(String.format("      - %s: %d\n", entry.getKey().name(), entry.getValue()));
            }
            sb.append("\n");
        }

        // Total summary
        sb.append("=".repeat(60)).append("\n");
        sb.append("TOTALS\n");
        sb.append("-".repeat(40)).append("\n");
        sb.append(String.format("  Datacenters: %d\n", template.getDatacenterConfigs().size()));
        sb.append(String.format("  Hosts: %d\n", template.getHostConfigs().size()));
        sb.append(String.format("  Users: %d\n", template.getUserTemplates().size()));
        sb.append(String.format("  VMs: %d\n", template.getTotalVMCount()));
        sb.append(String.format("  Tasks: %d\n", template.getTotalTaskCount()));
        sb.append("=".repeat(60)).append("\n");

        summaryArea.setText(sb.toString());
    }

    private void generateConfigs() {
        // Validate configuration
        if (!validateConfiguration()) {
            return;
        }

        try {
            long startSeed = Long.parseLong(startSeedField.getText().trim());
            long endSeed = Long.parseLong(endSeedField.getText().trim());

            if (startSeed > endSeed) {
                showError("Start seed must be <= end seed");
                return;
            }

            File outputDir = new File(outputDirField.getText().trim());

            List<File> generatedFiles = writer.generateConfigs(template, startSeed, endSeed, outputDir);

            statusLabel.setText(String.format("Generated %d configuration files in %s",
                generatedFiles.size(), outputDir.getAbsolutePath()));
            statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");

            // Show success dialog
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Generation Complete");
            alert.setHeaderText("Configuration files generated successfully!");
            alert.setContentText(String.format("Generated %d files:\n%s\n\nLocation: %s",
                generatedFiles.size(),
                generatedFiles.size() <= 5 ?
                    generatedFiles.stream()
                        .map(File::getName)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("") :
                    generatedFiles.get(0).getName() + "\n...\n" +
                        generatedFiles.get(generatedFiles.size() - 1).getName(),
                outputDir.getAbsolutePath()));
            alert.showAndWait();

        } catch (NumberFormatException e) {
            showError("Please enter valid seed values");
        } catch (Exception e) {
            showError("Error generating files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void previewConfig() {
        if (!validateConfiguration()) {
            return;
        }

        try {
            long seed = Long.parseLong(startSeedField.getText().trim());

            // Create a temporary file to preview
            File tempFile = File.createTempFile("preview_", ".cosc");
            tempFile.deleteOnExit();

            writer.writeConfigFile(template, seed, tempFile);

            // Read and display the file
            String content = new String(java.nio.file.Files.readAllBytes(tempFile.toPath()));

            TextArea previewArea = new TextArea(content);
            previewArea.setEditable(false);
            previewArea.setStyle("-fx-font-family: monospace;");
            previewArea.setPrefSize(700, 500);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Configuration Preview (Seed: " + seed + ")");
            dialog.setHeaderText("Preview of generated .cosc file");
            dialog.getDialogPane().setContent(previewArea);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();

        } catch (Exception e) {
            showError("Error generating preview: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean validateConfiguration() {
        if (template.getDatacenterConfigs().isEmpty()) {
            showError("At least one datacenter is required");
            return false;
        }

        if (template.getHostConfigs().isEmpty()) {
            showError("At least one host is required");
            return false;
        }

        if (template.getUserTemplates().isEmpty()) {
            showError("At least one user is required");
            return false;
        }

        // Check that users have VMs and tasks
        for (UserTemplate user : template.getUserTemplates()) {
            if (user.getVmTemplates().isEmpty()) {
                showError("User '" + user.getName() + "' has no VMs configured");
                return false;
            }
            if (user.getTaskTemplates().isEmpty()) {
                showError("User '" + user.getName() + "' has no tasks configured");
                return false;
            }
            if (user.getSelectedDatacenterNames().isEmpty()) {
                showError("User '" + user.getName() + "' has no datacenters selected");
                return false;
            }
        }

        return true;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();

        statusLabel.setText("Error: " + message);
        statusLabel.setStyle("-fx-text-fill: #f44336;");
    }
}
