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

package android.healthconnect.cts.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** TableInfo contains information about all the attributes that a table cna hold. */
class TableInfo {
    private final String mTableName;
    private final List<String> mPrimaryKey;

    private final HashMap<String, ColumnInfo> mColumnNameColumnInfoMap;

    private final HashMap<String, ForeignKeyInfo> mForeignKeyNameForeignKeyInfoMap;

    private final HashMap<String, IndexInfo> mIndexNameIndexInfoMap;

    /** Creates an instance for TableInfo. */
    TableInfo(Builder builder) {
        mTableName = builder.mTableName;
        mColumnNameColumnInfoMap = builder.mColumnNameColumnInfoMap;
        mForeignKeyNameForeignKeyInfoMap = builder.mForeignKeyNameForeignKeyInfoMap;
        mIndexNameIndexInfoMap = builder.mIndexNameIndexInfoMap;
        mPrimaryKey = builder.mPrimaryKey;
    }

    /** Builder pattern for TableInfo. */
    public static class Builder {
        private final String mTableName;

        private final List<String> mPrimaryKey;

        private final HashMap<String, ColumnInfo> mColumnNameColumnInfoMap;

        private final HashMap<String, ForeignKeyInfo> mForeignKeyNameForeignKeyInfoMap;

        private final HashMap<String, IndexInfo> mIndexNameIndexInfoMap;

        Builder(String tableName) {
            mTableName = tableName;
            mColumnNameColumnInfoMap = new HashMap<>();
            mForeignKeyNameForeignKeyInfoMap = new HashMap<>();
            mIndexNameIndexInfoMap = new HashMap<>();
            mPrimaryKey = new ArrayList<>();
        }

        /** Adds primary key column to the existing list of column. */
        public Builder addPrimaryKeyColumn(@NonNull String primaryKey) {
            Objects.requireNonNull(primaryKey);
            mPrimaryKey.add(primaryKey);
            return this;
        }

        /** Adds mapping for ColumnName and corresponding ColumnInfo. */
        public Builder addColumnInfoMapping(
                @NonNull String columnName, @NonNull ColumnInfo columnInfo) {
            Objects.requireNonNull(columnName);
            Objects.requireNonNull(columnInfo);
            mColumnNameColumnInfoMap.put(columnName, columnInfo);
            return this;
        }

        /** Adds mapping for ForeignKeyName and corresponding ForeignKeyInfo. */
        public Builder addForeignKeyInfoMapping(
                @NonNull String foreignKeyName, @NonNull ForeignKeyInfo foreignKeyInfo) {
            Objects.requireNonNull(foreignKeyName);
            Objects.requireNonNull(foreignKeyInfo);
            mForeignKeyNameForeignKeyInfoMap.put(foreignKeyName, foreignKeyInfo);
            return this;
        }

        /** Adds mapping for IndexName and corresponding IndexInfo. */
        public Builder addIndexInfoMapping(
                @NonNull String indexName, @NonNull IndexInfo indexInfo) {
            Objects.requireNonNull(indexName);
            Objects.requireNonNull(indexInfo);
            mIndexNameIndexInfoMap.put(indexName, indexInfo);
            return this;
        }

        /** Builds the TableInfo object. */
        public TableInfo build() {
            return new TableInfo(this);
        }
    }

    /**
     * @return name of the table.
     */
    @NonNull
    public String getTableName() {
        return mTableName;
    }

    /**
     * @return primary key of a table.
     */
    @Nullable
    public List<String> getPrimaryKey() {
        return mPrimaryKey;
    }

    /**
     * @return the columnName-ColumnInfo HashMap.
     */
    @NonNull
    public HashMap<String, ColumnInfo> getColumnInfoMapping() {
        return mColumnNameColumnInfoMap;
    }

    /**
     * @return the ForeignKeyName-ForeignKeyInfo HashMap.
     */
    @Nullable
    public HashMap<String, ForeignKeyInfo> getForeignKeyMapping() {
        return mForeignKeyNameForeignKeyInfoMap;
    }

    /**
     * @return the indexName-IndexInfo HashMap.
     */
    public HashMap<String, IndexInfo> getIndexInfoMapping() {
        return mIndexNameIndexInfoMap;
    }
}
