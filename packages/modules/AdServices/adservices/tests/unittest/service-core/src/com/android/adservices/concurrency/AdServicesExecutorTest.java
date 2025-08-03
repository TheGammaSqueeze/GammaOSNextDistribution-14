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

package com.android.adservices.concurrency;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;

import com.android.adservices.service.common.compat.BuildCompatUtils;
import com.android.compatibility.common.util.ShellUtils;


import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class AdServicesExecutorTest {
    // Command to kill the adservices process
    public static final String KILL_ADSERVICES_CMD =
            "su 0 killall -9 com.google.android.adservices.api";

    @Before
    public void setup() {
        // TODO(b/265113689) Unsuppress the test for user builds post removing the `su` utility
        Assume.assumeTrue(BuildCompatUtils.isDebuggable());
        ShellUtils.runShellCommand(KILL_ADSERVICES_CMD);
    }

    @Test
    public void testCreateLightWeightThreadSuccess() throws Exception {
        ExecutorResult expectedResult =
                new ExecutorResult(
                        AdServicesExecutors.getAsyncThreadPolicy(),
                        Process.THREAD_PRIORITY_DEFAULT,
                        "lightweight-\\d{1,19}$");

        runTaskAndAssertResult(AdServicesExecutors.getLightWeightExecutor(), expectedResult);
    }

    @Test
    public void testCreateBackgroundThreadSuccess() throws Exception {
        ExecutorResult expectedResult =
                new ExecutorResult(
                        AdServicesExecutors.getIoThreadPolicy(),
                        Process.THREAD_PRIORITY_BACKGROUND,
                        "background-\\d{1,19}$");
        runTaskAndAssertResult(AdServicesExecutors.getBackgroundExecutor(), expectedResult);
    }

    @Test
    public void testCreateBlockingThreadSuccess() throws Exception {
        ExecutorResult expectedResult =
                new ExecutorResult(
                        ThreadPolicy.LAX,
                        Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_LESS_FAVORABLE,
                        "blocking-\\d{1,19}$");
        runTaskAndAssertResult(AdServicesExecutors.getBlockingExecutor(), expectedResult);
    }

    @Test
    public void testCreateScheduledThreadSuccess() throws Exception {
        ExecutorResult expectedResult =
                new ExecutorResult(
                        AdServicesExecutors.getIoThreadPolicy(),
                        Process.THREAD_PRIORITY_DEFAULT,
                        "scheduled-\\d{1,19}$");
        runTaskAndAssertResult(AdServicesExecutors.getScheduler(), expectedResult);
    }

    private void runTaskAndAssertResult(
            ExecutorService executorService, ExecutorResult expectedResult) throws Exception {
        Callable<ExecutorResult> task =
                () ->
                        new ExecutorResult(
                                StrictMode.getThreadPolicy(),
                                Process.getThreadPriority(Process.myTid()),
                                Thread.currentThread().getName());
        ExecutorResult actualResult = executorService.submit(task).get();

        assertWithMessage("Thread priority")
                .that(actualResult.mPriority)
                .isEqualTo(expectedResult.mPriority);
        assertWithMessage("Thread name")
                .that(actualResult.mThreadName)
                .matches(expectedResult.mThreadName);
        assertThat(actualResult.mThreadPolicy.toString())
                .isEqualTo(expectedResult.mThreadPolicy.toString());
    }

    private static final class ExecutorResult {
        private ThreadPolicy mThreadPolicy;
        private int mPriority;
        private String mThreadName;

        ExecutorResult(ThreadPolicy threadPolicy, int priority, String threadName) {
            mThreadPolicy = threadPolicy;
            mPriority = priority;
            mThreadName = threadName;
        }
    }
}
