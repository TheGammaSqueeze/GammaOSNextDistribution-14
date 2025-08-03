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

package com.android.bedstead.nene.devicepolicy;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils.ACTION_DISABLE_SELF;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.Retry;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.time.Duration;
import java.util.Objects;

/**
 * A reference to a Profile Owner.
 */
public final class ProfileOwner extends DevicePolicyController {

    private static final String TEST_APP_APP_COMPONENT_FACTORY =
            "com.android.bedstead.testapp.TestAppAppComponentFactory";

    ProfileOwner(UserReference user,
            Package pkg,
            ComponentName componentName) {
        super(user, pkg, componentName);
    }

    /** Returns whether the current profile is organization owned. */
    @TargetApi(R)
    public boolean isOrganizationOwned() {
        if (!Versions.meetsMinimumSdkVersionRequirement(R)) {
            return false;
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);
        return devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile();
    }

    /** Sets whether the current profile is organization owned. */
    @TargetApi(TIRAMISU)
    public void setIsOrganizationOwned(boolean isOrganizationOwned) {
        if (isOrganizationOwned() == isOrganizationOwned) {
            return; // Nothing to do
        }

        Versions.requireMinimumVersion(TIRAMISU);

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        UserReference user = TestApis.users().system();
        boolean userSetupComplete = user.getSetupComplete();
        try {
            user.setSetupComplete(false);

            try (PermissionContext p = TestApis.permissions().withPermission(
                    MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
                devicePolicyManager.setProfileOwnerOnOrganizationOwnedDevice(mComponentName,
                        isOrganizationOwned);
            }
        } finally {
            user.setSetupComplete(userSetupComplete);
        }
    }

    @Override
    public void remove() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)
                || TestApis.packages().instrumented().isInstantApp()) {
            removePreS();
            return;
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            devicePolicyManager.forceRemoveActiveAdmin(mComponentName, mUser.id());
        } catch (SecurityException e) {
            if (e.getMessage().contains("Attempt to remove non-test admin")
                    && TEST_APP_APP_COMPONENT_FACTORY.equals(mPackage.appComponentFactory())
                    && user().parent() == null) {
                removeTestApp();
            } else {
                throw e;
            }
        }

        Poll.forValue("Profile Owner",
                () -> TestApis.devicePolicy().getProfileOwner(mUser))
                .toBeNull()
                .errorOnFail().await();
    }

    private void removePreS() {
        try {
            ShellCommand.builderForUser(mUser, "dpm remove-active-admin")
                    .addOperand(componentName().flattenToShortString())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();
        } catch (AdbException e) {
            if (TEST_APP_APP_COMPONENT_FACTORY.equals(mPackage.appComponentFactory())
                    && user().parent() == null) {
                // We can't see why it failed so we'll try the test app version
                removeTestApp();
            } else {
                throw new NeneException("Error removing profile owner " + this, e);
            }
        }
    }

    private void removeTestApp() {
        // Special case for removing TestApp DPCs - this works even when not testOnly
        // but not on profiles
        Intent intent = new Intent(ACTION_DISABLE_SELF);
        intent.setComponent(new ComponentName(pkg().packageName(),
                "com.android.bedstead.testapp.TestAppBroadcastController"));
        Context context = TestApis.context().androidContextAsUser(mUser);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            // If the profile isn't ready then the broadcast won't be sent and the profile owner
            // will not be removed. So we can retry until the broadcast has been dealt with.
            Retry.logic(() -> {
                BlockingBroadcastReceiver b = new BlockingBroadcastReceiver(
                        TestApis.context().instrumentedContext());

                context.sendOrderedBroadcast(
                        intent, /* receiverPermission= */ null, b, /* scheduler= */
                        null, /* initialCode= */
                        Activity.RESULT_CANCELED, /* initialData= */ null, /* initialExtras= */
                        null);

                b.awaitForBroadcastOrFail(Duration.ofSeconds(30).toMillis());
                assertThat(b.getResultCode()).isEqualTo(Activity.RESULT_OK);
            }).timeout(Duration.ofMinutes(5)).runAndWrapException();

            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);

            Poll.forValue(() -> dpm.isRemovingAdmin(mComponentName, mUser.id()))
                    .toNotBeEqualTo(true)
                    .timeout(Duration.ofMinutes(5))
                    .errorOnFail()
                    .await();
        }

    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("ProfileOwner{");
        stringBuilder.append("user=").append(user());
        stringBuilder.append(", package=").append(pkg());
        stringBuilder.append(", componentName=").append(componentName());
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProfileOwner)) {
            return false;
        }

        ProfileOwner other = (ProfileOwner) obj;

        return Objects.equals(other.mUser, mUser)
                && Objects.equals(other.mPackage, mPackage)
                && Objects.equals(other.mComponentName, mComponentName);
    }
}
