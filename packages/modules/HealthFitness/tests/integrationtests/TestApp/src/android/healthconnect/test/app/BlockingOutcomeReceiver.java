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

package android.healthconnect.test.app;

import android.os.OutcomeReceiver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A blocking implementation of {@link OutcomeReceiver} that allows waiting for responses. */
public class BlockingOutcomeReceiver<T, E extends Throwable> implements OutcomeReceiver<T, E> {

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private T mResult;
    private E mError;

    @Override
    public void onResult(T result) {
        mResult = result;
        mLatch.countDown();
    }

    @Override
    public void onError(E error) {
        mError = error;
        mLatch.countDown();
    }

    /** Waits for a response and returns the result if successful, or throws the error if failed. */
    public T getResult() throws E {
        awaitSuccess();
        return mResult;
    }

    /** Waits for a response, throws the error if failed. */
    public void awaitSuccess() throws E {
        await();

        if (mError != null) {
            throw mError;
        }
    }

    /** Waits for a response and returns the error if failed, or {@code null} if successful. */
    public E getError() {
        await();
        return mError;
    }

    private void await() {
        try {
            if (!mLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout waiting for outcome");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
