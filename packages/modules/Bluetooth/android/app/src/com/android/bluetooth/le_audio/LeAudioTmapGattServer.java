/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.le_audio;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A GATT server for Telephony and Media Audio Profile (TMAP)
 */
@VisibleForTesting
public class LeAudioTmapGattServer {
    private static final boolean DBG = true;
    private static final String TAG = "LeAudioTmapGattServer";

    /* Telephony and Media Audio Profile Role Characteristic UUID */
    @VisibleForTesting
    public static final UUID UUID_TMAP_ROLE =
            UUID.fromString("00002B51-0000-1000-8000-00805f9b34fb");

    /* TMAP Role: Call Gateway */
    public static final int TMAP_ROLE_FLAG_CG = 1;
    /* TMAP Role: Call Terminal */
    public static final int TMAP_ROLE_FLAG_CT = 1 << 1;
    /* TMAP Role: Unicast Media Sender */
    public static final int TMAP_ROLE_FLAG_UMS = 1 << 2;
    /* TMAP Role: Unicast Media Receiver */
    public static final int TMAP_ROLE_FLAG_UMR = 1 << 3;
    /* TMAP Role: Broadcast Media Sender */
    public static final int TMAP_ROLE_FLAG_BMS = 1 << 4;
    /* TMAP Role: Broadcast Media Receiver */
    public static final int TMAP_ROLE_FLAG_BMR = 1 << 5;

    private final BluetoothGattServerProxy mBluetoothGattServer;

    /*package*/ LeAudioTmapGattServer(BluetoothGattServerProxy gattServer) {
        mBluetoothGattServer = gattServer;
    }

    /**
     * Init TMAP server
     * @param roleMask bit mask of supported roles.
     */
    @VisibleForTesting
    public void start(int roleMask) {
        if (DBG) {
            Log.d(TAG, "start(roleMask:" + roleMask + ")");
        }

        if (!mBluetoothGattServer.open(mBluetoothGattServerCallback)) {
            throw new IllegalStateException("Could not open Gatt server");
        }

        BluetoothGattService service =
                new BluetoothGattService(BluetoothUuid.TMAP.getUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(
                        UUID_TMAP_ROLE,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ);

        characteristic.setValue(roleMask, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        service.addCharacteristic(characteristic);

        if (!mBluetoothGattServer.addService(service)) {
            throw new IllegalStateException("Failed to add service for TMAP");
        }
    }

    /**
     * Stop TMAP server
     */
    @VisibleForTesting
    public void stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "mBluetoothGattServer should not be null when stop() is called");
            return;
        }
        mBluetoothGattServer.close();
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private final BluetoothGattServerCallback mBluetoothGattServerCallback =
            new BluetoothGattServerCallback() {
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (DBG) {
                Log.d(TAG, "value " + Arrays.toString(value));
            }
            if (value != null) {
                Log.e(TAG, "value null");
                value = Arrays.copyOfRange(value, offset, value.length);
            }
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, value);
        }
    };

     /**
     * A proxy class that facilitates testing.
     *
     * This is necessary due to the "final" attribute of the BluetoothGattServer class.
     */
    public static class BluetoothGattServerProxy {
        private final Context mContext;
        private final BluetoothManager mBluetoothManager;

        private BluetoothGattServer mBluetoothGattServer;

         /**
          * Create a new GATT server proxy object
          * @param context context to use
          */
        public BluetoothGattServerProxy(Context context) {
            mContext = context;
            mBluetoothManager = context.getSystemService(BluetoothManager.class);
        }

         /**
          * Open with GATT server callback
          * @param callback callback to invoke
          * @return true on success
          */
        public boolean open(BluetoothGattServerCallback callback) {
            mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, callback);
            return mBluetoothGattServer != null;
        }

         /**
          * Close the GATT server, should be called as soon as the server is not needed
          */
        public void close() {
            if (mBluetoothGattServer == null) {
                Log.w(TAG, "BluetoothGattServerProxy.close() called without open()");
                return;
            }
            mBluetoothGattServer.close();
            mBluetoothGattServer = null;
        }

         /**
          * Add a GATT service
          * @param service added service
          * @return true on success
          */
        public boolean addService(BluetoothGattService service) {
            return mBluetoothGattServer.addService(service);
        }

         /**
          * Send GATT response to remote
          * @param device remote device
          * @param requestId request id
          * @param status status of response
          * @param offset offset of the value
          * @param value value content
          * @return true on success
          */
        public boolean sendResponse(
                BluetoothDevice device, int requestId, int status, int offset, byte[] value) {
            return mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
        }

         /**
          * Gatt a list of devices connected to this GATT server
          * @return list of connected devices at this moment
          */
        public List<BluetoothDevice> getConnectedDevices() {
            return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        }
    }
}
