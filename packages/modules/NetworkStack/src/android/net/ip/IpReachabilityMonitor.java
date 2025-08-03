/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.net.metrics.IpReachabilityEvent.NUD_FAILED;
import static android.net.metrics.IpReachabilityEvent.NUD_FAILED_ORGANIC;
import static android.net.metrics.IpReachabilityEvent.PROVISIONING_LOST;
import static android.net.metrics.IpReachabilityEvent.PROVISIONING_LOST_ORGANIC;

import static com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_IGNORE_INCOMPLETE_IPV6_DEFAULT_ROUTER_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_IGNORE_INCOMPLETE_IPV6_DNS_SERVER_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_MCAST_RESOLICIT_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.metrics.IpConnectivityLog;
import android.net.metrics.IpReachabilityEvent;
import android.net.networkstack.aidl.ip.ReachabilityLossReason;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.stats.connectivity.IpType;
import android.stats.connectivity.NudEventType;
import android.stats.connectivity.NudNeighborType;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.IpNeighborMonitor;
import com.android.net.module.util.ip.IpNeighborMonitor.NeighborEvent;
import com.android.net.module.util.ip.IpNeighborMonitor.NeighborEventConsumer;
import com.android.net.module.util.netlink.StructNdMsg;
import com.android.networkstack.R;
import com.android.networkstack.metrics.IpReachabilityMonitorMetrics;

import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * IpReachabilityMonitor.
 *
 * Monitors on-link IP reachability and notifies callers whenever any on-link
 * addresses of interest appear to have become unresponsive.
 *
 * This code does not concern itself with "why" a neighbour might have become
 * unreachable. Instead, it primarily reacts to the kernel's notion of IP
 * reachability for each of the neighbours we know to be critically important
 * to normal network connectivity. As such, it is often "just the messenger":
 * the neighbours about which it warns are already deemed by the kernel to have
 * become unreachable.
 *
 *
 * How it works:
 *
 *   1. The "on-link neighbours of interest" found in a given LinkProperties
 *      instance are added to a "watch list" via #updateLinkProperties().
 *      This usually means all default gateways and any on-link DNS servers.
 *
 *   2. We listen continuously for netlink neighbour messages (RTM_NEWNEIGH,
 *      RTM_DELNEIGH), watching only for neighbours in the watch list.
 *
 *        - A neighbour going into NUD_REACHABLE, NUD_STALE, NUD_DELAY, and
 *          even NUD_PROBE is perfectly normal; we merely record the new state.
 *
 *        - A neighbour's entry may be deleted (RTM_DELNEIGH), for example due
 *          to garbage collection.  This is not necessarily of immediate
 *          concern; we record the neighbour as moving to NUD_NONE.
 *
 *        - A neighbour transitioning to NUD_FAILED (for any reason) is
 *          critically important and is handled as described below in #4.
 *
 *   3. All on-link neighbours in the watch list can be forcibly "probed" by
 *      calling #probeAll(). This should be called whenever it is important to
 *      verify that critical neighbours on the link are still reachable, e.g.
 *      when roaming between BSSIDs.
 *
 *        - The kernel will send unicast ARP requests for IPv4 neighbours and
 *          unicast NS packets for IPv6 neighbours.  The expected replies will
 *          likely be unicast.
 *
 *        - The forced probing is done holding a wakelock. The kernel may,
 *          however, initiate probing of a neighbor on its own, i.e. whenever
 *          a neighbour has expired from NUD_DELAY.
 *
 *        - The kernel sends:
 *
 *              /proc/sys/net/ipv{4,6}/neigh/<ifname>/ucast_solicit
 *
 *          number of probes (usually 3) every:
 *
 *              /proc/sys/net/ipv{4,6}/neigh/<ifname>/retrans_time_ms
 *
 *          number of milliseconds (usually 1000ms). This normally results in
 *          3 unicast packets, 1 per second.
 *
 *        - If no response is received to any of the probe packets, the kernel
 *          marks the neighbour as being in state NUD_FAILED, and the listening
 *          process in #2 will learn of it.
 *
 *   4. We call the supplied Callback#notifyLost() function if the loss of a
 *      neighbour in NUD_FAILED would cause IPv4 or IPv6 configuration to
 *      become incomplete (a loss of provisioning).
 *
 *        - For example, losing all our IPv4 on-link DNS servers (or losing
 *          our only IPv6 default gateway) constitutes a loss of IPv4 (IPv6)
 *          provisioning; Callback#notifyLost() would be called.
 *
 *        - Since it can be non-trivial to reacquire certain IP provisioning
 *          state it may be best for the link to disconnect completely and
 *          reconnect afresh.
 *
 * Accessing an instance of this class from multiple threads is NOT safe.
 *
 * @hide
 */
