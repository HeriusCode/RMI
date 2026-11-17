package com.rmi.server1.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class ServerConfig {
    private final Properties properties;
    private final String source;

    private ServerConfig(Properties properties, String source) {
        this.properties = properties;
        this.source = source;
    }

    public static ServerConfig load(String explicitPath) throws IOException {
        Properties properties = new Properties();
        Path configPath = findConfigPath(explicitPath);
        String source;

        if (configPath != null) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            }
            source = configPath.toAbsolutePath().normalize().toString();
        } else {
            try (InputStream input = ServerConfig.class.getResourceAsStream("/server1.properties")) {
                if (input == null) throw new IOException("Khong tim thay server1.properties");
                properties.load(input);
            }
            source = "classpath:/server1.properties (cau hinh mau)";
        }

        Map<String, String> environmentOverrides = new LinkedHashMap<>();
        environmentOverrides.put("RMI_DB_URL", "db.url");
        environmentOverrides.put("RMI_DB_USER", "db.user");
        environmentOverrides.put("RMI_DB_PASSWORD", "db.password");
        environmentOverrides.put("RMI_HOST", "rmi.host");
        environmentOverrides.put("RMI_REGISTRY_PORT", "rmi.port");
        environmentOverrides.put("RMI_SERVICE_PORT", "rmi.servicePort");
        applyEnvironment(properties, environmentOverrides);

        return new ServerConfig(properties, source);
    }

    private static Path findConfigPath(String explicitPath) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            return Path.of(explicitPath);
        }

        Path[] candidates = {
                Path.of("server1.properties"),
                Path.of("server-1", "server1.properties")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static void applyEnvironment(Properties properties, Map<String, String> overrides) {
        overrides.forEach((environmentName, propertyName) -> {
            String value = System.getenv(environmentName);
            if (value != null && !value.isBlank()) {
                properties.setProperty(propertyName, value.trim());
            }
        });
    }

    public void validate() {
        rejectPlaceholder("db.password", "CHANGE_ME",
                "Hay dat mat khau that trong server1.properties hoac bien RMI_DB_PASSWORD.");
        rejectPlaceholder("db.password", "NHAP_MAT_KHAU_POSTGRESQL",
                "Hay thay dong db.password trong server1.properties bang mat khau cua role rmi_user.");
        rejectPlaceholder("rmi.host", "SERVER1_IP",
                "Hay dat IP LAN cua May that 1 trong server1.properties hoac bien RMI_HOST.");
        port("rmi.port");
        port("rmi.servicePort");
    }

    private void rejectPlaceholder(String key, String placeholder, String instruction) {
        if (placeholder.equalsIgnoreCase(required(key))) {
            throw new IllegalArgumentException(
                    "Cau hinh " + key + " van la gia tri mau '" + placeholder + "'. "
                            + instruction + " Nguon cau hinh: " + source);
        }
    }

    public String source() {
        return source;
    }

    public void updateNetwork(String host, int registryPort, int servicePort) {
        properties.setProperty("rmi.host", host.trim());
        properties.setProperty("rmi.port", String.valueOf(registryPort));
        properties.setProperty("rmi.servicePort", String.valueOf(servicePort));
    }

    public void save(Path path) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "RMI Server 1 configuration");
        }
    }

    public String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Thieu cau hinh: " + key);
        }
        return value.trim();
    }

    public int port(String key) {
        try {
            int value = Integer.parseInt(required(key));
            if (value < 1 || value > 65535) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Cong " + key + " phai la so tu 1 den 65535. Nguon cau hinh: " + source,
                    exception);
        }
    }
}
