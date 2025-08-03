/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;

/**
 * A helper class for the provisioning operation in primary profile after PO provisioning is done,
 * including removing the migrated account from primary profile, and sending
 * ACTION_MANAGED_PROFILE_PROVISIONED broadcast to the DPC in the primary profile.
 */
class PrimaryProfileFinalizationHelper {

    private final Account mMigratedAccount;
    private final String mMdmPackageName;
    private final UserHandle mManagedUserHandle;

    PrimaryProfileFinalizationHelper(Account migratedAccount, UserHandle managedUserHandle,
            String mdmPackageName) {
        mMigratedAccount = migratedAccount;
        mMdmPackageName = checkNotNull(mdmPackageName);
        mManagedUserHandle = checkNotNull(managedUserHandle);
    }

    void finalizeProvisioningInPrimaryProfile(Context context,
            DpcReceivedSuccessReceiver.Callback callback) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        devicePolicyManager.finalizeWorkProfileProvisioning(
                mManagedUserHandle, mMigratedAccount);

        if (callback != null) {
            callback.cleanup();
        }
    }
}
