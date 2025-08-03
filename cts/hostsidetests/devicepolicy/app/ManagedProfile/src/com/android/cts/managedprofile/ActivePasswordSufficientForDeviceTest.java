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
package com.android.cts.managedprofile;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

import static org.testng.Assert.assertThrows;

import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.compatibility.common.util.SystemUtil;

public class ActivePasswordSufficientForDeviceTest extends BaseManagedProfileTest {
    int mParentUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mParentUserId = getParentUserId();
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                PASSWORD_QUALITY_UNSPECIFIED);
        mDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
        mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
    }

    public void testActivePsswordSufficientForDevice_notCallableOnProfileInstance() {
        assertThrows(SecurityException.class,
                () -> mDevicePolicyManager.isActivePasswordSufficientForDeviceRequirement());
    }

    public void testActivePsswordSufficientForDevice_NoPolicy() {
        assertTrue(mParentDevicePolicyManager.isActivePasswordSufficientForDeviceRequirement());
    }

    public void testActivePsswordSufficientForDevice_UnmetParentPolicy() {
        mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_MEDIUM);
        assertFalse(mParentDevicePolicyManager.isActivePasswordSufficientForDeviceRequirement());
    }

    public void testActivePsswordSufficientForDevice_IrrelevantProfilePolicy() {
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_NUMERIC);
        mDevicePolicyManager.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, 4);
        mDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_MEDIUM);
        assertTrue(mParentDevicePolicyManager.isActivePasswordSufficientForDeviceRequirement());
    }

    public void testActivePsswordSufficientForDevice_UnifiedPassword_BothPolicies() {
        changeUserCredential("1234", null, mParentUserId);
        try {
            mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                    PASSWORD_QUALITY_ALPHANUMERIC);
            mDevicePolicyManager.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, 4);
            mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_LOW);

            assertFalse(mDevicePolicyManager.isActivePasswordSufficient());
            assertTrue(mParentDevicePolicyManager.isActivePasswordSufficientForDeviceRequirement());
        } finally {
            mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                    PASSWORD_QUALITY_UNSPECIFIED);
            mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
            changeUserCredential(null, "1234", mParentUserId);
        }
    }

    private int getParentUserId() {
        var myUserId = Process.myUserHandle().getIdentifier();
        var profiles = UserManager.get(mContext).getUserProfiles();
        var otherProfileIds = profiles.stream()
                .mapToInt(UserHandle::getIdentifier)
                .filter(userId -> userId != myUserId)
                .toArray();
        assertEquals("zero or multiple profiles, cannot find parent", 1, otherProfileIds.length);
        return otherProfileIds[0];
    }

    //TODO: reinstate test once LockSettingsShellCommand allows setting password for profiles
    // that have unified challenge b/176230819
    private void toTestActivePsswordSufficientForDevice_SeparatePassword_BothPolicies() {
        final int myUserId = UserHandle.getUserId(Process.myUid());
        changeUserCredential("1234", null, mParentUserId);
        changeUserCredential("asdf12", "1234", myUserId); // This currently fails
        try {
            mDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_LOW);
            mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_HIGH);

            assertTrue(mDevicePolicyManager.isActivePasswordSufficient());
            assertFalse(
                    mParentDevicePolicyManager.isActivePasswordSufficientForDeviceRequirement());
        } finally {
            mDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
            mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
            changeUserCredential(null, "1234", mParentUserId);
        }
    }

    private void changeUserCredential(String newCredential, String oldCredential, int userId) {
        final String oldCredentialArgument = (oldCredential == null || oldCredential.isEmpty()) ? ""
                : ("--old " + oldCredential);
        if (newCredential != null && !newCredential.isEmpty()) {
            String commandOutput = SystemUtil.runShellCommand(String.format(
                    "cmd lock_settings set-password --user %d %s %s", userId, oldCredentialArgument,
                    newCredential));
            if (!commandOutput.startsWith("Password set to")) {
                fail("Failed to set user credential: " + commandOutput);
            }
        } else {
            String commandOutput = SystemUtil.runShellCommand(String.format(
                    "cmd lock_settings clear --user %d %s", userId, oldCredentialArgument));
            if (!commandOutput.startsWith("Lock credential cleared")) {
                fail("Failed to clear user credential: " + commandOutput);
            }
        }
    }
}
