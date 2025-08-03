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

package com.android.networkstack.tethering;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStats.UID_TETHERING;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.ip.IpServer.STATE_TETHERED;
import static android.net.netstats.provider.NetworkStatsProvider.QUOTA_UNLIMITED;
import static android.system.OsConstants.ETH_P_IP;
import static android.system.OsConstants.ETH_P_IPV6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.NETLINK_NETFILTER;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.net.module.util.NetworkStackConstants.IPV4_MIN_MTU;
import static com.android.net.module.util.ip.ConntrackMonitor.ConntrackEvent;
import static com.android.net.module.util.netlink.ConntrackMessage.DYING_MASK;
import static com.android.net.module.util.netlink.ConntrackMessage.ESTABLISHED_MASK;
import static com.android.net.module.util.netlink.ConntrackMessage.Tuple;
import static com.android.net.module.util.netlink.ConntrackMessage.TupleIpv4;
import static com.android.net.module.util.netlink.ConntrackMessage.TupleProto;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_DELETE;
import static com.android.net.module.util.netlink.NetlinkConstants.IPCTNL_MSG_CT_NEW;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_DELNEIGH;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_NEWNEIGH;
import static com.android.net.module.util.netlink.StructNdMsg.NUD_FAILED;
import static com.android.net.module.util.netlink.StructNdMsg.NUD_REACHABLE;
import static com.android.net.module.util.netlink.StructNdMsg.NUD_STALE;
import static com.android.networkstack.tethering.BpfCoordinator.CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS;
import static com.android.networkstack.tethering.BpfCoordinator.INVALID_MTU;
import static com.android.networkstack.tethering.BpfCoordinator.NF_CONNTRACK_TCP_TIMEOUT_ESTABLISHED;
import static com.android.networkstack.tethering.BpfCoordinator.NF_CONNTRACK_UDP_TIMEOUT_STREAM;
import static com.android.networkstack.tethering.BpfCoordinator.NON_OFFLOADED_UPSTREAM_IPV4_TCP_PORTS;
import static com.android.networkstack.tethering.BpfCoordinator.StatsType;
import static com.android.networkstack.tethering.BpfCoordinator.StatsType.STATS_PER_IFACE;
import static com.android.networkstack.tethering.BpfCoordinator.StatsType.STATS_PER_UID;
import static com.android.networkstack.tethering.BpfCoordinator.toIpv4MappedAddressBytes;
import static com.android.networkstack.tethering.BpfUtils.DOWNSTREAM;
import static com.android.networkstack.tethering.BpfUtils.UPSTREAM;
import static com.android.networkstack.tethering.TetheringConfiguration.DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS;
import static com.android.testutils.MiscAsserts.assertSameElements;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.usage.NetworkStatsManager;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkStats;
import android.net.RoutingCoordinatorManager;
import android.net.TetherOffloadRuleParcel;
import android.net.TetherStatsParcel;
import android.net.ip.IpServer;
import android.net.ip.RouterAdvertisementDaemon;
import android.os.Build;
import android.os.Handler;
import android.os.test.TestLooper;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.util.IndentingPrintWriter;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.NetworkStackConstants;
import com.android.net.module.util.SdkUtil.LateSdk;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.Struct.S32;
import com.android.net.module.util.bpf.Tether4Key;
import com.android.net.module.util.bpf.Tether4Value;
import com.android.net.module.util.bpf.TetherStatsKey;
import com.android.net.module.util.bpf.TetherStatsValue;
import com.android.net.module.util.ip.ConntrackMonitor;
import com.android.net.module.util.ip.ConntrackMonitor.ConntrackEventConsumer;
import com.android.net.module.util.ip.IpNeighborMonitor;
import com.android.net.module.util.ip.IpNeighborMonitor.NeighborEvent;
import com.android.net.module.util.ip.IpNeighborMonitor.NeighborEventConsumer;
import com.android.net.module.util.netlink.ConntrackMessage;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.networkstack.tethering.BpfCoordinator.BpfConntrackEventConsumer;
import com.android.networkstack.tethering.BpfCoordinator.ClientInfo;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6DownstreamRule;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6UpstreamRule;
import com.android.networkstack.tethering.metrics.TetheringMetrics;
import com.android.networkstack.tethering.util.InterfaceSet;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.TestBpfMap;
import com.android.testutils.TestableNetworkStatsProviderCbBinder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.verification.VerificationMode;

