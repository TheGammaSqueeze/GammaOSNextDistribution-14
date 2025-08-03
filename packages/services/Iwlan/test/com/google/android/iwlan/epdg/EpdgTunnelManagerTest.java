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

package com.google.android.iwlan.epdg;

import static com.google.android.iwlan.epdg.EpdgTunnelManager.BRINGDOWN_REASON_UNKNOWN;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ipsec.ike.ChildSessionCallback;
import android.net.ipsec.ike.ChildSessionConfiguration;
import android.net.ipsec.ike.ChildSessionParams;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionCallback;
import android.net.ipsec.ike.IkeSessionConfiguration;
import android.net.ipsec.ike.IkeSessionConnectionInfo;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.SaProposal;
import android.net.ipsec.ike.TunnelModeChildSessionParams;
import android.net.ipsec.ike.exceptions.IkeException;
import android.net.ipsec.ike.exceptions.IkeIOException;
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.net.ipsec.ike.ike3gpp.Ike3gppBackoffTimer;
import android.net.ipsec.ike.ike3gpp.Ike3gppData;
import android.net.ipsec.ike.ike3gpp.Ike3gppExtension;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.Pair;

import com.google.android.iwlan.IwlanCarrierConfig;
import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.IwlanTunnelMetricsImpl;
import com.google.android.iwlan.TunnelMetricsInterface.OnClosedMetrics;
import com.google.android.iwlan.TunnelMetricsInterface.OnOpenedMetrics;
import com.google.android.iwlan.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
public class EpdgTunnelManagerTest {
    public static final int DEFAULT_SLOT_INDEX = 0;
    public static final int DEFAULT_SUBID = 0;
    public static final int DEFAULT_TOKEN = 0;

    private static final String EPDG_ADDRESS = "127.0.0.1";
    private static final String SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY = "127.0.0.2";
    private static final String EPDG_ADDRESS_IPV6 = "2600:387:f:707::1";
    private static final String TEST_APN_NAME = "www.xyz.com";

    private static final List<InetAddress> EXPECTED_LOCAL_ADDRESSES =
            List.of(InetAddresses.parseNumericAddress("201.1.100.10"));
    private static final List<InetAddress> EXPECTED_IPV6_LOCAL_ADDRESSES =
            List.of(InetAddresses.parseNumericAddress("2001:db8::1:2"));
    private static final List<InetAddress> EXPECTED_EPDG_ADDRESSES =
            List.of(InetAddresses.parseNumericAddress(EPDG_ADDRESS));
    private static final List<InetAddress> EXPECTE_EPDG_ADDRESSES_FOR_EMERGENCY_SESSION =
            List.of(
                    InetAddresses.parseNumericAddress(EPDG_ADDRESS),
                    InetAddresses.parseNumericAddress(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY));
    private static final List<InetAddress> EXPECTED_EPDG_ADDRESSES_IPV6 =
            List.of(InetAddresses.parseNumericAddress(EPDG_ADDRESS_IPV6));
    private static final List<LinkAddress> EXPECTED_INTERNAL_ADDRESSES =
            List.of(new LinkAddress(InetAddresses.parseNumericAddress("198.50.100.10"), 24));
    private static final List<InetAddress> EXPECTED_PCSCF_ADDRESSES =
            List.of(InetAddresses.parseNumericAddress("198.51.100.10"));
    private static final List<InetAddress> EXPECTED_DNS_ADDRESSES =
            List.of(InetAddresses.parseNumericAddress("198.50.100.10"));

    private EpdgTunnelManager mEpdgTunnelManager;

    private static class IwlanTunnelCallback implements EpdgTunnelManager.TunnelCallback {
        public void onOpened(String apnName, TunnelLinkProperties linkProperties) {}

        public void onClosed(String apnName, IwlanError error) {}

        public void onNetworkValidationStatusChanged(String apnName, int status) {}
    }

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    private TestLooper mTestLooper = new TestLooper();

    @Mock private Context mMockContext;
    @Mock private Network mMockDefaultNetwork;
    @Mock private IwlanTunnelCallback mMockIwlanTunnelCallback;
    @Mock private IwlanTunnelMetricsImpl mMockIwlanTunnelMetrics;
    @Mock private IkeSession mMockIkeSession;
    @Mock private EpdgSelector mMockEpdgSelector;
    @Mock private FeatureFlags mFakeFeatureFlags;
    @Mock ConnectivityManager mMockConnectivityManager;
    @Mock SubscriptionManager mMockSubscriptionManager;
    @Mock SubscriptionInfo mMockSubscriptionInfo;
    @Mock TelephonyManager mMockTelephonyManager;
    @Mock IpSecManager mMockIpSecManager;
    @Mock EpdgTunnelManager.IkeSessionCreator mMockIkeSessionCreator;
    @Mock IkeException mMockIkeException;
    @Mock IkeIOException mMockIkeIoException;
    @Mock IkeSessionConfiguration mMockIkeSessionConfiguration;
    @Mock ChildSessionConfiguration mMockChildSessionConfiguration;
    @Mock IpSecManager.IpSecTunnelInterface mMockIpSecTunnelInterface;
    @Mock IkeSessionConnectionInfo mMockIkeSessionConnectionInfo;
    @Mock IpSecTransform mMockedIpSecTransformIn;
    @Mock IpSecTransform mMockedIpSecTransformOut;
    @Mock LinkProperties mMockLinkProperties;

    static class IkeSessionArgumentCaptors {
        ArgumentCaptor<IkeSessionParams> mIkeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> mChildSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        ArgumentCaptor<IkeSessionCallback> mIkeSessionCallbackCaptor =
                ArgumentCaptor.forClass(IkeSessionCallback.class);
        ArgumentCaptor<ChildSessionCallback> mChildSessionCallbackCaptor =
                ArgumentCaptor.forClass(ChildSessionCallback.class);
    }

