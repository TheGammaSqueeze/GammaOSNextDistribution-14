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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** IndexInfo contains information about all the attributes that an index can hold. */
class IndexInfo {
    private final String mIndexName;
    private final String mTableName;
    private final List<String> mColumnList;
    private final boolean mCheckUniqueFlag;

    /** Creates an instance for IndexInfo. */
    IndexInfo(Builder builder) {
        mIndexName = builder.mIndexName;
        mTableName = builder.mTableName;
        mColumnList = builder.mColumnList;
        mCheckUniqueFlag = builder.mCheckUniqueFlag;
    }

    /** Builder pattern for IndexInfo. */
    public static class Builder {
        private final String mIndexName;
        private final String mTableName;
        private final List<String> mColumnList;
        private boolean mCheckUniqueFlag;

        Builder(String indexName, String tableName) {
            mIndexName = indexName;
            mTableName = tableName;
            mColumnList = new ArrayList<>();
        }

        /**
         * Sets the unique constraint flag of the index(if index has been made on unique flag then
         * it is set to true otherwise false).
         */
        public Builder setUniqueFlag(boolean flag) {
            mCheckUniqueFlag = flag;
            return this;
        }

        /** Appends column to the existing list of columns. */
        public Builder addIndexCols(@NonNull String column) {
            Objects.requireNonNull(column);
            mColumnList.add(column);
            return this;
        }

        /** Builds the IndexInfo object. */
        public IndexInfo build() {
            return new IndexInfo(this);
        }
    }

    /**
     * @return name of the index.
     */
    @NonNull
    public String getIndexName() {
        return mIndexName;
    }

    /**
     * @return name of the table of the index.
     */
    @NonNull
    public String getTableName() {
        return mTableName;
    }

    /**
     * @return list of the columns of the table of the index.
     */
    @NonNull
    public List<String> getColumnList() {
        return mColumnList;
    }

    /**
     * @return boolean value which shows whether index is made unique or not.
     */
    @NonNull
    public boolean isUnique() {
        return mCheckUniqueFlag;
    }

    /**
     * @return true if the objects of the two IndexInfo are same otherwise false
     */
    @NonNull
    public boolean isEqual(IndexInfo expectedIndex) {
        if (mIndexName.equals(expectedIndex.mIndexName)
                && mTableName.equals(expectedIndex.mTableName)
                && mCheckUniqueFlag == expectedIndex.mCheckUniqueFlag) {
            List<String> columnListCheck = mColumnList;
            List<String> expectedColumnList = expectedIndex.mColumnList;
            Collections.sort(columnListCheck);
            Collections.sort(expectedColumnList);
            return columnListCheck.equals(expectedColumnList);
        }
        return false;
    }

    /**
     * Compares two IndexInfo and stores any backward incompatible change to the corresponding
     * ErrorInfo of index.
     */
    public void checkIndexDiff(
            IndexInfo expectedIndex, List<String> modificationOfIndex, String tableName) {

        for (String columnName : mColumnList) {
            if (!expectedIndex.mColumnList.contains(columnName)) {
                modificationOfIndex.add(
                        "Column: "
                                + columnName
                                + " has been deleted from the  index : "
                                + mIndexName
                                + " of table: "
                                + tableName);
            }
        }

        for (String columnName : expectedIndex.mColumnList) {
            if (!mColumnList.contains(columnName)) {
                modificationOfIndex.add(
                        "Column: "
                                + columnName
                                + " has been added to the index : "
                                + mIndexName
                                + " of table: "
                                + tableName);
            }
        }

        if (mCheckUniqueFlag != expectedIndex.mCheckUniqueFlag) {
            if (mCheckUniqueFlag) {
                modificationOfIndex.add(
                        "Unique flag has been removed from the index: "
                                + mIndexName
                                + " of table: "
                                + tableName);
            } else {
                modificationOfIndex.add(
                        "Unique flag has been added to the index: "
                                + mIndexName
                                + " of table: "
                                + tableName);
            }
        }
    }
}
