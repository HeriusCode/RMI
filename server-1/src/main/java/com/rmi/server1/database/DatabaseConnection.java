package com.rmi.server1.database;

import com.rmi.server1.config.ServerConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private final String url;
    private final String user;
    private final String password;

    public DatabaseConnection(ServerConfig config) {
        url = config.required("db.url");
        user = config.required("db.user");
        password = config.required("db.password");
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void verify() throws SQLException {
        try (Connection connection = open();
             var statement = connection.prepareStatement("SELECT 1")) {
            statement.execute();
        }
    }
}
