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

package com.android.server.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for SupplicantP2pIfaceHal, which functions as a wrapper for either
 * SupplicantP2pIfaceHalHidlImpl or SupplicantP2pIfaceHalAidlImpl, depending on
 * which service (HIDL or AIDL) is available. Test the initialization logic and
 * verify that calls to all public methods are forwarded to the actual implementation.
 */
public class SupplicantP2pIfaceHalTest extends WifiBaseTest {
    private SupplicantP2pIfaceHalSpy mDut;
    private @Mock SupplicantP2pIfaceHalHidlImpl mP2pIfaceHalHidlMock;
    private @Mock SupplicantP2pIfaceHalAidlImpl mP2pIfaceHalAidlMock;
    private @Mock WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;
    private @Mock WifiP2pMonitor mMonitor;
    private @Mock WifiGlobals mWifiGlobals;
    private @Mock WifiInjector mWifiInjector;

    private static final String IFACE_NAME = "wlan0";
    private static final String BSSID = "fa:45:23:23:12:12";
    private static final String PARAMS = "blahblah";
    private static final String RESPONSE = "blahblahblah";
    private static final String PIN = "5678";
    private static final boolean ENABLE = true;
    private static final int NETWORK_ID = 2;
    private static final int CHANNEL = 3;

    private class SupplicantP2pIfaceHalSpy extends SupplicantP2pIfaceHal {
        SupplicantP2pIfaceHalSpy() {
            super(mMonitor, mWifiGlobals, mWifiInjector);
        }

        @Override
        protected ISupplicantP2pIfaceHal createP2pIfaceHalMockable()  {
            return mP2pIfaceHalAidlMock;
        }
    }

    /**
     * Implementation of SupplicantP2pIfaceHalSpy that uses the HIDL mock internally
     * rather than the default AIDL mock.
     */
    private class SupplicantP2pIfaceHidlHalSpy extends SupplicantP2pIfaceHalSpy {
        SupplicantP2pIfaceHidlHalSpy() {
            super();
        }

        @Override
        protected ISupplicantP2pIfaceHal createP2pIfaceHalMockable()  {
            return mP2pIfaceHalHidlMock;
        }
    }

    /**
     * Implementation of SupplicantP2pIfaceHalSpy that creates a null HAL internally.
     */
    private class SupplicantP2pIfaceNullHalSpy extends SupplicantP2pIfaceHalSpy {
        SupplicantP2pIfaceNullHalSpy() {
            super();
        }

        @Override
        protected ISupplicantP2pIfaceHal createP2pIfaceHalMockable()  {
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new SupplicantP2pIfaceHalSpy();
    }

    /**
     * Initialize SupplicantP2pIfaceHal with the AIDL implementation.
     */
    private void initializeWithAidlImpl(boolean shouldSucceed) {
        when(mP2pIfaceHalAidlMock.initialize()).thenReturn(shouldSucceed);
        assertEquals(shouldSucceed, mDut.initialize());
        verify(mP2pIfaceHalAidlMock).initialize();
        verify(mP2pIfaceHalHidlMock, never()).initialize();
    }

    /**
     * Initialize SupplicantP2pIfaceHal with the HIDL implementation.
     */
    private void initializeWithHidlImpl(boolean shouldSucceed) {
        mDut = new SupplicantP2pIfaceHidlHalSpy();
        when(mP2pIfaceHalHidlMock.initialize()).thenReturn(shouldSucceed);
        assertEquals(shouldSucceed, mDut.initialize());
        verify(mP2pIfaceHalAidlMock, never()).initialize();
        verify(mP2pIfaceHalHidlMock).initialize();
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
        mDut = new SupplicantP2pIfaceNullHalSpy();
        assertFalse(mDut.initialize());
        verify(mP2pIfaceHalAidlMock, never()).initialize();
        verify(mP2pIfaceHalHidlMock, never()).initialize();
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
        when(mP2pIfaceHalAidlMock.setupIface(anyString())).thenReturn(true);
        assertTrue(mDut.setupIface(IFACE_NAME));
        verify(mP2pIfaceHalAidlMock).setupIface(eq(IFACE_NAME));
    }

