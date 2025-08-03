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

package com.android.car.audio;

import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

final class RequestIdGenerator {

    private final Object mLock = new Object();

    private final long mMaxRequests;
    @GuardedBy("mLock")
    private final ArraySet<Long> mUsedRequestIds = new ArraySet<>();
    @GuardedBy("mLock")
    private long mRequestIdCounter;

    RequestIdGenerator() {
        this(Long.MAX_VALUE);
    }

    @VisibleForTesting
    RequestIdGenerator(long maxRequests) {
        mMaxRequests = maxRequests;
    }

    long generateUniqueRequestId() {
        synchronized (mLock) {
            while (mRequestIdCounter < mMaxRequests) {
                if (mUsedRequestIds.contains(mRequestIdCounter)) {
                    mRequestIdCounter++;
                    continue;
                }

                mUsedRequestIds.add(mRequestIdCounter);
                return mRequestIdCounter;
            }

            mRequestIdCounter = 0;
        }

        throw new IllegalStateException("Could not generate request id");
    }

    void releaseRequestId(long requestId) {
        synchronized (mLock) {
            mUsedRequestIds.remove(requestId);
            // Reset counter back to lower value,
            // on request the search will automatically assign this value or a newer one.
            if (mRequestIdCounter > requestId) {
                mRequestIdCounter = requestId;
            }
        }
    }
}
