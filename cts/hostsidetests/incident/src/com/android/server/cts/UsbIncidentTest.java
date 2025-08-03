/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts;

import static org.junit.Assume.assumeTrue;

import android.service.usb.UsbAccessoryProto;
import android.service.usb.UsbDebuggingManagerProto;
import android.service.usb.UsbDeviceFilterProto;
import android.service.usb.UsbDeviceManagerProto;
import android.service.usb.UsbDeviceProto;
import android.service.usb.UsbHandlerProto;
import android.service.usb.UsbHostManagerProto;
import android.service.usb.UsbProfileGroupSettingsManagerProto;
import android.service.usb.UsbServiceDumpProto;
import android.service.usb.UsbSettingsDevicePreferenceProto;
import android.service.usb.UsbSettingsManagerProto;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Tests for the UsbService proto dump.
 */
public class UsbIncidentTest extends ProtoDumpTestCase {

    /** Value of PackageManager.FEATURE_USB_HOST */
    private static final String FEATURE_USB_HOST = "android.hardware.usb.host";

    /** Value of PackageManager.FEATURE_USB_ACCESSORY */
    private static final String FEATURE_USB_ACCESSORY = "android.hardware.usb.accessory";

    public static boolean hasUsbFunctionality(ITestDevice device) throws DeviceNotAvailableException {
        boolean hasUsbHost = device.hasFeature(FEATURE_USB_HOST);
        boolean hasUsbAccessory = device.hasFeature(FEATURE_USB_ACCESSORY);
        return hasUsbHost || hasUsbAccessory;
    }

    public void testUsbServiceDump() throws Exception {
        // Devices that don't support USB functionality won't dump a USB service proto.
        if (!hasUsbFunctionality(getDevice())) {
            CLog.i("Device doesn't support USB functionality.");
            return;
        }

        final UsbServiceDumpProto dump = getDump(UsbServiceDumpProto.parser(),
                "dumpsys usb --proto");

        verifyUsbServiceDumpProto(dump, PRIVACY_NONE);
    }

    static void verifyUsbServiceDumpProto(UsbServiceDumpProto dump, final int filterLevel) throws Exception {
        verifyUsbDeviceManagerProto(dump.getDeviceManager(), filterLevel);
        verifyUsbHostManagerProto(dump.getHostManager(), filterLevel);
        verifyUsbSettingsManagerProto(dump.getSettingsManager(), filterLevel);
    }

    private static void verifyUsbDeviceManagerProto(UsbDeviceManagerProto udmp, final int filterLevel) throws Exception {
        verifyUsbHandlerProto(udmp.getHandler(), filterLevel);
        verifyUsbDebuggingManagerProto(udmp.getDebuggingManager(), filterLevel);
    }

    private static void verifyUsbHandlerProto(UsbHandlerProto uhp, final int filterLevel) throws Exception {
        for (UsbHandlerProto.Function f : uhp.getCurrentFunctionsList()) {
            assertTrue(UsbHandlerProto.Function.getDescriptor().getValues().contains(f.getValueDescriptor()));
        }
        for (UsbHandlerProto.Function f : uhp.getScreenUnlockedFunctionsList()) {
            assertTrue(UsbHandlerProto.Function.getDescriptor().getValues().contains(f.getValueDescriptor()));
        }
        verifyUsbAccessoryProto(uhp.getCurrentAccessory(), filterLevel);
    }

    private static void verifyUsbAccessoryProto(UsbAccessoryProto uap, final int filterLevel) throws Exception {
        if (filterLevel < PRIVACY_LOCAL) {
            assertTrue(uap.getSerial().isEmpty());
            if (filterLevel == PRIVACY_AUTO) {
                assertTrue(uap.getUri().isEmpty());
            }
        }
    }

    private static void verifyUsbDebuggingManagerProto(UsbDebuggingManagerProto udmp, final int filterLevel) throws Exception {
        if (filterLevel < PRIVACY_LOCAL) {
            assertTrue(udmp.getSystemKeys().isEmpty());
            assertTrue(udmp.getUserKeys().isEmpty());
            if (filterLevel < PRIVACY_EXPLICIT) {
                assertTrue(udmp.getLastKeyReceived().isEmpty());
            }
        }
    }

    private static void verifyUsbHostManagerProto(UsbHostManagerProto uhmp, final int filterLevel) throws Exception {
        for (UsbDeviceProto udp : uhmp.getDevicesList()) {
            verifyUsbDeviceProto(udp, filterLevel);
        }
    }

    private static void verifyUsbDeviceProto(UsbDeviceProto udp, final int filterLevel) throws Exception {
        if (filterLevel < PRIVACY_LOCAL) {
            assertTrue(udp.getSerialNumber().isEmpty());
        }
    }

    private static void verifyUsbSettingsManagerProto(UsbSettingsManagerProto usmp, final int filterLevel) throws Exception {
        for (UsbProfileGroupSettingsManagerProto upgsmp : usmp.getProfileGroupSettingsList()) {
            verifyUsbProfileGroupSettingsManagerProto(upgsmp, filterLevel);
        }
    }

    private static void verifyUsbProfileGroupSettingsManagerProto(UsbProfileGroupSettingsManagerProto pgsp, final int filterLevel) throws Exception {
        for (UsbSettingsDevicePreferenceProto usdp : pgsp.getDevicePreferencesList()) {
            verifyUsbDeviceFilterProto(usdp.getFilter(), filterLevel);
        }
    }

    private static void verifyUsbDeviceFilterProto(UsbDeviceFilterProto udfp, final int filterLevel) throws Exception {
        if (filterLevel == PRIVACY_AUTO) {
            assertTrue(udfp.getSerialNumber().isEmpty());
        }
    }
}
