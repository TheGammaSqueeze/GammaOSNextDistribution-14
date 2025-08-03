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

package com.android.ondevicepersonalization.services.util;

import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;

import java.util.Objects;

/** Fuctions for testing and debugging. */
public class DebugUtils {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = DebugUtils.class.getSimpleName();

    /** Returns true if the device is debuggable. */
    public static boolean isDeveloperModeEnabled(@NonNull Context context) {
        ContentResolver resolver = Objects.requireNonNull(context.getContentResolver());
        return Build.isDebuggable()
                || Settings.Global.getInt(
                    resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    /** Returns the exception code reported by the service if debugging is allowed. */
    public static int getIsolatedServiceExceptionCode(
            @NonNull Context context,
            @NonNull ComponentName service,
            @NonNull OdpServiceException e) {
        try {
            if (!FlagsFactory.getFlags().isIsolatedServiceDebuggingEnabled()) {
                return 0;
            }
            if (isDeveloperModeEnabled(context)
                    && PackageUtils.isPackageDebuggable(context, service.getPackageName())) {
                if (e.getCause() != null && e.getCause() instanceof IsolatedServiceException) {
                    return ((IsolatedServiceException) e.getCause()).getErrorCode();
                }
            }
        } catch (Exception e2) {
            sLogger.e(e2, TAG + ": failed to get code");
        }
        return 0;
    }

    private DebugUtils() {}
}
