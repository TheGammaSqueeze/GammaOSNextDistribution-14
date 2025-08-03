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

package com.android.server.healthconnect.migration;

import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_ALLOWED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_COMPLETE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS;

import static com.android.server.healthconnect.HealthConnectDailyService.EXTRA_JOB_NAME_KEY;
import static com.android.server.healthconnect.HealthConnectDailyService.EXTRA_USER_ID;
import static com.android.server.healthconnect.migration.MigrationConstants.CURRENT_STATE_START_TIME_KEY;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_COMPLETE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_PAUSE_JOB_NAME;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_STATE_CHANGE_NAMESPACE;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import com.android.server.healthconnect.HealthConnectDailyService;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A state-change jobs scheduler and executor. Schedules migration completion job to run daily, and
 * migration pause job to run every 4 hours
 *
 * @hide
 */
public final class MigrationStateChangeJob {
    static final int MIN_JOB_ID = MigrationStateChangeJob.class.hashCode();

    public static void scheduleMigrationCompletionJob(Context context, int userId) {
        HealthConnectDeviceConfigManager deviceConfigManager =
                HealthConnectDeviceConfigManager.getInitialisedInstance();
        if (!deviceConfigManager.isCompleteStateChangeJobEnabled()) {
            return;
        }
        ComponentName componentName = new ComponentName(context, HealthConnectDailyService.class);
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_USER_ID, userId);
        extras.putString(EXTRA_JOB_NAME_KEY, MIGRATION_COMPLETE_JOB_NAME);
        JobInfo.Builder builder =
                new JobInfo.Builder(MIN_JOB_ID + userId, componentName)
                        .setPeriodic(deviceConfigManager.getMigrationCompletionJobRunInterval())
                        .setExtras(extras);

        HealthConnectDailyService.schedule(
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(MIGRATION_STATE_CHANGE_NAMESPACE),
                userId,
                builder.build());
    }

    public static void scheduleMigrationPauseJob(Context context, int userId) {
        HealthConnectDeviceConfigManager deviceConfigManager =
                HealthConnectDeviceConfigManager.getInitialisedInstance();
        if (!deviceConfigManager.isPauseStateChangeJobEnabled()) {
            return;
        }
        ComponentName componentName = new ComponentName(context, HealthConnectDailyService.class);
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_USER_ID, userId);
        extras.putString(EXTRA_JOB_NAME_KEY, MIGRATION_PAUSE_JOB_NAME);
        JobInfo.Builder builder =
                new JobInfo.Builder(MIN_JOB_ID + userId, componentName)
                        .setPeriodic(deviceConfigManager.getMigrationPauseJobRunInterval())
                        .setExtras(extras);
        HealthConnectDailyService.schedule(
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(MIGRATION_STATE_CHANGE_NAMESPACE),
                userId,
                builder.build());
    }

    /** Execute migration completion job */
    public static void executeMigrationCompletionJob(@NonNull Context context) {
        HealthConnectDeviceConfigManager deviceConfigManager =
                HealthConnectDeviceConfigManager.getInitialisedInstance();
        if (!deviceConfigManager.isCompleteStateChangeJobEnabled()) {
            return;
        }
        if (MigrationStateManager.getInitialisedInstance().getMigrationState()
                == MIGRATION_STATE_COMPLETE) {
            return;
        }
        PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();

        String currentStateStartTime = preferenceHelper.getPreference(CURRENT_STATE_START_TIME_KEY);

        // This is a fallback but should never happen.
        if (Objects.isNull(currentStateStartTime)) {
            preferenceHelper.insertOrReplacePreference(
                    CURRENT_STATE_START_TIME_KEY, Instant.now().toString());
            return;
        }
        Instant executionTime =
                Instant.parse(currentStateStartTime)
                        .plusMillis(
                                MigrationStateManager.getInitialisedInstance().getMigrationState()
                                                == MIGRATION_STATE_IDLE
                                        ? deviceConfigManager.getIdleStateTimeoutPeriod().toMillis()
                                        : deviceConfigManager
                                                .getNonIdleStateTimeoutPeriod()
                                                .toMillis())
                        .minusMillis(deviceConfigManager.getExecutionTimeBuffer());

        if (MigrationStateManager.getInitialisedInstance().getMigrationState()
                        == MIGRATION_STATE_ALLOWED
                || MigrationStateManager.getInitialisedInstance().getMigrationState()
                        == MIGRATION_STATE_IN_PROGRESS) {
            String allowedStateTimeout =
                    MigrationStateManager.getInitialisedInstance().getAllowedStateTimeout();
            if (!Objects.isNull(allowedStateTimeout)) {
                Instant parsedAllowedStateTimeout =
                        Instant.parse(allowedStateTimeout)
                                .minusMillis(deviceConfigManager.getExecutionTimeBuffer());
                executionTime =
                        executionTime.isAfter(parsedAllowedStateTimeout)
                                ? parsedAllowedStateTimeout
                                : executionTime;
            }
        }

        if (Instant.now().isAfter(executionTime)) {
            // TODO (b/278728774) fix race condition
            MigrationStateManager.getInitialisedInstance()
                    .updateMigrationState(context, MIGRATION_STATE_COMPLETE, true);
        }
    }

    /** Execute migration pausing job. */
    public static void executeMigrationPauseJob(@NonNull Context context) {
        HealthConnectDeviceConfigManager deviceConfigManager =
                HealthConnectDeviceConfigManager.getInitialisedInstance();
        if (!deviceConfigManager.isPauseStateChangeJobEnabled()) {
            return;
        }
        if (MigrationStateManager.getInitialisedInstance().getMigrationState()
                != MIGRATION_STATE_IN_PROGRESS) {
            return;
        }
        PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
        String currentStateStartTime = preferenceHelper.getPreference(CURRENT_STATE_START_TIME_KEY);
        // This is a fallback but should never happen.
        if (Objects.isNull(currentStateStartTime)) {
            preferenceHelper.insertOrReplacePreference(
                    CURRENT_STATE_START_TIME_KEY, Instant.now().toString());
            return;
        }

        Instant executionTime =
                Instant.parse(currentStateStartTime)
                        .plusMillis(
                                deviceConfigManager.getInProgressStateTimeoutPeriod().toMillis())
                        .minusMillis(deviceConfigManager.getExecutionTimeBuffer());

        if (Instant.now().isAfter(executionTime)) {
            // If we move to ALLOWED from IN_PROGRESS, then we have reached the IN_PROGRESS_TIMEOUT
            MigrationStateManager.getInitialisedInstance()
                    .updateMigrationState(
                            context, MIGRATION_STATE_ALLOWED, /* timeoutReached= */ true);
        }
    }

    public static boolean existsAStateChangeJob(@NonNull Context context, @NonNull String jobName) {
        JobScheduler jobScheduler =
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(MIGRATION_STATE_CHANGE_NAMESPACE);
        List<JobInfo> allJobs = jobScheduler.getAllPendingJobs();
        for (JobInfo job : allJobs) {
            if (jobName.equals(job.getExtras().getString(EXTRA_JOB_NAME_KEY))) {
                return true;
            }
        }
        return false;
    }

    public static void cancelAllJobs(@NonNull Context context) {
        JobScheduler jobScheduler =
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(MIGRATION_STATE_CHANGE_NAMESPACE);
        jobScheduler.getAllPendingJobs().forEach(jobInfo -> jobScheduler.cancel(jobInfo.getId()));
    }
}
