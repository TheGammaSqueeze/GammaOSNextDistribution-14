/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static junit.framework.TestCase.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subclass this to create a blocking version of a callback. For example:
 *
 * {@code
 *     private static class KeyChainAliasCallback extends BlockingCallback<String> implements
 *             android.security.KeyChainAliasCallback {
 *         @Override
 *         public void alias(final String chosenAlias) {
 *             callbackTriggered(chosenAlias);
 *         }
 *     }
 * }
 *
 * <p>an instance of KeyChainAliasCallback can then be passed into a method, and the result can
 * be fetched using {@code .await()};
 */
public abstract class BlockingCallback<E> {
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private AtomicReference<E> mValue = new AtomicReference<>();

    /**
     * A default {@link BlockingCallback} used when a specific interface is not required.
     */
    public static class DefaultBlockingCallback<E> extends BlockingCallback<E> {
        public void triggerCallback(E e) {
            callbackTriggered(e);
        }
    }

    /**
     * A default {@link BlockingCallback} used when there is no data passed to the callback.
     */
    public static class VoidBlockingCallback extends BlockingCallback<Void> {
        public void triggerCallback() {
            callbackTriggered(null);
        }
    }

    /**
     * Blocking version of {@link Runnable}.
     */
    public static class BlockingRunnable extends VoidBlockingCallback implements Runnable {
        @Override
        public void run() {
            triggerCallback();
        }
    }

    /** Call this method from the callback method to mark the response as received. */
    protected void callbackTriggered(E value) {
        mValue.set(value);
        mLatch.countDown();
    }

    /**
     * Fetch the value passed into the callback.
     *
     * <p>Throws an {@link AssertionError} if the callback is not triggered in
     * {@link #DEFAULT_TIMEOUT_SECONDS} seconds.
     */
    public E await() throws InterruptedException {
        return await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Fetch the value passed into the callback.
     *
     * <p>Throws an {@link AssertionError} if the callback is not triggered before the timeout
     * elapses.
     */
    public E await(long timeout, TimeUnit unit) throws InterruptedException {
        if (!mLatch.await(timeout, unit)) {
            fail("Callback was not received");
        }
        return mValue.get();
    }
}
