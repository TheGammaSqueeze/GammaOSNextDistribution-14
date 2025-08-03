/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.GENERAL_SLICE_URI;

import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.tv.settings.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** The Service for handling Bluetooth-related logic. */
public class BluetoothDevicesService extends Service {

    private static final boolean DEBUG = false;
    private static final String TAG = "BtDevicesServices";

    private final List<BluetoothDeviceProvider.Listener> mListeners = new ArrayList<>();
    private final Binder mBinder = new LocalBinder();
    protected final Handler mHandler = new Handler(Looper.getMainLooper());

    /** Binder in BluetoothDeviceService. */
    public class LocalBinder extends Binder implements BluetoothDeviceProvider {

        public List<BluetoothDevice> getDevices() {
            return BluetoothDevicesService.this.getDevices();
        }

        @Override
        public void addListener(BluetoothDeviceProvider.Listener listener) {
            mHandler.post(() -> {
                mListeners.add(listener);

                // Trigger first update after listener callback is registered.
                for (BluetoothDevice device : getDevices()) {
                    if (device.isConnected()) {
                        listener.onDeviceUpdated(device);
                    }
                }
            });
        }

        @Override
        public void removeListener(BluetoothDeviceProvider.Listener listener) {
            mHandler.post(() -> mListeners.remove(listener));
        }

        @Override
        public void connectDevice(BluetoothDevice device) {
            BluetoothDevicesService.this.connectDevice(device);
        }

        @Override
        public void disconnectDevice(BluetoothDevice device) {
            BluetoothDevicesService.this.disconnectDevice(device);
        }

        @Override
        public void forgetDevice(BluetoothDevice device) {
            BluetoothDevicesService.this.forgetDevice(device);
        }

        @Override
        public void renameDevice(BluetoothDevice device, String newName) {
            BluetoothDevicesService.this.renameDevice(device, newName);
        }
    }

    BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final BluetoothDevice device =
                    intent == null ? null : intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            // The sequence of a typical connection is: acl connected, bonding, bonded, profile
            // connecting, profile connected.
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                switch (state) {
                    case BluetoothDevice.BOND_BONDED:
                        break;
                    case BluetoothDevice.BOND_NONE:
                        mHandler.post(() -> onDeviceUpdated(device));
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        break;
                    default:
                        if (DEBUG) {
                            Log.e(TAG, "unknown state " + state + " " + device);
                        }
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                // Actively refresh the connected devices slice.
                if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                    getContentResolver().notifyChange(GENERAL_SLICE_URI, null);
                }
            } else {
                switch(action) {
                    case BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED:
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            Log.i(TAG, "bonded " + device);
                            mHandler.post(() -> onDeviceUpdated(device));
                        }
                        break;
                    case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                        final int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                        mHandler.post(() -> {
                            onA2dpConnectionStateChanged(device.getName(), state);
                            if (state == BluetoothProfile.STATE_CONNECTED
                                    || state == BluetoothProfile.STATE_DISCONNECTED) {
                                onDeviceUpdated(device);
                            }
                        });
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        Log.i(TAG, "acl connected " + device);
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            mHandler.post(() -> onDeviceUpdated(device));
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        Log.i(TAG, "acl disconnected " + device);
                        mHandler.post(() -> onDeviceUpdated(device));
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                        Log.i(TAG, "acl disconnect requested: " + device);
                        break;
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.e(TAG, "onCreate");
        }
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED); // Headset connection
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); // Bluetooth toggle
        registerReceiver(mBluetoothReceiver, filter);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.e(TAG, "onDestroy");
        }
        unregisterReceiver(mBluetoothReceiver);
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        for (BluetoothDevice device: getDevices()) {
            if (!device.isConnected()) {
                continue;
            }
            writer.printf("%s (%s):%n", device.getName(), device.getAddress());
        }
    }

    private void connectDevice(BluetoothDevice device) {
        if (device != null) {
            CachedBluetoothDevice cachedDevice =
                    AccessoryUtils.getCachedBluetoothDevice(this, device);
            if (cachedDevice != null) {
                Log.i(TAG, "connectDevice: " + device);
                cachedDevice.connect();
            }
        }
    }

    private void disconnectDevice(BluetoothDevice device) {
        if (device != null) {
            CachedBluetoothDevice cachedDevice =
                    AccessoryUtils.getCachedBluetoothDevice(this, device);
            if (cachedDevice != null) {
                Log.i(TAG, "disconnectDevice: " + device);
                cachedDevice.disconnect();
            }
        }
    }

    private static void forgetDevice(BluetoothDevice device) {
        if (device == null || !device.removeBond()) {
            Log.w(TAG, "failed to remove bond: " + device);
        }
    }

    private void renameDevice(BluetoothDevice device, String newName) {
        if (device != null) {
            device.setAlias(newName);
            mHandler.post(() -> onDeviceUpdated(device));
        }
    }

    private void onA2dpConnectionStateChanged(String deviceName, int connectionStatus) {
        String resStr;
        String text;
        switch (connectionStatus) {
            case BluetoothProfile.STATE_CONNECTED:
                resStr = getResources().getString(R.string.bluetooth_device_connected_toast);
                text = String.format(resStr, deviceName);
                Toast.makeText(BluetoothDevicesService.this.getApplicationContext(),
                        text, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onA2dpConnectionStateChanged: Connected, toasting: " + text);
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                resStr = getResources().getString(R.string.bluetooth_device_disconnected_toast);
                text = String.format(resStr, deviceName);
                Toast.makeText(BluetoothDevicesService.this.getApplicationContext(),
                        text, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onA2dpConnectionStateChanged: Disconnected, toasting: " + text);
                break;
            case BluetoothProfile.STATE_CONNECTING:
                Log.d(TAG, "onA2dpConnectionStateChanged: Connecting");
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                Log.d(TAG, "onA2dpConnectionStateChanged: Disconnecting");
                break;
            default:
                Log.d(TAG, "onA2dpConnectionStateChanged: " + connectionStatus);
                break;
        }
    }

    private void onDeviceUpdated(BluetoothDevice device) {
        mListeners.forEach(listener -> listener.onDeviceUpdated(device));
    }

    /** Returns the BluetoothDevice object with the input address. */
    public static BluetoothDevice findDevice(String address) {
        List<BluetoothDevice> devices = getDevices();
        BluetoothDevice curDevice = null;
        for (BluetoothDevice device: devices) {
            if (address.equals(device.getAddress())) {
                curDevice = device;
                break;
            }
        }
        return curDevice;
    }

    private static List<BluetoothDevice> getDevices() {
        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            return new ArrayList<>(btAdapter.getBondedDevices());
        }
        return new ArrayList<>(); // Empty list
    }
}
