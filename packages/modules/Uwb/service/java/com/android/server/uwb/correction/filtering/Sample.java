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

/**
 * Represents a data sample and when it was acquired.
 */
public class Sample implements Comparable<Sample> {
    /** The value of the sample. */
    public float value;

    /** The time of the sample. */
    public long timeMs;

    /**
     * A value between 0 and 1 indicating how likely the measurement is to be based on good
     * information.
     */
    public double fom;

    /**
     * Creates a new instance of the Sample class.
     * @param value The value of the sample.
     * @param timeMs The time at which the value was relevant, in ms since boot.
     * @param fom The confidence (figure of merit) of the reading.
     */
    Sample(float value, long timeMs, double fom) {
        this.value = value;
        this.timeMs = timeMs;
        this.fom = fom;
    }

    /**
     * Compares this sample to another, ignoring the time of the samples.
     * @param other The other sample to compare to.
     * @return  a negative integer, zero, or a positive integer as this object
     *          is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     */
    @Override
    public int compareTo(@NonNull Sample other) {
        return Float.compare(value, other.value);
    }
}
