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

package android.net.ip;

import static android.net.INetd.IF_STATE_DOWN;
import static android.net.INetd.IF_STATE_UP;
import static android.net.RouteInfo.RTN_UNICAST;
import static android.net.TetheringManager.CONNECTIVITY_SCOPE_GLOBAL;
import static android.net.TetheringManager.CONNECTIVITY_SCOPE_LOCAL;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_NCM;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.TetheringManager.TETHER_ERROR_ENABLE_FORWARDING_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_TETHER_IFACE_ERROR;
import static android.net.dhcp.IDhcpServer.STATUS_SUCCESS;
import static android.net.ip.IpServer.STATE_AVAILABLE;
import static android.net.ip.IpServer.STATE_LOCAL_ONLY;
import static android.net.ip.IpServer.STATE_TETHERED;
import static android.net.ip.IpServer.STATE_UNAVAILABLE;
import static android.net.ip.IpServer.getTetherableIpv6Prefixes;

import static com.android.modules.utils.build.SdkLevel.isAtLeastT;
import static com.android.net.module.util.Inet4AddressUtils.intToInet4AddressHTH;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.usage.NetworkStatsManager;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.InterfaceConfigurationParcel;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.RouteInfo;
import android.net.RoutingCoordinatorManager;
import android.net.dhcp.DhcpServerCallbacks;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpEventCallbacks;
import android.net.dhcp.IDhcpServer;
import android.net.dhcp.IDhcpServerCallbacks;
import android.net.ip.RouterAdvertisementDaemon.RaParams;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.SdkUtil.LateSdk;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.IpNeighborMonitor;
import com.android.networkstack.tethering.BpfCoordinator;
import com.android.networkstack.tethering.PrivateAddressCoordinator;
import com.android.networkstack.tethering.TetheringConfiguration;
import com.android.networkstack.tethering.metrics.TetheringMetrics;
import com.android.networkstack.tethering.util.InterfaceSet;
import com.android.networkstack.tethering.util.PrefixUtils;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class IpServerTest {
    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private static final String IFACE_NAME = "testnet1";
    private static final String UPSTREAM_IFACE = "upstream0";
    private static final String UPSTREAM_IFACE2 = "upstream1";
    private static final String IPSEC_IFACE = "ipsec0";
    private static final int NO_UPSTREAM = 0;
    private static final int UPSTREAM_IFINDEX = 101;
    private static final int UPSTREAM_IFINDEX2 = 102;
    private static final int IPSEC_IFINDEX = 103;
    private static final String BLUETOOTH_IFACE_ADDR = "192.168.44.1";
    private static final int BLUETOOTH_DHCP_PREFIX_LENGTH = 24;
    private static final int DHCP_LEASE_TIME_SECS = 3600;
    private static final boolean DEFAULT_USING_BPF_OFFLOAD = true;
    private static final int DEFAULT_SUBNET_PREFIX_LENGTH = 0;
    private static final int P2P_SUBNET_PREFIX_LENGTH = 25;

    private static final InterfaceParams TEST_IFACE_PARAMS = new InterfaceParams(
            IFACE_NAME, 42 /* index */, MacAddress.ALL_ZEROS_ADDRESS, 1500 /* defaultMtu */);
    private static final InterfaceParams UPSTREAM_IFACE_PARAMS = new InterfaceParams(
            UPSTREAM_IFACE, UPSTREAM_IFINDEX, MacAddress.ALL_ZEROS_ADDRESS, 1500 /* defaultMtu */);
    private static final InterfaceParams UPSTREAM_IFACE_PARAMS2 = new InterfaceParams(
            UPSTREAM_IFACE2, UPSTREAM_IFINDEX2, MacAddress.ALL_ZEROS_ADDRESS,
            1500 /* defaultMtu */);
    private static final InterfaceParams IPSEC_IFACE_PARAMS = new InterfaceParams(
            IPSEC_IFACE, IPSEC_IFINDEX, MacAddress.ALL_ZEROS_ADDRESS, 1500 /* defaultMtu */);

    private static final int MAKE_DHCPSERVER_TIMEOUT_MS = 1000;

    private final LinkAddress mTestAddress = new LinkAddress("192.168.42.5/24");
    private final IpPrefix mBluetoothPrefix = new IpPrefix("192.168.44.0/24");

    private static final Set<LinkAddress> NO_ADDRESSES = Set.of();
    private static final Set<IpPrefix> NO_PREFIXES = Set.of();
    private static final Set<LinkAddress> UPSTREAM_ADDRESSES =
            Set.of(new LinkAddress("2001:db8:0:1234::168/64"));
    private static final Set<IpPrefix> UPSTREAM_PREFIXES =
            Set.of(new IpPrefix("2001:db8:0:1234::/64"));
    private static final Set<LinkAddress> UPSTREAM_ADDRESSES2 = Set.of(
            new LinkAddress("2001:db8:0:1234::168/64"),
            new LinkAddress("2001:db8:0:abcd::168/64"));
    private static final Set<IpPrefix> UPSTREAM_PREFIXES2 = Set.of(
            new IpPrefix("2001:db8:0:1234::/64"), new IpPrefix("2001:db8:0:abcd::/64"));

    @Mock private INetd mNetd;
    @Mock private IpServer.Callback mCallback;
    @Mock private SharedLog mSharedLog;
    @Mock private IDhcpServer mDhcpServer;
    @Mock private DadProxy mDadProxy;
    @Mock private RouterAdvertisementDaemon mRaDaemon;
    @Mock private IpNeighborMonitor mIpNeighborMonitor;
    @Mock private IpServer.Dependencies mDependencies;
    @Mock private PrivateAddressCoordinator mAddressCoordinator;
    private final LateSdk<RoutingCoordinatorManager> mRoutingCoordinatorManager =
            new LateSdk<>(SdkLevel.isAtLeastS() ? mock(RoutingCoordinatorManager.class) : null);
    @Mock private NetworkStatsManager mStatsManager;
    @Mock private TetheringConfiguration mTetherConfig;
    @Mock private TetheringMetrics mTetheringMetrics;
    @Mock private BpfCoordinator mBpfCoordinator;

    @Captor private ArgumentCaptor<DhcpServingParamsParcel> mDhcpParamsCaptor;

    private TestLooper mLooper;
    private Handler mHandler;
    private final ArgumentCaptor<LinkProperties> mLinkPropertiesCaptor =
            ArgumentCaptor.forClass(LinkProperties.class);
    private IpServer mIpServer;
    private InterfaceConfigurationParcel mInterfaceConfiguration;

    private void initStateMachine(int interfaceType) throws Exception {
        initStateMachine(interfaceType, false /* usingLegacyDhcp */, DEFAULT_USING_BPF_OFFLOAD);
    }

    private void initStateMachine(int interfaceType, boolean usingLegacyDhcp,
            boolean usingBpfOffload) throws Exception {
        when(mDependencies.getDadProxy(any(), any())).thenReturn(mDadProxy);
        when(mDependencies.getRouterAdvertisementDaemon(any())).thenReturn(mRaDaemon);
        when(mDependencies.getInterfaceParams(IFACE_NAME)).thenReturn(TEST_IFACE_PARAMS);
        when(mDependencies.getInterfaceParams(UPSTREAM_IFACE)).thenReturn(UPSTREAM_IFACE_PARAMS);
        when(mDependencies.getInterfaceParams(UPSTREAM_IFACE2)).thenReturn(UPSTREAM_IFACE_PARAMS2);
        when(mDependencies.getInterfaceParams(IPSEC_IFACE)).thenReturn(IPSEC_IFACE_PARAMS);

        mInterfaceConfiguration = new InterfaceConfigurationParcel();
        mInterfaceConfiguration.flags = new String[0];
        if (interfaceType == TETHERING_BLUETOOTH) {
            mInterfaceConfiguration.ipv4Addr = BLUETOOTH_IFACE_ADDR;
            mInterfaceConfiguration.prefixLength = BLUETOOTH_DHCP_PREFIX_LENGTH;
        }

        doReturn(mIpNeighborMonitor).when(mDependencies).getIpNeighborMonitor(any(), any(), any());

        when(mTetherConfig.isBpfOffloadEnabled()).thenReturn(usingBpfOffload);
        when(mTetherConfig.useLegacyDhcpServer()).thenReturn(usingLegacyDhcp);
        when(mTetherConfig.getP2pLeasesSubnetPrefixLength()).thenReturn(P2P_SUBNET_PREFIX_LENGTH);
        when(mBpfCoordinator.isUsingBpfOffload()).thenReturn(usingBpfOffload);
        mIpServer = createIpServer(interfaceType);
        verify(mIpNeighborMonitor).start();
        mIpServer.start();

        // Starting the state machine always puts us in a consistent state and notifies
        // the rest of the world that we've changed from an unknown to available state.
        mLooper.dispatchAll();
        reset(mNetd, mCallback, mIpNeighborMonitor);

        when(mRaDaemon.start()).thenReturn(true);
    }

    private void initTetheredStateMachine(int interfaceType, String upstreamIface)
            throws Exception {
        initTetheredStateMachine(interfaceType, upstreamIface, NO_ADDRESSES, false,
                DEFAULT_USING_BPF_OFFLOAD);
    }

    private void initTetheredStateMachine(int interfaceType, String upstreamIface,
            Set<LinkAddress> upstreamAddresses, boolean usingLegacyDhcp, boolean usingBpfOffload)
            throws Exception {
        initStateMachine(interfaceType, usingLegacyDhcp, usingBpfOffload);
        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_TETHERED);
        if (upstreamIface != null) {
            InterfaceParams interfaceParams = mDependencies.getInterfaceParams(upstreamIface);
            assertNotNull("missing upstream interface: " + upstreamIface, interfaceParams);
            LinkProperties lp = new LinkProperties();
            lp.setInterfaceName(upstreamIface);
            lp.setLinkAddresses(upstreamAddresses);
            dispatchTetherConnectionChanged(upstreamIface, lp, 0);
            Set<IpPrefix> upstreamPrefixes = getTetherableIpv6Prefixes(lp.getLinkAddresses());
            verify(mBpfCoordinator).updateAllIpv6Rules(
                    mIpServer, TEST_IFACE_PARAMS, interfaceParams.index, upstreamPrefixes);
        }
        reset(mNetd, mBpfCoordinator, mCallback, mAddressCoordinator);
        when(mAddressCoordinator.requestDownstreamAddress(any(), anyInt(),
                anyBoolean())).thenReturn(mTestAddress);
    }

    @SuppressWarnings("DoNotCall") // Ignore warning for synchronous to call to Thread.run()
    private void setUpDhcpServer() throws Exception {
        doAnswer(inv -> {
            final IDhcpServerCallbacks cb = inv.getArgument(2);
            new Thread(() -> {
                try {
                    cb.onDhcpServerCreated(STATUS_SUCCESS, mDhcpServer);
                } catch (RemoteException e) {
                    fail(e.getMessage());
                }
            }).run();
            return null;
        }).when(mDependencies).makeDhcpServer(any(), mDhcpParamsCaptor.capture(), any());
    }

    @Before public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mSharedLog.forSubComponent(anyString())).thenReturn(mSharedLog);
        when(mAddressCoordinator.requestDownstreamAddress(any(), anyInt(),
                anyBoolean())).thenReturn(mTestAddress);
        when(mTetherConfig.isBpfOffloadEnabled()).thenReturn(DEFAULT_USING_BPF_OFFLOAD);
        when(mTetherConfig.useLegacyDhcpServer()).thenReturn(false /* default value */);

        // Simulate the behavior of RoutingCoordinator
        if (null != mRoutingCoordinatorManager.value) {
            doAnswer(it -> {
                final String fromIface = (String) it.getArguments()[0];
                final String toIface = (String) it.getArguments()[1];
                mNetd.tetherAddForward(fromIface, toIface);
                mNetd.ipfwdAddInterfaceForward(fromIface, toIface);
                return null;
            }).when(mRoutingCoordinatorManager.value).addInterfaceForward(any(), any());
            doAnswer(it -> {
                final String fromIface = (String) it.getArguments()[0];
                final String toIface = (String) it.getArguments()[1];
                mNetd.ipfwdRemoveInterfaceForward(fromIface, toIface);
                mNetd.tetherRemoveForward(fromIface, toIface);
                return null;
            }).when(mRoutingCoordinatorManager.value).removeInterfaceForward(any(), any());
        }

        setUpDhcpServer();
    }

    // In order to interact with syncSM from the test, IpServer must be created in test thread.
    private IpServer createIpServer(final int interfaceType) {
        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        return new IpServer(IFACE_NAME, mHandler, interfaceType, mSharedLog, mNetd, mBpfCoordinator,
                mRoutingCoordinatorManager, mCallback, mTetherConfig, mAddressCoordinator,
                mTetheringMetrics, mDependencies);

    }

    @Test
    public void startsOutAvailable() throws Exception {
        when(mDependencies.getIpNeighborMonitor(any(), any(), any()))
                .thenReturn(mIpNeighborMonitor);
        mIpServer = createIpServer(TETHERING_BLUETOOTH);
        mIpServer.start();
        mLooper.dispatchAll();
        verify(mCallback).updateInterfaceState(
                mIpServer, STATE_AVAILABLE, TETHER_ERROR_NO_ERROR);
        verify(mCallback).updateLinkProperties(eq(mIpServer), any(LinkProperties.class));
        verifyNoMoreInteractions(mCallback, mNetd);
    }

    @Test
    public void shouldDoNothingUntilRequested() throws Exception {
        initStateMachine(TETHERING_BLUETOOTH);
        final int [] noOp_commands = {
            IpServer.CMD_TETHER_UNREQUESTED,
            IpServer.CMD_IP_FORWARDING_ENABLE_ERROR,
            IpServer.CMD_IP_FORWARDING_DISABLE_ERROR,
            IpServer.CMD_START_TETHERING_ERROR,
            IpServer.CMD_STOP_TETHERING_ERROR,
            IpServer.CMD_SET_DNS_FORWARDERS_ERROR,
            IpServer.CMD_TETHER_CONNECTION_CHANGED
        };
        for (int command : noOp_commands) {
            // None of these commands should trigger us to request action from
            // the rest of the system.
            dispatchCommand(command);
            verifyNoMoreInteractions(mNetd, mCallback);
        }
    }

    @Test
    public void handlesImmediateInterfaceDown() throws Exception {
        initStateMachine(TETHERING_BLUETOOTH);

        dispatchCommand(IpServer.CMD_INTERFACE_DOWN);
        verify(mCallback).updateInterfaceState(
                mIpServer, STATE_UNAVAILABLE, TETHER_ERROR_NO_ERROR);
        verify(mCallback).updateLinkProperties(eq(mIpServer), any(LinkProperties.class));
        verifyNoMoreInteractions(mNetd, mCallback);
    }

    @Test
    public void canBeTetheredAsBluetooth() throws Exception {
        initStateMachine(TETHERING_BLUETOOTH);

        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_TETHERED);
        InOrder inOrder = inOrder(mCallback, mNetd, mAddressCoordinator);
        if (isAtLeastT()) {
            inOrder.verify(mAddressCoordinator).requestDownstreamAddress(any(),
                    eq(CONNECTIVITY_SCOPE_GLOBAL), eq(true));
            inOrder.verify(mNetd).interfaceSetCfg(argThat(cfg ->
                    IFACE_NAME.equals(cfg.ifName) && assertContainsFlag(cfg.flags, IF_STATE_UP)));
        }
        inOrder.verify(mNetd).tetherInterfaceAdd(IFACE_NAME);
        inOrder.verify(mNetd).networkAddInterface(INetd.LOCAL_NET_ID, IFACE_NAME);
        // One for ipv4 route, one for ipv6 link local route.
        inOrder.verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(IFACE_NAME),
                any(), any());
        inOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_TETHERED, TETHER_ERROR_NO_ERROR);
        inOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), any(LinkProperties.class));
        verifyNoMoreInteractions(mNetd, mCallback);
    }

    @Test
    public void canUnrequestTethering() throws Exception {
        initTetheredStateMachine(TETHERING_BLUETOOTH, null);

        dispatchCommand(IpServer.CMD_TETHER_UNREQUESTED);
        InOrder inOrder = inOrder(mCallback, mNetd, mAddressCoordinator);
        inOrder.verify(mNetd).tetherApplyDnsInterfaces();
        inOrder.verify(mNetd).tetherInterfaceRemove(IFACE_NAME);
        inOrder.verify(mNetd).networkRemoveInterface(INetd.LOCAL_NET_ID, IFACE_NAME);
        // One is ipv4 address clear (set to 0.0.0.0), another is set interface down which only
        // happen after T. Before T, the interface configuration control in bluetooth side.
        if (isAtLeastT()) {
            inOrder.verify(mNetd).interfaceSetCfg(
                    argThat(cfg -> assertContainsFlag(cfg.flags, IF_STATE_DOWN)));
        }
        inOrder.verify(mNetd).interfaceSetCfg(argThat(cfg -> cfg.flags.length == 0));
        inOrder.verify(mAddressCoordinator).releaseDownstream(any());
        inOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_AVAILABLE, TETHER_ERROR_NO_ERROR);
        inOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), any(LinkProperties.class));
        verify(mTetheringMetrics).updateErrorCode(eq(TETHERING_BLUETOOTH),
                eq(TETHER_ERROR_NO_ERROR));
        verify(mTetheringMetrics).sendReport(eq(TETHERING_BLUETOOTH));
        verifyNoMoreInteractions(mNetd, mCallback, mAddressCoordinator);
    }

    @Test
    public void canBeTetheredAsUsb() throws Exception {
        initStateMachine(TETHERING_USB);

        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_TETHERED);
        InOrder inOrder = inOrder(mCallback, mNetd, mAddressCoordinator);
        inOrder.verify(mAddressCoordinator).requestDownstreamAddress(any(),
                eq(CONNECTIVITY_SCOPE_GLOBAL), eq(true));
        inOrder.verify(mNetd).interfaceSetCfg(argThat(cfg ->
                IFACE_NAME.equals(cfg.ifName) && assertContainsFlag(cfg.flags, IF_STATE_UP)));
        inOrder.verify(mNetd).tetherInterfaceAdd(IFACE_NAME);
        inOrder.verify(mNetd).networkAddInterface(INetd.LOCAL_NET_ID, IFACE_NAME);
        inOrder.verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(IFACE_NAME),
                any(), any());
        inOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_TETHERED, TETHER_ERROR_NO_ERROR);
        inOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), mLinkPropertiesCaptor.capture());
        assertIPv4AddressAndDirectlyConnectedRoute(mLinkPropertiesCaptor.getValue());
        verifyNoMoreInteractions(mNetd, mCallback, mAddressCoordinator);
    }

    @Test
    public void canBeTetheredAsWifiP2p() throws Exception {
        initStateMachine(TETHERING_WIFI_P2P);

        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_LOCAL_ONLY);
        InOrder inOrder = inOrder(mCallback, mNetd, mAddressCoordinator);
        inOrder.verify(mAddressCoordinator).requestDownstreamAddress(any(),
                eq(CONNECTIVITY_SCOPE_LOCAL), eq(true));
        inOrder.verify(mNetd).interfaceSetCfg(argThat(cfg ->
                  IFACE_NAME.equals(cfg.ifName) && assertNotContainsFlag(cfg.flags, IF_STATE_UP)));
        inOrder.verify(mNetd).tetherInterfaceAdd(IFACE_NAME);
        inOrder.verify(mNetd).networkAddInterface(INetd.LOCAL_NET_ID, IFACE_NAME);
        inOrder.verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(IFACE_NAME),
                any(), any());
        inOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_LOCAL_ONLY, TETHER_ERROR_NO_ERROR);
        inOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), mLinkPropertiesCaptor.capture());
        assertIPv4AddressAndDirectlyConnectedRoute(mLinkPropertiesCaptor.getValue());
        verifyNoMoreInteractions(mNetd, mCallback, mAddressCoordinator);
    }

    @Test
    public void handlesFirstUpstreamChange() throws Exception {
        initTetheredStateMachine(TETHERING_BLUETOOTH, null);

        // Telling the state machine about its upstream interface triggers
        // a little more configuration.
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);
        InOrder inOrder = inOrder(mNetd, mBpfCoordinator);

        // Add the forwarding pair <IFACE_NAME, UPSTREAM_IFACE>.
        inOrder.verify(mBpfCoordinator).maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX,
                UPSTREAM_IFACE);
        inOrder.verify(mBpfCoordinator).maybeAttachProgram(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).tetherAddForward(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).ipfwdAddInterfaceForward(IFACE_NAME, UPSTREAM_IFACE);

        verifyNoMoreInteractions(mNetd, mCallback, mBpfCoordinator);
    }

    @Test
    public void handlesChangingUpstream() throws Exception {
        initTetheredStateMachine(TETHERING_BLUETOOTH, UPSTREAM_IFACE);

        dispatchTetherConnectionChanged(UPSTREAM_IFACE2);
        InOrder inOrder = inOrder(mNetd, mBpfCoordinator);

        // Remove the forwarding pair <IFACE_NAME, UPSTREAM_IFACE>.
        inOrder.verify(mBpfCoordinator).maybeDetachProgram(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).tetherRemoveForward(IFACE_NAME, UPSTREAM_IFACE);

        // Add the forwarding pair <IFACE_NAME, UPSTREAM_IFACE2>.
        inOrder.verify(mBpfCoordinator).maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX2,
                UPSTREAM_IFACE2);
        inOrder.verify(mBpfCoordinator).maybeAttachProgram(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).tetherAddForward(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).ipfwdAddInterfaceForward(IFACE_NAME, UPSTREAM_IFACE2);

        verifyNoMoreInteractions(mNetd, mCallback, mBpfCoordinator);
    }

    @Test
    public void handlesChangingUpstreamNatFailure() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);

        doThrow(RemoteException.class).when(mNetd).tetherAddForward(IFACE_NAME, UPSTREAM_IFACE2);

        dispatchTetherConnectionChanged(UPSTREAM_IFACE2);
        InOrder inOrder = inOrder(mNetd, mBpfCoordinator);

        // Remove the forwarding pair <IFACE_NAME, UPSTREAM_IFACE>.
        inOrder.verify(mBpfCoordinator).maybeDetachProgram(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).tetherRemoveForward(IFACE_NAME, UPSTREAM_IFACE);

        // Add the forwarding pair <IFACE_NAME, UPSTREAM_IFACE2> and expect that failed on
        // tetherAddForward.
        inOrder.verify(mBpfCoordinator).maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX2,
                UPSTREAM_IFACE2);
        inOrder.verify(mBpfCoordinator).maybeAttachProgram(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).tetherAddForward(IFACE_NAME, UPSTREAM_IFACE2);

        // Remove the forwarding pair <IFACE_NAME, UPSTREAM_IFACE2> to fallback.
        inOrder.verify(mBpfCoordinator).maybeDetachProgram(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).tetherRemoveForward(IFACE_NAME, UPSTREAM_IFACE2);
    }

    @Test
    public void handlesChangingUpstreamInterfaceForwardingFailure() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);

        doThrow(RemoteException.class).when(mNetd).ipfwdAddInterfaceForward(
                IFACE_NAME, UPSTREAM_IFACE2);

        dispatchTetherConnectionChanged(UPSTREAM_IFACE2);
        InOrder inOrder = inOrder(mNetd, mBpfCoordinator);

        // Remove the forwarding pair <IFACE_NAME, UPSTREAM_IFACE>.
        inOrder.verify(mBpfCoordinator).maybeDetachProgram(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).tetherRemoveForward(IFACE_NAME, UPSTREAM_IFACE);

        // Add the forwarding pair <IFACE_NAME, UPSTREAM_IFACE2> and expect that failed on
        // ipfwdAddInterfaceForward.
        inOrder.verify(mBpfCoordinator).maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX2,
                UPSTREAM_IFACE2);
        inOrder.verify(mBpfCoordinator).maybeAttachProgram(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).tetherAddForward(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).ipfwdAddInterfaceForward(IFACE_NAME, UPSTREAM_IFACE2);

        // Remove the forwarding pair <IFACE_NAME, UPSTREAM_IFACE2> to fallback.
        inOrder.verify(mBpfCoordinator).maybeDetachProgram(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward(IFACE_NAME, UPSTREAM_IFACE2);
        inOrder.verify(mNetd).tetherRemoveForward(IFACE_NAME, UPSTREAM_IFACE2);
    }

    @Test
    public void canUnrequestTetheringWithUpstream() throws Exception {
        initTetheredStateMachine(TETHERING_BLUETOOTH, UPSTREAM_IFACE);

        dispatchCommand(IpServer.CMD_TETHER_UNREQUESTED);
        InOrder inOrder = inOrder(mNetd, mCallback, mAddressCoordinator, mBpfCoordinator);
        inOrder.verify(mBpfCoordinator).maybeDetachProgram(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).ipfwdRemoveInterfaceForward(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mNetd).tetherRemoveForward(IFACE_NAME, UPSTREAM_IFACE);
        inOrder.verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        // When tethering stops, upstream interface is set to zero and thus clearing all upstream
        // rules. Downstream rules are needed to be cleared explicitly by calling
        // BpfCoordinator#clearAllIpv6Rules in TetheredState#exit.
        inOrder.verify(mBpfCoordinator).clearAllIpv6Rules(mIpServer);
        inOrder.verify(mNetd).tetherApplyDnsInterfaces();
        inOrder.verify(mNetd).tetherInterfaceRemove(IFACE_NAME);
        inOrder.verify(mNetd).networkRemoveInterface(INetd.LOCAL_NET_ID, IFACE_NAME);
        inOrder.verify(mNetd, times(isAtLeastT() ? 2 : 1)).interfaceSetCfg(
                argThat(cfg -> IFACE_NAME.equals(cfg.ifName)));
        inOrder.verify(mAddressCoordinator).releaseDownstream(any());
        inOrder.verify(mBpfCoordinator).tetherOffloadClientClear(mIpServer);
        inOrder.verify(mBpfCoordinator).stopMonitoring(mIpServer);
        inOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_AVAILABLE, TETHER_ERROR_NO_ERROR);
        inOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), any(LinkProperties.class));
        verifyNoMoreInteractions(mNetd, mCallback, mAddressCoordinator, mBpfCoordinator);
    }

    @Test
    public void interfaceDownLeadsToUnavailable() throws Exception {
        for (boolean shouldThrow : new boolean[]{true, false}) {
            initTetheredStateMachine(TETHERING_USB, null);

            if (shouldThrow) {
                doThrow(RemoteException.class).when(mNetd).tetherInterfaceRemove(IFACE_NAME);
            }
            dispatchCommand(IpServer.CMD_INTERFACE_DOWN);
            InOrder usbTeardownOrder = inOrder(mNetd, mCallback);
            // Currently IpServer interfaceSetCfg twice to stop IPv4. One just set interface down
            // Another one is set IPv4 to 0.0.0.0/0 as clearng ipv4 address.
            usbTeardownOrder.verify(mNetd, times(2)).interfaceSetCfg(
                    argThat(cfg -> IFACE_NAME.equals(cfg.ifName)));
            usbTeardownOrder.verify(mCallback).updateInterfaceState(
                    mIpServer, STATE_UNAVAILABLE, TETHER_ERROR_NO_ERROR);
            usbTeardownOrder.verify(mCallback).updateLinkProperties(
                    eq(mIpServer), mLinkPropertiesCaptor.capture());
            assertNoAddressesNorRoutes(mLinkPropertiesCaptor.getValue());
        }
    }

    @Test
    public void usbShouldBeTornDownOnTetherError() throws Exception {
        initStateMachine(TETHERING_USB);

        doThrow(RemoteException.class).when(mNetd).tetherInterfaceAdd(IFACE_NAME);
        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_TETHERED);
        InOrder usbTeardownOrder = inOrder(mNetd, mCallback);
        usbTeardownOrder.verify(mNetd).interfaceSetCfg(
                argThat(cfg -> IFACE_NAME.equals(cfg.ifName)));
        usbTeardownOrder.verify(mNetd).tetherInterfaceAdd(IFACE_NAME);

        usbTeardownOrder.verify(mNetd, times(2)).interfaceSetCfg(
                argThat(cfg -> IFACE_NAME.equals(cfg.ifName)));
        usbTeardownOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_AVAILABLE, TETHER_ERROR_TETHER_IFACE_ERROR);
        usbTeardownOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), mLinkPropertiesCaptor.capture());
        assertNoAddressesNorRoutes(mLinkPropertiesCaptor.getValue());
        verify(mTetheringMetrics).updateErrorCode(eq(TETHERING_USB),
                eq(TETHER_ERROR_TETHER_IFACE_ERROR));
        verify(mTetheringMetrics).sendReport(eq(TETHERING_USB));
    }

    @Test
    public void shouldTearDownUsbOnUpstreamError() throws Exception {
        initTetheredStateMachine(TETHERING_USB, null);

        doThrow(RemoteException.class).when(mNetd).tetherAddForward(anyString(), anyString());
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);
        InOrder usbTeardownOrder = inOrder(mNetd, mCallback);
        usbTeardownOrder.verify(mNetd).tetherAddForward(IFACE_NAME, UPSTREAM_IFACE);

        usbTeardownOrder.verify(mNetd, times(2)).interfaceSetCfg(
                argThat(cfg -> IFACE_NAME.equals(cfg.ifName)));
        usbTeardownOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_AVAILABLE, TETHER_ERROR_ENABLE_FORWARDING_ERROR);
        usbTeardownOrder.verify(mCallback).updateLinkProperties(
                eq(mIpServer), mLinkPropertiesCaptor.capture());
        assertNoAddressesNorRoutes(mLinkPropertiesCaptor.getValue());
        verify(mTetheringMetrics).updateErrorCode(eq(TETHERING_USB),
                eq(TETHER_ERROR_ENABLE_FORWARDING_ERROR));
        verify(mTetheringMetrics).sendReport(eq(TETHERING_USB));
    }

    @Test
    public void ignoresDuplicateUpstreamNotifications() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);

        verifyNoMoreInteractions(mNetd, mCallback);

        for (int i = 0; i < 5; i++) {
            dispatchTetherConnectionChanged(UPSTREAM_IFACE);
            verifyNoMoreInteractions(mNetd, mCallback);
        }
    }

    @Test
    public void startsDhcpServer() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);

        assertDhcpStarted(PrefixUtils.asIpPrefix(mTestAddress));
    }

    @Test
    public void startsDhcpServerOnBluetooth() throws Exception {
        initTetheredStateMachine(TETHERING_BLUETOOTH, UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);

        if (isAtLeastT()) {
            assertDhcpStarted(PrefixUtils.asIpPrefix(mTestAddress));
        } else {
            assertDhcpStarted(mBluetoothPrefix);
        }
    }

    @Test
    public void startsDhcpServerOnWifiP2p() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI_P2P, UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);

        assertDhcpStarted(PrefixUtils.asIpPrefix(mTestAddress));
    }

    @Test
    public void startsDhcpServerOnNcm() throws Exception {
        initStateMachine(TETHERING_NCM);
        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_LOCAL_ONLY);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);

        assertDhcpStarted(new IpPrefix("192.168.42.0/24"));
    }

    @Test
    public void testOnNewPrefixRequest() throws Exception {
        initStateMachine(TETHERING_NCM);
        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_LOCAL_ONLY);

        final IDhcpEventCallbacks eventCallbacks;
        final ArgumentCaptor<IDhcpEventCallbacks> dhcpEventCbsCaptor =
                 ArgumentCaptor.forClass(IDhcpEventCallbacks.class);
        verify(mDhcpServer, timeout(MAKE_DHCPSERVER_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), dhcpEventCbsCaptor.capture());
        eventCallbacks = dhcpEventCbsCaptor.getValue();
        assertDhcpStarted(new IpPrefix("192.168.42.0/24"));

        final ArgumentCaptor<LinkProperties> lpCaptor =
                ArgumentCaptor.forClass(LinkProperties.class);
        InOrder inOrder = inOrder(mNetd, mCallback, mAddressCoordinator);
        inOrder.verify(mAddressCoordinator).requestDownstreamAddress(any(),
                eq(CONNECTIVITY_SCOPE_LOCAL), eq(true));
        inOrder.verify(mNetd).networkAddInterface(INetd.LOCAL_NET_ID, IFACE_NAME);
        // One for ipv4 route, one for ipv6 link local route.
        inOrder.verify(mNetd, times(2)).networkAddRoute(eq(INetd.LOCAL_NET_ID), eq(IFACE_NAME),
                any(), any());
        inOrder.verify(mCallback).updateInterfaceState(
                mIpServer, STATE_LOCAL_ONLY, TETHER_ERROR_NO_ERROR);
        inOrder.verify(mCallback).updateLinkProperties(eq(mIpServer), lpCaptor.capture());
        verifyNoMoreInteractions(mCallback, mAddressCoordinator);

        // Simulate the DHCP server receives DHCPDECLINE on MirrorLink and then signals
        // onNewPrefixRequest callback.
        final LinkAddress newAddress = new LinkAddress("192.168.100.125/24");
        when(mAddressCoordinator.requestDownstreamAddress(any(), anyInt(),
                anyBoolean())).thenReturn(newAddress);
        eventCallbacks.onNewPrefixRequest(new IpPrefix("192.168.42.0/24"));
        mLooper.dispatchAll();

        inOrder.verify(mAddressCoordinator).requestDownstreamAddress(any(),
                eq(CONNECTIVITY_SCOPE_LOCAL), eq(false));
        inOrder.verify(mNetd).tetherApplyDnsInterfaces();
        inOrder.verify(mCallback).updateLinkProperties(eq(mIpServer), lpCaptor.capture());
        verifyNoMoreInteractions(mCallback);

        final LinkProperties linkProperties = lpCaptor.getValue();
        final List<LinkAddress> linkAddresses = linkProperties.getLinkAddresses();
        assertEquals(1, linkProperties.getLinkAddresses().size());
        assertEquals(1, linkProperties.getRoutes().size());
        final IpPrefix prefix = new IpPrefix(linkAddresses.get(0).getAddress(),
                linkAddresses.get(0).getPrefixLength());
        assertNotEquals(prefix, new IpPrefix("192.168.42.0/24"));

        verify(mDhcpServer).updateParams(mDhcpParamsCaptor.capture(), any());
        assertDhcpServingParams(mDhcpParamsCaptor.getValue(), prefix);
    }

    @Test
    public void doesNotStartDhcpServerIfDisabled() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE, NO_ADDRESSES,
                true /* usingLegacyDhcp */, DEFAULT_USING_BPF_OFFLOAD);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);

        verify(mDependencies, never()).makeDhcpServer(any(), any(), any());
    }

    @Test
    public void ipv6UpstreamInterfaceChanges() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE, UPSTREAM_ADDRESSES,
                false /* usingLegacyDhcp */, DEFAULT_USING_BPF_OFFLOAD);

        // Upstream interface changes result in updating the rules.
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(UPSTREAM_IFACE2);
        lp.setLinkAddresses(UPSTREAM_ADDRESSES);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE2, lp, -1);
        verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, UPSTREAM_IFINDEX2, UPSTREAM_PREFIXES);
        reset(mBpfCoordinator);

        // Upstream link addresses change result in updating the rules.
        LinkProperties lp2 = new LinkProperties();
        lp2.setInterfaceName(UPSTREAM_IFACE2);
        lp2.setLinkAddresses(UPSTREAM_ADDRESSES2);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE2, lp2, -1);
        verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, UPSTREAM_IFINDEX2, UPSTREAM_PREFIXES2);
        reset(mBpfCoordinator);

        // When the upstream is lost, rules are removed.
        dispatchTetherConnectionChanged(null, null, 0);
        // Upstream clear function is called two times by:
        // - processMessage CMD_TETHER_CONNECTION_CHANGED for the upstream is lost.
        // - processMessage CMD_IPV6_TETHER_UPDATE for the IPv6 upstream is lost.
        // See dispatchTetherConnectionChanged.
        verify(mBpfCoordinator, times(2)).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        reset(mBpfCoordinator);

        // If the upstream is IPv4-only, no rules are added.
        dispatchTetherConnectionChanged(UPSTREAM_IFACE);
        verify(mBpfCoordinator, never()).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        reset(mBpfCoordinator);

        // Rules are added again once upstream IPv6 connectivity is available.
        lp.setInterfaceName(UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, -1);
        verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, UPSTREAM_IFINDEX, UPSTREAM_PREFIXES);
        reset(mBpfCoordinator);

        // If upstream IPv6 connectivity is lost, rules are removed.
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, null, 0);
        verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        reset(mBpfCoordinator);

        // When upstream IPv6 connectivity comes back, rules are added.
        lp.setInterfaceName(UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, -1);
        verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, UPSTREAM_IFINDEX, UPSTREAM_PREFIXES);
        reset(mBpfCoordinator);

        // When the downstream interface goes down, rules are removed.
        mIpServer.stop();
        mLooper.dispatchAll();
        verify(mBpfCoordinator).clearAllIpv6Rules(mIpServer);
        verify(mBpfCoordinator).updateAllIpv6Rules(
                mIpServer, TEST_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        reset(mBpfCoordinator);
    }

    @Test
    public void stopNeighborMonitoringWhenInterfaceDown() throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE, UPSTREAM_ADDRESSES,
                false /* usingLegacyDhcp */, DEFAULT_USING_BPF_OFFLOAD);

        mIpServer.stop();
        mLooper.dispatchAll();
        verify(mIpNeighborMonitor).stop();
    }

    private LinkProperties buildIpv6OnlyLinkProperties(final String iface) {
        final LinkProperties linkProp = new LinkProperties();
        linkProp.setInterfaceName(iface);
        linkProp.addLinkAddress(new LinkAddress("2001:db8::1/64"));
        linkProp.addRoute(new RouteInfo(new IpPrefix("::/0"), null, iface, RTN_UNICAST));
        final InetAddress dns = InetAddresses.parseNumericAddress("2001:4860:4860::8888");
        linkProp.addDnsServer(dns);

        return linkProp;
    }

    @Test
    public void testAdjustTtlValue() throws Exception {
        final ArgumentCaptor<RaParams> raParamsCaptor =
                ArgumentCaptor.forClass(RaParams.class);
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);
        verify(mRaDaemon).buildNewRa(any(), raParamsCaptor.capture());
        final RaParams noV6Params = raParamsCaptor.getValue();
        assertEquals(65, noV6Params.hopLimit);
        reset(mRaDaemon);

        when(mNetd.getProcSysNet(
                INetd.IPV6, INetd.CONF, UPSTREAM_IFACE, "hop_limit")).thenReturn("64");
        final LinkProperties lp = buildIpv6OnlyLinkProperties(UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, 1);
        verify(mRaDaemon).buildNewRa(any(), raParamsCaptor.capture());
        final RaParams nonCellularParams = raParamsCaptor.getValue();
        assertEquals(65, nonCellularParams.hopLimit);
        reset(mRaDaemon);

        dispatchTetherConnectionChanged(UPSTREAM_IFACE, null, 0);
        verify(mRaDaemon).buildNewRa(any(), raParamsCaptor.capture());
        final RaParams noUpstream = raParamsCaptor.getValue();
        assertEquals(65, nonCellularParams.hopLimit);
        reset(mRaDaemon);

        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, -1);
        verify(mRaDaemon).buildNewRa(any(), raParamsCaptor.capture());
        final RaParams cellularParams = raParamsCaptor.getValue();
        assertEquals(63, cellularParams.hopLimit);
        reset(mRaDaemon);
    }

    @Test
    public void testStopObsoleteDhcpServer() throws Exception {
        final ArgumentCaptor<DhcpServerCallbacks> cbCaptor =
                ArgumentCaptor.forClass(DhcpServerCallbacks.class);
        doNothing().when(mDependencies).makeDhcpServer(any(), mDhcpParamsCaptor.capture(),
                cbCaptor.capture());
        initStateMachine(TETHERING_WIFI);
        dispatchCommand(IpServer.CMD_TETHER_REQUESTED, STATE_TETHERED);
        verify(mDhcpServer, never()).startWithCallbacks(any(), any());

        // No stop dhcp server because dhcp server is not created yet.
        dispatchCommand(IpServer.CMD_TETHER_UNREQUESTED);
        verify(mDhcpServer, never()).stop(any());

        // Stop obsolete dhcp server.
        try {
            final DhcpServerCallbacks cb = cbCaptor.getValue();
            cb.onDhcpServerCreated(STATUS_SUCCESS, mDhcpServer);
            mLooper.dispatchAll();
        } catch (RemoteException e) {
            fail(e.getMessage());
        }
        verify(mDhcpServer).stop(any());
    }

    private void assertDhcpServingParams(final DhcpServingParamsParcel params,
            final IpPrefix prefix) {
        // Last address byte is random
        assertTrue(prefix.contains(intToInet4AddressHTH(params.serverAddr)));
        assertEquals(prefix.getPrefixLength(), params.serverAddrPrefixLength);
        assertEquals(1, params.defaultRouters.length);
        assertEquals(params.serverAddr, params.defaultRouters[0]);
        assertEquals(1, params.dnsServers.length);
        assertEquals(params.serverAddr, params.dnsServers[0]);
        assertEquals(DHCP_LEASE_TIME_SECS, params.dhcpLeaseTimeSecs);
        if (mIpServer.interfaceType() == TETHERING_NCM) {
            assertTrue(params.changePrefixOnDecline);
        }

        if (mIpServer.interfaceType() == TETHERING_WIFI_P2P) {
            assertEquals(P2P_SUBNET_PREFIX_LENGTH, params.leasesSubnetPrefixLength);
        } else {
            assertEquals(DEFAULT_SUBNET_PREFIX_LENGTH, params.leasesSubnetPrefixLength);
        }
    }

    private void assertDhcpStarted(IpPrefix expectedPrefix) throws Exception {
        verify(mDependencies, times(1)).makeDhcpServer(eq(IFACE_NAME), any(), any());
        verify(mDhcpServer, timeout(MAKE_DHCPSERVER_TIMEOUT_MS).times(1)).startWithCallbacks(
                any(), any());
        assertDhcpServingParams(mDhcpParamsCaptor.getValue(), expectedPrefix);
    }

    /**
     * Send a command to the state machine under test, and run the event loop to idle.
     *
     * @param command One of the IpServer.CMD_* constants.
     * @param arg1 An additional argument to pass.
     */
    private void dispatchCommand(int command, int arg1) {
        mIpServer.sendMessage(command, arg1);
        mLooper.dispatchAll();
    }

    /**
     * Send a command to the state machine under test, and run the event loop to idle.
     *
     * @param command One of the IpServer.CMD_* constants.
     */
    private void dispatchCommand(int command) {
        mIpServer.sendMessage(command);
        mLooper.dispatchAll();
    }

    /**
     * Special override to tell the state machine that the upstream interface has changed.
     *
     * @see #dispatchCommand(int)
     * @param upstreamIface String name of upstream interface (or null)
     * @param v6lp IPv6 LinkProperties of the upstream interface, or null for an IPv4-only upstream.
     */
    private void dispatchTetherConnectionChanged(String upstreamIface, LinkProperties v6lp,
            int ttlAdjustment) {
        dispatchTetherConnectionChanged(upstreamIface);
        mIpServer.sendMessage(IpServer.CMD_IPV6_TETHER_UPDATE, ttlAdjustment, 0, v6lp);
        mLooper.dispatchAll();
    }

    private void dispatchTetherConnectionChanged(String upstreamIface) {
        final InterfaceSet ifs = (upstreamIface != null) ? new InterfaceSet(upstreamIface) : null;
        mIpServer.sendMessage(IpServer.CMD_TETHER_CONNECTION_CHANGED, ifs);
        mLooper.dispatchAll();
    }

    private void assertIPv4AddressAndDirectlyConnectedRoute(LinkProperties lp) {
        // Find the first IPv4 LinkAddress.
        LinkAddress addr4 = null;
        for (LinkAddress addr : lp.getLinkAddresses()) {
            if (!(addr.getAddress() instanceof Inet4Address)) continue;
            addr4 = addr;
            break;
        }
        assertNotNull("missing IPv4 address", addr4);

        final IpPrefix destination = new IpPrefix(addr4.getAddress(), addr4.getPrefixLength());
        // Assert the presence of the associated directly connected route.
        final RouteInfo directlyConnected = new RouteInfo(destination, null, lp.getInterfaceName(),
                RouteInfo.RTN_UNICAST);
        assertTrue("missing directly connected route: '" + directlyConnected.toString() + "'",
                   lp.getRoutes().contains(directlyConnected));
    }

    private void assertNoAddressesNorRoutes(LinkProperties lp) {
        assertTrue(lp.getLinkAddresses().isEmpty());
        assertTrue(lp.getRoutes().isEmpty());
        // We also check that interface name is non-empty, because we should
        // never see an empty interface name in any LinkProperties update.
        assertFalse(TextUtils.isEmpty(lp.getInterfaceName()));
    }

    private boolean assertContainsFlag(String[] flags, String match) {
        for (String flag : flags) {
            if (flag.equals(match)) return true;
        }
        return false;
    }

    private boolean assertNotContainsFlag(String[] flags, String match) {
        for (String flag : flags) {
            if (flag.equals(match)) {
                fail("Unexpected flag: " + match);
                return false;
            }
        }
        return true;
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void dadProxyUpdates() throws Exception {
        InOrder inOrder = inOrder(mDadProxy);
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);
        inOrder.verify(mDadProxy).setUpstreamIface(UPSTREAM_IFACE_PARAMS);

        // Add an upstream without IPv6.
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, null, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(null);

        // Add IPv6 to the upstream.
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(UPSTREAM_IFACE_PARAMS);

        // Change upstream.
        // New linkproperties is needed, otherwise changing the iface has no impact.
        LinkProperties lp2 = new LinkProperties();
        lp2.setInterfaceName(UPSTREAM_IFACE2);
        dispatchTetherConnectionChanged(UPSTREAM_IFACE2, lp2, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(UPSTREAM_IFACE_PARAMS2);

        // Lose IPv6 on the upstream...
        dispatchTetherConnectionChanged(UPSTREAM_IFACE2, null, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(null);

        // ... and regain it on a different upstream.
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(UPSTREAM_IFACE_PARAMS);

        // Lose upstream.
        dispatchTetherConnectionChanged(null, null, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(null);

        // Regain upstream.
        dispatchTetherConnectionChanged(UPSTREAM_IFACE, lp, 0);
        inOrder.verify(mDadProxy).setUpstreamIface(UPSTREAM_IFACE_PARAMS);

        // Stop tethering.
        mIpServer.stop();
        mLooper.dispatchAll();
    }

    private void checkDadProxyEnabled(boolean expectEnabled) throws Exception {
        initTetheredStateMachine(TETHERING_WIFI, UPSTREAM_IFACE);
        InOrder inOrder = inOrder(mDadProxy);
        // Add IPv6 to the upstream.
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(UPSTREAM_IFACE);
        if (expectEnabled) {
            inOrder.verify(mDadProxy).setUpstreamIface(UPSTREAM_IFACE_PARAMS);
        } else {
            inOrder.verifyNoMoreInteractions();
        }
        // Stop tethering.
        mIpServer.stop();
        mLooper.dispatchAll();
        if (expectEnabled) {
            inOrder.verify(mDadProxy).stop();
        }
        else {
            verify(mDependencies, never()).getDadProxy(any(), any());
        }
    }
    @Test @IgnoreAfter(Build.VERSION_CODES.R)
    public void testDadProxyUpdates_DisabledUpToR() throws Exception {
        checkDadProxyEnabled(false);
    }
    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testDadProxyUpdates_EnabledAfterR() throws Exception {
        checkDadProxyEnabled(true);
    }
}
