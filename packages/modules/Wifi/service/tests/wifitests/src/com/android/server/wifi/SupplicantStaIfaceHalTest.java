/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.DscpPolicy;
import android.net.MacAddress;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.util.HexEncoding;
import android.os.Handler;
import android.util.Range;

import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyClassifierParams;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for SupplicantStaIfaceHal, which functions as a wrapper for either
 * SupplicantStaIfaceHalHidlImpl or SupplicantStaIfaceHalAidlImpl, depending on
 * which service (HIDL or AIDL) is available. Test the initialization logic and
 * verify that calls to all public methods are forwarded to the actual implementation.
 */
public class SupplicantStaIfaceHalTest extends WifiBaseTest {
    private SupplicantStaIfaceHalSpy mDut;
    private @Mock SupplicantStaIfaceHalHidlImpl mStaIfaceHalHidlMock;
    private @Mock SupplicantStaIfaceHalAidlImpl mStaIfaceHalAidlMock;
    private @Mock WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;
    private @Mock Context mContext;
    private @Mock WifiMonitor mWifiMonitor;
    private @Mock FrameworkFacade mFrameworkFacade;
    private @Mock Handler mHandler;
    private @Mock Clock mClock;
    private @Mock WifiMetrics mWifiMetrics;
    private @Mock WifiGlobals mWifiGlobals;
    private @Mock SsidTranslator mSsidTranslator;
    private @Mock WifiInjector mWifiInjector;

    private static final String IFACE_NAME = "wlan0";
    private static final String BSSID = "fa:45:23:23:12:12";
    private static final String PARAMS = "blahblah";
    private static final String RESPONSE = "blahblahblah";
    private static final String PIN = "5678";
    private static final boolean ENABLE = true;
    private static final int NETWORK_ID = 2;
    private static final int PEER_ID = 3;
    private static final int OWN_ID = 4;
    private static final int MODE = 5;

    private static final byte QOS_POLICY_ID = 12;
    private static final int QOS_POLICY_REQUEST_TYPE =
            SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD;
    private static final byte QOS_POLICY_DSCP = 0;
    private static final int QOS_POLICY_SRC_PORT = DscpPolicy.SOURCE_PORT_ANY;
    private static final int QOS_POLICY_PROTOCOL = DscpPolicy.PROTOCOL_ANY;

    private class SupplicantStaIfaceHalSpy extends SupplicantStaIfaceHal {
        SupplicantStaIfaceHalSpy() {
            super(mContext, mWifiMonitor, mFrameworkFacade,
                    mHandler, mClock, mWifiMetrics, mWifiGlobals, mSsidTranslator, mWifiInjector);
        }

        @Override
        protected ISupplicantStaIfaceHal createStaIfaceHalMockable()  {
            return mStaIfaceHalAidlMock;
        }
    }

    /**
     * Implementation of SupplicantStaIfaceHalSpy that uses the HIDL mock internally
     * rather than the default AIDL mock.
     */
    private class SupplicantStaIfaceHidlHalSpy extends SupplicantStaIfaceHalSpy {
        SupplicantStaIfaceHidlHalSpy() {
            super();
        }

        @Override
        protected ISupplicantStaIfaceHal createStaIfaceHalMockable()  {
            return mStaIfaceHalHidlMock;
        }
    }

    /**
     * Implementation of SupplicantStaIfaceHalSpy that creates a null HAL internally.
     */
    private class SupplicantStaIfaceNullHalSpy extends SupplicantStaIfaceHalSpy {
        SupplicantStaIfaceNullHalSpy() {
            super();
        }

        @Override
        protected ISupplicantStaIfaceHal createStaIfaceHalMockable()  {
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new SupplicantStaIfaceHalSpy();
    }

    /**
     * Initialize SupplicantStaIfaceHal with the AIDL implementation.
     */
    private void initializeWithAidlImpl(boolean shouldSucceed) {
        when(mStaIfaceHalAidlMock.initialize()).thenReturn(shouldSucceed);
        assertEquals(shouldSucceed, mDut.initialize());
        verify(mStaIfaceHalAidlMock).initialize();
        verify(mStaIfaceHalHidlMock, never()).initialize();
    }

    /**
     * Initialize SupplicantStaIfaceHal with the HIDL implementation.
     */
    private void initializeWithHidlImpl(boolean shouldSucceed) {
        mDut = new SupplicantStaIfaceHidlHalSpy();
        when(mStaIfaceHalHidlMock.initialize()).thenReturn(shouldSucceed);
        assertEquals(shouldSucceed, mDut.initialize());
        verify(mStaIfaceHalAidlMock, never()).initialize();
        verify(mStaIfaceHalHidlMock).initialize();
    }

    /**
     * Tests successful initialization with the AIDL implementation.
     */
    @Test
    public void testInitSuccessAidl() {
        initializeWithAidlImpl(true);
    }

    /**
     * Tests successful initialization with the HIDL implementation.
     */
    @Test
    public void testInitSuccessHidl() {
        initializeWithHidlImpl(true);
    }

    /**
     * Tests failed initialization with the AIDL implementation.
     */
    @Test
    public void testInitFailureAidl() {
        initializeWithAidlImpl(false);
    }

