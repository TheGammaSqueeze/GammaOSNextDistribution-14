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

package com.android.cobalt.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.cobalt.AggregateValue;

/**
 * Stores aggregate values of unique reports for a given event vector, day, and system profile.
 *
 * <p>References the Reports and SystemProfiles tables to ensure necessary data are kept in sync.
 */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "AggregateStore",
        primaryKeys = {
            "customer_id",
            "project_id",
            "metric_id",
            "report_id",
            "day_index",
            "system_profile_hash",
            "event_vector"
        },
        foreignKeys = {
            @ForeignKey(
                    entity = ReportEntity.class,
                    parentColumns = {"customer_id", "project_id", "metric_id", "report_id"},
                    childColumns = {"customer_id", "project_id", "metric_id", "report_id"},
                    onDelete = ForeignKey.CASCADE),
            @ForeignKey(
                    entity = SystemProfileEntity.class,
                    parentColumns = {"system_profile_hash"},
                    childColumns = {"system_profile_hash"})
        },
        indices = {@Index(value = {"system_profile_hash"})})
abstract class AggregateStoreEntity {
    /** The values uniquely identifying the report. */
    @CopyAnnotations
    @Embedded
    @NonNull
    abstract ReportKey reportKey();

    /** The day the value is being aggregated on. */
    @CopyAnnotations
    @ColumnInfo(name = "day_index")
    @NonNull
    abstract int dayIndex();

    /** The event being aggregated. */
    @CopyAnnotations
    @ColumnInfo(name = "event_vector")
    @NonNull
    abstract EventVector eventVector();

    /** The system profile hash of the value being aggregated. */
    @CopyAnnotations
    @ColumnInfo(name = "system_profile_hash")
    @NonNull
    abstract long systemProfileHash();

    /** The aggregated value. */
    @CopyAnnotations
    @ColumnInfo(name = "aggregate_value")
    @NonNull
    abstract AggregateValue aggregateValue();

    /**
     * Creates an {@link AggregateStoreEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static AggregateStoreEntity create(
            ReportKey reportKey,
            int dayIndex,
            EventVector eventVector,
            long systemProfileHash,
            AggregateValue aggregateValue) {
        return new AutoValue_AggregateStoreEntity(
                reportKey, dayIndex, eventVector, systemProfileHash, aggregateValue);
    }
}
