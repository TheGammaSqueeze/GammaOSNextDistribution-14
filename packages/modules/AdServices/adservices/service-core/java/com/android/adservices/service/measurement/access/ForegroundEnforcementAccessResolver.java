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

package com.android.adservices.service.measurement.access;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_FOREGROUND_ASSERTION_EXCEPTION;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_FOREGROUND_UNKNOWN_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.compat.ProcessCompatUtils;

import java.util.function.Supplier;

/** Resolves if API was called from foreground. */
public class ForegroundEnforcementAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Measurement API was not called from foreground.";
    private final int mAppNameId;
    private final int mCallingUid;
    private final AppImportanceFilter mAppImportanceFilter;
    private final boolean mEnforceForegroundStatus;

    public ForegroundEnforcementAccessResolver(
            int appNameId,
            int callingUid,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull Supplier<Boolean> enforceForegroundStatus) {
        mEnforceForegroundStatus = enforceForegroundStatus.get();
        mAppNameId = appNameId;
        mAppImportanceFilter = appImportanceFilter;
        mCallingUid = callingUid;
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        if (!mEnforceForegroundStatus) {
            LoggerFactory.getMeasurementLogger().d("Enforcement foreground flag has been disabled");
            return new AccessInfo(true, FAILURE_REASON_UNSET);
        }

        if (ProcessCompatUtils.isSdkSandboxUid(mCallingUid)) {
            LoggerFactory.getMeasurementLogger()
                    .d("Foreground check skipped, app running on Sandbox");
            return new AccessInfo(true, FAILURE_REASON_UNSET);
        }

        // @throws AppImportanceFilter.WrongCallingApplicationStateException if not in foreground
        try {
            mAppImportanceFilter.assertCallerIsInForeground(mCallingUid, mAppNameId, null);
        } catch (AppImportanceFilter.WrongCallingApplicationStateException e) {
            LoggerFactory.getMeasurementLogger().e("App not running in foreground");
            return new AccessInfo(false, FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Unexpected error occurred when asserting caller in foreground");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_FOREGROUND_UNKNOWN_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return new AccessInfo(false, FAILURE_REASON_FOREGROUND_ASSERTION_EXCEPTION);
        }
        return new AccessInfo(true, FAILURE_REASON_UNSET);
    }

    @NonNull
    @Override
    @AdServicesStatusUtils.StatusCode
    public int getErrorStatusCode() {
        return AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
