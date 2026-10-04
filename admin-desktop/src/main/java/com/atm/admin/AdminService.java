package com.atm.admin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminService {

    private final String baseUrl;
    private final HttpClient http;
    private String accessToken;
    private String refreshToken;
    private String accountNumber;
    private String holderName;
    private String role;

    public AdminService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Auth ──────────────────────────────────────────────────────

    public static class LoginStep1Result {
        public final String mfaToken;
        public final String demoOtp;
        public final String holderName;
        public final String accountNumber;

        public LoginStep1Result(String mfaToken, String demoOtp, String holderName, String accountNumber) {
            this.mfaToken = mfaToken;
            this.demoOtp = demoOtp;
            this.holderName = holderName;
            this.accountNumber = accountNumber;
        }
    }

    public LoginStep1Result login(String accountNumber, String pin) throws Exception {
        JSONObject body = new JSONObject();
        body.put("accountNumber", accountNumber);
        body.put("pin", pin);
        body.put("deviceId", "desktop-" + UUID.randomUUID());

        String resp = post("/auth/login", body.toString(), null);
        JSONObject json = new JSONObject(resp);
        if (json.has("error")) throw new Exception(json.getString("error"));
        return new LoginStep1Result(
                json.getString("mfaToken"),
                json.optString("demoOtp", ""),
                json.optString("holderName", ""),
                json.optString("accountNumber", "")
        );
    }

    public void verifyMfa(String mfaToken, String otp) throws Exception {
        JSONObject body = new JSONObject();
        body.put("mfaToken", mfaToken);
        body.put("otp", otp);

        String resp = post("/auth/mfa/verify", body.toString(), null);
        JSONObject json = new JSONObject(resp);
        if (json.has("error")) throw new Exception(json.getString("error"));

        this.accessToken = json.getString("accessToken");
        this.refreshToken = json.optString("refreshToken", null);
        this.accountNumber = json.getString("accountNumber");
        this.holderName = json.getString("holderName");
        this.role = json.getString("role");
    }

    public void logout() {
        try {
            if (accessToken != null) {
                post("/auth/logout", "{}", accessToken);
            }
        } catch (Exception ignored) {}
        this.accessToken = null;
        this.refreshToken = null;
    }

    private void refreshAccessToken() {
        if (refreshToken == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("refreshToken", refreshToken);
            String resp = postRaw("/auth/refresh", body.toString());
            JSONObject json = new JSONObject(resp);
            if (json.has("accessToken")) {
                this.accessToken = json.getString("accessToken");
                if (json.has("refreshToken")) this.refreshToken = json.getString("refreshToken");
            }
        } catch (Exception e) {
            this.accessToken = null;
        }
    }

    // ── Admin operations ─────────────────────────────────────────

    public List<Account> listPending() throws Exception {
        String resp = get("/admin/pending", accessToken);
        return parseAccounts(resp);
    }

    public List<Account> listActive() throws Exception {
        String resp = get("/admin/accounts", accessToken);
        return parseAccounts(resp);
    }

    private List<Account> parseAccounts(String resp) {
        JSONArray arr = new JSONArray(resp);
        List<Account> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            list.add(parseAccount(obj));
        }
        return list;
    }

    public Account approve(String accountNumber) throws Exception {
        String resp = post("/admin/" + accountNumber + "/approve", "{}", accessToken);
        JSONObject json = new JSONObject(resp);
        if (json.has("error")) throw new Exception(json.getString("error"));
        return parseAccount(json);
    }

    public Account reject(String accountNumber) throws Exception {
        String resp = post("/admin/" + accountNumber + "/reject", "{}", accessToken);
        JSONObject json = new JSONObject(resp);
        if (json.has("error")) throw new Exception(json.getString("error"));
        return parseAccount(json);
    }

    public Account credit(String accountNumber, double amount, String reference) throws Exception {
        JSONObject body = new JSONObject();
        body.put("amount", amount);
        if (reference != null && !reference.isEmpty()) body.put("reference", reference);

        String resp = post("/admin/" + accountNumber + "/credit", body.toString(), accessToken);
        JSONObject json = new JSONObject(resp);
        if (json.has("error")) throw new Exception(json.getString("error"));
        return parseAccount(json.getJSONObject("account"));
    }

    // ── Health ───────────────────────────────────────────────────

    public boolean checkHealth() {
        try {
            String resp = get("/health", null);
            JSONObject json = new JSONObject(resp);
            return "UP".equals(json.optString("status"));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private Account parseAccount(JSONObject obj) {
        Account a = new Account();
        a.setId(obj.optLong("id", 0));
        a.setAccountNumber(obj.optString("accountNumber", ""));
        a.setHolderName(obj.optString("holderName", ""));
        a.setBalance(obj.optBigDecimal("balance", java.math.BigDecimal.ZERO));
        a.setCurrency(obj.optString("currency", "USD"));
        a.setRole(obj.optString("role", "USER"));
        a.setActive(obj.optBoolean("active", false));
        a.setStatus(obj.optString("status", ""));
        a.setCreatedAt(obj.optString("createdAt", ""));
        return a;
    }

    private String get(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1" + path))
                .header("Accept", "application/json")
                .GET();
        if (token != null) req.header("Authorization", "Bearer " + token);

        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401 && token != null) {
            refreshAccessToken();
            if (accessToken != null) return get(path, null);
        }
        if (resp.statusCode() >= 400) throw new Exception("Error " + resp.statusCode() + ": " + resp.body());
        return resp.body();
    }

    private String post(String path, String body, String token) throws Exception {
        String resp = postRaw(path, body, token);
        return resp;
    }

    private String postRaw(String path, String body) throws Exception {
        return postRaw(path, body, null);
    }

    private String postRaw(String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.header("X-Idempotency-Key", UUID.randomUUID().toString());

        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401 && token != null) {
            refreshAccessToken();
            if (accessToken != null) return postRaw(path, body, null);
        }
        return resp.body();
    }

    public boolean isLoggedIn() { return accessToken != null; }
    public String getHolderName() { return holderName; }
    public String getRole() { return role; }
    public String getAccountNumber() { return accountNumber; }
}
