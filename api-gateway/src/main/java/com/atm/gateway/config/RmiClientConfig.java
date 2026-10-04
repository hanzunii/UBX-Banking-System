package com.atm.gateway.config;

import com.atm.rmi.common.AccountServiceRemote;
import com.atm.rmi.common.AdminServiceRemote;
import com.atm.rmi.common.AuthServiceRemote;
import com.atm.rmi.common.TransactionServiceRemote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

@Configuration
public class RmiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RmiClientConfig.class);

    @Bean
    public Registry rmiRegistry(@Value("${rmi.host:localhost}") String host,
                                @Value("${rmi.port:1099}") int port) {
        try {
            return LocateRegistry.getRegistry(host, port);
        } catch (Exception e) {
            log.error("Failed to locate RMI registry at {}:{}", host, port, e);
            throw new IllegalStateException("RMI Registry unavailable: " + e.getMessage(), e);
        }
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public AuthServiceRemote authServiceRemote(Registry rmiRegistry) {
        return lookup(rmiRegistry, "AuthService", AuthServiceRemote.class);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public AccountServiceRemote accountServiceRemote(Registry rmiRegistry) {
        return lookup(rmiRegistry, "AccountService", AccountServiceRemote.class);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public TransactionServiceRemote transactionServiceRemote(Registry rmiRegistry) {
        return lookup(rmiRegistry, "TransactionService", TransactionServiceRemote.class);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public AdminServiceRemote adminServiceRemote(Registry rmiRegistry) {
        return lookup(rmiRegistry, "AdminService", AdminServiceRemote.class);
    }

    private <T> T lookup(Registry registry, String name, Class<T> type) {
        try {
            Object obj = registry.lookup(name);
            log.info("RMI stub resolved: {} -> {}", name, type.getSimpleName());
            return type.cast(obj);
        } catch (NotBoundException e) {
            log.error("RMI service '{}' not bound yet: {}", name, e.getMessage());
            throw new IllegalStateException("RMI service " + name + " not bound: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to lookup RMI service '{}': {}", name, e.getMessage(), e);
            throw new IllegalStateException("RMI lookup " + name + " failed: " + e.getMessage(), e);
        }
    }
}