/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.testtype.suite.retry;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map.Entry;

/** Run unit tests for {@link ResultsPlayer}. */
@RunWith(JUnit4.class)
public class ResultsPlayerTest {
    private ResultsPlayer mPlayer;
    @Mock ITestInvocationListener mMockListener;
    private IInvocationContext mContext;
    private TestInformation mTestInfo;
    @Mock ITestDevice mMockDevice;
    @Mock IDevice mMockIDevice;
    @Mock IConfiguration mMockConfig;
    @Mock ILeveledLogOutput mMockLogOutput;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = new InvocationContext();

        when(mMockConfig.getLogOutput()).thenReturn(mMockLogOutput);
        when(mMockLogOutput.getLogLevel()).thenReturn(LogLevel.VERBOSE);
        mMockLogOutput.setLogLevel(LogLevel.WARN);
        mMockLogOutput.setLogLevel(LogLevel.VERBOSE);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mContext).build();

        mPlayer = new ResultsPlayer();
        mPlayer.setConfiguration(mMockConfig);
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());

        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
    }

    /** Test that the replay of a full test run is properly working. */
    @Test
    public void testReplay() throws DeviceNotAvailableException {
        mPlayer.addToReplay(null, createTestRunResult("run1", 2, 1), null);

        TestDescription test = new TestDescription("test.class", "method0");
        TestDescription testFail = new TestDescription("test.class", "fail0");

        mPlayer.run(mTestInfo, mMockListener);

        Truth.assertThat(
                        mContext.getBuildInfos()
                                .get(0)
                                .getBuildAttributes()
                                .get(ResultsPlayer.REPLAY_DONE))
                .isEqualTo("true");

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted("run1", 2);
        inOrder.verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        inOrder.verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).testStarted(Mockito.eq(testFail), Mockito.anyLong());
        inOrder.verify(mMockListener).testFailed(testFail, "fail0");
        inOrder.verify(mMockListener)
                .testEnded(
                        Mockito.eq(testFail),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).testRunEnded(500L, new HashMap<String, Metric>());

        verify(mMockDevice, times(1)).waitForDeviceAvailable();
        verify(mMockListener).testRunStarted("run1", 2);
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testStarted(Mockito.eq(testFail), Mockito.anyLong());
        verify(mMockListener).testFailed(testFail, "fail0");
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testFail),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunEnded(500L, new HashMap<String, Metric>());
    }

    /** Test that when replaying a module we properly replay all the results. */
    @Test
    public void testReplayModules() throws DeviceNotAvailableException {
        IInvocationContext module1 = new InvocationContext();
        mPlayer.addToReplay(module1, createTestRunResult("run1", 2, 1), null);
        IInvocationContext module2 = new InvocationContext();
        mPlayer.addToReplay(module2, createTestRunResult("run2", 2, 1), null);

        TestDescription test = new TestDescription("test.class", "method0");
        TestDescription testFail = new TestDescription("test.class", "fail0");

        test = new TestDescription("test.class", "method0");
        testFail = new TestDescription("test.class", "fail0");

        mPlayer.run(mTestInfo, mMockListener);

        verify(mMockDevice, times(1)).waitForDeviceAvailable();
        verify(mMockListener).testModuleStarted(module1);
        verify(mMockListener).testRunStarted("run1", 2);
        verify(mMockListener, times(2)).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener, times(2))
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener, times(2)).testStarted(Mockito.eq(testFail), Mockito.anyLong());
        verify(mMockListener, times(2)).testFailed(testFail, "fail0");
        verify(mMockListener, times(2))
                .testEnded(
                        Mockito.eq(testFail),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener, times(2)).testRunEnded(500L, new HashMap<String, Metric>());
        verify(mMockListener, times(2)).testModuleEnded();
        // Second module
        verify(mMockListener).testModuleStarted(module2);
        verify(mMockListener).testRunStarted("run2", 2);
    }

    /** Test that the replay of a single requested test case is working. */
    @Test
    public void testReplay_oneTest() throws DeviceNotAvailableException {
        TestDescription test = new TestDescription("test.class", "method0");
        TestResult result = new TestResult();
        result.setStatus(TestStatus.ASSUMPTION_FAILURE);
        result.setStartTime(0L);
        result.setEndTime(10L);
        result.setStackTrace("assertionfailure");

        Entry<TestDescription, TestResult> entry = new SimpleEntry<>(test, result);
        mPlayer.addToReplay(null, createTestRunResult("run1", 2, 1), entry);

        mPlayer.run(mTestInfo, mMockListener);

        verify(mMockDevice, times(1)).waitForDeviceAvailable();
        verify(mMockListener).testRunStarted("run1", 1);
        // Only the provided test is re-run
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testAssumptionFailure(test, "assertionfailure");
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunEnded(500L, new HashMap<String, Metric>());
    }

    /** Test requesting several tests to re-run. */
    @Test
    public void testReplay_MultiTest() throws DeviceNotAvailableException {
        TestRunResult runResult = createTestRunResult("run1", 5, 1);

        TestDescription test = new TestDescription("test.class", "method0");
        TestResult result = new TestResult();
        result.setStatus(TestStatus.ASSUMPTION_FAILURE);
        result.setStartTime(0L);
        result.setEndTime(10L);
        result.setStackTrace("assertionfailure");
        Entry<TestDescription, TestResult> entry = new SimpleEntry<>(test, result);
        mPlayer.addToReplay(null, runResult, entry);

        TestDescription test2 = new TestDescription("test.class", "fail0");
        TestResult result2 = new TestResult();
        result2.setStatus(TestStatus.FAILURE);
        result2.setStartTime(0L);
        result2.setEndTime(10L);
        result2.setStackTrace("fail0");
        Entry<TestDescription, TestResult> entry2 = new SimpleEntry<>(test2, result2);
        mPlayer.addToReplay(null, runResult, entry2);

        mPlayer.run(mTestInfo, mMockListener);

        verify(mMockDevice, times(1)).waitForDeviceAvailable();
        verify(mMockListener).testRunStarted("run1", 2);
        // Only the provided test is re-run
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testAssumptionFailure(test, "assertionfailure");
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        verify(mMockListener).testFailed(test2, "fail0");
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunEnded(500L, new HashMap<String, Metric>());
    }

    private TestRunResult createTestRunResult(String runName, int testCount, int failCount) {
        TestRunResult result = new TestRunResult();
        result.testRunStarted(runName, testCount);
        for (int i = 0; i < testCount - failCount; i++) {
            TestDescription test = new TestDescription("test.class", "method" + i);
            result.testStarted(test);
            result.testEnded(test, new HashMap<String, Metric>());
        }
        for (int i = 0; i < failCount; i++) {
            TestDescription test = new TestDescription("test.class", "fail" + i);
            result.testStarted(test);
            result.testFailed(test, "fail" + i);
            result.testEnded(test, new HashMap<String, Metric>());
        }
        result.testRunEnded(500L, new HashMap<String, Metric>());
        return result;
    }
}
