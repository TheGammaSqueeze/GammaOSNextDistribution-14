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

import static com.android.devicelockcontroller.activities.DeviceLockNotificationManager.DEVICE_RESET_NOTIFICATION_ID;
import static com.android.devicelockcontroller.activities.DeviceLockNotificationManager.DEVICE_RESET_NOTIFICATION_TAG;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_FACTORY_RESET;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_RETRY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class ReportDeviceProvisionStateWorkerTest {
    private static final int TEST_DAYS_LEFT_UNTIL_RESET = 3;
    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private DeviceCheckInClient mClient;
    @Mock
    private ReportDeviceProvisionStateGrpcResponse mResponse;
    private StatsLogger mStatsLogger;
    private ReportDeviceProvisionStateWorker mWorker;
    private TestDeviceLockControllerApplication mTestApp;
    private ListeningExecutorService mExecutorService =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp,
                new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .setExecutor(new SynchronousExecutor())
                        .build());

        when(mClient.reportDeviceProvisionState(anyInt(), anyBoolean(), anyInt())).thenReturn(
                mResponse);
        mWorker = TestListenableWorkerBuilder.from(
                        mTestApp, ReportDeviceProvisionStateWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        ReportDeviceProvisionStateWorker.class.getName())
                                        ? new ReportDeviceProvisionStateWorker(context,
                                        workerParameters, mClient,
                                        MoreExecutors.listeningDecorator(
                                                Executors.newSingleThreadExecutor()))
                                        : null;
                            }
                        }).build();
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) mTestApp.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
        SetupParametersClient.getInstance(mTestApp, mExecutorService);
    }

    @Test
    public void doWork_responseHasRecoverableError_returnRetryAndNotLogged() {
        when(mResponse.hasRecoverableError()).thenReturn(true);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.retry());
        // THEN report provisioning state or complete was NOT logged
        verify(mStatsLogger, never()).logReportDeviceProvisionState();
    }

    @Test
    public void doWork_responseHasFatalError_returnFailureAndNotLogged() {
        when(mResponse.hasFatalError()).thenReturn(true);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.failure());
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleNextProvisionFailedStepAlarm();
        // THEN report provisioning state or complete was NOT logged
        verify(mStatsLogger, never()).logReportDeviceProvisionState();
    }

    @Test
    public void doWork_responseIsSuccessful_globalParametersSetAndEventLogged() throws Exception {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_RETRY);
        when(mResponse.getDaysLeftUntilReset()).thenReturn(TEST_DAYS_LEFT_UNTIL_RESET);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());

        // THEN parameters are saved and report state was logged
        GlobalParametersClient globalParameters = GlobalParametersClient.getInstance();
        assertThat(globalParameters.getLastReceivedProvisionState().get()).isEqualTo(
                PROVISION_STATE_RETRY);
        Executors.newSingleThreadExecutor().submit(
                () -> assertThat(UserParameters.getDaysLeftUntilReset(mTestApp)).isEqualTo(
                        TEST_DAYS_LEFT_UNTIL_RESET)).get();
        verify(mStatsLogger).logReportDeviceProvisionState();
    }

    @Test
    public void doWork_retryState_schedulesAlarm() {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_RETRY);
        when(mResponse.getDaysLeftUntilReset()).thenReturn(TEST_DAYS_LEFT_UNTIL_RESET);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());

        // THEN we schedule to try again later
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleNextProvisionFailedStepAlarm();
    }

    @Test
    @Ignore
    //TODO(b/327652632): Re-enable after fixing
    public void doWork_dismissibleUiState_schedulesAlarmAndSendsNotification() throws Exception {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_DISMISSIBLE_UI);
        when(mResponse.getDaysLeftUntilReset()).thenReturn(TEST_DAYS_LEFT_UNTIL_RESET);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());

        CountDownLatch latch = new CountDownLatch(1);
        Futures.getUnchecked(mExecutorService.submit(latch::countDown));
        latch.await();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // THEN we schedule to try again later and send a notification to the user
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleNextProvisionFailedStepAlarm();

        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(
                mTestApp.getSystemService(NotificationManager.class));
        StatusBarNotification[] activeNotifs = shadowNotificationManager.getActiveNotifications();
        assertThat(activeNotifs.length).isGreaterThan(0);
        StatusBarNotification notif = activeNotifs[0];
        assertThat(notif.getTag()).isEqualTo(DEVICE_RESET_NOTIFICATION_TAG);
        assertThat(notif.getId()).isEqualTo(DEVICE_RESET_NOTIFICATION_ID);
        assertThat(notif.isOngoing()).isFalse();
    }

    @Test
    @Ignore
    //TODO(b/327652632): Re-enable after fixing
    public void doWork_persistentUiState_schedulesAlarmAndSendsOngoingNotification()
            throws Exception {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_PERSISTENT_UI);
        when(mResponse.getDaysLeftUntilReset()).thenReturn(TEST_DAYS_LEFT_UNTIL_RESET);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());

        CountDownLatch latch = new CountDownLatch(1);
        Futures.getUnchecked(mExecutorService.submit(latch::countDown));
        latch.await();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // THEN we schedule to try again later and send an undismissable notification to the user
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleNextProvisionFailedStepAlarm();

        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(
                mTestApp.getSystemService(NotificationManager.class));
        StatusBarNotification[] activeNotifs = shadowNotificationManager.getActiveNotifications();
        assertThat(activeNotifs.length).isGreaterThan(0);
        StatusBarNotification notif = activeNotifs[0];
        assertThat(notif.getTag()).isEqualTo(DEVICE_RESET_NOTIFICATION_TAG);
        assertThat(notif.getId()).isEqualTo(DEVICE_RESET_NOTIFICATION_ID);
        assertThat(notif.isOngoing()).isTrue();
    }

    @Test
    public void doWork_factoryResetState_schedulesResetDeviceAlarm()
            throws Exception {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_FACTORY_RESET);
        when(mResponse.getDaysLeftUntilReset()).thenReturn(TEST_DAYS_LEFT_UNTIL_RESET);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());

        CountDownLatch latch = new CountDownLatch(1);
        Futures.getUnchecked(mExecutorService.submit(latch::countDown));
        latch.await();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // THEN we schedule reset.
        // Note that the scheduler class sends the notification
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleResetDeviceAlarm();
    }
}
