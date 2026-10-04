package com.atm.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AccountServiceRemote extends Remote {
    Account getAccountDetails(String accountNumber) throws RemoteException;
    Account getAccountById(String accountNumber, Long id) throws RemoteException;
}
