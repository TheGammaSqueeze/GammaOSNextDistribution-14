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

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.datatypes.ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING;

import static com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper.PACKAGE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper.UNIQUE_COLUMN_INFO;

import static com.google.common.truth.Truth.assertThat;

import static java.time.Duration.ofMinutes;

import android.content.ContentValues;
import android.content.Context;
import android.health.connect.aidl.ReadRecordsRequestParcel;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.BloodPressureRecordInternal;
import android.health.connect.internal.datatypes.ExerciseRouteInternal;
import android.health.connect.internal.datatypes.ExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.StepsRecordInternal;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTransactionRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/** Util class provides shared functionality for db transaction testing. */
public final class TransactionTestUtils {
    private static final Set<String> NO_EXTRA_PERMS = Set.of();
    private static final String TEST_PACKAGE_NAME = "package.name";
    private final TransactionManager mTransactionManager;
    private final Context mContext;

    public TransactionTestUtils(Context context, TransactionManager transactionManager) {
        mContext = context;
        mTransactionManager = transactionManager;
    }

    public void insertApp(String packageName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(PACKAGE_COLUMN_NAME, packageName);
        mTransactionManager.insert(
                new UpsertTableRequest(
                        AppInfoHelper.TABLE_NAME, contentValues, UNIQUE_COLUMN_INFO));
        AppInfoHelper.getInstance().clearCache();
        assertThat(AppInfoHelper.getInstance().getAppInfoId(packageName))
                .isNotEqualTo(DEFAULT_LONG);
    }

    /** Inserts records attributed to the given package. */
    public List<String> insertRecords(String packageName, RecordInternal<?>... records) {
        return insertRecords(packageName, List.of(records));
    }

    /** Inserts records attributed to the given package. */
    public List<String> insertRecords(String packageName, List<RecordInternal<?>> records) {
        return mTransactionManager.insertAll(
                new UpsertTransactionRequest(
                        packageName,
                        records,
                        mContext,
                        /* isInsertRequest= */ true,
                        /* skipPackageNameAndLogs= */ false));
    }

    public static ReadTransactionRequest getReadTransactionRequest(
            Map<Integer, List<UUID>> recordTypeToUuids) {
        return new ReadTransactionRequest(
                TEST_PACKAGE_NAME,
                recordTypeToUuids,
                /* startDateAccessMillis= */ 0,
                NO_EXTRA_PERMS,
                /* isInForeground= */ true);
    }

    public static ReadTransactionRequest getReadTransactionRequest(
            ReadRecordsRequestParcel request) {
        return new ReadTransactionRequest(
                TEST_PACKAGE_NAME,
                request,
                /* startDateAccessMillis= */ 0,
                /* enforceSelfRead= */ false,
                NO_EXTRA_PERMS,
                /* isInForeground= */ true);
    }

    public static RecordInternal<StepsRecord> createStepsRecord(
            long startTimeMillis, long endTimeMillis, int stepsCount) {
        return createStepsRecord(/* clientId= */ null, startTimeMillis, endTimeMillis, stepsCount);
    }

    public static RecordInternal<StepsRecord> createStepsRecord(
            String clientId, long startTimeMillis, long endTimeMillis, int stepsCount) {
        return new StepsRecordInternal()
                .setCount(stepsCount)
                .setStartTime(startTimeMillis)
                .setEndTime(endTimeMillis)
                .setClientRecordId(clientId);
    }

    public static RecordInternal<BloodPressureRecord> createBloodPressureRecord(
            long timeMillis, double systolic, double diastolic) {
        return new BloodPressureRecordInternal()
                .setSystolic(systolic)
                .setDiastolic(diastolic)
                .setTime(timeMillis);
    }

    /** Creates an exercise sessions with a route. */
    public static ExerciseSessionRecordInternal createExerciseSessionRecordWithRoute(
            Instant startTime) {
        return (ExerciseSessionRecordInternal)
                new ExerciseSessionRecordInternal()
                        .setExerciseType(EXERCISE_SESSION_TYPE_RUNNING)
                        .setRoute(createExerciseRoute(startTime))
                        .setStartTime(startTime.toEpochMilli())
                        .setEndTime(startTime.plus(ofMinutes(10)).toEpochMilli());
    }

    private static ExerciseRouteInternal createExerciseRoute(Instant startTime) {
        int numberOfLocations = 3;
        double latitude = 52.13;
        double longitude = 0.14;

        return new ExerciseRouteInternal(
                IntStream.range(0, numberOfLocations)
                        .mapToObj(
                                i ->
                                        new ExerciseRouteInternal.LocationInternal()
                                                .setTime(startTime.plusSeconds(i).toEpochMilli())
                                                .setLatitude(latitude + 0.001 * i)
                                                .setLongitude(longitude + 0.001 * i))
                        .toList());
    }
}
