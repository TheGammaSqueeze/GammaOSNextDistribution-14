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

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.devapi.DevContext;

/** Resolves access related to development/testing context. */
public class DevContextAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Localhost is only permitted on user-debug builds "
            + "or with developer options enabled.";
    private final boolean mIsAllowed;

    public DevContextAccessResolver(
            @NonNull DevContext devContext,
            @NonNull RegistrationRequest registrationRequest) {
        mIsAllowed = WebAddresses.isLocalhost(registrationRequest.getRegistrationUri())
                ? devContext.getDevOptionsEnabled()
                : true;
    }

    public DevContextAccessResolver(
            @NonNull DevContext devContext,
            @NonNull WebSourceRegistrationRequest webRegistrationRequest) {
        boolean hasLocalhost = false;
        for (WebSourceParams params : webRegistrationRequest.getSourceParams()) {
            if (WebAddresses.isLocalhost(params.getRegistrationUri())) {
                hasLocalhost = true;
                break;
            }
        }
        mIsAllowed = hasLocalhost ? devContext.getDevOptionsEnabled() : true;
    }

    public DevContextAccessResolver(
            @NonNull DevContext devContext,
            @NonNull SourceRegistrationRequest registrationRequest) {
        boolean hasLocalhost = false;
        for (Uri uri : registrationRequest.getRegistrationUris()) {
            if (WebAddresses.isLocalhost(uri)) {
                hasLocalhost = true;
                break;
            }
        }
        mIsAllowed = hasLocalhost ? devContext.getDevOptionsEnabled() : true;
    }

    public DevContextAccessResolver(
            @NonNull DevContext devContext,
            @NonNull WebTriggerRegistrationRequest webRegistrationRequest) {
        boolean hasLocalhost = false;
        for (WebTriggerParams params : webRegistrationRequest.getTriggerParams()) {
            if (WebAddresses.isLocalhost(params.getRegistrationUri())) {
                hasLocalhost = true;
                break;
            }
        }
        mIsAllowed = hasLocalhost ? devContext.getDevOptionsEnabled() : true;
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        int failureReason =
                mIsAllowed
                        ? FAILURE_REASON_UNSET
                        : FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST;
        return new AccessInfo(mIsAllowed, failureReason);
    }

    @NonNull
    @Override
    @AdServicesStatusUtils.StatusCode
    public int getErrorStatusCode() {
        return STATUS_UNAUTHORIZED;
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
