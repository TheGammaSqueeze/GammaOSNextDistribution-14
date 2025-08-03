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

import static android.hardware.wifi.V1_0.NanRangingIndication.EGRESS_MET_MASK;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_WIFI_AWARE;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;

import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_CAPABILITIES;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.app.StatsManager;
import android.app.test.MockAnswerUtil;
import android.app.test.TestAlarmManager;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.IBooleanListener;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.AwareResources;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.MacAddrMapping;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.util.HexEncoding;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.Clock;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.InterfaceConflictManager;
import com.android.server.wifi.MockResources;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.hal.WifiNanIface.NanRangingIndication;
import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.WaitingState;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


/**
 * Unit test harness for WifiAwareStateManager.
 */
@SmallTest
public class WifiAwareStateManagerTest extends WifiBaseTest {
    private TestLooper mMockLooper;
    private Random mRandomNg = new Random(15687);
    private WifiAwareStateManager mDut;
    @Mock private WifiAwareNativeManager mMockNativeManager;
    @Spy private TestUtils.MonitoredWifiAwareNativeApi mMockNative =
            new TestUtils.MonitoredWifiAwareNativeApi();
    @Mock private Context mMockContext;
    @Mock private AppOpsManager mMockAppOpsManager;
    @Mock private WifiAwareMetrics mAwareMetricsMock;
    @Mock private WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock private WifiPermissionsWrapper mPermissionsWrapperMock;
    @Mock private InterfaceConflictManager mInterfaceConflictManager;
    TestAlarmManager mAlarmManager;
    @Mock private PowerManager mMockPowerManager;
    @Mock private WifiManager mMockWifiManager;
    @Mock private WifiNative mWifiNative;
    private BroadcastReceiver mPowerBcastReceiver;
    private BroadcastReceiver mLocationModeReceiver;
    private BroadcastReceiver mWifiStateChangedReceiver;
    @Mock private WifiAwareDataPathStateManager mMockAwareDataPathStatemanager;
    @Mock private WifiInjector mWifiInjector;
    @Mock private PairingConfigManager mPairingConfigManager;
    @Mock private StatsManager mStatsManager;

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    private static final byte[] ALL_ZERO_MAC = new byte[] {0, 0, 0, 0, 0, 0};
    private MockResources mResources;
    private Bundle mExtras = new Bundle();
    private WifiManager.ActiveCountryCodeChangedCallback mActiveCountryCodeChangedCallback;
    private HandlerThread mWifiHandlerThread;
    private byte[] mNik = "0123456789012345".getBytes();
    private byte[] mPeerNik = "6789012345678901".getBytes();
    private byte[] mPmk = "01234567890123456789012345678901".getBytes();
    private StaticMockitoSession mSession;

    @Captor
    ArgumentCaptor<StatsManager.StatsPullAtomCallback> mPullAtomCallbackArgumentCaptor;

    /**
     * Pre-test configuration. Initialize and install mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiStatsLog.class)
                .startMocking();

        mAlarmManager = new TestAlarmManager();
        when(mMockContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());
        when(mMockContext.getSystemService(StatsManager.class)).thenReturn(mStatsManager);

        when(mMockContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mMockWifiManager);
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mMockLooper = new TestLooper();

        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(
                mock(ConnectivityManager.class));
        when(mMockContext.getSystemService(ConnectivityManager.class)).thenReturn(
                mock(ConnectivityManager.class));
        when(mMockContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mMockAppOpsManager);
        when(mMockContext.getSystemServiceName(PowerManager.class)).thenReturn(
                Context.POWER_SERVICE);
        when(mMockContext.getSystemService(PowerManager.class)).thenReturn(mMockPowerManager);
        when(mMockContext.checkPermission(eq(android.Manifest.permission.ACCESS_FINE_LOCATION),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockAppOpsManager.noteOp(eq(AppOpsManager.OP_FINE_LOCATION), anyInt(),
                any())).thenReturn(AppOpsManager.MODE_ERRORED);
        when(mMockPowerManager.isDeviceIdleMode()).thenReturn(false);
        when(mMockPowerManager.isInteractive()).thenReturn(true);
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(true);
        when(mWifiPermissionsUtil.getWifiCallerType(anyInt(), anyString())).thenReturn(6);
        if (SdkLevel.isAtLeastS()) {
            when(mWifiPermissionsUtil.getWifiCallerType(any())).thenReturn(6);
        }
        mResources = new MockResources();
        mResources.setInteger(R.integer.config_wifiAwareInstantCommunicationModeDurationMillis,
                30000);
        mResources.setInteger(R.integer.config_wifiConfigurationWifiRunnerThresholdInMs, 4000);
        when(mMockContext.getResources()).thenReturn(mResources);

        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_NAN), any(), anyBoolean()))
                .thenReturn(InterfaceConflictManager.ICM_EXECUTE_COMMAND);

        ArgumentCaptor<BroadcastReceiver> bcastRxCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        mWifiHandlerThread = new HandlerThread("WifiHandlerThread");
        mWifiHandlerThread.start();
        Looper wifiLooper = mWifiHandlerThread.getLooper();
        Handler wifiHandler = new Handler(wifiLooper);
        WifiThreadRunner wifiThreadRunner = new WifiThreadRunner(wifiHandler);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getWifiThreadRunner()).thenReturn(wifiThreadRunner);
        mDut = new WifiAwareStateManager(mWifiInjector, mPairingConfigManager);
        mDut.setNative(mMockNativeManager, mMockNative);
        mDut.start(mMockContext, mMockLooper.getLooper(), mAwareMetricsMock,
                mWifiPermissionsUtil, mPermissionsWrapperMock, new Clock(),
                mock(NetdWrapper.class), mInterfaceConflictManager);
        mDut.startLate();
        mDut.enableVerboseLogging(true, true, true);
        mMockLooper.dispatchAll();
        ArgumentCaptor<WifiManager.ActiveCountryCodeChangedCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(WifiManager.ActiveCountryCodeChangedCallback.class);
        if (SdkLevel.isAtLeastT()) {
            verify(mMockWifiManager).registerActiveCountryCodeChangedCallback(any(),
                    callbackArgumentCaptor.capture());
            mActiveCountryCodeChangedCallback = callbackArgumentCaptor.getValue();
        }
        verify(mMockContext, times(3))
                .registerReceiver(
                        bcastRxCaptor.capture(),
                        any(IntentFilter.class),
                        isNull(),
                        any(Handler.class));
        mPowerBcastReceiver = bcastRxCaptor.getAllValues().get(0);
        mLocationModeReceiver = bcastRxCaptor.getAllValues().get(1);
        mWifiStateChangedReceiver = bcastRxCaptor.getAllValues().get(2);
        installMocksInStateManager(mDut, mMockAwareDataPathStatemanager);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        mDut.tryToGetAwareCapability();
        mMockLooper.dispatchAll();
        verify(mMockNative).getCapabilities(transactionId.capture());
        mDut.onCapabilitiesUpdateResponse(transactionId.getValue(), getCapabilities());
        mMockLooper.dispatchAll();
        when(mMockAwareDataPathStatemanager.getNumOfNdps()).thenReturn(1);
        verify(mStatsManager).setPullAtomCallback(eq(WIFI_AWARE_CAPABILITIES), isNull(), any(),
                mPullAtomCallbackArgumentCaptor.capture());
        assertEquals(StatsManager.PULL_SUCCESS, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WIFI_AWARE_CAPABILITIES, new ArrayList<>()));
        ExtendedMockito.verify(() -> WifiStatsLog.buildStatsEvent(
                WIFI_AWARE_CAPABILITIES, true, true, true, 0, 1, 1, 2));
    }

    /**
     * Post-test validation.
     */
    @After
    public void tearDown() throws Exception {
        mSession.finishMocking();
        mMockNative.validateUniqueTransactionIds();
        mWifiHandlerThread.quit();
    }

    /**
     * Test that the set parameter shell command executor works when parameters are valid.
     */
    @Test
    public void testSetParameterShellCommandSuccess() {
        setSettableParam(WifiAwareStateManager.PARAM_ON_IDLE_DISABLE_AWARE, Integer.toString(1),
                true);
    }

    /**
     * Test that the set parameter shell command executor fails on incorrect name.
     */
    @Test
    public void testSetParameterShellCommandInvalidParameterName() {
        setSettableParam("XXX", Integer.toString(1), false);
    }

    /**
     * Test that the set parameter shell command executor fails on invalid value (not convertible
     * to an int).
     */
    @Test
    public void testSetParameterShellCommandInvalidValue() {
        setSettableParam(WifiAwareStateManager.PARAM_ON_IDLE_DISABLE_AWARE, "garbage", false);
    }

