/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.WorkSource;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.OemWifiNetworkFactory}.
 */
@SmallTest
public class OemWifiNetworkFactoryTest extends WifiBaseTest {
    private static final int TEST_UID = 4556;
    private static final String TEST_PACKAGE_NAME = "com.test";
    private static final WorkSource TEST_WORKSOURCE = new WorkSource(TEST_UID, TEST_PACKAGE_NAME);

    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock Context mContext;
    NetworkCapabilities mNetworkCapabilities;
    TestLooper mLooper;
    NetworkRequest mNetworkRequest;

    private OemWifiNetworkFactory mOemWifiNetworkFactory;

    /**
     * Setup the mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastS());

        mLooper = new TestLooper();
        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        mNetworkCapabilities.setRequestorUid(TEST_UID);
        mNetworkCapabilities.setRequestorPackageName(TEST_PACKAGE_NAME);

        mOemWifiNetworkFactory = new OemWifiNetworkFactory(
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
    public void testOemPaidHandleNetworkRequest() {
        mNetworkRequest.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_OEM_PAID);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);

        // First network request should turn on auto-join.
        verify(mWifiConnectivityManager).setOemPaidConnectionAllowed(true, TEST_WORKSOURCE);
        assertTrue(mOemWifiNetworkFactory.hasConnectionRequests());

        // Subsequent ones should do nothing.
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        verifyNoMoreInteractions(mWifiConnectivityManager);
    }

    /**
     * Validates handling of releaseNetwork.
     */
    @Test
    public void testHandleOemPaidNetworkRelease() {
        mNetworkRequest.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_OEM_PAID);
        // Release network without a corresponding request should be ignored.
        mOemWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());

        // Now request & then release the network request
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        assertTrue(mOemWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).setOemPaidConnectionAllowed(true, TEST_WORKSOURCE);

        mOemWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).setOemPaidConnectionAllowed(false, null);
    }

    /**
     * Validates handling of needNetworkFor.
     */
    @Test
    public void testOemPrivateHandleNetworkRequest() {
        mNetworkRequest.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);

        // First network request should turn on auto-join.
        verify(mWifiConnectivityManager).setOemPrivateConnectionAllowed(true, TEST_WORKSOURCE);
        assertTrue(mOemWifiNetworkFactory.hasConnectionRequests());

        // Subsequent ones should do nothing.
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        verifyNoMoreInteractions(mWifiConnectivityManager);
    }

    /**
     * Validates handling of releaseNetwork.
     */
    @Test
    public void testHandleOemPrivateNetworkRelease() {
        mNetworkRequest.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE);
        // Release network without a corresponding request should be ignored.
        mOemWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());

        // Now request & then release the network request
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        assertTrue(mOemWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).setOemPrivateConnectionAllowed(true, TEST_WORKSOURCE);

        mOemWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).setOemPrivateConnectionAllowed(false, null);
    }

    /**
     * Validates handling of releaseNetwork.
     */
    @Test
    public void testHandleOemPaidAndOemPrivateNetworkRequestAndRelease() {
        mNetworkRequest.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_OEM_PAID);
        mNetworkRequest.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE);
        // Release network without a corresponding request should be ignored.
        mOemWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());

        // Now request & then release the network request
        mOemWifiNetworkFactory.needNetworkFor(mNetworkRequest);
        assertTrue(mOemWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).setOemPaidConnectionAllowed(true, TEST_WORKSOURCE);
        verify(mWifiConnectivityManager).setOemPrivateConnectionAllowed(true, TEST_WORKSOURCE);

        mOemWifiNetworkFactory.releaseNetworkFor(mNetworkRequest);
        assertFalse(mOemWifiNetworkFactory.hasConnectionRequests());
        verify(mWifiConnectivityManager).setOemPaidConnectionAllowed(false, null);
        verify(mWifiConnectivityManager).setOemPrivateConnectionAllowed(false, null);
    }

}
