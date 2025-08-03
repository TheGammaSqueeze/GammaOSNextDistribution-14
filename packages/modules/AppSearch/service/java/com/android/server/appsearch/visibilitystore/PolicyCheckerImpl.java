/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appsearch.visibilitystore;

import android.annotation.NonNull;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;

/**
 * An implementation of {@link PolicyChecker} that uses {@link DevicePolicyManager}.
 *
 * @hide
 */
public class PolicyCheckerImpl implements PolicyChecker {
    private final Context mUserContext;
    private final DevicePolicyManager mDevicePolicyManager;

    public PolicyCheckerImpl(@NonNull Context userContext) {
        mUserContext = userContext;
        mDevicePolicyManager = userContext.getSystemService(DevicePolicyManager.class);
    }

    /**
     * Returns whether the calling package has managed profile contacts search access.
     *
     * <p>Note, this method calls {@link DevicePolicyManager#hasManagedProfileContactsAccess} which
     * is only supported on U+ and therefore returns false on devices below U.
     */
    @Override
    public boolean doesCallerHaveManagedProfileContactsAccess(@NonNull String callingPackageName) {
        // CP2 checks managed profile caller id and contacts access to determine whether a user can
        // access enterprise contacts. Accessible fields are restricted to match the allowed fields
        // for caller id access which is more limited than contacts access.
        // https://cs.android.com/android/platform/superproject/main/+/main:packages/providers/ContactsProvider/src/com/android/providers/contacts/enterprise/EnterprisePolicyGuard.java;l=81;drc=242bb9f25b210fbfe36a384088221b54b2602b34
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // This api is only supported on U+
            return mDevicePolicyManager.hasManagedProfileContactsAccess(mUserContext.getUser(),
                    callingPackageName);
        }
        // Below U, we should call
        // DevicePolicyManager#getCrossProfileContactsSearchDisabled(UserHandle) to check if the
        // caller is allowed access. This api is hidden however, so we can only return false to
        // avoid allowing access when it should not be allowed.
        return false;
    }
}
