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

package com.android.server.healthconnect.storage.request;

import android.annotation.NonNull;

import java.util.List;

/**
 * Creates a new index in the given table.
 *
 * @hide
 */
public final class CreateIndexRequest {

    private final String mTableName;
    private final String mIndexName;
    private final boolean mIsUnique;
    private final List<String> mColumnNames;

    public CreateIndexRequest(
            @NonNull String tableName,
            @NonNull String indexName,
            boolean isUnique,
            @NonNull List<String> columnNames) {
        mTableName = tableName;
        mIndexName = indexName;
        mIsUnique = isUnique;
        mColumnNames = columnNames;
    }

    /** Returns a ready-for-use SQL command. */
    @NonNull
    public String getCommand() {
        final StringBuilder builder = new StringBuilder("CREATE ");

        if (mIsUnique) {
            builder.append("UNIQUE ");
        }

        builder.append("INDEX ")
                .append(mIndexName)
                .append(" ON ")
                .append(mTableName)
                .append(" (")
                .append(String.join(", ", mColumnNames))
                .append(")");

        return builder.toString();
    }
}
