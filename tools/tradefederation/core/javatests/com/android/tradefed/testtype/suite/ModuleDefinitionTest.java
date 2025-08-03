/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype.suite;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.TestInvocation;
import com.android.tradefed.invoker.shard.token.TokenProperty;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.MultiFailureDescription;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.retry.BaseRetryDecision;
import com.android.tradefed.retry.IRetryDecision;
import com.android.tradefed.retry.RetryPreparationDecision;
import com.android.tradefed.retry.RetryStrategy;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.suite.module.BaseModuleController;
import com.android.tradefed.testtype.suite.module.IModuleController;
import com.android.tradefed.testtype.suite.module.TestFailureModuleController;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link ModuleDefinition} */
@RunWith(JUnit4.class)
public class ModuleDefinitionTest {

    private static final String MODULE_NAME = "fakeName";
    private static final String DEFAULT_DEVICE_NAME = "DEFAULT_DEVICE";
    private ModuleDefinition mModule;
    private TestInformation mModuleInfo;
    private List<IRemoteTest> mTestList;
    @Mock ITestInterface mMockTest;
    @Mock ITargetPreparer mMockPrep;
    private List<ITargetPreparer> mTargetPrepList;
    private Map<String, List<ITargetPreparer>> mMapDeviceTargetPreparer;
    private List<IMultiTargetPreparer> mMultiTargetPrepList;
    @Mock ITestInvocationListener mMockListener;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock ITestDevice mMockDevice;
    // Extra mock for log saving testing
    @Mock ILogSaver mMockLogSaver;
    @Mock ILogSaverListener mMockLogSaverListener;
    @Mock IRetryDecision mMockDecision;
    private IRetryDecision mDecision = new BaseRetryDecision();

    private interface ITestInterface
            extends IRemoteTest, IBuildReceiver, IDeviceTest, IConfigurationReceiver {}

    /** Test implementation that allows us to exercise different use cases * */
    private class TestObject implements ITestInterface {

        private ITestDevice mDevice;
        private String mRunName;
        private int mNumTest;
        private boolean mShouldThrow;
        private boolean mDeviceUnresponsive = false;
        private boolean mThrowError = false;
        private IConfiguration mConfig;

        public TestObject(String runName, int numTest, boolean shouldThrow) {
            mRunName = runName;
            mNumTest = numTest;
            mShouldThrow = shouldThrow;
        }

        public TestObject(
                String runName, int numTest, boolean shouldThrow, boolean deviceUnresponsive) {
            this(runName, numTest, shouldThrow);
            mDeviceUnresponsive = deviceUnresponsive;
        }

        public TestObject(
                String runName,
                int numTest,
                boolean shouldThrow,
                boolean deviceUnresponsive,
                boolean throwError) {
            this(runName, numTest, shouldThrow, deviceUnresponsive);
            mThrowError = throwError;
        }

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            Assert.assertNotNull(mConfig);
            listener.testRunStarted(mRunName, mNumTest);
            for (int i = 0; i < mNumTest; i++) {
                TestDescription test = new TestDescription(mRunName + "class", "test" + i);
                listener.testStarted(test);
                if (mShouldThrow && i == mNumTest / 2) {
                    throw new DeviceNotAvailableException(
                            "unavailable", "serial", DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
                }
                if (mDeviceUnresponsive) {
                    throw new DeviceUnresponsiveException(
                            "unresponsive", "serial", DeviceErrorIdentifier.DEVICE_UNRESPONSIVE);
                }
                if (mThrowError && i == mNumTest / 2) {
                    throw new AssertionError("assert error");
                }
                listener.testEnded(test, new HashMap<String, Metric>());
            }
            listener.testRunEnded(0, new HashMap<String, Metric>());
        }

        @Override
        public void setBuild(IBuildInfo buildInfo) {
            // ignore
        }

        @Override
        public void setDevice(ITestDevice device) {
            mDevice = device;
        }

        @Override
        public ITestDevice getDevice() {
            return mDevice;
        }

        @Override
        public void setConfiguration(IConfiguration configuration) {
            mConfig = configuration;
        }
    }

    /** Test implementation that allows us to exercise different use cases * */
    private class MultiRunTestObject implements IRemoteTest, ITestFilterReceiver {

        private String mBaseRunName;
        private int mNumTest;
        private int mRepeatedRun;
        private int mFailedTest;
        private Set<String> mIncludeFilters;
        private Set<String> mExcludeFilters;