    /**
     * Test the PeerHandle -> MAC address API:
     * - Start up discovery of 2 sessions
     * - Get multiple matches (PeerHandles)
     * - Request translation as UID of session #1 for PeerHandles of the same UID + of the other
     *   discovery session (to which we shouldn't have access) + invalid PeerHandle.
     * - Vendor data list is converted to an array for callback
     * -> validate results
     */
    @Test
    public void testRequestMacAddresses() throws Exception {
        final int clientId1 = 1005;
        final int clientId2 = 1006;
        final int uid1 = 1000;
        final int uid2 = 1001;
        final int pid1 = 2000;
        final int pid2 = 2001;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String serviceName = "some-service-name";
        final byte subscribeId1 = 15;
        final byte subscribeId2 = 16;
        final int requestorIdBase = 22;
        final byte[] peerMac1 = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final byte[] peerMac2 = HexEncoding.decode("010203040506".toCharArray(), false);
        final byte[] peerMac3 = HexEncoding.decode("AABBCCDDEEFF".toCharArray(), false);
        final int distance = 10;

        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(0x00112233, new PersistableBundle()).build();
        List<OuiKeyedData> vendorDataList = Arrays.asList(vendorDataElement);
        OuiKeyedData[] vendorDataArray = new OuiKeyedData[vendorDataList.size()];
        vendorDataList.toArray(vendorDataArray);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(serviceName)
                .build();

        IWifiAwareEventCallback mockCallback1 = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback2 = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback1 = mock(
                IWifiAwareDiscoverySessionCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback2 = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback1, mockCallback2, mockSessionCallback1,
                mockSessionCallback2, mMockNative, mMockNativeManager);

        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).start(any(Handler.class));
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(Process.WIFI_UID));
        inOrder.verify(mMockNativeManager).releaseAware();

        // (0) enable
        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect 2 clients
        mDut.connect(clientId1, uid1, pid1, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid1, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback1).onConnectSuccess(clientId1);

        mDut.connect(clientId2, uid2, pid2, callingPackage, callingFeature, mockCallback2,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onConnectSuccess(clientId2);
        // merged requestorWs
        WorkSource expectedRequestorWs = new WorkSource(uid1, callingPackage);
        expectedRequestorWs.add(uid2, callingPackage);
        inOrder.verify(mMockNativeManager).replaceRequestorWs(expectedRequestorWs);

        // (2) subscribe both clients
        mDut.subscribe(clientId1, subscribeConfig, mockSessionCallback1);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId1);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback1).onSessionStarted(sessionId.capture());

        mDut.subscribe(clientId2, subscribeConfig, mockSessionCallback2);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId2);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback2).onSessionStarted(sessionId.capture());

        // (3) 2 matches on session 1 (second one with distance), 1 match on session 2
        mDut.onMatchNotification(subscribeId1, requestorIdBase, peerMac1, null, null, 0, 0,
                null, 0, null, null, null, vendorDataList);
        mDut.onMatchNotification(subscribeId1, requestorIdBase + 1, peerMac2, null, null,
                NanRangingIndication.INGRESS_MET_MASK, distance, null, 0, null, null, null,
                vendorDataList);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback1).onMatch(peerIdCaptor.capture(), isNull(),
                isNull(), anyInt(), isNull(), any(), any(), eq(vendorDataArray));
        inOrder.verify(mockSessionCallback1).onMatchWithDistance(peerIdCaptor.capture(), isNull(),
                isNull(), eq(distance), anyInt(), isNull(), any(), any(), eq(vendorDataArray));
        int peerId1 = peerIdCaptor.getAllValues().get(0);
        int peerId2 = peerIdCaptor.getAllValues().get(1);

        mDut.onMatchNotification(subscribeId2, requestorIdBase + 2, peerMac3, null, null, 0, 0,
                null, 0, null, null, null, vendorDataList);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback2).onMatch(peerIdCaptor.capture(), isNull(), isNull(),
                anyInt(), isNull(), any(), any(), eq(vendorDataArray));
        int peerId3 = peerIdCaptor.getAllValues().get(0);

        // request MAC addresses
        int[] request = new int[4];
        request[0] = peerId1;
        request[1] = peerId2;
        request[2] = peerId3; // for uid2: so should not be in results
        request[3] = peerId1 * 20 + peerId2 + peerId3; // garbage values != to any
        Mutable<MacAddrMapping[]> response = new Mutable<>();
        mDut.requestMacAddresses(uid1, request, new IWifiAwareMacAddressProvider() {
            @Override
            public void macAddress(MacAddrMapping[] peerIdToMacList) throws RemoteException {
                response.value = peerIdToMacList;
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        });
        mMockLooper.dispatchAll();

        assertNotEquals("Non-null result", null, response.value);
        assertEquals("Number of results", 2, response.value.length);

        MacAddrMapping responseMapping1, responseMapping2;
        responseMapping1 = responseMapping2 = null;
        for (MacAddrMapping mapping : response.value) {
            if (mapping.peerId == peerId1) {
                responseMapping1 = mapping;
            } else if (mapping.peerId == peerId2) {
                responseMapping2 = mapping;
            }
        }
        assertNotNull(responseMapping1);
        assertNotNull(responseMapping2);
        assertEquals("Results[peerId1]", peerMac1, responseMapping1.macAddress);
        assertEquals("Results[peerId2]", peerMac2, responseMapping2.macAddress);
    }

    /**
     * Validate that Aware data-path interfaces are brought up and down correctly.
     */
    @Test
    public void testAwareDataPathInterfaceUpDown() throws Exception {
        final int clientId = 12341;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mMockAwareDataPathStatemanager,
                mockCallback);

        // (1) enable usage
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));

        // (2) connect (enable Aware)
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mMockAwareDataPathStatemanager).createAllInterfaces();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (3) disconnect (disable Aware)
        mDut.disconnect(clientId);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockAwareDataPathStatemanager).deleteAllInterfaces();
        inOrder.verify(mMockNative).disable(transactionId.capture());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        assertFalse(mDut.isDeviceAttached());

        verifyNoMoreInteractions(mMockNative, mMockAwareDataPathStatemanager);
    }

    /**
     * Validate that APIs aren't functional when usage is disabled.
     */
    @Test
    public void testDisableUsageDisablesApis() throws Exception {
        final int clientId = 12314;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mockCallback);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);

        // (1) check initial state
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));

        // (2) disable usage and validate state
        mDut.disableUsage(false);
        mMockLooper.dispatchAll();
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(false));
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrder.verify(mMockNative).disable(transactionId.capture());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);

        // (3) try connecting and validate that get failure callback (though app should be aware of
        // non-availability through state change broadcast and/or query API)
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectFail(anyInt());

        verifyNoMoreInteractions(mMockNative, mockCallback);
    }

    /**
     * Validate that when API usage is disabled while in the middle of a connection that internal
     * state is cleaned-up, and that all subsequent operations are NOP. Then enable usage again and
     * validate that operates correctly.
     */
    @Test
    public void testDisableUsageFlow() throws Exception {
        final int clientId = 12341;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<SparseArray> sparseArrayCaptor = ArgumentCaptor.forClass(SparseArray.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mockCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) check initial state
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));

        // (2) connect (successfully)
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false),
                sparseArrayCaptor.capture(), eq(6), eq(callingFeature));
        collector.checkThat("num of clients", sparseArrayCaptor.getValue().size(), equalTo(1));

        // (3) disable usage & verify callbacks
        mDut.disableUsage(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onAttachTerminate();
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(false));
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrder.verify(mMockNative).disable(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordAttachSessionDuration(anyLong());
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        inOrderM.verify(mAwareMetricsMock).recordDisableUsage();
        validateInternalClientInfoCleanedUp(clientId);
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        assertFalse(mDut.isDeviceAttached());

        // (4) try connecting again and validate that get a failure
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectFail(anyInt());
        inOrderM.verify(mAwareMetricsMock).recordAttachStatus(NanStatusCode.INTERNAL_FAILURE, 6,
                callingFeature, uid);
        assertFalse(mDut.isDeviceAttached());

        // (5) disable usage again and validate that not much happens
        mDut.disableUsage(false);
        mMockLooper.dispatchAll();
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(false));

        // (6) enable usage
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));
        validateCorrectAwareStatusChangeBroadcast(inOrder);

        // (7) connect (should be successful)
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false),
                sparseArrayCaptor.capture(), eq(6), eq(callingFeature));
        collector.checkThat("num of clients", sparseArrayCaptor.getValue().size(), equalTo(1));
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mockCallback, mAwareMetricsMock);
    }

    /**
     * Validates that a HAL failure on enable and configure results in failed callback.
     */
    @Test
    public void testHalFailureEnableAndConfigure() throws Exception {
        final int clientId = 12341;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mockCallback, mMockNativeManager);

        when(mMockNative.enableAndConfigure(anyShort(), any(), anyBoolean(),
                anyBoolean(), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt()))
                .thenReturn(false);

        // (1) check initial state
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);

        // (2) connect with HAL failure
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());

        inOrder.verify(mMockNativeManager).releaseAware();
        inOrder.verify(mockCallback).onConnectFail(NanStatusCode.INTERNAL_FAILURE);

        validateInternalClientInfoCleanedUp(clientId);
        verifyNoMoreInteractions(mMockNative, mockCallback);
    }

    /**
     * Validates that all events are delivered with correct arguments. Validates
     * that IdentityChanged not delivered if configuration disables delivery.
     */
    @Test
    public void testAwareEventsDelivery() throws Exception {
        final int clientId1 = 1005;
        final int clientId2 = 1007;
        final int clusterLow = 5;
        final int clusterHigh = 100;
        final int masterPref = 111;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reason = NanStatusCode.INTERNAL_FAILURE;
        final byte[] someMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] someMac2 = HexEncoding.decode("060708090A0B".toCharArray(), false);

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref)
                .build();

        IWifiAwareEventCallback mockCallback1 = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback2 = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionIdCapture = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback1, mockCallback2, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionIdCapture.capture());

        // (1) connect 1st and 2nd clients
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionIdCapture.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        short transactionId = transactionIdCapture.getValue();
        mDut.onConfigSuccessResponse(transactionId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onConnectSuccess(clientId1);

        mDut.connect(clientId2, uid, pid, callingPackage, callingFeature, mockCallback2,
                configRequest, true, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionIdCapture.capture(),
                eq(configRequest), eq(true), eq(false), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        transactionId = transactionIdCapture.getValue();
        mDut.onConfigSuccessResponse(transactionId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onConnectSuccess(clientId2);

        // (2) deliver Aware events - without LOCATIONING permission
        mDut.onClusterChangeNotification(IdentityChangedListener.CLUSTER_CHANGE_EVENT_STARTED,
                someMac);
        mDut.onInterfaceAddressChangeNotification(someMac);
        mMockLooper.dispatchAll();

        inOrder.verify(mockCallback2).onClusterIdChanged(
                IdentityChangedListener.CLUSTER_CHANGE_EVENT_STARTED, ALL_ZERO_MAC);
        inOrder.verify(mockCallback2).onIdentityChanged(ALL_ZERO_MAC);

        // (3) deliver new identity - still without LOCATIONING permission (should get an event)
        mDut.onInterfaceAddressChangeNotification(someMac2);
        mMockLooper.dispatchAll();

        inOrder.verify(mockCallback2).onIdentityChanged(ALL_ZERO_MAC);

        // (4) deliver same identity - still without LOCATIONING permission (should
        // not get an event)
        mDut.onInterfaceAddressChangeNotification(someMac2);
        mMockLooper.dispatchAll();

        // (5) deliver new identity - with LOCATIONING permission
        when(mWifiPermissionsUtil.checkCallersLocationPermission(eq(callingPackage),
                eq(callingFeature), eq(uid), anyBoolean(), any())).thenReturn(true);
        mDut.onInterfaceAddressChangeNotification(someMac);
        mMockLooper.dispatchAll();

        inOrder.verify(mockCallback2).onIdentityChanged(someMac);

        // (6) Aware down - session should be terminated.
        mDut.onAwareDownNotification(reason);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onAttachTerminate();
        inOrder.verify(mockCallback2).onAttachTerminate();

        validateInternalClientInfoCleanedUp(clientId1);
        validateInternalClientInfoCleanedUp(clientId2);

        verifyNoMoreInteractions(mockCallback1, mockCallback2, mMockNative);
    }

    /**
     * Validate that when the HAL doesn't respond we get a TIMEOUT (which
     * results in a failure response) at which point we can process additional
     * commands. Steps: (1) connect, (2) publish - timeout, (3) publish +
     * success.
     */
    @Test
    public void testHalNoResponseTimeout() throws Exception {
        final int clientId = 12341;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();
        final PublishConfig publishConfig = new PublishConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (1) connect (successfully)
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (2) publish + timeout
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(anyShort(), eq((byte) 0), eq(publishConfig), isNull());
        assertTrue(mAlarmManager.dispatch(WifiAwareStateManager.HAL_COMMAND_TIMEOUT_TAG));
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid,
                NanStatusCode.INTERNAL_FAILURE, true, 6, callingFeature);
        validateInternalNoSessions(clientId);

        // (3) publish + success
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, (byte) 99);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(anyInt());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback, mAwareMetricsMock);
    }

    /**
     * Validates publish flow: (1) initial publish (2) fail informed by notification, (3) fail due
     * to immediate HAL failure. Expected: get a failure callback.
     */
    @Test
    public void testPublishFail() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonFail = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) publish failure callback (i.e. firmware tried and failed)
        mDut.onSessionConfigFailResponse(transactionId.getValue(), true, reasonFail);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, true,
                6, callingFeature);
        validateInternalNoSessions(clientId);

        // (3) publish and get immediate failure (i.e. HAL failed)
        when(mMockNative.publish(anyShort(), anyByte(), any(), any())).thenReturn(false);

        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, true,
                6, callingFeature);
        validateInternalNoSessions(clientId);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validates the publish flow: (1) initial publish (2) success (3)
     * termination (e.g. DONE) (4) update session attempt (5) terminateSession
     * (6) update session attempt. Expected: session ID callback + session
     * cleaned-up.
     */
    @Test
    public void testPublishSuccessTerminated() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte publishId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(1, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(2, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) publish termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(publishId, reasonTerminate, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(true),
                anyInt());

        // (4) app update session (race condition: app didn't get termination
        // yet)
        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();

        // (5) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        // (6) app updates session (app already knows that terminated - will get
        // a local FAIL).
        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validates publish with instant communication with correct channel freq
     */
    @Test
    public void testPublishSuccessInstantCommunicationMode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte publishId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setInstantCommunicationModeEnabled(true, WifiScanner.WIFI_BAND_5_GHZ).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(1, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(2, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) Verify reconfig to enable instant mode. And 5G is invalid
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                any(), eq(false), eq(false), eq(true), eq(false), eq(false), eq(true),
                eq(2437), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (4) country code change, 5G is valid
        when(mWifiNative.getUsableChannels(WifiScanner.WIFI_BAND_5_GHZ, OP_MODE_WIFI_AWARE,
                WifiAvailableChannel.FILTER_NAN_INSTANT_MODE))
                .thenReturn(List.of(new WifiAvailableChannel(5220,
                                WifiAvailableChannel.OP_MODE_WIFI_AWARE),
                        new WifiAvailableChannel(5745, WifiAvailableChannel.OP_MODE_WIFI_AWARE)));
        mActiveCountryCodeChangedCallback.onActiveCountryCodeChanged("US");
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                any(), eq(false), eq(false), eq(true), eq(false), eq(false), eq(true),
                eq(5745), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validates subscribe with instant communication with correct channel freq
     */
    @Test
    public void testSubscribeSuccessInstantCommunicationMode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte subscribeId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setInstantCommunicationModeEnabled(true, WifiScanner.WIFI_BAND_5_GHZ).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(2, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(1, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) Verify reconfig to enable instant mode. And 5G is invalid
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                any(), eq(false), eq(false), eq(true), eq(false), eq(false), eq(true),
                eq(2437), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (4) country code change, 5G is valid
        when(mWifiNative.getUsableChannels(WifiScanner.WIFI_BAND_5_GHZ, OP_MODE_WIFI_AWARE,
                WifiAvailableChannel.FILTER_NAN_INSTANT_MODE))
                .thenReturn(List.of(new WifiAvailableChannel(5220,
                        WifiAvailableChannel.OP_MODE_WIFI_AWARE)));
        mActiveCountryCodeChangedCallback.onActiveCountryCodeChanged("US");
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                any(), eq(false), eq(false), eq(true), eq(false), eq(false), eq(true),
                eq(5220), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validate the publish flow: (1) initial publish + (2) success + (3) update + (4) update
     * fails (callback from firmware) + (5) update + (6). Expected: session is still alive after
     * update failure so second update succeeds (no callbacks) + (7) update + immediate failure from
     * HAL + (8) update + failure for invalid ID (which should clean-up state) + (9) another update
     * - should get no response.
     */
    @Test
    public void testPublishUpdateFail() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte publishId = 15;
        final int reasonFail = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().setRangingEnabled(true).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionWithRanging(eq(uid), eq(false),
                eq(-1), eq(-1), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        mMockLooper.dispatchAll();
        // Verify reconfigure aware to enable ranging.
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(false), eq(true), eq(false), eq(true), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (3) update publish
        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq(publishId),
                eq(publishConfig), isNull());

        // (4) update fails
        mDut.onSessionConfigFailResponse(transactionId.getValue(), true, reasonFail);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, true,
                6, callingFeature);

        // (5) another update publish
        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq(publishId),
                eq(publishConfig), isNull());

        // (6) update succeeds
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigSuccess();
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, NanStatusCode.SUCCESS, true,
                6, callingFeature);

        // (7) another update + immediate failure
        when(mMockNative.publish(anyShort(), anyByte(), any(), any())).thenReturn(false);

        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq(publishId),
                eq(publishConfig), isNull());
        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, true,
                6, callingFeature);

        // (8) an update with bad ID failure
        when(mMockNative.publish(anyShort(), anyByte(), any(), any())).thenReturn(true);

        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq(publishId),
                eq(publishConfig), isNull());
        mDut.onSessionConfigFailResponse(transactionId.getValue(), true,
                NanStatusCode.INVALID_SESSION_ID);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(NanStatusCode.INVALID_SESSION_ID);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid,
                NanStatusCode.INVALID_SESSION_ID, true, 6, callingFeature);
        // Verify reconfigure aware to disable ranging.
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(false), eq(true), eq(false), eq(false), eq(false), anyInt(),
                anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (9) try updating again - do nothing/get nothing
        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validate race condition: publish pending but session terminated (due to
     * disconnect - can't terminate such a session directly from app). Need to
     * make sure that once publish succeeds (failure isn't a problem) the
     * session is immediately terminated since no-one is listening for it.
     */
    @Test
    public void testDisconnectWhilePublishPending() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte publishId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) disconnect (but doesn't get executed until get response for
        // publish command)
        mDut.disconnect(clientId);
        mMockLooper.dispatchAll();

        // (3) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(anyInt());
        inOrder.verify(mockSessionCallback).onSessionTerminated(anyInt());
        inOrder.verify(mMockNative).stopPublish(transactionId.capture(), eq(publishId));
        inOrder.verify(mockCallback).onAttachTerminate();
        inOrder.verify(mMockNative).disable(transactionId.capture());

        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        inOrderM.verify(mAwareMetricsMock).recordAttachSessionDuration(anyLong());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(true),
                anyInt());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        verify(mMockNativeManager, times(2)).releaseAware();

        assertFalse(mDut.isDeviceAttached());
        validateInternalClientInfoCleanedUp(clientId);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validates subscribe flow: (1) initial subscribe (2) fail (callback from firmware), (3) fail
     * due to immeidate HAL failure. Expected: get a failure callback.
     */
    @Test
    public void testSubscribeFail() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonFail = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        // (2) subscribe failure
        mDut.onSessionConfigFailResponse(transactionId.getValue(), false, reasonFail);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, false,
                6, callingFeature);
        validateInternalNoSessions(clientId);

        // (3) subscribe and get immediate failure (i.e. HAL failed)
        when(mMockNative.subscribe(anyShort(), anyByte(), any(), any())).thenReturn(false);

        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, false,
                6, callingFeature);
        validateInternalNoSessions(clientId);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validates the subscribe flow: (1) initial subscribe (2) success (3)
     * termination (e.g. DONE) (4) update session attempt (5) terminateSession
     * (6) update session attempt. Expected: session ID callback + session
     * cleaned-up
     */
    @Test
    public void testSubscribeSuccessTerminated() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte subscribeId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(2, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(1, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) subscribe termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(subscribeId, reasonTerminate, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(false),
                anyInt());

        // (4) app update session (race condition: app didn't get termination
        // yet)
        mDut.updateSubscribe(clientId, sessionId.getValue(), subscribeConfig);
        mMockLooper.dispatchAll();

        // (5) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        // (6) app updates session
        mDut.updateSubscribe(clientId, sessionId.getValue(), subscribeConfig);
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validate the subscribe flow: (1) initial subscribe + (2) success + (3) update + (4) update
     * fails (callback from firmware) + (5) update + (6). Expected: session is still alive after
     * update failure so second update succeeds (no callbacks). + (7) update + immediate failure
     * from HAL.
     */
    @Test
    public void testSubscribeUpdateFail() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte subscribeId = 15;
        final int reasonFail = NanStatusCode.INTERNAL_FAILURE;
        final int rangeMax = 10;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setMaxDistanceMm(
                rangeMax).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionWithRanging(eq(uid), eq(true),
                eq(-1), eq(rangeMax), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        // Verify reconfigure aware to enable ranging.
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(false), eq(true), eq(false), eq(true), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (3) update subscribe
        mDut.updateSubscribe(clientId, sessionId.getValue(), subscribeConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq(subscribeId),
                eq(subscribeConfig), isNull());

        // (4) update fails
        mDut.onSessionConfigFailResponse(transactionId.getValue(), false, reasonFail);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, false,
                6, callingFeature);

        // (5) another update subscribe
        mDut.updateSubscribe(clientId, sessionId.getValue(), subscribeConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq(subscribeId),
                eq(subscribeConfig), isNull());

        // (6) update succeeds
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigSuccess();
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, NanStatusCode.SUCCESS, false,
                6, callingFeature);

        // (7) another update + immediate failure
        when(mMockNative.subscribe(anyShort(), anyByte(), any(), any())).thenReturn(false);

        mDut.updateSubscribe(clientId, sessionId.getValue(), subscribeConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq(subscribeId),
                eq(subscribeConfig), isNull());
        inOrder.verify(mockSessionCallback).onSessionConfigFail(reasonFail);
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(uid, reasonFail, false,
                6, callingFeature);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validate race condition: subscribe pending but session terminated (due to
     * disconnect - can't terminate such a session directly from app). Need to
     * make sure that once subscribe succeeds (failure isn't a problem) the
     * session is immediately terminated since no-one is listening for it.
     */
    @Test
    public void testDisconnectWhileSubscribePending() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte subscribeId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        // (2) disconnect (but doesn't get executed until get response for
        // subscribe command)
        mDut.disconnect(clientId);
        mMockLooper.dispatchAll();

        // (3) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(anyInt());
        inOrder.verify(mockSessionCallback).onSessionTerminated(anyInt());
        inOrder.verify(mMockNative).stopSubscribe((short) 0, subscribeId);
        inOrder.verify(mockCallback).onAttachTerminate();
        inOrder.verify(mMockNative).disable(anyShort());

        validateInternalClientInfoCleanedUp(clientId);

        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative);
    }

    /**
     * Validate (1) subscribe (success), (2) match (i.e. discovery), (3) message reception,
     * (4) message transmission failed (after ok queuing), (5) message transmission success.
     */
    @Test
    public void testMatchAndMessages() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String serviceName = "some-service-name";
        final String ssi = "some much longer and more arbitrary data";
        final int reasonFail = NanStatusCode.INTERNAL_FAILURE;
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final String peerMsg = "some message from peer";
        final int messageId = 6948;
        final int messageId2 = 6949;
        final int rangeMin = 0;
        final int rangeMax = 55;
        final int rangedDistance = 30;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(serviceName)
                .setServiceSpecificInfo(ssi.getBytes())
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
                .setMinDistanceMm(rangeMin)
                .setMaxDistanceMm(rangeMax)
                .build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionWithRanging(eq(uid), eq(true),
                eq(rangeMin), eq(rangeMax), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        // Verify reconfigure aware to enable ranging.
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(false), eq(true), eq(false), eq(true), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (2) 2 matches : with and w/o range
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());
        inOrderM.verify(mAwareMetricsMock).recordMatchIndicationForRangeEnabledSubscribe(false);
        int peerId1 = peerIdCaptor.getValue();

        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), EGRESS_MET_MASK, rangedDistance, null, 0, null, null,
                null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatchWithDistance(peerIdCaptor.capture(),
                eq(peerSsi.getBytes()), eq(peerMatchFilter.getBytes()), eq(rangedDistance),
                anyInt(), isNull(), isNull(), isNull(), isNull());
        inOrderM.verify(mAwareMetricsMock).recordMatchIndicationForRangeEnabledSubscribe(true);
        int peerId2 = peerIdCaptor.getValue();

        assertEquals(peerId1, peerId2);

        // (3) message Rx
        mDut.onMessageReceivedNotification(subscribeId, requestorId, peerMac, peerMsg.getBytes());
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageReceived(peerIdCaptor.getValue(),
                peerMsg.getBytes());

        // (4) message Tx successful queuing
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId));
        short tid1 = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(tid1);
        mMockLooper.dispatchAll();

        // (5) message Tx successful queuing
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId2, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId2));
        short tid2 = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(tid2);
        mMockLooper.dispatchAll();

        // (4) and (5) final Tx results (on-air results)
        mDut.onMessageSendFailNotification(tid1, reasonFail);
        mDut.onMessageSendSuccessNotification(tid2);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendFail(messageId, reasonFail);
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(messageId2);
        validateInternalSendMessageQueuesCleanedUp(messageId);
        validateInternalSendMessageQueuesCleanedUp(messageId2);
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Summary: in a single publish session interact with multiple peers
     * (different MAC addresses).
     */
    @Test
    public void testMultipleMessageSources() throws Exception {
        final int clientId = 300;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int clusterLow = 7;
        final int clusterHigh = 7;
        final int masterPref = 0;
        final String serviceName = "some-service-name";
        final byte publishId = 88;
        final int requestorId1 = 568;
        final int requestorId2 = 873;
        final byte[] peerMac1 = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerMac2 = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String msgFromPeer1 = "hey from 000102...";
        final String msgFromPeer2 = "hey from 0607...";
        final String msgToPeer1 = "hey there 000102...";
        final String msgToPeer2 = "hey there 0506...";
        final int msgToPeerId1 = 546;
        final int msgToPeerId2 = 9654;
        final int reason = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref).build();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(serviceName)
                .setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED).build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (3) message received from peers 1 & 2
        mDut.onMessageReceivedNotification(publishId, requestorId1, peerMac1,
                msgFromPeer1.getBytes());
        mDut.onMessageReceivedNotification(publishId, requestorId2, peerMac2,
                msgFromPeer2.getBytes());
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageReceived(peerIdCaptor.capture(),
                eq(msgFromPeer1.getBytes()));
        int peerId1 = peerIdCaptor.getValue();
        inOrder.verify(mockSessionCallback).onMessageReceived(peerIdCaptor.capture(),
                eq(msgFromPeer2.getBytes()));
        int peerId2 = peerIdCaptor.getValue();

        // (4) sending messages back to same peers: one Tx fails, other succeeds
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerId2, msgToPeer2.getBytes(),
                msgToPeerId2, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(publishId),
                eq(requestorId2), eq(peerMac2), eq(msgToPeer2.getBytes()), eq(msgToPeerId2));
        short transactionIdVal = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(transactionIdVal);
        mDut.onMessageSendSuccessNotification(transactionIdVal);

        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerId1, msgToPeer1.getBytes(),
                msgToPeerId1, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(msgToPeerId2);
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(publishId),
                eq(requestorId1), eq(peerMac1), eq(msgToPeer1.getBytes()), eq(msgToPeerId1));
        transactionIdVal = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(transactionIdVal);
        mDut.onMessageSendFailNotification(transactionIdVal, reason);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendFail(msgToPeerId1, reason);
        validateInternalSendMessageQueuesCleanedUp(msgToPeerId1);
        validateInternalSendMessageQueuesCleanedUp(msgToPeerId2);

        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback);
    }

    /**
     * Summary: interact with a peer which changed its identity (MAC address)
     * but which keeps its requestor instance ID. Should be transparent.
     */
    @Test
    public void testMessageWhilePeerChangesIdentity() throws Exception {
        final int clientId = 300;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int clusterLow = 7;
        final int clusterHigh = 7;
        final int masterPref = 0;
        final String serviceName = "some-service-name";
        final byte publishId = 88;
        final int requestorId = 568;
        final byte[] peerMacOrig = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerMacLater = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String msgFromPeer1 = "hey from 000102...";
        final String msgFromPeer2 = "hey from 0607...";
        final String msgToPeer1 = "hey there 000102...";
        final String msgToPeer2 = "hey there 0506...";
        final int msgToPeerId1 = 546;
        final int msgToPeerId2 = 9654;
        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref).build();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(serviceName)
                .setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED).build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerId = ArgumentCaptor.forClass(Integer.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (3) message received & responded to
        mDut.onMessageReceivedNotification(publishId, requestorId, peerMacOrig,
                msgFromPeer1.getBytes());
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageReceived(peerId.capture(),
                eq(msgFromPeer1.getBytes()));
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerId.getValue(),
                msgToPeer1.getBytes(), msgToPeerId1, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(publishId),
                eq(requestorId), eq(peerMacOrig), eq(msgToPeer1.getBytes()),
                eq(msgToPeerId1));
        mDut.onMessageSendQueuedSuccessResponse(transactionId.getValue());
        mDut.onMessageSendSuccessNotification(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(msgToPeerId1);
        validateInternalSendMessageQueuesCleanedUp(msgToPeerId1);

        // (4) message received with same peer ID but different MAC
        mDut.onMessageReceivedNotification(publishId, requestorId, peerMacLater,
                msgFromPeer2.getBytes());
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageReceived(peerId.capture(),
                eq(msgFromPeer2.getBytes()));
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerId.getValue(),
                msgToPeer2.getBytes(), msgToPeerId2, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(publishId),
                eq(requestorId), eq(peerMacLater), eq(msgToPeer2.getBytes()),
                eq(msgToPeerId2));
        mDut.onMessageSendQueuedSuccessResponse(transactionId.getValue());
        mDut.onMessageSendSuccessNotification(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(msgToPeerId2);
        validateInternalSendMessageQueuesCleanedUp(msgToPeerId2);

        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback);
    }

    /**
     * Validate that get failure (with correct code) when trying to send a
     * message to an invalid peer ID.
     */
    @Test
    public void testSendMessageToInvalidPeerId() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String ssi = "some much longer and more arbitrary data";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final int messageId = 6948;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) subscribe & match
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) send message to invalid peer ID
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue() + 5,
                ssi.getBytes(), messageId, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendFail(messageId,
                NanStatusCode.INTERNAL_FAILURE);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative);
    }

    /**
     * Validate that on send message errors are handled correctly: immediate send error, queue fail
     * error (not queue full), and timeout. Behavior: correct callback is dispatched and a later
     * firmware notification is ignored. Intersperse with one successfull transmission.
     */
    @Test
    public void testSendMessageErrorsImmediateQueueTimeout() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String ssi = "some much longer and more arbitrary data";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final int messageId = 6948;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) subscribe & match
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) send 2 messages and enqueue successfully
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId));
        short transactionId1 = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(transactionId1);
        mMockLooper.dispatchAll();

        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId + 1, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId + 1));
        short transactionId2 = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(transactionId2);
        mMockLooper.dispatchAll();

        // (4) send a message and get a queueing failure (not queue full)
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId + 2, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId + 2));
        short transactionId3 = transactionId.getValue();
        mDut.onMessageSendQueuedFailResponse(transactionId3, NanStatusCode.INTERNAL_FAILURE);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendFail(messageId + 2,
                NanStatusCode.INTERNAL_FAILURE);
        validateInternalSendMessageQueuesCleanedUp(messageId + 2);

        // (5) send a message and get an immediate failure (configure first)
        when(mMockNative.sendMessage(anyShort(), anyByte(), anyInt(), any(),
                any(), anyInt())).thenReturn(false);

        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId + 3, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId + 3));
        short transactionId4 = transactionId.getValue();
        inOrder.verify(mockSessionCallback).onMessageSendFail(messageId + 3,
                NanStatusCode.INTERNAL_FAILURE);
        validateInternalSendMessageQueuesCleanedUp(messageId + 3);

        // (6) message send timeout
        assertTrue(mAlarmManager.dispatch(WifiAwareStateManager.HAL_SEND_MESSAGE_TIMEOUT_TAG));
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendFail(messageId,
                NanStatusCode.INTERNAL_FAILURE);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        // (7) firmware response (unlikely - but good to check)
        mDut.onMessageSendSuccessNotification(transactionId1);
        mDut.onMessageSendSuccessNotification(transactionId2);

        // bogus: these didn't even go to firmware or weren't queued
        mDut.onMessageSendSuccessNotification(transactionId3);
        mDut.onMessageSendFailNotification(transactionId4, NanStatusCode.INTERNAL_FAILURE);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(messageId + 1);

        validateInternalSendMessageQueuesCleanedUp(messageId + 1);

        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative);
    }

    /**
     * Validate that when sending a message with a retry count the message is retried the specified
     * number of times. Scenario ending with success.
     */
    @Test
    public void testSendMessageRetransmitSuccess() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String ssi = "some much longer and more arbitrary data";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final int messageId = 6948;
        final int retryCount = 3;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) subscribe & match
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) send message and enqueue successfully
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId, retryCount);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId));
        mDut.onMessageSendQueuedSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (4) loop and fail until reach retryCount
        for (int i = 0; i < retryCount; ++i) {
            mDut.onMessageSendFailNotification(transactionId.getValue(), NanStatusCode.NO_OTA_ACK);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                    eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId));
            mDut.onMessageSendQueuedSuccessResponse(transactionId.getValue());
            mMockLooper.dispatchAll();
        }

        // (5) succeed on last retry
        mDut.onMessageSendSuccessNotification(transactionId.getValue());
        mMockLooper.dispatchAll();

        inOrder.verify(mockSessionCallback).onMessageSendSuccess(messageId);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative);
    }

    /**
     * Validate that when sending a message with a retry count the message is retried the specified
     * number of times. Scenario ending with failure.
     */
    @Test
    public void testSendMessageRetransmitFail() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String ssi = "some much longer and more arbitrary data";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final int messageId = 6948;
        final int retryCount = 3;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) subscribe & match
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) send message and enqueue successfully
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                ssi.getBytes(), messageId, retryCount);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId));
        mDut.onMessageSendQueuedSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (4) loop and fail until reach retryCount+1
        for (int i = 0; i < retryCount + 1; ++i) {
            mDut.onMessageSendFailNotification(transactionId.getValue(), NanStatusCode.NO_OTA_ACK);
            mMockLooper.dispatchAll();

            if (i != retryCount) {
                inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                        eq(requestorId), eq(peerMac), eq(ssi.getBytes()), eq(messageId));
                mDut.onMessageSendQueuedSuccessResponse(transactionId.getValue());
                mMockLooper.dispatchAll();
            }
        }

        inOrder.verify(mockSessionCallback).onMessageSendFail(messageId,
                NanStatusCode.NO_OTA_ACK);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative);
    }

    /**
     * Validate that the host-side message queue functions. Tests the perfect case of queue always
     * succeeds and all messages are received on first attempt.
     */
    @Test
    public void testSendMessageQueueSequence() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String serviceName = "some-service-name";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final int messageIdBase = 6948;
        final int numberOfMessages = 30;
        final int queueDepth = 6;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(serviceName)
                .build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> messageIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (1) subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (2) match
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, null, null, 0, 0,
                null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), isNull(), isNull(),
                anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) transmit messages
        SendMessageQueueModelAnswer answerObj = new SendMessageQueueModelAnswer(queueDepth,
                null, null, null);
        when(mMockNative.sendMessage(anyShort(), anyByte(), anyInt(), any(),
                any(), anyInt())).thenAnswer(answerObj);

        int remainingMessages = numberOfMessages;
        for (int i = 0; i < numberOfMessages; ++i) {
            mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(), null,
                    messageIdBase + i, 0);
            mMockLooper.dispatchAll();
            // at 1/2 interval have the system simulate transmitting a queued message over-the-air
            if (i % 2 == 1) {
                assertTrue(answerObj.process());
                remainingMessages--;
                mMockLooper.dispatchAll();
            }
        }
        for (int i = 0; i < remainingMessages; ++i) {
            assertTrue(answerObj.process());
            mMockLooper.dispatchAll();
        }
        assertEquals("queue empty", 0, answerObj.queueSize());

        inOrder.verify(mockSessionCallback, times(numberOfMessages)).onMessageSendSuccess(
                messageIdCaptor.capture());
        for (int i = 0; i < numberOfMessages; ++i) {
            assertEquals("message ID: " + i, (long) messageIdBase + i,
                    (long) messageIdCaptor.getAllValues().get(i));
        }

        verifyNoMoreInteractions(mockCallback, mockSessionCallback);
    }

    /**
     * Validate that the message queue depth per process function. Tests the case
     * with two processes both have message num larger than queue depth. And all messages get
     * into the firmware queue are sent out and are received on first attempt.
     */
    @Test
    public void testSendMessageQueueLimitBlock() throws Exception {
        final int clientId1 = 1005;
        final int clientId2 = 1006;
        final int uid1 = 1000;
        final int uid2 = 1500;
        final int pid1 = 2000;
        final int pid2 = 3000;
        final String callingPackage1 = "com.google.somePackage1";
        final String callingPackage2 = "com.google.somePackage2";
        final String callingFeature = "com.google.someFeature";
        final String serviceName1 = "some-service-name1";
        final String serviceName2 = "some-service-name2";
        final byte subscribeId1 = 15;
        final byte subscribeId2 = 16;
        final int requestorId1 = 22;
        final int requestorId2 = 23;
        final byte[] peerMac1 = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final byte[] peerMac2 = HexEncoding.decode("060708090A0C".toCharArray(), false);
        final int messageIdBase1 = 6948;
        final int messageIdBase2 = 7948;
        final int numberOfMessages = 70;
        final int queueDepth = 6;
        final int messageQueueDepthPerUid = 50;
        final int numOfReject = numberOfMessages - messageQueueDepthPerUid;

        ConfigRequest configRequest1 = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig1 = new SubscribeConfig.Builder()
                .setServiceName(serviceName1).build();
        ConfigRequest configRequest2 = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig2 = new SubscribeConfig.Builder()
                .setServiceName(serviceName2).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId1 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sessionId2 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> messageIdCaptorFail = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> messageIdCaptorSuccess = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor1 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor2 = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative,
                mMockNativeManager);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (0) connect
        mDut.connect(clientId1, uid1, pid1, callingPackage1, callingFeature, mockCallback,
                configRequest1, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid1, callingPackage1));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest1), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId1);

        mDut.connect(clientId2, uid2, pid2, callingPackage2, callingFeature, mockCallback,
                configRequest2, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId2);
        // merged requestorWs
        WorkSource expectedRequestorWs = new WorkSource(uid1, callingPackage1);
        expectedRequestorWs.add(uid2, callingPackage2);
        inOrder.verify(mMockNativeManager).replaceRequestorWs(expectedRequestorWs);

        // (1) subscribe
        mDut.subscribe(clientId1, subscribeConfig1, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig1), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId1);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId1.capture());

        mDut.subscribe(clientId2, subscribeConfig2, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig2), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId2);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId2.capture());

        // (2) match
        mDut.onMatchNotification(subscribeId1, requestorId1, peerMac1, null, null, 0, 0,
                null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor1.capture(), isNull(), isNull(),
                anyInt(), isNull(), isNull(), isNull(), isNull());

        mDut.onMatchNotification(subscribeId2, requestorId2, peerMac2, null, null, 0, 0,
                null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor2.capture(), isNull(), isNull(),
                anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) Enqueue messages
        SendMessageQueueModelAnswer answerObj = new SendMessageQueueModelAnswer(queueDepth,
                null, null, null);
        when(mMockNative.sendMessage(anyShort(), anyByte(), anyInt(), any(),
                any(), anyInt())).thenAnswer(answerObj);

        for (int i = 0; i < numberOfMessages; ++i) {
            mDut.sendMessage(uid1, clientId1, sessionId1.getValue(), peerIdCaptor1.getValue(), null,
                    messageIdBase1 + i, 0);
        }
        for (int i = 0; i < numberOfMessages; ++i) {
            mDut.sendMessage(uid2, clientId2, sessionId2.getValue(), peerIdCaptor2.getValue(), null,
                    messageIdBase2 + i, 0);
        }
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback, times(numOfReject * 2))
                .onMessageSendFail(messageIdCaptorFail.capture(),
                        eq(NanStatusCode.INTERNAL_FAILURE));

        // (4) Transmit messages
        int successNum = 0;
        for (int i = 0; i < numberOfMessages * 2; ++i) {
            if (answerObj.process()) {
                successNum++;
            } else {
                break;
            }
            mMockLooper.dispatchAll();
        }
        assertEquals("queue empty", 0, answerObj.queueSize());
        assertEquals("success message num", messageQueueDepthPerUid * 2, successNum);
        inOrder.verify(mockSessionCallback, times(messageQueueDepthPerUid * 2))
                .onMessageSendSuccess(messageIdCaptorSuccess.capture());

        for (int i = 0; i < numOfReject; ++i) {
            assertEquals("message ID: " + i + messageQueueDepthPerUid,
                    messageIdBase1 + i + messageQueueDepthPerUid,
                    (int) messageIdCaptorFail.getAllValues().get(i));
            assertEquals("message ID: " + i + messageQueueDepthPerUid,
                    messageIdBase2 + i + messageQueueDepthPerUid,
                    (int) messageIdCaptorFail.getAllValues().get(i + numOfReject));
        }

        for (int i = 0; i < messageQueueDepthPerUid; ++i) {
            assertEquals("message ID: " + i, messageIdBase1 + i,
                    (int) messageIdCaptorSuccess.getAllValues().get(i));
            assertEquals("message ID: " + i,  messageIdBase2 + i,
                    (int) messageIdCaptorSuccess.getAllValues().get(i + messageQueueDepthPerUid));
        }

        verifyNoMoreInteractions(mockCallback, mockSessionCallback);
    }

    /**
     * Validate that the host-side message queue functions. A combination of imperfect conditions:
     * - Failure to queue: synchronous firmware error
     * - Failure to queue: asyncronous firmware error
     * - Failure to transmit: OTA (which will be retried)
     * - Failure to transmit: other
     */
    @Test
    public void testSendMessageQueueSequenceImperfect() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String serviceName = "some-service-name";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final int messageIdBase = 6948;
        final int numberOfMessages = 300;
        final int queueDepth = 6;
        final int retransmitCount = 3; // not the maximum

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(serviceName)
                .build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (1) subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (2) match
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, null, null, 0, 0,
                null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), isNull(), isNull(),
                anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) transmit messages: configure a mix of failures/success
        Set<Integer> failQueueCommandImmediately = new HashSet<>();
        Set<Integer> failQueueCommandLater = new HashSet<>();
        Map<Integer, Integer> numberOfRetries = new HashMap<>();

        int numOfSuccesses = 0;
        int numOfFailuresInternalFailure = 0;
        int numOfFailuresNoOta = 0;
        for (int i = 0; i < numberOfMessages; ++i) {
            // random results:
            // - 0-50: success
            // - 51-60: retransmit value (which will fail for >5)
            // - 61-70: fail queue later
            // - 71-80: fail queue immediately
            // - 81-90: fail retransmit with non-OTA failure
            int random = mRandomNg.nextInt(90);
            if (random <= 50) {
                numberOfRetries.put(messageIdBase + i, 0);
                numOfSuccesses++;
            } else if (random <= 60) {
                numberOfRetries.put(messageIdBase + i, random - 51);
                if (random - 51 > retransmitCount) {
                    numOfFailuresNoOta++;
                } else {
                    numOfSuccesses++;
                }
            } else if (random <= 70) {
                failQueueCommandLater.add(messageIdBase + i);
                numOfFailuresInternalFailure++;
            } else if (random <= 80) {
                failQueueCommandImmediately.add(messageIdBase + i);
                numOfFailuresInternalFailure++;
            } else {
                numberOfRetries.put(messageIdBase + i, -1);
                numOfFailuresInternalFailure++;
            }
        }

        Log.v("WifiAwareStateManagerTest",
                "failQueueCommandImmediately=" + failQueueCommandImmediately
                        + ", failQueueCommandLater=" + failQueueCommandLater + ", numberOfRetries="
                        + numberOfRetries + ", numOfSuccesses=" + numOfSuccesses
                        + ", numOfFailuresInternalFailure=" + numOfFailuresInternalFailure
                        + ", numOfFailuresNoOta=" + numOfFailuresNoOta);

        SendMessageQueueModelAnswer answerObj = new SendMessageQueueModelAnswer(queueDepth,
                failQueueCommandImmediately, failQueueCommandLater, numberOfRetries);
        when(mMockNative.sendMessage(anyShort(), anyByte(), anyInt(), any(),
                any(), anyInt())).thenAnswer(answerObj);

        for (int i = 0; i < numberOfMessages; ++i) {
            mDut.sendMessage(uid + i, clientId, sessionId.getValue(), peerIdCaptor.getValue(), null,
                    messageIdBase + i, retransmitCount);
            mMockLooper.dispatchAll();
        }

        while (answerObj.queueSize() != 0) {
            assertTrue(answerObj.process());
            mMockLooper.dispatchAll();
        }

        verify(mockSessionCallback, times(numOfSuccesses)).onMessageSendSuccess(anyInt());
        verify(mockSessionCallback, times(numOfFailuresInternalFailure)).onMessageSendFail(anyInt(),
                eq(NanStatusCode.INTERNAL_FAILURE));
        verify(mockSessionCallback, times(numOfFailuresNoOta)).onMessageSendFail(anyInt(),
                eq(NanStatusCode.NO_OTA_ACK));

        verifyNoMoreInteractions(mockCallback, mockSessionCallback);
    }

    /**
     * Validate that can send empty message successfully: null, byte[0], ""
     */
    @Test
    public void testSendEmptyMessages() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String serviceName = "some-service-name";
        final String ssi = "some much longer and more arbitrary data";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final int messageId = 6948;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(serviceName)
                .setServiceSpecificInfo(ssi.getBytes())
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
                .build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<byte[]> byteArrayCaptor = ArgumentCaptor.forClass(byte[].class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (1) subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (2) match
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());

        // (3) message null Tx successful queuing
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                null, messageId, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), isNull(byte[].class), eq(messageId));
        short tid = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(tid);
        mMockLooper.dispatchAll();

        // (4) final Tx results (on-air results)
        mDut.onMessageSendSuccessNotification(tid);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(messageId);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        // (5) message byte[0] Tx successful queuing
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(), new byte[0],
                messageId, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), eq(new byte[0]), eq(messageId));
        tid = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(tid);
        mMockLooper.dispatchAll();

        // (6) final Tx results (on-air results)
        mDut.onMessageSendSuccessNotification(tid);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(messageId);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        // (7) message "" Tx successful queuing
        mDut.sendMessage(uid, clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                "".getBytes(), messageId, 0);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).sendMessage(transactionId.capture(), eq(subscribeId),
                eq(requestorId), eq(peerMac), byteArrayCaptor.capture(), eq(messageId));
        collector.checkThat("Empty message contents", "",
                equalTo(new String(byteArrayCaptor.getValue())));
        tid = transactionId.getValue();
        mDut.onMessageSendQueuedSuccessResponse(tid);
        mMockLooper.dispatchAll();

        // (8) final Tx results (on-air results)
        mDut.onMessageSendSuccessNotification(tid);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMessageSendSuccess(messageId);
        validateInternalSendMessageQueuesCleanedUp(messageId);

        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative);
    }

    private class SendMessageQueueModelAnswer extends MockAnswerUtil.AnswerWithArguments {
        private final int mMaxQueueDepth;

        // keyed by message ID
        private final Set<Integer> mFailQueueCommandImmediately; // return a false
        private final Set<Integer> mFailQueueCommandLater; // return an error != TX_QUEUE_FULL

        // # of times to return NO_OTA_ACK before returning SUCCESS. So a 0 means success on first
        // try, a very large number means - never succeed (since max retry is 5).
        // a -1 impiles a non-OTA failure: on first attempt
        private final Map<Integer, Integer> mRetryLimit;

        private final LinkedList<Short> mQueue = new LinkedList<>(); // transaction ID (tid)
        private final Map<Short, Integer> mMessageIdsByTid = new HashMap<>(); // tid -> message ID
        private final Map<Integer, Integer> mTriesUsedByMid = new HashMap<>(); // mid -> # of retx

        SendMessageQueueModelAnswer(int maxQueueDepth, Set<Integer> failQueueCommandImmediately,
                Set<Integer> failQueueCommandLater, Map<Integer, Integer> numberOfRetries) {
            mMaxQueueDepth = maxQueueDepth;
            mFailQueueCommandImmediately = failQueueCommandImmediately;
            mFailQueueCommandLater = failQueueCommandLater;
            mRetryLimit = numberOfRetries;

            if (mRetryLimit != null) {
                for (int mid : mRetryLimit.keySet()) {
                    mTriesUsedByMid.put(mid, 0);
                }
            }
        }

        public boolean answer(short transactionId, byte pubSubId, int requestorInstanceId,
                byte[] dest, byte[] message, int messageId) throws Exception {
            if (mFailQueueCommandImmediately != null && mFailQueueCommandImmediately.contains(
                    messageId)) {
                return false;
            }

            if (mFailQueueCommandLater != null && mFailQueueCommandLater.contains(messageId)) {
                mDut.onMessageSendQueuedFailResponse(transactionId, NanStatusCode.INTERNAL_FAILURE);
            } else {
                if (mQueue.size() <= mMaxQueueDepth) {
                    mQueue.addLast(transactionId);
                    mMessageIdsByTid.put(transactionId, messageId);
                    mDut.onMessageSendQueuedSuccessResponse(transactionId);
                } else {
                    mDut.onMessageSendQueuedFailResponse(transactionId,
                            NanStatusCode.FOLLOWUP_TX_QUEUE_FULL);
                }
            }

            return true;
        }

        /**
         * Processes the first message in the queue: i.e. responds as if sent over-the-air
         * (successfully or failed)
         */
        boolean process() {
            if (mQueue.size() == 0) {
                return false;
            }
            short tid = mQueue.poll();
            int mid = mMessageIdsByTid.get(tid);

            if (mRetryLimit != null && mRetryLimit.containsKey(mid)) {
                int numRetries = mRetryLimit.get(mid);
                if (numRetries == -1) {
                    mDut.onMessageSendFailNotification(tid, NanStatusCode.INTERNAL_FAILURE);
                } else {
                    int currentRetries = mTriesUsedByMid.get(mid);
                    if (currentRetries > numRetries) {
                        return false; // shouldn't be retrying!?
                    } else if (currentRetries == numRetries) {
                        mDut.onMessageSendSuccessNotification(tid);
                    } else {
                        mDut.onMessageSendFailNotification(tid, NanStatusCode.NO_OTA_ACK);
                    }
                    mTriesUsedByMid.put(mid, currentRetries + 1);
                }
            } else {
                mDut.onMessageSendSuccessNotification(tid);
            }

            return true;
        }

        /**
         * Returns the number of elements in the queue.
         */
        int queueSize() {
            return mQueue.size();
        }
    }

    private List<OuiKeyedData> generateVendorData(int oui) {
        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(oui, new PersistableBundle()).build();
        return Arrays.asList(vendorDataElement);
    }

    /**
     * Test sequence of configuration: (1) config1, (2) config2 - incompatible,
     * (3) config3 - compatible with config1 (requiring upgrade), (4) disconnect
     * config3 (should get a downgrade), (5) disconnect config1 (should get a
     * disable).
     */
    @Test
    public void testConfigs() throws Exception {
        final int clientId1 = 9999;
        final int clientId2 = 1001;
        final int clientId3 = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int masterPref1 = 111;
        final int masterPref3 = 115;
        final int dwInterval1Band24 = 2;
        final int dwInterval3Band24 = 1;
        final int dwInterval3Band5 = 0;
        final List<OuiKeyedData> vendorData1 = generateVendorData(1);
        final List<OuiKeyedData> vendorData3 = generateVendorData(3);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<ConfigRequest> crCapture = ArgumentCaptor.forClass(ConfigRequest.class);

        ConfigRequest.Builder builder = new ConfigRequest.Builder()
                .setClusterLow(5).setClusterHigh(100)
                .setMasterPreference(masterPref1)
                .setDiscoveryWindowInterval(ConfigRequest.NAN_BAND_24GHZ, dwInterval1Band24);
        if (SdkLevel.isAtLeastV()) {
            builder.setVendorData(vendorData1);
        }
        ConfigRequest configRequest1 = builder.build();

        ConfigRequest configRequest2 = new ConfigRequest.Builder()
                .setSupport5gBand(true) // compatible
                .setSupport6gBand(false)
                .setClusterLow(7).setClusterHigh(155) // incompatible!
                .setMasterPreference(0) // compatible
                .build();

        builder = new ConfigRequest.Builder()
                .setSupport5gBand(true) // compatible (will use true)
                .setSupport6gBand(false)
                .setClusterLow(5).setClusterHigh(100) // identical (hence compatible)
                .setMasterPreference(masterPref3) // compatible (will use max)
                // compatible: will use min
                .setDiscoveryWindowInterval(ConfigRequest.NAN_BAND_24GHZ, dwInterval3Band24)
                // compatible: will use interval3 since interval1 not init
                .setDiscoveryWindowInterval(ConfigRequest.NAN_BAND_5GHZ, dwInterval3Band5);
        if (SdkLevel.isAtLeastV()) {
            builder.setVendorData(vendorData3);
        }
        ConfigRequest configRequest3 = builder.build();

        IWifiAwareEventCallback mockCallback1 = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback2 = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback3 = mock(IWifiAwareEventCallback.class);

        InOrder inOrder = inOrder(mMockNative, mMockNativeManager, mockCallback1, mockCallback2,
                mockCallback3);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) config1 (valid)
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest1, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                crCapture.capture(), eq(false), eq(true), eq(true), eq(false),
                eq(false), eq(false), anyInt(), anyInt());
        collector.checkThat("merge: stage 1", crCapture.getValue(), equalTo(configRequest1));
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback1).onConnectSuccess(clientId1);

        // (2) config2 (incompatible with config1)
        mDut.connect(clientId2, uid, pid, callingPackage, callingFeature, mockCallback2,
                configRequest2, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onConnectFail(NanStatusCode.INTERNAL_FAILURE);
        validateInternalClientInfoCleanedUp(clientId2);

        // (3) config3 (compatible with config1)
        mDut.connect(clientId3, uid, pid, callingPackage, callingFeature, mockCallback3,
                configRequest3, true, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                crCapture.capture(), eq(true), eq(false), eq(true), eq(false),
                eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback3).onConnectSuccess(clientId3);

        collector.checkThat("support 5g: or", true, equalTo(crCapture.getValue().mSupport5gBand));
        collector.checkThat("support 6g: or", false, equalTo(crCapture.getValue().mSupport6gBand));
        collector.checkThat("master preference: max", Math.max(masterPref1, masterPref3),
                equalTo(crCapture.getValue().mMasterPreference));
        collector.checkThat("dw interval on 2.4: ~min",
                Math.min(dwInterval1Band24, dwInterval3Band24),
                equalTo(crCapture.getValue().mDiscoveryWindowInterval[ConfigRequest
                        .NAN_BAND_24GHZ]));
        collector.checkThat("dw interval on 5: ~min", dwInterval3Band5,
                equalTo(crCapture.getValue().mDiscoveryWindowInterval[ConfigRequest
                        .NAN_BAND_5GHZ]));
        if (SdkLevel.isAtLeastV()) {
            // Vendor data from the provided ConfigRequest should be prioritized during the merge
            collector.checkThat("merge: vendor data 3", crCapture.getValue().getVendorData(),
                    equalTo(vendorData3));
        }

        // (4) disconnect config3: downgrade to config1
        mDut.disconnect(clientId3);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback3).onAttachTerminate();
        validateInternalClientInfoCleanedUp(clientId3);
        inOrder.verify(mMockNativeManager).replaceRequestorWs(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                crCapture.capture(), eq(false), eq(false), eq(true), eq(false),
                eq(false), eq(false), anyInt(), anyInt());

        collector.checkThat("configRequest1", configRequest1, equalTo(crCapture.getValue()));

        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());

        // (5) disconnect config1: disable
        mDut.disconnect(clientId1);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onAttachTerminate();
        validateInternalClientInfoCleanedUp(clientId1);
        inOrder.verify(mMockNative).disable(anyShort());
        assertFalse(mDut.isDeviceAttached());

        verifyNoMoreInteractions(mMockNative, mockCallback1, mockCallback2, mockCallback3);
    }

    /**
     * Validate that identical configuration but with different identity callback requirements
     * trigger the correct HAL sequence.
     * 1. Attach w/o identity -> enable
     * 2. Attach w/o identity -> nop
     * 3. Attach w/ identity -> re-configure
     * 4. Attach w/o identity -> nop
     * 5. Attach w/ identity -> nop
     */
    @Test
    public void testConfigsIdentityCallback() throws Exception {
        int clientId = 9999;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        InOrder inOrder = inOrder(mMockNative, mockCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) attach w/o identity
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                any(ConfigRequest.class), eq(false), eq(true), eq(true), eq(false),
                eq(false),
                eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) attach w/o identity
        ++clientId;
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (3) attach w/ identity
        ++clientId;
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, true, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                any(ConfigRequest.class), eq(true), eq(false), eq(true), eq(false), eq(false),
                eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (4) attach w/o identity
        ++clientId;
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (5) attach w/ identity
        ++clientId;
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, true, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        verifyNoMoreInteractions(mMockNative, mockCallback);
    }

    /**
     * Summary: disconnect a client while there are pending transactions.
     */
    @Test
    public void testDisconnectWithPendingTransactions() throws Exception {
        final int clientId = 125;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int clusterLow = 5;
        final int clusterHigh = 100;
        final int masterPref = 111;
        final String serviceName = "some-service-name";
        final String ssi = "some much longer and more arbitrary data";
        final byte publishId = 22;

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref).build();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                serviceName).setServiceSpecificInfo(ssi.getBytes()).setPublishType(
                PublishConfig.PUBLISH_TYPE_UNSOLICITED).build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) publish (no response yet)
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (3) disconnect (but doesn't get executed until get a RESPONSE to the
        // previous publish)
        mDut.disconnect(clientId);
        mMockLooper.dispatchAll();

        // (4) get successful response to the publish
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(anyInt());
        inOrder.verify(mockSessionCallback).onSessionTerminated(anyInt());
        inOrder.verify(mMockNative).stopPublish((short) 0, publishId);
        inOrder.verify(mockCallback).onAttachTerminate();
        inOrder.verify(mMockNative).disable(transactionId.capture());
        validateInternalClientInfoCleanedUp(clientId);
        assertFalse(mDut.isDeviceAttached());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        verify(mMockNativeManager, times(2)).releaseAware();

        // (5) trying to publish on the same client: NOP
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);

        // (6) got some callback on original publishId - should be ignored
        mDut.onSessionTerminatedNotification(publishId, 0, true);
        mMockLooper.dispatchAll();

        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback);
    }

    /**
     * Validate that an unknown transaction (i.e. a callback from HAL with an
     * unknown type) is simply ignored - but also cleans up its state.
     */
    @Test
    public void testUnknownTransactionType() throws Exception {
        final int clientId = 129;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int clusterLow = 15;
        final int clusterHigh = 192;
        final int masterPref = 234;
        final String serviceName = "some-service-name";
        final String ssi = "some much longer and more arbitrary data";

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref).build();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                serviceName).setServiceSpecificInfo(ssi.getBytes()).setPublishType(
                PublishConfig.PUBLISH_TYPE_UNSOLICITED).build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockPublishSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockPublishSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) publish - no response
        mDut.publish(clientId, publishConfig, mockPublishSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        verifyNoMoreInteractions(mMockNative, mockCallback, mockPublishSessionCallback);
    }

    /**
     * Validate that a NoOp transaction (i.e. a callback from HAL which doesn't
     * require any action except clearing up state) actually cleans up its state
     * (and does nothing else).
     */
    @Test
    public void testNoOpTransaction() throws Exception {
        final int clientId = 1294;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect (no response)
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());

        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback);
    }

    /**
     * Validate that getting callbacks from HAL with unknown (expired)
     * transaction ID or invalid publish/subscribe ID session doesn't have any
     * impact.
     */
    @Test
    public void testInvalidCallbackIdParameters() throws Exception {
        final byte pubSubId = 125;
        final int clientId = 132;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect and succeed
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        short transactionIdConfig = transactionId.getValue();
        mDut.onConfigSuccessResponse(transactionIdConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) use the same transaction ID to send a bunch of other responses
        mDut.onConfigSuccessResponse(transactionIdConfig);
        mDut.onConfigFailedResponse(transactionIdConfig, -1);
        mDut.onSessionConfigFailResponse(transactionIdConfig, true, -1);
        mDut.onMessageSendQueuedSuccessResponse(transactionIdConfig);
        mDut.onMessageSendQueuedFailResponse(transactionIdConfig, -1);
        mDut.onSessionConfigFailResponse(transactionIdConfig, false, -1);
        mDut.onMatchNotification(-1, -1, new byte[0], new byte[0], new byte[0], 0, 0, null, 0, null,
                null, null, null);
        mDut.onSessionTerminatedNotification(-1, -1, true);
        mDut.onSessionTerminatedNotification(-1, -1, false);
        mDut.onMessageReceivedNotification(-1, -1, new byte[0], new byte[0]);
        mDut.onSessionConfigSuccessResponse(transactionIdConfig, true, pubSubId);
        mDut.onSessionConfigSuccessResponse(transactionIdConfig, false, pubSubId);
        mMockLooper.dispatchAll();

        verifyNoMoreInteractions(mMockNative, mockCallback);
    }


    /**
     * Verify when attach aware failed, the aware interface will be released.
     */
    @Test
    public void testAttachFailureAndReleaseAware() throws Exception {
        final int clientId = 132;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mMockNativeManager);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect and succeed
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        short transactionIdConfig = transactionId.getValue();
        mDut.onConfigFailedResponse(transactionIdConfig, NanStatusCode.INTERNAL_FAILURE);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).releaseAware();
        inOrder.verify(mockCallback).onConnectFail(NanStatusCode.INTERNAL_FAILURE);


        verifyNoMoreInteractions(mMockNative, mockCallback);
    }

    /**
     * Validate that trying to update-subscribe on a publish session fails.
     */
    @Test
    public void testSubscribeOnPublishSessionType() throws Exception {
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte publishId = 25;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (3) update-subscribe -> failure
        mDut.updateSubscribe(clientId, sessionId.getValue(), subscribeConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);

        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback);
    }

    /**
     * Validate that trying to (re)subscribe on a publish session or (re)publish
     * on a subscribe session fails.
     */
    @Test
    public void testPublishOnSubscribeSessionType() throws Exception {
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte subscribeId = 25;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

        // (3) update-publish -> error
        mDut.updatePublish(clientId, sessionId.getValue(), publishConfig);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);

        verifyNoMoreInteractions(mMockNative, mockCallback, mockSessionCallback);
    }

    /**
     * Validate that the session ID increments monotonically
     */
    @Test
    public void testSessionIdIncrement() throws Exception {
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        int loopCount = 100;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback, mockSessionCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false),
                eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        int prevId = 0;
        for (int i = 0; i < loopCount; ++i) {
            // (2) publish
            mDut.publish(clientId, publishConfig, mockSessionCallback);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                    eq(publishConfig), isNull());

            // (3) publish-success
            mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, (byte) (i + 1));
            mMockLooper.dispatchAll();
            inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());

            if (i != 0) {
                assertTrue("Session ID incrementing", sessionId.getValue() > prevId);
            }
            prevId = sessionId.getValue();
        }
    }

    /**
     * Validate configuration changes on power state changes when Aware is not disabled on doze.
     */
    @Test
    public void testConfigOnPowerStateChanges() throws Exception {
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        setSettableParam(WifiAwareStateManager.PARAM_ON_IDLE_DISABLE_AWARE, Integer.toString(0),
                true);

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockNative, mockCallback);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false),
                eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) power state change: SCREEN OFF
        simulatePowerStateChangeInteractive(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(false), eq(false), eq(false),
                eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (3) power state change: DOZE
        simulatePowerStateChangeDoze(true);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(false), eq(false), eq(true),
                eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (4) restore power state to default
        simulatePowerStateChangeInteractive(true); // effectively treated as no-doze
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(false), eq(true), eq(true),
                eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        verifyNoMoreInteractions(mMockNative, mockCallback);
    }

    /**
     * Validate aware enable/disable during doze transitions.
     */
    @Test
    public void testEnableDisableOnDoze() throws Exception {
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        setSettableParam(WifiAwareStateManager.PARAM_ON_IDLE_DISABLE_AWARE, Integer.toString(1),
                true);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockContext, mMockNativeManager, mMockNative, mockCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).start(any(Handler.class));
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(Process.WIFI_UID));
        inOrder.verify(mMockNativeManager).releaseAware();

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) set the App to isIgnoringBatteryOptimizations to true and power state change: DOZE
        when(mMockPowerManager.isIgnoringBatteryOptimizations(callingPackage)).thenReturn(true);
        simulatePowerStateChangeDoze(true);
        mMockLooper.dispatchAll();
        assertTrue(mDut.isUsageEnabled());
        inOrder.verify(mockCallback, never()).onAttachTerminate();

        // (3) set the App to isIgnoringBatteryOptimizations to false and power state change: DOZE
        when(mMockPowerManager.isIgnoringBatteryOptimizations(callingPackage)).thenReturn(false);
        simulatePowerStateChangeDoze(true);
        mMockLooper.dispatchAll();
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(false));
        inOrder.verify(mockCallback).onAttachTerminate();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrder.verify(mMockNative).disable(transactionId.capture());
        assertFalse(mDut.isDeviceAttached());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).releaseAware();

        // (4) power state change: SCREEN ON (but DOZE still on - fakish but expect no changes)
        simulatePowerStateChangeInteractive(false);
        mMockLooper.dispatchAll();

        // and same for other gating changes -> no changes
        simulateLocationModeChange(false);
        simulateWifiStateChange(false);
        mMockLooper.dispatchAll();

        // and same for other gating changes -> no changes
        simulateLocationModeChange(true);
        simulateWifiStateChange(true);
        mMockLooper.dispatchAll();

        // (5) power state change: DOZE OFF
        simulatePowerStateChangeDoze(false);
        mMockLooper.dispatchAll();
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));
        validateCorrectAwareStatusChangeBroadcast(inOrder);

        verifyNoMoreInteractions(mMockNativeManager, mMockNative, mockCallback);
    }

    /**
     * Validate aware enable/disable during LOCATION MODE transitions on pre-T, Aware should be
     * disabled.
     */
    @Test
    public void testEnableDisableOnLocationModeChangesPreT() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockContext, mMockNativeManager, mMockNative, mockCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).start(any(Handler.class));
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(Process.WIFI_UID));
        inOrder.verify(mMockNativeManager).releaseAware();

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (3) location mode change: disable
        simulateLocationModeChange(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onAttachTerminate();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrder.verify(mMockNative).disable(transactionId.capture());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        assertFalse(mDut.isDeviceAttached());
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(false));
        inOrder.verify(mMockNativeManager).releaseAware();

        // disable other gating feature -> no change
        simulatePowerStateChangeDoze(true);
        simulateWifiStateChange(false);
        mMockLooper.dispatchAll();

        // enable other gating feature -> no change
        simulatePowerStateChangeDoze(false);
        simulateWifiStateChange(true);
        mMockLooper.dispatchAll();

        // (4) location mode change: enable
        simulateLocationModeChange(true);
        mMockLooper.dispatchAll();
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));
        validateCorrectAwareStatusChangeBroadcast(inOrder);

        verifyNoMoreInteractions(mMockNativeManager, mMockNative, mockCallback);
    }

    /**
     * Validate aware enable/disable during LOCATION MODE transitions on T+, Aware will not be
     * disabled, and app with location disavowal will keep session alive.
     */
    @Test
    public void testEnableDisableOnLocationModeChanges() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        final int client1 = 188;
        final int client2 = 199;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final Bundle bundle = new Bundle();
        final AttributionSource attributionSource = mock(AttributionSource.class);
        bundle.putParcelable(WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE, attributionSource);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback2 = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockContext, mMockNativeManager, mMockNative, mockCallback,
                mockCallback2);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).start(any(Handler.class));
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(Process.WIFI_UID));
        inOrder.verify(mMockNativeManager).releaseAware();

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect first client without location disavowal
        mDut.connect(client1, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, bundle, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(client1);

        // (2) connect second client with location disavowal
        mDut.connect(client2, uid, pid, callingPackage, callingFeature, mockCallback2,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onConnectSuccess(client2);
        inOrder.verify(mMockNativeManager).replaceRequestorWs(any());

        // (3) location mode change: disable
        doThrow(new SecurityException()).when(mWifiPermissionsUtil)
                .enforceNearbyDevicesPermission(eq(attributionSource), eq(true), anyString());
        simulateLocationModeChange(false);
        mMockLooper.dispatchAll();
        // client 1 should be terminated
        inOrder.verify(mockCallback).onAttachTerminate();
        inOrder.verify(mMockNativeManager).replaceRequestorWs(any());
        // client 2 is still attached and usage is enabeld.
        assertTrue(mDut.isDeviceAttached());
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(true));
        verifyNoMoreInteractions(mMockNativeManager, mMockNative, mockCallback, mockCallback2);
    }

    /**
     * Validate aware enable/disable during Wi-Fi State transitions.
     */
    @Test
    public void testEnableDisableOnWifiStateChanges() throws Exception {
        final int clientId = 188;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockContext, mMockNativeManager, mMockNative, mockCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).start(any(Handler.class));
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(Process.WIFI_UID));
        inOrder.verify(mMockNativeManager).releaseAware();

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(uid, callingPackage));
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (3) wifi state change: disable
        simulateWifiStateChange(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onAttachTerminate();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrder.verify(mMockNative).disable(transactionId.capture());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).releaseAware();
        assertFalse(mDut.isDeviceAttached());
        collector.checkThat("usage disabled", mDut.isUsageEnabled(), equalTo(false));

        // disable other gating feature -> no change
        simulatePowerStateChangeDoze(true);
        simulateLocationModeChange(false);
        mMockLooper.dispatchAll();

        // enable other gating feature -> no change
        simulatePowerStateChangeDoze(false);
        simulateLocationModeChange(true);
        mMockLooper.dispatchAll();

        // (4) wifi state change: enable
        simulateWifiStateChange(true);
        mMockLooper.dispatchAll();
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));
        validateCorrectAwareStatusChangeBroadcast(inOrder);

        verifyNoMoreInteractions(mMockNativeManager, mMockNative, mockCallback);
    }

    /**
     * Validate aware state change when get aware down from native
     */
    @Test
    public void testWifiAwareStateChangeFromNative() throws Exception {
        final int clientId = 12314;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mockCallback);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);

        // (0) check initial state
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));

        // (1) connect client
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        // (2) Aware down notification from native
        mDut.onAwareDownNotification(NanStatusCode.UNSUPPORTED_CONCURRENCY_NAN_DISABLED);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onAttachTerminate();
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));
        assertFalse(mDut.isDeviceAttached());
        validateCorrectAwareStatusChangeBroadcast(inOrder);

        // (3) try reconnect client
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);

        verifyNoMoreInteractions(mMockNative, mockCallback);
    }

    /**
     * Validate (1) subscribe (success), (2) match (i.e. discovery), (3) discovered peer is no
     * longer visible.
     */
    @Test
    public void testMatchAndLost() throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final String serviceName = "some-service-name";
        final String ssi = "some much longer and more arbitrary data";
        final byte subscribeId = 15;
        final int requestorId = 22;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String peerSsi = "some peer ssi data";
        final String peerMatchFilter = "filter binary array represented as string";
        final String peerMsg = "some message from peer";
        final int messageId = 6948;
        final int messageId2 = 6949;
        final int rangeMin = 0;
        final int rangeMax = 55;
        final int rangedDistance = 30;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(serviceName)
                .setServiceSpecificInfo(ssi.getBytes())
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
                .setMinDistanceMm(rangeMin)
                .setMaxDistanceMm(rangeMax)
                .build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionWithRanging(eq(uid), eq(true),
                eq(rangeMin), eq(rangeMax), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        // Verify reconfigure aware to enable ranging.
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(false), eq(true), eq(false), eq(true), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();


        // (2) 2 matches : with and w/o range
        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), 0, 0, null, 0, null, null, null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), eq(peerSsi.getBytes()),
                eq(peerMatchFilter.getBytes()), anyInt(), isNull(), isNull(), isNull(), isNull());
        inOrderM.verify(mAwareMetricsMock).recordMatchIndicationForRangeEnabledSubscribe(false);
        int peerId1 = peerIdCaptor.getValue();

        mDut.onMatchNotification(subscribeId, requestorId, peerMac, peerSsi.getBytes(),
                peerMatchFilter.getBytes(), EGRESS_MET_MASK, rangedDistance, null, 0, null, null,
                null, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatchWithDistance(peerIdCaptor.capture(),
                eq(peerSsi.getBytes()), eq(peerMatchFilter.getBytes()), eq(rangedDistance),
                anyInt(), isNull(), isNull(), isNull(), isNull());
        inOrderM.verify(mAwareMetricsMock).recordMatchIndicationForRangeEnabledSubscribe(true);
        int peerId2 = peerIdCaptor.getValue();

        assertEquals(peerId1, peerId2);

        // (3) peer is no longer visible.
        mDut.onMatchExpiredNotification(subscribeId, requestorId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatchExpired(peerIdCaptor.capture());
        assertEquals(peerId1, (int) peerIdCaptor.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Test enable and disable instant communication mode.
     * @throws RemoteException
     */
    @Test
    public void testInstantCommunicationMode() throws RemoteException {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) enable instant communication mode without any client
        mDut.enableInstantCommunicationMode(true);
        mMockLooper.dispatchAll();
        verify(mMockNative, never()).enableAndConfigure(anyShort(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyInt(), anyInt());
        assertTrue(mDut.isInstantCommModeGlobalEnable());

        // (1) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(true),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (2) disable instant communication mode
        mDut.enableInstantCommunicationMode(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(false), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        assertFalse(mDut.isInstantCommModeGlobalEnable());
    }

    /**
     * Validate consecutive requests Connect-Disconnect-Connect can be processed in the right order,
     * and the final state is correct.
     */
    @Test
    public void testConnectDisconnectConnectSequence() throws Exception {
        final int clientId = 12341;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<SparseArray> sparseArrayCaptor = ArgumentCaptor.forClass(SparseArray.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mockCallback,
                mMockAwareDataPathStatemanager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) check initial state
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));

        // (2) Placing a connect -> disconnect -> connect sequence
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mDut.disconnect(clientId);
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);

        // (3) Verify the command executed in the correct order
        // (3.1) verify the connect execution
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false),
                any(), eq(6), eq(callingFeature));
        // (3.2) verify the disconnect execution
        inOrder.verify(mockCallback).onAttachTerminate();
        inOrder.verify(mMockAwareDataPathStatemanager).deleteAllInterfaces();
        inOrder.verify(mMockNative).disable(transactionId.capture());
        assertFalse(mDut.isDeviceAttached());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordAttachSessionDuration(anyLong());
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        // (3.3) verify the connect execution again
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false),
                sparseArrayCaptor.capture(), eq(6), eq(callingFeature));
        collector.checkThat("num of clients", sparseArrayCaptor.getValue().size(), equalTo(1));
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mockCallback, mAwareMetricsMock);
    }

    /**
     * Validate consecutive requests DisableUsage-EnableUsage-Connect can be processed in the right
     * order, and the final state is correct.
     */
    @Test
    public void testDisableUsageEnableUsageConnectSequence() throws Exception {
        final int clientId = 12341;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<SparseArray> sparseArrayCaptor = ArgumentCaptor.forClass(SparseArray.class);
        InOrder inOrder = inOrder(mMockContext, mMockNative, mockCallback,
                mMockAwareDataPathStatemanager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        // (1) check initial state
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));

        // (2) Connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false),
                sparseArrayCaptor.capture(), eq(6), eq(callingFeature));
        collector.checkThat("num of clients", sparseArrayCaptor.getValue().size(), equalTo(1));

        // (3) Placing a disableUsage -> enabledUsage -> connect sequence
        mDut.disableUsage(false);
        mDut.enableUsage();
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);

        // (4) Verify the command executed in the correct order
        // (4.1) verify the disable usage execution
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback).onAttachTerminate();
        validateCorrectAwareStatusChangeBroadcast(inOrder);
        inOrder.verify(mMockNative).disable(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordAttachSessionDuration(anyLong());
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        inOrderM.verify(mAwareMetricsMock).recordDisableUsage();
        validateInternalClientInfoCleanedUp(clientId);
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        // (4.2) verify the usage is enabled
        collector.checkThat("usage enabled", mDut.isUsageEnabled(), equalTo(true));
        // (4.3)verify the connect execution again
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false),
                sparseArrayCaptor.capture(), eq(6), eq(callingFeature));
        collector.checkThat("num of clients", sparseArrayCaptor.getValue().size(), equalTo(1));
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mMockNative, mockCallback, mAwareMetricsMock);
    }

    @Test
    public void testInternalStateCleanUp() throws Exception {
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final byte publishId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().setRangingEnabled(true).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, true, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(true), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(true), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionWithRanging(eq(uid), eq(false),
                eq(-1), eq(-1), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        mMockLooper.dispatchAll();
        // Verify reconfigure aware to enable ranging.
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(true), eq(false), eq(true), eq(false), eq(true), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();

        mDut.disconnect(clientId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(NanStatusCode.SUCCESS);
        inOrder.verify(mMockNative).stopPublish(anyShort(), eq(publishId));
        inOrder.verify(mockCallback).onAttachTerminate();
        inOrder.verify(mMockNative).disable(transactionId.capture());
        mDut.onDisableResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrderM.verify(mAwareMetricsMock).recordAttachSessionDuration(anyLong());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(),
                eq(true), anyInt());
        inOrderM.verify(mAwareMetricsMock).recordDisableAware();
        assertFalse(mDut.isDeviceAttached());

        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockCallback, mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validate the connection operation when user approval is required and the user accepts or
     * rejects the request.
     */
    private void runTestConnectUserApproval(
            boolean userAcceptsRequest,
            boolean changedToOpportunistic) throws Exception {
        final int clientId = 1005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<State> mTargetStateCaptor = ArgumentCaptor.forClass(State.class);
        ArgumentCaptor<WaitingState> mWaitingStateCaptor = ArgumentCaptor.forClass(
                WaitingState.class);
        InOrder inOrder = inOrder(mInterfaceConflictManager);

        mDut.enableUsage();
        mMockLooper.dispatchAll();

        // simulate user approval needed
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_NAN), any(), anyBoolean()))
                .thenAnswer(new MockAnswerUtil.AnswerWithArguments() {
                    public int answer(String tag, Message msg, StateMachine stateMachine,
                            WaitingState waitingState, State targetState, int createIfaceType,
                            WorkSource requestorWs,
                            boolean bypassDialog) {
                        stateMachine.deferMessage(msg);
                        stateMachine.transitionTo(waitingState);
                        return InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER;
                    }
                });
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mInterfaceConflictManager).manageInterfaceConflictForStateMachine(any(),
                any(), any(), mWaitingStateCaptor.capture(), mTargetStateCaptor.capture(),
                eq(HalDeviceManager.HDM_CREATE_IFACE_NAN), any(), anyBoolean());

        if (changedToOpportunistic) {
            // calling package changed to opportunistic before the response was received.
            mDut.setOpportunisticPackage(callingPackage, true);
        }

        // simulate user approval triggered and granted/rejected (userAcceptsRequest)
        when(mInterfaceConflictManager.manageInterfaceConflictForStateMachine(any(), any(), any(),
                any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_NAN), any(), anyBoolean()))
                .thenReturn(userAcceptsRequest ? InterfaceConflictManager.ICM_EXECUTE_COMMAND
                        : InterfaceConflictManager.ICM_ABORT_COMMAND);
        mWaitingStateCaptor.getValue().sendTransitionStateCommand(mTargetStateCaptor.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mInterfaceConflictManager).manageInterfaceConflictForStateMachine(any(),
                any(), any(), any(), any(), eq(HalDeviceManager.HDM_CREATE_IFACE_NAN), any(),
                eq(changedToOpportunistic));

        if (userAcceptsRequest) {
            verify(mMockNative).enableAndConfigure(anyShort(), eq(configRequest), eq(false),
                    eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        } else {
            verify(mockCallback).onConnectFail(NanStatusCode.NO_RESOURCES_AVAILABLE);
        }
    }

    @Test
    public void testPublishWithPairingSetup() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte publishId = 15;
        final int peerId = 20;
        final int pairId = 1;
        final int bootstrappingId = 101;
        final byte[] peerMac1 = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String alias = "alias";

        AwarePairingConfig pairingConfig = new AwarePairingConfig(true, true, true,
                AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setPairingConfig(pairingConfig).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext,
                mPairingConfigManager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);
        when(mPairingConfigManager.getNikForCallingPackage(callingPackage)).thenReturn(mNik);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), eq(mNik));

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(1, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(2, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) Receive bootstrapping request
        mDut.onBootstrappingRequestNotification(publishId, peerId, peerMac1, bootstrappingId,
                AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);
        mMockLooper.dispatchAll();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).respondToBootstrappingRequest(transactionId.capture(),
                eq(bootstrappingId), eq(true), eq(publishId));
        mDut.onRespondToBootstrappingIndicationResponseSuccess(transactionId.getValue());
        mMockLooper.dispatchAll();
        verify(mockSessionCallback).onBootstrappingVerificationConfirmed(peerIdCaptor.capture(),
                eq(true), eq(AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN));

        // (4) receive pairing request
        mDut.onPairingRequestNotification(publishId, peerId, peerMac1, pairId,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP, true, null, null);
        mMockLooper.dispatchAll();
        verify(mockSessionCallback).onPairingSetupRequestReceived(peerIdCaptor.capture(),
                eq(pairId));

        // (5) Response the request
        mDut.responseNanPairingSetupRequest(clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                pairId, null, alias, true, WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).respondToPairingRequest(transactionId.capture(), anyInt(),
                eq(true),
                eq(mNik),
                eq(true),
                eq(WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP),
                isNull(),
                isNull(),
                eq(WifiAwareStateManager.NAN_PAIRING_AKM_PASN),
                eq(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));

        // (6) Notify response succeed
        mDut.onRespondToPairingIndicationResponseSuccess(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (7) Receive confirm event
        mDut.onPairingConfirmNotification(pairId, true, NanStatusCode.SUCCESS,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP, true,
                new PairingConfigManager.PairingSecurityAssociationInfo(mPeerNik, mNik, mPmk,
                        WifiAwareStateManager.NAN_PAIRING_AKM_PASN,
                        WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onPairingSetupConfirmed(eq(peerIdCaptor.getValue()),
                eq(true), eq(alias));
        inOrder.verify(mPairingConfigManager).addPairedDeviceSecurityAssociation(eq(callingPackage),
                eq(alias), any(PairingConfigManager.PairingSecurityAssociationInfo.class));

        // (8) publish termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(publishId, reasonTerminate, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(true),
                anyInt());

        // (9) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    @Test
    public void testPublishWithPairingVerification() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte publishId = 15;
        final int peerId = 20;
        final int pairId = 1;
        final byte[] peerMac1 = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String alias = "alias";

        AwarePairingConfig pairingConfig = new AwarePairingConfig(true, true, true,
                AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setPairingConfig(pairingConfig).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext,
                mPairingConfigManager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);
        when(mPairingConfigManager.getNikForCallingPackage(callingPackage)).thenReturn(mNik);
        when(mPairingConfigManager.getPairedDeviceAlias(eq(callingPackage), any(), any(), any()))
                .thenReturn(alias);
        when(mPairingConfigManager.getSecurityInfoPairedDevice(anyString())).thenReturn(
                new PairingConfigManager.PairingSecurityAssociationInfo(mPeerNik, mNik, mPmk,
                        WifiAwareStateManager.NAN_PAIRING_AKM_SAE,
                        WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), eq(mNik));

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(1, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(2, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) receive pairing request
        mDut.onPairingRequestNotification(publishId, peerId, peerMac1, pairId,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION, true, null, null);
        mMockLooper.dispatchAll();
        verify(mockSessionCallback, never()).onPairingSetupRequestReceived(anyInt(),
                eq(pairId));

        // (4) Response the request
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).respondToPairingRequest(transactionId.capture(), eq(pairId),
                eq(true),
                eq(mNik),
                eq(true),
                eq(WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION),
                eq(mPmk),
                isNull(),
                eq(WifiAwareStateManager.NAN_PAIRING_AKM_SAE),
                eq(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));

        // (5) Notify response succeed
        mDut.onRespondToPairingIndicationResponseSuccess(transactionId.getValue());
        mMockLooper.dispatchAll();

        // (6) Receive confirm event
        mDut.onPairingConfirmNotification(pairId, true, NanStatusCode.SUCCESS,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION, true,
                new PairingConfigManager.PairingSecurityAssociationInfo(mPeerNik, mNik, mPmk,
                        WifiAwareStateManager.NAN_PAIRING_AKM_SAE,
                        WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onPairingVerificationConfirmed(anyInt(),
                eq(true), eq(alias));
        inOrder.verify(mPairingConfigManager, never())
                .addPairedDeviceSecurityAssociation(eq(callingPackage),
                eq(alias), any(PairingConfigManager.PairingSecurityAssociationInfo.class));

        // (7) publish termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(publishId, reasonTerminate, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(true),
                anyInt());

        // (8) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    @Test
    public void testSubscribeSWithPairingSetup() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte subscribeId = 15;
        final int peerId = 20;
        final int pairId = 1;
        final int bootstrappingId = 101;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String alias = "alias";

        AwarePairingConfig pairingConfig = new AwarePairingConfig(true, true, true,
                AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setPairingConfig(pairingConfig).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext,
                mPairingConfigManager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();
        when(mPairingConfigManager.getNikForCallingPackage(callingPackage)).thenReturn(mNik);

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), eq(mNik));

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(2, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(1, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) Match peer
        mDut.onMatchNotification(subscribeId, peerId, peerMac, null, null, 0, 0, null, 0, null,
                null, pairingConfig, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), isNull(),
                isNull(), anyInt(), isNull(), isNull(), any(), isNull());

        // (4) Initiate bootstrapping request
        mDut.initiateBootStrappingSetupRequest(clientId, sessionId.getValue(),
                peerIdCaptor.getValue(), AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN, 0, null
        );
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).initiateBootstrapping(transactionId.capture(), eq(peerId),
                eq(peerMac), eq(AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN), isNull(),
                eq(subscribeId), eq(false));
        mDut.onInitiateBootStrappingResponseSuccess(transactionId.getValue(), bootstrappingId);
        mDut.onBootstrappingConfirmNotification(bootstrappingId,
                WifiAwareStateManager.NAN_BOOTSTRAPPING_ACCEPT, NanStatusCode.SUCCESS, 0, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onBootstrappingVerificationConfirmed(
                peerIdCaptor.getValue(), true, AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);

        // (5) Initiate pairing setup request
        mDut.initiateNanPairingSetupRequest(clientId, sessionId.getValue(), peerIdCaptor.getValue(),
                null, alias, WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).initiatePairing(transactionId.capture(), eq(peerId),
                eq(peerMac), eq(mNik), eq(true),
                eq(WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP), isNull(), isNull(),
                eq(WifiAwareStateManager.NAN_PAIRING_AKM_PASN),
                eq(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));

        // (6) request send success and receive confirm
        mDut.onInitiatePairingResponseSuccess(transactionId.getValue(), pairId);
        mMockLooper.dispatchAll();
        mDut.onPairingConfirmNotification(pairId, true, NanStatusCode.SUCCESS,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP, true,
                new PairingConfigManager.PairingSecurityAssociationInfo(mPeerNik, mNik, mPmk,
                        WifiAwareStateManager.NAN_PAIRING_AKM_PASN,
                        WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onPairingSetupConfirmed(eq(peerIdCaptor.getValue()),
                eq(true), eq(alias));
        inOrder.verify(mPairingConfigManager).addPairedDeviceSecurityAssociation(eq(callingPackage),
                eq(alias), any(PairingConfigManager.PairingSecurityAssociationInfo.class));

        // (7) subscribe termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(subscribeId, reasonTerminate, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(false),
                anyInt());

        // (8) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    @Test
    public void testSubscribeSWithPairingVerification() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte subscribeId = 15;
        final int peerId = 20;
        final int pairId = 1;
        final byte[] peerMac1 = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String alias = "alias";

        AwarePairingConfig pairingConfig = new AwarePairingConfig(true, true, true,
                AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setPairingConfig(pairingConfig).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext,
                mPairingConfigManager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();
        when(mPairingConfigManager.getNikForCallingPackage(callingPackage)).thenReturn(mNik);
        when(mPairingConfigManager.getPairedDeviceAlias(eq(callingPackage), any(), any(), any()))
                .thenReturn(alias);
        when(mPairingConfigManager.getSecurityInfoPairedDevice(anyString())).thenReturn(
                new PairingConfigManager.PairingSecurityAssociationInfo(mPeerNik, mNik, mPmk,
                        WifiAwareStateManager.NAN_PAIRING_AKM_SAE,
                        WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), eq(mNik));

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(2, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(1, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) Match peer
        mDut.onMatchNotification(subscribeId, peerId, peerMac1, null, null, 0, 0, null, 0, null,
                null, pairingConfig, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), isNull(),
                isNull(), anyInt(), isNull(), eq(alias), any(), isNull());
        int localPeerId = peerIdCaptor.getValue();

        // (4) Initiate pairing setup request
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).initiatePairing(transactionId.capture(), eq(peerId),
                eq(peerMac1), eq(mNik), eq(true),
                eq(WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION), eq(mPmk), isNull(),
                eq(WifiAwareStateManager.NAN_PAIRING_AKM_SAE),
                eq(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));

        // (5) request send success and receive confirm
        mDut.onInitiatePairingResponseSuccess(transactionId.getValue(), pairId);
        mMockLooper.dispatchAll();
        mDut.onPairingConfirmNotification(pairId, true, NanStatusCode.SUCCESS,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION, true,
                new PairingConfigManager.PairingSecurityAssociationInfo(mPeerNik, mNik, mPmk,
                        WifiAwareStateManager.NAN_PAIRING_AKM_SAE,
                        WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onPairingVerificationConfirmed(eq(localPeerId),
                eq(true), eq(alias));
        inOrder.verify(mPairingConfigManager, never())
                .addPairedDeviceSecurityAssociation(eq(callingPackage), eq(alias),
                        any(PairingConfigManager.PairingSecurityAssociationInfo.class));


        // (6) subscribe termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(subscribeId, reasonTerminate, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(false),
                anyInt());

        // (7) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    @Test
    public void testBootstrappingComeBack() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte subscribeId = 15;
        final int peerId = 20;
        final int pairId = 1;
        final int bootstrappingId = 101;
        final byte[] peerMac = HexEncoding.decode("060708090A0B".toCharArray(), false);
        final String alias = "alias";

        AwarePairingConfig pairingConfig = new AwarePairingConfig(true, true, true,
                AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setPairingConfig(pairingConfig).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> peerIdCaptor = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext,
                mPairingConfigManager);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();
        when(mPairingConfigManager.getNikForCallingPackage(callingPackage)).thenReturn(mNik);

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), eq(mNik));

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(2, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(1, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) Match peer
        mDut.onMatchNotification(subscribeId, peerId, peerMac, null, null, 0, 0, null, 0, null,
                null, pairingConfig, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onMatch(peerIdCaptor.capture(), isNull(),
                isNull(), anyInt(), isNull(), isNull(), any(), isNull());
        // (3) Initiate bootstrapping request

        mDut.initiateBootStrappingSetupRequest(clientId, sessionId.getValue(),
                peerIdCaptor.getValue(), AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN, 0, null
        );
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).initiateBootstrapping(transactionId.capture(), eq(peerId),
                eq(peerMac), eq(AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN), isNull(),
                eq(subscribeId), eq(false));
        mDut.onInitiateBootStrappingResponseSuccess(transactionId.getValue(), bootstrappingId);

        // (4) Receive comeback respond, will send the request again with delay
        mDut.onBootstrappingConfirmNotification(bootstrappingId,
                WifiAwareStateManager.NAN_BOOTSTRAPPING_COMEBACK, NanStatusCode.SUCCESS, 1, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback, never()).onBootstrappingVerificationConfirmed(
                anyInt(), anyBoolean(), anyInt());

        Thread.sleep(1000);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).initiateBootstrapping(transactionId.capture(), eq(peerId),
                eq(peerMac), eq(AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN), isNull(),
                eq(subscribeId), eq(true));
        mDut.onInitiateBootStrappingResponseSuccess(transactionId.getValue(), bootstrappingId + 1);

        // (5) Receive comeback respond on the followup request, will consider reject and notify the
        // app
        mDut.onBootstrappingConfirmNotification(bootstrappingId + 1,
                WifiAwareStateManager.NAN_BOOTSTRAPPING_COMEBACK, NanStatusCode.SUCCESS, 1, null);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onBootstrappingVerificationConfirmed(
                peerIdCaptor.getValue(), false, AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN);

        // (6) subscribe termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(subscribeId, reasonTerminate, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(false),
                anyInt());

        // (7) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    @Test
    public void testPublishWithSuspendResume() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte publishId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PublishConfig publishConfig = new PublishConfig.Builder().setSuspendable(true).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial publish
        mDut.publish(clientId, publishConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).publish(transactionId.capture(), eq((byte) 0),
                eq(publishConfig), isNull());

        // (2) publish success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), true, publishId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(true), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(1, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(2, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) receive suspend request
        mDut.suspend(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).suspendRequest(transactionId.capture(), eq(publishId));

        // (4) notify suspend response succeed
        mDut.onSuspendResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionSuspendSucceeded();

        // (5) event suspension mode changed, device enters suspension
        mDut.onSuspensionModeChangedNotification(true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback, never()).onSessionSuspendSucceeded();

        // (6) receive resume request
        mDut.resume(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).resumeRequest(transactionId.capture(), eq(publishId));

        // (7) notify resume response succeed
        mDut.onResumeResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback, never()).onSessionResumeSucceeded();

        // (8) event suspension mode changed, device exits suspension
        mDut.onSuspensionModeChangedNotification(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionResumeSucceeded();

        // (9) publish termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(publishId, reasonTerminate, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(true),
                anyInt());

        // (10) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    @Test
    public void testSubscribeWithSuspendResume() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        final int clientId = 2005;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reasonTerminate = NanStatusCode.SUCCESS;
        final byte subscribeId = 15;

        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setSuspendable(true).build();

        IWifiAwareEventCallback mockCallback = mock(IWifiAwareEventCallback.class);
        IWifiAwareDiscoverySessionCallback mockSessionCallback = mock(
                IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        InOrder inOrder = inOrder(mockCallback, mockSessionCallback, mMockNative, mMockContext);
        InOrder inOrderM = inOrder(mAwareMetricsMock);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordEnableUsage();

        // (0) connect
        mDut.connect(clientId, uid, pid, callingPackage, callingFeature, mockCallback,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(), eq(configRequest),
                eq(false), eq(true), eq(true), eq(false), eq(false), eq(false), anyInt(), anyInt());
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        assertTrue(mDut.isDeviceAttached());
        inOrder.verify(mockCallback).onConnectSuccess(clientId);
        inOrderM.verify(mAwareMetricsMock).recordAttachSession(eq(uid), eq(false), any(),
                eq(6), eq(callingFeature));

        // (1) initial subscribe
        mDut.subscribe(clientId, subscribeConfig, mockSessionCallback);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq((byte) 0),
                eq(subscribeConfig), isNull());

        // (2) subscribe success
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), false, subscribeId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionStarted(sessionId.capture());
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySession(eq(uid), any());
        inOrderM.verify(mAwareMetricsMock).recordDiscoveryStatus(eq(uid), eq(NanStatusCode.SUCCESS),
                eq(false), anyInt(), eq(6), eq(callingFeature));
        validateCorrectAwareResourcesChangeBroadcast(inOrder);
        assertEquals(2, mDut.getAvailableAwareResources().getAvailablePublishSessionsCount());
        assertEquals(1, mDut.getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        assertEquals(0, mDut.getAvailableAwareResources().getAvailableDataPathsCount());

        // (3) receive suspend request
        mDut.suspend(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).suspendRequest(transactionId.capture(), eq(subscribeId));

        // (4) notify suspend response succeed
        mDut.onSuspendResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionSuspendSucceeded();

        // (5) event suspension mode changed, device enters suspension
        mDut.onSuspensionModeChangedNotification(true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback, never()).onSessionSuspendSucceeded();

        // (6) receive resume request
        mDut.resume(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).resumeRequest(transactionId.capture(), eq(subscribeId));

        // (7) notify resume response succeed
        mDut.onResumeResponse(transactionId.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback, never()).onSessionResumeSucceeded();

        // (8) event suspension mode changed, device exits suspension
        mDut.onSuspensionModeChangedNotification(false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionResumeSucceeded();

        // (9) subscribe termination (from firmware - not app!)
        mDut.onSessionTerminatedNotification(subscribeId, reasonTerminate, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockSessionCallback).onSessionTerminated(reasonTerminate);
        inOrderM.verify(mAwareMetricsMock).recordDiscoverySessionDuration(anyLong(), eq(false),
                anyInt());

        // (10) app terminates session
        mDut.terminateSession(clientId, sessionId.getValue());
        mMockLooper.dispatchAll();

        validateInternalSessionInfoCleanedUp(clientId, sessionId.getValue());
        verify(mAwareMetricsMock, atLeastOnce()).reportAwareInstantModeEnabled(anyBoolean());
        verifyNoMoreInteractions(mockSessionCallback, mMockNative, mAwareMetricsMock);
    }

    /**
     * Validate the connection operation when user approval is required and the user accepts the
     * request.
     */
    @Test
    public void testConnectUserApprovalAccept() throws Exception {
        runTestConnectUserApproval(true, false);
    }

    /**
     * Validate the connection operation when user approval is required and the user rejects the
     * request.
     */
    @Test
    public void testConnectUserApprovalReject() throws Exception {
        runTestConnectUserApproval(false, false);
    }

    /**
     * Validate the connection operation when user approval is required and the user accepts the
     * request after the request changes to opportunistic.
     */
    @Test
    public void testConnectUserApprovalAcceptAfterChangingToOpportunistic() throws Exception {
        runTestConnectUserApproval(true, true);
    }

    /**
     * Validate the connection operation when user approval is required and the user rejects the
     * request after the request changes to opportunistic.
     */
    @Test
    public void testConnectUserApprovalRejectAfterChangingToOpportunistic() throws Exception {
        runTestConnectUserApproval(false, true);
    }

    /*
     * Tests of internal state of WifiAwareStateManager: very limited (not usually
     * a good idea). However, these test that the internal state is cleaned-up
     * appropriately. Alternatively would cause issues with memory leaks or
     * information leak between sessions.
     */

    /**
     * Utility routine used to validate that the internal state is cleaned-up
     * after a client is disconnected. To be used in every test which terminates
     * a client.
     *
     * @param clientId The ID of the client which should be deleted.
     */
    private void validateInternalClientInfoCleanedUp(int clientId) throws Exception {
        WifiAwareClientState client = getInternalClientState(mDut, clientId);
        collector.checkThat("Client record not cleared up for clientId=" + clientId, client,
                nullValue());
    }

    /**
     * Utility routine used to validate that the internal state is cleaned-up
     * (deleted) after a session is terminated through API (not callback!). To
     * be used in every test which terminates a session.
     *
     * @param clientId The ID of the client containing the session.
     * @param sessionId The ID of the terminated session.
     */
    private void validateInternalSessionInfoCleanedUp(int clientId, int sessionId)
            throws Exception {
        WifiAwareClientState client = getInternalClientState(mDut, clientId);
        collector.checkThat("Client record exists clientId=" + clientId, client, notNullValue());
        WifiAwareDiscoverySessionState session = getInternalSessionState(client, sessionId);
        collector.checkThat("Client record not cleaned-up for sessionId=" + sessionId, session,
                nullValue());
    }

    /**
     * Utility routine used to validate that the internal state is cleaned-up
     * (deleted) correctly. Checks that a specific client has no sessions
     * attached to it.
     *
     * @param clientId The ID of the client which we want to check.
     */
    private void validateInternalNoSessions(int clientId) throws Exception {
        WifiAwareClientState client = getInternalClientState(mDut, clientId);
        collector.checkThat("Client record exists clientId=" + clientId, client, notNullValue());

        Field field = WifiAwareClientState.class.getDeclaredField("mSessions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        SparseArray<WifiAwareDiscoverySessionState> sessions =
                (SparseArray<WifiAwareDiscoverySessionState>) field.get(client);

        collector.checkThat("No sessions exist for clientId=" + clientId, sessions.size(),
                equalTo(0));
    }

    /**
     * Validates that the broadcast sent on Aware status change is correct.
     */
    private void validateCorrectAwareStatusChangeBroadcast(InOrder inOrder) {
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);

        inOrder.verify(mMockContext, atLeastOnce()).sendBroadcastAsUser(intent.capture(),
                eq(UserHandle.ALL));

        collector.checkThat("intent action", intent.getValue().getAction(),
                equalTo(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED));
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

    /*
     * Utilities
     */
    private void setSettableParam(String name, String value, boolean expectSuccess) {
        PrintWriter pwMock = mock(PrintWriter.class);
        WifiAwareShellCommand parentShellMock = mock(WifiAwareShellCommand.class);
        when(parentShellMock.getNextArgRequired()).thenReturn("set").thenReturn(name).thenReturn(
                value);
        when(parentShellMock.getErrPrintWriter()).thenReturn(pwMock);

        collector.checkThat(mDut.onCommand(parentShellMock), equalTo(expectSuccess ? 0 : -1));
    }

    private void dumpDut(String prefix) {
        StringWriter sw = new StringWriter();
        mDut.dump(null, new PrintWriter(sw), null);
        Log.e("WifiAwareStateManagerTest", prefix + sw.toString());
    }

    private static void installMocksInStateManager(WifiAwareStateManager awareStateManager,
            WifiAwareDataPathStateManager mockDpMgr)
            throws Exception {
        Field field = WifiAwareStateManager.class.getDeclaredField("mDataPathMgr");
        field.setAccessible(true);
        field.set(awareStateManager, mockDpMgr);
    }

    private static WifiAwareClientState getInternalClientState(WifiAwareStateManager dut,
            int clientId) throws Exception {
        Field field = WifiAwareStateManager.class.getDeclaredField("mClients");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        SparseArray<WifiAwareClientState> clients = (SparseArray<WifiAwareClientState>) field.get(
                dut);

        return clients.get(clientId);
    }

    private static WifiAwareDiscoverySessionState getInternalSessionState(
            WifiAwareClientState client, int sessionId) throws Exception {
        Field field = WifiAwareClientState.class.getDeclaredField("mSessions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        SparseArray<WifiAwareDiscoverySessionState> sessions =
                (SparseArray<WifiAwareDiscoverySessionState>) field.get(client);

        return sessions.get(sessionId);
    }

    private void validateInternalSendMessageQueuesCleanedUp(int messageId) throws Exception {
        Field field = WifiAwareStateManager.class.getDeclaredField("mSm");
        field.setAccessible(true);
        WifiAwareStateManager.WifiAwareStateMachine sm =
                (WifiAwareStateManager.WifiAwareStateMachine) field.get(mDut);

        field = WifiAwareStateManager.WifiAwareStateMachine.class.getDeclaredField(
                "mHostQueuedSendMessages");
        field.setAccessible(true);
        SparseArray<Message> hostQueuedSendMessages = (SparseArray<Message>) field.get(sm);

        field = WifiAwareStateManager.WifiAwareStateMachine.class.getDeclaredField(
                "mFwQueuedSendMessages");
        field.setAccessible(true);
        Map<Short, Message> fwQueuedSendMessages = (Map<Short, Message>) field.get(sm);

        for (int i = 0; i < hostQueuedSendMessages.size(); ++i) {
            Message msg = hostQueuedSendMessages.valueAt(i);
            if (msg.getData().getInt("message_id") == messageId) {
                collector.checkThat(
                        "Message not cleared-up from host queue. Message ID=" + messageId, msg,
                        nullValue());
            }
        }

        for (Message msg: fwQueuedSendMessages.values()) {
            if (msg.getData().getInt("message_id") == messageId) {
                collector.checkThat(
                        "Message not cleared-up from firmware queue. Message ID=" + messageId, msg,
                        nullValue());
            }
        }
    }

    /**
     * Simulate power state change due to doze. Changes the power manager return values and
     * dispatches a broadcast.
     */
    private void simulatePowerStateChangeDoze(boolean isDozeOn) {
        when(mMockPowerManager.isDeviceIdleMode()).thenReturn(isDozeOn);

        Intent intent = new Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        mPowerBcastReceiver.onReceive(mMockContext, intent);
    }

    /**
     * Simulate power state change due to interactive mode change (screen on/off). Changes the power
     * manager return values and dispatches a broadcast.
     */
    private void simulatePowerStateChangeInteractive(boolean isInteractive) {
        when(mMockPowerManager.isInteractive()).thenReturn(isInteractive);

        Intent intent = new Intent(
                isInteractive ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF);
        mPowerBcastReceiver.onReceive(mMockContext, intent);
    }

    /**
     * Simulate Location Mode change. Changes the location manager return values and dispatches a
     * broadcast.
     */
    private void simulateLocationModeChange(boolean isLocationModeEnabled) {
        when(mWifiPermissionsUtil.isLocationModeEnabled()).thenReturn(isLocationModeEnabled);

        Intent intent = new Intent(LocationManager.MODE_CHANGED_ACTION);
        mLocationModeReceiver.onReceive(mMockContext, intent);
    }

    /**
     * Simulate Wi-Fi state change: broadcast state change and modify the API return value.
     */
    private void simulateWifiStateChange(boolean isWifiOn) {
        when(mMockWifiManager.getWifiState()).thenReturn(
                isWifiOn ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);

        Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE,
                isWifiOn ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);
        mWifiStateChangedReceiver.onReceive(mMockContext, intent);
    }

    private static Capabilities getCapabilities() {
        Capabilities cap = new Capabilities();
        cap.maxConcurrentAwareClusters = 1;
        cap.maxPublishes = 2;
        cap.maxSubscribes = 2;
        cap.maxServiceNameLen = 255;
        cap.maxMatchFilterLen = 255;
        cap.maxTotalMatchFilterLen = 255;
        cap.maxServiceSpecificInfoLen = 255;
        cap.maxExtendedServiceSpecificInfoLen = 255;
        cap.maxNdiInterfaces = 1;
        cap.maxNdpSessions = 1;
        cap.maxAppInfoLen = 255;
        cap.maxQueuedTransmitMessages = 6;
        cap.isInstantCommunicationModeSupported = true;
        cap.isSuspensionSupported = true;
        cap.isNanPairingSupported = true;
        cap.isSetClusterIdSupported = true;
        cap.isHeSupported = true;
        cap.is6gSupported = true;
        return cap;
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }

    /**
     * Validates that all events are delivered with correct arguments. Validates
     * that IdentityChanged not delivered if configuration disables delivery.
     */
    @Test
    public void testAwareWithOffloadingWithFwHandlePriority() throws Exception {
        mResources.setBoolean(R.bool.config_wifiAwareOffloadingFirmwareHandlePriority, true);
        final int clientId1 = 1005;
        final int clientId2 = 1007;
        final int clusterLow = 5;
        final int clusterHigh = 100;
        final int masterPref = 111;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reason = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref)
                .build();

        IWifiAwareEventCallback mockCallback1 = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback2 = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionIdCapture = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback1, mockCallback2, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionIdCapture.capture());

        // (1) connect Aware session for offloading
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionIdCapture.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        short transactionId = transactionIdCapture.getValue();
        mDut.onConfigSuccessResponse(transactionId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onConnectSuccess(clientId1);

        // (2) connect Aware session for App
        mDut.connect(clientId2, uid, pid, callingPackage, callingFeature, mockCallback2,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onConnectSuccess(clientId2);
        // Session for offloading should be terminated.
        inOrder.verify(mockCallback1).onAttachTerminate();

        // (3) connect Aware session for offloading again - should reject
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onConnectFail(anyInt());

        // (4) Aware down - session should be terminated.
        mDut.onAwareDownNotification(reason);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onAttachTerminate();

        validateInternalClientInfoCleanedUp(clientId1);
        validateInternalClientInfoCleanedUp(clientId2);

        verifyNoMoreInteractions(mockCallback1, mockCallback2, mMockNative);
    }

    /**
     * Validates that all events are delivered with correct arguments. Validates
     * that IdentityChanged not delivered if configuration disables delivery.
     */
    @Test
    public void testAwareWithOffloadingWithoutFwHandlePriority() throws Exception {
        mResources.setBoolean(R.bool.config_wifiAwareOffloadingFirmwareHandlePriority, false);
        final int clientId1 = 1005;
        final int clientId2 = 1007;
        final int clusterLow = 5;
        final int clusterHigh = 100;
        final int masterPref = 111;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reason = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref)
                .build();

        IWifiAwareEventCallback mockCallback1 = mock(IWifiAwareEventCallback.class);
        IWifiAwareEventCallback mockCallback2 = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionIdCapture = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback1, mockCallback2, mMockNative);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionIdCapture.capture());
        reset(mMockNativeManager);

        // (1) connect Aware session for offloading
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionIdCapture.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        short transactionId = transactionIdCapture.getValue();
        mDut.onConfigSuccessResponse(transactionId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onConnectSuccess(clientId1);

        // (2) connect Aware session for App
        mDut.connect(clientId2, uid, pid, callingPackage, callingFeature, mockCallback2,
                configRequest, true, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).disable(transactionIdCapture.capture());
        mDut.onDisableResponse(transactionIdCapture.getValue(), NanStatusCode.SUCCESS);
        mMockLooper.dispatchAll();
        verify(mMockNativeManager, never()).releaseAware();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionIdCapture.capture(),
                eq(configRequest), eq(true), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        transactionId = transactionIdCapture.getValue();
        mDut.onConfigSuccessResponse(transactionId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onConnectSuccess(clientId2);
        // Session for offloading should be terminated.
        inOrder.verify(mockCallback1).onAttachTerminate();

        // (3) connect Aware session for offloading again - should reject
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, true);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onConnectFail(anyInt());

        // (4) Aware down - session should be terminated.
        mDut.onAwareDownNotification(reason);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback2).onAttachTerminate();

        validateInternalClientInfoCleanedUp(clientId1);
        validateInternalClientInfoCleanedUp(clientId2);

        verifyNoMoreInteractions(mockCallback1, mockCallback2, mMockNative);
    }

    @Test
    public void testAwareWithOpportunisticMode() throws Exception {
        final int clientId1 = 1005;
        final int clusterLow = 5;
        final int clusterHigh = 100;
        final int masterPref = 111;
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.google.somePackage";
        final String callingFeature = "com.google.someFeature";
        final int reason = NanStatusCode.INTERNAL_FAILURE;

        ConfigRequest configRequest = new ConfigRequest.Builder().setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh).setMasterPreference(masterPref)
                .build();

        IWifiAwareEventCallback mockCallback1 = mock(IWifiAwareEventCallback.class);
        ArgumentCaptor<Short> transactionIdCapture = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mockCallback1, mMockNative, mMockNativeManager);
        mDut.setOpportunisticPackage(callingPackage, true);

        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionIdCapture.capture());
        reset(mMockNativeManager);

        // (1) connect Aware session
        mDut.connect(clientId1, uid, pid, callingPackage, callingFeature, mockCallback1,
                configRequest, false, mExtras, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).tryToGetAware(new WorkSource(Process.WIFI_UID));
        inOrder.verify(mMockNative).enableAndConfigure(transactionIdCapture.capture(),
                eq(configRequest), eq(false), eq(true), eq(true), eq(false), eq(false), eq(false),
                anyInt(), anyInt());
        short transactionId = transactionIdCapture.getValue();
        mDut.onConfigSuccessResponse(transactionId);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onConnectSuccess(clientId1);

        // (2) Verify attach status
        IBooleanListener booleanListener = mock(IBooleanListener.class);
        mDut.isOpportunistic(callingPackage, booleanListener);
        mMockLooper.dispatchAll();
        verify(booleanListener).onResult(true);
        reset(booleanListener);

        // (3) Change from opportunistic to normal
        mDut.setOpportunisticPackage(callingPackage, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNativeManager).replaceRequestorWs(new WorkSource(uid, callingPackage));

        // (4) Aware down - session should be terminated.
        mDut.onAwareDownNotification(reason);
        mMockLooper.dispatchAll();
        inOrder.verify(mockCallback1).onAttachTerminate();

        validateInternalClientInfoCleanedUp(clientId1);

        verifyNoMoreInteractions(mockCallback1, mMockNative);
    }
}

