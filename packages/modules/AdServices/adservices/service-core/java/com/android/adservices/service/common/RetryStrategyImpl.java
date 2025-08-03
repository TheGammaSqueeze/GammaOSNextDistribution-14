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

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class RetryStrategyImpl implements RetryStrategy {
    private final int mNumberOfRetryAttempts;
    private final ExecutorService mExecutorService;

    public RetryStrategyImpl(int numberOfRetryAttempts, @NonNull ExecutorService executorService) {
        Objects.requireNonNull(executorService);

        mNumberOfRetryAttempts = numberOfRetryAttempts;
        mExecutorService = executorService;
    }

    @Override
    public <T> ListenableFuture<T> call(
            @NonNull Callable<ListenableFuture<T>> listenableFutureToRetry,
            @NonNull Set<Class<? extends Exception>> retryableExceptions,
            @NonNull LoggerFactory.Logger logger,
            @NonNull String callingIdentifier) {
        return retry(
                listenableFutureToRetry,
                mNumberOfRetryAttempts,
                retryableExceptions,
                callingIdentifier,
                logger);
    }

    private <T> ListenableFuture<T> retry(
            Callable<ListenableFuture<T>> listenableFutureToRetry,
            int numOfTimes,
            Set<Class<? extends Exception>> retryableExceptions,
            String callIdentifier,
            LoggerFactory.Logger logger) {
        try {
            return FluentFuture.from(listenableFutureToRetry.call())
                    .catchingAsync(
                            Exception.class,
                            e -> {
                                if (!isExceptionRetryable(e, retryableExceptions)) {
                                    logger.w(
                                            "Error while calling %s but the exception is not "
                                                    + "retryable.",
                                            callIdentifier);
                                    throw e;
                                }
                                logger.w(
                                        "Error while calling %s, remaining retry attempts: %d",
                                        callIdentifier, numOfTimes);
                                if (numOfTimes <= 0) {
                                    logger.e(
                                            e,
                                            "No more retry attempts remaining while calling %s.",
                                            callIdentifier);
                                    throw e;
                                }
                                return retry(
                                        listenableFutureToRetry,
                                        numOfTimes - 1,
                                        retryableExceptions,
                                        callIdentifier,
                                        logger);
                            },
                            mExecutorService);
        } catch (Exception e) {
            logger.e(
                    e,
                    "Error while calling %s but the exception is not retryable.",
                    callIdentifier);
            throw new RuntimeException(e);
        }
    }

    private boolean isExceptionRetryable(
            Exception exception, Set<Class<? extends Exception>> retryableExceptions) {
        for (Class<? extends Exception> clazz : retryableExceptions) {
            if (exception.getClass() == clazz) {
                return true;
            }
        }
        return false;
    }
}
