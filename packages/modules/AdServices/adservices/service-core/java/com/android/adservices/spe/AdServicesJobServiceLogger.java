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

package com.android.adservices.spe;

import android.content.Context;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.spe.logging.StatsdJobServiceLogger;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.GuardedBy;

import java.util.Map;
import java.util.concurrent.Executor;

// TODO(b/323029425): This class will be removed once all jobs are fully migrated to SPE. Make it as
// a child class to minimize the change.
/** A background job logger to log AdServices background job stats. */
public final class AdServicesJobServiceLogger extends JobServiceLogger {
    @GuardedBy("SINGLETON_LOCK")
    private static volatile AdServicesJobServiceLogger sSingleton;

    private static final Object SINGLETON_LOCK = new Object();

    /** Create an instance of {@link JobServiceLogger}. */
    public AdServicesJobServiceLogger(
            Context context,
            Clock clock,
            StatsdJobServiceLogger statsdLogger,
            AdServicesErrorLogger errorLogger,
            Executor executor,
            Map<Integer, String> jobIdToNameMap,
            ModuleSharedFlags flags) {
        super(context, clock, statsdLogger, errorLogger, executor, jobIdToNameMap, flags);
    }

    /** Get a singleton instance of {@link JobServiceLogger} to be used. */
    public static AdServicesJobServiceLogger getInstance(Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new AdServicesJobServiceLogger(
                                context,
                                Clock.getInstance(),
                                new AdServicesStatsdJobServiceLogger(),
                                AdServicesErrorLoggerImpl.getInstance(),
                                AdServicesExecutors.getBackgroundExecutor(),
                                AdServicesJobInfo.getJobIdToJobNameMap(),
                                FlagsFactory.getFlags());
            }

            return sSingleton;
        }
    }
}
