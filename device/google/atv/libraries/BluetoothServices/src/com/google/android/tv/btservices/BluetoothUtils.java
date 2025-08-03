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

package com.google.android.tv.btservices;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class BluetoothUtils {

    private static final String TAG = "Atv.BluetoothUtils";

    private static List<String> sKnownRemoteLabels = null;
    private static List<String> sOfficialRemoteLabels = null;
    private static List<String> sBtDeviceServiceUpdatableRemoteLabels = null;
    private static final int MINOR_MASK = 0b11111100;

    private static final int MINOR_DEVICE_CLASS_POINTING = 0b10000000;
    private static final int MINOR_DEVICE_CLASS_KEYBOARD = 0b01000000;
    private static final int MINOR_DEVICE_CLASS_JOYSTICK = 0b00000100;
    private static final int MINOR_DEVICE_CLASS_GAMEPAD = 0b00001000;
    private static final int MINOR_DEVICE_CLASS_REMOTE = 0b00001100;

    // Includes any generic keyboards or pointers, and any joystick, game pad, or remote subtypes.
    private static final int MINOR_REMOTE_MASK = 0b11001100;

    public static boolean isRemoteClass(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        int major = device.getBluetoothClass().getMajorDeviceClass();
        int minor = device.getBluetoothClass().getDeviceClass() & MINOR_MASK;
        return BluetoothClass.Device.Major.PERIPHERAL == major
            && (minor & ~MINOR_REMOTE_MASK) == 0;
    }

    private static void setRemoteLabels(Context context) {
        if (context == null) {
            return;
        }
        sKnownRemoteLabels = Collections.unmodifiableList(Arrays.asList(
                context.getResources().getStringArray(R.array.known_bluetooth_device_labels)));
        // For backward compatibility, the customization name used to be known_remote_labels
        if (sKnownRemoteLabels.isEmpty()) {
            sKnownRemoteLabels = Collections.unmodifiableList(
                    Arrays.asList(
                        context.getResources().getStringArray(
                            R.array.known_remote_labels)));
        }

        sOfficialRemoteLabels = Collections.unmodifiableList(Arrays.asList(
                context.getResources().getStringArray(R.array.official_bt_device_labels)));

        sBtDeviceServiceUpdatableRemoteLabels = Collections.unmodifiableList(Arrays.asList(
                context.getResources().getStringArray(R.array.bt_device_service_updatable_labels)));
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

    public static String getName(BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        return device.getAlias() != null ? device.getAlias() : device.getName();
    }

    public static String getOriginalName(BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        return device.getName();
    }

    public static boolean isRemote(Context context, BluetoothDevice device) {
        if (sKnownRemoteLabels == null) {
            setRemoteLabels(context);
        }
        if (device == null) {
            return false;
        }
        if (device.getName() == null) {
            return false;
        }

        if (sKnownRemoteLabels == null) {
            return false;
        }

        final String name = device.getName().toLowerCase();
        for (String knownLabel: sKnownRemoteLabels) {
            if (name.contains(knownLabel)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBluetoothHeadset(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        final BluetoothClass bluetoothClass = device.getBluetoothClass();
        final int devClass = bluetoothClass.getDeviceClass();
        return (devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO);
    }

    public static boolean isLeCompatible(BluetoothDevice device) {
        return device != null && (device.getType() == BluetoothDevice.DEVICE_TYPE_LE ||
                device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL);
    }

    @SuppressLint("NewApi") // Hidden API made public
    public static boolean isA2dpSource(BluetoothDevice device) {
        return device != null && device.getBluetoothClass() != null &&
                device.getBluetoothClass().doesClassMatch(BluetoothProfile.A2DP);
    }

    /**
     * If a device's metadata (manufacturer, model) or name matches against a predefined list,
     * treat it as an official device to be used with the host device.
     */
    public static boolean isOfficialDevice(Context context, BluetoothDevice device) {
        boolean isManufacturerOfficial =
                isBluetoothDeviceMetadataInList(
                        context,
                        device,
                        BluetoothDevice.METADATA_MANUFACTURER_NAME,
                        R.array.official_bt_device_manufacturer_names);
        boolean isModelOfficial =
                isBluetoothDeviceMetadataInList(
                        context,
                        device,
                        BluetoothDevice.METADATA_MODEL_NAME,
                        R.array.official_bt_device_model_names);

        if (isManufacturerOfficial && isModelOfficial) {
            return true;
        }

        if (sOfficialRemoteLabels == null) {
            setRemoteLabels(context);
        }

        if (device == null || device.getName() == null) {
            return false;
        }

        if (sOfficialRemoteLabels == null) {
            return false;
        }

        final String name = device.getName().toLowerCase();
        for (String knownLabel : sOfficialRemoteLabels) {
            if (name.contains(knownLabel)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOfficialRemote(Context context, BluetoothDevice device) {
        return isRemote(context, device) && isOfficialDevice(context, device);
    }

    public static boolean isBtDeviceServiceUpdableRemote(Context context, BluetoothDevice device) {
        if (sBtDeviceServiceUpdatableRemoteLabels == null) {
            setRemoteLabels(context);
        }

        if (device == null || device.getName() == null) {
            return false;
        }

        if (sBtDeviceServiceUpdatableRemoteLabels == null) {
            return false;
        }

        final String name = device.getName().toLowerCase();
        for (String knownLabel : sBtDeviceServiceUpdatableRemoteLabels) {
            if (name.contains(knownLabel)) {
                return true;
            }
        }
        return false;
    }


    public static boolean supportBtDeviceServiceUpdate(Context context, BluetoothDevice device) {
        return isRemote(context, device) && isBtDeviceServiceUpdableRemote(context, device);
    }

    public static int getIcon(Context context, BluetoothDevice device) {
        if (device == null) {
            return 0;
        }
        final BluetoothClass bluetoothClass = device.getBluetoothClass();
        final int devClass = bluetoothClass.getDeviceClass();
        // Below ordering does matter
        if (isOfficialRemote(context, device)) {
            return R.drawable.ic_official_remote;
        } else if (isRemote(context, device)) {
            return R.drawable.ic_games;
        } else if (isBluetoothHeadset(device)) {
            return R.drawable.ic_headset;
        } else if ((devClass & MINOR_DEVICE_CLASS_POINTING) != 0) {
            return R.drawable.ic_mouse;
        } else if (isA2dpSource(device)) {
            return R.drawable.ic_baseline_smartphone_24dp;
        } else if ((devClass & MINOR_DEVICE_CLASS_REMOTE) != 0) {
            return R.drawable.ic_games;
        } else if ((devClass & MINOR_DEVICE_CLASS_JOYSTICK) != 0) {
            return R.drawable.ic_games;
        } else if ((devClass & MINOR_DEVICE_CLASS_GAMEPAD) != 0) {
            return R.drawable.ic_games;
        } else if ((devClass & MINOR_DEVICE_CLASS_KEYBOARD) != 0) {
            return R.drawable.ic_keyboard;
        }
        // Default for now
        return R.drawable.ic_bluetooth;
    }

    /**
     * @param context the context
     * @param device the bluetooth device
     * @param metadataKey one of BluetoothDevice.METADATA_*
     * @param stringArrayResId resource Id of <string-array> to match the metadata against
     * @return whether the specified metadata in within the list of stringArrayResId.
     */
    public static boolean isBluetoothDeviceMetadataInList(
            Context context, BluetoothDevice device, int metadataKey, int stringArrayResId) {
        if (context == null || device == null) {
            return false;
        }
        byte[] metadataBytes = device.getMetadata(metadataKey);
        if (metadataBytes == null) {
            return false;
        }
        final List<String> stringResList =
                Arrays.asList(context.getResources().getStringArray(stringArrayResId));
        if (stringResList == null || stringResList.isEmpty()) {
            return false;
        }
        for (String res : stringResList) {
            if (res.equals(new String(metadataBytes))) {
                return true;
            }
        }
        return false;
    }

    public static Class getBluetoothDeviceServiceClass(Context context) {
        String str = context.getString(R.string.bluetooth_device_service_class);
        try {
            return Class.forName(str);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class not found: " + str);
            return null;
        }
    }

    public static LocalBluetoothManager getLocalBluetoothManager(Context context) {
        final FutureTask<LocalBluetoothManager> localBluetoothManagerFutureTask =
                new FutureTask<>(
                        // Avoid StrictMode ThreadPolicy violation
                        () -> LocalBluetoothManager.getInstance(
                                context, (c, bluetoothManager) -> {}));
        try {
            localBluetoothManagerFutureTask.run();
            return localBluetoothManagerFutureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, "Error getting LocalBluetoothManager.", e);
            return null;
        }
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

    public static CachedBluetoothDevice getCachedBluetoothDevice(
            Context context, BluetoothDevice device) {
        LocalBluetoothManager localBluetoothManager = getLocalBluetoothManager(context);
        if (localBluetoothManager != null) {
            return localBluetoothManager.getCachedDeviceManager().findDevice(device);
        }
        return null;
    }

    /** Returns true if the BluetoothDevice is the active audio output, false otherwise. */
    public static boolean isActiveAudioOutput(BluetoothDevice device) {
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
    public static boolean setActiveAudioOutput(BluetoothDevice device) {
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
}
