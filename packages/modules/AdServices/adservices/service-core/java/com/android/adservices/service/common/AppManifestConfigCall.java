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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;

import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.util.Objects;

/** Represents a call to a public {@link AppManifestConfigHelper} method. */
public final class AppManifestConfigCall {

    public static final int API_UNSPECIFIED =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_TYPE__API_ACCESS_TYPE_UNSPECIFIED;
    public static final int API_TOPICS =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_TYPE__API_ACCESS_TYPE_TOPICS;
    public static final int API_CUSTOM_AUDIENCES =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_TYPE__API_ACCESS_TYPE_CUSTOM_AUDIENCES;

    public static final int API_PROTECTED_SIGNALS =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_TYPE__API_ACCESS_TYPE_PROTECTED_SIGNALS;

    public static final int API_AD_SELECTION =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_TYPE__API_ACCESS_TYPE_AD_SELECTION;
    public static final int API_ATTRIBUTION =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_TYPE__API_ACCESS_TYPE_ATTRIBUTION;

    @IntDef({
        API_TOPICS,
        API_CUSTOM_AUDIENCES,
        API_ATTRIBUTION,
        API_PROTECTED_SIGNALS,
        API_AD_SELECTION
    })
    @Retention(SOURCE)
    public @interface ApiType {}

    public static final int RESULT_UNSPECIFIED =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_UNSPECIFIED;
    public static final int RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
    public static final int RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION;
    public static final int RESULT_ALLOWED_APP_ALLOWS_ALL =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_ALLOWED_APP_ALLOWS_ALL;
    public static final int RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID;
    public static final int RESULT_DISALLOWED_APP_DOES_NOT_EXIST =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_DISALLOWED_APP_DOES_NOT_EXIST;
    public static final int RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR;
    public static final int RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG;
    public static final int RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_DISALLOWED_APP_DOES_HAS_CONFIG_WITHOUT_API_SECTION;
    public static final int RESULT_DISALLOWED_BY_APP =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_DISALLOWED_BY_APP;
    public static final int RESULT_DISALLOWED_GENERIC_ERROR =
            AdServicesStatsLog
                    .APP_MANIFEST_CONFIG_HELPER_CALLED__API_ACCESS_RESULT__API_ACCESS_RESULT_DISALLOWED_GENERIC_ERROR;

    @IntDef({
        RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG,
        RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION,
        RESULT_ALLOWED_APP_ALLOWS_ALL,
        RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID,
        RESULT_DISALLOWED_APP_DOES_NOT_EXIST,
        RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR,
        RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG,
        RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION,
        RESULT_DISALLOWED_BY_APP,
        RESULT_DISALLOWED_GENERIC_ERROR
    })
    @Retention(SOURCE)
    public @interface Result {}

    @VisibleForTesting static final String INVALID_API_TEMPLATE = "Invalid API: %d";

    public final String packageName;
    public final @ApiType int api;
    public @Result int result;

    public AppManifestConfigCall(String packageName, @ApiType int api) {
        switch (api) {
            case API_TOPICS:
            case API_CUSTOM_AUDIENCES:
            case API_ATTRIBUTION:
            case API_PROTECTED_SIGNALS:
            case API_AD_SELECTION:
                this.api = api;
                break;
            default:
                throw new IllegalArgumentException(String.format(INVALID_API_TEMPLATE, api));
        }
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AppManifestConfigCall other = (AppManifestConfigCall) obj;
        return api == other.api
                && Objects.equals(packageName, other.packageName)
                && result == other.result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(api, packageName, result);
    }

    @Override
    public String toString() {
        return "AppManifestConfigCall[pkg="
                + packageName
                + ", api="
                + apiToString(api)
                + ", result="
                + resultToString(result)
                + "]";
    }

    static String resultToString(@Result int result) {
        switch (result) {
            case RESULT_UNSPECIFIED:
                return "UNSPECIFIED";
            case RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG:
                return "ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG";
            case RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION:
                return "ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION";
            case RESULT_ALLOWED_APP_ALLOWS_ALL:
                return "ALLOWED_APP_ALLOWS_ALL";
            case RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID:
                return "ALLOWED_APP_ALLOWS_SPECIFIC_ID";
            case RESULT_DISALLOWED_APP_DOES_NOT_EXIST:
                return "DISALLOWED_APP_DOES_NOT_EXIST";
            case RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR:
                return "DISALLOWED_APP_CONFIG_PARSING_ERROR";
            case RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG:
                return "DISALLOWED_APP_DOES_NOT_HAVE_CONFIG";
            case RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION:
                return "DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION";
            case RESULT_DISALLOWED_BY_APP:
                return "DISALLOWED_BY_APP";
            case RESULT_DISALLOWED_GENERIC_ERROR:
                return "DISALLOWED_GENERIC_ERROR";
            default:
                return "INVALID-" + result;
        }
    }

    static String apiToString(@ApiType int result) {
        switch (result) {
            case API_UNSPECIFIED:
                return "UNSPECIFIED";
            case API_TOPICS:
                return "TOPICS";
            case API_CUSTOM_AUDIENCES:
                return "CUSTOM_AUDIENCES";
            case API_ATTRIBUTION:
                return "ATTRIBUTION";
            case API_PROTECTED_SIGNALS:
                return "PROTECTED_SIGNALS";
            case API_AD_SELECTION:
                return "AD_SELECTION";
            default:
                return "INVALID-" + result;
        }
    }

    static boolean isAllowed(@Result int result) {
        switch (result) {
            case RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG:
            case RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION:
            case RESULT_ALLOWED_APP_ALLOWS_ALL:
            case RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID:
                return true;
            default:
                return false;
        }
    }
}
