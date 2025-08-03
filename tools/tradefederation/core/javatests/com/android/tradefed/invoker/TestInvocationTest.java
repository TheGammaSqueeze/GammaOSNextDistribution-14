/*
 * Copyright (C) 2010 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildInfo.BuildInfoProperties;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildProvider;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.command.CommandRunner.ExitCode;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.device.metric.BaseDeviceMetricCollector;
import com.android.tradefed.device.metric.DeviceMetricData;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.shard.IShardHelper;
import com.android.tradefed.invoker.shard.ShardHelper;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.ILogRegistry;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.postprocessor.BasePostProcessor;
import com.android.tradefed.result.ActionInProgress;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.util.keystore.IKeyStoreClient;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link TestInvocation}. */
@SuppressWarnings("MustBeClosedChecker")
@RunWith(JUnit4.class)
public class TestInvocationTest {

    private static final String SERIAL = "serial";
    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();
    private static final String PATH = "path";
    private static final String URL = "url";
    private static final TestSummary mSummary = new TestSummary("http://www.url.com/report.txt");
    private static final String LOGCAT_NAME_ERROR =
            TestInvocation.getDeviceLogName(TestInvocation.Stage.ERROR);
    private static final String LOGCAT_NAME_SETUP =
            TestInvocation.getDeviceLogName(TestInvocation.Stage.SETUP);
    private static final String LOGCAT_NAME_TEST =
            TestInvocation.getDeviceLogName(TestInvocation.Stage.TEST);
    private static final String LOGCAT_NAME_TEARDOWN =
            TestInvocation.getDeviceLogName(TestInvocation.Stage.TEARDOWN);
    private static final String CONFIG_LOG_NAME = TestInvocation.TRADEFED_CONFIG_NAME;
    /** The {@link TestInvocation} under test, with all dependencies mocked out */
    private TestInvocation mTestInvocation;

    private FailureStatus mExceptedStatus = null;
    private boolean mShardingEarlyFailure = false;

    private IConfiguration mStubConfiguration;
    private IConfiguration mStubMultiConfiguration;
    private IGlobalConfiguration mGlobalConfiguration;

    private IInvocationContext mStubInvocationMetadata;

    // Log streams, they need a new instance per test
    private InputStreamSource mLogcatSetupSource =
            new ByteArrayInputStreamSource("logcat_setup".getBytes());
    private InputStreamSource mLogcatTestSource =
            new ByteArrayInputStreamSource("logcat_test".getBytes());
    private InputStreamSource mLogcatTeardownSource =
            new ByteArrayInputStreamSource("logcat_test".getBytes());
    private InputStreamSource mHostLogSource =
            new ByteArrayInputStreamSource("host_log".getBytes());

    // The mock objects.
    @Mock ITestDevice mMockDevice;
    @Mock ITargetPreparer mMockPreparer;
    @Mock IBuildProvider mMockBuildProvider;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock ITestInvocationListener mMockTestListener;
    @Mock ITestSummaryListener mMockSummaryListener;
    @Mock ILeveledLogOutput mMockLogger;
    @Mock ILogSaver mMockLogSaver;
    @Mock IDeviceRecovery mMockRecovery;
    @Captor ArgumentCaptor<List<TestSummary>> mUriCapture;
    @Mock ILogRegistry mMockLogRegistry;
    @Mock IConfigurationFactory mMockConfigFactory;
    @Mock IRescheduler mockRescheduler;
    @Mock DeviceDescriptor mFakeDescriptor;

    // inOrder verifiers
    private InOrder mInOrderSummaryListener;
    private InOrder mInOrderTestListener;
    private InOrder mInOrderLogSaver;

    private InOrder mTestOrder;

    @BeforeClass
    public static void setUpClass() throws Exception {
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty"});
        } catch (IllegalStateException e) {
            // Avoid exception in case of multi-init
        }
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mInOrderSummaryListener = inOrder(mMockSummaryListener);
        mInOrderTestListener = inOrder(mMockTestListener);
        mInOrderLogSaver = inOrder(mMockLogSaver);

        mStubConfiguration =
                new Configuration("foo", "bar") {
                    @Override
                    public IConfiguration partialDeepClone(
                            List<String> objectToDeepClone, IKeyStoreClient client)
                            throws ConfigurationException {
                        return new Configuration(this.getName(), this.getDescription());
                    }
                };
        OptionSetter setter = new OptionSetter(mStubConfiguration.getCommandOptions());
        setter.setOptionValue("report-passed-tests", "false");
        mStubMultiConfiguration = new Configuration("foo", "bar");

        mStubConfiguration.setDeviceRecovery(mMockRecovery);
        mStubConfiguration.setTargetPreparer(mMockPreparer);
        mStubConfiguration.setBuildProvider(mMockBuildProvider);

        lenient().when(mMockPreparer.isDisabled()).thenReturn(false);
        lenient().when(mMockPreparer.isTearDownDisabled()).thenReturn(false);

        List<IDeviceConfiguration> deviceConfigs = new ArrayList<IDeviceConfiguration>();
        IDeviceConfiguration device1 =
                new DeviceConfigurationHolder(ConfigurationDef.DEFAULT_DEVICE_NAME);
        device1.addSpecificConfig(mMockRecovery);
        device1.addSpecificConfig(mMockPreparer);
        device1.addSpecificConfig(mMockBuildProvider);
        deviceConfigs.add(device1);
        mStubMultiConfiguration.setDeviceConfigList(deviceConfigs);

        mStubConfiguration.setLogSaver(mMockLogSaver);
        mStubMultiConfiguration.setLogSaver(mMockLogSaver);

        List<ITestInvocationListener> listenerList = new ArrayList<ITestInvocationListener>(1);
        listenerList.add(mMockTestListener);
        listenerList.add(mMockSummaryListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);
        mStubMultiConfiguration.setTestInvocationListeners(listenerList);

        mStubConfiguration.setLogOutput(mMockLogger);
        mStubMultiConfiguration.setLogOutput(mMockLogger);
        lenient().when(mMockDevice.getSerialNumber()).thenReturn(SERIAL);
        lenient().when(mMockDevice.getIDevice()).thenReturn(null);
        lenient().when(mMockDevice.getBattery()).thenReturn(null);
        lenient().when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
        mFakeDescriptor =
                new DeviceDescriptor(
                        SERIAL,
                        false,
                        DeviceAllocationState.Available,
                        "unknown",
                        "unknown",
                        "unknown",
                        "unknown",
                        "unknown");
        lenient().when(mMockDevice.getDeviceDescriptor()).thenReturn(mFakeDescriptor);

        lenient().when(mMockBuildInfo.getBuildId()).thenReturn("1");
        lenient().when(mMockBuildInfo.getBuildAttributes()).thenReturn(EMPTY_MAP);
        lenient().when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");
        lenient().when(mMockBuildInfo.getBuildFlavor()).thenReturn("flavor");
        lenient().when(mMockBuildInfo.getProperties()).thenReturn(new HashSet<>());

        mStubInvocationMetadata = new InvocationContext();
        mStubInvocationMetadata.addAllocatedDevice(
                ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mStubInvocationMetadata.addDeviceBuildInfo(
                ConfigurationDef.DEFAULT_DEVICE_NAME, mMockBuildInfo);
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        when(mMockDevice.getOptions()).thenReturn(options);

        // create the BaseTestInvocation to test
        mTestInvocation =
                new TestInvocation() {
                    @Override
                    ILogRegistry getLogRegistry() {
                        return mMockLogRegistry;
                    }

                    @Override
                    public IInvocationExecution createInvocationExec(RunMode mode) {
                        return new InvocationExecution() {
                            @Override
                            protected IShardHelper createShardHelper() {
                                return new ShardHelper() {
                                    @Override
                                    protected IGlobalConfiguration getGlobalConfiguration() {
                                        return mGlobalConfiguration;
                                    }
                                };
                            }

                            @Override
                            protected String getAdbVersion() {
                                return null;
                            }

                            @Override
                            protected void collectAutoInfo(
                                    IConfiguration config, TestInformation info)
                                    throws DeviceNotAvailableException {
                                // Inop for the command test case.
                            }

                            @Override
                            protected void logHostAdb(IConfiguration config, ITestLogger logger) {
                                // inop for the common test case.
                            }
                        };
                    }

                    @Override
                    protected void setExitCode(ExitCode code, Throwable stack) {
                        // Empty on purpose
                    }

                    @Override
                    public void registerExecutionFiles(ExecutionFiles executionFiles) {
                        // Empty on purpose
                    }

                    @Override
                    protected void applyAutomatedReporters(IConfiguration config) {
                        // Empty on purpose
                    }

                    @Override
                    protected void addInvocationMetric(InvocationMetricKey key, long value) {}

                    @Override
                    protected void addInvocationMetric(InvocationMetricKey key, String value) {}
                };
    }

    /** Helper to set the expectation for N number of shards. */
    private void stubNShardInvocation(int numShard, String commandLine) throws Exception {
        /*
        when(mMockBuildProvider.getBuild()).thenReturn(mMockBuildInfo);
        Mockito.lenient().when(mMockBuildInfo.getTestTag()).thenReturn("");
        when(mMockBuildInfo.clone()).thenReturn(mMockBuildInfo);
        when(mMockBuildInfo.getVersionedFileKeys()).thenReturn(new HashSet<>());
        when(mockRescheduler.scheduleConfig(Mockito.any())).thenReturn(true);
        */
    }

    /** Helper to set the expectation for N number of shards. */
    private void verifyNShardInvocation(int numShard, String commandLine) throws Exception {
        /*
        verify(mMockBuildInfo).setTestTag(Mockito.eq("stub"));
        verify(mMockBuildProvider.getBuild());
        verify(mMockBuildInfo)
                .addBuildAttribute(Mockito.eq("command_line_args"), Mockito.eq(commandLine));

        mInOrderTestListener.verify(mMockTestListener).invocationStarted(Mockito.any());
        mInOrderSummaryListener.verify(mMockSummaryListener).invocationStarted(Mockito.any());

        verify(mMockBuildInfo, times(numShard)).clone();
        verify(mMockBuildInfo, times(numShard)).getVersionedFileKeys();
        verify(mockRescheduler, times(numShard)).scheduleConfig(Mockito.any());
        verify(mMockBuildInfo).setDeviceSerial(Mockito.eq(SERIAL));
        verify(mMockBuildProvider).cleanUp(Mockito.any());
        */
    }

    private void stubMockSuccessListeners() throws IOException {
        stubMockListeners(InvocationStatus.SUCCESS, null, false, true, false);
    }

    private void verifyMockSuccessListeners() throws IOException {
        verifyMockListeners(InvocationStatus.SUCCESS, null, false, true, false, false);
    }

