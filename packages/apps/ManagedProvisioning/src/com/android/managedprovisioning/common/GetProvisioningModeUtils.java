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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_GET_PROVISIONING_MODE;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * A utility class for checking for the existence of and starting DPC's activity with intent filter
 * with action {@link DevicePolicyManager#ACTION_GET_PROVISIONING_MODE}.
 */
public class GetProvisioningModeUtils {

    /**
     * Returns {@code true} if the DPC has an intent filter with action {@link
     * DevicePolicyManager#ACTION_GET_PROVISIONING_MODE}.
     */
    public boolean isGetProvisioningModeActivityResolvable(
            Context context, ProvisioningParams params) {
        return getGetProvisioningModeIntent(params)
                .resolveActivity(context.getPackageManager()) != null;
    }

    /**
     * Starts the DPC activity with action @link DevicePolicyManager#ACTION_GET_PROVISIONING_MODE},
     * if one exists.
     *
     * @param parentActivity the activity which starts the DPC screen
     * @param params the {@link ProvisioningParams} used for provisioning
     * @param additionalExtras a bundle of additional extras to be included for the DPC to read
     * @param requestCode the request code to be used when started by {@code parentActivity}
     * @return {@code true} if the activity was successfully started
     */
    public boolean startGetProvisioningModeActivityIfResolved(
            Activity parentActivity, ProvisioningParams params, Bundle additionalExtras,
            int requestCode, TransitionHelper transitionHelper) {
        if (!isGetProvisioningModeActivityResolvable(parentActivity, params)) {
            return false;
        }
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent(params);
        getProvisioningModeIntent.putExtras(additionalExtras);
        if (parentActivity.getIntent() != null) {
            WizardManagerHelper.copyWizardManagerExtras(
                    parentActivity.getIntent(), getProvisioningModeIntent);
        }
        transitionHelper.startActivityForResultWithTransition(
                parentActivity, getProvisioningModeIntent, requestCode);
        return true;
    }

    private Intent getGetProvisioningModeIntent(ProvisioningParams params) {
        final Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(params.inferDeviceAdminPackageName());
        return intentGetMode;
    }
}
