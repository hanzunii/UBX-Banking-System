package com.atm.mobile.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.atm.mobile.R;
import com.atm.mobile.network.ApiClient;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private ProgressBar progressLoading;
    private TextView textEmpty, textError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.getInstance().init(getApplicationContext());
        setContentView(R.layout.activity_history);

        historyContainer = findViewById(R.id.historyContainer);
        progressLoading = findViewById(R.id.progressLoading);
        textEmpty = findViewById(R.id.textEmpty);
        textError = findViewById(R.id.textError);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        promptForPin();
    }

    private void promptForPin() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int outerPad = dp(20);
        container.setPadding(outerPad, 0, outerPad, 0);

        TextView message = new TextView(this);
        message.setText("Transaction history shows your account activity. Enter your PIN to continue.");
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Enter PIN")
                .setView(container)
                .setPositiveButton("View history", null)
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String pin = pinInput.getText().toString().trim();
                    if (pin.length() != 4 || !pin.matches("\\d{4}")) {
                        pinError.setText("PIN must be 4 digits");
                        pinError.setVisibility(View.VISIBLE);
                        return;
                    }
                    pinError.setVisibility(View.GONE);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                    ApiClient.getInstance().verifyPin(pin, new ApiClient.ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            dialog.dismiss();
                            loadHistory();
                        }

                        @Override
                        public void onError(String error) {
                            pinError.setText(error != null ? error : "PIN verification failed");
                            pinError.setVisibility(View.VISIBLE);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            pinInput.setText("");
                        }
                    });
                }));

        dialog.show();
    }

    private void loadHistory() {
        progressLoading.setVisibility(View.VISIBLE);
        textEmpty.setVisibility(View.GONE);
        textError.setVisibility(View.GONE);
        historyContainer.removeAllViews();

        ApiClient.getInstance().getHistory(50, new ApiClient.ApiCallback<List<JSONObject>>() {
            @Override
            public void onSuccess(List<JSONObject> transactions) {
                progressLoading.setVisibility(View.GONE);

                if (transactions.isEmpty()) {
                    textEmpty.setVisibility(View.VISIBLE);
                    return;
                }

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                String myAccount = ApiClient.getInstance().getAccountNumber();
                String currency = ApiClient.getInstance().getCurrency();
                String moneyFormat = "%s%,.2f " + currency;

                for (JSONObject tx : transactions) {
                    try {
                        String type = tx.getString("type"); // TRANSFER | DEPOSIT | WITHDRAWAL
                        double amount = Math.abs(tx.getDouble("amount"));
                        double balanceAfter = tx.getDouble("senderBalanceAfter");
                        String timestamp = tx.optString("timestamp", "");

                        boolean isCredit;
                        String peer = "";
                        if (type.equals("TRANSFER")) {
                            String from = tx.optString("fromAccountNumber", "");
                            String to = tx.optString("toAccountNumber", "");
                            boolean isOut = myAccount != null && myAccount.equals(from);
                            isCredit = !isOut;
                            peer = isOut ? ("To: " + to) : ("From: " + from);
                        } else {
                            isCredit = type.equals("DEPOSIT");
                        }
                        String reference = tx.optString("reference", "");

                        LinearLayout card = new LinearLayout(HistoryActivity.this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(dp(16), dp(12), dp(16), dp(12));
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        cardParams.setMargins(0, 0, 0, dp(8));
                        card.setLayoutParams(cardParams);

                        LinearLayout topRow = new LinearLayout(HistoryActivity.this);
                        topRow.setOrientation(LinearLayout.HORIZONTAL);

                        TextView typeLabel = new TextView(HistoryActivity.this);
                        typeLabel.setText(formatType(type));
                        typeLabel.setTextSize(15);
                        typeLabel.setTextColor(isCredit ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
                        typeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        typeParams.weight = 1;
                        typeLabel.setLayoutParams(typeParams);

                        TextView amountView = new TextView(HistoryActivity.this);
                        amountView.setText(formatAmount(isCredit, amount, moneyFormat));
                        amountView.setTextSize(15);
                        amountView.setTextColor(isCredit ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
                        amountView.setTypeface(null, android.graphics.Typeface.BOLD);

                        topRow.addView(typeLabel);
                        topRow.addView(amountView);
                        card.addView(topRow);

                        LinearLayout bottomRow = new LinearLayout(HistoryActivity.this);
                        bottomRow.setOrientation(LinearLayout.HORIZONTAL);

                        String dateStr = formatTimestamp(timestamp, sdf);
                        TextView dateView = new TextView(HistoryActivity.this);
                        dateView.setText(dateStr);
                        dateView.setTextSize(12);
                        dateView.setTextColor(Color.parseColor("#9E9E9E"));
                        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        dateParams.weight = 1;
                        dateView.setLayoutParams(dateParams);

                        TextView balanceView = new TextView(HistoryActivity.this);
                        balanceView.setText(String.format(Locale.getDefault(), "Balance: %s%,.2f %s",
                                "", balanceAfter, currency));
                        balanceView.setTextSize(12);
                        balanceView.setTextColor(Color.parseColor("#9E9E9E"));

                        bottomRow.addView(dateView);
                        bottomRow.addView(balanceView);
                        card.addView(bottomRow);

                        if (!peer.isEmpty()) {
                            TextView counterpartyView = new TextView(HistoryActivity.this);
                            counterpartyView.setText(peer);
                            counterpartyView.setTextSize(12);
                            counterpartyView.setTextColor(Color.parseColor("#757575"));
                            card.addView(counterpartyView);
                        }

                        if (!reference.isEmpty()) {
                            TextView referenceView = new TextView(HistoryActivity.this);
                            referenceView.setText("Ref: " + reference);
                            referenceView.setTextSize(12);
                            referenceView.setTextColor(Color.parseColor("#757575"));
                            card.addView(referenceView);
                        }

                        historyContainer.addView(card);

                    } catch (Exception e) {
                        // skip malformed item
                    }
                }
            }

            @Override
            public void onError(String error) {
                progressLoading.setVisibility(View.GONE);
                textError.setText(error != null ? error : "Failed to load history");
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private String formatType(String type) {
        switch (type) {
            case "DEPOSIT": return "Deposit";
            case "WITHDRAWAL": return "Withdrawal";
            case "TRANSFER": return "Transfer";
            default: return type;
        }
    }

    private String formatAmount(boolean isCredit, double amount, String moneyFormat) {
        return String.format(Locale.getDefault(), moneyFormat, isCredit ? "+" : "-", amount);
    }

    private String formatTimestamp(String iso, SimpleDateFormat sdf) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            String cleaned = iso.contains(".") ? iso.substring(0, iso.indexOf('.')) : iso;
            String[] parts = cleaned.split("T");
            String datePart = parts.length > 0 ? parts[0] : cleaned;
            String timePart = parts.length > 1 ? parts[1] : "00:00:00";
            String[] d = datePart.split("-");
            int year = Integer.parseInt(d[0]);
            int month = Integer.parseInt(d[1]);
            int day = Integer.parseInt(d[2]);
            String[] t = timePart.split(":");
            int hour = Integer.parseInt(t[0]);
            int minute = Integer.parseInt(t[1]);
            int second = t.length > 2 ? Integer.parseInt(t[2]) : 0;
            return sdf.format(new java.util.Date(year - 1900, month - 1, day, hour, minute, second));
        } catch (Exception e) {
            return iso.replace('T', ' ');
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}