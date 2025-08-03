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

import android.app.job.JobService;

/**
 * Custom {@link SyncCallback} implementation where used for checking methods in {@link JobService}
 * is called or executed. This implementation must only used in tests.
 *
 * <p>Use a {@link Boolean} type as a place holder for received on success. This {@link Boolean} is
 * used for checking a method has been called when calling {@link #assertResultReceived()}
 */
public final class JobServiceCallback extends NoFailureSyncCallback<Boolean> {

    /**
     * Injects a boolean {@code true} as Result. This is used for checking a stub method is called.
     */
    public void onJobFinished() {
        super.injectResult(true);
    }

    /**
     * Injects a boolean {@code false} as Result. This is used for checking a stub method is called.
     */
    public void onJobStopped() {
        super.injectResult(false);
    }

    /** Asserts {@link #onJobFinished} was called. */
    public void assertJobFinished() throws InterruptedException {
        assertResultReceived();
    }
}
