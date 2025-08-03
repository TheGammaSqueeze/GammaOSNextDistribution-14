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


import android.os.OutcomeReceiver;

/**
 * Simple implementation of {@link OutcomeReceiver} for tests.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailure(Class)} to assert the
 * expected result.
 */
public final class OutcomeReceiverForTests<T> extends ExceptionFailureSyncCallback<T>
        implements OutcomeReceiver<T, Exception> {

    /**
     * Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout and fails if the {@code
     * inject...} method is called in the main thread.
     */
    public OutcomeReceiverForTests() {
        super();
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    public OutcomeReceiverForTests(int timeoutMs) {
        super(timeoutMs);
    }

    /** Constructor with custom settings. */
    public OutcomeReceiverForTests(int timeoutMs, boolean failIfCalledOnMainThread) {
        super(timeoutMs, failIfCalledOnMainThread);
    }
}
