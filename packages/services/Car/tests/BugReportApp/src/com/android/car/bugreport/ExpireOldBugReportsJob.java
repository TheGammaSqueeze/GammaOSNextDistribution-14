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

package com.android.car.bugreport;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Marks the old bugreport entries {@link Status#STATUS_EXPIRED} and deletes associated zip files.
 * Bug report is considered old if it has 0 TTL points, which is decremented every system boot by
 * {@link TtlPointsDecremental}.
 *
 * <p>This job will be executed in garage-mode, see
 * {@link JobSchedulingUtils#scheduleExpireOldBugReportsJobInGarageMode}.
 */
public class ExpireOldBugReportsJob extends JobService {
    private final AtomicBoolean mIsCancelled = new AtomicBoolean(false);
    private ExecutorService mExecutorService;

    @Override
    public void onCreate() {
        super.onCreate();

        mExecutorService = Executors.newSingleThreadExecutor();
    }

    /**
     * @return {@code true} if your service will continue running, using a separate thread.
     */
    @Override
    public boolean onStartJob(JobParameters params) {
        if (!Config.isBugReportEnabled()) {
            return false;
        }
        mIsCancelled.set(false);
        mExecutorService.submit(() -> run(params));
        return true;
    }

    /**
     * @return {@code true} to indicate to the JobManager whether you'd like to reschedule
     * this job based on the retry criteria provided at job creation-time; or {@code false}
     * to end the job entirely. Regardless of the value returned, your job must stop executing.
     */
    @Override
    public boolean onStopJob(JobParameters params) {
        mIsCancelled.set(true);
        return false;
    }

    /** Deletes old bugreports. Must be run in a separate thread. */
    private void run(JobParameters params) {
        List<MetaBugReport> bugReportsWithZeroTtlPoints =
                BugStorageUtils.getUnexpiredBugReportsWithZipFile(
                        this, /* ttlPointsReachedZero= */ true);
        for (MetaBugReport bug : bugReportsWithZeroTtlPoints) {
            if (mIsCancelled.get()) {
                break;
            }
            BugStorageUtils.expireBugReport(this, bug.getId());
        }
        jobFinished(params, /* wantsReschedule= */ false);
    }
}
