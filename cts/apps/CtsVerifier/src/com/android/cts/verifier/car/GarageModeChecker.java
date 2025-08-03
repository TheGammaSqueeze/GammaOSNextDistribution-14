/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.car;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import java.lang.ref.WeakReference;

public final class GarageModeChecker extends JobService {
    private static final String TAG = GarageModeChecker.class.getSimpleName();

    static final String PREFS_FILE_NAME = "GarageModeChecker_prefs";
    static final String PREFS_INITIATION = "test-initiation";
    static final String PREFS_GARAGE_MODE_START = "garage-mode-start";
    static final String PREFS_GARAGE_MODE_END = "garage-mode-end";
    static final String PREFS_TERMINATION = "termination-time";
    static final String PREFS_JOB_UPDATE = "job-update-time";
    static final String PREFS_HAD_CONNECTIVITY = "had-connectivity";
    static final String PREFS_START_BOOT_COUNT = "start-boot-count";

    static final int SECONDS_PER_ITERATION = 10;
    static final int MS_PER_ITERATION = SECONDS_PER_ITERATION * 1000;

    private static final int GARAGE_JOB_ID = GarageModeTestActivity.class.hashCode();
    private static final String JOB_NUMBER = "job_number";
    private static final String REMAINING_SECONDS = "remaining_seconds";
    // JobScheduler allows a maximum of 10 minutes for a job, but depending on vendor implementation
    // Garage Mode may not last that long. So, max job duration is set to 60 seconds.
    private static final int MAX_SECONDS_PER_JOB = 60;

    private static final int MSG_FINISHED = 0;
    private static final int MSG_RUN_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;

    private Context mIdleJobContext;

    private boolean mReportFirstExecution = true;

