/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.wifi;

import android.content.DialogInterface;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiDialogFragment;
import com.android.wifitrackerlib.WifiEntry;

/** Password dialog for {@link WifiEntry}. */
public class WifiPasswordDialog extends CarUiDialogFragment {
    public static final String TAG = "WifiPasswordDialog";
    private final WifiEntry mWifiEntry;
    private final WifiDialogListener mListener;
    private EditText mEditText;

    /**
     * Host UI component of WifiDialog can receive callbacks by this interface.
     */
    public interface WifiDialogListener {
        /**
         * To save the Wi-Fi network.
         */
        default void onSubmit(WifiPasswordDialog dialog) {
        }
    }

    public WifiPasswordDialog(@NonNull WifiEntry wifiEntry, @Nullable WifiDialogListener listener) {
        super();
        mListener = listener;
        mWifiEntry = wifiEntry;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialogTitle = mWifiEntry.getTitle();
        mDialogLayoutRes = R.layout.preference_dialog_password_edittext;
        mPositiveButtonText = getContext().getString(R.string.okay);
        mNegativeButtonText = getContext().getString(R.string.cancel);
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);
        mEditText = view.findViewById(android.R.id.edit);
        mEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox cb = view.findViewById(R.id.checkbox);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mEditText.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    mEditText.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                // Place cursor at the end
                mEditText.setSelection(mEditText.getText().length());
            }
        });
        mEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                InputMethodManager imm = getActivity().getSystemService(InputMethodManager.class);
                imm.hideSoftInputFromWindow(mEditText.getWindowToken(), /* flags= */ 0);
            }
        });
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mListener != null) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                mListener.onSubmit(this);
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
    }

    public WifiEntry getWifiEntry() {
        return mWifiEntry;
    }

    /**
     * @return {@link WifiConfiguration} from mWifiEntry and UI edit result
     */
    public WifiConfiguration getConfig() {
        return WifiUtil.getWifiConfig(mWifiEntry, mEditText.getText().toString());
    }
}
