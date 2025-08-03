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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_JS_SANDBOX_UNAVAILABLE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;

import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.js.JSSandboxIsNotAvailableException;

import com.google.common.util.concurrent.UncheckedTimeoutException;

/** Util class for AdServicesLogger */
public class AdServicesLoggerUtil {
    /** enum type value for any field in a telemetry atom that should be unset. */
    public static final int FIELD_UNSET = -1;

    /** @return the resultCode corresponding to the type of exception to be used in logging. */
    public static int getResultCodeFromException(Throwable t) {
        if (t instanceof FilterException) {
            return FilterException.getResultCode(t);
        } else if (t instanceof UncheckedTimeoutException) {
            return STATUS_TIMEOUT;
        } else if (t instanceof JSSandboxIsNotAvailableException) {
            return STATUS_JS_SANDBOX_UNAVAILABLE;
        } else if (t instanceof IllegalArgumentException) {
            return STATUS_INVALID_ARGUMENT;
        } else {
            return STATUS_INTERNAL_ERROR;
        }
    }
}
