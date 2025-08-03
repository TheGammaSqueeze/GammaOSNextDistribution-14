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

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.BMR_RECORD_BASAL_CALORIES_TOTAL;

import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.BasalMetabolicRateRecordInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.request.AggregateTableRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for BasalMetabolicRateRecord.
 *
 * @hide
 */
public final class BasalMetabolicRateRecordHelper
        extends InstantRecordHelper<BasalMetabolicRateRecordInternal> {
    public static final String BASAL_METABOLIC_RATE_RECORD_TABLE_NAME =
            "basal_metabolic_rate_record_table";
    public static final String BASAL_METABOLIC_RATE_COLUMN_NAME = "basal_metabolic_rate";

    public BasalMetabolicRateRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType, double result) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case BMR_RECORD_BASAL_CALORIES_TOTAL:
                return new AggregateResult<>(result).setZoneOffset(getZoneOffset(results));

            default:
                return null;
        }
    }

    @Override
    @NonNull
    public String getMainTableName() {
        return BASAL_METABOLIC_RATE_RECORD_TABLE_NAME;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case BMR_RECORD_BASAL_CALORIES_TOTAL:
                return new AggregateParams(
                        BASAL_METABOLIC_RATE_RECORD_TABLE_NAME,
                        new ArrayList(Arrays.asList(BASAL_METABOLIC_RATE_COLUMN_NAME)));
            default:
                return null;
        }
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull BasalMetabolicRateRecordInternal basalMetabolicRateRecord) {
        contentValues.put(
                BASAL_METABOLIC_RATE_COLUMN_NAME, basalMetabolicRateRecord.getBasalMetabolicRate());
    }

    @Override
    public double[] deriveAggregate(Cursor cursor, AggregateTableRequest request) {
        DeriveBasalCaloriesBurnedHelper deriveBasalCaloriesBurnedHelper =
                new DeriveBasalCaloriesBurnedHelper(
                        cursor, BASAL_METABOLIC_RATE_COLUMN_NAME, request.getTimeColumnName());
        List<Pair<Long, Long>> groupIntervals = request.getGroupSplitIntervals();
        return deriveBasalCaloriesBurnedHelper.getBasalCaloriesBurned(groupIntervals);
    }

    @Override
    protected void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull BasalMetabolicRateRecordInternal recordInternal) {
        recordInternal.setBasalMetabolicRate(
                getCursorDouble(cursor, BASAL_METABOLIC_RATE_COLUMN_NAME));
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Collections.singletonList(new Pair<>(BASAL_METABOLIC_RATE_COLUMN_NAME, REAL));
    }
}
