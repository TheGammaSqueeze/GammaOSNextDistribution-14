/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.StatsManager;
import android.app.test.TestAlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.AwareResources;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareManager;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.TlvBufferUtils;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.util.HexEncoding;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.Clock;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.InterfaceConflictManager;
import com.android.server.wifi.MockResources;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.aware.WifiAwareDataPathStateManager.WifiAwareNetworkAgent;
import com.android.server.wifi.hal.WifiNanIface.NanDataPathChannelCfg;
import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Unit test harness for WifiAwareDataPathStateManager class.
 */
@SmallTest
public class WifiAwareDataPathStateManagerTest extends WifiBaseTest {
    private static final String sAwareInterfacePrefix = "aware_data";
    private static final String TEST_PACKAGE_NAME = "com.android.somePackage";
    private static final String TEST_FEATURE_ID = "com.android.someFeature";
    private static final int MAX_NDP_SESSION = 8;

    private static final WifiAwareChannelInfo AWARE_CHANNEL_INFO =
            new WifiAwareChannelInfo(5750, CHANNEL_WIDTH_80MHZ, 2);

    private TestLooper mMockLooper;
    private Handler mMockLooperHandler;
    private WifiAwareStateManager mDut;
    @Mock private Clock mClock;
    @Mock private WifiAwareNativeManager mMockNativeManager;
    @Spy private TestUtils.MonitoredWifiAwareNativeApi mMockNative =
            new TestUtils.MonitoredWifiAwareNativeApi();
    @Mock private Context mMockContext;
    @Mock private ConnectivityManager mMockCm;
    @Mock private NetdWrapper mMockNetdWrapper;
    @Mock private InterfaceConflictManager mInterfaceConflictManager;
    @Mock private WifiAwareDataPathStateManager.NetworkInterfaceWrapper mMockNetworkInterface;
    @Mock private IWifiAwareEventCallback mMockCallback;
    @Mock IWifiAwareDiscoverySessionCallback mMockSessionCallback;
    @Mock private WifiAwareMetrics mAwareMetricsMock;
    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private WifiPermissionsWrapper mPermissionsWrapperMock;
    @Mock private WifiManager mMockWifiManager;
    TestAlarmManager mAlarmManager;
    @Mock private PowerManager mMockPowerManager;
    @Mock private WifiInjector mWifiInjector;
    @Mock private PairingConfigManager mPairingConfigManager;
    @Mock private StatsManager mStatsManager;

    @Rule
    public ErrorCollector collector = new ErrorCollector();
    private MockResources mResources;
    private Bundle mExtras = new Bundle();

    /**
     * Initialize mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAlarmManager = new TestAlarmManager();
        when(mMockContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());

        when(mMockContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mMockWifiManager);
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mMockLooper = new TestLooper();
        mMockLooperHandler = new Handler(mMockLooper.getLooper());

        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mMockCm);
        when(mMockContext.getSystemService(ConnectivityManager.class)).thenReturn(mMockCm);
        when(mMockContext.getSystemServiceName(PowerManager.class)).thenReturn(
                Context.POWER_SERVICE);
        when(mMockContext.getSystemService(PowerManager.class)).thenReturn(mMockPowerManager);
        when(mMockContext.getSystemService(StatsManager.class)).thenReturn(mStatsManager);
        mResources = new MockResources();
        mResources.setBoolean(R.bool.config_wifiAllowMultipleNetworksOnSameAwareNdi, false);
        mResources.setBoolean(R.bool.config_wifiAwareNdpSecurityUpdateOnSameNdi, false);
        mResources.setInteger(R.integer.config_wifiConfigurationWifiRunnerThresholdInMs, 4000);
        when(mMockContext.getResources()).thenReturn(mResources);

        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_NAN), any(), anyBoolean()))
                .thenReturn(InterfaceConflictManager.ICM_EXECUTE_COMMAND);

        // by default pretend to be an old API: i.e. allow Responders configured as *ANY*. This
        // allows older (more extrensive) tests to run.
        when(mWifiPermissionsUtil.isTargetSdkLessThan(anyString(), anyInt(), anyInt()))
            .thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.getWifiCallerType(anyInt(), anyString())).thenReturn(6);
        if (SdkLevel.isAtLeastS()) {
            when(mWifiPermissionsUtil.getWifiCallerType(any())).thenReturn(6);
        }

        mDut = new WifiAwareStateManager(mWifiInjector, mPairingConfigManager);
        mDut.setNative(mMockNativeManager, mMockNative);
        mDut.start(mMockContext, mMockLooper.getLooper(), mAwareMetricsMock,
                mWifiPermissionsUtil, mPermissionsWrapperMock, mClock, mMockNetdWrapper,
                mInterfaceConflictManager);
        mDut.startLate();
        mDut.enableVerboseLogging(true, true , true);
        mMockLooper.dispatchAll();

        when(mMockNetworkInterface.configureAgentProperties(any(), any(), any())).thenReturn(true);
        when(mMockNetworkInterface.isAddressUsable(any())).thenReturn(true);

        when(mMockPowerManager.isDeviceIdleMode()).thenReturn(false);
        when(mMockPowerManager.isInteractive()).thenReturn(true);

        mDut.mDataPathMgr.mNetdWrapper = mMockNetdWrapper;
        mDut.mDataPathMgr.mNiWrapper = mMockNetworkInterface;
    }

    /**
     * Post-test validation.
     */
    @After
    public void tearDown() throws Exception {
        mMockNative.validateUniqueTransactionIds();
    }

