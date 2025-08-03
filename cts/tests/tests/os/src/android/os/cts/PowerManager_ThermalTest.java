/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.os.cts;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Flags;
import android.os.PowerManager;
import android.os.PowerManager.OnThermalStatusChangedListener;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ThermalUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PowerManager_ThermalTest {
    private static final long CALLBACK_TIMEOUT_MILLI_SEC = 5000;
    private UiDevice mUiDevice;
    private Context mContext;
    private PowerManager mPowerManager;
    private Executor mExec = Executors.newSingleThreadExecutor();
    @Mock
    private OnThermalStatusChangedListener mListener1;
    @Mock
    private OnThermalStatusChangedListener mListener2;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mPowerManager = mContext.getSystemService(PowerManager.class);
        ThermalUtils.overrideThermalNotThrottling();
    }

    @After
    public void tearDown() throws Exception {
        ThermalUtils.resetThermalStatus();
    }

    @Test
    public void testGetThermalStatus() throws Exception {
        int status = 0; // Temperature.THROTTLING_NONE
        assertEquals(status, mPowerManager.getCurrentThermalStatus());
        status = 3; // Temperature.THROTTLING_SEVERE
        ThermalUtils.overrideThermalStatus(status);
        assertEquals(status, mPowerManager.getCurrentThermalStatus());
    }

    @Test
    public void testThermalStatusCallback() throws Exception {
        // Initial override status is THERMAL_STATUS_NONE
        int status = PowerManager.THERMAL_STATUS_NONE;
        // Add listener1
        mPowerManager.addThermalStatusListener(mExec, mListener1);
        verify(mListener1, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onThermalStatusChanged(status);
        reset(mListener1);
        status = PowerManager.THERMAL_STATUS_SEVERE;
        ThermalUtils.overrideThermalStatus(status);
        verify(mListener1, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onThermalStatusChanged(status);
        reset(mListener1);
        // Add listener1 again
        try {
            mPowerManager.addThermalStatusListener(mListener1);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException expectedException) {
        }
        // Add listener2 on main thread.
        mPowerManager.addThermalStatusListener(mListener2);
        verify(mListener2, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
            .times(1)).onThermalStatusChanged(status);
        reset(mListener2);
        status = PowerManager.THERMAL_STATUS_MODERATE;
        ThermalUtils.overrideThermalStatus(status);
        verify(mListener1, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onThermalStatusChanged(status);
        verify(mListener2, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onThermalStatusChanged(status);
        reset(mListener1);
        reset(mListener2);
        // Remove listener1
        mPowerManager.removeThermalStatusListener(mListener1);
        // Remove listener1 again
        try {
            mPowerManager.removeThermalStatusListener(mListener1);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException expectedException) {
        }
        status = PowerManager.THERMAL_STATUS_LIGHT;
        ThermalUtils.overrideThermalStatus(status);
        verify(mListener1, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(0)).onThermalStatusChanged(status);
        verify(mListener2, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onThermalStatusChanged(status);
    }

    /**
     * Test that getThermalHeadroom works
     *
     * @throws Exception
     */
    @CddTest(requirement="7.3.6")
    @Test
    public void testGetThermalHeadroom() throws Exception {
        float headroom = mPowerManager.getThermalHeadroom(0);
        // If the device doesn't support thermal headroom, return early
        if (Float.isNaN(headroom)) {
            return;
        }
        assertTrue("Expected non-negative headroom", headroom >= 0.0f);
        assertTrue("Expected reasonably small headroom", headroom < 10.0f);

        // Sleep for a second before attempting to call again so as to not get rate limited
        Thread.sleep(1000);
        headroom = mPowerManager.getThermalHeadroom(5);
        assertFalse("Expected data to still be available", Float.isNaN(headroom));
        assertTrue("Expected non-negative headroom", headroom >= 0.0f);
        assertTrue("Expected reasonably small headroom", headroom < 10.0f);

        // Test rate limiting by spamming calls and ensuring that at least the last one fails
        for (int i = 0; i < 20; ++i) {
            headroom = mPowerManager.getThermalHeadroom(5);
        }
        assertTrue("Abusive calls get rate limited", Float.isNaN(headroom));
    }

    @Test
    @ApiTest(apis = {"android.os.PowerManager#getThermalHeadroomThresholds"})
    @RequiresFlagsEnabled(Flags.FLAG_ALLOW_THERMAL_HEADROOM_THRESHOLDS)
    public void testGetThermalHeadroomThresholds() throws Exception {
        float headroom = mPowerManager.getThermalHeadroom(0);
        // If the device doesn't support thermal headroom, return early
        if (Float.isNaN(headroom)) {
            return;
        }
        Map<Integer, Float> thresholds = mPowerManager.getThermalHeadroomThresholds();
        assertTrue("Thermal headroom thresholds should contain SEVERE status",
                thresholds.containsKey(PowerManager.THERMAL_STATUS_SEVERE));
        float severeThreshold = thresholds.get(PowerManager.THERMAL_STATUS_SEVERE);
        assertEquals(1f, severeThreshold, .01f);
        float lastHeadroom = Float.NaN;
        int lastStatus = PowerManager.THERMAL_STATUS_NONE;
        int[] allStatus =
                new int[]{PowerManager.THERMAL_STATUS_LIGHT,
                        PowerManager.THERMAL_STATUS_MODERATE,
                        PowerManager.THERMAL_STATUS_SEVERE,
                        PowerManager.THERMAL_STATUS_CRITICAL,
                        PowerManager.THERMAL_STATUS_EMERGENCY,
                        PowerManager.THERMAL_STATUS_SHUTDOWN};
        for (int status : allStatus) {
            if (thresholds.containsKey(status)) {
                assertFalse(Float.isNaN(thresholds.get(status)));
                headroom = thresholds.get(status);
                if (headroom >= 0) {
                    if (lastStatus == PowerManager.THERMAL_STATUS_NONE) {
                        lastStatus = status;
                        lastHeadroom = headroom;
                    } else {
                        assertTrue("Thermal headroom threshold for status " + status
                                + " is " + headroom + " which should not be smaller than "
                                + "a lower status " + lastStatus + " which is "
                                + lastHeadroom, headroom >= lastHeadroom);
                    }
                } else {
                    fail("Expected non-negative headroom threshold but got " + headroom
                            + " for status "
                            + status);
                }
            }
        }
    }
}
