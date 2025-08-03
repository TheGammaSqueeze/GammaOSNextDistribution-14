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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;

/** Unit tests for {@link ShardListener}. */
@RunWith(JUnit4.class)
public class ShardListenerTest {
    private ShardListener mShardListener;
    @Mock ILogSaverListener mMockListener;
    private IInvocationContext mContext;
    @Mock ITestDevice mMockDevice;
    @Mock ILogSaver mMockSaver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mShardListener = new ShardListener(mMockListener);

        when(mMockDevice.getSerialNumber()).thenReturn("serial");
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("default", new BuildInfo());
        mContext.addAllocatedDevice("default", mMockDevice);
    }

    /** Ensure that all the events given to the shardlistener are replayed on invocationEnded. */
    @Test
    public void testBufferAndReplay() {

        TestDescription tid = new TestDescription("class1", "name1");

        mShardListener.invocationStarted(mContext);
        mShardListener.testRunStarted("run1", 1);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.invocationEnded(0L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(mContext);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).invocationEnded(0L);

        verify(mMockListener).invocationStarted(mContext);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(tid, 0L);
        verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        verify(mMockListener).invocationEnded(0L);
    }

    /** Test that we can replay events even if invocationEnded hasn't be called yet. */
    @Test
    public void testPlayRuns() {

        TestDescription tid = new TestDescription("class1", "name1");

        // mMockListener.invocationEnded(0L); On purpose not calling invocationEnded.

        mShardListener.invocationStarted(mContext);
        mShardListener.testRunStarted("run1", 1);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        // mShardListener.invocationEnded(0L); On purpose not calling invocationEnded.
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(mContext);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());

        verify(mMockListener).invocationStarted(mContext);
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(tid, 0L);
        verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
    }

    /** Ensure that replaying a log without a run (no tests ran) still works. */
    @Test
    public void testLogWithoutRun() {

        mShardListener.invocationStarted(mContext);
        mShardListener.logAssociation("test-file", new LogFile("path", "url", LogDataType.TEXT));
        mShardListener.invocationEnded(0L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(mContext);
        inOrder.verify(mMockListener).logAssociation(Mockito.eq("test-file"), Mockito.any());
        inOrder.verify(mMockListener).invocationEnded(0L);

        verify(mMockListener).invocationStarted(mContext);
        verify(mMockListener).logAssociation(Mockito.eq("test-file"), Mockito.any());
        verify(mMockListener).invocationEnded(0L);
    }

    /** Test that the buffering of events is properly done in respect to the modules too. */
    @Test
    public void testBufferAndReplay_withModule() {
        LogFile moduleLog1 = new LogFile("path", "url", LogDataType.TEXT);
        LogFile moduleLog2 = new LogFile("path2", "url2", LogDataType.TEXT);
        IInvocationContext module1 = new InvocationContext();
        IInvocationContext module2 = new InvocationContext();

        TestDescription tid = new TestDescription("class1", "name1");

        // expectation on second module

        mShardListener.invocationStarted(mContext);
        // 1st module
        mShardListener.testModuleStarted(module1);
        mShardListener.testRunStarted("run1", 1);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.testRunStarted("run2", 1);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.logAssociation("module-log1", moduleLog1);
        mShardListener.testModuleEnded();
        // 2nd module
        mShardListener.testModuleStarted(module2);
        mShardListener.testRunStarted("run3", 1);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.logAssociation("module-log2", moduleLog2);
        mShardListener.testModuleEnded();

        mShardListener.invocationEnded(0L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(mContext);
        inOrder.verify(mMockListener).testModuleStarted(module1);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).logAssociation("module-log1", moduleLog1);
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener).testModuleStarted(module2);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run3"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).logAssociation("module-log2", moduleLog2);
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener).invocationEnded(0L);
    }

    @Test
    public void testBufferAndReplay_withModule_attempts() {
        LogFile moduleLog1 = new LogFile("path", "url", LogDataType.TEXT);
        LogFile moduleLog2 = new LogFile("path2", "url2", LogDataType.TEXT);
        IInvocationContext module1 = new InvocationContext();
        IInvocationContext module2 = new InvocationContext();

        TestDescription tid = new TestDescription("class1", "name1");

        // expectation on second module

        mShardListener.setSupportGranularResults(true);
        mShardListener.invocationStarted(mContext);
        // 1st module
        mShardListener.testModuleStarted(module1);
        mShardListener.testRunStarted("run1", 1, 0);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.logAssociation("moduleLog1", moduleLog1);
        mShardListener.testRunStarted("run1", 1, 1);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.logAssociation("moduleLog1", moduleLog2);
        mShardListener.testModuleEnded();
        // 2nd module
        mShardListener.testModuleStarted(module2);
        mShardListener.testRunStarted("run2", 1, 0);
        mShardListener.testStarted(tid, 0L);
        mShardListener.testEnded(tid, 0L, new HashMap<String, Metric>());
        mShardListener.testRunEnded(0L, new HashMap<String, Metric>());
        mShardListener.testModuleEnded();

        mShardListener.invocationEnded(0L);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).invocationStarted(mContext);
        inOrder.verify(mMockListener).testModuleStarted(module1);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(1), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).logAssociation("moduleLog1", moduleLog1);
        inOrder.verify(mMockListener).logAssociation("moduleLog1", moduleLog2);
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener).testModuleStarted(module2);
        inOrder.verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mMockListener).testStarted(tid, 0L);
        inOrder.verify(mMockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testModuleEnded();
        inOrder.verify(mMockListener).invocationEnded(0L);
    }

    /** Test the full ordering structure during a sharded pattern. */
    @Test
    public void testLogOrderingForSharding() throws Exception {
        // Force ordering check
        ILogSaverListener mockListener = mock(ILogSaverListener.class);

        when(mockListener.getSummary()).thenReturn(null);

        LogFile runFile = new LogFile("path", "url", false, LogDataType.TEXT, 0L);
        when(mMockSaver.saveLogData(
                        Mockito.eq("run-file"), Mockito.eq(LogDataType.TEXT), Mockito.any()))
                .thenReturn(runFile);

        LogFile testFile = new LogFile("path", "url", false, LogDataType.TEXT, 0L);
        when(mMockSaver.saveLogData(
                        Mockito.eq("test-file"), Mockito.eq(LogDataType.TEXT), Mockito.any()))
                .thenReturn(testFile);

        TestDescription tid = new TestDescription("class1", "name1");

        // Log association played in order for the test.

        // Log association to re-associate file to the run.

        // The log not associated to the run are replay at invocation level.

        LogFile invocFile = new LogFile("path", "url", false, LogDataType.TEXT, 0L);
        when(mMockSaver.saveLogData(
                        Mockito.eq("host_log_of_shard"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any()))
                .thenReturn(invocFile);

        when(mockListener.getSummary()).thenReturn(null);

        // TODO: Fix the name of end_host_log for each shard
        when(mMockSaver.saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.eq(LogDataType.HOST_LOG),
                        Mockito.any()))
                .thenReturn(invocFile);

        when(mMockSaver.saveLogData(
                        Mockito.eq(TestInvocation.TRADEFED_END_HOST_LOG),
                        Mockito.eq(LogDataType.HOST_LOG),
                        Mockito.any()))
                .thenReturn(invocFile);

        // Setup of sharding
        LogSaverResultForwarder originalInvocation =
                new LogSaverResultForwarder(mMockSaver, Arrays.asList(mockListener));
        ShardMainResultForwarder mainForwarder =
                new ShardMainResultForwarder(Arrays.asList(originalInvocation), 1);
        mainForwarder.invocationStarted(mContext);
        ShardListener shard1 = new ShardListener(mainForwarder);
        LogSaverResultForwarder shardedInvocation =
                new LogSaverResultForwarder(mMockSaver, Arrays.asList(shard1));

        shardedInvocation.invocationStarted(mContext);
        shardedInvocation.testRunStarted("run1", 1);
        shardedInvocation.testLog(
                "run-file", LogDataType.TEXT, new ByteArrayInputStreamSource("test".getBytes()));
        shardedInvocation.testStarted(tid, 0L);
        shardedInvocation.testLog(
                "test-file",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource("test file".getBytes()));
        shardedInvocation.testEnded(tid, 0L, new HashMap<String, Metric>());
        shardedInvocation.testRunEnded(0L, new HashMap<String, Metric>());
        shardedInvocation.testLog(
                "host_log_of_shard",
                LogDataType.TEXT,
                new ByteArrayInputStreamSource("test".getBytes()));
        shardedInvocation.invocationEnded(0L);

        InOrder inOrder = Mockito.inOrder(mMockSaver, mockListener);
        inOrder.verify(mockListener).setLogSaver(mMockSaver);
        inOrder.verify(mMockSaver).invocationStarted(mContext);
        inOrder.verify(mockListener).invocationStarted(mContext);
        inOrder.verify(mockListener).getSummary();
        inOrder.verify(mockListener)
                .testLog(Mockito.eq("run-file"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        inOrder.verify(mockListener)
                .testLogSaved(
                        Mockito.eq("run-file"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any(),
                        Mockito.eq(runFile));
        inOrder.verify(mockListener)
                .testLog(Mockito.eq("test-file"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        inOrder.verify(mockListener)
                .testLogSaved(
                        Mockito.eq("test-file"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any(),
                        Mockito.eq(testFile));
        inOrder.verify(mockListener)
                .testRunStarted(
                        Mockito.eq("run1"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        inOrder.verify(mockListener).testStarted(tid, 0L);
        inOrder.verify(mockListener).logAssociation("test-file", testFile);
        inOrder.verify(mockListener).testEnded(tid, 0L, new HashMap<String, Metric>());
        inOrder.verify(mockListener).logAssociation("run-file", runFile);
        inOrder.verify(mockListener).testRunEnded(0L, new HashMap<String, Metric>());
        inOrder.verify(mockListener)
                .testLog(
                        Mockito.eq("host_log_of_shard"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any());
        inOrder.verify(mockListener)
                .testLogSaved(
                        Mockito.eq("host_log_of_shard"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any(),
                        Mockito.eq(invocFile));
        inOrder.verify(mockListener).logAssociation("host_log_of_shard", invocFile);
        inOrder.verify(mockListener).invocationEnded(0L);
        inOrder.verify(mockListener).getSummary();
        inOrder.verify(mMockSaver).invocationEnded(0L);
        inOrder.verify(mMockSaver).invocationEnded(0L);
    }
}
