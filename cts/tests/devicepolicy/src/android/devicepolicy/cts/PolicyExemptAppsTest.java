/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_ADMINS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PolicyExemptAppsTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS)
    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyExemptApps")
    public void getPolicyExemptAppsCanOnlyBeDefinedOnAutomotiveBuilds() throws Exception {
        assertWithMessage("list of policy-exempt apps")
                .that(sDevicePolicyManager.getPolicyExemptApps())
                .isEmpty();
    }
}
