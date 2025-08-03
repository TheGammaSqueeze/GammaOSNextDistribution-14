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

import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.OR;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.util.ArrayMap;
import android.util.Pair;

import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** @hide */
public class UpsertTableRequest {
    public static final int INVALID_ROW_ID = -1;

    public static final int TYPE_STRING = 0;
    public static final int TYPE_BLOB = 1;
    private final String mTable;
    private ContentValues mContentValues;
    private final List<Pair<String, Integer>> mUniqueColumns;
    private List<UpsertTableRequest> mChildTableRequests = Collections.emptyList();
    private String mParentCol;
    private long mRowId = INVALID_ROW_ID;
    private WhereClauses mWhereClausesForUpdate;
    private IRequiresUpdate mRequiresUpdate = new IRequiresUpdate() {};
    private Integer mRecordType;
    private RecordInternal<?> mRecordInternal;
    private RecordHelper<?> mRecordHelper;

    private ArrayMap<String, Boolean> mExtraWritePermissionsStateMapping;

    public UpsertTableRequest(@NonNull String table, @NonNull ContentValues contentValues) {
        this(table, contentValues, Collections.emptyList());
    }

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public UpsertTableRequest(
            @NonNull String table,
            @NonNull ContentValues contentValues,
            @NonNull List<Pair<String, Integer>> uniqueColumns) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(contentValues);
        Objects.requireNonNull(uniqueColumns);

        mTable = table;
        mContentValues = contentValues;
        mUniqueColumns = uniqueColumns;
    }

    public int getUniqueColumnsCount() {
        return mUniqueColumns.size();
    }

    @NonNull
    public UpsertTableRequest withParentKey(long rowId) {
        mRowId = rowId;
        return this;
    }

    /**
     * Use this if you want to add row_id of the parent table to all the child entries in {@code
     * parentCol}
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    public UpsertTableRequest setParentColumnForChildTables(@Nullable String parentCol) {
        mParentCol = parentCol;
        return this;
    }

    @NonNull
    public UpsertTableRequest setRequiresUpdateClause(@NonNull IRequiresUpdate requiresUpdate) {
        Objects.requireNonNull(requiresUpdate);

        mRequiresUpdate = requiresUpdate;
        return this;
    }

    @NonNull
    public String getTable() {
        return mTable;
    }

    @NonNull
    public ContentValues getContentValues() {
        // Set the parent column of the creator of this requested to do that
        if (!Objects.isNull(mParentCol) && mRowId != INVALID_ROW_ID) {
            mContentValues.put(mParentCol, mRowId);
        }

        return mContentValues;
    }

    @NonNull
    public List<UpsertTableRequest> getChildTableRequests() {
        return mChildTableRequests;
    }

    @NonNull
    public UpsertTableRequest setChildTableRequests(
            @NonNull List<UpsertTableRequest> childTableRequests) {
        Objects.requireNonNull(childTableRequests);

        mChildTableRequests = childTableRequests;
        return this;
    }

    @NonNull
    public WhereClauses getUpdateWhereClauses() {
        if (mWhereClausesForUpdate == null) {
            return getReadWhereClauses();
        }

        return mWhereClausesForUpdate;
    }

    public UpsertTableRequest setUpdateWhereClauses(WhereClauses whereClauses) {
        Objects.requireNonNull(whereClauses);

        mWhereClausesForUpdate = whereClauses;
        return this;
    }

    public ReadTableRequest getReadRequest() {
        return new ReadTableRequest(getTable()).setWhereClause(getReadWhereClauses());
    }

    public ReadTableRequest getReadRequestUsingUpdateClause() {
        return new ReadTableRequest(getTable()).setWhereClause(getUpdateWhereClauses());
    }

    @NonNull
    private WhereClauses getReadWhereClauses() {
        WhereClauses readWhereClause = new WhereClauses(OR);

        for (Pair<String, Integer> uniqueColumn : mUniqueColumns) {
            switch (uniqueColumn.second) {
                 case TYPE_BLOB -> readWhereClause.addWhereEqualsClause(
                        uniqueColumn.first, StorageUtils.getHexString(
                                mContentValues.getAsByteArray(uniqueColumn.first)));
                 case TYPE_STRING -> readWhereClause.addWhereEqualsClause(
                         uniqueColumn.first, mContentValues.getAsString(uniqueColumn.first));
                default -> throw new UnsupportedOperationException(
                        "Unable to find type: " + uniqueColumn.second);
            }
        }

        return readWhereClause;
    }

    public boolean requiresUpdate(Cursor cursor, UpsertTableRequest request) {
        return mRequiresUpdate.requiresUpdate(cursor, getContentValues(), request);
    }

    public String getRowIdColName() {
        return RecordHelper.PRIMARY_COLUMN_NAME;
    }

    @RecordTypeIdentifier.RecordType
    public int getRecordType() {
        Objects.requireNonNull(mRecordType);
        return mRecordType;
    }

    public void setRecordType(@RecordTypeIdentifier.RecordType int recordIdentifier) {
        mRecordType = recordIdentifier;
    }

    public <T extends RecordInternal<?>> UpsertTableRequest setHelper(
            RecordHelper<?> recordHelper) {
        mRecordHelper = recordHelper;

        return this;
    }

    @NonNull
    public List<String> getAllChildTablesToDelete() {
        return mRecordHelper == null
                ? Collections.emptyList()
                : mRecordHelper.getChildTablesToDeleteOnRecordUpsert(
                        mExtraWritePermissionsStateMapping);
    }

    @NonNull
    public List<String> getAllChildTables() {
        return mRecordHelper == null ? Collections.emptyList() : mRecordHelper.getAllChildTables();
    }

    public RecordInternal<?> getRecordInternal() {
        return mRecordInternal;
    }

    public void setRecordInternal(RecordInternal<?> recordInternal) {
        mRecordInternal = recordInternal;
    }

    public <T extends RecordInternal<?>> UpsertTableRequest setExtraWritePermissionsStateMapping(
            ArrayMap<String, Boolean> extraWritePermissionsToState) {
        mExtraWritePermissionsStateMapping = extraWritePermissionsToState;
        return this;
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_STRING, TYPE_BLOB})
    public @interface ColumnType {}

    public interface IRequiresUpdate {
        default boolean requiresUpdate(
                Cursor cursor, ContentValues contentValues, UpsertTableRequest request) {
            return true;
        }
    }
}
