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
package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.TOTAL_CALORIES_BURNED_RECORD_ENERGY_TOTAL;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED;

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.TotalCaloriesBurnedRecordInternal;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.request.AggregateTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for TotalCaloriesBurnedRecord.
 *
 * @hide
 */
public final class TotalCaloriesBurnedRecordHelper
        extends IntervalRecordHelper<TotalCaloriesBurnedRecordInternal> {

    @VisibleForTesting
    public static final String TOTAL_CALORIES_BURNED_RECORD_TABLE_NAME =
            "total_calories_burned_record_table";

    private static final String ENERGY_COLUMN_NAME = "energy";

    public TotalCaloriesBurnedRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType, double aggregation) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case TOTAL_CALORIES_BURNED_RECORD_ENERGY_TOTAL:
                results.moveToFirst();
                ZoneOffset zoneOffset = getZoneOffset(results);
                return new AggregateResult<>(aggregation).setZoneOffset(zoneOffset);
            default:
                return null;
        }
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return TOTAL_CALORIES_BURNED_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case TOTAL_CALORIES_BURNED_RECORD_ENERGY_TOTAL:
                return new AggregateParams(
                        TOTAL_CALORIES_BURNED_RECORD_TABLE_NAME,
                        new ArrayList<>(List.of(ENERGY_COLUMN_NAME)),
                        Double.class);
            default:
                return null;
        }
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor,
            @NonNull TotalCaloriesBurnedRecordInternal totalCaloriesBurnedRecord) {
        totalCaloriesBurnedRecord.setEnergy(getCursorDouble(cursor, ENERGY_COLUMN_NAME));
    }

    @Override
    public double[] deriveAggregate(Cursor cursor, AggregateTableRequest request) {
        int index = 0;
        List<Pair<Long, Long>> groupIntervals = request.getGroupSplitIntervals();

        List<Long> priorityList =
                StorageUtils.getAppIdPriorityList(RECORD_TYPE_TOTAL_CALORIES_BURNED);
        MergeDataHelper mergeDataHelper =
                new MergeDataHelper(
                        cursor,
                        priorityList,
                        ENERGY_COLUMN_NAME,
                        Double.class,
                        request.getUseLocalTime());
        DeriveTotalCaloriesBurnedHelper deriveTotalCaloriesBurnedHelper =
                new DeriveTotalCaloriesBurnedHelper(
                        groupIntervals.get(0).first,
                        groupIntervals.get(groupIntervals.size() - 1).second,
                        priorityList,
                        request.getUseLocalTime());
        double[] totalCaloriesBurnedArray = new double[groupIntervals.size()];
        for (Pair<Long, Long> groupInterval : groupIntervals) {
            long groupStartTime = groupInterval.first;
            long groupEndTime = groupInterval.second;
            // Based on the number of groups calculate aggregate for each group by calling
            // MergeDataHelper by eliminate duplicate for overlapping time interval
            double total = mergeDataHelper.readCursor(groupStartTime, groupEndTime);
            // For only TotalCaloriesBurned aggregate request we derive data from
            // ActiveCaloriesRecord and BasalMetabolicRateRecord for empty intervals
            List<Pair<Instant, Instant>> emptyIntervalList =
                    mergeDataHelper.getEmptyIntervals(
                            Instant.ofEpochMilli(groupStartTime),
                            Instant.ofEpochMilli(groupEndTime));
            if (emptyIntervalList.size() > 0) {
                total += deriveTotalCaloriesBurnedHelper.getDerivedCalories(emptyIntervalList);
            }

            totalCaloriesBurnedArray[index++] = total;
        }
        deriveTotalCaloriesBurnedHelper.closeCursors();
        return totalCaloriesBurnedArray;
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull TotalCaloriesBurnedRecordInternal totalCaloriesBurnedRecord) {
        contentValues.put(ENERGY_COLUMN_NAME, totalCaloriesBurnedRecord.getEnergy());
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(ENERGY_COLUMN_NAME, REAL));
    }
}
