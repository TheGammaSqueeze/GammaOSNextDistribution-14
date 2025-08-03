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

import static com.android.devicelockcontroller.WorkManagerExceptionHandler.AlarmReason;
import static com.android.devicelockcontroller.common.DeviceLockConstants.MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;
import static com.android.devicelockcontroller.provision.worker.AbstractCheckInWorker.BACKOFF_DELAY;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.WorkManagerExceptionHandler;
import com.android.devicelockcontroller.activities.DeviceLockNotificationManager;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.ThreadUtils;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link DeviceLockControllerScheduler}.
 * WARNING: Do not create an instance directly, instead you should retrieve it using the
 * {@link DeviceLockControllerApplication#getDeviceLockControllerScheduler()} API.
 */
public final class DeviceLockControllerSchedulerImpl implements DeviceLockControllerScheduler {
    private static final String TAG = "DeviceLockControllerSchedulerImpl";
    private static final String FILENAME = "device-lock-controller-scheduler-preferences";
    public static final String DEVICE_CHECK_IN_WORK_NAME = "device-check-in";
    private static final String DEBUG_DEVICELOCK_PAUSED_MINUTES = "debug.devicelock.paused-minutes";
    private static final String DEBUG_DEVICELOCK_REPORT_INTERVAL_MINUTES =
            "debug.devicelock.report-interval-minutes";
    private static final String DEBUG_DEVICELOCK_RESET_DEVICE_MINUTES =
            "debug.devicelock.reset-device-minutes";
    private static final String DEBUG_DEVICELOCK_MANDATORY_RESET_DEVICE_MINUTES =
            "debug.devicelock.mandatory-reset-device-minutes";

    // The default minute value of the duration that provision UI can be paused.
    public static final int PROVISION_PAUSED_MINUTES_DEFAULT = 60;
    // The default minute value of the interval between steps of provision failed flow.
    public static final long PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES =
            TimeUnit.DAYS.toMinutes(1);
    private final Context mContext;
    private final Clock mClock;
    private final Executor mSequentialExecutor;
    private final ProvisionStateController mProvisionStateController;

    private static volatile SharedPreferences sSharedPreferences;

    private static synchronized SharedPreferences getSharedPreferences(
            Context context) {
        if (sSharedPreferences == null) {
            sSharedPreferences = context.createDeviceProtectedStorageContext().getSharedPreferences(
                    FILENAME,
                    Context.MODE_PRIVATE);
        }
        return sSharedPreferences;
    }

    /**
     * Set how long provision should be paused after user hit the "Do it in 1 hour" button, in
     * minutes.
     */
    public static void setDebugProvisionPausedMinutes(Context context, int minutes) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_PAUSED_MINUTES,
                minutes).apply();
    }

    /**
     * Set the length of the interval of provisioning failure reporting for debugging purpose.
     */
    public static void setDebugReportIntervalMinutes(Context context, long minutes) {
        getSharedPreferences(context).edit().putLong(DEBUG_DEVICELOCK_REPORT_INTERVAL_MINUTES,
                minutes).apply();
    }

    /**
     * Set the length of the countdown minutes when device is about to factory reset in
     * non-mandatory provisioning case for debugging purpose.
     */
    public static void setDebugResetDeviceMinutes(Context context, int minutes) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_RESET_DEVICE_MINUTES,
                minutes).apply();
    }

    /**
     * Set the length of the countdown minutes when device is about to factory reset in mandatory
     * provisioning case for debugging purpose.
     */
    public static void setDebugMandatoryResetDeviceMinutes(Context context, int minutes) {
        getSharedPreferences(context).edit().putInt(DEBUG_DEVICELOCK_MANDATORY_RESET_DEVICE_MINUTES,
                minutes).apply();
    }

    /**
     * Dump current debugging setup to logcat.
     */
    public static void dumpDebugScheduler(Context context) {
        LogUtil.d(TAG,
                "Current Debug Scheduler setups:\n" + getSharedPreferences(context).getAll());
    }

    /**
     * Clear current debugging setup.
     */
    public static void clear(Context context) {
        getSharedPreferences(context).edit().clear().apply();
    }

    public DeviceLockControllerSchedulerImpl(Context context,
            ProvisionStateController provisionStateController) {
        this(context, Clock.systemUTC(), provisionStateController);
    }

    @VisibleForTesting
    DeviceLockControllerSchedulerImpl(Context context, Clock clock,
            ProvisionStateController provisionStateController) {
        mContext = context;
        mProvisionStateController = provisionStateController;
        mClock = clock;
        mSequentialExecutor = ThreadUtils.getSequentialSchedulerExecutor();
    }

    @Override
    public void notifyTimeChanged() {
        Futures.addCallback(mProvisionStateController.getState(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@ProvisionState Integer currentState) {
                        correctStoredTime(currentState);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, mSequentialExecutor);
    }

    /**
     * Correct the stored time for when a scheduled work/alarm should execute based on the
     * difference between current time and stored time.
     *
     * @param currentState The current {@link ProvisionState} used to determine which work/alarm may
     *                     be possibly scheduled.
     */
    @VisibleForTesting
    void correctStoredTime(@ProvisionState Integer currentState) {
        long bootTimestamp = UserParameters.getBootTimeMillis(mContext);
        long delta =
                mClock.millis() - (bootTimestamp + SystemClock.elapsedRealtime());
        UserParameters.setBootTimeMillis(mContext,
                UserParameters.getBootTimeMillis(mContext) + delta);
        if (currentState == UNPROVISIONED) {
            long before = UserParameters.getNextCheckInTimeMillis(mContext);
            if (before > 0) {
                UserParameters.setNextCheckInTimeMillis(mContext,
                        before + delta);
            }
            // We have to reschedule (update) the check-in work, because, otherwise, if device
            // reboots, WorkManager will reschedule the work based on the changed system clock,
            // which will result in inaccurate schedule. (see b/285221785)
            rescheduleRetryCheckInWork();
        } else if (currentState == PROVISION_PAUSED) {
            long before = UserParameters.getResumeProvisionTimeMillis(mContext);
            if (before > 0) {
                UserParameters.setResumeProvisionTimeMillis(mContext,
                        before + delta);
            }
        } else if (currentState == PROVISION_FAILED) {
            long before = UserParameters.getNextProvisionFailedStepTimeMills(
                    mContext);
            if (before > 0) {
                UserParameters.setNextProvisionFailedStepTimeMills(mContext,
                        before + delta);
            }
            before = UserParameters.getResetDeviceTimeMillis(mContext);
            if (before > 0) {
                UserParameters.setResetDeviceTimeMillis(mContext,
                        before + delta);
            }
        }
    }

    @Override
    public void scheduleResumeProvisionAlarm() {
        Duration delay = Duration.ofMinutes(PROVISION_PAUSED_MINUTES_DEFAULT);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    getSharedPreferences(mContext).getInt(DEBUG_DEVICELOCK_PAUSED_MINUTES,
                            PROVISION_PAUSED_MINUTES_DEFAULT));
        }
        LogUtil.i(TAG, "Scheduling resume provision work with delay: " + delay);
        scheduleResumeProvisionAlarm(delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        UserParameters.setResumeProvisionTimeMillis(mContext,
                whenExpectedToRun.toEpochMilli());
    }

    @Override
    public void notifyRebootWhenProvisionPaused() {
        dispatchFuture(this::rescheduleResumeProvisionAlarmIfNeeded,
                "notifyRebootWhenProvisionPaused");
    }

    @Override
    public ListenableFuture<Void> scheduleInitialCheckInWork() {
        LogUtil.i(TAG, "Scheduling initial check-in work");
        final Operation operation =
                enqueueCheckInWorkRequest(/* isExpedited= */ true, Duration.ZERO);
        final ListenableFuture<Operation.State.SUCCESS> result = operation.getResult();

        return FluentFuture.from(result)
                .transform((Function<Operation.State.SUCCESS, Void>) ignored -> {
                    UserParameters.initialCheckInScheduled(mContext);
                    return null;
                }, mSequentialExecutor)
                .catching(Throwable.class, (e) -> {
                    LogUtil.e(TAG, "Failed to enqueue initial check in work", e);
                    WorkManagerExceptionHandler.scheduleAlarm(mContext,
                            AlarmReason.INITIAL_CHECK_IN);
                    throw new RuntimeException(e);
                }, mSequentialExecutor);
    }

    @Override
    public ListenableFuture<Void> scheduleRetryCheckInWork(Duration delay) {
        LogUtil.i(TAG, "Scheduling retry check-in work with delay: " + delay);
        final Operation operation =
                enqueueCheckInWorkRequest(/* isExpedited= */ false, delay);
        final ListenableFuture<Operation.State.SUCCESS> result = operation.getResult();

        return FluentFuture.from(result)
                .transform((Function<Operation.State.SUCCESS, Void>) ignored -> {
                    Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
                    UserParameters.setNextCheckInTimeMillis(mContext,
                            whenExpectedToRun.toEpochMilli());
                    return null;
                }, mSequentialExecutor)
                .catching(Throwable.class, (e) -> {
                    LogUtil.e(TAG, "Failed to enqueue retry check in work", e);
                    WorkManagerExceptionHandler.scheduleAlarm(mContext,
                            AlarmReason.RETRY_CHECK_IN);
                    throw new RuntimeException(e);
                }, mSequentialExecutor);
    }

    @Override
    public ListenableFuture<Void> notifyNeedRescheduleCheckIn() {
        final ListenableFuture<Void> result =
                Futures.submit(this::rescheduleRetryCheckInWork, mSequentialExecutor);
        Futures.addCallback(result,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void unused) {
                        LogUtil.i(TAG, "Successfully called notifyNeedRescheduleCheckIn");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException("failed to call notifyNeedRescheduleCheckIn", t);
                    }
                }, MoreExecutors.directExecutor());
        return result;
    }

    @VisibleForTesting
    void rescheduleRetryCheckInWork() {
        long nextCheckInTimeMillis = UserParameters.getNextCheckInTimeMillis(mContext);
        if (nextCheckInTimeMillis > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(nextCheckInTimeMillis));
            LogUtil.i(TAG, "Rescheduling retry check-in work with delay: " + delay);
            final Operation operation =
                    enqueueCheckInWorkRequest(/* isExpedited= */ false, delay);
            Futures.addCallback(operation.getResult(), new FutureCallback<>() {
                @Override
                public void onSuccess(Operation.State.SUCCESS result) {
                    // No-op
                }

                @Override
                public void onFailure(Throwable t) {
                    LogUtil.e(TAG, "Failed to reschedule retry check in work", t);
                    WorkManagerExceptionHandler.scheduleAlarm(mContext,
                            AlarmReason.RESCHEDULE_CHECK_IN);
                }
            }, mSequentialExecutor);
        }
    }

    @Override
    public ListenableFuture<Void> maybeScheduleInitialCheckIn() {
        return FluentFuture.from(Futures.submit(() -> UserParameters.needInitialCheckIn(mContext),
                        mSequentialExecutor))
                .transformAsync(needCheckIn -> {
                    if (needCheckIn) {
                        return Futures.transform(scheduleInitialCheckInWork(),
                                input -> false /* reschedule */, mSequentialExecutor);
                    } else {
                        return Futures.transform(
                                GlobalParametersClient.getInstance().isProvisionReady(),
                                ready -> !ready, mSequentialExecutor);
                    }
                }, mSequentialExecutor)
                .transformAsync(reschedule -> {
                    if (reschedule) {
                        return notifyNeedRescheduleCheckIn();
                    }
                    return Futures.immediateVoidFuture();
                }, mSequentialExecutor);
    }

    @Override
    public void scheduleNextProvisionFailedStepAlarm() {
        LogUtil.d(TAG,
                "Scheduling next provision failed step alarm");
        long lastTimestamp = UserParameters.getNextProvisionFailedStepTimeMills(mContext);
        long nextTimestamp;
        if (lastTimestamp == 0) {
            lastTimestamp = Instant.now(mClock).toEpochMilli();
        }
        long minutes = Build.isDebuggable() ? getSharedPreferences(mContext).getLong(
                DEBUG_DEVICELOCK_REPORT_INTERVAL_MINUTES,
                PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES)
                : PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES;
        Duration delay = Duration.ofMinutes(minutes);
        nextTimestamp = lastTimestamp + delay.toMillis();
        scheduleNextProvisionFailedStepAlarm(
                Duration.between(Instant.now(mClock), Instant.ofEpochMilli(nextTimestamp)));
        UserParameters.setNextProvisionFailedStepTimeMills(mContext, nextTimestamp);
    }

    @Override
    public void notifyRebootWhenProvisionFailed() {
        dispatchFuture(() -> {
            rescheduleNextProvisionFailedStepAlarmIfNeeded();
            rescheduleResetDeviceAlarmIfNeeded();
        }, "notifyRebootWhenProvisionFailed");
    }


    @Override
    public void scheduleResetDeviceAlarm() {
        Duration delay = Duration.ofMinutes(NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    getSharedPreferences(mContext)
                            .getInt(DEBUG_DEVICELOCK_RESET_DEVICE_MINUTES,
                                    NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        }
        scheduleResetDeviceAlarm(delay);
    }

    @Override
    public void scheduleMandatoryResetDeviceAlarm() {
        Duration delay = Duration.ofMinutes(MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    getSharedPreferences(mContext)
                            .getInt(DEBUG_DEVICELOCK_MANDATORY_RESET_DEVICE_MINUTES,
                                    MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        }
        scheduleResetDeviceAlarm(delay);
    }

    private void scheduleResetDeviceAlarm(Duration delay) {
        scheduleResetDeviceAlarmInternal(delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        DeviceLockNotificationManager.sendDeviceResetTimerNotification(mContext,
                SystemClock.elapsedRealtime() + delay.toMillis());
        UserParameters.setResetDeviceTimeMillis(mContext, whenExpectedToRun.toEpochMilli());
    }

    @VisibleForTesting
    void rescheduleNextProvisionFailedStepAlarmIfNeeded() {
        long timestamp = UserParameters.getNextProvisionFailedStepTimeMills(mContext);
        if (timestamp > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(timestamp));
            scheduleNextProvisionFailedStepAlarm(delay);
        }
    }

    @VisibleForTesting
    void rescheduleResetDeviceAlarmIfNeeded() {
        long timestamp = UserParameters.getResetDeviceTimeMillis(mContext);
        if (timestamp > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(timestamp));
            scheduleResetDeviceAlarmInternal(delay);
        }
    }

    @VisibleForTesting
    void rescheduleResumeProvisionAlarmIfNeeded() {
        long resumeProvisionTimeMillis = UserParameters.getResumeProvisionTimeMillis(mContext);
        if (resumeProvisionTimeMillis > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(resumeProvisionTimeMillis));
            scheduleResumeProvisionAlarm(delay);
        }
    }

    /**
     * Run the input runnable in order on the scheduler's sequential executor
     *
     * @param runnable   The runnable to run on worker thread.
     * @param methodName The name of the method that requested to run runnable.
     */
    private void dispatchFuture(Runnable runnable, String methodName) {
        Futures.addCallback(Futures.submit(runnable, mSequentialExecutor),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void unused) {
                        LogUtil.i(TAG, "Successfully called " + methodName);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException("failed to call " + methodName, t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private Operation enqueueCheckInWorkRequest(boolean isExpedited, Duration delay) {
        OneTimeWorkRequest.Builder builder =
                new OneTimeWorkRequest.Builder(DeviceCheckInWorker.class)
                        .setConstraints(
                                new Constraints.Builder().setRequiredNetworkType(
                                        NetworkType.CONNECTED).build())
                        .setInitialDelay(delay)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY);
        if (isExpedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);

        return WorkManager.getInstance(mContext).enqueueUniqueWork(DEVICE_CHECK_IN_WORK_NAME,
                ExistingWorkPolicy.REPLACE, builder.build());
    }

    private void scheduleResumeProvisionAlarm(Duration delay) {
        scheduleAlarmWithPendingIntentAndDelay(ResumeProvisionReceiver.class, delay);
    }

    private void scheduleNextProvisionFailedStepAlarm(Duration delay) {
        scheduleAlarmWithPendingIntentAndDelay(NextProvisionFailedStepReceiver.class, delay);
    }

    private void scheduleResetDeviceAlarmInternal(Duration delay) {
        scheduleAlarmWithPendingIntentAndDelay(ResetDeviceReceiver.class, delay);
    }

    private void scheduleAlarmWithPendingIntentAndDelay(
            Class<? extends BroadcastReceiver> receiverClass, Duration delay) {
        long countDownBase = SystemClock.elapsedRealtime() + delay.toMillis();
        AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, /* ignored */ 0,
                new Intent(mContext, receiverClass),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        Objects.requireNonNull(alarmManager).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                countDownBase,
                pendingIntent);
    }
}
