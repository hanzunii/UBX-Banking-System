package com.atm.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.atm.mobile.R;
import com.atm.mobile.network.ApiClient;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editAccountNumber, editPhone, editHolderName, editPin, editPinConfirm;
    private Button btnRegister;
    private TextView textError, textLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        editAccountNumber = findViewById(R.id.editAccountNumber);
        editPhone = findViewById(R.id.editPhone);
        editHolderName = findViewById(R.id.editHolderName);
        editPin = findViewById(R.id.editPin);
        editPinConfirm = findViewById(R.id.editPinConfirm);
        btnRegister = findViewById(R.id.btnRegister);
        textError = findViewById(R.id.textError);
        textLogin = findViewById(R.id.textLogin);

        btnRegister.setOnClickListener(v -> attemptRegister());
        textLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String accNum = editAccountNumber.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();
        String name = editHolderName.getText().toString().trim();
        String pin = editPin.getText().toString().trim();
        String pinConfirm = editPinConfirm.getText().toString().trim();

        if (accNum.isEmpty() || phone.isEmpty() || name.isEmpty() || pin.isEmpty() || pinConfirm.isEmpty()) {
            textError.setText("Please fill in all fields");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        if (pin.length() != 4 || !pin.matches("\\d{4}")) {
            textError.setText("PIN must be exactly 4 digits");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        if (!pin.equals(pinConfirm)) {
            textError.setText("PINs do not match");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        if (!phone.matches("\\d{7,15}")) {
            textError.setText("Phone must be 7-15 digits");
            textError.setVisibility(View.VISIBLE);
            return;
        }

        textError.setVisibility(View.GONE);
        btnRegister.setEnabled(false);
        btnRegister.setText("Registering...");

        ApiClient.getInstance().register(accNum, phone, name, pin, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                String status = result.optString("status", "PENDING");
                String message = result.optString("message",
                        "Account registered. A bank administrator will review and approve it before you can log in.");
                Toast.makeText(RegisterActivity.this, "Registered: " + status + " — " + message,
                        Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String error) {
                btnRegister.setEnabled(true);
                btnRegister.setText("Register");
                textError.setText(error != null ? error : "Registration failed");
                textError.setVisibility(View.VISIBLE);
            }
        });
    }
}