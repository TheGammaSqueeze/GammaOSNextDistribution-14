/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.content.Context;
import android.hardware.usb.IUsbOperationInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.util.Log;

import android.os.Build;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.util.function.Consumer;
import java.util.concurrent.Executor;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link android.hardware.usb.UsbPort}.
 * Note: MUST claimed MANAGE_USB permission in Manifest
 */
@RunWith(AndroidJUnit4.class)
public class UsbPortApiTest {
    private static final String TAG = UsbPortApiTest.class.getSimpleName();

    private Context mContext;

    private UsbManager mUsbManagerSys =
        InstrumentationRegistry.getContext().getSystemService(UsbManager.class);
    private UsbManager mUsbManagerMock;
    @Mock private android.hardware.usb.IUsbManager mMockUsbService;

    private UsbPort mUsbPort;
    private UsbPort mMockUsbPort;

    private UiAutomation mUiAutomation =
        InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private Executor mExecutor;
    private Consumer<Integer> mConsumer;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mExecutor = mContext.getMainExecutor();
        PackageManager pm = mContext.getPackageManager();
        MockitoAnnotations.initMocks(this);

        Assert.assertNotNull(mUsbManagerSys);
        Assert.assertNotNull(mUsbManagerMock =
                new UsbManager(mContext, mMockUsbService));
        mUsbPort = new UsbPort(mUsbManagerSys, "1", 0, 0, true, true);
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForResetUsbPort() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        mMockUsbPort = new UsbPort(mUsbManagerMock, "1", 0, 0, true, true);
        boolean result = true;

        mConsumer = new Consumer<Integer>(){
            public void accept(Integer status){
                Log.d(TAG, "Consumer status:" + status);
            };
        };
        // Should pass with permission.
        mMockUsbService.resetUsbPort(anyString(), anyInt(),
                  any(IUsbOperationInternal.class));
        mMockUsbPort.resetUsbPort(mExecutor, mConsumer);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbPort.resetUsbPort(mExecutor, mConsumer);
            Assert.fail("SecurityException not thrown for resetUsbPort when MANAGE_USB is not acquired.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "SecurityException expected on resetUsbPort  when MANAGE_USB is not acquired.");
        }
    }

    /**
     * Verify that SecurityException is thrown when MANAGE_USB is not
     * held and not thrown when MANAGE_USB is held.
     */
    @Test
    public void test_UsbApiForEnableUsbDataWhileDocked() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        try {
            mUsbPort.enableUsbDataWhileDocked();
        } catch (SecurityException secEx) {
            Assert.fail("Unexpected SecurityException on enableUsbDataWhileDocked.");
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbPort.enableUsbDataWhileDocked();
            Assert.fail(
                "SecurityException not thrown for enableUsbDataWhileDocked when MANAGE_USB is not acquired.");
        } catch (SecurityException secEx) {
            Log.i(TAG,
                "SecurityException expected on enableUsbDataWhileDocked when MANAGE_USB is not acquired.");
        }
    }

    /**
     * Verify that SecurityException is thrown when MANAGE_USB is not
     * held and not thrown when MANAGE_USB is held.
     */
    @Test
    public void test_UsbApiForEnableUsbData() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        try {
            mUsbPort.enableUsbData(true);
        } catch (SecurityException secEx) {
            Assert.fail("Unexpected SecurityException on enableUsbData.");
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbPort.enableUsbData(true);
            Assert.fail(
                "SecurityException not thrown for enableUsbData when MANAGE_USB is not acquired.");
        } catch (SecurityException secEx) {
            Log.i(TAG,
                "SecurityException expected on enableUsbData when MANAGE_USB is not acquired.");
        }
    }

    /**
     * Verify that SecurityException is thrown when MANAGE_USB is not
     * held and not thrown when MANAGE_USB is held.
     */
    @Test
    public void test_UsbApiForEnableLimitPowerTransfer() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        try {
            mUsbPort.enableLimitPowerTransfer(false);
        } catch (SecurityException secEx) {
            Assert.fail("Unexpected SecurityException on enableLimitPowerTransfer.");
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbPort.enableLimitPowerTransfer(false);
            Assert.fail(
                "SecurityException not thrown for enableLimitPowerTransfer when MANAGE_USB is not acquired.");
        } catch (SecurityException secEx) {
            Log.i(TAG,
                "SecurityException expected on enableLimitPowerTransfer when MANAGE_USB is not acquired.");
        }
    }

    /**
     * Verify that SecurityException is thrown when MANAGE_USB is not
     * held and not thrown when MANAGE_USB is held.
     */
    @Test
    public void test_UsbApiForGetStatus() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Should pass with permission.
        try {
            mUsbPort.getStatus();
        } catch (SecurityException secEx) {
            Assert.fail("Unexpected SecurityException on getStatus.");
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbPort.getStatus();
            Assert.fail(
                    "SecurityException not thrown for getStatus when MANAGE_USB is not acquired.");
        } catch (SecurityException secEx) {
            Log.i(TAG,
                    "SecurityException expected on getStatus when MANAGE_USB is not acquired.");
        }
    }

    /**
     * Verify that supportsComplianceWarnings is defaulted to false on
     * older version of UsbPort constructors.
     */
    @Test
    public void test_UsbApiForSupportsComplianceWarnings() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Check to see that build version is valid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                Assert.assertFalse(mUsbPort.supportsComplianceWarnings());
            } catch (Exception e) {
                Assert.fail("Unexpected Exception on supportsNonCompliantIdentification");
            }
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify that getSupportedAltModesMask is defaulted to 0 on older version of UsbPort
     * constructors and uses isAltModeSupported as a consistency check.
     */
    @Test
    public void test_UsbApiForSupportedAltModes() throws Exception {
        Assert.assertTrue(mUsbPort.getSupportedAltModesMask() == 0);
        Assert.assertFalse(mUsbPort.isAltModeSupported(UsbPort.FLAG_ALT_MODE_TYPE_DISPLAYPORT));
    }

}
