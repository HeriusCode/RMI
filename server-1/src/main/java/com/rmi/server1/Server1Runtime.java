package com.rmi.server1;

import com.rmi.common.remote.ServiceNames;
import com.rmi.server1.config.ServerConfig;
import com.rmi.server1.database.DatabaseConnection;
import com.rmi.server1.service.ReplicaServiceImpl;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;

public final class Server1Runtime implements AutoCloseable {
    private final ServerConfig config;
    private final DatabaseConnection database;
    private final ReplicaServiceImpl service;
    private final Registry registry;
    private final boolean ownsRmi;

    private Server1Runtime(ServerConfig config, DatabaseConnection database,
                           ReplicaServiceImpl service, Registry registry, boolean ownsRmi) {
        this.config = config;
        this.database = database;
        this.service = service;
        this.registry = registry;
        this.ownsRmi = ownsRmi;
    }

    public static Server1Runtime start(ServerConfig config) throws Exception {
        config.validate();
        DatabaseConnection database = new DatabaseConnection(config);
        database.verify();
        String host = config.required("rmi.host");
        int registryPort = config.port("rmi.port");
        int servicePort = config.port("rmi.servicePort");
        System.setProperty("java.rmi.server.hostname", host);
        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(registryPort);
        } catch (ExportException portInUse) {
            Registry existing = LocateRegistry.getRegistry("127.0.0.1", registryPort);
            try {
                existing.lookup(ServiceNames.REPLICA);
                System.out.printf("Server 1 da chay tren cong %d; dashboard dang ket noi vao phien hien co.%n",
                        registryPort);
                return new Server1Runtime(config, database, null, existing, false);
            } catch (Exception notOurRegistry) {
                throw new IllegalStateException("Cong " + registryPort
                        + " dang bi chuong trinh khac chiem, khong tim thay dich vu "
                        + ServiceNames.REPLICA + ".", portInUse);
            }
        }

        ReplicaServiceImpl service = new ReplicaServiceImpl(database);
        try {
            registry.rebind(ServiceNames.REPLICA,
                    UnicastRemoteObject.exportObject(service, servicePort));
        } catch (Exception error) {
            try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) {}
            throw error;
        }
        System.out.printf("Server 1 san sang tai %s:%d (service %d)%n",
                host, registryPort, servicePort);
        return new Server1Runtime(config, database, service, registry, true);
    }

    public ServerConfig config() { return config; }
    public DatabaseConnection database() { return database; }

    @Override
    public void close() {
        if (!ownsRmi) return;
        try { registry.unbind(ServiceNames.REPLICA); } catch (Exception ignored) {}
        if (service != null) {
            try { UnicastRemoteObject.unexportObject(service, true); } catch (Exception ignored) {}
        }
        try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) {}
    }
}
