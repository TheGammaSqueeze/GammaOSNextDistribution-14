/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.remoteaccess;

import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_CUSTOM;
import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_ENTER_GARAGE_MODE;
import static android.car.remoteaccess.ICarRemoteAccessService.SERVICE_ERROR_CODE_GENERAL;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.feature.FakeFeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.remoteaccess.CarRemoteAccessManager.CompletableRemoteTaskFuture;
import android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskSchedulerException;
import android.car.remoteaccess.CarRemoteAccessManager.RemoteTaskClientCallback;
import android.car.remoteaccess.CarRemoteAccessManager.ScheduleInfo;
import android.car.remoteaccess.CarRemoteAccessManager.TaskType;
import android.car.test.AbstractExpectableTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.platform.test.ravenwood.RavenwoodRule;

import com.android.car.internal.ICarBase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * <p>This class contains unit tests for the {@link CarRemoteAccessManager}.
 */
public final class CarRemoteAccessManagerUnitTest extends AbstractExpectableTestCase {
    private static final int DEFAULT_TIMEOUT = 3000;

    private static final String TEST_SCHEDULE_ID = "test schedule id";
    private static final byte[] TEST_TASK_DATA = new byte[]{
            (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
    private static final @TaskType int TEST_TASK_TYPE = TASK_TYPE_CUSTOM;
    private static final long TEST_START_TIME = 1234;
    private static final int TEST_TASK_COUNT = 10;
    private static final Duration TEST_PERIODIC = Duration.ofSeconds(1);

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder().setProcessApp().build();

    @Mock
    private ICarBase mCar;
    @Mock
    private IBinder mBinder;
    @Mock
    private ICarRemoteAccessService mService;
    @Captor
    private ArgumentCaptor<CompletableRemoteTaskFuture> mFutureCaptor;

    private CarRemoteAccessManager mRemoteAccessManager;
    private final Executor mExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        when(mCar.handleRemoteExceptionFromCarService(any(RemoteException.class), any()))
                .thenAnswer((inv) -> {
                    return inv.getArgument(1);
                });
        mRemoteAccessManager = new CarRemoteAccessManager(mCar, mBinder);
    }

    @Test
    public void testSetRemoteTaskClient() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 0);

        mRemoteAccessManager.setRemoteTaskClient(mExecutor, remoteTaskClient);

