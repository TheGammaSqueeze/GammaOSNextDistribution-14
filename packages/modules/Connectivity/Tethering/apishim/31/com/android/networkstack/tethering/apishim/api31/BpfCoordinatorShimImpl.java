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

package com.android.networkstack.tethering.apishim.api31;

import static android.net.netstats.provider.NetworkStatsProvider.QUOTA_UNLIMITED;

import static com.android.net.module.util.NetworkStackConstants.RFC7421_PREFIX_LENGTH;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.IBpfMap.ThrowingBiConsumer;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.bpf.Tether4Key;
import com.android.net.module.util.bpf.Tether4Value;
import com.android.net.module.util.bpf.TetherStatsKey;
import com.android.net.module.util.bpf.TetherStatsValue;
import com.android.networkstack.tethering.BpfCoordinator.Dependencies;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6DownstreamRule;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6UpstreamRule;
import com.android.networkstack.tethering.BpfUtils;
import com.android.networkstack.tethering.Tether6Value;
import com.android.networkstack.tethering.TetherDevKey;
import com.android.networkstack.tethering.TetherDevValue;
import com.android.networkstack.tethering.TetherDownstream6Key;
import com.android.networkstack.tethering.TetherLimitKey;
import com.android.networkstack.tethering.TetherLimitValue;
import com.android.networkstack.tethering.TetherUpstream6Key;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Bpf coordinator class for API shims.
 */
