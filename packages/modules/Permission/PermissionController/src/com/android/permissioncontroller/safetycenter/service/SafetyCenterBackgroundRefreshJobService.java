/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.service;

import static android.app.job.JobScheduler.RESULT_SUCCESS;
import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PERIODIC;

import static com.android.permissioncontroller.Constants.SAFETY_CENTER_BACKGROUND_REFRESH_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.safetycenter.SafetyCenterManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.time.Duration;

/**
 * Uses {@link android.app.job.JobScheduler} to schedule periodic calls to {@link
 * SafetyCenterManager#refreshSafetySources} after boot completed if safety center is already
 * enabled, or after safety center is enabled otherwise.
 *
 * <p>The job waits until the device is in idle mode to minimize impact on system health.
 */
public final class SafetyCenterBackgroundRefreshJobService extends JobService {
    private static final String TAG = "SafetyCenterBackgroundR";

    /** Schedules a periodic background refresh. */
    public static final class SetupSafetyCenterBackgroundRefreshReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            schedulePeriodicBackgroundRefresh(context, intent.getAction());
        }
    }

    /**
     * Schedules a periodic call to {@link SafetyCenterManager#refreshSafetySources} to be run when
     * the device is idle, after either {@link android.content.Intent#ACTION_BOOT_COMPLETED} or
     * {@link android.safetycenter.SafetyCenterManager#ACTION_SAFETY_CENTER_ENABLED_CHANGED}.
     *
     * <p>The {@link SafetyCenterManager#isSafetyCenterEnabled} check ensures that jobs are never
     * scheduled if SafetyCenter is disabled, we check again in {@link
     * SafetyCenterBackgroundRefreshJobService#onStartJob} in case SafetyCenter becomes disabled
     * later.
     *
     * <p>{@link SafetyCenterJobServiceFlags#areBackgroundRefreshesEnabled} is only checked in
     * {@link SafetyCenterBackgroundRefreshJobService#onStartJob} as we do not receive a new
     * broadcast if this flag gets enabled.
     */
    private static void schedulePeriodicBackgroundRefresh(
            Context context, @Nullable String actionString) {

        if (!isActionStringValid(actionString)) {
            Log.i(TAG, "Ignoring a " + actionString + " broadcast.");
            return;
        }

        SafetyCenterManager safetyCenterManager =
                context.getSystemService(SafetyCenterManager.class);
        if (safetyCenterManager == null) {
            Log.w(TAG, "SafetyCenterManager is null, cannot schedule background refresh.");
            return;
        }

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            Log.w(TAG, "JobScheduler is null, cannot schedule background refresh.");
            return;
        }

        if (!safetyCenterManager.isSafetyCenterEnabled()) {
            Log.i(
                    TAG,
                    "Received a "
                            + actionString
                            + " broadcast, but safety center is currently disabled. Cancelling any"
                            + " existing job.");
            jobScheduler.cancel(SAFETY_CENTER_BACKGROUND_REFRESH_JOB_ID);
            return;
        }

        Duration periodicBackgroundRefreshInterval =
                SafetyCenterJobServiceFlags.getPeriodicBackgroundRefreshInterval();
        JobInfo jobInfo =
                new JobInfo.Builder(
                                SAFETY_CENTER_BACKGROUND_REFRESH_JOB_ID,
                                new ComponentName(
                                        context, SafetyCenterBackgroundRefreshJobService.class))
                        .setRequiresDeviceIdle(true)
                        .setRequiresCharging(true)
                        .setPeriodic(periodicBackgroundRefreshInterval.toMillis())
                        .build();

        Log.v(
                TAG,
                "Scheduling a periodic background refresh with interval="
                        + periodicBackgroundRefreshInterval);

        int scheduleResult = jobScheduler.schedule(jobInfo);
        if (scheduleResult != RESULT_SUCCESS) {
            Log.w(
                    TAG,
                    "Could not schedule the background refresh job, scheduleResult="
                            + scheduleResult);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        // background thread not required, PC APK makes all API calls in main thread
        if (!SafetyCenterJobServiceFlags.areBackgroundRefreshesEnabled()) {
            Log.i(TAG, "Background refreshes are not enabled, skipping job.");
            return false; // job is no longer running
        }
        SafetyCenterManager safetyCenterManager = this.getSystemService(SafetyCenterManager.class);
        if (safetyCenterManager == null) {
            Log.w(TAG, "Safety center manager is null, skipping job.");
            return false; // job is no longer running
        }
        if (!safetyCenterManager.isSafetyCenterEnabled()) {
            Log.i(TAG, "Safety center is not enabled, skipping job.");
            return false; // job is no longer running
        }

        Log.v(TAG, "Background refresh job has started.");
        safetyCenterManager.refreshSafetySources(getRefreshReason());
        return false; // job is no longer running
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false; // never want job to be rescheduled
    }

    private static boolean isActionStringValid(@Nullable String actionString) {
        return ACTION_BOOT_COMPLETED.equals(actionString)
                || ACTION_SAFETY_CENTER_ENABLED_CHANGED.equals(actionString);
    }

    private static int getRefreshReason() {
        if (SdkLevel.isAtLeastU()) {
            return REFRESH_REASON_PERIODIC;
        }
        return REFRESH_REASON_OTHER;
    }
}
