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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.task.interactacrossprofiles.CrossProfileAppsSnapshot;
import com.android.managedprovisioning.task.nonrequiredapps.SystemAppsSnapshot;

/**
 * Controller for actions to be performed after DO/PO provisioning has completed.
 */
// TODO(b/178711424): move this into the framework.
public class ProvisioningCompletedController {

    private final SystemAppsSnapshot mSystemAppsSnapshot;
    private final CrossProfileAppsSnapshot mCrossProfileAppsSnapshot;
    private final UserManager mUserManager;
    private final int mUserId;
    private final String mProvisioningAction;
    private final boolean mLeaveAllSystemAppsEnabled;

    public ProvisioningCompletedController(
            int userId, String action, boolean leaveAllSystemAppsEnabled, Context context) {
        this(
                userId,
                action,
                leaveAllSystemAppsEnabled,
                new SystemAppsSnapshot(context),
                new CrossProfileAppsSnapshot(context),
                context.getSystemService(UserManager.class));
    }

    @VisibleForTesting
    ProvisioningCompletedController(
            int userId,
            String action,
            boolean leaveAllSystemAppsEnabled,
            SystemAppsSnapshot systemAppsSnapshot,
            CrossProfileAppsSnapshot crossProfileAppsSnapshot,
            UserManager userManager) {
        mUserId = userId;
        mProvisioningAction = requireNonNull(action);
        mLeaveAllSystemAppsEnabled = leaveAllSystemAppsEnabled;
        mSystemAppsSnapshot = requireNonNull(systemAppsSnapshot);
        mCrossProfileAppsSnapshot = requireNonNull(crossProfileAppsSnapshot);
        mUserManager = requireNonNull(userManager);
    }

    public void run() {
        if (mUserId == UserHandle.USER_NULL) {
            ProvisionLogger.loge("Missing userId.");
            return;
        }
        if (!mLeaveAllSystemAppsEnabled) {
            mSystemAppsSnapshot.takeNewSnapshot(mUserId);
        }

        if (mProvisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
            UserInfo parentUser = mUserManager.getProfileParent(mUserId);
            if (parentUser == null) {
                ProvisionLogger.loge("A managed profile must have a parent profile.");
            }
            mCrossProfileAppsSnapshot.takeNewSnapshot(parentUser.id);
        }
    }
}
