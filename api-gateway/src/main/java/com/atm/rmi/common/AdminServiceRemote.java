package com.atm.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.math.BigDecimal;
import java.util.List;

public interface AdminServiceRemote extends Remote {
    List<Account> listPendingAccounts() throws RemoteException;
    List<Account> listActiveAccounts() throws RemoteException;
    Account approveAccount(String adminAccountNumber, String accountNumber) throws RemoteException;
    Account rejectAccount(String adminAccountNumber, String accountNumber) throws RemoteException;
    Account creditAccount(String adminAccountNumber, String accountNumber, BigDecimal amount, String reference) throws RemoteException;
}