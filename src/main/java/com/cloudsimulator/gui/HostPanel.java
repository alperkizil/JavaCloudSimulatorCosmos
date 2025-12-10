package com.cloudsimulator.gui;

import com.cloudsimulator.config.HostConfig;
import com.cloudsimulator.enums.ComputeType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

/**
 * Panel for managing host configurations.
 * Supports bulk host creation, unit-based input for IPS/RAM/Network/Storage,
 * and a simplified mode for quick configuration.
 */
public class HostPanel extends VBox {

    private final ExperimentTemplate template;
    private final TableView<HostConfig> tableView;
    private final ObservableList<HostConfig> hostList;

    // Basic fields
    private TextField ipsField;
    private ComboBox<String> ipsUnitCombo;
    private TextField coresField;
    private ComboBox<ComputeType> computeTypeCombo;
    private TextField gpusField;

    // Fields with unit selectors
    private TextField ramField;
    private ComboBox<String> ramUnitCombo;
    private TextField networkField;
    private ComboBox<String> networkUnitCombo;
    private TextField storageField;
    private ComboBox<String> storageUnitCombo;

    private ComboBox<String> powerModelCombo;

    // Bulk and simplified mode
    private Spinner<Integer> instanceCountSpinner;
    private CheckBox simplifiedModeCheckbox;

    // Unit conversion constants
    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;

    private static final long MEGABYTE = 1L;
    private static final long GIGABYTE = 1_024L;
    private static final long TERABYTE = 1_024L * 1_024L;

    private static final long MEGABIT = 1L;
    private static final long GIGABIT = 1_000L;
    private static final long TERABIT = 1_000_000L;

    // Simplified mode value: 99 TB in MB
    private static final long SIMPLIFIED_VALUE_MB = 99L * TERABYTE;
    // 99 Tbit in Mbps
    private static final long SIMPLIFIED_VALUE_MBPS = 99L * TERABIT;

    public HostPanel(ExperimentTemplate template) {
        this.template = template;
        this.hostList = FXCollections.observableArrayList(template.getHostConfigs());

        setSpacing(15);
        setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("Host Configuration");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Simplified mode and bulk options row
        HBox optionsRow = createOptionsRow();

        // Input form
        GridPane form = createInputForm();

        // Buttons
        HBox buttonBox = createButtonBox();

        // Table
        tableView = createTableView();

        getChildren().addAll(titleLabel, optionsRow, form, buttonBox, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
    }

    private HBox createOptionsRow() {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 10, 5, 10));
        row.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 5;");

        // Bulk instance count
        Label instanceLabel = new Label("Number of Instances:");
        instanceCountSpinner = new Spinner<>(1, 1000, 1);
        instanceCountSpinner.setEditable(true);
        instanceCountSpinner.setPrefWidth(80);
        instanceCountSpinner.setTooltip(new Tooltip("Number of identical hosts to add at once"));

        // Simplified mode checkbox
        simplifiedModeCheckbox = new CheckBox("Simplified Mode");
        simplifiedModeCheckbox.setTooltip(new Tooltip("Sets RAM, Bandwidth, Storage to 99 TB (ignore these resources)"));
        simplifiedModeCheckbox.setOnAction(e -> applySimplifiedMode());

