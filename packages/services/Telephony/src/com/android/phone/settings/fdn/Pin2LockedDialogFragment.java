/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone.settings.fdn;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;

import com.android.phone.R;

/**
 * Dialog Fragment that displays dialogs indicating that PIN2/PUK2 has been locked out.
 *
 * 1. When user fails PIN2 authentication and PIN2 is locked, show the dialog indicating that PIN2
 * is locked and PUK2 must be entered.
 * 2. When user fails PUK2 authentication and PUK2 is locked, show the dialog indicating that PUK2
 * is locked and user must contact service provider to unlock PUK2.
 */
public class Pin2LockedDialogFragment extends DialogFragment {

    static final String TAG_PIN2_LOCKED_DIALOG = "tag_pin2_locked_dialog";
    static final String KEY_DIALOG_ID = "key_dialog_id";

    // AlertDialog IDs
    static final int DIALOG_ID_PUK2_LOCKED_OUT = 10;
    static final int DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY = 11;
    static final int DIALOG_ID_PUK2_REQUESTED_ON_PIN_CHANGED = 12;

    private Listener mListener;
    private int mId;

    interface Listener {
        void onRequestPuk2(int id);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Activity activity = getActivity();
        if (!(activity instanceof Listener)) {
            return null;
        }
        mListener = (Listener) activity;
        mId = getArguments().getInt(KEY_DIALOG_ID);

        if (mId == DIALOG_ID_PUK2_LOCKED_OUT) {
            AlertDialog alert = new AlertDialog.Builder(activity)
                    .setMessage(R.string.puk2_locked)
                    .setCancelable(true)
                    .create();
            alert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            alert.setButton(DialogInterface.BUTTON_NEUTRAL, getText(R.string.ok),
                    (dialog, which) -> {
                    });
            return alert;
        }

        if (mId == DIALOG_ID_PUK2_REQUESTED_ON_PIN_CHANGED
                || mId == DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY) {
            AlertDialog alert = new AlertDialog.Builder(activity)
                    .setMessage(R.string.puk2_requested)
                    .setCancelable(true)
                    .create();
            alert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            alert.setButton(DialogInterface.BUTTON_NEUTRAL, getText(R.string.ok),
                    (dialog, which) -> {
                        mListener.onRequestPuk2(mId);
                        dialog.dismiss();
                    });
            return alert;
        }
        return null;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (mId == DIALOG_ID_PUK2_REQUESTED_ON_PIN_CHANGED
                || mId == DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY) {
            mListener.onRequestPuk2(mId);
        }
        dialog.dismiss();
    }
}

