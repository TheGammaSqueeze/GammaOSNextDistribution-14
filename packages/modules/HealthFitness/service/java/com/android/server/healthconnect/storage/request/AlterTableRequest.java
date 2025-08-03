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

import android.util.Pair;
import android.util.Slog;

import java.util.List;

/**
 * Creates a alter table request and alter statements for it.
 *
 * @hide
 */
public final class AlterTableRequest {
    public static final String TAG = "HealthConnectAlter";
    private static final String ALTER_TABLE_COMMAND = "ALTER TABLE ";
    private static final String ADD_COLUMN_COMMAND = " ADD COLUMN ";
    private final String mTableName;
    private final List<Pair<String, String>> mColumnInfo;

    public AlterTableRequest(String tableName, List<Pair<String, String>> columnInfo) {
        mTableName = tableName;
        mColumnInfo = columnInfo;
    }

    /** Returns command for alter table request to add new columns */
    public String getAlterTableAddColumnsCommand() {
        final StringBuilder builder = new StringBuilder(ALTER_TABLE_COMMAND);
        builder.append(mTableName);
        mColumnInfo.forEach(
                (columnInfo) ->
                        builder.append(ADD_COLUMN_COMMAND)
                                .append(columnInfo.first)
                                .append(" ")
                                .append(columnInfo.second)
                                .append(", "));
        builder.setLength(builder.length() - 2); // Remove the last 2 char i.e. ", "
        Slog.d(TAG, "Alter table: " + builder);

        return builder.toString();
    }

    public static String getAlterTableCommandToAddGeneratedColumn(
            String tableName, CreateTableRequest.GeneratedColumnInfo generatedColumnInfo) {
        String request =
                ALTER_TABLE_COMMAND
                        + tableName
                        + ADD_COLUMN_COMMAND
                        + generatedColumnInfo.getColumnName()
                        + " "
                        + generatedColumnInfo.getColumnType()
                        + " GENERATED ALWAYS AS ("
                        + generatedColumnInfo.getExpression()
                        + ")";

        Slog.d(TAG, "Alter table generated: " + request);
        return request;
    }
}
