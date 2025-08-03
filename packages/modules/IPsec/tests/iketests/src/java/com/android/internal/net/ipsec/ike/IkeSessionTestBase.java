/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike;

import static com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.LOCAL_REQUEST_WAKE_LOCK_TAG;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.BUSY_WAKE_LOCK_TAG;
import static com.android.internal.net.ipsec.test.ike.IkeSocket.SERVER_PORT_NON_UDP_ENCAPSULATED;
import static com.android.internal.net.ipsec.test.ike.IkeSocket.SERVER_PORT_UDP_ENCAPSULATED;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.IpSecManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.SocketKeepalive;
import android.net.ipsec.test.ike.IkeManager;
import android.net.ipsec.test.ike.IkeSession;
import android.os.Handler;
import android.os.PowerManager;

import com.android.internal.net.ipsec.test.ike.net.IkeLocalAddressGenerator;
import com.android.internal.net.ipsec.test.ike.testutils.MockIpSecTestUtils;
import com.android.internal.net.ipsec.test.ike.utils.IState;
import com.android.internal.net.ipsec.test.ike.utils.IkeAlarmReceiver;
import com.android.internal.net.ipsec.test.ike.utils.IkeMetrics;
import com.android.internal.net.ipsec.test.ike.utils.RandomnessFactory;

import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.Executor;

public abstract class IkeSessionTestBase {
    protected static final Inet4Address LOCAL_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.200");
    protected static final Inet4Address UPDATED_LOCAL_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.201");
    protected static final Inet4Address REMOTE_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("127.0.0.1");
    protected static final Inet6Address LOCAL_ADDRESS_V6 =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::200");
    protected static final Inet6Address UPDATED_LOCAL_ADDRESS_V6 =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::201");
    protected static final Inet6Address REMOTE_ADDRESS_V6 =
            (Inet6Address) InetAddresses.parseNumericAddress("::1");
    protected static final String REMOTE_HOSTNAME = "ike.test.android.com";

    protected PowerManager.WakeLock mMockBusyWakelock;
    protected PowerManager.WakeLock mMockLocalRequestWakelock;

    protected MockIpSecTestUtils mMockIpSecTestUtils;
    protected Context mSpyContext;
    protected IpSecManager mIpSecManager;
    protected PowerManager mPowerManager;
    protected IkeMetrics mIkeMetrics;

    protected ConnectivityManager mMockConnectManager;
    protected Network mMockDefaultNetwork;
    protected SocketKeepalive mMockSocketKeepalive;
    protected NetworkCapabilities mMockNetworkCapabilities;
    protected IkeLocalAddressGenerator mMockIkeLocalAddressGenerator;

    @Before
    public void setUp() throws Exception {
        mMockIpSecTestUtils = MockIpSecTestUtils.setUpMockIpSec();
        mIpSecManager = mMockIpSecTestUtils.getIpSecManager();

        mSpyContext = spy(mMockIpSecTestUtils.getContext());
        doReturn(null)
                .when(mSpyContext)
                .registerReceiver(
                        any(IkeAlarmReceiver.class),
                        any(IntentFilter.class),
                        any(),
                        any(Handler.class),
                        anyInt());
        doNothing().when(mSpyContext).unregisterReceiver(any(IkeAlarmReceiver.class));

        mPowerManager = mock(PowerManager.class);
        mMockBusyWakelock = mock(PowerManager.WakeLock.class);
        mMockLocalRequestWakelock = mock(PowerManager.WakeLock.class);
        doReturn(mPowerManager).when(mSpyContext).getSystemService(eq(PowerManager.class));
        doReturn(mMockBusyWakelock)
                .when(mPowerManager)
                .newWakeLock(anyInt(), argThat(tag -> tag.contains(BUSY_WAKE_LOCK_TAG)));
        // Only in test that all local requests will get the same WakeLock instance but in function
        // code each local request will have a separate WakeLock.
        doReturn(mMockLocalRequestWakelock)
                .when(mPowerManager)
                .newWakeLock(anyInt(), argThat(tag -> tag.contains(LOCAL_REQUEST_WAKE_LOCK_TAG)));

        mMockDefaultNetwork = mock(Network.class);
        resetDefaultNetwork();

        mMockSocketKeepalive = mock(SocketKeepalive.class);

        mMockNetworkCapabilities = mock(NetworkCapabilities.class);
        doReturn(false)
                .when(mMockNetworkCapabilities)
                .hasTransport(RandomnessFactory.TRANSPORT_TEST);

        mMockConnectManager = mock(ConnectivityManager.class);
        doReturn(mMockConnectManager)
                .when(mSpyContext)
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        mMockIkeLocalAddressGenerator = mock(IkeLocalAddressGenerator.class);
        resetMockConnectManager();

        // Setup metrics parameters
        mIkeMetrics = mock(IkeMetrics.class);
        IkeManager.setIkeMetrics(mIkeMetrics);
        doReturn(IkeSession.CONTEXT_ATTRIBUTION_TAG_VCN).when(mSpyContext).getAttributionTag();
    }

