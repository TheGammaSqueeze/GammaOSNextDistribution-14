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

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.ipsec.ike.ike3gpp.Ike3gppParams.PDU_SESSION_ID_UNSET;
import static android.telephony.TelephonyManager.CALL_STATE_IDLE;
import static android.telephony.TelephonyManager.CALL_STATE_RINGING;
import static android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_NR;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_DEACTIVATE_DATA_CALL;
import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_NETWORK_UPDATE_WHEN_TUNNEL_IN_BRINGUP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TelephonyNetworkSpecifier;
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.net.vcn.VcnTransportInfo;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.IDataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;

import com.google.android.iwlan.IwlanDataService.IwlanDataServiceProvider;
import com.google.android.iwlan.IwlanDataService.IwlanDataServiceProvider.IwlanTunnelCallback;
import com.google.android.iwlan.IwlanDataService.IwlanDataServiceProvider.TunnelState;
import com.google.android.iwlan.epdg.EpdgSelector;
import com.google.android.iwlan.epdg.EpdgTunnelManager;
import com.google.android.iwlan.epdg.NetworkSliceSelectionAssistanceInformation;
import com.google.android.iwlan.epdg.TunnelLinkProperties;
import com.google.android.iwlan.epdg.TunnelSetupRequest;
import com.google.android.iwlan.flags.FeatureFlags;
import com.google.android.iwlan.proto.MetricsAtom;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.LongSummaryStatistics;

public class IwlanDataServiceTest {
    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int DEFAULT_SUB_INDEX = 0;
    private static final int INVALID_SUB_INDEX = -1;
    private static final int LINK_MTU = 1280;
    private static final String TEST_APN_NAME = "ims";
    private static final String IP_ADDRESS = "192.0.2.1";
    private static final String DNS_ADDRESS = "8.8.8.8";
    private static final String GATEWAY_ADDRESS = "0.0.0.0";
    private static final String PSCF_ADDRESS = "10.159.204.230";
    private static final String INTERFACE_NAME = "ipsec6";

    @Mock private Context mMockContext;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private DataServiceCallback mMockDataServiceCallback;
    @Mock private EpdgTunnelManager mMockEpdgTunnelManager;
    @Mock private IwlanDataServiceProvider mMockIwlanDataServiceProvider;
    @Mock private Network mMockNetwork;
    @Mock private TunnelLinkProperties mMockTunnelLinkProperties;
    @Mock private ErrorPolicyManager mMockErrorPolicyManager;
    @Mock private ImsManager mMockImsManager;
    @Mock private ImsMmTelManager mMockImsMmTelManager;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private EpdgSelector mMockEpdgSelector;
    @Mock private LinkAddress mMockIPv4LinkAddress;
    @Mock private LinkAddress mMockIPv6LinkAddress;
    @Mock private Inet4Address mMockInet4Address;
    @Mock private Inet6Address mMockInet6Address;
    @Mock private FeatureFlags mFakeFeatureFlags;

    MockitoSession mStaticMockSession;

    private LinkProperties mLinkProperties;
    private List<DataCallResponse> mResultDataCallList;
    private @DataServiceCallback.ResultCode int mResultCode;
    private IwlanDataService mIwlanDataService;
    private IwlanDataServiceProvider mSpyIwlanDataServiceProvider;
    private TestLooper mTestLooper = new TestLooper();
    private long mMockedCalendarTime;
    private ArgumentCaptor<NetworkCallback> mNetworkCallbackCaptor =
            ArgumentCaptor.forClass(NetworkCallback.class);

    private final class IwlanDataServiceCallback extends IDataServiceCallback.Stub {

        private final String mTag;

        IwlanDataServiceCallback(String tag) {
            mTag = tag;
        }

        @Override
        public void onSetupDataCallComplete(
                @DataServiceCallback.ResultCode int resultCode, DataCallResponse response) {}

        @Override
        public void onDeactivateDataCallComplete(@DataServiceCallback.ResultCode int resultCode) {}

        @Override
        public void onSetInitialAttachApnComplete(@DataServiceCallback.ResultCode int resultCode) {}

        @Override
        public void onSetDataProfileComplete(@DataServiceCallback.ResultCode int resultCode) {}

        @Override
        public void onRequestDataCallListComplete(
                @DataServiceCallback.ResultCode int resultCode,
                List<DataCallResponse> dataCallList) {
            mResultCode = resultCode;
            mResultDataCallList = new ArrayList<DataCallResponse>(dataCallList);
        }

        @Override
        public void onDataCallListChanged(List<DataCallResponse> dataCallList) {}

        @Override
        public void onHandoverStarted(@DataServiceCallback.ResultCode int result) {}

        @Override
        public void onHandoverCancelled(@DataServiceCallback.ResultCode int result) {}

        @Override
        public void onApnUnthrottled(String apn) {}

        @Override
        public void onDataProfileUnthrottled(DataProfile dataProfile) {}
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(EpdgSelector.class)
                        .mockStatic(EpdgTunnelManager.class)
                        .mockStatic(ErrorPolicyManager.class)
                        .mockStatic(IwlanBroadcastReceiver.class)
                        .mockStatic(SubscriptionManager.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);
        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);

        doNothing()
                .when(mMockConnectivityManager)
                .registerSystemDefaultNetworkCallback(mNetworkCallbackCaptor.capture(), any());

