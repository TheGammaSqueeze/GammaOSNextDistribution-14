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
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Comparator;
import java.util.List;

/** Signal Evictor based on creation time. */
public class FifoSignalEvictor implements SignalEvictor {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final Comparator<DBProtectedSignal> CREATION_TIME_COMPARATOR =
            Comparator.comparing(DBProtectedSignal::getCreationTime);

    /**
     * {@inheritDoc} Triggers eviction if and only if total size exceeding the oversubscription
     * policy (hard limit).
     *
     * <p>Removes signal with the oldest creation time from the signal list and adds to the to
     * remove list in the {@code combinedUpdates} until the total size of signals fall below the
     * {@code maxAllowedSignalSize}.
     */
    @Override
    public boolean evict(
            AdTechIdentifier adTechIdentifier,
            List<DBProtectedSignal> updatedSignals,
            UpdateOutput combinedUpdates,
            int maxAllowedSignalSize,
            int maxAllowedSignalSizeWithOversubscription) {
        sLogger.v("Start FIFO eviction.");
        int currentSignalSize = SignalSizeCalculator.calculate(updatedSignals);
        if (currentSignalSize <= maxAllowedSignalSizeWithOversubscription) {
            sLogger.v("Signal size within the limit, skipping the FIFO eviction.");
            return false;
        }

        updatedSignals.sort(CREATION_TIME_COMPARATOR.reversed());

        while (currentSignalSize > maxAllowedSignalSize) {
            DBProtectedSignal oldestSignal = updatedSignals.remove(updatedSignals.size() - 1);
            combinedUpdates.getToRemove().add(oldestSignal);
            currentSignalSize -= SignalSizeCalculator.calculate(oldestSignal);
        }

        sLogger.v(
                "Finished FIFO signal Eviction, %d signals to add, and %d signals to remove",
                combinedUpdates.getToAdd().size(), combinedUpdates.getToRemove().size());
        return true;
    }
}
