/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.app.usage.cts;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.function.Supplier;

public final class TestJob extends JobService {

    public static final int TEST_JOB_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = TestJob.class.getSimpleName();
    private static boolean sJobStarted;
    public static Supplier<Boolean> hasJobStarted = () -> sJobStarted;

    @Override
    public boolean onStartJob(JobParameters params) {
        sJobStarted = true;
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    public static void schedule(Context context) {
        sJobStarted = false;
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        ComponentName componentName = new ComponentName(context, TestJob.class);

        JobInfo jobInfo = new JobInfo.Builder(TEST_JOB_ID, componentName).build();
        jobScheduler.schedule(jobInfo);
    }
}
