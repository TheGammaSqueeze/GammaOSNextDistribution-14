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
import com.google.cobalt.AggregateValue;

/**
 * Encapsulates the values required for aggregation at log time.
 *
 * <p>Values are tagged with @ColumnInfo so a {@link SystemProfileAndAggregateValue} can be
 * automatically read from tables.
 */
@AutoValue
@CopyAnnotations
abstract class SystemProfileAndAggregateValue {
    /** The system profile hash. */
    @CopyAnnotations
    @ColumnInfo(name = "system_profile_hash")
    @NonNull
    abstract long systemProfileHash();

    /** The found aggregate value. */
    @CopyAnnotations
    @ColumnInfo(name = "aggregate_value")
    @NonNull
    abstract AggregateValue aggregateValue();

    /**
     * Creates a {@link SystemProfileAndAggregateValue}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static SystemProfileAndAggregateValue create(
            long systemProfileHash, AggregateValue aggregateValue) {
        return new AutoValue_SystemProfileAndAggregateValue(systemProfileHash, aggregateValue);
    }
}
