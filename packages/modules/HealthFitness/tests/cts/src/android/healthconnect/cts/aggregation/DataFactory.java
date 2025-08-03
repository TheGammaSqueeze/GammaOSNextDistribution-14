/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.healthconnect.cts.aggregation;

import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;

import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Power;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Test data factory for aggregations. */
public final class DataFactory {
    /**
     * Returns a {@link ActiveCaloriesBurnedRecord} with given {@code energy} between the given
     * {@code start} and {@code end} time.
     */
    static ActiveCaloriesBurnedRecord getActiveCaloriesBurnedRecord(
            double energy, Instant start, Instant end) {
        return new ActiveCaloriesBurnedRecord.Builder(
                        getEmptyMetadata(), start, end, Energy.fromCalories(energy))
                .build();
    }

    /**
     * Returns a {@link BasalMetabolicRateRecord} with given {@code power} at the given {@code
     * time}.
     */
    static BasalMetabolicRateRecord getBasalMetabolicRateRecord(double power, Instant time) {
        return new BasalMetabolicRateRecord.Builder(
                        new Metadata.Builder().build(), time, Power.fromWatts(power))
                .build();
    }

    /**
     * Returns a {@link BasalMetabolicRateRecord} with given {@code power} at the given {@code time}
     * at the given {@code offset}.
     */
    static BasalMetabolicRateRecord getBasalMetabolicRateRecord(
            double power, Instant time, ZoneOffset offset) {
        return new BasalMetabolicRateRecord.Builder(
                        new Metadata.Builder().build(), time, Power.fromWatts(power))
                .setZoneOffset(offset)
                .build();
    }

    /** Returns a {@link TimeInstantRangeFilter} with given {@code start} and {@code end} time. */
    static TimeInstantRangeFilter getTimeFilter(Instant start, Instant end) {
        return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build();
    }

    /** Returns a {@link LocalTimeRangeFilter} with given {@code start} and {@code end} time. */
    static LocalTimeRangeFilter getTimeFilter(LocalDateTime start, LocalDateTime end) {
        return new LocalTimeRangeFilter.Builder().setStartTime(start).setEndTime(end).build();
    }
}