public class BpfCoordinatorShimImpl
        extends com.android.networkstack.tethering.apishim.common.BpfCoordinatorShim {
    private static final String TAG = "api31.BpfCoordinatorShimImpl";

    // AF_KEY socket type. See include/linux/socket.h.
    private static final int AF_KEY = 15;
    // PFKEYv2 constants. See include/uapi/linux/pfkeyv2.h.
    private static final int PF_KEY_V2 = 2;

    @NonNull
    private final SharedLog mLog;

    // BPF map for downstream IPv4 forwarding.
    @Nullable
    private final IBpfMap<Tether4Key, Tether4Value> mBpfDownstream4Map;

    // BPF map for upstream IPv4 forwarding.
    @Nullable
    private final IBpfMap<Tether4Key, Tether4Value> mBpfUpstream4Map;

    // BPF map for downstream IPv6 forwarding.
    @Nullable
    private final IBpfMap<TetherDownstream6Key, Tether6Value> mBpfDownstream6Map;

    // BPF map for upstream IPv6 forwarding.
    @Nullable
    private final IBpfMap<TetherUpstream6Key, Tether6Value> mBpfUpstream6Map;

    // BPF map of tethering statistics of the upstream interface since tethering startup.
    @Nullable
    private final IBpfMap<TetherStatsKey, TetherStatsValue> mBpfStatsMap;

    // BPF map of per-interface quota for tethering offload.
    @Nullable
    private final IBpfMap<TetherLimitKey, TetherLimitValue> mBpfLimitMap;

    // BPF map of interface index mapping for XDP.
    @Nullable
    private final IBpfMap<TetherDevKey, TetherDevValue> mBpfDevMap;

    // Tracking IPv4 rule count while any rule is using the given upstream interfaces. Used for
    // reducing the BPF map iteration query. The count is increased or decreased when the rule is
    // added or removed successfully on mBpfDownstream4Map. Counting the rules on downstream4 map
    // is because tetherOffloadRuleRemove can't get upstream interface index from upstream key,
    // unless pass upstream value which is not required for deleting map entry. The upstream
    // interface index is the same in Upstream4Value.oif and Downstream4Key.iif. For now, it is
    // okay to count on Downstream4Key. See BpfConntrackEventConsumer#accept.
    // Note that except the constructor, any calls to mBpfDownstream4Map.clear() need to clear
    // this counter as well.
    // TODO: Count the rule on upstream if multi-upstream is supported and the
    // packet needs to be sent and responded on different upstream interfaces.
    // TODO: Add IPv6 rule count.
    private final SparseArray<Integer> mRule4CountOnUpstream = new SparseArray<>();

    public BpfCoordinatorShimImpl(@NonNull final Dependencies deps) {
        mLog = deps.getSharedLog().forSubComponent(TAG);

        mBpfDownstream4Map = deps.getBpfDownstream4Map();
        mBpfUpstream4Map = deps.getBpfUpstream4Map();
        mBpfDownstream6Map = deps.getBpfDownstream6Map();
        mBpfUpstream6Map = deps.getBpfUpstream6Map();
        mBpfStatsMap = deps.getBpfStatsMap();
        mBpfLimitMap = deps.getBpfLimitMap();
        mBpfDevMap = deps.getBpfDevMap();

        // Clear the stubs of the maps for handling the system service crash if any.
        // Doesn't throw the exception and clear the stubs as many as possible.
        try {
            if (mBpfDownstream4Map != null) mBpfDownstream4Map.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfDownstream4Map: " + e);
        }
        try {
            if (mBpfUpstream4Map != null) mBpfUpstream4Map.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfUpstream4Map: " + e);
        }
        try {
            if (mBpfDownstream6Map != null) mBpfDownstream6Map.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfDownstream6Map: " + e);
        }
        try {
            if (mBpfUpstream6Map != null) mBpfUpstream6Map.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfUpstream6Map: " + e);
        }
        try {
            if (mBpfStatsMap != null) mBpfStatsMap.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfStatsMap: " + e);
        }
        try {
            if (mBpfLimitMap != null) mBpfLimitMap.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfLimitMap: " + e);
        }
        try {
            if (mBpfDevMap != null) mBpfDevMap.clear();
        } catch (ErrnoException e) {
            mLog.e("Could not clear mBpfDevMap: " + e);
        }
    }

    @Override
    public boolean isInitialized() {
        return mBpfDownstream4Map != null && mBpfUpstream4Map != null && mBpfDownstream6Map != null
                && mBpfUpstream6Map != null && mBpfStatsMap != null && mBpfLimitMap != null
                && mBpfDevMap != null;
    }

    @Override
    public boolean addIpv6UpstreamRule(@NonNull final Ipv6UpstreamRule rule) {
        // RFC7421_PREFIX_LENGTH = 64 which is the most commonly used IPv6 subnet prefix length.
        if (rule.sourcePrefix.getPrefixLength() != RFC7421_PREFIX_LENGTH) return false;

        final TetherUpstream6Key key = rule.makeTetherUpstream6Key();
        final Tether6Value value = rule.makeTether6Value();

        try {
            mBpfUpstream6Map.insertEntry(key, value);
        } catch (ErrnoException | IllegalStateException e) {
            mLog.e("Could not insert upstream IPv6 entry: " + e);
            return false;
        }
        return true;
    }

    @Override
    public boolean removeIpv6UpstreamRule(@NonNull final Ipv6UpstreamRule rule) {
        // RFC7421_PREFIX_LENGTH = 64 which is the most commonly used IPv6 subnet prefix length.
        if (rule.sourcePrefix.getPrefixLength() != RFC7421_PREFIX_LENGTH) return false;

        try {
            mBpfUpstream6Map.deleteEntry(rule.makeTetherUpstream6Key());
        } catch (ErrnoException e) {
            mLog.e("Could not delete upstream IPv6 entry: " + e);
            return false;
        }
        return true;
    }

    @Override
    public boolean addIpv6DownstreamRule(@NonNull final Ipv6DownstreamRule rule) {
        final TetherDownstream6Key key = rule.makeTetherDownstream6Key();
        final Tether6Value value = rule.makeTether6Value();

        try {
            mBpfDownstream6Map.updateEntry(key, value);
        } catch (ErrnoException e) {
            mLog.e("Could not update entry: ", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean removeIpv6DownstreamRule(@NonNull final Ipv6DownstreamRule rule) {
        try {
            mBpfDownstream6Map.deleteEntry(rule.makeTetherDownstream6Key());
        } catch (ErrnoException e) {
            // Silent if the rule did not exist.
            if (e.errno != OsConstants.ENOENT) {
                mLog.e("Could not update entry: ", e);
                return false;
            }
        }
        return true;
    }

    @Override
    @Nullable
    public SparseArray<TetherStatsValue> tetherOffloadGetStats() {
        final SparseArray<TetherStatsValue> tetherStatsList = new SparseArray<TetherStatsValue>();
        try {
            // The reported tether stats are total data usage for all currently-active upstream
            // interfaces since tethering start.
            mBpfStatsMap.forEach((key, value) -> tetherStatsList.put((int) key.ifindex, value));
        } catch (ErrnoException e) {
            mLog.e("Fail to fetch tethering stats from BPF map: ", e);
            return null;
        }
        return tetherStatsList;
    }

    @Override
    public boolean tetherOffloadSetInterfaceQuota(int ifIndex, long quotaBytes) {
        // The common case is an update, where the stats already exist,
        // hence we read first, even though writing with BPF_NOEXIST
        // first would make the code simpler.
        long rxBytes, txBytes;
        TetherStatsValue statsValue = null;

        try {
            statsValue = mBpfStatsMap.getValue(new TetherStatsKey(ifIndex));
        } catch (ErrnoException e) {
            // The BpfMap#getValue doesn't throw an errno ENOENT exception. Catch other error
            // while trying to get stats entry.
            mLog.e("Could not get stats entry of interface index " + ifIndex + ": ", e);
            return false;
        }

        if (statsValue != null) {
            // Ok, there was a stats entry.
            rxBytes = statsValue.rxBytes;
            txBytes = statsValue.txBytes;
        } else {
            // No stats entry - create one with zeroes.
            try {
                // This function is the *only* thing that can create entries.
                // BpfMap#insertEntry use BPF_NOEXIST to create the entry. The entry is created
                // if and only if it doesn't exist.
                mBpfStatsMap.insertEntry(new TetherStatsKey(ifIndex), new TetherStatsValue(
                        0 /* rxPackets */, 0 /* rxBytes */, 0 /* rxErrors */, 0 /* txPackets */,
                        0 /* txBytes */, 0 /* txErrors */));
            } catch (ErrnoException | IllegalArgumentException e) {
                mLog.e("Could not create stats entry: ", e);
                return false;
            }
            rxBytes = 0;
            txBytes = 0;
        }

        // rxBytes + txBytes won't overflow even at 5gbps for ~936 years.
        long newLimit = rxBytes + txBytes + quotaBytes;

        // if adding limit (e.g., if limit is QUOTA_UNLIMITED) caused overflow: clamp to 'infinity'
        if (newLimit < rxBytes + txBytes) newLimit = QUOTA_UNLIMITED;

        try {
            mBpfLimitMap.updateEntry(new TetherLimitKey(ifIndex), new TetherLimitValue(newLimit));
        } catch (ErrnoException e) {
            mLog.e("Fail to set quota " + quotaBytes + " for interface index " + ifIndex + ": ", e);
            return false;
        }

        return true;
    }

    @Override
    @Nullable
    public TetherStatsValue tetherOffloadGetAndClearStats(int ifIndex) {
        // getAndClearTetherOffloadStats is called after all offload rules have already been
        // deleted for the given upstream interface. Before starting to do cleanup stuff in this
        // function, use synchronizeKernelRCU to make sure that all the current running eBPF
        // programs are finished on all CPUs, especially the unfinished packet processing. After
        // synchronizeKernelRCU returned, we can safely read or delete on the stats map or the
        // limit map.
        final int res = synchronizeKernelRCU();
        if (res != 0) {
            // Error log but don't return. Do as much cleanup as possible.
            mLog.e("synchronize_rcu() failed: " + res);
        }

        TetherStatsValue statsValue = null;
        try {
            statsValue = mBpfStatsMap.getValue(new TetherStatsKey(ifIndex));
        } catch (ErrnoException e) {
            mLog.e("Could not get stats entry for interface index " + ifIndex + ": ", e);
            return null;
        }

        if (statsValue == null) {
            mLog.e("Could not get stats entry for interface index " + ifIndex);
            return null;
        }

        try {
            mBpfStatsMap.deleteEntry(new TetherStatsKey(ifIndex));
        } catch (ErrnoException e) {
            mLog.e("Could not delete stats entry for interface index " + ifIndex + ": ", e);
            return null;
        }

        try {
            mBpfLimitMap.deleteEntry(new TetherLimitKey(ifIndex));
        } catch (ErrnoException e) {
            mLog.e("Could not delete limit for interface index " + ifIndex + ": ", e);
            return null;
        }

        return statsValue;
    }

    @Override
    public boolean tetherOffloadRuleAdd(boolean downstream, @NonNull Tether4Key key,
            @NonNull Tether4Value value) {
        try {
            if (downstream) {
                mBpfDownstream4Map.insertEntry(key, value);

                // Increase the rule count while a adding rule is using a given upstream interface.
                final int upstreamIfindex = (int) key.iif;
                int count = mRule4CountOnUpstream.get(upstreamIfindex, 0 /* default */);
                mRule4CountOnUpstream.put(upstreamIfindex, ++count);
            } else {
                mBpfUpstream4Map.insertEntry(key, value);
            }
        } catch (ErrnoException e) {
            mLog.e("Could not insert entry (" + key + ", " + value + "): " + e);
            return false;
        } catch (IllegalStateException e) {
            // Silent if the rule already exists. Note that the errno EEXIST was rethrown as
            // IllegalStateException. See BpfMap#insertEntry.
        }
        return true;
    }

    @Override
    public boolean tetherOffloadRuleRemove(boolean downstream, @NonNull Tether4Key key) {
        try {
            if (downstream) {
                if (!mBpfDownstream4Map.deleteEntry(key)) return false;  // Rule did not exist

                // Decrease the rule count while a deleting rule is not using a given upstream
                // interface anymore.
                final int upstreamIfindex = (int) key.iif;
                Integer count = mRule4CountOnUpstream.get(upstreamIfindex);
                if (count == null) {
                    Log.wtf(TAG, "Could not delete count for interface " + upstreamIfindex);
                    return false;
                }

                if (--count == 0) {
                    // Remove the entry if the count decreases to zero.
                    mRule4CountOnUpstream.remove(upstreamIfindex);
                } else {
                    mRule4CountOnUpstream.put(upstreamIfindex, count);
                }
            } else {
                if (!mBpfUpstream4Map.deleteEntry(key)) return false;  // Rule did not exist
            }
        } catch (ErrnoException e) {
            mLog.e("Could not delete entry (key: " + key + ")", e);
            return false;
        }
        return true;
    }

    @Override
    public void tetherOffloadRuleForEach(boolean downstream,
            @NonNull ThrowingBiConsumer<Tether4Key, Tether4Value> action) {
        try {
            if (downstream) {
                mBpfDownstream4Map.forEach(action);
            } else {
                mBpfUpstream4Map.forEach(action);
            }
        } catch (ErrnoException e) {
            mLog.e("Could not iterate map: ", e);
        }
    }

    @Override
    public boolean attachProgram(String iface, boolean downstream, boolean ipv4) {
        try {
            BpfUtils.attachProgram(iface, downstream, ipv4);
        } catch (IOException e) {
            mLog.e("Could not attach program: " + e);
            return false;
        }
        return true;
    }

    @Override
    public boolean detachProgram(String iface, boolean ipv4) {
        try {
            BpfUtils.detachProgram(iface, ipv4);
        } catch (IOException e) {
            mLog.e("Could not detach program: " + e);
            return false;
        }
        return true;
    }

    @Override
    public boolean isAnyIpv4RuleOnUpstream(int ifIndex) {
        // No entry means no rule for the given interface because 0 has never been stored.
        return mRule4CountOnUpstream.get(ifIndex) != null;
    }

    @Override
    public boolean addDevMap(int ifIndex) {
        try {
            mBpfDevMap.updateEntry(new TetherDevKey(ifIndex), new TetherDevValue(ifIndex));
        } catch (ErrnoException e) {
            mLog.e("Could not add interface " + ifIndex + ": " + e);
            return false;
        }
        return true;
    }

    @Override
    public boolean removeDevMap(int ifIndex) {
        try {
            mBpfDevMap.deleteEntry(new TetherDevKey(ifIndex));
        } catch (ErrnoException e) {
            mLog.e("Could not delete interface " + ifIndex + ": " + e);
            return false;
        }
        return true;
    }

    private String mapStatus(IBpfMap m, String name) {
        return name + "{" + (m != null ? "OK" : "ERROR") + "}";
    }

    @Override
    public String toString() {
        return String.join(", ", new String[] {
                mapStatus(mBpfDownstream6Map, "mBpfDownstream6Map"),
                mapStatus(mBpfUpstream6Map, "mBpfUpstream6Map"),
                mapStatus(mBpfDownstream4Map, "mBpfDownstream4Map"),
                mapStatus(mBpfUpstream4Map, "mBpfUpstream4Map"),
                mapStatus(mBpfStatsMap, "mBpfStatsMap"),
                mapStatus(mBpfLimitMap, "mBpfLimitMap"),
                mapStatus(mBpfDevMap, "mBpfDevMap")
        });
    }

    /**
     * Call synchronize_rcu() to block until all existing RCU read-side critical sections have
     * been completed.
     * Note that BpfCoordinatorTest have no permissions to create or close pf_key socket. It is
     * okay for now because the caller #bpfGetAndClearStats doesn't care the result of this
     * function. The tests don't be broken.
     * TODO: Wrap this function into Dependencies for mocking in tests.
     */
    private int synchronizeKernelRCU() {
        // This is a temporary hack for network stats map swap on devices running
        // 4.9 kernels. The kernel code of socket release on pf_key socket will
        // explicitly call synchronize_rcu() which is exactly what we need.
        FileDescriptor pfSocket;
        try {
            pfSocket = Os.socket(AF_KEY, OsConstants.SOCK_RAW | OsConstants.SOCK_CLOEXEC,
                    PF_KEY_V2);
        } catch (ErrnoException e) {
            mLog.e("create PF_KEY socket failed: ", e);
            return e.errno;
        }

        // When closing socket, synchronize_rcu() gets called in sock_release().
        try {
            Os.close(pfSocket);
        } catch (ErrnoException e) {
            mLog.e("failed to close the PF_KEY socket: ", e);
            return e.errno;
        }

        return 0;
    }
}
