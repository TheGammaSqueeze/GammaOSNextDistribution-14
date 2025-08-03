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

import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA256;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA384;
import static android.net.wifi.WifiManager.WIFI_FEATURE_MBO;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OCE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OWE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WAPI;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SAE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SUITE_B;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import android.app.test.MockAnswerUtil;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.hardware.wifi.V1_0.WifiChannelWidthInMhz;
import android.hardware.wifi.supplicant.DebugLevel;
import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback.BssidChangeReason;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.IfaceType;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatusCode;
import android.hardware.wifi.supplicant.V1_0.WpsConfigMethods;
import android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback.BssTmData;
import android.hardware.wifi.supplicant.V1_3.WifiTechnology;
import android.hardware.wifi.supplicant.V1_4.ConnectionCapabilities;
import android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback.AssociationRejectionData;
import android.hardware.wifi.supplicant.V1_4.LegacyMode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.MboOceController.BtmFrameData;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.util.NativeUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Unit tests for SupplicantStaIfaceHalHidlImpl
 */
@SmallTest
public class SupplicantStaIfaceHalHidlImplTest extends WifiBaseTest {
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
    private static final byte[] CONNECTED_MAC_ADDRESS_BYTES =
            {0x00, 0x01, 0x02, 0x03, 0x04, 0x05};

    private @Mock IServiceManager mServiceManagerMock;
    private @Mock ISupplicant mISupplicantMock;
    private @Mock android.hardware.wifi.supplicant.V1_1.ISupplicant mISupplicantMockV11;
    private @Mock android.hardware.wifi.supplicant.V1_2.ISupplicant mISupplicantMockV12;
    private @Mock android.hardware.wifi.supplicant.V1_3.ISupplicant mISupplicantMockV13;
    private @Mock android.hardware.wifi.supplicant.V1_3.ISupplicant mISupplicantMockV14;
    private @Mock ISupplicantIface mISupplicantIfaceMock;
    private @Mock ISupplicantStaIface mISupplicantStaIfaceMock;
    private @Mock android.hardware.wifi.supplicant.V1_1.ISupplicantStaIface
            mISupplicantStaIfaceMockV11;
    private @Mock android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
            mISupplicantStaIfaceMockV12;
    private @Mock android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
            mISupplicantStaIfaceMockV13;
    private @Mock android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
            mISupplicantStaIfaceMockV14;
    private @Mock Context mContext;
    private @Mock WifiMonitor mWifiMonitor;
    private @Mock FrameworkFacade mFrameworkFacade;
    private @Mock SupplicantStaNetworkHalHidlImpl mSupplicantStaNetworkMock;
    private @Mock WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;
    private @Mock Clock mClock;
    private @Mock WifiMetrics mWifiMetrics;
    private @Mock WifiGlobals mWifiGlobals;
    private @Mock SsidTranslator mSsidTranslator;
    private @Mock PmkCacheManager mPmkCacheManager;

    SupplicantStatus mStatusSuccess;
    SupplicantStatus mStatusFailure;
    android.hardware.wifi.supplicant.V1_4.SupplicantStatus mStatusSuccessV14;
    android.hardware.wifi.supplicant.V1_4.SupplicantStatus mStatusFailureV14;
    ISupplicant.IfaceInfo mStaIface0;
    ISupplicant.IfaceInfo mStaIface1;
    ISupplicant.IfaceInfo mP2pIface;
    ArrayList<ISupplicant.IfaceInfo> mIfaceInfoList;
    ISupplicantStaIfaceCallback mISupplicantStaIfaceCallback;
    android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback
            mISupplicantStaIfaceCallbackV11;
    android.hardware.wifi.supplicant.V1_2.ISupplicantStaIfaceCallback
            mISupplicantStaIfaceCallbackV12;
    android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback
            mISupplicantStaIfaceCallbackV13 = null;
    android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback
            mISupplicantStaIfaceCallbackV14 = null;

    private TestLooper mLooper = new TestLooper();
    private Handler mHandler = null;
    private SupplicantStaIfaceHalSpy mDut;
    private ArgumentCaptor<IHwBinder.DeathRecipient> mServiceManagerDeathCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IHwBinder.DeathRecipient> mSupplicantDeathCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IHwBinder.DeathRecipient> mSupplicantStaIfaceDeathCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IServiceNotification.Stub> mServiceNotificationCaptor =
            ArgumentCaptor.forClass(IServiceNotification.Stub.class);
    private ArgumentCaptor<Long> mDeathRecipientCookieCaptor = ArgumentCaptor.forClass(Long.class);
    private InOrder mInOrder;

    private class SupplicantStaIfaceHalSpy extends SupplicantStaIfaceHalHidlImpl {
        SupplicantStaNetworkHalHidlImpl mStaNetwork;

        SupplicantStaIfaceHalSpy() {
            super(mContext, mWifiMonitor, mFrameworkFacade,
                    mHandler, mClock, mWifiMetrics, mWifiGlobals, mSsidTranslator);
            mStaNetwork = mSupplicantStaNetworkMock;
        }

        @Override
        protected IServiceManager getServiceManagerMockable() throws RemoteException {
            return mServiceManagerMock;
        }

        @Override
        protected ISupplicant getSupplicantMockable() throws RemoteException {
            return mISupplicantMock;
        }

        @Override
        protected android.hardware.wifi.supplicant.V1_1.ISupplicant getSupplicantMockableV1_1()
                throws RemoteException {
            return mISupplicantMockV11;
        }

        @Override
        protected ISupplicantStaIface getStaIfaceMockable(ISupplicantIface iface) {
            return mISupplicantStaIfaceMock;
        }

        @Override
        protected android.hardware.wifi.supplicant.V1_1.ISupplicantStaIface
                getStaIfaceMockableV1_1(ISupplicantIface iface) {
            return mISupplicantStaIfaceMockV11;
        }

        @Override
        protected android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                getStaIfaceMockableV1_2(ISupplicantIface iface) {
            return mISupplicantStaIfaceMockV12;
        }

        @Override
        protected android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                getStaIfaceMockableV1_3(ISupplicantIface iface) {
            return (mISupplicantMockV13 != null)
                    ? mISupplicantStaIfaceMockV13
                    : null;
        }

        @Override
        protected android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
                getStaIfaceMockableV1_4(ISupplicantIface iface) {
            return (mISupplicantMockV14 != null)
                    ? mISupplicantStaIfaceMockV14
                    : null;
        }

        @Override
        protected SupplicantStaNetworkHalHidlImpl getStaNetworkMockable(
                @NonNull String ifaceName,
                ISupplicantStaNetwork iSupplicantStaNetwork) {
            return mStaNetwork;
        }

