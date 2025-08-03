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

package com.google.android.tv.btservices.pairing.profiles;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;

import java.util.List;

public class PairingProfileWrapperHidHost implements PairingProfileWrapper {

    private BluetoothHidHost mProxy;

    public PairingProfileWrapperHidHost(BluetoothProfile proxy) {
        mProxy = (BluetoothHidHost) proxy;
    }

    @Override
    public BluetoothProfile getProxy() {
        return mProxy;
    }

    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        return mProxy.getConnectedDevices();
    }

    @Override
    public int getConnectionState(BluetoothDevice device) {
        return mProxy.getConnectionState(device);
    }

    @Override
    public boolean connect(BluetoothDevice device) {
        return mProxy.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    @Override
    public boolean disconnect(BluetoothDevice device) {
        return mProxy.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }
}
