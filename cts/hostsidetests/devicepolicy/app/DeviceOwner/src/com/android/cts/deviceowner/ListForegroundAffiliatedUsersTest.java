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

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeStaticMethodWithShellPermissions;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.UserHandle;
import android.util.Log;


import java.util.List;

public final class ListForegroundAffiliatedUsersTest extends BaseDeviceOwnerTest {

    private static final String TAG = ListForegroundAffiliatedUsersTest.class.getSimpleName();

    public void testListForegroundAffiliatedUsers_onlyForegroundUser() throws Exception {
        List<UserHandle> users = mDevicePolicyManager.listForegroundAffiliatedUsers();

        UserHandle currentUser = invokeStaticMethodWithShellPermissions(() -> getCurrentUser());
        Log.d(TAG, "currentUser: " + currentUser + " users: "  + users);

        assertWithMessage("foreground users").that(users).containsExactly(currentUser);
    }

    public void testListForegroundAffiliatedUsers_empty() throws Exception {
        List<UserHandle> users = mDevicePolicyManager.listForegroundAffiliatedUsers();
        Log.d(TAG, "users: "  + users);

        assertWithMessage("foreground users").that(users).isEmpty();
    }
}
