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

package com.android.ondevicepersonalization.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Process;
import android.os.StrictMode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.ExecutionException;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationExecutorsTests {

    @Test
    public void testBackgroundExecutorSettings() throws InterruptedException, ExecutionException {
        Runnable task = () -> {
            StrictMode.ThreadPolicy threadPolicy = StrictMode.getThreadPolicy();
            assertNotEquals(threadPolicy.toString(), StrictMode.ThreadPolicy.LAX.toString());
            assertEquals(Process.getThreadPriority(Process.myTid()),
                    Process.THREAD_PRIORITY_BACKGROUND);
        };
        OnDevicePersonalizationExecutors.getBackgroundExecutor().submit(task).get();
    }

    @Test
    public void testLightweightExecutorSettings() throws InterruptedException, ExecutionException {
        Runnable task = () -> {
            StrictMode.ThreadPolicy threadPolicy = StrictMode.getThreadPolicy();
            assertNotEquals(threadPolicy.toString(), StrictMode.ThreadPolicy.LAX.toString());
            assertEquals(Process.getThreadPriority(Process.myTid()),
                    Process.THREAD_PRIORITY_DEFAULT);
        };
        OnDevicePersonalizationExecutors.getLightweightExecutor().submit(task).get();
    }

    @Test
    public void testBlockingExecutorSettings() throws InterruptedException, ExecutionException {
        Runnable task = () -> {
            StrictMode.ThreadPolicy threadPolicy = StrictMode.getThreadPolicy();
            assertEquals(threadPolicy.toString(), StrictMode.ThreadPolicy.LAX.toString());
            assertEquals(Process.getThreadPriority(Process.myTid()),
                    Process.THREAD_PRIORITY_BACKGROUND
                            + Process.THREAD_PRIORITY_LESS_FAVORABLE);
        };
        OnDevicePersonalizationExecutors.getBlockingExecutor().submit(task).get();
    }
}
