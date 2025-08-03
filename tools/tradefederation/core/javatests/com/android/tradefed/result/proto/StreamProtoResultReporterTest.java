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

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;

/** Unit tests for {@link StreamProtoResultReporter}. */
@RunWith(JUnit4.class)
public class StreamProtoResultReporterTest {

    private StreamProtoResultReporter mReporter;
    private IInvocationContext mInvocationContext;
    private IInvocationContext mMainInvocationContext;
    @Mock ITestInvocationListener mMockListener;

    private class StreamProtoReceiverTestable extends StreamProtoReceiver {

        public StreamProtoReceiverTestable(
                ITestInvocationListener listener,
                IInvocationContext mainContext,
                boolean reportInvocation)
                throws IOException {
            super(listener, mainContext, reportInvocation);
        }

        @Override
        protected long getJoinTimeout(long millis) {
            return millis;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mReporter = new StreamProtoResultReporter();
        mInvocationContext = new InvocationContext();
        mMainInvocationContext = new InvocationContext();
        mInvocationContext.setConfigurationDescriptor(new ConfigurationDescriptor());
    }

    @Test
    public void testStream() throws Exception {
        StreamProtoReceiver receiver =
                new StreamProtoReceiverTestable(mMockListener, mMainInvocationContext, true);
        OptionSetter setter = new OptionSetter(mReporter);
        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        try {
            setter.setOptionValue(
                    "proto-report-port", Integer.toString(receiver.getSocketServerPort()));
            HashMap<String, Metric> metrics = new HashMap<String, Metric>();
            metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
            // Verify mocks

            mReporter.invocationStarted(mInvocationContext);
            // Run modules
            mReporter.testModuleStarted(createModuleContext("arm64 module1"));
            mReporter.testRunStarted("run1", 2);

            mReporter.testStarted(test1, 5L);
            mReporter.testEnded(test1, 10L, new HashMap<String, Metric>());

            mReporter.testStarted(test2, 11L);
            mReporter.testFailed(test2, "I failed");
            // test log
            mReporter.logAssociation(
                    "log1", new LogFile("path", "url", false, LogDataType.TEXT, 5));

            mReporter.testEnded(test2, 60L, metrics);
            // run log
            mReporter.logAssociation(
                    "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
            mReporter.testRunEnded(50L, new HashMap<String, Metric>());

            mReporter.testModuleEnded();
            // Invocation ends
            mReporter.invocationEnded(500L);
        } finally {
            receiver.joinReceiver(5000);
            receiver.close();
            mReporter.closeSocket();
        }
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
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener).invocationEnded(500L);

        verify(mMockListener).invocationStarted(Mockito.any());
        verify(mMockListener).testModuleStarted(Mockito.any());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test1, 5L);
        verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test2, 11L);
        verify(mMockListener).testFailed(test2, FailureDescription.create("I failed"));
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testModuleEnded();
        verify(mMockListener).invocationEnded(500L);
        assertNull(receiver.getError());
    }

    /** Once the join receiver is done, we don't parse any more events. */
    @Test
    public void testStream_stopParsing() throws Exception {
        StreamProtoReceiver receiver =
                new StreamProtoReceiverTestable(mMockListener, mMainInvocationContext, true);
        OptionSetter setter = new OptionSetter(mReporter);
        try {
            setter.setOptionValue(
                    "proto-report-port", Integer.toString(receiver.getSocketServerPort()));
            // No calls on the mocks

            // If we join, then we will stop parsing events
            receiver.joinReceiver(100);
            mReporter.invocationStarted(mInvocationContext);
            // Invocation ends
            mReporter.invocationEnded(500L);
        } finally {
            receiver.close();
            mReporter.closeSocket();
        }

        assertNull(receiver.getError());
    }

    @Test
    public void testStream_noInvocationReporting() throws Exception {
        StreamProtoReceiver receiver =
                new StreamProtoReceiverTestable(
                        mMockListener,
                        mMainInvocationContext,
                        /** No invocation reporting */
                        false);
        OptionSetter setter = new OptionSetter(mReporter);
        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        try {
            setter.setOptionValue(
                    "proto-report-port", Integer.toString(receiver.getSocketServerPort()));
            HashMap<String, Metric> metrics = new HashMap<String, Metric>();
            metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
            // Verify mocks

            mReporter.invocationStarted(mInvocationContext);
            // Run modules
            mReporter.testModuleStarted(createModuleContext("arm64 module1"));
            mReporter.testRunStarted("run1", 2);

            mReporter.testStarted(test1, 5L);
            mReporter.testEnded(test1, 10L, new HashMap<String, Metric>());

            mReporter.testStarted(test2, 11L);
            mReporter.testFailed(test2, "I failed");
            // test log
            mReporter.logAssociation(
                    "log1", new LogFile("path", "url", false, LogDataType.TEXT, 5));

            mReporter.testEnded(test2, 60L, metrics);
            // run log
            mReporter.logAssociation(
                    "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
            mReporter.testRunEnded(50L, new HashMap<String, Metric>());

            mReporter.testModuleEnded();
            // Invocation ends
            mReporter.invocationEnded(500L);
        } finally {
            receiver.joinReceiver(5000);
            receiver.close();
            mReporter.closeSocket();
        }

        verify(mMockListener).testModuleStarted(Mockito.any());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(2), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test1, 5L);
        verify(mMockListener).testEnded(test1, 10L, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(test2, 11L);
        verify(mMockListener).testFailed(test2, FailureDescription.create("I failed"));
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testModuleEnded();
        assertNull(receiver.getError());
    }

    /** Helper to create a module context. */
    private IInvocationContext createModuleContext(String moduleId) {
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_ID, moduleId);
        context.setConfigurationDescriptor(new ConfigurationDescriptor());
        return context;
    }
}
