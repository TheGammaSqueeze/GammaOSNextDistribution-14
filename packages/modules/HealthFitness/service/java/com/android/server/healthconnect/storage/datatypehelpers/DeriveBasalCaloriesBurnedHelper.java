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

import static com.android.server.healthconnect.storage.datatypehelpers.BasalMetabolicRateRecordHelper.BASAL_METABOLIC_RATE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.BasalMetabolicRateRecordHelper.BASAL_METABOLIC_RATE_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.HeightRecordHelper.HEIGHT_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.HeightRecordHelper.HEIGHT_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.LeanBodyMassRecordHelper.LEAN_BODY_MASS_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.LeanBodyMassRecordHelper.MASS_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.WeightRecordHelper.WEIGHT_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.WeightRecordHelper.WEIGHT_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.database.Cursor;
import android.health.connect.Constants;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.util.Pair;
import android.util.Slog;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.utils.OrderByClause;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Helper class to Derive BasalCaloriesTotal aggregate
 *
 * @hide
 */
public final class DeriveBasalCaloriesBurnedHelper {
    private static final int KCAL_TO_CAL = 1000;
    private static final double GMS_IN_KG = 1000.0;
    private static final double WATT_TO_CAL_PER_HR = 860;
    private static final int HOURS_PER_DAY = 24;
    private static final double DEFAULT_WEIGHT_IN_GMS = 73000;
    private static final double DEFAULT_HEIGHT_IN_METERS = 1.7;
    private static final int DEFAULT_GENDER_CONSTANT = -78;
    private static final String TAG = "DeriveBasalCalories";
    private final Cursor mCursor;
    private final String mColumnName;
    private double mRateOfEnergyBurntInWatts = 0;
    private String mTimeColumnName;

    @SuppressWarnings("GoodTime") // constant age represented by primitive
    private static final int DEFAULT_AGE = 30;

    public DeriveBasalCaloriesBurnedHelper(
            @NonNull Cursor cursor, @NonNull String columnName, @NonNull String timeColumnName) {
        Objects.requireNonNull(cursor);
        Objects.requireNonNull(columnName);
        Objects.requireNonNull(timeColumnName);
        mCursor = cursor;
        mColumnName = columnName;
        mTimeColumnName = timeColumnName;
    }

    /**
     * Calculates and returns aggregate of total basal calories burned from table {@link
     * BasalMetabolicRateRecord} for the interval.
     */
    @NonNull
    public double getBasalCaloriesBurned(long intervalStartTime, long intervalEndTime) {
        if (intervalStartTime >= intervalEndTime) {
            return 0;
        }

        double currentGroupTotal = 0;
        // calculate aggregate for current interval between start and end time by iterating
        // cursor until current time is inside current group interval
        if (mCursor.getCount() == 0) {
            return derivedBasalCaloriesViaReadBack(intervalStartTime, intervalEndTime);
        }

        long lastItemTime = -1;
        while (mCursor.moveToNext()) {
            long time = StorageUtils.getCursorLong(mCursor, mTimeColumnName);

            if (lastItemTime == -1) {
                if (time > intervalStartTime && mRateOfEnergyBurntInWatts == 0) {
                    if (time > intervalEndTime) {
                        mCursor.moveToPrevious();
                        return derivedBasalCaloriesViaReadBack(intervalStartTime, intervalEndTime);
                    }

                    currentGroupTotal += derivedBasalCaloriesViaReadBack(intervalStartTime, time);
                    lastItemTime = time;
                } else {
                    lastItemTime = intervalStartTime;
                }

                mRateOfEnergyBurntInWatts = StorageUtils.getCursorDouble(mCursor, mColumnName);
                continue;
            }

            if (time >= intervalEndTime) {
                mCursor.moveToPrevious();
                break;
            }

            currentGroupTotal +=
                    getCurrentIntervalEnergy(mRateOfEnergyBurntInWatts, lastItemTime, time);
            mRateOfEnergyBurntInWatts = StorageUtils.getCursorDouble(mCursor, mColumnName);
            lastItemTime = time;
        }

        if (lastItemTime == -1) {
            currentGroupTotal +=
                    getCurrentIntervalEnergy(
                            mRateOfEnergyBurntInWatts, intervalStartTime, intervalEndTime);
        } else if (lastItemTime < intervalEndTime) {
            currentGroupTotal +=
                    getCurrentIntervalEnergy(
                            mRateOfEnergyBurntInWatts, lastItemTime, intervalEndTime);
        }

        return currentGroupTotal;
    }

