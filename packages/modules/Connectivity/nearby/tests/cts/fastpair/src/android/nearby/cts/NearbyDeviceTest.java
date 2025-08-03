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

package android.nearby.cts;

import static android.nearby.NearbyDevice.Medium.BLE;

import android.annotation.TargetApi;
import android.nearby.FastPairDevice;
import android.nearby.NearbyDevice;
import android.os.Build;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class NearbyDeviceTest {
    private static final String NAME = "NearbyDevice";
    private static final String MODEL_ID = "112233";
    private static final int TX_POWER = -10;
    private static final int RSSI = -60;
    private static final String BLUETOOTH_ADDRESS = "00:11:22:33:FF:EE";
    private static final byte[] SCAN_DATA = new byte[] {1, 2, 3, 4};

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_isValidMedium() {
        assertThat(NearbyDevice.isValidMedium(1)).isTrue();
        assertThat(NearbyDevice.isValidMedium(2)).isTrue();
        assertThat(NearbyDevice.isValidMedium(0)).isFalse();
        assertThat(NearbyDevice.isValidMedium(3)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getMedium_fromChild() {
        FastPairDevice fastPairDevice = new FastPairDevice.Builder()
                .addMedium(BLE)
                .setRssi(RSSI)
                .build();

        assertThat(fastPairDevice.getMediums()).contains(1);
        assertThat(fastPairDevice.getRssi()).isEqualTo(RSSI);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEqual() {
        FastPairDevice fastPairDevice1 = new FastPairDevice.Builder()
                .setModelId(MODEL_ID)
                .setTxPower(TX_POWER)
                .setBluetoothAddress(BLUETOOTH_ADDRESS)
                .setData(SCAN_DATA)
                .setRssi(RSSI)
                .addMedium(BLE)
                .setName(NAME)
                .build();
        FastPairDevice fastPairDevice2 = new FastPairDevice.Builder()
                .setModelId(MODEL_ID)
                .setTxPower(TX_POWER)
                .setBluetoothAddress(BLUETOOTH_ADDRESS)
                .setData(SCAN_DATA)
                .setRssi(RSSI)
                .addMedium(BLE)
                .setName(NAME)
                .build();

        assertThat(fastPairDevice1.equals(fastPairDevice1)).isTrue();
        assertThat(fastPairDevice1.equals(fastPairDevice2)).isTrue();
        assertThat(fastPairDevice1.equals(null)).isFalse();
        assertThat(fastPairDevice1.hashCode()).isEqualTo(fastPairDevice2.hashCode());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testToString() {
        FastPairDevice fastPairDevice1 = new FastPairDevice.Builder()
                .addMedium(BLE)
                .setRssi(RSSI)
                .setModelId(MODEL_ID)
                .setTxPower(TX_POWER)
                .setBluetoothAddress(BLUETOOTH_ADDRESS)
                .build();

        assertThat(fastPairDevice1.toString())
                .isEqualTo("FastPairDevice [medium={BLE} rssi=-60 "
                        + "txPower=-10 modelId=112233 bluetoothAddress=00:11:22:33:FF:EE]");
    }
}
