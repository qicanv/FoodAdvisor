package com.foodadvisor;

import org.mindrot.jbcrypt.BCrypt;

public class GenerateHash {
    public static void main(String[] args) {
        String password = "123456";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println("BCrypt hash for '123456': " + hash);
        System.out.println();
        System.out.println("SQL to update demo user:");
        System.out.println("UPDATE users SET password_hash = '" + hash + "' WHERE username = 'demo';");
    }
}