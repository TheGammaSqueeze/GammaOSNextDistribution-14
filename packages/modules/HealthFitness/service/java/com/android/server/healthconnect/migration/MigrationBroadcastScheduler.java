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
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS;

import static com.android.server.healthconnect.migration.MigrationConstants.COUNT_DEFAULT;
import static com.android.server.healthconnect.migration.MigrationConstants.EXTRA_USER_ID;
import static com.android.server.healthconnect.migration.MigrationConstants.INTERVAL_DEFAULT;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.health.connect.Constants;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * This class schedules the {@link MigrationBroadcastJobService} service.
 *
 * @hide
 */
public final class MigrationBroadcastScheduler {

    private static final String TAG = "MigrationBroadcastScheduler";

    @VisibleForTesting
    static final String MIGRATION_BROADCAST_NAMESPACE = "HEALTH_CONNECT_MIGRATION_BROADCAST";

    private final Object mLock = new Object();
    private final HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager =
            HealthConnectDeviceConfigManager.getInitialisedInstance();

    @GuardedBy("mLock")
    private int mUserId;

    public MigrationBroadcastScheduler(int userId) {
        mUserId = userId;
    }

    /** Sets userId. Invoked when the user is switched. */
    public void setUserId(int userId) {
        synchronized (mLock) {
            mUserId = userId;
        }
    }

    /***
     * Cancels all previously scheduled {@link MigrationBroadcastJobService} service jobs.
     * Retrieves the requiredCount and requiredInterval corresponding to the given migration
     * state.
     * If the requiredInterval is greater than or equal to the minimum interval allowed for
     * periodic jobs, a periodic job is scheduled, else a set of non-periodic jobs are
     * pre-scheduled.
     */
    public void scheduleNewJobs(Context context) {
        synchronized (mLock) {
            int migrationState = MigrationStateManager.getInitialisedInstance().getMigrationState();

            if (Constants.DEBUG) {
                Slog.d(TAG, "Current migration state: " + migrationState);
                Slog.d(TAG, "Current user: " + mUserId);
            }

            Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                    .forNamespace(MIGRATION_BROADCAST_NAMESPACE)
                    .cancelAll();

            try {
                // When migration state is not in progress or allowed the count will be zero and no
                // job will be scheduled.
                if (getRequiredCount(migrationState) > 0) {
                    createJobLocked(
                            Math.max(
                                    getRequiredInterval(migrationState),
                                    JobInfo.getMinPeriodMillis()),
                            context);
                }
            } catch (Exception e) {
                Slog.e(TAG, "Exception while creating job : ", e);
            }
        }
    }

    /***
     * Creates a new {@link MigrationBroadcastJobService} job, to which it passes the user id in a
     * PersistableBundle object.
     *
     * @param requiredInterval Time interval between each successive job for that current
     *                         migration state
     * @param context Context
     *
     * @throws Exception if migration broadcast job scheduling fails.
     */
    @GuardedBy("mLock")
    private void createJobLocked(long requiredInterval, Context context) throws Exception {
        ComponentName schedulerServiceComponent =
                new ComponentName(context, MigrationBroadcastJobService.class);

        int uuid = UUID.randomUUID().toString().hashCode();
        int jobId = String.valueOf(mUserId + uuid).hashCode();

        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_USER_ID, mUserId);

        JobInfo.Builder builder =
                new JobInfo.Builder(jobId, schedulerServiceComponent).setExtras(extras);

        builder.setPeriodic(requiredInterval);

        JobScheduler jobScheduler =
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(MIGRATION_BROADCAST_NAMESPACE);
        int result = jobScheduler.schedule(builder.build());
        if (result == JobScheduler.RESULT_SUCCESS) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "Successfully scheduled migration broadcast job");
            }
        } else {
            throw new Exception("Failed to schedule migration broadcast job");
        }
    }

    /**
     * Returns the number of migration broadcast jobs to be scheduled for the given migration state.
     */
    @VisibleForTesting
    int getRequiredCount(int migrationState) {
        switch (migrationState) {
            case MIGRATION_STATE_IN_PROGRESS:
                return mHealthConnectDeviceConfigManager.getMigrationStateInProgressCount();
            case MIGRATION_STATE_ALLOWED:
                return mHealthConnectDeviceConfigManager.getMigrationStateAllowedCount();
            default:
                return COUNT_DEFAULT;
        }
    }

    /** Returns the interval between each migration broadcast job for the given migration state. */
    @VisibleForTesting
    long getRequiredInterval(int migrationState) {
        switch (migrationState) {
            case MIGRATION_STATE_IN_PROGRESS:
                return calculateRequiredInterval(
                        mHealthConnectDeviceConfigManager.getInProgressStateTimeoutPeriod(),
                        getRequiredCount(MIGRATION_STATE_IN_PROGRESS));
            case MIGRATION_STATE_ALLOWED:
                return calculateRequiredInterval(
                        mHealthConnectDeviceConfigManager.getNonIdleStateTimeoutPeriod(),
                        getRequiredCount(MIGRATION_STATE_ALLOWED));
            default:
                return INTERVAL_DEFAULT;
        }
    }

    private static long calculateRequiredInterval(Duration timeoutPeriod, int maxBroadcastCount) {
        return timeoutPeriod.toMillis() / maxBroadcastCount;
    }
}
