package com.rmi.server1.ui;

import com.rmi.server1.Server1Runtime;
import com.rmi.server1.config.ServerConfig;
import com.rmi.server1.database.DatabaseConnection;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class Server1Application extends Application {
    private Server1Runtime runtime;
    private ServerConfig config;
    private DatabaseConnection database;

    public static void launchApplication(String[] args) { launch(args); }

    @Override
    public void init() throws Exception {
        String path = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        config = ServerConfig.load(path);
        database = new DatabaseConnection(config);
    }

    @Override
    public void start(Stage stage) throws Exception {
        String startupError = null;
        try { runtime = Server1Runtime.start(config); }
        catch (Exception error) { startupError = rootMessage(error); }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/server1-view.fxml"));
        loader.setControllerFactory(type -> {
            if (type == Server1Controller.class) {
                return new Server1Controller(database, config, this::restartRuntime);
            }
            try { return type.getDeclaredConstructor().newInstance(); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
        Scene scene = new Scene(loader.load(), 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/css/server-admin.css").toExternalForm());
        stage.setTitle("Server 1 - Quan ly Database dich");
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
        Server1Controller controller = loader.getController();
        controller.updateRuntimeStatus(runtime != null,
                runtime != null ? "RMI và PostgreSQL đang hoạt động" : startupError);
    }

    @Override
    public void stop() { if (runtime != null) runtime.close(); }

    private synchronized String restartRuntime(String host, int registryPort, int servicePort)
            throws Exception {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
        config.updateNetwork(host, registryPort, servicePort);
        config.validate();
        config.save(Path.of("server1.properties"));
        runtime = Server1Runtime.start(config);
        return "Server 1 đang chạy tại " + host + ":" + registryPort
                + " (service " + servicePort + ")";
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
