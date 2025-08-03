/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.tbs;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.List;
import java.util.UUID;

/**
 * A proxy class that facilitates testing of the TbsService class.
 *
 * This is necessary due to the "final" attribute of the BluetoothGattServer class. In order to test
 * the correct functioning of the TbsService class, the final class must be put into a container
 * that can be mocked correctly.
 */
public class BluetoothGattServerProxy {

    private final Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;

    public BluetoothGattServerProxy(Context context) {
        mContext = context;
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public boolean open(BluetoothGattServerCallback callback) {
        mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, callback);
        return (mBluetoothGattServer != null);
    }

    public void close() {
        if (mBluetoothGattServer == null) {
            return;
        }
        mBluetoothGattServer.close();
        mBluetoothGattServer = null;
    }

    public boolean addService(BluetoothGattService service) {
        return mBluetoothGattServer.addService(service);
    }

    /**
     * A proxy that Returns a {@link BluetoothGattService} from the list of services offered
     * by this device.
     *
     * <p>If multiple instances of the same service (as identified by UUID)
     * exist, the first instance of the service is returned.
     *
     * @param uuid UUID of the requested service
     * @return BluetoothGattService if supported, or null if the requested service is not offered by
     * this device.
     */
    public BluetoothGattService getService(UUID uuid) {
        return mBluetoothGattServer.getService(uuid);
    }

    /**
    * See {@link android.bluetooth.BluetoothGattServer#sendResponse(
    * android.bluetooth.BluetoothDevice, int, int, int, byte[])}
    */
    public boolean sendResponse(BluetoothDevice device, int requestId, int status, int offset,
            byte[] value) {
        return mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
    }

    /**
     * See {@link android.bluetooth.BluetoothGattServer#notifyCharacteristicChanged(
     * android.bluetooth.BluetoothDevice, BluetoothGattCharacteristic, boolean, byte[])}.
     */
    public int notifyCharacteristicChanged(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, boolean confirm, byte[] value) {
        return mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm,
                                                                value);
    }

    /**
     * See {@link android.bluetooth.BluetoothGattServer#notifyCharacteristicChanged(
     * android.bluetooth.BluetoothDevice, BluetoothGattCharacteristic, boolean)}.
     */
    public boolean notifyCharacteristicChanged(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, boolean confirm) {
        return mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
    }

    /**
     * Get connected devices
     * @return list of connected devices
     */
    public List<BluetoothDevice> getConnectedDevices() {
        return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
    }
}