public class IpReachabilityMonitor {
    private static final String TAG = "IpReachabilityMonitor";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    // Upper and lower bound for NUD probe parameters.
    protected static final int MAX_NUD_SOLICIT_NUM = 15;
    protected static final int MIN_NUD_SOLICIT_NUM = 5;
    protected static final int MAX_NUD_SOLICIT_INTERVAL_MS = 1000;
    protected static final int MIN_NUD_SOLICIT_INTERVAL_MS = 750;
    protected static final int NUD_MCAST_RESOLICIT_NUM = 3;
    private static final int INVALID_NUD_MCAST_RESOLICIT_NUM = -1;

    private static final int INVALID_LEGACY_NUD_FAILURE_TYPE = -1;
    public static final int INVALID_REACHABILITY_LOSS_TYPE = -1;

    public interface Callback {
        /**
         * This callback function must execute as quickly as possible as it is
         * run on the same thread that listens to kernel neighbor updates.
         *
         * TODO: refactor to something like notifyProvisioningLost(String msg).
         */
        void notifyLost(InetAddress ip, String logMsg, NudEventType type);
    }

    /**
     * Encapsulates IpReachabilityMonitor dependencies on systems that hinder unit testing.
     * TODO: consider also wrapping MultinetworkPolicyTracker in this interface.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface Dependencies {
        void acquireWakeLock(long durationMs);
        IpNeighborMonitor makeIpNeighborMonitor(Handler h, SharedLog log, NeighborEventConsumer cb);
        boolean isFeatureEnabled(Context context, String name);
        boolean isFeatureNotChickenedOut(Context context, String name);
        IpReachabilityMonitorMetrics getIpReachabilityMonitorMetrics();

        static Dependencies makeDefault(Context context, String iface) {
            final String lockName = TAG + "." + iface;
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            final WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);

            return new Dependencies() {
                public void acquireWakeLock(long durationMs) {
                    lock.acquire(durationMs);
                }

                public IpNeighborMonitor makeIpNeighborMonitor(Handler h, SharedLog log,
                        NeighborEventConsumer cb) {
                    return new IpNeighborMonitor(h, log, cb);
                }

                public boolean isFeatureEnabled(final Context context, final String name) {
                    return DeviceConfigUtils.isNetworkStackFeatureEnabled(context, name);
                }

                public boolean isFeatureNotChickenedOut(final Context context, final String name) {
                    return DeviceConfigUtils.isNetworkStackFeatureNotChickenedOut(context, name);
                }

                public IpReachabilityMonitorMetrics getIpReachabilityMonitorMetrics() {
                    return new IpReachabilityMonitorMetrics();
                }
            };
        }
    }

    private final InterfaceParams mInterfaceParams;
    private final IpNeighborMonitor mIpNeighborMonitor;
    private final SharedLog mLog;
    private final Dependencies mDependencies;
    private final boolean mUsingMultinetworkPolicyTracker;
    private final ConnectivityManager mCm;
    private final IpConnectivityLog mMetricsLog;
    private final Context mContext;
    private final INetd mNetd;
    private final IpReachabilityMonitorMetrics mIpReachabilityMetrics;
    private LinkProperties mLinkProperties = new LinkProperties();
    private Map<InetAddress, NeighborEvent> mNeighborWatchList = new HashMap<>();
    // Time in milliseconds of the last forced probe request.
    private volatile long mLastProbeTimeMs;
    // Time in milliseconds of the last forced probe request due to roam or CMD_CONFIRM.
    private long mLastProbeDueToRoamMs;
    private long mLastProbeDueToConfirmMs;
    private int mNumSolicits;
    private int mInterSolicitIntervalMs;
    @NonNull
    private final Callback mCallback;
    private final boolean mMulticastResolicitEnabled;
    private final boolean mIgnoreIncompleteIpv6DnsServerEnabled;
    private final boolean mIgnoreIncompleteIpv6DefaultRouterEnabled;
    private final boolean mMacChangeFailureOnlyAfterRoam;
    private final boolean mIgnoreOrganicNudFailure;

    public IpReachabilityMonitor(
            Context context, InterfaceParams ifParams, Handler h, SharedLog log, Callback callback,
            boolean usingMultinetworkPolicyTracker, Dependencies dependencies, final INetd netd) {
        this(context, ifParams, h, log, callback, usingMultinetworkPolicyTracker, dependencies,
                new IpConnectivityLog(), netd);
    }

    @VisibleForTesting
    public IpReachabilityMonitor(Context context, InterfaceParams ifParams, Handler h,
            SharedLog log, Callback callback, boolean usingMultinetworkPolicyTracker,
            Dependencies dependencies, final IpConnectivityLog metricsLog, final INetd netd) {
        if (ifParams == null) throw new IllegalArgumentException("null InterfaceParams");

        mContext = context;
        mInterfaceParams = ifParams;
        mLog = log.forSubComponent(TAG);
        mCallback = callback;
        mUsingMultinetworkPolicyTracker = usingMultinetworkPolicyTracker;
        mCm = context.getSystemService(ConnectivityManager.class);
        mDependencies = dependencies;
        mMulticastResolicitEnabled = dependencies.isFeatureNotChickenedOut(context,
                IP_REACHABILITY_MCAST_RESOLICIT_VERSION);
        mIgnoreIncompleteIpv6DnsServerEnabled = dependencies.isFeatureNotChickenedOut(context,
                IP_REACHABILITY_IGNORE_INCOMPLETE_IPV6_DNS_SERVER_VERSION);
        mIgnoreIncompleteIpv6DefaultRouterEnabled = dependencies.isFeatureEnabled(context,
                IP_REACHABILITY_IGNORE_INCOMPLETE_IPV6_DEFAULT_ROUTER_VERSION);
        mMacChangeFailureOnlyAfterRoam = dependencies.isFeatureNotChickenedOut(context,
                IP_REACHABILITY_ROUTER_MAC_CHANGE_FAILURE_ONLY_AFTER_ROAM_VERSION);
        mIgnoreOrganicNudFailure = dependencies.isFeatureEnabled(context,
                IP_REACHABILITY_IGNORE_ORGANIC_NUD_FAILURE_VERSION);
        mMetricsLog = metricsLog;
        mNetd = netd;
        Preconditions.checkNotNull(mNetd);
        Preconditions.checkArgument(!TextUtils.isEmpty(mInterfaceParams.name));

        // In case the overylaid parameters specify an invalid configuration, set the parameters
        // to the hardcoded defaults first, then set them to the values used in the steady state.
        try {
            int numResolicits = mMulticastResolicitEnabled
                    ? NUD_MCAST_RESOLICIT_NUM
                    : INVALID_NUD_MCAST_RESOLICIT_NUM;
            setNeighborParameters(MIN_NUD_SOLICIT_NUM, MIN_NUD_SOLICIT_INTERVAL_MS, numResolicits);
        } catch (Exception e) {
            Log.e(TAG, "Failed to adjust neighbor parameters with hardcoded defaults");
        }
        setNeighbourParametersForSteadyState();

        mIpNeighborMonitor = dependencies.makeIpNeighborMonitor(h, mLog,
                (NeighborEvent event) -> {
                    if (mInterfaceParams.index != event.ifindex) return;
                    if (!mNeighborWatchList.containsKey(event.ip)) return;

                    final NeighborEvent prev = mNeighborWatchList.put(event.ip, event);

                    // TODO: Consider what to do with other states that are not within
                    // NeighborEvent#isValid() (i.e. NUD_NONE, NUD_INCOMPLETE).
                    if (event.nudState == StructNdMsg.NUD_FAILED) {
                        // After both unicast probe and multicast probe(if mcast_resolicit is not 0)
                        // attempts fail, trigger the neighbor lost event and disconnect.
                        mLog.w("ALERT neighbor went from: " + prev + " to: " + event);
                        handleNeighborLost(prev, event);
                    } else if (event.nudState == StructNdMsg.NUD_REACHABLE) {
                        handleNeighborReachable(prev, event);
                    }
                });
        mIpNeighborMonitor.start();
        mIpReachabilityMetrics = dependencies.getIpReachabilityMonitorMetrics();
    }

    public void stop() {
        mIpNeighborMonitor.stop();
        clearLinkProperties();
    }

    public void dump(PrintWriter pw) {
        if (Looper.myLooper() == mIpNeighborMonitor.getHandler().getLooper()) {
            pw.println(describeWatchList("\n"));
            return;
        }

        final ConditionVariable cv = new ConditionVariable(false);
        mIpNeighborMonitor.getHandler().post(() -> {
            pw.println(describeWatchList("\n"));
            cv.open();
        });

        if (!cv.block(1000)) {
            pw.println("Timed out waiting for IpReachabilityMonitor dump");
        }
    }

    private String describeWatchList() { return describeWatchList(" "); }

    private String describeWatchList(String sep) {
        final StringBuilder sb = new StringBuilder();
        sb.append("iface{" + mInterfaceParams + "}," + sep);
        sb.append("ntable=[" + sep);
        String delimiter = "";
        for (Map.Entry<InetAddress, NeighborEvent> entry : mNeighborWatchList.entrySet()) {
            sb.append(delimiter).append(entry.getKey().getHostAddress() + "/" + entry.getValue());
            delimiter = "," + sep;
        }
        sb.append("]");
        return sb.toString();
    }

    @VisibleForTesting
    static boolean isOnLink(List<RouteInfo> routes, InetAddress ip) {
        for (RouteInfo route : routes) {
            if (!route.hasGateway() && route.matches(ip)
                    && route.getType() == RouteInfo.RTN_UNICAST) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDefaultRouterNeighborMacAddressChanged(
            @Nullable final NeighborEvent prev, @NonNull final NeighborEvent event) {
        // TODO: once this rolls out safely, merge something like aosp/2908139 and remove this code.
        if (mMacChangeFailureOnlyAfterRoam) {
            if (!isNeighborDefaultRouter(event)) return false;
            if (prev == null || prev.nudState != StructNdMsg.NUD_PROBE) return false;
            if (!isNudFailureDueToRoam()) return false;
        } else {
            // Previous, incorrect, behaviour: MAC address changes are a failure at all times.
            if (prev == null || !isNeighborDefaultRouter(event)) return false;
        }
        return !event.macAddr.equals(prev.macAddr);
    }

    private boolean isNeighborDefaultRouter(@NonNull final NeighborEvent event) {
        // For the IPv6 link-local scoped address, equals() works because the NeighborEvent.ip
        // doesn't have a scope id and Inet6Address#equals doesn't consider scope id neither.
        for (RouteInfo route : mLinkProperties.getRoutes()) {
            if (route.isDefaultRoute() && event.ip.equals(route.getGateway())) return true;
        }
        return false;
    }

    private boolean isNeighborDnsServer(@NonNull final NeighborEvent event) {
        for (InetAddress dns : mLinkProperties.getDnsServers()) {
            if (event.ip.equals(dns)) return true;
        }
        return false;
    }

    public void updateLinkProperties(LinkProperties lp) {
        if (!mInterfaceParams.name.equals(lp.getInterfaceName())) {
            // TODO: figure out whether / how to cope with interface changes.
            Log.wtf(TAG, "requested LinkProperties interface '" + lp.getInterfaceName() +
                    "' does not match: " + mInterfaceParams.name);
            return;
        }

        mLinkProperties = new LinkProperties(lp);
        Map<InetAddress, NeighborEvent> newNeighborWatchList = new HashMap<>();

        final List<RouteInfo> routes = mLinkProperties.getRoutes();
        for (RouteInfo route : routes) {
            if (route.hasGateway()) {
                InetAddress gw = route.getGateway();
                if (isOnLink(routes, gw)) {
                    newNeighborWatchList.put(gw, mNeighborWatchList.getOrDefault(gw, null));
                }
            }
        }

        for (InetAddress dns : lp.getDnsServers()) {
            if (isOnLink(routes, dns)) {
                newNeighborWatchList.put(dns, mNeighborWatchList.getOrDefault(dns, null));
            }
        }

        mNeighborWatchList = newNeighborWatchList;
        if (DBG) { Log.d(TAG, "watch: " + describeWatchList()); }
    }

    public void clearLinkProperties() {
        mLinkProperties.clear();
        mNeighborWatchList.clear();
        if (DBG) { Log.d(TAG, "clear: " + describeWatchList()); }
    }

    private void handleNeighborReachable(@Nullable final NeighborEvent prev,
            @NonNull final NeighborEvent event) {
        if (mMulticastResolicitEnabled
                && hasDefaultRouterNeighborMacAddressChanged(prev, event)) {
            // This implies device has confirmed the neighbor's reachability from
            // other states(e.g., NUD_PROBE or NUD_STALE), checking if the mac
            // address hasn't changed is required. If Mac address does change, then
            // trigger a new neighbor lost event and disconnect.
            final String logMsg = "ALERT neighbor: " + event.ip
                    + " MAC address changed from: " + prev.macAddr
                    + " to: " + event.macAddr;
            final NudEventType type =
                    getMacAddressChangedEventType(isFromProbe(), isNudFailureDueToRoam());
            mLog.w(logMsg);
            mCallback.notifyLost(event.ip, logMsg, type);
            logNudFailed(event, type);
            return;
        }
        maybeRestoreNeighborParameters();
    }

    private boolean shouldIgnoreIncompleteIpv6Neighbor(@Nullable final NeighborEvent prev,
            @NonNull final NeighborEvent event) {
        // If it isn't IPv6 neighbor, return false.
        if (!(event.ip instanceof Inet6Address)) return false;

        // If neighbor isn't in the watch list, return false.
        if (!mNeighborWatchList.containsKey(event.ip)) return false;

        // For on-link IPv6 DNS server or default router that never ever responds to address
        // resolution, kernel will send RTM_NEWNEIGH with NUD_FAILED to user space directly,
        // and there is no netlink neighbor events related to this neighbor received before.
        return (prev == null || event.nudState == StructNdMsg.NUD_FAILED);
    }

    private void handleNeighborLost(@Nullable final NeighborEvent prev,
            @NonNull final NeighborEvent event) {
        final LinkProperties whatIfLp = new LinkProperties(mLinkProperties);

        InetAddress ip = null;
        for (Map.Entry<InetAddress, NeighborEvent> entry : mNeighborWatchList.entrySet()) {
            // TODO: Consider using NeighborEvent#isValid() here; it's more
            // strict but may interact badly if other entries are somehow in
            // NUD_INCOMPLETE (say, during network attach).
            final NeighborEvent val = entry.getValue();

            // Find all the neighbors that have gone into FAILED state.
            // Ignore entries for which we have never received an event. If there are neighbors
            // that never respond to ARP/ND, the kernel will send several FAILED events, then
            // an INCOMPLETE event, and then more FAILED events. The INCOMPLETE event will
            // populate the map and the subsequent FAILED event will be processed.
            if (val == null || val.nudState != StructNdMsg.NUD_FAILED) continue;

            ip = entry.getKey();
            for (RouteInfo route : mLinkProperties.getRoutes()) {
                if (ip.equals(route.getGateway())) {
                    whatIfLp.removeRoute(route);
                }
            }

            if (avoidingBadLinks() || !(ip instanceof Inet6Address)) {
                // We should do this unconditionally, but alas we cannot: b/31827713.
                whatIfLp.removeDnsServer(ip);
            }
        }

        final boolean ignoreIncompleteIpv6DnsServer =
                mIgnoreIncompleteIpv6DnsServerEnabled
                        && isNeighborDnsServer(event)
                        && shouldIgnoreIncompleteIpv6Neighbor(prev, event);

        // Generally Router Advertisement should take SLLA option, then device won't do address
        // resolution for default router's IPv6 link-local address automatically. But sometimes
        // it may miss SLLA option, also add a flag to check these cases.
        final boolean ignoreIncompleteIpv6DefaultRouter =
                mIgnoreIncompleteIpv6DefaultRouterEnabled
                        && isNeighborDefaultRouter(event)
                        && shouldIgnoreIncompleteIpv6Neighbor(prev, event);

        // Only ignore the incomplete IPv6 neighbor iff IPv4 is still provisioned. For IPv6-only
        // networks, we MUST not ignore any incomplete IPv6 neighbor.
        final boolean ignoreIncompleteIpv6Neighbor =
                (ignoreIncompleteIpv6DnsServer || ignoreIncompleteIpv6DefaultRouter)
                        && whatIfLp.isIpv4Provisioned();

        // It's better to remove the incompleted on-link IPv6 DNS server or default router from
        // watch list, otherwise, when wifi invokes probeAll later (e.g. post roam) to send probe
        // to an incompleted on-link DNS server or default router, it should fail to send netlink
        // message to kernel as there is no neighbor cache entry for it at all.
        if (ignoreIncompleteIpv6Neighbor) {
            Log.d(TAG, "remove incomplete IPv6 neighbor " + event.ip
                    + " which fails to respond to address resolution from watch list.");
            mNeighborWatchList.remove(event.ip);
        }

        final boolean lostProvisioning =
                (mLinkProperties.isIpv4Provisioned() && !whatIfLp.isIpv4Provisioned())
                        || (mLinkProperties.isIpv6Provisioned() && !whatIfLp.isIpv6Provisioned()
                                && !ignoreIncompleteIpv6Neighbor);
        final NudEventType type = getNudFailureEventType(isFromProbe(),
                isNudFailureDueToRoam(), lostProvisioning);

        if (lostProvisioning) {
            final boolean isOrganicNudFailureAndToBeIgnored =
                    ((type == NudEventType.NUD_ORGANIC_FAILED_CRITICAL)
                            && mIgnoreOrganicNudFailure);
            final String logMsg = "FAILURE: LOST_PROVISIONING, " + event
                    + ", NUD event type: " + type.name()
                    + (isOrganicNudFailureAndToBeIgnored ? ", to be ignored" : "");
            Log.w(TAG, logMsg);
            // TODO: remove |ip| when the callback signature no longer has
            // an InetAddress argument.
            // Notify critical neighbor lost as long as the NUD failures
            // are not from kernel organic or the NUD failure event type is
            // NUD_ORGANIC_FAILED_CRITICAL but the experiment flag is not
            // enabled. Regardless, the event metrics are still recoreded.
            if (!isOrganicNudFailureAndToBeIgnored) {
                mCallback.notifyLost(ip, logMsg, type);
            }
        }
        logNudFailed(event, type);
    }

    private void maybeRestoreNeighborParameters() {
        for (Map.Entry<InetAddress, NeighborEvent> entry : mNeighborWatchList.entrySet()) {
            if (DBG) {
                Log.d(TAG, "neighbour IPv4(v6): " + entry.getKey() + " neighbour state: "
                        + StructNdMsg.stringForNudState(entry.getValue().nudState));
            }
            final NeighborEvent val = entry.getValue();
            // If an entry is null, consider that probing for that neighbour has completed.
            if (val == null || val.nudState != StructNdMsg.NUD_REACHABLE) return;
        }

        // Probing for all neighbours in the watchlist is complete and the connection is stable,
        // restore NUD probe parameters to steadystate value. In the case where neighbours
        // are responsive, this code will run before the wakelock expires.
        setNeighbourParametersForSteadyState();
    }

    private boolean avoidingBadLinks() {
        return !mUsingMultinetworkPolicyTracker || mCm.shouldAvoidBadWifi();
    }

    /**
     * Force probe to verify whether or not the critical on-link neighbours are still reachable.
     *
     * @param dueToRoam indicate on which situation forced probe has been sent, e.g., on post
     *                  roaming or receiving CMD_CONFIRM from IpClient.
     */
    public void probeAll(boolean dueToRoam) {
        setNeighbourParametersPostRoaming();

        final List<InetAddress> ipProbeList = new ArrayList<>(mNeighborWatchList.keySet());
        if (!ipProbeList.isEmpty()) {
            // Keep the CPU awake long enough to allow all ARP/ND
            // probes a reasonable chance at success. See b/23197666.
            //
            // The wakelock we use is (by default) refcounted, and this version
            // of acquire(timeout) queues a release message to keep acquisitions
            // and releases balanced.
            mDependencies.acquireWakeLock(getProbeWakeLockDuration());
        }

        for (InetAddress ip : ipProbeList) {
            final int rval = IpNeighborMonitor.startKernelNeighborProbe(mInterfaceParams.index, ip);
            mLog.log(String.format("put neighbor %s into NUD_PROBE state (rval=%d)",
                     ip.getHostAddress(), rval));
            logEvent(IpReachabilityEvent.PROBE, rval);
        }
        mLastProbeTimeMs = SystemClock.elapsedRealtime();
        if (dueToRoam) {
            mLastProbeDueToRoamMs = mLastProbeTimeMs;
        } else {
            mLastProbeDueToConfirmMs = mLastProbeTimeMs;
        }
    }

