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

import static android.net.wifi.WifiManager.WIFI_FEATURE_DECORATED_IDENTITY;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA256;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA384;
import static android.net.wifi.WifiManager.WIFI_FEATURE_MBO;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OCE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OWE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WAPI;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SAE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SUITE_B;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.hardware.wifi.V1_0.WifiChannelWidthInMhz;
import android.hardware.wifi.supplicant.AnqpData;
import android.hardware.wifi.supplicant.AssociationRejectionData;
import android.hardware.wifi.supplicant.BssTmData;
import android.hardware.wifi.supplicant.BssidChangeReason;
import android.hardware.wifi.supplicant.ConnectionCapabilities;
import android.hardware.wifi.supplicant.DebugLevel;
import android.hardware.wifi.supplicant.Hs20AnqpData;
import android.hardware.wifi.supplicant.ISupplicant;
import android.hardware.wifi.supplicant.ISupplicantStaIface;
import android.hardware.wifi.supplicant.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.IfaceInfo;
import android.hardware.wifi.supplicant.IfaceType;
import android.hardware.wifi.supplicant.KeyMgmtMask;
import android.hardware.wifi.supplicant.LegacyMode;
import android.hardware.wifi.supplicant.MloLink;
import android.hardware.wifi.supplicant.MloLinksInfo;
import android.hardware.wifi.supplicant.MscsParams.FrameClassifierFields;
import android.hardware.wifi.supplicant.MsduDeliveryInfo;
import android.hardware.wifi.supplicant.OceRssiBasedAssocRejectAttr;
import android.hardware.wifi.supplicant.OsuMethod;
import android.hardware.wifi.supplicant.PmkSaCacheData;
import android.hardware.wifi.supplicant.PortRange;
import android.hardware.wifi.supplicant.QosCharacteristics.QosCharacteristicsMask;
import android.hardware.wifi.supplicant.QosPolicyClassifierParams;
import android.hardware.wifi.supplicant.QosPolicyClassifierParamsMask;
import android.hardware.wifi.supplicant.QosPolicyData;
import android.hardware.wifi.supplicant.QosPolicyRequestType;
import android.hardware.wifi.supplicant.QosPolicyScsData;
import android.hardware.wifi.supplicant.QosPolicyStatus;
import android.hardware.wifi.supplicant.QosPolicyStatusCode;
import android.hardware.wifi.supplicant.StaIfaceCallbackState;
import android.hardware.wifi.supplicant.StaIfaceReasonCode;
import android.hardware.wifi.supplicant.StaIfaceStatusCode;
import android.hardware.wifi.supplicant.SupplicantStateChangeData;
import android.hardware.wifi.supplicant.SupplicantStatusCode;
import android.hardware.wifi.supplicant.WifiTechnology;
import android.hardware.wifi.supplicant.WpaDriverCapabilitiesMask;
import android.hardware.wifi.supplicant.WpsConfigError;
import android.hardware.wifi.supplicant.WpsConfigMethods;
import android.hardware.wifi.supplicant.WpsErrorIndication;
import android.net.DscpPolicy;
import android.net.MacAddress;
import android.net.NetworkAgent;
import android.net.wifi.MscsParams;
import android.net.wifi.QosCharacteristics;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.test.TestLooper;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.MboOceController.BtmFrameData;
import com.android.server.wifi.hal.HalTestUtils;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.util.NativeUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Unit tests for SupplicantStaIfaceHalAidlImpl
 */
