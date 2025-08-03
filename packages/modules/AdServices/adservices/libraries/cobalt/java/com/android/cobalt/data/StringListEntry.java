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

package com.android.cobalt.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.common.hash.HashCode;

/**
 * A string hash and its relative order in the string hash list for a given report and day.
 *
 * <p>Values are tagged with @ColumnInfo so a {@link StringListEntry} can be automatically read from
 * tables.
 */
@AutoValue
public abstract class StringListEntry {
    /**
     * The index of the string hash in the hash list.
     *
     * <p>This value is shared with the persisted aggregate values.
     */
    @CopyAnnotations
    @ColumnInfo(name = "list_index")
    @NonNull
    public abstract int listIndex();

    /** The string hash. */
    @CopyAnnotations
    @ColumnInfo(name = "string_hash")
    @NonNull
    public abstract HashCode stringHash();

    /**
     * Creates a {@link StringListEntry}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    public static StringListEntry create(int listIndex, HashCode stringHash) {
        return new AutoValue_StringListEntry(listIndex, stringHash);
    }
}
