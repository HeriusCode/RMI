package com.rmi.client.controller;

import com.rmi.client.config.ClientConfig;
import com.rmi.client.service.ComparisonResult;
import com.rmi.client.service.AuditResult;
import com.rmi.client.service.HealthSnapshot;
import com.rmi.client.service.OperationResult;
import com.rmi.client.service.RmiClientService;
import com.rmi.common.model.Student;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

public final class MainController {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private TextField server1HostField;
    @FXML private TextField server1PortField;
    @FXML private TextField server2HostField;
    @FXML private TextField server2PortField;
    @FXML private TextField studentIdField;
    @FXML private TextField fullNameField;
    @FXML private TextField classNameField;
    @FXML private TextField searchField;
    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, String> idColumn;
    @FXML private TableColumn<Student, String> nameColumn;
    @FXML private TableColumn<Student, String> classColumn;
    @FXML private TableColumn<Student, String> updatedColumn;
    @FXML private Label server1StatusLabel;
    @FXML private Label server2StatusLabel;
    @FXML private Label server1DataLabel;
    @FXML private Label server2DataLabel;
    @FXML private Label syncStatusLabel;
    @FXML private Label messageLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button saveServer1Button;
    @FXML private Button saveServer2Button;
    @FXML private Button deleteServer1Button;
    @FXML private Button deleteServer2Button;
    @FXML private Button compareButton;

    private final RmiClientService service = new RmiClientService();
    private ClientConfig config;

    @FXML
    private void initialize() {
        configureTable();
        try {
            config = ClientConfig.load();
            server1HostField.setText(config.get("server1.host", "SERVER1_IP"));
            server1PortField.setText(config.get("server1.port", "1099"));
            server2HostField.setText(config.get("server2.host", "SERVER2_IP"));
            server2PortField.setText(config.get("server2.port", "1099"));
            applyRuntimeConfiguration();
            checkHealth();
            onSearch();
        } catch (Exception error) {
            showError("Không thể đọc cấu hình", rootMessage(error));
        }
    }

