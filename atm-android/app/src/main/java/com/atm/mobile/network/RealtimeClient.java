package com.atm.mobile.network;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Realtime push client. Connects to the API Gateway WebSocket endpoint (/ws),
 * authenticates with the JWT access token and relays ACCOUNT_UPDATED events
 * (balance changes from transfers / admin credit) to registered listeners so
 * the dashboard stays in sync without manual refresh.
 */
public final class RealtimeClient {

    public interface BalanceListener {
        void onBalanceUpdated(String balance, String message);
    }

    public interface StateListener {
        void onRealtimeStateChanged(boolean connected);
    }

    private static RealtimeClient instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<BalanceListener> balanceListeners = new CopyOnWriteArraySet<>();
    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<>();

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean manualClose = true;
    private boolean socketOpen = false;

    private final Runnable reconnectTask = () -> {
        if (!manualClose) {
            connect();
        }
    };

    private RealtimeClient() {}

    public static synchronized RealtimeClient getInstance() {
        if (instance == null) {
            instance = new RealtimeClient();
        }
        return instance;
    }

    public void addBalanceListener(BalanceListener l) { balanceListeners.add(l); }
    public void removeBalanceListener(BalanceListener l) { balanceListeners.remove(l); }
    public void addStateListener(StateListener l) { stateListeners.add(l); }
    public void removeStateListener(StateListener l) { stateListeners.remove(l); }

    public boolean isConnected() { return socketOpen; }

    public void connect() {
        ApiClient api = ApiClient.getInstance();
        if (api.getToken() == null || api.getToken().isEmpty()) return;
        if (webSocket != null) return;

        String url = deriveWebSocketUrl(api.getStoredBaseUrl());
        if (url == null) return;

        manualClose = false;
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .pingInterval(30, TimeUnit.SECONDS)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MINUTES)
                    .build();
        }

        Request request = new Request.Builder().url(url).build();
        this.webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                setSocketOpen(true);
                sendAuth();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                ws.cancel();
                webSocket = null;
                setSocketOpen(false);
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                webSocket = null;
                setSocketOpen(false);
                if (!manualClose) scheduleReconnect();
            }
        });
    }

    public void disconnect() {
        manualClose = true;
        main.removeCallbacks(reconnectTask);
        setSocketOpen(false);
        if (webSocket != null) {
            webSocket.close(1000, "sign out");
            webSocket = null;
        }
        if (httpClient != null) {
            httpClient.connectionPool().evictAll();
        }
    }

    private void sendAuth() {
        String token = ApiClient.getInstance().getToken();
        if (token == null || webSocket == null) return;
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "AUTH");
            msg.put("token", token);
        } catch (JSONException ignored) {
        }
        webSocket.send(msg.toString());
    }

    private void handleMessage(String text) {
        JSONObject json;
        try {
            json = new JSONObject(text);
        } catch (JSONException e) {
            return;
        }
        String type = json.optString("type", "");
        if ("ACCOUNT_UPDATED".equals(type)) {
            double balance = json.optDouble("balance", 0.0);
            String currency = json.optString("currency", "USD");
            String message = json.optString("message", "");
            final String formatted = String.format("%s%,.2f", "$", balance);
            main.post(() -> {
                for (BalanceListener l : balanceListeners) {
                    l.onBalanceUpdated(formatted, message);
                }
            });
        }
    }

    private void setSocketOpen(final boolean open) {
        socketOpen = open;
        main.post(() -> {
            for (StateListener l : stateListeners) {
                l.onRealtimeStateChanged(open);
            }
        });
    }

    private void scheduleReconnect() {
        main.removeCallbacks(reconnectTask);
        main.postDelayed(reconnectTask, 5000);
    }

    private String deriveWebSocketUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) return null;
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getHost() == null) return null;
            String scheme = "https".equalsIgnoreCase(uri.getScheme()) ? "wss" : "ws";
            int port = uri.getPort();
            return scheme + "://" + uri.getHost() + (port > 0 ? ":" + port : "") + "/ws";
        } catch (Exception e) {
            return null;
        }
    }
}