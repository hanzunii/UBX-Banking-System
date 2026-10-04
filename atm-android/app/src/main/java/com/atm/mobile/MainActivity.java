package com.atm.mobile;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.atm.mobile.network.ApiClient;
import com.atm.mobile.ui.LoginActivity;
import com.atm.mobile.ui.DashboardActivity;

import org.json.JSONObject;

/**
 * Cold-start splash screen. Locates the gateway on the local network, then
 * pings its /health endpoint so the app renders live server state before the
 * user proceeds. A DHCP change on the server is absorbed here rather than
 * surfacing as a login failure.
 */
public class MainActivity extends AppCompatActivity {

    private static final int SPLASH_TIMEOUT_MS = 15000;
    private static final long RETRY_DELAY_MS = 3000;

    private TextView textStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean startedNext = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.getInstance().init(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(76, 29, 149));

        TextView logo = new TextView(this);
        logo.setText("UBX");
        logo.setTextSize(42);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, 24, 0, 24);
        root.addView(logo);

        TextView title = new TextView(this);
        title.setText("Distributed RMI Banking");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        ProgressBar bar = new ProgressBar(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = 48;
        root.addView(bar, barParams);

        textStatus = new TextView(this);
        textStatus.setText("Connecting to banking services…");
        textStatus.setTextColor(Color.parseColor("#B3FFFFFF"));
        textStatus.setTextSize(13);
        textStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 24;
        root.addView(textStatus, statusParams);

        setContentView(root);

        handler.postDelayed(() -> proceedApparently(), SPLASH_TIMEOUT_MS);
        locateGatewayAndPing();
    }

    /**
     * Resolves the gateway address, then confirms it is healthy. Both steps are
     * retried until the splash timeout hands over to the login screen.
     */
    private void locateGatewayAndPing() {
        textStatus.setText("Looking for the banking server on this network…");
        ApiClient.getInstance().ensureServer(getApplicationContext(), new ApiClient.ServerCallback() {
            @Override
            public void onResolved(String baseUrl) {
                runOnUiThread(() -> textStatus.setText("Server found at " + baseUrl));
                pingGateway();
            }

            @Override
            public void onFailed(String reason) {
                runOnUiThread(() -> textStatus.setText(
                        "No banking server on this Wi-Fi — retrying…"));
                handler.postDelayed(MainActivity.this::locateGatewayAndPing, RETRY_DELAY_MS);
            }
        });
    }

    private void pingGateway() {
        ApiClient.getInstance().checkHealth(new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                runOnUiThread(() -> textStatus.setText("System operational — loading account…"));
                handler.postDelayed(MainActivity.this::proceed, 500);
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> textStatus.setText("Gateway not responding — retrying…"));
                handler.postDelayed(MainActivity.this::locateGatewayAndPing, RETRY_DELAY_MS);
            }
        });
    }

    private void proceed() {
        if (startedNext) return;
        startedNext = true;
        Intent target = ApiClient.getInstance().isLoggedIn()
                ? new Intent(this, DashboardActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(target);
        finish();
    }

    private void proceedApparently() {
        if (startedNext) return;
        proceed();
    }
}