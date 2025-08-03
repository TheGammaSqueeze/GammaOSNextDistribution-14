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
import android.app.usage.NetworkStats;
import android.os.PersistableBundle;

import com.android.car.internal.util.IntArray;
import com.android.car.internal.util.LongArray;
import com.android.car.telemetry.UidPackageMapper;
import com.android.car.telemetry.publisher.Constants;

/**
 * Restructured {@link NetworkStats} to simplify subtract/add arithmetics, as well as converting
 * into the final {@link PersistableBundle} to publish to the scripts. It converts the given array
 * of {@link NetworkStats.Bucket} into a map, where uid/tag are the keys, and rxBytes/txBytes are
 * values. All the values are stored in arrays for performance.
 *
 * <p>It's similar to the hidden {@link android.net.NetworkStats} class.
 *
 * <p>Not thread-safe.
 */
public class RefinedStats {
    // The size of all the following arrays are the same.
    // Keys (called dimensions in some cases). mUid/mTag pairs are unique.
    private final IntArray mUid = new IntArray();
    private final IntArray mTag = new IntArray();

    // Values.
    private final LongArray mRxBytes = new LongArray();
    private final LongArray mTxBytes = new LongArray();

    // NetworkStats time range.
    private final long mStartMillis;
    private final long mEndMillis;

    public RefinedStats(long startMillis, long endMillis) {
        mStartMillis = startMillis;
        mEndMillis = endMillis;
    }

    /** Adds {@link NetworkStats}. Mutates the current object. */
    public void addNetworkStats(@NonNull NetworkStatsWrapper stats) {
        while (stats.hasNextBucket()) {
            NetworkStats.Bucket bucket = stats.getNextBucket();
            int index = findIndex(bucket.getUid(), bucket.getTag());
            if (index == -1) {
                mUid.add(bucket.getUid());
                mTag.add(bucket.getTag());
                mRxBytes.add(0);
                mTxBytes.add(0);
                index = mUid.size() - 1;
            }
            mRxBytes.set(index, mRxBytes.get(index) + bucket.getRxBytes());
            mTxBytes.set(index, mTxBytes.get(index) + bucket.getTxBytes());
        }
    }

    /**
     * Subtracts "right" from the "left". The resulting {@link RefinedStats} contains timestamps of
     * the "left" argument, and keys are the same as the keys of the "left" argument, and values are
     * the subtraction of the matching keys of "right" from "left".
     *
     * <p>It doesn't mutate the arguments.
     */
    @NonNull
    public static RefinedStats subtract(@NonNull RefinedStats left, @NonNull RefinedStats right) {
        RefinedStats result = new RefinedStats(left.mStartMillis, left.mEndMillis);
        // NOTE: If there items in right that don't exist in the left, just ignore them.
        // TODO(b/218529196): improve performance, see android.net.NetworkStats for examples.
        for (int l = 0; l < left.mUid.size(); l++) {
            int rIndex = right.findIndex(left.mUid.get(l), left.mTag.get(l));
            result.mUid.add(left.mUid.get(l));
            result.mTag.add(left.mTag.get(l));
            if (rIndex == -1) { // nothing to subtract
                result.mRxBytes.add(left.mRxBytes.get(l));
                result.mTxBytes.add(left.mTxBytes.get(l));
                continue;
            }
            result.mRxBytes.add(Math.max(left.mRxBytes.get(l) - right.mRxBytes.get(rIndex), 0));
            result.mTxBytes.add(Math.max(left.mTxBytes.get(l) - right.mTxBytes.get(rIndex), 0));
        }
        return result;
    }

    /** Returns the index matching the arguments. Returns {@code -1} if not found. */
    private int findIndex(int uid, int tag) {
        for (int i = 0; i < mUid.size(); i++) {
            if (mUid.get(i) == uid && mTag.get(i) == tag) {
                return i;
            }
        }
        return -1;
    }

    /** Returns true if the stats is empty. */
    public boolean isEmpty() {
        return mUid.size() == 0;
    }

    /**
     * Converts the {@link RefinedStats} object to {@link PersistableBundle}. It also combines
     * dimensions {@code set}, {@code metered}, {@code roaming} and {@code defaultNetwork}, leaving
     * only {@code uid} and {@code tag}.
     *
     * <p>Schema: <code>
     *   PersistableBundle[{
     *     startMillis = 1640779280000,
     *     endMillis = 1640779380000,
     *     size  = 3,
     *     uid = IntArray[0, 1200, 12345],
     *     packages = StringArray["root", "", "com.android.car,com.android.settings"],
     *     tag = IntArray[0, 0, 5555],
     *     rxBytes = LongArray[0, 0, 0],
     *     txBytes = LongArray[0, 0, 0],
     *   }]
     * </code> Where "startMillis" and "endMillis" are timestamps (wall clock) since epoch of time
     * range when the data is collected; field "size" is the length of "uid", "tag", "rxBytes",
     * "txBytes" arrays; fields "uid" and "tag" are dimensions; fields "rxBytes", "txBytes" are
     * received and transmitted bytes for given dimensions; field "packages" contains the comma
     * separated package names of the apps running in the given uid.
     *
     * <p>It's possible to add more data and dimensions to the result. Please see
     * {@link NetworkStats#Bucket} class to see what's available. Some information can be found in
     * b/223297091.
     *
     * <p>"tag" field may contain "0" {@link NetworkStats.TAG_NONE}, which is the total value across
     * all the tags. These stats contain both tagged and untagged network usage.
     *
     * <p>The result of this method is similar to the hidden {@code
     * android.net.NetworkStats#groupedByIface()} method.
     */
    @NonNull
    public PersistableBundle toPersistableBundle(@NonNull UidPackageMapper uidMapper) {
        PersistableBundle data = new PersistableBundle();
        data.putLong(Constants.CONNECTIVITY_BUNDLE_KEY_START_MILLIS, mStartMillis);
        data.putLong(Constants.CONNECTIVITY_BUNDLE_KEY_END_MILLIS, mEndMillis);
        data.putInt(Constants.CONNECTIVITY_BUNDLE_KEY_SIZE, mUid.size());
        // TODO(b/218596960): send empty array anyway for data schema consistency.
        if (mUid.size() > 0) {
            data.putIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_UID, mUid.toArray());
            String[] packages = new String[mUid.size()];
            for (int i = 0; i < mUid.size(); i++) {
                packages[i] = String.join(",", uidMapper.getPackagesForUid(mUid.get(i)));
            }
            data.putStringArray(Constants.CONNECTIVITY_BUNDLE_KEY_PACKAGES, packages);
            data.putIntArray(Constants.CONNECTIVITY_BUNDLE_KEY_TAG, mTag.toArray());
            data.putLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_RX_BYTES, mRxBytes.toArray());
            data.putLongArray(Constants.CONNECTIVITY_BUNDLE_KEY_TX_BYTES, mTxBytes.toArray());
        }
        return data;
    }
}
