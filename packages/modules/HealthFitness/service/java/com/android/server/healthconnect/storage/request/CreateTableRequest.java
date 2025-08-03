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

package com.android.server.healthconnect.storage.request;

import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;

import android.annotation.NonNull;
import android.health.connect.Constants;
import android.util.Pair;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Creates a request and the table create statements for it.
 *
 * <p>Note: For every child table. This class automatically creates index statements for all the
 * defined foreign keys.
 *
 * @hide
 */
public final class CreateTableRequest {
    public static final String TAG = "HealthConnectCreate";
    public static final String FOREIGN_KEY_COMMAND = " FOREIGN KEY (";
    private static final String CREATE_INDEX_COMMAND = "CREATE INDEX IF NOT EXISTS idx_";
    private static final String CREATE_TABLE_COMMAND = "CREATE TABLE IF NOT EXISTS ";
    private static final String UNIQUE_COMMAND = "UNIQUE ( ";
    private final String mTableName;
    private final List<Pair<String, String>> mColumnInfo;
    private final List<String> mColumnsToIndex = new ArrayList<>();
    private final List<List<String>> mUniqueColumns = new ArrayList<>();
    private List<ForeignKey> mForeignKeys = new ArrayList<>();
    private List<CreateTableRequest> mChildTableRequests = Collections.emptyList();
    private List<GeneratedColumnInfo> mGeneratedColumnInfo = Collections.emptyList();

    public CreateTableRequest(String tableName, List<Pair<String, String>> columnInfo) {
        mTableName = tableName;
        mColumnInfo = columnInfo;
    }

    public String getTableName() {
        return mTableName;
    }

    @NonNull
    public CreateTableRequest addForeignKey(
            String referencedTable, List<String> columnNames, List<String> referencedColumnNames) {
        mForeignKeys = mForeignKeys == null ? new ArrayList<>() : mForeignKeys;
        mForeignKeys.add(new ForeignKey(referencedTable, columnNames, referencedColumnNames));

        return this;
    }

    @NonNull
    public CreateTableRequest createIndexOn(@NonNull String columnName) {
        Objects.requireNonNull(columnName);

        mColumnsToIndex.add(columnName);
        return this;
    }

    @NonNull
    public List<CreateTableRequest> getChildTableRequests() {
        return mChildTableRequests;
    }

    @NonNull
    public CreateTableRequest setChildTableRequests(
            @NonNull List<CreateTableRequest> childCreateTableRequests) {
        Objects.requireNonNull(childCreateTableRequests);

        mChildTableRequests = childCreateTableRequests;
        return this;
    }

    @NonNull
    public String getCreateCommand() {
        final StringBuilder builder = new StringBuilder(CREATE_TABLE_COMMAND);
        builder.append(mTableName);
        builder.append(" (");
        mColumnInfo.forEach(
                (columnInfo) ->
                        builder.append(columnInfo.first)
                                .append(" ")
                                .append(columnInfo.second)
                                .append(", "));

        for (GeneratedColumnInfo generatedColumnInfo : mGeneratedColumnInfo) {
            builder.append(generatedColumnInfo.getColumnName())
                    .append(" ")
                    .append(generatedColumnInfo.getColumnType())
                    .append(" AS (")
                    .append(generatedColumnInfo.getExpression())
                    .append("), ");
        }

        if (mForeignKeys != null) {
            for (ForeignKey foreignKey : mForeignKeys) {
                builder.append(foreignKey.getFkConstraint()).append(", ");
            }
        }

        if (!mUniqueColumns.isEmpty()) {
            mUniqueColumns.forEach(
                    columns -> {
                        builder.append(UNIQUE_COMMAND);
                        for (String column : columns) {
                            builder.append(column).append(", ");
                        }
                        builder.setLength(builder.length() - 2); // Remove the last 2 char i.e. ", "
                        builder.append("), ");
                    });
        }
        builder.setLength(builder.length() - 2); // Remove the last 2 char i.e. ", "

        builder.append(")");
        if (Constants.DEBUG) {
            Slog.d(TAG, "Create table: " + builder);
        }

        return builder.toString();
    }

    public CreateTableRequest addUniqueConstraints(List<String> columnNames) {
        mUniqueColumns.add(columnNames);
        return this;
    }

    @NonNull
    public List<String> getCreateIndexStatements() {
        List<String> result = new ArrayList<>();
        if (mForeignKeys != null) {
            int index = 0;
            for (ForeignKey foreignKey : mForeignKeys) {
                result.add(foreignKey.getFkIndexStatement(index++));
            }
        }

        if (!mColumnsToIndex.isEmpty()) {
            for (String columnToIndex : mColumnsToIndex) {
                result.add(getCreateIndexCommand(columnToIndex));
            }
        }

        return result;
    }

    public CreateTableRequest setGeneratedColumnInfo(
            @NonNull List<GeneratedColumnInfo> generatedColumnInfo) {
        Objects.requireNonNull(generatedColumnInfo);

        mGeneratedColumnInfo = generatedColumnInfo;
        return this;
    }

    private String getCreateIndexCommand(String indexName, List<String> columnNames) {
        Objects.requireNonNull(columnNames);
        Objects.requireNonNull(indexName);

        return CREATE_INDEX_COMMAND
                + indexName
                + " ON "
                + mTableName
                + "("
                + String.join(DELIMITER, columnNames)
                + ")";
    }

    private String getCreateIndexCommand(String columnName) {
        Objects.requireNonNull(columnName);

        return CREATE_INDEX_COMMAND
                + mTableName
                + "_"
                + columnName
                + " ON "
                + mTableName
                + "("
                + columnName
                + ")";
    }

    public static final class GeneratedColumnInfo {
        private final String columnName;
        private final String type;
        private final String expression;

        public GeneratedColumnInfo(String columnName, String type, String expression) {
            this.columnName = columnName;
            this.type = type;
            this.expression = expression;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getColumnType() {
            return type;
        }

        public String getExpression() {
            return expression;
        }
    }

    private final class ForeignKey {
        private final List<String> mColumnNames;
        private final String mReferencedTableName;
        private final List<String> mReferencedColumnNames;

        ForeignKey(
                String referencedTable,
                List<String> columnNames,
                List<String> referencedColumnNames) {
            mReferencedTableName = referencedTable;
            mColumnNames = columnNames;
            mReferencedColumnNames = referencedColumnNames;
        }

        String getFkConstraint() {
            return FOREIGN_KEY_COMMAND
                    + String.join(DELIMITER, mColumnNames)
                    + ")"
                    + " REFERENCES "
                    + mReferencedTableName
                    + "("
                    + String.join(DELIMITER, mReferencedColumnNames)
                    + ") ON DELETE CASCADE";
        }

        String getFkIndexStatement(int fkNumber) {
            return getCreateIndexCommand(mTableName + "_" + fkNumber, mColumnNames);
        }
    }
}
