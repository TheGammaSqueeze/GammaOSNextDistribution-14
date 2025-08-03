/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.RouteInfo.RTN_UNICAST;
import static android.net.dhcp.DhcpClient.EXPIRED_LEASE;
import static android.net.dhcp.DhcpPacket.DHCP_BOOTREQUEST;
import static android.net.dhcp.DhcpPacket.DHCP_CLIENT;
import static android.net.dhcp.DhcpPacket.DHCP_IPV6_ONLY_PREFERRED;
import static android.net.dhcp.DhcpPacket.DHCP_MAGIC_COOKIE;
import static android.net.dhcp.DhcpPacket.DHCP_SERVER;
import static android.net.dhcp.DhcpPacket.ENCAP_L2;
import static android.net.dhcp.DhcpPacket.INADDR_BROADCAST;
import static android.net.dhcp.DhcpPacket.INFINITE_LEASE;
import static android.net.dhcp.DhcpPacket.MIN_V6ONLY_WAIT_MS;
import static android.net.dhcp6.Dhcp6Packet.PrefixDelegation;
import static android.net.ip.IIpClientCallbacks.DTIM_MULTIPLIER_RESET;
import static android.net.ip.IpClient.CONFIG_IPV6_AUTOCONF_TIMEOUT;
import static android.net.ip.IpClient.CONFIG_ACCEPT_RA_MIN_LFT;
import static android.net.ip.IpClient.CONFIG_APF_COUNTER_POLLING_INTERVAL_SECS;
import static android.net.ip.IpClient.DEFAULT_ACCEPT_RA_MIN_LFT;
import static android.net.ip.IpClient.DEFAULT_APF_COUNTER_POLLING_INTERVAL_SECS;
import static android.net.ip.IpClientLinkObserver.CLAT_PREFIX;
import static android.net.ip.IpClientLinkObserver.CONFIG_SOCKET_RECV_BUFSIZE;
import static android.net.ip.IpReachabilityMonitor.NUD_MCAST_RESOLICIT_NUM;
import static android.net.ip.IpReachabilityMonitor.nudEventTypeToInt;
import static android.net.ipmemorystore.Status.SUCCESS;
import static android.system.OsConstants.ETH_P_IPV6;
import static android.system.OsConstants.IFA_F_TEMPORARY;
import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.IPPROTO_IPV6;
import static android.system.OsConstants.IPPROTO_UDP;

import static com.android.net.module.util.Inet4AddressUtils.getBroadcastAddress;
import static com.android.net.module.util.Inet4AddressUtils.getPrefixMaskAsInet4Address;
import static com.android.net.module.util.NetworkStackConstants.ALL_DHCP_RELAY_AGENTS_AND_SERVERS;
import static com.android.net.module.util.NetworkStackConstants.ARP_REPLY;
import static com.android.net.module.util.NetworkStackConstants.ARP_REQUEST;
import static com.android.net.module.util.NetworkStackConstants.DHCP6_CLIENT_PORT;
import static com.android.net.module.util.NetworkStackConstants.DHCP6_SERVER_PORT;
import static com.android.net.module.util.NetworkStackConstants.ETHER_ADDR_LEN;
import static com.android.net.module.util.NetworkStackConstants.ETHER_BROADCAST;
import static com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.ETHER_TYPE_OFFSET;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_SLLA;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_NEIGHBOR_ADVERTISEMENT;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_NEIGHBOR_SOLICITATION;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ROUTER_SOLICITATION;
import static com.android.net.module.util.NetworkStackConstants.IPV4_ADDR_ANY;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ALL_NODES_MULTICAST;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ALL_ROUTERS_MULTICAST;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ANY;
import static com.android.net.module.util.NetworkStackConstants.IPV6_PROTOCOL_OFFSET;
import static com.android.net.module.util.NetworkStackConstants.NEIGHBOR_ADVERTISEMENT_FLAG_OVERRIDE;
import static com.android.net.module.util.NetworkStackConstants.NEIGHBOR_ADVERTISEMENT_FLAG_ROUTER;
import static com.android.net.module.util.NetworkStackConstants.NEIGHBOR_ADVERTISEMENT_FLAG_SOLICITED;
import static com.android.net.module.util.NetworkStackConstants.PIO_FLAG_AUTONOMOUS;
import static com.android.net.module.util.NetworkStackConstants.PIO_FLAG_ON_LINK;
import static com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION;
import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.ParcelUtils.parcelingRoundTrip;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.DhcpResultsParcelable;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.InterfaceConfigurationParcel;
import android.net.IpPrefix;
import android.net.Layer2InformationParcelable;
import android.net.Layer2PacketParcelable;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.NetworkStackIpMemoryStore;
import android.net.RouteInfo;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.Uri;
import android.net.dhcp.DhcpClient;
import android.net.dhcp.DhcpDeclinePacket;
import android.net.dhcp.DhcpDiscoverPacket;
import android.net.dhcp.DhcpPacket;
import android.net.dhcp.DhcpPacket.ParseException;
import android.net.dhcp.DhcpRequestPacket;
import android.net.dhcp6.Dhcp6Client;
import android.net.dhcp6.Dhcp6Packet;
import android.net.dhcp6.Dhcp6Packet.PrefixDelegation;
import android.net.dhcp6.Dhcp6RebindPacket;
import android.net.dhcp6.Dhcp6RenewPacket;
import android.net.dhcp6.Dhcp6RequestPacket;
import android.net.dhcp6.Dhcp6SolicitPacket;
import android.net.ipmemorystore.NetworkAttributes;
import android.net.ipmemorystore.OnNetworkAttributesRetrievedListener;
import android.net.ipmemorystore.Status;
import android.net.networkstack.TestNetworkStackServiceClient;
import android.net.networkstack.aidl.dhcp.DhcpOption;
import android.net.networkstack.aidl.ip.ReachabilityLossInfoParcelable;
import android.net.networkstack.aidl.ip.ReachabilityLossReason;
import android.net.shared.Layer2Information;
import android.net.shared.ProvisioningConfiguration;
import android.net.shared.ProvisioningConfiguration.ScanResultInfo;
import android.net.util.HostnameTransliterator;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.stats.connectivity.NudEventType;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.internal.util.HexDump;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.ArrayTrackRecord;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.Ipv6Utils;
import com.android.net.module.util.PacketBuilder;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.Struct;
import com.android.net.module.util.arp.ArpPacket;
import com.android.net.module.util.ip.IpNeighborMonitor;
import com.android.net.module.util.ip.IpNeighborMonitor.NeighborEventConsumer;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNdOptPref64;
import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.IaPrefixOption;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.net.module.util.structs.LlaOption;
import com.android.net.module.util.structs.PrefixInformationOption;
import com.android.net.module.util.structs.RdnssOption;
import com.android.networkstack.R;
import com.android.networkstack.apishim.CaptivePortalDataShimImpl;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.common.ShimUtils;
import com.android.networkstack.ipmemorystore.IpMemoryStoreService;
import com.android.networkstack.metrics.IpProvisioningMetrics;
import com.android.networkstack.metrics.IpReachabilityMonitorMetrics;
import com.android.networkstack.metrics.NetworkQuirkMetrics;
import com.android.networkstack.packets.NeighborAdvertisement;
import com.android.networkstack.packets.NeighborSolicitation;
import com.android.networkstack.util.NetworkStackUtils;
import com.android.server.NetworkObserver;
import com.android.server.NetworkObserverRegistry;
import com.android.server.NetworkStackService.NetworkStackServiceManager;
import com.android.testutils.CompatUtil;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.HandlerUtils;
import com.android.testutils.TapPacketReader;
import com.android.testutils.TestableNetworkAgent;
import com.android.testutils.TestableNetworkCallback;

import kotlin.Lazy;
import kotlin.LazyKt;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Base class for IpClient tests.
 *
 * Tests in this class can either be run with signature permissions, or with root access.
 */
@RunWith(Parameterized.class)
@SmallTest
public abstract class IpClientIntegrationTestCommon {
    private static final String TAG = IpClientIntegrationTestCommon.class.getSimpleName();
    private static final int DATA_BUFFER_LEN = 4096;
    private static final int PACKET_TIMEOUT_MS = 5_000;
    private static final String TEST_CLUSTER = "some cluster";
    private static final int TEST_LEASE_DURATION_S = 3_600; // 1 hour
    private static final int TEST_IPV6_ONLY_WAIT_S = 1_800; // 30 min
    private static final int TEST_LOWER_IPV6_ONLY_WAIT_S = (int) (MIN_V6ONLY_WAIT_MS / 1000 - 1);
    private static final int TEST_ZERO_IPV6_ONLY_WAIT_S = 0;
    private static final long TEST_MAX_IPV6_ONLY_WAIT_S = 0xffffffffL;
    private static final int TEST_DEVICE_OWNER_APP_UID = 14242;
    private static final String TEST_DEVICE_OWNER_APP_PACKAGE = "com.example.deviceowner";
    protected static final String TEST_L2KEY = "some l2key";

    // TODO: move to NetlinkConstants, NetworkStackConstants, or OsConstants.
    private static final int IFA_F_STABLE_PRIVACY = 0x800;
    // To fix below AndroidLint warning:
    // [InlinedApi] Field requires version 3 of the U Extensions SDK (current min is 0).
    private static final int RTN_UNREACHABLE =
            SdkLevel.isAtLeastT() ? RouteInfo.RTN_UNREACHABLE : 7;

    protected static final long TEST_TIMEOUT_MS = 2_000L;
    private static final long TEST_WAIT_ENOBUFS_TIMEOUT_MS = 30_000L;
    private static final long TEST_WAIT_RENEW_REBIND_RETRANSMIT_MS = 15_000L;
    // To prevent the flakiness about deprecationTime and expirationTime check, +/- 4s tolerance
    // should be enough between the timestamp when the IP provisioning completes successfully and
    // when IpClientLinkObserver sees the RTM_NEWADDR netlink events.
    private static final long TEST_LIFETIME_TOLERANCE_MS = 4_000L;

    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();
    @Rule
    public final TestName mTestNameRule = new TestName();

    // Indicate whether the flag of parsing netlink event is enabled or not. If it's disabled,
    // integration test still covers the old codepath(i.e. using NetworkObserver), otherwise,
    // test goes through the new codepath(i.e. processRtNetlinkxxx).
    @Parameterized.Parameter(0)
    public boolean mIsNetlinkEventParseEnabled;

    @Parameterized.Parameters
    public static Iterable<? extends Object> data() {
        return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    }

    /**
     * Indicates that a test requires signature permissions to run.
     *
     * Such tests can only be run on devices that use known signing keys, so this annotation must be
     * avoided as much as possible. Consider whether the test can be written to use shell and root
     * shell permissions, and run against the NetworkStack AIDL interface (IIpClient) instead.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    private @interface SignatureRequiredTest {
        String reason();
    }

    /**** BEGIN signature required test members ****/
    // Do not use unless the test *really* cannot be written to exercise IIpClient without mocks.
    // Tests using the below members must be annotated with @SignatureRequiredTest (otherwise the
    // members will be null), and can only be run on devices that use known signing keys.
    // The members could technically be moved to the IpClientIntegrationTest subclass together with
    // the tests requiring signature permissions, but this would make it harder to follow tests in
    // multiple classes, and harder to migrate tests between signature required and not required.

    @Mock private Context mContext;
    @Mock private ConnectivityManager mCm;
    @Mock private Resources mResources;
    @Mock private AlarmManager mAlarm;
    @Mock private ContentResolver mContentResolver;
    @Mock private NetworkStackServiceManager mNetworkStackServiceManager;
    @Mock private IpMemoryStoreService mIpMemoryStoreService;
    @Mock private PowerManager.WakeLock mTimeoutWakeLock;
    @Mock protected NetworkStackIpMemoryStore mIpMemoryStore;
    @Mock private NetworkQuirkMetrics.Dependencies mNetworkQuirkMetricsDeps;
    @Mock private IpReachabilityMonitorMetrics mIpReachabilityMonitorMetrics;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private PackageManager mPackageManager;
    @Spy private INetd mNetd;
    private NetworkObserverRegistry mNetworkObserverRegistry;

    protected IpClient mIpc;
    protected Dependencies mDependencies;

    /***** END signature required test members *****/

    protected IIpClientCallbacks mCb;
    private IIpClient mIIpClient;
    private String mIfaceName;
    private HandlerThread mPacketReaderThread;
    private Handler mHandler;
    private TapPacketReader mPacketReader;
    private FileDescriptor mTapFd;
    private byte[] mClientMac;
    private InetAddress mClientIpAddress;
    private TestableNetworkAgent mNetworkAgent;
    private HandlerThread mNetworkAgentThread;

    private boolean mIsSignatureRequiredTest;

    // ReadHeads for various packet streams. Cannot be initialized in @Before because ReadHead is
    // single-thread-only, and AndroidJUnitRunner runs @Before and @Test on different threads.
    // While it looks like these are created only once per test, they are actually created once per
    // test method because JUnit recreates a fresh test class instance before every test method.
    private Lazy<ArrayTrackRecord<byte[]>.ReadHead> mDhcpPacketReadHead =
            LazyKt.lazy(() -> mPacketReader.getReceivedPackets().newReadHead());
    private Lazy<ArrayTrackRecord<byte[]>.ReadHead> mArpPacketReadHead =
            LazyKt.lazy(() -> mPacketReader.getReceivedPackets().newReadHead());
    private Lazy<ArrayTrackRecord<byte[]>.ReadHead> mDhcp6PacketReadHead =
            LazyKt.lazy(() -> mPacketReader.getReceivedPackets().newReadHead());

    // Ethernet header
    private static final int ETH_HEADER_LEN = 14;

    // IP header
    private static final int IPV4_HEADER_LEN = 20;
    private static final int IPV6_HEADER_LEN = 40;
    private static final int IPV4_SRC_ADDR_OFFSET = ETH_HEADER_LEN + 12;
    private static final int IPV4_DST_ADDR_OFFSET = IPV4_SRC_ADDR_OFFSET + 4;

    // UDP header
    private static final int UDP_HEADER_LEN = 8;
    private static final int UDP_HEADER_OFFSET = ETH_HEADER_LEN + IPV4_HEADER_LEN;
    private static final int UDP_SRC_PORT_OFFSET = UDP_HEADER_OFFSET + 0;

    // DHCP header
    private static final int DHCP_HEADER_OFFSET = ETH_HEADER_LEN + IPV4_HEADER_LEN
            + UDP_HEADER_LEN;
    private static final int DHCP_MESSAGE_OP_CODE_OFFSET = DHCP_HEADER_OFFSET + 0;
    private static final int DHCP_TRANSACTION_ID_OFFSET = DHCP_HEADER_OFFSET + 4;
    private static final int DHCP_OPTION_MAGIC_COOKIE_OFFSET = DHCP_HEADER_OFFSET + 236;

    // DHCPv6 header
    private static final int DHCP6_HEADER_OFFSET = ETH_HEADER_LEN + IPV6_HEADER_LEN
            + UDP_HEADER_LEN;

    private static final Inet4Address SERVER_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("192.168.1.100");
    private static final Inet4Address CLIENT_ADDR =
            (Inet4Address) InetAddresses.parseNumericAddress("192.168.1.2");
    private static final Inet4Address CLIENT_ADDR_NEW =
            (Inet4Address) InetAddresses.parseNumericAddress("192.168.1.3");
    private static final Inet4Address INADDR_ANY =
            (Inet4Address) InetAddresses.parseNumericAddress("0.0.0.0");
    private static final int PREFIX_LENGTH = 24;
    private static final Inet4Address NETMASK = getPrefixMaskAsInet4Address(PREFIX_LENGTH);
    private static final Inet4Address BROADCAST_ADDR = getBroadcastAddress(
            SERVER_ADDR, PREFIX_LENGTH);
    private static final String IPV6_LINK_LOCAL_PREFIX = "fe80::/64";
    private static final String IPV4_TEST_SUBNET_PREFIX = "192.168.1.0/24";
    private static final String IPV4_ANY_ADDRESS_PREFIX = "0.0.0.0/0";
    private static final String HOSTNAME = "testhostname";
    private static final String IPV6_OFF_LINK_DNS_SERVER = "2001:4860:4860::64";
    private static final String IPV6_ON_LINK_DNS_SERVER = "2001:db8:1::64";
    private static final int TEST_DEFAULT_MTU = 1500;
    private static final int TEST_MIN_MTU = 1280;
    private static final MacAddress ROUTER_MAC = MacAddress.fromString("00:1A:11:22:33:44");
    private static final byte[] ROUTER_MAC_BYTES = ROUTER_MAC.toByteArray();
    private static final Inet6Address ROUTER_LINK_LOCAL =
                (Inet6Address) InetAddresses.parseNumericAddress("fe80::1");
    private static final byte[] ROUTER_DUID = new byte[] {
            // type: Link-layer address, hardware type: EUI64(27)
            (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x1b,
            // set 7th bit, and copy the first 3 bytes of mac address
            (byte) 0x02, (byte) 0x1A, (byte) 0x11,
            (byte) 0xFF, (byte) 0xFE,
            // copy the last 3 bytes of mac address
            (byte) 0x22, (byte) 0x33, (byte) 0x44,
    };
    private static final String TEST_HOST_NAME = "AOSP on Crosshatch";
    private static final String TEST_HOST_NAME_TRANSLITERATION = "AOSP-on-Crosshatch";
    private static final String TEST_CAPTIVE_PORTAL_URL = "https://example.com/capportapi";
    private static final byte[] TEST_HOTSPOT_OUI = new byte[] {
            (byte) 0x00, (byte) 0x17, (byte) 0xF2
    };
    private static final byte LEGACY_TEST_VENDOR_SPECIFIC_IE_TYPE = 0x11;
    private static final byte TEST_VENDOR_SPECIFIC_IE_TYPE = 0x21;
    private static final int TEST_VENDOR_SPECIFIC_IE_ID = 0xdd;

    private static final String TEST_DEFAULT_SSID = "test_ssid";
    private static final String TEST_DEFAULT_BSSID = "00:11:22:33:44:55";
    private static final String TEST_DHCP_ROAM_SSID = "0001docomo";
    private static final String TEST_DHCP_ROAM_BSSID = "00:4e:35:17:98:55";
    private static final String TEST_DHCP_ROAM_L2KEY = "roaming_l2key";
    private static final String TEST_DHCP_ROAM_CLUSTER = "roaming_cluster";
    private static final byte[] TEST_AP_OUI = new byte[] { 0x00, 0x1A, 0x11 };
    private static final byte[] TEST_OEM_OUI = new byte[] {(byte) 0x00, (byte) 0x17, (byte) 0xc3};
    private static final String TEST_OEM_VENDOR_ID = "vendor-class-identifier";
    private static final byte[] TEST_OEM_USER_CLASS_INFO = new byte[] {
            // Instance of User Class: [0]
            (byte) 0x03, /* UC_Len_0 */ (byte) 0x11, (byte) 0x22, (byte) 0x33,
            // Instance of User Class: [1]
            (byte) 0x03, /* UC_Len_1 */ (byte) 0x44, (byte) 0x55, (byte) 0x66,
    };

    protected class Dependencies extends IpClient.Dependencies {
        private DhcpClient mDhcpClient;
        private Dhcp6Client mDhcp6Client;
        private boolean mIsHostnameConfigurationEnabled;
        private String mHostname;
        private boolean mIsInterfaceRecovered;

        public void setHostnameConfiguration(final boolean enable, final String hostname) {
            mIsHostnameConfigurationEnabled = enable;
            mHostname = hostname;
        }

        // Enable this flag to simulate the interface has been added back after removing
        // on the provisioning start. However, the actual tap interface has been removed,
        // interface parameters query will get null when attempting to restore Interface
        // MTU. Create a new InterfaceParams instance and return instead just for interface
        // toggling test case.
        public void simulateInterfaceRecover() {
            mIsInterfaceRecovered = true;
        }

        @Override
        public InterfaceParams getInterfaceParams(String ifname) {
            return mIsInterfaceRecovered
                    ? new InterfaceParams(ifname, 1 /* index */,
                            MacAddress.fromString("00:11:22:33:44:55"))
                    : super.getInterfaceParams(ifname);
        }

        @Override
        public INetd getNetd(Context context) {
            return mNetd;
        }

        @Override
        public NetworkStackIpMemoryStore getIpMemoryStore(Context context,
                NetworkStackServiceManager nssManager) {
            return mIpMemoryStore;
        }

        @Override
        public DhcpClient makeDhcpClient(Context context, StateMachine controller,
                InterfaceParams ifParams, DhcpClient.Dependencies deps) {
            mDhcpClient = DhcpClient.makeDhcpClient(context, controller, ifParams, deps);
            return mDhcpClient;
        }

        @Override
        public Dhcp6Client makeDhcp6Client(Context context, StateMachine controller,
                InterfaceParams ifParams, Dhcp6Client.Dependencies deps) {
            mDhcp6Client = Dhcp6Client.makeDhcp6Client(context, controller, ifParams, deps);
            return mDhcp6Client;
        }

        @Override
        public IpReachabilityMonitor getIpReachabilityMonitor(Context context,
                InterfaceParams ifParams, Handler h, SharedLog log,
                IpReachabilityMonitor.Callback callback, boolean usingMultinetworkPolicyTracker,
                IpReachabilityMonitor.Dependencies deps, final INetd netd) {
            return new IpReachabilityMonitor(context, ifParams, h, log, callback,
                    usingMultinetworkPolicyTracker, deps, netd);
        }

        @Override
        public boolean isFeatureEnabled(final Context context, final String name) {
            return IpClientIntegrationTestCommon.this.isFeatureEnabled(name);
        }

        @Override
        public boolean isFeatureNotChickenedOut(final Context context, final String name) {
            return IpClientIntegrationTestCommon.this.isFeatureNotChickenedOut(name);
        }

        @Override
        public int getDeviceConfigPropertyInt(String name, int ignoredDefaultValue) {
            // Default is never used because all device config properties must be mocked by test.
            try {
                return Integer.parseInt(getDeviceConfigProperty(name));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Non-mocked device config property " + name);
            }
        }

        @Override
        public Dhcp6Client.Dependencies getDhcp6ClientDependencies() {
            return new Dhcp6Client.Dependencies() {
                @Override
                public int getDeviceConfigPropertyInt(String name, int defaultValue) {
                    return Dependencies.this.getDeviceConfigPropertyInt(name,
                            defaultValue);
                }
            };
        }

        @Override
        public DhcpClient.Dependencies getDhcpClientDependencies(
                NetworkStackIpMemoryStore ipMemoryStore, IpProvisioningMetrics metrics) {
            return new DhcpClient.Dependencies(ipMemoryStore, metrics) {
                @Override
                public boolean isFeatureEnabled(final Context context, final String name) {
                    return Dependencies.this.isFeatureEnabled(context, name);
                }

                @Override
                public boolean isFeatureNotChickenedOut(final Context context, final String name) {
                    return Dependencies.this.isFeatureNotChickenedOut(context, name);
                }

                @Override
                public int getIntDeviceConfig(final String name, int minimumValue,
                        int maximumValue, int defaultValue) {
                    return Dependencies.this.getDeviceConfigPropertyInt(name, defaultValue);
                }

                @Override
                public PowerManager.WakeLock getWakeLock(final PowerManager powerManager) {
                    return mTimeoutWakeLock;
                }

                @Override
                public boolean getSendHostnameOverlaySetting(final Context context) {
                    return mIsHostnameConfigurationEnabled;
                }

                @Override
                public String getDeviceName(final Context context) {
                    return mHostname;
                }
            };
        }

        @Override
        public IpReachabilityMonitor.Dependencies getIpReachabilityMonitorDeps(Context context,
                String name) {
            return new IpReachabilityMonitor.Dependencies() {
                public void acquireWakeLock(long durationMs) {
                    // It doesn't matter for the integration test app on whether the wake lock
                    // is acquired or not.
                    return;
                }

                public IpNeighborMonitor makeIpNeighborMonitor(Handler h, SharedLog log,
                        NeighborEventConsumer cb) {
                    return new IpNeighborMonitor(h, log, cb);
                }

                public boolean isFeatureEnabled(final Context context, final String name) {
                    return Dependencies.this.isFeatureEnabled(context, name);
                }

                public boolean isFeatureNotChickenedOut(final Context context, final String name) {
                    return Dependencies.this.isFeatureNotChickenedOut(context, name);
                }

                public IpReachabilityMonitorMetrics getIpReachabilityMonitorMetrics() {
                    return mIpReachabilityMonitorMetrics;
                }
            };
        }

        @Override
        public NetworkQuirkMetrics getNetworkQuirkMetrics() {
            return new NetworkQuirkMetrics(mNetworkQuirkMetricsDeps);
        }
    }

    @NonNull
    protected abstract IIpClient makeIIpClient(
            @NonNull String ifaceName, @NonNull IIpClientCallbacks cb);

    // In production. features are enabled if the flag is lower than the package version.
    // For testing, we can just use 1 for enabled and -1 for disabled or chickened out.
    static final String FEATURE_ENABLED = "1";
    static final String FEATURE_DISABLED = "-1";

    final void setFeatureEnabled(String feature, boolean enabled) {
        setDeviceConfigProperty(feature, enabled ? FEATURE_ENABLED : FEATURE_DISABLED);
    }

    final void setFeatureChickenedOut(String feature, boolean chickenedOut) {
        setDeviceConfigProperty(feature, chickenedOut ? FEATURE_DISABLED : FEATURE_ENABLED);
    }

    final void setDeviceConfigProperty(String name, int value) {
        setDeviceConfigProperty(name, Integer.toString(value));
    }

    protected abstract void setDeviceConfigProperty(String name, String value);

    protected abstract String getDeviceConfigProperty(String name);

    protected abstract boolean isFeatureEnabled(String name);

    protected abstract boolean isFeatureNotChickenedOut(String name);

    protected abstract boolean useNetworkStackSignature();

    protected abstract NetworkAttributes getStoredNetworkAttributes(String l2Key, long timeout);

    protected abstract void storeNetworkAttributes(String l2Key, NetworkAttributes na);

    protected abstract void assertIpMemoryNeverStoreNetworkAttributes(String l2Key, long timeout);

    protected abstract int readNudSolicitNumInSteadyStateFromResource();

    protected abstract int readNudSolicitNumPostRoamingFromResource();

    protected final boolean testSkipped() {
        if (!useNetworkStackSignature() && !TestNetworkStackServiceClient.isSupported()) {
            fail("Device running root tests doesn't support TestNetworkStackServiceClient.");
        }
        return !useNetworkStackSignature() && mIsSignatureRequiredTest;
    }

    private void setDhcpFeatures(final boolean isRapidCommitEnabled,
            final boolean isDhcpIpConflictDetectEnabled) {
        setFeatureEnabled(NetworkStackUtils.DHCP_RAPID_COMMIT_VERSION, isRapidCommitEnabled);
        setFeatureEnabled(NetworkStackUtils.DHCP_IP_CONFLICT_DETECT_VERSION,
                isDhcpIpConflictDetectEnabled);
    }

    private void setDeviceConfigForMaxDtimMultiplier() {
        setDeviceConfigProperty(IpClient.CONFIG_INITIAL_PROVISIONING_DTIM_DELAY_MS,
                500 /* default value */);
        setDeviceConfigProperty(IpClient.CONFIG_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER,
                IpClient.DEFAULT_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);
        setDeviceConfigProperty(IpClient.CONFIG_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER,
                IpClient.DEFAULT_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        setDeviceConfigProperty(IpClient.CONFIG_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER,
                IpClient.DEFAULT_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        setDeviceConfigProperty(IpClient.CONFIG_DUAL_STACK_MAX_DTIM_MULTIPLIER,
                IpClient.DEFAULT_DUAL_STACK_MAX_DTIM_MULTIPLIER);
        setDeviceConfigProperty(IpClient.CONFIG_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER,
                IpClient.DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER);
    }

    @Before
    public void setUp() throws Exception {
        // Suffix "[0]" or "[1]" is added to the end of test method name after running with
        // Parameterized.class, that's intended behavior, to iterate each test method with the
        // parameterize value. However, Class#getMethod() throws NoSuchMethodException when
        // searching the target test method name due to this change. Just keep the original test
        // method name to fix NoSuchMethodException, and find the correct annotation associated
        // to test method.
        final String testMethodName = mTestNameRule.getMethodName().split("\\[")[0];
        final Method testMethod = IpClientIntegrationTestCommon.class.getMethod(testMethodName);
        mIsSignatureRequiredTest = testMethod.getAnnotation(SignatureRequiredTest.class) != null;
        assumeFalse(testSkipped());

        // Depend on the parameterized value to enable/disable netlink message refactor flag.
        // Make sure both of the old codepath(rely on the INetdUnsolicitedEventListener aidl)
        // and new codepath(parse netlink event from kernel) will be executed.
        //
        // Note this must be called before making IpClient instance since MyNetlinkMontior ctor
        // in IpClientLinkObserver will use mIsNetlinkEventParseEnabled to decide the proper
        // bindGroups, otherwise, the parameterized value got from ArrayMap(integration test) is
        // always false.
        //
        // Set feature kill switch flag with the parameterized value to keep running test cases on
        // both code paths. Once we clean up the old code path (i.e.when the parameterized variable
        // is false), then we can also delete this code.
        setFeatureChickenedOut(NetworkStackUtils.IPCLIENT_PARSE_NETLINK_EVENTS_FORCE_DISABLE,
                !mIsNetlinkEventParseEnabled);

        // Enable DHCPv6 Prefix Delegation.
        setFeatureEnabled(NetworkStackUtils.IPCLIENT_DHCPV6_PREFIX_DELEGATION_VERSION,
                true /* isDhcp6PrefixDelegationEnabled */);

        // Enable populating the IP Link Address lifetime.
        setFeatureEnabled(NetworkStackUtils.IPCLIENT_POPULATE_LINK_ADDRESS_LIFETIME_VERSION,
                true /* enabled */);

        setUpTapInterface();
        // It turns out that Router Solicitation will also be sent out even after the tap interface
        // is brought up, however, we want to wait for RS which is sent due to IPv6 stack is enabled
        // in the test code. The early RS might bring kind of race, for example, the IPv6 stack has
        // not been enabled when test code sees the RS, then kernel will not process RA even if we
        // replies immediately after receiving RS. Always waiting for the first RS show up after
        // interface is brought up helps prevent the race.
        waitForRouterSolicitation();

        mCb = mock(IIpClientCallbacks.class);
        if (useNetworkStackSignature()) {
            setUpMocks();
            setUpIpClient();
            // Enable packet retransmit alarm in DhcpClient.
            enableRealAlarm("DhcpClient." + mIfaceName + ".KICK");
            // Enable alarm for IPv6 autoconf via SLAAC in IpClient.
            enableRealAlarm("IpClient." + mIfaceName + ".EVENT_IPV6_AUTOCONF_TIMEOUT");
            // Enable packet retransmit alarm in Dhcp6Client.
            enableRealAlarm("Dhcp6Client." + mIfaceName + ".KICK");
        }

        mIIpClient = makeIIpClient(mIfaceName, mCb);

        // Enable multicast filtering after creating IpClient instance, make the integration test
        // more realistic.
        mIIpClient.setMulticastFilter(true);
        setDeviceConfigForMaxDtimMultiplier();
        // Set IPv6 autoconf timeout. For signature tests, it has disabled the provisioning delay,
        // use a small timeout value to speed up the test execution; For root tests, we have to
        // wait a bit longer to make sure that we do see the success IPv6 provisioning, otherwise,
        // the global IPv6 address may show up later due to DAD, so we consider that autoconf fails
        // in this case and start DHCPv6 Prefix Delegation then.
        final int timeout = useNetworkStackSignature() ? 500 : (int) TEST_TIMEOUT_MS;
        setDeviceConfigProperty(IpClient.CONFIG_IPV6_AUTOCONF_TIMEOUT, timeout /* default value */);
    }

    protected void setUpMocks() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDependencies = new Dependencies();
        when(mContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(mAlarm);
        when(mContext.getSystemService(ConnectivityManager.class)).thenReturn(mCm);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getInteger(eq(R.integer.config_nud_postroaming_solicit_num))).thenReturn(5);
        when(mResources.getInteger(eq(R.integer.config_nud_postroaming_solicit_interval)))
                 .thenReturn(750);
        when(mResources.getInteger(eq(R.integer.config_nud_steadystate_solicit_num)))
                 .thenReturn(10);
        when(mResources.getInteger(eq(R.integer.config_nud_steadystate_solicit_interval)))
                 .thenReturn(750);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mNetworkStackServiceManager.getIpMemoryStoreService())
                .thenReturn(mIpMemoryStoreService);
        when(mCb.getInterfaceVersion()).thenReturn(IpClient.VERSION_ADDED_REACHABILITY_FAILURE);
        // This mock is required, otherwise, ignoreIPv6ProvisioningLoss variable is always true,
        // and IpReachabilityMonitor#avoidingBadLinks() will always return false as well, that
        // results in the target tested IPv6 off-link DNS server won't be removed from LP and
        // notifyLost won't be invoked, or the wrong code path when receiving RA with 0 router
        // liftime.
        when(mCm.shouldAvoidBadWifi()).thenReturn(true);

        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser()).thenReturn(
                new ComponentName(TEST_DEVICE_OWNER_APP_PACKAGE, "com.example.SomeClass"));
        when(mPackageManager.getPackagesForUid(TEST_DEVICE_OWNER_APP_UID)).thenReturn(
                new String[] { TEST_DEVICE_OWNER_APP_PACKAGE });

        setDeviceConfigProperty(IpClient.CONFIG_MIN_RDNSS_LIFETIME, 67);
        setDeviceConfigProperty(DhcpClient.DHCP_RESTART_CONFIG_DELAY, 10);
        setDeviceConfigProperty(DhcpClient.ARP_FIRST_PROBE_DELAY_MS, 10);
        setDeviceConfigProperty(DhcpClient.ARP_PROBE_MIN_MS, 10);
        setDeviceConfigProperty(DhcpClient.ARP_PROBE_MAX_MS, 20);
        setDeviceConfigProperty(DhcpClient.ARP_FIRST_ANNOUNCE_DELAY_MS, 10);
        setDeviceConfigProperty(DhcpClient.ARP_ANNOUNCE_INTERVAL_MS, 10);

        // Set the initial netlink socket receive buffer size to a minimum of 100KB to ensure test
        // cases are still working, meanwhile in order to easily overflow the receive buffer by
        // sending as few RAs as possible for test case where it's used to verify ENOBUFS.
        setDeviceConfigProperty(CONFIG_SOCKET_RECV_BUFSIZE, 100 * 1024);

        // Set the timeout to wait IPv6 autoconf to complete.
        setDeviceConfigProperty(CONFIG_IPV6_AUTOCONF_TIMEOUT, 500);

        // Set the minimal RA lifetime value, any RA section with liftime below this value will be
        // ignored.
        setDeviceConfigProperty(CONFIG_ACCEPT_RA_MIN_LFT, DEFAULT_ACCEPT_RA_MIN_LFT);

        // Set the polling interval to update APF data snapshot.
        setDeviceConfigProperty(CONFIG_APF_COUNTER_POLLING_INTERVAL_SECS,
                DEFAULT_APF_COUNTER_POLLING_INTERVAL_SECS);
    }

