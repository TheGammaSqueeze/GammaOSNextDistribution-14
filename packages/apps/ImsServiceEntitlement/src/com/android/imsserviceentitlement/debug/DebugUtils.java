/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement.debug;

import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;

import java.util.Optional;

/** Provides API for debugging and not allow to debug on user build. */
public final class DebugUtils {
    private static final String PROP_PII_LOGGABLE = "dbg.imsse.pii_loggable";
    private static final String PROP_BYPASS_EAP_AKA_RESPONSE = "dbg.imsse.bypass_eap_aka_response";
    private static final String PROP_SERVER_URL_OVERRIDE = "persist.dbg.imsse.server_url";
    private static final String BUILD_TYPE_USER = "user";

    private DebugUtils() {}

    /**
     * Tells if current build is user-debug or eng build which is debuggable.
     *
     * @see {@link android.os.Build.TYPE}
     */
    public static boolean isDebugBuild() {
        return !BUILD_TYPE_USER.equals(Build.TYPE);
    }

    /** Returns {@code true} if allow to print PII data for debugging. */
    public static boolean isPiiLoggable() {
        if (!isDebugBuild()) {
            return false;
        }

        return SystemProperties.getBoolean(PROP_PII_LOGGABLE, false);
    }

    /** Returns a non empty string if bypass EAP-AKA authentication is enabled. */
    public static String getBypassEapAkaResponse() {
        if (!isDebugBuild()) {
            return "";
        }

        return SystemProperties.get(PROP_BYPASS_EAP_AKA_RESPONSE);
    }

    /**
     * Returns {@link Optional} if testing server url was set in system property.
     */
    public static Optional<String> getOverrideServerUrl() {
        if (!isDebugBuild()) {
            return Optional.empty();
        }

        String urlOverride = SystemProperties.get(PROP_SERVER_URL_OVERRIDE, "");
        if (TextUtils.isEmpty(urlOverride)) {
            return Optional.empty();
        }

        return Optional.of(urlOverride);
    }
}
