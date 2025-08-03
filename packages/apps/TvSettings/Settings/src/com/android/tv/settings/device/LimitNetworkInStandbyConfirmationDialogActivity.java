/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tv.settings.device;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.PowerManager;

import com.android.tv.settings.R;

/**
 * Activity presenting a confirmation dialog for disabling Low Power Standby.
 */
public class LimitNetworkInStandbyConfirmationDialogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new AlertDialog.Builder(this)
                .setTitle(R.string.limit_network_in_standby_confirm_title)
                .setMessage(R.string.limit_network_in_standby_confirm_message)
                .setPositiveButton(R.string.settings_confirm, (v, w) -> confirm())
                .setNegativeButton(R.string.settings_cancel, (v, w) -> finish())
                .setOnDismissListener((d) -> finish())
                .create()
                .show();
    }

    private void confirm() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        powerManager.setLowPowerStandbyEnabled(false);
        finish();
    }
}
