package com.rmi.server2;

import com.rmi.common.remote.ServiceNames;
import com.rmi.common.remote.PrimaryServiceRemote;
import com.rmi.server2.config.ServerConfig;
import com.rmi.server2.database.DatabaseConnection;
import com.rmi.server2.service.PrimaryServiceImpl;
import com.rmi.server2.service.SyncDispatcher;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Server2Runtime implements AutoCloseable {
    private final ServerConfig config;
    private final DatabaseConnection database;
    private final PrimaryServiceRemote service;
    private final PrimaryServiceImpl localService;
    private final Registry registry;
    private final ScheduledExecutorService worker;
    private final boolean ownsRmi;

    private Server2Runtime(ServerConfig config, DatabaseConnection database,
                           PrimaryServiceRemote service, PrimaryServiceImpl localService,
                           Registry registry, ScheduledExecutorService worker, boolean ownsRmi) {
        this.config = config;
        this.database = database;
        this.service = service;
        this.localService = localService;
        this.registry = registry;
        this.worker = worker;
        this.ownsRmi = ownsRmi;
    }

    public static Server2Runtime start(ServerConfig config) throws Exception {
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
                PrimaryServiceRemote remote = (PrimaryServiceRemote) existing.lookup(ServiceNames.PRIMARY);
                System.out.printf("Server 2 da chay tren cong %d; dashboard dang ket noi vao phien hien co.%n",
                        registryPort);
                return new Server2Runtime(config, database, remote, null,
                        existing, null, false);
            } catch (Exception notOurRegistry) {
                throw new IllegalStateException("Cong " + registryPort
                        + " dang bi chuong trinh khac chiem, khong tim thay dich vu "
                        + ServiceNames.PRIMARY + ".", portInUse);
            }
        }

        SyncDispatcher dispatcher = new SyncDispatcher(database, config);
        PrimaryServiceImpl service = new PrimaryServiceImpl(database, dispatcher);
        try {
            registry.rebind(ServiceNames.PRIMARY,
                    UnicastRemoteObject.exportObject(service, servicePort));
        } catch (Exception error) {
            try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) {}
            throw error;
        }
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "server2-sync-worker");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(dispatcher, 0, 1, TimeUnit.SECONDS);
        System.out.printf("Server 2 san sang tai %s:%d (service %d)%n",
                host, registryPort, servicePort);
        return new Server2Runtime(config, database, service, service,
                registry, worker, true);
    }

    public ServerConfig config() { return config; }
    public DatabaseConnection database() { return database; }
    public PrimaryServiceRemote service() { return service; }

    @Override
    public void close() {
        if (!ownsRmi) return;
        if (worker != null) worker.shutdownNow();
        try { registry.unbind(ServiceNames.PRIMARY); } catch (Exception ignored) {}
        if (localService != null) {
            try { UnicastRemoteObject.unexportObject(localService, true); } catch (Exception ignored) {}
        }
        try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) {}
    }
}
