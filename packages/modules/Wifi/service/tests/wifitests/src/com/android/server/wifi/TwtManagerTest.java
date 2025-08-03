/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.AlarmManager;
import android.app.test.TestAlarmManager;
import android.net.wifi.ITwtCallback;
import android.net.wifi.ITwtCapabilitiesListener;
import android.net.wifi.ITwtStatsListener;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiTwtSession;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSession;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;

/**
 * Unit test for {@link TwtManager}
 */
@SmallTest
public class TwtManagerTest extends WifiBaseTest {
    private static final int TWT_CALLBACKS_ID_START_OFFSET = 1;
    private static final String WIFI_IFACE_NAME = "wlan0";
    private static final String WIFI_IFACE_NAME_1 = "wlan1";

    private static final int TEST_TWT_SESSION_ID = 10;
    private static final int TEST_TWT_CMD_ID = 1;

    @Mock
    Clock mClock;
    private TestAlarmManager mTestAlarmManager;
    private final TestLooper mLooper = new TestLooper();
    @Mock
    private WifiInjector mWifiInjector;
    private TwtManager mTwtManager;
    @Mock
    WifiNative mWifiNative;
    @Mock
    private ClientModeImplMonitor mCmiMonitor;
    @Captor
    private ArgumentCaptor<ClientModeImplListener> mCmiListenerCaptor;
    @Mock
    private IBinder mAppBinder;
    private Handler mHandler;
    private AlarmManager mAlarmManager;
    @Mock
    ConcreteClientModeManager mClientModeManager;
    @Captor
    private ArgumentCaptor<TwtManager.WifiNativeTwtEvents> mWifiNativeTwtEventsArgumentCaptor;

