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

package com.android.managedprovisioning.preprovisioning;


import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SUPPORTED_MODES;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;

import com.google.android.setupdesign.util.DeviceHelper;

//TODO(b/181323689) Create tests for activity
//TODO(b/213177227) Extract business logic to a controller for testability
public class PreProvisioningActivityViaNfc extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (new SettingsFacade().isDeviceProvisioned(this)) {
            ProvisionLogger.loge("Device already provisioned, aborting NFC provisioning.");
            createDeviceAlreadySetupDialog().show();
        } else {
            Intent provisioningIntent = transformIntentToProvisioningIntent();
            if (provisioningIntent != null) {
                Intent provisioningIntentWithAdditionalExtras =
                        addAdditionalExtras(provisioningIntent);
                startActivity(provisioningIntentWithAdditionalExtras);
                finish();
            } else {
                ProvisionLogger.loge("NFC tag data is invalid.");
                createCantSetupDeviceDialog().show();
            }
        }
    }

    private Intent addAdditionalExtras(Intent provisioningIntent) {
        return new Intent(provisioningIntent).putExtra(
                EXTRA_PROVISIONING_SUPPORTED_MODES, FLAG_SUPPORTED_MODES_DEVICE_OWNER);
    }

    private Intent transformIntentToProvisioningIntent() {
        DevicePolicyManager devicePolicyManager =
                getApplicationContext().getSystemService(DevicePolicyManager.class);
        return devicePolicyManager.createProvisioningIntentFromNfcIntent(getIntent());
    }

    private AlertDialog createCantSetupDeviceDialog() {
        return new AlertDialog.Builder(this)
                .setMessage(R.string.contact_your_admin_for_help)
                .setTitle(R.string.cant_set_up_device)
                .setPositiveButton(android.R.string.ok, createDialogOnClickListener())
                .setCancelable(false)
                .create();
    }

    private AlertDialog createDeviceAlreadySetupDialog() {
        var context = getApplicationContext();
        return new AlertDialog.Builder(this)
                .setMessage(R.string.if_questions_contact_admin)
                .setTitle(context.getString(
                        R.string.device_already_set_up, DeviceHelper.getDeviceName(context)))
                .setPositiveButton(android.R.string.ok, createDialogOnClickListener())
                .setCancelable(false)
                .create();
    }

    private DialogInterface.OnClickListener createDialogOnClickListener() {
        return (dialog, id) -> finish();
    }
}
