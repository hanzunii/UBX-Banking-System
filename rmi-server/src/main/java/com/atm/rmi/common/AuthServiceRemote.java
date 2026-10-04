package com.atm.rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthServiceRemote extends Remote {
    String login(String accountNumber, String pin, String deviceId) throws RemoteException;
    boolean register(String accountNumber, String holderName, String phone, String pin, String role) throws RemoteException;
    void logout(String token) throws RemoteException;
    void preauthenticate(String accountNumber, String pin) throws RemoteException;
    String generateOtp(String accountNumber) throws RemoteException;
    boolean verifyOtp(String accountNumber, String otp) throws RemoteException;
}
