package com.atm.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.atm.mobile.R;
import com.atm.mobile.network.ApiClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;

import org.json.JSONObject;

public class TransferActivity extends AppCompatActivity {

    private TextInputEditText editToAccount, editAmount;
    private TextInputLayout layoutToAccount;
    private Button btnTransfer;
    private TextView textError, textBalance, textVerifiedHolder;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String confirmedName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.getInstance().init(getApplicationContext());
        setContentView(R.layout.activity_transfer);

        editToAccount = findViewById(R.id.editToAccount);
        editAmount = findViewById(R.id.editAmount);
        layoutToAccount = findViewById(R.id.layoutToAccount);
        btnTransfer = findViewById(R.id.btnTransfer);
        textError = findViewById(R.id.textError);
        textBalance = findViewById(R.id.textBalance);
        textVerifiedHolder = findViewById(R.id.textVerifiedHolder);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnScan).setOnClickListener(v -> openScanner());

        layoutToAccount.setEndIconOnClickListener(v -> lookupAndVerify());
        // Auto-lookup after the user pauses typing (debounced)
        editToAccount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                handler.removeCallbacksAndMessages(null);
                confirmedName = "";
                textVerifiedHolder.setVisibility(View.GONE);
                String acct = s.toString().trim();
                if (acct.length() >= 7) {
                    handler.postDelayed(() -> lookupOnly(acct), 700);
                }
            }
        });

        // Prefill from a scanned QR code
        Intent intent = getIntent();
        String toAccount = intent != null ? intent.getStringExtra("toAccount") : null;
        String toName = intent != null ? intent.getStringExtra("toName") : null;
        if (toAccount != null && !toAccount.isEmpty()) {
            editToAccount.setText(toAccount);
            if (toName != null && !toName.isEmpty()) {
                setVerified(toName);
            }
            lookupAndVerify();
        }

        loadBalance();
        btnTransfer.setOnClickListener(v -> attemptTransfer());
    }

    private void loadBalance() {
        ApiClient.getInstance().checkBalance(new ApiClient.ApiCallback<Double>() {
            @Override
            public void onSuccess(Double balance) {
                runOnUiThread(() -> textBalance.setText(String.format("$%,.2f", balance)));
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void openScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Align a UBX payment QR within the frame");
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        com.google.zxing.integration.android.IntentResult result =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) return;
        String contents = result.getContents();
        if (contents == null || contents.isEmpty()) return;

        String[] parts = contents.split("\\|");
        boolean validPrefix = "UBX_BANK".equalsIgnoreCase(parts[0].trim())
                || "ATM_BANK".equalsIgnoreCase(parts[0].trim());
        if (parts.length < 2 || !validPrefix) {
            Toast.makeText(this, "Not a valid UBX payment QR", Toast.LENGTH_SHORT).show();
            return;
        }
        String acct = parts[1].trim();
        String name = parts.length >= 3 ? parts[2].trim() : "";
        editToAccount.setText(acct);
        if (!name.isEmpty()) setVerified(name);
        lookupAndVerify();
        Toast.makeText(this, "QR loaded for " + (name.isEmpty() ? acct : name), Toast.LENGTH_SHORT).show();
    }

    private void lookupAndVerify() {
        String acct = editToAccount.getText().toString().trim();
        if (acct.isEmpty()) {
            textError.setText("Enter a recipient account number");
            textError.setVisibility(View.VISIBLE);
            return;
        }
        textError.setVisibility(View.GONE);
        lookupOnly(acct);
    }

    private void lookupOnly(String acct) {
        if (ApiClient.getInstance().getAccountNumber().equals(acct)) {
            setVerifiedError("You cannot transfer to your own account");
            return;
        }
        ApiClient.getInstance().lookupAccount(acct, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                boolean active = json.optBoolean("active", false);
                String status = json.optString("status", "");
                String name = json.optString("holderName", "");
                String self = ApiClient.getInstance().getAccountNumber();
                if (acct.equals(self)) {
                    setVerifiedError("You cannot transfer to your own account");
                    return;
                }
                if (!active || !"ACTIVE".equalsIgnoreCase(status)) {
                    setVerifiedError("Account " + acct + " is not active yet (" + status + ")");
                    return;
                }
                setVerified(name);
            }

            @Override
            public void onError(String error) {
                setVerifiedError("No account found for " + acct);
            }
        });
    }

    private void setVerified(String name) {
        confirmedName = name;
        runOnUiThread(() -> {
            textVerifiedHolder.setTextColor(getResources().getColor(R.color.success));
            textVerifiedHolder.setText("✓ " + name + " · verified");
            textVerifiedHolder.setVisibility(View.VISIBLE);
        });
    }

    private void setVerifiedError(String msg) {
        confirmedName = "";
        runOnUiThread(() -> {
            textVerifiedHolder.setTextColor(getResources().getColor(R.color.error));
            textVerifiedHolder.setText("✕ " + msg);
            textVerifiedHolder.setVisibility(View.VISIBLE);
        });
    }

    private void attemptTransfer() {
        String toAccount = editToAccount.getText().toString().trim();
        String amountStr = editAmount.getText().toString().trim();

        if (toAccount.isEmpty() || amountStr.isEmpty()) {
            textError.setText("Please fill in all fields");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            textError.setText("Invalid amount");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        if (amount <= 0) {
            textError.setText("Amount must be greater than 0");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        promptForPin(toAccount, amount);
    }

    private void promptForPin(String toAccount, double amount) {
        String confirmMsg = confirmedName.isEmpty()
                ? "Send $" + String.format("%,.2f", amount) + " to account " + toAccount + "?"
                : "Send $" + String.format("%,.2f", amount) + " to " + confirmedName + " (" + toAccount + ")?";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int outerPad = dp(20);
        container.setPadding(outerPad, 0, outerPad, 0);

        TextView message = new TextView(this);
        message.setText(confirmMsg);
        message.setTextSize(14);
        message.setTextColor(getResources().getColor(R.color.text));
        container.addView(message);

        EditText pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Enter your 4-digit PIN");
        pinInput.setTextSize(16);
        pinInput.setSingleLine(true);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pinParams.topMargin = dp(14);
        pinInput.setLayoutParams(pinParams);
        container.addView(pinInput);

        TextView pinError = new TextView(this);
        pinError.setTextColor(getResources().getColor(R.color.error));
        pinError.setTextSize(13);
        pinError.setVisibility(View.GONE);
        LinearLayout.LayoutParams errParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errParams.topMargin = dp(10);
        pinError.setLayoutParams(errParams);
        container.addView(pinError);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enter PIN to confirm")
                .setView(container)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", (d, w) -> btnTransfer.setEnabled(true))
                .setOnCancelListener(d -> btnTransfer.setEnabled(true))
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String pin = pinInput.getText().toString().trim();
                    if (pin.length() != 4 || !pin.matches("\\d{4}")) {
                        pinError.setText("PIN must be 4 digits");
                        pinError.setVisibility(View.VISIBLE);
                        return;
                    }
                    pinError.setVisibility(View.GONE);
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                    ApiClient.getInstance().verifyPin(pin, new ApiClient.ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            dialog.dismiss();
                            doTransfer(toAccount, amount);
                        }

                        @Override
                        public void onError(String error) {
                            pinError.setText(error != null ? error : "PIN verification failed");
                            pinError.setVisibility(View.VISIBLE);
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            pinInput.setText("");
                        }
                    });
                }));

        dialog.show();
    }

    private void doTransfer(String toAccount, double amount) {
        btnTransfer.setEnabled(false);
        btnTransfer.setText("Sending…");
        textError.setVisibility(View.GONE);
        final LoadingDialog loading = new LoadingDialog(this, "Processing transfer…");
        loading.show();

        ApiClient.getInstance().transfer(toAccount, amount, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                loading.dismiss();
                Toast.makeText(TransferActivity.this, "Transfer successful!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                loading.dismiss();
                btnTransfer.setEnabled(true);
                btnTransfer.setText("Send Money");
                textError.setText(error != null ? error : "Transfer failed");
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}