/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.net;

import static android.net.NetworkStats.INTERFACES_ALL;
import static android.net.NetworkStats.TAG_ALL;
import static android.net.NetworkStats.UID_ALL;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.NetworkStats;
import android.net.UnderlyingNetworkInfo;
import android.os.ServiceSpecificException;
import android.os.SystemClock;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.BpfNetMaps;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates {@link NetworkStats} instances by parsing various {@code /proc/}
 * files as needed.
 *
 * @hide
 */
public class NetworkStatsFactory {
    static {
        System.loadLibrary("service-connectivity");
    }

    private static final String TAG = "NetworkStatsFactory";

    private final Context mContext;

    private final BpfNetMaps mBpfNetMaps;

    /**
     * Guards persistent data access in this class
     *
     * <p>In order to prevent deadlocks, critical sections protected by this lock SHALL NOT call out
     * to other code that will acquire other locks within the system server. See b/134244752.
     */
    private final Object mPersistentDataLock = new Object();

    /** Set containing info about active VPNs and their underlying networks. */
    private volatile UnderlyingNetworkInfo[] mUnderlyingNetworkInfos = new UnderlyingNetworkInfo[0];

    // A persistent snapshot of cumulative stats since device start
    @GuardedBy("mPersistentDataLock")
    private NetworkStats mPersistSnapshot;

    // The persistent snapshot of tun and 464xlat adjusted stats since device start
    @GuardedBy("mPersistentDataLock")
    private NetworkStats mTunAnd464xlatAdjustedStats;

    private final Dependencies mDeps;
    /**
     * Dependencies of NetworkStatsFactory, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * Parse detailed statistics from bpf into given {@link NetworkStats} object. Values
         * are expected to monotonically increase since device boot.
         */
        @NonNull
        public NetworkStats getNetworkStatsDetail() throws IOException {
            final NetworkStats stats = new NetworkStats(SystemClock.elapsedRealtime(), 0);
            final int ret = nativeReadNetworkStatsDetail(stats);
            if (ret != 0) {
                throw new IOException("Failed to parse network stats");
            }
            return stats;
        }
        /**
         * Parse device summary statistics from bpf into given {@link NetworkStats} object. Values
         * are expected to monotonically increase since device boot.
         */
        @NonNull
        public NetworkStats getNetworkStatsDev() throws IOException {
            final NetworkStats stats = new NetworkStats(SystemClock.elapsedRealtime(), 6);
            final int ret = nativeReadNetworkStatsDev(stats);
            if (ret != 0) {
                throw new IOException("Failed to parse bpf iface stats");
            }
            return stats;
        }