    private void awaitIpClientShutdown() throws Exception {
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onQuit();
    }

    @After
    public void tearDown() throws Exception {
        if (testSkipped()) return;
        if (mNetworkAgent != null) {
            mNetworkAgent.unregister();
        }
        if (mNetworkAgentThread != null) {
            mNetworkAgentThread.quitSafely();
            mNetworkAgentThread.join();
        }
        teardownTapInterface();
        mIIpClient.shutdown();
        awaitIpClientShutdown();
    }

    private void setUpTapInterface() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final TestNetworkInterface iface = runAsShell(MANAGE_TEST_NETWORKS, () -> {
            final TestNetworkManager tnm =
                    inst.getContext().getSystemService(TestNetworkManager.class);
            try {
                return tnm.createTapInterface(true /* carrierUp */, true /* bringUp */,
                        true /* disableIpv6ProvisioningDelay */);
            } catch (NoSuchMethodError e) {
                // createTapInterface(boolean, boolean, boolean) has been introduced since T,
                // use the legancy API if the method is not found on previous platforms.
                return tnm.createTapInterface();
            }
        });
        mIfaceName = iface.getInterfaceName();
        mClientMac = getIfaceMacAddr(mIfaceName).toByteArray();
        mPacketReaderThread = new HandlerThread(
                IpClientIntegrationTestCommon.class.getSimpleName());
        mPacketReaderThread.start();
        mHandler = mPacketReaderThread.getThreadHandler();

