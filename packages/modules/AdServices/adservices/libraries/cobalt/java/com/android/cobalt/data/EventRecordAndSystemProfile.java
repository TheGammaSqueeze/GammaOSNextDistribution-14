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
import com.google.cobalt.SystemProfile;

/**
 * Encapsulates one locally aggregated event that is required for generating observations.
 *
 * <p>Values are tagged with @ColumnInfo so {@link EventRecordAndSystemProfile} can be automatically
 * read from tables.
 */
@AutoValue
@CopyAnnotations
public abstract class EventRecordAndSystemProfile {
    /** The system profile. */
    @CopyAnnotations
    @ColumnInfo(name = "system_profile")
    @NonNull
    public abstract SystemProfile systemProfile();

    /** The event vector. */
    @CopyAnnotations
    @ColumnInfo(name = "event_vector")
    @NonNull
    public abstract EventVector eventVector();

    /** The found aggregate value. */
    @CopyAnnotations
    @ColumnInfo(name = "aggregate_value")
    @NonNull
    public abstract AggregateValue aggregateValue();

    /**
     * Creates a {@link EventRecordAndSystemProfile}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    public static EventRecordAndSystemProfile create(
            SystemProfile systemProfile, EventVector eventVector, AggregateValue aggregateValue) {
        return new AutoValue_EventRecordAndSystemProfile(
                systemProfile, eventVector, aggregateValue);
    }
}
