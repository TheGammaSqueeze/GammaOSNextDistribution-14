/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.RssiMonitor}
 */
@SmallTest
public class RssiMonitorTest extends WifiBaseTest {
    private static final int TEST_CLIENT_RSSI_THRESHOLD = -68;
    private static final int TEST_CLIENT_RSSI_HYSTERESIS = 5;
    private static final int TEST_RSSI_HIGH = -55;
    private static final int TEST_RSSI_MEDIUM = -65;
    private static final int TEST_RSSI_LOW = -70;
    private static final int TEST_APP_RSSI_THRESHOLD_BREACH_LOW = -80;
    private static final int TEST_APP_RSSI_THRESHOLD_BREACH_HIGH = -50;
    private static final int TEST_POLL_INTERVAL_SHORT = 3000;
    private static final int TEST_POLL_INTERVAL_LONG = 9000;
    private static final int TEST_POLL_INTERVAL_FIXED = 1000;
    private static final int[] TEST_APP_RSSI_THRESHOLDS = new int[] {-75};
    private static final String TEST_INTERFACE_NAME = "wlan0";

    private RssiMonitor mRssiMonitor;
    private WifiGlobals mWifiGlobals;
    private TestLooper mLooper;
    @Mock Context mContext;
    MockResources mMockResources = new MockResources();
    private final WifiInfo mWifiInfo = new ExtendedWifiInfo(mWifiGlobals, TEST_INTERFACE_NAME);
    @Mock WifiNative mWifiNative;
    @Mock Runnable mUpdateCapabilityRunnable;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Captor ArgumentCaptor<WifiNative.WifiRssiEventHandler> mRssiEventHandlerCaptor;

    /**
     * Sets up for unit test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getResources()).thenReturn(mMockResources);
        when(mDeviceConfigFacade.isAdjustPollRssiIntervalEnabled()).thenReturn(true);

        mMockResources.setInteger(R.integer.config_wifiPollRssiIntervalMilliseconds,
                TEST_POLL_INTERVAL_SHORT);
        mMockResources.setInteger(R.integer.config_wifiPollRssiLongIntervalMilliseconds,
                TEST_POLL_INTERVAL_LONG);
        mMockResources.setInteger(R.integer.config_wifiClientRssiMonitorThresholdDbm,
                TEST_CLIENT_RSSI_THRESHOLD);
        mMockResources.setInteger(R.integer.config_wifiClientRssiMonitorHysteresisDb,
                TEST_CLIENT_RSSI_HYSTERESIS);
        mMockResources.setBoolean(R.bool.config_wifiAdjustPollRssiIntervalEnabled,
                true);

        mWifiGlobals = new WifiGlobals(mContext);
        mLooper = new TestLooper();
        WifiThreadRunner wifiThreadRunner = new WifiThreadRunner(new Handler(mLooper.getLooper()));

        mRssiMonitor = new RssiMonitor(mWifiGlobals, wifiThreadRunner, mWifiInfo, mWifiNative,
                TEST_INTERFACE_NAME, mUpdateCapabilityRunnable, mDeviceConfigFacade);
        mRssiMonitor.enableVerboseLogging(true);

        mWifiInfo.setRssi(TEST_RSSI_HIGH);
        mRssiMonitor.updateAppThresholdsAndStartMonitor(TEST_APP_RSSI_THRESHOLDS);
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE),
                eq((byte) TEST_APP_RSSI_THRESHOLDS[0]), mRssiEventHandlerCaptor.capture());
    }

    private void setupClientMonitorHighRssiStationary() throws Exception {
        mWifiInfo.setRssi(TEST_RSSI_HIGH);
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
    }

    private void verifyShortIntervalAndMonitorStatus() throws Exception {
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative, times(2)).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq(Byte.MAX_VALUE), eq((byte) TEST_APP_RSSI_THRESHOLDS[0]),
                mRssiEventHandlerCaptor.capture());
    }

    private void verifyLongIntervalAndMonitorStatus() throws Exception {
        assertEquals(TEST_POLL_INTERVAL_LONG, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE),
                eq((byte) TEST_CLIENT_RSSI_THRESHOLD), mRssiEventHandlerCaptor.capture());
    }

    /**
     * Check if RSSI polling interval is set to long interval and client RSSI monitor is
     * started, when device is stationary and RSSI is high.
     */
    @Test
    public void testUpdatePollRssiIntervalStationaryHighRssi() throws Exception {
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
        setupClientMonitorHighRssiStationary();
        verifyLongIntervalAndMonitorStatus();
    }

