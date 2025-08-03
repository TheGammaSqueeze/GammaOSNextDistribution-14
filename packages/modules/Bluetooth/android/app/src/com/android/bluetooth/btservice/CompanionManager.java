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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.bluetooth.R;

import java.util.HashSet;
import java.util.Set;

/**
  A CompanionManager to specify parameters between companion devices and regular devices.

  1.  A paired device is recognized as a companion device if its METADATA_SOFTWARE_VERSION is
      set to BluetoothDevice.COMPANION_TYPE_PRIMARY or BluetoothDevice.COMPANION_TYPE_SECONDARY.
  2.  Only can have one companion device at a time.
  3.  Remove bond does not remove the companion device record.
  4.  Factory reset Bluetooth removes the companion device.
  5.  Companion device has individual GATT connection parameters.
*/

public class CompanionManager {
    private static final String TAG = "BluetoothCompanionManager";

    private BluetoothDevice mCompanionDevice;
    private int mCompanionType;

    private final int[] mGattConnHighPrimary;
    private final int[] mGattConnBalancePrimary;
    private final int[] mGattConnLowPrimary;
    private final int[] mGattConnHighSecondary;
    private final int[] mGattConnBalanceSecondary;
    private final int[] mGattConnLowSecondary;
    private final int[] mGattConnHighDefault;
    private final int[] mGattConnBalanceDefault;
    private final int[] mGattConnLowDefault;
    private final int[] mGattConnDckDefault;

    @VisibleForTesting static final int COMPANION_TYPE_NONE      = 0;
    @VisibleForTesting static final int COMPANION_TYPE_PRIMARY   = 1;
    @VisibleForTesting static final int COMPANION_TYPE_SECONDARY = 2;

    public static final int GATT_CONN_INTERVAL_MIN = 0;
    public static final int GATT_CONN_INTERVAL_MAX = 1;
    public static final int GATT_CONN_LATENCY      = 2;

    @VisibleForTesting static final String COMPANION_INFO = "bluetooth_companion_info";
    @VisibleForTesting static final String COMPANION_DEVICE_KEY = "companion_device";
    @VisibleForTesting static final String COMPANION_TYPE_KEY = "companion_type";

    static final String PROPERTY_HIGH_MIN_INTERVAL = "bluetooth.gatt.high_priority.min_interval";
    static final String PROPERTY_HIGH_MAX_INTERVAL = "bluetooth.gatt.high_priority.max_interval";
    static final String PROPERTY_HIGH_LATENCY = "bluetooth.gatt.high_priority.latency";
    static final String PROPERTY_BALANCED_MIN_INTERVAL =
            "bluetooth.gatt.balanced_priority.min_interval";
    static final String PROPERTY_BALANCED_MAX_INTERVAL =
            "bluetooth.gatt.balanced_priority.max_interval";
    static final String PROPERTY_BALANCED_LATENCY = "bluetooth.gatt.balanced_priority.latency";
    static final String PROPERTY_LOW_MIN_INTERVAL = "bluetooth.gatt.low_priority_min.interval";
    static final String PROPERTY_LOW_MAX_INTERVAL = "bluetooth.gatt.low_priority_max.interval";
    static final String PROPERTY_LOW_LATENCY = "bluetooth.gatt.low_priority.latency";
    static final String PROPERTY_DCK_MIN_INTERVAL = "bluetooth.gatt.dck_priority_min.interval";
    static final String PROPERTY_DCK_MAX_INTERVAL = "bluetooth.gatt.dck_priority_max.interval";
    static final String PROPERTY_DCK_LATENCY = "bluetooth.gatt.dck_priority.latency";
    static final String PROPERTY_SUFFIX_PRIMARY = ".primary";
    static final String PROPERTY_SUFFIX_SECONDARY = ".secondary";

    private final AdapterService mAdapterService;
    private final BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    private final Set<BluetoothDevice> mMetadataListeningDevices = new HashSet<>();

