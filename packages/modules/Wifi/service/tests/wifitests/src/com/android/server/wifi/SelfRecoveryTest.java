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

package com.android.server.wifi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link com.android.server.wifi.SelfRecovery}.
 */
@SmallTest
public class SelfRecoveryTest extends WifiBaseTest {
    private static final int DEFAULT_MAX_RECOVERY_PER_HOUR = 2;
    SelfRecovery mSelfRecovery;
    MockResources mResources;
    @Mock Context mContext;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock Clock mClock;
    @Mock WifiNative mWifiNative;
    @Mock WifiGlobals mWifiGlobals;
    private MockitoSession mSession;
    final ArgumentCaptor<HalDeviceManager.SubsystemRestartListener> mRestartListenerCaptor =
            ArgumentCaptor.forClass(HalDeviceManager.SubsystemRestartListener.class);

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mResources = new MockResources();
        // Default value of 2 recovery per hour.
        mResources.setInteger(R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour,
                DEFAULT_MAX_RECOVERY_PER_HOUR);
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiGlobals.isWifiInterfaceAddedSelfRecoveryEnabled()).thenReturn(false);
        mSelfRecovery = new SelfRecovery(mContext, mActiveModeWarden, mClock, mWifiNative,
                mWifiGlobals);
        verify(mWifiNative).registerSubsystemRestartListener(mRestartListenerCaptor.capture());
        doAnswer((invocation) -> {
            mRestartListenerCaptor.getValue().onSubsystemRestart();
            return true;
        }).when(mWifiNative).startSubsystemRestart();

        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiStatsLog.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    /**
     * Verifies that invocations of {@link SelfRecovery#trigger(int)} with valid reasons will send
     * the restart message to {@link ActiveModeWarden}.
     */
    @Test
    public void testValidTriggerReasonsSendMessageToWifiController() {
        mSelfRecovery.trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                false);
        reset(mActiveModeWarden);

        when(mClock.getElapsedSinceBootMillis())
                .thenReturn(TimeUnit.HOURS.toMillis(1) + 1);
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        reset(mActiveModeWarden);
    }

    /**
     * Verifies that invocations of {@link SelfRecovery#trigger(int)} with invalid reasons will not
     * send the restart message to {@link ActiveModeWarden}.
     */
    @Test
    public void testInvalidTriggerReasonsDoesNotSendMessageToWifiController() {
        mSelfRecovery.trigger(-1);
        verifyNoMoreInteractions(mActiveModeWarden);

        mSelfRecovery.trigger(8);
        verifyNoMoreInteractions(mActiveModeWarden);
    }

    /**
     * Verifies that a STA interface down event will trigger WifiController to disable wifi.
     */
    @Test
    public void testStaIfaceDownDisablesWifi() {
        mSelfRecovery.trigger(SelfRecovery.REASON_STA_IFACE_DOWN);
        verify(mActiveModeWarden).recoveryDisableWifi();
    }

    /**
     * Verifies that invocations of {@link SelfRecovery#trigger(int)} for REASON_WIFI_NATIVE
     * are limited to {@link R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour} in a
     * 1 hour time window.
     */
    @Test
    public void testTimeWindowLimiting_typicalUse() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Fill up the SelfRecovery's restart time window buffer, ensure all the restart triggers
        // aren't ignored
        for (int i = 0; i < DEFAULT_MAX_RECOVERY_PER_HOUR; i++) {
            mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
            verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                    true);
            mSelfRecovery.onWifiStopped();
            assertTrue(mSelfRecovery.isRecoveryInProgress());
            mSelfRecovery.onRecoveryCompleted();
            assertFalse(mSelfRecovery.isRecoveryInProgress());
            reset(mActiveModeWarden);
        }

        // Verify that further attempts to trigger restarts disable wifi
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        verify(mActiveModeWarden, never()).recoveryRestartWifi(
                SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        verify(mActiveModeWarden).recoveryDisableWifi();
        mSelfRecovery.onWifiStopped();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
        reset(mActiveModeWarden);

        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        verify(mActiveModeWarden, never()).recoveryRestartWifi(
                SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        verify(mActiveModeWarden).recoveryDisableWifi();
        mSelfRecovery.onWifiStopped();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
        reset(mActiveModeWarden);

        // Verify L.R.Watchdog can still restart things (It has its own complex limiter)
        mSelfRecovery.trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                false);
        mSelfRecovery.onWifiStopped();
        assertTrue(mSelfRecovery.isRecoveryInProgress());
        mSelfRecovery.onRecoveryCompleted();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
        reset(mActiveModeWarden);

        // Verify Sta Interface Down will still disable wifi
        mSelfRecovery.trigger(SelfRecovery.REASON_STA_IFACE_DOWN);
        verify(mActiveModeWarden).recoveryDisableWifi();
        mSelfRecovery.onWifiStopped();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
        reset(mActiveModeWarden);

        // now TRAVEL FORWARDS IN TIME and ensure that more restarts can occur
        when(mClock.getElapsedSinceBootMillis())
                .thenReturn(TimeUnit.HOURS.toMillis(1) + 1);
        mSelfRecovery.trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                false);
        mSelfRecovery.onWifiStopped();
        assertTrue(mSelfRecovery.isRecoveryInProgress());
        mSelfRecovery.onRecoveryCompleted();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
        reset(mActiveModeWarden);

        when(mClock.getElapsedSinceBootMillis())
                .thenReturn(TimeUnit.HOURS.toMillis(1) + 1);
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        reset(mActiveModeWarden);
    }

    /**
     * Verifies that invocations of {@link SelfRecovery#trigger(int)} for REASON_WIFI_NATIVE
     * does not trigger recovery if {@link R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour}
     * is set to 0
     */
    @Test
    public void testTimeWindowLimiting_NativeFailureOff() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        mResources.setInteger(R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour, 0);
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        verify(mActiveModeWarden, never()).recoveryRestartWifi(
                SelfRecovery.REASON_WIFINATIVE_FAILURE,
                true);
        verify(mActiveModeWarden).recoveryDisableWifi();
        mSelfRecovery.onWifiStopped();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
        reset(mActiveModeWarden);

        // Verify L.R.Watchdog can still restart things (It has its own complex limiter)
        mSelfRecovery.trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                false);
    }

    /**
     * Verifies that invocations of {@link SelfRecovery#trigger(int)} for
     * REASON_LAST_RESORT_WATCHDOG are NOT limited to
     * {{@link R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour} in a 1 hour time window.
     */
    @Test
    public void testTimeWindowLimiting_lastResortWatchdog_noEffect() {
        for (int i = 0; i < DEFAULT_MAX_RECOVERY_PER_HOUR * 2; i++) {
            // Verify L.R.Watchdog can still restart things (It has it's own complex limiter)
            mSelfRecovery.trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
            verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_LAST_RESORT_WATCHDOG,
                    false);
            mSelfRecovery.onWifiStopped();
            assertTrue(mSelfRecovery.isRecoveryInProgress());
            mSelfRecovery.onRecoveryCompleted();
            assertFalse(mSelfRecovery.isRecoveryInProgress());
            reset(mActiveModeWarden);
        }
    }

    /**
     * Verifies that invocations of {@link SelfRecovery#trigger(int)} for
     * REASON_STA_IFACE_DOWN are NOT limited to
     * {{@link R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour} in a 1 hour time window.
     */
    @Test
    public void testTimeWindowLimiting_staIfaceDown_noEffect() {
        for (int i = 0; i < DEFAULT_MAX_RECOVERY_PER_HOUR * 2; i++) {
            mSelfRecovery.trigger(SelfRecovery.REASON_STA_IFACE_DOWN);
            verify(mActiveModeWarden).recoveryDisableWifi();
            verify(mActiveModeWarden, never()).recoveryRestartWifi(
                    SelfRecovery.REASON_STA_IFACE_DOWN,
                    true);
            mSelfRecovery.onWifiStopped();
            assertFalse(mSelfRecovery.isRecoveryInProgress());
            reset(mActiveModeWarden);
        }
    }

    /**
     * Verifies an unsolicited system restart received by system restart listener.
     */
    @Test
    public void testUnsolicitedSystemRestart() {
        mRestartListenerCaptor.getValue().onSubsystemRestart();
        ExtendedMockito.verify(() -> WifiStatsLog.write(eq(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED),
                eq(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_SUBSYSTEM_RESTART),
                eq(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_STARTED_INTERNAL_RECOVERY_BY_NATIVE_CALLBACK),
                anyLong()));
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_SUBSYSTEM_RESTART,
                true);
        assertTrue(mSelfRecovery.isRecoveryInProgress());
        mSelfRecovery.onWifiStopped();
        assertTrue(mSelfRecovery.isRecoveryInProgress());
        mSelfRecovery.onRecoveryCompleted();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
    }

    /**
     * Verifies a system restart when WifiNative#startSubsystemRestart failed.
     */
    @Test
    public void testWifiNativeStartSubsystemRestartFailed() {
        doAnswer((invocation) -> {
            return false;
        }).when(mWifiNative).startSubsystemRestart();
        mSelfRecovery.trigger(SelfRecovery.REASON_API_CALL);
        verify(mActiveModeWarden).recoveryRestartWifi(SelfRecovery.REASON_API_CALL,
                false);
        assertTrue(mSelfRecovery.isRecoveryInProgress());
        mSelfRecovery.onWifiStopped();
        assertTrue(mSelfRecovery.isRecoveryInProgress());
        mSelfRecovery.onRecoveryCompleted();
        assertFalse(mSelfRecovery.isRecoveryInProgress());
    }

    /**
     * Verifies that by default, recovery on added interface is disabled.
     */
    @Test
    public void testStaIfaceAddedNoAction() {
        mSelfRecovery.trigger(SelfRecovery.REASON_IFACE_ADDED);
        verify(mWifiNative, never()).startSubsystemRestart();
    }

    /**
     * Verifies that when the self recovery on interface added flag is enabled, an interface added
     * event will trigger recovery.
     */
    @Test
    public void testStaIfaceAddedTriggersSelfRecovery() {
        when(mWifiGlobals.isWifiInterfaceAddedSelfRecoveryEnabled()).thenReturn(true);
        mSelfRecovery.trigger(SelfRecovery.REASON_IFACE_ADDED);
        verify(mWifiNative).startSubsystemRestart();
    }

    @Test
    public void testSelfRecoveryTriggeredMetrics() {
        mSelfRecovery.trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_SUBSYSTEM_RESTART,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_INVALID_REASON,
                -1L));

        when(mClock.getWallClockMillis()).thenReturn(10000L);
        mSelfRecovery.trigger(SelfRecovery.REASON_STA_IFACE_DOWN);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_STA_IFACE_DOWN,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_IFACE_DOWN,
                10000L));

        when(mClock.getWallClockMillis()).thenReturn(20000L);
        when(mWifiGlobals.isWifiInterfaceAddedSelfRecoveryEnabled()).thenReturn(false);
        mSelfRecovery.trigger(SelfRecovery.REASON_IFACE_ADDED);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_IFACE_ADDED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_IFACE_ADD_DISABLED,
                10000L));

        when(mClock.getWallClockMillis()).thenReturn(40000L);
        mResources.setInteger(R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour, 0);
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_WIFINATIVE_FAILURE,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RETRY_DISABLED,
                20000L));

        when(mClock.getWallClockMillis()).thenReturn(70000L);
        mResources.setInteger(R.integer.config_wifiMaxNativeFailureSelfRecoveryPerHour, 1);
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_WIFINATIVE_FAILURE,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_SUCCESS,
                30000L));
        when(mClock.getWallClockMillis()).thenReturn(80000L);
        mSelfRecovery.trigger(SelfRecovery.REASON_WIFINATIVE_FAILURE);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_WIFINATIVE_FAILURE,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_ABOVE_MAX_RETRY,
                10000L));

        when(mClock.getWallClockMillis()).thenReturn(100000L);
        doAnswer((invocation) -> {
            return false;
        }).when(mWifiNative).startSubsystemRestart();
        mSelfRecovery.trigger(SelfRecovery.REASON_API_CALL);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_API_CALL,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_STARTED_INTERNAL_RECOVERY,
                20000L));

        when(mClock.getWallClockMillis()).thenReturn(160000L);
        doAnswer((invocation) -> {
            mRestartListenerCaptor.getValue().onSubsystemRestart();
            return true;
        }).when(mWifiNative).startSubsystemRestart();
        mSelfRecovery.trigger(SelfRecovery.REASON_LAST_RESORT_WATCHDOG);
        ExtendedMockito.verify(() -> WifiStatsLog.write(WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__REASON__REASON_LAST_RESORT_WDOG,
                WifiStatsLog.WIFI_SELF_RECOVERY_TRIGGERED__RESULT__RES_RESTART_SUCCESS,
                60000L));
    }
}
