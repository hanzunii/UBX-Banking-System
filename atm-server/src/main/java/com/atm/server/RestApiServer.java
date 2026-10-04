package com.atm.server;

import com.atm.common.Account;
import com.atm.common.BankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RestApiServer {

    private final BankingService bankingService;
    private final HttpServer server;

    public RestApiServer(int port, String rmiHost, int rmiPort) throws Exception {
        Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
        this.bankingService = (BankingService) registry.lookup("BankingService");

        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/balance", new BalanceHandler());
        server.createContext("/api/withdraw", new WithdrawHandler());
        server.createContext("/api/deposit", new DepositHandler());
        server.createContext("/api/transfer", new TransferHandler());
        server.setExecutor(null);
    }

    public void start() {
        server.start();
        System.out.println("REST API Server started on port " + server.getAddress().getPort());
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] data = is.readAllBytes();
        return new String(data);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] response = json.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private String extractValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        idx = json.indexOf(":", idx) + 1;
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        int start = idx;
        if (idx < json.length() && json.charAt(idx) == '"') {
            start = idx + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    // POST /api/auth  {"accountNumber":"...", "pin":"..."}
    private class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                String accNum = extractValue(body, "accountNumber");
                String pin = extractValue(body, "pin");

                Account account = bankingService.authenticate(accNum, pin);
                if (account != null) {
                    String json = String.format(
                        "{\"accountNumber\":\"%s\",\"holderName\":\"%s\",\"balance\":%.2f}",
                        account.getAccountNumber(), account.getHolderName(), account.getBalance()
                    );
                    sendResponse(exchange, 200, json);
                } else {
                    sendResponse(exchange, 401, "{\"error\":\"Invalid credentials\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // GET /api/balance/{accountNumber}
    private class BalanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String path = exchange.getRequestURI().getPath();
                String accNum = path.substring(path.lastIndexOf('/') + 1);
                double balance = bankingService.checkBalance(accNum);
                if (balance >= 0) {
                    sendResponse(exchange, 200, "{\"balance\":" + balance + "}");
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Account not found\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // POST /api/withdraw  {"accountNumber":"...", "amount":500}
    private class WithdrawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                String accNum = extractValue(body, "accountNumber");
                double amount = Double.parseDouble(extractValue(body, "amount"));

                boolean success = bankingService.withdraw(accNum, amount);
                if (success) {
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Withdrawal successful\"}");
                } else {
                    sendResponse(exchange, 400, "{\"success\":false,\"error\":\"Insufficient balance or invalid amount\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // POST /api/deposit  {"accountNumber":"...", "amount":500}
    private class DepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                String accNum = extractValue(body, "accountNumber");
                double amount = Double.parseDouble(extractValue(body, "amount"));

                boolean success = bankingService.deposit(accNum, amount);
                if (success) {
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Deposit successful\"}");
                } else {
                    sendResponse(exchange, 400, "{\"success\":false,\"error\":\"Invalid amount\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // POST /api/transfer  {"from":"...", "to":"...", "amount":500}
    private class TransferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = readBody(exchange);
                String from = extractValue(body, "from");
                String to = extractValue(body, "to");
                double amount = Double.parseDouble(extractValue(body, "amount"));

                boolean success = bankingService.transfer(from, to, amount);
                if (success) {
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Transfer successful\"}");
                } else {
                    sendResponse(exchange, 400, "{\"success\":false,\"error\":\"Transfer failed\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
}
