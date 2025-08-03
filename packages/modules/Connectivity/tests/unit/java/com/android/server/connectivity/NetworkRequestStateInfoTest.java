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

package com.android.server.connectivity;

import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED;
import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED;

import static org.junit.Assert.assertEquals;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class NetworkRequestStateInfoTest {

    @Mock
    private NetworkRequestStateInfo.Dependencies mDependencies;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }
    @Test
    public void testSetNetworkRequestRemoved() {
        final long nrStartTime = 1L;
        final long nrEndTime = 101L;

        NetworkRequest notMeteredWifiNetworkRequest = new NetworkRequest(
                new NetworkCapabilities()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, true),
                0, 1, NetworkRequest.Type.REQUEST
        );

        // This call will be used to calculate NR received time
        Mockito.when(mDependencies.getElapsedRealtime()).thenReturn(nrStartTime);
        NetworkRequestStateInfo networkRequestStateInfo = new NetworkRequestStateInfo(
                notMeteredWifiNetworkRequest, mDependencies);

        // This call will be used to calculate NR removed time
        Mockito.when(mDependencies.getElapsedRealtime()).thenReturn(nrEndTime);
        networkRequestStateInfo.setNetworkRequestRemoved();
        assertEquals(
                nrEndTime - nrStartTime,
                networkRequestStateInfo.getNetworkRequestDurationMillis());
        assertEquals(networkRequestStateInfo.getNetworkRequestStateStatsType(),
                NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_REMOVED);
    }

    @Test
    public void testCheckInitialState() {
        NetworkRequestStateInfo networkRequestStateInfo = new NetworkRequestStateInfo(
                new NetworkRequest(new NetworkCapabilities(), 0, 1, NetworkRequest.Type.REQUEST),
                mDependencies);
        assertEquals(networkRequestStateInfo.getNetworkRequestStateStatsType(),
                NETWORK_REQUEST_STATE_CHANGED__STATE__NETWORK_REQUEST_STATE_RECEIVED);
    }
}
