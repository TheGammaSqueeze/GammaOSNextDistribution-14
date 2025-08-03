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
package com.android.tradefed.invoker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.metric.AutoLogCollector;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.device.metric.IMetricCollectorReceiver;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.IHostCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.TestSuiteStub;
import com.android.tradefed.util.IDisableable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link InvocationExecution}. Tests for each individual interface of
 * InvocationExecution, integration tests for orders or calls should be in {@link
 * TestInvocationTest}.
 */
@RunWith(JUnit4.class)
public class InvocationExecutionTest {
    private InvocationExecution mExec;
    private IInvocationContext mContext;
    private IConfiguration mConfig;
    private ILogSaver mLogSaver;
    private ITestInvocationListener mMockListener;
    private ILogSaverListener mMockLogListener;
    private ITestLogger mMockLogger;

    @Before
    public void setUp() {
        mExec = new InvocationExecution();
        mContext = new InvocationContext();
        mConfig = new Configuration("test", "test");
        mLogSaver = mock(ILogSaver.class);
        mConfig.setLogSaver(mLogSaver);
        mMockListener = mock(ITestInvocationListener.class);
        mMockLogListener = mock(ILogSaverListener.class);
        mMockLogger = mock(ITestLogger.class);
        // Reset the counters
        TestBaseMetricCollector.sTotalInit = 0;
        RemoteTestCollector.sTotalInit = 0;
    }

    /** Test class for a target preparer class that also do host cleaner. */
    public interface ITargetHostCleaner extends ITargetPreparer, IHostCleaner {}

    /**
     * Test that {@link InvocationExecution#doCleanUp(IInvocationContext, IConfiguration,
     * Throwable)} properly use {@link IDisableable} to let an object run.
     */
    @Test
    public void testCleanUp() throws Exception {
        DeviceConfigurationHolder holder = new DeviceConfigurationHolder("default");
        ITargetHostCleaner cleaner = mock(ITargetHostCleaner.class);
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        mContext.addAllocatedDevice("default", mock(ITestDevice.class));
        when(cleaner.isDisabled()).thenReturn(false);
        when(cleaner.isTearDownDisabled()).thenReturn(false);

        mExec.doCleanUp(mContext, mConfig, null);

        verify(cleaner).cleanUp(null, null);
    }

    /**
     * Test that {@link InvocationExecution#doCleanUp(IInvocationContext, IConfiguration,
     * Throwable)} properly use {@link IDisableable} to prevent an object from running.
     */
    @Test
    public void testCleanUp_disabled() throws Exception {
        DeviceConfigurationHolder holder = new DeviceConfigurationHolder("default");
        ITargetHostCleaner cleaner = mock(ITargetHostCleaner.class);
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        mContext.addAllocatedDevice("default", mock(ITestDevice.class));
        when(cleaner.isDisabled()).thenReturn(true);
        // cleaner.isTearDownDisabled not expected, because isDisabled true stops || execution.
        // cleanUp call is not expected

        mExec.doCleanUp(mContext, mConfig, null);
    }

    /**
     * Test that {@link InvocationExecution#doCleanUp(IInvocationContext, IConfiguration,
     * Throwable)} properly use {@link IDisableable} isTearDownDisabled to prevent cleanup step.
     */
    @Test
    public void testCleanUp_tearDownDisabled() throws Exception {
        DeviceConfigurationHolder holder = new DeviceConfigurationHolder("default");
        ITargetHostCleaner cleaner = mock(ITargetHostCleaner.class);
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        mContext.addAllocatedDevice("default", mock(ITestDevice.class));
        when(cleaner.isDisabled()).thenReturn(false);
        when(cleaner.isTearDownDisabled()).thenReturn(true);
        // cleanUp call is not expected

        mExec.doCleanUp(mContext, mConfig, null);
    }

    /**
     * Test {@link IRemoteTest} that also implements {@link IMetricCollectorReceiver} to test the
     * init behavior.
     */
    private static class RemoteTestCollector implements IRemoteTest, IMetricCollectorReceiver {

        private List<IMetricCollector> mCollectors;
        private static int sTotalInit = 0;

