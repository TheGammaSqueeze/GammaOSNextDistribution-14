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

package com.android.cobalt.data;

import static com.google.common.truth.Truth.assertThat;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Instant;
import java.util.Optional;

@RunWith(JUnit4.class)
public final class CobaltDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper mHelper =
            new MigrationTestHelper(
                    InstrumentationRegistry.getInstrumentation(), CobaltDatabase.class);

    @Test
    public void migrate1To2() throws Exception {
        SupportSQLiteDatabase db = mHelper.createDatabase(TEST_DB, /* version= */ 1);
        ReportKey reportKey = ReportKey.create(1, 2, 3, 4);
        int dayIndex = 5;
        long systemProfileHash = 6;
        EventVector eventVector = EventVector.create(7);
        int observationId = 8;
        Instant initialEnabledTime = Instant.parse("2023-06-13T16:09:30.00Z");
        AggregateValue aggregateValue = AggregateValue.getDefaultInstance();
        SystemProfile systemProfile = SystemProfile.getDefaultInstance();
        UnencryptedObservationBatch unencryptedObservationBatch =
                UnencryptedObservationBatch.getDefaultInstance();

        db.execSQL(
                "INSERT INTO AggregateStore (customer_id, project_id, metric_id, report_id,"
                    + " day_index, system_profile_hash, event_vector, aggregate_value) VALUES (?,"
                    + " ?, ?, ?, ?, ?, ?, ?)",
                new Object[] {
                    reportKey.customerId(),
                    reportKey.projectId(),
                    reportKey.metricId(),
                    reportKey.reportId(),
                    dayIndex,
                    systemProfileHash,
                    Converters.fromEventVector(eventVector),
                    aggregateValue.toByteArray()
                });
        db.execSQL(
                "INSERT INTO GlobalValues (key, value) VALUES (?,?)",
                new Object[] {
                    /* key= */ "INITIAL_ENABLED_TIME",
                    /* value= */ GlobalValueEntity.timeToDbString(initialEnabledTime)
                });
        db.execSQL(
                "INSERT INTO ObservationStore (observation_store_id, unencrypted_observation_batch)"
                        + " VALUES (?,?)",
                new Object[] {observationId, unencryptedObservationBatch.toByteArray()});
        db.execSQL(
                "INSERT INTO Reports (customer_id, project_id, metric_id, report_id,"
                        + " last_sent_day_index) VALUES (?,?,?,?,?)",
                new Object[] {
                    reportKey.customerId(),
                    reportKey.projectId(),
                    reportKey.metricId(),
                    reportKey.reportId(),
                    dayIndex,
                });
        db.execSQL(
                "INSERT INTO SystemProfiles (system_profile_hash, system_profile) "
                        + " VALUES (?,?)",
                new Object[] {systemProfileHash, systemProfile.toByteArray()});
        db.close();

        CobaltDatabase cobaltDatabase =
                Room.databaseBuilder(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                CobaltDatabase.class,
                                TEST_DB)
                        .build();
        DaoBuildingBlocks daoBuildingBlocks = cobaltDatabase.daoBuildingBlocks();
        TestOnlyDao testOnlyDao = cobaltDatabase.testOnlyDao();

        assertThat(
                        daoBuildingBlocks.queryOneSystemProfileAndAggregateValue(
                                reportKey, dayIndex, eventVector, systemProfileHash))
                .isEqualTo(
                        Optional.of(
                                SystemProfileAndAggregateValue.create(
                                        systemProfileHash, aggregateValue)));
        assertThat(daoBuildingBlocks.queryOldestObservations())
                .containsExactly(
                        ObservationStoreEntity.create(observationId, unencryptedObservationBatch));
        assertThat(testOnlyDao.getReportKeys()).containsExactly(reportKey);
        assertThat(testOnlyDao.getInitialEnabledTime()).isEqualTo(Optional.of(initialEnabledTime));

        cobaltDatabase.close();
    }
}
