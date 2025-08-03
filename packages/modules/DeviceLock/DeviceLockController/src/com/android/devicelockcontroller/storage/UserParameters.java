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

package com.android.devicelockcontroller.storage;

import android.annotation.CurrentTimeMillisLong;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.ThreadAsserts;

import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Stores per-user local parameters.
 * Unlike {@link GlobalParameters}, this class can be directly accessed.
 */
public final class UserParameters {
    private static final String FILENAME = "user-params";
    private static final String KEY_HOME_PACKAGE_OVERRIDE = "home_override_package";
    private static final String TAG = "UserParameters";
    private static final String KEY_PROVISION_STATE = "provision-state";
    private static final String KEY_BOOT_TIME_MILLS = "boot-time-mills";
    private static final String KEY_NEXT_CHECK_IN_TIME_MILLIS = "next-check-in-time-millis";
    private static final String KEY_RESUME_PROVISION_TIME_MILLIS =
            "resume-provision-time-millis";
    private static final String KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS =
            "next-provision-failed-step-time-millis";
    private static final String KEY_RESET_DEVICE_TIME_MILLIS = "reset-device-time-millis";
    private static final String KEY_DAYS_LEFT_UNTIL_RESET = "days-left-until-reset";
    private static final String KEY_PROVISIONING_START_TIME_MILLIS =
            "provisioning-start-time-millis";
    public static final String KEY_NEED_INITIAL_CHECK_IN = "need-initial-check-in";
    public static final String KEY_NOTIFICATION_CHANNEL_ID_SUFFIX =
            "notification-channel-id-suffix";

    private UserParameters() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();

        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the current user state.
     */
    @WorkerThread
    @ProvisionState
    public static int getProvisionState(Context context) {
        ThreadAsserts.assertWorkerThread("getProvisionState");
        return getSharedPreferences(context).getInt(KEY_PROVISION_STATE,
                ProvisionState.UNPROVISIONED);
    }

    /**
     * Sets the current user state.
     */
    public static void setProvisionState(Context context, @ProvisionState int state) {
        getSharedPreferences(context).edit().putInt(KEY_PROVISION_STATE, state).apply();
    }

    /** Check if initial check-in is required. */
    @WorkerThread
    public static boolean needInitialCheckIn(Context context) {
        ThreadAsserts.assertWorkerThread("needInitialCheckIn");
        return getSharedPreferences(context).getBoolean(KEY_NEED_INITIAL_CHECK_IN, true);
    }

    /** Mark initial check-in has been scheduled. */
    public static void initialCheckInScheduled(Context context) {
        getSharedPreferences(context).edit().putBoolean(KEY_NEED_INITIAL_CHECK_IN, false).apply();
    }

    /**
     * Gets the name of the package overriding home.
     *
     * @param context Context used to get the shared preferences.
     * @return Package overriding home.
     */
    @WorkerThread
    @Nullable
    public static String getPackageOverridingHome(Context context) {
        ThreadAsserts.assertWorkerThread("getPackageOverridingHome");
        return getSharedPreferences(context).getString(KEY_HOME_PACKAGE_OVERRIDE, null);
    }

    /**
     * Sets the name of the package overriding home.
     *
     * @param context     Context used to get the shared preferences.
     * @param packageName Package overriding home.
     */
    public static void setPackageOverridingHome(Context context, @Nullable String packageName) {
        getSharedPreferences(context).edit()
                .putString(KEY_HOME_PACKAGE_OVERRIDE, packageName).apply();
    }

    /** Get the device boot time */
    @WorkerThread
    @CurrentTimeMillisLong
    public static long getBootTimeMillis(Context context) {
        ThreadAsserts.assertWorkerThread("getBootTimeMillis");
        return getSharedPreferences(context).getLong(KEY_BOOT_TIME_MILLS, 0L);
    }

    /** Set the time when device boot */
    public static void setBootTimeMillis(Context context, @CurrentTimeMillisLong long bootTime) {
        getSharedPreferences(context).edit().putLong(KEY_BOOT_TIME_MILLS, bootTime).apply();
    }

    /** Get the time when next check in should happen */
    @WorkerThread
    @CurrentTimeMillisLong
    public static long getNextCheckInTimeMillis(Context context) {
        ThreadAsserts.assertWorkerThread("getNextCheckInTimeMillis");
        return getSharedPreferences(context).getLong(KEY_NEXT_CHECK_IN_TIME_MILLIS, 0L);
    }

    /** Set the time when next check in should happen */
    public static void setNextCheckInTimeMillis(Context context,
            @CurrentTimeMillisLong long nextCheckInTime) {
        getSharedPreferences(context).edit().putLong(KEY_NEXT_CHECK_IN_TIME_MILLIS,
                nextCheckInTime).apply();
    }

    /** Get the time when provision should resume */
    @WorkerThread
    @CurrentTimeMillisLong
    public static long getResumeProvisionTimeMillis(Context context) {
        ThreadAsserts.assertWorkerThread("getResumeProvisionTimeMillis");
        return getSharedPreferences(context).getLong(KEY_RESUME_PROVISION_TIME_MILLIS, 0L);
    }

    /** Set the time when provision should resume */
    public static void setResumeProvisionTimeMillis(Context context,
            @CurrentTimeMillisLong long resumeProvisionTime) {
        getSharedPreferences(context).edit().putLong(KEY_RESUME_PROVISION_TIME_MILLIS,
                resumeProvisionTime).apply();
    }

