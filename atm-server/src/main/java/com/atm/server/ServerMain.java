package com.atm.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {
    public static void main(String[] args) {
        try {
            // ၁။ Database setup ပြုလုပ်ခြင်း
//            DatabaseManager.initializeDatabase();

            // ၂။ RMI Registry ကို Port 1098 ဖြင့် စတင်ဖွင့်လှစ်ခြင်း
            int rmiPort = 1098;
            Registry registry = LocateRegistry.createRegistry(rmiPort);

            // ၃။ Remote Object ကို Instant စွဲပြီး Registry တွင် မှတ်ပုံတင် (Bind) ခြင်း
            BankingServiceImpl bankingService = new BankingServiceImpl();
            registry.rebind("BankingService", bankingService);

            System.out.println("=========================================");
            System.out.println("ATM RMI Server is running on port " + rmiPort + "...");
            System.out.println("Ready to accept Client requests.");
            System.out.println("=========================================");

            // ၄။ REST API Server ကို Port 8080 ဖြင့် စတင်ခြင်း
            int restPort = 8080;
            RestApiServer restServer = new RestApiServer(restPort, "localhost", rmiPort);
            restServer.start();

            System.out.println("=========================================");
            System.out.println("REST API Server is running on port " + restPort + "...");
            System.out.println("Android App can connect via HTTP.");
            System.out.println("=========================================");

        } catch (Exception e) {
            System.err.println("[Server Exception] " + e.getMessage());
            e.printStackTrace();
        }
    }
}