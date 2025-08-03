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

package com.android.managedprovisioning.finalization;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;

import com.android.managedprovisioning.ManagedProvisioningScreens;
import com.android.managedprovisioning.ScreenManager;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderHelper;
import com.android.managedprovisioning.common.SharedPreferences;
import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * A controller that contains business logic for the finalization forwarder.
 *
 * @see FinalizationForwarderActivity
 */
public final class FinalizationForwarderController {

    private final DeviceManagementRoleHolderHelper mRoleHolderHelper;
    private final Ui mUi;
    private final SharedPreferences mSharedPreferences;
    private final ScreenManager mScreenManager;

    interface Ui {
        void startRoleHolderFinalization();

        void startPlatformProvidedProvisioningFinalization();
    }

    public FinalizationForwarderController(
            DeviceManagementRoleHolderHelper roleHolderHelper,
            Ui ui,
            SharedPreferences sharedPreferences,
            ScreenManager screenManager) {
        mRoleHolderHelper = requireNonNull(roleHolderHelper);
        mUi = requireNonNull(ui);
        mSharedPreferences = requireNonNull(sharedPreferences);
        mScreenManager = requireNonNull(screenManager);
    }

    /**
     * Returns a new {@link Intent} instance which resolves to the AOSP ManagedProvisioning
     * finalization.
     */
    public Intent createPlatformProvidedProvisioningFinalizationIntent(
            Context context, @Nullable Intent parentActivityIntent) {
        requireNonNull(context);
        Intent intent = new Intent(context, mScreenManager.getActivityClassForScreen(
                ManagedProvisioningScreens.FINALIZATION_INSIDE_SUW));
        if (parentActivityIntent != null) {
            WizardManagerHelper.copyWizardManagerExtras(parentActivityIntent, intent);
        }
        return intent;
    }

    /**
     * Returns a new {@link Intent} instance which resolves to the device management role holder
     * finalization.
     */
    public Intent createRoleHolderFinalizationIntent(
            Context context, @Nullable Intent parentActivityIntent) {
        requireNonNull(context);
        return mRoleHolderHelper.createRoleHolderFinalizationIntent(parentActivityIntent);
    }

    /**
     * Starts the relevant {@link Ui} method depending on whether the provisioning finalization
     * should be handled by the device management role holder or AOSP ManagedProvisioning.
     */
    public void forwardFinalization(Context context) {
        requireNonNull(context);
        if (mSharedPreferences.isProvisioningFlowDelegatedToRoleHolder()) {
            mUi.startRoleHolderFinalization();
        } else {
            mUi.startPlatformProvidedProvisioningFinalization();
        }
    }
}
