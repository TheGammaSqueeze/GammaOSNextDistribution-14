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

package com.android.ondevicepersonalization.services.statsd.joblogging;

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.JOB_ID_TO_NAME_MAP;

import android.content.Context;

import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.spe.logging.StatsdJobServiceLogger;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.GuardedBy;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import java.util.Map;
import java.util.concurrent.Executor;

/** A background job logger to log ODP background job stats. */
public final class OdpJobServiceLogger extends JobServiceLogger {
    @GuardedBy("SINGLETON_LOCK")
    private static volatile OdpJobServiceLogger sSingleton;

    private static final Object SINGLETON_LOCK = new Object();

    /** Create an instance of {@link JobServiceLogger}. */
    public OdpJobServiceLogger(
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
    public static OdpJobServiceLogger getInstance(Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new OdpJobServiceLogger(
                                context,
                                Clock.getInstance(),
                                new OdpStatsdJobServiceLogger(),
                                ClientErrorLogger.getInstance(),
                                OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                                JOB_ID_TO_NAME_MAP,
                                FlagsFactory.getFlags());
            }

            return sSingleton;
        }
    }
}
