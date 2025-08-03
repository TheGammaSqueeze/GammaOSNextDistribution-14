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

package com.android.server.healthconnect.storage.datatypehelpers.aggregation;

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.ACTIVE_CALORIES_BURNED_RECORD_ACTIVE_CALORIES_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.DISTANCE_RECORD_DISTANCE_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.ELEVATION_RECORD_ELEVATION_GAINED_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.EXERCISE_SESSION_DURATION_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.FLOORS_CLIMBED_RECORD_FLOORS_CLIMBED_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.SLEEP_SESSION_DURATION_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.STEPS_RECORD_COUNT_TOTAL;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.WHEEL_CHAIR_PUSHES_RECORD_COUNT_TOTAL;

import android.database.Cursor;
import android.health.connect.Constants;
import android.health.connect.datatypes.AggregationType;
import android.util.ArrayMap;
import android.util.Slog;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.storage.request.AggregateParams;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Aggregates records with priorities.
 *
 * @hide
 */
public class PriorityRecordsAggregator {
    static final String TAG = "HealthPriorityRecordsAggregator";

    private final List<Long> mGroupSplits;
    private final Map<Long, Integer> mAppIdToPriority;
    private final Map<Integer, Double> mGroupToAggregationResult;
    private final Map<Integer, ZoneOffset> mGroupToFirstZoneOffset;
    private final int mNumberOfGroups;
    private int mCurrentGroup = -1;
    private long mLatestPopulatedStart = -1;
    @AggregationType.AggregationTypeIdentifier private final int mAggregationType;

    private final TreeSet<AggregationTimestamp> mTimestampsBuffer;
    private final TreeSet<AggregationRecordData> mOpenIntervals;

    private final AggregateParams.PriorityAggregationExtraParams mExtraParams;

    private final boolean mUseLocalTime;

