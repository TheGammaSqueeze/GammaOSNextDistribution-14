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

import static com.android.server.healthconnect.migration.MigrationConstants.EXTRA_USER_ID;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.healthconnect.HealthConnectThreadScheduler;

/**
 * A service that sends migration broadcast to migration aware apps.
 *
 * @hide
 */
public class MigrationBroadcastJobService extends JobService {

    private static final String TAG = "MigrationBroadcastJobService";

    /** Called every time the operation corresponding to this service is to be performed. */
    @Override
    public boolean onStartJob(JobParameters params) {
        try {
            Context context = getApplicationContext();

            PersistableBundle extras = params.getExtras();
            int userId = extras.getInt(EXTRA_USER_ID);

            HealthConnectThreadScheduler.scheduleInternalTask(
                    () -> {
                        try {
                            MigrationBroadcast migrationBroadcast =
                                    new MigrationBroadcast(context, UserHandle.of(userId));
                            migrationBroadcast.sendInvocationBroadcast();
                        } catch (Exception e) {
                            Slog.e(TAG, "Exception while executing job : ", e);
                        }
                    });
        } catch (Exception e) {
            // Don't rethrow as that will crash system_server
            Slog.e(TAG, "Scheduled job failed to run", e);
        }
        return false;
    }

    /** Called when job needs to be stopped. Don't do anything here and let the job be killed. */
    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
