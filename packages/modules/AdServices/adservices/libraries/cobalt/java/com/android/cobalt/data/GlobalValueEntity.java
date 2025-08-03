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
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.time.Instant;

/** Stores values used for features as string (key, value) pairs. */
@AutoValue
@CopyAnnotations
@Entity(tableName = "GlobalValues")
abstract class GlobalValueEntity {
    enum Key {
        INITIAL_ENABLED_TIME,
        INITIAL_DISABLED_TIME,
    }

    /** The feature's key. */
    @CopyAnnotations
    @ColumnInfo(name = "key")
    @PrimaryKey
    @NonNull
    abstract Key key();

    /** The feature's value. */
    @CopyAnnotations
    @ColumnInfo(name = "value")
    @NonNull
    abstract String value();

    /**
     * Creates a {@link GlobalValueEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static GlobalValueEntity create(Key key, String value) {
        return new AutoValue_GlobalValueEntity(key, value);
    }

    static Instant timeFromDbString(String time) {
        return Instant.parse(time);
    }

    static String timeToDbString(Instant time) {
        return time.toString();
    }
}
