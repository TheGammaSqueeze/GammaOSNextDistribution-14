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

package com.android.adservices.shared.testing.common;

import com.android.adservices.shared.util.LogUtil;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to help implementing async tasks.
 *
 * <p>Wraps an {@link Callable} instance to enable the caller to control the command start time
 * (could be used to creating hanging commands) and to support synchronization on command completion
 *
 * @param <T> the return type of the {@link Callable}
 */
// TODO(b/324919960): refactor this class and SyncCallback (once it's moved to shared)
//  to use a common superclass
public final class BlockingCallableWrapper<T> implements Callable<T> {
    private final Callable<T> mDelegate;
    private final CountDownLatch mStartWorkLatch;
    private final CountDownLatch mWorkCompletedLatch;
    private boolean mWorkCompleted;

    /**
     * Returns a new instance of {@code BlockingCallableWrapper} that will immediately trigger the
     * {@code delegate} when running.
     */
    public static <T> BlockingCallableWrapper<T> createNonBlockableInstance(Callable<T> delegate) {
        return new BlockingCallableWrapper<>(false, delegate);
    }

    /**
     * Returns a new instance of {@code BlockingCallableWrapper} that will wait for {link
     * BlockingCallableWrapper#startWork} to be invoked before running the {@code delegate}.
     */
    public static <T> BlockingCallableWrapper<T> createBlockableInstance(Callable<T> delegate) {
        return new BlockingCallableWrapper<>(true, delegate);
    }

    private BlockingCallableWrapper(boolean waitBeforeStartingWork, Callable<T> delegate) {
        mStartWorkLatch = new CountDownLatch(waitBeforeStartingWork ? 1 : 0);
        mWorkCompletedLatch = new CountDownLatch(1);
        mWorkCompleted = false;
        mDelegate = delegate;
    }

    /**
     * Unblocks the execution of the delegate. If this instance has been created with {@code
     * waitBeforeStartingWork} set to {@code false} the delegate is not blocked and this method does
     * nothing.
     */
    public void startWork() {
        mStartWorkLatch.countDown();
    }

    /**
     * Blocks the caller until the delegate execution is completed or the timeout is reached.
     *
     * @return {@code true} if the {code delegate} execution is complete, {@code false} otherwise
     * @throws InterruptedException if the running thread is interrupted while waiting
     */
    public boolean waitForCompletion(Duration timeout) throws InterruptedException {
        return mWorkCompletedLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Returns {@code true} if the execution of the delegate is completed, {@code false} otherwise
     */
    public boolean isWorkCompleted() {
        return mWorkCompleted;
    }

    @Override
    public T call() throws Exception {
        try {
            LogUtil.d("Waiting to be enabled to start");
            mStartWorkLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LogUtil.d("Enabled. Calling delegate");
        try {
            return mDelegate.call();
        } finally {
            LogUtil.d("Call completed");
            mWorkCompleted = true;
            mWorkCompletedLatch.countDown();
        }
    }
}
