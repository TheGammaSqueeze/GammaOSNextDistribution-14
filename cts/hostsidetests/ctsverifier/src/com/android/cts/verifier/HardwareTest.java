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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class HardwareTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void NfcTestActivity() throws Exception {
        requireFeatures("android.hardware.nfc");

        runTest(".nfc.NfcTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.7.1/H-1-1")
    public void UsbAccessoryTest() throws Exception {
        requireFeatures("android.hardware.usb.accessor");
        excludeFeatures("android.hardware.type.watch");

        runTest(".usb.accessory.UsbAccessoryTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.7.2/C-1-1")
    @ApiTest(
            apis = {
                "android.hardware.usb.UsbDeviceConnection#controlTransfer",
                "android.hardware.usb.UsbDeviceConnection#bulkTransfer"
            })
    public void UsbDeviceTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures("android.hardware.type.watch");

        runTest(".usb.device.UsbDeviceTestActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @CddTest(requirements = "7.7.2/C-3-1")
    public void MtpHostTest() throws Exception {
        requireFeatures("android.hardware.usb.host");
        excludeFeatures("android.hardware.type.automotive", "android.hardware.type.television");

        runTest(".usb.mtp.MtpHostTestActivity");
    }
}