    /** Get the time when next provision failed step should happen */
    @WorkerThread
    @CurrentTimeMillisLong
    public static long getNextProvisionFailedStepTimeMills(Context context) {
        ThreadAsserts.assertWorkerThread("getNextProvisionFailedStepTimeMills");
        return getSharedPreferences(context).getLong(KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS,
                0L);
    }

    /** Set the time when next provision failed step should happen */
    public static void setNextProvisionFailedStepTimeMills(Context context,
            @CurrentTimeMillisLong long nextProvisionFailedStep) {
        getSharedPreferences(context).edit().putLong(KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS,
                nextProvisionFailedStep).apply();
    }

    /** Get the time when device should factory reset */
    @WorkerThread
    @CurrentTimeMillisLong
    public static long getResetDeviceTimeMillis(Context context) {
        ThreadAsserts.assertWorkerThread("getResetDeviceTimeMillis");
        return getSharedPreferences(context).getLong(KEY_RESET_DEVICE_TIME_MILLIS, 0L);
    }

    /** Set the time when device should factory reset */
    public static void setResetDeviceTimeMillis(Context context,
            @CurrentTimeMillisLong long resetDeviceTime) {
        getSharedPreferences(context).edit().putLong(KEY_RESET_DEVICE_TIME_MILLIS,
                resetDeviceTime).apply();
    }

    /** Get the number of days before device should factory reset */
    @WorkerThread
    public static int getDaysLeftUntilReset(Context context) {
        ThreadAsserts.assertWorkerThread("getDaysLeftUntilReset");
        return getSharedPreferences(context).getInt(KEY_DAYS_LEFT_UNTIL_RESET, Integer.MAX_VALUE);
    }

    /** Set the number of days before device should factory reset */
    public static void setDaysLeftUntilReset(Context context, int days) {
        getSharedPreferences(context).edit().putInt(KEY_DAYS_LEFT_UNTIL_RESET, days).apply();
    }

    public static long getProvisioningStartTimeMillis(Context context) {
        return getSharedPreferences(context).getLong(KEY_PROVISIONING_START_TIME_MILLIS,
                /* defValue = */-1L);
    }

    public static void setProvisioningStartTimeMillis(Context context,
            @CurrentTimeMillisLong long provisioningStartTime) {
        getSharedPreferences(context).edit().putLong(KEY_PROVISIONING_START_TIME_MILLIS,
                provisioningStartTime).apply();
    }

    /** Get the suffix used for the notification channel */
    @WorkerThread
    public static String getNotificationChannelIdSuffix(Context context) {
        ThreadAsserts.assertWorkerThread("getNotificationChannelIdSuffix");
        return getSharedPreferences(context).getString(KEY_NOTIFICATION_CHANNEL_ID_SUFFIX,
                /* defValue= */ "");
    }

    /** Set the suffix used for the notification channel */
    @WorkerThread
    public static void setNotificationChannelIdSuffix(Context context,
            @Nullable String notificationChannelSuffix) {
        ThreadAsserts.assertWorkerThread("setNotificationChannelIdSuffix");
        getSharedPreferences(context).edit()
                .putString(KEY_NOTIFICATION_CHANNEL_ID_SUFFIX, notificationChannelSuffix).apply();
    }

    /**
     * Clear all user parameters.
     */
    @WorkerThread
    public static void clear(Context context) {
        ThreadAsserts.assertWorkerThread("clear");
        if (!Build.isDebuggable()) {
            throw new SecurityException("Clear is not allowed in non-debuggable build!");
        }
        // We want to keep the boot time in order to reschedule works/alarms when system clock
        // changes.
        long bootTime = UserParameters.getBootTimeMillis(context);
        getSharedPreferences(context).edit().clear().commit();
        UserParameters.setBootTimeMillis(context, bootTime);
    }

    /**
     * Dump the current value of user parameters for the user associated with the input context.
     */
    public static void dump(Context context) {
        Executors.newSingleThreadScheduledExecutor().submit(() -> {
            LogUtil.d(TAG, String.format(Locale.US,
                    "Dumping UserParameters for user: %s ...\n"
                            + "%s: %s\n"    // user_state:
                            + "%s: %s\n"    // home_override_package:
                            + "%s: %s\n"    // boot-time-mills:
                            + "%s: %s\n"    // next-check-in-time-millis:
                            + "%s: %s\n"    // resume-provision-time-millis:
                            + "%s: %s\n"    // next-provision-failed-step-time-millis:
                            + "%s: %s\n"    // reset-device-time-millis:
                            + "%s: %s\n"    // days-left-until-reset:
                            + "%s: %s\n",   // notification-channel-suffix:
                    context.getUser(),
                    KEY_PROVISION_STATE, getProvisionState(context),
                    KEY_HOME_PACKAGE_OVERRIDE, getPackageOverridingHome(context),
                    KEY_BOOT_TIME_MILLS, getBootTimeMillis(context),
                    KEY_NEXT_CHECK_IN_TIME_MILLIS, getNextCheckInTimeMillis(context),
                    KEY_RESUME_PROVISION_TIME_MILLIS, getResumeProvisionTimeMillis(context),
                    KEY_NEXT_PROVISION_FAILED_STEP_TIME_MILLIS,
                    getNextProvisionFailedStepTimeMills(context),
                    KEY_RESET_DEVICE_TIME_MILLIS, getResetDeviceTimeMillis(context),
                    KEY_DAYS_LEFT_UNTIL_RESET, getDaysLeftUntilReset(context),
                    KEY_NOTIFICATION_CHANNEL_ID_SUFFIX, getNotificationChannelIdSuffix(context)
            ));
        });
    }
}