    /**
     * Check when using the long interval and client RSSI monitor enabled in stationary state,
     * if the RSSI drops slightly but still greater than the RSSI threshold, whether the
     * polling interval is handled properly based on the hysteresis RSSI.
     */
    @Test
    public void testUpdatePollRssiIntervalStationaryHighToHysteresisRssi() throws Exception {
        setupClientMonitorHighRssiStationary();
        mWifiInfo.setRssi(TEST_RSSI_MEDIUM);
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        assertEquals(TEST_POLL_INTERVAL_LONG, mWifiGlobals.getPollRssiIntervalMillis());
    }

    /**
     * Check when using the long interval and client RSSI monitor enabled in stationary state,
     * if the RSSI drops below the RSSI threshold, whether the interval is set to short interval
     * and client RSSI monitor is stopped.
     */
    @Test
    public void testUpdatePollRssiIntervalStationaryHighToLowRssi() throws Exception {
        setupClientMonitorHighRssiStationary();
        mWifiInfo.setRssi(TEST_RSSI_LOW);
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        verifyShortIntervalAndMonitorStatus();
    }

    /**
    * Check when using the long interval and client RSSI monitor enabled in stationary state,
    * if the mobility state changes to non-stationary (low movement in this case), whether
    * the interval is set to short interval and client RSSI monitor is stopped.
    */
    @Test
    public void testUpdatePollRssiIntervalNonStationaryHighRssi() throws Exception {
        setupClientMonitorHighRssiStationary();
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);
        verifyShortIntervalAndMonitorStatus();
    }

    /**
     * Check when device is stationary but RSSI is low, if the RSSI goes above the RSSI
     * threshold + Hysteresis RSSI, whether the interval is set to long interval and client
     * RSSI monitor is started properly.
     */
    @Test
    public void testUpdatePollRssiIntervalStationaryLowToHighRssi() throws Exception {
        mWifiInfo.setRssi(TEST_RSSI_LOW);
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
        setupClientMonitorHighRssiStationary();
        verifyLongIntervalAndMonitorStatus();
    }

    /**
     * Check when using the long interval and client RSSI monitor enabled in stationary state,
     * whether the interval and client RSSI monitor are handled properly for the event of
     * RSSI threshold breach.
     */
    @Test
    public void testOnClientRssiThresholdBreached() throws Exception {
        setupClientMonitorHighRssiStationary();
        mRssiEventHandlerCaptor.getValue().onRssiThresholdBreached((byte) TEST_RSSI_LOW);
        mLooper.dispatchAll();
        assertEquals(TEST_RSSI_LOW, mWifiInfo.getRssi());
        verifyShortIntervalAndMonitorStatus();
    }

    /**
     * Check when RSSI threshold breach happens for the App RSSI monitor, whether the new
     * monitoring thresholds are set properly. First test when RSSI drops below the threshold,
     * then test when RSSI becomes higher than the threshold.
     */
    @Test
    public void testOnAppRssiThresholdBreached() throws Exception {
        mRssiEventHandlerCaptor.getValue()
                .onRssiThresholdBreached((byte) TEST_APP_RSSI_THRESHOLD_BREACH_LOW);
        mLooper.dispatchAll();
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq((byte) TEST_APP_RSSI_THRESHOLDS[0]), eq(Byte.MIN_VALUE),
                mRssiEventHandlerCaptor.capture());
        assertEquals(TEST_APP_RSSI_THRESHOLD_BREACH_LOW, mWifiInfo.getRssi());

        mRssiEventHandlerCaptor.getValue()
                .onRssiThresholdBreached((byte) TEST_APP_RSSI_THRESHOLD_BREACH_HIGH);
        mLooper.dispatchAll();
        verify(mWifiNative, times(2)).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq(Byte.MAX_VALUE), eq((byte) TEST_APP_RSSI_THRESHOLDS[0]),
                mRssiEventHandlerCaptor.capture());
        assertEquals(TEST_APP_RSSI_THRESHOLD_BREACH_HIGH, mWifiInfo.getRssi());
    }

    /** Check when App and framework RSSI monitor have the same threshold value, if the RSSI
     * threshold breach event happens for low RSSI, whether the framework monitor is stopped
     * and the App monitor is restarted properly without removing the threshold by mistake.
     */
    @Test
    public void testOnRssiThresholdBreachedIdenticalAppFrameworkThreshold() throws Exception {
        mWifiInfo.setRssi(TEST_RSSI_HIGH);
        mRssiMonitor.updateAppThresholdsAndStartMonitor(new int [] {TEST_CLIENT_RSSI_THRESHOLD});
        mLooper.dispatchAll();
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE),
                eq((byte) TEST_CLIENT_RSSI_THRESHOLD), mRssiEventHandlerCaptor.capture());

        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiNative, times(2)).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq(Byte.MAX_VALUE), eq((byte) TEST_CLIENT_RSSI_THRESHOLD),
                mRssiEventHandlerCaptor.capture());
        assertEquals(TEST_POLL_INTERVAL_LONG, mWifiGlobals.getPollRssiIntervalMillis());

        mRssiEventHandlerCaptor.getValue()
                .onRssiThresholdBreached((byte) TEST_RSSI_LOW);
        mLooper.dispatchAll();
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq((byte) TEST_CLIENT_RSSI_THRESHOLD), eq(Byte.MIN_VALUE),
                mRssiEventHandlerCaptor.capture());
        assertEquals(TEST_RSSI_LOW, mWifiInfo.getRssi());
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
    }

    /**
     * Verify when the App RSSI thresholds are empty, whether the RSSI monitor stops properly
     */
    @Test
    public void testStopAppRssiMonitor() throws Exception {
        mRssiMonitor.updateAppThresholdsAndStartMonitor(new int[] {});
        verify(mWifiNative).stopRssiMonitoring(eq(TEST_INTERFACE_NAME));
    }

    /**
     * Verify that client mode RSSI monitor is not triggered by mistake when the feature of
     * adjusting RSSI polling interval is disabled
     */
    @Test
    public void testAdjustPollRssiIntervalDisabled() throws Exception {
        when(mDeviceConfigFacade.isAdjustPollRssiIntervalEnabled()).thenReturn(false);
        setupClientMonitorHighRssiStationary();
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative, never()).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq(Byte.MAX_VALUE), eq((byte) TEST_CLIENT_RSSI_THRESHOLD),
                mRssiEventHandlerCaptor.capture());
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE),
                eq((byte) TEST_APP_RSSI_THRESHOLDS[0]), mRssiEventHandlerCaptor.capture());
    }

    /**
     * Verify that the fixed link layer stats polling interval is set correctly by
     * overridePollRssiInterval() when the feature of adjusting the polling interval
     * is disabled
     */
    @Test
    public void testOverridePollRssiIntervalAdjustIntervalDisabled() throws Exception {
        when(mDeviceConfigFacade.isAdjustPollRssiIntervalEnabled()).thenReturn(false);
        mRssiMonitor.overridePollRssiInterval(TEST_POLL_INTERVAL_FIXED);
        assertEquals(TEST_POLL_INTERVAL_FIXED, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative, never()).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq(Byte.MAX_VALUE), eq((byte) TEST_CLIENT_RSSI_THRESHOLD),
                mRssiEventHandlerCaptor.capture());
        mRssiMonitor.overridePollRssiInterval(0);
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
    }

    /**
     * Verify that the fixed link layer stats polling interval is set correctly by
     * overridePollRssiInterval() when the feature of adjusting the polling interval
     * is enabled
     */
    @Test
    public void testOverridePollRssiIntervalAdjustIntervalEnabled() throws Exception {
        // Check if the interval changes to fixed when setting it while current interval is
        // long interval and client mode RSSI monitor is enabled
        setupClientMonitorHighRssiStationary();
        mRssiMonitor.overridePollRssiInterval(TEST_POLL_INTERVAL_FIXED);
        assertEquals(TEST_POLL_INTERVAL_FIXED, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative, times(2)).startRssiMonitoring(
                eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE),
                eq((byte) TEST_APP_RSSI_THRESHOLDS[0]), mRssiEventHandlerCaptor.capture());
        // Check if the interval stays fixed when updatePollRssiInterval() is called
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        assertEquals(TEST_POLL_INTERVAL_FIXED, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative).startRssiMonitoring(eq(TEST_INTERFACE_NAME),
                eq(Byte.MAX_VALUE), eq((byte) TEST_CLIENT_RSSI_THRESHOLD),
                mRssiEventHandlerCaptor.capture());
        // Check if the interval stays fixed when setShortPollRssiInterval() is called
        mRssiMonitor.setShortPollRssiInterval();
        assertEquals(TEST_POLL_INTERVAL_FIXED, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative, times(2)).startRssiMonitoring(
                eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE),
                eq((byte) TEST_APP_RSSI_THRESHOLDS[0]), mRssiEventHandlerCaptor.capture());
        // Check if the interval changes back to short interval after setting it to automatic
        // handling
        mRssiMonitor.overridePollRssiInterval(0);
        assertEquals(TEST_POLL_INTERVAL_SHORT, mWifiGlobals.getPollRssiIntervalMillis());
        // Check if the interval changes to long interval when updatePollRssiInterval() is called
        // for automatic handling, while the device is stationary and RSSI is high
        mRssiMonitor.updatePollRssiInterval(WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        assertEquals(TEST_POLL_INTERVAL_LONG, mWifiGlobals.getPollRssiIntervalMillis());
        verify(mWifiNative, times(2)).startRssiMonitoring(
                eq(TEST_INTERFACE_NAME), eq(Byte.MAX_VALUE), eq((byte) TEST_CLIENT_RSSI_THRESHOLD),
                mRssiEventHandlerCaptor.capture());
    }
}
