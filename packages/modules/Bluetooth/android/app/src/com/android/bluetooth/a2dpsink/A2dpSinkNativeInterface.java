/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bluetooth.a2dpsink;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.Flags;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * A2DP Sink Native Interface to/from JNI.
 */
public class A2dpSinkNativeInterface {
    private static final String TAG = "A2dpSinkNativeInterface";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private AdapterService mAdapterService;

    @GuardedBy("INSTANCE_LOCK")
    private static A2dpSinkNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private A2dpSinkNativeInterface() {
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when A2dpSinkNativeInterface init");
    }

    /**
     * Get singleton instance.
     */
    public static A2dpSinkNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new A2dpSinkNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(A2dpSinkNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    /**
     * Initializes the native interface and sets the max number of connected devices
     *
     * @param maxConnectedAudioDevices The maximum number of devices that can be connected at once
     */
    public void init(int maxConnectedAudioDevices) {
        initNative(maxConnectedAudioDevices);
    }

    /**
     * Cleanup the native interface.
     */
    public void cleanup() {
        cleanupNative();
    }

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapterService.getDeviceFromByte(address);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        if (Flags.identityAddressNullIfUnknown()) {
            return Utils.getByteBrEdrAddress(device);
        } else {
            return mAdapterService.getByteIdentityAddress(device);
        }
    }

    /**
     * Initiates an A2DP connection to a remote device.
     *
     * @param device the remote device
     * @return true on success, otherwise false.
     */
    public boolean connectA2dpSink(BluetoothDevice device) {
        return connectA2dpNative(getByteAddress(device));
    }

    /**
     * Disconnects A2DP from a remote device.
     *
     * @param device the remote device
     * @return true on success, otherwise false.
     */
    public boolean disconnectA2dpSink(BluetoothDevice device) {
        return disconnectA2dpNative(getByteAddress(device));
    }

    /**
     * Set a BluetoothDevice as the active device
     *
     * The active device is the only one that will receive passthrough commands and the only one
     * that will have its audio decoded.
     *
     * Sending null for the active device will make no device active.
     *
     * @param device
     * @return True if the active device request has been scheduled
     */
    public boolean setActiveDevice(BluetoothDevice device) {
        // Translate to byte address for JNI. Use an all 0 MAC for no active device
        byte[] address = null;
        if (device != null) {
            address = getByteAddress(device);
        } else {
            address = Utils.getBytesFromAddress("00:00:00:00:00:00");
        }
        return setActiveDeviceNative(address);
    }

    /**
     * Inform A2DP decoder of the current audio focus
     *
     * @param focusGranted
     */
    public void informAudioFocusState(int focusGranted) {
        informAudioFocusStateNative(focusGranted);
    }

    /**
     * Inform A2DP decoder the desired audio gain
     *
     * @param gain
     */
    public void informAudioTrackGain(float gain) {
        informAudioTrackGainNative(gain);
    }

    /**
     * Send a stack event up to the A2DP Sink Service
     */
    private void sendMessageToService(StackEvent event) {
        A2dpSinkService service = A2dpSinkService.getA2dpSinkService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.e(TAG, "Event ignored, service not available: " + event);
        }
    }

    /**
     * For the JNI to send messages about connection state changes
     */
    public void onConnectionStateChanged(byte[] address, int state) {
        StackEvent event =
                StackEvent.connectionStateChanged(getDevice(address), state);
        if (DBG) {
            Log.d(TAG, "onConnectionStateChanged: " + event);
        }
        sendMessageToService(event);
    }

    /**
     * For the JNI to send messages about audio stream state changes
     */
    public void onAudioStateChanged(byte[] address, int state) {
        StackEvent event = StackEvent.audioStateChanged(getDevice(address), state);
        if (DBG) {
            Log.d(TAG, "onAudioStateChanged: " + event);
        }
        sendMessageToService(event);
    }

    /**
     * For the JNI to send messages about audio configuration changes
     */
    public void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        StackEvent event = StackEvent.audioConfigChanged(
                getDevice(address), sampleRate, channelCount);
        if (DBG) {
            Log.d(TAG, "onAudioConfigChanged: " + event);
        }
        sendMessageToService(event);
    }

    // Native methods that call into the JNI interface
    private native void initNative(int maxConnectedAudioDevices);
    private native void cleanupNative();
    private native boolean connectA2dpNative(byte[] address);
    private native boolean disconnectA2dpNative(byte[] address);
    private native boolean setActiveDeviceNative(byte[] address);
    private native void informAudioFocusStateNative(int focusGranted);
    private native void informAudioTrackGainNative(float gain);
}
