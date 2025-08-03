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

package android.adservices.rootcts;

import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Assume;

import java.util.Objects;

class BackgroundJobHelper {

    private static final String TAG = "BackgroundJobHelper";
    private static final String PACKAGE = "com.google.android.adservices.api";
    private static final long MAX_TIMEOUT_MS = 16000;

    private final JobScheduler mJobScheduler;

    BackgroundJobHelper(Context context) {
        mJobScheduler = context.getSystemService(JobScheduler.class);
        // ExtServices has a different package name for U. Currently only run on S+.
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
    }

    /**
     * Run an adservices background job with the given job ID.
     *
     * @param jobId ID of the job to run.
     * @return false if the job didn't schedule or didn't complete on time.
     */
    boolean runJob(int jobId) throws InterruptedException {
        runScheduleJobCommand(jobId);
        if (isJobPending(jobId)) {
            // If the job wasn't immediately scheduled then something has gone wrong.
            Log.d(TAG, String.format("rootcts: Job with id %s did not schedule!", jobId));
            return false;
        }

        // Check job is present in the queue for at least some time, with exponential backoff.
        long timeout = 500;
        while (timeout < MAX_TIMEOUT_MS) {
            Thread.sleep(timeout);
            if (!isJobPending(jobId)) {
                Log.d(TAG, String.format("rootcts: Job with id %s did schedule.", jobId));
                return true;
            }
            timeout *= 2;
        }

        Log.d(TAG, String.format("rootcts: Job with id %s scheduled but timed out!", jobId));
        return false;
    }

    private void runScheduleJobCommand(int jobId) {
        ShellUtils.runShellCommand("cmd jobscheduler run --force %s %s", PACKAGE, jobId);
    }

    private boolean isJobPending(int jobId) {
        return !Objects.isNull(mJobScheduler.getPendingJob(jobId));
    }
}
