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

package com.google.snippet.wifi.direct;

import android.net.MacAddress;
import android.net.wifi.p2p.WifiP2pConfig;

import org.json.JSONException;
import org.json.JSONObject;

/** Deserializes JSONObject into data objects defined in Android API. */
public class JsonDeserializer {
    private static final String PERSISTENT_MODE = "persistent_mode";
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String GROUP_CLIENT_IP_PROVISIONING_MODE =
            "group_client_ip_provisioning_mode";
    private static final String GROUP_OPERATING_BAND = "group_operating_band";
    private static final String GROUP_OPERATING_FREQUENCY = "group_operating_frequency";
    private static final String NETWORK_NAME = "network_name";
    private static final String PASSPHRASE = "passphrase";

    private JsonDeserializer() {
    }

    /** Converts Python dict to android.net.wifi.p2p.WifiP2pConfig. */
    public static WifiP2pConfig jsonToWifiP2pConfig(JSONObject jsonObject) throws JSONException {
        WifiP2pConfig.Builder builder = new WifiP2pConfig.Builder();
        if (jsonObject.has(PERSISTENT_MODE)) {
            builder.enablePersistentMode(jsonObject.getBoolean(PERSISTENT_MODE));
        }
        if (jsonObject.has(DEVICE_ADDRESS)) {
            builder.setDeviceAddress(MacAddress.fromString(jsonObject.getString(DEVICE_ADDRESS)));
        }
        if (jsonObject.has(GROUP_CLIENT_IP_PROVISIONING_MODE)) {
            builder.setGroupClientIpProvisioningMode(
                    jsonObject.getInt(GROUP_CLIENT_IP_PROVISIONING_MODE));
        }
        if (jsonObject.has(GROUP_OPERATING_BAND)) {
            builder.setGroupOperatingBand(jsonObject.getInt(GROUP_OPERATING_BAND));
        }
        if (jsonObject.has(GROUP_OPERATING_FREQUENCY)) {
            builder.setGroupOperatingFrequency(jsonObject.getInt(GROUP_OPERATING_FREQUENCY));
        }
        if (jsonObject.has(NETWORK_NAME)) {
            builder.setNetworkName(jsonObject.getString(NETWORK_NAME));
        }
        if (jsonObject.has(PASSPHRASE)) {
            builder.setPassphrase(jsonObject.getString(PASSPHRASE));
        }
        return builder.build();
    }
}

