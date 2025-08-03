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

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils.ACTION_DISABLE_SELF;

import static com.google.common.truth.Truth.assertThat;

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
 * A reference to a Device Owner.
 */
public final class DeviceOwner extends DevicePolicyController {

    private static final String TEST_APP_APP_COMPONENT_FACTORY =
            "com.android.bedstead.testapp.TestAppAppComponentFactory";

    DeviceOwner(UserReference user,
            Package pkg,
            ComponentName componentName) {
        super(user, pkg, componentName);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("DeviceOwner{");
        stringBuilder.append("user=").append(user());
        stringBuilder.append(", package=").append(pkg());
        stringBuilder.append(", componentName=").append(componentName());
        stringBuilder.append("}");

        return stringBuilder.toString();
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
                    && TEST_APP_APP_COMPONENT_FACTORY.equals(mPackage.appComponentFactory())) {
                removeTestApp();
            } else {
                throw e;
            }
        }

        Poll.forValue("Device Owner", () -> TestApis.devicePolicy().getDeviceOwner())
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
                throw new NeneException("Error removing device owner " + this, e);
            }
        }
    }

    private void removeTestApp() {
        // Special case for removing TestApp DPCs - this works even when not testOnly
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
    public boolean equals(Object obj) {
        if (!(obj instanceof DeviceOwner)) {
            return false;
        }

        DeviceOwner other = (DeviceOwner) obj;

        return Objects.equals(other.mUser, mUser)
                && Objects.equals(other.mPackage, mPackage)
                && Objects.equals(other.mComponentName, mComponentName);
    }

    /**
     * Sets the type of device owner that is on the device.
     *
     * @param deviceOwnerType The type of device owner that is managing the device which can be
     *                        {@link DeviceOwnerType#DEFAULT} as a default device owner or
     *                        {@link DeviceOwnerType#FINANCED} as a financed device owner.
     * @throws IllegalArgumentException If the device owner type is not one of
     *                                  {@link DeviceOwnerType#DEFAULT} or {@link
     *                                  DeviceOwnerType#FINANCED}.
     * @throws NeneException            When the device owner type fails to be set.
     */
    public void setType(int deviceOwnerType) {
        if (!isValidDeviceOwnerType(deviceOwnerType)) {
            throw new IllegalArgumentException("Device owner type provided is not valid: "
                    + deviceOwnerType);
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            devicePolicyManager.setDeviceOwnerType(mComponentName, deviceOwnerType);
        } catch (IllegalStateException e) {
            throw new NeneException("Failed to set the device owner type", e);
        }

        assertThat(getType()).isEqualTo(deviceOwnerType);
    }

    /**
     * Returns the device owner type set by {@link #setType(int)}. If it is not set, then
     * {@link DeviceOwnerType#DEFAULT} is returned instead.
     *
     * @throws NeneException         If retrieving the device owner type fails.
     * @throws IllegalStateException If the device owner type returned is not one of {@link
     *                               DeviceOwnerType#DEFAULT} or
     *                               {@link DeviceOwnerType#FINANCED}.
     */
    public int getType() {
        int deviceOwnerType;

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try {
            deviceOwnerType = devicePolicyManager.getDeviceOwnerType(mComponentName);
        } catch (IllegalStateException e) {
            throw new NeneException("Failed to retrieve the device owner type", e);
        }

        if (!isValidDeviceOwnerType(deviceOwnerType)) {
            throw new IllegalStateException("Device owner type returned is not valid: "
                    + deviceOwnerType);
        }
        return deviceOwnerType;
    }

    private boolean isValidDeviceOwnerType(int deviceOwnerType) {
        return (deviceOwnerType == DeviceOwnerType.DEFAULT)
                || (deviceOwnerType == DeviceOwnerType.FINANCED);
    }
}
