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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControl;

/**
 * This is a blueprint to implement the host part of {@link CarTaskViewClient}.
 *
 * @hide
 */
@SystemApi
public interface CarTaskViewHost {
    /** Releases the resources held by this task view's host side. */
    void release();

    /**
     * See {@link TaskView#startActivity(PendingIntent, Intent,
     * ActivityOptions, Rect)}
     */
    void startActivity(
            @NonNull PendingIntent pendingIntent, @Nullable Intent intent, @NonNull Bundle options,
            @Nullable Rect launchBounds);

    /**
     * Creates the root task, which will be embedded inside this task view.
     *
     * @hide
     */
    void createRootTask(int displayId);

    /**
     * Creates the launch root task, which will be embedded inside this task view.
     *
     * @hide
     */
    void createLaunchRootTask(int displayId, boolean embedHomeTask,
            boolean embedRecentsTask, boolean embedAssistantTask);

    /**
     * Notifies the host side that the client surface has been created.
     *
     * @param control the {@link SurfaceControl} of the surface that has been created.
     */
    void notifySurfaceCreated(@NonNull SurfaceControl control);

    /**
     * Sets the bounds of the window for the underlying Task.
     *
     * @param windowBoundsOnScreen the new bounds in screen coordinates.
     */
    void setWindowBounds(@NonNull Rect windowBoundsOnScreen);

    /** Notifies the host side that the client surface has been destroyed. */
    void notifySurfaceDestroyed();

    /** Brings the embedded Task to the front in the WM Hierarchy. */
    void showEmbeddedTask();

    /**
     * Sets the visibility of the embedded task.
     *
     * @hide
     */
    void setTaskVisibility(boolean visibility);

    /**
     * Reorders the embedded task to top when {@code onTop} is {@code true} and to bottom when
     * its {@code false}.
     *
     * @hide
     */
    void reorderTask(boolean onTop);
    /**
     * Adds the given {@code insets} on the Task.
     *
     * <p>
     * The given rectangle for the given insets type is applied to the underlying task right
     * away.
     * If a rectangle for an insets type was added previously, it will be replaced with the
     * new value.
     * If a rectangle for an insets type was already added, but is not specified currently in
     * {@code insets}, it will remain applied to the task. Clients should explicitly call
     * {@link #removeInsets(int, int)} to remove the rectangle for that insets type from
     * the underlying task.
     *
     * @param index An owner might add multiple insets sources with the same type.
     *              This identifies them.
     * @param type  The insets type of the insets source. This doesn't accept the composite types.
     * @param frame The rectangle area of the insets source.
     */
    void addInsets(int index, int type, @NonNull Rect frame);

    /**
     * Removes the insets for the given @code index}, and {@code type} that were added via
     * {@link #addInsets(int, int, Rect)}
     */
    void removeInsets(int index, int type);
}
