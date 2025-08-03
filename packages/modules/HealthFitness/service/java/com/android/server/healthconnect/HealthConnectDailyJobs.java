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

import static com.android.server.healthconnect.HealthConnectDailyService.EXTRA_JOB_NAME_KEY;
import static com.android.server.healthconnect.HealthConnectDailyService.EXTRA_USER_ID;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.UserHandle;

import com.android.server.healthconnect.logging.DailyLoggingService;
import com.android.server.healthconnect.storage.AutoDeleteService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** @hide */
public class HealthConnectDailyJobs {
    public static final String HC_DAILY_JOB = "hc_daily_job";
    private static final int MIN_JOB_ID = HealthConnectDailyJobs.class.hashCode();
    private static final long JOB_RUN_INTERVAL = TimeUnit.DAYS.toMillis(1);
    private static final String HEALTH_CONNECT_NAMESPACE = "HEALTH_CONNECT_DAILY_JOB";

    public static void schedule(@NonNull Context context, @UserIdInt int userId) {
        ComponentName componentName = new ComponentName(context, HealthConnectDailyService.class);
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_USER_ID, userId);
        extras.putString(EXTRA_JOB_NAME_KEY, HC_DAILY_JOB);
        JobInfo.Builder builder =
                new JobInfo.Builder(MIN_JOB_ID + userId, componentName)
                        .setExtras(extras)
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(JOB_RUN_INTERVAL, JOB_RUN_INTERVAL / 2);

        HealthConnectDailyService.schedule(
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(HEALTH_CONNECT_NAMESPACE),
                userId,
                builder.build());
    }

    public static void cancelAllJobs(Context context) {
        Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                .forNamespace(HEALTH_CONNECT_NAMESPACE)
                .cancelAll();
    }

    public static void execute(@NonNull Context context, JobParameters params) {
        int userId = params.getExtras().getInt(EXTRA_USER_ID, /* defaultValue= */ DEFAULT_INT);
        AutoDeleteService.startAutoDelete(context);
        DailyLoggingService.logDailyMetrics(context, UserHandle.getUserHandleForUid(userId));
    }
}
