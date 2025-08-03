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

package com.android.adservices.service.exception;

import android.adservices.common.AdServicesStatusUtils;
import android.os.LimitExceededException;

import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;

/** Parent exception to wrap exceptions thrown by filters. */
public class FilterException extends RuntimeException {
    public FilterException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilterException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        return this.getCause().getMessage();
    }

    /** @return the resultCode corresponding to the type of filter exception. */
    public static int getResultCode(Throwable t) {
        Throwable cause = t instanceof FilterException ? t.getCause() : t;

        if (cause instanceof ConsentManager.RevokedConsentException) {
            return AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
        } else if (cause instanceof AppImportanceFilter.WrongCallingApplicationStateException) {
            return AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
        } else if (cause instanceof FledgeAuthorizationFilter.AdTechNotAllowedException
                || cause instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            return AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
        } else if (cause instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            return AdServicesStatusUtils.STATUS_UNAUTHORIZED;
        } else if (cause instanceof LimitExceededException) {
            return AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
        }

        return AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
    }
}
