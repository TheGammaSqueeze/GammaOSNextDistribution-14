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

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RetryStrategyTest extends AdServicesMockitoTestCase {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final Set<Class<? extends Exception>> RETRYABLE_EXCEPTIONS =
            Set.of(RetryableException.class);
    private static final String CALLING_IDENTIFIER = RetryStrategyFactoryTest.class.getName();

    private RetryStrategy mRetryStrategy;

    @Test
    public void test_call_success()
            throws ExecutionException, InterruptedException, TimeoutException {
        int maxRetryAttempts = 1;
        mRetryStrategy =
                new RetryStrategyImpl(
                        maxRetryAttempts, AdServicesExecutors.getLightWeightExecutor());

        RetryStrategyTestHelper futureThrowsRetryableExceptionOnFailure =
                new RetryStrategyTestHelper(maxRetryAttempts, new RetryableException());

        mRetryStrategy
                .call(
                        futureThrowsRetryableExceptionOnFailure::getListenableFuture,
                        RETRYABLE_EXCEPTIONS,
                        sLogger,
                        CALLING_IDENTIFIER)
                .get(1, TimeUnit.SECONDS);
    }

    @Test
    public void test_call_withZeroRetryAttemptsDoesNotRetry() {
        int maxRetryAttempts = 0;
        mRetryStrategy =
                new RetryStrategyImpl(
                        maxRetryAttempts, AdServicesExecutors.getLightWeightExecutor());

        RetryStrategyTestHelper futureThrowsRetryableExceptionOnFailure =
                new RetryStrategyTestHelper(1, new RetryableException());

        retryAndWaitForException(futureThrowsRetryableExceptionOnFailure, RetryableException.class);
    }

    @Test
    public void test_call_doseNotRetryOnNonRetryableExceptions() {
        int retryAttempts = 1;
        mRetryStrategy =
                new RetryStrategyImpl(retryAttempts, AdServicesExecutors.getLightWeightExecutor());
        RetryStrategyTestHelper futureThrowsNonRetryableException =
                new RetryStrategyTestHelper(retryAttempts, new NonRetryableException());
        retryAndWaitForException(futureThrowsNonRetryableException, NonRetryableException.class);
    }

    @Test
    public void test_call_NoOpRetryStrategy_doesNotRetryOnAnyException() {
        int failCount = 1;
        mRetryStrategy = new NoOpRetryStrategyImpl();
        RetryStrategyTestHelper futureThrowsRetryableException =
                new RetryStrategyTestHelper(failCount, new RetryableException());
        RetryStrategyTestHelper futureThrowsNonRetryableException =
                new RetryStrategyTestHelper(failCount, new NonRetryableException());

        retryAndWaitForException(futureThrowsRetryableException, RetryableException.class);
        retryAndWaitForException(futureThrowsNonRetryableException, NonRetryableException.class);
    }

    private void retryAndWaitForException(
            RetryStrategyTestHelper test, Class<? extends Exception> exceptionClass) {

        Throwable exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                mRetryStrategy
                                        .call(
                                                test::getListenableFuture,
                                                RETRYABLE_EXCEPTIONS,
                                                sLogger,
                                                CALLING_IDENTIFIER)
                                        .get(1, TimeUnit.SECONDS));
        Truth.assertThat(exception).hasCauseThat().isInstanceOf(exceptionClass);
    }

    static class RetryableException extends Exception {}

    static class NonRetryableException extends Exception {}

    /** Class to facilitate testing for RetryStrategy. */
    static class RetryStrategyTestHelper {
        private final CountDownLatch mFailedCallCountLatch;
        private final Exception mExpectedExceptionOnFailure;

        RetryStrategyTestHelper(int failCallCount, Exception expectedExceptionOnFailure) {
            mFailedCallCountLatch = new CountDownLatch(failCallCount);
            mExpectedExceptionOnFailure = expectedExceptionOnFailure;
        }

        public ListenableFuture<Void> getListenableFuture() {
            if (mFailedCallCountLatch.getCount() == 0) {
                return Futures.immediateVoidFuture();
            } else {
                mFailedCallCountLatch.countDown();
                return Futures.immediateFailedFuture(mExpectedExceptionOnFailure);
            }
        }
    }
}
