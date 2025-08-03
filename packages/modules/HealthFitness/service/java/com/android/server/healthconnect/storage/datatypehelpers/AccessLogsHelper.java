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

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorIntegerList;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.accesslog.AccessLog.OperationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.Pair;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.OrderByClause;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A helper class to fetch and store the access logs.
 *
 * @hide
 */
public final class AccessLogsHelper extends DatabaseHelper {
    public static final String TABLE_NAME = "access_logs_table";
    private static final String RECORD_TYPE_COLUMN_NAME = "record_type";
    private static final String APP_ID_COLUMN_NAME = "app_id";
    private static final String ACCESS_TIME_COLUMN_NAME = "access_time";
    private static final String OPERATION_TYPE_COLUMN_NAME = "operation_type";
    private static final int NUM_COLS = 5;
    private static final int DEFAULT_ACCESS_LOG_TIME_PERIOD_IN_DAYS = 7;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile AccessLogsHelper sAccessLogsHelper;

    private AccessLogsHelper() {}

    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo());
    }

    /**
     * @return AccessLog list
     */
    public List<AccessLog> queryAccessLogs() {
        final ReadTableRequest readTableRequest = new ReadTableRequest(TABLE_NAME);

        List<AccessLog> accessLogsList = new ArrayList<>();
        final AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(readTableRequest)) {
            while (cursor.moveToNext()) {
                String packageName =
                        String.valueOf(
                                appInfoHelper.getPackageName(
                                        getCursorLong(cursor, APP_ID_COLUMN_NAME)));
                @RecordTypeIdentifier.RecordType
                List<Integer> recordTypes =
                        getCursorIntegerList(cursor, RECORD_TYPE_COLUMN_NAME, DELIMITER);
                long accessTime = getCursorLong(cursor, ACCESS_TIME_COLUMN_NAME);
                @OperationType.OperationTypes
                int operationType = getCursorInt(cursor, OPERATION_TYPE_COLUMN_NAME);
                accessLogsList.add(
                        new AccessLog(packageName, recordTypes, accessTime, operationType));
            }
        }

        return accessLogsList;
    }

    /**
     * Returns the timestamp of the latest access log and {@link Long.MIN_VALUE} if there is no
     * access log.
     */
    public long getLatestAccessLogTimeStamp() {

        final ReadTableRequest readTableRequest =
                new ReadTableRequest(TABLE_NAME)
                        .setOrderBy(
                                new OrderByClause()
                                        .addOrderByClause(ACCESS_TIME_COLUMN_NAME, false))
                        .setLimit(1);

        long mostRecentAccessTime = Long.MIN_VALUE;
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(readTableRequest)) {
            while (cursor.moveToNext()) {
                long accessTime = getCursorLong(cursor, ACCESS_TIME_COLUMN_NAME);
                mostRecentAccessTime = Math.max(mostRecentAccessTime, accessTime);
            }
        }
        return mostRecentAccessTime;
    }

    /** Adds an entry in to the access logs table for every insert or read operation request */
    public void addAccessLog(
            String packageName,
            @RecordTypeIdentifier.RecordType List<Integer> recordTypeList,
            @OperationType.OperationTypes int operationType) {
        UpsertTableRequest request =
                getUpsertTableRequest(packageName, recordTypeList, operationType);
        TransactionManager.getInitialisedInstance().insert(request);
    }

    @NonNull
    public UpsertTableRequest getUpsertTableRequest(
            String packageName, List<Integer> recordTypeList, int operationType) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(
                RECORD_TYPE_COLUMN_NAME,
                recordTypeList.stream().map(String::valueOf).collect(Collectors.joining(",")));
        contentValues.put(
                APP_ID_COLUMN_NAME, AppInfoHelper.getInstance().getAppInfoId(packageName));
        contentValues.put(ACCESS_TIME_COLUMN_NAME, Instant.now().toEpochMilli());
        contentValues.put(OPERATION_TYPE_COLUMN_NAME, operationType);

        return new UpsertTableRequest(TABLE_NAME, contentValues);
    }

    /**
     * Returns an instance of {@link DeleteTableRequest} to delete entries in access logs table
     * older than a week.
     */
    public DeleteTableRequest getDeleteRequestForAutoDelete() {
        return new DeleteTableRequest(TABLE_NAME)
                .setTimeFilter(
                        ACCESS_TIME_COLUMN_NAME,
                        Instant.EPOCH.toEpochMilli(),
                        Instant.now()
                                .minus(DEFAULT_ACCESS_LOG_TIME_PERIOD_IN_DAYS, ChronoUnit.DAYS)
                                .toEpochMilli());
    }

    @Override
    @NonNull
    public List<Pair<String, String>> getColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>(NUM_COLS);
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT));
        columnInfo.add(new Pair<>(APP_ID_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(RECORD_TYPE_COLUMN_NAME, TEXT_NOT_NULL));
        columnInfo.add(new Pair<>(ACCESS_TIME_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(OPERATION_TYPE_COLUMN_NAME, INTEGER_NOT_NULL));

        return columnInfo;
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }

    public static synchronized AccessLogsHelper getInstance() {
        if (sAccessLogsHelper == null) {
            sAccessLogsHelper = new AccessLogsHelper();
        }

        return sAccessLogsHelper;
    }
}
