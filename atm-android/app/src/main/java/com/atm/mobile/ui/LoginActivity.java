package com.atm.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

import com.atm.mobile.R;
import com.atm.mobile.model.Account;
import com.atm.mobile.network.ApiClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editAccountNumber, editPin, editOtp, editServer;
    private Button btnLogin, btnDetect;
    private TextView textError, textRegister, textDemoOtp, textServerStatus;
    private TextInputLayout otpLayout;

    private boolean mfaPending = false;
    private String mfaToken = "";
    private boolean retriedAfterDiscovery = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.getInstance().init(getApplicationContext());

        if (ApiClient.getInstance().isLoggedIn()) {
            goToDashboard();
            return;
        }

        setContentView(R.layout.activity_login);

        editServer = findViewById(R.id.editServer);
        editAccountNumber = findViewById(R.id.editAccountNumber);
        editPin = findViewById(R.id.editPin);
        editOtp = findViewById(R.id.editOtp);
        btnLogin = findViewById(R.id.btnLogin);
        btnDetect = findViewById(R.id.btnDetect);
        textServerStatus = findViewById(R.id.textServerStatus);
        textError = findViewById(R.id.textError);
        textRegister = findViewById(R.id.textRegister);
        textDemoOtp = findViewById(R.id.textDemoOtp);
        otpLayout = findViewById(R.id.otpLayout);

        String storedServer = ApiClient.getInstance().getStoredBaseUrl();
        if (storedServer != null && !storedServer.isEmpty()) {
            editServer.setText(storedServer);
        }
        btnDetect.setOnClickListener(v -> detectServer(null));

        btnLogin.setOnClickListener(v -> {
            String serverUrl = editServer.getText().toString().trim();
            if (!serverUrl.isEmpty()) {
                ApiClient.getInstance().setBaseUrl(serverUrl.replaceAll("/+$", ""));
            }
            if (mfaPending) {
                attemptVerify();
            } else {
                attemptLogin();
            }
        });
        textRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    /**
     * Locates the gateway on the local network and stores the address it
     * reports, so the app keeps working after the server's DHCP lease moves.
     *
     * @param onDetected optional action to run once an address is found, used
     *                   to replay a login that failed against a stale URL
     */
    private void detectServer(Runnable onDetected) {
        btnDetect.setEnabled(false);
        btnDetect.setText("Detecting…");
        textServerStatus.setText("Scanning this network for the gateway…");

        ApiClient.getInstance().ensureServer(getApplicationContext(), new ApiClient.ServerCallback() {
            @Override
            public void onResolved(String baseUrl) {
                btnDetect.setEnabled(true);
                btnDetect.setText("Detect server");
                editServer.setText(baseUrl);
                textServerStatus.setText("Using " + baseUrl);
                if (onDetected != null) {
                    onDetected.run();
                }
            }

            @Override
            public void onFailed(String reason) {
                btnDetect.setEnabled(true);
                btnDetect.setText("Detect server");
                textServerStatus.setText("Nothing found - is the server running on this Wi-Fi?");
                showError("No banking server found on this network. Start the gateway, then tap "
                        + "Detect server - or type its URL, e.g. http://<laptop-ip>:8080/api/v1");
            }
        });
    }

    /** True when a failure looks like a dead address rather than bad credentials. */
    private boolean isConnectionError(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase(Locale.ROOT);
        return lower.contains("failed to connect")
                || lower.contains("connection refused")
                || lower.contains("connectexception")
                || lower.contains("timeoutexception")
                || lower.contains("timeout")
                || lower.contains("network is unreachable")
                || lower.contains("unable to resolve host")
                || lower.contains("no route to host");
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void attemptLogin() {
        String accNum = editAccountNumber.getText().toString().trim();
        String pin = editPin.getText().toString().trim();

        if (accNum.isEmpty() || pin.isEmpty()) {
            textError.setText("Please enter account number and PIN");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        textError.setVisibility(View.GONE);
        btnLogin.setEnabled(false);
        btnLogin.setText("Verifying credentials...");

        ApiClient.getInstance().authenticate(accNum, pin, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                try {
                    boolean requiresOtp = json.optBoolean("requiresOtp", false);
                    if (requiresOtp) {
                        mfaPending = true;
                        mfaToken = json.getString("mfaToken");
                        String demoOtp = json.optString("demoOtp", null);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Verify & sign in");
                        otpLayout.setVisibility(View.VISIBLE);
                        if (demoOtp != null && !demoOtp.isEmpty()) {
                            textDemoOtp.setText("Demo code: " + demoOtp);
                        }
                        textDemoOtp.setVisibility(View.VISIBLE);
                        textRegister.setVisibility(View.GONE);
                    } else {
                        ApiClient.getInstance().saveSession(json);
                        goToDashboard();
                    }
                } catch (Exception e) {
                    onError(e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");
                // A stale cached address is indistinguishable from a wrong PIN
                // until we re-locate the gateway, so try that once first.
                if (!retriedAfterDiscovery && isConnectionError(error)) {
                    retriedAfterDiscovery = true;
                    textError.setText("Server unreachable - searching the network…");
                    detectServer(LoginActivity.this::retryLogin);
                    return;
                }
                textError.setText(error != null ? error : "Login failed");
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void retryLogin() {
        retriedAfterDiscovery = false;
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
        attemptLogin();
    }

    private void attemptVerify() {
        String otp = editOtp.getText().toString().trim();
        if (otp.isEmpty() || otp.length() != 6) {
            textError.setText("Enter the 6-digit verification code");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        textError.setVisibility(View.GONE);
        btnLogin.setEnabled(false);
        btnLogin.setText("Verifying...");

        ApiClient.getInstance().verifyMfa(mfaToken, otp, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                try {
                    ApiClient.getInstance().saveSession(json);
                    goToDashboard();
                } catch (Exception e) {
                    onError(e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Verify & sign in");
                textError.setText(error != null ? error : "Verification failed");
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void goToDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
