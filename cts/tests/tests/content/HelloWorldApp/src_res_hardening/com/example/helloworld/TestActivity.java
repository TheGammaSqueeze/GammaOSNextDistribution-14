/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.example.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.Nullable;

import com.example.helloworld.lib.TestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TestActivity extends Activity {
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Respond to the test with the pid of this application.
        final Intent pidResponse = new Intent(TestUtils.TEST_STATUS_ACTION);
        pidResponse.putExtra(TestUtils.PID_STATUS_PID_KEY, Process.myPid());
        sendBroadcast(pidResponse);

        // Run the test in a separate thread so that onCreate can finish.
        mExecutorService.execute(this::runTest);
    }

    private void runTest() {
        try {
            final Intent intent = getIntent();
            final String testName = intent.getStringExtra(TestUtils.TEST_NAME_EXTRA_KEY);
            final boolean assertionType = intent.getBooleanExtra(
                    TestUtils.TEST_ASSERT_SUCCESS_EXTRA_KEY, true);

            final Method testMethod = TestUtils.class.getMethod(testName, Resources.class,
                    TestUtils.AssertionType.class);
            testMethod.invoke(null, getResources(),
                    assertionType ? TestUtils.AssertionType.ASSERT_SUCCESS
                            : TestUtils.AssertionType.ASSERT_READ_FAILURE);
        } catch (AssertionError | Exception e) {
            final Throwable t = (e instanceof InvocationTargetException) ? e.getCause() : e;
            final Intent failureMessage = new Intent(TestUtils.TEST_STATUS_ACTION);
            failureMessage.putExtra(TestUtils.TEST_STATUS_RESULT_KEY,
                    String.format("%s: %s\n%s",
                            t.getClass().getName(),
                            t.getMessage(),
                            Arrays.stream(t.getStackTrace())
                                    .map(Object::toString).collect(Collectors.joining("\n"))));
            sendBroadcast(failureMessage);
            return;
        }

        // Report that the test finished without crashing.
        final Intent successMessage = new Intent(TestUtils.TEST_STATUS_ACTION);
        successMessage.putExtra(TestUtils.TEST_STATUS_RESULT_KEY,
                TestUtils.TEST_STATUS_RESULT_SUCCESS);
        sendBroadcast(successMessage);
    }
}
