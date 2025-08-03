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

package com.android.mms.service;

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.robolectric.RuntimeEnvironment.getMasterScheduler;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public final class MmsNetworkManagerTest {
    private static final int TEST_SUBID = 1234;
    private static final int CALLBACK_TIMEOUT_MS = 3000;
    private static final int NETWORK_ACQUIRE_TIMEOUT_MS = 5000;

    private static final String MMS_APN = "mmsapn";
    private static final String MMS_APN2 = "mmsapn2";
    private static final NetworkCapabilities SUSPEND_NC = new NetworkCapabilities.Builder().build();
    private static final NetworkCapabilities USABLE_NC =
            new NetworkCapabilities.Builder().addCapability(NET_CAPABILITY_NOT_SUSPENDED).build();

    @Mock Network mTestNetwork;
    @Mock Network mTestNetwork2;
    @Mock NetworkInfo mNetworkInfo;
    @Mock NetworkInfo mNetworkInfo2;
    @Mock Context mCtx;
    @Mock ConnectivityManager mCm;
    @Mock MmsNetworkManager.Dependencies mDeps;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock PersistableBundle mConfig;

    private MmsNetworkManager mMnm;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger mRequestId = new AtomicInteger(1);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        doReturn(mCm).when(mCtx).getSystemService(Context.CONNECTIVITY_SERVICE);
        doReturn(mCm).when(mCtx).getSystemService(ConnectivityManager.class);
        doReturn(1).when(mDeps).getPhoneId(anyInt());
        doReturn(mNetworkInfo).when(mCm).getNetworkInfo(eq(mTestNetwork));
        doReturn(MMS_APN).when(mNetworkInfo).getExtraInfo();
        doReturn(mNetworkInfo2).when(mCm).getNetworkInfo(eq(mTestNetwork2));
        doReturn(MMS_APN2).when(mNetworkInfo2).getExtraInfo();
        doReturn(NETWORK_ACQUIRE_TIMEOUT_MS).when(mDeps).getNetworkRequestTimeoutMillis();
        doReturn(NETWORK_ACQUIRE_TIMEOUT_MS).when(mDeps).getAdditionalNetworkAcquireTimeoutMillis();
        doReturn(mCarrierConfigManager).when(mCtx).getSystemService(Context.CARRIER_CONFIG_SERVICE);
        doReturn(mConfig).when(mCarrierConfigManager).getConfigForSubId(TEST_SUBID);

        mMnm = new MmsNetworkManager(mCtx, TEST_SUBID, mDeps);
    }

    @Test
    public void testAvailableNetwork_newNetworkAvailable() throws Exception {
        acquireAvailableNetworkAndGetCallback(
                mTestNetwork /* expectNetwork */, MMS_APN /* expectApn */);
    }

    @Test
    public void testAvailableNetwork_newNetworkIsSuspend() throws Exception {
        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        final CompletableFuture<String> future =
                acquireNetwork(Integer.toString(mRequestId.getAndIncrement()));
        verify(mCm, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .requestNetwork(any(), callbackCaptor.capture(), anyInt());
        final NetworkCallback callback = callbackCaptor.getValue();

        callback.onCapabilitiesChanged(mTestNetwork, SUSPEND_NC);
        assertFalse(future.isDone());
        // mNetwork should be null.
        assertEquals(null, mMnm.getApnName());
        verify(mCm, never()).unregisterNetworkCallback(eq(callback));
    }

    @Test
    public void testAvailableNetwork_networkSuspendThenResume() throws Exception {
        final NetworkCallback callback = acquireAvailableNetworkAndGetCallback(
                mTestNetwork /* expectNetwork */, MMS_APN /* expectApn */);

        // Network becomes suspended. mNetwork should be null.
        callback.onCapabilitiesChanged(mTestNetwork, SUSPEND_NC);
        assertEquals(null, mMnm.getApnName());
        verify(mCm, never()).unregisterNetworkCallback(eq(callback));

        // Network resume
        callback.onCapabilitiesChanged(mTestNetwork, USABLE_NC);
        assertEquals(MMS_APN, mMnm.getApnName());
    }

    @Test
    public void testAvailableNetwork_networkReplaced() throws Exception {
        final NetworkCallback callback = acquireAvailableNetworkAndGetCallback(
                mTestNetwork /* expectNetwork */, MMS_APN /* expectApn */);

        // Previous network lost. Callback will not release to wait for possible network.
        callback.onLost(mTestNetwork);
        verify(mCm, never()).unregisterNetworkCallback(eq(callback));
        // New mTestNetwork2 available
        callback.onCapabilitiesChanged(mTestNetwork2, USABLE_NC);
        assertEquals(MMS_APN2, mMnm.getApnName());
    }

    @Test
    public void testAvailableNetwork_wwanNetworkReplacedByWlanNetwork() throws Exception {
        MmsHttpClient mockMmsHttpClient = mock(MmsHttpClient.class);
        doReturn(true).when(mDeps).isMmsEnhancementEnabled();

        // WWAN network is always available, whereas WLAN network needs extra time to be set up.
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mNetworkInfo).getSubtype();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mNetworkInfo2).getSubtype();

        final NetworkCallback callback = acquireAvailableNetworkAndGetCallback(
                mTestNetwork /* expectNetwork */, MMS_APN /* expectApn */);
        replaceInstance(MmsNetworkManager.class, "mMmsHttpClient", mMnm,
                mockMmsHttpClient);

        // The WLAN network become available.
        callback.onCapabilitiesChanged(mTestNetwork2, USABLE_NC);
        getMasterScheduler().advanceToLastPostedRunnable();

        // Verify current connections disconnect, then the client is replaced with a new network.
        verify(mockMmsHttpClient).disconnectAllUrlConnections();
        assertNotSame(mMnm.getOrCreateHttpClient(), mockMmsHttpClient);
    }

    @Test
    public void testAvailableNetwork_networkBecomeSuspend() throws Exception {
        final NetworkCallback callback = acquireAvailableNetworkAndGetCallback(
                mTestNetwork /* expectNetwork */, MMS_APN /* expectApn */);

        callback.onCapabilitiesChanged(mTestNetwork, SUSPEND_NC);
        // mNetwork should be null.
        assertEquals(null, mMnm.getApnName());
        // Callback will not release to wait for possible network.
        verify(mCm, never()).unregisterNetworkCallback(eq(callback));
    }

    @Test
    public void testAvailableNetwork_networkUnavailable() throws Exception {
        doReturn(100).when(mDeps).getNetworkRequestTimeoutMillis();
        doReturn(100).when(mDeps).getAdditionalNetworkAcquireTimeoutMillis();
        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        final CompletableFuture<String> future =
                acquireNetwork(Integer.toString(mRequestId.getAndIncrement()));
        verify(mCm, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .requestNetwork(any(), callbackCaptor.capture(), anyInt());
        final NetworkCallback callback = callbackCaptor.getValue();

        assertFalse(future.isDone());
        // No network available after 100+100 ms. Callback will be released.
        verify(mCm, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .unregisterNetworkCallback(eq(callback));

        // mNetwork should be null.
        assertEquals(null, mMnm.getApnName());
    }

    @Test
    public void testAvailableNetwork_newNetworkSuspendedEventuallyReleased() throws Exception {
        doReturn(100).when(mDeps).getNetworkRequestTimeoutMillis();
        doReturn(100).when(mDeps).getAdditionalNetworkAcquireTimeoutMillis();
        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        final CompletableFuture<String> future =
                acquireNetwork(Integer.toString(mRequestId.getAndIncrement()));
        verify(mCm, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .requestNetwork(any(), callbackCaptor.capture(), anyInt());
        final NetworkCallback callback = callbackCaptor.getValue();

        // New network but not a usable network.
        callback.onCapabilitiesChanged(mTestNetwork, SUSPEND_NC);

        assertFalse(future.isDone());
        // No network available after 100+100 ms. Callback will be released.
        verify(mCm, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .unregisterNetworkCallback(eq(callback));

        // mNetwork should be null.
        assertEquals(null, mMnm.getApnName());
    }

    @Test
    public void testHandleCarrierConfigChanged() throws Exception {
        // Expect receiving default NETWORK_RELEASE_TIMEOUT of 5 seconds
        int defaultNetworkReleaseTimeout = 5000;
        doReturn(defaultNetworkReleaseTimeout).when(mConfig).getInt(
                CarrierConfigManager.KEY_MMS_NETWORK_RELEASE_TIMEOUT_MILLIS_INT);
        MmsNetworkManager mmsNetworkManager = new MmsNetworkManager(mCtx, TEST_SUBID, mDeps);
        assertEquals(defaultNetworkReleaseTimeout,
                mmsNetworkManager.getNetworkReleaseTimeoutMillis());

        // Expect receiving a carrier-configured value
        int configuredNetworkReleaseTimeout = 10000;
        doReturn(configuredNetworkReleaseTimeout).when(mConfig).getInt(
                CarrierConfigManager.KEY_MMS_NETWORK_RELEASE_TIMEOUT_MILLIS_INT);
        mmsNetworkManager = new MmsNetworkManager(mCtx, TEST_SUBID, mDeps);
        assertEquals(configuredNetworkReleaseTimeout,
                mmsNetworkManager.getNetworkReleaseTimeoutMillis());
    }

    private NetworkCallback acquireAvailableNetworkAndGetCallback(
            Network expectNetwork, String expectApn) throws Exception {
        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        final CompletableFuture<String> future =
                acquireNetwork(Integer.toString(mRequestId.getAndIncrement()));
        verify(mCm, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .requestNetwork(any(), callbackCaptor.capture(), anyInt());
        final NetworkCallback callback = callbackCaptor.getValue();

        // Network available
        callback.onCapabilitiesChanged(expectNetwork, USABLE_NC);
        assertEquals(expectApn, future.get(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        return callbackCaptor.getValue();
    }

    private CompletableFuture<String> acquireNetwork(String requestId) {
        final CompletableFuture<String> future = new CompletableFuture();

        mExecutor.execute(() -> {
            try {
                mMnm.acquireNetwork(requestId);
                future.complete(mMnm.getApnName());
                android.util.Log.d("MmsNetworkManagerTest", "acquireNetwork done");
            } catch (Exception e) {
                fail("Acquire network fail");
            }
        });

        return future;
    }

    /** Helper to replace instance field with reflection. */
    private void replaceInstance(final Class c, final String instanceName,
            final Object obj, final Object newValue) throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);
        field.set(obj, newValue);
    }
}
