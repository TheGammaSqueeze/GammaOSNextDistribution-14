/*
 * Copyright 2023 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.hardware.cts.R;
import android.view.InputDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.cts.kernelinfo.KernelInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SonyDualSenseEdgeBluetoothTest extends InputHidTestCase {

    // Simulates the behavior of PlayStation DualSense Edge gamepad
    public SonyDualSenseEdgeBluetoothTest() {
        super(R.raw.sony_dualsense_edge_bluetooth_register);
    }

    @Override
    protected int getAdditionalSources() {
        if (KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION")) {
            return InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_SENSOR;
        }
        return 0;
    }

    /**
     * Basic support is required on all kernels. After kernel 4.19, devices must have
     * CONFIG_HID_PLAYSTATION enabled, which supports advanced features like haptics.
     */
    @Test
    public void kernelModule() {
        if (KernelInfo.isKernelVersionGreaterThan("4.19")) {
            assertTrue(KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION"));
        }
        assertTrue(KernelInfo.hasConfig("CONFIG_HID_GENERIC"));
    }

    @Test
    public void testAllKeys() {
        if (KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION")) {
            testInputEvents(R.raw.sony_dualsense_bluetooth_keyeventtests);
        } else {
            testInputEvents(R.raw.sony_dualsense_bluetooth_keyeventtests_hid_generic);
        }
    }

    @Test
    public void testAllMotions() {
        if (KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION")) {
            testInputEvents(R.raw.sony_dualsense_bluetooth_motioneventtests);
        } else {
            testInputEvents(R.raw.sony_dualsense_bluetooth_motioneventtests_hid_generic);
        }
    }

    @Test
    public void testVibrator() throws Exception {
        // hid-generic does not support vibration for this device
        assumeTrue(KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION"));
        testInputVibratorEvents(R.raw.sony_dualsense_edge_bluetooth_vibratortests);
    }
}
