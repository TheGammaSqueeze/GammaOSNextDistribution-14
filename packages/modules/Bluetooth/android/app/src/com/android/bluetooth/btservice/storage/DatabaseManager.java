/*
 * Copyright 2019 The Android Open Source Project
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
import android.bluetooth.BluetoothA2dp.OptionalCodecsPreferenceStatus;
import android.bluetooth.BluetoothA2dp.OptionalCodecsSupportStatus;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothStatusCodes;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.Flags;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.EvictingQueue;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The active device manager is responsible to handle a Room database
 * for Bluetooth persistent data.
 */
public class DatabaseManager {
    private static final String TAG = "BluetoothDatabase";

    private final AdapterService mAdapterService;
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private final Object mDatabaseLock = new Object();
    private @GuardedBy("mDatabaseLock") MetadataDatabase mDatabase = null;
    private boolean mMigratedFromSettingsGlobal = false;

    @VisibleForTesting
    final Map<String, Metadata> mMetadataCache = new HashMap<>();
    private final Semaphore mSemaphore = new Semaphore(1);
    private static final int METADATA_CHANGED_LOG_MAX_SIZE = 20;
    private final EvictingQueue<String> mMetadataChangedLog;

    private static final int LOAD_DATABASE_TIMEOUT = 500; // milliseconds
    private static final int MSG_LOAD_DATABASE = 0;
    private static final int MSG_UPDATE_DATABASE = 1;
    private static final int MSG_DELETE_DATABASE = 2;
    private static final int MSG_CLEAR_DATABASE = 100;
    private static final String LOCAL_STORAGE = "LocalStorage";

    private static final String
            LEGACY_HEADSET_PRIORITY_PREFIX = "bluetooth_headset_priority_";
    private static final String
            LEGACY_A2DP_SINK_PRIORITY_PREFIX = "bluetooth_a2dp_sink_priority_";
    private static final String
            LEGACY_A2DP_SRC_PRIORITY_PREFIX = "bluetooth_a2dp_src_priority_";
    private static final String LEGACY_A2DP_SUPPORTS_OPTIONAL_CODECS_PREFIX =
            "bluetooth_a2dp_supports_optional_codecs_";
    private static final String LEGACY_A2DP_OPTIONAL_CODECS_ENABLED_PREFIX =
            "bluetooth_a2dp_optional_codecs_enabled_";
    private static final String
            LEGACY_INPUT_DEVICE_PRIORITY_PREFIX = "bluetooth_input_device_priority_";
    private static final String
            LEGACY_MAP_PRIORITY_PREFIX = "bluetooth_map_priority_";
    private static final String
            LEGACY_MAP_CLIENT_PRIORITY_PREFIX = "bluetooth_map_client_priority_";
    private static final String
            LEGACY_PBAP_CLIENT_PRIORITY_PREFIX = "bluetooth_pbap_client_priority_";
    private static final String
            LEGACY_SAP_PRIORITY_PREFIX = "bluetooth_sap_priority_";
    private static final String
            LEGACY_PAN_PRIORITY_PREFIX = "bluetooth_pan_priority_";
    private static final String
            LEGACY_HEARING_AID_PRIORITY_PREFIX = "bluetooth_hearing_aid_priority_";

    /** Constructor of the DatabaseManager */
    public DatabaseManager(AdapterService service) {
        mAdapterService = Objects.requireNonNull(service, "Adapter service cannot be null");
        mMetadataChangedLog = EvictingQueue.create(METADATA_CHANGED_LOG_MAX_SIZE);
    }

