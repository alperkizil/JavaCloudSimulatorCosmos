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
 */
public class HostPanel extends VBox {

    private final ExperimentTemplate template;
    private final TableView<HostConfig> tableView;
    private final ObservableList<HostConfig> hostList;

    private TextField ipsField;
    private TextField coresField;
    private ComboBox<ComputeType> computeTypeCombo;
    private TextField gpusField;
    private TextField ramField;
    private TextField networkField;
    private TextField storageField;
    private ComboBox<String> powerModelCombo;

    public HostPanel(ExperimentTemplate template) {
        this.template = template;
        this.hostList = FXCollections.observableArrayList(template.getHostConfigs());

        setSpacing(15);
        setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("Host Configuration");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Input form
        GridPane form = createInputForm();

        // Buttons
        HBox buttonBox = createButtonBox();

        // Table
        tableView = createTableView();

        getChildren().addAll(titleLabel, form, buttonBox, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
    }

    private GridPane createInputForm() {
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(10));
        form.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        // Row 1
        ipsField = new TextField("2500000000");
        ipsField.setPromptText("IPS");
        ipsField.setPrefWidth(120);

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
        form.add(ipsField, 1, 0);
        form.add(new Label("CPU Cores:"), 2, 0);
        form.add(coresField, 3, 0);
        form.add(new Label("Compute Type:"), 4, 0);
        form.add(computeTypeCombo, 5, 0);
        form.add(new Label("GPUs:"), 6, 0);
        form.add(gpusField, 7, 0);

        // Row 2
        ramField = new TextField("2097152");
        ramField.setPromptText("RAM MB");
        ramField.setPrefWidth(100);

        networkField = new TextField("2000000");
        networkField.setPromptText("Network Mbps");
        networkField.setPrefWidth(100);

        storageField = new TextField("20971520");
        storageField.setPromptText("Storage MB");
        storageField.setPrefWidth(100);

        powerModelCombo = new ComboBox<>(FXCollections.observableArrayList(
            "StandardPowerModel", "HighPerformancePowerModel", "LowPowerModel"));
        powerModelCombo.setValue("StandardPowerModel");
        powerModelCombo.setPrefWidth(180);

        form.add(new Label("RAM (MB):"), 0, 1);
        form.add(ramField, 1, 1);
        form.add(new Label("Network (Mbps):"), 2, 1);
        form.add(networkField, 3, 1);
        form.add(new Label("Storage (MB):"), 4, 1);
        form.add(storageField, 5, 1);
        form.add(new Label("Power Model:"), 6, 1);
        form.add(powerModelCombo, 7, 1);

        return form;
    }

    private HBox createButtonBox() {
        Button addButton = new Button("Add Host");
        addButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addButton.setOnAction(e -> addHost());

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

    private void addHost() {
        try {
            long ips = Long.parseLong(ipsField.getText().trim());
            int cores = Integer.parseInt(coresField.getText().trim());
            ComputeType computeType = computeTypeCombo.getValue();
            int gpus = Integer.parseInt(gpusField.getText().trim());
            long ram = Long.parseLong(ramField.getText().trim());
            long network = Long.parseLong(networkField.getText().trim());
            long storage = Long.parseLong(storageField.getText().trim());
            String powerModel = powerModelCombo.getValue();

            HostConfig config = new HostConfig(ips, cores, computeType, gpus, ram, network, storage, powerModel);
            hostList.add(config);
            template.getHostConfigs().add(config);
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
            HostConfig clone = selected.clone();
            hostList.add(clone);
            template.getHostConfigs().add(clone);
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
