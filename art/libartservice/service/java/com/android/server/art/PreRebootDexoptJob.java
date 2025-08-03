/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.server.art.model.ArtFlags.ScheduleStatus;

import android.annotation.NonNull;
import android.app.job.JobParameters;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.ArtServiceJobInterface;

/** @hide */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class PreRebootDexoptJob implements ArtServiceJobInterface {
    private static final String TAG = ArtManagerLocal.TAG;

    /**
     * "android" is the package name for a <service> declared in
     * frameworks/base/core/res/AndroidManifest.xml
     */
    private static final String JOB_PKG_NAME = Utils.PLATFORM_PACKAGE_NAME;
    /** An arbitrary number. Must be unique among all jobs owned by the system uid. */
    public static final int JOB_ID = 27873781;

    @NonNull private final Injector mInjector;

    public PreRebootDexoptJob(@NonNull Context context) {
        this(new Injector(context));
    }

    @VisibleForTesting
    public PreRebootDexoptJob(@NonNull Injector injector) {
        mInjector = injector;
    }

    @Override
    public boolean onStartJob(
            @NonNull BackgroundDexoptJobService jobService, @NonNull JobParameters params) {
        // "true" means the job will continue running until `jobFinished` is called.
        return false;
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        // "true" means to execute again in the same interval with the default retry policy.
        return true;
    }

    public @ScheduleStatus int schedule() {
        // TODO(b/311377497): Schedule the job.
        return ArtFlags.SCHEDULE_SUCCESS;
    }

    /**
     * Injector pattern for testing purpose.
     *
     * @hide
     */
    @VisibleForTesting
    public static class Injector {
        @NonNull private final Context mContext;

        Injector(@NonNull Context context) {
            mContext = context;
        }
    }
}
