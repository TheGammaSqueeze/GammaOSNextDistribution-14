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

package com.android.car.carlauncher.recents;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface RecentTasksProviderInterface {
    /**
     * Loads recent tasks, icons and thumbnails from the system asynchronously and calls
     * {@link RecentsDataChangeListener} callbacks to communicate the result.
     */
    void getRecentTasksAsync();

    /**
     * @return list of {@code taskId} for recent tasks synchronously. An empty list is
     * returned if the tasks were never fetched from the system.
     */
    @NonNull
    List<Integer> getRecentTaskIds();

    /**
     * @return {@link ComponentName} for the requested {@code taskId} or null.
     */
    @Nullable
    ComponentName getRecentTaskComponentName(int taskId);

    /**
     * @return the base intent used to launch the task for the requested {@code taskId} or null.
     */
    @Nullable
    Intent getRecentTaskBaseIntent(int taskId);

    /**
     * @return {@link Drawable} icon for the requested {@code taskId} or null.
     */
    @Nullable
    Drawable getRecentTaskIcon(int taskId);

    /**
     * @return {@link Bitmap} thumbnail for the requested {@code taskId} or null.
     */
    @Nullable
    Bitmap getRecentTaskThumbnail(int taskId);

    /**
     * @return thumbnail insets for the requested {@code taskId} or new Rect object.
     */
    @NonNull
    Rect getRecentTaskInsets(int taskId);

    /**
     * @param taskId The {@code taskId} of the recent task to be opened.
     * @return if the recent task with {@code taskId} was successfully opened.
     */
    boolean openRecentTask(int taskId);

    /**
     * Attempts to open the top running task after {@code recentsActivity} for the given display id.
     * For instance, For {@code recentsActivity} R and other tasks A and B in this order: R,A,B;
     * this method will attempt to open A.
     * If the display associated with the given {@code displayId} doesn't contain the
     * {@code recentsActivity} in the top running tasks, the method will not attempt to open the top
     * task and return false.
     *
     * @param recentsActivity {@link Activity} that is responsible to show recent tasks.
     * @param displayId       the display's id where {@code recentsActivity} is drawn.
     * @return if the top task was found and opened.
     */
    boolean openTopRunningTask(@NonNull Class<? extends Activity> recentsActivity, int displayId);

    /**
     * @param taskId the {@code taskId} of the recent task to be removed from recents.
     */
    void removeTaskFromRecents(int taskId);

    /**
     * Removes all tasks from recents and clears cached data by calling {@link #clearCache}.
     */
    void removeAllRecentTasks();

    /**
     * clears cached data.
     */
    void clearCache();

    /**
     * @param listener to notify changes to. Set it to null to remove the listener.
     */
    void setRecentsDataChangeListener(@Nullable RecentsDataChangeListener listener);

    /**
     * Listener used to convey changes to the task list or task properties.
     */
    interface RecentsDataChangeListener {
        /**
         * Notifies the tasks were fetched/loaded from the system.
         */
        void recentTasksFetched();

        /**
         * Notifies the task's thumbnail was fetched/loaded from the system.
         *
         * @param taskId the {@code taskId} of the task whose thumbnail changed.
         */
        void recentTaskThumbnailChange(int taskId);

        /**
         * Notifies the task's icon was fetched/loaded from the system.
         *
         * @param taskId the {@code taskId} of the Task whose icon changed.
         */
        void recentTaskIconChange(int taskId);
    }
}