    private long getProbeWakeLockDuration() {
        final long gracePeriodMs = 500;
        final int numSolicits =
                mNumSolicits + (mMulticastResolicitEnabled ? NUD_MCAST_RESOLICIT_NUM : 0);
        return (long) (numSolicits * mInterSolicitIntervalMs) + gracePeriodMs;
    }

    private void setNeighbourParametersPostRoaming() {
        setNeighborParametersFromResources(R.integer.config_nud_postroaming_solicit_num,
                R.integer.config_nud_postroaming_solicit_interval);
    }

    private void setNeighbourParametersForSteadyState() {
        setNeighborParametersFromResources(R.integer.config_nud_steadystate_solicit_num,
                R.integer.config_nud_steadystate_solicit_interval);
    }

    private void setNeighborParametersFromResources(final int numResId, final int intervalResId) {
        try {
            final int numSolicits = mContext.getResources().getInteger(numResId);
            final int interSolicitIntervalMs = mContext.getResources().getInteger(intervalResId);
            setNeighborParameters(numSolicits, interSolicitIntervalMs);
        } catch (Exception e) {
            Log.e(TAG, "Failed to adjust neighbor parameters");
        }
    }

    private void setNeighborParameters(int numSolicits, int interSolicitIntervalMs)
            throws RemoteException, IllegalArgumentException {
        // Do not set mcast_resolicit param by default.
        setNeighborParameters(numSolicits, interSolicitIntervalMs, INVALID_NUD_MCAST_RESOLICIT_NUM);
    }

