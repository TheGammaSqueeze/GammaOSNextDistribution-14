/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.flags;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.DISABLE_RESOURCES_UPDATABILITY_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.permissions.CommonPermissions.WRITE_ALLOWLISTED_DEVICE_CONFIG;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class FlagsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String NAMESPACE = NAMESPACE_DEVICE_POLICY_MANAGER;
    private static final String KEY = DISABLE_RESOURCES_UPDATABILITY_FLAG;
    private static final String VALUE = Flags.ENABLED_VALUE;
    private static final String DIFFERENT_VALUE = "different-value";

    @Test
    @RequireSdkVersion(min = TIRAMISU)
    public void getFlagSyncEnabled_flagSyncIsEnabled_returnsTrue() {
        TestApis.flags().setFlagSyncEnabled(true);

        assertThat(TestApis.flags().getFlagSyncEnabled()).isTrue();
    }

    @Test
    @RequireSdkVersion(min = TIRAMISU)
    public void getFlagSyncEnabled_flagSyncIsNotEnabled_returnsFalse() {
        TestApis.flags().setFlagSyncEnabled(false);

        assertThat(TestApis.flags().getFlagSyncEnabled()).isFalse();
    }

    @Test
    @RequireSdkVersion(min = TIRAMISU)
    @EnsureHasPermission(WRITE_ALLOWLISTED_DEVICE_CONFIG)
    public void setFlagSyncEnabledFalse_bulkFlagUpdateDoesNotChangeValues() throws Exception {
        TestApis.flags().setFlagSyncEnabled(false);
        TestApis.flags().set(NAMESPACE, KEY, VALUE);

        DeviceConfig.setProperties(new DeviceConfig.Properties.Builder(NAMESPACE)
                .setString(KEY, DIFFERENT_VALUE)
                .build());

        assertThat(TestApis.flags().get(NAMESPACE, KEY)).isEqualTo(VALUE);
    }

    @Test
    @RequireSdkVersion(min = TIRAMISU)
    @EnsureHasPermission(WRITE_ALLOWLISTED_DEVICE_CONFIG)
    public void setFlagSyncEnabledTrue_bulkFlagUpdateDoesChangeValues() throws Exception {
        TestApis.flags().setFlagSyncEnabled(true);
        TestApis.flags().set(NAMESPACE, KEY, VALUE);

        DeviceConfig.setProperties(new DeviceConfig.Properties.Builder(NAMESPACE)
                .setString(KEY, DIFFERENT_VALUE)
                .build());

        assertThat(TestApis.flags().get(NAMESPACE, KEY)).isEqualTo(DIFFERENT_VALUE);
    }

    @Test
    public void set_flagValueIsSet() {
        TestApis.flags().set(NAMESPACE, KEY, VALUE);

        assertThat(TestApis.flags().get(NAMESPACE, KEY)).isEqualTo(VALUE);
    }

    @Test
    public void setEnabled_true_isEnabled() {
        TestApis.flags().setEnabled(NAMESPACE, KEY, true);

        assertThat(TestApis.flags().isEnabled(NAMESPACE, KEY)).isTrue();
    }

    @Test
    public void setEnabled_false_isNotEnabled() {
        TestApis.flags().setEnabled(NAMESPACE, KEY, false);

        assertThat(TestApis.flags().isEnabled(NAMESPACE, KEY)).isFalse();
    }
}
