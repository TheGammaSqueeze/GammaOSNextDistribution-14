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

package com.android.server.wifi.aware;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hal.WifiNanIface;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;


/**
 * Unit test harness for WifiAwareNativeApi
 */
@SmallTest
public class WifiAwareNativeApiTest extends WifiBaseTest {
    @Mock WifiAwareNativeManager mWifiAwareNativeManagerMock;
    @Mock WifiNanIface mWifiNanIfaceMock;

    @Rule public ErrorCollector collector = new ErrorCollector();

    private WifiAwareNativeApi mDut;

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiAwareNativeManagerMock.getWifiNanIface()).thenReturn(mWifiNanIfaceMock);
        mDut = new WifiAwareNativeApi(mWifiAwareNativeManagerMock);
        mDut.enableVerboseLogging(true, true);
    }

    /**
     * Test that the set parameter shell command executor works when parameters are valid.
     */
    @Test
    public void testSetParameterShellCommandSuccess() {
        setSettableParam(WifiAwareNativeApi.PARAM_MAC_RANDOM_INTERVAL_SEC, Integer.toString(1),
                true);
    }

    /**
     * Test that the set parameter shell command executor fails on incorrect name.
     */
    @Test
    public void testSetParameterShellCommandInvalidParameterName() {
        setSettableParam("XXX", Integer.toString(1), false);
    }

    /**
     * Test that the set parameter shell command executor fails on invalid value (not convertible
     * to an int).
     */
    @Test
    public void testSetParameterShellCommandInvalidValue() {
        setSettableParam(WifiAwareNativeApi.PARAM_MAC_RANDOM_INTERVAL_SEC, "garbage", false);
    }

    /**
     * Validate disable Aware will pass to the NAN interface, and trigger releaseAware.
     * @throws Exception
     */
    @Test
    public void testDisableConfigRequest() throws Exception {
        when(mWifiNanIfaceMock.disable(anyShort())).thenReturn(true);
        assertTrue(mDut.disable((short) 10));
        verify(mWifiNanIfaceMock).disable((short) 10);
    }

    /**
     * Validate that power configuration params set in the DUT are properly converted to
     * {@link WifiNanIface.PowerParameters}.
     */
    @Test
    public void testConversionToPowerParams() {
        ArgumentCaptor<WifiNanIface.PowerParameters> powerParamsCaptor = ArgumentCaptor.forClass(
                WifiNanIface.PowerParameters.class);
        when(mWifiNanIfaceMock.enableAndConfigure(anyShort(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(true);

        // Call enableAndConfigure without changing the config.
        mDut.enableAndConfigure((short) 1, null, true, true,
                false, true /* isIdle */, true, true, 1, -1);
        verify(mWifiNanIfaceMock).enableAndConfigure(anyShort(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(),
                eq(1800),   // PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT
                powerParamsCaptor.capture());

        // Expect the default power parameters.
        WifiNanIface.PowerParameters powerParams = powerParamsCaptor.getValue();
        assertEquals(powerParams.discoveryWindow24Ghz, 4);  // PARAM_DW_24GHZ_IDLE
        assertEquals(powerParams.discoveryWindow5Ghz, 0);   // PARAM_DW_5GHZ_IDLE
        assertEquals(powerParams.discoveryWindow6Ghz, 0);   // PARAM_DW_6GHZ_IDLE
        assertEquals(powerParams.discoveryBeaconIntervalMs,
                0); // PARAM_DISCOVERY_BEACON_INTERVAL_MS_IDLE
        assertEquals(powerParams.numberOfSpatialStreamsInDiscovery,
                0); // PARAM_NUM_SS_IN_DISCOVERY_IDLE
        assertFalse(powerParams.enableDiscoveryWindowEarlyTermination);

        // Set custom power configuration params.
        byte idle5 = 2;
        byte idle24 = -1;
        setSettablePowerParam(WifiAwareNativeApi.POWER_PARAM_IDLE_KEY,
                WifiAwareNativeApi.PARAM_DW_5GHZ, Integer.toString(idle5), true);
        setSettablePowerParam(WifiAwareNativeApi.POWER_PARAM_IDLE_KEY,
                WifiAwareNativeApi.PARAM_DW_24GHZ, Integer.toString(idle24), true);

        mDut.enableAndConfigure((short) 1, null, true, true,
                false, true /* isIdle */, true, true, 1, -1);
        verify(mWifiNanIfaceMock, times(2)).enableAndConfigure(anyShort(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(),
                eq(1800),   // PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT
                powerParamsCaptor.capture());

        // Expect the updated power parameters.
        powerParams = powerParamsCaptor.getValue();
        assertEquals(powerParams.discoveryWindow24Ghz, idle24);
        assertEquals(powerParams.discoveryWindow5Ghz, idle5);
        assertEquals(powerParams.discoveryWindow6Ghz, 0);   // PARAM_DW_6GHZ_IDLE
        assertEquals(powerParams.discoveryBeaconIntervalMs,
                0); // PARAM_DISCOVERY_BEACON_INTERVAL_MS_IDLE
        assertEquals(powerParams.numberOfSpatialStreamsInDiscovery,
                0); // PARAM_NUM_SS_IN_DISCOVERY_IDLE
        assertFalse(powerParams.enableDiscoveryWindowEarlyTermination);
    }

    // utilities

    private void setSettablePowerParam(String mode, String name, String value,
            boolean expectSuccess) {
        PrintWriter pwMock = mock(PrintWriter.class);
        WifiAwareShellCommand parentShellMock = mock(WifiAwareShellCommand.class);
        when(parentShellMock.getNextArgRequired()).thenReturn("set-power").thenReturn(
                mode).thenReturn(name).thenReturn(value);
        when(parentShellMock.getErrPrintWriter()).thenReturn(pwMock);

        collector.checkThat(mDut.onCommand(parentShellMock), equalTo(expectSuccess ? 0 : -1));
    }

    private void setSettableParam(String name, String value, boolean expectSuccess) {
        PrintWriter pwMock = mock(PrintWriter.class);
        WifiAwareShellCommand parentShellMock = mock(WifiAwareShellCommand.class);
        when(parentShellMock.getNextArgRequired()).thenReturn("set").thenReturn(name).thenReturn(
                value);
        when(parentShellMock.getErrPrintWriter()).thenReturn(pwMock);

        collector.checkThat(mDut.onCommand(parentShellMock), equalTo(expectSuccess ? 0 : -1));
    }
}
