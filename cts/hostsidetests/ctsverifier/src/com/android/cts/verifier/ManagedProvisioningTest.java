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

package com.android.cts.verifier;

import com.android.interactive.annotations.Interactive;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class ManagedProvisioningTest extends CtsVerifierTest {

    @Interactive
    @Test
    // SingleDisplayMode
    public void DeviceOwnerNegativeTest() throws Exception {
        requireFeatures("android.software.device_admin");
        excludeFeatures("android.software.lockscreen_disabled");

        runTest(".managedprovisioning.DeviceOwnerNegativeTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void DeviceOwnerPositiveTest() throws Exception {
        requireFeatures("android.software.device_admin");
        excludeFeatures("android.software.lockscreen_disabled");

        runTest(".managedprovisioning.DeviceOwnerPositiveTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void DeviceOwnerRequestingBugreportTest() throws Exception {
        requireFeatures("android.software.device_admin");

        runTest(".managedprovisioning.DeviceOwnerRequestingBugreportTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void ByodFlowTest() throws Exception {
        requireFeatures("android.software.managed_users", "android.software.device_admin");

        runTest(".managedprovisioning.ByodFlowTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void ByodProvisioningTest() throws Exception {
        requireFeatures("android.software.managed_users", "android.software.device_admin");

        runTest(".managedprovisioning.ByodProvisioningTestActivity");
    }
}