    private void stubMockFailureListeners(Throwable throwable) throws IOException {
        stubMockListeners(InvocationStatus.FAILED, throwable, false, true, false);
    }

    private void verifyMockFailureListeners(Throwable throwable) throws IOException {
        verifyMockListeners(InvocationStatus.FAILED, throwable, false, true, false, false);
    }

    private void stubMockFailureListenersAny(Throwable throwable, boolean stubFailures)
            throws IOException {
        stubMockListeners(InvocationStatus.FAILED, throwable, stubFailures, true, false);
    }

    private void verifyMockFailureListenersAny(Throwable throwable, boolean stubFailures)
            throws IOException {
        verifyMockListeners(InvocationStatus.FAILED, throwable, stubFailures, true, false, false);
    }

    private void stubMockFailureListeners(
            Throwable throwable, boolean stubFailures, boolean reportHostLog) throws IOException {
        stubMockListeners(InvocationStatus.FAILED, throwable, stubFailures, reportHostLog, false);
    }

    private void verifyMockFailureListeners(
            Throwable throwable, boolean stubFailures, boolean reportHostLog) throws IOException {
        verifyMockListeners(
                InvocationStatus.FAILED, throwable, stubFailures, reportHostLog, false, false);
    }

    private void stubMockStoppedListeners() throws IOException {
        stubMockListeners(InvocationStatus.SUCCESS, null, false, true, true);
    }

    private void verifyMockStoppedListeners(boolean testSkipped) throws IOException {
        verifyMockListeners(InvocationStatus.SUCCESS, null, false, true, true, testSkipped);
    }

    private void verifySummaryListener() {
        // Check that we captured the expected uris List
        List<TestSummary> summaries = mUriCapture.getValue();
        assertEquals(1, summaries.size());
        assertEquals(mSummary, summaries.get(0));
    }

    /**
     * Set up expected conditions for normal run up to the part where tests are run.
     *
     * @param test the {@link Test} to use.
     */
    private void stubNormalInvoke(IRemoteTest test) throws Throwable {
        stubInvokeWithBuild();
        mStubConfiguration.setTest(test);
        mStubMultiConfiguration.setTest(test);
        when(mMockBuildProvider.getBuild()).thenReturn(mMockBuildInfo);
    }

