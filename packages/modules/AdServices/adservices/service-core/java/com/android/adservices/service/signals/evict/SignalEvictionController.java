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

package com.android.adservices.service.signals.evict;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/** Controller to run a series of {@link SignalEvictor}s in a water fall modal. */
public class SignalEvictionController {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final List<SignalEvictor> mSignalEvictors;
    private final int mMaxAllowedSignalSize;
    private final int mMaxAllowedSignalSizeWithOversubscription;

    @VisibleForTesting
    public SignalEvictionController(
            List<SignalEvictor> signalEvictors,
            int maxAllowedSignalSize,
            int maxAllowedSignalSizeWithOversubscription) {
        mSignalEvictors = signalEvictors;
        mMaxAllowedSignalSize = maxAllowedSignalSize;
        mMaxAllowedSignalSizeWithOversubscription = maxAllowedSignalSizeWithOversubscription;
    }

    public SignalEvictionController() {
        this(
                List.of(new FifoSignalEvictor()),
                FlagsFactory.getFlags().getProtectedSignalsMaxSignalSizePerBuyerBytes(),
                FlagsFactory.getFlags()
                        .getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes());
    }

    /**
     * Run signal eviction with a waterfall module of defined evictors. Skips the following evictors
     * if the previous evictor takes no action (returns false).
     */
    public void evict(
            AdTechIdentifier adTech,
            List<DBProtectedSignal> updatedSignals,
            UpdateOutput combinedUpdates) {
        sLogger.v("Start running signal eviction.");
        for (SignalEvictor evictor : mSignalEvictors) {
            if (!evictor.evict(
                    adTech,
                    updatedSignals,
                    combinedUpdates,
                    mMaxAllowedSignalSize,
                    mMaxAllowedSignalSizeWithOversubscription)) {
                sLogger.v("Eviction finished.");
                break;
            }
        }
    }
}
