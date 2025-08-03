/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi.p2p.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.wifi.cts.WifiFeature;
import android.net.wifi.cts.WifiJUnit4TestBase;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiP2pWfdInfoTest extends WifiJUnit4TestBase {

    private final int TEST_DEVICE_TYPE = WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE;
    private final boolean TEST_DEVICE_ENABLE_STATUS = true;
    private final boolean TEST_SESSION_STATUS = true;
    private final int TEST_CONTROL_PORT = 9999;
    private final int TEST_MAX_THROUGHPUT = 1024;
    private final boolean TEST_CONTENT_PROTECTION_SUPPORTED_STATUS = true;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue(WifiFeature.isWifiSupported(context));
    }

    @Test
    public void testWifiP2pWfdInfo() {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();

        info.setDeviceType(TEST_DEVICE_TYPE);
        info.setEnabled(TEST_DEVICE_ENABLE_STATUS);
        info.setSessionAvailable(true);
        info.setControlPort(TEST_CONTROL_PORT);
        info.setMaxThroughput(TEST_MAX_THROUGHPUT);
        info.setContentProtectionSupported(true);

        WifiP2pWfdInfo copiedInfo = new WifiP2pWfdInfo(info);
        assertEquals(TEST_DEVICE_TYPE, copiedInfo.getDeviceType());
        assertEquals(TEST_DEVICE_ENABLE_STATUS, copiedInfo.isEnabled());
        assertEquals(TEST_SESSION_STATUS, copiedInfo.isSessionAvailable());
        assertEquals(TEST_CONTROL_PORT, copiedInfo.getControlPort());
        assertEquals(TEST_MAX_THROUGHPUT, copiedInfo.getMaxThroughput());
        assertEquals(TEST_CONTENT_PROTECTION_SUPPORTED_STATUS,
                copiedInfo.isContentProtectionSupported());
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testWifiCoupledSink() {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();

        assertFalse(info.isCoupledSinkSupportedAtSink());
        info.setCoupledSinkSupportAtSink(true);
        assertTrue(info.isCoupledSinkSupportedAtSink());

        assertFalse(info.isCoupledSinkSupportedAtSource());
        info.setCoupledSinkSupportAtSource(true);
        assertTrue(info.isCoupledSinkSupportedAtSource());
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testWifiP2pWfdR2Info() {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();

        info.setR2DeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        assertEquals(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE, info.getR2DeviceType());
        assertTrue(info.isR2Supported());

        assertEquals(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE, info.getR2DeviceInfo());
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void testWifiP2pWfdDeviceInfo() {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();
        info.setDeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        assertEquals(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE, info.getDeviceInfo());
    }
}
