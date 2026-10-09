package com.azuresight;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The teacher dashboard (JavaFX).
 * Tabs: Dashboard | Register Student | Attendance History | Proxy Alerts
 */
public class MainApp extends Application {

    private final DatabaseHelper db = new DatabaseHelper();
    private final CameraService camera = new CameraService();
    private final AzureFaceService azure = new AzureFaceService();
    private AttendanceService attendanceService;

    // camera previews (one on dashboard, one on register tab)
    private final ImageView dashboardCamera = new ImageView();
    private final ImageView registerCamera = new ImageView();

    // dashboard widgets
    private final Label statusLabel = new Label("Ready. Click 'Start Check-in' when a student is in front of the camera.");
    private final Label alertLabel = new Label();
    private final Label statsLabel = new Label();
    private final Button checkInButton = new Button("Start Check-in");
    private final TableView<AttendanceRecord> todayTable = new TableView<>();

    // other tabs
    private final TableView<AttendanceRecord> historyTable = new TableView<>();
    private final TableView<ProxyLog> proxyTable = new TableView<>();

    // register tab widgets
    private final TextField idField = new TextField();
    private final TextField nameField = new TextField();
    private final Label registerStatus = new Label();
    private final Button registerButton = new Button("Capture & Register");

    @Override
    public void start(Stage stage) {
        // 1. database
        try {
            db.init();
        } catch (Exception e) {
            showError("Database problem", e.getMessage());
            return;
        }
        attendanceService = new AttendanceService(camera, azure, db);

        // 2. screen
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Dashboard", buildDashboardTab()),
                new Tab("Register Student", buildRegisterTab()),
                new Tab("Attendance History", buildHistoryTab()),
                new Tab("Proxy Alerts", buildProxyTab()));

