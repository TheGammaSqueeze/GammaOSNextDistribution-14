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

package android.healthconnect.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.InstantRecord;
import android.health.connect.datatypes.IntervalRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class GetActivityDatesTest {
    private static final String TAG = "GetActivityDatesTest";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testEmptyActivityDates() throws InterruptedException {
        List<Record> records = getTestRecords();

        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));

        assertThat(activityDates).hasSize(0);
    }

    @Test
    public void testActivityDates() throws InterruptedException {
        List<Record> records = getTestRecords();
        TestUtils.insertRecords(records);
        // Wait for some time, as activity dates are updated in the background so might take some
        // additional time.
        Thread.sleep(500);
        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isGreaterThan(1);
        assertThat(activityDates)
                .containsAtLeastElementsIn(
                        records.stream().map(this::getRecordDate).collect(Collectors.toSet()));
    }

    @Test
    public void testGetActivityDates_onUpdate() throws InterruptedException {
        List<Record> records = getTestRecords();
        TestUtils.insertRecords(records);
        // Wait for some time, as activity dates are updated in the background so might take some
        // additional time.
        Thread.sleep(500);
        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isGreaterThan(1);
        assertThat(activityDates)
                .containsExactlyElementsIn(
                        records.stream().map(this::getRecordDate).collect(Collectors.toSet()));
        List<Record> updatedRecords = getTestRecords();

        for (int itr = 0; itr < updatedRecords.size(); itr++) {
            updatedRecords.set(
                    itr,
                    new StepsRecord.Builder(
                                    new Metadata.Builder()
                                            .setId(records.get(itr).getMetadata().getId())
                                            .setDataOrigin(
                                                    records.get(itr).getMetadata().getDataOrigin())
                                            .build(),
                                    Instant.now().minusSeconds(5000 + itr * 2L),
                                    Instant.now().minusSeconds(itr * 2L),
                                    20)
                            .build());
        }

        TestUtils.updateRecords(updatedRecords);
        Thread.sleep(500);

        List<LocalDate> updatedActivityDates =
                TestUtils.getActivityDates(
                        updatedRecords.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(updatedActivityDates)
                .containsExactlyElementsIn(
                        updatedRecords.stream()
                                .map(this::getRecordDate)
                                .collect(Collectors.toSet()));
        assertThat(updatedActivityDates).containsNoneIn(activityDates);
    }

    @Test
    public void testGetActivityDates_onDelete() throws InterruptedException {
        List<Record> records = getTestRecords();
        TestUtils.insertRecords(records);
        // Wait for some time, as activity dates are updated in the background so might take some
        // additional time.
        Thread.sleep(500);
        List<LocalDate> activityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(activityDates.size()).isGreaterThan(1);
        assertThat(activityDates)
                .containsExactlyElementsIn(
                        records.stream().map(this::getRecordDate).collect(Collectors.toSet()));

        TimeInstantRangeFilter timeRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now().minusSeconds(1200000))
                        .setEndTime(Instant.now().minusSeconds(700000))
                        .build();
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(StepsRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());

        Thread.sleep(500);

        List<LocalDate> updatedActivityDates =
                TestUtils.getActivityDates(
                        records.stream().map(Record::getClass).collect(Collectors.toList()));
        assertThat(updatedActivityDates.size()).isLessThan(activityDates.size());
    }

    /** Returns test records with different start times */
    private List<Record> getTestRecords() {
        Context context = ApplicationProvider.getApplicationContext();
        Metadata.Builder metadata =
                new Metadata.Builder()
                        .setDevice(
                                new Device.Builder()
                                        .setManufacturer("google")
                                        .setModel("Pixel")
                                        .setType(1)
                                        .build())
                        .setDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build());

        return new ArrayList<>(
                Arrays.asList(
                        new StepsRecord.Builder(
                                        metadata.setId(String.valueOf(Math.random())).build(),
                                        Instant.now().minusSeconds(2000000),
                                        Instant.now().minusSeconds(1900000),
                                        10)
                                .build(),
                        new StepsRecord.Builder(
                                        metadata.setId(String.valueOf(Math.random())).build(),
                                        Instant.now().minusSeconds(1000000),
                                        Instant.now().minusSeconds(900000),
                                        10)
                                .build()));
    }

    private LocalDate getRecordDate(Record record) {
        LocalDate activityDate;
        if (record instanceof IntervalRecord) {
            activityDate =
                    LocalDate.ofInstant(
                            ((IntervalRecord) record).getStartTime(),
                            ((IntervalRecord) record).getStartZoneOffset());
        } else if (record instanceof InstantRecord) {
            activityDate =
                    LocalDate.ofInstant(
                            ((InstantRecord) record).getTime(),
                            ((InstantRecord) record).getZoneOffset());
        } else {
            activityDate =
                    LocalDate.ofInstant(
                            Instant.now(),
                            ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        }
        return activityDate;
    }
}
