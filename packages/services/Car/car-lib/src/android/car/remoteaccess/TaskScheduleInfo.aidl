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

package android.car.remoteaccess;

/**
 * The schedule info for a remote task.
 *
 * @hide
 */
@JavaDerive(equals = true, toString = true)
parcelable TaskScheduleInfo {
    // The unique ID for this schedule.
    String scheduleId;
    // The task type, one of CarRemoteAccessManager.TaskType.
    int taskType;
    // The task data.
    byte[] taskData;
    // How many times the task is scheduled to be executed. 0 means infinite.
    int count;
    // The start time in epoch seconds;
    long startTimeInEpochSeconds;
    // The interval (in seconds) between scheduled task executions.
    long periodicInSeconds;
}
