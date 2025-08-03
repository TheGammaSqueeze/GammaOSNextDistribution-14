/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

/*
 * Defines the native interface that is used by state machine/service to
 * send or receive messages from the native stack. This file is registered
 * for the native methods in the corresponding JNI C++ file.
 */
package com.android.bluetooth.le_audio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * LeAudio Native Interface to/from JNI.
 */
public class LeAudioBroadcasterNativeInterface {
    private static final String TAG = "LeAudioBroadcasterNativeInterface";
    private static final boolean DBG = true;
    private BluetoothAdapter mAdapter;

    @GuardedBy("INSTANCE_LOCK")
    private static LeAudioBroadcasterNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private LeAudioBroadcasterNativeInterface() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            Log.wtf(TAG, "No Bluetooth Adapter Available");
        }
    }

    /**
     * Get singleton instance.
     */
    public static LeAudioBroadcasterNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new LeAudioBroadcasterNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    static void setInstance(LeAudioBroadcasterNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    private void sendMessageToService(LeAudioStackEvent event) {
        LeAudioService service = LeAudioService.getLeAudioService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.e(TAG, "Event ignored, service not available: " + event);
        }
    }

    @VisibleForTesting
    public BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(address);
    }

    // Callbacks from the native stack back into the Java framework.
    @VisibleForTesting
    public void onBroadcastCreated(int broadcastId, boolean success) {
        if (DBG) {
            Log.d(TAG, "onBroadcastCreated: broadcastId=" + broadcastId);
        }
        LeAudioStackEvent event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);

        event.valueInt1 = broadcastId;
        event.valueBool1 = success;
        sendMessageToService(event);
    }

    @VisibleForTesting
    public void onBroadcastDestroyed(int broadcastId) {
        if (DBG) {
            Log.d(TAG, "onBroadcastDestroyed: broadcastId=" + broadcastId);
        }
        LeAudioStackEvent event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_DESTROYED);

        event.valueInt1 = broadcastId;
        sendMessageToService(event);
    }

    @VisibleForTesting
    public void onBroadcastStateChanged(int broadcastId, int state) {
        if (DBG) {
            Log.d(TAG, "onBroadcastStateChanged: broadcastId=" + broadcastId + " state=" + state);
        }
        LeAudioStackEvent event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);

        /* NOTICE: This is a fake device to satisfy Audio Manager in the upper
         * layers which needs a device instance to route audio streams to the
         * proper module (here it's Bluetooth). Broadcast has no concept of a
         * destination or peer device therefore this fake device was created.
         * For now it's only important that this device is a Bluetooth device.
         */
        event.device = getDevice(Utils.getBytesFromAddress("FF:FF:FF:FF:FF:FF"));
        event.valueInt1 = broadcastId;
        event.valueInt2 = state;
        sendMessageToService(event);
    }

    @VisibleForTesting
    public void onBroadcastMetadataChanged(int broadcastId, BluetoothLeBroadcastMetadata metadata) {
        if (DBG) {
            Log.d(TAG, "onBroadcastMetadataChanged: broadcastId=" + broadcastId);
        }
        LeAudioStackEvent event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_METADATA_CHANGED);

        event.valueInt1 = broadcastId;
        event.broadcastMetadata = metadata;
        sendMessageToService(event);
    }

    /**
     * Initializes the native interface.
     *
     * priorities to configure.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void init() {
        initNative();
    }

    /**
     * Stop the Broadcast Service.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void stop() {
        stopNative();
    }

    /**
     * Cleanup the native interface.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void cleanup() {
        cleanupNative();
    }

    /**
     * Creates LeAudio Broadcast instance.
     *
     * @param isPublicBroadcast this BIG is public broadcast
     * @param broadcastName BIG broadcast name
     * @param broadcastCode BIG broadcast code
     * @param publicMetadata BIG public broadcast meta data
     * @param qualityArray BIG sub group audio quality array
     * @param metadataArray BIG sub group metadata array
     *
     * qualityArray and metadataArray use the same subgroup index
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void createBroadcast(boolean isPublicBroadcast, String broadcastName,
            byte[] broadcastCode, byte[] publicMetadata, int[] qualityArray,
            byte[][] metadataArray) {
        createBroadcastNative(isPublicBroadcast, broadcastName, broadcastCode, publicMetadata,
                qualityArray, metadataArray);
    }

    /**
     * Update LeAudio Broadcast instance metadata.
     *
     * @param broadcastId broadcast instance identifier
     * @param broadcastName BIG broadcast name
     * @param publicMetadata BIG public broadcast meta data
     * @param metadataArray BIG sub group metadata array
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void updateMetadata(int broadcastId, String broadcastName,
            byte[] publicMetadata, byte[][] metadataArray) {
        updateMetadataNative(broadcastId, broadcastName, publicMetadata, metadataArray);
    }

    /**
     * Start LeAudio Broadcast instance.
     *
     * @param broadcastId broadcast instance identifier
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void startBroadcast(int broadcastId) {
        startBroadcastNative(broadcastId);
    }

    /**
     * Stop LeAudio Broadcast instance.
     *
     * @param broadcastId broadcast instance identifier
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void stopBroadcast(int broadcastId) {
        stopBroadcastNative(broadcastId);
    }

    /**
     * Pause LeAudio Broadcast instance.
     *
     * @param broadcastId broadcast instance identifier
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void pauseBroadcast(int broadcastId) {
        pauseBroadcastNative(broadcastId);
    }

    /**
     * Destroy LeAudio Broadcast instance.
     *
     * @param broadcastId broadcast instance identifier
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void destroyBroadcast(int broadcastId) {
        destroyBroadcastNative(broadcastId);
    }

    /**
     * Get all LeAudio Broadcast instance states.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void getBroadcastMetadata(int broadcastId) {
        getBroadcastMetadataNative(broadcastId);
    }

    // Native methods that call into the JNI interface
    private native void initNative();
    private native void stopNative();
    private native void cleanupNative();
    private native void createBroadcastNative(boolean isPublicBroadcast, String broadcastName,
            byte[] broadcastCode, byte[] publicMetadata, int[] qualityArray,
            byte[][] metadataArray);
    private native void updateMetadataNative(int broadcastId, String broadcastName,
            byte[] publicMetadata, byte[][] metadataArray);
    private native void startBroadcastNative(int broadcastId);
    private native void stopBroadcastNative(int broadcastId);
    private native void pauseBroadcastNative(int broadcastId);
    private native void destroyBroadcastNative(int broadcastId);
    private native void getBroadcastMetadataNative(int broadcastId);
}
