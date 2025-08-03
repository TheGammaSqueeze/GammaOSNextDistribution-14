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
package com.android.tradefed.testtype.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link com.android.tradefed.testtype.binary.KernelTargetTest}. */
@RunWith(JUnit4.class)
public class KernelTargetTestTest {
    private final String testName1 = "testName1";
    private final String testCmd1 = "cmd1";
    private final String testName2 = "testName2";
    private final String testCmd2 = "cmd2";

    private ITestInvocationListener mListener = null;
    private ITestDevice mMockITestDevice = null;
    private KernelTargetTest mKernelTargetTest;
    private CommandResult mCommandResult;

    private TestInformation mTestInfo;

    /** Helper to initialize the various EasyMocks we'll need. */
    @Before
    public void setUp() throws Exception {
        mListener = Mockito.mock(ITestInvocationListener.class);
        mMockITestDevice = Mockito.mock(ITestDevice.class);
        InvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockITestDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mTestInfo = TestInformation.newBuilder().build();
    }

    /** Test that custom exit code causes a skip */
    @Test
    public void test_exitCodeSkip() throws ConfigurationException {
        mKernelTargetTest = new KernelTargetTest();
        OptionSetter setter = new OptionSetter(mKernelTargetTest);
        setter.setOptionValue("exit-code-skip", "32");
        setter.setOptionValue("test-command-line", testName1, testCmd1);
        mCommandResult = new CommandResult(CommandStatus.SUCCESS);
        mCommandResult.setExitCode(32);
        mKernelTargetTest.setDevice(mMockITestDevice);
        TestDescription testDescription = new TestDescription(testName1, testName1);
        mKernelTargetTest.checkCommandResult(mCommandResult, mListener, testDescription);
        Mockito.verify(mListener, Mockito.times(1)).testIgnored(Mockito.eq(testDescription));
    }

    /** Test that mismatched skip exit code does not cause a skip */
    @Test
    public void test_mismatchedExitCodeSkip() throws ConfigurationException {
        mKernelTargetTest = new KernelTargetTest();
        OptionSetter setter = new OptionSetter(mKernelTargetTest);
        setter.setOptionValue("exit-code-skip", "32");
        setter.setOptionValue("test-command-line", testName1, testCmd1);
        mCommandResult = new CommandResult(CommandStatus.SUCCESS);
        mCommandResult.setExitCode(20);
        mKernelTargetTest.setDevice(mMockITestDevice);
        TestDescription testDescription = new TestDescription(testName1, testName1);
        mKernelTargetTest.checkCommandResult(mCommandResult, mListener, testDescription);
        Mockito.verify(mListener, Mockito.never()).testIgnored(Mockito.eq(testDescription));
    }

    /** Test that null skip exit code does not cause a skip */
    @Test
    public void test_noExitCodeSkip() throws ConfigurationException {
        mKernelTargetTest = new KernelTargetTest();
        OptionSetter setter = new OptionSetter(mKernelTargetTest);
        setter.setOptionValue("test-command-line", testName1, testCmd1);
        mCommandResult = new CommandResult(CommandStatus.SUCCESS);
        mCommandResult.setExitCode(32);
        mKernelTargetTest.setDevice(mMockITestDevice);
        TestDescription testDescription = new TestDescription(testName1, testName1);
        mKernelTargetTest.checkCommandResult(mCommandResult, mListener, testDescription);
        Mockito.verify(mListener, Mockito.never()).testIgnored(Mockito.eq(testDescription));
    }

    /** Test the parsing of kernel version strings */
    @Test
    public void test_parseKernelVersion() {
        mKernelTargetTest = new KernelTargetTest();
        assertEquals(
                mKernelTargetTest.parseKernelVersion(
                        "6.1.25-android14-11-g34fde9ec08a3-ab10675345"),
                mKernelTargetTest.parseKernelVersion("6.1.25"));
        assertEquals(
                mKernelTargetTest.parseKernelVersion("5.10"),
                mKernelTargetTest.parseKernelVersion("5.10.0"));
        assertTrue(
                mKernelTargetTest.parseKernelVersion("4.14.328")
                        > mKernelTargetTest.parseKernelVersion("4.14.327"));
        assertTrue(
                mKernelTargetTest.parseKernelVersion("4.14.328")
                        > mKernelTargetTest.parseKernelVersion("4.14"));
        assertTrue(
                mKernelTargetTest.parseKernelVersion("4.14.328")
                        < mKernelTargetTest.parseKernelVersion("4.14.329"));
        assertTrue(
                mKernelTargetTest.parseKernelVersion("4.14.328")
                        < mKernelTargetTest.parseKernelVersion("4.15"));
    }

