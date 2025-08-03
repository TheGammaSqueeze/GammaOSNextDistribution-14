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

package com.android.tests.usbgadget;

import android.platform.test.annotations.RequiresDevice;
import com.android.tests.usbgadget.libusb.ConfigDescriptor;
import com.android.tests.usbgadget.libusb.DeviceDescriptor;
import com.android.tests.usbgadget.libusb.IUsbNative;
import com.android.tests.usbgadget.libusb.Interface;
import com.android.tests.usbgadget.libusb.InterfaceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;
import com.google.common.base.Strings;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A host-side test for USB Gadget HAL */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class VtsHalUsbGadgetV2_0HostTest extends BaseHostJUnit4Test {
    public static final String TAG = VtsHalUsbGadgetV2_0HostTest.class.getSimpleName();

    private static final String HAL_SERVICE = "android.hardware.usb.gadget.IUsbGadget/default";
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final long CONN_TIMEOUT = 5000;
    private static final int UNKNOWN_SPEED = -1;

    private static boolean mHasService;
    private static IUsbNative mUsb;
    private static Pointer mContext;

    private ITestDevice mDevice;
    private boolean mReconnected = false;

    @Before
    public void setUp() {
        mDevice = getDevice();
    }

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        String serviceFound =
                testInfo.getDevice()
                        .executeShellCommand(String.format("dumpsys -l | grep \"%s\"", HAL_SERVICE))
                        .trim();
        mHasService = !Strings.isNullOrEmpty(serviceFound) && serviceFound.contains(HAL_SERVICE);

        if (mHasService) {
            mUsb = (IUsbNative) Native.loadLibrary("usb-1.0", IUsbNative.class);
            PointerByReference context = new PointerByReference();
            mUsb.libusb_init(context);
            mContext = context.getValue();
        }
    }

    private static boolean checkProtocol(int usbClass, int usbSubClass, int usbProtocol) {
        PointerByReference list = new PointerByReference();
        int count = mUsb.libusb_get_device_list(mContext, list);
        Pointer[] devices = list.getValue().getPointerArray(0, count);
        for (Pointer device : devices) {
            DeviceDescriptor[] devDescriptors = new DeviceDescriptor[1];
            mUsb.libusb_get_device_descriptor(device, devDescriptors);
            for (int j = 0; j < devDescriptors[0].bNumConfigurations; j++) {
                PointerByReference configRef = new PointerByReference();
                int success = mUsb.libusb_get_config_descriptor(device, j, configRef);
                ConfigDescriptor config = new ConfigDescriptor(configRef.getValue());
                List<Interface> interfaces =
                        Arrays.asList(config.interfaces.toArray(config.bNumInterfaces));
                for (Interface interface_ : interfaces) {
                    List<InterfaceDescriptor> descriptors =
                            Arrays.asList(interface_.altsetting.toArray(interface_.num_altsetting));
                    for (InterfaceDescriptor d : descriptors) {
                        if (Byte.toUnsignedInt(d.bInterfaceClass) == usbClass
                                && Byte.toUnsignedInt(d.bInterfaceSubClass) == usbSubClass
                                && Byte.toUnsignedInt(d.bInterfaceProtocol) == usbProtocol) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Check for ADB */
    @RequiresDevice
    @Test
    public void testAndroidUSB() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);
        Assert.assertTrue("ADB not present", checkProtocol(255, 66, 1));
    }

    /**
     * Check for MTP.
     *
     * <p>Enables mtp and checks the host to see if mtp interface is present. MTP:
     * https://en.wikipedia.org/wiki/Media_Transfer_Protocol.
     */
    @RequiresDevice
    @Test
    public void testMtp() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);
        getDevice().executeShellCommand("svc usb setFunctions mtp true");
        RunUtil.getDefault().sleep(CONN_TIMEOUT);
        Assert.assertTrue("MTP not present", checkProtocol(6, 1, 1));
    }

    /**
     * Check for PTP.
     *
     * <p>Enables ptp and checks the host to see if ptp interface is present. PTP:
     * https://en.wikipedia.org/wiki/Picture_Transfer_Protocol.
     */
    @RequiresDevice
    @Test
    public void testPtp() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);
        getDevice().executeShellCommand("svc usb setFunctions ptp true");
        RunUtil.getDefault().sleep(CONN_TIMEOUT);
        Assert.assertTrue("PTP not present", checkProtocol(6, 1, 1));
    }

    /**
     * Check for MIDI.
     *
     * <p>Enables midi and checks the host to see if midi interface is present. MIDI:
     * https://en.wikipedia.org/wiki/MIDI.
     */
    @RequiresDevice
    @Test
    public void testMIDI() throws Exception {
        Assume.assumeFalse("Skip test: MIDI support is not required for automotive",
                getDevice().hasFeature(FEATURE_AUTOMOTIVE));
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);
        getDevice().executeShellCommand("svc usb setFunctions midi true");
        RunUtil.getDefault().sleep(CONN_TIMEOUT);
        Assert.assertTrue("MIDI not present", checkProtocol(1, 3, 0));
    }

    /**
     * Check for NCM.
     *
     * <p>Enable ncm and check the host to see if ncm interface is present.
     * For NCM interface definition, you can find more information on
     * https://www.usb.org/.
     */
    @RequiresDevice
    @Test
    public void testAndroidNcm() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);
        Assert.assertNotNull("Target device does not exist", mDevice);

        String deviceSerialNumber = mDevice.getSerialNumber();

        CLog.i("testAndroidNcm on device [%s]", deviceSerialNumber);

        mDevice.executeShellCommand("svc usb setFunctions ncm");
        RunUtil.getDefault().sleep(CONN_TIMEOUT);
        Assert.assertTrue("NCM not present", checkProtocol(2, 13, 0));
    }

    /**
     * Check for USB connection speed.
     *
     * <p>Gets the command result from USB Gadget Hal v1.2. If success,
     * it will get the USB speed except unknown.
     */
    @RequiresDevice
    @Test
    public void testGetUsbSpeed() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);

        Assert.assertNotNull("Target device does not exist", mDevice);

        String deviceSerialNumber = mDevice.getSerialNumber();

        CLog.i("testGetUsbSpeed on device [%s]", deviceSerialNumber);

        String output = mDevice.executeShellCommand("svc usb getUsbSpeed");
        int speed = Integer.parseInt(output.trim());

        Assert.assertNotNull("There is no USB enumeration", speed);
    }

    /**
     * Check for USB resetUsbGadget function.
     *
     * <p>This command will reset USB Gadget.
     */
    @RequiresDevice
    @Test
    public void testResetUsbGadget() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);

        Assert.assertNotNull("Target device does not exist", mDevice);

        String deviceSerialNumber = mDevice.getSerialNumber();

        CLog.i("testResetUsbGadget on device [%s]", deviceSerialNumber);

        new Thread(new Runnable() {
            public void run() {
                try {
                    mDevice.waitForDeviceNotAvailable(CONN_TIMEOUT);
                    RunUtil.getDefault().sleep(300);
                    mDevice.waitForDeviceAvailable(CONN_TIMEOUT);
                    mReconnected = true;
                } catch (DeviceNotAvailableException dnae) {
                    CLog.e("Device is not available");
                } catch (RunInterruptedException ie) {
                    CLog.w("Thread.sleep interrupted");
                }
            }
        }).start();

        RunUtil.getDefault().sleep(100);
        String cmd = "svc usb resetUsbGadget";
        CLog.i("Invoke shell command [" + cmd + "]");
        long startTime = System.currentTimeMillis();
        mDevice.executeShellCommand("svc usb resetUsbGadget");
        while (!mReconnected && System.currentTimeMillis() - startTime < CONN_TIMEOUT) {
            RunUtil.getDefault().sleep(100);
        }

        Assert.assertTrue("usb not reconnect", mReconnected);
    }
}
