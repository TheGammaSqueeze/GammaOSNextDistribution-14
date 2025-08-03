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

package android.car;

import android.annotation.Nullable;
import android.annotation.SystemApi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Synchronous implementation for {@link ResultCallback}.
 *
 * <p>Can be used to get the results synchronously where {@link ResultCallback} is required.
 * {@link #get()} and {@link #get(long, TimeUnit)} methods can be used to get the result.
 *
 * @param <V> refer to a Parcelable object.
 *
 * @hide
 */
@SystemApi
public final class SyncResultCallback<V> implements ResultCallback<V> {

    private final CountDownLatch mLatch = new CountDownLatch(1);

    private AtomicReference<V> mResult = new AtomicReference<V>(null);

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     */
    @Nullable
    public V get() throws InterruptedException {
        mLatch.await();
        return mResult.get();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the computed result
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    @Nullable
    public V get(long timeout, @Nullable TimeUnit unit)
            throws InterruptedException, TimeoutException {
        if (mLatch.await(timeout, unit)) {
            return mResult.get();
        }

        throw new TimeoutException("Failed to receive result after " + timeout + " " + unit);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This method should be called internally only.
     */
    @Override
    public void onResult(V result) {
        mResult.set(result);
        mLatch.countDown();
    }
}
