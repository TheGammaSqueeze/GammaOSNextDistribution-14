/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.text.Html;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.tv.settings.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Provide utilities for Remote & Accessories. */
final class AccessoryUtils {

    public static final String TAG = "AccessoryUtils";

    private static final int MINOR_MASK = 0b11111100;
    // Includes any generic keyboards or pointers, and any joystick, game pad, or remote subtypes.
    private static final int MINOR_REMOTE_MASK = 0b11001100;
    private static List<String> sKnownDeviceLabels = null;

    /** This allows OEM to easily override the main Service if desired. */
    public static Class getBluetoothDeviceServiceClass() {
        return BluetoothDevicesService.class;
    }

    public static LocalBluetoothManager getLocalBluetoothManager(Context context) {
        final FutureTask<LocalBluetoothManager> localBluetoothManagerFutureTask =
                new FutureTask<>(
                        // Avoid StrictMode ThreadPolicy violation
                        () -> LocalBluetoothManager.getInstance(
                                context, (c, bluetoothManager) -> {
                                })
                );
        try {
            localBluetoothManagerFutureTask.run();
            return localBluetoothManagerFutureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, "Error getting LocalBluetoothManager.", e);
            return null;
        }
    }

    public static CachedBluetoothDevice getCachedBluetoothDevice(
            Context context, BluetoothDevice device) {
        LocalBluetoothManager localBluetoothManager = getLocalBluetoothManager(context);
        if (localBluetoothManager != null) {
            return localBluetoothManager.getCachedDeviceManager().findDevice(device);
        }
        return null;
    }

    public static BluetoothAdapter getDefaultBluetoothAdapter() {
        final FutureTask<BluetoothAdapter> defaultBluetoothAdapterFutureTask =
                new FutureTask<>(
                        // Avoid StrictMode ThreadPolicy violation
                        BluetoothAdapter::getDefaultAdapter);
        try {
            defaultBluetoothAdapterFutureTask.run();
            return defaultBluetoothAdapterFutureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, "Error getting default BluetoothAdapter.", e);
            return null;
        }
    }

    public static String getLocalName(BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        return device.getAlias();
    }

    public static boolean isBluetoothEnabled() {
        return getDefaultBluetoothAdapter() != null && getDefaultBluetoothAdapter().isEnabled();
    }

    public static boolean isConnected(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        return device.getBondState() == BluetoothDevice.BOND_BONDED && device.isConnected();
    }

    public static boolean isBonded(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        return device.getBondState() == BluetoothDevice.BOND_BONDED && !device.isConnected();
    }

    public static boolean isRemoteClass(BluetoothDevice device) {
        if (device == null || device.getBluetoothClass() == null) {
            return false;
        }
        int major = device.getBluetoothClass().getMajorDeviceClass();
        int minor = device.getBluetoothClass().getDeviceClass() & MINOR_MASK;
        return BluetoothClass.Device.Major.PERIPHERAL == major
                && (minor & ~MINOR_REMOTE_MASK) == 0;
    }

    // For partner, this will be used to identify official device to omit it in the generic
    // accessories section since the device's settings will be displayed in partner-implemented
    // Slice.
    public static boolean isKnownDevice(Context context, BluetoothDevice device) {
        if (device == null || device.getName() == null) {
            return false;
        }
        if (sKnownDeviceLabels == null) {
            if (context == null) {
                return false;
            } else {
                sKnownDeviceLabels =
                        Collections.unmodifiableList(
                                Arrays.asList(context.getResources().getStringArray(
                                        R.array.known_bluetooth_device_labels)));
                // For backward compatibility, the customization name used to be known_remote_labels
                if (sKnownDeviceLabels.isEmpty()) {
                    sKnownDeviceLabels = Collections.unmodifiableList(
                            Arrays.asList(
                                context.getResources().getStringArray(
                                    R.array.known_remote_labels)));
                }
            }
        }

        final String name = device.getName().toLowerCase();
        for (String knownLabel : sKnownDeviceLabels) {
            if (name.contains(knownLabel.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static String getHtmlEscapedDeviceName(@Nullable BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null || bluetoothDevice.getName() == null) {
            return null;
        }
        return Html.escapeHtml(bluetoothDevice.getName());
    }

    public static boolean isBluetoothHeadset(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        final BluetoothClass bluetoothClass = device.getBluetoothClass();
        final int devClass = bluetoothClass.getDeviceClass();
        return (devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                || devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
                || devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
                || devClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
                || devClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO);
    }

    static boolean isA2dpSource(BluetoothDevice device) {
        return device != null && device.getBluetoothClass() != null
                && device.getBluetoothClass().doesClassMatch(BluetoothProfile.A2DP);
    }

    /** Returns true if the BluetoothDevice is the active audio output, false otherwise. */
    static boolean isActiveAudioOutput(BluetoothDevice device) {
        if (device != null) {
            final BluetoothAdapter btAdapter = getDefaultBluetoothAdapter();
            if (btAdapter != null) {
                return btAdapter.getActiveDevices(BluetoothProfile.A2DP).contains(device);
            }
        }
        return false;
    }

    /**
     * Sets the specified BluetoothDevice as the active audio output. Passing `null`
     * resets the active audio output to the default. Returns false on immediate error,
     * true otherwise.
     */
    static boolean setActiveAudioOutput(BluetoothDevice device) {
        // null is an accepted value for unsetting the active audio output
        final BluetoothAdapter btAdapter = getDefaultBluetoothAdapter();
        if (btAdapter != null) {
            if (device == null) {
                return btAdapter.removeActiveDevice(BluetoothAdapter.ACTIVE_DEVICE_AUDIO);
            } else {
                return btAdapter.setActiveDevice(device, BluetoothAdapter.ACTIVE_DEVICE_AUDIO);
            }
        }
        return false;
    }

    /**
     * Returns true if the CachedBluetoothDevice supports an audio profile (A2DP for now),
     * false otherwise.
     */
    public static boolean hasAudioProfile(CachedBluetoothDevice cachedDevice) {
        if (cachedDevice != null) {
            for (LocalBluetoothProfile profile : cachedDevice.getProfiles()) {
                if (profile.getProfileId() == BluetoothProfile.A2DP) {
                    return true;
                }
            }
        }
        return false;
    }

    private AccessoryUtils() {
        // do not allow instantiation
    }
}
