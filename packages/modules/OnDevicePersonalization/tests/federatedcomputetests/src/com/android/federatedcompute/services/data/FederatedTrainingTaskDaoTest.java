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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@MockStatic(ClientErrorLogger.class)
public final class FederatedTrainingTaskDaoTest {

    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final String PACKAGE_NAME = "app_package_name";
    private static final String POPULATION_NAME = "population_name";
    private static final String TASK_NAME = "task_name";
    private static final String TASK_ID = "task_id";
    private static final String SERVER_ADDRESS = "https://server.uri/";
    private static final int JOB_ID = 123;
    private static final String OWNER_ID = "com.android.pckg.name/com.android.class.name";
    private static final String OWNER_ID_CERT_DIGEST = "123SOME45DIGEST78";
    private static final Long CREATION_TIME = 1233L;
    private static final Long LAST_SCHEDULE_TIME = 1230L;
    private static final Long LAST_RUN_START_TIME = 1200L;
    private static final Long LAST_RUN_END_TIME = 1210L;
    private static final Long EARLIEST_NEXT_RUN_TIME = 1290L;
    private static final byte[] INTERVAL_OPTIONS = createDefaultTrainingIntervalOptions();
    private static final byte[] TRAINING_CONSTRAINTS = createDefaultTrainingConstraints();
    private static final TaskHistory TASK_HISTORY =
            new TaskHistory.Builder()
                    .setJobId(JOB_ID)
                    .setTaskId(TASK_ID)
                    .setPopulationName(POPULATION_NAME)
                    .setContributionTime(100L)
                    .setContributionRound(10)
                    .setTotalParticipation(2)
                    .build();

    private FederatedTrainingTaskDao mTrainingTaskDao;
    private Context mContext;

