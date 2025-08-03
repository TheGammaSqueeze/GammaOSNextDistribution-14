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

package com.android.devicelockcontroller.util;

import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public class ThreadAssertsTest {
    @Before
    public void setUp() {
        ApplicationProvider.getApplicationContext();
    }

    @Test
    public void assertWorkerThread_workThread() throws Exception {
        runOnWorkThread(() -> ThreadAsserts.assertWorkerThread(
                "assertWorkerThread_workThread"));
    }

    @Test
    public void assertWorkerThread_mainThread_shouldThrowException() {
        assertThrows(IllegalStateException.class,
                () -> ThreadAsserts.assertWorkerThread("assertWorkerThread_workThread"));
    }

    @Test
    public void assertMainThread_mainThread() {
        ThreadAsserts.assertMainThread("assertMainThread_mainThread");
    }

    @Test
    public void assertMainThread_workThread_shouldThrowException() throws Exception {
        runOnWorkThread(() -> assertThrows(IllegalStateException.class,
                () -> ThreadAsserts.assertMainThread("assertWorkerThread_workThread")));
    }

    private static void runOnWorkThread(Runnable task)
            throws InterruptedException, ExecutionException {
        Executors.newSingleThreadExecutor().submit(task).get();
    }
}
