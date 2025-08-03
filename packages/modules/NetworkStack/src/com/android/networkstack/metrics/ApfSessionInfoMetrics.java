/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.net.apf.ApfCounterTracker.Counter;
import android.stats.connectivity.CounterName;

import androidx.annotation.VisibleForTesting;

/**
 * Class to record the network stack ApfSessionInfo metrics into statsd.
 *
 * This class is not thread-safe, and should always be accessed from the same thread.
 *
 * @hide
 */
public class ApfSessionInfoMetrics {
    // Define the maximum size of the counter list
    public static final int MAX_NUM_OF_COUNTERS = Counter.class.getEnumConstants().length - 1;
    private final ApfSessionInfoReported.Builder mStatsBuilder =
            ApfSessionInfoReported.newBuilder();
    private final ApfCounterList.Builder mApfCounterListBuilder = ApfCounterList.newBuilder();

    /**
     * Write the version to mStatsBuilder.
     */
    public void setVersion(final int version) {
        mStatsBuilder.setVersion(version);
    }

    /**
     * Write the memory size to mStatsBuilder.
     */
    public void setMemorySize(final int memorySize) {
        mStatsBuilder.setMemorySize(memorySize);
    }

    /**
     * Add an APF counter to the metrics builder.
     */
    public void addApfCounter(final Counter counter, final long value) {
        if (mApfCounterListBuilder.getApfCounterCount() >= MAX_NUM_OF_COUNTERS) return;
        final ApfCounter.Builder apfCounterBuilder = ApfCounter.newBuilder()
                .setCounterName(apfFilterCounterToEnum(counter))
                .setCounterValue(value);

        mApfCounterListBuilder.addApfCounter(apfCounterBuilder);
    }

    /**
     * Write the session duration to mStatsBuilder.
     */
    public void setApfSessionDurationSeconds(final int durationSeconds) {
        mStatsBuilder.setApfSessionDurationSeconds(durationSeconds);
    }

    /**
     * Write the number of times APF program updated to mStatsBuilder.
     */
    public void setNumOfTimesApfProgramUpdated(final int updatedTimes) {
        mStatsBuilder.setNumOfTimesApfProgramUpdated(updatedTimes);
    }

    /**
     * Write the maximum program size to mStatsBuilder.
     */
    public void setMaxProgramSize(final int programSize) {
        mStatsBuilder.setMaxProgramSize(programSize);
    }

    /**
     * Write the ApfSessionInfoReported proto into statsd.
     */
    public ApfSessionInfoReported statsWrite() {
        mStatsBuilder.setApfCounterList(mApfCounterListBuilder);
        final ApfSessionInfoReported stats = mStatsBuilder.build();
        final byte[] apfCounterList = stats.getApfCounterList().toByteArray();
        NetworkStackStatsLog.write(NetworkStackStatsLog.APF_SESSION_INFO_REPORTED,
                stats.getVersion(),
                stats.getMemorySize(),
                apfCounterList,
                stats.getApfSessionDurationSeconds(),
                stats.getNumOfTimesApfProgramUpdated(),
                stats.getMaxProgramSize());
        return stats;
    }

