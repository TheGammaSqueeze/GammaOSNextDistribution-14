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

package android.usb.cts;

import static android.Manifest.permission.MANAGE_USB;

import com.android.compatibility.common.util.SystemUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_DOCK;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_DOCK_DEVICE_MODE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_DOCK_HOST_MODE;

import android.annotation.Nullable;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.content.Context;
import android.hardware.usb.IUsbOperationInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.hardware.usb.DisplayPortAltModeInfo;
import android.util.Log;

import android.os.Build;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.util.function.Consumer;
import java.util.concurrent.Executor;
import java.util.List;

import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link android.hardware.usb.UsbPortStatus}.
 * Note: MUST claimed MANAGE_USB permission in Manifest
 */
@RunWith(AndroidJUnit4.class)
public class UsbPortStatusApiTest {
    private static final String TAG = UsbPortStatusApiTest.class.getSimpleName();

    private Context mContext;

    private UsbManager mUsbManagerSys =
            InstrumentationRegistry.getContext().getSystemService(UsbManager.class);
    private UsbManager mUsbManagerMock;
    @Mock private android.hardware.usb.IUsbManager mMockUsbService;

    private UsbPort mUsbPort;
    private UsbPort mMockUsbPort;
    private UsbPortStatus mUsbPortStatus;
    private DisplayPortAltModeInfo mDisplayPortAltModeInfo;

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

