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

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/**
 * All the identifiers needed to uniquely identify a report.
 *
 * <p>Values are tagged with @ColumnInfo so a {@link ReportKey} can be automatically read from
 * tables.
 */
@AutoValue
public abstract class ReportKey {
    /** The customer id. */
    @CopyAnnotations
    @ColumnInfo(name = "customer_id")
    @NonNull
    public abstract long customerId();

    /** The project id. */
    @CopyAnnotations
    @ColumnInfo(name = "project_id")
    @NonNull
    public abstract long projectId();

    /** The metric id. */
    @CopyAnnotations
    @ColumnInfo(name = "metric_id")
    @NonNull
    public abstract long metricId();

    /** The report id. */
    @CopyAnnotations
    @ColumnInfo(name = "report_id")
    @NonNull
    public abstract long reportId();

    /**
     * Creates a {@link ReportKey}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    public static ReportKey create(long customerId, long projectId, long metricId, long reportId) {
        return new AutoValue_ReportKey(customerId, projectId, metricId, reportId);
    }
}
