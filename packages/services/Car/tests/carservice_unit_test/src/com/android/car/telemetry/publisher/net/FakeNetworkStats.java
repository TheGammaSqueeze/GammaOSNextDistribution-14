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

import android.app.usage.NetworkStats;
import android.net.NetworkIdentity;

import java.util.ArrayDeque;

/** A fake for {@link NetworkStats}. */
public class FakeNetworkStats extends NetworkStatsWrapper {
    private final ArrayDeque<NetworkStats.Bucket> mBuckets = new ArrayDeque<>();

    public FakeNetworkStats() {
        super(/* networkStats= */ null);
    }

    @Override
    public boolean hasNextBucket() {
        return !mBuckets.isEmpty();
    }

    @Override
    public NetworkStats.Bucket getNextBucket() {
        if (mBuckets.isEmpty()) {
            return null;
        }
        return mBuckets.removeFirst();
    }

    /** Adds the bucket to the fake. */
    public void add(NetworkStats.Bucket bucket) {
        mBuckets.addLast(bucket);
    }

    /**
     * A custom implementation of {@link NetworkStats.Bucket} for testing purpose. This class
     * overrides getter methods, because the original class doesn't allow setting the fields.
     */
    public static class CustomBucket extends NetworkStats.Bucket {
        private final NetworkIdentity mIdentity;
        private final int mUid;
        private final int mTag;
        private final long mRxBytes;
        private final long mTxBytes;
        private final long mTimestampMillis;

        public CustomBucket(
                NetworkIdentity identity,
                int uid,
                int tag,
                long rxBytes,
                long txBytes,
                long timestampMillis) {
            mIdentity = identity;
            mUid = uid;
            mTag = tag;
            mRxBytes = rxBytes;
            mTxBytes = txBytes;
            mTimestampMillis = timestampMillis;
        }

        public NetworkIdentity getIdentity() {
            return mIdentity;
        }

        @Override
        public int getUid() {
            return mUid;
        }

        @Override
        public int getTag() {
            return mTag;
        }

        @Override
        public long getRxBytes() {
            return mRxBytes;
        }

        @Override
        public long getTxBytes() {
            return mTxBytes;
        }

        @Override
        public long getStartTimeStamp() {
            return mTimestampMillis;
        }

        @Override
        public long getEndTimeStamp() {
            return mTimestampMillis;
        }
    }
}
