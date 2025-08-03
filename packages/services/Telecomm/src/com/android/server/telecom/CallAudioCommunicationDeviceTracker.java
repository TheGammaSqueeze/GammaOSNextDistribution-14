/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.telecom;

import static com.android.server.telecom.AudioRoute.BT_AUDIO_DEVICE_INFO_TYPES;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.flags.Flags;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Helper class used to keep track of the requested communication device within Telecom for audio
 * use cases. Handles the set/clear communication use case logic for all audio routes (speaker, BT,
 * headset, and earpiece). For BT devices, this handles switches between hearing aids, SCO, and LE
 * audio (also takes into account switching between multiple LE audio devices).
 */
public class CallAudioCommunicationDeviceTracker {

    // Use -1 indicates device is not set for any communication use case
    private static final int sAUDIO_DEVICE_TYPE_INVALID = -1;
    private AudioManager mAudioManager;
    private BluetoothRouteManager mBluetoothRouteManager;
    private int mAudioDeviceType = sAUDIO_DEVICE_TYPE_INVALID;
    // Keep track of the locally requested BT audio device if set
    private String mBtAudioDevice = null;
    private final Semaphore mLock =  new Semaphore(1);

    public CallAudioCommunicationDeviceTracker(Context context) {
        mAudioManager = context.getSystemService(AudioManager.class);
    }

    public void setBluetoothRouteManager(BluetoothRouteManager bluetoothRouteManager) {
        mBluetoothRouteManager = bluetoothRouteManager;
    }

    public boolean isAudioDeviceSetForType(int audioDeviceType) {
        return mAudioDeviceType == audioDeviceType;
    }

    public int getCurrentLocallyRequestedCommunicationDevice() {
       return mAudioDeviceType;
    }

    @VisibleForTesting
    public void setTestCommunicationDevice(int audioDeviceType) {
        mAudioDeviceType = audioDeviceType;
    }

    public void clearBtCommunicationDevice() {
        if (mBtAudioDevice == null) {
            Log.i(this, "No bluetooth device was set for communication that can be cleared.");
            return;
        }
        // If mBtAudioDevice is set, we know a BT audio device was set for communication so
        // mAudioDeviceType corresponds to a BT device type (e.g. hearing aid, SCO, LE).
        clearCommunicationDevice(mAudioDeviceType);
    }

