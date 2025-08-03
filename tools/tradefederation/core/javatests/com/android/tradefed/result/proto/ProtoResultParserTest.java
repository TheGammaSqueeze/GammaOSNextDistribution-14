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
package com.android.tradefed.result.proto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ActionInProgress;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.truth.Truth;
import com.google.protobuf.Any;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;

/** Unit tests for {@link ProtoResultParser}. */
@RunWith(JUnit4.class)
public class ProtoResultParserTest {

    private static final String CONTEXT_TEST_KEY = "context-late-attribute";
    private static final String TEST_KEY = "late-attribute";

    private ProtoResultParser mParser;
    @Mock ILogSaverListener mMockListener;
    private TestProtoParser mTestParser;
    private FinalTestProtoParser mFinalTestParser;
    private IInvocationContext mInvocationContext;
    private IInvocationContext mMainInvocationContext;

    private class TestProtoParser extends ProtoResultReporter {

        @Override
        public void processStartInvocation(
                TestRecord invocationStartRecord, IInvocationContext context) {
            mParser.processNewProto(invocationStartRecord);
            // After context was proto-ified once add an attribute
            context.getBuildInfos().get(0).addBuildAttribute(TEST_KEY, "build_value");
            // Test a context attribute
            context.addInvocationAttribute(CONTEXT_TEST_KEY, "context_value");
        }

        @Override
        public void processFinalProto(TestRecord finalRecord) {
            mParser.processNewProto(finalRecord);
        }

        @Override
        public void processTestModuleStarted(TestRecord moduleStartRecord) {
            mParser.processNewProto(moduleStartRecord);
        }

        @Override
        public void processTestModuleEnd(TestRecord moduleRecord) {
            mParser.processNewProto(moduleRecord);
        }

        @Override
        public void processTestRunStarted(TestRecord runStartedRecord) {
            mParser.processNewProto(runStartedRecord);
        }

        @Override
        public void processTestRunEnded(TestRecord runRecord, boolean moduleInProgress) {
            mParser.processNewProto(runRecord);
        }

        @Override
        public void processTestCaseStarted(TestRecord testCaseStartedRecord) {
            mParser.processNewProto(testCaseStartedRecord);
        }

        @Override
        public void processTestCaseEnded(TestRecord testCaseRecord) {
            mParser.processNewProto(testCaseRecord);
        }
    }

    private class FinalTestProtoParser extends ProtoResultReporter {
        @Override
        public void processFinalProto(TestRecord finalRecord) {
            mParser.processFinalizedProto(finalRecord);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mMainInvocationContext = new InvocationContext();
        mParser = new ProtoResultParser(mMockListener, mMainInvocationContext, true);
        mTestParser = new TestProtoParser();
        mFinalTestParser = new FinalTestProtoParser();
        mInvocationContext = new InvocationContext();
        mInvocationContext.setConfigurationDescriptor(new ConfigurationDescriptor());
        BuildInfo info = new BuildInfo();
        mInvocationContext.addAllocatedDevice(
                ConfigurationDef.DEFAULT_DEVICE_NAME, mock(ITestDevice.class));
        mMainInvocationContext.addAllocatedDevice(
                ConfigurationDef.DEFAULT_DEVICE_NAME, mock(ITestDevice.class));
        mInvocationContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, info);
        mMainInvocationContext.addDeviceBuildInfo(
                ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());
    }