    public PriorityRecordsAggregator(
            List<Long> groupSplits,
            List<Long> appIdPriorityList,
            @AggregationType.AggregationTypeIdentifier int aggregationType,
            AggregateParams.PriorityAggregationExtraParams extraParams,
            boolean useLocalTime) {
        mGroupSplits = groupSplits;
        mAggregationType = aggregationType;
        mExtraParams = extraParams;
        mAppIdToPriority = new ArrayMap<>();
        for (int i = 0; i < appIdPriorityList.size(); i++) {
            // Add to the map with -index, so app with higher priority has higher value in the map.
            mAppIdToPriority.put(appIdPriorityList.get(i), appIdPriorityList.size() - i);
        }
        mUseLocalTime = useLocalTime;
        mTimestampsBuffer = new TreeSet<>();
        mNumberOfGroups = mGroupSplits.size() - 1;
        mGroupToFirstZoneOffset = new ArrayMap<>(mNumberOfGroups);
        mOpenIntervals = new TreeSet<>();
        mGroupToAggregationResult = new ArrayMap<>(mGroupSplits.size());

        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Aggregation request for splits: "
                            + mGroupSplits
                            + " with priorities: "
                            + appIdPriorityList);
        }
    }

    /** Calculates aggregation result for each group. */
    public void calculateAggregation(Cursor cursor) {
        initialiseTimestampsBuffer(cursor);
        populateTimestampBuffer(cursor);
        AggregationTimestamp scanPoint, nextPoint;
        while (mTimestampsBuffer.size() > 1) {
            scanPoint = mTimestampsBuffer.pollFirst();
            nextPoint = mTimestampsBuffer.first();
            if (scanPoint.getType() == AggregationTimestamp.GROUP_BORDER) {
                mCurrentGroup += 1;
            } else if (scanPoint.getType() == AggregationTimestamp.INTERVAL_START) {
                mOpenIntervals.add(scanPoint.getParentData());
            } else if (scanPoint.getType() == AggregationTimestamp.INTERVAL_END) {
                mOpenIntervals.remove(scanPoint.getParentData());
            } else {
                throw new UnsupportedOperationException(
                        "Unknown aggregation timestamp type: " + scanPoint.getType());
            }
            updateAggregationResult(scanPoint, nextPoint);
            populateTimestampBuffer(cursor);
        }

        if (Constants.DEBUG) {
            Slog.d(TAG, "Aggregation result: " + mGroupToAggregationResult.toString());
        }
    }

    private void populateTimestampBuffer(Cursor cursor) {
        // Buffer populating strategy guarantees that at the moment we start to process the earliest
        // record, we added to the buffer later overlapping records and the first non-overlapping
        // record. It guarantees that the aggregation score can be calculated correctly for any
        // timestamp within the earliest record interval.
        if (mTimestampsBuffer.first().getType() != AggregationTimestamp.INTERVAL_START) {
            return;
        }

        // Add record timestamps to buffer until latest buffer record do not overlap with earliest
        // buffer record.
        long expansionBorder = mTimestampsBuffer.first().getParentData().getEndTime();
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Try to update buffer exp border: "
                            + expansionBorder
                            + " latest start: "
                            + mLatestPopulatedStart);
        }

        while (mLatestPopulatedStart <= expansionBorder && cursor.moveToNext()) {
            AggregationRecordData data = readNewDataAndMaybeAddToBuffer(cursor);
            if (data != null) {
                mLatestPopulatedStart = data.getStartTime();

                if (Constants.DEBUG) {
                    Slog.d(TAG, "Updated buffer with : " + data);
                }
            }
        }

        if (Constants.DEBUG) {
            Slog.d(TAG, "Timestamps buffer: " + mTimestampsBuffer);
        }
    }

    private void initialiseTimestampsBuffer(Cursor cursor) {
        for (Long groupSplit : mGroupSplits) {
            mTimestampsBuffer.add(
                    new AggregationTimestamp(AggregationTimestamp.GROUP_BORDER, groupSplit));
        }

        while (cursor.moveToNext()) {
            AggregationRecordData data = readNewDataAndMaybeAddToBuffer(cursor);
            if (data != null) {
                break;
            }
        }

        if (Constants.DEBUG) {
            Slog.d(TAG, "Initialised aggregation buffer: " + mTimestampsBuffer);
        }
    }

    @Nullable
    private AggregationRecordData readNewDataAndMaybeAddToBuffer(Cursor cursor) {
        AggregationRecordData data = readNewData(cursor);
        int priority = data.getPriority();

        if (HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isAggregationSourceControlsEnabled()
                && priority == Integer.MIN_VALUE) {
            return null;
        }

        // TODO(b/313924267): workaround for b/308467442, should be remove once we have a long term
        // solution
        if (data.getStartTime() > data.getEndTime()) {
            // skip records with start time > end time to keep the algorithm functional
            return null;
        }

        mTimestampsBuffer.add(data.getStartTimestamp());
        mTimestampsBuffer.add(data.getEndTimestamp());
        return data;
    }

    @VisibleForTesting
    AggregationRecordData readNewData(Cursor cursor) {
        AggregationRecordData data = createAggregationRecordData();
        data.populateAggregationData(cursor, mUseLocalTime, mAppIdToPriority);
        return data;
    }

    /** Returns result for the given group */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public Double getResultForGroup(Integer groupNumber) {
        return mGroupToAggregationResult.get(groupNumber);
    }

    /** Returns start time zone offset for the given group */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public ZoneOffset getZoneOffsetForGroup(Integer groupNumber) {
        return mGroupToFirstZoneOffset.get(groupNumber);
    }

    private AggregationRecordData createAggregationRecordData() {
        return switch (mAggregationType) {
            case STEPS_RECORD_COUNT_TOTAL,
                    ACTIVE_CALORIES_BURNED_RECORD_ACTIVE_CALORIES_TOTAL,
                    DISTANCE_RECORD_DISTANCE_TOTAL,
                    ELEVATION_RECORD_ELEVATION_GAINED_TOTAL,
                    FLOORS_CLIMBED_RECORD_FLOORS_CLIMBED_TOTAL,
                    WHEEL_CHAIR_PUSHES_RECORD_COUNT_TOTAL -> new ValueColumnAggregationData(
                    mExtraParams.getColumnToAggregateName(),
                    mExtraParams.getColumnToAggregateType());
            case SLEEP_SESSION_DURATION_TOTAL,
                    EXERCISE_SESSION_DURATION_TOTAL -> new SessionDurationAggregationData(
                    mExtraParams.getExcludeIntervalStartColumnName(),
                    mExtraParams.getExcludeIntervalEndColumnName());
            default -> throw new UnsupportedOperationException(
                    "Priority aggregation do not support type: " + mAggregationType);
        };
    }

    private void updateAggregationResult(
            AggregationTimestamp startPoint, AggregationTimestamp endPoint) {
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Updating result for group "
                            + mCurrentGroup
                            + " for interval: ("
                            + startPoint.getTime()
                            + ", "
                            + endPoint.getTime()
                            + ")");
        }

        if (mOpenIntervals.isEmpty() || mCurrentGroup < 0 || mCurrentGroup >= mNumberOfGroups) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "No open intervals or current group: " + mCurrentGroup);
            }
            return;
        }

        if (startPoint.getTime() == endPoint.getTime()
                && startPoint.getType() == AggregationTimestamp.GROUP_BORDER
                && endPoint.getType() == AggregationTimestamp.INTERVAL_END) {
            // Don't create new aggregation result as no open intervals in this group so far.
            return;
        }

        if (!mGroupToAggregationResult.containsKey(mCurrentGroup)) {
            mGroupToAggregationResult.put(mCurrentGroup, 0.0d);
        }

        if (Constants.DEBUG) {
            Slog.d(TAG, "Update result with: " + mOpenIntervals.last());
        }

        mGroupToAggregationResult.put(
                mCurrentGroup,
                mGroupToAggregationResult.get(mCurrentGroup)
                        + mOpenIntervals.last().getResultOnInterval(startPoint, endPoint));

        if (mCurrentGroup >= 0
                && !mGroupToFirstZoneOffset.containsKey(mCurrentGroup)
                && !mOpenIntervals.isEmpty()) {
            mGroupToFirstZoneOffset.put(mCurrentGroup, getZoneOffsetOfEarliestOpenInterval());
        }
    }

    private ZoneOffset getZoneOffsetOfEarliestOpenInterval() {
        AggregationRecordData earliestInterval = mOpenIntervals.first();
        for (AggregationRecordData data : mOpenIntervals) {
            if (data.getStartTime() < earliestInterval.getStartTime()) {
                earliestInterval = data;
            }
        }
        return earliestInterval.getStartTimeZoneOffset();
    }
}
