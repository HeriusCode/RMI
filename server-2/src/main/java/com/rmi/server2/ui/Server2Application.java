package com.rmi.server2.ui;

import com.rmi.server2.Server2Runtime;
import com.rmi.server2.config.ServerConfig;
import com.rmi.server2.database.DatabaseConnection;
import com.rmi.common.remote.PrimaryServiceRemote;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class Server2Application extends Application {
    private Server2Runtime runtime;
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
        try { runtime = Server2Runtime.start(config); }
        catch (Exception error) { startupError = rootMessage(error); }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/server2-view.fxml"));
        loader.setControllerFactory(type -> {
            if (type == Server2Controller.class) {
                return new Server2Controller(database, config, this::restartRuntime,
                        this::currentService);
            }
            try { return type.getDeclaredConstructor().newInstance(); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
        Scene scene = new Scene(loader.load(), 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/css/server-admin.css").toExternalForm());
        stage.setTitle("Server 2 - Quan ly Database nguon");
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
        Server2Controller controller = loader.getController();
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
        config.save(Path.of("server2.properties"));
        runtime = Server2Runtime.start(config);
        return "Server 2 đang chạy tại " + host + ":" + registryPort
                + " (service " + servicePort + ")";
    }

    private synchronized PrimaryServiceRemote currentService() {
        if (runtime == null) {
            throw new IllegalStateException("RMI Server 2 chưa được khởi động");
        }
        return runtime.service();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
