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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker.RETRY_ON_FAILURE_DELAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.receivers.CheckInBootCompletedReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
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
public class DeviceCheckInWorkerTest {
    public static final ArraySet<DeviceId> TEST_DEVICE_IDS = new ArraySet<>(
            new DeviceId[]{new DeviceId(DEVICE_ID_TYPE_IMEI, "1234667890")});
    public static final ArraySet<DeviceId> EMPTY_DEVICE_IDS = new ArraySet<>(new DeviceId[]{});
    public static final String TEST_CARRIER_INFO = "1234567890";
    public static final String EMPTY_CARRIER_INFO = "";
    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private AbstractDeviceCheckInHelper mHelper;
    @Mock
    private DeviceCheckInClient mClient;
    @Mock
    private GetDeviceCheckInStatusGrpcResponse mResponse;
    private StatsLogger mStatsLogger;
    private DeviceCheckInWorker mWorker;
    private Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        when(mClient.getDeviceCheckInStatus(
                eq(TEST_DEVICE_IDS), anyString(), isNull())).thenReturn(mResponse);
        mWorker = TestListenableWorkerBuilder.from(
                        mContext, DeviceCheckInWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(DeviceCheckInWorker.class.getName())
                                        ? new DeviceCheckInWorker(
                                        context, workerParameters, mHelper, mClient,
                                        TestingExecutors.sameThreadScheduledExecutor())
                                        : null;
                            }
                        }).build();
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) mContext.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
    }


    @Test
    public void checkIn_allInfoAvailable_checkInResponseSuccessfulAndHandleable_successAndLogged() {
        // GIVEN all device info available
        setDeviceIdAvailability(/* isAvailable= */ true);
        setCarrierInfoAvailability(/* isAvailable= */ true);

        // GIVEN check-in response is successful
        setUpSuccessfulCheckInResponse(/* isHandleable= */ true);

        // WHEN work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN work succeeded
        assertThat(result).isEqualTo(Result.success());
        // THEN check in request was logged
        verify(mStatsLogger).logGetDeviceCheckInStatus();
        verify(mStatsLogger).logSuccessfulCheckIn();
    }

    @Test
    public void checkIn_allInfoAvailable_checkInResponseSuccessfulNotHandleable_retryAndLogged() {
        // GIVEN all device info available
        setDeviceIdAvailability(/* isAvailable= */ true);
        setCarrierInfoAvailability(/* isAvailable= */ true);

        // GIVEN check-in response is successful
        setUpSuccessfulCheckInResponse(/* isHandleable= */ false);

        // WHEN work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN work succeeded
        assertThat(result).isEqualTo(Result.retry());
        // THEN check in request was logged, but successful check in is NOT
        verify(mStatsLogger).logGetDeviceCheckInStatus();
        verify(mStatsLogger, never()).logSuccessfulCheckIn();
    }

    @Test
    public void checkIn_allInfoAvailable_checkInResponseHasRecoverableError_retryAndLogCheckIn() {
        // GIVEN all device info available
        setDeviceIdAvailability(/* isAvailable= */ true);
        setCarrierInfoAvailability(/* isAvailable= */ true);

        // GIVEN check-in response has recoverable failure.
        setUpFailedCheckInResponse(/* isRecoverable= */ true);

        // WHEN work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN work succeeded
        assertThat(result).isEqualTo(Result.retry());
        // THEN attempt of check in request WAS logged, but the successful check in was NOT logged.
        verify(mStatsLogger).logGetDeviceCheckInStatus();
        verify(mStatsLogger, never()).logSuccessfulCheckIn();
    }

    @Test
    public void checkIn_allInfoAvailable_checkInResponseHasNonRecoverableError_failAndLogCheckIn() {
        // GIVEN all device info available
        setDeviceIdAvailability(/* isAvailable= */ true);
        setCarrierInfoAvailability(/* isAvailable= */ true);

        // GIVEN check-in response has non-recoverable failure.
        setUpFailedCheckInResponse(/* isRecoverable= */ false);

        // WHEN work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN work succeeded
        assertThat(result).isEqualTo(Result.failure());

        // THEN scheduled retry work
        DeviceLockControllerScheduler scheduler =
                ((TestDeviceLockControllerApplication) ApplicationProvider.getApplicationContext())
                        .getDeviceLockControllerScheduler();
        verify(scheduler).scheduleRetryCheckInWork(eq(RETRY_ON_FAILURE_DELAY));
        // THEN attempt of check in request WAS logged, but the successful check in was NOT logged.
        verify(mStatsLogger).logGetDeviceCheckInStatus();
        verify(mStatsLogger, never()).logSuccessfulCheckIn();
    }

    @Test
    public void checkIn_carrierInfoUnavailable_shouldAtLeastSendCheckInRequest() {
        // GIVEN only device ids available
        setDeviceIdAvailability(/* isAvailable= */ true);
        setCarrierInfoAvailability(/* isAvailable= */ false);

        // WHEN work runs
        Futures.getUnchecked(mWorker.startWork());

        // THEN check-in is requested
        verify(mClient).getDeviceCheckInStatus(eq(TEST_DEVICE_IDS), eq(EMPTY_CARRIER_INFO),
                isNull());
    }

    @Test
    public void checkIn_deviceIdsUnavailable_shouldNotSendCheckInRequest() {
        // GIVEN only device ids available
        setDeviceIdAvailability(/* isAvailable= */ false);
        setCarrierInfoAvailability(/* isAvailable= */ false);

        // WHEN work runs
        Futures.getUnchecked(mWorker.startWork());

        // THEN check-in is not requested
        verify(mClient, never()).getDeviceCheckInStatus(eq(TEST_DEVICE_IDS), eq(EMPTY_CARRIER_INFO),
                isNull());

        // THEN CheckInBootCompletedReceiver should be disabled
        assertThat(mContext.getPackageManager()
                .getComponentEnabledSetting(new ComponentName(mContext,
                        CheckInBootCompletedReceiver.class)))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

    }

    private void setDeviceIdAvailability(boolean isAvailable) {
        when(mHelper.getDeviceUniqueIds()).thenReturn(
                isAvailable ? TEST_DEVICE_IDS : EMPTY_DEVICE_IDS);
    }

    private void setCarrierInfoAvailability(boolean isAvailable) {
        when(mHelper.getCarrierInfo()).thenReturn(
                isAvailable ? TEST_CARRIER_INFO : EMPTY_CARRIER_INFO);
    }

    private void setUpSuccessfulCheckInResponse(boolean isHandleable) {
        when(mResponse.hasRecoverableError()).thenReturn(false);
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mHelper.handleGetDeviceCheckInStatusResponse(eq(mResponse), any())).thenReturn(
                isHandleable);
    }

    private void setUpFailedCheckInResponse(boolean isRecoverable) {
        when(mResponse.hasRecoverableError()).thenReturn(isRecoverable);
        when(mResponse.isSuccessful()).thenReturn(false);
    }
}
