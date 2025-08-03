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

package com.google.android.tv.btservices.remote;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

public abstract class Transport extends BluetoothGattCallback {

    private static final String TAG = "Atv.Transport";

    public static class Result {

        public static final int SUCCESS = 1;
        public static final int FAILURE = 2;
        public static final int FAILURE_GATT_DISCONNECTED = 3;
        public static final int FAILURE_LOCKED = 4;
        public static final int FAILURE_TIMED_OUT = 5;

        private byte[] mData;
        private int mCode;

        public Result(byte[] data) {
            this(data, SUCCESS);
        }

        public Result(byte[] data, int code) {
            mData = data;
            mCode = code;
        }

        public byte[] data() {
            return mData;
        }

        public int code() {
            return mCode;
        }
    }

    public interface Factory {
        Transport build(BluetoothDevice device, Runnable pendingRequest, Handler handler,
                Context context, TransportManager manager);
    }

    private Runnable mPendingRequest;
    private Runnable mReadyToConnect;
    private TransportManager mManager;
    protected Handler mHandler;
    protected BluetoothGatt mGatt;

    protected Transport(BluetoothDevice device, Runnable pendingRequest, Handler handler,
            Context context, TransportManager manager) {
        mPendingRequest = pendingRequest;
        mHandler = handler;
        mManager = manager;
        mReadyToConnect = () -> {
            mGatt = device.connectGatt(context, false, this);
        };
    }

    /**
     * This method must be called by subclasses right after constructor initializations.
     */
    protected void connect() {
        mHandler.post(mReadyToConnect);
    }

    protected abstract UUID[] getServiceUuids();
    protected abstract boolean initCharacteristicsImpl(List<BluetoothGattCharacteristic> chars);
    protected abstract void onCharacteristicChangedImpl(BluetoothGattCharacteristic characteristic);
    public abstract boolean ready();
    public abstract boolean write(byte reqType, byte[] vals);
    public abstract boolean read(byte reqType);
    public abstract boolean meta(byte reqType);
    public abstract Byte getExpectedResponse(byte reqType);

    public void shutdown() {
        mGatt.close();
        mGatt = null;
    }

    protected void onWritten(BluetoothGattCharacteristic gattch, int status) {
        final byte[] bytes = gattch != null && gattch.getValue() != null ? gattch.getValue() : null;
        mManager.onWritten(status, bytes);
    }

    protected void onRead(BluetoothGattCharacteristic gattch, int status) {
        // Default no action
    }

    protected void onResponse(byte status, byte[] bytes) {
        mManager.onResponse(this, status, bytes);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        try {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    mHandler.post(() -> onGattStateChanged(TransportManager.GATT_CONNECTED));
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    mHandler.post(() -> onGattStateChanged(TransportManager.GATT_DISCONNECTED));
                    break;
                default:
                    break;
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == GATT_SUCCESS) {
            ArrayList<BluetoothGattCharacteristic> chars = new ArrayList<>();
            for (UUID uuid : getServiceUuids()) {
                BluetoothGattService service = gatt.getService(uuid);
                if (service != null) {
                    chars.addAll(service.getCharacteristics());
                } else {
                    Log.e(TAG, "Cannot find service: " + uuid);
                }
            }
            final Runnable pending = mPendingRequest;
            mPendingRequest = null;
            mHandler.post(() -> initCharacteristics(chars, pending));
        } else {
            Log.w(TAG, "onServicesDiscovered received: " + status);
        }
    }

    // Implements BluetoothGattCallback
    @Override
    public void onCharacteristicChanged(
            BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        mHandler.post(() -> onCharacteristicChangedImpl(characteristic));
    }

    // Implements BluetoothGattCallback
    @Override
    public void onCharacteristicWrite(
            BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        mHandler.post(() -> onWritten(characteristic, status));
    }

    // Implements BluetoothGattCallback
    @Override
    public void onCharacteristicRead(
            BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        mHandler.post(() -> onRead(characteristic, status));
    }

    private void initCharacteristics(
            List<BluetoothGattCharacteristic> chars, Runnable pendingTask) {
        if (mGatt == null) {
            mHandler.post(this::shutdown);
            return;
        }

        boolean success = initCharacteristicsImpl(chars);
        mManager.onInitCharacteristics(success, pendingTask);
        mManager.onGattStateChanged(TransportManager.GATT_CONNECTED);
    }

    protected void onGattStateChanged(int state) {
        if (mGatt == null) {
            Log.e(TAG, "onGattStateChanged: gatt is null");
            return;
        }
        switch (state) {
            case TransportManager.GATT_CONNECTED:
                if (!mGatt.discoverServices()) {
                    Log.e(TAG, "failed to discover services");
                }
                mManager.onGattStateChanged(state);
                break;
            case TransportManager.GATT_DISCONNECTED:
                mManager.onGattStateChanged(state);
                break;
        }
    }
}
