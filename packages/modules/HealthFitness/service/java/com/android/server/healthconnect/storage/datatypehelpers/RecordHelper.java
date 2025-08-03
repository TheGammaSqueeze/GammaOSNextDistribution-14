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

import static android.health.connect.Constants.DEFAULT_INT;
import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.Constants.MAXIMUM_ALLOWED_CURSOR_COUNT;
import static android.health.connect.Constants.MAXIMUM_PAGE_SIZE;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;

import static com.android.server.healthconnect.storage.datatypehelpers.IntervalRecordHelper.END_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.request.ReadTransactionRequest.TYPE_NOT_PRESENT_PACKAGE_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getDedupeByteBuffer;
import static com.android.server.healthconnect.storage.utils.StorageUtils.supportsPriority;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.OR;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.AggregateResult;
import android.health.connect.PageTokenWrapper;
import android.health.connect.aidl.ReadRecordsRequestParcel;
import android.health.connect.aidl.RecordIdFiltersParcel;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.request.AggregateTableRequest;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.OrderByClause;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Parent class for all the helper classes for all the records
 *
 * @hide
 */
public abstract class RecordHelper<T extends RecordInternal<?>> {
    public static final String PRIMARY_COLUMN_NAME = "row_id";
    public static final String UUID_COLUMN_NAME = "uuid";
    public static final String CLIENT_RECORD_ID_COLUMN_NAME = "client_record_id";
    public static final String APP_INFO_ID_COLUMN_NAME = "app_info_id";
    public static final String LAST_MODIFIED_TIME_COLUMN_NAME = "last_modified_time";
    private static final String CLIENT_RECORD_VERSION_COLUMN_NAME = "client_record_version";
    private static final String DEVICE_INFO_ID_COLUMN_NAME = "device_info_id";
    private static final String RECORDING_METHOD_COLUMN_NAME = "recording_method";
    private static final String DEDUPE_HASH_COLUMN_NAME = "dedupe_hash";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMNS_INFO =
            List.of(
                    new Pair<>(DEDUPE_HASH_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB),
                    new Pair<>(UUID_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB));
    private static final String TAG_RECORD_HELPER = "HealthConnectRecordHelper";
    private static final int TRACE_TAG_RECORD_HELPER = TAG_RECORD_HELPER.hashCode();
    @RecordTypeIdentifier.RecordType private final int mRecordIdentifier;

    RecordHelper(@RecordTypeIdentifier.RecordType int recordIdentifier) {
        mRecordIdentifier = recordIdentifier;
    }

    public DeleteTableRequest getDeleteRequestForAutoDelete(int recordAutoDeletePeriodInDays) {
        return new DeleteTableRequest(getMainTableName())
                .setTimeFilter(
                        getStartTimeColumnName(),
                        Instant.EPOCH.toEpochMilli(),
                        Instant.now()
                                .minus(recordAutoDeletePeriodInDays, ChronoUnit.DAYS)
                                .toEpochMilli());
    }

    /** Database migration. Introduces automatic local time generation. */
    public abstract void applyGeneratedLocalTimeUpgrade(@NonNull SQLiteDatabase db);

    @RecordTypeIdentifier.RecordType
    public int getRecordIdentifier() {
        return mRecordIdentifier;
    }

