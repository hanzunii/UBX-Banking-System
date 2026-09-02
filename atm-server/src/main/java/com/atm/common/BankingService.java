package com.atm.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BankingService extends Remote {
    // ATM Login / Authentication
    Account authenticate(String accountNumber, String pin) throws RemoteException;

    // Core Banking Operations
    double checkBalance(String accountNumber) throws RemoteException;
    boolean withdraw(String accountNumber, double amount) throws RemoteException;
    boolean deposit(String accountNumber, double amount) throws RemoteException;
    boolean transfer(String fromAccount, String toAccount, double amount) throws RemoteException;
}