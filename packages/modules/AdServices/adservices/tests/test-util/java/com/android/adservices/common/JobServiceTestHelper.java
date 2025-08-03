/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

import android.app.job.JobService;
import android.util.Log;

public final class JobServiceTestHelper {

    private static final String TAG = JobServiceTestHelper.class.getSimpleName();

    private JobServiceTestHelper() {
        throw new UnsupportedOperationException();
    }

    /** Creates callback for tests where jobFinished is invoked. */
    public static JobServiceCallback createJobFinishedCallback(JobService jobService) {
        JobServiceCallback callback = new JobServiceCallback();

        doAnswer(
                        unusedInvocation -> {
                            Log.d(TAG, "Calling callback.onJobFinished() on " + unusedInvocation);
                            callback.onJobFinished();
                            return null;
                        })
                .when(jobService)
                .jobFinished(any(), anyBoolean());
        return callback;
    }

    /** Creates a callback for tests where onStopJob is invoked. */
    public static JobServiceCallback createOnStopJobCallback(JobService jobService) {
        JobServiceCallback callback = new JobServiceCallback();

        doAnswer(
                        invocation -> {
                            Log.d(TAG, "Calling callback.onJobStopped() on " + invocation);
                            try {
                                invocation.callRealMethod();
                            } finally {
                                callback.onJobStopped();
                            }
                            return null;
                        })
                .when(jobService)
                .onStopJob(any());

        return callback;
    }
}
