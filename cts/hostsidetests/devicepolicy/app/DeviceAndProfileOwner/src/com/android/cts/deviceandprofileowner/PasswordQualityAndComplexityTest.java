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

package com.android.cts.deviceandprofileowner;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;

/** Test combination of quality and complexity. */
public class PasswordQualityAndComplexityTest extends BaseDeviceAdminTest {

    private DevicePolicyManager mParentDpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mParentDpm = mDevicePolicyManager.getParentProfileInstance(ADMIN_RECEIVER_COMPONENT);
        clearQualityAndComplexity(mDevicePolicyManager);
        clearQualityAndComplexity(mParentDpm);
    }

    @Override
    protected void tearDown() throws Exception {
        clearQualityAndComplexity(mDevicePolicyManager);
        clearQualityAndComplexity(mParentDpm);
        super.tearDown();
    }

    public void testCannotSetComplexityWithQualityOnParent() {
        mDevicePolicyManager.setRequiredPasswordComplexity(
                PASSWORD_COMPLEXITY_NONE);
        mParentDpm.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_SOMETHING);

        assertThrows(IllegalStateException.class, () ->
                mDevicePolicyManager.setRequiredPasswordComplexity(
                        DevicePolicyManager.PASSWORD_COMPLEXITY_LOW));
    }

    public void testCannotSetQualityOnParentWithComplexity() {
        mDevicePolicyManager.setRequiredPasswordComplexity(
                DevicePolicyManager.PASSWORD_COMPLEXITY_LOW);

        assertThrows(IllegalStateException.class, () ->
                mParentDpm.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                        PASSWORD_QUALITY_SOMETHING));
    }

    private static void clearQualityAndComplexity(DevicePolicyManager dpm) {
        // Clear quality
        dpm.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_UNSPECIFIED);
        // Clear complexity
        dpm.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
    }
}
