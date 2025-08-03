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

/**
 * Class to record the network stack IpClientRaInfo metrics into statsd.
 *
 * This class is not thread-safe, and should always be accessed from the same thread.
 *
 * @hide
 */
public class IpClientRaInfoMetrics {
    private final IpClientRaInfoReported.Builder mStatsBuilder =
            IpClientRaInfoReported.newBuilder();

    /**
     * Write the maximum number of distinct RAs into mStatsBuilder.
     */
    public void setMaxNumberOfDistinctRas(final int maxNum) {
        mStatsBuilder.setMaxNumberOfDistinctRas(maxNum);
    }

    /**
     * Write the number of zero lifetime RAs into mStatsBuilder.
     */
    public void setNumberOfZeroLifetimeRas(final int number) {
        mStatsBuilder.setNumberOfZeroLifetimeRas(number);
    }

    /**
     * Write the number of parsing error RAs into mStatsBuilder.
     */
    public void setNumberOfParsingErrorRas(final int number) {
        mStatsBuilder.setNumberOfParsingErrorRas(number);
    }

    /**
     * Write the lowest router lifetime into mStatsBuilder.
     */
    public void setLowestRouterLifetimeSeconds(final int lifetime) {
        mStatsBuilder.setLowestRouterLifetimeSeconds(lifetime);
    }

    /**
     * Write the lowest valid lifetime of PIOs into mStatsBuilder.
     */
    public void setLowestPioValidLifetimeSeconds(final long lifetime) {
        mStatsBuilder.setLowestPioValidLifetimeSeconds(lifetime);
    }

    /**
     * Write the lowest route lifetime of RIOs into mStatsBuilder.
     */
    public void setLowestRioRouteLifetimeSeconds(final long lifetime) {
        mStatsBuilder.setLowestRioRouteLifetimeSeconds(lifetime);
    }

    /**
     * Write the lowest lifetime of RDNSSs into mStatsBuilder.
     */
    public void setLowestRdnssLifetimeSeconds(final long lifetime) {
        mStatsBuilder.setLowestRdnssLifetimeSeconds(lifetime);
    }

    /**
     * Write the IpClientRaInfoReported proto into statsd.
     */
    public IpClientRaInfoReported statsWrite() {
        final IpClientRaInfoReported stats = mStatsBuilder.build();
        NetworkStackStatsLog.write(NetworkStackStatsLog.IP_CLIENT_RA_INFO_REPORTED,
                stats.getMaxNumberOfDistinctRas(),
                stats.getNumberOfZeroLifetimeRas(),
                stats.getNumberOfParsingErrorRas(),
                stats.getLowestRouterLifetimeSeconds(),
                stats.getLowestPioValidLifetimeSeconds(),
                stats.getLowestRioRouteLifetimeSeconds(),
                stats.getLowestRdnssLifetimeSeconds());
        return stats;
    }
}