    /**
     * Tests failed initialization with the HIDL implementation.
     */
    @Test
    public void testInitFailureHidl() {
        initializeWithHidlImpl(false);
    }

    /**
     * Check that initialize() returns false if we receive a null implementation.
     */
    @Test
    public void testInitFailure_null() {
        mDut = new SupplicantStaIfaceNullHalSpy();
        assertFalse(mDut.initialize());
        verify(mStaIfaceHalAidlMock, never()).initialize();
        verify(mStaIfaceHalHidlMock, never()).initialize();
    }

    /**
     * Check that other functions cannot be called if we received a null implementation.
     */
    @Test
    public void testCallAfterNullInitFailure() {
        mDut = new SupplicantStaIfaceNullHalSpy();
        assertFalse(mDut.initialize());
        when(mStaIfaceHalAidlMock.setupIface(anyString())).thenReturn(true);
        assertFalse(mDut.setupIface(IFACE_NAME));
        verify(mStaIfaceHalAidlMock, never()).setupIface(anyString());
    }

    // Now check that we can call all public methods. All of the arguments should get
    // forwarded to the corresponding method in the implementation and we should return
    // the implementation's result.

    /**
     * Test that we can call setupIface
     */
    @Test
    public void testSetupIface() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setupIface(anyString())).thenReturn(true);
        assertTrue(mDut.setupIface(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).setupIface(eq(IFACE_NAME));
    }

