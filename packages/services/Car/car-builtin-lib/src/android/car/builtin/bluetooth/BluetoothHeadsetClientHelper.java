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

package android.car.builtin.bluetooth;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to {@code android.bluetooth.BluetoothHeadsetClient} calls.
 * @hide
 * @deprecated Moving towards a framework solution that better integrates voice recognition.
 */
@Deprecated
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class BluetoothHeadsetClientHelper {

    private BluetoothHeadsetClientHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Gets connected devices that support BVRA (voice recognition).
     *
     * @param headsetClient Proxy object for controlling the Bluetooth HFP Client service.
     * @return a list of connected devices that support BVRA.
     */
    public static List<BluetoothDevice> getConnectedBvraDevices(
            @NonNull BluetoothHeadsetClient headsetClient) {
        List<BluetoothDevice> devices = headsetClient.getConnectedDevices();
        List<BluetoothDevice> bvraDevices = new ArrayList<BluetoothDevice>();
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice device = devices.get(i);
            Bundle bundle = headsetClient.getCurrentAgFeatures(device);
            if (bundle != null && bundle.getBoolean(
                    BluetoothHeadsetClient.EXTRA_AG_FEATURE_VOICE_RECOGNITION)) {
                bvraDevices.add(device);
            }
        }
        return bvraDevices;
    }

    /**
     * Starts BVRA.
     *
     * @param headsetClient Proxy object for controlling the Bluetooth HFP Client service.
     * @param device The connected device whose voice recognition will be started.
     * @return {@code true} if the command has been issued successfully; {@code false} otherwise.
     */
    public static boolean startVoiceRecognition(@NonNull BluetoothHeadsetClient headsetClient,
            BluetoothDevice device) {
        return headsetClient.startVoiceRecognition(device);
    }

    /**
     * Stops BVRA.
     *
     * @param headsetClient Proxy object for controlling the Bluetooth HFP Client service.
     * @param device The connected device whose voice recognition will be stopped.
     * @return {@code true} if the command has been issued successfully; {@code false} otherwise.
     */
    public static boolean stopVoiceRecognition(@NonNull BluetoothHeadsetClient headsetClient,
            BluetoothDevice device) {
        return headsetClient.stopVoiceRecognition(device);
    }
}
