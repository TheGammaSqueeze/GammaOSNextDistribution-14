/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.harrier;

import static com.android.bedstead.harrier.BedsteadResult.ASSUMPTION_FAILED_RESULT;
import static com.android.bedstead.harrier.BedsteadResult.FAILED_RESULT;
import static com.android.bedstead.harrier.BedsteadResult.IGNORED_RESULT;
import static com.android.bedstead.harrier.BedsteadResult.PASSED_RESULT;

import android.util.Log;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RunListener} which exposes results in a contentprovider to be queried.
 */
public final class BedsteadRunListener extends RunListener {
    // I'm assuming this is single threaded...
    private int mIndex = 0;
    private boolean mIsFinished = false;
    private long mStartTimeNanos = 0;
    private Map<String, Integer> mTestNameToIndex = new HashMap<>();

    private static final String LOG_TAG = "BedsteadRunListener";

    @Override
    public void testRunStarted(Description description) throws Exception {
        BedsteadRunResultsProvider.sNumberOfTests.set(description.testCount());
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        // Block until all results have been read
        while (thereAreUnreadResults()) {
            Thread.sleep(10);
        }
    }

    private boolean thereAreUnreadResults() {
        for (BedsteadResult result : BedsteadRunResultsProvider.sResults.values()) {
            if (!result.mHasBeenFetched) {
                return true;
            }
        }
        return false;
    }

    private String getTestName(Description description) {
        return description.getClassName() + "#" + description.getMethodName();
    }

    @Override
    public void testStarted(Description description) throws Exception {
        Log.d(LOG_TAG, "Test started: " + description);
        mTestNameToIndex.put(getTestName(description), mIndex);
        BedsteadRunResultsProvider.sResults.put(mIndex,
                new BedsteadResult(mIndex, getTestName(description)));
        mIsFinished = false;
        mIndex++;
        mStartTimeNanos = System.nanoTime();
    }

    @Override
    public void testFinished(Description description) throws Exception {
        Log.d(LOG_TAG, "Test finished: " + description);
        if (!mIsFinished) {
            BedsteadResult result = BedsteadRunResultsProvider.sResults.get(
                    mTestNameToIndex.get(getTestName(description)));
            result.mResult = PASSED_RESULT;
            result.mRuntime = (System.nanoTime() - mStartTimeNanos);
            result.mIsFinished = true;
        }
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        Log.d(LOG_TAG, "Test failed: " + failure);
        mIsFinished = true;
        BedsteadResult result = BedsteadRunResultsProvider.sResults.get(
                mTestNameToIndex.get(getTestName(failure.getDescription())));
        result.mResult = FAILED_RESULT;
        result.mRuntime = (System.nanoTime() - mStartTimeNanos);
        result.mMessage = failure.getMessage();
        result.mStackTrace = failure.getTrace();
        result.mIsFinished = true;
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        Log.d(LOG_TAG, "Test assumption failed: " + failure);
        mIsFinished = true;
        BedsteadResult result = BedsteadRunResultsProvider.sResults.get(
                mTestNameToIndex.get(getTestName(failure.getDescription())));
        result.mResult = ASSUMPTION_FAILED_RESULT;
        result.mRuntime = (System.nanoTime() - mStartTimeNanos);
        result.mMessage = failure.getMessage();
        result.mIsFinished = true;
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        Log.d(LOG_TAG, "Test ignored: " + description);
        testStarted(description);

        BedsteadResult result = BedsteadRunResultsProvider.sResults.get(
                mTestNameToIndex.get(getTestName(description)));
        result.mResult = IGNORED_RESULT;
        result.mRuntime = (System.nanoTime() - mStartTimeNanos);
        result.mIsFinished = true;
    }
}
