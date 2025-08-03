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
package com.android.adservices.common;

import org.junit.runners.model.Statement;

/** A simple JUnit statement that provides methods to assert if was evaluated or not. */
public final class SimpleStatement extends Statement {

    private final Logger mLog =
            new Logger(StandardStreamsLogger.getInstance(), SimpleStatement.class);

    private boolean mEvaluated;
    private Throwable mThrowable;

    @Override
    public void evaluate() throws Throwable {
        mLog.v("evaluate() called");
        mEvaluated = true;
        if (mThrowable != null) {
            mLog.v("Throwing %s", mThrowable);
            throw mThrowable;
        }
    }

    public void failWith(Throwable t) {
        mThrowable = t;
    }

    public void assertEvaluated() {
        if (!mEvaluated) {
            throw new AssertionError("test statement was not evaluated");
        }
    }

    public void assertNotEvaluated() {
        if (mEvaluated) {
            throw new AssertionError("test statement was evaluated");
        }
    }
}