    @Before
    public void setUp() throws Exception {
        EpdgTunnelManager.resetAllInstances();
        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);
        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getSystemService(eq(IpSecManager.class))).thenReturn(mMockIpSecManager);
        when(mFakeFeatureFlags.epdgSelectionExcludeFailedIpAddress()).thenReturn(false);

        mEpdgTunnelManager =
                spy(new EpdgTunnelManager(mMockContext, DEFAULT_SLOT_INDEX, mFakeFeatureFlags));
        doReturn(mTestLooper.getLooper()).when(mEpdgTunnelManager).getLooper();
        setVariable(mEpdgTunnelManager, "mContext", mMockContext);
        mEpdgTunnelManager.initHandler();
        doReturn(mMockEpdgSelector).when(mEpdgTunnelManager).getEpdgSelector();
        when(mEpdgTunnelManager.getIkeSessionCreator()).thenReturn(mMockIkeSessionCreator);

        when(mMockEpdgSelector.getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        any(Network.class),
                        any(EpdgSelector.EpdgSelectorCallback.class)))
                .thenReturn(new IwlanError(IwlanError.NO_ERROR));

        when(mMockIkeSessionConfiguration.getPcscfServers()).thenReturn(EXPECTED_PCSCF_ADDRESSES);

        when(mMockChildSessionConfiguration.getInternalDnsServers())
                .thenReturn(EXPECTED_DNS_ADDRESSES);
        when(mMockChildSessionConfiguration.getInternalAddresses())
                .thenReturn(EXPECTED_INTERNAL_ADDRESSES);

        when(mMockIpSecManager.createIpSecTunnelInterface(
                        any(InetAddress.class), any(InetAddress.class), any(Network.class)))
                .thenReturn(mMockIpSecTunnelInterface);
        when(mMockIpSecTunnelInterface.getInterfaceName()).thenReturn("ipsec10");

        when(mMockIkeSessionConnectionInfo.getNetwork()).thenReturn(mMockDefaultNetwork);

        doReturn(EXPECTED_LOCAL_ADDRESSES).when(mEpdgTunnelManager).getAddressForNetwork(any());

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(DEFAULT_SUBID);
        when(mMockSubscriptionInfo.getMncString()).thenReturn("344");

        when(mMockLinkProperties.isReachable(any())).thenReturn(true);
        mEpdgTunnelManager.updateNetwork(mMockDefaultNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();
    }

    @After
    public void cleanUp() {
        IwlanCarrierConfig.resetTestConfig();
    }

    @Test
    public void testBringUpTunnelWithInvalidProtocol() {
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_PPP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertFalse(ret);
    }

    @Test
    public void testBringUpTunnelWithInvalidPduSessionId() {
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6, 16),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertFalse(ret);

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6, -1),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertFalse(ret);
    }

    @Test
    public void testBringUpTunnelWithValidProtocols() {
        String testApnName1 = "www.xyz.com1";
        String testApnName2 = "www.xyz.com2";
        String testApnName3 = "www.xyz.com3";

        TunnelSetupRequest TSR_v4 =
                getBasicTunnelSetupRequest(testApnName1, ApnSetting.PROTOCOL_IP);

        TunnelSetupRequest TSR_v6 =
                getBasicTunnelSetupRequest(testApnName2, ApnSetting.PROTOCOL_IPV6);

        TunnelSetupRequest TSR_v4v6 =
                getBasicTunnelSetupRequest(testApnName3, ApnSetting.PROTOCOL_IPV4V6);

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName1));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName2));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName3));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR_v4, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR_v6, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR_v4v6, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
    }

    @Test
    public void testBringUpTunnelWithNullApn() {

        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);

        when(mEpdgTunnelManager.getTunnelSetupRequestApnName(TSR)).thenReturn(null);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertFalse(ret);
        verify(mEpdgTunnelManager).getTunnelSetupRequestApnName(TSR);
    }

    @Test
    public void testBringUpTunnelWithExistApn() {
        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);

        when(mEpdgTunnelManager.isTunnelConfigContainExistApn(TEST_APN_NAME)).thenReturn(true);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertFalse(ret);
        verify(mEpdgTunnelManager).isTunnelConfigContainExistApn(TEST_APN_NAME);
    }

    @Test
    public void testBringUpTunnelWithNoBringUpInProcess() {
        String testApnName2 = "www.abc.com";

        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName2,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
    }

    @Test
    public void testBringUpTunnelSuccess() throws Exception {

        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV4V6),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
    }

    private void setupTunnelBringup(
            String apnName, List<InetAddress> epdgAddresses, int transactionId) throws Exception {
        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(apnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(apnName, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                epdgAddresses, new IwlanError(IwlanError.NO_ERROR), transactionId);
        mTestLooper.dispatchAll();
    }

    private void setupTunnelBringup() throws Exception {
        setupTunnelBringup(TEST_APN_NAME, EXPECTED_EPDG_ADDRESSES, 1 /* transactionId */);
    }

    @Test
    public void testBringUpTunnelSetsDeviceIdentityImeiSv() throws Exception {
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL, true);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);

        String TEST_IMEI = "012345678901234";
        String TEST_IMEI_SUFFIX = "56";
        String EXPECTED_IMEISV = TEST_IMEI.substring(0, TEST_IMEI.length() - 1) + TEST_IMEI_SUFFIX;
        when(mMockTelephonyManager.getImei()).thenReturn(TEST_IMEI);
        when(mMockTelephonyManager.getDeviceSoftwareVersion()).thenReturn(TEST_IMEI_SUFFIX);

        setupTunnelBringup();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getMobileDeviceIdentity(),
                EXPECTED_IMEISV);
    }

    @Test
    public void testBringUpTunnelSetsDeviceIdentityImei() throws Exception {
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL, true);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);

        String TEST_IMEI = "012345678901234";
        when(mMockTelephonyManager.getImei()).thenReturn(TEST_IMEI);
        when(mMockTelephonyManager.getDeviceSoftwareVersion()).thenReturn(null);

        setupTunnelBringup();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getMobileDeviceIdentity(),
                TEST_IMEI);
    }

    @Test
    public void testBringUpTunnelNoDeviceIdentityWhenImeiUnavailable() throws Exception {
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL, true);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(DEFAULT_SUBID))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.getImei()).thenReturn(null);

        setupTunnelBringup();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertNull(
                ikeSessionParams
                        .getIke3gppExtension()
                        .getIke3gppParams()
                        .getMobileDeviceIdentity());
    }

    @Test
    public void testBringUpTunnelWithMobilityOptions() throws Exception {
        setupTunnelBringup();
        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertTrue(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_MOBIKE));
        assertTrue(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_REKEY_MOBILITY));
    }

    @Test
    public void testBringUpTunnelIpv6_verifyMobikeDisabled() throws Exception {
        setupTunnelBringup(TEST_APN_NAME, EXPECTED_EPDG_ADDRESSES_IPV6, 1);
        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertTrue(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_REKEY_MOBILITY));
        assertFalse(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_MOBIKE));
    }

    @Test
    public void testInitialContactForFirstTunnelOnly() throws Exception {
        final String firstApnName = "ims";
        final String secondApnName = "mms";

        IkeSessionArgumentCaptors firstTunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(firstApnName, mMockDefaultNetwork);
        ChildSessionCallback firstCallback =
                firstTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();

        IkeSessionArgumentCaptors secondTunnelArgumentCaptors =
                verifyBringUpTunnel(
                        secondApnName, mMockDefaultNetwork, true /* needPendingBringUpReq */);
        verifyTunnelOnOpened(firstApnName, firstCallback);

        ChildSessionCallback secondCallback =
                secondTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(secondApnName, secondCallback);

        IkeSessionParams firstTunnelParams =
                firstTunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();
        IkeSessionParams secondTunnelParams =
                secondTunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();
        assertTrue(firstTunnelParams.hasIkeOption(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT));
        assertFalse(secondTunnelParams.hasIkeOption(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT));
    }

    @Test
    public void testAeadSaProposals() throws Exception {
        when(mFakeFeatureFlags.aeadAlgosEnabled()).thenReturn(true);
        final String apnName = "ims";
        int[] aeadAlgos = {
            SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8,
            SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12,
            SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16,
        };
        int[] aeadAlgosKeyLens = {
            SaProposal.KEY_LEN_AES_128, SaProposal.KEY_LEN_AES_192, SaProposal.KEY_LEN_AES_256,
        };

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_SUPPORTED_IKE_SESSION_AEAD_ALGORITHMS_INT_ARRAY,
                aeadAlgos);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_IKE_SESSION_AES_GCM_KEY_SIZE_INT_ARRAY,
                aeadAlgosKeyLens);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_SUPPORTED_CHILD_SESSION_AEAD_ALGORITHMS_INT_ARRAY,
                aeadAlgos);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_CHILD_SESSION_AES_GCM_KEY_SIZE_INT_ARRAY,
                aeadAlgosKeyLens);

        IkeSessionArgumentCaptors tunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork);

        IkeSessionParams ikeTunnelParams = tunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();

        List<Pair<Integer, Integer>> ikeEncrAlgos =
                ikeTunnelParams.getIkeSaProposals().get(0).getEncryptionAlgorithms();

        assertTrue(ikeEncrAlgos.contains(new Pair(aeadAlgos[0], aeadAlgosKeyLens[0])));
        assertEquals(
                "IKE AEAD algorithms mismatch",
                (long) aeadAlgos.length * aeadAlgosKeyLens.length,
                (long) ikeEncrAlgos.size());

        ChildSessionParams childTunnelParams =
                tunnelArgumentCaptors.mChildSessionParamsCaptor.getValue();

        List<Pair<Integer, Integer>> childEncrAlgos =
                childTunnelParams.getChildSaProposals().get(0).getEncryptionAlgorithms();

        assertTrue(childEncrAlgos.contains(new Pair(aeadAlgos[0], aeadAlgosKeyLens[0])));
        assertEquals(
                "Child AEAD algorithms mismatch",
                (long) aeadAlgos.length * aeadAlgosKeyLens.length,
                (long) childEncrAlgos.size());
    }

    @Test
    public void testMultipleSaProposals() throws Exception {
        when(mFakeFeatureFlags.multipleSaProposals()).thenReturn(true);
        final String apnName = "ims";

        int[] aeadAlgos = {
            SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12,
        };
        int[] aeadAlgosKeyLens = {
            SaProposal.KEY_LEN_AES_192, SaProposal.KEY_LEN_AES_256,
        };

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_SUPPORTED_IKE_SESSION_AEAD_ALGORITHMS_INT_ARRAY,
                aeadAlgos);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_IKE_SESSION_AES_GCM_KEY_SIZE_INT_ARRAY,
                aeadAlgosKeyLens);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_SUPPORTED_CHILD_SESSION_AEAD_ALGORITHMS_INT_ARRAY,
                aeadAlgos);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_CHILD_SESSION_AES_GCM_KEY_SIZE_INT_ARRAY,
                aeadAlgosKeyLens);

        IwlanCarrierConfig.putTestConfigBoolean(
                CarrierConfigManager.Iwlan.KEY_SUPPORTS_IKE_SESSION_MULTIPLE_SA_PROPOSALS_BOOL,
                true);
        IwlanCarrierConfig.putTestConfigBoolean(
                CarrierConfigManager.Iwlan.KEY_SUPPORTS_CHILD_SESSION_MULTIPLE_SA_PROPOSALS_BOOL,
                true);

        IkeSessionArgumentCaptors tunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork);

        IkeSessionParams ikeTunnelParams = tunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();

        assertTrue(ikeTunnelParams.getIkeSaProposals().size() > 1);

        List<Pair<Integer, Integer>> ikeAeadAlgos =
                ikeTunnelParams.getIkeSaProposals().get(0).getEncryptionAlgorithms();
        assertEquals(
                "Reorder higher AEAD in  IKE SA mismatch",
                (long) SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12,
                (long) ikeAeadAlgos.get(0).first);

        ChildSessionParams childTunnelParams =
                tunnelArgumentCaptors.mChildSessionParamsCaptor.getValue();

        assertTrue(childTunnelParams.getChildSaProposals().size() > 1);

        List<Pair<Integer, Integer>> childAeadAlgos =
                childTunnelParams.getChildSaProposals().get(0).getEncryptionAlgorithms();
        assertEquals(
                "Reorder higher AEAD in  Child SA mismatch",
                (long) SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12,
                (long) childAeadAlgos.get(0).first);
        assertTrue(childTunnelParams.getChildSaProposals().get(0).getDhGroups().size() == 0);
    }

    @Test
    public void testSaProposalsReorder() throws Exception {
        when(mFakeFeatureFlags.aeadAlgosEnabled()).thenReturn(true);
        when(mFakeFeatureFlags.multipleSaProposals()).thenReturn(true);
        when(mFakeFeatureFlags.highSecureTransformsPrioritized()).thenReturn(true);

        final String apnName = "ims";
        int[] aeadAlgos = {
            SaProposal.ENCRYPTION_ALGORITHM_AES_CBC,
        };
        int[] aeadAlgosKeyLens = {
            SaProposal.KEY_LEN_AES_128, SaProposal.KEY_LEN_AES_192, SaProposal.KEY_LEN_AES_256,
        };

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan
                        .KEY_SUPPORTED_IKE_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY,
                aeadAlgos);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_IKE_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY,
                aeadAlgosKeyLens);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan
                        .KEY_SUPPORTED_CHILD_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY,
                aeadAlgos);
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_CHILD_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY,
                aeadAlgosKeyLens);

        IwlanCarrierConfig.putTestConfigBoolean(
                CarrierConfigManager.Iwlan.KEY_SUPPORTS_IKE_SESSION_MULTIPLE_SA_PROPOSALS_BOOL,
                true);
        IwlanCarrierConfig.putTestConfigBoolean(
                CarrierConfigManager.Iwlan.KEY_SUPPORTS_CHILD_SESSION_MULTIPLE_SA_PROPOSALS_BOOL,
                true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_IKE_SA_TRANSFORMS_REORDER_BOOL, true);

        IkeSessionArgumentCaptors tunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork);

        IkeSessionParams ikeTunnelParams = tunnelArgumentCaptors.mIkeSessionParamsCaptor.getValue();

        assertTrue(ikeTunnelParams.getIkeSaProposals().size() > 1);

        List<Pair<Integer, Integer>> ikeEncrAlgos =
                ikeTunnelParams.getIkeSaProposals().get(0).getEncryptionAlgorithms();

        assertEquals(
                "Reorder bigger key length in IKE SA mismatch",
                (long) SaProposal.KEY_LEN_AES_256,
                (long) ikeEncrAlgos.get(0).second);

        List<Pair<Integer, Integer>> ikeAeadAlgos =
                ikeTunnelParams.getIkeSaProposals().get(1).getEncryptionAlgorithms();
        assertEquals(
                "Reorder higher AEAD in  IKE SA mismatch",
                (long) SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16,
                (long) ikeAeadAlgos.get(0).first);

        ChildSessionParams childTunnelParams =
                tunnelArgumentCaptors.mChildSessionParamsCaptor.getValue();

        assertTrue(childTunnelParams.getChildSaProposals().size() > 1);

        List<Pair<Integer, Integer>> childEncrAlgos =
                childTunnelParams.getChildSaProposals().get(0).getEncryptionAlgorithms();

        assertEquals(
                "Reorder bigger key length in Child SA mismatch",
                (long) SaProposal.KEY_LEN_AES_256,
                (long) childEncrAlgos.get(0).second);

        List<Pair<Integer, Integer>> childAeadAlgos =
                childTunnelParams.getChildSaProposals().get(1).getEncryptionAlgorithms();
        assertEquals(
                "Reorder higher AEAD in  Child SA mismatch",
                (long) SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16,
                (long) childAeadAlgos.get(0).first);
    }

    @Test
    public void testAddDHGroupForKePayloadInChildSaParamsForRekey() throws Exception {
        when(mFakeFeatureFlags.multipleSaProposals()).thenReturn(true);
        final String apnName = "ims";

        IwlanCarrierConfig.putTestConfigBoolean(
                CarrierConfigManager.Iwlan.KEY_SUPPORTS_CHILD_SESSION_MULTIPLE_SA_PROPOSALS_BOOL,
                true);
        IwlanCarrierConfig.putTestConfigBoolean(
                CarrierConfigManager.Iwlan.KEY_ADD_KE_TO_CHILD_SESSION_REKEY_BOOL, true);

        IkeSessionArgumentCaptors tunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork);

        ChildSessionParams childTunnelParams =
                tunnelArgumentCaptors.mChildSessionParamsCaptor.getValue();

        assertTrue(childTunnelParams.getChildSaProposals().size() > 0);

        assertTrue(childTunnelParams.getChildSaProposals().get(0).getDhGroups().size() != 0);
    }

    @Test
    public void testCloseTunnelWithNoTunnelForApn() throws Exception {
        String testApnName = "www.xyz.com";
        doReturn(0L)
                .when(mEpdgTunnelManager)
                .reportIwlanError(eq(testApnName), eq(new IwlanError(IwlanError.TUNNEL_NOT_FOUND)));

        mEpdgTunnelManager.closeTunnel(
                testApnName,
                false /*forceClose*/,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                BRINGDOWN_REASON_UNKNOWN);
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager).closePendingRequestsForApn(eq(testApnName));
        verify(mMockIwlanTunnelCallback)
                .onClosed(eq(testApnName), eq(new IwlanError(IwlanError.TUNNEL_NOT_FOUND)));
        ArgumentCaptor<OnClosedMetrics> metricsCaptor =
                ArgumentCaptor.forClass(OnClosedMetrics.class);
        verify(mMockIwlanTunnelMetrics, times(1)).onClosed(metricsCaptor.capture());
        assertEquals(testApnName, metricsCaptor.getValue().getApnName());
    }

    @Test
    public void testCloseTunnelWithForceClose() throws Exception {
        String testApnName = "www.xyz.com";

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());

        mEpdgTunnelManager.closeTunnel(
                testApnName,
                true /*forceClose*/,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                BRINGDOWN_REASON_UNKNOWN);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession).kill();
        verify(mEpdgTunnelManager).closePendingRequestsForApn(eq(testApnName));
    }

    @Test
    public void testCloseTunnelWithNonForceClose() throws Exception {
        String testApnName = "www.xyz.com";

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());

        mEpdgTunnelManager.closeTunnel(
                testApnName,
                false /*forceClose*/,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                BRINGDOWN_REASON_UNKNOWN);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession).close();
        verify(mEpdgTunnelManager).closePendingRequestsForApn(eq(testApnName));
    }

    @Test
    public void testRekeyAndNattTimerFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        // Test values
        int hardTime = 50000;
        int softTime = 20000;
        int hardTimeChild = 10000;
        int softTimeChild = 1000;
        int nattTimer = 60;

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_IKE_REKEY_HARD_TIMER_SEC_INT, hardTime);
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_IKE_REKEY_SOFT_TIMER_SEC_INT, softTime);
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_CHILD_SA_REKEY_HARD_TIMER_SEC_INT, hardTimeChild);
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_CHILD_SA_REKEY_SOFT_TIMER_SEC_INT, softTimeChild);
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT, nattTimer);

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        ChildSessionParams childSessionParams = childSessionParamsCaptor.getValue();

        assertEquals(ikeSessionParams.getHardLifetimeSeconds(), hardTime);
        assertEquals(ikeSessionParams.getSoftLifetimeSeconds(), softTime);
        assertEquals(childSessionParams.getHardLifetimeSeconds(), hardTimeChild);
        assertEquals(childSessionParams.getSoftLifetimeSeconds(), softTimeChild);
        assertEquals(ikeSessionParams.getNattKeepAliveDelaySeconds(), nattTimer);
    }

    @Test
    public void testSetRetransmissionTimeoutsFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        int[] testTimeouts = {1000, 1200, 1400, 1600, 2000, 4000};

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_RETRANSMIT_TIMER_MSEC_INT_ARRAY, testTimeouts);

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertArrayEquals(ikeSessionParams.getRetransmissionTimeoutsMillis(), testTimeouts);
    }

    @Test
    public void testSetDpdDelayFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        // Test values
        int testDpdDelay = 600;

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_DPD_TIMER_SEC_INT, testDpdDelay);

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getDpdDelaySeconds(), testDpdDelay);
    }

    @Test
    public void testGetValidEpdgAddress_DiffAddr() throws Exception {
        String testApnName = "www.xyz.com";

        List<InetAddress> ipList1 = new ArrayList<>();
        ipList1.add(InetAddress.getByName("1.1.1.1"));
        mEpdgTunnelManager.validateAndSetEpdgAddress(ipList1);

        IwlanError error = new IwlanError(new IkeInternalException(new IOException()));

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArrayList<InetAddress> ipList2 = new ArrayList<>();
        ipList2.add(InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.sendSelectionRequestComplete(
                ipList2, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                verifyCreateIkeSession(ipList2.get(0));
        ikeSessionCallback.onClosedWithException(
                new IkeInternalException(new IOException("Retransmitting failure")));
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testGetValidEpdgAddress_NextAddr() throws Exception {
        String testApnName = "www.xyz.com";

        List<InetAddress> ipList1 = new ArrayList<>();
        ipList1.add(InetAddress.getByName("1.1.1.1"));
        ipList1.add(InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.validateAndSetEpdgAddress(ipList1);

        IwlanError error = new IwlanError(new IkeInternalException(new IOException()));

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArrayList<InetAddress> ipList2 = new ArrayList<>();
        ipList2.add(InetAddress.getByName("1.1.1.1"));
        ipList2.add(InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.sendSelectionRequestComplete(
                ipList2, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                verifyCreateIkeSession(ipList2.get(1));
        ikeSessionCallback.onClosedWithException(
                new IkeInternalException(new IOException("Retransmitting failure")));
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testGetValidEpdgAddress_WhenExcludeFailedIpEnabled() throws Exception {
        String testApnName = "www.xyz.com";
        when(mFakeFeatureFlags.epdgSelectionExcludeFailedIpAddress()).thenReturn(true);

        List<InetAddress> ipList1 =
                List.of(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.validateAndSetEpdgAddress(ipList1);

        IwlanError error = new IwlanError(new IkeInternalException(new IOException()));

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        List<InetAddress> ipList2 =
                List.of(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("8.8.8.8"));
        mEpdgTunnelManager.sendSelectionRequestComplete(
                ipList2, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        // When exclude failed IP is enabled, EpdgSelector is responsible to excluding the failed
        // IP address from result. EpdgTunnelManager should always use the first IP address from
        // the ePDG selection result IP address list, regardless the list is same as prev or not
        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                verifyCreateIkeSession(ipList2.get(0));
        ikeSessionCallback.onClosedWithException(
                new IkeInternalException(new IOException("Retransmitting failure")));
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    private EpdgTunnelManager.TmIkeSessionCallback verifyCreateIkeSession(InetAddress ip)
            throws Exception {
        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<EpdgTunnelManager.TmIkeSessionCallback> ikeSessionCallbackCaptor =
                ArgumentCaptor.forClass(EpdgTunnelManager.TmIkeSessionCallback.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        ikeSessionCallbackCaptor.capture(),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getServerHostname(), ip.getHostAddress());
        return ikeSessionCallbackCaptor.getValue();
    }

    @Test
    public void testIpv6PrefixMatching() throws Exception {
        InetAddress a1 = InetAddress.getByName("2600:381:4872:5d1e:ac45:69c7:bab2:639b");
        LinkAddress l1 = new LinkAddress(a1, 64);
        InetAddress src = InetAddress.getByName("2600:381:4872:5d1e:0:10:3582:a501");
        EpdgTunnelManager.TunnelConfig tf =
                mEpdgTunnelManager
                .new TunnelConfig(null, null, null, mMockIpSecTunnelInterface, src, 64, false, a1);
        assertTrue(tf.isPrefixSameAsSrcIP(l1));

        // different prefix length
        LinkAddress l2 = new LinkAddress(a1, 63);
        assertFalse(tf.isPrefixSameAsSrcIP(l2));
    }

    @Test
    public void testBackOffTimeCalculation() throws Exception {
        int transactionId = 1;

        // unit: 10 mins value: 2 expectedTime: 1200 (10 * 60 * 2)
        verifyBackOffTimer("00000010", 1200, transactionId++);
        // unit: 1 hour value: 4 expectedTime: 14400 (1 * 60 * 60 * 4)
        verifyBackOffTimer("00100100", 14400, transactionId++);
        // unit: 10 hours value: 3 expectedTime: (10 * 60 * 60 * 3)
        verifyBackOffTimer("01000011", 108000, transactionId++);
        // unit: 2 secs value: 21 expectedTime: 42 (2 * 21)
        verifyBackOffTimer("01110101", 42, transactionId++);
        // unit: 30 secs value: 31 expectedTime: 930 (30 * 31)
        verifyBackOffTimer("10011111", 930, transactionId++);
        // unit: 1 min value: 25 expectedTime: 1500 (1 * 60 * 25)
        verifyBackOffTimer("10111001", 1500, transactionId++);
        // unit: 1 hour value: 12 expectedTime: 43200 (1 * 60 * 60 * 12)
        verifyBackOffTimer("11001100", 43200, transactionId++);
        // deactivate - Should not report backoff time.
        verifyBackOffTimer("11100100", -1, transactionId++);
    }

    private void verifyBackOffTimer(String backoffByte, long expectedBackoffTime, int transactionId)
            throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(new IkeInternalException(new Exception()));
        Ike3gppBackoffTimer mockIke3gppBackoffTimer = mock(Ike3gppBackoffTimer.class);
        List<Ike3gppData> ike3gppInfoList = new ArrayList<>();
        ike3gppInfoList.add(mockIke3gppBackoffTimer);
        doReturn(Ike3gppData.DATA_TYPE_NOTIFY_BACKOFF_TIMER)
                .when(mockIke3gppBackoffTimer)
                .getDataType();
        doReturn((byte) Integer.parseInt(backoffByte, 2))
                .when(mockIke3gppBackoffTimer)
                .getBackoffTimer();

        // if back off time expected is negative normal reportIwlanError should be called.
        if (expectedBackoffTime < 0) {
            doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        } else {
            doReturn(0L)
                    .when(mEpdgTunnelManager)
                    .reportIwlanError(eq(testApnName), eq(error), anyLong());
        }

        doReturn(null)
                .doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), transactionId);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<EpdgTunnelManager.TmIkeSessionCallback> ikeSessionCallbackCaptor =
                ArgumentCaptor.forClass(EpdgTunnelManager.TmIkeSessionCallback.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        ikeSessionCallbackCaptor.capture(),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(
                ikeSessionParams.getServerHostname(),
                EXPECTED_EPDG_ADDRESSES.get(0).getHostAddress());

        Ike3gppExtension.Ike3gppDataListener ike3gppCallback =
                ikeSessionParams.getIke3gppExtension().getIke3gppDataListener();
        ike3gppCallback.onIke3gppDataReceived(ike3gppInfoList);
        EpdgTunnelManager.TmIkeSessionCallback ikeSessionCallback =
                ikeSessionCallbackCaptor.getValue();
        ikeSessionCallback.onClosedWithException(new IkeInternalException(new Exception()));
        mTestLooper.dispatchAll();

        // if expected backoff time is negative - verify that backoff time is not reported.
        if (expectedBackoffTime < 0) {
            verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        } else {
            // Else - Verify reportIwlanError with correct backoff time is being called.
            verify(mEpdgTunnelManager, times(1))
                    .reportIwlanError(eq(testApnName), eq(error), eq(expectedBackoffTime));
        }
        verify(mMockIwlanTunnelCallback, atLeastOnce()).onClosed(eq(testApnName), eq(error));
    }

    private TunnelSetupRequest getBasicTunnelSetupRequest(String apnName, int apnIpProtocol) {
        return getBasicTunnelSetupRequest(apnName, apnIpProtocol, 1);
    }

    private TunnelSetupRequest getBasicTunnelSetupRequest(
            String apnName, int apnIpProtocol, int pduSessionId) {
        return TunnelSetupRequest.builder()
                .setApnName(apnName)
                .setIsRoaming(false /*isRoaming*/)
                .setIsEmergency(false /*IsEmergency*/)
                .setRequestPcscf(false /*requestPcscf*/)
                .setApnIpProtocol(apnIpProtocol)
                .setPduSessionId(pduSessionId)
                .build();
    }

    private TunnelSetupRequest getHandoverTunnelSetupRequest(String apnName, int apnIpProtocol) {
        TunnelSetupRequest.Builder bld = TunnelSetupRequest.builder();
        bld.setApnName(apnName)
                .setIsRoaming(false /*isRoaming*/)
                .setIsEmergency(false /*IsEmergency*/)
                .setRequestPcscf(false /*requestPcscf*/)
                .setApnIpProtocol(apnIpProtocol)
                .setPduSessionId(1);
        switch (apnIpProtocol) {
            case ApnSetting.PROTOCOL_IP:
                bld.setSrcIpv4Address(InetAddresses.parseNumericAddress("10.10.10.10"));
                break;
            case ApnSetting.PROTOCOL_IPV6:
                bld.setSrcIpv6Address(
                        InetAddresses.parseNumericAddress(
                                "2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
                break;
            case ApnSetting.PROTOCOL_IPV4V6:
                bld.setSrcIpv4Address(InetAddresses.parseNumericAddress("10.10.10.10"));
                bld.setSrcIpv6Address(
                        InetAddresses.parseNumericAddress(
                                "2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
                break;
        }
        return bld.build();
    }

    private void setVariable(Object target, String variableName, Object value) throws Exception {
        FieldSetter.setField(target, target.getClass().getDeclaredField(variableName), value);
    }

    @Test
    public void testHandleOnClosedWithEpdgConnected_True() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error =
                new IwlanError(IwlanError.IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                InetAddresses.parseNumericAddress(EPDG_ADDRESS));
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.onConnectedToEpdg(true);
        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(
                testApnName, InetAddresses.parseNumericAddress(EPDG_ADDRESS));

        mEpdgTunnelManager.getTmIkeSessionCallback(testApnName, token).onClosed();
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testHandleOnClosedWithEpdgConnected_False() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error =
                new IwlanError(IwlanError.IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.onConnectedToEpdg(false);

        mEpdgTunnelManager.getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN).onClosed();
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    private void setOneTunnelOpened(String apnName) throws Exception {
        InetAddress epdgAddress =
                mEpdgTunnelManager.validateAndSetEpdgAddress(EXPECTED_EPDG_ADDRESSES);
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                apnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                epdgAddress);
        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(apnName, epdgAddress);
        mEpdgTunnelManager.onConnectedToEpdg(true);
    }

    private IkeSessionArgumentCaptors verifyBringUpTunnelWithDnsQuery(
            String apnName, Network network) throws Exception {
        return verifyBringUpTunnelWithDnsQuery(apnName, network, null);
    }

    private IkeSessionArgumentCaptors verifyBringUpTunnelWithDnsQuery(
            String apnName, Network network, IkeSession ikeSession) throws Exception {
        reset(mMockIwlanTunnelCallback);
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors = new IkeSessionArgumentCaptors();

        verifyBringUpTunnel(apnName, network, true /* needPendingBringUpReq */);

        doReturn(ikeSession)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        verify(mMockIkeSessionCreator, times(1))
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        return ikeSessionArgumentCaptors;
    }

    private IkeSessionArgumentCaptors verifyBringUpTunnel(
            String apnName, Network network, boolean needPendingBringUpReq) throws Exception {
        reset(mMockIkeSessionCreator);
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors = new IkeSessionArgumentCaptors();

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(apnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(apnName, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockIkeSessionCreator, times(needPendingBringUpReq ? 0 : 1))
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionParamsCaptor.capture(),
                        any(Executor.class),
                        ikeSessionArgumentCaptors.mIkeSessionCallbackCaptor.capture(),
                        ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.capture());

        if (!needPendingBringUpReq) {
            verify(mMockIpSecManager, times(1))
                    .createIpSecTunnelInterface(
                            any(InetAddress.class), any(InetAddress.class), eq(network));
        }

        return ikeSessionArgumentCaptors;
    }

    private void verifyTunnelOnOpened(String apnName, ChildSessionCallback childSessionCallback) {
        clearInvocations(mMockIpSecManager);
        doReturn(0L)
                .when(mEpdgTunnelManager)
                .reportIwlanError(eq(apnName), eq(new IwlanError(IwlanError.NO_ERROR)));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(apnName, mEpdgTunnelManager.getCurrentTokenForApn(apnName))
                .onOpened(mMockIkeSessionConfiguration);
        mTestLooper.dispatchAll();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformOut, IpSecManager.DIRECTION_OUT);
        mTestLooper.dispatchAll();

        childSessionCallback.onOpened(mMockChildSessionConfiguration);
        mTestLooper.dispatchAll();
        verify(mEpdgTunnelManager, times(1))
                .reportIwlanError(eq(apnName), eq(new IwlanError(IwlanError.NO_ERROR)));
        verify(mMockIwlanTunnelCallback, times(1)).onOpened(eq(apnName), any());
    }

    @Test
    public void testHandleOnOpenedWithEpdgConnected_True() throws Exception {
        final String openedApnName = "ims";
        final String toBeOpenedApnName = "mms";

        setOneTunnelOpened(openedApnName);

        // FIXME: Since the network from bringUpTunnel() will only be stored for the first request,
        // and we are skipping the first tunnel setup procedure in this test case, it is necessary
        // to set the network instance directly.
        mEpdgTunnelManager.updateNetwork(mMockDefaultNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnel(
                        toBeOpenedApnName, mMockDefaultNetwork, false /* needPendingBringUpReq */);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(toBeOpenedApnName, childSessionCallback);
        verify(mMockEpdgSelector, times(0)).onEpdgConnectionFailed(any());
        verify(mMockEpdgSelector).onEpdgConnectedSuccessfully();
    }

    @Test
    public void testServicePendingRequests() throws Exception {
        final String firstApnName = "ims";
        final String secondApnName = "mms";

        IkeSessionArgumentCaptors firstTunnelArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(firstApnName, mMockDefaultNetwork);
        ChildSessionCallback firstCallback =
                firstTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();

        IkeSessionArgumentCaptors secondTunnelArgumentCaptors =
                verifyBringUpTunnel(
                        secondApnName, mMockDefaultNetwork, true /* needPendingBringUpReq */);
        verifyTunnelOnOpened(firstApnName, firstCallback);

        ChildSessionCallback secondCallback =
                secondTunnelArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(secondApnName, secondCallback);
    }

    @Test
    public void testHandleOnClosedExceptionallyWithEpdgConnected_True() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(mMockIkeException);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                InetAddresses.parseNumericAddress(EPDG_ADDRESS));
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.onConnectedToEpdg(true);
        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(
                testApnName, InetAddresses.parseNumericAddress(EPDG_ADDRESS));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onClosedWithException(mMockIkeException);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testHandleOnClosedExceptionallyWithEpdgConnected_False() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(mMockIkeException);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.mEpdgMonitor.onApnDisconnectFromEpdg(TEST_APN_NAME);
        mEpdgTunnelManager.onConnectedToEpdg(false);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN)
                .onClosedWithException(mMockIkeException);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), any(IwlanError.class));
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testIkeSessionOnOpenedUpdatesPcscfAddrInTunnelConfig() throws Exception {
        String testApnName = "ims";
        IwlanError error = new IwlanError(IwlanError.NO_ERROR);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        when(mMockIkeSessionConfiguration.getPcscfServers()).thenReturn(EXPECTED_EPDG_ADDRESSES);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onOpened(mMockIkeSessionConfiguration);
        mTestLooper.dispatchAll();

        EpdgTunnelManager.TunnelConfig testApnTunnelConfig =
                mEpdgTunnelManager.getTunnelConfigForApn(testApnName);
        assertEquals(testApnTunnelConfig.getPcscfAddrList(), EXPECTED_EPDG_ADDRESSES);
    }

    @Test
    public void testIkeSessionClosesWhenChildSessionTransformThrows() throws Exception {
        String testApnName = "ims";

        doThrow(new IllegalArgumentException())
                .when(mMockIpSecManager)
                .applyTunnelModeTransform(eq(mMockIpSecTunnelInterface), anyInt(), any());
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(testApnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession, times(1)).close();
    }

    @Test
    public void testIkeSessionConnectionInfoChangedSetsUnderlyingNetwork() throws Exception {
        String testApnName = "ims";
        when(mMockConnectivityManager.getLinkProperties(any())).thenReturn(mMockLinkProperties);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(testApnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN)
                .onIkeSessionConnectionInfoChanged(mMockIkeSessionConnectionInfo);
        mTestLooper.dispatchAll();

        verify(mMockIpSecTunnelInterface, times(1)).setUnderlyingNetwork(mMockDefaultNetwork);
    }

    @Test
    public void testIkeSessionConnectionInfoChangedWithNullLinkPropertiesDoesNothing()
            throws Exception {
        String testApnName = "ims";
        when(mMockConnectivityManager.getLinkProperties(any())).thenReturn(null);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(testApnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, DEFAULT_TOKEN)
                .onIkeSessionConnectionInfoChanged(mMockIkeSessionConnectionInfo);
        mTestLooper.dispatchAll();

        verify(mMockIpSecTunnelInterface, times(0)).setUnderlyingNetwork(any());
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IP, false);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv6() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV6, false);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4v6() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV4V6, false);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4_handover() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IP, true);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv6_handover() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV6, true);
    }

    @Test
    public void testSetIkeTrafficSelectorsIPv4v6_handover() throws Exception {
        testSetIkeTrafficSelectors(ApnSetting.PROTOCOL_IPV4V6, true);
    }

    private void testSetIkeTrafficSelectors(int apnProtocol, boolean handover) throws Exception {
        String testApnName = "www.xyz.com";

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret;

        if (handover) {
            ret =
                    mEpdgTunnelManager.bringUpTunnel(
                            getHandoverTunnelSetupRequest(TEST_APN_NAME, apnProtocol),
                            mMockIwlanTunnelCallback,
                            mMockIwlanTunnelMetrics);
        } else {
            ret =
                    mEpdgTunnelManager.bringUpTunnel(
                            getBasicTunnelSetupRequest(TEST_APN_NAME, apnProtocol),
                            mMockIwlanTunnelCallback,
                            mMockIwlanTunnelMetrics);
        }

        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        ChildSessionParams childSessionParams = childSessionParamsCaptor.getValue();

        switch (apnProtocol) {
            case ApnSetting.PROTOCOL_IPV4V6:
                assertEquals(childSessionParams.getInboundTrafficSelectors().size(), 2);
                assertEquals(childSessionParams.getOutboundTrafficSelectors().size(), 2);
                assertTrue(
                        childSessionParams.getInboundTrafficSelectors().get(0).endingAddress
                                != childSessionParams
                                        .getInboundTrafficSelectors()
                                        .get(1)
                                        .endingAddress);
                assertTrue(
                        childSessionParams.getInboundTrafficSelectors().get(0).startingAddress
                                != childSessionParams
                                        .getInboundTrafficSelectors()
                                        .get(1)
                                        .startingAddress);
                break;
            case ApnSetting.PROTOCOL_IPV6:
                assertEquals(childSessionParams.getInboundTrafficSelectors().size(), 1);
                assertEquals(childSessionParams.getOutboundTrafficSelectors().size(), 1);
                assertEquals(
                        childSessionParams.getOutboundTrafficSelectors().get(0),
                        childSessionParams.getInboundTrafficSelectors().get(0));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).endingAddress,
                        InetAddresses.parseNumericAddress(
                                "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).startingAddress,
                        InetAddresses.parseNumericAddress("::"));
                break;
            case ApnSetting.PROTOCOL_IP:
                assertEquals(childSessionParams.getInboundTrafficSelectors().size(), 1);
                assertEquals(childSessionParams.getOutboundTrafficSelectors().size(), 1);
                assertEquals(
                        childSessionParams.getOutboundTrafficSelectors().get(0),
                        childSessionParams.getInboundTrafficSelectors().get(0));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).endingAddress,
                        InetAddresses.parseNumericAddress("255.255.255.255"));
                assertEquals(
                        childSessionParams.getInboundTrafficSelectors().get(0).startingAddress,
                        InetAddresses.parseNumericAddress("0.0.0.0"));
                break;
        }
    }

    @Test
    public void testUnsetPduSessionIdInclusion() throws Exception {
        verifyN1modeCapability(0);
    }

    @Test
    public void testPduSessionIdInclusion() throws Exception {
        verifyN1modeCapability(8);
    }

    @Test
    public void testReportIwlanErrorIkeProtocolException() throws Exception {
        String testApnName = "www.xyz.com";

        IkeProtocolException mockException = mock(IkeProtocolException.class);
        doReturn(IkeProtocolException.ERROR_TYPE_INVALID_IKE_SPI)
                .when(mockException)
                .getErrorType();
        doReturn(new byte[0]).when(mockException).getErrorData();
        IwlanError error = new IwlanError(mockException);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                InetAddresses.parseNumericAddress(EPDG_ADDRESS));
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.onConnectedToEpdg(true);
        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(
                testApnName, InetAddresses.parseNumericAddress(EPDG_ADDRESS));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onClosedWithException(mockException);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testReportIwlanErrorServerSelectionFailed() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(null, error, 1);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.mEpdgMonitor.onApnDisconnectFromEpdg(TEST_APN_NAME);
        mEpdgTunnelManager.onConnectedToEpdg(false);

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
    }

    @Test
    public void testNeverReportIwlanErrorWhenCloseAnOpenedTunnel() throws Exception {
        IkeInternalException ikeException =
                new IkeInternalException(new IOException("Retransmitting failure"));

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(TEST_APN_NAME, mMockDefaultNetwork);

        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(TEST_APN_NAME, childSessionCallback);

        reset(mEpdgTunnelManager); // reset number of times of reportIwlanError()

        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 0)
                .onClosedWithException(ikeException);
        mTestLooper.dispatchAll();
        verify(mEpdgTunnelManager, never()).reportIwlanError(eq(TEST_APN_NAME), any());
        verify(mMockIwlanTunnelCallback, times(1))
                .onClosed(eq(TEST_APN_NAME), eq(new IwlanError(ikeException)));
    }

    @Test
    public void testCanBringUpTunnel() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(mMockIkeException);

        doReturn(false).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));
        doReturn(error).when(mEpdgTunnelManager).getLastError(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    private void verifyN1modeCapability(int pduSessionId) throws Exception {

        String testApnName = "www.xyz.com";
        byte pduSessionIdToByte = (byte) pduSessionId;

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret;

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(
                                TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6, pduSessionId),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);

        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<ChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(ChildSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();

        assertNotNull(ikeSessionParams.getIke3gppExtension().getIke3gppParams());

        byte pduSessionIdByte =
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getPduSessionId();
        assertEquals(pduSessionIdToByte, pduSessionIdByte);
    }

    @Test
    public void testInvalidNattTimerFromCarrierConfig() throws Exception {
        String testApnName = "www.xyz.com";

        int nattTimer = 4500; // valid range for natt timer is 0-3600
        int ikeDefaultNattTimerValue = 20; // default value for natt timer is 20 secs

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT, nattTimer);

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);

        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getNattKeepAliveDelaySeconds(), ikeDefaultNattTimerValue);
    }

    @Test
    public void testTunnelSetupRequestParams() throws Exception {
        String testApnName = "www.xyz.com";
        Inet6Address testAddressV6 = Inet6Address.getByAddress("25.25.25.25", new byte[16], 0);
        Inet4Address testAddressV4 = (Inet4Address) Inet4Address.getByName("30.30.30.30");
        int pduSessionId = 5;
        boolean isRoaming = false;
        boolean isEmergency = true;
        boolean requestPcscf = true;
        int ipv6AddressLen = 64;

        TunnelSetupRequest tsr =
                TunnelSetupRequest.builder()
                        .setApnName(testApnName)
                        .setApnIpProtocol(ApnSetting.PROTOCOL_IPV4V6)
                        .setSrcIpv6Address(testAddressV6)
                        .setSrcIpv6AddressPrefixLength(ipv6AddressLen)
                        .setSrcIpv4Address(testAddressV4)
                        .setPduSessionId(pduSessionId)
                        .setIsRoaming(isRoaming)
                        .setIsEmergency(isEmergency)
                        .setRequestPcscf(requestPcscf)
                        .build();

        doReturn(null)
                .when(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        tsr, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        // verify isRoaming, isEmergency and Network variables.
        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        anyInt(), // only Ipv6 address is added
                        anyInt(),
                        eq(isRoaming),
                        eq(isEmergency),
                        eq(mMockDefaultNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class));

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        ArgumentCaptor<TunnelModeChildSessionParams> childSessionParamsCaptor =
                ArgumentCaptor.forClass(TunnelModeChildSessionParams.class);

        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        childSessionParamsCaptor.capture(),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));

        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        TunnelModeChildSessionParams childSessionParams = childSessionParamsCaptor.getValue();

        // apnName verification. By default remote identification is type fqdn
        IkeFqdnIdentification ikeId =
                (IkeFqdnIdentification) ikeSessionParams.getRemoteIdentification();
        assertEquals(ikeId.fqdn, testApnName);

        // verify Network
        assertEquals(ikeSessionParams.getNetwork(), mMockDefaultNetwork);

        // verify requestPcscf (true) with Apn protocol IPV6
        // it should add the pcscf config requests of type ConfigRequestIpv6PcscfServer and
        // ConfigRequestIpv4PcscfServer
        assertTrue(
                ikeSessionParams.getConfigurationRequests().stream()
                        .anyMatch(c -> c instanceof IkeSessionParams.ConfigRequestIpv6PcscfServer));
        assertTrue(
                ikeSessionParams.getConfigurationRequests().stream()
                        .anyMatch(c -> c instanceof IkeSessionParams.ConfigRequestIpv4PcscfServer));

        // verify pduSessionID
        assertEquals(
                ikeSessionParams.getIke3gppExtension().getIke3gppParams().getPduSessionId(),
                pduSessionId);

        // verify src ipv6  and src ipv4 address
        List<TunnelModeChildSessionParams.TunnelModeChildConfigRequest> configRequests =
                childSessionParams.getConfigurationRequests();
        boolean ipv6ConfigRequestPresent = false;
        boolean ipv4ConfigRequestPresent = true;
        for (TunnelModeChildSessionParams.TunnelModeChildConfigRequest configRequest :
                configRequests) {
            if (configRequest instanceof TunnelModeChildSessionParams.ConfigRequestIpv6Address) {
                ipv6ConfigRequestPresent = true;
                assertEquals(
                        ((TunnelModeChildSessionParams.ConfigRequestIpv6Address) configRequest)
                                .getAddress(),
                        testAddressV6);
                assertEquals(
                        ((TunnelModeChildSessionParams.ConfigRequestIpv6Address) configRequest)
                                .getPrefixLength(),
                        ipv6AddressLen);
            }
            if (configRequest instanceof TunnelModeChildSessionParams.ConfigRequestIpv4Address) {
                ipv4ConfigRequestPresent = true;
                assertEquals(
                        ((TunnelModeChildSessionParams.ConfigRequestIpv4Address) configRequest)
                                .getAddress(),
                        testAddressV4);
            }
        }
        assertTrue(ipv6ConfigRequestPresent);
        assertTrue(ipv4ConfigRequestPresent);
    }

    @Test
    public void testBringupTunnelFailWithInvalidSimState() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.SIM_NOT_READY_EXCEPTION);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX))
                .thenReturn(null);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testBringupTunnelFailWithInvalidNai() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.SIM_NOT_READY_EXCEPTION);

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testApnName));

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX))
                .thenReturn(mMockSubscriptionInfo)
                .thenReturn(mMockSubscriptionInfo)
                .thenReturn(null);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTED_EPDG_ADDRESSES, new IwlanError(IwlanError.NO_ERROR), 1);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testCloseTunnelWithEpdgSelectionIncomplete() {
        // Bring up tunnel
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);

        // close tunnel when ePDG selection is incomplete
        mEpdgTunnelManager.closeTunnel(
                TEST_APN_NAME,
                false /*forceClose*/,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                BRINGDOWN_REASON_UNKNOWN);
        mTestLooper.dispatchAll();

        verify(mMockIwlanTunnelCallback, times(1))
                .onClosed(eq(TEST_APN_NAME), eq(new IwlanError(IwlanError.NO_ERROR)));
        ArgumentCaptor<OnClosedMetrics> metricsCaptor =
                ArgumentCaptor.forClass(OnClosedMetrics.class);
        verify(mMockIwlanTunnelMetrics, times(1)).onClosed(metricsCaptor.capture());
        assertEquals(TEST_APN_NAME, metricsCaptor.getValue().getApnName());
        assertNull(metricsCaptor.getValue().getEpdgServerAddress());
    }

    @Test
    public void testIgnoreSignalFromObsoleteCallback() throws Exception {
        int transactionId = 0;

        // testApnName with token 0
        setupTunnelBringup(TEST_APN_NAME, EXPECTED_EPDG_ADDRESSES, ++transactionId);
        mEpdgTunnelManager.onConnectedToEpdg(true);

        IwlanError error = new IwlanError(mMockIkeException);
        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(TEST_APN_NAME), eq(error));

        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 0 /* token */)
                .onClosedWithException(mMockIkeException);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
        assertNull(mEpdgTunnelManager.getTunnelConfigForApn(TEST_APN_NAME));

        // testApnName1 with token 1
        setupTunnelBringup(TEST_APN_NAME, EXPECTED_EPDG_ADDRESSES, ++transactionId);
        mEpdgTunnelManager.onConnectedToEpdg(true);

        // signal from obsolete callback (token 0), ignore it
        reset(mMockIwlanTunnelCallback);
        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 0 /* token */)
                .onClosedWithException(mMockIkeException);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, never()).onClosed(eq(TEST_APN_NAME), eq(error));
        assertNotNull(mEpdgTunnelManager.getTunnelConfigForApn(TEST_APN_NAME));

        // signals from active callback
        mEpdgTunnelManager
                .getTmIkeSessionCallback(TEST_APN_NAME, 1 /* token */)
                .onClosedWithException(mMockIkeException);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
        assertNull(mEpdgTunnelManager.getTunnelConfigForApn(TEST_APN_NAME));
    }

    @Test
    public void testBringUpTunnelIpv4Preferred() throws Exception {
        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV4_PREFERRED);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV4V6),
                        eq(EpdgSelector.IPV4_PREFERRED),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
    }

    @Test
    public void testBringUpTunnelIpv6Preferred() throws Exception {
        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV6_PREFERRED);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV4V6),
                        eq(EpdgSelector.IPV6_PREFERRED),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
    }

    @Test
    public void testBringUpTunnelIpv4Only() throws Exception {
        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV4_ONLY);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV4),
                        eq(EpdgSelector.SYSTEM_PREFERRED),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
    }

    @Test
    public void testBringUpTunnelIpv6Only() throws Exception {
        TunnelSetupRequest TSR =
                getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));
        doReturn(EXPECTED_IPV6_LOCAL_ADDRESSES)
                .when(mEpdgTunnelManager)
                .getAddressForNetwork(any());

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV6_ONLY);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV6),
                        eq(EpdgSelector.SYSTEM_PREFERRED),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
    }

    @Test
    public void testBringUpTunnelIpv6OnlyOnIpv4Wifi() throws Exception {
        TunnelSetupRequest TSR =
                getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IPV6);
        IwlanError error = new IwlanError(IwlanError.EPDG_ADDRESS_ONLY_IPV6_ALLOWED);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));
        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(TEST_APN_NAME), eq(error));

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV6_ONLY);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector, never())
                .getValidatedServerList(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(TEST_APN_NAME), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
    }

    @Test
    public void testBringUpTunnelSystemPreferred() throws Exception {
        TunnelSetupRequest TSR = getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP);
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));

        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_SYSTEM_PREFERRED);

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        TSR, mMockIwlanTunnelCallback, mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector)
                .getValidatedServerList(
                        anyInt(),
                        eq(EpdgSelector.PROTO_FILTER_IPV4V6),
                        eq(EpdgSelector.SYSTEM_PREFERRED),
                        eq(false),
                        eq(false),
                        eq(mMockDefaultNetwork),
                        any());
    }

    @Test
    public void testOnOpenedTunnelMetricsData() throws Exception {
        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(TEST_APN_NAME));
        mEpdgTunnelManager.bringUpTunnel(
                getBasicTunnelSetupRequest(TEST_APN_NAME, ApnSetting.PROTOCOL_IP),
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics);
        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(TEST_APN_NAME, mMockDefaultNetwork);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(TEST_APN_NAME, childSessionCallback);
        mTestLooper.dispatchAll();

        ArgumentCaptor<OnOpenedMetrics> metricsCaptor =
                ArgumentCaptor.forClass(OnOpenedMetrics.class);
        verify(mMockIwlanTunnelMetrics, times(1)).onOpened(metricsCaptor.capture());
        assertEquals(TEST_APN_NAME, metricsCaptor.getValue().getApnName());
    }

    @Test
    public void testCloseTunnelWithIkeInitTimeout() throws Exception {
        String testApnName = "www.xyz.com";
        IwlanError error = new IwlanError(IwlanError.IKE_INIT_TIMEOUT, mMockIkeIoException);
        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testApnName), eq(error));

        setupTunnelBringup();

        ArgumentCaptor<EpdgTunnelManager.TmIkeSessionCallback> ikeSessionCallbackCaptor =
                ArgumentCaptor.forClass(EpdgTunnelManager.TmIkeSessionCallback.class);
        verify(mMockIkeSessionCreator, atLeastOnce())
                .createIkeSession(
                        eq(mMockContext),
                        any(IkeSessionParams.class),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        ikeSessionCallbackCaptor.capture(),
                        any(ChildSessionCallback.class));
        ikeSessionCallbackCaptor.getValue().onClosedWithException(mMockIkeIoException);
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, times(1)).reportIwlanError(eq(testApnName), eq(error));
        verify(mMockEpdgSelector).onEpdgConnectionFailed(eq(EXPECTED_EPDG_ADDRESSES.get(0)));
        verify(mMockEpdgSelector, times(0)).onEpdgConnectedSuccessfully();
        verify(mMockIwlanTunnelCallback, atLeastOnce()).onClosed(eq(testApnName), eq(error));
    }

    @Test
    public void testCloseTunnelWithIkeDpdTimeout() throws Exception {
        IwlanError error = new IwlanError(IwlanError.IKE_DPD_TIMEOUT, mMockIkeIoException);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(TEST_APN_NAME, mMockDefaultNetwork);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(TEST_APN_NAME, childSessionCallback);
        mEpdgTunnelManager
                .getTmIkeSessionCallback(
                        TEST_APN_NAME, mEpdgTunnelManager.getCurrentTokenForApn(TEST_APN_NAME))
                .onClosedWithException(mMockIkeIoException);
        mTestLooper.dispatchAll();

        verify(mEpdgTunnelManager, never()).reportIwlanError(eq(TEST_APN_NAME), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
    }

    @Test
    public void testCloseTunnelWithIkeMobilityTimeout() throws Exception {
        IwlanError error = new IwlanError(IwlanError.IKE_MOBILITY_TIMEOUT, mMockIkeIoException);

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(
                        TEST_APN_NAME, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        verifyTunnelOnOpened(TEST_APN_NAME, childSessionCallback);

        Network newNetwork = mock(Network.class);
        mEpdgTunnelManager.updateNetwork(newNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager
                .getTmIkeSessionCallback(
                        TEST_APN_NAME, mEpdgTunnelManager.getCurrentTokenForApn(TEST_APN_NAME))
                .onClosedWithException(mMockIkeIoException);
        mTestLooper.dispatchAll();

        verify(mMockIkeSession, times(1)).setNetwork(eq(newNetwork));
        verify(mEpdgTunnelManager, never()).reportIwlanError(eq(TEST_APN_NAME), eq(error));
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(TEST_APN_NAME), eq(error));
    }

    @Test
    public void testUpdateNetworkToOpenedTunnel() throws Exception {
        String apnName = "ims";

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(
                apnName, InetAddresses.parseNumericAddress(EPDG_ADDRESS));
        mEpdgTunnelManager.onConnectedToEpdg(true);
        Network newNetwork = mock(Network.class);
        mEpdgTunnelManager.updateNetwork(newNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, times(1)).setNetwork(eq(newNetwork));
    }

    @Test
    public void testUpdateNetworkForIncomingSetupRequest() throws Exception {
        String apnName = "ims";
        Network newNetwork = mock(Network.class);

        mEpdgTunnelManager.updateNetwork(newNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();

        verify(mMockEpdgSelector, times(1))
                .getValidatedServerList(
                        anyInt(), /* transactionId */
                        anyInt(), /* filter */
                        anyInt(), /* order */
                        eq(false), /* isRoaming */
                        eq(false), /* isEmergency */
                        eq(newNetwork),
                        any(EpdgSelector.EpdgSelectorCallback.class));
        IkeSessionParams ikeSessionParams =
                ikeSessionArgumentCaptors.mIkeSessionParamsCaptor.getValue();
        assertEquals(ikeSessionParams.getNetwork(), newNetwork);
    }

    @Test
    public void testUpdateNullNetworkToOpenedTunnel() throws Exception {
        String apnName = "ims";

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.updateNetwork(null, null);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, never()).setNetwork(any());
    }

    @Test
    public void testUpdateNullNetworkAndRejectIncomingSetupRequest() throws Exception {
        String apnName = "ims";

        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(apnName), any(IwlanError.class));

        mEpdgTunnelManager.updateNetwork(null, null);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.bringUpTunnel(
                getBasicTunnelSetupRequest(apnName, ApnSetting.PROTOCOL_IP),
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1)).onClosed(eq(apnName), any(IwlanError.class));
    }

    @Test
    public void testUpdateUnreachableLinkProperties() throws Exception {
        String apnName = "ims";

        IkeSessionArgumentCaptors ikeSessionArgumentCaptors =
                verifyBringUpTunnelWithDnsQuery(apnName, mMockDefaultNetwork, mMockIkeSession);
        ChildSessionCallback childSessionCallback =
                ikeSessionArgumentCaptors.mChildSessionCallbackCaptor.getValue();
        childSessionCallback.onIpSecTransformCreated(
                mMockedIpSecTransformIn, IpSecManager.DIRECTION_IN);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(
                apnName, InetAddresses.parseNumericAddress(EPDG_ADDRESS));
        mEpdgTunnelManager.onConnectedToEpdg(true);
        Network newNetwork = mock(Network.class);
        LinkProperties mockUnreachableLinkProperties = mock(LinkProperties.class);
        when(mockUnreachableLinkProperties.isReachable(any())).thenReturn(false);
        mEpdgTunnelManager.updateNetwork(newNetwork, mockUnreachableLinkProperties);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, never()).setNetwork(eq(newNetwork));

        mEpdgTunnelManager.updateNetwork(newNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, times(1)).setNetwork(eq(newNetwork));
    }

    @Test
    public void testNetworkValidationSuccess() throws Exception {
        String testApnName = "ims";
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        mEpdgTunnelManager.requestNetworkValidationForApn(testApnName);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, times(1)).requestLivenessCheck();

        int[][] orderedUpdateEvents = {
            {
                IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_STARTED,
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                1
            },
            {
                IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_ONGOING,
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                2
            },
            {
                IkeSessionCallback.LIVENESS_STATUS_SUCCESS,
                PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS,
                1
            },
        };

        for (int[] event : orderedUpdateEvents) {
            int testedLivenessStatus = event[0];
            int expectedNetworkVadlidationState = event[1];
            int numOfSameStatus = event[2];
            mEpdgTunnelManager
                    .getTmIkeSessionCallback(testApnName, token)
                    .onLivenessStatusChanged(testedLivenessStatus);
            mTestLooper.dispatchAll();
            verify(mMockIwlanTunnelCallback, times(numOfSameStatus))
                    .onNetworkValidationStatusChanged(
                            eq(testApnName), eq(expectedNetworkVadlidationState));
        }
    }

    @Test
    public void testNetworkValidationFailed() throws Exception {
        String testApnName = "ims";
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());

        mEpdgTunnelManager.requestNetworkValidationForApn(testApnName);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, times(1)).requestLivenessCheck();

        int[][] orderedUpdateEvents = {
            {
                IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_STARTED,
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                1
            },
            {
                IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_ONGOING,
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                2
            },
            {
                IkeSessionCallback.LIVENESS_STATUS_FAILURE,
                PreciseDataConnectionState.NETWORK_VALIDATION_FAILURE,
                1
            }
        };
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        for (int[] event : orderedUpdateEvents) {
            int testedLivenessStatus = event[0];
            int expectedNetworkVadlidationState = event[1];
            int numOfSameStatus = event[2];
            mEpdgTunnelManager
                    .getTmIkeSessionCallback(testApnName, token)
                    .onLivenessStatusChanged(testedLivenessStatus);
            mTestLooper.dispatchAll();
            verify(mMockIwlanTunnelCallback, times(numOfSameStatus))
                    .onNetworkValidationStatusChanged(
                            eq(testApnName), eq(expectedNetworkVadlidationState));
        }
    }

    @Test
    public void testOnBackgroundLivenessCheckUpdate() throws Exception {
        String testApnName = "ims";
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);

        int[][] orderedUpdateEvents = {
            {
                IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_STARTED,
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                1
            },
            {
                IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_ONGOING,
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                2
            },
            {
                IkeSessionCallback.LIVENESS_STATUS_SUCCESS,
                PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS,
                1
            }
        };

        for (int[] event : orderedUpdateEvents) {
            int testedLivenessStatus = event[0];
            int expectedNetworkVadlidationState = event[1];
            int numOfSameStatus = event[2];
            mEpdgTunnelManager
                    .getTmIkeSessionCallback(testApnName, token)
                    .onLivenessStatusChanged(testedLivenessStatus);
            mTestLooper.dispatchAll();
            verify(mMockIwlanTunnelCallback, times(numOfSameStatus))
                    .onNetworkValidationStatusChanged(
                            eq(testApnName), eq(expectedNetworkVadlidationState));
        }
    }

    @Test
    public void testLivenessCheckUpdateWithUnknownStatus() throws Exception {
        String testApnName = "ims";
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                testApnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIpv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                false /* isEmergency */,
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());
        int token = mEpdgTunnelManager.incrementAndGetCurrentTokenForApn(testApnName);
        int unknown_liveness_status = 9999;

        mEpdgTunnelManager
                .getTmIkeSessionCallback(testApnName, token)
                .onLivenessStatusChanged(unknown_liveness_status);
        mTestLooper.dispatchAll();
        verify(mMockIwlanTunnelCallback, times(1))
                .onNetworkValidationStatusChanged(
                        eq(testApnName), eq(PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS));
    }

    @Test
    public void testRequestNetworkValidationWithNoActiveApn() {
        String testApnName = "ims";
        mEpdgTunnelManager.requestNetworkValidationForApn(testApnName);
        mTestLooper.dispatchAll();
        verify(mMockIkeSession, never()).requestLivenessCheck();
    }

    private TunnelSetupRequest getBasicEmergencyTunnelSetupRequest(String apnName) {
        return TunnelSetupRequest.builder()
                .setApnName(apnName)
                .setIsRoaming(false)
                .setIsEmergency(true)
                .setRequestPcscf(true)
                .setApnIpProtocol(ApnSetting.PROTOCOL_IP)
                .setPduSessionId(1)
                .build();
    }

    private void setOneEmeregencyTunnelOpened(String apnName, InetAddress epdgAddress)
            throws Exception {
        mEpdgTunnelManager.putApnNameToTunnelConfig(
                apnName,
                mMockIkeSession,
                mMockIwlanTunnelCallback,
                mMockIwlanTunnelMetrics,
                mMockIpSecTunnelInterface,
                null /* srcIPv6Addr */,
                0 /* srcIPv6AddrPrefixLen */,
                true /* isEmergency */,
                epdgAddress);
        mEpdgTunnelManager.mEpdgMonitor.onApnConnectToEpdg(apnName, epdgAddress);
        mEpdgTunnelManager.onConnectedToEpdg(true);
    }

    @Test
    public void testEmergencyPdnEstablishWithImsPdn_Success() throws Exception {
        when(mFakeFeatureFlags.distinctEpdgSelectionForEmergencySessions()).thenReturn(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL, true);
        String testImsApnName = "testIms";
        String testEmergencyApnName = "testSos";
        setOneTunnelOpened(testImsApnName);
        mEpdgTunnelManager.updateNetwork(mMockDefaultNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();
        // Verify IMS PDN established
        assertTrue(mEpdgTunnelManager.mEpdgMonitor.hasEpdgConnectedForNormalSession());
        assertFalse(mEpdgTunnelManager.mEpdgMonitor.hasSeparateEpdgConnectedForEmergencySession());

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testEmergencyApnName));
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicEmergencyTunnelSetupRequest(testEmergencyApnName),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(EPDG_ADDRESS, ikeSessionParams.getServerHostname());
    }

    @Test
    public void testEmergencyPdnFailedEstablishWithImsPdn_establishWithSeparateEpdg()
            throws Exception {
        when(mFakeFeatureFlags.distinctEpdgSelectionForEmergencySessions()).thenReturn(true);
        when(mFakeFeatureFlags.epdgSelectionExcludeFailedIpAddress()).thenReturn(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL, true);
        assertTrue(
                IwlanCarrierConfig.getConfigBoolean(
                        mMockContext,
                        DEFAULT_SLOT_INDEX,
                        IwlanCarrierConfig.KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL));
        String testImsApnName = "testIms";
        String testEmergencyApnName = "testSos";

        setOneTunnelOpened(testImsApnName);
        mEpdgTunnelManager.updateNetwork(mMockDefaultNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();
        // Verify IMS PDN established
        assertTrue(mEpdgTunnelManager.mEpdgMonitor.hasEpdgConnectedForNormalSession());
        assertFalse(mEpdgTunnelManager.mEpdgMonitor.hasSeparateEpdgConnectedForEmergencySession());

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testEmergencyApnName));
        IwlanError error =
                new IwlanError(IwlanError.IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED);
        doReturn(0L).when(mEpdgTunnelManager).reportIwlanError(eq(testEmergencyApnName), eq(error));

        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicEmergencyTunnelSetupRequest(testEmergencyApnName),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(EPDG_ADDRESS, ikeSessionParams.getServerHostname());
        assertFalse(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT));

        mEpdgTunnelManager.getTmIkeSessionCallback(testEmergencyApnName, DEFAULT_TOKEN).onClosed();
        mTestLooper.dispatchAll();

        ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicEmergencyTunnelSetupRequest(testEmergencyApnName),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        mEpdgTunnelManager.sendSelectionRequestComplete(
                EXPECTE_EPDG_ADDRESSES_FOR_EMERGENCY_SESSION,
                new IwlanError(IwlanError.NO_ERROR),
                1);
        mTestLooper.dispatchAll();

        verify(mMockIkeSessionCreator, times(2))
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY, ikeSessionParams.getServerHostname());
        assertTrue(ikeSessionParams.hasIkeOption(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT));
    }

    private void bringUpImsPdnAndEmergencyPdnWithDifferentEpdgs() throws Exception {
        when(mFakeFeatureFlags.distinctEpdgSelectionForEmergencySessions()).thenReturn(true);
        String testImsApnName = "testIms";
        String testEmergencyApnName = "testSos";
        setOneTunnelOpened(testImsApnName);
        mEpdgTunnelManager.updateNetwork(mMockDefaultNetwork, mMockLinkProperties);
        mTestLooper.dispatchAll();
        setOneEmeregencyTunnelOpened(
                testEmergencyApnName,
                InetAddresses.parseNumericAddress(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY));
        mTestLooper.dispatchAll();

        // Verify IMS PDN established on ePDG A, emergency PDN established on ePDG B
        assertTrue(mEpdgTunnelManager.mEpdgMonitor.hasEpdgConnectedForNormalSession());
        assertEquals(
                InetAddresses.parseNumericAddress(EPDG_ADDRESS),
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());
        assertTrue(mEpdgTunnelManager.mEpdgMonitor.hasSeparateEpdgConnectedForEmergencySession());
        assertEquals(
                InetAddresses.parseNumericAddress(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY),
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForEmergencySession());
    }

    @Test
    public void testMmsPdnEstablishWithEmergencySessionEpdgNotNormalSessionEpdg_Success()
            throws Exception {
        when(mFakeFeatureFlags.distinctEpdgSelectionForEmergencySessions()).thenReturn(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL, true);
        String testMmsApnName = "testMms";
        bringUpImsPdnAndEmergencyPdnWithDifferentEpdgs();

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testMmsApnName));
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(testMmsApnName, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY, ikeSessionParams.getServerHostname());
    }

    @Test
    public void testImsPdnReestablishWithEpdgForEmergencySession() throws Exception {
        when(mFakeFeatureFlags.distinctEpdgSelectionForEmergencySessions()).thenReturn(true);
        IwlanCarrierConfig.putTestConfigBoolean(
                IwlanCarrierConfig.KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL, true);
        String testImsApnName = "testIms";
        bringUpImsPdnAndEmergencyPdnWithDifferentEpdgs();

        mEpdgTunnelManager.getTmIkeSessionCallback(testImsApnName, DEFAULT_TOKEN).onClosed();
        mEpdgTunnelManager.removeApnNameInTunnelConfig(testImsApnName);
        mEpdgTunnelManager.mEpdgMonitor.onApnDisconnectFromEpdg(testImsApnName);
        mTestLooper.dispatchAll();

        assertTrue(mEpdgTunnelManager.mEpdgMonitor.hasEpdgConnectedForNormalSession());
        assertFalse(mEpdgTunnelManager.mEpdgMonitor.hasSeparateEpdgConnectedForEmergencySession());
        assertEquals(
                InetAddresses.parseNumericAddress(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY),
                mEpdgTunnelManager.mEpdgMonitor.getEpdgAddressForNormalSession());

        doReturn(true).when(mEpdgTunnelManager).canBringUpTunnel(eq(testImsApnName));
        boolean ret =
                mEpdgTunnelManager.bringUpTunnel(
                        getBasicTunnelSetupRequest(testImsApnName, ApnSetting.PROTOCOL_IP),
                        mMockIwlanTunnelCallback,
                        mMockIwlanTunnelMetrics);
        assertTrue(ret);
        mTestLooper.dispatchAll();

        ArgumentCaptor<IkeSessionParams> ikeSessionParamsCaptor =
                ArgumentCaptor.forClass(IkeSessionParams.class);
        verify(mMockIkeSessionCreator)
                .createIkeSession(
                        eq(mMockContext),
                        ikeSessionParamsCaptor.capture(),
                        any(ChildSessionParams.class),
                        any(Executor.class),
                        any(IkeSessionCallback.class),
                        any(ChildSessionCallback.class));
        IkeSessionParams ikeSessionParams = ikeSessionParamsCaptor.getValue();
        assertEquals(SEPARATE_EPDG_ADDRESS_FOR_EMERGENCY, ikeSessionParams.getServerHostname());
    }
}
