/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts;

import static android.car.Car.PERMISSION_CONTROL_REMOTE_ACCESS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.feature.Flags;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.CarRemoteAccessManager.CompletableRemoteTaskFuture;
import android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler;
import android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskSchedulerException;
import android.car.remoteaccess.CarRemoteAccessManager.RemoteTaskClientCallback;
import android.car.remoteaccess.CarRemoteAccessManager.ScheduleInfo;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.remoteaccess.CarRemoteAccessDumpProto;
import com.android.car.remoteaccess.CarRemoteAccessDumpProto.ServerlessClientInfo;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarRemoteAccessManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarRemoteAccessManagerTest.class.getSimpleName();
    private static final int CALLBACK_WAIT_TIME_MS = 2_000;
    private static final String INVALID_TASK_ID = "THIS_ID_CANNOT_BE_VALID_!@#$%^&*()";
    private static final String DUMP_COMMAND =
            "dumpsys car_service --services CarRemoteAccessService --proto";
    private static final String SERVERLESS_CLIENT_ID = "TestServerlessClientId";
    private static final String TEST_SCHEDULE_ID_1 = "TestScheduleId1";
    private static final String TEST_SCHEDULE_ID_2 = "TestScheduleId2";
    private static final byte[] TEST_TASK_DATA = "test data".getBytes();

    private Executor mExecutor;
    private CarRemoteAccessManager mCarRemoteAccessManager;
    private boolean mServerlessRemoteTaskClientSet = false;
    private String mPackageName;

    @Before
    public void setUp() throws Exception {
        assumeTrue("CarRemoteAccessService is not enabled, skipping test",
                getCar().isFeatureEnabled(Car.CAR_REMOTE_ACCESS_SERVICE));

        mExecutor = mContext.getMainExecutor();
        mCarRemoteAccessManager = (CarRemoteAccessManager) getCar()
                .getCarManager(Car.CAR_REMOTE_ACCESS_SERVICE);
        assertThat(mCarRemoteAccessManager).isNotNull();
        mPackageName = mContext.getPackageName();
    }

    @After
    public void tearDown() throws Exception {
        if (mServerlessRemoteTaskClientSet) {
            mCarRemoteAccessManager.removeServerlessRemoteTaskClient(mPackageName);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#setRemoteTaskClient",
    })
    @RequiresFlagsEnabled(Flags.FLAG_CAR_DUMP_TO_PROTO)
    public void testSetRemoteTaskClient_regularClient() throws Exception {
        assumeFalse("This test requires the test package to be a regular remote task client",
                isTestPkgServerlessClient());

        RemoteTaskClientCallbackImpl callback = new RemoteTaskClientCallbackImpl();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, callback);

        PollingCheck.waitFor(CALLBACK_WAIT_TIME_MS, () -> callback.getServiceId() != null);
        PollingCheck.waitFor(CALLBACK_WAIT_TIME_MS, () -> callback.getVehicleId() != null);
        PollingCheck.waitFor(CALLBACK_WAIT_TIME_MS, () -> callback.getProcessorId() != null);
        PollingCheck.waitFor(CALLBACK_WAIT_TIME_MS, () -> callback.getClientId() != null);
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#setRemoteTaskClient"
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testSetRemoteTaskClient_serverlessClient() throws Exception {
        setSelfAsServerlessClient();

        RemoteTaskClientCallbackImpl callback = new RemoteTaskClientCallbackImpl();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, callback);

        PollingCheck.waitFor(CALLBACK_WAIT_TIME_MS,
                () -> callback.isServerlessClientRegistered());
    }

    /**
     * Tests that calling {@code setRemoteTaskClient} twice from the same client is not allowed.
     */
    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#setRemoteTaskClient"
    })
    public void testSetRemoteTaskClient_withAlreadyRegisteredClient() {
        RemoteTaskClientCallbackImpl callbackOne = new RemoteTaskClientCallbackImpl();
        RemoteTaskClientCallbackImpl callbackTwo = new RemoteTaskClientCallbackImpl();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, callbackOne);

        assertThrows(IllegalStateException.class,
                () -> mCarRemoteAccessManager.setRemoteTaskClient(mExecutor,
                        callbackTwo));
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#setRemoteTaskClient",
            "android.car.remoteaccess.CarRemoteAccessManager#clearRemoteTaskClient"
    })
    public void testClearRemoteTaskClient() {
        RemoteTaskClientCallbackImpl callback = new RemoteTaskClientCallbackImpl();
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, callback);

        mCarRemoteAccessManager.clearRemoteTaskClient();

        // Calling clearRemoteTaskClient again to ensure that multiple calls do not cause errors.
        mCarRemoteAccessManager.clearRemoteTaskClient();
    }

    @Test
    @ApiTest(apis = {"android.car.remoteaccess.CarRemoteAccessManager#clearRemoteTaskClient"})
    public void testClearRemoteTaskClient_unregisteredClient() {
        mCarRemoteAccessManager.clearRemoteTaskClient();
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#reportRemoteTaskDone"
    })
    public void testReportRemoteTaskDone_unregisteredClient() {
        assertThrows(IllegalStateException.class,
                () -> mCarRemoteAccessManager.reportRemoteTaskDone(INVALID_TASK_ID));
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testGetInVehicleTaskScheduler_notSupported() {
        setSelfAsServerlessClient();

        assumeFalse("Task scheduling is supported, skipping the test",
                mCarRemoteAccessManager.isTaskScheduleSupported());

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        assertWithMessage("InVehicleTaskScheduler must be null when task schedule is not supported")
                .that(taskScheduler).isNull();
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testGetInVehicleTaskScheduler_isSupported() {
        setSelfAsServerlessClient();
        assumeTaskSchedulingSupported();

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        assertWithMessage("InVehicleTaskScheduler must not be null when task schedule is supported")
                .that(taskScheduler).isNotNull();
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#scheduleTask",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "unscheduleAllTasks",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testScheduleTask() throws Exception {
        setSelfAsServerlessClient();
        assumeTaskSchedulingSupported();

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        // Schedule the task to be executed 30s later.
        long startTimeInEpochSeconds = System.currentTimeMillis() / 1000 + 30;
        ScheduleInfo scheduleInfo = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_1,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).build();
        try {
            taskScheduler.scheduleTask(scheduleInfo);
        } catch (InVehicleTaskSchedulerException e) {
            assumeNoException("Assume task schedule to succeed", e);
        }

        taskScheduler.unscheduleAllTasks();
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#scheduleTask",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "unscheduleAllTasks",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testScheduleTask_duplicateScheduleIdMustThrowException() throws Exception {
        setSelfAsServerlessClient();
        assumeTaskSchedulingSupported();

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        // Schedule the task to be executed 30s later.
        long startTimeInEpochSeconds = System.currentTimeMillis() / 1000 + 30;
        ScheduleInfo scheduleInfo = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_1,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).build();
        try {
            taskScheduler.scheduleTask(scheduleInfo);
        } catch (InVehicleTaskSchedulerException e) {
            assumeNoException("Assume task schedule to succeed", e);
        }

        try {
            // Schedule the same task twice must cause IllegalArgumentException.
            assertThrows(IllegalArgumentException.class, () -> taskScheduler.scheduleTask(
                    scheduleInfo));
        } finally {
            taskScheduler.unscheduleAllTasks();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#scheduleTask",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "unscheduleAllTasks",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "isTaskScheduled",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testIsTaskScheduled() throws Exception {
        setSelfAsServerlessClient();
        assumeTaskSchedulingSupported();

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        // Schedule the task to be executed 30s later.
        long startTimeInEpochSeconds = System.currentTimeMillis() / 1000 + 30;
        ScheduleInfo scheduleInfo = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_1,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).build();
        try {
            taskScheduler.scheduleTask(scheduleInfo);
        } catch (InVehicleTaskSchedulerException e) {
            assumeNoException("Assume task schedule to succeed", e);
        }

        try {
            expectWithMessage("isTaskScheduled for scheduled task").that(
                    taskScheduler.isTaskScheduled(TEST_SCHEDULE_ID_1)).isTrue();
            expectWithMessage("isTaskScheduled for unscheduled task").that(
                    taskScheduler.isTaskScheduled(TEST_SCHEDULE_ID_2)).isFalse();
        } finally {
            taskScheduler.unscheduleAllTasks();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#scheduleTask",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "unscheduleAllTasks",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "getAllPendingScheduledTasks",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testGetAllPendingScheduledTasks() throws Exception {
        setSelfAsServerlessClient();
        assumeTaskSchedulingSupported();

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        // Schedule the task to be executed 30s later.
        long startTimeInEpochSeconds = System.currentTimeMillis() / 1000 + 30;
        ScheduleInfo scheduleInfo1 = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_1,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).build();
        ScheduleInfo scheduleInfo2 = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_2,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).setCount(2).setPeriodic(Duration.ofSeconds(30))
                .build();

        try {
            taskScheduler.unscheduleAllTasks();
            taskScheduler.scheduleTask(scheduleInfo1);
            taskScheduler.scheduleTask(scheduleInfo2);
        } catch (InVehicleTaskSchedulerException e) {
            assumeNoException("Assume task schedule to succeed", e);
        }

        try {
            List<ScheduleInfo> scheduleInfo = taskScheduler.getAllPendingScheduledTasks();

            assertWithMessage("Must return two scheduled tasks").that(scheduleInfo).hasSize(2);
            List<String> gotScheduleIds = new ArrayList<>();
            for (int i = 0; i < scheduleInfo.size(); i++) {
                ScheduleInfo info = scheduleInfo.get(i);
                expectWithMessage("Got expected scheduleId").that(info.getScheduleId())
                        .isAnyOf(TEST_SCHEDULE_ID_1, TEST_SCHEDULE_ID_2);
                gotScheduleIds.add(info.getScheduleId());
                expectWithMessage("Got expected task data").that(info.getTaskData()).isEqualTo(
                        TEST_TASK_DATA);
                if (info.getScheduleId().equals(TEST_SCHEDULE_ID_1)) {
                    expectWithMessage("Got expected count").that(info.getCount()).isEqualTo(1);
                    expectWithMessage("Got expected periodic").that(info.getPeriodic()).isEqualTo(
                            Duration.ZERO);
                } else {
                    expectWithMessage("Got expected count").that(info.getCount()).isEqualTo(2);
                    expectWithMessage("Got expected periodic").that(info.getPeriodic()).isEqualTo(
                            Duration.ofSeconds(30));
                }
            }
            expectWithMessage("Got all expected schedule Ids").that(gotScheduleIds).containsExactly(
                    TEST_SCHEDULE_ID_1, TEST_SCHEDULE_ID_2);
        } finally {
            taskScheduler.unscheduleAllTasks();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported",
            "android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#scheduleTask",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "unscheduleAllTasks",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "getAllPendingScheduledTasks",
            "android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler#"
                    + "unscheduleTask",
    })
    @EnsureHasPermission(PERMISSION_CONTROL_REMOTE_ACCESS)
    @RequiresFlagsEnabled({Flags.FLAG_SERVERLESS_REMOTE_ACCESS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testUnscheduleTask() throws Exception {
        setSelfAsServerlessClient();
        assumeTaskSchedulingSupported();

        InVehicleTaskScheduler taskScheduler = mCarRemoteAccessManager.getInVehicleTaskScheduler();
        // Schedule the task to be executed 30s later.
        long startTimeInEpochSeconds = System.currentTimeMillis() / 1000 + 30;
        ScheduleInfo scheduleInfo1 = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_1,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).build();
        ScheduleInfo scheduleInfo2 = new ScheduleInfo.Builder(TEST_SCHEDULE_ID_2,
                CarRemoteAccessManager.TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                .setTaskData(TEST_TASK_DATA).setCount(2).setPeriodic(Duration.ofSeconds(30))
                .build();

        try {
            taskScheduler.unscheduleAllTasks();
            taskScheduler.scheduleTask(scheduleInfo1);
            taskScheduler.scheduleTask(scheduleInfo2);
        } catch (InVehicleTaskSchedulerException e) {
            assumeNoException("Assume task schedule to succeed", e);
        }

        taskScheduler.unscheduleTask(TEST_SCHEDULE_ID_2);

        try {
            List<ScheduleInfo> scheduleInfo = taskScheduler.getAllPendingScheduledTasks();

            assertWithMessage("Must return one scheduled tasks").that(scheduleInfo).hasSize(1);
            ScheduleInfo info = scheduleInfo.get(0);
            expectWithMessage("Got expected scheduleId").that(info.getScheduleId())
                    .isEqualTo(TEST_SCHEDULE_ID_1);
            expectWithMessage("Got expected task data").that(info.getTaskType()).isEqualTo(
                    CarRemoteAccessManager.TASK_TYPE_CUSTOM);
            expectWithMessage("Got expected task data").that(info.getTaskData()).isEqualTo(
                    TEST_TASK_DATA);
            expectWithMessage("Got expected count").that(info.getCount()).isEqualTo(1);
            expectWithMessage("Got expected periodic").that(info.getPeriodic()).isEqualTo(
                    Duration.ZERO);
        } finally {
            taskScheduler.unscheduleAllTasks();
        }
    }

    private static final class RemoteTaskClientCallbackImpl implements RemoteTaskClientCallback {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private RemoteTaskClientRegistrationInfo mInfo;
        @GuardedBy("mLock")
        private boolean mServerlessClientRegistered;

        @Override
        public void onRegistrationUpdated(@NonNull RemoteTaskClientRegistrationInfo info) {
            synchronized (mLock) {
                mInfo = info;
            }
        }

        @Override
        public void onServerlessClientRegistered() {
            synchronized (mLock) {
                mServerlessClientRegistered = true;
            }
        }

        @Override
        public void onRegistrationFailed() {
        }

        @Override
        public void onRemoteTaskRequested(@NonNull String taskId, @Nullable byte[] data,
                int taskMaxDurationInSec) {
        }

        @Override
        public void onShutdownStarting(@NonNull CompletableRemoteTaskFuture future) {
        }

        public String getServiceId() {
            synchronized (mLock) {
                if (mInfo == null) {
                    return null;
                }
                return mInfo.getVehicleId();
            }
        }

        public String getVehicleId() {

            synchronized (mLock) {
                if (mInfo == null) {
                    return null;
                }
                return mInfo.getVehicleId();
            }
        }

        public String getProcessorId() {
            synchronized (mLock) {
                if (mInfo == null) {
                    return null;
                }
                return mInfo.getProcessorId();
            }
        }

        public String getClientId() {
            synchronized (mLock) {
                if (mInfo == null) {
                    return null;
                }
                return mInfo.getClientId();
            }
        }

        public boolean isServerlessClientRegistered() {
            synchronized (mLock) {
                return mServerlessClientRegistered;
            }
        }
    }

    private boolean isTestPkgServerlessClient() {
        try {
            CarRemoteAccessDumpProto dump = ProtoUtils.getProto(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    CarRemoteAccessDumpProto.class, DUMP_COMMAND);

            for (int i = 0; i < dump.getServerlessClientsCount(); i++) {
                ServerlessClientInfo serverlessClientInfo = dump.getServerlessClients(i);
                if (serverlessClientInfo.getPackageName().equals(mContext.getPackageName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check whether this test is a serverless remote task client"
                    + ", default to false", e);
        }

        return false;
    }

    private void setSelfAsServerlessClient() {
        try {
            mCarRemoteAccessManager.addServerlessRemoteTaskClient(mPackageName,
                    SERVERLESS_CLIENT_ID);
            mServerlessRemoteTaskClientSet = true;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "failed to call addServerlessRemoteTaskClient, maybe the test pkg is "
                    + "already a serverless remote task client?", e);
        }

        assertWithMessage(
                "This test requires the test package to be a serverless remote task client").that(
                isTestPkgServerlessClient()).isTrue();
    }

    private void assumeTaskSchedulingSupported() {
        assumeTrue("Task scheduling is not supported, skipping the test",
                mCarRemoteAccessManager.isTaskScheduleSupported());
    }
}
