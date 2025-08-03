/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.INetd.IF_STATE_UP;
import static android.system.OsConstants.ETH_P_IP;
import static android.system.OsConstants.ETH_P_IPV6;

import static com.android.net.module.util.NetworkStackConstants.ETHER_MTU;
import static com.android.server.connectivity.ClatCoordinator.AID_CLAT;
import static com.android.server.connectivity.ClatCoordinator.CLAT_MAX_MTU;
import static com.android.server.connectivity.ClatCoordinator.EGRESS;
import static com.android.server.connectivity.ClatCoordinator.INGRESS;
import static com.android.server.connectivity.ClatCoordinator.INIT_V4ADDR_PREFIX_LEN;
import static com.android.server.connectivity.ClatCoordinator.INIT_V4ADDR_STRING;
import static com.android.server.connectivity.ClatCoordinator.PRIO_CLAT;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.test.filters.SmallTest;

import com.android.internal.util.IndentingPrintWriter;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.bpf.ClatEgress4Key;
import com.android.net.module.util.bpf.ClatEgress4Value;
import com.android.net.module.util.bpf.ClatIngress6Key;
import com.android.net.module.util.bpf.ClatIngress6Value;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.TestBpfMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Objects;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class ClatCoordinatorTest {
    private static final String BASE_IFACE = "test0";
    private static final String STACKED_IFACE = "v4-test0";
    private static final int BASE_IFINDEX = 1000;
    private static final int STACKED_IFINDEX = 1001;

    private static final IpPrefix NAT64_IP_PREFIX = new IpPrefix("64:ff9b::/96");
    private static final String NAT64_PREFIX_STRING = "64:ff9b::";
    private static final Inet6Address INET6_PFX96 = (Inet6Address)
            InetAddresses.parseNumericAddress(NAT64_PREFIX_STRING);
    private static final int GOOGLE_DNS_4 = 0x08080808;  // 8.8.8.8
    private static final int NETID = 42;

    // The test fwmark means: PERMISSION_NETWORK | PERMISSION_SYSTEM (0x3), protectedFromVpn: true,
    // explicitlySelected: true, netid: 42. For bit field structure definition, see union Fwmark in
    // system/netd/include/Fwmark.h
    private static final int MARK = 0xf002a;

    private static final String XLAT_LOCAL_IPV4ADDR_STRING = "192.0.0.46";
    private static final String XLAT_LOCAL_IPV6ADDR_STRING = "2001:db8:0:b11::464";
    private static final Inet4Address INET4_LOCAL4 = (Inet4Address)
            InetAddresses.parseNumericAddress(XLAT_LOCAL_IPV4ADDR_STRING);
    private static final Inet6Address INET6_LOCAL6 = (Inet6Address)
            InetAddresses.parseNumericAddress(XLAT_LOCAL_IPV6ADDR_STRING);
    private static final int CLATD_PID = 10483;

    private static final int TUN_FD = 534;
    private static final int RAW_SOCK_FD = 535;
    private static final int PACKET_SOCK_FD = 536;
    private static final long RAW_SOCK_COOKIE = 27149;
    private static final ParcelFileDescriptor TUN_PFD = spy(new ParcelFileDescriptor(
            new FileDescriptor()));
    private static final ParcelFileDescriptor RAW_SOCK_PFD = spy(new ParcelFileDescriptor(
            new FileDescriptor()));
    private static final ParcelFileDescriptor PACKET_SOCK_PFD = spy(new ParcelFileDescriptor(
            new FileDescriptor()));

    private static final String EGRESS_PROG_PATH =
            "/sys/fs/bpf/net_shared/prog_clatd_schedcls_egress4_clat_rawip";
    private static final String INGRESS_PROG_PATH =
            "/sys/fs/bpf/net_shared/prog_clatd_schedcls_ingress6_clat_ether";
    private static final ClatEgress4Key EGRESS_KEY = new ClatEgress4Key(STACKED_IFINDEX,
            INET4_LOCAL4);
    private static final ClatEgress4Value EGRESS_VALUE = new ClatEgress4Value(BASE_IFINDEX,
            INET6_LOCAL6, INET6_PFX96, (short) 1 /* oifIsEthernet, 1 = true */);
    private static final ClatIngress6Key INGRESS_KEY = new ClatIngress6Key(BASE_IFINDEX,
            INET6_PFX96, INET6_LOCAL6);
    private static final ClatIngress6Value INGRESS_VALUE = new ClatIngress6Value(STACKED_IFINDEX,
            INET4_LOCAL4);
    private static final CookieTagMapKey COOKIE_TAG_KEY = new CookieTagMapKey(RAW_SOCK_COOKIE);
    private static final CookieTagMapValue COOKIE_TAG_VALUE = new CookieTagMapValue(AID_CLAT,
            0 /* tag, unused */);

    private final TestBpfMap<ClatIngress6Key, ClatIngress6Value> mIngressMap =
            spy(new TestBpfMap<>(ClatIngress6Key.class, ClatIngress6Value.class));
    private final TestBpfMap<ClatEgress4Key, ClatEgress4Value> mEgressMap =
            spy(new TestBpfMap<>(ClatEgress4Key.class, ClatEgress4Value.class));
    private final TestBpfMap<CookieTagMapKey, CookieTagMapValue> mCookieTagMap =
            spy(new TestBpfMap<>(CookieTagMapKey.class, CookieTagMapValue.class));

    @Mock private INetd mNetd;
    @Spy private TestDependencies mDeps = new TestDependencies();

    /**
      * The dependency injection class is used to mock the JNI functions and system functions
      * for clatd coordinator control plane. Note that any testing used JNI functions need to
      * be overridden to avoid calling native methods.
      */
    protected class TestDependencies extends ClatCoordinator.Dependencies {
        /**
          * Get netd.
          */
        @Override
        public INetd getNetd() {
            return mNetd;
        }

        /**
         * @see ParcelFileDescriptor#adoptFd(int).
         */
        @Override
        public ParcelFileDescriptor adoptFd(int fd) {
            switch (fd) {
                case TUN_FD:
                    return TUN_PFD;
                case RAW_SOCK_FD:
                    return RAW_SOCK_PFD;
                case PACKET_SOCK_FD:
                    return PACKET_SOCK_PFD;
                default:
                    fail("unsupported arg: " + fd);
                    return null;
            }
        }

        /**
         * Get interface index for a given interface.
         */
        @Override
        public int getInterfaceIndex(String ifName) {
            if (BASE_IFACE.equals(ifName)) {
                return BASE_IFINDEX;
            } else if (STACKED_IFACE.equals(ifName)) {
                return STACKED_IFINDEX;
            }
            fail("unsupported arg: " + ifName);
            return -1;
        }

        /**
         * Create tun interface for a given interface name.
         */
        @Override
        public int createTunInterface(@NonNull String tuniface) throws IOException {
            if (STACKED_IFACE.equals(tuniface)) {
                return TUN_FD;
            }
            fail("unsupported arg: " + tuniface);
            return -1;
        }

        /**
         * Pick an IPv4 address for clat.
         */
        @Override
        public String selectIpv4Address(@NonNull String v4addr, int prefixlen)
                throws IOException {
            if (INIT_V4ADDR_STRING.equals(v4addr) && INIT_V4ADDR_PREFIX_LEN == prefixlen) {
                return XLAT_LOCAL_IPV4ADDR_STRING;
            }
            fail("unsupported args: " + v4addr + ", " + prefixlen);
            return null;
        }

        /**
         * Generate a checksum-neutral IID.
         */
        @Override
        public String generateIpv6Address(@NonNull String iface, @NonNull String v4,
                @NonNull String prefix64, int mark) throws IOException {
            if (BASE_IFACE.equals(iface) && XLAT_LOCAL_IPV4ADDR_STRING.equals(v4)
                    && NAT64_PREFIX_STRING.equals(prefix64) && MARK == mark) {
                return XLAT_LOCAL_IPV6ADDR_STRING;
            }
            fail("unsupported args: " + iface + ", " + v4 + ", " + prefix64 + ", " + mark);
            return null;
        }

        /**
         * Detect MTU.
         */
        @Override
        public int detectMtu(@NonNull String platSubnet, int platSuffix, int mark)
                throws IOException {
            if (NAT64_PREFIX_STRING.equals(platSubnet) && GOOGLE_DNS_4 == platSuffix
                    && MARK == mark) {
                return ETHER_MTU;
            }
            fail("unsupported args: " + platSubnet + ", " + platSuffix + ", " + mark);
            return -1;
        }

        /**
         * Open IPv6 raw socket and set SO_MARK.
         */
        @Override
        public int openRawSocket6(int mark) throws IOException {
            if (mark == MARK) {
                return RAW_SOCK_FD;
            }
            fail("unsupported arg: " + mark);
            return -1;
        }

        /**
         * Open packet socket.
         */
        @Override
        public int openPacketSocket() throws IOException {
            // assume that open socket always successfully because there is no argument to check.
            return PACKET_SOCK_FD;
        }

        /**
         * Add anycast setsockopt.
         */
        @Override
        public void addAnycastSetsockopt(@NonNull FileDescriptor sock, String v6, int ifindex)
                throws IOException {
            if (Objects.equals(RAW_SOCK_PFD.getFileDescriptor(), sock)
                    && XLAT_LOCAL_IPV6ADDR_STRING.equals(v6)
                    && BASE_IFINDEX == ifindex) return;
            fail("unsupported args: " + sock + ", " + v6 + ", " + ifindex);
        }

        /**
         * Configure packet socket.
         */
        @Override
        public void configurePacketSocket(@NonNull FileDescriptor sock, String v6, int ifindex)
                throws IOException {
            if (Objects.equals(PACKET_SOCK_PFD.getFileDescriptor(), sock)
                    && XLAT_LOCAL_IPV6ADDR_STRING.equals(v6)
                    && BASE_IFINDEX == ifindex) return;
            fail("unsupported args: " + sock + ", " + v6 + ", " + ifindex);
        }

        /**
         * Start clatd.
         */
        @Override
        public int startClatd(@NonNull FileDescriptor tunfd, @NonNull FileDescriptor readsock6,
                @NonNull FileDescriptor writesock6, @NonNull String iface, @NonNull String pfx96,
                @NonNull String v4, @NonNull String v6) throws IOException {
            if (Objects.equals(TUN_PFD.getFileDescriptor(), tunfd)
                    && Objects.equals(PACKET_SOCK_PFD.getFileDescriptor(), readsock6)
                    && Objects.equals(RAW_SOCK_PFD.getFileDescriptor(), writesock6)
                    && BASE_IFACE.equals(iface)
                    && NAT64_PREFIX_STRING.equals(pfx96)
                    && XLAT_LOCAL_IPV4ADDR_STRING.equals(v4)
                    && XLAT_LOCAL_IPV6ADDR_STRING.equals(v6)) {
                return CLATD_PID;
            }
            fail("unsupported args: " + tunfd + ", " + readsock6 + ", " + writesock6 + ", "
                    + ", " + iface + ", " + v4 + ", " + v6);
            return -1;
        }

        /**
         * Stop clatd.
         */
        @Override
        public void stopClatd(int pid) throws IOException {
            if (pid == -1) {
                fail("unsupported arg: " + pid);
            }
        }

        /**
         * Get socket cookie.
         */
        public long getSocketCookie(@NonNull FileDescriptor sock) throws IOException {
            return RAW_SOCK_COOKIE;
        }

        /** Get ingress6 BPF map. */
        @Override
        public IBpfMap<ClatIngress6Key, ClatIngress6Value> getBpfIngress6Map() {
            return mIngressMap;
        }

        /** Get egress4 BPF map. */
        @Override
        public IBpfMap<ClatEgress4Key, ClatEgress4Value> getBpfEgress4Map() {
            return mEgressMap;
        }

        /** Get cookie tag map */
        @Override
        public IBpfMap<CookieTagMapKey, CookieTagMapValue> getBpfCookieTagMap() {
            return mCookieTagMap;
        }

        /** Checks if the network interface uses an ethernet L2 header. */
        public boolean isEthernet(String iface) throws IOException {
            if (BASE_IFACE.equals(iface)) return true;

            fail("unsupported arg: " + iface);
            return false;
        }

        /** Add a clsact qdisc. */
        @Override
        public void tcQdiscAddDevClsact(int ifIndex) throws IOException {
            // no-op
            return;
        }

        /** Attach a tc bpf filter. */
        @Override
        public void tcFilterAddDevBpf(int ifIndex, boolean ingress, short prio, short proto,
                String bpfProgPath) throws IOException {
            // no-op
            return;
        }

        /** Delete a tc filter. */
        @Override
        public void tcFilterDelDev(int ifIndex, boolean ingress, short prio, short proto)
                throws IOException {
            // no-op
            return;
        }
    };

    @NonNull
    private ClatCoordinator makeClatCoordinator() throws Exception {
        final ClatCoordinator coordinator = new ClatCoordinator(mDeps);
        return coordinator;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private boolean assertContainsFlag(String[] flags, String match) {
        for (String flag : flags) {
            if (flag.equals(match)) return true;
        }
        fail("Missing flag: " + match);
        return false;
    }

    @Test
    public void testStartStopClatd() throws Exception {
        final ClatCoordinator coordinator = makeClatCoordinator();
        final InOrder inOrder = inOrder(mNetd, mDeps, mIngressMap, mEgressMap, mCookieTagMap);
        clearInvocations(mNetd, mDeps, mIngressMap, mEgressMap, mCookieTagMap);

        // [1] Start clatd.
        final String addr6For464xlat = coordinator.clatStart(BASE_IFACE, NETID, NAT64_IP_PREFIX);
        assertEquals(XLAT_LOCAL_IPV6ADDR_STRING, addr6For464xlat);
        final ClatCoordinator.ClatdTracker expected = new ClatCoordinator.ClatdTracker(
                BASE_IFACE, BASE_IFINDEX, STACKED_IFACE, STACKED_IFINDEX,
                INET4_LOCAL4, INET6_LOCAL6, INET6_PFX96, CLATD_PID, RAW_SOCK_COOKIE);
        final ClatCoordinator.ClatdTracker actual = coordinator.getClatdTrackerForTesting();
        assertEquals(expected, actual);

        // Pick an IPv4 address.
        inOrder.verify(mDeps).selectIpv4Address(eq(INIT_V4ADDR_STRING),
                eq(INIT_V4ADDR_PREFIX_LEN));

        // Generate a checksum-neutral IID.
        inOrder.verify(mDeps).generateIpv6Address(eq(BASE_IFACE),
                eq(XLAT_LOCAL_IPV4ADDR_STRING), eq(NAT64_PREFIX_STRING), eq(MARK));

        // Open, configure and bring up the tun interface.
        inOrder.verify(mDeps).createTunInterface(eq(STACKED_IFACE));
        inOrder.verify(mDeps).adoptFd(eq(TUN_FD));
        inOrder.verify(mDeps).getInterfaceIndex(eq(STACKED_IFACE));
        inOrder.verify(mNetd).interfaceSetEnableIPv6(eq(STACKED_IFACE), eq(false /* enable */));
        inOrder.verify(mDeps).detectMtu(eq(NAT64_PREFIX_STRING), eq(GOOGLE_DNS_4), eq(MARK));
        inOrder.verify(mNetd).interfaceSetMtu(eq(STACKED_IFACE),
                eq(1472 /* ETHER_MTU(1500) - MTU_DELTA(28) */));
        inOrder.verify(mNetd).interfaceSetCfg(argThat(cfg ->
                STACKED_IFACE.equals(cfg.ifName)
                && XLAT_LOCAL_IPV4ADDR_STRING.equals(cfg.ipv4Addr)
                && (32 == cfg.prefixLength)
                && "".equals(cfg.hwAddr)
                && assertContainsFlag(cfg.flags, IF_STATE_UP)));

        // Open and configure 464xlat read/write sockets.
        inOrder.verify(mDeps).openPacketSocket();
        inOrder.verify(mDeps).adoptFd(eq(PACKET_SOCK_FD));
        inOrder.verify(mDeps).openRawSocket6(eq(MARK));
        inOrder.verify(mDeps).adoptFd(eq(RAW_SOCK_FD));
        inOrder.verify(mDeps).getInterfaceIndex(eq(BASE_IFACE));
        inOrder.verify(mDeps).addAnycastSetsockopt(
                argThat(fd -> Objects.equals(RAW_SOCK_PFD.getFileDescriptor(), fd)),
                eq(XLAT_LOCAL_IPV6ADDR_STRING), eq(BASE_IFINDEX));
        inOrder.verify(mDeps).getSocketCookie(
                argThat(fd -> Objects.equals(RAW_SOCK_PFD.getFileDescriptor(), fd)));
        inOrder.verify(mCookieTagMap).insertEntry(eq(COOKIE_TAG_KEY), eq(COOKIE_TAG_VALUE));
        inOrder.verify(mDeps).configurePacketSocket(
                argThat(fd -> Objects.equals(PACKET_SOCK_PFD.getFileDescriptor(), fd)),
                eq(XLAT_LOCAL_IPV6ADDR_STRING), eq(BASE_IFINDEX));

        // Start clatd.
        inOrder.verify(mDeps).startClatd(
                argThat(fd -> Objects.equals(TUN_PFD.getFileDescriptor(), fd)),
                argThat(fd -> Objects.equals(PACKET_SOCK_PFD.getFileDescriptor(), fd)),
                argThat(fd -> Objects.equals(RAW_SOCK_PFD.getFileDescriptor(), fd)),
                eq(BASE_IFACE), eq(NAT64_PREFIX_STRING),
                eq(XLAT_LOCAL_IPV4ADDR_STRING), eq(XLAT_LOCAL_IPV6ADDR_STRING));
        inOrder.verify(mEgressMap).insertEntry(eq(EGRESS_KEY), eq(EGRESS_VALUE));
        inOrder.verify(mIngressMap).insertEntry(eq(INGRESS_KEY), eq(INGRESS_VALUE));
        inOrder.verify(mDeps).tcQdiscAddDevClsact(eq(STACKED_IFINDEX));
        inOrder.verify(mDeps).tcFilterAddDevBpf(eq(STACKED_IFINDEX), eq(EGRESS),
                eq((short) PRIO_CLAT), eq((short) ETH_P_IP), eq(EGRESS_PROG_PATH));
        inOrder.verify(mDeps).tcFilterAddDevBpf(eq(BASE_IFINDEX), eq(INGRESS),
                eq((short) PRIO_CLAT), eq((short) ETH_P_IPV6), eq(INGRESS_PROG_PATH));
        inOrder.verifyNoMoreInteractions();

        // [2] Start clatd again failed.
        assertThrows("java.io.IOException: Clatd is already running on test0 (pid 10483)",
                IOException.class,
                () -> coordinator.clatStart(BASE_IFACE, NETID, NAT64_IP_PREFIX));

        // [3] Expect clatd to stop successfully.
        coordinator.clatStop();
        inOrder.verify(mDeps).tcFilterDelDev(eq(BASE_IFINDEX), eq(INGRESS),
                eq((short) PRIO_CLAT), eq((short) ETH_P_IPV6));
        inOrder.verify(mDeps).tcFilterDelDev(eq(STACKED_IFINDEX), eq(EGRESS),
                eq((short) PRIO_CLAT), eq((short) ETH_P_IP));
        inOrder.verify(mEgressMap).deleteEntry(eq(EGRESS_KEY));
        inOrder.verify(mIngressMap).deleteEntry(eq(INGRESS_KEY));
        inOrder.verify(mDeps).stopClatd(eq(CLATD_PID));
        inOrder.verify(mCookieTagMap).deleteEntry(eq(COOKIE_TAG_KEY));
        assertNull(coordinator.getClatdTrackerForTesting());
        inOrder.verifyNoMoreInteractions();

        // [4] Expect an IO exception while stopping a clatd that doesn't exist.
        assertThrows("java.io.IOException: Clatd has not started", IOException.class,
                () -> coordinator.clatStop());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetFwmark() throws Exception {
        assertEquals(0xf0064, ClatCoordinator.getFwmark(100));
        assertEquals(0xf03e8, ClatCoordinator.getFwmark(1000));
        assertEquals(0xf2710, ClatCoordinator.getFwmark(10000));
        assertEquals(0xfffff, ClatCoordinator.getFwmark(65535));
    }

    @Test
    public void testAdjustMtu() throws Exception {
        // Expected mtu is that IPV6_MIN_MTU(1280) minus MTU_DELTA(28).
        assertEquals(1252, ClatCoordinator.adjustMtu(-1 /* detect mtu failed */));
        assertEquals(1252, ClatCoordinator.adjustMtu(500));
        assertEquals(1252, ClatCoordinator.adjustMtu(1000));
        assertEquals(1252, ClatCoordinator.adjustMtu(1280));

        // Expected mtu is that the detected mtu minus MTU_DELTA(28).
        assertEquals(1372, ClatCoordinator.adjustMtu(1400));
        assertEquals(1472, ClatCoordinator.adjustMtu(ETHER_MTU));
        assertEquals(1500, ClatCoordinator.adjustMtu(CLAT_MAX_MTU));

        // Expected mtu is that CLAT_MAX_MTU(1528) minus MTU_DELTA(28).
        assertEquals(1500, ClatCoordinator.adjustMtu(CLAT_MAX_MTU + 1 /* over maximum mtu */));
    }

    private void verifyDump(final ClatCoordinator coordinator, boolean clatStarted) {
        final StringWriter stringWriter = new StringWriter();
        final IndentingPrintWriter ipw = new IndentingPrintWriter(stringWriter, " ");
        coordinator.dump(ipw);

        final String[] dumpStrings = stringWriter.toString().split("\n");
        if (clatStarted) {
            assertEquals(6, dumpStrings.length);
            assertEquals("CLAT tracker: iface: test0 (1000), v4iface: v4-test0 (1001), "
                    + "v4: /192.0.0.46, v6: /2001:db8:0:b11::464, pfx96: /64:ff9b::, "
                    + "pid: 10483, cookie: 27149", dumpStrings[0].trim());
            assertEquals("Forwarding rules:", dumpStrings[1].trim());
            assertEquals("BPF ingress map: iif nat64Prefix v6Addr -> v4Addr oif",
                    dumpStrings[2].trim());
            assertEquals("1000 /64:ff9b::/96 /2001:db8:0:b11::464 -> /192.0.0.46 1001",
                    dumpStrings[3].trim());
            assertEquals("BPF egress map: iif v4Addr -> v6Addr nat64Prefix oif",
                    dumpStrings[4].trim());
            assertEquals("1001 /192.0.0.46 -> /2001:db8:0:b11::464 /64:ff9b::/96 1000 ether",
                    dumpStrings[5].trim());
        } else {
            assertEquals(1, dumpStrings.length);
            assertEquals("<not started>", dumpStrings[0].trim());
        }
    }

    @Test
    public void testDump() throws Exception {
        final ClatCoordinator coordinator = makeClatCoordinator();
        verifyDump(coordinator, false /* clatStarted */);
        coordinator.clatStart(BASE_IFACE, NETID, NAT64_IP_PREFIX);
        verifyDump(coordinator, true /* clatStarted */);
    }

    @Test
    public void testNotStartClatWithInvalidPrefix() throws Exception {
        final ClatCoordinator coordinator = makeClatCoordinator();
        final IpPrefix invalidPrefix = new IpPrefix("2001:db8::/64");
        assertThrows(IOException.class,
                () -> coordinator.clatStart(BASE_IFACE, NETID, invalidPrefix));
    }

    private void checkNotStartClat(final TestDependencies deps, final boolean needToCloseTunFd,
            final boolean needToClosePacketSockFd, final boolean needToCloseRawSockFd)
            throws Exception {
        clearInvocations(TUN_PFD, RAW_SOCK_PFD, PACKET_SOCK_PFD);

        // [1] Expect that modified TestDependencies can't start clatd.
        // Expect that the injection function of TestDependencies causes clatStart() failed.
        final ClatCoordinator coordinatorWithBrokenDeps = new ClatCoordinator(deps);
        assertThrows(IOException.class,
                () -> coordinatorWithBrokenDeps.clatStart(BASE_IFACE, NETID, NAT64_IP_PREFIX));

        // Use precise check to make sure that there is no unexpected file descriptor closing.
        if (needToCloseTunFd) {
            verify(TUN_PFD).close();
        } else {
            verify(TUN_PFD, never()).close();
        }
        if (needToClosePacketSockFd) {
            verify(PACKET_SOCK_PFD).close();
        } else {
            verify(PACKET_SOCK_PFD, never()).close();
        }
        if (needToCloseRawSockFd) {
            verify(RAW_SOCK_PFD).close();
        } else {
            verify(RAW_SOCK_PFD, never()).close();
        }

        // Check that dump doesn't crash after any clat starting failure.
        verifyDump(coordinatorWithBrokenDeps, false /* clatStarted */);

        // [2] Expect that unmodified TestDependencies can start clatd.
        // Used to make sure that the above modified TestDependencies has really broken the
        // clatd starting.
        final ClatCoordinator coordinatorWithDefaultDeps = new ClatCoordinator(
                new TestDependencies());
        assertNotNull(coordinatorWithDefaultDeps.clatStart(BASE_IFACE, NETID, NAT64_IP_PREFIX));
    }

    // The following testNotStartClat* tests verifies bunches of code for unwinding the
    // failure if any.
    @Test
    public void testNotStartClatWithNativeFailureSelectIpv4Address() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public String selectIpv4Address(@NonNull String v4addr, int prefixlen)
                    throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), false /* needToCloseTunFd */,
                false /* needToClosePacketSockFd */, false /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureGenerateIpv6Address() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public String generateIpv6Address(@NonNull String iface, @NonNull String v4,
                    @NonNull String prefix64, int mark) throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), false /* needToCloseTunFd */,
                false /* needToClosePacketSockFd */, false /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureCreateTunInterface() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public int createTunInterface(@NonNull String tuniface) throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), false /* needToCloseTunFd */,
                false /* needToClosePacketSockFd */, false /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureDetectMtu() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public int detectMtu(@NonNull String platSubnet, int platSuffix, int mark)
                    throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                false /* needToClosePacketSockFd */, false /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureOpenPacketSocket() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public int openPacketSocket() throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                false /* needToClosePacketSockFd */, false /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureOpenRawSocket6() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public int openRawSocket6(int mark) throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                true /* needToClosePacketSockFd */, false /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureAddAnycastSetsockopt() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public void addAnycastSetsockopt(@NonNull FileDescriptor sock, String v6,
                    int ifindex) throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                true /* needToClosePacketSockFd */, true /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureConfigurePacketSocket() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public void configurePacketSocket(@NonNull FileDescriptor sock, String v6,
                    int ifindex) throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                true /* needToClosePacketSockFd */, true /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureStartClatd() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public int startClatd(@NonNull FileDescriptor tunfd, @NonNull FileDescriptor readsock6,
                    @NonNull FileDescriptor writesock6, @NonNull String iface,
                    @NonNull String pfx96, @NonNull String v4, @NonNull String v6)
                    throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                true /* needToClosePacketSockFd */, true /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNativeFailureGetSocketCookie() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public long getSocketCookie(@NonNull FileDescriptor sock) throws IOException {
                throw new IOException();
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                true /* needToClosePacketSockFd */, true /* needToCloseRawSockFd */);
    }

    @Test
    public void testNotStartClatWithNullCookieTagMap() throws Exception {
        class FailureDependencies extends TestDependencies {
            @Override
            public IBpfMap<CookieTagMapKey, CookieTagMapValue> getBpfCookieTagMap() {
                return null;
            }
        }
        checkNotStartClat(new FailureDependencies(), true /* needToCloseTunFd */,
                true /* needToClosePacketSockFd */, true /* needToCloseRawSockFd */);
    }
}
