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

import static android.content.Intent.ACTION_USER_REMOVED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ManagedProfileChecker;
import com.android.managedprovisioning.common.UserProvisioningStateHelperProvider;
import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;

/**
 * This receiver is invoked after a managed user is removed.
 */
public class ManagedUserRemovalListener extends BroadcastReceiver {
    private final ManagedProfileChecker mManagedProfileChecker;
    private final UserProvisioningStateHelperProvider mUserProvisioningStateHelperProvider;
    private final ManagedUserRemovalUtils mManagedUserRemovalUtils;

    public ManagedUserRemovalListener() {
        this(
                ManagedProfileChecker.DEFAULT,
                UserProvisioningStateHelperProvider.DEFAULT,
                new ManagedUserRemovalUtils());
    }

    @VisibleForTesting
    public ManagedUserRemovalListener(
            ManagedProfileChecker managedProfileChecker,
            UserProvisioningStateHelperProvider userProvisioningStateHelperProvider,
            ManagedUserRemovalUtils managedUserRemovalUtils) {
        mManagedProfileChecker = managedProfileChecker;
        mUserProvisioningStateHelperProvider = userProvisioningStateHelperProvider;
        mManagedUserRemovalUtils = managedUserRemovalUtils;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_USER_REMOVED.equals(intent.getAction())) {
            return;
        }
        if (mManagedProfileChecker.hasManagedProfile(context)) {
            return;
        }
        UserProvisioningStateHelper userProvisioningStateHelper =
                mUserProvisioningStateHelperProvider.createUserProvisioningStateHelper(context);
        mManagedUserRemovalUtils
                .resetPrimaryUserProvisioningStateIfNecessary(context, userProvisioningStateHelper);
    }
}
