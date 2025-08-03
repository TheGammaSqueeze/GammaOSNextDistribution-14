/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tradefed.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.android.ddmlib.IShellOutputReceiver;

import java.util.concurrent.TimeUnit;

class MockTestDeviceHelper {

    /**
     * Helper method to build a response to a {@link ITestDevice#executeShellCommand(String,
     * IShellOutputReceiver)} call.
     *
     * @param mockDevice the mock created {@link ITestDevice}
     * @param expectedCommand the shell command to expect or null to skip verification of command
     * @param response the response to simulate
     */
    @SuppressWarnings("unchecked")
    static void injectShellResponse(
            ITestDevice mockDevice, final String expectedCommand, final String response)
            throws Exception {
        if (expectedCommand != null) {
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] inputData = response.getBytes();
                                receiver.addOutput(inputData, 0, inputData.length);
                                receiver.flush();
                                return null;
                            })
                    .when(mockDevice)
                    .executeShellCommand(eq(expectedCommand), any());
        } else {
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] inputData = response.getBytes();
                                receiver.addOutput(inputData, 0, inputData.length);
                                receiver.flush();
                                return null;
                            })
                    .when(mockDevice)
                    .executeShellCommand(anyString(), any());
        }
    }

    /**
     * Helper method to build a response to a {@link ITestDevice#executeShellCommand(String,
     * IShellOutputReceiver, long, TimeUnit, int)} call.
     *
     * @param mockDevice the mock created {@link ITestDevice}
     * @param expectedCommand the shell command to expect or null to skip verification of command
     * @param expectedTimeout the shell timeout to expect
     * @param expectedTimeUnit the shell timeout unit to expect
     * @param expectedRetryAttempts the retry attempts to expect
     * @param response the response to simulate
     */
    @SuppressWarnings("unchecked")
    static void injectShellResponse(
            ITestDevice mockDevice,
            final String expectedCommand,
            final long expectedTimeout,
            final TimeUnit expectedTimeUnit,
            final int expectedRetryAttempts,
            final String response)
            throws Exception {
        if (expectedCommand != null) {
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] inputData = response.getBytes();
                                receiver.addOutput(inputData, 0, inputData.length);
                                receiver.flush();
                                return null;
                            })
                    .when(mockDevice)
                    .executeShellCommand(
                            eq(expectedCommand),
                            any(),
                            eq(expectedTimeout),
                            eq(expectedTimeout),
                            eq(expectedTimeUnit),
                            eq(expectedRetryAttempts));
        } else {
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] inputData = response.getBytes();
                                receiver.addOutput(inputData, 0, inputData.length);
                                receiver.flush();
                                return null;
                            })
                    .when(mockDevice)
                    .executeShellCommand(
                            anyString(),
                            any(),
                            eq(expectedTimeout),
                            eq(expectedTimeout),
                            eq(expectedTimeUnit),
                            eq(expectedRetryAttempts));
        }
    }
}
