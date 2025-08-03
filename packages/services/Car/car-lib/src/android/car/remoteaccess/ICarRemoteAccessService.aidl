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

import android.car.remoteaccess.ICarRemoteAccessCallback;
import android.car.remoteaccess.TaskScheduleInfo;

/** @hide */
interface ICarRemoteAccessService {
    /**
     * General service error code.
     */
    const int SERVICE_ERROR_CODE_GENERAL = 1;

    /**
     * Adds the remote task client represented as {@link ICarRemoteAccessCallback} to listen to
     * remote access related events.
     */
    void addCarRemoteTaskClient(in ICarRemoteAccessCallback callback);

    /**
     * Removes the remote task client represented as {@link ICarRemoteAccessCallback} from
     * CarRemoteAccessService.
     */
    void removeCarRemoteTaskClient(in ICarRemoteAccessCallback callback);

    /**
     * Tells CarRemoteAccessService that the remote task is completed.
     *
     * @param clientId ID of the remote task client.
     */
    void reportRemoteTaskDone(in String clientId, in String taskId);

    /**
     * Sets the power state after all the remote tasks are completed.
     *
     * @param nextPowerState The next power state.
     * @param runGarageMode Whether to run GarageMode.
     */
    void setPowerStatePostTaskExecution(int nextPowerState, boolean runGarageMode);

    /**
     * Tells CarRemoteAccessService that the remote task client is ready for shutdown.
     *
     * <p>After the allowed delay(= 5s), CarRemoteAccessService moves on to shutdown the system
     * even without this confirmation.
     *
     * @param clientId ID of the remote task client.
     */
    void confirmReadyForShutdown(in String clientId);

    /**
     * Returns whether task scheduling is supported.
     */
    boolean isTaskScheduleSupported();

    /**
     * Schedules a task to be executed later even when the vehicle is off.
     *
     * <p>This sends a scheduled task message to a device external to Android so that the device
     * can wake up Android and deliver the task through {@code ICarRemoteAccessCallback}.
     */
    void scheduleTask(in TaskScheduleInfo scheduleInfo);

    /**
     * Unschedules a scheduled task.
     */
    void unscheduleTask(in String scheduleId);

    /**
     * Unschedules all scheduled tasks for this client.
     */
    void unscheduleAllTasks();

    /**
     * Returns whether the specified task is scheduled.
     */
    boolean isTaskScheduled(in String scheduleId);

    /**
     * Gets all pending scheduled tasks for this client.
     */
    List<TaskScheduleInfo> getAllPendingScheduledTasks();

    /**
     * Gets the supported task types for scheduling.
     *
     * <p>If task scheduling is not supported, this returns an empty array.
     *
     * <p>Otherwise, this returns a list of {@code CarRemoteAccessManager.TaskType}.
     *
     * <p>{@code CarRemoteAccessManager.TASK_TYPE_CUSTOM} must be supported.
     */
    int[] getSupportedTaskTypesForScheduling();

    /**
     * For testing only. Add a package as a new serverless remote access client.
     */
    void addServerlessRemoteTaskClient(in String packageName, in String clientId);

    /**
     * For testing only. Remove a package as serverless remote access client.
     */
    void removeServerlessRemoteTaskClient(in String packageName);
}
