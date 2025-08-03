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
package com.android.server.uwb.correction.filtering;

import static com.android.server.uwb.correction.math.SphericalVector.Annotated;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.pose.IPoseSource;

/**
 * Interface for a filter that operates on a UwbPosition.
 */
public interface IPositionFilter {
    /**
     * Adds a value to the filter.
     * @param value The value to add to the filter.
     * @param timeMs The time at which the UWB value was received, in ms since boot. This is
     * used to determine the latency introduced by the filter. Note that this has no effect on the
     * order in which the filter operates on values.
     */
    void add(@NonNull Annotated value, long timeMs);

    /**
     * Computes a predicted UWB position based on the new pose.
     * @param timeMs The time for which the position should be computed, in ms since boot.
     */
    Annotated compute(long timeMs);

    /**
     * Updates the filter history to account for changes to the pose.
      * @param poseSource The pose source from which to get the latest pose.
     */
    void updatePose(@Nullable IPoseSource poseSource, long timeMs);
}
