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

import static org.junit.Assert.assertEquals;

import android.net.apf.ApfCounterTracker.Counter;
import android.stats.connectivity.CounterName;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for ApfSessionInfoMetrics.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ApfSessionInfoMetricsTest {
    @Test
    public void testApfSessionInfoMetrics_VerifyCollectMetrics() throws Exception {
        ApfSessionInfoReported mStats;
        final ApfSessionInfoMetrics mMetrics = new ApfSessionInfoMetrics();
        mMetrics.setVersion(4);
        mMetrics.setMemorySize(4096);
        mMetrics.setApfSessionDurationSeconds(123);
        mMetrics.setNumOfTimesApfProgramUpdated(456);
        mMetrics.setMaxProgramSize(1234);
        mMetrics.addApfCounter(Counter.TOTAL_PACKETS, 5678);
        mMetrics.addApfCounter(Counter.PASSED_ARP_UNICAST_REPLY, 1010);
        mMetrics.addApfCounter(Counter.DROPPED_MDNS, 333);
        mStats = mMetrics.statsWrite();
        assertEquals(4, mStats.getVersion());
        assertEquals(4096, mStats.getMemorySize());
        assertEquals(123, mStats.getApfSessionDurationSeconds());
        assertEquals(456, mStats.getNumOfTimesApfProgramUpdated());
        assertEquals(1234, mStats.getMaxProgramSize());

        // ApfCounter count: 3 (CN_TOTAL_PACKETS, CN_PASSED_ARP_UNICAST_REPLY, CN_DROPPED_MDNS)
        final ApfCounterList apfCounterList = mStats.getApfCounterList();
        assertEquals(3, apfCounterList.getApfCounterCount());

        // Verify 1st ApfCounter: CounterName = CN_TOTAL_PACKETS, CounterValue = 5678
        ApfCounter apfCounter = apfCounterList.getApfCounter(0);
        assertEquals(CounterName.CN_TOTAL_PACKETS, apfCounter.getCounterName());
        assertEquals(5678, apfCounter.getCounterValue());

        // Verify 1st ApfCounter: CounterName = CN_PASSED_ARP_UNICAST_REPLY, CounterValue = 1010
        apfCounter = apfCounterList.getApfCounter(1);
        assertEquals(CounterName.CN_PASSED_ARP_UNICAST_REPLY, apfCounter.getCounterName());
        assertEquals(1010, apfCounter.getCounterValue());

        // Verify 1st ApfCounter: CounterName = CN_DROPPED_MDNS, CounterValue = 333
        apfCounter = apfCounterList.getApfCounter(2);
        assertEquals(CounterName.CN_DROPPED_MDNS, apfCounter.getCounterName());
        assertEquals(333, apfCounter.getCounterValue());
    }

    @Test
    public void testApfSessionInfoMetrics_VerifyMaxApfCounter() throws Exception {
        ApfSessionInfoReported mStats;
        final ApfSessionInfoMetrics mMetrics = new ApfSessionInfoMetrics();
        for (Counter counter : Counter.class.getEnumConstants()) {
            mMetrics.addApfCounter(counter, 1);
        }
        final int expectedApfCounterCount = Counter.class.getEnumConstants().length - 1;
        mStats = mMetrics.statsWrite();
        final ApfCounterList apfCounterList = mStats.getApfCounterList();
        assertEquals(expectedApfCounterCount, apfCounterList.getApfCounterCount());
    }

    private void verifyCounterName(Counter counter,
            CounterName expectedCounterName) {
        assertEquals(expectedCounterName, ApfSessionInfoMetrics.apfFilterCounterToEnum(counter));
    }

    @Test
    public void testApfSessionInfoMetrics_VerifyApfCounterToEnum() throws Exception {
        verifyCounterName(Counter.RESERVED_OOB, CounterName.CN_UNKNOWN);
        verifyCounterName(Counter.TOTAL_PACKETS, CounterName.CN_TOTAL_PACKETS);
        verifyCounterName(Counter.PASSED_ARP, CounterName.CN_PASSED_ARP);
        verifyCounterName(Counter.PASSED_DHCP, CounterName.CN_PASSED_DHCP);
        verifyCounterName(Counter.PASSED_IPV4, CounterName.CN_PASSED_IPV4);
        verifyCounterName(Counter.PASSED_IPV6_NON_ICMP, CounterName.CN_PASSED_IPV6_NON_ICMP);
        verifyCounterName(Counter.PASSED_IPV4_UNICAST,  CounterName.CN_PASSED_IPV4_UNICAST);
        verifyCounterName(Counter.PASSED_IPV6_ICMP, CounterName.CN_PASSED_IPV6_ICMP);
        verifyCounterName(Counter.PASSED_IPV6_UNICAST_NON_ICMP,
                CounterName.CN_PASSED_IPV6_UNICAST_NON_ICMP);
        verifyCounterName(Counter.PASSED_ARP_NON_IPV4, CounterName.CN_UNKNOWN);
        verifyCounterName(Counter.PASSED_ARP_UNKNOWN, CounterName.CN_UNKNOWN);
        verifyCounterName(Counter.PASSED_ARP_UNICAST_REPLY,
                CounterName.CN_PASSED_ARP_UNICAST_REPLY);
        verifyCounterName(Counter.PASSED_NON_IP_UNICAST, CounterName.CN_PASSED_NON_IP_UNICAST);
        verifyCounterName(Counter.PASSED_MDNS, CounterName.CN_PASSED_MDNS);
        verifyCounterName(Counter.DROPPED_ETH_BROADCAST, CounterName.CN_DROPPED_ETH_BROADCAST);
        verifyCounterName(Counter.DROPPED_RA, CounterName.CN_DROPPED_RA);
        verifyCounterName(Counter.DROPPED_GARP_REPLY, CounterName.CN_DROPPED_GARP_REPLY);
        verifyCounterName(Counter.DROPPED_ARP_OTHER_HOST, CounterName.CN_DROPPED_ARP_OTHER_HOST);
        verifyCounterName(Counter.DROPPED_IPV4_L2_BROADCAST,
                CounterName.CN_DROPPED_IPV4_L2_BROADCAST);
        verifyCounterName(Counter.DROPPED_IPV4_BROADCAST_ADDR,
                CounterName.CN_DROPPED_IPV4_BROADCAST_ADDR);
        verifyCounterName(Counter.DROPPED_IPV4_BROADCAST_NET,
                CounterName.CN_DROPPED_IPV4_BROADCAST_NET);
        verifyCounterName(Counter.DROPPED_IPV4_MULTICAST, CounterName.CN_DROPPED_IPV4_MULTICAST);
        verifyCounterName(Counter.DROPPED_IPV6_ROUTER_SOLICITATION,
                CounterName.CN_DROPPED_IPV6_ROUTER_SOLICITATION);
        verifyCounterName(Counter.DROPPED_IPV6_MULTICAST_NA,
                CounterName.CN_DROPPED_IPV6_MULTICAST_NA);
        verifyCounterName(Counter.DROPPED_IPV6_MULTICAST, CounterName.CN_DROPPED_IPV6_MULTICAST);
        verifyCounterName(Counter.DROPPED_IPV6_MULTICAST_PING,
                CounterName.CN_DROPPED_IPV6_MULTICAST_PING);
        verifyCounterName(Counter.DROPPED_IPV6_NON_ICMP_MULTICAST,
                CounterName.CN_DROPPED_IPV6_NON_ICMP_MULTICAST);
        verifyCounterName(Counter.DROPPED_802_3_FRAME, CounterName.CN_DROPPED_802_3_FRAME);
        verifyCounterName(Counter.DROPPED_ETHERTYPE_DENYLISTED,
                CounterName.CN_DROPPED_ETHERTYPE_DENYLISTED);
        verifyCounterName(Counter.DROPPED_ARP_REPLY_SPA_NO_HOST,
                CounterName.CN_DROPPED_ARP_REPLY_SPA_NO_HOST);
        verifyCounterName(Counter.DROPPED_IPV4_KEEPALIVE_ACK,
                CounterName.CN_DROPPED_IPV4_KEEPALIVE_ACK);
        verifyCounterName(Counter.DROPPED_IPV6_KEEPALIVE_ACK,
                CounterName.CN_DROPPED_IPV6_KEEPALIVE_ACK);
        verifyCounterName(Counter.DROPPED_IPV4_NATT_KEEPALIVE,
                CounterName.CN_DROPPED_IPV4_NATT_KEEPALIVE);
        verifyCounterName(Counter.DROPPED_MDNS, CounterName.CN_DROPPED_MDNS);
        verifyCounterName(Counter.DROPPED_IPV4_TCP_PORT7_UNICAST, CounterName.CN_UNKNOWN);
        verifyCounterName(Counter.DROPPED_ARP_NON_IPV4, CounterName.CN_DROPPED_ARP_NON_IPV4);
        verifyCounterName(Counter.DROPPED_ARP_UNKNOWN, CounterName.CN_DROPPED_ARP_UNKNOWN);
    }
}
