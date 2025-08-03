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

package com.android.managedprovisioning.provisioning;

import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.ProvisioningModeWrapperProvider.ProvisioningModeWrapper;

import com.google.android.setupdesign.util.DeviceHelper;

import org.junit.Test;

/**
 * Unit tests for {@link ProvisioningModeWrapperProvider}.
 */
@SmallTest
public class ProvisioningModeWrapperProviderTest {
    private static final ComponentName ADMIN = new ComponentName("com.foo", "com.bar");
    private static final String TEST_PROVIONING_ACTION = "android.app.action.TEST";
    private static final ProvisioningParams SIMPLE_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(TEST_PROVIONING_ACTION)
            .setDeviceAdminComponentName(ADMIN)
            .build();

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private final ProvisioningModeWrapperProvider mTestProvider =
            new ProvisioningModeWrapperProvider(SIMPLE_PARAMS);

    private final CharSequence TEST_DEVICE_NAME = DeviceHelper.getDeviceName(mContext);

    @Test
    public void testGetProvisioningModeWrapper_invalidMode() {
        assertThrows(IllegalStateException.class,
                () -> mTestProvider.getProvisioningModeWrapper(-1, TEST_DEVICE_NAME));
    }

    @Test
    public void testGetProvisioningModeWrapper_workProfile() {
        String expected = mContext.getString(R.string.work_profile_provisioning_summary);

        ProvisioningModeWrapper wrapper = mTestProvider.getProvisioningModeWrapper(
                PROVISIONING_MODE_WORK_PROFILE, TEST_DEVICE_NAME);

        assertWrapperAsExpected(wrapper, expected);
    }

    @Test
    public void testGetProvisioningModeWrapper_workProfileOnOrgOwnedDevice() {
        String expected = mContext.getString(R.string.cope_provisioning_summary, TEST_DEVICE_NAME);

        ProvisioningModeWrapper wrapper = mTestProvider.getProvisioningModeWrapper(
                PROVISIONING_MODE_WORK_PROFILE_ON_ORG_OWNED_DEVICE, TEST_DEVICE_NAME);

        assertWrapperAsExpected(wrapper, expected);
    }

    @Test
    public void testGetProvisioningModeWrapper_fullyManagedDevice_withoutSensorPermission() {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(TEST_PROVIONING_ACTION)
                .setDeviceAdminComponentName(ADMIN)
                .setDeviceOwnerPermissionGrantOptOut(true)
                .build();
        ProvisioningModeWrapperProvider provider = new ProvisioningModeWrapperProvider(params);
        String expected = mContext.getString(R.string.fully_managed_device_provisioning_summary,
                TEST_DEVICE_NAME);

        ProvisioningModeWrapper wrapper =
                provider.getProvisioningModeWrapper(PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                        TEST_DEVICE_NAME);

        assertWrapperAsExpected(wrapper, expected);
    }

    @Test
    public void testGetProvisioningModeWrapper_fullyManagedDevice_withSensorPermission() {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(TEST_PROVIONING_ACTION)
                .setDeviceAdminComponentName(ADMIN)
                .setDeviceOwnerPermissionGrantOptOut(false)
                .build();
        ProvisioningModeWrapperProvider provider = new ProvisioningModeWrapperProvider(params);
        String expected = mContext.getString(
                R.string.fully_managed_device_with_permission_control_provisioning_summary,
                TEST_DEVICE_NAME);

        ProvisioningModeWrapper wrapper =
                provider.getProvisioningModeWrapper(PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
                        TEST_DEVICE_NAME);

        assertWrapperAsExpected(wrapper, expected);
    }

    private void assertWrapperAsExpected(ProvisioningModeWrapper wrapper, String expected) {
        assertThat(wrapper.mSummary.value(mContext)).isEqualTo(expected);
    }
}
