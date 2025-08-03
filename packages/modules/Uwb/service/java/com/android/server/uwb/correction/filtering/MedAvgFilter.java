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

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A Median, Average filter.  The filter has an adjustable median window and
 * the configured percentage of non-outliers are averaged.
 */
public class MedAvgFilter implements IFilter {
    private static final int MAX_FILTER = 255;

    private int mWindowSize;
    private float mCut;
    @NonNull
    private final ArrayDeque<Sample> mWindow = new ArrayDeque<>();
    @NonNull
    private Sample mResult = new Sample(0F, 0, 0);

    /**
     * Creates a new instance of the MedAvgFilter class.
     * @param windowSize The maximum number of samples to store in the moving window.
     * @param cut What percentage of non-outliers are to be averaged, from 0 to 1. See
     *            {@link #setCut(float)} for more information.
     */
    public MedAvgFilter(int windowSize, float cut) {
        setWindowSize(windowSize);
        setCut(cut);
    }

    /**
     * Gets the size of the median window.
     * @return A count of samples.
     */
    public int getWindowSize() {
        return mWindowSize;
    }

    /**
     * Sets the size of the median window; how many samples are considered when producing a filtered
     * result. Must be between 1 and {@link #MAX_FILTER}.
     * @param value The number of samples to set as the maximum window size.
     */
    public void setWindowSize(int value) {
        if (value <= 0 || value > MAX_FILTER) {
            throw new IllegalArgumentException(
                    "Value is out of range; must be between 1 and " + MAX_FILTER + " inclusive.");
        }
        mWindowSize = value;
    }

    /**
     * Gets the size of the median cut.
     * @return A value from 0-1 that describes what percentage of values in the window will be
     * kept and averaged.
     */
    public float getCut() {
        return mCut;
    }

    /**
     * Sets the size of the median cut. A value of 0 is a perfect median, taking only the
     * center value(s).  A value of 1 is a perfect average.  A value of 0.25 discards 75% of the
     * outliers and averages the 25% remaining values.
     * @param value A value 0-1 that describes what median percentage of the window to average.
     */
    public void setCut(float value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(
                    "Value is out of range; must be between 0 and 1 inclusive");
        }
        mCut = value;
    }

    /**
     * Gets a sample object with the result from the last computation. The sample's time is
     * the average time of the samples that created the result, effectively describing the
     * latency introduced by the filter.
     * @return The result from the last computation.
     */
    @NonNull
    public Sample getResult() {
        return mResult;
    }

    /**
     * Adds a value to the filter.
     * @param value The value to add to the filter.
     * @param timeMs When the value occurred, in ms since boot. Used to determine the latency
     * introduced by the filter. Note that this has no effect on the order in which the filter
     * operates on values.
     */
    @Override
    public void add(float value, long timeMs, double fom) {
        mWindow.addLast(new Sample(value, timeMs, fom));
        while (mWindow.size() > mWindowSize) {
            mWindow.removeFirst();
        }
        mResult = compute();
    }

    /**
     * Rewrites all sample values based on the selector.
     * @param selector The interface containing the function that selects the new sample values.
     */
    protected void remap(RemapFunction selector) {
        mWindow.forEach(s -> s.value = selector.run(s.value));
        mResult = new Sample(selector.run(mResult.value), mResult.timeMs, mResult.fom);
    }

    /**
     * Alters the state of the filter such that it anticipates a change by the given amount.
     * For example, if the filter is working with distance, and the distance of the next
     * reading is expected to increase by 1 meter, 'shift' should be 1.
     * @param shift How much to alter the filter state.
     */
    @Override
    public void compensate(float shift) {
        remap(s -> s + shift);
    }

    /**
     * Performs the median and average component and returns a new sample.
     * The sample's instant indicates the sourced data's center time, approximating how much
     * latency was introduced by the filter.
     */
    private Sample compute() {
        int count = mWindow.size();
        if (count == 0) {
            throw new IllegalStateException("The filter is empty.");
        }
        if (count == 1) {
            return mWindow.getFirst();
        }
        List<Sample> sorted = sortSamples(mWindow);

        if (mCut == 1F) {
            // 100% of a median cut is just an average.

            // Note that this comes AFTER the sort. MedAvgRotationFilterFilter's averaging routine
            // requires that samples are sorted, as it sorts in a special way to respect angle
            // rollover.
            return averageSamples(sorted);
        }

        int throwAway = Math.round(count * (1 - mCut) / 2);
        if (2 * throwAway >= count) {
            // At least 2 samples if count is even or 1 sample if count is odd
            throwAway--;
        }

        return averageSamples(sorted.subList(throwAway, count - throwAway));
    }

    /**
     * Creates a new, sorted list containing the provided samples. Sorting is based on the sample
     * value.
     * @param list A list of samples to sort.
     * @return A new list, of the same samples, sorted by value.
     */
    protected List<Sample> sortSamples(Collection<Sample> list) {
        ArrayList<Sample> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Averages a list of samples.
     * @param samples The list of samples.
     * @return A sample containing the average value and time of the samples in the list.
     */
    protected Sample averageSamples(Collection<Sample> samples) {
        if (samples.size() == 0 || samples.stream().anyMatch(Objects::isNull)) {
            return null; // Average can't be computed.
        }
        float valueSum = 0F;
        double fomSum = 0.0;
        long instantSum = 0;

        // Using a relevant epoch keeps the values small and therefore decreases the risk of
        //  overflow.
        long instantEpoch = samples.stream().findFirst().get().timeMs;

        // If the FOM of all values is 1, fomWeight will be the size of the array.
        float fomWeight = (float) samples.stream().mapToDouble(s -> s.fom).sum();

        if (fomWeight == 0) {
            // Every reading has 0 confidence. Can't produce an average. Also, this shouldn't
            // happen because FOM = 0 readings should be ignored by the engine.
            return null;
        }
        for (Sample s: samples) {
            // Sum up all samples to be averaged, multiplied by their confidence weight.
            valueSum += s.value * (float) s.fom;
            instantSum += (long) ((s.timeMs - instantEpoch) * s.fom);
            fomSum += s.fom * s.fom;
        }
        return new Sample(
            valueSum / fomWeight,
            instantEpoch + (long) (instantSum / fomWeight),
            fomSum / fomWeight
        );
    }

    /**
     * This interface can be used to implement a remapper - a function that changes historical data
     * in the filter in order to compensate for aspects of pose changes.
     */
    public interface RemapFunction {
        /**
         * Performs a change to a data point in a filter.
         * @param value The value that needs compensation from a pose change.
         * @return The new, pose-compensated value.
         */
        float run(float value);
    }
}