        Label title = new Label("SmartPresence - Face Recognition & Proxy Detector");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");
        HBox header = new HBox(title);
        header.setPadding(new Insets(12));
        header.setStyle("-fx-background-color: #0078d4;");   // Azure blue

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabs);

        stage.setTitle("SmartPresence - Team Azure Sight");
        stage.setScene(new Scene(root, 1000, 650));
        stage.show();

        // 3. start the camera
        try {
            camera.start(dashboardCamera, registerCamera);
        } catch (Exception e) {
            showError("Camera problem", e.getMessage());
            checkInButton.setDisable(true);
            registerButton.setDisable(true);
        }

        // 4. make sure the student group exists in Azure (runs in background)
        new Thread(() -> {
            try {
                azure.createPersonGroupIfNeeded();
            } catch (Exception e) {
                Platform.runLater(() -> showError("Azure problem",
                        "Could not connect to Azure. Check your key, endpoint and internet.\n\n" + e.getMessage()));
            }
        }).start();

        refreshAll();
    }

    @Override
    public void stop() {
        camera.stop();
    }

    // ================= DASHBOARD TAB =================

    private VBox buildDashboardTab() {
        dashboardCamera.setFitWidth(520);
        dashboardCamera.setPreserveRatio(true);

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 15px;");

        alertLabel.setWrapText(true);
        alertLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;"
                + "-fx-background-color: #d13438; -fx-padding: 8;");
        alertLabel.setVisible(false);
        alertLabel.setManaged(false);

        statsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        checkInButton.setStyle("-fx-font-size: 16px; -fx-base: #0078d4;");
        checkInButton.setOnAction(e -> startCheckIn());

        VBox rightSide = new VBox(12, statsLabel, checkInButton, statusLabel, alertLabel);
        rightSide.setPrefWidth(400);

        HBox top = new HBox(20, dashboardCamera, rightSide);

        todayTable.getColumns().add(makeColumn("Student ID", "studentId"));
        todayTable.getColumns().add(makeColumn("Name", "name"));
        todayTable.getColumns().add(makeColumn("Date", "date"));
        todayTable.getColumns().add(makeColumn("Time", "time"));
        todayTable.setPlaceholder(new Label("Nobody is marked present yet today."));

        VBox box = new VBox(10, top, new Label("Today's attendance:"), todayTable);
        box.setPadding(new Insets(12));
        VBox.setVgrow(todayTable, Priority.ALWAYS);
        return box;
    }

    // Runs the check-in in a separate thread so the screen does not freeze
    private void startCheckIn() {
        checkInButton.setDisable(true);
        alertLabel.setVisible(false);
        alertLabel.setManaged(false);

        new Thread(() -> {
            try {
                CheckInResult result = attendanceService.checkIn(
                        message -> Platform.runLater(() -> statusLabel.setText(message)));
                Platform.runLater(() -> showCheckInResult(result));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> checkInButton.setDisable(false));
            }
        }).start();
    }

    private void showCheckInResult(CheckInResult result) {
        statusLabel.setText(result.getMessage());

        if (result.isSuccess()) {
            statusLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: #107c10; -fx-font-weight: bold;");
        } else {
            statusLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: #d13438;");
        }

        // the red PROXY ALERT banner for the teacher
        if (result.isProxyAlert()) {
            alertLabel.setText("PROXY ALERT: " + result.getMessage());
            alertLabel.setVisible(true);
            alertLabel.setManaged(true);
        }
        refreshAll();
    }

    // ================= REGISTER TAB =================

    private VBox buildRegisterTab() {
        registerCamera.setFitWidth(520);
        registerCamera.setPreserveRatio(true);

        idField.setPromptText("Student ID / Roll No (e.g. 24152598)");
        nameField.setPromptText("Full name");

        registerButton.setStyle("-fx-font-size: 15px; -fx-base: #0078d4;");
        registerButton.setOnAction(e -> registerStudent());

        Label help = new Label("Ask the student to look straight at the camera in good light.\n"
                + "3 photos will be taken one after another.");
        help.setWrapText(true);

        VBox form = new VBox(10, new Label("Student ID:"), idField, new Label("Name:"), nameField,
                help, registerButton, registerStatus);
        form.setPrefWidth(400);
        registerStatus.setWrapText(true);

        HBox box = new HBox(20, registerCamera, form);
        box.setPadding(new Insets(12));

        VBox wrapper = new VBox(box);
        return wrapper;
    }

    private void registerStudent() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();

        if (id.isEmpty() || name.isEmpty()) {
            registerStatus.setText("Please fill both Student ID and Name.");
            return;
        }

        registerButton.setDisable(true);
        registerStatus.setText("Taking photos, please keep looking at the camera...");

        new Thread(() -> {
            try {
                if (db.studentExists(id)) {
                    Platform.runLater(() -> registerStatus.setText("This Student ID is already registered."));
                    return;
                }

                // take 3 photos, with a small gap between them
                List<byte[]> photos = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    photos.add(camera.captureJpeg());
                    Thread.sleep(800);
                }

                Platform.runLater(() -> registerStatus.setText("Sending to Azure and training, please wait..."));
                String personId = azure.registerStudent(name, photos);

                db.addStudent(new Student(id, name, personId));

                Platform.runLater(() -> {
                    registerStatus.setText("Registered successfully: " + name);
                    idField.clear();
                    nameField.clear();
                    refreshAll();
                });
            } catch (Exception e) {
                Platform.runLater(() -> registerStatus.setText("Registration failed: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> registerButton.setDisable(false));
            }
        }).start();
    }

    // ================= HISTORY + PROXY TABS =================

    private VBox buildHistoryTab() {
        historyTable.getColumns().add(makeColumn("Student ID", "studentId"));
        historyTable.getColumns().add(makeColumn("Name", "name"));
        historyTable.getColumns().add(makeColumn("Date", "date"));
        historyTable.getColumns().add(makeColumn("Time", "time"));

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshAll());

        VBox box = new VBox(10, refresh, historyTable);
        box.setPadding(new Insets(12));
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildProxyTab() {
        TableColumn<ProxyLog, String> reasonColumn = makeColumn("Reason", "reason");
        reasonColumn.setPrefWidth(450);

        proxyTable.getColumns().add(makeColumn("Date & Time", "dateTime"));
        proxyTable.getColumns().add(makeColumn("Student ID", "studentId"));
        proxyTable.getColumns().add(reasonColumn);
        proxyTable.setPlaceholder(new Label("No proxy attempts found. 👍"));

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshAll());

        VBox box = new VBox(10, refresh, proxyTable);
        box.setPadding(new Insets(12));
        VBox.setVgrow(proxyTable, Priority.ALWAYS);
        return box;
    }

    // ================= SMALL HELPERS =================

    // Reloads everything from the database
    private void refreshAll() {
        try {
            List<AttendanceRecord> today = db.getAttendance(true);
            todayTable.setItems(FXCollections.observableArrayList(today));
            historyTable.setItems(FXCollections.observableArrayList(db.getAttendance(false)));
            proxyTable.setItems(FXCollections.observableArrayList(db.getProxyLogs()));

            statsLabel.setText("Registered students: " + db.countStudents()
                    + "   |   Present today: " + today.size());
        } catch (Exception e) {
            statusLabel.setText("Could not load data: " + e.getMessage());
        }
    }

    // Makes one table column that shows the value of a getter (e.g. "name" -> getName())
    private <T> TableColumn<T, String> makeColumn(String title, String property) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(160);
        return column;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
