/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Store data for storing B2B wifi roaming policies.
 * These are key (string) / value pairs that are stored in
 * WifiConfigStore.xml file in a separate section.
 */
public class WifiRoamingConfigStore {
    private static final String TAG = "WifiRoamingConfigStore";
    private static final int INVALID_ROAMING_MODE = -1;

    // To store roaming policies that are added by the device owner (DO) or
    // the profile owner of an organization owned device (COPE).
    private final Map<String, Integer> mDeviceAdminRoamingPolicies = new ArrayMap<>();
    // To store roaming policies that are added by non-admins.
    private final Map<String, Integer> mNonAdminRoamingPolicies = new ArrayMap<>();
    private final WifiConfigManager mWifiConfigManager;
    private boolean mHasNewDataToSerialize = false;

    public WifiRoamingConfigStore(@NonNull WifiConfigManager wifiConfigManager,
                                  @NonNull WifiConfigStore wifiConfigStore) {
        mWifiConfigManager = wifiConfigManager;
        // Register our data store.
        wifiConfigStore.registerStoreData(new StoreData());
    }

    /**
     * Trigger config store writes in the main wifi service looper's handler.
     */
    private void triggerSaveToStore() {
        mHasNewDataToSerialize = true;
        mWifiConfigManager.saveToStore(true);
    }

    /**
     * Add a roaming policy to the corresponding stored policies.
     *
     * @param ssid of the network on which policy to be added.
     * @param roamingMode denotes roaming mode value configured.
     * @param isDeviceOwner flag denoting whether API is called by the device owner.
     */
    public void addRoamingMode(@NonNull String ssid, @NonNull int roamingMode,
                               boolean isDeviceOwner) {
        if (isDeviceOwner) {
            mDeviceAdminRoamingPolicies.put(ssid, roamingMode);
        } else {
            mNonAdminRoamingPolicies.put(ssid, roamingMode);
        }
        triggerSaveToStore();
    }

    /**
     * Remove a roaming policy from the corresponding stored policies.
     *
     * @param ssid of the network on which policy to be removed.
     * @param isDeviceOwner flag denoting whether API is called by the device owner.
     */
    public void removeRoamingMode(@NonNull String ssid, boolean isDeviceOwner) {
        if (isDeviceOwner) {
            mDeviceAdminRoamingPolicies.remove(ssid);
        } else {
            mNonAdminRoamingPolicies.remove(ssid);
        }
        triggerSaveToStore();
    }

    /**
     * Retrieve roaming policy/mode for the given network name.
     *
     * @param ssid of the network which needs to be queried to fetch policy.
     * @return roaming mode stored in policy list,
     *         {@value WifiManager#ROAMING_MODE_NORMAL} if the key does not exist.
     */
    public @NonNull int getRoamingMode(@NonNull String ssid) {
        int roamingMode;
        roamingMode = mDeviceAdminRoamingPolicies.getOrDefault(ssid, INVALID_ROAMING_MODE);
        if (roamingMode == INVALID_ROAMING_MODE) {
            roamingMode = mNonAdminRoamingPolicies.getOrDefault(ssid,
                    WifiManager.ROAMING_MODE_NORMAL);
        }
        return roamingMode;
    }

    /**
     * Get all the network roaming policies configured.
     *
     * @param isDeviceOwner flag denoting whether API is called by the device owner.
     * @return Map of corresponding policies for the API caller,
     *         where key is ssid and value is roaming mode/policy configured for that ssid.
     */
    public Map<String, Integer> getPerSsidRoamingModes(boolean isDeviceOwner) {
        Map<String, Integer> roamingPolicies = new ArrayMap<>();
        if (isDeviceOwner) {
            roamingPolicies.putAll(mDeviceAdminRoamingPolicies);
        } else {
            roamingPolicies.putAll(mNonAdminRoamingPolicies);
        }
        return roamingPolicies;
    }

    /**
     * Dump all roaming policies for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println();
        pw.println("Dump of " + TAG);
        pw.println("DEVICE_ADMIN_POLICIES");
        for (Map.Entry<String, Integer> entry : mDeviceAdminRoamingPolicies.entrySet()) {
            pw.print(entry.getKey());
            pw.print("=");
            pw.println(entry.getValue());
        }
        pw.println();
        pw.println("NON_ADMIN_POLICIES");
        for (Map.Entry<String, Integer> entry : mNonAdminRoamingPolicies.entrySet()) {
            pw.print(entry.getKey());
            pw.print("=");
            pw.println(entry.getValue());
        }
    }

    /**
     * Store data for persisting the roaming policies data to config store.
     */
    private class StoreData implements WifiConfigStore.StoreData {
        private static final String XML_TAG_SECTION_HEADER = "RoamingPolicies";
        private static final String XML_TAG_DEVICE_ADMIN_POLICIES = "DeviceAdminPolicies";
        private static final String XML_TAG_NON_ADMIN_POLICIES = "NonAdminPolicies";

        @Override
        public void serializeData(XmlSerializer out,
                                  @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(out, XML_TAG_DEVICE_ADMIN_POLICIES, mDeviceAdminRoamingPolicies);
            XmlUtil.writeNextValue(out, XML_TAG_NON_ADMIN_POLICIES, mNonAdminRoamingPolicies);
            mHasNewDataToSerialize = false;
        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth,
                                    @WifiConfigStore.Version int version,
                                    @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (in == null) {
                mDeviceAdminRoamingPolicies.clear();
                mNonAdminRoamingPolicies.clear();
                return;
            }
            Map<String, Integer> deviceAdminPolicies = null, nonAdminPolicies = null;
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (TextUtils.isEmpty(valueName[0])) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (valueName[0]) {
                    case XML_TAG_DEVICE_ADMIN_POLICIES:
                        deviceAdminPolicies = (HashMap) value;
                        break;
                    case XML_TAG_NON_ADMIN_POLICIES:
                        nonAdminPolicies = (HashMap) value;
                        break;
                    default:
                        Log.w(TAG, "Ignoring unknown tag under " + XML_TAG_SECTION_HEADER + ": "
                                + valueName[0]);
                        break;
                }
            }
            if (deviceAdminPolicies != null) {
                mDeviceAdminRoamingPolicies.putAll(deviceAdminPolicies);
            }
            if (nonAdminPolicies != null) {
                mNonAdminRoamingPolicies.putAll(nonAdminPolicies);
            }
        }

        @Override
        public void resetData() {
            mDeviceAdminRoamingPolicies.clear();
            mNonAdminRoamingPolicies.clear();
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }

        @Override
        public String getName() {
            return XML_TAG_SECTION_HEADER;
        }

        @Override
        public @WifiConfigStore.StoreFileId int getStoreFileId() {
            // Shared general store.
            return WifiConfigStore.STORE_FILE_SHARED_GENERAL;
        }
    }
}
