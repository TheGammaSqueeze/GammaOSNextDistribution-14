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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.common.JobServiceTestHelper.createJobFinishedCallback;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.service.measurement.reporting.VerboseDebugReportingJobService.VERBOSE_DEBUG_REPORT_JOB_ID;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.JobServiceCallback;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/** Unit test for {@link VerboseDebugReportingJobService} */
public class VerboseDebugReportingJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID =
            MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB.getJobId();

    private static final long WAIT_IN_MILLIS = 1_000L;

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;
    private JobParameters mJobParameters;
    private VerboseDebugReportingJobService mSpyService;
    private Flags mMockFlags;
    private AdServicesJobServiceLogger mSpyLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AdServicesConfig.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(EnrollmentDao.class)
                    .spyStatic(VerboseDebugReportingJobService.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdServicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        mSpyService = spy(new VerboseDebugReportingJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);
        mJobParameters = mock(JobParameters.class);

        mMockFlags = mock(Flags.class);
        mSpyLogger = getSpiedAdServicesJobServiceLogger(CONTEXT, mMockFlags);
    }

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    JobServiceCallback callback = createJobFinishedCallback(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertFalse(result);

                    callback.assertJobFinished();
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            VerboseDebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    ExtendedMockito.doNothing()
                            .when(mSpyLogger)
                            .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

                    JobServiceCallback callback = createJobFinishedCallback(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertTrue(result);

                    callback.assertJobFinished();
                    verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            VerboseDebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    ExtendedMockito.doNothing()
                            .when(mSpyLogger)
                            .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

                    JobServiceCallback callback = createJobFinishedCallback(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    callback.assertJobFinished();

                    assertTrue(result);

                    JobServiceCallback secondCallback = createJobFinishedCallback(mSpyService);
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1))
                            .jobFinished(any(), anyBoolean());

                    result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    secondCallback.assertJobFinished();

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    verify(mMockDatastoreManager, times(2)).runInTransactionWithResult(any());
                    verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    ExtendedMockito.doReturn(true)
                            .when(
                                    () ->
                                            ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                                    any(Context.class)));

                    JobServiceCallback callback = createJobFinishedCallback(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertFalse(result);

                    callback.assertJobFinished();
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                    ExtendedMockito.verifyZeroInteractions(
                            ExtendedMockito.staticMockMarker(FlagsFactory.class));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_sameJobInfoDontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            VERBOSE_DEBUG_REPORT_JOB_ID,
                                            new ComponentName(
                                                    mockContext,
                                                    VerboseDebugReportingJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_diffJobInfoDontForceSchedule_doesSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            VERBOSE_DEBUG_REPORT_JOB_ID,
                                            new ComponentName(
                                                    mockContext,
                                                    VerboseDebugReportingJobService.class))
                                    // Difference
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    doReturn(/* noJobInfo= */ null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .sendReports();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1)).onStartJob(any());

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        // Setup mock everything in job
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                .when(() -> EnrollmentDao.getInstance(any()));
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.schedule(any(), any()));
        mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    private void enableKillSwitch() {
        toggleKillSwitch(true);
    }

    private void disableKillSwitch() {
        toggleKillSwitch(false);
    }

    private void toggleKillSwitch(boolean value) {
        adServicesExtendedMockitoRule.mockGetFlags(mMockFlags);
        when(mMockFlags.getMeasurementJobVerboseDebugReportingKillSwitch()).thenReturn(value);
        when(mMockFlags.getMeasurementVerboseDebugReportingJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
    }

    private CountDownLatch createCountDownLatch() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(i -> countDown(countDownLatch)).when(mSpyService).jobFinished(any(), anyBoolean());
        return countDownLatch;
    }

    private Object countDown(CountDownLatch countDownLatch) {
        countDownLatch.countDown();
        return null;
    }
}