    public CompanionManager(AdapterService service, ServiceFactory factory) {
        mAdapterService = service;

        mGattConnHighDefault = new int[] {
                getGattConfig(PROPERTY_HIGH_MIN_INTERVAL,
                        R.integer.gatt_high_priority_min_interval),
                getGattConfig(PROPERTY_HIGH_MAX_INTERVAL,
                        R.integer.gatt_high_priority_max_interval),
                getGattConfig(PROPERTY_HIGH_LATENCY,
                        R.integer.gatt_high_priority_latency)};
        mGattConnBalanceDefault = new int[] {
                getGattConfig(PROPERTY_BALANCED_MIN_INTERVAL,
                        R.integer.gatt_balanced_priority_min_interval),
                getGattConfig(PROPERTY_BALANCED_MAX_INTERVAL,
                        R.integer.gatt_balanced_priority_max_interval),
                getGattConfig(PROPERTY_BALANCED_LATENCY,
                        R.integer.gatt_balanced_priority_latency)};
        mGattConnLowDefault = new int[] {
                getGattConfig(PROPERTY_LOW_MIN_INTERVAL, R.integer.gatt_low_power_min_interval),
                getGattConfig(PROPERTY_LOW_MAX_INTERVAL, R.integer.gatt_low_power_max_interval),
                getGattConfig(PROPERTY_LOW_LATENCY, R.integer.gatt_low_power_latency)};
        mGattConnDckDefault = new int[] {
                getGattConfig(PROPERTY_DCK_MIN_INTERVAL, R.integer.gatt_dck_priority_min_interval),
                getGattConfig(PROPERTY_DCK_MAX_INTERVAL, R.integer.gatt_dck_priority_max_interval),
                getGattConfig(PROPERTY_DCK_LATENCY, R.integer.gatt_dck_priority_latency)};

        mGattConnHighPrimary = new int[] {
                getGattConfig(PROPERTY_HIGH_MIN_INTERVAL + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_high_priority_min_interval_primary),
                getGattConfig(PROPERTY_HIGH_MAX_INTERVAL + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_high_priority_max_interval_primary),
                getGattConfig(PROPERTY_HIGH_LATENCY + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_high_priority_latency_primary)};
        mGattConnBalancePrimary = new int[] {
                getGattConfig(PROPERTY_BALANCED_MIN_INTERVAL + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_balanced_priority_min_interval_primary),
                getGattConfig(PROPERTY_BALANCED_MAX_INTERVAL + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_balanced_priority_max_interval_primary),
                getGattConfig(PROPERTY_BALANCED_LATENCY + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_balanced_priority_latency_primary)};
        mGattConnLowPrimary = new int[] {
                getGattConfig(PROPERTY_LOW_MIN_INTERVAL + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_low_power_min_interval_primary),
                getGattConfig(PROPERTY_LOW_MAX_INTERVAL + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_low_power_max_interval_primary),
                getGattConfig(PROPERTY_LOW_LATENCY + PROPERTY_SUFFIX_PRIMARY,
                        R.integer.gatt_low_power_latency_primary)};

        mGattConnHighSecondary = new int[] {
                getGattConfig(PROPERTY_HIGH_MIN_INTERVAL + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_high_priority_min_interval_secondary),
                getGattConfig(PROPERTY_HIGH_MAX_INTERVAL + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_high_priority_max_interval_secondary),
                getGattConfig(PROPERTY_HIGH_LATENCY + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_high_priority_latency_secondary)};
        mGattConnBalanceSecondary = new int[] {
                getGattConfig(PROPERTY_BALANCED_MIN_INTERVAL + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_balanced_priority_min_interval_secondary),
                getGattConfig(PROPERTY_BALANCED_MAX_INTERVAL + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_balanced_priority_max_interval_secondary),
                getGattConfig(PROPERTY_BALANCED_LATENCY + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_balanced_priority_latency_secondary)};
        mGattConnLowSecondary = new int[] {
                getGattConfig(PROPERTY_LOW_MIN_INTERVAL + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_low_power_min_interval_secondary),
                getGattConfig(PROPERTY_LOW_MAX_INTERVAL + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_low_power_max_interval_secondary),
                getGattConfig(PROPERTY_LOW_LATENCY + PROPERTY_SUFFIX_SECONDARY,
                        R.integer.gatt_low_power_latency_secondary)};
    }

    private int getGattConfig(String property, int resId) {
        return SystemProperties.getInt(property, mAdapterService.getResources().getInteger(resId));
    }

