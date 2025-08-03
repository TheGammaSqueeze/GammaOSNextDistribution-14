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

package com.android.adservices.data.adselection;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.service.adselection.HistogramEvent;

import com.google.auto.value.AutoValue;

import java.util.Set;

/**
 * A DB value class representing all the data used to generate a {@link HistogramEvent} that is
 * associated with a single {@link com.android.adservices.data.adselection.DBAdSelection}.
 */
@AutoValue
public abstract class DBAdSelectionHistogramInfo {
    /** Creates and returns a new {@link DBAdSelectionHistogramInfo} object. */
    public static DBAdSelectionHistogramInfo create(
            @NonNull AdTechIdentifier buyer, @Nullable String serializedAdCounterKeys) {
        return new AutoValue_DBAdSelectionHistogramInfo(buyer, serializedAdCounterKeys);
    }

    /** Returns the winning ad's buyer adtech's {@link AdTechIdentifier}. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "custom_audience_signals_buyer")
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /**
     * Returns the arbitrary keys, serialized as a JSON array, representing groupings that a buyer
     * adtech has assigned to the winning ad.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_counter_int_keys")
    @Nullable
    protected abstract String getSerializedAdCounterKeys();

    /**
     * Returns the {@link Set} of arbitrary keys representing groupings that a buyer adtech has
     * assigned to the winning ad.
     */
    @Nullable
    public final Set<Integer> getAdCounterKeys() {
        return FledgeRoomConverters.deserializeIntegerSet(getSerializedAdCounterKeys());
    }
}
