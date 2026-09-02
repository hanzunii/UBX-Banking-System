package com.atm.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    // Oracle DB URL (XE instance သို့မဟုတ် မိမိ Service Name အတိုင်း ပြင်ပါ)
    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:XE"; 
    private static final String USER = "system"; // မိမိ Oracle DB Username
    private static final String PASS = "your_password"; // မိမိ Oracle DB Password

    // Oracle JDBC Driver ကို explicit load လုပ်ပေးခြင်း
    static {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("Oracle JDBC Driver not found!");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }
}