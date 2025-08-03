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

package com.android.tradefed.testtype.junit4;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

@RunWith(JUnit4.class)
public class JUnit4ResultForwarderTest {
    private ITestInvocationListener mTestInvocationListener;
    private JUnit4ResultForwarder mJUnit4ResultForwarder;
    private Description mDescription;
    private DeviceJUnit4ClassRunner.TestLogData mTestLogData;

    /**
     * Number of logs produced for test. Set to a high number to be able to test possible
     * multithreading issues
     */
    private int mTimesToLogMessage = 1000;

    @Before
    public void setUp() throws Exception {
        mTestInvocationListener = mock(ITestInvocationListener.class);
        mJUnit4ResultForwarder = new JUnit4ResultForwarder(mTestInvocationListener);
        mDescription = Description.createTestDescription("LOGS", "LOGS");
        mTestLogData = new DeviceJUnit4ClassRunner.TestLogData();
        mTestLogData.apply(mock(Statement.class), mDescription);
    }

    /** Test that all messages passed to addTestLog() are logged after testFinished() */
    @Test
    public void testLoggedAfterTestFinished() throws Exception {
        mJUnit4ResultForwarder.testStarted(mDescription);
        for (int i = 0; i < mTimesToLogMessage; i++) {
            mTestLogData.addTestLog(
                    "", LogDataType.ADB_HOST_LOG, new ByteArrayInputStreamSource(new byte[] {}));
        }
        mJUnit4ResultForwarder.testFinished(mDescription);
        verify(mTestInvocationListener, times(mTimesToLogMessage))
                .testLog(anyString(), any(LogDataType.class), any(InputStreamSource.class));
    }

    /**
     * Test that all messages passed to addTestLog() are logged after test is finished. Same as
     * previous test, but also verifies individual messages. Had to do separately and run on a
     * smaller number of logs as it's very time consuming
     */
    @Test
    public void testIndividualLogsLoggedAfterTestFinished() throws Exception {
        int timesToLogMessage = 100;
        mJUnit4ResultForwarder.testStarted(mDescription);
        for (int i = 0; i < timesToLogMessage; i++) {
            mTestLogData.addTestLog(
                    "" + i,
                    LogDataType.ADB_HOST_LOG,
                    new ByteArrayInputStreamSource(new byte[] {}));
        }
        mJUnit4ResultForwarder.testFinished(mDescription);

        // total number of testLog(*) invocations
        verify(mTestInvocationListener, times(timesToLogMessage))
                .testLog(anyString(), any(LogDataType.class), any(InputStreamSource.class));
        // invocations of testLog(*) for specific logs
        for (int i = 0; i < timesToLogMessage; i++) {
            verify(mTestInvocationListener, times(1))
                    .testLog(eq("" + i), any(LogDataType.class), any(InputStreamSource.class));
        }
    }

    /** Test that all messages passed to addTestLog() are logged before testFinished is called */
    @Test
    public void testLoggedBeforeTestFinished() throws Exception {
        mJUnit4ResultForwarder.testStarted(mDescription);
        for (int i = 0; i < mTimesToLogMessage; i++) {
            mTestLogData.addTestLog(
                    "", LogDataType.ADB_HOST_LOG, new ByteArrayInputStreamSource(new byte[] {}));
        }
        // make sure LogUploaderThread has time to catch up
        Thread.sleep(100);

        verify(mTestInvocationListener, times(mTimesToLogMessage))
                .testLog(anyString(), any(LogDataType.class), any(InputStreamSource.class));
        mJUnit4ResultForwarder.testFinished(mDescription);
    }

    /**
     * Test that all messages passed to addTestLog() are logged if multiple tests are
     * started/finished
     */
    @Test
    public void testLoggedWithMultipleTestExecutions() throws Exception {
        for (int i = 0; i < 5; i++) {
            mJUnit4ResultForwarder.testStarted(mDescription);
            mTestLogData.addTestLog(
                    "", LogDataType.ADB_HOST_LOG, new ByteArrayInputStreamSource(new byte[] {}));
            mJUnit4ResultForwarder.testFinished(mDescription);
        }
        verify(mTestInvocationListener, times(5))
                .testLog(anyString(), any(LogDataType.class), any(InputStreamSource.class));
    }
}
