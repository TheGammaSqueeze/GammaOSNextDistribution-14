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
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Ignore;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.util.Optional;

/** Stores when reports were last sent. */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "Reports",
        primaryKeys = {"customer_id", "project_id", "metric_id", "report_id"})
abstract class ReportEntity {
    /** Values uniquely identifying the report. */
    @CopyAnnotations
    @Embedded
    @NonNull
    abstract ReportKey reportKey();

    /** Day the report was last sent, can be empty if not yet sent. */
    @CopyAnnotations
    @ColumnInfo(name = "last_sent_day_index")
    @Nullable
    abstract Optional<Integer> lastSentDayIndex();

    /**
     * Creates a {@link ReportEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static ReportEntity create(ReportKey reportKey, Optional<Integer> lastSentDayIndex) {
        return new AutoValue_ReportEntity(reportKey, lastSentDayIndex);
    }

    /**
     * Creates a {@link ReportEntity} without a last sent day index.
     *
     * <p>Ignored by Room.
     */
    @Ignore
    @NonNull
    static ReportEntity create(ReportKey reportKey) {
        return new AutoValue_ReportEntity(reportKey, Optional.empty());
    }

    /**
     * Creates a {@link ReportEntity} with a last sent day index.
     *
     * <p>Ignored by Room.
     */
    @Ignore
    @NonNull
    static ReportEntity create(ReportKey reportKey, int lastSentDayIndex) {
        return new AutoValue_ReportEntity(reportKey, Optional.of(lastSentDayIndex));
    }
}
