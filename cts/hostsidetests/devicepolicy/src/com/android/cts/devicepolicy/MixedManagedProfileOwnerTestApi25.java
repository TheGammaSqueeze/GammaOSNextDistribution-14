/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import static com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.FEATURE_MANAGED_USERS;

import com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.RequiresAdditionalFeatures;
import com.android.cts.devicepolicy.annotations.PermissionsTest;

import org.junit.Test;

/**
 * Set of tests for managed profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTestApi25.
 */
// We need managed users to be supported in order to create a profile of the user owner.
@RequiresAdditionalFeatures({FEATURE_MANAGED_USERS})
public class MixedManagedProfileOwnerTestApi25 extends DeviceAndProfileOwnerTestApi25 {
    private int mParentUserId = -1;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        removeTestUsers();
        mParentUserId = mPrimaryUserId;
        createManagedProfile();
    }

    private void createManagedProfile() throws Exception {
        mUserId = createManagedProfile(mParentUserId);
        switchUser(mParentUserId);
        startUserAndWait(mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mUserId);
        setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        startUserAndWait(mUserId);
    }

    @Override
    public void tearDown() throws Exception {
        removeUser(mUserId);

        super.tearDown();
    }

    @Override
    @PermissionsTest
    @Test
    public void testPermissionGrantPreMApp() throws Exception {
        super.testPermissionGrantPreMApp();
    }
}
