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

package com.android.server.sdksandbox;

import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class for methods used to store and fetch SDK storage information
 *
 * @hide
 */
public class SandboxesStorageMetrics {
    private static final int MAX_ENTRIES_PER_UID = 5;
    public final Object mLock = new Object();

    @GuardedBy("mLock")
    public final SparseArray<List<SandboxMetrics>> mMetrics = new SparseArray<>();

    /** Store SDK storage info for UID */
    public void log(int uid, int sharedStorageKb, int sdkStorageKb) {
        List<SandboxMetrics> sandboxMetrics;
        synchronized (mLock) {
            sandboxMetrics = mMetrics.get(uid);
            if (sandboxMetrics == null) {
                sandboxMetrics = new ArrayList<>();
                mMetrics.append(uid, sandboxMetrics);
            }
            sandboxMetrics.add(new SandboxMetrics(sharedStorageKb, sdkStorageKb));
        }

        /**
         * If there are more metrics data points than we want, we remove the one that was added at
         * the beginning of the list.
         */
        if (sandboxMetrics.size() > MAX_ENTRIES_PER_UID) {
            sandboxMetrics.remove(0);
        }
    }

    /** Class to store the private and shared metrics from a single log of a single sandbox. */
    private static class SandboxMetrics {
        public int mSharedKb;
        public int mPrivateKb;

        SandboxMetrics(int sharedKb, int privateKb) {
            mSharedKb = sharedKb;
            mPrivateKb = privateKb;
        }
    }

    /** Collects the SDK storage metrics information into an object */
    public List<StorageStatsEvent> consumeStorageStatsEvents() {
        List<StorageStatsEvent> sandboxStorageStatsEvents = new ArrayList<>();
        synchronized (mLock) {
            final int metricsSize = mMetrics.size();
            for (int i = 0; i < metricsSize; i++) {
                final List<SandboxMetrics> sandboxMetrics = mMetrics.valueAt(i);

                for (SandboxMetrics sandboxMetric : sandboxMetrics) {
                    sandboxStorageStatsEvents.add(
                            new StorageStatsEvent(
                                    /*shared=*/ true, sandboxMetric.mSharedKb, mMetrics.keyAt(i)));
                    sandboxStorageStatsEvents.add(
                            new StorageStatsEvent(
                                    /*shared=*/ false,
                                    sandboxMetric.mPrivateKb,
                                    mMetrics.keyAt(i)));
                }
            }
            mMetrics.clear();
            return sandboxStorageStatsEvents;
        }
    }

    /** Class to store the parameters of buildStatsEvent for SANDBOX_SDK_STORAGE. */
    static class StorageStatsEvent {
        public boolean mShared;
        public int mStorageKb;
        public int mUid;

        StorageStatsEvent(boolean shared, int storageKb, int uid) {
            mShared = shared;
            mStorageKb = storageKb;
            mUid = uid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StorageStatsEvent)) return false;
            StorageStatsEvent that = (StorageStatsEvent) o;
            return mShared == that.mShared && mStorageKb == that.mStorageKb && mUid == that.mUid;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mShared, mStorageKb, mUid);
        }
    }
}
