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

package com.android.adservices.cobalt;

import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.METRIC_ID;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_LOWER_API_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_LOWER_ERROR_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_UPPER_API_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.RANGE_UPPER_ERROR_CODE;
import static com.android.adservices.cobalt.CobaltLoggerConstantUtils.UNKNOWN_EVENT_CODE;

import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.cobalt.CobaltLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Wrapper around {@link CobaltLogger} that logs {@code ApiNameCode, StatusCode} and app package
 * name occurrences to Cobalt.
 */
public final class AppNameApiErrorLogger {
    private static final Object SINGLETON_LOCK = new Object();

    @Nullable private static AppNameApiErrorLogger sInstance;

    private final CobaltLogger mCobaltLogger;

    /**
     * Returns an instance of the {@code AppNameApiErrorLogger}. Returns {@code null} when Cobalt
     * logging is disabled or Cobalt logger initialization failed.
     */
    @Nullable
    public static AppNameApiErrorLogger getInstance(Context context, Flags flags) {
        synchronized (SINGLETON_LOCK) {
            if (flags.getCobaltLoggingEnabled() && flags.getAppNameApiErrorCobaltLoggingEnabled()) {
                if (sInstance == null) {
                    try {
                        sInstance =
                                new AppNameApiErrorLogger(
                                        CobaltFactory.getCobaltLogger(context, flags));
                    } catch (CobaltInitializationException e) {
                        LogUtil.e(e, "Cobalt logger initialization failed.");
                        // TODO(b/323253975): Add CEL.
                        return null;
                    }
                }
            } else {
                LogUtil.d("Cobalt logger is disabled.");
                return null;
            }
            return sInstance;
        }
    }

    @VisibleForTesting
    AppNameApiErrorLogger(CobaltLogger cobaltLogger) {
        this.mCobaltLogger = cobaltLogger;
    }

    /**
     * Log an api call event with app package name, api name and error code. This method only logs
     * the event if the api call returns error.
     *
     * @param appPackageName the app package name made the api call
     * @param apiCode the api name code in int
     * @param errorCode the error code in int
     */
    @SuppressWarnings("FutureReturnValueIgnored") // TODO(b/323263328): Remove @SuppressWarnings.
    public void logErrorOccurrence(String appPackageName, int apiCode, int errorCode) {
        if (errorCode == 0) {
            LogUtil.d(
                    "Skip logging success event for app package name: %s, api code %d.",
                    appPackageName, apiCode);
            return;
        }

        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");
        int apiCodeEvent = getRangeValue(RANGE_LOWER_API_CODE, RANGE_UPPER_API_CODE, apiCode);
        int errorCodeEvent =
                getRangeValue(RANGE_LOWER_ERROR_CODE, RANGE_UPPER_ERROR_CODE, errorCode);

        mCobaltLogger.logString(
                METRIC_ID, appPackageName, ImmutableList.of(apiCodeEvent, errorCodeEvent));
    }

    /**
     * Returns the value if it is within the range, otherwise returns {@code UNKNOWN_EVENT_CODE}.
     */
    private static int getRangeValue(int lowerRange, int upperRange, int value) {
        return value <= upperRange && value >= lowerRange ? value : UNKNOWN_EVENT_CODE;
    }
}
