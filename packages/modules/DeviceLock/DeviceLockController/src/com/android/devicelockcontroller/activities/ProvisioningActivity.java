/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.activities;

import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * The activity displayed when provisioning is in progress.
 */
public final class ProvisioningActivity extends AppCompatActivity {

    private static final String TAG = "ProvisioningActivity";

    static final String EXTRA_SHOW_PROVISION_FAILED_UI_ON_START =
            "com.android.devicelockcontroller.activities.extra.SHOW_PROVISION_FAILED_UI_ON_START";

    /**
     * An extra boolean set on the provisioning activity intent to signal that it should
     * show the provisioning failed screen on start.
     */
    public static final String EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START =
            "com.android.devicelockcontroller.activities.extra.SHOW_CRITICAL_PROVISION"
                    + "_FAILED_UI_ON_START";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.provisioning_activity);

        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
        }
        ProvisioningProgressViewModel viewModel = new ViewModelProvider(this).get(
                ProvisioningProgressViewModel.class);
        viewModel.getProvisioningProgressLiveData().observe(this, progress -> {
            ProgressFragment progressFragment = new ProgressFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, progressFragment)
                    .commit();
        });
        final Intent intent = getIntent();
        if (intent.getBooleanExtra(EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START, false)) {
            LogUtil.d(TAG, "showing critical provision failed ui");
            viewModel.setProvisioningProgress(
                    ProvisioningProgress.getMandatoryProvisioningFailedProgress(
                            POLICY_ENFORCEMENT_FAILED));
        } else if (intent.getBooleanExtra(EXTRA_SHOW_PROVISION_FAILED_UI_ON_START, false)) {
            LogUtil.d(TAG, "showing provision failed ui");
            // Using an unknown reason should not be allowed but is okay here because the UI will
            // not report to the server
            // TODO(b/321110148): Refactor reporting logic so that we don't need to do this
            viewModel.setProvisioningProgress(
                    ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(UNKNOWN_REASON));
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, new DevicePoliciesFragment())
                    .commit();
        }
    }
}
