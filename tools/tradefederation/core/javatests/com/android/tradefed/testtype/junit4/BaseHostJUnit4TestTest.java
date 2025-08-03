/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ListInstrumentationParser;

import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;

/** Unit tests for {@link BaseHostJUnit4Test}. */
@RunWith(JUnit4.class)
public class BaseHostJUnit4TestTest {

    /** An implementation of the base class for testing purpose. */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class TestableHostJUnit4Test extends BaseHostJUnit4Test {
        @Test
        public void testPass() {
            Assert.assertNotNull(getDevice());
            Assert.assertNotNull(getBuild());
        }

        @Override
        CollectingTestListener createListener() {
            CollectingTestListener listener = new CollectingTestListener();
            listener.testRunStarted("testRun", 1);
            TestDescription tid = new TestDescription("class", "test1");
            listener.testStarted(tid);
            listener.testEnded(tid, new HashMap<String, Metric>());
            listener.testRunEnded(500L, new HashMap<String, Metric>());
            return listener;
        }

        @Override
        ListInstrumentationParser getListInstrumentationParser() {
            ListInstrumentationParser parser = new ListInstrumentationParser();
            parser.processNewLines(
                    new String[] {
                        "instrumentation:com.package/"
                                + "android.support.test.runner.AndroidJUnitRunner "
                                + "(target=com.example2)"
                    });
            return parser;
        }
    }

    /**
     * An implementation of the base class that simulate a crashed instrumentation from an host test
     * run.
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class FailureHostJUnit4Test extends TestableHostJUnit4Test {
        @Test
        public void testOne() {
            Assert.assertNotNull(getDevice());
            Assert.assertNotNull(getBuild());
        }

        @Override
        CollectingTestListener createListener() {
            CollectingTestListener listener = new CollectingTestListener();
            listener.testRunStarted("testRun", 1);
            listener.testRunFailed("instrumentation crashed");
            listener.testRunEnded(50L, new HashMap<String, Metric>());
            return listener;
        }
    }

    private static final String CLASSNAME =
            "com.android.tradefed.testtype.junit4.BaseHostJUnit4TestTest$TestableHostJUnit4Test";

    @Mock ITestInvocationListener mMockListener;
    @Mock IBuildInfo mMockBuild;
    @Mock ITestDevice mMockDevice;
    private IInvocationContext mMockContext;
    private TestInformation mTestInfo;
    private HostTest mHostTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDevice.isAppEnumerationSupported()).thenReturn(false);
        mMockContext = new InvocationContext();
        mMockContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mMockContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockBuild);

        when(mMockDevice.checkApiLevelAgainstNextRelease(Mockito.anyInt())).thenReturn(false);

        mHostTest = new HostTest();
        mHostTest.setBuild(mMockBuild);
        mHostTest.setDevice(mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mMockContext).build();
        OptionSetter setter = new OptionSetter(mHostTest);
        // Disable pretty logging for testing
        setter.setOptionValue("enable-pretty-logs", "false");
    }

    /** Test that we are able to run the test as a JUnit4. */
    @Test
    public void testSimpleRun() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", CLASSNAME);

        TestDescription tid = new TestDescription(CLASSNAME, "testPass");

        mHostTest.run(mTestInfo, mMockListener);

        verify(mMockListener).testRunStarted(Mockito.any(), Mockito.eq(1));
        verify(mMockListener).testStarted(tid);
        verify(mMockListener).testEnded(tid, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that {@link BaseHostJUnit4Test#runDeviceTests(String, String)} properly trigger an
     * instrumentation run.
     */
    @Test
    public void testRunDeviceTests() throws Exception {
        TestableHostJUnit4Test test = new TestableHostJUnit4Test();
        test.setTestInformation(mTestInfo);

        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));
        when(mMockDevice.runInstrumentationTests(
                        (IRemoteAndroidTestRunner) Mockito.any(),
                        Mockito.<Collection<ITestLifeCycleReceiver>>any()))
                .thenReturn(true);

        try {
            test.runDeviceTests("com.package", "testClass");
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            throw new RuntimeException("Should not have thrown an Assume exception.", e);
        }

        verify(mMockDevice)
                .executeShellCommand(Mockito.eq("pm list instrumentation"), Mockito.any());
    }

