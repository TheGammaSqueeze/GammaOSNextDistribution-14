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

package com.android.server.healthconnect;

import static android.health.connect.Constants.DEFAULT_INT;

import static com.android.server.healthconnect.HealthConnectDailyJobs.HC_DAILY_JOB;
import static com.android.server.healthconnect.exportimport.ExportImportJobs.PERIODIC_EXPORT_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_COMPLETE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_PAUSE_JOB_NAME;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.health.connect.Constants;
import android.util.Slog;

import com.android.server.healthconnect.exportimport.ExportImportJobs;
import com.android.server.healthconnect.migration.MigrationStateChangeJob;

import java.util.Objects;

/**
 * Health Connect wrapper around JobService.
 *
 * @hide
 */
public class HealthConnectDailyService extends JobService {
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_JOB_NAME_KEY = "job_name";
    private static final String TAG = "HealthConnectDailyService";
    @UserIdInt private static volatile int sCurrentUserId;

    /**
     * Routes the job to the right place based on the job name, after performing common checks.,
     *
     * <p>Please handle exceptions for each task within the task. Do not crash the job as it might
     * result in failure of other tasks being triggered from the job.
     */
    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        int userId = params.getExtras().getInt(EXTRA_USER_ID, /* defaultValue= */ DEFAULT_INT);
        String jobName = params.getExtras().getString(EXTRA_JOB_NAME_KEY);
        if (userId == DEFAULT_INT || userId != sCurrentUserId) {
            // This job is no longer valid, the service for this user should have been stopped.
            // Just ignore this request in case we still got the request.
            return false;
        }

        if (Objects.isNull(jobName)) {
            return false;
        }

        // This service executes each incoming job on a Handler running on the application's
        // main thread. This means that we must offload the execution logic to background executor.
        switch (jobName) {
            case HC_DAILY_JOB:
                HealthConnectThreadScheduler.scheduleInternalTask(
                        () -> {
                            HealthConnectDailyJobs.execute(getApplicationContext(), params);
                            jobFinished(params, false);
                        });
                return true;
            case MIGRATION_COMPLETE_JOB_NAME:
                HealthConnectThreadScheduler.scheduleInternalTask(
                        () -> {
                            MigrationStateChangeJob.executeMigrationCompletionJob(
                                    getApplicationContext());
                            jobFinished(params, false);
                        });
                return true;
            case MIGRATION_PAUSE_JOB_NAME:
                HealthConnectThreadScheduler.scheduleInternalTask(
                        () -> {
                            MigrationStateChangeJob.executeMigrationPauseJob(
                                    getApplicationContext());
                            jobFinished(params, false);
                        });
                return true;
            case PERIODIC_EXPORT_JOB_NAME:
                HealthConnectThreadScheduler.scheduleInternalTask(
                        () -> {
                            ExportImportJobs.executePeriodicExportJob(getApplicationContext());
                            jobFinished(params, false);
                        });
                return true;
            default:
                Slog.w(TAG, "Job name " + jobName + " is not supported.");
                break;
        }
        return false;
    }

    /** Called when job needs to be stopped. Don't do anything here and let the job be killed. */
    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        return false;
    }

    /** Start periodically scheduling this service for {@code userId}. */
    public static void schedule(
            @NonNull JobScheduler jobScheduler, @UserIdInt int userId, @NonNull JobInfo jobInfo) {
        Objects.requireNonNull(jobScheduler);
        sCurrentUserId = userId;

        int result = jobScheduler.schedule(jobInfo);
        if (result != JobScheduler.RESULT_SUCCESS) {
            Slog.e(
                    TAG,
                    "Failed to schedule the job: "
                            + jobInfo.getExtras().getString(EXTRA_JOB_NAME_KEY));
        } else if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Scheduled a job successfully: "
                            + jobInfo.getExtras().getString(EXTRA_JOB_NAME_KEY));
        }
    }
}
