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
package com.android.adservices.service.common;

import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_UNSPECIFIED;
import static com.android.adservices.service.common.AppManifestConfigCall.apiToString;
import static com.android.adservices.service.common.AppManifestConfigCall.resultToString;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_LOGGING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall.ApiType;
import com.android.adservices.service.common.AppManifestConfigCall.Result;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

// NOTE: public because of dump()
/** Class used to log metrics of how apps are using the app config on manifest. */
public final class AppManifestConfigMetricsLogger {

    @VisibleForTesting
    static final String PREFS_NAME =
            FileCompatUtils.getAdservicesFilename("AppManifestConfigMetricsLogger");

    @VisibleForTesting static final String PREFS_KEY_TEMPLATE = "%s-%d";

    // TODO(b/310270746): make it package-protected when TopicsServiceImplTest is refactored
    /** Represents a call to a public {@link AppManifestConfigHelper} method. */
    /** Logs the app usage. */
    public static void logUsage(AppManifestConfigCall call) {
        Objects.requireNonNull(call, "call cannot be null");

        // Cannot be RESULT_UNSPECIFIED because that's used to check if the shared preferences value
        // doesn't exist yet
        if (call.result == RESULT_UNSPECIFIED) {
            LogUtil.e("invalid call result: %s", call);
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_LOGGING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            return;
        }
        AdServicesExecutors.getBackgroundExecutor().execute(() -> handleLogUsage(call));
    }

    private static void handleLogUsage(AppManifestConfigCall call) {
        Context context = ApplicationContextSingleton.get();
        try {
            @Result int newValue = call.result;
            LogUtil.d(
                    "AppManifestConfigMetricsLogger.logUsage(): call=%s, newValue=%d",
                    call, newValue);

            SharedPreferences prefs = getPrefs(context);
            String key = String.format(Locale.US, PREFS_KEY_TEMPLATE, call.packageName, call.api);

            @Result int currentValue = prefs.getInt(key, RESULT_UNSPECIFIED);
            if (currentValue == RESULT_UNSPECIFIED) {
                LogUtil.v("Logging for the first time (value=%d)", newValue);
            } else if (currentValue != newValue) {
                LogUtil.v("Logging as value change (was %d)", currentValue);
            } else {
                LogUtil.v("Value didn't change, don't need to log");
                return;
            }

            // Send metrics to statsd first...
            StatsdAdServicesLogger.getInstance().logAppManifestConfigCall(call);

            // ...then "mark" as sent
            Editor editor = prefs.edit().putInt(key, newValue);

            if (editor.commit()) {
                LogUtil.v("Changes committed");
            } else {
                LogUtil.e(
                        "logUsage(ctx, file=%s, call=%s, newValue=%d): failed to commit",
                        PREFS_NAME, call, newValue);
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            }
        } catch (Exception e) {
            LogUtil.e(e, "logUsage(ctx, file=%s, call=%s) failed", PREFS_NAME, call);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }

    /** Dumps the internal state. */
    public static void dump(Context context, PrintWriter pw) {
        String prefix = "  ";
        pw.println("AppManifestConfigMetricsLogger");

        // NOTE: shared_prefs is hard-coded on ContextImpl, but unfortunately Context doesn't offer
        // any API we could use here to get that path (getSharedPreferencesPath() is @removed and
        // the available APIs return a SharedPreferences, not a File).
        @SuppressWarnings("NewAdServicesFile") // PREFS_NAME already called FileCompatUtils
        String path =
                new File(context.getDataDir() + "/shared_prefs", PREFS_NAME).getAbsolutePath();
        pw.printf("%sPreferences file: %s.xml\n", prefix, path);

        boolean flagEnabledByDefault =
                FlagsFactory.getFlags().getAppConfigReturnsEnabledByDefault();
        pw.printf("%s(Currently) enabled by default: %b\n", prefix, flagEnabledByDefault);

        SharedPreferences prefs = getPrefs(context);
        Map<String, ?> appPrefs = prefs.getAll();
        pw.printf("%s%d entries:\n", prefix, appPrefs.size());

        String prefix2 = prefix + "  ";
        for (Entry<String, ?> pref : appPrefs.entrySet()) {
            String key = pref.getKey();
            String appAndApi = key;
            try {
                String[] keyParts = key.split("-");
                String app = keyParts[0];
                @ApiType int api = Integer.parseInt(keyParts[1]);
                appAndApi = app + "-" + apiToString(api);
            } catch (Exception e) {
                LogUtil.e(e, "failed to parse key %s", key);
            }
            Object value = pref.getValue();
            if (value instanceof Integer) {
                @Result int result = (Integer) value;
                pw.printf("%s%s: %s\n", prefix2, appAndApi, resultToString(result));
            } else {
                // Shouldn't happen
                pw.printf(
                        "  %s: unexpected value %s (class %s):\n",
                        appAndApi, value, value.getClass());
            }
        }
    }

    @SuppressWarnings("NewAdServicesFile") // PREFS_NAME already called FileCompatUtils
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private AppManifestConfigMetricsLogger() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
