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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link com.android.tradefed.testtype.binary.KUnitModuleTest}. */
@RunWith(JUnit4.class)
public class KUnitModuleTestTest {
    private ITestInvocationListener mListener = null;
    private ITestDevice mMockDevice = null;
    private TestInformation mTestInfo;

    /** Object under test. */
    private KUnitModuleTest mKUnitModuleTest;

    private final CommandResult mSuccessResult;
    private final CommandResult mFailedResult;

    private static final String MODULE_01 = "kunit-module-01.ko";
    private static final String MODULE_NAME_01 = "kunit_module_01";
    private static final String KTAP_RESULTS_01 =
            "KTAP version 1\n"
                    + "1..1\n"
                    + "  KTAP version 1\n"
                    + "  1..2\n"
                    + "    KTAP version 1\n"
                    + "    1..1\n"
                    + "    # test_1: initializing test_1\n"
                    + "    ok 1 test_1\n"
                    + "  ok 1 example_test_1\n"
                    + "    KTAP version 1\n"
                    + "    1..2\n"
                    + "    ok 1 test_1 # SKIP test_1 skipped\n"
                    + "    ok 2 test_2\n"
                    + "  ok 2 example_test_2\n"
                    + "ok 1 main_test_01\n";

    private static final String MODULE_02 = "kunit-module-02.ko";
    private static final String MODULE_NAME_02 = "kunit_module_02";
    private static final String KTAP_RESULTS_02 =
            "KTAP version 1\n"
                    + "1..1\n"
                    + "  KTAP version 1\n"
                    + "  1..2\n"
                    + "    KTAP version 1\n"
                    + "    1..1\n"
                    + "    # test_1: initializing test_1\n"
                    + "    ok 1 test_1\n"
                    + "  ok 1 example_test_1\n"
                    + "    KTAP version 1\n"
                    + "    1..2\n"
                    + "    ok 1 test_1 # SKIP test_1 skipped\n"
                    + "    ok 2 test_2\n"
                    + "  ok 2 example_test_2\n"
                    + "ok 1 main_test_02\n";

    public KUnitModuleTestTest() {
        mSuccessResult = new CommandResult(CommandStatus.SUCCESS);
        mSuccessResult.setStdout("ffffffffffff\n");
        mSuccessResult.setExitCode(0);

        mFailedResult = new CommandResult(CommandStatus.FAILED);
        mFailedResult.setStdout("");
        mFailedResult.setExitCode(-1);
    }

    @Before
    public void setUp() throws Exception {
        mListener = Mockito.mock(ITestInvocationListener.class);
        mMockDevice = Mockito.mock(ITestDevice.class);
        InvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);

        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mTestInfo = TestInformation.newBuilder().build();

        // The baseline setup is the happy path, two KUnit test modules with no unexpected errors.
        // Specific error condition tests will overwrit certain aspects of this inside their
        // indiviual test method.
        mKUnitModuleTest =
                new KUnitModuleTest() {
                    @Override
                    public String findBinary(String binary) {
                        return binary;
                    }
                };

        mKUnitModuleTest.setDevice(mMockDevice);

        OptionSetter setter = new OptionSetter(mKUnitModuleTest);
        setter.setOptionValue("binary", MODULE_NAME_01, MODULE_01);
        setter.setOptionValue("binary", MODULE_NAME_02, MODULE_02);

        // For 2 modules: first rmmod call expect fail, second rmmod call expect pass
        when(mMockDevice.executeShellV2Command(
                        startsWith(String.format(KUnitModuleTest.RMMOD_COMMAND_FMT, ""))))
                .thenReturn(mFailedResult)
                .thenReturn(mSuccessResult)
                .thenReturn(mFailedResult)
                .thenReturn(mSuccessResult);
        when(mMockDevice.isDebugfsMounted()).thenReturn(false);

        when(mMockDevice.getChildren(KUnitModuleTest.KUNIT_DEBUGFS_PATH))
                .thenReturn(new String[0]) // module 1, call 1
                .thenReturn(new String[] {MODULE_NAME_01}) // module 1, call 2
                .thenReturn(new String[0]) // module 2, call 1
                .thenReturn(new String[] {MODULE_NAME_02}); // module 2, call 2

        when(mMockDevice.executeShellV2Command(
                        startsWith(String.format(KUnitModuleTest.INSMOD_COMMAND_FMT, "")),
                        anyLong(),
                        any()))
                .thenReturn(mSuccessResult);

