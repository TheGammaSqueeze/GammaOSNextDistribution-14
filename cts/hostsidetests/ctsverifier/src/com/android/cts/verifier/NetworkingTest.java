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

import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class NetworkingTest extends CtsVerifierTest {

    @Interactive
    @Test
    // SingleDisplayMode
    @CddTest(requirements = "7.4.3/C-7-1")
    public void PresenceTest() throws Exception {
        requireFeatures("android.hardware.bluetooth_le");

        runTest(".presence.PresenceTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void BluetoothTest() throws Exception {
        // Has a bunch of subtests but all require a second device so probably best to not split
        requireFeatures("android.hardware.bluetooth");

        runTest(".bluetooth.BluetoothTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ConnectivityBackgroundTest() throws Exception {
        requireFeatures("android.hardware.wifi");

        runTest(".net.ConnectivityBackgroundTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void MultiNetworkConnectivityTest() throws Exception {
        requireFeatures("android.hardware.wifi:android.hardware.telephony");
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch");

        runTest(".net.MultiNetworkConnectivityTestActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void P2pTest() throws Exception {
        requireFeatures("android.hardware.wifi.direct");

        runTest(".p2p.P2pTestListActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void WifiTest() throws Exception {
        // This test could potentially be split up into sub-tests for calling directly
        requireFeatures("android.hardware.wifi");

        runTest(".wifi.TestListActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void WifiAwareTest() throws Exception {
        requireFeatures("android.hardware.wifi.aware");

        runTest(".wifiaware.TestListActivity");
    }
}
