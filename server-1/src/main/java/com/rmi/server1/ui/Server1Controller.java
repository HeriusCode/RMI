package com.rmi.server1.ui;

import com.rmi.common.model.Student;
import com.rmi.common.model.SyncLogEntry;
import com.rmi.common.validation.StudentValidator;
import com.rmi.server1.config.ServerConfig;
import com.rmi.server1.database.DatabaseConnection;
import com.rmi.server1.repository.StudentRepository;
import com.rmi.server1.repository.SyncLogRepository;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public final class Server1Controller {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FunctionalInterface
    public interface RuntimeRestarter {
        String restart(String host, int registryPort, int servicePort) throws Exception;
    }

    private final DatabaseConnection database;
    private final ServerConfig config;
    private final RuntimeRestarter restarter;
    private final StudentRepository students = new StudentRepository();
    private final SyncLogRepository logs = new SyncLogRepository();

    @FXML private Label statusLabel;
    @FXML private Label endpointLabel;
    @FXML private Label countLabel;
    @FXML private Label messageLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Button applyNetworkButton;
    @FXML private TextField studentIdField;
    @FXML private TextField fullNameField;
    @FXML private TextField classNameField;
    @FXML private TextField searchField;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;
    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> idColumn;
    @FXML private TableColumn<Student, String> nameColumn;
    @FXML private TableColumn<Student, String> classColumn;
    @FXML private TableColumn<Student, String> updatedColumn;
    @FXML private TableView<SyncLogEntry> logTable;
    @FXML private TableColumn<SyncLogEntry, String> logTimeColumn;
    @FXML private TableColumn<SyncLogEntry, String> logStudentColumn;
    @FXML private TableColumn<SyncLogEntry, String> logStatusColumn;
    @FXML private TableColumn<SyncLogEntry, String> logMessageColumn;

    public Server1Controller(DatabaseConnection database, ServerConfig config, RuntimeRestarter restarter) {
        this.database = database;
        this.config = config;
        this.restarter = restarter;
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().studentId()));
        nameColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fullName()));
        classColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().className()));
        updatedColumn.setCellValueFactory(v -> new SimpleStringProperty(formatTime(v.getValue().updatedAt())));
        logTimeColumn.setCellValueFactory(v -> new SimpleStringProperty(formatTime(v.getValue().createdAt())));
        logStudentColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().studentId()));
        logStatusColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status()));
        logMessageColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().message()));
        studentTable.getSelectionModel().selectedItemProperty().addListener((o, oldValue, value) -> {
            if (value != null) fill(value);
        });

        hostField.setText(config.required("rmi.host"));
        portField.setText(String.valueOf(config.port("rmi.port")));

        updateEndpointLabel(config.required("rmi.host"), config.port("rmi.port"));
        refresh();
    }

    public void updateRuntimeStatus(boolean online, String message) {
        setOnline(online, online ? "RMI VÀ POSTGRESQL ĐANG HOẠT ĐỘNG" : "LỖI KHỞI ĐỘNG RMI");
        if (message != null) {
            messageLabel.setText(message);
        }
    }

    @FXML
    private void onApplyNetwork() {
        try {
            String host = required(hostField, "IP / Hostname Server 1");
            int port = parsePort(portField.getText(), "Cổng Server 1");
            int servicePort = port == 1099 ? 1100 : port + 1;

            runAsync(() -> restarter.restart(host, port, servicePort), result -> {
                updateEndpointLabel(host, port);
                setOnline(true, "RMI VÀ POSTGRESQL ĐANG HOẠT ĐỘNG");
                messageLabel.setText(result);
                refresh();
            }, "Đang áp dụng cấu hình và khởi động lại RMI…");
        } catch (IllegalArgumentException error) {
            showError(error.getMessage());
        }
    }

    @FXML private void onRefresh() { refresh(); }

    @FXML
    private void onSearch() {
        runAsync(() -> load(searchField.getText()), this::display,
                "Đang tìm dữ liệu trên Database 1…");
    }

    @FXML
    private void onSave() {
        try {
            Student student = new Student(required(studentIdField, "Mã sinh viên"),
                    required(fullNameField, "Họ và tên"), required(classNameField, "Lớp"), 0, false);
            runAsync(() -> saveLocal(student), this::afterWrite,
                    "Đang lưu trực tiếp Database 1…");
        } catch (IllegalArgumentException error) {
            showError(error.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        try {
            String id = required(studentIdField, "Mã sinh viên");
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Xóa sinh viên " + id + " khỏi Database 1?", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Xác nhận xóa");
            if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                runAsync(() -> deleteLocal(id), this::afterWrite,
                        "Đang xóa trực tiếp trên Database 1…");
            }
        } catch (IllegalArgumentException error) {
            showError(error.getMessage());
        }
    }

    @FXML private void onClear() {
        studentIdField.clear(); fullNameField.clear(); classNameField.clear();
        studentTable.getSelectionModel().clearSelection();
    }

    private void updateEndpointLabel(String host, int port) {
        endpointLabel.setText(host + ":" + port + "  •  Quản lý trực tiếp Database 1");
    }

    private int parsePort(String value, String label) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (Exception error) {
            throw new IllegalArgumentException(label + " phải là số từ 1 đến 65535");
        }
    }

    private void afterWrite(String message) {
        messageLabel.setText(message);
        refresh();
    }

    private String saveLocal(Student input) throws Exception {
        StudentValidator.validate(input);
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                long timestamp = Math.max(System.currentTimeMillis(),
                        students.latestTimestamp(connection) + 1);
                Student student = new Student(input.studentId().trim(), input.fullName().trim(),
                        input.className().trim(), timestamp, false);
                students.upsert(connection, student);
                logs.localSuccess(connection, UUID.randomUUID().toString(), student.studentId(),
                        "Quản trị cục bộ đã lưu trên Server 1");
                connection.commit();
                return "Đã lưu trực tiếp trên Database 1";
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private String deleteLocal(String studentId) throws Exception {
        StudentValidator.validateId(studentId);
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                Student current = students.findById(connection, studentId.trim());
                long timestamp = Math.max(System.currentTimeMillis(),
                        students.latestTimestamp(connection) + 1);
                Student tombstone = current == null
                        ? new Student(studentId.trim(), null, null, timestamp, true)
                        : current.asDeleted(timestamp);
                students.upsert(connection, tombstone);
                logs.localSuccess(connection, UUID.randomUUID().toString(), tombstone.studentId(),
                        "Quản trị cục bộ đã xóa trên Server 1");
                connection.commit();
                return "Đã xóa trực tiếp trên Database 1";
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private void refresh() {
        runAsync(() -> load(searchField.getText()), this::display,
                "Đang tải Database 1 và lịch sử đồng bộ…");
    }

    private Dashboard load(String keyword) throws Exception {
        try (Connection connection = database.open()) {
            return new Dashboard(students.search(connection, keyword), logs.recent(connection, 100));
        }
    }

    private void display(Dashboard dashboard) {
        studentTable.setItems(FXCollections.observableArrayList(dashboard.students()));
        logTable.setItems(FXCollections.observableArrayList(dashboard.logs()));
        countLabel.setText(dashboard.students().size() + " SINH VIÊN");
        messageLabel.setText("Đã cập nhật dữ liệu Database 1");
    }

    private void fill(Student student) {
        studentIdField.setText(student.studentId());
        fullNameField.setText(student.fullName());
        classNameField.setText(student.className());
    }

    private String required(TextField field, String label) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(label + " không được để trống");
        return value;
    }

    private String formatTime(long value) {
        return value <= 0 ? "" : Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(TIME);
    }

    private <T> void runAsync(Callable<T> action, Consumer<T> success, String message) {
        setBusy(true); messageLabel.setText(message);
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(e -> { setBusy(false); setOnline(true, "RMI VÀ POSTGRESQL ĐANG HOẠT ĐỘNG"); success.accept(task.getValue()); });
        task.setOnFailed(e -> { setBusy(false); setOnline(false, "CÓ LỖI KẾT NỐI"); showError(rootMessage(task.getException())); });
        Thread thread = new Thread(task, "server1-ui-worker");
        thread.setDaemon(true); thread.start();
    }

    private void setBusy(boolean value) {
        progressIndicator.setVisible(value);
        saveButton.setDisable(value);
        deleteButton.setDisable(value);
        if (applyNetworkButton != null) applyNetworkButton.setDisable(value);
    }

    private void setOnline(boolean online, String text) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("status-ok", "status-error");
        statusLabel.getStyleClass().add(online ? "status-ok" : "status-error");
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Server 1"); alert.setHeaderText("Không thể thực hiện"); alert.setContentText(message);
            alert.showAndWait(); messageLabel.setText(message);
        });
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Dashboard(List<Student> students, List<SyncLogEntry> logs) {}
}
