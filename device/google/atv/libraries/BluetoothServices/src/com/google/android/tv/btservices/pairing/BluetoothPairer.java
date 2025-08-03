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

package com.google.android.tv.btservices.pairing;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.tv.btservices.pairing.profiles.PairingProfileWrapper;
import com.google.android.tv.btservices.pairing.profiles.PairingProfileWrapperA2dp;
import com.google.android.tv.btservices.pairing.profiles.PairingProfileWrapperHidHost;

public final class BluetoothPairer {

    private static final String TAG = "Atv.BtPairer";

    // A typical device pairing process will proceed in order or bond, service discovery, and
    // profile connection.

    public static final int STATUS_ERROR = -1;
    public static final int STATUS_INIT = 0;
    public static final int STATUS_PAIRING = 3;
    public static final int STATUS_CONNECTING = 4;
    public static final int STATUS_DONE = 5;
    public static final int STATUS_CANCELLED = 6;
    public static final int STATUS_TIMEOUT = 7;

    private static final int MSG_PAIR = 1;
    private static final int MSG_ADD_DEVICE = 2;
    private static final int ADD_DEVICE_RETRY_MS = 1000;
    private static final int ADD_DEVICE_DELAY = 1000;

    private static final int MSG_TIMEOUT = 3;
    private static final int PAIRING_TIMEOUT_MS = 25000;
    private static final int CONNECTING_TIMEOUT_MS = 15000;

    private final Context context;
    private Listener mListener;
    private boolean mForgetOnFail = true;
    private BluetoothDevice mDevice;
    private int mBluetoothProfile;
    private PairingProfileWrapper mPairingProfileWrapper;

    private int status = STATUS_INIT;

