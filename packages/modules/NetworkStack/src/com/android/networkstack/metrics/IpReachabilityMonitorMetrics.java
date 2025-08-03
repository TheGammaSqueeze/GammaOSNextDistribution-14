/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.networkstack.metrics;

import android.stats.connectivity.IpType;
import android.stats.connectivity.NudEventType;
import android.stats.connectivity.NudNeighborType;

/**
 * Class to record the network stack IpReachabilityMonitor metrics into statsd.
 *
 * This class is not thread-safe, and should always be accessed from the same thread.
 *
 * @hide
 */
public class IpReachabilityMonitorMetrics {
    private final NetworkIpReachabilityMonitorReported.Builder mStatsBuilder =
            NetworkIpReachabilityMonitorReported.newBuilder();

    /**
     * Write the NUD event type into mStatsBuilder.
     */
    public void setNudEventType(final NudEventType type) {
        mStatsBuilder.setEventType(type);
    }

    /**
     * Write the NUD probe type(IPv4 or IPv6) into mStatsBuilder.
     */
    public void setNudIpType(final IpType type) {
        mStatsBuilder.setIpType(type);
    }

    /**
     * Write the NUD probe neighbor type into mStatsBuilder.
     */
    public void setNudNeighborType(final NudNeighborType type) {
        mStatsBuilder.setNeighborType(type);
    }

    /**
     * Write the NetworkIpReachabilityMonitorReported proto into statsd.
     */
    public NetworkIpReachabilityMonitorReported statsWrite() {
        final NetworkIpReachabilityMonitorReported stats = mStatsBuilder.build();
        NetworkStackStatsLog.write(NetworkStackStatsLog.NETWORK_IP_REACHABILITY_MONITOR_REPORTED,
                stats.getEventType().getNumber(),
                stats.getIpType().getNumber(),
                stats.getNeighborType().getNumber());
        return stats;
    }
}
