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

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for IpReachabilityMonitorMetrics.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class NetworkIpReachabilityMonitorMetricsTest {
    @Test
    public void testIpReachabilityMonitorMetrics_setIpType() throws Exception {
        NetworkIpReachabilityMonitorReported mStats;
        final IpReachabilityMonitorMetrics mMetrics = new IpReachabilityMonitorMetrics();

        for (IpType ip : IpType.values()) {
            mMetrics.setNudIpType(ip);
            mStats = mMetrics.statsWrite();
            assertEquals(ip, mStats.getIpType());
        }
    }

    @Test
    public void testIpReachabilityMonitorMetrics_setNeighborType() throws Exception {
        NetworkIpReachabilityMonitorReported mStats;
        final IpReachabilityMonitorMetrics mMetrics = new IpReachabilityMonitorMetrics();

        for (NudNeighborType neighborType : NudNeighborType.values()) {
            mMetrics.setNudNeighborType(neighborType);
            mStats = mMetrics.statsWrite();
            assertEquals(neighborType, mStats.getNeighborType());
        }
    }

    @Test
    public void testIpReachabilityMonitorMetrics_setEventType() {
        NetworkIpReachabilityMonitorReported mStats;
        final IpReachabilityMonitorMetrics mMetrics = new IpReachabilityMonitorMetrics();

        for (NudEventType type : NudEventType.values()) {
            mMetrics.setNudEventType(type);
            mStats = mMetrics.statsWrite();
            assertEquals(type, mStats.getEventType());
        }
    }
}
