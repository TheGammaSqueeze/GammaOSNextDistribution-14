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

package com.android.managedprovisioning.provisioninglisteners;

import static android.app.admin.DevicePolicyManager.STATE_USER_PROFILE_FINALIZED;

import android.app.admin.DevicePolicyManager;
import android.content.Context;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;

/**
 * Class containing utility methods related to removing a managed user.
 */
public class ManagedUserRemovalUtils {
    /**
     * Resets the primary user provisioning state if a work profile was removed.
     *
     * @see com.android.managedprovisioning.BootReminder
     */
    public void resetPrimaryUserProvisioningStateIfNecessary(
            Context context, UserProvisioningStateHelper userProvisioningStateHelper) {
        DevicePolicyManager devicePolicyManager =
                context.getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager.getUserProvisioningState() != STATE_USER_PROFILE_FINALIZED) {
            return;
        }
        ProvisionLogger.logi("Resetting primary user provisioning state.");
        userProvisioningStateHelper.resetPrimaryUserProvisioningState();
    }
}