    /** Test that we carry the assumption failure messages. */
    @Test
    public void testRunDeviceTests_assumptionFailure() throws Exception {
        TestableHostJUnit4Test test = new TestableHostJUnit4Test();
        test.setTestInformation(mTestInfo);

        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));
        when(mMockDevice.runInstrumentationTests(
                        (IRemoteAndroidTestRunner) Mockito.any(),
                        Mockito.<Collection<ITestLifeCycleReceiver>>any()))
                .thenAnswer(
                        new Answer<Boolean>() {
                            @SuppressWarnings("unchecked")
                            @Override
                            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                                Collection<ITestLifeCycleReceiver> receivers =
                                        (Collection<ITestLifeCycleReceiver>)
                                                invocation.getArguments()[1];
                                for (ITestLifeCycleReceiver i : receivers) {
                                    i.testRunStarted("runName", 2);
                                    i.testStarted(new TestDescription("class", "test1"));
                                    i.testAssumptionFailure(
                                            new TestDescription("class", "test1"), "assumpFail");
                                    i.testEnded(
                                            new TestDescription("class", "test1"),
                                            new HashMap<String, Metric>());

                                    i.testStarted(new TestDescription("class", "test2"));
                                    i.testAssumptionFailure(
                                            new TestDescription("class", "test2"), "assumpFail2");
                                    i.testEnded(
                                            new TestDescription("class", "test2"),
                                            new HashMap<String, Metric>());
                                }
                                return true;
                            }
                        });

        try {
            test.runDeviceTests("com.package", "testClass");
            fail("Should have thrown an Assume exception.");
        } catch (AssumptionViolatedException e) {
            assertEquals("assumpFail\n\nassumpFail2", e.getMessage());
        }

        verify(mMockDevice)
                .executeShellCommand(Mockito.eq("pm list instrumentation"), Mockito.any());
    }

    /** Test that when running an instrumentation, the abi is properly passed. */
    @Test
    public void testRunDeviceTests_abi() throws Exception {
        RemoteAndroidTestRunner runner = Mockito.mock(RemoteAndroidTestRunner.class);
        TestableHostJUnit4Test test =
                new TestableHostJUnit4Test() {
                    @Override
                    RemoteAndroidTestRunner createTestRunner(
                            String packageName, String runnerName, ITestDevice device) {
                        return runner;
                    }
                };
        test.setTestInformation(mTestInfo);
        test.setAbi(new Abi("arm", "32"));
        when(mMockDevice.runInstrumentationTests(
                        (IRemoteAndroidTestRunner) Mockito.any(),
                        Mockito.<Collection<ITestLifeCycleReceiver>>any()))
                .thenReturn(true);

        try {
            test.runDeviceTests("com.package", "testClass");
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            throw new RuntimeException("Should not have thrown an Assume exception.", e);
        }

        // Verify that the runner options were properly set.
        Mockito.verify(runner).setRunOptions("--abi arm");
    }

    /**
     * Test that {@link BaseHostJUnit4Test#runDeviceTests(String, String)} properly trigger an
     * instrumentation run as a user.
     */
    @Test
    public void testRunDeviceTests_asUser() throws Exception {
        TestableHostJUnit4Test test = new TestableHostJUnit4Test();
        test.setTestInformation(mTestInfo);

        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));
        when(mMockDevice.runInstrumentationTestsAsUser(
                        (IRemoteAndroidTestRunner) Mockito.any(),
                        Mockito.eq(0),
                        Mockito.<Collection<ITestLifeCycleReceiver>>any()))
                .thenReturn(true);

        try {
            test.runDeviceTests("com.package", "class", 0, null);
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            throw new RuntimeException("Should not have thrown an Assume exception.", e);
        }

        verify(mMockDevice)
                .executeShellCommand(Mockito.eq("pm list instrumentation"), Mockito.any());
    }

    /**
     * Test that {@link BaseHostJUnit4Test#runDeviceTests(DeviceTestRunOptions)} properly trigger an
     * instrumentation run.
     */
    @Test
    public void testRunDeviceTestsWithOptions() throws Exception {
        RemoteAndroidTestRunner mockRunner = Mockito.mock(RemoteAndroidTestRunner.class);
        TestableHostJUnit4Test test =
                new TestableHostJUnit4Test() {
                    @Override
                    RemoteAndroidTestRunner createTestRunner(
                            String packageName, String runnerName, ITestDevice device)
                            throws DeviceNotAvailableException {
                        return mockRunner;
                    }
                };
        test.setTestInformation(mTestInfo);
        when(mMockDevice.runInstrumentationTests(
                        (IRemoteAndroidTestRunner) Mockito.any(),
                        Mockito.<Collection<ITestLifeCycleReceiver>>any()))
                .thenReturn(true);

        try {
            test.runDeviceTests(
                    new DeviceTestRunOptions("com.package")
                            .setTestClassName("testClass")
                            .addInstrumentationArg("test", "value")
                            .addInstrumentationArg("test2", "value2"));
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            throw new RuntimeException("Should not have thrown an Assume exception.", e);
        }
        // Our args are translated to the runner
        Mockito.verify(mockRunner).addInstrumentationArg("test", "value");
        Mockito.verify(mockRunner).addInstrumentationArg("test2", "value2");
    }

    /**
     * Test that if the instrumentation crash directly we report it as a failure and not an
     * AssumptionFailure (which would improperly categorize the failure).
     */
    @Test
    public void testRunDeviceTests_crashedInstrumentation() throws Exception {
        FailureHostJUnit4Test test = new FailureHostJUnit4Test();
        test.setTestInformation(mTestInfo);

        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));
        when(mMockDevice.runInstrumentationTests(
                        (IRemoteAndroidTestRunner) Mockito.any(),
                        Mockito.<Collection<ITestLifeCycleReceiver>>any()))
                .thenReturn(true);

        try {
            test.runDeviceTests("com.package", "class");
        } catch (AssumptionViolatedException e) {
            // Ensure that the Assume logic in the test does not make a false pass for the unit test
            throw new RuntimeException("Should not have thrown an Assume exception.", e);
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage().contains("instrumentation crashed"));
        }

        verify(mMockDevice)
                .executeShellCommand(Mockito.eq("pm list instrumentation"), Mockito.any());
    }

    /** An implementation of the base class for testing purpose of installation of apk. */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class InstallApkHostJUnit4Test extends BaseHostJUnit4Test {
        @Test
        public void testInstall() throws Exception {
            installPackage("apkFileName");
        }

        @Override
        SuiteApkInstaller createSuiteApkInstaller() {
            return new SuiteApkInstaller() {
                @Override
                protected String parsePackageName(File testAppFile) throws TargetSetupError {
                    return "fakepackage";
                }
            };
        }
    }

    /**
     * Test that when running a test that use the {@link BaseHostJUnit4Test#installPackage(String,
     * String...)} the package is properly auto uninstalled.
     */
    @Test
    public void testInstallUninstall() throws Exception {
        File fakeTestsDir = FileUtil.createTempDir("fake-base-host-dir");
        mTestInfo.executionFiles().put(FilesKey.TESTS_DIRECTORY, fakeTestsDir);
        try {
            File apk = new File(fakeTestsDir, "apkFileName");
            apk.createNewFile();
            HostTest test = new HostTest();
            test.setBuild(mMockBuild);
            test.setDevice(mMockDevice);
            OptionSetter setter = new OptionSetter(test);
            // Disable pretty logging for testing
            setter.setOptionValue("enable-pretty-logs", "false");
            setter.setOptionValue("class", InstallApkHostJUnit4Test.class.getName());

            TestDescription description =
                    new TestDescription(InstallApkHostJUnit4Test.class.getName(), "testInstall");

            when(mMockDevice.getDeviceDescriptor()).thenReturn(null);

            when(mMockDevice.installPackage(apk, true)).thenReturn(null);
            // Ensure that the auto-uninstall is triggered
            when(mMockDevice.uninstallPackage("fakepackage")).thenReturn(null);

            test.run(mTestInfo, mMockListener);

            verify(mMockListener).testRunStarted(InstallApkHostJUnit4Test.class.getName(), 1);
            verify(mMockListener).testStarted(description);
            verify(mMockListener).testEnded(description, new HashMap<String, Metric>());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.recursiveDelete(fakeTestsDir);
        }
    }
}
