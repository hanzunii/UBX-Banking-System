
package com.atm.server;

import com.atm.common.Account;
import com.atm.common.BankingService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BankingServiceImpl extends UnicastRemoteObject implements BankingService {

    public BankingServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized Account authenticate(String accountNumber, String pin) throws RemoteException {
        String query = "SELECT * FROM accounts WHERE account_number = ? AND pin = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, accountNumber);
            pstmt.setString(2, pin);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Account(
                        rs.getString("account_number"),
                        rs.getString("pin"),
                        rs.getString("holder_name"),
                        rs.getDouble("balance")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Login မအောင်မြင်ပါက null ပြန်မည်
    }

    @Override
    public synchronized double checkBalance(String accountNumber) throws RemoteException {
        String query = "SELECT balance FROM accounts WHERE account_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, accountNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public synchronized boolean withdraw(String accountNumber, double amount) throws RemoteException {
        double currentBalance = checkBalance(accountNumber);
        if (currentBalance < amount || amount <= 0) {
            return false; // လက်ကျန်ငွေ မလုံလောက်ပါ သို့မဟုတ် amount မှားယွင်းနေပါက
        }

        String query = "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setDouble(1, amount);
            pstmt.setString(2, accountNumber);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean deposit(String accountNumber, double amount) throws RemoteException {
        if (amount <= 0) return false;

        String query = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setDouble(1, amount);
            pstmt.setString(2, accountNumber);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean transfer(String fromAccount, String toAccount, double amount) throws RemoteException {
        // Simple transaction logic: From အကောင့်မှ ငွေနှုတ်ပြီး To အကောင့်သို့ ငွေပေါင်းထည့်ပေးခြင်း
        if (withdraw(fromAccount, amount)) {
            if (deposit(toAccount, amount)) {
                return true;
            } else {
                // To အကောင့်သို့ ထည့်မရပါက ရုပ်သိမ်းရန် (Rollback)
                deposit(fromAccount, amount);
            }
        }
        return false;
    }
}