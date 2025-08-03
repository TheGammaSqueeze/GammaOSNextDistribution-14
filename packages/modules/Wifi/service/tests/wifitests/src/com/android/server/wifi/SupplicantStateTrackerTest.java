/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.BatteryStatsManager;
import android.os.Message;
import android.os.UserHandle;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ClientModeManagerBroadcastQueue.QueuedBroadcast;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link SupplicantStateTracker}.
 */
@SmallTest
public class SupplicantStateTrackerTest extends WifiBaseTest {

    private static final String TAG = "SupplicantStateTrackerTest";
    private static final String SSID = "\"GoogleGuest\"";
    private static final WifiSsid WIFI_SSID = WifiSsid.fromUtf8Text(SSID);
    private static final String BSSID = "01:02:03:04:05:06";
    private static final String TEST_IFACE = "wlan_test";

    private @Mock WifiConfigManager mWcm;
    private @Mock Context mContext;
    private @Mock BatteryStatsManager mBatteryStats;
    private @Mock WifiMonitor mWifiMonitor;
    private @Mock ClientModeManager mClientModeManager;
    private @Mock ClientModeManagerBroadcastQueue mBroadcastQueue;
    private SupplicantStateTracker mSupplicantStateTracker;
    private TestLooper mLooper;

    private @Captor ArgumentCaptor<Intent> mIntentCaptor;
    private @Captor ArgumentCaptor<QueuedBroadcast> mQueuedBroadcastCaptor;

    private Message getSupplicantStateChangeMessage(int networkId, WifiSsid wifiSsid,
            String bssid, SupplicantState newSupplicantState) {
        return Message.obtain(null, WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(networkId, wifiSsid, bssid, 0, newSupplicantState));
    }

    @Before
    public void setUp() {
        mLooper = new TestLooper();
        MockitoAnnotations.initMocks(this);
        mSupplicantStateTracker = new SupplicantStateTracker(mContext, mWcm, mBatteryStats,
                mLooper.getLooper(), mWifiMonitor, TEST_IFACE, mClientModeManager, mBroadcastQueue);

        verify(mWifiMonitor, atLeastOnce()).registerHandler(eq(TEST_IFACE), anyInt(), any());
    }

    @After
    public void tearDown() {
        mSupplicantStateTracker.stop();
        verify(mWifiMonitor, atLeastOnce()).deregisterHandler(eq(TEST_IFACE), anyInt(), any());
    }

    /**
     * This test verifies that the SupplicantStateTracker sends a broadcast intent upon receiving
     * a message when supplicant state changes
     */
    @Test
    public void testSupplicantStateChangeIntent() {
        mSupplicantStateTracker.sendMessage(getSupplicantStateChangeMessage(0, WIFI_SSID,
                BSSID, SupplicantState.SCANNING));
        mLooper.dispatchAll();

        verify(mBroadcastQueue).queueOrSendBroadcast(
                eq(mClientModeManager), mQueuedBroadcastCaptor.capture());
        mQueuedBroadcastCaptor.getValue().send();

        verify(mContext).sendStickyBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        assertThat(intent.<SupplicantState>getParcelableExtra(WifiManager.EXTRA_NEW_STATE))
                .isEqualTo(SupplicantState.SCANNING);
    }

    /**
     * This test verifies that the current auth status is sent in the Broadcast intent
     */
    @Test
    public void testAuthPassInSupplicantStateChangeIntent() {
        mSupplicantStateTracker.sendMessage(getSupplicantStateChangeMessage(0, WIFI_SSID,
                BSSID, SupplicantState.AUTHENTICATING));
        mSupplicantStateTracker.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT);
        mLooper.dispatchAll();

        verify(mBroadcastQueue).queueOrSendBroadcast(
                eq(mClientModeManager), mQueuedBroadcastCaptor.capture());
        mQueuedBroadcastCaptor.getValue().send();

        verify(mContext).sendStickyBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        assertThat(intent.<SupplicantState>getParcelableExtra(WifiManager.EXTRA_NEW_STATE))
                .isEqualTo(SupplicantState.AUTHENTICATING);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0)).isEqualTo(0);
    }

    /**
     * This test verifies that the current auth status is sent in the Broadcast intent
     */
    @Test
    public void testAuthFailedInSupplicantStateChangeIntent() {
        mSupplicantStateTracker.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT);
        mSupplicantStateTracker.sendMessage(getSupplicantStateChangeMessage(0, WIFI_SSID,
                BSSID, SupplicantState.AUTHENTICATING));
        mLooper.dispatchAll();

        verify(mBroadcastQueue).queueOrSendBroadcast(
                eq(mClientModeManager), mQueuedBroadcastCaptor.capture());
        mQueuedBroadcastCaptor.getValue().send();

        verify(mContext).sendStickyBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        assertThat(intent.<SupplicantState>getParcelableExtra(WifiManager.EXTRA_NEW_STATE))
                .isEqualTo(SupplicantState.AUTHENTICATING);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0))
                .isEqualTo(WifiManager.ERROR_AUTHENTICATING);
    }

    /**
     * This test verifies the correct reasonCode for auth failure is sent in Broadcast
     * intent.
     */
    @Test
    public void testReasonCodeInSupplicantStateChangeIntent() {
        mSupplicantStateTracker.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
                WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1);
        mSupplicantStateTracker.sendMessage(getSupplicantStateChangeMessage(0, WIFI_SSID,
                BSSID, SupplicantState.AUTHENTICATING));
        mLooper.dispatchAll();

        verify(mBroadcastQueue).queueOrSendBroadcast(
                eq(mClientModeManager), mQueuedBroadcastCaptor.capture());
        mQueuedBroadcastCaptor.getValue().send();

        verify(mContext).sendStickyBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        assertThat(intent.<SupplicantState>getParcelableExtra(WifiManager.EXTRA_NEW_STATE))
                .isEqualTo(SupplicantState.AUTHENTICATING);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0))
                .isEqualTo(WifiManager.ERROR_AUTHENTICATING);
        assertThat(intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR_REASON, -1))
                .isEqualTo(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD);
    }
}
