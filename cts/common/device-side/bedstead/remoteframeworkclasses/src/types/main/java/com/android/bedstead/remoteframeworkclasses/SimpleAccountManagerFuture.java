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

package com.android.bedstead.remoteframeworkclasses;

import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple implementation of {@link AccountManagerFuture} for use with remote calls.
 *
 * @param <V> The wrapped future result.
 */
public final class SimpleAccountManagerFuture<V> implements AccountManagerFuture<V> {

    private final CountDownLatch mIsDone = new CountDownLatch(1);
    private final AtomicReference<Throwable> mException = new AtomicReference<>();
    private final AtomicReference<V> mValue = new AtomicReference<>();

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // Remote calls cannot cancel futures
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return mIsDone.getCount() < 1;
    }

    @Override
    public V getResult() throws OperationCanceledException, IOException, AuthenticatorException {
        try {
            mIsDone.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for result", e);
        }

        Throwable exception = mException.get();
        if (exception == null) {
            return mValue.get();
        } else if (exception instanceof OperationCanceledException) {
            throw (OperationCanceledException) exception;
        } else if (exception instanceof IOException) {
            throw (IOException)  exception;
        } else if (exception instanceof AuthenticatorException) {
            throw (AuthenticatorException) exception;
        } else {
            throw new RuntimeException("Unexpected exception type", exception);
        }
    }

    /** Set the result of this future. */
    public void setResult(V result) {
        mValue.set(result);
        mIsDone.countDown();
    }

    @Override
    public V getResult(long timeout, TimeUnit unit)
            throws OperationCanceledException, IOException, AuthenticatorException {
        try {
            mIsDone.await(timeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for result", e);
        }

        Throwable exception = mException.get();
        if (exception == null) {
            return mValue.get();
        } else if (exception instanceof OperationCanceledException) {
            throw (OperationCanceledException) exception;
        } else if (exception instanceof IOException) {
            throw (IOException)  exception;
        } else if (exception instanceof AuthenticatorException) {
            throw (AuthenticatorException) exception;
        } else {
            throw new RuntimeException("Unexpected exception type", exception);
        }
    }

    /** Set an exception thrown by this future. */
    public void setException(Throwable throwable) {
        mException.set(throwable);
        mIsDone.countDown();
    }
}
