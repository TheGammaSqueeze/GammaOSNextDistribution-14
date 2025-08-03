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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for IpClientRaInfoMetrics.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class IpClientRaInfoMetricsTest {
    @Test
    public void testIpClientRaInfoMetrics_VerifyCollectMetrics() throws Exception {
        IpClientRaInfoReported mStats;
        final IpClientRaInfoMetrics mMetrics = new IpClientRaInfoMetrics();
        mMetrics.setMaxNumberOfDistinctRas(12);
        mMetrics.setNumberOfZeroLifetimeRas(34);
        mMetrics.setNumberOfParsingErrorRas(56);
        mMetrics.setLowestRouterLifetimeSeconds(78);
        mMetrics.setLowestPioValidLifetimeSeconds(123);
        mMetrics.setLowestRioRouteLifetimeSeconds(456);
        mMetrics.setLowestRdnssLifetimeSeconds(789);
        mStats = mMetrics.statsWrite();
        assertEquals(12, mStats.getMaxNumberOfDistinctRas());
        assertEquals(34, mStats.getNumberOfZeroLifetimeRas());
        assertEquals(56, mStats.getNumberOfParsingErrorRas());
        assertEquals(78, mStats.getLowestRouterLifetimeSeconds());
        assertEquals(123, mStats.getLowestPioValidLifetimeSeconds());
        assertEquals(456, mStats.getLowestRioRouteLifetimeSeconds());
        assertEquals(789, mStats.getLowestRdnssLifetimeSeconds());
    }
}