import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BpfCoordinatorTest {
    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private static final boolean IPV4 = true;
    private static final boolean IPV6 = false;

    private static final int TEST_NET_ID = 24;
    private static final int TEST_NET_ID2 = 25;

    private static final int NO_UPSTREAM = 0;
    private static final int UPSTREAM_IFINDEX = 1001;
    private static final int UPSTREAM_XLAT_IFINDEX = 1002;
    private static final int UPSTREAM_IFINDEX2 = 1003;
    private static final int DOWNSTREAM_IFINDEX = 2001;
    private static final int DOWNSTREAM_IFINDEX2 = 2002;
    private static final int IPSEC_IFINDEX = 103;

    private static final String UPSTREAM_IFACE = "rmnet0";
    private static final String UPSTREAM_XLAT_IFACE = "v4-rmnet0";
    private static final String UPSTREAM_IFACE2 = "wlan0";
    private static final String DOWNSTREAM_IFACE = "downstream1";
    private static final String DOWNSTREAM_IFACE2 = "downstream2";
    private static final String IPSEC_IFACE = "ipsec0";

    private static final MacAddress DOWNSTREAM_MAC = MacAddress.fromString("12:34:56:78:90:ab");
    private static final MacAddress DOWNSTREAM_MAC2 = MacAddress.fromString("ab:90:78:56:34:12");

    private static final MacAddress MAC_A = MacAddress.fromString("00:00:00:00:00:0a");
    private static final MacAddress MAC_B = MacAddress.fromString("11:22:33:00:00:0b");
    private static final MacAddress MAC_NULL = MacAddress.fromString("00:00:00:00:00:00");

    private static final LinkAddress UPSTREAM_ADDRESS = new LinkAddress("2001:db8:0:1234::168/64");
    private static final LinkAddress UPSTREAM_ADDRESS2 = new LinkAddress("2001:db8:0:abcd::168/64");
    private static final Set<LinkAddress> UPSTREAM_ADDRESSES = Set.of(UPSTREAM_ADDRESS);
    private static final Set<LinkAddress> UPSTREAM_ADDRESSES2 =
            Set.of(UPSTREAM_ADDRESS, UPSTREAM_ADDRESS2);
    private static final IpPrefix UPSTREAM_PREFIX = new IpPrefix("2001:db8:0:1234::/64");
    private static final IpPrefix UPSTREAM_PREFIX2 = new IpPrefix("2001:db8:0:abcd::/64");
    private static final Set<IpPrefix> UPSTREAM_PREFIXES = Set.of(UPSTREAM_PREFIX);
    private static final Set<IpPrefix> UPSTREAM_PREFIXES2 =
            Set.of(UPSTREAM_PREFIX, UPSTREAM_PREFIX2);
    private static final Set<IpPrefix> NO_PREFIXES = Set.of();

    private static final InetAddress NEIGH_A =
            InetAddresses.parseNumericAddress("2001:db8:0:1234::1");
    private static final InetAddress NEIGH_B =
            InetAddresses.parseNumericAddress("2001:db8:0:1234::2");
    private static final InetAddress NEIGH_LL = InetAddresses.parseNumericAddress("fe80::1");
    private static final InetAddress NEIGH_MC = InetAddresses.parseNumericAddress("ff02::1234");

    private static final Inet4Address REMOTE_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("140.112.8.116");
    private static final Inet4Address PUBLIC_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("1.0.0.1");
    private static final Inet4Address PUBLIC_ADDR2 =
            (Inet4Address) InetAddresses.parseNumericAddress("1.0.0.2");
    private static final Inet4Address PRIVATE_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("192.168.80.12");
    private static final Inet4Address PRIVATE_ADDR2 =
            (Inet4Address) InetAddresses.parseNumericAddress("192.168.90.12");

    private static final Inet4Address XLAT_LOCAL_IPV4ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.0.46");
    private static final IpPrefix NAT64_IP_PREFIX = new IpPrefix("64:ff9b::/96");

    // Generally, public port and private port are the same in the NAT conntrack message.
    // TODO: consider using different private port and public port for testing.
    private static final short REMOTE_PORT = (short) 443;
    private static final short PUBLIC_PORT = (short) 62449;
    private static final short PUBLIC_PORT2 = (short) 62450;
    private static final short PRIVATE_PORT = (short) 62449;
    private static final short PRIVATE_PORT2 = (short) 62450;

    private static final InterfaceParams UPSTREAM_IFACE_PARAMS = new InterfaceParams(
            UPSTREAM_IFACE, UPSTREAM_IFINDEX, null /* macAddr, rawip */,
            NetworkStackConstants.ETHER_MTU);
    private static final InterfaceParams UPSTREAM_XLAT_IFACE_PARAMS = new InterfaceParams(
            UPSTREAM_XLAT_IFACE, UPSTREAM_XLAT_IFINDEX, null /* macAddr, rawip */,
            NetworkStackConstants.ETHER_MTU - 28
            /* mtu delta from external/android-clat/clatd.c */);
    private static final InterfaceParams UPSTREAM_IFACE_PARAMS2 = new InterfaceParams(
            UPSTREAM_IFACE2, UPSTREAM_IFINDEX2, MacAddress.fromString("44:55:66:00:00:0c"),
            NetworkStackConstants.ETHER_MTU);
    private static final InterfaceParams DOWNSTREAM_IFACE_PARAMS = new InterfaceParams(
            DOWNSTREAM_IFACE, DOWNSTREAM_IFINDEX, DOWNSTREAM_MAC, NetworkStackConstants.ETHER_MTU);
    private static final InterfaceParams DOWNSTREAM_IFACE_PARAMS2 = new InterfaceParams(
            DOWNSTREAM_IFACE2, DOWNSTREAM_IFINDEX2, DOWNSTREAM_MAC2,
            NetworkStackConstants.ETHER_MTU);
    private static final InterfaceParams IPSEC_IFACE_PARAMS = new InterfaceParams(
            IPSEC_IFACE, IPSEC_IFINDEX, MacAddress.ALL_ZEROS_ADDRESS,
            NetworkStackConstants.ETHER_MTU);

    private static final Map<Integer, UpstreamInformation> UPSTREAM_INFORMATIONS = Map.of(
            UPSTREAM_IFINDEX, new UpstreamInformation(UPSTREAM_IFACE_PARAMS,
                    PUBLIC_ADDR, NetworkCapabilities.TRANSPORT_CELLULAR, TEST_NET_ID),
            UPSTREAM_IFINDEX2, new UpstreamInformation(UPSTREAM_IFACE_PARAMS2,
                    PUBLIC_ADDR2, NetworkCapabilities.TRANSPORT_WIFI, TEST_NET_ID2));

    private static final ClientInfo CLIENT_INFO_A = new ClientInfo(DOWNSTREAM_IFINDEX,
            DOWNSTREAM_MAC, PRIVATE_ADDR, MAC_A);
    private static final ClientInfo CLIENT_INFO_B = new ClientInfo(DOWNSTREAM_IFINDEX2,
            DOWNSTREAM_MAC2, PRIVATE_ADDR2, MAC_B);

    private static class UpstreamInformation {
        public final InterfaceParams interfaceParams;
        public final Inet4Address address;
        public final int transportType;
        public final int netId;

        UpstreamInformation(final InterfaceParams interfaceParams,
                final Inet4Address address, int transportType, int netId) {
            this.interfaceParams = interfaceParams;
            this.address = address;
            this.transportType = transportType;
            this.netId = netId;
        }
    }

    private static class TestUpstream4Key {
        public static class Builder {
            private int mIif = DOWNSTREAM_IFINDEX;
            private MacAddress mDstMac = DOWNSTREAM_MAC;
            private short mL4proto = (short) IPPROTO_TCP;
            private byte[] mSrc4 = PRIVATE_ADDR.getAddress();
            private byte[] mDst4 = REMOTE_ADDR.getAddress();
            private int mSrcPort = PRIVATE_PORT;
            private int mDstPort = REMOTE_PORT;

            public Builder setProto(int proto) {
                if (proto != IPPROTO_TCP && proto != IPPROTO_UDP) {
                    fail("Not support protocol " + proto);
                }
                mL4proto = (short) proto;
                return this;
            }

            public Tether4Key build() {
                return new Tether4Key(mIif, mDstMac, mL4proto, mSrc4, mDst4, mSrcPort, mDstPort);
            }
        }
    }

    private static class TestDownstream4Key {
        public static class Builder {
            private int mIif = UPSTREAM_IFINDEX;
            private MacAddress mDstMac = MacAddress.ALL_ZEROS_ADDRESS /* dstMac (rawip) */;
            private short mL4proto = (short) IPPROTO_TCP;
            private byte[] mSrc4 = REMOTE_ADDR.getAddress();
            private byte[] mDst4 = PUBLIC_ADDR.getAddress();
            private int mSrcPort = REMOTE_PORT;
            private int mDstPort = PUBLIC_PORT;

            public Builder setProto(int proto) {
                if (proto != IPPROTO_TCP && proto != IPPROTO_UDP) {
                    fail("Not support protocol " + proto);
                }
                mL4proto = (short) proto;
                return this;
            }

            public Tether4Key build() {
                return new Tether4Key(mIif, mDstMac, mL4proto, mSrc4, mDst4, mSrcPort, mDstPort);
            }
        }
    }

    private static class TestUpstream4Value {
        public static class Builder {
            private int mOif = UPSTREAM_IFINDEX;
            private MacAddress mEthDstMac = MacAddress.ALL_ZEROS_ADDRESS /* dstMac (rawip) */;
            private MacAddress mEthSrcMac = MacAddress.ALL_ZEROS_ADDRESS /* dstMac (rawip) */;
            private int mEthProto = ETH_P_IP;
            private short mPmtu = NetworkStackConstants.ETHER_MTU;
            private byte[] mSrc46 = toIpv4MappedAddressBytes(PUBLIC_ADDR);
            private byte[] mDst46 = toIpv4MappedAddressBytes(REMOTE_ADDR);
            private int mSrcPort = PUBLIC_PORT;
            private int mDstPort = REMOTE_PORT;
            private long mLastUsed = 0;

            public Builder setPmtu(short pmtu) {
                mPmtu = pmtu;
                return this;
            }

            public Tether4Value build() {
                return new Tether4Value(mOif, mEthDstMac, mEthSrcMac, mEthProto, mPmtu,
                        mSrc46, mDst46, mSrcPort, mDstPort, mLastUsed);
            }
        }
    }

    private static class TestDownstream4Value {
        public static class Builder {
            private int mOif = DOWNSTREAM_IFINDEX;
            private MacAddress mEthDstMac = MAC_A /* client mac */;
            private MacAddress mEthSrcMac = DOWNSTREAM_MAC;
            private int mEthProto = ETH_P_IP;
            private short mPmtu = NetworkStackConstants.ETHER_MTU;
            private byte[] mSrc46 = toIpv4MappedAddressBytes(REMOTE_ADDR);
            private byte[] mDst46 = toIpv4MappedAddressBytes(PRIVATE_ADDR);
            private int mSrcPort = REMOTE_PORT;
            private int mDstPort = PRIVATE_PORT;
            private long mLastUsed = 0;

            public Builder setPmtu(short pmtu) {
                mPmtu = pmtu;
                return this;
            }

            public Tether4Value build() {
                return new Tether4Value(mOif, mEthDstMac, mEthSrcMac, mEthProto, mPmtu,
                        mSrc46, mDst46, mSrcPort, mDstPort, mLastUsed);
            }
        }
    }

    private static class TestConntrackEvent {
        public static class Builder {
            private short mMsgType = IPCTNL_MSG_CT_NEW;
            private short mProto = (short) IPPROTO_TCP;
            private Inet4Address mPrivateAddr = PRIVATE_ADDR;
            private Inet4Address mPublicAddr = PUBLIC_ADDR;
            private Inet4Address mRemoteAddr = REMOTE_ADDR;
            private short mPrivatePort = PRIVATE_PORT;
            private short mPublicPort = PUBLIC_PORT;
            private short mRemotePort = REMOTE_PORT;

            public Builder setMsgType(short msgType) {
                if (msgType != IPCTNL_MSG_CT_NEW && msgType != IPCTNL_MSG_CT_DELETE) {
                    fail("Not support message type " + msgType);
                }
                mMsgType = (short) msgType;
                return this;
            }

            public Builder setProto(int proto) {
                if (proto != IPPROTO_TCP && proto != IPPROTO_UDP) {
                    fail("Not support protocol " + proto);
                }
                mProto = (short) proto;
                return this;
            }

            public Builder setRemotePort(int remotePort) {
                mRemotePort = (short) remotePort;
                return this;
            }

            public ConntrackEvent build() {
                final int status = (mMsgType == IPCTNL_MSG_CT_NEW) ? ESTABLISHED_MASK : DYING_MASK;
                final int timeoutSec = (mMsgType == IPCTNL_MSG_CT_NEW) ? 100 /* nonzero, new */
                        : 0 /* unused, delete */;
                return new ConntrackEvent(
                        (short) (NetlinkConstants.NFNL_SUBSYS_CTNETLINK << 8 | mMsgType),
                        new Tuple(new TupleIpv4(mPrivateAddr, mRemoteAddr),
                                new TupleProto((byte) mProto, mPrivatePort, mRemotePort)),
                        new Tuple(new TupleIpv4(mRemoteAddr, mPublicAddr),
                                new TupleProto((byte) mProto, mRemotePort, mPublicPort)),
                        status,
                        timeoutSec);
            }
        }
    }

    @Mock private NetworkStatsManager mStatsManager;
    @Mock private INetd mNetd;
    @Mock private IpServer mIpServer;
    @Mock private IpServer mIpServer2;
    @Mock private TetheringConfiguration mTetherConfig;
    @Mock private ConntrackMonitor mConntrackMonitor;
    @Mock private IpNeighborMonitor mIpNeighborMonitor;
    @Mock private RouterAdvertisementDaemon mRaDaemon;
    @Mock private IpServer.Dependencies mIpServerDeps;
    @Mock private IpServer.Callback mIpServerCallback;
    @Mock private PrivateAddressCoordinator mAddressCoordinator;
    private final LateSdk<RoutingCoordinatorManager> mRoutingCoordinatorManager =
            new LateSdk<>(SdkLevel.isAtLeastS() ? mock(RoutingCoordinatorManager.class) : null);
    @Mock private TetheringMetrics mTetheringMetrics;

    // Late init since methods must be called by the thread that created this object.
    private TestableNetworkStatsProviderCbBinder mTetherStatsProviderCb;
    private BpfCoordinator.BpfTetherStatsProvider mTetherStatsProvider;

    // Late init since the object must be initialized by the BPF coordinator instance because
    // it has to access the non-static function of BPF coordinator.
    private BpfConntrackEventConsumer mConsumer;
    private NeighborEventConsumer mNeighborEventConsumer;
    private HashMap<IpServer, HashMap<Inet4Address, ClientInfo>> mTetherClients;

    private long mElapsedRealtimeNanos = 0;
    private int mMtu = NetworkStackConstants.ETHER_MTU;
    private final ArgumentCaptor<ArrayList> mStringArrayCaptor =
            ArgumentCaptor.forClass(ArrayList.class);
    private final TestLooper mTestLooper = new TestLooper();
    private final Handler mHandler = new Handler(mTestLooper.getLooper());
    private final IBpfMap<Tether4Key, Tether4Value> mBpfDownstream4Map =
            spy(new TestBpfMap<>(Tether4Key.class, Tether4Value.class));
    private final IBpfMap<Tether4Key, Tether4Value> mBpfUpstream4Map =
            spy(new TestBpfMap<>(Tether4Key.class, Tether4Value.class));
    private final IBpfMap<TetherDownstream6Key, Tether6Value> mBpfDownstream6Map =
            spy(new TestBpfMap<>(TetherDownstream6Key.class, Tether6Value.class));
    private final IBpfMap<TetherUpstream6Key, Tether6Value> mBpfUpstream6Map =
            spy(new TestBpfMap<>(TetherUpstream6Key.class, Tether6Value.class));
    private final IBpfMap<TetherStatsKey, TetherStatsValue> mBpfStatsMap =
            spy(new TestBpfMap<>(TetherStatsKey.class, TetherStatsValue.class));
    private final IBpfMap<TetherLimitKey, TetherLimitValue> mBpfLimitMap =
            spy(new TestBpfMap<>(TetherLimitKey.class, TetherLimitValue.class));
    private final IBpfMap<TetherDevKey, TetherDevValue> mBpfDevMap =
            spy(new TestBpfMap<>(TetherDevKey.class, TetherDevValue.class));
    private final IBpfMap<S32, S32> mBpfErrorMap =
            spy(new TestBpfMap<>(S32.class, S32.class));
    private BpfCoordinator.Dependencies mDeps =
            spy(new BpfCoordinator.Dependencies() {
                    @NonNull
                    public Handler getHandler() {
                        return mHandler;
                    }

                    @NonNull
                    public INetd getNetd() {
                        return mNetd;
                    }

                    @NonNull
                    public NetworkStatsManager getNetworkStatsManager() {
                        return mStatsManager;
                    }

                    @NonNull
                    public SharedLog getSharedLog() {
                        return new SharedLog("test");
                    }

                    @Nullable
                    public TetheringConfiguration getTetherConfig() {
                        return mTetherConfig;
                    }

                    @NonNull
                    public ConntrackMonitor getConntrackMonitor(ConntrackEventConsumer consumer) {
                        return mConntrackMonitor;
                    }

                    public long elapsedRealtimeNanos() {
                        return mElapsedRealtimeNanos;
                    }

                    public int getNetworkInterfaceMtu(@NonNull String iface) {
                        return mMtu;
                    }

                    @Nullable
                    public IBpfMap<Tether4Key, Tether4Value> getBpfDownstream4Map() {
                        return mBpfDownstream4Map;
                    }

                    @Nullable
                    public IBpfMap<Tether4Key, Tether4Value> getBpfUpstream4Map() {
                        return mBpfUpstream4Map;
                    }

                    @Nullable
                    public IBpfMap<TetherDownstream6Key, Tether6Value> getBpfDownstream6Map() {
                        return mBpfDownstream6Map;
                    }

                    @Nullable
                    public IBpfMap<TetherUpstream6Key, Tether6Value> getBpfUpstream6Map() {
                        return mBpfUpstream6Map;
                    }

                    @Nullable
                    public IBpfMap<TetherStatsKey, TetherStatsValue> getBpfStatsMap() {
                        return mBpfStatsMap;
                    }

                    @Nullable
                    public IBpfMap<TetherLimitKey, TetherLimitValue> getBpfLimitMap() {
                        return mBpfLimitMap;
                    }

                    @Nullable
                    public IBpfMap<TetherDevKey, TetherDevValue> getBpfDevMap() {
                        return mBpfDevMap;
                    }

                    @Nullable
                    public IBpfMap<S32, S32> getBpfErrorMap() {
                        return mBpfErrorMap;
                    }
            });

    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mTetherConfig.isBpfOffloadEnabled()).thenReturn(true /* default value */);

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
    }

    private void waitForIdle() {
        mTestLooper.dispatchAll();
    }

    // TODO: Remove unnecessary calling on R because the BPF map accessing has been moved into
    // module.
    private void setupFunctioningNetdInterface() throws Exception {
        when(mNetd.tetherOffloadGetStats()).thenReturn(new TetherStatsParcel[0]);
    }

    @NonNull
    private IpServer makeAndStartIpServer(String interfaceName, BpfCoordinator bpfCoordinator)
            throws Exception {
        final LinkAddress testAddress = new LinkAddress("192.168.42.5/24");
        when(mIpServerDeps.getRouterAdvertisementDaemon(any())).thenReturn(mRaDaemon);
        when(mIpServerDeps.getInterfaceParams(DOWNSTREAM_IFACE)).thenReturn(
                DOWNSTREAM_IFACE_PARAMS);
        when(mIpServerDeps.getInterfaceParams(UPSTREAM_IFACE)).thenReturn(UPSTREAM_IFACE_PARAMS);
        when(mIpServerDeps.getInterfaceParams(UPSTREAM_IFACE2)).thenReturn(UPSTREAM_IFACE_PARAMS2);
        when(mIpServerDeps.getInterfaceParams(IPSEC_IFACE)).thenReturn(IPSEC_IFACE_PARAMS);
        when(mAddressCoordinator.requestDownstreamAddress(any(), anyInt(),
                anyBoolean())).thenReturn(testAddress);
        when(mRaDaemon.start()).thenReturn(true);
        ArgumentCaptor<NeighborEventConsumer> neighborEventCaptor =
                ArgumentCaptor.forClass(NeighborEventConsumer.class);
        doReturn(mIpNeighborMonitor).when(mIpServerDeps).getIpNeighborMonitor(any(), any(),
                neighborEventCaptor.capture());
        final IpServer ipServer = new IpServer(
                interfaceName, mHandler, TETHERING_WIFI, new SharedLog("test"), mNetd,
                bpfCoordinator, mRoutingCoordinatorManager, mIpServerCallback, mTetherConfig,
                mAddressCoordinator, mTetheringMetrics, mIpServerDeps);
        ipServer.start();
        ipServer.sendMessage(IpServer.CMD_TETHER_REQUESTED, STATE_TETHERED);
        mTestLooper.dispatchAll();

        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(UPSTREAM_IFACE);
        lp.setLinkAddresses(UPSTREAM_ADDRESSES);
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE, lp, 0);

        mNeighborEventConsumer = neighborEventCaptor.getValue();
        return ipServer;
    }

    private void dispatchTetherConnectionChanged(IpServer ipServer, String upstreamIface,
            LinkProperties v6lp, int ttlAdjustment) {
        dispatchTetherConnectionChanged(ipServer, upstreamIface);
        ipServer.sendMessage(IpServer.CMD_IPV6_TETHER_UPDATE, ttlAdjustment, 0, v6lp);
        mTestLooper.dispatchAll();
    }

    private void dispatchTetherConnectionChanged(IpServer ipServer, String upstreamIface) {
        final InterfaceSet ifs = (upstreamIface != null) ? new InterfaceSet(upstreamIface) : null;
        ipServer.sendMessage(IpServer.CMD_TETHER_CONNECTION_CHANGED, ifs);
        mTestLooper.dispatchAll();
    }

    private void recvNewNeigh(int ifindex, InetAddress addr, short nudState, MacAddress mac) {
        mNeighborEventConsumer.accept(new NeighborEvent(0, RTM_NEWNEIGH, ifindex, addr,
                nudState, mac));
        mTestLooper.dispatchAll();
    }

    private void recvDelNeigh(int ifindex, InetAddress addr, short nudState, MacAddress mac) {
        mNeighborEventConsumer.accept(new NeighborEvent(0, RTM_DELNEIGH, ifindex, addr,
                nudState, mac));
        mTestLooper.dispatchAll();
    }

    @NonNull
    private BpfCoordinator makeBpfCoordinator() throws Exception {
        // mStatsManager will be invoked twice if BpfCoordinator is created the second time.
        clearInvocations(mStatsManager);
        final BpfCoordinator coordinator = new BpfCoordinator(mDeps);

        mConsumer = coordinator.getBpfConntrackEventConsumerForTesting();
        mTetherClients = coordinator.getTetherClientsForTesting();

        final ArgumentCaptor<BpfCoordinator.BpfTetherStatsProvider>
                tetherStatsProviderCaptor =
                ArgumentCaptor.forClass(BpfCoordinator.BpfTetherStatsProvider.class);
        verify(mStatsManager).registerNetworkStatsProvider(anyString(),
                tetherStatsProviderCaptor.capture());
        mTetherStatsProvider = tetherStatsProviderCaptor.getValue();
        assertNotNull(mTetherStatsProvider);
        mTetherStatsProviderCb = new TestableNetworkStatsProviderCbBinder();
        mTetherStatsProvider.setProviderCallbackBinder(mTetherStatsProviderCb);

        return coordinator;
    }

    @NonNull
    private static NetworkStats.Entry buildTestEntry(@NonNull StatsType how,
            @NonNull String iface, long rxBytes, long rxPackets, long txBytes, long txPackets) {
        return new NetworkStats.Entry(iface, how == STATS_PER_IFACE ? UID_ALL : UID_TETHERING,
                SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, rxBytes,
                rxPackets, txBytes, txPackets, 0L);
    }

    @NonNull
    private static TetherStatsParcel buildTestTetherStatsParcel(@NonNull Integer ifIndex,
            long rxBytes, long rxPackets, long txBytes, long txPackets) {
        final TetherStatsParcel parcel = new TetherStatsParcel();
        parcel.ifIndex = ifIndex;
        parcel.rxBytes = rxBytes;
        parcel.rxPackets = rxPackets;
        parcel.txBytes = txBytes;
        parcel.txPackets = txPackets;
        return parcel;
    }

    // Update a stats entry or create if not exists.
    private void updateStatsEntryToStatsMap(@NonNull TetherStatsParcel stats) throws Exception {
        final TetherStatsKey key = new TetherStatsKey(stats.ifIndex);
        final TetherStatsValue value = new TetherStatsValue(stats.rxPackets, stats.rxBytes,
                0L /* rxErrors */, stats.txPackets, stats.txBytes, 0L /* txErrors */);
        mBpfStatsMap.updateEntry(key, value);
    }

    private void updateStatsEntry(@NonNull TetherStatsParcel stats) throws Exception {
        if (mDeps.isAtLeastS()) {
            updateStatsEntryToStatsMap(stats);
        } else {
            when(mNetd.tetherOffloadGetStats()).thenReturn(new TetherStatsParcel[] {stats});
        }
    }

    // Update specific tether stats list and wait for the stats cache is updated by polling thread
    // in the coordinator. Beware of that it is only used for the default polling interval.
    // Note that the mocked tetherOffloadGetStats of netd replaces all stats entries because it
    // doesn't store the previous entries.
    private void updateStatsEntriesAndWaitForUpdate(@NonNull TetherStatsParcel[] tetherStatsList)
            throws Exception {
        if (mDeps.isAtLeastS()) {
            for (TetherStatsParcel stats : tetherStatsList) {
                updateStatsEntry(stats);
            }
        } else {
            when(mNetd.tetherOffloadGetStats()).thenReturn(tetherStatsList);
        }

        mTestLooper.moveTimeForward(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        waitForIdle();
    }

    // In tests, the stats need to be set before deleting the last rule.
    // The reason is that BpfCoordinator#tetherOffloadRuleRemove reads the stats
    // of the deleting interface after the last rule deleted. #tetherOffloadRuleRemove
    // does the interface cleanup failed if there is no stats for the deleting interface.
    // Note that the mocked tetherOffloadGetAndClearStats of netd replaces all stats entries
    // because it doesn't store the previous entries.
    private void updateStatsEntryForTetherOffloadGetAndClearStats(TetherStatsParcel stats)
            throws Exception {
        if (mDeps.isAtLeastS()) {
            updateStatsEntryToStatsMap(stats);
        } else {
            when(mNetd.tetherOffloadGetAndClearStats(stats.ifIndex)).thenReturn(stats);
        }
    }

    private void clearStatsInvocations() {
        if (mDeps.isAtLeastS()) {
            clearInvocations(mBpfStatsMap);
        } else {
            clearInvocations(mNetd);
        }
    }

    private <T> T verifyWithOrder(@Nullable InOrder inOrder, @NonNull T t) {
        return verifyWithOrder(inOrder, t, times(1));
    }

    private <T> T verifyWithOrder(@Nullable InOrder inOrder, @NonNull T t, VerificationMode mode) {
        if (inOrder != null) {
            return inOrder.verify(t, mode);
        } else {
            return verify(t, mode);
        }
    }

    private void verifyTetherOffloadGetStats() throws Exception {
        if (mDeps.isAtLeastS()) {
            verify(mBpfStatsMap).forEach(any());
        } else {
            verify(mNetd).tetherOffloadGetStats();
        }
    }

    private void verifyNeverTetherOffloadGetStats() throws Exception {
        if (mDeps.isAtLeastS()) {
            verify(mBpfStatsMap, never()).forEach(any());
        } else {
            verify(mNetd, never()).tetherOffloadGetStats();
        }
    }

    private void verifyStartUpstreamIpv6Forwarding(@Nullable InOrder inOrder, int upstreamIfindex,
            @NonNull Set<IpPrefix> upstreamPrefixes) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        ArrayMap<TetherUpstream6Key, Tether6Value> expected = new ArrayMap<>();
        for (IpPrefix upstreamPrefix : upstreamPrefixes) {
            final byte[] prefix64 = prefixToIp64(upstreamPrefix);
            final TetherUpstream6Key key = new TetherUpstream6Key(DOWNSTREAM_IFACE_PARAMS.index,
                    DOWNSTREAM_IFACE_PARAMS.macAddr, prefix64);
            final Tether6Value value = new Tether6Value(upstreamIfindex,
                    MacAddress.ALL_ZEROS_ADDRESS, MacAddress.ALL_ZEROS_ADDRESS, ETH_P_IPV6,
                    NetworkStackConstants.ETHER_MTU);
            expected.put(key, value);
        }
        ArgumentCaptor<TetherUpstream6Key> keyCaptor =
                ArgumentCaptor.forClass(TetherUpstream6Key.class);
        ArgumentCaptor<Tether6Value> valueCaptor =
                ArgumentCaptor.forClass(Tether6Value.class);
        verifyWithOrder(inOrder, mBpfUpstream6Map, times(expected.size())).insertEntry(
                keyCaptor.capture(), valueCaptor.capture());
        List<TetherUpstream6Key> keys = keyCaptor.getAllValues();
        List<Tether6Value> values = valueCaptor.getAllValues();
        ArrayMap<TetherUpstream6Key, Tether6Value> captured = new ArrayMap<>();
        for (int i = 0; i < keys.size(); i++) {
            captured.put(keys.get(i), values.get(i));
        }
        assertEquals(expected, captured);
    }

    private void verifyStopUpstreamIpv6Forwarding(@Nullable InOrder inOrder,
            @NonNull Set<IpPrefix> upstreamPrefixes) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        Set<TetherUpstream6Key> expected = new ArraySet<>();
        for (IpPrefix upstreamPrefix : upstreamPrefixes) {
            final byte[] prefix64 = prefixToIp64(upstreamPrefix);
            final TetherUpstream6Key key = new TetherUpstream6Key(DOWNSTREAM_IFACE_PARAMS.index,
                    DOWNSTREAM_IFACE_PARAMS.macAddr, prefix64);
            expected.add(key);
        }
        ArgumentCaptor<TetherUpstream6Key> keyCaptor =
                ArgumentCaptor.forClass(TetherUpstream6Key.class);
        verifyWithOrder(inOrder, mBpfUpstream6Map, times(expected.size())).deleteEntry(
                keyCaptor.capture());
        assertEquals(expected, new ArraySet(keyCaptor.getAllValues()));
    }

    private void verifyNoUpstreamIpv6ForwardingChange(@Nullable InOrder inOrder) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        if (inOrder != null) {
            inOrder.verify(mBpfUpstream6Map, never()).deleteEntry(any());
            inOrder.verify(mBpfUpstream6Map, never()).insertEntry(any(), any());
            inOrder.verify(mBpfUpstream6Map, never()).updateEntry(any(), any());
        } else {
            verify(mBpfUpstream6Map, never()).deleteEntry(any());
            verify(mBpfUpstream6Map, never()).insertEntry(any(), any());
            verify(mBpfUpstream6Map, never()).updateEntry(any(), any());
        }
    }

    private void verifyAddUpstreamRule(@Nullable InOrder inOrder,
            @NonNull Ipv6UpstreamRule rule) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        verifyWithOrder(inOrder, mBpfUpstream6Map).insertEntry(
                rule.makeTetherUpstream6Key(), rule.makeTether6Value());
    }

    private void verifyAddUpstreamRules(@Nullable InOrder inOrder,
            @NonNull Set<Ipv6UpstreamRule> rules) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        ArrayMap<TetherUpstream6Key, Tether6Value> expected = new ArrayMap<>();
        for (Ipv6UpstreamRule rule : rules) {
            expected.put(rule.makeTetherUpstream6Key(), rule.makeTether6Value());
        }
        ArgumentCaptor<TetherUpstream6Key> keyCaptor =
                ArgumentCaptor.forClass(TetherUpstream6Key.class);
        ArgumentCaptor<Tether6Value> valueCaptor =
                ArgumentCaptor.forClass(Tether6Value.class);
        verifyWithOrder(inOrder, mBpfUpstream6Map, times(expected.size())).insertEntry(
                keyCaptor.capture(), valueCaptor.capture());
        List<TetherUpstream6Key> keys = keyCaptor.getAllValues();
        List<Tether6Value> values = valueCaptor.getAllValues();
        ArrayMap<TetherUpstream6Key, Tether6Value> captured = new ArrayMap<>();
        for (int i = 0; i < keys.size(); i++) {
            captured.put(keys.get(i), values.get(i));
        }
        assertEquals(expected, captured);
    }

    private void verifyAddDownstreamRule(@NonNull Ipv6DownstreamRule rule) throws Exception {
        verifyAddDownstreamRule(null, rule);
    }

    private void verifyAddDownstreamRule(@Nullable InOrder inOrder,
            @NonNull Ipv6DownstreamRule rule) throws Exception {
        if (mDeps.isAtLeastS()) {
            verifyWithOrder(inOrder, mBpfDownstream6Map).updateEntry(
                    rule.makeTetherDownstream6Key(), rule.makeTether6Value());
        } else {
            verifyWithOrder(inOrder, mNetd).tetherOffloadRuleAdd(matches(rule));
        }
    }

    private void verifyNeverAddUpstreamRule() throws Exception {
        if (!mDeps.isAtLeastS()) return;
        verify(mBpfUpstream6Map, never()).insertEntry(any(), any());
    }

    private void verifyNeverAddDownstreamRule() throws Exception {
        if (mDeps.isAtLeastS()) {
            verify(mBpfDownstream6Map, never()).updateEntry(any(), any());
        } else {
            verify(mNetd, never()).tetherOffloadRuleAdd(any());
        }
    }

    private void verifyRemoveUpstreamRule(@Nullable InOrder inOrder,
            @NonNull final Ipv6UpstreamRule rule) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        verifyWithOrder(inOrder, mBpfUpstream6Map).deleteEntry(
                rule.makeTetherUpstream6Key());
    }

    private void verifyRemoveUpstreamRules(@Nullable InOrder inOrder,
            @NonNull Set<Ipv6UpstreamRule> rules) throws Exception {
        if (!mDeps.isAtLeastS()) return;
        List<TetherUpstream6Key> expected = new ArrayList<>();
        for (Ipv6UpstreamRule rule : rules) {
            expected.add(rule.makeTetherUpstream6Key());
        }
        ArgumentCaptor<TetherUpstream6Key> keyCaptor =
                ArgumentCaptor.forClass(TetherUpstream6Key.class);
        verifyWithOrder(inOrder, mBpfUpstream6Map, times(expected.size())).deleteEntry(
                keyCaptor.capture());
        assertSameElements(expected, keyCaptor.getAllValues());
    }

    private void verifyRemoveDownstreamRule(@NonNull final Ipv6DownstreamRule rule)
            throws Exception {
        verifyRemoveDownstreamRule(null, rule);
    }

    private void verifyRemoveDownstreamRule(@Nullable InOrder inOrder,
            @NonNull final Ipv6DownstreamRule rule) throws Exception {
        if (mDeps.isAtLeastS()) {
            verifyWithOrder(inOrder, mBpfDownstream6Map).deleteEntry(
                    rule.makeTetherDownstream6Key());
        } else {
            verifyWithOrder(inOrder, mNetd).tetherOffloadRuleRemove(matches(rule));
        }
    }

    private void verifyNeverRemoveUpstreamRule() throws Exception {
        if (!mDeps.isAtLeastS()) return;
        verify(mBpfUpstream6Map, never()).deleteEntry(any());
    }

    private void verifyNeverRemoveDownstreamRule() throws Exception {
        if (mDeps.isAtLeastS()) {
            verify(mBpfDownstream6Map, never()).deleteEntry(any());
        } else {
            verify(mNetd, never()).tetherOffloadRuleRemove(any());
        }
    }

    private void verifyTetherOffloadSetInterfaceQuota(@Nullable InOrder inOrder, int ifIndex,
            long quotaBytes, boolean isInit) throws Exception {
        if (mDeps.isAtLeastS()) {
            final TetherStatsKey key = new TetherStatsKey(ifIndex);
            verifyWithOrder(inOrder, mBpfStatsMap).getValue(key);
            if (isInit) {
                verifyWithOrder(inOrder, mBpfStatsMap).insertEntry(key, new TetherStatsValue(
                        0L /* rxPackets */, 0L /* rxBytes */, 0L /* rxErrors */,
                        0L /* txPackets */, 0L /* txBytes */, 0L /* txErrors */));
            }
            verifyWithOrder(inOrder, mBpfLimitMap).updateEntry(new TetherLimitKey(ifIndex),
                    new TetherLimitValue(quotaBytes));
        } else {
            verifyWithOrder(inOrder, mNetd).tetherOffloadSetInterfaceQuota(ifIndex, quotaBytes);
        }
    }

    private void verifyNeverTetherOffloadSetInterfaceQuota(@NonNull InOrder inOrder)
            throws Exception {
        if (mDeps.isAtLeastS()) {
            inOrder.verify(mBpfStatsMap, never()).getValue(any());
            inOrder.verify(mBpfStatsMap, never()).insertEntry(any(), any());
            inOrder.verify(mBpfLimitMap, never()).updateEntry(any(), any());
        } else {
            inOrder.verify(mNetd, never()).tetherOffloadSetInterfaceQuota(anyInt(), anyLong());
        }
    }

    private void verifyTetherOffloadGetAndClearStats(@NonNull InOrder inOrder, int ifIndex)
            throws Exception {
        if (mDeps.isAtLeastS()) {
            inOrder.verify(mBpfStatsMap).getValue(new TetherStatsKey(ifIndex));
            inOrder.verify(mBpfStatsMap).deleteEntry(new TetherStatsKey(ifIndex));
            inOrder.verify(mBpfLimitMap).deleteEntry(new TetherLimitKey(ifIndex));
        } else {
            inOrder.verify(mNetd).tetherOffloadGetAndClearStats(ifIndex);
        }
    }

    // S+ and R api minimum tests.
    // The following tests are used to provide minimum checking for the APIs on different flow.
    // The auto merge is not enabled on mainline prod. The code flow R may be verified at the
    // late stage by manual cherry pick. It is risky if the R code flow has broken and be found at
    // the last minute.
    // TODO: remove once presubmit tests on R even the code is submitted on S.
    private void checkTetherOffloadRuleAddAndRemove(boolean usingApiS) throws Exception {
        setupFunctioningNetdInterface();

        // Replace Dependencies#isAtLeastS() for testing R and S+ BPF map apis. Note that |mDeps|
        // must be mocked before calling #makeBpfCoordinator which use |mDeps| to initialize the
        // coordinator.
        doReturn(usingApiS).when(mDeps).isAtLeastS();
        final BpfCoordinator coordinator = makeBpfCoordinator();

        final String mobileIface = "rmnet_data0";
        final Integer mobileIfIndex = 100;
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        // InOrder is required because mBpfStatsMap may be accessed by both
        // BpfCoordinator#tetherOffloadRuleAdd and BpfCoordinator#tetherOffloadGetAndClearStats.
        // The #verifyTetherOffloadGetAndClearStats can't distinguish who has ever called
        // mBpfStatsMap#getValue and get a wrong calling count which counts all.
        final InOrder inOrder = inOrder(mNetd, mBpfUpstream6Map, mBpfDownstream6Map, mBpfLimitMap,
                mBpfStatsMap);
        final Ipv6UpstreamRule upstreamRule = buildTestUpstreamRule(
                mobileIfIndex, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX, DOWNSTREAM_MAC);
        final Ipv6DownstreamRule downstreamRule = buildTestDownstreamRule(
                mobileIfIndex, NEIGH_A, MAC_A);
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, mobileIfIndex, UPSTREAM_PREFIXES);
        verifyTetherOffloadSetInterfaceQuota(inOrder, mobileIfIndex, QUOTA_UNLIMITED,
                true /* isInit */);
        verifyAddUpstreamRule(inOrder, upstreamRule);
        coordinator.addIpv6DownstreamRule(mIpServer, downstreamRule);
        verifyAddDownstreamRule(inOrder, downstreamRule);

        // Removing the last rule on current upstream immediately sends the cleanup stuff to BPF.
        updateStatsEntryForTetherOffloadGetAndClearStats(
                buildTestTetherStatsParcel(mobileIfIndex, 0, 0, 0, 0));
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        verifyRemoveDownstreamRule(inOrder, downstreamRule);
        verifyRemoveUpstreamRule(inOrder, upstreamRule);
        verifyTetherOffloadGetAndClearStats(inOrder, mobileIfIndex);
    }

    // TODO: remove once presubmit tests on R even the code is submitted on S.
    @Test
    public void testTetherOffloadRuleAddAndRemoveSdkR() throws Exception {
        checkTetherOffloadRuleAddAndRemove(false /* R */);
    }

    // TODO: remove once presubmit tests on R even the code is submitted on S.
    @Test
    public void testTetherOffloadRuleAddAndRemoveAtLeastSdkS() throws Exception {
        checkTetherOffloadRuleAddAndRemove(true /* S+ */);
    }

    // TODO: remove once presubmit tests on R even the code is submitted on S.
    private void checkTetherOffloadGetStats(boolean usingApiS) throws Exception {
        setupFunctioningNetdInterface();

        doReturn(usingApiS).when(mDeps).isAtLeastS();
        final BpfCoordinator coordinator = makeBpfCoordinator();
        coordinator.startPolling();

        final String mobileIface = "rmnet_data0";
        final Integer mobileIfIndex = 100;
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        updateStatsEntriesAndWaitForUpdate(new TetherStatsParcel[] {
                buildTestTetherStatsParcel(mobileIfIndex, 1000, 100, 2000, 200)});

        final NetworkStats expectedIfaceStats = new NetworkStats(0L, 1)
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 1000, 100, 2000, 200));

        final NetworkStats expectedUidStats = new NetworkStats(0L, 1)
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 1000, 100, 2000, 200));

        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(expectedIfaceStats, expectedUidStats);
    }

    // TODO: remove once presubmit tests on R even the code is submitted on S.
    @Test
    public void testTetherOffloadGetStatsSdkR() throws Exception {
        checkTetherOffloadGetStats(false /* R */);
    }

    // TODO: remove once presubmit tests on R even the code is submitted on S.
    @Test
    public void testTetherOffloadGetStatsAtLeastSdkS() throws Exception {
        checkTetherOffloadGetStats(true /* S+ */);
    }

    @Test
    public void testGetForwardedStats() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();
        coordinator.startPolling();

        final String wlanIface = "wlan0";
        final Integer wlanIfIndex = 100;
        final String mobileIface = "rmnet_data0";
        final Integer mobileIfIndex = 101;

        // Add interface name to lookup table. In realistic case, the upstream interface name will
        // be added by IpServer when IpServer has received with a new IPv6 upstream update event.
        coordinator.maybeAddUpstreamToLookupTable(wlanIfIndex, wlanIface);
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        // [1] Both interface stats are changed.
        // Setup the tether stats of wlan and mobile interface. Note that move forward the time of
        // the looper to make sure the new tether stats has been updated by polling update thread.
        updateStatsEntriesAndWaitForUpdate(new TetherStatsParcel[] {
                buildTestTetherStatsParcel(wlanIfIndex, 1000, 100, 2000, 200),
                buildTestTetherStatsParcel(mobileIfIndex, 3000, 300, 4000, 400)});

        final NetworkStats expectedIfaceStats = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, wlanIface, 1000, 100, 2000, 200))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 3000, 300, 4000, 400));

        final NetworkStats expectedUidStats = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, wlanIface, 1000, 100, 2000, 200))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 3000, 300, 4000, 400));

        // Force pushing stats update to verify the stats reported.
        // TODO: Perhaps make #expectNotifyStatsUpdated to use test TetherStatsParcel object for
        // verifying the notification.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(expectedIfaceStats, expectedUidStats);

        // [2] Only one interface stats is changed.
        // The tether stats of mobile interface is accumulated and The tether stats of wlan
        // interface is the same.
        updateStatsEntriesAndWaitForUpdate(new TetherStatsParcel[] {
                buildTestTetherStatsParcel(wlanIfIndex, 1000, 100, 2000, 200),
                buildTestTetherStatsParcel(mobileIfIndex, 3010, 320, 4030, 440)});

        final NetworkStats expectedIfaceStatsDiff = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, wlanIface, 0, 0, 0, 0))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 10, 20, 30, 40));

        final NetworkStats expectedUidStatsDiff = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, wlanIface, 0, 0, 0, 0))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 10, 20, 30, 40));

        // Force pushing stats update to verify that only diff of stats is reported.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(expectedIfaceStatsDiff,
                expectedUidStatsDiff);

        // [3] Stop coordinator.
        // Shutdown the coordinator and clear the invocation history, especially the
        // tetherOffloadGetStats() calls.
        coordinator.stopPolling();
        clearStatsInvocations();

        // Verify the polling update thread stopped.
        mTestLooper.moveTimeForward(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        waitForIdle();
        verifyNeverTetherOffloadGetStats();
    }

    @Test
    public void testOnSetAlert() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();
        coordinator.startPolling();

        final String mobileIface = "rmnet_data0";
        final Integer mobileIfIndex = 100;
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        // Verify that set quota to 0 will immediately triggers a callback.
        mTetherStatsProvider.onSetAlert(0);
        waitForIdle();
        mTetherStatsProviderCb.expectNotifyAlertReached();

        // Verify that notifyAlertReached never fired if quota is not yet reached.
        updateStatsEntry(buildTestTetherStatsParcel(mobileIfIndex, 0, 0, 0, 0));
        mTetherStatsProvider.onSetAlert(100);
        mTestLooper.moveTimeForward(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        waitForIdle();
        mTetherStatsProviderCb.assertNoCallback();

        // Verify that notifyAlertReached fired when quota is reached.
        updateStatsEntry(buildTestTetherStatsParcel(mobileIfIndex, 50, 0, 50, 0));
        mTestLooper.moveTimeForward(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        waitForIdle();
        mTetherStatsProviderCb.expectNotifyAlertReached();

        // Verify that set quota with UNLIMITED won't trigger any callback.
        mTetherStatsProvider.onSetAlert(QUOTA_UNLIMITED);
        mTestLooper.moveTimeForward(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        waitForIdle();
        mTetherStatsProviderCb.assertNoCallback();
    }

    /**
     * Custom ArgumentMatcher for TetherOffloadRuleParcel. This is needed because generated stable
     * AIDL classes don't have equals(), so we cannot just use eq(). A custom assert, such as:
     *
     * private void checkFooCalled(StableParcelable p, ...) {
     *     ArgumentCaptor<@FooParam> captor = ArgumentCaptor.forClass(FooParam.class);
     *     verify(mMock).foo(captor.capture());
     *     Foo foo = captor.getValue();
     *     assertFooMatchesExpectations(foo);
     * }
     *
     * almost works, but not quite. This is because if the code under test calls foo() twice, the
     * first call to checkFooCalled() matches both the calls, putting both calls into the captor,
     * and then fails with TooManyActualInvocations. It also makes it harder to use other mockito
     * features such as never(), inOrder(), etc.
     *
     * This approach isn't great because if the match fails, the error message is unhelpful
     * (actual: "android.net.TetherOffloadRuleParcel@8c827b0" or some such), but at least it does
     * work.
     *
     * TODO: consider making the error message more readable by adding a method that catching the
     * AssertionFailedError and throwing a new assertion with more details. See
     * NetworkMonitorTest#verifyNetworkTested.
     *
     * See ConnectivityServiceTest#assertRoutesAdded for an alternative approach which solves the
     * TooManyActualInvocations problem described above by forcing the caller of the custom assert
     * method to specify all expected invocations in one call. This is useful when the stable
     * parcelable class being asserted on has a corresponding Java object (eg., RouteInfo and
     * RouteInfoParcelable), and the caller can just pass in a list of them. It not useful here
     * because there is no such object.
     */
    private static class TetherOffloadRuleParcelMatcher implements
            ArgumentMatcher<TetherOffloadRuleParcel> {
        public final int upstreamIfindex;
        public final int downstreamIfindex;
        public final Inet6Address address;
        public final MacAddress srcMac;
        public final MacAddress dstMac;

        TetherOffloadRuleParcelMatcher(@NonNull Ipv6DownstreamRule rule) {
            upstreamIfindex = rule.upstreamIfindex;
            downstreamIfindex = rule.downstreamIfindex;
            address = rule.address;
            srcMac = rule.srcMac;
            dstMac = rule.dstMac;
        }

        public boolean matches(@NonNull TetherOffloadRuleParcel parcel) {
            return upstreamIfindex == parcel.inputInterfaceIndex
                    && (downstreamIfindex == parcel.outputInterfaceIndex)
                    && Arrays.equals(address.getAddress(), parcel.destination)
                    && (128 == parcel.prefixLength)
                    && Arrays.equals(srcMac.toByteArray(), parcel.srcL2Address)
                    && Arrays.equals(dstMac.toByteArray(), parcel.dstL2Address);
        }

        public String toString() {
            return String.format("TetherOffloadRuleParcelMatcher(%d, %d, %s, %s, %s",
                    upstreamIfindex, downstreamIfindex, address.getHostAddress(), srcMac, dstMac);
        }
    }

    @NonNull
    private TetherOffloadRuleParcel matches(@NonNull Ipv6DownstreamRule rule) {
        return argThat(new TetherOffloadRuleParcelMatcher(rule));
    }

    @NonNull
    private static Ipv6UpstreamRule buildTestUpstreamRule(int upstreamIfindex,
            int downstreamIfindex, @NonNull IpPrefix sourcePrefix, @NonNull MacAddress inDstMac) {
        return new Ipv6UpstreamRule(upstreamIfindex, downstreamIfindex, sourcePrefix, inDstMac,
                MacAddress.ALL_ZEROS_ADDRESS, MacAddress.ALL_ZEROS_ADDRESS);
    }

    @NonNull
    private static Ipv6DownstreamRule buildTestDownstreamRule(
            int upstreamIfindex, @NonNull InetAddress address, @NonNull MacAddress dstMac) {
        return new Ipv6DownstreamRule(upstreamIfindex, DOWNSTREAM_IFINDEX,
                (Inet6Address) address, DOWNSTREAM_MAC, dstMac);
    }

    @Test
    public void testIpv6DownstreamRuleMakeTetherDownstream6Key() throws Exception {
        final int mobileIfIndex = 100;
        final Ipv6DownstreamRule rule = buildTestDownstreamRule(mobileIfIndex, NEIGH_A, MAC_A);

        final TetherDownstream6Key key = rule.makeTetherDownstream6Key();
        assertEquals(mobileIfIndex, key.iif);
        assertEquals(MacAddress.ALL_ZEROS_ADDRESS, key.dstMac);  // rawip upstream
        assertArrayEquals(NEIGH_A.getAddress(), key.neigh6);
        // iif (4) + dstMac(6) + padding(2) + neigh6 (16) = 28.
        assertEquals(28, key.writeToBytes().length);
    }

    @Test
    public void testIpv6DownstreamRuleMakeTether6Value() throws Exception {
        final int mobileIfIndex = 100;
        final Ipv6DownstreamRule rule = buildTestDownstreamRule(mobileIfIndex, NEIGH_A, MAC_A);

        final Tether6Value value = rule.makeTether6Value();
        assertEquals(DOWNSTREAM_IFINDEX, value.oif);
        assertEquals(MAC_A, value.ethDstMac);
        assertEquals(DOWNSTREAM_MAC, value.ethSrcMac);
        assertEquals(ETH_P_IPV6, value.ethProto);
        assertEquals(NetworkStackConstants.ETHER_MTU, value.pmtu);
        // oif (4) + ethDstMac (6) + ethSrcMac (6) + ethProto (2) + pmtu (2) = 20
        assertEquals(20, value.writeToBytes().length);
    }

    @Test
    public void testIpv6UpstreamRuleMakeTetherUpstream6Key() {
        final byte[] bytes = new byte[]{(byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0xab, (byte) 0xcd, (byte) 0xfe, (byte) 0x00};
        final IpPrefix prefix = new IpPrefix("2001:db8:abcd:fe00::/64");
        final Ipv6UpstreamRule rule = buildTestUpstreamRule(UPSTREAM_IFINDEX,
                DOWNSTREAM_IFINDEX, prefix, DOWNSTREAM_MAC);

        final TetherUpstream6Key key = rule.makeTetherUpstream6Key();
        assertEquals(DOWNSTREAM_IFINDEX, key.iif);
        assertEquals(DOWNSTREAM_MAC, key.dstMac);
        assertArrayEquals(bytes, key.src64);
        // iif (4) + dstMac (6) + padding (6) + src64 (8) = 24
        assertEquals(24, key.writeToBytes().length);
    }

    @Test
    public void testIpv6UpstreamRuleMakeTether6Value() {
        final IpPrefix prefix = new IpPrefix("2001:db8:abcd:fe00::/64");
        final Ipv6UpstreamRule rule = buildTestUpstreamRule(UPSTREAM_IFINDEX,
                DOWNSTREAM_IFINDEX, prefix, DOWNSTREAM_MAC);

        final Tether6Value value = rule.makeTether6Value();
        assertEquals(UPSTREAM_IFINDEX, value.oif);
        assertEquals(MAC_NULL, value.ethDstMac);
        assertEquals(MAC_NULL, value.ethSrcMac);
        assertEquals(ETH_P_IPV6, value.ethProto);
        assertEquals(NetworkStackConstants.ETHER_MTU, value.pmtu);
        // oif (4) + ethDstMac (6) + ethSrcMac (6) + ethProto (2) + pmtu (2) = 20
        assertEquals(20, value.writeToBytes().length);
    }

    @Test
    public void testBytesToPrefix() {
        final byte[] bytes = new byte[]{(byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                (byte) 0x00, (byte) 0x00, (byte) 0x12, (byte) 0x34};
        final IpPrefix prefix = new IpPrefix("2001:db8:0:1234::/64");
        assertEquals(prefix, BpfCoordinator.bytesToPrefix(bytes));
    }

    @Test
    public void testSetDataLimit() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        final String mobileIface = "rmnet_data0";
        final int mobileIfIndex = 100;
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        // [1] Default limit.
        // Set the unlimited quota as default if the service has never applied a data limit for a
        // given upstream. Note that the data limit only be applied on an upstream which has rules.
        final Ipv6UpstreamRule rule = buildTestUpstreamRule(
                mobileIfIndex, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX, DOWNSTREAM_MAC);
        final InOrder inOrder = inOrder(mNetd, mBpfUpstream6Map, mBpfLimitMap, mBpfStatsMap);
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, mobileIfIndex, UPSTREAM_PREFIXES);
        verifyTetherOffloadSetInterfaceQuota(inOrder, mobileIfIndex, QUOTA_UNLIMITED,
                true /* isInit */);
        verifyAddUpstreamRule(inOrder, rule);
        inOrder.verifyNoMoreInteractions();

        // [2] Specific limit.
        // Applying the data limit boundary {min, 1gb, max, infinity} on current upstream.
        for (final long quota : new long[] {0, 1048576000, Long.MAX_VALUE, QUOTA_UNLIMITED}) {
            mTetherStatsProvider.onSetLimit(mobileIface, quota);
            waitForIdle();
            verifyTetherOffloadSetInterfaceQuota(inOrder, mobileIfIndex, quota,
                    false /* isInit */);
            inOrder.verifyNoMoreInteractions();
        }

        // [3] Invalid limit.
        // The valid range of quota is 0..max_int64 or -1 (unlimited).
        final long invalidLimit = Long.MIN_VALUE;
        try {
            mTetherStatsProvider.onSetLimit(mobileIface, invalidLimit);
            waitForIdle();
            fail("No exception thrown for invalid limit " + invalidLimit + ".");
        } catch (IllegalArgumentException expected) {
            assertEquals(expected.getMessage(), "invalid quota value " + invalidLimit);
        }
    }

    @Test
    public void testSetDataLimitOnRule6Change() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        final String mobileIface = "rmnet_data0";
        final int mobileIfIndex = 100;
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        // Applying a data limit to the current upstream does not take any immediate action.
        // The data limit could be only set on an upstream which has rules.
        final long limit = 12345;
        final InOrder inOrder = inOrder(mNetd, mBpfUpstream6Map, mBpfLimitMap, mBpfStatsMap);
        mTetherStatsProvider.onSetLimit(mobileIface, limit);
        waitForIdle();
        verifyNeverTetherOffloadSetInterfaceQuota(inOrder);

        // Adding the first rule on current upstream immediately sends the quota to BPF.
        final Ipv6UpstreamRule ruleA = buildTestUpstreamRule(
                mobileIfIndex, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX, DOWNSTREAM_MAC);
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, mobileIfIndex, UPSTREAM_PREFIXES);
        verifyTetherOffloadSetInterfaceQuota(inOrder, mobileIfIndex, limit, true /* isInit */);
        verifyAddUpstreamRule(inOrder, ruleA);
        inOrder.verifyNoMoreInteractions();

        // Adding the second rule on current upstream does not send the quota to BPF.
        final Ipv6UpstreamRule ruleB = buildTestUpstreamRule(
                mobileIfIndex, DOWNSTREAM_IFINDEX2, UPSTREAM_PREFIX, DOWNSTREAM_MAC2);
        coordinator.updateAllIpv6Rules(
                mIpServer2, DOWNSTREAM_IFACE_PARAMS2, mobileIfIndex, UPSTREAM_PREFIXES);
        verifyAddUpstreamRule(inOrder, ruleB);
        verifyNeverTetherOffloadSetInterfaceQuota(inOrder);

        // Removing the second rule on current upstream does not send the quota to BPF.
        coordinator.updateAllIpv6Rules(
                mIpServer2, DOWNSTREAM_IFACE_PARAMS2, NO_UPSTREAM, NO_PREFIXES);
        verifyRemoveUpstreamRule(inOrder, ruleB);
        verifyNeverTetherOffloadSetInterfaceQuota(inOrder);

        // Removing the last rule on current upstream immediately sends the cleanup stuff to BPF.
        updateStatsEntryForTetherOffloadGetAndClearStats(
                buildTestTetherStatsParcel(mobileIfIndex, 0, 0, 0, 0));
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, NO_UPSTREAM, NO_PREFIXES);
        verifyRemoveUpstreamRule(inOrder, ruleA);
        verifyTetherOffloadGetAndClearStats(inOrder, mobileIfIndex);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTetherOffloadRuleUpdateAndClear() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        final String ethIface = "eth1";
        final String mobileIface = "rmnet_data0";
        final Integer ethIfIndex = 100;
        final Integer mobileIfIndex = 101;
        coordinator.maybeAddUpstreamToLookupTable(ethIfIndex, ethIface);
        coordinator.maybeAddUpstreamToLookupTable(mobileIfIndex, mobileIface);

        final InOrder inOrder = inOrder(mNetd, mBpfDownstream6Map, mBpfUpstream6Map, mBpfLimitMap,
                mBpfStatsMap);

        // Before the rule test, here are the additional actions while the rules are changed.
        // - After adding the first rule on a given upstream, the coordinator adds a data limit.
        //   If the service has never applied the data limit, set an unlimited quota as default.
        // - After removing the last rule on a given upstream, the coordinator gets the last stats.
        //   Then, it clears the stats and the limit entry from BPF maps.
        // See tetherOffloadRule{Add, Remove, Clear, Clean}.

        // [1] Adding rules on the upstream Ethernet.
        // Note that the default data limit is applied after the first rule is added.
        final Ipv6UpstreamRule ethernetUpstreamRule = buildTestUpstreamRule(
                ethIfIndex, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX, DOWNSTREAM_MAC);
        final Ipv6DownstreamRule ethernetRuleA = buildTestDownstreamRule(
                ethIfIndex, NEIGH_A, MAC_A);
        final Ipv6DownstreamRule ethernetRuleB = buildTestDownstreamRule(
                ethIfIndex, NEIGH_B, MAC_B);

        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, ethIfIndex, UPSTREAM_PREFIXES);
        verifyTetherOffloadSetInterfaceQuota(inOrder, ethIfIndex, QUOTA_UNLIMITED,
                true /* isInit */);
        verifyAddUpstreamRule(inOrder, ethernetUpstreamRule);
        coordinator.addIpv6DownstreamRule(mIpServer, ethernetRuleA);
        verifyAddDownstreamRule(inOrder, ethernetRuleA);
        coordinator.addIpv6DownstreamRule(mIpServer, ethernetRuleB);
        verifyAddDownstreamRule(inOrder, ethernetRuleB);

        // [2] Update the existing rules from Ethernet to cellular.
        final Ipv6UpstreamRule mobileUpstreamRule = buildTestUpstreamRule(
                mobileIfIndex, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX, DOWNSTREAM_MAC);
        final Ipv6UpstreamRule mobileUpstreamRule2 = buildTestUpstreamRule(
                mobileIfIndex, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX2, DOWNSTREAM_MAC);
        final Ipv6DownstreamRule mobileRuleA = buildTestDownstreamRule(
                mobileIfIndex, NEIGH_A, MAC_A);
        final Ipv6DownstreamRule mobileRuleB = buildTestDownstreamRule(
                mobileIfIndex, NEIGH_B, MAC_B);
        updateStatsEntryForTetherOffloadGetAndClearStats(
                buildTestTetherStatsParcel(ethIfIndex, 10, 20, 30, 40));

        // Update the existing rules for upstream changes. The rules are removed and re-added one
        // by one for updating upstream interface index and prefixes by #tetherOffloadRuleUpdate.
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, mobileIfIndex, UPSTREAM_PREFIXES2);
        verifyRemoveDownstreamRule(inOrder, ethernetRuleA);
        verifyRemoveDownstreamRule(inOrder, ethernetRuleB);
        verifyRemoveUpstreamRule(inOrder, ethernetUpstreamRule);
        verifyTetherOffloadGetAndClearStats(inOrder, ethIfIndex);
        verifyTetherOffloadSetInterfaceQuota(inOrder, mobileIfIndex, QUOTA_UNLIMITED,
                true /* isInit */);
        verifyAddUpstreamRules(inOrder, Set.of(mobileUpstreamRule, mobileUpstreamRule2));
        verifyAddDownstreamRule(inOrder, mobileRuleA);
        verifyAddDownstreamRule(inOrder, mobileRuleB);

        // [3] Clear all rules for a given IpServer.
        updateStatsEntryForTetherOffloadGetAndClearStats(
                buildTestTetherStatsParcel(mobileIfIndex, 50, 60, 70, 80));
        coordinator.clearAllIpv6Rules(mIpServer);
        verifyRemoveDownstreamRule(inOrder, mobileRuleA);
        verifyRemoveDownstreamRule(inOrder, mobileRuleB);
        verifyRemoveUpstreamRules(inOrder, Set.of(mobileUpstreamRule, mobileUpstreamRule2));
        verifyTetherOffloadGetAndClearStats(inOrder, mobileIfIndex);

        // [4] Force pushing stats update to verify that the last diff of stats is reported on all
        // upstreams.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(
                new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, ethIface, 10, 20, 30, 40))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 50, 60, 70, 80)),
                new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, ethIface, 10, 20, 30, 40))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 50, 60, 70, 80)));
    }

    private void checkBpfDisabled() throws Exception {
        // The caller may mock the global dependencies |mDeps| which is used in
        // #makeBpfCoordinator for testing.
        // See #testBpfDisabledbyNoBpfDownstream6Map.
        final BpfCoordinator coordinator = makeBpfCoordinator();
        coordinator.startPolling();

        // The tether stats polling task should not be scheduled.
        mTestLooper.moveTimeForward(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        waitForIdle();
        verifyNeverTetherOffloadGetStats();

        // The interface name lookup table can't be added.
        final String iface = "rmnet_data0";
        final Integer ifIndex = 100;
        coordinator.maybeAddUpstreamToLookupTable(ifIndex, iface);
        assertEquals(0, coordinator.getInterfaceNamesForTesting().size());

        // The rule can't be added.
        final InetAddress neigh = InetAddresses.parseNumericAddress("2001:db8::1");
        final MacAddress mac = MacAddress.fromString("00:00:00:00:00:0a");
        final Ipv6DownstreamRule rule = buildTestDownstreamRule(ifIndex, neigh, mac);
        coordinator.addIpv6DownstreamRule(mIpServer, rule);
        verifyNeverAddDownstreamRule();
        LinkedHashMap<Inet6Address, Ipv6DownstreamRule> rules =
                coordinator.getIpv6DownstreamRulesForTesting().get(mIpServer);
        assertNull(rules);

        // The rule can't be removed. This is not a realistic case because adding rule is not
        // allowed. That implies no rule could be removed, cleared or updated. Verify these
        // cases just in case.
        rules = new LinkedHashMap<Inet6Address, Ipv6DownstreamRule>();
        rules.put(rule.address, rule);
        coordinator.getIpv6DownstreamRulesForTesting().put(mIpServer, rules);
        coordinator.removeIpv6DownstreamRule(mIpServer, rule);
        verifyNeverRemoveDownstreamRule();
        rules = coordinator.getIpv6DownstreamRulesForTesting().get(mIpServer);
        assertNotNull(rules);
        assertEquals(1, rules.size());

        // The rule can't be cleared.
        coordinator.clearAllIpv6Rules(mIpServer);
        verifyNeverRemoveDownstreamRule();
        rules = coordinator.getIpv6DownstreamRulesForTesting().get(mIpServer);
        assertNotNull(rules);
        assertEquals(1, rules.size());

        // The rule can't be updated.
        coordinator.updateAllIpv6Rules(mIpServer, DOWNSTREAM_IFACE_PARAMS,
                rule.upstreamIfindex + 1 /* new */, UPSTREAM_PREFIXES);
        verifyNeverRemoveDownstreamRule();
        verifyNeverAddDownstreamRule();
        rules = coordinator.getIpv6DownstreamRulesForTesting().get(mIpServer);
        assertNotNull(rules);
        assertEquals(1, rules.size());
    }

    @Test
    public void testBpfDisabledbyConfig() throws Exception {
        setupFunctioningNetdInterface();
        when(mTetherConfig.isBpfOffloadEnabled()).thenReturn(false);

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfDisabledbyNoBpfDownstream6Map() throws Exception {
        setupFunctioningNetdInterface();
        doReturn(null).when(mDeps).getBpfDownstream6Map();

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfDisabledbyNoBpfUpstream6Map() throws Exception {
        setupFunctioningNetdInterface();
        doReturn(null).when(mDeps).getBpfUpstream6Map();

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfDisabledbyNoBpfDownstream4Map() throws Exception {
        setupFunctioningNetdInterface();
        doReturn(null).when(mDeps).getBpfDownstream4Map();

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfDisabledbyNoBpfUpstream4Map() throws Exception {
        setupFunctioningNetdInterface();
        doReturn(null).when(mDeps).getBpfUpstream4Map();

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfDisabledbyNoBpfStatsMap() throws Exception {
        setupFunctioningNetdInterface();
        doReturn(null).when(mDeps).getBpfStatsMap();

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfDisabledbyNoBpfLimitMap() throws Exception {
        setupFunctioningNetdInterface();
        doReturn(null).when(mDeps).getBpfLimitMap();

        checkBpfDisabled();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testBpfMapClear() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();
        verify(mBpfDownstream4Map).clear();
        verify(mBpfUpstream4Map).clear();
        verify(mBpfDownstream6Map).clear();
        verify(mBpfUpstream6Map).clear();
        verify(mBpfStatsMap).clear();
        verify(mBpfLimitMap).clear();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAttachDetachBpfProgram() throws Exception {
        setupFunctioningNetdInterface();

        // Static mocking for BpfUtils.
        MockitoSession mockSession = ExtendedMockito.mockitoSession()
                .mockStatic(BpfUtils.class)
                .startMocking();
        try {
            final String intIface1 = "wlan1";
            final String intIface2 = "rndis0";
            final String extIface1 = "rmnet_data0";
            final String extIface2 = "v4-rmnet_data0";
            final String virtualIface = "ipsec0";
            final BpfUtils mockMarkerBpfUtils = staticMockMarker(BpfUtils.class);
            final BpfCoordinator coordinator = makeBpfCoordinator();

            // [1] Add the forwarding pair <wlan1, rmnet_data0>. Expect that attach both wlan1 and
            // rmnet_data0.
            coordinator.maybeAttachProgram(intIface1, extIface1);
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(extIface1, DOWNSTREAM, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(extIface1, DOWNSTREAM, IPV6));
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(intIface1, UPSTREAM, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(intIface1, UPSTREAM, IPV6));
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [2] Add the forwarding pair <wlan1, rmnet_data0> again. Expect no more action.
            coordinator.maybeAttachProgram(intIface1, extIface1);
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [3] Add the forwarding pair <rndis0, rmnet_data0>. Expect that attach rndis0 only.
            coordinator.maybeAttachProgram(intIface2, extIface1);
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(intIface2, UPSTREAM, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(intIface2, UPSTREAM, IPV6));
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [4] Add the forwarding pair <rndis0, v4-rmnet_data0>. Expect that attach
            // v4-rmnet_data0 IPv4 program only.
            coordinator.maybeAttachProgram(intIface2, extIface2);
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(extIface2, DOWNSTREAM, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.attachProgram(extIface2, DOWNSTREAM, IPV6),
                    never());
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [5] Remove the forwarding pair <rndis0, v4-rmnet_data0>. Expect detach
            // v4-rmnet_data0 IPv4 program only.
            coordinator.maybeDetachProgram(intIface2, extIface2);
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(extIface2, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(extIface2, IPV6), never());
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [6] Remove the forwarding pair <rndis0, rmnet_data0>. Expect detach rndis0 only.
            coordinator.maybeDetachProgram(intIface2, extIface1);
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(intIface2, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(intIface2, IPV6));
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [7] Remove the forwarding pair <wlan1, rmnet_data0>. Expect that detach both wlan1
            // and rmnet_data0.
            coordinator.maybeDetachProgram(intIface1, extIface1);
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(extIface1, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(extIface1, IPV6));
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(intIface1, IPV4));
            ExtendedMockito.verify(() -> BpfUtils.detachProgram(intIface1, IPV6));
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

            // [8] Skip attaching if upstream is virtual interface.
            coordinator.maybeAttachProgram(intIface1, virtualIface);
            ExtendedMockito.verify(() ->
                    BpfUtils.attachProgram(anyString(), anyBoolean(), anyBoolean()), never());
            ExtendedMockito.verifyNoMoreInteractions(mockMarkerBpfUtils);
            ExtendedMockito.clearInvocations(mockMarkerBpfUtils);

        } finally {
            mockSession.finishMocking();
        }
    }

    @Test
    public void testTetheringConfigSetPollingInterval() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        // [1] The default polling interval.
        coordinator.startPolling();
        assertEquals(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS, coordinator.getPollingInterval());
        coordinator.stopPolling();

        // [2] Expect the invalid polling interval isn't applied. The valid range of interval is
        // DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS..max_long.
        for (final int interval
                : new int[] {0, 100, DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS - 1}) {
            when(mTetherConfig.getOffloadPollInterval()).thenReturn(interval);
            coordinator.startPolling();
            assertEquals(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS, coordinator.getPollingInterval());
            coordinator.stopPolling();
        }

        // [3] Set a specific polling interval which is larger than default value.
        // Use a large polling interval to avoid flaky test because the time forwarding
        // approximation is used to verify the scheduled time of the polling thread.
        final int pollingInterval = 100_000;
        when(mTetherConfig.getOffloadPollInterval()).thenReturn(pollingInterval);
        coordinator.startPolling();

        // Expect the specific polling interval to be applied.
        assertEquals(pollingInterval, coordinator.getPollingInterval());

        // Start on a new polling time slot.
        mTestLooper.moveTimeForward(pollingInterval);
        waitForIdle();
        clearStatsInvocations();

        // Move time forward to 90% polling interval time. Expect that the polling thread has not
        // scheduled yet.
        mTestLooper.moveTimeForward((long) (pollingInterval * 0.9));
        waitForIdle();
        verifyNeverTetherOffloadGetStats();

        // Move time forward to the remaining 10% polling interval time. Expect that the polling
        // thread has scheduled.
        mTestLooper.moveTimeForward((long) (pollingInterval * 0.1));
        waitForIdle();
        verifyTetherOffloadGetStats();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testStartStopConntrackMonitoring() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        // [1] Don't stop monitoring if it has never started.
        coordinator.stopMonitoring(mIpServer);
        verify(mConntrackMonitor, never()).stop();

        // [2] Start monitoring.
        coordinator.startMonitoring(mIpServer);
        verify(mConntrackMonitor).start();
        clearInvocations(mConntrackMonitor);

        // [3] Stop monitoring.
        coordinator.stopMonitoring(mIpServer);
        verify(mConntrackMonitor).stop();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    @IgnoreAfter(Build.VERSION_CODES.R)
    // Only run this test on Android R.
    public void testStartStopConntrackMonitoring_R() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        coordinator.startMonitoring(mIpServer);
        verify(mConntrackMonitor, never()).start();

        coordinator.stopMonitoring(mIpServer);
        verify(mConntrackMonitor, never()).stop();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testStartStopConntrackMonitoringWithTwoDownstreamIfaces() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        // [1] Start monitoring at the first IpServer adding.
        coordinator.startMonitoring(mIpServer);
        verify(mConntrackMonitor).start();
        clearInvocations(mConntrackMonitor);

        // [2] Don't start monitoring at the second IpServer adding.
        coordinator.startMonitoring(mIpServer2);
        verify(mConntrackMonitor, never()).start();

        // [3] Don't stop monitoring if any downstream interface exists.
        coordinator.stopMonitoring(mIpServer2);
        verify(mConntrackMonitor, never()).stop();

        // [4] Stop monitoring if no downstream exists.
        coordinator.stopMonitoring(mIpServer);
        verify(mConntrackMonitor).stop();
    }

    // Test network topology:
    //
    //         public network (rawip)                 private network
    //                   |                 UE                |
    // +------------+    V    +------------+------------+    V    +------------+
    // |   Sever    +---------+  Upstream  | Downstream +---------+   Client   |
    // +------------+         +------------+------------+         +------------+
    // remote ip              public ip                           private ip
    // 140.112.8.116:443      1.0.0.1:62449                       192.168.80.12:62449
    //

    // Setup upstream interface to BpfCoordinator.
    //
    // @param coordinator BpfCoordinator instance.
    // @param upstreamIfindex upstream interface index. can be the following values.
    //        NO_UPSTREAM: no upstream interface
    //        UPSTREAM_IFINDEX: CELLULAR (raw ip interface)
    //        UPSTREAM_IFINDEX2: WIFI (ethernet interface)
    private void setUpstreamInformationTo(final BpfCoordinator coordinator,
            @Nullable Integer upstreamIfindex) {
        if (upstreamIfindex == NO_UPSTREAM) {
            coordinator.updateUpstreamNetworkState(null);
            return;
        }

        final UpstreamInformation upstreamInfo = UPSTREAM_INFORMATIONS.get(upstreamIfindex);
        if (upstreamInfo == null) {
            fail("Not support upstream interface index " + upstreamIfindex);
        }

        // Needed because BpfCoordinator#addUpstreamIfindexToMap queries interface parameter for
        // interface index.
        doReturn(upstreamInfo.interfaceParams).when(mDeps).getInterfaceParams(
                upstreamInfo.interfaceParams.name);
        coordinator.maybeAddUpstreamToLookupTable(upstreamInfo.interfaceParams.index,
                upstreamInfo.interfaceParams.name);

        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(upstreamInfo.interfaceParams.name);
        lp.addLinkAddress(new LinkAddress(upstreamInfo.address, 32 /* prefix length */));
        lp.setMtu(mMtu);
        final NetworkCapabilities capabilities = new NetworkCapabilities()
                .addTransportType(upstreamInfo.transportType);
        coordinator.updateUpstreamNetworkState(new UpstreamNetworkState(lp, capabilities,
                new Network(upstreamInfo.netId)));
    }

    // Setup downstream interface and its client information to BpfCoordinator.
    //
    // @param coordinator BpfCoordinator instance.
    // @param downstreamIfindex downstream interface index. can be the following values.
    //        DOWNSTREAM_IFINDEX: a client information which uses MAC_A is added.
    //        DOWNSTREAM_IFINDEX2: a client information which uses MAC_B is added.
    // TODO: refactor this function once the client switches between each downstream interface.
    private void addDownstreamAndClientInformationTo(final BpfCoordinator coordinator,
            int downstreamIfindex) {
        if (downstreamIfindex != DOWNSTREAM_IFINDEX && downstreamIfindex != DOWNSTREAM_IFINDEX2) {
            fail("Not support downstream interface index " + downstreamIfindex);
        }

        if (downstreamIfindex == DOWNSTREAM_IFINDEX) {
            coordinator.tetherOffloadClientAdd(mIpServer, CLIENT_INFO_A);
        } else {
            coordinator.tetherOffloadClientAdd(mIpServer2, CLIENT_INFO_B);
        }
    }

    private void initBpfCoordinatorForRule4(final BpfCoordinator coordinator) throws Exception {
        setUpstreamInformationTo(coordinator, UPSTREAM_IFINDEX);
        addDownstreamAndClientInformationTo(coordinator, DOWNSTREAM_IFINDEX);
    }

    // TODO: Test the IPv4 and IPv6 exist concurrently.
    // TODO: Test the IPv4 rule delete failed.
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testSetDataLimitOnRule4Change() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();
        initBpfCoordinatorForRule4(coordinator);

        // Applying a data limit to the current upstream does not take any immediate action.
        // The data limit could be only set on an upstream which has rules.
        final long limit = 12345;
        final InOrder inOrder = inOrder(mNetd, mBpfUpstream4Map, mBpfDownstream4Map, mBpfLimitMap,
                mBpfStatsMap);
        mTetherStatsProvider.onSetLimit(UPSTREAM_IFACE, limit);
        waitForIdle();
        verifyNeverTetherOffloadSetInterfaceQuota(inOrder);

        // Build TCP and UDP rules for testing. Note that the values of {TCP, UDP} are the same
        // because the protocol is not an element of the value. Consider using different address
        // or port to make them different for better testing.
        // TODO: Make the values of {TCP, UDP} rules different.
        final Tether4Key expectedUpstream4KeyTcp = new TestUpstream4Key.Builder()
                .setProto(IPPROTO_TCP).build();
        final Tether4Key expectedDownstream4KeyTcp = new TestDownstream4Key.Builder()
                .setProto(IPPROTO_TCP).build();
        final Tether4Value expectedUpstream4ValueTcp = new TestUpstream4Value.Builder().build();
        final Tether4Value expectedDownstream4ValueTcp = new TestDownstream4Value.Builder().build();

        final Tether4Key expectedUpstream4KeyUdp = new TestUpstream4Key.Builder()
                .setProto(IPPROTO_UDP).build();
        final Tether4Key expectedDownstream4KeyUdp = new TestDownstream4Key.Builder()
                .setProto(IPPROTO_UDP).build();
        final Tether4Value expectedUpstream4ValueUdp = new TestUpstream4Value.Builder().build();
        final Tether4Value expectedDownstream4ValueUdp = new TestDownstream4Value.Builder().build();

        // [1] Adding the first rule on current upstream immediately sends the quota.
        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_NEW)
                .setProto(IPPROTO_TCP)
                .build());
        verifyTetherOffloadSetInterfaceQuota(inOrder, UPSTREAM_IFINDEX, limit, true /* isInit */);
        inOrder.verify(mBpfUpstream4Map)
                .insertEntry(eq(expectedUpstream4KeyTcp), eq(expectedUpstream4ValueTcp));
        inOrder.verify(mBpfDownstream4Map)
                .insertEntry(eq(expectedDownstream4KeyTcp), eq(expectedDownstream4ValueTcp));
        inOrder.verifyNoMoreInteractions();

        // [2] Adding the second rule on current upstream does not send the quota.
        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_NEW)
                .setProto(IPPROTO_UDP)
                .build());
        verifyNeverTetherOffloadSetInterfaceQuota(inOrder);
        inOrder.verify(mBpfUpstream4Map)
                .insertEntry(eq(expectedUpstream4KeyUdp), eq(expectedUpstream4ValueUdp));
        inOrder.verify(mBpfDownstream4Map)
                .insertEntry(eq(expectedDownstream4KeyUdp), eq(expectedDownstream4ValueUdp));
        inOrder.verifyNoMoreInteractions();

        // [3] Removing the second rule on current upstream does not send the quota.
        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_DELETE)
                .setProto(IPPROTO_UDP)
                .build());
        verifyNeverTetherOffloadSetInterfaceQuota(inOrder);
        inOrder.verify(mBpfUpstream4Map).deleteEntry(eq(expectedUpstream4KeyUdp));
        inOrder.verify(mBpfDownstream4Map).deleteEntry(eq(expectedDownstream4KeyUdp));
        inOrder.verifyNoMoreInteractions();

        // [4] Removing the last rule on current upstream immediately sends the cleanup stuff.
        updateStatsEntryForTetherOffloadGetAndClearStats(
                buildTestTetherStatsParcel(UPSTREAM_IFINDEX, 0, 0, 0, 0));
        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_DELETE)
                .setProto(IPPROTO_TCP)
                .build());
        inOrder.verify(mBpfUpstream4Map).deleteEntry(eq(expectedUpstream4KeyTcp));
        inOrder.verify(mBpfDownstream4Map).deleteEntry(eq(expectedDownstream4KeyTcp));
        verifyTetherOffloadGetAndClearStats(inOrder, UPSTREAM_IFINDEX);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAddDevMapRule6() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();

        coordinator.maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX, UPSTREAM_IFACE);
        coordinator.updateAllIpv6Rules(
                mIpServer, DOWNSTREAM_IFACE_PARAMS, UPSTREAM_IFINDEX, UPSTREAM_PREFIXES);
        verify(mBpfDevMap).updateEntry(eq(new TetherDevKey(UPSTREAM_IFINDEX)),
                eq(new TetherDevValue(UPSTREAM_IFINDEX)));
        verify(mBpfDevMap).updateEntry(eq(new TetherDevKey(DOWNSTREAM_IFINDEX)),
                eq(new TetherDevValue(DOWNSTREAM_IFINDEX)));
        clearInvocations(mBpfDevMap);

        // Adding the second downstream, only the second downstream ifindex is added to DevMap,
        // the existing upstream ifindex won't be added again.
        coordinator.updateAllIpv6Rules(
                mIpServer2, DOWNSTREAM_IFACE_PARAMS2, UPSTREAM_IFINDEX, UPSTREAM_PREFIXES);
        verify(mBpfDevMap).updateEntry(eq(new TetherDevKey(DOWNSTREAM_IFINDEX2)),
                eq(new TetherDevValue(DOWNSTREAM_IFINDEX2)));
        verify(mBpfDevMap, never()).updateEntry(eq(new TetherDevKey(UPSTREAM_IFINDEX)),
                eq(new TetherDevValue(UPSTREAM_IFINDEX)));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAddDevMapRule4() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();
        initBpfCoordinatorForRule4(coordinator);

        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_NEW)
                .setProto(IPPROTO_TCP)
                .build());
        verify(mBpfDevMap).updateEntry(eq(new TetherDevKey(UPSTREAM_IFINDEX)),
                eq(new TetherDevValue(UPSTREAM_IFINDEX)));
        verify(mBpfDevMap).updateEntry(eq(new TetherDevKey(DOWNSTREAM_IFINDEX)),
                eq(new TetherDevValue(DOWNSTREAM_IFINDEX)));
        clearInvocations(mBpfDevMap);

        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_NEW)
                .setProto(IPPROTO_UDP)
                .build());
        verify(mBpfDevMap, never()).updateEntry(any(), any());
    }

    private void setElapsedRealtimeNanos(long nanoSec) {
        mElapsedRealtimeNanos = nanoSec;
    }

    private void checkRefreshConntrackTimeout(final TestBpfMap<Tether4Key, Tether4Value> bpfMap,
            final Tether4Key tcpKey, final Tether4Value tcpValue, final Tether4Key udpKey,
            final Tether4Value udpValue) throws Exception {
        // Both system elapsed time since boot and the rule last used time are used to measure
        // the rule expiration. In this test, all test rules are fixed the last used time to 0.
        // Set the different testing elapsed time to make the rule to be valid or expired.
        //
        // Timeline:
        // 0                                       60 (seconds)
        // +---+---+---+---+--...--+---+---+---+---+---+- ..
        // | CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS  |
        // +---+---+---+---+--...--+---+---+---+---+---+- ..
        // |<-          valid diff           ->|
        // |<-          expired diff                 ->|
        // ^                                   ^       ^
        // last used time      elapsed time (valid)    elapsed time (expired)
        final long validTime = (CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS - 1) * 1_000_000L;
        final long expiredTime = (CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS + 1) * 1_000_000L;

        // Static mocking for NetlinkUtils.
        MockitoSession mockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NetlinkUtils.class)
                .startMocking();
        try {
            final BpfCoordinator coordinator = makeBpfCoordinator();
            coordinator.startPolling();
            bpfMap.insertEntry(tcpKey, tcpValue);
            bpfMap.insertEntry(udpKey, udpValue);

            // [1] Don't refresh conntrack timeout.
            setElapsedRealtimeNanos(expiredTime);
            mTestLooper.moveTimeForward(CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS);
            waitForIdle();
            ExtendedMockito.verifyNoMoreInteractions(staticMockMarker(NetlinkUtils.class));
            ExtendedMockito.clearInvocations(staticMockMarker(NetlinkUtils.class));

            // [2] Refresh conntrack timeout.
            setElapsedRealtimeNanos(validTime);
            mTestLooper.moveTimeForward(CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS);
            waitForIdle();
            final byte[] expectedNetlinkTcp = ConntrackMessage.newIPv4TimeoutUpdateRequest(
                    IPPROTO_TCP, PRIVATE_ADDR, (int) PRIVATE_PORT, REMOTE_ADDR,
                    (int) REMOTE_PORT, NF_CONNTRACK_TCP_TIMEOUT_ESTABLISHED);
            final byte[] expectedNetlinkUdp = ConntrackMessage.newIPv4TimeoutUpdateRequest(
                    IPPROTO_UDP, PRIVATE_ADDR, (int) PRIVATE_PORT, REMOTE_ADDR,
                    (int) REMOTE_PORT, NF_CONNTRACK_UDP_TIMEOUT_STREAM);
            ExtendedMockito.verify(() -> NetlinkUtils.sendOneShotKernelMessage(
                    eq(NETLINK_NETFILTER), eq(expectedNetlinkTcp)));
            ExtendedMockito.verify(() -> NetlinkUtils.sendOneShotKernelMessage(
                    eq(NETLINK_NETFILTER), eq(expectedNetlinkUdp)));
            ExtendedMockito.verifyNoMoreInteractions(staticMockMarker(NetlinkUtils.class));
            ExtendedMockito.clearInvocations(staticMockMarker(NetlinkUtils.class));

            // [3] Don't refresh conntrack timeout if polling stopped.
            coordinator.stopPolling();
            mTestLooper.moveTimeForward(CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS);
            waitForIdle();
            ExtendedMockito.verifyNoMoreInteractions(staticMockMarker(NetlinkUtils.class));
            ExtendedMockito.clearInvocations(staticMockMarker(NetlinkUtils.class));
        } finally {
            mockSession.finishMocking();
        }
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testRefreshConntrackTimeout_Upstream4Map() throws Exception {
        // TODO: Replace the dependencies BPF map with a non-mocked TestBpfMap object.
        final TestBpfMap<Tether4Key, Tether4Value> bpfUpstream4Map =
                new TestBpfMap<>(Tether4Key.class, Tether4Value.class);
        doReturn(bpfUpstream4Map).when(mDeps).getBpfUpstream4Map();

        final Tether4Key tcpKey = new TestUpstream4Key.Builder().setProto(IPPROTO_TCP).build();
        final Tether4Key udpKey = new TestUpstream4Key.Builder().setProto(IPPROTO_UDP).build();
        final Tether4Value tcpValue = new TestUpstream4Value.Builder().build();
        final Tether4Value udpValue = new TestUpstream4Value.Builder().build();

        checkRefreshConntrackTimeout(bpfUpstream4Map, tcpKey, tcpValue, udpKey, udpValue);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testRefreshConntrackTimeout_Downstream4Map() throws Exception {
        // TODO: Replace the dependencies BPF map with a non-mocked TestBpfMap object.
        final TestBpfMap<Tether4Key, Tether4Value> bpfDownstream4Map =
                new TestBpfMap<>(Tether4Key.class, Tether4Value.class);
        doReturn(bpfDownstream4Map).when(mDeps).getBpfDownstream4Map();

        final Tether4Key tcpKey = new TestDownstream4Key.Builder().setProto(IPPROTO_TCP).build();
        final Tether4Key udpKey = new TestDownstream4Key.Builder().setProto(IPPROTO_UDP).build();
        final Tether4Value tcpValue = new TestDownstream4Value.Builder().build();
        final Tether4Value udpValue = new TestDownstream4Value.Builder().build();

        checkRefreshConntrackTimeout(bpfDownstream4Map, tcpKey, tcpValue, udpKey, udpValue);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testNotAllowOffloadByConntrackMessageDestinationPort() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();
        initBpfCoordinatorForRule4(coordinator);

        final short offloadedPort = 42;
        assertFalse(CollectionUtils.contains(NON_OFFLOADED_UPSTREAM_IPV4_TCP_PORTS,
                offloadedPort));
        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_NEW)
                .setProto(IPPROTO_TCP)
                .setRemotePort(offloadedPort)
                .build());
        verify(mBpfUpstream4Map).insertEntry(any(), any());
        verify(mBpfDownstream4Map).insertEntry(any(), any());
        clearInvocations(mBpfUpstream4Map, mBpfDownstream4Map);

        for (final short port : NON_OFFLOADED_UPSTREAM_IPV4_TCP_PORTS) {
            mConsumer.accept(new TestConntrackEvent.Builder()
                    .setMsgType(IPCTNL_MSG_CT_NEW)
                    .setProto(IPPROTO_TCP)
                    .setRemotePort(port)
                    .build());
            verify(mBpfUpstream4Map, never()).insertEntry(any(), any());
            verify(mBpfDownstream4Map, never()).insertEntry(any(), any());

            mConsumer.accept(new TestConntrackEvent.Builder()
                    .setMsgType(IPCTNL_MSG_CT_DELETE)
                    .setProto(IPPROTO_TCP)
                    .setRemotePort(port)
                    .build());
            verify(mBpfUpstream4Map, never()).deleteEntry(any());
            verify(mBpfDownstream4Map, never()).deleteEntry(any());

            mConsumer.accept(new TestConntrackEvent.Builder()
                    .setMsgType(IPCTNL_MSG_CT_NEW)
                    .setProto(IPPROTO_UDP)
                    .setRemotePort(port)
                    .build());
            verify(mBpfUpstream4Map).insertEntry(any(), any());
            verify(mBpfDownstream4Map).insertEntry(any(), any());
            clearInvocations(mBpfUpstream4Map, mBpfDownstream4Map);

            mConsumer.accept(new TestConntrackEvent.Builder()
                    .setMsgType(IPCTNL_MSG_CT_DELETE)
                    .setProto(IPPROTO_UDP)
                    .setRemotePort(port)
                    .build());
            verify(mBpfUpstream4Map).deleteEntry(any());
            verify(mBpfDownstream4Map).deleteEntry(any());
            clearInvocations(mBpfUpstream4Map, mBpfDownstream4Map);
        }
    }

    // Test network topology:
    //
    //            public network                UE                private network
    //                  |                     /     \                    |
    // +------------+   V  +-------------+             +--------------+  V  +------------+
    // |   Sever    +------+  Upstream   |+------+-----+ Downstream 1 +-----+  Client A  |
    // +------------+      +-------------+|      |     +--------------+     +------------+
    // remote ip            +-------------+      |                          private ip
    // 140.112.8.116:443   public ip             |                          192.168.80.12:62449
    //                     (upstream 1, rawip)   |
    //                     1.0.0.1:62449         |
    //                     1.0.0.1:62450         |     +--------------+     +------------+
    //                            - or -         +-----+ Downstream 2 +-----+  Client B  |
    //                     (upstream 2, ether)         +--------------+     +------------+
    //                                                                      private ip
    //                                                                      192.168.90.12:62450
    //
    // Build two test rule sets which include BPF upstream and downstream rules.
    //
    // Rule set A: a socket connection from client A to remote server via the first upstream
    //             (UPSTREAM_IFINDEX).
    //             192.168.80.12:62449 -> 1.0.0.1:62449 -> 140.112.8.116:443
    // Rule set B: a socket connection from client B to remote server via the first upstream
    //             (UPSTREAM_IFINDEX).
    //             192.168.80.12:62450 -> 1.0.0.1:62450 -> 140.112.8.116:443
    //
    // The second upstream (UPSTREAM_IFINDEX2) is an ethernet interface which is not supported by
    // BPF. Used for testing the rule adding and removing on an unsupported upstream interface.
    //
    private static final Tether4Key UPSTREAM4_RULE_KEY_A = makeUpstream4Key(
            DOWNSTREAM_IFINDEX, DOWNSTREAM_MAC, PRIVATE_ADDR, PRIVATE_PORT);
    private static final Tether4Value UPSTREAM4_RULE_VALUE_A = makeUpstream4Value(PUBLIC_PORT);
    private static final Tether4Key DOWNSTREAM4_RULE_KEY_A = makeDownstream4Key(PUBLIC_PORT);
    private static final Tether4Value DOWNSTREAM4_RULE_VALUE_A = makeDownstream4Value(
            DOWNSTREAM_IFINDEX, MAC_A, DOWNSTREAM_MAC, PRIVATE_ADDR, PRIVATE_PORT);

    private static final Tether4Key UPSTREAM4_RULE_KEY_B = makeUpstream4Key(
            DOWNSTREAM_IFINDEX2, DOWNSTREAM_MAC2, PRIVATE_ADDR2, PRIVATE_PORT2);
    private static final Tether4Value UPSTREAM4_RULE_VALUE_B = makeUpstream4Value(PUBLIC_PORT2);
    private static final Tether4Key DOWNSTREAM4_RULE_KEY_B = makeDownstream4Key(PUBLIC_PORT2);
    private static final Tether4Value DOWNSTREAM4_RULE_VALUE_B = makeDownstream4Value(
            DOWNSTREAM_IFINDEX2, MAC_B, DOWNSTREAM_MAC2, PRIVATE_ADDR2, PRIVATE_PORT2);

    private static final ConntrackEvent CONNTRACK_EVENT_A = makeTestConntrackEvent(
            PUBLIC_PORT, PRIVATE_ADDR, PRIVATE_PORT);

    private static final ConntrackEvent CONNTRACK_EVENT_B = makeTestConntrackEvent(
            PUBLIC_PORT2, PRIVATE_ADDR2, PRIVATE_PORT2);

    @NonNull
    private static Tether4Key makeUpstream4Key(final int downstreamIfindex,
            @NonNull final MacAddress downstreamMac, @NonNull final Inet4Address privateAddr,
            final short privatePort) {
        return new Tether4Key(downstreamIfindex, downstreamMac, (short) IPPROTO_TCP,
            privateAddr.getAddress(), REMOTE_ADDR.getAddress(), privatePort, REMOTE_PORT);
    }

    @NonNull
    private static Tether4Key makeDownstream4Key(final short publicPort) {
        return new Tether4Key(UPSTREAM_IFINDEX, MacAddress.ALL_ZEROS_ADDRESS /* dstMac (rawip) */,
                (short) IPPROTO_TCP, REMOTE_ADDR.getAddress(), PUBLIC_ADDR.getAddress(),
                REMOTE_PORT, publicPort);
    }

    @NonNull
    private static Tether4Value makeUpstream4Value(final short publicPort) {
        return new Tether4Value(UPSTREAM_IFINDEX,
                MacAddress.ALL_ZEROS_ADDRESS /* ethDstMac (rawip) */,
                MacAddress.ALL_ZEROS_ADDRESS /* ethSrcMac (rawip) */, ETH_P_IP,
                NetworkStackConstants.ETHER_MTU, toIpv4MappedAddressBytes(PUBLIC_ADDR),
                toIpv4MappedAddressBytes(REMOTE_ADDR), publicPort, REMOTE_PORT,
                0 /* lastUsed */);
    }

    @NonNull
    private static Tether4Value makeDownstream4Value(final int downstreamIfindex,
            @NonNull final MacAddress clientMac, @NonNull final MacAddress downstreamMac,
            @NonNull final Inet4Address privateAddr, final short privatePort) {
        return new Tether4Value(downstreamIfindex, clientMac, downstreamMac,
                ETH_P_IP, NetworkStackConstants.ETHER_MTU, toIpv4MappedAddressBytes(REMOTE_ADDR),
                toIpv4MappedAddressBytes(privateAddr), REMOTE_PORT, privatePort, 0 /* lastUsed */);
    }

    @NonNull
    private static ConntrackEvent makeTestConntrackEvent(final short publicPort,
                @NonNull final Inet4Address privateAddr, final short privatePort) {
        return new ConntrackEvent(
                (short) (NetlinkConstants.NFNL_SUBSYS_CTNETLINK << 8 | IPCTNL_MSG_CT_NEW),
                new Tuple(new TupleIpv4(privateAddr, REMOTE_ADDR),
                        new TupleProto((byte) IPPROTO_TCP, privatePort, REMOTE_PORT)),
                new Tuple(new TupleIpv4(REMOTE_ADDR, PUBLIC_ADDR),
                        new TupleProto((byte) IPPROTO_TCP, REMOTE_PORT, publicPort)),
                ESTABLISHED_MASK,
                100 /* nonzero, CT_NEW */);
    }

    private static byte[] prefixToIp64(IpPrefix prefix) {
        return Arrays.copyOf(prefix.getRawAddress(), 8);
    }

    void checkRule4ExistInUpstreamDownstreamMap() throws Exception {
        assertEquals(UPSTREAM4_RULE_VALUE_A, mBpfUpstream4Map.getValue(UPSTREAM4_RULE_KEY_A));
        assertEquals(DOWNSTREAM4_RULE_VALUE_A, mBpfDownstream4Map.getValue(
                DOWNSTREAM4_RULE_KEY_A));
        assertEquals(UPSTREAM4_RULE_VALUE_B, mBpfUpstream4Map.getValue(UPSTREAM4_RULE_KEY_B));
        assertEquals(DOWNSTREAM4_RULE_VALUE_B, mBpfDownstream4Map.getValue(
                DOWNSTREAM4_RULE_KEY_B));
    }

    void checkRule4NotExistInUpstreamDownstreamMap() throws Exception {
        assertNull(mBpfUpstream4Map.getValue(UPSTREAM4_RULE_KEY_A));
        assertNull(mBpfDownstream4Map.getValue(DOWNSTREAM4_RULE_KEY_A));
        assertNull(mBpfUpstream4Map.getValue(UPSTREAM4_RULE_KEY_B));
        assertNull(mBpfDownstream4Map.getValue(DOWNSTREAM4_RULE_KEY_B));
    }

    // Both #addDownstreamAndClientInformationTo and #setUpstreamInformationTo need to be called
    // before this function because upstream and downstream information are required to build
    // the rules while conntrack event is received.
    void addAndCheckRule4ForDownstreams() throws Exception {
        // Add rule set A which is on the first downstream and rule set B which is on the second
        // downstream.
        mConsumer.accept(CONNTRACK_EVENT_A);
        mConsumer.accept(CONNTRACK_EVENT_B);

        // Check that both rule set A and B were added.
        checkRule4ExistInUpstreamDownstreamMap();
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testTetherOffloadRule4Clear_RemoveDownstream() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();

        // Initialize upstream and downstream information manually but calling the setup helper
        // #initBpfCoordinatorForRule4 because this test needs to {update, remove} upstream and
        // downstream manually for testing.
        addDownstreamAndClientInformationTo(coordinator, DOWNSTREAM_IFINDEX);
        addDownstreamAndClientInformationTo(coordinator, DOWNSTREAM_IFINDEX2);

        setUpstreamInformationTo(coordinator, UPSTREAM_IFINDEX);
        addAndCheckRule4ForDownstreams();

        // [1] Remove the first downstream. Remove only the rule set A which is on the first
        // downstream.
        coordinator.tetherOffloadClientClear(mIpServer);
        assertNull(mBpfUpstream4Map.getValue(UPSTREAM4_RULE_KEY_A));
        assertNull(mBpfDownstream4Map.getValue(DOWNSTREAM4_RULE_KEY_A));
        assertEquals(UPSTREAM4_RULE_VALUE_B, mBpfUpstream4Map.getValue(
                UPSTREAM4_RULE_KEY_B));
        assertEquals(DOWNSTREAM4_RULE_VALUE_B, mBpfDownstream4Map.getValue(
                DOWNSTREAM4_RULE_KEY_B));

        // Clear client information for the first downstream only.
        assertNull(mTetherClients.get(mIpServer));
        assertNotNull(mTetherClients.get(mIpServer2));

        // [2] Remove the second downstream. Remove the rule set B which is on the second
        // downstream.
        coordinator.tetherOffloadClientClear(mIpServer2);
        assertNull(mBpfUpstream4Map.getValue(UPSTREAM4_RULE_KEY_B));
        assertNull(mBpfDownstream4Map.getValue(DOWNSTREAM4_RULE_KEY_B));

        // Clear client information for the second downstream.
        assertNull(mTetherClients.get(mIpServer2));
    }

    private void assertClientInfoExists(@NonNull IpServer ipServer,
            @NonNull ClientInfo clientInfo) {
        HashMap<Inet4Address, ClientInfo> clients = mTetherClients.get(ipServer);
        assertNotNull(clients);
        assertEquals(clientInfo, clients.get(clientInfo.clientAddress));
    }

    // Although either ClientInfo for a given downstream (IpServer) is not found or a given
    // client address is not found on a given downstream can be treated "ClientInfo not
    // exist", we still want to know the real reason exactly. For example, we don't know the
    // exact reason in the following:
    //   assertNull(clients == null ? clients : clients.get(clientAddress));
    // This helper only verifies the case that the downstream still has at least one client.
    // In other words, ClientInfo for a given IpServer has not been removed yet.
    private void assertClientInfoDoesNotExist(@NonNull IpServer ipServer,
            @NonNull Inet4Address clientAddress) {
        HashMap<Inet4Address, ClientInfo> clients = mTetherClients.get(ipServer);
        assertNotNull(clients);
        assertNull(clients.get(clientAddress));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testTetherOffloadRule4Clear_ChangeOrRemoveUpstream() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();

        // Initialize upstream and downstream information manually but calling the helper
        // #initBpfCoordinatorForRule4 because this test needs to {update, remove} upstream and
        // downstream.
        addDownstreamAndClientInformationTo(coordinator, DOWNSTREAM_IFINDEX);
        addDownstreamAndClientInformationTo(coordinator, DOWNSTREAM_IFINDEX2);

        setUpstreamInformationTo(coordinator, UPSTREAM_IFINDEX);
        addAndCheckRule4ForDownstreams();

        // [1] Update the same upstream state. Nothing happens.
        setUpstreamInformationTo(coordinator, UPSTREAM_IFINDEX);
        checkRule4ExistInUpstreamDownstreamMap();

        // [2] Switch upstream interface from the first upstream (rawip, bpf supported) to
        // the second upstream (ethernet, bpf not supported). Clear all rules.
        setUpstreamInformationTo(coordinator, UPSTREAM_IFINDEX2);
        checkRule4NotExistInUpstreamDownstreamMap();

        // Setup the upstream interface information and the rules for next test.
        setUpstreamInformationTo(coordinator, UPSTREAM_IFINDEX);
        addAndCheckRule4ForDownstreams();

        // [3] Switch upstream from the first upstream (rawip, bpf supported) to no upstream. Clear
        // all rules.
        setUpstreamInformationTo(coordinator, NO_UPSTREAM);
        checkRule4NotExistInUpstreamDownstreamMap();

        // Client information should be not deleted.
        assertClientInfoExists(mIpServer, CLIENT_INFO_A);
        assertClientInfoExists(mIpServer2, CLIENT_INFO_B);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testTetherOffloadClientAddRemove() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();

        // [1] Add client information A and B on on the same downstream.
        final ClientInfo clientA = new ClientInfo(DOWNSTREAM_IFINDEX, DOWNSTREAM_MAC,
                PRIVATE_ADDR, MAC_A);
        final ClientInfo clientB = new ClientInfo(DOWNSTREAM_IFINDEX, DOWNSTREAM_MAC,
                PRIVATE_ADDR2, MAC_B);
        coordinator.tetherOffloadClientAdd(mIpServer, clientA);
        coordinator.tetherOffloadClientAdd(mIpServer, clientB);
        assertClientInfoExists(mIpServer, clientA);
        assertClientInfoExists(mIpServer, clientB);

        // Add the rules for client A and client B.
        final Tether4Key upstream4KeyA = makeUpstream4Key(
                DOWNSTREAM_IFINDEX, DOWNSTREAM_MAC, PRIVATE_ADDR, PRIVATE_PORT);
        final Tether4Value upstream4ValueA = makeUpstream4Value(PUBLIC_PORT);
        final Tether4Key downstream4KeyA = makeDownstream4Key(PUBLIC_PORT);
        final Tether4Value downstream4ValueA = makeDownstream4Value(
                DOWNSTREAM_IFINDEX, MAC_A, DOWNSTREAM_MAC, PRIVATE_ADDR, PRIVATE_PORT);
        final Tether4Key upstream4KeyB = makeUpstream4Key(
                DOWNSTREAM_IFINDEX, DOWNSTREAM_MAC2, PRIVATE_ADDR2, PRIVATE_PORT2);
        final Tether4Value upstream4ValueB = makeUpstream4Value(PUBLIC_PORT2);
        final Tether4Key downstream4KeyB = makeDownstream4Key(PUBLIC_PORT2);
        final Tether4Value downstream4ValueB = makeDownstream4Value(
                DOWNSTREAM_IFINDEX, MAC_B, DOWNSTREAM_MAC2, PRIVATE_ADDR2, PRIVATE_PORT2);

        mBpfUpstream4Map.insertEntry(upstream4KeyA, upstream4ValueA);
        mBpfDownstream4Map.insertEntry(downstream4KeyA, downstream4ValueA);
        mBpfUpstream4Map.insertEntry(upstream4KeyB, upstream4ValueB);
        mBpfDownstream4Map.insertEntry(downstream4KeyB, downstream4ValueB);

        // [2] Remove client information A. Only the rules on client A should be removed and
        // the rules on client B should exist.
        coordinator.tetherOffloadClientRemove(mIpServer, clientA);
        assertClientInfoDoesNotExist(mIpServer, clientA.clientAddress);
        assertClientInfoExists(mIpServer, clientB);
        assertNull(mBpfUpstream4Map.getValue(upstream4KeyA));
        assertNull(mBpfDownstream4Map.getValue(downstream4KeyA));
        assertEquals(upstream4ValueB, mBpfUpstream4Map.getValue(upstream4KeyB));
        assertEquals(downstream4ValueB, mBpfDownstream4Map.getValue(downstream4KeyB));

        // [3] Remove client information B. The rules on client B should be removed.
        // Exactly, ClientInfo for a given IpServer is removed because the last client B
        // has been removed from the downstream. Can't use the helper #assertClientInfoExists
        // to check because the container ClientInfo for a given downstream has been removed.
        // See #assertClientInfoExists.
        coordinator.tetherOffloadClientRemove(mIpServer, clientB);
        assertNull(mTetherClients.get(mIpServer));
        assertNull(mBpfUpstream4Map.getValue(upstream4KeyB));
        assertNull(mBpfDownstream4Map.getValue(downstream4KeyB));
    }

    @Test
    public void testIpv6ForwardingRuleToString() throws Exception {
        final Ipv6DownstreamRule downstreamRule = buildTestDownstreamRule(UPSTREAM_IFINDEX, NEIGH_A,
                MAC_A);
        assertEquals("upstreamIfindex: 1001, downstreamIfindex: 2001, address: 2001:db8:0:1234::1, "
                + "srcMac: 12:34:56:78:90:ab, dstMac: 00:00:00:00:00:0a",
                downstreamRule.toString());
        final Ipv6UpstreamRule upstreamRule = buildTestUpstreamRule(
                UPSTREAM_IFINDEX, DOWNSTREAM_IFINDEX, UPSTREAM_PREFIX, DOWNSTREAM_MAC);
        assertEquals("upstreamIfindex: 1001, downstreamIfindex: 2001, "
                + "sourcePrefix: 2001:db8:0:1234::/64, inDstMac: 12:34:56:78:90:ab, "
                + "outSrcMac: 00:00:00:00:00:00, outDstMac: 00:00:00:00:00:00",
                upstreamRule.toString());
    }

    private void verifyDump(@NonNull final BpfCoordinator coordinator) {
        final StringWriter stringWriter = new StringWriter();
        final IndentingPrintWriter ipw = new IndentingPrintWriter(stringWriter, " ");
        coordinator.dump(ipw);
        assertFalse(stringWriter.toString().isEmpty());
    }

    @Test
    public void testDumpDoesNotCrash() throws Exception {
        // This dump test only used to for improving mainline module test coverage and doesn't
        // really do any meaningful tests.
        // TODO: consider verifying the dump content and separate tests into testDumpXXX().
        final BpfCoordinator coordinator = makeBpfCoordinator();

        // [1] Dump mostly empty content.
        verifyDump(coordinator);

        // [2] Dump mostly non-empty content.
        // Test the following dump function and fill the corresponding content to execute
        // code as more as possible for test coverage.
        // - dumpBpfForwardingRulesIpv4
        //   * mBpfDownstream4Map
        //   * mBpfUpstream4Map
        // - dumpBpfForwardingRulesIpv6
        //   * mBpfDownstream6Map
        //   * mBpfUpstream6Map
        // - dumpStats
        //   * mBpfStatsMap
        // - dumpDevmap
        //   * mBpfDevMap
        // - dumpCounters
        //   * mBpfErrorMap
        // - dumpIpv6ForwardingRulesByDownstream
        //   * mIpv6DownstreamRules

        // dumpBpfForwardingRulesIpv4
        mBpfDownstream4Map.insertEntry(
                new TestDownstream4Key.Builder().build(),
                new TestDownstream4Value.Builder().build());
        mBpfUpstream4Map.insertEntry(
                new TestUpstream4Key.Builder().build(),
                new TestUpstream4Value.Builder().build());

        // dumpBpfForwardingRulesIpv6
        final Ipv6DownstreamRule rule = buildTestDownstreamRule(UPSTREAM_IFINDEX, NEIGH_A, MAC_A);
        mBpfDownstream6Map.insertEntry(rule.makeTetherDownstream6Key(), rule.makeTether6Value());

        final byte[] prefix64 = prefixToIp64(UPSTREAM_PREFIX);
        final TetherUpstream6Key upstream6Key = new TetherUpstream6Key(DOWNSTREAM_IFINDEX,
                DOWNSTREAM_MAC, prefix64);
        final Tether6Value upstream6Value = new Tether6Value(UPSTREAM_IFINDEX,
                MacAddress.ALL_ZEROS_ADDRESS, MacAddress.ALL_ZEROS_ADDRESS,
                ETH_P_IPV6, NetworkStackConstants.ETHER_MTU);
        mBpfUpstream6Map.insertEntry(upstream6Key, upstream6Value);

        // dumpStats
        mBpfStatsMap.insertEntry(
                new TetherStatsKey(UPSTREAM_IFINDEX),
                new TetherStatsValue(
                        0L /* rxPackets */, 0L /* rxBytes */, 0L /* rxErrors */,
                        0L /* txPackets */, 0L /* txBytes */, 0L /* txErrors */));

        // dumpDevmap
        coordinator.maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX, UPSTREAM_IFACE);
        mBpfDevMap.insertEntry(
                new TetherDevKey(UPSTREAM_IFINDEX),
                new TetherDevValue(UPSTREAM_IFINDEX));

        // dumpCounters
        // The error code is defined in packages/modules/Connectivity/bpf_progs/offload.h.
        mBpfErrorMap.insertEntry(
                new S32(0 /* INVALID_IPV4_VERSION */),
                new S32(1000 /* count */));

        // dumpIpv6ForwardingRulesByDownstream
        final HashMap<IpServer, LinkedHashMap<Inet6Address, Ipv6DownstreamRule>>
                ipv6DownstreamRules = coordinator.getIpv6DownstreamRulesForTesting();
        final LinkedHashMap<Inet6Address, Ipv6DownstreamRule> addressRuleMap =
                new LinkedHashMap<>();
        addressRuleMap.put(rule.address, rule);
        ipv6DownstreamRules.put(mIpServer, addressRuleMap);

        verifyDump(coordinator);
    }

    private void verifyAddTetherOffloadRule4Mtu(final int ifaceMtu, final boolean isKernelMtu,
            final int expectedMtu) throws Exception {
        // BpfCoordinator#updateUpstreamNetworkState geta mtu from LinkProperties. If not found,
        // try to get from kernel.
        if (isKernelMtu) {
            // LinkProperties mtu is invalid and kernel mtu is valid.
            mMtu = INVALID_MTU;
            doReturn(ifaceMtu).when(mDeps).getNetworkInterfaceMtu(any());
        } else {
            // LinkProperties mtu is valid and kernel mtu is invalid.
            mMtu = ifaceMtu;
            doReturn(INVALID_MTU).when(mDeps).getNetworkInterfaceMtu(any());
        }

        final BpfCoordinator coordinator = makeBpfCoordinator();
        initBpfCoordinatorForRule4(coordinator);

        final Tether4Key expectedUpstream4KeyTcp = new TestUpstream4Key.Builder()
                .setProto(IPPROTO_TCP)
                .build();
        final Tether4Key expectedDownstream4KeyTcp = new TestDownstream4Key.Builder()
                .setProto(IPPROTO_TCP)
                .build();
        final Tether4Value expectedUpstream4ValueTcp = new TestUpstream4Value.Builder()
                .setPmtu((short) expectedMtu)
                .build();
        final Tether4Value expectedDownstream4ValueTcp = new TestDownstream4Value.Builder()
                .setPmtu((short) expectedMtu)
                .build();

        mConsumer.accept(new TestConntrackEvent.Builder()
                .setMsgType(IPCTNL_MSG_CT_NEW)
                .setProto(IPPROTO_TCP)
                .build());
        verify(mBpfUpstream4Map)
                .insertEntry(eq(expectedUpstream4KeyTcp), eq(expectedUpstream4ValueTcp));
        verify(mBpfDownstream4Map)
                .insertEntry(eq(expectedDownstream4KeyTcp), eq(expectedDownstream4ValueTcp));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAddTetherOffloadRule4LowMtuFromLinkProperties() throws Exception {
        verifyAddTetherOffloadRule4Mtu(
                IPV4_MIN_MTU, false /* isKernelMtu */, IPV4_MIN_MTU /* expectedMtu */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAddTetherOffloadRule4LowMtuFromKernel() throws Exception {
        verifyAddTetherOffloadRule4Mtu(
                IPV4_MIN_MTU, true /* isKernelMtu */, IPV4_MIN_MTU /* expectedMtu */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAddTetherOffloadRule4LessThanIpv4MinMtu() throws Exception {
        verifyAddTetherOffloadRule4Mtu(
                IPV4_MIN_MTU - 1, false /* isKernelMtu */, IPV4_MIN_MTU /* expectedMtu */);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testAddTetherOffloadRule4InvalidMtu() throws Exception {
        verifyAddTetherOffloadRule4Mtu(INVALID_MTU, false /* isKernelMtu */,
                NetworkStackConstants.ETHER_MTU /* expectedMtu */);
    }

    private static LinkProperties buildUpstreamLinkProperties(final String interfaceName,
            boolean withIPv4, boolean withIPv6, boolean with464xlat) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(interfaceName);

        if (withIPv4) {
            // Assign the address no matter what the interface is. It is okay for now because
            // only single upstream is available.
            // TODO: consider to assign address by interface once we need to test two or more
            // BPF supported upstreams or multi upstreams are supported.
            prop.addLinkAddress(new LinkAddress(PUBLIC_ADDR, 24));
        }

        if (withIPv6) {
            // TODO: make this to be constant. Currently, no test who uses this function cares what
            // the upstream IPv6 address is.
            prop.addLinkAddress(new LinkAddress("2001:db8::5175:15ca/64"));
        }

        if (with464xlat) {
            final String clatInterface = "v4-" + interfaceName;
            final LinkProperties stackedLink = new LinkProperties();
            stackedLink.setInterfaceName(clatInterface);
            stackedLink.addLinkAddress(new LinkAddress(XLAT_LOCAL_IPV4ADDR, 24));
            prop.addStackedLink(stackedLink);
            prop.setNat64Prefix(NAT64_IP_PREFIX);
        }

        return prop;
    }

    private void verifyIpv4Upstream(
            @NonNull final HashMap<Inet4Address, Integer> ipv4UpstreamIndices,
            @NonNull final SparseArray<String> interfaceNames) {
        assertEquals(1, ipv4UpstreamIndices.size());
        Integer upstreamIndex = ipv4UpstreamIndices.get(PUBLIC_ADDR);
        assertNotNull(upstreamIndex);
        assertEquals(UPSTREAM_IFINDEX, upstreamIndex.intValue());
        assertEquals(1, interfaceNames.size());
        assertTrue(interfaceNames.contains(UPSTREAM_IFINDEX));
    }

    private void verifyUpdateUpstreamNetworkState()
            throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();
        final HashMap<Inet4Address, Integer> ipv4UpstreamIndices =
                coordinator.getIpv4UpstreamIndicesForTesting();
        assertTrue(ipv4UpstreamIndices.isEmpty());
        final SparseArray<String> interfaceNames =
                coordinator.getInterfaceNamesForTesting();
        assertEquals(0, interfaceNames.size());

        // Verify the following are added or removed after upstream changes.
        // - BpfCoordinator#mIpv4UpstreamIndices (for building IPv4 offload rules)
        // - BpfCoordinator#mInterfaceNames (for updating limit)
        //
        // +-------+-------+-----------------------+
        // | Test  | Up    |       Protocol        |
        // | Case# | stream+-------+-------+-------+
        // |       |       | IPv4  | IPv6  | Xlat  |
        // +-------+-------+-------+-------+-------+
        // |   1   | Cell  |   O   |       |       |
        // +-------+-------+-------+-------+-------+
        // |   2   | Cell  |       |   O   |       |
        // +-------+-------+-------+-------+-------+
        // |   3   | Cell  |   O   |   O   |       |
        // +-------+-------+-------+-------+-------+
        // |   4   |   -   |       |       |       |
        // +-------+-------+-------+-------+-------+
        // |       | Cell  |   O   |       |       |
        // |       +-------+-------+-------+-------+
        // |   5   | Cell  |       |   O   |   O   | <-- doesn't support offload (xlat)
        // |       +-------+-------+-------+-------+
        // |       | Cell  |   O   |       |       |
        // +-------+-------+-------+-------+-------+
        // |   6   | Wifi  |   O   |   O   |       | <-- doesn't support offload (ether ip)
        // +-------+-------+-------+-------+-------+

        // [1] Mobile IPv4 only
        coordinator.maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX, UPSTREAM_IFACE);
        doReturn(UPSTREAM_IFACE_PARAMS).when(mDeps).getInterfaceParams(UPSTREAM_IFACE);
        final UpstreamNetworkState mobileIPv4UpstreamState = new UpstreamNetworkState(
                buildUpstreamLinkProperties(UPSTREAM_IFACE,
                        true /* IPv4 */, false /* IPv6 */, false /* 464xlat */),
                new NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR),
                new Network(TEST_NET_ID));
        coordinator.updateUpstreamNetworkState(mobileIPv4UpstreamState);
        verifyIpv4Upstream(ipv4UpstreamIndices, interfaceNames);

        // [2] Mobile IPv6 only
        final UpstreamNetworkState mobileIPv6UpstreamState = new UpstreamNetworkState(
                buildUpstreamLinkProperties(UPSTREAM_IFACE,
                        false /* IPv4 */, true /* IPv6 */, false /* 464xlat */),
                new NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR),
                new Network(TEST_NET_ID));
        coordinator.updateUpstreamNetworkState(mobileIPv6UpstreamState);
        assertTrue(ipv4UpstreamIndices.isEmpty());
        assertEquals(1, interfaceNames.size());
        assertTrue(interfaceNames.contains(UPSTREAM_IFINDEX));

        // [3] Mobile IPv4 and IPv6
        final UpstreamNetworkState mobileDualStackUpstreamState = new UpstreamNetworkState(
                buildUpstreamLinkProperties(UPSTREAM_IFACE,
                        true /* IPv4 */, true /* IPv6 */, false /* 464xlat */),
                new NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR),
                new Network(TEST_NET_ID));
        coordinator.updateUpstreamNetworkState(mobileDualStackUpstreamState);
        verifyIpv4Upstream(ipv4UpstreamIndices, interfaceNames);

        // [4] Lost upstream
        coordinator.updateUpstreamNetworkState(null);
        assertTrue(ipv4UpstreamIndices.isEmpty());
        assertEquals(1, interfaceNames.size());
        assertTrue(interfaceNames.contains(UPSTREAM_IFINDEX));

        // [5] verify xlat interface
        // Expect that xlat interface information isn't added to mapping.
        doReturn(UPSTREAM_XLAT_IFACE_PARAMS).when(mDeps).getInterfaceParams(
                UPSTREAM_XLAT_IFACE);
        final UpstreamNetworkState mobile464xlatUpstreamState = new UpstreamNetworkState(
                buildUpstreamLinkProperties(UPSTREAM_IFACE,
                        false /* IPv4 */, true /* IPv6 */, true /* 464xlat */),
                new NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR),
                new Network(TEST_NET_ID));

        // Need to add a valid IPv4 upstream to verify that xlat interface doesn't support.
        // Mobile IPv4 only
        coordinator.updateUpstreamNetworkState(mobileIPv4UpstreamState);
        verifyIpv4Upstream(ipv4UpstreamIndices, interfaceNames);

        // Mobile IPv6 and xlat
        // IpServer doesn't add xlat interface mapping via #maybeAddUpstreamToLookupTable on
        // S and T devices.
        coordinator.updateUpstreamNetworkState(mobile464xlatUpstreamState);
        // Upstream IPv4 address mapping is removed because xlat interface is not supported.
        assertTrue(ipv4UpstreamIndices.isEmpty());
        assertEquals(1, interfaceNames.size());
        assertTrue(interfaceNames.contains(UPSTREAM_IFINDEX));

        // Need to add a valid IPv4 upstream to verify that wifi interface doesn't support.
        // Mobile IPv4 only
        coordinator.updateUpstreamNetworkState(mobileIPv4UpstreamState);
        verifyIpv4Upstream(ipv4UpstreamIndices, interfaceNames);

        // [6] Wifi IPv4 and IPv6
        // Expect that upstream index map is cleared because ether ip is not supported.
        coordinator.maybeAddUpstreamToLookupTable(UPSTREAM_IFINDEX2, UPSTREAM_IFACE2);
        doReturn(UPSTREAM_IFACE_PARAMS2).when(mDeps).getInterfaceParams(UPSTREAM_IFACE2);
        final UpstreamNetworkState wifiDualStackUpstreamState = new UpstreamNetworkState(
                buildUpstreamLinkProperties(UPSTREAM_IFACE2,
                        true /* IPv4 */, true /* IPv6 */, false /* 464xlat */),
                new NetworkCapabilities().addTransportType(TRANSPORT_WIFI),
                new Network(TEST_NET_ID2));
        coordinator.updateUpstreamNetworkState(wifiDualStackUpstreamState);
        assertTrue(ipv4UpstreamIndices.isEmpty());
        assertEquals(2, interfaceNames.size());
        assertTrue(interfaceNames.contains(UPSTREAM_IFINDEX));
        assertTrue(interfaceNames.contains(UPSTREAM_IFINDEX2));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testUpdateUpstreamNetworkState() throws Exception {
        verifyUpdateUpstreamNetworkState();
    }

    @NonNull
    private static TetherStatsParcel buildEmptyTetherStatsParcel(int ifIndex) {
        TetherStatsParcel parcel = new TetherStatsParcel();
        parcel.ifIndex = ifIndex;
        return parcel;
    }

    private void resetNetdAndBpfMaps() throws Exception {
        reset(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);
        // When the last rule is removed, tetherOffloadGetAndClearStats will log a WTF (and
        // potentially crash the test) if the stats map is empty.
        when(mNetd.tetherOffloadGetStats()).thenReturn(new TetherStatsParcel[0]);
        when(mNetd.tetherOffloadGetAndClearStats(UPSTREAM_IFINDEX))
                .thenReturn(buildEmptyTetherStatsParcel(UPSTREAM_IFINDEX));
        when(mNetd.tetherOffloadGetAndClearStats(UPSTREAM_IFINDEX2))
                .thenReturn(buildEmptyTetherStatsParcel(UPSTREAM_IFINDEX2));
        // When the last rule is removed, tetherOffloadGetAndClearStats will log a WTF (and
        // potentially crash the test) if the stats map is empty.
        final TetherStatsValue allZeros = new TetherStatsValue(0, 0, 0, 0, 0, 0);
        when(mBpfStatsMap.getValue(new TetherStatsKey(UPSTREAM_IFINDEX))).thenReturn(allZeros);
        when(mBpfStatsMap.getValue(new TetherStatsKey(UPSTREAM_IFINDEX2))).thenReturn(allZeros);
    }

    @Test
    public void addRemoveIpv6ForwardingRules() throws Exception {
        final int myIfindex = DOWNSTREAM_IFINDEX;
        final int notMyIfindex = myIfindex - 1;
        final BpfCoordinator coordinator = makeBpfCoordinator();
        final IpServer ipServer = makeAndStartIpServer(DOWNSTREAM_IFACE, coordinator);

        resetNetdAndBpfMaps();
        verifyNoMoreInteractions(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);

        // TODO: Perhaps verify the interaction of tetherOffloadSetInterfaceQuota and
        // tetherOffloadGetAndClearStats in netd while the rules are changed.

        // Events on other interfaces are ignored.
        recvNewNeigh(notMyIfindex, NEIGH_A, NUD_REACHABLE, MAC_A);
        verifyNoMoreInteractions(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);

        // Events on this interface are received and sent to BpfCoordinator.
        recvNewNeigh(myIfindex, NEIGH_A, NUD_REACHABLE, MAC_A);
        final Ipv6DownstreamRule ruleA = buildTestDownstreamRule(UPSTREAM_IFINDEX, NEIGH_A, MAC_A);
        verifyAddDownstreamRule(ruleA);
        resetNetdAndBpfMaps();

        recvNewNeigh(myIfindex, NEIGH_B, NUD_REACHABLE, MAC_B);
        final Ipv6DownstreamRule ruleB = buildTestDownstreamRule(UPSTREAM_IFINDEX, NEIGH_B, MAC_B);
        verifyAddDownstreamRule(ruleB);
        resetNetdAndBpfMaps();

        // Link-local and multicast neighbors are ignored.
        recvNewNeigh(myIfindex, NEIGH_LL, NUD_REACHABLE, MAC_A);
        verifyNoMoreInteractions(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);
        recvNewNeigh(myIfindex, NEIGH_MC, NUD_REACHABLE, MAC_A);
        verifyNoMoreInteractions(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);

        // A neighbor that is no longer valid causes the rule to be removed.
        // NUD_FAILED events do not have a MAC address.
        recvNewNeigh(myIfindex, NEIGH_A, NUD_FAILED, null);
        final Ipv6DownstreamRule ruleANull = buildTestDownstreamRule(
                UPSTREAM_IFINDEX, NEIGH_A, MAC_NULL);
        verifyRemoveDownstreamRule(ruleANull);
        resetNetdAndBpfMaps();

        // A neighbor that is deleted causes the rule to be removed.
        recvDelNeigh(myIfindex, NEIGH_B, NUD_STALE, MAC_B);
        final Ipv6DownstreamRule ruleBNull = buildTestDownstreamRule(
                UPSTREAM_IFINDEX, NEIGH_B, MAC_NULL);
        verifyRemoveDownstreamRule(ruleBNull);
        resetNetdAndBpfMaps();

        // Upstream interface changes result in updating the rules.
        recvNewNeigh(myIfindex, NEIGH_A, NUD_REACHABLE, MAC_A);
        recvNewNeigh(myIfindex, NEIGH_B, NUD_REACHABLE, MAC_B);
        resetNetdAndBpfMaps();

        InOrder inOrder = inOrder(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(UPSTREAM_IFACE2);
        lp.setLinkAddresses(UPSTREAM_ADDRESSES);
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE2, lp, -1);
        final Ipv6DownstreamRule ruleA2 = buildTestDownstreamRule(
                UPSTREAM_IFINDEX2, NEIGH_A, MAC_A);
        final Ipv6DownstreamRule ruleB2 = buildTestDownstreamRule(
                UPSTREAM_IFINDEX2, NEIGH_B, MAC_B);
        verifyRemoveDownstreamRule(inOrder, ruleA);
        verifyRemoveDownstreamRule(inOrder, ruleB);
        verifyStopUpstreamIpv6Forwarding(inOrder, UPSTREAM_PREFIXES);
        verifyStartUpstreamIpv6Forwarding(inOrder, UPSTREAM_IFINDEX2, UPSTREAM_PREFIXES);
        verifyAddDownstreamRule(inOrder, ruleA2);
        verifyAddDownstreamRule(inOrder, ruleB2);
        verifyNoUpstreamIpv6ForwardingChange(inOrder);
        resetNetdAndBpfMaps();

        // Upstream link addresses change result in updating the rules.
        LinkProperties lp2 = new LinkProperties();
        lp2.setInterfaceName(UPSTREAM_IFACE2);
        lp2.setLinkAddresses(UPSTREAM_ADDRESSES2);
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE2, lp2, -1);
        verifyRemoveDownstreamRule(inOrder, ruleA2);
        verifyRemoveDownstreamRule(inOrder, ruleB2);
        verifyStopUpstreamIpv6Forwarding(inOrder, UPSTREAM_PREFIXES);
        verifyStartUpstreamIpv6Forwarding(inOrder, UPSTREAM_IFINDEX2, UPSTREAM_PREFIXES2);
        verifyAddDownstreamRule(inOrder, ruleA2);
        verifyAddDownstreamRule(inOrder, ruleB2);
        resetNetdAndBpfMaps();

        // When the upstream is lost, rules are removed.
        dispatchTetherConnectionChanged(ipServer, null, null, 0);
        verifyStopUpstreamIpv6Forwarding(inOrder, UPSTREAM_PREFIXES2);
        verifyRemoveDownstreamRule(ruleA2);
        verifyRemoveDownstreamRule(ruleB2);
        // Upstream lost doesn't clear the downstream rules from the maps.
        // Do that here.
        recvDelNeigh(myIfindex, NEIGH_A, NUD_STALE, MAC_A);
        recvDelNeigh(myIfindex, NEIGH_B, NUD_STALE, MAC_B);
        resetNetdAndBpfMaps();

        // If the upstream is IPv4-only, no IPv6 rules are added to BPF map.
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE);
        resetNetdAndBpfMaps();
        recvNewNeigh(myIfindex, NEIGH_A, NUD_REACHABLE, MAC_A);
        verifyNoUpstreamIpv6ForwardingChange(null);
        // Downstream rules are only added to BpfCoordinator but not BPF map.
        verifyNeverAddDownstreamRule();
        verifyNoMoreInteractions(mNetd, mBpfDownstream6Map, mBpfUpstream6Map);

        // Rules can be added again once upstream IPv6 connectivity is available. The existing rules
        // with an upstream of NO_UPSTREAM are reapplied.
        lp.setInterfaceName(UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE, lp, -1);
        verifyStartUpstreamIpv6Forwarding(null, UPSTREAM_IFINDEX, UPSTREAM_PREFIXES);
        verifyAddDownstreamRule(ruleA);
        recvNewNeigh(myIfindex, NEIGH_B, NUD_REACHABLE, MAC_B);
        verifyAddDownstreamRule(ruleB);

        // If upstream IPv6 connectivity is lost, rules are removed.
        resetNetdAndBpfMaps();
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE, null, 0);
        verifyRemoveDownstreamRule(ruleA);
        verifyRemoveDownstreamRule(ruleB);
        verifyStopUpstreamIpv6Forwarding(null, UPSTREAM_PREFIXES);

        // When upstream IPv6 connectivity comes back, upstream rules are added and downstream rules
        // are reapplied.
        lp.setInterfaceName(UPSTREAM_IFACE);
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE, lp, -1);
        verifyStartUpstreamIpv6Forwarding(null, UPSTREAM_IFINDEX, UPSTREAM_PREFIXES);
        verifyAddDownstreamRule(ruleA);
        verifyAddDownstreamRule(ruleB);
        resetNetdAndBpfMaps();

        // When the downstream interface goes down, rules are removed.
        ipServer.stop();
        mTestLooper.dispatchAll();
        verifyStopUpstreamIpv6Forwarding(null, UPSTREAM_PREFIXES);
        verifyRemoveDownstreamRule(ruleA);
        verifyRemoveDownstreamRule(ruleB);
        verify(mIpNeighborMonitor).stop();
        resetNetdAndBpfMaps();
    }

    @Test
    public void enableDisableUsingBpfOffload() throws Exception {
        final int myIfindex = DOWNSTREAM_IFINDEX;

        // Expect that rules can be only added/removed when the BPF offload config is enabled.
        // Note that the BPF offload disabled case is not a realistic test case. Because IP
        // neighbor monitor doesn't start if BPF offload is disabled, there should have no
        // neighbor event listening. This is used for testing the protection check just in case.
        // TODO: Perhaps remove the BPF offload disabled case test once this check isn't needed
        // anymore.

        // [1] Enable BPF offload.
        // A neighbor that is added or deleted causes the rule to be added or removed.
        final BpfCoordinator coordinator = makeBpfCoordinator();
        final IpServer ipServer = makeAndStartIpServer(DOWNSTREAM_IFACE, coordinator);
        resetNetdAndBpfMaps();

        recvNewNeigh(myIfindex, NEIGH_A, NUD_REACHABLE, MAC_A);
        final Ipv6DownstreamRule rule = buildTestDownstreamRule(UPSTREAM_IFINDEX, NEIGH_A, MAC_A);
        verifyAddDownstreamRule(rule);
        resetNetdAndBpfMaps();

        recvDelNeigh(myIfindex, NEIGH_A, NUD_STALE, MAC_A);
        final Ipv6DownstreamRule ruleNull = buildTestDownstreamRule(
                UPSTREAM_IFINDEX, NEIGH_A, MAC_NULL);
        verifyRemoveDownstreamRule(ruleNull);
        resetNetdAndBpfMaps();

        // Upstream IPv6 connectivity change causes upstream rules change.
        LinkProperties lp2 = new LinkProperties();
        lp2.setInterfaceName(UPSTREAM_IFACE2);
        lp2.setLinkAddresses(UPSTREAM_ADDRESSES2);
        dispatchTetherConnectionChanged(ipServer, UPSTREAM_IFACE2, lp2, 0);
        verifyStartUpstreamIpv6Forwarding(null, UPSTREAM_IFINDEX2, UPSTREAM_PREFIXES2);
        resetNetdAndBpfMaps();

        // [2] Disable BPF offload.
        // A neighbor that is added or deleted doesn’t cause the rule to be added or removed.
        when(mTetherConfig.isBpfOffloadEnabled()).thenReturn(false);
        final BpfCoordinator coordinator2 = makeBpfCoordinator();
        final IpServer ipServer2 = makeAndStartIpServer(DOWNSTREAM_IFACE, coordinator2);
        verifyNoUpstreamIpv6ForwardingChange(null);
        resetNetdAndBpfMaps();

        recvNewNeigh(myIfindex, NEIGH_A, NUD_REACHABLE, MAC_A);
        verifyNeverAddDownstreamRule();
        resetNetdAndBpfMaps();

        recvDelNeigh(myIfindex, NEIGH_A, NUD_STALE, MAC_A);
        verifyNeverRemoveDownstreamRule();
        resetNetdAndBpfMaps();

        // Upstream IPv6 connectivity change doesn't cause the rule to be added or removed.
        dispatchTetherConnectionChanged(ipServer2, UPSTREAM_IFACE2, lp2, 0);
        verifyNoUpstreamIpv6ForwardingChange(null);
        verifyNeverRemoveDownstreamRule();
        resetNetdAndBpfMaps();
    }

    @Test
    public void doesNotStartIpNeighborMonitorIfBpfOffloadDisabled() throws Exception {
        when(mTetherConfig.isBpfOffloadEnabled()).thenReturn(false);
        final BpfCoordinator coordinator = makeBpfCoordinator();
        final IpServer ipServer = makeAndStartIpServer(DOWNSTREAM_IFACE, coordinator);

        // IP neighbor monitor doesn't start if BPF offload is disabled.
        verify(mIpNeighborMonitor, never()).start();
    }

    @Test
    public void testSkipVirtualNetworkInBpf() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();
        final IpServer ipServer = makeAndStartIpServer(DOWNSTREAM_IFACE, coordinator);
        final LinkProperties v6Only = new LinkProperties();
        v6Only.setInterfaceName(IPSEC_IFACE);
        v6Only.setLinkAddresses(UPSTREAM_ADDRESSES);

        resetNetdAndBpfMaps();
        dispatchTetherConnectionChanged(ipServer, IPSEC_IFACE, v6Only, 0);
        verify(mNetd).tetherAddForward(DOWNSTREAM_IFACE, IPSEC_IFACE);
        verify(mNetd).ipfwdAddInterfaceForward(DOWNSTREAM_IFACE, IPSEC_IFACE);
        verifyNeverAddUpstreamRule();

        recvNewNeigh(DOWNSTREAM_IFINDEX, NEIGH_A, NUD_REACHABLE, MAC_A);
        verifyNeverAddDownstreamRule();
    }

    @Test
    public void addRemoveTetherClient() throws Exception {
        final BpfCoordinator coordinator = makeBpfCoordinator();
        final IpServer ipServer = makeAndStartIpServer(DOWNSTREAM_IFACE, coordinator);
        final int myIfindex = DOWNSTREAM_IFINDEX;
        final int notMyIfindex = myIfindex - 1;

        final InetAddress neighA = InetAddresses.parseNumericAddress("192.168.80.1");
        final InetAddress neighB = InetAddresses.parseNumericAddress("192.168.80.2");
        final InetAddress neighLL = InetAddresses.parseNumericAddress("169.254.0.1");
        final InetAddress neighMC = InetAddresses.parseNumericAddress("224.0.0.1");

        // Events on other interfaces are ignored.
        recvNewNeigh(notMyIfindex, neighA, NUD_REACHABLE, MAC_A);
        assertNull(mTetherClients.get(ipServer));

        // Events on this interface are received and sent to BpfCoordinator.
        recvNewNeigh(myIfindex, neighA, NUD_REACHABLE, MAC_A);
        assertClientInfoExists(ipServer,
                new ClientInfo(myIfindex, DOWNSTREAM_MAC, (Inet4Address) neighA, MAC_A));

        recvNewNeigh(myIfindex, neighB, NUD_REACHABLE, MAC_B);
        assertClientInfoExists(ipServer,
                new ClientInfo(myIfindex, DOWNSTREAM_MAC, (Inet4Address) neighB, MAC_B));

        // Link-local and multicast neighbors are ignored.
        recvNewNeigh(myIfindex, neighLL, NUD_REACHABLE, MAC_A);
        assertClientInfoDoesNotExist(ipServer, (Inet4Address) neighLL);
        recvNewNeigh(myIfindex, neighMC, NUD_REACHABLE, MAC_A);
        assertClientInfoDoesNotExist(ipServer, (Inet4Address) neighMC);

        // A neighbor that is no longer valid causes the client to be removed.
        // NUD_FAILED events do not have a MAC address.
        recvNewNeigh(myIfindex, neighA, NUD_FAILED, null);
        assertClientInfoDoesNotExist(ipServer, (Inet4Address) neighA);

        // A neighbor that is deleted causes the client to be removed.
        recvDelNeigh(myIfindex, neighB, NUD_STALE, MAC_B);
        // When last client information is deleted, IpServer will be removed from mTetherClients
        assertNull(mTetherClients.get(ipServer));
    }
}
