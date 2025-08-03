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

package android.healthconnect.cts.testhelper;

import static android.health.connect.datatypes.HeartRateRecord.BPM_MAX;
import static android.health.connect.datatypes.NutritionRecord.BIOTIN_TOTAL;
import static android.healthconnect.cts.testhelper.TestHelperUtils.TIMEOUT_SECONDS;
import static android.healthconnect.cts.testhelper.TestHelperUtils.deleteAllRecordsAddedByTestApp;
import static android.healthconnect.cts.testhelper.TestHelperUtils.deleteRecords;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getBloodPressureRecord;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getDataOrigin;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getDefaultTimeRangeFilter;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getHeartRateRecord;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getHeightRecord;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getMetadata;
import static android.healthconnect.cts.testhelper.TestHelperUtils.getStepsRecord;
import static android.healthconnect.cts.testhelper.TestHelperUtils.insertRecords;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;
import android.os.OutcomeReceiver;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.NonApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * These tests are run by statsdatom/healthconnect to log atoms by triggering Health Connect APIs.
 *
 * <p>They only trigger the APIs, but don't test anything themselves.
 */
@NonApiTest(
        exemptionReasons = {},
        justification = "METRIC")
public class HealthConnectServiceLogsTests {

    private final HealthConnectManager mHealthConnectManager =
            InstrumentationRegistry.getContext().getSystemService(HealthConnectManager.class);
    private static final String MY_PACKAGE_NAME =
            InstrumentationRegistry.getContext().getPackageName();

    @Before
    public void before() throws InterruptedException {
        deleteAllRecordsAddedByTestApp(mHealthConnectManager);
    }

    @After
    public void after() throws InterruptedException {
        deleteAllRecordsAddedByTestApp(mHealthConnectManager);
    }

    @Test
    public void testHealthConnectInsertRecords() throws Exception {
        insertRecords(
                List.of(getBloodPressureRecord(), getHeartRateRecord()), mHealthConnectManager);
    }

    @Test
    public void testHealthConnectInsertRecordsError() throws Exception {
        // No permission for Height so it should throw Security Exception
        insertRecords(List.of(getBloodPressureRecord(), getHeightRecord()), mHealthConnectManager);
    }

    @Test
    public void testHealthConnectUpdateRecords() throws Exception {
        List<Record> records =
                insertRecords(
                        List.of(getBloodPressureRecord(), getHeartRateRecord(), getStepsRecord()),
                        mHealthConnectManager);
        updateRecords(records);
    }

    @Test
    public void testHealthConnectUpdateRecordsError() throws Exception {
        List<Record> insertRecords =
                insertRecords(List.of(getBloodPressureRecord()), mHealthConnectManager);

        updateRecords(
                List.of(
                        new HeightRecord.Builder(
                                        getMetadata(insertRecords.get(0).getMetadata().getId()),
                                        Instant.now(),
                                        Length.fromMeters(1.5))
                                .build()));
    }

    @Test
    public void testHealthConnectDeleteRecords() throws Exception {
        insertRecords(List.of(getBloodPressureRecord(), getStepsRecord()), mHealthConnectManager);
        DeleteUsingFiltersRequest deleteUsingFiltersRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodPressureRecord.class)
                        .addRecordType(StepsRecord.class)
                        .addDataOrigin(getDataOrigin())
                        .build();

        assertThat(mHealthConnectManager).isNotNull();
        deleteRecords(deleteUsingFiltersRequest, mHealthConnectManager);
    }

    @Test
    public void testHealthConnectDeleteRecordsError() throws Exception {
        insertRecords(List.of(getBloodPressureRecord(), getStepsRecord()), mHealthConnectManager);
        DeleteUsingFiltersRequest deleteUsingFiltersRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeightRecord.class)
                        .addDataOrigin(getDataOrigin())
                        .build();

        assertThat(mHealthConnectManager).isNotNull();
        deleteRecords(deleteUsingFiltersRequest, mHealthConnectManager);
    }

    @Test
    public void testHealthConnectReadRecords() throws Exception {

        insertRecords(List.of(getStepsRecord()), mHealthConnectManager);
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(getDefaultTimeRangeFilter())
                        .addDataOrigins(
                                new DataOrigin.Builder().setPackageName(MY_PACKAGE_NAME).build())
                        .setPageSize(1)
                        .build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(ReadRecordsResponse<StepsRecord> result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectReadRecordsError() throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class)
                        .setTimeRangeFilter(getDefaultTimeRangeFilter())
                        .setPageSize(1)
                        .build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(ReadRecordsResponse<HeightRecord> result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectGetChangeLogToken() throws Exception {
        getChangeLogToken();
    }

    @Test
    public void testHealthConnectGetChangeLogTokenError() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.getChangeLogToken(
                new ChangeLogTokenRequest.Builder().addRecordType(HeightRecord.class).build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(ChangeLogTokenResponse result) {
                        token.set(result.getToken());
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectGetChangeLogs() throws Exception {
        String token = getChangeLogToken();

        insertRecords(
                List.of(getBloodPressureRecord(), getHeartRateRecord()), mHealthConnectManager);

        deleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodPressureRecord.class)
                        .addDataOrigin(getDataOrigin())
                        .build(),
                mHealthConnectManager);

        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.getChangeLogs(
                new ChangeLogsRequest.Builder(token).build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(ChangeLogsResponse result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectGetChangeLogsError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.getChangeLogs(
                new ChangeLogsRequest.Builder("FAIL").build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(ChangeLogsResponse result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectAggregatedData() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.aggregate(
                new AggregateRecordsRequest.Builder<Long>(getDefaultTimeRangeFilter())
                        .addAggregationType(BPM_MAX)
                        .build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(AggregateRecordsResponse<Long> result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectAggregatedDataError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.aggregate(
                new AggregateRecordsRequest.Builder<Mass>(getDefaultTimeRangeFilter())
                        .addAggregationType(BIOTIN_TOTAL)
                        .build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(AggregateRecordsResponse<Mass> result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testHealthConnectDatabaseStats() throws Exception {
        insertRecords(
                List.of(getStepsRecord(), getBloodPressureRecord(), getHeartRateRecord()),
                mHealthConnectManager);
    }

    private String getChangeLogToken() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mHealthConnectManager).isNotNull();
        mHealthConnectManager.getChangeLogToken(
                new ChangeLogTokenRequest.Builder()
                        .addRecordType(BloodPressureRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .build(),
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(ChangeLogTokenResponse result) {
                        token.set(result.getToken());
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return token.get();
    }

    private void updateRecords(List<Record> records) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        mHealthConnectManager.updateRecords(
                records,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }

                    @Override
                    public void onResult(Void result) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
}
