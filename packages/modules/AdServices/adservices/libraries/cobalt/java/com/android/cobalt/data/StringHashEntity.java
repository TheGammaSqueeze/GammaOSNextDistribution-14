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
import androidx.room.Ignore;
import androidx.room.Index;

import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

/**
 * Stores the unique string hashes logged to a report on a given day and their index in the string
 * list for the report, day combination.
 */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "StringHashes",
        primaryKeys = {
            "customer_id",
            "project_id",
            "metric_id",
            "report_id",
            "day_index",
            "list_index",
        },
        indices = {
            @Index(
                    value = {
                        "customer_id",
                        "project_id",
                        "metric_id",
                        "report_id",
                        "day_index",
                        "string_hash"
                    },
                    unique = true)
        })
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public abstract class StringHashEntity {
    /** Values uniquely identifying the report. */
    @CopyAnnotations
    @Embedded
    @NonNull
    abstract ReportKey reportKey();

    /** The day the string hash was logged on. */
    @CopyAnnotations
    @ColumnInfo(name = "day_index")
    @NonNull
    abstract int dayIndex();

    /** The index of the string hash in the hash list. */
    @CopyAnnotations
    @ColumnInfo(name = "list_index")
    @NonNull
    abstract int listIndex();

    /** The string hash. */
    @CopyAnnotations
    @ColumnInfo(name = "string_hash")
    @NonNull
    abstract HashCode stringHash();

    /**
     * Creates an {@link StringHashEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static StringHashEntity create(
            ReportKey reportKey, int dayIndex, int listIndex, HashCode stringHash) {
        return new AutoValue_StringHashEntity(reportKey, dayIndex, listIndex, stringHash);
    }

    /** Creates an {@link StringHashEntity}. */
    @Ignore
    @NonNull
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public static StringHashEntity create(
            ReportKey reportKey, int dayIndex, int listIndex, String string) {
        return new AutoValue_StringHashEntity(reportKey, dayIndex, listIndex, getHash(string));
    }

    /** Creates a hash of the input value that's usable for observation generation */
    static HashCode getHash(String value) {
        return Hashing.farmHashFingerprint64().hashBytes(value.getBytes());
    }
}
