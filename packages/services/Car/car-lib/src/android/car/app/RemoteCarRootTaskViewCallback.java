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

/**
 * A callback interface for {@link ControlledRemoteCarTaskView}.
 *
 * @hide
 */
public interface RemoteCarRootTaskViewCallback
        extends RemoteCarTaskViewCallback<RemoteCarRootTaskView> {
    /**
     * Called when the underlying {@link RemoteCarTaskView} instance is created.
     *
     * @param taskView the new newly created {@link RemoteCarTaskView} instance.
     */
    @Override
    default void onTaskViewCreated(@NonNull RemoteCarRootTaskView taskView) {}

    /**
     * Called when the underlying {@link RemoteCarTaskView} is ready. A {@link RemoteCarTaskView}
     * is considered ready when it has completed all the set up that is required.
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
}