        mUsbPort = new UsbPort(mUsbManagerSys, "1", 0, 0, true, true, true,
                UsbPort.FLAG_ALT_MODE_TYPE_DISPLAYPORT);
        mUsbPortStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0);

        mDisplayPortAltModeInfo = new DisplayPortAltModeInfo();
    }

    /**
     * Verify that getComplianceWarnings is initialized to empty array on older
     * version of UsbPortStatus constructors.
     */
    @Test
    public void test_UsbApiForGetComplianceWarnings() throws Exception {
        int[] complianceWarnings;

        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Check to see that build version is valid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                complianceWarnings = mUsbPortStatus.getComplianceWarnings();
                Assert.assertTrue(complianceWarnings.length == 0);
            } catch (Exception e) {
                Assert.fail("Unexpected Exception on getNonCompliantReasons");
            }
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify that DATA_STATUS_DISABLED_DOCK is set when DATA_STATUS_DISABLED_DOCK_HOST_MODE is set.
     */
    @Test
    public void test_UsbApiDataStatusHostModeCheck() throws Exception {
        UsbPortStatus portStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0,
            DATA_STATUS_DISABLED_DOCK_HOST_MODE, false, 0);
        Assert.assertTrue((portStatus.getUsbDataStatus() & DATA_STATUS_DISABLED_DOCK)
                 == DATA_STATUS_DISABLED_DOCK);
    }

    /**
     * Verify that DATA_STATUS_DISABLED_DOCK is set when  DATA_STATUS_DISABLED_DOCK_DEVICE_MODE
     * is set.
     */
    @Test
    public void test_UsbApiDataStatusGadgetModeCheck() throws Exception {
        UsbPortStatus portStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0,
            DATA_STATUS_DISABLED_DOCK_DEVICE_MODE, false, 0);
        Assert.assertTrue((portStatus.getUsbDataStatus() & DATA_STATUS_DISABLED_DOCK)
                 == DATA_STATUS_DISABLED_DOCK);
    }

    /**
     * Verify that DATA_STATUS_DISABLED_DOCK is not true when neither subflags
     * (viz., DATA_STATUS_DISABLED_DOCK_DEVICE_MODE,
     * DATA_STATUS_DISABLED_DOCK_HOST_MODE) is true.
     */
    @Test
    public void test_UsbApiDataStatusDisabledDockCheck() throws Exception {
        UsbPortStatus portStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0,
            DATA_STATUS_DISABLED_DOCK, false, 0);
        Assert.assertTrue((portStatus.getUsbDataStatus() & DATA_STATUS_DISABLED_DOCK)
                 != DATA_STATUS_DISABLED_DOCK);
    }

    /**
     * Verify that getPlugState is initialized to default value.
     */
    @Test
    public void test_UsbApiForGetPlugState() throws Exception {
        // Adopt MANAGE_USB permission to access UsbPort and UsbPortStatus
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Simulate UsbPort
        final String portId = "ctstest-plugState";
        UsbPort port = setupSimulatedPort(portId);
        UsbPortStatus portStatus = port.getStatus();

        assertEquals(UsbPortStatus.PLUG_STATE_UNKNOWN,
                portStatus.getPlugState());

        removeSimulatedPort(portId);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify that getDpAltModeCableStatus(), getDpAltModePartnerStatus(), getDpAltModePinConfig(),
     * isHotPlugDetectActive(), and getLinkTrainingStatus() are initialized to default values.
     */
    @Test
    public void test_UsbApiForDisplayPortAltMode() throws Exception {
        // Adopt MANAGE_USB permission to access UsbPort and UsbPortStatus
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Simulate UsbPort
        final String portId = "ctstest-displayPortAltMode";
        UsbPort port = setupSimulatedPort(portId);
        UsbPortStatus portStatus = port.getStatus();
        DisplayPortAltModeInfo displayPortAltModeInfo = portStatus.getDisplayPortAltModeInfo();

        assertNotNull(displayPortAltModeInfo);
        assertEquals(DisplayPortAltModeInfo.DISPLAYPORT_ALT_MODE_STATUS_UNKNOWN,
                displayPortAltModeInfo.getPartnerSinkStatus());
        assertEquals(DisplayPortAltModeInfo.DISPLAYPORT_ALT_MODE_STATUS_UNKNOWN,
                displayPortAltModeInfo.getCableStatus());
        assertEquals(0, displayPortAltModeInfo.getNumberOfLanes());
        assertFalse(displayPortAltModeInfo.isHotPlugDetectActive());
        assertEquals(DisplayPortAltModeInfo.LINK_TRAINING_STATUS_UNKNOWN,
                displayPortAltModeInfo.getLinkTrainingStatus());

        removeSimulatedPort(portId);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify that changes to mLinkTrainingStatus within DisplayPortAltModeInfo
     * are properly reflected.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeLinkTrainingStatus() throws Exception {
        // Adopt MANAGE_USB permission to access UsbPort and UsbPortStatus
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        final String portId = "ctstest-linkTrainingStatus";
        UsbPort port = setupSimulatedPort(portId,
                "0 0 0 false " + DisplayPortAltModeInfo.LINK_TRAINING_STATUS_SUCCESS);

        UsbPortStatus portStatus = port.getStatus();
        DisplayPortAltModeInfo displayPortAltModeInfo = portStatus.getDisplayPortAltModeInfo();
        assertEquals(DisplayPortAltModeInfo.LINK_TRAINING_STATUS_SUCCESS,
                displayPortAltModeInfo.getLinkTrainingStatus());

        removeSimulatedPort(portId);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Verify that changes to mHotPlugDetect within DisplayPortAltModeInfo
     * are properly reflected.
     */
    @Test
    public void test_UsbApiForDisplayPortAltModeHotPlugDetect() throws Exception {
        // Adopt MANAGE_USB permission to access UsbPort and UsbPortStatus
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        final String portId = "ctstest-hotPlugDetect";
        UsbPort port = setupSimulatedPort(portId, "0 0 0 true 0");

        UsbPortStatus portStatus = port.getStatus();
        DisplayPortAltModeInfo displayPortAltModeInfo = portStatus.getDisplayPortAltModeInfo();
        assertTrue(displayPortAltModeInfo.isHotPlugDetectActive());

        removeSimulatedPort(portId);

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }

    /**
     * Sets up a simulated UsbPort with full functionality.
     */
    UsbPort setupSimulatedPort(String portId) {
        SystemUtil.runShellCommand("dumpsys usb add-port " + portId
                + " dual --compliance-warnings --displayport", null);

        for (UsbPort p : mUsbManagerSys.getPorts()) {
            if (p.getId().equals(portId)) {
                return p;
            }
        }
        fail("Could not find the port after add-port");
        return null;
    }

    /**
     * Sets up a simulated UsbPort and sets the DisplayPortAltModeInfo values if given.
     * setDisplayPortStatusString is expected to be given with format
     * "<partner-sink> <cable> <num-lanes> <hpd> <link-training-status>" with given types:
     *      <partner-sink>          type DisplayPortAltModeStatus
     *      <cable>                 type DisplayPortAltModeStatus
     *      <num-lanes>             type int, with typical values of 0, 2, or 4
     *      <hpd>                   type boolean, with values true or false
     *      <link-training-status>  type LinkTrainingStatus
     */
    UsbPort setupSimulatedPort(String portId, @Nullable String setDisplayPortStatusString) {
        UsbPort port = setupSimulatedPort(portId);
        assertNotNull(port);

        if (setDisplayPortStatusString != null) {
            SystemUtil.runShellCommand("dumpsys usb set-displayport-status "
            + portId + " " + setDisplayPortStatusString, null);
        }
        return port;
    }

    void removeSimulatedPort(String portId) {
        SystemUtil.runShellCommand("dumpsys usb remove-port " + portId, null);
    }
}
