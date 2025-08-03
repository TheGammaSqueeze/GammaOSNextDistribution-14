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

package com.android.server.wifi;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.WorkSource;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ActiveModeWarden.ModeChangeCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link MultiInternetManager}. */
@SmallTest
public class MultiInternetManagerTest extends WifiBaseTest {
    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private Context mContext;
    @Mock private ClientModeImplMonitor mCmiMonitor;
    @Mock private Clock mClock;
    @Mock private ConcreteClientModeManager mPrimaryCmm;
    @Mock private ConcreteClientModeManager mSecondaryCmm;
    @Mock private WifiSettingsStore mSettingsStore;
    @Mock private MultiInternetManager.ConnectionStatusListener mConnectionStatusListener;
    @Mock WorkSource mWorksource;
    @Captor private ArgumentCaptor<ModeChangeCallback> mModeChangeCallbackCaptor;
    @Captor private ArgumentCaptor<ClientModeImplListener> mCmiListenerCaptor;

    private static final int TEST_NETWORK_ID1 = 5;
    private static final int TEST_NETWORK_ID2 = 6;
    private static final String TEST_SSID1 = "Test123";
    private static final String TEST_SSID2 = "Test456";
    private static final String TEST_BSSID1 = "12:12:12:12:12:12";
    private static final String TEST_BSSID2 = "22:22:22:22:22:22";
    private static final String TEST_BSSID3 = "33:332:33:33:33:332";
    private static final int TEST_FREQUENCY1 = 2412;
    private static final int TEST_FREQUENCY2 = 5262;
    private static final int TEST_UID = 4556;
    private static final int TEST_UID2 = 4567;
    private static final int SCAN_INTERVAL = 10_000;
    private static final String TEST_PACKAGE_NAME = "com.test";
    private static final WorkSource TEST_WORKSOURCE = new WorkSource(TEST_UID, TEST_PACKAGE_NAME);
    private static final String TEST_PACKAGE_NAME2 = "com.test2";
    private static final WorkSource TEST_WORKSOURCE2 = new WorkSource(TEST_UID2,
            TEST_PACKAGE_NAME2);
    private TestLooper mLooper;
    private TestHandler mTestHandler;
    private MultiInternetManager mMultiInternetManager;
    private WifiInfo mPrimaryInfo = new WifiInfo();
    private WifiInfo mSecondaryInfo = new WifiInfo();

    /**
    * A test Handler that stores one single incoming Message with delayed time internally, to be
    * able to manually triggered by calling {@link #timeAdvance}. Only one delayed message can be
    * scheduled at a time. The scheduled delayed message intervals are recorded and returned by
    * {@link #getIntervals}. The intervals are cleared by calling {@link #reset}.
    */
    private class TestHandler extends Handler {
        private ArrayList<Long> mIntervals = new ArrayList<>();
        private Message mMessage;

        TestHandler(Looper looper) {
            super(looper);
        }

        public List<Long> getIntervals() {
            return mIntervals;
        }

        public void reset() {
            mIntervals.clear();
        }

        public void timeAdvance() {
            if (mMessage != null) {
                // Dispatch the message without waiting.
                super.dispatchMessage(mMessage);
            }
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            // uptimeMillis is an absolute time obtained as SystemClock.uptimeMillis() + delay
            // in Handler and can't be replaced with customized clock.
            // if custom clock is given, recalculate the time with regards to it
            long delayMs = uptimeMillis - SystemClock.uptimeMillis();
            if (delayMs > 0) {
                mIntervals.add(delayMs);
                mMessage = msg;
            }
            uptimeMillis = delayMs + mClock.getElapsedSinceBootMillis();
            // Message is still queued to super, so it doesn't get filtered out and rely on the
            // timeAdvance() to dispatch. timeAdvance() can force time to advance and send the
            // message immediately. If it is not called not the message can still be dispatched
            // at the time the message is scheduled.
            return super.sendMessageAtTime(msg, uptimeMillis);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPrimaryInfo.setSSID(WifiSsid.fromUtf8Text(TEST_SSID1));
        mPrimaryInfo.setBSSID(TEST_BSSID1);
        mPrimaryInfo.setNetworkId(TEST_NETWORK_ID1);
        mPrimaryInfo.setFrequency(TEST_FREQUENCY1);
        mPrimaryInfo.setIsPrimary(true);

        mSecondaryInfo.setSSID(WifiSsid.fromUtf8Text(TEST_SSID2));
        mSecondaryInfo.setBSSID(TEST_BSSID2);
        mSecondaryInfo.setNetworkId(TEST_NETWORK_ID2);
        mSecondaryInfo.setFrequency(TEST_FREQUENCY2);
        mSecondaryInfo.setIsPrimary(false);

        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()).thenReturn(true);