    /*
     * Sets the communication device for the passed in audio device type, if it's available for
     * communication use cases. Tries to clear any communication device which was previously
     * requested for communication before setting the new device.
     * @param audioDeviceTypes The supported audio device types for the device.
     * @param btDevice The bluetooth device to connect to (only used for switching between multiple
     *        LE audio devices).
     * @return {@code true} if the device was set for communication, {@code false} if the device
     * wasn't set.
     */
    public boolean setCommunicationDevice(int audioDeviceType,
            BluetoothDevice btDevice) {
        if (Flags.communicationDeviceProtectedByLock()) {
            mLock.tryAcquire();
        }
        // There is only one audio device type associated with each type of BT device.
        boolean isBtDevice = BT_AUDIO_DEVICE_INFO_TYPES.contains(audioDeviceType);
        Log.i(this, "setCommunicationDevice: type = %s, isBtDevice = %s, btDevice = %s",
                audioDeviceType, isBtDevice, btDevice);

        // Account for switching between multiple LE audio devices.
        boolean handleLeAudioDeviceSwitch = btDevice != null
                && !btDevice.getAddress().equals(mBtAudioDevice);
        if ((audioDeviceType == mAudioDeviceType
                || isUsbHeadsetType(audioDeviceType, mAudioDeviceType))
                && !handleLeAudioDeviceSwitch) {
            Log.i(this, "Communication device is already set for this audio type");
            return false;
        }

        AudioDeviceInfo activeDevice = null;
        List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();
        if (devices.size() == 0) {
            Log.w(this, "No communication devices available");
            return false;
        }

        for (AudioDeviceInfo device : devices) {
            Log.i(this, "Available device type: " + device.getType());
            // Ensure that we do not select the same BT LE audio device for communication.
            if ((audioDeviceType == device.getType()
                    || isUsbHeadsetType(audioDeviceType, device.getType()))
                    && !device.getAddress().equals(mBtAudioDevice)) {
                activeDevice = device;
                break;
            }
        }

        if (activeDevice == null) {
            Log.i(this, "No active device of type(s) %s available",
                    audioDeviceType == AudioDeviceInfo.TYPE_WIRED_HEADSET
                            ? Arrays.asList(AudioDeviceInfo.TYPE_WIRED_HEADSET,
                                    AudioDeviceInfo.TYPE_USB_HEADSET)
                            : audioDeviceType);
            return false;
        }

        // Force clear previous communication device, if one was set, before setting the new device.
        if (mAudioDeviceType != sAUDIO_DEVICE_TYPE_INVALID) {
            clearCommunicationDevice(mAudioDeviceType);
        }

        // Turn activeDevice ON.
        boolean result = mAudioManager.setCommunicationDevice(activeDevice);
        if (!result) {
            Log.w(this, "Could not set active device");
        } else {
            Log.i(this, "Active device set");
            mAudioDeviceType = activeDevice.getType();
            if (isBtDevice) {
                mBtAudioDevice = activeDevice.getAddress();
                if (audioDeviceType == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    mBluetoothRouteManager.onAudioOn(mBtAudioDevice);
                }
            } else if (Flags.communicationDeviceProtectedByLock()) {
                // Clear BT device if it's still stored. Handles race condition for when a non-BT
                // device is set for communication shortly after a BT (LE) device is set for
                // communication but the selection hasn't been cleared yet.
                mBtAudioDevice = null;
            }
        }
        if (Flags.communicationDeviceProtectedByLock()) {
            mLock.release();
        }
        return result;
    }

    /*
     * Clears the communication device for the passed in audio device types, given that the device
     * has previously been set for communication.
     * @param audioDeviceTypes The supported audio device types for the device.
     */
    public void clearCommunicationDevice(int audioDeviceType) {
        if (Flags.communicationDeviceProtectedByLock()) {
            mLock.tryAcquire();
        }
        // There is only one audio device type associated with each type of BT device.
        boolean isBtDevice = BT_AUDIO_DEVICE_INFO_TYPES.contains(audioDeviceType);
        Log.i(this, "clearCommunicationDevice: type = %s, isBtDevice = %s",
                audioDeviceType, isBtDevice);

        if (audioDeviceType != mAudioDeviceType
                && !isUsbHeadsetType(audioDeviceType, mAudioDeviceType)) {
            Log.i(this, "Unable to clear communication device of type(s), %s. "
                    + "Device does not correspond to the locally requested device type.",
                    audioDeviceType == AudioDeviceInfo.TYPE_WIRED_HEADSET
                            ? Arrays.asList(AudioDeviceInfo.TYPE_WIRED_HEADSET,
                                    AudioDeviceInfo.TYPE_USB_HEADSET)
                            : audioDeviceType
            );
            return;
        }

        if (mAudioManager == null) {
            Log.i(this, "clearCommunicationDevice: mAudioManager is null");
            return;
        }

        // Clear device and reset locally saved device type.
        mAudioManager.clearCommunicationDevice();
        mAudioDeviceType = sAUDIO_DEVICE_TYPE_INVALID;

        if (isBtDevice && mBtAudioDevice != null) {
            // Signal that BT audio was lost for device.
            mBluetoothRouteManager.onAudioLost(mBtAudioDevice);
            mBtAudioDevice = null;
        }
        if (Flags.communicationDeviceProtectedByLock()) {
            mLock.release();
        }
    }

    private boolean isUsbHeadsetType(int audioDeviceType, int sourceType) {
        return audioDeviceType != AudioDeviceInfo.TYPE_WIRED_HEADSET
                ? false : sourceType == AudioDeviceInfo.TYPE_USB_HEADSET;
    }
}
