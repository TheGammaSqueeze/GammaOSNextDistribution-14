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

import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.isNullValue;

import android.database.Cursor;
import android.health.connect.Constants;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Helper class to aggregate Sleep and Exercise Sessions.
 *
 * @hide
 */
public class SessionDurationAggregationData extends AggregationRecordData {
    private static final String TAG = "HealthSessionPriorityAggregation";
    private final String mExcludeIntervalStartTimeColumn;
    private final String mExcludeIntervalEndTimeColumn;
    private static final long MILLIS_IN_SECOND = 1000L;
    List<Long> mExcludeStarts;
    List<Long> mExcludeEnds;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public SessionDurationAggregationData(
            String excludeIntervalStartTimeColumn, String excludeIntervalEndTimeColumn) {
        mExcludeIntervalStartTimeColumn = excludeIntervalStartTimeColumn;
        mExcludeIntervalEndTimeColumn = excludeIntervalEndTimeColumn;
    }

    @Override
    double getResultOnInterval(AggregationTimestamp startPoint, AggregationTimestamp endPoint) {
        return AggregationRecordData.calculateIntervalOverlapDuration(
                        getStartTime(), startPoint.getTime(), getEndTime(), endPoint.getTime())
                - calculateDurationToExclude(startPoint.getTime(), endPoint.getTime());
    }

    @Override
    void populateSpecificAggregationData(Cursor cursor, boolean useLocalTime) {
        UUID currentSessionUuid = readUuid(cursor);
        do {
            // Populate stages from each row.
            updateIntervalsToExclude(cursor, useLocalTime);
        } while (cursor.moveToNext() && currentSessionUuid.equals(readUuid(cursor)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        cursor.moveToPrevious();

        if (mExcludeStarts != null) {
            mExcludeStarts.sort(Comparator.naturalOrder());
            mExcludeEnds.sort(Comparator.naturalOrder());

            if (Constants.DEBUG) {
                Slog.d(TAG, "Exclude intervals: " + mExcludeStarts + " ends: " + mExcludeEnds);
            }
        }
    }

    @VisibleForTesting
    SessionDurationAggregationData setExcludeIntervals(
            List<Long> excludeStarts, List<Long> excludeEnds) {
        mExcludeStarts = excludeStarts;
        mExcludeEnds = excludeEnds;
        return this;
    }

    private void updateIntervalsToExclude(Cursor cursor, boolean useLocalTime) {
        if (isNullValue(cursor, mExcludeIntervalStartTimeColumn)) {
            return;
        }

        if (mExcludeStarts == null) {
            mExcludeStarts = new ArrayList<>();
            mExcludeEnds = new ArrayList<>();
        }

        if (useLocalTime) {
            mExcludeStarts.add(calculateLocalTime(cursor, mExcludeIntervalStartTimeColumn));
            mExcludeEnds.add(calculateLocalTime(cursor, mExcludeIntervalEndTimeColumn));
        } else {
            mExcludeStarts.add(getCursorLong(cursor, mExcludeIntervalStartTimeColumn));
            mExcludeEnds.add(getCursorLong(cursor, mExcludeIntervalEndTimeColumn));
        }
    }

    private Long calculateLocalTime(Cursor cursor, String physicalColumnName) {
        return getCursorLong(cursor, physicalColumnName)
                + MILLIS_IN_SECOND * getStartTimeZoneOffset().getTotalSeconds();
    }

    private long calculateDurationToExclude(long startTime, long endTime) {
        if (mExcludeStarts == null) {
            // No intervals to exclude for this record data.
            return 0;
        }

        long durationToExclude = 0;
        // Find intervals to exclude which potentially can overlap with the given interval.

        // Find the latest start timestamp index such that intervalStart <= startTime
        int lowerBoundStartIndex = Collections.binarySearch(mExcludeStarts, startTime);
        if (lowerBoundStartIndex < 0) {
            // startTime not found in mExcludeStarts, bin search output = -(insertionIndex + 1)
            int insertionIndex = -lowerBoundStartIndex - 1;
            lowerBoundStartIndex = Math.max(insertionIndex - 1, 0);
        }

        // Find the earliest end timestamp index such that intervalEnd >= endTime
        int upperBoundEndIndex = Collections.binarySearch(mExcludeEnds, endTime);
        if (upperBoundEndIndex < 0) {
            // endTime not found, bin search output = - (insertionIndex + 1) = -upper bound
            upperBoundEndIndex = -upperBoundEndIndex;
        }

        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Excluding overlaps with intervals within indexes "
                            + lowerBoundStartIndex
                            + " and "
                            + upperBoundEndIndex);
        }

        for (int index = lowerBoundStartIndex;
                index < Math.min(upperBoundEndIndex + 1, mExcludeStarts.size());
                index++) {
            durationToExclude +=
                    AggregationRecordData.calculateIntervalOverlapDuration(
                            mExcludeStarts.get(index), startTime,
                            mExcludeEnds.get(index), endTime);
        }

        return durationToExclude;
    }
}
