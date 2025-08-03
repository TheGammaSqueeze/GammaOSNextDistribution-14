/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.retry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.MultiFailureDescription;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.retry.ISupportGranularResults;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/** Unit tests for {@link ResultAggregator}. */
@RunWith(JUnit4.class)
public class ResultAggregatorTest {

    private TestableResultAggregator mAggregator;
    @Mock ILogSaverListener mAggListener;
    @Mock ITestDetailedReceiver mDetailedListener;
    @Mock ILogSaver mLogger;
    private IInvocationContext mInvocationContext;
    private IInvocationContext mModuleContext;

    private interface ITestDetailedReceiver
            extends ISupportGranularResults, ITestInvocationListener, ILogSaverListener {}

    private class TestableResultAggregator extends ResultAggregator {

        private String mCurrentRunError = null;

        public TestableResultAggregator(
                List<ITestInvocationListener> listeners, RetryStrategy strategy) {
            super(listeners, strategy);
        }

        @Override
        String getInvocationMetricRunError() {
            return mCurrentRunError;
        }

        @Override
        void addInvocationMetricRunError(String errors) {
            mCurrentRunError = errors;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mInvocationContext = new InvocationContext();
        mInvocationContext.addDeviceBuildInfo(
                ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());
        mModuleContext = new InvocationContext();
    }

    @After
    public void tearDown() throws Exception {
        if (mAggregator != null) {
            for (File f : mAggregator.getEventsLogs()) {
                FileUtil.deleteFile(f);
            }
        }
    }