        @Override
        public void setMetricCollectors(List<IMetricCollector> collectors) {
            mCollectors = collectors;
        }

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            for (IMetricCollector collector : mCollectors) {
                collector.init(new InvocationContext(), new ITestInvocationListener() {});
                sTotalInit++;
            }
        }
    }

    public static class TestBaseMetricCollector extends BaseDeviceMetricCollector {
        public static int sTotalInit = 0;
        private boolean mFirstInit = true;

        @Override
        public void extraInit(IInvocationContext context, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            if (mFirstInit) {
                sTotalInit++;
                mFirstInit = false;
            } else {
                fail("Init should only be called once per instance.");
            }
        }
    }

    /** Test that the run is retried the number of expected time. */
    @Test
    public void testRun_autoRetry() throws Throwable {
        TestInformation info = TestInformation.newBuilder().setInvocationContext(mContext).build();
        OptionSetter setter = new OptionSetter(mConfig.getRetryDecision());
        setter.setOptionValue("retry-strategy", "ITERATIONS");
        setter.setOptionValue("max-testcase-run-count", "3");
        setter.setOptionValue("auto-retry", "true");
        List<IRemoteTest> tests = new ArrayList<>();

        TestSuiteStub stubTest = new TestSuiteStub();
        OptionSetter testStubSetter = new OptionSetter(stubTest);
        testStubSetter.setOptionValue("report-test", "true");
        testStubSetter.setOptionValue("module", "runName");
        testStubSetter.setOptionValue("log-fake-files", "true");
        tests.add(stubTest);
        mConfig.setTests(tests);
        mConfig.getRetryDecision().setInvocationContext(mContext);
        mExec.runTests(info, mConfig, mMockLogListener);

        verify(mMockLogListener).testRunStarted(eq("runName"), eq(3), eq(0), Mockito.anyLong());
        verify(mMockLogListener).testRunStarted(eq("runName"), eq(3), eq(1), Mockito.anyLong());
        verify(mMockLogListener).testRunStarted(eq("runName"), eq(3), eq(2), Mockito.anyLong());

        verify(mMockLogListener, times(3))
                .testLog(Mockito.eq("TestStub#test1-file"), Mockito.any(), Mockito.any());
    }

    /**
     * Ensure that when logging file during auto-retry we don't multi-associate the files due to the
     * two LogSaverResultForwarder being used.
     */
    @Test
    public void testRun_autoRetry_throughForwarder() throws Throwable {
        TestInformation info = TestInformation.newBuilder().setInvocationContext(mContext).build();
        OptionSetter setter = new OptionSetter(mConfig.getRetryDecision());
        setter.setOptionValue("retry-strategy", "ITERATIONS");
        setter.setOptionValue("max-testcase-run-count", "3");
        setter.setOptionValue("auto-retry", "true");
        List<IRemoteTest> tests = new ArrayList<>();

        TestSuiteStub stubTest = new TestSuiteStub();
        OptionSetter testStubSetter = new OptionSetter(stubTest);
        testStubSetter.setOptionValue("report-test", "true");
        testStubSetter.setOptionValue("module", "runName");
        testStubSetter.setOptionValue("log-fake-files", "true");
        tests.add(stubTest);
        mConfig.setTests(tests);
        mConfig.getRetryDecision().setInvocationContext(mContext);
        LogSaverResultForwarder forwarder =
                new LogSaverResultForwarder(mConfig.getLogSaver(), Arrays.asList(mMockLogListener));
        mExec.runTests(info, mConfig, forwarder);

        verify(mMockLogListener).testRunStarted(eq("runName"), eq(3), eq(0), Mockito.anyLong());
        verify(mMockLogListener).testRunStarted(eq("runName"), eq(3), eq(1), Mockito.anyLong());
        verify(mMockLogListener).testRunStarted(eq("runName"), eq(3), eq(2), Mockito.anyLong());

        verify(mMockLogListener, times(3))
                .testLog(Mockito.eq("TestStub#test1-file"), Mockito.any(), Mockito.any());
        verify(mMockLogListener, times(3))
                .logAssociation(Mockito.eq("TestStub#test1-file"), Mockito.any());
    }

    /**
     * Test that the collectors always run with the right context no matter where they are
     * (re)initialized.
     */
    @Test
    public void testRun_metricCollectors() throws Throwable {
        TestInformation info = TestInformation.newBuilder().setInvocationContext(mContext).build();
        List<IRemoteTest> tests = new ArrayList<>();
        // First add an IMetricCollectorReceiver
        tests.add(new RemoteTestCollector());
        // Then a regular non IMetricCollectorReceiver
        tests.add(
                new IRemoteTest() {
                    @Override
                    public void run(TestInformation info, ITestInvocationListener listener)
                            throws DeviceNotAvailableException {}
                });
        mConfig.setTests(tests);
        List<IMetricCollector> collectors = new ArrayList<>();
        collectors.add(new TestBaseMetricCollector());
        mConfig.setDeviceMetricCollectors(collectors);
        mExec.runTests(info, mConfig, mMockListener);
        // Init was called twice in total on the class, but only once per instance.
        assertEquals(2, TestBaseMetricCollector.sTotalInit);
    }

    /** Test that auto collectors are properly added. */
    @Test
    public void testRun_metricCollectors_auto() throws Throwable {
        TestInformation info = TestInformation.newBuilder().setInvocationContext(mContext).build();
        List<IRemoteTest> tests = new ArrayList<>();
        // First add an IMetricCollectorReceiver
        RemoteTestCollector remoteTest = new RemoteTestCollector();
        tests.add(remoteTest);
        mConfig.setTests(tests);
        List<IMetricCollector> collectors = new ArrayList<>();
        mConfig.setDeviceMetricCollectors(collectors);

        Set<AutoLogCollector> specialCollectors = new HashSet<>();
        specialCollectors.add(AutoLogCollector.SCREENSHOT_ON_FAILURE);
        mConfig.getCommandOptions().setAutoLogCollectors(specialCollectors);

        mExec.runTests(info, mConfig, mMockListener);
        // Init was called twice in total on the class, but only once per instance.
        assertEquals(1, RemoteTestCollector.sTotalInit);
    }

    /** Test parallel pre-invocation setup. */
    @Test
    public void testRunPreInvocationSetup_parallel() throws Throwable {
        ITestDevice mockDevice1 = mock(ITestDevice.class);
        ITestDevice mockDevice2 = mock(ITestDevice.class);
        CountDownLatch latch = new CountDownLatch(2);
        Answer answer =
                new Answer() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws InterruptedException {
                        latch.countDown();
                        assertTrue(
                                "preInvocationSetup does not run in parallel.",
                                latch.await(1L, TimeUnit.SECONDS));
                        return null;
                    }
                };
        doAnswer(answer).when(mockDevice1).preInvocationSetup(any(), any());
        doAnswer(answer).when(mockDevice2).preInvocationSetup(any(), any());
        mContext.addAllocatedDevice("device1", mockDevice1);
        mContext.addAllocatedDevice("device2", mockDevice2);

        OptionSetter setter = new OptionSetter(mConfig.getCommandOptions());
        setter.setOptionValue("parallel-pre-invocation-setup", "true");
        setter.setOptionValue("parallel-pre-invocation-setup-timeout", "2s");

        mExec.runDevicePreInvocationSetup(mContext, mConfig, mMockLogger);

        verify(mockDevice1).preInvocationSetup(any(), any());
        verify(mockDevice2).preInvocationSetup(any(), any());
    }

    /** Test parallel pre-invocation setup with an exception. */
    @Test
    public void testRunPreInvocationSetup_parallelException() throws Throwable {
        ITestDevice mockDevice1 = mock(ITestDevice.class);
        ITestDevice mockDevice2 = mock(ITestDevice.class);
        doThrow(new DeviceNotAvailableException("msg", "serial"))
                .when(mockDevice2)
                .preInvocationSetup(any(), any());
        mContext.addAllocatedDevice("device1", mockDevice1);
        mContext.addAllocatedDevice("device2", mockDevice2);

        OptionSetter setter = new OptionSetter(mConfig.getCommandOptions());
        setter.setOptionValue("parallel-pre-invocation-setup", "true");
        setter.setOptionValue("parallel-pre-invocation-setup-timeout", "2s");

        assertThrows(
                DeviceNotAvailableException.class,
                () -> {
                    mExec.runDevicePreInvocationSetup(mContext, mConfig, mMockLogger);
                });
    }

    /**
     * Test the ordering of multi_pre_target_preparer/target_preparer/multi_target_preparer during
     * setup and tearDown.
     */
    @Test
    public void testDoSetup() throws Throwable {
        IMultiTargetPreparer stub1 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub2 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub3 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub4 = mock(IMultiTargetPreparer.class);
        mConfig.setMultiPreTargetPreparers(Arrays.asList(stub1, stub2));
        mConfig.setMultiTargetPreparers(Arrays.asList(stub3, stub4));

        ITargetPreparer cleaner = mock(ITargetPreparer.class);
        IDeviceConfiguration holder = new DeviceConfigurationHolder("default");
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        ITestDevice device = mock(ITestDevice.class);
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        when(device.getOptions()).thenReturn(options);
        mContext.addAllocatedDevice("default", device);
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        mExec.doSetup(testInfo, mConfig, mMockListener);
        mExec.doTeardown(testInfo, mConfig, mMockLogger, null);

        // Pre multi preparers are always called before.
        InOrder inOrder = Mockito.inOrder(stub1, stub2, stub3, stub4, cleaner);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).setUp(testInfo);
        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).setUp(testInfo);

        inOrder.verify(cleaner).setUp(Mockito.any());

        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).setUp(testInfo);
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).setUp(testInfo);

        // tear down
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).tearDown(testInfo, null);
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).tearDown(testInfo, null);

        inOrder.verify(cleaner).tearDown(Mockito.any(), Mockito.any());

        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).tearDown(testInfo, null);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).tearDown(testInfo, null);
    }

    /** Ensure that during tear down the original exception is kept. */
    @Test
    public void testDoTearDown() throws Throwable {
        IMultiTargetPreparer stub1 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub2 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub3 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub4 = mock(IMultiTargetPreparer.class);
        mConfig.setMultiPreTargetPreparers(Arrays.asList(stub1, stub2));
        mConfig.setMultiTargetPreparers(Arrays.asList(stub3, stub4));

        ITargetPreparer cleaner = mock(ITargetPreparer.class);
        IDeviceConfiguration holder = new DeviceConfigurationHolder("default");
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        ITestDevice device = mock(ITestDevice.class);
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        when(device.getOptions()).thenReturn(options);
        mContext.addAllocatedDevice("default", device);
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        mExec.doSetup(testInfo, mConfig, mMockLogger);
        // Ensure that the original error is the one passed around.
        Throwable exception = new Throwable("Original error");
        mExec.doTeardown(testInfo, mConfig, mMockLogger, exception);

        InOrder inOrder = Mockito.inOrder(stub1, stub2, stub3, stub4, cleaner);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).setUp(testInfo);
        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).setUp(testInfo);
        inOrder.verify(cleaner).isDisabled();
        inOrder.verify(cleaner).setUp(Mockito.any());
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).setUp(testInfo);
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).setUp(testInfo);
        // tear down
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).tearDown(testInfo, exception);
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).tearDown(testInfo, exception);

        inOrder.verify(cleaner).tearDown(Mockito.any(), Mockito.any());

        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).tearDown(testInfo, exception);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).tearDown(testInfo, exception);
    }

    /** Ensure that setup and teardown are called with the proper device. */
    @Test
    public void testDoTearDown_multiDevice() throws Throwable {
        boolean[] wasCalled = new boolean[2];
        wasCalled[0] = false;
        wasCalled[1] = false;
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        ITestDevice mockDevice1 = mock(ITestDevice.class);
        when(mockDevice1.getOptions()).thenReturn(options);
        ITestDevice mockDevice2 = mock(ITestDevice.class);
        when(mockDevice2.getOptions()).thenReturn(options);
        BaseTargetPreparer cleaner =
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInformation)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        wasCalled[0] = true;
                        assertEquals(mockDevice2, testInformation.getDevice());
                    }

                    @Override
                    public void tearDown(TestInformation testInformation, Throwable e)
                            throws DeviceNotAvailableException {
                        wasCalled[1] = true;
                        assertEquals(mockDevice2, testInformation.getDevice());
                    }
                };
        IDeviceConfiguration holder1 = new DeviceConfigurationHolder("device1");
        IDeviceConfiguration holder2 = new DeviceConfigurationHolder("device2");
        holder2.addSpecificConfig(cleaner);
        List<IDeviceConfiguration> deviceConfigs = new ArrayList<>();
        deviceConfigs.add(holder1);
        deviceConfigs.add(holder2);
        mConfig.setDeviceConfigList(deviceConfigs);
        mContext.addAllocatedDevice("device1", mockDevice1);
        mContext.addDeviceBuildInfo("device1", new BuildInfo());
        mContext.addAllocatedDevice("device2", mockDevice2);
        mContext.addDeviceBuildInfo("device2", new BuildInfo());
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        mExec.doSetup(testInfo, mConfig, mMockLogger);
        // Ensure that the original error is the one passed around.
        Throwable exception = new Throwable("Original error");
        mExec.doTeardown(testInfo, mConfig, mMockLogger, exception);
        assertTrue(wasCalled[0]);
        assertTrue(wasCalled[1]);
    }

    /** Interface to test a preparer receiving the logger. */
    private interface ILoggerMultiTargetPreparer
            extends IMultiTargetPreparer, ITestLoggerReceiver {}

    /** Ensure that during tear down the original exception is kept and logger is received */
    @Test
    public void testDoTearDown_logger() throws Throwable {
        ILoggerMultiTargetPreparer stub1 = mock(ILoggerMultiTargetPreparer.class);
        IMultiTargetPreparer stub2 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub3 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub4 = mock(IMultiTargetPreparer.class);
        mConfig.setMultiPreTargetPreparers(Arrays.asList(stub1, stub2));
        mConfig.setMultiTargetPreparers(Arrays.asList(stub3, stub4));

        ITargetPreparer cleaner = mock(ITargetPreparer.class);
        IDeviceConfiguration holder = new DeviceConfigurationHolder("default");
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        ITestDevice device = mock(ITestDevice.class);
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        when(device.getOptions()).thenReturn(options);
        mContext.addAllocatedDevice("default", device);
        // Ensure that the original error is the one passed around.
        Throwable exception = new Throwable("Original error");
        ITestLogger logger = new CollectingTestListener();
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        mExec.doSetup(testInfo, mConfig, logger);
        mExec.doTeardown(testInfo, mConfig, logger, exception);

        InOrder inOrder = Mockito.inOrder(stub1, stub2, stub3, stub4, cleaner);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).setUp(testInfo);
        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).setUp(testInfo);
        inOrder.verify(cleaner).isDisabled();
        inOrder.verify(cleaner).setUp(Mockito.any());
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).setUp(testInfo);
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).setUp(testInfo);
        // tear down
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).tearDown(testInfo, exception);
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).tearDown(testInfo, exception);

        inOrder.verify(cleaner).tearDown(Mockito.any(), Mockito.any());

        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).tearDown(testInfo, exception);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).setTestLogger(logger);
        inOrder.verify(stub1).tearDown(testInfo, exception);
    }

    /** Ensure that the full tear down is attempted before throwning an exception. */
    @Test
    public void testDoTearDown_exception() throws Throwable {
        IMultiTargetPreparer stub1 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub2 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub3 = mock(IMultiTargetPreparer.class);
        IMultiTargetPreparer stub4 = mock(IMultiTargetPreparer.class);
        mConfig.setMultiPreTargetPreparers(Arrays.asList(stub1, stub2));
        mConfig.setMultiTargetPreparers(Arrays.asList(stub3, stub4));

        ITargetPreparer cleaner = mock(ITargetPreparer.class);
        IDeviceConfiguration holder = new DeviceConfigurationHolder("default");
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        ITestDevice device = mock(ITestDevice.class);
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        when(device.getOptions()).thenReturn(options);
        mContext.addAllocatedDevice("default", device);
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        // Ensure that the original error is the one passed around.
        Throwable exception = new Throwable("Original error");
        doThrow(new RuntimeException("Oups I failed")).when(stub3).tearDown(testInfo, exception);

        try {
            mExec.doSetup(testInfo, mConfig, mMockLogger);
            mExec.doTeardown(testInfo, mConfig, null, exception);
            fail("Should have thrown an exception");
        } catch (RuntimeException expected) {
            // Expected
        }
        // Ensure that even in case of exception, the full tear down goes through before throwing.
        InOrder inOrder = Mockito.inOrder(stub1, stub2, stub3, stub4, cleaner);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).setUp(testInfo);
        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).setUp(testInfo);
        inOrder.verify(cleaner).isDisabled();
        inOrder.verify(cleaner).setUp(Mockito.any());
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).setUp(testInfo);
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).setUp(testInfo);
        // tear down
        inOrder.verify(stub4).isDisabled();
        inOrder.verify(stub4).tearDown(testInfo, exception);
        inOrder.verify(stub3).isDisabled();
        inOrder.verify(stub3).tearDown(testInfo, exception);

        inOrder.verify(cleaner).tearDown(Mockito.any(), Mockito.any());

        inOrder.verify(stub2).isDisabled();
        inOrder.verify(stub2).tearDown(testInfo, exception);
        inOrder.verify(stub1).isDisabled();
        inOrder.verify(stub1).tearDown(testInfo, exception);
    }
}
