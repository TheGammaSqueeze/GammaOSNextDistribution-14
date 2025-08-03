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

package com.android.adservices.service.adselection;

import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.os.Build;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;

import java.util.concurrent.ExecutorService;

/** Implements a disabled {@link EventReporter} that fails immediately. */
@RequiresApi(Build.VERSION_CODES.S)
class ReportEventDisabledImpl extends EventReporter {
    static final String API_DISABLED_MESSAGE = "The reportEvent API is disabled.";

    // TODO(b/297351791): Remove unused members from this implementation.
    ReportEventDisabledImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull AdSelectionServiceFilter adSelectionServiceFilter,
            int callerUid,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull DevContext devContext,
            boolean shouldUseUnifiedTables) {
        super(
                adSelectionEntryDao,
                adServicesHttpsClient,
                lightweightExecutorService,
                backgroundExecutorService,
                adServicesLogger,
                flags,
                adSelectionServiceFilter,
                callerUid,
                fledgeAuthorizationFilter,
                devContext,
                shouldUseUnifiedTables);
    }

    @Override
    public void reportInteraction(
            @NonNull ReportInteractionInput input, @NonNull ReportInteractionCallback callback) {
        sLogger.v(API_DISABLED_MESSAGE);
        notifyFailureToCaller(
                input.getCallerPackageName(),
                callback,
                new IllegalStateException(API_DISABLED_MESSAGE));
    }
}
