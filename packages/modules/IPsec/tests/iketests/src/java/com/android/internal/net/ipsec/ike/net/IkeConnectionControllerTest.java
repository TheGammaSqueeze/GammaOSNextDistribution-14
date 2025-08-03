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

package com.android.internal.net.ipsec.test.ike.net;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.ipsec.ike.IkeSessionParams.ESP_ENCAP_TYPE_AUTO;
import static android.net.ipsec.ike.IkeSessionParams.ESP_ENCAP_TYPE_NONE;
import static android.net.ipsec.ike.IkeSessionParams.ESP_ENCAP_TYPE_UDP;
import static android.net.ipsec.ike.IkeSessionParams.ESP_IP_VERSION_AUTO;
import static android.net.ipsec.ike.IkeSessionParams.ESP_IP_VERSION_IPV4;
import static android.net.ipsec.ike.IkeSessionParams.ESP_IP_VERSION_IPV6;
import static android.net.ipsec.ike.IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_NATT_KEEPALIVE_DELAY_SEC_MAX;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_NATT_KEEPALIVE_DELAY_SEC_MIN;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_AUTOMATIC_ADDRESS_FAMILY_SELECTION;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_AUTOMATIC_NATT_KEEPALIVES;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_FORCE_PORT_4500;

import static com.android.internal.net.ipsec.test.ike.IkeContext.CONFIG_AUTO_NATT_KEEPALIVES_CELLULAR_TIMEOUT_OVERRIDE_SECONDS;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.AUTO_KEEPALIVE_DELAY_SEC_CELL;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.AUTO_KEEPALIVE_DELAY_SEC_WIFI;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_TRAVERSAL_SUPPORT_NOT_CHECKED;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_TRAVERSAL_UNSUPPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ipsec.test.ike.IkeManager;
import android.net.ipsec.test.ike.IkeSessionParams;
import android.net.ipsec.test.ike.exceptions.IkeException;
import android.net.ipsec.test.ike.exceptions.IkeIOException;
import android.net.ipsec.test.ike.exceptions.IkeInternalException;
import android.os.Build.VERSION_CODES;
import android.os.Looper;

import com.android.internal.net.TestUtils;
import com.android.internal.net.ipsec.test.ike.IkeContext;
import com.android.internal.net.ipsec.test.ike.IkeSessionTestBase;
import com.android.internal.net.ipsec.test.ike.IkeSocket;
import com.android.internal.net.ipsec.test.ike.IkeSocketConfig;
import com.android.internal.net.ipsec.test.ike.IkeUdp4Socket;
import com.android.internal.net.ipsec.test.ike.IkeUdp6Socket;
import com.android.internal.net.ipsec.test.ike.IkeUdp6WithEncapPortSocket;
import com.android.internal.net.ipsec.test.ike.IkeUdpEncapSocket;
import com.android.internal.net.ipsec.test.ike.SaRecord.IkeSaRecord;
import com.android.internal.net.ipsec.test.ike.keepalive.IkeNattKeepalive;
import com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;
import com.android.internal.net.ipsec.test.ike.utils.RandomnessFactory;
import com.android.modules.utils.build.SdkLevel;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashSet;

public class IkeConnectionControllerTest extends IkeSessionTestBase {
    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final long IKE_LOCAL_SPI = 11L;

    private static final int ESP_IP_VERSION_NONE = -1;

    private static final int FAKE_SESSION_ID = 0;
    private static final int MOCK_ALARM_CMD = 1;
    private static final int MOCK_KEEPALIVE_CMD = 2;

    private static final int DSCP_FIELD = 50;

    private static final int KEEPALIVE_DELAY_CALLER_CONFIGURED = 50;

    private IkeSessionParams mMockIkeParams;
    private IkeAlarmConfig mMockAlarmConfig;
    private IkeNattKeepalive mMockIkeNattKeepalive;
    private IkeConnectionController.Callback mMockConnectionCtrlCb;
    private IkeConnectionController.Dependencies mMockConnectionCtrlDeps;
    private Network mMockCallerConfiguredNetwork;
    private IkeSaRecord mMockIkeSaRecord;

    private IkeUdp4Socket mMockIkeUdp4Socket;
    private IkeUdp6Socket mMockIkeUdp6Socket;
    private IkeUdpEncapSocket mMockIkeUdpEncapSocket;
    private IkeUdp6WithEncapPortSocket mMockIkeUdp6WithEncapPortSocket;

    private IkeContext mIkeContext;
    private IkeConnectionController mIkeConnectionCtrl;

    private IkeConnectionController buildIkeConnectionCtrl() throws Exception {
        mMockConnectionCtrlCb = mock(IkeConnectionController.Callback.class);
        mMockConnectionCtrlDeps = mock(IkeConnectionController.Dependencies.class);

        when(mMockConnectionCtrlDeps.newIkeLocalAddressGenerator())
                .thenReturn(mMockIkeLocalAddressGenerator);
        when(mMockConnectionCtrlDeps.newIkeNattKeepalive(any(), any()))
                .thenReturn(mMockIkeNattKeepalive);

        when(mMockConnectionCtrlDeps.newIkeUdp4Socket(any(), any(), any()))
                .thenReturn(mMockIkeUdp4Socket);
        when(mMockConnectionCtrlDeps.newIkeUdp6Socket(any(), any(), any()))
                .thenReturn(mMockIkeUdp6Socket);
        when(mMockConnectionCtrlDeps.newIkeUdpEncapSocket(any(), any(), any(), any()))
                .thenReturn(mMockIkeUdpEncapSocket);
        when(mMockConnectionCtrlDeps.newIkeUdp6WithEncapPortSocket(any(), any(), any()))
                .thenReturn(mMockIkeUdp6WithEncapPortSocket);

        return new IkeConnectionController(
                mIkeContext,
                new IkeConnectionController.Config(
                        mMockIkeParams, FAKE_SESSION_ID, MOCK_ALARM_CMD, MOCK_KEEPALIVE_CMD,
                        mMockConnectionCtrlCb),
                mMockConnectionCtrlDeps);
    }

    private IkeConnectionController buildIkeConnectionCtrlWithNetwork(Network callerConfiguredNw)
            throws Exception {
        when(mMockIkeParams.getConfiguredNetwork()).thenReturn(callerConfiguredNw);

        Network networkBeingUsed =
                callerConfiguredNw == null ? mMockDefaultNetwork : callerConfiguredNw;
        setupLocalAddressForNetwork(networkBeingUsed, LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(networkBeingUsed, REMOTE_ADDRESS);

        return buildIkeConnectionCtrl();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mIkeContext =
                new IkeContext(mock(Looper.class), mSpyContext, mock(RandomnessFactory.class));
        mMockIkeParams = mock(IkeSessionParams.class);
        mMockAlarmConfig = mock(IkeAlarmConfig.class);
        mMockIkeNattKeepalive = mock(IkeNattKeepalive.class);
        mMockCallerConfiguredNetwork = mock(Network.class);
        mMockIkeSaRecord = mock(IkeSaRecord.class);

        mMockIkeUdp4Socket = newMockIkeSocket(IkeUdp4Socket.class);
        mMockIkeUdp6Socket = newMockIkeSocket(IkeUdp6Socket.class);
        mMockIkeUdpEncapSocket = newMockIkeSocket(IkeUdpEncapSocket.class);
        mMockIkeUdp6WithEncapPortSocket = newMockIkeSocket(IkeUdp6WithEncapPortSocket.class);

        resetMockIkeParams();

        setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS);

        when(mMockIkeSaRecord.getLocalSpi()).thenReturn(IKE_LOCAL_SPI);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        verify(mMockIkeParams).getNattKeepAliveDelaySeconds();
        mIkeConnectionCtrl.registerIkeSaRecord(mMockIkeSaRecord);
    }

