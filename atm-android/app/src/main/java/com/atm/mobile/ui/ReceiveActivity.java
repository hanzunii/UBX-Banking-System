package com.atm.mobile.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.atm.mobile.R;
import com.atm.mobile.network.ApiClient;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ReceiveActivity extends AppCompatActivity {

    private ImageView imageQr;
    private TextView textHolderName, textAccountNumber;
    private Button btnCopyAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        imageQr = findViewById(R.id.imageQr);
        textHolderName = findViewById(R.id.textHolderName);
        textAccountNumber = findViewById(R.id.textAccountNumber);
        btnCopyAccount = findViewById(R.id.btnCopyAccount);

        String accountNumber = ApiClient.getInstance().getAccountNumber();
        String holderName = ApiClient.getInstance().getHolderName();

        textHolderName.setText(holderName != null ? holderName : "");
        textAccountNumber.setText(accountNumber != null ? accountNumber : "");

        String payload = "UBX_BANK|" + accountNumber + "|" + holderName;
        generateQrCode(payload);

        btnCopyAccount.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (accountNumber != null && clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Account Number", accountNumber);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Account number copied!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateQrCode(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 600, 600);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            imageQr.setImageBitmap(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }
}
