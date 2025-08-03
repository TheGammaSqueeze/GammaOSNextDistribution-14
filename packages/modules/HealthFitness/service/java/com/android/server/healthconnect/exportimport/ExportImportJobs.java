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

package com.android.server.healthconnect.exportimport;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.HealthConnectDailyService;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines jobs related to Health Connect export and imports.
 *
 * @hide
 */
public class ExportImportJobs {
    private static final int MIN_JOB_ID = ExportImportJobs.class.hashCode();

    @VisibleForTesting static final String NAMESPACE = "HEALTH_CONNECT_IMPORT_EXPORT_JOBS";

    public static final String PERIODIC_EXPORT_JOB_NAME = "periodic_export_job";

    /** Schedule the periodic export job. */
    public static void schedulePeriodicExportJob(Context context, int userId) {
        ComponentName componentName = new ComponentName(context, HealthConnectDailyService.class);
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(HealthConnectDailyService.EXTRA_USER_ID, userId);
        extras.putString(HealthConnectDailyService.EXTRA_JOB_NAME_KEY, PERIODIC_EXPORT_JOB_NAME);
        JobInfo.Builder builder =
                new JobInfo.Builder(MIN_JOB_ID + userId, componentName)
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        // TODO(b/325599089): Fetch duration from export settings.
                        .setPeriodic(Duration.ofDays(1).toMillis(), Duration.ofHours(4).toMillis())
                        .setExtras(extras);

        HealthConnectDailyService.schedule(
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(NAMESPACE),
                userId,
                builder.build());
    }

    /** Execute the periodic export job. */
    public static void executePeriodicExportJob(@NonNull Context context) {
        // TODO(b/325599089): Check if export is enabled, and call ExportManager.export().

        // TODO(b/325599089): Do we need an additional periodic / one-off task to make sure a single
        //  export completes? We need to test if JobScheduler will call the job again if jobFinished
        //  is never called.

        // TODO(b/325599089): Consider if we need to do any checkpointing here in case the job
        //  doesn't complete and we need to pick it up again.
    }
}
