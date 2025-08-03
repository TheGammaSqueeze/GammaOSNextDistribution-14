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

import static android.health.connect.datatypes.AggregationType.AVG;
import static android.health.connect.datatypes.AggregationType.COUNT;
import static android.health.connect.datatypes.AggregationType.MAX;
import static android.health.connect.datatypes.AggregationType.MIN;
import static android.health.connect.datatypes.AggregationType.SUM;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.APP_INFO_ID_COLUMN_NAME;

import android.annotation.NonNull;
import android.database.Cursor;
import android.health.connect.AggregateResult;
import android.health.connect.Constants;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeRangeFilter;
import android.health.connect.TimeRangeFilterHelper;
import android.health.connect.datatypes.AggregationType;
import android.util.ArrayMap;
import android.util.Pair;
import android.util.Slog;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.aggregation.PriorityRecordsAggregator;
import com.android.server.healthconnect.storage.utils.OrderByClause;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A request for {@link TransactionManager} to query the DB for aggregation results
 *
 * @hide
 */
public class AggregateTableRequest {
    private static final String TAG = "HealthConnectAggregate";
    private static final String GROUP_BY_COLUMN_NAME = "category";

    private static final int MAX_NUMBER_OF_GROUPS = Constants.MAXIMUM_PAGE_SIZE;

    private final String mTableName;
    private final List<String> mColumnNamesToAggregate;
    private final AggregationType<?> mAggregationType;
    private final RecordHelper<?> mRecordHelper;
    private final Map<Integer, AggregateResult<?>> mAggregateResults = new ArrayMap<>();

    /**
     * Represents "start time" for interval record, and "time" for instant record.
     *
     * <p>{@link #mUseLocalTime} is already taken into account when this field is set, meaning if
     * {@link #mUseLocalTime} is {@code true}, then this field represent local time, otherwise
     * physical time.
     */
    private final String mTimeColumnName;

    private final WhereClauses mWhereClauses;
    private final SqlJoin mSqlJoin;
    private String mGroupByColumnName;
    private int mGroupBySize = 1;
    private final List<String> mAdditionalColumnsToFetch;
    private final AggregateParams.PriorityAggregationExtraParams mPriorityParams;
    private final boolean mUseLocalTime;
    private List<Long> mTimeSplits;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public AggregateTableRequest(
            AggregateParams params,
            AggregationType<?> aggregationType,
            RecordHelper<?> recordHelper,
            WhereClauses whereClauses,
            boolean useLocalTime) {
        mTableName = params.getTableName();
        mColumnNamesToAggregate = params.getColumnsToFetch();
        mTimeColumnName = params.getTimeColumnName();
        mAggregationType = aggregationType;
        mRecordHelper = recordHelper;
        mSqlJoin = params.getJoin();
        mPriorityParams = params.getPriorityAggregationExtraParams();
        mWhereClauses = whereClauses;
        mAdditionalColumnsToFetch = new ArrayList<>();
        mAdditionalColumnsToFetch.add(params.getTimeOffsetColumnName());
        mAdditionalColumnsToFetch.add(mTimeColumnName);
        String endTimeColumnName = params.getExtraTimeColumnName();
        if (endTimeColumnName != null) {
            mAdditionalColumnsToFetch.add(endTimeColumnName);
        }
        mUseLocalTime = useLocalTime;
    }

    /**
     * @return {@link AggregationType} for this request
     */
    public AggregationType<?> getAggregationType() {
        return mAggregationType;
    }

    /**
     * @return {@link RecordHelper} for this request
     */
    public RecordHelper<?> getRecordHelper() {
        return mRecordHelper;
    }

    /**
     * @return results fetched after performing aggregate operation for this class.
     *     <p>Note: Only available after the call to {@link
     *     TransactionManager#populateWithAggregation} has been made
     */
    public List<AggregateResult<?>> getAggregateResults() {
        List<AggregateResult<?>> aggregateResults = new ArrayList<>(mGroupBySize);
        for (int i = 0; i < mGroupBySize; i++) {
            aggregateResults.add(mAggregateResults.get(i));
        }

        return aggregateResults;
    }

    /** Returns SQL statement to get data origins for the aggregation operation */
    public String getCommandToFetchAggregateMetadata() {
        final StringBuilder builder = new StringBuilder("SELECT DISTINCT ");
        builder.append(APP_INFO_ID_COLUMN_NAME).append(", ");
        return appendAggregateCommand(builder, /* isMetadata= */ true);
    }

    /** Returns name of the main time column (start time for Interval, time for Instant records) */
    public String getTimeColumnName() {
        return mTimeColumnName;
    }

    /** Returns whether request is using local time instead of physical one. */
    public boolean getUseLocalTime() {
        return mUseLocalTime;
    }

