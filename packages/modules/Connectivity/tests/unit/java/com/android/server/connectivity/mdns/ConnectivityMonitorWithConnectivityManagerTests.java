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
 * limitations under the License
 */

package com.android.server.connectivity.mdns;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkRequest;

import com.android.net.module.util.SharedLog;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link ConnectivityMonitor}. */
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class ConnectivityMonitorWithConnectivityManagerTests {
    @Mock private Context mContext;
    @Mock private ConnectivityMonitor.Listener mockListener;
    @Mock private ConnectivityManager mConnectivityManager;
    @Mock private SharedLog sharedLog;

    private ConnectivityMonitorWithConnectivityManager monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(mConnectivityManager).when(mContext)
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        monitor = new ConnectivityMonitorWithConnectivityManager(mContext, mockListener, sharedLog);
    }

    @Test
    public void testInitialState_shouldNotRegisterNetworkCallback() {
        verifyNetworkCallbackRegistered(0 /* time */);
        verifyNetworkCallbackUnregistered(0 /* time */);
    }

    @Test
    public void testStartDiscovery_shouldRegisterNetworkCallback() {
        monitor.startWatchingConnectivityChanges();

        verifyNetworkCallbackRegistered(1 /* time */);
        verifyNetworkCallbackUnregistered(0 /* time */);
    }

    @Test
    public void testStartDiscoveryTwice_shouldRegisterOneNetworkCallback() {
        monitor.startWatchingConnectivityChanges();
        monitor.startWatchingConnectivityChanges();

        verifyNetworkCallbackRegistered(1 /* time */);
        verifyNetworkCallbackUnregistered(0 /* time */);
    }

    @Test
    public void testStopDiscovery_shouldUnregisterNetworkCallback() {
        monitor.startWatchingConnectivityChanges();
        monitor.stopWatchingConnectivityChanges();

        verifyNetworkCallbackRegistered(1 /* time */);
        verifyNetworkCallbackUnregistered(1 /* time */);
    }

    @Test
    public void testStopDiscoveryTwice_shouldUnregisterNetworkCallback() {
        monitor.startWatchingConnectivityChanges();
        monitor.stopWatchingConnectivityChanges();

        verifyNetworkCallbackRegistered(1 /* time */);
        verifyNetworkCallbackUnregistered(1 /* time */);
    }

    @Test
    public void testIntentFired_shouldNotifyListener() {
        InOrder inOrder = inOrder(mockListener);
        monitor.startWatchingConnectivityChanges();

        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        verify(mConnectivityManager, times(1)).registerNetworkCallback(
                any(NetworkRequest.class), callbackCaptor.capture());

        final NetworkCallback callback = callbackCaptor.getValue();
        final Network testNetwork = mock(Network.class);

        // Simulate network available.
        callback.onAvailable(testNetwork);
        inOrder.verify(mockListener).onConnectivityChanged();

        // Simulate network lost.
        callback.onLost(testNetwork);
        inOrder.verify(mockListener).onConnectivityChanged();

        // Simulate network unavailable.
        callback.onUnavailable();
        inOrder.verify(mockListener).onConnectivityChanged();
    }

    private void verifyNetworkCallbackRegistered(int time) {
        verify(mConnectivityManager, times(time)).registerNetworkCallback(
                any(NetworkRequest.class), any(NetworkCallback.class));
    }

    private void verifyNetworkCallbackUnregistered(int time) {
        verify(mConnectivityManager, times(time))
                .unregisterNetworkCallback(any(NetworkCallback.class));
    }
}