    /**
     * Test setup.
     */
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mHandler = new Handler(mLooper.getLooper());
        mTestAlarmManager = new TestAlarmManager();
        mAlarmManager = mTestAlarmManager.getAlarmManager();
        when(mWifiInjector.getAlarmManager()).thenReturn(mAlarmManager);
        mTwtManager = new TwtManager(mWifiInjector, mCmiMonitor, mWifiNative, mHandler, mClock,
                WifiTwtSession.MAX_TWT_SESSIONS, TWT_CALLBACKS_ID_START_OFFSET);
        verify(mCmiMonitor).registerListener(mCmiListenerCaptor.capture());
        mTwtManager.registerWifiNativeTwtEvents();
        verify(mWifiNative).registerTwtCallbacks(mWifiNativeTwtEventsArgumentCaptor.capture());
    }

    private Bundle getDefaultTwtCapabilities() {
        Bundle twtCapabilities = new Bundle();
        twtCapabilities.putBoolean(WifiManager.TWT_CAPABILITIES_KEY_BOOLEAN_TWT_REQUESTER, false);
        twtCapabilities.putInt(WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS, -1);
        twtCapabilities.putInt(WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS, -1);
        twtCapabilities.putLong(WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS, -1);
        twtCapabilities.putLong(WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS, -1);
        return twtCapabilities;
    }

    private Bundle getMockTwtCapabilities() {
        Bundle twtCapabilities = new Bundle();
        twtCapabilities.putBoolean(WifiManager.TWT_CAPABILITIES_KEY_BOOLEAN_TWT_REQUESTER, true);
        twtCapabilities.putInt(WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS, 100);
        twtCapabilities.putInt(WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS, 1000);
        twtCapabilities.putLong(WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS,
                1000);
        twtCapabilities.putLong(WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS,
                10000);
        return twtCapabilities;
    }

    private static Bundle getMockTwtStats() {
        Bundle twtStats = new Bundle();
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_COUNT, 200);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_COUNT, 300);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_SIZE, 400);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_SIZE, 200);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_EOSP_DURATION_MICROS, 1000);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_EOSP_COUNT, 10);
        return twtStats;
    }

    private static Bundle getDefaultTwtStats() {
        Bundle twtStats = new Bundle();
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_COUNT, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_COUNT, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_SIZE, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_SIZE, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_EOSP_DURATION_MICROS, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_EOSP_COUNT, -1);
        return twtStats;
    }

    private boolean isBundleContentEqual(Bundle expected, Bundle actual) {
        if (expected == actual) return true;
        for (String key : expected.keySet()) {
            if (!actual.containsKey(key) || !actual.get(key).equals(expected.get(key))) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testGetTwtCapabilities() throws RemoteException {
        ITwtCapabilitiesListener iTwtCapabilitiesListener = mock(ITwtCapabilitiesListener.class);
        InOrder inorder = inOrder(iTwtCapabilitiesListener);
        // Test with null interface name
        final Bundle defaultTwtCapabilities = getDefaultTwtCapabilities();
        mTwtManager.getTwtCapabilities(null, iTwtCapabilitiesListener);
        inorder.verify(iTwtCapabilitiesListener).onResult(
                argThat(argument -> isBundleContentEqual(defaultTwtCapabilities, argument)));
        // Test getTwtCapabilities when WifiNative return null
        when(mWifiNative.getTwtCapabilities(eq(WIFI_IFACE_NAME))).thenReturn(null);
        mTwtManager.getTwtCapabilities(null, iTwtCapabilitiesListener);
        inorder.verify(iTwtCapabilitiesListener).onResult(
                argThat(argument -> isBundleContentEqual(defaultTwtCapabilities, argument)));
        // Test getTwtCapabilities
        final Bundle mockTwtCapabilities = getMockTwtCapabilities();
        when(mWifiNative.getTwtCapabilities(eq(WIFI_IFACE_NAME))).thenReturn(mockTwtCapabilities);
        mTwtManager.getTwtCapabilities(WIFI_IFACE_NAME, iTwtCapabilitiesListener);
        inorder.verify(iTwtCapabilitiesListener).onResult(
                argThat(argument -> isBundleContentEqual(mockTwtCapabilities, argument)));
    }

    @Test
    public void testSetupTwtSession() throws RemoteException {
        ITwtCallback iTwtCallback = mock(ITwtCallback.class);
        TwtRequest twtRequest = mock(TwtRequest.class);
        when(iTwtCallback.asBinder()).thenReturn(mAppBinder);
        InOrder inOrderCallback = inOrder(iTwtCallback);
        InOrder inOrderBinder = inOrder(mAppBinder);
        InOrder inOrderAlarm = inOrder(mAlarmManager);
        // Test with null interface
        mTwtManager.setupTwtSession(null, twtRequest, iTwtCallback, Binder.getCallingUid());
        inOrderCallback.verify(iTwtCallback).onFailure(
                TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        // Test when wifiNative.setupTwtSession return false
        when(mWifiNative.setupTwtSession(eq(1), eq(WIFI_IFACE_NAME), eq(twtRequest))).thenReturn(
                false);
        mTwtManager.setupTwtSession(WIFI_IFACE_NAME, twtRequest, iTwtCallback,
                Binder.getCallingUid());
        inOrderBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                anyString(), any(AlarmManager.OnAlarmListener.class), eq(mHandler));
        inOrderBinder.verify(mAppBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderCallback.verify(iTwtCallback).onFailure(
                TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        inOrderAlarm.verify(mAlarmManager).cancel(any(AlarmManager.OnAlarmListener.class));
        // Test when wifiNative.setupTwtSession return true
        when(mWifiNative.setupTwtSession(eq(1), eq(WIFI_IFACE_NAME), eq(twtRequest))).thenReturn(
                true);
        mTwtManager.setupTwtSession(WIFI_IFACE_NAME, twtRequest, iTwtCallback,
                Binder.getCallingUid());
        inOrderBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                anyString(), any(AlarmManager.OnAlarmListener.class), eq(mHandler));
    }

    @Test
    public void testTeardownTwtSession() throws RemoteException {
        ITwtCallback iTwtCallback = mock(ITwtCallback.class);
        TwtRequest twtRequest = mock(TwtRequest.class);
        when(iTwtCallback.asBinder()).thenReturn(mAppBinder);
        InOrder inOrderCallback = inOrder(iTwtCallback);
        InOrder inOrderBinder = inOrder(mAppBinder);
        InOrder inOrderAlarm = inOrder(mAlarmManager);
        when(mWifiNative.tearDownTwtSession(eq(TEST_TWT_CMD_ID), eq(WIFI_IFACE_NAME),
                eq(TEST_TWT_SESSION_ID))).thenReturn(true);
        when(mWifiNative.setupTwtSession(eq(TEST_TWT_CMD_ID), eq(WIFI_IFACE_NAME),
                eq(twtRequest))).thenReturn(true);
        // Test when session is not setup
        mTwtManager.tearDownTwtSession(WIFI_IFACE_NAME, TEST_TWT_SESSION_ID);
        inOrderCallback.verifyNoMoreInteractions();
        inOrderBinder.verifyNoMoreInteractions();
        inOrderAlarm.verifyNoMoreInteractions();
        // Make a session
        mTwtManager.setupTwtSession(WIFI_IFACE_NAME, twtRequest, iTwtCallback,
                Binder.getCallingUid());
        inOrderBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                anyString(), any(AlarmManager.OnAlarmListener.class), eq(mHandler));
        mWifiNativeTwtEventsArgumentCaptor.getValue().onTwtSessionCreate(TEST_TWT_CMD_ID, 100, 1000,
                1, TEST_TWT_SESSION_ID);
        inOrderCallback.verify(iTwtCallback).onCreate(eq(100), eq(1000L), eq(1),
                eq(Binder.getCallingUid()), eq(TEST_TWT_SESSION_ID));
        // Test teardown with null interface
        mTwtManager.tearDownTwtSession(null, TEST_TWT_SESSION_ID);
        inOrderCallback.verify(iTwtCallback).onFailure(
                TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        // Test teardown with wrong interface
        mTwtManager.tearDownTwtSession(WIFI_IFACE_NAME_1, TEST_TWT_SESSION_ID);
        inOrderCallback.verify(iTwtCallback).onFailure(
                TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        // Test teardown the session
        mTwtManager.tearDownTwtSession(WIFI_IFACE_NAME, TEST_TWT_SESSION_ID);
        inOrderBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                anyString(), any(AlarmManager.OnAlarmListener.class), eq(mHandler));
    }

    @Test
    public void testGetStatsSession() throws RemoteException {
        final Bundle defaultTwtStats = getDefaultTwtStats();
        ITwtCallback iTwtCallback = mock(ITwtCallback.class);
        ITwtStatsListener iTwtStatsListener = mock(ITwtStatsListener.class);
        TwtRequest twtRequest = mock(TwtRequest.class);
        when(iTwtStatsListener.asBinder()).thenReturn(mAppBinder);
        when(iTwtCallback.asBinder()).thenReturn(mAppBinder);
        InOrder inOrderListener = inOrder(iTwtStatsListener);
        InOrder inOrderBinder = inOrder(mAppBinder);
        InOrder inOrderAlarm = inOrder(mAlarmManager);
        when(mWifiNative.getStatsTwtSession(eq(TEST_TWT_CMD_ID), eq(WIFI_IFACE_NAME),
                eq(TEST_TWT_SESSION_ID))).thenReturn(true);
        when(mWifiNative.setupTwtSession(eq(TEST_TWT_CMD_ID), eq(WIFI_IFACE_NAME),
                eq(twtRequest))).thenReturn(true);
        // Test when interface is not registered
        mTwtManager.getStatsTwtSession(WIFI_IFACE_NAME_1, iTwtStatsListener, TEST_TWT_SESSION_ID);
        inOrderListener.verify(iTwtStatsListener).onResult(
                argThat(argument -> isBundleContentEqual(defaultTwtStats, argument)));
        // Test when session is not setup
        mTwtManager.getStatsTwtSession(WIFI_IFACE_NAME, iTwtStatsListener, TEST_TWT_SESSION_ID);
        inOrderListener.verify(iTwtStatsListener).onResult(
                argThat(argument -> isBundleContentEqual(defaultTwtStats, argument)));
        // Make a session
        mTwtManager.setupTwtSession(WIFI_IFACE_NAME, twtRequest, iTwtCallback,
                Binder.getCallingUid());
        inOrderBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                anyString(), any(AlarmManager.OnAlarmListener.class), eq(mHandler));
        mWifiNativeTwtEventsArgumentCaptor.getValue().onTwtSessionCreate(TEST_TWT_CMD_ID, 100, 1000,
                1, TEST_TWT_SESSION_ID);
        verify(iTwtCallback).onCreate(eq(100), eq(1000L), eq(1), eq(Binder.getCallingUid()),
                eq(TEST_TWT_SESSION_ID));
        // Test get stats on the session
        mTwtManager.getStatsTwtSession(WIFI_IFACE_NAME, iTwtStatsListener, TEST_TWT_SESSION_ID);
        Bundle twtStats = getMockTwtStats();
        mWifiNativeTwtEventsArgumentCaptor.getValue().onTwtSessionStats(TEST_TWT_CMD_ID,
                TEST_TWT_SESSION_ID, twtStats);
        inOrderListener.verify(iTwtStatsListener).onResult(
                argThat(argument -> isBundleContentEqual(twtStats, argument)));
    }

    @Test
    public void testDisconnect() throws RemoteException {
        ITwtCallback iTwtCallback = mock(ITwtCallback.class);
        TwtRequest twtRequest = mock(TwtRequest.class);
        when(iTwtCallback.asBinder()).thenReturn(mAppBinder);
        InOrder inOrderCallback = inOrder(iTwtCallback);
        InOrder inOrderBinder = inOrder(mAppBinder);
        InOrder inOrderAlarm = inOrder(mAlarmManager);
        when(mClientModeManager.getInterfaceName()).thenReturn("wlan0");
        when(mClientModeManager.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        // Setup TWT session
        when(mWifiNative.setupTwtSession(eq(1), eq(WIFI_IFACE_NAME), eq(twtRequest))).thenReturn(
                true);
        mTwtManager.setupTwtSession(WIFI_IFACE_NAME, twtRequest, iTwtCallback,
                Binder.getCallingUid());
        inOrderBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                anyString(), any(AlarmManager.OnAlarmListener.class), eq(mHandler));
        // Disconnect and check the cleanup
        mCmiListenerCaptor.getValue().onConnectionEnd(mClientModeManager);
        inOrderCallback.verify(iTwtCallback).onFailure(TwtSessionCallback.TWT_ERROR_CODE_FAIL);
        inOrderBinder.verify(mAppBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        inOrderAlarm.verify(mAlarmManager).cancel(any(AlarmManager.OnAlarmListener.class));
    }
}