    public static void scheduleAnIdleJob(Context context, int durationSeconds) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        // Get the next available job ID
        int highestJobNumber = 0;
        for (JobInfo jobInfo : jobScheduler.getAllPendingJobs()) {
            int jobId = jobInfo.getId();
            if (highestJobNumber < jobId) {
                highestJobNumber = jobId;
            }
        }
        scheduleJob(context, highestJobNumber + 1, durationSeconds);
    }

    private static void scheduleJob(Context context, int jobNumber, int durationSeconds) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        ComponentName jobComponentName = new ComponentName(context, GarageModeChecker.class);
        JobInfo.Builder builder = new JobInfo.Builder(jobNumber, jobComponentName);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(JOB_NUMBER, jobNumber);
        bundle.putInt(REMAINING_SECONDS, durationSeconds);

        JobInfo jobInfo = builder
                .setRequiresDeviceIdle(true)
                .setExtras(bundle)
                .build();
        jobScheduler.schedule(jobInfo);
    }

    private final Handler mHandler = new Handler() {
        private SparseArray<JobParameters> mTaskMap = new SparseArray<>();
        private boolean mHaveContinuousConnectivity = true; // Assume true initially

        @Override
        public void handleMessage(Message msg) {
            JobParameters job = (JobParameters) msg.obj;
            switch (msg.what) {
                case MSG_FINISHED:
                    mTaskMap.remove(job.getJobId());
                    jobFinished(job, false);
                    break;
                case MSG_RUN_JOB:
                    checkConnectivity(msg.arg1);
                    GarageModeCheckerTask task = new GarageModeCheckerTask(this, job, msg.arg1);
                    task.execute();
                    mTaskMap.put(job.getJobId(), job);
                    break;
                case MSG_CANCEL_JOB:
                    JobParameters runningJob = mTaskMap.get(job.getJobId());
                    if (runningJob != null) {
                        removeMessages(MSG_RUN_JOB, runningJob);
                        mTaskMap.remove(job.getJobId());
                    }
                    SharedPreferences prefs = mIdleJobContext.getSharedPreferences(PREFS_FILE_NAME,
                            Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(PREFS_TERMINATION, System.currentTimeMillis());
                    editor.commit();
                    Log.v(TAG, "Idle job was terminated");
                    break;
            }
        }

        private void checkConnectivity(int iteration) {
            if (mHaveContinuousConnectivity) {
                // Check if we still have internet connectivity
                NetworkInfo networkInfo = getApplicationContext()
                        .getSystemService(ConnectivityManager.class).getActiveNetworkInfo();
                mHaveContinuousConnectivity = networkInfo != null
                        && networkInfo.isAvailable() && networkInfo.isConnected();
                if (iteration == 0 || !mHaveContinuousConnectivity) {
                    // Save the connectivity state on the first pass and
                    // the first time we lose connectivity
                    SharedPreferences prefs = mIdleJobContext.getSharedPreferences(PREFS_FILE_NAME,
                            Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(PREFS_HAD_CONNECTIVITY, mHaveContinuousConnectivity);
                    editor.commit();
                }
            }
        }
    };

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Message msg = mHandler.obtainMessage(MSG_CANCEL_JOB, 0, 0, jobParameters);
        mHandler.sendMessage(msg);
        return false;
    }

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        mIdleJobContext = getApplicationContext();
        if (mReportFirstExecution) {
            mReportFirstExecution = false;
            // Remember the start time
            SharedPreferences prefs = mIdleJobContext.getSharedPreferences(PREFS_FILE_NAME,
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(PREFS_GARAGE_MODE_START, System.currentTimeMillis());
            editor.commit();
            Log.v(TAG, "Starting idle job");
        }
        Message msg = mHandler.obtainMessage(MSG_RUN_JOB, 0, 0, jobParameters);
        mHandler.sendMessage(msg);
        return true;
    }

    static int getBootCount(Context context) {
        int bootCount = 0;
        try {
            bootCount = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BOOT_COUNT);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Could not get Settings.Global.BOOT_COUNT: ", e);
        }
        return bootCount;
    }

    private final class GarageModeCheckerTask extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<Handler> mHandler;
        private final JobParameters mJobParameter;
        private final int mIteration;

        GarageModeCheckerTask(Handler handler, JobParameters jobParameters, int iteration) {
            mHandler = new WeakReference<Handler>(handler);
            mJobParameter = jobParameters;
            mIteration = iteration;
        }

        @Override
        protected Boolean doInBackground(Void... infos) {
            int remainingSeconds = mJobParameter.getExtras().getInt(REMAINING_SECONDS);
            int myMaxTime = Math.min(remainingSeconds, MAX_SECONDS_PER_JOB);
            int elapsedSeconds = SECONDS_PER_ITERATION * mIteration;
            long now = System.currentTimeMillis();
            SharedPreferences prefs = mIdleJobContext.getSharedPreferences(PREFS_FILE_NAME,
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = null;

            if (elapsedSeconds >= myMaxTime + SECONDS_PER_ITERATION) {
                // This job is done
                if (myMaxTime == remainingSeconds) {
                    // This is the final job. Note the completion time.
                    editor = prefs.edit();
                    editor.putLong(PREFS_GARAGE_MODE_END, now);
                    editor.commit();
                    Log.v(TAG, "Idle job is finished");
                }
                return false;
            }

            editor = prefs.edit();
            editor.putLong(PREFS_JOB_UPDATE, now);
            editor.commit();
            if (elapsedSeconds >= myMaxTime && (myMaxTime < remainingSeconds)) {
                // This job is about to finish and there is more time remaining.
                // Schedule another job.
                scheduleJob(mIdleJobContext, mJobParameter.getJobId() + 1,
                        remainingSeconds - elapsedSeconds);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            final Handler handler = mHandler.get();
            if (handler == null) {
                return;
            }
            if (result) {
                Message msg = handler.obtainMessage(MSG_RUN_JOB, mIteration + 1, 0, mJobParameter);
                handler.sendMessageDelayed(msg, MS_PER_ITERATION);
            } else {
                Message msg = handler.obtainMessage(MSG_FINISHED, 0, 0, mJobParameter);
                handler.sendMessage(msg);
            }
        }
    }
}