        when(mMockDevice.pullFileContents(
                        String.format(KUnitModuleTest.KUNIT_RESULTS_FMT, MODULE_NAME_01)))
                .thenReturn(KTAP_RESULTS_01);
        when(mMockDevice.pullFileContents(
                        String.format(KUnitModuleTest.KUNIT_RESULTS_FMT, MODULE_NAME_02)))
                .thenReturn(KTAP_RESULTS_02);
    }

    @Test
    public void test_success() throws DeviceNotAvailableException, ConfigurationException {

        // Run test
        mKUnitModuleTest.run(mTestInfo, mListener);

        TestDescription[] testDescriptions = {
            new TestDescription(MODULE_01, "main_test_01"),
            new TestDescription(MODULE_02, "main_test_02"),
        };

        Mockito.verify(mListener, Mockito.times(1)).testRunStarted(Mockito.any(), eq(2));
        for (TestDescription testDescription : testDescriptions) {
            Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testDescription));
            Mockito.verify(mListener, Mockito.times(1))
                    .testEnded(
                            Mockito.eq(testDescription),
                            Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));
        }

        Mockito.verify(mListener, Mockito.times(1))
                .testRunEnded(
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, MetricMeasurement.Metric>>any());
    }

    @Test
    public void test_module_load_fail() throws DeviceNotAvailableException, ConfigurationException {

        // First module loads successfully
        when(mMockDevice.executeShellV2Command(
                        startsWith(String.format(KUnitModuleTest.INSMOD_COMMAND_FMT, MODULE_01)),
                        anyLong(),
                        any()))
                .thenReturn(mSuccessResult);
        // Second module set fail on load
        when(mMockDevice.executeShellV2Command(
                        startsWith(String.format(KUnitModuleTest.INSMOD_COMMAND_FMT, MODULE_02)),
                        anyLong(),
                        any()))
                .thenReturn(mFailedResult);

        // Run test
        mKUnitModuleTest.run(mTestInfo, mListener);

        ArrayList<Pair<TestDescription, Boolean>> expectedTestResults =
                new ArrayList<>() {
                    {
                        add(Pair.create(new TestDescription(MODULE_01, "main_test_01"), true));
                        add(Pair.create(new TestDescription(MODULE_02, MODULE_02), false));
                    }
                };

        Mockito.verify(mListener, Mockito.times(1)).testRunStarted(Mockito.any(), eq(2));
        for (Pair<TestDescription, Boolean> testResult : expectedTestResults) {
            Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testResult.first));
            if (!testResult.second) {
                Mockito.verify(mListener, Mockito.times(1))
                        .testFailed(Mockito.eq(testResult.first), any(FailureDescription.class));
            }
            Mockito.verify(mListener, Mockito.times(1))
                    .testEnded(
                            Mockito.eq(testResult.first),
                            Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));
        }

        Mockito.verify(mListener, Mockito.times(1))
                .testRunEnded(
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, MetricMeasurement.Metric>>any());
    }

    @Test
    public void test_ktap_parse_fail() throws DeviceNotAvailableException, ConfigurationException {

        // Remove half of the ktap to force parse error for module02
        when(mMockDevice.pullFileContents(
                        String.format(KUnitModuleTest.KUNIT_RESULTS_FMT, MODULE_NAME_02)))
                .thenReturn(KTAP_RESULTS_02.substring(0, KTAP_RESULTS_02.length() / 2));

        // Run test
        mKUnitModuleTest.run(mTestInfo, mListener);

        ArrayList<Pair<TestDescription, Boolean>> expectedTestResults =
                new ArrayList<>() {
                    {
                        add(Pair.create(new TestDescription(MODULE_01, "main_test_01"), true));
                        add(Pair.create(new TestDescription(MODULE_02, MODULE_02), false));
                    }
                };
        Mockito.verify(mListener, Mockito.times(1)).testRunStarted(Mockito.any(), eq(2));
        for (Pair<TestDescription, Boolean> testResult : expectedTestResults) {
            Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testResult.first));
            if (!testResult.second) {
                Mockito.verify(mListener, Mockito.times(1))
                        .testFailed(Mockito.eq(testResult.first), any(FailureDescription.class));
            }
            Mockito.verify(mListener, Mockito.times(1))
                    .testEnded(
                            Mockito.eq(testResult.first),
                            Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));
        }

        Mockito.verify(mListener, Mockito.times(1))
                .testRunEnded(
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, MetricMeasurement.Metric>>any());
    }

    @Test
    public void test_missing_ktap_results_file()
            throws DeviceNotAvailableException, ConfigurationException {

        when(mMockDevice.getChildren(KUnitModuleTest.KUNIT_DEBUGFS_PATH))
                .thenReturn(new String[0]) // module 1, call 1
                .thenReturn(new String[] {MODULE_NAME_01}) // module 1, call 2
                .thenReturn(new String[0]) // module 2, call 1
                .thenReturn(
                        new String[0]); // This is the injected error, nothing for module 2, call 2

        // Run test
        mKUnitModuleTest.run(mTestInfo, mListener);

        ArrayList<Pair<TestDescription, Boolean>> expectedTestResults =
                new ArrayList<>() {
                    {
                        add(Pair.create(new TestDescription(MODULE_01, "main_test_01"), true));
                        add(Pair.create(new TestDescription(MODULE_02, MODULE_02), false));
                    }
                };
        Mockito.verify(mListener, Mockito.times(1)).testRunStarted(Mockito.any(), eq(2));
        for (Pair<TestDescription, Boolean> testResult : expectedTestResults) {
            Mockito.verify(mListener, Mockito.times(1)).testStarted(Mockito.eq(testResult.first));
            if (!testResult.second) {
                Mockito.verify(mListener, Mockito.times(1))
                        .testFailed(Mockito.eq(testResult.first), any(FailureDescription.class));
            }
            Mockito.verify(mListener, Mockito.times(1))
                    .testEnded(
                            Mockito.eq(testResult.first),
                            Mockito.eq(new HashMap<String, MetricMeasurement.Metric>()));
        }

        Mockito.verify(mListener, Mockito.times(1))
                .testRunEnded(
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, MetricMeasurement.Metric>>any());
    }
}
