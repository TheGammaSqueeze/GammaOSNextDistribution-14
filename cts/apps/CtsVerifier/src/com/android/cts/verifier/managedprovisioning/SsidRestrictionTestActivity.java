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

package com.android.cts.verifier.managedprovisioning;

import static com.android.cts.verifier.managedprovisioning.CommandReceiverActivity.COMMAND_SET_SSID_ALLOWLIST;
import static com.android.cts.verifier.managedprovisioning.CommandReceiverActivity.COMMAND_SET_SSID_DENYLIST;
import static com.android.cts.verifier.managedprovisioning.CommandReceiverActivity.EXTRA_COMMAND;
import static com.android.cts.verifier.managedprovisioning.CommandReceiverActivity.EXTRA_VALUE;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test class to verify setting WiFi SSID restriction.
 */
public class SsidRestrictionTestActivity extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ssid_restriction);
        setPassFailButtonClickListeners();
        setButtonClickListeners();
    }

    private void setButtonClickListeners() {
        findViewById(R.id.ssid_allowlist_set_button)
                .setOnClickListener(v -> setSsidAllowlist());
        findViewById(R.id.ssid_denylist_set_button)
                .setOnClickListener(v -> setSsidDenylist());
        findViewById(R.id.go_button).setOnClickListener(v -> goToSettings());
    }

    private void setSsidAllowlist() {
        TextView ssidRestrictionView = findViewById(R.id.ssid_restriction_edit_text);
        String ssid = ssidRestrictionView.getText().toString();
        if (ssid.isEmpty()) {
            Toast.makeText(this, R.string.device_owner_ssid_restriction_removing_toast,
                    Toast.LENGTH_SHORT).show();
        }

        Intent intent = new Intent(this, CommandReceiverActivity.class)
                .putExtra(EXTRA_COMMAND, COMMAND_SET_SSID_ALLOWLIST)
                .putExtra(EXTRA_VALUE, ssid);
        startActivity(intent);
    }

    private void setSsidDenylist() {
        TextView ssidRestrictionView = findViewById(R.id.ssid_restriction_edit_text);
        String ssid = ssidRestrictionView.getText().toString();
        if (ssid.isEmpty()) {
            Toast.makeText(this, R.string.device_owner_ssid_restriction_removing_toast,
                    Toast.LENGTH_SHORT).show();
        }

        Intent intent = new Intent(this, CommandReceiverActivity.class)
                .putExtra(EXTRA_COMMAND, COMMAND_SET_SSID_DENYLIST)
                .putExtra(EXTRA_VALUE, ssid);
        startActivity(intent);
    }

    private void goToSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        startActivity(intent);
    }
}