    /** Returns SQL statement to perform aggregation operation */
    @NonNull
    public String getAggregationCommand() {
        final StringBuilder builder = new StringBuilder("SELECT ");
        String aggCommand;
        boolean usingPriority =
                StorageUtils.supportsPriority(
                                mRecordHelper.getRecordIdentifier(),
                                mAggregationType.getAggregateOperationType())
                        || StorageUtils.isDerivedType(mRecordHelper.getRecordIdentifier());
        if (usingPriority) {
            for (String columnName : mColumnNamesToAggregate) {
                builder.append(columnName).append(", ");
            }
        } else {
            aggCommand = getSqlCommandFor(mAggregationType.getAggregateOperationType());

            for (String columnName : mColumnNamesToAggregate) {
                builder.append(aggCommand)
                        .append("(")
                        .append(columnName)
                        .append(")")
                        .append(" as ")
                        .append(columnName)
                        .append(", ");
            }
        }

        if (mAdditionalColumnsToFetch != null) {
            for (String additionalColumnToFetch : mAdditionalColumnsToFetch) {
                builder.append(additionalColumnToFetch).append(", ");
            }
        }

        return appendAggregateCommand(builder, usingPriority);
    }

    /** Sets time filter for table request. */
    public AggregateTableRequest setTimeFilter(long startTime, long endTime) {
        // Return if the params will result in no impact on the query
        if (startTime < 0 || endTime < startTime) {
            return this;
        }

        mTimeSplits = List.of(startTime, endTime);
        return this;
    }

