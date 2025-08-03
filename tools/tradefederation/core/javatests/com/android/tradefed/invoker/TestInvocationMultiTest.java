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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.command.CommandRunner.ExitCode;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.shard.IShardHelper;
import com.android.tradefed.invoker.shard.ShardHelper;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.ILogRegistry;
import com.android.tradefed.postprocessor.IPostProcessor;
import com.android.tradefed.result.ActionInProgress;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.result.skipped.SkipManager;
import com.android.tradefed.retry.BaseRetryDecision;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Unit tests for {@link TestInvocation} for multi device invocation. */
@SuppressWarnings("MustBeClosedChecker")
@RunWith(JUnit4.class)
public class TestInvocationMultiTest {
    private TestInvocation mInvocation;
    private IInvocationContext mContext;
    @Mock IConfiguration mMockConfig;
    @Mock IRescheduler mMockRescheduler;
    @Mock ITestInvocationListener mMockTestListener;
    @Mock ILogSaver mMockLogSaver;
    @Mock ILeveledLogOutput mMockLogger;
    @Mock ILogRegistry mMockLogRegistry;
    private ConfigurationDescriptor mConfigDesc;

    private ITestDevice mDevice1;
    private ITestDevice mDevice2;
    private IBuildProvider mProvider1;
    private IBuildProvider mProvider2;

    private List<IPostProcessor> mPostProcessors;

