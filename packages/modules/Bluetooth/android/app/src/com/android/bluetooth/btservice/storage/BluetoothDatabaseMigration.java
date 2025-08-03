/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.btservice.storage;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUtils;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class for regrouping the migration that occur when going mainline
 * @hide
 */
public final class BluetoothDatabaseMigration {
    private static final String TAG = "BluetoothDatabaseMigration";
    /**
     * @hide
     */
    public static boolean run(Context ctx, Cursor cursor) {
        boolean result = true;
        MetadataDatabase database = MetadataDatabase.createDatabaseWithoutMigration(ctx);
        while (cursor.moveToNext()) {
            try {
                final String primaryKey = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                String logKey = BluetoothUtils.toAnonymizedAddress(primaryKey);
                if (logKey == null) { // handle non device address
                    logKey = primaryKey;
                }

                Metadata metadata = new Metadata(primaryKey);

                metadata.migrated = fetchInt(cursor, "migrated") > 0;
                migrate_a2dpSupportsOptionalCodecs(cursor, logKey, metadata);
                migrate_a2dpOptionalCodecsEnabled(cursor, logKey, metadata);
                metadata.last_active_time = fetchInt(cursor, "last_active_time");
                metadata.is_active_a2dp_device = fetchInt(cursor, "is_active_a2dp_device") > 0;
                migrate_connectionPolicy(cursor, logKey, metadata);
                migrate_customizedMeta(cursor, metadata);

                database.insert(metadata);
                Log.d(TAG, "One item migrated: " + metadata);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to migrate one item: " + e);
                result = false;
            }
        }
        return result;
    }

    private static final List<Pair<Integer, String>> CONNECTION_POLICIES =
            Arrays.asList(
            new Pair(BluetoothProfile.A2DP, "a2dp_connection_policy"),
            new Pair(BluetoothProfile.A2DP_SINK, "a2dp_sink_connection_policy"),
            new Pair(BluetoothProfile.HEADSET, "hfp_connection_policy"),
            new Pair(BluetoothProfile.HEADSET_CLIENT, "hfp_client_connection_policy"),
            new Pair(BluetoothProfile.HID_HOST, "hid_host_connection_policy"),
            new Pair(BluetoothProfile.PAN, "pan_connection_policy"),
            new Pair(BluetoothProfile.PBAP, "pbap_connection_policy"),
            new Pair(BluetoothProfile.PBAP_CLIENT, "pbap_client_connection_policy"),
            new Pair(BluetoothProfile.MAP, "map_connection_policy"),
            new Pair(BluetoothProfile.SAP, "sap_connection_policy"),
            new Pair(BluetoothProfile.HEARING_AID, "hearing_aid_connection_policy"),
            new Pair(BluetoothProfile.HAP_CLIENT, "hap_client_connection_policy"),
            new Pair(BluetoothProfile.MAP_CLIENT, "map_client_connection_policy"),
            new Pair(BluetoothProfile.LE_AUDIO, "le_audio_connection_policy"),
            new Pair(BluetoothProfile.VOLUME_CONTROL, "volume_control_connection_policy"),
            new Pair(BluetoothProfile.CSIP_SET_COORDINATOR,
                "csip_set_coordinator_connection_policy"),
            new Pair(BluetoothProfile.LE_CALL_CONTROL, "le_call_control_connection_policy"),
            new Pair(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT,
                "bass_client_connection_policy"),
            new Pair(BluetoothProfile.BATTERY, "battery_connection_policy")
    );

