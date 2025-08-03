/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.usb.cts;

import static android.Manifest.permission.MANAGE_USB;

import com.android.compatibility.common.util.SystemUtil;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.IDisplayPortAltModeInfoListener;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbManager.DisplayPortAltModeInfoListener;
import android.hardware.usb.DisplayPortAltModeInfo;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.Assert;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link android.hardware.usb.UsbManager}.
 * Note: MUST claimed MANAGE_USB permission in Manifest
 */
@RunWith(AndroidJUnit4.class)
public class UsbManagerApiTest {
    private static final String TAG = UsbManagerApiTest.class.getSimpleName();

    private UsbManager mUsbManagerSys =
        InstrumentationRegistry.getContext().getSystemService(UsbManager.class);

    // Update latest HAL version here
    private int USB_HAL_LATEST_VERSION = UsbManager.USB_HAL_V1_3;

    private UiAutomation mUiAutomation =
        InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private Context mContext;
    private Executor mExecutor;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mExecutor = mContext.getMainExecutor();
        PackageManager pm = mContext.getPackageManager();

        boolean hasUsbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        boolean hasUsbAccessory =
            pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);
        Assume.assumeTrue(hasUsbHost || hasUsbAccessory);
        Assert.assertNotNull(mUsbManagerSys);
    }

    /**
     * Verify NO SecurityException.
     * Go through System Server.
     */
    @Test
    public void test_UsbApiSetGetCurrentFunctionsSys() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        mUsbManagerSys.setCurrentFunctions(UsbManager.FUNCTION_NONE);
        Assert.assertEquals("CurrentFunctions mismatched: ", UsbManager.FUNCTION_NONE,
                mUsbManagerSys.getCurrentFunctions());

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbManagerSys.getCurrentFunctions();
            Assert.fail("Expecting SecurityException on getCurrentFunctions.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on getCurrentFunctions");
        }

        try {
            mUsbManagerSys.setCurrentFunctions(UsbManager.FUNCTION_NONE);
            Assert.fail("Expecting SecurityException on setCurrentFunctions.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on setCurrentFunctions");
        }
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForUsbGadgetHal() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        int version = mUsbManagerSys.getGadgetHalVersion();
        int usbBandwidth = mUsbManagerSys.getUsbBandwidthMbps();
        if (version > UsbManager.GADGET_HAL_V1_2) {
            Assert.assertTrue(usbBandwidth >= UsbManager.USB_DATA_TRANSFER_RATE_UNKNOWN);
        } else if (version > UsbManager.GADGET_HAL_V1_1) {
            Assert.assertTrue(usbBandwidth > UsbManager.USB_DATA_TRANSFER_RATE_UNKNOWN);
        } else {
            Assert.assertEquals(usbBandwidth, UsbManager.USB_DATA_TRANSFER_RATE_UNKNOWN);
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbManagerSys.getGadgetHalVersion();
            Assert.fail("Expecting SecurityException on getGadgetHalVersion.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on getGadgetHalVersion.");
        }
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForUsbHal() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        int version = mUsbManagerSys.getUsbHalVersion();
        if (version == USB_HAL_LATEST_VERSION) {
            Log.d(TAG, "Running with the latest HAL version");
        } else if (version == UsbManager.USB_HAL_NOT_SUPPORTED) {
            Log.d(TAG, "Not supported HAL version");
        }
        else {
            Log.d(TAG, "Not the latest HAL version");
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbManagerSys.getUsbHalVersion();
            Assert.fail("Expecting SecurityException on getUsbHalVersion.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on getUsbHalVersion.");
        }
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeRegisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        final DisplayPortAltModeInfoListener displayPortListener =
                new DisplayPortAltModeInfoListener() {
            public void onDisplayPortAltModeInfoChanged(String portId,
                    DisplayPortAltModeInfo dpInfo) {
                Log.d(TAG, "test_UsbApiForDisplayPortAltModeRegisterSecurity listener called");
            };
        };

        mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, displayPortListener);
        mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(displayPortListener);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        assertThrows(SecurityException.class, () ->
                mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor,
                displayPortListener));
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeUnregisterSecurity() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        final DisplayPortAltModeInfoListener displayPortListener =
                new DisplayPortAltModeInfoListener() {
            public void onDisplayPortAltModeInfoChanged(String portId,
                    DisplayPortAltModeInfo dpInfo) {
                Log.d(TAG, "test_UsbApiForDisplayPortAltModeUnregisterSecurity listener called");
            };
        };

        mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, displayPortListener);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        assertThrows(SecurityException.class, () ->
                mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(displayPortListener));
    }

    /**
     * Verify DisplayPortAltModeInfo changes properly invoke consumers from
     * registerDisplayPortAltModeInfoListener.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeRegisterFunctionality() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        final String portIdTestString = "ctstest-singlelistener";
        final CountDownLatch notifiedForCtsPort = new CountDownLatch(1);

        // Should pass with permission.
        final LatchedDisplayPortAltModeInfoListener displayPortListener =
                new LatchedDisplayPortAltModeInfoListener(notifiedForCtsPort);

        mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, displayPortListener);

        SystemUtil.runShellCommand("dumpsys usb add-port " + portIdTestString
                + " dual --displayport");
        SystemUtil.runShellCommand("dumpsys usb set-displayport-status "
                + portIdTestString + " 2 2 2 false 0");

        assertTrue(notifiedForCtsPort.await(1000, TimeUnit.MILLISECONDS));
        mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(displayPortListener);

        SystemUtil.runShellCommand("dumpsys usb remove-port " + portIdTestString);

        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify DisplayPortAltModeInfo changes properly invoke consumers from
     * registerDisplayPortAltModeInfoListener.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeRegisterMultiListenerFunctionality()
            throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        final String portIdTestString = "ctstest-multilistener";
        final int numListeners = 2;
        final CountDownLatch notifiedForCtsPort = new CountDownLatch(numListeners);

        // Should pass with permission.
        final ArrayList<LatchedDisplayPortAltModeInfoListener> listeners =
                new ArrayList<LatchedDisplayPortAltModeInfoListener>();
        for (int i = 0; i < numListeners; i++) {
            final LatchedDisplayPortAltModeInfoListener listener =
                    new LatchedDisplayPortAltModeInfoListener(notifiedForCtsPort);
            mUsbManagerSys.registerDisplayPortAltModeInfoListener(mExecutor, listener);
            listeners.add(listener);
        }

        SystemUtil.runShellCommand("dumpsys usb add-port " + portIdTestString
                + " dual --displayport");
        SystemUtil.runShellCommand("dumpsys usb set-displayport-status "
                + portIdTestString + " 2 2 2 false 0");

        assertTrue(notifiedForCtsPort.await(1000, TimeUnit.MILLISECONDS));

        for (int i = 0; i < numListeners; i++) {
            mUsbManagerSys.unregisterDisplayPortAltModeInfoListener(listeners.get(i));
        }

        SystemUtil.runShellCommand("dumpsys usb remove-port " + portIdTestString);

        mUiAutomation.dropShellPermissionIdentity();
    }

    private static class LatchedDisplayPortAltModeInfoListener implements
            DisplayPortAltModeInfoListener {
        private final CountDownLatch mLatch;

        public LatchedDisplayPortAltModeInfoListener(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onDisplayPortAltModeInfoChanged(String portId,
                    DisplayPortAltModeInfo dpInfo) {
            mLatch.countDown();
        }
    }
}
