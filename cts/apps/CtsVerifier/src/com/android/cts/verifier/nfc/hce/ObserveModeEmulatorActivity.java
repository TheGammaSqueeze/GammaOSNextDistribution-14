/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.nfc.hce;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

public class ObserveModeEmulatorActivity extends BaseEmulatorActivity {

    static String sRfOnAction = "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(true);
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        IntentFilter filter = new IntentFilter(sRfOnAction);
        registerReceiver(mFieldStateReceiver, filter, RECEIVER_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NfcDialogs.createHceTapReaderDialog(this,
                getString(R.string.nfc_observe_mode_emulator_help)).show();
        mAdapter.setObserveModeEnabled(true);
        getPassButton().setEnabled(false);
    }

    @Override
    void onServicesSetup(boolean result) {
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        getPassButton().setEnabled(false);
    }

    final BroadcastReceiver mFieldStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(sRfOnAction)) {
                getPassButton().setEnabled(true);
            }
        }
    };
}
