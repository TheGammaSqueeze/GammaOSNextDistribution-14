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

package com.android.federatedcompute.services.training;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.PhFlagsTestUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.intelligence.fcp.client.engine.TaskRetry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public final class FederatedJobServiceTest {
    private static final TaskRetry TASK_RETRY =
            TaskRetry.newBuilder().setRetryToken("foobar").build();
    private static final FLRunnerResult FL_RUNNER_SUCCESS_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.SUCCESS)
                    .setRetryInfo(
                            RetryInfo.newBuilder()
                                    .setRetryToken(TASK_RETRY.getRetryToken())
                                    .build())
                    .build();

    private FederatedJobService mSpyService;
    @Mock private FederatedComputeWorker mMockWorker;

    private Context mContext;

    private JobScheduler mJobScheduler;

    private MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();

        mSpyService = spy(new FederatedJobService());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mSpyService).when(mSpyService).getApplicationContext();
        mContext = ApplicationProvider.getApplicationContext();
        mJobScheduler = mContext.getSystemService(JobScheduler.class);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FederatedComputeExecutors.class)
                        .spyStatic(FederatedComputeWorker.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService())
                .when(() -> FederatedComputeExecutors.getBackgroundExecutor());
        ExtendedMockito.doReturn(mMockWorker).when(() -> FederatedComputeWorker.getInstance(any()));
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testOnStartJob() throws Exception {
        doReturn(Futures.immediateFuture(FL_RUNNER_SUCCESS_RESULT))
                .when(mMockWorker)
                .startTrainingRun(anyInt(), any());
        doNothing().when(mMockWorker).finish(eq(FL_RUNNER_SUCCESS_RESULT));

        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mMockWorker, times(1)).finish(eq(FL_RUNNER_SUCCESS_RESULT));
    }

    @Test
    public void testOnStartJobNullRunnerResult() throws Exception {
        doReturn(Futures.immediateFuture(null))
                .when(mMockWorker)
                .startTrainingRun(anyInt(), any());

        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mMockWorker, never()).finish(any());
        verify(mMockWorker).cleanUpActiveRun();
    }

    @Test
    public void testOnStartJobKillSwitch() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mMockWorker, never()).startTrainingRun(anyInt(), any());
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
    }

    @Test
    public void testOnStopJob() {
        doNothing().when(mMockWorker).finish(any(), eq(ContributionResult.FAIL), eq(true));

        // Do not reschedule in JobService. FederatedComputeJobManager will handle it.
        assertFalse(mSpyService.onStopJob(mock(JobParameters.class)));
    }
}