    /**
     *  Map ApfCounterTracker.Counter to {@link CounterName}.
     */
    @VisibleForTesting
    public static CounterName apfFilterCounterToEnum(final Counter counter) {
        switch(counter) {
            case TOTAL_PACKETS:
                return CounterName.CN_TOTAL_PACKETS;
            case PASSED_ARP:
                return CounterName.CN_PASSED_ARP;
            case PASSED_DHCP:
                return CounterName.CN_PASSED_DHCP;
            case PASSED_IPV4:
                return CounterName.CN_PASSED_IPV4;
            case PASSED_IPV6_NON_ICMP:
                return CounterName.CN_PASSED_IPV6_NON_ICMP;
            case PASSED_IPV4_UNICAST:
                return CounterName.CN_PASSED_IPV4_UNICAST;
            case PASSED_IPV6_ICMP:
                return CounterName.CN_PASSED_IPV6_ICMP;
            case PASSED_IPV6_UNICAST_NON_ICMP:
                return CounterName.CN_PASSED_IPV6_UNICAST_NON_ICMP;
            // PASSED_ARP_NON_IPV4 and PASSED_ARP_UNKNOWN were deprecated in ApfFilter:
            //     PASSED_ARP_NON_IPV4 ==> DROPPED_ARP_NON_IPV4
            //     PASSED_ARP_UNKNOWN  ==> DROPPED_ARP_UNKNOWN
            // They are not supported in the metrics.
            case PASSED_ARP_NON_IPV4:
            case PASSED_ARP_UNKNOWN:
                return CounterName.CN_UNKNOWN;
            case PASSED_ARP_UNICAST_REPLY:
                return CounterName.CN_PASSED_ARP_UNICAST_REPLY;
            case PASSED_NON_IP_UNICAST:
                return CounterName.CN_PASSED_NON_IP_UNICAST;
            case PASSED_MDNS:
                return CounterName.CN_PASSED_MDNS;
            case DROPPED_ETH_BROADCAST:
                return CounterName.CN_DROPPED_ETH_BROADCAST;
            case DROPPED_RA:
                return CounterName.CN_DROPPED_RA;
            case DROPPED_GARP_REPLY:
                return CounterName.CN_DROPPED_GARP_REPLY;
            case DROPPED_ARP_OTHER_HOST:
                return CounterName.CN_DROPPED_ARP_OTHER_HOST;
            case DROPPED_IPV4_L2_BROADCAST:
                return CounterName.CN_DROPPED_IPV4_L2_BROADCAST;
            case DROPPED_IPV4_BROADCAST_ADDR:
                return CounterName.CN_DROPPED_IPV4_BROADCAST_ADDR;
            case DROPPED_IPV4_BROADCAST_NET:
                return CounterName.CN_DROPPED_IPV4_BROADCAST_NET;
            case DROPPED_IPV4_MULTICAST:
                return CounterName.CN_DROPPED_IPV4_MULTICAST;
            case DROPPED_IPV6_ROUTER_SOLICITATION:
                return CounterName.CN_DROPPED_IPV6_ROUTER_SOLICITATION;
            case DROPPED_IPV6_MULTICAST_NA:
                return CounterName.CN_DROPPED_IPV6_MULTICAST_NA;
            case DROPPED_IPV6_MULTICAST:
                return CounterName.CN_DROPPED_IPV6_MULTICAST;
            case DROPPED_IPV6_MULTICAST_PING:
                return CounterName.CN_DROPPED_IPV6_MULTICAST_PING;
            case DROPPED_IPV6_NON_ICMP_MULTICAST:
                return CounterName.CN_DROPPED_IPV6_NON_ICMP_MULTICAST;
            case DROPPED_802_3_FRAME:
                return CounterName.CN_DROPPED_802_3_FRAME;
            case DROPPED_ETHERTYPE_DENYLISTED:
                return CounterName.CN_DROPPED_ETHERTYPE_DENYLISTED;
            case DROPPED_ARP_REPLY_SPA_NO_HOST:
                return CounterName.CN_DROPPED_ARP_REPLY_SPA_NO_HOST;
            case DROPPED_IPV4_KEEPALIVE_ACK:
                return CounterName.CN_DROPPED_IPV4_KEEPALIVE_ACK;
            case DROPPED_IPV6_KEEPALIVE_ACK:
                return CounterName.CN_DROPPED_IPV6_KEEPALIVE_ACK;
            case DROPPED_IPV4_NATT_KEEPALIVE:
                return CounterName.CN_DROPPED_IPV4_NATT_KEEPALIVE;
            case DROPPED_MDNS:
                return CounterName.CN_DROPPED_MDNS;
            case DROPPED_IPV4_TCP_PORT7_UNICAST:
                // TODO: Not supported yet in the metrics backend.
                return CounterName.CN_UNKNOWN;
            case DROPPED_ARP_NON_IPV4:
                return CounterName.CN_DROPPED_ARP_NON_IPV4;
            case DROPPED_ARP_UNKNOWN:
                return CounterName.CN_DROPPED_ARP_UNKNOWN;
            default:
                return CounterName.CN_UNKNOWN;
        }
    }
}