    private double derivedBasalCaloriesViaReadBack(long intervalStartTime, long intervalEndTime) {
        if (mRateOfEnergyBurntInWatts != 0) {
            return getCurrentIntervalEnergy(
                    mRateOfEnergyBurntInWatts, intervalStartTime, intervalEndTime);
        }

        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor =
                transactionManager.read(
                        new ReadTableRequest(BASAL_METABOLIC_RATE_RECORD_TABLE_NAME)
                                .setColumnNames(List.of(BASAL_METABOLIC_RATE_COLUMN_NAME))
                                .setWhereClause(
                                        new WhereClauses(AND)
                                                .addWhereLessThanOrEqualClause(
                                                        mTimeColumnName, intervalStartTime))
                                .setLimit(1)
                                .setOrderBy(
                                        new OrderByClause()
                                                .addOrderByClause(mTimeColumnName, false)))) {
            if (cursor.getCount() == 0) {
                // No data found, fallback to LBM
                return derivedBasalCaloriesBurnedFromLeanBodyMass(
                        intervalStartTime, intervalEndTime);
            }
            cursor.moveToNext();
            mRateOfEnergyBurntInWatts =
                    StorageUtils.getCursorDouble(cursor, BASAL_METABOLIC_RATE_COLUMN_NAME);
            return getCurrentIntervalEnergy(
                    mRateOfEnergyBurntInWatts, intervalStartTime, intervalEndTime);
        }
    }

    private double derivedBasalCaloriesBurnedFromLeanBodyMass(
            long intervalStartTime, long intervalEndTime) {
        double totalCalories = 0;
        try (Cursor lbmCursor = getLeanBodyMassCursor(intervalStartTime, intervalEndTime)) {
            if (lbmCursor.getCount() == 0) {
                // No data found, fallback to profile data
                return derivedBasalCaloriesBurnedFromProfile(intervalStartTime, intervalEndTime);
            }

            long lastReadTime = -1;
            double bmrFromLbmInCaloriesPerDay = 0;
            while (lbmCursor.moveToNext()) {
                double mass = StorageUtils.getCursorDouble(lbmCursor, MASS_COLUMN_NAME);
                long time = StorageUtils.getCursorLong(lbmCursor, mTimeColumnName);
                if (lastReadTime == -1) {
                    // Derive calories from profile for start time to first entry time, if required
                    if (time > intervalStartTime) {
                        totalCalories +=
                                derivedBasalCaloriesBurnedFromProfile(intervalStartTime, time);
                        lastReadTime = time;
                    } else {
                        lastReadTime = intervalStartTime;
                    }
                    bmrFromLbmInCaloriesPerDay = getBmrFromLbmInCaloriesPerDay(mass);
                    continue;
                }

                totalCalories += getCalories(bmrFromLbmInCaloriesPerDay, lastReadTime, time);

                bmrFromLbmInCaloriesPerDay = getBmrFromLbmInCaloriesPerDay(mass);
                lastReadTime = time;
            }

            if (lastReadTime < intervalEndTime) {
                totalCalories +=
                        getCalories(bmrFromLbmInCaloriesPerDay, lastReadTime, intervalEndTime);
            }
        }

        return totalCalories;
    }

    private double getBmrFromLbmInCaloriesPerDay(double massInGms) {
        return (370 + 21.6 * (massInGms / GMS_IN_KG)) * KCAL_TO_CAL;
    }

    // TODO(b/302521219): Restructure this derivation logic
    private double derivedBasalCaloriesBurnedFromProfile(
            long intervalStartTime, long intervalEndTime) {
        double caloriesFromProfile = 0;
        try (Cursor heightCursor = getHeightCursor(intervalStartTime, intervalEndTime);
                Cursor weightCursor = getWeightCursor(intervalStartTime, intervalEndTime)) {
            if (heightCursor.getCount() == 0 && weightCursor.getCount() == 0) {
                return getCaloriesFromHeightAndWeight(
                        DEFAULT_HEIGHT_IN_METERS,
                        DEFAULT_WEIGHT_IN_GMS,
                        intervalStartTime,
                        intervalEndTime);
            }

            boolean hasHeight = heightCursor.moveToNext();
            boolean hasWeight = weightCursor.moveToNext();
            long lastTimeUsed = -1;
            double height = DEFAULT_HEIGHT_IN_METERS;
            double weight = DEFAULT_WEIGHT_IN_GMS;

            long heightTime = Long.MAX_VALUE;
            long weightTime = Long.MAX_VALUE;
            while (hasHeight || hasWeight) {
                if (hasHeight) {
                    heightTime = StorageUtils.getCursorLong(heightCursor, mTimeColumnName);
                }

                if (hasWeight) {
                    weightTime = StorageUtils.getCursorLong(weightCursor, mTimeColumnName);
                }

                if (lastTimeUsed < intervalStartTime) {
                    lastTimeUsed = Math.min(heightTime, weightTime);
                    if (lastTimeUsed > intervalStartTime) {
                        caloriesFromProfile +=
                                getCaloriesFromHeightAndWeight(
                                        height, weight, intervalStartTime, lastTimeUsed);
                    }
                } else {
                    long time = Math.min(heightTime, weightTime);
                    caloriesFromProfile +=
                            getCaloriesFromHeightAndWeight(height, weight, lastTimeUsed, time);
                    lastTimeUsed = time;
                }

                // Move the cursor one by one to calculate BMR as accurately as possible.
                if ((heightTime < weightTime) && hasHeight) {
                    height = StorageUtils.getCursorDouble(heightCursor, HEIGHT_COLUMN_NAME);
                    hasHeight = heightCursor.moveToNext();
                } else if ((weightTime < heightTime) && hasWeight) {
                    weight = StorageUtils.getCursorDouble(weightCursor, WEIGHT_COLUMN_NAME);
                    hasWeight = weightCursor.moveToNext();
                } else {
                    if (hasWeight) {
                        weight = StorageUtils.getCursorDouble(weightCursor, WEIGHT_COLUMN_NAME);
                        hasWeight = weightCursor.moveToNext();
                    }

                    if (hasHeight) {
                        height = StorageUtils.getCursorDouble(heightCursor, HEIGHT_COLUMN_NAME);
                        hasHeight = heightCursor.moveToNext();
                    }
                }
            }

            if (lastTimeUsed < intervalEndTime) {
                caloriesFromProfile +=
                        getCaloriesFromHeightAndWeight(
                                height,
                                weight,
                                // Snap to startTime in case the last-used record is still before
                                // the startTime of the interval
                                Math.max(intervalStartTime, lastTimeUsed),
                                intervalEndTime);
            }
        }

        return caloriesFromProfile;
    }

    private Cursor getLeanBodyMassCursor(long intervalStartTime, long intervalEndTime) {
        return getReadCursorForDerivingBMR(
                intervalStartTime,
                intervalEndTime,
                LEAN_BODY_MASS_RECORD_TABLE_NAME,
                MASS_COLUMN_NAME);
    }

    private Cursor getHeightCursor(long intervalStartTime, long intervalEndTime) {
        return getReadCursorForDerivingBMR(
                intervalStartTime, intervalEndTime, HEIGHT_RECORD_TABLE_NAME, HEIGHT_COLUMN_NAME);
    }

    private Cursor getWeightCursor(long intervalStartTime, long intervalEndTime) {
        return getReadCursorForDerivingBMR(
                intervalStartTime, intervalEndTime, WEIGHT_RECORD_TABLE_NAME, WEIGHT_COLUMN_NAME);
    }

    private Cursor getReadCursorForDerivingBMR(
            long intervalStartTime, long intervalEndTime, String tableName, String colName) {
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        return transactionManager.read(
                new ReadTableRequest(tableName)
                        .setColumnNames(List.of(colName, mTimeColumnName))
                        .setWhereClause(
                                new WhereClauses(AND)
                                        .addWhereBetweenTimeClause(
                                                mTimeColumnName,
                                                intervalStartTime,
                                                intervalEndTime))
                        .setOrderBy(new OrderByClause().addOrderByClause(mTimeColumnName, true))
                        .setUnionReadRequests(
                                List.of(
                                        new ReadTableRequest(tableName)
                                                .setColumnNames(List.of(colName, mTimeColumnName))
                                                .setWhereClause(
                                                        new WhereClauses(AND)
                                                                .addWhereLessThanOrEqualClause(
                                                                        mTimeColumnName,
                                                                        intervalStartTime))
                                                .setLimit(1)
                                                .setOrderBy(
                                                        new OrderByClause()
                                                                .addOrderByClause(
                                                                        mTimeColumnName, false)))));
    }

    /**
     * Calculates and returns an array of aggregate of total basal calories burned from table {@link
     * BasalMetabolicRateRecord} for group of intervals.
     */
    public double[] getBasalCaloriesBurned(@NonNull List<Pair<Long, Long>> groupIntervalList) {
        double[] basalCaloriesBurned = new double[groupIntervalList.size()];
        for (int group = 0; group < groupIntervalList.size(); group++) {
            basalCaloriesBurned[group] =
                    getBasalCaloriesBurned(
                            groupIntervalList.get(group).first,
                            groupIntervalList.get(group).second);
        }
        return basalCaloriesBurned;
    }

    private double getCaloriesFromHeightAndWeight(
            double height, double weight, long startTime, long endTime) {
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Calculating calories from profile start time: "
                            + startTime
                            + " end time: "
                            + endTime
                            + " height: "
                            + height
                            + " weight: "
                            + weight);
        }

        double bmrInCaloriesPerDay =
                (10 * (weight / GMS_IN_KG)
                                + 6.25 * height * 100
                                - 5 * DEFAULT_AGE
                                + DEFAULT_GENDER_CONSTANT)
                        * KCAL_TO_CAL;
        return bmrInCaloriesPerDay
                * ((double) (endTime - startTime) / Duration.ofDays(1).toMillis());
    }

    private double getCalories(double bmrInCaloriesPerDay, long startTime, long endTime) {
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Calculating calories from BMR start time: "
                            + startTime
                            + " end time: "
                            + endTime
                            + " bmrInCaloriesPerDay: "
                            + bmrInCaloriesPerDay);
        }

        return bmrInCaloriesPerDay
                * ((double) (endTime - startTime) / Duration.ofDays(1).toMillis());
    }

    private double getCurrentIntervalEnergy(
            double rateOfEnergyBurntInWatts, long startTime, long endTime) {
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Calculating calories from LBM start time: "
                            + startTime
                            + " end time: "
                            + endTime
                            + " bmrInCaloriesPerDay: "
                            + rateOfEnergyBurntInWatts);
        }
        return getCalPerDay(rateOfEnergyBurntInWatts)
                * ((double) (endTime - startTime) / Duration.ofDays(1).toMillis());
    }

    private double getCalPerDay(double rateOfEnergyBurntInWatt) {
        return rateOfEnergyBurntInWatt * HOURS_PER_DAY * WATT_TO_CAL_PER_HR;
    }
}
