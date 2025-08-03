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

package com.android.devicelockcontroller.schedule;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;
import static com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerImpl.DEVICE_CHECK_IN_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceLockConstants;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.ThreadUtils;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public final class DeviceLockControllerSchedulerImplTest {
    private static final long PROVISION_PAUSED_MILLIS = TimeUnit.MINUTES.toMillis(
            DeviceLockControllerSchedulerImpl.PROVISION_PAUSED_MINUTES_DEFAULT);
    private static final Duration TEST_RETRY_CHECK_IN_DELAY = Duration.ofDays(30);
    private static final long TEST_NEXT_CHECK_IN_TIME_MILLIS = Duration.ofHours(10).toMillis();
    private static final long TEST_RESUME_PROVISION_TIME_MILLIS = Duration.ofHours(20).toMillis();
    private static final long TEST_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS = Duration.ofHours(
            2).toMillis();
    private static final long TEST_RESET_DEVICE_TIME_MILLIS = Duration.ofHours(
            50).toMillis();

    private static final long PROVISION_STATE_REPORT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(
            DeviceLockControllerSchedulerImpl.PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES);
    private static final long TEST_CURRENT_TIME_MILLIS = Duration.ofHours(5).toMillis();
    private static final Clock TEST_CLOCK = Clock.fixed(
            Instant.ofEpochMilli(TEST_CURRENT_TIME_MILLIS),
            ZoneOffset.UTC);
    public static final long TEST_POSITIVE_DELTA_MILLIS = Duration.ofHours(5).toMillis();
    public static final long TEST_NEGATIVE_DELTA_MILLIS = Duration.ofHours(-5).toMillis();
    private static final long RESET_DEVICE_MILLIS = TimeUnit.MINUTES.toMillis(
            DeviceLockConstants.NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE);
    DeviceLockControllerSchedulerImpl mScheduler;
    TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        mScheduler = new DeviceLockControllerSchedulerImpl(mTestApp, TEST_CLOCK,
                mTestApp.getProvisionStateController());
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp, config);
    }

    @Test
    public void correctExpectedToRunTime_retryCheckInExpected_positiveDelta_shouldUpdate() {
        // GIVEN retry check in is expected
        UserParameters.setNextCheckInTimeMillis(mTestApp, TEST_NEXT_CHECK_IN_TIME_MILLIS);

        // GIVEN time change delta is positive.
        UserParameters.setBootTimeMillis(mTestApp,
                TEST_CURRENT_TIME_MILLIS - TEST_POSITIVE_DELTA_MILLIS
                        - SystemClock.elapsedRealtime());


        runBySequentialExecutor(() -> {
            // WHEN correct expected to run time for UNPROVISIONED state
            mScheduler.correctStoredTime(UNPROVISIONED);

            // THEN next check in time should be updated
            long expectedToRunAfterChange =
                    TEST_NEXT_CHECK_IN_TIME_MILLIS + TEST_POSITIVE_DELTA_MILLIS;
            assertThat(UserParameters.getNextCheckInTimeMillis(mTestApp)).isEqualTo(
                    expectedToRunAfterChange);
        });
    }


    @Test
    public void correctExpectedToRunTime_retryCheckInExpected_negativeDelta_shouldUpdate() {
        // GIVEN retry check in is expected
        UserParameters.setNextCheckInTimeMillis(mTestApp, TEST_NEXT_CHECK_IN_TIME_MILLIS);

        // GIVEN time change delta is negative.
        UserParameters.setBootTimeMillis(mTestApp,
                TEST_CURRENT_TIME_MILLIS - TEST_NEGATIVE_DELTA_MILLIS
                        - SystemClock.elapsedRealtime());

        runBySequentialExecutor(() -> {
            // WHEN correct expected to run time for UNPROVISIONED state
            mScheduler.correctStoredTime(UNPROVISIONED);

            // THEN next check in time should be updated
            long expectedToRunAfterChange =
                    TEST_NEXT_CHECK_IN_TIME_MILLIS + TEST_NEGATIVE_DELTA_MILLIS;
            assertThat(UserParameters.getNextCheckInTimeMillis(mTestApp)).isEqualTo(
                    expectedToRunAfterChange);
        });
    }

    @Test
    public void correctExpectedToRunTime_resumeProvisionExpected_positiveDelta_shouldUpdate() {
        // GIVEN retry check in is expected
        UserParameters.setResumeProvisionTimeMillis(mTestApp, TEST_RESUME_PROVISION_TIME_MILLIS);

        // GIVEN time change delta is positive.
        UserParameters.setBootTimeMillis(mTestApp,
                TEST_CURRENT_TIME_MILLIS - TEST_POSITIVE_DELTA_MILLIS
                        - SystemClock.elapsedRealtime());

        runBySequentialExecutor(() -> {
            // WHEN correct expected to run time for PROVISION_PAUSED state
            mScheduler.correctStoredTime(PROVISION_PAUSED);

            // THEN next check in time should be updated
            long expectedToRunAfterChange =
                    TEST_RESUME_PROVISION_TIME_MILLIS + TEST_POSITIVE_DELTA_MILLIS;
            assertThat(UserParameters.getResumeProvisionTimeMillis(mTestApp)).isEqualTo(
                    expectedToRunAfterChange);
        });
    }

    @Test
    public void correctExpectedToRunTime_resumeProvisionExpected_negativeDelta_shouldUpdate() {
        // GIVEN retry check in is expected
        UserParameters.setResumeProvisionTimeMillis(mTestApp, TEST_RESUME_PROVISION_TIME_MILLIS);

        // GIVEN time change delta is negative.
        UserParameters.setBootTimeMillis(mTestApp,
                TEST_CURRENT_TIME_MILLIS - TEST_NEGATIVE_DELTA_MILLIS
                        - SystemClock.elapsedRealtime());

        runBySequentialExecutor(() -> {
            // WHEN correct expected to run time for PROVISION_PAUSED state
            mScheduler.correctStoredTime(PROVISION_PAUSED);

            // THEN next check in time should be updated
            long expectedToRunAfterChange =
                    TEST_RESUME_PROVISION_TIME_MILLIS + TEST_NEGATIVE_DELTA_MILLIS;
            assertThat(UserParameters.getResumeProvisionTimeMillis(mTestApp)).isEqualTo(
                    expectedToRunAfterChange);
        });
    }

    @Test
    public void scheduleResumeProvisionAlarm() {
        // GIVEN no alarm is scheduled and no expected resume time
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();
        runBySequentialExecutor(() ->
                assertThat(UserParameters.getResumeProvisionTimeMillis(mTestApp)).isEqualTo(0));

        // WHEN resume provision alarm is scheduled
        mScheduler.scheduleResumeProvisionAlarm();

        // THEN correct alarm should be scheduled
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();

        // THEN alarm should be scheduled at correct time
        long actualTriggerTime = alarmManager.peekNextScheduledAlarm().triggerAtTime;
        assertThat(actualTriggerTime).isEqualTo(
                SystemClock.elapsedRealtime() + PROVISION_PAUSED_MILLIS);

        // THEN expected trigger time should be stored in storage
        runBySequentialExecutor(() -> {
            assertThat(UserParameters.getResumeProvisionTimeMillis(mTestApp)).isEqualTo(
                    TEST_CURRENT_TIME_MILLIS + PROVISION_PAUSED_MILLIS);
        });
    }

    @Test
    public void rescheduleResumeProvisionAlarm() {
        // GIVEN no alarm is scheduled
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();

        // GIVEN expected resume time in storage
        UserParameters.setResumeProvisionTimeMillis(mTestApp, TEST_RESUME_PROVISION_TIME_MILLIS);

        // WHEN resume provision alarm is rescheduled
        runBySequentialExecutor(mScheduler::rescheduleResumeProvisionAlarmIfNeeded);

        // THEN correct alarm should be scheduled at correct time
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();


        long expectedTriggerTime = SystemClock.elapsedRealtime()
                + (TEST_RESUME_PROVISION_TIME_MILLIS - TEST_CURRENT_TIME_MILLIS);
        assertThat(alarmManager.peekNextScheduledAlarm().triggerAtTime).isEqualTo(
                expectedTriggerTime);
    }

    @Test
    public void scheduleInitialCheckInWork() throws Exception {
        // GIVEN check-in work is not scheduled
        WorkManager workManager = WorkManager.getInstance(mTestApp);
        assertThat(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME).get()).isEmpty();

        // WHEN schedule initial check-in work
        mScheduler.scheduleInitialCheckInWork();

        // THEN check-in work should be scheduled
        List<WorkInfo> actualWorks = Futures.getUnchecked(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME));
        assertThat(actualWorks.size()).isEqualTo(1);
        WorkInfo actualWorkInfo = actualWorks.get(0);
        assertThat(actualWorkInfo.getConstraints().getRequiredNetworkType()).isEqualTo(
                NetworkType.CONNECTED);
        assertThat(actualWorkInfo.getInitialDelayMillis()).isEqualTo(0);
    }

    @Test
    public void scheduleRetryCheckInWork() throws Exception {
        // GIVEN check-in work is not scheduled
        WorkManager workManager = WorkManager.getInstance(mTestApp);
        assertThat(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME).get()).isEmpty();

        // WHEN schedule retry check-in work
        mScheduler.scheduleRetryCheckInWork(TEST_RETRY_CHECK_IN_DELAY);

        // THEN retry check-in work should be scheduled
        List<WorkInfo> actualWorks = Futures.getUnchecked(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME));
        assertThat(actualWorks.size()).isEqualTo(1);
        WorkInfo actualWorkInfo = actualWorks.get(0);
        assertThat(actualWorkInfo.getConstraints().getRequiredNetworkType()).isEqualTo(
                NetworkType.CONNECTED);
        assertThat(actualWorkInfo.getInitialDelayMillis()).isEqualTo(
                TEST_RETRY_CHECK_IN_DELAY.toMillis());

        // THEN expected trigger time is stored in storage
        long expectedTriggerTime =
                TEST_CURRENT_TIME_MILLIS + TEST_RETRY_CHECK_IN_DELAY.toMillis();
        runBySequentialExecutor(
                () -> assertThat(UserParameters.getNextCheckInTimeMillis(mTestApp)).isEqualTo(
                        expectedTriggerTime));
    }

    @Test
    public void rescheduleRetryCheckInWork() {
        // GIVEN check-in work is scheduled with original delay
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(DeviceCheckInWorker.class)
                        .setInitialDelay(TEST_RETRY_CHECK_IN_DELAY).build();
        WorkManager workManager = WorkManager.getInstance(mTestApp);
        workManager.enqueueUniqueWork(DEVICE_CHECK_IN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request);

        // GIVEN expected trigger time
        UserParameters.setNextCheckInTimeMillis(mTestApp, TEST_NEXT_CHECK_IN_TIME_MILLIS);

        // WHEN reschedule retry check-in work
        runBySequentialExecutor(mScheduler::rescheduleRetryCheckInWork);

        // THEN retry check-in work should be scheduled with correct delay
        List<WorkInfo> actualWorks = Futures.getUnchecked(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME));
        assertThat(actualWorks.size()).isEqualTo(1);
        WorkInfo actualWorkInfo = actualWorks.get(0);
        assertThat(actualWorkInfo.getConstraints().getRequiredNetworkType()).isEqualTo(
                NetworkType.CONNECTED);

        long expectedDelay = TEST_NEXT_CHECK_IN_TIME_MILLIS - TEST_CURRENT_TIME_MILLIS;
        assertThat(actualWorkInfo.getInitialDelayMillis()).isEqualTo(expectedDelay);
    }

    @Test
    public void maybeScheduleInitialCheckIn_needInitialCheckIn_enqueuesWorker() throws Exception {
        // Make sure the initial state is "initial check in needed"
        runBySequentialExecutor(
                () -> assertThat(UserParameters.needInitialCheckIn(mTestApp)).isTrue());

        // GIVEN check-in work is not scheduled
        WorkManager workManager = WorkManager.getInstance(mTestApp);
        assertThat(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME).get()).isEmpty();

        // WHEN maybe schedule initial check-in work
        mScheduler.maybeScheduleInitialCheckIn().get();

        // THEN check-in work should be scheduled
        assertThat(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME).get()).isNotEmpty();
    }

    @Test
    public void maybeScheduleInitialCheckIn_NoNeedInitialCheckIn_noWorker() throws Exception {
        // GIVEN initial check in marked as scheduled
        UserParameters.initialCheckInScheduled(mTestApp);

        // GIVEN check-in work is not scheduled
        WorkManager workManager = WorkManager.getInstance(mTestApp);
        assertThat(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME).get()).isEmpty();

        // WHEN maybe schedule initial check-in work
        mScheduler.maybeScheduleInitialCheckIn().get();

        // THEN check-in work should not be scheduled
        assertThat(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME).get()).isEmpty();
    }

    @Test
    public void maybeScheduleInitialCheckIn_noNeedCheckIn_reschedule() throws Exception {
        // GIVEN check-in work is scheduled with original delay
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(DeviceCheckInWorker.class)
                        .setInitialDelay(TEST_RETRY_CHECK_IN_DELAY).build();
        WorkManager workManager = WorkManager.getInstance(mTestApp);
        workManager.enqueueUniqueWork(DEVICE_CHECK_IN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request);

        // GIVEN initial check in marked as scheduled
        UserParameters.initialCheckInScheduled(mTestApp);

        // GIVEN expected trigger time
        UserParameters.setNextCheckInTimeMillis(mTestApp, TEST_NEXT_CHECK_IN_TIME_MILLIS);

        // WHEN maybe schedule initial check-in work
        mScheduler.maybeScheduleInitialCheckIn().get();

        // THEN retry check-in work should be scheduled with correct delay
        List<WorkInfo> actualWorks = Futures.getUnchecked(workManager.getWorkInfosForUniqueWork(
                DEVICE_CHECK_IN_WORK_NAME));
        assertThat(actualWorks.size()).isEqualTo(1);
        WorkInfo actualWorkInfo = actualWorks.get(0);
        assertThat(actualWorkInfo.getConstraints().getRequiredNetworkType()).isEqualTo(
                NetworkType.CONNECTED);

        long expectedDelay = TEST_NEXT_CHECK_IN_TIME_MILLIS - TEST_CURRENT_TIME_MILLIS;
        assertThat(actualWorkInfo.getInitialDelayMillis()).isEqualTo(expectedDelay);
    }

    @Test
    public void scheduleNextProvisionFailedStepAlarm_initialStep_setsAlarm() {
        // GIVEN no alarm is scheduled
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();

        // GIVEN no existing timestamp
        runBySequentialExecutor(() -> assertThat(
                UserParameters.getNextProvisionFailedStepTimeMills(mTestApp)).isEqualTo(0));

        // WHEN schedule next provision failed step alarm
        runBySequentialExecutor(() -> mScheduler.scheduleNextProvisionFailedStepAlarm());

        // THEN correct alarm should be scheduled
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();

        // THEN alarm should be scheduled at correct time
        long actualTriggerTime = alarmManager.peekNextScheduledAlarm().triggerAtTime;
        assertThat(actualTriggerTime).isEqualTo(
                SystemClock.elapsedRealtime() + PROVISION_STATE_REPORT_INTERVAL_MILLIS);

        // THEN expected trigger time should be stored in storage
        runBySequentialExecutor(() -> assertThat(
                UserParameters.getNextProvisionFailedStepTimeMills(mTestApp)).isEqualTo(
                TEST_CURRENT_TIME_MILLIS + PROVISION_STATE_REPORT_INTERVAL_MILLIS));
    }

    @Test
    public void scheduleNextProvisionFailedStepAlarm_followUpStep_setsAlarm() {
        // GIVEN no alarm is scheduled
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();

        // GIVEN timestamp exists for last performed step.
        UserParameters.setNextProvisionFailedStepTimeMills(mTestApp,
                TEST_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS);

        // WHEN schedule next provision failed step alarm
        runBySequentialExecutor(() -> mScheduler.scheduleNextProvisionFailedStepAlarm());

        // THEN correct alarm should be scheduled
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();

        // THEN alarm should be scheduled at correct time
        long actualTriggerTime = alarmManager.peekNextScheduledAlarm().triggerAtTime;
        long expectedTriggerTime = TEST_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS
                + PROVISION_STATE_REPORT_INTERVAL_MILLIS - TEST_CURRENT_TIME_MILLIS
                + SystemClock.elapsedRealtime();
        assertThat(actualTriggerTime).isEqualTo(expectedTriggerTime);


        // THEN expected trigger time should be stored in storage
        runBySequentialExecutor(() -> assertThat(
                UserParameters.getNextProvisionFailedStepTimeMills(mTestApp)).isEqualTo(
                TEST_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS
                        + PROVISION_STATE_REPORT_INTERVAL_MILLIS));
    }

    @Test
    public void scheduleResetDeviceAlarm() {
        // GIVEN no alarm is scheduled
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();
        runBySequentialExecutor(
                () -> assertThat(UserParameters.getResetDeviceTimeMillis(mTestApp)).isEqualTo(0));

        // WHEN schedule reset device alarm
        mScheduler.scheduleResetDeviceAlarm();

        // THEN correct alarm should be scheduled
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();

        // THEN alarm should be scheduled at correct time
        long actualTriggerTime = alarmManager.peekNextScheduledAlarm().triggerAtTime;
        assertThat(actualTriggerTime).isEqualTo(
                SystemClock.elapsedRealtime() + RESET_DEVICE_MILLIS);

        // THEN expected trigger time should be stored in storage
        runBySequentialExecutor(
                () -> assertThat(UserParameters.getResetDeviceTimeMillis(mTestApp)).isEqualTo(
                        TEST_CURRENT_TIME_MILLIS + RESET_DEVICE_MILLIS));
    }

    @Test
    public void rescheduleNextProvisionFailedStepAlarm() {
        // GIVEN no alarm is scheduled
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();

        // GIVEN expected time in storage
        UserParameters.setNextProvisionFailedStepTimeMills(mTestApp,
                TEST_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS);

        // WHEN next provision failed step alarm is rescheduled
        runBySequentialExecutor(mScheduler::rescheduleNextProvisionFailedStepAlarmIfNeeded);

        // THEN correct alarm should be scheduled at correct time
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();

        long expectedTriggerTime = SystemClock.elapsedRealtime()
                + (TEST_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS - TEST_CURRENT_TIME_MILLIS);
        assertThat(alarmManager.peekNextScheduledAlarm().triggerAtTime).isEqualTo(
                expectedTriggerTime);
    }

    @Test
    public void rescheduleResetDeviceAlarm() {
        // GIVEN no alarm is scheduled
        ShadowAlarmManager alarmManager = Shadows.shadowOf(
                mTestApp.getSystemService(AlarmManager.class));
        assertThat(alarmManager.peekNextScheduledAlarm()).isNull();

        // GIVEN expected reset device time in storage
        UserParameters.setResetDeviceTimeMillis(mTestApp, TEST_RESET_DEVICE_TIME_MILLIS);

        // WHEN reset device alarm is rescheduled
        runBySequentialExecutor(mScheduler::rescheduleResetDeviceAlarmIfNeeded);

        // THEN correct alarm should be scheduled at correct time
        PendingIntent actualPendingIntent = alarmManager.peekNextScheduledAlarm().operation;
        assertThat(actualPendingIntent.isBroadcast()).isTrue();

        long expectedTriggerTime = SystemClock.elapsedRealtime()
                + (TEST_RESET_DEVICE_TIME_MILLIS - TEST_CURRENT_TIME_MILLIS);
        assertThat(alarmManager.peekNextScheduledAlarm().triggerAtTime).isEqualTo(
                expectedTriggerTime);
    }

    private static void runBySequentialExecutor(Runnable runnable) {
        Futures.getUnchecked(
                Futures.submit(runnable, ThreadUtils.getSequentialSchedulerExecutor()));
    }
}
