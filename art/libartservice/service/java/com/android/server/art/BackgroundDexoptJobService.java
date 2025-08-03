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

package com.android.server.art;

import android.annotation.NonNull;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.server.LocalManagerRegistry;
import com.android.server.art.model.ArtServiceJobInterface;

/**
 * Entry point for the callback from the job scheduler. This class is instantiated by the system
 * automatically.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class BackgroundDexoptJobService extends JobService {
    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        return getJob(params.getJobId()).onStartJob(this, params);
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        return getJob(params.getJobId()).onStopJob(params);
    }

    @NonNull
    static ArtServiceJobInterface getJob(int jobId) {
        if (jobId == BackgroundDexoptJob.JOB_ID) {
            return LocalManagerRegistry.getManager(ArtManagerLocal.class).getBackgroundDexoptJob();
        } else if (jobId == PreRebootDexoptJob.JOB_ID) {
            return LocalManagerRegistry.getManager(ArtManagerLocal.class).getPreRebootDexoptJob();
        }
        throw new IllegalArgumentException("Unknown job ID " + jobId);
    }
}