    /**
     * @return {@link AggregateTableRequest} corresponding to {@code aggregationType}
     */
    public final AggregateTableRequest getAggregateTableRequest(
            AggregationType<?> aggregationType,
            String callingPackage,
            List<String> packageFilters,
            long startTime,
            long endTime,
            long startDateAccess,
            boolean useLocalTime) {
        AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();
        AggregateParams params = getAggregateParams(aggregationType);
        String physicalTimeColumnName = getStartTimeColumnName();
        String startTimeColumnName =
                useLocalTime ? getLocalStartTimeColumnName() : physicalTimeColumnName;
        String endTimeColumnName =
                useLocalTime ? getLocalEndTimeColumnName() : getEndTimeColumnName();
        params.setTimeColumnName(startTimeColumnName);
        params.setExtraTimeColumn(endTimeColumnName);
        params.setOffsetColumnToFetch(getZoneOffsetColumnName());

        if (supportsPriority(mRecordIdentifier, aggregationType.getAggregateOperationType())) {
            List<String> columns =
                    Arrays.asList(
                            physicalTimeColumnName,
                            END_TIME_COLUMN_NAME,
                            APP_INFO_ID_COLUMN_NAME,
                            LAST_MODIFIED_TIME_COLUMN_NAME);
            params.appendAdditionalColumns(columns);
        }
        if (StorageUtils.isDerivedType(mRecordIdentifier)) {
            params.appendAdditionalColumns(Collections.singletonList(physicalTimeColumnName));
        }

        WhereClauses whereClauses = new WhereClauses(AND);
        // filters by package names
        whereClauses.addWhereInLongsClause(
                APP_INFO_ID_COLUMN_NAME, appInfoHelper.getAppInfoIds(packageFilters));
        // filter by start date access
        whereClauses.addNestedWhereClauses(
                getFilterByStartAccessDateWhereClauses(
                        appInfoHelper.getAppInfoId(callingPackage), startDateAccess));
        // start/end time filter
        whereClauses.addWhereLessThanClause(startTimeColumnName, endTime);
        if (endTimeColumnName != null) {
            // for IntervalRecord, filters by overlapping
            whereClauses.addWhereGreaterThanOrEqualClause(endTimeColumnName, startTime);
        } else {
            // for InstantRecord, filters by whether time falls into [startTime, endTime)
            whereClauses.addWhereGreaterThanOrEqualClause(startTimeColumnName, startTime);
        }

        return new AggregateTableRequest(params, aggregationType, this, whereClauses, useLocalTime)
                .setTimeFilter(startTime, endTime);
    }

