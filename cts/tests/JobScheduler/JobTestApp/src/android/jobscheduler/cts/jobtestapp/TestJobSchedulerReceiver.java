/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.jobscheduler.cts.jobtestapp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

/**
 * Schedules jobs for this package but does not, by itself, occupy a foreground uid state
 * while doing so.
 */
public class TestJobSchedulerReceiver extends BroadcastReceiver {
    private static final String TAG = TestJobSchedulerReceiver.class.getSimpleName();
    static final String PACKAGE_NAME = "android.jobscheduler.cts.jobtestapp";
    private static final String NOTIFICATION_CHANNEL_ID = TAG + "_channel";

    public static final long NO_DEADLINE = -1L;
    // Don't use JobInfo.PRIORITY_DEFAULT because that may cause issues if the job is meant to be
    // expedited or user-initiated.
    public static final int NO_PRIORITY = -1;

    public static final String ACTION_JOB_SCHEDULE_RESULT =
            PACKAGE_NAME + ".action.SCHEDULE_RESULT";
    public static final String EXTRA_SCHEDULE_RESULT = PACKAGE_NAME + ".extra.SCHEDULE_RESULT";

    public static final String EXTRA_JOB_ID_KEY = PACKAGE_NAME + ".extra.JOB_ID";
    public static final String EXTRA_ALLOW_IN_IDLE = PACKAGE_NAME + ".extra.ALLOW_IN_IDLE";
    public static final String EXTRA_DEADLINE = PACKAGE_NAME + ".extra.DEADLINE";
    public static final String EXTRA_REQUIRED_NETWORK_TYPE =
            PACKAGE_NAME + ".extra.REQUIRED_NETWORK_TYPE";
    public static final String EXTRA_REQUIRES_STORAGE_NOT_LOW =
            PACKAGE_NAME + ".extra.REQUIRES_STORAGE_NOT_LOW";
    public static final String EXTRA_AS_EXPEDITED = PACKAGE_NAME + ".extra.AS_EXPEDITED";
    public static final String EXTRA_AS_USER_INITIATED = PACKAGE_NAME + ".extra.AS_USER_INITIATED";
    public static final String EXTRA_PRIORITY = ".extra.PRIORITY";
    public static final String EXTRA_REQUEST_JOB_UID_STATE =
            PACKAGE_NAME + ".extra.REQUEST_JOB_UID_STATE";
    public static final String EXTRA_SET_NOTIFICATION = PACKAGE_NAME + ".extra.SET_NOTIFICATION";
    public static final String EXTRA_SET_NOTIFICATION_JOB_END_POLICY =
            PACKAGE_NAME + ".extra.SET_NOTIFICATION_JOB_END_POLICY";
    public static final String EXTRA_SLOW_START = PACKAGE_NAME + ".extra.SLOW_START";
    public static final String EXTRA_SLOW_STOP = PACKAGE_NAME + ".extra.SLOW_STOP";
    public static final String ACTION_SCHEDULE_JOB = PACKAGE_NAME + ".action.SCHEDULE_JOB";
    public static final String ACTION_CANCEL_JOBS = PACKAGE_NAME + ".action.CANCEL_JOBS";
    public static final String ACTION_POST_UI_INITIATING_NOTIFICATION =
            PACKAGE_NAME + ".action.POST_UI_INITIATING_NOTIFICATION";
    public static final String ACTION_SCHEDULE_FGS_START_ALARM =
            PACKAGE_NAME + ".action.SCHEDULE_FGS_START_ALARM";
    public static final String ACTION_START_FGS = PACKAGE_NAME + ".action.START_FGS";
    public static final String ACTION_NOTIFICATION_POSTED =
            PACKAGE_NAME + ".action.NOTIFICATION_POSTED";
    public static final String ACTION_ALARM_SCHEDULED =
            PACKAGE_NAME + ".action.ALARM_SCHEDULED";
    public static final int JOB_INITIAL_BACKOFF = 10_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Processing intent: " + intent.getAction());
        final ComponentName jobServiceComponent = new ComponentName(context, TestJobService.class);
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        switch (intent.getAction()) {
            case ACTION_CANCEL_JOBS:
                jobScheduler.cancelAll();
                Log.d(TAG, "Cancelled all jobs for " + context.getPackageName());
                break;
            case ACTION_SCHEDULE_JOB:
                final int jobId = intent.getIntExtra(EXTRA_JOB_ID_KEY, hashCode());
                final boolean allowInIdle = intent.getBooleanExtra(EXTRA_ALLOW_IN_IDLE, false);
                final int networkType =
                        intent.getIntExtra(EXTRA_REQUIRED_NETWORK_TYPE, JobInfo.NETWORK_TYPE_NONE);
                final boolean expedited = intent.getBooleanExtra(EXTRA_AS_EXPEDITED, false);
                final boolean storageNotLow =
                        intent.getBooleanExtra(EXTRA_REQUIRES_STORAGE_NOT_LOW, false);
                final long deadline = intent.getLongExtra(EXTRA_DEADLINE, NO_DEADLINE);
                final int priority = intent.getIntExtra(EXTRA_PRIORITY, NO_PRIORITY);
                final boolean userInitiated =
                        intent.getBooleanExtra(EXTRA_AS_USER_INITIATED, false);
                final int backoffPolicy = userInitiated ? JobInfo.BACKOFF_POLICY_EXPONENTIAL
                                                        : JobInfo.BACKOFF_POLICY_LINEAR;
                final boolean requestJobUidState = intent.getBooleanExtra(
                        EXTRA_REQUEST_JOB_UID_STATE, false);
                final Bundle extras = new Bundle();
                extras.putBoolean(EXTRA_REQUEST_JOB_UID_STATE, requestJobUidState);
                extras.putBoolean(EXTRA_SET_NOTIFICATION,
                        intent.getBooleanExtra(EXTRA_SET_NOTIFICATION, false));
                extras.putInt(EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                        intent.getIntExtra(EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE));
                extras.putBoolean(EXTRA_SLOW_START,
                        intent.getBooleanExtra(EXTRA_SLOW_START, false));
                extras.putBoolean(EXTRA_SLOW_STOP, intent.getBooleanExtra(EXTRA_SLOW_STOP, false));
                JobInfo.Builder jobBuilder = new JobInfo.Builder(jobId, jobServiceComponent)
                        .setBackoffCriteria(JOB_INITIAL_BACKOFF, backoffPolicy)
                        .setTransientExtras(extras)
                        .setImportantWhileForeground(allowInIdle)
                        .setExpedited(expedited)
                        .setUserInitiated(userInitiated)
                        .setRequiredNetworkType(networkType)
                        .setRequiresStorageNotLow(storageNotLow);
                if (deadline != NO_DEADLINE) {
                    jobBuilder.setOverrideDeadline(deadline);
                }
                if (priority != NO_PRIORITY) {
                    jobBuilder.setPriority(priority);
                }
                final int result = jobScheduler.schedule(jobBuilder.build());
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.e(TAG, "Could not schedule job " + jobId);
                } else {
                    Log.d(TAG, "Successfully scheduled job with id " + jobId);
                }