    private void setNeighborParameters(int numSolicits, int interSolicitIntervalMs,
            int numResolicits) throws RemoteException, IllegalArgumentException {
        Preconditions.checkArgument(numSolicits >= MIN_NUD_SOLICIT_NUM,
                "numSolicits must be at least " + MIN_NUD_SOLICIT_NUM);
        Preconditions.checkArgument(numSolicits <= MAX_NUD_SOLICIT_NUM,
                "numSolicits must be at most " + MAX_NUD_SOLICIT_NUM);
        Preconditions.checkArgument(interSolicitIntervalMs >= MIN_NUD_SOLICIT_INTERVAL_MS,
                "interSolicitIntervalMs must be at least " + MIN_NUD_SOLICIT_INTERVAL_MS);
        Preconditions.checkArgument(interSolicitIntervalMs <= MAX_NUD_SOLICIT_INTERVAL_MS,
                "interSolicitIntervalMs must be at most " + MAX_NUD_SOLICIT_INTERVAL_MS);

        for (int family : new Integer[]{INetd.IPV4, INetd.IPV6}) {
            mNetd.setProcSysNet(family, INetd.NEIGH, mInterfaceParams.name, "retrans_time_ms",
                    Integer.toString(interSolicitIntervalMs));
            mNetd.setProcSysNet(family, INetd.NEIGH, mInterfaceParams.name, "ucast_solicit",
                    Integer.toString(numSolicits));
            if (numResolicits != INVALID_NUD_MCAST_RESOLICIT_NUM) {
                mNetd.setProcSysNet(family, INetd.NEIGH, mInterfaceParams.name, "mcast_resolicit",
                        Integer.toString(numResolicits));
            }
        }

        mNumSolicits = numSolicits;
        mInterSolicitIntervalMs = interSolicitIntervalMs;
    }

