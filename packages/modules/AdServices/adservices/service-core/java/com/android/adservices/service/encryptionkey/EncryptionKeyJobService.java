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

package com.android.adservices.service.encryptionkey;

import static com.android.adservices.spe.AdServicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/** Class schedules a periodic job to check all encryption keys in table and fetch updated keys. */
public class EncryptionKeyJobService extends JobService {

    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();
    private static final int ENCRYPTION_KEY_JOB_ID = ENCRYPTION_KEY_PERIODIC_JOB.getJobId();

    // This Future should only be accessed on the main thread.
    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling EncryptionKeyJobService job because it's running in "
                            + "ExtServices on T+");
            return skipAndCancelBackgroundJob(params);
        }

        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(ENCRYPTION_KEY_JOB_ID);

        if (FlagsFactory.getFlags().getEncryptionKeyPeriodicFetchKillSwitch()) {
            LogUtil.e(
                    "Encryption key fetch job service is disabled, skipping and cancelling"
                            + " EncryptionKeyJobService");
            return skipAndCancelBackgroundJob(params);
        }

        LogUtil.d("EncryptionKeyJobService.onStartJob");
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            fetchAndUpdateEncryptionKeys();
                            AdServicesJobServiceLogger.getInstance(EncryptionKeyJobService.this)
                                    .recordJobFinished(
                                            ENCRYPTION_KEY_JOB_ID,
                                            /* isSuccessful */ true,
                                            /* shouldRetry*/ false);
                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("EncryptionKeyJobService.onStopJob");

        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, ENCRYPTION_KEY_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /**
     * Schedule Encryption key fetch job if it is not already scheduled.
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static boolean scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (FlagsFactory.getFlags().getEncryptionKeyPeriodicFetchKillSwitch()) {
            LogUtil.e("Encryption key fetch job is disabled, skip scheduling.");
            return false;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("Cannot fetch Job Scheduler.");
            return false;
        }

        final JobInfo scheduledJob = jobScheduler.getPendingJob(ENCRYPTION_KEY_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling.
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LogUtil.d("Scheduled EncryptionKeyJobService.");
            return true;
        } else {
            LogUtil.d("EncryptionKeyJobService already scheduled, skipping reschedule.");
            return false;
        }
    }

    /** Fetch encryption keys or update expired encryption keys. */
    @VisibleForTesting
    public void fetchAndUpdateEncryptionKeys() {
        EncryptionKeyDao encryptionKeyDao = EncryptionKeyDao.getInstance(getApplicationContext());
        EnrollmentDao enrollmentDao = EnrollmentDao.getInstance(getApplicationContext());
        EncryptionKeyJobHandler encryptionKeyJobHandler =
                new EncryptionKeyJobHandler(
                        encryptionKeyDao,
                        enrollmentDao,
                        new EncryptionKeyFetcher(FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB));
        encryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();
    }

    /**
     * Schedule Encryption key fetch job.
     *
     * @param jobScheduler the jobScheduler
     * @param jobInfo the jobInfo for this job
     */
    @VisibleForTesting
    public static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        ENCRYPTION_KEY_JOB_ID,
                        new ComponentName(context, EncryptionKeyJobService.class))
                .setRequiredNetworkType(flags.getEncryptionKeyJobRequiredNetworkType())
                .setPeriodic(flags.getEncryptionKeyJobPeriodMs())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(ENCRYPTION_KEY_JOB_ID);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, /* wantsReschedule= */ false);

        // Returning false means that this job has completed its work.
        return false;
    }

    @VisibleForTesting
    public Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