    @Test
    public void testEvents() {
        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        HashMap<String, Metric> metrics = new HashMap<String, Metric>();
        metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
        LogFile logFile = new LogFile("path", "url", false, LogDataType.TEXT, 5);
        Throwable failure = new RuntimeException("invoc failure");
        ArgumentCaptor<LogFile> capture = ArgumentCaptor.forClass(LogFile.class);

        // Verify Mocks

        // Invocation failure is replayed
        ArgumentCaptor<FailureDescription> captureInvocFailure =
                ArgumentCaptor.forClass(FailureDescription.class);

        // Invocation start
        mInvocationContext.getBuildInfos().get(0).addBuildAttribute("early_key", "build_value");
        mInvocationContext.addInvocationAttribute("early_context_key", "context_value");
        mTestParser.invocationStarted(mInvocationContext);
        mInvocationContext.getBuildInfos().get(0).addBuildAttribute("after_start", "build_value");
        // Check early context
        IInvocationContext context = mMainInvocationContext;
        assertFalse(context.getBuildInfos().get(0).getBuildAttributes().containsKey(TEST_KEY));
        assertFalse(context.getAttributes().containsKey(CONTEXT_TEST_KEY));
        assertEquals(
                "build_value",
                context.getBuildInfos().get(0).getBuildAttributes().get("early_key"));
        assertEquals("context_value", context.getAttributes().get("early_context_key").get(0));

        assertFalse(context.getBuildInfos().get(0).getBuildAttributes().containsKey("after_start"));
        // Run modules
        mTestParser.testModuleStarted(createModuleContext("arm64 module1"));
        assertEquals(
                "build_value",
                context.getBuildInfos().get(0).getBuildAttributes().get("after_start"));
        mTestParser.testRunStarted("run1", 2);

        mTestParser.testStarted(test1, 5L);
        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mTestParser.testStarted(test2, 11L);
        mTestParser.testFailed(test2, "I failed");
        // test log
        mTestParser.logAssociation("log1", logFile);

        mTestParser.testEnded(test2, 60L, metrics);

        mTestParser.invocationFailed(failure);
        // run log
        mTestParser.logAssociation(
                "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());

        mTestParser.logAssociation(
                "module_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mTestParser.testModuleEnded();

        mTestParser.logAssociation(
                "invocation_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        // Invocation ends
        mTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener).testModuleStarted(Mockito.any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testStarted(test2, 11L);
        inOrder.verify(mMockListener).testFailed(test2, FailureDescription.create("I failed"));
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-log1"), capture.capture());
        inOrder.verify(mMockListener).testEnded(test2, 60L, metrics);
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-run_log1"), Mockito.any());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-module_log1"), Mockito.any());
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-invocation_log1"), Mockito.any());
        inOrder.verify(mMockListener).invocationFailed(captureInvocFailure.capture());
        inOrder.verify(mMockListener).invocationEnded(500L);

        // Check capture
        LogFile capturedFile = capture.getValue();
        assertEquals(logFile.getPath(), capturedFile.getPath());
        assertEquals(logFile.getUrl(), capturedFile.getUrl());
        assertEquals(logFile.getType(), capturedFile.getType());
        assertEquals(logFile.getSize(), capturedFile.getSize());

        FailureDescription invocFailureCaptured = captureInvocFailure.getValue();
        assertTrue(invocFailureCaptured.getCause() instanceof RuntimeException);

        // Check Context at the end
        assertEquals(
                "build_value", context.getBuildInfos().get(0).getBuildAttributes().get(TEST_KEY));
        assertEquals("context_value", context.getAttributes().get(CONTEXT_TEST_KEY).get(0));
        assertEquals(
                "build_value",
                context.getBuildInfos().get(0).getBuildAttributes().get("early_key"));
        assertEquals("context_value", context.getAttributes().get("early_context_key").get(0));
        assertEquals(
                "build_value",
                context.getBuildInfos().get(0).getBuildAttributes().get("after_start"));
    }

    @Test
    public void testEvents_invocationFailure() {
        Throwable failure = new RuntimeException("invoc failure");
        FailureDescription invocFailure =
                FailureDescription.create(failure.getMessage())
                        .setCause(failure)
                        .setActionInProgress(ActionInProgress.FETCHING_ARTIFACTS)
                        .setErrorIdentifier(InfraErrorIdentifier.UNDETERMINED)
                        .setOrigin("origin");

        // Verify Mocks

        // Invocation failure is replayed
        ArgumentCaptor<FailureDescription> captureInvocFailure =
                ArgumentCaptor.forClass(FailureDescription.class);

        // Invocation start
        mInvocationContext.getBuildInfos().get(0).addBuildAttribute("early_key", "build_value");
        mInvocationContext.addInvocationAttribute("early_context_key", "context_value");
        mTestParser.invocationStarted(mInvocationContext);
        mTestParser.invocationFailed(invocFailure);
        // Invocation ends
        mTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener).invocationFailed(captureInvocFailure.capture());
        inOrder.verify(mMockListener).invocationEnded(500L);

        // Check capture
        FailureDescription invocFailureCaptured = captureInvocFailure.getValue();
        assertTrue(invocFailureCaptured.getCause() instanceof RuntimeException);
        assertEquals(
                ActionInProgress.FETCHING_ARTIFACTS, invocFailureCaptured.getActionInProgress());
        assertEquals(
                InfraErrorIdentifier.UNDETERMINED.name(),
                invocFailureCaptured.getErrorIdentifier().name());
        assertEquals(
                InfraErrorIdentifier.UNDETERMINED.code(),
                invocFailureCaptured.getErrorIdentifier().code());
        assertEquals("origin", invocFailureCaptured.getOrigin());
    }

