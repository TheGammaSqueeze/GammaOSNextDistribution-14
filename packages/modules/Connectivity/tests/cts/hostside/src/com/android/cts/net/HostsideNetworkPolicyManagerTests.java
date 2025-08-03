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

package com.android.cts.net;

import static com.android.cts.net.arguments.InstrumentationArguments.ARG_WAIVE_BIND_PRIORITY;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class HostsideNetworkPolicyManagerTests extends HostsideNetworkTestCase {
    @Before
    public void setUp() throws Exception {
        uninstallPackage(TEST_APP2_PKG, false);
        installPackage(TEST_APP2_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP2_PKG, true);
    }

    @Test
    public void testIsUidNetworkingBlocked_withUidNotBlocked() throws Exception {
        runDeviceTests(TEST_PKG,
                TEST_PKG + ".NetworkPolicyManagerTest",
                "testIsUidNetworkingBlocked_withUidNotBlocked");
    }

    @Test
    public void testIsUidNetworkingBlocked_withSystemUid() throws Exception {
        runDeviceTests(TEST_PKG,
                TEST_PKG + ".NetworkPolicyManagerTest", "testIsUidNetworkingBlocked_withSystemUid");
    }

    @Test
    public void testIsUidNetworkingBlocked_withDataSaverMode() throws Exception {
        runDeviceTests(TEST_PKG,
                TEST_PKG + ".NetworkPolicyManagerTest",
                "testIsUidNetworkingBlocked_withDataSaverMode");
    }

    @Test
    public void testIsUidNetworkingBlocked_withRestrictedNetworkingMode() throws Exception {
        runDeviceTests(TEST_PKG,
                TEST_PKG + ".NetworkPolicyManagerTest",
                "testIsUidNetworkingBlocked_withRestrictedNetworkingMode");
    }

    @Test
    public void testIsUidNetworkingBlocked_withPowerSaverMode() throws Exception {
        runDeviceTests(TEST_PKG,
                TEST_PKG + ".NetworkPolicyManagerTest",
                "testIsUidNetworkingBlocked_withPowerSaverMode");
    }

    @Test
    public void testIsUidRestrictedOnMeteredNetworks() throws Exception {
        runDeviceTests(TEST_PKG,
                TEST_PKG + ".NetworkPolicyManagerTest", "testIsUidRestrictedOnMeteredNetworks");
    }

    // TODO(b/321848487): Annotate with @RequiresFlagsEnabled to mirror the device-side test.
    @Test
    public void testIsUidNetworkingBlocked_whenInBackground() throws Exception {
        runDeviceTestsWithArgs(TEST_PKG, TEST_PKG + ".NetworkPolicyManagerTest",
                "testIsUidNetworkingBlocked_whenInBackground",
                Map.of(ARG_WAIVE_BIND_PRIORITY, "true"));
    }
}
