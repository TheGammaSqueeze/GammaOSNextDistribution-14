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

package com.android.server.healthconnect;

import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@RunWith(AndroidJUnit4.class)
public class HealthConnectThreadSchedulerTest {
    private ThreadPoolExecutor mInternalTaskScheduler;
    private ThreadPoolExecutor mControllerTaskScheduler;
    private ThreadPoolExecutor mForegroundTaskScheduler;
    private ThreadPoolExecutor mBackgroundTaskScheduler;
    private long mInternalTaskSchedulerCompletedJobs;
    private long mControllerTaskSchedulerCompletedJobs;
    private long mForegroundTaskSchedulerCompletedJobs;
    private long mBackgroundTaskSchedulerCompletedJobs;
    private Context mContext;

    @Mock private Context mMockContext;
    @Mock private ActivityManager mActivityManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        HealthConnectThreadScheduler.resetThreadPools();

        mInternalTaskScheduler = HealthConnectThreadScheduler.sInternalBackgroundExecutor;
        mInternalTaskSchedulerCompletedJobs = mInternalTaskScheduler.getCompletedTaskCount();
        mControllerTaskScheduler = HealthConnectThreadScheduler.sControllerExecutor;
        mControllerTaskSchedulerCompletedJobs = mControllerTaskScheduler.getCompletedTaskCount();
        mForegroundTaskScheduler = HealthConnectThreadScheduler.sForegroundExecutor;
        mForegroundTaskSchedulerCompletedJobs = mForegroundTaskScheduler.getCompletedTaskCount();
        mBackgroundTaskScheduler = HealthConnectThreadScheduler.sBackgroundThreadExecutor;
        mBackgroundTaskSchedulerCompletedJobs = mBackgroundTaskScheduler.getCompletedTaskCount();
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testHealthConnectSchedulerScheduleInternal() throws Exception {
        HealthConnectThreadScheduler.scheduleInternalTask(() -> {});
        TestUtils.waitForTaskToFinishSuccessfully(
                () -> {
                    if (mInternalTaskScheduler.getCompletedTaskCount()
                            != mInternalTaskSchedulerCompletedJobs + 1) {
                        throw new RuntimeException();
                    }
                });
        HealthConnectThreadScheduler.scheduleControllerTask(() -> {});
        TestUtils.waitForTaskToFinishSuccessfully(
                () -> {
                    if (mControllerTaskScheduler.getCompletedTaskCount()
                            != mControllerTaskSchedulerCompletedJobs + 1) {
                        throw new RuntimeException();
                    }
                });
        HealthConnectThreadScheduler.schedule(mContext, () -> {}, Process.myUid(), false);
        TestUtils.waitForTaskToFinishSuccessfully(
                () -> {
                    if (mBackgroundTaskScheduler.getCompletedTaskCount()
                            != mBackgroundTaskSchedulerCompletedJobs + 1) {
                        throw new RuntimeException();
                    }
                });

        when(mMockContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        ActivityManager.RunningAppProcessInfo runningAppProcessInfo =
                new ActivityManager.RunningAppProcessInfo();
        runningAppProcessInfo.uid = Process.myUid();
        runningAppProcessInfo.importance =
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        when(mActivityManager.getRunningAppProcesses()).thenReturn(List.of(runningAppProcessInfo));

        HealthConnectThreadScheduler.schedule(mMockContext, () -> {}, Process.myUid(), false);
        TestUtils.waitForTaskToFinishSuccessfully(
                () -> {
                    if (mForegroundTaskScheduler.getCompletedTaskCount()
                            != mForegroundTaskSchedulerCompletedJobs + 1) {
                        throw new RuntimeException();
                    }
                });
    }

    @Test
    public void testHealthConnectScheduler_runningAppProcessNull() throws Exception {
        when(mMockContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(mActivityManager.getRunningAppProcesses()).thenReturn(null);

        HealthConnectThreadScheduler.scheduleInternalTask(() -> {});
        TestUtils.waitForTaskToFinishSuccessfully(
                () -> {
                    if (mInternalTaskScheduler.getCompletedTaskCount()
                            != mInternalTaskSchedulerCompletedJobs + 1) {
                        throw new RuntimeException();
                    }
                });
    }

    @Test
    public void testHealthConnectSchedulerClear() {
        Truth.assertThat(mInternalTaskSchedulerCompletedJobs).isEqualTo(0);
        Truth.assertThat(mControllerTaskSchedulerCompletedJobs).isEqualTo(0);
        Truth.assertThat(mForegroundTaskSchedulerCompletedJobs).isEqualTo(0);
        Truth.assertThat(mBackgroundTaskSchedulerCompletedJobs).isEqualTo(0);
    }

    @Test
    public void testScheduleAfterTheSchedulersAreShutdown_expectNoException() {
        HealthConnectThreadScheduler.shutdownThreadPools();

        HealthConnectThreadScheduler.schedule(mContext, () -> {}, Process.myUid(), false);
        HealthConnectThreadScheduler.schedule(mContext, () -> {}, Process.myUid(), true);
        HealthConnectThreadScheduler.scheduleInternalTask(() -> {});
        HealthConnectThreadScheduler.scheduleControllerTask(() -> {});
    }
}