        when(mPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mPrimaryCmm.getConnectionInfo()).thenReturn(null);
        when(mSecondaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(mSecondaryCmm.getConnectionInfo()).thenReturn(mock(WifiInfo.class));
        when(mSecondaryCmm.isSecondaryInternet()).thenReturn(true);

        when(mActiveModeWarden.getPrimaryClientModeManagerNullable()).thenReturn(mPrimaryCmm);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(List.of(mSecondaryCmm));
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers()).thenReturn(
                List.of(mPrimaryCmm, mSecondaryCmm));
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_PRIMARY))
                .thenReturn(List.of(mPrimaryCmm));
        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_LONG_LIVED))
                .thenReturn(mSecondaryCmm);

        mLooper = new TestLooper(mClock::getElapsedSinceBootMillis);
        mTestHandler = new TestHandler(mLooper.getLooper());

        mMultiInternetManager = new MultiInternetManager(mActiveModeWarden, mFrameworkFacade,
                mContext, mCmiMonitor, mSettingsStore, mTestHandler, mClock);
        mMultiInternetManager.setVerboseLoggingEnabled(true);

        verify(mActiveModeWarden).registerModeChangeCallback(mModeChangeCallbackCaptor.capture());
        verify(mCmiMonitor).registerListener(mCmiListenerCaptor.capture());
        mMultiInternetManager.setConnectionStatusListener(mConnectionStatusListener);
    }

    private void fakePrimaryCmmConnected(boolean isConnected) {
        when(mPrimaryCmm.getConnectionInfo()).thenReturn(isConnected ? mPrimaryInfo : null);
        when(mPrimaryCmm.isConnected()).thenReturn(isConnected);
    }

    private void fakeSecondaryCmmConnected(boolean isConnected) {
        when(mSecondaryCmm.getConnectionInfo()).thenReturn(
                isConnected ? mSecondaryInfo : null);
        when(mSecondaryCmm.isConnected()).thenReturn(isConnected);
        when(mSecondaryCmm.getConnectedBssid()).thenReturn(
                isConnected ? mSecondaryInfo.getBSSID() : null);
    }

    @Test
    public void testMultiInternetFeatureDisabledNoOp() {
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()).thenReturn(false);

        mCmiListenerCaptor.getValue().onInternetValidated(mSecondaryCmm);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mSecondaryCmm);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mSecondaryCmm);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSecondaryCmm);

        verify(mSecondaryCmm, never()).getConnectionInfo();
        assertEquals(0, mMultiInternetManager.getNetworkConnectionState().size());
    }

    @Test
    public void testSetMultiInternetStateWhenFeatureDisabled() {
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()).thenReturn(false);
        assertFalse(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        verify(mSettingsStore, never()).handleWifiMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        assertEquals(0, mMultiInternetManager.getNetworkConnectionState().size());
    }

    @Test
    public void testSetMultiInternetStateWhenFeatureEnabled() {
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());

        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP));
        assertFalse(mMultiInternetManager.isStaConcurrencyForMultiInternetMultiApAllowed());
        assertTrue(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled());
        verify(mSettingsStore).handleWifiMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP);
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());

        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        assertTrue(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled());
        assertTrue(mMultiInternetManager.isStaConcurrencyForMultiInternetMultiApAllowed());
        verify(mSettingsStore).handleWifiMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        assertEquals(0, mMultiInternetManager.getNetworkConnectionState().size());
    }

    @Test
    public void testSetMultiInternetConnectionWorksource() {
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        verify(mSettingsStore).handleWifiMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        assertTrue(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled());

        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        assertTrue(mMultiInternetManager.getNetworkConnectionState()
                .contains(ScanResult.WIFI_BAND_5_GHZ));
        verify(mConnectionStatusListener).onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED, TEST_WORKSOURCE);
        verify(mConnectionStatusListener).onStartScan(TEST_WORKSOURCE);
        assertEquals(0, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testSetMultiInternetConnectionWorksourceOnTwoBands() {
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        verify(mSettingsStore).handleWifiMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP);
        assertEquals(WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP,
                mMultiInternetManager.getStaConcurrencyForMultiInternetMode());
        assertTrue(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled());

        // Set for 2.4G
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                null, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        assertTrue(mMultiInternetManager.getNetworkConnectionState()
                .contains(ScanResult.WIFI_BAND_24_GHZ));
        verify(mConnectionStatusListener).onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED, TEST_WORKSOURCE);
        verify(mConnectionStatusListener).onStartScan(TEST_WORKSOURCE);
        // Set for 5G
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.getNetworkConnectionState()
                .contains(ScanResult.WIFI_BAND_5_GHZ));
        verify(mConnectionStatusListener).onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED, TEST_WORKSOURCE);
        verify(mConnectionStatusListener, times(2)).onStartScan(TEST_WORKSOURCE);
        // Clear the WorkSource
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                null, null);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, null);
        assertFalse(mMultiInternetManager.hasPendingConnectionRequests());
        assertEquals(0, mMultiInternetManager.getNetworkConnectionState().size());
        assertEquals(0, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testOnInternetValidatedSecondaryCmmConnection() {
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                TEST_BSSID2, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        fakePrimaryCmmConnected(true);
        fakeSecondaryCmmConnected(true);
        mCmiListenerCaptor.getValue().onInternetValidated(mSecondaryCmm);
        assertFalse(mMultiInternetManager.hasPendingConnectionRequests());
        assertTrue(mMultiInternetManager.getNetworkConnectionState()
                .contains(ScanResult.WIFI_BAND_5_GHZ));
        assertTrue(mMultiInternetManager.getNetworkConnectionState()
                .get(ScanResult.WIFI_BAND_5_GHZ).isValidated());
        assertEquals(1, mMultiInternetManager.getSpecifiedBssids().size());

        // Add another request with the same band and BSSID specified and verify no disconnect
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                TEST_BSSID2, TEST_WORKSOURCE);
        verify(mSecondaryCmm, never()).disconnect();

        // Add another request with a different band and BSSID specified and verify no disconnect
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                TEST_BSSID2, TEST_WORKSOURCE);
        verify(mSecondaryCmm, never()).disconnect();

        // Add another request with the same band but different BSSID and verify disconnect
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                TEST_BSSID3, TEST_WORKSOURCE);
        verify(mSecondaryCmm).disconnect();
        assertEquals(2, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testOnL3ConnectedSecondaryCmmConnection() {
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                null, TEST_WORKSOURCE);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        fakePrimaryCmmConnected(true);
        fakeSecondaryCmmConnected(true);
        mCmiListenerCaptor.getValue().onL3Connected(mSecondaryCmm);
        assertFalse(mMultiInternetManager.hasPendingConnectionRequests());
        assertTrue(mMultiInternetManager.getNetworkConnectionState()
                .get(ScanResult.WIFI_BAND_5_GHZ).isConnected());
        assertEquals(0, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testOnConnectionEndPrimaryCmmConnection() {
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                null, TEST_WORKSOURCE);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        fakePrimaryCmmConnected(true);
        fakeSecondaryCmmConnected(true);
        mCmiListenerCaptor.getValue().onL3Connected(mSecondaryCmm);
        assertFalse(mMultiInternetManager.hasPendingConnectionRequests());
        // Primary disconnected
        fakePrimaryCmmConnected(false);
        mCmiListenerCaptor.getValue().onConnectionEnd(mPrimaryCmm);
        verify(mSecondaryCmm).disconnect();
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        assertEquals(0, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testStartConnectivityScan() {
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        // Primary is 2G, fake 2G is connected.
        fakePrimaryCmmConnected(true);
        long currentTimeStamp = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                null, TEST_WORKSOURCE);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, TEST_WORKSOURCE2);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        for (int i = 0; i < 5; i++) {
            currentTimeStamp += SCAN_INTERVAL;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            mTestHandler.timeAdvance();
        }
        verify(mConnectionStatusListener, times(1)).onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED, TEST_WORKSOURCE2);
        verify(mConnectionStatusListener, times(1)).onStartScan(TEST_WORKSOURCE2);
        assertEquals(0, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testNotifyBssidAssociatedEvent() {
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                null, TEST_WORKSOURCE);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                null, TEST_WORKSOURCE);
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        fakePrimaryCmmConnected(true);
        fakeSecondaryCmmConnected(true);
        mCmiListenerCaptor.getValue().onL3Connected(mSecondaryCmm);
        assertFalse(mMultiInternetManager.hasPendingConnectionRequests());
        // Primary roamed to same frequency as secondary
        mPrimaryInfo.setBSSID(TEST_BSSID3);
        mPrimaryInfo.setFrequency(TEST_FREQUENCY2);
        when(mPrimaryCmm.getConnectionInfo()).thenReturn(mPrimaryInfo);
        mMultiInternetManager.notifyBssidAssociatedEvent(mPrimaryCmm);
        verify(mSecondaryCmm).disconnect();
        assertTrue(mMultiInternetManager.hasPendingConnectionRequests());
        assertEquals(0, mMultiInternetManager.getSpecifiedBssids().size());
    }

    @Test
    public void testGetSpecifiedBssids() {
        assertTrue(mMultiInternetManager.setStaConcurrencyForMultiInternetMode(
                WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP));
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_24_GHZ,
                TEST_BSSID1, TEST_WORKSOURCE);
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                TEST_BSSID2, TEST_WORKSOURCE);
        Map<Integer, String> specifiedBssids = mMultiInternetManager.getSpecifiedBssids();
        assertEquals(2, specifiedBssids.size());
        assertEquals(TEST_BSSID1, specifiedBssids.get(ScanResult.WIFI_BAND_24_GHZ));
        assertEquals(TEST_BSSID2, specifiedBssids.get(ScanResult.WIFI_BAND_5_GHZ));

        // verify overriding existing BSSID mapping
        mMultiInternetManager.setMultiInternetConnectionWorksource(ScanResult.WIFI_BAND_5_GHZ,
                TEST_BSSID3, TEST_WORKSOURCE);
        specifiedBssids = mMultiInternetManager.getSpecifiedBssids();
        assertEquals(2, specifiedBssids.size());
        assertEquals(TEST_BSSID1, specifiedBssids.get(ScanResult.WIFI_BAND_24_GHZ));
        assertEquals(TEST_BSSID3, specifiedBssids.get(ScanResult.WIFI_BAND_5_GHZ));
    }
}
