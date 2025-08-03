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

package com.android.networkstack.tethering.apishim.common;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.IBpfMap.ThrowingBiConsumer;
import com.android.net.module.util.bpf.Tether4Key;
import com.android.net.module.util.bpf.Tether4Value;
import com.android.net.module.util.bpf.TetherStatsValue;
import com.android.networkstack.tethering.BpfCoordinator.Dependencies;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6DownstreamRule;
import com.android.networkstack.tethering.BpfCoordinator.Ipv6UpstreamRule;

/**
 * Bpf coordinator class for API shims.
 */
public abstract class BpfCoordinatorShim {
    /**
     * Get BpfCoordinatorShim object by OS build version.
     */
    @NonNull
    public static BpfCoordinatorShim getBpfCoordinatorShim(@NonNull final Dependencies deps) {
        if (deps.isAtLeastS()) {
            return new com.android.networkstack.tethering.apishim.api31.BpfCoordinatorShimImpl(
                    deps);
        } else {
            return new com.android.networkstack.tethering.apishim.api30.BpfCoordinatorShimImpl(
                    deps);
        }
    }

    /**
     * Return true if this class has been initialized, otherwise return false.
     */
    public abstract boolean isInitialized();

    /**
     * Adds a tethering offload upstream rule to BPF map, or updates it if it already exists.
     *
     * An existing rule will be updated if the input interface, destination MAC and source prefix
     * match. Otherwise, a new rule will be created. Note that this can be only called on handler
     * thread.
     *
     * @param rule The rule to add or update.
     * @return true if operation succeeded or was a no-op, false otherwise.
     */
    public abstract boolean addIpv6UpstreamRule(@NonNull Ipv6UpstreamRule rule);

    /**
     * Deletes a tethering offload upstream rule from the BPF map.
     *
     * An existing rule will be deleted if the input interface, destination MAC and source prefix
     * match. It is not an error if there is no matching rule to delete.
     *
     * @param rule The rule to delete.
     * @return true if operation succeeded or was a no-op, false otherwise.
     */
    public abstract boolean removeIpv6UpstreamRule(@NonNull Ipv6UpstreamRule rule);

    /**
     * Adds a tethering offload downstream rule to BPF map, or updates it if it already exists.
     *
     * Currently, only downstream /128 IPv6 entries are supported. An existing rule will be updated
     * if the input interface and destination prefix match. Otherwise, a new rule will be created.
     * Note that this can be only called on handler thread.
     *
     * @param rule The rule to add or update.
     * @return true if operation succeeded or was a no-op, false otherwise.
     */
    public abstract boolean addIpv6DownstreamRule(@NonNull Ipv6DownstreamRule rule);

    /**
     * Deletes a tethering offload downstream rule from the BPF map.
     *
     * Currently, only downstream /128 IPv6 entries are supported. An existing rule will be deleted
     * if the destination IP address and the source interface match. It is not an error if there is
     * no matching rule to delete.
     *
     * @param rule The rule to delete.
     * @return true if operation succeeded or was a no-op, false otherwise.
     */
    public abstract boolean removeIpv6DownstreamRule(@NonNull Ipv6DownstreamRule rule);

    /**
     * Return BPF tethering offload statistics.
     *
     * @return an array of TetherStatsValue's, where each entry contains the upstream interface
     *         index and its tethering statistics since tethering was first started.
     *         There will only ever be one entry for a given interface index.
     */
    @Nullable
    public abstract SparseArray<TetherStatsValue> tetherOffloadGetStats();

   /**
    * Set a per-interface quota for tethering offload.
    *
    * @param ifIndex Index of upstream interface
    * @param quotaBytes The quota defined as the number of bytes, starting from zero and counting
    *       from *now*. A value of QUOTA_UNLIMITED (-1) indicates there is no limit.
    */
    @Nullable
    public abstract boolean tetherOffloadSetInterfaceQuota(int ifIndex, long quotaBytes);

    /**
     * Return BPF tethering offload statistics and clear the stats for a given upstream.
     *
     * Must only be called once all offload rules have already been deleted for the given upstream
     * interface. The existing stats will be fetched and returned. The stats and the limit for the
     * given upstream interface will be deleted as well.
     *
     * The stats and limit for a given upstream interface must be initialized (using
     * tetherOffloadSetInterfaceQuota) before any offload will occur on that interface.
     *
     * Note that this can be only called while the BPF maps were initialized.
     *
     * @param ifIndex Index of upstream interface.
     * @return TetherStatsValue, which contains the given upstream interface's tethering statistics
     *         since tethering was first started on that upstream interface.
     */
    @Nullable
    public abstract TetherStatsValue tetherOffloadGetAndClearStats(int ifIndex);

    /**
     * Adds a tethering IPv4 offload rule to appropriate BPF map.
     */
    public abstract boolean tetherOffloadRuleAdd(boolean downstream, @NonNull Tether4Key key,
            @NonNull Tether4Value value);

    /**
     * Deletes a tethering IPv4 offload rule from the appropriate BPF map.
     *
     * @param downstream true if downstream, false if upstream.
     * @param key the key to delete.
     * @return true iff the map was modified, false if the key did not exist or there was an error.
     */
    public abstract boolean tetherOffloadRuleRemove(boolean downstream, @NonNull Tether4Key key);

    /**
     * Iterate through the map and handle each key -> value retrieved base on the given BiConsumer.
     *
     * @param downstream true if downstream, false if upstream.
     * @param action represents the action for each key -> value. The entry deletion is not
     *        allowed and use #tetherOffloadRuleRemove instead.
     */
    @Nullable
    public abstract void tetherOffloadRuleForEach(boolean downstream,
            @NonNull ThrowingBiConsumer<Tether4Key, Tether4Value> action);

    /**
     * Whether there is currently any IPv4 rule on the specified upstream.
     */
    public abstract boolean isAnyIpv4RuleOnUpstream(int ifIndex);

    /**
     * Attach BPF program.
     *
     * @param iface the interface name to attach program.
     * @param downstream indicate the datapath. true if downstream, false if upstream.
     * @param ipv4 indicate the protocol family. true if ipv4, false if ipv6.
     *
     * TODO: consider using InterfaceParams to replace interface name.
     */
    public abstract boolean attachProgram(@NonNull String iface, boolean downstream,
            boolean ipv4);

    /**
     * Detach BPF program.
     *
     * @param iface the interface name to detach program.
     * @param ipv4 indicate the protocol family. true if ipv4, false if ipv6.
     *
     * TODO: consider using InterfaceParams to replace interface name.
     */
    public abstract boolean detachProgram(@NonNull String iface, boolean ipv4);

    /**
     * Add interface index mapping.
     */
    public abstract boolean addDevMap(int ifIndex);

    /**
     * Remove interface index mapping.
     */
    public abstract boolean removeDevMap(int ifIndex);
}

