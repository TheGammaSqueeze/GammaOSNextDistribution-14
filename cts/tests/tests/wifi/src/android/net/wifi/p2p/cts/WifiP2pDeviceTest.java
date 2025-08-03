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

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.test.AndroidTestCase;

import androidx.test.filters.SdkSuppress;

import com.android.wifi.flags.Flags;

public class WifiP2pDeviceTest extends AndroidTestCase {

    public void testDefaultWpsMethodSupportCheck() {
        WifiP2pDevice dev = new WifiP2pDevice();

        assertFalse(dev.wpsPbcSupported());
        assertFalse(dev.wpsDisplaySupported());
        assertFalse(dev.wpsKeypadSupported());
    }

    public void testDefaultDeviceCapabilityCheck() {
        WifiP2pDevice dev = new WifiP2pDevice();

        assertFalse(dev.isServiceDiscoveryCapable());
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testGetVendorElements() {
        WifiP2pDevice dev = new WifiP2pDevice();
        dev.getVendorElements();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    public void testGetIpAddress() {
        WifiP2pDevice dev = new WifiP2pDevice();
        dev.getIpAddress();
    }
}
