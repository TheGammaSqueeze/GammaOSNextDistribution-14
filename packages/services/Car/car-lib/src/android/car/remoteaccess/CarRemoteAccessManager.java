/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.car.feature.Flags.FLAG_SERVERLESS_REMOTE_ACCESS;

import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;

import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.car.internal.ICarBase;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * CarRemoteAccessManager allows applications to listen to remote task requests even while Android
 * System is not running.
 *
 * <p>The remote task client registers to {@link CarRemoteAccessManager} to listen to remote access
 * events. At {@link RemoteTaskClientCallback#onRegistrationUpdated} it is required to share
 * {@code serviceId}, {@code deviceId} and {@code clientId} with the cloud service which will use
 * the IDs to wake the vehicle. At {@link RemoteTaskClientCallback#onRemoteTaskRequested}, it starts
 * executing the given task. It is supposed to call {@link #reportRemoteTaskDone(String)} when it
 * finishes the given task. Once the task completion is reported or the timeout expires, Android
 * System goes back to either the previous power state or the specified power state.
 *
 * <p>Note that the remote task will be executed even when the vehicle is actively in use, not
 * nessarily when the vehicle is off. Remote task client must make sure the task to be executed
 * will not affect the system performance or affect driving safety. If certain task must be
 * executed while the vehicle is off, the remote task client must check VHAL property
 * {@code VEHICLE_IN_USE} and/or check igntition state via {@code VehicleIgnitionState}.
 *
 * <p>Note: all remote task clients must run as system user.
 *
 * <p>A serverless setup might also be supported if the RRO overlay for
 * remote_access_serverless_client_map exists which provides a map from serverless client ID to
 * their package names.
 *
 * <p>Here the term 'remote' refers to the source of task coming from outside of the
 * Android system, it does not necessarily means the task comes from Internet. In the serverless
 * setup, no cloud service is required. Another device within the same vehicle, but outside the
 * Android system is the issuer for the remote task.
 *
 * <p>For serverless setup, there is a pre-configured set of serverless remote task clients. They
 * register to {@link CarRemoteAccessManager} to listen to remote access events.
 * {@link RemoteTaskClientCallback#onServerlessClientRegistered} will be called instead of
 * {@link RemoteTaskClientCallback#onRegistrationUpdated} and there is no cloud service involved.
 * {@link RemoteTaskClientCallback#onRemoteTaskRequested} will be invoked when the task is to be
 * executed. It is supposed to call {@link #reportRemoteTaskDone(String)} when it
 * finishes the given task. Once the task completion is reported or the timeout expires, Android
 * system goes back to either the previous power state or the specified power state.
 *
 * <p>For serverless setup, if {@link isTaskScheduleSupported} returns {@code true}, client may
 * use {@link InVehicleTaskScheduler#scheduleTask} to schedule a remote task to be executed later.
 * If {@link isTaskScheduleSupported} returns {@code false}, it is assumed there exists some other
 * channel outside of the Android system for task scheduling.
 */
public final class CarRemoteAccessManager extends CarManagerBase {

    private static final String TAG = CarRemoteAccessManager.class.getSimpleName();

    private final InVehicleTaskScheduler mInVehicleTaskScheduler = new InVehicleTaskScheduler();

    /**
     * The system remains ON after completing the remote tasks.
     *
     * @hide
     */
    @SystemApi
    public static final int NEXT_POWER_STATE_ON = 1;

    /**
     * The system shuts down to power off after completing the remote tasks.
     *
     * @hide
     */
    @SystemApi
    public static final int NEXT_POWER_STATE_OFF = 2;

    /**
     * The system goes into deep sleep after completing the remote tasks.
     *
     * @hide
     */
    @SystemApi
    public static final int NEXT_POWER_STATE_SUSPEND_TO_RAM = 3;

    /**
     * The system goes into hibernation after completing the remote tasks.
     *
     * @hide
     */
    @SystemApi
    public static final int NEXT_POWER_STATE_SUSPEND_TO_DISK = 4;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "NEXT_POWER_STATE_", value = {
            NEXT_POWER_STATE_ON,
            NEXT_POWER_STATE_OFF,
            NEXT_POWER_STATE_SUSPEND_TO_RAM,
            NEXT_POWER_STATE_SUSPEND_TO_DISK,
    })
    @Target({ElementType.TYPE_USE})
    public @interface NextPowerState {}

    /**
     * Custom task. The task data is opaque to framework.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    public static final int TASK_TYPE_CUSTOM = 0;

    /**
     * Schedule to enter garage mode if the vehicle is off.
     *
     * taskData is ignore for this type.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    public static final int TASK_TYPE_ENTER_GARAGE_MODE = 1;

    /** @hide */
    @IntDef(prefix = {"TASK_TYPE_"}, value = {
            TASK_TYPE_CUSTOM,
            TASK_TYPE_ENTER_GARAGE_MODE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TaskType {}

    private final ICarRemoteAccessService mService;
    private final Object mLock = new Object();
    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    /**
     * Sets fake feature flag for unit testing.
     *
     * @hide
     */
    @VisibleForTesting
    public void setFeatureFlags(FeatureFlags fakeFeatureFlags) {
        mFeatureFlags = fakeFeatureFlags;
    }

    private final class CarRemoteAccessCallback extends ICarRemoteAccessCallback.Stub {
        @Override
        public void onClientRegistrationUpdated(RemoteTaskClientRegistrationInfo registrationInfo) {
            RemoteTaskClientCallback callback;
            Executor executor;
            synchronized (mLock) {
                if (mRemoteTaskClientCallback == null || mExecutor == null) {
                    Log.w(TAG, "Cannot call onRegistrationUpdated because no remote task client "
                            + "is registered");
                    return;
                }
                mCurrentClientId = registrationInfo.getClientId();
                callback = mRemoteTaskClientCallback;
                executor = mExecutor;
            }
            Binder.clearCallingIdentity();
            executor.execute(() -> callback.onRegistrationUpdated(registrationInfo));
        }

        @Override
        public void onServerlessClientRegistered(String clientId) {
            RemoteTaskClientCallback callback;
            Executor executor;
            synchronized (mLock) {
                if (mRemoteTaskClientCallback == null || mExecutor == null) {
                    Log.w(TAG, "Cannot call onRegistrationUpdated because no remote task client "
                            + "is registered");
                    return;
                }
                mCurrentClientId = clientId;
                callback = mRemoteTaskClientCallback;
                executor = mExecutor;
            }
            if (mFeatureFlags.serverlessRemoteAccess()) {
                Binder.clearCallingIdentity();
                executor.execute(() -> callback.onServerlessClientRegistered());
            } else {
                Log.e(TAG, "Serverless remote access flag is not enabled, "
                        + "the callback must not be called");
            }
        }

        @Override
        public void onClientRegistrationFailed() {
            RemoteTaskClientCallback callback;
            Executor executor;
            synchronized (mLock) {
                if (mRemoteTaskClientCallback == null || mExecutor == null) {
                    Log.w(TAG, "Cannot call onRegistrationFailed because no remote task client "
                            + "is registered");
                    return;
                }
                callback = mRemoteTaskClientCallback;
                executor = mExecutor;
            }
            Binder.clearCallingIdentity();
            executor.execute(() -> callback.onRegistrationFailed());
        }

        @Override
        public void onRemoteTaskRequested(String clientId, String taskId, byte[] data,
                int taskMaxDurationInSec) {
            RemoteTaskClientCallback callback;
            Executor executor;
            synchronized (mLock) {
                if (mCurrentClientId == null || !mCurrentClientId.equals(clientId)) {
                    Log.w(TAG, "Received a task for a mismatched client ID(" + clientId
                            + "): the current client ID = " + mCurrentClientId);
                    return;
                }
                callback = mRemoteTaskClientCallback;
                executor = mExecutor;
            }
            if (callback == null || executor == null) {
                Log.w(TAG, "Cannot call onRemoteTaskRequested because no remote task client is "
                        + "registered");
                return;
            }
            Binder.clearCallingIdentity();
            executor.execute(() -> callback.onRemoteTaskRequested(taskId, data,
                    taskMaxDurationInSec));
        }

        @Override
        public void onShutdownStarting() {
            String clientId;
            RemoteTaskClientCallback callback;
            Executor executor;
            synchronized (mLock) {
                clientId = mCurrentClientId;
                callback = mRemoteTaskClientCallback;
                executor = mExecutor;
            }
            if (clientId == null || callback == null || executor == null) {
                Log.w(TAG, "Cannot call onShutdownStarting because no remote task client is "
                        + "registered");
                return;
            }
            Binder.clearCallingIdentity();
            executor.execute(() ->
                    callback.onShutdownStarting(new MyCompletableRemoteTaskFuture(clientId)));
        }
    }

    private final ICarRemoteAccessCallback mCarRemoteAccessCallback = new CarRemoteAccessCallback();

    @GuardedBy("mLock")
    private RemoteTaskClientCallback mRemoteTaskClientCallback;
    @GuardedBy("mLock")
    private Executor mExecutor;
    @GuardedBy("mLock")
    private String mCurrentClientId;

    /**
     * An interface passed from {@link RemoteTaskClientCallback}.
     *
     * <p>The remote task client uses this interface to tell {@link CarRemoteAccessManager} that it
     * finalized the pending remote tasks.
     */
    public interface CompletableRemoteTaskFuture {
        /**
         * Tells {@link CarRemoteAccessManager} that the remote task client finalized the pending
         * remoate tasks.
         */
        void complete();
    }

    private final class MyCompletableRemoteTaskFuture implements CompletableRemoteTaskFuture {
        private final String mClientIdToComplete;

        MyCompletableRemoteTaskFuture(String clientId) {
            mClientIdToComplete = clientId;
        }

        @Override
        public void complete() {
            try {
                mService.confirmReadyForShutdown(mClientIdToComplete);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Listener for remote task events.
     */
    public interface RemoteTaskClientCallback {
        /**
         * This is called when the remote task client is successfully registered or the client ID is
         * updated by AAOS.
         *
         * <p>For a serverless remote task client, the {@link onServerlessClientRegistered} will be
         * called instead of this.
         *
         * @param info {@link RemoteTaskClientRegistrationInfo} which contains wake-up service ID,
         *             vehicle ID, processor ID and client ID.
         */
        void onRegistrationUpdated(@NonNull RemoteTaskClientRegistrationInfo info);

        /**
         * This is called when a pre-configured serverless remote task client is registered.
         *
         * <p>The serverless remote task client is configured via including a runtime config file
         * at {@code /vendor/etc/}
         */
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        default void onServerlessClientRegistered() {
            Log.i(TAG, "onServerlessClientRegistered called");
        }

        /**
         * This is called when registering the remote task client fails.
         */
        void onRegistrationFailed();

        /**
         * This is called when a wake-up request is received/processed.
         *
         * @param taskId ID of the task that is requested by the remote task server.
         * @param data Extra data passed along with the wake-up request.
         * @param taskMaxDurationInSec The timeout before AAOS goes back to the previous power
         *                             state.
         */
        void onRemoteTaskRequested(@NonNull String taskId, @Nullable byte[] data,
                int taskMaxDurationInSec);

        /**
         * This is called when the device is about to shutdown.
         *
         * <p>The remote task client should finalize the ongoing tasks, if any, and complete the
         * given future within 5 seconds. After the given timeout, the Android system will shutdown,
         * anyway.
         *
         * @param future {@link CompletableRemoteTaskFuture} used by the remote task client to
         *               notify CarRemoteAccessManager that all pending remote tasks are finalized.
         */
        void onShutdownStarting(@NonNull CompletableRemoteTaskFuture future);
    }

    /** @hide */
    public CarRemoteAccessManager(ICarBase car, IBinder service) {
        super(car);
        mService = ICarRemoteAccessService.Stub.asInterface(service);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // Nothing to do.
    }

    /**
     * Sets the remote task client represented as {@link RemoteTaskClientCallback}.
     *
     * @param executor Executor on which {@code callback} is executed.
     * @param callback {@link RemoteTaskClientCallback} that listens to remote task events.
     * @throws IllegalStateException When a remote task client is already set.
     * @throws IllegalArgumentException When the given callback or the executor is {@code null}.
     */
    @RequiresPermission(Car.PERMISSION_USE_REMOTE_ACCESS)
    public void setRemoteTaskClient(@NonNull @CallbackExecutor Executor executor,
            @NonNull RemoteTaskClientCallback callback) {
        Preconditions.checkArgument(executor != null, "Executor cannot be null");
        Preconditions.checkArgument(callback != null, "Callback cannot be null");

        synchronized (mLock) {
            if (mRemoteTaskClientCallback != null) {
                throw new IllegalStateException("Remote task client must be cleared first");
            }
            mRemoteTaskClientCallback = callback;
            mExecutor = executor;
        }

        try {
            mService.addCarRemoteTaskClient(mCarRemoteAccessCallback);
        } catch (RemoteException e) {
            synchronized (mLock) {
                mRemoteTaskClientCallback = null;
                mExecutor = null;
            }
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Clears the remote task client previously set via {@link #setRemoteTaskClient(Executor,
     * RemoteTaskClientCallback)}.
     *
     * <p>After the remote task client is cleared, all tasks associated with the previous client
     * will not be delivered and the client must not call {@code reportRemoteTaskDone} with the
     * task ID associated with the previous client ID.
     *
     * @throws IllegalStateException if {@code callback} is not registered.
     */
    @RequiresPermission(Car.PERMISSION_USE_REMOTE_ACCESS)
    public void clearRemoteTaskClient() {
        synchronized (mLock) {
            if (mRemoteTaskClientCallback == null) {
                Log.w(TAG, "No registered remote task client to clear");
                return;
            }
            mRemoteTaskClientCallback = null;
            mExecutor = null;
            mCurrentClientId = null;
        }
        try {
            mService.removeCarRemoteTaskClient(mCarRemoteAccessCallback);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Reports that remote task execution is completed, so that the vehicle will go back to the
     * power state before the wake-up.
     *
     * @param taskId ID of the remote task which has been completed.
     * @throws IllegalArgumentException If {@code taskId} is null.
     * @throws IllegalStateException If the remote task client is not registered or not woken up.
     */
    @RequiresPermission(Car.PERMISSION_USE_REMOTE_ACCESS)
    public void reportRemoteTaskDone(@NonNull String taskId) {
        Preconditions.checkArgument(taskId != null, "Task ID cannot be null");

        String currentClientId;
        synchronized (mLock) {
            if (mCurrentClientId == null) {
                Log.w(TAG, "Failed to report remote task completion: no remote task client is "
                        + "registered");
                throw new IllegalStateException("No remote task client is registered");
            }
            currentClientId = mCurrentClientId;
        }
        try {
            mService.reportRemoteTaskDone(currentClientId, taskId);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Task ID(" + taskId + ") is not valid", e);
            throw e;
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Sets the power state after all the remote tasks are completed.
     *
     * <p>By default, the system returns to the previous power state from which the system woke up.
     * If the given power state is {@code NEXT_POWER_STATE_ON}, Garage Mode is not executed.
     *
     * @param nextPowerState The next power state after the remote task is completed.
     * @param runGarageMode Whether to run Garage Mode when switching to the next power state.
     * @throws IllegalArgumentException If {@code nextPowerState} is not valid.
     * @throws IllegalStateException If the remote task client is not registered or not woken up.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
    public void setPowerStatePostTaskExecution(@NextPowerState int nextPowerState,
            boolean runGarageMode) {
        try {
            mService.setPowerStatePostTaskExecution(nextPowerState, runGarageMode);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns whether task scheduling is supported.
     *
     * <p>If this returns {@code true}, user may use
     * {@link InVehicleTaskScheduler#scheduleTask} to schedule a task to be executed at a later
     * time. If the device is off when the task is scheduled to be executed, the device will be
     * woken up to execute the task.
     *
     * @return {@code true} if serverless remote task scheduling is supported by the HAL and the
     *      caller is a pre-configured serverless remote task client.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
    public boolean isTaskScheduleSupported() {
        try {
            return mService.isTaskScheduleSupported();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Gets a in vehicle task scheduler that can be used to schedule a task to be executed later.
     *
     * <p>This is only supported for pre-configured remote task serverless clients.
     *
     * <p>See {@link InVehicleTaskScheduler.scheduleTask} for usage.
     *
     * @return An in vehicle task scheduler or {@code null} if {@link isTaskScheduleSupported} is
     *      {@code false}.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
    @Nullable
    public InVehicleTaskScheduler getInVehicleTaskScheduler() {
        if (!isTaskScheduleSupported()) {
            Log.w(TAG, "getInVehicleTaskScheduler: Task schedule is not supported, return null");
            return null;
        }
        return mInVehicleTaskScheduler;
    }

    /**
     * For testing only. Adds a package as a new serverless remote task client.
     *
     * @param packageName The package name for the serverless remote task client. This should be a
     *      test package name.
     * @param clientId An arbitrary client ID picked for the client. Client should add some test
     *      identifier to the ID to avoid conflict with an existing real client ID.
     * @throws IllegalArgumentException If the packageName is already an serverless remote task
     *      client or if the client ID is already used.
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
    public void addServerlessRemoteTaskClient(@NonNull String packageName,
            @NonNull String clientId) {
        try {
            mService.addServerlessRemoteTaskClient(packageName, clientId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * For testing only. Removes a package as serverless remote task client.
     *
     * @param packageName The package name for the previously added test serverless remote task
     *      client.
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
    public void removeServerlessRemoteTaskClient(@NonNull String packageName) {
        try {
            mService.removeServerlessRemoteTaskClient(packageName);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * The schedule information, which contains the schedule ID, the task data, the scheduling
     * start time, frequency and counts.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    public static final class ScheduleInfo {
        @NonNull
        public static final Duration PERIODIC_DAILY = Duration.ofDays(1);
        @NonNull
        public static final Duration PERIODIC_WEEKLY = Duration.ofDays(7);

        /**
         * The builder for {@link ScheduleInfo}.
         */
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        public static final class Builder {
            private String mScheduleId;
            private @TaskType int mTaskType;
            private byte[] mTaskData = new byte[0];
            // By default the task is to be executed once.
            private int mCount = 1;
            private long mStartTimeInEpochSeconds;
            private Duration mPeriodic = Duration.ZERO;
            private boolean mBuilderUsed;

            /**
             * Creates the builder for {@link ScheduleInfo}.
             *
             * <p>By default the task will be executed once at the {@code startTime}.
             *
             * <p>If {@code count} is not 1, the task will be executed multiple times at
             * {@code startTime}, {@code startTime + periodic}, {@code startTime + periodic * 2}
             * etc.
             *
             * <p>Note that all tasks scheduled in the past will not be executed. E.g. if the
             * {@code startTime} is in the past and task is scheduled to be executed once, the task
             * will not be executed. If the {@code startTime} is 6:00 am, the {@code periodic} is
             * 1h, the {@code count} is 3, and the current time is 7:30 am. The first two scheduled
             * time: 6:00am, 7:00am already past, so the task will only be executed once at 8:00am.
             *
             * <p>Note that {@code startTime} is an eopch time not containing time zone info.
             * Changing the timezone will not affect the scheduling.
             *
             * <p>If the client always want the task to be executed at 3:00pm relative to the
             * current Android timezone, the client must listen to {@code ACTION_TIMEZONE_CHANGED}.
             * It must unschedule and reschedule the task when time zone is changed.
             *
             * <p>It is expected that the other device has the same time concept as Android.
             * Optionally, the VHAL property {@code EPOCH_TIME} can be used to sync the time.
             *
             * @param scheduleId A unique ID to identify this scheduling. Must be unique among all
             *      pending schedules for this client.
             * @param mStartTimeInEpochSeconds When the task is scheduled to be executed in epoch
             *      seconds. It is not guaranteed that the task will be executed exactly at this
             *      time (or be executed at all). Typically the task will be executed at a time
             *      slightly later than the scheduled time due to the time spent waking up Android
             *      system and starting the remote task client.
             */
            @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
            public Builder(@NonNull String scheduleId, @TaskType int taskType,
                    long startTimeInEpochSeconds) {
                Preconditions.checkArgument(scheduleId != null, "scheduleId must not be null");
                Preconditions.checkArgument(
                        taskType == TASK_TYPE_CUSTOM || taskType == TASK_TYPE_ENTER_GARAGE_MODE,
                        "unsupported task type: " + taskType);
                Preconditions.checkArgument(startTimeInEpochSeconds > 0,
                        "startTimeInEpochSeconds must > 0");
                mScheduleId = scheduleId;
                mTaskType = taskType;
                mStartTimeInEpochSeconds = startTimeInEpochSeconds;
            }

            /**
             * Sets the task data.
             *
             * @param taskData The opaque task data that will be sent back via
             *      {@link onRemoteTaskRequested} when the task is to be executed. This field is
             *      ignored if task type is ENTER_GARAGE_MODE. If this is not set, task data is
             *      empty.
             */
            @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
            @NonNull
            public Builder setTaskData(@NonNull byte[] taskData) {
                Preconditions.checkArgument(taskData != null, "taskData must not be null");
                mTaskData = taskData.clone();
                return this;
            }

            /**
             * Sets how many times the task is scheduled to be executed.
             *
             * <p>Note that if {@code startTime} is in the past, the actual task execution count
             * might be lower than the specified value since all the scheduled tasks in the past
             * will be ignored.
             *
             * @param count How many times the task is scheduled to be executed. A special value of
             *      0 means the count is infinite.
             * @return the builder.
             */
            @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
            @NonNull
            public Builder setCount(int count) {
                Preconditions.checkArgument(count >= 0, "count must not be negative");
                mCount = count;
                return this;
            }

            /**
             * Sets the interval between two scheduled tasks.
             *
             * <p>This is ignored if {@code count} is 1.
             *
             * @param periodic The interval between two scheduled tasks. Can be
             *      {@link PERIODIC_DAILY} or {@link PERIODIC_WEEKLY} or any custom interval.
             * @return the builder.
             */
            @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
            @NonNull
            public Builder setPeriodic(@NonNull Duration periodic) {
                Preconditions.checkArgument(periodic != null, "periodic must not be null");
                Preconditions.checkArgument(!periodic.isNegative(),
                        "periodic must not be negative");
                mPeriodic = periodic;
                return this;
            }

            /**
             * Builds the {@link ScheduleInfo}.
             */
            @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
            @NonNull
            public ScheduleInfo build() {
                if (mBuilderUsed) {
                    throw new IllegalStateException(
                            "build is only supposed to be called once on one builder, use a new "
                            + "builder instance instead");
                }
                mBuilderUsed = true;
                if (mCount == 1) {
                    mPeriodic = Duration.ZERO;
                }
                return new ScheduleInfo(this);
            }
        };

        private String mScheduleId;
        private @TaskType int mTaskType;
        private byte[] mTaskData;
        private int mCount;
        private long mStartTimeInEpochSeconds;
        private Duration mPeriodic;

        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @NonNull
        public String getScheduleId() {
            return mScheduleId;
        }

        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        public @TaskType int getTaskType() {
            return mTaskType;
        }

        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @NonNull
        public byte[] getTaskData() {
            return mTaskData;
        }

        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        public int getCount() {
            return mCount;
        }

        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        public long getStartTimeInEpochSeconds() {
            return mStartTimeInEpochSeconds;
        }

        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @NonNull
        public Duration getPeriodic() {
            return mPeriodic;
        }

        private ScheduleInfo(Builder builder) {
            mScheduleId = builder.mScheduleId;
            mTaskType = builder.mTaskType;
            if (builder.mTaskData != null) {
                mTaskData = builder.mTaskData;
            } else {
                mTaskData = new byte[0];
            }
            mCount = builder.mCount;
            mStartTimeInEpochSeconds = builder.mStartTimeInEpochSeconds;
            mPeriodic = builder.mPeriodic;
        }
    };

    /**
     * Exception that might be thrown by {@link InVehicleTaskScheduler} methods.
     *
     * <p>This indicates that something is wrong while communicating with the external device
     * which is responsible for managing task schedule or the external device reports some error.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    public static final class InVehicleTaskSchedulerException extends Exception {
        InVehicleTaskSchedulerException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * A scheduler for scheduling a task to be executed later.
     *
     * <p>It schedules a task via sending a scheduled task message to a device in the same vehicle,
     * but external to Android.
     *
     * <p>This is only supported for pre-configured remote task serverless clients.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
    public final class InVehicleTaskScheduler {
        /**
         * Please use {@link getInVehicleTaskScheduler} to create.
         */
        private InVehicleTaskScheduler() {}

        /**
         * Gets supported schedule task type.
         *
         * @return a list of supported task types as defined by {@link TaskType}.
         * @throws InVehicleTaskSchedulerException if unable to get supported schedule task type.
         *
         * @hide
         */
        @SystemApi
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
        public @NonNull int[] getSupportedTaskTypes() throws InVehicleTaskSchedulerException {
            try {
                return mService.getSupportedTaskTypesForScheduling();
            } catch (RemoteException e) {
                return handleRemoteExceptionFromCarService(e, EMPTY_INT_ARRAY);
            } catch (ServiceSpecificException e) {
                throw new InVehicleTaskSchedulerException(e);
            }
        }

        /**
         * Schedules a task to be executed later even when the vehicle is off.
         *
         * <p>This sends a scheduled task message to a device external to Android so that the device
         * can wake up Android and deliver the task through {@code onRemoteTaskRequested}.
         *
         * <p>Note that the remote task will be executed even when the vehicle is in use. The task
         * must not affect the system performance or driving safety.
         *
         * <p>Note that the scheduled task execution is on a best-effort basis. Multiple situations
         * might cause the task not to execute successfully:
         *
         * <ul>
         * <li>The vehicle is low on battery and the other device decides not to wake up Android.
         * <li>User turns off vehicle while the task is executing.
         * <li>The task logic itself fails.
         *
         * <p>The framework does not provide a mechanism to report the task status or task result.
         * In order for the user to check whether the task is executed successfully, the client must
         * record in its persistent storage the task's status/result during
         * {@code onRemoteTaskRequested}.
         *
         * <p>For example, if the scheduled task is to update some of the application's resources.
         * When the application is opened by the user, it may check whether the resource is the
         * latest version, if not, it may check whether a scheduled update task exists. If not, it
         * may schedule a new task at midnight at the same day to update the resource. The
         * application uses the persistent resource version to decide whether the previous update
         * succeeded.
         *
         * <p>All the tasks for this client will be unscheduled if the client app is removed.
         *
         * <p>If the client app is updated, the task will still be scheduled, so the client app must
         * make sure the logic handling remote task is backward compatible.
         *
         * <p>It is possible that the client app's persistent data might be removed, e.g. during
         * a factory reset. If the client app stores task-related information, it will be lost. The
         * scheduled task will still be delivered to the app as expected unless client explicitly
         * unschedule all the scheduled tasks when it detects that the data is erased.
         *
         * @param scheduleInfo The schedule information.
         *
         * @throws IllegalArgumentException if a pending schedule with the same {@code scheduleId}
         *      for this client exists.
         * @throws InVehicleTaskSchedulerException if unable to schedule the task.
         *
         * @hide
         */
        @SystemApi
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
        public void scheduleTask(@NonNull ScheduleInfo scheduleInfo)
                throws InVehicleTaskSchedulerException {
            Preconditions.checkArgument(scheduleInfo != null, "scheduleInfo cannot be null");
            TaskScheduleInfo taskScheduleInfo = toTaskScheduleInfo(scheduleInfo);
            try {
                mService.scheduleTask(taskScheduleInfo);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            } catch (ServiceSpecificException e) {
                throw new InVehicleTaskSchedulerException(e);
            }
        }

        /**
         * Unschedules a scheduled task.
         *
         * @param scheduleId The ID for the schedule.
         *
         * <p>Does nothing if a pending schedule with {@code scheduleId} does not exist.
         *
         * @throws InVehicleTaskSchedulerException if failed to unschedule the tasks.
         *
         * @hide
         */
        @SystemApi
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
        public void unscheduleTask(@NonNull String scheduleId)
                throws InVehicleTaskSchedulerException {
            Preconditions.checkArgument(scheduleId != null, "scheduleId cannot be null");
            try {
                mService.unscheduleTask(scheduleId);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            } catch (ServiceSpecificException e) {
                throw new InVehicleTaskSchedulerException(e);
            }
        }

        /**
         * Unschedules all scheduled tasks for this client.
         *
         * @hide
         *
         * @throws InVehicleTaskSchedulerException if failed to unschedule the tasks.
         */
        @SystemApi
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
        public void unscheduleAllTasks() throws InVehicleTaskSchedulerException {
            try {
                mService.unscheduleAllTasks();
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            } catch (ServiceSpecificException e) {
                throw new InVehicleTaskSchedulerException(e);
            }
        }

        /**
         * Returns whether the specified task is scheduled.
         *
         * @param scheduleId The ID for the schedule.
         * @return {@code true} if the task was scheduled and pending to be executed.
         * @throws InVehicleTaskSchedulerException if failed to check whether the task is scheduled.
         *
         * @hide
         */
        @SystemApi
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
        public boolean isTaskScheduled(@NonNull String scheduleId)
                throws InVehicleTaskSchedulerException {
            Preconditions.checkArgument(scheduleId != null, "scheduleId cannot be null");
            try {
                return mService.isTaskScheduled(scheduleId);
            } catch (RemoteException e) {
                return handleRemoteExceptionFromCarService(e, false);
            } catch (ServiceSpecificException e) {
                throw new InVehicleTaskSchedulerException(e);
            }
        }

        /**
         * Gets all pending scheduled tasks for this client.
         *
         * <p>The finished scheduled tasks will not be included.
         *
         * @return A list of schedule info.
         *
         * @throws InVehicleTaskSchedulerException if unable to get the scheduled tasks.
         *
         * @hide
         */
        @SystemApi
        @FlaggedApi(FLAG_SERVERLESS_REMOTE_ACCESS)
        @RequiresPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS)
        @NonNull
        public List<ScheduleInfo> getAllPendingScheduledTasks()
                throws InVehicleTaskSchedulerException {
            List<ScheduleInfo> scheduleInfoList = new ArrayList<>();
            try {
                List<TaskScheduleInfo> taskScheduleInfoList =
                        mService.getAllPendingScheduledTasks();
                for (int i = 0; i < taskScheduleInfoList.size(); i++) {
                    scheduleInfoList.add(fromTaskScheduleInfo(taskScheduleInfoList.get(i)));
                }
                return scheduleInfoList;
            } catch (RemoteException e) {
                return handleRemoteExceptionFromCarService(e, scheduleInfoList);
            } catch (ServiceSpecificException e) {
                throw new InVehicleTaskSchedulerException(e);
            }
        }

        private static ScheduleInfo fromTaskScheduleInfo(TaskScheduleInfo taskScheduleInfo) {
            return new ScheduleInfo.Builder(taskScheduleInfo.scheduleId,
                    taskScheduleInfo.taskType, taskScheduleInfo.startTimeInEpochSeconds)
                    .setTaskData(taskScheduleInfo.taskData).setCount(taskScheduleInfo.count)
                    .setPeriodic(Duration.ofSeconds(taskScheduleInfo.periodicInSeconds)).build();
        }

        private static TaskScheduleInfo toTaskScheduleInfo(ScheduleInfo scheduleInfo) {
            TaskScheduleInfo taskScheduleInfo = new TaskScheduleInfo();
            taskScheduleInfo.taskType = scheduleInfo.getTaskType();
            taskScheduleInfo.scheduleId = scheduleInfo.getScheduleId();
            taskScheduleInfo.taskData = scheduleInfo.getTaskData();
            taskScheduleInfo.count = scheduleInfo.getCount();
            taskScheduleInfo.startTimeInEpochSeconds = scheduleInfo.getStartTimeInEpochSeconds();
            taskScheduleInfo.periodicInSeconds = scheduleInfo.getPeriodic().getSeconds();
            return taskScheduleInfo;
        }
    }
}
