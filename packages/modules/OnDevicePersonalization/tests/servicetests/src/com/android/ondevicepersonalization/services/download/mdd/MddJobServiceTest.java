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

package com.android.ondevicepersonalization.services.download.mdd;

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.MDD_TASK_TAG_KEY;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;

import static org.junit.Assert.assertThrows;
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

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class MddJobServiceTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private JobScheduler mMockJobScheduler;
    private MddJobService mSpyService;
    private UserPrivacyStatus mUserPrivacyStatus = UserPrivacyStatus.getInstance();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        mUserPrivacyStatus.setPersonalizationStatusEnabled(true);
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);

        mSpyService = spy(new MddJobService());
        mMockJobScheduler = mock(JobScheduler.class);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(null).when(mMockJobScheduler).getPendingJob(DOWNLOAD_PROCESSING_TASK_JOB_ID);
        doReturn(0).when(mMockJobScheduler).schedule(any());
        doReturn(mContext.getPackageName()).when(mSpyService).getPackageName();
    }

    @Test
    public void onStartJobTest() {
        PhFlagsTestUtil.disableGlobalKillSwitch();
        MockitoSession session = ExtendedMockito.mockitoSession().spyStatic(
                OnDevicePersonalizationExecutors.class).strictness(
                Strictness.LENIENT).startMocking();
        try {
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getBackgroundExecutor);
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getLightweightExecutor);

            JobParameters jobParameters = mock(JobParameters.class);
            PersistableBundle extras = new PersistableBundle();
            extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
            doReturn(extras).when(jobParameters).getExtras();

            boolean result = mSpyService.onStartJob(jobParameters);
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
            verify(mMockJobScheduler, times(1)).schedule(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
            PersistableBundle extras = new PersistableBundle();
            extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
            JobInfo jobInfo =
                    new JobInfo.Builder(
                            MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                            new ComponentName(mContext, MddJobService.class))
                            .setRequiresDeviceIdle(true)
                            .setRequiresCharging(false)
                            .setRequiresBatteryNotLow(true)
                            .setPeriodic(21_600_000L)
                            .setPersisted(true)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                            .setExtras(extras)
                            .build();
            mJobScheduler.schedule(jobInfo);
            assertTrue(mJobScheduler.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                    != null);
            doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            JobParameters jobParameters = mock(JobParameters.class);
            doReturn(extras).when(jobParameters).getExtras();
            boolean result = mSpyService.onStartJob(jobParameters);
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
            verify(mMockJobScheduler, times(0)).schedule(any());
            assertTrue(mJobScheduler.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                    == null);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestPersonalizationBlocked() {
        mUserPrivacyStatus.setPersonalizationStatusEnabled(false);
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
            PersistableBundle extras = new PersistableBundle();
            extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
            JobInfo jobInfo =
                    new JobInfo.Builder(
                            MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                            new ComponentName(mContext, MddJobService.class))
                            .setRequiresDeviceIdle(true)
                            .setRequiresCharging(false)
                            .setRequiresBatteryNotLow(true)
                            .setPeriodic(21_600_000L)
                            .setPersisted(true)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                            .setExtras(extras)
                            .build();
            mJobScheduler.schedule(jobInfo);
            assertTrue(mJobScheduler.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                    != null);
            doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            JobParameters jobParameters = mock(JobParameters.class);
            doReturn(extras).when(jobParameters).getExtras();
            boolean result = mSpyService.onStartJob(jobParameters);
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
            verify(mMockJobScheduler, times(0)).schedule(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobNoTaskTagTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().spyStatic(
                OnDevicePersonalizationExecutors.class).strictness(
                Strictness.LENIENT).startMocking();
        try {
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getBackgroundExecutor);
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getLightweightExecutor);

            assertThrows(IllegalArgumentException.class,
                    () -> mSpyService.onStartJob(mock(JobParameters.class)));
            verify(mSpyService, times(0)).jobFinished(any(), eq(false));
            verify(mMockJobScheduler, times(0)).schedule(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobFailHandleTaskTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().spyStatic(
                OnDevicePersonalizationExecutors.class).strictness(
                Strictness.LENIENT).startMocking();
        try {
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getBackgroundExecutor);
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getLightweightExecutor);

            JobParameters jobParameters = mock(JobParameters.class);
            PersistableBundle extras = new PersistableBundle();
            extras.putString(MDD_TASK_TAG_KEY, "INVALID_TASK_TAG_KEY");
            doReturn(extras).when(jobParameters).getExtras();

            boolean result = mSpyService.onStartJob(jobParameters);
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
            verify(mMockJobScheduler, times(0)).schedule(any());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStopJobTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().strictness(
                Strictness.LENIENT).startMocking();
        try {
            JobParameters jobParameters = mock(JobParameters.class);
            PersistableBundle extras = new PersistableBundle();
            extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
            doReturn(extras).when(jobParameters).getExtras();

            assertTrue(mSpyService.onStopJob(jobParameters));
            verify(mMockJobScheduler, times(0)).schedule(any());
        } finally {
            session.finishMocking();
        }
    }
}