    private void resetMockIkeParams() {
        reset(mMockIkeParams);
        mMockIkeParams = mock(IkeSessionParams.class);
        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_FORCE_PORT_4500))).thenReturn(false);
        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_AUTOMATIC_ADDRESS_FAMILY_SELECTION)))
                .thenReturn(false);
        when(mMockIkeParams.getServerHostname()).thenReturn(REMOTE_HOSTNAME);
        when(mMockIkeParams.getConfiguredNetwork()).thenReturn(null);
        when(mMockIkeParams.getIpVersion()).thenReturn(ESP_IP_VERSION_AUTO);
        when(mMockIkeParams.getEncapType()).thenReturn(ESP_ENCAP_TYPE_AUTO);
        when(mMockIkeParams.getDscp()).thenReturn(DSCP_FIELD);
    }

    @After
    public void tearDown() throws Exception {
        mIkeConnectionCtrl.tearDown();
    }

    private void verifyKeepalive(boolean hasOldKeepalive, boolean isKeepaliveExpected)
            throws Exception {
        if (isKeepaliveExpected) {
            assertNotNull(mIkeConnectionCtrl.getIkeNattKeepalive());

            if (hasOldKeepalive) {
                verify(mMockIkeNattKeepalive).restart(any());
            } else {
                verify(mMockConnectionCtrlDeps).newIkeNattKeepalive(any(), any());
            }
        } else {
            if (hasOldKeepalive) {
                verify(mMockIkeNattKeepalive).stop();
            }
            assertNull(mIkeConnectionCtrl.getIkeNattKeepalive());
        }
    }

    private void verifySocketBoundToNetwork(IkeSocket socket, Network network) throws Exception {
        verify(socket, atLeastOnce()).bindToNetwork(network);
    }

    private void verifySetup(
            Network expectedNetwork,
            InetAddress expectedLocalAddress,
            InetAddress expectedRemoteAddress,
            Class<? extends IkeSocket> socketType)
            throws Exception {
        assertEquals(expectedNetwork, mIkeConnectionCtrl.getNetwork());
        assertEquals(expectedLocalAddress, mIkeConnectionCtrl.getLocalAddress());
        assertEquals(expectedRemoteAddress, mIkeConnectionCtrl.getRemoteAddress());
        assertTrue(socketType.isInstance(mIkeConnectionCtrl.getIkeSocket()));
        assertEquals(NAT_TRAVERSAL_SUPPORT_NOT_CHECKED, mIkeConnectionCtrl.getNatStatus());
        verifyKeepalive(
                false /* hasOldKeepalive */,
                mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);

        verifyDscpSetForSocket(socketType);
        verifySocketBoundToNetwork(mIkeConnectionCtrl.getIkeSocket(), expectedNetwork);
    }

    private void verifyDscpSetForSocket(Class<? extends IkeSocket> socketType) throws Exception {
        ArgumentCaptor<IkeSocketConfig> socketConfig =
                ArgumentCaptor.forClass(IkeSocketConfig.class);

        assertEquals(DSCP_FIELD, mIkeConnectionCtrl.getDscp());
        if (socketType.equals(IkeUdpEncapSocket.class)) {
            verify(mMockConnectionCtrlDeps)
                    .newIkeUdpEncapSocket(socketConfig.capture(), any(), any(), any());
        } else if (socketType.equals(IkeUdp4Socket.class))  {
            verify(mMockConnectionCtrlDeps)
                    .newIkeUdp4Socket(socketConfig.capture(), any(), any());
        } else if (socketType.equals(IkeUdp6WithEncapPortSocket.class)) {
            verify(mMockConnectionCtrlDeps)
                    .newIkeUdp6WithEncapPortSocket(socketConfig.capture(), any(), any());
        } else if (socketType.equals(IkeUdp6Socket.class)) {
            verify(mMockConnectionCtrlDeps)
                    .newIkeUdp6Socket(socketConfig.capture(), any(), any());
        }
        assertEquals(DSCP_FIELD, socketConfig.getValue().getDscp());
    }

    private void resetMockIkeSockets() {
        resetMockIkeSocket(mMockIkeUdp4Socket);
        resetMockIkeSocket(mMockIkeUdp6Socket);
        resetMockIkeSocket(mMockIkeUdpEncapSocket);
        resetMockIkeSocket(mMockIkeUdp6WithEncapPortSocket);
    }

    private void verifyTearDown() {
        verify(mMockConnectManager).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mMockIkeUdp4Socket).releaseReference(mIkeConnectionCtrl);
    }

    private void verifySetupAndTeardownWithNw(Network callerConfiguredNw) throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration and IkeSessionParams query in #setUp()
        resetMockConnectManager();
        resetMockIkeParams();
        resetMockIkeSockets();

        mIkeConnectionCtrl = buildIkeConnectionCtrlWithNetwork(callerConfiguredNw);
        mIkeConnectionCtrl.setUp();

        Network expectedNetwork =
                callerConfiguredNw == null ? mMockDefaultNetwork : callerConfiguredNw;

        ArgumentCaptor<IkeNetworkCallbackBase> networkCallbackCaptor =
                ArgumentCaptor.forClass(IkeNetworkCallbackBase.class);

        if (callerConfiguredNw == null) {
            verify(mMockConnectManager)
                    .registerDefaultNetworkCallback(networkCallbackCaptor.capture(), any());
        } else {
            verify(mMockConnectManager)
                    .registerNetworkCallback(any(), networkCallbackCaptor.capture(), any());
        }

        IkeNetworkCallbackBase nwCallback = networkCallbackCaptor.getValue();
        nwCallback.onAvailable(expectedNetwork);
        nwCallback.onLinkPropertiesChanged(
                expectedNetwork, mMockConnectManager.getLinkProperties(expectedNetwork));
        nwCallback.onCapabilitiesChanged(
                expectedNetwork, mMockConnectManager.getNetworkCapabilities(expectedNetwork));

        verifySetup(expectedNetwork, LOCAL_ADDRESS, REMOTE_ADDRESS, IkeUdp4Socket.class);
        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkDied(any());
        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkUpdated();

        mIkeConnectionCtrl.tearDown();
        verifyTearDown();
    }

    private void verifyTearDownInSecondSetup(Network callerConfiguredNw) throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        mIkeConnectionCtrl = buildIkeConnectionCtrlWithNetwork(callerConfiguredNw);
        mIkeConnectionCtrl.setUp();
        mIkeConnectionCtrl.setUp();

        verifyTearDown();
    }

    private Class<? extends IkeSocket> getExpectedSocketType(boolean isIpv4, boolean force4500) {
        if (force4500) {
            if (isIpv4) {
                return IkeUdpEncapSocket.class;
            } else {
                return IkeUdp6WithEncapPortSocket.class;
            }
        } else {
            if (isIpv4) {
                return IkeUdp4Socket.class;
            } else {
                return IkeUdp6Socket.class;
            }
        }
    }

    private void verifySetupAndTeardownWithIpVersionAndPort(boolean isIpv4, boolean force4500)
            throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration and IkeSessionParams query in #setUp()
        resetMockConnectManager();
        resetMockIkeParams();

        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_FORCE_PORT_4500))).thenReturn(force4500);

        InetAddress expectedLocalAddress = isIpv4 ? LOCAL_ADDRESS : LOCAL_ADDRESS_V6;
        InetAddress expectedRemoteAddress = isIpv4 ? REMOTE_ADDRESS : REMOTE_ADDRESS_V6;
        setupLocalAddressForNetwork(mMockDefaultNetwork, expectedLocalAddress);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, expectedRemoteAddress);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        verifySetup(
                mMockDefaultNetwork,
                expectedLocalAddress,
                expectedRemoteAddress,
                getExpectedSocketType(isIpv4, force4500));

        mIkeConnectionCtrl.tearDown();
        verify(mMockConnectManager).unregisterNetworkCallback(any(NetworkCallback.class));
    }

    @Test
    public void testSetupAndTeardownWithDefaultNw() throws Exception {
        verifySetupAndTeardownWithNw(null /* callerConfiguredNw */);
    }

    @Test
    public void testSetupAndTeardownWithConfiguredNw() throws Exception {
        verifySetupAndTeardownWithNw(mMockCallerConfiguredNetwork);
    }

    @Test
    public void testTearDownInSecondSetupWithDefaultNw() throws Exception {
        verifyTearDownInSecondSetup(null /* callerConfiguredNw */);
    }

    @Test
    public void testTearDownInSecondSetupWithConfiguredNw() throws Exception {
        verifyTearDownInSecondSetup(mMockCallerConfiguredNetwork);
    }

    @Test
    public void testSetupAndTeardownIpv4Force4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(true /* isIpv4 */, true /* force4500 */);
    }

    @Test
    public void testSetupAndTeardownIpv4NotForce4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(true /* isIpv4 */, false /* force4500 */);
    }

    @Test
    public void testSetupAndTeardownIpv6Force4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(false /* isIpv4 */, true /* force4500 */);
    }

    @Test
    public void testSetupAndTeardownIpv6NotForce4500() throws Exception {
        verifySetupAndTeardownWithIpVersionAndPort(false /* isIpv4 */, false /* force4500 */);
    }

    private void verifyIpFamilySelection(int ipVersion, boolean remoteHasV4, boolean remoteHasV6,
            int expectedIpVersion) throws Exception {
        verifyIpFamilySelection(ipVersion, ESP_ENCAP_TYPE_AUTO, remoteHasV4, remoteHasV6,
                expectedIpVersion);
    }

    private void verifyIpFamilySelection(
            int ipVersion, int encapType, boolean remoteHasV4, boolean remoteHasV6,
            int expectedIpVersion) throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration and IkeSessionParams query in #setUp()
        resetMockConnectManager();
        resetMockIkeParams();

        final InetAddress expectedLocalAddress;
        final InetAddress expectedRemoteAddress;

        if (remoteHasV4 && remoteHasV6) {
            setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS, LOCAL_ADDRESS_V6);
            setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS, REMOTE_ADDRESS_V6);
        } else if (remoteHasV4) {
            setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS);
            setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS);
        } else if (remoteHasV6) {
            setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS_V6);
            setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS_V6);
        } else {
            throw new IllegalArgumentException("Invalid test setup");
        }

        if (expectedIpVersion == ESP_IP_VERSION_IPV4) {
            expectedLocalAddress = LOCAL_ADDRESS;
            expectedRemoteAddress = REMOTE_ADDRESS;
        } else if (expectedIpVersion == ESP_IP_VERSION_IPV6) {
            expectedLocalAddress = LOCAL_ADDRESS_V6;
            expectedRemoteAddress = REMOTE_ADDRESS_V6;
        } else {
            expectedLocalAddress = null;
            expectedRemoteAddress = null;
        }

        when(mMockIkeParams.getIpVersion()).thenReturn(ipVersion);
        when(mMockIkeParams.getEncapType()).thenReturn(encapType);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();

        if (expectedLocalAddress == null) {
            try {
                mIkeConnectionCtrl.setUp();
                fail("Expected connection setup to fail");
            } catch (IkeIOException | IkeInternalException expected) {
                if (SdkLevel.isAtLeastT()) {
                    assertTrue(expected instanceof IkeIOException);
                } else {
                    assertTrue(expected instanceof IkeInternalException);
                }

                return;
            }
        }

        mIkeConnectionCtrl.setUp();

        boolean ipV4Expected = expectedLocalAddress instanceof Inet4Address;
        verifySetup(
                mMockDefaultNetwork,
                expectedLocalAddress,
                expectedRemoteAddress,
                getExpectedSocketType(ipV4Expected, false /* force4500 */));

        mIkeConnectionCtrl.tearDown();
        verify(mMockConnectManager).unregisterNetworkCallback(any(NetworkCallback.class));
    }

    @Test
    public void testIpFamilySelectionAutoEncapNoneWithIpV4IpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_NONE,
                true /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionAutoEncapNoneWithIpV4Remote() throws Exception {
        try {
            verifyIpFamilySelection(
                    ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_NONE,
                    true /* remoteHasV4 */, false /* remoteHasV6 */,
                    ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
            fail("IPv6 required but no global IPv6 address available should cause an exception");
        } catch (IkeIOException | IkeInternalException expected) {
            if (SdkLevel.isAtLeastT()) {
                assertTrue(expected instanceof IkeIOException);
            } else {
                assertTrue(expected instanceof IkeInternalException);
            }
        }
    }

    @Test
    public void testIpFamilySelectionAutoEncapNoneWithIpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_NONE,
                false /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionAutoEncapUdpWithIpV4IpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_UDP,
                true /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV4 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionAutoEncapUdpWithIpV4Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_UDP,
                true /* remoteHasV4 */, false /* remoteHasV6 */,
                ESP_IP_VERSION_IPV4 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionAutoEncapUdpWithIpV6Remote() throws Exception {
        try {
            verifyIpFamilySelection(
                    ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_UDP,
                    false /* remoteHasV4 */, true /* remoteHasV6 */,
                    ESP_IP_VERSION_IPV4 /* expectedIpVersion */);
            fail("IPv4 required but no global IPv4 address available should cause an exception");
        } catch (IkeIOException | IkeInternalException expected) {
            if (SdkLevel.isAtLeastT()) {
                assertTrue(expected instanceof IkeIOException);
            } else {
                assertTrue(expected instanceof IkeInternalException);
            }
        }
    }

    @Test
    public void testIpFamilySelectionV4WithIpV4IpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_IPV4, true /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV4 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionV4WithIpV4Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_IPV4, true /* remoteHasV4 */, false /* remoteHasV6 */,
                ESP_IP_VERSION_IPV4 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionV4WithIpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_IPV4, false /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_NONE /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionV6WithIpV4Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_IPV6, true /* remoteHasV4 */, false /* remoteHasV6 */,
                ESP_IP_VERSION_NONE /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionV6WithIpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_IPV6, false /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionV6WithIpV4IpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_IPV6, true /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionDisabledWithIpV4IpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, true /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionDisabledWithIpV4Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, true /* remoteHasV4 */, false /* remoteHasV6 */,
                ESP_IP_VERSION_IPV4 /* expectedIpVersion */);
    }

    @Test
    public void testIpFamilySelectionDisabledWithIpV6Remote() throws Exception {
        verifyIpFamilySelection(
                ESP_IP_VERSION_AUTO, false /* remoteHasV4 */, true /* remoteHasV6 */,
                ESP_IP_VERSION_IPV6 /* expectedIpVersion */);
    }

    private void verifyIsIpV4Preferred(
            boolean isAutoSelectionEnabled,
            int transportType,
            int originalIpVersion,
            @Nullable Integer updatedIpVersion,
            boolean expected)
            throws Exception {
        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        mIkeConnectionCtrl.enableMobility();
        final IkeSessionParams mockIkeParams = mock(IkeSessionParams.class);
        final NetworkCapabilities mockNc = mock(NetworkCapabilities.class);
        doReturn(isAutoSelectionEnabled).when(mockIkeParams)
                .hasIkeOption(IKE_OPTION_AUTOMATIC_ADDRESS_FAMILY_SELECTION);
        doReturn(originalIpVersion).when(mockIkeParams).getIpVersion();
        doReturn(true).when(mockNc).hasTransport(transportType);

        if (updatedIpVersion != null) {
            final Network n = new Network(100);
            doReturn(mMockNetworkCapabilities).when(mMockConnectManager).getNetworkCapabilities(n);
            doReturn(new LinkProperties()).when(mMockConnectManager).getLinkProperties(n);
            mIkeConnectionCtrl.onNetworkSetByUser(
                    new Network(100),
                    updatedIpVersion,
                    ESP_ENCAP_TYPE_AUTO,
                    300 /* keepaliveDelaySeconds */);
        }

        assertEquals(expected, mIkeConnectionCtrl.isIpV4Preferred(mockIkeParams, mockNc));
        mIkeConnectionCtrl.tearDown();
    }

    private void verifyIsIpV4Preferred(
            boolean isAutoSelectionEnabled, int transportType, boolean expected) throws Exception {
        verifyIsIpV4Preferred(
                isAutoSelectionEnabled, transportType, ESP_IP_VERSION_AUTO, null, expected);
    }

    @Test
    public void testIsIpV4Preferred_Auto_Wifi() throws Exception {
        verifyIsIpV4Preferred(true /* autoEnabled */, TRANSPORT_WIFI, true /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_NotAuto_Wifi() throws Exception {
        verifyIsIpV4Preferred(false /* autoEnabled */, TRANSPORT_WIFI, false /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_Auto_Cell() throws Exception {
        verifyIsIpV4Preferred(true /* autoEnabled */, TRANSPORT_CELLULAR, false /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_NotAuto_Cell() throws Exception {
        verifyIsIpV4Preferred(false /* autoEnabled */, TRANSPORT_CELLULAR, false /* expected */);
    }

    private void verifyIsIpV4Preferred(
            int transportType, int originalIpVersion, int updatedIpVersion, boolean expected)
            throws Exception {
        verifyIsIpV4Preferred(
                true /* isAutoSelectionEnabled */,
                transportType,
                originalIpVersion,
                updatedIpVersion,
                expected);
    }

    @Test
    public void testIsIpV4Preferred_AutoToAuto_Wifi() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_AUTO, ESP_IP_VERSION_AUTO, true /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_AutoToAuto_Cell() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_CELLULAR, ESP_IP_VERSION_AUTO, ESP_IP_VERSION_AUTO, false /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_AutoToV4() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_AUTO, ESP_IP_VERSION_IPV4, true /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_AutoToV6() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_AUTO, ESP_IP_VERSION_IPV6, false /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_V6ToVAuto() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_IPV6, ESP_IP_VERSION_AUTO, true /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_V4ToV4() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_IPV4, ESP_IP_VERSION_IPV4, true /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_V4ToV6() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_IPV4, ESP_IP_VERSION_IPV6, false /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_V6ToV4() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_IPV6, ESP_IP_VERSION_IPV4, true /* expected */);
    }

    @Test
    public void testIsIpV4Preferred_V6ToV6() throws Exception {
        verifyIsIpV4Preferred(
                TRANSPORT_WIFI, ESP_IP_VERSION_IPV6, ESP_IP_VERSION_IPV6, false /* expected */);
    }

    private void verifyUsedIpVersion(
            int requiredIpVersion,
            boolean isAutoSelectionEnabled,
            int transportType,
            boolean v4Available,
            int expectedIpVersion)
            throws Exception {
        mMockIkeParams = mock(IkeSessionParams.class);
        final NetworkCapabilities mockNc = mock(NetworkCapabilities.class);

        doReturn(requiredIpVersion).when(mMockIkeParams).getIpVersion();
        doReturn(isAutoSelectionEnabled).when(mMockIkeParams)
                .hasIkeOption(IKE_OPTION_AUTOMATIC_ADDRESS_FAMILY_SELECTION);
        doReturn(true).when(mockNc).hasTransport(transportType);

        final LinkProperties lp = new LinkProperties();
        final LinkAddress v4Addr = new LinkAddress("198.51.100.1/24");
        final LinkAddress v6Addr = new LinkAddress("2001:db8:1:3::1/64");
        if (v4Available) lp.addLinkAddress(v4Addr);
        lp.addLinkAddress(v6Addr);

        final IkeConnectionController controller = buildIkeConnectionCtrl();
        if (v4Available) controller.addRemoteAddress(v4Addr.getAddress());
        controller.addRemoteAddress(v6Addr.getAddress());
        controller.onCapabilitiesUpdated(mockNc);
        controller.selectAndSetRemoteAddress(lp);
        final InetAddress resultAddr = controller.getRemoteAddress();
        final int result;
        if (resultAddr instanceof Inet4Address) {
            result = ESP_IP_VERSION_IPV4;
        } else if (resultAddr instanceof Inet6Address) {
            result = ESP_IP_VERSION_IPV6;
        } else { // null, or some UFO address
            result = ESP_IP_VERSION_NONE;
        }
        assertEquals(expectedIpVersion, result);

        if (ESP_IP_VERSION_AUTO == requiredIpVersion) {
            verify(mMockIkeParams, atLeastOnce())
                    .hasIkeOption(IKE_OPTION_AUTOMATIC_ADDRESS_FAMILY_SELECTION);
            if (isAutoSelectionEnabled) verify(mockNc, atLeastOnce()).hasTransport(TRANSPORT_WIFI);
        } else {
            verify(mMockIkeParams, never())
                    .hasIkeOption(IKE_OPTION_AUTOMATIC_ADDRESS_FAMILY_SELECTION);
        }
    }

    private void verifyUsedIpVersion_withRequiredVersion(
            int requiredVersion,
            boolean hasV4,
            int expect) throws Exception {
        verifyUsedIpVersion(
                requiredVersion,
                true /* isAutoSelectionEnabled */,
                TRANSPORT_WIFI /* arbitrarily selected, doesn't really matter much */,
                hasV4,
                expect);
    }

    private void verifyUsedIpVersion_withAutoSelect(int transportType, boolean hasV4, int expect)
            throws Exception {
        verifyUsedIpVersion(
                ESP_IP_VERSION_AUTO,
                true /* isAutoSelectionEnabled */,
                transportType,
                hasV4,
                expect);
    }

    private void verifyUsedIpVersion_Default(boolean hasV4, int expect) throws Exception {
        verifyUsedIpVersion(
                ESP_IP_VERSION_AUTO,
                false /* isAutoSelectionEnabled */,
                TRANSPORT_WIFI /* arbitrarily selected */,
                hasV4,
                expect);
    }

    // To save test time and maintenance, don't test all combinations. Arbitrarily select a
    // representative combination of parameters that provide good coverage.
    @Test
    public void testIpVersion_withRequiredVersion_V4Required_HasV4() throws Exception {
        verifyUsedIpVersion_withRequiredVersion(
                ESP_IP_VERSION_IPV4 /* required */,
                true /* has v4 */,
                ESP_IP_VERSION_IPV4 /* expected */);
    }

    @Test
    public void testIpVersion_withRequiredVersion_V6Required_HasV4() throws Exception {
        verifyUsedIpVersion_withRequiredVersion(
                ESP_IP_VERSION_IPV6 /* required */,
                true /* has v4 */,
                ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testIpVersion_withRequiredVersion_V4Required_NotHasV4() throws Exception {
        assertThrows(IOException.class, () ->
                verifyUsedIpVersion_withRequiredVersion(
                        ESP_IP_VERSION_IPV4 /* required */,
                        false /* has v4 */,
                        ESP_IP_VERSION_NONE /* expected */));
    }

    @Test
    public void testIpVersion_withRequiredVersion_V6Required_NotHasV4() throws Exception {
        verifyUsedIpVersion_withRequiredVersion(
                ESP_IP_VERSION_IPV6 /* required */,
                false /* has v4 */,
                ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testIpVersion_withAutoSelect_Cell_HasV4() throws Exception {
        verifyUsedIpVersion_withAutoSelect(
                TRANSPORT_CELLULAR,
                true /* has v4 */,
                ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testIpVersion_withAutoSelect_WiFi_HasV4() throws Exception {
        verifyUsedIpVersion_withAutoSelect(
                TRANSPORT_WIFI,
                true /* has v4 */,
                ESP_IP_VERSION_IPV4 /* expected */);
    }

    @Test
    public void testIpVersion_withAutoSelect_Cell_NotHasV4() throws Exception {
        verifyUsedIpVersion_withAutoSelect(
                TRANSPORT_CELLULAR,
                false /* has v4 */,
                ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testIpVersion_withAutoSelect_WiFi_NotHasV4() throws Exception {
        verifyUsedIpVersion_withAutoSelect(
                TRANSPORT_WIFI,
                false /* has v4 */,
                ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testIpVersion_Default_HasV4() throws Exception {
        verifyUsedIpVersion_Default(true /* has V4 */, ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testIpVersion_Default_NotHasV4() throws Exception {
        verifyUsedIpVersion_Default(false /* has V4 */, ESP_IP_VERSION_IPV6 /* expected */);
    }

    @Test
    public void testSetupWithDnsFailure() throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        setupRemoteAddressForNetwork(mMockDefaultNetwork, new InetAddress[0]);
        mIkeConnectionCtrl = buildIkeConnectionCtrl();

        try {
            mIkeConnectionCtrl.setUp();
            fail("Expected to fail due to DNS failure");
        } catch (Exception expected) {

        }
    }

    @Test
    public void testSendIkePacket() throws Exception {
        byte[] ikePacket = "testSendIkePacket".getBytes();
        mIkeConnectionCtrl.sendIkePacket(ikePacket);

        verify(mMockIkeUdp4Socket).sendIkePacket(eq(ikePacket), eq(REMOTE_ADDRESS));
    }

    @Test
    public void testRegisterAndUnregisterIkeSpi() throws Exception {
        // Clear invocation in setup
        reset(mMockIkeUdp4Socket);

        mIkeConnectionCtrl.registerIkeSpi(IKE_LOCAL_SPI);
        verify(mMockIkeUdp4Socket).registerIke(IKE_LOCAL_SPI, mIkeConnectionCtrl);

        mIkeConnectionCtrl.unregisterIkeSpi(IKE_LOCAL_SPI);
        verify(mMockIkeUdp4Socket).unregisterIke(IKE_LOCAL_SPI);
    }

    @Test
    public void testRegisterAndUnregisterIkeSaRecord() throws Exception {
        // Clear invocation in setup
        reset(mMockIkeUdp4Socket);
        mIkeConnectionCtrl.registerIkeSaRecord(mMockIkeSaRecord);
        verify(mMockIkeUdp4Socket).registerIke(IKE_LOCAL_SPI, mIkeConnectionCtrl);
        HashSet<IkeSaRecord> expectedSet = new HashSet<>();
        expectedSet.add(mMockIkeSaRecord);
        assertEquals(expectedSet, mIkeConnectionCtrl.getIkeSaRecords());

        mIkeConnectionCtrl.unregisterIkeSaRecord(mMockIkeSaRecord);
        verify(mMockIkeUdp4Socket).unregisterIke(IKE_LOCAL_SPI);
        assertTrue(mIkeConnectionCtrl.getIkeSaRecords().isEmpty());
    }

    @Test
    public void testMarkSeverNattUnsupported() throws Exception {
        mIkeConnectionCtrl.markSeverNattUnsupported();

        assertEquals(NAT_TRAVERSAL_UNSUPPORTED, mIkeConnectionCtrl.getNatStatus());
    }

    @Test
    public void testHandleNatDetectionResultInIkeInit() throws Exception {
        // Clear call in IkeConnectionController#setUp()
        reset(mMockConnectionCtrlCb);

        // Either NAT detected or not detected won't affect the test since both cases indicate
        // the server support NAT-T
        mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                true /* isNatDetected */, IKE_LOCAL_SPI);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
        verifyKeepalive(false /* hasOldKeepalive */, true /* isKeepaliveExpected */);
    }

    private IkeDefaultNetworkCallback getDefaultNetworkCallback() throws Exception {
        ArgumentCaptor<IkeNetworkCallbackBase> networkCallbackCaptor =
                ArgumentCaptor.forClass(IkeNetworkCallbackBase.class);

        verify(mMockConnectManager)
                .registerDefaultNetworkCallback(networkCallbackCaptor.capture(), any());

        return (IkeDefaultNetworkCallback) networkCallbackCaptor.getValue();
    }

    @Test
    public void testNetworkLossWhenMobilityDisabled() throws Exception {
        getDefaultNetworkCallback().onLost(mMockDefaultNetwork);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    @Test
    public void testNetworkUpdateWhenMobilityDisabled() throws Exception {
        final IkeDefaultNetworkCallback callback = getDefaultNetworkCallback();
        final Network newNetwork = mock(Network.class);
        callback.onAvailable(newNetwork);
        callback.onCapabilitiesChanged(newNetwork, mock(NetworkCapabilities.class));
        callback.onLinkPropertiesChanged(newNetwork, mock(LinkProperties.class));
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    @Test
    public void testLinkPropertiesUpdateWhenMobilityDisabled() throws Exception {
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.addLinkAddress(mock(LinkAddress.class));
        getDefaultNetworkCallback().onLinkPropertiesChanged(mMockDefaultNetwork, linkProperties);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    private IkeNetworkCallbackBase enableMobilityAndReturnCb(boolean isDefaultNetwork)
            throws Exception {
        // Clear call in IkeConnectionController#setUp()
        reset(mMockConnectionCtrlCb);
        reset(mMockIkeNattKeepalive);

        mIkeConnectionCtrl.enableMobility();

        ArgumentCaptor<IkeNetworkCallbackBase> networkCallbackCaptor =
                ArgumentCaptor.forClass(IkeNetworkCallbackBase.class);

        if (isDefaultNetwork) {
            verify(mMockConnectManager)
                    .registerDefaultNetworkCallback(networkCallbackCaptor.capture(), any());
        } else {
            verify(mMockConnectManager)
                    .registerNetworkCallback(any(), networkCallbackCaptor.capture(), any());
        }

        return networkCallbackCaptor.getValue();
    }

    @Test
    public void testEnableMobilityWithDefaultNw() throws Exception {
        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        assertEquals(mMockDefaultNetwork, callback.getNetwork());
        assertEquals(LOCAL_ADDRESS, callback.getAddress());
    }

    @Test
    public void testEnableMobilityWithConfiguredNw() throws Exception {
        mIkeConnectionCtrl.tearDown();
        mIkeConnectionCtrl = buildIkeConnectionCtrlWithNetwork(mMockCallerConfiguredNetwork);
        mIkeConnectionCtrl.setUp();

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(false /* isDefaultNetwork */);
        assertEquals(mMockCallerConfiguredNetwork, callback.getNetwork());
        assertEquals(LOCAL_ADDRESS, callback.getAddress());
    }

    @Test
    public void testEnableMobilityWithServerSupportNatt() throws Exception {
        mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                true /* isNatDetected */, IKE_LOCAL_SPI);
        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
        verifyKeepalive(false /* hasOldKeepalive */, true /* isKeepaliveExpected */);
    }

    @Test
    public void testEnableMobilityWithServerNotSupportNatt() throws Exception {
        mIkeConnectionCtrl.markSeverNattUnsupported();
        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdp4Socket);
        verifyKeepalive(false /* hasOldKeepalive */, false /* isKeepaliveExpected */);
    }

    @Test
    public void handleNatDetectionResultInMobike() throws Exception {
        mIkeConnectionCtrl.handleNatDetectionResultInMobike(true /* isNatDetected */);

        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
        verifyKeepalive(false /* hasOldKeepalive */, true /* isKeepaliveExpected */);
    }

    private void onNetworkSetByUserWithDefaultParams(
            IkeConnectionController ikeConnectionCtrl, Network network) throws Exception {
        ikeConnectionCtrl.onNetworkSetByUser(
                network, ESP_IP_VERSION_AUTO, ESP_ENCAP_TYPE_AUTO, NATT_KEEPALIVE_INTERVAL_AUTO);
    }

    private void verifyNetworkAndAddressesAfterMobilityEvent(
            Network expectedNetwork,
            InetAddress expectedLocalAddress,
            InetAddress expectedRemoteAddress,
            IkeNetworkCallbackBase callback)
            throws Exception {
        assertEquals(expectedNetwork, mIkeConnectionCtrl.getNetwork());
        assertEquals(expectedLocalAddress, mIkeConnectionCtrl.getLocalAddress());
        assertEquals(expectedRemoteAddress, mIkeConnectionCtrl.getRemoteAddress());

        assertEquals(expectedNetwork, callback.getNetwork());
        assertEquals(expectedLocalAddress, callback.getAddress());
        verifySocketBoundToNetwork(mIkeConnectionCtrl.getIkeSocket(), expectedNetwork);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedWithNewNetwork() throws Exception {
        Network newNetwork = mock(Network.class);
        setupLocalAddressForNetwork(newNetwork, UPDATED_LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(newNetwork, REMOTE_ADDRESS);

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        onNetworkSetByUserWithDefaultParams(mIkeConnectionCtrl, newNetwork);

        // hasIkeOption and getNattKeepAliveDelaySeconds were already called once by
        // IkeConnectionController#setUp() so check they were called a second time
        verify(mMockIkeParams, times(2)).hasIkeOption(IKE_OPTION_AUTOMATIC_NATT_KEEPALIVES);
        verify(mMockIkeParams, times(2)).getNattKeepAliveDelaySeconds();
        verifyNetworkAndAddressesAfterMobilityEvent(
                newNetwork, UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, callback);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockIkeSaRecord).migrate(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedWithNewLp() throws Exception {
        reset(mMockDefaultNetwork);
        setupLocalAddressForNetwork(mMockDefaultNetwork, UPDATED_LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS);

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(
                mMockDefaultNetwork,
                mMockConnectManager.getLinkProperties(mMockDefaultNetwork),
                mMockNetworkCapabilities);

        verifyNetworkAndAddressesAfterMobilityEvent(
                mMockDefaultNetwork, UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, callback);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockIkeSaRecord).migrate(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    // Test updating network from IPv4 network to IPv6 network
    private void verifyUnderlyingNetworkUpdated(
            boolean force4500,
            boolean doesPeerSupportNatt,
            Class<? extends IkeSocket> expectedSocketType)
            throws Exception {
        mIkeConnectionCtrl.tearDown();

        // Clear the network callback registration call in #setUp()
        resetMockConnectManager();

        // Set up mIkeConnectionCtrl for the test case
        when(mMockIkeParams.hasIkeOption(eq(IKE_OPTION_FORCE_PORT_4500))).thenReturn(force4500);

        mIkeConnectionCtrl = buildIkeConnectionCtrl();
        mIkeConnectionCtrl.setUp();
        mIkeConnectionCtrl.registerIkeSaRecord(mMockIkeSaRecord);
        boolean hasKeepalivePostSetup = false;
        if (doesPeerSupportNatt) {
            // Either NAT detected or not detected won't affect the test since both cases indicate
            // the server support NAT-T
            mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                    true /* isNatDetected */, IKE_LOCAL_SPI);
            hasKeepalivePostSetup = true;
        } else {
            mIkeConnectionCtrl.markSeverNattUnsupported();
        }

        // Update network from IPv4 network to IPv6 network
        Network newNetwork = mock(Network.class);
        setupLocalAddressForNetwork(newNetwork, UPDATED_LOCAL_ADDRESS_V6);
        setupRemoteAddressForNetwork(newNetwork, REMOTE_ADDRESS_V6);
        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        // Clear call in IkeConnectionController#setUp() and
        // IkeConnectionController#enableMobility()
        reset(mMockConnectionCtrlCb);
        mIkeConnectionCtrl.onNetworkSetByUser(newNetwork,
                ESP_IP_VERSION_AUTO,
                ESP_ENCAP_TYPE_AUTO,
                NATT_KEEPALIVE_INTERVAL_AUTO);

        // Validation
        verifyNetworkAndAddressesAfterMobilityEvent(
                newNetwork, UPDATED_LOCAL_ADDRESS_V6, REMOTE_ADDRESS_V6, callback);
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockIkeSaRecord).migrate(UPDATED_LOCAL_ADDRESS_V6, REMOTE_ADDRESS_V6);
        assertTrue(expectedSocketType.isInstance(mIkeConnectionCtrl.getIkeSocket()));
        verifyKeepalive(
                hasKeepalivePostSetup,
                mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedForce4500NattSupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                true /* force4500 */,
                true /* doesPeerSupportNatt */,
                IkeUdp6WithEncapPortSocket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedForce4500NattUnsupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                true /* force4500 */,
                false /* doesPeerSupportNatt */,
                IkeUdp6WithEncapPortSocket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedNotForce4500NattSupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                false /* force4500 */,
                true /* doesPeerSupportNatt */,
                IkeUdp6WithEncapPortSocket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedNOtForce4500NattUnsupported() throws Exception {
        verifyUnderlyingNetworkUpdated(
                false /* force4500 */, false /* doesPeerSupportNatt */, IkeUdp6Socket.class);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedFail() throws Exception {
        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(
                mock(Network.class), mock(LinkProperties.class), mock(NetworkCapabilities.class));

        // Expected to fail due to DNS resolution failure
        if (SdkLevel.isAtLeastT()) {
            verify(mMockConnectionCtrlCb).onError(any(IkeIOException.class));
        } else {
            verify(mMockConnectionCtrlCb).onError(any(IkeInternalException.class));
        }
    }

    @Test
    public void testOnNetworkSetByExternalCallerWithNullLp() throws Exception {
        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        try {
            onNetworkSetByUserWithDefaultParams(mIkeConnectionCtrl, mock(Network.class));
            fail("Expected to fail due to null LinkProperties");
        } catch (IkeException expected) {
            assertTrue(expected instanceof IkeInternalException);
            assertTrue(expected.getCause() instanceof NullPointerException);
        }
    }

    @Test
    public void testOnUnderlyingNetworkDied() throws Exception {
        mIkeConnectionCtrl.onUnderlyingNetworkDied();
        verify(mMockConnectionCtrlCb).onUnderlyingNetworkDied(eq(mMockDefaultNetwork));
    }

    @IgnoreUpTo(VERSION_CODES.S_V2)
    @Test
    public void testCatchUnexpectedExceptionInNetworkUpdate() throws Exception {
        IkeManager.setIkeLog(
                TestUtils.makeSpyLogDoLogErrorForWtf(
                        "testCatchUnexpectedExceptionInNetworkUpdate"));

        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        Network mockNetwork = mock(Network.class);
        Exception testException =
                new IllegalStateException("testCatchUnexpectedExceptionInNetworkUpdate");
        doThrow(testException).when(mockNetwork).getAllByName(anyString());

        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(
                mockNetwork, mock(LinkProperties.class), mock(NetworkCapabilities.class));

        ArgumentCaptor<IkeInternalException> captor =
                ArgumentCaptor.forClass(IkeInternalException.class);
        verify(mMockConnectionCtrlCb).onError(captor.capture());
        assertEquals(testException, captor.getValue().getCause());

        IkeManager.resetIkeLog();
    }

    @IgnoreAfter(VERSION_CODES.S_V2)
    @Test
    public void testThrowInNetworkUpdate() throws Exception {
        enableMobilityAndReturnCb(true /* isDefaultNetwork */);

        Network mockNetwork = mock(Network.class);
        Exception testException = new IllegalStateException("testThrowNetworkUpdate");
        doThrow(testException).when(mockNetwork).getAllByName(anyString());

        try {
            mIkeConnectionCtrl.onUnderlyingNetworkUpdated(
                    mockNetwork, mock(LinkProperties.class), mock(NetworkCapabilities.class));
            fail("Expected to throw IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    private void verifyGetKeepaliveDelaySec(
            boolean autoKeepalivesEnabled,
            int transportType,
            int callerConfiguredDelay,
            int cellDeviceKeepaliveDelay,
            int expectedDelay)
            throws Exception {
        final IkeContext mockIkeContext = mock(IkeContext.class);
        final IkeSessionParams mockIkeParams = mock(IkeSessionParams.class);
        final NetworkCapabilities mockNc = mock(NetworkCapabilities.class);

        doReturn(cellDeviceKeepaliveDelay)
                .when(mockIkeContext)
                .getDeviceConfigPropertyInt(anyString(), anyInt(), anyInt(), anyInt());
        doReturn(autoKeepalivesEnabled)
                .when(mockIkeParams)
                .hasIkeOption(IKE_OPTION_AUTOMATIC_NATT_KEEPALIVES);
        doReturn(callerConfiguredDelay).when(mockIkeParams).getNattKeepAliveDelaySeconds();
        doReturn(true).when(mockNc).hasTransport(transportType);

        final int actualDelay =
                IkeConnectionController.getKeepaliveDelaySec(mockIkeContext, mockIkeParams, mockNc);

        // Verification
        assertEquals(expectedDelay, actualDelay);
        verify(mockIkeParams).getNattKeepAliveDelaySeconds();

        if (autoKeepalivesEnabled) {
            verify(mockNc).hasTransport(TRANSPORT_WIFI);
            if (transportType == TRANSPORT_CELLULAR) {
                verify(mockNc).hasTransport(TRANSPORT_CELLULAR);
            }
        }

        final boolean expectReadDevice =
                autoKeepalivesEnabled && transportType == TRANSPORT_CELLULAR;
        if (expectReadDevice) {
            verify(mockIkeContext)
                    .getDeviceConfigPropertyInt(
                            eq(CONFIG_AUTO_NATT_KEEPALIVES_CELLULAR_TIMEOUT_OVERRIDE_SECONDS),
                            eq(IKE_NATT_KEEPALIVE_DELAY_SEC_MIN),
                            eq(IKE_NATT_KEEPALIVE_DELAY_SEC_MAX),
                            eq(AUTO_KEEPALIVE_DELAY_SEC_CELL));
        } else {
            verify(mockIkeContext, never())
                    .getDeviceConfigPropertyInt(anyString(), anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void testGetKeepaliveDelaySecAutoKeepalivesDisabled() throws Exception {
        verifyGetKeepaliveDelaySec(
                false /* autoKeepalivesEnabled */,
                TRANSPORT_WIFI,
                KEEPALIVE_DELAY_CALLER_CONFIGURED,
                AUTO_KEEPALIVE_DELAY_SEC_CELL,
                KEEPALIVE_DELAY_CALLER_CONFIGURED);
    }

    @Test
    public void testWifiGetAutoKeepaliveDelaySecCallerOverride() throws Exception {
        verifyGetKeepaliveDelaySec(
                true /* autoKeepalivesEnabled */,
                TRANSPORT_WIFI,
                10 /* callerConfiguredDelay */,
                AUTO_KEEPALIVE_DELAY_SEC_CELL,
                10 /* expectedDelay */);
    }

    @Test
    public void testWifiGetAutoKeepaliveDelaySecNoCallerOverride() throws Exception {
        verifyGetKeepaliveDelaySec(
                true /* autoKeepalivesEnabled */,
                TRANSPORT_WIFI,
                20 /* callerConfiguredDelay */,
                AUTO_KEEPALIVE_DELAY_SEC_CELL,
                AUTO_KEEPALIVE_DELAY_SEC_WIFI);
    }

    @Test
    public void testCellGetAutoKeepaliveDelaySecCallerOverride() throws Exception {
        verifyGetKeepaliveDelaySec(
                true /* autoKeepalivesEnabled */,
                TRANSPORT_CELLULAR,
                10 /* callerConfiguredDelay */,
                90 /* cellDeviceKeepaliveDelay */,
                10 /* expectedDelay */);
    }

    @Test
    public void testCellGetAutoKeepaliveDelaySecNoCallerOverride() throws Exception {
        verifyGetKeepaliveDelaySec(
                true /* autoKeepalivesEnabled */,
                TRANSPORT_CELLULAR,
                100 /* callerConfiguredDelay */,
                90 /* cellDeviceKeepaliveDelay */,
                90 /* expectedDelay */);
    }

    @IgnoreUpTo(VERSION_CODES.TIRAMISU)
    @Test
    public void testForceUpdateOnNetworkSetByUser() throws Exception {
        mIkeConnectionCtrl.enableMobility();
        onNetworkSetByUserWithDefaultParams(mIkeConnectionCtrl, mMockDefaultNetwork);

        verify(mMockConnectionCtrlCb).onUnderlyingNetworkUpdated();
        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkDied(any());
    }

    @IgnoreAfter(VERSION_CODES.TIRAMISU)
    @Test
    public void testSkipUpdateOnNetworkSetByUser() throws Exception {
        mIkeConnectionCtrl.enableMobility();
        onNetworkSetByUserWithDefaultParams(mIkeConnectionCtrl, mMockDefaultNetwork);

        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkUpdated();
        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkDied(any());
    }

    @Test
    public void testSkipUpdateOnNetworkCallbackChange() throws Exception {
        mIkeConnectionCtrl.enableMobility();
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(
                mMockDefaultNetwork,
                mMockConnectManager.getLinkProperties(mMockDefaultNetwork),
                mMockConnectManager.getNetworkCapabilities(mMockDefaultNetwork));

        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkUpdated();
        verify(mMockConnectionCtrlCb, never()).onUnderlyingNetworkDied(any());
    }

    @IgnoreAfter(VERSION_CODES.TIRAMISU)
    @Test
    public void testOnUnderpinnedNetworkSetByUser() throws Exception {
        mIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                true /* isNatDetected */, IKE_LOCAL_SPI);
        verifyKeepalive(false /* hasOldKeepalive */, true /* isKeepaliveExpected */);
        assertTrue(mIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);

        final Network underpinnedNetwork = mock(Network.class);
        mIkeConnectionCtrl.onUnderpinnedNetworkSetByUser(underpinnedNetwork);
        verifyKeepalive(true /* hasOldKeepalive */, true /* isKeepaliveExpected */);
        assertEquals(underpinnedNetwork, mIkeConnectionCtrl.getUnderpinnedNetwork());
    }

    private void verifyIsDnsLookupRequired(
            boolean isNetworkChanged,
            boolean hasLocalV4,
            boolean hasLocalV6,
            boolean hasRemoteV4Cached,
            boolean hasRemoteV6Cached,
            boolean dnsExpected)
            throws Exception {
        final Network newNetwork = isNetworkChanged ? mock(Network.class) : mMockDefaultNetwork;

        final Inet4Address localV4 = hasLocalV4 ? LOCAL_ADDRESS : null;
        final Inet6Address localV6 = hasLocalV6 ? LOCAL_ADDRESS_V6 : null;
        setupLocalAddressForNetwork(newNetwork, localV4, localV6);

        mIkeConnectionCtrl.clearRemoteAddress();
        if (hasRemoteV4Cached) {
            mIkeConnectionCtrl.addRemoteAddress(REMOTE_ADDRESS);
        }
        if (hasRemoteV6Cached) {
            mIkeConnectionCtrl.addRemoteAddress(REMOTE_ADDRESS_V6);
        }

        assertEquals(
                dnsExpected,
                mIkeConnectionCtrl.isDnsLookupRequiredWithGlobalRemoteAddress(
                        mMockDefaultNetwork,
                        newNetwork,
                        mMockConnectManager.getLinkProperties(newNetwork)));
    }

    private void verifyIsDnsLookupRequired(
            boolean hasLocalV4,
            boolean hasLocalV6,
            boolean hasRemoteV4Cached,
            boolean hasRemoteV6Cached,
            boolean dnsExpected)
            throws Exception {
        verifyIsDnsLookupRequired(
                false /* isNetworkChanged */,
                hasLocalV4,
                hasLocalV6,
                hasRemoteV4Cached,
                hasRemoteV6Cached,
                dnsExpected);
    }

    @Test
    public void testIsDnsLookupRequired_NetworkChange() throws Exception {
        verifyIsDnsLookupRequired(
                true /* isNetworkChanged */,
                true /* hasLocalV4 */,
                true /* hasLocalV6 */,
                true /* hasRemoteV4Cached */,
                true /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV4V6_RemoteV4() throws Exception {
        verifyIsDnsLookupRequired(
                true /* hasLocalV4 */,
                true /* hasLocalV6 */,
                true /* hasRemoteV4Cached */,
                false /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV4V6_RemoteV6() throws Exception {
        verifyIsDnsLookupRequired(
                true /* hasLocalV4 */,
                true /* hasLocalV6 */,
                false /* hasRemoteV4Cached */,
                true /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV4_RemoteV6() throws Exception {
        verifyIsDnsLookupRequired(
                true /* hasLocalV4 */,
                false /* hasLocalV6 */,
                false /* hasRemoteV4Cached */,
                true /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV6_RemoteV4() throws Exception {
        verifyIsDnsLookupRequired(
                false /* hasLocalV4 */,
                true /* hasLocalV6 */,
                true /* hasRemoteV4Cached */,
                false /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalNone_RemoteV4() throws Exception {
        verifyIsDnsLookupRequired(
                false /* hasLocalV4 */,
                false /* hasLocalV6 */,
                true /* hasRemoteV4Cached */,
                false /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV6_RemoteNone() throws Exception {
        verifyIsDnsLookupRequired(
                false /* hasLocalV4 */,
                true /* hasLocalV6 */,
                false /* hasRemoteV4Cached */,
                false /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalNone_RemoteNone() throws Exception {
        verifyIsDnsLookupRequired(
                false /* hasLocalV4 */,
                false /* hasLocalV6 */,
                false /* hasRemoteV4Cached */,
                false /* hasRemoteV6Cached */,
                true /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV4_RemoteV4() throws Exception {
        verifyIsDnsLookupRequired(
                true /* hasLocalV4 */,
                false /* hasLocalV6 */,
                true /* hasRemoteV4Cached */,
                false /* hasRemoteV6Cached */,
                false /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV6_RemoteV6() throws Exception {
        verifyIsDnsLookupRequired(
                false /* hasLocalV4 */,
                true /* hasLocalV6 */,
                false /* hasRemoteV4Cached */,
                true /* hasRemoteV6Cached */,
                false /* dnsExpected */);
    }

    @Test
    public void testIsDnsLookupRequired_LocalV4V6_RemoteV4V6() throws Exception {
        verifyIsDnsLookupRequired(
                true /* hasLocalV4 */,
                true /* hasLocalV6 */,
                true /* hasRemoteV4Cached */,
                true /* hasRemoteV6Cached */,
                false /* dnsExpected */);
    }

    private void verifyDnsLookupWithCachedIpv6Address(boolean isNat64, boolean dnsExpected)
            throws Exception {
        reset(mMockDefaultNetwork);
        setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS_V6);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS_V6);

        mIkeConnectionCtrl.clearRemoteAddress();
        mIkeConnectionCtrl.addRemoteAddressV6(REMOTE_ADDRESS_V6, isNat64);

        IkeNetworkCallbackBase callback = enableMobilityAndReturnCb(true /* isDefaultNetwork */);
        mIkeConnectionCtrl.onUnderlyingNetworkUpdated(
                mMockDefaultNetwork,
                mMockConnectManager.getLinkProperties(mMockDefaultNetwork),
                mMockNetworkCapabilities);

        if (dnsExpected) {
            verify(mMockDefaultNetwork).getAllByName(anyString());
        } else {
            verify(mMockDefaultNetwork, never()).getAllByName(anyString());
        }
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedWithGlobalV6Address() throws Exception {
        verifyDnsLookupWithCachedIpv6Address(false /* isNat64 */, false /* dnsExpected */);
    }

    @Test
    public void testOnUnderlyingNetworkUpdatedWithNat64V6Address() throws Exception {
        verifyDnsLookupWithCachedIpv6Address(true /* isNat64 */, true /* dnsExpected */);
    }
}