    class DatabaseHandler extends Handler {
        DatabaseHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOAD_DATABASE: {
                    synchronized (mDatabaseLock) {
                        List<Metadata> list;
                        try {
                            list = mDatabase.load();
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Unable to open database: " + e);
                            mDatabase = MetadataDatabase
                                    .createDatabaseWithoutMigration(mAdapterService);
                            list = mDatabase.load();
                        }
                        compactLastConnectionTime(list);
                        cacheMetadata(list);
                    }
                    break;
                }
                case MSG_UPDATE_DATABASE: {
                    Metadata data = (Metadata) msg.obj;
                    synchronized (mDatabaseLock) {
                        mDatabase.insert(data);
                    }
                    break;
                }
                case MSG_DELETE_DATABASE: {
                    String address = (String) msg.obj;
                    synchronized (mDatabaseLock) {
                        mDatabase.delete(address);
                    }
                    break;
                }
                case MSG_CLEAR_DATABASE: {
                    synchronized (mDatabaseLock) {
                        mDatabase.deleteAll();
                    }
                    break;
                }
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                Log.e(TAG, "Received intent with null action");
                return;
            }
            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);
                    if (!mMigratedFromSettingsGlobal
                            && state == BluetoothAdapter.STATE_TURNING_ON) {
                        migrateSettingsGlobal();
                    }
                    break;
                }
            }
        }
    };

    /** Process a change in the bonding state for a device */
    public void handleBondStateChanged(BluetoothDevice device, int fromState, int toState) {
        if (mHandlerThread == null) {
            Log.w(TAG, "handleBondStateChanged call but DatabaseManager cleaned up");
            return;
        }
        mHandler.post(() -> bondStateChanged(device, toState));
    }

    void bondStateChanged(BluetoothDevice device, int state) {
        synchronized (mMetadataCache) {
            String address = device.getAddress();
            if (state != BluetoothDevice.BOND_NONE) {
                if (mMetadataCache.containsKey(address)) {
                    return;
                }
                createMetadata(address, false);
            } else {
                Metadata metadata = mMetadataCache.get(address);
                if (metadata != null) {
                    mMetadataCache.remove(address);
                    deleteDatabase(metadata);
                }
            }
        }
    }

    boolean isValidMetaKey(int key) {
        if (key >= 0 && key <= BluetoothDevice.getMaxMetadataKey()) {
            return true;
        }
        Log.w(TAG, "Invalid metadata key " + key);
        return false;
    }

    /**
     * Set customized metadata to database with requested key
     */
    @VisibleForTesting
    public boolean setCustomMeta(BluetoothDevice device, int key, byte[] newValue) {
        if (device == null) {
            Log.e(TAG, "setCustomMeta: device is null");
            return false;
        }
        if (!isValidMetaKey(key)) {
            Log.e(TAG, "setCustomMeta: meta key invalid " + key);
            return false;
        }

        String address = device.getAddress();
        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                createMetadata(address, false);
            }
            Metadata data = mMetadataCache.get(address);
            byte[] oldValue = data.getCustomizedMeta(key);
            if (oldValue != null && Arrays.equals(oldValue, newValue)) {
                Log.v(TAG, "setCustomMeta: metadata not changed.");
                return true;
            }
            logManufacturerInfo(device, key, newValue);
            logMetadataChange(data, "setCustomMeta key=" + key);
            data.setCustomizedMeta(key, newValue);

            updateDatabase(data);
        }
        mAdapterService.metadataChanged(address, key, newValue);
        return true;
    }

    /**
     * Get customized metadata from database with requested key
     */
    public byte[] getCustomMeta(BluetoothDevice device, int key) {
        if (device == null) {
            Log.e(TAG, "getCustomMeta: device is null");
            return null;
        }
        if (!isValidMetaKey(key)) {
            Log.e(TAG, "getCustomMeta: meta key invalid " + key);
            return null;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                Log.d(TAG, "getCustomMeta: device " + device + " is not in cache");
                return null;
            }

            Metadata data = mMetadataCache.get(address);
            return data.getCustomizedMeta(key);
        }
    }

    /**
     * Set audio policy metadata to database with requested key
     */
    @VisibleForTesting
    public boolean setAudioPolicyMetadata(BluetoothDevice device,
            BluetoothSinkAudioPolicy policies) {
        if (device == null) {
            Log.e(TAG, "setAudioPolicyMetadata: device is null");
            return false;
        }

        String address = device.getAddress();
        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                createMetadata(address, false);
            }
            Metadata data = mMetadataCache.get(address);
            AudioPolicyEntity entity = data.audioPolicyMetadata;
            entity.callEstablishAudioPolicy = policies.getCallEstablishPolicy();
            entity.connectingTimeAudioPolicy = policies.getActiveDevicePolicyAfterConnection();
            entity.inBandRingtoneAudioPolicy = policies.getInBandRingtonePolicy();

            updateDatabase(data);
            return true;
        }
    }

    /**
     * Get audio policy metadata from database with requested key
     */
    @VisibleForTesting
    public BluetoothSinkAudioPolicy getAudioPolicyMetadata(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getAudioPolicyMetadata: device is null");
            return null;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                Log.d(TAG, "getAudioPolicyMetadata: device " + device + " is not in cache");
                return null;
            }

            AudioPolicyEntity entity = mMetadataCache.get(address).audioPolicyMetadata;
            return new BluetoothSinkAudioPolicy.Builder()
                    .setCallEstablishPolicy(entity.callEstablishAudioPolicy)
                    .setActiveDevicePolicyAfterConnection(entity.connectingTimeAudioPolicy)
                    .setInBandRingtonePolicy(entity.inBandRingtoneAudioPolicy)
                    .build();
        }
    }

    /**
     * Set the device profile connection policy
     *
     * @param device {@link BluetoothDevice} wish to set
     * @param profile The Bluetooth profile; one of {@link BluetoothProfile#HEADSET},
     * {@link BluetoothProfile#HEADSET_CLIENT}, {@link BluetoothProfile#A2DP},
     * {@link BluetoothProfile#A2DP_SINK}, {@link BluetoothProfile#HID_HOST},
     * {@link BluetoothProfile#PAN}, {@link BluetoothProfile#PBAP},
     * {@link BluetoothProfile#PBAP_CLIENT}, {@link BluetoothProfile#MAP},
     * {@link BluetoothProfile#MAP_CLIENT}, {@link BluetoothProfile#SAP},
     * {@link BluetoothProfile#HEARING_AID}, {@link BluetoothProfile#LE_AUDIO},
     * {@link BluetoothProfile#VOLUME_CONTROL}, {@link BluetoothProfile#CSIP_SET_COORDINATOR},
     * {@link BluetoothProfile#LE_AUDIO_BROADCAST_ASSISTANT},
     * @param newConnectionPolicy the connectionPolicy to set; one of
     * {@link BluetoothProfile.CONNECTION_POLICY_UNKNOWN},
     * {@link BluetoothProfile.CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile.CONNECTION_POLICY_ALLOWED}
     */
    @VisibleForTesting
    public boolean setProfileConnectionPolicy(BluetoothDevice device, int profile,
            int newConnectionPolicy) {
        if (device == null) {
            Log.e(TAG, "setProfileConnectionPolicy: device is null");
            return false;
        }

        if (newConnectionPolicy != BluetoothProfile.CONNECTION_POLICY_UNKNOWN
                && newConnectionPolicy != BluetoothProfile.CONNECTION_POLICY_FORBIDDEN
                && newConnectionPolicy != BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            Log.e(
                    TAG,
                    "setProfileConnectionPolicy: invalid connection policy " + newConnectionPolicy);
            return false;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                if (newConnectionPolicy == BluetoothProfile.CONNECTION_POLICY_UNKNOWN) {
                    return true;
                }
                createMetadata(address, false);
            }
            Metadata data = mMetadataCache.get(address);
            int oldConnectionPolicy = data.getProfileConnectionPolicy(profile);
            if (oldConnectionPolicy == newConnectionPolicy) {
                Log.v(TAG, "setProfileConnectionPolicy connection policy not changed.");
                return true;
            }
            String profileStr = BluetoothProfile.getProfileName(profile);
            logMetadataChange(data, profileStr + " connection policy changed: "
                                    + oldConnectionPolicy + " -> " + newConnectionPolicy);

            Log.v(TAG, "setProfileConnectionPolicy: device " + device.getAnonymizedAddress()
                    + " profile=" + profileStr + ", connectionPolicy=" + newConnectionPolicy);

            data.setProfileConnectionPolicy(profile, newConnectionPolicy);
            updateDatabase(data);
            return true;
        }
    }

    /**
     * Get the device profile connection policy
     *
     * @param device {@link BluetoothDevice} wish to get
     * @param profile The Bluetooth profile; one of {@link BluetoothProfile#HEADSET},
     * {@link BluetoothProfile#HEADSET_CLIENT}, {@link BluetoothProfile#A2DP},
     * {@link BluetoothProfile#A2DP_SINK}, {@link BluetoothProfile#HID_HOST},
     * {@link BluetoothProfile#PAN}, {@link BluetoothProfile#PBAP},
     * {@link BluetoothProfile#PBAP_CLIENT}, {@link BluetoothProfile#MAP},
     * {@link BluetoothProfile#MAP_CLIENT}, {@link BluetoothProfile#SAP},
     * {@link BluetoothProfile#HEARING_AID}, {@link BluetoothProfile#LE_AUDIO},
     * {@link BluetoothProfile#VOLUME_CONTROL}, {@link BluetoothProfile#CSIP_SET_COORDINATOR},
     * {@link BluetoothProfile#LE_AUDIO_BROADCAST_ASSISTANT},
     * @return the profile connection policy of the device; one of
     * {@link BluetoothProfile.CONNECTION_POLICY_UNKNOWN},
     * {@link BluetoothProfile.CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile.CONNECTION_POLICY_ALLOWED}
     */
    public int getProfileConnectionPolicy(BluetoothDevice device, int profile) {
        if (device == null) {
            Log.e(TAG, "getProfileConnectionPolicy: device is null");
            return BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                Log.d(TAG, "getProfileConnectionPolicy: device " + device.getAnonymizedAddress()
                        + " is not in cache");
                return BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
            }

            Metadata data = mMetadataCache.get(address);
            int connectionPolicy = data.getProfileConnectionPolicy(profile);

            Log.v(TAG, "getProfileConnectionPolicy: device " + device.getAnonymizedAddress()
                    + " profile=" + BluetoothProfile.getProfileName(profile)
                    + ", connectionPolicy=" + connectionPolicy);
            return connectionPolicy;
        }
    }

    /**
     * Set the A2DP optional coedc support value
     *
     * @param device {@link BluetoothDevice} wish to set
     * @param newValue the new A2DP optional coedc support value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORT_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_NOT_SUPPORTED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORTED}
     */
    @VisibleForTesting
    public void setA2dpSupportsOptionalCodecs(BluetoothDevice device, int newValue) {
        if (device == null) {
            Log.e(TAG, "setA2dpOptionalCodec: device is null");
            return;
        }
        if (newValue != BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN
                && newValue != BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED
                && newValue != BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED) {
            Log.e(TAG, "setA2dpSupportsOptionalCodecs: invalid value " + newValue);
            return;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                return;
            }
            Metadata data = mMetadataCache.get(address);
            int oldValue = data.a2dpSupportsOptionalCodecs;
            if (oldValue == newValue) {
                return;
            }
            logMetadataChange(data, "Supports optional codec changed: "
                                    + oldValue + " -> " + newValue);

            data.a2dpSupportsOptionalCodecs = newValue;
            updateDatabase(data);
        }
    }

    /**
     * Get the A2DP optional coedc support value
     *
     * @param device {@link BluetoothDevice} wish to get
     * @return the A2DP optional coedc support value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORT_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_NOT_SUPPORTED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORTED},
     */
    @VisibleForTesting
    @OptionalCodecsSupportStatus
    public int getA2dpSupportsOptionalCodecs(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "setA2dpOptionalCodec: device is null");
            return BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                Log.d(TAG, "getA2dpOptionalCodec: device " + device + " is not in cache");
                return BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
            }

            Metadata data = mMetadataCache.get(address);
            return data.a2dpSupportsOptionalCodecs;
        }
    }

    /**
     * Set the A2DP optional coedc enabled value
     *
     * @param device {@link BluetoothDevice} wish to set
     * @param newValue the new A2DP optional coedc enabled value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_DISABLED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_ENABLED}
     */
    @VisibleForTesting
    public void setA2dpOptionalCodecsEnabled(BluetoothDevice device, int newValue) {
        if (device == null) {
            Log.e(TAG, "setA2dpOptionalCodecEnabled: device is null");
            return;
        }
        if (newValue != BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN
                && newValue != BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED
                && newValue != BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED) {
            Log.e(TAG, "setA2dpOptionalCodecsEnabled: invalid value " + newValue);
            return;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                return;
            }
            Metadata data = mMetadataCache.get(address);
            int oldValue = data.a2dpOptionalCodecsEnabled;
            if (oldValue == newValue) {
                return;
            }
            logMetadataChange(data, "Enable optional codec changed: "
                                     + oldValue + " -> " + newValue);

            data.a2dpOptionalCodecsEnabled = newValue;
            updateDatabase(data);
        }
    }

    /**
     * Get the A2DP optional coedc enabled value
     *
     * @param device {@link BluetoothDevice} wish to get
     * @return the A2DP optional coedc enabled value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_DISABLED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_ENABLED}
     */
    @VisibleForTesting
    @OptionalCodecsPreferenceStatus
    public int getA2dpOptionalCodecsEnabled(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getA2dpOptionalCodecEnabled: device is null");
            return BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
        }
        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                Log.d(TAG, "getA2dpOptionalCodecEnabled: device " + device + " is not in cache");
                return BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
            }

            Metadata data = mMetadataCache.get(address);
            return data.a2dpOptionalCodecsEnabled;
        }
    }

    @GuardedBy("mMetadataCache")
    private void setConnection(BluetoothDevice device, boolean isActiveA2dp, boolean isActiveHfp) {
        if (device == null) {
            Log.e(TAG, "setConnection: device is null");
            return;
        }
        String address = device.getAddress();

        if (!mMetadataCache.containsKey(address)) {
            createMetadata(address, isActiveA2dp, isActiveHfp);
            return;
        }
        // Updates last_active_time to the current counter value and increments the counter
        Metadata metadata = mMetadataCache.get(address);
        synchronized (MetadataDatabase.class) {
            metadata.last_active_time = MetadataDatabase.sCurrentConnectionNumber++;
        }

        // Only update is_active_a2dp_device if an a2dp device is connected
        if (isActiveA2dp) {
            metadata.is_active_a2dp_device = true;
        }

        if (isActiveHfp) {
            metadata.isActiveHfpDevice = true;
        }

        Log.d(
                TAG,
                "Updating last connected time for device: "
                        + device
                        + " to "
                        + metadata.last_active_time);
        updateDatabase(metadata);
    }

    /**
     * Updates the time this device was last connected
     *
     * @param device is the remote bluetooth device for which we are setting the connection time
     */
    public void setConnection(BluetoothDevice device) {
        synchronized (mMetadataCache) {
            setConnection(device, false, false);
        }
    }

    /**
     * Updates the time this device was last connected with its profile information
     *
     * @param device is the remote bluetooth device for which we are setting the connection time
     * @param profileId see {@link BluetoothProfile}
     */
    public void setConnection(BluetoothDevice device, int profileId) {
        boolean isA2dpDevice = profileId == BluetoothProfile.A2DP;
        boolean isHfpDevice = profileId == BluetoothProfile.HEADSET;

        synchronized (mMetadataCache) {
            if (isA2dpDevice) {
                resetActiveA2dpDevice();
            }
            if (isHfpDevice && !Flags.autoConnectOnMultipleHfpWhenNoA2dpDevice()) {
                resetActiveHfpDevice();
            }

            setConnection(device, isA2dpDevice, isHfpDevice);
        }
    }

    /**
     * Sets device profileId's active status to false if currently true
     *
     * @param device is the remote bluetooth device with which we have disconnected
     * @param profileId see {@link BluetoothProfile}
     */
    public void setDisconnection(BluetoothDevice device, int profileId) {
        if (device == null) {
            Log.e(
                    TAG,
                    "setDisconnection: device is null, "
                            + "profileId: "
                            + BluetoothProfile.getProfileName(profileId));
            return;
        }
        Log.d(
                TAG,
                "setDisconnection: device "
                        + device
                        + "profileId: "
                        + BluetoothProfile.getProfileName(profileId));

        if (profileId != BluetoothProfile.A2DP && profileId != BluetoothProfile.HEADSET) {
            // there is no change on metadata when profile is neither A2DP nor Headset
            return;
        }

        String address = device.getAddress();

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                return;
            }
            Metadata metadata = mMetadataCache.get(address);

            if (profileId == BluetoothProfile.A2DP && metadata.is_active_a2dp_device) {
                metadata.is_active_a2dp_device = false;
                Log.d(
                        TAG,
                        "setDisconnection: Updating is_active_device to false for device: "
                                + device);
                updateDatabase(metadata);
            }
            if (profileId == BluetoothProfile.HEADSET && metadata.isActiveHfpDevice) {
                metadata.isActiveHfpDevice = false;
                Log.d(
                        TAG,
                        "setDisconnection: Updating isActiveHfpDevice to false for device: "
                                + device);
                updateDatabase(metadata);
            }
        }
    }

    /** Remove a2dpActiveDevice from the current active device in the connection order table */
    @GuardedBy("mMetadataCache")
    private void resetActiveA2dpDevice() {
        Log.d(TAG, "resetActiveA2dpDevice()");
        for (Map.Entry<String, Metadata> entry : mMetadataCache.entrySet()) {
            Metadata metadata = entry.getValue();
            if (metadata.is_active_a2dp_device) {
                Log.d(TAG, "resetActiveA2dpDevice");
                metadata.is_active_a2dp_device = false;
                updateDatabase(metadata);
            }
        }
    }

    /** Remove hfpActiveDevice from the current active device in the connection order table */
    @GuardedBy("mMetadataCache")
    private void resetActiveHfpDevice() {
        Log.d(TAG, "resetActiveHfpDevice()");
        for (Map.Entry<String, Metadata> entry : mMetadataCache.entrySet()) {
            Metadata metadata = entry.getValue();
            if (metadata.isActiveHfpDevice) {
                Log.d(TAG, "resetActiveHfpDevice");
                metadata.isActiveHfpDevice = false;
                updateDatabase(metadata);
            }
        }
    }

    /**
     * Gets the most recently connected bluetooth devices in order with most recently connected
     * first and least recently connected last
     *
     * @return a {@link List} of {@link BluetoothDevice} representing connected bluetooth devices
     * in order of most recently connected
     */
    public List<BluetoothDevice> getMostRecentlyConnectedDevices() {
        List<BluetoothDevice> mostRecentlyConnectedDevices = new ArrayList<>();
        synchronized (mMetadataCache) {
            List<Metadata> sortedMetadata = new ArrayList<>(mMetadataCache.values());
            sortedMetadata.sort((o1, o2) -> Long.compare(o2.last_active_time, o1.last_active_time));
            for (Metadata metadata : sortedMetadata) {
                try {
                    mostRecentlyConnectedDevices.add(BluetoothAdapter.getDefaultAdapter()
                            .getRemoteDevice(metadata.getAddress()));
                } catch (IllegalArgumentException ex) {
                    Log.d(TAG, "getBondedDevicesOrdered: Invalid address for "
                               + "device " + metadata.getAnonymizedAddress());
                }
            }
        }
        return mostRecentlyConnectedDevices;
    }

    /**
     * Gets the most recently connected bluetooth device in a given list.
     *
     * @param devicesList the list of {@link BluetoothDevice} to search in
     * @return the most recently connected {@link BluetoothDevice} in the given
     *         {@code devicesList}, or null if an error occurred
     *
     * @hide
     */
    public BluetoothDevice getMostRecentlyConnectedDevicesInList(
            List<BluetoothDevice> devicesList) {
        if (devicesList == null) {
            return null;
        }

        BluetoothDevice mostRecentDevice = null;
        long mostRecentLastActiveTime = -1;
        synchronized (mMetadataCache) {
            for (BluetoothDevice device : devicesList) {
                String address = device.getAddress();
                Metadata metadata = mMetadataCache.get(address);
                if (metadata != null && (mostRecentLastActiveTime == -1
                            || mostRecentLastActiveTime < metadata.last_active_time)) {
                    mostRecentLastActiveTime = metadata.last_active_time;
                    mostRecentDevice = device;
                }
            }
        }
        return mostRecentDevice;
    }

    /**
     * Gets the last active a2dp device
     *
     * @return the most recently active a2dp device or null if the last a2dp device was null
     */
    public BluetoothDevice getMostRecentlyConnectedA2dpDevice() {
        synchronized (mMetadataCache) {
            for (Map.Entry<String, Metadata> entry : mMetadataCache.entrySet()) {
                Metadata metadata = entry.getValue();
                if (metadata.is_active_a2dp_device) {
                    try {
                        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                                metadata.getAddress());
                    } catch (IllegalArgumentException ex) {
                        Log.d(TAG, "getMostRecentlyConnectedA2dpDevice: Invalid address for "
                                   + "device " + metadata.getAnonymizedAddress());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gets the last active HFP device
     *
     * @return the most recently active HFP device or null if the last hfp device was null
     */
    public BluetoothDevice getMostRecentlyActiveHfpDevice() {
        Map.Entry<String, Metadata> entry;
        synchronized (mMetadataCache) {
            entry =
                    mMetadataCache.entrySet().stream()
                            .filter(x -> x.getValue().isActiveHfpDevice)
                            .findFirst()
                            .orElse(null);
        }
        if (entry != null) {
            try {
                return BluetoothAdapter.getDefaultAdapter()
                        .getRemoteDevice(entry.getValue().getAddress());
            } catch (IllegalArgumentException ex) {
                Log.d(
                        TAG,
                        "getMostRecentlyActiveHfpDevice: Invalid address for "
                                + "device "
                                + entry.getValue().getAnonymizedAddress());
            }
        }

        return null;
    }

    /**
     * @return the list of device registered as HFP active
     */
    public List<BluetoothDevice> getMostRecentlyActiveHfpDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        synchronized (mMetadataCache) {
            return mMetadataCache.entrySet().stream()
                    .filter(x -> x.getValue().isActiveHfpDevice)
                    .map(x -> adapter.getRemoteDevice(x.getValue().getAddress()))
                    .collect(Collectors.toList());
        }
    }

    /**
     *
     * @param metadataList is the list of metadata
     */
    private void compactLastConnectionTime(List<Metadata> metadataList) {
        Log.d(TAG, "compactLastConnectionTime: Compacting metadata after load");
        synchronized (MetadataDatabase.class) {
            MetadataDatabase.sCurrentConnectionNumber = 0;
            // Have to go in reverse order as list is ordered by descending last_active_time
            for (int index = metadataList.size() - 1; index >= 0; index--) {
                Metadata metadata = metadataList.get(index);
                if (metadata.last_active_time != MetadataDatabase.sCurrentConnectionNumber) {
                    Log.d(TAG, "compactLastConnectionTime: Setting last_active_item for device: "
                            + metadata.getAnonymizedAddress() + " from " + metadata.last_active_time
                            + " to " + MetadataDatabase.sCurrentConnectionNumber);
                    metadata.last_active_time = MetadataDatabase.sCurrentConnectionNumber;
                    updateDatabase(metadata);
                    MetadataDatabase.sCurrentConnectionNumber++;
                }
            }
        }
    }

    /**
     * Sets the preferred profile for the supplied audio modes. See {@link
     * BluetoothAdapter#setPreferredAudioProfiles(BluetoothDevice, Bundle)} for more details.
     *
     * <p>If a device in the group has been designated to store the preference for the group, this
     * will update its database preferences. If there is not one designated, the first device from
     * the group list will be chosen for this purpose. From then on, any preferred audio profile
     * changes for this group will be stored on that device.
     *
     * @param groupDevices is the CSIP group for which we are setting the preferred audio profiles
     * @param modeToProfileBundle contains the preferred profile
     * @return whether the new preferences were saved in the database
     */
    public int setPreferredAudioProfiles(
            List<BluetoothDevice> groupDevices, Bundle modeToProfileBundle) {
        Objects.requireNonNull(groupDevices, "groupDevices must not be null");
        Objects.requireNonNull(modeToProfileBundle, "modeToProfileBundle must not be null");
        if (groupDevices.isEmpty()) {
            throw new IllegalArgumentException("groupDevices cannot be empty");
        }
        int outputProfile = modeToProfileBundle.getInt(
                BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY);
        int duplexProfile = modeToProfileBundle.getInt(BluetoothAdapter.AUDIO_MODE_DUPLEX);
        boolean isPreferenceSet = false;

        synchronized (mMetadataCache) {
            for (BluetoothDevice device : groupDevices) {
                if (device == null) {
                    Log.e(TAG, "setPreferredAudioProfiles: device is null");
                    throw new IllegalArgumentException("setPreferredAudioProfiles: device is null");
                }

                String address = device.getAddress();
                if (!mMetadataCache.containsKey(address)) {
                    Log.e(TAG, "setPreferredAudioProfiles: Device not found in the database");
                    return BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED;
                }

                // Finds the device in the group which stores the group's preferences
                Metadata metadata = mMetadataCache.get(address);
                if (outputProfile != 0 && (metadata.preferred_output_only_profile != 0
                        || metadata.preferred_duplex_profile != 0)) {
                    Log.i(TAG, "setPreferredAudioProfiles: Updating OUTPUT_ONLY audio profile for "
                            + "device: " + device + " to "
                            + BluetoothProfile.getProfileName(outputProfile));
                    metadata.preferred_output_only_profile = outputProfile;
                    isPreferenceSet = true;
                }
                if (duplexProfile != 0 && (metadata.preferred_output_only_profile != 0
                        || metadata.preferred_duplex_profile != 0)) {
                    Log.i(TAG,
                            "setPreferredAudioProfiles: Updating DUPLEX audio profile for device: "
                                    + device + " to " + BluetoothProfile.getProfileName(
                                    duplexProfile));
                    metadata.preferred_duplex_profile = duplexProfile;
                    isPreferenceSet = true;
                }

                updateDatabase(metadata);
            }

            // If no device in the group has a preference set, choose the first device in the list
            if (!isPreferenceSet) {
                Log.i(TAG, "No device in the group has preferred audio profiles set");
                BluetoothDevice firstGroupDevice = groupDevices.get(0);
                // Updates preferred audio profiles for the device
                Metadata metadata = mMetadataCache.get(firstGroupDevice.getAddress());
                if (outputProfile != 0) {
                    Log.i(TAG, "setPreferredAudioProfiles: Updating output only audio profile for "
                            + "device: " + firstGroupDevice + " to "
                            + BluetoothProfile.getProfileName(outputProfile));
                    metadata.preferred_output_only_profile = outputProfile;
                }
                if (duplexProfile != 0) {
                    Log.i(TAG,
                            "setPreferredAudioProfiles: Updating duplex audio profile for device: "
                                    + firstGroupDevice + " to " + BluetoothProfile.getProfileName(
                                    duplexProfile));
                    metadata.preferred_duplex_profile = duplexProfile;
                }

                updateDatabase(metadata);
            }
        }
        return BluetoothStatusCodes.SUCCESS;
    }

    /**
     * Sets the preferred profile for the supplied audio modes. See
     * {@link BluetoothAdapter#getPreferredAudioProfiles(BluetoothDevice)} for more details.
     *
     * @param device is the device for which we want to get the preferred audio profiles
     * @return a Bundle containing the preferred audio profiles
     */
    public Bundle getPreferredAudioProfiles(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getPreferredAudioProfiles: device is null");
            throw new IllegalArgumentException("getPreferredAudioProfiles: device is null");
        }

        String address = device.getAddress();
        final int outputOnlyProfile;
        final int duplexProfile;

        synchronized (mMetadataCache) {
            if (!mMetadataCache.containsKey(address)) {
                return Bundle.EMPTY;
            }

            // Gets the preferred audio profiles for each audio mode
            Metadata metadata = mMetadataCache.get(address);
            outputOnlyProfile = metadata.preferred_output_only_profile;
            duplexProfile = metadata.preferred_duplex_profile;
        }

        // Checks if the default values are present (aka no explicit preference)
        if (outputOnlyProfile == 0 && duplexProfile == 0) {
            return Bundle.EMPTY;
        }

        Bundle modeToProfileBundle = new Bundle();
        if (outputOnlyProfile != 0) {
            modeToProfileBundle.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, outputOnlyProfile);
        }
        if (duplexProfile != 0) {
            modeToProfileBundle.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, duplexProfile);
        }

        return modeToProfileBundle;
    }

    /**
     * Set the device active audio policy. See {@link
     * BluetoothDevice#setActiveAudioDevicePolicy(activeAudioDevicePolicy)} for more details.
     *
     * @param device is the remote device for which we are setting the active audio device policy.
     * @param activeAudioDevicePolicy active audio device policy.
     * @return whether the policy was set properly
     */
    public int setActiveAudioDevicePolicy(BluetoothDevice device, int activeAudioDevicePolicy) {
        synchronized (mMetadataCache) {
            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                Log.e(TAG, "device is not bonded");
                return BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED;
            }

            Metadata metadata = mMetadataCache.get(address);
            Log.i(
                    TAG,
                    "Updating active_audio_device_policy setting for "
                            + "device "
                            + device
                            + " to: "
                            + activeAudioDevicePolicy);
            metadata.active_audio_device_policy = activeAudioDevicePolicy;

            updateDatabase(metadata);
        }
        return BluetoothStatusCodes.SUCCESS;
    }

    /**
     * Get the active audio device policy for this device. See {@link
     * BluetoothDevice#getActiveAudioDevicePolicy()} for more details.
     *
     * @param device is the device for which we want to get the policy
     * @return active audio device policy for this device
     */
    public int getActiveAudioDevicePolicy(BluetoothDevice device) {
        synchronized (mMetadataCache) {
            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                Log.e(TAG, "device is not bonded");
                return BluetoothDevice.ACTIVE_AUDIO_DEVICE_POLICY_DEFAULT;
            }

            Metadata metadata = mMetadataCache.get(address);

            return metadata.active_audio_device_policy;
        }
    }

    /**
     * Get the {@link Looper} for the handler thread. This is used in testing and helper
     * objects
     *
     * @return {@link Looper} for the handler thread
     */
    @VisibleForTesting
    public Looper getHandlerLooper() {
        if (mHandlerThread == null) {
            return null;
        }
        return mHandlerThread.getLooper();
    }

    /**
     * Start and initialize the DatabaseManager
     *
     * @param database the Bluetooth storage {@link MetadataDatabase}
     */
    public void start(MetadataDatabase database) {
        Log.d(TAG, "start()");

        if (database == null) {
            Log.e(TAG, "stat failed, database is null.");
            return;
        }

        synchronized (mDatabaseLock) {
            mDatabase = database;
        }

        mHandlerThread = new HandlerThread("BluetoothDatabaseManager");
        mHandlerThread.start();
        mHandler = new DatabaseHandler(mHandlerThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mAdapterService.registerReceiver(mReceiver, filter);

        loadDatabase();
    }

    String getDatabaseAbsolutePath() {
        //TODO backup database when Bluetooth turn off and FOTA?
        return mAdapterService.getDatabasePath(MetadataDatabase.DATABASE_NAME)
                .getAbsolutePath();
    }

    /**
     * Clear all persistence data in database
     */
    public void factoryReset() {
        Log.w(TAG, "factoryReset");
        Message message = mHandler.obtainMessage(MSG_CLEAR_DATABASE);
        mHandler.sendMessage(message);
    }

    /** Close and de-init the DatabaseManager */
    public void cleanup() {
        removeUnusedMetadata();
        mAdapterService.unregisterReceiver(mReceiver);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        mMetadataCache.clear();
    }

    void createMetadata(String address, boolean isActiveA2dpDevice) {
        createMetadata(address, isActiveA2dpDevice, false);
    }

    void createMetadata(String address, boolean isActiveA2dpDevice, boolean isActiveHfpDevice) {
        Metadata.Builder dataBuilder = new Metadata.Builder(address);

        if (isActiveA2dpDevice) {
            dataBuilder.setActiveA2dp();
        }
        if (isActiveHfpDevice) {
            dataBuilder.setActiveHfp();
        }

        Metadata data = dataBuilder.build();
        Log.d(
                TAG,
                "createMetadata: "
                        + (" address=" + data.getAnonymizedAddress())
                        + (" isActiveHfpDevice=" + isActiveHfpDevice)
                        + (" isActiveA2dpDevice=" + isActiveA2dpDevice));
        mMetadataCache.put(address, data);
        updateDatabase(data);
        logMetadataChange(data, "Metadata created");
    }

    @VisibleForTesting
    void removeUnusedMetadata() {
        BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        synchronized (mMetadataCache) {
            mMetadataCache.forEach((address, metadata) -> {
                if (!address.equals(LOCAL_STORAGE)
                        && !Arrays.asList(bondedDevices).stream().anyMatch(device ->
                        address.equals(device.getAddress()))) {
                    List<Integer> list = metadata.getChangedCustomizedMeta();
                    for (int key : list) {
                        mAdapterService.metadataChanged(address, key, null);
                    }
                    Log.i(TAG, "remove unpaired device from database "
                               + metadata.getAnonymizedAddress());
                    deleteDatabase(mMetadataCache.get(address));
                }
            });
        }
    }

    void cacheMetadata(List<Metadata> list) {
        synchronized (mMetadataCache) {
            Log.i(TAG, "cacheMetadata");
            // Unlock the main thread.
            mSemaphore.release();

            if (!isMigrated(list)) {
                // Wait for data migrate from Settings Global
                mMigratedFromSettingsGlobal = false;
                return;
            }
            mMigratedFromSettingsGlobal = true;
            for (Metadata data : list) {
                String address = data.getAddress();
                Log.v(TAG, "cacheMetadata: found device " + data.getAnonymizedAddress());
                mMetadataCache.put(address, data);
            }
            Log.i(TAG, "cacheMetadata: Database is ready");
        }
    }

    boolean isMigrated(List<Metadata> list) {
        for (Metadata data : list) {
            String address = data.getAddress();
            if (address.equals(LOCAL_STORAGE) && data.migrated) {
                return true;
            }
        }
        return false;
    }

    void migrateSettingsGlobal() {
        Log.i(TAG, "migrateSettingGlobal");

        BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        ContentResolver contentResolver = mAdapterService.getContentResolver();

        for (BluetoothDevice device : bondedDevices) {
            int a2dpConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyA2dpSinkPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int a2dpSinkConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyA2dpSrcPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int hearingaidConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyHearingAidPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int headsetConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyHeadsetPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int headsetClientConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyHeadsetPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int hidHostConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyHidHostPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int mapConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyMapPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int mapClientConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyMapClientPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int panConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyPanPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int pbapConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyPbapClientPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int pbapClientConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacyPbapClientPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int sapConnectionPolicy = Settings.Global.getInt(contentResolver,
                    getLegacySapPriorityKey(device.getAddress()),
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            int a2dpSupportsOptionalCodec = Settings.Global.getInt(contentResolver,
                    getLegacyA2dpSupportsOptionalCodecsKey(device.getAddress()),
                    BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
            int a2dpOptionalCodecEnabled = Settings.Global.getInt(contentResolver,
                    getLegacyA2dpOptionalCodecsEnabledKey(device.getAddress()),
                    BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);

            String address = device.getAddress();
            Metadata data = new Metadata(address);
            data.setProfileConnectionPolicy(BluetoothProfile.A2DP, a2dpConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.A2DP_SINK, a2dpSinkConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.HEADSET, headsetConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.HEADSET_CLIENT,
                    headsetClientConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.HID_HOST, hidHostConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.PAN, panConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.PBAP, pbapConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.PBAP_CLIENT,
                    pbapClientConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.MAP, mapConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.MAP_CLIENT, mapClientConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.SAP, sapConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.HEARING_AID,
                    hearingaidConnectionPolicy);
            data.setProfileConnectionPolicy(BluetoothProfile.LE_AUDIO,
                    BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
            data.a2dpSupportsOptionalCodecs = a2dpSupportsOptionalCodec;
            data.a2dpOptionalCodecsEnabled = a2dpOptionalCodecEnabled;
            mMetadataCache.put(address, data);
            updateDatabase(data);
        }

        // Mark database migrated from Settings Global
        Metadata localData = new Metadata(LOCAL_STORAGE);
        localData.migrated = true;
        mMetadataCache.put(LOCAL_STORAGE, localData);
        updateDatabase(localData);

        // Reload database after migration is completed
        loadDatabase();

    }

    /**
     * Get the key that retrieves a bluetooth headset's priority.
     */
    private static String getLegacyHeadsetPriorityKey(String address) {
        return LEGACY_HEADSET_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth a2dp sink's priority.
     */
    private static String getLegacyA2dpSinkPriorityKey(String address) {
        return LEGACY_A2DP_SINK_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth a2dp src's priority.
     */
    private static String getLegacyA2dpSrcPriorityKey(String address) {
        return LEGACY_A2DP_SRC_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth a2dp device's ability to support optional codecs.
     */
    private static String getLegacyA2dpSupportsOptionalCodecsKey(String address) {
        return LEGACY_A2DP_SUPPORTS_OPTIONAL_CODECS_PREFIX
                + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves whether a bluetooth a2dp device should have optional codecs
     * enabled.
     */
    private static String getLegacyA2dpOptionalCodecsEnabledKey(String address) {
        return LEGACY_A2DP_OPTIONAL_CODECS_ENABLED_PREFIX
                + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth Input Device's priority.
     */
    private static String getLegacyHidHostPriorityKey(String address) {
        return LEGACY_INPUT_DEVICE_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth pan client priority.
     */
    private static String getLegacyPanPriorityKey(String address) {
        return LEGACY_PAN_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth hearing aid priority.
     */
    private static String getLegacyHearingAidPriorityKey(String address) {
        return LEGACY_HEARING_AID_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth map priority.
     */
    private static String getLegacyMapPriorityKey(String address) {
        return LEGACY_MAP_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth map client priority.
     */
    private static String getLegacyMapClientPriorityKey(String address) {
        return LEGACY_MAP_CLIENT_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth pbap client priority.
     */
    private static String getLegacyPbapClientPriorityKey(String address) {
        return LEGACY_PBAP_CLIENT_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the key that retrieves a bluetooth sap priority.
     */
    private static String getLegacySapPriorityKey(String address) {
        return LEGACY_SAP_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
    }

    private void loadDatabase() {
        Log.d(TAG, "Load Database");
        Message message = mHandler.obtainMessage(MSG_LOAD_DATABASE);
        mHandler.sendMessage(message);
        try {
            // Lock the thread until handler thread finish loading database.
            mSemaphore.tryAcquire(LOAD_DATABASE_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "loadDatabase: semaphore acquire failed");
        }
    }

    private void updateDatabase(Metadata data) {
        if (data.getAddress() == null) {
            Log.e(TAG, "updateDatabase: address is null");
            return;
        }
        Log.d(TAG, "updateDatabase " + data.getAnonymizedAddress());
        Message message = mHandler.obtainMessage(MSG_UPDATE_DATABASE);
        message.obj = data;
        mHandler.sendMessage(message);
    }

    @VisibleForTesting
    void deleteDatabase(Metadata data) {
        String address = data.getAddress();
        if (address == null) {
            Log.e(TAG, "deleteDatabase: address is null");
            return;
        }
        logMetadataChange(data, "Metadata deleted");
        Message message = mHandler.obtainMessage(MSG_DELETE_DATABASE);
        message.obj = data.getAddress();
        mHandler.sendMessage(message);
    }

    private void logManufacturerInfo(BluetoothDevice device, int key, byte[] bytesValue) {
        String callingApp = mAdapterService.getPackageManager().getNameForUid(
                Binder.getCallingUid());
        String manufacturerName = "";
        String modelName = "";
        String hardwareVersion = "";
        String softwareVersion = "";
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
                manufacturerName = Utils.byteArrayToUtf8String(bytesValue);
                break;
            case BluetoothDevice.METADATA_MODEL_NAME:
                modelName = Utils.byteArrayToUtf8String(bytesValue);
                break;
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
                hardwareVersion = Utils.byteArrayToUtf8String(bytesValue);
                break;
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
                softwareVersion = Utils.byteArrayToUtf8String(bytesValue);
                break;
            default:
                // Do not log anything if metadata doesn't fall into above categories
                return;
        }
        String[] macAddress = device.getAddress().split(":");
        BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_DEVICE_INFO_REPORTED,
                mAdapterService.obfuscateAddress(device),
                BluetoothProtoEnums.DEVICE_INFO_EXTERNAL, callingApp, manufacturerName, modelName,
                hardwareVersion, softwareVersion, mAdapterService.getMetricId(device),
                device.getAddressType(),
                Integer.parseInt(macAddress[0], 16),
                Integer.parseInt(macAddress[1], 16),
                Integer.parseInt(macAddress[2], 16));
    }

    private void logMetadataChange(Metadata data, String log) {
        String time = Utils.getLocalTimeString();
        String uidPid = Utils.getUidPidString();
        mMetadataChangedLog.add(time + " (" + uidPid + ") " + data.getAnonymizedAddress()
                                + " " + log);
    }

    /**
     * Dump database info to a PrintWriter
     *
     * @param writer the PrintWriter to write log
     */
    public void dump(PrintWriter writer) {
        writer.println("\nBluetoothDatabase:");
        writer.println("  Metadata Changes:");
        for (String log : mMetadataChangedLog) {
            writer.println("    " + log);
        }
        writer.println("\nMetadata:");
        for (Map.Entry<String, Metadata> entry : mMetadataCache.entrySet()) {
            if (entry.getKey().equals(LOCAL_STORAGE)) {
                // No need to dump local storage
                continue;
            }
            writer.println("    " + entry.getValue());
        }
    }
}