@SmallTest
public class SupplicantStaIfaceHalAidlImplTest extends WifiBaseTest {
    private static final Map<Integer, String> NETWORK_ID_TO_SSID = Map.of(
            1, "\"ssid1\"",
            2, "\"ssid2\"",
            3, "\"ssid3\"");
    private static final int SUPPLICANT_NETWORK_ID = 2;
    private static final String SUPPLICANT_SSID = NETWORK_ID_TO_SSID.get(SUPPLICANT_NETWORK_ID);
    private static final WifiSsid TRANSLATED_SUPPLICANT_SSID =
            WifiSsid.fromString("\"translated ssid\"");
    private static final int ROAM_NETWORK_ID = 4;
    private static final String BSSID = "fa:45:23:23:12:12";
    private static final String WLAN0_IFACE_NAME = "wlan0";
    private static final String WLAN1_IFACE_NAME = "wlan1";
    private static final String P2P_IFACE_NAME = "p2p0";
    private static final String ICON_FILE_NAME  = "blahblah";
    private static final int ICON_FILE_SIZE = 72;
    private static final String HS20_URL = "http://blahblah";
    private static final long PMK_CACHE_EXPIRATION_IN_SEC = 1024;
    private static final byte[] CONNECTED_MAC_ADDRESS_BYTES = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05};

    private @Mock ISupplicant mISupplicantMock;
    private @Mock IBinder mServiceBinderMock;
    private @Mock ISupplicantStaIface mISupplicantStaIfaceMock;
    private @Mock Context mContext;
    private @Mock WifiMonitor mWifiMonitor;
    private @Mock SupplicantStaNetworkHalAidlImpl mSupplicantStaNetworkMock;
    private @Mock WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;
    private @Mock Clock mClock;
    private @Mock WifiMetrics mWifiMetrics;
    private @Mock WifiGlobals mWifiGlobals;
    private @Mock SsidTranslator mSsidTranslator;
    private @Mock WifiInjector mWifiInjector;
    private @Mock PmkCacheManager mPmkCacheManager;
    private @Mock WifiSettingsConfigStore mWifiSettingsConfigStore;

    private @Captor ArgumentCaptor<List<SupplicantStaIfaceHal.QosPolicyRequest>>
            mQosPolicyRequestListCaptor;

    IfaceInfo[] mIfaceInfoList;
    ISupplicantStaIfaceCallback mISupplicantStaIfaceCallback;

    private TestLooper mLooper = new TestLooper();
    private Handler mHandler = null;
    private SupplicantStaIfaceHalSpy mDut;
    private ArgumentCaptor<IBinder.DeathRecipient> mSupplicantDeathCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    private class SupplicantStaIfaceHalSpy extends SupplicantStaIfaceHalAidlImpl {
        SupplicantStaNetworkHalAidlImpl mStaNetwork;

        SupplicantStaIfaceHalSpy() {
            super(mContext, mWifiMonitor, mHandler, mClock, mWifiMetrics, mWifiGlobals,
                    mSsidTranslator, mWifiInjector);
            mStaNetwork = mSupplicantStaNetworkMock;
        }

        @Override
        protected ISupplicant getSupplicantMockable() {
            return mISupplicantMock;
        }

        @Override
        protected IBinder getServiceBinderMockable() {
            return mServiceBinderMock;
        }

        @Override
        protected SupplicantStaNetworkHalAidlImpl getStaNetworkHalMockable(
                @NonNull String ifaceName, ISupplicantStaNetwork network) {
            return mStaNetwork;
        }

        private void setStaNetworkMockable(SupplicantStaNetworkHalAidlImpl network) {
            mStaNetwork = network;
        }

        @Override
        public boolean initialize() {
            return true;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mIfaceInfoList = new IfaceInfo[3];
        mIfaceInfoList[0] = createIfaceInfo(IfaceType.STA, WLAN0_IFACE_NAME);
        mIfaceInfoList[1] = createIfaceInfo(IfaceType.STA, WLAN1_IFACE_NAME);
        mIfaceInfoList[2] = createIfaceInfo(IfaceType.P2P, P2P_IFACE_NAME);
        doReturn(CONNECTED_MAC_ADDRESS_BYTES).when(mISupplicantStaIfaceMock).getMacAddress();
        mHandler = spy(new Handler(mLooper.getLooper()));
        when(mISupplicantMock.asBinder()).thenReturn(mServiceBinderMock);
        when(mISupplicantMock.getInterfaceVersion()).thenReturn(ISupplicant.VERSION);
        when(mSsidTranslator.getTranslatedSsidForStaIface(any(), anyString()))
                .thenReturn(TRANSLATED_SUPPLICANT_SSID);
        when(mSsidTranslator.getOriginalSsid(any())).thenAnswer((Answer<WifiSsid>) invocation ->
                WifiSsid.fromString(((WifiConfiguration) invocation.getArgument(0)).SSID));
        when(mSsidTranslator.getAllPossibleOriginalSsids(TRANSLATED_SUPPLICANT_SSID)).thenAnswer(
                (Answer<List<WifiSsid>>) invocation -> {
                    List<WifiSsid> ssids = new ArrayList<>();
                    ssids.add(TRANSLATED_SUPPLICANT_SSID);
                    return ssids;
                });
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
        mDut = new SupplicantStaIfaceHalSpy();
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence(false, false);
    }

    /**
     * Tests the initialization flow, with a RemoteException thrown when addStaInterface is called.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_remoteExceptionFailure() throws Exception {
        executeAndValidateInitializationSequence(true, false);
    }


    /**
     * Tests the initialization flow, with a null interface being returned by addStaInterface.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_nullInterfaceFailure() throws Exception {
        executeAndValidateInitializationSequence(false, true);
    }

    /**
     * Ensures that we do not allow operations on an interface until it's setup.
     */
    @Test
    public void testEnsureOperationFailsUntilSetupInterfaces() throws Exception {
        executeAndValidateInitializationSequence();

        // Ensure that the cancelWps operation fails because the wlan1 interface is not set up.
        assertFalse(mDut.cancelWps(WLAN1_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).cancelWps();

        // Now setup the wlan1 interface and ensure that the cancelWps operation is successful.
        assertTrue(mDut.setupIface(WLAN1_IFACE_NAME));
        doNothing().when(mISupplicantStaIfaceMock).cancelWps();
        assertTrue(mDut.cancelWps(WLAN1_IFACE_NAME));
        verify(mISupplicantStaIfaceMock).cancelWps();
    }

    /**
     * Ensures that we reject the addition of an existing iface.
     */
    @Test
    public void testDuplicateSetupIface_Fails() throws Exception {
        executeAndValidateInitializationSequence();
        // Trying setting up the wlan0 interface again & ensure it fails.
        assertFalse(mDut.setupIface(WLAN0_IFACE_NAME));
        verify(mISupplicantMock)
                .setDebugParams(eq(DebugLevel.INFO), eq(false), eq(false));
        verifyNoMoreInteractions(mISupplicantMock);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal interface teardown.
     */
    @Test
    public void testTeardownInterface() throws Exception {
        executeAndValidateInitializationSequence();

        doNothing().when(mISupplicantMock).removeInterface(any());
        assertTrue(mDut.teardownIface(WLAN0_IFACE_NAME));
        verify(mISupplicantMock).removeInterface(any());

        // Ensure that the cancelWps operation fails because there are no interfaces set up.
        assertFalse(mDut.cancelWps(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).cancelWps();
    }

    /**
     * Ensures that we reject removal of an invalid iface.
     */
    @Test
    public void testInvalidTeardownInterface_Fails() throws Exception {
        assertFalse(mDut.teardownIface(WLAN0_IFACE_NAME));
        verifyNoMoreInteractions(mISupplicantMock);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization of second interface
     */
    @Test
    public void testSetupTwoInterfaces() throws Exception {
        executeAndValidateInitializationSequence();
        assertTrue(mDut.setupIface(WLAN1_IFACE_NAME));
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal interface teardown.
     * Asserts successful initialization of second interface.
     */
    @Test
    public void testTeardownTwoInterfaces() throws Exception {
        testSetupTwoInterfaces();
        assertTrue(mDut.teardownIface(WLAN0_IFACE_NAME));
        assertTrue(mDut.teardownIface(WLAN1_IFACE_NAME));

        // Ensure that the cancelWps operation fails because there are no interfaces set up.
        assertFalse(mDut.cancelWps(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).cancelWps();
    }

    /**
     * Tests connection to a specified network with empty existing network.
     */
    @Test
    public void testConnectWithEmptyExistingNetwork() throws Exception {
        executeAndValidateInitializationSequence();
        executeAndValidateConnectSequence(0, false, TRANSLATED_SUPPLICANT_SSID.toString());
    }

    /**
     * Tests that calling connectToNetwork with a different config removes the
     * old network and replaces it with the new one.
     */
    @Test
    public void testConnectToNetworkWithDifferentConfigReplacesNetworkInSupplicant()
            throws Exception {
        executeAndValidateInitializationSequence();
        WifiConfiguration config = executeAndValidateConnectSequence(
                SUPPLICANT_NETWORK_ID, false, TRANSLATED_SUPPLICANT_SSID.toString());
        // Reset mocks for mISupplicantStaIfaceMock because we finished the first connection.
        reset(mISupplicantStaIfaceMock);
        setupMocksForConnectSequence(true);
        // Make this network different by changing SSID.
        config.SSID = "\"ADifferentSSID\"";
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        verify(mISupplicantStaIfaceMock).removeNetwork(SUPPLICANT_NETWORK_ID);
        verify(mISupplicantStaIfaceMock).addNetwork();
    }

    /**
     * Tests that calling connectToNetwork with the same config does not trigger the
     * removal of the old network and addition of a new one.
     */
    @Test
    public void connectToNetworkWithSameNetworkDoesNotRemoveNetworkFromSupplicant()
            throws Exception {
        executeAndValidateInitializationSequence();
        WifiConfiguration config = executeAndValidateConnectSequence(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        // Reset mocks for mISupplicantStaIfaceMock because we finished the first connection.
        reset(mISupplicantStaIfaceMock);
        setupMocksForConnectSequence(true /*haveExistingNetwork*/);
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        verify(mISupplicantStaIfaceMock, never()).removeNetwork(anyInt());
        verify(mISupplicantStaIfaceMock, never()).addNetwork();
    }

    /**
     * Tests that calling connectToNetwork with the same network but a different bssid
     * updates the bssid, but does not trigger the removal of the old network and addition
     * of a new one.
     */
    @Test
    public void connectToNetworkWithSameNetworkButDifferentBssidUpdatesNetworkFromSupplicant()
            throws Exception {
        executeAndValidateInitializationSequence();
        WifiConfiguration config = executeAndValidateConnectSequence(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        String testBssid = "11:22:33:44:55:66";
        when(mSupplicantStaNetworkMock.setBssid(eq(testBssid))).thenReturn(true);

        // Reset mocks for mISupplicantStaIfaceMock because we finished the first connection.
        reset(mISupplicantStaIfaceMock);
        setupMocksForConnectSequence(true /*haveExistingNetwork*/);
        // Change the BSSID and connect to the same network.
        assertFalse(TextUtils.equals(
                testBssid, config.getNetworkSelectionStatus().getNetworkSelectionBSSID()));
        config.getNetworkSelectionStatus().setNetworkSelectionBSSID(testBssid);
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        verify(mSupplicantStaNetworkMock).setBssid(eq(testBssid));
        verify(mISupplicantStaIfaceMock, never()).removeNetwork(anyInt());
        verify(mISupplicantStaIfaceMock, never()).addNetwork();
    }

    /**
     * Tests connection to a specified network failure due to network add.
     */
    @Test
    public void testConnectFailureDueToNetworkAddFailure() throws Exception {
        executeAndValidateInitializationSequence();
        setupMocksForConnectSequence(false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantStaIfaceMock).addNetwork();
        assertFalse(mDut.connectToNetwork(WLAN0_IFACE_NAME, createTestWifiConfiguration()));
    }

    /**
     * Tests connection to a specified network failure due to network save.
     */
    @Test
    public void testConnectFailureDueToNetworkSaveFailure() throws Exception {
        executeAndValidateInitializationSequence();
        setupMocksForConnectSequence(true);

        when(mSupplicantStaNetworkMock.saveWifiConfiguration(any(WifiConfiguration.class)))
                .thenReturn(false);

        assertFalse(mDut.connectToNetwork(WLAN0_IFACE_NAME, createTestWifiConfiguration()));
        // We should have removed the existing network once before connection and once more
        // on failure to save network configuration.
        verify(mISupplicantStaIfaceMock, times(2)).removeNetwork(anyInt());
    }

    /**
     * Tests connection to a specified network failure due to exception in network save.
     */
    @Test
    public void testConnectFailureDueToNetworkSaveException() throws Exception {
        executeAndValidateInitializationSequence();
        setupMocksForConnectSequence(true);

        doThrow(new IllegalArgumentException())
                .when(mSupplicantStaNetworkMock).saveWifiConfiguration(
                any(WifiConfiguration.class));

        assertFalse(mDut.connectToNetwork(WLAN0_IFACE_NAME, createTestWifiConfiguration()));
        // We should have removed the existing network once before connection and once more
        // on failure to save network configuration.
        verify(mISupplicantStaIfaceMock, times(2)).removeNetwork(anyInt());
    }

    /**
     * Tests connection to a specified network failure due to network select.
     */
    @Test
    public void testConnectFailureDueToNetworkSelectFailure() throws Exception {
        executeAndValidateInitializationSequence();
        setupMocksForConnectSequence(false);

        when(mSupplicantStaNetworkMock.select()).thenReturn(false);

        assertFalse(mDut.connectToNetwork(WLAN0_IFACE_NAME, createTestWifiConfiguration()));
    }

    /**
     * Tests roaming to the same network as the currently connected one.
     */
    @Test
    public void testRoamToSameNetwork() throws Exception {
        executeAndValidateInitializationSequence();
        executeAndValidateRoamSequence(true, false);
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, createTestWifiConfiguration()));
    }

    /**
     * Tests roaming to a different network.
     */
    @Test
    public void testRoamToDifferentNetwork() throws Exception {
        executeAndValidateInitializationSequence();
        executeAndValidateRoamSequence(false, false);
    }

    /**
     * Tests framework roaming to a linked network.
     */
    @Test
    public void testRoamToLinkedNetwork() throws Exception {
        executeAndValidateInitializationSequence();
        executeAndValidateRoamSequence(false, true);
    }

    /**
     * Tests updating linked networks for a network id
     */
    @Test
    public void testUpdateLinkedNetworks() throws Exception {
        executeAndValidateInitializationSequence();

        final int frameworkNetId = 1;
        final int supplicantNetId = 10;

        // No current network in supplicant, return false
        assertFalse(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, SUPPLICANT_NETWORK_ID, null));

        WifiConfiguration config = executeAndValidateConnectSequence(
                frameworkNetId, false, TRANSLATED_SUPPLICANT_SSID.toString());

        // Mismatched framework network id, return false
        assertFalse(mDut.updateLinkedNetworks(WLAN0_IFACE_NAME, frameworkNetId + 1, null));

        // Supplicant network id is invalid, return false
        when(mSupplicantStaNetworkMock.getNetworkId()).thenReturn(-1);
        assertFalse(mDut.updateLinkedNetworks(WLAN0_IFACE_NAME, frameworkNetId, null));

        // Supplicant failed to return network list, return false
        when(mSupplicantStaNetworkMock.getNetworkId()).thenReturn(supplicantNetId);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantStaIfaceMock).listNetworks();
        assertFalse(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, null));

        // Supplicant returned a null network list, return false
        doReturn(null).when(mISupplicantStaIfaceMock).listNetworks();
        assertFalse(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, null));

        // Successfully link a network to the current network
        int[] supplicantNetIds = new int[1];
        supplicantNetIds[0] = supplicantNetId;
        doReturn(supplicantNetIds).when(mISupplicantStaIfaceMock).listNetworks();
        WifiConfiguration linkedConfig = new WifiConfiguration();
        linkedConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        Map<String, WifiConfiguration> linkedNetworks = new HashMap<>();
        linkedNetworks.put(linkedConfig.getProfileKey(), linkedConfig);
        SupplicantStaNetworkHalAidlImpl linkedNetworkHandle =
                mock(SupplicantStaNetworkHalAidlImpl.class);
        when(linkedNetworkHandle.getNetworkId()).thenReturn(supplicantNetId + 1);
        when(linkedNetworkHandle.saveWifiConfiguration(linkedConfig)).thenReturn(true);
        when(linkedNetworkHandle.select()).thenReturn(true);
        mDut.setStaNetworkMockable(linkedNetworkHandle);
        assertTrue(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, linkedNetworks));

        // Successfully remove linked network but not the current network from supplicant
        supplicantNetIds = new int[2];
        supplicantNetIds[0] = supplicantNetId;
        supplicantNetIds[1] = supplicantNetId + 1;
        doReturn(supplicantNetIds).when(mISupplicantStaIfaceMock).listNetworks();
        doNothing().when(mISupplicantStaIfaceMock).removeNetwork(supplicantNetId + 1);
        assertTrue(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, null));
        verify(mISupplicantStaIfaceMock).removeNetwork(supplicantNetId + 1);
        verify(mISupplicantStaIfaceMock, never()).removeNetwork(supplicantNetId);
    }

    /**
     * Tests roaming failure because of unable to set bssid.
     */
    @Test
    public void testRoamFailureDueToBssidSet() throws Exception {
        executeAndValidateInitializationSequence();
        int connectedNetworkId = 5;
        executeAndValidateConnectSequence(connectedNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        when(mSupplicantStaNetworkMock.setBssid(anyString())).thenReturn(false);

        WifiConfiguration roamingConfig = new WifiConfiguration();
        roamingConfig.networkId = connectedNetworkId;
        roamingConfig.getNetworkSelectionStatus().setNetworkSelectionBSSID("45:34:23:23:ab:ed");
        assertFalse(mDut.roamToNetwork(WLAN0_IFACE_NAME, roamingConfig));
    }

    /**
     * Tests removal of all configured networks from wpa_supplicant.
     */
    @Test
    public void testRemoveAllNetworks() throws Exception {
        executeAndValidateInitializationSequence();
        Set<Integer> keys = NETWORK_ID_TO_SSID.keySet();
        int[] networks = new int[keys.size()];
        int index = 0;
        for (Integer e : keys) {
            networks[index++] = e;
        }

        doReturn(networks).when(mISupplicantStaIfaceMock).listNetworks();
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(int id) {
                assertTrue(NETWORK_ID_TO_SSID.containsKey(id));
            }
        }).when(mISupplicantStaIfaceMock).removeNetwork(anyInt());

        assertTrue(mDut.removeAllNetworks(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, times(NETWORK_ID_TO_SSID.size())).removeNetwork(anyInt());
    }

    /**
     * Remove all networks while connected, verify that the current network info is resetted.
     */
    @Test
    public void testRemoveAllNetworksWhileConnected() throws Exception {
        String testBssid = "11:22:33:44:55:66";
        when(mSupplicantStaNetworkMock.setBssid(eq(testBssid))).thenReturn(true);

        executeAndValidateInitializationSequence();

        // Connect to a network and verify current network is set.
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertTrue(mDut.setCurrentNetworkBssid(WLAN0_IFACE_NAME, testBssid));
        verify(mSupplicantStaNetworkMock).setBssid(eq(testBssid));
        reset(mSupplicantStaNetworkMock);

        // Remove all networks and verify current network info is resetted.
        assertTrue(mDut.removeAllNetworks(WLAN0_IFACE_NAME));
        assertFalse(mDut.setCurrentNetworkBssid(WLAN0_IFACE_NAME, testBssid));
        verify(mSupplicantStaNetworkMock, never()).setBssid(eq(testBssid));
    }

    /**
     * Tests roaming failure because of unable to reassociate.
     */
    @Test
    public void testRoamFailureDueToReassociate() throws Exception {
        executeAndValidateInitializationSequence();
        int connectedNetworkId = 5;
        executeAndValidateConnectSequence(connectedNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());

        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantStaIfaceMock).reassociate();
        when(mSupplicantStaNetworkMock.setBssid(anyString())).thenReturn(true);

        WifiConfiguration roamingConfig = new WifiConfiguration();
        roamingConfig.networkId = connectedNetworkId;
        roamingConfig.getNetworkSelectionStatus().setNetworkSelectionBSSID("45:34:23:23:ab:ed");
        assertFalse(mDut.roamToNetwork(WLAN0_IFACE_NAME, roamingConfig));
    }

    /**
     * Tests the retrieval of WPS NFC token.
     */
    @Test
    public void testGetCurrentNetworkWpsNfcConfigurationToken() throws Exception {
        String token = "45adbc1";
        when(mSupplicantStaNetworkMock.getWpsNfcConfigurationToken()).thenReturn(token);

        executeAndValidateInitializationSequence();
        // Return null when not connected to the network.
        assertTrue(mDut.getCurrentNetworkWpsNfcConfigurationToken(WLAN0_IFACE_NAME) == null);
        verify(mSupplicantStaNetworkMock, never()).getWpsNfcConfigurationToken();
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertEquals(token, mDut.getCurrentNetworkWpsNfcConfigurationToken(WLAN0_IFACE_NAME));
        verify(mSupplicantStaNetworkMock).getWpsNfcConfigurationToken();
    }

    /**
     * Tests the setting of BSSID.
     */
    @Test
    public void testSetCurrentNetworkBssid() throws Exception {
        String bssidStr = "34:34:12:12:12:90";
        when(mSupplicantStaNetworkMock.setBssid(eq(bssidStr))).thenReturn(true);

        executeAndValidateInitializationSequence();
        // Fail when not connected to a network.
        assertFalse(mDut.setCurrentNetworkBssid(WLAN0_IFACE_NAME, bssidStr));
        verify(mSupplicantStaNetworkMock, never()).setBssid(eq(bssidStr));
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertTrue(mDut.setCurrentNetworkBssid(WLAN0_IFACE_NAME, bssidStr));
        verify(mSupplicantStaNetworkMock).setBssid(eq(bssidStr));
    }

    /**
     * Tests the sending identity response for the current network.
     */
    @Test
    public void testSetCurrentNetworkEapIdentityResponse() throws Exception {
        String identity = "blah@blah.com";
        String encryptedIdentity = "blah2@blah.com";
        when(mSupplicantStaNetworkMock.sendNetworkEapIdentityResponse(eq(identity),
                eq(encryptedIdentity)))
                .thenReturn(true);

        executeAndValidateInitializationSequence();
        // Fail when not connected to a network.
        assertFalse(mDut.sendCurrentNetworkEapIdentityResponse(WLAN0_IFACE_NAME, identity,
                encryptedIdentity));
        verify(mSupplicantStaNetworkMock, never()).sendNetworkEapIdentityResponse(eq(identity),
                eq(encryptedIdentity));
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertTrue(mDut.sendCurrentNetworkEapIdentityResponse(WLAN0_IFACE_NAME, identity,
                encryptedIdentity));
        verify(mSupplicantStaNetworkMock).sendNetworkEapIdentityResponse(eq(identity),
                eq(encryptedIdentity));
    }

    /**
     * Tests the getting of anonymous identity for the current network.
     */
    @Test
    public void testGetCurrentNetworkEapAnonymousIdentity() throws Exception {
        String anonymousIdentity = "aaa@bbb.ccc";
        when(mSupplicantStaNetworkMock.fetchEapAnonymousIdentity())
                .thenReturn(anonymousIdentity);
        executeAndValidateInitializationSequence();

        // Return null when not connected to the network.
        assertEquals(null, mDut.getCurrentNetworkEapAnonymousIdentity(WLAN0_IFACE_NAME));
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        // Return anonymous identity for the current network.
        assertEquals(
                anonymousIdentity, mDut.getCurrentNetworkEapAnonymousIdentity(WLAN0_IFACE_NAME));
    }

    /**
     * Tests the sending gsm auth response for the current network.
     */
    @Test
    public void testSetCurrentNetworkEapSimGsmAuthResponse() throws Exception {
        String params = "test";
        when(mSupplicantStaNetworkMock.sendNetworkEapSimGsmAuthResponse(eq(params)))
                .thenReturn(true);

        executeAndValidateInitializationSequence();
        // Fail when not connected to a network.
        assertFalse(mDut.sendCurrentNetworkEapSimGsmAuthResponse(WLAN0_IFACE_NAME, params));
        verify(mSupplicantStaNetworkMock, never()).sendNetworkEapSimGsmAuthResponse(eq(params));
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertTrue(mDut.sendCurrentNetworkEapSimGsmAuthResponse(WLAN0_IFACE_NAME, params));
        verify(mSupplicantStaNetworkMock).sendNetworkEapSimGsmAuthResponse(eq(params));
    }

    /**
     * Tests the sending umts auth response for the current network.
     */
    @Test
    public void testSetCurrentNetworkEapSimUmtsAuthResponse() throws Exception {
        String params = "test";
        when(mSupplicantStaNetworkMock.sendNetworkEapSimUmtsAuthResponse(eq(params)))
                .thenReturn(true);

        executeAndValidateInitializationSequence();
        // Fail when not connected to a network.
        assertFalse(mDut.sendCurrentNetworkEapSimUmtsAuthResponse(WLAN0_IFACE_NAME, params));
        verify(mSupplicantStaNetworkMock, never()).sendNetworkEapSimUmtsAuthResponse(eq(params));
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertTrue(mDut.sendCurrentNetworkEapSimUmtsAuthResponse(WLAN0_IFACE_NAME, params));
        verify(mSupplicantStaNetworkMock).sendNetworkEapSimUmtsAuthResponse(eq(params));
    }

    /**
     * Tests the sending umts auts response for the current network.
     */
    @Test
    public void testSetCurrentNetworkEapSimUmtsAutsResponse() throws Exception {
        String params = "test";
        when(mSupplicantStaNetworkMock.sendNetworkEapSimUmtsAutsResponse(eq(params)))
                .thenReturn(true);

        executeAndValidateInitializationSequence();
        // Fail when not connected to a network.
        assertFalse(mDut.sendCurrentNetworkEapSimUmtsAutsResponse(WLAN0_IFACE_NAME, params));
        verify(mSupplicantStaNetworkMock, never()).sendNetworkEapSimUmtsAutsResponse(eq(params));
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());
        assertTrue(mDut.sendCurrentNetworkEapSimUmtsAutsResponse(WLAN0_IFACE_NAME, params));
        verify(mSupplicantStaNetworkMock).sendNetworkEapSimUmtsAutsResponse(eq(params));
    }

    /**
     * Tests the setting of WPS device type.
     */
    @Test
    public void testSetWpsDeviceType() throws Exception {
        String validDeviceTypeStr = "10-0050F204-5";
        byte[] expectedDeviceType = { 0x0, 0xa, 0x0, 0x50, (byte) 0xf2, 0x04, 0x0, 0x05};
        String invalidDeviceType1Str = "10-02050F204-5";
        String invalidDeviceType2Str = "10-0050F204-534";
        doNothing().when(mISupplicantStaIfaceMock).setWpsDeviceType(any(byte[].class));

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setWpsDeviceType(WLAN0_IFACE_NAME, validDeviceTypeStr));
        verify(mISupplicantStaIfaceMock).setWpsDeviceType(eq(expectedDeviceType));

        // This should not work
        assertFalse(mDut.setWpsDeviceType(WLAN0_IFACE_NAME, invalidDeviceType1Str));
        assertFalse(mDut.setWpsDeviceType(WLAN0_IFACE_NAME, invalidDeviceType2Str));
    }

    /**
     * Tests the setting of WPS config methods.
     */
    @Test
    public void testSetWpsConfigMethods() throws Exception {
        String validConfigMethodsStr = "physical_display virtual_push_button";
        int expectedConfigMethods = WpsConfigMethods.PHY_DISPLAY | WpsConfigMethods.VIRT_PUSHBUTTON;
        String invalidConfigMethodsStr = "physical_display virtual_push_button test";
        doNothing().when(mISupplicantStaIfaceMock).setWpsConfigMethods(anyInt());

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setWpsConfigMethods(WLAN0_IFACE_NAME, validConfigMethodsStr));
        verify(mISupplicantStaIfaceMock).setWpsConfigMethods(eq(expectedConfigMethods));

        // This should throw an illegal argument exception.
        try {
            assertFalse(mDut.setWpsConfigMethods(WLAN0_IFACE_NAME, invalidConfigMethodsStr));
        } catch (IllegalArgumentException e) {
            return;
        }
        assertTrue(false);
    }

    /**
     * Tests the handling of ANQP done callback.
     * Note: Since the ANQP element parsing methods are static, this can only test the negative test
     * where all the parsing fails because the data is empty. It'll be non-trivial and unnecessary
     * to test out the parsing logic here.
     */
    @Test
    public void testAnqpDoneCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        mISupplicantStaIfaceCallback.onAnqpQueryDone(bssid, new AnqpData(), new Hs20AnqpData());

        ArgumentCaptor<AnqpEvent> anqpEventCaptor = ArgumentCaptor.forClass(AnqpEvent.class);
        verify(mWifiMonitor).broadcastAnqpDoneEvent(
                eq(WLAN0_IFACE_NAME), anqpEventCaptor.capture());
        assertEquals(
                ByteBufferReader.readInteger(
                        ByteBuffer.wrap(bssid), ByteOrder.BIG_ENDIAN, bssid.length),
                anqpEventCaptor.getValue().getBssid());
    }

    /**
     * Tests the handling of Icon done callback.
     */
    @Test
    public void testIconDoneCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        byte[] iconData = new byte[ICON_FILE_SIZE];
        new Random().nextBytes(iconData);
        mISupplicantStaIfaceCallback.onHs20IconQueryDone(bssid, ICON_FILE_NAME, iconData);

        ArgumentCaptor<IconEvent> iconEventCaptor = ArgumentCaptor.forClass(IconEvent.class);
        verify(mWifiMonitor).broadcastIconDoneEvent(
                eq(WLAN0_IFACE_NAME), iconEventCaptor.capture());
        assertEquals(
                ByteBufferReader.readInteger(
                        ByteBuffer.wrap(bssid), ByteOrder.BIG_ENDIAN, bssid.length),
                iconEventCaptor.getValue().getBSSID());
        assertEquals(ICON_FILE_NAME, iconEventCaptor.getValue().getFileName());
        assertArrayEquals(iconData, iconEventCaptor.getValue().getData());
    }

    /**
     * Tests the handling of HS20 subscription remediation callback.
     */
    @Test
    public void testHs20SubscriptionRemediationCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        byte osuMethod = OsuMethod.OMA_DM;
        mISupplicantStaIfaceCallback.onHs20SubscriptionRemediation(
                bssid, osuMethod, HS20_URL);

        ArgumentCaptor<WnmData> wnmDataCaptor = ArgumentCaptor.forClass(WnmData.class);
        verify(mWifiMonitor).broadcastWnmEvent(eq(WLAN0_IFACE_NAME), wnmDataCaptor.capture());
        assertEquals(
                ByteBufferReader.readInteger(
                        ByteBuffer.wrap(bssid), ByteOrder.BIG_ENDIAN, bssid.length),
                wnmDataCaptor.getValue().getBssid());
        assertEquals(osuMethod, wnmDataCaptor.getValue().getMethod());
        assertEquals(HS20_URL, wnmDataCaptor.getValue().getUrl());
    }

    /**
     * Tests the handling of HS20 deauth imminent callback.
     */
    @Test
    public void testHs20DeauthImminentCallbackWithEssReasonCode() throws Exception {
        executeAndValidateHs20DeauthImminentCallback(true);
    }

    /**
     * Tests the handling of HS20 deauth imminent callback.
     */
    @Test
    public void testHs20DeauthImminentCallbackWithNonEssReasonCode() throws Exception {
        executeAndValidateHs20DeauthImminentCallback(false);
    }

    /**
     * Tests the handling of HS20 Terms & Conditions acceptance callback.
     */
    @Test
    public void testHs20TermsAndConditionsAcceptance() throws Exception {
        executeAndValidateHs20TermsAndConditionsCallback();
    }

    /**
     * Tests the handling of state change notification without any configured network.
     */
    @Test
    public void testStateChangeCallbackWithNoConfiguredNetwork() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.INACTIVE,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);

        // Can't compare WifiSsid instances because they lack an equals.
        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiConfiguration.INVALID_NETWORK_ID),
                any(WifiSsid.class), eq(BSSID), eq(0), eq(SupplicantState.INACTIVE));
    }

    /**
     * Tests the handling of state change notification to associated after configuring a network.
     */
    @Test
    public void testStateChangeToAssociatedCallback() throws Exception {
        executeAndValidateInitializationSequence();
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);

        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                any(WifiSsid.class), eq(BSSID), eq(0), eq(SupplicantState.ASSOCIATED));
    }

    /**
     * Tests the handling of state change notification to completed after configuring a network.
     */
    @Test
    public void testStateChangeToCompletedCallback() throws Exception {
        InOrder wifiMonitorInOrder = inOrder(mWifiMonitor);
        executeAndValidateInitializationSequence();
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.COMPLETED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);

        wifiMonitorInOrder.verify(mWifiMonitor).broadcastNetworkConnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId), eq(false),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(null));
        wifiMonitorInOrder.verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(0), eq(SupplicantState.COMPLETED));
    }

    /**
     * Tests the handling of state change notification after configuring a network.
     */
    @Test
    public void testStateChangeToCompleted() throws Exception {
        InOrder wifiMonitorInOrder = inOrder(mWifiMonitor);
        executeAndValidateInitializationSequence();
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        assertNotNull(mISupplicantStaIfaceCallback);

        BitSet expectedAkmMask = new BitSet();
        expectedAkmMask.set(WifiConfiguration.KeyMgmt.WPA_PSK);

        SupplicantStateChangeData stateChangeData = new SupplicantStateChangeData();
        stateChangeData.newState = StaIfaceCallbackState.COMPLETED;
        stateChangeData.id = frameworkNetworkId;
        stateChangeData.ssid = NativeUtil.byteArrayFromArrayList(
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        stateChangeData.bssid = NativeUtil.macAddressToByteArray(BSSID);
        stateChangeData.keyMgmtMask = android.hardware.wifi.supplicant.KeyMgmtMask.WPA_PSK;
        stateChangeData.filsHlpSent = false;
        stateChangeData.frequencyMhz = 2412;
        mISupplicantStaIfaceCallback.onSupplicantStateChanged(stateChangeData);

        wifiMonitorInOrder.verify(mWifiMonitor).broadcastNetworkConnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId), eq(false),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(expectedAkmMask));
        wifiMonitorInOrder.verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(2412),
                eq(SupplicantState.COMPLETED));
    }

    /**
     * Tests the handling of network disconnected notification.
     */
    @Test
    public void testDisconnectedCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        // Set the SSID for the current connection.
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        int reasonCode = 5;
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor).broadcastNetworkDisconnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(true), eq(reasonCode),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(BSSID));

        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);
        verify(mWifiMonitor).broadcastNetworkDisconnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(false), eq(reasonCode),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(BSSID));
    }

    /**
     * Tests the handling of incorrect network passwords.
     */
    @Test
    public void testAuthFailurePasswordOnDisconnect() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        executeAndValidateConnectSequenceWithKeyMgmt(0, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_PSK, null);

        int reasonCode = 3;
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0))
                .broadcastAuthenticationFailureEvent(any(), anyInt(), anyInt(), any(), any());

        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);
        verify(mWifiMonitor, times(0))
                .broadcastAuthenticationFailureEvent(any(), anyInt(), anyInt(), any(), any());


        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);

        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
    }

    /**
     * Tests that connection failure due to wrong password in WAPI-PSK network is notified.
     */
    @Test
    public void testWapiPskWrongPasswordNotification() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        executeAndValidateConnectSequenceWithKeyMgmt(0, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_WAPI_PSK,
                null);

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, 3);

        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
    }

    /**
     * Tests the handling of EAP failure disconnects.
     */
    @Test
    public void testAuthFailureEapOnDisconnect() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        executeAndValidateConnectSequenceWithKeyMgmt(0, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_EAP, null);

        int reasonCode = 3;
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0))
                .broadcastAuthenticationFailureEvent(any(), anyInt(), anyInt(), any(), any());

        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);
        verify(mWifiMonitor, times(0))
                .broadcastAuthenticationFailureEvent(any(), anyInt(), anyInt(), any(), any());

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATED,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        // Ensure we don't lose our prev state with this state changed event.
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.DISCONNECTED,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);

        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
    }

    /**
     * Tests the handling of incorrect network passwords for WPA3-Personal networks
     */
    @Test
    public void testWpa3AuthRejectionPassword() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        executeAndValidateConnectSequenceWithKeyMgmt(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_SAE, null);

        int statusCode = StaIfaceStatusCode.UNSPECIFIED_FAILURE;
        AssociationRejectionData rejectionData = createAssocRejectData(SUPPLICANT_SSID, BSSID,
                statusCode, false);
        mISupplicantStaIfaceCallback.onAssociationRejected(rejectionData);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()),
                eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo = assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(SupplicantStaIfaceCallbackAidlImpl.halToFrameworkStatusCode(
                statusCode), assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Tests the handling of association rejection for WPA3-Personal networks
     */
    @Test
    public void testWpa3AuthRejectionEverConnected() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        WifiConfiguration config = executeAndValidateConnectSequenceWithKeyMgmt(
                SUPPLICANT_NETWORK_ID, false, TRANSLATED_SUPPLICANT_SSID.toString(),
                WifiConfiguration.SECURITY_TYPE_SAE, null, true);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        int statusCode = StaIfaceStatusCode.UNSPECIFIED_FAILURE;
        AssociationRejectionData rejectionData = createAssocRejectData(SUPPLICANT_SSID, BSSID,
                statusCode, false);
        mISupplicantStaIfaceCallback.onAssociationRejected(rejectionData);
        verify(mWifiMonitor, never()).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                anyInt(), anyInt(), any(), any());
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo = assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(SupplicantStaIfaceCallbackAidlImpl.halToFrameworkStatusCode(
                statusCode), assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Tests that association rejection due to timeout doesn't broadcast authentication failure
     * with reason code ERROR_AUTH_FAILURE_WRONG_PSWD.
     * Driver/Supplicant sets the timedOut field when there is no ACK or response frame for
     * Authentication request or Association request frame.
     */
    @Test
    public void testAssociationRejectionDueToTimedOutDoesntNotifyWrongPassword() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        executeAndValidateConnectSequenceWithKeyMgmt(
                SUPPLICANT_NETWORK_ID, false, TRANSLATED_SUPPLICANT_SSID.toString(),
                WifiConfiguration.SECURITY_TYPE_SAE, null, true);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        AssociationRejectionData rejectionData = createAssocRejectData(SUPPLICANT_SSID, BSSID,
                StaIfaceStatusCode.UNSPECIFIED_FAILURE, true);
        mISupplicantStaIfaceCallback.onAssociationRejected(rejectionData);
        verify(mWifiMonitor, never()).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                anyInt(), anyInt(), any(), any());
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo = assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertTrue(assocRejectEventInfo.timedOut);
    }

    /**
     * Tests the handling of authentication failure for WPA3-Personal networks with
     * status code = 15 (CHALLENGE_FAIL)
     */
    @Test
    public void testWpa3AuthRejectionDueToChallengeFail() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        executeAndValidateConnectSequenceWithKeyMgmt(
                SUPPLICANT_NETWORK_ID, false, TRANSLATED_SUPPLICANT_SSID.toString(),
                WifiConfiguration.SECURITY_TYPE_SAE, null, true);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        int statusCode = StaIfaceStatusCode.CHALLENGE_FAIL;
        AssociationRejectionData rejectionData = createAssocRejectData(SUPPLICANT_SSID, BSSID,
                statusCode, false);
        mISupplicantStaIfaceCallback.onAssociationRejected(rejectionData);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()),
                eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo = assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(SupplicantStaIfaceCallbackAidlImpl.halToFrameworkStatusCode(
                statusCode), assocRejectEventInfo.statusCode);
    }

    /**
     * Tests the handling of incorrect network passwords for WEP networks.
     */
    @Test
    public void testWepAuthRejectionPassword() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        executeAndValidateConnectSequenceWithKeyMgmt(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_WEP,
                "97CA326539");

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        int statusCode = StaIfaceStatusCode.CHALLENGE_FAIL;
        AssociationRejectionData rejectionData = createAssocRejectData(SUPPLICANT_SSID, BSSID,
                statusCode, false);
        mISupplicantStaIfaceCallback.onAssociationRejected(rejectionData);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()),
                eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(SupplicantStaIfaceCallbackAidlImpl.halToFrameworkStatusCode(
                statusCode), assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Tests the handling of incorrect network passwords, edge case.
     *
     * If the network is removed during 4-way handshake, do not call it a password mismatch.
     */
    @Test
    public void testNetworkRemovedDuring4way() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        int reasonCode = 3;
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onNetworkRemoved(SUPPLICANT_NETWORK_ID);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of incorrect network passwords, edge case.
     *
     * If the disconnect reason is "IE in 4way differs", do not call it a password mismatch.
     */
    @Test
    public void testIeDiffers() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        int reasonCode = StaIfaceReasonCode.IE_IN_4WAY_DIFFERS;

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of incorrect network password for AP_BUSY error code
     *
     * If the disconnect reason is "NO_MORE_STAS - Disassociated because AP is unable
     * to handle all currently associated STAs", do not call it a password mismatch.
     */
    @Test
    public void testApBusy() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        int reasonCode = StaIfaceReasonCode.DISASSOC_AP_BUSY;

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, never()).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of eap failure during disconnect.
     */
    @Test
    public void testEapFailure() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        int reasonCode = StaIfaceReasonCode.IEEE_802_1X_AUTH_FAILED;
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of authentication timeout notification.
     */
    @Test
    public void testAuthenticationTimeoutCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.COMPLETED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onAuthenticationTimeout(
                NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_TIMEOUT), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()),
                eq(MacAddress.fromString(BSSID)));
    }

    /**
     * Tests the handling of bssid change notification.
     */
    @Test
    public void testBssidChangedCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onBssidChanged(
                BssidChangeReason.ASSOC_START, NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor).broadcastTargetBssidEvent(eq(WLAN0_IFACE_NAME), eq(BSSID));
        verify(mWifiMonitor, never()).broadcastAssociatedBssidEvent(
                eq(WLAN0_IFACE_NAME), eq(BSSID));

        reset(mWifiMonitor);
        mISupplicantStaIfaceCallback.onBssidChanged(
                BssidChangeReason.ASSOC_COMPLETE, NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor, never()).broadcastTargetBssidEvent(eq(WLAN0_IFACE_NAME), eq(BSSID));
        verify(mWifiMonitor).broadcastAssociatedBssidEvent(eq(WLAN0_IFACE_NAME), eq(BSSID));

        reset(mWifiMonitor);
        mISupplicantStaIfaceCallback.onBssidChanged(
                BssidChangeReason.DISASSOC, NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor, never()).broadcastTargetBssidEvent(eq(WLAN0_IFACE_NAME), eq(BSSID));
        verify(mWifiMonitor, never()).broadcastAssociatedBssidEvent(
                eq(WLAN0_IFACE_NAME), eq(BSSID));
    }

    /**
     * Tests the handling of EAP failure notification.
     */
    @Test
    public void testEapFailureCallback() throws Exception {
        int eapFailureCode = WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED;
        MacAddress bssid = MacAddress.fromString(BSSID);
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onEapFailure(bssid.toByteArray(), eapFailureCode);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE),
                eq(eapFailureCode), any(), eq(bssid));
    }

    /**
     * Tests the handling of Wps success notification.
     */
    @Test
    public void testWpsSuccessCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onWpsEventSuccess();
        verify(mWifiMonitor).broadcastWpsSuccessEvent(eq(WLAN0_IFACE_NAME));
    }

    /**
     * Tests the handling of Wps fail notification.
     */
    @Test
    public void testWpsFailureCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        short cfgError = WpsConfigError.MULTIPLE_PBC_DETECTED;
        short errorInd = WpsErrorIndication.SECURITY_WEP_PROHIBITED;
        mISupplicantStaIfaceCallback.onWpsEventFail(
                NativeUtil.macAddressToByteArray(BSSID), cfgError, errorInd);
        verify(mWifiMonitor).broadcastWpsFailEvent(eq(WLAN0_IFACE_NAME),
                eq((int) cfgError), eq((int) errorInd));
    }

    /**
     * Tests the handling of Wps fail notification.
     */
    @Test
    public void testWpsTimeoutCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        short cfgError = WpsConfigError.MSG_TIMEOUT;
        short errorInd = WpsErrorIndication.NO_ERROR;
        mISupplicantStaIfaceCallback.onWpsEventFail(
                NativeUtil.macAddressToByteArray(BSSID), cfgError, errorInd);
        verify(mWifiMonitor).broadcastWpsTimeoutEvent(eq(WLAN0_IFACE_NAME));
    }

    /**
     * Tests the handling of Wps pbc overlap notification.
     */
    @Test
    public void testWpsPbcOverlapCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onWpsEventPbcOverlap();
        verify(mWifiMonitor).broadcastWpsOverlapEvent(eq(WLAN0_IFACE_NAME));
    }

    /**
     * Tests the handling of supplicant death notification.
     */
    @Test
    public void testSupplicantDeathCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mSupplicantDeathCaptor.getValue());
        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));

        mSupplicantDeathCaptor.getValue().binderDied(mServiceBinderMock);
        mLooper.dispatchAll();

        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler).onDeath();
    }

    /**
     * When wpa_supplicant is dead, we could end up getting a remote exception on a binder call
     * and then the death notification.
     */
    @Test
    public void testHandleRemoteExceptionAndDeathNotification() throws Exception {
        executeAndValidateInitializationSequence();
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));
        assertTrue(mDut.isInitializationComplete());

        // Throw remote exception on a binder call.
        doThrow(new RemoteException()).when(mISupplicantStaIfaceMock).setPowerSave(anyBoolean());
        assertFalse(mDut.setPowerSave(WLAN0_IFACE_NAME, true));
        verify(mISupplicantStaIfaceMock, times(1)).setPowerSave(true);

        // Check that remote exception cleared all internal state.
        assertFalse(mDut.isInitializationComplete());

        // Ensure that further calls fail because the remote exception clears any state.
        assertFalse(mDut.setPowerSave(WLAN0_IFACE_NAME, true));
        verify(mISupplicantStaIfaceMock, times(1)).setPowerSave(true);
        //.. No call to ISupplicantStaIface object

        // Now trigger a death notification and ensure it's handled.
        assertNotNull(mSupplicantDeathCaptor.getValue());
        mSupplicantDeathCaptor.getValue().binderDied(mServiceBinderMock);
        mLooper.dispatchAll();

        // External death notification fires only once!
        verify(mSupplicantHalDeathHandler).onDeath();
    }

    /**
     * Tests the setting of log level.
     */
    @Test
    public void testSetLogLevel() throws Exception {
        doNothing().when(mISupplicantMock).setDebugParams(anyInt(), anyBoolean(), anyBoolean());

        // Fail before initialization is performed.
        assertFalse(mDut.setLogLevel(true));

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setLogLevel(true));
        verify(mISupplicantMock)
                .setDebugParams(eq(DebugLevel.DEBUG), eq(false), eq(false));
    }

    /**
     * Tests the setting of log level with show key enabled.
     */
    @Test
    public void testSetLogLevelWithShowKeyEnabled() throws Exception {
        when(mWifiGlobals.getShowKeyVerboseLoggingModeEnabled())
                .thenReturn(true);
        doNothing().when(mISupplicantMock).setDebugParams(anyInt(), anyBoolean(), anyBoolean());

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setLogLevel(true));
        verify(mISupplicantMock)
                .setDebugParams(eq(DebugLevel.DEBUG), eq(false), eq(true));
    }

    /**
     * Tests that show key is not enabled when verbose logging is not enabled.
     */
    @Test
    public void testVerboseLoggingDisabledWithShowKeyEnabled() throws Exception {
        when(mWifiGlobals.getShowKeyVerboseLoggingModeEnabled())
                .thenReturn(true);
        doNothing().when(mISupplicantMock).setDebugParams(anyInt(), anyBoolean(), anyBoolean());

        executeAndValidateInitializationSequence();

        // If verbose logging is not enabled, show key should not be enabled.
        assertTrue(mDut.setLogLevel(false));
        verify(mISupplicantMock, times(2))
                .setDebugParams(eq(DebugLevel.INFO), eq(false), eq(false));
    }

    /**
     * Tests the setting of concurrency priority.
     */
    @Test
    public void testConcurrencyPriority() throws Exception {
        doNothing().when(mISupplicantMock).setConcurrencyPriority(anyInt());

        // Fail before initialization is performed.
        assertFalse(mDut.setConcurrencyPriority(false));

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setConcurrencyPriority(false));
        verify(mISupplicantMock).setConcurrencyPriority(eq(IfaceType.P2P));
        assertTrue(mDut.setConcurrencyPriority(true));
        verify(mISupplicantMock).setConcurrencyPriority(eq(IfaceType.STA));
    }

    /**
     * Tests the start of wps registrar.
     */
    @Test
    public void testStartWpsRegistrar() throws Exception {
        doNothing().when(mISupplicantStaIfaceMock)
                .startWpsRegistrar(any(byte[].class), anyString());

        // Fail before initialization is performed.
        assertFalse(mDut.startWpsRegistrar(WLAN0_IFACE_NAME, null, null));

        executeAndValidateInitializationSequence();

        assertFalse(mDut.startWpsRegistrar(WLAN0_IFACE_NAME, null, null));
        verify(mISupplicantStaIfaceMock, never()).startWpsRegistrar(any(byte[].class), anyString());

        assertFalse(mDut.startWpsRegistrar(WLAN0_IFACE_NAME, new String(), "452233"));
        verify(mISupplicantStaIfaceMock, never()).startWpsRegistrar(any(byte[].class), anyString());

        assertTrue(mDut.startWpsRegistrar(WLAN0_IFACE_NAME, "45:23:12:12:12:98", "562535"));
        verify(mISupplicantStaIfaceMock).startWpsRegistrar(any(byte[].class), anyString());
    }

    /**
     * Tests the start of wps PBC.
     */
    @Test
    public void testStartWpsPbc() throws Exception {
        doNothing().when(mISupplicantStaIfaceMock).startWpsPbc(any(byte[].class));
        String bssid = "45:23:12:12:12:98";
        byte[] bssidBytes = {0x45, 0x23, 0x12, 0x12, 0x12, (byte) 0x98};
        byte[] anyBssidBytes = {0, 0, 0, 0, 0, 0};

        // Fail before initialization is performed.
        assertFalse(mDut.startWpsPbc(WLAN0_IFACE_NAME, bssid));
        verify(mISupplicantStaIfaceMock, never()).startWpsPbc(any(byte[].class));

        executeAndValidateInitializationSequence();

        assertTrue(mDut.startWpsPbc(WLAN0_IFACE_NAME, bssid));
        verify(mISupplicantStaIfaceMock).startWpsPbc(eq(bssidBytes));

        assertTrue(mDut.startWpsPbc(WLAN0_IFACE_NAME, null));
        verify(mISupplicantStaIfaceMock).startWpsPbc(eq(anyBssidBytes));
    }

    /**
     * Tests country code setter.
     */
    @Test
    public void testSetCountryCode() throws Exception {
        doNothing().when(mISupplicantStaIfaceMock).setCountryCode(any(byte[].class));
        String testCountryCode = "US";

        // Fail before initialization is performed.
        assertFalse(mDut.setCountryCode(WLAN0_IFACE_NAME, testCountryCode));
        verify(mISupplicantStaIfaceMock, never()).setCountryCode(any(byte[].class));

        executeAndValidateInitializationSequence();

        assertTrue(mDut.setCountryCode(WLAN0_IFACE_NAME, testCountryCode));
        verify(mISupplicantStaIfaceMock).setCountryCode(eq(testCountryCode.getBytes()));

        // Bad input values should fail the call.
        reset(mISupplicantStaIfaceMock);

        assertFalse(mDut.setCountryCode(WLAN0_IFACE_NAME, null));
        verify(mISupplicantStaIfaceMock, never()).setCountryCode(any(byte[].class));

        assertFalse(mDut.setCountryCode(WLAN0_IFACE_NAME, "U"));
        verify(mISupplicantStaIfaceMock, never()).setCountryCode(any(byte[].class));
    }

    /**
     * Tests the terminate function and ensures that its callback gets called.
     */
    @Test
    public void testTerminate() throws Exception {
        executeAndValidateInitializationSequence();

        mDut.terminate();
        verify(mISupplicantMock).terminate();

        // Check that all internal state is cleared once the death notification is received.
        assertTrue(mDut.isInitializationComplete());
        mSupplicantDeathCaptor.getValue().binderDied(mServiceBinderMock);
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Helper function for tests involving getAdvancedCapabilities.
     */
    private void checkKeyMgmtCapabilities(int serviceCapabilities, long expectedCapabilities)
            throws Exception {
        executeAndValidateInitializationSequence();
        doReturn(serviceCapabilities).when(mISupplicantStaIfaceMock).getKeyMgmtCapabilities();
        expectedCapabilities = addDefaultKeyMgmtCap(expectedCapabilities);
        assertEquals(expectedCapabilities, mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test WPA3-Personal SAE key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesWpa3Sae() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.SAE, WIFI_FEATURE_WPA3_SAE);
    }

    /**
     * Test WPA3-Enterprise Suite-B-192 key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesWpa3SuiteB() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.SUITE_B_192, WIFI_FEATURE_WPA3_SUITE_B);
    }

    /**
     * Test Enhanced Open (OWE) key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesOwe() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.OWE, WIFI_FEATURE_OWE);
    }

    /**
     * Test Enhanced Open (OWE) and SAE key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesOweAndSae() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.OWE | KeyMgmtMask.SAE,
                WIFI_FEATURE_OWE | WIFI_FEATURE_WPA3_SAE);
    }

    /**
     * Test Easy Connect (DPP) key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesDpp() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.DPP, WIFI_FEATURE_DPP
                        | WIFI_FEATURE_DPP_ENROLLEE_RESPONDER);
    }

    /**
     * Test WAPI key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesWapi() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.WAPI_PSK, WIFI_FEATURE_WAPI);
    }

    /**
     * Test FILS SHA256 key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesFilsSha256() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.FILS_SHA256, WIFI_FEATURE_FILS_SHA256);
    }

    /**
     * Test FILS SHA384 key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesFilsSha384() throws Exception {
        checkKeyMgmtCapabilities(KeyMgmtMask.FILS_SHA384, WIFI_FEATURE_FILS_SHA384);
    }

    /**
     * Test adding PMK cache entry to the supplicant.
     */
    @Test
    public void testSetPmkSuccess() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_EAP));
        ArrayList<Byte> pmkCacheData = NativeUtil.byteArrayToArrayList("deadbeef".getBytes());
        setupMocksForPmkCache(pmkCacheData);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager).get(eq(testFrameworkNetworkId));
        verify(mSupplicantStaNetworkMock).setPmkCache(eq(
                NativeUtil.byteArrayFromArrayList(pmkCacheData)));
        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<PmkSaCacheData> pmkSaCacheCaptor =
                    ArgumentCaptor.forClass(PmkSaCacheData.class);
            verify(mISupplicantStaIfaceCallback).onPmkSaCacheAdded(pmkSaCacheCaptor.capture());
            verifyOnPmkSaCacheAddedCallbackData(pmkCacheData, pmkSaCacheCaptor.getValue());
        } else {
            verify(mISupplicantStaIfaceCallback)
                    .onPmkCacheAdded(eq(PMK_CACHE_EXPIRATION_IN_SEC), eq(
                            NativeUtil.byteArrayFromArrayList(pmkCacheData)));
        }
    }

    /**
     * Test adding PMK cache entry to the supplicant when SAE is selected
     * for a PSK/SAE configuration.
     */
    @Test
    public void testSetPmkWhenSaeIsSelected() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.networkId = testFrameworkNetworkId;
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE));

        ArrayList<Byte> pmkCacheData = NativeUtil.byteArrayToArrayList("deadbeef".getBytes());
        byte[] cacheDataArr = NativeUtil.byteArrayFromArrayList(pmkCacheData);
        setupMocksForPmkCache(pmkCacheData);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager).get(eq(testFrameworkNetworkId));
        verify(mSupplicantStaNetworkMock).setPmkCache(eq(cacheDataArr));
        if (SdkLevel.isAtLeastU()) {
            ArgumentCaptor<PmkSaCacheData> pmkSaCacheCaptor =
                    ArgumentCaptor.forClass(PmkSaCacheData.class);
            verify(mISupplicantStaIfaceCallback).onPmkSaCacheAdded(pmkSaCacheCaptor.capture());
            verifyOnPmkSaCacheAddedCallbackData(pmkCacheData, pmkSaCacheCaptor.getValue());
        } else {
            verify(mISupplicantStaIfaceCallback)
                    .onPmkCacheAdded(eq(PMK_CACHE_EXPIRATION_IN_SEC), eq(cacheDataArr));
        }
    }

    /**
     * Test PMK cache entry is not added to the supplicant when PSK is selected
     * for a PSK/SAE configuration.
     */
    @Test
    public void testAddPmkEntryNotCalledIfPskIsSelected() throws Exception {
        int testFrameworkNetworkId = 9;

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.networkId = testFrameworkNetworkId;
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_PSK));

        setupMocksForPmkCache();
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any());
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkCacheAdded(anyLong(), any());
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkSaCacheAdded(any());
    }

    /**
     * Test PMK cache entry is not added to the supplicant if no security
     * params is selected.
     */
    @Test
    public void testAddPmkEntryNotCalledIfNoSecurityParamsIsSelected() throws Exception {
        int testFrameworkNetworkId = 9;

        WifiConfiguration config = WifiConfigurationTestUtil.createPskSaeNetwork();
        config.networkId = testFrameworkNetworkId;
        config.getNetworkSelectionStatus().setCandidateSecurityParams(null);

        setupMocksForPmkCache();
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any());
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkCacheAdded(anyLong(), any());
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkSaCacheAdded(any());
    }

    /**
     * Test adding PMK cache entry is not called if there is no
     * valid PMK cache for a corresponding configuration.
     */
    @Test
    public void testAddPmkEntryNotCalledIfNoPmkCache() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);

        setupMocksForPmkCache(null);
        setupMocksForConnectSequence(false);
        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any(byte[].class));
        verify(mISupplicantStaIfaceCallback, never()).onPmkCacheAdded(
                anyLong(), any(byte[].class));
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkSaCacheAdded(any());
    }

    /**
     * Tests that PMK cache entry is not added for PSK network.
     */
    @Test
    public void testAddPmkEntryIsOmittedWithPskNetwork() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);

        setupMocksForPmkCache();
        setupMocksForConnectSequence(false);
        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager, never()).add(any(), anyInt(), any(), anyLong(), any());
        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any(byte[].class));
        verify(mISupplicantStaIfaceCallback, never()).onPmkCacheAdded(
                anyLong(), any(byte[].class));
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkSaCacheAdded(any());
    }

    /**
     * Tests that PMK cache entry is not added for DPP network.
     */
    @Test
    public void testAddPmkEntryIsOmittedWithDppNetwork() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_DPP);

        setupMocksForPmkCache();
        setupMocksForConnectSequence(false);
        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager, never()).add(any(), anyInt(), any(), anyLong(), any());
        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any(byte[].class));
        verify(mISupplicantStaIfaceCallback, never()).onPmkCacheAdded(
                anyLong(), any(byte[].class));
        verify(mISupplicantStaIfaceCallback, never())
                .onPmkSaCacheAdded(any());
    }

    /**
     * Tests the handling of assoc reject for PMK cache
     */
    @Test
    public void testRemovePmkEntryOnReceivingAssocReject() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);

        setupMocksForPmkCache();
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        int statusCode = 7;
        AssociationRejectionData rejectionData = createAssocRejectData(SUPPLICANT_SSID, BSSID,
                statusCode, false);
        mISupplicantStaIfaceCallback.onAssociationRejected(rejectionData);
        verify(mPmkCacheManager).remove(eq(testFrameworkNetworkId));
    }

    /**
     * Test getConnectionCapabilities
     */
    @Test
    public void testGetConnectionCapabilities() throws Exception {
        executeAndValidateInitializationSequence();
        int testWifiStandardWifiInfo = ScanResult.WIFI_STANDARD_LEGACY;
        int testChannelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        int maxNumberTxSpatialStreams = 1;
        int maxNumberRxSpatialStreams = 1;

        ConnectionCapabilities halCap = new ConnectionCapabilities();
        halCap.technology = WifiTechnology.LEGACY;
        halCap.legacyMode = LegacyMode.B_MODE;
        halCap.channelBandwidth = WifiChannelWidthInMhz.WIDTH_20;
        halCap.maxNumberTxSpatialStreams = 1;
        halCap.maxNumberRxSpatialStreams = 1;
        if (SdkLevel.isAtLeastV()) {
            halCap.vendorData = HalTestUtils.createHalOuiKeyedDataList(5);
        }

        doReturn(halCap).when(mISupplicantStaIfaceMock).getConnectionCapabilities();
        WifiNative.ConnectionCapabilities expectedCap =
                mDut.getConnectionCapabilities(WLAN0_IFACE_NAME);
        assertEquals(testWifiStandardWifiInfo, expectedCap.wifiStandard);
        assertEquals(true, expectedCap.is11bMode);
        assertEquals(testChannelBandwidth, expectedCap.channelBandwidth);
        assertEquals(maxNumberTxSpatialStreams, expectedCap.maxNumberTxSpatialStreams);
        assertEquals(maxNumberRxSpatialStreams, expectedCap.maxNumberRxSpatialStreams);
        if (SdkLevel.isAtLeastV()) {
            assertTrue(HalTestUtils.ouiKeyedDataListEquals(
                    halCap.vendorData, expectedCap.vendorData));
        } else {
            assertTrue(expectedCap.vendorData.isEmpty());
        }
    }

    /**
     * Test Multi Band operation support (MBO).
     */
    @Test
    public void testGetWpaDriverCapabilitiesMbo() throws Exception {
        executeAndValidateInitializationSequence();
        doReturn(WpaDriverCapabilitiesMask.MBO).when(mISupplicantStaIfaceMock)
                .getWpaDriverCapabilities();
        assertEquals(WIFI_FEATURE_MBO, mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test Optimized Connectivity support (OCE).
     */
    @Test
    public void testGetWpaDriverCapabilitiesOce() throws Exception {
        executeAndValidateInitializationSequence();
        doReturn(WpaDriverCapabilitiesMask.MBO | WpaDriverCapabilitiesMask.OCE)
                .when(mISupplicantStaIfaceMock).getWpaDriverCapabilities();
        assertEquals(WIFI_FEATURE_MBO | WIFI_FEATURE_OCE,
                mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test Trust On First Use support (TOFU).
     */
    @Test
    public void testGetWpaDriverCapabilitiesTofu() throws Exception {
        executeAndValidateInitializationSequence();
        doReturn(WpaDriverCapabilitiesMask.TRUST_ON_FIRST_USE)
                .when(mISupplicantStaIfaceMock).getWpaDriverCapabilities();
        assertEquals(WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE,
                mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test the handling of BSS transition request callback.
     */
    @Test
    public void testBssTmHandlingDoneCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        mISupplicantStaIfaceCallback.onBssTmHandlingDone(new BssTmData());

        ArgumentCaptor<BtmFrameData> btmFrameDataCaptor =
                ArgumentCaptor.forClass(BtmFrameData.class);
        verify(mWifiMonitor).broadcastBssTmHandlingDoneEvent(
                eq(WLAN0_IFACE_NAME), btmFrameDataCaptor.capture());
    }

    /**
     * Tests the configuring of FILS HLP packet in supplicant.
     */
    @Test
    public void testAddHlpReq() throws Exception {
        byte[] dstAddr = {0x45, 0x23, 0x12, 0x12, 0x12, 0x45};
        byte[] hlpPacket = {0x00, 0x01, 0x02, 0x03, 0x04, 0x12, 0x15, 0x34, 0x55, 0x12,
                0x12, 0x45, 0x23, 0x52, 0x32, 0x16, 0x15, 0x53, 0x62, 0x32, 0x32, 0x10};

        doNothing().when(mISupplicantStaIfaceMock).filsHlpAddRequest(any(byte[].class),
                any(byte[].class));

        // Fail before initialization is performed.
        assertFalse(mDut.addHlpReq(WLAN0_IFACE_NAME, dstAddr, hlpPacket));
        verify(mISupplicantStaIfaceMock, never()).filsHlpAddRequest(any(byte[].class),
                any(byte[].class));

        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        assertTrue(mDut.addHlpReq(WLAN0_IFACE_NAME, dstAddr, hlpPacket));
        verify(mISupplicantStaIfaceMock).filsHlpAddRequest(eq(dstAddr), eq(hlpPacket));
    }

    /**
     * Tests the flushing of FILS HLP packet from supplicant.
     */
    @Test
    public void testFlushAllHlp() throws Exception {
        doNothing().when(mISupplicantStaIfaceMock).filsHlpFlushRequest();

        // Fail before initialization is performed.
        assertFalse(mDut.flushAllHlp(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).filsHlpFlushRequest();

        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        assertTrue(mDut.flushAllHlp(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock).filsHlpFlushRequest();
    }

    /**
     * Tests the handling of state change notification without
     * any configured network.
     */
    @Test
    public void testStateChangedCallbackWithNoConfiguredNetwork() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.INACTIVE,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);

        // Can't compare WifiSsid instances because they lack an equality operator.
        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiConfiguration.INVALID_NETWORK_ID),
                any(WifiSsid.class), eq(BSSID), eq(0), eq(SupplicantState.INACTIVE));
    }

    /**
     * Tests the handling of incorrect network passwords, edge case
     * when onStateChanged() is used.
     *
     * If the network is removed during 4-way handshake, do not call it a password mismatch.
     */
    @Test
    public void testNetworkRemovedDuring4wayWhenOnStateChangedIsUsed() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        int reasonCode = 3;

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onNetworkRemoved(SUPPLICANT_NETWORK_ID);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of incorrect network passwords when
     * onStateChanged() is used, edge case.
     *
     * If the disconnect reason is "IE in 4way differs", do not call it a password mismatch.
     */
    @Test
    public void testIeDiffersWhenOnStateChangedIsUsed() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        int reasonCode = StaIfaceReasonCode.IE_IN_4WAY_DIFFERS;

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of state change notification to
     * completed (with FILS HLP IE sent) after configuring a network.
     */
    @Test
    public void testStateChangeWithFilsHlpIESentToCompletedCallback() throws Exception {
        InOrder wifiMonitorInOrder = inOrder(mWifiMonitor);
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());

        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.COMPLETED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), true);

        wifiMonitorInOrder.verify(mWifiMonitor).broadcastNetworkConnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId), eq(true),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(null));
        wifiMonitorInOrder.verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(0), eq(SupplicantState.COMPLETED));
    }

    /**
     * Tests that we can disable a network after connecting to it.
     */
    @Test
    public void testDisableNetworkAfterConnected() throws Exception {
        when(mSupplicantStaNetworkMock.disable()).thenReturn(true);
        executeAndValidateInitializationSequence();

        // Connect to a network.
        executeAndValidateConnectSequence(4, false, TRANSLATED_SUPPLICANT_SSID.toString());

        // Disable it.
        assertTrue(mDut.disableCurrentNetwork(WLAN0_IFACE_NAME));
        verify(mSupplicantStaNetworkMock).disable();
    }

    /**
     * Tests the handling of association rejection notification.
     */
    @Test
    public void testAssociationRejectionCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        AssociationRejectionData assocRejectData = new AssociationRejectionData();
        assocRejectData.ssid = NativeUtil.byteArrayFromArrayList(
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        assocRejectData.bssid = NativeUtil.macAddressToByteArray(BSSID);
        assocRejectData.statusCode = 5;
        assocRejectData.isOceRssiBasedAssocRejectAttrPresent = true;
        assocRejectData.oceRssiBasedAssocRejectData = new OceRssiBasedAssocRejectAttr();
        assocRejectData.oceRssiBasedAssocRejectData.retryDelayS = 10;
        assocRejectData.oceRssiBasedAssocRejectData.deltaRssi = 20;
        mISupplicantStaIfaceCallback.onAssociationRejected(assocRejectData);

        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo = assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(SupplicantStaIfaceCallbackAidlImpl.halToFrameworkStatusCode(
                assocRejectData.statusCode), assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertNotNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertEquals(assocRejectData.oceRssiBasedAssocRejectData.retryDelayS,
                assocRejectEventInfo.oceRssiBasedAssocRejectInfo.mRetryDelayS);
        assertEquals(assocRejectData.oceRssiBasedAssocRejectData.deltaRssi,
                assocRejectEventInfo.oceRssiBasedAssocRejectInfo.mDeltaRssi);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Tests the handling of network not found notification.
     */
    @Test
    public void testNetworkNotFoundCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        executeAndValidateConnectSequence(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        mISupplicantStaIfaceCallback.onNetworkNotFound(NativeUtil.byteArrayFromArrayList(
                NativeUtil.decodeSsid(SUPPLICANT_SSID)));

        verify(mWifiMonitor).broadcastNetworkNotFoundEvent(
                eq(WLAN0_IFACE_NAME), eq(TRANSLATED_SUPPLICANT_SSID.toString()));
    }

    /**
     * Tests the handling of network not found notification.
     */
    @Test
    public void testNetworkNotFoundCallbackTriggersConnectToFallbackSsid() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        // Setup mocks to return two possible original SSIDs. We will pick
        // TRANSLATED_SUPPLICANT_SSID as the first SSID to try.
        when(mSsidTranslator.getAllPossibleOriginalSsids(TRANSLATED_SUPPLICANT_SSID)).thenAnswer(
                (Answer<List<WifiSsid>>) invocation -> {
                    List<WifiSsid> ssids = new ArrayList<>();
                    ssids.add(TRANSLATED_SUPPLICANT_SSID);
                    ssids.add(WifiSsid.fromString(SUPPLICANT_SSID));
                    return ssids;
                });
        executeAndValidateConnectSequence(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        mISupplicantStaIfaceCallback.onNetworkNotFound(NativeUtil.byteArrayFromArrayList(
                NativeUtil.decodeSsid(SUPPLICANT_SSID)));

        // Validate that we initiated another connect sequence to the fallback SUPPLICANT_SSID.
        verify(mWifiMonitor, never()).broadcastNetworkNotFoundEvent(
                eq(WLAN0_IFACE_NAME), eq(TRANSLATED_SUPPLICANT_SSID.toString()));
        validateConnectSequence(false, 2, SUPPLICANT_SSID);

        // Fallback SSID was not found, broadcast the network not found event now.
        mISupplicantStaIfaceCallback.onNetworkNotFound(NativeUtil.byteArrayFromArrayList(
                NativeUtil.decodeSsid(SUPPLICANT_SSID)));
        verify(mWifiMonitor).broadcastNetworkNotFoundEvent(
                eq(WLAN0_IFACE_NAME), eq(TRANSLATED_SUPPLICANT_SSID.toString()));
    }

    /**
     * Tests the behavior of {@link SupplicantStaIfaceHal#getCurrentNetworkSecurityParams(String)}
     * @throws Exception
     */
    @Test
    public void testGetCurrentNetworkSecurityParams() throws Exception {
        executeAndValidateInitializationSequence();

        // Null current network should return null security params
        assertNull(mDut.getCurrentNetworkSecurityParams(WLAN0_IFACE_NAME));

        // Connecting to network with PSK candidate security params should return PSK params.
        executeAndValidateConnectSequenceWithKeyMgmt(0, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_PSK,
                "97CA326539");
        assertTrue(mDut.getCurrentNetworkSecurityParams(WLAN0_IFACE_NAME)
                .isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK));
    }

    /**
     * Tests the behavior of
     * {@link SupplicantStaIfaceHal#sendQosPolicyResponse(String, int, boolean, List)}
     * @throws Exception
     */
    @Test
    public void testSendQosPolicyResponse() throws Exception {
        final int policyRequestId = 124;
        final boolean morePolicies = true;
        ArgumentCaptor<QosPolicyStatus[]> policyStatusListCaptor =
                ArgumentCaptor.forClass(QosPolicyStatus[].class);

        List<SupplicantStaIfaceHal.QosPolicyStatus> policyStatusList = new ArrayList();
        policyStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                1, NetworkAgent.DSCP_POLICY_STATUS_SUCCESS));
        policyStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                2, NetworkAgent.DSCP_POLICY_STATUS_REQUEST_DECLINED));
        policyStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                3, NetworkAgent.DSCP_POLICY_STATUS_REQUESTED_CLASSIFIER_NOT_SUPPORTED));

        QosPolicyStatus[] expectedHalPolicyStatusList = {
                createQosPolicyStatus(1, QosPolicyStatusCode.QOS_POLICY_SUCCESS),
                createQosPolicyStatus(2, QosPolicyStatusCode.QOS_POLICY_REQUEST_DECLINED),
                createQosPolicyStatus(3, QosPolicyStatusCode.QOS_POLICY_CLASSIFIER_NOT_SUPPORTED)};

        executeAndValidateInitializationSequence();
        mDut.sendQosPolicyResponse(WLAN0_IFACE_NAME, policyRequestId, morePolicies,
                policyStatusList);
        verify(mISupplicantStaIfaceMock).sendQosPolicyResponse(eq(policyRequestId),
                eq(morePolicies), policyStatusListCaptor.capture());
        assertTrue(qosPolicyStatusListsAreEqual(expectedHalPolicyStatusList,
                policyStatusListCaptor.getValue()));
    }

    /**
     * Tests the behavior of
     * {@link SupplicantStaIfaceCallbackAidlImpl#onQosPolicyReset()}
     * @throws Exception
     */
    @Test
    public void executeAndValidateQosPolicyResetCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onQosPolicyReset();
        verify(mWifiMonitor).broadcastQosPolicyResetEvent(eq(WLAN0_IFACE_NAME));
    }

    /**
     * Tests the behavior of
     * {@link SupplicantStaIfaceCallbackAidlImpl#onQosPolicyRequest(int, QosPolicyData[])}
     * @throws Exception
     */
    @Test
    public void executeAndValidateQosPolicyRequestCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        final int dialogToken = 124;
        final int srcPort = 1337;
        QosPolicyData qosPolicyData1 = createQosPolicyData(0,
                QosPolicyRequestType.QOS_POLICY_ADD, 0, null, null, srcPort, null, null);
        QosPolicyData qosPolicyData2 = createQosPolicyData(1,
                QosPolicyRequestType.QOS_POLICY_REMOVE, 0, null, null, null, null, null);
        QosPolicyData[] qosPolicyDataList = new QosPolicyData[]{qosPolicyData1, qosPolicyData2};

        mISupplicantStaIfaceCallback.onQosPolicyRequest(dialogToken, qosPolicyDataList);
        verify(mWifiMonitor).broadcastQosPolicyRequestEvent(eq(WLAN0_IFACE_NAME),
                eq(dialogToken), mQosPolicyRequestListCaptor.capture());

        List<SupplicantStaIfaceHal.QosPolicyRequest> qosPolicyRequestList =
                mQosPolicyRequestListCaptor.getValue();
        assertEquals(qosPolicyRequestList.size(), qosPolicyDataList.length);

        assertEquals(0, qosPolicyRequestList.get(0).policyId);
        assertEquals(SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD,
                qosPolicyRequestList.get(0).requestType);
        assertEquals(srcPort, qosPolicyRequestList.get(0).classifierParams.srcPort);

        assertEquals(1, qosPolicyRequestList.get(1).policyId);
        assertEquals(SupplicantStaIfaceHal.QOS_POLICY_REQUEST_REMOVE,
                qosPolicyRequestList.get(1).requestType);
    }

    /**
     * Tests the conversion method
     * {@link SupplicantStaIfaceHalAidlImpl#frameworkToHalQosPolicyScsData(QosPolicyParams)}
     */
    @Test
    public void testFrameworkToHalQosPolicyScsData() throws Exception {
        byte translatedPolicyId = 15;
        QosPolicyParams frameworkPolicy = new QosPolicyParams.Builder(
                5 /* policyId */, QosPolicyParams.DIRECTION_DOWNLINK)
                .setSourceAddress(InetAddress.getByName("127.0.0.1"))
                .setDestinationAddress(InetAddress.getByName("127.0.0.2"))
                .setDscp(25)
                .setUserPriority(QosPolicyParams.USER_PRIORITY_BACKGROUND_HIGH)
                .setIpVersion(QosPolicyParams.IP_VERSION_4)
                .setSourcePort(17)
                .setProtocol(QosPolicyParams.PROTOCOL_TCP)
                .setDestinationPort(10)
                .build();
        frameworkPolicy.setTranslatedPolicyId(translatedPolicyId);
        QosPolicyScsData halPolicy = mDut.frameworkToHalQosPolicyScsData(frameworkPolicy);
        compareQosPolicyParamsToHal(frameworkPolicy, halPolicy);
    }

    /**
     * Tests the conversion method
     * {@link SupplicantStaIfaceHalAidlImpl#frameworkToHalQosPolicyScsData(QosPolicyParams)}
     * when the instance contains QosCharacteristics.
     */
    @Test
    public void testFrameworkToHalQosPolicyScsDataWithCharacteristics() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mISupplicantMock.getInterfaceVersion()).thenReturn(3);
        assertTrue(mDut.startDaemon()); // retrieves and caches the interface version

        QosCharacteristics frameworkChars = new QosCharacteristics.Builder(
                2000, 5000, 500, 2)
                .setMaxMsduSizeOctets(4)
                .setServiceStartTimeInfo(250, 0x5)
                .setMeanDataRateKbps(1500)
                .setMsduLifetimeMillis(400)
                .setMsduDeliveryInfo(QosCharacteristics.DELIVERY_RATIO_99, 5)
                .build();
        QosPolicyParams frameworkPolicy = new QosPolicyParams.Builder(
                5 /* policyId */, QosPolicyParams.DIRECTION_UPLINK)
                .setQosCharacteristics(frameworkChars)
                .build();
        frameworkPolicy.setTranslatedPolicyId(15);
        QosPolicyScsData halPolicy = mDut.frameworkToHalQosPolicyScsData(frameworkPolicy);
        compareQosPolicyParamsToHal(frameworkPolicy, halPolicy);
    }

    private void verifySetEapAnonymousIdentity(boolean updateToNativeService)
            throws Exception {
        int testFrameworkNetworkId = 9;
        String anonymousIdentity = "abc@realm.org";
        byte[] bytes = anonymousIdentity.getBytes();
        when(mSupplicantStaNetworkMock.setEapAnonymousIdentity(any()))
                .thenReturn(true);

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);

        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        config.enterpriseConfig.setAnonymousIdentity(anonymousIdentity);
        // Check the data are sent to the native service.
        assertTrue(mDut.setEapAnonymousIdentity(WLAN0_IFACE_NAME, anonymousIdentity,
                updateToNativeService));
        if (updateToNativeService) {
            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(mSupplicantStaNetworkMock).setEapAnonymousIdentity(captor.capture());
            assertTrue(Arrays.equals(bytes, captor.getValue()));
        } else {
            verify(mSupplicantStaNetworkMock, never()).setEapAnonymousIdentity(any());
        }

        // Clear the first connection interaction.
        reset(mISupplicantStaIfaceMock);
        setupMocksForConnectSequence(true);
        // Check the cached value is updated, and this
        // config should be considered the same network.
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        verify(mISupplicantStaIfaceMock, never()).removeNetwork(anyInt());
        verify(mISupplicantStaIfaceMock, never()).addNetwork();
    }

    /**
     * Tests the setting of EAP anonymous identity.
     */
    @Test
    public void testSetEapAnonymousIdentity() throws Exception {
        verifySetEapAnonymousIdentity(true);
    }

    /**
     * Tests the setting of EAP anonymous identity.
     */
    @Test
    public void testSetEapAnonymousIdentityNotUpdateToNativeService() throws Exception {
        verifySetEapAnonymousIdentity(false);
    }

    private WifiConfiguration createTestWifiConfiguration() {
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = SUPPLICANT_NETWORK_ID;
        return config;
    }

    private QosPolicyStatus createQosPolicyStatus(int policyId, int status) {
        QosPolicyStatus policyStatus = new QosPolicyStatus();
        policyStatus.policyId = (byte) policyId;
        policyStatus.status = (byte) status;
        return policyStatus;
    }

    private QosPolicyData createQosPolicyData(int policyId, int requestType, int dscp,
            @Nullable byte[] srcIp, @Nullable byte[] dstIp, @Nullable Integer srcPort,
            @Nullable int[] dstPortRange, @Nullable Integer protocol) {
        QosPolicyClassifierParams classifierParams = new QosPolicyClassifierParams();
        int paramMask = 0;
        if (srcIp != null) {
            classifierParams.srcIp = srcIp;
            paramMask |= QosPolicyClassifierParamsMask.SRC_IP;
        }
        if (dstIp != null) {
            classifierParams.dstIp = dstIp;
            paramMask |= QosPolicyClassifierParamsMask.DST_IP;
        }
        if (srcPort != null) {
            classifierParams.srcPort = srcPort;
            paramMask |= QosPolicyClassifierParamsMask.SRC_PORT;
        }
        if (dstPortRange != null && dstPortRange.length == 2) {
            PortRange portRange = new PortRange();
            portRange.startPort = dstPortRange[0];
            portRange.endPort = dstPortRange[1];
            classifierParams.dstPortRange = portRange;
            paramMask |= QosPolicyClassifierParamsMask.DST_PORT_RANGE;
        }
        if (protocol != null) {
            classifierParams.protocolNextHdr = protocol.byteValue();
            paramMask |= QosPolicyClassifierParamsMask.PROTOCOL_NEXT_HEADER;
        }
        classifierParams.classifierParamMask = paramMask;

        QosPolicyData qosPolicyData = new QosPolicyData();
        qosPolicyData.policyId = (byte) policyId;
        qosPolicyData.requestType = (byte) requestType;
        qosPolicyData.dscp = (byte) dscp;
        qosPolicyData.classifierParams = classifierParams;
        return qosPolicyData;
    }

    private static void compareFrameworkQosCharacteristicsToHal(
            android.net.wifi.QosCharacteristics frameworkChars,
            android.hardware.wifi.supplicant.QosCharacteristics halChars) {
        assertEquals(frameworkChars.getMinServiceIntervalMicros(), halChars.minServiceIntervalUs);
        assertEquals(frameworkChars.getMaxServiceIntervalMicros(), halChars.maxServiceIntervalUs);
        assertEquals(frameworkChars.getMinDataRateKbps(), halChars.minDataRateKbps);
        assertEquals(frameworkChars.getDelayBoundMicros(), halChars.delayBoundUs);

        int paramsMask = halChars.optionalFieldMask;
        if (frameworkChars.containsOptionalField(
                android.net.wifi.QosCharacteristics.MAX_MSDU_SIZE)) {
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.MAX_MSDU_SIZE);
            assertEquals((char) frameworkChars.getMaxMsduSizeOctets(), halChars.maxMsduSizeOctets);
        }
        if (frameworkChars.containsOptionalField(
                android.net.wifi.QosCharacteristics.SERVICE_START_TIME)) {
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.SERVICE_START_TIME);
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.SERVICE_START_TIME_LINK_ID);
            assertEquals(frameworkChars.getServiceStartTimeMicros(), halChars.serviceStartTimeUs);
            assertEquals((byte) frameworkChars.getServiceStartTimeLinkId(),
                    halChars.serviceStartTimeLinkId);
        }
        if (frameworkChars.containsOptionalField(
                android.net.wifi.QosCharacteristics.MEAN_DATA_RATE)) {
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.MEAN_DATA_RATE);
            assertEquals(frameworkChars.getMeanDataRateKbps(), halChars.meanDataRateKbps);
        }
        if (frameworkChars.containsOptionalField(
                android.net.wifi.QosCharacteristics.BURST_SIZE)) {
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.BURST_SIZE);
            assertEquals(frameworkChars.getBurstSizeOctets(), halChars.burstSizeOctets);
        }
        if (frameworkChars.containsOptionalField(
                android.net.wifi.QosCharacteristics.MSDU_LIFETIME)) {
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.MSDU_LIFETIME);
            assertEquals((char) frameworkChars.getMsduLifetimeMillis(), halChars.msduLifetimeMs);
        }
        if (frameworkChars.containsOptionalField(
                android.net.wifi.QosCharacteristics.MSDU_DELIVERY_INFO)) {
            assertNotEquals(0, paramsMask & QosCharacteristicsMask.MSDU_DELIVERY_INFO);
            MsduDeliveryInfo halDeliveryInfo = halChars.msduDeliveryInfo;
            int convertedFrameworkRatio =
                    SupplicantStaIfaceHalAidlImpl.frameworkToHalDeliveryRatio(
                            frameworkChars.getDeliveryRatio());
            assertEquals(convertedFrameworkRatio, halDeliveryInfo.deliveryRatio);
            assertEquals((byte) frameworkChars.getCountExponent(), halDeliveryInfo.countExponent);
        }
    }

    private void compareQosPolicyParamsToHal(QosPolicyParams frameworkPolicy,
            QosPolicyScsData halPolicy) {
        assertEquals((byte) frameworkPolicy.getTranslatedPolicyId(), halPolicy.policyId);
        assertEquals((byte) frameworkPolicy.getUserPriority(), halPolicy.userPriority);

        QosPolicyClassifierParams classifierParams = halPolicy.classifierParams;
        int paramsMask = classifierParams.classifierParamMask;

        if (frameworkPolicy.getSourceAddress() != null) {
            assertNotEquals(0, paramsMask & QosPolicyClassifierParamsMask.SRC_IP);
            assertArrayEquals(frameworkPolicy.getSourceAddress().getAddress(),
                    classifierParams.srcIp);
        }
        if (frameworkPolicy.getDestinationAddress() != null) {
            assertNotEquals(0, paramsMask & QosPolicyClassifierParamsMask.DST_IP);
            assertArrayEquals(frameworkPolicy.getDestinationAddress().getAddress(),
                    classifierParams.dstIp);
        }
        if (frameworkPolicy.getSourcePort() != DscpPolicy.SOURCE_PORT_ANY) {
            assertNotEquals(0, paramsMask & QosPolicyClassifierParamsMask.SRC_PORT);
            assertEquals(frameworkPolicy.getSourcePort(), classifierParams.srcPort);
        }
        if (frameworkPolicy.getDestinationPortRange() != null) {
            assertNotEquals(0, paramsMask & QosPolicyClassifierParamsMask.DST_PORT_RANGE);
            PortRange portRange = classifierParams.dstPortRange;
            int[] halDstPortRange = new int[]{portRange.startPort, portRange.endPort};
            assertArrayEquals(frameworkPolicy.getDestinationPortRange(), halDstPortRange);
        }
        if (frameworkPolicy.getProtocol() != QosPolicyParams.PROTOCOL_ANY) {
            assertNotEquals(0, paramsMask & QosPolicyClassifierParamsMask.PROTOCOL_NEXT_HEADER);
            assertEquals((byte) frameworkPolicy.getProtocol(), classifierParams.protocolNextHdr);
        }
        if (frameworkPolicy.getDscp() != QosPolicyParams.DSCP_ANY) {
            assertNotEquals(0, paramsMask & QosPolicyClassifierParamsMask.DSCP);
            assertEquals((byte) frameworkPolicy.getDscp(), classifierParams.dscp);
        }

        if (mDut.isServiceVersionAtLeast(3)) {
            int convertedFrameworkDirection =
                    SupplicantStaIfaceHalAidlImpl.frameworkToHalPolicyDirection(
                            frameworkPolicy.getDirection());
            assertEquals(convertedFrameworkDirection, halPolicy.direction);
            if (frameworkPolicy.getQosCharacteristics() != null) {
                compareFrameworkQosCharacteristicsToHal(
                        frameworkPolicy.getQosCharacteristics(), halPolicy.QosCharacteristics);
            }
        }
    }

    /**
     * Indicate support for key mgmt features supported by default in HIDL HAL V1.4,
     * i.e. the latest HIDL version before the conversion to AIDL.
     */
    private long addDefaultKeyMgmtCap(long capabilities) {
        return capabilities | WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS
                | WIFI_FEATURE_DECORATED_IDENTITY;
    }

    /**
     * Create AssociationRejectionData to send to the onAssociationRejected callback.
     * Used for test cases that originally targeted the old 3-argument version of
     * the callback, but were adapted to test the implementation that requires a single
     * AssociationRejectionData argument.
     */
    private AssociationRejectionData createAssocRejectData(String ssid, String bssid,
            int statusCode, boolean timedOut) {
        AssociationRejectionData rejectionData = new AssociationRejectionData();
        rejectionData.ssid = NativeUtil.byteArrayFromArrayList(
                NativeUtil.decodeSsid(ssid));
        rejectionData.bssid = NativeUtil.macAddressToByteArray(bssid);
        rejectionData.statusCode = statusCode;
        rejectionData.timedOut = timedOut;
        rejectionData.isMboAssocDisallowedReasonCodePresent = false;
        rejectionData.isOceRssiBasedAssocRejectAttrPresent = false;
        return rejectionData;
    }

    private void executeAndValidateHs20DeauthImminentCallback(boolean isEss) throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        int reasonCode = isEss ? WnmData.ESS : WnmData.ESS + 1;
        int reauthDelay = 5;
        mISupplicantStaIfaceCallback.onHs20DeauthImminentNotice(
                bssid, reasonCode, reauthDelay, HS20_URL);

        ArgumentCaptor<WnmData> wnmDataCaptor = ArgumentCaptor.forClass(WnmData.class);
        verify(mWifiMonitor).broadcastWnmEvent(eq(WLAN0_IFACE_NAME), wnmDataCaptor.capture());
        assertEquals(
                ByteBufferReader.readInteger(
                        ByteBuffer.wrap(bssid), ByteOrder.BIG_ENDIAN, bssid.length),
                wnmDataCaptor.getValue().getBssid());
        assertEquals(isEss, wnmDataCaptor.getValue().isEss());
        assertEquals(reauthDelay, wnmDataCaptor.getValue().getDelay());
        assertEquals(HS20_URL, wnmDataCaptor.getValue().getUrl());
    }

    private void executeAndValidateHs20TermsAndConditionsCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        mISupplicantStaIfaceCallback.onHs20TermsAndConditionsAcceptanceRequestedNotification(
                bssid, HS20_URL);

        ArgumentCaptor<WnmData> wnmDataCaptor = ArgumentCaptor.forClass(WnmData.class);
        verify(mWifiMonitor).broadcastWnmEvent(eq(WLAN0_IFACE_NAME), wnmDataCaptor.capture());
        assertEquals(
                ByteBufferReader.readInteger(
                        ByteBuffer.wrap(bssid), ByteOrder.BIG_ENDIAN, bssid.length),
                wnmDataCaptor.getValue().getBssid());
        assertEquals(HS20_URL, wnmDataCaptor.getValue().getUrl());
    }

    /**
     * Wrapper for successful initialization with executeAndValidateInitializationSequence.
     */
    private void executeAndValidateInitializationSequence() throws Exception {
        executeAndValidateInitializationSequence(false, false);
    }

    /**
     * Calls initialize and addP2pInterface to mock the startup sequence.
     * The two arguments will each trigger a different failure in addStaInterface
     * when set to true.
     */
    void executeAndValidateInitializationSequence(boolean causeRemoteException,
            boolean getNullInterface) throws Exception {
        boolean shouldSucceed = !causeRemoteException && !getNullInterface;
        if (causeRemoteException) {
            doThrow(new RemoteException()).when(mISupplicantMock).addStaInterface(anyString());
        } else if (getNullInterface) {
            doReturn(null).when(mISupplicantMock).addStaInterface(anyString());
        } else {
            doReturn(mISupplicantStaIfaceMock).when(mISupplicantMock).addStaInterface(anyString());
        }

        /** Callback registration */
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIfaceCallback cb) throws RemoteException {
                mISupplicantStaIfaceCallback = spy(cb);
            }
        }).when(mISupplicantStaIfaceMock).registerCallback(any(ISupplicantStaIfaceCallback.class));

        // Initialize the SupplicantStaIfaceHal
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        verify(mISupplicantMock).getInterfaceVersion();
        verify(mServiceBinderMock).linkToDeath(mSupplicantDeathCaptor.capture(), anyInt());
        verify(mISupplicantMock).registerNonStandardCertCallback(any());
        assertTrue(mDut.isInitializationComplete());

        // Attempt to setup the interface
        assertEquals(shouldSucceed, mDut.setupIface(WLAN0_IFACE_NAME));
        verify(mISupplicantMock).addStaInterface(anyString());
        if (!causeRemoteException && !getNullInterface) {
            verify(mISupplicantStaIfaceMock).registerCallback(
                    any(ISupplicantStaIfaceCallback.class));
        }
    }

    /**
     * Create an IfaceInfo with given type and name
     */
    private IfaceInfo createIfaceInfo(int type, String name) {
        IfaceInfo info = new IfaceInfo();
        info.type = type;
        info.name = name;
        return info;
    }

    /**
     * Setup mocks for connect sequence.
     */
    private void setupMocksForConnectSequence(final boolean haveExistingNetwork) throws Exception {
        final int existingNetworkId = SUPPLICANT_NETWORK_ID;
        doNothing().when(mISupplicantStaIfaceMock).disconnect();
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public int[] answer() throws RemoteException {
                if (haveExistingNetwork) {
                    int[] networks = {existingNetworkId};
                    return networks;
                } else {
                    return new int[0];
                }
            }
        }).when(mISupplicantStaIfaceMock).listNetworks();
        doNothing().when(mISupplicantStaIfaceMock).removeNetwork(eq(existingNetworkId));
        doReturn(mock(ISupplicantStaNetwork.class)).when(mISupplicantStaIfaceMock).addNetwork();
        doReturn(true).when(mSupplicantStaNetworkMock)
                .saveWifiConfiguration(any(WifiConfiguration.class));
        doReturn(true).when(mSupplicantStaNetworkMock).select();
    }

    /**
     * Helper function to validate the connect sequence.
     */
    private void validateConnectSequence(
            final boolean haveExistingNetwork, int numNetworkAdditions, String ssid)
            throws Exception {
        if (haveExistingNetwork) {
            verify(mISupplicantStaIfaceMock).removeNetwork(anyInt());
        }
        verify(mISupplicantStaIfaceMock, times(numNetworkAdditions)).addNetwork();
        ArgumentCaptor<WifiConfiguration> configCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mSupplicantStaNetworkMock, times(numNetworkAdditions))
                .saveWifiConfiguration(configCaptor.capture());
        assertTrue(TextUtils.equals(configCaptor.getValue().SSID, ssid));
        verify(mSupplicantStaNetworkMock, times(numNetworkAdditions)).select();
        verify(mSsidTranslator).setTranslatedSsidForStaIface(any(), anyString());
    }

    /**
     * Helper function to execute all the actions to perform connection to the network.
     *
     * @param newFrameworkNetworkId Framework Network Id of the new network to connect.
     * @param haveExistingNetwork Removes the existing network.
     * @param ssid Raw SSID to send to supplicant.
     * @return the WifiConfiguration object of the new network to connect.
     */
    private WifiConfiguration executeAndValidateConnectSequence(
            final int newFrameworkNetworkId, final boolean haveExistingNetwork,
            String ssid) throws Exception {
        return executeAndValidateConnectSequenceWithKeyMgmt(newFrameworkNetworkId,
                haveExistingNetwork, ssid, WifiConfiguration.SECURITY_TYPE_PSK, null);
    }

    /**
     * Helper function to execute all the actions to perform connection to the network.
     *
     * @param newFrameworkNetworkId Framework Network Id of the new network to connect.
     * @param haveExistingNetwork Removes the existing network.
     * @param ssid Raw SSID to send to supplicant.
     * @param securityType The security type.
     * @param wepKey if configurations are for a WEP network else null.
     * @param hasEverConnected indicate that this configuration is ever connected or not.
     * @return the WifiConfiguration object of the new network to connect.
     */
    private WifiConfiguration executeAndValidateConnectSequenceWithKeyMgmt(
            final int newFrameworkNetworkId, final boolean haveExistingNetwork,
            String ssid, int securityType, String wepKey, boolean hasEverConnected)
            throws Exception {
        setupMocksForConnectSequence(haveExistingNetwork);
        WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(securityType);
        config.networkId = newFrameworkNetworkId;
        config.SSID = ssid;
        config.wepKeys[0] = wepKey;
        config.wepTxKeyIndex = 0;
        WifiConfiguration.NetworkSelectionStatus networkSelectionStatus =
                new WifiConfiguration.NetworkSelectionStatus();
        networkSelectionStatus.setCandidateSecurityParams(config.getSecurityParams(securityType));
        networkSelectionStatus.setHasEverConnected(hasEverConnected);
        config.setNetworkSelectionStatus(networkSelectionStatus);
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        validateConnectSequence(haveExistingNetwork, 1, config.SSID);
        return config;
    }

    /**
     * Helper function to execute all the actions to perform connection to the network.
     *
     * @param newFrameworkNetworkId Framework Network Id of the new network to connect.
     * @param haveExistingNetwork Removes the existing network.
     * @param ssid Raw SSID to send to supplicant.
     * @param securityType The security type.
     * @param wepKey if configurations are for a WEP network else null.
     * @return the WifiConfiguration object of the new network to connect.
     */
    private WifiConfiguration executeAndValidateConnectSequenceWithKeyMgmt(
            final int newFrameworkNetworkId, final boolean haveExistingNetwork,
            String ssid, int securityType, String wepKey) throws Exception {
        return executeAndValidateConnectSequenceWithKeyMgmt(
                newFrameworkNetworkId, haveExistingNetwork,
                ssid, securityType, wepKey, false);
    }

    /**
     * Setup mocks for roam sequence.
     */
    private void setupMocksForRoamSequence(String roamBssid) throws Exception {
        doNothing().when(mISupplicantStaIfaceMock).reassociate();
        when(mSupplicantStaNetworkMock.setBssid(eq(roamBssid))).thenReturn(true);
    }

    /**
     * Helper function to execute all the actions to perform roaming to the network.
     *
     * @param sameNetwork Roam to the same network or not.
     * @param linkedNetwork Roam to linked network or not.
     */
    private void executeAndValidateRoamSequence(boolean sameNetwork, boolean linkedNetwork)
            throws Exception {
        int connectedNetworkId = ROAM_NETWORK_ID;
        String roamBssid = BSSID;
        int roamNetworkId;
        if (sameNetwork) {
            roamNetworkId = connectedNetworkId;
        } else {
            roamNetworkId = connectedNetworkId + 1;
        }
        executeAndValidateConnectSequence(connectedNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        setupMocksForRoamSequence(roamBssid);

        WifiConfiguration roamingConfig = new WifiConfiguration();
        roamingConfig.networkId = roamNetworkId;
        roamingConfig.getNetworkSelectionStatus().setNetworkSelectionBSSID(roamBssid);
        SupplicantStaNetworkHalAidlImpl linkedNetworkHandle =
                mock(SupplicantStaNetworkHalAidlImpl.class);
        if (linkedNetwork) {
            // Set the StaNetworkMockable to add a new handle for the linked network
            int roamRemoteNetworkId = roamNetworkId + 1;
            when(linkedNetworkHandle.getNetworkId()).thenReturn(roamRemoteNetworkId);
            when(linkedNetworkHandle.saveWifiConfiguration(any())).thenReturn(true);
            when(linkedNetworkHandle.select()).thenReturn(true);
            mDut.setStaNetworkMockable(linkedNetworkHandle);
            final HashMap<String, WifiConfiguration> linkedNetworks = new HashMap<>();
            linkedNetworks.put(roamingConfig.getProfileKey(), roamingConfig);
            assertTrue(mDut.updateLinkedNetworks(
                    WLAN0_IFACE_NAME, connectedNetworkId, linkedNetworks));
        }
        assertTrue(mDut.roamToNetwork(WLAN0_IFACE_NAME, roamingConfig));

        if (sameNetwork) {
            verify(mSupplicantStaNetworkMock).setBssid(eq(roamBssid));
            verify(mISupplicantStaIfaceMock).reassociate();
        } else if (linkedNetwork) {
            verify(mISupplicantStaIfaceMock, never()).removeNetwork(anyInt());
            verify(mISupplicantStaIfaceMock, times(2)).addNetwork();
            verify(mSupplicantStaNetworkMock).saveWifiConfiguration(any(WifiConfiguration.class));
            verify(mSupplicantStaNetworkMock).select();
            verify(linkedNetworkHandle).saveWifiConfiguration(any(WifiConfiguration.class));
            verify(linkedNetworkHandle).select();
            verify(mSupplicantStaNetworkMock, never()).setBssid(anyString());
            verify(mISupplicantStaIfaceMock, never()).reassociate();
        } else {
            validateConnectSequence(false, 2, null);
            verify(mSupplicantStaNetworkMock, never()).setBssid(anyString());
            verify(mISupplicantStaIfaceMock, never()).reassociate();
        }
    }

    private void setupMocksForPmkCache() throws Exception {
        ArrayList<Byte> pmkCacheData = NativeUtil.byteArrayToArrayList("deadbeef".getBytes());
        setupMocksForPmkCache(pmkCacheData);
    }

    private void setupMocksForPmkCache(ArrayList<Byte> pmkCacheData) throws Exception {
        mDut.mPmkCacheManager = mPmkCacheManager;
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public List<ArrayList<Byte>> answer(int networkId) {
                if (pmkCacheData == null) return null;

                List<ArrayList<Byte>> pmkDataList = new ArrayList<>();
                pmkDataList.add(pmkCacheData);
                return pmkDataList;
            }
        }).when(mPmkCacheManager)
                .get(anyInt());
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra)
                    throws Exception {
                config.networkId = SUPPLICANT_NETWORK_ID;
                return true;
            }
        }).when(mSupplicantStaNetworkMock)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));

        if (SdkLevel.isAtLeastU()) {
            doAnswer(new MockAnswerUtil.AnswerWithArguments() {
                public boolean answer(byte[] serializedData) throws Exception {
                    mISupplicantStaIfaceCallback.onPmkSaCacheAdded(createPmkSaCacheData(
                            BSSID, PMK_CACHE_EXPIRATION_IN_SEC, serializedData));
                    return true;
                }
            }).when(mSupplicantStaNetworkMock)
                    .setPmkCache(any(byte[].class));
        } else {
            doAnswer(new MockAnswerUtil.AnswerWithArguments() {
                public boolean answer(byte[] serializedData) throws Exception {
                    mISupplicantStaIfaceCallback.onPmkCacheAdded(
                            PMK_CACHE_EXPIRATION_IN_SEC, serializedData);
                    return true;
                }
            }).when(mSupplicantStaNetworkMock)
                    .setPmkCache(any(byte[].class));
        }
    }

    private PmkSaCacheData createPmkSaCacheData(String bssid,
            long expirationTimeInSec, byte[] serializedEntry) {
        PmkSaCacheData pmkSaData = new PmkSaCacheData();
        pmkSaData.bssid = NativeUtil.macAddressToByteArray(bssid);
        pmkSaData.expirationTimeInSec = expirationTimeInSec;
        pmkSaData.serializedEntry = serializedEntry;
        return pmkSaData;
    }

    private void verifyOnPmkSaCacheAddedCallbackData(ArrayList<Byte> pmkCacheData,
            PmkSaCacheData pmkSaCache) {
        assertNotNull(pmkSaCache);
        assertEquals(MacAddress.fromString(BSSID), MacAddress.fromBytes(pmkSaCache.bssid));
        assertEquals(PMK_CACHE_EXPIRATION_IN_SEC, pmkSaCache.expirationTimeInSec);
        assertTrue(Arrays.equals(NativeUtil.byteArrayFromArrayList(pmkCacheData),
                pmkSaCache.serializedEntry));
    }

    /**
     * Check that two unsorted QoS policy status lists contain the same entries.
     * @return true if lists contain the same entries, false otherwise.
     */
    private boolean qosPolicyStatusListsAreEqual(
            QosPolicyStatus[] expected, QosPolicyStatus[] actual) {
        if (expected.length != actual.length) {
            return false;
        }

        class PolicyStatusComparator implements Comparator<QosPolicyStatus> {
            public int compare(QosPolicyStatus a, QosPolicyStatus b) {
                if (a.policyId == b.policyId) {
                    return 0;
                }
                return a.policyId < b.policyId ? -1 : 1;
            }
        }

        List<QosPolicyStatus> expectedList = Arrays.asList(expected);
        List<QosPolicyStatus> actualList = Arrays.asList(actual);
        Collections.sort(expectedList, new PolicyStatusComparator());
        Collections.sort(actualList, new PolicyStatusComparator());

        for (int i = 0; i < expectedList.size(); i++) {
            if (expectedList.get(i).policyId != actualList.get(i).policyId
                    || expectedList.get(i).status != actualList.get(i).status) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test getConnectionMloLinkInfo returns WifiNative.ConnectionMloLinksInfo from interface name.
     */
    @Test
    public void testGetConnectionMloLinksInfo() throws Exception {
        final int mDownlinkTid = 3;
        final int mUplinkTid = 6;
        // initialize MLO Links
        MloLinksInfo info = new MloLinksInfo();
        MloLink[] links = new MloLink[3];
        // link 0
        links[0] = new android.hardware.wifi.supplicant.MloLink();
        links[0].linkId = 1;
        links[0].staLinkMacAddress = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x01};
        links[0].tidsDownlinkMap = Byte.MAX_VALUE;
        links[0].tidsDownlinkMap = Byte.MIN_VALUE;
        links[0].apLinkMacAddress = new byte[]{0x00, 0x0a, 0x0b, 0x0c, 0x0d, 0x01};
        links[0].frequencyMHz = 5160;
        // link 1
        links[1] = new android.hardware.wifi.supplicant.MloLink();
        links[1].linkId = 2;
        links[1].staLinkMacAddress = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x02};
        links[1].tidsDownlinkMap =  1 << mDownlinkTid;
        links[1].tidsUplinkMap = 1 << mUplinkTid;
        links[1].apLinkMacAddress = new byte[]{0x00, 0x0a, 0x0b, 0x0c, 0x0d, 0x02};
        links[1].frequencyMHz = 2437;
        // link 2
        links[2] = new android.hardware.wifi.supplicant.MloLink();
        links[2].linkId = 3;
        links[2].staLinkMacAddress = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x03};
        links[2].tidsDownlinkMap = 0;
        links[2].tidsUplinkMap = 0;
        links[2].apLinkMacAddress = new byte[]{0x00, 0x0a, 0x0b, 0x0c, 0x0d, 0x03};
        links[2].frequencyMHz = 6835;
        info.links = links;
        executeAndValidateInitializationSequence();
        // Mock MloLinksInfo as null.
        when(mISupplicantStaIfaceMock.getConnectionMloLinksInfo()).thenReturn(null);
        // Pass wrong interface.
        assertNull(mDut.getConnectionMloLinksInfo(WLAN1_IFACE_NAME));
        // Pass correct interface.
        assertNull(mDut.getConnectionMloLinksInfo(WLAN0_IFACE_NAME));
        // Mock MloLinksInfo.
        when(mISupplicantStaIfaceMock.getConnectionMloLinksInfo()).thenReturn(info);
        // Pass wrong interface with mock MloLinksInfo.
        assertNull(mDut.getConnectionMloLinksInfo(WLAN1_IFACE_NAME));
        // Pass correct interface with mock MloLinksInfo.
        WifiNative.ConnectionMloLinksInfo nativeInfo = mDut.getConnectionMloLinksInfo(
                WLAN0_IFACE_NAME);
        // Check all return values.
        assertNotNull(nativeInfo);
        assertEquals(nativeInfo.links.length, info.links.length);
        // link 0
        assertEquals(nativeInfo.links[0].getLinkId(), info.links[0].linkId);
        assertEquals(nativeInfo.links[0].getStaMacAddress(),
                MacAddress.fromBytes(info.links[0].staLinkMacAddress));
        assertTrue(nativeInfo.links[0].isAnyTidMapped());
        assertEquals(nativeInfo.links[0].getApMacAddress(),
                MacAddress.fromBytes(info.links[0].apLinkMacAddress));
        assertEquals(nativeInfo.links[0].getFrequencyMHz(), info.links[0].frequencyMHz);
        // link 1
        assertEquals(nativeInfo.links[1].getLinkId(), info.links[1].linkId);
        assertEquals(nativeInfo.links[1].getStaMacAddress(),
                MacAddress.fromBytes(info.links[1].staLinkMacAddress));
        assertTrue(nativeInfo.links[1].isAnyTidMapped());
        assertTrue(nativeInfo.links[1].isTidMappedtoDownlink((byte) mDownlinkTid));
        assertTrue(nativeInfo.links[1].isTidMappedToUplink((byte) mUplinkTid));
        assertEquals(nativeInfo.links[1].getApMacAddress(),
                MacAddress.fromBytes(info.links[1].apLinkMacAddress));
        assertEquals(nativeInfo.links[1].getFrequencyMHz(), info.links[1].frequencyMHz);
        // link 2
        assertEquals(nativeInfo.links[2].getLinkId(), info.links[2].linkId);
        assertEquals(nativeInfo.links[2].getStaMacAddress(),
                MacAddress.fromBytes(info.links[2].staLinkMacAddress));
        assertFalse(nativeInfo.links[2].isAnyTidMapped());
        assertEquals(nativeInfo.links[2].getApMacAddress(),
                MacAddress.fromBytes(info.links[2].apLinkMacAddress));
        assertEquals(nativeInfo.links[2].getFrequencyMHz(), info.links[2].frequencyMHz);
    }

    /*
     * Tests that the very first connection attempt failure due to Authentication timeout in PSK
     * network is notified as wrong password error.
     */
    @Test
    public void testPskNetworkAuthenticationTimeOutDueToWrongPasswordInFirstConnectAttempt()
            throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        executeAndValidateConnectSequenceWithKeyMgmt(
                SUPPLICANT_NETWORK_ID, false, TRANSLATED_SUPPLICANT_SSID.toString(),
                WifiConfiguration.SECURITY_TYPE_PSK, null, false);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onStateChanged(
                StaIfaceCallbackState.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(SUPPLICANT_SSID)), false);
        mISupplicantStaIfaceCallback.onAuthenticationTimeout(
                NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
    }

    /**
     * Tests the handling of BSS frequency changed event.
     */
    @Test
    public void testBssFrequencyChangedCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onBssFrequencyChanged(2412);
        verify(mWifiMonitor).broadcastBssFrequencyChanged(
                eq(WLAN0_IFACE_NAME), eq(2412));
    }

    /*
     * Test that the service version is cached on the first successful call to startDaemon.
     */
    @Test
    public void testServiceVersionCaching() throws RemoteException {
        mDut.initialize();
        int serviceVersion = ISupplicant.VERSION;
        assertFalse(mDut.isServiceVersionAtLeast(serviceVersion));

        // Service version should not be cached on a remote exception.
        doThrow(new RemoteException()).when(mISupplicantMock).getInterfaceVersion();
        assertFalse(mDut.startDaemon());
        verify(mISupplicantMock).getInterfaceVersion();
        assertFalse(mDut.isServiceVersionAtLeast(serviceVersion));

        // Service version should be cached if retrieved successfully.
        reset(mISupplicantMock);
        when(mISupplicantMock.getInterfaceVersion()).thenReturn(serviceVersion);
        assertTrue(mDut.startDaemon());
        verify(mISupplicantMock).getInterfaceVersion();
        verify(mWifiSettingsConfigStore).put(any(), anyInt());
        assertTrue(mDut.isServiceVersionAtLeast(serviceVersion));

        // Interface version should not be retrieved again after it has been cached.
        reset(mISupplicantMock);
        assertTrue(mDut.startDaemon());
        verify(mISupplicantMock, never()).getInterfaceVersion();
    }

    /**
     * Test {@link SupplicantStaIfaceHalAidlImpl#enableMscs(MscsParams, String)} and verify the
     * conversion from {@link MscsParams} to its HAL equivalent.
     */
    @Test
    public void testEnableMscs() throws Exception {
        int userPriorityBitmap = (1 << 6) | (1 << 7);
        int userPriorityLimit = 5;
        int streamTimeoutUs = 1500;
        int frameworkFrameClassifierMask =
                MscsParams.FRAME_CLASSIFIER_IP_VERSION | MscsParams.FRAME_CLASSIFIER_DSCP;
        byte halFrameClassifierMask =
                FrameClassifierFields.IP_VERSION | FrameClassifierFields.DSCP;

        ArgumentCaptor<android.hardware.wifi.supplicant.MscsParams> halParamsCaptor =
                ArgumentCaptor.forClass(android.hardware.wifi.supplicant.MscsParams.class);
        doNothing().when(mISupplicantStaIfaceMock).configureMscs(any());
        executeAndValidateInitializationSequence();

        MscsParams frameworkParams = new MscsParams.Builder()
                .setUserPriorityBitmap(userPriorityBitmap)
                .setUserPriorityLimit(userPriorityLimit)
                .setStreamTimeoutUs(streamTimeoutUs)
                .setFrameClassifierFields(frameworkFrameClassifierMask)
                .build();
        mDut.setupIface(WLAN0_IFACE_NAME);
        mDut.enableMscs(frameworkParams, WLAN0_IFACE_NAME);

        verify(mISupplicantStaIfaceMock).configureMscs(halParamsCaptor.capture());
        android.hardware.wifi.supplicant.MscsParams halParams = halParamsCaptor.getValue();
        assertEquals((byte) userPriorityBitmap, halParams.upBitmap);
        assertEquals((byte) userPriorityLimit, halParams.upLimit);
        assertEquals(streamTimeoutUs, halParams.streamTimeoutUs);
        assertEquals(halFrameClassifierMask, halParams.frameClassifierMask);
    }
}
