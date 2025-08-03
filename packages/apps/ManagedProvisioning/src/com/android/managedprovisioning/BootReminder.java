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
package com.android.managedprovisioning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.CrossProfileAppsPregrantControllerProvider;
import com.android.managedprovisioning.common.EncryptionControllerProvider;
import com.android.managedprovisioning.common.ManagedProfileChecker;
import com.android.managedprovisioning.common.UserProvisioningStateHelperProvider;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.provisioninglisteners.ManagedUserRemovalListener;
import com.android.managedprovisioning.provisioninglisteners.ManagedUserRemovalUtils;

/**
 * Boot listener for triggering reminders at boot time.
 */
public class BootReminder extends BroadcastReceiver {
    private final ManagedProfileChecker mManagedProfileChecker;
    private final UserProvisioningStateHelperProvider mUserProvisioningStateHelperProvider;
    private final EncryptionControllerProvider mEncryptionControllerProvider;
    private final CrossProfileAppsPregrantControllerProvider
            mCrossProfileAppsPregrantControllerProvider;
    private final ManagedUserRemovalUtils mManagedUserRemovalUtils;

    public BootReminder() {
        this(
                ManagedProfileChecker.DEFAULT,
                UserProvisioningStateHelperProvider.DEFAULT,
                EncryptionControllerProvider.DEFAULT,
                CrossProfileAppsPregrantControllerProvider.DEFAULT,
                new ManagedUserRemovalUtils());
    }

    @VisibleForTesting
    public BootReminder(
            ManagedProfileChecker managedProfileChecker,
            UserProvisioningStateHelperProvider userProvisioningStateHelperProvider,
            EncryptionControllerProvider encryptionControllerProvider,
            CrossProfileAppsPregrantControllerProvider crossProfileAppsPregrantControllerProvider,
            ManagedUserRemovalUtils managedUserRemovalUtils) {
        mManagedProfileChecker = managedProfileChecker;
        mUserProvisioningStateHelperProvider = userProvisioningStateHelperProvider;
        mEncryptionControllerProvider = encryptionControllerProvider;
        mCrossProfileAppsPregrantControllerProvider = crossProfileAppsPregrantControllerProvider;
        mManagedUserRemovalUtils = managedUserRemovalUtils;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mCrossProfileAppsPregrantControllerProvider
                .createCrossProfileAppsPregrantController(context)
                        .checkCrossProfileAppsPermissions();

        // For encryption flows during setup wizard, this acts as a backup to
        // PostEncryptionActivity in case the PackageManager has not yet written the package state
        // to disk when the reboot is triggered.
        mEncryptionControllerProvider.createEncryptionController(context).resumeProvisioning();

        resetPrimaryUserProvisioningStateIfNecessary(context);
    }

    /**
     * Resets the primary user provisioning state if a work profile was removed, but the state
     * hasn't been updated by {@link ManagedUserRemovalListener}.
     *
     * <p>This can happen if the device gets rebooted after removing the work profile, but before
     * {@link ManagedUserRemovalListener} receives the {@link Intent#ACTION_USER_REMOVED}
     * broadcast.
     */
    private void resetPrimaryUserProvisioningStateIfNecessary(Context context) {
        if (mManagedProfileChecker.hasManagedProfile(context)) {
            return;
        }
        UserProvisioningStateHelper userProvisioningStateHelper =
                mUserProvisioningStateHelperProvider.createUserProvisioningStateHelper(context);
        mManagedUserRemovalUtils
                .resetPrimaryUserProvisioningStateIfNecessary(context, userProvisioningStateHelper);
    }
}

