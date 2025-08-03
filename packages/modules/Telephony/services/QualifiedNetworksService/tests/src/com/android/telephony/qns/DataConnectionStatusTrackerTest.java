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

package com.android.telephony.qns;

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.DataFailCause.IWLAN_NO_APN_SUBSCRIPTION;
import static android.telephony.DataFailCause.MISSING_UNKNOWN_APN;

import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_FAILED;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_STARTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_CONNECTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_CONNECTING;
import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_HANDOVER;
import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_INACTIVE;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.CarrierConfigManager;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import androidx.test.core.app.ApplicationProvider;

import com.android.telephony.qns.DataConnectionStatusTracker.DataConnectionChangedInfo;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class DataConnectionStatusTrackerTest extends QnsTest {
    @Mock private Context mContext;
    @Mock protected TelephonyManager mMockTelephonyManager;
    @Mock protected CarrierConfigManager mMockCarrierConfigManager;
    @Mock protected ConnectivityManager mMockConnectivityManager;
    @Mock protected SubscriptionManager mMockSubscriptionManager;
    @Mock protected WifiManager mMockWifiManager;
    @Mock protected Resources mResources;
    protected DataConnectionStatusTracker mDataConnectionStatusTracker;
    private static final int EVENT_DATA_CONNECTION_STATE_CHANGED = 1;
    DataConnectionChangedInfo mDcStatus;

    private boolean mReady = false;
    private Object mLock = new Object();

    HandlerThread mHandlerThread =
            new HandlerThread("") {
                @Override
                protected void onLooperPrepared() {
                    super.onLooperPrepared();
                    mDataConnectionStatusTracker =
                            new DataConnectionStatusTracker(
                                    mMockQnsTelephonyListener,
                                    this.getLooper(),
                                    0,
                                    NetworkCapabilities.NET_CAPABILITY_IMS);
                    setReady(true);
                }
            };

    Handler mHandler =
            new Handler(Looper.getMainLooper()) {
                public void handleMessage(Message message) {
                    QnsAsyncResult ar;
                    switch (message.what) {
                        case EVENT_DATA_CONNECTION_STATE_CHANGED:
                            ar = (QnsAsyncResult) message.obj;
                            mDcStatus = (DataConnectionChangedInfo) ar.mResult;
                            setReady(true);
                            break;
                    }
                }
            };

    protected void waitUntilReady() {
        synchronized (mLock) {
            if (!mReady) {
                try {
                    mLock.wait(10000);
                } catch (InterruptedException e) {
                }
                if (!mReady) {
                    Assert.fail("Test is not ready!!");
                }
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mMockTelephonyManager);
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mMockCarrierConfigManager);
        when(mContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mMockWifiManager);
        when(mContext.getResources()).thenReturn(mResources);
        mHandlerThread.start();
        waitUntilReady();
        mDataConnectionStatusTracker.registerDataConnectionStatusChanged(
                mHandler, EVENT_DATA_CONNECTION_STATE_CHANGED);
    }

    @Test
    public void testOnCellular() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_STARTED);
        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_DISCONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_INACTIVE, TRANSPORT_TYPE_INVALID, EVENT_DATA_CONNECTION_DISCONNECTED);
    }

    @Test
    public void testCellularConnectingToDisconnect() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_STARTED);
        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_DISCONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_INACTIVE, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_FAILED);
    }

    @Test
    public void testOnIwlan() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_DISCONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_INACTIVE, TRANSPORT_TYPE_INVALID, EVENT_DATA_CONNECTION_DISCONNECTED);
    }

    @Test
    public void testIwlanConnectingToDisconnect() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_DISCONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_INACTIVE, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_FAILED);
    }

    @Test
    public void testOnCellularHandover() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_HANDOVER, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_HANDOVER_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_HANDOVER_SUCCESS);
    }

    @Test
    public void testHandoverOnNonSrcTransportType() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        Assert.assertFalse(mDataConnectionStatusTracker.isHandoverState());
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testOnCellularHandoverSuspended() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_HANDOVER, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_HANDOVER_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_SUSPENDED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_HANDOVER_SUCCESS);
    }

    @Test
    public void testOnCellularHandoverFail() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_HANDOVER, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_HANDOVER_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_HANDOVER_FAILED);
    }

    @Test
    public void testOnCellularHandoverFailWithCause() {

        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);

        loadPrecisionDataConnectionStateWithFailCause(
                TRANSPORT_TYPE_WWAN,
                IWLAN_NO_APN_SUBSCRIPTION,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        validateHandoverFailCause(IWLAN_NO_APN_SUBSCRIPTION);
    }

    @Test
    public void testOnIwlanWithApnSetting() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionStateWithApnSetting(
                TRANSPORT_TYPE_WLAN,
                NetworkCapabilities.NET_CAPABILITY_IMS,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionStateWithApnSetting(
                TRANSPORT_TYPE_WLAN,
                NetworkCapabilities.NET_CAPABILITY_IMS,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_CONNECTED);

        validateApnSetting(TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testOnIwlanHandover() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_HANDOVER, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_HANDOVER_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WWAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WWAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WWAN, EVENT_DATA_CONNECTION_HANDOVER_SUCCESS);
    }

    @Test
    public void testOnIwlanHandoverFail() {
        mReady = false;
        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTING, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_CONNECTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_HANDOVER, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_HANDOVER_STARTED);

        mReady = false;
        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        waitUntilReady();
        validateDataConnectionChangedInfo(
                STATE_CONNECTED, TRANSPORT_TYPE_WLAN, EVENT_DATA_CONNECTION_HANDOVER_FAILED);
    }

    @Test
    public void testOnIwlanHandoverFailWithCause() {

        validateNotConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTING,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectingStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);

        loadPrecisionDataConnectionState(
                TRANSPORT_TYPE_WLAN,
                TelephonyManager.DATA_HANDOVER_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateHandoverStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);

        loadPrecisionDataConnectionStateWithFailCause(
                TRANSPORT_TYPE_WLAN,
                MISSING_UNKNOWN_APN,
                TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        validateConnectedStatusChecks();
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_WLAN);
        validateHandoverFailCause(MISSING_UNKNOWN_APN);
        validateApnSettingForNull(TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testInvalidDataConnectionStatusTrackerHandler() {
        mDataConnectionStatusTracker.mHandler.handleMessage(
                Message.obtain(mDataConnectionStatusTracker.mHandler, -1, null));
        validateDataConnectionLastTransportType(TRANSPORT_TYPE_INVALID);
    }

    private void loadPrecisionDataConnectionState(
            int accessNetwork, int telephonyState, int currentRat) {

        PreciseDataConnectionState dataState =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(accessNetwork)
                        .setState(telephonyState)
                        .setNetworkType(currentRat)
                        .build();
        QnsAsyncResult ar = new QnsAsyncResult(null, dataState, null);
        Message msg = mDataConnectionStatusTracker.mHandler.obtainMessage(11001, ar);
        mDataConnectionStatusTracker.mHandler.handleMessage(msg);
    }

    private void loadPrecisionDataConnectionStateWithFailCause(
            int accessNetwork, int failCause, int telephonyState, int currentRat) {

        PreciseDataConnectionState dataState =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(accessNetwork)
                        .setFailCause(failCause)
                        .setState(telephonyState)
                        .setNetworkType(currentRat)
                        .build();
        QnsAsyncResult ar = new QnsAsyncResult(null, dataState, null);
        Message msg = mDataConnectionStatusTracker.mHandler.obtainMessage(11001, ar);
        mDataConnectionStatusTracker.mHandler.handleMessage(msg);
    }

    private void loadPrecisionDataConnectionStateWithApnSetting(
            int accessNetwork, int apnType, int telephonyState, int currentRat) {
        ApnSetting apnSetting =
                new ApnSetting.Builder()
                        .setApnTypeBitmask(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .setApnName("ims")
                        .setEntryName("IMS")
                        .setApnTypeBitmask(apnType)
                        .setNetworkTypeBitmask(
                                (int) TelephonyManager.NETWORK_STANDARDS_FAMILY_BITMASK_3GPP)
                        .setCarrierEnabled(true)
                        .build();

        PreciseDataConnectionState dataState =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(accessNetwork)
                        .setApnSetting(apnSetting)
                        .setState(telephonyState)
                        .setNetworkType(currentRat)
                        .build();
        QnsAsyncResult ar = new QnsAsyncResult(null, dataState, null);
        Message msg = mDataConnectionStatusTracker.mHandler.obtainMessage(11001, ar);
        mDataConnectionStatusTracker.mHandler.handleMessage(msg);
    }

    private void validateNotConnectedStatusChecks() {
        Assert.assertFalse(mDataConnectionStatusTracker.isActiveState());
        Assert.assertTrue(mDataConnectionStatusTracker.isInactiveState());
        Assert.assertFalse(mDataConnectionStatusTracker.isHandoverState());
    }

    private void validateConnectingStatusChecks() {
        Assert.assertFalse(mDataConnectionStatusTracker.isActiveState());
        Assert.assertFalse(mDataConnectionStatusTracker.isInactiveState());
        Assert.assertFalse(mDataConnectionStatusTracker.isHandoverState());
        Assert.assertTrue(mDataConnectionStatusTracker.isConnectionInProgress());
    }

    private void validateConnectedStatusChecks() {
        Assert.assertTrue(mDataConnectionStatusTracker.isActiveState());
        Assert.assertFalse(mDataConnectionStatusTracker.isInactiveState());
        Assert.assertFalse(mDataConnectionStatusTracker.isHandoverState());
    }

    private void validateHandoverStatusChecks() {
        Assert.assertTrue(mDataConnectionStatusTracker.isActiveState());
        Assert.assertFalse(mDataConnectionStatusTracker.isInactiveState());
        Assert.assertTrue(mDataConnectionStatusTracker.isHandoverState());
        Assert.assertTrue(mDataConnectionStatusTracker.isConnectionInProgress());
    }

    private void validateDataConnectionLastTransportType(int transportType) {
        Assert.assertEquals(transportType, mDataConnectionStatusTracker.getLastTransportType());
    }

    private void validateHandoverFailCause(int dcFailCause) {
        Assert.assertEquals(dcFailCause, mDataConnectionStatusTracker.getLastFailCause());
    }

    private void validateDataConnectionChangedInfo(int state, int transportType, int event) {

        Assert.assertEquals(state, mDcStatus.getState());
        Assert.assertEquals(transportType, mDcStatus.getTransportType());
        Assert.assertEquals(event, mDcStatus.getEvent());
    }

    private void validateApnSettingForNull(int transportType) {
        ApnSetting apnSetting = mDataConnectionStatusTracker.getLastApnSetting(transportType);
        Assert.assertNull(apnSetting);
    }

    private void validateApnSetting(int transportType) {
        ApnSetting apnSetting = mDataConnectionStatusTracker.getLastApnSetting(transportType);
        Assert.assertTrue(apnSetting.canHandleType(NetworkCapabilities.NET_CAPABILITY_IMS));
    }

    @After
    public void tearDown() {
        mDataConnectionStatusTracker.unRegisterDataConnectionStatusChanged(mHandler);
    }
}
