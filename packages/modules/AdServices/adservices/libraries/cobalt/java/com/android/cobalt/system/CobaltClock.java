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

package com.android.cobalt.system;

import com.google.cobalt.MetricDefinition;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Utility class for working with times and Cobalt day indices. */
public final class CobaltClock {

    private final Instant mCurrentInstant;

    public CobaltClock(long currentTimeMillis) {
        mCurrentInstant = Instant.ofEpochMilli(currentTimeMillis);
    }

    /** Get the day index in the UTC timezone. */
    public int dayIndexUtc() {
        return (int) mCurrentInstant.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
    }

    /** Get the day index in the local device's timezone. */
    public int dayIndexLocal() {
        return (int) mCurrentInstant.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
    }

    /** Get the day index in the specified timezone. */
    public int dayIndex(String timeZone) {
        return (int) mCurrentInstant.atZone(ZoneId.of(timeZone)).toLocalDate().toEpochDay();
    }

    /** Get the day index in the timezone specified for the metric. */
    public int dayIndex(MetricDefinition metric) {
        switch (metric.getTimeZonePolicy()) {
            case UTC:
                return dayIndexUtc();
            case LOCAL:
                return dayIndexLocal();
            case OTHER_TIME_ZONE:
                return dayIndex(metric.getOtherTimeZone());
            case UNRECOGNIZED:
                throw new AssertionError("Invalid TimeZonePolicy");
        }
        throw new AssertionError("Unreachable code (exhaustive enum)");
    }
}
