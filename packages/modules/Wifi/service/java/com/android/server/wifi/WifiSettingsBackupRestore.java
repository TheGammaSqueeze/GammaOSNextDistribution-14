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

import android.util.Log;

import com.android.server.wifi.util.XmlUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to backup/restore data using the SettingsBackupAgent.
 * There are 2 symmetric API's exposed here:
 * 1. retrieveBackupDataFromSettingsConfigStore: Retrieve the configuration data to be backed up.
 * 2. retrieveSettingsFromBackupData: Restore the configuration using the provided data.
 * The byte stream to be backed up is XML encoded and versioned to migrate the data easily across
 * revisions.
 */
public class WifiSettingsBackupRestore {
    private static final String TAG = "WifiSettingsBackupRestore";

    public static final String XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA =
            "WifiSettingsBackData";

    private WifiSettingsConfigStore mWifiSettingsConfigStore;
    private Map<String, WifiSettingsConfigStore.Key> mRestoreSettingsMap = new HashMap<>();
    /**
     * Verbose logging flag.
     */
    private boolean mVerboseLoggingEnabled = false;

    public WifiSettingsBackupRestore(WifiSettingsConfigStore settingsConfigStore) {
        mWifiSettingsConfigStore = settingsConfigStore;
        for (WifiSettingsConfigStore.Key key : mWifiSettingsConfigStore.getAllKeys()) {
            mRestoreSettingsMap.put(key.key, key);
        }
    }

    /**
     * Retrieve a byte stream representing the data that needs to be backed up from the
     * provided WifiSettingsConfigStore.
     */
    public void retrieveBackupDataFromSettingsConfigStore(XmlSerializer out,
            ByteArrayOutputStream outputStream) {
        Map<String, Object> backupSettingsMap = new HashMap<>();
        try {
            out.setOutput(outputStream, StandardCharsets.UTF_8.name());
            // Start writing the XML stream.
            XmlUtil.writeNextSectionStart(out, XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA);
            XmlUtil.writeNextSectionStart(out,
                    WifiSettingsConfigStore.StoreData.XML_TAG_SECTION_HEADER);
            // Prepare the setting's map.
            for (WifiSettingsConfigStore.Key key
                    : mWifiSettingsConfigStore.getAllBackupRestoreKeys()) {
                backupSettingsMap.put(key.key, mWifiSettingsConfigStore.get(key));
            }
            XmlUtil.writeNextValue(out, WifiSettingsConfigStore.StoreData.XML_TAG_VALUES,
                    backupSettingsMap);

            XmlUtil.writeNextSectionEnd(out,
                    WifiSettingsConfigStore.StoreData.XML_TAG_SECTION_HEADER);
            XmlUtil.writeNextSectionEnd(out, XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error retrieving the backup data: " + e);
        }
    }

    /**
     * Parse out the wifi settings from the back up data and restore it.
     *
     * @param in the xml parser.
     * @param depth the current depth which the tag XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA.
     */
    public void restoreSettingsFromBackupData(XmlPullParser in, int depth) {
        if (in == null) {
            Log.e(TAG, "Invalid backup data received");
            return;
        }
        try {
            XmlUtil.gotoNextSectionWithName(in,
                    WifiSettingsConfigStore.StoreData.XML_TAG_SECTION_HEADER, depth + 1);
            Map<String, Object> values =
                    WifiSettingsConfigStore.StoreData.deserializeSettingsData(in, depth + 2);
            if (values != null) {
                for (String keyString : values.keySet()) {
                    if (mRestoreSettingsMap.containsKey(keyString)) {
                        if (mVerboseLoggingEnabled) {
                            Log.i(TAG, "Restored Settings: " + keyString
                                    + " with value: " + values.get(keyString));
                        }
                        mWifiSettingsConfigStore.put(mRestoreSettingsMap.get(keyString),
                                values.get(keyString));
                    } else {
                        Log.e(TAG, "Unexpcected Settings found: " + keyString
                                + " with value: " + values.get(keyString));
                    }
                }
            }
        } catch (XmlPullParserException | IOException | ClassCastException
                | IllegalArgumentException e) {
            Log.e(TAG, "Error parsing the backup data: " + e);
        }
    }

    /**
     * Enable verbose logging.
     *
     * @param verboseEnabled whether or not verbosity log level is enabled.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }
}
