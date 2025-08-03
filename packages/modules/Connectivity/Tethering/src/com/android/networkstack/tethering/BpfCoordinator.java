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

import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStats.UID_TETHERING;
import static android.net.netstats.provider.NetworkStatsProvider.QUOTA_UNLIMITED;
import static android.system.OsConstants.ETH_P_IP;
import static android.system.OsConstants.ETH_P_IPV6;

import static com.android.net.module.util.NetworkStackConstants.IPV4_MIN_MTU;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_LEN;
import static com.android.net.module.util.ip.ConntrackMonitor.ConntrackEvent;
import static com.android.networkstack.tethering.BpfUtils.DOWNSTREAM;
import static com.android.networkstack.tethering.BpfUtils.UPSTREAM;
import static com.android.networkstack.tethering.TetheringConfiguration.DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS;
import static com.android.networkstack.tethering.UpstreamNetworkState.isVcnInterface;
import static com.android.networkstack.tethering.util.TetheringUtils.getTetheringJniLibraryName;

import android.app.usage.NetworkStatsManager;
import android.net.INetd;
import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.TetherOffloadRuleParcel;
import android.net.ip.IpServer;
import android.net.netstats.provider.NetworkStatsProvider;
import android.os.Handler;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.BpfDump;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.NetworkStackConstants;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.S32;
import com.android.net.module.util.bpf.Tether4Key;
import com.android.net.module.util.bpf.Tether4Value;
import com.android.net.module.util.bpf.TetherStatsKey;
import com.android.net.module.util.bpf.TetherStatsValue;
import com.android.net.module.util.ip.ConntrackMonitor;
import com.android.net.module.util.ip.ConntrackMonitor.ConntrackEventConsumer;
import com.android.net.module.util.netlink.ConntrackMessage;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.networkstack.tethering.apishim.common.BpfCoordinatorShim;
import com.android.networkstack.tethering.util.TetheringUtils.ForwardedStats;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *  This coordinator is responsible for providing BPF offload relevant functionality.
 *  - Get tethering stats.
 *  - Set data limit.
 *  - Set global alert.
 *  - Add/remove forwarding rules.
 *
 * @hide
 */
public class BpfCoordinator {
    // Ensure the JNI code is loaded. In production this will already have been loaded by
    // TetherService, but for tests it needs to be either loaded here or loaded by every test.
    // TODO: is there a better way?
    static {
        System.loadLibrary(getTetheringJniLibraryName());
    }

    private static final String TAG = BpfCoordinator.class.getSimpleName();
    private static final int DUMP_TIMEOUT_MS = 10_000;
    private static final MacAddress NULL_MAC_ADDRESS = MacAddress.fromString(
            "00:00:00:00:00:00");
    private static final String TETHER_DOWNSTREAM4_MAP_PATH = makeMapPath(DOWNSTREAM, 4);
    private static final String TETHER_UPSTREAM4_MAP_PATH = makeMapPath(UPSTREAM, 4);
    private static final String TETHER_DOWNSTREAM6_FS_PATH = makeMapPath(DOWNSTREAM, 6);
    private static final String TETHER_UPSTREAM6_FS_PATH = makeMapPath(UPSTREAM, 6);
    private static final String TETHER_STATS_MAP_PATH = makeMapPath("stats");
    private static final String TETHER_LIMIT_MAP_PATH = makeMapPath("limit");
    private static final String TETHER_ERROR_MAP_PATH = makeMapPath("error");
    private static final String TETHER_DEV_MAP_PATH = makeMapPath("dev");
    private static final String DUMPSYS_RAWMAP_ARG_STATS = "--stats";
    private static final String DUMPSYS_RAWMAP_ARG_UPSTREAM4 = "--upstream4";

    /** The names of all the BPF counters defined in offload.h. */
    public static final String[] sBpfCounterNames = getBpfCounterNames();

    private static String makeMapPath(String which) {
        return "/sys/fs/bpf/tethering/map_offload_tether_" + which + "_map";
    }

    private static String makeMapPath(boolean downstream, int ipVersion) {
        return makeMapPath((downstream ? "downstream" : "upstream") + ipVersion);
    }

    @VisibleForTesting
    static final int CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS = 60_000;
    @VisibleForTesting
    static final int NF_CONNTRACK_TCP_TIMEOUT_ESTABLISHED = 432_000;
    @VisibleForTesting
    static final int NF_CONNTRACK_UDP_TIMEOUT_STREAM = 180;
    @VisibleForTesting
    static final int INVALID_MTU = 0;
    static final int NO_UPSTREAM = 0;

    // List of TCP port numbers which aren't offloaded because the packets require the netfilter
    // conntrack helper. See also TetherController::setForwardRules in netd.
    @VisibleForTesting
    static final short [] NON_OFFLOADED_UPSTREAM_IPV4_TCP_PORTS = new short [] {
            21 /* ftp */, 1723 /* pptp */};

    @VisibleForTesting
    enum StatsType {
        STATS_PER_IFACE,
        STATS_PER_UID,
    }

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final INetd mNetd;
    @NonNull
    private final SharedLog mLog;
    @NonNull
    private final Dependencies mDeps;
    @NonNull
    private final ConntrackMonitor mConntrackMonitor;
    @Nullable
    private final BpfTetherStatsProvider mStatsProvider;
    @NonNull
    private final BpfCoordinatorShim mBpfCoordinatorShim;
    @NonNull
    private final BpfConntrackEventConsumer mBpfConntrackEventConsumer;

    // True if BPF offload is supported, false otherwise. The BPF offload could be disabled by
    // a runtime resource overlay package or device configuration. This flag is only initialized
    // in the constructor because it is hard to unwind all existing change once device
    // configuration is changed. Especially the forwarding rules. Keep the same setting
    // to make it simpler. See also TetheringConfiguration.
    private final boolean mIsBpfEnabled;

    // Tracks whether BPF tethering is started or not. This is set by tethering before it
    // starts the first IpServer and is cleared by tethering shortly before the last IpServer
    // is stopped. Note that rule updates (especially deletions, but sometimes additions as
    // well) may arrive when this is false. If they do, they must be communicated to netd.
    // Changes in data limits may also arrive when this is false, and if they do, they must
    // also be communicated to netd.
    private boolean mPollingStarted = false;

    // Tracking remaining alert quota. Unlike limit quota is subject to interface, the alert
    // quota is interface independent and global for tether offload.
    private long mRemainingAlertQuota = QUOTA_UNLIMITED;

    // Maps upstream interface index to offloaded traffic statistics.
    // Always contains the latest total bytes/packets, since each upstream was started, received
    // from the BPF maps for each interface.
    private final SparseArray<ForwardedStats> mStats = new SparseArray<>();

    // Maps upstream interface names to interface quotas.
    // Always contains the latest value received from the framework for each interface, regardless
    // of whether offload is currently running (or is even supported) on that interface. Only
    // includes interfaces that have a quota set. Note that this map is used for storing the quota
    // which is set from the service. Because the service uses the interface name to present the
    // interface, this map uses the interface name to be the mapping index.
    private final HashMap<String, Long> mInterfaceQuotas = new HashMap<>();

    // Maps upstream interface index to interface names.
    // Store all interface name since boot. Used for lookup what interface name it is from the
    // tether stats got from netd because netd reports interface index to present an interface.
    // TODO: Remove the unused interface name.
    private final SparseArray<String> mInterfaceNames = new SparseArray<>();

    // How IPv6 upstream rules and downstream rules are managed in BpfCoordinator:
    // 1. Each upstream rule represents a downstream interface to an upstream interface forwarding.
    //    No upstream rule will be exist if there is no upstream interface.
    //    Note that there is at most one upstream interface for a given downstream interface.
    // 2. Each downstream rule represents an IPv6 neighbor, regardless of the existence of the
    //    upstream interface. If the upstream is not present, the downstream rules have an upstream
    //    interface index of NO_UPSTREAM, only exist in BpfCoordinator and won't be written to the
    //    BPF map. When the upstream comes back, those downstream rules will be updated by calling
    //    Ipv6DownstreamRule#onNewUpstream and written to the BPF map again. We don't remove the
    //    downstream rules when upstream is lost is because the upstream may come back with the
    //    same prefix and we won't receive any neighbor update event in this case.
    //    TODO: Remove downstream rules when upstream is lost and dump neighbors table when upstream
    //    interface comes back in order to reconstruct the downstream rules.
    // 3. It is the same thing for BpfCoordinator if there is no upstream interface or the upstream
    //    interface is a virtual interface (which currently not supports BPF). In this case,
    //    IpServer will update its upstream ifindex to NO_UPSTREAM to the BpfCoordinator.

    // Map of downstream rule maps. Each of these maps represents the IPv6 forwarding rules for a
    // given downstream. Each map:
    // - Is owned by the IpServer that is responsible for that downstream.
    // - Must only be modified by that IpServer.
    // - Is created when the IpServer adds its first rule, and deleted when the IpServer deletes
    //   its last rule (or clears its rules).
    // TODO: Perhaps seal the map and rule operations which communicates with netd into a class.
    // TODO: Does this need to be a LinkedHashMap or can it just be a HashMap? Also, could it be
    // a ConcurrentHashMap, in order to avoid the copies in tetherOffloadRuleClear
    // and tetherOffloadRuleUpdate?
    // TODO: Perhaps use one-dimensional map and access specific downstream rules via downstream
    // index. For doing that, IpServer must guarantee that it always has a valid IPv6 downstream
    // interface index while calling function to clear all rules. IpServer may be calling clear
    // rules function without a valid IPv6 downstream interface index even if it may have one
    // before. IpServer would need to call getInterfaceParams() in the constructor instead of when
    // startIpv6() is called, and make mInterfaceParams final.
    private final HashMap<IpServer, LinkedHashMap<Inet6Address, Ipv6DownstreamRule>>
            mIpv6DownstreamRules = new LinkedHashMap<>();

    // Map of IPv6 upstream rules maps. Each of these maps represents the IPv6 upstream rules for a
    // given downstream. Each map:
    // - Is owned by the IpServer that is responsible for that downstream.
    // - Must only be modified by that IpServer.
    // - Is created when the IpServer adds its first upstream rule, and deleted when the IpServer
    //   deletes its last upstream rule (or clears its upstream rules)
    // - Each upstream rule in the ArraySet is corresponding to an upstream interface.
    private final ArrayMap<IpServer, ArraySet<Ipv6UpstreamRule>>
            mIpv6UpstreamRules = new ArrayMap<>();

    // Map of downstream client maps. Each of these maps represents the IPv4 clients for a given
    // downstream. Needed to build IPv4 forwarding rules when conntrack events are received.
    // Each map:
    // - Is owned by the IpServer that is responsible for that downstream.
    // - Must only be modified by that IpServer.
    // - Is created when the IpServer adds its first client, and deleted when the IpServer deletes
    //   its last client.
    // Note that relying on the client address for finding downstream is okay for now because the
    // client address is unique. See PrivateAddressCoordinator#requestDownstreamAddress.
    // TODO: Refactor if any possible that the client address is not unique.
    private final HashMap<IpServer, HashMap<Inet4Address, ClientInfo>>
            mTetherClients = new HashMap<>();

    // Set for which downstream is monitoring the conntrack netlink message.
    private final Set<IpServer> mMonitoringIpServers = new HashSet<>();

    // Map of upstream interface IPv4 address to interface index.
    // TODO: consider making the key to be unique because the upstream address is not unique. It
    // is okay for now because there have only one upstream generally.
    private final HashMap<Inet4Address, Integer> mIpv4UpstreamIndices = new HashMap<>();

    // Map for upstream and downstream pair.
    private final HashMap<String, HashSet<String>> mForwardingPairs = new HashMap<>();

    // Set for upstream and downstream device map. Used for caching BPF dev map status and
    // reduce duplicate adding or removing map operations. Use LinkedHashSet because the test
    // BpfCoordinatorTest needs predictable iteration order.
    private final Set<Integer> mDeviceMapSet = new LinkedHashSet<>();

    // Tracks the last IPv4 upstream index. Support single upstream only.
    // TODO: Support multi-upstream interfaces.
    private int mLastIPv4UpstreamIfindex = 0;

    // Tracks the IPv4 upstream interface information.
    @Nullable
    private UpstreamInfo mIpv4UpstreamInfo = null;

