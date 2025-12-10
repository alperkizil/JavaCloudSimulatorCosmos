package com.cloudsimulator.gui;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel for managing user configurations with VMs and tasks.
 * Uses an accordion layout for Datacenter Assignment, VMs, and Tasks sections
 * where only one section can be expanded at a time.
 */
public class UserPanel extends VBox {

    private final ExperimentTemplate template;
    private ListView<UserTemplate> userListView;
    private final ObservableList<UserTemplate> userList;

    private TextField userNameField;
    private VBox userDetailBox;
    private UserTemplate selectedUser;

    // Accordion for expandable sections
    private Accordion accordion;
    private TitledPane dcTitledPane;
    private TitledPane vmTitledPane;
    private TitledPane taskTitledPane;

    // VM components
    private ListView<VMTemplate> vmListView;
    private ObservableList<VMTemplate> vmList;
    private ComboBox<ComputeType> vmTypeCombo;
    private TextField vmIpsField;
    private TextField vmVcpusField;
    private TextField vmGpusField;
    private TextField vmRamField;
    private TextField vmStorageField;
    private TextField vmBandwidthField;
    private Spinner<Integer> vmInstanceCountSpinner;

    // Task components
    private ListView<TaskTemplate> taskListView;
    private ObservableList<TaskTemplate> taskList;
    private TextField taskPrefixField;
    private ComboBox<WorkloadType> taskTypeCombo;
    private TextField taskMinInstrField;
    private TextField taskMaxInstrField;
    private TextField taskCountField;

    // Datacenter selection
    private ListView<String> datacenterListView;
    private ObservableList<String> selectedDatacenters;
    private ComboBox<String> dcCombo;

    public UserPanel(ExperimentTemplate template) {
        this.template = template;
        this.userList = FXCollections.observableArrayList(template.getUserTemplates());
        this.vmList = FXCollections.observableArrayList();
        this.taskList = FXCollections.observableArrayList();
        this.selectedDatacenters = FXCollections.observableArrayList();

        setSpacing(10);
        setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("User Configuration");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Main content - split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.25);

        // Left side - user list
        VBox leftPane = createUserListPane();

        // Right side - user details with accordion
        userDetailBox = createUserDetailPane();
        userDetailBox.setDisable(true);

