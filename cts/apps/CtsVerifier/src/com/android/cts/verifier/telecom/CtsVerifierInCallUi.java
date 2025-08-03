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

package com.android.cts.verifier.telecom;

import static com.android.cts.verifier.dialer.DialerCallTestService.EXTRA_CALL_NAME;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.R;

@ApiTest(apis={"android.telecom.InCallService"})
public class CtsVerifierInCallUi extends Activity {
    TextView mCallNumber;
    Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.incall_screen, null);
        setContentView(view);
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        km.requestDismissKeyguard(this, null);

        mCallNumber = view.findViewById(R.id.incoming_call_number);
        if (mCallNumber == null) {
            finish();
            return;
        }

        mButton = view.findViewById(R.id.telecom_default_dialer_end_button);
        if (mButton == null) {
            finish();
            return;
        }

        mCallNumber.setText(getIntent().getStringExtra(EXTRA_CALL_NAME));
        mButton.setOnClickListener(v -> {
            boolean pass = false;
            if (km.isDeviceLocked()) {
                pass = true;
            }
            CtsIncomingCall.getInstance().setAcceptWhenLocked(pass);
            CtsIncomingCall.getInstance().disconnectCall();
            finish();
        });
    }
}
