package com.atm.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.math.BigDecimal;
import java.util.List;

public interface TransactionServiceRemote extends Remote {
    Transaction transfer(String senderAccountNumber, String receiverAccountNumber, BigDecimal amount, String reference) throws RemoteException;
    Transaction deposit(String accountNumber, BigDecimal amount) throws RemoteException;
    Transaction withdraw(String accountNumber, BigDecimal amount, String pin) throws RemoteException;
    List<Transaction> getHistory(String accountNumber, int limit) throws RemoteException;
}