        splitPane.getItems().addAll(leftPane, userDetailBox);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, splitPane);
    }

    private VBox createUserListPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color: #f5f5f5;");

        Label label = new Label("Users");
        label.setStyle("-fx-font-weight: bold;");

        userNameField = new TextField();
        userNameField.setPromptText("User name");

        Button addUserBtn = new Button("Add User");
        addUserBtn.setMaxWidth(Double.MAX_VALUE);
        addUserBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addUserBtn.setOnAction(e -> addUser());

        Button removeUserBtn = new Button("Remove User");
        removeUserBtn.setMaxWidth(Double.MAX_VALUE);
        removeUserBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeUserBtn.setOnAction(e -> removeUser());

        userListView = new ListView<>(userList);
        userListView.setPlaceholder(new Label("No users"));
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectUser(newVal);
        });
        VBox.setVgrow(userListView, Priority.ALWAYS);

        pane.getChildren().addAll(label, userNameField, addUserBtn, removeUserBtn, userListView);
        return pane;
    }

    private VBox createUserDetailPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // Create the accordion with three expandable sections
        accordion = new Accordion();

        // Datacenter selection pane
        dcTitledPane = createDatacenterSelectionPane();

        // VM configuration pane
        vmTitledPane = createVMPane();

        // Task configuration pane
        taskTitledPane = createTaskPane();

        // Add all panes to accordion
        accordion.getPanes().addAll(dcTitledPane, vmTitledPane, taskTitledPane);

        // Set default expanded pane
        accordion.setExpandedPane(dcTitledPane);

        VBox.setVgrow(accordion, Priority.ALWAYS);
        pane.getChildren().add(accordion);

        return pane;
    }

    private TitledPane createDatacenterSelectionPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        Label infoLabel = new Label("Select datacenters for this user:");
        infoLabel.setStyle("-fx-font-weight: bold;");

        // Datacenter ComboBox - made larger and more prominent
        dcCombo = new ComboBox<>();
        dcCombo.setPromptText("Select datacenter to add");
        dcCombo.setMaxWidth(Double.MAX_VALUE);
        dcCombo.setPrefHeight(35);
        dcCombo.setStyle("-fx-font-size: 14px;");
        HBox.setHgrow(dcCombo, Priority.ALWAYS);

        // Refresh combo when showing
        dcCombo.setOnShowing(e -> {
            dcCombo.setItems(FXCollections.observableArrayList(template.getDatacenterNames()));
        });

        // Buttons row
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button addDcBtn = new Button("Add Datacenter");
        addDcBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addDcBtn.setOnAction(e -> {
            String dc = dcCombo.getValue();
            if (dc != null && !selectedDatacenters.contains(dc) && selectedUser != null) {
                selectedDatacenters.add(dc);
                selectedUser.addDatacenter(dc);
            }
        });

        Button removeDcBtn = new Button("Remove Selected");
        removeDcBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeDcBtn.setOnAction(e -> {
            String dc = datacenterListView.getSelectionModel().getSelectedItem();
            if (dc != null && selectedUser != null) {
                selectedDatacenters.remove(dc);
                selectedUser.removeDatacenter(dc);
            }
        });

        btnBox.getChildren().addAll(addDcBtn, removeDcBtn);

        // Datacenter list
        datacenterListView = new ListView<>(selectedDatacenters);
        datacenterListView.setPrefHeight(150);
        datacenterListView.setPlaceholder(new Label("No datacenters selected"));
        VBox.setVgrow(datacenterListView, Priority.ALWAYS);

        content.getChildren().addAll(infoLabel, dcCombo, btnBox, datacenterListView);

        TitledPane pane = new TitledPane("Datacenter Assignment", content);
        return pane;
    }

    private TitledPane createVMPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // Bulk instance option row
        HBox bulkRow = new HBox(15);
        bulkRow.setAlignment(Pos.CENTER_LEFT);
        bulkRow.setPadding(new Insets(5, 10, 10, 10));
        bulkRow.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 5;");

        Label instanceLabel = new Label("Number of VMs to Add:");
        instanceLabel.setStyle("-fx-font-weight: bold;");
        vmInstanceCountSpinner = new Spinner<>(1, 1000, 1);
        vmInstanceCountSpinner.setEditable(true);
        vmInstanceCountSpinner.setPrefWidth(80);
        vmInstanceCountSpinner.setTooltip(new Tooltip("Number of identical VMs to add at once"));

        bulkRow.getChildren().addAll(instanceLabel, vmInstanceCountSpinner);

        // VM input form - more compact layout
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);

        vmTypeCombo = new ComboBox<>(FXCollections.observableArrayList(ComputeType.values()));
        vmTypeCombo.setValue(ComputeType.CPU_ONLY);
        vmTypeCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(vmTypeCombo, Priority.ALWAYS);

        vmIpsField = new TextField("2000000000");
        vmIpsField.setPromptText("IPS per vCPU");

        vmVcpusField = new TextField("4");
        vmVcpusField.setPromptText("vCPUs");

        vmGpusField = new TextField("0");
        vmGpusField.setPromptText("GPUs");

        vmRamField = new TextField("8192");
        vmRamField.setPromptText("RAM (MB)");

        vmStorageField = new TextField("102400");
        vmStorageField.setPromptText("Storage (MB)");

        vmBandwidthField = new TextField("1000");
        vmBandwidthField.setPromptText("Bandwidth (Mbps)");

        // Row 0: Type and IPS
        form.add(new Label("Type:"), 0, 0);
        form.add(vmTypeCombo, 1, 0, 3, 1);

        form.add(new Label("IPS/vCPU:"), 0, 1);
        form.add(vmIpsField, 1, 1);
        form.add(new Label("vCPUs:"), 2, 1);
        form.add(vmVcpusField, 3, 1);

        // Row 2: GPUs and RAM
        form.add(new Label("GPUs:"), 0, 2);
        form.add(vmGpusField, 1, 2);
        form.add(new Label("RAM (MB):"), 2, 2);
        form.add(vmRamField, 3, 2);

        // Row 3: Storage and Bandwidth
        form.add(new Label("Storage (MB):"), 0, 3);
        form.add(vmStorageField, 1, 3);
        form.add(new Label("Bandwidth:"), 2, 3);
        form.add(vmBandwidthField, 3, 3);

        // VM buttons
        HBox vmBtnBox = new HBox(10);
        vmBtnBox.setAlignment(Pos.CENTER_LEFT);

        Button addVmBtn = new Button("Add VM(s)");
        addVmBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addVmBtn.setOnAction(e -> addVM());

        Button removeVmBtn = new Button("Remove VM");
        removeVmBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeVmBtn.setOnAction(e -> removeVM());

        vmBtnBox.getChildren().addAll(addVmBtn, removeVmBtn);

        // VM list
        vmListView = new ListView<>(vmList);
        vmListView.setPrefHeight(120);
        vmListView.setPlaceholder(new Label("No VMs configured"));
        VBox.setVgrow(vmListView, Priority.ALWAYS);

        content.getChildren().addAll(bulkRow, form, vmBtnBox, vmListView);

        TitledPane pane = new TitledPane("Virtual Machines", content);
        return pane;
    }

    private TitledPane createTaskPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // Task input form
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);

        taskPrefixField = new TextField("Task");
        taskPrefixField.setPromptText("Task name prefix");

        List<WorkloadType> workloadTypes = new ArrayList<>();
        for (WorkloadType wt : WorkloadType.values()) {
            if (wt != WorkloadType.IDLE) {
                workloadTypes.add(wt);
            }
        }
        taskTypeCombo = new ComboBox<>(FXCollections.observableArrayList(workloadTypes));
        taskTypeCombo.setValue(WorkloadType.SEVEN_ZIP);
        taskTypeCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(taskTypeCombo, Priority.ALWAYS);

        taskMinInstrField = new TextField("1000000000");
        taskMinInstrField.setPromptText("Min instructions");

        taskMaxInstrField = new TextField("10000000000");
        taskMaxInstrField.setPromptText("Max instructions");

        taskCountField = new TextField("1");
        taskCountField.setPromptText("Count");

        // Row 0: Name prefix and workload type
        form.add(new Label("Name Prefix:"), 0, 0);
        form.add(taskPrefixField, 1, 0);

        form.add(new Label("Workload Type:"), 0, 1);
        form.add(taskTypeCombo, 1, 1, 3, 1);

        // Row 2: Instructions range
        form.add(new Label("Min Instructions:"), 0, 2);
        form.add(taskMinInstrField, 1, 2);
        form.add(new Label("Max Instructions:"), 2, 2);
        form.add(taskMaxInstrField, 3, 2);

        // Row 3: Count
        form.add(new Label("Count:"), 0, 3);
        form.add(taskCountField, 1, 3);

        Label rangeHint = new Label("(Actual instruction length randomized per seed within range)");
        rangeHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        // Task buttons
        HBox taskBtnBox = new HBox(10);
        taskBtnBox.setAlignment(Pos.CENTER_LEFT);

        Button addTaskBtn = new Button("Add Task Template");
        addTaskBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addTaskBtn.setOnAction(e -> addTask());

        Button removeTaskBtn = new Button("Remove Task");
        removeTaskBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeTaskBtn.setOnAction(e -> removeTask());

        taskBtnBox.getChildren().addAll(addTaskBtn, removeTaskBtn);

        // Task list
        taskListView = new ListView<>(taskList);
        taskListView.setPrefHeight(150);
        taskListView.setPlaceholder(new Label("No tasks configured"));
        VBox.setVgrow(taskListView, Priority.ALWAYS);

        content.getChildren().addAll(form, rangeHint, taskBtnBox, taskListView);

        TitledPane pane = new TitledPane("Tasks (with instruction length range)", content);
        return pane;
    }

    private void addUser() {
        String name = userNameField.getText().trim();
        if (name.isEmpty()) {
            showError("User name is required");
            return;
        }

        // Check for duplicate names
        for (UserTemplate user : userList) {
            if (user.getName().equals(name)) {
                showError("User with this name already exists");
                return;
            }
        }

        UserTemplate user = new UserTemplate(name);
        userList.add(user);
        template.getUserTemplates().add(user);
        userNameField.clear();

        // Select the new user
        userListView.getSelectionModel().select(user);
    }

    private void removeUser() {
        UserTemplate user = userListView.getSelectionModel().getSelectedItem();
        if (user != null) {
            userList.remove(user);
            template.getUserTemplates().remove(user);
            selectUser(null);
        }
    }

    private void selectUser(UserTemplate user) {
        selectedUser = user;
        userDetailBox.setDisable(user == null);

        if (user != null) {
            vmList.setAll(user.getVmTemplates());
            taskList.setAll(user.getTaskTemplates());
            selectedDatacenters.setAll(user.getSelectedDatacenterNames());
            // Expand the first section when user is selected
            accordion.setExpandedPane(dcTitledPane);
        } else {
            vmList.clear();
            taskList.clear();
            selectedDatacenters.clear();
        }
    }

    private void addVM() {
        if (selectedUser == null) return;

        try {
            ComputeType type = vmTypeCombo.getValue();
            long ips = Long.parseLong(vmIpsField.getText().trim());
            int vcpus = Integer.parseInt(vmVcpusField.getText().trim());
            int gpus = Integer.parseInt(vmGpusField.getText().trim());
            long ram = Long.parseLong(vmRamField.getText().trim());
            long storage = Long.parseLong(vmStorageField.getText().trim());
            long bandwidth = Long.parseLong(vmBandwidthField.getText().trim());

            int instanceCount = vmInstanceCountSpinner.getValue();

            for (int i = 0; i < instanceCount; i++) {
                VMTemplate vm = new VMTemplate(ips, vcpus, gpus, ram, storage, bandwidth, type);
                selectedUser.addVmTemplate(vm);
                vmList.add(vm);
            }

            // Reset instance count to 1 after adding
            vmInstanceCountSpinner.getValueFactory().setValue(1);

        } catch (NumberFormatException e) {
            showError("Please enter valid numeric values for VM");
        }
    }

    private void removeVM() {
        VMTemplate vm = vmListView.getSelectionModel().getSelectedItem();
        if (vm != null && selectedUser != null) {
            selectedUser.removeVmTemplate(vm);
            vmList.remove(vm);
        }
    }

    private void addTask() {
        if (selectedUser == null) return;

        try {
            String prefix = taskPrefixField.getText().trim();
            if (prefix.isEmpty()) {
                showError("Task prefix is required");
                return;
            }

            WorkloadType type = taskTypeCombo.getValue();
            long minInstr = Long.parseLong(taskMinInstrField.getText().trim());
            long maxInstr = Long.parseLong(taskMaxInstrField.getText().trim());
            int count = Integer.parseInt(taskCountField.getText().trim());

            if (minInstr > maxInstr) {
                showError("Min instructions must be <= Max instructions");
                return;
            }

            if (count <= 0) {
                showError("Task count must be positive");
                return;
            }

            TaskTemplate task = new TaskTemplate(prefix, type, minInstr, maxInstr, count);
            selectedUser.addTaskTemplate(task);
            taskList.add(task);
        } catch (NumberFormatException e) {
            showError("Please enter valid numeric values for task");
        }
    }

    private void removeTask() {
        TaskTemplate task = taskListView.getSelectionModel().getSelectedItem();
        if (task != null && selectedUser != null) {
            selectedUser.removeTaskTemplate(task);
            taskList.remove(task);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Input Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void refresh() {
        userList.setAll(template.getUserTemplates());
        selectUser(null);
    }
}
