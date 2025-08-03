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

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.TimeRangeFilter;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HeartRateRecord.HeartRateSample;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Pressure;
import android.os.OutcomeReceiver;

import androidx.test.InstrumentationRegistry;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TestHelperUtils {
    public static final String MY_PACKAGE_NAME =
            InstrumentationRegistry.getContext().getPackageName();

    public static final int TIMEOUT_SECONDS = 5;

    public static Metadata getMetadata() {
        return new Metadata.Builder().setDataOrigin(getDataOrigin()).build();
    }

    public static Metadata getMetadata(String id) {
        return new Metadata.Builder().setDataOrigin(getDataOrigin()).setId(id).build();
    }

    public static DataOrigin getDataOrigin() {
        return new DataOrigin.Builder().setPackageName(MY_PACKAGE_NAME).build();
    }

    public static BloodPressureRecord getBloodPressureRecord() {
        return new BloodPressureRecord.Builder(
                        getMetadata(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(22.0),
                        Pressure.fromMillimetersOfMercury(24.0),
                        1)
                .build();
    }

    public static StepsRecord getStepsRecord() {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(1, ChronoUnit.HOURS);
        return new StepsRecord.Builder(getMetadata(), startTime, endTime, 100).build();
    }

    public static HeartRateRecord getHeartRateRecord() {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(1, ChronoUnit.HOURS);
        return new HeartRateRecord.Builder(
                        getMetadata(),
                        startTime,
                        endTime,
                        List.of(new HeartRateSample(100, startTime)))
                .build();
    }

    /**
     * Insertion/Reading of Height Record should fail as HealthConnectTestHelper do not have
     * permissions for Height.
     */
    public static HeightRecord getHeightRecord() {
        return new HeightRecord.Builder(getMetadata(), Instant.now(), Length.fromMeters(1.9))
                .build();
    }

    public static TimeRangeFilter getDefaultTimeRangeFilter() {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofHours(24)).truncatedTo(ChronoUnit.DAYS);
        Instant end = now.plus(Duration.ofHours(24)).truncatedTo(ChronoUnit.DAYS);
        return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build();
    }

    public static List<Record> insertRecords(
            List<Record> records, HealthConnectManager healthConnectManager)
            throws InterruptedException {
        AtomicReference<List<Record>> response = new AtomicReference<>(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(healthConnectManager).isNotNull();

        healthConnectManager.insertRecords(
                records,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(InsertRecordsResponse result) {
                        response.set(result.getRecords());
                        latch.countDown();
                    }

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return response.get();
    }

    public static void deleteRecords(
            DeleteUsingFiltersRequest deleteUsingFiltersRequest,
            HealthConnectManager healthConnectManager)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(healthConnectManager).isNotNull();

        for (Class<? extends Record> recordType : deleteUsingFiltersRequest.getRecordTypes()) {
            TimeRangeFilter timeRangeFilter = deleteUsingFiltersRequest.getTimeRangeFilter();
            if (timeRangeFilter == null) {
                timeRangeFilter =
                        new TimeInstantRangeFilter.Builder()
                                .setStartTime(Instant.EPOCH)
                                .setEndTime(Instant.now().plus(1200, ChronoUnit.SECONDS))
                                .build();
            }
            healthConnectManager.deleteRecords(
                    recordType,
                    timeRangeFilter,
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

    /** Query access logs */
    public static List<AccessLog> queryAccessLogs(HealthConnectManager healthConnectManager)
            throws InterruptedException {
        AtomicReference<List<AccessLog>> response = new AtomicReference<>(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(healthConnectManager).isNotNull();

        healthConnectManager.queryAccessLogs(
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(List<AccessLog> accessLogs) {
                        response.set(accessLogs);
                        latch.countDown();
                    }

                    @Override
                    public void onError(HealthConnectException exception) {
                        latch.countDown();
                    }
                });

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return response.get();
    }

    /** Deletes the records added by the test app. */
    public static void deleteAllRecordsAddedByTestApp(HealthConnectManager healthConnectManager)
            throws InterruptedException {
        assertThat(healthConnectManager).isNotNull();

        DeleteUsingFiltersRequest deleteUsingFiltersRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodPressureRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .addRecordType(StepsRecord.class)
                        .addDataOrigin(getDataOrigin())
                        .build();
        deleteRecords(deleteUsingFiltersRequest, healthConnectManager);
    }
}
