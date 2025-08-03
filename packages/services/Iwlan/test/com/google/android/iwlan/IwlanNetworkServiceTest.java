/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TelephonyNetworkSpecifier;
import android.net.vcn.VcnTransportInfo;
import android.telephony.AccessNetworkConstants;
import android.telephony.INetworkService;
import android.telephony.INetworkServiceCallback;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkServiceCallback;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;

import com.google.android.iwlan.IwlanNetworkService.IwlanNetworkServiceProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;

public class IwlanNetworkServiceTest {
    private static final String TAG = IwlanNetworkServiceTest.class.getSimpleName();
    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int DEFAULT_SUB_INDEX = 0;

    @Mock private Context mMockContext;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private ImsManager mMockImsManager;
    @Mock private ImsMmTelManager mMockImsMmTelManager;
    @Mock private INetworkServiceCallback mCallback;
    @Mock private Network mMockNetwork;
    MockitoSession mStaticMockSession;

    IwlanNetworkService mIwlanNetworkService;
    INetworkService mBinder;
    IwlanNetworkServiceProvider mIwlanNetworkServiceProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(SubscriptionManager.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);

        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                        eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockSubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(DEFAULT_SUB_INDEX);
        when(mMockSubscriptionManager.getSlotIndex(DEFAULT_SUB_INDEX))
                .thenReturn(DEFAULT_SLOT_INDEX);
        when(mMockSubscriptionManager.getSlotIndex(DEFAULT_SUB_INDEX + 1))
                .thenReturn(DEFAULT_SLOT_INDEX + 1);

        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(DEFAULT_SUB_INDEX);

        when(mMockContext.getSystemService(eq(ImsManager.class))).thenReturn(mMockImsManager);

        when(mMockImsManager.getImsMmTelManager(anyInt())).thenReturn(mMockImsMmTelManager);

        mIwlanNetworkService = new IwlanNetworkService();
        mIwlanNetworkService.setAppContext(mMockContext);
        mIwlanNetworkServiceProvider = null;

        mBinder = mIwlanNetworkService.mBinder;
        mBinder.createNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        mBinder.registerForNetworkRegistrationInfoChanged(DEFAULT_SLOT_INDEX, mCallback);
    }

    @After
    public void cleanUp() throws Exception {
        mBinder.removeNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        mStaticMockSession.finishMocking();
    }

    @Nullable
    IwlanNetworkServiceProvider initNSP() {
        // Wait for IwlanNetworkServiceProvider created and timeout is 1 second.
        long startTime = System.currentTimeMillis();
        IwlanNetworkServiceProvider nsp = null;
        while (System.currentTimeMillis() - startTime < 1000) {
            nsp = mIwlanNetworkService.getNetworkServiceProvider(DEFAULT_SLOT_INDEX);
            if (nsp != null) {
                break;
            }
        }
        return nsp;
    }

    @Test
    public void testRequestNetworkRegistrationInfo() throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        // Set Wifi on and verify mCallback should receive onNetworkStateChanged.
        mIwlanNetworkService.setNetworkConnected(true, IwlanNetworkService.Transport.WIFI);
        verify(mCallback, timeout(1000).times(1)).onNetworkStateChanged();

        // Set Sub active and verify mCallback should receive onNetworkStateChanged.
        mIwlanNetworkServiceProvider.subscriptionChanged();
        verify(mCallback, timeout(1000).times(2)).onNetworkStateChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* isSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    private NetworkCapabilities prepareCellularNetworkCapabilitiesForTest(
            int subId, boolean isVcn) {
        NetworkCapabilities.Builder builder =
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (isVcn) {
            builder.setTransportInfo(new VcnTransportInfo(subId));
        } else {
            builder.setNetworkSpecifier(new TelephonyNetworkSpecifier(subId));
        }
        return builder.build();
    }

    private NetworkCapabilities prepareWifiNetworkCapabilitiesForTest() {
        return new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
    }

    @Test
    public void testNetworkRegistrationInfoSearchingForCellularAndCstDisabled() throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(false);

        NetworkCapabilities nc =
                prepareCellularNetworkCapabilitiesForTest(DEFAULT_SUB_INDEX, false /* is Vcn */);
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);
        mIwlanNetworkServiceProvider.subscriptionChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* isSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    @Test
    public void testNetworkRegistrationInfoSearchingForCellularOnSameSubAndCstEnabled()
            throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        NetworkCapabilities nc =
                prepareCellularNetworkCapabilitiesForTest(DEFAULT_SUB_INDEX, false /* is Vcn */);
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);
        mIwlanNetworkServiceProvider.subscriptionChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* mIsSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    @Test
    public void testNetworkRegistrationInfoHomeForCellularOnDifferentSubAndCstEnabled()
            throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        // Cellular data is on the other sub
        NetworkCapabilities nc =
                prepareCellularNetworkCapabilitiesForTest(
                        DEFAULT_SUB_INDEX + 1, false /* is Vcn */);
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);
        mIwlanNetworkServiceProvider.subscriptionChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* isSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    @Test
    public void testNetworkRegistrationInfoHomeForCellularVcnOnDifferentSubAndCstEnabled()
            throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        // Cellular data as a VCN network is on the other sub
        NetworkCapabilities nc =
                prepareCellularNetworkCapabilitiesForTest(DEFAULT_SUB_INDEX + 1, true /* is Vcn */);
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);
        mIwlanNetworkServiceProvider.subscriptionChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* isSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    @Test
    public void testNetworkRegistrationInfoHomeForWiFiAndCstEnabled() throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        NetworkCapabilities nc = prepareWifiNetworkCapabilitiesForTest();
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);
        mIwlanNetworkServiceProvider.subscriptionChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* isSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    @Test
    public void testNetworkRegistrationInfoHomeForWiFiAndCstDisabled() throws Exception {
        mIwlanNetworkServiceProvider = initNSP();
        assertTrue(mIwlanNetworkServiceProvider != null);

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(false);

        NetworkCapabilities nc = prepareWifiNetworkCapabilitiesForTest();
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        mIwlanNetworkServiceProvider.subscriptionChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                generateStateBuilder(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        true /* isSubActive */,
                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mBinder.requestNetworkRegistrationInfo(0, NetworkRegistrationInfo.DOMAIN_PS, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }

    private NetworkRegistrationInfo.Builder generateStateBuilder(
            int domain, boolean isSubActive, int registrationState) {
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                new NetworkRegistrationInfo.Builder();
        expectedStateBuilder
                .setAccessNetworkTechnology(
                        (registrationState
                                        == NetworkRegistrationInfo
                                                .REGISTRATION_STATE_NOT_REGISTERED_SEARCHING)
                                ? TelephonyManager.NETWORK_TYPE_UNKNOWN
                                : TelephonyManager.NETWORK_TYPE_IWLAN)
                .setAvailableServices(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setEmergencyOnly(!isSubActive)
                .setDomain(domain)
                .setRegistrationState(registrationState);

        return expectedStateBuilder;
    }
}