        row.getChildren().addAll(instanceLabel, instanceCountSpinner,
                                 new Separator(javafx.geometry.Orientation.VERTICAL),
                                 simplifiedModeCheckbox);
        return row;
    }

    private GridPane createInputForm() {
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(10));
        form.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        // Row 0: IPS with unit selector
        ipsField = new TextField("2.5");
        ipsField.setPromptText("IPS");
        ipsField.setPrefWidth(80);

        ipsUnitCombo = new ComboBox<>(FXCollections.observableArrayList("Thousand", "Million", "Billion"));
        ipsUnitCombo.setValue("Billion");
        ipsUnitCombo.setPrefWidth(90);
        ipsUnitCombo.setTooltip(new Tooltip("Instructions Per Second unit"));

        HBox ipsBox = new HBox(5, ipsField, ipsUnitCombo);

        coresField = new TextField("16");
        coresField.setPromptText("Cores");
        coresField.setPrefWidth(60);

        computeTypeCombo = new ComboBox<>(FXCollections.observableArrayList(ComputeType.values()));
        computeTypeCombo.setValue(ComputeType.CPU_ONLY);
        computeTypeCombo.setPrefWidth(130);

        gpusField = new TextField("0");
        gpusField.setPromptText("GPUs");
        gpusField.setPrefWidth(60);

        form.add(new Label("IPS:"), 0, 0);
        form.add(ipsBox, 1, 0);
        form.add(new Label("CPU Cores:"), 2, 0);
        form.add(coresField, 3, 0);
        form.add(new Label("Compute Type:"), 4, 0);
        form.add(computeTypeCombo, 5, 0);
        form.add(new Label("GPUs:"), 6, 0);
        form.add(gpusField, 7, 0);

        // Row 1: RAM with unit selector
        ramField = new TextField("2");
        ramField.setPromptText("RAM");
        ramField.setPrefWidth(60);

        ramUnitCombo = new ComboBox<>(FXCollections.observableArrayList("Megabytes", "Gigabytes", "Terabytes"));
        ramUnitCombo.setValue("Gigabytes");
        ramUnitCombo.setPrefWidth(100);
        ramUnitCombo.setTooltip(new Tooltip("RAM capacity unit"));

        HBox ramBox = new HBox(5, ramField, ramUnitCombo);

        // Network with unit selector
        networkField = new TextField("2");
        networkField.setPromptText("Network");
        networkField.setPrefWidth(60);

        networkUnitCombo = new ComboBox<>(FXCollections.observableArrayList("Megabit", "Gigabit", "Terabit"));
        networkUnitCombo.setValue("Gigabit");
        networkUnitCombo.setPrefWidth(90);
        networkUnitCombo.setTooltip(new Tooltip("Network bandwidth unit (per second)"));

        HBox networkBox = new HBox(5, networkField, networkUnitCombo);

        form.add(new Label("RAM:"), 0, 1);
        form.add(ramBox, 1, 1);
        form.add(new Label("Network:"), 2, 1);
        form.add(networkBox, 3, 1);

        // Row 2: Storage with unit selector and Power Model
        storageField = new TextField("20");
        storageField.setPromptText("Storage");
        storageField.setPrefWidth(60);

        storageUnitCombo = new ComboBox<>(FXCollections.observableArrayList("Megabytes", "Gigabytes", "Terabytes"));
        storageUnitCombo.setValue("Gigabytes");
        storageUnitCombo.setPrefWidth(100);
        storageUnitCombo.setTooltip(new Tooltip("Storage capacity unit"));

        HBox storageBox = new HBox(5, storageField, storageUnitCombo);

        // Power model combo with all 6 models from PowerModelFactory
        powerModelCombo = new ComboBox<>(FXCollections.observableArrayList(
            "StandardPowerModel",
            "HighPerformancePowerModel",
            "LowPowerModel",
            "EfficientPowerModel",
            "ServerPowerModel",
            "MeasurementBasedPowerModel"));
        powerModelCombo.setValue("StandardPowerModel");
        powerModelCombo.setPrefWidth(200);
        powerModelCombo.setTooltip(new Tooltip("Power consumption model for the host"));

        form.add(new Label("Storage:"), 0, 2);
        form.add(storageBox, 1, 2);
        form.add(new Label("Power Model:"), 2, 2);
        form.add(powerModelCombo, 3, 2, 5, 1);

        return form;
    }

    private HBox createButtonBox() {
        Button addButton = new Button("Add Host(s)");
        addButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addButton.setOnAction(e -> addHosts());

        Button removeButton = new Button("Remove Selected");
        removeButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeButton.setOnAction(e -> removeSelected());

        Button duplicateButton = new Button("Duplicate Selected");
        duplicateButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        duplicateButton.setOnAction(e -> duplicateSelected());

        Button clearButton = new Button("Clear All");
        clearButton.setOnAction(e -> clearAll());

        HBox buttonBox = new HBox(10, addButton, removeButton, duplicateButton, clearButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        return buttonBox;
    }

    @SuppressWarnings("unchecked")
    private TableView<HostConfig> createTableView() {
        TableView<HostConfig> table = new TableView<>(hostList);
        table.setPlaceholder(new Label("No hosts configured. Add one above."));

        TableColumn<HostConfig, Long> ipsCol = new TableColumn<>("IPS");
        ipsCol.setCellValueFactory(new PropertyValueFactory<>("instructionsPerSecond"));
        ipsCol.setPrefWidth(100);

        TableColumn<HostConfig, Integer> coresCol = new TableColumn<>("Cores");
        coresCol.setCellValueFactory(new PropertyValueFactory<>("numberOfCpuCores"));
        coresCol.setPrefWidth(60);

        TableColumn<HostConfig, ComputeType> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("computeType"));
        typeCol.setPrefWidth(100);

        TableColumn<HostConfig, Integer> gpusCol = new TableColumn<>("GPUs");
        gpusCol.setCellValueFactory(new PropertyValueFactory<>("numberOfGpus"));
        gpusCol.setPrefWidth(50);

        TableColumn<HostConfig, Long> ramCol = new TableColumn<>("RAM (MB)");
        ramCol.setCellValueFactory(new PropertyValueFactory<>("ramCapacityMB"));
        ramCol.setPrefWidth(80);

        TableColumn<HostConfig, Long> networkCol = new TableColumn<>("Network");
        networkCol.setCellValueFactory(new PropertyValueFactory<>("networkCapacityMbps"));
        networkCol.setPrefWidth(80);

        TableColumn<HostConfig, Long> storageCol = new TableColumn<>("Storage");
        storageCol.setCellValueFactory(new PropertyValueFactory<>("hardDriveCapacityMB"));
        storageCol.setPrefWidth(80);

        TableColumn<HostConfig, String> powerCol = new TableColumn<>("Power Model");
        powerCol.setCellValueFactory(new PropertyValueFactory<>("powerModelName"));
        powerCol.setPrefWidth(150);

        table.getColumns().addAll(ipsCol, coresCol, typeCol, gpusCol, ramCol, networkCol, storageCol, powerCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    private void applySimplifiedMode() {
        if (simplifiedModeCheckbox.isSelected()) {
            // Set fields to 99 TB equivalent
            ramField.setText("99");
            ramUnitCombo.setValue("Terabytes");
            ramField.setDisable(true);
            ramUnitCombo.setDisable(true);

            networkField.setText("99");
            networkUnitCombo.setValue("Terabit");
            networkField.setDisable(true);
            networkUnitCombo.setDisable(true);

            storageField.setText("99");
            storageUnitCombo.setValue("Terabytes");
            storageField.setDisable(true);
            storageUnitCombo.setDisable(true);
        } else {
            // Re-enable fields with default values
            ramField.setText("2");
            ramUnitCombo.setValue("Gigabytes");
            ramField.setDisable(false);
            ramUnitCombo.setDisable(false);

            networkField.setText("2");
            networkUnitCombo.setValue("Gigabit");
            networkField.setDisable(false);
            networkUnitCombo.setDisable(false);

            storageField.setText("20");
            storageUnitCombo.setValue("Gigabytes");
            storageField.setDisable(false);
            storageUnitCombo.setDisable(false);
        }
    }

    private long convertIpsToRaw(double value, String unit) {
        switch (unit) {
            case "Thousand": return (long) (value * THOUSAND);
            case "Million": return (long) (value * MILLION);
            case "Billion": return (long) (value * BILLION);
            default: return (long) value;
        }
    }

    private long convertRamToMB(double value, String unit) {
        switch (unit) {
            case "Megabytes": return (long) (value * MEGABYTE);
            case "Gigabytes": return (long) (value * GIGABYTE);
            case "Terabytes": return (long) (value * TERABYTE);
            default: return (long) value;
        }
    }

    private long convertNetworkToMbps(double value, String unit) {
        switch (unit) {
            case "Megabit": return (long) (value * MEGABIT);
            case "Gigabit": return (long) (value * GIGABIT);
            case "Terabit": return (long) (value * TERABIT);
            default: return (long) value;
        }
    }

    private long convertStorageToMB(double value, String unit) {
        return convertRamToMB(value, unit); // Same conversion as RAM
    }

    private void addHosts() {
        try {
            double ipsValue = Double.parseDouble(ipsField.getText().trim());
            long ips = convertIpsToRaw(ipsValue, ipsUnitCombo.getValue());

            int cores = Integer.parseInt(coresField.getText().trim());
            ComputeType computeType = computeTypeCombo.getValue();
            int gpus = Integer.parseInt(gpusField.getText().trim());

            double ramValue = Double.parseDouble(ramField.getText().trim());
            long ram = convertRamToMB(ramValue, ramUnitCombo.getValue());

            double networkValue = Double.parseDouble(networkField.getText().trim());
            long network = convertNetworkToMbps(networkValue, networkUnitCombo.getValue());

            double storageValue = Double.parseDouble(storageField.getText().trim());
            long storage = convertStorageToMB(storageValue, storageUnitCombo.getValue());

            String powerModel = powerModelCombo.getValue();

            int instanceCount = instanceCountSpinner.getValue();

            for (int i = 0; i < instanceCount; i++) {
                HostConfig config = new HostConfig(ips, cores, computeType, gpus, ram, network, storage, powerModel);
                hostList.add(config);
                template.getHostConfigs().add(config);
            }

            // Reset instance count to 1 after adding
            instanceCountSpinner.getValueFactory().setValue(1);

        } catch (NumberFormatException e) {
            showError("Please enter valid numeric values");
        }
    }

    private void removeSelected() {
        HostConfig selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            hostList.remove(selected);
            template.getHostConfigs().remove(selected);
        }
    }

    private void duplicateSelected() {
        HostConfig selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int instanceCount = instanceCountSpinner.getValue();
            for (int i = 0; i < instanceCount; i++) {
                HostConfig clone = selected.clone();
                hostList.add(clone);
                template.getHostConfigs().add(clone);
            }
            // Reset instance count to 1 after duplicating
            instanceCountSpinner.getValueFactory().setValue(1);
        }
    }

    private void clearAll() {
        hostList.clear();
        template.getHostConfigs().clear();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Input Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void refresh() {
        hostList.setAll(template.getHostConfigs());
    }
}
