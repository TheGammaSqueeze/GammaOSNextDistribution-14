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

package android.bluetooth.cts;

import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.test.AndroidTestCase;

public class BluetoothHidDeviceAppQosSettingsTest extends AndroidTestCase {
    private final int TEST_SERVICE_TYPE = BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT;
    private final int TEST_TOKEN_RATE = 800;
    private final int TEST_TOKEN_BUCKET_SIZE = 9;
    private final int TEST_PEAK_BANDWIDTH = 10;
    private final int TEST_LATENCY = 11250;
    private final int TEST_DELAY_VARIATION = BluetoothHidDeviceAppQosSettings.MAX;
    private BluetoothHidDeviceAppQosSettings mBluetoothHidDeviceAppQosSettings;

    @Override
    public void setUp() throws Exception {
        mBluetoothHidDeviceAppQosSettings = new BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, TEST_TOKEN_RATE,
                TEST_TOKEN_BUCKET_SIZE, TEST_PEAK_BANDWIDTH, TEST_LATENCY, TEST_DELAY_VARIATION);
    }

    @Override
    public void tearDown() throws Exception {
        mBluetoothHidDeviceAppQosSettings = null;
    }


    public void test_allMethods() {
        assertEquals(mBluetoothHidDeviceAppQosSettings.getServiceType(),
                TEST_SERVICE_TYPE);
        assertEquals(mBluetoothHidDeviceAppQosSettings.getLatency(), TEST_LATENCY);
        assertEquals(mBluetoothHidDeviceAppQosSettings.getTokenRate(), TEST_TOKEN_RATE);
        assertEquals(mBluetoothHidDeviceAppQosSettings.getPeakBandwidth(), TEST_PEAK_BANDWIDTH);
        assertEquals(mBluetoothHidDeviceAppQosSettings.getDelayVariation(), TEST_DELAY_VARIATION);
        assertEquals(mBluetoothHidDeviceAppQosSettings.getTokenBucketSize(),
                TEST_TOKEN_BUCKET_SIZE);
    }
}
