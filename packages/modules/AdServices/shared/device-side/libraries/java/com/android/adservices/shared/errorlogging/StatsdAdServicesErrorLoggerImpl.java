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

package com.android.adservices.shared.errorlogging;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED;

import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.internal.annotations.VisibleForTesting;

/** {@link StatsdAdServicesErrorLogger} that logs error stats to Statsd. */
public final class StatsdAdServicesErrorLoggerImpl implements StatsdAdServicesErrorLogger {
    private static final StatsdAdServicesErrorLogger sStatsdAdServicesErrorLogger =
            new StatsdAdServicesErrorLoggerImpl();

    @VisibleForTesting
    private StatsdAdServicesErrorLoggerImpl() {}

    /** Returns an instance of {@link StatsdAdServicesErrorLogger}. */
    public static StatsdAdServicesErrorLogger getInstance() {
        return sStatsdAdServicesErrorLogger;
    }

    @Override
    public void logAdServicesError(AdServicesErrorStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_ERROR_REPORTED,
                stats.getErrorCode(),
                stats.getPpapiName(),
                stats.getClassName(),
                stats.getMethodName(),
                stats.getLineNumber(),
                stats.getLastObservedExceptionName());
    }
}