    /**
     * Used to get the Aggregate result for aggregate types
     *
     * @return {@link AggregateResult} for {@link AggregationType}
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public AggregateResult<?> getAggregateResult(
            Cursor cursor, AggregationType<?> aggregationType) {
        return null;
    }

    /**
     * Used to get the Aggregate result for aggregate types where the priority of apps is to be
     * considered for overlapping data for sleep and activity interval records
     *
     * @return {@link AggregateResult} for {@link AggregationType}
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType, double total) {
        return null;
    }

    /**
     * Used to calculate and get aggregate results for data types that support derived aggregates
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public double[] deriveAggregate(Cursor cursor, AggregateTableRequest request) {
        return null;
    }

    /**
     * Returns a requests representing the tables that should be created corresponding to this
     * helper
     */
    @NonNull
    public final CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(getMainTableName(), getColumnInfo())
                .addForeignKey(
                        DeviceInfoHelper.getInstance().getTableName(),
                        Collections.singletonList(DEVICE_INFO_ID_COLUMN_NAME),
                        Collections.singletonList(PRIMARY_COLUMN_NAME))
                .addForeignKey(
                        AppInfoHelper.TABLE_NAME,
                        Collections.singletonList(APP_INFO_ID_COLUMN_NAME),
                        Collections.singletonList(PRIMARY_COLUMN_NAME))
                .setChildTableRequests(getChildTableCreateRequests())
                .setGeneratedColumnInfo(getGeneratedColumnInfo());
    }

    /** Gets {@link UpsertTableRequest} from {@code recordInternal}. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public UpsertTableRequest getUpsertTableRequest(RecordInternal<?> recordInternal) {
        return getUpsertTableRequest(recordInternal, null);
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public UpsertTableRequest getUpsertTableRequest(
            RecordInternal<?> recordInternal,
            ArrayMap<String, Boolean> extraWritePermissionToStateMap) {
        Trace.traceBegin(
                TRACE_TAG_RECORD_HELPER, TAG_RECORD_HELPER.concat("GetUpsertTableRequest"));
        ContentValues upsertValues = getContentValues((T) recordInternal);
        updateUpsertValuesIfRequired(upsertValues, extraWritePermissionToStateMap);
        UpsertTableRequest upsertTableRequest =
                new UpsertTableRequest(getMainTableName(), upsertValues, UNIQUE_COLUMNS_INFO)
                        .setRequiresUpdateClause(
                                new UpsertTableRequest.IRequiresUpdate() {
                                    @Override
                                    public boolean requiresUpdate(
                                            Cursor cursor,
                                            ContentValues contentValues,
                                            UpsertTableRequest request) {
                                        final UUID newUUID =
                                                StorageUtils.convertBytesToUUID(
                                                        contentValues.getAsByteArray(
                                                                UUID_COLUMN_NAME));
                                        final UUID oldUUID =
                                                StorageUtils.getCursorUUID(
                                                        cursor, UUID_COLUMN_NAME);

                                        if (!Objects.equals(newUUID, oldUUID)) {
                                            // Use old UUID in case of conflicts on de-dupe.
                                            contentValues.put(
                                                    UUID_COLUMN_NAME,
                                                    StorageUtils.convertUUIDToBytes(oldUUID));
                                            request.getRecordInternal().setUuid(oldUUID);
                                            // This means there was a duplication conflict, we want
                                            // to update in this case.
                                            return true;
                                        }

                                        long clientRecordVersion =
                                                StorageUtils.getCursorLong(
                                                        cursor, CLIENT_RECORD_VERSION_COLUMN_NAME);
                                        long newClientRecordVersion =
                                                contentValues.getAsLong(
                                                        CLIENT_RECORD_VERSION_COLUMN_NAME);

                                        return newClientRecordVersion >= clientRecordVersion;
                                    }
                                })
                        .setChildTableRequests(getChildTableUpsertRequests((T) recordInternal))
                        .setHelper(this)
                        .setExtraWritePermissionsStateMapping(extraWritePermissionToStateMap);
        Trace.traceEnd(TRACE_TAG_RECORD_HELPER);
        return upsertTableRequest;
    }

    /* Updates upsert content values based on extra permissions state. */
    protected void updateUpsertValuesIfRequired(
            @NonNull ContentValues values,
            @Nullable ArrayMap<String, Boolean> extraWritePermissionToStateMap) {}

    public List<String> getChildTablesToDeleteOnRecordUpsert(
            ArrayMap<String, Boolean> extraWritePermissionToState) {
        return getAllChildTables();
    }

    @NonNull
    public List<String> getAllChildTables() {
        List<String> childTables = new ArrayList<>();
        for (CreateTableRequest childTableCreateRequest : getChildTableCreateRequests()) {
            populateWithTablesNames(childTableCreateRequest, childTables);
        }

        return childTables;
    }

    @NonNull
    protected List<CreateTableRequest.GeneratedColumnInfo> getGeneratedColumnInfo() {
        return Collections.emptyList();
    }

    private void populateWithTablesNames(
            CreateTableRequest childTableCreateRequest, List<String> childTables) {
        childTables.add(childTableCreateRequest.getTableName());
        for (CreateTableRequest childTableRequest :
                childTableCreateRequest.getChildTableRequests()) {
            populateWithTablesNames(childTableRequest, childTables);
        }
    }

    /** Returns ReadSingleTableRequest for {@code request} and package name {@code packageName} */
    public ReadTableRequest getReadTableRequest(
            ReadRecordsRequestParcel request,
            String callingPackageName,
            boolean enforceSelfRead,
            long startDateAccessMillis,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        return new ReadTableRequest(getMainTableName())
                .setJoinClause(getJoinForReadRequest())
                .setWhereClause(
                        getReadTableWhereClause(
                                request,
                                callingPackageName,
                                enforceSelfRead,
                                startDateAccessMillis))
                .setOrderBy(getOrderByClause(request))
                .setLimit(getLimitSize(request))
                .setRecordHelper(this)
                .setExtraReadRequests(
                        getExtraDataReadRequests(
                                request,
                                callingPackageName,
                                startDateAccessMillis,
                                grantedExtraReadPermissions,
                                isInForeground));
    }

    /**
     * Logs metrics specific to a record type's insertion/update.
     *
     * @param recordInternals List of records being inserted/updated
     * @param packageName Caller package name
     */
    public void logUpsertMetrics(
            @NonNull List<RecordInternal<?>> recordInternals, @NonNull String packageName) {
        // Do nothing, implement in record specific helpers
    }

    /**
     * Logs metrics specific to a record type's read.
     *
     * @param recordInternals List of records being read
     * @param packageName Caller package name
     */
    public void logReadMetrics(
            @NonNull List<RecordInternal<?>> recordInternals, @NonNull String packageName) {
        // Do nothing, implement in record specific helpers
    }

    /** Returns ReadTableRequest for {@code uuids} */
    public final ReadTableRequest getReadTableRequest(
            String packageName,
            List<UUID> uuids,
            long startDateAccess,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        return new ReadTableRequest(getMainTableName())
                .setJoinClause(getJoinForReadRequest())
                .setWhereClause(
                        new WhereClauses(AND)
                                .addWhereInClauseWithoutQuotes(
                                        UUID_COLUMN_NAME, StorageUtils.getListOfHexString(uuids))
                                .addWhereLaterThanTimeClause(
                                        getStartTimeColumnName(), startDateAccess))
                .setRecordHelper(this)
                .setExtraReadRequests(
                        getExtraDataReadRequests(
                                packageName,
                                uuids,
                                startDateAccess,
                                grantedExtraReadPermissions,
                                isInForeground));
    }

    /**
     * Returns a list of ReadSingleTableRequest for {@code request} and package name {@code
     * packageName} to populate extra data. Called in database read requests.
     */
    List<ReadTableRequest> getExtraDataReadRequests(
            ReadRecordsRequestParcel request,
            String packageName,
            long startDateAccess,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        return Collections.emptyList();
    }

    /**
     * Returns a list of ReadSingleTableRequest for {@code uuids} to populate extra data. Called in
     * change logs read requests.
     */
    List<ReadTableRequest> getExtraDataReadRequests(
            String packageName,
            List<UUID> uuids,
            long startDateAccess,
            Set<String> grantedExtraReadPermissions,
            boolean isInForeground) {
        return Collections.emptyList();
    }

    /**
     * Returns ReadTableRequest for the record corresponding to this helper with a distinct clause
     * on the input column names.
     */
    public ReadTableRequest getReadTableRequestWithDistinctAppInfoIds() {
        return new ReadTableRequest(getMainTableName())
                .setColumnNames(new ArrayList<>(List.of(APP_INFO_ID_COLUMN_NAME)))
                .setDistinctClause(true);
    }

    /**
     * Returns List of Internal records from the cursor. If the cursor contains more than {@link
     * MAXIMUM_ALLOWED_CURSOR_COUNT} records, it throws {@link IllegalArgumentException}.
     */
    public List<RecordInternal<?>> getInternalRecords(Cursor cursor) {
        if (cursor.getCount() > MAXIMUM_ALLOWED_CURSOR_COUNT) {
            throw new IllegalArgumentException(
                    "Too many records in the cursor. Max allowed: " + MAXIMUM_ALLOWED_CURSOR_COUNT);
        }
        Trace.traceBegin(TRACE_TAG_RECORD_HELPER, TAG_RECORD_HELPER.concat("GetInternalRecords"));

        List<RecordInternal<?>> recordInternalList = new ArrayList<>();
        while (cursor.moveToNext()) {
            recordInternalList.add(getRecord(cursor, /* packageNamesByAppIds= */ null));
        }

        Trace.traceEnd(TRACE_TAG_RECORD_HELPER);
        return recordInternalList;
    }

    /**
     * Returns a list of Internal records from the cursor up to the requested size, with pagination
     * handled.
     *
     * @see #getNextInternalRecordsPageAndToken(Cursor, int, PageTokenWrapper, Map)
     */
    public Pair<List<RecordInternal<?>>, PageTokenWrapper> getNextInternalRecordsPageAndToken(
            Cursor cursor, int requestSize, PageTokenWrapper pageToken) {
        return getNextInternalRecordsPageAndToken(
                cursor, requestSize, pageToken, /* packageNamesByAppIds= */ null);
    }

    /**
     * Returns List of Internal records from the cursor up to the requested size, with pagination
     * handled.
     *
     * <p>Note that the cursor limit is set to {@code requestSize + offset + 1},
     * <li>+ offset: {@code offset} records has already been returned in previous page(s). See
     *     go/hc-page-token for details.
     * <li>+ 1: if number of records queried is more than pageSize we know there are more records
     *     available to return for the next read.
     *
     *     <p>Note that the cursor may contain more records that we need to return. Cursor limit set
     *     to sum of the following:
     * <li>offset: {@code offset} records have already been returned in previous page(s), and should
     *     be skipped from this current page. In rare occasions (e.g. records deleted in between two
     *     reads), there are less than {@code offset} records, an empty list is returned, with no
     *     page token.
     * <li>requestSize: {@code requestSize} records to return in the response.
     * <li>one extra record: If there are more records than (offset+requestSize), a page token is
     *     returned for the next page. If not, then a default token is returned.
     *
     * @see #getLimitSize(ReadRecordsRequestParcel)
     */
    public Pair<List<RecordInternal<?>>, PageTokenWrapper> getNextInternalRecordsPageAndToken(
            Cursor cursor,
            int requestSize,
            PageTokenWrapper prevPageToken,
            @Nullable Map<Long, String> packageNamesByAppIds) {
        Trace.traceBegin(
                TRACE_TAG_RECORD_HELPER,
                TAG_RECORD_HELPER.concat("getNextInternalRecordsPageAndToken"));

        // Ignore <offset> records of the same start time, because it was returned in previous
        // page(s).
        // If the offset is greater than number of records in the cursor, it'll move to the last
        // index and will not enter the while loop below.
        long prevStartTime;
        long currentStartTime = DEFAULT_LONG;
        for (int i = 0; i < prevPageToken.offset(); i++) {
            if (!cursor.moveToNext()) {
                break;
            }
            prevStartTime = currentStartTime;
            currentStartTime = getCursorLong(cursor, getStartTimeColumnName());
            if (prevStartTime != DEFAULT_LONG && prevStartTime != currentStartTime) {
                // The current record should not be skipped
                cursor.moveToPrevious();
                break;
            }
        }

        currentStartTime = DEFAULT_LONG;
        int offset = 0;
        List<RecordInternal<?>> recordInternalList = new ArrayList<>();
        PageTokenWrapper nextPageToken = EMPTY_PAGE_TOKEN;
        while (cursor.moveToNext()) {
            prevStartTime = currentStartTime;
            currentStartTime = getCursorLong(cursor, getStartTimeColumnName());
            if (currentStartTime != prevStartTime) {
                offset = 0;
            }

            if (recordInternalList.size() >= requestSize) {
                nextPageToken =
                        PageTokenWrapper.of(prevPageToken.isAscending(), currentStartTime, offset);
                break;
            } else {
                T record = getRecord(cursor, packageNamesByAppIds);
                recordInternalList.add(record);
                offset++;
            }
        }

        Trace.traceEnd(TRACE_TAG_RECORD_HELPER);
        return Pair.create(recordInternalList, nextPageToken);
    }

    @SuppressWarnings("unchecked") // uncheck cast to T
    private T getRecord(Cursor cursor, @Nullable Map<Long, String> packageNamesByAppIds) {
        try {
            @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
            T record =
                    (T)
                            RecordMapper.getInstance()
                                    .getRecordIdToInternalRecordClassMap()
                                    .get(getRecordIdentifier())
                                    .getConstructor()
                                    .newInstance();
            record.setUuid(getCursorUUID(cursor, UUID_COLUMN_NAME));
            record.setLastModifiedTime(getCursorLong(cursor, LAST_MODIFIED_TIME_COLUMN_NAME));
            record.setClientRecordId(getCursorString(cursor, CLIENT_RECORD_ID_COLUMN_NAME));
            record.setClientRecordVersion(getCursorLong(cursor, CLIENT_RECORD_VERSION_COLUMN_NAME));
            record.setRecordingMethod(getCursorInt(cursor, RECORDING_METHOD_COLUMN_NAME));
            record.setRowId(getCursorInt(cursor, PRIMARY_COLUMN_NAME));
            long deviceInfoId = getCursorLong(cursor, DEVICE_INFO_ID_COLUMN_NAME);
            DeviceInfoHelper.getInstance().populateRecordWithValue(deviceInfoId, record);
            long appInfoId = getCursorLong(cursor, APP_INFO_ID_COLUMN_NAME);
            String packageName =
                    packageNamesByAppIds != null
                            ? packageNamesByAppIds.get(appInfoId)
                            : AppInfoHelper.getInstance().getPackageName(appInfoId);
            record.setPackageName(packageName);
            populateRecordValue(cursor, record);

            return record;
        } catch (InstantiationException
                | IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    /** Returns is the read of this record type is enabled */
    public boolean isRecordOperationsEnabled() {
        return true;
    }

    /** Populate internalRecords fields using extraDataCursor */
    @SuppressWarnings("unchecked")
    public void updateInternalRecordsWithExtraFields(
            List<RecordInternal<?>> internalRecords, Cursor cursorExtraData, String tableName) {
        readExtraData((List<T>) internalRecords, cursorExtraData, tableName);
    }

    public DeleteTableRequest getDeleteTableRequest(
            List<String> packageFilters,
            long startTime,
            long endTime,
            boolean usesLocalTimeFilter) {
        final String timeColumnName =
                usesLocalTimeFilter ? getLocalStartTimeColumnName() : getStartTimeColumnName();
        return new DeleteTableRequest(getMainTableName(), getRecordIdentifier())
                .setTimeFilter(timeColumnName, startTime, endTime)
                .setPackageFilter(
                        APP_INFO_ID_COLUMN_NAME,
                        AppInfoHelper.getInstance().getAppInfoIds(packageFilters))
                .setRequiresUuId(UUID_COLUMN_NAME);
    }

    public DeleteTableRequest getDeleteTableRequest(List<UUID> ids) {
        return new DeleteTableRequest(getMainTableName(), getRecordIdentifier())
                .setIds(UUID_COLUMN_NAME, StorageUtils.getListOfHexString(ids))
                .setRequiresUuId(UUID_COLUMN_NAME)
                .setEnforcePackageCheck(APP_INFO_ID_COLUMN_NAME, UUID_COLUMN_NAME);
    }

    public abstract String getDurationGroupByColumnName();

    public abstract String getPeriodGroupByColumnName();

    public abstract String getStartTimeColumnName();

    public abstract String getLocalStartTimeColumnName();

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public String getLocalEndTimeColumnName() {
        return null;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public String getEndTimeColumnName() {
        return null;
    }

    /** Populate internalRecords with extra data. */
    void readExtraData(List<T> internalRecords, Cursor cursorExtraData, String tableName) {}

    /**
     * Child classes should implement this if it wants to create additional tables, apart from the
     * main table.
     */
    @NonNull
    List<CreateTableRequest> getChildTableCreateRequests() {
        return Collections.emptyList();
    }

    /** Returns the table name to be created corresponding to this helper */
    @NonNull
    abstract String getMainTableName();

    /** Returns the information required to perform aggregate operation. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        return null;
    }

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @NonNull
    abstract List<Pair<String, String>> getSpecificColumnInfo();

    /**
     * Child classes implementation should add the values of {@code recordInternal} that needs to be
     * populated in the DB to {@code contentValues}.
     */
    abstract void populateContentValues(
            @NonNull ContentValues contentValues, @NonNull T recordInternal);

    /**
     * Child classes implementation should populate the values to the {@code record} using the
     * cursor {@code cursor} queried from the DB .
     */
    abstract void populateRecordValue(@NonNull Cursor cursor, @NonNull T recordInternal);

    List<UpsertTableRequest> getChildTableUpsertRequests(T record) {
        return Collections.emptyList();
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    SqlJoin getJoinForReadRequest() {
        return null;
    }

    private static int getLimitSize(ReadRecordsRequestParcel request) {
        // Querying extra records on top of page size
        // + pageOffset: <pageOffset> records has already been returned in previous page(s). See
        //               go/hc-page-token for details.
        // + 1: if number of records queried is more than pageSize we know there are more records
        //      available to return for the next read.
        if (request.getRecordIdFiltersParcel() == null) {
            int pageOffset =
                    PageTokenWrapper.from(request.getPageToken(), request.isAscending()).offset();
            return request.getPageSize() + pageOffset + 1;
        } else {
            return MAXIMUM_PAGE_SIZE;
        }
    }

    final WhereClauses getReadTableWhereClause(
            ReadRecordsRequestParcel request,
            String callingPackageName,
            boolean enforceSelfRead,
            long startDateAccessMillis) {
        AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();
        long callingAppInfoId = appInfoHelper.getAppInfoId(callingPackageName);
        RecordIdFiltersParcel recordIdFiltersParcel = request.getRecordIdFiltersParcel();
        if (recordIdFiltersParcel == null) {
            List<Long> appInfoIds =
                    appInfoHelper.getAppInfoIds(request.getPackageFilters()).stream()
                            .distinct()
                            .toList();
            if (enforceSelfRead) {
                appInfoIds = Collections.singletonList(callingAppInfoId);
            }
            if (appInfoIds.size() == 1 && appInfoIds.get(0) == DEFAULT_INT) {
                throw new TypeNotPresentException(TYPE_NOT_PRESENT_PACKAGE_NAME, new Throwable());
            }

            WhereClauses clauses = new WhereClauses(AND);

            // package names filter
            clauses.addWhereInLongsClause(APP_INFO_ID_COLUMN_NAME, appInfoIds);

            // page token filter
            PageTokenWrapper pageToken =
                    PageTokenWrapper.from(request.getPageToken(), request.isAscending());
            if (pageToken.isTimestampSet()) {
                long timestamp = pageToken.timeMillis();
                if (pageToken.isAscending()) {
                    clauses.addWhereGreaterThanOrEqualClause(getStartTimeColumnName(), timestamp);
                } else {
                    clauses.addWhereLessThanOrEqualClause(getStartTimeColumnName(), timestamp);
                }
            }

            // start/end time filter
            String timeColumnName =
                    request.usesLocalTimeFilter()
                            ? getLocalStartTimeColumnName()
                            : getStartTimeColumnName();
            long startTimeMillis = request.getStartTime();
            long endTimeMillis = request.getEndTime();
            if (startTimeMillis != DEFAULT_LONG) {
                clauses.addWhereGreaterThanOrEqualClause(timeColumnName, startTimeMillis);
            }
            if (endTimeMillis != DEFAULT_LONG) {
                clauses.addWhereLessThanClause(timeColumnName, endTimeMillis);
            }

            // start date access
            clauses.addNestedWhereClauses(
                    getFilterByStartAccessDateWhereClauses(
                            callingAppInfoId, startDateAccessMillis));

            return clauses;
        }

        // Since for now we don't support mixing IDs and filters, we need to look for IDs now
        List<UUID> ids =
                recordIdFiltersParcel.getRecordIdFilters().stream()
                        .map(
                                (recordIdFilter) ->
                                        StorageUtils.getUUIDFor(recordIdFilter, callingPackageName))
                        .toList();
        WhereClauses filterByIdsWhereClauses =
                new WhereClauses(AND)
                        .addWhereInClauseWithoutQuotes(
                                UUID_COLUMN_NAME, StorageUtils.getListOfHexString(ids));

        if (enforceSelfRead) {
            if (callingAppInfoId == DEFAULT_LONG) {
                throw new TypeNotPresentException(TYPE_NOT_PRESENT_PACKAGE_NAME, new Throwable());
            }
            // if self read is enforced, startDateAccess must not be applied.
            return filterByIdsWhereClauses.addWhereInLongsClause(
                    APP_INFO_ID_COLUMN_NAME, Collections.singletonList(callingAppInfoId));
        } else {
            return filterByIdsWhereClauses.addNestedWhereClauses(
                    getFilterByStartAccessDateWhereClauses(
                            callingAppInfoId, startDateAccessMillis));
        }
    }

    /**
     * Returns a {@link WhereClauses} that takes in to account start date access date & reading own
     * data.
     */
    private WhereClauses getFilterByStartAccessDateWhereClauses(
            long callingAppInfoId, long startDateAccessMillis) {
        WhereClauses resultWhereClauses = new WhereClauses(OR);

        // if the data point belongs to the calling app, then we should not enforce startDateAccess
        resultWhereClauses.addWhereEqualsClause(
                APP_INFO_ID_COLUMN_NAME, String.valueOf(callingAppInfoId));

        // Otherwise, we should enforce startDateAccess. Also we must use physical time column
        // regardless whether local time filter is used or not.
        String physicalTimeColumn = getStartTimeColumnName();
        resultWhereClauses.addWhereGreaterThanOrEqualClause(
                physicalTimeColumn, startDateAccessMillis);

        return resultWhereClauses;
    }

    abstract String getZoneOffsetColumnName();

    private OrderByClause getOrderByClause(ReadRecordsRequestParcel request) {
        if (request.getRecordIdFiltersParcel() != null) {
            return new OrderByClause();
        }
        PageTokenWrapper pageToken =
                PageTokenWrapper.from(request.getPageToken(), request.isAscending());
        return new OrderByClause()
                .addOrderByClause(getStartTimeColumnName(), pageToken.isAscending())
                .addOrderByClause(PRIMARY_COLUMN_NAME, /* isAscending= */ true);
    }

    @NonNull
    private ContentValues getContentValues(@NonNull T recordInternal) {
        ContentValues recordContentValues = new ContentValues();

        recordContentValues.put(
                UUID_COLUMN_NAME, StorageUtils.convertUUIDToBytes(recordInternal.getUuid()));
        recordContentValues.put(
                LAST_MODIFIED_TIME_COLUMN_NAME, recordInternal.getLastModifiedTime());
        recordContentValues.put(CLIENT_RECORD_ID_COLUMN_NAME, recordInternal.getClientRecordId());
        recordContentValues.put(
                CLIENT_RECORD_VERSION_COLUMN_NAME, recordInternal.getClientRecordVersion());
        recordContentValues.put(RECORDING_METHOD_COLUMN_NAME, recordInternal.getRecordingMethod());
        recordContentValues.put(DEVICE_INFO_ID_COLUMN_NAME, recordInternal.getDeviceInfoId());
        recordContentValues.put(APP_INFO_ID_COLUMN_NAME, recordInternal.getAppInfoId());
        recordContentValues.put(DEDUPE_HASH_COLUMN_NAME, getDedupeByteBuffer(recordInternal));

        populateContentValues(recordContentValues, recordInternal);

        return recordContentValues;
    }

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @NonNull
    private List<Pair<String, String>> getColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT));
        columnInfo.add(new Pair<>(UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL));
        columnInfo.add(new Pair<>(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(CLIENT_RECORD_ID_COLUMN_NAME, TEXT_NULL));
        columnInfo.add(new Pair<>(CLIENT_RECORD_VERSION_COLUMN_NAME, TEXT_NULL));
        columnInfo.add(new Pair<>(DEVICE_INFO_ID_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(APP_INFO_ID_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(RECORDING_METHOD_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(DEDUPE_HASH_COLUMN_NAME, BLOB_UNIQUE_NULL));

        columnInfo.addAll(getSpecificColumnInfo());

        return columnInfo;
    }

    /** Checks that operation with current record type are supported. */
    public void checkRecordOperationsAreEnabled(RecordInternal<?> recordInternal) {}

    /** Returns permissions required to read extra record data. */
    public List<String> getExtraReadPermissions() {
        return Collections.emptyList();
    }

    /** Returns all extra permissions associated with current record type. */
    public List<String> getExtraWritePermissions() {
        return Collections.emptyList();
    }

    /** Returns extra permissions required to write given record. */
    public List<String> getRequiredExtraWritePermissions(RecordInternal<?> recordInternal) {
        return Collections.emptyList();
    }
}