    protected void resetMockConnectManager() throws Exception {
        reset(mMockConnectManager);
        doReturn(mMockDefaultNetwork).when(mMockConnectManager).getActiveNetwork();
        doReturn(mMockSocketKeepalive)
                .when(mMockConnectManager)
                .createSocketKeepalive(
                        any(Network.class),
                        any(),
                        any(Inet4Address.class),
                        any(Inet4Address.class),
                        any(Executor.class),
                        any(SocketKeepalive.Callback.class));
        doReturn(mMockNetworkCapabilities)
                .when(mMockConnectManager)
                .getNetworkCapabilities(any(Network.class));
        setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(mMockDefaultNetwork, REMOTE_ADDRESS);
    }

    protected void resetDefaultNetwork() throws Exception {
        reset(mMockDefaultNetwork);
        doReturn(new InetAddress[] {REMOTE_ADDRESS})
                .when(mMockDefaultNetwork)
                .getAllByName(REMOTE_HOSTNAME);
        doReturn(new InetAddress[] {REMOTE_ADDRESS})
                .when(mMockDefaultNetwork)
                .getAllByName(REMOTE_ADDRESS.getHostAddress());
    }

    protected void resetMockIkeSocket(IkeSocket mockSocket) {
        reset(mockSocket);
        if (mockSocket instanceof IkeUdp4Socket || mockSocket instanceof IkeUdp6Socket) {
            when(mockSocket.getIkeServerPort()).thenReturn(SERVER_PORT_NON_UDP_ENCAPSULATED);
        } else {
            when(mockSocket.getIkeServerPort()).thenReturn(SERVER_PORT_UDP_ENCAPSULATED);
        }
    }

    protected <T extends IkeSocket> T newMockIkeSocket(Class<T> socketClass) {
        T mockSocket = mock(socketClass);
        resetMockIkeSocket(mockSocket);

        return mockSocket;
    }

    private LinkAddress setupLocalAddressAndGetLinkAddress(Network network, InetAddress address)
            throws Exception {
        boolean isIpv4 = address instanceof Inet4Address;
        when(mMockIkeLocalAddressGenerator.generateLocalAddress(
                        eq(network), eq(isIpv4), any(), anyInt()))
                .thenReturn(address);

        LinkAddress mockLinkAddress = mock(LinkAddress.class);
        when(mockLinkAddress.getAddress()).thenReturn(address);
        if (!isIpv4) {
            when(mockLinkAddress.isGlobalPreferred()).thenReturn(true);
        }

        return mockLinkAddress;
    }

    protected void setupLocalAddressForNetwork(
            Network network, Inet4Address addressV4, Inet6Address addressV6) throws Exception {
        LinkProperties linkProperties = new LinkProperties();
        if (addressV4 != null) {
            linkProperties.addLinkAddress(setupLocalAddressAndGetLinkAddress(network, addressV4));
        }
        if (addressV6 != null) {
            linkProperties.addLinkAddress(setupLocalAddressAndGetLinkAddress(network, addressV6));
        }
        when(mMockConnectManager.getLinkProperties(eq(network))).thenReturn(linkProperties);
    }

    protected void setupLocalAddressForNetwork(Network network, InetAddress address)
            throws Exception {
        if (address instanceof Inet4Address) {
            setupLocalAddressForNetwork(network, (Inet4Address) address, null);
        } else {
            setupLocalAddressForNetwork(network, null, (Inet6Address) address);
        }
    }

    protected void setupRemoteAddressForNetwork(Network network, InetAddress... addresses)
            throws Exception {
        doAnswer(
                new Answer() {
                        public Object answer(InvocationOnMock invocation) throws IOException {
                            return addresses;
                        }
                })
                .when(network)
                .getAllByName(REMOTE_HOSTNAME);
    }

    protected void verifyMetricsLogged(int sessionType, int stateCode, int exceptionCode) {
        verify(mIkeMetrics)
                .logSessionTerminated(
                        IkeMetrics.IKE_CALLER_UNKNOWN, sessionType, stateCode, exceptionCode);
    }

    protected int getStateCode(IState state) {
        return state instanceof AbstractSessionStateMachine.ExceptionHandlerBase
                ? ((AbstractSessionStateMachine.ExceptionHandlerBase) state).getMetricsStateCode()
                : IkeMetrics.IKE_STATE_UNKNOWN;
    }
}