        public MultiRunTestObject(
                String baseRunName, int numTest, int repeatedRun, int failedTest) {
            mBaseRunName = baseRunName;
            mNumTest = numTest;
            mRepeatedRun = repeatedRun;
            mFailedTest = failedTest;
            mIncludeFilters = new LinkedHashSet<>();
            mExcludeFilters = new LinkedHashSet<>();
        }

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            // The runner generates several set of different runs.
            for (int j = 0; j < mRepeatedRun; j++) {
                String runName = mBaseRunName + j;
                if (mIncludeFilters.isEmpty() && mExcludeFilters.isEmpty()) {
                    listener.testRunStarted(runName, mNumTest);
                } else {
                    int size = (mNumTest * mRepeatedRun) - mExcludeFilters.size();
                    listener.testRunStarted(runName, size / mRepeatedRun);
                }
                for (int i = 0; i < mNumTest - mFailedTest; i++) {
                    // TODO: Store the list of expected test cases to verify against it.
                    TestDescription test = new TestDescription(runName + "class", "test" + i);
                    if (mExcludeFilters.contains(test.toString())) {
                        continue;
                    }
                    if (!mIncludeFilters.isEmpty() && !mIncludeFilters.contains(test.toString())) {
                        continue;
                    }
                    listener.testStarted(test);
                    listener.testEnded(test, new HashMap<String, Metric>());
                }
                for (int i = 0; i < mFailedTest; i++) {
                    TestDescription test = new TestDescription(runName + "class", "fail" + i);
                    if (mExcludeFilters.contains(test.toString())) {
                        continue;
                    }
                    if (!mIncludeFilters.isEmpty() && !mIncludeFilters.contains(test.toString())) {
                        continue;
                    }
                    listener.testStarted(test);
                    listener.testFailed(
                            test,
                            FailureDescription.create("I failed.", FailureStatus.TEST_FAILURE));
                    listener.testEnded(test, new HashMap<String, Metric>());
                }
                listener.testRunEnded(0, new HashMap<String, Metric>());
            }
        }

        @Override
        public void addIncludeFilter(String filter) {
            mIncludeFilters.add(filter);
        }

        @Override
        public void addAllIncludeFilters(Set<String> filters) {
            mIncludeFilters.addAll(filters);
        }

        @Override
        public void addExcludeFilter(String filter) {
            mExcludeFilters.add(filter);
        }

        @Override
        public void addAllExcludeFilters(Set<String> filters) {
            mExcludeFilters.addAll(filters);
        }

        @Override
        public Set<String> getIncludeFilters() {
            return mIncludeFilters;
        }

        @Override
        public Set<String> getExcludeFilters() {
            return null;
        }

        @Override
        public void clearIncludeFilters() {
            mIncludeFilters.clear();
        }

