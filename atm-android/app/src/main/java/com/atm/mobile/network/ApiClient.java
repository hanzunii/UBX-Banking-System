package com.atm.mobile.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client for the UBX API Gateway (REST -> RMI bridge).
 * Coordinates with the gateway at /api/v1 and includes client-generated
 * X-Idempotency-Key headers on all financial operations to prevent
 * duplicate submissions.
 *
 * <p>The gateway's address is never compiled in: it is discovered on the
 * network at runtime by {@link ServerDiscovery} and cached in preferences,
 * so a DHCP change on the server no longer requires rebuilding the app.
 */
public class ApiClient {

    private static ApiClient instance;
    private static final String PREF_NAME = "atm_prefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_ACCOUNT = "account_number";
    private static final String KEY_HOLDER = "holder_name";
    private static final String KEY_ROLE = "role";
    private static final String KEY_CURRENCY = "currency";
    private static final String KEY_BASE_URL = "base_url";

    /** Last-resort URL for a stock emulator, which reaches the host via 10.0.2.2. */
    private static final String FALLBACK_BASE_URL =
            "http://10.0.2.2:" + ServerDiscovery.DEFAULT_HTTP_PORT + ServerDiscovery.API_PATH;

    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    /** Result of {@link #ensureServer}, always delivered on the main thread. */
    public interface ServerCallback {
        void onResolved(String baseUrl);

        void onFailed(String reason);
    }

