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

package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.Set;

/**
 * Interface for talking to the HeadsetClientService
 *
 * Deals with service lifecycle and returns consistent error values
 */
public class HeadsetClientServiceInterface {
    private static final String TAG = "HeadsetClientServiceInterface";

    /* Action policy for other calls when accepting call */
    public static final int CALL_ACCEPT_NONE = 0;
    public static final int CALL_ACCEPT_HOLD = 1;
    public static final int CALL_ACCEPT_TERMINATE = 2;

    public HeadsetClientServiceInterface() {
    }

    private boolean isServiceAvailable(HeadsetClientService service) {
        if (service == null) {
            Log.w(TAG, "HeadsetClientService is not available");
            return false;
        }
        return true;
    }

    public HfpClientCall dial(BluetoothDevice device, String number) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return null;
        return service.dial(device, number);
    }

    public boolean enterPrivateMode(BluetoothDevice device, int index) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.enterPrivateMode(device, index);
    }

    public boolean sendDTMF(BluetoothDevice device, byte code) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.sendDTMF(device, code);
    }

    public boolean terminateCall(BluetoothDevice device, HfpClientCall call) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.terminateCall(device, call != null ? call.getUUID() : null);
    }

    public boolean holdCall(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.holdCall(device);
    }

    public boolean acceptCall(BluetoothDevice device, int flag) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.acceptCall(device, flag);
    }

    public boolean rejectCall(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.rejectCall(device);
    }

    public boolean connectAudio(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.connectAudio(device);
    }

    public boolean disconnectAudio(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return false;
        return service.disconnectAudio(device);
    }

    public Set<Integer> getCurrentAgFeatures(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return null;
        return service.getCurrentAgFeatures(device);
    }

    public Bundle getCurrentAgEvents(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return null;
        return service.getCurrentAgEvents(device);
    }

    public List<BluetoothDevice> getConnectedDevices() {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return null;
        return service.getConnectedDevices();
    }

    public List<HfpClientCall> getCurrentCalls(BluetoothDevice device) {
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (!isServiceAvailable(service)) return null;
        return service.getCurrentCalls(device);
    }

    public boolean hasHfpClientEcc(BluetoothDevice device) {
        Set<Integer> features = getCurrentAgFeatures(device);
        return features != null && features.contains(HeadsetClientHalConstants.PEER_FEAT_ECC);
    }
}
