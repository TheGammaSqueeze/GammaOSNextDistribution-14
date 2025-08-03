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

package android.os.profiling;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.os.ProfilingRequest;
import android.os.ProfilingResult;
import android.provider.DeviceConfig;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;

public class RateLimiter {

    private static final String DEVICE_CONFIG_NAMESPACE = "profiling";
    private static final String DEVICE_CONFIG_RATE_LIMITER_DISABLE_PROPERTY
            = "rate_limiter.disabled";

    private static final long TIME_1_HOUR_MS = 60 * 60 * 1000;
    private static final long TIME_24_HOUR_MS = 24 * 60 * 60 * 1000;
    private static final long TIME_7_DAY_MS = 7 * 24 * 60 * 60 * 1000;

    public static final int RATE_LIMIT_RESULT_ALLOWED = 0;
    public static final int RATE_LIMIT_RESULT_BLOCKED_PROCESS = 1;
    public static final int RATE_LIMIT_RESULT_BLOCKED_SYSTEM = 2;

    private final Object mLock = new Object();

    private final Context mContext;
    private final long mPersistToDiskFrequency;

    /** To be disabled for testing only. */
    @GuardedBy("mLock")
    private boolean mRateLimiterEnabled = true;

    /** Collection of run costs and entries from the last hour. */
    private final EntryGroupWrapper mPastRuns1Hour;
    /** Collection of run costs and entries from the 24 hours. */
    private final EntryGroupWrapper mPastRuns24Hour;
    /** Collection of run costs and entries from the 7 days. */
    private final EntryGroupWrapper mPastRuns7Day;

    private long mLastPersistedTimestampMs;

