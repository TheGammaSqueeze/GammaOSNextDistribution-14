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

package com.android.adservices.service.common;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.ExecutorService;

/** Class to instantiate {@link RetryStrategy} based on feature flag */
public class RetryStrategyFactory {

    private final boolean mIsRetryStrategyEnabled;
    private final ExecutorService mExecutorService;

    private RetryStrategyFactory(boolean isRetryStrategyEnabled, ExecutorService executorService) {
        mIsRetryStrategyEnabled = isRetryStrategyEnabled;
        mExecutorService = executorService;
    }

    /** provides instance of {@link RetryStrategyFactory}. */
    public static RetryStrategyFactory createInstance(
            boolean isAdServicesRetryStrategyEnabled, ExecutorService executorService) {
        return new RetryStrategyFactory(isAdServicesRetryStrategyEnabled, executorService);
    }

    /**
     * provides instance of {@link RetryStrategyFactory} which will always return {@link
     * NoOpRetryStrategyImpl }.
     */
    @VisibleForTesting
    public static RetryStrategyFactory createInstanceForTesting() {
        return new RetryStrategyFactory(false, AdServicesExecutors.getLightWeightExecutor());
    }

    /**
     * @return the correct instance of {@link RetryStrategy} based on the feature flag.
     */
    public RetryStrategy createRetryStrategy(int maxRetryAttempts) {
        return mIsRetryStrategyEnabled
                ? new RetryStrategyImpl(maxRetryAttempts, mExecutorService)
                : new NoOpRetryStrategyImpl();
    }
}