    private static final List<Pair<Integer, String>> CUSTOMIZED_META_KEYS =
            Arrays.asList(
            new Pair(BluetoothDevice.METADATA_MANUFACTURER_NAME, "manufacturer_name"),
            new Pair(BluetoothDevice.METADATA_MODEL_NAME, "model_name"),
            new Pair(BluetoothDevice.METADATA_SOFTWARE_VERSION, "software_version"),
            new Pair(BluetoothDevice.METADATA_HARDWARE_VERSION, "hardware_version"),
            new Pair(BluetoothDevice.METADATA_COMPANION_APP, "companion_app"),
            new Pair(BluetoothDevice.METADATA_MAIN_ICON, "main_icon"),
            new Pair(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET, "is_untethered_headset"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON, "untethered_left_icon"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON, "untethered_right_icon"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_CASE_ICON, "untethered_case_icon"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY,
                "untethered_left_battery"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY,
                "untethered_right_battery"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY,
                "untethered_case_battery"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING,
                "untethered_left_charging"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING,
                "untethered_right_charging"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING,
                    "untethered_case_charging"),
            new Pair(BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI,
                    "enhanced_settings_ui_uri"),
            new Pair(BluetoothDevice.METADATA_DEVICE_TYPE, "device_type"),
            new Pair(BluetoothDevice.METADATA_MAIN_BATTERY, "main_battery"),
            new Pair(BluetoothDevice.METADATA_MAIN_CHARGING, "main_charging"),
            new Pair(BluetoothDevice.METADATA_MAIN_LOW_BATTERY_THRESHOLD,
                    "main_low_battery_threshold"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_LEFT_LOW_BATTERY_THRESHOLD,
                    "untethered_left_low_battery_threshold"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_RIGHT_LOW_BATTERY_THRESHOLD,
                    "untethered_right_low_battery_threshold"),
            new Pair(BluetoothDevice.METADATA_UNTETHERED_CASE_LOW_BATTERY_THRESHOLD,
                    "untethered_case_low_battery_threshold"),
            new Pair(BluetoothDevice.METADATA_SPATIAL_AUDIO, "spatial_audio"),
            new Pair(BluetoothDevice.METADATA_FAST_PAIR_CUSTOMIZED_FIELDS,
                    "fastpair_customized")
    );

    private static int fetchInt(Cursor cursor, String key) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(key));
    }

    private static void migrate_a2dpSupportsOptionalCodecs(Cursor cursor, String logKey,
            Metadata metadata) {
        final String key = "a2dpSupportsOptionalCodecs";
        final List<Integer> allowedValue =  new ArrayList<>(Arrays.asList(
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN,
                BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED));
        final int value = fetchInt(cursor, key);
        if (!allowedValue.contains(value)) {
            throw new IllegalArgumentException(logKey + ": Bad value for [" + key + "]: " + value);
        }
        metadata.a2dpSupportsOptionalCodecs = value;
    }

    private static void migrate_a2dpOptionalCodecsEnabled(Cursor cursor, String logKey,
            Metadata metadata) {
        final String key = "a2dpOptionalCodecsEnabled";
        final List<Integer> allowedValue =  new ArrayList<>(Arrays.asList(
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED));
        final int value = fetchInt(cursor, key);
        if (!allowedValue.contains(value)) {
            throw new IllegalArgumentException(logKey + ": Bad value for [" + key + "]: " + value);
        }
        metadata.a2dpOptionalCodecsEnabled = value;
    }

    private static void migrate_connectionPolicy(Cursor cursor, String logKey,
            Metadata metadata) {
        final List<Integer> allowedValue =  new ArrayList<>(Arrays.asList(
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED));
        for (Pair<Integer, String> p : CONNECTION_POLICIES) {
            final int policy = cursor.getInt(cursor.getColumnIndexOrThrow(p.second));
            if (allowedValue.contains(policy)) {
                metadata.setProfileConnectionPolicy(p.first, policy);
            } else {
                throw new IllegalArgumentException(logKey + ": Bad value for ["
                        + BluetoothProfile.getProfileName(p.first)
                        + "]: " + policy);
            }
        }
    }

    private static void migrate_customizedMeta(Cursor cursor, Metadata metadata) {
        for (Pair<Integer, String> p : CUSTOMIZED_META_KEYS) {
            final byte[] blob = cursor.getBlob(cursor.getColumnIndexOrThrow(p.second));
            // There is no specific pattern to check the custom meta data
            metadata.setCustomizedMeta(p.first, blob);
        }
    }

}
