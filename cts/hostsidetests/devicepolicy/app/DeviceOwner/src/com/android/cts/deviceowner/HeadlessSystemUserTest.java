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
package com.android.cts.deviceowner;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.UserIdInt;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

//TODO(b/174859111): move to automotive specific module
/**
 * Device owner tests specific for devices that use
 * {@link android.os.UserManager#isHeadlessSystemUserMode()}.
 */
public final class HeadlessSystemUserTest extends BaseDeviceOwnerTest {

    private static final String TAG = HeadlessSystemUserTest.class.getSimpleName();

    // To be used in cases where it needs to test the DPM of the current user (as
    // mDevicePolicyManager wraps calls to user 0's DeviceOwner DPM);
    private DevicePolicyManager mCurrentUserDpm;

    private UserManager mUserManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCurrentUserDpm = mContext.getSystemService(DevicePolicyManager.class);
        mUserManager = mContext.getSystemService(UserManager.class);

        Log.d(TAG, "setUp(): userId=" + mUserId);

    }

    public void testProfileOwnerIsSetOnCurrentUser() {
        ComponentName admin = mCurrentUserDpm.getProfileOwner();

        assertProfileOwner(admin, mUserId);
    }

    public void testProfileOwnerIsSetOnNewUser() throws Exception {
        UserInfo user = null;
        try {
            user = callWithShellPermissionIdentity(() -> mUserManager
                    .createUser("testProfileOwnerIsSetOnNewUser", /* flags= */ 0));
            assertWithMessage("new user").that(user).isNotNull();
            Log.d(TAG, "Created user " + user.toFullString());
            final int userId = user.id;

            // Must try a couple times as PO is asynchronously set after user is created.
            // TODO(b/178102911): use a callback instead
            eventually(() -> {
                Context newUserContext = mContext.createContextAsUser(UserHandle.of(userId),
                        /* flags=*/ 0);
                DevicePolicyManager newUserDpm = newUserContext
                        .getSystemService(DevicePolicyManager.class);
                assertProfileOwner(newUserDpm.getProfileOwner(), userId);
            });

        } finally {
            if (user != null) {
                final int userId = user.id;
                Log.d(TAG, "Removing user " + userId);
                boolean removed = callWithShellPermissionIdentity(
                        () -> mUserManager.removeUser(userId));
                assertWithMessage("user %s removed", userId).that(removed).isTrue();
            }
        }
    }

    private void assertProfileOwner(ComponentName admin, @UserIdInt int userId) {
        assertWithMessage("Component %s is profile owner for user %s", admin, userId)
                .that(admin).isEqualTo(getWho());
    }
}
