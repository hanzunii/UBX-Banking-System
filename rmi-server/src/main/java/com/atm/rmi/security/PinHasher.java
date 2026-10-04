package com.atm.rmi.security;

import org.mindrot.jbcrypt.BCrypt;

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
