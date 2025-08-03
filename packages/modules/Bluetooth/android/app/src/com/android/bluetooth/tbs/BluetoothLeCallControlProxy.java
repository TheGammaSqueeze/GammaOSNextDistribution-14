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

import android.bluetooth.BluetoothLeCall;
import android.bluetooth.BluetoothLeCallControl;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/*
 * A proxy class that facilitates testing of the BluetoothInCallService class.
 *
 * This is necessary due to the "final" attribute of the BluetoothLeCallControl class. In order to test the
 * correct functioning of the BluetoothInCallService class, the final class must be put into a
 * container that can be mocked correctly.
 */
public class BluetoothLeCallControlProxy {

    private BluetoothLeCallControl mBluetoothLeCallControl;

    public static final int BEARER_TECHNOLOGY_3G = 0x01;
    public static final int BEARER_TECHNOLOGY_4G = 0x02;
    public static final int BEARER_TECHNOLOGY_LTE = 0x03;
    public static final int BEARER_TECHNOLOGY_WIFI = 0x04;
    public static final int BEARER_TECHNOLOGY_5G = 0x05;
    public static final int BEARER_TECHNOLOGY_GSM = 0x06;
    public static final int BEARER_TECHNOLOGY_CDMA = 0x07;
    public static final int BEARER_TECHNOLOGY_2G = 0x08;
    public static final int BEARER_TECHNOLOGY_WCDMA = 0x09;

    public BluetoothLeCallControlProxy(BluetoothLeCallControl tbs) {
        mBluetoothLeCallControl = tbs;
    }

    public void closeBluetoothLeCallControlProxy(Context context) {
        final BluetoothManager btManager =
                context.getSystemService(BluetoothManager.class);
        if (btManager != null) {
            btManager.getAdapter().closeProfileProxy(BluetoothProfile.LE_CALL_CONTROL,
                    mBluetoothLeCallControl);
        }
    }

    public boolean registerBearer(String uci, List<String> uriSchemes, int featureFlags,
            String provider, int technology, Executor executor, BluetoothLeCallControl.Callback callback) {
        return mBluetoothLeCallControl.registerBearer(uci, uriSchemes, featureFlags, provider, technology,
                executor, callback);
    }

    public void unregisterBearer() {
        mBluetoothLeCallControl.unregisterBearer();
    }

    public int getContentControlId() {
        return mBluetoothLeCallControl.getContentControlId();
    }

    public void requestResult(int requestId, int result) {
        mBluetoothLeCallControl.requestResult(requestId, result);
    }

    public void onCallAdded(BluetoothLeCall call) {
        mBluetoothLeCallControl.onCallAdded(call);
    }

    public void onCallRemoved(UUID callId, int reason) {
        mBluetoothLeCallControl.onCallRemoved(callId, reason);
    }

    public void onCallStateChanged(UUID callId, int state) {
        mBluetoothLeCallControl.onCallStateChanged(callId, state);
    }

    public void currentCallsList(List<BluetoothLeCall> calls) {
        mBluetoothLeCallControl.currentCallsList(calls);
    }

    public void networkStateChanged(String providerName, int technology) {
        mBluetoothLeCallControl.networkStateChanged(providerName, technology);
    }
}
