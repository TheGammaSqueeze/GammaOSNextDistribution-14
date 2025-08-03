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

package android.bluetooth.cts;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothHidDeviceCallbackTest {
    private BluetoothHidDevice.Callback mHidDeviceCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            super.onGetReport(device, type, id, bufferSize);
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            super.onSetReport(device, type, id, data);
        }

        @Override
        public void onSetProtocol(BluetoothDevice device, byte protocol) {
            super.onSetProtocol(device, protocol);
        }

        @Override
        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
            super.onInterruptData(device, reportId, data);
        }

        @Override
        public void onVirtualCableUnplug(BluetoothDevice device) {
            super.onVirtualCableUnplug(device);
        }
    };

    @Test
    public void hidDeviceCallback() {
        // TODO: Provide a way to simulate BluetoothHidHost for better testing.
        // We may need to have a new BluetoothAdapter.getProfileProxy method with a new test profile
        // like HID_DEVICE_TEST which also takes a mock hid host instance.
        mHidDeviceCallback.onGetReport(null, (byte) 0, (byte) 0, 0);
        mHidDeviceCallback.onSetReport(null, (byte) 0, (byte) 0, null);
        mHidDeviceCallback.onSetProtocol(null, (byte) 0);
        mHidDeviceCallback.onInterruptData(null, (byte) 0, null);
        mHidDeviceCallback.onVirtualCableUnplug(null);
        // FYI, onAppStatusChanged and onConnectionStateChanged are covered by CtsVerifier.
    }
}