        verify(mService).addCarRemoteTaskClient(any(ICarRemoteAccessCallback.class));
    }

    @Test
    public void testSetRemoteTaskClient_invalidArguments() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 0);

        assertThrows(IllegalArgumentException.class, () -> mRemoteAccessManager.setRemoteTaskClient(
                /* executor= */ null, remoteTaskClient));
        assertThrows(IllegalArgumentException.class, () -> mRemoteAccessManager.setRemoteTaskClient(
                mExecutor, /* callback= */ null));
    }

    @Test
    public void testSetRemoteTaskClient_doubleRegistration() throws Exception {
        RemoteTaskClient remoteTaskClientOne = new RemoteTaskClient(/* expectedCallbackCount= */ 0);
        RemoteTaskClient remoteTaskClientTwo = new RemoteTaskClient(/* expectedCallbackCount= */ 0);

        mRemoteAccessManager.setRemoteTaskClient(mExecutor, remoteTaskClientOne);

        assertThrows(IllegalStateException.class, () -> mRemoteAccessManager.setRemoteTaskClient(
                mExecutor, remoteTaskClientTwo));
    }

    @Test
    public void testSetRmoteTaskClient_remoteException() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 0);
        doThrow(RemoteException.class).when(mService)
                .addCarRemoteTaskClient(any(ICarRemoteAccessCallback.class));

        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);
        internalCallback.onRemoteTaskRequested("clientId_testing", "taskId_testing",
                /* data= */ null, /* taskMaxDurationInSec= */ 10);

        assertWithMessage("Remote task").that(remoteTaskClient.getTaskId()).isNull();
    }

    @Test
    public void testClearRemoteTaskClient() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 0);
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);

        mRemoteAccessManager.clearRemoteTaskClient();
        internalCallback.onRemoteTaskRequested("clientId_testing", "taskId_testing",
                /* data= */ null, /* taskMaxDurationInSec= */ 10);

        assertWithMessage("Remote task").that(remoteTaskClient.getTaskId()).isNull();
    }

    @Test
    public void testClearRemoteTaskClient_remoteException() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 0);
        doThrow(RemoteException.class).when(mService)
                .removeCarRemoteTaskClient(any(ICarRemoteAccessCallback.class));
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);

        mRemoteAccessManager.clearRemoteTaskClient();
        internalCallback.onRemoteTaskRequested("clientId_testing", "taskId_testing",
                /* data= */ null, /* taskMaxDurationInSec= */ 10);

        assertWithMessage("Remote task").that(remoteTaskClient.getTaskId()).isNull();
    }

    @Test
    public void testClientRegistration() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 1);
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);
        String serviceId = "serviceId_testing";
        String vehicleId = "vehicleId_testing";
        String processorId = "processorId_testing";
        String clientId = "clientId_testing";

        internalCallback.onClientRegistrationUpdated(
                new RemoteTaskClientRegistrationInfo(serviceId, vehicleId, processorId, clientId));

        assertWithMessage("Service ID").that(remoteTaskClient.getServiceId()).isEqualTo(serviceId);
        assertWithMessage("Vehicle ID").that(remoteTaskClient.getVehicleId()).isEqualTo(vehicleId);
        assertWithMessage("Processor ID").that(remoteTaskClient.getProcessorId())
                .isEqualTo(processorId);
        assertWithMessage("Client ID").that(remoteTaskClient.getClientId()).isEqualTo(clientId);
    }

    @Test
    public void testServerlessClientRegistration() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 1);
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);
        String clientId = "clientId_testing";
        FakeFeatureFlagsImpl fakeFlagsImpl = new FakeFeatureFlagsImpl();
        fakeFlagsImpl.setFlag(Flags.FLAG_SERVERLESS_REMOTE_ACCESS, true);
        mRemoteAccessManager.setFeatureFlags(fakeFlagsImpl);

        internalCallback.onServerlessClientRegistered(clientId);

        assertThat(remoteTaskClient.isServerlessClientRegistered()).isTrue();
    }

    @Test
    public void testClientRegistrationFail() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 1);
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);

        internalCallback.onClientRegistrationFailed();

        assertWithMessage("Registration fail").that(remoteTaskClient.isRegistrationFail()).isTrue();
    }

    @Test
    public void testRemoteTaskRequested() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 2);
        String clientId = "clientId_testing";
        String taskId = "taskId_testing";
        prepareRemoteTaskRequested(remoteTaskClient, clientId, taskId, /* data= */ null);

        assertWithMessage("Task ID").that(remoteTaskClient.getTaskId()).isEqualTo(taskId);
        assertWithMessage("Data").that(remoteTaskClient.getData()).isNull();
    }

    @Test
    public void testRemoteTaskRequested_withData() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 2);
        String clientId = "clientId_testing";
        String taskId = "taskId_testing";
        byte[] data = new byte[]{1, 2, 3, 4};
        prepareRemoteTaskRequested(remoteTaskClient, clientId, taskId, data);

        assertWithMessage("Task ID").that(remoteTaskClient.getTaskId()).isEqualTo(taskId);
        assertWithMessage("Data").that(remoteTaskClient.getData()).asList()
                .containsExactlyElementsIn(new Byte[]{1, 2, 3, 4});
    }

    @Test
    public void testRemoteTaskRequested_mismatchedClientId() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 1);
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);
        String serviceId = "serviceId_testing";
        String vehicleId = "vehicleId_testing";
        String processorId = "processorId_testing";
        String clientId = "clientId_testing";
        String misMatchedClientId = "clientId_mismatch";
        String taskId = "taskId_testing";
        byte[] data = new byte[]{1, 2, 3, 4};

        internalCallback.onClientRegistrationUpdated(
                new RemoteTaskClientRegistrationInfo(serviceId, vehicleId, processorId, clientId));
        internalCallback.onRemoteTaskRequested(misMatchedClientId, taskId, data,
                /* taskMaximumDurationInSec= */ 10);

        assertWithMessage("Task ID").that(remoteTaskClient.getTaskId()).isNull();
        assertWithMessage("Data").that(remoteTaskClient.getData()).isNull();
    }

    @Test
    public void testReportRemoteTaskDone() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 2);
        String clientId = "clientId_testing";
        String taskId = "taskId_testing";
        prepareRemoteTaskRequested(remoteTaskClient, clientId, taskId, /* data= */ null);

        mRemoteAccessManager.reportRemoteTaskDone(taskId);

        verify(mService).reportRemoteTaskDone(clientId, taskId);
    }

    @Test
    public void testReportRemoteTaskDone_nullTaskId() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mRemoteAccessManager.reportRemoteTaskDone(/* taskId= */ null));
    }

    @Test
    public void testReportRemoteTaskDone_noRegisteredClient() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> mRemoteAccessManager.reportRemoteTaskDone("taskId_testing"));
    }

    @Test
    public void testReportRemoteTaskDone_invalidTaskId() throws Exception {
        RemoteTaskClient remoteTaskClient = new RemoteTaskClient(/* expectedCallbackCount= */ 2);
        String clientId = "clientId_testing";
        String taskId = "taskId_testing";
        prepareRemoteTaskRequested(remoteTaskClient, clientId, taskId, /* data= */ null);
        doThrow(IllegalStateException.class).when(mService)
                .reportRemoteTaskDone(clientId, taskId);

        assertThrows(IllegalStateException.class,
                () -> mRemoteAccessManager.reportRemoteTaskDone(taskId));
    }

    @Test
    public void testSetPowerStatePostTaskExecution() throws Exception {
        int nextPowerState = CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM;
        boolean runGarageMode = true;

        mRemoteAccessManager.setPowerStatePostTaskExecution(nextPowerState, runGarageMode);

        verify(mService).setPowerStatePostTaskExecution(nextPowerState, runGarageMode);
    }

    @Test
    public void testOnShutdownStarting() throws Exception {
        RemoteTaskClientCallback remoteTaskClient = mock(RemoteTaskClientCallback.class);
        String clientId = "clientId_testing";
        String serviceId = "serviceId_testing";
        String vehicleId = "vehicleId_testing";
        String processorId = "processorId_testing";
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(remoteTaskClient);

        internalCallback.onClientRegistrationUpdated(
                new RemoteTaskClientRegistrationInfo(serviceId, vehicleId, processorId, clientId));

        verify(remoteTaskClient, timeout(DEFAULT_TIMEOUT)).onRegistrationUpdated(any());

        internalCallback.onShutdownStarting();

        verify(remoteTaskClient, timeout(DEFAULT_TIMEOUT)).onShutdownStarting(
                mFutureCaptor.capture());
        CompletableRemoteTaskFuture future = mFutureCaptor.getValue();

        verify(mService, never()).confirmReadyForShutdown(any());

        future.complete();

        verify(mService).confirmReadyForShutdown(clientId);
    }

    @Test
    public void testScheduleInfoBuilder() {
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID, TEST_TASK_TYPE,
                TEST_START_TIME);
        builder.setTaskData(TEST_TASK_DATA);
        ScheduleInfo scheduleInfo = builder.setCount(TEST_TASK_COUNT).setPeriodic(TEST_PERIODIC)
                .build();

        expectWithMessage("scheduleId from ScheduleInfo").that(scheduleInfo.getScheduleId())
                .isEqualTo(TEST_SCHEDULE_ID);
        expectWithMessage("taskType from ScheduleInfo").that(scheduleInfo.getTaskType())
                .isEqualTo(TEST_TASK_TYPE);
        expectWithMessage("taskData from ScheduleInfo").that(scheduleInfo.getTaskData())
                .isEqualTo(TEST_TASK_DATA);
        expectWithMessage("startTimeInEpochSeconds from ScheduleInfo")
                .that(scheduleInfo.getStartTimeInEpochSeconds()).isEqualTo(TEST_START_TIME);
        expectWithMessage("count from ScheduleInfo").that(scheduleInfo.getCount())
                .isEqualTo(TEST_TASK_COUNT);
        expectWithMessage("periodic from ScheduleInfo").that(scheduleInfo.getPeriodic())
                .isEqualTo(TEST_PERIODIC);
    }

    @Test
    public void testScheduleInfoBuilder_enterGarageMode() {
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID,
                TASK_TYPE_ENTER_GARAGE_MODE, TEST_START_TIME);
        ScheduleInfo scheduleInfo = builder.setCount(TEST_TASK_COUNT).setPeriodic(TEST_PERIODIC)
                .build();

        expectWithMessage("scheduleId from ScheduleInfo").that(scheduleInfo.getScheduleId())
                .isEqualTo(TEST_SCHEDULE_ID);
        expectWithMessage("taskType from ScheduleInfo").that(scheduleInfo.getTaskType())
                .isEqualTo(TASK_TYPE_ENTER_GARAGE_MODE);
        expectWithMessage("taskData from ScheduleInfo").that(scheduleInfo.getTaskData())
                .isEmpty();
        expectWithMessage("startTimeInEpochSeconds from ScheduleInfo")
                .that(scheduleInfo.getStartTimeInEpochSeconds()).isEqualTo(TEST_START_TIME);
        expectWithMessage("count from ScheduleInfo").that(scheduleInfo.getCount())
                .isEqualTo(TEST_TASK_COUNT);
        expectWithMessage("periodic from ScheduleInfo").that(scheduleInfo.getPeriodic())
                .isEqualTo(TEST_PERIODIC);
    }

    @Test
    public void testScheduleInfoBuilder_invalidTaskType() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ScheduleInfo.Builder(
                TEST_SCHEDULE_ID, /*taskType=*/-1234, TEST_START_TIME));
    }

    @Test
    public void testScheduleInfoBuilder_buildTwiceNotAllowed() {
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID, TEST_TASK_TYPE,
                TEST_START_TIME);
        builder.setTaskData(TEST_TASK_DATA).setCount(TEST_TASK_COUNT).setPeriodic(TEST_PERIODIC)
                .build();

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    public void testScheduleInfoBuilder_nullScheduleId() {
        assertThrows(IllegalArgumentException.class, () -> new ScheduleInfo.Builder(
                /* scheduleId= */ null, TEST_TASK_TYPE, TEST_START_TIME));
    }

    @Test
    public void testScheduleInfoBuilder_nullTaskData() {
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID, TEST_TASK_TYPE,
                TEST_START_TIME);

        assertThrows(IllegalArgumentException.class, () -> builder.setTaskData(null));
    }

    @Test
    public void testScheduleInfoBuilder_negativeCount() {
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID,
                TEST_TASK_TYPE, TEST_START_TIME);

        assertThrows(IllegalArgumentException.class, () -> builder.setCount(-1));
    }

    @Test
    public void testScheduleInfoBuilder_nullPeriodic() {
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID,  TEST_TASK_TYPE,
                TEST_START_TIME);

        assertThrows(IllegalArgumentException.class, () -> builder.setPeriodic(null));
    }

    @Test
    public void testIsTaskScheduleSupported() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        assertThat(mRemoteAccessManager.isTaskScheduleSupported()).isTrue();
    }

    @Test
    public void testGetInVehicleTaskScheduler() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        assertThat(mRemoteAccessManager.getInVehicleTaskScheduler()).isNotNull();
    }

    @Test
    public void testGetInVehicleTaskScheduler_notSupported() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(false);

        assertThat(mRemoteAccessManager.getInVehicleTaskScheduler()).isNull();
    }

    private TaskScheduleInfo getTestTaskScheduleInfo() {
        TaskScheduleInfo taskScheduleInfo = new TaskScheduleInfo();
        taskScheduleInfo.scheduleId = TEST_SCHEDULE_ID;
        taskScheduleInfo.taskType = TEST_TASK_TYPE;
        taskScheduleInfo.taskData = TEST_TASK_DATA;
        taskScheduleInfo.startTimeInEpochSeconds = TEST_START_TIME;
        taskScheduleInfo.count = TEST_TASK_COUNT;
        taskScheduleInfo.periodicInSeconds = TEST_PERIODIC.getSeconds();
        return taskScheduleInfo;
    }

    @Test
    public void testScheduleTask() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        ScheduleInfo.Builder builder = new ScheduleInfo.Builder(TEST_SCHEDULE_ID, TEST_TASK_TYPE,
                TEST_START_TIME);
        ScheduleInfo scheduleInfo = builder.setTaskData(TEST_TASK_DATA).setCount(TEST_TASK_COUNT)
                .setPeriodic(TEST_PERIODIC).build();

        mRemoteAccessManager.getInVehicleTaskScheduler().scheduleTask(scheduleInfo);

        verify(mService).scheduleTask(getTestTaskScheduleInfo());
    }

    @Test
    public void testScheduleTask_nullScheduleInfo() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().scheduleTask(null));
    }

    @Test
    public void testScheduleTask_ServiceSpecificException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        doThrow(new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL)).when(mService)
                .scheduleTask(any());
        ScheduleInfo scheduleInfo = new ScheduleInfo.Builder(TEST_SCHEDULE_ID,
                TEST_TASK_TYPE, TEST_START_TIME).setTaskData(TEST_TASK_DATA)
                .setCount(TEST_TASK_COUNT).setPeriodic(TEST_PERIODIC).build();

        assertThrows(InVehicleTaskSchedulerException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().scheduleTask(scheduleInfo));
    }

    @Test
    public void testUnscheduleTask() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        mRemoteAccessManager.getInVehicleTaskScheduler().unscheduleTask(TEST_SCHEDULE_ID);

        verify(mService).unscheduleTask(TEST_SCHEDULE_ID);
    }

    @Test
    public void testUnscheduleTask_nullScheduleId() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().unscheduleTask(null));
    }

    @Test
    public void testUnscheduleTask_ServiceSpecificException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        doThrow(new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL)).when(mService)
                .unscheduleTask(any());

        assertThrows(InVehicleTaskSchedulerException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().unscheduleTask(TEST_SCHEDULE_ID));
    }

    @Test
    public void testGetAllPendingScheduledTasks() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        when(mService.getAllPendingScheduledTasks()).thenReturn(List.of(getTestTaskScheduleInfo()));

        List<ScheduleInfo> scheduleInfoList = mRemoteAccessManager.getInVehicleTaskScheduler()
                .getAllPendingScheduledTasks();

        assertThat(scheduleInfoList).hasSize(1);
        ScheduleInfo scheduleInfo = scheduleInfoList.get(0);
        expectWithMessage("scheduleId from ScheduleInfo").that(scheduleInfo.getScheduleId())
                .isEqualTo(TEST_SCHEDULE_ID);
        expectWithMessage("taskData from ScheduleInfo").that(scheduleInfo.getTaskData())
                .isEqualTo(TEST_TASK_DATA);
        expectWithMessage("startTimeInEpochSeconds from ScheduleInfo")
                .that(scheduleInfo.getStartTimeInEpochSeconds()).isEqualTo(TEST_START_TIME);
        expectWithMessage("count from ScheduleInfo").that(scheduleInfo.getCount())
                .isEqualTo(TEST_TASK_COUNT);
        expectWithMessage("periodic from ScheduleInfo").that(scheduleInfo.getPeriodic())
                .isEqualTo(TEST_PERIODIC);
    }

    @Test
    public void testGetAllPendingScheduledTasks_ServiceSpecificException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        doThrow(new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL)).when(mService)
                .getAllPendingScheduledTasks();

        assertThrows(InVehicleTaskSchedulerException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().getAllPendingScheduledTasks());
    }

    @Test
    public void testUnscheduleAllTasks() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        mRemoteAccessManager.getInVehicleTaskScheduler().unscheduleAllTasks();

        verify(mService).unscheduleAllTasks();
    }

    @Test
    public void testUnscheduleAllTasks_ServiceSpecificException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        doThrow(new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL)).when(mService)
                .unscheduleAllTasks();

        assertThrows(InVehicleTaskSchedulerException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().unscheduleAllTasks());
    }

    @Test
    public void testIsTaskScheduled() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        when(mService.isTaskScheduled(TEST_SCHEDULE_ID)).thenReturn(true);

        assertThat(mRemoteAccessManager.getInVehicleTaskScheduler()
                .isTaskScheduled(TEST_SCHEDULE_ID)).isTrue();
    }

    @Test
    public void testIsTaskScheduled_nullScheduleId() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().isTaskScheduled(null));
    }

    @Test
    public void testIsTaskScheduled_ServiceSpecificException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        when(mService.isTaskScheduled(any())).thenThrow(new ServiceSpecificException(
                SERVICE_ERROR_CODE_GENERAL));

        assertThrows(InVehicleTaskSchedulerException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().isTaskScheduled(TEST_SCHEDULE_ID));
    }

    @Test
    public void testGetSupportedTaskTypes() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        int[] taskTypes = new int[]{TASK_TYPE_CUSTOM, TASK_TYPE_ENTER_GARAGE_MODE};
        when(mService.getSupportedTaskTypesForScheduling()).thenReturn(taskTypes);

        assertThat(mRemoteAccessManager.getInVehicleTaskScheduler().getSupportedTaskTypes())
                .isEqualTo(taskTypes);
    }

    @Test
    public void testGetSupportedTaskTypes_RemoteException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        when(mService.getSupportedTaskTypesForScheduling()).thenThrow(new RemoteException());

        assertThat(mRemoteAccessManager.getInVehicleTaskScheduler().getSupportedTaskTypes())
                .isEmpty();
    }

    @Test
    public void testGetSupportedTaskTypes_ServiceSpecificException() throws Exception {
        when(mService.isTaskScheduleSupported()).thenReturn(true);
        when(mService.getSupportedTaskTypesForScheduling()).thenThrow(
                new ServiceSpecificException(0));

        assertThrows(InVehicleTaskSchedulerException.class, () ->
                mRemoteAccessManager.getInVehicleTaskScheduler().getSupportedTaskTypes());
    }

    private ICarRemoteAccessCallback setClientAndGetCallback(RemoteTaskClientCallback client)
            throws Exception {
        ArgumentCaptor<ICarRemoteAccessCallback> internalCallbackCaptor =
                ArgumentCaptor.forClass(ICarRemoteAccessCallback.class);
        mRemoteAccessManager.setRemoteTaskClient(mExecutor, client);
        verify(mService).addCarRemoteTaskClient(internalCallbackCaptor.capture());
        return internalCallbackCaptor.getValue();
    }

    private void prepareRemoteTaskRequested(RemoteTaskClient client, String clientId,
            String taskId, byte[] data) throws Exception {
        ICarRemoteAccessCallback internalCallback = setClientAndGetCallback(client);
        String serviceId = "serviceId_testing";
        String vehicleId = "vehicleId_testing";
        String processorId = "processorId_testing";

        internalCallback.onClientRegistrationUpdated(
                new RemoteTaskClientRegistrationInfo(serviceId, vehicleId, processorId, clientId));
        internalCallback.onRemoteTaskRequested(clientId, taskId, data,
                /* taskMaximumDurationInSec= */ 10);
    }

    private static final class RemoteTaskClient implements RemoteTaskClientCallback {
        private static final int DEFAULT_TIMEOUT = 3000;

        private final CountDownLatch mLatch;
        private String mServiceId;
        private String mVehicleId;
        private String mProcessorId;
        private String mClientId;
        private String mTaskId;
        private boolean mRegistrationFailed;
        private boolean mServerlessClientRegistered;
        private byte[] mData;

        private RemoteTaskClient(int expectedCallbackCount) {
            mLatch = new CountDownLatch(expectedCallbackCount);
        }

        @Override
        public void onRegistrationUpdated(RemoteTaskClientRegistrationInfo info) {
            mServiceId = info.getServiceId();
            mVehicleId = info.getVehicleId();
            mProcessorId = info.getProcessorId();
            mClientId = info.getClientId();
            mLatch.countDown();
        }

        @Override
        public void onServerlessClientRegistered() {
            mServerlessClientRegistered = true;
            mLatch.countDown();
        }

        @Override
        public void onRegistrationFailed() {
            mRegistrationFailed = true;
            mLatch.countDown();
        }

        @Override
        public void onRemoteTaskRequested(String taskId, byte[] data, int remainingTimeSec) {
            mTaskId = taskId;
            mData = data;
            mLatch.countDown();
        }

        @Override
        public void onShutdownStarting(CarRemoteAccessManager.CompletableRemoteTaskFuture future) {
            mLatch.countDown();
        }

        public String getServiceId() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mServiceId;
        }

        public String getVehicleId() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mVehicleId;
        }

        public String getProcessorId() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mProcessorId;
        }

        public String getClientId() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mClientId;
        }

        public String getTaskId() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mTaskId;
        }

        public byte[] getData() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mData;
        }

        public boolean isRegistrationFail() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mRegistrationFailed;
        }

        public boolean isServerlessClientRegistered() throws Exception {
            JavaMockitoHelper.await(mLatch, DEFAULT_TIMEOUT);
            return mServerlessClientRegistered;
        }
    }
}