    @Mock private ClientErrorLogger mMockClientErrorLogger;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mContext);
        when(ClientErrorLogger.getInstance()).thenReturn(mMockClientErrorLogger);
    }

    @After
    public void cleanUp() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void findAndRemoveTaskByJobId_success() {
        FederatedTrainingTask task = createDefaultFederatedTrainingTask();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task);
        int jobId2 = 456;
        FederatedTrainingTask task2 =
                createDefaultFederatedTrainingTask().toBuilder().jobId(jobId2).build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task2);
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(2);

        FederatedTrainingTask removedTask = mTrainingTaskDao.findAndRemoveTaskByJobId(JOB_ID);

        assertThat(DataTestUtil.isEqualTask(removedTask, task)).isTrue();
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(1);
    }

    @Test
    public void findAndRemoveTaskByJobId_nonExist() {
        FederatedTrainingTask removedTask = mTrainingTaskDao.findAndRemoveTaskByJobId(JOB_ID);

        assertThat(removedTask).isNull();
        verify(mMockClientErrorLogger)
                .logError(
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void findAndRemoveTaskByPopulationNameAndJobId_success() {
        FederatedTrainingTask task = createDefaultFederatedTrainingTask();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task);
        FederatedTrainingTask task2 =
                createDefaultFederatedTrainingTask().toBuilder()
                        .jobId(456)
                        .populationName(POPULATION_NAME + "_2")
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task2);
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(2);

        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationAndJobId(POPULATION_NAME, JOB_ID);

        assertThat(DataTestUtil.isEqualTask(removedTask, task)).isTrue();
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(1);
    }

    @Test
    public void findAndRemoveTaskByPopulationNameAndJobId_nonExist() {
        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationAndJobId(POPULATION_NAME, JOB_ID);

        assertThat(removedTask).isNull();
        verify(mMockClientErrorLogger)
                .logError(
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void findAndRemoveTaskByPopulationName_success() {
        FederatedTrainingTask task = createDefaultFederatedTrainingTask();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task);
        FederatedTrainingTask task2 =
                createDefaultFederatedTrainingTask().toBuilder()
                        .jobId(456)
                        .populationName(POPULATION_NAME + "_2")
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task2);
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(2);

        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationName(POPULATION_NAME);

        assertThat(DataTestUtil.isEqualTask(removedTask, task)).isTrue();
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(1);
    }

    @Test
    public void findAndRemoveTaskByPopulationNameAndCallingPackage_success() {
        FederatedTrainingTask task = createDefaultFederatedTrainingTask();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task);
        FederatedTrainingTask task2 =
                createDefaultFederatedTrainingTask().toBuilder()
                        .jobId(456)
                        .appPackageName(PACKAGE_NAME)
                        .populationName(POPULATION_NAME + "_2")
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task2);
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(2);

        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationNameAndCallingPackage(
                        POPULATION_NAME, PACKAGE_NAME);

        assertThat(DataTestUtil.isEqualTask(removedTask, task)).isTrue();
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(1);
    }

    @Test
    public void findAndRemoveTaskByPopulationName_nonExist() {
        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationName(POPULATION_NAME);

        assertThat(removedTask).isNull();
        verify(mMockClientErrorLogger)
                .logError(
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void findAndRemoveTaskByPopulationNameAndCallingPackage_nonExist() {
        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationNameAndCallingPackage(
                        POPULATION_NAME, PACKAGE_NAME);

        assertThat(removedTask).isNull();
        verify(mMockClientErrorLogger)
                .logError(
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void findAndRemoveTaskByPopulationNameAndAndOwnerId_success() {
        FederatedTrainingTask task = createDefaultFederatedTrainingTask();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task);
        FederatedTrainingTask task2 =
                createDefaultFederatedTrainingTask().toBuilder()
                        .jobId(456)
                        .populationName(POPULATION_NAME + "_2")
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task2);
        FederatedTrainingTask task3 =
                createDefaultFederatedTrainingTask().toBuilder()
                        .jobId(457)
                        .ownerId(OWNER_ID + "_2")
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task3);
        FederatedTrainingTask task4 =
                createDefaultFederatedTrainingTask().toBuilder()
                        .jobId(458)
                        .ownerIdCertDigest(OWNER_ID_CERT_DIGEST + "_2")
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task4);
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(4);

        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationNameAndOwnerId(
                        POPULATION_NAME, OWNER_ID, OWNER_ID_CERT_DIGEST);

        assertThat(DataTestUtil.isEqualTask(removedTask, task)).isTrue();
        assertThat(mTrainingTaskDao.getFederatedTrainingTask(null, null)).hasSize(3);
    }

    @Test
    public void findAndRemoveTaskByPopulationNameAndOwnerId_nonExist() {
        FederatedTrainingTask removedTask =
                mTrainingTaskDao.findAndRemoveTaskByPopulationNameAndOwnerId(
                        POPULATION_NAME, OWNER_ID, OWNER_ID_CERT_DIGEST);

        assertThat(removedTask).isNull();
        verify(mMockClientErrorLogger)
                .logError(
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DELETE_TASK_FAILURE),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void getLatestTaskHistory_nonExist() {
        TaskHistory taskHistory =
                mTrainingTaskDao.getLatestTaskHistory(JOB_ID, POPULATION_NAME, TASK_NAME);

        assertThat(taskHistory).isNull();
    }

    @Test
    public void insertTaskHistory_success() {
        assertTrue(mTrainingTaskDao.updateOrInsertTaskHistory(TASK_HISTORY));

        TaskHistory taskHistory =
                mTrainingTaskDao.getLatestTaskHistory(JOB_ID, POPULATION_NAME, TASK_ID);

        assertThat(taskHistory).isEqualTo(TASK_HISTORY);
    }

    @Test
    public void updateTaskHistory_success() {
        mTrainingTaskDao.updateOrInsertTaskHistory(TASK_HISTORY);

        // Update the same task.
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(500L)
                        .build());

        TaskHistory taskHistory =
                mTrainingTaskDao.getLatestTaskHistory(JOB_ID, POPULATION_NAME, TASK_ID);
        assertThat(taskHistory.getContributionRound()).isEqualTo(15);
        assertThat(taskHistory.getTotalParticipation()).isEqualTo(3);
        assertThat(taskHistory.getContributionTime()).isEqualTo(500L);
    }

    @Test
    public void deleteExpiredTaskHistory_success() {
        TaskHistory record1 =
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(100)
                        .build();
        TaskHistory record2 =
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(300)
                        .build();
        mTrainingTaskDao.updateOrInsertTaskHistory(record1);
        mTrainingTaskDao.updateOrInsertTaskHistory(record2);

        assertThat(mTrainingTaskDao.getTaskHistoryList(JOB_ID, POPULATION_NAME, TASK_ID))
                .containsExactly(record1, record2);

        // record1 is expired because contribution time (100) < deletion time (200).
        int rowDeleted = mTrainingTaskDao.deleteExpiredTaskHistory(200);

        assertThat(rowDeleted).isEqualTo(1);
        assertThat(mTrainingTaskDao.getTaskHistoryList(JOB_ID, POPULATION_NAME, TASK_ID))
                .containsExactly(record2);
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

    private FederatedTrainingTask createDefaultFederatedTrainingTask() {
        return FederatedTrainingTask.builder()
                .appPackageName(PACKAGE_NAME)
                .jobId(JOB_ID)
                .ownerId(OWNER_ID)
                .ownerIdCertDigest(OWNER_ID_CERT_DIGEST)
                .populationName(POPULATION_NAME)
                .serverAddress(SERVER_ADDRESS)
                .intervalOptions(INTERVAL_OPTIONS)
                .constraints(TRAINING_CONSTRAINTS)
                .creationTime(CREATION_TIME)
                .lastScheduledTime(LAST_SCHEDULE_TIME)
                .lastRunStartTime(LAST_RUN_START_TIME)
                .lastRunEndTime(LAST_RUN_END_TIME)
                .earliestNextRunTime(EARLIEST_NEXT_RUN_TIME)
                .schedulingReason(SchedulingReason.SCHEDULING_REASON_NEW_TASK)
                .build();
    }
}
