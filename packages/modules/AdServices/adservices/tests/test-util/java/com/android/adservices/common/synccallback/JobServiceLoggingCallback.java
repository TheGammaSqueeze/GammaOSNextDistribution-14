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

package com.android.adservices.common.synccallback;

import android.app.job.JobService;

import com.android.adservices.common.SyncCallback;

/**
 * A synchronized callback used for logging {@link JobService} on testing purpose.
 *
 * <p>The logging methods in {@link AdServicesJobServiceLogger} are offloaded to a separate thread.
 * In order to make the test result deterministic, use this callback to help wait for the completion
 * of such logging methods.
 */
public class JobServiceLoggingCallback extends SyncCallback<Boolean, Void> {
    /**
     * Injects a boolean {@code true} as Result. This is used for checking a stub method is called.
     */
    public void onLoggingMethodCalled() {
        super.injectResult(true);
    }

    /** Assert the corresponding logging method has happened. */
    public void assertLoggingFinished() throws InterruptedException {
        assertResultReceived();
    }
}
