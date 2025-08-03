/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assume.assumeTrue;

import android.hardware.cts.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.cts.kernelinfo.KernelInfo;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SonyDualshock4BluetoothTest extends InputHidTestCase {

    // Simulates the behavior of PlayStation DualShock4 gamepad (model CUH-ZCT1U)
    public SonyDualshock4BluetoothTest() {
        super(R.raw.sony_dualshock4_bluetooth_register);
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.sony_dualshock4_bluetooth_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        testInputEvents(R.raw.sony_dualshock4_bluetooth_motioneventtests);
    }

    @Test
    public void testVibrator() throws Exception {
        assumeTrue(KernelInfo.isKernelVersionGreaterThan("4.19"));
        testInputVibratorEvents(R.raw.sony_dualshock4_bluetooth_vibratortests);
    }

    @Test
    public void testBattery() {
        testInputBatteryEvents(R.raw.sony_dualshock4_bluetooth_batteryeventtests);
    }

    @Ignore("b/329585708: Disabled on Android 14 due to changes in touchpad processing")
    @Test
    public void testAllTouch() throws Throwable {
        try (PointerCaptureSession session = new PointerCaptureSession()) {
            testInputEvents(R.raw.sony_dualshock4_toucheventtests);
        }
    }

    @Test
    public void testLights() throws Exception {
        testInputLightsManager(R.raw.sony_dualshock4_bluetooth_lighttests);
    }
}
