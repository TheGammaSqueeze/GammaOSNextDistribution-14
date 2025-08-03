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
package com.android.tradefed.retry;

/**
 * A Class to describe the decisions about whether or not to retry preparation and to fail the
 * module run. Overall, there would be 3 situations:
 *   - NO_NEED_RETRY: No need to retry preparation but need to stop the module run.
 *   - RETRIED_SUCCESS: No need to retry preparation and no need to stop the module run.
 *   - RETRIED_FAILED: Need to retry preparation but no need to stop the module run.
 */
public class RetryPreparationDecision {

    /** Decide whether or not to retry module preparation. */
    private final boolean mShouldRetry;

    /** Decide whether or not to stop the module run. */
    private final boolean mShouldFailRun;

    /** Store the previous exception after retrying. */
    private Throwable mPreviousException;

    public RetryPreparationDecision(boolean shouldRetry, boolean shouldFailRun) {
        mShouldRetry = shouldRetry;
        mShouldFailRun = shouldFailRun;
    }

    /** Returns whether or not to retry module preparation. */
    public boolean shouldRetry() {
      return mShouldRetry;
    }

    /** Returns whether or not to stop the module run. */
    public boolean shouldFailRun() {
        return mShouldFailRun;
    }

    /** Returns the previous exception after retrying. */
    public Throwable getPreviousException() {
        return mPreviousException;
    }

    /** Set the previous exception after retrying. */
    public void setPreviousException(Throwable exception) {
        mPreviousException = exception;
    }
}
