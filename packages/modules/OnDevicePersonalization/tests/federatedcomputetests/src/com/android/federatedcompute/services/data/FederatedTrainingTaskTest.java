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

package com.android.federatedcompute.services.data;

import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.data.FederatedTraningTaskContract.FederatedTrainingTaskColumns;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;

import com.google.common.collect.Iterables;
import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class FederatedTrainingTaskTest {
    private static final String PACKAGE_NAME = "app_package_name";
    private static final String POPULATION_NAME = "population_name";
    private static final String SERVER_ADDRESS = "https://server.uri/";
    private static final String OWNER_ID =
            "com.android.pckg.name/com.android.class.name";
    private static final String OWNER_ID_CERT_DIGEST = "123SOME45DIGEST78";
    private static final int JOB_ID = 123;
    private static final Long CREATION_TIME = 1233L;
    private static final Long LAST_SCHEDULE_TIME = 1230L;
    private static final Long LAST_RUN_START_TIME = 1200L;
    private static final Long LAST_RUN_END_TIME = 1210L;
    private static final Long EARLIEST_NEXT_RUN_TIME = 1290L;
    private static final int SCHEDULING_REASON = SchedulingReason.SCHEDULING_REASON_NEW_TASK;
    private static final byte[] INTERVAL_OPTIONS = createDefaultTrainingIntervalOptions();
    private static final byte[] TRAINING_CONSTRAINTS = createDefaultTrainingConstraints();
    public static final int RESCHEDULE_COUNT = 2;

    private SQLiteDatabase mDatabase;
    private FederatedComputeDbHelper mDbHelper;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDbHelper = FederatedComputeDbHelper.getInstanceForTest(context);
        mDatabase = mDbHelper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void readAndWrite() throws Exception {
        FederatedTrainingTask task = createFederatedTrainingTaskWithAllFields();

        task.addToDatabase(mDatabase);

        assertThat(DatabaseUtils.queryNumEntries(mDatabase, FEDERATED_TRAINING_TASKS_TABLE))
                .isEqualTo(1);
        FederatedTrainingTask storedFederatedTrainingTask =
                Iterables.getOnlyElement(
                        FederatedTrainingTask.readFederatedTrainingTasksFromDatabase(
                                mDatabase, null, null));

        assertThat(DataTestUtil.isEqualTask(storedFederatedTrainingTask, task)).isTrue();
    }

    @Test
    public void readAndWrite_onlyRequiredFields() throws Exception {
        FederatedTrainingTask task = createFederatedTrainingTaskWithRequiredFields();

        task.addToDatabase(mDatabase);

        assertThat(DatabaseUtils.queryNumEntries(mDatabase, FEDERATED_TRAINING_TASKS_TABLE))
                .isEqualTo(1);
        FederatedTrainingTask storedFederatedTrainingTask =
                Iterables.getOnlyElement(
                        FederatedTrainingTask.readFederatedTrainingTasksFromDatabase(
                                mDatabase, null, null));

        assertThat(storedFederatedTrainingTask.jobId()).isEqualTo(JOB_ID);
        assertThat(storedFederatedTrainingTask.appPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(storedFederatedTrainingTask.creationTime()).isEqualTo(CREATION_TIME);
        assertThat(storedFederatedTrainingTask.lastScheduledTime()).isEqualTo(LAST_SCHEDULE_TIME);
        assertThat(storedFederatedTrainingTask.constraints()).isEqualTo(TRAINING_CONSTRAINTS);
        assertThat(storedFederatedTrainingTask.intervalOptions()).isEqualTo(INTERVAL_OPTIONS);
        // Unset integer field is 0 by default.
        assertThat(storedFederatedTrainingTask.lastRunStartTime()).isEqualTo(0);
        assertThat(storedFederatedTrainingTask.lastRunEndTime()).isEqualTo(0);
        assertThat(storedFederatedTrainingTask.lastRunStartTime()).isEqualTo(0);
        assertThat(storedFederatedTrainingTask.rescheduleCount()).isEqualTo(0);
    }

    @Test
    public void queryWithJobId() throws Exception {
        FederatedTrainingTask task1 = createFederatedTrainingTaskWithAllFields();
        task1.addToDatabase(mDatabase);
        FederatedTrainingTask task2 =
                createFederatedTrainingTaskWithAllFields().toBuilder().jobId(456).build();
        task2.addToDatabase(mDatabase);

        List<FederatedTrainingTask> trainingTaskList =
                FederatedTrainingTask.readFederatedTrainingTasksFromDatabase(
                        mDatabase,
                        FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID + " = ?",
                        new String[] {String.valueOf(JOB_ID)});

        assertThat(DataTestUtil.isEqualTask(trainingTaskList.get(0), task1)).isTrue();
    }

    @Test
    public void buildFederatedTrainingTask() {
        FederatedTrainingTask trainingTask = createFederatedTrainingTaskWithAllFields();

        assertThat(trainingTask.jobId()).isEqualTo(JOB_ID);
        assertThat(trainingTask.appPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(trainingTask.populationName()).isEqualTo(POPULATION_NAME);
        assertThat(trainingTask.serverAddress()).isEqualTo(SERVER_ADDRESS);
        assertThat(trainingTask.constraints()).isEqualTo(TRAINING_CONSTRAINTS);
        assertThat(trainingTask.intervalOptions()).isEqualTo(INTERVAL_OPTIONS);
        assertThat(trainingTask.creationTime()).isEqualTo(CREATION_TIME);
        assertThat(trainingTask.lastScheduledTime()).isEqualTo(LAST_SCHEDULE_TIME);
        assertThat(trainingTask.lastRunStartTime()).isEqualTo(LAST_RUN_START_TIME);
        assertThat(trainingTask.lastRunEndTime()).isEqualTo(LAST_RUN_END_TIME);
        assertThat(trainingTask.earliestNextRunTime()).isEqualTo(EARLIEST_NEXT_RUN_TIME);
        assertThat(trainingTask.schedulingReason()).isEqualTo(SCHEDULING_REASON);
        assertThat(trainingTask.rescheduleCount()).isEqualTo(RESCHEDULE_COUNT);
    }

    private static byte[] createDefaultTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(TrainingConstraints.createTrainingConstraints(builder, true, true, true));
        return builder.sizedByteArray();
    }

    private static byte[] createDefaultTrainingIntervalOptions() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder, SchedulingMode.ONE_TIME, 0));
        return builder.sizedByteArray();
    }

    private FederatedTrainingTask createFederatedTrainingTaskWithAllFields() {
        return FederatedTrainingTask.builder()
                .appPackageName(PACKAGE_NAME)
                .jobId(JOB_ID)
                .populationName(POPULATION_NAME)
                .ownerId(OWNER_ID)
                .ownerIdCertDigest(OWNER_ID_CERT_DIGEST)
                .serverAddress(SERVER_ADDRESS)
                .intervalOptions(INTERVAL_OPTIONS)
                .constraints(TRAINING_CONSTRAINTS)
                .creationTime(CREATION_TIME)
                .lastScheduledTime(LAST_SCHEDULE_TIME)
                .lastRunStartTime(LAST_RUN_START_TIME)
                .lastRunEndTime(LAST_RUN_END_TIME)
                .earliestNextRunTime(EARLIEST_NEXT_RUN_TIME)
                .schedulingReason(SCHEDULING_REASON)
                .rescheduleCount(RESCHEDULE_COUNT)
                .build();
    }

    private FederatedTrainingTask createFederatedTrainingTaskWithRequiredFields() {
        return FederatedTrainingTask.builder()
                .appPackageName(PACKAGE_NAME)
                .jobId(JOB_ID)
                .populationName(POPULATION_NAME)
                .ownerId(OWNER_ID)
                .ownerIdCertDigest(OWNER_ID_CERT_DIGEST)
                .serverAddress(SERVER_ADDRESS)
                .intervalOptions(INTERVAL_OPTIONS)
                .constraints(TRAINING_CONSTRAINTS)
                .creationTime(CREATION_TIME)
                .lastScheduledTime(LAST_SCHEDULE_TIME)
                .earliestNextRunTime(EARLIEST_NEXT_RUN_TIME)
                .schedulingReason(SCHEDULING_REASON)
                .build();
    }
}