    @Before
    public void setUp() throws ConfigurationException {
        MockitoAnnotations.initMocks(this);

        mContext = new InvocationContext();
        mPostProcessors = new ArrayList<>();

        when(mMockConfig.getSkipManager()).thenReturn(new SkipManager());
        when(mMockConfig.getPostProcessors()).thenReturn(mPostProcessors);
        when(mMockConfig.getRetryDecision()).thenReturn(new BaseRetryDecision());
        when(mMockConfig.getConfigurationObject(ShardHelper.SHARED_TEST_INFORMATION))
                .thenReturn(null);
        when(mMockConfig.getConfigurationObject(ShardHelper.LAST_SHARD_DETECTOR)).thenReturn(null);
        mMockConfig.setConfigurationObject(Mockito.eq("TEST_INFORMATION"), Mockito.any());
        when(mMockConfig.getConfigurationObject("DELEGATE")).thenReturn(null);
        when(mMockConfig.getInopOptions()).thenReturn(new HashSet<>());

        mConfigDesc = new ConfigurationDescriptor();
        mInvocation =
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
                };
    }

    private void makeTwoDeviceContext() throws Exception {
        mDevice1 = mock(ITestDevice.class);
        when(mDevice1.getIDevice()).thenReturn(new StubDevice("serial1"));
        when(mDevice1.getSerialNumber()).thenReturn("serial1");
        mDevice1.clearLastConnectedWifiNetwork();
        DeviceConfigurationHolder holder1 = new DeviceConfigurationHolder();
        mProvider1 = mock(IBuildProvider.class);
        holder1.addSpecificConfig(mProvider1);
        when(mMockConfig.getDeviceConfigByName("device1")).thenReturn(holder1);
        mDevice1.setOptions(Mockito.any());
        mDevice1.setRecovery(Mockito.any());
        when(mDevice1.getLogcat()).thenReturn(new ByteArrayInputStreamSource(new byte[0]));
        mDevice1.clearLogcat();

        mDevice2 = mock(ITestDevice.class);
        when(mDevice2.getIDevice()).thenReturn(new StubDevice("serial2"));
        when(mDevice2.getSerialNumber()).thenReturn("serial2");
        mDevice2.clearLastConnectedWifiNetwork();
        DeviceConfigurationHolder holder2 = new DeviceConfigurationHolder();
        mProvider2 = mock(IBuildProvider.class);
        holder2.addSpecificConfig(mProvider2);
        when(mMockConfig.getDeviceConfigByName("device2")).thenReturn(holder2);
        mDevice2.setOptions(Mockito.any());
        when(mDevice2.getLogcat()).thenReturn(new ByteArrayInputStreamSource(new byte[0]));
        mDevice2.clearLogcat();

        mContext.addAllocatedDevice("device1", mDevice1);
        mContext.addAllocatedDevice("device2", mDevice2);
    }

    /**
     * Test for multi device invocation when the first download succeed and second one is missing.
     * We clean up all the downloaded builds.
     */
    @Test
    public void testRunBuildProvider_oneMiss() throws Throwable {
        makeTwoDeviceContext();

        List<ITestInvocationListener> configListener = new ArrayList<>();
        configListener.add(mMockTestListener);
        when(mMockConfig.getTestInvocationListeners()).thenReturn(configListener);
        when(mMockConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mMockConfig.getLogOutput()).thenReturn(mMockLogger);
        when(mMockConfig.getConfigurationDescription()).thenReturn(mConfigDesc);

        when(mMockLogger.getLog()).thenReturn(new ByteArrayInputStreamSource("fake".getBytes()));

        when(mMockConfig.getCommandLine()).thenReturn("empty");
        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());
        when(mMockConfig.getTests()).thenReturn(new ArrayList<>());

        IBuildInfo build1 = new BuildInfo();
        when(mProvider1.getBuild()).thenReturn(build1);
        // Second build is not found
        when(mProvider2.getBuild()).thenReturn(null);
        // The downloaded build is cleaned

        ArgumentCaptor<IBuildInfo> captured = ArgumentCaptor.forClass(IBuildInfo.class);

        when(mMockTestListener.getSummary()).thenReturn(null);

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.HARNESS_CONFIG));

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));
        when(mMockLogSaver.saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));

        when(mMockTestListener.getSummary()).thenReturn(null);

        try {
            mInvocation.invoke(
                    mContext, mMockConfig, mMockRescheduler, new ITestInvocationListener[] {});
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            // Expected
        }
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockConfig, times(1)).getTestInvocationListeners();
        verify(mMockConfig, times(3)).getConfigurationDescription();
        verify(mMockConfig).resolveDynamicOptions(Mockito.any());
        verify(mMockConfig).cleanConfigurationData();
        verify(mProvider1).cleanUp(build1);
        verify(mProvider2).cleanUp(captured.capture());
        verify(mMockTestListener).invocationStarted(mContext);
        verify(mMockLogSaver).invocationStarted(mContext);
        verify(mMockConfig)
                .dumpXml(Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.eq(false));
        verify(mMockTestListener, times(3)).testLog(Mockito.any(), Mockito.any(), Mockito.any());
        verify(mMockTestListener).invocationFailed(Mockito.<FailureDescription>any());
        verify(mMockTestListener).invocationEnded(Mockito.anyLong());
        verify(mMockLogSaver).invocationEnded(Mockito.anyLong());

        IBuildInfo stubBuild = captured.getValue();
        assertEquals(BuildInfo.UNKNOWN_BUILD_ID, stubBuild.getBuildId());
        stubBuild.cleanUp();
    }

    /**
     * Test when the {@link IConfiguration#resolveDynamicOptions(DynamicRemoteFileResolver)} fails,
     * ensure we report all the logs and error.
     */
    @Test
    public void testResolveDynamicFails() throws Throwable {
        mDevice1 = mock(ITestDevice.class);
        when(mDevice1.getIDevice()).thenReturn(new StubDevice("serial1"));
        when(mDevice1.getLogcat()).thenReturn(new ByteArrayInputStreamSource(new byte[0]));

        mDevice2 = mock(ITestDevice.class);
        when(mDevice2.getIDevice()).thenReturn(new StubDevice("serial1"));
        when(mDevice2.getLogcat()).thenReturn(new ByteArrayInputStreamSource(new byte[0]));

        mContext.addAllocatedDevice("device1", mDevice1);
        mContext.addAllocatedDevice("device2", mDevice2);

        List<ITestInvocationListener> configListener = new ArrayList<>();
        configListener.add(mMockTestListener);
        when(mMockConfig.getTestInvocationListeners()).thenReturn(configListener);
        when(mMockConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mMockConfig.getLogOutput()).thenReturn(mMockLogger);
        when(mMockConfig.getConfigurationDescription()).thenReturn(mConfigDesc);

        when(mMockLogger.getLog()).thenReturn(new ByteArrayInputStreamSource("fake".getBytes()));

        when(mMockConfig.getCommandLine()).thenReturn("empty");
        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());
        when(mMockConfig.getTests()).thenReturn(new ArrayList<>());

        ConfigurationException configException = new ConfigurationException("failed to resolve");
        doThrow(configException).when(mMockConfig).resolveDynamicOptions(Mockito.any());

        DeviceConfigurationHolder holder1 = new DeviceConfigurationHolder();
        mProvider1 = mock(IBuildProvider.class);
        holder1.addSpecificConfig(mProvider1);
        when(mMockConfig.getDeviceConfigByName("device1")).thenReturn(holder1);
        when(mDevice1.getSerialNumber()).thenReturn("serial1");

        when(mMockTestListener.getSummary()).thenReturn(null);

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.HARNESS_CONFIG));

        FailureDescription failure =
                FailureDescription.create(configException.getMessage(), FailureStatus.INFRA_FAILURE)
                        .setActionInProgress(ActionInProgress.FETCHING_ARTIFACTS);

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));
        when(mMockLogSaver.saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));

        when(mMockTestListener.getSummary()).thenReturn(null);

        try {
            mInvocation.invoke(
                    mContext, mMockConfig, mMockRescheduler, new ITestInvocationListener[] {});
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // Expected
        }
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockConfig, times(1)).getTestInvocationListeners();
        verify(mMockConfig, times(3)).getConfigurationDescription();
        verify(mDevice1).clearLogcat();
        verify(mDevice2).clearLogcat();
        verify(mMockConfig).cleanConfigurationData();
        verify(mMockTestListener).invocationStarted(mContext);
        verify(mMockLogSaver).invocationStarted(mContext);
        verify(mMockConfig)
                .dumpXml(Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.eq(false));
        verify(mMockTestListener, times(3)).testLog(Mockito.any(), Mockito.any(), Mockito.any());
        verify(mMockTestListener).invocationFailed(Mockito.eq(failure));
        verify(mMockTestListener).invocationEnded(Mockito.anyLong());
        verify(mMockLogSaver).invocationEnded(Mockito.anyLong());
    }

    @Test
    public void testRunBuildProvider_oneThrow() throws Throwable {
        makeTwoDeviceContext();

        List<ITestInvocationListener> configListener = new ArrayList<>();
        configListener.add(mMockTestListener);
        when(mMockConfig.getTestInvocationListeners()).thenReturn(configListener);
        when(mMockConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mMockConfig.getLogOutput()).thenReturn(mMockLogger);
        when(mMockConfig.getConfigurationDescription()).thenReturn(mConfigDesc);

        when(mMockLogger.getLog()).thenReturn(new ByteArrayInputStreamSource("fake".getBytes()));

        when(mMockConfig.getCommandLine()).thenReturn("empty");
        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());
        when(mMockConfig.getTests()).thenReturn(new ArrayList<>());

        when(mMockTestListener.getSummary()).thenReturn(null);

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.HARNESS_CONFIG));

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));
        when(mMockLogSaver.saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));

        when(mMockTestListener.getSummary()).thenReturn(null);

        IBuildInfo build1 = new BuildInfo();
        when(mProvider1.getBuild()).thenReturn(build1);
        // Second build is not found
        when(mProvider2.getBuild())
                .thenThrow(
                        new BuildRetrievalError(
                                "fail", InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR));
        // The downloaded build is cleaned

        // A second build from the BuildRetrievalError is generated but still cleaned.

        try {
            mInvocation.invoke(
                    mContext, mMockConfig, mMockRescheduler, new ITestInvocationListener[] {});
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            // Expected
        }
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockConfig, times(1)).getTestInvocationListeners();
        verify(mMockConfig, times(3)).getConfigurationDescription();
        verify(mMockConfig).resolveDynamicOptions(Mockito.any());
        verify(mMockConfig).cleanConfigurationData();
        verify(mMockTestListener).invocationStarted(mContext);
        verify(mMockLogSaver).invocationStarted(mContext);
        verify(mMockConfig)
                .dumpXml(Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.eq(false));
        verify(mMockTestListener, times(3)).testLog(Mockito.any(), Mockito.any(), Mockito.any());
        verify(mMockTestListener).invocationFailed(Mockito.<FailureDescription>any());
        verify(mMockTestListener).invocationEnded(Mockito.anyLong());
        verify(mMockLogSaver).invocationEnded(Mockito.anyLong());
        verify(mProvider1).cleanUp(build1);
        verify(mProvider2).cleanUp(Mockito.any());
    }

    /**
     * Test when the provider clean up throws an exception, we still continue to clean up the rest
     * to ensure nothing is left afterward.
     */
    @Test
    public void testRunBuildProvider_cleanUpThrow() throws Throwable {
        makeTwoDeviceContext();

        List<ITestInvocationListener> configListener = new ArrayList<>();
        configListener.add(mMockTestListener);
        when(mMockConfig.getTestInvocationListeners()).thenReturn(configListener);
        when(mMockConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mMockConfig.getLogOutput()).thenReturn(mMockLogger);
        when(mMockConfig.getConfigurationDescription()).thenReturn(mConfigDesc);

        when(mMockLogger.getLog()).thenReturn(new ByteArrayInputStreamSource("fake".getBytes()));

        when(mMockConfig.getCommandLine()).thenReturn("empty");
        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());
        when(mMockConfig.getTests()).thenReturn(new ArrayList<>());

        when(mMockTestListener.getSummary()).thenReturn(null);

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.HARNESS_CONFIG));

        when(mMockLogSaver.saveLogData(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));
        when(mMockLogSaver.saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(new LogFile("", "", LogDataType.TEXT));

        when(mMockTestListener.getSummary()).thenReturn(null);

        IBuildInfo build1 = new BuildInfo();
        when(mProvider1.getBuild()).thenReturn(build1);
        // Second build is not found
        when(mProvider2.getBuild())
                .thenThrow(
                        new BuildRetrievalError(
                                "fail", InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR));
        // The downloaded build is cleaned
        doThrow(new RuntimeException("I failed to clean!")).when(mProvider1).cleanUp(build1);
        // A second build from the BuildRetrievalError is generated but still cleaned, even if the
        // first clean up failed.

        try {
            mInvocation.invoke(
                    mContext, mMockConfig, mMockRescheduler, new ITestInvocationListener[] {});
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            // Expected
        }
        verify(mMockLogger, times(3)).init();
        verify(mMockLogger, times(2)).closeLog();
        verify(mMockLogRegistry, times(3)).registerLogger(mMockLogger);
        verify(mMockLogRegistry, times(2)).unregisterLogger();
        verify(mMockConfig, times(1)).getTestInvocationListeners();
        verify(mMockConfig, times(3)).getConfigurationDescription();
        verify(mMockConfig).resolveDynamicOptions(Mockito.any());
        verify(mMockConfig).cleanConfigurationData();
        verify(mMockTestListener).invocationStarted(mContext);
        verify(mMockLogSaver).invocationStarted(mContext);
        verify(mMockConfig)
                .dumpXml(Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.eq(false));
        verify(mMockTestListener, times(3)).testLog(Mockito.any(), Mockito.any(), Mockito.any());
        verify(mMockTestListener).invocationFailed(Mockito.<FailureDescription>any());
        verify(mMockTestListener).invocationEnded(Mockito.anyLong());
        verify(mMockLogSaver).invocationEnded(Mockito.anyLong());
        verify(mProvider2).cleanUp(Mockito.any());
    }
}
