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

package com.android.devicelockcontroller.debug;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerImpl.PROVISION_PAUSED_MINUTES_DEFAULT;
import static com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerImpl.PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.StringDef;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.FcmRegistrationTokenProvider;
import com.android.devicelockcontroller.WorkManagerExceptionHandler.WorkFailureAlarmReceiver;
import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionHelperImpl;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.provision.worker.PauseProvisioningWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerImpl;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.annotation.Retention;
import java.util.Objects;

/**
 * A {@link BroadcastReceiver} that can handle reset, lock, unlock command.
 * <p>
 * Note:
 * Reboot device are {@link DeviceLockCommandReceiver#onReceive(Context, Intent)} has been called to
 * take effect.
 */
public final class DeviceLockCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "DeviceLockCommandReceiver";
    private static final String EXTRA_COMMAND = "command";
    private static final String EXTRA_CHECK_IN_STATUS = "check-in-status";
    private static final String EXTRA_CHECK_IN_RETRY_DELAY = "check-in-retry-delay";
    private static final String EXTRA_FORCE_PROVISION = "force-provision";
    private static final String EXTRA_IS_IN_APPROVED_COUNTRY = "is-in-approved-country";
    private static final String EXTRA_NEXT_PROVISION_STATE = "next-provision-state";
    private static final String EXTRA_DAYS_LEFT_UNTIL_RESET = "days-left-until-reset";
    private static final String EXTRA_PAUSED_MINUTES = "paused-minutes";
    private static final String EXTRA_REPORT_INTERVAL_MINUTES = "report-interval-minutes";
    private static final String EXTRA_RESET_DEVICE_MINUTES = "reset-device-minutes";
    private static final String EXTRA_MANDATORY_RESET_DEVICE_MINUTES =
            "mandatory-reset-device-minutes";
    private static final String EXTRA_HOST_NAME = "host-name";
    public static final String EXTRA_RESET_INCLUDE_SETUP_PARAMETERS_AND_DEBUG_SETUPS =
            "include-setup-params-and-debug-setups";

    @Retention(SOURCE)
    @StringDef({
            Commands.RESET,
            Commands.LOCK,
            Commands.UNLOCK,
            Commands.CHECK_IN,
            Commands.CLEAR,
            Commands.DUMP,
            Commands.FCM,
            Commands.ENABLE_DEBUG_CLIENT,
            Commands.DISABLE_DEBUG_CLIENT,
            Commands.SET_DEBUG_CLIENT_RESPONSE,
            Commands.DUMP_DEBUG_CLIENT_RESPONSE,
            Commands.SET_DEBUG_CLIENT_RESPONSE,
            Commands.DUMP_DEBUG_SCHEDULER,
    })
    private @interface Commands {
        String RESET = "reset";
        String LOCK = "lock";
        String UNLOCK = "unlock";
        String CHECK_IN = "check-in";
        String CLEAR = "clear";
        String DUMP = "dump";
        String FCM = "fcm";
        String ENABLE_DEBUG_CLIENT = "enable-debug-client";
        String DISABLE_DEBUG_CLIENT = "disable-debug-client";
        String SET_CHECK_IN_HOST_NAME = "set-check-in-host-name";
        String SET_FINALIZE_HOST_NAME = "set-finalize-host-name";
        String SET_DEBUG_CLIENT_RESPONSE = "set-debug-client-response";
        String DUMP_DEBUG_CLIENT_RESPONSE = "dump-debug-client-response";
        String SET_UP_DEBUG_SCHEDULER = "set-up-debug-scheduler";
        String DUMP_DEBUG_SCHEDULER = "dump-debug-scheduler";
        String ENABLE_PREINSTALLED_KIOSK = "enable-preinstalled-kiosk";
        String DISABLE_PREINSTALLED_KIOSK = "disable-preinstalled-kiosk";
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            throw new SecurityException("This should never be run in production build!");
        }

        if (!TextUtils.equals(intent.getComponent().getClassName(), getClass().getName())) {
            throw new IllegalArgumentException("Intent does not match this class!");
        }

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();
        if (isUserProfile) {
            LogUtil.w(TAG, "Broadcast should not target user profiles");
            return;
        }

        Context appContext = context.getApplicationContext();

        ProvisionStateController provisionStateController =
                ((PolicyObjectsProvider) appContext).getProvisionStateController();
        DeviceStateController deviceStateController =
                provisionStateController.getDeviceStateController();

        @Commands
        String command = String.valueOf(intent.getStringExtra(EXTRA_COMMAND));
        switch (command) {
            case Commands.RESET:
                forceReset(appContext, intent.getBooleanExtra(
                        EXTRA_RESET_INCLUDE_SETUP_PARAMETERS_AND_DEBUG_SETUPS, false));
                break;
            case Commands.LOCK:
                Futures.addCallback(deviceStateController.lockDevice(),
                        getSetStateCallBack(LOCKED), MoreExecutors.directExecutor());
                break;
            case Commands.UNLOCK:
                Futures.addCallback(deviceStateController.unlockDevice(),
                        getSetStateCallBack(UNLOCKED), MoreExecutors.directExecutor());
                break;
            case Commands.CLEAR:
                Futures.addCallback(deviceStateController.clearDevice(),
                        getSetStateCallBack(CLEARED), MoreExecutors.directExecutor());
                break;
            case Commands.CHECK_IN:
                tryCheckIn(appContext);
                break;
            case Commands.DUMP:
                dumpStorage(context);
                break;
            case Commands.FCM:
                logFcmToken(appContext);
                break;
            case Commands.ENABLE_DEBUG_CLIENT:
                DeviceCheckInClientDebug.setDebugClientEnabled(context, true);
                break;
            case Commands.DISABLE_DEBUG_CLIENT:
                DeviceCheckInClientDebug.setDebugClientEnabled(context, false);
                break;
            case Commands.SET_CHECK_IN_HOST_NAME:
                setCheckInHostName(context, intent);
                break;
            case Commands.SET_FINALIZE_HOST_NAME:
                setFinalizeHostName(context, intent);
                break;
            case Commands.SET_DEBUG_CLIENT_RESPONSE:
                setDebugCheckInClientResponse(context, intent);
                break;
            case Commands.DUMP_DEBUG_CLIENT_RESPONSE:
                DeviceCheckInClientDebug.dumpDebugCheckInClientResponses(context);
                break;
            case Commands.SET_UP_DEBUG_SCHEDULER:
                setUpDebugScheduler(context, intent);
                break;
            case Commands.DUMP_DEBUG_SCHEDULER:
                DeviceLockControllerSchedulerImpl.dumpDebugScheduler(context);
                break;
            case Commands.ENABLE_PREINSTALLED_KIOSK:
                ProvisionHelperImpl.setPreinstalledKioskAllowed(context, true);
                break;
            case Commands.DISABLE_PREINSTALLED_KIOSK:
                ProvisionHelperImpl.setPreinstalledKioskAllowed(context, false);
                break;
            default:
                throw new IllegalArgumentException("Unsupported command: " + command);
        }
    }

    private static void setCheckInHostName(Context context, Intent intent) {
        if (!intent.hasExtra(EXTRA_HOST_NAME)) {
            throw new IllegalArgumentException("Missing host name override!");
        }
        DeviceCheckInClient.setHostNameOverride(context, intent.getStringExtra(EXTRA_HOST_NAME));
    }

    private static void setFinalizeHostName(Context context, Intent intent) {
        if (!intent.hasExtra(EXTRA_HOST_NAME)) {
            throw new IllegalArgumentException("Missing host name override!");
        }
        DeviceFinalizeClient.setHostNameOverride(context, intent.getStringExtra(EXTRA_HOST_NAME));
    }

    private static void setDebugCheckInClientResponse(Context context, Intent intent) {
        if (intent.hasExtra(EXTRA_CHECK_IN_STATUS)) {
            DeviceCheckInClientDebug.setDebugCheckInStatus(context,
                    intent.getIntExtra(EXTRA_CHECK_IN_STATUS, READY_FOR_PROVISION));

        }
        if (intent.hasExtra(EXTRA_FORCE_PROVISION)) {
            DeviceCheckInClientDebug.setDebugForceProvisioning(context,
                    intent.getBooleanExtra(EXTRA_FORCE_PROVISION, false));
        }
        if (intent.hasExtra(EXTRA_IS_IN_APPROVED_COUNTRY)) {
            DeviceCheckInClientDebug.setDebugApprovedCountry(context,
                    intent.getBooleanExtra(EXTRA_IS_IN_APPROVED_COUNTRY, true));
        }
        if (intent.hasExtra(EXTRA_NEXT_PROVISION_STATE)) {
            DeviceCheckInClientDebug.setDebugNextProvisionState(context,
                    intent.getIntExtra(EXTRA_NEXT_PROVISION_STATE,
                            PROVISION_STATE_UNSPECIFIED));
        }
        if (intent.hasExtra(EXTRA_DAYS_LEFT_UNTIL_RESET)) {
            DeviceCheckInClientDebug.setDebugDaysLeftUntilReset(
                    context, intent.getIntExtra(EXTRA_DAYS_LEFT_UNTIL_RESET, /* days_left*/ 1));
        }
        if (intent.hasExtra(EXTRA_CHECK_IN_RETRY_DELAY)) {
            DeviceCheckInClientDebug.setDebugCheckInRetryDelay(context,
                    intent.getIntExtra(EXTRA_CHECK_IN_RETRY_DELAY, /* delay_minute= */ 1));
        }
    }

    private static void setUpDebugScheduler(Context context, Intent intent) {
        if (intent.hasExtra(EXTRA_PAUSED_MINUTES)) {
            DeviceLockControllerSchedulerImpl.setDebugProvisionPausedMinutes(context,
                    intent.getIntExtra(EXTRA_PAUSED_MINUTES, PROVISION_PAUSED_MINUTES_DEFAULT));
        }
        if (intent.hasExtra(EXTRA_REPORT_INTERVAL_MINUTES)) {
            DeviceLockControllerSchedulerImpl.setDebugReportIntervalMinutes(context,
                    intent.getLongExtra(EXTRA_REPORT_INTERVAL_MINUTES,
                            PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES));
        }
        if (intent.hasExtra(EXTRA_RESET_DEVICE_MINUTES)) {
            DeviceLockControllerSchedulerImpl.setDebugResetDeviceMinutes(context,
                    intent.getIntExtra(EXTRA_RESET_DEVICE_MINUTES,
                            NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        }
        if (intent.hasExtra(EXTRA_MANDATORY_RESET_DEVICE_MINUTES)) {
            DeviceLockControllerSchedulerImpl.setDebugMandatoryResetDeviceMinutes(context,
                    intent.getIntExtra(EXTRA_MANDATORY_RESET_DEVICE_MINUTES,
                            MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        }
    }

    private static void dumpStorage(Context context) {
        Futures.addCallback(
                Futures.transformAsync(SetupParametersClient.getInstance().dump(),
                        unused -> GlobalParametersClient.getInstance().dump(),
                        MoreExecutors.directExecutor()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        UserParameters.dump(context);
                        LogUtil.i(TAG, "Successfully dumped storage");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Error encountered when dumping storage", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static void tryCheckIn(Context appContext) {
        if (!appContext.getSystemService(UserManager.class).isSystemUser()) {
            LogUtil.e(TAG, "Only system user can perform a check-in");
            return;
        }
        DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) appContext;
        DeviceLockControllerScheduler scheduler =
                schedulerProvider.getDeviceLockControllerScheduler();

        Futures.addCallback(GlobalParametersClient.getInstance().isProvisionReady(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Boolean provisioningInfoReady) {
                        if (!provisioningInfoReady) {
                            scheduler.scheduleInitialCheckInWork();
                        } else {
                            LogUtil.e(TAG,
                                    "Can not check in when provisioning info has already been "
                                            + "received. Use the \"reset\" command to reset "
                                            + "DLC first.");
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to determine if provisioning info is ready", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static void forceReset(Context context,
            boolean shouldCleanSetupParametersAndDebugSetups) {
        // Cancel provision works
        LogUtil.d(TAG, "cancelling works");
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelAllWorkByTag(DeviceCheckInWorker.class.getName());
        workManager.cancelAllWorkByTag(PauseProvisioningWorker.class.getName());
        workManager.cancelAllWorkByTag(
                ReportDeviceProvisionStateWorker.class.getName());
        workManager.cancelAllWorkByTag(
                ReportDeviceLockProgramCompleteWorker.class.getName());

        // Cancel All alarms
        AlarmManager alarmManager = Objects.requireNonNull(
                context.getSystemService(AlarmManager.class));
        alarmManager.cancel(
                PendingIntent.getBroadcast(
                        context, /* ignored */ 0,
                        new Intent(context, ResetDeviceReceiver.class),
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        alarmManager.cancel(PendingIntent.getBroadcast(
                context, /* ignored */ 0,
                new Intent(context, NextProvisionFailedStepReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        alarmManager.cancel(PendingIntent.getBroadcast(
                context, /* ignored */ 0,
                new Intent(context, ResumeProvisionReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        alarmManager.cancel(PendingIntent.getBroadcast(
                context, /* ignored */ 0,
                new Intent(context, NextProvisionFailedStepReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        alarmManager.cancel(PendingIntent.getBroadcast(
                context, /* ignored */ 0,
                new Intent(context, WorkFailureAlarmReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));

        PolicyObjectsProvider policyObjectsProvider =
                (PolicyObjectsProvider) context.getApplicationContext();
        policyObjectsProvider.destroyObjects();
        UserParameters.setProvisionState(context, PROVISION_SUCCEEDED);
        GlobalParametersClient.getInstance().setDeviceState(CLEARED);
        DevicePolicyController policyController = policyObjectsProvider.getPolicyController();
        ListenableFuture<Void> clearPolicies = Futures.catching(
                policyController.enforceCurrentPolicies(),
                RuntimeException.class, unused -> null,
                MoreExecutors.directExecutor());
        Futures.addCallback(Futures.transformAsync(clearPolicies,
                        unused -> clearStorage(context, shouldCleanSetupParametersAndDebugSetups),
                        MoreExecutors.directExecutor()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.i(TAG, "Reset device state.");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static void logFcmToken(Context appContext) {
        final ListenableFuture<String> fcmRegistrationToken =
                ((FcmRegistrationTokenProvider) appContext).getFcmRegistrationToken();
        Futures.addCallback(fcmRegistrationToken, new FutureCallback<>() {
            @Override
            public void onSuccess(String token) {
                LogUtil.i(TAG,
                        "FCM Registration Token: " + (token == null ? "Not set" : token));
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Unable to get FCM registration token", t);
            }
        }, MoreExecutors.directExecutor());
    }

    private static FutureCallback<Void> getSetStateCallBack(@DeviceState int state) {

        return new FutureCallback<>() {

            @Override
            public void onSuccess(Void unused) {
                LogUtil.i(TAG, "Successfully set state to: " + state);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Unsuccessfully set state to: " + state, t);
            }
        };
    }

    private static ListenableFuture<Void> clearStorage(Context context,
            boolean shouldCleanSetupParametersAndDebugSetups) {
        if (shouldCleanSetupParametersAndDebugSetups) {
            DeviceCheckInClientDebug.clear(context);
            DeviceLockControllerSchedulerImpl.clear(context);
        }
        UserParameters.clear(context);
        return Futures.whenAllSucceed(
                        shouldCleanSetupParametersAndDebugSetups
                                ? SetupParametersClient.getInstance().clear()
                                : Futures.immediateVoidFuture(),
                        GlobalParametersClient.getInstance().clear())
                .call(() -> {
                    ((PolicyObjectsProvider) context.getApplicationContext()).destroyObjects();
                    return null;
                }, context.getMainExecutor());
    }
}
