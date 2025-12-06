package com.cloudsimulator.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * JavaFX application for generating Cosmos experiment configuration files (.cosc).
 *
 * Features:
 * - Configure datacenters, hosts, users, VMs, and tasks
 * - Generate multiple configuration files with different seeds
 * - Task instruction lengths vary based on seed within specified ranges
 * - All other configuration elements remain identical across seed variations
 *
 * Usage:
 *   java -cp out:javafx-libs/* com.cloudsimulator.gui.ConfigGeneratorApp
 *
 * Or with Maven:
 *   mvn javafx:run
 */
public class ConfigGeneratorApp extends Application {

    private ExperimentTemplate template;
    private DatacenterPanel datacenterPanel;
    private HostPanel hostPanel;
    private UserPanel userPanel;
    private SummaryPanel summaryPanel;

    @Override
    public void start(Stage primaryStage) {
        // Initialize the experiment template
        template = new ExperimentTemplate();

        // Create the main layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Create header
        VBox header = createHeader();
        root.setTop(header);

        // Create tabbed content
        TabPane tabPane = createTabPane();
        root.setCenter(tabPane);

        // Create scene
        Scene scene = new Scene(root, 1200, 800);

        // Apply some basic styling
        scene.getStylesheets().add("data:text/css," +
            ".tab-pane .tab-header-area .tab-header-background { -fx-background-color: #e0e0e0; } " +
            ".tab { -fx-background-color: #f5f5f5; } " +
            ".tab:selected { -fx-background-color: white; } " +
            ".button { -fx-cursor: hand; } " +
            ".table-view { -fx-background-color: white; } " +
            ".text-field, .combo-box { -fx-background-color: white; }");

        primaryStage.setTitle("Cosmos Experiment Configuration Generator");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createHeader() {
        VBox header = new VBox(5);
        header.setPadding(new Insets(10, 10, 20, 10));

        Label titleLabel = new Label("Cosmos Experiment Configuration Generator");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1976D2;");

        Label subtitleLabel = new Label("Create and export simulation experiment configurations with multiple seed variations");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        Separator separator = new Separator();

        header.getChildren().addAll(titleLabel, subtitleLabel, separator);
        return header;
    }

    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Create panels
        datacenterPanel = new DatacenterPanel(template);
        hostPanel = new HostPanel(template);
        userPanel = new UserPanel(template);
        summaryPanel = new SummaryPanel(template);

        // Create tabs
        Tab datacenterTab = new Tab("1. Datacenters", datacenterPanel);
        datacenterTab.setStyle("-fx-font-weight: bold;");

        Tab hostTab = new Tab("2. Hosts", hostPanel);
        hostTab.setStyle("-fx-font-weight: bold;");

        Tab userTab = new Tab("3. Users (VMs & Tasks)", userPanel);
        userTab.setStyle("-fx-font-weight: bold;");

        Tab summaryTab = new Tab("4. Summary & Export", summaryPanel);
        summaryTab.setStyle("-fx-font-weight: bold;");

        // Refresh summary when tab is selected
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == summaryTab) {
                summaryPanel.refreshSummary();
            }
        });

        tabPane.getTabs().addAll(datacenterTab, hostTab, userTab, summaryTab);
        return tabPane;
    }

    /**
     * Main entry point for the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
