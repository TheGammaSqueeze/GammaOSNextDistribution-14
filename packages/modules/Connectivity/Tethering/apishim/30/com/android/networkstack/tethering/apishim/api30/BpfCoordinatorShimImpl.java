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

package com.android.networkstack.tethering.apishim.api30;

import android.net.INetd;
import android.net.TetherStatsParcel;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.IBpfMap.ThrowingBiConsumer;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.bpf.Tether4Key;
import com.android.net.module.util.bpf.Tether4Value;
import com.android.net.module.util.bpf.TetherStatsValue;
import com.android.networkstack.tethering.BpfCoordinator.Dependencies;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6DownstreamRule;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6UpstreamRule;

/**
 * Bpf coordinator class for API shims.
 */
public class BpfCoordinatorShimImpl
        extends com.android.networkstack.tethering.apishim.common.BpfCoordinatorShim {
    private static final String TAG = "api30.BpfCoordinatorShimImpl";

    @NonNull
    private final SharedLog mLog;
    @NonNull
    private final INetd mNetd;

    public BpfCoordinatorShimImpl(@NonNull final Dependencies deps) {
        mLog = deps.getSharedLog().forSubComponent(TAG);
        mNetd = deps.getNetd();
    }

    @Override
    public boolean isInitialized() {
        return true;
    };

    @Override
    public boolean addIpv6UpstreamRule(@NonNull final Ipv6UpstreamRule rule) {
        return true;
    };

    @Override
    public boolean removeIpv6UpstreamRule(@NonNull final Ipv6UpstreamRule rule) {
        return true;
    }

    @Override
    public boolean addIpv6DownstreamRule(@NonNull final Ipv6DownstreamRule rule) {
        try {
            mNetd.tetherOffloadRuleAdd(rule.toTetherOffloadRuleParcel());
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Could not add IPv6 forwarding rule: ", e);
            return false;
        }

        return true;
    };

    @Override
    public boolean removeIpv6DownstreamRule(@NonNull final Ipv6DownstreamRule rule) {
        try {
            mNetd.tetherOffloadRuleRemove(rule.toTetherOffloadRuleParcel());
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Could not remove IPv6 forwarding rule: ", e);
            return false;
        }
        return true;
    }

    @Override
    @Nullable
    public SparseArray<TetherStatsValue> tetherOffloadGetStats() {
        final TetherStatsParcel[] tetherStatsList;
        try {
            // The reported tether stats are total data usage for all currently-active upstream
            // interfaces since tethering start. There will only ever be one entry for a given
            // interface index.
            tetherStatsList = mNetd.tetherOffloadGetStats();
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Fail to fetch tethering stats from netd: " + e);
            return null;
        }

        return toTetherStatsValueSparseArray(tetherStatsList);
    }

    @Override
    public boolean tetherOffloadSetInterfaceQuota(int ifIndex, long quotaBytes) {
        try {
            mNetd.tetherOffloadSetInterfaceQuota(ifIndex, quotaBytes);
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Exception when updating quota " + quotaBytes + ": ", e);
            return false;
        }
        return true;
    }

    @NonNull
    private SparseArray<TetherStatsValue> toTetherStatsValueSparseArray(
            @NonNull final TetherStatsParcel[] parcels) {
        final SparseArray<TetherStatsValue> tetherStatsList = new SparseArray<TetherStatsValue>();

        for (TetherStatsParcel p : parcels) {
            tetherStatsList.put(p.ifIndex, new TetherStatsValue(p.rxPackets, p.rxBytes,
                    0 /* rxErrors */, p.txPackets, p.txBytes, 0 /* txErrors */));
        }

        return tetherStatsList;
    }

    @Override
    @Nullable
    public TetherStatsValue tetherOffloadGetAndClearStats(int ifIndex) {
        try {
            final TetherStatsParcel stats =
                    mNetd.tetherOffloadGetAndClearStats(ifIndex);
            return new TetherStatsValue(stats.rxPackets, stats.rxBytes, 0 /* rxErrors */,
                    stats.txPackets, stats.txBytes, 0 /* txErrors */);
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Exception when cleanup tether stats for upstream index "
                    + ifIndex + ": ", e);
            return null;
        }
    }

    @Override
    public boolean tetherOffloadRuleAdd(boolean downstream, @NonNull Tether4Key key,
            @NonNull Tether4Value value) {
        /* no op */
        return true;
    }

    @Override
    public boolean tetherOffloadRuleRemove(boolean downstream, @NonNull Tether4Key key) {
        /* no op */
        return true;
    }

    @Override
    public void tetherOffloadRuleForEach(boolean downstream,
            @NonNull ThrowingBiConsumer<Tether4Key, Tether4Value> action) {
        /* no op */
    }

    @Override
    public boolean attachProgram(String iface, boolean downstream, boolean ipv4) {
        /* no op */
        return true;
    }

    @Override
    public boolean detachProgram(String iface, boolean ipv4) {
        /* no op */
        return true;
    }

    @Override
    public boolean isAnyIpv4RuleOnUpstream(int ifIndex) {
        /* no op */
        return false;
    }

    @Override
    public boolean addDevMap(int ifIndex) {
        /* no op */
        return false;
    }

    @Override
    public boolean removeDevMap(int ifIndex) {
        /* no op */
        return false;
    }

    @Override
    public String toString() {
        return "Netd used";
    }
}
