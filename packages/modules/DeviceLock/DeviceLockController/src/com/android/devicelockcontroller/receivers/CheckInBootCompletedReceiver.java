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

package com.android.devicelockcontroller.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Boot completed broadcast receiver to enqueue the check-in work for provision when device boots
 * for the first time.
 *
 * Only runs on system user and is disabled after check-in completes successfully.
 */
public final class CheckInBootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "CheckInBootCompletedReceiver";
    private final Executor mExecutor;

    public CheckInBootCompletedReceiver() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @VisibleForTesting
    CheckInBootCompletedReceiver(Executor executor) {
        mExecutor = executor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) return;

        LogUtil.i(TAG, "Received boot completed intent");

        if (!context.getUser().isSystem()) {
            // This is not *supposed* to happen since the receiver is marked systemUserOnly but
            // there seems to be some edge case where it does. See b/304318606.
            // In this case, we'll just disable and return early.
            LogUtil.w(TAG, "Called check in boot receiver on non-system user!");
            disableCheckInBootCompletedReceiver(context);
            return;
        }

        final DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) context.getApplicationContext();
        final DeviceLockControllerScheduler scheduler =
                schedulerProvider.getDeviceLockControllerScheduler();

        ListenableFuture<Void> scheduleCheckIn = scheduler.maybeScheduleInitialCheckIn();

        final PendingResult pendingResult = goAsync();

        scheduleCheckIn.addListener(pendingResult::finish, mExecutor);
    }

    /**
     * Disable the receiver for the current user
     *
     * @param context context of current user
     */
    public static void disableCheckInBootCompletedReceiver(Context context) {
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, CheckInBootCompletedReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
}
