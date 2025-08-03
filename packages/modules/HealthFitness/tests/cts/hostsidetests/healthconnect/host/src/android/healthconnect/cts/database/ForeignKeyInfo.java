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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ForeignKeyInfo contains information about all the attributes that a Foreign Key can hold. */
public class ForeignKeyInfo {
    private final String mForeignKeyName;
    private final String mForeignKeyTableName;
    private final String mForeignKeyReferredColumn;
    private final List<Integer> mForeignKeyFlags;

    /**
     * ForeignKeyFlags contains all types of flags that can be used while creating a foreign key for
     * a table.
     */
    @IntDef({
            ON_DELETE_CASCADE,
            ON_DELETE_SET_NULL,
            ON_DELETE_SET_DEFAULT,
            ON_DELETE_RESTRICT,
            ON_UPDATE_CASCADE,
            ON_UPDATE_SET_NULL,
            ON_UPDATE_SET_DEFAULT,
            ON_UPDATE_RESTRICT,
            DEFERRABLE_FLAG,
            INITIALLY_DEFERRED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ForeignKeyFlags {
    }

    public static final int ON_DELETE_CASCADE = 0;
    public static final int ON_DELETE_SET_NULL = 1;
    public static final int ON_DELETE_SET_DEFAULT = 2;
    public static final int ON_DELETE_RESTRICT = 3;
    public static final int ON_UPDATE_CASCADE = 4;
    public static final int ON_UPDATE_SET_NULL = 5;
    public static final int ON_UPDATE_SET_DEFAULT = 6;
    public static final int ON_UPDATE_RESTRICT = 7;
    public static final int DEFERRABLE_FLAG = 8;
    public static final int INITIALLY_DEFERRED = 9;

    /** Creates an instance for ForeignKeyInfo. */
    ForeignKeyInfo(Builder builder) {
        mForeignKeyName = builder.mForeignKeyName;
        mForeignKeyTableName = builder.mForeignKeyTableName;
        mForeignKeyReferredColumn = builder.mForeignKeyReferredColumn;
        mForeignKeyFlags = builder.mForeignKeyFlags;
    }

    /** Builder pattern for ForeignKeyInfo. */
    public static class Builder {
        private final String mForeignKeyName;
        private final String mForeignKeyTableName;
        private final String mForeignKeyReferredColumn;
        private final List<Integer> mForeignKeyFlags;

        Builder(String foreignKey, String referencedTable, String referencedColumn) {
            mForeignKeyName = foreignKey;
            mForeignKeyTableName = referencedTable;
            mForeignKeyReferredColumn = referencedColumn;
            mForeignKeyFlags = new ArrayList<>();
        }

        /** Appends flag to the existing list of flags. */
        public ForeignKeyInfo.Builder addFlag(@ForeignKeyFlags int foreignKeyFlag) {
            mForeignKeyFlags.add(foreignKeyFlag);
            return this;
        }

        /** Builds the ForeignKeyInfo object. */
        public ForeignKeyInfo build() {
            return new ForeignKeyInfo(this);
        }
    }

    /**
     * @return name of the foreignKey.
     */
    @NonNull
    public String getForeignKeyName() {
        return mForeignKeyName;
    }

    /**
     * @return referenced table of the foreignKey.
     */
    @NonNull
    public String getForeignKeyTableName() {
        return mForeignKeyTableName;
    }

    /**
     * @return primary key of referenced table of the foreign key.
     */
    @NonNull
    public String getForeignKeyReferredColumnName() {
        return mForeignKeyReferredColumn;
    }

    /**
     * @return list of all the flags of the foreignkey.
     */
    @Nullable
    public List<Integer> getForeignKeyFlags() {
        return mForeignKeyFlags;
    }

    /**
     * @return true if the objects of the two ForeignkeyInfo are same otherwise false.
     */
    @NonNull
    public Boolean isEqual(ForeignKeyInfo expectedForeignKey) {
        if (mForeignKeyName.equals(expectedForeignKey.mForeignKeyName)
                && mForeignKeyTableName.equals(expectedForeignKey.mForeignKeyTableName)
                && mForeignKeyReferredColumn.equals(expectedForeignKey.mForeignKeyReferredColumn)) {

            List<Integer> flagList = mForeignKeyFlags;
            List<Integer> expectedFlags = expectedForeignKey.mForeignKeyFlags;
            Collections.sort(flagList);
            Collections.sort(expectedFlags);
            return flagList.equals(expectedFlags);
        }
        return false;
    }

    /**
     * Compares two ForeignKeyInfo and stores any backward incompatible change to the corresponding
     * ErrorInfo of foreignKey.
     */
    public void checkForeignKeyDiff(
            ForeignKeyInfo expectedForeignkeyInfo,
            List<String> modificationOfForeignKey,
            String tableName) {

        if (!mForeignKeyTableName.equals(expectedForeignkeyInfo.mForeignKeyTableName)) {
            modificationOfForeignKey.add(
                    "Referenced table has been changed for Foreign key: "
                            + mForeignKeyName
                            + " of table: "
                            + tableName);
        }
        if (!mForeignKeyReferredColumn.equals(expectedForeignkeyInfo.mForeignKeyReferredColumn)) {
            modificationOfForeignKey.add(
                    "Primary Key of Referenced table has been changed for Foreign key: "
                            + mForeignKeyName
                            + " of table: "
                            + tableName);
        }

        List<Integer> actualForeignKeyFlags = mForeignKeyFlags.stream().sorted().toList();
        List<Integer> expectedForeignKeyFlags =
                expectedForeignkeyInfo.mForeignKeyFlags.stream().sorted().toList();

        if (!actualForeignKeyFlags.equals(expectedForeignKeyFlags)) {
            modificationOfForeignKey.add(
                    "Foreign Key Flags have been changed for Foreign Key: "
                            + mForeignKeyName
                            + " of table: "
                            + tableName);
        }
    }
}