        // Detach the FileDescriptor from the ParcelFileDescriptor.
        // Otherwise, the garbage collector might call the ParcelFileDescriptor's finalizer, which
        // closes the FileDescriptor and destroys our tap interface. An alternative would be to
        // make the ParcelFileDescriptor or the TestNetworkInterface a class member so they never
        // go out of scope.
        mTapFd = new FileDescriptor();
        mTapFd.setInt$(iface.getFileDescriptor().detachFd());
        mPacketReader = new TapPacketReader(mHandler, mTapFd, DATA_BUFFER_LEN);
        mHandler.post(() -> mPacketReader.start());
    }

    private TestNetworkInterface setUpClatInterface(@NonNull String baseIface) throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final TestNetworkInterface iface = runAsShell(MANAGE_TEST_NETWORKS, () -> {
            final TestNetworkManager tnm =
                    inst.getContext().getSystemService(TestNetworkManager.class);
            return tnm.createTapInterface(false /* bringUp */, CLAT_PREFIX + baseIface);
        });
        return iface;
    }

    private void teardownTapInterface() throws Exception {
        if (mPacketReader != null) {
            mHandler.post(() -> mPacketReader.stop());  // Also closes the socket
            mTapFd = null;
        }
        if (mPacketReaderThread != null) {
            mPacketReaderThread.quitSafely();
            mPacketReaderThread.join();
        }
    }

    private MacAddress getIfaceMacAddr(String ifaceName) throws IOException {
        // InterfaceParams.getByName requires CAP_NET_ADMIN: read the mac address with the shell
        final String strMacAddr = getOneLineCommandOutput(
                "su root cat /sys/class/net/" + ifaceName + "/address");
        return MacAddress.fromString(strMacAddr);
    }

    private String getOneLineCommandOutput(String cmd) throws IOException {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(cmd);
             BufferedReader reader = new BufferedReader(new FileReader(fd.getFileDescriptor()))) {
            return reader.readLine();
        }
    }

    private void enableRealAlarm(String cmdName) {
        doAnswer((inv) -> {
            final Context context = InstrumentationRegistry.getTargetContext();
            final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
            alarmManager.setExact(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                    inv.getArgument(3), inv.getArgument(4));
            return null;
        }).when(mAlarm).setExact(anyInt(), anyLong(), eq(cmdName), any(OnAlarmListener.class),
                any(Handler.class));
    }

    private IpClient makeIpClient() throws Exception {
        IpClient ipc = new IpClient(mContext, mIfaceName, mCb, mNetworkObserverRegistry,
                mNetworkStackServiceManager, mDependencies);
        // Wait for IpClient to enter its initial state. Otherwise, additional setup steps or tests
        // that mock IpClient's dependencies might interact with those mocks while IpClient is
        // starting. This would cause UnfinishedStubbingExceptions as mocks cannot be interacted
        // with while they are being stubbed.
        HandlerUtils.waitForIdle(ipc.getHandler(), TEST_TIMEOUT_MS);
        return ipc;
    }

    private void setUpIpClient() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final IBinder netdIBinder =
                (IBinder) inst.getContext().getSystemService(Context.NETD_SERVICE);
        mNetd = spy(INetd.Stub.asInterface(netdIBinder));
        when(mContext.getSystemService(eq(Context.NETD_SERVICE))).thenReturn(netdIBinder);
        assertNotNull(mNetd);

        mNetworkObserverRegistry = new NetworkObserverRegistry();
        mNetworkObserverRegistry.register(mNetd);
        mIpc = makeIpClient();

        // Tell the IpMemoryStore immediately to answer any question about network attributes with a
        // null response. Otherwise, the DHCP client will wait for two seconds before starting,
        // while its query to the IpMemoryStore times out.
        // This does not affect any test that makes the mock memory store return results, because
        // unlike when(), it is documented that doAnswer() can be called more than once, to change
        // the behaviour of a mock in the middle of a test.
        doAnswer(invocation -> {
            final String l2Key = invocation.getArgument(0);
            ((OnNetworkAttributesRetrievedListener) invocation.getArgument(1))
                    .onNetworkAttributesRetrieved(new Status(SUCCESS), l2Key, null);
            return null;
        }).when(mIpMemoryStore).retrieveNetworkAttributes(any(), any());

        disableIpv6ProvisioningDelays();
    }

    private <T> T verifyWithTimeout(InOrder inOrder, T t) {
        if (inOrder != null) {
            return inOrder.verify(t, timeout(TEST_TIMEOUT_MS));
        } else {
            return verify(t, timeout(TEST_TIMEOUT_MS));
        }
    }

    private void expectAlarmCancelled(InOrder inOrder, OnAlarmListener listener) {
        inOrder.verify(mAlarm, timeout(TEST_TIMEOUT_MS)).cancel(eq(listener));
    }

    private OnAlarmListener expectAlarmSet(InOrder inOrder, String tagMatch, long afterSeconds,
            Handler handler) {
        // Allow +/- 3 seconds to prevent flaky tests.
        final long when = SystemClock.elapsedRealtime() + afterSeconds * 1000;
        final long min = when - 3 * 1000;
        final long max = when + 3 * 1000;
        ArgumentCaptor<OnAlarmListener> captor = ArgumentCaptor.forClass(OnAlarmListener.class);
        verifyWithTimeout(inOrder, mAlarm).setExact(
                anyInt(), longThat(x -> x >= min && x <= max),
                contains(tagMatch), captor.capture(), eq(handler));
        return captor.getValue();
    }

    private OnAlarmListener expectAlarmSet(InOrder inOrder, String tagMatch, int afterSeconds) {
        return expectAlarmSet(inOrder, tagMatch, (long) afterSeconds, mIpc.getHandler());
    }

    private boolean packetContainsExpectedField(final byte[] packet, final int offset,
            final byte[] expected) {
        if (packet.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; ++i) {
            if (packet[offset + i] != expected[i]) return false;
        }
        return true;
    }

    private boolean isDhcpPacket(final byte[] packet) {
        final ByteBuffer buffer = ByteBuffer.wrap(packet);

        // check the packet length
        if (packet.length < DHCP_HEADER_OFFSET) return false;

        // check the source port and dest port in UDP header
        buffer.position(UDP_SRC_PORT_OFFSET);
        final short udpSrcPort = buffer.getShort();
        final short udpDstPort = buffer.getShort();
        if (udpSrcPort != DHCP_CLIENT || udpDstPort != DHCP_SERVER) return false;

        // check DHCP message type
        buffer.position(DHCP_MESSAGE_OP_CODE_OFFSET);
        final byte dhcpOpCode = buffer.get();
        if (dhcpOpCode != DHCP_BOOTREQUEST) return false;

        // check DHCP magic cookie
        buffer.position(DHCP_OPTION_MAGIC_COOKIE_OFFSET);
        final int dhcpMagicCookie = buffer.getInt();
        if (dhcpMagicCookie != DHCP_MAGIC_COOKIE) return false;

        return true;
    }

    private boolean isDhcp6Packet(final byte[] packet) {
        final ByteBuffer buffer = ByteBuffer.wrap(packet);

        // check the packet length
        if (packet.length < DHCP6_HEADER_OFFSET) return false;

        // check Ethernet header
        final EthernetHeader ethHdr = Struct.parse(EthernetHeader.class, buffer);
        if (ethHdr.etherType != ETH_P_IPV6) {
            return false;
        }

        // check IPv6 header
        final Ipv6Header ipv6Hdr = Struct.parse(Ipv6Header.class, buffer);
        final int version = (ipv6Hdr.vtf >> 28) & 0x0F;
        if (version != 6) {
            return false;
        }
        if (ipv6Hdr.nextHeader != IPPROTO_UDP) {
            return false;
        }
        if (!ipv6Hdr.dstIp.equals(ALL_DHCP_RELAY_AGENTS_AND_SERVERS)) {
            return false;
        }
        mClientIpAddress = ipv6Hdr.srcIp;

        // check the source port and dest port in UDP header
        final short udpSrcPort = buffer.getShort();
        final short udpDstPort = buffer.getShort();
        return (udpSrcPort == DHCP6_CLIENT_PORT && udpDstPort == DHCP6_SERVER_PORT);
    }

    private ArpPacket parseArpPacketOrNull(final byte[] packet) {
        try {
            return ArpPacket.parseArpPacket(packet, packet.length);
        } catch (ArpPacket.ParseException e) {
            return null;
        }
    }

    private NeighborAdvertisement parseNeighborAdvertisementOrNull(final byte[] packet) {
        try {
            return NeighborAdvertisement.parse(packet, packet.length);
        } catch (NeighborAdvertisement.ParseException e) {
            return null;
        }
    }

    private NeighborSolicitation parseNeighborSolicitationOrNull(final byte[] packet) {
        try {
            return NeighborSolicitation.parse(packet, packet.length);
        } catch (NeighborSolicitation.ParseException e) {
            return null;
        }
    }

    private static ByteBuffer buildDhcpOfferPacket(final DhcpPacket packet,
            final Inet4Address clientAddress, final Integer leaseTimeSec, final short mtu,
            final String captivePortalUrl, final Integer ipv6OnlyWaitTime,
            final String domainName, final List<String> domainSearchList) {
        return DhcpPacket.buildOfferPacket(DhcpPacket.ENCAP_L2, packet.getTransactionId(),
                false /* broadcast */, SERVER_ADDR, INADDR_ANY /* relayIp */,
                clientAddress /* yourIp */, packet.getClientMac(), leaseTimeSec,
                NETMASK /* netMask */, BROADCAST_ADDR /* bcAddr */,
                Collections.singletonList(SERVER_ADDR) /* gateways */,
                Collections.singletonList(SERVER_ADDR) /* dnsServers */,
                SERVER_ADDR /* dhcpServerIdentifier */, domainName, HOSTNAME,
                false /* metered */, mtu, captivePortalUrl, ipv6OnlyWaitTime, domainSearchList);
    }

    private static ByteBuffer buildDhcpOfferPacket(final DhcpPacket packet,
            final Inet4Address clientAddress, final Integer leaseTimeSec, final short mtu,
            final String captivePortalUrl) {
        return buildDhcpOfferPacket(packet, clientAddress, leaseTimeSec, mtu, captivePortalUrl,
                null /* ipv6OnlyWaitTime */, null /* domainName */, null /* domainSearchList */);
    }

    private static ByteBuffer buildDhcpAckPacket(final DhcpPacket packet,
            final Inet4Address clientAddress, final Integer leaseTimeSec, final short mtu,
            final boolean rapidCommit, final String captivePortalApiUrl,
            final Integer ipv6OnlyWaitTime, final String domainName,
            final List<String> domainSearchList) {
        return DhcpPacket.buildAckPacket(DhcpPacket.ENCAP_L2, packet.getTransactionId(),
                false /* broadcast */, SERVER_ADDR, INADDR_ANY /* relayIp */,
                clientAddress /* yourIp */, CLIENT_ADDR /* requestIp */, packet.getClientMac(),
                leaseTimeSec, NETMASK /* netMask */, BROADCAST_ADDR /* bcAddr */,
                Collections.singletonList(SERVER_ADDR) /* gateways */,
                Collections.singletonList(SERVER_ADDR) /* dnsServers */,
                SERVER_ADDR /* dhcpServerIdentifier */, domainName, HOSTNAME,
                false /* metered */, mtu, rapidCommit, captivePortalApiUrl, ipv6OnlyWaitTime,
                domainSearchList);
    }

    private static ByteBuffer buildDhcpAckPacket(final DhcpPacket packet,
            final Inet4Address clientAddress, final Integer leaseTimeSec, final short mtu,
            final boolean rapidCommit, final String captivePortalApiUrl) {
        return buildDhcpAckPacket(packet, clientAddress, leaseTimeSec, mtu, rapidCommit,
                captivePortalApiUrl, null /* ipv6OnlyWaitTime */, null /* domainName */,
                null /* domainSearchList */);
    }

    private static ByteBuffer buildDhcpNakPacket(final DhcpPacket packet, final String message) {
        return DhcpPacket.buildNakPacket(DhcpPacket.ENCAP_L2, packet.getTransactionId(),
            SERVER_ADDR /* serverIp */, INADDR_ANY /* relayIp */, packet.getClientMac(),
            false /* broadcast */, message);
    }

    private static ByteBuffer buildDhcp6Packet(final ByteBuffer payload, final MacAddress clientMac,
            final Inet6Address clientIp) throws Exception {
        final ByteBuffer buffer = PacketBuilder.allocate(true /* hasEther */, IPPROTO_IPV6,
                IPPROTO_UDP, payload.limit());
        final PacketBuilder pb = new PacketBuilder(buffer);

        pb.writeL2Header(ROUTER_MAC /* srcMac */, clientMac /* dstMac */, (short) ETH_P_IPV6);
        pb.writeIpv6Header(0x60000000 /* version=6, traffic class=0, flow label=0 */,
                (byte) IPPROTO_UDP, (short) 64 /* hop limit */, ROUTER_LINK_LOCAL /* srcIp */,
                clientIp /* dstIp */);
        pb.writeUdpHeader((short) DHCP6_SERVER_PORT /*src port */,
                (short) DHCP6_CLIENT_PORT /* dst port */);
        buffer.put(payload);
        return pb.finalizePacket();
    }

    private static ByteBuffer buildDhcp6Advertise(final Dhcp6Packet solicit, final byte[] iapd,
            final byte[] clientMac, final Inet6Address clientIp) throws Exception {
        final ByteBuffer advertise = Dhcp6Packet.buildAdvertisePacket(solicit.getTransactionId(),
                iapd, solicit.getClientDuid(), ROUTER_DUID);
        return buildDhcp6Packet(advertise, MacAddress.fromBytes(clientMac), clientIp);
    }

    private static ByteBuffer buildDhcp6Reply(final Dhcp6Packet request, final byte[] iapd,
            final byte[] clientMac, final Inet6Address clientIp, boolean rapidCommit)
            throws Exception {
        final ByteBuffer reply = Dhcp6Packet.buildReplyPacket(request.getTransactionId(),
                iapd, request.getClientDuid(), ROUTER_DUID, rapidCommit);
        return buildDhcp6Packet(reply, MacAddress.fromBytes(clientMac), clientIp);
    }

    private void sendArpReply(final byte[] dstMac, final byte[] srcMac, final Inet4Address targetIp,
            final Inet4Address senderIp) throws IOException {
        final ByteBuffer packet = ArpPacket.buildArpPacket(dstMac, srcMac, targetIp.getAddress(),
                dstMac /* target HW address */, senderIp.getAddress(), (short) ARP_REPLY);
        mPacketReader.sendResponse(packet);
    }

    private void sendArpProbe() throws IOException {
        final ByteBuffer packet = ArpPacket.buildArpPacket(DhcpPacket.ETHER_BROADCAST /* dst */,
                ROUTER_MAC_BYTES /* srcMac */, CLIENT_ADDR.getAddress() /* target IP */,
                new byte[ETHER_ADDR_LEN] /* target HW address */,
                INADDR_ANY.getAddress() /* sender IP */, (short) ARP_REQUEST);
        mPacketReader.sendResponse(packet);
    }

    private void startIpClientProvisioning(final ProvisioningConfiguration cfg) throws Exception {
        mIIpClient.startProvisioning(cfg.toStableParcelable());
    }

    private void startIpClientProvisioning(final boolean shouldReplyRapidCommitAck,
            final boolean isPreconnectionEnabled,
            final boolean isDhcpIpConflictDetectEnabled,
            final String displayName,
            final ScanResultInfo scanResultInfo,
            final Layer2Information layer2Info)
                    throws Exception {
        ProvisioningConfiguration.Builder prov = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withLayer2Information(layer2Info == null
                        ? new Layer2Information(TEST_L2KEY, TEST_CLUSTER,
                              MacAddress.fromString(TEST_DEFAULT_BSSID))
                        : layer2Info)
                .withoutIPv6();
        if (isPreconnectionEnabled) prov.withPreconnection();
        if (displayName != null) prov.withDisplayName(displayName);
        if (scanResultInfo != null) prov.withScanResultInfo(scanResultInfo);

        setDhcpFeatures(shouldReplyRapidCommitAck, isDhcpIpConflictDetectEnabled);

        startIpClientProvisioning(prov.build());
        if (!isPreconnectionEnabled) {
            verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);
        }
        verify(mCb, never()).onProvisioningFailure(any());
    }

    private void startIpClientProvisioning(final boolean isDhcpRapidCommitEnabled,
            final boolean isPreconnectionEnabled,
            final boolean isDhcpIpConflictDetectEnabled) throws Exception {
        startIpClientProvisioning(isDhcpRapidCommitEnabled,
                isPreconnectionEnabled, isDhcpIpConflictDetectEnabled,
                null /* displayName */, null /* ScanResultInfo */, null /* layer2Info */);
    }

    private void assertIpMemoryStoreNetworkAttributes(final Integer leaseTimeSec,
            final long startTime, final int mtu) {
        final NetworkAttributes na = getStoredNetworkAttributes(TEST_L2KEY, TEST_TIMEOUT_MS);
        assertNotNull(na);
        assertEquals(CLIENT_ADDR, na.assignedV4Address);
        if (leaseTimeSec == null || leaseTimeSec.intValue() == DhcpPacket.INFINITE_LEASE) {
            assertEquals(Long.MAX_VALUE, na.assignedV4AddressExpiry.longValue());
        } else {
            // check the lease expiry's scope
            final long upperBound = startTime + 7_200_000; // start timestamp + 2h
            final long lowerBound = startTime + 3_600_000; // start timestamp + 1h
            final long expiry = na.assignedV4AddressExpiry;
            assertTrue(upperBound > expiry);
            assertTrue(lowerBound < expiry);
        }
        assertEquals(Collections.singletonList(SERVER_ADDR), na.dnsAddresses);
        assertEquals(new Integer(mtu), na.mtu);
    }

    private void assertIpMemoryNeverStoreNetworkAttributes() {
        assertIpMemoryNeverStoreNetworkAttributes(TEST_L2KEY, TEST_TIMEOUT_MS);
    }

    private void assertHostname(final boolean expectSendHostname,
            final String hostname, final String hostnameAfterTransliteration,
            final List<DhcpPacket> packetList) throws Exception {
        for (DhcpPacket packet : packetList) {
            if (!expectSendHostname || hostname == null) {
                assertNoHostname(packet.getHostname());
            } else {
                assertEquals(hostnameAfterTransliteration, packet.getHostname());
            }
        }
    }

    private void assertNoHostname(String hostname) {
        if (ShimUtils.isAtLeastR()) {
            assertNull(hostname);
        } else {
            // Until Q, if no hostname is set, the device falls back to the hostname set via
            // system property, to avoid breaking Q devices already launched with that setup.
            assertEquals(SystemProperties.get("net.hostname"), hostname);
        }
    }

    // Helper method to complete DHCP 2-way or 4-way handshake
    private List<DhcpPacket> performDhcpHandshake(final boolean isSuccessLease,
            final Integer leaseTimeSec, final boolean shouldReplyRapidCommitAck, final int mtu,
            final boolean isDhcpIpConflictDetectEnabled,
            final String captivePortalApiUrl, final String displayName,
            final ScanResultInfo scanResultInfo, final Layer2Information layer2Info)
            throws Exception {
        startIpClientProvisioning(shouldReplyRapidCommitAck,
                false /* isPreconnectionEnabled */, isDhcpIpConflictDetectEnabled,
                displayName, scanResultInfo, layer2Info);
        return handleDhcpPackets(isSuccessLease, leaseTimeSec, shouldReplyRapidCommitAck, mtu,
                captivePortalApiUrl);
    }

    private List<DhcpPacket> handleDhcpPackets(final boolean isSuccessLease,
            final Integer leaseTimeSec, final boolean shouldReplyRapidCommitAck, final int mtu,
            final String captivePortalApiUrl) throws Exception {
        return handleDhcpPackets(isSuccessLease, leaseTimeSec, shouldReplyRapidCommitAck,
                mtu, captivePortalApiUrl, null /* ipv6OnlyWaitTime */,
                null /* domainName */, null /* domainSearchList */);
    }

    private List<DhcpPacket> handleDhcpPackets(final boolean isSuccessLease,
            final Integer leaseTimeSec, final boolean shouldReplyRapidCommitAck, final int mtu,
            final String captivePortalApiUrl, final Integer ipv6OnlyWaitTime,
            final String domainName, final List<String> domainSearchList) throws Exception {
        final List<DhcpPacket> packetList = new ArrayList<>();
        DhcpPacket packet;
        while ((packet = getNextDhcpPacket()) != null) {
            packetList.add(packet);
            if (packet instanceof DhcpDiscoverPacket) {
                if (shouldReplyRapidCommitAck) {
                    mPacketReader.sendResponse(buildDhcpAckPacket(packet, CLIENT_ADDR, leaseTimeSec,
                              (short) mtu, true /* rapidCommit */, captivePortalApiUrl,
                              ipv6OnlyWaitTime, domainName, domainSearchList));
                } else {
                    mPacketReader.sendResponse(buildDhcpOfferPacket(packet, CLIENT_ADDR,
                            leaseTimeSec, (short) mtu, captivePortalApiUrl, ipv6OnlyWaitTime,
                            domainName, domainSearchList));
                }
            } else if (packet instanceof DhcpRequestPacket) {
                final ByteBuffer byteBuffer = isSuccessLease
                        ? buildDhcpAckPacket(packet, CLIENT_ADDR, leaseTimeSec, (short) mtu,
                                false /* rapidCommit */, captivePortalApiUrl, ipv6OnlyWaitTime,
                                domainName, domainSearchList)
                        : buildDhcpNakPacket(packet, "duplicated request IP address");
                mPacketReader.sendResponse(byteBuffer);
            } else {
                fail("invalid DHCP packet");
            }

            // wait for reply to DHCPOFFER packet if disabling rapid commit option
            if (shouldReplyRapidCommitAck || !(packet instanceof DhcpDiscoverPacket)) {
                return packetList;
            }
        }
        fail("No DHCPREQUEST received on interface");
        return packetList;
    }

    private List<DhcpPacket> performDhcpHandshake(final boolean isSuccessLease,
            final Integer leaseTimeSec, final boolean isDhcpRapidCommitEnabled, final int mtu,
            final boolean isDhcpIpConflictDetectEnabled) throws Exception {
        return performDhcpHandshake(isSuccessLease, leaseTimeSec, isDhcpRapidCommitEnabled,
                mtu, isDhcpIpConflictDetectEnabled,
                null /* captivePortalApiUrl */, null /* displayName */, null /* scanResultInfo */,
                null /* layer2Info */);
    }

    private List<DhcpPacket> performDhcpHandshake() throws Exception {
        return performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
    }

    private DhcpPacket getNextDhcpPacket(final long timeout) throws Exception {
        byte[] packet;
        while ((packet = mDhcpPacketReadHead.getValue()
                .poll(timeout, this::isDhcpPacket)) != null) {
            final DhcpPacket dhcpPacket = DhcpPacket.decodeFullPacket(packet, packet.length,
                    ENCAP_L2);
            if (dhcpPacket != null) return dhcpPacket;
        }
        return null;
    }

    private DhcpPacket getNextDhcpPacket() throws Exception {
        final DhcpPacket packet = getNextDhcpPacket(PACKET_TIMEOUT_MS);
        assertNotNull("No expected DHCP packet received on interface within timeout", packet);
        return packet;
    }

    private Dhcp6Packet getNextDhcp6Packet(final long timeout) throws Exception {
        byte[] packet;
        while ((packet = mDhcp6PacketReadHead.getValue()
                .poll(timeout, this::isDhcp6Packet)) != null) {
            // Strip the Ethernet/IPv6/UDP headers, only keep DHCPv6 message payload for decode.
            final byte[] payload =
                    Arrays.copyOfRange(packet, DHCP6_HEADER_OFFSET, packet.length);
            final Dhcp6Packet dhcp6Packet = Dhcp6Packet.decode(payload, payload.length);
            if (dhcp6Packet != null) return dhcp6Packet;
        }
        return null;
    }

    private Dhcp6Packet getNextDhcp6Packet() throws Exception {
        final Dhcp6Packet packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertNotNull("No expected DHCPv6 packet received on interface within timeout", packet);
        return packet;
    }

    private DhcpPacket getReplyFromDhcpLease(final NetworkAttributes na, boolean timeout)
            throws Exception {
        doAnswer(invocation -> {
            if (timeout) return null;
            ((OnNetworkAttributesRetrievedListener) invocation.getArgument(1))
                    .onNetworkAttributesRetrieved(new Status(SUCCESS), TEST_L2KEY, na);
            return null;
        }).when(mIpMemoryStore).retrieveNetworkAttributes(eq(TEST_L2KEY), any());
        startIpClientProvisioning(false /* shouldReplyRapidCommitAck */,
                false /* isPreconnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        return getNextDhcpPacket();
    }

    private void removeTestInterface(final FileDescriptor fd) {
        try {
            Os.close(fd);
        } catch (ErrnoException e) {
            fail("Fail to close file descriptor: " + e);
        }
    }

    private void verifyAfterIpClientShutdown() throws RemoteException {
        final LinkProperties emptyLp = new LinkProperties();
        emptyLp.setInterfaceName(mIfaceName);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(emptyLp);
    }

    // Verify IPv4-only provisioning success. No need to verify IPv4 provisioning when below cases
    // happen:
    // 1. if there's a failure lease, onProvisioningSuccess() won't be called;
    // 2. if duplicated IPv4 address detection is enabled, verify TIMEOUT will affect ARP packets
    //    capture running in other test cases.
    // 3. if IPv6 is enabled, e.g. withoutIPv6() isn't called when starting provisioning.
    private LinkProperties verifyIPv4OnlyProvisioningSuccess(
            final Collection<InetAddress> addresses) throws Exception {
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertNotEquals(0, lp.getDnsServers().size());
        assertEquals(addresses.size(), lp.getAddresses().size());
        assertTrue(lp.getAddresses().containsAll(addresses));
        assertTrue(hasRouteTo(lp, IPV4_TEST_SUBNET_PREFIX)); // IPv4 directly-connected route
        assertTrue(hasRouteTo(lp, IPV4_ANY_ADDRESS_PREFIX)); // IPv4 default route
        return lp;
    }

    private void doRestoreInitialMtuTest(final boolean shouldChangeMtu,
            final boolean shouldRemoveTestInterface) throws Exception {
        final long currentTime = System.currentTimeMillis();
        int mtu = TEST_DEFAULT_MTU;

        if (shouldChangeMtu) mtu = TEST_MIN_MTU;
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                mtu, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, mtu);

        if (shouldChangeMtu) {
            // Pretend that ConnectivityService set the MTU.
            mNetd.interfaceSetMtu(mIfaceName, mtu);
            assertEquals(NetworkInterface.getByName(mIfaceName).getMTU(), mtu);
        }

        // Sometimes, IpClient receives an update with an empty LinkProperties during startup,
        // when the link-local address is deleted after interface bringup. Reset expectations
        // here to ensure that verifyAfterIpClientShutdown does not fail because it sees two
        // empty LinkProperties changes instead of one.
        reset(mCb);

        if (shouldRemoveTestInterface) removeTestInterface(mTapFd);
        try {
            mIpc.shutdown();
            awaitIpClientShutdown();
            if (shouldRemoveTestInterface) {
                verify(mNetd, never()).interfaceSetMtu(mIfaceName, TEST_DEFAULT_MTU);
            } else {
                // Verify that MTU indeed has been restored or not.
                verify(mNetd, times(shouldChangeMtu ? 1 : 0))
                        .interfaceSetMtu(mIfaceName, TEST_DEFAULT_MTU);
            }
            verifyAfterIpClientShutdown();
        } catch (Exception e) {
            fail("Exception should not have been thrown after shutdown: " + e);
        }
    }

    private DhcpPacket assertDiscoverPacketOnPreconnectionStart() throws Exception {
        final ArgumentCaptor<List<Layer2PacketParcelable>> l2PacketList =
                ArgumentCaptor.forClass(List.class);

        verify(mCb, timeout(TEST_TIMEOUT_MS)).onPreconnectionStart(l2PacketList.capture());
        final byte[] payload = l2PacketList.getValue().get(0).payload;
        DhcpPacket packet = DhcpPacket.decodeFullPacket(payload, payload.length, ENCAP_L2);
        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertArrayEquals(INADDR_BROADCAST.getAddress(),
                Arrays.copyOfRange(payload, IPV4_DST_ADDR_OFFSET, IPV4_DST_ADDR_OFFSET + 4));
        return packet;
    }

    private void doIpClientProvisioningWithPreconnectionTest(
            final boolean shouldReplyRapidCommitAck, final boolean shouldAbortPreconnection,
            final boolean shouldFirePreconnectionTimeout,
            final boolean timeoutBeforePreconnectionComplete) throws Exception {
        final long currentTime = System.currentTimeMillis();
        startIpClientProvisioning(shouldReplyRapidCommitAck,
                true /* isDhcpPreConnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        DhcpPacket packet = assertDiscoverPacketOnPreconnectionStart();
        final int preconnDiscoverTransId = packet.getTransactionId();

        if (shouldAbortPreconnection) {
            if (shouldFirePreconnectionTimeout && timeoutBeforePreconnectionComplete) {
                mDependencies.mDhcpClient.sendMessage(DhcpClient.CMD_TIMEOUT);
            }

            mIpc.notifyPreconnectionComplete(false /* abort */);
            HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);

            if (shouldFirePreconnectionTimeout && !timeoutBeforePreconnectionComplete) {
                mDependencies.mDhcpClient.sendMessage(DhcpClient.CMD_TIMEOUT);
            }

            // Either way should get DhcpClient go back to INIT state, and broadcast
            // DISCOVER with new transaction ID.
            packet = getNextDhcpPacket();
            assertTrue(packet instanceof DhcpDiscoverPacket);
            assertTrue(packet.getTransactionId() != preconnDiscoverTransId);
        } else if (shouldFirePreconnectionTimeout && timeoutBeforePreconnectionComplete) {
            // If timeout fires before success preconnection, DhcpClient will go back to INIT state,
            // and broadcast DISCOVER with new transaction ID.
            mDependencies.mDhcpClient.sendMessage(DhcpClient.CMD_TIMEOUT);
            packet = getNextDhcpPacket();
            assertTrue(packet instanceof DhcpDiscoverPacket);
            assertTrue(packet.getTransactionId() != preconnDiscoverTransId);
            // any old response would be ignored due to mismatched transaction ID.
        }

        final short mtu = (short) TEST_DEFAULT_MTU;
        if (!shouldReplyRapidCommitAck) {
            mPacketReader.sendResponse(buildDhcpOfferPacket(packet, CLIENT_ADDR,
                    TEST_LEASE_DURATION_S, mtu, null /* captivePortalUrl */));
            packet = getNextDhcpPacket();
            assertTrue(packet instanceof DhcpRequestPacket);
        }
        mPacketReader.sendResponse(buildDhcpAckPacket(packet, CLIENT_ADDR, TEST_LEASE_DURATION_S,
                mtu, shouldReplyRapidCommitAck, null /* captivePortalUrl */));

        if (!shouldAbortPreconnection) {
            mIpc.notifyPreconnectionComplete(true /* success */);
            HandlerUtils.waitForIdle(mDependencies.mDhcpClient.getHandler(), TEST_TIMEOUT_MS);

            // If timeout fires after successful preconnection, right now DhcpClient will have
            // already entered BOUND state, the delayed CMD_TIMEOUT command would be ignored. So
            // this case should be very rare, because the timeout alarm is cancelled when state
            // machine exits from Preconnecting state.
            if (shouldFirePreconnectionTimeout && !timeoutBeforePreconnectionComplete) {
                mDependencies.mDhcpClient.sendMessage(DhcpClient.CMD_TIMEOUT);
            }
        }
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    private ArpPacket getNextArpPacket(final long timeout) throws Exception {
        byte[] packet;
        while ((packet = mArpPacketReadHead.getValue().poll(timeout, p -> true)) != null) {
            final ArpPacket arpPacket = parseArpPacketOrNull(packet);
            if (arpPacket != null) return arpPacket;
        }
        return null;
    }

    private ArpPacket getNextArpPacket() throws Exception {
        final ArpPacket packet = getNextArpPacket(PACKET_TIMEOUT_MS);
        assertNotNull("No expected ARP packet received on interface within timeout", packet);
        return packet;
    }

    private void assertArpPacket(final ArpPacket packet) {
        assertEquals(packet.opCode, ARP_REQUEST);
        assertEquals(packet.targetIp, CLIENT_ADDR);
        assertTrue(Arrays.equals(packet.senderHwAddress.toByteArray(), mClientMac));
    }

    private void assertArpProbe(final ArpPacket packet) {
        assertArpPacket(packet);
        assertEquals(packet.senderIp, INADDR_ANY);
    }

    private void assertArpAnnounce(final ArpPacket packet) {
        assertArpPacket(packet);
        assertEquals(packet.senderIp, CLIENT_ADDR);
    }

    private void assertArpRequest(final ArpPacket packet, final Inet4Address targetIp) {
        assertEquals(packet.opCode, ARP_REQUEST);
        assertEquals(packet.senderIp, CLIENT_ADDR);
        assertEquals(packet.targetIp, targetIp);
        assertTrue(Arrays.equals(packet.targetHwAddress.toByteArray(),
                MacAddress.fromString("00:00:00:00:00:00").toByteArray()));
        assertTrue(Arrays.equals(packet.senderHwAddress.toByteArray(), mClientMac));
    }

    private void assertGratuitousARP(final ArpPacket packet) {
        assertEquals(packet.opCode, ARP_REPLY);
        assertEquals(packet.senderIp, CLIENT_ADDR);
        assertEquals(packet.targetIp, CLIENT_ADDR);
        assertTrue(Arrays.equals(packet.senderHwAddress.toByteArray(), mClientMac));
        assertTrue(Arrays.equals(packet.targetHwAddress.toByteArray(), ETHER_BROADCAST));
    }

    private void doIpAddressConflictDetectionTest(final boolean causeIpAddressConflict,
            final boolean shouldReplyRapidCommitAck, final boolean isDhcpIpConflictDetectEnabled,
            final boolean shouldResponseArpReply) throws Exception {
        final long currentTime = System.currentTimeMillis();

        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                shouldReplyRapidCommitAck,
                TEST_DEFAULT_MTU, isDhcpIpConflictDetectEnabled);

        // If we receive an ARP packet here, it's guaranteed to be from IP conflict detection,
        // because at this time the test interface does not have an IP address and therefore
        // won't send ARP for anything.
        if (causeIpAddressConflict) {
            final ArpPacket arpProbe = getNextArpPacket();
            assertArpProbe(arpProbe);

            if (shouldResponseArpReply) {
                sendArpReply(mClientMac /* dstMac */, ROUTER_MAC_BYTES /* srcMac */,
                        INADDR_ANY /* target IP */, CLIENT_ADDR /* sender IP */);
            } else {
                sendArpProbe();
            }
            final DhcpPacket packet = getNextDhcpPacket();
            assertTrue(packet instanceof DhcpDeclinePacket);
            assertEquals(packet.mServerIdentifier, SERVER_ADDR);
            assertEquals(packet.mRequestedIp, CLIENT_ADDR);

            verify(mCb, never()).onProvisioningFailure(any());
            assertIpMemoryNeverStoreNetworkAttributes();
        } else if (isDhcpIpConflictDetectEnabled) {
            int arpPacketCount = 0;
            final List<ArpPacket> packetList = new ArrayList<ArpPacket>();
            // Total sent ARP packets should be 5 (3 ARP Probes + 2 ARP Announcements)
            ArpPacket packet;
            while ((packet = getNextArpPacket(TEST_TIMEOUT_MS)) != null) {
                packetList.add(packet);
            }
            assertEquals(5, packetList.size());
            assertArpProbe(packetList.get(0));
            assertArpAnnounce(packetList.get(3));
        } else {
            verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
            assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime,
                    TEST_DEFAULT_MTU);
        }
    }

    @Test @SignatureRequiredTest(reason = "InterfaceParams.getByName requires CAP_NET_ADMIN")
    public void testInterfaceParams() throws Exception {
        InterfaceParams params = InterfaceParams.getByName(mIfaceName);
        assertNotNull(params);
        assertEquals(mIfaceName, params.name);
        assertTrue(params.index > 0);
        assertNotNull(params.macAddr);
        assertTrue(params.hasMacAddress);

        //  Check interface "lo".
        params = InterfaceParams.getByName("lo");
        assertNotNull(params);
        assertEquals("lo", params.name);
        assertTrue(params.index > 0);
        assertNotNull(params.macAddr);
        assertFalse(params.hasMacAddress);
    }

    @Test
    public void testDhcpInit() throws Exception {
        startIpClientProvisioning(false /* shouldReplyRapidCommitAck */,
                false /* isPreconnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        final DhcpPacket packet = getNextDhcpPacket();
        assertTrue(packet instanceof DhcpDiscoverPacket);
    }

    @Test
    public void testHandleSuccessDhcpLease() throws Exception {
        final long currentTime = System.currentTimeMillis();
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    @Test
    public void testHandleFailureDhcpLease() throws Exception {
        performDhcpHandshake(false /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);

        verify(mCb, never()).onProvisioningSuccess(any());
        assertIpMemoryNeverStoreNetworkAttributes();
    }

    @Test
    public void testHandleInfiniteLease() throws Exception {
        final long currentTime = System.currentTimeMillis();
        performDhcpHandshake(true /* isSuccessLease */, INFINITE_LEASE,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(INFINITE_LEASE, currentTime, TEST_DEFAULT_MTU);
    }

    @Test
    public void testHandleNoLease() throws Exception {
        final long currentTime = System.currentTimeMillis();
        performDhcpHandshake(true /* isSuccessLease */, null /* no lease time */,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(null, currentTime, TEST_DEFAULT_MTU);
    }

    @Test
    public void testHandleRapidCommitOption() throws Exception {
        final long currentTime = System.currentTimeMillis();
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                true /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testRollbackFromRapidCommitOption() throws Exception {
        startIpClientProvisioning(true /* isDhcpRapidCommitEnabled */,
                false /* isPreConnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);

        final List<DhcpPacket> discoverList = new ArrayList<DhcpPacket>();
        DhcpPacket packet;
        do {
            packet = getNextDhcpPacket();
            assertTrue(packet instanceof DhcpDiscoverPacket);
            discoverList.add(packet);
        } while (discoverList.size() < 4);

        // Check the only first 3 DHCPDISCOVERs take rapid commit option.
        assertTrue(discoverList.get(0).mRapidCommit);
        assertTrue(discoverList.get(1).mRapidCommit);
        assertTrue(discoverList.get(2).mRapidCommit);
        assertFalse(discoverList.get(3).mRapidCommit);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientStartWithCachedInfiniteLease() throws Exception {
        final DhcpPacket packet = getReplyFromDhcpLease(
                new NetworkAttributes.Builder()
                    .setAssignedV4Address(CLIENT_ADDR)
                    .setAssignedV4AddressExpiry(Long.MAX_VALUE) // lease is always valid
                    .setMtu(new Integer(TEST_DEFAULT_MTU))
                    .setCluster(TEST_CLUSTER)
                    .setDnsAddresses(Collections.singletonList(SERVER_ADDR))
                    .build(), false /* timeout */);
        assertTrue(packet instanceof DhcpRequestPacket);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientStartWithCachedExpiredLease() throws Exception {
        final DhcpPacket packet = getReplyFromDhcpLease(
                 new NetworkAttributes.Builder()
                    .setAssignedV4Address(CLIENT_ADDR)
                    .setAssignedV4AddressExpiry(EXPIRED_LEASE)
                    .setMtu(new Integer(TEST_DEFAULT_MTU))
                    .setCluster(TEST_CLUSTER)
                    .setDnsAddresses(Collections.singletonList(SERVER_ADDR))
                    .build(), false /* timeout */);
        assertTrue(packet instanceof DhcpDiscoverPacket);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientStartWithNullRetrieveNetworkAttributes() throws Exception {
        final DhcpPacket packet = getReplyFromDhcpLease(null /* na */, false /* timeout */);
        assertTrue(packet instanceof DhcpDiscoverPacket);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientStartWithTimeoutRetrieveNetworkAttributes() throws Exception {
        final DhcpPacket packet = getReplyFromDhcpLease(
                new NetworkAttributes.Builder()
                    .setAssignedV4Address(CLIENT_ADDR)
                    .setAssignedV4AddressExpiry(System.currentTimeMillis() + 3_600_000)
                    .setMtu(new Integer(TEST_DEFAULT_MTU))
                    .setCluster(TEST_CLUSTER)
                    .setDnsAddresses(Collections.singletonList(SERVER_ADDR))
                    .build(), true /* timeout */);
        assertTrue(packet instanceof DhcpDiscoverPacket);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientStartWithCachedLeaseWithoutIPAddress() throws Exception {
        final DhcpPacket packet = getReplyFromDhcpLease(
                new NetworkAttributes.Builder()
                    .setMtu(new Integer(TEST_DEFAULT_MTU))
                    .setCluster(TEST_CLUSTER)
                    .setDnsAddresses(Collections.singletonList(SERVER_ADDR))
                    .build(), false /* timeout */);
        assertTrue(packet instanceof DhcpDiscoverPacket);
    }

    @Test
    public void testDhcpClientRapidCommitEnabled() throws Exception {
        startIpClientProvisioning(true /* shouldReplyRapidCommitAck */,
                false /* isPreconnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        final DhcpPacket packet = getNextDhcpPacket();
        assertTrue(packet instanceof DhcpDiscoverPacket);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.Q)
    public void testDhcpServerInLinkProperties() throws Exception {
        assumeTrue(ConstantsShim.VERSION > Build.VERSION_CODES.Q);

        performDhcpHandshake();
        ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        assertEquals(SERVER_ADDR, captor.getValue().getDhcpServerAddress());
    }

    private void createTestNetworkAgentAndRegister(final LinkProperties lp) throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        final NetworkSpecifier testNetworkSpecifier =
                CompatUtil.makeTestNetworkSpecifier(mIfaceName);
        final TestableNetworkCallback cb = new TestableNetworkCallback();

        // Requesting a network make sure the NetworkAgent is alive during the whole life cycle of
        // requested network.
        cm.requestNetwork(new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .removeCapability(NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(testNetworkSpecifier)
                .build(), cb);
        mNetworkAgent = new TestableNetworkAgent(context, mNetworkAgentThread.getLooper(),
                new NetworkCapabilities.Builder()
                        .removeCapability(NET_CAPABILITY_TRUSTED)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                        .addCapability(NET_CAPABILITY_NOT_ROAMING)
                        .addCapability(NET_CAPABILITY_NOT_VPN)
                        .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                        .addTransportType(TRANSPORT_TEST)
                        .setNetworkSpecifier(testNetworkSpecifier)
                        .build(),
                lp,
                new NetworkAgentConfig.Builder().build());
        mNetworkAgent.register();
        mNetworkAgent.markConnected();
        cb.expectAvailableThenValidatedCallbacks(mNetworkAgent.getNetwork(), TEST_TIMEOUT_MS);
    }

    private void assertReceivedDhcpRequestPacketCount() throws Exception {
        final List<DhcpPacket> packetList = new ArrayList<>();
        DhcpPacket packet;
        while ((packet = getNextDhcpPacket(PACKET_TIMEOUT_MS)) != null) {
            assertDhcpRequestForReacquire(packet);
            packetList.add(packet);
        }
        assertEquals(1, packetList.size());
    }

    private LinkProperties prepareDhcpReacquireTest() throws Exception {
        mNetworkAgentThread =
                new HandlerThread(IpClientIntegrationTestCommon.class.getSimpleName());
        mNetworkAgentThread.start();

        final long currentTime = System.currentTimeMillis();
        setFeatureEnabled(NetworkStackUtils.DHCP_SLOW_RETRANSMISSION_VERSION, true);
        performDhcpHandshake(true /* isSuccessLease */,
                TEST_LEASE_DURATION_S, false /* isDhcpRapidCommitEnabled */, TEST_DEFAULT_MTU,
                false /* isDhcpIpConflictDetectEnabled */);
        final LinkProperties lp =
                verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
        return lp;
    }

    private OnAlarmListener runDhcpRenewTest(final Handler handler, final LinkProperties lp,
            final InOrder inOrder) throws Exception {
        // Create a NetworkAgent and register it to ConnectivityService with IPv4 LinkProperties,
        // then ConnectivityService will call netd API to configure the IPv4 route on the kernel,
        // otherwise, unicast DHCPREQUEST cannot be sent out due to no route to host(EHOSTUNREACH).
        runAsShell(MANAGE_TEST_NETWORKS, () -> createTestNetworkAgentAndRegister(lp));

        // DHCP client is in BOUND state right now, simulate the renewal via triggering renew alarm
        // which should happen at T1. E.g. lease duration is 3600s, T1 = lease_duration * 0.5(1800s)
        // T2 = lease_duration * 0.875(3150s).
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 1800, handler);
        final OnAlarmListener rebindAlarm = expectAlarmSet(inOrder, "REBIND", 3150, handler);

        // Trigger renew alarm and force DHCP client enter RenewingState. Device needs to start
        // the ARP resolution for the fake DHCP server IPv4 address before sending the unicast
        // DHCPREQUEST out, wait for the unicast ARP request and respond to it with ARP reply,
        // otherwise, DHCPREQUEST still cannot be sent out due to that there is no correct ARP
        // table for the dest IPv4 address.
        handler.post(() -> renewAlarm.onAlarm());
        final ArpPacket request = getNextArpPacket();
        assertArpRequest(request, SERVER_ADDR);
        sendArpReply(request.senderHwAddress.toByteArray() /* dst */, ROUTER_MAC_BYTES /* srcMac */,
                request.senderIp /* target IP */, SERVER_ADDR /* sender IP */);
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        // Verify there should be only one unicast DHCPREQUESTs to be received per RFC2131.
        assertReceivedDhcpRequestPacketCount();

        return rebindAlarm;
    }

    @Test @SignatureRequiredTest(reason = "Need to mock the DHCP renew/rebind alarms")
    public void testDhcpRenew() throws Exception {
        final LinkProperties lp = prepareDhcpReacquireTest();
        final InOrder inOrder = inOrder(mAlarm);
        runDhcpRenewTest(mDependencies.mDhcpClient.getHandler(), lp, inOrder);
    }

    @Test @SignatureRequiredTest(reason = "Need to mock the DHCP renew/rebind alarms")
    public void testDhcpRebind() throws Exception {
        final LinkProperties lp = prepareDhcpReacquireTest();
        final Handler handler = mDependencies.mDhcpClient.getHandler();
        final InOrder inOrder = inOrder(mAlarm);
        final OnAlarmListener rebindAlarm = runDhcpRenewTest(handler, lp, inOrder);

        // Trigger rebind alarm and forece DHCP client enter RebindingState. DHCP client sends
        // broadcast DHCPREQUEST to nearby servers, then check how many DHCPREQUEST packets are
        // retransmitted within PACKET_TIMEOUT_MS(5s), there should be only one DHCPREQUEST
        // captured per RFC2131.
        handler.post(() -> rebindAlarm.onAlarm());
        assertReceivedDhcpRequestPacketCount();
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRestoreInitialInterfaceMtu() throws Exception {
        doRestoreInitialMtuTest(true /* shouldChangeMtu */, false /* shouldRemoveTestInterface */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRestoreInitialInterfaceMtu_WithoutMtuChange() throws Exception {
        doRestoreInitialMtuTest(false /* shouldChangeMtu */, false /* shouldRemoveTestInterface */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRestoreInitialInterfaceMtu_WithException() throws Exception {
        doThrow(new RemoteException("NetdNativeService::interfaceSetMtu")).when(mNetd)
                .interfaceSetMtu(mIfaceName, TEST_DEFAULT_MTU);

        doRestoreInitialMtuTest(true /* shouldChangeMtu */, false /* shouldRemoveTestInterface */);
        assertEquals(NetworkInterface.getByName(mIfaceName).getMTU(), TEST_MIN_MTU);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRestoreInitialInterfaceMtu_NotFoundInterfaceWhenStopping() throws Exception {
        doRestoreInitialMtuTest(true /* shouldChangeMtu */, true /* shouldRemoveTestInterface */);
    }

    @Test
    public void testRestoreInitialInterfaceMtu_NotFoundInterfaceWhenStartingProvisioning()
            throws Exception {
        removeTestInterface(mTapFd);
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv6()
                .build();

        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(any());
        verify(mCb, never()).setNeighborDiscoveryOffload(true);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRestoreInitialInterfaceMtu_stopIpClientAndRestart() throws Exception {
        long currentTime = System.currentTimeMillis();

        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                TEST_MIN_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_MIN_MTU);

        // Pretend that ConnectivityService set the MTU.
        mNetd.interfaceSetMtu(mIfaceName, TEST_MIN_MTU);
        assertEquals(NetworkInterface.getByName(mIfaceName).getMTU(), TEST_MIN_MTU);

        reset(mCb);
        reset(mIpMemoryStore);

        // Stop IpClient and then restart provisioning immediately.
        mIpc.stop();
        currentTime = System.currentTimeMillis();
        // Intend to set mtu option to 0, then verify that won't influence interface mtu restore.
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                0 /* mtu */, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, 0 /* mtu */);
        assertEquals(NetworkInterface.getByName(mIfaceName).getMTU(), TEST_DEFAULT_MTU);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRestoreInitialInterfaceMtu_removeInterfaceAndAddback() throws Exception {
        doAnswer(invocation -> {
            final LinkProperties lp = invocation.getArgument(0);
            assertEquals(lp.getInterfaceName(), mIfaceName);
            assertEquals(0, lp.getLinkAddresses().size());
            assertEquals(0, lp.getDnsServers().size());

            mDependencies.simulateInterfaceRecover();
            return null;
        }).when(mCb).onProvisioningFailure(any());

        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv6()
                .build();

        // Intend to remove the tap interface and force IpClient throw provisioning failure
        // due to that interface is not found.
        removeTestInterface(mTapFd);
        assertNull(InterfaceParams.getByName(mIfaceName));

        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(any());

        // Make sure everything queued by this test was processed (e.g. transition to StoppingState
        // from ClearingIpAddressState) and tearDown will check if IpClient exits normally or crash.
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);
    }

    private boolean isIcmpv6PacketOfType(final byte[] packetBytes, int type) {
        ByteBuffer packet = ByteBuffer.wrap(packetBytes);
        return packet.getShort(ETHER_TYPE_OFFSET) == (short) ETH_P_IPV6
                && packet.get(ETHER_HEADER_LEN + IPV6_PROTOCOL_OFFSET) == (byte) IPPROTO_ICMPV6
                && packet.get(ETHER_HEADER_LEN + IPV6_HEADER_LEN) == (byte) type;
    }

    private boolean isRouterSolicitation(final byte[] packetBytes) {
        return isIcmpv6PacketOfType(packetBytes, ICMPV6_ROUTER_SOLICITATION);
    }

    private boolean isNeighborAdvertisement(final byte[] packetBytes) {
        return isIcmpv6PacketOfType(packetBytes, ICMPV6_NEIGHBOR_ADVERTISEMENT);
    }

    private boolean isNeighborSolicitation(final byte[] packetBytes) {
        return isIcmpv6PacketOfType(packetBytes, ICMPV6_NEIGHBOR_SOLICITATION);
    }

    private NeighborAdvertisement getNextNeighborAdvertisement() throws ParseException {
        final byte[] packet = mPacketReader.popPacket(PACKET_TIMEOUT_MS,
                this::isNeighborAdvertisement);
        if (packet == null) return null;

        final NeighborAdvertisement na = parseNeighborAdvertisementOrNull(packet);
        assertNotNull("Invalid neighbour advertisement received", na);
        return na;
    }

    private NeighborSolicitation getNextNeighborSolicitation() throws ParseException {
        final byte[] packet = mPacketReader.popPacket(PACKET_TIMEOUT_MS,
                this::isNeighborSolicitation);
        if (packet == null) return null;

        final NeighborSolicitation ns = parseNeighborSolicitationOrNull(packet);
        assertNotNull("Invalid neighbour solicitation received", ns);
        return ns;
    }

    private void waitForRouterSolicitation() throws ParseException {
        assertNotNull("No router solicitation received on interface within timeout",
                mPacketReader.popPacket(PACKET_TIMEOUT_MS, this::isRouterSolicitation));
    }

    private void sendRouterAdvertisement(boolean waitForRs, short lifetime, int valid,
            int preferred) throws Exception {
        final ByteBuffer pio = buildPioOption(valid, preferred, "2001:db8:1::/64");
        final ByteBuffer rdnss = buildRdnssOption(3600, IPV6_OFF_LINK_DNS_SERVER);
        sendRouterAdvertisement(waitForRs, lifetime, pio, rdnss);
    }

    private void sendRouterAdvertisement(boolean waitForRs, short lifetime,
            ByteBuffer... options) throws Exception {
        final ByteBuffer ra = buildRaPacket(lifetime, options);
        if (waitForRs) {
            waitForRouterSolicitation();
        }
        mPacketReader.sendResponse(ra);
    }

    private void sendBasicRouterAdvertisement(boolean waitForRs) throws Exception {
        sendRouterAdvertisement(waitForRs, (short) 1800 /* lifetime */, 3600 /* valid */,
                1800 /* preferred */);
    }

    private void sendRouterAdvertisementWithZeroRouterLifetime() throws Exception {
        sendRouterAdvertisement(false /* waitForRs */, (short) 0 /* lifetime */, 3600 /* valid */,
                1800 /* preferred */);
    }

    // TODO: move this and the following method to a common location and use them in ApfTest.
    private static ByteBuffer buildPioOption(int valid, int preferred, String prefixString)
            throws Exception {
        return PrefixInformationOption.build(new IpPrefix(prefixString),
                (byte) (PIO_FLAG_ON_LINK | PIO_FLAG_AUTONOMOUS), valid, preferred);
    }

    private static ByteBuffer buildRdnssOption(int lifetime, String... servers) throws Exception {
        return RdnssOption.build(lifetime, servers);
    }

    private static ByteBuffer buildSllaOption() throws Exception {
        return LlaOption.build((byte) ICMPV6_ND_OPTION_SLLA, ROUTER_MAC);
    }

    private static ByteBuffer buildRaPacket(short lifetime, ByteBuffer... options)
            throws Exception {
        final MacAddress dstMac =
                NetworkStackUtils.ipv6MulticastToEthernetMulticast(IPV6_ADDR_ALL_ROUTERS_MULTICAST);
        return Ipv6Utils.buildRaPacket(ROUTER_MAC /* srcMac */, dstMac,
                ROUTER_LINK_LOCAL /* srcIp */, IPV6_ADDR_ALL_NODES_MULTICAST /* dstIp */,
                (byte) 0 /* M=0, O=0 */, lifetime, 0 /* Reachable time, unspecified */,
                100 /* Retrans time 100ms */, options);
    }

    private static ByteBuffer buildRaPacket(ByteBuffer... options) throws Exception {
        return buildRaPacket((short) 1800, options);
    }

    private void disableIpv6ProvisioningDelays() throws Exception {
        // Speed up the test by disabling DAD and removing router_solicitation_delay.
        // We don't need to restore the default value because the interface is removed in tearDown.
        // TODO: speed up further by not waiting for RS but keying off first IPv6 packet.
        mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "router_solicitation_delay", "0");
        mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "dad_transmits", "0");
    }

    private void assertHasAddressThat(String msg, LinkProperties lp,
            Predicate<LinkAddress> condition) {
        for (LinkAddress addr : lp.getLinkAddresses()) {
            if (condition.test(addr)) {
                return;
            }
        }
        fail(msg + " not found in: " + lp);
    }

    private boolean hasFlag(LinkAddress addr, int flag) {
        return (addr.getFlags() & flag) == flag;
    }

    private boolean isPrivacyAddress(LinkAddress addr) {
        return addr.isGlobalPreferred() && hasFlag(addr, IFA_F_TEMPORARY);
    }

    private boolean isStablePrivacyAddress(LinkAddress addr) {
        // The Q netd does not understand the IFA_F_STABLE_PRIVACY flag.
        // See r.android.com/1295670.
        final int flag = (mIsNetlinkEventParseEnabled || ShimUtils.isAtLeastR())
                ? IFA_F_STABLE_PRIVACY : 0;
        return addr.isGlobalPreferred() && hasFlag(addr, flag);
    }

    private LinkProperties doIpv6OnlyProvisioning() throws Exception {
        final InOrder inOrder = inOrder(mCb);
        final ByteBuffer pio = buildPioOption(3600, 1800, "2001:db8:1::/64");
        final ByteBuffer rdnss = buildRdnssOption(3600, IPV6_OFF_LINK_DNS_SERVER);
        final ByteBuffer slla = buildSllaOption();
        final ByteBuffer ra = buildRaPacket(pio, rdnss, slla);

        return doIpv6OnlyProvisioning(inOrder, ra);
    }

    private LinkProperties doIpv6OnlyProvisioning(InOrder inOrder, ByteBuffer ra) throws Exception {
        waitForRouterSolicitation();
        mPacketReader.sendResponse(ra);

        // The lambda below needs to write a LinkProperties to a local variable, but lambdas cannot
        // write to non-final local variables. So declare a final variable to write to.
        final AtomicReference<LinkProperties> lpRef = new AtomicReference<>();

        ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verifyWithTimeout(inOrder, mCb).onProvisioningSuccess(captor.capture());
        lpRef.set(captor.getValue());

        // Sometimes provisioning completes as soon as the link-local and the stable address appear,
        // before the privacy address appears. If so, wait here for the LinkProperties update that
        // contains all three address. Otherwise, future calls to verify() might get confused.
        if (captor.getValue().getLinkAddresses().size() == 2) {
            verifyWithTimeout(inOrder, mCb).onLinkPropertiesChange(argThat(lp -> {
                lpRef.set(lp);
                return lp.getLinkAddresses().size() == 3;
            }));
        }

        LinkProperties lp = lpRef.get();
        assertEquals("Should have 3 IPv6 addresses after provisioning: " + lp,
                3, lp.getLinkAddresses().size());
        assertHasAddressThat("link-local address", lp, x -> x.getAddress().isLinkLocalAddress());
        assertHasAddressThat("privacy address", lp, this::isPrivacyAddress);
        assertHasAddressThat("stable privacy address", lp, this::isStablePrivacyAddress);

        return lp;
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRaRdnss() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);

        InOrder inOrder = inOrder(mCb);
        ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);

        final String dnsServer = "2001:4860:4860::64";
        final String lowlifeDnsServer = "2001:4860:4860::6464";

        final ByteBuffer pio = buildPioOption(600, 300, "2001:db8:1::/64");
        ByteBuffer rdnss1 = buildRdnssOption(60, lowlifeDnsServer);
        ByteBuffer rdnss2 = buildRdnssOption(600, dnsServer);
        ByteBuffer ra = buildRaPacket(pio, rdnss1, rdnss2);

        LinkProperties lp = doIpv6OnlyProvisioning(inOrder, ra);

        // Expect that DNS servers with lifetimes below CONFIG_MIN_RDNSS_LIFETIME are not accepted.
        assertNotNull(lp);
        assertEquals(1, lp.getDnsServers().size());
        assertTrue(lp.getDnsServers().contains(InetAddress.getByName(dnsServer)));

        // If the RDNSS lifetime is above the minimum, the DNS server is accepted.
        rdnss1 = buildRdnssOption(68, lowlifeDnsServer);
        ra = buildRaPacket(pio, rdnss1, rdnss2);
        mPacketReader.sendResponse(ra);
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(captor.capture());
        lp = captor.getValue();
        assertNotNull(lp);
        assertEquals(2, lp.getDnsServers().size());
        assertTrue(lp.getDnsServers().contains(InetAddress.getByName(dnsServer)));
        assertTrue(lp.getDnsServers().contains(InetAddress.getByName(lowlifeDnsServer)));

        // Expect that setting RDNSS lifetime of 0 causes loss of provisioning.
        rdnss1 = buildRdnssOption(0, dnsServer);
        rdnss2 = buildRdnssOption(0, lowlifeDnsServer);
        ra = buildRaPacket(pio, rdnss1, rdnss2);
        mPacketReader.sendResponse(ra);

        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(captor.capture());
        lp = captor.getValue();
        assertNotNull(lp);
        assertEquals(0, lp.getDnsServers().size());
        reset(mCb);
    }

    private void runRaRdnssIpv6LinkLocalDnsTest(boolean isIpv6LinkLocalDnsAccepted)
            throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv4()
                .build();
        setFeatureEnabled(NetworkStackUtils.IPCLIENT_ACCEPT_IPV6_LINK_LOCAL_DNS_VERSION,
                isIpv6LinkLocalDnsAccepted /* default value */);
        startIpClientProvisioning(config);

        final ByteBuffer pio = buildPioOption(600, 300, "2001:db8:1::/64");
        // put an IPv6 link-local DNS server
        final ByteBuffer rdnss = buildRdnssOption(600, ROUTER_LINK_LOCAL.getHostAddress());
        // put SLLA option to avoid address resolution for "fe80::1"
        final ByteBuffer slla = buildSllaOption();
        final ByteBuffer ra = buildRaPacket(pio, rdnss, slla);

        waitForRouterSolicitation();
        mPacketReader.sendResponse(ra);
    }

    @Test
    public void testRaRdnss_Ipv6LinkLocalDns() throws Exception {
        runRaRdnssIpv6LinkLocalDnsTest(true /* isIpv6LinkLocalDnsAccepted */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertEquals(1, lp.getDnsServers().size());
        assertEquals(ROUTER_LINK_LOCAL, (Inet6Address) lp.getDnsServers().get(0));
        assertTrue(lp.isIpv6Provisioned());
    }

    @Test
    public void testRaRdnss_disableIpv6LinkLocalDns() throws Exception {
        // Only run the test when the flag of parsing netlink events is enabled, feature flag
        // "ipclient_accept_ipv6_link_local_dns" doesn't affect the legacy code.
        assumeTrue(mIsNetlinkEventParseEnabled);
        runRaRdnssIpv6LinkLocalDnsTest(false /* isIpv6LinkLocalDnsAccepted */);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(lp -> {
            return lp.hasGlobalIpv6Address()
                    && lp.hasIpv6DefaultRoute()
                    && !lp.hasIpv6DnsServer();
        }));
        verify(mCb, never()).onProvisioningSuccess(any());
    }

    private void expectNat64PrefixUpdate(InOrder inOrder, IpPrefix expected) throws Exception {
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(
                argThat(lp -> Objects.equals(expected, lp.getNat64Prefix())));

    }

    private void expectNoNat64PrefixUpdate(InOrder inOrder, IpPrefix unchanged) throws Exception {
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS).times(0)).onLinkPropertiesChange(argThat(
                lp -> !Objects.equals(unchanged, lp.getNat64Prefix())));

    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.Q)
    @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testPref64Option() throws Exception {
        assumeTrue(ConstantsShim.VERSION > Build.VERSION_CODES.Q);

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);

        final IpPrefix prefix = new IpPrefix("64:ff9b::/96");
        final IpPrefix otherPrefix = new IpPrefix("2001:db8:64::/96");

        final ByteBuffer pio = buildPioOption(600, 300, "2001:db8:1::/64");
        ByteBuffer rdnss = buildRdnssOption(600, IPV6_OFF_LINK_DNS_SERVER);
        ByteBuffer pref64 = new StructNdOptPref64(prefix, 600).toByteBuffer();
        ByteBuffer ra = buildRaPacket(pio, rdnss, pref64);

        // The NAT64 prefix might be detected before or after provisioning success.
        // Don't test order between these two events.
        LinkProperties lp = doIpv6OnlyProvisioning(null /*inOrder*/, ra);
        expectAlarmSet(null /*inOrder*/, "PREF64", 600);

        // From now on expect events in order.
        InOrder inOrder = inOrder(mCb, mAlarm);
        if (lp.getNat64Prefix() != null) {
            assertEquals(prefix, lp.getNat64Prefix());
        } else {
            expectNat64PrefixUpdate(inOrder, prefix);
        }

        // Increase the lifetime and expect the prefix not to change.
        pref64 = new StructNdOptPref64(prefix, 1800).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64);
        mPacketReader.sendResponse(ra);
        OnAlarmListener pref64Alarm = expectAlarmSet(inOrder, "PREF64", 1800);
        expectNoNat64PrefixUpdate(inOrder, prefix);
        reset(mCb, mAlarm);

        // Reduce the lifetime and expect to reschedule expiry.
        pref64 = new StructNdOptPref64(prefix, 1500).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64);
        mPacketReader.sendResponse(ra);
        pref64Alarm = expectAlarmSet(inOrder, "PREF64", 1496);
        expectNoNat64PrefixUpdate(inOrder, prefix);
        reset(mCb, mAlarm);

        // Withdraw the prefix and expect it to be set to null.
        pref64 = new StructNdOptPref64(prefix, 0).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64);
        mPacketReader.sendResponse(ra);
        expectAlarmCancelled(inOrder, pref64Alarm);
        expectNat64PrefixUpdate(inOrder, null);
        reset(mCb, mAlarm);

        // Re-announce the prefix.
        pref64 = new StructNdOptPref64(prefix, 600).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64);
        mPacketReader.sendResponse(ra);
        expectAlarmSet(inOrder, "PREF64", 600);
        expectNat64PrefixUpdate(inOrder, prefix);
        reset(mCb, mAlarm);

        // Announce two prefixes. Don't expect any update because if there is already a NAT64
        // prefix, any new prefix is ignored.
        ByteBuffer otherPref64 = new StructNdOptPref64(otherPrefix, 1200).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64, otherPref64);
        mPacketReader.sendResponse(ra);
        expectAlarmSet(inOrder, "PREF64", 600);
        expectNoNat64PrefixUpdate(inOrder, prefix);
        reset(mCb, mAlarm);

        // Withdraw the old prefix and continue to announce the new one. Expect a prefix change.
        pref64 = new StructNdOptPref64(prefix, 0).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64, otherPref64);
        mPacketReader.sendResponse(ra);
        expectAlarmCancelled(inOrder, pref64Alarm);
        // Need a different OnAlarmListener local variable because posting it to the handler in the
        // lambda below requires it to be final.
        final OnAlarmListener lastAlarm = expectAlarmSet(inOrder, "PREF64", 1200);
        expectNat64PrefixUpdate(inOrder, otherPrefix);
        reset(mCb, mAlarm);

        // Simulate prefix expiry.
        mIpc.getHandler().post(() -> lastAlarm.onAlarm());
        expectAlarmCancelled(inOrder, pref64Alarm);
        expectNat64PrefixUpdate(inOrder, null);

        // Announce a non-/96 prefix and expect it to be ignored.
        IpPrefix invalidPrefix = new IpPrefix("64:ff9b::/64");
        pref64 = new StructNdOptPref64(invalidPrefix, 1200).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64);
        mPacketReader.sendResponse(ra);
        expectNoNat64PrefixUpdate(inOrder, invalidPrefix);

        // Re-announce the prefix.
        pref64 = new StructNdOptPref64(prefix, 600).toByteBuffer();
        ra = buildRaPacket(pio, rdnss, pref64);
        mPacketReader.sendResponse(ra);
        final OnAlarmListener clearAlarm = expectAlarmSet(inOrder, "PREF64", 600);
        expectNat64PrefixUpdate(inOrder, prefix);
        reset(mCb, mAlarm);

        // Check that the alarm is cancelled when IpClient is stopped.
        mIpc.stop();
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);
        expectAlarmCancelled(inOrder, clearAlarm);
        expectNat64PrefixUpdate(inOrder, null);

        // Check that even if the alarm was already in the message queue while it was cancelled, it
        // is safely ignored.
        mIpc.getHandler().post(() -> clearAlarm.onAlarm());
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);
    }

    private void waitForAddressViaNetworkObserver(final String iface, final String addr1,
            final String addr2, int prefixLength) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // Add two IPv4 addresses to the specified interface, and proceed when the NetworkObserver
        // has seen the second one. This ensures that every other NetworkObserver registered with
        // mNetworkObserverRegistry - in particular, IpClient's - has seen the addition of the first
        // address.
        final LinkAddress trigger = new LinkAddress(addr2 + "/" + prefixLength);
        NetworkObserver observer = new NetworkObserver() {
            @Override
            public void onInterfaceAddressUpdated(LinkAddress address, String ifName) {
                if (ifName.equals(iface) && address.isSameAddressAs(trigger)) {
                    latch.countDown();
                }
            }
        };

        mNetworkObserverRegistry.registerObserverForNonblockingCallback(observer);
        try {
            mNetd.interfaceAddAddress(iface, addr1, prefixLength);
            mNetd.interfaceAddAddress(iface, addr2, prefixLength);
            assertTrue("Trigger IP address " + addr2 + " not seen after " + TEST_TIMEOUT_MS + "ms",
                    latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } finally {
            mNetworkObserverRegistry.unregisterObserver(observer);
        }
    }

    private void addIpAddressAndWaitForIt(final String iface) throws Exception {
        final String addr1 = "192.0.2.99";
        final String addr2 = "192.0.2.3";
        final int prefixLength = 26;

        if (!mIsNetlinkEventParseEnabled) {
            waitForAddressViaNetworkObserver(iface, addr1, addr2, prefixLength);
        } else {
            // IpClient gets IP addresses directly from netlink instead of from netd, unnecessary
            // to rely on the NetworkObserver callbacks to confirm new added address update. Just
            // add the addresses directly and wait to see if IpClient has seen the address
            mNetd.interfaceAddAddress(iface, addr1, prefixLength);
            mNetd.interfaceAddAddress(iface, addr2, prefixLength);
        }

        // Wait for IpClient to process the addition of the address.
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);
    }

    private void doIPv4OnlyProvisioningAndExitWithLeftAddress() throws Exception {
        final long currentTime = System.currentTimeMillis();
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);

        // Stop IpClient and expect a final LinkProperties callback with an empty LP.
        mIIpClient.stop();
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(
                x -> x.getAddresses().size() == 0
                        && x.getRoutes().size() == 0
                        && x.getDnsServers().size() == 0));
        reset(mCb);

        // Pretend that something else (e.g., Tethering) used the interface and left an IP address
        // configured on it. When IpClient starts, it must clear this address before proceeding.
        // The address must be noticed before startProvisioning is called, or IpClient will
        // immediately declare provisioning success due to the presence of an IPv4 address.
        // The address must be IPv4 because IpClient clears IPv6 addresses on startup.
        addIpAddressAndWaitForIt(mIfaceName);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testIpClientClearingIpAddressState() throws Exception {
        doIPv4OnlyProvisioningAndExitWithLeftAddress();

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .build();
        startIpClientProvisioning(config);

        sendBasicRouterAdvertisement(true /*waitForRs*/);

        // Check that the IPv4 addresses configured earlier are not in LinkProperties...
        ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        assertFalse(captor.getValue().hasIpv4Address());

        // ... or configured on the interface.
        InterfaceConfigurationParcel cfg = mNetd.interfaceGetCfg(mIfaceName);
        assertEquals("0.0.0.0", cfg.ipv4Addr);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testIpClientClearingIpAddressState_enablePreconnection() throws Exception {
        doIPv4OnlyProvisioningAndExitWithLeftAddress();

        // Enter ClearingIpAddressesState to clear the remaining IPv4 addresses and transition to
        // PreconnectionState instead of RunningState.
        startIpClientProvisioning(false /* shouldReplyRapidCommitAck */,
                true /* isDhcpPreConnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        assertDiscoverPacketOnPreconnectionStart();

        // Force to enter RunningState.
        mIpc.notifyPreconnectionComplete(false /* abort */);
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_success() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(true /* shouldReplyRapidCommitAck */,
                false /* shouldAbortPreconnection */, false /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_SuccessWithoutRapidCommit() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(false /* shouldReplyRapidCommitAck */,
                false /* shouldAbortPreconnection */, false /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_Abort() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(true /* shouldReplyRapidCommitAck */,
                true /* shouldAbortPreconnection */, false /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_AbortWithoutRapiCommit() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(false /* shouldReplyRapidCommitAck */,
                true /* shouldAbortPreconnection */, false /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutBeforeAbort() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(true /* shouldReplyRapidCommitAck */,
                true /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                true /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutBeforeAbortWithoutRapidCommit()
            throws Exception {
        doIpClientProvisioningWithPreconnectionTest(false /* shouldReplyRapidCommitAck */,
                true /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                true /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutafterAbort() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(true /* shouldReplyRapidCommitAck */,
                true /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutAfterAbortWithoutRapidCommit() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(false /* shouldReplyRapidCommitAck */,
                true /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutBeforeSuccess() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(true /* shouldReplyRapidCommitAck */,
                false /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                true /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutBeforeSuccessWithoutRapidCommit()
            throws Exception {
        doIpClientProvisioningWithPreconnectionTest(false /* shouldReplyRapidCommitAck */,
                false /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                true /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutAfterSuccess() throws Exception {
        doIpClientProvisioningWithPreconnectionTest(true /* shouldReplyRapidCommitAck */,
                false /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_TimeoutAfterSuccessWithoutRapidCommit()
            throws Exception {
        doIpClientProvisioningWithPreconnectionTest(false /* shouldReplyRapidCommitAck */,
                false /* shouldAbortPreconnection */, true /* shouldFirePreconnectionTimeout */,
                false /* timeoutBeforePreconnectionComplete */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpClientPreconnection_WithoutLayer2InfoWhenStartingProv() throws Exception {
        // For FILS connection, current bssid (also l2key and cluster) is still null when
        // starting provisioning since the L2 link hasn't been established yet. Ensure that
        // IpClient won't crash even if initializing an Layer2Info class with null members.
        ProvisioningConfiguration.Builder prov = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv6()
                .withPreconnection()
                .withLayer2Information(new Layer2Information(null /* l2key */, null /* cluster */,
                        null /* bssid */));

        startIpClientProvisioning(prov.build());
        assertDiscoverPacketOnPreconnectionStart();
        verify(mCb).setNeighborDiscoveryOffload(true);

        // Force IpClient transition to RunningState from PreconnectionState.
        mIIpClient.notifyPreconnectionComplete(false /* success */);
        HandlerUtils.waitForIdle(mDependencies.mDhcpClient.getHandler(), TEST_TIMEOUT_MS);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);
    }

    @Test
    @SignatureRequiredTest(reason = "needs mocked alarm and access to IpClient handler thread")
    public void testDhcpClientPreconnection_DelayedAbortAndTransitToStoppedState()
            throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withPreconnection()
                .build();
        setDhcpFeatures(false /* shouldReplyRapidCommitAck */,
                false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);
        assertDiscoverPacketOnPreconnectionStart();

        // IpClient is in the PreconnectingState, simulate provisioning timeout event
        // and force IpClient state machine transit to StoppingState.
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        final OnAlarmListener alarm = expectAlarmSet(null /* inOrder */, "TIMEOUT", 18,
                mIpc.getHandler());
        mIpc.getHandler().post(() -> alarm.onAlarm());

        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertEquals(mIfaceName, lp.getInterfaceName());
        assertEquals(0, lp.getLinkAddresses().size());
        assertEquals(0, lp.getRoutes().size());
        assertEquals(0, lp.getMtu());
        assertEquals(0, lp.getDnsServers().size());

        // Send preconnection abort message, but IpClient should ignore it at this moment and
        // transit to StoppedState finally.
        mIpc.notifyPreconnectionComplete(false /* abort */);
        mIpc.stop();
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);

        reset(mCb);

        // Start provisioning again to verify IpClient can process CMD_START correctly at
        // StoppedState.
        startIpClientProvisioning(false /* shouldReplyRapidCommitAck */,
                false /* isPreConnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        final DhcpPacket discover = getNextDhcpPacket();
        assertTrue(discover instanceof DhcpDiscoverPacket);
    }

    @Test
    public void testDhcpDecline_conflictByArpReply() throws Exception {
        doIpAddressConflictDetectionTest(true /* causeIpAddressConflict */,
                false /* shouldReplyRapidCommitAck */, true /* isDhcpIpConflictDetectEnabled */,
                true /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_conflictByArpProbe() throws Exception {
        doIpAddressConflictDetectionTest(true /* causeIpAddressConflict */,
                false /* shouldReplyRapidCommitAck */, true /* isDhcpIpConflictDetectEnabled */,
                false /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_EnableFlagWithoutIpConflict() throws Exception {
        doIpAddressConflictDetectionTest(false /* causeIpAddressConflict */,
                false /* shouldReplyRapidCommitAck */, true /* isDhcpIpConflictDetectEnabled */,
                false /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_WithoutIpConflict() throws Exception {
        doIpAddressConflictDetectionTest(false /* causeIpAddressConflict */,
                false /* shouldReplyRapidCommitAck */, false /* isDhcpIpConflictDetectEnabled */,
                false /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_WithRapidCommitWithoutIpConflict() throws Exception {
        doIpAddressConflictDetectionTest(false /* causeIpAddressConflict */,
                true /* shouldReplyRapidCommitAck */, false /* isDhcpIpConflictDetectEnabled */,
                false /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_WithRapidCommitConflictByArpReply() throws Exception {
        doIpAddressConflictDetectionTest(true /* causeIpAddressConflict */,
                true /* shouldReplyRapidCommitAck */, true /* isDhcpIpConflictDetectEnabled */,
                true /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_WithRapidCommitConflictByArpProbe() throws Exception {
        doIpAddressConflictDetectionTest(true /* causeIpAddressConflict */,
                true /* shouldReplyRapidCommitAck */, true /* isDhcpIpConflictDetectEnabled */,
                false /* shouldResponseArpReply */);
    }

    @Test
    public void testDhcpDecline_EnableFlagWithRapidCommitWithoutIpConflict() throws Exception {
        doIpAddressConflictDetectionTest(false /* causeIpAddressConflict */,
                true /* shouldReplyRapidCommitAck */, true /* isDhcpIpConflictDetectEnabled */,
                false /* shouldResponseArpReply */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testHostname_enableConfig() throws Exception {
        mDependencies.setHostnameConfiguration(true /* isHostnameConfigurationEnabled */,
                TEST_HOST_NAME);

        final long currentTime = System.currentTimeMillis();
        final List<DhcpPacket> sentPackets = performDhcpHandshake(true /* isSuccessLease */,
                TEST_LEASE_DURATION_S, false /* isDhcpRapidCommitEnabled */, TEST_DEFAULT_MTU,
                false /* isDhcpIpConflictDetectEnabled */);

        assertEquals(2, sentPackets.size());
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertHostname(true, TEST_HOST_NAME, TEST_HOST_NAME_TRANSLITERATION, sentPackets);
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testHostname_disableConfig() throws Exception {
        mDependencies.setHostnameConfiguration(false /* isHostnameConfigurationEnabled */,
                TEST_HOST_NAME);

        final long currentTime = System.currentTimeMillis();
        final List<DhcpPacket> sentPackets = performDhcpHandshake(true /* isSuccessLease */,
                TEST_LEASE_DURATION_S, false /* isDhcpRapidCommitEnabled */, TEST_DEFAULT_MTU,
                false /* isDhcpIpConflictDetectEnabled */);

        assertEquals(2, sentPackets.size());
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertHostname(false, TEST_HOST_NAME, TEST_HOST_NAME_TRANSLITERATION, sentPackets);
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testHostname_enableConfigWithNullHostname() throws Exception {
        mDependencies.setHostnameConfiguration(true /* isHostnameConfigurationEnabled */,
                null /* hostname */);

        final long currentTime = System.currentTimeMillis();
        final List<DhcpPacket> sentPackets = performDhcpHandshake(true /* isSuccessLease */,
                TEST_LEASE_DURATION_S, false /* isDhcpRapidCommitEnabled */, TEST_DEFAULT_MTU,
                false /* isDhcpIpConflictDetectEnabled */);

        assertEquals(2, sentPackets.size());
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertHostname(true, null /* hostname */, null /* hostnameAfterTransliteration */,
                sentPackets);
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    private LinkProperties runDhcpClientCaptivePortalApiTest(boolean featureEnabled,
            boolean serverSendsOption) throws Exception {
        startIpClientProvisioning(false /* shouldReplyRapidCommitAck */,
                false /* isPreConnectionEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        final DhcpPacket discover = getNextDhcpPacket();
        assertTrue(discover instanceof DhcpDiscoverPacket);
        assertEquals(featureEnabled, discover.hasRequestedParam(DhcpPacket.DHCP_CAPTIVE_PORTAL));

        // Send Offer and handle Request -> Ack
        final String serverSentUrl = serverSendsOption ? TEST_CAPTIVE_PORTAL_URL : null;
        mPacketReader.sendResponse(buildDhcpOfferPacket(discover, CLIENT_ADDR,
                TEST_LEASE_DURATION_S, (short) TEST_DEFAULT_MTU, serverSentUrl));
        final int testMtu = 1345;
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */, testMtu, serverSentUrl);

        final Uri expectedUrl = featureEnabled && serverSendsOption
                ? Uri.parse(TEST_CAPTIVE_PORTAL_URL) : null;
        // LinkProperties will be updated multiple times. Wait for it to contain DHCP-obtained info,
        // such as MTU.
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS).atLeastOnce()).onLinkPropertiesChange(
                argThat(lp -> lp.getMtu() == testMtu));

        // Ensure that the URL was set as expected in the callbacks.
        // Can't verify the URL up to Q as there is no such attribute in LinkProperties.
        if (!ShimUtils.isAtLeastR()) return null;
        verify(mCb, atLeastOnce()).onLinkPropertiesChange(captor.capture());
        final LinkProperties expectedLp = captor.getAllValues().stream().findFirst().get();
        assertNotNull(expectedLp);
        assertEquals(expectedUrl, expectedLp.getCaptivePortalApiUrl());
        return expectedLp;
    }

    @Test
    public void testDhcpClientCaptivePortalApiEnabled() throws Exception {
        // Only run the test on platforms / builds where the API is enabled
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        runDhcpClientCaptivePortalApiTest(true /* featureEnabled */, true /* serverSendsOption */);
    }

    @Test
    public void testDhcpClientCaptivePortalApiEnabled_NoUrl() throws Exception {
        // Only run the test on platforms / builds where the API is enabled
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        runDhcpClientCaptivePortalApiTest(true /* featureEnabled */, false /* serverSendsOption */);
    }

    @Test
    public void testDhcpClientCaptivePortalApiEnabled_ParcelSensitiveFields() throws Exception {
        // Only run the test on platforms / builds where the API is enabled
        assumeTrue(CaptivePortalDataShimImpl.isSupported());
        LinkProperties lp = runDhcpClientCaptivePortalApiTest(true /* featureEnabled */,
                true /* serverSendsOption */);

        // Integration test process runs in the same process with network stack module, there
        // won't be any IPC call happened on IpClientCallbacks, manually run parcelingRoundTrip
        // to parcel and unparcel the LinkProperties to simulate what happens during the binder
        // call. In this case lp should contain the senstive data but mParcelSensitiveFields is
        // false after round trip.
        if (useNetworkStackSignature()) {
            lp = parcelingRoundTrip(lp);
        }
        final Uri expectedUrl = Uri.parse(TEST_CAPTIVE_PORTAL_URL);
        assertEquals(expectedUrl, lp.getCaptivePortalApiUrl());

        // Parcel and unparcel the captured LinkProperties, mParcelSensitiveFields is false,
        // CaptivePortalApiUrl should be null after parceling round trip.
        final LinkProperties unparceled = parcelingRoundTrip(lp);
        assertNull(unparceled.getCaptivePortalApiUrl());
    }

    @Test
    public void testDhcpClientCaptivePortalApiDisabled() throws Exception {
        // Only run the test on platforms / builds where the API is disabled
        assumeFalse(CaptivePortalDataShimImpl.isSupported());
        runDhcpClientCaptivePortalApiTest(false /* featureEnabled */, true /* serverSendsOption */);
    }

    private ScanResultInfo makeScanResultInfo(final int id, final String ssid,
            final String bssid, final byte[] oui, final byte type, final byte[] data) {
        final ByteBuffer payload = ByteBuffer.allocate(4 + data.length);
        payload.put(oui);
        payload.put(type);
        payload.put(data);
        payload.flip();
        final ScanResultInfo.InformationElement ie =
                new ScanResultInfo.InformationElement(id /* IE id */, payload);
        return new ScanResultInfo(ssid, bssid, Collections.singletonList(ie));
    }

    private ScanResultInfo makeScanResultInfo(final int id, final byte[] oui, final byte type) {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        return makeScanResultInfo(id, TEST_DEFAULT_SSID, TEST_DEFAULT_BSSID, oui, type, data);
    }

    private ScanResultInfo makeScanResultInfo(final String ssid, final String bssid) {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        return makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, ssid, bssid, TEST_AP_OUI,
                (byte) 0x06, data);
    }

    private void doUpstreamHotspotDetectionTest(final int id, final String displayName,
            final String ssid, final byte[] oui, final byte type, final byte[] data,
            final boolean expectMetered) throws Exception {
        final ScanResultInfo info = makeScanResultInfo(id, ssid, TEST_DEFAULT_BSSID, oui, type,
                data);
        final long currentTime = System.currentTimeMillis();
        final List<DhcpPacket> sentPackets = performDhcpHandshake(true /* isSuccessLease */,
                TEST_LEASE_DURATION_S, false /* isDhcpRapidCommitEnabled */, TEST_DEFAULT_MTU,
                false /* isDhcpIpConflictDetectEnabled */,
                null /* captivePortalApiUrl */, displayName, info /* scanResultInfo */,
                null /* layer2Info */);
        assertEquals(2, sentPackets.size());
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));

        ArgumentCaptor<DhcpResultsParcelable> captor =
                ArgumentCaptor.forClass(DhcpResultsParcelable.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onNewDhcpResults(captor.capture());
        final DhcpResultsParcelable lease = captor.getValue();
        assertNotNull(lease);
        assertEquals(CLIENT_ADDR, lease.baseConfiguration.getIpAddress().getAddress());
        assertEquals(SERVER_ADDR, lease.baseConfiguration.getGateway());
        assertEquals(1, lease.baseConfiguration.getDnsServers().size());
        assertTrue(lease.baseConfiguration.getDnsServers().contains(SERVER_ADDR));
        assertEquals(SERVER_ADDR, InetAddresses.parseNumericAddress(lease.serverAddress));
        assertEquals(TEST_DEFAULT_MTU, lease.mtu);

        if (expectMetered) {
            assertEquals(lease.vendorInfo, DhcpPacket.VENDOR_INFO_ANDROID_METERED);
        } else {
            assertNull(lease.vendorInfo);
        }

        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    @Test
    public void testUpstreamHotspotDetection() throws Exception {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        doUpstreamHotspotDetectionTest(TEST_VENDOR_SPECIFIC_IE_ID, "\"ssid\"", "ssid",
                new byte[] { (byte) 0x00, (byte) 0x17, (byte) 0xF2 }, (byte) 0x06, data,
                true /* expectMetered */);
    }

    @Test
    public void testUpstreamHotspotDetection_incorrectIeId() throws Exception {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        doUpstreamHotspotDetectionTest(0xdc, "\"ssid\"", "ssid",
                new byte[] { (byte) 0x00, (byte) 0x17, (byte) 0xF2 }, (byte) 0x06, data,
                false /* expectMetered */);
    }

    @Test
    public void testUpstreamHotspotDetection_incorrectOUI() throws Exception {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        doUpstreamHotspotDetectionTest(TEST_VENDOR_SPECIFIC_IE_ID, "\"ssid\"", "ssid",
                new byte[] { (byte) 0x00, (byte) 0x1A, (byte) 0x11 }, (byte) 0x06, data,
                false /* expectMetered */);
    }

    @Test
    public void testUpstreamHotspotDetection_incorrectSsid() throws Exception {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        doUpstreamHotspotDetectionTest(TEST_VENDOR_SPECIFIC_IE_ID, "\"another ssid\"", "ssid",
                new byte[] { (byte) 0x00, (byte) 0x17, (byte) 0xF2 }, (byte) 0x06, data,
                false /* expectMetered */);
    }

    @Test
    public void testUpstreamHotspotDetection_incorrectType() throws Exception {
        byte[] data = new byte[10];
        new Random().nextBytes(data);
        doUpstreamHotspotDetectionTest(TEST_VENDOR_SPECIFIC_IE_ID, "\"ssid\"", "ssid",
                new byte[] { (byte) 0x00, (byte) 0x17, (byte) 0xF2 }, (byte) 0x0a, data,
                false /* expectMetered */);
    }

    @Test
    public void testUpstreamHotspotDetection_zeroLengthData() throws Exception {
        byte[] data = new byte[0];
        doUpstreamHotspotDetectionTest(TEST_VENDOR_SPECIFIC_IE_ID, "\"ssid\"", "ssid",
                new byte[] { (byte) 0x00, (byte) 0x17, (byte) 0xF2 }, (byte) 0x06, data,
                true /* expectMetered */);
    }

    private void forceLayer2Roaming() throws Exception {
        final Layer2InformationParcelable roamingInfo = new Layer2InformationParcelable();
        roamingInfo.bssid = MacAddress.fromString(TEST_DHCP_ROAM_BSSID);
        roamingInfo.l2Key = TEST_DHCP_ROAM_L2KEY;
        roamingInfo.cluster = TEST_DHCP_ROAM_CLUSTER;
        mIIpClient.updateLayer2Information(roamingInfo);
    }

    private void assertDhcpRequestForReacquire(final DhcpPacket packet) {
        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mClientIp, CLIENT_ADDR);    // client IP
        assertNull(packet.mRequestedIp);                // requested IP option
        assertNull(packet.mServerIdentifier);           // server ID
    }

    private void doDhcpRoamingTest(final boolean hasMismatchedIpAddress, final String displayName,
            final MacAddress bssid, final boolean expectRoaming,
            final boolean shouldReplyNakOnRoam) throws Exception {
        long currentTime = System.currentTimeMillis();
        final Layer2Information layer2Info = new Layer2Information(TEST_L2KEY, TEST_CLUSTER, bssid);

        doAnswer(invocation -> {
            // we don't rely on the Init-Reboot state to renew previous cached IP lease.
            // Just return null and force state machine enter INIT state.
            final String l2Key = invocation.getArgument(0);
            ((OnNetworkAttributesRetrievedListener) invocation.getArgument(1))
                    .onNetworkAttributesRetrieved(new Status(SUCCESS), l2Key, null);
            return null;
        }).when(mIpMemoryStore).retrieveNetworkAttributes(eq(TEST_L2KEY), any());

        mDependencies.setHostnameConfiguration(true /* isHostnameConfigurationEnabled */,
                null /* hostname */);
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* isDhcpRapidCommitEnabled */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */,
                null /* captivePortalApiUrl */, displayName, null /* scanResultInfo */,
                layer2Info);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);

        // simulate the roaming by updating bssid.
        forceLayer2Roaming();

        currentTime = System.currentTimeMillis();
        reset(mIpMemoryStore);
        reset(mCb);
        if (!expectRoaming) {
            assertIpMemoryNeverStoreNetworkAttributes();
            return;
        }
        // check DHCPREQUEST broadcast sent to renew IP address.
        final DhcpPacket packet = getNextDhcpPacket();
        assertDhcpRequestForReacquire(packet);

        final ByteBuffer packetBuffer = shouldReplyNakOnRoam
                ? buildDhcpNakPacket(packet, "request IP on a wrong subnet")
                : buildDhcpAckPacket(packet,
                        hasMismatchedIpAddress ? CLIENT_ADDR_NEW : CLIENT_ADDR,
                        TEST_LEASE_DURATION_S, (short) TEST_DEFAULT_MTU,
                        false /* rapidCommit */, null /* captivePortalApiUrl */);
        mPacketReader.sendResponse(packetBuffer);
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);

        if (shouldReplyNakOnRoam) {
            ArgumentCaptor<ReachabilityLossInfoParcelable> lossInfoCaptor =
                    ArgumentCaptor.forClass(ReachabilityLossInfoParcelable.class);
            verify(mCb, timeout(TEST_TIMEOUT_MS)).onReachabilityFailure(lossInfoCaptor.capture());
            assertEquals(ReachabilityLossReason.ROAM, lossInfoCaptor.getValue().reason);

            // IPv4 address will be still deleted when DhcpClient state machine exits from
            // DhcpHaveLeaseState, a following onProvisioningFailure will be thrown then.
            // Also check DhcpClient won't send any DHCPDISCOVER packet.
            verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(any());
            assertNull(getNextDhcpPacket(TEST_TIMEOUT_MS));
            verify(mCb, never()).onNewDhcpResults(any());
        } else if (hasMismatchedIpAddress) {
            ArgumentCaptor<DhcpResultsParcelable> resultsCaptor =
                    ArgumentCaptor.forClass(DhcpResultsParcelable.class);
            verify(mCb, timeout(TEST_TIMEOUT_MS)).onNewDhcpResults(resultsCaptor.capture());
            final DhcpResultsParcelable lease = resultsCaptor.getValue();
            assertNull(lease);

            // DhcpClient rolls back to StoppedState instead of INIT state after calling
            // notifyFailure, DHCPDISCOVER should not be sent out.
            assertNull(getNextDhcpPacket(TEST_TIMEOUT_MS));
        } else {
            assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime,
                    TEST_DEFAULT_MTU);
        }
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpRoaming() throws Exception {
        doDhcpRoamingTest(false /* hasMismatchedIpAddress */, "\"0001docomo\"" /* display name */,
                MacAddress.fromString(TEST_DEFAULT_BSSID), true /* expectRoaming */,
                false /* shouldReplyNakOnRoam */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpRoaming_invalidBssid() throws Exception {
        doDhcpRoamingTest(false /* hasMismatchedIpAddress */, "\"0001docomo\"" /* display name */,
                MacAddress.fromString(TEST_DHCP_ROAM_BSSID), false /* expectRoaming */,
                false/* shouldReplyNakOnRoam */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpRoaming_nullBssid() throws Exception {
        doDhcpRoamingTest(false /* hasMismatchedIpAddress */, "\"0001docomo\"" /* display name */,
                null /* BSSID */, false /* expectRoaming */, false /* shouldReplyNakOnRoam */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpRoaming_invalidDisplayName() throws Exception {
        doDhcpRoamingTest(false /* hasMismatchedIpAddress */, "\"test-ssid\"" /* display name */,
                MacAddress.fromString(TEST_DEFAULT_BSSID), false /* expectRoaming */,
                false /* shouldReplyNakOnRoam */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpRoaming_mismatchedLeasedIpAddress() throws Exception {
        doDhcpRoamingTest(true /* hasMismatchedIpAddress */, "\"0001docomo\"" /* display name */,
                MacAddress.fromString(TEST_DEFAULT_BSSID), true /* expectRoaming */,
                false /* shouldReplyNakOnRoam */);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDhcpRoaming_failureLeaseOnNak() throws Exception {
        doDhcpRoamingTest(false /* hasMismatchedIpAddress */, "\"0001docomo\"" /* display name */,
                MacAddress.fromString(TEST_DEFAULT_BSSID), true /* expectRoaming */,
                true /* shouldReplyNakOnRoam */);
    }

    private LinkProperties performDualStackProvisioning() throws Exception {
        final Inet6Address dnsServer =
                (Inet6Address) InetAddresses.parseNumericAddress(IPV6_OFF_LINK_DNS_SERVER);
        final ByteBuffer pio = buildPioOption(3600, 1800, "2001:db8:1::/64");
        final ByteBuffer rdnss = buildRdnssOption(3600, IPV6_OFF_LINK_DNS_SERVER);
        final ByteBuffer slla = buildSllaOption();
        final ByteBuffer ra = buildRaPacket(pio, rdnss, slla);

        return performDualStackProvisioning(ra, dnsServer);
    }

    private LinkProperties performDualStackProvisioning(final ByteBuffer ra,
            final InetAddress dnsServer) throws Exception {
        final InOrder inOrder = inOrder(mCb);
        final CompletableFuture<LinkProperties> lpFuture = new CompletableFuture<>();

        // Start IPv4 provisioning first and wait IPv4 provisioning to succeed, and then start
        // IPv6 provisioning, which is more realistic and avoid the flaky case of both IPv4 and
        // IPv6 provisioning complete at the same time.
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                true /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU, null /* serverSentUrl */);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(any());

        waitForRouterSolicitation();
        mPacketReader.sendResponse(ra);

        // Wait until we see both success IPv4 and IPv6 provisioning, then there would be 4
        // addresses in LinkProperties, they are IPv4 address, IPv6 link-local address, stable
        // privacy address and privacy address.
        verify(mCb, timeout(TEST_TIMEOUT_MS).atLeastOnce()).onLinkPropertiesChange(argThat(x -> {
            if (!x.isIpv4Provisioned() || !x.isIpv6Provisioned()) return false;
            if (x.getLinkAddresses().size() != 4) return false;
            lpFuture.complete(x);
            return true;
        }));

        final LinkProperties lp = lpFuture.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(lp);
        assertTrue(lp.getDnsServers().contains(dnsServer));
        assertTrue(lp.getDnsServers().contains(SERVER_ADDR));
        assertHasAddressThat("link-local address", lp, x -> x.getAddress().isLinkLocalAddress());
        assertHasAddressThat("privacy address", lp, this::isPrivacyAddress);
        assertHasAddressThat("stable privacy address", lp, this::isStablePrivacyAddress);

        return lp;
    }

    private LinkProperties doDualStackProvisioning() throws Exception {
        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .build();

        // Enable rapid commit to accelerate DHCP handshake to shorten test duration,
        // not strictly necessary.
        setDhcpFeatures(true /* isRapidCommitEnabled */, false /* isDhcpIpConflictDetectEnabled */);
        // Both signature and root tests can use this function to do dual-stack provisioning.
        if (useNetworkStackSignature()) {
            mIpc.startProvisioning(config);
        } else {
            mIIpClient.startProvisioning(config.toStableParcelable());
        }

        return performDualStackProvisioning();
    }

    private boolean hasRouteTo(@NonNull final LinkProperties lp, @NonNull final String prefix) {
        return hasRouteTo(lp, prefix, RTN_UNICAST);
    }

    private boolean hasRouteTo(@NonNull final LinkProperties lp, @NonNull final String prefix,
            int type) {
        for (RouteInfo r : lp.getRoutes()) {
            if (r.getDestination().equals(new IpPrefix(prefix))) return r.getType() == type;
        }
        return false;
    }

    private boolean hasIpv6AddressPrefixedWith(@NonNull final LinkProperties lp,
            @NonNull final IpPrefix prefix) {
        for (LinkAddress la : lp.getLinkAddresses()) {
            final InetAddress addr = la.getAddress();
            if ((addr instanceof Inet6Address) && !addr.isLinkLocalAddress()) {
                if (prefix.contains(addr)) return true;
            }
        }
        return false;
    }

    @Test
    @SignatureRequiredTest(reason = "Out of SLO flakiness")
    public void testIgnoreIpv6ProvisioningLoss_disableAcceptRaDefrtr() throws Exception {
        LinkProperties lp = doDualStackProvisioning();
        Log.d(TAG, "current LinkProperties: " + lp);

        final CompletableFuture<LinkProperties> lpFuture = new CompletableFuture<>();

        // Send RA with 0-lifetime and wait until all global IPv6 addresses, IPv6-related default
        // route and DNS servers have been removed, then verify if there is IPv4-only, IPv6 link
        // local address and route to fe80::/64 info left in the LinkProperties.
        sendRouterAdvertisementWithZeroRouterLifetime();
        verify(mCb, timeout(TEST_TIMEOUT_MS).atLeastOnce()).onLinkPropertiesChange(
                argThat(x -> {
                    // Only IPv4 provisioned and IPv6 link-local address
                    final boolean isIPv6LinkLocalAndIPv4OnlyProvisioned =
                            (x.getLinkAddresses().size() == 2
                                    && x.getDnsServers().size() == 1
                                    && x.getAddresses().get(0) instanceof Inet4Address
                                    && x.getDnsServers().get(0) instanceof Inet4Address);

                    if (!isIPv6LinkLocalAndIPv4OnlyProvisioned) return false;
                    lpFuture.complete(x);
                    return true;
                }));
        lp = lpFuture.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Log.d(TAG, "After receiving RA with 0 router lifetime, LinkProperties: " + lp);
        assertNotNull(lp);
        assertEquals(lp.getAddresses().get(0), CLIENT_ADDR);
        assertEquals(lp.getDnsServers().get(0), SERVER_ADDR);
        assertTrue(hasRouteTo(lp, IPV6_LINK_LOCAL_PREFIX)); // fe80::/64
        assertTrue(hasRouteTo(lp, IPV4_TEST_SUBNET_PREFIX)); // IPv4 directly-connected route
        assertTrue(hasRouteTo(lp, IPV4_ANY_ADDRESS_PREFIX)); // IPv4 default route
        assertTrue(lp.getAddresses().get(1).isLinkLocalAddress());

        clearInvocations(mCb);

        // Wait for RS after IPv6 stack has been restarted and reply with a normal RA to verify
        // that device gains the IPv6 provisioning without default route and off-link DNS server.
        sendBasicRouterAdvertisement(true /* waitForRs */);
        verify(mCb, timeout(TEST_TIMEOUT_MS).atLeastOnce()).onLinkPropertiesChange(argThat(
                x -> x.hasGlobalIpv6Address()
                        // IPv4, IPv6 link local, privacy and stable privacy
                        && x.getLinkAddresses().size() == 4
                        && !x.hasIpv6DefaultRoute()
                        && x.getDnsServers().size() == 1
                        && x.getDnsServers().get(0).equals(SERVER_ADDR)));
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDualStackProvisioning() throws Exception {
        doDualStackProvisioning();

        verify(mCb, never()).onProvisioningFailure(any());
    }

    private DhcpPacket verifyDhcpPacketRequestsIPv6OnlyPreferredOption(
            Class<? extends DhcpPacket> packetType) throws Exception {
        final DhcpPacket packet = getNextDhcpPacket();
        assertTrue(packetType.isInstance(packet));
        assertTrue(packet.hasRequestedParam(DHCP_IPV6_ONLY_PREFERRED));
        return packet;
    }

    private void doIPv6OnlyPreferredOptionTest(final Integer ipv6OnlyWaitTime,
            final Inet4Address clientAddress) throws Exception {
        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .build();
        setDhcpFeatures(false /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);

        final DhcpPacket packet =
                verifyDhcpPacketRequestsIPv6OnlyPreferredOption(DhcpDiscoverPacket.class);

        // Respond DHCPOFFER with IPv6-Only preferred option and offered address.
        mPacketReader.sendResponse(buildDhcpOfferPacket(packet, clientAddress,
                TEST_LEASE_DURATION_S, (short) TEST_DEFAULT_MTU, null /* captivePortalUrl */,
                ipv6OnlyWaitTime, null /* domainName */, null /* domainSearchList */));
    }

    private void doDiscoverIPv6OnlyPreferredOptionTest(final int optionSecs,
            final long expectedWaitSecs) throws Exception {
        doIPv6OnlyPreferredOptionTest(optionSecs, CLIENT_ADDR);
        final OnAlarmListener alarm = expectAlarmSet(null /* inOrder */, "TIMEOUT",
                expectedWaitSecs, mDependencies.mDhcpClient.getHandler());
        mDependencies.mDhcpClient.getHandler().post(() -> alarm.onAlarm());
        // Implicitly check that the client never sent a DHCPREQUEST to request the offered address.
        verifyDhcpPacketRequestsIPv6OnlyPreferredOption(DhcpDiscoverPacket.class);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption() throws Exception {
        doDiscoverIPv6OnlyPreferredOptionTest(TEST_IPV6_ONLY_WAIT_S, TEST_IPV6_ONLY_WAIT_S);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption_LowerIPv6OnlyWait() throws Exception {
        doDiscoverIPv6OnlyPreferredOptionTest(TEST_LOWER_IPV6_ONLY_WAIT_S,
                TEST_LOWER_IPV6_ONLY_WAIT_S);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption_ZeroIPv6OnlyWait() throws Exception {
        doDiscoverIPv6OnlyPreferredOptionTest(TEST_ZERO_IPV6_ONLY_WAIT_S,
                TEST_LOWER_IPV6_ONLY_WAIT_S);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption_MaxIPv6OnlyWait() throws Exception {
        doDiscoverIPv6OnlyPreferredOptionTest((int) TEST_MAX_IPV6_ONLY_WAIT_S, 0xffffffffL);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption_ZeroIPv6OnlyWaitWithOfferedAnyAddress()
            throws Exception {
        doIPv6OnlyPreferredOptionTest(TEST_ZERO_IPV6_ONLY_WAIT_S, IPV4_ADDR_ANY);

        final OnAlarmListener alarm = expectAlarmSet(null /* inOrder */, "TIMEOUT", 300,
                mDependencies.mDhcpClient.getHandler());
        mDependencies.mDhcpClient.getHandler().post(() -> alarm.onAlarm());

        verifyDhcpPacketRequestsIPv6OnlyPreferredOption(DhcpDiscoverPacket.class);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption_enabledPreconnection() throws Exception {
        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withPreconnection()
                .build();

        setDhcpFeatures(true /* isRapidCommitEnabled */, false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);

        final DhcpPacket packet = assertDiscoverPacketOnPreconnectionStart();
        verify(mCb).setNeighborDiscoveryOffload(true);

        // Force IpClient transition to RunningState from PreconnectionState.
        mIpc.notifyPreconnectionComplete(true /* success */);
        HandlerUtils.waitForIdle(mDependencies.mDhcpClient.getHandler(), TEST_TIMEOUT_MS);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);

        // DHCP server SHOULD NOT honor the Rapid-Commit option if the response would
        // contain the IPv6-only Preferred option to the client, instead respond with
        // a DHCPOFFER.
        mPacketReader.sendResponse(buildDhcpOfferPacket(packet, CLIENT_ADDR, TEST_LEASE_DURATION_S,
                (short) TEST_DEFAULT_MTU, null /* captivePortalUrl */, TEST_IPV6_ONLY_WAIT_S,
                null /* domainName */, null /* domainSearchList */));

        final OnAlarmListener alarm = expectAlarmSet(null /* inOrder */, "TIMEOUT", 1800,
                mDependencies.mDhcpClient.getHandler());
        mDependencies.mDhcpClient.getHandler().post(() -> alarm.onAlarm());

        verifyDhcpPacketRequestsIPv6OnlyPreferredOption(DhcpDiscoverPacket.class);
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testDiscoverIPv6OnlyPreferredOption_NoIPv6OnlyPreferredOption() throws Exception {
        doIPv6OnlyPreferredOptionTest(null /* ipv6OnlyWaitTime */, CLIENT_ADDR);

        // The IPv6-only Preferred option SHOULD be included in the Parameter Request List option
        // in DHCPREQUEST messages after receiving a DHCPOFFER without this option.
        verifyDhcpPacketRequestsIPv6OnlyPreferredOption(DhcpRequestPacket.class);
    }

    private void setUpRetrievedNetworkAttributesForInitRebootState() {
        final NetworkAttributes na = new NetworkAttributes.Builder()
                .setAssignedV4Address(CLIENT_ADDR)
                .setAssignedV4AddressExpiry(Long.MAX_VALUE) // lease is always valid
                .setMtu(new Integer(TEST_DEFAULT_MTU))
                .setCluster(TEST_CLUSTER)
                .setDnsAddresses(Collections.singletonList(SERVER_ADDR))
                .build();
        storeNetworkAttributes(TEST_L2KEY, na);
    }

    private void startFromInitRebootStateWithIPv6OnlyPreferredOption(final Integer ipv6OnlyWaitTime,
            final long expectedWaitSecs) throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withLayer2Information(new Layer2Information(TEST_L2KEY, TEST_CLUSTER,
                          MacAddress.fromString(TEST_DEFAULT_BSSID)))
                .build();

        setDhcpFeatures(false /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);

        final DhcpPacket packet =
                verifyDhcpPacketRequestsIPv6OnlyPreferredOption(DhcpRequestPacket.class);

        // Respond DHCPACK with IPv6-Only preferred option.
        mPacketReader.sendResponse(buildDhcpAckPacket(packet, CLIENT_ADDR,
                TEST_LEASE_DURATION_S, (short) TEST_DEFAULT_MTU, false /* rapidcommit */,
                null /* captivePortalUrl */, ipv6OnlyWaitTime, null /* domainName */,
                null /* domainSearchList */));

        if (ipv6OnlyWaitTime != null) {
            expectAlarmSet(null /* inOrder */, "TIMEOUT", expectedWaitSecs,
                    mDependencies.mDhcpClient.getHandler());
        }
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRequestIPv6OnlyPreferredOption() throws Exception {
        startFromInitRebootStateWithIPv6OnlyPreferredOption(TEST_IPV6_ONLY_WAIT_S,
                TEST_IPV6_ONLY_WAIT_S);

        // Client transits to IPv6OnlyPreferredState from INIT-REBOOT state when receiving valid
        // IPv6-Only preferred option(default value) in the DHCPACK packet.
        assertIpMemoryNeverStoreNetworkAttributes();
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRequestIPv6OnlyPreferredOption_LowerIPv6OnlyWait() throws Exception {
        startFromInitRebootStateWithIPv6OnlyPreferredOption(TEST_LOWER_IPV6_ONLY_WAIT_S,
                TEST_LOWER_IPV6_ONLY_WAIT_S);

        // Client transits to IPv6OnlyPreferredState from INIT-REBOOT state when receiving valid
        // IPv6-Only preferred option(less than MIN_V6ONLY_WAIT_MS) in the DHCPACK packet.
        assertIpMemoryNeverStoreNetworkAttributes();
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRequestIPv6OnlyPreferredOption_ZeroIPv6OnlyWait() throws Exception {
        startFromInitRebootStateWithIPv6OnlyPreferredOption(TEST_ZERO_IPV6_ONLY_WAIT_S,
                TEST_LOWER_IPV6_ONLY_WAIT_S);

        // Client transits to IPv6OnlyPreferredState from INIT-REBOOT state when receiving valid
        // IPv6-Only preferred option(0) in the DHCPACK packet.
        assertIpMemoryNeverStoreNetworkAttributes();
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRequestIPv6OnlyPreferredOption_MaxIPv6OnlyWait() throws Exception {
        startFromInitRebootStateWithIPv6OnlyPreferredOption((int) TEST_MAX_IPV6_ONLY_WAIT_S,
                0xffffffffL);

        // Client transits to IPv6OnlyPreferredState from INIT-REBOOT state when receiving valid
        // IPv6-Only preferred option(MAX_UNSIGNED_INTEGER: 0xFFFFFFFF) in the DHCPACK packet.
        assertIpMemoryNeverStoreNetworkAttributes();
    }

    @Test @SignatureRequiredTest(reason = "TODO: evaluate whether signature perms are required")
    public void testRequestIPv6OnlyPreferredOption_NoIPv6OnlyPreferredOption() throws Exception {
        final long currentTime = System.currentTimeMillis();
        startFromInitRebootStateWithIPv6OnlyPreferredOption(null /* ipv6OnlyWaitTime */,
                0 /* expectedWaitSecs */);

        // Client processes DHCPACK packet normally and transits to the ConfiguringInterfaceState
        // due to the null V6ONLY_WAIT.
        assertIpMemoryStoreNetworkAttributes(TEST_LEASE_DURATION_S, currentTime, TEST_DEFAULT_MTU);
    }

    private static int getNumOpenFds() {
        return new File("/proc/" + Os.getpid() + "/fd").listFiles().length;
    }

    private void shutdownAndRecreateIpClient() throws Exception {
        clearInvocations(mCb);
        mIpc.shutdown();
        awaitIpClientShutdown();
        mIpc = makeIpClient();
    }

    @Test @SignatureRequiredTest(reason = "Only counts FDs from the current process. TODO: fix")
    public void testNoFdLeaks() throws Exception {
        // Shut down and restart IpClient once to ensure that any fds that are opened the first
        // time it runs do not cause the test to fail.
        doDualStackProvisioning();
        shutdownAndRecreateIpClient();

        // Unfortunately we cannot use a large number of iterations as it would make the test run
        // too slowly. On crosshatch-eng each iteration takes ~250ms.
        final int iterations = 10;
        final int before = getNumOpenFds();
        for (int i = 0; i < iterations; i++) {
            doDualStackProvisioning();
            shutdownAndRecreateIpClient();
            // The last time this loop runs, mIpc will be shut down in tearDown.
        }
        final int after = getNumOpenFds();

        // Check that the number of open fds is the same as before, within some tolerance (e.g.,
        // garbage collection or other cleanups might have caused an fd to be closed). This
        // shouldn't make leak detection much less reliable, since it's likely that any leak would
        // at least leak one FD per loop.
        final int tolerance = 4;
        assertTrue(
                "FD leak detected after " + iterations + " iterations: expected "
                        + before + " +/- " + tolerance + " fds, found " + after,
                Math.abs(after - before) <= tolerance);
    }

    // TODO: delete when DhcpOption is @JavaOnlyImmutable.
    private static DhcpOption makeDhcpOption(final byte type, final byte[] value) {
        final DhcpOption opt = new DhcpOption();
        opt.type = type;
        opt.value = value;
        return opt;
    }

    private static final List<DhcpOption> TEST_OEM_DHCP_OPTIONS = Arrays.asList(
            // DHCP_USER_CLASS
            makeDhcpOption((byte) 77, TEST_OEM_USER_CLASS_INFO),
            // DHCP_VENDOR_CLASS_ID
            makeDhcpOption((byte) 60, TEST_OEM_VENDOR_ID.getBytes())
    );

    private DhcpPacket doCustomizedDhcpOptionsTest(final List<DhcpOption> options,
             final ScanResultInfo info) throws Exception {
        ProvisioningConfiguration.Builder prov = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withLayer2Information(new Layer2Information(TEST_L2KEY, TEST_CLUSTER,
                        MacAddress.fromString(TEST_DEFAULT_BSSID)))
                .withScanResultInfo(info)
                .withDhcpOptions(options)
                .withoutIPv6();

        setDhcpFeatures(false /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);

        startIpClientProvisioning(prov.build());
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);
        verify(mCb, never()).onProvisioningFailure(any());

        return getNextDhcpPacket();
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions() throws Exception {
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, TEST_OEM_VENDOR_ID);
        assertArrayEquals(packet.mUserClass, TEST_OEM_USER_CLASS_INFO);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_nullDhcpOptions() throws Exception {
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(null /* options */, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_nullScanResultInfo() throws Exception {
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS,
                null /* scanResultInfo */);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_disallowedOui() throws Exception {
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID,
                new byte[]{ 0x00, 0x11, 0x22} /* oui */, TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_invalidIeId() throws Exception {
        final ScanResultInfo info = makeScanResultInfo(0xde /* vendor-specific IE */, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_invalidVendorSpecificType() throws Exception {
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                (byte) 0x10 /* vendor-specific IE type */);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_legacyVendorSpecificType() throws Exception {
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                 LEGACY_TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testDisoverCustomizedDhcpOptions_disallowedOption() throws Exception {
        final List<DhcpOption> options = Arrays.asList(
                makeDhcpOption((byte) 60, TEST_OEM_VENDOR_ID.getBytes()),
                makeDhcpOption((byte) 77, TEST_OEM_USER_CLASS_INFO),
                // Option 26: MTU
                makeDhcpOption((byte) 26, HexDump.toByteArray(TEST_DEFAULT_MTU)));
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(options, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, TEST_OEM_VENDOR_ID);
        assertArrayEquals(packet.mUserClass, TEST_OEM_USER_CLASS_INFO);
        assertNull(packet.mMtu);
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_disallowedParamRequestOption() throws Exception {
        final List<DhcpOption> options = Arrays.asList(
                makeDhcpOption((byte) 60, TEST_OEM_VENDOR_ID.getBytes()),
                makeDhcpOption((byte) 77, TEST_OEM_USER_CLASS_INFO),
                // NTP_SERVER
                makeDhcpOption((byte) 42, null));
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(options, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertEquals(packet.mVendorId, TEST_OEM_VENDOR_ID);
        assertArrayEquals(packet.mUserClass, TEST_OEM_USER_CLASS_INFO);
        assertFalse(packet.hasRequestedParam((byte) 42 /* NTP_SERVER */));
    }

    @Test
    public void testDiscoverCustomizedDhcpOptions_ParameterRequestListOnly() throws Exception {
        final List<DhcpOption> options = Arrays.asList(
                // DHCP_USER_CLASS
                makeDhcpOption((byte) 77, null));
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(options, info);

        assertTrue(packet instanceof DhcpDiscoverPacket);
        assertTrue(packet.hasRequestedParam((byte) 77 /* DHCP_USER_CLASS */));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, TEST_OEM_VENDOR_ID);
        assertArrayEquals(packet.mUserClass, TEST_OEM_USER_CLASS_INFO);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_nullDhcpOptions() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(null /* options */, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_nullScanResultInfo() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS,
                null /* scanResultInfo */);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_disallowedOui() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID,
                new byte[]{ 0x00, 0x11, 0x22} /* oui */, TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_invalidIeId() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ScanResultInfo info = makeScanResultInfo(0xde /* vendor-specific IE */, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_invalidVendorSpecificType() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                (byte) 0x20 /* vendor-specific IE type */);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_legacyVendorSpecificType() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                LEGACY_TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(TEST_OEM_DHCP_OPTIONS, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, new String("android-dhcp-" + Build.VERSION.RELEASE));
        assertNull(packet.mUserClass);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_disallowedOption() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final List<DhcpOption> options = Arrays.asList(
                makeDhcpOption((byte) 60, TEST_OEM_VENDOR_ID.getBytes()),
                makeDhcpOption((byte) 77, TEST_OEM_USER_CLASS_INFO),
                // Option 26: MTU
                makeDhcpOption((byte) 26, HexDump.toByteArray(TEST_DEFAULT_MTU)));
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(options, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, TEST_OEM_VENDOR_ID);
        assertArrayEquals(packet.mUserClass, TEST_OEM_USER_CLASS_INFO);
        assertNull(packet.mMtu);
    }

    @Test
    public void testRequestCustomizedDhcpOptions_disallowedParamRequestOption() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final List<DhcpOption> options = Arrays.asList(
                makeDhcpOption((byte) 60, TEST_OEM_VENDOR_ID.getBytes()),
                makeDhcpOption((byte) 77, TEST_OEM_USER_CLASS_INFO),
                // NTP_SERVER
                makeDhcpOption((byte) 42, null));
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(options, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertEquals(packet.mVendorId, TEST_OEM_VENDOR_ID);
        assertArrayEquals(packet.mUserClass, TEST_OEM_USER_CLASS_INFO);
        assertFalse(packet.hasRequestedParam((byte) 42 /* NTP_SERVER */));
    }

    @Test
    public void testRequestCustomizedDhcpOptions_ParameterRequestListOnly() throws Exception {
        setUpRetrievedNetworkAttributesForInitRebootState();

        final List<DhcpOption> options = Arrays.asList(
                // DHCP_USER_CLASS
                makeDhcpOption((byte) 77, null));
        final ScanResultInfo info = makeScanResultInfo(TEST_VENDOR_SPECIFIC_IE_ID, TEST_OEM_OUI,
                TEST_VENDOR_SPECIFIC_IE_TYPE);
        final DhcpPacket packet = doCustomizedDhcpOptionsTest(options, info);

        assertTrue(packet instanceof DhcpRequestPacket);
        assertTrue(packet.hasRequestedParam((byte) 77 /* DHCP_USER_CLASS */));
        assertNull(packet.mUserClass);
    }

    private void assertGratuitousNa(final NeighborAdvertisement na) throws Exception {
        final MacAddress etherMulticast =
                NetworkStackUtils.ipv6MulticastToEthernetMulticast(IPV6_ADDR_ALL_ROUTERS_MULTICAST);
        final LinkAddress target = new LinkAddress(na.naHdr.target, 64);

        assertEquals(etherMulticast, na.ethHdr.dstMac);
        assertEquals(ETH_P_IPV6, na.ethHdr.etherType);
        assertEquals(IPPROTO_ICMPV6, na.ipv6Hdr.nextHeader);
        assertEquals(0xff, na.ipv6Hdr.hopLimit);
        assertTrue(na.ipv6Hdr.srcIp.isLinkLocalAddress());
        assertEquals(IPV6_ADDR_ALL_ROUTERS_MULTICAST, na.ipv6Hdr.dstIp);
        assertEquals(ICMPV6_NEIGHBOR_ADVERTISEMENT, na.icmpv6Hdr.type);
        assertEquals(0, na.icmpv6Hdr.code);
        assertEquals(0, na.naHdr.flags);
        assertTrue(target.isGlobalPreferred());
    }

    private void assertMulticastNsFromIpv6Gua(final NeighborSolicitation ns) throws Exception {
        final Inet6Address solicitedNodeMulticast =
                NetworkStackUtils.ipv6AddressToSolicitedNodeMulticast(ROUTER_LINK_LOCAL);
        final MacAddress etherMulticast =
                NetworkStackUtils.ipv6MulticastToEthernetMulticast(solicitedNodeMulticast);

        assertEquals(etherMulticast, ns.ethHdr.dstMac);
        assertEquals(ETH_P_IPV6, ns.ethHdr.etherType);
        assertEquals(IPPROTO_ICMPV6, ns.ipv6Hdr.nextHeader);
        assertEquals(0xff, ns.ipv6Hdr.hopLimit);

        final LinkAddress srcIp = new LinkAddress(ns.ipv6Hdr.srcIp.getHostAddress() + "/64");
        assertTrue(srcIp.isGlobalPreferred());
        assertEquals(solicitedNodeMulticast, ns.ipv6Hdr.dstIp);
        assertEquals(ICMPV6_NEIGHBOR_SOLICITATION, ns.icmpv6Hdr.type);
        assertEquals(0, ns.icmpv6Hdr.code);
        assertEquals(ROUTER_LINK_LOCAL, ns.nsHdr.target);
    }

    @Test
    public void testGratuitousNaForNewGlobalUnicastAddresses() throws Exception {
        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv4()
                .build();

        setFeatureEnabled(NetworkStackUtils.IPCLIENT_GRATUITOUS_NA_VERSION,
                true /* isGratuitousNaEnabled */);
        startIpClientProvisioning(config);

        doIpv6OnlyProvisioning();

        final List<NeighborAdvertisement> naList = new ArrayList<>();
        NeighborAdvertisement packet;
        while ((packet = getNextNeighborAdvertisement()) != null) {
            assertGratuitousNa(packet);
            naList.add(packet);
        }
        assertEquals(2, naList.size()); // privacy address and stable privacy address
    }

    private void startGratuitousArpAndNaAfterRoamingTest(boolean isGratuitousArpNaRoamingEnabled,
            boolean hasIpv4, boolean hasIpv6) throws Exception {
        final Layer2Information layer2Info = new Layer2Information(TEST_L2KEY, TEST_CLUSTER,
                MacAddress.fromString(TEST_DEFAULT_BSSID));
        final ScanResultInfo scanResultInfo =
                makeScanResultInfo(TEST_DEFAULT_SSID, TEST_DEFAULT_BSSID);
        final ProvisioningConfiguration.Builder prov = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withLayer2Information(layer2Info)
                .withScanResultInfo(scanResultInfo)
                .withDisplayName("ssid");
        if (!hasIpv4) prov.withoutIPv4();
        if (!hasIpv6) prov.withoutIPv6();

        // Enable rapid commit to accelerate DHCP handshake to shorten test duration,
        // not strictly necessary.
        setDhcpFeatures(true /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);

        // Disable gratuitious neighbor discovery feature manually, if the feature is enabled on
        // the DUT during experiment launch, that will send another two duplicate NA packets and
        // mess up the assert of received NA packets.
        setFeatureEnabled(NetworkStackUtils.IPCLIENT_GRATUITOUS_NA_VERSION,
                false /* isGratuitousNaEnabled */);
        if (isGratuitousArpNaRoamingEnabled) {
            setFeatureEnabled(NetworkStackUtils.IPCLIENT_GARP_NA_ROAMING_VERSION, true);
        } else {
            setFeatureEnabled(NetworkStackUtils.IPCLIENT_GARP_NA_ROAMING_VERSION, false);
        }
        startIpClientProvisioning(prov.build());
    }

    private void waitForGratuitousArpAndNaPacket(final List<ArpPacket> arpList,
            final List<NeighborAdvertisement> naList) throws Exception {
        NeighborAdvertisement na;
        ArpPacket garp;
        do {
            na = getNextNeighborAdvertisement();
            if (na != null) {
                assertGratuitousNa(na);
                naList.add(na);
            }
            garp = getNextArpPacket(TEST_TIMEOUT_MS);
            if (garp != null) {
                assertGratuitousARP(garp);
                arpList.add(garp);
            }
        } while (na != null || garp != null);
    }

    @Test
    public void testGratuitousArpAndNaAfterRoaming() throws Exception {
        startGratuitousArpAndNaAfterRoamingTest(true /* isGratuitousArpNaRoamingEnabled */,
                true /* hasIpv4 */, true /* hasIpv6 */);
        performDualStackProvisioning();
        forceLayer2Roaming();

        final List<ArpPacket> arpList = new ArrayList<>();
        final List<NeighborAdvertisement> naList = new ArrayList<>();
        waitForGratuitousArpAndNaPacket(arpList, naList);
        assertEquals(2, naList.size()); // privacy address and stable privacy address
        assertEquals(1, arpList.size()); // IPv4 address
    }

    @Test
    public void testGratuitousArpAndNaAfterRoaming_disableExpFlag() throws Exception {
        startGratuitousArpAndNaAfterRoamingTest(false /* isGratuitousArpNaRoamingEnabled */,
                true /* hasIpv4 */, true /* hasIpv6 */);
        performDualStackProvisioning();
        forceLayer2Roaming();

        final List<ArpPacket> arpList = new ArrayList<>();
        final List<NeighborAdvertisement> naList = new ArrayList<>();
        waitForGratuitousArpAndNaPacket(arpList, naList);
        assertEquals(0, naList.size());
        assertEquals(0, arpList.size());
    }

    @Test
    public void testGratuitousArpAndNaAfterRoaming_IPv6OnlyNetwork() throws Exception {
        startGratuitousArpAndNaAfterRoamingTest(true /* isGratuitousArpNaRoamingEnabled */,
                false /* hasIpv4 */, true /* hasIpv6 */);
        doIpv6OnlyProvisioning();
        forceLayer2Roaming();

        final List<ArpPacket> arpList = new ArrayList<>();
        final List<NeighborAdvertisement> naList = new ArrayList<>();
        waitForGratuitousArpAndNaPacket(arpList, naList);
        assertEquals(2, naList.size());
        assertEquals(0, arpList.size());
    }

    @Test
    public void testGratuitousArpAndNaAfterRoaming_IPv4OnlyNetwork() throws Exception {
        startGratuitousArpAndNaAfterRoamingTest(true /* isGratuitousArpNaRoamingEnabled */,
                true /* hasIpv4 */, false /* hasIpv6 */);

        // Start IPv4 provisioning and wait until entire provisioning completes.
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                true /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU, null /* serverSentUrl */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        forceLayer2Roaming();

        final List<ArpPacket> arpList = new ArrayList<>();
        final List<NeighborAdvertisement> naList = new ArrayList<>();
        waitForGratuitousArpAndNaPacket(arpList, naList);
        assertEquals(0, naList.size());
        assertEquals(1, arpList.size());
    }

    private void assertNeighborSolicitation(final NeighborSolicitation ns,
            final Inet6Address target) {
        assertEquals(ETH_P_IPV6, ns.ethHdr.etherType);
        assertEquals(IPPROTO_ICMPV6, ns.ipv6Hdr.nextHeader);
        assertEquals(0xff, ns.ipv6Hdr.hopLimit);
        assertTrue(ns.ipv6Hdr.srcIp.isLinkLocalAddress());
        assertEquals(ICMPV6_NEIGHBOR_SOLICITATION, ns.icmpv6Hdr.type);
        assertEquals(0, ns.icmpv6Hdr.code);
        assertEquals(0, ns.nsHdr.reserved);
        assertEquals(target, ns.nsHdr.target);
        assertEquals(ns.slla.linkLayerAddress, ns.ethHdr.srcMac);
    }

    private void assertUnicastNeighborSolicitation(final NeighborSolicitation ns,
            final MacAddress dstMac, final Inet6Address dstIp, final Inet6Address target) {
        assertEquals(dstMac, ns.ethHdr.dstMac);
        assertEquals(dstIp, ns.ipv6Hdr.dstIp);
        assertNeighborSolicitation(ns, target);
    }

    private void assertMulticastNeighborSolicitation(final NeighborSolicitation ns,
            final Inet6Address target) {
        final MacAddress etherMulticast =
                NetworkStackUtils.ipv6MulticastToEthernetMulticast(ns.ipv6Hdr.dstIp);
        assertEquals(etherMulticast, ns.ethHdr.dstMac);
        assertTrue(ns.ipv6Hdr.dstIp.isMulticastAddress());
        assertNeighborSolicitation(ns, target);
    }

    private NeighborSolicitation waitForUnicastNeighborSolicitation(final MacAddress dstMac,
            final Inet6Address dstIp, final Inet6Address targetIp) throws Exception {
        NeighborSolicitation ns;
        while ((ns = getNextNeighborSolicitation()) != null) {
            // Filter out the multicast NSes used for duplicate address detetction, the target
            // address is the global IPv6 address inside these NSes, and multicast NSes sent from
            // device's GUAs to force first-hop router to update the neighbor cache entry.
            if (ns.ipv6Hdr.srcIp.isLinkLocalAddress() && ns.nsHdr.target.isLinkLocalAddress()) {
                break;
            }
        }
        assertNotNull("No unicast Neighbor solicitation received on interface within timeout", ns);
        assertUnicastNeighborSolicitation(ns, dstMac, dstIp, targetIp);
        return ns;
    }

    private List<NeighborSolicitation> waitForMultipleNeighborSolicitations() throws Exception {
        NeighborSolicitation ns;
        final List<NeighborSolicitation> nsList = new ArrayList<NeighborSolicitation>();
        while ((ns = getNextNeighborSolicitation()) != null) {
            // Filter out the multicast NSes used for duplicate address detetction, the target
            // address is the global IPv6 address inside these NSes, and multicast NSes sent from
            // device's GUAs to force first-hop router to update the neighbor cache entry.
            if (ns.ipv6Hdr.srcIp.isLinkLocalAddress() && ns.nsHdr.target.isLinkLocalAddress()) {
                nsList.add(ns);
            }
        }
        assertFalse(nsList.isEmpty());
        return nsList;
    }

    private NeighborSolicitation expectDadNeighborSolicitationForLinkLocal(boolean shouldDisableDad)
            throws Exception {
        final NeighborSolicitation ns = getNextNeighborSolicitation();
        if (!shouldDisableDad) {
            final Inet6Address solicitedNodeMulticast =
                    NetworkStackUtils.ipv6AddressToSolicitedNodeMulticast(ns.nsHdr.target);
            assertNotNull("No multicast NS received on interface within timeout", ns);
            assertEquals(IPV6_ADDR_ANY, ns.ipv6Hdr.srcIp);     // srcIp: ::/
            assertTrue(ns.ipv6Hdr.dstIp.isMulticastAddress()); // dstIp: solicited-node mcast
            assertTrue(ns.ipv6Hdr.dstIp.equals(solicitedNodeMulticast));
            assertTrue(ns.nsHdr.target.isLinkLocalAddress());  // targetIp: IPv6 LL address
        } else {
            assertNull(ns);
        }
        return ns;
    }

    // Override this function with disabled experiment flag by default, in order not to
    // affect those tests which are just related to basic IpReachabilityMonitor infra.
    private void prepareIpReachabilityMonitorTest() throws Exception {
        prepareIpReachabilityMonitorTest(false /* isMulticastResolicitEnabled */);
    }

    private void assertNotifyNeighborLost(Inet6Address targetIp, NudEventType eventType)
            throws Exception {
        // For root test suite, rely on the IIpClient aidl interface version constant defined in
        // {@link IpClientRootTest.BinderCbWrapper}; for privileged integration test suite that
        // requires signature permission, use the mocked aidl version defined in {@link setUpMocks},
        // which results in only new callbacks are verified. And add separate test cases to test the
        // legacy callbacks explicitly as well.
        assertNeighborReachabilityLoss(targetIp, eventType,
                useNetworkStackSignature()
                        ? IpClient.VERSION_ADDED_REACHABILITY_FAILURE
                        : mIIpClient.getInterfaceVersion());
    }

    private void assertNeighborReachabilityLoss(Inet6Address targetIp, NudEventType eventType,
            int targetAidlVersion) throws Exception {
        if (targetAidlVersion >= IpClient.VERSION_ADDED_REACHABILITY_FAILURE) {
            final ArgumentCaptor<ReachabilityLossInfoParcelable> lossInfoCaptor =
                    ArgumentCaptor.forClass(ReachabilityLossInfoParcelable.class);
            verify(mCb, timeout(TEST_TIMEOUT_MS)).onReachabilityFailure(lossInfoCaptor.capture());
            assertEquals(nudEventTypeToInt(eventType), lossInfoCaptor.getValue().reason);
            verify(mCb, never()).onReachabilityLost(any());
        } else {
            verify(mCb, timeout(TEST_TIMEOUT_MS)).onReachabilityLost(any());
            verify(mCb, never()).onReachabilityFailure(any());
        }
    }

    private void assertNeverNotifyNeighborLost() throws Exception {
        verify(mCb, never()).onReachabilityFailure(any());
        verify(mCb, never()).onReachabilityLost(any());
    }

    private void prepareIpReachabilityMonitorTest(boolean isMulticastResolicitEnabled)
            throws Exception {
        final ScanResultInfo info = makeScanResultInfo(TEST_DEFAULT_SSID, TEST_DEFAULT_BSSID);
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withLayer2Information(new Layer2Information(TEST_L2KEY, TEST_CLUSTER,
                       MacAddress.fromString(TEST_DEFAULT_BSSID)))
                .withScanResultInfo(info)
                .withDisplayName(TEST_DEFAULT_SSID)
                .withoutIPv4()
                .build();
        setFeatureEnabled(NetworkStackUtils.IP_REACHABILITY_MCAST_RESOLICIT_VERSION,
                isMulticastResolicitEnabled);
        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);
        doIpv6OnlyProvisioning();

        // Simulate the roaming.
        forceLayer2Roaming();
    }

    private void runIpReachabilityMonitorProbeFailedTest() throws Exception {
        prepareIpReachabilityMonitorTest();

        final List<NeighborSolicitation> nsList = waitForMultipleNeighborSolicitations();
        final int expectedNudSolicitNum = readNudSolicitNumPostRoamingFromResource();
        assertEquals(expectedNudSolicitNum, nsList.size());
        for (NeighborSolicitation ns : nsList) {
            assertUnicastNeighborSolicitation(ns, ROUTER_MAC /* dstMac */,
                    ROUTER_LINK_LOCAL /* dstIp */, ROUTER_LINK_LOCAL /* targetIp */);
        }
    }

    @Test
    public void testIpReachabilityMonitor_probeFailed() throws Exception {
        runIpReachabilityMonitorProbeFailedTest();
        assertNotifyNeighborLost(ROUTER_LINK_LOCAL /* targetIp */,
                NudEventType.NUD_POST_ROAMING_FAILED_CRITICAL);
    }

    @Test @SignatureRequiredTest(reason = "requires mock callback object")
    public void testIpReachabilityMonitor_probeFailed_legacyCallback() throws Exception {
        when(mCb.getInterfaceVersion()).thenReturn(12 /* assign an older interface aidl version */);

        runIpReachabilityMonitorProbeFailedTest();
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onReachabilityLost(any());
        verify(mCb, never()).onReachabilityFailure(any());
    }

    @Test
    public void testIpReachabilityMonitor_probeReachable() throws Exception {
        prepareIpReachabilityMonitorTest();

        final NeighborSolicitation ns = waitForUnicastNeighborSolicitation(ROUTER_MAC /* dstMac */,
                ROUTER_LINK_LOCAL /* dstIp */, ROUTER_LINK_LOCAL /* targetIp */);

        // Reply Neighbor Advertisement and check notifyLost callback won't be triggered.
        int flag = NEIGHBOR_ADVERTISEMENT_FLAG_ROUTER | NEIGHBOR_ADVERTISEMENT_FLAG_SOLICITED;
        final ByteBuffer na = NeighborAdvertisement.build(ROUTER_MAC /* srcMac */,
                ns.ethHdr.srcMac /* dstMac */, ROUTER_LINK_LOCAL /* srcIp */,
                ns.ipv6Hdr.srcIp /* dstIp */, flag, ROUTER_LINK_LOCAL /* target */);
        mPacketReader.sendResponse(na);
        assertNeverNotifyNeighborLost();
    }

    private void runIpReachabilityMonitorMcastResolicitProbeFailedTest() throws Exception {
        prepareIpReachabilityMonitorTest(true /* isMulticastResolicitEnabled */);

        final List<NeighborSolicitation> nsList = waitForMultipleNeighborSolicitations();
        final int expectedNudSolicitNum = readNudSolicitNumPostRoamingFromResource();
        int expectedSize = expectedNudSolicitNum + NUD_MCAST_RESOLICIT_NUM;
        assertEquals(expectedSize, nsList.size());
        for (NeighborSolicitation ns : nsList.subList(0, expectedNudSolicitNum)) {
            assertUnicastNeighborSolicitation(ns, ROUTER_MAC /* dstMac */,
                    ROUTER_LINK_LOCAL /* dstIp */, ROUTER_LINK_LOCAL /* targetIp */);
        }
        for (NeighborSolicitation ns : nsList.subList(expectedNudSolicitNum, nsList.size())) {
            assertMulticastNeighborSolicitation(ns, ROUTER_LINK_LOCAL /* targetIp */);
        }
    }

    @Test
    public void testIpReachabilityMonitor_mcastResolicitProbeFailed() throws Exception {
        runIpReachabilityMonitorMcastResolicitProbeFailedTest();
        assertNotifyNeighborLost(ROUTER_LINK_LOCAL /* targetIp */,
                NudEventType.NUD_POST_ROAMING_FAILED_CRITICAL);
    }

    @Test @SignatureRequiredTest(reason = "requires mock callback object")
    public void testIpReachabilityMonitor_mcastResolicitProbeFailed_legacyCallback()
            throws Exception {
        when(mCb.getInterfaceVersion()).thenReturn(12 /* assign an older interface aidl version */);

        runIpReachabilityMonitorMcastResolicitProbeFailedTest();
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onReachabilityLost(any());
        verify(mCb, never()).onReachabilityFailure(any());
    }

    @Test
    public void testIpReachabilityMonitor_mcastResolicitProbeReachableWithSameLinkLayerAddress()
            throws Exception {
        prepareIpReachabilityMonitorTest(true /* isMulticastResolicitEnabled */);

        final NeighborSolicitation ns = waitForUnicastNeighborSolicitation(ROUTER_MAC /* dstMac */,
                ROUTER_LINK_LOCAL /* dstIp */, ROUTER_LINK_LOCAL /* targetIp */);

        // Reply Neighbor Advertisement and check notifyLost callback won't be triggered.
        int flag = NEIGHBOR_ADVERTISEMENT_FLAG_ROUTER | NEIGHBOR_ADVERTISEMENT_FLAG_SOLICITED;
        final ByteBuffer na = NeighborAdvertisement.build(ROUTER_MAC /* srcMac */,
                ns.ethHdr.srcMac /* dstMac */, ROUTER_LINK_LOCAL /* srcIp */,
                ns.ipv6Hdr.srcIp /* dstIp */, flag, ROUTER_LINK_LOCAL /* target */);
        mPacketReader.sendResponse(na);
        assertNeverNotifyNeighborLost();
    }

    @Test
    public void testIpReachabilityMonitor_mcastResolicitProbeReachableWithDiffLinkLayerAddress()
            throws Exception {
        prepareIpReachabilityMonitorTest(true /* isMulticastResolicitEnabled */);

        final NeighborSolicitation ns = waitForUnicastNeighborSolicitation(ROUTER_MAC /* dstMac */,
                ROUTER_LINK_LOCAL /* dstIp */, ROUTER_LINK_LOCAL /* targetIp */);

        // Reply Neighbor Advertisement with a different link-layer address and check notifyLost
        // callback will be triggered. Override flag must be set, which indicates that the
        // advertisement should override an existing cache entry and update the cached link-layer
        // address, otherwise, kernel won't transit to REACHABLE state with a different link-layer
        // address.
        int flag = NEIGHBOR_ADVERTISEMENT_FLAG_ROUTER | NEIGHBOR_ADVERTISEMENT_FLAG_SOLICITED
                | NEIGHBOR_ADVERTISEMENT_FLAG_OVERRIDE;
        final MacAddress newMac = MacAddress.fromString("00:1a:11:22:33:55");
        final ByteBuffer na = NeighborAdvertisement.build(newMac /* srcMac */,
                ns.ethHdr.srcMac /* dstMac */, ROUTER_LINK_LOCAL /* srcIp */,
                ns.ipv6Hdr.srcIp /* dstIp */, flag, ROUTER_LINK_LOCAL /* target */);
        mPacketReader.sendResponse(na);
        assertNotifyNeighborLost(ROUTER_LINK_LOCAL /* targetIp */,
                NudEventType.NUD_POST_ROAMING_MAC_ADDRESS_CHANGED);
    }

    private void prepareIpReachabilityMonitorIpv4AddressResolutionTest() throws Exception {
        mNetworkAgentThread =
                new HandlerThread(IpClientIntegrationTestCommon.class.getSimpleName());
        mNetworkAgentThread.start();

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv6()
                .build();
        setDhcpFeatures(true /* isRapidCommitEnabled */, false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);

        // Start IPv4 provisioning and wait until entire provisioning completes.
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                true /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU, null /* serverSentUrl */);
        final LinkProperties lp =
                verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));

        runAsShell(MANAGE_TEST_NETWORKS, () -> createTestNetworkAgentAndRegister(lp));

        // Send a UDP packet to IPv4 DNS server to trigger address resolution process for IPv4
        // on-link DNS server or default router.
        final Random random = new Random();
        final byte[] data = new byte[100];
        random.nextBytes(data);
        sendUdpPacketToNetwork(mNetworkAgent.getNetwork(), SERVER_ADDR, 1234 /* port */, data);
    }

    private void doTestIpReachabilityMonitor_replyBroadcastArpRequestWithDiffMacAddresses(
            boolean disconnect) throws Exception {
        prepareIpReachabilityMonitorIpv4AddressResolutionTest();

        // Respond to the broadcast ARP request.
        final ArpPacket request = getNextArpPacket();
        assertArpRequest(request, SERVER_ADDR);
        sendArpReply(request.senderHwAddress.toByteArray() /* dst */, ROUTER_MAC_BYTES /* srcMac */,
                request.senderIp /* target IP */, SERVER_ADDR /* sender IP */);

        Thread.sleep(1500);

        // Reply with a different MAC address but the same server IP.
        final MacAddress gateway = MacAddress.fromString("00:11:22:33:44:55");
        sendArpReply(request.senderHwAddress.toByteArray() /* dst */,
                gateway.toByteArray() /* srcMac */,
                request.senderIp /* target IP */, SERVER_ADDR /* sender IP */);

        if (disconnect) {
            final ArgumentCaptor<ReachabilityLossInfoParcelable> lossInfoCaptor =
                    ArgumentCaptor.forClass(ReachabilityLossInfoParcelable.class);
            verify(mCb, timeout(TEST_TIMEOUT_MS)).onReachabilityFailure(lossInfoCaptor.capture());
            assertEquals(ReachabilityLossReason.ORGANIC, lossInfoCaptor.getValue().reason);
        } else {
            verify(mCb, after(100).never()).onReachabilityFailure(any());
        }
    }

    @Test
    public void testIpReachabilityMonitor_macAddressChangedWithoutRoam_ok()
            throws Exception {
        setFeatureChickenedOut(IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION,
                false);
        doTestIpReachabilityMonitor_replyBroadcastArpRequestWithDiffMacAddresses(false);
    }

    @Test
    public void testIpReachabilityMonitor_macAddressChangedWithoutRoam_disconnect()
            throws Exception {
        setFeatureChickenedOut(IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION,
                true);
        doTestIpReachabilityMonitor_replyBroadcastArpRequestWithDiffMacAddresses(true);
    }

    @Test
    public void testIpReachabilityMonitor_ignoreIpv4DefaultRouterOrganicNudFailure()
            throws Exception {
        setFeatureEnabled(NetworkStackUtils.IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION,
                true /* ignoreOrganicNudFailure */);
        prepareIpReachabilityMonitorIpv4AddressResolutionTest();

        ArpPacket packet;
        while ((packet = getNextArpPacket(TEST_TIMEOUT_MS)) != null) {
            // wait address resolution to complete.
        }
        verify(mCb, never()).onReachabilityFailure(any());
    }

    @Test
    public void testIpReachabilityMonitor_ignoreIpv4DefaultRouterOrganicNudFailure_flagoff()
            throws Exception {
        setFeatureEnabled(NetworkStackUtils.IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION,
                false /* ignoreOrganicNudFailure */);
        prepareIpReachabilityMonitorIpv4AddressResolutionTest();

        ArpPacket packet;
        while ((packet = getNextArpPacket(TEST_TIMEOUT_MS)) != null) {
            // wait address resolution to complete.
        }
        final ArgumentCaptor<ReachabilityLossInfoParcelable> lossInfoCaptor =
                ArgumentCaptor.forClass(ReachabilityLossInfoParcelable.class);
        verify(mCb).onReachabilityFailure(lossInfoCaptor.capture());
        assertEquals(ReachabilityLossReason.ORGANIC, lossInfoCaptor.getValue().reason);
    }

    private void sendUdpPacketToNetwork(final Network network, final InetAddress remoteIp,
            int port, final byte[] data) throws Exception {
        final InetAddress laddr =
                (remoteIp instanceof Inet6Address) ? Inet6Address.ANY : Inet4Address.ANY;
        final DatagramSocket socket = new DatagramSocket(0, laddr);
        final DatagramPacket pkt = new DatagramPacket(data, data.length, remoteIp, port);
        network.bindSocket(socket);
        socket.send(pkt);
    }

    private void runIpReachabilityMonitorAddressResolutionTest(final String dnsServer,
            final Inet6Address targetIp,
            final boolean isIgnoreIncompleteIpv6DnsServerEnabled,
            final boolean isIgnoreIncompleteIpv6DefaultRouterEnabled,
            final boolean isIgnoreOrganicNudFailureEnabled,
            final boolean expectNeighborLost) throws Exception {
        mNetworkAgentThread =
                new HandlerThread(IpClientIntegrationTestCommon.class.getSimpleName());
        mNetworkAgentThread.start();

        setDhcpFeatures(true /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        setFeatureEnabled(
                NetworkStackUtils.IP_REACHABILITY_IGNORE_INCOMPLETE_IPV6_DNS_SERVER_VERSION,
                isIgnoreIncompleteIpv6DnsServerEnabled);
        setFeatureEnabled(
                NetworkStackUtils.IP_REACHABILITY_IGNORE_INCOMPLETE_IPV6_DEFAULT_ROUTER_VERSION,
                isIgnoreIncompleteIpv6DefaultRouterEnabled);
        setFeatureEnabled(
                NetworkStackUtils.IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION,
                isIgnoreOrganicNudFailureEnabled);
        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .build();
        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setFallbackMulticastFilter(true);

        final List<ByteBuffer> options = new ArrayList<ByteBuffer>();
        options.add(buildPioOption(3600, 1800, "2001:db8:1::/64")); // PIO
        options.add(buildRdnssOption(3600, dnsServer));             // RDNSS
        // If target IP of address resolution is default router's IPv6 link-local address,
        // then we should not take SLLA option in RA.
        if (!targetIp.equals(ROUTER_LINK_LOCAL)) {
            options.add(buildSllaOption());                         // SLLA
        }
        final ByteBuffer ra = buildRaPacket(options.toArray(new ByteBuffer[options.size()]));
        final Inet6Address dnsServerIp =
                (Inet6Address) InetAddresses.parseNumericAddress(dnsServer);
        final LinkProperties lp = performDualStackProvisioning(ra, dnsServerIp);
        runAsShell(MANAGE_TEST_NETWORKS, () -> createTestNetworkAgentAndRegister(lp));

        // Send a UDP packet to IPv6 DNS server to trigger address resolution process for IPv6
        // on-link DNS server or default router(if the target is default router, we should pass
        // in an IPv6 off-link DNS server such as 2001:db8:4860:4860::64).
        final Random random = new Random();
        final byte[] data = new byte[100];
        random.nextBytes(data);
        sendUdpPacketToNetwork(mNetworkAgent.getNetwork(), dnsServerIp, 1234 /* port */, data);

        // Wait for the multicast NSes but never respond to them, that results in the on-link
        // DNS gets lost and onReachabilityLost callback will be invoked.
        final List<NeighborSolicitation> nsList = new ArrayList<NeighborSolicitation>();
        NeighborSolicitation ns;
        while ((ns = getNextNeighborSolicitation()) != null) {
            // multicast NS for address resolution, IPv6 dst address in that NS is solicited-node
            // multicast address based on the target IP, the target IP is either on-link IPv6 DNS
            // server address or IPv6 link-local address of default gateway.
            final LinkAddress actual = new LinkAddress(ns.nsHdr.target, 64);
            final LinkAddress target = new LinkAddress(targetIp, 64);
            if (actual.equals(target) && ns.ipv6Hdr.dstIp.isMulticastAddress()) {
                nsList.add(ns);
            }
        }
        assertFalse(nsList.isEmpty());

        if (expectNeighborLost) {
            assertNotifyNeighborLost(targetIp, NudEventType.NUD_ORGANIC_FAILED_CRITICAL);
        } else {
            assertNeverNotifyNeighborLost();
        }
    }

    @Test
    public void testIpReachabilityMonitor_incompleteIpv6DnsServerInDualStack() throws Exception {
        final Inet6Address targetIp =
                (Inet6Address) InetAddresses.parseNumericAddress(IPV6_ON_LINK_DNS_SERVER);
        runIpReachabilityMonitorAddressResolutionTest(IPV6_ON_LINK_DNS_SERVER, targetIp,
                true /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                false /* isIgnoreOrganicNudFailureEnabled */,
                false /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_incompleteIpv6DnsServerInDualStack_flagoff()
            throws Exception {
        final Inet6Address targetIp =
                (Inet6Address) InetAddresses.parseNumericAddress(IPV6_ON_LINK_DNS_SERVER);
        runIpReachabilityMonitorAddressResolutionTest(IPV6_ON_LINK_DNS_SERVER, targetIp,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                false /* isIgnoreOrganicNudFailureEnabled */,
                true /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_incompleteIpv6DefaultRouterInDualStack()
            throws Exception {
        runIpReachabilityMonitorAddressResolutionTest(IPV6_OFF_LINK_DNS_SERVER,
                ROUTER_LINK_LOCAL /* targetIp */,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                true /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                false /* isIgnoreOrganicNudFailureEnabled */,
                false /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_incompleteIpv6DefaultRouterInDualStack_flagoff()
            throws Exception {
        runIpReachabilityMonitorAddressResolutionTest(IPV6_OFF_LINK_DNS_SERVER,
                ROUTER_LINK_LOCAL /* targetIp */,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                false /* isIgnoreOrganicNudFailureEnabled */,
                true /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_ignoreOnLinkIpv6DnsOrganicNudFailure()
            throws Exception {
        final Inet6Address targetIp =
                (Inet6Address) InetAddresses.parseNumericAddress(IPV6_ON_LINK_DNS_SERVER);
        runIpReachabilityMonitorAddressResolutionTest(IPV6_ON_LINK_DNS_SERVER, targetIp,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                true /* isIgnoreOrganicNudFailureEnabled */,
                false /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_ignoreOnLinkIpv6DnsOrganicNudFailure_flagoff()
            throws Exception {
        final Inet6Address targetIp =
                (Inet6Address) InetAddresses.parseNumericAddress(IPV6_ON_LINK_DNS_SERVER);
        runIpReachabilityMonitorAddressResolutionTest(IPV6_ON_LINK_DNS_SERVER, targetIp,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                false /* isIgnoreOrganicNudFailureEnabled */,
                true /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_ignoreIpv6DefaultRouterOrganicNudFailure()
            throws Exception {
        runIpReachabilityMonitorAddressResolutionTest(IPV6_OFF_LINK_DNS_SERVER,
                ROUTER_LINK_LOCAL /* targetIp */,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                true /* isIgnoreOrganicNudFailureEnabled */,
                false /* expectNeighborLost */);
    }

    @Test
    public void testIpReachabilityMonitor_ignoreIpv6DefaultRouterOrganicNudFailure_flagoff()
            throws Exception {
        runIpReachabilityMonitorAddressResolutionTest(IPV6_OFF_LINK_DNS_SERVER,
                ROUTER_LINK_LOCAL /* targetIp */,
                false /* isIgnoreIncompleteIpv6DnsServerEnabled */,
                false /* isIgnoreIncompleteIpv6DefaultRouterEnabled */,
                false /* isIgnoreOrganicNudFailureEnabled */,
                true /* expectNeighborLost */);
    }

    @Test
    public void testIPv6LinkLocalOnly() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .withIpv6LinkLocalOnly()
                .withRandomMacAddress()
                .build();
        startIpClientProvisioning(config);

        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertEquals(0, lp.getDnsServers().size());
        final List<LinkAddress> addresses = lp.getLinkAddresses();
        assertEquals(1, addresses.size());
        assertTrue(addresses.get(0).getAddress().isLinkLocalAddress()); // only IPv6 link-local
        assertTrue(hasRouteTo(lp, IPV6_LINK_LOCAL_PREFIX)); // fe80::/64 -> :: iface mtu 0

        // Check that if an RA is received, no IP addresses, routes, or DNS servers are configured.
        // Instead of waiting some period of time for the RA to be received and checking the
        // LinkProperties after that, tear down the interface and wait for it to go down. Then check
        // that no LinkProperties updates ever contained non-link-local information.
        sendBasicRouterAdvertisement(false /* waitForRs */);
        teardownTapInterface();
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(any());
        verify(mCb, never()).onLinkPropertiesChange(argThat(newLp ->
                // Ideally there should be only one route(fe80::/64 -> :: iface mtu 0) in the
                // LinkProperties, however, the multicast route(ff00::/8 -> :: iface mtu 0) may
                // appear on some old platforms where the kernel is still notifying the userspace
                // the multicast route. Therefore, we cannot assert that size of routes in the
                // LinkProperties is more than one, but other properties such as DNS or IPv6
                // default route or global IPv6 address should never appear in the IPv6 link-local
                // only mode.
                newLp.getDnsServers().size() != 0
                        || newLp.hasIpv6DefaultRoute()
                        || newLp.hasGlobalIpv6Address()
        ));
    }

    @Test
    public void testIPv6LinkLocalOnly_verifyAcceptRaDefrtr() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .withIpv6LinkLocalOnly()
                .withRandomMacAddress()
                .build();
        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(any());

        clearInvocations(mCb);

        // accept_ra is set to 0 and accept_ra_defrtr is set to 1 in IPv6 link-local only mode,
        // send another RA to tap interface, to verify that we should not see any IPv6 provisioning
        // although accept_ra_defrtr is set to 1.
        sendBasicRouterAdvertisement(false /* waitForRs */);
        verify(mCb, never()).onLinkPropertiesChange(argThat(x -> x.isIpv6Provisioned()));
    }

    @Test
    public void testIPv6LinkLocalOnlyAndThenGlobal() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .withIpv6LinkLocalOnly()
                .withRandomMacAddress()
                .build();
        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(any());
        mIIpClient.stop();
        verifyAfterIpClientShutdown();
        reset(mCb);

        // Speed up provisioning by enabling rapid commit. TODO: why is this necessary?
        setDhcpFeatures(true /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        config = new ProvisioningConfiguration.Builder()
                .build();
        startIpClientProvisioning(config);
        performDualStackProvisioning();
        // No exceptions? Dual-stack provisioning worked.
    }

    @Test
    public void testIPv6LinkLocalOnly_enableBothIPv4andIPv6LinkLocalOnly() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new ProvisioningConfiguration.Builder()
                        .withoutIpReachabilityMonitor()
                        .withIpv6LinkLocalOnly()
                        .withRandomMacAddress()
                        .build()
        );
    }

    private void runIpv6LinkLocalOnlyDadTransmitsCheckTest(boolean shouldDisableDad)
            throws Exception {
        ProvisioningConfiguration.Builder config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .withIpv6LinkLocalOnly()
                .withRandomMacAddress();
        if (shouldDisableDad) config.withUniqueEui64AddressesOnly();

        // dad_transmits has been set to 0 in disableIpv6ProvisioningDelays, re-enable dad_transmits
        // for testing, but production code could disable dad again later, we should never see any
        // multicast NS for duplicate address detection then.
        mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "dad_transmits", "1");
        startIpClientProvisioning(config.build());
        verify(mNetd, timeout(TEST_TIMEOUT_MS)).interfaceSetEnableIPv6(mIfaceName, true);
        // Check dad_transmits should be set to 0 if UniqueEui64AddressesOnly mode is enabled.
        int dadTransmits = Integer.parseUnsignedInt(
                mNetd.getProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "dad_transmits"));
        if (shouldDisableDad) {
            assertEquals(0, dadTransmits);
        } else {
            assertEquals(1, dadTransmits);
        }

        final NeighborSolicitation ns =
                expectDadNeighborSolicitationForLinkLocal(shouldDisableDad);
        if (shouldDisableDad) {
            assertNull(ns);
        } else {
            assertNotNull(ns);
        }

        // Shutdown IpClient and check if the dad_transmits always equals to default value 1 (if
        // dad_transmit was set to 0 before, it should get recovered to default value 1 after
        // shutting down IpClient)
        mIpc.shutdown();
        awaitIpClientShutdown();
        dadTransmits = Integer.parseUnsignedInt(
                mNetd.getProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "dad_transmits"));
        assertEquals(1, dadTransmits);
    }

    @Test
    @SignatureRequiredTest(reason = "requires mocked netd")
    public void testIPv6LinkLocalOnly_enableDad() throws Exception {
        runIpv6LinkLocalOnlyDadTransmitsCheckTest(false /* shouldDisableDad */);
    }

    @Test
    @SignatureRequiredTest(reason = "requires mocked netd")
    public void testIPv6LinkLocalOnly_disableDad() throws Exception {
        runIpv6LinkLocalOnlyDadTransmitsCheckTest(true /* shouldDisableDad */);
    }

    // Since createTapInterface(boolean, String) method was introduced since T, this method
    // cannot be found on Q/R/S platform, ignore this test on T- platform.
    @Test
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testIpClientLinkObserver_onClatInterfaceStateUpdate() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);
        doIpv6OnlyProvisioning();

        reset(mCb);

        // Add the clat interface and check the callback.
        final TestNetworkInterface clatIface = setUpClatInterface(mIfaceName);
        assertNotNull(clatIface);
        assertTrue(clatIface.getInterfaceName().equals(CLAT_PREFIX + mIfaceName));
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setNeighborDiscoveryOffload(false);

        // Remove the clat interface and check the callback.
        removeTestInterface(clatIface.getFileDescriptor().getFileDescriptor());
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setNeighborDiscoveryOffload(true);
    }

    @Test @SignatureRequiredTest(reason = "requires mock callback object")
    public void testNetlinkSocketReceiveENOBUFS() throws Exception {
        // Only run the test when the flag of parsing netlink events is enabled.
        assumeTrue(mIsNetlinkEventParseEnabled);

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);
        doIpv6OnlyProvisioning();
        HandlerUtils.waitForIdle(mIpc.getHandler(), TEST_TIMEOUT_MS);

        final Handler handler = mIpc.getHandler();
        // Block IpClient handler.
        final CountDownLatch latch = new CountDownLatch(1);
        handler.post(() -> {
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("latch wait unexpectedly interrupted");
            }
        });

        // Send large amount of RAs to overflow the netlink socket receive buffer.
        for (int i = 0; i < 200; i++) {
            sendBasicRouterAdvertisement(false /* waitRs */);
        }

        // Send another RA with a different IPv6 global prefix. This PIO option should be dropped
        // due to the ENOBUFS happens, it means IpClient shouldn't see the new IPv6 global prefix.
        final String prefix = "2001:db8:dead:beef::/64";
        final ByteBuffer pio = buildPioOption(3600, 1800, prefix);
        ByteBuffer rdnss = buildRdnssOption(3600, IPV6_OFF_LINK_DNS_SERVER);
        sendRouterAdvertisement(false /* waitForRs */, (short) 1800, pio, rdnss);

        // Unblock the IpClient handler and ENOBUFS should happen then.
        latch.countDown();
        HandlerUtils.waitForIdle(handler, TEST_WAIT_ENOBUFS_TIMEOUT_MS);

        reset(mCb);

        // Send RA with 0 router lifetime to see if IpClient can see the loss of IPv6 default route.
        // Due to ignoring the ENOBUFS and wait until handler gets idle, IpClient should be still
        // able to see the RA with 0 router lifetime and the IPv6 default route will be removed.
        // LinkProperties should not include any route to the new prefix 2001:db8:dead:beef::/64.
        sendRouterAdvertisementWithZeroRouterLifetime();
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertFalse(hasRouteTo(lp, prefix));
        assertFalse(lp.hasIpv6DefaultRoute());
    }

    @Test
    public void testMulticastNsFromIPv6Gua() throws Exception {
        final ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv4()
                .build();

        startIpClientProvisioning(config);

        doIpv6OnlyProvisioning();

        final List<NeighborSolicitation> nsList = new ArrayList<>();
        NeighborSolicitation packet;
        while ((packet = getNextNeighborSolicitation()) != null) {
            // Filter out the NSes used for duplicate address detetction, whose target address
            // is the global IPv6 address inside these NSes.
            if (packet.nsHdr.target.isLinkLocalAddress()) {
                assertMulticastNsFromIpv6Gua(packet);
                nsList.add(packet);
            }
        }
        assertEquals(2, nsList.size()); // from privacy address and stable privacy address
    }

    @Test
    public void testDeprecatedGlobalUnicastAddress() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);
        doIpv6OnlyProvisioning();

        // Send RA with PIO(0 preferred but valid lifetime) to deprecate the global IPv6 addresses.
        // Check all of global IPv6 addresses will become deprecated, but still valid.
        // NetworkStackUtils#isIPv6GUA() will return false for deprecated addresses, however, when
        // checking if the DNS is still reachable, deprecated addresses are not acceptable, that
        // results in the on-link DNS server gets lost from LinkProperties, and provisioning failure
        // happened.
        // TODO: update the logic of checking reachable on-link DNS server to accept the deprecated
        // addresses, then onProvisioningFailure callback should never happen.
        sendRouterAdvertisement(false /* waitForRs*/, (short) 1800 /* router lifetime */,
                3600 /* valid */, 0 /* preferred */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertFalse(lp.hasGlobalIpv6Address());
        assertEquals(3, lp.getLinkAddresses().size()); // IPv6 privacy, stable privacy, link-local
        for (LinkAddress la : lp.getLinkAddresses()) {
            assertFalse(NetworkStackUtils.isIPv6GUA(la));
        }
    }

    @Test @SignatureRequiredTest(reason = "requires mNetd to delete IPv6 GUAs")
    public void testOnIpv6AddressRemoved() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);

        LinkProperties lp = doIpv6OnlyProvisioning();
        assertNotNull(lp);
        assertEquals(3, lp.getLinkAddresses().size()); // IPv6 privacy, stable privacy, link-local
        for (LinkAddress la : lp.getLinkAddresses()) {
            final Inet6Address address = (Inet6Address) la.getAddress();
            if (address.isLinkLocalAddress()) continue;
            // Remove IPv6 GUAs from interface.
            mNetd.interfaceDelAddress(mIfaceName, address.getHostAddress(), la.getPrefixLength());
        }

        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(captor.capture());
        lp = captor.getValue();
        assertFalse(lp.hasGlobalIpv6Address());
        assertEquals(1, lp.getLinkAddresses().size()); // only link-local
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_IPv6OnlyNetwork() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);

        verify(mCb, timeout(TEST_TIMEOUT_MS)).setMaxDtimMultiplier(
                IpClient.DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER);

        LinkProperties lp = doIpv6OnlyProvisioning();
        assertNotNull(lp);
        assertEquals(3, lp.getLinkAddresses().size()); // IPv6 privacy, stable privacy, link-local
        verify(mCb, timeout(TEST_TIMEOUT_MS)).setMaxDtimMultiplier(
                IpClient.DEFAULT_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_IPv6LinkLocalOnlyMode() throws Exception {
        final InOrder inOrder = inOrder(mCb);
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .withIpv6LinkLocalOnly()
                .build();
        startIpClientProvisioning(config);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(any());
        // IPv6 DTIM grace period doesn't apply to IPv6 link-local only mode and the multiplier
        // has been initialized to DTIM_MULTIPLIER_RESET before starting provisioning, therefore,
        // the multiplier should not be updated neither.
        verify(mCb, never()).setMaxDtimMultiplier(
                IpClient.DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER);
        verify(mCb, never()).setMaxDtimMultiplier(DTIM_MULTIPLIER_RESET);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_IPv4OnlyNetwork() throws Exception {
        performDhcpHandshake(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */,
                TEST_DEFAULT_MTU, false /* isDhcpIpConflictDetectEnabled */);
        verifyIPv4OnlyProvisioningSuccess(Collections.singletonList(CLIENT_ADDR));
        verify(mCb, timeout(TEST_TIMEOUT_MS).times(1)).setMaxDtimMultiplier(
                IpClient.DEFAULT_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        // IPv6 DTIM grace period doesn't apply to IPv4-only networks.
        verify(mCb, never()).setMaxDtimMultiplier(
                IpClient.DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER);
    }

    private void runDualStackNetworkDtimMultiplierSetting(final InOrder inOrder) throws Exception {
        doDualStackProvisioning();
        inOrder.verify(mCb).setMaxDtimMultiplier(
                IpClient.DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER);
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).setMaxDtimMultiplier(
                IpClient.DEFAULT_DUAL_STACK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_DualStackNetwork() throws Exception {
        final InOrder inOrder = inOrder(mCb);
        runDualStackNetworkDtimMultiplierSetting(inOrder);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_MulticastLock() throws Exception {
        final InOrder inOrder = inOrder(mCb);
        runDualStackNetworkDtimMultiplierSetting(inOrder);

        // Simulate to hold the multicast lock by disabling the multicast filter.
        mIIpClient.setMulticastFilter(false);
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).setMaxDtimMultiplier(
                IpClient.DEFAULT_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);

        // Simulate to disable the multicast lock again, then check the multiplier should be
        // changed to 2 (dual-stack setting)
        mIIpClient.setMulticastFilter(true);
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).setMaxDtimMultiplier(
                IpClient.DEFAULT_DUAL_STACK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_MulticastLockEnabled_StoppedState() throws Exception {
        // Simulate to hold the multicast lock by disabling the multicast filter at StoppedState,
        // verify no callback to be sent, start dual-stack provisioning and verify the multiplier
        // to be set to 1 (multicast lock setting) later.
        mIIpClient.setMulticastFilter(false);
        verify(mCb, after(10).never()).setMaxDtimMultiplier(
                IpClient.DEFAULT_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);

        doDualStackProvisioning();
        verify(mCb, times(1)).setMaxDtimMultiplier(
                IpClient.DEFAULT_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMaxDtimMultiplier_resetMultiplier() throws Exception {
        final InOrder inOrder = inOrder(mCb);
        runDualStackNetworkDtimMultiplierSetting(inOrder);

        verify(mCb, never()).setMaxDtimMultiplier(DTIM_MULTIPLIER_RESET);

        // Stop IpClient and verify if the multiplier has been reset.
        mIIpClient.stop();
        inOrder.verify(mCb, timeout(TEST_TIMEOUT_MS)).setMaxDtimMultiplier(DTIM_MULTIPLIER_RESET);
    }

    private IaPrefixOption buildIaPrefixOption(final IpPrefix prefix, int preferred,
            int valid) {
        return new IaPrefixOption((short) IaPrefixOption.LENGTH, preferred, valid,
                (byte) prefix.getPrefixLength(), prefix.getRawAddress() /* prefix */);
    }

    private void handleDhcp6Packets(final IpPrefix prefix, boolean shouldReplyRapidCommit)
            throws Exception {
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 4500 /* preferred */,
                7200 /* valid */);
        handleDhcp6Packets(Collections.singletonList(ipo), 3600 /* t1 */, 4500 /* t2 */,
                shouldReplyRapidCommit);
    }

    private void handleDhcp6Packets(final List<IaPrefixOption> ipos, int t1, int t2,
            boolean shouldReplyRapidCommit) throws Exception {
        ByteBuffer iapd;
        Dhcp6Packet packet;
        while ((packet = getNextDhcp6Packet()) != null) {
            final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), t1, t2, ipos);
            iapd = pd.build();
            if (packet instanceof Dhcp6SolicitPacket) {
                if (shouldReplyRapidCommit) {
                    mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                            (Inet6Address) mClientIpAddress, true /* rapidCommit */));
                } else {
                    mPacketReader.sendResponse(buildDhcp6Advertise(packet, iapd.array(), mClientMac,
                            (Inet6Address) mClientIpAddress));
                }
            } else if (packet instanceof Dhcp6RequestPacket) {
                mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                          (Inet6Address) mClientIpAddress, false /* rapidCommit */));
            } else {
                fail("invalid DHCPv6 Packet");
            }

            if ((packet instanceof Dhcp6RequestPacket) || shouldReplyRapidCommit) {
                return;
            }
        }
        fail("No DHCPv6 packet received on interface within timeout");
    }

    private void prepareDhcp6PdTest() throws Exception {
        final String dnsServer = "2001:4860:4860::64";
        final ByteBuffer rdnss = buildRdnssOption(3600, dnsServer);
        final ByteBuffer ra = buildRaPacket(rdnss);

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);

        waitForRouterSolicitation();
        mPacketReader.sendResponse(ra);
    }

    @Test
    public void testDhcp6Pd() throws Exception {
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        prepareDhcp6PdTest();
        handleDhcp6Packets(prefix, true /* shouldReplyRapidCommit */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertTrue(hasIpv6AddressPrefixedWith(lp, prefix));

        // Only run the test when the flag of parsing netlink events is enabled, where the
        // deprecationTime and expirationTime is set.
        if (mIsNetlinkEventParseEnabled) {
            final long now = SystemClock.elapsedRealtime();
            long when = 0;
            for (LinkAddress la : lp.getLinkAddresses()) {
                if (la.getAddress().isLinkLocalAddress()) {
                    assertLinkAddressPermanentLifetime(la);
                } else if (la.isGlobalPreferred()) {
                    when = now + 4500 * 1000; // preferred=4500s
                    assertLinkAddressDeprecationTime(la, when);
                    when = now + 7200 * 1000; // valid=7200s
                    assertLinkAddressExpirationTime(la, when);
                }
            }
        }
    }

    @Test
    public void testDhcp6Pd_disableRapidCommit() throws Exception {
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        prepareDhcp6PdTest();
        handleDhcp6Packets(prefix, false /* shouldReplyRapidCommit */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        assertTrue(hasIpv6AddressPrefixedWith(captor.getValue(), prefix));
    }

    @Test
    public void testDhcp6Pd_longPrefixLength() throws Exception {
        prepareDhcp6PdTest();
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/80");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 3600 /* preferred */,
                4000 /* valid */);
        handleDhcp6Packets(Collections.singletonList(ipo), 3600 /* t1 */, 4500 /* t2 */,
                true /* shouldReplyRapidCommit */);
        verify(mCb, never()).onProvisioningSuccess(any());
    }

    @Test
    public void testDhcp6Pd_shortPrefixLength() throws Exception {
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/56");
        prepareDhcp6PdTest();
        handleDhcp6Packets(prefix, true /* shouldReplyRapidCommit */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        assertTrue(hasIpv6AddressPrefixedWith(captor.getValue(), prefix));
    }

    @Test
    public void testDhcp6Pd_T1GreaterThanT2() throws Exception {
        prepareDhcp6PdTest();
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 3600 /* preferred */,
                4000 /* valid */);
        handleDhcp6Packets(Collections.singletonList(ipo), 4500 /* t1 */, 3600 /* t2 */,
                true /* shouldReplyRapidCommit */);
        verify(mCb, never()).onProvisioningSuccess(any());
    }

    @Test
    public void testDhcp6Pd_preferredLifetimeGreaterThanValidLifetime() throws Exception {
        prepareDhcp6PdTest();
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 7200 /* preferred */,
                4500 /* valid */);
        handleDhcp6Packets(Collections.singletonList(ipo), 3600 /* t1 */, 4500 /* t2 */,
                true /* shouldReplyRapidCommit */);
        verify(mCb, never()).onProvisioningSuccess(any());
    }

    @Test
    public void testDhcp6Pd_preferredLifetimeLessThanT2() throws Exception {
        prepareDhcp6PdTest();
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 3600 /* preferred */,
                4000 /* valid */);
        handleDhcp6Packets(Collections.singletonList(ipo), 3600 /* t1 */, 4500 /* t2 */,
                true /* shouldReplyRapidCommit */);
        verify(mCb, never()).onProvisioningSuccess(any());
    }

    private void runDhcp6PdNotStartInDualStackTest(final String prefix, final String dnsServer)
            throws Exception {
        final List<ByteBuffer> options = new ArrayList<>();
        if (prefix != null) {
            options.add(buildPioOption(3600, 1800, prefix));
        }
        if (dnsServer != null) {
            options.add(buildRdnssOption(3600, dnsServer));
        }
        options.add(buildSllaOption());
        final ByteBuffer ra = buildRaPacket(options.toArray(new ByteBuffer[options.size()]));

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .build();
        setDhcpFeatures(true /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);

        waitForRouterSolicitation();
        mPacketReader.sendResponse(ra);

        // Start IPv4 provisioning and wait until entire provisioning completes.
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                true /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU, null /* serverSentUrl */);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(any());
    }

    @Test
    public void testDhcp6Pd_notStartWithGlobalPio() throws Exception {
        runDhcp6PdNotStartInDualStackTest("2001:db8:1::/64" /* prefix */,
                "2001:4860:4860::64" /* dnsServer */);
        // Reply with a normal RA with global prefix and an off-link DNS for IPv6 provisioning,
        // DHCPv6 prefix delegation should not start.
        assertNull(getNextDhcp6Packet(PACKET_TIMEOUT_MS));
    }

    @Test
    public void testDhcp6Pd_notStartWithUlaPioAndDns() throws Exception {
        runDhcp6PdNotStartInDualStackTest("fd7c:9df8:7f39:dc89::/64" /* prefix */,
                "fd7c:9df8:7f39:dc89::1"  /* dnsServer */);
        // Reply with a normal RA even with ULA prefix and on-link ULA DNS for IPv6 provisioning,
        // DHCPv6 prefix delegation should not start.
        assertNull(getNextDhcp6Packet(PACKET_TIMEOUT_MS));
    }

    @Test
    public void testDhcp6Pd_notStartWithUlaPioAndOffLinkDns() throws Exception {
        runDhcp6PdNotStartInDualStackTest("fd7c:9df8:7f39:dc89::/64" /* prefix */,
                "2001:4860:4860::64"  /* dnsServer */);
        // Reply with a normal RA even with ULA prefix and off-link DNS for IPv6 provisioning,
        // DHCPv6 prefix delegation should not start.
        assertNull(getNextDhcp6Packet(PACKET_TIMEOUT_MS));
    }

    @Test
    public void testDhcp6Pd_startWithNoNonIpv6LinkLocalAddresses() throws Exception {
        runDhcp6PdNotStartInDualStackTest(null /* prefix */,
                "2001:4860:4860::64"  /* dnsServer */);
        // Reply with a normal RA with only RDNSS but no PIO for IPv6 provisioning,
        // DHCPv6 prefix delegation should start.
        final Dhcp6Packet packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertTrue(packet instanceof Dhcp6SolicitPacket);
    }

    @Test
    public void testDhcp6Pd_dualstack() throws Exception {
        final String dnsServer = "2001:4860:4860::64";
        final ByteBuffer rdnss = buildRdnssOption(3600, dnsServer);
        final ByteBuffer ra = buildRaPacket(rdnss);

        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .build();
        setDhcpFeatures(true /* isRapidCommitEnabled */,
                false /* isDhcpIpConflictDetectEnabled */);
        startIpClientProvisioning(config);

        waitForRouterSolicitation();
        mPacketReader.sendResponse(ra);

        // Start IPv4 provisioning and wait until entire provisioning completes.
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                true /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU, null /* serverSentUrl */);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(any());

        // Start DHCPv6 Prefix Delegation.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        handleDhcp6Packets(prefix, false /* shouldReplyRapidCommit */);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(
                x -> x.isIpv6Provisioned()
                        && hasIpv6AddressPrefixedWith(x, prefix)
                        && hasRouteTo(x, "2001:db8:1::/64", RTN_UNREACHABLE)
                        // IPv4 address, IPv6 link-local, two global delegated IPv6 addresses
                        && x.getLinkAddresses().size() == 4
        ));
    }

    @Test
    public void testDhcp6Pd_multiplePrefixesWithInvalidPrefix() throws Exception {
        final IpPrefix valid = new IpPrefix("2001:db8:1::/64");
        final IpPrefix invalid = new IpPrefix("2001:db8:2::/64"); // preferred lft > valid lft
        final IaPrefixOption validIpo = buildIaPrefixOption(valid, 4500 /* preferred */,
                7200 /* valid */);
        final IaPrefixOption invalidIpo = buildIaPrefixOption(invalid, 4500 /* preferred */,
                3000 /* valid */);

        prepareDhcp6PdTest();
        handleDhcp6Packets(Arrays.asList(invalidIpo, validIpo), 3600 /* t1 */, 4500 /* t2 */,
                true /* shouldReplyRapidCommit */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertTrue(hasIpv6AddressPrefixedWith(lp, valid));
        assertFalse(hasIpv6AddressPrefixedWith(lp, invalid));
    }

    @Test
    public void testDhcp6Pd_multiplePrefixesWithPrefixValidLifetimeOfZero() throws Exception {
        final IpPrefix valid = new IpPrefix("2001:db8:1::/64");
        final IpPrefix invalid = new IpPrefix("2001:db8:2::/64"); // preferred/valid lft 0
        final IaPrefixOption validIpo = buildIaPrefixOption(valid, 4500 /* preferred */,
                7200 /* valid */);
        final IaPrefixOption invalidIpo = buildIaPrefixOption(invalid, 0 /* preferred */,
                0 /* valid */);

        prepareDhcp6PdTest();
        handleDhcp6Packets(Arrays.asList(invalidIpo, validIpo), 3600 /* t1 */, 4500 /* t2 */,
                true /* shouldReplyRapidCommit */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertTrue(hasIpv6AddressPrefixedWith(lp, valid));
        assertFalse(hasIpv6AddressPrefixedWith(lp, invalid));
    }

    private void prepareDhcp6PdRenewTest() throws Exception {
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        prepareDhcp6PdTest();
        handleDhcp6Packets(prefix, true /* shouldReplyRapidCommit */);
        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        assertTrue(hasIpv6AddressPrefixedWith(captor.getValue(), prefix));
    }

    @Test
    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    public void testDhcp6Pd_renewAndRebind() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);
        final OnAlarmListener rebindAlarm = expectAlarmSet(inOrder, "REBIND", 4500, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        handler.post(() -> rebindAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RebindPacket);
    }

    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    @Test
    public void testDhcp6Pd_prefixMismatchOnRenew_newPrefix() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        // Reply with a new prefix apart of the requested one, per RFC8415#section-18.2.10.1
        // any new prefix should be added.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IpPrefix prefix1 = new IpPrefix("2001:db8:2::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 4500 /* preferred */,
                7200 /* valid */);
        final IaPrefixOption ipo1 = buildIaPrefixOption(prefix1, 5000 /* preferred */,
                6000 /* valid */);
        final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */,
                4500 /* t2 */, Arrays.asList(ipo, ipo1));
        final ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        verify(mCb, never()).onProvisioningFailure(any());
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(
                x -> x.isIpv6Provisioned()
                        && hasIpv6AddressPrefixedWith(x, prefix)
                        && hasIpv6AddressPrefixedWith(x, prefix1)
                        && hasRouteTo(x, "2001:db8:1::/64", RTN_UNREACHABLE)
                        && hasRouteTo(x, "2001:db8:2::/64", RTN_UNREACHABLE)
                        // IPv6 link-local, four global delegated IPv6 addresses
                        && x.getLinkAddresses().size() == 5
        ));
    }

    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    @Test
    public void testDhcp6Pd_prefixMismatchOnRenew_requestedPrefixAbsent() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        // Reply with a new prefix but the requested one is absent, per RFC8415#section-18.2.10.1
        // the new prefix should be added and the absent prefix will expire in nature.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IpPrefix prefix1 = new IpPrefix("2001:db8:2::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix1, 4500 /* preferred */,
                7200 /* valid */);
        final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */,
                4500 /* t2 */, Arrays.asList(ipo));
        final ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        verify(mCb, never()).onProvisioningFailure(any());
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(
                x -> x.isIpv6Provisioned()
                        && hasIpv6AddressPrefixedWith(x, prefix)
                        && hasIpv6AddressPrefixedWith(x, prefix1)
                        && hasRouteTo(x, "2001:db8:1::/64", RTN_UNREACHABLE)
                        && hasRouteTo(x, "2001:db8:2::/64", RTN_UNREACHABLE)
                        // IPv6 link-local, four global delegated IPv6 addresses
                        && x.getLinkAddresses().size() == 5
        ));
    }

    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    @Test
    public void testDhcp6Pd_prefixMismatchOnRenew_allPrefixesAbsent() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        clearInvocations(mCb);

        // Reply with IA_PD but IA_Prefix is absent, client should still stay at the RenewState
        // and restransmit the Renew message, that should not result in any LinkProperties update.
        final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */,
                4500 /* t2 */, new ArrayList<IaPrefixOption>(0));
        final ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        verify(mCb, never()).onLinkPropertiesChange(any());
    }

    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    @Test
    public void testDhcp6Pd_renewInvalidPrefixes_zeroPreferredAndValidLifetime() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        // Reply with the requested prefix with preferred/valid lifetime of 0.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IpPrefix prefix1 = new IpPrefix("2001:db8:2::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 0 /* preferred */,
                0 /* valid */);
        final IaPrefixOption ipo1 = buildIaPrefixOption(prefix1, 5000 /* preferred */,
                6000 /* valid */);
        final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */,
                4500 /* t2 */, Arrays.asList(ipo, ipo1));
        final ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        verify(mCb, never()).onProvisioningFailure(any());
        // IPv6 addresses derived from prefix with 0 preferred/valid lifetime should be deleted.
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(
                x -> x.isIpv6Provisioned()
                        && !hasIpv6AddressPrefixedWith(x, prefix)
                        && hasIpv6AddressPrefixedWith(x, prefix1)
                        && !hasRouteTo(x, "2001:db8:1::/64", RTN_UNREACHABLE)
                        && hasRouteTo(x, "2001:db8:2::/64", RTN_UNREACHABLE)
                        // IPv6 link-local, two global delegated IPv6 addresses with prefix1
                        && x.getLinkAddresses().size() == 3
        ));

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);
        final List<IaPrefixOption> renewIpos = packet.getPrefixDelegation().ipos;
        assertEquals(1, renewIpos.size()); // don't renew prefix 2001:db8:1::/64 with 0
                                           // preferred/valid lifetime
        assertEquals(prefix1, renewIpos.get(0).getIpPrefix());
    }

    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    @Test
    public void testDhcp6Pd_renewInvalidPrefixes_theSameT1T2ValidLifetime() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        clearInvocations(mCb);

        // Reply with the requested prefix with the same t1/t2/lifetime.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 3600 /* preferred */,
                3600 /* valid */);
        final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */,
                3600 /* t2 */, Collections.singletonList(ipo));
        final ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        // The prefix doesn't change only the lifetime is updated, therefore, LinkProperties update
        // isn't expected.
        verify(mCb, never()).onProvisioningFailure(any());
        verify(mCb, never()).onLinkPropertiesChange(any());

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet(TEST_TIMEOUT_MS);
        assertNull(packet);
    }

    @Test
    public void testDhcp6Pd_multipleIaPrefixOptions() throws Exception {
        final InOrder inOrder = inOrder(mCb);
        final IpPrefix prefix1 = new IpPrefix("2001:db8:1::/64");
        final IpPrefix prefix2 = new IpPrefix("2400:db8:100::/64");
        final IpPrefix prefix3 = new IpPrefix("fd7c:9df8:7f39:dc89::/64");
        final IaPrefixOption ipo1 = buildIaPrefixOption(prefix1, 4500 /* preferred */,
                7200 /* valid */);
        final IaPrefixOption ipo2 = buildIaPrefixOption(prefix2, 5600 /* preferred */,
                6000 /* valid */);
        final IaPrefixOption ipo3 = buildIaPrefixOption(prefix3, 7200 /* preferred */,
                14400 /* valid */);
        prepareDhcp6PdTest();
        handleDhcp6Packets(Arrays.asList(ipo1, ipo2, ipo3), 3600 /* t1 */, 4500 /* t2 */,
                true /* shouldReplyRapidCommit */);

        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verifyWithTimeout(inOrder, mCb).onProvisioningSuccess(captor.capture());
        LinkProperties lp = captor.getValue();

        // Sometimes privacy address or route may appear later along with onLinkPropertiesChange
        // callback, in this case we wait a bit longer to see all of these properties appeared and
        // then verify if they are what we are looking for.
        if (lp.getLinkAddresses().size() < 5 || lp.getRoutes().size() < 4) {
            final CompletableFuture<LinkProperties> lpFuture = new CompletableFuture<>();
            verifyWithTimeout(inOrder, mCb).onLinkPropertiesChange(argThat(x -> {
                if (!x.isIpv6Provisioned()) return false;
                if (x.getLinkAddresses().size() != 5) return false;
                if (x.getRoutes().size() != 4) return false;
                lpFuture.complete(x);
                return true;
            }));
            lp = lpFuture.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        assertNotNull(lp);
        assertTrue(hasIpv6AddressPrefixedWith(lp, prefix1));
        assertTrue(hasIpv6AddressPrefixedWith(lp, prefix2));
        assertFalse(hasIpv6AddressPrefixedWith(lp, prefix3));
        assertTrue(hasRouteTo(lp, prefix1.toString(), RTN_UNREACHABLE));
        assertTrue(hasRouteTo(lp, prefix2.toString(), RTN_UNREACHABLE));
        assertFalse(hasRouteTo(lp, prefix3.toString(), RTN_UNREACHABLE));
    }

    private void runDhcp6PacketWithNoPrefixAvailStatusCodeTest(boolean shouldReplyWithAdvertise)
            throws Exception {
        prepareDhcp6PdTest();
        Dhcp6Packet packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertTrue(packet instanceof Dhcp6SolicitPacket);

        final PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 0 /* t1 */, 0 /* t2 */,
                new ArrayList<IaPrefixOption>() /* ipos */, Dhcp6Packet.STATUS_NO_PREFIX_AVAIL);
        final ByteBuffer iapd = pd.build();
        if (shouldReplyWithAdvertise) {
            mPacketReader.sendResponse(buildDhcp6Advertise(packet, iapd.array(), mClientMac,
                    (Inet6Address) mClientIpAddress));
        } else {
            mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                    (Inet6Address) mClientIpAddress, true /* rapidCommit */));
        }

        // Check if client will ignore Advertise or Reply for Rapid Commit Solicit and
        // retransmit Solicit.
        packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertTrue(packet instanceof Dhcp6SolicitPacket);
    }

    @Test
    public void testDhcp6AdvertiseWithNoPrefixAvailStatusCode() throws Exception {
        // Advertise
        runDhcp6PacketWithNoPrefixAvailStatusCodeTest(true /* shouldReplyWithAdvertise */);
    }

    @Test
    public void testDhcp6ReplyForRapidCommitSolicitWithNoPrefixAvailStatusCode() throws Exception {
        // Reply
        runDhcp6PacketWithNoPrefixAvailStatusCodeTest(false /* shouldReplyWithAdvertise */);
    }

    @Test
    public void testDhcp6ReplyForRequestWithNoPrefixAvailStatusCode() throws Exception {
        prepareDhcp6PdTest();
        Dhcp6Packet packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertTrue(packet instanceof Dhcp6SolicitPacket);

        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 4500 /* preferred */,
                7200 /* valid */);
        PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 1000 /* t1 */,
                2000 /* t2 */, Arrays.asList(ipo));
        ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Advertise(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress));

        packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertTrue(packet instanceof Dhcp6RequestPacket);

        // Reply for Request with NoPrefixAvail status code. Not sure if this is reasonable in
        // practice, but Server can do everything it wants.
        pd = new PrefixDelegation(packet.getIaId(), 0 /* t1 */, 0 /* t2 */,
                new ArrayList<IaPrefixOption>() /* ipos */, Dhcp6Packet.STATUS_NO_PREFIX_AVAIL);
        iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));

        // Check if client will ignore Reply for Request with NoPrefixAvail status code, and
        // rollback to SolicitState.
        packet = getNextDhcp6Packet(PACKET_TIMEOUT_MS);
        assertTrue(packet instanceof Dhcp6SolicitPacket);
    }

    @Test
    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    public void testDhcp6ReplyForRenewWithNoPrefixAvailStatusCode() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        // Reply with normal IA_PD.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 4500 /* preferred */,
                7200 /* valid */);
        PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 1000 /* t1 */,
                2000 /* t2 */, Arrays.asList(ipo));
        ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        // Trigger another Renew message.
        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        // Reply for Renew with NoPrefixAvail status code, check if client will retransmit the
        // Renew message.
        pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */, 4500 /* t2 */,
                new ArrayList<IaPrefixOption>(0) /* ipos */, Dhcp6Packet.STATUS_NO_PREFIX_AVAIL);
        iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));

        packet = getNextDhcp6Packet(TEST_WAIT_RENEW_REBIND_RETRANSMIT_MS);
        assertTrue(packet instanceof Dhcp6RenewPacket);
    }

    @Test
    @SignatureRequiredTest(reason = "Need to mock the DHCP6 renew/rebind alarms")
    public void testDhcp6ReplyForRebindWithNoPrefixAvailStatusCode() throws Exception {
        prepareDhcp6PdRenewTest();

        final InOrder inOrder = inOrder(mAlarm);
        final Handler handler = mDependencies.mDhcp6Client.getHandler();
        final OnAlarmListener renewAlarm = expectAlarmSet(inOrder, "RENEW", 3600, handler);
        final OnAlarmListener rebindAlarm = expectAlarmSet(inOrder, "REBIND", 4500, handler);

        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        Dhcp6Packet packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        handler.post(() -> rebindAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RebindPacket);

        // Reply with normal IA_PD.
        final IpPrefix prefix = new IpPrefix("2001:db8:1::/64");
        final IaPrefixOption ipo = buildIaPrefixOption(prefix, 4500 /* preferred */,
                7200 /* valid */);
        PrefixDelegation pd = new PrefixDelegation(packet.getIaId(), 1000 /* t1 */,
                2000 /* t2 */, Arrays.asList(ipo));
        ByteBuffer iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        // Trigger another Rebind message.
        handler.post(() -> renewAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RenewPacket);

        handler.post(() -> rebindAlarm.onAlarm());
        HandlerUtils.waitForIdle(handler, TEST_TIMEOUT_MS);

        packet = getNextDhcp6Packet();
        assertTrue(packet instanceof Dhcp6RebindPacket);

        // Reply for Rebind with NoPrefixAvail status code, check if client will retransmit the
        // Rebind message.
        pd = new PrefixDelegation(packet.getIaId(), 3600 /* t1 */,
                4500 /* t2 */, new ArrayList<IaPrefixOption>(0) /* ipos */,
                Dhcp6Packet.STATUS_NO_PREFIX_AVAIL);
        iapd = pd.build();
        mPacketReader.sendResponse(buildDhcp6Reply(packet, iapd.array(), mClientMac,
                (Inet6Address) mClientIpAddress, false /* rapidCommit */));

        packet = getNextDhcp6Packet(TEST_WAIT_RENEW_REBIND_RETRANSMIT_MS);
        assertTrue(packet instanceof Dhcp6RebindPacket);
    }

    @Test
    @SignatureRequiredTest(reason = "InterfaceParams.getByName requires CAP_NET_ADMIN")
    public void testSendRtmDelAddressMethod() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        startIpClientProvisioning(config);

        final LinkProperties lp = doIpv6OnlyProvisioning();
        assertNotNull(lp);
        assertEquals(3, lp.getLinkAddresses().size()); // IPv6 privacy, stable privacy, link-local

        clearInvocations(mCb);

        // Delete all global IPv6 addresses, then that will trigger onProvisioningFailure callback.
        final InterfaceParams params = InterfaceParams.getByName(mIfaceName);
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.isGlobalPreferred()) {
                NetlinkUtils.sendRtmDelAddressRequest(params.index, (Inet6Address) la.getAddress(),
                        (short) la.getPrefixLength());
                verify(mCb, timeout(TEST_TIMEOUT_MS)).onLinkPropertiesChange(argThat(
                        x -> !x.getLinkAddresses().contains(la)
                ));
            }
        }
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningFailure(any());
    }

    @Test
    @SignatureRequiredTest(reason = "requires mocked netd to read/write IPv6 sysctl")
    public void testIpv6SysctlsRestAfterStoppingIpClient() throws Exception {
        ProvisioningConfiguration config = new ProvisioningConfiguration.Builder()
                .withoutIPv4()
                .build();
        // dad_transmits has been set to 0 in disableIpv6ProvisioningDelays, re-enable
        // dad_transmits for testing, production code will restore all IPv6 sysctls at
        // StoppedState#enter anyway, read this parameter value after IpClient shutdown
        // to check if that's default value 1.
        mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "dad_transmits", "1");
        startIpClientProvisioning(config);
        verify(mNetd, timeout(TEST_TIMEOUT_MS)).interfaceSetEnableIPv6(mIfaceName, true);
        doIpv6OnlyProvisioning();

        // Shutdown IpClient and check if the IPv6 sysctls: accept_ra, accept_ra_defrtr and
        // dad_transmits have been reset to the default values.
        mIpc.shutdown();
        awaitIpClientShutdown();
        final int dadTransmits = Integer.parseUnsignedInt(
                mNetd.getProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "dad_transmits"));
        assertEquals(1, dadTransmits);
        final int acceptRa = Integer.parseUnsignedInt(
                mNetd.getProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "accept_ra"));
        assertEquals(2, acceptRa);
        final int acceptRaDefRtr = Integer.parseUnsignedInt(
                mNetd.getProcSysNet(INetd.IPV6, INetd.CONF, mIfaceName, "accept_ra_defrtr"));
        assertEquals(1, acceptRaDefRtr);
    }

    private void runDhcpDomainSearchListOptionTest(final String domainName,
            final List<String> domainSearchList, final String expectedDomain) throws Exception {
        when(mResources.getBoolean(R.bool.config_dhcp_client_domain_search_list)).thenReturn(true);
        final ProvisioningConfiguration cfg = new ProvisioningConfiguration.Builder()
                .withoutIpReachabilityMonitor()
                .withoutIPv6()
                .withCreatorUid(TEST_DEVICE_OWNER_APP_UID)
                .build();

        startIpClientProvisioning(cfg);
        handleDhcpPackets(true /* isSuccessLease */, TEST_LEASE_DURATION_S,
                false /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU,
                null /* captivePortalApiUrl */, null /* ipv6OnlyWaitTime */,
                domainName, domainSearchList);

        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        assertEquals(expectedDomain, lp.getDomains());
    }

    @Test
    @SignatureRequiredTest(reason = "requires mocked DevicePolicyManager")
    public void testDhcpDomainSearchListOption() throws Exception {
        final String domainName = "google.com";
        final List<String> searchList = List.of("suffix1.google.com", "suffix2.google.com");
        final String expectedDomain = "google.com suffix1.google.com suffix2.google.com";
        runDhcpDomainSearchListOptionTest(domainName, searchList, expectedDomain);
    }

    @Test
    @SignatureRequiredTest(reason = "requires mocked DevicePolicyManager")
    public void testDhcpDomainSearchListOption_invalidSuffix() throws Exception {
        final String domainName = "google.com";
        final List<String> searchList = List.of("google com");
        runDhcpDomainSearchListOptionTest(domainName, searchList, domainName /* expectedDomain */);
    }

    @Test
    @SignatureRequiredTest(reason = "requires mocked DevicePolicyManager")
    public void testDhcpDomainSearchListOption_onlySearchList() throws Exception {
        final List<String> searchList = List.of("google.com", "example.com");
        final String expectedDomain = "google.com example.com";
        runDhcpDomainSearchListOptionTest(null /* domainName */, searchList,
                expectedDomain);
    }

    private void assertLinkAddressDeprecationTime(final LinkAddress la, final long when) {
        assertTrue(la.getDeprecationTime() != LinkAddress.LIFETIME_UNKNOWN);
        // Allow +/- 2 seconds to prevent flaky tests
        assertTrue(la.getDeprecationTime() < when + TEST_LIFETIME_TOLERANCE_MS);
        assertTrue(la.getDeprecationTime() > when - TEST_LIFETIME_TOLERANCE_MS);
    }

    private void assertLinkAddressExpirationTime(final LinkAddress la, final long when) {
        assertTrue(la.getExpirationTime() != LinkAddress.LIFETIME_UNKNOWN);
        // Allow +/- 2 seconds to prevent flaky tests
        assertTrue(la.getExpirationTime() < when + TEST_LIFETIME_TOLERANCE_MS);
        assertTrue(la.getExpirationTime() > when - TEST_LIFETIME_TOLERANCE_MS);
    }

    private void assertLinkAddressPermanentLifetime(final LinkAddress la) {
        assertEquals(LinkAddress.LIFETIME_PERMANENT, la.getDeprecationTime());
        assertEquals(LinkAddress.LIFETIME_PERMANENT, la.getExpirationTime());
    }

    @Test
    public void testPopulateLinkAddressLifetime() throws Exception {
        // Only run the test when the flag of parsing netlink events is enabled to verify the
        // code of setting deprecationTime/expirationTime added when IpClientLinkObserver sees
        // the RTM_NEWADDR, and we are going to delete the dead old code path completely soon.
        assumeTrue(mIsNetlinkEventParseEnabled);

        final LinkProperties lp = doDualStackProvisioning();
        final long now = SystemClock.elapsedRealtime();
        long when = 0;
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.isIpv4()) {
                when = now + 3600 * 1000; // DHCP lease duration
                assertLinkAddressDeprecationTime(la, when);
                assertLinkAddressExpirationTime(la, when);
            } else if (la.isIpv6() && la.getAddress().isLinkLocalAddress()) {
                assertLinkAddressPermanentLifetime(la);
            } else if (la.isIpv6() && la.isGlobalPreferred()) {
                when = now + 1800 * 1000; // preferred=1800s
                assertLinkAddressDeprecationTime(la, when);
                when = now + 3600 * 1000; // valid=3600s
                assertLinkAddressExpirationTime(la, when);
            }
        }
    }

    @Test
    public void testPopulateLinkAddressLifetime_infiniteLeaseDuration() throws Exception {
        // Only run the test when the flag of parsing netlink events is enabled.
        assumeTrue(mIsNetlinkEventParseEnabled);

        final ProvisioningConfiguration cfg = new ProvisioningConfiguration.Builder()
                .withoutIPv6()
                .build();

        startIpClientProvisioning(cfg);
        handleDhcpPackets(true /* isSuccessLease */, DhcpPacket.INFINITE_LEASE,
                false /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU,
                null /* captivePortalApiUrl */, null /* ipv6OnlyWaitTime */,
                null /* domainName */, null /* domainSearchList */);

        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.isIpv4()) {
                assertLinkAddressPermanentLifetime(la);
            }
        }
    }

    @Test
    public void testPopulateLinkAddressLifetime_minimalLeaseDuration() throws Exception {
        // Only run the test when the flag of parsing netlink events is enabled.
        assumeTrue(mIsNetlinkEventParseEnabled);

        final ProvisioningConfiguration cfg = new ProvisioningConfiguration.Builder()
                .withoutIPv6()
                .build();

        startIpClientProvisioning(cfg);
        handleDhcpPackets(true /* isSuccessLease */, 59 /* lease duration */,
                false /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU,
                null /* captivePortalApiUrl */, null /* ipv6OnlyWaitTime */,
                null /* domainName */, null /* domainSearchList */);

        final ArgumentCaptor<LinkProperties> captor = ArgumentCaptor.forClass(LinkProperties.class);
        verify(mCb, timeout(TEST_TIMEOUT_MS)).onProvisioningSuccess(captor.capture());
        final LinkProperties lp = captor.getValue();
        assertNotNull(lp);
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.isIpv4()) {
                final long now = SystemClock.elapsedRealtime();
                final long when = now + 60 * 1000; // minimal lease duration
                assertLinkAddressDeprecationTime(la, when);
                assertLinkAddressExpirationTime(la, when);
            }
        }
    }

    private void doDhcpHostnameSettingTest(int hostnameSetting,
            boolean isHostnameConfigurationEnabled, boolean expectSendHostname) throws Exception {
        final ProvisioningConfiguration cfg = new ProvisioningConfiguration.Builder()
                .withoutIPv6()
                .withHostnameSetting(hostnameSetting)
                .build();
        final String expectedHostname;
        final String expectedHostnameAfterTransliteration;
        if (mDependencies != null) {
            mDependencies.setHostnameConfiguration(isHostnameConfigurationEnabled,
                    TEST_HOST_NAME);
            expectedHostname = TEST_HOST_NAME;
            expectedHostnameAfterTransliteration = TEST_HOST_NAME_TRANSLITERATION;
        } else {
            expectedHostname = Settings.Global.getString(
                    InstrumentationRegistry.getInstrumentation().getContext().getContentResolver(),
                    Settings.Global.DEVICE_NAME);
            expectedHostnameAfterTransliteration = new HostnameTransliterator()
                    .transliterate(expectedHostname);
        }
        startIpClientProvisioning(cfg);

        // perform DHCP handshake and capture the packets sent from client such as
        // DHCPDISCOVER and DHCPREQUEST.
        final List<DhcpPacket> sentPackets = handleDhcpPackets(true /* isSuccessLease */,
                DhcpPacket.INFINITE_LEASE,
                false /* shouldReplyRapidCommitAck */, TEST_DEFAULT_MTU,
                null /* captivePortalApiUrl */, null /* ipv6OnlyWaitTime */,
                null /* domainName */, null /* domainSearchList */);

        // check if the DHCP packet sent from the client takes a hostname option per different
        // configs. Do not consider the null hostname case.
        assertHostname(expectSendHostname, expectedHostname, expectedHostnameAfterTransliteration,
                sentPackets);
    }

    @Test
    @SignatureRequiredTest(reason = "need to mock setHostnameConfiguration")
    public void testHostname_hostnameSettingUnset_enableHostnameConfig() throws Exception {
        // If hostname setting is unset but legacy hostname overlay config is enabled,
        // we expect that the DHCP packet takes a hostname option.
        doDhcpHostnameSettingTest(IIpClient.HOSTNAME_SETTING_UNSET,
                true /* isHostnameConfigurationEnabled */, true /* expectSendHostname */);
    }

    @Test
    @SignatureRequiredTest(reason = "need to mock setHostnameConfiguration")
    public void testHostname_hostnameSettingUnset_disableHostnameConfig() throws Exception {
        // If hostname setting is unset and legacy hostname overlay config is disabled,
        // we expect that the DHCP packet doesn't take a hostname option.
        doDhcpHostnameSettingTest(IIpClient.HOSTNAME_SETTING_UNSET,
                false /* isHostnameConfigurationEnabled */, false /* expectSendHostname */);
    }

    @Test
    public void testHostname_hostnameSettingSend_enableHostnameConfig() throws Exception {
        // If hostname setting is set and legacy hostname overlay config is enabled,
        // we expect that the DHCP packet takes a hostname option.
        doDhcpHostnameSettingTest(IIpClient.HOSTNAME_SETTING_SEND,
                true /* isHostnameConfigurationEnabled */, true /* expectSendHostname */);
    }

    @Test
    public void testHostname_hostnameSettingSend_disableHostnameConfig() throws Exception {
        // If hostname setting is set and legacy hostname overlay config is disabled,
        // we still expect that the DHCP packet takes a hostname option.
        doDhcpHostnameSettingTest(IIpClient.HOSTNAME_SETTING_SEND,
                false /* isHostnameConfigurationEnabled */, true /* expectSendHostname */);
    }

    @Test
    public void testHostname_hostnameSettingNotSend_enableHostnameConfig() throws Exception {
        // If hostname setting is not send and even if legacy hostname overlay config is
        // enabled, we expect that the DHCP packet doesn't take a hostname option.
        doDhcpHostnameSettingTest(IIpClient.HOSTNAME_SETTING_DO_NOT_SEND,
                true /* isHostnameConfigurationEnabled */, false /* expectSendHostname */);
    }

    @Test
    public void testHostname_hostnameSettingNotSend_disableHostnameConfig() throws Exception {
        // If hostname setting is not send and even if legacy hostname overlay config is
        // disabled, we expect that the DHCP packet doesn't take a hostname option.
        doDhcpHostnameSettingTest(IIpClient.HOSTNAME_SETTING_DO_NOT_SEND,
                false /* isHostnameConfigurationEnabled */, false /* expectSendHostname */);
    }
}
