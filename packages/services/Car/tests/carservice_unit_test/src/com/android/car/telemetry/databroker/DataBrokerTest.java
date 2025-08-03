/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.telemetry.databroker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.car.AbstractExtendedMockitoCarServiceTestCase;
import android.car.builtin.util.TimingsTraceLog;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;
import android.car.telemetry.TelemetryProto;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.CarPropertyService;
import com.android.car.internal.property.CarPropertyConfigList;
import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.publisher.AbstractPublisher;
import com.android.car.telemetry.publisher.PublisherFactory;
import com.android.car.telemetry.scriptexecutorinterface.BundleList;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutor;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutorListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public final class DataBrokerTest extends AbstractExtendedMockitoCarServiceTestCase {
    private static final String TAG = DataBrokerTest.class.getSimpleName();

    private static final int PROP_ID = 100;
    private static final int PROP_AREA = 200;
    private static final int PRIORITY_HIGH = 0;
    private static final int PRIORITY_LOW = 100;
    private static final long TIMEOUT_MS = 15_000L;
    private static final CarPropertyConfig<Integer> PROP_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_ID, PROP_AREA)
                    .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final TelemetryProto.VehiclePropertyPublisher
            VEHICLE_PROPERTY_PUBLISHER_CONFIGURATION =
            TelemetryProto.VehiclePropertyPublisher.newBuilder().setReadRate(
                    1).setVehiclePropertyId(PROP_ID).build();
    private static final TelemetryProto.Publisher PUBLISHER_CONFIGURATION =
            TelemetryProto.Publisher.newBuilder().setVehicleProperty(
                    VEHICLE_PROPERTY_PUBLISHER_CONFIGURATION).build();

    /** MetricsConfig that contains a high priority subscriber. */
    private static final TelemetryProto.Subscriber SUBSCRIBER_FOO =
            TelemetryProto.Subscriber.newBuilder().setHandler("function_name_foo").setPublisher(
                    PUBLISHER_CONFIGURATION).setPriority(PRIORITY_HIGH).build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_FOO =
            TelemetryProto.MetricsConfig.newBuilder().setName("Foo").setVersion(
                    1).addSubscribers(SUBSCRIBER_FOO).build();
    private static final String NAME_FOO = METRICS_CONFIG_FOO.getName();

    /** MetricsConfig that contains a low priority subscriber. */
    private static final TelemetryProto.Subscriber SUBSCRIBER_BAR =
            TelemetryProto.Subscriber.newBuilder().setHandler("function_name_bar").setPublisher(
                    PUBLISHER_CONFIGURATION).setPriority(PRIORITY_LOW).build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_BAR =
            TelemetryProto.MetricsConfig.newBuilder().setName("Bar").setVersion(
                    1).addSubscribers(SUBSCRIBER_BAR).build();
    private static final String NAME_BAR = METRICS_CONFIG_BAR.getName();

    // when count reaches 0, all handler messages are scheduled to be dispatched after current time
    private CountDownLatch mIdleHandlerLatch = new CountDownLatch(1);
    private PersistableBundle mData = new PersistableBundle();
    private DataBrokerImpl mDataBroker;
    private FakeScriptExecutor mFakeScriptExecutor;
    private AbstractPublisher.PublisherListener mPublisherListener;
    private ScriptExecutionTask mHighPriorityTask;
    private ScriptExecutionTask mLowPriorityTask;

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private CarPropertyService mMockCarPropertyService;
    @Mock
    private DataBroker.DataBrokerListener mMockDataBrokerListener;
    @Mock
    private IBinder mMockScriptExecutorBinder;
    @Mock
    private ResultStore mMockResultStore;
    @Mock
    private TimingsTraceLog mMockTimingsTraceLog;
    @Mock
    private PublisherFactory mMockPublisherFactory;
    @Mock
    private AbstractPublisher mAbstractPublisher;

    public DataBrokerTest() {
        super(CarLog.TAG_TELEMETRY);
    }

    @Before
    public void setUp() throws Exception {
        when(mMockCarPropertyService.getPropertyList())
                .thenReturn(new CarPropertyConfigList(
                        Collections.singletonList(PROP_CONFIG)));
        mockPackageManager();

        mFakeScriptExecutor = new FakeScriptExecutor();
        when(mMockScriptExecutorBinder.queryLocalInterface(anyString()))
                .thenReturn(mFakeScriptExecutor);
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any())).thenAnswer(i -> {
            ServiceConnection conn = i.getArgument(1);
            conn.onServiceConnected(null, mMockScriptExecutorBinder);
            return true;
        });

        when(mMockPublisherFactory.getPublisher(any())).thenReturn(mAbstractPublisher);
        mDataBroker = new DataBrokerImpl(
                mMockContext, mMockPublisherFactory, mMockResultStore, mMockTimingsTraceLog);
        mDataBroker.setDataBrokerListener(mMockDataBrokerListener);
        // add IdleHandler to get notified when all messages and posts are handled
        mDataBroker.getTelemetryHandler().getLooper().getQueue().addIdleHandler(() -> {
            mIdleHandlerLatch.countDown();
            return true;
        });

        ArgumentCaptor<AbstractPublisher.PublisherListener> listenerCaptor =
                ArgumentCaptor.forClass(AbstractPublisher.PublisherListener.class);
        verify(mMockPublisherFactory).initialize(listenerCaptor.capture());
        mPublisherListener = listenerCaptor.getValue();

        mHighPriorityTask = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                mData,
                SystemClock.elapsedRealtime(),
                false,
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber());
        mLowPriorityTask = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_BAR, SUBSCRIBER_BAR),
                mData,
                SystemClock.elapsedRealtime(),
                false,
                TelemetryProto.Publisher.PublisherCase.MEMORY.getNumber());
    }

    private void mockPackageManager() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        PackageInfo info = new PackageInfo();
        info.packageName = "com.android.car.scriptexecutor";
        when(mMockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(info);
    }

    @After
    public void tearDown() throws Exception {
        if (mDataBroker != null) {
            // Remove all to make sure that those are not kicked in after test.
            mDataBroker.getTelemetryHandler().removeMessages(DataBrokerImpl.MSG_HANDLE_TASK);
            mDataBroker.getTelemetryHandler().removeMessages(
                    DataBrokerImpl.MSG_BIND_TO_SCRIPT_EXECUTOR);
            mDataBroker.getTelemetryHandler().removeMessages(
                    DataBrokerImpl.MSG_STOP_HANGING_SCRIPT);
        }
        Log.i(TAG, "tearDown completed");
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(ParcelFileDescriptor.class);
    }

    @Test
    public void testStopHangingScript_shouldUnbindScriptExecutor() throws Exception {
        mDataBroker.getTelemetryHandler().sendEmptyMessage(DataBrokerImpl.MSG_STOP_HANGING_SCRIPT);

        waitForTelemetryThreadToFinish();
        verify(mMockContext).unbindService(any());
    }

    @Test
    public void testSetTaskExecutionPriority_whenNoTask_shouldNotInvokeScriptExecutor()
            throws Exception {
        mDataBroker.setTaskExecutionPriority(PRIORITY_HIGH);

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(0);
    }

    @Test
    public void testSetTaskExecutionPriority_whenNextTaskPriorityLow_shouldNotRunTask()
            throws Exception {
        mDataBroker.getTaskQueue().add(mLowPriorityTask);

        mDataBroker.setTaskExecutionPriority(PRIORITY_HIGH);

        waitForTelemetryThreadToFinish();
        // task is not polled
        assertThat(mDataBroker.getTaskQueue().peek()).isEqualTo(mLowPriorityTask);
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(0);
    }

    @Test
    public void testSetTaskExecutionPriority_whenNextTaskPriorityHigh_shouldInvokeScriptExecutor()
            throws Exception {
        mDataBroker.getTaskQueue().add(mHighPriorityTask);

        mDataBroker.setTaskExecutionPriority(PRIORITY_HIGH);

        waitForTelemetryThreadToFinish();
        // task is polled and run
        assertThat(mDataBroker.getTaskQueue().peek()).isNull();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
    }

    @Test
    public void testScheduleNextTask_whenNoTask_shouldNotInvokeScriptExecutor() throws Exception {
        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(0);
    }

    @Test
    public void testScheduleNextTask_whenTaskInProgress_shouldNotInvokeScriptExecutorAgain()
            throws Exception {
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask);
        mDataBroker.scheduleNextTask(); // start a task
        waitForTelemetryThreadToFinish();
        assertThat(taskQueue.peek()).isNull(); // assert that task is polled and running
        taskQueue.add(mHighPriorityTask); // add another task into the queue

        mDataBroker.scheduleNextTask(); // schedule next task while the last task is in progress

        waitForTelemetryThreadToFinish();
        // verify task is not polled
        assertThat(taskQueue.peek()).isEqualTo(mHighPriorityTask);
        // expect one invocation for the task that is running
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
    }

    @Test
    public void testScheduleNextTask_onScriptSuccess_shouldStoreInterimResult() throws Exception {
        mData.putBoolean("script is finished", false);
        mData.putDouble("value of euler's number", 2.71828);
        mDataBroker.getTaskQueue().add(mHighPriorityTask);

        mDataBroker.scheduleNextTask();
        waitForTelemetryThreadToFinish();
        mFakeScriptExecutor.notifyScriptSuccess(mData); // posts to telemetry handler

        waitForTelemetryThreadToFinish();
        assertThat(mDataBroker.getTaskQueue().peek()).isNull();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
        verify(mMockDataBrokerListener).onEventConsumed(
                eq(mHighPriorityTask.getMetricsConfig().getName()), eq(mData));
    }

    @Test
    public void testScheduleNextTask_onScriptError_shouldStoreErrorObject() throws Exception {
        mDataBroker.getTaskQueue().add(mHighPriorityTask);
        TelemetryProto.TelemetryError.ErrorType errorType =
                TelemetryProto.TelemetryError.ErrorType.LUA_RUNTIME_ERROR;
        String errorMessage = "test onError";
        TelemetryProto.TelemetryError expectedError = TelemetryProto.TelemetryError.newBuilder()
                .setErrorType(errorType)
                .setMessage(errorMessage)
                .build();

        mDataBroker.scheduleNextTask();
        waitForTelemetryThreadToFinish();
        mFakeScriptExecutor.notifyScriptError(errorType.getNumber(), errorMessage);

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
        verify(mMockDataBrokerListener).onReportFinished(eq(NAME_FOO), eq(expectedError));
    }

    @Test
    public void testScheduleNextTask_whenScriptFinishes_shouldStoreFinalResult()
            throws Exception {
        mData.putBoolean("script is finished", true);
        mData.putDouble("value of pi", 3.14159265359);
        mDataBroker.getTaskQueue().add(mHighPriorityTask);

        mDataBroker.scheduleNextTask();
        waitForTelemetryThreadToFinish();
        mFakeScriptExecutor.notifyScriptFinish(mData); // posts to telemetry handler

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
        verify(mMockDataBrokerListener).onReportFinished(eq(NAME_FOO), eq(mData));
    }

    @Test
    public void testScheduleNextTask_whenScriptProducesReport_shouldStoreFinalResult()
            throws Exception {
        mData.putBoolean("script produces report", true);
        mData.putDouble("value of pi", 3.14159265359);
        mDataBroker.getTaskQueue().add(mHighPriorityTask);

        mDataBroker.scheduleNextTask();
        waitForTelemetryThreadToFinish();
        mFakeScriptExecutor.notifyMetricsReport(mData); // posts to telemetry handler

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
        verify(mMockDataBrokerListener).onMetricsReport(
                eq(mHighPriorityTask.getMetricsConfig().getName()), eq(mData), isNull());
    }

    @Test
    public void testScheduleNextTask_whenScriptExecutorBypassed_shouldStoreFinalResult()
            throws Exception {
        mData.putBoolean("bypass successful", true);
        mData.putDouble("value of pi", 3.14159265359);
        TelemetryProto.Subscriber subscriberWithoutHandler =
                TelemetryProto.Subscriber.newBuilder().setPublisher(
                        PUBLISHER_CONFIGURATION).setPriority(PRIORITY_HIGH).build();
        TelemetryProto.MetricsConfig metricConfigForBypass =
                TelemetryProto.MetricsConfig.newBuilder().setName("Bypass").setVersion(
                        1).addSubscribers(subscriberWithoutHandler).build();
        ScriptExecutionTask bypassTask = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, metricConfigForBypass, subscriberWithoutHandler),
                mData,
                SystemClock.elapsedRealtime(),
                false,
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber());
        mDataBroker.getTaskQueue().add(bypassTask);

        mDataBroker.scheduleNextTask();
        waitForTelemetryThreadToFinish();

        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(0);
        verify(mMockDataBrokerListener).onMetricsReport(
                eq(bypassTask.getMetricsConfig().getName()), eq(mData), isNull());
    }

    @Test
    public void testScheduleNextTask_whenInterimDataExists_shouldPassToScriptExecutor()
            throws Exception {
        mData.putDouble("value of golden ratio", 1.618033);
        mDataBroker.getTaskQueue().add(mHighPriorityTask);
        when(mMockResultStore.getInterimResult(mHighPriorityTask.getMetricsConfig().getName()))
                .thenReturn(mData);

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
        assertThat(mFakeScriptExecutor.getSavedState()).isEqualTo(mData);
    }

    @Test
    public void testScheduleNextTask_withLargeDataFlag_shouldPipeData() throws Exception {
        PersistableBundle data = new PersistableBundle();
        ScriptExecutionTask highPriorityTask = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                data,
                SystemClock.elapsedRealtime(),
                true,
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber());
        mDataBroker.getTaskQueue().add(highPriorityTask);

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptForLargeInputCount()).isEqualTo(1);
    }

    @Test
    public void testScheduleNextTask_withoutLargeDataFlag_doesNotPipeData() throws Exception {
        PersistableBundle data = new PersistableBundle();
        ScriptExecutionTask highPriorityTask = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                data,
                SystemClock.elapsedRealtime(),
                false,
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber());
        mDataBroker.getTaskQueue().add(highPriorityTask);

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
    }

    @Test
    public void testScheduleNextTask_largeInputPipeIOException_shouldIgnoreCurrentTask()
            throws Exception {
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                new PersistableBundle(),
                SystemClock.elapsedRealtime(),
                true, // invokeScriptForLargeInput() path
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber()));
        taskQueue.add(new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                new PersistableBundle(),
                SystemClock.elapsedRealtime(),
                false,  // invokeScript() path
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber()));
        ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
        when(ParcelFileDescriptor.createPipe()).thenReturn(fds);
        fds[1].close(); // cause IO Exception in invokeScriptForLargeInput() path

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptForLargeInputCount()).isEqualTo(1);
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
        assertThat(taskQueue).isEmpty();
    }

    @Test
    public void testScheduleNextTask_withBundleList_shouldPassData() throws Exception {
        List<PersistableBundle> bundles = new ArrayList<>();
        bundles.add(new PersistableBundle());
        bundles.add(new PersistableBundle());
        ScriptExecutionTask highPriorityTask = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                bundles,
                SystemClock.elapsedRealtime(),
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber());
        mDataBroker.getTaskQueue().add(highPriorityTask);

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptForBundleListCount()).isEqualTo(1);
    }

    @Test
    public void testScheduleNextTask_bindScriptExecutorFailedOnce_shouldRebind()
            throws Exception {
        Mockito.reset(mMockContext);
        mockPackageManager();
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any())).thenAnswer(
                new Answer() {
                    private int mCount = 0;

                    @Override
                    public Object answer(InvocationOnMock invocation) {
                        if (mCount++ == 1) {
                            return false; // fail first attempt
                        }
                        ServiceConnection conn = invocation.getArgument(1);
                        conn.onServiceConnected(null, mMockScriptExecutorBinder);
                        return true; // second attempt should succeed
                    }
                });
        mDataBroker.mBindScriptExecutorDelayMillis = 0L; // immediately rebind for testing purpose
        mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO);
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask);

        // will rebind to ScriptExecutor if it is null
        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(taskQueue.peek()).isNull();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
    }

    @Test
    public void testScheduleNextTask_bindScriptExecutorFailedMultipleTimes_shouldDisableBroker()
            throws Exception {
        // fail 6 future attempts to bind to it
        Mockito.reset(mMockContext);
        mockPackageManager();
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any()))
                .thenReturn(false, false, false, false, false, false);
        mDataBroker.mBindScriptExecutorDelayMillis = 0L; // immediately rebind for testing purpose
        mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO);
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask);

        // will rebind to ScriptExecutor if it is null
        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        // broker disabled, all subscribers should have been removed
        assertThat(mDataBroker.getSubscriptionMap()).hasSize(0);
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(0);
    }

    @Test
    public void testScheduleNextTask_whenScriptExecutorThrowsException_shouldResetAndTryAgain()
            throws Exception {
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask);
        mFakeScriptExecutor.failNextApiCalls(1); // fail the next invokeScript() call

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        // invokeScript() failed, task is re-queued and re-run
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(2);
        assertThat(taskQueue).isEmpty();
    }

    @Test
    public void testScheduleNextTask_shouldPreventHangingScript() throws Exception {
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask);

        mDataBroker.scheduleNextTask();

        waitForTelemetryThreadToFinish();
        assertThat(mDataBroker.getTelemetryHandler().hasMessages(
                DataBrokerImpl.MSG_STOP_HANGING_SCRIPT)).isTrue();
    }

    @Test
    public void testScheduleNextTask_whenScriptReturns_shouldCancelStopHangingScriptMessage()
            throws Exception {
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask);

        mDataBroker.scheduleNextTask();
        waitForTelemetryThreadToFinish();
        mFakeScriptExecutor.notifyScriptSuccess(mData); // script returns

        waitForTelemetryThreadToFinish();
        assertThat(mDataBroker.getTelemetryHandler().hasMessages(
                DataBrokerImpl.MSG_STOP_HANGING_SCRIPT)).isFalse();
    }

    @Test
    public void testAddTaskToQueue_shouldInvokeScriptExecutor() throws Exception {
        mDataBroker.addTaskToQueue(mHighPriorityTask);

        waitForTelemetryThreadToFinish();
        assertThat(mFakeScriptExecutor.getInvokeScriptCount()).isEqualTo(1);
    }

    @Test
    public void testAddTaskToQueue_shouldReturnCorrectCount() throws Exception {
        // since addTaskToQueue() calls scheduleNextTask(), script executor will be invoked,
        // which polls a task from the queue
        mDataBroker.addTaskToQueue(mHighPriorityTask);
        // this will poll the task that was just added, which means stats publisher count will be
        // decremented to 0
        // as long as the test does not make ScriptExecutor return, no other task will be polled
        // because a script is currently running.
        waitForTelemetryThreadToFinish();
        // StatsPublisher publishes once
        mDataBroker.addTaskToQueue(mHighPriorityTask);
        // MemoryPublisher publishes 3 times
        mDataBroker.addTaskToQueue(mLowPriorityTask);
        mDataBroker.addTaskToQueue(mLowPriorityTask);
        mDataBroker.addTaskToQueue(mLowPriorityTask);

        // expect 1 existing task + 1 new task = 2
        int statsTaskCount = mDataBroker.addTaskToQueue(mHighPriorityTask);
        // expect 3 existing tasks + 1 new task = 4
        int memoryTaskCount = mDataBroker.addTaskToQueue(mLowPriorityTask);

        assertThat(statsTaskCount).isEqualTo(2);
        assertThat(memoryTaskCount).isEqualTo(4);
    }

    @Test
    public void testAddMetricsConfig_newMetricsConfig() {
        mDataBroker.addMetricsConfig(NAME_BAR, METRICS_CONFIG_BAR);

        assertThat(mDataBroker.getSubscriptionMap()).hasSize(1);
        assertThat(mDataBroker.getSubscriptionMap()).containsKey(NAME_BAR);
        // there should be one data subscriber in the subscription list of METRICS_CONFIG_BAR
        assertThat(mDataBroker.getSubscriptionMap().get(NAME_BAR)).hasSize(1);
    }


    @Test
    public void testAddMetricsConfig_duplicateMetricsConfig_shouldDoNothing() {
        mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO);
        mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO);

        assertThat(mDataBroker.getSubscriptionMap()).hasSize(1);
        assertThat(mDataBroker.getSubscriptionMap()).containsKey(NAME_FOO);
        assertThat(mDataBroker.getSubscriptionMap().get(NAME_FOO)).hasSize(1);
    }

    @Test
    public void testAddMetricsConfig_whenInvalidConfig_shouldThrowException() {
        // priority cannot be negative
        TelemetryProto.Subscriber badSub = SUBSCRIBER_FOO.toBuilder().setPriority(-1).build();
        TelemetryProto.MetricsConfig badConfig = METRICS_CONFIG_FOO.toBuilder()
                .clearSubscribers().addSubscribers(badSub).build();

        assertThrows(IllegalArgumentException.class,
                () -> mDataBroker.addMetricsConfig(badConfig.getName(), badConfig));
    }

    @Test
    public void testAddMetricsConfig_whenPublisherThrowsException_shouldRelayException() {
        doThrow(new IllegalArgumentException()).when(mAbstractPublisher).addDataSubscriber(any());

        assertThrows(IllegalArgumentException.class,
                () -> mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO));
    }

    @Test
    public void testRemoveMetricsConfiguration_shouldRemoveAllAssociatedTasks() {
        mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO);
        mDataBroker.addMetricsConfig(NAME_BAR, METRICS_CONFIG_BAR);
        ScriptExecutionTask taskWithMetricsConfigFoo = new ScriptExecutionTask(
                new DataSubscriber(mDataBroker, METRICS_CONFIG_FOO, SUBSCRIBER_FOO),
                mData,
                SystemClock.elapsedRealtime(),
                false,
                TelemetryProto.Publisher.PublisherCase.STATS.getNumber());
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask); // associated with METRICS_CONFIG_FOO
        taskQueue.add(mLowPriorityTask); // associated with METRICS_CONFIG_BAR
        taskQueue.add(taskWithMetricsConfigFoo); // associated with METRICS_CONFIG_FOO
        assertThat(taskQueue).hasSize(3);

        mDataBroker.removeMetricsConfig(NAME_FOO);

        assertThat(taskQueue).hasSize(1);
        assertThat(taskQueue.poll()).isEqualTo(mLowPriorityTask);
    }

    @Test
    public void testRemoveMetricsConfiguration_whenMetricsConfigNonExistent_shouldDoNothing() {
        mDataBroker.removeMetricsConfig(NAME_BAR);

        assertThat(mDataBroker.getSubscriptionMap()).hasSize(0);
    }

    @Test
    public void testRemoveAllMetricsConfigs_shouldRemoveTasksAndClearSubscriptionMap() {
        mDataBroker.addMetricsConfig(NAME_FOO, METRICS_CONFIG_FOO);
        mDataBroker.addMetricsConfig(NAME_BAR, METRICS_CONFIG_BAR);
        PriorityBlockingQueue<ScriptExecutionTask> taskQueue = mDataBroker.getTaskQueue();
        taskQueue.add(mHighPriorityTask); // associated with METRICS_CONFIG_FOO
        taskQueue.add(mLowPriorityTask); // associated with METRICS_CONFIG_BAR

        mDataBroker.removeAllMetricsConfigs();

        assertThat(taskQueue).isEmpty();
        assertThat(mDataBroker.getSubscriptionMap()).isEmpty();
    }

    @Test
    public void testPublisherListener_whenFailure_shouldSetConfigFinishedWithReport() {
        mPublisherListener.onPublisherFailure(
                Arrays.asList(METRICS_CONFIG_FOO, METRICS_CONFIG_BAR), null);

        verify(mMockDataBrokerListener).onReportFinished(
                eq(NAME_FOO), any(TelemetryProto.TelemetryError.class));
        verify(mMockDataBrokerListener).onReportFinished(
                eq(NAME_BAR), any(TelemetryProto.TelemetryError.class));
    }

    @Test
    public void testPublisherListener_whenNoReport_shouldSetConfigFinished() {
        mPublisherListener.onConfigFinished(METRICS_CONFIG_FOO);

        verify(mMockDataBrokerListener).onReportFinished(eq(NAME_FOO));
    }

    private void waitForTelemetryThreadToFinish() throws Exception {
        assertWithMessage("handler not idle in %sms", TIMEOUT_MS)
                .that(mIdleHandlerLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        mIdleHandlerLatch = new CountDownLatch(1); // reset idle handler condition
    }

    private static class FakeScriptExecutor implements IScriptExecutor {
        private IScriptExecutorListener mListener;
        private int mInvokeScriptCount = 0;
        private int mInvokeScriptForLargeInputCount = 0;
        private int mInvokeScriptForBundleListCount = 0;
        private int mFailApi = 0;
        private PersistableBundle mSavedState = null;

        @Override
        public void invokeScript(String scriptBody, String functionName,
                PersistableBundle publishedData, @Nullable PersistableBundle savedState,
                IScriptExecutorListener listener)
                throws RemoteException {
            mInvokeScriptCount++;
            mSavedState = savedState;
            mListener = listener;
            if (mFailApi > 0) {
                mFailApi--;
                throw new RemoteException("Simulated failure");
            }
        }

        @Override
        public void invokeScriptForLargeInput(String scriptBody, String functionName,
                ParcelFileDescriptor publishedDataFileDescriptor,
                @Nullable PersistableBundle savedState,
                IScriptExecutorListener listener) throws RemoteException {
            mInvokeScriptForLargeInputCount++;
            mSavedState = savedState;
            mListener = listener;
            if (mFailApi > 0) {
                mFailApi--;
                throw new RemoteException("Simulated failure");
            }
            // Since DataBrokerImpl and FakeScriptExecutor are in the same process, they do not
            // use real IPC and share the fd. When DataBroker closes the fd, it affects
            // FakeScriptExecutor. Therefore FakeScriptExecutor must dup the fd before it is
            // closed by DataBroker
            ParcelFileDescriptor dup = null;
            try {
                dup = publishedDataFileDescriptor.dup();
            } catch (IOException e) { }
            final ParcelFileDescriptor fd = Objects.requireNonNull(dup);
            // to prevent deadlock, read and write must happen on separate threads
            Handler.getMain().post(() -> {
                try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                    PersistableBundle.readFromStream(input);
                } catch (IOException e) { }
            });
        }

        @Override
        public void invokeScriptForBundleList(String scriptBody, String functionName,
                BundleList bundleList, PersistableBundle savedState,
                IScriptExecutorListener listener) throws RemoteException {
            mInvokeScriptForBundleListCount++;
            mSavedState = savedState;
            mListener = listener;
            if (mFailApi > 0) {
                mFailApi--;
                throw new RemoteException("Simulated failure");
            }
        }

        @Override
        public IBinder asBinder() {
            return null;
        }

        /** Mocks script temporary completion. */
        public void notifyScriptSuccess(PersistableBundle bundle) {
            try {
                mListener.onSuccess(bundle);
            } catch (RemoteException e) {
                // nothing to do
            }
        }

        /** Mocks script producing final result. */
        public void notifyScriptFinish(PersistableBundle bundle) {
            try {
                mListener.onScriptFinished(bundle);
            } catch (RemoteException e) {
                // nothing to do
            }
        }

        /** Mocks script finished with error. */
        public void notifyScriptError(int errorType, String errorMessage) {
            try {
                mListener.onError(errorType, errorMessage, null);
            } catch (RemoteException e) {
                // nothing to do
            }
        }

        /** Mocks script finished without completing its lifecycle. */
        public void notifyMetricsReport(PersistableBundle bundle) {
            try {
                mListener.onMetricsReport(bundle, null);
            } catch (RemoteException e) {
                // nothing to do
            }
        }

        /** Fails the next N invokeScript() call. */
        public void failNextApiCalls(int n) {
            mFailApi = n;
        }

        /** Returns number of times invokeScript() was called. */
        public int getInvokeScriptCount() {
            return mInvokeScriptCount;
        }

        /** Returns number of times invokeScriptForLargeInput() was called. */
        public int getInvokeScriptForLargeInputCount() {
            return mInvokeScriptForLargeInputCount;
        }

        /** Returns number of times invokeScriptForBundleList() was called. */
        public int getInvokeScriptForBundleListCount() {
            return mInvokeScriptForBundleListCount;
        }

        /** Returns the interim data passed in invokeScript(). */
        public PersistableBundle getSavedState() {
            return mSavedState;
        }
    }
}