    /**
     * Test that we can call teardownIface
     */
    @Test
    public void testTeardownIface() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.teardownIface(anyString())).thenReturn(true);
        assertTrue(mDut.teardownIface(IFACE_NAME));
        verify(mP2pIfaceHalAidlMock).teardownIface(eq(IFACE_NAME));
    }

    /**
     * Test that we can call isInitializationStarted
     */
    @Test
    public void testIsInitializationStarted() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.isInitializationStarted()).thenReturn(true);
        assertTrue(mDut.isInitializationStarted());
        verify(mP2pIfaceHalAidlMock).isInitializationStarted();
    }

    /**
     * Test that we can call isInitializationComplete
     */
    @Test
    public void testIsInitializationComplete() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.isInitializationComplete()).thenReturn(true);
        assertTrue(mDut.isInitializationComplete());
        verify(mP2pIfaceHalAidlMock).isInitializationComplete();
    }

    /**
     * Test that we can call find with timeout
     */
    @Test
    public void testFind() {
        initializeWithAidlImpl(true);
        int timeout = 5;
        when(mP2pIfaceHalAidlMock.find(anyInt())).thenReturn(true);
        assertTrue(mDut.find(timeout));
        verify(mP2pIfaceHalAidlMock).find(eq(timeout));
    }

    /**
     * Test that we can call find with {@link WifiP2pManager#WifiP2pScanType}
     */
    @Test
    public void testFindWithType() {
        initializeWithAidlImpl(true);
        int scanType = WifiP2pManager.WIFI_P2P_SCAN_FULL;
        int freq = WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED;
        int timeout = 5;
        when(mP2pIfaceHalAidlMock.find(anyInt(), anyInt(), anyInt())).thenReturn(true);
        assertTrue(mDut.find(scanType, freq, timeout));
        verify(mP2pIfaceHalAidlMock).find(eq(scanType), eq(freq), eq(timeout));
    }

    /**
     * Test that we can call stopFind
     */
    @Test
    public void testStopFind() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.stopFind()).thenReturn(true);
        assertTrue(mDut.stopFind());
        verify(mP2pIfaceHalAidlMock).stopFind();
    }

    /**
     * Test that we can call stopFind
     */
    @Test
    public void testFlush() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.flush()).thenReturn(true);
        assertTrue(mDut.flush());
        verify(mP2pIfaceHalAidlMock).flush();
    }

    /**
     * Test that we can call serviceFlush
     */
    @Test
    public void testServiceFlush() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.serviceFlush()).thenReturn(true);
        assertTrue(mDut.serviceFlush());
        verify(mP2pIfaceHalAidlMock).serviceFlush();
    }

    /**
     * Test that we can call setPowerSave
     */
    @Test
    public void testSetPowerSave() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setPowerSave(anyString(), anyBoolean())).thenReturn(true);
        assertTrue(mDut.setPowerSave(IFACE_NAME, ENABLE));
        verify(mP2pIfaceHalAidlMock).setPowerSave(eq(IFACE_NAME), eq(ENABLE));
    }

    /**
     * Test that we can call setGroupIdle
     */
    @Test
    public void testSetGroupIdle() {
        initializeWithAidlImpl(true);
        int timeout = 5;
        when(mP2pIfaceHalAidlMock.setGroupIdle(anyString(), anyInt())).thenReturn(true);
        assertTrue(mDut.setGroupIdle(IFACE_NAME, timeout));
        verify(mP2pIfaceHalAidlMock).setGroupIdle(eq(IFACE_NAME), eq(timeout));
    }

    /**
     * Test that we can call setSsidPostfix
     */
    @Test
    public void testSetSsidPostfix() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setSsidPostfix(anyString())).thenReturn(true);
        assertTrue(mDut.setSsidPostfix(PARAMS));
        verify(mP2pIfaceHalAidlMock).setSsidPostfix(eq(PARAMS));
    }

    /**
     * Test that we can call connect
     */
    @Test
    public void testConnect() {
        initializeWithAidlImpl(true);
        WifiP2pConfig config = mock(WifiP2pConfig.class);
        when(mP2pIfaceHalAidlMock.connect(any(WifiP2pConfig.class), anyBoolean())).thenReturn(PIN);
        assertEquals(PIN, mDut.connect(config, ENABLE));
        verify(mP2pIfaceHalAidlMock).connect(eq(config), eq(ENABLE));
    }

    /**
     * Test that we can call cancelConnect
     */
    @Test
    public void testCancelConnect() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.cancelConnect()).thenReturn(true);
        assertTrue(mDut.cancelConnect());
        verify(mP2pIfaceHalAidlMock).cancelConnect();
    }

    /**
     * Test that we can call provisionDiscovery
     */
    @Test
    public void testProvisionDiscovery() {
        initializeWithAidlImpl(true);
        WifiP2pConfig config = mock(WifiP2pConfig.class);
        when(mP2pIfaceHalAidlMock.provisionDiscovery(any(WifiP2pConfig.class))).thenReturn(true);
        assertTrue(mDut.provisionDiscovery(config));
        verify(mP2pIfaceHalAidlMock).provisionDiscovery(eq(config));
    }

    /**
     * Test that we can call invite
     */
    @Test
    public void testInvite() {
        initializeWithAidlImpl(true);
        WifiP2pGroup group = mock(WifiP2pGroup.class);
        when(mP2pIfaceHalAidlMock.invite(any(WifiP2pGroup.class), anyString())).thenReturn(true);
        assertTrue(mDut.invite(group, BSSID));
        verify(mP2pIfaceHalAidlMock).invite(eq(group), eq(BSSID));
    }

    /**
     * Test that we can call reject
     */
    @Test
    public void testReject() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.reject(anyString())).thenReturn(true);
        assertTrue(mDut.reject(BSSID));
        verify(mP2pIfaceHalAidlMock).reject(eq(BSSID));
    }

    /**
     * Test that we can call getDeviceAddress
     */
    @Test
    public void testGetDeviceAddress() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.getDeviceAddress()).thenReturn(BSSID);
        assertEquals(BSSID, mDut.getDeviceAddress());
        verify(mP2pIfaceHalAidlMock).getDeviceAddress();
    }

    /**
     * Test that we can call getSsid
     */
    @Test
    public void testGetSsid() {
        initializeWithAidlImpl(true);
        String ssid = "someSsid";
        when(mP2pIfaceHalAidlMock.getSsid(anyString())).thenReturn(ssid);
        assertEquals(ssid, mDut.getSsid(BSSID));
        verify(mP2pIfaceHalAidlMock).getSsid(eq(BSSID));
    }

    /**
     * Test that we can call reinvoke
     */
    @Test
    public void testReinvoke() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.reinvoke(anyInt(), anyString())).thenReturn(true);
        assertTrue(mDut.reinvoke(NETWORK_ID, BSSID));
        verify(mP2pIfaceHalAidlMock).reinvoke(eq(NETWORK_ID), eq(BSSID));
    }

    /**
     * Test that we can call groupAdd
     */
    @Test
    public void testGroupAdd() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.groupAdd(anyInt(), anyBoolean())).thenReturn(true);
        assertTrue(mDut.groupAdd(NETWORK_ID, ENABLE));
        verify(mP2pIfaceHalAidlMock).groupAdd(eq(NETWORK_ID), eq(ENABLE));
    }

    /**
     * Test that we can call the groupAdd wrapper function
     */
    @Test
    public void testGroupAddWrapper() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.groupAdd(anyInt(), anyBoolean())).thenReturn(true);
        assertTrue(mDut.groupAdd(ENABLE));
        verify(mP2pIfaceHalAidlMock).groupAdd(eq(-1) /* set by wrapper */, eq(ENABLE));
    }

    /**
     * Test that we can call groupAddWithConfig
     */
    @Test
    public void testGroupAddWithConfig() {
        initializeWithAidlImpl(true);
        String networkName = "someName";
        String passphrase = "somePassword";
        boolean persistent = true;
        boolean join = true;
        int freq = 10;
        when(mP2pIfaceHalAidlMock.groupAdd(anyString(), anyString(),
                anyBoolean(), anyInt(), anyString(), anyBoolean())).thenReturn(true);
        assertTrue(mDut.groupAdd(networkName, passphrase, persistent, freq, BSSID, join));
        verify(mP2pIfaceHalAidlMock).groupAdd(eq(networkName), eq(passphrase), eq(persistent),
                eq(freq), eq(BSSID), eq(join));
    }

    /**
     * Test that we can call groupRemove
     */
    @Test
    public void testGroupRemove() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.groupRemove(anyString())).thenReturn(true);
        assertTrue(mDut.groupRemove(IFACE_NAME));
        verify(mP2pIfaceHalAidlMock).groupRemove(eq(IFACE_NAME));
    }

    /**
     * Test that we can call getGroupCapability
     */
    @Test
    public void testGetGroupCapability() {
        initializeWithAidlImpl(true);
        int capabilities = 0;
        when(mP2pIfaceHalAidlMock.getGroupCapability(anyString())).thenReturn(capabilities);
        assertEquals(capabilities, mDut.getGroupCapability(BSSID));
        verify(mP2pIfaceHalAidlMock).getGroupCapability(eq(BSSID));
    }

    /**
     * Test that we can call configureExtListen
     */
    @Test
    public void testConfigureExtListen() {
        initializeWithAidlImpl(true);
        int period = 2;
        int interval = 3;
        when(mP2pIfaceHalAidlMock.configureExtListen(anyBoolean(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        assertTrue(mDut.configureExtListen(ENABLE, period, interval, null));
        verify(mP2pIfaceHalAidlMock).configureExtListen(
                eq(ENABLE), eq(period), eq(interval), eq(null));
    }

    /**
     * Test that we can call setListenChannel
     */
    @Test
    public void testSetListenChannel() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setListenChannel(anyInt())).thenReturn(true);
        assertTrue(mDut.setListenChannel(CHANNEL));
        verify(mP2pIfaceHalAidlMock).setListenChannel(eq(CHANNEL));
    }

    /**
     * Test that we can call setOperatingChannel
     */
    @Test
    public void testSetOperatingChannel() {
        initializeWithAidlImpl(true);
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        when(mP2pIfaceHalAidlMock.setOperatingChannel(anyInt(), any(List.class))).thenReturn(true);
        assertTrue(mDut.setOperatingChannel(CHANNEL, unsafeChannels));
        verify(mP2pIfaceHalAidlMock).setOperatingChannel(eq(CHANNEL), eq(unsafeChannels));
    }

    /**
     * Test that we can call serviceAdd
     */
    @Test
    public void testServiceAdd() {
        initializeWithAidlImpl(true);
        WifiP2pServiceInfo serviceInfo = mock(WifiP2pServiceInfo.class);
        when(mP2pIfaceHalAidlMock.serviceAdd(any(WifiP2pServiceInfo.class))).thenReturn(true);
        assertTrue(mDut.serviceAdd(serviceInfo));
        verify(mP2pIfaceHalAidlMock).serviceAdd(eq(serviceInfo));
    }

    /**
     * Test that we can call serviceRemove
     */
    @Test
    public void testServiceRemove() {
        initializeWithAidlImpl(true);
        WifiP2pServiceInfo serviceInfo = mock(WifiP2pServiceInfo.class);
        when(mP2pIfaceHalAidlMock.serviceRemove(any(WifiP2pServiceInfo.class))).thenReturn(true);
        assertTrue(mDut.serviceRemove(serviceInfo));
        verify(mP2pIfaceHalAidlMock).serviceRemove(eq(serviceInfo));
    }

    /**
     * Test that we can call requestServiceDiscovery
     */
    @Test
    public void testRequestServiceDiscovery() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.requestServiceDiscovery(anyString(), anyString()))
                .thenReturn(RESPONSE);
        assertEquals(RESPONSE, mDut.requestServiceDiscovery(BSSID, PARAMS));
        verify(mP2pIfaceHalAidlMock).requestServiceDiscovery(eq(BSSID), eq(PARAMS));
    }

    /**
     * Test that we can call cancelServiceDiscovery
     */
    @Test
    public void testCancelServiceDiscovery() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.cancelServiceDiscovery(anyString())).thenReturn(true);
        assertTrue(mDut.cancelServiceDiscovery(PARAMS));
        verify(mP2pIfaceHalAidlMock).cancelServiceDiscovery(eq(PARAMS));
    }

    /**
     * Test that we can call setMiracastMode
     */
    @Test
    public void testSetMiracastMode() {
        initializeWithAidlImpl(true);
        int mode = 5;
        when(mP2pIfaceHalAidlMock.setMiracastMode(anyInt())).thenReturn(true);
        assertTrue(mDut.setMiracastMode(mode));
        verify(mP2pIfaceHalAidlMock).setMiracastMode(eq(mode));
    }

    /**
     * Test that we can call startWpsPbc
     */
    @Test
    public void testStartWpsPbc() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.startWpsPbc(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.startWpsPbc(IFACE_NAME, BSSID));
        verify(mP2pIfaceHalAidlMock).startWpsPbc(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call startWpsPinKeypad
     */
    @Test
    public void testStartWpsPinKeypad() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.startWpsPinKeypad(anyString(), anyString())).thenReturn(true);
        assertTrue(mDut.startWpsPinKeypad(IFACE_NAME, PIN));
        verify(mP2pIfaceHalAidlMock).startWpsPinKeypad(eq(IFACE_NAME), eq(PIN));
    }

    /**
     * Test that we can call startWpsPinDisplay
     */
    @Test
    public void testStartWpsPinDisplay() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.startWpsPinDisplay(anyString(), anyString())).thenReturn(PIN);
        assertEquals(PIN, mDut.startWpsPinDisplay(IFACE_NAME, BSSID));
        verify(mP2pIfaceHalAidlMock).startWpsPinDisplay(eq(IFACE_NAME), eq(BSSID));
    }

    /**
     * Test that we can call cancelWps
     */
    @Test
    public void testCancelWps() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.cancelWps(anyString())).thenReturn(true);
        assertTrue(mDut.cancelWps(IFACE_NAME));
        verify(mP2pIfaceHalAidlMock).cancelWps(eq(IFACE_NAME));
    }

    /**
     * Test that we can call enableWfd
     */
    @Test
    public void testEnableWfd() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.enableWfd(anyBoolean())).thenReturn(true);
        assertTrue(mDut.enableWfd(ENABLE));
        verify(mP2pIfaceHalAidlMock).enableWfd(eq(ENABLE));
    }

    /**
     * Test that we can call setWfdDeviceInfo
     */
    @Test
    public void testSetWfdDeviceInfo() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setWfdDeviceInfo(anyString())).thenReturn(true);
        assertTrue(mDut.setWfdDeviceInfo(PARAMS));
        verify(mP2pIfaceHalAidlMock).setWfdDeviceInfo(eq(PARAMS));
    }

    /**
     * Test that we can call removeNetwork
     */
    @Test
    public void testRemoveNetwork() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.removeNetwork(anyInt())).thenReturn(true);
        assertTrue(mDut.removeNetwork(NETWORK_ID));
        verify(mP2pIfaceHalAidlMock).removeNetwork(eq(NETWORK_ID));
    }

    /**
     * Test that we can call removeNetwork
     */
    @Test
    public void testLoadGroups() {
        initializeWithAidlImpl(true);
        WifiP2pGroupList groups = mock(WifiP2pGroupList.class);
        when(mP2pIfaceHalAidlMock.loadGroups(any(WifiP2pGroupList.class))).thenReturn(true);
        assertTrue(mDut.loadGroups(groups));
        verify(mP2pIfaceHalAidlMock).loadGroups(eq(groups));
    }

    /**
     * Test that we can call setWpsDeviceName
     */
    @Test
    public void testSetWpsDeviceName() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setWpsDeviceName(anyString())).thenReturn(true);
        assertTrue(mDut.setWpsDeviceName(PARAMS));
        verify(mP2pIfaceHalAidlMock).setWpsDeviceName(eq(PARAMS));
    }

    /**
     * Test that we can call setWpsDeviceType
     */
    @Test
    public void testSetWpsDeviceType() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setWpsDeviceType(anyString())).thenReturn(true);
        assertTrue(mDut.setWpsDeviceType(PARAMS));
        verify(mP2pIfaceHalAidlMock).setWpsDeviceType(eq(PARAMS));
    }

    /**
     * Test that we can call setWpsConfigMethods
     */
    @Test
    public void testSetWpsConfigMethods() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setWpsConfigMethods(anyString())).thenReturn(true);
        assertTrue(mDut.setWpsConfigMethods(PARAMS));
        verify(mP2pIfaceHalAidlMock).setWpsConfigMethods(eq(PARAMS));
    }

    /**
     * Test that we can call getNfcHandoverRequest
     */
    @Test
    public void testGetNfcHandoverRequest() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.getNfcHandoverRequest()).thenReturn(RESPONSE);
        assertEquals(RESPONSE, mDut.getNfcHandoverRequest());
        verify(mP2pIfaceHalAidlMock).getNfcHandoverRequest();
    }

    /**
     * Test that we can call getNfcHandoverSelect
     */
    @Test
    public void testGetNfcHandoverSelect() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.getNfcHandoverSelect()).thenReturn(RESPONSE);
        assertEquals(RESPONSE, mDut.getNfcHandoverSelect());
        verify(mP2pIfaceHalAidlMock).getNfcHandoverSelect();
    }

    /**
     * Test that we can call responderReportNfcHandover
     */
    @Test
    public void testResponderReportNfcHandover() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.responderReportNfcHandover(anyString())).thenReturn(true);
        assertTrue(mDut.responderReportNfcHandover(PARAMS));
        verify(mP2pIfaceHalAidlMock).responderReportNfcHandover(eq(PARAMS));
    }

    /**
     * Test that we can call setClientList
     */
    @Test
    public void testSetClientList() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setClientList(anyInt(), anyString())).thenReturn(true);
        assertTrue(mDut.setClientList(NETWORK_ID, PARAMS));
        verify(mP2pIfaceHalAidlMock).setClientList(eq(NETWORK_ID), eq(PARAMS));
    }

    /**
     * Test that we can call getClientList
     */
    @Test
    public void testGetClientList() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.getClientList(anyInt())).thenReturn(RESPONSE);
        assertEquals(RESPONSE, mDut.getClientList(NETWORK_ID));
        verify(mP2pIfaceHalAidlMock).getClientList(eq(NETWORK_ID));
    }

    /**
     * Test that we can call saveConfig
     */
    @Test
    public void testSaveConfig() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.saveConfig()).thenReturn(true);
        assertTrue(mDut.saveConfig());
        verify(mP2pIfaceHalAidlMock).saveConfig();
    }

    /**
     * Test that we can call setMacRandomization
     */
    @Test
    public void testSetMacRandomization() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setMacRandomization(anyBoolean())).thenReturn(true);
        assertTrue(mDut.setMacRandomization(ENABLE));
        verify(mP2pIfaceHalAidlMock).setMacRandomization(eq(ENABLE));
    }

    /**
     * Test that we can call setWfdR2DeviceInfo
     */
    @Test
    public void testSetWfdR2DeviceInfo() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.setWfdR2DeviceInfo(anyString())).thenReturn(true);
        assertTrue(mDut.setWfdR2DeviceInfo(PARAMS));
        verify(mP2pIfaceHalAidlMock).setWfdR2DeviceInfo(eq(PARAMS));
    }

    /**
     * Test that we can call removeClient
     */
    @Test
    public void testRemoveClient() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.removeClient(eq(BSSID), anyBoolean())).thenReturn(true);
        assertTrue(mDut.removeClient(BSSID, true));
        verify(mP2pIfaceHalAidlMock).removeClient(eq(BSSID), eq(true));
    }

    /**
     * Test that configureEapolIpAddressAllocationParams succeeds
     */
    @Test
    public void testConfigureEapolIpAddressAllocationParamsSuccess() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.configureEapolIpAddressAllocationParams(
                anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
        assertTrue(mDut.configureEapolIpAddressAllocationParams(0x0101A8C0,
                0x00FFFFFF, 0x0501A8C0, 0x0801A8C0));
        verify(mP2pIfaceHalAidlMock).configureEapolIpAddressAllocationParams(eq(0x0101A8C0),
                eq(0x00FFFFFF), eq(0x0501A8C0), eq(0x0801A8C0));
    }

    /**
     * Test that we can call terminate
     */
    @Test
    public void testTerminate() {
        initializeWithAidlImpl(true);
        doNothing().when(mP2pIfaceHalAidlMock).terminate();
        mDut.terminate();
        verify(mP2pIfaceHalAidlMock).terminate();
    }

    /**
     * Test that we can call registerDeathHandler
     */
    @Test
    public void testRegisterDeathHandler() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.registerDeathHandler(
                any(WifiNative.SupplicantDeathEventHandler.class))).thenReturn(true);
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));
        verify(mP2pIfaceHalAidlMock).registerDeathHandler(eq(mSupplicantHalDeathHandler));
    }

    /**
     * Test that we can call deregisterDeathHandler
     */
    @Test
    public void testDeregisterDeathHandler() {
        initializeWithAidlImpl(true);
        when(mP2pIfaceHalAidlMock.deregisterDeathHandler()).thenReturn(true);
        assertTrue(mDut.deregisterDeathHandler());
        verify(mP2pIfaceHalAidlMock).deregisterDeathHandler();
    }
}
