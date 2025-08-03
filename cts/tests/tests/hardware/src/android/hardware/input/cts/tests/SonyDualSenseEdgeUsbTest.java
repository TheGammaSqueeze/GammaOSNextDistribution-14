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

import static org.junit.Assume.assumeTrue;

import android.hardware.cts.R;
import android.view.InputDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.cts.kernelinfo.KernelInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SonyDualSenseEdgeUsbTest extends InputHidTestCase {

    // Simulates the behavior of PlayStation DualSense Edge gamepad
    public SonyDualSenseEdgeUsbTest() {
        super(R.raw.sony_dualsense_edge_usb_register);
    }

    @Override
    protected int getAdditionalSources() {
        if (KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION")) {
            return InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_SENSOR;
        }
        return 0;
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.sony_dualsense_usb_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        testInputEvents(R.raw.sony_dualsense_usb_motioneventtests);
    }

    @Test
    public void testVibrator() throws Exception {
        assumeTrue(KernelInfo.hasConfig("CONFIG_HID_PLAYSTATION"));
        testInputVibratorEvents(R.raw.sony_dualsense_edge_usb_vibratortests);
        // hid-generic does not support vibration for this device
    }
}