    @IntDef(value={
        RATE_LIMIT_RESULT_ALLOWED,
        RATE_LIMIT_RESULT_BLOCKED_PROCESS,
        RATE_LIMIT_RESULT_BLOCKED_SYSTEM,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface RateLimitResult {}

    public RateLimiter(Context context) {
        // TODO: b/324885858 use DeviceConfig for adjustable values.
        mContext = context;
        mPastRuns1Hour = new EntryGroupWrapper(10, 10, TIME_1_HOUR_MS);
        mPastRuns24Hour = new EntryGroupWrapper(100, 100, TIME_24_HOUR_MS);
        mPastRuns7Day = new EntryGroupWrapper(1000, 1000, TIME_7_DAY_MS);
        mPersistToDiskFrequency = 0;
        mLastPersistedTimestampMs = System.currentTimeMillis();
        loadFromDisk();

        // Get initial value for whether rate limiter should be enforcing or if it should always
        // allow profiling requests. This is used for (automated and manual) testing only.
        synchronized (mLock) {
            mRateLimiterEnabled = !DeviceConfig.getBoolean(DEVICE_CONFIG_NAMESPACE,
                DEVICE_CONFIG_RATE_LIMITER_DISABLE_PROPERTY, false);
        }
        // Now subscribe to updates on rate limiter enforcing config.
        DeviceConfig.addOnPropertiesChangedListener(DEVICE_CONFIG_NAMESPACE,
                mContext.getMainExecutor(), new DeviceConfig.OnPropertiesChangedListener() {
                    @Override
                    public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
                        synchronized (mLock) {
                            mRateLimiterEnabled = properties.getBoolean(
                                    DEVICE_CONFIG_RATE_LIMITER_DISABLE_PROPERTY, false);
                        }
                    }
                });
    }

    public @RateLimitResult int isProfilingRequestAllowed(int uid,
            ProfilingRequest request) {
        synchronized (mLock) {
            if (!mRateLimiterEnabled) {
                // Rate limiter is disabled for testing, approve request and don't store cost.
                return RATE_LIMIT_RESULT_ALLOWED;
            }
            final int cost = 1; // TODO: compute cost b/293957254
            final long currentTimeMillis = System.currentTimeMillis();
            int status = mPastRuns1Hour.isProfilingAllowed(uid, cost, currentTimeMillis);
            if (status == RATE_LIMIT_RESULT_ALLOWED) {
                status = mPastRuns24Hour.isProfilingAllowed(uid, cost, currentTimeMillis);
            }
            if (status == RATE_LIMIT_RESULT_ALLOWED) {
                status = mPastRuns7Day.isProfilingAllowed(uid, cost, currentTimeMillis);
            }
            if (status == RATE_LIMIT_RESULT_ALLOWED) {
                mPastRuns1Hour.add(uid, cost, currentTimeMillis);
                mPastRuns24Hour.add(uid, cost, currentTimeMillis);
                mPastRuns7Day.add(uid, cost, currentTimeMillis);
                maybePersistToDisk();
                return RATE_LIMIT_RESULT_ALLOWED;
            }
            return status;
        }
    }

    void maybePersistToDisk() {
        if (mPersistToDiskFrequency == 0
                || System.currentTimeMillis() - mLastPersistedTimestampMs
                >= mPersistToDiskFrequency) {
            persistToDisk();
        } else {
            // TODO: queue persist job b/293957254
        }
    }

    void persistToDisk() {
        // TODO: b/293957254
    }

    void loadFromDisk() {
        // TODO: b/293957254
    }

    static int statusToResult(@RateLimitResult int resultStatus) {
        switch (resultStatus) {
            case RATE_LIMIT_RESULT_BLOCKED_PROCESS:
                return ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS;
            case RATE_LIMIT_RESULT_BLOCKED_SYSTEM:
                return ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM;
            default:
                return ProfilingResult.ERROR_UNKNOWN;
        }
    }

    final static class EntryGroupWrapper {
        final Queue<CollectionEntry> mEntries;
        int mTotalCost;
        // uid indexed
        final SparseIntArray mPerUidCost;
        final int mMaxCost;
        final int mMaxCostPerUid;
        final long mTimeRangeMs;

        EntryGroupWrapper(final int maxCost, final int maxPerUidCost, final long timeRangeMs) {
            mMaxCost = maxCost;
            mMaxCostPerUid = maxPerUidCost;
            mTimeRangeMs = timeRangeMs;
            mEntries = new ArrayDeque<>();
            mPerUidCost = new SparseIntArray();
        }

        void add(final int uid, final int cost, final long timestamp) {
            mTotalCost += cost;
            final int index = mPerUidCost.indexOfKey(uid);
            if (index < 0) {
                mPerUidCost.put(uid, cost);
            } else {
                mPerUidCost.put(uid, mPerUidCost.valueAt(index) + cost);
            }
            mEntries.offer(new CollectionEntry(uid, cost, timestamp));
        }

        /**
         * Clean up the queue by removing entries that are too old.
         *
         * @param olderThanTimestamp timestamp to remove record which are older than.
         */
        void removeOlderThan(final long olderThanTimestamp) {
            while (mEntries.peek() != null && mEntries.peek().mTimestamp <= olderThanTimestamp) {
                final CollectionEntry entry = mEntries.poll();
                if (entry == null) {
                    return;
                }
                mTotalCost -= entry.mCost;
                if (mTotalCost < 0) {
                    mTotalCost = 0;
                }
                final int index = mPerUidCost.indexOfKey(entry.mUid);
                if (index >= 0) {
                    mPerUidCost.setValueAt(index, mPerUidCost.valueAt(index) - entry.mCost);
                }
            }
        }

        /**
         * Check if the requested profiling is allowed by the limits of this collection after
         * ensuring the collection is up to date.
         *
         * @param uid of requesting process
         * @param cost calculated perf cost of running this query
         * @param currentTimeMillis cache time and keep consistent across checks
         * @return status indicating whether request is allowed, or which rate limiting applied to
         *         deny it.
         */
        @RateLimitResult int isProfilingAllowed(final int uid, final int cost,
                final long currentTimeMillis) {
            removeOlderThan(currentTimeMillis - mTimeRangeMs);
            if (mTotalCost + cost > mMaxCost) {
                return RATE_LIMIT_RESULT_BLOCKED_SYSTEM;
            }
            final int index = mPerUidCost.indexOfKey(uid);
            return ((index < 0 ? 0 : mPerUidCost.valueAt(index)) + cost < mMaxCostPerUid)
                    ? RATE_LIMIT_RESULT_ALLOWED : RATE_LIMIT_RESULT_BLOCKED_PROCESS;
        }
    }
    final static class CollectionEntry {
        final int mUid;
        final int mCost;
        final Long mTimestamp;

        CollectionEntry(final int uid, final int cost, final Long timestamp) {
            mUid = uid;
            mCost = cost;
            mTimestamp = timestamp;
        }
    }
}