    /** Test where two kernel versions meet the minimum requirement */
    @Test
    public void testRun_kernelTwoMatches()
            throws DeviceNotAvailableException, ConfigurationException {
        mKernelTargetTest =
                new KernelTargetTest() {
                    @Override
                    public String findBinary(String binary) {
                        return binary;
                    }

                    @Override
                    protected void checkCommandResult(
                            CommandResult result,
                            ITestInvocationListener listener,
                            TestDescription description) {}

                    @Override
                    public Integer getDeviceKernelVersion() throws DeviceNotAvailableException {
                        return super.parseKernelVersion(
                                "6.1.25-android14-11-g34fde9ec08a3-ab10675345");
                    }
                };
        mKernelTargetTest.setDevice(mMockITestDevice);
        // Set test commands
        OptionSetter setter = new OptionSetter(mKernelTargetTest);
        setter.setOptionValue("test-command-line", testName1, testCmd1);
        setter.setOptionValue("min-kernel-version", testName1, "5.10");
        setter.setOptionValue("test-command-line", testName2, testCmd2);
        mKernelTargetTest.run(mTestInfo, mListener);
        Mockito.verify(mListener, Mockito.times(1)).testRunStarted(Mockito.any(), eq(2));

        // Both tests should run
        TestDescription testDescription = new TestDescription(testName1, testName1);
        Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testDescription));
        Mockito.verify(mListener, Mockito.never()).testIgnored(Mockito.eq(testDescription));
        Mockito.verify(mListener, Mockito.times(1))
                .testEnded(
                        Mockito.eq(testDescription),
                        Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));

        TestDescription testDescription2 = new TestDescription(testName2, testName2);
        Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testDescription2));
        Mockito.verify(mListener, Mockito.never()).testIgnored(Mockito.eq(testDescription2));
        Mockito.verify(mListener, Mockito.times(1))
                .testEnded(
                        Mockito.eq(testDescription2),
                        Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));
        Mockito.verify(mListener, Mockito.times(1))
                .testRunEnded(
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, MetricMeasurement.Metric>>any());
    }

    /** Test where one min kernel version dependency is not met and test is ignored */
    @Test
    public void testRun_kernelOneMismatch()
            throws DeviceNotAvailableException, ConfigurationException {
        mKernelTargetTest =
                new KernelTargetTest() {
                    @Override
                    public String findBinary(String binary) {
                        return binary;
                    }

                    @Override
                    protected void checkCommandResult(
                            CommandResult result,
                            ITestInvocationListener listener,
                            TestDescription description) {}

                    @Override
                    public Integer getDeviceKernelVersion() throws DeviceNotAvailableException {
                        return super.parseKernelVersion("4.14.328-00126-g67419faf9ff6-ab11001899");
                    }
                };
        mKernelTargetTest.setDevice(mMockITestDevice);
        // Set test commands
        OptionSetter setter = new OptionSetter(mKernelTargetTest);
        setter.setOptionValue("test-command-line", testName1, testCmd1);
        setter.setOptionValue("min-kernel-version", testName1, "5.10.0");
        setter.setOptionValue("test-command-line", testName2, testCmd2);
        setter.setOptionValue("min-kernel-version", testName2, "4.14");
        mKernelTargetTest.run(mTestInfo, mListener);
        Mockito.verify(mListener, Mockito.times(1)).testRunStarted(Mockito.any(), eq(2));

        // First test should be ignored
        TestDescription testDescription = new TestDescription(testName1, testName1);
        Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testDescription));
        Mockito.verify(mListener, Mockito.times(1)).testIgnored(Mockito.eq(testDescription));
        Mockito.verify(mListener, Mockito.times(1))
                .testEnded(
                        Mockito.eq(testDescription),
                        Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));

        // Second test should be run
        TestDescription testDescription2 = new TestDescription(testName2, testName2);
        Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testDescription2));
        Mockito.verify(mListener, Mockito.never()).testIgnored(Mockito.eq(testDescription2));
        Mockito.verify(mListener, Mockito.times(1))
                .testEnded(
                        Mockito.eq(testDescription2),
                        Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));
        Mockito.verify(mListener, Mockito.times(1))
                .testRunEnded(
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, MetricMeasurement.Metric>>any());
    }
}
