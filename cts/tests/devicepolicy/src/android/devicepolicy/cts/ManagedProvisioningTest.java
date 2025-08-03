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

package android.devicepolicy.cts;

import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ManagedProvisioningTest {

    private static final String MANAGED_PROVISIONING_PKG = "com.android.managedprovisioning";

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    public void managedProvisioning_isInstalledInSystemImage() throws Exception {
        assertThat(TestApis.packages().find(MANAGED_PROVISIONING_PKG).hasSystemFlag()).isTrue();
    }

    @Test
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    public void managedProvisioning_hasLaunchDeviceManagerSetupPermission() {
        assertThat(TestApis.packages().find(MANAGED_PROVISIONING_PKG)
                .hasPermission(android.Manifest.permission.LAUNCH_DEVICE_MANAGER_SETUP)).isTrue();
    }
}
