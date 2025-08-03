/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.telemetry.publisher.net;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.usage.NetworkStats;

/**
 * Wrapper for {@link NetworkStats} to improve testability, because the original {@link
 * NetworkStats#getNextBucket} uses argument as an output and the output is immutable.
 */
public class NetworkStatsWrapper {
    private final NetworkStats mNetworkStats;

    /** Wraps the given NetworkStats. */
    public NetworkStatsWrapper(@NonNull NetworkStats networkStats) {
        mNetworkStats = networkStats;
    }

    /** Returns the next bucket if exists. */
    @Nullable
    public NetworkStats.Bucket getNextBucket() {
        if (!mNetworkStats.hasNextBucket()) {
            return null;
        }
        // TODO(b/218529196): improve performance by recycling NetworkStats.Bucket.
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        mNetworkStats.getNextBucket(bucket);
        return bucket;
    }

    /** Returns true if there is a bucket. */
    public boolean hasNextBucket() {
        return mNetworkStats.hasNextBucket();
    }
}