    private boolean isFromProbe() {
        final long duration = SystemClock.elapsedRealtime() - mLastProbeTimeMs;
        return duration < getProbeWakeLockDuration();
    }

    private boolean isNudFailureDueToRoam() {
        if (!isFromProbe()) return false;

        // Check to which probe expiry the curren timestamp gets close when NUD failure event
        // happens, theoretically that indicates which probe event(due to roam or CMD_CONFIRM)
        // was triggered eariler.
        //
        // Note that this would be incorrect if the probe or confirm was so long ago that the
        // probe duration has already expired. That cannot happen because isFromProbe would return
        // false.
        final long probeExpiryAfterRoam = mLastProbeDueToRoamMs + getProbeWakeLockDuration();
        final long probeExpiryAfterConfirm =
                mLastProbeDueToConfirmMs + getProbeWakeLockDuration();
        final long currentTime = SystemClock.elapsedRealtime();
        return Math.abs(probeExpiryAfterRoam - currentTime)
                < Math.abs(probeExpiryAfterConfirm - currentTime);
    }

    private void logEvent(int probeType, int errorCode) {
        int eventType = probeType | (errorCode & 0xff);
        mMetricsLog.log(mInterfaceParams.name, new IpReachabilityEvent(eventType));
    }

    private void logNudFailed(final NeighborEvent event, final NudEventType type) {
        logNeighborLostEvent(event, type);

        // The legacy metrics only record whether the failure came from a probe and whether
        // the network is still provisioned. They do not record provisioning failures due to
        // multicast resolicits finding that the MAC address has changed.
        final int eventType = legacyNudFailureType(type);
        if (eventType == INVALID_LEGACY_NUD_FAILURE_TYPE) return;
        mMetricsLog.log(mInterfaceParams.name, new IpReachabilityEvent(eventType));
    }

