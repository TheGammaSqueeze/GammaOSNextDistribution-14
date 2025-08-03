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

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Set;
import java.util.concurrent.Callable;

public class NoOpRetryStrategyImpl implements RetryStrategy {

    @Override
    public <T> ListenableFuture<T> call(
            Callable<ListenableFuture<T>> listenableFutureToRetry,
            Set<Class<? extends Exception>> retryableExceptions,
            @NonNull LoggerFactory.Logger logger,
            @NonNull String callingIdentifier) {
        try {
            return listenableFutureToRetry.call();
        } catch (Exception e) {
            logger.e(
                    e,
                    "Error while calling %s but the exception is not retryable.",
                    callingIdentifier);
            throw new RuntimeException(e);
        }
    }
}
