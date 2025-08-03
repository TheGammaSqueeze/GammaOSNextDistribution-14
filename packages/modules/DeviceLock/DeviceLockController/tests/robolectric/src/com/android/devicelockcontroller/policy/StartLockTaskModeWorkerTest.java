/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.devicelockcontroller.policy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Executors;

/**
 * Tests for {@link StartLockTaskModeWorker}
 */
@RunWith(RobolectricTestRunner.class)
public final class StartLockTaskModeWorkerTest {

    private static final String PACKAGE_NAME = "test.package";
    private static final String COMPONENT_CLASS_NAME = "TestActivity";

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    private final ListeningExecutorService mBgExecutor =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    // Mock of DevicePolicyManager. Cannot use shadow as shadow requires a non-null admin even
    // though the real implementation allows nullable admin
    @Mock
    private DevicePolicyManager mDpm;
    // Mock of ActivityManager. Cannot use shadow as we need to modify behavior before and after
    // activity start since the shadow context does not change the lock task state
    @Mock
    private ActivityManager mAm;
    private TestDeviceLockControllerApplication mTestApp;
    private DevicePolicyController mDevicePolicyController;
    private Intent mLockTaskIntent;
    private StartLockTaskModeWorker mWorker;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        mWorker = TestListenableWorkerBuilder.from(mTestApp, StartLockTaskModeWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        StartLockTaskModeWorker.class.getName())
                                        ? new StartLockTaskModeWorker(mTestApp,
                                                mDpm,
                                                mAm,
                                                workerParameters,
                                                TestingExecutors.sameThreadScheduledExecutor())
                                        : null /* worker */;
                            }
                        }
                ).build();

        shadowOf(mTestApp.getSystemService(ActivityManager.class)).setLockTaskModeState(
                ActivityManager.LOCK_TASK_MODE_NONE);
        mLockTaskIntent = new Intent()
                .setComponent(new ComponentName(PACKAGE_NAME, COMPONENT_CLASS_NAME));
        mDevicePolicyController = mTestApp.getPolicyController();
        when(mDevicePolicyController.getLaunchIntentForCurrentState()).thenReturn(
                Futures.immediateFuture(mLockTaskIntent));
        when(mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure()).thenReturn(
                Futures.immediateVoidFuture());
        when(mDpm.isLockTaskPermitted(PACKAGE_NAME)).thenReturn(true);
    }

    @Test
    public void doWork_launchesActivityInLockedTaskMode() throws Exception {
        // GIVEN device is not locked and then locked after worker finishes
        when(mAm.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE)
                .thenReturn(ActivityManager.LOCK_TASK_MODE_LOCKED);

        // WHEN the work finishes
        final Result result = mBgExecutor.submit(() -> mWorker.startWork().get()).get();

        // THEN the launched intent starts with the lock task flag
        assertThat(result).isEqualTo(Result.success());
        Intent launchedIntent = shadowOf(mTestApp).getNextStartedActivity();
        assertThat(launchedIntent.getPackage()).isEqualTo(mLockTaskIntent.getPackage());
        assertThat(launchedIntent.getComponent()).isEqualTo(mLockTaskIntent.getComponent());
        assertThat(launchedIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0);
        assertThat(launchedIntent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK).isNotEqualTo(0);
        verify(mDpm).addPersistentPreferredActivity(any(), any(),
                eq(launchedIntent.getComponent()));
    }

    @Test
    public void doWork_hitsException_failsAndEnforcesCriticalFailure() throws Exception {
        // GIVEN device is not locked and getting intent throws exception
        when(mAm.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);
        when(mDevicePolicyController.getLaunchIntentForCurrentState()).thenThrow(
                new RuntimeException());

        // WHEN the work finishes
        final Result result = mBgExecutor.submit(() -> mWorker.startWork().get()).get();

        // THEN we enforce a critical failure
        assertThat(result).isEqualTo(Result.failure());
        verify(mDevicePolicyController).enforceCurrentPoliciesForCriticalFailure();
    }

    @Test
    public void doWork_alreadyLockedDoesNothing() throws Exception {
        // GIVEN device is already locked
        when(mAm.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_LOCKED);

        // WHEN the work finishes
        final Result result = mBgExecutor.submit(() -> mWorker.startWork().get()).get();

        // THEN the work succeeds and there is no launched intent
        assertThat(result).isEqualTo(Result.success());
        Intent launchedIntent = shadowOf(mTestApp).getNextStartedActivity();
        assertThat(launchedIntent).isNull();
    }

    @Test
    public void doWork_failsWhenNoLaunchIntent() throws Exception {
        // GIVEN device is not locked and launch intent is null
        when(mAm.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);
        when(mDevicePolicyController.getLaunchIntentForCurrentState()).thenReturn(null);

        // WHEN the work finishes
        final Result result = mBgExecutor.submit(() -> mWorker.startWork().get()).get();

        // THEN the work fails
        assertThat(result).isEqualTo(Result.failure());
    }

    @Test
    public void doWork_failsLaunchIntentNotPermitted() throws Exception {
        // GIVEN device is not locked and launch intent package is not permitted
        when(mAm.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);
        when(mDpm.isLockTaskPermitted(PACKAGE_NAME)).thenReturn(false);

        // WHEN the work finishes
        final Result result = mBgExecutor.submit(() -> mWorker.startWork().get()).get();

        // THEN the work fails
        assertThat(result).isEqualTo(Result.failure());
    }

    @Test
    public void doWork_retriesIfNotInLockTaskMode() throws Exception {
        // GIVEN device is not locked and still not locked after worker finishes
        when(mAm.getLockTaskModeState())
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE)
                .thenReturn(ActivityManager.LOCK_TASK_MODE_NONE);

        // WHEN the work finishes
        final Result result = mBgExecutor.submit(() -> mWorker.startWork().get()).get();

        // THEN the work retries
        assertThat(result).isEqualTo(Result.retry());
    }
}