    // Runnable that used by scheduling next polling of stats.
    private final Runnable mScheduledPollingStats = () -> {
        updateForwardedStats();
        maybeSchedulePollingStats();
    };

    // Runnable that used by scheduling next refreshing of conntrack timeout.
    private final Runnable mScheduledConntrackTimeoutUpdate = () -> {
        refreshAllConntrackTimeouts();
        maybeScheduleConntrackTimeoutUpdate();
    };

    // TODO: add BpfMap<TetherDownstream64Key, TetherDownstream64Value> retrieving function.
    @VisibleForTesting
    public abstract static class Dependencies {
        /** Get handler. */
        @NonNull public abstract Handler getHandler();

        /** Get netd. */
        @NonNull public abstract INetd getNetd();

        /** Get network stats manager. */
        @NonNull public abstract NetworkStatsManager getNetworkStatsManager();

        /** Get shared log. */
        @NonNull public abstract SharedLog getSharedLog();

        /** Get tethering configuration. */
        @Nullable public abstract TetheringConfiguration getTetherConfig();

        /** Get conntrack monitor. */
        @NonNull public ConntrackMonitor getConntrackMonitor(ConntrackEventConsumer consumer) {
            return new ConntrackMonitor(getHandler(), getSharedLog(), consumer);
        }

        /** Get interface information for a given interface. */
        @NonNull public InterfaceParams getInterfaceParams(String ifName) {
            return InterfaceParams.getByName(ifName);
        }

        /**
         * Represents an estimate of elapsed time since boot in nanoseconds.
         */
        public long elapsedRealtimeNanos() {
            return SystemClock.elapsedRealtimeNanos();
        }

        /**
         * Check OS Build at least S.
         *
         * TODO: move to BpfCoordinatorShim once the test doesn't need the mocked OS build for
         * testing different code flows concurrently.
         */
        public boolean isAtLeastS() {
            return SdkLevel.isAtLeastS();
        }

        /**
         * Gets the MTU of the given interface.
         */
        public int getNetworkInterfaceMtu(@NonNull String iface) {
            try {
                final NetworkInterface networkInterface = NetworkInterface.getByName(iface);
                return networkInterface == null ? INVALID_MTU : networkInterface.getMTU();
            } catch (SocketException e) {
                Log.e(TAG, "Could not get MTU for interface " + iface, e);
                return INVALID_MTU;
            }
        }

