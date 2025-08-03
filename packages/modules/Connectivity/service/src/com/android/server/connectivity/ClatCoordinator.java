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
import static android.net.INetd.PERMISSION_NETWORK;
import static android.net.INetd.PERMISSION_SYSTEM;
import static android.system.OsConstants.ETH_P_IP;
import static android.system.OsConstants.ETH_P_IPV6;

import static com.android.net.module.util.NetworkStackConstants.IPV6_MIN_MTU;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.InterfaceConfigurationParcel;
import android.net.IpPrefix;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.ErrnoException;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.TcUtils;
import com.android.net.module.util.bpf.ClatEgress4Key;
import com.android.net.module.util.bpf.ClatEgress4Value;
import com.android.net.module.util.bpf.ClatIngress6Key;
import com.android.net.module.util.bpf.ClatIngress6Value;
import com.android.net.module.util.bpf.CookieTagMapKey;
import com.android.net.module.util.bpf.CookieTagMapValue;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This coordinator is responsible for providing clat relevant functionality.
 *
 * {@hide}
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class ClatCoordinator {
    private static final String TAG = ClatCoordinator.class.getSimpleName();

    // Sync from system/core/libcutils/include/private/android_filesystem_config.h
    @VisibleForTesting
    static final int AID_CLAT = 1029;

    // Sync from external/android-clat/clatd.c
    // 40 bytes IPv6 header - 20 bytes IPv4 header + 8 bytes fragment header.
    @VisibleForTesting
    static final int MTU_DELTA = 28;
    @VisibleForTesting
    static final int CLAT_MAX_MTU = 1500 + MTU_DELTA;

    // This must match the interface prefix in clatd.c.
    private static final String CLAT_PREFIX = "v4-";

    // For historical reasons, start with 192.0.0.4, and after that, use all subsequent addresses
    // in 192.0.0.0/29 (RFC 7335).
    @VisibleForTesting
    static final String INIT_V4ADDR_STRING = "192.0.0.4";
    @VisibleForTesting
    static final int INIT_V4ADDR_PREFIX_LEN = 29;
    private static final InetAddress GOOGLE_DNS_4 = InetAddress.parseNumericAddress("8.8.8.8");

    private static final int INVALID_IFINDEX = 0;

    // For better code clarity when used for 'bool ingress' parameter.
    @VisibleForTesting
    static final boolean EGRESS = false;
    @VisibleForTesting
    static final boolean INGRESS = true;

    // For better code clarity when used for 'bool ether' parameter.
    static final boolean RAWIP = false;
    static final boolean ETHER = true;

    // The priority of clat hook - must be after tethering.
    @VisibleForTesting
    static final int PRIO_CLAT = 4;

    private static final String COOKIE_TAG_MAP_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_cookie_tag_map";
    private static final String CLAT_EGRESS4_MAP_PATH = makeMapPath("egress4");
    private static final String CLAT_INGRESS6_MAP_PATH = makeMapPath("ingress6");

    private static String makeMapPath(String which) {
        return "/sys/fs/bpf/net_shared/map_clatd_clat_" + which + "_map";
    }

    private static final String CLAT_EGRESS4_RAWIP_PROG_PATH =
            "/sys/fs/bpf/net_shared/prog_clatd_schedcls_egress4_clat_rawip";

    private static String makeIngressProgPath(boolean ether) {
        return "/sys/fs/bpf/net_shared/prog_clatd_schedcls_ingress6_clat_"
                + (ether ? "ether" : "rawip");
    }

    @NonNull
    private final INetd mNetd;
    @NonNull
    private final Dependencies mDeps;
    @Nullable
    private final IBpfMap<ClatIngress6Key, ClatIngress6Value> mIngressMap;
    @Nullable
    private final IBpfMap<ClatEgress4Key, ClatEgress4Value> mEgressMap;
    @Nullable
    private final IBpfMap<CookieTagMapKey, CookieTagMapValue> mCookieTagMap;
    @Nullable
    private ClatdTracker mClatdTracker = null;

    /**
     * Dependencies of ClatCoordinator which makes ConnectivityService injection
     * in tests.
     */
    @VisibleForTesting
    public abstract static class Dependencies {
        /**
          * Get netd.
          */
        @NonNull
        public abstract INetd getNetd();

        /**
         * @see ParcelFileDescriptor#adoptFd(int).
         */
        @NonNull
        public ParcelFileDescriptor adoptFd(int fd) {
            return ParcelFileDescriptor.adoptFd(fd);
        }

        /**
         * Get interface index for a given interface.
         */
        public int getInterfaceIndex(String ifName) {
            final InterfaceParams params = InterfaceParams.getByName(ifName);
            return params != null ? params.index : INVALID_IFINDEX;
        }

        /**
         * Create tun interface for a given interface name.
         */
        public int createTunInterface(@NonNull String tuniface) throws IOException {
            return native_createTunInterface(tuniface);
        }

        /**
         * Pick an IPv4 address for clat.
         */
        @NonNull
        public String selectIpv4Address(@NonNull String v4addr, int prefixlen)
                throws IOException {
            return native_selectIpv4Address(v4addr, prefixlen);
        }

        /**
         * Generate a checksum-neutral IID.
         */
        @NonNull
        public String generateIpv6Address(@NonNull String iface, @NonNull String v4,
                @NonNull String prefix64, int mark) throws IOException {
            return native_generateIpv6Address(iface, v4, prefix64, mark);
        }

        /**
         * Detect MTU.
         */
        public int detectMtu(@NonNull String platSubnet, int platSuffix, int mark)
                throws IOException {
            return native_detectMtu(platSubnet, platSuffix, mark);
        }

        /**
         * Open packet socket.
         */
        public int openPacketSocket() throws IOException {
            return native_openPacketSocket();
        }

        /**
         * Open IPv6 raw socket and set SO_MARK.
         */
        public int openRawSocket6(int mark) throws IOException {
            return native_openRawSocket6(mark);
        }

        /**
         * Add anycast setsockopt.
         */
        public void addAnycastSetsockopt(@NonNull FileDescriptor sock, String v6, int ifindex)
                throws IOException {
            native_addAnycastSetsockopt(sock, v6, ifindex);
        }

        /**
         * Configure packet socket.
         */
        public void configurePacketSocket(@NonNull FileDescriptor sock, String v6, int ifindex)
                throws IOException {
            native_configurePacketSocket(sock, v6, ifindex);
        }

        /**
         * Start clatd.
         */
        public int startClatd(@NonNull FileDescriptor tunfd, @NonNull FileDescriptor readsock6,
                @NonNull FileDescriptor writesock6, @NonNull String iface, @NonNull String pfx96,
                @NonNull String v4, @NonNull String v6) throws IOException {
            return native_startClatd(tunfd, readsock6, writesock6, iface, pfx96, v4, v6);
        }

        /**
         * Stop clatd.
         */
        public void stopClatd(int pid) throws IOException {
            native_stopClatd(pid);
        }

        /**
         * Get socket cookie.
         */
        public long getSocketCookie(@NonNull FileDescriptor sock) throws IOException {
            return native_getSocketCookie(sock);
        }

        /** Get ingress6 BPF map. */
        @Nullable
        public IBpfMap<ClatIngress6Key, ClatIngress6Value> getBpfIngress6Map() {
            try {
                return new BpfMap<>(CLAT_INGRESS6_MAP_PATH,
                       ClatIngress6Key.class, ClatIngress6Value.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create ingress6 map: " + e);
                return null;
            }
        }

        /** Get egress4 BPF map. */
        @Nullable
        public IBpfMap<ClatEgress4Key, ClatEgress4Value> getBpfEgress4Map() {
            try {
                return new BpfMap<>(CLAT_EGRESS4_MAP_PATH,
                       ClatEgress4Key.class, ClatEgress4Value.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create egress4 map: " + e);
                return null;
            }
        }

        /** Get cookie tag map */
        @Nullable
        public IBpfMap<CookieTagMapKey, CookieTagMapValue> getBpfCookieTagMap() {
            try {
                return new BpfMap<>(COOKIE_TAG_MAP_PATH,
                       CookieTagMapKey.class, CookieTagMapValue.class);
            } catch (ErrnoException e) {
                Log.wtf(TAG, "Cannot open cookie tag map: " + e);
                return null;
            }
        }

        /** Checks if the network interface uses an ethernet L2 header. */
        public boolean isEthernet(String iface) throws IOException {
            return TcUtils.isEthernet(iface);
        }

        /** Add a clsact qdisc. */
        public void tcQdiscAddDevClsact(int ifIndex) throws IOException {
            TcUtils.tcQdiscAddDevClsact(ifIndex);
        }

        /** Attach a tc bpf filter. */
        public void tcFilterAddDevBpf(int ifIndex, boolean ingress, short prio, short proto,
                String bpfProgPath) throws IOException {
            TcUtils.tcFilterAddDevBpf(ifIndex, ingress, prio, proto, bpfProgPath);
        }

        /** Delete a tc filter. */
        public void tcFilterDelDev(int ifIndex, boolean ingress, short prio, short proto)
                throws IOException {
            TcUtils.tcFilterDelDev(ifIndex, ingress, prio, proto);
        }
    }

    @VisibleForTesting
    static class ClatdTracker {
        @NonNull
        public final String iface;
        public final int ifIndex;
        @NonNull
        public final String v4iface;
        public final int v4ifIndex;
        @NonNull
        public final Inet4Address v4;
        @NonNull
        public final Inet6Address v6;
        @NonNull
        public final Inet6Address pfx96;
        public final int pid;
        public final long cookie;

        ClatdTracker(@NonNull String iface, int ifIndex, @NonNull String v4iface,
                int v4ifIndex, @NonNull Inet4Address v4, @NonNull Inet6Address v6,
                @NonNull Inet6Address pfx96, int pid, long cookie) {
            this.iface = iface;
            this.ifIndex = ifIndex;
            this.v4iface = v4iface;
            this.v4ifIndex = v4ifIndex;
            this.v4 = v4;
            this.v6 = v6;
            this.pfx96 = pfx96;
            this.pid = pid;
            this.cookie = cookie;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ClatdTracker)) return false;
            ClatdTracker that = (ClatdTracker) o;
            return Objects.equals(this.iface, that.iface)
                    && this.ifIndex == that.ifIndex
                    && Objects.equals(this.v4iface, that.v4iface)
                    && this.v4ifIndex == that.v4ifIndex
                    && Objects.equals(this.v4, that.v4)
                    && Objects.equals(this.v6, that.v6)
                    && Objects.equals(this.pfx96, that.pfx96)
                    && this.pid == that.pid
                    && this.cookie == that.cookie;
        }

        @Override
        public String toString() {
            return "iface: " + iface
                    + " (" + ifIndex + ")"
                    + ", v4iface: " + v4iface
                    + " (" + v4ifIndex + ")"
                    + ", v4: " + v4
                    + ", v6: " + v6
                    + ", pfx96: " + pfx96
                    + ", pid: " + pid
                    + ", cookie: " + cookie;
        }
    };

    @VisibleForTesting
    static int getFwmark(int netId) {
        // See union Fwmark in system/netd/include/Fwmark.h
        return (netId & 0xffff)
                | 0x1 << 16  // explicitlySelected: true
                | 0x1 << 17  // protectedFromVpn: true
                | ((PERMISSION_NETWORK | PERMISSION_SYSTEM) & 0x3) << 18;  // 2 permission bits = 3
    }

    @VisibleForTesting
    static int adjustMtu(int mtu) {
        // clamp to minimum ipv6 mtu - this probably cannot ever trigger
        if (mtu < IPV6_MIN_MTU) mtu = IPV6_MIN_MTU;
        // clamp to buffer size
        if (mtu > CLAT_MAX_MTU) mtu = CLAT_MAX_MTU;
        // decrease by ipv6(40) + ipv6 fragmentation header(8) vs ipv4(20) overhead of 28 bytes
        mtu -= MTU_DELTA;

        return mtu;
    }

    public ClatCoordinator(@NonNull Dependencies deps) {
        mDeps = deps;
        mNetd = mDeps.getNetd();
        mIngressMap = mDeps.getBpfIngress6Map();
        mEgressMap = mDeps.getBpfEgress4Map();
        mCookieTagMap = mDeps.getBpfCookieTagMap();
    }

    private void maybeStartBpf(final ClatdTracker tracker) {
        if (mIngressMap == null || mEgressMap == null) return;

        final boolean isEthernet;
        try {
            isEthernet = mDeps.isEthernet(tracker.iface);
        } catch (IOException e) {
            Log.e(TAG, "Fail to call isEthernet for interface " + tracker.iface);
            return;
        }

        final ClatEgress4Key txKey = new ClatEgress4Key(tracker.v4ifIndex, tracker.v4);
        final ClatEgress4Value txValue = new ClatEgress4Value(tracker.ifIndex, tracker.v6,
                tracker.pfx96, (short) (isEthernet ? 1 /* ETHER */ : 0 /* RAWIP */));
        try {
            mEgressMap.insertEntry(txKey, txValue);
        } catch (ErrnoException | IllegalStateException e) {
            Log.e(TAG, "Could not insert entry (" + txKey + ", " + txValue + ") on egress map: "
                    + e);
            return;
        }

        final ClatIngress6Key rxKey = new ClatIngress6Key(tracker.ifIndex, tracker.pfx96,
                tracker.v6);
        final ClatIngress6Value rxValue = new ClatIngress6Value(tracker.v4ifIndex,
                tracker.v4);
        try {
            mIngressMap.insertEntry(rxKey, rxValue);
        } catch (ErrnoException | IllegalStateException e) {
            Log.e(TAG, "Could not insert entry (" + rxKey + ", " + rxValue + ") ingress map: "
                    + e);
            try {
                mEgressMap.deleteEntry(txKey);
            } catch (ErrnoException | IllegalStateException e2) {
                Log.e(TAG, "Could not delete entry (" + txKey + ") from egress map: " + e2);
            }
            return;
        }

        // Usually the clsact will be added in netd RouteController::addInterfaceToPhysicalNetwork.
        // But clat is started before the v4- interface is added to the network. The clat startup
        // have to add clsact of v4- tun interface first for adding bpf filter in maybeStartBpf.
        try {
            // tc qdisc add dev .. clsact
            mDeps.tcQdiscAddDevClsact(tracker.v4ifIndex);
        } catch (IOException e) {
            Log.e(TAG, "tc qdisc add dev (" + tracker.v4ifIndex + "[" + tracker.v4iface
                    + "]) failure: " + e);
            try {
                mEgressMap.deleteEntry(txKey);
            } catch (ErrnoException | IllegalStateException e2) {
                Log.e(TAG, "Could not delete entry (" + txKey + ") from egress map: " + e2);
            }
            try {
                mIngressMap.deleteEntry(rxKey);
            } catch (ErrnoException | IllegalStateException e3) {
                Log.e(TAG, "Could not delete entry (" + rxKey + ") from ingress map: " + e3);
            }
            return;
        }

        // This program will be attached to the v4-* interface which is a TUN and thus always rawip.
        try {
            // tc filter add dev .. egress prio 4 protocol ip bpf object-pinned /sys/fs/bpf/...
            // direct-action
            mDeps.tcFilterAddDevBpf(tracker.v4ifIndex, EGRESS, (short) PRIO_CLAT, (short) ETH_P_IP,
                    CLAT_EGRESS4_RAWIP_PROG_PATH);
        } catch (IOException e) {
            Log.e(TAG, "tc filter add dev (" + tracker.v4ifIndex + "[" + tracker.v4iface
                    + "]) egress prio PRIO_CLAT protocol ip failure: " + e);

            // The v4- interface clsact is not deleted for unwinding error because once it is
            // created with interface addition, the lifetime is till interface deletion. Moreover,
            // the clsact has no clat filter now. It should not break anything.

            try {
                mEgressMap.deleteEntry(txKey);
            } catch (ErrnoException | IllegalStateException e2) {
                Log.e(TAG, "Could not delete entry (" + txKey + ") from egress map: " + e2);
            }
            try {
                mIngressMap.deleteEntry(rxKey);
            } catch (ErrnoException | IllegalStateException e3) {
                Log.e(TAG, "Could not delete entry (" + rxKey + ") from ingress map: " + e3);
            }
            return;
        }

        try {
            // tc filter add dev .. ingress prio 4 protocol ipv6 bpf object-pinned /sys/fs/bpf/...
            // direct-action
            mDeps.tcFilterAddDevBpf(tracker.ifIndex, INGRESS, (short) PRIO_CLAT,
                    (short) ETH_P_IPV6, makeIngressProgPath(isEthernet));
        } catch (IOException e) {
            Log.e(TAG, "tc filter add dev (" + tracker.ifIndex + "[" + tracker.iface
                    + "]) ingress prio PRIO_CLAT protocol ipv6 failure: " + e);

            // The v4- interface clsact is not deleted. See the reason in the error unwinding code
            // of the egress filter attaching of v4- tun interface.

            try {
                mDeps.tcFilterDelDev(tracker.v4ifIndex, EGRESS, (short) PRIO_CLAT,
                        (short) ETH_P_IP);
            } catch (IOException e2) {
                Log.e(TAG, "tc filter del dev (" + tracker.v4ifIndex + "[" + tracker.v4iface
                        + "]) egress prio PRIO_CLAT protocol ip failure: " + e2);
            }
            try {
                mEgressMap.deleteEntry(txKey);
            } catch (ErrnoException | IllegalStateException e3) {
                Log.e(TAG, "Could not delete entry (" + txKey + ") from egress map: " + e3);
            }
            try {
                mIngressMap.deleteEntry(rxKey);
            } catch (ErrnoException | IllegalStateException e4) {
                Log.e(TAG, "Could not delete entry (" + rxKey + ") from ingress map: " + e4);
            }
            return;
        }
    }

    private void maybeCleanUp(ParcelFileDescriptor tunFd, ParcelFileDescriptor readSock6,
            ParcelFileDescriptor writeSock6) {
        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close tun file descriptor " + e);
            }
        }
        if (readSock6 != null) {
            try {
                readSock6.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close read socket " + e);
            }
        }
        if (writeSock6 != null) {
            try {
                writeSock6.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close write socket " + e);
            }
        }
    }

    private void tagSocketAsClat(long cookie) throws IOException {
        if (mCookieTagMap == null) {
            throw new IOException("Cookie tag map is not initialized");
        }

        // Tag raw socket with uid AID_CLAT and set tag as zero because tag is unused in bpf
        // program for counting data usage in netd.c. Tagging socket is used to avoid counting
        // duplicated clat traffic in bpf stat.
        final CookieTagMapKey key = new CookieTagMapKey(cookie);
        final CookieTagMapValue value = new CookieTagMapValue(AID_CLAT, 0 /* tag, unused */);
        try {
            mCookieTagMap.insertEntry(key, value);
        } catch (ErrnoException | IllegalStateException e) {
            throw new IOException("Could not insert entry (" + key + ", " + value
                    + ") on cookie tag map: " + e);
        }
        Log.i(TAG, "tag socket cookie " + cookie);
    }

    private void untagSocket(long cookie) throws IOException {
        if (mCookieTagMap == null) {
            throw new IOException("Cookie tag map is not initialized");
        }

        // The reason that deleting entry from cookie tag map directly is that the tag socket
        // destroy listener only monitors on group INET_TCP, INET_UDP, INET6_TCP, INET6_UDP.
        // The other socket types, ex: raw, are not able to be removed automatically by the
        // listener. See TrafficController::makeSkDestroyListener.
        final CookieTagMapKey key = new CookieTagMapKey(cookie);
        try {
            mCookieTagMap.deleteEntry(key);
        } catch (ErrnoException | IllegalStateException e) {
            throw new IOException("Could not delete entry (" + key + ") on cookie tag map: " + e);
        }
        Log.i(TAG, "untag socket cookie " + cookie);
    }

    private boolean isStarted() {
        return mClatdTracker != null;
    }

    /**
     * Start clatd for a given interface and NAT64 prefix.
     */
    public String clatStart(final String iface, final int netId,
            @NonNull final IpPrefix nat64Prefix)
            throws IOException {
        if (isStarted()) {
            throw new IOException("Clatd is already running on " + mClatdTracker.iface
                    + " (pid " + mClatdTracker.pid + ")");
        }
        if (nat64Prefix.getPrefixLength() != 96) {
            throw new IOException("Prefix must be 96 bits long: " + nat64Prefix);
        }

        // [1] Pick an IPv4 address from 192.0.0.4, 192.0.0.5, 192.0.0.6 ..
        final String v4Str;
        try {
            v4Str = mDeps.selectIpv4Address(INIT_V4ADDR_STRING, INIT_V4ADDR_PREFIX_LEN);
        } catch (IOException e) {
            throw new IOException("no IPv4 addresses were available for clat: " + e);
        }

        final Inet4Address v4;
        try {
            v4 = (Inet4Address) InetAddresses.parseNumericAddress(v4Str);
        } catch (ClassCastException | IllegalArgumentException | NullPointerException e) {
            throw new IOException("Invalid IPv4 address " + v4Str);
        }

        // [2] Generate a checksum-neutral IID.
        final Integer fwmark = getFwmark(netId);
        final String pfx96Str = nat64Prefix.getAddress().getHostAddress();
        final String v6Str;
        try {
            v6Str = mDeps.generateIpv6Address(iface, v4Str, pfx96Str, fwmark);
        } catch (IOException e) {
            throw new IOException("no IPv6 addresses were available for clat: " + e);
        }

        final Inet6Address pfx96 = (Inet6Address) nat64Prefix.getAddress();
        final Inet6Address v6;
        try {
            v6 = (Inet6Address) InetAddresses.parseNumericAddress(v6Str);
        } catch (ClassCastException | IllegalArgumentException | NullPointerException e) {
            throw new IOException("Invalid IPv6 address " + v6Str);
        }

        // [3] Open, configure and bring up the tun interface.
        // Create the v4-... tun interface.

        // Initialize all required file descriptors with null pointer. This makes the following
        // error handling easier. Simply always call #maybeCleanUp for closing file descriptors,
        // if any valid ones, in error handling.
        ParcelFileDescriptor tunFd = null;
        ParcelFileDescriptor readSock6 = null;
        ParcelFileDescriptor writeSock6 = null;

        final String tunIface = CLAT_PREFIX + iface;
        try {
            tunFd = mDeps.adoptFd(mDeps.createTunInterface(tunIface));
        } catch (IOException e) {
            throw new IOException("Create tun interface " + tunIface + " failed: " + e);
        }

        final int tunIfIndex = mDeps.getInterfaceIndex(tunIface);
        if (tunIfIndex == INVALID_IFINDEX) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Fail to get interface index for interface " + tunIface);
        }

        // disable IPv6 on it - failing to do so is not a critical error
        try {
            mNetd.interfaceSetEnableIPv6(tunIface, false /* enabled */);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Disable IPv6 on " + tunIface + " failed: " + e);
        }

        // Detect ipv4 mtu.
        final int detectedMtu;
        try {
            detectedMtu = mDeps.detectMtu(pfx96Str,
                ByteBuffer.wrap(GOOGLE_DNS_4.getAddress()).getInt(), fwmark);
        } catch (IOException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Detect MTU on " + tunIface + " failed: " + e);
        }
        final int mtu = adjustMtu(detectedMtu);
        Log.i(TAG, "detected ipv4 mtu of " + detectedMtu + " adjusted to " + mtu);

        // Config tun interface mtu, address and bring up.
        try {
            mNetd.interfaceSetMtu(tunIface, mtu);
        } catch (RemoteException | ServiceSpecificException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Set MTU " + mtu + " on " + tunIface + " failed: " + e);
        }
        final InterfaceConfigurationParcel ifConfig = new InterfaceConfigurationParcel();
        ifConfig.ifName = tunIface;
        ifConfig.ipv4Addr = v4Str;
        ifConfig.prefixLength = 32;
        ifConfig.hwAddr = "";
        ifConfig.flags = new String[] {IF_STATE_UP};
        try {
            mNetd.interfaceSetCfg(ifConfig);
        } catch (RemoteException | ServiceSpecificException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Setting IPv4 address to " + ifConfig.ipv4Addr + "/"
                    + ifConfig.prefixLength + " failed on " + ifConfig.ifName + ": " + e);
        }

        // [4] Open and configure local 464xlat read/write sockets.
        // Opens a packet socket to receive IPv6 packets in clatd.
        try {
            // Use a JNI call to get native file descriptor instead of Os.socket() because we would
            // like to use ParcelFileDescriptor to manage file descriptor. But ctor
            // ParcelFileDescriptor(FileDescriptor fd) is a @hide function. Need to use native file
            // descriptor to initialize ParcelFileDescriptor object instead.
            readSock6 = mDeps.adoptFd(mDeps.openPacketSocket());
        } catch (IOException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Open packet socket failed: " + e);
        }

        // Opens a raw socket with a given fwmark to send IPv6 packets in clatd.
        try {
            // Use a JNI call to get native file descriptor instead of Os.socket(). See above
            // reason why we use jniOpenPacketSocket6().
            writeSock6 = mDeps.adoptFd(mDeps.openRawSocket6(fwmark));
        } catch (IOException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Open raw socket failed: " + e);
        }

        final int ifIndex = mDeps.getInterfaceIndex(iface);
        if (ifIndex == INVALID_IFINDEX) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("Fail to get interface index for interface " + iface);
        }

        // Start translating packets to the new prefix.
        try {
            mDeps.addAnycastSetsockopt(writeSock6.getFileDescriptor(), v6Str, ifIndex);
        } catch (IOException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("add anycast sockopt failed: " + e);
        }

        // Tag socket as AID_CLAT to avoid duplicated CLAT data usage accounting.
        final long cookie;
        try {
            cookie = mDeps.getSocketCookie(writeSock6.getFileDescriptor());
            tagSocketAsClat(cookie);
        } catch (IOException e) {
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("tag raw socket failed: " + e);
        }

        // Update our packet socket filter to reflect the new 464xlat IP address.
        try {
            mDeps.configurePacketSocket(readSock6.getFileDescriptor(), v6Str, ifIndex);
        } catch (IOException e) {
            try {
                untagSocket(cookie);
            } catch (IOException e2) {
                Log.e(TAG, "untagSocket cookie " + cookie + " failed: " + e2);
            }
            maybeCleanUp(tunFd, readSock6, writeSock6);
            throw new IOException("configure packet socket failed: " + e);
        }

        // [5] Start clatd.
        final int pid;
        try {
            pid = mDeps.startClatd(tunFd.getFileDescriptor(), readSock6.getFileDescriptor(),
                    writeSock6.getFileDescriptor(), iface, pfx96Str, v4Str, v6Str);
        } catch (IOException e) {
            try {
                untagSocket(cookie);
            } catch (IOException e2) {
                Log.e(TAG, "untagSocket cookie " + cookie + " failed: " + e2);
            }
            throw new IOException("Error start clatd on " + iface + ": " + e);
        } finally {
            // The file descriptors have been duplicated (dup2) to clatd in native_startClatd().
            // Close these file descriptor stubs which are unused anymore.
            maybeCleanUp(tunFd, readSock6, writeSock6);
        }

        // [6] Initialize and store clatd tracker object.
        mClatdTracker = new ClatdTracker(iface, ifIndex, tunIface, tunIfIndex, v4, v6, pfx96,
                pid, cookie);

        // [7] Start BPF
        maybeStartBpf(mClatdTracker);

        return v6Str;
    }

    private void maybeStopBpf(final ClatdTracker tracker) {
        if (mIngressMap == null || mEgressMap == null) return;

        try {
            mDeps.tcFilterDelDev(tracker.ifIndex, INGRESS, (short) PRIO_CLAT, (short) ETH_P_IPV6);
        } catch (IOException e) {
            Log.e(TAG, "tc filter del dev (" + tracker.ifIndex + "[" + tracker.iface
                    + "]) ingress prio PRIO_CLAT protocol ipv6 failure: " + e);
        }

        try {
            mDeps.tcFilterDelDev(tracker.v4ifIndex, EGRESS, (short) PRIO_CLAT, (short) ETH_P_IP);
        } catch (IOException e) {
            Log.e(TAG, "tc filter del dev (" + tracker.v4ifIndex + "[" + tracker.v4iface
                    + "]) egress prio PRIO_CLAT protocol ip failure: " + e);
        }

        // We cleanup the maps last, so scanning through them can be used to
        // determine what still needs cleanup.

        final ClatEgress4Key txKey = new ClatEgress4Key(tracker.v4ifIndex, tracker.v4);
        try {
            mEgressMap.deleteEntry(txKey);
        } catch (ErrnoException | IllegalStateException e) {
            Log.e(TAG, "Could not delete entry (" + txKey + "): " + e);
        }

        final ClatIngress6Key rxKey = new ClatIngress6Key(tracker.ifIndex, tracker.pfx96,
                tracker.v6);
        try {
            mIngressMap.deleteEntry(rxKey);
        } catch (ErrnoException | IllegalStateException e) {
            Log.e(TAG, "Could not delete entry (" + rxKey + "): " + e);
        }
    }

    /**
     * Stop clatd
     */
    public void clatStop() throws IOException {
        if (!isStarted()) {
            throw new IOException("Clatd has not started");
        }
        Log.i(TAG, "Stopping clatd pid=" + mClatdTracker.pid + " on " + mClatdTracker.iface);

        maybeStopBpf(mClatdTracker);
        mDeps.stopClatd(mClatdTracker.pid);
        untagSocket(mClatdTracker.cookie);

        Log.i(TAG, "clatd on " + mClatdTracker.iface + " stopped");
        mClatdTracker = null;
    }

    private void dumpBpfIngress(@NonNull IndentingPrintWriter pw) {
        if (mIngressMap == null) {
            pw.println("No BPF ingress6 map");
            return;
        }

        try {
            if (mIngressMap.isEmpty()) {
                pw.println("<empty>");
            }
            pw.println("BPF ingress map: iif nat64Prefix v6Addr -> v4Addr oif");
            pw.increaseIndent();
            mIngressMap.forEach((k, v) -> {
                // TODO: print interface name
                pw.println(String.format("%d %s/96 %s -> %s %d", k.iif, k.pfx96, k.local6,
                        v.local4, v.oif));
            });
            pw.decreaseIndent();
        } catch (ErrnoException e) {
            pw.println("Error dumping BPF ingress6 map: " + e);
        }
    }

    private void dumpBpfEgress(@NonNull IndentingPrintWriter pw) {
        if (mEgressMap == null) {
            pw.println("No BPF egress4 map");
            return;
        }

        try {
            if (mEgressMap.isEmpty()) {
                pw.println("<empty>");
            }
            pw.println("BPF egress map: iif v4Addr -> v6Addr nat64Prefix oif");
            pw.increaseIndent();
            mEgressMap.forEach((k, v) -> {
                // TODO: print interface name
                pw.println(String.format("%d %s -> %s %s/96 %d %s", k.iif, k.local4, v.local6,
                        v.pfx96, v.oif, v.oifIsEthernet != 0 ? "ether" : "rawip"));
            });
            pw.decreaseIndent();
        } catch (ErrnoException e) {
            pw.println("Error dumping BPF egress4 map: " + e);
        }
    }

    /**
     * Dump the coordinator information.
     *
     * @param pw print writer.
     */
    public void dump(@NonNull IndentingPrintWriter pw) {
        // TODO: move map dump to a global place to avoid duplicate dump while there are two or
        // more IPv6 only networks.
        if (isStarted()) {
            pw.println("CLAT tracker: " + mClatdTracker);
            pw.println("Forwarding rules:");
            pw.increaseIndent();
            dumpBpfIngress(pw);
            dumpBpfEgress(pw);
            pw.decreaseIndent();
        } else {
            pw.println("<not started>");
        }
        pw.println();
    }

    /**
     * Get clatd tracker. For test only.
     */
    @VisibleForTesting
    @Nullable
    ClatdTracker getClatdTrackerForTesting() {
        return mClatdTracker;
    }

    private static native String native_selectIpv4Address(String v4addr, int prefixlen)
            throws IOException;
    private static native String native_generateIpv6Address(String iface, String v4,
            String prefix64, int mark) throws IOException;
    private static native int native_createTunInterface(String tuniface) throws IOException;
    private static native int native_detectMtu(String platSubnet, int platSuffix, int mark)
            throws IOException;
    private static native int native_openPacketSocket() throws IOException;
    private static native int native_openRawSocket6(int mark) throws IOException;
    private static native void native_addAnycastSetsockopt(FileDescriptor sock, String v6,
            int ifindex) throws IOException;
    private static native void native_configurePacketSocket(FileDescriptor sock, String v6,
            int ifindex) throws IOException;
    private static native int native_startClatd(FileDescriptor tunfd, FileDescriptor readsock6,
            FileDescriptor writesock6, String iface, String pfx96, String v4, String v6)
            throws IOException;
    private static native void native_stopClatd(int pid) throws IOException;
    private static native long native_getSocketCookie(FileDescriptor sock) throws IOException;
}
