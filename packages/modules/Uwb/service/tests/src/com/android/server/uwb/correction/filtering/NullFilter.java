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

import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

@Presubmit
public class NullFilter implements IFilter {

    long mWhen = 0;
    float mValue;

    /**
     * Adds a value to the filter.
     *
     * @param value The value to add to the filter.
     * @param timeMs When the value occurred, used to determine the latency introduced by the
     *                filter. Note that this has no effect on the order in which the filter
     *                operates. This is in milliseconds, relative to any consistent epoch.
     * @param fom The figure of merit for the reading.
     */
    @Override
    public void add(float value, long timeMs, double fom) {
        mWhen = timeMs;
        this.mValue = value;
    }

    /**
     * Alters the state of the filter such that it anticipates a change by the given amount. For
     * example, if the filter is working with distance, and the distance of the next reading is
     * expected to increase by 1 meter, 'shift' should be 1.
     *
     * @param shift How much to alter the filter state.
     */
    @Override
    public void compensate(float shift) {
        this.mValue += shift;
    }

    /**
     * Gets a sample object with the result from the last computation. The sample's time is the
     * average time of the samples that created the result, effectively describing the latency
     * introduced by the filter.
     *
     * @return The result from the last computation.
     */
    @NonNull
    @Override
    public Sample getResult() {
        return new Sample(mValue, mWhen, 1);
    }
}
