/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import android.annotation.NonNull;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.SingletonRunner;

import com.google.common.util.concurrent.FluentFuture;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public class ScheduleCustomAudienceUpdateWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static final String JOB_DESCRIPTION = "Schedule Custom Audience Update Job";

    private static final Object SINGLETON_LOCK = new Object();

    private static volatile ScheduleCustomAudienceUpdateWorker sCustomAudienceUpdateWorker;

    private final ScheduledUpdatesHandler mUpdatesHandler;

    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    @VisibleForTesting
    ScheduleCustomAudienceUpdateWorker(@NonNull ScheduledUpdatesHandler handler) {
        mUpdatesHandler = handler;
    }

    private ScheduleCustomAudienceUpdateWorker(@NonNull Context context) {
        mUpdatesHandler = new ScheduledUpdatesHandler(context);
    }

    /**
     * Returns an instance of {@link ScheduleCustomAudienceUpdateWorker} responsible for
     * orchestrating updates on their schedule
     */
    @NonNull
    public static ScheduleCustomAudienceUpdateWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        ScheduleCustomAudienceUpdateWorker singleReadResult = sCustomAudienceUpdateWorker;
        if (singleReadResult != null) {
            return singleReadResult;
        }

        synchronized (SINGLETON_LOCK) {
            if (sCustomAudienceUpdateWorker == null) {
                sCustomAudienceUpdateWorker = new ScheduleCustomAudienceUpdateWorker(context);
            }
        }
        return sCustomAudienceUpdateWorker;
    }

    /** Initiates the updates for Custom Audience */
    public FluentFuture<Void> updateCustomAudience() {
        sLogger.v("Starting %s", JOB_DESCRIPTION);
        return mSingletonRunner.runSingleInstance();
    }

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }

    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {
        return mUpdatesHandler.performScheduledUpdates(Instant.now());
    }
}