    protected interface Listener {
        void onStatusChanged(BluetoothDevice device, int status);
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PAIR:
                    startBonding();
                    break;
                case MSG_ADD_DEVICE:
                    addDevice();
                    break;
                case MSG_TIMEOUT:
                    timeout();
                    break;
                default:
                    Log.i(TAG, "No handler case available for message: " + msg.what);
            }
        }
    };

    private final BroadcastReceiver linkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                onBondStateChanged(intent);
            } else if (getBroadcastListeningState(mBluetoothProfile).equals(action)) {
                onConnectionStateChanged(intent);
            } else if (BluetoothDevice.ACTION_UUID.equals(intent.getAction())) {
                onServicesDiscovered();
            }
        }
    };

    protected BluetoothPairer(Context context, int bluetoothProfile) {
        this.context = context.getApplicationContext();
        if (bluetoothProfile != BluetoothProfile.A2DP
                && bluetoothProfile != BluetoothProfile.HID_HOST) {
            throw new IllegalArgumentException(bluetoothProfile + " is not a supported profile");
        }
        mBluetoothProfile = bluetoothProfile;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        ServiceListener serviceConnection =
                new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceDisconnected(int profile) {
                        // TODO handle unexpected disconnection
                        Log.i(TAG, "Service disconnected, perhaps unexpectedly");
                        mPairingProfileWrapper = null;
                    }

                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        Log.i(TAG, "Connection made to bluetooth proxy.");
                        mPairingProfileWrapper = getPairingProfileWrapper(profile, proxy);
                    }
                };
        adapter.getProfileProxy(this.context, serviceConnection, bluetoothProfile);
        registerReceiver();
    }

    private static PairingProfileWrapper getPairingProfileWrapper(int bluetoothProfile,
            BluetoothProfile proxy) {
        if (BluetoothProfile.A2DP == bluetoothProfile) {
            return new PairingProfileWrapperA2dp(proxy);
        } else if (BluetoothProfile.HID_HOST == bluetoothProfile) {
            return new PairingProfileWrapperHidHost(proxy);
        } else {
            return null;
        }
    }

    // Bond and add the device as input after bonded.
    protected void startPairing(BluetoothDevice device, Listener listener, boolean forgetOnFail) {
        Log.i(TAG, "startPairing: " + device.getAddress() + " " + device.getName());
        if (isInProgress()) {
            Log.e(TAG, "Pairing already in progress, you must cancel the "
                    + "previous request first");
            return;
        }
        mForgetOnFail = forgetOnFail;
        mDevice = device;
        mListener = listener;
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            Log.i(TAG, "Already bonded " + device);
            onBonded();
            return;
        }
        mHandler.removeMessages(MSG_PAIR);
        mHandler.sendEmptyMessage(MSG_PAIR);
        mHandler.removeMessages(MSG_TIMEOUT);
        mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, PAIRING_TIMEOUT_MS);
    }

    protected void dispose() {
        if (isInProgress()) {
            doCancel(STATUS_CANCELLED);
        }
        mHandler.removeCallbacksAndMessages(null);
        unregisterReceiver();
        if (mPairingProfileWrapper != null) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            adapter.closeProfileProxy(mBluetoothProfile, mPairingProfileWrapper.getProxy());
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(getBroadcastListeningState(mBluetoothProfile));
        context.registerReceiver(linkStatusReceiver, filter);
    }

    private static String getBroadcastListeningState(int bluetoothProfile) {
        if (BluetoothProfile.A2DP == bluetoothProfile) {
            return BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED;
        } else if (BluetoothProfile.HID_HOST == bluetoothProfile) {
            return BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED;
        } else {
            return null;
        }
    }

    private void unregisterReceiver() {
        context.unregisterReceiver(linkStatusReceiver);
    }

    private boolean isInProgress() {
        return status != STATUS_INIT && status != STATUS_ERROR && status != STATUS_CANCELLED
                && status != STATUS_DONE && status != STATUS_TIMEOUT;
    }

    private void onBondStateChanged(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (!device.equals(mDevice)) {
            return;
        }

        int bondState =
                intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
        int previousBondState = intent.getIntExtra(
                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE);
        if (bondState == BluetoothDevice.BOND_NONE &&
                previousBondState == BluetoothDevice.BOND_BONDING) {
            onBondFailed();
        } else if (bondState == BluetoothDevice.BOND_BONDED) {
            onBonded();
        }
    }

    private void onConnectionStateChanged(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
        if (mDevice == null || !mDevice.equals(device)) {
            Log.e(TAG, "addAsInput (handler): non-device connecting: " + device);
            return;
        }
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                mHandler.post(BluetoothPairer.this::onAdded);
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                mHandler.post(BluetoothPairer.this::onAddFailed);
                break;
            case BluetoothProfile.STATE_CONNECTING:
            case BluetoothProfile.STATE_DISCONNECTING:
                Log.i(TAG, "addAsInput (handler): no action for transient states");
                break;
            default:
                Log.e(TAG, "addAsInput (handler): unknown state " + state);
        }
    }

    private void onServicesDiscovered() {
        // regardless of the UUID content, at this point, we're sure we can initiate a
        // profile connection.
        if (!mHandler.hasMessages(MSG_ADD_DEVICE)) {
            mHandler.sendEmptyMessageDelayed(MSG_ADD_DEVICE, ADD_DEVICE_DELAY);
        }
    }

    private void startBonding() {
        updateStatus(STATUS_PAIRING);
        if (mDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            if (!mDevice.createBond()) {
                onBondFailed();
            }
        } else {
            onBonded();
        }
    }

    // Open a connection to the profile host.
    private void onBonded() {
        mHandler.removeMessages(MSG_TIMEOUT);
        mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, CONNECTING_TIMEOUT_MS);
        serviceDiscovery();
    }

    private void onBondFailed() {
        Log.e(TAG, "There was an error bonding with the device.");
        updateStatus(STATUS_ERROR);
        unpairDevice(mDevice);
        mDevice = null;
    }

    // For a2dp devices, we must complete discovery before initiating a profile connection.
    // See b/135210487.
    private void serviceDiscovery() {
        if (mDevice == null) {
            Log.e(TAG, "serviceDiscovery(): mDevice is null");
            return;
        }
        mDevice.fetchUuidsWithSdp();
    }

    // Adding as input = CONNECTING
    private void addDevice() {
        if (mDevice == null) {
            Log.i(TAG, "No device to add");
            mHandler.post(this::onAddFailed);
            return;
        }
        if (mPairingProfileWrapper == null) {
            Log.i(TAG, "No Bluetooth proxy");
            mHandler.removeMessages(MSG_ADD_DEVICE);
            mHandler.sendEmptyMessageDelayed(MSG_ADD_DEVICE, ADD_DEVICE_RETRY_MS);
            return;
        }
        updateStatus(STATUS_CONNECTING);
        if (isDeviceAdded()) {
            mHandler.post(this::onAdded);
            return;
        }
        mPairingProfileWrapper.connect(mDevice);
    }

    private void onAdded() {
        updateStatus(STATUS_DONE);
        mHandler.removeMessages(MSG_TIMEOUT);
        mDevice = null;
    }

    private void onAddFailed() {
        Log.e(TAG, "There was an error adding the device as input.");
        updateStatus(STATUS_ERROR);
        mHandler.removeMessages(MSG_TIMEOUT);
        unpairDevice(mDevice);
        mDevice = null;
    }

    private void updateStatus(int status) {
        this.status = status;
        if (mListener != null) {
            mListener.onStatusChanged(mDevice, status);
        }
    }

    private boolean isDeviceAdded() {
        if (mPairingProfileWrapper == null) {
            return false;
        }
        if (mDevice == null) {
            return false;
        }
        for (BluetoothDevice device : mPairingProfileWrapper.getConnectedDevices()) {
            Log.i(TAG, "Device connected: " + device);
            if (TextUtils.equals(device.getAddress(), mDevice.getAddress())) {
                return true;
            }
        }
        return false;
    }

    private void doCancel(int status) {
        if (isInProgress()) {
            Log.i(TAG, "Pairing process has already begun, forcing cancel anyway.");
            updateStatus(status);
        }
        mHandler.removeMessages(MSG_TIMEOUT);
        mHandler.removeMessages(MSG_PAIR);
        if (mDevice != null && mPairingProfileWrapper != null) {
            mPairingProfileWrapper.disconnect(mDevice);
        }
        unpairDevice(mDevice);
        mDevice = null;
        updateStatus(STATUS_ERROR);
    }

    private void unpairDevice(BluetoothDevice device) {
        if (device == null) {
            return;
        }
        if (BluetoothDevice.BOND_BONDING == device.getBondState()) {
            device.cancelBondProcess();
        }
        if (mForgetOnFail) {
            final boolean successful = device.removeBond();
            if (successful) {
                Log.i(TAG, "Bluetooth device successfully unpaired: " + device.getName());
            } else {
                Log.i(TAG, "Failed to unpair device: " + device.getName());
            }
        }
    }

    private void timeout() {
        Log.e(TAG, "Bluetooth pairing timed out, cancelling...");
        doCancel(STATUS_TIMEOUT);
    }
}
