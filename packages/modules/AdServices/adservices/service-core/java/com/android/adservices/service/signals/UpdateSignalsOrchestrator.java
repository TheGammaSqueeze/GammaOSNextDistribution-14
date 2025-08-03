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

package com.android.adservices.service.signals;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.FluentFuture;

import org.json.JSONObject;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Orchestrates the updateSignals API. */
public class UpdateSignalsOrchestrator {

    @NonNull private final Executor mBackgroundExecutor;
    @NonNull private final UpdatesDownloader mUpdatesDownloader;
    @NonNull private final UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;
    @NonNull private final AdTechUriValidator mAdTechUriValidator;
    @NonNull private final Clock mClock;

    public UpdateSignalsOrchestrator(
            @NonNull Executor backgroundExecutor,
            @NonNull UpdatesDownloader updatesDownloader,
            @NonNull UpdateProcessingOrchestrator updateProcessingOrchestrator,
            @NonNull AdTechUriValidator adTechUriValidator,
            @NonNull Clock clock) {
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(updatesDownloader);
        Objects.requireNonNull(updateProcessingOrchestrator);
        Objects.requireNonNull(adTechUriValidator);
        Objects.requireNonNull(clock);
        mBackgroundExecutor = backgroundExecutor;
        mUpdateProcessingOrchestrator = updateProcessingOrchestrator;
        mUpdatesDownloader = updatesDownloader;
        mAdTechUriValidator = adTechUriValidator;
        mClock = clock;
    }

    /**
     * Orchestrate the updateSignals API.
     *
     * @param uri Validated Uri to fetch JSON from.
     * @param packageName The package name of the calling app.
     * @param devContext development context used for testing network calls
     * @return A future for running the orchestration, with no return value
     */
    public FluentFuture<Object> orchestrateUpdate(
            Uri uri, AdTechIdentifier adtech, String packageName, DevContext devContext) {
        mAdTechUriValidator.validate(uri);
        FluentFuture<JSONObject> jsonFuture =
                mUpdatesDownloader.getUpdateJson(uri, packageName, devContext);
        return jsonFuture.transform(
                x -> {
                    mUpdateProcessingOrchestrator.processUpdates(
                            adtech, packageName, mClock.instant(), x, devContext);
                    return null;
                },
                mBackgroundExecutor);
    }
}