    /**
     * Returns the neighbor type code corresponding to the given conditions.
     */
    private NudNeighborType getNeighborType(final NeighborEvent event) {
        final boolean isGateway = isNeighborDefaultRouter(event);
        final boolean isDnsServer = isNeighborDnsServer(event);

        if (isGateway && isDnsServer) return NudNeighborType.NUD_NEIGHBOR_BOTH;
        if (isGateway && !isDnsServer) return NudNeighborType.NUD_NEIGHBOR_GATEWAY;
        if (!isGateway && isDnsServer) return NudNeighborType.NUD_NEIGHBOR_DNS;
        return NudNeighborType.NUD_NEIGHBOR_UNKNOWN;
    }

    /**
     * Returns the NUD failure event type code corresponding to the given conditions.
     */
    private static NudEventType getNudFailureEventType(boolean isFromProbe, boolean isDueToRoam,
            boolean isProvisioningLost) {
        if (!isFromProbe) {
            return isProvisioningLost
                    ? NudEventType.NUD_ORGANIC_FAILED_CRITICAL
                    : NudEventType.NUD_ORGANIC_FAILED;
        }
        return isProvisioningLost
                ? isDueToRoam
                        ? NudEventType.NUD_POST_ROAMING_FAILED_CRITICAL
                        : NudEventType.NUD_CONFIRM_FAILED_CRITICAL
                : isDueToRoam
                        ? NudEventType.NUD_POST_ROAMING_FAILED
                        : NudEventType.NUD_CONFIRM_FAILED;
    }

