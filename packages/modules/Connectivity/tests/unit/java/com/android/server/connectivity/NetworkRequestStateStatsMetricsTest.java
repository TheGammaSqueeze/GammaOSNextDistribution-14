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

package com.android.server.connectivity;

import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED;
import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.testutils.HandlerUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class NetworkRequestStateStatsMetricsTest {
    @Mock
    private NetworkRequestStateStatsMetrics.Dependencies mNRStateStatsDeps;
    @Mock
    private NetworkRequestStateInfo.Dependencies mNRStateInfoDeps;
    @Captor
    private ArgumentCaptor<Handler> mHandlerCaptor;
    @Captor
    private ArgumentCaptor<Integer> mMessageWhatCaptor;

    private NetworkRequestStateStatsMetrics mNetworkRequestStateStatsMetrics;
    private HandlerThread mHandlerThread;
    private static final int TEST_REQUEST_ID = 10;
    private static final int TEST_PACKAGE_UID = 20;
    private static final int TIMEOUT_MS = 30_000;
    private static final NetworkRequest NOT_METERED_WIFI_NETWORK_REQUEST = new NetworkRequest(
            new NetworkCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, true)
                    .setCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET, false)
                    .setRequestorUid(TEST_PACKAGE_UID),
            0, TEST_REQUEST_ID, NetworkRequest.Type.REQUEST
    );

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mHandlerThread = new HandlerThread("NetworkRequestStateStatsMetrics");
        Mockito.when(mNRStateStatsDeps.makeHandlerThread("NetworkRequestStateStatsMetrics"))
                .thenReturn(mHandlerThread);
        Mockito.when(mNRStateStatsDeps.getMillisSinceEvent(anyLong())).thenReturn(0L);
        Mockito.doAnswer(invocation -> {
            mHandlerCaptor.getValue().sendMessage(
                    Message.obtain(mHandlerCaptor.getValue(), mMessageWhatCaptor.getValue()));
            return null;
        }).when(mNRStateStatsDeps).sendMessageDelayed(
                mHandlerCaptor.capture(), mMessageWhatCaptor.capture(), anyLong());
        mNetworkRequestStateStatsMetrics = new NetworkRequestStateStatsMetrics(
                mNRStateStatsDeps, mNRStateInfoDeps);
    }

    @Test
    public void testNetworkRequestReceivedRemoved() {
        final long nrStartTime = 1L;
        final long nrEndTime = 101L;
        // This call will be used to calculate NR received time
        Mockito.when(mNRStateInfoDeps.getElapsedRealtime()).thenReturn(nrStartTime);
        mNetworkRequestStateStatsMetrics.onNetworkRequestReceived(NOT_METERED_WIFI_NETWORK_REQUEST);

        ArgumentCaptor<NetworkRequestStateInfo> networkRequestStateInfoCaptor =
                ArgumentCaptor.forClass(NetworkRequestStateInfo.class);
        verify(mNRStateStatsDeps, timeout(TIMEOUT_MS))
                .writeStats(networkRequestStateInfoCaptor.capture());

        NetworkRequestStateInfo nrStateInfoSent = networkRequestStateInfoCaptor.getValue();
        assertEquals(NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED,
                nrStateInfoSent.getNetworkRequestStateStatsType());
        assertEquals(NOT_METERED_WIFI_NETWORK_REQUEST.requestId, nrStateInfoSent.getRequestId());
        assertEquals(TEST_PACKAGE_UID, nrStateInfoSent.getPackageUid());
        assertEquals(1 << NetworkCapabilities.TRANSPORT_WIFI, nrStateInfoSent.getTransportTypes());
        assertTrue(nrStateInfoSent.getNetCapabilityNotMetered());
        assertFalse(nrStateInfoSent.getNetCapabilityInternet());
        assertEquals(0, nrStateInfoSent.getNetworkRequestDurationMillis());

        clearInvocations(mNRStateStatsDeps);
        // This call will be used to calculate NR removed time
        Mockito.when(mNRStateInfoDeps.getElapsedRealtime()).thenReturn(nrEndTime);
        mNetworkRequestStateStatsMetrics.onNetworkRequestRemoved(NOT_METERED_WIFI_NETWORK_REQUEST);

        verify(mNRStateStatsDeps, timeout(TIMEOUT_MS))
                .writeStats(networkRequestStateInfoCaptor.capture());

        nrStateInfoSent = networkRequestStateInfoCaptor.getValue();
        assertEquals(NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED,
                nrStateInfoSent.getNetworkRequestStateStatsType());
        assertEquals(NOT_METERED_WIFI_NETWORK_REQUEST.requestId, nrStateInfoSent.getRequestId());
        assertEquals(TEST_PACKAGE_UID, nrStateInfoSent.getPackageUid());
        assertEquals(1 << NetworkCapabilities.TRANSPORT_WIFI, nrStateInfoSent.getTransportTypes());
        assertTrue(nrStateInfoSent.getNetCapabilityNotMetered());
        assertFalse(nrStateInfoSent.getNetCapabilityInternet());
        assertEquals(nrEndTime - nrStartTime, nrStateInfoSent.getNetworkRequestDurationMillis());
    }

    @Test
    public void testUnreceivedNetworkRequestRemoved() {
        mNetworkRequestStateStatsMetrics.onNetworkRequestRemoved(NOT_METERED_WIFI_NETWORK_REQUEST);
        HandlerUtils.waitForIdle(mHandlerThread, TIMEOUT_MS);
        verify(mNRStateStatsDeps, never())
                .writeStats(any(NetworkRequestStateInfo.class));
    }

    @Test
    public void testNoMessagesWhenNetworkRequestReceived() {
        mNetworkRequestStateStatsMetrics.onNetworkRequestReceived(NOT_METERED_WIFI_NETWORK_REQUEST);
        verify(mNRStateStatsDeps, timeout(TIMEOUT_MS))
                .writeStats(any(NetworkRequestStateInfo.class));

        clearInvocations(mNRStateStatsDeps);
        mNetworkRequestStateStatsMetrics.onNetworkRequestReceived(NOT_METERED_WIFI_NETWORK_REQUEST);
        HandlerUtils.waitForIdle(mHandlerThread, TIMEOUT_MS);
        verify(mNRStateStatsDeps, never())
                .writeStats(any(NetworkRequestStateInfo.class));
    }

    @Test
    public void testMessageQueueSizeLimitNotExceeded() {
        // Imitate many events (MAX_QUEUED_REQUESTS) are coming together at once while
        // the other event is being processed.
        final ConditionVariable cv = new ConditionVariable();
        mHandlerThread.getThreadHandler().post(() -> cv.block());
        for (int i = 0; i < NetworkRequestStateStatsMetrics.MAX_QUEUED_REQUESTS / 2; i++) {
            mNetworkRequestStateStatsMetrics.onNetworkRequestReceived(new NetworkRequest(
                    new NetworkCapabilities().setRequestorUid(TEST_PACKAGE_UID),
                    0, i + 1, NetworkRequest.Type.REQUEST));
            mNetworkRequestStateStatsMetrics.onNetworkRequestRemoved(new NetworkRequest(
                    new NetworkCapabilities().setRequestorUid(TEST_PACKAGE_UID),
                    0, i + 1, NetworkRequest.Type.REQUEST));
        }

        // When event queue is full, all other events should be dropped.
        mNetworkRequestStateStatsMetrics.onNetworkRequestReceived(new NetworkRequest(
                new NetworkCapabilities().setRequestorUid(TEST_PACKAGE_UID),
                0, 2 * NetworkRequestStateStatsMetrics.MAX_QUEUED_REQUESTS + 1,
                NetworkRequest.Type.REQUEST));

        cv.open();

        // Check only first MAX_QUEUED_REQUESTS events are logged.
        ArgumentCaptor<NetworkRequestStateInfo> networkRequestStateInfoCaptor =
                ArgumentCaptor.forClass(NetworkRequestStateInfo.class);
        verify(mNRStateStatsDeps, timeout(TIMEOUT_MS).times(
                NetworkRequestStateStatsMetrics.MAX_QUEUED_REQUESTS))
                .writeStats(networkRequestStateInfoCaptor.capture());
        for (int i = 0; i < NetworkRequestStateStatsMetrics.MAX_QUEUED_REQUESTS; i++) {
            NetworkRequestStateInfo nrStateInfoSent =
                    networkRequestStateInfoCaptor.getAllValues().get(i);
            assertEquals(i / 2 + 1, nrStateInfoSent.getRequestId());
            assertEquals(
                    (i % 2 == 0)
                            ? NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED
                            : NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED,
                    nrStateInfoSent.getNetworkRequestStateStatsType());
        }
    }
}