        when(EpdgTunnelManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX))
                .thenReturn(mMockEpdgTunnelManager);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockSubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(DEFAULT_SUB_INDEX);
        when(mMockSubscriptionManager.getSlotIndex(DEFAULT_SUB_INDEX))
                .thenReturn(DEFAULT_SLOT_INDEX);
        when(mMockSubscriptionManager.getSlotIndex(DEFAULT_SUB_INDEX + 1))
                .thenReturn(DEFAULT_SLOT_INDEX + 1);

        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(DEFAULT_SUB_INDEX);

        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);

        when(mMockTelephonyManager.createForSubscriptionId(eq(DEFAULT_SUB_INDEX)))
                .thenReturn(mMockTelephonyManager);

        when(mMockTelephonyManager.isNetworkRoaming()).thenReturn(false);

        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);

        when(mMockContext.getSystemService(eq(ImsManager.class))).thenReturn(mMockImsManager);

        when(mMockImsManager.getImsMmTelManager(anyInt())).thenReturn(mMockImsMmTelManager);

        when(mMockImsMmTelManager.isVoWiFiSettingEnabled()).thenReturn(false);

        when(EpdgSelector.getSelectorInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockEpdgSelector);

        when(mMockIPv4LinkAddress.getAddress()).thenReturn(mMockInet4Address);
        when(mMockIPv6LinkAddress.getAddress()).thenReturn(mMockInet6Address);

        mIwlanDataService = spy(new IwlanDataService(mFakeFeatureFlags));

        // Injects the test looper into the IwlanDataServiceHandler
        doReturn(mTestLooper.getLooper()).when(mIwlanDataService).getLooper();
        mIwlanDataService.setAppContext(mMockContext);
        mSpyIwlanDataServiceProvider =
                spy(
                        (IwlanDataServiceProvider)
                                mIwlanDataService.onCreateDataServiceProvider(DEFAULT_SLOT_INDEX));
        mTestLooper.dispatchAll();

        when(Calendar.getInstance().getTime()).thenAnswer(i -> mMockedCalendarTime);

        mLinkProperties = new LinkProperties();
        mLinkProperties.setInterfaceName("wlan0");
        mLinkProperties.addLinkAddress(mMockIPv4LinkAddress);

        when(mMockConnectivityManager.getLinkProperties(eq(mMockNetwork)))
                .thenReturn(mLinkProperties);
        when(mMockTunnelLinkProperties.ifaceName()).thenReturn("mockipsec0");

        mockCarrierConfigForN1Mode(true);
    }

    private void moveTimeForwardAndDispatch(long milliSeconds) {
        mTestLooper.moveTimeForward(milliSeconds);
        mTestLooper.dispatchAll();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
        IwlanCarrierConfig.resetTestConfig();
        mSpyIwlanDataServiceProvider.close();
        mTestLooper.dispatchAll();
        if (mIwlanDataService != null) {
            mIwlanDataService.onDestroy();
        }
    }

    public Network createMockNetwork(LinkProperties linkProperties) {
        Network network = mock(Network.class);
        when(mMockConnectivityManager.getLinkProperties(eq(network))).thenReturn(linkProperties);
        return network;
    }

    private NetworkCallback getNetworkMonitorCallback() {
        return mNetworkCallbackCaptor.getValue();
    }

    private void onSystemDefaultNetworkConnected(
            Network network, LinkProperties linkProperties, int transportType, int subId) {
        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        transportType,
                        subId /* unused if transportType is TRANSPORT_WIFI */,
                        false /* isVcn */);
        NetworkCallback networkMonitorCallback = getNetworkMonitorCallback();
        networkMonitorCallback.onCapabilitiesChanged(network, nc);
        networkMonitorCallback.onLinkPropertiesChanged(network, linkProperties);
        mTestLooper.dispatchAll();
    }

    private void onSystemDefaultNetworkConnected(int transportType) {
        Network newNetwork = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork, mLinkProperties, transportType, DEFAULT_SUB_INDEX);
    }

    private void onSystemDefaultNetworkLost() {
        NetworkCallback networkMonitorCallback = getNetworkMonitorCallback();
        networkMonitorCallback.onLost(mMockNetwork);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testWifiOnConnected() {
        onSystemDefaultNetworkConnected(TRANSPORT_WIFI);
        assertTrue(
                mIwlanDataService.isNetworkConnected(
                        false /* isActiveDataOnOtherSub */, false /* isCstEnabled */));
    }

    @Test
    public void testWifiOnLost() {
        when(mMockIwlanDataServiceProvider.getSlotIndex()).thenReturn(DEFAULT_SLOT_INDEX + 1);
        mIwlanDataService.addIwlanDataServiceProvider(mMockIwlanDataServiceProvider);

        onSystemDefaultNetworkLost();
        assertFalse(
                mIwlanDataService.isNetworkConnected(
                        false /* isActiveDataOnOtherSub */, false /* isCstEnabled */));
        verify(mMockIwlanDataServiceProvider).forceCloseTunnelsInDeactivatingState();
        mIwlanDataService.removeDataServiceProvider(mMockIwlanDataServiceProvider);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testWifiOnReconnected() {
        Network newNetwork = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);
        verify(mMockEpdgTunnelManager, times(1)).updateNetwork(eq(newNetwork), eq(mLinkProperties));

        onSystemDefaultNetworkLost();
        onSystemDefaultNetworkConnected(
                newNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);
        verify(mMockEpdgTunnelManager, times(2)).updateNetwork(eq(newNetwork), eq(mLinkProperties));
    }

    @Test
    public void testOnLinkPropertiesChangedForConnectedNetwork() {
        NetworkCallback networkCallback = getNetworkMonitorCallback();
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        clearInvocations(mMockEpdgTunnelManager);

        LinkProperties newLinkProperties = new LinkProperties(mLinkProperties);
        newLinkProperties.setInterfaceName("wlan0");
        newLinkProperties.addLinkAddress(mMockIPv6LinkAddress);

        networkCallback.onLinkPropertiesChanged(mMockNetwork, newLinkProperties);
        verify(mMockEpdgTunnelManager, times(1))
                .updateNetwork(eq(mMockNetwork), eq(newLinkProperties));
    }

    @Test
    public void testOnLinkPropertiesChangedForNonConnectedNetwork() {
        NetworkCallback networkCallback = getNetworkMonitorCallback();
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        clearInvocations(mMockEpdgTunnelManager);

        LinkProperties newLinkProperties = new LinkProperties();
        newLinkProperties.setInterfaceName("wlan0");
        newLinkProperties.addLinkAddress(mMockIPv6LinkAddress);
        Network newNetwork = createMockNetwork(newLinkProperties);

        networkCallback.onLinkPropertiesChanged(newNetwork, newLinkProperties);
        verify(mMockEpdgTunnelManager, never())
                .updateNetwork(eq(newNetwork), any(LinkProperties.class));
    }

    @Test
    public void testOnLinkPropertiesChangedWithClatInstalled() throws Exception {
        NetworkCallback networkCallback = getNetworkMonitorCallback();
        mLinkProperties.setLinkAddresses(
                new ArrayList<>(Collections.singletonList(mMockIPv6LinkAddress)));
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        clearInvocations(mMockEpdgTunnelManager);

        // LinkProperties#addStackedLink() is marked with @UnsupportedAppUsage
        LinkProperties newLinkProperties = new LinkProperties(mLinkProperties);
        newLinkProperties.setInterfaceName("wlan0");
        LinkProperties stackedLink = new LinkProperties();
        stackedLink.setInterfaceName("v4-wlan0");
        stackedLink.addLinkAddress(mMockIPv4LinkAddress);
        Class<?>[] parameterTypes = new Class<?>[] {LinkProperties.class};
        Object[] args = new Object[] {stackedLink};
        callUnsupportedAppUsageMethod(newLinkProperties, "addStackedLink", parameterTypes, args);
        assertNotEquals(mLinkProperties, newLinkProperties);

        networkCallback.onLinkPropertiesChanged(mMockNetwork, newLinkProperties);
        verify(mMockEpdgTunnelManager, times(1))
                .updateNetwork(eq(mMockNetwork), eq(newLinkProperties));
    }

    @Test
    public void testOnLinkPropertiesChangedForBringingUpIkeSession() {
        DataProfile dp = buildImsDataProfile();

        NetworkCallback networkCallback = getNetworkMonitorCallback();
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        clearInvocations(mMockEpdgTunnelManager);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                false /* isHandover */,
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        LinkProperties newLinkProperties = new LinkProperties(mLinkProperties);
        newLinkProperties.setInterfaceName("wlan0");
        newLinkProperties.addLinkAddress(mMockIPv6LinkAddress);

        networkCallback.onLinkPropertiesChanged(mMockNetwork, newLinkProperties);
        verify(mMockEpdgTunnelManager, times(1))
                .updateNetwork(eq(mMockNetwork), eq(newLinkProperties));
        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(any(), anyBoolean(), any(), any(), anyInt());
    }

    @Test
    public void testNetworkNotConnectedWithCellularOnSameSubAndCrossSimEnabled()
            throws InterruptedException {
        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX, false /* isVcn */);
        getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        boolean isActiveDataOnOtherSub =
                mIwlanDataService.isActiveDataOnOtherSub(DEFAULT_SLOT_INDEX);

        assertFalse(isActiveDataOnOtherSub);
        assertFalse(
                mIwlanDataService.isNetworkConnected(
                        isActiveDataOnOtherSub, true /* isCstEnabled */));
    }

    @Test
    public void testCrossSimNetworkConnectedWithCellularOnDifferentSub()
            throws InterruptedException {
        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX + 1, false /* isVcn */);
        getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        boolean isActiveDataOnOtherSub =
                mIwlanDataService.isActiveDataOnOtherSub(DEFAULT_SLOT_INDEX);

        assertTrue(isActiveDataOnOtherSub);
        assertTrue(
                mIwlanDataService.isNetworkConnected(
                        isActiveDataOnOtherSub, true /* isCstEnabled */));
    }

    @Test
    public void testCrossSimNetworkConnectedWithVcnCellularOnDifferentSub()
            throws InterruptedException {
        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX + 1, true /* isVcn */);
        getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        boolean isActiveDataOnOtherSub =
                mIwlanDataService.isActiveDataOnOtherSub(DEFAULT_SLOT_INDEX);

        assertTrue(isActiveDataOnOtherSub);
        assertTrue(
                mIwlanDataService.isNetworkConnected(
                        isActiveDataOnOtherSub, true /* isCstEnabled */));
    }

    @Test
    public void testOnCrossSimCallingEnable_doNotUpdateTunnelManagerIfCellularDataOnSameSub()
            throws Exception {
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        Network newNetwork = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork, mLinkProperties, TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX);

        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT,
                        DEFAULT_SLOT_INDEX,
                        0 /* unused */)
                .sendToTarget();
        mTestLooper.dispatchAll();
        verify(mMockEpdgTunnelManager, never())
                .updateNetwork(eq(newNetwork), any(LinkProperties.class));
    }

    @Test
    public void testOnCrossSimCallingEnable_updateTunnelManagerIfCellularDataOnDifferentSub()
            throws Exception {
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        Network newNetwork = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork, mLinkProperties, TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX + 1);
        verify(mMockEpdgTunnelManager, times(1)).updateNetwork(eq(newNetwork), eq(mLinkProperties));

        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT,
                        DEFAULT_SLOT_INDEX,
                        0 /* unused */)
                .sendToTarget();
        mTestLooper.dispatchAll();
        verify(mMockEpdgTunnelManager, times(2)).updateNetwork(eq(newNetwork), eq(mLinkProperties));
    }

    @Test
    public void testOnCrossSimCallingEnable_doNotUpdateTunnelManagerIfNoNetwork() throws Exception {
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);
        onSystemDefaultNetworkLost();

        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT,
                        DEFAULT_SLOT_INDEX,
                        0 /* unused */)
                .sendToTarget();
        mTestLooper.dispatchAll();
        verify(mMockEpdgTunnelManager, never())
                .updateNetwork(any(Network.class), any(LinkProperties.class));
    }

    @Test
    public void testOnEthernetConnection_doNotUpdateTunnelManager() throws Exception {
        Network newNetwork = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork, mLinkProperties, TRANSPORT_ETHERNET, DEFAULT_SUB_INDEX);
        verify(mMockEpdgTunnelManager, never())
                .updateNetwork(eq(newNetwork), any(LinkProperties.class));
    }

    @Test
    public void testAddDuplicateDataServiceProviderThrows() throws Exception {
        when(mMockIwlanDataServiceProvider.getSlotIndex()).thenReturn(DEFAULT_SLOT_INDEX);
        assertThrows(
                IllegalStateException.class,
                () -> mIwlanDataService.addIwlanDataServiceProvider(mMockIwlanDataServiceProvider));
    }

    @Test
    public void testRemoveDataServiceProvider() {
        when(mMockIwlanDataServiceProvider.getSlotIndex()).thenReturn(DEFAULT_SLOT_INDEX);
        mIwlanDataService.removeDataServiceProvider(mMockIwlanDataServiceProvider);
        mTestLooper.dispatchAll();
        verify(mIwlanDataService, times(1)).deinitNetworkCallback();
        mIwlanDataService.onCreateDataServiceProvider(DEFAULT_SLOT_INDEX);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testRequestDataCallListPass() throws Exception {
        DataProfile dp = buildImsDataProfile();
        List<LinkAddress> mInternalAddressList;
        List<InetAddress> mDNSAddressList;
        List<InetAddress> mGatewayAddressList;
        List<InetAddress> mPCSFAddressList;

        IwlanDataServiceCallback callback = new IwlanDataServiceCallback("requestDataCallList");
        TunnelLinkProperties mLinkProperties = createTunnelLinkProperties();
        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                new DataServiceCallback(callback),
                TunnelState.TUNNEL_UP,
                mLinkProperties,
                false, /* isHandover */
                1, /* pduSessionId */
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);
        mSpyIwlanDataServiceProvider.requestDataCallList(new DataServiceCallback(callback));
        mTestLooper.dispatchAll();

        assertEquals(mResultCode, DataServiceCallback.RESULT_SUCCESS);
        assertEquals(mResultDataCallList.size(), 1);
        for (DataCallResponse dataCallInfo : mResultDataCallList) {
            assertEquals(dataCallInfo.getId(), TEST_APN_NAME.hashCode());
            assertEquals(dataCallInfo.getLinkStatus(), DataCallResponse.LINK_STATUS_ACTIVE);
            assertEquals(dataCallInfo.getProtocolType(), ApnSetting.PROTOCOL_IP);
            assertEquals(dataCallInfo.getInterfaceName(), INTERFACE_NAME);

            mInternalAddressList = dataCallInfo.getAddresses();
            assertEquals(mInternalAddressList.size(), 1);
            for (LinkAddress mLinkAddress : mInternalAddressList) {
                assertEquals(mLinkAddress, new LinkAddress(InetAddress.getByName(IP_ADDRESS), 3));
            }

            mDNSAddressList = dataCallInfo.getDnsAddresses();
            assertEquals(mDNSAddressList.size(), 1);
            for (InetAddress mInetAddress : mDNSAddressList) {
                assertEquals(mInetAddress, InetAddress.getByName(DNS_ADDRESS));
            }

            mGatewayAddressList = dataCallInfo.getGatewayAddresses();
            assertEquals(mGatewayAddressList.size(), 1);
            for (InetAddress mInetAddress : mGatewayAddressList) {
                assertEquals(mInetAddress, Inet4Address.getByName(GATEWAY_ADDRESS));
            }

            mPCSFAddressList = dataCallInfo.getPcscfAddresses();
            assertEquals(mPCSFAddressList.size(), 1);
            for (InetAddress mInetAddress : mPCSFAddressList) {
                assertEquals(mInetAddress, InetAddress.getByName(PSCF_ADDRESS));
            }

            assertEquals(dataCallInfo.getMtuV4(), LINK_MTU);
            assertEquals(dataCallInfo.getMtuV6(), LINK_MTU);
        }
    }

    @Test
    public void testRequestDataCallListEmpty() throws Exception {
        IwlanDataServiceCallback callback = new IwlanDataServiceCallback("requestDataCallList");
        mSpyIwlanDataServiceProvider.requestDataCallList(new DataServiceCallback(callback));
        mTestLooper.dispatchAll();

        assertEquals(mResultCode, DataServiceCallback.RESULT_SUCCESS);
        assertEquals(mResultDataCallList.size(), 0);
    }

    @Test
    public void testIwlanSetupDataCallWithInvalidArg() {
        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.UNKNOWN, /* AccessNetworkType */
                null, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                2, /* pdu session id */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, timeout(1000).times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_ERROR_INVALID_ARG), isNull());
    }

    @Test
    public void testIwlanSetupDataCallWithIllegalState() {
        DataProfile dp = buildImsDataProfile();

        /* Wifi is not connected */
        onSystemDefaultNetworkLost();

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pdu session id */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, timeout(1000).times(1))
                .onSetupDataCallComplete(
                        eq(5 /*DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE */),
                        isNull());
    }

    @Test
    public void testIwlanDeactivateDataCallWithInvalidArg() {
        mSpyIwlanDataServiceProvider.deactivateDataCall(
                0, /* cid */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, timeout(1000).times(1))
                .onDeactivateDataCallComplete(eq(DataServiceCallback.RESULT_ERROR_INVALID_ARG));
    }

    @Test
    public void testIwlanSetupDataCallWithBringUpTunnel() {
        DataProfile dp = buildImsDataProfile();

        /* Wifi is connected */
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        /* Check bringUpTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));

        /* Check callback result is RESULT_SUCCESS when onOpened() is called. */
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onOpened(TEST_APN_NAME, mMockTunnelLinkProperties);
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), any(DataCallResponse.class));
    }

    @Test
    public void testIwlanSetupDataCallWithBringUpTunnelAndNullApnSetting() {
        DataProfile dp = buildImsDataProfileWithEmptyApnSetting();

        /* Wifi is connected */
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_ERROR_INVALID_ARG), isNull());
    }

    @Test
    public void testSliceInfoInclusionInDataCallResponse() throws Exception {
        DataProfile dp = buildImsDataProfile();

        /* Wifi is connected */
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        /* Check bringUpTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));

        /* Check callback result is RESULT_SUCCESS when onOpened() is called. */
        TunnelLinkProperties tp = createTunnelLinkProperties();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);

        mSpyIwlanDataServiceProvider.getIwlanTunnelCallback().onOpened(TEST_APN_NAME, tp);
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());

        /* check that sliceinfo is filled up and matches */
        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        assertNotNull(dataCallResponse.getSliceInfo());
        assertEquals(dataCallResponse.getSliceInfo(), tp.sliceInfo().get());
    }

    @Test
    public void testIwlanDeactivateDataCallWithCloseTunnel() {
        DataProfile dp = buildImsDataProfile();

        onSystemDefaultNetworkConnected(TRANSPORT_WIFI);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                false, /* isHandover */
                1, /* pduSessionId */
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.deactivateDataCall(
                TEST_APN_NAME.hashCode() /* cid: hashcode() of "ims" */,
                DataService.REQUEST_REASON_NORMAL,
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();
        /* Check closeTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(false),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(BRINGDOWN_REASON_DEACTIVATE_DATA_CALL));

        /* Check callback result is RESULT_SUCCESS when onClosed() is called. */
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onDeactivateDataCallComplete(eq(DataServiceCallback.RESULT_SUCCESS));
    }

    @Test
    public void testDeactivateDataCall_ImmediateReleaseAfterHandover() {
        DataProfile dp = buildImsDataProfile();

        onSystemDefaultNetworkConnected(TRANSPORT_WIFI);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_UP,
                null, /* linkProperties */
                false, /* isHandover */
                1, /* pduSessionId */
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.deactivateDataCall(
                TEST_APN_NAME.hashCode() /* cid: hashcode() of "ims" */,
                DataService.REQUEST_REASON_HANDOVER,
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        moveTimeForwardAndDispatch(50);
        /* Check closeTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(true) /* forceClose */,
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(BRINGDOWN_REASON_DEACTIVATE_DATA_CALL));

        /* Check callback result is RESULT_SUCCESS when onClosed() is called. */
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onDeactivateDataCallComplete(eq(DataServiceCallback.RESULT_SUCCESS));
    }

    @Test
    public void testDeactivateDataCall_DelayedReleaseAfterHandover() {
        DataProfile dp = buildImsDataProfile();

        IwlanCarrierConfig.putTestConfigInt(
                IwlanCarrierConfig.KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT, 3);
        onSystemDefaultNetworkConnected(TRANSPORT_WIFI);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_UP,
                null, /* linkProperties */
                false, /* isHandover */
                1, /* pduSessionId */
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.deactivateDataCall(
                TEST_APN_NAME.hashCode() /* cid: hashcode() of "ims" */,
                DataService.REQUEST_REASON_HANDOVER,
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        moveTimeForwardAndDispatch(2950);
        /* Check closeTunnel() is called. */
        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        anyBoolean(),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(BRINGDOWN_REASON_DEACTIVATE_DATA_CALL));

        moveTimeForwardAndDispatch(50);
        /* Check closeTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(true) /* forceClose */,
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(BRINGDOWN_REASON_DEACTIVATE_DATA_CALL));

        /* Check callback result is RESULT_SUCCESS when onClosed() is called. */
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onDeactivateDataCallComplete(eq(DataServiceCallback.RESULT_SUCCESS));
    }

    @Test
    public void testDeactivateDataCall_DelayedReleaseAfterHandover_NetworkReleaseBeforeDelay() {
        DataProfile dp = buildImsDataProfile();

        IwlanCarrierConfig.putTestConfigInt(
                IwlanCarrierConfig.KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT, 3);
        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.NONE);

        onSystemDefaultNetworkConnected(TRANSPORT_WIFI);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_UP,
                null, /* linkProperties */
                false, /* isHandover */
                1, /* pduSessionId */
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, /* type IMS */
                true,
                13, /* LTE */
                false,
                true,
                1 /* Transport WiFi */);

        mSpyIwlanDataServiceProvider.deactivateDataCall(
                TEST_APN_NAME.hashCode() /* cid: hashcode() of "ims" */,
                DataService.REQUEST_REASON_HANDOVER,
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        moveTimeForwardAndDispatch(50);
        /* Check closeTunnel() is called. */
        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        anyBoolean(),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        anyInt());

        /* Check callback result is RESULT_SUCCESS when onClosed() is called. */
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onDeactivateDataCallComplete(eq(DataServiceCallback.RESULT_ERROR_INVALID_ARG));

        moveTimeForwardAndDispatch(4000);

        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        anyBoolean(),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        anyInt());

        // No additional callbacks are involved.
        verify(mMockDataServiceCallback, times(1)).onDeactivateDataCallComplete(anyInt());
    }

    @Test
    public void testHandoverFailureModeDefault() {
        DataProfile dp = buildImsDataProfile();
        int setupDataReason = DataService.REQUEST_REASON_NORMAL;

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getRemainingRetryTimeMs(eq(TEST_APN_NAME))).thenReturn(5L);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.USER_AUTHENTICATION);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                (setupDataReason == DataService.REQUEST_REASON_HANDOVER),
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);

        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());

        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        assertEquals(
                dataCallResponse.getHandoverFailureMode(),
                DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY);
        assertEquals(dataCallResponse.getCause(), DataFailCause.USER_AUTHENTICATION);
        assertEquals(dataCallResponse.getRetryDurationMillis(), 5L);
    }

    @Test
    public void testHandoverFailureModeHandover() {
        DataProfile dp = buildImsDataProfile();
        int setupDataReason = DataService.REQUEST_REASON_HANDOVER;

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getRemainingRetryTimeMs(eq(TEST_APN_NAME))).thenReturn(-1L);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);
        when(mMockErrorPolicyManager.shouldRetryWithInitialAttach(eq(TEST_APN_NAME)))
                .thenReturn(false);

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                (setupDataReason == DataService.REQUEST_REASON_HANDOVER),
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);

        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());

        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        assertEquals(
                dataCallResponse.getHandoverFailureMode(),
                DataCallResponse.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER);
        assertEquals(dataCallResponse.getCause(), DataFailCause.ERROR_UNSPECIFIED);
        assertEquals(dataCallResponse.getRetryDurationMillis(), -1L);
    }

    @Test
    public void testSupportInitialAttachSuccessOnIms() {
        DataProfile dp = buildImsDataProfile();
        int setupDataReason = DataService.REQUEST_REASON_HANDOVER;

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getRemainingRetryTimeMs(eq(TEST_APN_NAME))).thenReturn(-1L);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);
        when(mMockErrorPolicyManager.shouldRetryWithInitialAttach(eq(TEST_APN_NAME)))
                .thenReturn(true);

        // APN = IMS, in idle call state
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        CALL_STATE_IDLE)
                .sendToTarget();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                (setupDataReason == DataService.REQUEST_REASON_HANDOVER),
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());
        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        // Not on video or voice call
        assertEquals(
                DataCallResponse.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL,
                dataCallResponse.getHandoverFailureMode());
    }

    @Test
    public void testSupportInitialAttachSuccessOnEmergency() {
        DataProfile dp = buildDataProfile(ApnSetting.TYPE_EMERGENCY);
        int setupDataReason = DataService.REQUEST_REASON_HANDOVER;

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getRemainingRetryTimeMs(eq(TEST_APN_NAME))).thenReturn(-1L);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);
        when(mMockErrorPolicyManager.shouldRetryWithInitialAttach(eq(TEST_APN_NAME)))
                .thenReturn(true);

        // APN = Emergency, in idle call state
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        CALL_STATE_IDLE)
                .sendToTarget();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                (setupDataReason == DataService.REQUEST_REASON_HANDOVER),
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                512, // type Emergency
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());
        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        // Not on video or voice call
        assertEquals(
                DataCallResponse.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL,
                dataCallResponse.getHandoverFailureMode());
    }

    @Test
    public void testSupportInitialAttachOnImsCall() {
        DataProfile dp = buildImsDataProfile();
        int setupDataReason = DataService.REQUEST_REASON_HANDOVER;

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getRemainingRetryTimeMs(eq(TEST_APN_NAME))).thenReturn(-1L);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);
        when(mMockErrorPolicyManager.shouldRetryWithInitialAttach(eq(TEST_APN_NAME)))
                .thenReturn(true);

        // APN = IMS, in call
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        TelephonyManager.CALL_STATE_OFFHOOK)
                .sendToTarget();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null /* linkProperties */,
                (setupDataReason == DataService.REQUEST_REASON_HANDOVER),
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());
        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        // In call state
        assertEquals(
                DataCallResponse.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER,
                dataCallResponse.getHandoverFailureMode());
    }

    @Test
    public void testSupportInitialAttachOnEmergencyCall() {
        DataProfile dp = buildDataProfile(ApnSetting.TYPE_EMERGENCY);
        int setupDataReason = DataService.REQUEST_REASON_HANDOVER;

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getRemainingRetryTimeMs(eq(TEST_APN_NAME))).thenReturn(-1L);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);
        when(mMockErrorPolicyManager.shouldRetryWithInitialAttach(eq(TEST_APN_NAME)))
                .thenReturn(true);

        // APN = Emergency, in call
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        TelephonyManager.CALL_STATE_OFFHOOK)
                .sendToTarget();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null /* linkProperties */,
                (setupDataReason == DataService.REQUEST_REASON_HANDOVER),
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                512, // type Emergency
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();

        ArgumentCaptor<DataCallResponse> dataCallResponseCaptor =
                ArgumentCaptor.forClass(DataCallResponse.class);
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallResponseCaptor.capture());
        DataCallResponse dataCallResponse = dataCallResponseCaptor.getValue();
        // In call state
        assertEquals(
                DataCallResponse.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER,
                dataCallResponse.getHandoverFailureMode());
    }

    @Test
    public void testDnsPrefetching() throws Exception {
        NetworkCallback networkCallback = getNetworkMonitorCallback();
        /* Wifi is connected */
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);
        networkCallback.onLinkPropertiesChanged(mMockNetwork, mLinkProperties);

        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        0 /* unused */)
                .sendToTarget();

        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.WIFI_CALLING_ENABLE_EVENT,
                        DEFAULT_SLOT_INDEX,
                        0 /* unused */)
                .sendToTarget();
        mTestLooper.dispatchAll();

        LinkProperties newLinkProperties = new LinkProperties();
        newLinkProperties.setInterfaceName("wlan0");
        newLinkProperties.addLinkAddress(mMockIPv4LinkAddress);
        newLinkProperties.addLinkAddress(mMockIPv6LinkAddress);

        networkCallback.onLinkPropertiesChanged(mMockNetwork, newLinkProperties);

        /* Prefetching will be triggered twice.
           1. Network connected, CarrierConfig ready, WifiCallingSetting enabled
           2. Connection ipFamily changed.
        */
        verify(mMockEpdgSelector, times(2))
                .getValidatedServerList(
                        eq(0),
                        eq(EpdgSelector.PROTO_FILTER_IPV4V6),
                        eq(EpdgSelector.SYSTEM_PREFERRED),
                        eq(false),
                        eq(false),
                        eq(mMockNetwork),
                        isNull());
        verify(mMockEpdgSelector, times(2))
                .getValidatedServerList(
                        eq(0),
                        eq(EpdgSelector.PROTO_FILTER_IPV4V6),
                        eq(EpdgSelector.SYSTEM_PREFERRED),
                        eq(false),
                        eq(true),
                        eq(mMockNetwork),
                        isNull());
    }

    private void advanceCalendarByTimeMs(long time, Calendar calendar) {
        mMockedCalendarTime += time;
        if (calendar != null) {
            calendar.setTimeInMillis(mMockedCalendarTime);
        }
        mTestLooper.dispatchAll();
    }

    private DataProfile buildImsDataProfileWithEmptyApnSetting() {
        return new DataProfile.Builder()
                .setTrafficDescriptor(
                        new TrafficDescriptor.Builder().setDataNetworkName("").build())
                .setType(1)
                .enable(true)
                .setPreferred(true)
                .build();
    }

    private DataProfile buildImsDataProfile() {
        return buildDataProfile(ApnSetting.TYPE_IMS);
    }

    private DataProfile buildDataProfile(int supportedApnTypesBitmask) {
        return new DataProfile.Builder()
                .setApnSetting(
                        new ApnSetting.Builder()
                                .setProfileId(1)
                                .setEntryName(TEST_APN_NAME)
                                .setApnName(TEST_APN_NAME)
                                .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                                .setAuthType(ApnSetting.AUTH_TYPE_NONE)
                                .setUser("")
                                .setPassword("")
                                .setApnTypeBitmask(supportedApnTypesBitmask)
                                .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                                .setNetworkTypeBitmask(
                                        (int) TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN)
                                .setPersistent(true)
                                .build())
                .setType(1) // 3gpp
                .enable(true)
                .setPreferred(true)
                .build();
    }

    private NetworkCapabilities prepareNetworkCapabilitiesForTest(
            int transportType, int subId, boolean isVcn) {
        NetworkCapabilities.Builder builder =
                new NetworkCapabilities.Builder().addTransportType(transportType);
        if (isVcn) {
            builder.setTransportInfo(new VcnTransportInfo(subId));
        } else {
            builder.setNetworkSpecifier(new TelephonyNetworkSpecifier(subId));
        }
        return builder.build();
    }

    @Test
    public void testIwlanSetupDataCallFailsWithCellularAndCstDisabled() throws Exception {
        DataProfile dp = buildImsDataProfile();
        /* CST is disabled, and data is on the same sub as the data service provider */
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(false);

        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX, false /* isVcn */);
        getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pdu session id */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, timeout(1000).times(1))
                .onSetupDataCallComplete(
                        eq(5 /* DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE */),
                        isNull());
    }

    @Test
    public void testIwlanSetupDataCallFailsWithCellularOnSameSubAndCstEnabled() throws Exception {
        DataProfile dp = buildImsDataProfile();

        /* CST is enabled, but data is on the same sub as the DataServiceProvider */
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX, false /* isVcn */);
        getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, timeout(1000).times(1))
                .onSetupDataCallComplete(
                        eq(5 /* DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE */),
                        isNull());
    }

    @Test
    public void testIwlanSetupDataCallSucceedsWithCellularOnDifferentSubAndCstEnabled()
            throws Exception {
        DataProfile dp = buildImsDataProfile();

        /* CST is enabled, but data is on the same sub as the DataServiceProvider */
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);

        NetworkCapabilities nc =
                prepareNetworkCapabilitiesForTest(
                        TRANSPORT_CELLULAR, DEFAULT_SUB_INDEX + 1, false /* isVcn */);
        getNetworkMonitorCallback().onCapabilitiesChanged(mMockNetwork, nc);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        /* Check bringUpTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));

        /* Check callback result is RESULT_SUCCESS when onOpened() is called. */
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onOpened(TEST_APN_NAME, mMockTunnelLinkProperties);
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, times(1))
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), any(DataCallResponse.class));
    }

    @Test
    public void testIwlanTunnelStatsFailureCounts() {
        DataProfile dp = buildImsDataProfile();

        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);

        long count = 3L;
        for (int i = 0; i < count; i++) {
            mockTunnelSetupFail(dp);
            mTestLooper.dispatchAll();
        }

        IwlanDataServiceProvider.IwlanDataTunnelStats stats =
                mSpyIwlanDataServiceProvider.getTunnelStats();
        long result = stats.mTunnelSetupFailureCounts.get(TEST_APN_NAME);
        assertEquals(result, count);
    }

    @Test
    public void testIwlanTunnelStatsUnsolDownCounts() {
        DataProfile dp = buildImsDataProfile();

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);

        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        long count = 3L;
        for (int i = 0; i < count; i++) {
            mockTunnelSetupSuccess(dp, 0, null);
            mockUnsolTunnelDown();
        }

        IwlanDataServiceProvider.IwlanDataTunnelStats stats =
                mSpyIwlanDataServiceProvider.getTunnelStats();
        long result = stats.mUnsolTunnelDownCounts.get(TEST_APN_NAME);
        assertEquals(result, count);
    }

    @Test
    public void testIwlanTunnelStats() {
        DataProfile dp = buildImsDataProfile();
        Calendar calendar = mock(Calendar.class);
        when(calendar.getTime()).thenAnswer(i -> new Date(mMockedCalendarTime));

        mSpyIwlanDataServiceProvider.setCalendar(calendar);
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);

        LongSummaryStatistics tunnelSetupSuccessStats = new LongSummaryStatistics();
        LongSummaryStatistics tunnelUpStats = new LongSummaryStatistics();

        Date beforeSetup = calendar.getTime();
        mockTunnelSetupSuccess(dp, 0, calendar);
        Date tunnelUp = calendar.getTime();
        mockDeactivateTunnel(0, calendar);
        Date tunnelDown = calendar.getTime();
        tunnelSetupSuccessStats.accept(tunnelUp.getTime() - beforeSetup.getTime());
        tunnelUpStats.accept(tunnelDown.getTime() - tunnelUp.getTime());

        beforeSetup = calendar.getTime();
        mockTunnelSetupSuccess(dp, 1000, calendar);
        tunnelUp = calendar.getTime();
        mockDeactivateTunnel(3000, calendar);
        tunnelDown = calendar.getTime();
        tunnelSetupSuccessStats.accept(tunnelUp.getTime() - beforeSetup.getTime());
        tunnelUpStats.accept(tunnelDown.getTime() - tunnelUp.getTime());

        beforeSetup = calendar.getTime();
        mockTunnelSetupSuccess(dp, 600, calendar);
        tunnelUp = calendar.getTime();
        mockDeactivateTunnel(500, calendar);
        tunnelDown = calendar.getTime();
        tunnelSetupSuccessStats.accept(tunnelUp.getTime() - beforeSetup.getTime());
        tunnelUpStats.accept(tunnelDown.getTime() - tunnelUp.getTime());

        IwlanDataServiceProvider.IwlanDataTunnelStats stats =
                mSpyIwlanDataServiceProvider.getTunnelStats();
        LongSummaryStatistics finalSetupStats = stats.mTunnelSetupSuccessStats.get(TEST_APN_NAME);
        LongSummaryStatistics finalUpStats = stats.mTunnelUpStats.get(TEST_APN_NAME);

        assertEquals(tunnelSetupSuccessStats.getAverage(), finalSetupStats.getAverage(), 0);
        assertEquals(tunnelSetupSuccessStats.getCount(), finalSetupStats.getCount());
        assertEquals(tunnelSetupSuccessStats.getMax(), finalSetupStats.getMax(), 0);

        assertEquals(tunnelUpStats.getAverage(), finalUpStats.getAverage(), 0);
        assertEquals(tunnelUpStats.getCount(), finalUpStats.getCount());
        assertEquals(tunnelUpStats.getMax(), finalUpStats.getMax(), 0);
    }

    @Test
    public void testUnexpectedTunnelClosedIsSuppressed() {
        mockUnsolTunnelDown();
    }

    @Test
    public void testIwlanDataServiceHandlerOnUnbind() {
        DataProfile dp = buildImsDataProfile();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_UP,
                null /* linkProperties */,
                false /* isHandover */,
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);

        // Simulate IwlanDataService.onUnbind() which force close all tunnels
        mSpyIwlanDataServiceProvider.forceCloseTunnels(EpdgTunnelManager.BRINGDOWN_REASON_UNKNOWN);
        // Simulate DataService.onUnbind() which remove all IwlanDataServiceProviders
        mSpyIwlanDataServiceProvider.close();
        mTestLooper.dispatchAll();

        verify(mMockEpdgTunnelManager, atLeastOnce())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(true),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(EpdgTunnelManager.BRINGDOWN_REASON_UNKNOWN));
        assertNotNull(mIwlanDataService.mIwlanDataServiceHandler);
        // Should not raise NullPointerException
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();
    }

    @Test
    public void testBackToBackOnBindAndOnUnbindDoesNotThrow() {
        mIwlanDataService.onBind(null);
        mIwlanDataService.onUnbind(null);
    }

    @Test
    public void testMetricsWhenTunnelClosedWithWrappedException() {
        DataProfile dp = buildImsDataProfile();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                false /* isHandover */,
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        MetricsAtom metricsAtom = mSpyIwlanDataServiceProvider.getMetricsAtomByApn(TEST_APN_NAME);
        assertNotNull(metricsAtom);

        String exceptionMessage = "Some exception message";
        Exception mockException = spy(new IllegalStateException(exceptionMessage));
        String firstDeclaringClassName = "test.test.TestClass";
        String firstMethodName = "someMethod";
        String firstFileName = "TestClass.java";
        int firstLineNumber = 12345;
        StackTraceElement[] stackTraceElements = {
            new StackTraceElement(
                    firstDeclaringClassName, firstMethodName, firstFileName, firstLineNumber),
            new StackTraceElement("test", "test", "test.java", 123)
        };
        doReturn(stackTraceElements).when(mockException).getStackTrace();

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(new IkeInternalException(mockException)));

        mTestLooper.dispatchAll();

        var expectedStackFirstFrame =
                firstDeclaringClassName
                        + "."
                        + firstMethodName
                        + "("
                        + firstFileName
                        + ":"
                        + firstLineNumber
                        + ")";

        assertEquals(
                mockException.getClass().getCanonicalName(),
                metricsAtom.getIwlanErrorWrappedClassname());

        assertEquals(expectedStackFirstFrame, metricsAtom.getIwlanErrorWrappedStackFirstFrame());
    }

    @Test
    public void testMetricsWhenTunnelClosedWithoutWrappedException() {
        DataProfile dp = buildImsDataProfile();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                false /* isHandover */,
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, // type IMS
                true,
                13, // LTE
                false,
                true,
                1 // Transport Wi-Fi
                );

        MetricsAtom metricsAtom = mSpyIwlanDataServiceProvider.getMetricsAtomByApn(TEST_APN_NAME);
        assertNotNull(metricsAtom);

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(
                        TEST_APN_NAME,
                        new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED));

        mTestLooper.dispatchAll();

        assertEquals(null, metricsAtom.getIwlanErrorWrappedClassname());
        assertEquals(null, metricsAtom.getIwlanErrorWrappedStackFirstFrame());
    }

    @Test
    public void testMetricsWhenTunnelClosedWithErrorCount() {
        DataProfile dp = buildImsDataProfile();

        mSpyIwlanDataServiceProvider.setTunnelState(
                dp,
                mMockDataServiceCallback,
                TunnelState.TUNNEL_IN_BRINGUP,
                null, /* linkProperties */
                false /* isHandover */,
                1 /* pduSessionId */,
                true /* isImsOrEmergency */,
                true /* isDataCallSetupWithN1 */);

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME,
                64, /* type IMS */
                true,
                13, /* LTE */
                false,
                true,
                1 /* Transport Wi-Fi */);

        MetricsAtom metricsAtom = mSpyIwlanDataServiceProvider.getMetricsAtomByApn(TEST_APN_NAME);
        assertNotNull(metricsAtom);

        when(ErrorPolicyManager.getInstance(eq(mMockContext), eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockErrorPolicyManager);
        when(mMockErrorPolicyManager.getDataFailCause(eq(TEST_APN_NAME)))
                .thenReturn(DataFailCause.ERROR_UNSPECIFIED);

        when(mMockErrorPolicyManager.getLastErrorCountOfSameCause(eq(TEST_APN_NAME))).thenReturn(5);

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.IKE_PROTOCOL_EXCEPTION));
        mTestLooper.dispatchAll();

        assertEquals(5, metricsAtom.getErrorCountOfSameCause());
    }

    private void mockTunnelSetupFail(DataProfile dp) {
        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        doReturn(true)
                .when(mMockEpdgTunnelManager)
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.IKE_INTERNAL_IO_EXCEPTION));
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, atLeastOnce())
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), any(DataCallResponse.class));
    }

    private void mockTunnelSetupSuccess(DataProfile dp, long setupTime, Calendar calendar) {
        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        doReturn(true)
                .when(mMockEpdgTunnelManager)
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));
        mTestLooper.dispatchAll();

        advanceCalendarByTimeMs(setupTime, calendar);

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onOpened(TEST_APN_NAME, mMockTunnelLinkProperties);
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, atLeastOnce())
                .onSetupDataCallComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), any(DataCallResponse.class));
    }

    private void mockUnsolTunnelDown() {
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.IKE_INTERNAL_IO_EXCEPTION));
        mTestLooper.dispatchAll();
    }

    private void mockDeactivateTunnel(long deactivationTime, Calendar calendar) {
        mSpyIwlanDataServiceProvider.deactivateDataCall(
                TEST_APN_NAME.hashCode() /* cid: hashcode() of "ims" */,
                DataService.REQUEST_REASON_NORMAL /* DataService.REQUEST_REASON_NORMAL */,
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();
        verify(mMockEpdgTunnelManager, atLeastOnce())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        anyBoolean(),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(BRINGDOWN_REASON_DEACTIVATE_DATA_CALL));

        advanceCalendarByTimeMs(deactivationTime, calendar);

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onClosed(TEST_APN_NAME, new IwlanError(IwlanError.NO_ERROR));
        mTestLooper.dispatchAll();
        verify(mMockDataServiceCallback, atLeastOnce())
                .onDeactivateDataCallComplete(eq(DataServiceCallback.RESULT_SUCCESS));
    }

    private Object callUnsupportedAppUsageMethod(
            Object target, String methodName, Class<?>[] parameterTypes, Object[] args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @Test
    public void testNetworkChangeDuringTunnelBringUp_closeTunnel() {
        DataProfile dp = buildImsDataProfile();
        Network newNetwork1 = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork1, mLinkProperties, TRANSPORT_WIFI, DEFAULT_SUB_INDEX);

        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                true, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                1, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        /* Check bringUpTunnel() is called. */
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));

        Network newNetwork2 = createMockNetwork(mLinkProperties);
        onSystemDefaultNetworkConnected(
                newNetwork2, mLinkProperties, TRANSPORT_WIFI, DEFAULT_SUB_INDEX);
        verify(mMockEpdgTunnelManager, times(1))
                .closeTunnel(
                        any(),
                        anyBoolean(),
                        any(),
                        any(),
                        eq(BRINGDOWN_REASON_NETWORK_UPDATE_WHEN_TUNNEL_IN_BRINGUP));
    }

    public static TunnelLinkProperties createTunnelLinkProperties() throws Exception {
        final String IP_ADDRESS = "192.0.2.1";
        final String DNS_ADDRESS = "8.8.8.8";
        final String PSCF_ADDRESS = "10.159.204.230";
        final String INTERFACE_NAME = "ipsec6";
        final NetworkSliceInfo SLICE_INFO =
                NetworkSliceSelectionAssistanceInformation.getSliceInfo(new byte[] {1});

        List<LinkAddress> mInternalAddressList = new ArrayList<>();
        List<InetAddress> mDNSAddressList = new ArrayList<>();
        List<InetAddress> mPCSFAddressList = new ArrayList<>();

        mInternalAddressList.add(new LinkAddress(InetAddress.getByName(IP_ADDRESS), 3));
        mDNSAddressList.add(InetAddress.getByName(DNS_ADDRESS));
        mPCSFAddressList.add(InetAddress.getByName(PSCF_ADDRESS));

        return TunnelLinkProperties.builder()
                .setInternalAddresses(mInternalAddressList)
                .setDnsAddresses(mDNSAddressList)
                .setPcscfAddresses(mPCSFAddressList)
                .setIfaceName(INTERFACE_NAME)
                .setSliceInfo(SLICE_INFO)
                .build();
    }

    private void mockCarrierConfigForN1Mode(boolean supportN1Mode) {
        PersistableBundle bundle = new PersistableBundle();
        if (supportN1Mode) {
            bundle.putIntArray(
                    CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                    new int[] {
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA
                    });
        } else {
            bundle.putIntArray(
                    CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                    new int[] {CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA});
        }
        IwlanCarrierConfig.putTestConfigBundle(bundle);
    }

    private void mockCallState(int callState) {
        onSystemDefaultNetworkConnected(TRANSPORT_CELLULAR);

        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT, DEFAULT_SLOT_INDEX, callState)
                .sendToTarget();

        mSpyIwlanDataServiceProvider.setMetricsAtom(
                TEST_APN_NAME, 64, true, TelephonyManager.NETWORK_TYPE_LTE, false, true, 1);
    }

    private void updatePreferredNetworkType(long networkTypeBitmask) {
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.PREFERRED_NETWORK_TYPE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        0 /* unused */,
                        networkTypeBitmask)
                .sendToTarget();
        mTestLooper.dispatchAll();
    }

    @Test
    public void testIsN1ModeSupported() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                    CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA
                });
        IwlanCarrierConfig.putTestConfigBundle(bundle);
        assertTrue(mSpyIwlanDataServiceProvider.isN1ModeSupported());

        bundle.putIntArray(
                CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                });
        IwlanCarrierConfig.putTestConfigBundle(bundle);
        assertFalse(mSpyIwlanDataServiceProvider.isN1ModeSupported());
    }

    @Test
    public void testMultipleAllowedNetworkTypeChangeInIdle_updateN1Mode() throws Exception {
        mockCarrierConfigForN1Mode(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, true);
        mockCallState(CALL_STATE_IDLE);
        mockSetupDataCallWithPduSessionId(0);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);

        /* Check closeTunnel() is called. */
        verify(mMockEpdgTunnelManager, atLeastOnce())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(true),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(EpdgTunnelManager.BRINGDOWN_REASON_ENABLE_N1_MODE));

        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_LTE);

        verify(mMockEpdgTunnelManager, atLeastOnce())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(true),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(EpdgTunnelManager.BRINGDOWN_REASON_DISABLE_N1_MODE));
    }

    @Test
    public void testMultipleAllowedNetworkTypeChangeInCall_preferenceChanged_updateAfterCallEnds()
            throws Exception {
        mockCarrierConfigForN1Mode(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, true);

        mockCallState(CALL_STATE_RINGING);
        mockSetupDataCallWithPduSessionId(0);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_LTE);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);

        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(any(), anyBoolean(), any(), any(), anyInt());

        // in idle call state
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        CALL_STATE_IDLE)
                .sendToTarget();
        mTestLooper.dispatchAll();

        verify(mMockEpdgTunnelManager, atLeastOnce())
                .closeTunnel(
                        eq(TEST_APN_NAME),
                        eq(true),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class),
                        eq(EpdgTunnelManager.BRINGDOWN_REASON_ENABLE_N1_MODE));
    }

    @Test
    public void testMultipleAllowedNetworkTypeChangeInCall_preferenceNotChanged_noUpdate()
            throws Exception {
        mockCarrierConfigForN1Mode(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, true);

        mockCallState(CALL_STATE_RINGING);
        mockSetupDataCallWithPduSessionId(0);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_LTE);

        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(any(), anyBoolean(), any(), any(), anyInt());

        // in idle call state
        mIwlanDataService
                .mIwlanDataServiceHandler
                .obtainMessage(
                        IwlanEventListener.CALL_STATE_CHANGED_EVENT,
                        DEFAULT_SLOT_INDEX,
                        CALL_STATE_IDLE)
                .sendToTarget();
        mTestLooper.dispatchAll();

        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(any(), anyBoolean(), any(), any(), anyInt());
    }

    @Test
    public void testOnAllowedNetworkTypeChange_flagDisabled_noTunnelClose() {
        mockCarrierConfigForN1Mode(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, false);

        mockCallState(CALL_STATE_IDLE);
        mockSetupDataCallWithPduSessionId(0);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);

        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(any(), anyBoolean(), any(), any(), anyInt());
    }

    @Test
    public void testOnAllowedNetworkTypeChange_n1ModeNotSupported_noTunnelClose() {
        mockCarrierConfigForN1Mode(false);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, true);

        mockCallState(CALL_STATE_IDLE);
        mockSetupDataCallWithPduSessionId(0);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);

        verify(mMockEpdgTunnelManager, never())
                .closeTunnel(any(), anyBoolean(), any(), any(), anyInt());
    }

    @Test
    public void testN1ModeNotSupported_tunnelBringupWithNoN1ModeCapability() {
        mockCarrierConfigForN1Mode(false);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, true);

        mockSetupDataCallWithPduSessionId(1);

        ArgumentCaptor<TunnelSetupRequest> tunnelSetupRequestCaptor =
                ArgumentCaptor.forClass(TunnelSetupRequest.class);
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(tunnelSetupRequestCaptor.capture(), any(), any());
        TunnelSetupRequest tunnelSetupRequest = tunnelSetupRequestCaptor.getValue();
        assertEquals(PDU_SESSION_ID_UNSET, tunnelSetupRequest.getPduSessionId());
    }

    @Test
    public void testNoN1ModeCapabilityInOngoingDataCall_newTunnelBringup_doNotIncludeN1() {
        mockCarrierConfigForN1Mode(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, true);

        mockSetupDataCallWithPduSessionId(0);

        ArgumentCaptor<TunnelSetupRequest> tunnelSetupRequestCaptor =
                ArgumentCaptor.forClass(TunnelSetupRequest.class);
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(tunnelSetupRequestCaptor.capture(), any(), any());
        TunnelSetupRequest tunnelSetupRequest = tunnelSetupRequestCaptor.getValue();
        assertEquals(PDU_SESSION_ID_UNSET, tunnelSetupRequest.getPduSessionId());

        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);
        mockSetupDataCallWithPduSessionId(1);

        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(tunnelSetupRequestCaptor.capture(), any(), any());
        tunnelSetupRequest = tunnelSetupRequestCaptor.getValue();
        assertEquals(PDU_SESSION_ID_UNSET, tunnelSetupRequest.getPduSessionId());
    }

    private void mockSetupDataCallWithPduSessionId(int pduSessionId) {
        DataProfile dp = buildImsDataProfile();

        verifySetupDataCallRequestHandled(pduSessionId, dp);
    }

    private void verifySetupDataCallRequestHandled(int pduSessionId, DataProfile dp) {
        onSystemDefaultNetworkConnected(
                mMockNetwork, mLinkProperties, TRANSPORT_WIFI, INVALID_SUB_INDEX);
        mSpyIwlanDataServiceProvider.setupDataCall(
                AccessNetworkType.IWLAN, /* AccessNetworkType */
                dp, /* dataProfile */
                false, /* isRoaming */
                false, /* allowRoaming */
                DataService.REQUEST_REASON_NORMAL, /* DataService.REQUEST_REASON_NORMAL */
                null, /* LinkProperties */
                pduSessionId, /* pduSessionId */
                null, /* sliceInfo */
                null, /* trafficDescriptor */
                true, /* matchAllRuleAllowed */
                mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(
                        any(TunnelSetupRequest.class),
                        any(IwlanTunnelCallback.class),
                        any(IwlanTunnelMetricsImpl.class));
    }

    @Test
    public void testN1ModeForEmergencySession() {
        int pduSessionId = 5;
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);
        DataProfile dp = buildDataProfile(ApnSetting.TYPE_EMERGENCY);
        verifySetupDataCallRequestHandled(pduSessionId, dp);

        ArgumentCaptor<TunnelSetupRequest> tunnelSetupRequestCaptor =
                ArgumentCaptor.forClass(TunnelSetupRequest.class);
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(tunnelSetupRequestCaptor.capture(), any(), any());
        TunnelSetupRequest tunnelSetupRequest = tunnelSetupRequestCaptor.getValue();
        assertEquals(pduSessionId, tunnelSetupRequest.getPduSessionId());
    }

    @Test
    public void testN1ModeExclusionForEmergencySession() {
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL, true);
        updatePreferredNetworkType(NETWORK_TYPE_BITMASK_NR);
        DataProfile dp = buildDataProfile(ApnSetting.TYPE_EMERGENCY);
        verifySetupDataCallRequestHandled(5 /* pduSessionId */, dp);

        ArgumentCaptor<TunnelSetupRequest> tunnelSetupRequestCaptor =
                ArgumentCaptor.forClass(TunnelSetupRequest.class);
        verify(mMockEpdgTunnelManager, times(1))
                .bringUpTunnel(tunnelSetupRequestCaptor.capture(), any(), any());
        TunnelSetupRequest tunnelSetupRequest = tunnelSetupRequestCaptor.getValue();
        assertEquals(PDU_SESSION_ID_UNSET, tunnelSetupRequest.getPduSessionId());
    }

    @Test
    public void testRequestNetworkValidationForUnregisteredApn() {
        int index = 0;
        String apnName = "mms";
        ArrayList<Integer> resultCodeCallback = new ArrayList<>();
        mSpyIwlanDataServiceProvider.requestNetworkValidation(
                apnName.hashCode(), Runnable::run, resultCodeCallback::add);
        mTestLooper.dispatchAll();

        assertEquals(1, resultCodeCallback.size());
        assertEquals(
                DataServiceCallback.RESULT_ERROR_UNSUPPORTED,
                resultCodeCallback.get(index).intValue());
        verify(mMockEpdgTunnelManager, never()).requestNetworkValidationForApn(eq(apnName));
    }

    private void verifySetupDataCallSuccess(DataProfile dp) {
        verifySetupDataCallRequestHandled(5 /* pduSessionId */, dp);

        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onOpened(dp.getApn(), mMockTunnelLinkProperties);
        mTestLooper.dispatchAll();
    }

    private List<DataCallResponse> verifyDataCallListChangeAndCaptureUpdatedList() {
        ArgumentCaptor<List<DataCallResponse>> dataCallListCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mSpyIwlanDataServiceProvider, atLeastOnce())
                .notifyDataCallListChanged(dataCallListCaptor.capture());
        return dataCallListCaptor.getValue();
    }

    private void assertDataCallResponsePresentByCidAndStatus(
            int cid, int status, List<DataCallResponse> dataCallList) {
        boolean isMatchFound = false;

        for (DataCallResponse response : dataCallList) {
            if (response.getId() == cid && response.getNetworkValidationStatus() == status) {
                isMatchFound = true;
                break;
            }
        }

        assertTrue(
                "Expected CID and Network Validation Status not found in DataCallResponse list",
                isMatchFound);
    }

    @Test
    public void testOnNetworkValidationStatusChangedForRegisteredApn() {
        List<DataCallResponse> dataCallList;

        ArrayList<Integer> resultCodeCallback = new ArrayList<>();
        DataProfile dp = buildImsDataProfile();
        String apnName = dp.getApn();
        int cid = apnName.hashCode();

        verifySetupDataCallSuccess(dp);
        dataCallList = verifyDataCallListChangeAndCaptureUpdatedList();
        assertEquals(1, dataCallList.size());
        // TODO: b/324874097 - Fix IwlanDataServiceTest to correctly spy on
        // IwlanDataServiceProvider. Address flakiness caused by Mockito spy instrumentation issues
        // on Android. Investigate solutions.
        //
        // assertDataCallResponsePresentByCidAndStatus(
        //        cid, PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS, dataCallList);

        // Requests network validation
        mSpyIwlanDataServiceProvider.requestNetworkValidation(
                cid, Runnable::run, resultCodeCallback::add);
        mTestLooper.dispatchAll();

        dataCallList = verifyDataCallListChangeAndCaptureUpdatedList();
        assertEquals(1, dataCallList.size());
        // TODO: b/324874097 - Fix IwlanDataServiceTest to correctly spy on
        // IwlanDataServiceProvider. Address flakiness caused by Mockito spy instrumentation issues
        // on Android. Investigate solutions.
        //
        // assertDataCallResponsePresentByCidAndStatus(
        //        cid, PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS, dataCallList);

        // Validation success
        mSpyIwlanDataServiceProvider
                .getIwlanTunnelCallback()
                .onNetworkValidationStatusChanged(
                        dp.getApn(), PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS);
        mTestLooper.dispatchAll();

        dataCallList = verifyDataCallListChangeAndCaptureUpdatedList();
        assertEquals(1, dataCallList.size());
        // TODO: b/324874097 - Fix IwlanDataServiceTest to correctly spy on
        // IwlanDataServiceProvider. Address flakiness caused by Mockito spy instrumentation issues
        // on Android. Investigate solutions.
        //
        // assertDataCallResponsePresentByCidAndStatus(
        //        cid, PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS, dataCallList);
    }

    @Test
    public void testGetCallListWithRequestNetworkValidationInProgress() {
        ArgumentCaptor<List<DataCallResponse>> dataCallListCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        DataProfile dp = buildImsDataProfile();
        String apnName = dp.getApn();
        int cid = apnName.hashCode();
        verifySetupDataCallSuccess(dp);

        // Requests network validation, network validation status in progress
        ArrayList<Integer> resultCodeCallback = new ArrayList<>();
        mSpyIwlanDataServiceProvider.requestNetworkValidation(
                cid, Runnable::run, resultCodeCallback::add);
        mTestLooper.dispatchAll();

        // Requests data call list
        mSpyIwlanDataServiceProvider.requestDataCallList(mMockDataServiceCallback);
        mTestLooper.dispatchAll();

        verify(mMockDataServiceCallback, times(1))
                .onRequestDataCallListComplete(
                        eq(DataServiceCallback.RESULT_SUCCESS), dataCallListCaptor.capture());

        List<DataCallResponse> dataCallList = dataCallListCaptor.getValue();
        assertEquals(1, dataCallList.size());
        assertDataCallResponsePresentByCidAndStatus(
                cid, PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS, dataCallList);
    }
}