    /**
     * Returns the NUD failure event type code due to neighbor's MAC address has changed
     * corresponding to the given conditions.
     */
    private static NudEventType getMacAddressChangedEventType(boolean isFromProbe,
            boolean isDueToRoam) {
        return isFromProbe
                ? isDueToRoam
                        ? NudEventType.NUD_POST_ROAMING_MAC_ADDRESS_CHANGED
                        : NudEventType.NUD_CONFIRM_MAC_ADDRESS_CHANGED
                : NudEventType.NUD_ORGANIC_MAC_ADDRESS_CHANGED;
    }

    /**
     * Log NUD failure metrics with new statsd APIs while the function using mMetricsLog API
     * still sends the legacy metrics, @see #logNudFailed.
     */
    private void logNeighborLostEvent(final NeighborEvent event, final NudEventType type) {
        final IpType ipType = (event.ip instanceof Inet6Address) ? IpType.IPV6 : IpType.IPV4;
        mIpReachabilityMetrics.setNudIpType(ipType);
        mIpReachabilityMetrics.setNudNeighborType(getNeighborType(event));
        mIpReachabilityMetrics.setNudEventType(type);
        mIpReachabilityMetrics.statsWrite();
    }

    /**
     * Returns the NUD failure event type code corresponding to the given conditions.
     */
    private static int legacyNudFailureType(final NudEventType type) {
        switch (type) {
            case NUD_POST_ROAMING_FAILED:
            case NUD_CONFIRM_FAILED:
                return NUD_FAILED;
            case NUD_POST_ROAMING_FAILED_CRITICAL:
            case NUD_CONFIRM_FAILED_CRITICAL:
                return PROVISIONING_LOST;
            case NUD_ORGANIC_FAILED:
                return NUD_FAILED_ORGANIC;
            case NUD_ORGANIC_FAILED_CRITICAL:
                return PROVISIONING_LOST_ORGANIC;
            default:
                // Do not log legacy event
                return INVALID_LEGACY_NUD_FAILURE_TYPE;
        }
    }

    /**
     * Convert the NUD critical failure event type to a int constant defined in IIpClientCallbacks.
     */
    public static int nudEventTypeToInt(final NudEventType type) {
        switch (type) {
            case NUD_POST_ROAMING_FAILED_CRITICAL:
            case NUD_POST_ROAMING_MAC_ADDRESS_CHANGED:
                return ReachabilityLossReason.ROAM;
            case NUD_CONFIRM_FAILED_CRITICAL:
            case NUD_CONFIRM_MAC_ADDRESS_CHANGED:
                return ReachabilityLossReason.CONFIRM;
            case NUD_ORGANIC_FAILED_CRITICAL:
            case NUD_ORGANIC_MAC_ADDRESS_CHANGED:
                return ReachabilityLossReason.ORGANIC;
            // For other NudEventType which won't trigger notifyLost, just ignore these events.
            default:
                return INVALID_REACHABILITY_LOSS_TYPE;
        }
    }
}
