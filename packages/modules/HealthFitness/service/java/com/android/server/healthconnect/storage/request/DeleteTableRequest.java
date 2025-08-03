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

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_UNKNOWN;

import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.Constants;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.Slog;

import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * No need to have delete-requests for child tables as ideally they should be following cascaded
 * deletes. If not please rethink the table structure and if possible remove the parent-child
 * relationship.
 *
 * @hide
 */
public class DeleteTableRequest {

    private static final String TAG = "HealthConnectDelete";
    private final String mTableName;
    @RecordTypeIdentifier.RecordType private final int mRecordType;

    private String mIdColumnName;
    private String mPackageColumnName;
    private String mTimeColumnName;
    private List<Long> mPackageFilters;
    private long mStartTime = DEFAULT_LONG;
    private long mEndTime = DEFAULT_LONG;
    private boolean mRequiresUuId;
    private List<String> mIds;
    private boolean mEnforcePackageCheck;
    private int mNumberOfUuidsToDelete;
    private WhereClauses mCustomWhereClauses;
    private long mLessThanOrEqualValue;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public DeleteTableRequest(
            @NonNull String tableName, @RecordTypeIdentifier.RecordType int recordType) {
        Objects.requireNonNull(tableName);

        mTableName = tableName;
        mRecordType = recordType;
    }

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public DeleteTableRequest(@NonNull String tableName) {
        Objects.requireNonNull(tableName);

        mTableName = tableName;
        mRecordType = RECORD_TYPE_UNKNOWN;
    }

    public String getPackageColumnName() {
        return mPackageColumnName;
    }

    public boolean requiresPackageCheck() {
        return mEnforcePackageCheck;
    }

    public DeleteTableRequest setEnforcePackageCheck(
            String packageColumnName, String uuidColumnName) {
        mEnforcePackageCheck = true;
        mPackageColumnName = packageColumnName;
        mIdColumnName = uuidColumnName;
        return this;
    }

    public DeleteTableRequest setIds(@NonNull String idColumnName, @NonNull List<String> ids) {
        Objects.requireNonNull(ids);
        Objects.requireNonNull(idColumnName);

        mIds = ids.stream().map(StorageUtils::getNormalisedString).toList();
        mIdColumnName = idColumnName;
        return this;
    }

    public DeleteTableRequest setId(@NonNull String idColumnName, @NonNull String id) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(idColumnName);

        mIds = Collections.singletonList(StorageUtils.getNormalisedString(id));
        mIdColumnName = idColumnName;
        return this;
    }

    public boolean requiresRead() {
        return mRequiresUuId || mEnforcePackageCheck;
    }

    public DeleteTableRequest setRequiresUuId(@NonNull String idColumnName) {
        Objects.requireNonNull(idColumnName);

        mRequiresUuId = true;
        mIdColumnName = idColumnName;

        return this;
    }

    public int getRecordType() {
        return mRecordType;
    }

    @Nullable
    public String getIdColumnName() {
        return mIdColumnName;
    }

    @Nullable
    public List<String> getIds() {
        return mIds;
    }

    @NonNull
    public String getTableName() {
        return mTableName;
    }

    @NonNull
    public DeleteTableRequest setPackageFilter(
            String packageColumnName, List<Long> packageFilters) {
        mPackageFilters = packageFilters;
        mPackageColumnName = packageColumnName;

        return this;
    }

    @NonNull
    public String getDeleteCommand() {
        return "DELETE FROM " + mTableName + getWhereCommand();
    }

    public String getReadCommand() {
        return "SELECT "
                + mIdColumnName
                + ", "
                + mPackageColumnName
                + " FROM "
                + mTableName
                + getWhereCommand();
    }

    public String getWhereCommand() {
        WhereClauses whereClauses =
                Objects.isNull(mCustomWhereClauses) ? new WhereClauses(AND) : mCustomWhereClauses;
        whereClauses.addWhereInLongsClause(mPackageColumnName, mPackageFilters);
        whereClauses.addWhereBetweenTimeClause(mTimeColumnName, mStartTime, mEndTime);
        whereClauses.addWhereInClauseWithoutQuotes(mIdColumnName, mIds);

        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "delete query: tableName: "
                            + mTableName
                            + " whereClause: "
                            + whereClauses.get(true));
        }

        return whereClauses.get(true);
    }

    @NonNull
    public DeleteTableRequest setTimeFilter(
            @NonNull String timeColumnName, long startTime, long endTime) {
        Objects.requireNonNull(timeColumnName);

        // Return if the params will result in no impact on the query
        if (startTime < 0 || endTime < startTime) {
            return this;
        }

        mStartTime = startTime;
        mEndTime = endTime;
        mTimeColumnName = timeColumnName;

        return this;
    }

    /**
     * Sets total number of UUIDs being deleted by this request.
     *
     * @param numberOfUuidsToDelete Number of UUIDs being deleted
     */
    public void setNumberOfUuidsToDelete(int numberOfUuidsToDelete) {
        this.mNumberOfUuidsToDelete = numberOfUuidsToDelete;
    }

    /**
     * Total number of records deleted.
     *
     * @return Number of records deleted by this request
     */
    public int getTotalNumberOfRecordsDeleted() {
        if (requiresRead()) {
            return mNumberOfUuidsToDelete;
        }
        return mIds.size();
    }
}
