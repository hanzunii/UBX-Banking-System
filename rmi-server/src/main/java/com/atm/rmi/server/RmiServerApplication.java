package com.atm.rmi.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.atm.rmi.common.AuthServiceRemote;
import com.atm.rmi.common.AccountServiceRemote;
import com.atm.rmi.common.TransactionServiceRemote;
import com.atm.rmi.common.AdminServiceRemote;

import jakarta.annotation.PreDestroy;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

@SpringBootApplication(scanBasePackages = "com.atm.rmi")
@EnableJpaRepositories(basePackages = "com.atm.rmi")
@EntityScan(basePackages = "com.atm.rmi")
public class RmiServerApplication {

    @Autowired
    private AuthServiceRemote authServiceRemote;

    @Autowired
    private AccountServiceRemote accountServiceRemote;

    @Autowired
    private TransactionServiceRemote transactionServiceRemote;

    @Autowired
    private AdminServiceRemote adminServiceRemote;

    @Value("${rmi.port:1099}")
    private int rmiPort;

    private Registry registry;

    public static void main(String[] args) {
        SpringApplication.run(RmiServerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRmiRegistry() {
        try {
            registry = LocateRegistry.createRegistry(rmiPort);

            UnicastRemoteObject.exportObject(authServiceRemote, 0);
            UnicastRemoteObject.exportObject(accountServiceRemote, 0);
            UnicastRemoteObject.exportObject(transactionServiceRemote, 0);
            UnicastRemoteObject.exportObject(adminServiceRemote, 0);

            registry.rebind("AuthService", authServiceRemote);
            registry.rebind("AccountService", accountServiceRemote);
            registry.rebind("TransactionService", transactionServiceRemote);
            registry.rebind("AdminService", adminServiceRemote);

            System.out.println("=== RMI Registry started on port " + rmiPort + " ===");
            System.out.println("Bound services: AuthService, AccountService, TransactionService, AdminService");
        } catch (Exception e) {
            System.err.println("Failed to start RMI Registry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (registry != null) {
            try {
                UnicastRemoteObject.unexportObject(authServiceRemote, true);
                UnicastRemoteObject.unexportObject(accountServiceRemote, true);
                UnicastRemoteObject.unexportObject(transactionServiceRemote, true);
                UnicastRemoteObject.unexportObject(adminServiceRemote, true);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