    /**
     * Test that we can call teardownIface
     */
    @Test
    public void testTeardownIface() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.teardownIface(anyString())).thenReturn(true);
        assertTrue(mDut.teardownIface(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).teardownIface(eq(IFACE_NAME));
    }

    /**
     * Test that we can call registerDeathHandler
     */
    @Test
    public void testRegisterDeathHandler() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.registerDeathHandler(
                any(WifiNative.SupplicantDeathEventHandler.class))).thenReturn(true);
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));
        verify(mStaIfaceHalAidlMock).registerDeathHandler(eq(mSupplicantHalDeathHandler));
    }

    /**
     * Test that we can call deregisterDeathHandler
     */
    @Test
    public void testDeregisterDeathHandler() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.deregisterDeathHandler()).thenReturn(true);
        assertTrue(mDut.deregisterDeathHandler());
        verify(mStaIfaceHalAidlMock).deregisterDeathHandler();
    }

    /**
     * Test that we can call isInitializationStarted
     */
    @Test
    public void testIsInitializationStarted() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.isInitializationStarted()).thenReturn(true);
        assertTrue(mDut.isInitializationStarted());
        verify(mStaIfaceHalAidlMock).isInitializationStarted();
    }

    /**
     * Test that we can call isInitializationComplete
     */
    @Test
    public void testIsInitializationComplete() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.isInitializationComplete()).thenReturn(true);
        assertTrue(mDut.isInitializationComplete());
        verify(mStaIfaceHalAidlMock).isInitializationComplete();
    }

    /**
     * Test that we can call startDaemon
     */
    @Test
    public void testStartDaemon() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.startDaemon()).thenReturn(true);
        assertTrue(mDut.startDaemon());
        verify(mStaIfaceHalAidlMock).startDaemon();
    }

    /**
     * Test that we can call terminate
     */
    @Test
    public void testTerminate() {
        initializeWithAidlImpl(true);
        doNothing().when(mStaIfaceHalAidlMock).terminate();
        mDut.terminate();
        verify(mStaIfaceHalAidlMock).terminate();
    }

    /**
     * Test that we can call connectToNetwork
     */
    @Test
    public void testConnectToNetwork() {
        initializeWithAidlImpl(true);
        WifiConfiguration testConfig = new WifiConfiguration();
        when(mStaIfaceHalAidlMock.connectToNetwork(anyString(), any(WifiConfiguration.class)))
            .thenReturn(true);
        assertTrue(mDut.connectToNetwork(IFACE_NAME, testConfig));
        verify(mStaIfaceHalAidlMock).connectToNetwork(eq(IFACE_NAME), eq(testConfig));
    }

    /**
     * Test that we can call roamToNetwork
     */
    @Test
    public void testRoamToNetwork() {
        initializeWithAidlImpl(true);
        WifiConfiguration testConfig = mock(WifiConfiguration.class);
        when(mStaIfaceHalAidlMock.roamToNetwork(anyString(), any(WifiConfiguration.class)))
                .thenReturn(true);
        assertTrue(mDut.roamToNetwork(IFACE_NAME, testConfig));
        verify(mStaIfaceHalAidlMock).roamToNetwork(eq(IFACE_NAME), eq(testConfig));
    }

    /**
     * Test that we can call removeNetworkCachedData
     */
    @Test
    public void testRemoveNetworkCachedData() {
        initializeWithAidlImpl(true);
        doNothing().when(mStaIfaceHalAidlMock).removeNetworkCachedData(anyInt());
        mDut.removeNetworkCachedData(NETWORK_ID);
        verify(mStaIfaceHalAidlMock).removeNetworkCachedData(eq(NETWORK_ID));
    }

    /**
     * Test that we can call removeNetworkCachedDataIfNeeded
     */
    @Test
    public void testRemoveNetworkCachedDataIfNeeded() {
        initializeWithAidlImpl(true);
        MacAddress testAddress = MacAddress.fromString(BSSID);
        doNothing().when(mStaIfaceHalAidlMock)
                .removeNetworkCachedDataIfNeeded(anyInt(), any(MacAddress.class));
        mDut.removeNetworkCachedDataIfNeeded(NETWORK_ID, testAddress);
        verify(mStaIfaceHalAidlMock).removeNetworkCachedDataIfNeeded(
                eq(NETWORK_ID), eq(testAddress));
    }

    /**
     * Test that we can call removeAllNetworks
     */
    @Test
    public void testRemoveAllNetworks() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.removeAllNetworks(anyString())).thenReturn(true);
        assertTrue(mDut.removeAllNetworks(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).removeAllNetworks(eq(IFACE_NAME));
    }

    /**
     * Test that we can call disableCurrentNetwork
     */
    @Test
    public void testDisableCurrentNetwork() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.disableCurrentNetwork(anyString())).thenReturn(true);
        assertTrue(mDut.disableCurrentNetwork(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).disableCurrentNetwork(eq(IFACE_NAME));
    }

    /**
     * Test that we can call setCurrentNetworkBssid
     */
    @Test
    public void testSetCurrentNetworkBssid() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setCurrentNetworkBssid(anyString(), anyString()))
                .thenReturn(true);
        assertTrue(mDut.setCurrentNetworkBssid(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).setCurrentNetworkBssid(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call getCurrentNetworkWpsNfcConfigurationToken
     */
    @Test
    public void testGetCurrentNetworkWpsNfcConfigurationToken() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.getCurrentNetworkWpsNfcConfigurationToken(anyString()))
                .thenReturn(RESPONSE);
        assertEquals(RESPONSE, mDut.getCurrentNetworkWpsNfcConfigurationToken(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getCurrentNetworkWpsNfcConfigurationToken(eq(IFACE_NAME));
    }

    /**
     * Test that we can call getCurrentNetworkEapAnonymousIdentity
     */
    @Test
    public void testGetCurrentNetworkEapAnonymousIdentity() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.getCurrentNetworkEapAnonymousIdentity(anyString()))
                .thenReturn(RESPONSE);
        assertEquals(RESPONSE, mDut.getCurrentNetworkEapAnonymousIdentity(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getCurrentNetworkEapAnonymousIdentity(eq(IFACE_NAME));
    }

    /**
     * Test that we can call sendCurrentNetworkEapIdentityResponse
     */
    @Test
    public void testSendCurrentNetworkEapIdentityResponse() {
        initializeWithAidlImpl(true);
        String identity = "blah@blah.com";
        String encryptedIdentity = "blah2@blah.com";
        when(mStaIfaceHalAidlMock.sendCurrentNetworkEapIdentityResponse(
                anyString(), anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.sendCurrentNetworkEapIdentityResponse(
                IFACE_NAME, identity, encryptedIdentity));
        verify(mStaIfaceHalAidlMock).sendCurrentNetworkEapIdentityResponse(
                eq(IFACE_NAME), eq(identity), eq(encryptedIdentity));
    }

    /**
     * Test that we can call sendCurrentNetworkEapSimGsmAuthResponse
     */
    @Test
    public void testSendCurrentNetworkEapSimGsmAuthResponse() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.sendCurrentNetworkEapSimGsmAuthResponse(anyString(), anyString()))
                .thenReturn(true);
        assertTrue(mDut.sendCurrentNetworkEapSimGsmAuthResponse(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).sendCurrentNetworkEapSimGsmAuthResponse(
                eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call sendCurrentNetworkEapSimGsmAuthFailure
     */
    @Test
    public void testSendCurrentNetworkEapSimGsmAuthFailure() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.sendCurrentNetworkEapSimGsmAuthFailure(anyString()))
                .thenReturn(true);
        assertTrue(mDut.sendCurrentNetworkEapSimGsmAuthFailure(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).sendCurrentNetworkEapSimGsmAuthFailure(eq(IFACE_NAME));
    }

    /**
     * Test that we can call sendCurrentNetworkEapSimUmtsAuthResponse
     */
    @Test
    public void testSendCurrentNetworkEapSimUmtsAuthResponse() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.sendCurrentNetworkEapSimUmtsAuthResponse(
                anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.sendCurrentNetworkEapSimUmtsAuthResponse(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).sendCurrentNetworkEapSimUmtsAuthResponse(
                eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call sendCurrentNetworkEapSimUmtsAutsResponse
     */
    @Test
    public void testSendCurrentNetworkEapSimUmtsAutsResponse() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.sendCurrentNetworkEapSimUmtsAutsResponse(
                anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.sendCurrentNetworkEapSimUmtsAutsResponse(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).sendCurrentNetworkEapSimUmtsAutsResponse(
                eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call sendCurrentNetworkEapSimUmtsAuthFailure
     */
    @Test
    public void testSendCurrentNetworkEapSimUmtsAuthFailure() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.sendCurrentNetworkEapSimUmtsAuthFailure(anyString()))
                .thenReturn(true);
        assertTrue(mDut.sendCurrentNetworkEapSimUmtsAuthFailure(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).sendCurrentNetworkEapSimUmtsAuthFailure(eq(IFACE_NAME));
    }

    /**
     * Test that we can call setWpsDeviceName
     */
    @Test
    public void testSetWpsDeviceName() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsDeviceName(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsDeviceName(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsDeviceName(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call setWpsDeviceType
     */
    @Test
    public void testSetWpsDeviceType() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsDeviceType(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsDeviceType(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsDeviceType(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call setWpsManufacturer
     */
    @Test
    public void testSetWpsManufacturer() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsManufacturer(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsManufacturer(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsManufacturer(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call setWpsModelName
     */
    @Test
    public void testSetWpsModelName() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsModelName(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsModelName(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsModelName(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call setWpsModelNumber
     */
    @Test
    public void testSetWpsModelNumber() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsModelNumber(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsModelNumber(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsModelNumber(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call setWpsSerialNumber
     */
    @Test
    public void testSetWpsSerialNumber() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsSerialNumber(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsSerialNumber(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsSerialNumber(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call setWpsConfigMethods
     */
    @Test
    public void testSetWpsConfigMethods() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setWpsConfigMethods(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.setWpsConfigMethods(IFACE_NAME, PARAMS));
        verify(mStaIfaceHalAidlMock).setWpsConfigMethods(eq(IFACE_NAME), eq(PARAMS));
    }

    /**
     * Test that we can call reassociate
     */
    @Test
    public void testReassociate() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.reassociate(anyString())).thenReturn(true);
        assertTrue(mDut.reassociate(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).reassociate(eq(IFACE_NAME));
    }

    /**
     * Test that we can call reconnect
     */
    @Test
    public void testReconnect() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.reconnect(anyString())).thenReturn(true);
        assertTrue(mDut.reconnect(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).reconnect(eq(IFACE_NAME));
    }

    /**
     * Test that we can call disconnect
     */
    @Test
    public void testDisconnect() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.disconnect(anyString())).thenReturn(true);
        assertTrue(mDut.disconnect(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).disconnect(eq(IFACE_NAME));
    }

    /**
     * Test that we can call setPowerSave
     */
    @Test
    public void testSetPowerSave() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setPowerSave(anyString(), anyBoolean())).thenReturn(true);
        assertTrue(mDut.setPowerSave(IFACE_NAME, ENABLE));
        verify(mStaIfaceHalAidlMock).setPowerSave(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call initiateTdlsDiscover
     */
    @Test
    public void testInitiateTdlsDiscover() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.initiateTdlsDiscover(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.initiateTdlsDiscover(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).initiateTdlsDiscover(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call initiateTdlsSetup
     */
    @Test
    public void testInitiateTdlsSetup() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.initiateTdlsSetup(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.initiateTdlsSetup(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).initiateTdlsSetup(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call initiateTdlsTeardown
     */
    @Test
    public void testInitiateTdlsTeardown() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.initiateTdlsTeardown(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.initiateTdlsTeardown(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).initiateTdlsTeardown(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call initiateAnqpQuery
     */
    @Test
    public void testInitiateAnqpQuery() {
        initializeWithAidlImpl(true);
        ArrayList<Short> infoElements = new ArrayList<>();
        ArrayList<Integer> hs20SubTypes = new ArrayList<>();
        when(mStaIfaceHalAidlMock.initiateAnqpQuery(anyString(), anyString(),
                any(ArrayList.class), any(ArrayList.class))).thenReturn(true);
        assertTrue(mDut.initiateAnqpQuery(IFACE_NAME, BSSID, infoElements, hs20SubTypes));
        verify(mStaIfaceHalAidlMock).initiateAnqpQuery(
                eq(IFACE_NAME), eq(BSSID), eq(infoElements), eq(hs20SubTypes));
    }

    /**
     * Test that we can call initiateVenueUrlAnqpQuery
     */
    @Test
    public void testInitiateVenueUrlAnqpQuery() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.initiateVenueUrlAnqpQuery(anyString(), anyString()))
                .thenReturn(true);
        assertTrue(mDut.initiateVenueUrlAnqpQuery(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).initiateVenueUrlAnqpQuery(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call initiateHs20IconQuery
     */
    @Test
    public void testInitiateHs20IconQuery() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.initiateHs20IconQuery(anyString(), anyString(), anyString()))
                .thenReturn(true);
        assertTrue(mDut.initiateHs20IconQuery(IFACE_NAME, BSSID, PARAMS));
        verify(mStaIfaceHalAidlMock).initiateHs20IconQuery(eq(IFACE_NAME), eq(BSSID), eq(PARAMS));
    }

    /**
     * Test that we can call getMacAddress
     */
    @Test
    public void testGetMacAddress() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.getMacAddress(anyString())).thenReturn(BSSID);
        assertEquals(BSSID, mDut.getMacAddress(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getMacAddress(eq(IFACE_NAME));
    }

    /**
     * Test that we can call startRxFilter
     */
    @Test
    public void testStartRxFilter() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.startRxFilter(anyString())).thenReturn(true);
        assertTrue(mDut.startRxFilter(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).startRxFilter(eq(IFACE_NAME));
    }

    /**
     * Test that we can call stopRxFilter
     */
    @Test
    public void testStopRxFilter() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.stopRxFilter(anyString())).thenReturn(true);
        assertTrue(mDut.stopRxFilter(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).stopRxFilter(eq(IFACE_NAME));
    }

    /**
     * Test that we can call addRxFilter
     */
    @Test
    public void testAddRxFilter() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.addRxFilter(anyString(), anyInt())).thenReturn(true);
        assertTrue(mDut.addRxFilter(IFACE_NAME, MODE));
        verify(mStaIfaceHalAidlMock).addRxFilter(eq(IFACE_NAME), eq(MODE));
    }

    /**
     * Test that we can call removeRxFilter
     */
    @Test
    public void testRemoveRxFilter() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.removeRxFilter(anyString(), anyInt())).thenReturn(true);
        assertTrue(mDut.removeRxFilter(IFACE_NAME, MODE));
        verify(mStaIfaceHalAidlMock).removeRxFilter(eq(IFACE_NAME), eq(MODE));
    }

    /**
     * Test that we can call setBtCoexistenceMode
     */
    @Test
    public void testSetBtCoexistenceMode() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setBtCoexistenceMode(anyString(), anyInt())).thenReturn(true);
        assertTrue(mDut.setBtCoexistenceMode(IFACE_NAME, MODE));
        verify(mStaIfaceHalAidlMock).setBtCoexistenceMode(eq(IFACE_NAME), eq(MODE));
    }

    /**
     * Test that we can call setBtCoexistenceScanModeEnabled
     */
    @Test
    public void testSetBtCoexistenceScanModeEnabled() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setBtCoexistenceScanModeEnabled(anyString(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mDut.setBtCoexistenceScanModeEnabled(IFACE_NAME, ENABLE));
        verify(mStaIfaceHalAidlMock).setBtCoexistenceScanModeEnabled(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call setSuspendModeEnabled
     */
    @Test
    public void testSetSuspendModeEnabled() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setSuspendModeEnabled(anyString(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mDut.setSuspendModeEnabled(IFACE_NAME, ENABLE));
        verify(mStaIfaceHalAidlMock).setSuspendModeEnabled(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call setCountryCode
     */
    @Test
    public void testSetCountryCode() {
        initializeWithAidlImpl(true);
        String countryCode = "MX";
        when(mStaIfaceHalAidlMock.setCountryCode(anyString(), anyString()))
                .thenReturn(true);
        assertTrue(mDut.setCountryCode(IFACE_NAME, countryCode));
        verify(mStaIfaceHalAidlMock).setCountryCode(eq(IFACE_NAME), eq(countryCode));
    }

    /**
     * Test that we can call flushAllHlp
     */
    @Test
    public void testFlushAllHlp() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.flushAllHlp(anyString())).thenReturn(true);
        assertTrue(mDut.flushAllHlp(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).flushAllHlp(eq(IFACE_NAME));
    }

    /**
     * Test that we can call addHlpReq
     */
    @Test
    public void addHlpReq() {
        initializeWithAidlImpl(true);
        byte[] dstAddr = {0x45, 0x23, 0x12, 0x12, 0x12, 0x45};
        byte[] hlpPacket = {0x00, 0x01, 0x02, 0x03, 0x04, 0x12, 0x15, 0x34, 0x55, 0x12,
                0x12, 0x45, 0x23, 0x52, 0x32, 0x16, 0x15, 0x53, 0x62, 0x32, 0x32, 0x10};
        when(mStaIfaceHalAidlMock.addHlpReq(anyString(), any(byte[].class), any(byte[].class)))
                .thenReturn(true);
        assertTrue(mDut.addHlpReq(IFACE_NAME, dstAddr, hlpPacket));
        verify(mStaIfaceHalAidlMock).addHlpReq(eq(IFACE_NAME), eq(dstAddr), eq(hlpPacket));
    }

    /**
     * Test that we can call startWpsRegistrar
     */
    @Test
    public void testStartWpsRegistrar() {
        initializeWithAidlImpl(true);
        String pin = "5678";
        when(mStaIfaceHalAidlMock.startWpsRegistrar(anyString(), anyString(), anyString()))
                .thenReturn(true);
        assertTrue(mDut.startWpsRegistrar(IFACE_NAME, BSSID, pin));
        verify(mStaIfaceHalAidlMock).startWpsRegistrar(eq(IFACE_NAME), eq(BSSID), eq(pin));
    }

    /**
     * Test that we can call startWpsPbc
     */
    @Test
    public void testStartWpsPbc() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.startWpsPbc(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.startWpsPbc(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).startWpsPbc(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call startWpsPinKeypad
     */
    @Test
    public void testStartWpsPinKeypad() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.startWpsPinKeypad(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.startWpsPinKeypad(IFACE_NAME, PIN));
        verify(mStaIfaceHalAidlMock).startWpsPinKeypad(eq(IFACE_NAME), eq(PIN));
    }

    /**
     * Test that we can call startWpsPinDisplay
     */
    @Test
    public void testStartWpsPinDisplay() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.startWpsPinDisplay(anyString(), anyString())).thenReturn(PIN);
        assertEquals(PIN, mDut.startWpsPinDisplay(IFACE_NAME, BSSID));
        verify(mStaIfaceHalAidlMock).startWpsPinDisplay(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call cancelWps
     */
    @Test
    public void testCancelWps() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.cancelWps(anyString())).thenReturn(true);
        assertTrue(mDut.cancelWps(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).cancelWps(eq(IFACE_NAME));
    }

    /**
     * Test that we can call setExternalSim
     */
    @Test
    public void testSetExternalSim() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setExternalSim(anyString(), anyBoolean())).thenReturn(true);
        assertTrue(mDut.setExternalSim(IFACE_NAME, ENABLE));
        verify(mStaIfaceHalAidlMock).setExternalSim(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call enableAutoReconnect
     */
    @Test
    public void testEnableAutoReconnect() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.enableAutoReconnect(anyString(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mDut.enableAutoReconnect(IFACE_NAME, ENABLE));
        verify(mStaIfaceHalAidlMock).enableAutoReconnect(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call setLogLevel
     */
    @Test
    public void testSetLogLevel() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setLogLevel(anyBoolean())).thenReturn(true);
        assertTrue(mDut.setLogLevel(ENABLE));
        verify(mStaIfaceHalAidlMock).setLogLevel(eq(ENABLE));
    }

    /**
     * Test that we can call setConcurrencyPriority
     */
    @Test
    public void testSetConcurrencyPriority() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setConcurrencyPriority(anyBoolean())).thenReturn(true);
        assertTrue(mDut.setConcurrencyPriority(ENABLE));
        verify(mStaIfaceHalAidlMock).setConcurrencyPriority(eq(ENABLE));
    }

    /**
     * Test that we can call getAdvancedCapabilities
     */
    @Test
    public void testGetAdvancedCapabilities() {
        initializeWithAidlImpl(true);
        long capabilities = 0X1234;
        when(mStaIfaceHalAidlMock.getAdvancedCapabilities(anyString())).thenReturn(capabilities);
        assertEquals(capabilities, mDut.getAdvancedCapabilities(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getAdvancedCapabilities(eq(IFACE_NAME));
    }

    /**
     * Test that we can call getWpaDriverFeatureSet
     */
    @Test
    public void testGetWpaDriverFeatureSet() {
        initializeWithAidlImpl(true);
        long capabilities = 0X1234;
        when(mStaIfaceHalAidlMock.getWpaDriverFeatureSet(anyString())).thenReturn(capabilities);
        assertEquals(capabilities, mDut.getWpaDriverFeatureSet(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getWpaDriverFeatureSet(eq(IFACE_NAME));
    }

    /**
     * Test that we can call getConnectionCapabilities
     */
    @Test
    public void testGetConnectionCapabilities() {
        initializeWithAidlImpl(true);
        WifiNative.ConnectionCapabilities capabilities =
                mock(WifiNative.ConnectionCapabilities.class);
        when(mStaIfaceHalAidlMock.getConnectionCapabilities(anyString())).thenReturn(capabilities);
        assertEquals(capabilities, mDut.getConnectionCapabilities(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getConnectionCapabilities(eq(IFACE_NAME));
    }

    /**
     * Test that we can call addDppPeerUri
     */
    @Test
    public void testAddDppPeerUri() {
        initializeWithAidlImpl(true);
        String uri = "/blah";
        when(mStaIfaceHalAidlMock.addDppPeerUri(anyString(), anyString())).thenReturn(NETWORK_ID);
        assertEquals(NETWORK_ID, mDut.addDppPeerUri(IFACE_NAME, uri));
        verify(mStaIfaceHalAidlMock).addDppPeerUri(eq(IFACE_NAME), eq(uri));
    }

    /**
     * Test that we can call removeDppUri
     */
    @Test
    public void testRemoveDppUri() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.removeDppUri(anyString(), anyInt())).thenReturn(true);
        assertTrue(mDut.removeDppUri(IFACE_NAME, NETWORK_ID));
        verify(mStaIfaceHalAidlMock).removeDppUri(eq(IFACE_NAME), eq(NETWORK_ID));
    }

    /**
     * Test that we can call stopDppInitiator
     */
    @Test
    public void testStopDppInitiator() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.stopDppInitiator(anyString())).thenReturn(true);
        assertTrue(mDut.stopDppInitiator(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).stopDppInitiator(eq(IFACE_NAME));
    }

    /**
     * Test that we can call startDppConfiguratorInitiator
     */
    @Test
    public void testStartDppConfiguratorInitiator() {
        initializeWithAidlImpl(true);
        int netRole = 1;
        int securityAkm = 2;
        String ssid = "someSsid";
        String password = "somePassword";
        String psk = "somePsk";
        String sKey = "3077020101042088a442d945b0c2fcd6346e4b47dd5cd1abebcc3b251"
                + "a2e6a615111d918b3e749a00a06082a8648ce3d030107a14403420004d34506c1c2fd500c38768b"
                + "76293cb208f203cc92b42976c31e1b51914c5200400b521ef3f608a163875c203b34430ad4aa52d"
                + "b3e95eacb7481782328d4fb45af";
        byte[] key = HexEncoding.decode(sKey.toCharArray(), false);
        when(mStaIfaceHalAidlMock.startDppConfiguratorInitiator(anyString(), anyInt(), anyInt(),
                anyString(), anyString(), anyString(), anyInt(), anyInt(),
                any(byte[].class))).thenReturn(true);
        assertTrue(mDut.startDppConfiguratorInitiator(IFACE_NAME, PEER_ID, OWN_ID, ssid,
                password, psk, netRole, securityAkm, key));
        verify(mStaIfaceHalAidlMock).startDppConfiguratorInitiator(eq(IFACE_NAME), eq(PEER_ID),
                eq(OWN_ID), eq(ssid), eq(password), eq(psk), eq(netRole), eq(securityAkm), eq(key));
    }

    /**
     * Test that we can call startDppEnrolleeInitiator
     */
    @Test
    public void testStartDppEnrolleeInitiator() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.startDppEnrolleeInitiator(anyString(), anyInt(), anyInt()))
                .thenReturn(true);
        assertTrue(mDut.startDppEnrolleeInitiator(IFACE_NAME, PEER_ID, OWN_ID));
        verify(mStaIfaceHalAidlMock).startDppEnrolleeInitiator(
                eq(IFACE_NAME), eq(PEER_ID), eq(OWN_ID));
    }

    /**
     * Test that we can call generateDppBootstrapInfoForResponder
     */
    @Test
    public void testGenerateDppBootstrapInfoForResponder() {
        initializeWithAidlImpl(true);
        WifiNative.DppBootstrapQrCodeInfo qrCodeInfo = new WifiNative.DppBootstrapQrCodeInfo();
        when(mStaIfaceHalAidlMock.generateDppBootstrapInfoForResponder(anyString(), anyString(),
                anyString(), anyInt())).thenReturn(qrCodeInfo);
        assertEquals(qrCodeInfo, mDut.generateDppBootstrapInfoForResponder(
                IFACE_NAME, BSSID, PARAMS, MODE));
        verify(mStaIfaceHalAidlMock).generateDppBootstrapInfoForResponder(
                eq(IFACE_NAME), eq(BSSID), eq(PARAMS), eq(MODE));
    }

    /**
     * Test that we can call startDppEnrolleeResponder
     */
    @Test
    public void startDppEnrolleeResponder() {
        initializeWithAidlImpl(true);
        int listenChannel = 5;
        when(mStaIfaceHalAidlMock.startDppEnrolleeResponder(anyString(), anyInt()))
                .thenReturn(true);
        assertTrue(mDut.startDppEnrolleeResponder(IFACE_NAME, listenChannel));
        verify(mStaIfaceHalAidlMock).startDppEnrolleeResponder(eq(IFACE_NAME), eq(listenChannel));
    }

    /**
     * Test that we can call stopDppResponder
     */
    @Test
    public void testStopDppResponder() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.stopDppResponder(anyString(), anyInt())).thenReturn(true);
        assertTrue(mDut.stopDppResponder(IFACE_NAME, NETWORK_ID));
        verify(mStaIfaceHalAidlMock).stopDppResponder(eq(IFACE_NAME), eq(NETWORK_ID));
    }

    /**
     * Test that we can call registerDppCallback
     */
    @Test
    public void registerDppCallback() {
        initializeWithAidlImpl(true);
        WifiNative.DppEventCallback dppCallback = mock(WifiNative.DppEventCallback.class);
        doNothing().when(mStaIfaceHalAidlMock).registerDppCallback(
                any(WifiNative.DppEventCallback.class));
        mDut.registerDppCallback(dppCallback);
        verify(mStaIfaceHalAidlMock).registerDppCallback(dppCallback);
    }

    /**
     * Test that we can call setMboCellularDataStatus
     */
    @Test
    public void testSetMboCellularDataStatus() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setMboCellularDataStatus(anyString(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mDut.setMboCellularDataStatus(IFACE_NAME, ENABLE));
        verify(mStaIfaceHalAidlMock).setMboCellularDataStatus(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call updateOnLinkedNetworkRoaming
     */
    @Test
    public void testUpdateOnLinkedNetworkRoaming() {
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.updateOnLinkedNetworkRoaming(anyString(), anyInt(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mDut.updateOnLinkedNetworkRoaming(IFACE_NAME, NETWORK_ID, ENABLE));
        verify(mStaIfaceHalAidlMock).updateOnLinkedNetworkRoaming(
                eq(IFACE_NAME), eq(NETWORK_ID), eq(ENABLE));
    }

    /**
     * Test that we can call updateLinkedNetworks
     */
    @Test
    public void testUpdateLinkedNetworks() {
        initializeWithAidlImpl(true);
        Map<String, WifiConfiguration> linkedConfigurations =
                new HashMap<String, WifiConfiguration>();
        when(mStaIfaceHalAidlMock.updateLinkedNetworks(anyString(), anyInt(), any(Map.class)))
                .thenReturn(true);
        assertTrue(mDut.updateLinkedNetworks(IFACE_NAME, NETWORK_ID, linkedConfigurations));
        verify(mStaIfaceHalAidlMock).updateLinkedNetworks(
                eq(IFACE_NAME), eq(NETWORK_ID), eq(linkedConfigurations));
    }

    /**
     * Test that we can call getCurrentNetworkSecurityParams
     */
    @Test
    public void testGetCurrentNetworkSecurityParams() {
        initializeWithAidlImpl(true);
        SecurityParams params = mock(SecurityParams.class);
        when(mStaIfaceHalAidlMock.getCurrentNetworkSecurityParams(anyString())).thenReturn(params);
        assertEquals(params, mDut.getCurrentNetworkSecurityParams(IFACE_NAME));
        verify(mStaIfaceHalAidlMock).getCurrentNetworkSecurityParams(eq(IFACE_NAME));
    }

    /*
     * Test the creation of a valid QosPolicyRequest object.
     */
    @Test
    public void testCreateValidQosPolicyRequest() {
        byte[] srcIp = new byte[]{127, 0, 0, 1};
        int[] dstPortRange = new int[]{131, 250};
        QosPolicyRequest request = new QosPolicyRequest(QOS_POLICY_ID, QOS_POLICY_REQUEST_TYPE,
                QOS_POLICY_DSCP, new QosPolicyClassifierParams(true, srcIp, false, null,
                        QOS_POLICY_SRC_PORT, dstPortRange, QOS_POLICY_PROTOCOL));
        assertEquals(QOS_POLICY_ID, request.policyId);
        assertEquals(QOS_POLICY_DSCP, request.dscp);
        assertTrue(request.isAddRequest());
        assertFalse(request.isRemoveRequest());

        assertTrue(request.classifierParams.isValid);
        assertTrue(request.classifierParams.hasSrcIp);
        assertFalse(request.classifierParams.hasDstIp);

        assertEquals(QOS_POLICY_SRC_PORT, request.classifierParams.srcPort);
        assertEquals(QOS_POLICY_PROTOCOL, request.classifierParams.protocol);
        assertEquals(new Range(dstPortRange[0], dstPortRange[1]),
                request.classifierParams.dstPortRange);
        assertTrue(Arrays.equals(srcIp, request.classifierParams.srcIp.getAddress()));
    }

    /*
     * Test that a QosPolicyRequest object is marked as invalid if an invalid
     * srcIp is passed in during construction.
     */
    @Test
    public void testCreateQosPolicyRequestWithInvalidSrcIp() {
        byte[] srcIp = new byte[]{53};
        QosPolicyRequest request = new QosPolicyRequest(QOS_POLICY_ID, QOS_POLICY_REQUEST_TYPE,
                QOS_POLICY_DSCP, new QosPolicyClassifierParams(true, srcIp, false, null,
                QOS_POLICY_SRC_PORT, null, QOS_POLICY_PROTOCOL));
        assertEquals(QOS_POLICY_ID, request.policyId);
        assertEquals(QOS_POLICY_DSCP, request.dscp);
        assertTrue(request.isAddRequest());
        assertFalse(request.isRemoveRequest());
        assertFalse(request.classifierParams.isValid);
    }

    /*
     * Test that a QosPolicyRequest object is marked as invalid if an invalid
     * dstIp is passed in during construction.
     */
    @Test
    public void testCreateQosPolicyRequestWithInvalidDstIp() {
        byte[] dstIp = new byte[]{53};
        QosPolicyRequest request = new QosPolicyRequest(QOS_POLICY_ID, QOS_POLICY_REQUEST_TYPE,
                QOS_POLICY_DSCP, new QosPolicyClassifierParams(false, null, true, dstIp,
                QOS_POLICY_SRC_PORT, null, QOS_POLICY_PROTOCOL));
        assertEquals(QOS_POLICY_ID, request.policyId);
        assertEquals(QOS_POLICY_DSCP, request.dscp);
        assertTrue(request.isAddRequest());
        assertFalse(request.isRemoveRequest());
        assertFalse(request.classifierParams.isValid);
    }

    /*
     * Test that a QosPolicyRequest object is marked as invalid if an invalid
     * dstPortRange is passed in during construction.
     */
    @Test
    public void testCreateQosPolicyRequestWithInvalidDstPortRange() {
        int[] dstPortRange = new int[]{250, 131};
        QosPolicyRequest request = new QosPolicyRequest(QOS_POLICY_ID, QOS_POLICY_REQUEST_TYPE,
                QOS_POLICY_DSCP, new QosPolicyClassifierParams(false, null, false, null,
                QOS_POLICY_SRC_PORT, dstPortRange, QOS_POLICY_PROTOCOL));
        assertEquals(QOS_POLICY_ID, request.policyId);
        assertEquals(QOS_POLICY_DSCP, request.dscp);
        assertTrue(request.isAddRequest());
        assertFalse(request.isRemoveRequest());
        assertFalse(request.classifierParams.isValid);
    }

    private void verifySetEapAnonymousIdentity(boolean updateToNativeService) {
        final String anonymousIdentity = "abc@realm.net";
        initializeWithAidlImpl(true);
        when(mStaIfaceHalAidlMock.setEapAnonymousIdentity(anyString(), anyString(), anyBoolean()))
                .thenReturn(true);
        assertTrue(mDut.setEapAnonymousIdentity(IFACE_NAME, anonymousIdentity,
                updateToNativeService));
        verify(mStaIfaceHalAidlMock).setEapAnonymousIdentity(eq(IFACE_NAME), eq(anonymousIdentity),
                eq(updateToNativeService));
    }

    /**
     * Test that we can call setEapAnonymousIdentity
     */
    @Test
    public void testSetEapAnonymousIdentity() {
        verifySetEapAnonymousIdentity(true);
    }

    /**
     * Test that we can call setEapAnonymousIdentity
     */
    @Test
    public void testSetEapAnonymousIdentityWithNotUpdateToNativeService() {
        verifySetEapAnonymousIdentity(false);
    }
}
