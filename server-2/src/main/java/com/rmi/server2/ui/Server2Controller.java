package com.rmi.server2.ui;

import com.rmi.common.dto.SyncResult;
import com.rmi.common.model.Student;
import com.rmi.common.model.SyncLogEntry;
import com.rmi.common.remote.PrimaryServiceRemote;
import com.rmi.server2.config.ServerConfig;
import com.rmi.server2.database.DatabaseConnection;
import com.rmi.server2.repository.OutboxRepository;
import com.rmi.server2.repository.StudentRepository;
import com.rmi.server2.repository.SyncLogRepository;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

public final class Server2Controller {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FunctionalInterface
    public interface RuntimeRestarter {
        String restart(String host, int registryPort, int servicePort) throws Exception;
    }

    private final DatabaseConnection database;
    private final ServerConfig config;
    private final RuntimeRestarter restarter;
    private final Supplier<PrimaryServiceRemote> serviceSupplier;
    private final StudentRepository students = new StudentRepository();
    private final SyncLogRepository logs = new SyncLogRepository();
    private final OutboxRepository outbox = new OutboxRepository();

    @FXML private Label statusLabel;
    @FXML private Label endpointLabel;
    @FXML private Label countLabel;
    @FXML private Label pendingLabel;
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

    public Server2Controller(DatabaseConnection database, ServerConfig config,
                            RuntimeRestarter restarter, Supplier<PrimaryServiceRemote> serviceSupplier) {
        this.database = database;
        this.config = config;
        this.restarter = restarter;
        this.serviceSupplier = serviceSupplier;
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
            String host = required(hostField, "IP / Hostname Server 2");
            int port = parsePort(portField.getText(), "Cổng Server 2");
            int servicePort = port == 2099 ? 2100 : port + 1;

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
    @FXML private void onSearch() { runAsync(() -> load(searchField.getText()), this::display, "Đang tìm Database 2…"); }

    @FXML
    private void onSave() {
        try {
            Student student = new Student(required(studentIdField, "Mã sinh viên"),
                    required(fullNameField, "Họ và tên"), required(classNameField, "Lớp"), 0, false);
            runAsync(() -> serviceSupplier.get().saveStudent(student), this::afterWrite,
                    "Đang lưu Database 2 và đồng bộ…");
        } catch (IllegalArgumentException error) { showError(error.getMessage()); }
    }

    @FXML
    private void onDelete() {
        try {
            String id = required(studentIdField, "Mã sinh viên");
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Xóa sinh viên " + id + " khỏi hai database?", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Xác nhận xóa");
            if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                runAsync(() -> serviceSupplier.get().deleteStudent(id), this::afterWrite, "Đang xóa và đồng bộ…");
            }
        } catch (IllegalArgumentException error) { showError(error.getMessage()); }
    }

    @FXML private void onClear() {
        studentIdField.clear(); fullNameField.clear(); classNameField.clear();
        studentTable.getSelectionModel().clearSelection();
    }

    private void updateEndpointLabel(String host, int port) {
        endpointLabel.setText(host + ":" + port);
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

    private void afterWrite(SyncResult result) { messageLabel.setText(result.message()); refresh(); }
    private void refresh() { runAsync(() -> load(searchField.getText()), this::display, "Đang tải Database 2 và nhật ký…"); }

    private Dashboard load(String keyword) throws Exception {
        try (Connection connection = database.open()) {
            return new Dashboard(students.search(connection, keyword), logs.recent(connection, 100),
                    outbox.pendingCount(connection));
        }
    }

    private void display(Dashboard dashboard) {
        studentTable.setItems(FXCollections.observableArrayList(dashboard.students()));
        logTable.setItems(FXCollections.observableArrayList(dashboard.logs()));
        countLabel.setText(dashboard.students().size() + " SINH VIÊN");
        pendingLabel.setText(dashboard.pending() + " CHỜ ĐỒNG BỘ");
        pendingLabel.getStyleClass().removeAll("status-ok", "status-warn");
        pendingLabel.getStyleClass().add(dashboard.pending() == 0 ? "status-ok" : "status-warn");
        messageLabel.setText("Đã cập nhật dữ liệu Database 2");
    }

    private void fill(Student student) {
        studentIdField.setText(student.studentId()); fullNameField.setText(student.fullName());
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
        Thread thread = new Thread(task, "server2-ui-worker"); thread.setDaemon(true); thread.start();
    }

    private void setBusy(boolean value) {
        progressIndicator.setVisible(value);
        saveButton.setDisable(value);
        deleteButton.setDisable(value);
        if (applyNetworkButton != null) applyNetworkButton.setDisable(value);
    }

    private void setOnline(boolean online, String text) {
        statusLabel.setText(text); statusLabel.getStyleClass().removeAll("status-ok", "status-error");
        statusLabel.getStyleClass().add(online ? "status-ok" : "status-error");
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("Server 2");
            alert.setHeaderText("Không thể thực hiện"); alert.setContentText(message); alert.showAndWait();
            messageLabel.setText(message);
        });
    }

    private String rootMessage(Throwable error) {
        Throwable current = error; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Dashboard(List<Student> students, List<SyncLogEntry> logs, int pending) {}
}
