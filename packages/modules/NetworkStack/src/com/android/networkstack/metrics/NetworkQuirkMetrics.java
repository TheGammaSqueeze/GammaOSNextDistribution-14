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

import android.stats.connectivity.NetworkQuirkEvent;

import androidx.annotation.VisibleForTesting;

/**
 * Class to record the network Quirk event into statsd.
 * @hide
 */
public class NetworkQuirkMetrics {
    private final Dependencies mDependencies;
    private final NetworkStackQuirkReported.Builder mStatsBuilder =
            NetworkStackQuirkReported.newBuilder();
    /**
     * Dependencies of {@link NetworkQuirkMetrics}, useful for testing.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * @see NetworkStackStatsLog#write.
         */
        public void writeStats(int event) {
            NetworkStackStatsLog.write(NetworkStackStatsLog.NETWORK_STACK_QUIRK_REPORTED,
                    0, event);
        }
    }

    /**
     * Get a NetworkQuirkMetrics instance.
     */
    public NetworkQuirkMetrics() {
        this(new Dependencies());
    }

    @VisibleForTesting
    public NetworkQuirkMetrics(Dependencies deps) {
        mDependencies = deps;
    }

    /**
     * Write the network Quirk Event into mStatsBuilder.
     */
    public void setEvent(NetworkQuirkEvent event) {
        mStatsBuilder.setEvent(event);
    }

    /**
     * Write the NetworkStackQuirkReported proto into statsd.
     */
    public NetworkStackQuirkReported statsWrite() {
        final NetworkStackQuirkReported stats = mStatsBuilder.build();
        mDependencies.writeStats(stats.getEvent().getNumber());
        return stats;
    }
}