    /** Sets group by fields. */
    public void setGroupBy(
            String columnName, Period period, Duration duration, TimeRangeFilter timeRangeFilter) {
        mGroupByColumnName = columnName;
        if (period != null) {
            mTimeSplits = getGroupSplitsForPeriod(timeRangeFilter, period);
        } else if (duration != null) {
            mTimeSplits = getGroupSplitsForDuration(timeRangeFilter, duration);
        } else {
            throw new IllegalArgumentException(
                    "Either aggregation period or duration should be not null");
        }
        mGroupBySize = mTimeSplits.size() - 1;

        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Group aggregation splits: "
                            + mTimeSplits
                            + " number of groups: "
                            + mGroupBySize);
        }
    }

    public void onResultsFetched(Cursor cursor, Cursor metaDataCursor) {
        if (StorageUtils.isDerivedType(mRecordHelper.getRecordIdentifier())) {
            deriveAggregate(cursor);
        } else if (StorageUtils.supportsPriority(
                mRecordHelper.getRecordIdentifier(),
                mAggregationType.getAggregateOperationType())) {
            processPriorityRequest(cursor);
        } else {
            processNoPrioritiesRequest(cursor);
        }

        updateResultWithDataOriginPackageNames(metaDataCursor);
    }

    private void processPriorityRequest(Cursor cursor) {
        List<Long> priorityList =
                StorageUtils.getAppIdPriorityList(mRecordHelper.getRecordIdentifier());
        PriorityRecordsAggregator aggregator =
                new PriorityRecordsAggregator(
                        mTimeSplits,
                        priorityList,
                        mAggregationType.getAggregationTypeIdentifier(),
                        mPriorityParams,
                        mUseLocalTime);
        aggregator.calculateAggregation(cursor);
        AggregateResult<?> result;
        for (int groupNumber = 0; groupNumber < mGroupBySize; groupNumber++) {
            if (aggregator.getResultForGroup(groupNumber) == null) {
                continue;
            }

            if (mAggregationType.getAggregateResultClass() == Long.class) {
                result =
                        new AggregateResult<>(
                                aggregator.getResultForGroup(groupNumber).longValue());
            } else {
                result = new AggregateResult<>(aggregator.getResultForGroup(groupNumber));
            }
            mAggregateResults.put(
                    groupNumber,
                    result.setZoneOffset(aggregator.getZoneOffsetForGroup(groupNumber)));
        }

        if (Constants.DEBUG) {
            Slog.d(TAG, "Priority aggregation result: " + mAggregateResults);
        }
    }

    private void processNoPrioritiesRequest(Cursor cursor) {
        while (cursor.moveToNext()) {
            mAggregateResults.put(
                    StorageUtils.getCursorInt(cursor, GROUP_BY_COLUMN_NAME),
                    mRecordHelper.getAggregateResult(cursor, mAggregationType));
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private static String getSqlCommandFor(@AggregationType.AggregateOperationType int type) {
        return switch (type) {
            case MAX -> "MAX";
            case MIN -> "MIN";
            case AVG -> "AVG";
            case SUM -> "SUM";
            case COUNT -> "COUNT";
            default -> null;
        };
    }

    private String appendAggregateCommand(StringBuilder builder, boolean isMetadata) {
        boolean useGroupBy = mGroupByColumnName != null && !isMetadata;
        if (useGroupBy) {
            builder.append(" CASE ");
            int groupByIndex = 0;
            for (int i = 0; i < mTimeSplits.size() - 1; i++) {
                builder.append(" WHEN ")
                        .append(mTimeColumnName)
                        .append(" >= ")
                        .append(mTimeSplits.get(i))
                        .append(" AND ")
                        .append(mTimeColumnName)
                        .append(" < ")
                        .append(mTimeSplits.get(i + 1))
                        .append(" THEN ")
                        .append(groupByIndex++);
            }
            builder.append(" END " + GROUP_BY_COLUMN_NAME + " ");
        } else {
            builder.setLength(builder.length() - 2); // Remove the last 2 char i.e. ", "
        }

        builder.append(" FROM ").append(mTableName);
        if (mSqlJoin != null) {
            builder.append(mSqlJoin.getJoinCommand());
        }

        builder.append(mWhereClauses.get(/* withWhereKeyword= */ true));

        if (useGroupBy) {
            builder.append(" GROUP BY " + GROUP_BY_COLUMN_NAME);
        }

        OrderByClause orderByClause = new OrderByClause();
        orderByClause.addOrderByClause(mTimeColumnName, true);
        builder.append(orderByClause.getOrderBy());

        if (Constants.DEBUG) {
            Slog.d(TAG, "Aggregation origin query: " + builder);
        }

        return builder.toString();
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private void updateResultWithDataOriginPackageNames(Cursor metaDataCursor) {
        List<Long> packageIds = new ArrayList<>();
        while (metaDataCursor.moveToNext()) {
            packageIds.add(StorageUtils.getCursorLong(metaDataCursor, APP_INFO_ID_COLUMN_NAME));
        }
        List<String> packageNames = AppInfoHelper.getInstance().getPackageNames(packageIds);

        mAggregateResults.replaceAll(
                (n, v) -> mAggregateResults.get(n).setDataOrigins(packageNames));
    }

    public List<Pair<Long, Long>> getGroupSplitIntervals() {
        List<Pair<Long, Long>> groupIntervals = new ArrayList<>();
        long previous = mTimeSplits.get(0);
        for (int i = 1; i < mTimeSplits.size(); i++) {
            Pair<Long, Long> pair = new Pair<>(previous, mTimeSplits.get(i));
            groupIntervals.add(pair);
            previous = mTimeSplits.get(i);
        }

        return groupIntervals;
    }

    private List<Long> getGroupSplitsForPeriod(TimeRangeFilter timeFilter, Period period) {
        LocalDateTime filterStart = ((LocalTimeRangeFilter) timeFilter).getStartTime();
        LocalDateTime filterEnd = ((LocalTimeRangeFilter) timeFilter).getEndTime();

        List<Long> splits = new ArrayList<>();
        splits.add(TimeRangeFilterHelper.getMillisOfLocalTime(filterStart));

        LocalDateTime currentEnd = filterStart.plus(period);
        while (!currentEnd.isAfter(filterEnd)) {
            splits.add(TimeRangeFilterHelper.getMillisOfLocalTime(currentEnd));
            currentEnd = currentEnd.plus(period);

            if (splits.size() > MAX_NUMBER_OF_GROUPS) {
                throw new IllegalArgumentException(
                        "Number of groups must not exceed " + MAX_NUMBER_OF_GROUPS);
            }
        }

        // If the last group doesn't fit the rest of the window, we cut it up to filterEnd
        if (splits.get(splits.size() - 1) < TimeRangeFilterHelper.getMillisOfLocalTime(filterEnd)) {
            splits.add(TimeRangeFilterHelper.getMillisOfLocalTime(filterEnd));
        }
        return splits;
    }

    private List<Long> getGroupSplitsForDuration(
            TimeRangeFilter timeRangeFilter, Duration duration) {
        long groupByStart = TimeRangeFilterHelper.getFilterStartTimeMillis(timeRangeFilter);
        long groupByEnd = TimeRangeFilterHelper.getFilterEndTimeMillis(timeRangeFilter);
        long groupDurationMillis = duration.toMillis();

        if ((groupByEnd - groupByStart) / groupDurationMillis > MAX_NUMBER_OF_GROUPS) {
            throw new IllegalArgumentException(
                    "Number of buckets must not exceed " + MAX_NUMBER_OF_GROUPS);
        }

        List<Long> splits = new ArrayList<>();
        splits.add(groupByStart);
        long currentEnd = groupByStart + groupDurationMillis;
        while (currentEnd <= groupByEnd) {
            splits.add(currentEnd);
            currentEnd += groupDurationMillis;
        }

        // If the last group doesn't fit the rest of the window, we cut it up to filterEnd
        if (splits.get(splits.size() - 1) < groupByEnd) {
            splits.add(groupByEnd);
        }
        return splits;
    }

    private void deriveAggregate(Cursor cursor) {
        double[] derivedAggregateArray = mRecordHelper.deriveAggregate(cursor, this);
        int index = 0;
        cursor.moveToFirst();
        for (double aggregate : derivedAggregateArray) {
            mAggregateResults.put(
                    index, mRecordHelper.getAggregateResult(cursor, mAggregationType, aggregate));
            index++;
        }
    }
}
