/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task.wifi;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link NetworkMonitor}.
 */
@SmallTest
public class NetworkMonitorTest {

    @Mock private Context mContext;
    @Mock private ConnectivityManager mConnManager;
    @Mock private NetworkMonitor.NetworkConnectedCallback mCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(mConnManager).when(mContext).getSystemService(Context.CONNECTIVITY_SERVICE);
        doReturn(Context.CONNECTIVITY_SERVICE).when(mContext).getSystemServiceName(
                ConnectivityManager.class);
    }

    @Test
    public void testStartListening() {
        // WHEN starting to listen for connectivity changes
        final NetworkMonitor nm = new NetworkMonitor(mContext, false /* waitForValidated */);
        nm.startListening(mCallback);

        // THEN a callback should be registered
        final NetworkCallback cb = verifyCallbackRegistered();

        // WHEN the network connects and a callback is received
        final Network network = mock(Network.class);
        cb.onAvailable(network);
        cb.onBlockedStatusChanged(network, false);

        // THEN a callback should be given
        verify(mCallback).onNetworkConnected();

        nm.stopListening();
    }

    @Test
    public void testStopListening() {
        // WHEN starting and stopping to listen for connectivity changes
        final NetworkMonitor nm = new NetworkMonitor(mContext, false /* waitForValidated */);
        nm.startListening(mCallback);
        nm.stopListening();

        // THEN a callback should be registered and later unregistered
        final NetworkCallback cb = verifyCallbackRegistered();
        verify(mConnManager).unregisterNetworkCallback(cb);

        // Even if an unexpected callback is received after unregistering
        cb.onBlockedStatusChanged(mock(Network.class), false);

        // THEN no callback should be given
        verifyZeroInteractions(mCallback);
    }

    @Test
    public void testWaitForValidated_NoCallbackBeforeValidated() {
        final NetworkMonitor nm = new NetworkMonitor(mContext, /* waitForValidated */ true);
        nm.startListening(mCallback);

        final NetworkCallback cb = verifyCallbackRegistered();

        final Network network = mock(Network.class);
        cb.onAvailable(network);
        cb.onBlockedStatusChanged(network, false);
        cb.onCapabilitiesChanged(network, new NetworkCapabilities());

        verifyZeroInteractions(mCallback);

        nm.stopListening();
    }

    @Test
    public void testWaitForValidated_NetworkChange() {
        final NetworkMonitor nm = new NetworkMonitor(mContext, /* waitForValidated */ true);
        nm.startListening(mCallback);

        final NetworkCallback cb = verifyCallbackRegistered();

        final Network network = mock(Network.class);
        cb.onAvailable(network);
        cb.onBlockedStatusChanged(network, false);
        cb.onCapabilitiesChanged(network, new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());

        verify(mCallback).onNetworkConnected();

        final Network network2 = mock(Network.class);
        cb.onAvailable(network2);
        cb.onBlockedStatusChanged(network2, false);

        // Callback is only called after the second network validates
        verifyNoMoreInteractions(mCallback);

        cb.onCapabilitiesChanged(network2, new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());

        verify(mCallback, times(2)).onNetworkConnected();

        nm.stopListening();
    }

    @Test
    public void testWaitForValidated_NoCallbackOnCapabilitiesChange() {
        final NetworkMonitor nm = new NetworkMonitor(mContext, /* waitForValidated */ true);
        nm.startListening(mCallback);

        final NetworkCallback cb = verifyCallbackRegistered();

        final Network network = mock(Network.class);
        final NetworkCapabilities validatedCaps = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        cb.onAvailable(network);
        cb.onBlockedStatusChanged(network, false);
        cb.onCapabilitiesChanged(network, validatedCaps);

        verify(mCallback).onNetworkConnected();

        cb.onCapabilitiesChanged(network, validatedCaps);

        verifyNoMoreInteractions(mCallback);

        nm.stopListening();
    }

    @Test
    public void testWaitForValidated_ToggleBlocked() {
        final NetworkMonitor nm = new NetworkMonitor(mContext, /* waitForValidated */ true);
        nm.startListening(mCallback);

        final NetworkCallback cb = verifyCallbackRegistered();

        final Network network = mock(Network.class);
        cb.onAvailable(network);
        cb.onBlockedStatusChanged(network, false);
        cb.onCapabilitiesChanged(network, new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build());

        verify(mCallback).onNetworkConnected();

        // Network gets blocked then unblocked. Callback only gets called after unblocking.
        cb.onBlockedStatusChanged(network, true);

        verifyNoMoreInteractions(mCallback);

        cb.onBlockedStatusChanged(network, false);

        verify(mCallback, times(2)).onNetworkConnected();

        nm.stopListening();
    }

    private NetworkCallback verifyCallbackRegistered() {
        final ArgumentCaptor<NetworkCallback> cbCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        verify(mConnManager).registerDefaultNetworkCallback(cbCaptor.capture());
        return cbCaptor.getValue();
    }
}