    private ApiClient() {}

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public void init(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    private String getBaseUrl() {
        String stored = getStoredBaseUrl();
        return (stored != null && !stored.isEmpty()) ? stored : FALLBACK_BASE_URL;
    }

    /** Last known gateway URL, or null when the app has never found one. */
    public String getStoredBaseUrl() {
        return prefs != null ? prefs.getString(KEY_BASE_URL, null) : null;
    }

    // ── Server discovery ─────────────────────────────────────────────
    /**
     * Guarantees that requests go to a live gateway, whatever address it has
     * today.
     *
     * <p>The cached URL is validated first, so a normal launch costs a single
     * cheap health probe. When the server has moved - the usual DHCP case -
     * the probe fails and {@link ServerDiscovery} locates the new address.
     * Any URL found this way is persisted for the next launch.
     */
    public void ensureServer(Context context, ServerCallback callback) {
        executor.execute(() -> {
            String stored = getStoredBaseUrl();
            if (stored != null && !stored.isEmpty() && ServerDiscovery.isReachable(stored)) {
                postServerResolved(callback, stored);
                return;
            }
            ServerDiscovery.discover(context, portOf(stored), new ServerDiscovery.Callback() {
                @Override
                public void onFound(String baseUrl) {
                    setBaseUrl(baseUrl);
                    postServerResolved(callback, baseUrl);
                }

                @Override
                public void onFailed(String reason) {
                    postServerFailed(callback, reason);
                }
            });
        });
    }

    /** Port of a stored base URL, or the gateway default when unknown. */
    private int portOf(String baseUrl) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            try {
                int port = new URL(baseUrl).getPort();
                if (port > 0) {
                    return port;
                }
            } catch (Exception ignored) {
                // Fall through to the default.
            }
        }
        return ServerDiscovery.DEFAULT_HTTP_PORT;
    }

    private void postServerResolved(ServerCallback callback, String baseUrl) {
        mainHandler.post(() -> callback.onResolved(baseUrl));
    }

    private void postServerFailed(ServerCallback callback, String reason) {
        mainHandler.post(() -> callback.onFailed(reason));
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public void saveRefreshToken(String refreshToken) {
        prefs.edit().putString(KEY_REFRESH, refreshToken).apply();
    }

    public String getRefreshToken() {
        return prefs != null ? prefs.getString(KEY_REFRESH, null) : null;
    }

    public String getToken() {
        return prefs != null ? prefs.getString(KEY_TOKEN, null) : null;
    }

    public void saveAccount(String accountNumber, String holderName, String role, String currency) {
        prefs.edit()
                .putString(KEY_ACCOUNT, accountNumber)
                .putString(KEY_HOLDER, holderName)
                .putString(KEY_ROLE, role)
                .putString(KEY_CURRENCY, currency)
                .apply();
    }

    public String getAccountNumber() {
        return prefs != null ? prefs.getString(KEY_ACCOUNT, null) : null;
    }

    public String getHolderName() {
        return prefs != null ? prefs.getString(KEY_HOLDER, null) : null;
    }

    public String getCurrency() {
        return prefs != null ? prefs.getString(KEY_CURRENCY, "USD") : "USD";
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_REFRESH).remove(KEY_ACCOUNT).remove(KEY_HOLDER)
                .remove(KEY_ROLE).remove(KEY_CURRENCY).apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty();
    }

    // ── Register ─────────────────────────────────────────────────────
    public void register(String accountNumber, String phone, String holderName, String pin,
                         ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("accountNumber", accountNumber);
                body.put("phone", phone);
                body.put("holderName", holderName);
                body.put("pin", pin);

                String response = post("/auth/register", body.toString());
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── Login ────────────────────────────────────────────────────────
    public void authenticate(String accountNumber, String pin, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("accountNumber", accountNumber);
                body.put("pin", pin);
                body.put("deviceId", "android-" + UUID.randomUUID());

                String response = post("/auth/login", body.toString());
                JSONObject json = new JSONObject(response);

                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── MFA verify ───────────────────────────────────────────────────
    public void verifyMfa(String mfaToken, String otp, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("mfaToken", mfaToken);
                body.put("otp", otp);

                String response = post("/auth/mfa/verify", body.toString());
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── PIN verification (re-auth for sensitive actions) ───────────
    public void verifyPin(String pin, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("pin", pin);

                String response = post("/auth/verify-pin", body.toString());
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    public void saveSession(JSONObject session) throws Exception {
        String accessToken = session.getString("accessToken");
        String refreshToken = session.optString("refreshToken", null);
        String accountNumber = session.optString("accountNumber", getAccountNumber());
        String holderName = session.optString("holderName", null);
        if (holderName == null) holderName = session.optString("name", "");
        String role = session.optString("role", "USER");
        String currency = session.optString("currency", "USD");
        saveToken(accessToken);
        if (refreshToken != null) saveRefreshToken(refreshToken);
        saveAccount(accountNumber, holderName, role, currency);
    }

    // ── Refresh token ────────────────────────────────────────────────
    public void refreshAccessToken(ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String refresh = getRefreshToken();
                if (refresh == null) {
                    postError(callback, "No refresh token");
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("refreshToken", refresh);
                String response = post("/auth/refresh", body.toString());
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    clearToken();
                    postError(callback, json.getString("error"));
                } else {
                    saveToken(json.getString("accessToken"));
                    if (json.has("refreshToken")) saveRefreshToken(json.getString("refreshToken"));
                    postResult(callback, true);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── Health ───────────────────────────────────────────────────────
    public void checkHealth(ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String response = get("/health");
                JSONObject json = new JSONObject(response);
                postResult(callback, json);
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── Balance (GET /accounts/me) ───────────────────────────────────
    public void checkBalance(ApiCallback<Double> callback) {
        executor.execute(() -> {
            try {
                String response = get("/accounts/me");
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json.getDouble("balance"));
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── Transfer ─────────────────────────────────────────────────────
    public void transfer(String toAccountNumber, double amount, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("toAccountNumber", toAccountNumber);
                body.put("amount", amount);

                String response = postWithIdempotency("/transactions/transfer", body.toString());
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── History ──────────────────────────────────────────────────────
    public void getHistory(int limit, ApiCallback<List<JSONObject>> callback) {
        executor.execute(() -> {
            try {
                String response = get("/transactions/history?limit=" + limit);
                JSONObject json = new JSONObject(response);
                JSONArray arr = json.getJSONArray("transactions");
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getJSONObject(i));
                }
                postResult(callback, list);
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── Beneficiary lookup (GET /accounts/lookup/{accountNumber}) ───
    public void lookupAccount(String accountNumber, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String response = get("/accounts/lookup/" + accountNumber);
                JSONObject json = new JSONObject(response);
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                } else {
                    postResult(callback, json);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    // ── HTTP helpers ─────────────────────────────────────────────────
    private String get(String endpoint) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            URL url = new URL(getBaseUrl() + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            String token = getToken();
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            String response = readResponse(conn);
            if (conn.getResponseCode() == 401 && attempt == 0 && getRefreshToken() != null) {
                doRefresh();
                continue;
            }
            return response;
        }
        return null;
    }

    private String post(String endpoint, String jsonBody) throws Exception {
        return postWithHeaders(endpoint, jsonBody, true, null);
    }

    private String postWithIdempotency(String endpoint, String jsonBody) throws Exception {
        return postWithHeaders(endpoint, jsonBody, true, UUID.randomUUID().toString());
    }

    private String postWithHeaders(String endpoint, String jsonBody, boolean withToken, String idempotencyKey) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = openConnection("POST", endpoint, jsonBody, idempotencyKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            if (withToken) {
                String token = getToken();
                if (token != null) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
            }
            if (idempotencyKey != null) {
                conn.setRequestProperty("X-Idempotency-Key", idempotencyKey);
            }

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes());
            os.flush();
            os.close();

            String response = readResponse(conn);
            if (conn.getResponseCode() == 401 && attempt == 0 && withToken && getRefreshToken() != null) {
                doRefresh();
                continue;
            }
            return response;
        }
        return null;
    }

    private HttpURLConnection openConnection(String method, String endpoint, String jsonBody, String idempotencyKey) throws Exception {
        URL url = new URL(getBaseUrl() + endpoint);
        return (HttpURLConnection) url.openConnection();
    }

    private void doRefresh() {
        try {
            String refresh = getRefreshToken();
            if (refresh == null) return;
            JSONObject body = new JSONObject();
            body.put("refreshToken", refresh);
            String response = postWithHeaders("/auth/refresh", body.toString(), false, null);
            JSONObject json = new JSONObject(response);
            if (json.has("accessToken")) {
                saveToken(json.getString("accessToken"));
                if (json.has("refreshToken")) saveRefreshToken(json.getString("refreshToken"));
            }
        } catch (Exception ignored) {
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        java.io.InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private <T> void postResult(ApiCallback<T> callback, T result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(ApiCallback<T> callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }
}