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

package com.android.devicelockcontroller.provision.worker;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.testing.TestingExecutors;

import io.grpc.Status;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class ReportDeviceLockProgramCompleteWorkerTest {
    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private DeviceFinalizeClient mClient;
    @Mock
    private PolicyObjectsProvider mPolicyObjectsProvider;
    @Mock
    private FinalizationController mFinalizationController;
    private ReportDeviceLockProgramCompleteWorker mWorker;

    @Before
    public void setUp() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();
        when(mPolicyObjectsProvider.getFinalizationController())
                .thenReturn(mFinalizationController);
        when(mFinalizationController.notifyFinalizationReportResult(any()))
                .thenReturn(Futures.immediateVoidFuture());
        mWorker = TestListenableWorkerBuilder.from(
                        context, ReportDeviceLockProgramCompleteWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        ReportDeviceLockProgramCompleteWorker.class.getName())
                                        ? new ReportDeviceLockProgramCompleteWorker(context,
                                        workerParameters, mClient, mPolicyObjectsProvider,
                                        TestingExecutors.sameThreadScheduledExecutor())
                                        : null;
                            }
                        }).build();
    }

    @Test
    public void doWork_responseIsSuccessful_returnSuccess() {
        when(mClient.reportDeviceProgramComplete()).thenReturn(
                new DeviceFinalizeClient.ReportDeviceProgramCompleteResponse());

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());
    }

    @Test
    public void doWork_responseHasRecoverableError_returnRetry() {
        when(mClient.reportDeviceProgramComplete()).thenReturn(
                new DeviceFinalizeClient.ReportDeviceProgramCompleteResponse(Status.UNAVAILABLE));

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.retry());
    }

    @Test
    public void doWork_responseHasFatalError_returnFailure() {
        when(mClient.reportDeviceProgramComplete()).thenReturn(
                new DeviceFinalizeClient.ReportDeviceProgramCompleteResponse(Status.UNIMPLEMENTED));

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.failure());
    }
}
