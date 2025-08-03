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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link RestrictedWifiNetworkFactory}.
 */
@SmallTest
public class RestrictedWifiNetworkFactoryTest extends WifiBaseTest {
    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock Context mContext;
    NetworkCapabilities mNetworkCapabilities;
    TestLooper mLooper;
    NetworkRequest mNetworkRequest;

    private RestrictedWifiNetworkFactory mRestrictedWifiNetworkFactory;

    /**
     * Setup the mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();
        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        mNetworkCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        mRestrictedWifiNetworkFactory = new RestrictedWifiNetworkFactory(
                mLooper.getLooper(), mContext,
                mNetworkCapabilities, mWifiConnectivityManager);

        mNetworkRequest = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .build();
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Validates handling of needNetworkFor.
     */
    @Test
    public void testHandleNetworkRequest() {
        assertFalse(mRestrictedWifiNetworkFactory.hasConnectionRequests());
        mRestrictedWifiNetworkFactory.needNetworkFor(mNetworkRequest);

        // First network request should turn on auto-join.
        verify(mWifiConnectivityManager).addRestrictionConnectionAllowedUid(anyInt());
        assertTrue(mRestrictedWifiNetworkFactory.hasConnectionRequests());

        // Subsequent ones should do nothing.
        mRestrictedWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        verifyNoMoreInteractions(mWifiConnectivityManager);
    }

    /**
     * Validates handling of releaseNetwork.
     */
    @Test
    public void testHandleNetworkRelease() {
        // Release network without a corresponding request should be ignored.
        mRestrictedWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mRestrictedWifiNetworkFactory.hasConnectionRequests());

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        // Now request & then release the network request
        mRestrictedWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        assertTrue(mRestrictedWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).addRestrictionConnectionAllowedUid(captor.capture());

        mRestrictedWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mRestrictedWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).addRestrictionConnectionAllowedUid(captor.getValue());
    }
}
