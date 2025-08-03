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
 * limitations under the License
 */

package android.jobscheduler.cts.jobtestapp;

import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.EXTRA_SET_NOTIFICATION_JOB_END_POLICY;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.EXTRA_SLOW_START;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.EXTRA_SLOW_STOP;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.PACKAGE_NAME;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TestJobService extends JobService {
    private static final String TAG = TestJobService.class.getSimpleName();
    public static final String ACTION_JOB_STARTED = PACKAGE_NAME + ".action.JOB_STARTED";
    public static final String ACTION_JOB_STOPPED = PACKAGE_NAME + ".action.JOB_STOPPED";
    public static final String JOB_PARAMS_EXTRA_KEY = PACKAGE_NAME + ".extra.JOB_PARAMETERS";
    public static final String JOB_PROC_STATE_KEY = PACKAGE_NAME + ".extra.PROC_STATE";
    public static final String JOB_CAPABILITIES_KEY = PACKAGE_NAME + ".extra.CAPABILITIES";
    public static final String JOB_OOM_SCORE_ADJ_KEY = PACKAGE_NAME + ".extra.OOM_SCORE_ADJ";

    // TODO: Move ProcessList.INVALID_ADJ to an app-accessible location and mark it @TestApi
    public static final int INVALID_ADJ = -10000; // ProcessList.INVALID_ADJ

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Test job executing: " + params.getJobId());
        final Bundle transientExtras = params.getTransientExtras();
        final Intent reportJobStartIntent = new Intent(ACTION_JOB_STARTED);
        reportJobStartIntent.putExtra(JOB_PARAMS_EXTRA_KEY, params);
        final boolean requestJobUidState = transientExtras != null
                ? transientExtras.getBoolean(EXTRA_REQUEST_JOB_UID_STATE) : false;
        if (requestJobUidState) {
            reportJobStartIntent.putExtra(EXTRA_REQUEST_JOB_UID_STATE, true);
            reportJobStartIntent.putExtras(getJobUidStateExtras());
        }
        sendBroadcast(reportJobStartIntent);
        if (transientExtras.getBoolean(EXTRA_SLOW_START)) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted sleeping for slow start");
            }
        }
        if (transientExtras.getBoolean(EXTRA_SET_NOTIFICATION)) {
            final NotificationManager notificationManager =
                    getSystemService(NotificationManager.class);
            final NotificationChannel channel =
                    new NotificationChannel(TAG, TAG, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            final Notification notification = new Notification.Builder(this, TAG)
                    .setContentTitle("Test")
                    .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                    .setContentText(TAG)
                    .build();
            setNotification(params, params.getJobId(), notification,
                    transientExtras.getInt(EXTRA_SET_NOTIFICATION_JOB_END_POLICY,
                            JobService.JOB_END_NOTIFICATION_POLICY_REMOVE));
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "Test job stopped executing: " + params.getJobId());
        final Intent reportJobStopIntent = new Intent(ACTION_JOB_STOPPED);
        reportJobStopIntent.putExtra(JOB_PARAMS_EXTRA_KEY, params);
        sendBroadcast(reportJobStopIntent);
        if (params.getTransientExtras().getBoolean(EXTRA_SLOW_STOP)) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted sleeping for slow stop");
            }
        }
        return true;
    }

    private Bundle getJobUidStateExtras() {
        final Bundle extras = new Bundle();
        extras.putInt(JOB_PROC_STATE_KEY, getProcState());
        extras.putInt(JOB_CAPABILITIES_KEY, getCapabilities());
        extras.putInt(JOB_OOM_SCORE_ADJ_KEY, getOomScoreAdj());
        return extras;
    }

    private int getProcState() {
        final ActivityManager activityManager = getSystemService(ActivityManager.class);
        return activityManager.getUidProcessState(Process.myUid());
    }

    private int getCapabilities() {
        final ActivityManager activityManager = getSystemService(ActivityManager.class);
        return activityManager.getUidProcessCapabilities(Process.myUid());
    }

    private int getOomScoreAdj() {
        try (BufferedReader reader = new BufferedReader(
                new FileReader("/proc/self/oom_score_adj"))) {
            return Integer.parseInt(reader.readLine().trim());
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "Error reading oom_score_adj", e);
            return INVALID_ADJ;
        }
    }
}