    /**
     * Set up expected conditions for normal run up to the part where tests are run.
     *
     * @param test the {@link Test} to use.
     */
    private void verifyNormalInvoke(IRemoteTest test) throws Throwable {
        try {
            verify(mMockLogger).getLog();
            verify(mMockLogger, times(3)).init();
            verify(mMockLogger, times(2)).closeLog();

            verify(mMockBuildProvider, atLeast(1)).cleanUp(Mockito.eq(mMockBuildInfo));
            verify(mMockBuildProvider).getBuild();

            verify(mMockBuildInfo).setDeviceSerial(Mockito.eq(SERIAL));
            verify(mMockBuildInfo).setTestTag(Mockito.eq("stub"));

            // always expect logger initialization and cleanup calls
            verify(mMockLogRegistry, times(2)).unregisterLogger();
            verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);

            verify(mMockPreparer).setUp(Mockito.any());

        } catch (IOException | TargetSetupError | DeviceNotAvailableException e) {
            // Never happens since these are all verify calls
        }
    }

    /** Set up expected calls that occur on every invoke, regardless of result */
    private void verifyInvoke() throws DeviceNotAvailableException {
        // TODO(murj) Probably needs the in-order treatment
        try {
            // always expect logger initialization and cleanup calls
            verify(mMockLogRegistry, times(2)).registerLogger(mMockLogger);
            verify(mMockLogRegistry, times(2)).unregisterLogger();

            verify(mMockLogger, times(2)).init();
            verify(mMockLogger, times(2)).closeLog();
        } catch (IOException e) {
            // Never happens since these are all verify calls
        }
    }

    /** Set up expected calls that occur on every invoke that gets a valid build */
    private void stubInvokeWithBuild() throws DeviceNotAvailableException {
        when(mMockDevice.getLogcat())
                .thenReturn(mLogcatSetupSource)
                .thenReturn(mLogcatTestSource)
                .thenReturn(mLogcatTeardownSource);
        when(mMockDevice.waitForDeviceAvailable(TestInvocation.AVAILABILITY_CHECK_TIMEOUT))
                .thenReturn(true);

        when(mMockLogger.getLog()).thenReturn(mHostLogSource);
        Mockito.lenient().when(mMockBuildInfo.getTestTag()).thenReturn("");
    }

    /** Set up expected calls that occur on every invoke that gets a valid build */
    private void verifyInvokeWithBuild() {
        try {
            verify(mMockLogger).getLog();
            verify(mMockLogger, times(3)).init();
            verify(mMockLogger, times(2)).closeLog();

            verify(mMockBuildInfo).setDeviceSerial(Mockito.eq(SERIAL));
            verify(mMockBuildInfo).setTestTag(Mockito.eq("stub"));

            verify(mMockBuildProvider, atLeast(1)).cleanUp(Mockito.eq(mMockBuildInfo));

            // always expect logger initialization and cleanup calls
            verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
            verify(mMockLogRegistry, times(2)).unregisterLogger();

        } catch (IOException e) {
            // Never happens since these are all verify calls
        }
    }

    /**
     * Set up expected conditions for the test InvocationListener and SummaryListener
     *
     * <p>The order of calls for a single listener should be:
     *
     * <ol>
     *   <li>invocationStarted
     *   <li>testLog(LOGCAT_NAME_SETUP, ...) (if no build or retrieval error)
     *   <li>invocationFailed (if run failed)
     *   <li>testLog(LOGCAT_NAME_ERROR, ...) (if build retrieval error)
     *   <li>testLog(LOGCAT_NAME_TEST, ...) (otherwise)
     *   <li>testLog(build error bugreport, ...) (otherwise and if build error)
     *   <li>testLog(LOGCAT_NAME_TEARDOWN, ...) (otherwise)
     *   <li>testLog(TRADEFED_LOG_NAME, ...)
     *   <li>putSummary (for an ITestSummaryListener)
     *   <li>invocationEnded
     *   <li>getSummary (for an ITestInvocationListener)
     * </ol>
     *
     * However note that, across all listeners, any getSummary call will precede all putSummary
     * calls.
     */
    private void stubMockListeners(
            InvocationStatus status,
            Throwable throwable,
            boolean stubFailures,
            boolean reportHostLog,
            boolean stopped)
            throws IOException {
        // Very important, the second and beyond calls to mMockTestListener.getSummary() will
        // actually
        // return the summary which was originally stubbed at the "end" of this method.
        doReturn(null).doReturn(mSummary).when(mMockTestListener).getSummary();
        doReturn(null).when(mMockSummaryListener).getSummary();

        doReturn(new LogFile(PATH, URL, LogDataType.TEXT))
                .when(mMockLogSaver)
                .saveLogData(
                        Mockito.contains(CONFIG_LOG_NAME),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        (InputStream) Mockito.any());

        if (!(throwable instanceof BuildRetrievalError) && !mShardingEarlyFailure) {
            doReturn(new LogFile(PATH, URL, LogDataType.TEXT))
                    .when(mMockLogSaver)
                    .saveLogData(
                            Mockito.startsWith(LOGCAT_NAME_SETUP),
                            Mockito.eq(LogDataType.LOGCAT),
                            (InputStream) Mockito.any());
        }

        if (throwable instanceof BuildRetrievalError) {
            // Handle logcat error listeners
            doReturn(new LogFile(PATH, URL, LogDataType.TEXT))
                    .when(mMockLogSaver)
                    .saveLogData(
                            Mockito.startsWith(LOGCAT_NAME_ERROR),
                            Mockito.eq(LogDataType.LOGCAT),
                            (InputStream) Mockito.any());
        } else {
            // Handle build error bugreport listeners
            if (throwable instanceof BuildError) {
                doReturn(true)
                        .when(mMockDevice)
                        .logBugreport(
                                Mockito.eq(
                                        TestInvocation.BUILD_ERROR_BUGREPORT_NAME + "_" + SERIAL),
                                Mockito.any());
            } else if (!(throwable instanceof TargetSetupError) && !mShardingEarlyFailure) {
                // Handle test logcat listeners
                doReturn(new LogFile(PATH, URL, LogDataType.TEXT))
                        .when(mMockLogSaver)
                        .saveLogData(
                                Mockito.startsWith(LOGCAT_NAME_TEST),
                                Mockito.eq(LogDataType.LOGCAT),
                                (InputStream) Mockito.any());
            }
            // Handle teardown logcat listeners
            if (!mShardingEarlyFailure) {
                doReturn(new LogFile(PATH, URL, LogDataType.TEXT))
                        .when(mMockLogSaver)
                        .saveLogData(
                                Mockito.startsWith(LOGCAT_NAME_TEARDOWN),
                                Mockito.eq(LogDataType.LOGCAT),
                                (InputStream) Mockito.any());
            }
        }

        doReturn(new LogFile(PATH, URL, LogDataType.HOST_LOG))
                .when(mMockLogSaver)
                .saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.eq(LogDataType.HOST_LOG),
                        (InputStream) Mockito.any());

        if (reportHostLog) {
            doReturn(new LogFile(PATH, URL, LogDataType.HOST_LOG))
                    .when(mMockLogSaver)
                    .saveLogData(
                            Mockito.eq(TestInvocation.TRADEFED_LOG_NAME),
                            Mockito.eq(LogDataType.HOST_LOG),
                            (InputStream) Mockito.any());
        }
    }

    private void verifyMockListeners(
            InvocationStatus status,
            Throwable throwable,
            boolean stubFailures,
            boolean reportHostLog,
            boolean stopped,
            boolean testSkipped)
            throws IOException {
        // invocationStarted
        mInOrderTestListener
                .verify(mMockTestListener)
                .invocationStarted(Mockito.eq(mStubInvocationMetadata));
        mInOrderTestListener.verify(mMockTestListener).getSummary();
        mInOrderSummaryListener.verify(mMockSummaryListener).putEarlySummary(Mockito.any());
        mInOrderSummaryListener
                .verify(mMockSummaryListener)
                .invocationStarted(mStubInvocationMetadata);
        mInOrderSummaryListener.verify(mMockSummaryListener).getSummary();
        mInOrderLogSaver
                .verify(mMockLogSaver)
                .invocationStarted(Mockito.eq(mStubInvocationMetadata));

        mInOrderTestListener
                .verify(mMockTestListener)
                .testLog(
                        Mockito.contains(CONFIG_LOG_NAME),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        (InputStreamSource) Mockito.any());
        mInOrderSummaryListener
                .verify(mMockSummaryListener)
                .testLog(
                        Mockito.contains(CONFIG_LOG_NAME),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        (InputStreamSource) Mockito.any());

        if (throwable == null) {
            verify(mMockDevice, atLeast(0)).postInvocationTearDown(null);
        } else {
            verify(mMockDevice, atLeast(0)).postInvocationTearDown(throwable);
        }

        if (!(throwable instanceof BuildRetrievalError) && !mShardingEarlyFailure) {
            mInOrderTestListener
                    .verify(mMockTestListener)
                    .testLog(
                            Mockito.startsWith(LOGCAT_NAME_SETUP),
                            Mockito.eq(LogDataType.LOGCAT),
                            (InputStreamSource) Mockito.any());

            mInOrderSummaryListener
                    .verify(mMockSummaryListener)
                    .testLog(
                            Mockito.startsWith(LOGCAT_NAME_SETUP),
                            Mockito.eq(LogDataType.LOGCAT),
                            (InputStreamSource) Mockito.any());
        }

        // invocationFailed
        if (!status.equals(InvocationStatus.SUCCESS)) {
            if (stubFailures) {
                mInOrderTestListener
                        .verify(mMockTestListener)
                        .invocationFailed((FailureDescription) Mockito.any());

                mInOrderSummaryListener
                        .verify(mMockSummaryListener)
                        .invocationFailed((FailureDescription) Mockito.any());

            } else {
                FailureStatus failureStatus = FailureStatus.INFRA_FAILURE;
                if (throwable instanceof BuildError) {
                    failureStatus = FailureStatus.DEPENDENCY_ISSUE;
                }
                FailureDescription failure =
                        FailureDescription.create(throwable.getMessage(), failureStatus)
                                .setCause(throwable);
                if (throwable instanceof BuildRetrievalError) {
                    failure.setActionInProgress(ActionInProgress.FETCHING_ARTIFACTS);
                } else if (throwable instanceof BuildError
                        || throwable instanceof TargetSetupError) {
                    failure.setActionInProgress(ActionInProgress.SETUP);
                } else {
                    failure.setActionInProgress(ActionInProgress.TEST);
                }
                if (mExceptedStatus != null) {
                    failure.setFailureStatus(mExceptedStatus);
                }

                mInOrderTestListener
                        .verify(mMockTestListener)
                        .invocationFailed(Mockito.eq(failure));
                mInOrderSummaryListener
                        .verify(mMockSummaryListener)
                        .invocationFailed(Mockito.eq(failure));
            }
        }

        if (throwable instanceof BuildRetrievalError) {
            // Handle logcat error listeners
            mInOrderTestListener
                    .verify(mMockTestListener)
                    .testLog(
                            Mockito.startsWith(LOGCAT_NAME_ERROR),
                            Mockito.eq(LogDataType.LOGCAT),
                            (InputStreamSource) Mockito.any());
            mInOrderSummaryListener
                    .verify(mMockSummaryListener)
                    .testLog(
                            Mockito.startsWith(LOGCAT_NAME_ERROR),
                            Mockito.eq(LogDataType.LOGCAT),
                            (InputStreamSource) Mockito.any());
        } else {
            // Handle build error bugreport listeners
            if (throwable instanceof BuildError) {
            } else if (!(throwable instanceof TargetSetupError)
                    && !mShardingEarlyFailure
                    && !testSkipped) {
                // Handle test logcat listeners
                mInOrderTestListener
                        .verify(mMockTestListener)
                        .testLog(
                                Mockito.startsWith(LOGCAT_NAME_TEST),
                                Mockito.eq(LogDataType.LOGCAT),
                                (InputStreamSource) Mockito.any());
                mInOrderSummaryListener
                        .verify(mMockSummaryListener)
                        .testLog(
                                Mockito.startsWith(LOGCAT_NAME_TEST),
                                Mockito.eq(LogDataType.LOGCAT),
                                (InputStreamSource) Mockito.any());
            }
            // Handle teardown logcat listeners
            if (!mShardingEarlyFailure) {
                mInOrderTestListener
                        .verify(mMockTestListener)
                        .testLog(
                                Mockito.startsWith(LOGCAT_NAME_TEARDOWN),
                                Mockito.eq(LogDataType.LOGCAT),
                                (InputStreamSource) Mockito.any());
                mInOrderSummaryListener
                        .verify(mMockSummaryListener)
                        .testLog(
                                Mockito.startsWith(LOGCAT_NAME_TEARDOWN),
                                Mockito.eq(LogDataType.LOGCAT),
                                (InputStreamSource) Mockito.any());
            }

            if (stopped) {
                mInOrderTestListener
                        .verify(mMockTestListener)
                        .invocationFailed((FailureDescription) Mockito.any());
                mInOrderSummaryListener
                        .verify(mMockSummaryListener)
                        .invocationFailed((FailureDescription) Mockito.any());
            }
        }

        if (reportHostLog) {
            mInOrderTestListener
                    .verify(mMockTestListener)
                    .testLog(
                            Mockito.eq(TestInvocation.TRADEFED_LOG_NAME),
                            Mockito.eq(LogDataType.HOST_LOG),
                            (InputStreamSource) Mockito.any());
            mInOrderSummaryListener
                    .verify(mMockSummaryListener)
                    .testLog(
                            Mockito.eq(TestInvocation.TRADEFED_LOG_NAME),
                            Mockito.eq(LogDataType.HOST_LOG),
                            (InputStreamSource) Mockito.any());
        }

        // invocationEnded, getSummary (mMockTestListener)
        mInOrderTestListener.verify(mMockTestListener).invocationEnded(Mockito.anyLong());

        // putSummary, invocationEnded (mMockSummaryListener)
        mInOrderSummaryListener.verify(mMockSummaryListener).putSummary(mUriCapture.capture());
        mInOrderSummaryListener.verify(mMockSummaryListener).invocationEnded(Mockito.anyLong());

        mInOrderLogSaver.verify(mMockLogSaver).invocationEnded(Mockito.anyLong());
    }

    /** Helper tests class to expose all the interfaces needed for the tests. */
    private interface IFakeBuildProvider extends IDeviceBuildProvider, IInvocationContextReceiver {}

    @SuppressWarnings("deprecation")
    private TargetSetupError createTargetSetupError(String reason) {
        // Use the deprecated constructor on purpose to simulate missing DeviceDescriptor.
        return new TargetSetupError(reason);
    }

    /** Interface for testing device config pass through. */
    private interface DeviceConfigTest extends IRemoteTest, IDeviceTest {}

    public static class TestableCollector extends BaseDeviceMetricCollector {

        @Option(name = "name")
        private String mName;

        public TestableCollector() {}

        public TestableCollector(String name) {
            mName = name;
        }

        @Override
        public void onTestRunEnd(
                DeviceMetricData runData, final Map<String, Metric> currentRunMetrics) {
            runData.addMetric(
                    mName,
                    Metric.newBuilder()
                            .setMeasurements(
                                    Measurements.newBuilder().setSingleString(mName).build()));
        }
    }

    public static class TestableProcessor extends BasePostProcessor {

        @Option(name = "name")
        private String mName;

        public TestableProcessor() {}

        public TestableProcessor(String name) {
            mName = name;
        }

        @Override
        public Map<String, Metric.Builder> processRunMetricsAndLogs(
                HashMap<String, Metric> rawMetrics, Map<String, LogFile> runLogs) {
            Map<String, Metric.Builder> post = new LinkedHashMap<>();
            post.put(mName, Metric.newBuilder());
            return post;
        }
    }

    private class TestLogSaverListener implements ILogSaverListener {

        public boolean mWasLoggerSet = false;

        @Override
        public void setLogSaver(ILogSaver logSaver) {
            mWasLoggerSet = true;
        }
    }

    private void stubEarlyDeviceReleaseExpectation() {
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockDevice.waitForDeviceShell(30000L)).thenReturn(true);
    }

    private void verifyEarlyDeviceReleaseExpectation() {
        verify(mMockDevice, atLeast(1)).getDeviceState();
        verify(mMockDevice).waitForDeviceShell(30000L);
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
    }

    /**
     * Test the normal case invoke scenario with a {@link IRemoteTest}.
     *
     * <p>Verifies that all external interfaces get notified as expected.
     */
    @Test
    public void testInvoke_RemoteTest() throws Throwable {
        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        stubEarlyDeviceReleaseExpectation();
        stubMockSuccessListeners();
        stubNormalInvoke(test);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verify(test).run(Mockito.any(), Mockito.any());

        verifyNormalInvoke(test);
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.isNull());

        verifyMockSuccessListeners();
        verifyEarlyDeviceReleaseExpectation();

        verifySummaryListener();
    }

    /**
     * Test the normal case for multi invoke scenario with a {@link IRemoteTest}.
     *
     * <p>Verifies that all external interfaces get notified as expected.
     */
    @Test
    public void testInvokeMulti_RemoteTest() throws Throwable {
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockSuccessListeners();
        stubNormalInvoke(test);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubMultiConfiguration, mockRescheduler);

        verify(test).run(Mockito.any(), Mockito.any());

        verifyNormalInvoke(test);
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.isNull());

        verifyMockSuccessListeners();

        verifySummaryListener();
    }

    /**
     * Test the normal case invoke scenario with an {@link ITestSummaryListener} masquerading as an
     * {@link ITestInvocationListener}.
     *
     * <p>Verifies that all external interfaces get notified as expected.
     */
    @Test
    public void testInvoke_twoSummary() throws Throwable {
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockSuccessListeners();
        stubNormalInvoke(test);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verify(test).run(Mockito.any(), Mockito.any());

        verifyNormalInvoke(test);
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.isNull());

        verifyMockSuccessListeners();

        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where build retrieve fails.
     *
     * <p>An invocation will be started in this scenario.
     */
    @Test
    public void testInvoke_buildFailed() throws Throwable {
        BuildRetrievalError exception =
                new BuildRetrievalError("testInvoke_buildFailed", null, mMockBuildInfo);
        when(mMockBuildProvider.getBuild()).thenThrow(exception);
        when(mMockBuildInfo.getTestTag()).thenReturn(null);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        when(mMockLogger.getLog()).thenReturn(mHostLogSource);
        when(mMockDevice.getLogcat()).thenReturn(mLogcatSetupSource).thenReturn(mLogcatTestSource);

        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        CommandOptions cmdOptions = new CommandOptions();
        final String expectedTestTag = "TEST_TAG";
        cmdOptions.setTestTag(expectedTestTag);
        mStubConfiguration.setCommandOptions(cmdOptions);
        mStubConfiguration.setTest(test);

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            // Expected
        }

        // Needed a full custom set of verifications because it is messy
        try {
            // always expect logger initialization and cleanup calls
            verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
            verify(mMockLogRegistry, times(2)).unregisterLogger();

            verify(mMockLogger, times(3)).init();
            verify(mMockLogger, times(2)).closeLog();
            verify(mMockLogger).getLog();

            verify(mMockBuildProvider).cleanUp(mMockBuildInfo);

            verify(mMockBuildInfo).setTestTag(expectedTestTag);
        } catch (IOException e) {
            // Never happens since these are all verify calls
        }

        verifyMockFailureListeners(exception);
    }

    /** Ensure we get a build info when we get a runtime exception in fetch build. */
    @Test
    public void testInvoke_buildFailed_runtimeException() throws Throwable {
        RuntimeException runtimeException = new RuntimeException("failed to get build.");
        when(mMockBuildProvider.getBuild()).thenThrow(runtimeException);

        BuildRetrievalError error =
                new BuildRetrievalError("fake", InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR);
        stubMockFailureListenersAny(error, true);

        when(mMockLogger.getLog()).thenReturn(mHostLogSource);
        when(mMockDevice.getLogcat())
                .thenReturn(mLogcatSetupSource)
                .thenReturn(mLogcatTeardownSource);
        ArgumentCaptor<IBuildInfo> captured = ArgumentCaptor.forClass(IBuildInfo.class);

        mStubConfiguration.setCommandLine(new String[] {"empty", "--build-id", "5"});

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // Expected
        }

        verify(mMockBuildProvider).cleanUp(captured.capture());
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();

        verifyMockFailureListenersAny(error, true);

        IBuildInfo stubBuild = captured.getValue();
        assertEquals("5", stubBuild.getBuildId());
        stubBuild.cleanUp();
    }

    /** Test the invoke scenario where there is no build to test. */
    @Test
    public void testInvoke_noBuild() throws Throwable {
        when(mMockBuildProvider.getBuild()).thenReturn(null);
        BuildRetrievalError error =
                new BuildRetrievalError(
                        "No build found to test.", InfraErrorIdentifier.ARTIFACT_NOT_FOUND);

        stubMockFailureListenersAny(error, true);

        when(mMockLogger.getLog()).thenReturn(mHostLogSource);
        when(mMockDevice.getLogcat()).thenReturn(mLogcatSetupSource).thenReturn(mLogcatTestSource);
        ArgumentCaptor<IBuildInfo> captured = ArgumentCaptor.forClass(IBuildInfo.class);

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            // Expected
        }

        verify(mMockBuildProvider).cleanUp(captured.capture());
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();

        verifyMockFailureListenersAny(error, true);

        IBuildInfo stubBuild = captured.getValue();
        assertEquals(BuildInfo.UNKNOWN_BUILD_ID, stubBuild.getBuildId());
        stubBuild.cleanUp();
    }

    /**
     * Test when the reporting of host_log is returning null, in this case we don't log anything.
     */
    @Test
    public void testInvoke_noBuild_noHostLog() throws Throwable {
        when(mMockBuildProvider.getBuild()).thenReturn(null);

        Throwable error =
                new BuildRetrievalError(
                        "No build found to test.", InfraErrorIdentifier.ARTIFACT_NOT_FOUND);
        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        mStubConfiguration.setTest(test);

        stubMockFailureListeners(error, true, false);

        when(mMockLogger.getLog()).thenReturn(null);
        when(mMockDevice.getLogcat()).thenReturn(mLogcatSetupSource).thenReturn(mLogcatTestSource);
        ArgumentCaptor<IBuildInfo> captured = ArgumentCaptor.forClass(IBuildInfo.class);

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            // Expected
        }

        verify(mMockBuildProvider).cleanUp(captured.capture());
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();

        verifyMockFailureListeners(error, true, false);

        IBuildInfo stubBuild = captured.getValue();
        assertEquals(BuildInfo.UNKNOWN_BUILD_ID, stubBuild.getBuildId());
        stubBuild.cleanUp();
    }

    /** Ensure that we do not take a bugreport if the build throws a runtime exception */
    @Test
    public void testInvoke_skipBugreport_buildFailed() throws Throwable {
        RuntimeException runtimeException = new RuntimeException("failed to get build.");
        when(mMockBuildProvider.getBuild()).thenThrow(runtimeException);

        BuildRetrievalError error =
                new BuildRetrievalError("fake", InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR);
        stubMockFailureListenersAny(error, true);

        when(mMockLogger.getLog()).thenReturn(mHostLogSource);
        when(mMockDevice.getLogcat())
                .thenReturn(mLogcatSetupSource)
                .thenReturn(mLogcatTeardownSource);
        ArgumentCaptor<IBuildInfo> captured = ArgumentCaptor.forClass(IBuildInfo.class);

        mStubConfiguration.setCommandLine(new String[] {"empty", "--build-id", "5"});

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // Expected
        }

        verify(mMockBuildProvider).cleanUp(captured.capture());
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();
        verify(mMockDevice, times(0))
                .logBugreport(Mockito.anyString(), (ITestInvocationListener) Mockito.any());

        verifyMockFailureListenersAny(error, true);

        IBuildInfo stubBuild = captured.getValue();
        assertEquals("5", stubBuild.getBuildId());
        stubBuild.cleanUp();
    }

    /**
     * Test the{@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario where the test is a {@link IDeviceTest}
     */
    @Test
    public void testInvoke_deviceTest() throws Throwable {
        DeviceConfigTest mockDeviceTest = Mockito.mock(DeviceConfigTest.class);
        mStubConfiguration.setTest(mockDeviceTest);

        stubMockSuccessListeners();
        stubEarlyDeviceReleaseExpectation();
        stubNormalInvoke(mockDeviceTest);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyMockSuccessListeners();

        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.isNull());
        verify(mockDeviceTest).setDevice(mMockDevice);
        verify(mockDeviceTest).run(Mockito.any(), Mockito.any());

        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link IllegalArgumentException}
     *
     * @throws Exception if unexpected error occurs
     */
    @Test
    public void testInvoke_testFail() throws Throwable {
        IllegalArgumentException exception = new IllegalArgumentException("testInvoke_testFail");
        mExceptedStatus = FailureStatus.UNSET;
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        stubNormalInvoke(test);

        doThrow(exception).when(test).run(Mockito.any(), Mockito.any());

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("IllegalArgumentException was not rethrown");
        } catch (IllegalArgumentException e) {
            // expected
        }

        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.eq(exception));
        verifyMockFailureListeners(exception);
        verifyNormalInvoke(test);

        verifySummaryListener();
    }

    /**
     * Test that tests were skipped and metrics SHUTDOWN_HARD_LATENCY is collected when the
     * invocation is stopped/interrupted before test phase started.
     */
    @Test
    public void testInvoke_metricsCollectedWhenStopped() throws Throwable {
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockStoppedListeners();
        stubNormalInvoke(test);

        mTestInvocation.notifyInvocationForceStopped(
                "Stopped", InfraErrorIdentifier.INVOCATION_TIMEOUT);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verify(test, never()).run(Mockito.any(), Mockito.any());
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.any());

        verifyNormalInvoke(test);
        verifyMockStoppedListeners(true);

        assertTrue(
                mStubInvocationMetadata
                        .getAttributes()
                        .containsKey(InvocationMetricKey.SHUTDOWN_HARD_LATENCY.toString()));
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link FatalHostError}
     *
     * @throws Exception if unexpected error occurs
     */
    @Test
    public void testInvoke_fatalError() throws Throwable {
        FatalHostError exception = new FatalHostError("testInvoke_fatalError");
        mExceptedStatus = FailureStatus.UNSET;
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        doThrow(exception).when(test).run(Mockito.any(), Mockito.any());
        stubNormalInvoke(test);

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("FatalHostError was not rethrown");
        } catch (FatalHostError e) {
            // expected
        }

        verifyNormalInvoke(test);
        verifyMockFailureListeners(exception);

        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link DeviceNotAvailableException}
     *
     * @throws Exception if unexpected error occurs
     */
    @Test
    public void testInvoke_deviceNotAvail() throws Throwable {
        DeviceNotAvailableException exception = new DeviceNotAvailableException("ERROR", SERIAL);
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        stubNormalInvoke(test);

        doThrow(exception).when(test).run(Mockito.any(), Mockito.any());

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verifyNormalInvoke(test);
        verifyMockFailureListeners(exception);

        verifySummaryListener();
    }

    @Test
    public void testInvoke_setupError() throws Throwable {
        TargetSetupError tse = createTargetSetupError("reason");
        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        mStubConfiguration.setTest(test);
        mStubMultiConfiguration.setTest(test);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(tse);
        stubInvokeWithBuild();

        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);
        when(mMockDevice.logBugreport(
                        Mockito.startsWith("target_setup_error_bugreport"), Mockito.any()))
                .thenReturn(true);
        when(mMockBuildProvider.getBuild()).thenReturn(mMockBuildInfo);
        doThrow(tse).when(mMockPreparer).setUp(Mockito.any());
        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("stub"));

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyInvokeWithBuild();
        verifyMockFailureListeners(tse);

        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where preparer throws {@link BuildError}
     *
     * @throws Exception if unexpected error occurs
     */
    @Test
    public void testInvoke_buildError() throws Throwable {
        BuildError exception =
                new BuildError(
                        "error", mFakeDescriptor, InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR);
        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        mStubConfiguration.setTest(test);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        stubInvokeWithBuild();

        when(mMockBuildProvider.getBuild()).thenReturn(mMockBuildInfo);
        doThrow(exception).when(mMockPreparer).setUp(Mockito.any());
        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);
        when(mMockDevice.getBugreport()).thenReturn(new ByteArrayInputStreamSource(new byte[0]));

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyInvokeWithBuild();
        verifyMockFailureListeners(exception);

        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario when a {@link ITargetPreparer} is part of the config.
     */
    @Test
    public void testInvoke_tearDown() throws Throwable {
        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        ITargetPreparer mockCleaner = Mockito.mock(ITargetPreparer.class);
        mStubConfiguration.getTargetPreparers().add(mockCleaner);

        stubEarlyDeviceReleaseExpectation();
        stubMockSuccessListeners();
        stubNormalInvoke(test);

        when(mockCleaner.isDisabled()).thenReturn(false);
        when(mockCleaner.isTearDownDisabled()).thenReturn(false);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verify(mockCleaner, times(2)).isDisabled();
        verify(mockCleaner).isTearDownDisabled();
        verify(mockCleaner).setUp(Mockito.any());
        verify(mockCleaner).tearDown(Mockito.any(), Mockito.isNull());
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.isNull());

        verifyNormalInvoke(test);
        verifyMockSuccessListeners();

        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario when a {@link ITargetPreparer} is part of the config,
     * and the test throws a {@link DeviceNotAvailableException}.
     */
    @Test
    public void testInvoke_tearDown_deviceNotAvail() throws Throwable {
        DeviceNotAvailableException exception = new DeviceNotAvailableException("ERROR", SERIAL);
        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        ITargetPreparer mockCleaner = Mockito.mock(ITargetPreparer.class);
        mStubConfiguration.getTargetPreparers().add(mockCleaner);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        stubNormalInvoke(test);

        when(mockCleaner.isDisabled()).thenReturn(false);
        when(mockCleaner.isTearDownDisabled()).thenReturn(false);
        doThrow(exception).when(test).run(Mockito.any(), Mockito.any());

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mockCleaner, times(2)).isDisabled();
        verify(mockCleaner).isTearDownDisabled();
        verify(mockCleaner).setUp(Mockito.any());
        verify(mockCleaner).tearDown(Mockito.any(), Mockito.eq(exception));
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.eq(exception));

        verifyNormalInvoke(test);
        verifyMockFailureListeners(exception);

        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario when a {@link ITargetCleaner} is part of the config, and
     * the test throws a {@link RuntimeException}.
     */
    @Test
    public void testInvoke_tearDown_runtime() throws Throwable {
        RuntimeException exception = new RuntimeException("testInvoke_tearDown_runtime");
        mExceptedStatus = FailureStatus.UNSET;
        IRemoteTest test = Mockito.mock(IRemoteTest.class);
        ITargetPreparer mockCleaner = Mockito.mock(ITargetPreparer.class);
        mStubConfiguration.getTargetPreparers().add(mockCleaner);

        stubEarlyDeviceReleaseExpectation();
        stubMockFailureListeners(exception);
        stubNormalInvoke(test);

        when(mockCleaner.isDisabled()).thenReturn(false);
        when(mockCleaner.isTearDownDisabled()).thenReturn(false);
        doThrow(exception).when(test).run(Mockito.any(), Mockito.any());

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("RuntimeException not thrown");
        } catch (RuntimeException e) {
            // expected
        }

        verify(mockCleaner, times(2)).isDisabled();
        verify(mockCleaner).isTearDownDisabled();
        verify(mockCleaner).setUp(Mockito.any());
        verify(mockCleaner).tearDown(Mockito.any(), Mockito.eq(exception));
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.eq(exception));

        verifyNormalInvoke(test);
        verifyMockFailureListeners(exception);

        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario when there is {@link ITestInvocationListener} which
     * implements the {@link ILogSaverListener} interface.
     */
    @Test
    public void testInvoke_logFileSaved() throws Throwable {
        List<ITestInvocationListener> listenerList =
                mStubConfiguration.getTestInvocationListeners();
        ILogSaverListener logSaverListener = Mockito.mock(ILogSaverListener.class);
        listenerList.add(logSaverListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);

        IRemoteTest test = Mockito.mock(IRemoteTest.class);

        stubEarlyDeviceReleaseExpectation();
        stubMockSuccessListeners();
        stubNormalInvoke(test);

        when(logSaverListener.getSummary()).thenReturn(mSummary);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        InOrder io = inOrder(logSaverListener);
        io.verify(logSaverListener, calls(1)).setLogSaver(mMockLogSaver);
        io.verify(logSaverListener, calls(1)).invocationStarted(mStubInvocationMetadata);
        io.verify(logSaverListener, calls(1))
                .testLog(
                        Mockito.contains(CONFIG_LOG_NAME),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        (InputStreamSource) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLogSaved(
                        Mockito.contains(CONFIG_LOG_NAME),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        (InputStreamSource) Mockito.any(),
                        (LogFile) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .logAssociation(Mockito.contains(CONFIG_LOG_NAME), Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLog(
                        Mockito.startsWith(LOGCAT_NAME_SETUP),
                        Mockito.eq(LogDataType.LOGCAT),
                        (InputStreamSource) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLogSaved(
                        Mockito.startsWith(LOGCAT_NAME_SETUP),
                        Mockito.eq(LogDataType.LOGCAT),
                        (InputStreamSource) Mockito.any(),
                        (LogFile) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .logAssociation(Mockito.startsWith(LOGCAT_NAME_SETUP), Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLog(
                        Mockito.startsWith(LOGCAT_NAME_TEST),
                        Mockito.eq(LogDataType.LOGCAT),
                        (InputStreamSource) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLogSaved(
                        Mockito.startsWith(LOGCAT_NAME_TEST),
                        Mockito.eq(LogDataType.LOGCAT),
                        (InputStreamSource) Mockito.any(),
                        (LogFile) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .logAssociation(Mockito.startsWith(LOGCAT_NAME_TEST), Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLog(
                        Mockito.startsWith(LOGCAT_NAME_TEARDOWN),
                        Mockito.eq(LogDataType.LOGCAT),
                        (InputStreamSource) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLogSaved(
                        Mockito.startsWith(LOGCAT_NAME_TEARDOWN),
                        Mockito.eq(LogDataType.LOGCAT),
                        (InputStreamSource) Mockito.any(),
                        (LogFile) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .logAssociation(Mockito.startsWith(LOGCAT_NAME_TEARDOWN), Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLog(
                        Mockito.eq(TestInvocation.TRADEFED_LOG_NAME),
                        Mockito.eq(LogDataType.HOST_LOG),
                        (InputStreamSource) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .testLogSaved(
                        Mockito.eq(TestInvocation.TRADEFED_LOG_NAME),
                        Mockito.eq(LogDataType.HOST_LOG),
                        (InputStreamSource) Mockito.any(),
                        (LogFile) Mockito.any());
        io.verify(logSaverListener, calls(1))
                .logAssociation(Mockito.eq(TestInvocation.TRADEFED_LOG_NAME), Mockito.any());
        io.verify(logSaverListener, calls(1)).invocationEnded(Mockito.anyLong());

        verify(test).run(Mockito.any(), Mockito.any());
        verify(mMockPreparer).tearDown(Mockito.any(), Mockito.isNull());
        verify(logSaverListener, times(2)).getSummary();

        verifyNormalInvoke(test);
        verifyMockSuccessListeners();

        assertEquals(2, mUriCapture.getValue().size());
    }

    @Test
    public void testShouldSkipBugreportError_buildError() throws Throwable {
        Throwable e =
                new BuildError(
                        "error", mFakeDescriptor, InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR);
        assertTrue(mTestInvocation.shouldSkipBugreportError(e));
    }

    @Test
    public void testShouldSkipBugreportError_wifiOptionError() throws Throwable {
        Throwable e =
                new TargetSetupError(
                        "wifi-network not specified",
                        InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        assertTrue(mTestInvocation.shouldSkipBugreportError(e));
    }

    @Test
    public void testShouldSkipBugreportError_wifiConnectionError() throws Throwable {
        Throwable e =
                new TargetSetupError("wifi-network not specified", InfraErrorIdentifier.NO_WIFI);
        assertFalse(mTestInvocation.shouldSkipBugreportError(e));
    }

    @Test
    public void testShouldSkipBugreportError_internalConfigError() throws Throwable {
        Throwable e =
                new HarnessRuntimeException(
                        "Invalid Configuration", InfraErrorIdentifier.INTERNAL_CONFIG_ERROR);
        assertFalse(mTestInvocation.shouldSkipBugreportError(e));
    }

    /** Test the test-tag is set when the IBuildInfo's test-tag is not. */
    @Ignore
    @Test
    public void testInvoke_testtag() throws Throwable {
        /*
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        mStubConfiguration.getCommandOptions().setTestTag("not-default");
        setEarlyDeviceReleaseExpectation();
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatSetupSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTestSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTeardownSource);
        mMockDevice.clearLogcat();
        EasyMock.expectLastCall().times(3);
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(EasyMock.anyObject());
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());
        // Default build is "stub" so we set the test-tag
        mMockBuildInfo.setTestTag(EasyMock.eq("not-default"));
        EasyMock.expectLastCall();
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("stub");
        mMockLogRegistry.unregisterLogger();
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        mMockLogger.closeLog();
        replayMocks();
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks();
        */
    }

    /**
     * Test the test-tag of the IBuildInfo is not modified when the CommandOption default test-tag
     * is not modified.
     */
    @Ignore
    @Test
    public void testInvoke_testtag_notset() throws Throwable {
        /*
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        setEarlyDeviceReleaseExpectation();
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatSetupSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTestSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTeardownSource);
        mMockDevice.clearLogcat();
        EasyMock.expectLastCall().times(3);
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(EasyMock.anyObject());
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("buildprovidertesttag");
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        mMockLogRegistry.unregisterLogger();
        mMockLogger.closeLog();
        replayMocks();
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks();
        */
    }

    /**
     * Test the test-tag of the IBuildInfo is not set and Command Option is not set either. A
     * default 'stub' test-tag is set to ensure reporting is done.
     */
    @Ignore
    @Test
    public void testInvoke_notesttag() throws Throwable {
        /*
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        setEarlyDeviceReleaseExpectation();
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatSetupSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTestSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTeardownSource);
        mMockDevice.clearLogcat();
        EasyMock.expectLastCall().times(3);
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(EasyMock.anyObject());
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn(null);
        mMockBuildInfo.setTestTag(EasyMock.eq("stub"));
        EasyMock.expectLastCall();
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        mMockLogRegistry.unregisterLogger();
        mMockLogger.closeLog();
        replayMocks();
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks();
        */
    }

    /**
     * Test the injection of test-tag from TestInvocation to the build provider via the {@link
     * IInvocationContextReceiver}.
     */
    @Ignore
    @Test
    public void testInvoke_buildProviderNeedTestTag() throws Throwable {
        /*
        final String testTag = "THISISTHETAG";
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        ICommandOptions commandOption = new CommandOptions();
        commandOption.setTestTag(testTag);
        IFakeBuildProvider mockProvider = EasyMock.createMock(IFakeBuildProvider.class);
        mStubConfiguration.setBuildProvider(mockProvider);
        mStubConfiguration.setCommandOptions(commandOption);
        setEarlyDeviceReleaseExpectation();
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatSetupSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTestSource);
        EasyMock.expect(mMockDevice.getLogcat()).andReturn(mLogcatTeardownSource);
        mMockDevice.clearLogcat();
        EasyMock.expectLastCall().times(3);
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        mMockBuildInfo.setDeviceSerial(SERIAL);
        setupMockSuccessListeners();
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(EasyMock.anyObject());
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn(null);
        // Validate proper tag is set on the build.
        mMockBuildInfo.setTestTag(EasyMock.eq(testTag));
        mockProvider.setInvocationContext((IInvocationContext) EasyMock.anyObject());
        EasyMock.expect(mockProvider.getBuild(mMockDevice)).andReturn(mMockBuildInfo);
        EasyMock.expectLastCall().anyTimes();
        mockProvider.cleanUp(mMockBuildInfo);
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        mMockLogRegistry.unregisterLogger();
        mMockLogger.closeLog();

        replayMocks(mockProvider);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(mockProvider);
        */
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario with {@link IShardableTest}.
     */
    @Ignore
    @Test
    public void testInvoke_shardableTest_legacy() throws Throwable {
        /*
        TestInformation info =
                TestInformation.newBuilder().setInvocationContext(mStubInvocationMetadata).build();
        mStubConfiguration.setConfigurationObject(ShardHelper.SHARED_TEST_INFORMATION, info);
        String command = "empty --test-tag t";
        String[] commandLine = {"empty", "--test-tag", "t"};
        int shardCount = 2;
        IShardableTest test = EasyMock.createMock(IShardableTest.class);
        List<IRemoteTest> shards = new ArrayList<>();
        IRemoteTest shard1 = EasyMock.createMock(IRemoteTest.class);
        IRemoteTest shard2 = EasyMock.createMock(IRemoteTest.class);
        shards.add(shard1);
        shards.add(shard2);
        EasyMock.expect(test.split(EasyMock.isNull(), EasyMock.anyObject())).andReturn(shards);
        mStubConfiguration.setTest(test);
        mStubConfiguration.setCommandLine(commandLine);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        // The keystore is cloned for each shard.
        EasyMock.expect(mGlobalConfiguration.getKeyStoreFactory())
                .andReturn(new StubKeyStoreFactory())
                .times(2);
        setupInvoke();
        EasyMock.reset(mMockLogger, mMockLogRegistry);
        mMockLogRegistry.registerLogger(mMockLogger);
        mMockLogger.init();
        mMockLogger.closeLog();
        mMockLogRegistry.unregisterLogger();
        mMockLogSaver.invocationStarted(mStubInvocationMetadata);
        mMockLogSaver.invocationEnded(0L);
        setupNShardInvocation(shardCount, command);
        // Ensure that the host_log gets logged after sharding.
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        String logName = "host_log_before_sharding";
        mMockTestListener.testLog(
                EasyMock.eq(logName), EasyMock.eq(LogDataType.HOST_LOG), EasyMock.anyObject());
        mMockSummaryListener.testLog(
                EasyMock.eq(logName), EasyMock.eq(LogDataType.HOST_LOG), EasyMock.anyObject());
        EasyMock.expect(
                        mMockLogSaver.saveLogData(
                                EasyMock.eq(logName),
                                EasyMock.eq(LogDataType.HOST_LOG),
                                EasyMock.anyObject()))
                .andReturn(new LogFile(PATH, URL, LogDataType.HOST_LOG));
        mMockLogRegistry.unregisterLogger();
        EasyMock.expectLastCall();
        mMockLogger.closeLog();
        EasyMock.expectLastCall();

        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        replayMocks(test, mockRescheduler, shard1, shard2, mGlobalConfiguration);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler, shard1, shard2, mGlobalConfiguration);
        */
    }

    /** Test that the before sharding log is properly carried even with auto-retry. */
    @Ignore
    @Test
    public void testInvoke_shardableTest_autoRetry() throws Throwable {
        /*
        TestInformation info =
                TestInformation.newBuilder().setInvocationContext(mStubInvocationMetadata).build();
        mStubConfiguration.setConfigurationObject(ShardHelper.SHARED_TEST_INFORMATION, info);
        List<ITestInvocationListener> listenerList =
                mStubConfiguration.getTestInvocationListeners();
        ILogSaverListener logSaverListener = EasyMock.createMock(ILogSaverListener.class);
        listenerList.add(logSaverListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);

        logSaverListener.setLogSaver(mMockLogSaver);
        logSaverListener.invocationStarted(mStubInvocationMetadata);

        String command = "empty --test-tag t";
        String[] commandLine = {"empty", "--test-tag", "t"};
        int shardCount = 2;
        IShardableTest test = EasyMock.createMock(IShardableTest.class);
        List<IRemoteTest> shards = new ArrayList<>();
        IRemoteTest shard1 = EasyMock.createMock(IRemoteTest.class);
        IRemoteTest shard2 = EasyMock.createMock(IRemoteTest.class);
        shards.add(shard1);
        shards.add(shard2);
        EasyMock.expect(test.split(EasyMock.isNull(), EasyMock.anyObject())).andReturn(shards);
        mStubConfiguration.setTest(test);
        mStubConfiguration.setCommandLine(commandLine);

        IRetryDecision decision = mStubConfiguration.getRetryDecision();
        OptionSetter decisionSetter = new OptionSetter(decision);
        decisionSetter.setOptionValue("auto-retry", "true");
        decisionSetter.setOptionValue("max-testcase-run-count", "2");
        decisionSetter.setOptionValue("retry-strategy", "RETRY_ANY_FAILURE");
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        // The keystore is cloned for each shard.
        EasyMock.expect(mGlobalConfiguration.getKeyStoreFactory())
                .andReturn(new StubKeyStoreFactory())
                .times(2);
        setupInvoke();
        EasyMock.reset(mMockLogger, mMockLogRegistry);
        mMockLogRegistry.registerLogger(mMockLogger);
        mMockLogger.init();
        mMockLogger.closeLog();
        mMockLogRegistry.unregisterLogger();
        mMockLogSaver.invocationStarted(mStubInvocationMetadata);
        mMockLogSaver.invocationEnded(0L);
        setupNShardInvocation(shardCount, command);
        // Ensure that the host_log gets logged after sharding.
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        String logName = "host_log_before_sharding";
        LogFile loggedFile = new LogFile(PATH, URL, LogDataType.HOST_LOG);
        for (ITestInvocationListener listener : listenerList) {
            listener.testLog(
                    EasyMock.eq(logName), EasyMock.eq(LogDataType.HOST_LOG), EasyMock.anyObject());
        }
        EasyMock.expect(
                        mMockLogSaver.saveLogData(
                                EasyMock.eq(logName),
                                EasyMock.eq(LogDataType.HOST_LOG),
                                EasyMock.anyObject()))
                .andReturn(loggedFile);
        logSaverListener.logAssociation(logName, loggedFile);
        mMockLogRegistry.unregisterLogger();
        EasyMock.expectLastCall();
        mMockLogger.closeLog();
        EasyMock.expectLastCall();

        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        replayMocks(test, mockRescheduler, shard1, shard2, mGlobalConfiguration, logSaverListener);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler, shard1, shard2, mGlobalConfiguration, logSaverListener);
        */
    }

    /**
     * Test that {@link TestInvocation#logDeviceBatteryLevel(IInvocationContext, String)} is not
     * adding battery information for placeholder device.
     */
    @Ignore
    @Test
    public void testLogDeviceBatteryLevel_placeholderDevice() {
        /*
        final String fakeEvent = "event";
        IInvocationContext context = new InvocationContext();
        ITestDevice device1 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device1.getIDevice()).andReturn(new StubDevice("stub"));
        context.addAllocatedDevice("device1", device1);
        EasyMock.replay(device1);
        mTestInvocation.logDeviceBatteryLevel(context, fakeEvent);
        EasyMock.verify(device1);
        assertEquals(0, context.getAttributes().size());
        */
    }

    /**
     * Test that {@link TestInvocation#logDeviceBatteryLevel(IInvocationContext, String)} is adding
     * battery information for physical real device.
     */
    @Ignore
    @Test
    public void testLogDeviceBatteryLevel_physicalDevice() {
        /*
        final String fakeEvent = "event";
        IInvocationContext context = new InvocationContext();
        ITestDevice device1 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device1.getIDevice()).andReturn(EasyMock.createMock(IDevice.class));
        EasyMock.expect(device1.getSerialNumber()).andReturn("serial1");
        EasyMock.expect(device1.getBattery()).andReturn(50);
        context.addAllocatedDevice("device1", device1);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        EasyMock.replay(device1);
        mTestInvocation.logDeviceBatteryLevel(context, fakeEvent);
        EasyMock.verify(device1);
        assertEquals(1, context.getBuildInfo("device1").getBuildAttributes().size());
        assertEquals(
                "50",
                context.getBuildInfo("device1")
                        .getBuildAttributes()
                        .get("serial1-battery-" + fakeEvent));
        */
    }

    /**
     * Test that {@link TestInvocation#logDeviceBatteryLevel(IInvocationContext, String)} is adding
     * battery information for multiple physical real device.
     */
    @Ignore
    @Test
    public void testLogDeviceBatteryLevel_physicalDevice_multi() {
        /*
        final String fakeEvent = "event";
        IInvocationContext context = new InvocationContext();
        ITestDevice device1 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device1.getIDevice()).andReturn(EasyMock.createMock(IDevice.class));
        EasyMock.expect(device1.getSerialNumber()).andReturn("serial1");
        EasyMock.expect(device1.getBattery()).andReturn(50);

        ITestDevice device2 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device2.getIDevice()).andReturn(EasyMock.createMock(IDevice.class));
        EasyMock.expect(device2.getSerialNumber()).andReturn("serial2");
        EasyMock.expect(device2.getBattery()).andReturn(55);

        context.addAllocatedDevice("device1", device1);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        context.addAllocatedDevice("device2", device2);
        context.addDeviceBuildInfo("device2", new BuildInfo());
        EasyMock.replay(device1, device2);
        mTestInvocation.logDeviceBatteryLevel(context, fakeEvent);
        EasyMock.verify(device1, device2);
        assertEquals(1, context.getBuildInfo("device1").getBuildAttributes().size());
        assertEquals(1, context.getBuildInfo("device2").getBuildAttributes().size());
        assertEquals(
                "50",
                context.getBuildInfo("device1")
                        .getBuildAttributes()
                        .get("serial1-battery-" + fakeEvent));
        assertEquals(
                "55",
                context.getBuildInfo("device2")
                        .getBuildAttributes()
                        .get("serial2-battery-" + fakeEvent));
        */
    }

    /**
     * Test that {@link TestInvocation#logDeviceBatteryLevel(IInvocationContext, String)} is adding
     * battery information for multiple physical real device, and ignore stub device if any.
     */
    @Test
    public void testLogDeviceBatteryLevel_physicalDevice_stub_multi() {
        /*
        final String fakeEvent = "event";
        IInvocationContext context = new InvocationContext();
        ITestDevice device1 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device1.getIDevice()).andReturn(EasyMock.createMock(IDevice.class));
        EasyMock.expect(device1.getSerialNumber()).andReturn("serial1");
        EasyMock.expect(device1.getBattery()).andReturn(50);

        ITestDevice device2 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device2.getIDevice()).andReturn(EasyMock.createMock(IDevice.class));
        EasyMock.expect(device2.getSerialNumber()).andReturn("serial2");
        EasyMock.expect(device2.getBattery()).andReturn(50);

        ITestDevice device3 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device3.getIDevice()).andReturn(new StubDevice("stub-3"));

        ITestDevice device4 = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(device4.getIDevice()).andReturn(new TcpDevice("tcp-4"));

        context.addAllocatedDevice("device1", device1);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        context.addAllocatedDevice("device2", device2);
        context.addDeviceBuildInfo("device2", new BuildInfo());
        context.addAllocatedDevice("device3", device3);
        context.addAllocatedDevice("device4", device4);
        EasyMock.replay(device1, device2, device3, device4);
        mTestInvocation.logDeviceBatteryLevel(context, fakeEvent);
        EasyMock.verify(device1, device2, device3, device4);
        assertEquals(1, context.getBuildInfo("device1").getBuildAttributes().size());
        assertEquals(1, context.getBuildInfo("device2").getBuildAttributes().size());
        assertEquals(
                "50",
                context.getBuildInfo("device1")
                        .getBuildAttributes()
                        .get("serial1-battery-" + fakeEvent));
        assertEquals(
                "50",
                context.getBuildInfo("device2")
                        .getBuildAttributes()
                        .get("serial2-battery-" + fakeEvent));
        */
    }

    /**
     * Test when a {@link IDeviceBuildInfo} is passing through we do not attempt to add any external
     * directories when there is none coming from environment.
     */
    @Ignore
    @Test
    public void testInvoke_deviceInfoBuild_noEnv() throws Throwable {
        /*
        mTestInvocation =
                new TestInvocation() {
                    @Override
                    ILogRegistry getLogRegistry() {
                        return mMockLogRegistry;
                    }

                    @Override
                    public IInvocationExecution createInvocationExec(RunMode mode) {
                        return new InvocationExecution() {
                            @Override
                            protected IShardHelper createShardHelper() {
                                return new ShardHelper();
                            }

                            @Override
                            File getExternalTestCasesDirs(EnvVariable envVar) {
                                // Return empty list to ensure we do not have any environment loaded
                                return null;
                            }

                            @Override
                            protected String getAdbVersion() {
                                return null;
                            }

                            @Override
                            protected void logHostAdb(IConfiguration config, ITestLogger logger) {
                                // inop for the common test case.
                            }

                            @Override
                            protected void collectAutoInfo(
                                    IConfiguration config, TestInformation info)
                                    throws DeviceNotAvailableException {
                                // Inop for the command test case.
                            }
                        };
                    }

                    @Override
                    protected void setExitCode(ExitCode code, Throwable stack) {
                        // empty on purpose
                    }

                    @Override
                    protected void applyAutomatedReporters(IConfiguration config) {
                        // Empty on purpose
                    }

                    @Override
                    protected void addInvocationMetric(InvocationMetricKey key, long value) {}

                    @Override
                    protected void addInvocationMetric(InvocationMetricKey key, String value) {}
                };
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        EasyMock.expect(mMockBuildInfo.getProperties()).andStubReturn(new HashSet<>());

        IRemoteTest test = EasyMock.createNiceMock(IRemoteTest.class);
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());

        File tmpTestsDir = FileUtil.createTempDir("invocation-tests-dir");
        try {
            EasyMock.expect(((IDeviceBuildInfo) mMockBuildInfo).getTestsDir())
                    .andReturn(tmpTestsDir);
            setupMockSuccessListeners();
            setEarlyDeviceReleaseExpectation();
            setupNormalInvoke(test);
            EasyMock.replay(mockRescheduler);
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            verifyMocks(mockRescheduler);
            verifySummaryListener();
        } finally {
            FileUtil.recursiveDelete(tmpTestsDir);
        }
        */
    }

    /**
     * Test when a {@link IDeviceBuildInfo} is passing through we attempt to add the external
     * directories to it when they are available.
     */
    @Ignore
    @Test
    public void testInvoke_deviceInfoBuild_withEnv() throws Throwable {
        /*
        File tmpTestsDir = FileUtil.createTempDir("invocation-tests-dir");
        File tmpExternalTestsDir = FileUtil.createTempDir("external-tf-dir");
        File tmpTestsFile = FileUtil.createTempFile("testsfile", "txt", tmpExternalTestsDir);
        try {
            mTestInvocation =
                    new TestInvocation() {
                        @Override
                        ILogRegistry getLogRegistry() {
                            return mMockLogRegistry;
                        }

                        @Override
                        public IInvocationExecution createInvocationExec(RunMode mode) {
                            return new InvocationExecution() {
                                @Override
                                protected IShardHelper createShardHelper() {
                                    return new ShardHelper();
                                }

                                @Override
                                File getExternalTestCasesDirs(EnvVariable envVar) {
                                    if (EnvVariable.ANDROID_TARGET_OUT_TESTCASES.equals(envVar)) {
                                        return tmpExternalTestsDir;
                                    }
                                    return null;
                                }

                                @Override
                                protected String getAdbVersion() {
                                    return null;
                                }

                                @Override
                                protected void logHostAdb(
                                        IConfiguration config, ITestLogger logger) {
                                    // inop for the common test case.
                                }

                                @Override
                                protected void collectAutoInfo(
                                        IConfiguration config, TestInformation info)
                                        throws DeviceNotAvailableException {
                                    // Inop for the command test case.
                                }
                            };
                        }

                        @Override
                        protected void applyAutomatedReporters(IConfiguration config) {
                            // Empty on purpose
                        }

                        @Override
                        protected void setExitCode(ExitCode code, Throwable stack) {
                            // empty on purpose
                        }

                        @Override
                        protected void addInvocationMetric(InvocationMetricKey key, long value) {}

                        @Override
                        protected void addInvocationMetric(InvocationMetricKey key, String value) {}
                    };
            mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
            IRemoteTest test = EasyMock.createNiceMock(IRemoteTest.class);
            ITargetPreparer mockCleaner = EasyMock.createMock(ITargetPreparer.class);
            EasyMock.expect(mockCleaner.isDisabled()).andReturn(false).times(2);
            EasyMock.expect(mockCleaner.isTearDownDisabled()).andReturn(false);
            mockCleaner.setUp(EasyMock.anyObject());
            mockCleaner.tearDown(EasyMock.anyObject(), EasyMock.isNull());
            mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());
            mStubConfiguration.getTargetPreparers().add(mockCleaner);

            mMockBuildInfo.setFile(
                    EasyMock.contains(BuildInfoFileKey.TARGET_LINKED_DIR.getFileKey()),
                    EasyMock.anyObject(),
                    EasyMock.eq("v1"));
            EasyMock.expect(((IDeviceBuildInfo) mMockBuildInfo).getTestsDir())
                    .andReturn(tmpTestsDir);
            EasyMock.expect(mMockBuildInfo.getProperties()).andStubReturn(new HashSet<>());

            setupMockSuccessListeners();
            setEarlyDeviceReleaseExpectation();
            setupNormalInvoke(test);
            EasyMock.replay(mockCleaner, mockRescheduler);
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            verifyMocks(mockCleaner, mockRescheduler);
            verifySummaryListener();
            // Check that the external directory was copied in the testsDir.
            assertTrue(tmpTestsDir.listFiles().length == 1);
            // external-tf-dir - the symlink is the original file name + randomized sequence
            assertTrue(
                    tmpTestsDir
                            .listFiles()[0]
                            .getName()
                            .startsWith(BuildInfoFileKey.TARGET_LINKED_DIR.getFileKey()));
            // testsfile.txt
            assertTrue(tmpTestsDir.listFiles()[0].listFiles().length == 1);
            assertEquals(
                    tmpTestsFile.getName(), tmpTestsDir.listFiles()[0].listFiles()[0].getName());
        } finally {
            FileUtil.recursiveDelete(tmpTestsDir);
            FileUtil.recursiveDelete(tmpExternalTestsDir);
        }
        */
    }

    /**
     * Test when a {@link IDeviceBuildInfo} is passing through we do not attempt to add the external
     * directories to it, since {@link BuildInfoProperties} is set to skip the linking.
     */
    @Ignore
    @Test
    public void testInvoke_deviceInfoBuild_withEnv_andSkipProperty() throws Throwable {
        /*
        File tmpTestsDir = FileUtil.createTempDir("invocation-tests-dir");
        File tmpExternalTestsDir = FileUtil.createTempDir("external-tf-dir");
        FileUtil.createTempFile("testsfile", "txt", tmpExternalTestsDir);
        try {
            mTestInvocation =
                    new TestInvocation() {
                        @Override
                        ILogRegistry getLogRegistry() {
                            return mMockLogRegistry;
                        }

                        @Override
                        public IInvocationExecution createInvocationExec(RunMode mode) {
                            return new InvocationExecution() {
                                @Override
                                protected IShardHelper createShardHelper() {
                                    return new ShardHelper();
                                }

                                @Override
                                File getExternalTestCasesDirs(EnvVariable envVar) {
                                    return tmpExternalTestsDir;
                                }

                                @Override
                                protected String getAdbVersion() {
                                    return null;
                                }

                                @Override
                                protected void logHostAdb(
                                        IConfiguration config, ITestLogger logger) {
                                    // inop for the common test case.
                                }

                                @Override
                                protected void collectAutoInfo(
                                        IConfiguration config, TestInformation info)
                                        throws DeviceNotAvailableException {
                                    // Inop for the command test case.
                                }
                            };
                        }

                        @Override
                        protected void setExitCode(ExitCode code, Throwable stack) {
                            // empty on purpose
                        }

                        @Override
                        protected void applyAutomatedReporters(IConfiguration config) {
                            // Empty on purpose
                        }

                        @Override
                        protected void addInvocationMetric(InvocationMetricKey key, long value) {}

                        @Override
                        protected void addInvocationMetric(InvocationMetricKey key, String value) {}
                    };
            mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
            IRemoteTest test = EasyMock.createNiceMock(IRemoteTest.class);
            mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());

            Set<BuildInfoProperties> prop = new HashSet<>();
            prop.add(BuildInfoProperties.DO_NOT_LINK_TESTS_DIR);
            EasyMock.expect(mMockBuildInfo.getProperties()).andStubReturn(prop);
            setEarlyDeviceReleaseExpectation();
            setupMockSuccessListeners();
            setupNormalInvoke(test);
            EasyMock.replay(mockRescheduler);
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            verifyMocks(mockRescheduler);
            verifySummaryListener();
            // Check that the external directory was NOT copied in the testsDir.
            assertTrue(tmpTestsDir.listFiles().length == 0);
        } finally {
            FileUtil.recursiveDelete(tmpTestsDir);
            FileUtil.recursiveDelete(tmpExternalTestsDir);
        }
        */
    }

    /**
     * Test that when {@link IMetricCollector} are used, they wrap and call in sequence the listener
     * so all metrics end up on the final receiver.
     */
    @Ignore
    @Test
    public void testMetricCollectionChain() throws Throwable {
        /*
        TestInformation info =
                TestInformation.newBuilder().setInvocationContext(mStubInvocationMetadata).build();
        IConfiguration configuration = new Configuration("test", "description");
        StubTest test = new StubTest();
        OptionSetter setter = new OptionSetter(test);
        setter.setOptionValue("run-a-test", "true");
        configuration.setTest(test);

        List<IMetricCollector> collectors = new ArrayList<>();
        collectors.add(new TestableCollector("collector1"));
        collectors.add(new TestableCollector("collector2"));
        collectors.add(new TestableCollector("collector3"));
        collectors.add(new TestableCollector("collector4"));
        configuration.setDeviceMetricCollectors(collectors);

        mMockTestListener.testRunStarted(
                EasyMock.eq("TestStub"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        TestDescription testId = new TestDescription("StubTest", "StubMethod");
        mMockTestListener.testStarted(EasyMock.eq(testId), EasyMock.anyLong());
        mMockTestListener.testEnded(
                EasyMock.eq(testId),
                EasyMock.anyLong(),
                EasyMock.eq(new HashMap<String, Metric>()));
        Capture<HashMap<String, Metric>> captured = new Capture<>();
        mMockTestListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captured));
        EasyMock.replay(mMockTestListener);
        new InvocationExecution().runTests(info, configuration, mMockTestListener);
        EasyMock.verify(mMockTestListener);
        // The collectors are called in sequence
        List<String> listKeys = new ArrayList<>(captured.getValue().keySet());
        assertEquals(4, listKeys.size());
        assertEquals("collector4", listKeys.get(0));
        assertEquals("collector3", listKeys.get(1));
        assertEquals("collector2", listKeys.get(2));
        assertEquals("collector1", listKeys.get(3));
        */
    }

    /**
     * Test that when a device collector is disabled, it will not be part of the initialization and
     * the collector chain.
     */
    @Ignore
    @Test
    public void testMetricCollectionChain_disabled() throws Throwable {
        /*
        TestInformation info =
                TestInformation.newBuilder().setInvocationContext(mStubInvocationMetadata).build();
        IConfiguration configuration = new Configuration("test", "description");
        StubTest test = new StubTest();
        OptionSetter setter = new OptionSetter(test);
        setter.setOptionValue("run-a-test", "true");
        configuration.setTest(test);

        List<IMetricCollector> collectors = new ArrayList<>();
        collectors.add(new TestableCollector("collector1"));
        collectors.add(new TestableCollector("collector2"));
        // Collector 3 is disabled
        TestableCollector col3 = new TestableCollector("collector3");
        col3.setDisable(true);
        collectors.add(col3);
        collectors.add(new TestableCollector("collector4"));
        configuration.setDeviceMetricCollectors(collectors);

        mMockTestListener.testRunStarted(
                EasyMock.eq("TestStub"), EasyMock.eq(1), EasyMock.eq(0), EasyMock.anyLong());
        TestDescription testId = new TestDescription("StubTest", "StubMethod");
        mMockTestListener.testStarted(EasyMock.eq(testId), EasyMock.anyLong());
        mMockTestListener.testEnded(
                EasyMock.eq(testId),
                EasyMock.anyLong(),
                EasyMock.eq(new HashMap<String, Metric>()));
        Capture<HashMap<String, Metric>> captured = new Capture<>();
        mMockTestListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captured));
        EasyMock.replay(mMockTestListener);
        new InvocationExecution().runTests(info, configuration, mMockTestListener);
        EasyMock.verify(mMockTestListener);
        // The collectors are called in sequence
        List<String> listKeys = new ArrayList<>(captured.getValue().keySet());
        assertEquals(3, listKeys.size());
        assertEquals("collector4", listKeys.get(0));
        assertEquals("collector2", listKeys.get(1));
        assertEquals("collector1", listKeys.get(2));
        */
    }

    /** Ensure post processors are called in order. */
    @Ignore
    @Test
    public void testProcessorCollectionChain() throws Throwable {
        /*
        mMockTestListener = EasyMock.createMock(ITestInvocationListener.class);
        List<ITestInvocationListener> listenerList = new ArrayList<ITestInvocationListener>(1);
        listenerList.add(mMockTestListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);

        List<IPostProcessor> processors = new ArrayList<>();
        processors.add(new TestableProcessor("processor1"));
        processors.add(new TestableProcessor("processor2"));
        processors.add(new TestableProcessor("processor3"));
        processors.add(new TestableProcessor("processor4"));
        mStubConfiguration.setPostProcessors(processors);

        mMockTestListener.testRunStarted("TestStub", 1);
        TestDescription testId = new TestDescription("StubTest", "StubMethod");
        mMockTestListener.testStarted(EasyMock.eq(testId), EasyMock.anyLong());
        mMockTestListener.testEnded(
                EasyMock.eq(testId),
                EasyMock.anyLong(),
                EasyMock.eq(new HashMap<String, Metric>()));
        Capture<HashMap<String, Metric>> captured = new Capture<>();
        mMockTestListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captured));

        setupMockSuccessListeners();
        setEarlyDeviceReleaseExpectation();
        setupInvokeWithBuild();
        StubTest test = new StubTest();
        OptionSetter setter = new OptionSetter(test);
        setter.setOptionValue("run-a-test", "true");
        mStubConfiguration.setTest(test);
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);

        mMockPreparer.setUp(EasyMock.anyObject());
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());

        EasyMock.reset(mMockSummaryListener);
        replayMocks();
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(mockRescheduler);

        // The post processors are called in sequence
        List<String> listKeys = new ArrayList<>(captured.getValue().keySet());
        assertEquals(4, listKeys.size());
        assertEquals("processor4", listKeys.get(0));
        assertEquals("processor3", listKeys.get(1));
        assertEquals("processor2", listKeys.get(2));
        assertEquals("processor1", listKeys.get(3));
        */
    }

    /** Ensure post processors are called in order and that ILogSaver is set properly */
    @Ignore
    @Test
    public void testProcessorCollectionChain_logSaver() throws Throwable {
        /*
        TestLogSaverListener mockLogSaverListener = new TestLogSaverListener();
        mMockTestListener = EasyMock.createMock(ITestInvocationListener.class);
        List<ITestInvocationListener> listenerList = new ArrayList<ITestInvocationListener>(1);
        listenerList.add(mMockTestListener);
        listenerList.add(mockLogSaverListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);

        List<IPostProcessor> processors = new ArrayList<>();
        processors.add(new TestableProcessor("processor1"));
        processors.add(new TestableProcessor("processor2"));
        processors.add(new TestableProcessor("processor3"));
        processors.add(new TestableProcessor("processor4"));
        mStubConfiguration.setPostProcessors(processors);

        mMockTestListener.testRunStarted("TestStub", 1);
        TestDescription testId = new TestDescription("StubTest", "StubMethod");
        mMockTestListener.testStarted(EasyMock.eq(testId), EasyMock.anyLong());
        mMockTestListener.testEnded(
                EasyMock.eq(testId),
                EasyMock.anyLong(),
                EasyMock.eq(new HashMap<String, Metric>()));
        Capture<HashMap<String, Metric>> captured = new Capture<>();
        mMockTestListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captured));

        setupMockSuccessListeners();
        setEarlyDeviceReleaseExpectation();
        setupInvokeWithBuild();
        StubTest test = new StubTest();
        OptionSetter setter = new OptionSetter(test);
        setter.setOptionValue("run-a-test", "true");
        mStubConfiguration.setTest(test);
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);

        mMockPreparer.setUp(EasyMock.anyObject());
        mMockPreparer.tearDown(EasyMock.anyObject(), EasyMock.isNull());

        EasyMock.reset(mMockSummaryListener);
        replayMocks();
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(mockRescheduler);

        // The post processors are called in sequence
        List<String> listKeys = new ArrayList<>(captured.getValue().keySet());
        assertEquals(4, listKeys.size());
        assertEquals("processor4", listKeys.get(0));
        assertEquals("processor3", listKeys.get(1));
        assertEquals("processor2", listKeys.get(2));
        assertEquals("processor1", listKeys.get(3));

        assertTrue(mockLogSaverListener.mWasLoggerSet);
        */
    }

    @Ignore
    @Test
    public void testInvoke_shardingException() throws Throwable {
        /*
        mShardingEarlyFailure = true;
        RuntimeException failedShard =
                new HarnessRuntimeException(
                        "Failed to shard", InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
        mTestInvocation =
                new TestInvocation() {
                    @Override
                    ILogRegistry getLogRegistry() {
                        return mMockLogRegistry;
                    }

                    @Override
                    public IInvocationExecution createInvocationExec(RunMode mode) {
                        return new InvocationExecution() {
                            @Override
                            public boolean shardConfig(
                                    IConfiguration config,
                                    TestInformation testInfo,
                                    IRescheduler rescheduler,
                                    ITestLogger logger) {
                                throw failedShard;
                            }

                            @Override
                            protected String getAdbVersion() {
                                return null;
                            }

                            @Override
                            protected void logHostAdb(IConfiguration config, ITestLogger logger) {
                                // inop for the common test case.
                            }
                        };
                    }

                    @Override
                    protected void setExitCode(ExitCode code, Throwable stack) {
                        // Empty on purpose
                    }

                    @Override
                    public void registerExecutionFiles(ExecutionFiles executionFiles) {
                        // Empty on purpose
                    }

                    @Override
                    protected void applyAutomatedReporters(IConfiguration config) {
                        // Empty on purpose
                    }

                    @Override
                    protected void addInvocationMetric(InvocationMetricKey key, long value) {}

                    @Override
                    protected void addInvocationMetric(InvocationMetricKey key, String value) {}
                };
        mStubConfiguration.getCommandOptions().setShardCount(5);
        mStubConfiguration.getCommandOptions().setShardIndex(2);
        mMockBuildInfo.addBuildAttribute("shard_count", "5");
        mMockBuildInfo.addBuildAttribute("shard_index", "2");
        mMockDevice.postInvocationTearDown(EasyMock.anyObject());

        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockFailureListeners(failedShard);

        setEarlyDeviceReleaseExpectation();
        setupInvokeWithBuild();
        mStubConfiguration.setTest(test);
        mStubMultiConfiguration.setTest(test);
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        replayMocks(test);
        EasyMock.reset(mMockLogger, mMockLogRegistry);
        mMockLogRegistry.registerLogger(mMockLogger);

        mMockLogger.init();
        mMockLogger.closeLog();
        EasyMock.expectLastCall().times(2);
        mMockLogRegistry.unregisterLogger();
        EasyMock.expectLastCall().times(2);
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        EasyMock.expect(mMockLogger.getLog()).andReturn(mHostLogSource);
        EasyMock.replay(mockRescheduler, mMockLogger, mMockLogRegistry);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
        */
    }
}
