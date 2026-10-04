package com.atm.server.security;

import org.springframework.security.crypto.bcrypt.BCrypt;

public final class PinHasher {

    private PinHasher() {}

    public static String hash(String pin) {
        return BCrypt.hashpw(pin, BCrypt.gensalt(12));
    }

    public static boolean matches(String pin, String hash) {
        try {
            return BCrypt.checkpw(pin, hash);
        } catch (Exception e) {
            return false;
        }
    }
}