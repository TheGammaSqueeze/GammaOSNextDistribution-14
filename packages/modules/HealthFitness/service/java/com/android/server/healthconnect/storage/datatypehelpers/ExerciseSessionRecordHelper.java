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

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.HealthConnectException.ERROR_UNSUPPORTED_OPERATION;
import static android.health.connect.HealthPermissions.READ_EXERCISE_ROUTE;
import static android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES;
import static android.health.connect.HealthPermissions.WRITE_EXERCISE_ROUTE;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.EXERCISE_SESSION_DURATION_TOTAL;

import static com.android.server.healthconnect.storage.datatypehelpers.ExerciseLapRecordHelper.EXERCISE_LAPS_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.ExerciseRouteRecordHelper.EXERCISE_ROUTE_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.ExerciseSegmentRecordHelper.EXERCISE_SEGMENT_RECORD_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.SeriesRecordHelper.PARENT_KEY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BOOLEAN_FALSE_VALUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BOOLEAN_TRUE_VALUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getIntegerAndConvertToBoolean;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.HealthConnectException;
import android.health.connect.aidl.ReadRecordsRequestParcel;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.ExerciseLapInternal;
import android.health.connect.internal.datatypes.ExerciseSegmentInternal;
import android.health.connect.internal.datatypes.ExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.logging.ExerciseRoutesLogger;
import com.android.server.healthconnect.logging.ExerciseRoutesLogger.Operations;
import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class for ExerciseSessionRecord.
 *
 * @hide
 */
