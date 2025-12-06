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
 */
public class UserPanel extends VBox {

    private final ExperimentTemplate template;
    private ListView<UserTemplate> userListView;
    private final ObservableList<UserTemplate> userList;

    private TextField userNameField;
    private VBox userDetailBox;
    private UserTemplate selectedUser;

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

        // Right side - user details
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
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(10));

        // Datacenter selection
        TitledPane dcPane = createDatacenterSelectionPane();

        // VM configuration
        TitledPane vmPane = createVMPane();

        // Task configuration
        TitledPane taskPane = createTaskPane();

        pane.getChildren().addAll(dcPane, vmPane, taskPane);
        return pane;
    }

    private TitledPane createDatacenterSelectionPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label infoLabel = new Label("Select datacenters for this user:");

        datacenterListView = new ListView<>(selectedDatacenters);
        datacenterListView.setPrefHeight(80);
        datacenterListView.setPlaceholder(new Label("No datacenters selected"));

        HBox btnBox = new HBox(10);
        ComboBox<String> dcCombo = new ComboBox<>();
        dcCombo.setPromptText("Select datacenter");
        dcCombo.setPrefWidth(150);

        Button addDcBtn = new Button("Add");
        addDcBtn.setOnAction(e -> {
            String dc = dcCombo.getValue();
            if (dc != null && !selectedDatacenters.contains(dc) && selectedUser != null) {
                selectedDatacenters.add(dc);
                selectedUser.addDatacenter(dc);
            }
        });

        Button removeDcBtn = new Button("Remove");
        removeDcBtn.setOnAction(e -> {
            String dc = datacenterListView.getSelectionModel().getSelectedItem();
            if (dc != null && selectedUser != null) {
                selectedDatacenters.remove(dc);
                selectedUser.removeDatacenter(dc);
            }
        });

        // Refresh combo when panel is updated
        dcCombo.setOnShowing(e -> {
            dcCombo.setItems(FXCollections.observableArrayList(template.getDatacenterNames()));
        });

        btnBox.getChildren().addAll(dcCombo, addDcBtn, removeDcBtn);
        content.getChildren().addAll(infoLabel, datacenterListView, btnBox);

        TitledPane pane = new TitledPane("Datacenter Assignment", content);
        pane.setCollapsible(false);
        return pane;
    }

    private TitledPane createVMPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // VM input form
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(5);

        vmTypeCombo = new ComboBox<>(FXCollections.observableArrayList(ComputeType.values()));
        vmTypeCombo.setValue(ComputeType.CPU_ONLY);
        vmTypeCombo.setPrefWidth(120);

        vmIpsField = new TextField("2000000000");
        vmIpsField.setPrefWidth(100);

        vmVcpusField = new TextField("4");
        vmVcpusField.setPrefWidth(50);

        vmGpusField = new TextField("0");
        vmGpusField.setPrefWidth(50);

        vmRamField = new TextField("8192");
        vmRamField.setPrefWidth(80);

        vmStorageField = new TextField("102400");
        vmStorageField.setPrefWidth(80);

        vmBandwidthField = new TextField("1000");
        vmBandwidthField.setPrefWidth(80);

        form.add(new Label("Type:"), 0, 0);
        form.add(vmTypeCombo, 1, 0);
        form.add(new Label("IPS/vCPU:"), 2, 0);
        form.add(vmIpsField, 3, 0);
        form.add(new Label("vCPUs:"), 4, 0);
        form.add(vmVcpusField, 5, 0);
        form.add(new Label("GPUs:"), 6, 0);
        form.add(vmGpusField, 7, 0);

        form.add(new Label("RAM (MB):"), 0, 1);
        form.add(vmRamField, 1, 1);
        form.add(new Label("Storage (MB):"), 2, 1);
        form.add(vmStorageField, 3, 1);
        form.add(new Label("Bandwidth:"), 4, 1);
        form.add(vmBandwidthField, 5, 1);

        // VM buttons
        HBox vmBtnBox = new HBox(10);
        Button addVmBtn = new Button("Add VM");
        addVmBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addVmBtn.setOnAction(e -> addVM());

        Button removeVmBtn = new Button("Remove VM");
        removeVmBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeVmBtn.setOnAction(e -> removeVM());

        vmBtnBox.getChildren().addAll(addVmBtn, removeVmBtn);

        // VM list
        vmListView = new ListView<>(vmList);
        vmListView.setPrefHeight(100);
        vmListView.setPlaceholder(new Label("No VMs configured"));

        content.getChildren().addAll(form, vmBtnBox, vmListView);

        TitledPane pane = new TitledPane("Virtual Machines", content);
        pane.setCollapsible(false);
        return pane;
    }

    private TitledPane createTaskPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Task input form
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(5);

        taskPrefixField = new TextField("Task");
        taskPrefixField.setPrefWidth(100);

        List<WorkloadType> workloadTypes = new ArrayList<>();
        for (WorkloadType wt : WorkloadType.values()) {
            if (wt != WorkloadType.IDLE) {
                workloadTypes.add(wt);
            }
        }
        taskTypeCombo = new ComboBox<>(FXCollections.observableArrayList(workloadTypes));
        taskTypeCombo.setValue(WorkloadType.SEVEN_ZIP);
        taskTypeCombo.setPrefWidth(140);

        taskMinInstrField = new TextField("1000000000");
        taskMinInstrField.setPrefWidth(120);

        taskMaxInstrField = new TextField("10000000000");
        taskMaxInstrField.setPrefWidth(120);

        taskCountField = new TextField("1");
        taskCountField.setPrefWidth(50);

        form.add(new Label("Name Prefix:"), 0, 0);
        form.add(taskPrefixField, 1, 0);
        form.add(new Label("Workload Type:"), 2, 0);
        form.add(taskTypeCombo, 3, 0);
        form.add(new Label("Count:"), 4, 0);
        form.add(taskCountField, 5, 0);

        form.add(new Label("Min Instructions:"), 0, 1);
        form.add(taskMinInstrField, 1, 1);
        form.add(new Label("Max Instructions:"), 2, 1);
        form.add(taskMaxInstrField, 3, 1);

        Label rangeHint = new Label("(Actual instruction length randomized per seed within range)");
        rangeHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        // Task buttons
        HBox taskBtnBox = new HBox(10);
        Button addTaskBtn = new Button("Add Task Template");
        addTaskBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addTaskBtn.setOnAction(e -> addTask());

        Button removeTaskBtn = new Button("Remove Task");
        removeTaskBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeTaskBtn.setOnAction(e -> removeTask());

        taskBtnBox.getChildren().addAll(addTaskBtn, removeTaskBtn);

        // Task list
        taskListView = new ListView<>(taskList);
        taskListView.setPrefHeight(120);
        taskListView.setPlaceholder(new Label("No tasks configured"));

        content.getChildren().addAll(form, rangeHint, taskBtnBox, taskListView);

        TitledPane pane = new TitledPane("Tasks (with instruction length range)", content);
        pane.setCollapsible(false);
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

            VMTemplate vm = new VMTemplate(ips, vcpus, gpus, ram, storage, bandwidth, type);
            selectedUser.addVmTemplate(vm);
            vmList.add(vm);
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
