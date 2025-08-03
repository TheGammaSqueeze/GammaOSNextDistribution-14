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

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;

import java.util.List;

/** Interface for delete signal from the updated signal list. */
public interface SignalEvictor {

    /**
     * Will take away signals from the signal list and add to the remove list in {@link
     * UpdateOutput}.
     *
     * @param maxAllowedSignalSize the soft limit
     * @param maxAllowedSignalSizeWithOversubscribe the hard limit
     * @return if any eviction has taken place.
     */
    boolean evict(
            AdTechIdentifier adTechIdentifier,
            List<DBProtectedSignal> updatedSignals,
            UpdateOutput combinedUpdates,
            int maxAllowedSignalSize,
            int maxAllowedSignalSizeWithOversubscribe);
}