        @Override
        public void clearExcludeFilters() {}
    }

    private class DirectFailureTestObject implements IRemoteTest {
        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            throw new RuntimeException("early failure!");
        }
    }

    @BeforeClass
    public static void SetUpClass() throws ConfigurationException {
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty"});
        } catch (IllegalStateException e) {
            // Expected outside IDE.
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTestList = new ArrayList<>();
        mTestList.add(mMockTest);

        mTargetPrepList = new ArrayList<>();
        mTargetPrepList.add(mMockPrep);

        mMapDeviceTargetPreparer = new LinkedHashMap<>();
        mMapDeviceTargetPreparer.put(DEFAULT_DEVICE_NAME, mTargetPrepList);

        mMultiTargetPrepList = new ArrayList<>();

        when(mMockDevice.getDeviceDate()).thenReturn(0L);
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.disableAutoRetryReportingTime();
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
    }

    @Test
    public void testCreateModule() {
        IConfiguration config = new Configuration("", "");
        ConfigurationDescriptor descriptor = config.getConfigurationDescription();
        descriptor.setAbi(new Abi("armeabi-v7a", "32"));
        descriptor.addMetadata(ITestSuite.PARAMETER_KEY, Arrays.asList("instant_app", "multi_abi"));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        assertNotNull(mModule.getModuleInvocationContext());
        IInvocationContext moduleContext = mModule.getModuleInvocationContext();
        assertNull(moduleContext.getAttributes().get(ModuleDefinition.MODULE_PARAMETERIZATION));
    }

    @Test
    public void testCreateModule_withParams() {
        IConfiguration config = new Configuration("", "");
        ConfigurationDescriptor descriptor = config.getConfigurationDescription();
        descriptor.setAbi(new Abi("armeabi-v7a", "32"));
        descriptor.addMetadata(
                ConfigurationDescriptor.ACTIVE_PARAMETER_KEY, Arrays.asList("instant"));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        assertNotNull(mModule.getModuleInvocationContext());
        IInvocationContext moduleContext = mModule.getModuleInvocationContext();
        assertEquals(
                1,
                moduleContext.getAttributes().get(ModuleDefinition.MODULE_PARAMETERIZATION).size());
        assertEquals(
                "instant",
                moduleContext
                        .getAttributes()
                        .getUniqueMap()
                        .get(ModuleDefinition.MODULE_PARAMETERIZATION));
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} is properly
     * going through the execution flow.
     */
    @Test
    public void testRun() throws Exception {
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockTest, times(2)).setConfiguration(Mockito.any());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockTest).setBuild(Mockito.eq(mMockBuildInfo));
        verify(mMockTest).setDevice(Mockito.eq(mMockDevice));
        verify(mMockTest).run(Mockito.eq(mModuleInfo), Mockito.any());
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testDynamicDownloadThrows_ReportsRunFailed() throws Exception {
        String expectedMessage = "Ooops!";
        ModuleDefinition module =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", "") {
                            @Override
                            public void resolveDynamicOptions(DynamicRemoteFileResolver resolver) {
                                throw new RuntimeException(expectedMessage);
                            }
                        });
        module.setEnableDynamicDownload(true);
        module.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        module.setRetryDecision(mDecision);
        module.run(mModuleInfo, mMockListener);

        ArgumentCaptor<FailureDescription> failureDescription =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(failureDescription.capture());
        assertThat(failureDescription.getValue().getErrorMessage()).contains(expectedMessage);
    }

    /**
     * If an exception is thrown during tear down, report it for the module if there was no other
     * errors.
     */
    @Test
    public void testRun_tearDownException() throws Exception {
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);
        // Exception thrown during tear down do not bubble up to invocation.
        RuntimeException exception = new RuntimeException("teardown failed");
        doThrow(exception).when(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockTest, times(2)).setConfiguration(Mockito.any());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockTest).setBuild(Mockito.eq(mMockBuildInfo));
        verify(mMockTest).setDevice(Mockito.eq(mMockDevice));
        verify(mMockTest).run(Mockito.eq(mModuleInfo), Mockito.any());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertTrue(captured.getValue().getErrorMessage().contains("teardown failed"));
    }

    /**
     * In case of multiple run failures happening, ensure we have some way to get them all
     * eventually.
     */
    @Test
    public void testRun_aggregateRunFailures() throws Exception {
        final int testCount = 4;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false, true));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.disableAutoRetryReportingTime();
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);

        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);
        // Exception thrown during tear down do not bubble up to invocation.
        RuntimeException exception = new RuntimeException("teardown failed");
        doThrow(exception).when(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());

        // There was a module failure so a bugreport should be captured.
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mMockDevice.logBugreport(
                        Mockito.eq("module-fakeName-failure-SERIAL-bugreport"), Mockito.any()))
                .thenReturn(true);

        CollectingTestListener errorChecker = new CollectingTestListener();
        // DeviceUnresponsive should not throw since it indicates that the device was recovered.
        mModule.run(mModuleInfo, new ResultForwarder(mMockListener, errorChecker));
        // Only one module
        assertEquals(1, mModule.getTestsResults().size());
        assertEquals(0, mModule.getTestsResults().get(0).getNumCompleteTests());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener).testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testFailed(Mockito.any(), (FailureDescription) Mockito.any());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        // Check that the error aggregates
        List<TestRunResult> res = errorChecker.getTestRunAttempts(MODULE_NAME);
        assertEquals(1, res.size());
        assertTrue(res.get(0).isRunFailure());
        assertTrue(
                res.get(0)
                        .getRunFailureDescription()
                        .getErrorMessage()
                        .contains(
                                "There were 2 failures:\n  unresponsive\n  "
                                        + "java.lang.RuntimeException: teardown failed"));
        assertTrue(captured.getValue() instanceof MultiFailureDescription);
    }

    /** Test that Module definition properly parse tokens out of the configuration description. */
    @Test
    public void testParseTokens() throws Exception {
        Configuration config = new Configuration("", "");
        ConfigurationDescriptor descriptor = config.getConfigurationDescription();
        descriptor.addMetadata(ITestSuite.TOKEN_KEY, Arrays.asList("SIM_CARD"));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);

        assertEquals(1, mModule.getRequiredTokens(mModuleInfo).size());
        assertEquals(
                TokenProperty.SIM_CARD, mModule.getRequiredTokens(mModuleInfo).iterator().next());
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} is properly
     * going through the execution flow and skip target preparers if disabled.
     */
    @Test
    public void testRun_disabledPreparation() throws Exception {
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        // No setup and teardown expected from preparers.
        when(mMockPrep.isDisabled()).thenReturn(true);

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockTest, times(2)).setConfiguration(Mockito.any());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockTest).setBuild(Mockito.eq(mMockBuildInfo));
        verify(mMockTest).setDevice(Mockito.eq(mMockDevice));
        verify(mMockTest).run(Mockito.eq(mModuleInfo), Mockito.any());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} is properly
     * going through the execution flow and skip target cleanup if teardown is disabled.
     */
    @Test
    public void testRun_disabledTearDown() throws Exception {
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        // Setup expected from preparers.
        when(mMockPrep.isDisabled()).thenReturn(false);
        when(mMockPrep.isTearDownDisabled()).thenReturn(true);

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockTest, times(2)).setConfiguration(Mockito.any());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockTest).setBuild(Mockito.eq(mMockBuildInfo));
        verify(mMockTest).setDevice(Mockito.eq(mMockDevice));
        verify(mMockTest).run(Mockito.eq(mModuleInfo), Mockito.any());
        // But no teardown expected from Cleaner.
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} properly
     * propagate an early preparation failure.
     */
    @Test
    public void testRun_failPreparation() throws Exception {
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        DeviceDescriptor nullDescriptor = null;
                        throw new TargetSetupError(exceptionMessage, nullDescriptor);
                    }
                });
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setRetryDecision(mDecision);
        mModule.run(mModuleInfo, mMockListener);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertThat(captured.getValue().getErrorMessage()).contains(exceptionMessage);
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} properly
     * propagate an early preparation failure, even for a runtime exception.
     */
    @Test
    public void testRun_failPreparation_runtime() throws Exception {
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        throw new RuntimeException(exceptionMessage);
                    }
                });
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setRetryDecision(mDecision);
        mModule.run(mModuleInfo, mMockListener);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertTrue(captured.getValue().getErrorMessage().contains(exceptionMessage));
    }

    @Test
    public void testRun_failPreparation_error() throws Exception {
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        // Throw AssertionError
                        Assert.assertNull(exceptionMessage);
                    }
                });
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setRetryDecision(mDecision);
        mModule.run(mModuleInfo, mMockListener);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertTrue(captured.getValue().getErrorMessage().contains(exceptionMessage));
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} properly
     * pass the results of early failures to both main listener and module listeners.
     */
    @Test
    public void testRun_failPreparation_moduleListener() throws Exception {
        ITestInvocationListener mockModuleListener = mock(ITestInvocationListener.class);
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        DeviceDescriptor nullDescriptor = null;
                        throw new TargetSetupError(exceptionMessage, nullDescriptor);
                    }
                });
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setRetryDecision(mDecision);
        mModule.run(mModuleInfo, mMockListener, Arrays.asList(mockModuleListener), null);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured1 =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured1.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        // Ensure that module listeners receive the callbacks too.
        verify(mockModuleListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured2 =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mockModuleListener).testRunFailed(captured2.capture());
        verify(mockModuleListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertTrue(captured1.getValue().getErrorMessage().contains(exceptionMessage));
        assertTrue(captured2.getValue().getErrorMessage().contains(exceptionMessage));
    }

    /** Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} */
    @Test
    public void testRun_failPreparation_unresponsive() throws Exception {
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        ITargetPreparer preparer =
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        throw new DeviceUnresponsiveException(exceptionMessage, "serial");
                    }
                };
        preparer.setDisableTearDown(true);
        mTargetPrepList.add(preparer);
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();

        try {
            mModule.run(mModuleInfo, mMockListener);
            fail("Should have thrown an exception.");
        } catch (DeviceUnresponsiveException expected) {
            // The exception is still bubbled up.
            assertEquals(exceptionMessage, expected.getMessage());
        }

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertTrue(captured.getValue().getErrorMessage().contains(exceptionMessage));
    }

    @Test
    public void testRun_failPreparation_PassAfterModuleRetry() throws Exception {
        final int testCount = 5;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false));
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        DeviceDescriptor nullDescriptor = null;
                        throw new TargetSetupError(exceptionMessage, nullDescriptor);
                    }
                });
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();

        // Indicate that the module retry succeed.
        doReturn(new RetryPreparationDecision(false, false))
                .when(mMockDecision)
                .shouldRetryPreparation(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());
        when(mMockDecision.getRetryStrategy()).thenReturn(RetryStrategy.ITERATIONS);
        mModule.setRetryDecision(mMockDecision);
        mModule.run(mModuleInfo, mMockListener);
        // At the first time, expect that it would report a set up failure.
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        // At the second time, the device is recovered, so expect that it run test level tests.
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(1),
                        Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener, times(2))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        assertTrue(captured.getValue().getErrorMessage().contains(exceptionMessage));
    }

    @Test
    public void testRun_failPreparation_FailAfterModuleRetry() throws Exception {
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(
                new BaseTargetPreparer() {
                    @Override
                    public void setUp(TestInformation testInfo)
                            throws TargetSetupError, BuildError, DeviceNotAvailableException {
                        DeviceDescriptor nullDescriptor = null;
                        throw new TargetSetupError(exceptionMessage, nullDescriptor);
                    }
                });
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        mTestList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        TargetSetupError error =
                new TargetSetupError(exceptionMessage, InfraErrorIdentifier.UNDETERMINED);
        RetryPreparationDecision decision = new RetryPreparationDecision(true, false);
        decision.setPreviousException(error);
        RetryPreparationDecision decision2 = new RetryPreparationDecision(false, true);
        doReturn(decision, decision, decision, decision2)
                .when(mMockDecision)
                .shouldRetryPreparation(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());
        when(mMockDecision.getRetryStrategy()).thenReturn(RetryStrategy.ITERATIONS);
        mModule.setRetryDecision(mMockDecision);
        mModule.run(mModuleInfo, mMockListener, null, null, 3);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(2), Mockito.anyLong());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(1), Mockito.eq(3), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener, times(4)).testRunFailed(captured.capture());
        verify(mMockListener, times(4))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        assertTrue(captured.getValue().getErrorMessage().contains(exceptionMessage));
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} is properly
     * going through the execution flow with actual test callbacks.
     */
    @Test
    public void testRun_fullPass() throws Exception {
        final int testCount = 5;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener, times(testCount))
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(testCount))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} is properly
     * going through the execution flow with actual test callbacks.
     */
    @Test
    public void testRun_partialRun() throws Exception {
        final int testCount = 4;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, true));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);
        // Recovery is disabled during tearDown
        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);
        when(mMockDevice.getSerialNumber()).thenReturn("serial");

        try {
            mModule.run(mModuleInfo, mMockListener);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected
        }
        // Only one module
        assertEquals(1, mModule.getTestsResults().size());
        assertEquals(2, mModule.getTestsResults().get(0).getNumCompleteTests());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep)
                .tearDown(Mockito.eq(mModuleInfo), Mockito.isA(DeviceNotAvailableException.class));
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener, times(3))
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testFailed(Mockito.any(), (FailureDescription) Mockito.any());
        verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.NONE);
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
    }

    @Test
    public void testRun_partialRun_error() throws Exception {
        final int testCount = 4;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false, false, true));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        mModule.run(mModuleInfo, mMockListener);
        // Only one module
        assertEquals(1, mModule.getTestsResults().size());
        assertEquals(2, mModule.getTestsResults().get(0).getNumCompleteTests());
        assertTrue(
                mModule.getTestsResults().get(0).getRunFailureMessage().contains("assert error"));
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener, times(3))
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testFailed(Mockito.any(), (FailureDescription) Mockito.any());
        verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that when a module is created with some particular informations, the resulting {@link
     * IInvocationContext} of the module is properly populated.
     */
    @Test
    public void testAbiSetting() throws Exception {
        final int testCount = 5;
        IConfiguration config = new Configuration("", "");
        ConfigurationDescriptor descriptor = new ConfigurationDescriptor();
        descriptor.setAbi(new Abi("arm", "32"));
        descriptor.setModuleName(MODULE_NAME);
        config.setConfigurationObject(
                Configuration.CONFIGURATION_DESCRIPTION_TYPE_NAME, descriptor);
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false));
        mModule =
                new ModuleDefinition(
                        "arm32 " + MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        // Check that the invocation module created has expected informations
        IInvocationContext moduleContext = mModule.getModuleInvocationContext();
        assertEquals(
                MODULE_NAME,
                moduleContext.getAttributes().get(ModuleDefinition.MODULE_NAME).get(0));
        assertEquals("arm", moduleContext.getAttributes().get(ModuleDefinition.MODULE_ABI).get(0));
        assertEquals(
                "arm32 " + MODULE_NAME,
                moduleContext.getAttributes().get(ModuleDefinition.MODULE_ID).get(0));
    }

    /**
     * Test running a module when the configuration has a module controller object that force a full
     * bypass of the module.
     */
    @Test
    public void testModuleController_fullBypass() throws Exception {
        IConfiguration config = new Configuration("", "");
        BaseModuleController moduleConfig =
                new BaseModuleController() {
                    @Override
                    public RunStrategy shouldRun(IInvocationContext context) {
                        return RunStrategy.FULL_MODULE_BYPASS;
                    }
                };
        List<BaseModuleController> listController = new ArrayList<>();
        listController.add(moduleConfig);
        listController.add(moduleConfig);
        config.setConfigurationObjectList(ModuleDefinition.MODULE_CONTROLLER, listController);
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(
                new IRemoteTest() {
                    @Override
                    public void run(TestInformation testInfo, ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        listener.testRunStarted("test", 1);
                        listener.testFailed(
                                new TestDescription("failedclass", "failedmethod"),
                                FailureDescription.create("trace", FailureStatus.TEST_FAILURE));
                    }
                });
        mTargetPrepList.clear();
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        // module is completely skipped, no tests is recorded.
        mModule.run(mModuleInfo, mMockListener, null, null);
    }

    /**
     * Test running a module when the configuration has a module controller object that force to
     * skip all the module test cases.
     */
    @Test
    public void testModuleController_skipTestCases() throws Exception {
        IConfiguration config = new Configuration("", "");
        BaseModuleController moduleConfig =
                new BaseModuleController() {
                    @Override
                    public RunStrategy shouldRun(IInvocationContext context) {
                        return RunStrategy.SKIP_MODULE_TESTCASES;
                    }
                };
        config.setConfigurationObject(ModuleDefinition.MODULE_CONTROLLER, moduleConfig);
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(
                new IRemoteTest() {
                    @Override
                    public void run(TestInformation testInfo, ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        TestDescription tid = new TestDescription("class", "method");
                        listener.testRunStarted("test", 1);
                        listener.testStarted(tid);
                        listener.testFailed(
                                tid,
                                FailureDescription.create("I failed", FailureStatus.TEST_FAILURE));
                        listener.testEnded(tid, new HashMap<String, Metric>());
                        listener.testRunEnded(0, new HashMap<String, Metric>());
                    }
                });
        mTargetPrepList.clear();
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();

        // expect the module to run but tests to be ignored
        mModule.run(mModuleInfo, mMockListener, null, null);
        verify(mMockListener)
                .testRunStarted(Mockito.any(), Mockito.anyInt(), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mMockListener).testIgnored(Mockito.any());
        verify(mMockListener)
                .testEnded(
                        Mockito.any(), Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test {@link IRemoteTest} that log a file during its run. */
    public class TestLogClass implements ITestInterface {

        @Override
        public void run(TestInformation testInfo, ITestInvocationListener listener)
                throws DeviceNotAvailableException {
            listener.testLog(
                    "testlogclass",
                    LogDataType.TEXT,
                    new ByteArrayInputStreamSource("".getBytes()));
        }

        @Override
        public void setBuild(IBuildInfo buildInfo) {}

        @Override
        public void setDevice(ITestDevice device) {}

        @Override
        public ITestDevice getDevice() {
            return null;
        }

        @Override
        public void setConfiguration(IConfiguration configuration) {}
    }

    /**
     * Test that the invocation level result_reporter receive the testLogSaved information from the
     * modules.
     *
     * <p>The {@link LogSaverResultForwarder} from the module is expected to log the file and ensure
     * that it passes the information to the {@link LogSaverResultForwarder} from the {@link
     * TestInvocation} in order for final result_reporter to know about logged files.
     */
    @Test
    public void testModule_LogSaverResultForwarder() throws Exception {
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestLogClass());
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.setLogSaver(mMockLogSaver);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);
        // The final reporter still receive the testLog signal
        LogFile loggedFile = new LogFile("path", "url", LogDataType.TEXT);
        when(mMockLogSaver.saveLogData(
                        Mockito.eq("testlogclass"), Mockito.eq(LogDataType.TEXT), Mockito.any()))
                .thenReturn(loggedFile);

        // Simulate how the invoker actually put the log saver
        LogSaverResultForwarder forwarder =
                new LogSaverResultForwarder(mMockLogSaver, Arrays.asList(mMockLogSaverListener));
        mModule.run(mModuleInfo, forwarder);
        InOrder inOrder = Mockito.inOrder(mMockLogSaverListener);
        inOrder.verify(mMockLogSaverListener).setLogSaver(mMockLogSaver);
        inOrder.verify(mMockLogSaverListener)
                .testLog(Mockito.eq("testlogclass"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        inOrder.verify(mMockLogSaverListener)
                .testLogSaved(
                        Mockito.eq("testlogclass"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any(),
                        Mockito.eq(loggedFile));
        inOrder.verify(mMockLogSaverListener).logAssociation("testlogclass", loggedFile);
        inOrder.verify(mMockLogSaverListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockLogSaverListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockLogSaverListener).setLogSaver(mMockLogSaver);
        verify(mMockLogSaverListener)
                .testLog(Mockito.eq("testlogclass"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        // mMockLogSaverListener should receive the testLogSaved call even from the module
        verify(mMockLogSaverListener)
                .testLogSaved(
                        Mockito.eq("testlogclass"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any(),
                        Mockito.eq(loggedFile));
        verify(mMockLogSaverListener).logAssociation("testlogclass", loggedFile);
        verify(mMockLogSaverListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mMockLogSaverListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that the {@link IModuleController} object can override the behavior of the capture of
     * the failure.
     */
    @Test
    public void testOverrideModuleConfig() throws Exception {
        // failure listener with capture logcat on failure and screenshot on failure.
        List<ITestDevice> listDevice = new ArrayList<>();
        listDevice.add(mMockDevice);
        when(mMockDevice.getSerialNumber()).thenReturn("Serial");
        TestFailureListener failureListener = new TestFailureListener(listDevice, true, false);
        failureListener.setLogger(mMockListener);
        IConfiguration config = new Configuration("", "");
        TestFailureModuleController moduleConfig = new TestFailureModuleController();
        OptionSetter setter = new OptionSetter(moduleConfig);
        // Module option should override the logcat on failure
        setter.setOptionValue("bugreportz-on-failure", "false");
        config.setConfigurationObject(ModuleDefinition.MODULE_CONTROLLER, moduleConfig);
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(
                new IRemoteTest() {
                    @Override
                    public void run(TestInformation testInfo, ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        listener.testFailed(
                                new TestDescription("failedclass", "failedmethod"),
                                FailureDescription.create("trace", FailureStatus.TEST_FAILURE));
                    }
                });
        mTargetPrepList.clear();
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        config);
        mModule.setRetryDecision(mDecision);
        mModule.setLogSaver(mMockLogSaver);

        mModule.run(mModuleInfo, mMockListener, null, failureListener);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("fakeName"), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test when the test yields a DeviceUnresponsive exception. */
    @Test
    public void testRun_partialRun_deviceUnresponsive() throws Exception {
        final int testCount = 4;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false, true));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        // There was a module failure so a bugreport should be captured.
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mMockDevice.logBugreport(
                        Mockito.eq("module-fakeName-failure-SERIAL-bugreport"), Mockito.any()))
                .thenReturn(true);

        // DeviceUnresponsive should not throw since it indicates that the device was recovered.
        mModule.run(mModuleInfo, mMockListener);
        // Only one module
        assertEquals(1, mModule.getTestsResults().size());
        assertEquals(0, mModule.getTestsResults().get(0).getNumCompleteTests());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener).testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        ArgumentCaptor<FailureDescription> captureRunFailure =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testFailed(Mockito.any(), (FailureDescription) Mockito.any());
        verify(mMockListener).testRunFailed(captureRunFailure.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        FailureDescription failure = captureRunFailure.getValue();
        assertTrue(failure.getErrorMessage().equals("unresponsive"));
        assertEquals(FailureStatus.LOST_SYSTEM_UNDER_TEST, failure.getFailureStatus());
    }

    /**
     * Test that when a module level listener is specified it receives the events before the
     * buffering and replay.
     */
    @Test
    public void testRun_moduleLevelListeners() throws Exception {
        mMockListener = mock(ITestInvocationListener.class);
        final int testCount = 5;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.setLogSaver(mMockLogSaver);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        // Simulate how the invoker actually put the log saver

        LogSaverResultForwarder forwarder =
                new LogSaverResultForwarder(mMockLogSaver, Arrays.asList(mMockLogSaverListener));
        mModule.run(mModuleInfo, forwarder, Arrays.asList(mMockListener), null);
        InOrder inOrder = Mockito.inOrder(mMockLogSaverListener, mMockListener);
        inOrder.verify(mMockLogSaverListener).setLogSaver(mMockLogSaver);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        inOrder.verify(mMockListener)
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        inOrder.verify(mMockListener)
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockLogSaverListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        inOrder.verify(mMockLogSaverListener)
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        inOrder.verify(mMockLogSaverListener)
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockLogSaverListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockLogSaverListener).setLogSaver(mMockLogSaver);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener, times(testCount))
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockListener, times(testCount))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockLogSaverListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME),
                        Mockito.eq(testCount),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockLogSaverListener, times(testCount))
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mMockLogSaverListener, times(testCount))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockLogSaverListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that {@link ModuleDefinition#run(TestInformation, ITestInvocationListener)} is properly
     * going through the execution flow and reports properly when the runner generates multiple
     * runs.
     */
    @Test
    public void testMultiRun() throws Exception {
        final String runName = "baseRun";
        List<IRemoteTest> testList = new ArrayList<>();
        // The runner will generates 2 test runs with 2 test cases each.
        testList.add(new MultiRunTestObject(runName, 2, 2, 0));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        // We expect a total count on the run start so 4, all aggregated under the same run
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(MODULE_NAME), Mockito.eq(4), Mockito.eq(0), Mockito.anyLong());
        // The first set of test cases from the first test run.
        for (int i = 0; i < 2; i++) {
            TestDescription testId = new TestDescription(runName + "0class", "test" + i);
            verify(mMockListener).testStarted(Mockito.eq(testId), Mockito.anyLong());
            verify(mMockListener)
                    .testEnded(
                            Mockito.eq(testId),
                            Mockito.anyLong(),
                            Mockito.<HashMap<String, Metric>>any());
        }
        // The second set of test cases from the second test run
        for (int i = 0; i < 2; i++) {
            TestDescription testId = new TestDescription(runName + "1class", "test" + i);
            verify(mMockListener).testStarted(Mockito.eq(testId), Mockito.anyLong());
            verify(mMockListener)
                    .testEnded(
                            Mockito.eq(testId),
                            Mockito.anyLong(),
                            Mockito.<HashMap<String, Metric>>any());
        }
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testRun_earlyFailure() throws Exception {
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new DirectFailureTestObject());
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.setRetryDecision(mDecision);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);

        when(mMockPrep.isDisabled()).thenReturn(false);
        // no isTearDownDisabled() expected for setup
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        mModule.run(mModuleInfo, mMockListener);
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("fakeName"), Mockito.eq(0), Mockito.eq(0), Mockito.anyLong());
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        assertTrue(captured.getValue().getErrorMessage().contains("early failure!"));
    }

    /** Test retry and reporting all the different attempts. */
    @Test
    public void testMultiRun_multiAttempts() throws Exception {
        final String runName = "baseRun";
        List<IRemoteTest> testList = new ArrayList<>();
        // The runner will generates 2 test runs with 3 test cases each.
        testList.add(new MultiRunTestObject(runName, 3, 2, 1));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.disableAutoRetryReportingTime();
        IRetryDecision decision = new BaseRetryDecision();
        OptionSetter setter = new OptionSetter(decision);
        setter.setOptionValue("retry-strategy", "ITERATIONS");
        setter.setOptionValue("max-testcase-run-count", Integer.toString(3));
        decision.setInvocationContext(mModule.getModuleInvocationContext());
        mModule.setRetryDecision(decision);
        mModule.setMergeAttemps(false);
        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);

        mModule.run(mModuleInfo, mMockListener, null, null, 3);
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        // We expect a total count on the run start so 4, all aggregated under the same run
        for (int attempt = 0; attempt < 3; attempt++) {
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(MODULE_NAME),
                            Mockito.eq(6),
                            Mockito.eq(attempt),
                            Mockito.anyLong());
        }
        // The first set of test cases from the first test run.
        TestDescription testId0 = new TestDescription(runName + "0class", "test0");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testId0), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testId0),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testFail0 = new TestDescription(runName + "0class", "fail0");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testFail0), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testFailed(Mockito.eq(testFail0), (FailureDescription) Mockito.any());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testFail0),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testId1 = new TestDescription(runName + "0class", "test1");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testId1), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testId1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        // The second set of test cases from the second test run
        TestDescription testId0_1 = new TestDescription(runName + "1class", "test0");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testId0_1), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testId0_1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testFail0_1 = new TestDescription(runName + "1class", "fail0");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testFail0_1), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testFailed(Mockito.eq(testFail0_1), (FailureDescription) Mockito.any());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testFail0_1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testId1_1 = new TestDescription(runName + "1class", "test1");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testId1_1), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testId1_1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener, times(3))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test retry and reporting all the different attempts when retrying failures. */
    @Test
    public void testMultiRun_multiAttempts_filter() throws Exception {
        final String runName = "baseRun";
        List<IRemoteTest> testList = new ArrayList<>();
        // The runner will generates 2 test runs with 3 test cases each. (2 passes and 1 fail)
        testList.add(new MultiRunTestObject(runName, 3, 2, 1));
        mModule =
                new ModuleDefinition(
                        MODULE_NAME,
                        testList,
                        mMapDeviceTargetPreparer,
                        mMultiTargetPrepList,
                        new Configuration("", ""));
        mModule.disableAutoRetryReportingTime();
        IRetryDecision decision = new BaseRetryDecision();
        OptionSetter setter = new OptionSetter(decision);
        setter.setOptionValue("retry-strategy", "RETRY_ANY_FAILURE");
        setter.setOptionValue("max-testcase-run-count", Integer.toString(3));
        decision.setInvocationContext(mModule.getModuleInvocationContext());
        mModule.setRetryDecision(decision);
        mModule.setMergeAttemps(false);

        mModule.getModuleInvocationContext().addAllocatedDevice(DEFAULT_DEVICE_NAME, mMockDevice);
        mModule.getModuleInvocationContext()
                .addDeviceBuildInfo(DEFAULT_DEVICE_NAME, mMockBuildInfo);
        mModuleInfo =
                TestInformation.newBuilder()
                        .setInvocationContext(mModule.getModuleInvocationContext())
                        .build();
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        when(mMockPrep.isDisabled()).thenReturn(false);
        when(mMockPrep.isTearDownDisabled()).thenReturn(false);
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));

        mModule.run(mModuleInfo, mMockListener, null, null, 3);
        verify(mMockPrep, times(2)).isDisabled();
        verify(mMockDevice, times(3)).getIDevice();
        verify(mMockPrep).setUp(Mockito.eq(mModuleInfo));
        verify(mMockPrep).tearDown(Mockito.eq(mModuleInfo), Mockito.isNull());
        // We expect a total count on the run start so 4, all aggregated under the same run
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt == 0) {
                verify(mMockListener)
                        .testRunStarted(
                                Mockito.eq(MODULE_NAME),
                                Mockito.eq(6),
                                Mockito.eq(attempt),
                                Mockito.anyLong());
            } else {
                verify(mMockListener)
                        .testRunStarted(
                                Mockito.eq(MODULE_NAME),
                                Mockito.eq(2),
                                Mockito.eq(attempt),
                                Mockito.anyLong());
            }
        }
        // The first set of test cases from the first test run.
        TestDescription testId0 = new TestDescription(runName + "0class", "test0");
        verify(mMockListener).testStarted(Mockito.eq(testId0), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testId0),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testFail0 = new TestDescription(runName + "0class", "fail0");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testFail0), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testFailed(Mockito.eq(testFail0), (FailureDescription) Mockito.any());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testFail0),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testId1 = new TestDescription(runName + "0class", "test1");
        verify(mMockListener).testStarted(Mockito.eq(testId1), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testId1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        // The second set of test cases from the second test run
        TestDescription testId0_1 = new TestDescription(runName + "1class", "test0");
        verify(mMockListener).testStarted(Mockito.eq(testId0_1), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testId0_1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testFail0_1 = new TestDescription(runName + "1class", "fail0");
        verify(mMockListener, times(3)).testStarted(Mockito.eq(testFail0_1), Mockito.anyLong());
        verify(mMockListener, times(3))
                .testFailed(Mockito.eq(testFail0_1), (FailureDescription) Mockito.any());
        verify(mMockListener, times(3))
                .testEnded(
                        Mockito.eq(testFail0_1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        TestDescription testId1_1 = new TestDescription(runName + "1class", "test1");
        verify(mMockListener).testStarted(Mockito.eq(testId1_1), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testId1_1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener, times(3))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }
}
