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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * An object that manages Bluetooth LE connection.
 *
 * <p>A {@code BleConnection} manages Bluetooth LE connection with a {@code
 * BluetoothDevice}. The lifecycle of an instance begins with a connect event
 * and ends when disconnect happens. A new instance should be created on device
 * reconnect.
 *
 * <p>For GATT requests, {@code BleConnection} maintains a request queue that is
 * built on Android's {@code BluetoothGatt} API. Methods like {@code
 * writeCharacteristic} are provided and can be called anytime after {@code
 * Callback#onGattReady} is called. All methods also takes a callback function
 * that is called when the response from GATT server is received.
 *
 * <p>Example usage:
 *
 * Create a new {@code BleConnection}:
 * <pre>
 * BluetoothDevice device;
 *
 * private class Callback implements BleConnection.Callback {
 *     &#64;Override
 *     public void onGattReady(BluetoothGatt gatt) {
 *         // Initialize with GATT requests here.
 *     }
 *
 *     &#64;Override
 *     public void onNotification(BluetoothGattCharacteristic characteristic, byte[] data) {
 *         // Handle GATT notification.
 *     }
 *
 *     &#64;Override
 *     public void onDisconnect(BluetoothGatt gatt, int status) {
 *         // Handle disconnect.
 *     }
 * }
 *
 * BleConnection bleConnection = new BleConnection(new Callback());
 * bleConnection.connect(context, device);
 * </pre>
 *
 * Write to GATT characteristic (must be called only after {@code
 * Callback#onGattReady} is called):
 * <pre>{@code
 * BluetoothGattCharacteristic characteristic;
 *
 * bleConnection.writeCharacteristic(
 *         characteristic,
 *         data, // byte array
 *         (BluetoothGattCharacteristic characteristic, int status) -> {
 *             if (status != BluetoothGatt.GATT_SUCCESS) {
 *                 Log.w(TAG, "GATT_FAILURE: " + status);
 *             }
 *             // write process completed
 *         });
 * }</pre>
 *
 */
public class BleConnection {
    private static final String TAG = "Atv.BleConnection";
    private static final boolean DEBUG = true;

    private enum ConnectionState {
        UNINITIALIZED,
        GATT_CONNECTING,
        SERVICE_DISCOVERING,
        READY,
        PENDING_RESPONSE,
        DISCONNECTED,
    }

    @FunctionalInterface
    public interface CharacteristicWriteResultCallback {
        void run(BluetoothGattCharacteristic characteristic, int status);
    }

    @FunctionalInterface
    public interface CharacteristicReadResultCallback {
        void run(BluetoothGattCharacteristic characteristic, int status);
    }

    @FunctionalInterface
    public interface DescriptorWriteResultCallback {
        void run(BluetoothGattDescriptor descriptor, int status);
    }

    private interface GattRequest {
        boolean processRequest(BluetoothGatt gatt);
    }

    private CharacteristicWriteResultCallback lastCharacteristicWriteCallback;
    private CharacteristicReadResultCallback lastCharacteristicReadCallback;
    private DescriptorWriteResultCallback lastDescriptorWriteCallback;
    private Consumer<Boolean> lastRequestMtuCallback;

    private class CharacteristicWriteRequest implements GattRequest {
        final BluetoothGattCharacteristic characteristic;
        final byte[] data;
        final CharacteristicWriteResultCallback callback;

        CharacteristicWriteRequest(
                BluetoothGattCharacteristic characteristic,
                byte[] data,
                CharacteristicWriteResultCallback callback) {
            this.characteristic = characteristic;
            this.data = data;
            this.callback = callback;
        }

        @Override
        public boolean processRequest(BluetoothGatt gatt) {
            lastCharacteristicWriteCallback = this.callback;
            characteristic.setValue(data);
            return gatt.writeCharacteristic(characteristic);
        }
    }

    private class CharacteristicReadRequest implements GattRequest {
        final BluetoothGattCharacteristic characteristic;
        final CharacteristicReadResultCallback callback;

        CharacteristicReadRequest(
                BluetoothGattCharacteristic characteristic,
                CharacteristicReadResultCallback callback) {
            this.characteristic = characteristic;
            this.callback = callback;
        }

        @Override
        public boolean processRequest(BluetoothGatt gatt) {
            lastCharacteristicReadCallback = this.callback;
            return gatt.readCharacteristic(characteristic);
        }
    }

    private class DescriptorWriteRequest implements GattRequest {
        final BluetoothGattDescriptor descriptor;
        final byte[] data;
        final DescriptorWriteResultCallback callback;

        DescriptorWriteRequest(
                BluetoothGattDescriptor descriptor,
                byte[] data,
                DescriptorWriteResultCallback callback) {
            this.descriptor = descriptor;
            this.data = data;
            this.callback = callback;
        }

        @Override
        public boolean processRequest(BluetoothGatt gatt) {
            lastDescriptorWriteCallback = this.callback;
            descriptor.setValue(data);
            return gatt.writeDescriptor(descriptor);
        }
    }

    private final AtomicReference<ConnectionState> state;
    private final Callback bleConnectionCallback;
    // Contains the following types of request: CharacteristicWriteRequest,
    // CharacteristicReadRequest, DescriptorWriteRequest.
    private final LinkedBlockingQueue<GattRequest> requestQueue = new LinkedBlockingQueue<>();
    private BluetoothGatt gatt;

    public BleConnection(Callback bleConnectionCallback) {
        this.bleConnectionCallback = bleConnectionCallback;
        state = new AtomicReference<>(ConnectionState.UNINITIALIZED);
    }

    public boolean connect(Context context, BluetoothDevice device) {
        if (state.compareAndSet(
                    ConnectionState.UNINITIALIZED,
                    ConnectionState.GATT_CONNECTING)) {
            synchronized (state) {
                gatt = device.connectGatt(context, false, new GattCallback());
            }

            if (gatt != null) {
              return true;
            }
        }
        state.set(ConnectionState.DISCONNECTED);
        return false;
    }

    /**
     * Sends the next command from {@link #requestQueue} if exists.
     *
     * <p>The main purpose of this method is to handle simultaneous write requests without an
     * explicit synchronization.
     */
    private void maybeSendNextCommand(BluetoothGatt gatt) {
        GattRequest upcomingRequest;
        while ((upcomingRequest = requestQueue.poll()) == null) {
            if (!state.compareAndSet(ConnectionState.PENDING_RESPONSE, ConnectionState.READY)
                    || requestQueue.isEmpty()
                    || !state.compareAndSet(ConnectionState.READY,
                        ConnectionState.PENDING_RESPONSE)) {
                return;
            }
        }

        if (!upcomingRequest.processRequest(gatt)) {
            closeGatt();
        }
    }

    public void writeCharacteristic(
            BluetoothGattCharacteristic characteristic,
            byte[] data,
            CharacteristicWriteResultCallback callback) {
        if (characteristic == null) {
            callback.run(characteristic, BluetoothGatt.GATT_FAILURE);
            return;
        }

        CharacteristicWriteRequest request =
                new CharacteristicWriteRequest(characteristic, data, callback);
        requestQueue.add(request);

        if (state.compareAndSet(ConnectionState.READY, ConnectionState.PENDING_RESPONSE)) {
            maybeSendNextCommand(gatt);
        } else {
            Log.i(TAG, "Queueing write request to " + gatt.getDevice());
        }
    }

    public void readCharacteristic(
            BluetoothGattCharacteristic characteristic,
            CharacteristicReadResultCallback callback) {
        if (characteristic == null) {
            callback.run(characteristic, BluetoothGatt.GATT_FAILURE);
            return;
        }

        CharacteristicReadRequest request = new CharacteristicReadRequest(characteristic, callback);
        requestQueue.add(request);

        if (state.compareAndSet(ConnectionState.READY, ConnectionState.PENDING_RESPONSE)) {
            maybeSendNextCommand(gatt);
        } else {
            Log.i(TAG, "Queueing read request to " + gatt.getDevice());
        }
    }

    public void writeDescriptor(
            BluetoothGattDescriptor descriptor,
            byte[] data,
            DescriptorWriteResultCallback callback) {
        if (descriptor == null) {
            callback.run(descriptor, BluetoothGatt.GATT_FAILURE);
            return;
        }

        DescriptorWriteRequest request = new DescriptorWriteRequest(descriptor, data, callback);
        requestQueue.add(request);

        if (state.compareAndSet(ConnectionState.READY, ConnectionState.PENDING_RESPONSE)) {
            maybeSendNextCommand(gatt);
        } else {
            Log.i(TAG, "Queueing write request to " + gatt.getDevice());
        }
    }

    public boolean setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enable) {
        return gatt.setCharacteristicNotification(characteristic, enable);
    }

    public boolean requestMtu(int mtu, Consumer<Boolean> callback) {
        lastRequestMtuCallback = callback;

        return gatt.requestMtu(mtu);
    }

    public void refreshGattCache() {
        gatt.discoverServices();
    }

    private void closeGatt() {
        disconnect(() -> bleConnectionCallback.onDisconnect(gatt, BluetoothGatt.GATT_FAILURE));
    }

    private boolean disconnect(Runnable callback) {
        ConnectionState prevState = state.getAndSet(ConnectionState.DISCONNECTED);

        if (prevState != ConnectionState.UNINITIALIZED &&
                prevState != ConnectionState.DISCONNECTED) {
            synchronized (state) {
                // Acts as a barrier

                callback.run();

                gatt.close();
            }
            return true;
        }
        return false;
    }

    public interface Callback {
        /** Called when GATT connection is set up and ready to handle requests. */
        void onGattReady(BluetoothGatt gatt);

        /**
         * Notifies about GATT notification from the server.
         *
         * @param characteristic Characteristic to which notification is sent.
         * @param data Payload sent by GATT server.
         */
        void onNotification(BluetoothGattCharacteristic characteristic, byte[] data);

        /**
         * Notifies disconnection and corresponding status code.
         *
         * @param status Status code for disconnection reason.
         */
        void onDisconnect(BluetoothGatt gatt, int status);
    }

    private class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) {
                Log.d(TAG,
                        "Device " + gatt.getDevice() + " newState: " + newState +
                        ", status: " + status);
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (state.compareAndSet(
                        ConnectionState.GATT_CONNECTING,
                        ConnectionState.SERVICE_DISCOVERING)) {
                    if (gatt.discoverServices()) {
                        return;
                    }
                    closeGatt();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect(() -> bleConnectionCallback.onDisconnect(gatt, status));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // state == SERVICE_DISCOVERING
            if (status == BluetoothGatt.GATT_SUCCESS &&
                    state.compareAndSet(ConnectionState.SERVICE_DISCOVERING,
                        ConnectionState.READY)) {
                bleConnectionCallback.onGattReady(gatt);
                return;
            }
            Log.w(TAG, "Failed to discover services for " + gatt.getDevice() + ": " + status);
            closeGatt();
        }

        @Override
        public void onDescriptorWrite(
                BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (lastDescriptorWriteCallback != null) {
                lastDescriptorWriteCallback.run(descriptor, status);
                lastDescriptorWriteCallback = null;
            }

            maybeSendNextCommand(gatt);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG,
                        "Error response to write descriptor " + descriptor.getUuid() +
                        " to " + gatt.getDevice() + ": " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (lastCharacteristicWriteCallback != null) {
                lastCharacteristicWriteCallback.run(characteristic, status);
                lastCharacteristicWriteCallback = null;
            }

            maybeSendNextCommand(gatt);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG,
                        "Error response to write characteristic " + characteristic.getUuid() +
                        " to " + gatt.getDevice() + ": " + status);
            }
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (lastCharacteristicReadCallback != null) {
                lastCharacteristicReadCallback.run(characteristic, status);
                lastCharacteristicReadCallback = null;
            }

            maybeSendNextCommand(gatt);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG,
                        "Error response to read characteristic " + characteristic.getUuid() +
                        " to " + gatt.getDevice() + ": " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            bleConnectionCallback.onNotification(characteristic, data);
        }

        @Override
        public void onMtuChanged(
                BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU size updated to " + mtu + " for device: " + gatt.getDevice());

                if (lastRequestMtuCallback != null) {
                    lastRequestMtuCallback.accept(true);
                    lastRequestMtuCallback = null;
                }
            } else {
                if (lastRequestMtuCallback != null) {
                    lastRequestMtuCallback.accept(false);
                    lastRequestMtuCallback = null;
                }
            }
        }
    }
}
