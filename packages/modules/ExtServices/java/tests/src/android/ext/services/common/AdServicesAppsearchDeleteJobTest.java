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

package android.ext.services.common;


import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appsearch.app.AppSearchResult;
import androidx.appsearch.app.SetSchemaResponse;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AdServicesAppsearchDeleteJobTest {

    private MockitoSession mMockitoSession;

    @Spy
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Spy
    private AdServicesAppsearchDeleteJob mAdServicesAppsearchDeleteJob;

    @Mock
    private AdservicesPhFlags mAdservicesPhFlags;

    @Mock
    private SharedPreferences mSharedPreferences;

    @Mock
    private SharedPreferences.Editor mEditor;

    @Rule
    public final Expect expect = Expect.create();

    public static final String TEST = "test";

    private final Executor mExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    @Before
    public void setup() {
        mMockitoSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();
        doReturn(mAdservicesPhFlags).when(mAdServicesAppsearchDeleteJob).getAdservicesPhFlags();
        doReturn(mSharedPreferences).when(mAdServicesAppsearchDeleteJob).getSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putLong(any(), anyLong());
        doReturn(mEditor).when(mEditor).putBoolean(any(), anyBoolean());
        doReturn(mEditor).when(mEditor).putInt(any(), anyInt());
        doReturn(true).when(mEditor).commit();
        doNothing().when(mAdServicesAppsearchDeleteJob).setReschedule(any(), anyBoolean());
        doReturn(0L).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 0L);
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void hasMinMinutestPassed_test() {
        assertWithMessage("hasMinMinutesPassed from 1693785600L to 1000 mins should be true")
                .that(mAdServicesAppsearchDeleteJob
                        .hasMinMinutesPassed(1693785600L, 1000))
                .isTrue();
        assertWithMessage("hasMinMinutesPassed from current time to 1000 mins"
                + " should be false").that(mAdServicesAppsearchDeleteJob
                .hasMinMinutesPassed(System.currentTimeMillis(),
                        1000)).isFalse();
    }

    @Test
    public void deleteAppsearchDb_onMigrationfailure_shouldBeFalse()
            throws Exception {
        SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
        SetSchemaResponse.MigrationFailure failure =
                new SetSchemaResponse.MigrationFailure(
                        /* namespace= */ TEST,
                        /* id= */ TEST,
                        /* schemaType= */ TEST,
                        /* appSearchResult= */ AppSearchResult.newFailedResult(1, TEST));
        when(mockResponse.getMigrationFailures()).thenReturn(List.of(failure));
        doReturn(mockResponse).when(mAdServicesAppsearchDeleteJob).getDeleteSchemaResponse(
                any(), any(), any());
        assertWithMessage("deleteAppsearchDb result should be false")
                .that(mAdServicesAppsearchDeleteJob.deleteAppsearchDb(mContext, mExecutor, TEST))
                .isFalse();
    }

    @Test
    public void deleteAppsearchDb_onException_shouldBeFalse()
            throws Exception {
        doThrow(new RuntimeException(TEST)).when(
                mAdServicesAppsearchDeleteJob).getDeleteSchemaResponse(any(), any(), any());
        assertWithMessage("deleteAppsearchDb result should be false")
                .that(mAdServicesAppsearchDeleteJob.deleteAppsearchDb(mContext, mExecutor, TEST))
                .isFalse();
    }

    @Test
    public void deleteAppsearchDb_onSuccess_shouldBeTrue()
            throws Exception {
        SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
        when(mockResponse.getMigrationFailures()).thenReturn(new ArrayList<>());
        doReturn(mockResponse).when(mAdServicesAppsearchDeleteJob).getDeleteSchemaResponse(
                any(), any(), any());
        assertWithMessage("deleteAppsearchDb result should be true")
                .that(mAdServicesAppsearchDeleteJob.deleteAppsearchDb(mContext, mExecutor, TEST))
                .isTrue();

    }

    @Test
    public void onCancelJob_shouldNotReschedule()
            throws Exception {
        doNothing().when(mAdServicesAppsearchDeleteJob).setReschedule(any(),
                anyBoolean());
        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(0).when(jobParameters).getJobId();
        final JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.getPendingJob(anyInt())).thenReturn(null);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(jobScheduler);
        doNothing().when(jobScheduler).cancel(anyInt());

        // Execute
        mAdServicesAppsearchDeleteJob.cancelPeriodicJob(mContext, jobParameters);

        // Validate
        verify(jobScheduler).cancel(0);
        verify(mAdServicesAppsearchDeleteJob).setReschedule(any(), eq(false));

    }

    @Test
    public void schedulePeriodic_onDisabledFlag_shouldNotSchedule() {
        final JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.getPendingJob(anyInt()))
                .thenReturn(null);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(jobScheduler);
        when(jobScheduler.schedule(any())).thenReturn(1);
        doReturn(false).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        // Execute
        AdServicesAppsearchDeleteJob
                .scheduleAdServicesAppsearchDeletePeriodicJob(
                        mContext, mAdservicesPhFlags);
        // Validate
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(jobScheduler, never()).schedule(any());

    }

    @Test
    public void schedulePeriodic_onExistingJob_shouldNotSchedule() {
        final JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.getPendingJob(anyInt()))
                .thenReturn(Mockito.mock((JobInfo.class)));
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(jobScheduler);
        when(jobScheduler.schedule(any())).thenReturn(1);
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        // Execute
        AdServicesAppsearchDeleteJob
                .scheduleAdServicesAppsearchDeletePeriodicJob(
                        mContext, mAdservicesPhFlags);
        // Validate

        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(jobScheduler).getPendingJob(anyInt());
        verify(jobScheduler, never()).schedule(any());

    }

    @Test
    public void schedulePeriodic_onNonExistingJob_EnabledJob_shouldSchedule() {
        final JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.getPendingJob(anyInt()))
                .thenReturn(null);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(jobScheduler);
        when(jobScheduler.schedule(any())).thenReturn(0);
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        doReturn(3600000L).when(mAdservicesPhFlags)
                .getAppsearchDeletePeriodicIntervalMillis();
        doReturn(3600000L).when(mAdservicesPhFlags).getAppsearchDeleteJobFlexMillis();

        final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);

        // Execute
        AdServicesAppsearchDeleteJob
                .scheduleAdServicesAppsearchDeletePeriodicJob(
                        mContext, mAdservicesPhFlags);

        // Validate
        verify(jobScheduler)
                .getPendingJob(AdServicesAppsearchDeleteJob.JOB_ID);
        verify(mAdservicesPhFlags)
                .isAppsearchDeleteJobEnabled();
        verify(mAdservicesPhFlags)
                .getAppsearchDeletePeriodicIntervalMillis();
        verify(mAdservicesPhFlags)
                .getAppsearchDeleteJobFlexMillis();
        verify(jobScheduler).schedule(captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(AdServicesAppsearchDeleteJob.JOB_ID,
                captor.getValue().getId());
        assertEquals("android.ext.services.common"
                        + ".AdServicesAppsearchDeleteJob",
                captor.getValue().getService().getClassName());
        assertTrue(captor.getValue().isPersisted());
        assertEquals(3600000L, captor.getValue().getIntervalMillis());
        assertEquals(3600000L, captor.getValue().getFlexMillis());

    }

    @Test
    public void onStartJob_disabledJob()
            throws Exception {
        doReturn(false).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags)
                .isAppsearchDeleteJobEnabled();
        verify(mAdServicesAppsearchDeleteJob)
                .cancelPeriodicJob(any(), any());

        // Not Wanted
        verify(mSharedPreferences, never())
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdServicesAppsearchDeleteJob, never())
                .isAppsearchDbEmpty(any(), any(), any());
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();

    }

    @Test
    public void onStartJob_onDoNothing()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        doReturn(true).when(mAdservicesPhFlags)
                .shouldDoNothingAdServicesAppsearchDeleteJob();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags)
                .isAppsearchDeleteJobEnabled();

        // Not Wanted
        verify(mSharedPreferences, never())
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdServicesAppsearchDeleteJob, never())
                .isAppsearchDbEmpty(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never())
                .cancelPeriodicJob(any(), any());
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();

    }

    @Test
    public void onStartJob_enabledJob_firstTimeOta_appsearchDataNotFound()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(true).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());
        doReturn(0L).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mEditor)
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());
        verify(mEditor)
                .putBoolean(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND,
                        false);

        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mAdServicesAppsearchDeleteJob, never()).hasMinMinutesPassed(anyLong(), anyLong());
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
    }

    @Test
    public void onStartJob_enabledJob_firstTimeOta_appsearchDataFound()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());
        doReturn(0L).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mEditor)
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob).isAppsearchDbEmpty(any(), any(), any());
        verify(mEditor)
                .putBoolean(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND,
                        true);
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mAdservicesPhFlags, never()).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).setReschedule(jobParameters, false);
        verify(mAdServicesAppsearchDeleteJob, never()).hasMinMinutesPassed(anyLong(), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
    }

    @Test
    public void onStartJob_enabledJob_minMinutesFromOTAPassed_shouldDelete()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();


        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = 1693785600L;
        long minMinutesFromOtaToTToDelete = 100L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);

        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();
        doReturn(true).when(mAdServicesAppsearchDeleteJob)
                .deleteAppsearchDbs(any(), any(), any());
        doNothing().when(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        //Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdServicesAppsearchDeleteJob).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());
        verify(mEditor).putLong(
                eq(AdServicesAppsearchDeleteJob
                        .SHARED_PREFS_KEY_ADSERVICES_APPSEARCH_DELETED),
                anyLong());
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never()).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdservicesPhFlags, never()).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());
    }

    @Test
    public void onStartJob_enabledJob_deleteDbsException_shouldNotCancel()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        doReturn(5).when(mAdservicesPhFlags).getMaxAppsearchAdServicesDeleteAttempts();
        doReturn(1).when(mSharedPreferences)
                .getInt(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT, 0);
        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();

        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = 1693785600L;
        long minMinutesFromOtaToTToDelete = 100L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .deleteAppsearchDbs(any(), any(), any());
        doNothing().when(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdServicesAppsearchDeleteJob).deleteAppsearchDbs(any(), any(), any());

        verify(mEditor).putInt(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT,
                2);
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never()).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdservicesPhFlags, never()).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
        verify(mEditor, never()).putLong(
                eq(AdServicesAppsearchDeleteJob
                        .SHARED_PREFS_KEY_ADSERVICES_APPSEARCH_DELETED),
                anyLong());
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());

    }

    @Test
    public void onStartJob_enabledJob_deleteDbsException_onMaxAttempts_shouldCancel()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        doReturn(5).when(mAdservicesPhFlags).getMaxAppsearchAdServicesDeleteAttempts();
        doReturn(4).when(mSharedPreferences)
                .getInt(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT, 0);
        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();

        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = 1693785600L;
        long minMinutesFromOtaToTToDelete = 100L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);

        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .deleteAppsearchDbs(any(), any(), any());
        doNothing().when(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        //Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdServicesAppsearchDeleteJob).deleteAppsearchDbs(any(), any(), any());
        verify(mEditor).putInt(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ATTEMPTED_DELETE_COUNT,
                5);
        verify(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never())
                .isAppsearchDbEmpty(any(), any(), any());
        verify(mAdservicesPhFlags, never()).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mEditor, never()).putLong(
                eq(AdServicesAppsearchDeleteJob
                        .SHARED_PREFS_KEY_ADSERVICES_APPSEARCH_DELETED),
                anyLong());
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());

    }

    @Test
    public void onStartJob_enabledJob_minMinutesToCheckAdServicesNotPassed_shouldDoNothing()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = System.currentTimeMillis();
        long minMinutesFromOtaToTToDelete = 1000L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();
        long minMinutesToCheckAdServices = 100L;
        doReturn(minMinutesToCheckAdServices).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToCheckAdServicesStatus();
        doNothing().when(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesToCheckAdServices);
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never())
                .isAppsearchDbEmpty(any(), any(), any());
        verify(mAdservicesPhFlags, never()).isAdServicesEnabled();
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
    }

    @Test
    public void onStartJob_enabledJob_adservicesDisabled_shouldNotDelete()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = System.currentTimeMillis();
        long minMinutesFromOtaToTToDelete = 10000L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();
        doReturn(true).when(mAdServicesAppsearchDeleteJob)
                .deleteAppsearchDbs(any(), any(), any());
        doReturn(0L).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        doReturn(false).when(mAdservicesPhFlags).isAdServicesEnabled();
        long minMinutesToCheckAdServices = 0;
        doReturn(minMinutesToCheckAdServices).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToCheckAdServicesStatus();

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        //Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesToCheckAdServices);
        verify(mAdservicesPhFlags).isAdServicesEnabled();

        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mAdServicesAppsearchDeleteJob, never()).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
        verify(mEditor, never())
                .putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0);
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());

    }

    @Test
    public void onStartJob_enabledJob_adservicesEnabledFirstTime_shouldNotDelete()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = System.currentTimeMillis();
        long minMinutesFromOtaToTToDelete = 10000L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();

        doReturn(true).when(mAdservicesPhFlags).isAdServicesEnabled();
        doReturn(0L).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        long minMinutesToCheckAdServices = 0L;
        doReturn(minMinutesToCheckAdServices).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToCheckAdServicesStatus();

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        //Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesToCheckAdServices);
        verify(mAdservicesPhFlags).isAdServicesEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        verify(mAdservicesPhFlags).isAdServicesEnabled();
        verify(mEditor).putLong(
                eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE),
                anyLong());
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never()).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
        verify(mAdservicesPhFlags, never()).getMinMinutesToDeleteFromAdServicesEnabled();
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());

    }

    @Test
    public void onStartJob_enabledJob_adservicesEnabled_MinMinsNotPassed_shouldNotDelete()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = System.currentTimeMillis();
        long minMinutesFromOtaToTToDelete = 10000L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();

        doReturn(true).when(mAdservicesPhFlags).isAdServicesEnabled();
        long adServicesDate = System.currentTimeMillis();
        doReturn(adServicesDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        long minMinsForAdServicesEnabled = 9000000L;
        doReturn(minMinsForAdServicesEnabled).when(mAdservicesPhFlags)
                .getMinMinutesToDeleteFromAdServicesEnabled();
        long minMinutesToCheckAdServices = 0;
        doReturn(minMinutesToCheckAdServices).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToCheckAdServicesStatus();

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesToCheckAdServices);
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        verify(mAdservicesPhFlags).isAdServicesEnabled();
        verify(mAdservicesPhFlags).getMinMinutesToDeleteFromAdServicesEnabled();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(adServicesDate, minMinsForAdServicesEnabled);

        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob, never()).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
        verify(mEditor, never()).putLong(
                eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE),
                anyLong());
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());

    }

    @Test
    public void onStartJob_enabledJob_adservicesDisabled_PostEnabled_shouldOverrideEnabledDate()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());

        long otaDate = System.currentTimeMillis();
        long minMinutesFromOtaToTToDelete = 10000L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();

        doReturn(false).when(mAdservicesPhFlags).isAdServicesEnabled();
        long adServicesDate = System.currentTimeMillis();
        doReturn(adServicesDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        long minMinsForAdServicesEnabled = 0L;
        doReturn(minMinsForAdServicesEnabled).when(mAdservicesPhFlags)
                .getMinMinutesToDeleteFromAdServicesEnabled();
        long minMinutesToCheckAdServices = 0;
        doReturn(minMinutesToCheckAdServices).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToCheckAdServicesStatus();

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        //Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesToCheckAdServices);
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        verify(mEditor)
                .putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0);
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdServicesAppsearchDeleteJob, never()).isAppsearchDbEmpty(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob, never()).cancelPeriodicJob(any(), any());
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());
    }

    @Test
    public void onStartJob_enabledJob_adservicesEnabled_MinMinsPassed_shouldDelete()
            throws Exception {
        doReturn(true).when(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();

        JobParameters jobParameters = mock(JobParameters.class);
        doReturn(AdServicesAppsearchDeleteJob.JOB_ID).when(jobParameters).getJobId();
        doReturn(false).when(mAdServicesAppsearchDeleteJob)
                .isAppsearchDbEmpty(any(), any(), any());
        doReturn(true).when(mAdServicesAppsearchDeleteJob)
                .deleteAppsearchDbs(any(), any(), any());
        long otaDate = System.currentTimeMillis();
        long minMinutesFromOtaToTToDelete = 10000L;
        doReturn(otaDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        doReturn(minMinutesFromOtaToTToDelete).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToDeleteAppsearchData();

        doReturn(true).when(mAdservicesPhFlags).isAdServicesEnabled();
        long adServicesDate = 1693785600L;
        doReturn(adServicesDate).when(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        long minMinsForAdServicesEnabled = 10L;
        doReturn(minMinsForAdServicesEnabled).when(mAdservicesPhFlags)
                .getMinMinutesToDeleteFromAdServicesEnabled();
        long minMinutesToCheckAdServices = 0L;
        doReturn(minMinutesToCheckAdServices).when(mAdservicesPhFlags)
                .getMinMinutesFromOtaToCheckAdServicesStatus();
        doNothing().when(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());

        // Execute
        mAdServicesAppsearchDeleteJob.onStartJob(jobParameters);

        // Validate
        // Wanted
        verify(mAdservicesPhFlags).isAppsearchDeleteJobEnabled();
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE, 0L);
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToDeleteAppsearchData();
        verify(mAdservicesPhFlags).getMinMinutesFromOtaToCheckAdServicesStatus();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(otaDate, minMinutesFromOtaToTToDelete);
        verify(mSharedPreferences)
                .getLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE,
                        0L);
        verify(mAdservicesPhFlags).isAdServicesEnabled();
        verify(mAdservicesPhFlags).getMinMinutesToDeleteFromAdServicesEnabled();
        verify(mAdServicesAppsearchDeleteJob)
                .hasMinMinutesPassed(adServicesDate, minMinsForAdServicesEnabled);
        verify(mAdServicesAppsearchDeleteJob).deleteAppsearchDbs(any(), any(), any());
        verify(mAdServicesAppsearchDeleteJob).cancelPeriodicJob(any(), any());
        verify(mEditor).putLong(
                eq(AdServicesAppsearchDeleteJob
                        .SHARED_PREFS_KEY_ADSERVICES_APPSEARCH_DELETED),
                anyLong());
        verify(mEditor).putLong(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_JOB_RUN_COUNT, 1);
        verify(mEditor).commit();

        // Not Wanted
        verify(mEditor, never())
                .putLong(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_OTA_DATE), anyLong());
        verify(mAdServicesAppsearchDeleteJob, never())
                .isAppsearchDbEmpty(any(), any(), any());
        verify(mEditor, never()).putLong(
                eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_ADSERVICES_ENABLED_DATE),
                anyLong());
        verify(mEditor, never())
                .putBoolean(eq(AdServicesAppsearchDeleteJob.SHARED_PREFS_KEY_APPSEARCH_DATA_FOUND),
                        anyBoolean());

    }

}
