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

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver;

@ApiTest(apis={"android.telecom.InCallService"})
@CddTest(requirement="3.2.3.5/C-2-2")
public class TelecomDefaultDialerTestActivity extends PassFailButtons.Activity {
    private Button mSetDefaultDialer;
    private Button mConfirmLockScreen;
    private Button mLockDevice;

    private ImageView mStep1Status;
    private ImageView mStep2Status;
    private ImageView mStep3Status;
    private ImageView mStep4Status;

    private final String LOCK_SCREEN_ACTION = "com.android.settings.SETUP_LOCK_SCREEN";
    private final int REQUEST_DIALER_ROLE = 1;
    private final int REQUEST_LOCK_SCREEN = 2;
    private final int REQUEST_ADD_ADMIN = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.telecom_default_dialer, null);
        setContentView(view);
        setInfoResources(R.string.telecom_default_dialer_test_title,
                R.string.telecom_default_dialer_test_info, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mSetDefaultDialer = view.findViewById(
                R.id.telecom_default_dialer_set_third_party_dialer_button);
        if (mSetDefaultDialer == null) {
            finish();
            return;
        }

        mConfirmLockScreen = view.findViewById(
                R.id.telecom_confirm_lock_screen);
        if (mConfirmLockScreen == null) {
            finish();
            return;
        }

        mLockDevice = view.findViewById(
                R.id.telecom_default_dialer_lock_button);
        if (mLockDevice == null) {
            finish();
            return;
        }

        mStep1Status = view.findViewById(R.id.step_1_status);
        mStep2Status = view.findViewById(R.id.step_2_status);
        mStep3Status = view.findViewById(R.id.step_3_status);
        mStep4Status = view.findViewById(R.id.step_4_status);
        mStep1Status.setImageResource(R.drawable.fs_indeterminate);
        mStep2Status.setImageResource(R.drawable.fs_indeterminate);
        mStep3Status.setImageResource(R.drawable.fs_indeterminate);
        mStep3Status.setImageResource(R.drawable.fs_indeterminate);

        mSetDefaultDialer.setOnClickListener(v -> {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm.getDefaultDialerPackage().equals(getPackageName())) {
                Toast.makeText(this, R.string.voicemail_default_dialer_already_set,
                        Toast.LENGTH_SHORT).show();
                mStep1Status.setImageResource(R.drawable.fs_good);
                return;
            }
            Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
            startActivityForResult(intent, REQUEST_DIALER_ROLE);
        });

        mConfirmLockScreen.setOnClickListener(v -> {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km.isKeyguardSecure()) {
                mStep2Status.setImageResource(R.drawable.fs_good);
                return;
            }
            Intent intent = new Intent(LOCK_SCREEN_ACTION);
            startActivityForResult(intent, REQUEST_LOCK_SCREEN);
        });

        mLockDevice.setOnClickListener(v -> {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    DeviceAdminTestReceiver.getReceiverComponentName());
            startActivityForResult(intent, REQUEST_ADD_ADMIN);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_DIALER_ROLE) {
            if (resultCode == RESULT_OK) {
                mStep1Status.setImageResource(R.drawable.fs_good);
            } else {
                mStep1Status.setImageResource(R.drawable.fs_error);
            }
        }
        if (requestCode == REQUEST_LOCK_SCREEN) {
            if (resultCode == RESULT_FIRST_USER) {
                mStep2Status.setImageResource(R.drawable.fs_good);
            } else {
                mStep2Status.setImageResource(R.drawable.fs_error);
            }
        }
        if (requestCode == REQUEST_ADD_ADMIN) {
            if (resultCode == RESULT_OK) {
                DevicePolicyManager dpm = (DevicePolicyManager)
                        getSystemService(DEVICE_POLICY_SERVICE);
                mStep3Status.setImageResource(R.drawable.fs_good);
                dpm.lockNow();
            } else {
                mStep3Status.setImageResource(R.drawable.fs_error);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (CtsIncomingCall.getInstance().isAcceptWhenLocked()) {
            mStep4Status.setImageResource(R.drawable.fs_good);
            getPassButton().setEnabled(true);
        } else {
            mStep4Status.setImageResource(R.drawable.fs_error);
        }
    }
}