        /** Create a new {@link BpfNetMaps}. */
        public BpfNetMaps createBpfNetMaps(@NonNull Context ctx) {
            return new BpfNetMaps(ctx);
        }
    }

    /**
     * (Stacked interface) -> (base interface) association for all connected ifaces since boot.
     *
     * Because counters must never roll backwards, once a given interface is stacked on top of an
     * underlying interface, the stacked interface can never be stacked on top of
     * another interface. */
    private final ConcurrentHashMap<String, String> mStackedIfaces
            = new ConcurrentHashMap<>();

    /** Informs the factory of a new stacked interface. */
    public void noteStackedIface(String stackedIface, String baseIface) {
        if (stackedIface != null && baseIface != null) {
            mStackedIfaces.put(stackedIface, baseIface);
        }
    }

    /**
     * Set active VPN information for data usage migration purposes
     *
     * <p>Traffic on TUN-based VPNs inherently all appear to be originated from the VPN providing
     * app's UID. This method is used to support migration of VPN data usage, ensuring data is
     * accurately billed to the real owner of the traffic.
     *
     * @param vpnArray The snapshot of the currently-running VPNs.
     */
    public void updateUnderlyingNetworkInfos(UnderlyingNetworkInfo[] vpnArray) {
        mUnderlyingNetworkInfos = vpnArray.clone();
    }

    /**
     * Applies 464xlat adjustments with ifaces noted with {@link #noteStackedIface(String, String)}.
     * @see NetworkStats#apply464xlatAdjustments(NetworkStats, NetworkStats, Map)
     */
    public void apply464xlatAdjustments(NetworkStats baseTraffic, NetworkStats stackedTraffic) {
        NetworkStats.apply464xlatAdjustments(baseTraffic, stackedTraffic, mStackedIfaces);
    }

    public NetworkStatsFactory(@NonNull Context ctx) {
        this(ctx, new Dependencies());
    }

    @VisibleForTesting
    public NetworkStatsFactory(@NonNull Context ctx, Dependencies deps) {
        mBpfNetMaps = deps.createBpfNetMaps(ctx);
        synchronized (mPersistentDataLock) {
            mPersistSnapshot = new NetworkStats(SystemClock.elapsedRealtime(), -1);
            mTunAnd464xlatAdjustedStats = new NetworkStats(SystemClock.elapsedRealtime(), -1);
        }
        mContext = ctx;
        mDeps = deps;
    }

    /**
     * Parse and return interface-level summary {@link NetworkStats}. Designed
     * to return only IP layer traffic. Values monotonically increase since
     * device boot, and may include details about inactive interfaces.
     */
    public NetworkStats readNetworkStatsSummaryXt() throws IOException {
        return mDeps.getNetworkStatsDev();
    }

    public NetworkStats readNetworkStatsDetail() throws IOException {
        return readNetworkStatsDetail(UID_ALL, INTERFACES_ALL, TAG_ALL);
    }

    @GuardedBy("mPersistentDataLock")
    private void requestSwapActiveStatsMapLocked() throws IOException {
        try {
            // Do a active map stats swap. Once the swap completes, this code
            // can read and clean the inactive map without races.
            mBpfNetMaps.swapActiveStatsMap();
        } catch (ServiceSpecificException e) {
            throw new IOException(e);
        }
    }

    /**
     * Reads the detailed UID stats based on the provided parameters
     *
     * @param limitUid the UID to limit this query to
     * @param limitIfaces the interfaces to limit this query to. Use {@link
     *     NetworkStats.INTERFACES_ALL} to select all interfaces
     * @param limitTag the tags to limit this query to
     * @return the NetworkStats instance containing network statistics at the present time.
     */
    public NetworkStats readNetworkStatsDetail(
            int limitUid, String[] limitIfaces, int limitTag) throws IOException {
        // In order to prevent deadlocks, anything protected by this lock MUST NOT call out to other
        // code that will acquire other locks within the system server. See b/134244752.
        synchronized (mPersistentDataLock) {
            // Take a reference. If this gets swapped out, we still have the old reference.
            final UnderlyingNetworkInfo[] vpnArray = mUnderlyingNetworkInfos;
            // Take a defensive copy. mPersistSnapshot is mutated in some cases below
            final NetworkStats prev = mPersistSnapshot.clone();

            requestSwapActiveStatsMapLocked();
            // Stats are always read from the inactive map, so they must be read after the
            // swap
            final NetworkStats stats = mDeps.getNetworkStatsDetail();
            // BPF stats are incremental; fold into mPersistSnapshot.
            mPersistSnapshot.setElapsedRealtime(stats.getElapsedRealtime());
            mPersistSnapshot.combineAllValues(stats);

            NetworkStats adjustedStats = adjustForTunAnd464Xlat(mPersistSnapshot, prev, vpnArray);

            // Filter return values
            adjustedStats.filter(limitUid, limitIfaces, limitTag);
            return adjustedStats;
        }
    }

    @GuardedBy("mPersistentDataLock")
    private NetworkStats adjustForTunAnd464Xlat(NetworkStats uidDetailStats,
            NetworkStats previousStats, UnderlyingNetworkInfo[] vpnArray) {
        // Calculate delta from last snapshot
        final NetworkStats delta = uidDetailStats.subtract(previousStats);

        // Apply 464xlat adjustments before VPN adjustments. If VPNs are using v4 on a v6 only
        // network, the overhead is their fault.
        // No locking here: apply464xlatAdjustments behaves fine with an add-only
        // ConcurrentHashMap.
        delta.apply464xlatAdjustments(mStackedIfaces);

        // Migrate data usage over a VPN to the TUN network.
        for (UnderlyingNetworkInfo info : vpnArray) {
            delta.migrateTun(info.getOwnerUid(), info.getInterface(),
                    info.getUnderlyingInterfaces());
            // Filter out debug entries as that may lead to over counting.
            delta.filterDebugEntries();
        }

        // Update mTunAnd464xlatAdjustedStats with migrated delta.
        mTunAnd464xlatAdjustedStats.combineAllValues(delta);
        mTunAnd464xlatAdjustedStats.setElapsedRealtime(uidDetailStats.getElapsedRealtime());

        return mTunAnd464xlatAdjustedStats.clone();
    }

    /**
     * Remove stats from {@code mPersistSnapshot} and {@code mTunAnd464xlatAdjustedStats} for the
     * given uids.
     */
    public void removeUidsLocked(int[] uids) {
        synchronized (mPersistentDataLock) {
            mPersistSnapshot.removeUids(uids);
            mTunAnd464xlatAdjustedStats.removeUids(uids);
        }
    }

    public void assertEquals(NetworkStats expected, NetworkStats actual) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(
                    "Expected size " + expected.size() + ", actual size " + actual.size());
        }

        NetworkStats.Entry expectedRow = null;
        NetworkStats.Entry actualRow = null;
        for (int i = 0; i < expected.size(); i++) {
            expectedRow = expected.getValues(i, expectedRow);
            actualRow = actual.getValues(i, actualRow);
            if (!expectedRow.equals(actualRow)) {
                throw new AssertionError(
                        "Expected row " + i + ": " + expectedRow + ", actual row " + actualRow);
            }
        }
    }

    /**
     * Convert {@code /proc/} tag format to {@link Integer}. Assumes incoming
     * format like {@code 0x7fffffff00000000}.
     */
    public static int kernelToTag(String string) {
        int length = string.length();
        if (length > 10) {
            return Long.decode(string.substring(0, length - 8)).intValue();
        } else {
            return 0;
        }
    }

    /**
     * Parse statistics from file into given {@link NetworkStats} object. Values
     * are expected to monotonically increase since device boot.
     */
    @VisibleForTesting
    public static native int nativeReadNetworkStatsDetail(NetworkStats stats);

    @VisibleForTesting
    public static native int nativeReadNetworkStatsDev(NetworkStats stats);

    private static ProtocolException protocolExceptionWithCause(String message, Throwable cause) {
        ProtocolException pe = new ProtocolException(message);
        pe.initCause(cause);
        return pe;
    }
}
