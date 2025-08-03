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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;
import static com.android.adservices.service.common.FledgeAuthorizationFilter.INVALID_API_TYPE;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;

import java.util.Locale;
import java.util.Objects;

/** FLEDGE Security filter for {@link AllowLists}. */
public class FledgeAllowListsFilter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Flags mFlags;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    public FledgeAllowListsFilter(
            @NonNull Flags flags, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adServicesLogger);

        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Asserts the package is allowed to call PPAPI.
     *
     * <p>Do not use this method in the Binder thread; flags must not be called there.
     *
     * @param appPackageName the package name to be validated.
     * @param apiNameLoggingId the id of the api being called
     * @param apiType the type of the api being called
     * @throws AppNotAllowedException if the package is not authorized.
     */
    public void assertAppInAllowlist(
            @NonNull String appPackageName,
            int apiNameLoggingId,
            @AppManifestConfigCall.ApiType int apiType)
            throws AppNotAllowedException {
        Objects.requireNonNull(appPackageName);
        if (!isInAllowList(appPackageName, apiType)) {
            sLogger.v(
                    "App package name \"%s\" not authorized to call API %d",
                    appPackageName, apiNameLoggingId);
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, appPackageName, STATUS_CALLER_NOT_ALLOWED, /*latencyMs=*/ 0);
            throw new AppNotAllowedException();
        }
    }

    /**
     * Internal exception for easy assertion catches specific to checking that an app is allowed to
     * use the PP APIs.
     *
     * <p>This exception is not meant to be exposed externally and should not be passed outside of
     * the service.
     */
    public static class AppNotAllowedException extends SecurityException {
        /**
         * Creates a {@link AppNotAllowedException}, used in cases where an app should have been
         * allowed to use the PP APIs.
         */
        public AppNotAllowedException() {
            super(AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
        }
    }

    private boolean isInAllowList(
            String appPackageName, @AppManifestConfigCall.ApiType int apiType) {
        if (apiType == API_CUSTOM_AUDIENCES) {
            return AllowLists.isPackageAllowListed(mFlags.getPpapiAppAllowList(), appPackageName);
        } else if (apiType == API_PROTECTED_SIGNALS) {
            return AllowLists.isPackageAllowListed(mFlags.getPasAppAllowList(), appPackageName);
        } else if (apiType == API_AD_SELECTION) {
            return AllowLists.isPackageAllowListed(mFlags.getPpapiAppAllowList(), appPackageName)
                    || AllowLists.isPackageAllowListed(mFlags.getPasAppAllowList(), appPackageName);
        } else {
            throw new IllegalStateException(
                    String.format(Locale.ENGLISH, INVALID_API_TYPE, apiType));
        }
    }
}