public final class ExerciseSessionRecordHelper
        extends IntervalRecordHelper<ExerciseSessionRecordInternal> {
    private static final String TAG = "ExerciseSessionRecordHelper";

    private static final String EXERCISE_SESSION_RECORD_TABLE_NAME =
            "exercise_session_record_table";

    // Exercise Session columns names
    private static final String NOTES_COLUMN_NAME = "notes";
    private static final String EXERCISE_TYPE_COLUMN_NAME = "exercise_type";
    private static final String TITLE_COLUMN_NAME = "title";
    private static final String HAS_ROUTE_COLUMN_NAME = "has_route";

    private static final int ROUTE_READ_ACCESS_TYPE_NONE = 0;
    private static final int ROUTE_READ_ACCESS_TYPE_OWN = 1;
    private static final int ROUTE_READ_ACCESS_TYPE_ALL = 2;

    public ExerciseSessionRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION);
    }

    /** Returns the table name to be created corresponding to this helper */
    @Override
    String getMainTableName() {
        return EXERCISE_SESSION_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull ExerciseSessionRecordInternal exerciseSessionRecord) {
        UUID uuid = getCursorUUID(cursor, UUID_COLUMN_NAME);
        exerciseSessionRecord.setNotes(getCursorString(cursor, NOTES_COLUMN_NAME));
        exerciseSessionRecord.setExerciseType(getCursorInt(cursor, EXERCISE_TYPE_COLUMN_NAME));
        exerciseSessionRecord.setTitle(getCursorString(cursor, TITLE_COLUMN_NAME));
        exerciseSessionRecord.setHasRoute(
                isExerciseRouteFeatureEnabled()
                        && getIntegerAndConvertToBoolean(cursor, HAS_ROUTE_COLUMN_NAME));

        // The table might contain duplicates because of 2 left joins, use sets to remove them.
        ArraySet<ExerciseLapInternal> lapsSet = new ArraySet<>();
        ArraySet<ExerciseSegmentInternal> segmentsSet = new ArraySet<>();
        do {
            // Populate lap and segments from each row.
            ExerciseLapRecordHelper.populateLapIfRecorded(cursor, lapsSet);
            ExerciseSegmentRecordHelper.updateSetWithRecordedSegment(cursor, segmentsSet);
        } while (cursor.moveToNext() && uuid.equals(getCursorUUID(cursor, UUID_COLUMN_NAME)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        cursor.moveToPrevious();

        if (!lapsSet.isEmpty()) {
            exerciseSessionRecord.setExerciseLaps(lapsSet.stream().toList());
        }

        if (!segmentsSet.isEmpty()) {
            exerciseSessionRecord.setExerciseSegments(segmentsSet.stream().toList());
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        List<String> sessionColumns = new ArrayList<>(super.getPriorityAggregationColumnNames());
        sessionColumns.add(ExerciseSegmentRecordHelper.getStartTimeColumnName());
        sessionColumns.add(ExerciseSegmentRecordHelper.getEndTimeColumnName());
        if (aggregateRequest.getAggregationTypeIdentifier() == EXERCISE_SESSION_DURATION_TOTAL) {
            return new AggregateParams(EXERCISE_SESSION_RECORD_TABLE_NAME, sessionColumns)
                    .setJoin(
                            ExerciseSegmentRecordHelper.getJoinForDurationAggregation(
                                    getMainTableName()))
                    .setPriorityAggregationExtraParams(
                            new AggregateParams.PriorityAggregationExtraParams(
                                    ExerciseSegmentRecordHelper.getStartTimeColumnName(),
                                    ExerciseSegmentRecordHelper.getEndTimeColumnName()));
        }
        return null;
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull ExerciseSessionRecordInternal exerciseSessionRecord) {
        contentValues.put(NOTES_COLUMN_NAME, exerciseSessionRecord.getNotes());
        contentValues.put(EXERCISE_TYPE_COLUMN_NAME, exerciseSessionRecord.getExerciseType());
        contentValues.put(TITLE_COLUMN_NAME, exerciseSessionRecord.getTitle());
        contentValues.put(
                HAS_ROUTE_COLUMN_NAME,
                exerciseSessionRecord.hasRoute() ? BOOLEAN_TRUE_VALUE : BOOLEAN_FALSE_VALUE);
    }

    @Override
    List<CreateTableRequest> getChildTableCreateRequests() {
        return List.of(
                ExerciseRouteRecordHelper.getCreateRouteTableRequest(getMainTableName()),
                ExerciseLapRecordHelper.getCreateLapsTableRequest(getMainTableName()),
                ExerciseSegmentRecordHelper.getCreateSegmentsTableRequest(getMainTableName()));
    }

    @Override
    List<UpsertTableRequest> getChildTableUpsertRequests(
            @NonNull ExerciseSessionRecordInternal record) {
        List<UpsertTableRequest> childUpsertRequests = new ArrayList<>();

        if (record.getRoute() != null) {
            childUpsertRequests.addAll(
                    ExerciseRouteRecordHelper.getRouteUpsertRequests(record.getRoute()));
        }

        if (record.getLaps() != null) {
            childUpsertRequests.addAll(
                    ExerciseLapRecordHelper.getLapsUpsertRequests(record.getLaps()));
        }

        if (record.getSegments() != null) {
            childUpsertRequests.addAll(
                    ExerciseSegmentRecordHelper.getSegmentsUpsertRequests(record.getSegments()));
        }

        return childUpsertRequests;
    }

    @Override
    public List<String> getChildTablesToDeleteOnRecordUpsert(
            ArrayMap<String, Boolean> extraWritePermissionToState) {
        ArrayList<String> childTablesToDelete = new ArrayList<>();
        childTablesToDelete.add(EXERCISE_LAPS_RECORD_TABLE_NAME);
        childTablesToDelete.add(EXERCISE_SEGMENT_RECORD_TABLE_NAME);

        // If on session update app doesn't have granted write_route, then we leave the route as is.
        if (canWriteExerciseRoute(extraWritePermissionToState)) {
            childTablesToDelete.add(EXERCISE_ROUTE_RECORD_TABLE_NAME);
        }
        return childTablesToDelete;
    }

    @Override
    protected void updateUpsertValuesIfRequired(
            @NonNull ContentValues values,
            @Nullable ArrayMap<String, Boolean> extraWritePermissionToStateMap) {
        if (extraWritePermissionToStateMap == null || extraWritePermissionToStateMap.isEmpty()) {
            // Use default logic for internal apis flows (apk migration and b&r)
            return;
        }

        // If app doesn't have granted write_route, then we ignore input hasRoute
        // value and use current value if recorded.
        if (!canWriteExerciseRoute(extraWritePermissionToStateMap)) {
            values.remove(HAS_ROUTE_COLUMN_NAME);
        }
    }

    @Override
    @NonNull
    protected List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return Arrays.asList(
                new Pair<>(NOTES_COLUMN_NAME, TEXT_NULL),
                new Pair<>(EXERCISE_TYPE_COLUMN_NAME, INTEGER),
                new Pair<>(TITLE_COLUMN_NAME, TEXT_NULL),
                new Pair<>(HAS_ROUTE_COLUMN_NAME, INTEGER));
    }

    @Override
    SqlJoin getJoinForReadRequest() {
        return ExerciseLapRecordHelper.getJoinReadRequest(getMainTableName())
                .attachJoin(ExerciseSegmentRecordHelper.getJoinReadRequest(getMainTableName()));
    }

    @Override
    List<ReadTableRequest> getExtraDataReadRequests(
            ReadRecordsRequestParcel request,
            String packageName,
            long startDateAccessMillis,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        int routeAccessType =
                getExerciseRouteReadAccessType(
                        packageName, grantedExtraReadPermissions, isInForeground);

        if (routeAccessType == ROUTE_READ_ACCESS_TYPE_NONE) {
            return Collections.emptyList();
        }

        boolean enforceSelfRead = routeAccessType == ROUTE_READ_ACCESS_TYPE_OWN;

        WhereClauses sessionsWithAccessibleRouteClause =
                getReadTableWhereClause(
                        request, packageName, enforceSelfRead, startDateAccessMillis);
        return List.of(getRouteReadRequest(sessionsWithAccessibleRouteClause));
    }

    @Override
    public boolean isRecordOperationsEnabled() {
        return HealthConnectDeviceConfigManager.getInitialisedInstance()
                .isSessionDatatypeFeatureEnabled();
    }

    @Override
    public void checkRecordOperationsAreEnabled(RecordInternal<?> recordInternal) {
        super.checkRecordOperationsAreEnabled(recordInternal);
        if (!isRecordOperationsEnabled()) {
            throw new HealthConnectException(
                    ERROR_UNSUPPORTED_OPERATION, "Writing exercise sessions is not supported.");
        }

        ExerciseSessionRecordInternal session = (ExerciseSessionRecordInternal) recordInternal;
        if (session.getRoute() != null && !isExerciseRouteFeatureEnabled()) {
            throw new HealthConnectException(
                    ERROR_UNSUPPORTED_OPERATION, "Writing exercise route is not supported.");
        }
    }

    /** Returns extra permissions required to write given record. */
    @Override
    public List<String> getRequiredExtraWritePermissions(RecordInternal<?> recordInternal) {
        ExerciseSessionRecordInternal session = (ExerciseSessionRecordInternal) recordInternal;
        if (session.getRoute() != null) {
            if (!isExerciseRouteFeatureEnabled()) {
                throw new HealthConnectException(
                        ERROR_UNSUPPORTED_OPERATION, "Writing exercise route is not supported.");
            }
            return Collections.singletonList(WRITE_EXERCISE_ROUTE);
        }
        return Collections.emptyList();
    }

    /** Returns permissions required to read extra record data. */
    @Override
    public List<String> getExtraReadPermissions() {
        // WRITE_EXERCISE_ROUTE is in fact a read permission as it allows reading own routes.
        return List.of(READ_EXERCISE_ROUTE, READ_EXERCISE_ROUTES, WRITE_EXERCISE_ROUTE);
    }

    public List<String> getExtraWritePermissions() {
        // If an app has write_route permission, we update existing route.
        // If app doesn't have this permission and wants to update non-route session data,
        // we don't change recorded route.
        return List.of(WRITE_EXERCISE_ROUTE);
    }

    @Override
    List<ReadTableRequest> getExtraDataReadRequests(
            String packageName,
            List<UUID> uuids,
            long startDateAccess,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        int routeAccessType =
                getExerciseRouteReadAccessType(
                        packageName, grantedExtraReadPermissions, isInForeground);

        if (routeAccessType == ROUTE_READ_ACCESS_TYPE_NONE) {
            return Collections.emptyList();
        }

        WhereClauses sessionsWithAccessibleRouteClause =
                new WhereClauses(AND)
                        .addWhereInClauseWithoutQuotes(
                                UUID_COLUMN_NAME, StorageUtils.getListOfHexString(uuids))
                        .addWhereLaterThanTimeClause(getStartTimeColumnName(), startDateAccess);

        if (routeAccessType == ROUTE_READ_ACCESS_TYPE_OWN) {
            long appId = AppInfoHelper.getInstance().getAppInfoId(packageName);
            sessionsWithAccessibleRouteClause.addWhereInLongsClause(
                    APP_INFO_ID_COLUMN_NAME, List.of(appId));
        }

        return List.of(getRouteReadRequest(sessionsWithAccessibleRouteClause));
    }

    @Override
    public void readExtraData(
            List<ExerciseSessionRecordInternal> internalRecords,
            Cursor cursorExtraData,
            String tableName) {
        // Collect rowId to Record mapping to understand which record update with route location.
        Map<Integer, Integer> mapping = new HashMap<>(internalRecords.size());
        for (int i = 0; i < internalRecords.size(); i++) {
            mapping.put(internalRecords.get(i).getRowId(), i);
        }

        while (cursorExtraData.moveToNext()) {
            ExerciseSessionRecordInternal record =
                    internalRecords.get(
                            mapping.get(getCursorInt(cursorExtraData, PARENT_KEY_COLUMN_NAME)));
            record.addRouteLocation(ExerciseRouteRecordHelper.populateLocation(cursorExtraData));
        }
    }

    private boolean isExerciseRouteFeatureEnabled() {
        return isRecordOperationsEnabled()
                && HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isExerciseRouteFeatureEnabled();
    }

    private boolean isReadExerciseRouteAllFeatureEnabled() {
        return isExerciseRouteFeatureEnabled()
                && HealthConnectDeviceConfigManager.getInitialisedInstance()
                        .isExerciseRoutesReadAllFeatureEnabled();
    }

    @Override
    public void logUpsertMetrics(
            @NonNull List<RecordInternal<?>> recordInternals, @NonNull String packageName) {
        Objects.requireNonNull(recordInternals);

        ExerciseRoutesLogger.log(
                Operations.UPSERT,
                packageName,
                getNumberOfRecordsWithExerciseRoutes(recordInternals));
    }

    @Override
    public void logReadMetrics(
            @NonNull List<RecordInternal<?>> recordInternals, @NonNull String packageName) {
        Objects.requireNonNull(recordInternals);

        ExerciseRoutesLogger.log(
                Operations.READ,
                packageName,
                getNumberOfRecordsWithExerciseRoutes(recordInternals));
    }

    private boolean canWriteExerciseRoute(ArrayMap<String, Boolean> extraWritePermissionToState) {
        return extraWritePermissionToState != null
                && Boolean.TRUE.equals(extraWritePermissionToState.get(WRITE_EXERCISE_ROUTE));
    }

    private int getNumberOfRecordsWithExerciseRoutes(
            @NonNull List<RecordInternal<?>> recordInternals) {

        int numberOfRecordsWithExerciseRoutes = 0;
        for (RecordInternal<?> recordInternal : recordInternals) {
            try {
                if (((ExerciseSessionRecordInternal) recordInternal).hasRoute()) {
                    numberOfRecordsWithExerciseRoutes++;
                }
            } catch (ClassCastException ignored) {
                // List might contain record types other than ExerciseSession which can be ignored.
            }
        }
        return numberOfRecordsWithExerciseRoutes;
    }

    private ReadTableRequest getRouteReadRequest(WhereClauses clauseToFilterSessionIds) {
        ReadTableRequest routeReadRequest = new ReadTableRequest(EXERCISE_ROUTE_RECORD_TABLE_NAME);

        ReadTableRequest sessionsIdsRequest = new ReadTableRequest(getMainTableName());
        sessionsIdsRequest.setColumnNames(List.of(PRIMARY_COLUMN_NAME));
        sessionsIdsRequest.setWhereClause(clauseToFilterSessionIds);

        WhereClauses inClause = new WhereClauses(AND);
        inClause.addWhereInSQLRequestClause(PARENT_KEY_COLUMN_NAME, sessionsIdsRequest);
        routeReadRequest.setWhereClause(inClause);
        return routeReadRequest;
    }

    private int getExerciseRouteReadAccessType(
            String packageName, Set<String> grantedExtraReadPermissions, boolean isInForeground) {
        if (!isExerciseRouteFeatureEnabled() || grantedExtraReadPermissions.isEmpty()) {
            return ROUTE_READ_ACCESS_TYPE_NONE;
        }

        boolean isController = grantedExtraReadPermissions.contains(READ_EXERCISE_ROUTE);

        if (isController) {
            // HC UI Controller has access to all routes.
            return ROUTE_READ_ACCESS_TYPE_ALL;
        }

        long appId = AppInfoHelper.getInstance().getAppInfoId(packageName);

        if (appId == DEFAULT_LONG) {
            return ROUTE_READ_ACCESS_TYPE_NONE;
        }

        boolean canReadAllRoutes =
                isInForeground
                        && isReadExerciseRouteAllFeatureEnabled()
                        && grantedExtraReadPermissions.contains(READ_EXERCISE_ROUTES);

        return canReadAllRoutes ? ROUTE_READ_ACCESS_TYPE_ALL : ROUTE_READ_ACCESS_TYPE_OWN;
    }
}
