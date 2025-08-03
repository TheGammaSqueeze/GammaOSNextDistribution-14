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

package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link WifiCommandUtil}. */
@RunWith(JUnit4.class)
public class WifiCommandUtilTest {

    @Test
    public void testParseScanResults() {
        String output = readTestFile("wifi_scan_results_output_1.txt");
        assertNotNull(output);

        List<WifiCommandUtil.ScanResult> results = WifiCommandUtil.parseScanResults(output);
        assertEquals(6, results.size());
        assertEquals("f4:2e:7f:22:e2:30", results.get(0).getInfo("BSSID"));
        assertEquals("5600", results.get(0).getInfo("Frequency"));
        assertEquals("-60(0:-62/1:-65)", results.get(0).getInfo("RSSI"));
        assertEquals("2.872", results.get(0).getInfo("Age(sec)"));
        assertEquals("wl-uhd-atc1-atc123-5", results.get(0).getInfo("SSID"));
        assertEquals("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]", results.get(0).getInfo("Flags"));
    }

    @Test
    public void testResolveNetworkType() {
        assertEquals("wep", WifiCommandUtil.resolveNetworkType("[WEP][ESS]"));
        assertEquals("owe", WifiCommandUtil.resolveNetworkType("[OWE][ESS]"));
        assertEquals(
                "wpa2", WifiCommandUtil.resolveNetworkType("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"));
        assertEquals("wpa3", WifiCommandUtil.resolveNetworkType("[RSN-PSK+SAE-CCMP][ESS]"));
        assertEquals("open", WifiCommandUtil.resolveNetworkType("[ESS]"));
    }

    @Test
    public void testParseWifiInfo() {
        String output = readTestFile("wifi_status_output_1.txt");
        assertNotNull(output);

        Map<String, String> wifiInfo = WifiCommandUtil.parseWifiInfo(output);
        assertNotNull(wifiInfo);
        assertEquals("GoogleGuest", wifiInfo.get("ssid"));
        assertEquals("48:2f:6b:ac:b2:31", wifiInfo.get("bssid"));
        assertEquals("573", wifiInfo.get("linkSpeed"));
        assertEquals("-60", wifiInfo.get("rssi"));
        assertEquals("82:f2:40:f1:51:be", wifiInfo.get("macAddress"));
    }

    private String readTestFile(String filename) {
        InputStream inputStream =
                WifiCommandUtilTest.class.getResourceAsStream(
                        File.separator + "device" + File.separator + filename);
        String output = null;
        try {
            output = StreamUtil.getStringFromStream(inputStream);
        } catch (IOException e) {
            CLog.e("Unable to read contents of output file: " + filename);
        }
        return output;
    }
}
