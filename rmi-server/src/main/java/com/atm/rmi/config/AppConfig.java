package com.atm.rmi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {

    @Value("${auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${auth.lockout-minutes:15}")
    private int lockoutMinutes;

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public int getLockoutMinutes() {
        return lockoutMinutes;
    }
}
