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

package com.android.adservices.service.stats;

import android.annotation.NonNull;

import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Factory class to create loggers for logging Custom Audience process signals. */
public class CustomAudienceLoggerFactory {
    private static CustomAudienceLoggerFactory sSingleton;
    private Clock mClock;
    private AdServicesLogger mAdServicesLogger;

    @VisibleForTesting
    CustomAudienceLoggerFactory(@NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
    }

    /** Returns the singleton instance of the {@link CustomAudienceLoggerFactory}. */
    @NonNull
    public static CustomAudienceLoggerFactory getInstance() {
        synchronized (CustomAudienceLoggerFactory.class) {
            if (sSingleton == null) {
                sSingleton =
                        new CustomAudienceLoggerFactory(
                                Clock.getInstance(), AdServicesLoggerImpl.getInstance());
            }
        }

        return sSingleton;
    }

    /** Create and returns a new instance of {@link CustomAudienceLoggerFactory} that is no-op. */
    @NonNull
    public static CustomAudienceLoggerFactory getNoOpInstance() {
        return new CustomAudienceLoggerFactory(Clock.getInstance(), new NoOpLoggerImpl());
    }

    /**
     * @return a new {@link BackgroundFetchExecutionLogger}.
     */
    public BackgroundFetchExecutionLogger getBackgroundFetchExecutionLogger() {
        return new BackgroundFetchExecutionLogger(mClock, mAdServicesLogger);
    }

    /**
     * @return a new {@link UpdateCustomAudienceExecutionLogger}.
     */
    public UpdateCustomAudienceExecutionLogger getUpdateCustomAudienceExecutionLogger() {
        return new UpdateCustomAudienceExecutionLogger(mClock, mAdServicesLogger);
    }
}