    void loadCompanionInfo() {
        synchronized (mMetadataListeningDevices) {
            String address = getCompanionPreferences().getString(COMPANION_DEVICE_KEY, "");

            try {
                mCompanionDevice = mAdapter.getRemoteDevice(address);
                mCompanionType = getCompanionPreferences().getInt(
                        COMPANION_TYPE_KEY, COMPANION_TYPE_NONE);
            } catch (IllegalArgumentException e) {
                mCompanionDevice = null;
                mCompanionType = COMPANION_TYPE_NONE;
            }
        }

        if (mCompanionDevice == null) {
            // We don't have any companion phone registered, try look from the bonded devices
            for (BluetoothDevice device : mAdapter.getBondedDevices()) {
                byte[] metadata = mAdapterService.getMetadata(device,
                        BluetoothDevice.METADATA_SOFTWARE_VERSION);
                if (metadata == null) {
                    continue;
                }
                String valueStr = new String(metadata);
                if ((valueStr.equals(BluetoothDevice.COMPANION_TYPE_PRIMARY)
                        || valueStr.equals(BluetoothDevice.COMPANION_TYPE_SECONDARY))) {
                    // found the companion device, store and unregister all listeners
                    Log.i(TAG, "Found companion device from the database!");
                    setCompanionDevice(device, valueStr);
                    break;
                }
                registerMetadataListener(device);
            }
        }
        Log.i(TAG, "Companion device is " + mCompanionDevice + ", type=" + mCompanionType);
    }

    final BluetoothAdapter.OnMetadataChangedListener mMetadataListener =
            new BluetoothAdapter.OnMetadataChangedListener() {
                @Override
                public void onMetadataChanged(BluetoothDevice device, int key, byte[] value) {
                    String valueStr = new String(value);
                    Log.d(TAG, String.format("Metadata updated in Device %s: %d = %s.", device,
                            key, value == null ? null : valueStr));
                    if (key == BluetoothDevice.METADATA_SOFTWARE_VERSION
                            && (valueStr.equals(BluetoothDevice.COMPANION_TYPE_PRIMARY)
                            || valueStr.equals(BluetoothDevice.COMPANION_TYPE_SECONDARY))) {
                        setCompanionDevice(device, valueStr);
                    }
                }
            };

