/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.rkpdapp.database;

import androidx.room.TypeConverter;

import java.time.Instant;

/**
 * Class to provide type conversion for Room database. The field expiration_time in the table is
 * implemented as long but since we want to handle that as Instant in code, we need to provide these
 * converters.
 */
public class InstantConverter {
    /**
     * Converts from epoch time in milliseconds to an Instant object.
     */
    @TypeConverter
    public static Instant fromTimestamp(Long value) {
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    /**
     * Converts from an Instant object to epoch time in milliseconds.
     */
    @TypeConverter
    public static Long toTimestamp(Instant time) {
        return time == null ? null : time.toEpochMilli();
    }
}
