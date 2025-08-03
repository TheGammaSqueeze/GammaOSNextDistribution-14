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

import com.android.tradefed.log.LogUtil.CLog;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A utility class that can parse wifi command outputs. */
public class WifiCommandUtil {

    public static final Pattern SSID_PATTERN =
            Pattern.compile(".*WifiInfo:.*SSID:\\s*\"([^,]*)\".*");
    public static final Pattern BSSID_PATTERN = Pattern.compile(".*WifiInfo:.*BSSID:\\s*([^,]*).*");
    public static final Pattern LINK_SPEED_PATTERN =
            Pattern.compile(
                    ".*WifiInfo:.*(?<!\\bTx\\s\\b|\\bRx\\s\\b)Link speed:\\s*([^,]*)Mbps.*");
    public static final Pattern RSSI_PATTERN = Pattern.compile(".*WifiInfo:.*RSSI:\\s*([^,]*).*");
    public static final Pattern MAC_ADDRESS_PATTERN =
            Pattern.compile(".*WifiInfo:.*MAC:\\s*([^,]*).*");

    /** Represents a wifi network containing its related info. */
    public static class ScanResult {
        private Map<String, String> scanInfo = new LinkedHashMap<>();
        /** Adds an info related to the current wifi network. */
        private void addInfo(String key, String value) {
            scanInfo.put(key, value);
        }
        /** Returns the wifi network information related to the key */
        public String getInfo(String infoKey) {
            return scanInfo.get(infoKey);
        }

        @Override
        public String toString() {
            return scanInfo.toString();
        }
    }

    /**
     * Parse the `wifi list-scan-results` command output and returns a list of {@link ScanResult}s.
     *
     * @param input Output of the list-scan-results command to parse.
     * @return List of {@link ScanResult}s.
     */
    public static List<ScanResult> parseScanResults(String input) {
        // EXAMPLE INPUT:

        // BSSID             Frequency   RSSI           Age(sec)   SSID         Flags
        // 20:9c:b4:16:09:f2   5580    -70(0:-70/1:-79)  4.731   GoogleGuest-2  [ESS]
        // 20:9c:b4:16:09:f0   5580    -69(0:-70/1:-78)  4.732   Google-A       [WPA2-PSK-CCMP][ESS]
        // 20:9c:b4:16:09:f1   5580    -69(0:-69/1:-78)  4.731   GoogleGuest    [ESS]

        if (input == null || input.isEmpty()) {
            return new LinkedList<>();
        }
        List<ScanResult> results = new LinkedList<>();

        // Figure out the column names from the first line of the output
        String[] scanResultLines = input.split("\n");
        String[] columnNames = scanResultLines[0].split("\\s+");

        // All lines after that should be related to wifi networks
        for (int i = 1; i < scanResultLines.length; i++) {
            if (scanResultLines[i].trim().isEmpty()) {
                continue;
            }
            String[] columnValues = scanResultLines[i].split("\\s+");
            if (columnValues.length != columnNames.length) {
                CLog.d(
                        "Skipping scan result since one or more of its value is undetermined:\n%s",
                        scanResultLines[i]);
            } else {
                ScanResult scanResult = new ScanResult();
                for (int j = 0; j < columnNames.length; j++) {
                    scanResult.addInfo(columnNames[j], columnValues[j]);
                }
                results.add(scanResult);
            }
        }
        return results;
    }

    /** Resolves the network type given the flags returned from list-scan-result cmd. */
    public static String resolveNetworkType(String flags) {
        if (flags.contains("WEP")) {
            return "wep";
        } else if (flags.contains("OWE")) {
            return "owe";
        } else if (flags.contains("WPA2")) {
            return "wpa2";
        } else if (flags.contains("SAE")) {
            return "wpa3";
        } else {
            return "open";
        }
    }

    /**
     * Parse the 'wifi status' output and returns a map of info about connected wifi network.
     *
     * @param input Output of the 'wifi status' command to parse.
     * @return a map of info about the connected network.
     */
    public static Map<String, String> parseWifiInfo(String input) {
        Map<String, String> wifiInfo = new LinkedHashMap<>();

        Matcher ssidMatcher = SSID_PATTERN.matcher(input);
        if (ssidMatcher.find()) {
            wifiInfo.put("ssid", ssidMatcher.group(1));
        }

        Matcher bssidMatcher = BSSID_PATTERN.matcher(input);
        if (bssidMatcher.find()) {
            wifiInfo.put("bssid", bssidMatcher.group(1));
        }

        // TODO: also gather ip address, which is not availabled in the 'wifi status" output

        Matcher linkSpeedMatcher = LINK_SPEED_PATTERN.matcher(input);
        if (linkSpeedMatcher.find()) {
            wifiInfo.put("linkSpeed", linkSpeedMatcher.group(1));
        }

        Matcher rssiMatcher = RSSI_PATTERN.matcher(input);
        if (rssiMatcher.find()) {
            wifiInfo.put("rssi", rssiMatcher.group(1));
        }

        Matcher macAddressMatcher = MAC_ADDRESS_PATTERN.matcher(input);
        if (macAddressMatcher.find()) {
            wifiInfo.put("macAddress", macAddressMatcher.group(1));
        }

        return wifiInfo;
    }
}