    private void setCompanionDevice(BluetoothDevice companionDevice, String type) {
        synchronized (mMetadataListeningDevices) {
            Log.i(TAG, "setCompanionDevice: " + companionDevice + ", type=" + type);
            mCompanionDevice = companionDevice;
            mCompanionType = type.equals(BluetoothDevice.COMPANION_TYPE_PRIMARY)
                    ? COMPANION_TYPE_PRIMARY : COMPANION_TYPE_SECONDARY;

            // unregister all metadata listeners
            for (BluetoothDevice device : mMetadataListeningDevices) {
                try {
                    mAdapter.removeOnMetadataChangedListener(device, mMetadataListener);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "failed to unregister metadata listener for "
                            + device + " " + e);
                }
            }
            mMetadataListeningDevices.clear();

            SharedPreferences.Editor pref = getCompanionPreferences().edit();
            pref.putString(COMPANION_DEVICE_KEY, mCompanionDevice.getAddress());
            pref.putInt(COMPANION_TYPE_KEY, mCompanionType);
            pref.apply();
        }
    }

    private SharedPreferences getCompanionPreferences() {
        return mAdapterService.getSharedPreferences(COMPANION_INFO, Context.MODE_PRIVATE);
    }

    /**
     * Bond state change event from the AdapterService
     *
     * @param device the Bluetooth device
     * @param state the new Bluetooth bond state of the device
     */
    public void onBondStateChanged(BluetoothDevice device, int state) {
        synchronized (mMetadataListeningDevices) {
            if (mCompanionDevice != null) {
                // We already have the companion device, do not care bond state change any more.
                return;
            }
            switch (state) {
                case BluetoothDevice.BOND_BONDING:
                    registerMetadataListener(device);
                    break;
                case BluetoothDevice.BOND_NONE:
                    removeMetadataListener(device);
                    break;
                default:
                    break;
            }
        }
    }

    private void registerMetadataListener(BluetoothDevice device) {
        synchronized (mMetadataListeningDevices) {
            Log.d(TAG, "register metadata listener: " + device);
            try {
                mAdapter.addOnMetadataChangedListener(
                        device, mAdapterService.getMainExecutor(), mMetadataListener);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "failed to register metadata listener for "
                        + device + " " + e);
            }
            mMetadataListeningDevices.add(device);
        }
    }

    private void removeMetadataListener(BluetoothDevice device) {
        synchronized (mMetadataListeningDevices) {
            if (!mMetadataListeningDevices.contains(device)) return;

            Log.d(TAG, "remove metadata listener: " + device);
            try {
                mAdapter.removeOnMetadataChangedListener(device, mMetadataListener);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "failed to unregister metadata listener for "
                        + device + " " + e);
            }
            mMetadataListeningDevices.remove(device);
        }
    }


    /**
     * Method to get the stored companion device
     *
     * @return the companion Bluetooth device
     */
    public BluetoothDevice getCompanionDevice() {
        return mCompanionDevice;
    }

    /**
     * Method to check whether it is a companion device
     *
     * @param address the address of the device
     * @return true if the address is a companion device, otherwise false
     */
    public boolean isCompanionDevice(String address) {
        try {
            return isCompanionDevice(mAdapter.getRemoteDevice(address));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Method to check whether it is a companion device
     *
     * @param device the Bluetooth device
     * @return true if the device is a companion device, otherwise false
     */
    public boolean isCompanionDevice(BluetoothDevice device) {
        if (device == null) return false;
        return device.equals(mCompanionDevice);
    }

    /**
     * Method to reset the stored companion info
     */
    public void factoryReset() {
        synchronized (mMetadataListeningDevices) {
            mCompanionDevice = null;
            mCompanionType = COMPANION_TYPE_NONE;

            SharedPreferences.Editor pref = getCompanionPreferences().edit();
            pref.remove(COMPANION_DEVICE_KEY);
            pref.remove(COMPANION_TYPE_KEY);
            pref.apply();
        }
    }

    /**
     * Gets the GATT connection parameters of the device
     *
     * @param address the address of the Bluetooth device
     * @param type type of the parameter, can be GATT_CONN_INTERVAL_MIN, GATT_CONN_INTERVAL_MAX
     * or GATT_CONN_LATENCY
     * @param priority the priority of the connection, can be
     * BluetoothGatt.CONNECTION_PRIORITY_HIGH, BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER or
     * BluetoothGatt.CONNECTION_PRIORITY_BALANCED
     * @return the connection parameter in integer
     */
    public int getGattConnParameters(String address, int type, int priority) {
        int companionType = isCompanionDevice(address) ? mCompanionType : COMPANION_TYPE_NONE;
        int parameter;
        switch (companionType) {
            case COMPANION_TYPE_PRIMARY:
                parameter = getGattConnParameterPrimary(type, priority);
                break;
            case COMPANION_TYPE_SECONDARY:
                parameter = getGattConnParameterSecondary(type, priority);
                break;
            default:
                parameter = getGattConnParameterDefault(type, priority);
                break;
        }
        return parameter;
    }

    private int getGattConnParameterPrimary(int type, int priority) {
        switch (priority) {
            case BluetoothGatt.CONNECTION_PRIORITY_HIGH:
                return mGattConnHighPrimary[type];
            case BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER:
                return mGattConnLowPrimary[type];
        }
        return mGattConnBalancePrimary[type];
    }

    private int getGattConnParameterSecondary(int type, int priority) {
        switch (priority) {
            case BluetoothGatt.CONNECTION_PRIORITY_HIGH:
                return mGattConnHighSecondary[type];
            case BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER:
                return mGattConnLowSecondary[type];
        }
        return mGattConnBalanceSecondary[type];
    }

    private int getGattConnParameterDefault(int type, int mode) {
        switch (mode) {
            case BluetoothGatt.CONNECTION_PRIORITY_HIGH:
                return mGattConnHighDefault[type];
            case BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER:
                return mGattConnLowDefault[type];
            case BluetoothGatt.CONNECTION_PRIORITY_DCK:
                return mGattConnDckDefault[type];
        }
        return mGattConnBalanceDefault[type];
    }
}
