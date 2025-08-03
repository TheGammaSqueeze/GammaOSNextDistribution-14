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

package com.android.adservices.errorlogging;

import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

/** Util class which logs errors/exception to the Statsd */
public class ErrorLogUtil {
    private static final AdServicesErrorLogger ERROR_LOGGER =
            AdServicesErrorLoggerImpl.getInstance();

    /** Logs an atom in the Statsd for error with exception info. */
    public static void e(Throwable tr, int errorCode, int ppapiName) {
        ERROR_LOGGER.logErrorWithExceptionInfo(tr, errorCode, ppapiName);
    }

    /** Logs an atom in the Statsd for error. */
    public static void e(int errorCode, int ppapiName) {
        ERROR_LOGGER.logError(errorCode, ppapiName);
    }
}