        private void setStaNetworkMockable(SupplicantStaNetworkHalHidlImpl network) {
            mStaNetwork = network;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStatusSuccess = createSupplicantStatus(SupplicantStatusCode.SUCCESS);
        mStatusFailure = createSupplicantStatus(SupplicantStatusCode.FAILURE_UNKNOWN);
        mStatusSuccessV14 = createSupplicantStatusV1_4(
                android.hardware.wifi.supplicant.V1_4.SupplicantStatusCode.SUCCESS);
        mStatusFailureV14 = createSupplicantStatusV1_4(
                android.hardware.wifi.supplicant.V1_4.SupplicantStatusCode.FAILURE_UNKNOWN);
        mStaIface0 = createIfaceInfo(IfaceType.STA, WLAN0_IFACE_NAME);
        mStaIface1 = createIfaceInfo(IfaceType.STA, WLAN1_IFACE_NAME);
        mP2pIface = createIfaceInfo(IfaceType.P2P, P2P_IFACE_NAME);

        mIfaceInfoList = new ArrayList<>();
        mIfaceInfoList.add(mStaIface0);
        mIfaceInfoList.add(mStaIface1);
        mIfaceInfoList.add(mP2pIface);
        when(mServiceManagerMock.getTransport(anyString(), anyString()))
                .thenReturn(IServiceManager.Transport.EMPTY);
        when(mServiceManagerMock.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        when(mServiceManagerMock.registerForNotifications(anyString(), anyString(),
                any(IServiceNotification.Stub.class))).thenReturn(true);
        when(mISupplicantMock.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        when(mISupplicantMockV11.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(ISupplicantStaIface.getMacAddressCallback cb) {
                cb.onValues(mStatusSuccess, CONNECTED_MAC_ADDRESS_BYTES);
            }
        })
        .when(mISupplicantStaIfaceMock)
                .getMacAddress(any(ISupplicantStaIface.getMacAddressCallback.class));
        when(mFrameworkFacade.startSupplicant()).thenReturn(true);
        mHandler = spy(new Handler(mLooper.getLooper()));
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
        mDut = new SupplicantStaIfaceHalSpy();
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHalHidlImpl initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence(false, false, false, false);
    }

    /**
     * Tests the initialization flow, with a RemoteException occurring when 'getInterface' is called
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_remoteExceptionFailure() throws Exception {
        executeAndValidateInitializationSequence(true, false, false, false);
    }

    /**
     * Tests the initialization flow, with listInterfaces returning 0 interfaces.
     * Ensures failure
     */
    @Test
    public void testInitialize_zeroInterfacesFailure() throws Exception {
        executeAndValidateInitializationSequence(false, true, false, false);
    }

    /**
     * Tests the initialization flow, with a null interface being returned by getInterface.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_nullInterfaceFailure() throws Exception {
        executeAndValidateInitializationSequence(false, false, true, false);
    }

    /**
     * Tests the initialization flow, with a callback registration failure.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_callbackRegistrationFailure() throws Exception {
        executeAndValidateInitializationSequence(false, false, false, true);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_successV1_1() throws Exception {
        setupMocksForHalV1_1();
        executeAndValidateInitializationSequenceV1_1(false, false);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_successV1_2() throws Exception {
        setupMocksForHalV1_2();
        executeAndValidateInitializationSequenceV1_2();
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_successV1_3() throws Exception {
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_successV1_4() throws Exception {
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
    }

    /**
     * Tests the initialization flow, with a RemoteException occurring when 'getInterface' is called
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_remoteExceptionFailureV1_1() throws Exception {
        setupMocksForHalV1_1();
        executeAndValidateInitializationSequenceV1_1(true, false);
    }

    /**
     * Tests the initialization flow, with a null interface being returned by getInterface.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_nullInterfaceFailureV1_1() throws Exception {
        setupMocksForHalV1_1();
        executeAndValidateInitializationSequenceV1_1(false, true);
    }

    /**
     * Ensures that we do not allow operations on an interface until it's setup.
     */
    @Test
    public void testEnsureOperationFailsUntilSetupInterfaces() throws Exception {
        executeAndValidateInitializationSequence(false, false, false, false);

        // Ensure that the cancel wps operation is failed because wlan1 interface is not yet setup.
        assertFalse(mDut.cancelWps(WLAN1_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).cancelWps();

        // Now setup the wlan1 interface and Ensure that the cancel wps operation is successful.
        assertTrue(mDut.setupIface(WLAN1_IFACE_NAME));
        when(mISupplicantStaIfaceMock.cancelWps()).thenReturn(mStatusSuccess);
        assertTrue(mDut.cancelWps(WLAN1_IFACE_NAME));
        verify(mISupplicantStaIfaceMock).cancelWps();
    }

    /**
     * Ensures that reject addition of an existing iface.
     */
    @Test
    public void testDuplicateSetupIfaceV1_1_Fails() throws Exception {
        setupMocksForHalV1_1();
        executeAndValidateInitializationSequenceV1_1(false, false);

        // Trying setting up the wlan0 interface again & ensure it fails.
        assertFalse(mDut.setupIface(WLAN0_IFACE_NAME));
        verify(mISupplicantMockV11)
                .setDebugParams(eq(DebugLevel.INFO), eq(false), eq(false));
        verifyNoMoreInteractions(mISupplicantMockV11);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal interface teardown.
     */
    @Test
    public void testTeardownInterface() throws Exception {
        testInitialize_success();
        assertTrue(mDut.teardownIface(WLAN0_IFACE_NAME));

        // Ensure that the cancel wps operation is failed because there are no interfaces setup.
        assertFalse(mDut.cancelWps(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).cancelWps();
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal interface teardown.
     */
    @Test
    public void testTeardownInterfaceV1_1() throws Exception {
        testInitialize_successV1_1();

        when(mISupplicantMockV11.removeInterface(any())).thenReturn(mStatusSuccess);
        assertTrue(mDut.teardownIface(WLAN0_IFACE_NAME));
        verify(mISupplicantMockV11).removeInterface(any());

        // Ensure that the cancel wps operation is failed because there are no interfaces setup.
        assertFalse(mDut.cancelWps(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMock, never()).cancelWps();
    }

    /**
     * Ensures that we reject removal of an invalid iface.
     */
    @Test
    public void testInvalidTeardownInterfaceV1_1_Fails() throws Exception {
        assertFalse(mDut.teardownIface(WLAN0_IFACE_NAME));
        verifyNoMoreInteractions(mISupplicantMock);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal initialization
     * Asserts successful initialization of second interface
     */
    @Test
    public void testSetupTwoInterfaces() throws Exception {
        executeAndValidateInitializationSequence(false, false, false, false);
        assertTrue(mDut.setupIface(WLAN1_IFACE_NAME));
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal interface teardown.
     * Asserts successful initialization of second interface
     */
    @Test
    public void testTeardownTwoInterfaces() throws Exception {
        testSetupTwoInterfaces();
        assertTrue(mDut.teardownIface(WLAN0_IFACE_NAME));
        assertTrue(mDut.teardownIface(WLAN1_IFACE_NAME));

        // Ensure that the cancel wps operation is failed because there are no interfaces setup.
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

    @Test
    public void testConnectToNetworkWithDifferentConfigReplacesNetworkInSupplicant()
            throws Exception {
        executeAndValidateInitializationSequence();
        WifiConfiguration config = executeAndValidateConnectSequence(
                SUPPLICANT_NETWORK_ID, false, TRANSLATED_SUPPLICANT_SSID.toString());
        // Reset mocks for mISupplicantStaIfaceMock because we finished the first connection.
        reset(mISupplicantStaIfaceMock);
        setupMocksForConnectSequence(true /*haveExistingNetwork*/);
        // Make this network different by changing SSID.
        config.SSID = "\"ADifferentSSID\"";
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        verify(mISupplicantStaIfaceMock).removeNetwork(SUPPLICANT_NETWORK_ID);
        verify(mISupplicantStaIfaceMock)
                .addNetwork(any(ISupplicantStaIface.addNetworkCallback.class));
    }

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
        verify(mISupplicantStaIfaceMock, never())
                .addNetwork(any(ISupplicantStaIface.addNetworkCallback.class));
    }

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
        verify(mISupplicantStaIfaceMock, never())
                .addNetwork(any(ISupplicantStaIface.addNetworkCallback.class));
    }

    /**
     * Tests connection to a specified network failure due to network add.
     */
    @Test
    public void testConnectFailureDueToNetworkAddFailure() throws Exception {
        executeAndValidateInitializationSequence();
        setupMocksForConnectSequence(false);
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.addNetworkCallback cb) throws RemoteException {
                cb.onValues(mStatusFailure, mock(ISupplicantStaNetwork.class));
                return;
            }
        }).when(mISupplicantStaIfaceMock).addNetwork(
                any(ISupplicantStaIface.addNetworkCallback.class));

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

        doThrow(new IllegalArgumentException("Some error!!!"))
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
        doAnswer(new AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusFailure, new ArrayList<>());
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        assertFalse(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, null));

        // Supplicant returned a null network list, return false
        doAnswer(new AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, null);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        assertFalse(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, null));

        // Successfully link a network to the current network
        final ArrayList<Integer> supplicantNetIds = new ArrayList<>();
        supplicantNetIds.add(supplicantNetId);
        doAnswer(new AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, supplicantNetIds);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        WifiConfiguration linkedConfig = new WifiConfiguration();
        linkedConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        Map<String, WifiConfiguration> linkedNetworks = new HashMap<>();
        linkedNetworks.put(linkedConfig.getProfileKey(), linkedConfig);
        SupplicantStaNetworkHalHidlImpl linkedNetworkHandle =
                mock(SupplicantStaNetworkHalHidlImpl.class);
        when(linkedNetworkHandle.getNetworkId()).thenReturn(supplicantNetId + 1);
        when(linkedNetworkHandle.saveWifiConfiguration(linkedConfig)).thenReturn(true);
        when(linkedNetworkHandle.select()).thenReturn(true);
        mDut.setStaNetworkMockable(linkedNetworkHandle);
        assertTrue(mDut.updateLinkedNetworks(
                WLAN0_IFACE_NAME, frameworkNetId, linkedNetworks));

        // Successfully remove linked network but not the current network from supplicant
        supplicantNetIds.add(supplicantNetId + 1);
        doAnswer(new AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, supplicantNetIds);
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        doAnswer(new AnswerWithArguments() {
            public SupplicantStatus answer(int id) {
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMock).removeNetwork(supplicantNetId + 1);
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
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) {
                cb.onValues(mStatusSuccess, new ArrayList<>(NETWORK_ID_TO_SSID.keySet()));
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(int id) {
                assertTrue(NETWORK_ID_TO_SSID.containsKey(id));
                return mStatusSuccess;
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

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer() throws RemoteException {
                return mStatusFailure;
            }
        }).when(mISupplicantStaIfaceMock).reassociate();
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
        when(mISupplicantStaIfaceMock.setWpsDeviceType(any(byte[].class)))
                .thenReturn(mStatusSuccess);

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setWpsDeviceType(WLAN0_IFACE_NAME, validDeviceTypeStr));
        verify(mISupplicantStaIfaceMock).setWpsDeviceType(eq(expectedDeviceType));

        // This should not work
        assertFalse(mDut.setWpsDeviceType(WLAN0_IFACE_NAME, invalidDeviceType1Str));
        // This should not work
        assertFalse(mDut.setWpsDeviceType(WLAN0_IFACE_NAME, invalidDeviceType2Str));
    }

    /**
     * Tests the setting of WPS config methods.
     */
    @Test
    public void testSetWpsConfigMethods() throws Exception {
        String validConfigMethodsStr = "physical_display virtual_push_button";
        Short expectedConfigMethods =
                WpsConfigMethods.PHY_DISPLAY | WpsConfigMethods.VIRT_PUSHBUTTON;
        String invalidConfigMethodsStr = "physical_display virtual_push_button test";
        when(mISupplicantStaIfaceMock.setWpsConfigMethods(anyShort())).thenReturn(mStatusSuccess);

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
        mISupplicantStaIfaceCallback.onAnqpQueryDone(
                bssid, new ISupplicantStaIfaceCallback.AnqpData(),
                new ISupplicantStaIfaceCallback.Hs20AnqpData());

        ArgumentCaptor<AnqpEvent> anqpEventCaptor = ArgumentCaptor.forClass(AnqpEvent.class);
        verify(mWifiMonitor).broadcastAnqpDoneEvent(
                eq(WLAN0_IFACE_NAME), anqpEventCaptor.capture());
        assertEquals(
                ByteBufferReader.readInteger(
                        ByteBuffer.wrap(bssid), ByteOrder.BIG_ENDIAN, bssid.length),
                anqpEventCaptor.getValue().getBssid());
    }

    /**
     * Tests the handling of ANQP done callback.
     * Note: Since the ANQP element parsing methods are static, this can only test the negative test
     * where all the parsing fails because the data is empty. It'll be non-trivial and unnecessary
     * to test out the parsing logic here.
     */
    @Test
    public void testAnqpDoneCallback_1_4() throws Exception {
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
        assertNotNull(mISupplicantStaIfaceCallbackV14);
        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        mISupplicantStaIfaceCallbackV14.onAnqpQueryDone_1_4(
                bssid,
                new android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback.AnqpData(),
                new ISupplicantStaIfaceCallback.Hs20AnqpData());

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
        mISupplicantStaIfaceCallback.onHs20IconQueryDone(
                bssid, ICON_FILE_NAME, NativeUtil.byteArrayToArrayList(iconData));

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
        byte osuMethod = ISupplicantStaIfaceCallback.OsuMethod.OMA_DM;
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
                ISupplicantStaIfaceCallback.State.INACTIVE,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));

        // Can't compare WifiSsid instances because they lack an equals.
        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiConfiguration.INVALID_NETWORK_ID),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(0), eq(SupplicantState.INACTIVE));
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
                ISupplicantStaIfaceCallback.State.ASSOCIATED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));

        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(0), eq(SupplicantState.ASSOCIATED));
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
                ISupplicantStaIfaceCallback.State.COMPLETED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));

        wifiMonitorInOrder.verify(mWifiMonitor).broadcastNetworkConnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId), eq(false),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID));
        wifiMonitorInOrder.verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(0), eq(SupplicantState.COMPLETED));
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATED,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        // Ensure we don't lose our prev state with this state changed event.
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.DISCONNECTED,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);

        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
    }

    /**
     * Tests the handling of EAP failure disconnects.
     */
    @Test
    public void testOnlyOneAuthFailureEap() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        executeAndValidateConnectSequenceWithKeyMgmt(0, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_EAP, null);

        int reasonCode = 3;
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATED,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onEapFailure();
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.BROADCAST_ADDRESS));

        // Ensure that the disconnect is ignored.
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);
        verify(mWifiMonitor, times(1)).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.BROADCAST_ADDRESS));
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

        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        int statusCode = ISupplicantStaIfaceCallback.StatusCode.UNSPECIFIED_FAILURE;
        mISupplicantStaIfaceCallback.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID), statusCode, false);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(statusCode, assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Tests the handling of incorrect network passwords for WPA3-Personal networks using
     * callback V1_4.
     */
    @Test
    public void testWpa3AuthRejectionPassword_1_4() throws Exception {
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
        assertNotNull(mISupplicantStaIfaceCallbackV14);

        executeAndValidateConnectSequenceWithKeyMgmt(SUPPLICANT_NETWORK_ID, false,
                TRANSLATED_SUPPLICANT_SSID.toString(), WifiConfiguration.SECURITY_TYPE_SAE, null);

        int statusCode = ISupplicantStaIfaceCallback.StatusCode.UNSPECIFIED_FAILURE;
        AssociationRejectionData rejectionData = new AssociationRejectionData();
        rejectionData.ssid = NativeUtil.decodeSsid(SUPPLICANT_SSID);
        rejectionData.bssid = NativeUtil.macAddressToByteArray(BSSID);
        rejectionData.statusCode = statusCode;
        rejectionData.timedOut = false;
        rejectionData.isMboAssocDisallowedReasonCodePresent = false;
        rejectionData.isOceRssiBasedAssocRejectAttrPresent = false;

        mISupplicantStaIfaceCallbackV14.onAssociationRejected_1_4(rejectionData);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo = assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(statusCode, assocRejectEventInfo.statusCode);
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        int statusCode = ISupplicantStaIfaceCallback.StatusCode.UNSPECIFIED_FAILURE;
        mISupplicantStaIfaceCallback.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID), statusCode, false);
        verify(mWifiMonitor, never()).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                anyInt(), anyInt(), any(), any());
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(statusCode, assocRejectEventInfo.statusCode);
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID),
                ISupplicantStaIfaceCallback.StatusCode.UNSPECIFIED_FAILURE, true);
        verify(mWifiMonitor, never()).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                anyInt(), anyInt(), any(), any());
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        int statusCode = ISupplicantStaIfaceCallback.StatusCode.CHALLENGE_FAIL;
        mISupplicantStaIfaceCallback.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID), statusCode, false);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(statusCode, assocRejectEventInfo.statusCode);
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        int statusCode = ISupplicantStaIfaceCallback.StatusCode.CHALLENGE_FAIL;
        mISupplicantStaIfaceCallback.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID), statusCode, false);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(statusCode, assocRejectEventInfo.statusCode);
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
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
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

        int reasonCode = ISupplicantStaIfaceCallback.ReasonCode.IE_IN_4WAY_DIFFERS;

        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
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

        int reasonCode = ISupplicantStaIfaceCallback.ReasonCode.DISASSOC_AP_BUSY;

        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
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

        int reasonCode = ISupplicantStaIfaceCallback.ReasonCode.IEEE_802_1X_AUTH_FAILED;
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), false, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of association rejection notification.
     */
    @Test
    public void testAssociationRejectionCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        int statusCode = 7;
        mISupplicantStaIfaceCallback.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID), statusCode, false);
        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(statusCode, assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Tests the handling of authentication timeout notification.
     */
    @Test
    public void testAuthenticationTimeoutCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);

        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onAuthenticationTimeout(
                NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(eq(WLAN0_IFACE_NAME),
                eq(WifiManager.ERROR_AUTH_FAILURE_TIMEOUT), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
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
        testInitialize_successV1_1();
        assertNotNull(mISupplicantStaIfaceCallbackV11);

        mISupplicantStaIfaceCallbackV11.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallbackV11.onEapFailure_1_1(eapFailureCode);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE),
                eq(eapFailureCode), eq(TRANSLATED_SUPPLICANT_SSID.toString()),
                eq(MacAddress.BROADCAST_ADDRESS));
    }

    /**
     * Tests the handling of EAP failure notification.
     */
    @Test
    public void testEapFailureCallback1_3() throws Exception {
        int eapFailureCode = WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED;
        testInitialize_successV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        mISupplicantStaIfaceCallbackV13.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallbackV13.onEapFailure_1_3(eapFailureCode);
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE),
                eq(eapFailureCode), eq(TRANSLATED_SUPPLICANT_SSID.toString()),
                eq(MacAddress.BROADCAST_ADDRESS));
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

        short cfgError = ISupplicantStaIfaceCallback.WpsConfigError.MULTIPLE_PBC_DETECTED;
        short errorInd = ISupplicantStaIfaceCallback.WpsErrorIndication.SECURITY_WEP_PROHIBITED;
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

        short cfgError = ISupplicantStaIfaceCallback.WpsConfigError.MSG_TIMEOUT;
        short errorInd = ISupplicantStaIfaceCallback.WpsErrorIndication.NO_ERROR;
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
     * Tests the handling of service manager death notification.
     */
    @Test
    public void testServiceManagerDeathCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mServiceManagerDeathCaptor.getValue());
        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));

        mServiceManagerDeathCaptor.getValue().serviceDied(5L);
        mLooper.dispatchAll();

        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler).onDeath();
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

        mSupplicantDeathCaptor.getValue().serviceDied(mDeathRecipientCookieCaptor.getValue());
        mLooper.dispatchAll();

        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler).onDeath();
    }

    /**
     * Tests the handling of supplicant death notification.
     */
    @Test
    public void testSupplicantStaleDeathCallback() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mSupplicantDeathCaptor.getValue());
        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));

        mSupplicantDeathCaptor.getValue().serviceDied(mDeathRecipientCookieCaptor.getValue() - 1);
        mLooper.dispatchAll();

        assertTrue(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler, never()).onDeath();
    }

    /**
     * When wpa_supplicant is dead, we could end up getting a remote exception on a hwbinder call
     * and then the death notification.
     */
    @Test
    public void testHandleRemoteExceptionAndDeathNotification() throws Exception {
        executeAndValidateInitializationSequence();
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));
        assertTrue(mDut.isInitializationComplete());

        // Throw remote exception on hwbinder call.
        when(mISupplicantStaIfaceMock.setPowerSave(anyBoolean()))
                .thenThrow(new RemoteException());
        assertFalse(mDut.setPowerSave(WLAN0_IFACE_NAME, true));
        verify(mISupplicantStaIfaceMock).setPowerSave(true);

        // Check that remote exception cleared all internal state.
        assertFalse(mDut.isInitializationComplete());

        // Ensure that further calls fail because the remote exception clears any state.
        assertFalse(mDut.setPowerSave(WLAN0_IFACE_NAME, true));
        //.. No call to ISupplicantStaIface object

        // Now trigger a death notification and ensure it's handled.
        assertNotNull(mSupplicantDeathCaptor.getValue());
        mSupplicantDeathCaptor.getValue().serviceDied(mDeathRecipientCookieCaptor.getValue());
        mLooper.dispatchAll();

        // External death notification fires only once!
        verify(mSupplicantHalDeathHandler).onDeath();
    }

    /**
     * Tests the setting of log level.
     */
    @Test
    public void testSetLogLevel() throws Exception {
        when(mISupplicantMock.setDebugParams(anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(mStatusSuccess);

        // Fail before initialization is performed.
        assertFalse(mDut.setLogLevel(true));

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setLogLevel(true));
        verify(mISupplicantMock)
                .setDebugParams(eq(ISupplicant.DebugLevel.DEBUG), eq(false), eq(false));
    }

    /**
     * Tests the setting of log level with show key enabled.
     */
    @Test
    public void testSetLogLevelWithShowKeyEnabled() throws Exception {
        when(mWifiGlobals.getShowKeyVerboseLoggingModeEnabled())
                .thenReturn(true);
        when(mISupplicantMock.setDebugParams(anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(mStatusSuccess);

        executeAndValidateInitializationSequence();

        // This should work.
        assertTrue(mDut.setLogLevel(true));
        verify(mISupplicantMock)
                .setDebugParams(eq(ISupplicant.DebugLevel.DEBUG), eq(false), eq(true));
    }

    /**
     * Tests that show key is not enabled when verbose logging is not enabled.
     */
    @Test
    public void testVerboseLoggingDisabledWithShowKeyEnabled() throws Exception {
        when(mWifiGlobals.getShowKeyVerboseLoggingModeEnabled())
                .thenReturn(true);
        when(mISupplicantMock.setDebugParams(anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(mStatusSuccess);

        executeAndValidateInitializationSequence();

        // If verbose logging is not enabled, show key should not be enabled.
        assertTrue(mDut.setLogLevel(false));
        verify(mISupplicantMock, times(2))
                .setDebugParams(eq(ISupplicant.DebugLevel.INFO), eq(false), eq(false));
    }

    /**
     * Tests the setting of concurrency priority.
     */
    @Test
    public void testConcurrencyPriority() throws Exception {
        when(mISupplicantMock.setConcurrencyPriority(anyInt())).thenReturn(mStatusSuccess);

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
        when(mISupplicantStaIfaceMock.startWpsRegistrar(any(byte[].class), anyString()))
                .thenReturn(mStatusSuccess);

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
        when(mISupplicantStaIfaceMock.startWpsPbc(any(byte[].class))).thenReturn(mStatusSuccess);
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
     * Tests country code setter
     */
    @Test
    public void testSetCountryCode() throws Exception {
        when(mISupplicantStaIfaceMock.setCountryCode(any(byte[].class))).thenReturn(mStatusSuccess);
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
     * Tests the start daemon for V1_0 service.
     */
    @Test
    public void testStartDaemonV1_0() throws Exception {
        executeAndValidateInitializationSequence();
        verify(mFrameworkFacade).startSupplicant();
    }

    /**
     * Tests the start daemon for V1_1 service.
     */
    @Test
    public void testStartDaemonV1_1() throws Exception {
        setupMocksForHalV1_1();

        executeAndValidateInitializationSequenceV1_1(false, false);
        assertTrue(mDut.startDaemon());
        verify(mFrameworkFacade, never()).startSupplicant();
    }

    /**
     * Tests the terminate for V1_0 service.
     */
    @Test
    public void testTerminateV1_0() throws Exception {
        executeAndValidateInitializationSequence();

        mDut.terminate();
        mSupplicantDeathCaptor.getValue().serviceDied(mDeathRecipientCookieCaptor.getValue());
        mLooper.dispatchAll();
        verify(mFrameworkFacade).stopSupplicant();

        // Check that terminate cleared all internal state.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Tests the start daemon for V1_1 service.
     */
    @Test
    public void testTerminateV1_1() throws Exception {
        setupMocksForHalV1_1();

        executeAndValidateInitializationSequenceV1_1(false, false);
        mDut.terminate();
        mSupplicantDeathCaptor.getValue().serviceDied(mDeathRecipientCookieCaptor.getValue());
        mLooper.dispatchAll();
        verify(mFrameworkFacade, never()).stopSupplicant();
        verify(mISupplicantMockV11).terminate();

        // Check that terminate cleared all internal state.
        assertFalse(mDut.isInitializationComplete());
    }

    private class GetKeyMgmtCapabilitiesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private int mKeyMgmtCapabilities;

        GetKeyMgmtCapabilitiesAnswer(int keyMgmtCapabilities) {
            mKeyMgmtCapabilities = keyMgmtCapabilities;
        }

        public void answer(android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                .getKeyMgmtCapabilitiesCallback cb) {
            cb.onValues(mStatusSuccess, mKeyMgmtCapabilities);
        }
    }

    private class GetKeyMgmtCapabilities_1_3Answer extends MockAnswerUtil.AnswerWithArguments {
        private int mKeyMgmtCapabilities;

        GetKeyMgmtCapabilities_1_3Answer(int keyMgmtCapabilities) {
            mKeyMgmtCapabilities = keyMgmtCapabilities;
        }

        public void answer(android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                .getKeyMgmtCapabilities_1_3Callback cb) {
            cb.onValues(mStatusSuccess, mKeyMgmtCapabilities);
        }
    }

    /**
     * Test get advanced capabilities API on old HAL, should return 0 (not supported)
     */
    @Test
    public void testGetKeyMgmtCapabilitiesOldHal() throws Exception {
        setupMocksForHalV1_1();

        executeAndValidateInitializationSequenceV1_1(false, false);

        assertTrue(mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME) == 0);
    }

    /**
     * Test WPA3-Personal SAE key may management support
     */
    @Test
    public void testGetKeyMgmtCapabilitiesWpa3Sae() throws Exception {
        setupMocksForHalV1_2();

        executeAndValidateInitializationSequenceV1_2();

        doAnswer(new GetKeyMgmtCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.SAE))
                .when(mISupplicantStaIfaceMockV12).getKeyMgmtCapabilities(any(
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                        .getKeyMgmtCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_WPA3_SAE, mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test WPA3-Enterprise Suite-B-192 key may management support
     */
    @Test
    public void testGetKeyMgmtCapabilitiesWpa3SuiteB() throws Exception {
        setupMocksForHalV1_2();

        executeAndValidateInitializationSequenceV1_2();

        doAnswer(new GetKeyMgmtCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.SUITE_B_192))
                .when(mISupplicantStaIfaceMockV12).getKeyMgmtCapabilities(any(
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                        .getKeyMgmtCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_WPA3_SUITE_B,
                mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test Enhanced Open (OWE) key may management support
     */
    @Test
    public void testGetKeyMgmtCapabilitiesOwe() throws Exception {
        setupMocksForHalV1_2();

        executeAndValidateInitializationSequenceV1_2();

        doAnswer(new GetKeyMgmtCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.OWE))
                .when(mISupplicantStaIfaceMockV12).getKeyMgmtCapabilities(any(
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                        .getKeyMgmtCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_OWE, mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test Enhanced Open (OWE) and SAE key may management support
     */
    @Test
    public void testGetKeyMgmtCapabilitiesOweAndSae() throws Exception {
        setupMocksForHalV1_2();

        executeAndValidateInitializationSequenceV1_2();

        doAnswer(new GetKeyMgmtCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.OWE
                | android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork.KeyMgmtMask.SAE))
                .when(mISupplicantStaIfaceMockV12).getKeyMgmtCapabilities(any(
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                        .getKeyMgmtCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_OWE | WIFI_FEATURE_WPA3_SAE,
                mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test Easy Connect (DPP) key may management support
     */
    @Test
    public void testGetKeyMgmtCapabilitiesDpp() throws Exception {
        setupMocksForHalV1_2();

        executeAndValidateInitializationSequenceV1_2();

        doAnswer(new GetKeyMgmtCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.DPP))
                .when(mISupplicantStaIfaceMockV12).getKeyMgmtCapabilities(any(
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaIface
                        .getKeyMgmtCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_DPP, mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test Easy Connect (DPP) Enrollee Responder mode supported on supplicant HAL V1_4
     */
    @Test
    public void testGetDppEnrolleeResponderModeSupport() throws Exception {
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();

        doAnswer(new GetKeyMgmtCapabilities_1_3Answer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.DPP))
                .when(mISupplicantStaIfaceMockV13).getKeyMgmtCapabilities_1_3(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getKeyMgmtCapabilities_1_3Callback.class));

        assertTrue((WIFI_FEATURE_DPP_ENROLLEE_RESPONDER
                & mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME))
                == WIFI_FEATURE_DPP_ENROLLEE_RESPONDER);
    }

    /**
     * Test Easy Connect (DPP) Enrollee Responder mode is not supported on supplicant HAL
     * V1_3 or less.
     */
    @Test
    public void testDppEnrolleeResponderModeNotSupportedOnHalV1_3OrLess() throws Exception {
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();

        doAnswer(new GetKeyMgmtCapabilities_1_3Answer(android.hardware.wifi.supplicant.V1_2
                .ISupplicantStaNetwork.KeyMgmtMask.DPP))
                .when(mISupplicantStaIfaceMockV13).getKeyMgmtCapabilities_1_3(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getKeyMgmtCapabilities_1_3Callback.class));

        assertFalse((WIFI_FEATURE_DPP_ENROLLEE_RESPONDER
                & mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME))
                == WIFI_FEATURE_DPP_ENROLLEE_RESPONDER);
    }

    /**
     * Test WAPI key may management support
     */
    @Test
    public void testGetKeyMgmtCapabilitiesWapi() throws Exception {
        setupMocksForHalV1_3();

        executeAndValidateInitializationSequenceV1_3();

        doAnswer(new GetKeyMgmtCapabilities_1_3Answer(android.hardware.wifi.supplicant.V1_3
                .ISupplicantStaNetwork.KeyMgmtMask.WAPI_PSK))
                .when(mISupplicantStaIfaceMockV13).getKeyMgmtCapabilities_1_3(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getKeyMgmtCapabilities_1_3Callback.class));

        assertEquals(WIFI_FEATURE_WAPI, mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test FILS SHA256 key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesFilsSha256() throws Exception {
        setupMocksForHalV1_3();

        executeAndValidateInitializationSequenceV1_3();

        doAnswer(new GetKeyMgmtCapabilities_1_3Answer(android.hardware.wifi.supplicant.V1_3
                .ISupplicantStaNetwork.KeyMgmtMask.FILS_SHA256))
                .when(mISupplicantStaIfaceMockV13).getKeyMgmtCapabilities_1_3(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getKeyMgmtCapabilities_1_3Callback.class));

        assertEquals(WIFI_FEATURE_FILS_SHA256,
                mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test FILS SHA384 key management support.
     */
    @Test
    public void testGetKeyMgmtCapabilitiesFilsSha384() throws Exception {
        setupMocksForHalV1_3();

        executeAndValidateInitializationSequenceV1_3();

        doAnswer(new GetKeyMgmtCapabilities_1_3Answer(android.hardware.wifi.supplicant.V1_3
                .ISupplicantStaNetwork.KeyMgmtMask.FILS_SHA384))
                .when(mISupplicantStaIfaceMockV13).getKeyMgmtCapabilities_1_3(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getKeyMgmtCapabilities_1_3Callback.class));

        assertEquals(WIFI_FEATURE_FILS_SHA384,
                mDut.getAdvancedCapabilities(WLAN0_IFACE_NAME));
    }

    /**
     * Test Easy Connect (DPP) calls return failure if hal version is less than 1_2
     */
    @Test
    public void testDppFailsWithOldHal() throws Exception {
        assertEquals(-1, mDut.addDppPeerUri(WLAN0_IFACE_NAME, "/blah"));
        assertFalse(mDut.removeDppUri(WLAN0_IFACE_NAME, 0));
        assertFalse(mDut.stopDppInitiator(WLAN0_IFACE_NAME));
        assertFalse(mDut.startDppConfiguratorInitiator(WLAN0_IFACE_NAME,
                1, 2, "Buckle", "My", "Shoe",
                3, 4, null));
        assertFalse(mDut.startDppEnrolleeInitiator(WLAN0_IFACE_NAME, 3, 14));
        WifiNative.DppBootstrapQrCodeInfo bootstrapInfo =
                mDut.generateDppBootstrapInfoForResponder(WLAN0_IFACE_NAME, "00:11:22:33:44:55",
                        "PRODUCT_INFO", SupplicantStaIfaceHal.DppCurve.PRIME256V1);
        assertEquals(-1, bootstrapInfo.bootstrapId);
        assertFalse(mDut.startDppEnrolleeResponder(WLAN0_IFACE_NAME, 6));
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

        setupMocksForHalV1_3();
        setupMocksForPmkCache(pmkCacheData, true);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager).get(eq(testFrameworkNetworkId));
        verify(mSupplicantStaNetworkMock).setPmkCache(eq(pmkCacheData));
        verify(mISupplicantStaIfaceCallbackV13)
                .onPmkCacheAdded(eq(PMK_CACHE_EXPIRATION_IN_SEC), eq(pmkCacheData));
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

        setupMocksForHalV1_3();
        setupMocksForPmkCache(pmkCacheData, true);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager).get(eq(testFrameworkNetworkId));
        verify(mSupplicantStaNetworkMock).setPmkCache(eq(pmkCacheData));
        verify(mISupplicantStaIfaceCallbackV13)
                .onPmkCacheAdded(eq(PMK_CACHE_EXPIRATION_IN_SEC), eq(pmkCacheData));
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

        setupMocksForHalV1_3();
        setupMocksForPmkCache(true);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any());
        verify(mISupplicantStaIfaceCallbackV13, never())
                .onPmkCacheAdded(anyLong(), any());
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

        setupMocksForHalV1_3();
        setupMocksForPmkCache(true);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any());
        verify(mISupplicantStaIfaceCallbackV13, never())
                .onPmkCacheAdded(anyLong(), any());
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

        setupMocksForHalV1_3();
        setupMocksForPmkCache(null, true);
        setupMocksForConnectSequence(false);
        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any(ArrayList.class));
        verify(mISupplicantStaIfaceCallbackV13, never()).onPmkCacheAdded(
                anyLong(), any(ArrayList.class));
    }

    /**
     * Test adding PMK cache entry returns faliure if this is a psk network.
     */
    @Test
    public void testAddPmkEntryIsOmittedWithPskNetwork() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);

        setupMocksForHalV1_3();
        setupMocksForPmkCache(true);
        setupMocksForConnectSequence(false);
        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mPmkCacheManager, never()).add(any(), anyInt(), any(), anyLong(), any());
        verify(mSupplicantStaNetworkMock, never()).setPmkCache(any(ArrayList.class));
        verify(mISupplicantStaIfaceCallbackV13, never()).onPmkCacheAdded(
                anyLong(), any(ArrayList.class));
    }

    /**
     * Test adding PMK cache entry returns faliure if HAL version is less than 1_3
     */
    @Test
    public void testAddPmkEntryIsOmittedWithOldHal() throws Exception {
        int testFrameworkNetworkId = 9;
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = testFrameworkNetworkId;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.getNetworkSelectionStatus().setCandidateSecurityParams(
                SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_EAP));
        ArrayList<Byte> pmkCacheData = NativeUtil.byteArrayToArrayList("deadbeef".getBytes());
        setupMocksForPmkCache(pmkCacheData, false);

        setupMocksForConnectSequence(false);
        executeAndValidateInitializationSequence();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));

        verify(mSupplicantStaNetworkMock).setPmkCache(eq(pmkCacheData));
        assertNull(mISupplicantStaIfaceCallbackV13);
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

        setupMocksForHalV1_3();
        setupMocksForPmkCache(true);
        setupMocksForConnectSequence(false);

        executeAndValidateInitializationSequenceV1_3();
        assertTrue(mDut.connectToNetwork(WLAN0_IFACE_NAME, config));
        mISupplicantStaIfaceCallbackV13.onStateChanged(
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        int statusCode = 7;
        mISupplicantStaIfaceCallbackV13.onAssociationRejected(
                NativeUtil.macAddressToByteArray(BSSID), statusCode, false);
        verify(mPmkCacheManager).remove(eq(testFrameworkNetworkId));
    }

    /**
     * Test getConnectionCapabilities
     * Should fail if running HAL lower than V1_3
     */
    @Test
    public void testGetConnectionCapabilitiesV1_2() throws Exception {
        setupMocksForHalV1_2();
        executeAndValidateInitializationSequenceV1_2();
        WifiNative.ConnectionCapabilities cap = mDut.getConnectionCapabilities(WLAN0_IFACE_NAME);
        assertEquals(ScanResult.WIFI_STANDARD_UNKNOWN, cap.wifiStandard);
    }

    private class GetConnCapabilitiesAnswerV1_3 extends MockAnswerUtil.AnswerWithArguments {
        private android.hardware.wifi.supplicant.V1_3.ConnectionCapabilities mConnCapabilities;

        GetConnCapabilitiesAnswerV1_3(int wifiTechnology, int channelBandwidth,
                int maxNumberTxSpatialStreams, int maxNumberRxSpatialStreams) {
            mConnCapabilities = new android.hardware.wifi.supplicant.V1_3.ConnectionCapabilities();
            mConnCapabilities.technology = wifiTechnology;
            mConnCapabilities.channelBandwidth = channelBandwidth;
            mConnCapabilities.maxNumberTxSpatialStreams = maxNumberTxSpatialStreams;
            mConnCapabilities.maxNumberRxSpatialStreams = maxNumberRxSpatialStreams;
        }

        public void answer(android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                .getConnectionCapabilitiesCallback cb) {
            cb.onValues(mStatusSuccess, mConnCapabilities);
        }
    }

    private class GetConnCapabilitiesAnswerV1_4 extends MockAnswerUtil.AnswerWithArguments {
        private ConnectionCapabilities mConnCapabilities;

        GetConnCapabilitiesAnswerV1_4(int wifiTechnology, int legacyMode, int channelBandwidth,
                int maxNumberTxSpatialStreams, int maxNumberRxSpatialStreams) {
            mConnCapabilities = new ConnectionCapabilities();
            mConnCapabilities.V1_3.technology = wifiTechnology;
            mConnCapabilities.legacyMode = legacyMode;
            mConnCapabilities.V1_3.channelBandwidth = channelBandwidth;
            mConnCapabilities.V1_3.maxNumberTxSpatialStreams = maxNumberTxSpatialStreams;
            mConnCapabilities.V1_3.maxNumberRxSpatialStreams = maxNumberRxSpatialStreams;
        }

        public void answer(android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
                .getConnectionCapabilities_1_4Callback cb) {
            cb.onValues(mStatusSuccessV14, mConnCapabilities);
        }
    }

    /**
     * Test getConnectionCapabilities if running with HAL V1_3
     */
    @Test
    public void testGetConnectionCapabilitiesV1_3() throws Exception {
        setupMocksForHalV1_3();

        executeAndValidateInitializationSequenceV1_3();
        int testWifiTechnologyHal = WifiTechnology.VHT;
        int testWifiStandardWifiInfo = ScanResult.WIFI_STANDARD_11AC;
        int testChannelBandwidthHal = WifiChannelWidthInMhz.WIDTH_80P80;
        int testChannelBandwidth = ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
        int maxNumberTxSpatialStreams = 3;
        int maxNumberRxSpatialStreams = 1;

        doAnswer(new GetConnCapabilitiesAnswerV1_3(testWifiTechnologyHal, testChannelBandwidthHal,
                maxNumberTxSpatialStreams, maxNumberRxSpatialStreams))
                .when(mISupplicantStaIfaceMockV13).getConnectionCapabilities(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getConnectionCapabilitiesCallback.class));
        WifiNative.ConnectionCapabilities cap = mDut.getConnectionCapabilities(WLAN0_IFACE_NAME);
        assertEquals(testWifiStandardWifiInfo, cap.wifiStandard);
        assertEquals(false, cap.is11bMode);
        assertEquals(testChannelBandwidth, cap.channelBandwidth);
        assertEquals(maxNumberTxSpatialStreams, cap.maxNumberTxSpatialStreams);
        assertEquals(maxNumberRxSpatialStreams, cap.maxNumberRxSpatialStreams);
    }

    /**
     * Test getConnectionCapabilities if running with HAL V1_4
     */
    @Test
    public void testGetConnectionCapabilitiesV1_4() throws Exception {
        setupMocksForHalV1_4();

        executeAndValidateInitializationSequenceV1_4();
        int testWifiTechnologyHal = WifiTechnology.LEGACY;
        int testLegacyMode = LegacyMode.B_MODE;
        int testWifiStandardWifiInfo = ScanResult.WIFI_STANDARD_LEGACY;
        int testChannelBandwidthHal = WifiChannelWidthInMhz.WIDTH_20;
        int testChannelBandwidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        int maxNumberTxSpatialStreams = 1;
        int maxNumberRxSpatialStreams = 1;

        doAnswer(new GetConnCapabilitiesAnswerV1_4(testWifiTechnologyHal, testLegacyMode,
                testChannelBandwidthHal, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams))
                .when(mISupplicantStaIfaceMockV14).getConnectionCapabilities_1_4(any(
                android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
                        .getConnectionCapabilities_1_4Callback.class));
        WifiNative.ConnectionCapabilities cap = mDut.getConnectionCapabilities(WLAN0_IFACE_NAME);
        assertEquals(testWifiStandardWifiInfo, cap.wifiStandard);
        assertEquals(true, cap.is11bMode);
        assertEquals(testChannelBandwidth, cap.channelBandwidth);
        assertEquals(maxNumberTxSpatialStreams, cap.maxNumberTxSpatialStreams);
        assertEquals(maxNumberRxSpatialStreams, cap.maxNumberRxSpatialStreams);
    }

    private WifiConfiguration createTestWifiConfiguration() {
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = SUPPLICANT_NETWORK_ID;
        return config;
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
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
        assertNotNull(mISupplicantStaIfaceCallbackV14);

        byte[] bssid = NativeUtil.macAddressToByteArray(BSSID);
        mISupplicantStaIfaceCallbackV14.onHs20TermsAndConditionsAcceptanceRequestedNotification(
                bssid, HS20_URL);

        //TODO: Add test logic once framework handling is implemented
    }

    private void executeAndValidateInitializationSequence() throws  Exception {
        executeAndValidateInitializationSequence(false, false, false, false);
    }

    /**
     * Calls.initialize(), mocking various call back answers and verifying flow, asserting for the
     * expected result. Verifies if ISupplicantStaIface manager is initialized or reset.
     * Each of the arguments will cause a different failure mode when set true.
     */
    private void executeAndValidateInitializationSequence(boolean causeRemoteException,
                                                          boolean getZeroInterfaces,
                                                          boolean getNullInterface,
                                                          boolean causeCallbackRegFailure)
            throws Exception {
        boolean shouldSucceed =
                !causeRemoteException && !getZeroInterfaces && !getNullInterface
                        && !causeCallbackRegFailure;
        // Setup callback mock answers
        ArrayList<ISupplicant.IfaceInfo> interfaces;
        if (getZeroInterfaces) {
            interfaces = new ArrayList<>();
        } else {
            interfaces = mIfaceInfoList;
        }
        doAnswer(new GetListInterfacesAnswer(interfaces)).when(mISupplicantMock)
                .listInterfaces(any(ISupplicant.listInterfacesCallback.class));
        if (causeRemoteException) {
            doThrow(new RemoteException("Some error!!!"))
                    .when(mISupplicantMock).getInterface(any(ISupplicant.IfaceInfo.class),
                    any(ISupplicant.getInterfaceCallback.class));
        } else {
            doAnswer(new GetGetInterfaceAnswer(getNullInterface))
                    .when(mISupplicantMock).getInterface(any(ISupplicant.IfaceInfo.class),
                    any(ISupplicant.getInterfaceCallback.class));
        }
        /** Callback registration */
        if (causeCallbackRegFailure) {
            doAnswer(new MockAnswerUtil.AnswerWithArguments() {
                public SupplicantStatus answer(ISupplicantStaIfaceCallback cb)
                        throws RemoteException {
                    return mStatusFailure;
                }
            }).when(mISupplicantStaIfaceMock)
                    .registerCallback(any(ISupplicantStaIfaceCallback.class));
        } else {
            doAnswer(new MockAnswerUtil.AnswerWithArguments() {
                public SupplicantStatus answer(ISupplicantStaIfaceCallback cb)
                        throws RemoteException {
                    mISupplicantStaIfaceCallback = cb;
                    return mStatusSuccess;
                }
            }).when(mISupplicantStaIfaceMock)
                    .registerCallback(any(ISupplicantStaIfaceCallback.class));
        }

        mInOrder = inOrder(mServiceManagerMock, mISupplicantMock, mISupplicantStaIfaceMock,
                mWifiMonitor);
        // Initialize SupplicantStaIfaceHal, should call serviceManager.registerForNotifications
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(mServiceManagerDeathCaptor.capture(),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(
                eq(ISupplicant.kInterfaceName), eq(""), mServiceNotificationCaptor.capture());
        // act: cause the onRegistration(...) callback to execute
        mServiceNotificationCaptor.getValue().onRegistration(ISupplicant.kInterfaceName, "", true);
        assertTrue(mDut.isInitializationComplete());
        assertEquals(shouldSucceed, mDut.setupIface(WLAN0_IFACE_NAME));
        mInOrder.verify(mISupplicantMock).linkToDeath(mSupplicantDeathCaptor.capture(),
                mDeathRecipientCookieCaptor.capture());
        // verify: listInterfaces is called
        mInOrder.verify(mISupplicantMock).listInterfaces(
                any(ISupplicant.listInterfacesCallback.class));
        if (!getZeroInterfaces) {
            mInOrder.verify(mISupplicantMock)
                    .getInterface(any(ISupplicant.IfaceInfo.class),
                            any(ISupplicant.getInterfaceCallback.class));
        }
        if (!causeRemoteException && !getZeroInterfaces && !getNullInterface) {
            mInOrder.verify(mISupplicantStaIfaceMock)
                    .registerCallback(any(ISupplicantStaIfaceCallback.class));
        }
    }

    /**
     * Calls.initialize(), mocking various call back answers and verifying flow, asserting for the
     * expected result. Verifies if ISupplicantStaIface manager is initialized or reset.
     * Each of the arguments will cause a different failure mode when set true.
     */
    private void executeAndValidateInitializationSequenceV1_1(boolean causeRemoteException,
                                                               boolean getNullInterface)
            throws Exception {
        boolean shouldSucceed = !causeRemoteException && !getNullInterface;
        // Setup callback mock answers
        if (causeRemoteException) {
            doThrow(new RemoteException("Some error!!!"))
                    .when(mISupplicantMockV11).addInterface(any(ISupplicant.IfaceInfo.class),
                    any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                            .addInterfaceCallback.class));
        } else {
            doAnswer(new GetAddInterfaceAnswer(getNullInterface))
                    .when(mISupplicantMockV11).addInterface(any(ISupplicant.IfaceInfo.class),
                    any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                            .addInterfaceCallback.class));
        }
        /** Callback registration */
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(
                    android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback cb)
                    throws RemoteException {
                mISupplicantStaIfaceCallbackV11 = cb;
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMockV11)
                .registerCallback_1_1(
                any(android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback.class));

        mInOrder = inOrder(mServiceManagerMock, mISupplicantMock, mISupplicantMockV11,
                mISupplicantStaIfaceMockV11, mWifiMonitor);
        // Initialize SupplicantStaIfaceHal, should call serviceManager.registerForNotifications
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(mServiceManagerDeathCaptor.capture(),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(
                eq(ISupplicant.kInterfaceName), eq(""), mServiceNotificationCaptor.capture());
        // act: cause the onRegistration(...) callback to execute
        mServiceNotificationCaptor.getValue().onRegistration(ISupplicant.kInterfaceName, "", true);

        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.setupIface(WLAN0_IFACE_NAME) == shouldSucceed);
        mInOrder.verify(mISupplicantMockV11).linkToDeath(mSupplicantDeathCaptor.capture(),
                mDeathRecipientCookieCaptor.capture());
        // verify: addInterface is called
        mInOrder.verify(mISupplicantMockV11)
                .addInterface(any(ISupplicant.IfaceInfo.class),
                        any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                                .addInterfaceCallback.class));
        if (!causeRemoteException && !getNullInterface) {
            mInOrder.verify(mISupplicantStaIfaceMockV11)
                    .registerCallback_1_1(
                    any(android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback.class));
        }

        // Ensure we don't try to use the listInterfaces method from 1.0 version.
        verify(mISupplicantMock, never()).listInterfaces(
                any(ISupplicant.listInterfacesCallback.class));
        verify(mISupplicantMock, never()).getInterface(any(ISupplicant.IfaceInfo.class),
                        any(ISupplicant.getInterfaceCallback.class));
    }

    /**
     * Calls.initialize(), mocking various call back answers and verifying flow, asserting for the
     * expected result. Verifies if ISupplicantStaIface manager is initialized or reset.
     * Each of the arguments will cause a different failure mode when set true.
     */
    private void executeAndValidateInitializationSequenceV1_2()
            throws Exception {
        // Setup callback mock answers
        doAnswer(new GetAddInterfaceAnswerV1_2(false))
                .when(mISupplicantMockV11).addInterface(any(ISupplicant.IfaceInfo.class),
                any(android.hardware.wifi.supplicant.V1_2.ISupplicant
                        .addInterfaceCallback.class));

        /** Callback registration */
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(
                    android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback cb)
                    throws RemoteException {
                mISupplicantStaIfaceCallbackV11 = cb;
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMockV12)
                .registerCallback_1_1(
                        any(android.hardware.wifi.supplicant.V1_1.ISupplicantStaIfaceCallback
                                .class));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(
                    android.hardware.wifi.supplicant.V1_2.ISupplicantStaIfaceCallback cb)
                    throws RemoteException {
                mISupplicantStaIfaceCallbackV12 = cb;
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMockV12)
                .registerCallback_1_2(
                        any(android.hardware.wifi.supplicant.V1_2.ISupplicantStaIfaceCallback
                                .class));

        mInOrder = inOrder(mServiceManagerMock, mISupplicantMock, mISupplicantMockV11,
                mISupplicantStaIfaceMockV12, mWifiMonitor);
        // Initialize SupplicantStaIfaceHal, should call serviceManager.registerForNotifications
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(mServiceManagerDeathCaptor.capture(),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(
                eq(ISupplicant.kInterfaceName), eq(""), mServiceNotificationCaptor.capture());
        // act: cause the onRegistration(...) callback to execute
        mServiceNotificationCaptor.getValue().onRegistration(ISupplicant.kInterfaceName, "", true);

        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.setupIface(WLAN0_IFACE_NAME));
        mInOrder.verify(mISupplicantMockV11).linkToDeath(mSupplicantDeathCaptor.capture(),
                anyLong());
        // verify: addInterface is called
        mInOrder.verify(mISupplicantMockV11)
                .addInterface(any(ISupplicant.IfaceInfo.class),
                        any(android.hardware.wifi.supplicant.V1_2.ISupplicant
                                .addInterfaceCallback.class));

        mInOrder.verify(mISupplicantStaIfaceMockV12)
                .registerCallback_1_2(
                        any(android.hardware.wifi.supplicant.V1_2.ISupplicantStaIfaceCallback
                                .class));

        // Ensure we don't try to use the listInterfaces method from 1.0 version.
//        verify(mISupplicantMock, never()).listInterfaces(
//                any(ISupplicant.listInterfacesCallback.class));
//        verify(mISupplicantMock, never()).getInterface(any(ISupplicant.IfaceInfo.class),
//                any(ISupplicant.getInterfaceCallback.class));
    }

    /**
     * Calls.initialize(), mocking various call back answers and verifying flow, asserting for the
     * expected result. Verifies if ISupplicantStaIface manager is initialized or reset.
     * Each of the arguments will cause a different failure mode when set true.
     */
    private void executeAndValidateInitializationSequenceV1_3()
            throws Exception {
        // Setup callback mock answers
        doAnswer(new GetAddInterfaceAnswerV1_3(false))
                .when(mISupplicantMockV11).addInterface(any(ISupplicant.IfaceInfo.class),
                any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                        .addInterfaceCallback.class));

        /** Callback registration */
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(
                    android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback cb)
                    throws RemoteException {
                mISupplicantStaIfaceCallbackV13 = spy(cb);
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMockV13)
                .registerCallback_1_3(
                        any(android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback
                                .class));

        mInOrder = inOrder(mServiceManagerMock, mISupplicantMock, mISupplicantMockV11,
                mISupplicantStaIfaceMockV13, mWifiMonitor);
        // Initialize SupplicantStaIfaceHal, should call serviceManager.registerForNotifications
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(mServiceManagerDeathCaptor.capture(),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(
                eq(ISupplicant.kInterfaceName), eq(""), mServiceNotificationCaptor.capture());
        // act: cause the onRegistration(...) callback to execute
        mServiceNotificationCaptor.getValue().onRegistration(ISupplicant.kInterfaceName, "", true);

        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.setupIface(WLAN0_IFACE_NAME));
        mInOrder.verify(mISupplicantMockV11).linkToDeath(mSupplicantDeathCaptor.capture(),
                anyLong());
        // verify: addInterface is called
        mInOrder.verify(mISupplicantMockV11)
                .addInterface(any(ISupplicant.IfaceInfo.class),
                        any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                                .addInterfaceCallback.class));

        mInOrder.verify(mISupplicantStaIfaceMockV13)
                .registerCallback_1_3(
                        any(android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback
                                .class));
    }

    /**
     * Calls.initialize(), mocking various call back answers and verifying flow, asserting for the
     * expected result. Verifies if ISupplicantStaIface manager is initialized or reset.
     * Each of the arguments will cause a different failure mode when set true.
     */
    private void executeAndValidateInitializationSequenceV1_4()
            throws Exception {
        // Setup callback mock answers
        doAnswer(new GetAddInterfaceAnswerV1_4(false))
                .when(mISupplicantMockV11).addInterface(any(ISupplicant.IfaceInfo.class),
                any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                        .addInterfaceCallback.class));

        /** Callback registration */
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public android.hardware.wifi.supplicant.V1_4.SupplicantStatus answer(
                    android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback cb)
                    throws RemoteException {
                mISupplicantStaIfaceCallbackV14 = spy(cb);
                return mStatusSuccessV14;
            }
        }).when(mISupplicantStaIfaceMockV14)
                .registerCallback_1_4(
                        any(android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback
                                .class));

        mInOrder = inOrder(mServiceManagerMock, mISupplicantMock, mISupplicantMockV11,
                mISupplicantStaIfaceMockV14, mWifiMonitor);
        // Initialize SupplicantStaIfaceHal, should call serviceManager.registerForNotifications
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(mServiceManagerDeathCaptor.capture(),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(
                eq(ISupplicant.kInterfaceName), eq(""), mServiceNotificationCaptor.capture());
        // act: cause the onRegistration(...) callback to execute
        mServiceNotificationCaptor.getValue().onRegistration(ISupplicant.kInterfaceName, "", true);

        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.setupIface(WLAN0_IFACE_NAME));
        mInOrder.verify(mISupplicantMockV11).linkToDeath(mSupplicantDeathCaptor.capture(),
                anyLong());
        // verify: addInterface is called
        mInOrder.verify(mISupplicantMockV11)
                .addInterface(any(ISupplicant.IfaceInfo.class),
                        any(android.hardware.wifi.supplicant.V1_1.ISupplicant
                                .addInterfaceCallback.class));

        mInOrder.verify(mISupplicantStaIfaceMockV14)
                .registerCallback_1_4(
                        any(android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback
                                .class));
    }

    private SupplicantStatus createSupplicantStatus(int code) {
        SupplicantStatus status = new SupplicantStatus();
        status.code = code;
        return status;
    }

    private android.hardware.wifi.supplicant.V1_4.SupplicantStatus
            createSupplicantStatusV1_4(int code) {
        android.hardware.wifi.supplicant.V1_4.SupplicantStatus status =
                new android.hardware.wifi.supplicant.V1_4.SupplicantStatus();
        status.code = code;
        return status;
    }

    /**
     * Create an IfaceInfo with given type and name
     */
    private ISupplicant.IfaceInfo createIfaceInfo(int type, String name) {
        ISupplicant.IfaceInfo info = new ISupplicant.IfaceInfo();
        info.type = type;
        info.name = name;
        return info;
    }

    private class GetListInterfacesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ArrayList<ISupplicant.IfaceInfo> mInterfaceList;

        GetListInterfacesAnswer(ArrayList<ISupplicant.IfaceInfo> ifaces) {
            mInterfaceList = ifaces;
        }

        public void answer(ISupplicant.listInterfacesCallback cb) {
            cb.onValues(mStatusSuccess, mInterfaceList);
        }
    }

    private class GetGetInterfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        boolean mGetNullInterface;

        GetGetInterfaceAnswer(boolean getNullInterface) {
            mGetNullInterface = getNullInterface;
        }

        public void answer(ISupplicant.IfaceInfo iface, ISupplicant.getInterfaceCallback cb) {
            if (mGetNullInterface) {
                cb.onValues(mStatusSuccess, null);
            } else {
                cb.onValues(mStatusSuccess, mISupplicantIfaceMock);
            }
        }
    }

    private class GetAddInterfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        boolean mGetNullInterface;

        GetAddInterfaceAnswer(boolean getNullInterface) {
            mGetNullInterface = getNullInterface;
        }

        public void answer(ISupplicant.IfaceInfo iface,
                           android.hardware.wifi.supplicant.V1_1.ISupplicant
                                   .addInterfaceCallback cb) {
            if (mGetNullInterface) {
                cb.onValues(mStatusSuccess, null);
            } else {
                cb.onValues(mStatusSuccess, mISupplicantIfaceMock);
            }
        }
    }

    private class GetAddInterfaceAnswerV1_2 extends MockAnswerUtil.AnswerWithArguments {
        boolean mGetNullInterface;

        GetAddInterfaceAnswerV1_2(boolean getNullInterface) {
            mGetNullInterface = getNullInterface;
        }

        public void answer(ISupplicant.IfaceInfo iface,
                android.hardware.wifi.supplicant.V1_2.ISupplicant
                        .addInterfaceCallback cb) {
            if (mGetNullInterface) {
                cb.onValues(mStatusSuccess, null);
            } else {
                cb.onValues(mStatusSuccess, mISupplicantIfaceMock);
            }
        }
    }

    private class GetAddInterfaceAnswerV1_3 extends MockAnswerUtil.AnswerWithArguments {
        boolean mGetNullInterface;

        GetAddInterfaceAnswerV1_3(boolean getNullInterface) {
            mGetNullInterface = getNullInterface;
        }

        public void answer(ISupplicant.IfaceInfo iface,
                android.hardware.wifi.supplicant.V1_3.ISupplicant
                        .addInterfaceCallback cb) {
            if (mGetNullInterface) {
                cb.onValues(mStatusSuccess, null);
            } else {
                cb.onValues(mStatusSuccess, mISupplicantIfaceMock);
            }
        }
    }

    private class GetAddInterfaceAnswerV1_4 extends MockAnswerUtil.AnswerWithArguments {
        boolean mGetNullInterface;

        GetAddInterfaceAnswerV1_4(boolean getNullInterface) {
            mGetNullInterface = getNullInterface;
        }

        public void answer(ISupplicant.IfaceInfo iface,
                android.hardware.wifi.supplicant.V1_4.ISupplicant
                        .addInterfaceCallback cb) {
            if (mGetNullInterface) {
                cb.onValues(mStatusSuccess, null);
            } else {
                cb.onValues(mStatusSuccess, mISupplicantIfaceMock);
            }
        }
    }

    /**
     * Setup mocks for connect sequence.
     */
    private void setupMocksForConnectSequence(final boolean haveExistingNetwork) throws Exception {
        final int existingNetworkId = SUPPLICANT_NETWORK_ID;
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer() throws RemoteException {
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMock).disconnect();
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.listNetworksCallback cb) throws RemoteException {
                if (haveExistingNetwork) {
                    cb.onValues(mStatusSuccess, new ArrayList<>(Arrays.asList(existingNetworkId)));
                } else {
                    cb.onValues(mStatusSuccess, new ArrayList<>());
                }
            }
        }).when(mISupplicantStaIfaceMock)
                .listNetworks(any(ISupplicantStaIface.listNetworksCallback.class));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(int id) throws RemoteException {
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMock).removeNetwork(eq(existingNetworkId));
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(ISupplicantStaIface.addNetworkCallback cb) throws RemoteException {
                cb.onValues(mStatusSuccess, mock(ISupplicantStaNetwork.class));
                return;
            }
        }).when(mISupplicantStaIfaceMock).addNetwork(
                any(ISupplicantStaIface.addNetworkCallback.class));
        when(mSupplicantStaNetworkMock.saveWifiConfiguration(any(WifiConfiguration.class)))
                .thenReturn(true);
        when(mSupplicantStaNetworkMock.select()).thenReturn(true);
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
        verify(mISupplicantStaIfaceMock, times(numNetworkAdditions))
                .addNetwork(any(ISupplicantStaIface.addNetworkCallback.class));
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
                haveExistingNetwork, TRANSLATED_SUPPLICANT_SSID.toString(),
                WifiConfiguration.SECURITY_TYPE_PSK, null);
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
        validateConnectSequence(haveExistingNetwork, 1, ssid);
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
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer() throws RemoteException {
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMock).reassociate();
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
        SupplicantStaNetworkHalHidlImpl linkedNetworkHandle =
                mock(SupplicantStaNetworkHalHidlImpl.class);
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
            verify(mISupplicantStaIfaceMock, times(2))
                    .addNetwork(any(ISupplicantStaIface.addNetworkCallback.class));
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

    /**
     * Helper function to set up Hal cascadingly.
     */
    private void setupMocksForHalV1_1() throws Exception {
        // V1_0 is set up by default, no need to do it.
        when(mServiceManagerMock.getTransport(eq(android.hardware.wifi.supplicant.V1_1.ISupplicant
                .kInterfaceName), anyString()))
                .thenReturn(IServiceManager.Transport.HWBINDER);
    }

    private void setupMocksForHalV1_2() throws Exception {
        setupMocksForHalV1_1();
        when(mServiceManagerMock.getTransport(eq(android.hardware.wifi.supplicant.V1_2.ISupplicant
                .kInterfaceName), anyString()))
                .thenReturn(IServiceManager.Transport.HWBINDER);
    }

    private void setupMocksForHalV1_3() throws Exception {
        setupMocksForHalV1_2();
        when(mServiceManagerMock.getTransport(eq(android.hardware.wifi.supplicant.V1_3.ISupplicant
                .kInterfaceName), anyString()))
                .thenReturn(IServiceManager.Transport.HWBINDER);
    }

    private void setupMocksForHalV1_4() throws Exception {
        setupMocksForHalV1_3();
        when(mServiceManagerMock.getTransport(eq(android.hardware.wifi.supplicant.V1_4.ISupplicant
                .kInterfaceName), anyString()))
                .thenReturn(IServiceManager.Transport.HWBINDER);
    }

    private void setupMocksForPmkCache(boolean isHalSupported) throws Exception {
        ArrayList<Byte> pmkCacheData = NativeUtil.byteArrayToArrayList("deadbeef".getBytes());
        setupMocksForPmkCache(pmkCacheData, isHalSupported);
    }

    private void setupMocksForPmkCache(ArrayList<Byte> pmkCacheData, boolean isHalSupported)
            throws Exception {
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

        if (!isHalSupported) return;
        /** Callback registration */
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public SupplicantStatus answer(
                    android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback cb)
                    throws RemoteException {
                mISupplicantStaIfaceCallbackV13 = cb;
                return mStatusSuccess;
            }
        }).when(mISupplicantStaIfaceMockV13)
                .registerCallback_1_3(
                        any(android.hardware.wifi.supplicant.V1_3.ISupplicantStaIfaceCallback
                                .class));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(WifiConfiguration config, Map<String, String> networkExtra)
                    throws Exception {
                config.networkId = SUPPLICANT_NETWORK_ID;
                return true;
            }
        }).when(mSupplicantStaNetworkMock)
                .loadWifiConfiguration(any(WifiConfiguration.class), any(Map.class));

        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public boolean answer(ArrayList<Byte> serializedData)
                    throws Exception {
                mISupplicantStaIfaceCallbackV13.onPmkCacheAdded(
                        PMK_CACHE_EXPIRATION_IN_SEC, serializedData);
                return true;
            }
        }).when(mSupplicantStaNetworkMock)
                .setPmkCache(any(ArrayList.class));
    }

    private class GetWpaDriverCapabilitiesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private int mWpaDriverCapabilities;

        GetWpaDriverCapabilitiesAnswer(int wpaDriverCapabilities) {
            mWpaDriverCapabilities = wpaDriverCapabilities;
        }

        public void answer(android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                .getWpaDriverCapabilitiesCallback cb) {
            cb.onValues(mStatusSuccess, mWpaDriverCapabilities);
        }
    }

    private class GetWpaDriverCapabilities_1_4Answer extends MockAnswerUtil.AnswerWithArguments {
        private int mWpaDriverCapabilities;

        GetWpaDriverCapabilities_1_4Answer(int wpaDriverCapabilities) {
            mWpaDriverCapabilities = wpaDriverCapabilities;
        }

        public void answer(android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
                .getWpaDriverCapabilities_1_4Callback cb) {
            cb.onValues(mStatusSuccessV14, mWpaDriverCapabilities);
        }
    }

    /**
     * Test To get wpa driver capabilities API on old HAL, should
     * return 0 (not supported)
     */
    @Test
    public void tetGetWpaDriverCapabilitiesOldHal() throws Exception {
        setupMocksForHalV1_2();

        executeAndValidateInitializationSequenceV1_2();

        assertEquals(0, mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test Multi Band operation support (MBO).
     */
    @Test
    public void testGetWpaDriverCapabilitiesMbo() throws Exception {
        setupMocksForHalV1_3();

        executeAndValidateInitializationSequenceV1_3();

        doAnswer(new GetWpaDriverCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_3
                .WpaDriverCapabilitiesMask.MBO))
                .when(mISupplicantStaIfaceMockV13).getWpaDriverCapabilities(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getWpaDriverCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_MBO, mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test Optimized Connectivity support (OCE).
     */
    @Test
    public void testGetWpaDriverCapabilitiesOce() throws Exception {
        setupMocksForHalV1_3();

        executeAndValidateInitializationSequenceV1_3();

        doAnswer(new GetWpaDriverCapabilitiesAnswer(android.hardware.wifi.supplicant.V1_3
                .WpaDriverCapabilitiesMask.MBO
                | android.hardware.wifi.supplicant.V1_3
                .WpaDriverCapabilitiesMask.OCE))
                .when(mISupplicantStaIfaceMockV13).getWpaDriverCapabilities(any(
                android.hardware.wifi.supplicant.V1_3.ISupplicantStaIface
                        .getWpaDriverCapabilitiesCallback.class));

        assertEquals(WIFI_FEATURE_MBO | WIFI_FEATURE_OCE,
                mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test getWpaDriverCapabilities_1_4
     */
    @Test
    public void testGetWpaDriverCapabilities_1_4() throws Exception {
        setupMocksForHalV1_4();

        executeAndValidateInitializationSequenceV1_4();

        doAnswer(new GetWpaDriverCapabilities_1_4Answer(android.hardware.wifi.supplicant.V1_3
                .WpaDriverCapabilitiesMask.MBO
                | android.hardware.wifi.supplicant.V1_3
                .WpaDriverCapabilitiesMask.OCE))
                .when(mISupplicantStaIfaceMockV14).getWpaDriverCapabilities_1_4(any(
                android.hardware.wifi.supplicant.V1_4.ISupplicantStaIface
                        .getWpaDriverCapabilities_1_4Callback.class));

        assertEquals(WIFI_FEATURE_MBO | WIFI_FEATURE_OCE,
                mDut.getWpaDriverFeatureSet(WLAN0_IFACE_NAME));
    }

    /**
     * Test the handling of BSS transition request callback.
     */
    @Test
    public void testBssTmHandlingDoneCallback() throws Exception {
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);
        mISupplicantStaIfaceCallbackV13.onBssTmHandlingDone(new BssTmData());

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

        setupMocksForHalV1_3();
        when(mISupplicantStaIfaceMockV13.filsHlpAddRequest(any(byte[].class),
                any(ArrayList.class))).thenReturn(mStatusSuccess);

        // Fail before initialization is performed.
        assertFalse(mDut.addHlpReq(WLAN0_IFACE_NAME, dstAddr, hlpPacket));
        verify(mISupplicantStaIfaceMockV13, never()).filsHlpAddRequest(any(byte[].class),
                any(ArrayList.class));

        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        ArrayList<Byte> hlpPayload = NativeUtil.byteArrayToArrayList(hlpPacket);
        assertTrue(mDut.addHlpReq(WLAN0_IFACE_NAME, dstAddr, hlpPacket));
        verify(mISupplicantStaIfaceMockV13).filsHlpAddRequest(eq(dstAddr), eq(hlpPayload));
    }

    /**
     * Tests the flushing of FILS HLP packet from supplicant.
     */
    @Test
    public void testFlushAllHlp() throws Exception {

        setupMocksForHalV1_3();
        when(mISupplicantStaIfaceMockV13.filsHlpFlushRequest()).thenReturn(mStatusSuccess);

        // Fail before initialization is performed.
        assertFalse(mDut.flushAllHlp(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMockV13, never()).filsHlpFlushRequest();

        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        assertTrue(mDut.flushAllHlp(WLAN0_IFACE_NAME));
        verify(mISupplicantStaIfaceMockV13).filsHlpFlushRequest();
    }

    /**
     * Tests the handling of state change V13 notification without
     * any configured network.
     */
    @Test
    public void testonStateChangedV13CallbackWithNoConfiguredNetwork() throws Exception {
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        mISupplicantStaIfaceCallbackV13.onStateChanged_1_3(
                ISupplicantStaIfaceCallback.State.INACTIVE,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID), false);

        // Can't compare WifiSsid instances because they lack an equals.
        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiConfiguration.INVALID_NETWORK_ID),
                any(WifiSsid.class), eq(BSSID), eq(0), eq(SupplicantState.INACTIVE));
    }

    /**
     * Tests the handling of state change V13 notification to
     * associated after configuring a network.
     */
    @Test
    public void testStateChangeV13ToAssociatedCallback() throws Exception {
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        mISupplicantStaIfaceCallbackV13.onStateChanged_1_3(
                ISupplicantStaIfaceCallback.State.ASSOCIATED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID), false);

        verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                any(WifiSsid.class), eq(BSSID), eq(0), eq(SupplicantState.ASSOCIATED));
    }

    /**
     * Tests the handling of state change V13 notification to
     * completed after configuring a network.
     */
    @Test
    public void testStateChangeV13ToCompletedCallback() throws Exception {
        InOrder wifiMonitorInOrder = inOrder(mWifiMonitor);
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());

        mISupplicantStaIfaceCallbackV13.onStateChanged_1_3(
                ISupplicantStaIfaceCallback.State.COMPLETED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID), false);

        wifiMonitorInOrder.verify(mWifiMonitor).broadcastNetworkConnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId), eq(false),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID));
        wifiMonitorInOrder.verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                any(WifiSsid.class), eq(BSSID), eq(0), eq(SupplicantState.COMPLETED));
    }

    /**
     * Tests the handling of incorrect network passwords, edge case
     * when onStateChanged_1_3() is used.
     *
     * If the network is removed during 4-way handshake, do not call it a password mismatch.
     */
    @Test
    public void testNetworkRemovedDuring4wayWhenonStateChangedV13IsUsed() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        int reasonCode = 3;

        mISupplicantStaIfaceCallbackV13.onStateChanged_1_3(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID), false);
        mISupplicantStaIfaceCallback.onNetworkRemoved(SUPPLICANT_NETWORK_ID);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

     /**
      * Tests the handling of incorrect network passwords when
      * onStateChanged_1_3() is used, edge case.
      *
      * If the disconnect reason is "IE in 4way differs", do not call it a password mismatch.
      */
    @Test
    public void testIeDiffersWhenonStateChangedV13IsUsed() throws Exception {
        executeAndValidateInitializationSequence();
        assertNotNull(mISupplicantStaIfaceCallback);
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);

        int reasonCode = ISupplicantStaIfaceCallback.ReasonCode.IE_IN_4WAY_DIFFERS;

        mISupplicantStaIfaceCallbackV13.onStateChanged_1_3(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID),
                SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID), false);
        mISupplicantStaIfaceCallback.onDisconnected(
                NativeUtil.macAddressToByteArray(BSSID), true, reasonCode);
        verify(mWifiMonitor, times(0)).broadcastAuthenticationFailureEvent(any(), anyInt(),
                anyInt(), any(), any());
    }

    /**
     * Tests the handling of state change V13 notification to
     * completed (with FILS HLP IE sent) after configuring a
     * network.
     */
    @Test
    public void testStateChangeV13WithFilsHlpIESentToCompletedCallback() throws Exception {
        InOrder wifiMonitorInOrder = inOrder(mWifiMonitor);
        setupMocksForHalV1_3();
        executeAndValidateInitializationSequenceV1_3();
        assertNotNull(mISupplicantStaIfaceCallbackV13);
        int frameworkNetworkId = 6;
        executeAndValidateConnectSequence(frameworkNetworkId, false,
                TRANSLATED_SUPPLICANT_SSID.toString());

        mISupplicantStaIfaceCallbackV13.onStateChanged_1_3(
                ISupplicantStaIfaceCallback.State.COMPLETED,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID), true);

        wifiMonitorInOrder.verify(mWifiMonitor).broadcastNetworkConnectionEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId), eq(true),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID));
        wifiMonitorInOrder.verify(mWifiMonitor).broadcastSupplicantStateChangeEvent(
                eq(WLAN0_IFACE_NAME), eq(frameworkNetworkId),
                eq(TRANSLATED_SUPPLICANT_SSID), eq(BSSID), eq(0), eq(SupplicantState.COMPLETED));
    }

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
     * Tests the handling of association rejection notification V1_4.
     */
    @Test
    public void testAssociationRejectionCallback_1_4() throws Exception {
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
        assertNotNull(mISupplicantStaIfaceCallbackV14);
        AssociationRejectionData assocRejectData = new AssociationRejectionData();
        assocRejectData.ssid = NativeUtil.decodeSsid(SUPPLICANT_SSID);
        assocRejectData.bssid = NativeUtil.macAddressToByteArray(BSSID);
        assocRejectData.statusCode = 5;
        assocRejectData.isOceRssiBasedAssocRejectAttrPresent = true;
        assocRejectData.oceRssiBasedAssocRejectData.retryDelayS = 10;
        assocRejectData.oceRssiBasedAssocRejectData.deltaRssi = 20;
        mISupplicantStaIfaceCallbackV14.onAssociationRejected_1_4(assocRejectData);

        ArgumentCaptor<AssocRejectEventInfo> assocRejectEventInfoCaptor =
                ArgumentCaptor.forClass(AssocRejectEventInfo.class);
        verify(mWifiMonitor).broadcastAssociationRejectionEvent(
                eq(WLAN0_IFACE_NAME), assocRejectEventInfoCaptor.capture());
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) assocRejectEventInfoCaptor.getValue();
        assertNotNull(assocRejectEventInfo);
        assertEquals(TRANSLATED_SUPPLICANT_SSID.toString(), assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertEquals(SupplicantStaIfaceCallbackHidlImpl.halToFrameworkStatusCode(
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
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
        assertNotNull(mISupplicantStaIfaceCallbackV14);
        mISupplicantStaIfaceCallbackV14.onNetworkNotFound(NativeUtil.decodeSsid(SUPPLICANT_SSID));

        verify(mWifiMonitor).broadcastNetworkNotFoundEvent(
                eq(WLAN0_IFACE_NAME), eq(TRANSLATED_SUPPLICANT_SSID.toString()));
    }

    /**
     * Tests the handling of network not found notification.
     */
    @Test
    public void testNetworkNotFoundCallbackTriggersConnectToFallbackSsid() throws Exception {
        setupMocksForHalV1_4();
        executeAndValidateInitializationSequenceV1_4();
        assertNotNull(mISupplicantStaIfaceCallbackV14);
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
        mISupplicantStaIfaceCallbackV14.onNetworkNotFound(NativeUtil.decodeSsid(SUPPLICANT_SSID));

        // Validate that we initiated another connect sequence to the fallback SUPPLICANT_SSID.
        verify(mWifiMonitor, never()).broadcastNetworkNotFoundEvent(
                eq(WLAN0_IFACE_NAME), eq(TRANSLATED_SUPPLICANT_SSID.toString()));
        validateConnectSequence(false, 2, SUPPLICANT_SSID);

        // Fallback SSID was not found, broadcast the network not found event now.
        mISupplicantStaIfaceCallbackV14.onNetworkNotFound(NativeUtil.decodeSsid(SUPPLICANT_SSID));
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
                ISupplicantStaIfaceCallback.State.ASSOCIATING,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onStateChanged(
                ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE,
                NativeUtil.macAddressToByteArray(BSSID), SUPPLICANT_NETWORK_ID,
                NativeUtil.decodeSsid(SUPPLICANT_SSID));
        mISupplicantStaIfaceCallback.onAuthenticationTimeout(
                NativeUtil.macAddressToByteArray(BSSID));
        verify(mWifiMonitor).broadcastAuthenticationFailureEvent(
                eq(WLAN0_IFACE_NAME), eq(WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD), eq(-1),
                eq(TRANSLATED_SUPPLICANT_SSID.toString()), eq(MacAddress.fromString(BSSID)));
    }
}