        /** Get downstream4 BPF map. */
        @Nullable public IBpfMap<Tether4Key, Tether4Value> getBpfDownstream4Map() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_DOWNSTREAM4_MAP_PATH,
                    Tether4Key.class, Tether4Value.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create downstream4 map: " + e);
                return null;
            }
        }

        /** Get upstream4 BPF map. */
        @Nullable public IBpfMap<Tether4Key, Tether4Value> getBpfUpstream4Map() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_UPSTREAM4_MAP_PATH,
                    Tether4Key.class, Tether4Value.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create upstream4 map: " + e);
                return null;
            }
        }

        /** Get downstream6 BPF map. */
        @Nullable public IBpfMap<TetherDownstream6Key, Tether6Value> getBpfDownstream6Map() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_DOWNSTREAM6_FS_PATH,
                    TetherDownstream6Key.class, Tether6Value.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create downstream6 map: " + e);
                return null;
            }
        }

        /** Get upstream6 BPF map. */
        @Nullable public IBpfMap<TetherUpstream6Key, Tether6Value> getBpfUpstream6Map() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_UPSTREAM6_FS_PATH,
                        TetherUpstream6Key.class, Tether6Value.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create upstream6 map: " + e);
                return null;
            }
        }

        /** Get stats BPF map. */
        @Nullable public IBpfMap<TetherStatsKey, TetherStatsValue> getBpfStatsMap() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_STATS_MAP_PATH,
                    TetherStatsKey.class, TetherStatsValue.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create stats map: " + e);
                return null;
            }
        }

        /** Get limit BPF map. */
        @Nullable public IBpfMap<TetherLimitKey, TetherLimitValue> getBpfLimitMap() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_LIMIT_MAP_PATH,
                    TetherLimitKey.class, TetherLimitValue.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create limit map: " + e);
                return null;
            }
        }

        /** Get dev BPF map. */
        @Nullable public IBpfMap<TetherDevKey, TetherDevValue> getBpfDevMap() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_DEV_MAP_PATH,
                    TetherDevKey.class, TetherDevValue.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create dev map: " + e);
                return null;
            }
        }

        /** Get error BPF map. */
        @Nullable public IBpfMap<S32, S32> getBpfErrorMap() {
            if (!isAtLeastS()) return null;
            try {
                return new BpfMap<>(TETHER_ERROR_MAP_PATH,
                    BpfMap.BPF_F_RDONLY, S32.class, S32.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create error map: " + e);
                return null;
            }
        }
    }

    @VisibleForTesting
    public BpfCoordinator(@NonNull Dependencies deps) {
        mDeps = deps;
        mHandler = mDeps.getHandler();
        mNetd = mDeps.getNetd();
        mLog = mDeps.getSharedLog().forSubComponent(TAG);
        mIsBpfEnabled = isBpfEnabled();

        // The conntrack consummer needs to be initialized in BpfCoordinator constructor because it
        // have to access the data members of BpfCoordinator which is not a static class. The
        // consumer object is also needed for initializing the conntrack monitor which may be
        // mocked for testing.
        mBpfConntrackEventConsumer = new BpfConntrackEventConsumer();
        mConntrackMonitor = mDeps.getConntrackMonitor(mBpfConntrackEventConsumer);

        BpfTetherStatsProvider provider = new BpfTetherStatsProvider();
        try {
            mDeps.getNetworkStatsManager().registerNetworkStatsProvider(
                    getClass().getSimpleName(), provider);
        } catch (RuntimeException e) {
            // TODO: Perhaps not allow to use BPF offload because the reregistration failure
            // implied that no data limit could be applies on a metered upstream if any.
            Log.wtf(TAG, "Cannot register offload stats provider: " + e);
            provider = null;
        }
        mStatsProvider = provider;

        mBpfCoordinatorShim = BpfCoordinatorShim.getBpfCoordinatorShim(deps);
        if (!mBpfCoordinatorShim.isInitialized()) {
            mLog.e("Bpf shim not initialized");
        }
    }

    /**
     * Start BPF tethering offload stats polling when the first upstream is started.
     * Note that this can be only called on handler thread.
     * TODO: Perhaps check BPF support before starting.
     * TODO: Start the stats polling only if there is any client on the downstream.
     */
    public void startPolling() {
        if (mPollingStarted) return;

        if (!isUsingBpf()) {
            mLog.i("BPF is not using");
            return;
        }

        mPollingStarted = true;
        maybeSchedulePollingStats();
        maybeScheduleConntrackTimeoutUpdate();

        mLog.i("Polling started");
    }

    /**
     * Stop BPF tethering offload stats polling.
     * The data limit cleanup and the tether stats maps cleanup are not implemented here.
     * These cleanups rely on all IpServers calling #removeIpv6DownstreamRule. After the
     * last rule is removed from the upstream, #removeIpv6DownstreamRule does the cleanup
     * functionality.
     * Note that this can be only called on handler thread.
     */
    public void stopPolling() {
        if (!mPollingStarted) return;

        // Stop scheduled polling conntrack timeout.
        if (mHandler.hasCallbacks(mScheduledConntrackTimeoutUpdate)) {
            mHandler.removeCallbacks(mScheduledConntrackTimeoutUpdate);
        }
        // Stop scheduled polling stats and poll the latest stats from BPF maps.
        if (mHandler.hasCallbacks(mScheduledPollingStats)) {
            mHandler.removeCallbacks(mScheduledPollingStats);
        }
        updateForwardedStats();
        mPollingStarted = false;

        mLog.i("Polling stopped");
    }

    /**
     * Return whether BPF offload is supported
     */
    public boolean isUsingBpfOffload() {
        return isUsingBpf();
    }

    // This is identical to isUsingBpfOffload above but is only used internally.
    // The reason for having two separate methods is that the code calls isUsingBpf
    // very often. But the tests call verifyNoMoreInteractions, which will check all
    // calls to public methods. If isUsingBpf were public, the test would need to
    // verify all calls to it, which would clutter the test.
    private boolean isUsingBpf() {
        return mIsBpfEnabled && mBpfCoordinatorShim.isInitialized();
    }

    /**
     * Start conntrack message monitoring.
     * Note that this can be only called on handler thread.
     *
     * TODO: figure out a better logging for non-interesting conntrack message.
     * For example, the following logging is an IPCTNL_MSG_CT_GET message but looks scary.
     * +---------------------------------------------------------------------------+
     * | ERROR unparsable netlink msg: 1400000001010103000000000000000002000000    |
     * +------------------+--------------------------------------------------------+
     * |                  | struct nlmsghdr                                        |
     * | 14000000         | length = 20                                            |
     * | 0101             | type = NFNL_SUBSYS_CTNETLINK << 8 | IPCTNL_MSG_CT_GET  |
     * | 0103             | flags                                                  |
     * | 00000000         | seqno = 0                                              |
     * | 00000000         | pid = 0                                                |
     * |                  | struct nfgenmsg                                        |
     * | 02               | nfgen_family  = AF_INET                                |
     * | 00               | version = NFNETLINK_V0                                 |
     * | 0000             | res_id                                                 |
     * +------------------+--------------------------------------------------------+
     * See NetlinkMonitor#handlePacket, NetlinkMessage#parseNfMessage.
     */
    public void startMonitoring(@NonNull final IpServer ipServer) {
        // TODO: Wrap conntrackMonitor starting function into mBpfCoordinatorShim.
        if (!isUsingBpf() || !mDeps.isAtLeastS()) return;

        if (mMonitoringIpServers.contains(ipServer)) {
            Log.wtf(TAG, "The same downstream " + ipServer.interfaceName()
                    + " should not start monitoring twice.");
            return;
        }

        if (mMonitoringIpServers.isEmpty()) {
            mConntrackMonitor.start();
            mLog.i("Monitoring started");
        }

        mMonitoringIpServers.add(ipServer);
    }

    /**
     * Stop conntrack event monitoring.
     * Note that this can be only called on handler thread.
     */
    public void stopMonitoring(@NonNull final IpServer ipServer) {
        // TODO: Wrap conntrackMonitor stopping function into mBpfCoordinatorShim.
        if (!isUsingBpf() || !mDeps.isAtLeastS()) return;

        // Ignore stopping monitoring if the monitor has never started for a given IpServer.
        if (!mMonitoringIpServers.contains(ipServer)) {
            mLog.e("Ignore stopping monitoring because monitoring has never started for "
                    + ipServer.interfaceName());
            return;
        }

        mMonitoringIpServers.remove(ipServer);

        if (!mMonitoringIpServers.isEmpty()) return;

        mConntrackMonitor.stop();
        mLog.i("Monitoring stopped");
    }

    /**
     * Add IPv6 upstream rule. After adding the first rule on a given upstream, must add the
     * data limit on the given upstream.
     */
    private void addIpv6UpstreamRule(
            @NonNull final IpServer ipServer, @NonNull final Ipv6UpstreamRule rule) {
        if (!isUsingBpf()) return;

        // Add upstream and downstream interface index to dev map.
        maybeAddDevMap(rule.upstreamIfindex, rule.downstreamIfindex);

        // When the first rule is added to an upstream, setup upstream forwarding and data limit.
        maybeSetLimit(rule.upstreamIfindex);

        // TODO: support upstream forwarding on non-point-to-point interfaces.
        // TODO: get the MTU from LinkProperties and update the rules when it changes.
        if (!mBpfCoordinatorShim.addIpv6UpstreamRule(rule)) {
            return;
        }

        ArraySet<Ipv6UpstreamRule> rules = mIpv6UpstreamRules.computeIfAbsent(
                ipServer, k -> new ArraySet<Ipv6UpstreamRule>());
        rules.add(rule);
    }

    /**
     * Clear all IPv6 upstream rules for a given downstream. After removing the last rule on a given
     * upstream, must clear data limit, update the last tether stats and remove the tether stats in
     * the BPF maps.
     */
    private void clearIpv6UpstreamRules(@NonNull final IpServer ipServer) {
        if (!isUsingBpf()) return;

        final ArraySet<Ipv6UpstreamRule> upstreamRules = mIpv6UpstreamRules.remove(ipServer);
        if (upstreamRules == null) return;

        int upstreamIfindex = 0;
        for (Ipv6UpstreamRule rule: upstreamRules) {
            if (upstreamIfindex != 0 && rule.upstreamIfindex != upstreamIfindex) {
                Log.wtf(TAG, "BUG: upstream rules point to more than one interface");
            }
            upstreamIfindex = rule.upstreamIfindex;
            mBpfCoordinatorShim.removeIpv6UpstreamRule(rule);
        }
        // Clear the limit if there are no more rules on the given upstream.
        // Using upstreamIfindex outside the loop is fine because all the rules for a given IpServer
        // will always have the same upstream index (since they are always added all together by
        // updateAllIpv6Rules).
        // The upstreamIfindex can't be 0 because we won't add an Ipv6UpstreamRule with
        // upstreamIfindex == 0 and if there is no Ipv6UpstreamRule for an IpServer, it will be
        // removed from mIpv6UpstreamRules.
        if (upstreamIfindex == 0) {
            Log.wtf(TAG, "BUG: upstream rules have empty Set or rule.upstreamIfindex == 0");
            return;
        }
        maybeClearLimit(upstreamIfindex);
    }

    /**
     * Add IPv6 downstream rule.
     * Note that this can be only called on handler thread.
     */
    public void addIpv6DownstreamRule(
            @NonNull final IpServer ipServer, @NonNull final Ipv6DownstreamRule rule) {
        if (!isUsingBpf()) return;

        // TODO: Perhaps avoid to add a duplicate rule.
        if (rule.upstreamIfindex != NO_UPSTREAM
                && !mBpfCoordinatorShim.addIpv6DownstreamRule(rule)) return;

        LinkedHashMap<Inet6Address, Ipv6DownstreamRule> rules =
                mIpv6DownstreamRules.computeIfAbsent(ipServer,
                        k -> new LinkedHashMap<Inet6Address, Ipv6DownstreamRule>());
        rules.put(rule.address, rule);
    }

    /**
     * Remove IPv6 downstream rule.
     * Note that this can be only called on handler thread.
     */
    public void removeIpv6DownstreamRule(
            @NonNull final IpServer ipServer, @NonNull final Ipv6DownstreamRule rule) {
        if (!isUsingBpf()) return;

        if (rule.upstreamIfindex != NO_UPSTREAM
                && !mBpfCoordinatorShim.removeIpv6DownstreamRule(rule)) return;

        LinkedHashMap<Inet6Address, Ipv6DownstreamRule> rules = mIpv6DownstreamRules.get(ipServer);
        if (rules == null) return;

        // If no rule is removed, return early. Avoid unnecessary work on a non-existent rule which
        // may have never been added or removed already.
        if (rules.remove(rule.address) == null) return;

        // Remove the downstream entry if it has no more rule.
        if (rules.isEmpty()) {
            mIpv6DownstreamRules.remove(ipServer);
        }
    }

    /**
      * Clear all downstream rules for a given IpServer and return a copy of all removed rules.
      */
    @Nullable
    private Collection<Ipv6DownstreamRule> clearIpv6DownstreamRules(
            @NonNull final IpServer ipServer) {
        final LinkedHashMap<Inet6Address, Ipv6DownstreamRule> downstreamRules =
                mIpv6DownstreamRules.remove(ipServer);
        if (downstreamRules == null) return null;

        final Collection<Ipv6DownstreamRule> removedRules = downstreamRules.values();
        for (final Ipv6DownstreamRule rule : removedRules) {
            if (rule.upstreamIfindex == NO_UPSTREAM) continue;
            mBpfCoordinatorShim.removeIpv6DownstreamRule(rule);
        }
        return removedRules;
    }

    /**
     * Clear all forwarding rules for a given downstream.
     * Note that this can be only called on handler thread.
     */
    public void clearAllIpv6Rules(@NonNull final IpServer ipServer) {
        if (!isUsingBpf()) return;

        // Clear downstream rules first, because clearing upstream rules fetches the stats, and
        // fetching the stats requires that no rules be forwarding traffic to or from the upstream.
        clearIpv6DownstreamRules(ipServer);
        clearIpv6UpstreamRules(ipServer);
    }

    /**
     * Delete all upstream and downstream rules for the passed-in IpServer, and if the new upstream
     * is nonzero, reapply them to the new upstream.
     * Note that this can be only called on handler thread.
     */
    public void updateAllIpv6Rules(@NonNull final IpServer ipServer,
            final InterfaceParams interfaceParams, int newUpstreamIfindex,
            @NonNull final Set<IpPrefix> newUpstreamPrefixes) {
        if (!isUsingBpf()) return;

        // Remove IPv6 downstream rules. Remove the old ones before adding the new rules, otherwise
        // we need to keep a copy of the old rules.
        // We still need to keep the downstream rules even when the upstream goes away because it
        // may come back with the same prefixes (unlikely, but possible). Neighbor entries won't be
        // deleted and we're not expected to receive new Neighbor events in this case.
        // TODO: Add new rule first to reduce the latency which has no rule. But this is okay
        //       because if this is a new upstream, it will probably have different prefixes than
        //       the one these downstream rules are in. If so, they will never see any downstream
        //       traffic before new neighbor entries are created.
        final Collection<Ipv6DownstreamRule> deletedDownstreamRules =
                clearIpv6DownstreamRules(ipServer);

        // Remove IPv6 upstream rules. Downstream rules must be removed first because
        // BpfCoordinatorShimImpl#tetherOffloadGetAndClearStats will be called after the removal of
        // the last upstream rule and it requires that no rules be forwarding traffic to or from
        // that upstream.
        clearIpv6UpstreamRules(ipServer);

        // Add new upstream rules.
        if (newUpstreamIfindex != 0 && interfaceParams != null && interfaceParams.macAddr != null) {
            for (final IpPrefix ipPrefix : newUpstreamPrefixes) {
                addIpv6UpstreamRule(ipServer, new Ipv6UpstreamRule(
                        newUpstreamIfindex, interfaceParams.index, ipPrefix,
                        interfaceParams.macAddr, NULL_MAC_ADDRESS, NULL_MAC_ADDRESS));
            }
        }

        // Add updated downstream rules.
        if (deletedDownstreamRules == null) return;
        for (final Ipv6DownstreamRule rule : deletedDownstreamRules) {
            addIpv6DownstreamRule(ipServer, rule.onNewUpstream(newUpstreamIfindex));
        }
    }

    /**
     * Add upstream name to lookup table. The lookup table is used for tether stats interface name
     * lookup because the netd only reports interface index in BPF tether stats but the service
     * expects the interface name in NetworkStats object.
     * Note that this can be only called on handler thread.
     */
    public void maybeAddUpstreamToLookupTable(int upstreamIfindex, @Nullable String upstreamIface) {
        if (!isUsingBpf()) return;

        if (upstreamIfindex == 0 || TextUtils.isEmpty(upstreamIface)) return;

        if (isVcnInterface(upstreamIface)) return;

        // The same interface index to name mapping may be added by different IpServer objects or
        // re-added by reconnection on the same upstream interface. Ignore the duplicate one.
        final String iface = mInterfaceNames.get(upstreamIfindex);
        if (iface == null) {
            mInterfaceNames.put(upstreamIfindex, upstreamIface);
        } else if (!TextUtils.equals(iface, upstreamIface)) {
            Log.wtf(TAG, "The upstream interface name " + upstreamIface
                    + " is different from the existing interface name "
                    + iface + " for index " + upstreamIfindex);
        }
    }

    /**
     * Add downstream client.
     * Note that this can be only called on handler thread.
     */
    public void tetherOffloadClientAdd(@NonNull final IpServer ipServer,
            @NonNull final ClientInfo client) {
        if (!isUsingBpf()) return;

        if (!mTetherClients.containsKey(ipServer)) {
            mTetherClients.put(ipServer, new HashMap<Inet4Address, ClientInfo>());
        }

        HashMap<Inet4Address, ClientInfo> clients = mTetherClients.get(ipServer);
        clients.put(client.clientAddress, client);
    }

    /**
     * Remove a downstream client and its rules if any.
     * Note that this can be only called on handler thread.
     */
    public void tetherOffloadClientRemove(@NonNull final IpServer ipServer,
            @NonNull final ClientInfo client) {
        if (!isUsingBpf()) return;

        // No clients on the downstream, return early.
        HashMap<Inet4Address, ClientInfo> clients = mTetherClients.get(ipServer);
        if (clients == null) return;

        // No client is removed, return early.
        if (clients.remove(client.clientAddress) == null) return;

        // Remove the client's rules. Removing the client implies that its rules are not used
        // anymore.
        tetherOffloadRuleClear(client);

        // Remove the downstream entry if it has no more client.
        if (clients.isEmpty()) {
            mTetherClients.remove(ipServer);
        }
    }

    /**
     * Clear all downstream clients and their rules if any.
     * Note that this can be only called on handler thread.
     */
    public void tetherOffloadClientClear(@NonNull final IpServer ipServer) {
        if (!isUsingBpf()) return;

        final HashMap<Inet4Address, ClientInfo> clients = mTetherClients.get(ipServer);
        if (clients == null) return;

        // Need to build a client list because the client map may be changed in the iteration.
        for (final ClientInfo c : new ArrayList<ClientInfo>(clients.values())) {
            tetherOffloadClientRemove(ipServer, c);
        }
    }

    /**
     * Clear all forwarding IPv4 rules for a given client.
     * Note that this can be only called on handler thread.
     */
    private void tetherOffloadRuleClear(@NonNull final ClientInfo clientInfo) {
        // TODO: consider removing the rules in #tetherOffloadRuleForEach once BpfMap#forEach
        // can guarantee that deleting some pass-in rules in the BPF map iteration can still
        // walk through every entry.
        final Inet4Address clientAddr = clientInfo.clientAddress;
        final Set<Integer> upstreamIndiceSet = new ArraySet<Integer>();
        final Set<Tether4Key> deleteUpstreamRuleKeys = new ArraySet<Tether4Key>();
        final Set<Tether4Key> deleteDownstreamRuleKeys = new ArraySet<Tether4Key>();

        // Find the rules which are related with the given client.
        mBpfCoordinatorShim.tetherOffloadRuleForEach(UPSTREAM, (k, v) -> {
            if (Arrays.equals(k.src4, clientAddr.getAddress())) {
                deleteUpstreamRuleKeys.add(k);
            }
        });
        mBpfCoordinatorShim.tetherOffloadRuleForEach(DOWNSTREAM, (k, v) -> {
            if (Arrays.equals(v.dst46, toIpv4MappedAddressBytes(clientAddr))) {
                deleteDownstreamRuleKeys.add(k);
                upstreamIndiceSet.add((int) k.iif);
            }
        });

        // The rules should be paired on upstream and downstream map because they are added by
        // conntrack events which have bidirectional information.
        // TODO: Consider figuring out a way to fix. Probably delete all rules to fallback.
        if (deleteUpstreamRuleKeys.size() != deleteDownstreamRuleKeys.size()) {
            Log.wtf(TAG, "The deleting rule numbers are different on upstream4 and downstream4 ("
                    + "upstream: " + deleteUpstreamRuleKeys.size() + ", "
                    + "downstream: " + deleteDownstreamRuleKeys.size() + ").");
            return;
        }

        // Delete the rules which are related with the given client.
        for (final Tether4Key k : deleteUpstreamRuleKeys) {
            mBpfCoordinatorShim.tetherOffloadRuleRemove(UPSTREAM, k);
        }
        for (final Tether4Key k : deleteDownstreamRuleKeys) {
            mBpfCoordinatorShim.tetherOffloadRuleRemove(DOWNSTREAM, k);
        }

        // Cleanup each upstream interface by a set which avoids duplicated work on the same
        // upstream interface. Cleaning up the same interface twice (or more) here may raise
        // an exception because all related information were removed in the first deletion.
        for (final int upstreamIndex : upstreamIndiceSet) {
            maybeClearLimit(upstreamIndex);
        }
    }

    /**
     * Clear all forwarding IPv4 rules for a given downstream. Needed because the client may still
     * connect on the downstream but the existing rules are not required anymore. Ex: upstream
     * changed.
     */
    private void tetherOffloadRule4Clear(@NonNull final IpServer ipServer) {
        if (!isUsingBpf()) return;

        final HashMap<Inet4Address, ClientInfo> clients = mTetherClients.get(ipServer);
        if (clients == null) return;

        // The value should be unique as its key because currently the key was using from its
        // client address of ClientInfo. See #tetherOffloadClientAdd.
        for (final ClientInfo client : clients.values()) {
            tetherOffloadRuleClear(client);
        }
    }

    private boolean isValidUpstreamIpv4Address(@NonNull final InetAddress addr) {
        if (!(addr instanceof Inet4Address)) return false;
        Inet4Address v4 = (Inet4Address) addr;
        if (v4.isAnyLocalAddress() || v4.isLinkLocalAddress()
                || v4.isLoopbackAddress() || v4.isMulticastAddress()) {
            return false;
        }
        return true;
    }

    private int getMtu(@NonNull final String ifaceName, @NonNull final LinkProperties lp) {
        int mtu = INVALID_MTU;

        if (ifaceName.equals(lp.getInterfaceName())) {
            mtu = lp.getMtu();
        }

        // Get mtu via kernel if mtu is not found in LinkProperties.
        if (mtu == INVALID_MTU) {
            mtu = mDeps.getNetworkInterfaceMtu(ifaceName);
        }

        // Use default mtu if can't find any.
        if (mtu == INVALID_MTU) mtu = NetworkStackConstants.ETHER_MTU;

        // Clamp to minimum ipv4 mtu
        if (mtu < IPV4_MIN_MTU) mtu = IPV4_MIN_MTU;

        return mtu;
    }

    /**
     * Call when UpstreamNetworkState may be changed.
     * If upstream has ipv4 for tethering, update this new UpstreamNetworkState
     * to BpfCoordinator for building upstream interface index mapping. Otherwise,
     * clear the all existing rules if any.
     *
     * Note that this can be only called on handler thread.
     */
    public void updateUpstreamNetworkState(UpstreamNetworkState ns) {
        if (!isUsingBpf()) return;

        int upstreamIndex = 0;
        int mtu = INVALID_MTU;

        // This will not work on a network that is using 464xlat because hasIpv4Address will not be
        // true.
        // TODO: need to consider 464xlat.
        if (ns != null && ns.linkProperties != null && ns.linkProperties.hasIpv4Address()) {
            // TODO: support ether ip upstream interface.
            final String ifaceName = ns.linkProperties.getInterfaceName();
            final InterfaceParams params = mDeps.getInterfaceParams(ifaceName);
            final boolean isVcn = isVcnInterface(ifaceName);
            mtu = getMtu(ifaceName, ns.linkProperties);

            if (!isVcn && params != null && !params.hasMacAddress /* raw ip upstream only */) {
                upstreamIndex = params.index;
            }
        }
        if (mLastIPv4UpstreamIfindex == upstreamIndex) return;

        // Clear existing rules if upstream interface is changed. The existing rules should be
        // cleared before upstream index mapping is cleared. It can avoid that ipServer or
        // conntrack event may use the non-existing upstream interfeace index to build a removing
        // key while removeing the rules. Can't notify each IpServer to clear the rules as
        // IPv6TetheringCoordinator#updateUpstreamNetworkState because the IpServer may not
        // handle the upstream changing notification before changing upstream index mapping.
        if (mLastIPv4UpstreamIfindex != 0) {
            // Clear all forwarding IPv4 rules for all downstreams.
            for (final IpServer ipserver : mTetherClients.keySet()) {
                tetherOffloadRule4Clear(ipserver);
            }
        }

        // Don't update mLastIPv4UpstreamIfindex before clearing existing rules if any. Need that
        // to tell if it is required to clean the out-of-date rules.
        mLastIPv4UpstreamIfindex = upstreamIndex;

        // If link properties are valid, build the upstream information mapping. Otherwise, clear
        // the upstream interface index mapping, to ensure that any conntrack events that arrive
        // after the upstream is lost do not incorrectly add rules pointing at the upstream.
        if (upstreamIndex == 0) {
            mIpv4UpstreamIndices.clear();
            mIpv4UpstreamInfo = null;
            return;
        }

        mIpv4UpstreamInfo = new UpstreamInfo(upstreamIndex, mtu);
        Collection<InetAddress> addresses = ns.linkProperties.getAddresses();
        for (final InetAddress addr: addresses) {
            if (isValidUpstreamIpv4Address(addr)) {
                mIpv4UpstreamIndices.put((Inet4Address) addr, upstreamIndex);
            }
        }
    }

    private boolean is464XlatInterface(@NonNull String ifaceName) {
        return ifaceName.startsWith("v4-");
    }

    private void maybeAttachProgramImpl(@NonNull String iface, boolean downstream) {
        mBpfCoordinatorShim.attachProgram(iface, downstream, true /* ipv4 */);

        // Ignore 464xlat interface because it is IPv4 only.
        if (!is464XlatInterface(iface)) {
            mBpfCoordinatorShim.attachProgram(iface, downstream, false /* ipv4 */);
        }
    }

    private void maybeDetachProgramImpl(@NonNull String iface) {
        mBpfCoordinatorShim.detachProgram(iface, true /* ipv4 */);

        // Ignore 464xlat interface because it is IPv4 only.
        if (!is464XlatInterface(iface)) {
            mBpfCoordinatorShim.detachProgram(iface, false /* ipv4 */);
        }
    }

    /**
     * Attach BPF program
     *
     * TODO: consider error handling if the attach program failed.
     */
    public void maybeAttachProgram(@NonNull String intIface, @NonNull String extIface) {
        if (!isUsingBpf() || isVcnInterface(extIface)) return;

        if (forwardingPairExists(intIface, extIface)) return;

        boolean firstUpstreamForThisDownstream = !isAnyForwardingPairOnDownstream(intIface);
        boolean firstDownstreamForThisUpstream = !isAnyForwardingPairOnUpstream(extIface);
        forwardingPairAdd(intIface, extIface);

        // Attach if the downstream is the first time to be used in a forwarding pair.
        // Ex: IPv6 only interface has two forwarding pair, iface and v4-iface, on the
        // same downstream.
        if (firstUpstreamForThisDownstream) {
            maybeAttachProgramImpl(intIface, UPSTREAM);
        }
        // Attach if the upstream is the first time to be used in a forwarding pair.
        if (firstDownstreamForThisUpstream) {
            maybeAttachProgramImpl(extIface, DOWNSTREAM);
        }
    }

    /**
     * Detach BPF program
     */
    public void maybeDetachProgram(@NonNull String intIface, @NonNull String extIface) {
        if (!isUsingBpf()) return;

        forwardingPairRemove(intIface, extIface);

        // Detaching program may fail because the interface has been removed already.
        if (!isAnyForwardingPairOnDownstream(intIface)) {
            maybeDetachProgramImpl(intIface);
        }
        // Detach if no more forwarding pair is using the upstream.
        if (!isAnyForwardingPairOnUpstream(extIface)) {
            maybeDetachProgramImpl(extIface);
        }
    }

    // TODO: make mInterfaceNames accessible to the shim and move this code to there.
    // This function should only be used for logging/dump purposes.
    private String getIfName(int ifindex) {
        // TODO: return something more useful on lookup failure
        // likely use the 'iface_index_name_map' bpf map and/or if_nametoindex
        // perhaps should even check that all 3 match if available.
        return mInterfaceNames.get(ifindex, Integer.toString(ifindex));
    }

    /**
     * Dump information.
     * Block the function until all the data are dumped on the handler thread or timed-out. The
     * reason is that dumpsys invokes this function on the thread of caller and the data may only
     * be allowed to be accessed on the handler thread.
     */
    public void dump(@NonNull IndentingPrintWriter pw) {
        // Note that EthernetTetheringTest#isTetherConfigBpfOffloadEnabled relies on
        // "mIsBpfEnabled" to check tethering config via dumpsys. Beware of the change if any.
        pw.println("mIsBpfEnabled: " + mIsBpfEnabled);
        pw.println("Polling " + (mPollingStarted ? "started" : "not started"));
        pw.println("Stats provider " + (mStatsProvider != null
                ? "registered" : "not registered"));
        pw.println("Upstream quota: " + mInterfaceQuotas.toString());
        pw.println("Polling interval: " + getPollingInterval() + " ms");
        pw.println("Bpf shim: " + mBpfCoordinatorShim.toString());

        pw.println("Forwarding stats:");
        pw.increaseIndent();
        if (mStats.size() == 0) {
            pw.println("<empty>");
        } else {
            dumpStats(pw);
        }
        pw.decreaseIndent();

        pw.println("BPF stats:");
        pw.increaseIndent();
        dumpBpfStats(pw);
        pw.decreaseIndent();
        pw.println();

        pw.println("Forwarding rules:");
        pw.increaseIndent();
        dumpIpv6ForwardingRulesByDownstream(pw);
        dumpBpfForwardingRulesIpv6(pw);
        dumpBpfForwardingRulesIpv4(pw);
        pw.decreaseIndent();
        pw.println();

        pw.println("Device map:");
        pw.increaseIndent();
        dumpDevmap(pw);
        pw.decreaseIndent();

        pw.println("Client Information:");
        pw.increaseIndent();
        if (mTetherClients.isEmpty()) {
            pw.println("<empty>");
        } else {
            pw.println(mTetherClients.toString());
        }
        pw.decreaseIndent();

        pw.println("IPv4 Upstream Indices:");
        pw.increaseIndent();
        if (mIpv4UpstreamIndices.isEmpty()) {
            pw.println("<empty>");
        } else {
            pw.println(mIpv4UpstreamIndices.toString());
        }
        pw.decreaseIndent();

        pw.println("IPv4 Upstream Information: "
                + (mIpv4UpstreamInfo != null ? mIpv4UpstreamInfo : "<empty>"));

        pw.println();
        pw.println("Forwarding counters:");
        pw.increaseIndent();
        dumpCounters(pw);
        pw.decreaseIndent();
    }

    private void dumpStats(@NonNull IndentingPrintWriter pw) {
        for (int i = 0; i < mStats.size(); i++) {
            final int upstreamIfindex = mStats.keyAt(i);
            final ForwardedStats stats = mStats.get(upstreamIfindex);
            pw.println(String.format("%d(%s) - %s", upstreamIfindex, getIfName(upstreamIfindex),
                    stats.toString()));
        }
    }
    private void dumpBpfStats(@NonNull IndentingPrintWriter pw) {
        try (IBpfMap<TetherStatsKey, TetherStatsValue> map = mDeps.getBpfStatsMap()) {
            if (map == null) {
                pw.println("No BPF stats map");
                return;
            }
            if (map.isEmpty()) {
                pw.println("<empty>");
            }
            map.forEach((k, v) -> {
                pw.println(String.format("%s: %s", k, v));
            });
        } catch (ErrnoException | IOException e) {
            pw.println("Error dumping BPF stats map: " + e);
        }
    }

    private void dumpIpv6ForwardingRulesByDownstream(@NonNull IndentingPrintWriter pw) {
        pw.println("IPv6 Forwarding rules by downstream interface:");
        pw.increaseIndent();
        if (mIpv6DownstreamRules.size() == 0) {
            pw.println("No downstream IPv6 rules");
            pw.decreaseIndent();
            return;
        }

        for (Map.Entry<IpServer, LinkedHashMap<Inet6Address, Ipv6DownstreamRule>> entry :
                mIpv6DownstreamRules.entrySet()) {
            IpServer ipServer = entry.getKey();
            // The rule downstream interface index is paired with the interface name from
            // IpServer#interfaceName. See #startIPv6, #updateIpv6ForwardingRules in IpServer.
            final String downstreamIface = ipServer.interfaceName();
            pw.println("[" + downstreamIface + "]: iif(iface) oif(iface) v6addr "
                    + "[srcmac] [dstmac]");

            pw.increaseIndent();
            LinkedHashMap<Inet6Address, Ipv6DownstreamRule> rules = entry.getValue();
            for (Ipv6DownstreamRule rule : rules.values()) {
                final int upstreamIfindex = rule.upstreamIfindex;
                pw.println(String.format("%d(%s) %d(%s) %s [%s] [%s]", upstreamIfindex,
                        getIfName(upstreamIfindex), rule.downstreamIfindex,
                        getIfName(rule.downstreamIfindex), rule.address.getHostAddress(),
                        rule.srcMac, rule.dstMac));
            }
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
    }

    /**
     * Returns a /64 IpPrefix corresponding to the passed in byte array
     *
     * @param ip64 byte array to convert format
     * @return the converted IpPrefix
     */
    @VisibleForTesting
    public static IpPrefix bytesToPrefix(final byte[] ip64) {
        IpPrefix sourcePrefix;
        byte[] prefixBytes = Arrays.copyOf(ip64, IPV6_ADDR_LEN);
        try {
            sourcePrefix = new IpPrefix(InetAddress.getByAddress(prefixBytes), 64);
        } catch (UnknownHostException e) {
            // Cannot happen. InetAddress.getByAddress can only throw an exception if the byte array
            // is the wrong length, but we allocate it with fixed length IPV6_ADDR_LEN.
            throw new IllegalArgumentException("Invalid IPv6 address");
        }
        return sourcePrefix;
    }

    private String ipv6UpstreamRuleToString(TetherUpstream6Key key, Tether6Value value) {
        return String.format("%d(%s) [%s] [%s] -> %d(%s) %04x [%s] [%s]",
                key.iif, getIfName(key.iif), key.dstMac, bytesToPrefix(key.src64), value.oif,
                getIfName(value.oif), value.ethProto, value.ethSrcMac, value.ethDstMac);
    }

    private void dumpIpv6UpstreamRules(IndentingPrintWriter pw) {
        try (IBpfMap<TetherUpstream6Key, Tether6Value> map = mDeps.getBpfUpstream6Map()) {
            if (map == null) {
                pw.println("No IPv6 upstream");
                return;
            }
            if (map.isEmpty()) {
                pw.println("No IPv6 upstream rules");
                return;
            }
            map.forEach((k, v) -> pw.println(ipv6UpstreamRuleToString(k, v)));
        } catch (ErrnoException | IOException e) {
            pw.println("Error dumping IPv6 upstream map: " + e);
        }
    }

    private String ipv6DownstreamRuleToString(TetherDownstream6Key key, Tether6Value value) {
        final String neigh6;
        try {
            neigh6 = InetAddress.getByAddress(key.neigh6).getHostAddress();
        } catch (UnknownHostException impossible) {
            throw new AssertionError("IP address array not valid IPv6 address!");
        }
        return String.format("%d(%s) [%s] %s -> %d(%s) %04x [%s] [%s]",
                key.iif, getIfName(key.iif), key.dstMac, neigh6, value.oif, getIfName(value.oif),
                value.ethProto, value.ethSrcMac, value.ethDstMac);
    }

    private void dumpIpv6DownstreamRules(IndentingPrintWriter pw) {
        try (IBpfMap<TetherDownstream6Key, Tether6Value> map = mDeps.getBpfDownstream6Map()) {
            if (map == null) {
                pw.println("No IPv6 downstream");
                return;
            }
            if (map.isEmpty()) {
                pw.println("No IPv6 downstream rules");
                return;
            }
            map.forEach((k, v) -> pw.println(ipv6DownstreamRuleToString(k, v)));
        } catch (ErrnoException | IOException e) {
            pw.println("Error dumping IPv6 downstream map: " + e);
        }
    }

    // TODO: use dump utils with headerline and lambda which prints key and value to reduce
    // duplicate bpf map dump code.
    private void dumpBpfForwardingRulesIpv6(IndentingPrintWriter pw) {
        pw.println("IPv6 Upstream: iif(iface) [inDstMac] [sourcePrefix] -> oif(iface) etherType "
                + "[outSrcMac] [outDstMac]");
        pw.increaseIndent();
        dumpIpv6UpstreamRules(pw);
        pw.decreaseIndent();

        pw.println("IPv6 Downstream: iif(iface) [inDstMac] neigh6 -> oif(iface) etherType "
                + "[outSrcMac] [outDstMac]");
        pw.increaseIndent();
        dumpIpv6DownstreamRules(pw);
        pw.decreaseIndent();
    }

    private <K extends Struct, V extends Struct> void dumpRawMap(IBpfMap<K, V> map,
            IndentingPrintWriter pw) throws ErrnoException {
        if (map == null) {
            pw.println("No BPF support");
            return;
        }
        if (map.isEmpty()) {
            pw.println("No entries");
            return;
        }
        map.forEach((k, v) -> pw.println(BpfDump.toBase64EncodedString(k, v)));
    }

    /**
     * Dump raw BPF map into the base64 encoded strings "<base64 key>,<base64 value>".
     * Allow to dump only one map path once. For test only.
     *
     * Usage:
     * $ dumpsys tethering bpfRawMap --<map name>
     *
     * Output:
     * <base64 encoded key #1>,<base64 encoded value #1>
     * <base64 encoded key #2>,<base64 encoded value #2>
     * ..
     */
    public void dumpRawMap(@NonNull IndentingPrintWriter pw, @Nullable String[] args) {
        // TODO: consider checking the arg order that <map name> is after "bpfRawMap". Probably
        // it is okay for now because this is used by test only and test is supposed to use
        // expected argument order.
        // TODO: dump downstream4 map.
        if (CollectionUtils.contains(args, DUMPSYS_RAWMAP_ARG_STATS)) {
            try (IBpfMap<TetherStatsKey, TetherStatsValue> statsMap = mDeps.getBpfStatsMap()) {
                dumpRawMap(statsMap, pw);
            } catch (ErrnoException | IOException e) {
                pw.println("Error dumping stats map: " + e);
            }
            return;
        }
        if (CollectionUtils.contains(args, DUMPSYS_RAWMAP_ARG_UPSTREAM4)) {
            try (IBpfMap<Tether4Key, Tether4Value> upstreamMap = mDeps.getBpfUpstream4Map()) {
                dumpRawMap(upstreamMap, pw);
            } catch (ErrnoException | IOException e) {
                pw.println("Error dumping IPv4 map: " + e);
            }
            return;
        }
    }

    private String l4protoToString(int proto) {
        if (proto == OsConstants.IPPROTO_TCP) {
            return "tcp";
        } else if (proto == OsConstants.IPPROTO_UDP) {
            return "udp";
        }
        return String.format("unknown(%d)", proto);
    }

    private String ipv4RuleToString(long now, boolean downstream,
            Tether4Key key, Tether4Value value) {
        final String src4, public4, dst4;
        final int publicPort;
        try {
            src4 = InetAddress.getByAddress(key.src4).getHostAddress();
            if (downstream) {
                public4 = InetAddress.getByAddress(key.dst4).getHostAddress();
                publicPort = key.dstPort;
            } else {
                public4 = InetAddress.getByAddress(value.src46).getHostAddress();
                publicPort = value.srcPort;
            }
            dst4 = InetAddress.getByAddress(value.dst46).getHostAddress();
        } catch (UnknownHostException impossible) {
            throw new AssertionError("IP address array not valid IPv4 address!");
        }

        final String ageStr = (value.lastUsed == 0) ? "-"
                : String.format("%dms", (now - value.lastUsed) / 1_000_000);
        return String.format("%s [%s] %d(%s) %s:%d -> %d(%s) %s:%d -> %s:%d [%s] %d %s",
                l4protoToString(key.l4proto), key.dstMac, key.iif, getIfName(key.iif),
                src4, key.srcPort, value.oif, getIfName(value.oif),
                public4, publicPort, dst4, value.dstPort, value.ethDstMac, value.pmtu, ageStr);
    }

    private void dumpIpv4ForwardingRuleMap(long now, boolean downstream,
            IBpfMap<Tether4Key, Tether4Value> map, IndentingPrintWriter pw) throws ErrnoException {
        if (map == null) {
            pw.println("No IPv4 support");
            return;
        }
        if (map.isEmpty()) {
            pw.println("No rules");
            return;
        }
        map.forEach((k, v) -> pw.println(ipv4RuleToString(now, downstream, k, v)));
    }

    private void dumpBpfForwardingRulesIpv4(IndentingPrintWriter pw) {
        final long now = SystemClock.elapsedRealtimeNanos();

        try (IBpfMap<Tether4Key, Tether4Value> upstreamMap = mDeps.getBpfUpstream4Map();
                IBpfMap<Tether4Key, Tether4Value> downstreamMap = mDeps.getBpfDownstream4Map()) {
            pw.println("IPv4 Upstream: proto [inDstMac] iif(iface) src -> nat -> "
                    + "dst [outDstMac] pmtu age");
            pw.increaseIndent();
            dumpIpv4ForwardingRuleMap(now, UPSTREAM, upstreamMap, pw);
            pw.decreaseIndent();

            pw.println("IPv4 Downstream: proto [inDstMac] iif(iface) src -> nat -> "
                    + "dst [outDstMac] pmtu age");
            pw.increaseIndent();
            dumpIpv4ForwardingRuleMap(now, DOWNSTREAM, downstreamMap, pw);
            pw.decreaseIndent();
        } catch (ErrnoException | IOException e) {
            pw.println("Error dumping IPv4 map: " + e);
        }
    }

    private void dumpCounters(@NonNull IndentingPrintWriter pw) {
        try (IBpfMap<S32, S32> map = mDeps.getBpfErrorMap()) {
            if (map == null) {
                pw.println("No error counter support");
                return;
            }
            if (map.isEmpty()) {
                pw.println("<empty>");
                return;
            }
            map.forEach((k, v) -> {
                String counterName;
                try {
                    counterName = sBpfCounterNames[k.val];
                } catch (IndexOutOfBoundsException e) {
                    // Should never happen because this code gets the counter name from the same
                    // include file as the BPF program that increments the counter.
                    Log.wtf(TAG, "Unknown tethering counter type " + k.val);
                    counterName = Integer.toString(k.val);
                }
                if (v.val > 0) pw.println(String.format("%s: %d", counterName, v.val));
            });
        } catch (ErrnoException | IOException e) {
            pw.println("Error dumping error counter map: " + e);
        }
    }

    private void dumpDevmap(@NonNull IndentingPrintWriter pw) {
        try (IBpfMap<TetherDevKey, TetherDevValue> map = mDeps.getBpfDevMap()) {
            if (map == null) {
                pw.println("No devmap support");
                return;
            }
            if (map.isEmpty()) {
                pw.println("<empty>");
                return;
            }
            pw.println("ifindex (iface) -> ifindex (iface)");
            pw.increaseIndent();
            map.forEach((k, v) -> {
                // Only get upstream interface name. Just do the best to make the index readable.
                // TODO: get downstream interface name because the index is either upstream or
                // downstream interface in dev map.
                pw.println(String.format("%d (%s) -> %d (%s)", k.ifIndex, getIfName(k.ifIndex),
                        v.ifIndex, getIfName(v.ifIndex)));
            });
        } catch (ErrnoException | IOException e) {
            pw.println("Error dumping dev map: " + e);
        }
        pw.decreaseIndent();
    }

    /** IPv6 upstream forwarding rule class. */
    public static class Ipv6UpstreamRule {
        // The upstream6 rules are built as the following tables. Only raw ip upstream interface is
        // supported.
        // TODO: support ether ip upstream interface.
        //
        // Tethering network topology:
        //
        //         public network (rawip)                 private network
        //                   |                 UE                |
        // +------------+    V    +------------+------------+    V    +------------+
        // |   Sever    +---------+  Upstream  | Downstream +---------+   Client   |
        // +------------+         +------------+------------+         +------------+
        //
        // upstream6 key and value:
        //
        // +------+-------------------+
        // | TetherUpstream6Key       |
        // +------+------+------+-----+
        // |field |iif   |dstMac|src64|
        // |      |      |      |     |
        // +------+------+------+-----+
        // |value |downst|downst|upstr|
        // |      |ream  |ream  |eam  |
        // +------+------+------+-----+
        //
        // +------+----------------------------------+
        // |      |Tether6Value                      |
        // +------+------+------+------+------+------+
        // |field |oif   |ethDst|ethSrc|ethPro|pmtu  |
        // |      |      |mac   |mac   |to    |      |
        // +------+------+------+------+------+------+
        // |value |upstre|--    |--    |ETH_P_|1500  |
        // |      |am    |      |      |IP    |      |
        // +------+------+------+------+------+------+
        //
        public final int upstreamIfindex;
        public final int downstreamIfindex;
        @NonNull
        public final IpPrefix sourcePrefix;
        @NonNull
        public final MacAddress inDstMac;
        @NonNull
        public final MacAddress outSrcMac;
        @NonNull
        public final MacAddress outDstMac;

        public Ipv6UpstreamRule(int upstreamIfindex, int downstreamIfindex,
                @NonNull IpPrefix sourcePrefix, @NonNull MacAddress inDstMac,
                @NonNull MacAddress outSrcMac, @NonNull MacAddress outDstMac) {
            this.upstreamIfindex = upstreamIfindex;
            this.downstreamIfindex = downstreamIfindex;
            this.sourcePrefix = sourcePrefix;
            this.inDstMac = inDstMac;
            this.outSrcMac = outSrcMac;
            this.outDstMac = outDstMac;
        }

        /**
         * Return a TetherUpstream6Key object built from the rule.
         */
        @NonNull
        public TetherUpstream6Key makeTetherUpstream6Key() {
            final byte[] prefix64 = Arrays.copyOf(sourcePrefix.getRawAddress(), 8);
            return new TetherUpstream6Key(downstreamIfindex, inDstMac, prefix64);
        }

        /**
         * Return a Tether6Value object built from the rule.
         */
        @NonNull
        public Tether6Value makeTether6Value() {
            return new Tether6Value(upstreamIfindex, outDstMac, outSrcMac, ETH_P_IPV6,
                    NetworkStackConstants.ETHER_MTU);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Ipv6UpstreamRule)) return false;
            Ipv6UpstreamRule that = (Ipv6UpstreamRule) o;
            return this.upstreamIfindex == that.upstreamIfindex
                    && this.downstreamIfindex == that.downstreamIfindex
                    && Objects.equals(this.sourcePrefix, that.sourcePrefix)
                    && Objects.equals(this.inDstMac, that.inDstMac)
                    && Objects.equals(this.outSrcMac, that.outSrcMac)
                    && Objects.equals(this.outDstMac, that.outDstMac);
        }

        @Override
        public int hashCode() {
            return 13 * upstreamIfindex + 41 * downstreamIfindex
                    + Objects.hash(sourcePrefix, inDstMac, outSrcMac, outDstMac);
        }

        @Override
        public String toString() {
            return "upstreamIfindex: " + upstreamIfindex
                    + ", downstreamIfindex: " + downstreamIfindex
                    + ", sourcePrefix: " + sourcePrefix
                    + ", inDstMac: " + inDstMac
                    + ", outSrcMac: " + outSrcMac
                    + ", outDstMac: " + outDstMac;
        }
    }

    /** IPv6 downstream forwarding rule class. */
    public static class Ipv6DownstreamRule {
        // The downstream6 rules are built as the following tables. Only raw ip upstream interface
        // is supported.
        // TODO: support ether ip upstream interface.
        //
        // Tethering network topology:
        //
        //         public network (rawip)                 private network
        //                   |                 UE                |
        // +------------+    V    +------------+------------+    V    +------------+
        // |   Sever    +---------+  Upstream  | Downstream +---------+   Client   |
        // +------------+         +------------+------------+         +------------+
        //
        // downstream6 key and value:
        //
        // +------+--------------------+
        // |      |TetherDownstream6Key|
        // +------+------+------+------+
        // |field |iif   |dstMac|neigh6|
        // |      |      |      |      |
        // +------+------+------+------+
        // |value |upstre|--    |client|
        // |      |am    |      |      |
        // +------+------+------+------+
        //
        // +------+----------------------------------+
        // |      |Tether6Value                      |
        // +------+------+------+------+------+------+
        // |field |oif   |ethDst|ethSrc|ethPro|pmtu  |
        // |      |      |mac   |mac   |to    |      |
        // +------+------+------+------+------+------+
        // |value |downst|client|downst|ETH_P_|1500  |
        // |      |ream  |      |ream  |IP    |      |
        // +------+------+------+------+------+------+
        //
        public final int upstreamIfindex;
        public final int downstreamIfindex;

        // TODO: store a ClientInfo object instead of storing address, srcMac, and dstMac directly.
        @NonNull
        public final Inet6Address address;
        @NonNull
        public final MacAddress srcMac;
        @NonNull
        public final MacAddress dstMac;

        public Ipv6DownstreamRule(int upstreamIfindex, int downstreamIfindex,
                @NonNull Inet6Address address, @NonNull MacAddress srcMac,
                @NonNull MacAddress dstMac) {
            this.upstreamIfindex = upstreamIfindex;
            this.downstreamIfindex = downstreamIfindex;
            this.address = address;
            this.srcMac = srcMac;
            this.dstMac = dstMac;
        }

        /** Return a new rule object which updates with new upstream index. */
        @NonNull
        public Ipv6DownstreamRule onNewUpstream(int newUpstreamIfindex) {
            return new Ipv6DownstreamRule(newUpstreamIfindex, downstreamIfindex, address, srcMac,
                    dstMac);
        }

        /**
         * Don't manipulate TetherOffloadRuleParcel directly because implementing onNewUpstream()
         * would be error-prone due to generated stable AIDL classes not having a copy constructor.
         */
        @NonNull
        public TetherOffloadRuleParcel toTetherOffloadRuleParcel() {
            final TetherOffloadRuleParcel parcel = new TetherOffloadRuleParcel();
            parcel.inputInterfaceIndex = upstreamIfindex;
            parcel.outputInterfaceIndex = downstreamIfindex;
            parcel.destination = address.getAddress();
            parcel.prefixLength = 128;
            parcel.srcL2Address = srcMac.toByteArray();
            parcel.dstL2Address = dstMac.toByteArray();
            return parcel;
        }

        /**
         * Return a TetherDownstream6Key object built from the rule.
         */
        @NonNull
        public TetherDownstream6Key makeTetherDownstream6Key() {
            return new TetherDownstream6Key(upstreamIfindex, NULL_MAC_ADDRESS,
                    address.getAddress());
        }

        /**
         * Return a Tether6Value object built from the rule.
         */
        @NonNull
        public Tether6Value makeTether6Value() {
            return new Tether6Value(downstreamIfindex, dstMac, srcMac, ETH_P_IPV6,
                    NetworkStackConstants.ETHER_MTU);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Ipv6DownstreamRule)) return false;
            Ipv6DownstreamRule that = (Ipv6DownstreamRule) o;
            return this.upstreamIfindex == that.upstreamIfindex
                    && this.downstreamIfindex == that.downstreamIfindex
                    && Objects.equals(this.address, that.address)
                    && Objects.equals(this.srcMac, that.srcMac)
                    && Objects.equals(this.dstMac, that.dstMac);
        }

        @Override
        public int hashCode() {
            return 13 * upstreamIfindex + 41 * downstreamIfindex
                    + Objects.hash(address, srcMac, dstMac);
        }

        @Override
        public String toString() {
            return "upstreamIfindex: " + upstreamIfindex
                    + ", downstreamIfindex: " + downstreamIfindex
                    + ", address: " + address.getHostAddress()
                    + ", srcMac: " + srcMac
                    + ", dstMac: " + dstMac;
        }
    }

    /** Tethering client information class. */
    public static class ClientInfo {
        public final int downstreamIfindex;

        @NonNull
        public final MacAddress downstreamMac;
        @NonNull
        public final Inet4Address clientAddress;
        @NonNull
        public final MacAddress clientMac;

        public ClientInfo(int downstreamIfindex,
                @NonNull MacAddress downstreamMac, @NonNull Inet4Address clientAddress,
                @NonNull MacAddress clientMac) {
            this.downstreamIfindex = downstreamIfindex;
            this.downstreamMac = downstreamMac;
            this.clientAddress = clientAddress;
            this.clientMac = clientMac;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ClientInfo)) return false;
            ClientInfo that = (ClientInfo) o;
            return this.downstreamIfindex == that.downstreamIfindex
                    && Objects.equals(this.downstreamMac, that.downstreamMac)
                    && Objects.equals(this.clientAddress, that.clientAddress)
                    && Objects.equals(this.clientMac, that.clientMac);
        }

        @Override
        public int hashCode() {
            return Objects.hash(downstreamIfindex, downstreamMac, clientAddress, clientMac);
        }

        @Override
        public String toString() {
            return String.format("downstream: %d (%s), client: %s (%s)",
                    downstreamIfindex, downstreamMac, clientAddress, clientMac);
        }
    }

    /** Upstream information class. */
    private static final class UpstreamInfo {
        // TODO: add clat interface information
        public final int ifIndex;
        public final int mtu;

        private UpstreamInfo(final int ifIndex, final int mtu) {
            this.ifIndex = ifIndex;
            this.mtu = mtu;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ifIndex, mtu);
        }

        @Override
        public String toString() {
            return String.format("ifIndex: %d, mtu: %d", ifIndex, mtu);
        }
    }

    /**
     * A BPF tethering stats provider to provide network statistics to the system.
     * Note that this class' data may only be accessed on the handler thread.
     */
    @VisibleForTesting
    class BpfTetherStatsProvider extends NetworkStatsProvider {
        // The offloaded traffic statistics per interface that has not been reported since the
        // last call to pushTetherStats. Only the interfaces that were ever tethering upstreams
        // and has pending tether stats delta are included in this NetworkStats object.
        private NetworkStats mIfaceStats = new NetworkStats(0L, 0);

        // The same stats as above, but counts network stats per uid.
        private NetworkStats mUidStats = new NetworkStats(0L, 0);

        @Override
        public void onRequestStatsUpdate(int token) {
            mHandler.post(() -> pushTetherStats());
        }

        @Override
        public void onSetAlert(long quotaBytes) {
            mHandler.post(() -> updateAlertQuota(quotaBytes));
        }

        @Override
        public void onSetLimit(@NonNull String iface, long quotaBytes) {
            if (quotaBytes < QUOTA_UNLIMITED) {
                throw new IllegalArgumentException("invalid quota value " + quotaBytes);
            }

            mHandler.post(() -> {
                final Long curIfaceQuota = mInterfaceQuotas.get(iface);

                if (null == curIfaceQuota && QUOTA_UNLIMITED == quotaBytes) return;

                if (quotaBytes == QUOTA_UNLIMITED) {
                    mInterfaceQuotas.remove(iface);
                } else {
                    mInterfaceQuotas.put(iface, quotaBytes);
                }
                maybeUpdateDataLimit(iface);
            });
        }

        @VisibleForTesting
        void pushTetherStats() {
            try {
                // The token is not used for now. See b/153606961.
                notifyStatsUpdated(0 /* token */, mIfaceStats, mUidStats);

                // Clear the accumulated tether stats delta after reported. Note that create a new
                // empty object because NetworkStats#clear is @hide.
                mIfaceStats = new NetworkStats(0L, 0);
                mUidStats = new NetworkStats(0L, 0);
            } catch (RuntimeException e) {
                mLog.e("Cannot report network stats: ", e);
            }
        }

        private void accumulateDiff(@NonNull NetworkStats ifaceDiff,
                @NonNull NetworkStats uidDiff) {
            mIfaceStats = mIfaceStats.add(ifaceDiff);
            mUidStats = mUidStats.add(uidDiff);
        }
    }

    @Nullable
    private ClientInfo getClientInfo(@NonNull Inet4Address clientAddress) {
        for (HashMap<Inet4Address, ClientInfo> clients : mTetherClients.values()) {
            for (ClientInfo client : clients.values()) {
                if (clientAddress.equals(client.clientAddress)) {
                    return client;
                }
            }
        }
        return null;
    }

    @NonNull
    @VisibleForTesting
    static byte[] toIpv4MappedAddressBytes(Inet4Address ia4) {
        final byte[] addr4 = ia4.getAddress();
        final byte[] addr6 = new byte[16];
        addr6[10] = (byte) 0xff;
        addr6[11] = (byte) 0xff;
        addr6[12] = addr4[0];
        addr6[13] = addr4[1];
        addr6[14] = addr4[2];
        addr6[15] = addr4[3];
        return addr6;
    }

    // TODO: parse CTA_PROTOINFO of conntrack event in ConntrackMonitor. For TCP, only add rules
    // while TCP status is established.
    @VisibleForTesting
    class BpfConntrackEventConsumer implements ConntrackEventConsumer {
        // The upstream4 and downstream4 rules are built as the following tables. Only raw ip
        // upstream interface is supported. Note that the field "lastUsed" is only updated by
        // BPF program which records the last used time for a given rule.
        // TODO: support ether ip upstream interface.
        //
        // NAT network topology:
        //
        //         public network (rawip)                 private network
        //                   |                 UE                |
        // +------------+    V    +------------+------------+    V    +------------+
        // |   Sever    +---------+  Upstream  | Downstream +---------+   Client   |
        // +------------+         +------------+------------+         +------------+
        //
        // upstream4 key and value:
        //
        // +------+------------------------------------------------+
        // |      |      TetherUpstream4Key                        |
        // +------+------+------+------+------+------+------+------+
        // |field |iif   |dstMac|l4prot|src4  |dst4  |srcPor|dstPor|
        // |      |      |      |o     |      |      |t     |t     |
        // +------+------+------+------+------+------+------+------+
        // |value |downst|downst|tcp/  |client|server|client|server|
        // |      |ream  |ream  |udp   |      |      |      |      |
        // +------+------+------+------+------+------+------+------+
        //
        // +------+---------------------------------------------------------------------+
        // |      |      TetherUpstream4Value                                           |
        // +------+------+------+------+------+------+------+------+------+------+------+
        // |field |oif   |ethDst|ethSrc|ethPro|pmtu  |src46 |dst46 |srcPor|dstPor|lastUs|
        // |      |      |mac   |mac   |to    |      |      |      |t     |t     |ed    |
        // +------+------+------+------+------+------+------+------+------+------+------+
        // |value |upstre|--    |--    |ETH_P_|1500  |upstre|server|upstre|server|--    |
        // |      |am    |      |      |IP    |      |am    |      |am    |      |      |
        // +------+------+------+------+------+------+------+------+------+------+------+
        //
        // downstream4 key and value:
        //
        // +------+------------------------------------------------+
        // |      |      TetherDownstream4Key                      |
        // +------+------+------+------+------+------+------+------+
        // |field |iif   |dstMac|l4prot|src4  |dst4  |srcPor|dstPor|
        // |      |      |      |o     |      |      |t     |t     |
        // +------+------+------+------+------+------+------+------+
        // |value |upstre|--    |tcp/  |server|upstre|server|upstre|
        // |      |am    |      |udp   |      |am    |      |am    |
        // +------+------+------+------+------+------+------+------+
        //
        // +------+---------------------------------------------------------------------+
        // |      |      TetherDownstream4Value                                         |
        // +------+------+------+------+------+------+------+------+------+------+------+
        // |field |oif   |ethDst|ethSrc|ethPro|pmtu  |src46 |dst46 |srcPor|dstPor|lastUs|
        // |      |      |mac   |mac   |to    |      |      |      |t     |t     |ed    |
        // +------+------+------+------+------+------+------+------+------+------+------+
        // |value |downst|client|downst|ETH_P_|1500  |server|client|server|client|--    |
        // |      |ream  |      |ream  |IP    |      |      |      |      |      |      |
        // +------+------+------+------+------+------+------+------+------+------+------+
        //
        @NonNull
        private Tether4Key makeTetherUpstream4Key(
                @NonNull ConntrackEvent e, @NonNull ClientInfo c) {
            return new Tether4Key(c.downstreamIfindex, c.downstreamMac,
                    e.tupleOrig.protoNum, e.tupleOrig.srcIp.getAddress(),
                    e.tupleOrig.dstIp.getAddress(), e.tupleOrig.srcPort, e.tupleOrig.dstPort);
        }

        @NonNull
        private Tether4Key makeTetherDownstream4Key(
                @NonNull ConntrackEvent e, @NonNull ClientInfo c, int upstreamIndex) {
            return new Tether4Key(upstreamIndex, NULL_MAC_ADDRESS /* dstMac (rawip) */,
                    e.tupleReply.protoNum, e.tupleReply.srcIp.getAddress(),
                    e.tupleReply.dstIp.getAddress(), e.tupleReply.srcPort, e.tupleReply.dstPort);
        }

        @NonNull
        private Tether4Value makeTetherUpstream4Value(@NonNull ConntrackEvent e,
                @NonNull UpstreamInfo upstreamInfo) {
            return new Tether4Value(upstreamInfo.ifIndex,
                    NULL_MAC_ADDRESS /* ethDstMac (rawip) */,
                    NULL_MAC_ADDRESS /* ethSrcMac (rawip) */, ETH_P_IP,
                    upstreamInfo.mtu, toIpv4MappedAddressBytes(e.tupleReply.dstIp),
                    toIpv4MappedAddressBytes(e.tupleReply.srcIp), e.tupleReply.dstPort,
                    e.tupleReply.srcPort, 0 /* lastUsed, filled by bpf prog only */);
        }

        @NonNull
        private Tether4Value makeTetherDownstream4Value(@NonNull ConntrackEvent e,
                @NonNull ClientInfo c, @NonNull UpstreamInfo upstreamInfo) {
            return new Tether4Value(c.downstreamIfindex,
                    c.clientMac, c.downstreamMac, ETH_P_IP, upstreamInfo.mtu,
                    toIpv4MappedAddressBytes(e.tupleOrig.dstIp),
                    toIpv4MappedAddressBytes(e.tupleOrig.srcIp),
                    e.tupleOrig.dstPort, e.tupleOrig.srcPort,
                    0 /* lastUsed, filled by bpf prog only */);
        }

        private boolean allowOffload(ConntrackEvent e) {
            if (e.tupleOrig.protoNum != OsConstants.IPPROTO_TCP) return true;
            return !CollectionUtils.contains(
                    NON_OFFLOADED_UPSTREAM_IPV4_TCP_PORTS, e.tupleOrig.dstPort);
        }

        public void accept(ConntrackEvent e) {
            if (!allowOffload(e)) return;

            final ClientInfo tetherClient = getClientInfo(e.tupleOrig.srcIp);
            if (tetherClient == null) return;

            final Integer upstreamIndex = mIpv4UpstreamIndices.get(e.tupleReply.dstIp);
            if (upstreamIndex == null) return;

            final Tether4Key upstream4Key = makeTetherUpstream4Key(e, tetherClient);
            final Tether4Key downstream4Key = makeTetherDownstream4Key(e, tetherClient,
                    upstreamIndex);

            if (e.msgType == (NetlinkConstants.NFNL_SUBSYS_CTNETLINK << 8
                    | NetlinkConstants.IPCTNL_MSG_CT_DELETE)) {
                final boolean deletedUpstream = mBpfCoordinatorShim.tetherOffloadRuleRemove(
                        UPSTREAM, upstream4Key);
                final boolean deletedDownstream = mBpfCoordinatorShim.tetherOffloadRuleRemove(
                        DOWNSTREAM, downstream4Key);

                if (!deletedUpstream && !deletedDownstream) {
                    // The rules may have been already removed by losing client or losing upstream.
                    return;
                }

                if (deletedUpstream != deletedDownstream) {
                    Log.wtf(TAG, "The bidirectional rules should be removed concurrently ("
                            + "upstream: " + deletedUpstream
                            + ", downstream: " + deletedDownstream + ")");
                    return;
                }

                maybeClearLimit(upstreamIndex);
                return;
            }

            if (mIpv4UpstreamInfo == null || mIpv4UpstreamInfo.ifIndex != upstreamIndex) return;

            final Tether4Value upstream4Value = makeTetherUpstream4Value(e, mIpv4UpstreamInfo);
            final Tether4Value downstream4Value = makeTetherDownstream4Value(e, tetherClient,
                    mIpv4UpstreamInfo);

            maybeAddDevMap(upstreamIndex, tetherClient.downstreamIfindex);
            maybeSetLimit(upstreamIndex);
            mBpfCoordinatorShim.tetherOffloadRuleAdd(UPSTREAM, upstream4Key, upstream4Value);
            mBpfCoordinatorShim.tetherOffloadRuleAdd(DOWNSTREAM, downstream4Key, downstream4Value);
        }
    }

    private boolean isBpfEnabled() {
        final TetheringConfiguration config = mDeps.getTetherConfig();
        return (config != null) ? config.isBpfOffloadEnabled() : true /* default value */;
    }

    private int getInterfaceIndexFromRules(@NonNull String ifName) {
        for (ArraySet<Ipv6UpstreamRule> rules : mIpv6UpstreamRules.values()) {
            for (Ipv6UpstreamRule rule : rules) {
                final int upstreamIfindex = rule.upstreamIfindex;
                if (TextUtils.equals(ifName, mInterfaceNames.get(upstreamIfindex))) {
                    return upstreamIfindex;
                }
            }
        }
        return 0;
    }

    private long getQuotaBytes(@NonNull String iface) {
        final Long limit = mInterfaceQuotas.get(iface);
        final long quotaBytes = (limit != null) ? limit : QUOTA_UNLIMITED;

        return quotaBytes;
    }

    private boolean sendDataLimitToBpfMap(int ifIndex, long quotaBytes) {
        if (!isUsingBpf()) return false;
        if (ifIndex == 0) {
            Log.wtf(TAG, "Invalid interface index.");
            return false;
        }

        return mBpfCoordinatorShim.tetherOffloadSetInterfaceQuota(ifIndex, quotaBytes);
    }

    // Handle the data limit update from the service which is the stats provider registered for.
    private void maybeUpdateDataLimit(@NonNull String iface) {
        // Set data limit only on a given upstream which has at least one rule. If we can't get
        // an interface index for a given interface name, it means either there is no rule for
        // a given upstream or the interface name is not an upstream which is monitored by the
        // coordinator.
        final int ifIndex = getInterfaceIndexFromRules(iface);
        if (ifIndex == 0) return;

        final long quotaBytes = getQuotaBytes(iface);
        sendDataLimitToBpfMap(ifIndex, quotaBytes);
    }

    // Handle the data limit update while adding forwarding rules.
    private boolean updateDataLimit(int ifIndex) {
        final String iface = mInterfaceNames.get(ifIndex);
        if (iface == null) {
            mLog.e("Fail to get the interface name for index " + ifIndex);
            return false;
        }
        final long quotaBytes = getQuotaBytes(iface);
        return sendDataLimitToBpfMap(ifIndex, quotaBytes);
    }

    private void maybeSetLimit(int upstreamIfindex) {
        if (isAnyRuleOnUpstream(upstreamIfindex)
                || mBpfCoordinatorShim.isAnyIpv4RuleOnUpstream(upstreamIfindex)) {
            return;
        }

        // If failed to set a data limit, probably should not use this upstream, because
        // the upstream may not want to blow through the data limit that was told to apply.
        // TODO: Perhaps stop the coordinator.
        boolean success = updateDataLimit(upstreamIfindex);
        if (!success) {
            mLog.e("Setting data limit for " + getIfName(upstreamIfindex) + " failed.");
        }
    }

    // TODO: This should be also called while IpServer wants to clear all IPv4 rules. Relying on
    // conntrack event can't cover this case.
    private void maybeClearLimit(int upstreamIfindex) {
        if (isAnyRuleOnUpstream(upstreamIfindex)
                || mBpfCoordinatorShim.isAnyIpv4RuleOnUpstream(upstreamIfindex)) {
            return;
        }

        final TetherStatsValue statsValue =
                mBpfCoordinatorShim.tetherOffloadGetAndClearStats(upstreamIfindex);
        if (statsValue == null) {
            Log.wtf(TAG, "Fail to cleanup tether stats for upstream index " + upstreamIfindex);
            return;
        }

        SparseArray<TetherStatsValue> tetherStatsList = new SparseArray<TetherStatsValue>();
        tetherStatsList.put(upstreamIfindex, statsValue);

        // Update the last stats delta and delete the local cache for a given upstream.
        updateQuotaAndStatsFromSnapshot(tetherStatsList);
        mStats.remove(upstreamIfindex);
    }

    // TODO: Rename to isAnyIpv6RuleOnUpstream and define an isAnyRuleOnUpstream method that called
    // both isAnyIpv6RuleOnUpstream and mBpfCoordinatorShim.isAnyIpv4RuleOnUpstream.
    private boolean isAnyRuleOnUpstream(int upstreamIfindex) {
        for (ArraySet<Ipv6UpstreamRule> rules : mIpv6UpstreamRules.values()) {
            for (Ipv6UpstreamRule rule : rules) {
                if (upstreamIfindex == rule.upstreamIfindex) return true;
            }
        }
        return false;
    }

    // TODO: remove the index from map while the interface has been removed because the map size
    // is 64 entries. See packages\modules\Connectivity\Tethering\bpf_progs\offload.c.
    private void maybeAddDevMap(int upstreamIfindex, int downstreamIfindex) {
        for (Integer index : new Integer[] {upstreamIfindex, downstreamIfindex}) {
            if (mDeviceMapSet.contains(index)) continue;
            if (mBpfCoordinatorShim.addDevMap(index)) mDeviceMapSet.add(index);
        }
    }

    private void forwardingPairAdd(@NonNull String intIface, @NonNull String extIface) {
        if (!mForwardingPairs.containsKey(extIface)) {
            mForwardingPairs.put(extIface, new HashSet<String>());
        }
        mForwardingPairs.get(extIface).add(intIface);
    }

    private void forwardingPairRemove(@NonNull String intIface, @NonNull String extIface) {
        HashSet<String> downstreams = mForwardingPairs.get(extIface);
        if (downstreams == null) return;
        if (!downstreams.remove(intIface)) return;

        if (downstreams.isEmpty()) {
            mForwardingPairs.remove(extIface);
        }
    }

    private boolean forwardingPairExists(@NonNull String intIface, @NonNull String extIface) {
        if (!mForwardingPairs.containsKey(extIface)) return false;

        return mForwardingPairs.get(extIface).contains(intIface);
    }

    private boolean isAnyForwardingPairOnUpstream(@NonNull String extIface) {
        return mForwardingPairs.containsKey(extIface);
    }

    private boolean isAnyForwardingPairOnDownstream(@NonNull String intIface) {
        for (final HashSet downstreams : mForwardingPairs.values()) {
            if (downstreams.contains(intIface)) return true;
        }
        return false;
    }

    @NonNull
    private NetworkStats buildNetworkStats(@NonNull StatsType type, int ifIndex,
            @NonNull final ForwardedStats diff) {
        NetworkStats stats = new NetworkStats(0L, 0);
        final String iface = mInterfaceNames.get(ifIndex);
        if (iface == null) {
            // TODO: Use Log.wtf once the coordinator owns full control of tether stats from netd.
            // For now, netd may add the empty stats for the upstream which is not monitored by
            // the coordinator. Silently ignore it.
            return stats;
        }
        final int uid = (type == StatsType.STATS_PER_UID) ? UID_TETHERING : UID_ALL;
        // Note that the argument 'metered', 'roaming' and 'defaultNetwork' are not recorded for
        // network stats snapshot. See NetworkStatsRecorder#recordSnapshotLocked.
        return stats.addEntry(new Entry(iface, uid, SET_DEFAULT, TAG_NONE, METERED_NO,
                ROAMING_NO, DEFAULT_NETWORK_NO, diff.rxBytes, diff.rxPackets,
                diff.txBytes, diff.txPackets, 0L /* operations */));
    }

    private void updateAlertQuota(long newQuota) {
        if (newQuota < QUOTA_UNLIMITED) {
            throw new IllegalArgumentException("invalid quota value " + newQuota);
        }
        if (mRemainingAlertQuota == newQuota) return;

        mRemainingAlertQuota = newQuota;
        if (mRemainingAlertQuota == 0) {
            mLog.i("onAlertReached");
            if (mStatsProvider != null) mStatsProvider.notifyAlertReached();
        }
    }

    private void updateQuotaAndStatsFromSnapshot(
            @NonNull final SparseArray<TetherStatsValue> tetherStatsList) {
        long usedAlertQuota = 0;
        for (int i = 0; i < tetherStatsList.size(); i++) {
            final Integer ifIndex = tetherStatsList.keyAt(i);
            final TetherStatsValue tetherStats = tetherStatsList.valueAt(i);
            final ForwardedStats curr = new ForwardedStats(tetherStats);
            final ForwardedStats base = mStats.get(ifIndex);
            final ForwardedStats diff = (base != null) ? curr.subtract(base) : curr;
            usedAlertQuota += diff.rxBytes + diff.txBytes;

            // Update the local cache for counting tether stats delta.
            mStats.put(ifIndex, curr);

            // Update the accumulated tether stats delta to the stats provider for the service
            // querying.
            if (mStatsProvider != null) {
                try {
                    mStatsProvider.accumulateDiff(
                            buildNetworkStats(StatsType.STATS_PER_IFACE, ifIndex, diff),
                            buildNetworkStats(StatsType.STATS_PER_UID, ifIndex, diff));
                } catch (ArrayIndexOutOfBoundsException e) {
                    Log.wtf(TAG, "Fail to update the accumulated stats delta for interface index "
                            + ifIndex + " : ", e);
                }
            }
        }

        if (mRemainingAlertQuota > 0 && usedAlertQuota > 0) {
            // Trim to zero if overshoot.
            final long newQuota = Math.max(mRemainingAlertQuota - usedAlertQuota, 0);
            updateAlertQuota(newQuota);
        }

        // TODO: Count the used limit quota for notifying data limit reached.
    }

    private void updateForwardedStats() {
        final SparseArray<TetherStatsValue> tetherStatsList =
                mBpfCoordinatorShim.tetherOffloadGetStats();

        if (tetherStatsList == null) {
            mLog.e("Problem fetching tethering stats");
            return;
        }

        updateQuotaAndStatsFromSnapshot(tetherStatsList);
    }

    @VisibleForTesting
    int getPollingInterval() {
        // The valid range of interval is DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS..max_long.
        // Ignore the config value is less than the minimum polling interval. Note that the
        // minimum interval definition is invoked as OffloadController#isPollingStatsNeeded does.
        // TODO: Perhaps define a minimum polling interval constant.
        final TetheringConfiguration config = mDeps.getTetherConfig();
        final int configInterval = (config != null) ? config.getOffloadPollInterval() : 0;
        return Math.max(DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS, configInterval);
    }

    @Nullable
    private Inet4Address parseIPv4Address(byte[] addrBytes) {
        try {
            final InetAddress ia = Inet4Address.getByAddress(addrBytes);
            if (ia instanceof Inet4Address) return (Inet4Address) ia;
        } catch (UnknownHostException e) {
            mLog.e("Failed to parse IPv4 address: " + e);
        }
        return null;
    }

    // Update CTA_TUPLE_ORIG timeout for a given conntrack entry. Note that there will also be
    // coming a conntrack event to notify updated timeout.
    private void updateConntrackTimeout(byte proto, Inet4Address src4, short srcPort,
            Inet4Address dst4, short dstPort) {
        if (src4 == null || dst4 == null) {
            mLog.e("Either source or destination IPv4 address is invalid ("
                    + "proto: " + proto + ", "
                    + "src4: " + src4 + ", "
                    + "srcPort: " + Short.toUnsignedInt(srcPort) + ", "
                    + "dst4: " + dst4 + ", "
                    + "dstPort: " + Short.toUnsignedInt(dstPort) + ")");
            return;
        }

        // TODO: consider acquiring the timeout setting from nf_conntrack_* variables.
        // - proc/sys/net/netfilter/nf_conntrack_tcp_timeout_established
        // - proc/sys/net/netfilter/nf_conntrack_udp_timeout_stream
        // See kernel document nf_conntrack-sysctl.txt.
        final int timeoutSec = (proto == OsConstants.IPPROTO_TCP)
                ? NF_CONNTRACK_TCP_TIMEOUT_ESTABLISHED
                : NF_CONNTRACK_UDP_TIMEOUT_STREAM;
        final byte[] msg = ConntrackMessage.newIPv4TimeoutUpdateRequest(
                proto, src4, (int) srcPort, dst4, (int) dstPort, timeoutSec);
        try {
            NetlinkUtils.sendOneShotKernelMessage(OsConstants.NETLINK_NETFILTER, msg);
        } catch (ErrnoException e) {
            // Lower the log level for the entry not existing. The conntrack entry may have been
            // deleted and not handled by the conntrack event monitor yet. In other words, the
            // rule has not been deleted from the BPF map yet. Deleting a non-existent entry may
            // happen during the conntrack timeout refreshing iteration. Note that ENOENT may be
            // a real error but is hard to distinguish.
            // TODO: Figure out a better way to handle this.
            final String errMsg = "Failed to update conntrack entry ("
                    + "proto: " + proto + ", "
                    + "src4: " + src4 + ", "
                    + "srcPort: " + Short.toUnsignedInt(srcPort) + ", "
                    + "dst4: " + dst4 + ", "
                    + "dstPort: " + Short.toUnsignedInt(dstPort) + "), "
                    + "msg: " + NetlinkConstants.hexify(msg) + ", "
                    + "e: " + e;
            if (OsConstants.ENOENT == e.errno) {
                mLog.w(errMsg);
            } else {
                mLog.e(errMsg);
            }
        }
    }

    private void refreshAllConntrackTimeouts() {
        final long now = mDeps.elapsedRealtimeNanos();

        // TODO: Consider ignoring TCP traffic on upstream and monitor on downstream only
        // because TCP is a bidirectional traffic. Probably don't need to extend timeout by
        // both directions for TCP.
        mBpfCoordinatorShim.tetherOffloadRuleForEach(UPSTREAM, (k, v) -> {
            if ((now - v.lastUsed) / 1_000_000 < CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS) {
                updateConntrackTimeout((byte) k.l4proto,
                        parseIPv4Address(k.src4), (short) k.srcPort,
                        parseIPv4Address(k.dst4), (short) k.dstPort);
            }
        });

        // Reverse the source and destination {address, port} from downstream value because
        // #updateConntrackTimeout refresh the timeout of netlink attribute CTA_TUPLE_ORIG
        // which is opposite direction for downstream map value.
        mBpfCoordinatorShim.tetherOffloadRuleForEach(DOWNSTREAM, (k, v) -> {
            if ((now - v.lastUsed) / 1_000_000 < CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS) {
                updateConntrackTimeout((byte) k.l4proto,
                        parseIPv4Address(v.dst46), (short) v.dstPort,
                        parseIPv4Address(v.src46), (short) v.srcPort);
            }
        });
    }

    private void maybeSchedulePollingStats() {
        if (!mPollingStarted) return;

        if (mHandler.hasCallbacks(mScheduledPollingStats)) {
            mHandler.removeCallbacks(mScheduledPollingStats);
        }

        mHandler.postDelayed(mScheduledPollingStats, getPollingInterval());
    }

    private void maybeScheduleConntrackTimeoutUpdate() {
        if (!mPollingStarted) return;

        if (mHandler.hasCallbacks(mScheduledConntrackTimeoutUpdate)) {
            mHandler.removeCallbacks(mScheduledConntrackTimeoutUpdate);
        }

        mHandler.postDelayed(mScheduledConntrackTimeoutUpdate,
                CONNTRACK_TIMEOUT_UPDATE_INTERVAL_MS);
    }

    // Return IPv6 downstream forwarding rule map. This is used for testing only.
    // Note that this can be only called on handler thread.
    @NonNull
    @VisibleForTesting
    final HashMap<IpServer, LinkedHashMap<Inet6Address, Ipv6DownstreamRule>>
            getIpv6DownstreamRulesForTesting() {
        return mIpv6DownstreamRules;
    }

    // Return upstream interface name map. This is used for testing only.
    // Note that this can be only called on handler thread.
    @NonNull
    @VisibleForTesting
    final SparseArray<String> getInterfaceNamesForTesting() {
        return mInterfaceNames;
    }

    // Return BPF conntrack event consumer. This is used for testing only.
    // Note that this can be only called on handler thread.
    @NonNull
    @VisibleForTesting
    final BpfConntrackEventConsumer getBpfConntrackEventConsumerForTesting() {
        return mBpfConntrackEventConsumer;
    }

    // Return tethering client information. This is used for testing only.
    @NonNull
    @VisibleForTesting
    final HashMap<IpServer, HashMap<Inet4Address, ClientInfo>>
            getTetherClientsForTesting() {
        return mTetherClients;
    }

    // Return map of upstream interface IPv4 address to interface index.
    // This is used for testing only.
    @NonNull
    @VisibleForTesting
    final HashMap<Inet4Address, Integer> getIpv4UpstreamIndicesForTesting() {
        return mIpv4UpstreamIndices;
    }

    private static native String[] getBpfCounterNames();
}
