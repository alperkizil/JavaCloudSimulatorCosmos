package com.cloudsimulator.gui;

import com.cloudsimulator.config.DatacenterConfig;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

/**
 * Panel for managing datacenter configurations.
 */
public class DatacenterPanel extends VBox {

    private final ExperimentTemplate template;
    private final TableView<DatacenterConfig> tableView;
    private final ObservableList<DatacenterConfig> datacenterList;

    private TextField nameField;
    private TextField capacityField;
    private TextField powerField;

    public DatacenterPanel(ExperimentTemplate template) {
        this.template = template;
        this.datacenterList = FXCollections.observableArrayList(template.getDatacenterConfigs());

        setSpacing(15);
        setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("Datacenter Configuration");
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

        nameField = new TextField();
        nameField.setPromptText("e.g., DC-East");
        nameField.setPrefWidth(200);

        capacityField = new TextField();
        capacityField.setPromptText("e.g., 50");
        capacityField.setPrefWidth(100);

        powerField = new TextField();
        powerField.setPromptText("e.g., 100000.0");
        powerField.setPrefWidth(120);

        form.add(new Label("Name:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Max Host Capacity:"), 2, 0);
        form.add(capacityField, 3, 0);
        form.add(new Label("Max Power Draw (W):"), 4, 0);
        form.add(powerField, 5, 0);

        return form;
    }

    private HBox createButtonBox() {
        Button addButton = new Button("Add Datacenter");
        addButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addButton.setOnAction(e -> addDatacenter());

        Button removeButton = new Button("Remove Selected");
        removeButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeButton.setOnAction(e -> removeSelected());

        Button clearButton = new Button("Clear All");
        clearButton.setOnAction(e -> clearAll());

        HBox buttonBox = new HBox(10, addButton, removeButton, clearButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        return buttonBox;
    }

    @SuppressWarnings("unchecked")
    private TableView<DatacenterConfig> createTableView() {
        TableView<DatacenterConfig> table = new TableView<>(datacenterList);
        table.setPlaceholder(new Label("No datacenters configured. Add one above."));

        TableColumn<DatacenterConfig, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);

        TableColumn<DatacenterConfig, Integer> capacityCol = new TableColumn<>("Max Hosts");
        capacityCol.setCellValueFactory(new PropertyValueFactory<>("maxHostCapacity"));
        capacityCol.setPrefWidth(100);

        TableColumn<DatacenterConfig, Double> powerCol = new TableColumn<>("Max Power (W)");
        powerCol.setCellValueFactory(new PropertyValueFactory<>("totalMaxPowerDraw"));
        powerCol.setPrefWidth(150);

        table.getColumns().addAll(nameCol, capacityCol, powerCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    private void addDatacenter() {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                showError("Name is required");
                return;
            }

            // Check for duplicate names
            for (DatacenterConfig dc : datacenterList) {
                if (dc.getName().equals(name)) {
                    showError("Datacenter with this name already exists");
                    return;
                }
            }

            int capacity = Integer.parseInt(capacityField.getText().trim());
            double power = Double.parseDouble(powerField.getText().trim());

            DatacenterConfig config = new DatacenterConfig(name, capacity, power);
            datacenterList.add(config);
            template.getDatacenterConfigs().add(config);

            clearInputFields();
        } catch (NumberFormatException e) {
            showError("Please enter valid numeric values");
        }
    }

    private void removeSelected() {
        DatacenterConfig selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            datacenterList.remove(selected);
            template.getDatacenterConfigs().remove(selected);
        }
    }

    private void clearAll() {
        datacenterList.clear();
        template.getDatacenterConfigs().clear();
    }

    private void clearInputFields() {
        nameField.clear();
        capacityField.clear();
        powerField.clear();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Input Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void refresh() {
        datacenterList.setAll(template.getDatacenterConfigs());
    }
}
