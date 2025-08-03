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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.datatypehelpers.ActiveCaloriesBurnedRecordHelper.ACTIVE_CALORIES_BURNED_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.ActiveCaloriesBurnedRecordHelper.ENERGY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.BasalMetabolicRateRecordHelper.BASAL_METABOLIC_RATE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.BasalMetabolicRateRecordHelper.BASAL_METABOLIC_RATE_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.InstantRecordHelper.LOCAL_DATE_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.InstantRecordHelper.TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.IntervalRecordHelper.LOCAL_DATE_TIME_START_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.IntervalRecordHelper.START_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.database.Cursor;
import android.util.Pair;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.utils.OrderByClause;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Helper class for deriving TotalCaloriesBurned aggregate from {@link
 * android.health.connect.datatypes.BasalMetabolicRateRecord} and {@link
 * android.health.connect.datatypes.ActiveCaloriesBurnedRecord}
 *
 * @hide
 */
public final class DeriveTotalCaloriesBurnedHelper {
    private final long mStartTime;
    private final long mEndTime;
    private final List<Long> mPriority;
    private Cursor mActiveCaloriesBurnedCursor;
    private Cursor mBasalCaloriesBurnedCursor;
    private MergeDataHelper mMergeDataHelper;
    private DeriveBasalCaloriesBurnedHelper mBasalCaloriesBurnedHelper;

    private String mInstantRecordTimeColumnName;

    private String mIntervalStartTimeColumnName;

    private boolean mUseLocalTime;

    public DeriveTotalCaloriesBurnedHelper(
            long startTime, long endTime, @NonNull List<Long> priorityList, boolean useLocaleTime) {
        Objects.requireNonNull(priorityList);
        mStartTime = startTime;
        mEndTime = endTime;
        mPriority = priorityList;
        mUseLocalTime = useLocaleTime;
        if (useLocaleTime) {
            mInstantRecordTimeColumnName = LOCAL_DATE_TIME_COLUMN_NAME;
            mIntervalStartTimeColumnName = LOCAL_DATE_TIME_START_TIME_COLUMN_NAME;
        } else {
            mInstantRecordTimeColumnName = TIME_COLUMN_NAME;
            mIntervalStartTimeColumnName = START_TIME_COLUMN_NAME;
        }
        inititalizeCursors();
    }

    private void inititalizeCursors() {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        mActiveCaloriesBurnedCursor =
                transactionManager.read(
                        new ReadTableRequest(ACTIVE_CALORIES_BURNED_RECORD_TABLE_NAME)
                                .setWhereClause(
                                        new WhereClauses(AND)
                                                .addWhereBetweenTimeClause(
                                                        mIntervalStartTimeColumnName,
                                                        mStartTime,
                                                        mEndTime))
                                .setOrderBy(
                                        new OrderByClause()
                                                .addOrderByClause(
                                                        mIntervalStartTimeColumnName, true)));
        mBasalCaloriesBurnedCursor =
                transactionManager.read(
                        new ReadTableRequest(BASAL_METABOLIC_RATE_RECORD_TABLE_NAME)
                                .setWhereClause(
                                        new WhereClauses(AND)
                                                .addWhereBetweenTimeClause(
                                                        mInstantRecordTimeColumnName,
                                                        mStartTime,
                                                        mEndTime))
                                .setOrderBy(
                                        new OrderByClause()
                                                .addOrderByClause(
                                                        mInstantRecordTimeColumnName, true)));
        mMergeDataHelper =
                new MergeDataHelper(
                        mActiveCaloriesBurnedCursor,
                        mPriority,
                        ENERGY_COLUMN_NAME,
                        Double.class,
                        mUseLocalTime);
        mBasalCaloriesBurnedHelper =
                new DeriveBasalCaloriesBurnedHelper(
                        mBasalCaloriesBurnedCursor,
                        BASAL_METABOLIC_RATE_COLUMN_NAME,
                        mInstantRecordTimeColumnName);
    }

    /** Close the cursors created */
    public void closeCursors() {
        if (mActiveCaloriesBurnedCursor != null) {
            mActiveCaloriesBurnedCursor.close();
        }
        if (mBasalCaloriesBurnedCursor != null) {
            mBasalCaloriesBurnedCursor.close();
        }
    }

    /**
     * Calculates and returns total derived calories for the empty interval time gaps where there is
     * no entry in {@link android.health.connect.datatypes.TotalCaloriesBurnedRecord}
     */
    public double getDerivedCalories(List<Pair<Instant, Instant>> emptyIntervalList) {
        double totalDerivedCalories = 0.0;
        for (Pair<Instant, Instant> instantInstantPair : emptyIntervalList) {
            long intervalStartTime = instantInstantPair.first.toEpochMilli();
            long intervalEndTime = instantInstantPair.second.toEpochMilli();
            totalDerivedCalories +=
                    mMergeDataHelper.readCursor(intervalStartTime, intervalEndTime)
                            + mBasalCaloriesBurnedHelper.getBasalCaloriesBurned(
                                    intervalStartTime, intervalEndTime);
        }
        return totalDerivedCalories;
    }
}
