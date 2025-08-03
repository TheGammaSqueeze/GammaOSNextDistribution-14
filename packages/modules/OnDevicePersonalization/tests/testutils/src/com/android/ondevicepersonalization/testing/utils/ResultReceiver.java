/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.ondevicepersonalization.testing.utils;

import android.os.OutcomeReceiver;

import java.util.concurrent.CountDownLatch;

/**
 * A synchronous wrapper around OutcomeReceiver for testing.
 */
public class ResultReceiver<T> implements OutcomeReceiver<T, Exception> {
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private T mResult = null;
    private Exception mException = null;
    private boolean mSuccess = false;
    private boolean mError = false;

    @Override public void onResult(T result) {
        mSuccess = true;
        mResult = result;
        mLatch.countDown();
    }

    @Override public void onError(Exception e) {
        mError = true;
        mException = e;
        mLatch.countDown();
    }

    /** Returns the result passed to the OutcomeReceiver. */
    public T getResult() throws InterruptedException {
        mLatch.await();
        return mResult;
    }

    /** Returns the exception passed to the OutcomeReceiver. */
    public Exception getException() throws InterruptedException {
        mLatch.await();
        return mException;
    }

    /** Returns true if onResult() was called. */
    public boolean isSuccess() throws InterruptedException {
        mLatch.await();
        return mSuccess;
    }

    /** Returns true if onError() was called. */
    public boolean isError() throws InterruptedException {
        mLatch.await();
        return mError;
    }
}
