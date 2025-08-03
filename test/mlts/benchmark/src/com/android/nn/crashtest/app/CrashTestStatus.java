/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.nn.crashtest.app;

import android.annotation.SuppressLint;
import android.util.Log;

import com.android.nn.crashtest.core.CrashTestCoordinator;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class CrashTestStatus implements CrashTestCoordinator.CrashTestCompletionListener {
    public static enum TestResult {
        SUCCESS,
        FAILURE,
        CRASH,
        HANG
    }

    private final String TAG = "CrashTestStatus";

    private final Consumer<String> mDisplayMessages;

    // Not using AtomicBoolean to have the concept of unset status
    private final AtomicReference<TestResult>
            mTestResult = new AtomicReference<>(null);
    private final CountDownLatch mParallelTestComplete = new CountDownLatch(1);

    CrashTestStatus(Consumer<String> displayMessages) {
        mDisplayMessages = displayMessages;
    }

    @SuppressLint("DefaultLocale")
    private void handleCompletionNotification(TestResult testResult,
            String reason) {
        Log.d(TAG,
                String.format("Received crash test notification: %s and extra msg %s.\n",
                        testResult,
                        reason));
        if (mTestResult.compareAndSet(null, testResult)) {
            if (reason != null) {
                mDisplayMessages.accept(
                        String.format("Test completed with result %s and msg: %s.\n", testResult,
                                reason));
            } else {
                mDisplayMessages.accept(String.format("Test completed with result %s\n", testResult));
            }
            mParallelTestComplete.countDown();
            mDisplayMessages.accept(
                    String.format("mParallelTestComplete count is now %d, test result is %s\n",
                            mParallelTestComplete.getCount(), mTestResult.get()));
        } else {
            Log.d(TAG, "Ignored, another completion notification was sent before");
        }
    }

    @Override
    public void testCrashed() {
        handleCompletionNotification(TestResult.CRASH, null);
    }

    @Override
    public void testSucceeded() {
        handleCompletionNotification(TestResult.SUCCESS, null);
    }

    @Override
    public void testFailed(String reason) {
        handleCompletionNotification(TestResult.FAILURE, reason);
    }

    @Override
    public void testProgressing(Optional<String> description) {
        mDisplayMessages.accept(".");
    }

    public boolean isTestCompleted() {
        return mParallelTestComplete.getCount() == 0;
    }

    public boolean waitForCompletion(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return mParallelTestComplete.await(timeout, timeUnit);
    }

    public boolean compareAndSetResult(TestResult expect, TestResult update) {
        return mTestResult.compareAndSet(expect, update);
    }

    public TestResult result() {
        return mTestResult.get();
    }
}