    /**
     * Validates that creating and deleting all interfaces works based on capabilities.
     */
    @Test
    public void testCreateDeleteAllInterfaces() throws Exception {
        final int numNdis = 3;
        final int failCreateInterfaceIndex = 1;

        Capabilities capabilities = new Capabilities();
        capabilities.maxNdiInterfaces = numNdis;
        capabilities.supportedDataPathCipherSuites = WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> interfaceName = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(mMockNative);

        // (1) get capabilities
        mDut.queryCapabilities();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        mDut.onCapabilitiesUpdateResponse(transactionId.getValue(), capabilities);
        mMockLooper.dispatchAll();

        // (2) create all interfaces
        mDut.createAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        for (int i = 0; i < numNdis; ++i) {
            inOrder.verify(mMockNative).createAwareNetworkInterface(transactionId.capture(),
                    interfaceName.capture());
            collector.checkThat("interface created -- " + i, sAwareInterfacePrefix + i,
                    equalTo(interfaceName.getValue()));
            mDut.onCreateDataPathInterfaceResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        // (3) delete all interfaces [one unsuccessfully] - note that will not necessarily be
        // done sequentially
        boolean[] done = new boolean[numNdis];
        Arrays.fill(done, false);
        mDut.deleteAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        for (int i = 0; i < numNdis; ++i) {
            inOrder.verify(mMockNative).deleteAwareNetworkInterface(transactionId.capture(),
                    interfaceName.capture());
            int interfaceIndex = Integer.valueOf(
                    interfaceName.getValue().substring(sAwareInterfacePrefix.length()));
            done[interfaceIndex] = true;
            if (interfaceIndex == failCreateInterfaceIndex) {
                mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), false, 0);
            } else {
                mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), true, 0);
            }
            mMockLooper.dispatchAll();
        }
        verify(mMockNativeManager, never()).releaseAware();
        for (int i = 0; i < numNdis; ++i) {
            collector.checkThat("interface deleted -- " + i, done[i], equalTo(true));
        }

        // (4) create all interfaces (should get a delete for the one which couldn't delete earlier)
        mDut.createAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        for (int i = 0; i < numNdis; ++i) {
            if (i == failCreateInterfaceIndex) {
                inOrder.verify(mMockNative).deleteAwareNetworkInterface(transactionId.capture(),
                        interfaceName.capture());
                collector.checkThat("interface delete pre-create -- " + i,
                        sAwareInterfacePrefix + i, equalTo(interfaceName.getValue()));
                mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), true, 0);
                mMockLooper.dispatchAll();
            }
            inOrder.verify(mMockNative).createAwareNetworkInterface(transactionId.capture(),
                    interfaceName.capture());
            collector.checkThat("interface created -- " + i, sAwareInterfacePrefix + i,
                    equalTo(interfaceName.getValue()));
            mDut.onCreateDataPathInterfaceResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }
        verifyNoMoreInteractions(mMockNative, mMockNetdWrapper);
    }

    /**
     * Validate that trying to specify port info on subscriber results in failure.
     */
    @Test
    public void testDataPathWithPortInfoOnSubscriber() throws Exception {
        final int clientId = 123;
        final byte pubSubId = 55;
        final int requestorId = 1341234;
        final String passphrase = "SomeSecurePassword";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, false);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequestMore(clientId, res.mSessionId, res.mPeerHandle,
                null, passphrase, false, 0, 5, 6);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());

        // do not create a data-path!
        verify(mMockNative, never()).initiateDataPath(anyShort(), anyInt(), anyInt(), anyInt(),
                any(), anyString(), anyBoolean(), any(), any(),
                any(), eq(pubSubId));


    }

    /**
     * Validate that trying to specify invalid port info results in failure.
     */
    @Test
    public void testDataPathWithPortInfoInvalidPort() throws Exception {
        final int clientId = 123;
        final byte pubSubId = 55;
        final int requestorId = 1341234;
        final int ndpId = 1;
        final String passphrase = "SomeSecurePassword";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, false);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequestMore(clientId, res.mSessionId, res.mPeerHandle,
                null, passphrase, true, 0, -3, 6);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());

        // (2) provide a request
        mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
        mMockLooper.dispatchAll();

        // (3) reject the request
        verify(mMockNative).respondToDataPathRequest(anyShort(), eq(false), anyInt(),
                anyString(), any(), anyBoolean(), any(), any(), eq((byte) 0));
    }

    /**
     * Validate that trying to specify port info without security results in failure.
     */
    @Test
    public void testDataPathWithPortInfoButNoSecurityOnPublisher() throws Exception {
        final int clientId = 123;
        final byte pubSubId = 55;
        final int requestorId = 1341234;
        final int ndpId = 1;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, false);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequestMore(clientId, res.mSessionId, res.mPeerHandle,
                null, null, true, 0, 10, 6);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());

        // (2) provide a request
        mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
        mMockLooper.dispatchAll();

        // (3) reject the request
        verify(mMockNative).respondToDataPathRequest(anyShort(), eq(false), anyInt(),
                anyString(), any(), anyBoolean(), any(), any(), eq((byte) 0));
    }

    /**
     * Validate that if the data-interfaces are deleted while a data-path is being created, the
     * process will terminate.
     */
    @Test
    public void testDestroyNdiDuringNdpSetupResponder() throws Exception {
        final int clientId = 123;
        final byte pubSubId = 55;
        final int requestorId = 1341234;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final int ndpId = 3;

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, true);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.mSessionId, res.mPeerHandle,
                null, null, true, 0);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB);

        // (2) delete interface(s)
        mDut.deleteAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).deleteAwareNetworkInterface(transactionId.capture(),
                anyString());
        mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), true, 0);
        mMockLooper.dispatchAll();

        // (3) have responder receive request
        mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
        mMockLooper.dispatchAll();

        // (4) verify that responder aborts (i.e. refuses request)
        inOrder.verify(mMockNative).respondToDataPathRequest(transactionId.capture(), eq(false),
                eq(ndpId), eq(""), eq(null), eq(false), any(), any(), eq((byte) 0));
        mDut.onRespondToDataPathSetupRequestResponse(transactionId.getValue(), true, 0);
        mMockLooper.dispatchAll();
        assertFalse(mAlarmManager
                .isPending(WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));

        verifyRequestDeclaredUnfullfillable(nr);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        // failure if there's further activity
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * Validate multiple NDPs created on a single NDI when overlay set to enabled multiple NDP on
     * same aware NDI. Most importantly that the interface is set up on first NDP and torn down on
     * last NDP - and not when one or the other is created or deleted.
     *
     * Procedure:
     * - create NDP 1, 2, and 3 (interface up only on first)
     * - delete NDP 2, 1, and 3 (interface down only on last)
     */
    @Test
    public void testMultipleNdpsOnSingleNdi() throws Exception {
        final int clientId = 123;
        final byte pubSubId = 58;
        final int requestorId = 1341234;
        final int ndpId = 2;
        final String interfaceName = sAwareInterfacePrefix + "0";
        final int port = 23;
        final byte transportProtocol = 6;

        final int[] startOrder = {0, 1, 2};
        final int[] endOrder = {1, 0, 2};
        int networkRequestId = 0;
        int expectAvailableNdp = MAX_NDP_SESSION;
        AwareResources awareResources;

        mResources.setBoolean(R.bool.config_wifiAllowMultipleNetworksOnSameAwareNdi, true);

        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor =
                ArgumentCaptor.forClass(WifiAwareNetworkAgent.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        NetworkRequest[] nrs = new NetworkRequest[3];
        DataPathEndPointInfo[] ress = new DataPathEndPointInfo[3];
        WifiAwareNetworkAgent[] agentBinders = new WifiAwareNetworkAgent[3];
        Messenger messenger = null;
        boolean first = true;
        for (int i : startOrder) {
            networkRequestId += 1;
            byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
            byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
            peerDiscoveryMac[5] = (byte) (peerDiscoveryMac[5] + i);
            peerDataPathMac[5] = (byte) (peerDataPathMac[5] + i);

            // (0) initialize
            ress[i] = initDataPathEndPoint(first, clientId, (byte) (pubSubId + i),
                    requestorId + i, peerDiscoveryMac, inOrder, inOrderM, false);
            if (first) {
                messenger = ress[i].mMessenger;
            }

            // (1) request network
            nrs[i] = getSessionNetworkRequest(clientId, ress[i].mSessionId, ress[i].mPeerHandle,
                    null, null, false, networkRequestId);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nrs[i];
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                    WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB);
            inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(),
                    eq(requestorId + i),
                    eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(), eq(peerDiscoveryMac),
                    eq(interfaceName),
                    eq(false), any(), any(), any(), eq((byte) (pubSubId + i)));

            mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId + i);
            mMockLooper.dispatchAll();
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(--expectAvailableNdp, awareResources.getAvailableDataPathsCount());
            }

            // (2) get confirmation
            mDut.onDataPathConfirmNotification(ndpId + i, peerDataPathMac, true, 0,
                    buildTlv(port, transportProtocol, true), List.of(AWARE_CHANNEL_INFO));
            mMockLooper.dispatchAll();
            if (first) {
                inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
                inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());

                first = false;
            }
            inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
            agentBinders[i] = agentCaptor.getValue();
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                    eq(false), anyInt(), anyLong(), anyInt(), anyInt());
            inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
            WifiAwareNetworkInfo netInfo =
                    (WifiAwareNetworkInfo) agentBinders[i].mDataPathCapabilities.getTransportInfo();
            assertArrayEquals(MacAddress.fromBytes(
                    peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                    netInfo.getPeerIpv6Addr().getAddress());
            assertEquals(port, netInfo.getPort());
            assertEquals(transportProtocol, netInfo.getTransportProtocol());
            assertEquals(i + 1, mDut.mDataPathMgr.getNumOfNdps());
            assertEquals(AWARE_CHANNEL_INFO, netInfo.getChannelInfoList().get(0));
        }

        // (3) end data-path (unless didn't get confirmation)
        int index = 0;
        for (int i: endOrder) {
            Message endNetworkReqMsg = Message.obtain();
            endNetworkReqMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
            endNetworkReqMsg.obj = nrs[i];
            messenger.send(endNetworkReqMsg);

            agentBinders[i].onNetworkUnwanted();
            mMockLooper.dispatchAll();
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(++expectAvailableNdp, awareResources.getAvailableDataPathsCount());
            }

            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId + i));

            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mDut.onDataPathEndNotification(ndpId + i);
            mMockLooper.dispatchAll();

            if (index++ == endOrder.length - 1) {
                inOrder.verify(mMockNetdWrapper).setInterfaceDown(anyString());
            }
            inOrderM.verify(mAwareMetricsMock).recordNdpSessionDuration(anyLong());
        }
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * Validate that multiple NDP requests which resolve to the same canonical request are treated
     * as one.
     */
    @Test
    public void testMultipleIdenticalRequests() throws Exception {
        final int numRequestsPre = 6;
        final int numRequestsPost = 5;
        final int clientId = 123;
        final int ndpId = 5;
        final int port = 0;
        final int transportProtocol = 6; // can't specify transport protocol without port
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        final byte[] allZeros = HexEncoding.decode("000000000000".toCharArray(), false);
        NetworkRequest[] nrs = new NetworkRequest[numRequestsPre + numRequestsPost + 1];
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor =
                ArgumentCaptor.forClass(WifiAwareNetworkAgent.class);
        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) initialize all clients
        Messenger messenger = initOobDataPathEndPoint(true, 2, clientId, inOrder, inOrderM);
        for (int i = 1; i < numRequestsPre + numRequestsPost; ++i) {
            initOobDataPathEndPoint(false, 1, clientId + i, inOrder, inOrderM);
        }
        DataPathEndPointInfo ddepi = initDataPathEndPoint(false,
                clientId + numRequestsPre + numRequestsPost, (byte) 10, 11, peerDiscoveryMac,
                inOrder, inOrderM, false);

        // (2) make initial network requests (all identical under the hood)
        for (int i = 0; i < numRequestsPre; ++i) {
            nrs[i] = getDirectNetworkRequest(clientId + i,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, null,
                    null, i);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nrs[i];
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
        }
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());

        // (3) verify the start NDP HAL request
        inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(), eq(0),
                eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(), eq(peerDiscoveryMac),
                eq(sAwareInterfacePrefix + "0"), eq(true), any(), any(),
                any(), eq((byte) 0));

        // (4) unregister request #0 (the primary)
        Message endNetworkReqMsg = Message.obtain();
        endNetworkReqMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
        endNetworkReqMsg.obj = nrs[0];
        messenger.send(endNetworkReqMsg);
        mMockLooper.dispatchAll();

        // (5) respond to the registration request
        mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId);
        mMockLooper.dispatchAll();
        if (SdkLevel.isAtLeastT()) {
            awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
            assertEquals(--expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
        }

        // (6) unregister request #1
        endNetworkReqMsg = Message.obtain();
        endNetworkReqMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
        endNetworkReqMsg.obj = nrs[1];
        messenger.send(endNetworkReqMsg);
        mMockLooper.dispatchAll();

        // (6.5) provide a (semi) bogus NDP Requst Indication - mostly bogus on Initiator but
        // may contain the peer's TLVs (in this case it does)
        mDut.onDataPathRequestNotification(0, allZeros, ndpId,
                buildTlv(port, transportProtocol, true));

        // (7) confirm the NDP creation
        mDut.onDataPathConfirmNotification(ndpId, peerDataPathMac, true, 0, null, null);
        mMockLooper.dispatchAll();

        inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
        inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
        inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
        inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), anyLong(), anyInt(), anyInt());
        inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
        WifiAwareNetworkInfo netInfo =
                (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                        .getTransportInfo();
        assertArrayEquals(MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                netInfo.getPeerIpv6Addr().getAddress());
        assertEquals(port, netInfo.getPort());
        assertEquals(transportProtocol, netInfo.getTransportProtocol());
        assertEquals(1, mDut.mDataPathMgr.getNumOfNdps());

        // (8) execute 'post' requests
        for (int i = numRequestsPre; i < numRequestsPre + numRequestsPost; ++i) {
            nrs[i] = getDirectNetworkRequest(clientId + i,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, null,
                    null, i);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nrs[i];
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
        }

        nrs[numRequestsPre + numRequestsPost] = getSessionNetworkRequest(
                clientId + numRequestsPre + numRequestsPost, ddepi.mSessionId, ddepi.mPeerHandle,
                null, null, false, 11);
        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nrs[numRequestsPre + numRequestsPost];
        reqNetworkMsg.arg1 = 0;
        messenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());

        // (9) unregister all requests
        for (int i = 2; i < numRequestsPre + numRequestsPost + 1; ++i) {
            endNetworkReqMsg = Message.obtain();
            endNetworkReqMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
            endNetworkReqMsg.obj = nrs[i];
            messenger.send(endNetworkReqMsg);
            mMockLooper.dispatchAll();
        }

        agentCaptor.getValue().onNetworkUnwanted();
        mMockLooper.dispatchAll();

        // (10) verify that NDP torn down
        if (SdkLevel.isAtLeastT()) {
            awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
            assertEquals(++expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
        }
        inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));

        mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
        mDut.onDataPathEndNotification(ndpId);
        mMockLooper.dispatchAll();

        inOrder.verify(mMockNetdWrapper).setInterfaceDown(anyString());
        inOrderM.verify(mAwareMetricsMock).recordNdpSessionDuration(anyLong());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mMockCallback, mMockSessionCallback,
                mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * Validate that multiple NDP requests to the same peer target different NDIs.
     */
    @Test
    public void testMultipleNdiToSamePeer() throws Exception {
        final int numNdis = 5;
        final int clientId = 123;
        final int ndpId = 5;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> ifNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor = ArgumentCaptor.forClass(
                WifiAwareNetworkAgent.class);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) initialize all clients
        Messenger messenger = initOobDataPathEndPoint(true, numNdis, clientId, inOrder, inOrderM);
        for (int i = 1; i < numNdis + 3; ++i) {
            initOobDataPathEndPoint(false, numNdis, clientId + i, inOrder, inOrderM);
        }

        // (2) make N network requests: each unique
        Set<String> interfaces = new HashSet<>();
        for (int i = 0; i < numNdis + 1; ++i) {
            byte[] pmk = new byte[32];
            pmk[0] = (byte) i;

            NetworkRequest nr = getDirectNetworkRequest(clientId + i,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, pmk,
                    null, i);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nr;
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                    WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB);

            if (i < numNdis) {
                inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(), eq(0),
                        eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(),
                        eq(peerDiscoveryMac), ifNameCaptor.capture(), eq(true), any(), any(),
                        any(), eq((byte) 0));
                interfaces.add(ifNameCaptor.getValue());

                mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId + i);
                mDut.onDataPathConfirmNotification(ndpId + i, peerDataPathMac, true, 0, null, null);
                mMockLooper.dispatchAll();
                if (SdkLevel.isAtLeastT()) {
                    awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                    assertEquals(--expectedAvailableNdps,
                            awareResources.getAvailableDataPathsCount());
                }

                inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
                inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
                inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
                inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                        eq(true), anyInt(), anyLong(), anyInt(), anyInt());
                inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
                WifiAwareNetworkInfo netInfo =
                        (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                                .getTransportInfo();
                assertArrayEquals(MacAddress.fromBytes(
                        peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                        netInfo.getPeerIpv6Addr().getAddress());
                assertEquals(0, netInfo.getPort()); // uninitialized -> 0
                assertEquals(-1, netInfo.getTransportProtocol()); // uninitialized -> -1
                assertEquals(i + 1, mDut.mDataPathMgr.getNumOfNdps());
            } else {
                verifyRequestDeclaredUnfullfillable(nr);
            }
        }

        // verify that each interface name is unique
        assertEquals("Number of unique interface names", numNdis, interfaces.size());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mMockCallback, mMockSessionCallback,
                mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * Validate that multiple NDP requests to the same peer target different NDIs.
     */
    @Test
    public void testMultipleNdiToSamePeerWithSecurityUpgrade() throws Exception {
        mResources.setBoolean(R.bool.config_wifiAwareNdpSecurityUpdateOnSameNdi, true);
        final int numNdis = 5;
        final int clientId = 123;
        final int ndpId = 5;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources;
        boolean first = true;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> ifNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor = ArgumentCaptor.forClass(
                WifiAwareNetworkAgent.class);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) initialize all clients
        Messenger messenger = initOobDataPathEndPoint(true, numNdis, clientId, inOrder, inOrderM);
        for (int i = 1; i < numNdis + 3; ++i) {
            initOobDataPathEndPoint(false, numNdis, clientId + i, inOrder, inOrderM);
        }

        // (2) make N network requests: each unique
        Set<String> interfaces = new HashSet<>();
        for (int i = 0; i < numNdis + 1; ++i) {
            byte[] pmk = new byte[32];
            pmk[0] = (byte) i;

            NetworkRequest nr = getDirectNetworkRequest(clientId + i,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, pmk,
                    null, i);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nr;
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                    WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB);

            inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(), eq(0),
                    eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(), eq(peerDiscoveryMac),
                    ifNameCaptor.capture(), eq(true), any(), any(), any(), eq((byte) 0));
            interfaces.add(ifNameCaptor.getValue());

            mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId + i);
            mDut.onDataPathConfirmNotification(ndpId + i, peerDataPathMac, true, 0, null, null);
            mMockLooper.dispatchAll();
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(--expectedAvailableNdps,
                        awareResources.getAvailableDataPathsCount());
            }

            if (first) {
                inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
                inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());

                first = false;
            }
            inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                    eq(true), anyInt(), anyLong(), anyInt(), anyInt());
            inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
            WifiAwareNetworkInfo netInfo =
                    (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                            .getTransportInfo();
            assertArrayEquals(MacAddress.fromBytes(
                    peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                    netInfo.getPeerIpv6Addr().getAddress());
            assertEquals(0, netInfo.getPort()); // uninitialized -> 0
            assertEquals(-1, netInfo.getTransportProtocol()); // uninitialized -> -1
            assertEquals(i + 1, mDut.mDataPathMgr.getNumOfNdps());
        }

        // verify that each interface name is unique
        assertEquals("Number of unique interface names", 1, interfaces.size());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mMockCallback, mMockSessionCallback,
                mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * When overlay set to disabled multiple networks on single Aware NDI, validate that multiple
     * NDP requests to the different peer target different NDIs. And when number of requests exceeds
     * the number of NDIs, request will be rejected.
     */
    @Test
    public void testMultipleNdiToDifferentPeer() throws Exception {
        final int numNdis = 5;
        final int clientId = 123;
        final int ndpId = 5;
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> ifNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor = ArgumentCaptor.forClass(
                WifiAwareNetworkAgent.class);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) initialize all clients
        Messenger messenger = initOobDataPathEndPoint(true, numNdis, clientId, inOrder, inOrderM);
        for (int i = 1; i < numNdis + 3; ++i) {
            initOobDataPathEndPoint(false, numNdis, clientId + i, inOrder, inOrderM);
        }

        // (2) make N network requests: each unique
        Set<String> interfaces = new HashSet<>();
        for (int i = 0; i < numNdis + 1; ++i) {
            final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
            final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
            peerDiscoveryMac[5] = (byte) (peerDiscoveryMac[5] + i);
            peerDataPathMac[5] = (byte) (peerDataPathMac[5] + i);

            byte[] pmk = new byte[32];
            pmk[0] = (byte) i;

            NetworkRequest nr = getDirectNetworkRequest(clientId + i,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, pmk,
                    null, i);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nr;
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                    WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB);

            if (i < numNdis) {
                inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(), eq(0),
                        eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(),
                        eq(peerDiscoveryMac), ifNameCaptor.capture(), eq(true), any(), any(),
                        any(), eq((byte) 0));
                interfaces.add(ifNameCaptor.getValue());

                mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId + i);
                mDut.onDataPathConfirmNotification(ndpId + i, peerDataPathMac, true, 0, null, null);
                mMockLooper.dispatchAll();

                if (SdkLevel.isAtLeastT()) {
                    awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                    assertEquals(--expectedAvailableNdps,
                            awareResources.getAvailableDataPathsCount());
                }
                inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
                inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
                inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
                inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                        eq(true), anyInt(), anyLong(), anyInt(), anyInt());
                inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
                WifiAwareNetworkInfo netInfo =
                        (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                                .getTransportInfo();
                assertArrayEquals(MacAddress.fromBytes(
                        peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                        netInfo.getPeerIpv6Addr().getAddress());
                assertEquals(0, netInfo.getPort()); // uninitialized -> 0
                assertEquals(-1, netInfo.getTransportProtocol()); // uninitialized -> -1
                assertEquals(i + 1, mDut.mDataPathMgr.getNumOfNdps());
            } else {
                verifyRequestDeclaredUnfullfillable(nr);
            }
        }

        // verify that each interface name is unique
        assertEquals("Number of unique interface names", numNdis, interfaces.size());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mMockCallback, mMockSessionCallback,
                mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * When overlay set to enable multiple networks on single Aware NDI, validate that multiple
     * NDP requests to the different peer target same NDI when only one NDI is available. Also when
     * requests to a peer that is already accepted by this NDI, the new request should be reject.
     */
    @Test
    public void testMultipleNdpToDifferentPeerOnSingleNdi() throws Exception {
        final int numNdis = 1;
        final int clientId = 123;
        final int ndpId = 5;
        final int numberNdp = 3;
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources = null;

        mResources.setBoolean(R.bool.config_wifiAllowMultipleNetworksOnSameAwareNdi, true);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> ifNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor = ArgumentCaptor.forClass(
                WifiAwareNetworkAgent.class);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) initialize all clients
        Messenger messenger = initOobDataPathEndPoint(true, numNdis, clientId, inOrder, inOrderM);
        for (int i = 1; i < numberNdp; ++i) {
            initOobDataPathEndPoint(false, numNdis, clientId + i, inOrder, inOrderM);
        }

        // (2) make 2 network requests: each unique
        Set<String> interfaces = new HashSet<>();
        boolean first = true;
        for (int i = 0; i < numberNdp - 1; ++i) {
            final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
            final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
            peerDiscoveryMac[5] = (byte) (peerDiscoveryMac[5] + i);
            peerDataPathMac[5] = (byte) (peerDataPathMac[5] + i);

            byte[] pmk = new byte[32];
            pmk[0] = (byte) i;

            NetworkRequest nr = getDirectNetworkRequest(clientId + i,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, pmk,
                    null, i);

            Message reqNetworkMsg = Message.obtain();
            reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
            reqNetworkMsg.obj = nr;
            reqNetworkMsg.arg1 = 0;
            messenger.send(reqNetworkMsg);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                    WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB);

            inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(), eq(0),
                    eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(), eq(peerDiscoveryMac),
                    ifNameCaptor.capture(), eq(true), any(), any(),
                    any(), eq((byte) 0));
            interfaces.add(ifNameCaptor.getValue());

            mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId + i);
            mDut.onDataPathConfirmNotification(ndpId + i, peerDataPathMac, true, 0, null, null);
            mMockLooper.dispatchAll();
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(--expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
            }

            if (first) {
                inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
                inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
                first = false;
            }
            inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                    eq(true), anyInt(), anyLong(), anyInt(), anyInt());
            inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
            WifiAwareNetworkInfo netInfo =
                    (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                            .getTransportInfo();
            assertArrayEquals(MacAddress.fromBytes(
                    peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                    netInfo.getPeerIpv6Addr().getAddress());
            assertEquals(0, netInfo.getPort()); // uninitialized -> 0
            assertEquals(-1, netInfo.getTransportProtocol()); // uninitialized -> -1
            assertEquals(i + 1, mDut.mDataPathMgr.getNumOfNdps());
        }

        // verify that two request all using the same interface
        assertEquals("Number of unique interface names", numNdis, interfaces.size());


        // make the 3rd network request which has the same peer as the first one.
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        byte[] pmk = new byte[32];
        pmk[0] = (byte) 2;

        NetworkRequest nr = getDirectNetworkRequest(clientId + 2,
                WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, pmk,
                null, 2);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        messenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB);

        // It should be reject as interface already has a request to this peer.
        verifyRequestDeclaredUnfullfillable(nr);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mMockCallback, mMockSessionCallback,
                mAwareMetricsMock, mMockNetdWrapper);
    }

    /*
     * Initiator tests
     */

    /**
     * Validate the success flow of the Initiator: using session network specifier with a non-null
     * PMK.
     */
    @Test
    public void testDataPathInitiatorMacPmkSuccess() throws Exception {
        testDataPathInitiatorUtility(false, true, true, false, true, false);
    }

    /**
     * Validate the success flow of the Initiator: using a direct network specifier with a non-null
     * peer mac and non-null PMK.
     */
    @Test
    public void testDataPathInitiatorDirectMacPmkSuccess() throws Exception {
        testDataPathInitiatorUtility(true, true, true, false, true, false);
    }


    /**
     * Validate the fail flow of the Initiator: use a session network specifier with a non-null
     * PMK, but don't get a confirmation.
     */
    @Test
    public void testDataPathInitiatorNoConfirmationTimeoutFail() throws Exception {
        testDataPathInitiatorUtility(false, true, true, false, false, false);
    }

    /**
     * Validate the fail flow of the Initiator: use a session network specifier with a non-null
     * Passphrase, but get an immediate failure
     */
    @Test
    public void testDataPathInitiatorNoConfirmationHalFail() throws Exception {
        testDataPathInitiatorUtility(false, true, false, true, true, true);
    }

    /**
     * Verify that an TLV configuration with large port/transport-protocol work correctly.
     */
    @Test
    public void testDataPathInitiatorNetInfoLargeValuesExp1() throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        String linkLocalIpv6Address = MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getHostAddress();

        testDataPathInitiatorUtilityMore(false, true, true, false, true, false, peerDataPathMac,
                buildTlv((1 << 16) - 1, (1 << 8) - 1, true), (1 << 16) - 1, (1 << 8) - 1,
                linkLocalIpv6Address, 0);
    }

    /**
     * Verify that an TLV configuration with large port/transport-protocol work correctly.
     */
    @Test
    public void testDataPathInitiatorNetInfoLargeValuesExp2() throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        String linkLocalIpv6Address = MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getHostAddress();

        testDataPathInitiatorUtilityMore(false, true, true, false, true, false, peerDataPathMac,
                buildTlv(1 << 15, 1 << 7, true), 1 << 15, 1 << 7, linkLocalIpv6Address, 0);
    }

    /**
     * Verify that an TLV configuration with large port/transport-protocol work correctly.
     */
    @Test
    public void testDataPathInitiatorNetInfoLargeValuesExp3() throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        String linkLocalIpv6Address = MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getHostAddress();

        testDataPathInitiatorUtilityMore(false, true, true, false, true, false, peerDataPathMac,
                buildTlv((1 << 15) - 1, (1 << 7) - 1, true), (1 << 15) - 1, (1 << 7) - 1,
                linkLocalIpv6Address, 0);
    }

    /**
     * Verify that an TLV configuration with an IPv6 override works correctly.
     */
    @Test
    public void testDataPathInitiatorNetInfoIpv6Override() throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        final byte[] testVector =
                new byte[]{0x00, 0x08, 0x00, 0x00, (byte) 0xb3, (byte) 0xe1, (byte) 0xff,
                        (byte) 0xfe, 0x7a, 0x2f, (byte) 0xa2};

        testDataPathInitiatorUtilityMore(false, true, true, false, true, false, peerDataPathMac,
                testVector, 0, -1, "fe80::b3:e1ff:fe7a:2fa2", 0);
    }

    /**
     * Verify that retrying address validation a 'small' number of times results in successful
     * NDP setup.
     */
    @Test
    public void testDataPathInitiatorAddressValidationRetrySuccess() throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        String linkLocalIpv6Address = MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getHostAddress();

        testDataPathInitiatorUtilityMore(false, true, true, false, true, false, peerDataPathMac,
                null, 0, -1, linkLocalIpv6Address,
                WifiAwareDataPathStateManager.ADDRESS_VALIDATION_TIMEOUT_MS
                        / WifiAwareDataPathStateManager.ADDRESS_VALIDATION_RETRY_INTERVAL_MS - 1);
    }

    /**
     * Verify that retrying address validation a 'large' number of times results in failure.
     */
    @Test
    public void testDataPathInitiatorAddressValidationRetryFail() throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        String linkLocalIpv6Address = MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getHostAddress();

        testDataPathInitiatorUtilityMore(false, true, true, false, true, false, peerDataPathMac,
                null, 0, -1, linkLocalIpv6Address,
                WifiAwareDataPathStateManager.ADDRESS_VALIDATION_TIMEOUT_MS
                        / WifiAwareDataPathStateManager.ADDRESS_VALIDATION_RETRY_INTERVAL_MS + 10);
    }

    /**
     * Validate the fail flow of a mis-configured request: Publisher as Initiator
     */
    @Test
    public void testDataPathInitiatorOnPublisherError() throws Exception {
        testDataPathInitiatorResponderMismatchUtility(true);
    }

    /**
     * Validate the fail flow of an Initiator (subscriber) with its UID set as a malicious
     * attacker (i.e. mismatched to its requested client's UID).
     */
    @Test
    public void testDataPathInitiatorUidSetIncorrectlyError() throws Exception {
        testDataPathInitiatorResponderInvalidUidUtility(false);
    }

    /**
     * Validate the fail flow of an Initiator (subscriber) with its package namee set as a malicious
     * attacker (i.e. mismatched to its requested client's package name).
     */
    @Test
    public void testDataPathInitiatorPackageNameSetIncorrectlyError() throws Exception {
        testDataPathInitiatorResponderInvalidPackageNameUtility(false);
    }

    /*
     * Responder tests
     */

    /**
     * Validate the success flow of the Responder: using session network specifier with a
     * PMK.
     */
    @Test
    public void testDataPathResonderMacPmkSuccess() throws Exception {
        testDataPathResponderUtility(false, true, true, false, true);
    }

    /**
     * Validate the success flow of the Responder: using session network specifier with a
     * Passphrase.
     */
    @Test
    public void testDataPathResonderMacPassphraseSuccess() throws Exception {
        testDataPathResponderUtility(false, true, false, false, true);
    }

    /**
     * Validate the success flow of the Responder: using session network specifier with a
     * Passphrase and no peer ID (i.e. 0).
     */
    @Test
    public void testDataPathResonderMacPassphraseNoPeerIdSuccess() throws Exception {
        testDataPathResponderUtility(false, false, false, true, true);
    }

    /**
     * Validate the success flow of the Responder: using session network specifier with a null
     * PMK/Passphrase and no peer ID (i.e. 0).
     */
    @Test
    public void testDataPathResonderMacOpenNoPeerIdNoPmkPassphraseSuccess() throws Exception {
        testDataPathResponderUtility(false, false, false, false, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a non-null
     * peer mac and non-null PMK.
     */
    @Test
    public void testDataPathResonderDirectMacPmkSuccess() throws Exception {
        testDataPathResponderUtility(true, true, true, false, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a non-null
     * peer mac and null PMK/Passphrase.
     */
    @Test
    public void testDataPathResonderDirectMacNoPmkPassphraseSuccess() throws Exception {
        testDataPathResponderUtility(true, true, false, false, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a null peer
     * mac and non-null Passphrase.
     */
    @Test
    public void testDataPathResonderDirectNoMacPassphraseSuccess() throws Exception {
        testDataPathResponderUtility(true, false, false, true, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a null peer
     * mac and null Pmk/Passphrase.
     */
    @Test
    public void testDataPathResonderDirectNoMacNoPmkPassphraseSuccess() throws Exception {
        testDataPathResponderUtility(true, false, false, false, true);
    }

    /**
     * Validate the fail flow of the Responder: use a session network specifier with a non-null
     * PMK, but don't get a confirmation.
     */
    @Test
    public void testDataPathResponderNoConfirmationTimeoutFail() throws Exception {
        testDataPathResponderUtility(false, true, true, false, false);
    }

    /**
     * Validate the fail flow of a mis-configured request: Subscriber as Responder
     */
    @Test
    public void testDataPathResponderOnSubscriberError() throws Exception {
        testDataPathInitiatorResponderMismatchUtility(false);
    }

    /**
     * Validate the fail flow of an Initiator (subscriber) with its UID set as a malicious
     * attacker (i.e. mismatched to its requested client's UID).
     */
    @Test
    public void testDataPathResponderUidSetIncorrectlyError() throws Exception {
        testDataPathInitiatorResponderInvalidUidUtility(true);
    }


    /**
     * Validate the fail flow of an Initiator (subscriber) with its package name set as a malicious
     * attacker (i.e. mismatched to its requested client's package name).
     */
    @Test
    public void testDataPathResponderPackageNameSetIncorrectlyError() throws Exception {
        testDataPathInitiatorResponderInvalidPackageNameUtility(true);
    }

    /**
     * Validate the TLV generation based on a test vector manually generated from spec.
     */
    @Test
    public void testTlvGenerationTestVectorPortTransportProtocol() {
        int port = 7000;
        int transportProtocol = 6;

        byte[] tlvData = WifiAwareDataPathStateManager.NetworkInformationData.buildTlv(port,
                transportProtocol);
        byte[] testVector =
                new byte[]{0x01, 0x0d, 0x00, 0x50, 0x6f, (byte) 0x9a, 0x02, 0x00, 0x02, 0x00, 0x58,
                        0x1b, 0x01, 0x01, 0x00, 0x06};

        assertArrayEquals(testVector, tlvData);
    }

    /**
     * Validate the TLV parsing based on a test vector of the port + transport protocol manually
     * generated from spec.
     */
    @Test
    public void testTlvParsingTestVectorPortTransportProtocol() {
        int port = 7000;
        int transportProtocol = 6;

        byte[] testVector =
                new byte[]{0x01, 0x0d, 0x00, 0x50, 0x6f, (byte) 0x9a, 0x02, 0x00, 0x02, 0x00, 0x58,
                        0x1b, 0x01, 0x01, 0x00, 0x06};

        WifiAwareDataPathStateManager.NetworkInformationData.ParsedResults parsed =
                WifiAwareDataPathStateManager.NetworkInformationData.parseTlv(testVector);
        assertEquals(port, (int) parsed.port);
        assertEquals(transportProtocol, (int) parsed.transportProtocol);
    }

    /*
     * Utilities
     */

    private void testDataPathInitiatorResponderMismatchUtility(boolean doPublish) throws Exception {
        final int clientId = 123;
        final byte pubSubId = 55;
        final int ndpId = 2;
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final int requestorId = 1341234;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, doPublish);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.mSessionId, res.mPeerHandle, pmk,
                null, doPublish, 0);

        // corrupt the network specifier: reverse the role (so it's mis-matched)
        WifiAwareNetworkSpecifier ns =
                (WifiAwareNetworkSpecifier) nr.networkCapabilities.getNetworkSpecifier();
        ns = new WifiAwareNetworkSpecifier(
                ns.type,
                1 - ns.role, // corruption hack
                ns.clientId,
                ns.sessionId,
                ns.peerId,
                ns.peerMac,
                ns.getWifiAwareDataPathSecurityConfig().getPmk(),
                ns.getWifiAwareDataPathSecurityConfig().getPskPassphrase(),
                0,
                0);
        nr.networkCapabilities.setNetworkSpecifier(ns);
        nr.networkCapabilities.setRequestorUid(Process.myUid());
        nr.networkCapabilities.setRequestorPackageName(TEST_PACKAGE_NAME);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());

        // consequences of failure:
        //   Responder (publisher): responds with a rejection to any data-path requests
        //   Initiator (subscribe): doesn't initiate (i.e. no HAL requests)
        verifyRequestDeclaredUnfullfillable(nr);
        if (doPublish) {
            // (2) get request & respond
            mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).respondToDataPathRequest(anyShort(), eq(false),
                    eq(ndpId), eq(""), eq(null), anyBoolean(), any(), any(), eq((byte) 0));
        }
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    private void testDataPathInitiatorResponderInvalidUidUtility(boolean doPublish)
            throws Exception {
        final int clientId = 123;
        final byte pubSubId = 56;
        final int ndpId = 2;
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final int requestorId = 1341234;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, doPublish);

        // (1) create network request
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.mSessionId, res.mPeerHandle, pmk,
                null, doPublish, 0);

        // (2) corrupt request's UID
        WifiAwareNetworkSpecifier ns =
                (WifiAwareNetworkSpecifier) nr.networkCapabilities.getNetworkSpecifier();
        ns = new WifiAwareNetworkSpecifier(
                ns.type,
                ns.role,
                ns.clientId,
                ns.sessionId,
                ns.peerId,
                ns.peerMac,
                ns.getWifiAwareDataPathSecurityConfig().getPmk(),
                ns.getWifiAwareDataPathSecurityConfig().getPskPassphrase(),
                0,
                0);
        nr.networkCapabilities.setNetworkSpecifier(ns);
        nr.networkCapabilities.setRequestorUid(0 + 1); // corruption hack
        nr.networkCapabilities.setRequestorPackageName(TEST_PACKAGE_NAME);

        // (3) request network
        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());


        // consequences of failure:
        //   Responder (publisher): responds with a rejection to any data-path requests
        //   Initiator (subscribe): doesn't initiate (i.e. no HAL requests)
        verifyRequestDeclaredUnfullfillable(nr);
        if (doPublish) {
            // (2) get request & respond
            mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).respondToDataPathRequest(anyShort(), eq(false),
                    eq(ndpId), eq(""), eq(null), anyBoolean(), any(), any(), eq((byte) 0));
        }
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    private void testDataPathInitiatorResponderInvalidPackageNameUtility(boolean doPublish)
            throws Exception {
        final int clientId = 123;
        final byte pubSubId = 56;
        final int ndpId = 2;
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final int requestorId = 1341234;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, doPublish);

        // (1) create network request
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.mSessionId, res.mPeerHandle, pmk,
                null, doPublish, 0);

        // (2) corrupt request's UID
        WifiAwareNetworkSpecifier ns =
                (WifiAwareNetworkSpecifier) nr.networkCapabilities.getNetworkSpecifier();
        ns = new WifiAwareNetworkSpecifier(
                ns.type,
                ns.role,
                ns.clientId,
                ns.sessionId,
                ns.peerId,
                ns.peerMac,
                ns.getWifiAwareDataPathSecurityConfig().getPmk(),
                ns.getWifiAwareDataPathSecurityConfig().getPskPassphrase(),
                0,
                0);
        nr.networkCapabilities.setNetworkSpecifier(ns);
        nr.networkCapabilities.setRequestorUid(Process.myUid()); // corruption hack
        nr.networkCapabilities.setRequestorPackageName(TEST_PACKAGE_NAME + "h"); // corruption hack

        // (3) request network
        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock, never()).recordNdpRequestType(anyInt());

        // consequences of failure:
        //   Responder (publisher): responds with a rejection to any data-path requests
        //   Initiator (subscribe): doesn't initiate (i.e. no HAL requests)
        verifyRequestDeclaredUnfullfillable(nr);
        if (doPublish) {
            // (2) get request & respond
            mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).respondToDataPathRequest(anyShort(), eq(false),
                    eq(ndpId), eq(""), eq(null), anyBoolean(), any(), any(), eq((byte) 0));
        }
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    private void testDataPathInitiatorUtility(boolean useDirect, boolean provideMac,
            boolean providePmk, boolean providePassphrase, boolean getConfirmation,
            boolean immediateHalFailure) throws Exception {
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        String linkLocalIpv6Address = MacAddress.fromBytes(
                peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getHostAddress();

        testDataPathInitiatorUtilityMore(useDirect, provideMac, providePmk, providePassphrase,
                getConfirmation, immediateHalFailure, peerDataPathMac, null, 0, -1,
                linkLocalIpv6Address, 0);
    }

    private void testDataPathInitiatorUtilityMore(boolean useDirect, boolean provideMac,
            boolean providePmk, boolean providePassphrase, boolean getConfirmation,
            boolean immediateHalFailure, byte[] peerDataPathMac, byte[] peerToken, int port,
            int transportProtocol, String ipv6Address, int numAddrValidationRetries)
            throws Exception {
        final int clientId = 123;
        final byte pubSubId = 58;
        final int requestorId = 1341234;
        final int ndpId = 2;
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final String passphrase = "some passphrase";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources;

        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor =
                ArgumentCaptor.forClass(WifiAwareNetworkAgent.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);
        WifiAwareNetworkAgent networkAgent = null;

        if (!providePmk) {
            when(mPermissionsWrapperMock.getUidPermission(
                    eq(Manifest.permission.NETWORK_STACK), eq(Process.myUid()))).thenReturn(
                    PackageManager.PERMISSION_DENIED);
        }

        if (immediateHalFailure) {
            when(mMockNative.initiateDataPath(anyShort(), anyInt(), anyInt(), anyInt(), any(),
                    any(), anyBoolean(), any(), any(), any(), anyByte())).thenReturn(false);

        }

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, false);

        // (1) request network
        NetworkRequest nr;
        if (useDirect) {
            nr = getDirectNetworkRequest(clientId,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR,
                    provideMac ? peerDiscoveryMac : null, providePmk ? pmk : null,
                    providePassphrase ? passphrase : null, 0);
        } else {
            nr = getSessionNetworkRequest(clientId, res.mSessionId,
                    provideMac ? res.mPeerHandle : null, providePmk ? pmk : null,
                    providePassphrase ? passphrase : null, false, 0);
        }

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());
        inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(),
                eq(useDirect ? 0 : requestorId),
                eq(NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED), anyInt(), eq(peerDiscoveryMac),
                eq(sAwareInterfacePrefix + "0"),
                eq(useDirect), any(), any(),
                any(), eq(useDirect ? (byte) 0 : pubSubId));
        if (immediateHalFailure) {
            // short-circuit the rest of this test
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.INTERNAL_FAILURE),
                    eq(useDirect), anyInt(), anyLong(), anyInt());
            verifyRequestDeclaredUnfullfillable(nr);
            verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
            verifyNoMoreInteractions(mMockNative, mAwareMetricsMock);
            return;
        }

        mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId);
        mMockLooper.dispatchAll();
        if (SdkLevel.isAtLeastT()) {
            awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
            assertEquals(--expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
        }

        // (2) get confirmation OR timeout
        boolean timeout = false;
        if (getConfirmation) {
            int numConfigureAgentPropertiesFail = 0;
            if (numAddrValidationRetries > 0) {
                when(mMockNetworkInterface.isAddressUsable(any())).thenReturn(false);
                when(mMockNetworkInterface.configureAgentProperties(any(), any(), any()))
                        .thenReturn(false);
                // First retry will be ConfigureAgentProperties failure.
                numConfigureAgentPropertiesFail = 1;
            }
            when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);

            mDut.onDataPathConfirmNotification(ndpId, peerDataPathMac, true, 0, peerToken, null);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
            inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
            inOrder.verify(mMockNetworkInterface).configureAgentProperties(any(), any(), any());
            if (numAddrValidationRetries <= 0) {
                inOrder.verify(mMockNetworkInterface).isAddressUsable(any());
            }
            for (int i = 0; i < numAddrValidationRetries; ++i) {
                if (i == numConfigureAgentPropertiesFail) {
                    when(mMockNetworkInterface.configureAgentProperties(any(), any(), any()))
                            .thenReturn(true);
                }
                if (i == numAddrValidationRetries - 1) {
                    when(mMockNetworkInterface.isAddressUsable(any())).thenReturn(true);
                }
                long currentTime = (i + 1L)
                        * WifiAwareDataPathStateManager.ADDRESS_VALIDATION_RETRY_INTERVAL_MS;
                when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTime);
                mMockLooper.moveTimeForward(
                        WifiAwareDataPathStateManager.ADDRESS_VALIDATION_RETRY_INTERVAL_MS + 1);
                mMockLooper.dispatchAll();
                inOrder.verify(mMockNetworkInterface).configureAgentProperties(any(), any(), any());
                if (i < numConfigureAgentPropertiesFail) {
                    continue;
                }
                inOrder.verify(mMockNetworkInterface).isAddressUsable(any());
                if (currentTime > WifiAwareDataPathStateManager.ADDRESS_VALIDATION_TIMEOUT_MS) {
                    timeout = true;
                    break;
                }
            }
            if (timeout) {
                verifyRequestDeclaredUnfullfillable(nr);
                if (SdkLevel.isAtLeastT()) {
                    awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                    assertEquals(++expectedAvailableNdps,
                            awareResources.getAvailableDataPathsCount());
                }
                inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
                mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            } else {
                inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
                networkAgent = agentCaptor.getValue();
                inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                        eq(useDirect), anyInt(), anyLong(), anyInt(), anyInt());
                inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
                WifiAwareNetworkInfo netInfo =
                        (WifiAwareNetworkInfo) networkAgent.mDataPathCapabilities
                                .getTransportInfo();
                assertEquals(ipv6Address, netInfo.getPeerIpv6Addr().getHostAddress());
                assertEquals(port, netInfo.getPort());
                assertEquals(transportProtocol, netInfo.getTransportProtocol());
                assertEquals(1, mDut.mDataPathMgr.getNumOfNdps());
                mDut.onDataPathScheduleUpdateNotification(peerDiscoveryMac, new ArrayList<>(ndpId),
                        Collections.emptyList());
                mMockLooper.dispatchAll();
            }
        } else {
            assertTrue(mAlarmManager.dispatch(
                    WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
            mMockLooper.dispatchAll();
            verifyRequestDeclaredUnfullfillable(nr);
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(++expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
            }
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.INTERNAL_FAILURE),
                    eq(useDirect), anyInt(), anyLong(), anyInt());
        }

        // (3) end data-path (unless didn't get confirmation)
        if (getConfirmation && !timeout) {
            Message endNetworkReqMsg = Message.obtain();
            endNetworkReqMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
            endNetworkReqMsg.obj = nr;
            res.mMessenger.send(endNetworkReqMsg);

            networkAgent.onNetworkUnwanted();
            mMockLooper.dispatchAll();
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(++expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
            }
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mDut.onDataPathEndNotification(ndpId);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNetdWrapper).setInterfaceDown(anyString());
            inOrderM.verify(mAwareMetricsMock).recordNdpSessionDuration(anyLong());
        }
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper,
                mMockNetworkInterface);
    }

    private void testDataPathResponderUtility(boolean useDirect, boolean provideMac,
            boolean providePmk, boolean providePassphrase, boolean getConfirmation)
            throws Exception {
        final int clientId = 123;
        final byte pubSubId = 60;
        final int requestorId = 1341234;
        final int ndpId = 2;
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final String passphrase = "some passphrase";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        int expectedAvailableNdps = MAX_NDP_SESSION;
        AwareResources awareResources;

        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor =
                ArgumentCaptor.forClass(WifiAwareNetworkAgent.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        if (providePmk) {
            when(mPermissionsWrapperMock.getUidPermission(
                    eq(Manifest.permission.NETWORK_STACK), eq(Process.myUid()))).thenReturn(
                    PackageManager.PERMISSION_GRANTED);
        }

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, true);

        // (1) request network
        NetworkRequest nr;
        if (useDirect) {
            nr = getDirectNetworkRequest(clientId,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER,
                    provideMac ? peerDiscoveryMac : null, providePmk ? pmk : null,
                    providePassphrase ? passphrase : null, 0);
        } else {
            nr = getSessionNetworkRequest(clientId, res.mSessionId,
                    provideMac ? res.mPeerHandle : null, providePmk ? pmk : null,
                    providePassphrase ? passphrase : null, true, 0);
        }

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());

        // (2) get request & respond (if legacy)
        mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).respondToDataPathRequest(transactionId.capture(), eq(true),
                eq(ndpId), eq(sAwareInterfacePrefix + "0"),
                eq(null), eq(useDirect), any(), any(), eq(useDirect ? (byte) 0 : pubSubId));
        mDut.onRespondToDataPathSetupRequestResponse(transactionId.getValue(), true, 0);
        mMockLooper.dispatchAll();
        assertTrue(mAlarmManager
                .isPending(WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
        if (SdkLevel.isAtLeastT()) {
            awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
            assertEquals(--expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
        }

        // (3) get confirmation OR timeout
        if (getConfirmation) {
            mDut.onDataPathConfirmNotification(ndpId, peerDataPathMac, true, 0, null, null);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
            inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
            inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                    eq(useDirect), anyInt(), anyLong(), anyInt(), anyInt());
            inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
            WifiAwareNetworkInfo netInfo =
                    (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                            .getTransportInfo();
            assertArrayEquals(MacAddress.fromBytes(
                    peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                    netInfo.getPeerIpv6Addr().getAddress());
            assertEquals(0, netInfo.getPort());
            assertEquals(-1, netInfo.getTransportProtocol());
            assertEquals(1, mDut.mDataPathMgr.getNumOfNdps());
        } else {
            assertTrue(mAlarmManager.dispatch(
                    WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
            mMockLooper.dispatchAll();
            verifyRequestDeclaredUnfullfillable(nr);
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(++expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
            }
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.INTERNAL_FAILURE),
                    eq(useDirect), anyInt(), anyLong(), anyInt());
        }

        // (4) end data-path (unless didn't get confirmation)
        if (getConfirmation) {
            Message endNetworkMsg = Message.obtain();
            endNetworkMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
            endNetworkMsg.obj = nr;
            res.mMessenger.send(endNetworkMsg);

            agentCaptor.getValue().onNetworkUnwanted();
            mMockLooper.dispatchAll();
            if (SdkLevel.isAtLeastT()) {
                awareResources = validateCorrectAwareResourcesChangeBroadcast(inOrder);
                assertEquals(++expectedAvailableNdps, awareResources.getAvailableDataPathsCount());
            }
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));

            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mDut.onDataPathEndNotification(ndpId);
            mMockLooper.dispatchAll();

            inOrder.verify(mMockNetdWrapper).setInterfaceDown(anyString());
            inOrderM.verify(mAwareMetricsMock).recordNdpSessionDuration(anyLong());
        }
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    private NetworkRequest getSessionNetworkRequest(int clientId, int sessionId,
            PeerHandle peerHandle, byte[] pmk, String passphrase, boolean doPublish, int requestId)
            throws Exception {
        return getSessionNetworkRequestMore(clientId, sessionId, peerHandle, pmk, passphrase,
                doPublish, requestId, 0, -1);
    }

    private NetworkRequest getSessionNetworkRequestMore(int clientId, int sessionId,
            PeerHandle peerHandle, byte[] pmk, String passphrase, boolean doPublish, int requestId,
            int port, int transportProtocol)
            throws Exception {
        final IWifiAwareManager mockAwareService = mock(IWifiAwareManager.class);
        final WifiAwareManager mgr = new WifiAwareManager(mMockContext, mockAwareService);
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();
        final PublishConfig publishConfig = new PublishConfig.Builder().build();
        final SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        ArgumentCaptor<WifiAwareSession> sessionCaptor = ArgumentCaptor.forClass(
                WifiAwareSession.class);
        ArgumentCaptor<IWifiAwareEventCallback> clientProxyCallback = ArgumentCaptor
                .forClass(IWifiAwareEventCallback.class);
        ArgumentCaptor<IWifiAwareDiscoverySessionCallback> sessionProxyCallback = ArgumentCaptor
                .forClass(IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<DiscoverySession> discoverySession = ArgumentCaptor
                .forClass(DiscoverySession.class);

        AttachCallback mockCallback = mock(AttachCallback.class);
        DiscoverySessionCallback mockSessionCallback = mock(
                DiscoverySessionCallback.class);

        InOrder inOrderS = inOrder(mockAwareService, mockCallback, mockSessionCallback);

        mgr.attach(mMockLooperHandler, configRequest, mockCallback, null, false, null);
        inOrderS.verify(mockAwareService).connect(any(), any(), any(),
                clientProxyCallback.capture(), eq(configRequest), eq(false), any(), eq(false));
        IWifiAwareEventCallback iwaec = clientProxyCallback.getValue();
        iwaec.onConnectSuccess(clientId);
        mMockLooper.dispatchAll();
        inOrderS.verify(mockCallback).onAttached(sessionCaptor.capture());
        if (doPublish) {
            sessionCaptor.getValue().publish(publishConfig, mockSessionCallback,
                    mMockLooperHandler);
            inOrderS.verify(mockAwareService).publish(any(), any(), eq(clientId), eq(publishConfig),
                    sessionProxyCallback.capture(), any());
        } else {
            sessionCaptor.getValue().subscribe(subscribeConfig, mockSessionCallback,
                    mMockLooperHandler);
            inOrderS.verify(mockAwareService).subscribe(any(), any(), eq(clientId),
                    eq(subscribeConfig), sessionProxyCallback.capture(), any());
        }
        sessionProxyCallback.getValue().onSessionStarted(sessionId);
        mMockLooper.dispatchAll();
        if (doPublish) {
            inOrderS.verify(mockSessionCallback).onPublishStarted(
                    (PublishDiscoverySession) discoverySession.capture());
        } else {
            inOrderS.verify(mockSessionCallback).onSubscribeStarted(
                    (SubscribeDiscoverySession) discoverySession.capture());
        }

        NetworkSpecifier ns;
        if (pmk == null && passphrase == null) {
            ns = createNetworkSpecifier(clientId,
                    doPublish ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER
                            : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, sessionId,
                    peerHandle, null, null, port, transportProtocol);
        } else if (passphrase == null) {
            ns = createNetworkSpecifier(clientId,
                    doPublish ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER
                            : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, sessionId,
                    peerHandle, pmk, null, port, transportProtocol);
        } else {
            ns = createNetworkSpecifier(clientId,
                    doPublish ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER
                            : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, sessionId,
                    peerHandle, null, passphrase, port, transportProtocol);
        }

        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll();
        nc.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        nc.setNetworkSpecifier(ns);
        nc.setLinkUpstreamBandwidthKbps(1);
        nc.setLinkDownstreamBandwidthKbps(1);
        nc.setSignalStrength(1);
        nc.setRequestorUid(Process.myUid());
        nc.setRequestorPackageName(TEST_PACKAGE_NAME);

        return new NetworkRequest(nc, 0, requestId, NetworkRequest.Type.REQUEST);
    }

    private NetworkRequest getDirectNetworkRequest(int clientId, int role, byte[] peer,
            byte[] pmk, String passphrase, int requestId) throws Exception {
        return getDirectNetworkRequestMore(clientId, role, peer, pmk, passphrase, requestId, 0, -1);
    }

    private NetworkRequest getDirectNetworkRequestMore(int clientId, int role, byte[] peer,
            byte[] pmk, String passphrase, int requestId, int port, int transportProtocol)
            throws Exception {
        final IWifiAwareManager mockAwareService = mock(IWifiAwareManager.class);
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();
        final WifiAwareManager mgr = new WifiAwareManager(mMockContext, mockAwareService);

        ArgumentCaptor<WifiAwareSession> sessionCaptor = ArgumentCaptor.forClass(
                WifiAwareSession.class);
        ArgumentCaptor<IWifiAwareEventCallback> clientProxyCallback = ArgumentCaptor
                .forClass(IWifiAwareEventCallback.class);

        AttachCallback mockCallback = mock(AttachCallback.class);

        mgr.attach(mMockLooperHandler, configRequest, mockCallback, null, false, null);
        verify(mockAwareService).connect(any(), any(), any(),
                clientProxyCallback.capture(), eq(configRequest), eq(false), any(), eq(false));
        clientProxyCallback.getValue().onConnectSuccess(clientId);
        mMockLooper.dispatchAll();
        verify(mockCallback).onAttached(sessionCaptor.capture());

        NetworkSpecifier ns;
        if (pmk == null && passphrase == null) {
            ns = createNetworkSpecifier(clientId, role, peer, null, null, port, transportProtocol);
        } else if (passphrase == null) {
            ns = createNetworkSpecifier(clientId, role, peer, pmk, null, port, transportProtocol);
        } else {
            ns = createNetworkSpecifier(clientId, role, peer, null, passphrase, port,
                    transportProtocol);
        }
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll();
        nc.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
        nc.setNetworkSpecifier(ns);
        nc.setLinkUpstreamBandwidthKbps(1);
        nc.setLinkDownstreamBandwidthKbps(1);
        nc.setSignalStrength(1);
        nc.setRequestorUid(Process.myUid());
        nc.setRequestorPackageName(TEST_PACKAGE_NAME);

        return new NetworkRequest(nc, 0, requestId, NetworkRequest.Type.REQUEST);
    }

    private DataPathEndPointInfo initDataPathEndPoint(boolean isFirstIteration, int clientId,
            byte pubSubId, int requestorId, byte[] peerDiscoveryMac, InOrder inOrder,
            InOrder inOrderM, boolean doPublish)
            throws Exception {
        final String someMsg = "some arbitrary message from peer";
        final PublishConfig publishConfig = new PublishConfig.Builder().build();
        final SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);

        Messenger messenger = initOobDataPathEndPoint(isFirstIteration, 1, clientId, inOrder,
                inOrderM);

        if (doPublish) {
            mDut.publish(clientId, publishConfig, mMockSessionCallback);
        } else {
            mDut.subscribe(clientId, subscribeConfig, mMockSessionCallback);
        }
        mMockLooper.dispatchAll();
        if (doPublish) {
            inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                    eq(publishConfig), isNull());
        } else {
            inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                    eq(subscribeConfig), isNull());
        }
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), doPublish, pubSubId);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(Process.myUid()), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(Process.myUid(),
                NanStatusCode.SUCCESS, doPublish, sessionId.getValue(), 6, TEST_FEATURE_ID);

        mDut.onMessageReceivedNotification(pubSubId, requestorId, peerDiscoveryMac,
                someMsg.getBytes());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockSessionCallback).onMessageReceived(peerIdCaptor.capture(),
                eq(someMsg.getBytes()));

        return new DataPathEndPointInfo(sessionId.getValue(), peerIdCaptor.getValue(),
                isFirstIteration ? messenger : null);
    }

    private Messenger initOobDataPathEndPoint(boolean startUpSequence,
            int maxNdiInterfaces, int clientId, InOrder inOrder, InOrder inOrderM)
            throws Exception {
        final int pid = 2000;
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Messenger> messengerCaptor = ArgumentCaptor.forClass(Messenger.class);
        ArgumentCaptor<NetworkProvider> networkProviderCaptor =
                ArgumentCaptor.forClass(NetworkProvider.class);
        ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);

        Capabilities capabilities = new Capabilities();
        capabilities.maxNdiInterfaces = maxNdiInterfaces;
        capabilities.supportedDataPathCipherSuites = WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
        capabilities.maxNdpSessions = MAX_NDP_SESSION;

        if (startUpSequence) {
            // (0) start/registrations
            inOrder.verify(mMockCm).registerNetworkProvider(networkProviderCaptor.capture());
            collector.checkThat("factory name", "WIFI_AWARE_FACTORY",
                    equalTo(networkProviderCaptor.getValue().getName()));

            // (1) get capabilities
            mDut.queryCapabilities();
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
            mDut.onCapabilitiesUpdateResponse(transactionId.getValue(), capabilities);
            mMockLooper.dispatchAll();

            // (2) enable usage
            mDut.enableUsage();
            mMockLooper.dispatchAll();
            inOrderM.verify(mAwareMetricsMock).recordEnableUsage();
        }

        // (3) create client
        mDut.connect(clientId, Process.myUid(), pid, TEST_PACKAGE_NAME, TEST_FEATURE_ID,
                mMockCallback, configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();

        if (startUpSequence) {
            inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                    eq(configRequest), eq(false), eq(true), eq(true),
                    eq(false), eq(false), eq(false), anyInt(), anyInt());
            mDut.onConfigSuccessResponse(transactionId.getValue());
            mMockLooper.dispatchAll();
        }

        inOrder.verify(mMockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(Process.myUid()), eq(false),
                any(),  eq(6), eq(TEST_FEATURE_ID));

        if (startUpSequence) {
            for (int i = 0; i < maxNdiInterfaces; ++i) {
                inOrder.verify(mMockNative).createAwareNetworkInterface(transactionId.capture(),
                        strCaptor.capture());
                collector.checkThat("interface created -- " + i, sAwareInterfacePrefix + i,
                        equalTo(strCaptor.getValue()));
                mDut.onCreateDataPathInterfaceResponse(transactionId.getValue(), true, 0);
                mMockLooper.dispatchAll();
            }
            Messenger messenger = networkProviderCaptor.getValue().getMessenger();
            return messenger;
        }

        return null;
    }

    /**
     * Copy of DiscoverySession.createNetworkSpecifier - but without any checks! Allows creating
     * network requests which may not be valid (e.g. for the API level).
     */
    public NetworkSpecifier createNetworkSpecifier(int clientId, int role, int sessionId,
            PeerHandle peerHandle, byte[] pmk, String passphrase, int port, int transportProtocol) {
        return new WifiAwareNetworkSpecifier(
                (peerHandle == null) ? WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB_ANY_PEER
                        : WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB,
                role,
                clientId,
                sessionId,
                peerHandle != null ? peerHandle.peerId : 0, // 0 is an invalid peer ID
                null, // peerMac (not used in this method)
                pmk,
                passphrase,
                port,
                transportProtocol);
    }

    /**
     * Copy of WifiAwareSession.createNetworkSpecifier - but without any checks! Allows creating
     * network requests which may not be valid (e.g. for the API level).
     */
    private NetworkSpecifier createNetworkSpecifier(int clientId, int role, byte[] peer, byte[] pmk,
            String passphrase, int port, int transportProtocol) {
        return new WifiAwareNetworkSpecifier(
                (peer == null) ? WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB_ANY_PEER
                        : WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB,
                role,
                clientId,
                0, // 0 is an invalid session ID
                0, // 0 is an invalid peer ID
                peer,
                pmk,
                passphrase,
                port,
                transportProtocol);
    }

    private static class DataPathEndPointInfo {
        int mSessionId;
        PeerHandle mPeerHandle;
        Messenger mMessenger;

        DataPathEndPointInfo(int sessionId, int peerId, Messenger messenger) {
            mSessionId = sessionId;
            mPeerHandle = new PeerHandle(peerId);
            mMessenger = messenger;
        }
    }

    /**
     * Verify that declareNetworkRequestUnfulfillable was called.
     */
    private void verifyRequestDeclaredUnfullfillable(NetworkRequest request) throws Exception {
        mMockLooper.dispatchAll();
        verify(mMockCm, atLeastOnce()).declareNetworkRequestUnfulfillable(any());
    }

    // copy of the method in WifiAwareDataPathStateManager - but without error checking (so we can
    // construct invalid TLVs for testing).
    private static byte[] buildTlv(int port, int transportProtocol, boolean includeGarbageTlv) {
        if (port == 0 && transportProtocol == -1) {
            return null;
        }

        TlvBufferUtils.TlvConstructor tlvc = new TlvBufferUtils.TlvConstructor(1, 2);
        tlvc.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        tlvc.allocate(30); // safe size for now

        tlvc.putRawByteArray(WifiAwareDataPathStateManager.NetworkInformationData.WFA_OUI);
        tlvc.putRawByte((byte) WifiAwareDataPathStateManager.NetworkInformationData
                .GENERIC_SERVICE_PROTOCOL_TYPE);

        if (port != 0) {
            tlvc.putShort(WifiAwareDataPathStateManager.NetworkInformationData.SUB_TYPE_PORT,
                    (short) port);
        }
        if (transportProtocol != -1) {
            tlvc.putByte(WifiAwareDataPathStateManager.NetworkInformationData
                    .SUB_TYPE_TRANSPORT_PROTOCOL, (byte) transportProtocol);
        }
        if (includeGarbageTlv) {
            tlvc.putShort(55, (short) -1298);
        }

        byte[] subTypes = tlvc.getArray();

        tlvc.allocate(30);
        tlvc.putByteArray(WifiAwareDataPathStateManager.NetworkInformationData.SERVICE_INFO_TYPE,
                subTypes);
        if (includeGarbageTlv) {
            tlvc.putInt(78, 44);
        }

        return tlvc.getArray();
    }

    @Test
    public void testAcceptRequestWhenAwareNotReadyWillReleaseRequest() throws Exception {
        final int clientId = 123;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        ArgumentCaptor<NetworkProvider> networkProviderCaptor =
                ArgumentCaptor.forClass(NetworkProvider.class);
        verify(mMockCm).registerNetworkProvider(networkProviderCaptor.capture());
        NetworkProvider awareProvider = networkProviderCaptor.getValue();
        collector.checkThat("factory name", "WIFI_AWARE_FACTORY",
                equalTo(awareProvider.getName()));
        NetworkRequest networkRequest = getDirectNetworkRequest(clientId,
                WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerDiscoveryMac, null,
                null, 1);
        // Aware usage is not enabled, should declare unfullfillable.
        awareProvider.onNetworkRequested(networkRequest, 0, awareProvider.getProviderId());
        verifyRequestDeclaredUnfullfillable(networkRequest);
        reset(mMockCm);
        // Aware usage is enabled but interface not ready, should declare unfullfillable.
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        awareProvider.onNetworkRequested(networkRequest, 0, awareProvider.getProviderId());
        verifyRequestDeclaredUnfullfillable(networkRequest);
    }
    private void testDataPathAcceptsAnyResponderWithMultipleInitiator(boolean providePmk,
            boolean providePassphrase, boolean failureOnFirst)
            throws Exception {
        final int clientId = 123;
        final byte pubSubId = 60;
        final int requestorId = 1341234;
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final String passphrase = "some passphrase";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        int indexOfFailure = failureOnFirst ? 0 : 1;
        int ndpAttemptsCount = 4;
        int ndpId = 2;
        List<Integer> successNdpIds = new ArrayList<>();


        ArgumentCaptor<WifiAwareNetworkAgent> agentCaptor =
                ArgumentCaptor.forClass(WifiAwareNetworkAgent.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mMockCallback, mMockSessionCallback,
                mMockNetdWrapper, mMockNetworkInterface, mMockCm);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        if (providePmk) {
            when(mPermissionsWrapperMock.getUidPermission(
                    eq(Manifest.permission.NETWORK_STACK), eq(Process.myUid()))).thenReturn(
                    PackageManager.PERMISSION_GRANTED);
        }

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, true);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.mSessionId,
                null, providePmk ? pmk : null,
                providePassphrase ? passphrase : null, true, 0);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB_ANY_PEER);
        boolean firstSuccess = true;

        for (int i = 0; i < ndpAttemptsCount; i++) {
            // (2) get request & respond
            peerDataPathMac[5] += i;
            mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).respondToDataPathRequest(transactionId.capture(), eq(true),
                    eq(ndpId), eq(sAwareInterfacePrefix + "0"),
                    eq(null), eq(false), any(), any(), eq(pubSubId));
            mDut.onRespondToDataPathSetupRequestResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
            assertTrue(mAlarmManager
                    .isPending(WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));

            // (3) get confirmation OR timeout
            if (i != indexOfFailure) {
                successNdpIds.add(ndpId);
                mDut.onDataPathConfirmNotification(ndpId, peerDataPathMac, true, 0, null, null);
                mMockLooper.dispatchAll();
                if (firstSuccess) {
                    inOrder.verify(mMockNetdWrapper).setInterfaceUp(anyString());
                    inOrder.verify(mMockNetdWrapper).enableIpv6(anyString());
                    inOrder.verify(mMockNetworkInterface).setConnected(agentCaptor.capture());
                    WifiAwareNetworkInfo netInfo =
                            (WifiAwareNetworkInfo) agentCaptor.getValue().mDataPathCapabilities
                                    .getTransportInfo();
                    assertArrayEquals(MacAddress.fromBytes(
                            peerDataPathMac).getLinkLocalIpv6FromEui48Mac().getAddress(),
                            netInfo.getPeerIpv6Addr().getAddress());
                    assertEquals(0, netInfo.getPort());
                    assertEquals(-1, netInfo.getTransportProtocol());
                    assertEquals(1, mDut.mDataPathMgr.getNumOfNdps());
                }
                inOrderM.verify(mAwareMetricsMock).recordNdpStatus(eq(NanStatusCode.SUCCESS),
                        eq(false), anyInt(), anyLong(), anyInt(), anyInt());
                inOrderM.verify(mAwareMetricsMock).recordNdpCreation(anyInt(), any(), any());
                assertEquals(successNdpIds.size(), mDut.mDataPathMgr.getNumOfNdps());

                firstSuccess = false;
            } else {
                assertTrue(mAlarmManager.dispatch(
                        WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
                mMockLooper.dispatchAll();
                inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
                inOrderM.verify(mAwareMetricsMock).recordNdpStatus(
                        eq(NanStatusCode.INTERNAL_FAILURE), eq(false), anyInt(), anyLong(),
                        anyInt());
                mDut.onDataPathEndNotification(ndpId);
                mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
                verify(mMockCm, never()).declareNetworkRequestUnfulfillable(any());
            }
            ndpId++;
        }

        // (4) one of the NDP is terminated by the other side
        int endNdpId = successNdpIds.remove(0);
        mDut.onDataPathEndNotification(endNdpId);
        mMockLooper.dispatchAll();

        inOrderM.verify(mAwareMetricsMock).recordNdpSessionDuration(anyLong());
        assertEquals(successNdpIds.size(), mDut.mDataPathMgr.getNumOfNdps());


        // (5) end data-path (unless didn't get confirmation)
        Message endNetworkMsg = Message.obtain();
        endNetworkMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
        endNetworkMsg.obj = nr;
        res.mMessenger.send(endNetworkMsg);
        agentCaptor.getValue().onNetworkUnwanted();

        for (int successNdpId : successNdpIds) {
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mDut.onDataPathEndNotification(successNdpId);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(successNdpId));
            inOrderM.verify(mAwareMetricsMock).recordNdpSessionDuration(anyLong());
        }
        inOrder.verify(mMockNetdWrapper).setInterfaceDown(anyString());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    @Test
    public void testAcceptAnyResponderWithMultipleInitiatorRequestWithTimeOutAtFirstRequest()
            throws Exception {
        testDataPathAcceptsAnyResponderWithMultipleInitiator(false, true, true);
    }

    @Test
    public void testAcceptAnyResponderWithMultipleInitiatorRequestWithTimeOutAtFollowingRequest()
            throws Exception {
        testDataPathAcceptsAnyResponderWithMultipleInitiator(false, true, false);
    }

    /**
     * Validate when multiple request present on a device, request from peer can match to the right
     * accepts any peer request when no peer specific request matches.
     */
    @Test
    public void testAcceptsAnyRequestMatchesCorrectlyWhenMultipleRequestPresent() throws Exception {
        final int clientId = 123;
        final byte pubId = 1;
        final byte subId = -128;
        final int requestorId = 1341234;
        final String passphrase = "SomeSecurePassword";
        final String passphrase1 = "SomeSecurePassword1";
        final int ndpId = 1;
        final int ndpId2 = 2;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> interfaceName1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> interfaceName2 = ArgumentCaptor.forClass(String.class);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        Messenger messenger = initOobDataPathEndPoint(true, 2, clientId, inOrder, inOrderM);

        // (0) initialize Publish
        DataPathEndPointInfo pubRes = initDataPathEndPoint(false, clientId, pubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, true);

        // (1) request responder network
        NetworkRequest pubNr = getSessionNetworkRequest(clientId, pubRes.mSessionId, null,
                null, passphrase, true, requestorId);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = pubNr;
        reqNetworkMsg.arg1 = 0;
        messenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());

        // (2) initialize Subscribe
        DataPathEndPointInfo subRes = initDataPathEndPoint(false, clientId + 1, subId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, false);

        // (3) request initiator network
        NetworkRequest subNr = getSessionNetworkRequest(clientId + 1, subRes.mSessionId,
                subRes.mPeerHandle, null, passphrase1, false, requestorId);

        Message subReqNetworkMsg = Message.obtain();
        subReqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        subReqNetworkMsg.obj = subNr;
        subReqNetworkMsg.arg1 = 0;
        messenger.send(subReqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());

        // (4) Initiator request succeed
        verify(mMockNative).initiateDataPath(transactionId.capture(), anyInt(), anyInt(), anyInt(),
                any(), interfaceName1.capture(), anyBoolean(), any(), any(),
                any(), eq(subId));
        mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId);

        // (5) provide a request from peer
        mDut.onDataPathRequestNotification(pubId, peerDiscoveryMac, ndpId2, null);
        mMockLooper.dispatchAll();

        // (6) make sure framework respond with the right accepts any peer request.
        verify(mMockNative).respondToDataPathRequest(anyShort(), eq(true), eq(ndpId2),
                interfaceName2.capture(), any(), anyBoolean(), any(),
                any(), eq(pubId));

        assertNotEquals(interfaceName1.getValue(), interfaceName2.getValue());
    }

    /**
     * Validate when both peer specific and accepts any peer requests are on the device, framework
     * will response to the matched peer with peer specific request. Other peers with accepts any
     * request.
     */
    @Test
    public void testPeerSpecificRequestMatchesCorrectlyWhenAcceptsAnyRequestExist()
            throws Exception {
        final int clientId = 123;
        final byte pubId = 1;
        final int requestorId = 1341234;
        final String passphrase = "SomeSecurePassword";
        final String passphrase1 = "SomeSecurePassword1";
        final int ndpId = 1;
        final int ndpId2 = 2;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDiscoveryMac1 = HexEncoding.decode("000102030406".toCharArray(), false);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> interfaceName1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> interfaceName2 = ArgumentCaptor.forClass(String.class);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        Messenger messenger = initOobDataPathEndPoint(true, 2, clientId, inOrder, inOrderM);

        // (0) initialize Publish
        DataPathEndPointInfo pubRes = initDataPathEndPoint(false, clientId, pubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, true);

        // (1) request accepts any responder network
        NetworkRequest pubNr = getSessionNetworkRequest(clientId, pubRes.mSessionId, null,
                null, passphrase, true, requestorId);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = pubNr;
        reqNetworkMsg.arg1 = 0;
        messenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());

        // (2) request peer specific responder network
        NetworkRequest subNr = getSessionNetworkRequest(clientId, pubRes.mSessionId,
                pubRes.mPeerHandle, null, passphrase1, true, requestorId);

        Message subReqNetworkMsg = Message.obtain();
        subReqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        subReqNetworkMsg.obj = subNr;
        subReqNetworkMsg.arg1 = 0;
        messenger.send(subReqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(anyInt());

        // (3) provide a request from specified peer
        mDut.onDataPathRequestNotification(pubId, peerDiscoveryMac, ndpId, null);
        mMockLooper.dispatchAll();

        // (4) make sure framework respond with the peer specific request.
        verify(mMockNative).respondToDataPathRequest(transactionId.capture(), eq(true), eq(ndpId),
                interfaceName1.capture(), any(), anyBoolean(), any(),
                any(), eq(pubId));
        mDut.onRespondToDataPathSetupRequestResponse(transactionId.getValue(), true, 0);
        mMockLooper.dispatchAll();
        assertTrue(mAlarmManager
                .isPending(WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));

        // (5) provide a request from a not specified peer.
        mDut.onDataPathRequestNotification(pubId, peerDiscoveryMac1, ndpId2, null);
        mMockLooper.dispatchAll();

        // (6) make sure framework respond with the right accepts any peer request.
        verify(mMockNative).respondToDataPathRequest(anyShort(), eq(true), eq(ndpId2),
                interfaceName2.capture(), any(), anyBoolean(), any(),
                any(), eq(pubId));

        assertNotEquals(interfaceName1.getValue(), interfaceName2.getValue());
    }

    @Test
    public void testResponseFailure() throws Exception {
        final int clientId = 123;
        final byte pubSubId = 55;
        final int requestorId = 1341234;
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);
        final int ndpId = 3;

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);

        // (0) initialize
        DataPathEndPointInfo res = initDataPathEndPoint(true, clientId, pubSubId, requestorId,
                peerDiscoveryMac, inOrder, inOrderM, true);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.mSessionId, res.mPeerHandle,
                null, null, true, 0);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkProvider.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.mMessenger.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordNdpRequestType(
                WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB);

        // (2) have responder receive request
        mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId, null);
        mMockLooper.dispatchAll();

        // (3) verify that responder accept
        verify(mMockNative).respondToDataPathRequest(transactionId.capture(), eq(true), eq(ndpId),
                anyString(), any(), anyBoolean(), any(),
                any(), eq(pubSubId));
        // (4) response failure
        mDut.onRespondToDataPathSetupRequestResponse(transactionId.getValue(), false, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).endDataPath(anyShort(), eq(ndpId));
        verifyRequestDeclaredUnfullfillable(nr);
        inOrderM.verify(mAwareMetricsMock).recordNdpStatus(anyInt(), anyBoolean(), anyInt(),
                anyLong(), anyInt());
        assertFalse(mAlarmManager
                .isPending(WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        // failure if there's further activity
        verifyNoMoreInteractions(mMockNative, mAwareMetricsMock, mMockNetdWrapper);
    }

    /**
     * Validates that the broadcast sent on Aware status change is correct.
     */
    private AwareResources validateCorrectAwareResourcesChangeBroadcast(InOrder inOrder) {
        if (!SdkLevel.isAtLeastT()) {
            return null;
        }
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);

        inOrder.verify(mMockContext, atLeastOnce()).sendBroadcastAsUser(captor.capture(),
                eq(UserHandle.ALL), anyString());
        Intent intent = captor.getValue();
        collector.checkThat("intent action", intent.getAction(),
                equalTo(WifiAwareManager.ACTION_WIFI_AWARE_RESOURCE_CHANGED));
        return intent.getParcelableExtra(WifiAwareManager.EXTRA_AWARE_RESOURCES);
    }
}