    @Test
    public void testEvents_invocationFailure_errorNotSet() {
        Throwable failure = new RuntimeException("invoc failure");
        // Error with not ErrorIdentifier set.
        FailureDescription invocFailure =
                FailureDescription.create(failure.getMessage())
                        .setCause(failure)
                        .setActionInProgress(ActionInProgress.FETCHING_ARTIFACTS)
                        .setOrigin("origin");

        // Verify Mocks

        // Invocation failure is replayed
        ArgumentCaptor<FailureDescription> captureInvocFailure =
                ArgumentCaptor.forClass(FailureDescription.class);

        // Invocation start
        mInvocationContext.getBuildInfos().get(0).addBuildAttribute("early_key", "build_value");
        mInvocationContext.addInvocationAttribute("early_context_key", "context_value");
        mTestParser.invocationStarted(mInvocationContext);
        mTestParser.invocationFailed(invocFailure);
        // Invocation ends
        mTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener).invocationFailed(captureInvocFailure.capture());
        inOrder.verify(mMockListener).invocationEnded(500L);

        // Check capture
        FailureDescription invocFailureCaptured = captureInvocFailure.getValue();
        assertTrue(invocFailureCaptured.getCause() instanceof RuntimeException);
        assertEquals(
                ActionInProgress.FETCHING_ARTIFACTS, invocFailureCaptured.getActionInProgress());
        assertEquals("origin", invocFailureCaptured.getOrigin());
        assertNull(invocFailureCaptured.getErrorIdentifier());
    }

    /** Test that a run failure occurring inside a test case pair is handled properly. */
    @Test
    public void testRunFail_interleavedWithTest() {
        TestDescription test1 = new TestDescription("class1", "test1");

        // Verify Mocks

        mTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mTestParser.testRunStarted("run1", 2);

        mTestParser.testStarted(test1, 5L);
        // test run failed inside a test
        mTestParser.testRunFailed("run failure");

        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());
        // Invocation ends
        mTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunFailed(FailureDescription.create("run failure"));
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).invocationEnded(500L);
    }

    @Test
    public void testEvents_finaleProto() {
        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        HashMap<String, Metric> metrics = new HashMap<String, Metric>();
        metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
        LogFile logFile = new LogFile("path", "url", false, LogDataType.TEXT, 5);
        LogFile logModuleFile = new LogFile("path", "url", false, LogDataType.TEXT, 5);

        // Verify Mocks

        // Invocation start
        mFinalTestParser.invocationStarted(mInvocationContext);
        // Run modules
        IInvocationContext moduleContext = createModuleContext("arm64 module1");
        mFinalTestParser.testModuleStarted(moduleContext);
        // test log at module level
        mFinalTestParser.logAssociation("log-module", logModuleFile);
        mFinalTestParser.testRunStarted("run1", 2);

        mFinalTestParser.testStarted(test1, 5L);
        mFinalTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mFinalTestParser.testStarted(test2, 11L);
        mFinalTestParser.testFailed(test2, "I failed");
        // test log
        mFinalTestParser.logAssociation("log1", logFile);

        mFinalTestParser.testEnded(test2, 60L, metrics);
        // run log
        mFinalTestParser.logAssociation(
                "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mFinalTestParser.testRunEnded(50L, new HashMap<String, Metric>());

        moduleContext.addInvocationAttribute(ITestSuite.MODULE_END_TIME, "endTime");
        mFinalTestParser.testModuleEnded();

        // Invocation ends
        mFinalTestParser.invocationEnded(500L);
        ArgumentCaptor<IInvocationContext> startCaptor =
                ArgumentCaptor.forClass(IInvocationContext.class);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener).testModuleStarted(startCaptor.capture());

        IInvocationContext captureModuleContext = startCaptor.getValue();
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testStarted(test2, 11L);
        inOrder.verify(mMockListener).testFailed(test2, FailureDescription.create("I failed"));
        inOrder.verify(mMockListener).logAssociation(Mockito.eq("subprocess-log1"), Mockito.any());
        inOrder.verify(mMockListener).testEnded(test2, 60L, metrics);
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-run_log1"), Mockito.any());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-log-module"), Mockito.any());
        inOrder.verify(mMockListener).testModuleEnded();
        Truth.assertThat(captureModuleContext.getAttribute(ITestSuite.MODULE_START_TIME))
                .isEqualTo("startTime");
        Truth.assertThat(captureModuleContext.getAttribute(ITestSuite.MODULE_END_TIME))
                .isEqualTo("endTime");
        inOrder.verify(mMockListener).invocationEnded(500L);
    }

    /** Test that a run failure occurring inside a test case pair is handled properly. */
    @Test
    public void testRunFail_interleavedWithTest_finalProto() {
        TestDescription test1 = new TestDescription("class1", "test1");

        // Verify Mocks

        mFinalTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mFinalTestParser.testRunStarted("run1", 2);

        mFinalTestParser.testStarted(test1, 5L);
        // test run failed inside a test
        mFinalTestParser.testRunFailed("run failure");

        mFinalTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mFinalTestParser.testRunEnded(50L, new HashMap<String, Metric>());
        // Invocation ends
        mFinalTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunFailed(FailureDescription.create("run failure"));
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).invocationEnded(500L);
    }

    /** Ensure that the FailureDescription is properly populated. */
    @Test
    public void testRunFail_failureDescription() {
        TestDescription test1 = new TestDescription("class1", "test1");

        // Verify Mocks

        mFinalTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mFinalTestParser.testRunStarted("run1", 2);

        mFinalTestParser.testStarted(test1, 5L);
        // test run failed inside a test
        mFinalTestParser.testRunFailed(
                FailureDescription.create("run failure")
                        .setFailureStatus(FailureStatus.INFRA_FAILURE)
                        .setActionInProgress(ActionInProgress.TEST)
                        .setDebugHelpMessage("help message"));

        mFinalTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mFinalTestParser.testRunEnded(50L, new HashMap<String, Metric>());
        // Invocation ends
        mFinalTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener)
                .testRunFailed(
                        FailureDescription.create("run failure")
                                .setFailureStatus(FailureStatus.INFRA_FAILURE)
                                .setActionInProgress(ActionInProgress.TEST)
                                .setDebugHelpMessage("help message"));
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).invocationEnded(500L);
    }

    /**
     * Ensure the testRunStart specified with an attempt number is carried through our proto test
     * record.
     */
    @Test
    public void testRun_withAttempts() {
        TestDescription test1 = new TestDescription("class1", "test1");

        // Verify Mocks

        mTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mTestParser.testRunStarted("run1", 2);
        mTestParser.testStarted(test1, 5L);
        // test run failed inside a test
        mTestParser.testRunFailed("run failure");
        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());
        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());

        mTestParser.testRunStarted("run1", 1, 1);
        mTestParser.testStarted(test1, 5L);
        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());
        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());
        // Invocation ends
        mTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunFailed(FailureDescription.create("run failure"));
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).invocationEnded(500L);
    }

    /**
     * Test a subprocess situation where invocation start/end is not called again in the parent
     * process.
     */
    @Test
    public void testEvents_subprocess() throws Exception {
        // In subprocess, we do not report invocationStart/end again
        mParser = new ProtoResultParser(mMockListener, mMainInvocationContext, false);

        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        HashMap<String, Metric> metrics = new HashMap<String, Metric>();
        metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
        LogFile logFile = new LogFile("path", "url", false, LogDataType.TEXT, 5);
        ArgumentCaptor<LogFile> capture = ArgumentCaptor.forClass(LogFile.class);

        // Verify Mocks - No Invocation Start and End

        // Invocation start
        mTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mTestParser.testModuleStarted(createModuleContext("arm64 module1"));
        mTestParser.testRunStarted("run1", 2);

        mTestParser.testStarted(test1, 5L);
        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mTestParser.testStarted(test2, 11L);
        mTestParser.testFailed(test2, "I failed");
        // test log
        mTestParser.logAssociation("log1", logFile);

        mTestParser.testEnded(test2, 60L, metrics);
        // run log
        mTestParser.logAssociation(
                "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());

        mTestParser.logAssociation(
                "module_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mTestParser.testModuleEnded();

        mTestParser.logAssociation(
                "invocation_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        File tmpLogFile = FileUtil.createTempFile("host_log", ".txt");
        File tmpZipLogFile = FileUtil.createTempFile("zip_host_log", ".zip");
        try {
            mTestParser.logAssociation(
                    "host_log",
                    new LogFile(tmpLogFile.getAbsolutePath(), "", false, LogDataType.TEXT, 5));
            mTestParser.logAssociation(
                    "host_log_zip",
                    new LogFile(tmpZipLogFile.getAbsolutePath(), "", false, LogDataType.TEXT, 5));
            // Invocation ends
            mTestParser.invocationEnded(500L);
            InOrder inOrder = Mockito.inOrder(mMockListener);
            inOrder.verify(mMockListener).testModuleStarted(Mockito.any());
            inOrder.verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
            inOrder.verify(mMockListener).testStarted(test1, 5L);
            inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
            inOrder.verify(mMockListener).testStarted(test2, 11L);
            inOrder.verify(mMockListener).testFailed(test2, FailureDescription.create("I failed"));
            inOrder.verify(mMockListener)
                    .logAssociation(Mockito.eq("subprocess-log1"), capture.capture());
            inOrder.verify(mMockListener).testEnded(test2, 60L, metrics);
            inOrder.verify(mMockListener)
                    .logAssociation(Mockito.eq("subprocess-run_log1"), Mockito.any());
            inOrder.verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
            inOrder.verify(mMockListener)
                    .logAssociation(Mockito.eq("subprocess-module_log1"), Mockito.any());
            inOrder.verify(mMockListener).testModuleEnded();
            inOrder.verify(mMockListener)
                    .logAssociation(Mockito.eq("subprocess-invocation_log1"), Mockito.any());
            inOrder.verify(mMockListener)
                    .testLog(
                            Mockito.eq("subprocess-host_log"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
            inOrder.verify(mMockListener)
                    .testLog(
                            Mockito.eq("subprocess-host_log_zip"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());

            // Check capture
            LogFile capturedFile = capture.getValue();
            assertEquals(logFile.getPath(), capturedFile.getPath());
            assertEquals(logFile.getUrl(), capturedFile.getUrl());
            assertEquals(logFile.getType(), capturedFile.getType());
            assertEquals(logFile.getSize(), capturedFile.getSize());

            // Check Context
            IInvocationContext context = mMainInvocationContext;
            assertEquals(
                    "build_value",
                    context.getBuildInfos().get(0).getBuildAttributes().get(TEST_KEY));
            assertEquals("context_value", context.getAttributes().get(CONTEXT_TEST_KEY).get(0));
        } finally {
            FileUtil.deleteFile(tmpLogFile);
            FileUtil.deleteFile(tmpZipLogFile);
        }
    }

    /** Test the parsing when a missing testModuleEnded occurs. */
    @Test
    public void testEvents_finaleProto_partialEvents() {
        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        HashMap<String, Metric> metrics = new HashMap<String, Metric>();
        metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
        LogFile logFile = new LogFile("path", "url", false, LogDataType.TEXT, 5);
        LogFile logModuleFile = new LogFile("path", "url", false, LogDataType.TEXT, 5);

        // Verify Mocks

        // We complete the missing event

        // Invocation start
        mFinalTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mFinalTestParser.testModuleStarted(createModuleContext("arm64 module1"));
        // test log at module level
        mFinalTestParser.logAssociation("log-module", logModuleFile);
        mFinalTestParser.testRunStarted("run1", 2);

        mFinalTestParser.testStarted(test1, 5L);
        mFinalTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mFinalTestParser.testStarted(test2, 11L);
        mFinalTestParser.testFailed(test2, "I failed");
        // test log
        mFinalTestParser.logAssociation("log1", logFile);

        mFinalTestParser.testEnded(test2, 60L, metrics);
        // run log
        mFinalTestParser.logAssociation(
                "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mFinalTestParser.testRunEnded(50L, new HashMap<String, Metric>());

        // Missing testModuleEnded due to a timeout for example
        // mFinalTestParser.testModuleEnded();

        // Invocation ends
        mFinalTestParser.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(Mockito.any());
        inOrder.verify(mMockListener).testModuleStarted(Mockito.any());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(test1, 5L);
        inOrder.verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testStarted(test2, 11L);
        inOrder.verify(mMockListener).testFailed(test2, FailureDescription.create("I failed"));
        inOrder.verify(mMockListener).logAssociation(Mockito.eq("subprocess-log1"), Mockito.any());
        inOrder.verify(mMockListener).testEnded(test2, 60L, metrics);
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-run_log1"), Mockito.any());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .logAssociation(Mockito.eq("subprocess-log-module"), Mockito.any());
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener).invocationEnded(500L);
    }

    @Test
    public void testIncompleteModule() throws Exception {
        ArgumentCaptor<FailureDescription> capture =
                ArgumentCaptor.forClass(FailureDescription.class);
        mTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mTestParser.testModuleStarted(createModuleContext("arm64 module1"));
        // It stops unexpectedly
        mParser.completeModuleEvents();

        verify(mMockListener).invocationStarted(Mockito.any());
        verify(mMockListener).testModuleStarted(Mockito.any());
        verify(mMockListener).testRunStarted(Mockito.eq("arm64 module1"), Mockito.eq(0));
        verify(mMockListener).testRunFailed(capture.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testModuleEnded();
        assertEquals(
                "Module was interrupted after starting, results are incomplete.",
                capture.getValue().getErrorMessage());
    }

    @Test
    public void testContextMergingEnabled() throws Exception {
        InvocationContext moduleContext = new InvocationContext();
        moduleContext.addInvocationAttribute("testkey", "testvalue");
        TestRecord.Builder record1 =
                TestRecord.newBuilder().setDescription(Any.pack(moduleContext.toProto()));

        ProtoResultParser parser =
                new ProtoResultParser(
                        mMockListener, mInvocationContext, false /* reportInvocation */);

        // context from mainContext should be transferred to mInvocationContext during call
        parser.processFinalizedProto(record1.build());

        assertEquals("testvalue", mInvocationContext.getAttribute("testkey"));
    }

    @Test
    public void testContextMergingDisabled() throws Exception {
        InvocationContext moduleContext = new InvocationContext();
        moduleContext.addInvocationAttribute("testkey", "testvalue");
        TestRecord.Builder record1 =
                TestRecord.newBuilder().setDescription(Any.pack(moduleContext.toProto()));
        ProtoResultParser parser =
                new ProtoResultParser(
                        mMockListener, mInvocationContext, false /* reportInvocation */);

        // context from mainContext should be transferred to mInvocationContext during call
        parser.setMergeInvocationContext(false);
        parser.processFinalizedProto(record1.build());

        // returns empty string for missing attributes
        assertEquals("", mInvocationContext.getAttribute("testkey"));
    }

    @Test
    public void testProcessFinalizedProtoAllowsInvocationAttributeUpdates() {
        // Calling processFinalizedProto has a side-effect of unlocking the InvocationContext
        // for invocation attribute updates.
        // If we disable merging, we also disable this side effect and the InvocationContext
        // attributes remain unmodifiable
        InvocationContext moduleContext = new InvocationContext();
        moduleContext.addInvocationAttribute("testkey", "testvalue");
        TestRecord.Builder record1 =
                TestRecord.newBuilder().setDescription(Any.pack(moduleContext.toProto()));
        ProtoResultParser parser =
                new ProtoResultParser(
                        mMockListener, mInvocationContext, false /* reportInvocation */);

        ((InvocationContext) mInvocationContext).lockAttributes();
        // allow merges, i.e. the default value.
        parser.setMergeInvocationContext(true);
        parser.processFinalizedProto(record1.build());
        mInvocationContext.addInvocationAttribute("merge-key", "car");

        // Value should have been set.
        assertEquals("car", mInvocationContext.getAttribute("merge-key"));
    }

    @Test
    public void test_when_processMergingDisabled_the_context_remains_locked() {
        // Calling processFinalizedProto has a side-effect of unlocking the InvocationContext
        // for invocation attribute updates.
        // If we disable merging, we also disable this side effect and the InvocationContext
        // attributes remain unmodifiable
        InvocationContext moduleContext = new InvocationContext();
        moduleContext.addInvocationAttribute("testkey", "testvalue");
        TestRecord.Builder record1 =
                TestRecord.newBuilder().setDescription(Any.pack(moduleContext.toProto()));
        ProtoResultParser parser =
                new ProtoResultParser(
                        mMockListener, mInvocationContext, false /* reportInvocation */);

        ((InvocationContext) mInvocationContext).lockAttributes();
        parser.setMergeInvocationContext(false);
        parser.processFinalizedProto(record1.build());
        assertThrows(
                IllegalStateException.class,
                () -> mInvocationContext.addInvocationAttribute("key", "car"));
    }

    /** Helper to create a module context. */
    private IInvocationContext createModuleContext(String moduleId) {
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_ID, moduleId);
        context.setConfigurationDescriptor(new ConfigurationDescriptor());
        context.addDeviceBuildInfo(
                ConfigurationDef.DEFAULT_DEVICE_NAME, mInvocationContext.getBuildInfos().get(0));
        context.addInvocationAttribute(ITestSuite.MODULE_START_TIME, "startTime");
        return context;
    }
}
