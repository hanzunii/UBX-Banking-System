package com.atm.mobile.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.atm.mobile.R;
import com.atm.mobile.network.ApiClient;
import com.atm.mobile.network.RealtimeClient;
import com.google.zxing.integration.android.IntentIntegrator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView textHolderName, textBalance, textAccountNumber, textLastUpdated, liveBadge;
    private View liveDot;
    private ProgressBar balanceProgress;
    private volatile double currentBalance = 0;

    private final RealtimeClient.BalanceListener balanceListener = (balance, message) -> {
        try {
            currentBalance = Double.parseDouble(balance.replaceAll("[^0-9.,-]", "").replace(",", ""));
        } catch (Exception ignored) {
        }
        textBalance.setText(balance);
        textLastUpdated.setText("Live · updated just now");
        pulse();
        balanceProgress.setVisibility(View.GONE);
    };

    private final RealtimeClient.StateListener stateListener = connected ->
            runOnUiThread(() -> setLiveBadge(connected));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.getInstance().init(getApplicationContext());
        setContentView(R.layout.activity_dashboard);

        textHolderName = findViewById(R.id.textHolderName);
        textBalance = findViewById(R.id.textBalance);
        textAccountNumber = findViewById(R.id.textAccountNumber);
        textLastUpdated = findViewById(R.id.textLastUpdated);
        balanceProgress = findViewById(R.id.balanceProgress);
        liveBadge = findViewById(R.id.liveBadge);
        liveDot = findViewById(R.id.liveDot);

        String holder = ApiClient.getInstance().getHolderName();
        textHolderName.setText(holder != null && !holder.isEmpty() ? holder : "Customer");
        textAccountNumber.setText(ApiClient.getInstance().getAccountNumber());

        findViewById(R.id.cardTransfer).setOnClickListener(v ->
                startActivity(new Intent(this, TransferActivity.class)));
        findViewById(R.id.cardScan).setOnClickListener(v -> openScanner());
        findViewById(R.id.cardReceive).setOnClickListener(v ->
                startActivity(new Intent(this, ReceiveActivity.class)));
        findViewById(R.id.cardHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        textAccountNumber.setOnClickListener(v -> showAccountActions());

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Yes", (d, w) -> {
                    RealtimeClient.getInstance().disconnect();
                    ApiClient.getInstance().clearToken();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
        });

        setLiveBadge(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RealtimeClient.getInstance().addBalanceListener(balanceListener);
        RealtimeClient.getInstance().addStateListener(stateListener);
        RealtimeClient.getInstance().connect();
        refreshBalance();
    }

    @Override
    protected void onPause() {
        super.onPause();
        RealtimeClient.getInstance().removeBalanceListener(balanceListener);
        RealtimeClient.getInstance().removeStateListener(stateListener);
    }

    private void refreshBalance() {
        balanceProgress.setVisibility(View.VISIBLE);
        ApiClient.getInstance().checkBalance(new ApiClient.ApiCallback<Double>() {
            @Override
            public void onSuccess(Double balance) {
                currentBalance = balance;
                balanceProgress.setVisibility(View.GONE);
                textBalance.setText(String.format("%s%,.2f", "$", balance));
                textLastUpdated.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + " · live updates");
            }

            @Override
            public void onError(String error) {
                balanceProgress.setVisibility(View.GONE);
                textBalance.setText(String.format("%s%,.2f", "$", currentBalance));
            }
        });
    }

    private void pulse() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(textBalance, "scaleX", 1f, 1.06f, 1f);
        anim.setDuration(420);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
        ObjectAnimator animY = ObjectAnimator.ofFloat(textBalance, "scaleY", 1f, 1.06f, 1f);
        animY.setDuration(420);
        animY.setInterpolator(new DecelerateInterpolator());
        animY.start();
    }

    private void setLiveBadge(boolean connected) {
        if (connected) {
            liveBadge.setText("LIVE");
            liveBadge.setTextColor(ContextCompat.getColor(this, R.color.success));
            liveBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success_soft));
            liveDot.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success));
        } else {
            liveBadge.setText("OFFLINE");
            liveBadge.setTextColor(ContextCompat.getColor(this, R.color.gray));
            liveBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light));
            liveDot.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.muted));
        }
    }

    private void showAccountActions() {
        PopupMenu menu = new PopupMenu(this, textAccountNumber);
        menu.getMenu().add("Copy account number");
        menu.setOnMenuItemClickListener(item -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(
                    "Account Number", ApiClient.getInstance().getAccountNumber());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Account number copied", Toast.LENGTH_SHORT).show();
            return true;
        });
        menu.show();
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
        Intent t = new Intent(this, TransferActivity.class);
        t.putExtra("toAccount", acct);
        t.putExtra("toName", name);
        startActivity(t);
    }
}