    @Test
    public void testForwarding() throws Exception {
        LogFile beforeModule = new LogFile("before-module", "url", LogDataType.TEXT);
        LogFile test1Log = new LogFile("test1", "url", LogDataType.TEXT);
        LogFile test2LogBefore = new LogFile("test2-before", "url", LogDataType.TEXT);
        LogFile test2LogAfter = new LogFile("test2-after", "url", LogDataType.TEXT);
        LogFile testRun1LogBefore = new LogFile("test-run1-before", "url", LogDataType.TEXT);
        LogFile testRun1LogAfter = new LogFile("test-run1-after", "url", LogDataType.TEXT);
        LogFile beforeEnd = new LogFile("path", "url", LogDataType.TEXT);
        LogFile betweenAttemptsLog = new LogFile("between-attempts", "url", LogDataType.TEXT);
        LogFile moduleLog = new LogFile("module-log", "url", LogDataType.TEXT);
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.logAssociation("before-module-log", beforeModule);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.logAssociation("test1-log", test1Log);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.logAssociation("test2-before-log", test2LogBefore);
        mAggregator.testFailed(test2, FailureDescription.create("I failed. retry me."));
        mAggregator.logAssociation("test2-after-log", test2LogAfter);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.logAssociation("test-run1-before-log", testRun1LogBefore);
        mAggregator.testRunFailed("run fail");
        mAggregator.logAssociation("test-run1-after-log", testRun1LogAfter);
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.logAssociation("between-attempts", betweenAttemptsLog);
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.logAssociation("module-log", moduleLog);
        mAggregator.testModuleEnded();
        mAggregator.logAssociation("before-end", beforeEnd);
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).logAssociation("before-module-log", beforeModule);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).logAssociation("test1-log", test1Log);
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).logAssociation("test2-before-log", test2LogBefore);
        inOrder.verify(mDetailedListener)
                .testFailed(test2, FailureDescription.create("I failed. retry me."));
        inOrder.verify(mDetailedListener).logAssociation("test2-after-log", test2LogAfter);
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).logAssociation("test-run1-before-log", testRun1LogBefore);
        inOrder.verify(mDetailedListener).logAssociation("test-run1-after-log", testRun1LogAfter);
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).logAssociation("between-attempts", betweenAttemptsLog);
        inOrder.verify(mDetailedListener).logAssociation("module-log", moduleLog);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).logAssociation("test1-log", test1Log);
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).logAssociation("test2-before-log", test2LogBefore);
        inOrderAgg.verify(mAggListener).logAssociation("test2-after-log", test2LogAfter);
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).logAssociation("test-run1-before-log", testRun1LogBefore);
        inOrderAgg.verify(mAggListener).logAssociation("test-run1-after-log", testRun1LogAfter);
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).logAssociation("between-attempts", betweenAttemptsLog);
        inOrderAgg.verify(mAggListener).logAssociation("module-log", moduleLog);
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).logAssociation("before-module-log", beforeModule);
        inOrderAgg.verify(mAggListener).logAssociation("before-end", beforeEnd);
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).logAssociation("before-end", beforeEnd);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertEquals("run fail", mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_emptyModule() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // Module 1
        mAggregator.testModuleStarted(mModuleContext);
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, FailureDescription.create("I failed. retry me."));
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();
        // Module 2 is empty
        mAggregator.testModuleStarted(mModuleContext);
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testFailed(test2, FailureDescription.create("I failed. retry me."));
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testFailed(test2, FailureDescription.create("I failed. retry me."));
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);
    }

    @Test
    public void testForwarding_newResult() throws Exception {
        LogFile beforeModule = new LogFile("before-module", "url", LogDataType.TEXT);
        LogFile test1Log = new LogFile("test1", "url", LogDataType.TEXT);
        LogFile test2LogBefore = new LogFile("test2-before", "url", LogDataType.TEXT);
        LogFile test2LogAfter = new LogFile("test2-after", "url", LogDataType.TEXT);
        LogFile testRun1LogBefore = new LogFile("test-run1-before", "url", LogDataType.TEXT);
        LogFile testRun1LogAfter = new LogFile("test-run1-after", "url", LogDataType.TEXT);
        LogFile beforeEnd = new LogFile("path", "url", LogDataType.TEXT);
        LogFile betweenAttemptsLog = new LogFile("between-attempts", "url", LogDataType.TEXT);
        LogFile moduleLog = new LogFile("module-log", "url", LogDataType.TEXT);
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setUpdatedReporting(true);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.logAssociation("before-module-log", beforeModule);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.logAssociation("test1-log", test1Log);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.logAssociation("test2-before-log", test2LogBefore);
        mAggregator.testFailed(test2, FailureDescription.create("I failed. retry me."));
        mAggregator.logAssociation("test2-after-log", test2LogAfter);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.logAssociation("test-run1-before-log", testRun1LogBefore);
        mAggregator.testRunFailed("run fail");
        mAggregator.logAssociation("test-run1-after-log", testRun1LogAfter);
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.logAssociation("between-attempts", betweenAttemptsLog);
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.logAssociation("module-log", moduleLog);
        mAggregator.testModuleEnded();
        mAggregator.logAssociation("before-end", beforeEnd);
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).logAssociation("before-module-log", beforeModule);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).logAssociation("test1-log", test1Log);
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).logAssociation("test2-before-log", test2LogBefore);
        inOrder.verify(mDetailedListener)
                .testFailed(test2, FailureDescription.create("I failed. retry me."));
        inOrder.verify(mDetailedListener).logAssociation("test2-after-log", test2LogAfter);
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).logAssociation("test-run1-before-log", testRun1LogBefore);
        inOrder.verify(mDetailedListener).testRunFailed("run fail");
        inOrder.verify(mDetailedListener).logAssociation("test-run1-after-log", testRun1LogAfter);
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).logAssociation("between-attempts", betweenAttemptsLog);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).logAssociation("module-log", moduleLog);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).logAssociation("test1-log", test1Log);
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).logAssociation("test2-before-log", test2LogBefore);
        inOrderAgg.verify(mAggListener).logAssociation("test2-after-log", test2LogAfter);
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).logAssociation("test-run1-before-log", testRun1LogBefore);
        inOrderAgg.verify(mAggListener).logAssociation("test-run1-after-log", testRun1LogAfter);
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).logAssociation("between-attempts", betweenAttemptsLog);
        inOrderAgg.verify(mAggListener).logAssociation("module-log", moduleLog);
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).logAssociation("before-module-log", beforeModule);
        inOrderAgg.verify(mAggListener).logAssociation("before-end", beforeEnd);
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).logAssociation("before-end", beforeEnd);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_assumptionFailure() throws Exception {
        mDetailedListener = mock(ITestDetailedReceiver.class);
        LogFile test1Log = new LogFile("test1", "url", LogDataType.TEXT);
        LogFile test2LogBefore = new LogFile("test2-before", "url", LogDataType.TEXT);
        LogFile test2LogAfter = new LogFile("test2-after", "url", LogDataType.TEXT);
        LogFile testRun1LogBefore = new LogFile("test-run1-before", "url", LogDataType.TEXT);
        LogFile testRun1LogAfter = new LogFile("test-run1-after", "url", LogDataType.TEXT);
        LogFile beforeEnd = new LogFile("path", "url", LogDataType.TEXT);
        LogFile betweenAttemptsLog = new LogFile("between-attempts", "url", LogDataType.TEXT);
        LogFile moduleLog = new LogFile("module-log", "url", LogDataType.TEXT);
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.logAssociation("test1-log", test1Log);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.logAssociation("test2-before-log", test2LogBefore);
        mAggregator.testFailed(test2, FailureDescription.create("I failed. retry me."));
        mAggregator.logAssociation("test2-after-log", test2LogAfter);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.logAssociation("test-run1-before-log", testRun1LogBefore);
        mAggregator.testRunFailed("run fail");
        mAggregator.logAssociation("test-run1-after-log", testRun1LogAfter);
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.logAssociation("between-attempts", betweenAttemptsLog);
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testAssumptionFailure(test2, FailureDescription.create("Assump failure"));
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.logAssociation("module-log", moduleLog);
        mAggregator.testModuleEnded();
        mAggregator.logAssociation("before-end", beforeEnd);
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).logAssociation("test1-log", test1Log);
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).logAssociation("test2-before-log", test2LogBefore);
        inOrder.verify(mDetailedListener)
                .testFailed(test2, FailureDescription.create("I failed. retry me."));
        inOrder.verify(mDetailedListener).logAssociation("test2-after-log", test2LogAfter);
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).logAssociation("test-run1-before-log", testRun1LogBefore);
        inOrder.verify(mDetailedListener).logAssociation("test-run1-after-log", testRun1LogAfter);
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testAssumptionFailure(
                        Mockito.eq(test2), Mockito.eq(FailureDescription.create("Assump failure")));
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).logAssociation("between-attempts", betweenAttemptsLog);
        inOrder.verify(mDetailedListener).logAssociation("module-log", moduleLog);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).logAssociation("test1-log", test1Log);
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testAssumptionFailure(Mockito.eq(test2), (FailureDescription) Mockito.any());
        inOrderAgg.verify(mAggListener).logAssociation("test2-before-log", test2LogBefore);
        inOrderAgg.verify(mAggListener).logAssociation("test2-after-log", test2LogAfter);
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).logAssociation("test-run1-before-log", testRun1LogBefore);
        inOrderAgg.verify(mAggListener).logAssociation("test-run1-after-log", testRun1LogAfter);
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).logAssociation("between-attempts", betweenAttemptsLog);
        inOrderAgg.verify(mAggListener).logAssociation("module-log", moduleLog);
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).logAssociation("before-end", beforeEnd);
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).logAssociation("before-end", beforeEnd);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertEquals("run fail", mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_runFailure() throws Exception {
        mDetailedListener = mock(ITestDetailedReceiver.class);
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        MultiFailureDescription aggFailure =
                new MultiFailureDescription(
                        FailureDescription.create("run fail"),
                        FailureDescription.create("run fail 2"));
        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("run fail");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("run fail 2");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testModuleEnded();
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunFailed(aggFailure);
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunFailed(aggFailure);
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_runFailure_noRerun() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("run fail");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener)
                .testRunFailed(Mockito.eq(FailureDescription.create("run fail")));
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testFailed(test2, FailureDescription.create("I failed. retry me."));
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg
                .verify(mAggListener)
                .testRunFailed(Mockito.eq(FailureDescription.create("run fail")));
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_runFailure_aggregation() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.testModuleStarted(mModuleContext);
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testRunFailed(FailureDescription.create("run fail"));
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testRunFailed(Mockito.eq(FailureDescription.create("run fail")));
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testFailed(
                        test2,
                        new MultiFailureDescription(
                                FailureDescription.create("I failed. retry me."),
                                FailureDescription.create("I failed. retry me.")));
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testRunFailed(Mockito.eq(FailureDescription.create("run fail")));
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_noModules() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        LogFile afterRunLog = new LogFile("after-run", "url", LogDataType.TEXT);

        mDetailedListener = mock(ITestDetailedReceiver.class);
        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.logAssociation("after-run", afterRunLog);
        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).logAssociation("after-run", afterRunLog);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).logAssociation("after-run", afterRunLog);
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertEquals("I failed", mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_singleRun_noModules_runFailures() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        MultiFailureDescription aggFailure =
                new MultiFailureDescription(
                        FailureDescription.create("I failed"),
                        FailureDescription.create("I failed 2"));
        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed 2");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunFailed(aggFailure);
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunFailed(aggFailure);
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_noModules_runFailures() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        MultiFailureDescription aggFailure =
                new MultiFailureDescription(
                        FailureDescription.create("I failed"),
                        FailureDescription.create("I failed 2"));
        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed 2");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // run 2
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunFailed(Mockito.eq(aggFailure));
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunFailed(Mockito.eq(aggFailure));
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    /** Test aggregation of results coming from a module first then from a simple test run. */
    @Test
    public void testForwarding_module_noModule() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        // New run that is not a module
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test1, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertEquals("I failed", mAggregator.getInvocationMetricRunError());
    }

    /** Test aggregation of results coming from a simple test run first then from a module. */
    @Test
    public void testForwarding_noModule_module() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // First run that is not a module
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        // Module start
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test1, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_noModule_module_runFailure() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        MultiFailureDescription aggFailure =
                new MultiFailureDescription(
                        FailureDescription.create("I failed"),
                        FailureDescription.create("I failed 2"));
        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // First run that is not a module
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed 2");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        // Module start
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test1, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunFailed(aggFailure);
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunFailed(aggFailure);
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    /** Test when two modules follow each others. */
    @Test
    public void testForwarding_module_module() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");
        LogFile moduleLog = new LogFile("module-log", "url", LogDataType.TEXT);

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);

        // Module 1 starts
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.logAssociation("module-log", moduleLog);
        mAggregator.testModuleEnded();

        // Module 2 starts
        mAggregator.testModuleStarted(mModuleContext);
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testFailed(test1, "I failed. retry me.");
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.testRunStarted("run2", 1, 1);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener).logAssociation("module-log", moduleLog);
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).logAssociation("module-log", moduleLog);
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test1, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }

    @Test
    public void testForwarding_module_pass_fail_fail() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);

        // Module 1 starts
        mAggregator.testModuleStarted(mModuleContext);
        // Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("failed2");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Attempt 3
        mAggregator.testRunStarted("run1", 2, 2);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("failed3");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        mAggregator.testModuleEnded();

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrderAgg.verify(mAggListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener).testModuleStarted(mModuleContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).testModuleEnded();
        inOrder.verify(mDetailedListener).testModuleEnded();
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertEquals(
                "There were 2 failures:\n  failed2\n  failed3",
                mAggregator.getInvocationMetricRunError());
    }

    /**
     * Ensure that we handle the aggregation properly when a single IRemoteTest generates several
     * testRuns and we retry them all (due to no filter support). In this case, the retry attempt
     * will not be right after the original attempt.
     */
    @Test
    public void testForwarding_noModules_runFailures_unordered() throws Exception {
        TestDescription test1 = new TestDescription("classname", "test1");
        TestDescription test2 = new TestDescription("classname", "test2");

        when(mDetailedListener.supportGranularResults()).thenReturn(true);

        // Invocation level

        when(mAggListener.getSummary()).thenReturn(null);

        when(mDetailedListener.getSummary()).thenReturn(null);

        when(mLogger.saveLogData(
                        Mockito.contains("aggregated-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);
        when(mLogger.saveLogData(
                        Mockito.contains("detailed-events"),
                        Mockito.eq(LogDataType.TF_EVENTS),
                        Mockito.any()))
                .thenReturn(null);

        mAggregator =
                new TestableResultAggregator(
                        Arrays.asList(mAggListener, mDetailedListener),
                        RetryStrategy.RETRY_ANY_FAILURE);
        mAggregator.setLogSaver(mLogger);
        mAggregator.invocationStarted(mInvocationContext);
        // Run 1 - Attempt 1
        mAggregator.testRunStarted("run1", 2, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testStarted(test2);
        mAggregator.testFailed(test2, "I failed. retry me.");
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunFailed("I failed");
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Run 2 - Attempt 1
        mAggregator.testRunStarted("run2", 1, 0);
        mAggregator.testStarted(test1);
        mAggregator.testEnded(test1, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());
        // Run 1 - Attempt 2
        mAggregator.testRunStarted("run1", 2, 1);
        mAggregator.testStarted(test2);
        mAggregator.testEnded(test2, new HashMap<String, Metric>());
        mAggregator.testRunEnded(450L, new HashMap<String, Metric>());

        mAggregator.invocationEnded(500L);
        InOrder inOrder = Mockito.inOrder(mDetailedListener);
        InOrder inOrderAgg = Mockito.inOrder(mAggListener);
        inOrderAgg.verify(mAggListener).setLogSaver(mLogger);
        inOrderAgg.verify(mAggListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener).setLogSaver(mLogger);
        inOrder.verify(mDetailedListener).invocationStarted(mInvocationContext);
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testFailed(test2, "I failed. retry me.");
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        // TODO: Fix detailed reporting that should clear this failure.
        inOrder.verify(mDetailedListener).testRunFailed(FailureDescription.create("I failed"));
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrder.verify(mDetailedListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mDetailedListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrder.verify(mDetailedListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mDetailedListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(900L, new HashMap<String, Metric>());
        inOrderAgg
                .verify(mAggListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrderAgg.verify(mAggListener).testStarted(Mockito.eq(test1), Mockito.anyLong());
        inOrderAgg
                .verify(mAggListener)
                .testEnded(
                        Mockito.eq(test1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrderAgg.verify(mAggListener).testRunEnded(450L, new HashMap<String, Metric>());
        inOrderAgg.verify(mAggListener).invocationEnded(500L);
        inOrder.verify(mDetailedListener).invocationEnded(500L);

        assertNull(mAggregator.getInvocationMetricRunError());
    }
}
