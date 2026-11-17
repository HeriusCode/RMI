package com.rmi.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ClientConfig {
    private static final Path EXTERNAL_FILE = Path.of("client.properties");
    private final Properties properties;

    private ClientConfig(Properties properties) {
        this.properties = properties;
    }

    public static ClientConfig load() throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(EXTERNAL_FILE)) {
            try (InputStream input = Files.newInputStream(EXTERNAL_FILE)) {
                properties.load(input);
            }
        } else {
            try (InputStream input = ClientConfig.class.getResourceAsStream("/client.properties")) {
                if (input == null) throw new IOException("Không tìm thấy client.properties");
                properties.load(input);
            }
        }
        return new ClientConfig(properties);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int port(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public void update(String server1Host, int server1Port, String server2Host, int server2Port)
            throws IOException {
        properties.setProperty("server1.host", server1Host);
        properties.setProperty("server1.port", String.valueOf(server1Port));
        properties.setProperty("server2.host", server2Host);
        properties.setProperty("server2.port", String.valueOf(server2Port));
        try (OutputStream output = Files.newOutputStream(EXTERNAL_FILE)) {
            properties.store(output, "RMI JavaFX client");
        }
    }
}
