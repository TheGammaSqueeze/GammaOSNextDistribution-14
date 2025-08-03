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

import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class FeaturesTest extends CtsVerifierTest {

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ClipboardPreviewTest() throws Exception {
        // I really like this test - it's clever!
        excludeFeatures(
                "android.hardware.type.watch",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".clipboard.ClipboardPreviewTestActivity");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(apis = "android.companion.CompanionDeviceManager#associate")
    public void CompanionDeviceTest() throws Exception {
        requireFeatures("android.software.companion_device_setup");

        runTest(".companion.CompanionDeviceTestActivity");
    }

    @Test
    @Interactive
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.companion.CompanionDeviceManager#startObservingDevicePresence",
                "android.companion.CompanionDeviceManager#stopObservingDevicePresence"
            })
    public void CompanionDeviceServiceTest() throws Exception {
        requireFeatures("android.software.companion_device_setup");

        runTest(".companion.CompanionDeviceServiceTestActivity");
    }
}
