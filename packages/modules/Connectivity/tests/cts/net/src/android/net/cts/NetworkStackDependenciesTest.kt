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

package android.net.cts

import android.content.pm.PackageManager
import android.net.cts.util.CtsNetUtils
import android.net.wifi.WifiManager
import android.os.Build
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Basic tests for APIs used by the network stack module.
 */
class NetworkStackDependenciesTest {
    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.Q)
    fun testGetFrequency() {
        // WifiInfo#getFrequency was missing a CTS test in Q: this test is run as part of MTS on Q
        // devices to ensure it behaves correctly.
        val context = InstrumentationRegistry.getInstrumentation().getContext()
        assumeTrue("This test only applies to devices that support wifi",
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI))
        val wifiManager = context.getSystemService(WifiManager::class.java)
        assertNotNull(wifiManager, "Device supports wifi but there is no WifiManager")

        CtsNetUtils(context).ensureWifiConnected()
        val wifiInfo = wifiManager.getConnectionInfo()
        // The NetworkStack can handle any value of getFrequency; unknown frequencies will not be
        // classified in metrics, but this is expected behavior. It is only important that the
        // method does not crash. Still verify that the frequency is positive
        val frequency = wifiInfo.getFrequency()
        assertTrue(frequency > 0, "Frequency must be > 0")
    }
}