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

package com.android.ondevicepersonalization.services.download;

import static android.app.job.JobScheduler.RESULT_FAILURE;
import static android.app.job.JobScheduler.RESULT_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDownloadProcessingJobServiceTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private OnDevicePersonalizationDownloadProcessingJobService mSpyService;

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID);

        mSpyService = spy(new OnDevicePersonalizationDownloadProcessingJobService());
    }

    @Test
    public void onStartJobTest() throws Exception {
        MockitoSession session = ExtendedMockito.mockitoSession().spyStatic(
                OnDevicePersonalizationExecutors.class).strictness(
                Strictness.LENIENT).startMocking();
        try {
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mContext.getPackageManager()).when(mSpyService).getPackageManager();
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getBackgroundExecutor);
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getLightweightExecutor);

            boolean result = mSpyService.onStartJob(mock(JobParameters.class));

            // Wait for the future to complete (fail on no data being downloaded in this case).
            Thread.sleep(5 * 1000);

            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            boolean result = mSpyService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStopJobTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().strictness(
                Strictness.LENIENT).startMocking();
        try {
            assertTrue(mSpyService.onStopJob(mock(JobParameters.class)));
        } finally {
            session.finishMocking();
        }
    }


    @Test
    public void testSuccessfulScheduling() {
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        assertEquals(RESULT_SUCCESS,
                OnDevicePersonalizationDownloadProcessingJobService.schedule(mContext));
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID) != null);
        assertEquals(RESULT_FAILURE,
                OnDevicePersonalizationDownloadProcessingJobService.schedule(mContext));
    }
}
