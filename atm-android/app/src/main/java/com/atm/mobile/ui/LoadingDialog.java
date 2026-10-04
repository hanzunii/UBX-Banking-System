package com.atm.mobile.ui;

import android.app.ProgressDialog;
import android.content.Context;

/**
 * Non-dismissable processing overlay used during financial operations.
 * Locks user input while a transaction is being processed.
 */
public class LoadingDialog {

    private final ProgressDialog dialog;

    public LoadingDialog(Context context, String message) {
        dialog = new ProgressDialog(context);
        dialog.setMessage(message);
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    public void show() {
        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}