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

package android.car.app;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ActivityManager;

/**
 * A callback interface for {@link ControlledRemoteCarTaskView}.
 *
 * @hide
 */
@SystemApi
public interface ControlledRemoteCarTaskViewCallback
        extends RemoteCarTaskViewCallback<ControlledRemoteCarTaskView> {
    /**
     * Called when the underlying {@link RemoteCarTaskView} instance is created.
     *
     * @param taskView the new newly created {@link RemoteCarTaskView} instance.
     */
    @Override
    default void onTaskViewCreated(@NonNull ControlledRemoteCarTaskView taskView) {}

    /**
     * Called when the underlying {@link RemoteCarTaskView} is ready. A {@link RemoteCarTaskView}
     * can be considered ready when it has completed all the set up that is required.
     * This callback is only triggered once.
     */
    @Override
    default void onTaskViewInitialized() {}

    /**
     * Called when the underlying {@link RemoteCarTaskView} is released.
     * This callback is only triggered once.
     */
    @Override
    default void onTaskViewReleased() {}

    /**
     * Called when the task has appeared in the taskview.
     *
     * @param taskInfo the taskInfo of the task that has appeared.
     */
    @Override
    default void onTaskAppeared(@NonNull ActivityManager.RunningTaskInfo taskInfo) {}

    /**
     * Called when the task's info has changed.
     *
     * @param taskInfo the taskInfo of the task that has a change in info.
     */
    @Override
    default void onTaskInfoChanged(@NonNull ActivityManager.RunningTaskInfo taskInfo) {}

    /**
     * Called when the task has vanished.
     *
     * @param taskInfo the taskInfo of the task that has vanished.
     */
    @Override
    default void onTaskVanished(@NonNull ActivityManager.RunningTaskInfo taskInfo) {}
}
