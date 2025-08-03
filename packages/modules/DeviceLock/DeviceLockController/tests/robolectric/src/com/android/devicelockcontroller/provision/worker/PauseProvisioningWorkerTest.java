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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PauseProvisioningWorkerTest {
    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private DeviceCheckInClient mClient;
    @Mock
    private PauseDeviceProvisioningGrpcResponse mResponse;
    private StatsLogger mStatsLogger;
    private PauseProvisioningWorker mWorker;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        when(mClient.pauseDeviceProvisioning(anyInt())).thenReturn(mResponse);
        mWorker = TestListenableWorkerBuilder.from(
                        mTestApp, PauseProvisioningWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        PauseProvisioningWorker.class.getName())
                                        ? new PauseProvisioningWorker(context,
                                        workerParameters, mClient,
                                        TestingExecutors.sameThreadScheduledExecutor())
                                        : null;
                            }
                        }).build();
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) mTestApp.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
    }

    @Test
    public void doWork_responseIsSuccessful_resultSuccessAndLogged() {
        when(mResponse.isSuccessful()).thenReturn(true);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());
        // THEN pause device provisioning was logged
        verify(mStatsLogger).logPauseDeviceProvisioning();
    }

    @Test
    public void doWork_responseIsNotSuccessful_resultFailure() {
        when(mResponse.isSuccessful()).thenReturn(false);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.failure());
        // THEN pause device provisioning was NOT logged
        verify(mStatsLogger, never()).logPauseDeviceProvisioning();
    }
}
