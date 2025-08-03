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
package com.android.server.wifi.entitlement;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;

/** The Imsi Pseudonym information*/
public class PseudonymInfo {
    @VisibleForTesting
    static final long DEFAULT_PSEUDONYM_TTL_IN_MILLIS = Duration.ofDays(2).toMillis();
    @VisibleForTesting
    static final long REFRESH_AHEAD_TIME_IN_MILLIS = Duration.ofMinutes(30).toMillis();
    static final long MINIMUM_REFRESH_INTERVAL_IN_MILLIS = Duration.ofHours(12).toMillis();

    private final String mImsi;
    private final String mPseudonym;

    /*
     * The number of milliseconds from the epoch of 1970-01-01T00:00:00Z when the pseudonym is
     * received.
     */
    private final long mTimeStamp;

    /*
     * Time To Live in milliseconds from the pseudonym is received. This is the maximum lifetime of
     * a pseudonym. The pseudonym remains valid from the time it is received, until the mTtlInMillis
     * has elapsed.
     */
    private final long mTtlInMillis;

    /*
     * Refresh Ahead Time in milliseconds. When a pseudonym is expiring, we should refresh it ahead
     * of time. For example, we refresh a pseudonym half an hour before it expires. If the TTL is 24
     * hours, we should refresh this pseudonym when it is 23.5 hours old.
     */
    private final long mRatInMillis;

    /*
     * Minimum Age To Refresh in milliseconds from the pseudonym is received. We should not
     * refresh the pseudonym too frequently.
     */
    private final long mMinAtrInMillis;

    public PseudonymInfo(@NonNull String pseudonym, @NonNull String imsi) {
        this(pseudonym, imsi, DEFAULT_PSEUDONYM_TTL_IN_MILLIS);
    }

    public PseudonymInfo(@NonNull String pseudonym, @NonNull String imsi, long ttlInMillis) {
        this(pseudonym, imsi, ttlInMillis, Instant.now().toEpochMilli());
    }

    @VisibleForTesting
    public PseudonymInfo(@NonNull String pseudonym, @NonNull String imsi, long ttlInMillis,
            long timeStamp) {
        mPseudonym = pseudonym;
        mImsi = imsi;
        mTimeStamp = timeStamp;
        mTtlInMillis = ttlInMillis;
        mRatInMillis = Math.min(ttlInMillis / 2, REFRESH_AHEAD_TIME_IN_MILLIS);
        mMinAtrInMillis = Math.min(ttlInMillis - mRatInMillis, MINIMUM_REFRESH_INTERVAL_IN_MILLIS);
    }

    public String getPseudonym() {
        return mPseudonym;
    }

    public String getImsi() {
        return mImsi;
    }

    /**
     * Returns the Time To Live in milliseconds.
     */
    public long getTtlInMillis() {
        return mTtlInMillis;
    }

    /** Returns the Left Time To Refresh in milliseconds. */
    public long getLttrInMillis() {
        long age = Instant.now().toEpochMilli() - mTimeStamp;
        long leftTimeToLive = mTtlInMillis - age;
        long leftTimeToRefresh = leftTimeToLive - mRatInMillis;
        return leftTimeToRefresh > 0 ? leftTimeToRefresh : 0;
    }

    /**
     * Returns whether the pseudonym has expired or not.
     */
    public boolean hasExpired() {
        return Instant.now().toEpochMilli() - mTimeStamp >= mTtlInMillis;
    }

    /**
     * Returns whether the pseudonym is old enough to refresh. To prevent DOS attack, the pseudonym
     * should not be refreshed too frequently.
     */
    public boolean isOldEnoughToRefresh() {
        return Instant.now().toEpochMilli() - mTimeStamp >= mMinAtrInMillis;
    }

    @Override
    public String toString() {
        // Mask the pseudonym and IMSI which are two kinds of PII.
        return " mPseudonym="
                + (mPseudonym.length() >= 7 ? (mPseudonym.substring(0, 7) + "***") : mPseudonym)
                + " mImsi=***"
                + (mImsi.length() >= 3 ? mImsi.substring(mImsi.length() - 3) : mImsi)
                + " mTimeStamp="
                + mTimeStamp
                + " mTtlInMillis="
                + mTtlInMillis
                + " mRatInMillis="
                + mRatInMillis
                + " mMinAtrInMillis="
                + mMinAtrInMillis;
    }
}