                final Intent scheduleJobResultIntent = new Intent(ACTION_JOB_SCHEDULE_RESULT);
                scheduleJobResultIntent.putExtra(EXTRA_JOB_ID_KEY, jobId);
                scheduleJobResultIntent.putExtra(EXTRA_SCHEDULE_RESULT, result);
                context.sendBroadcast(scheduleJobResultIntent);
                break;
            case ACTION_POST_UI_INITIATING_NOTIFICATION:
                NotificationManager notificationManager =
                        context.getSystemService(NotificationManager.class);
                NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                        TestJobSchedulerReceiver.class.getSimpleName(),
                        NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);

                final Intent scheduleJobIntent = new Intent(intent);
                scheduleJobIntent.setAction(ACTION_SCHEDULE_JOB);

                final PendingIntent scheduleJobPendingIntent = PendingIntent.getBroadcast(
                        context, 1, scheduleJobIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification notification =
                        new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                .setContentTitle("Test title")
                                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                                .setContentText("Click to start UI job!")
                                .setContentIntent(scheduleJobPendingIntent)
                                .build();
                notificationManager.notify(0, notification);

                final Intent completionIntent = new Intent(ACTION_NOTIFICATION_POSTED);
                context.sendBroadcast(completionIntent);
                break;
            case ACTION_SCHEDULE_FGS_START_ALARM: {
                final Intent startFgsIntent = new Intent(ACTION_START_FGS);
                ComponentName testComponentName = new ComponentName(context, TestFgsService.class);
                startFgsIntent.setComponent(testComponentName);

                PendingIntent startFgsPendingIntent = PendingIntent.getForegroundService(
                        context, 2, startFgsIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime(), // Get the alarm to fire immediately
                        startFgsPendingIntent);

                final Intent alarmScheduledIntent = new Intent(ACTION_ALARM_SCHEDULED);
                context.sendBroadcast(alarmScheduledIntent);
                break;
            }
            case ACTION_START_FGS: {
                final Intent startFgsIntent = new Intent();
                ComponentName testComponentName = new ComponentName(context, TestFgsService.class);
                startFgsIntent.setComponent(testComponentName);

                context.startForegroundService(startFgsIntent);
                break;
            }
            default:
                Log.e(TAG, "Unknown action " + intent.getAction());
        }
    }
}