    private void configureTable() {
        idColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().studentId()));
        nameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().fullName()));
        classColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().className()));
        updatedColumn.setCellValueFactory(data ->
                new SimpleStringProperty(formatTime(data.getValue().updatedAt())));
        studentTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, student) -> {
                    if (student != null) {
                        fillForm(student);
                        compareStudent(student.studentId());
                    }
                });
    }

    @FXML
    private void onApplyConfiguration() {
        try {
            String server1Host = required(server1HostField, "IP Server 1");
            String server2Host = required(server2HostField, "IP Server 2");
            int server1Port = parsePort(server1PortField, "Cổng Server 1");
            int server2Port = parsePort(server2PortField, "Cổng Server 2");
            config.update(server1Host, server1Port, server2Host, server2Port);
            service.configure(server1Host, server1Port, server2Host, server2Port);
            messageLabel.setText("Đã lưu client.properties và áp dụng cấu hình mới");
            checkHealth();
        } catch (IllegalArgumentException error) {
            showError("Cấu hình chưa hợp lệ", error.getMessage());
        } catch (Exception error) {
            showError("Không thể lưu cấu hình", rootMessage(error));
        }
    }

    @FXML
    private void onSaveServer1() {
        try {
            Student student = formStudent();
            runAsync("Đang lưu trực tiếp trên Server 1…",
                    () -> service.saveOnServer1(student), this::displayOperation);
        } catch (IllegalArgumentException error) {
            showError("Dữ liệu chưa hợp lệ", error.getMessage());
        }
    }

    @FXML
    private void onSaveServer2() {
        try {
            Student student = formStudent();
            runAsync("Đang lưu Server 2 và chờ đồng bộ…",
                    () -> service.saveAndWait(student), this::displayOperation);
        } catch (IllegalArgumentException error) {
            showError("Dữ liệu chưa hợp lệ", error.getMessage());
        }
    }

    @FXML
    private void onDeleteServer1() {
        deleteFromServer(true);
    }

    @FXML
    private void onDeleteServer2() {
        deleteFromServer(false);
    }

    private void deleteFromServer(boolean server1) {
        String studentId;
        try {
            studentId = required(studentIdField, "Mã sinh viên");
        } catch (IllegalArgumentException error) {
            showError("Dữ liệu chưa hợp lệ", error.getMessage());
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa sinh viên " + studentId + " trên "
                        + (server1 ? "Server 1" : "Server 2 và đồng bộ sang Server 1") + "?",
                ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("Xác nhận xóa");
        if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            runAsync(server1 ? "Đang xóa trực tiếp trên Server 1…" : "Đang xóa và chờ đồng bộ…",
                    () -> server1 ? service.deleteOnServer1(studentId)
                            : service.deleteAndWait(studentId), this::displayOperation);
        }
    }

    private Student formStudent() {
        return new Student(required(studentIdField, "Mã sinh viên"),
                required(fullNameField, "Họ và tên"),
                required(classNameField, "Lớp"), 0, false);
    }

    @FXML
    private void onSearch() {
        runAsync("Đang tải danh sách từ Server 1 và Server 2…",
                () -> service.searchAll(searchField.getText()),
                students -> {
                    studentTable.setItems(FXCollections.observableArrayList(students));
                    messageLabel.setText("Tìm thấy " + students.size() + " sinh viên từ hai server");
                    if (!students.isEmpty()) {
                        studentTable.getSelectionModel().selectFirst();
                    } else {
                        server1DataLabel.setText("Không tìm thấy sinh viên");
                        server2DataLabel.setText("Không tìm thấy sinh viên");
                    }
                });
    }

    @FXML
    private void onCompare() {
        try {
            String studentId = required(studentIdField, "Mã sinh viên");
            runAsync("Đang so sánh hai database…",
                    () -> service.compare(studentId), this::displayComparison);
        } catch (IllegalArgumentException error) {
            showError("Thiếu mã sinh viên", error.getMessage());
        }
    }

    @FXML
    private void onAudit() {
        runAsync("Đang đối soát toàn bộ hai database…",
                service::auditAll, this::displayAudit);
    }

    @FXML
    private void onClear() {
        studentIdField.clear();
        fullNameField.clear();
        classNameField.clear();
        studentTable.getSelectionModel().clearSelection();
        server1DataLabel.setText("Chưa kiểm tra bản ghi");
        server2DataLabel.setText("Chưa kiểm tra bản ghi");
        setBadge(syncStatusLabel, "CHƯA KIỂM TRA", "status-neutral");
    }

    @FXML
    private void onRefresh() {
        checkHealth();
        onSearch();
    }

    private void checkHealth() {
        runAsync("Đang kiểm tra kết nối RMI và PostgreSQL…",
                service::health, this::displayHealth);
    }

    private void applyRuntimeConfiguration() {
        service.configure(server1HostField.getText().trim(),
                parsePort(server1PortField, "Cổng Server 1"),
                server2HostField.getText().trim(),
                parsePort(server2PortField, "Cổng Server 2"));
    }

    private void displayOperation(OperationResult operation) {
        messageLabel.setText(operation.result().message() + " • Event " + operation.result().eventId());
        displayComparison(operation.comparison());
        onSearch();
    }

    private void displayComparison(ComparisonResult comparison) {
        server2DataLabel.setText(formatStudent(comparison.server2(), comparison.server2Error()));
        server1DataLabel.setText(formatStudent(comparison.server1(), comparison.server1Error()));
        if (comparison.synchronizedData()) {
            setBadge(syncStatusLabel, "ĐÃ ĐỒNG BỘ", "status-ok");
            messageLabel.setText("Hai database có cùng dữ liệu");
        } else if (comparison.server1Error() != null || comparison.server2Error() != null) {
            setBadge(syncStatusLabel, "LỖI KẾT NỐI", "status-error");
            messageLabel.setText("Không thể đọc đầy đủ hai server");
        } else {
            setBadge(syncStatusLabel, "CHƯA ĐỒNG BỘ", "status-warn");
            messageLabel.setText("Dữ liệu đang chờ tiến trình đồng bộ");
        }
        if (comparison.server2() != null && !comparison.server2().deleted()) {
            fillForm(comparison.server2());
        }
    }

    private void compareStudent(String studentId) {
        runAsync("Đang tải dữ liệu bản ghi từ hai server…",
                () -> service.compare(studentId), this::displayComparison);
    }

    private void displayHealth(HealthSnapshot health) {
        setBadge(server1StatusLabel,
                health.server1().available() ? "SERVER 1 ONLINE" : "SERVER 1 OFFLINE",
                health.server1().available() ? "status-ok" : "status-error");
        setBadge(server2StatusLabel,
                health.server2().available() ? "SERVER 2 ONLINE" : "SERVER 2 OFFLINE",
                health.server2().available() ? "status-ok" : "status-error");
        messageLabel.setText("Server 1: " + health.server1().message()
                + "  •  Server 2: " + health.server2().message());
    }

    private void displayAudit(AuditResult audit) {
        if (audit.consistent()) {
            setBadge(syncStatusLabel, "TOÀN BỘ ĐÃ ĐỒNG BỘ", "status-ok");
            messageLabel.setText("Đối soát thành công: Server 2 có " + audit.server2Count()
                    + " và Server 1 có " + audit.server1Count() + " sinh viên");
        } else {
            setBadge(syncStatusLabel, "PHÁT HIỆN SAI LỆCH", "status-warn");
            String ids = String.join(", ", audit.inconsistentStudentIds().stream().limit(10).toList());
            messageLabel.setText("Có " + audit.inconsistentStudentIds().size()
                    + " mã chưa đồng nhất: " + ids);
        }
    }

    private void fillForm(Student student) {
        studentIdField.setText(student.studentId());
        fullNameField.setText(student.fullName() == null ? "" : student.fullName());
        classNameField.setText(student.className() == null ? "" : student.className());
    }

    private String formatStudent(Student student, String error) {
        if (error != null) return "Lỗi: " + error;
        if (student == null) return "Không tìm thấy sinh viên";
        return "Mã: " + student.studentId()
                + "\nTên: " + (student.fullName() == null ? "(trống)" : student.fullName())
                + "\nLớp: " + (student.className() == null ? "(trống)" : student.className())
                + "\nCập nhật: " + formatTime(student.updatedAt())
                + "\nĐã xóa: " + (student.deleted() ? "Có" : "Không");
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    private String required(TextField field, String label) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(label + " không được để trống");
        return value;
    }

    private int parsePort(TextField field, String label) {
        try {
            int port = Integer.parseInt(field.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " phải nằm trong khoảng 1–65535");
        }
    }

    private <T> void runAsync(String message, Callable<T> operation, Consumer<T> onSuccess) {
        setBusy(true);
        messageLabel.setText(message);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showError("Không thể thực hiện", rootMessage(task.getException()));
        });
        Thread worker = new Thread(task, "rmi-client-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        progressIndicator.setVisible(busy);
        saveServer1Button.setDisable(busy);
        saveServer2Button.setDisable(busy);
        deleteServer1Button.setDisable(busy);
        deleteServer2Button.setDisable(busy);
        compareButton.setDisable(busy);
    }

    private void setBadge(Label label, String text, String styleClass) {
        label.setText(text);
        label.getStyleClass().removeAll("status-ok", "status-error", "status-warn", "status-neutral");
        label.getStyleClass().add(styleClass);
    }

    private void showError(String header, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("RMI Student Synchronization");
            alert.setHeaderText(header);
            alert.setContentText(message);
            alert.showAndWait();
            messageLabel.setText(message);
        });
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
