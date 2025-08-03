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

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecentTasksViewModel {
    private static final RecentsStatsLogHelper sStatsLogHelper =
            RecentsStatsLogHelper.getInstance();
    private static RecentTasksViewModel sInstance;
    private final RecentTasksProviderInterface mDataStore;
    private final Set<RecentTasksChangeListener> mRecentTasksChangeListener;
    private final Set<HiddenTaskProvider> mHiddenTaskProviders;
    private DisabledTaskProvider mDisabledTaskProvider;
    private final RecentTasksProviderInterface.RecentsDataChangeListener
            mRecentsDataChangeListener =
            new RecentTasksProviderInterface.RecentsDataChangeListener() {
                @Override
                public void recentTasksFetched() {
                    mapAbsoluteTasksToShownTasks();
                    mRecentTasksChangeListener.forEach(
                            RecentTasksChangeListener::onRecentTasksFetched);
                }

                @Override
                public void recentTaskThumbnailChange(int taskId) {
                    int index = mRecentTaskIds.indexOf(taskId);
                    if (index == -1) {
                        return;
                    }
                    mTaskIdToCroppedThumbnailMap.remove(taskId);
                    mRecentTasksChangeListener.forEach(l -> l.onTaskThumbnailChange(index));
                }

                @Override
                public void recentTaskIconChange(int taskId) {
                    int index = mRecentTaskIds.indexOf(taskId);
                    if (index == -1) {
                        return;
                    }
                    mRecentTasksChangeListener.forEach(l ->
                            l.onTaskIconChange(index));
                }
            };
    private List<Integer> mRecentTaskIds;
    private final Map<Integer, Bitmap> mTaskIdToCroppedThumbnailMap;
    private Bitmap mDefaultThumbnail;
    private boolean isInitialised;
    private int mDisplayId = DEFAULT_DISPLAY;
    private int mWindowWidth;
    private int mWindowHeight;
    private Rect mWindowInsets;

    private RecentTasksViewModel() {
        mDataStore = RecentTasksProvider.getInstance();
        mRecentTasksChangeListener = new HashSet<>();
        mHiddenTaskProviders = new HashSet<>();
        mRecentTaskIds = new ArrayList<>();
        mTaskIdToCroppedThumbnailMap = new HashMap<>();
    }

    /**
     * Initialise connections and setup configs
     *
     * @param displayId             the display on which the recents activity is displayed.
     * @param windowWidth           width of window on which recent activity is displayed.
     * @param windowHeight          height of window on which recent activity is displayed.
     * @param windowInsets          insets of window on which recent activity is displayed.
     * @param defaultThumbnailColor color of the default recent task thumbnail to be shown when
     *                              thumbnail is not loaded or not present.
     */
    public void init(int displayId, int windowWidth, int windowHeight, @NonNull Rect windowInsets,
            @ColorInt Integer defaultThumbnailColor) {
        if (isInitialised) {
            return;
        }
        isInitialised = true;
        mDataStore.setRecentsDataChangeListener(mRecentsDataChangeListener);
        mDisplayId = displayId;
        mWindowWidth = windowWidth;
        mWindowHeight = windowHeight;
        mWindowInsets = windowInsets;
        mDefaultThumbnail = createThumbnail(defaultThumbnailColor);
    }

    /**
     * Terminates connections and removes all {@link RecentTasksChangeListener}s and
     * {@link HiddenTaskProvider}s.
     */
    public void terminate() {
        isInitialised = false;
        mDataStore.setRecentsDataChangeListener(/* listener= */ null);
        mRecentTasksChangeListener.clear();
        mHiddenTaskProviders.clear();
        mDisabledTaskProvider = null;
    }

    public static RecentTasksViewModel getInstance() {
        if (sInstance == null) {
            sInstance = new RecentTasksViewModel();
        }
        return sInstance;
    }

    /**
     * Fetches recent task list asynchronously and communicates changes through
     * {@link RecentTasksChangeListener}.
     */
    public void fetchRecentTaskList() {
        mDataStore.getRecentTasksAsync();
    }

    /**
     * Refreshes the UI associated with recent tasks.
     * Does not fetch recent task list from the system.
     */
    public void refreshRecentTaskList() {
        mRecentTasksChangeListener.forEach(RecentTasksChangeListener::onRecentTasksFetched);
    }

    /**
     * @return the {@link Drawable} icon for the given {@code index} or null.
     */
    @Nullable
    public Drawable getRecentTaskIconAt(int index) {
        if (!safeCheckIndex(mRecentTaskIds, index)) {
            return null;
        }
        return mDataStore.getRecentTaskIcon(mRecentTaskIds.get(index));
    }

    /**
     * @return the {@link Bitmap} thumbnail for the given {@code index} or
     * default thumbnail(which could be null of not initialised).
     */
    @Nullable
    public Bitmap getRecentTaskThumbnailAt(int index) {
        if (!safeCheckIndex(mRecentTaskIds, index)) {
            return null;
        }
        if (mTaskIdToCroppedThumbnailMap.containsKey(mRecentTaskIds.get(index))) {
            return mTaskIdToCroppedThumbnailMap.get(mRecentTaskIds.get(index));
        }
        Bitmap thumbnail = mDataStore.getRecentTaskThumbnail(mRecentTaskIds.get(index));
        Rect insets = mDataStore.getRecentTaskInsets(mRecentTaskIds.get(index));
        if (thumbnail != null) {
            Bitmap croppedThumbnail = cropInsets(thumbnail, insets);
            mTaskIdToCroppedThumbnailMap.put(mRecentTaskIds.get(index), croppedThumbnail);
            return croppedThumbnail;
        }
        return mDefaultThumbnail;
    }

    /**
     * @return {@code true} if task for the given {@code index} is disabled.
     */
    public boolean isRecentTaskDisabled(int index) {
        if (mDisabledTaskProvider == null) {
            return false;
        }
        ComponentName componentName = getRecentTaskComponentName(index);
        return componentName != null &&
                mDisabledTaskProvider.isTaskDisabledFromRecents(componentName);
    }

    /**
     * @return the {@link View.OnClickListener} for the task at the given {@code index} or null.
     */
    @Nullable
    public View.OnClickListener getDisabledTaskClickListener(int index) {
        if (mDisabledTaskProvider == null) {
            return null;
        }
        ComponentName componentName = getRecentTaskComponentName(index);
        return componentName != null
                ? mDisabledTaskProvider.getDisabledTaskClickListener(componentName) : null;
    }

    @Nullable
    private ComponentName getRecentTaskComponentName(int index) {
        if (!safeCheckIndex(mRecentTaskIds, index)) {
            return null;
        }
        return mDataStore.getRecentTaskComponentName(mRecentTaskIds.get(index));
    }

    /**
     * Tries to open the recent task at the given {@code index}.
     * Communicates failure through {@link RecentTasksChangeListener}.
     */
    public void openRecentTask(int index) {
        if (safeCheckIndex(mRecentTaskIds, index) &&
                mDataStore.openRecentTask(mRecentTaskIds.get(index))) {
            // TODO(b/311427536): log a boolean to indicate if openRecentTask finished successfully
            ComponentName name = mDataStore.getRecentTaskComponentName(mRecentTaskIds.get(index));
            sStatsLogHelper.logAppLaunched(/* totalTaskCount */ getRecentTasksSize(),
                    /* eventTaskIndex */ index, /* componentName */ name.getPackageName());
            return;
        }
        // failure to open recent task
        mRecentTasksChangeListener.forEach(RecentTasksChangeListener::onOpenRecentTaskFail);
    }

    /**
     * Tries to open the top running task.
     * Communicates failure through {@link RecentTasksChangeListener}.
     */
    public void openMostRecentTask() {
        if (!mDataStore.openTopRunningTask(CarRecentsActivity.class, mDisplayId)) {
            mRecentTasksChangeListener.forEach(RecentTasksChangeListener::onOpenTopRunningTaskFail);
        }
    }

    /**
     * Communicates success through {@link RecentTasksChangeListener}.
     *
     * @param index index of the task to be removed from recents.
     */
    public void removeTaskFromRecents(int index) {
        if (!safeCheckIndex(mRecentTaskIds, index)) {
            return;
        }
        ComponentName name = getRecentTaskComponentName(index);
        removeTaskWithId(mRecentTaskIds.get(index));
        mRecentTaskIds.remove(index);
        mRecentTasksChangeListener.forEach(l -> l.onRecentTaskRemoved(index));
        sStatsLogHelper.logAppDismissed(/* totalTaskCount */ getRecentTasksSize(),
                /* eventTaskIndex */ index, /* packageName */ name.getPackageName());
    }

    /**
     * Removes all tasks from recents and clears cached data by calling {@link #clearCache}.
     */
    public void removeAllRecentTasks() {
        for (int recentTaskId : mRecentTaskIds) {
            removeTaskWithId(recentTaskId);
        }
        sStatsLogHelper.logClearAll(getRecentTasksSize());
        clearCache();
    }

    /**
     * Clears cached data.
     * Communicates success through {@link RecentTasksChangeListener}.
     */
    public void clearCache() {
        mDataStore.clearCache();
        mTaskIdToCroppedThumbnailMap.clear();
        int countRemoved = mRecentTaskIds.size();
        mRecentTaskIds.clear();
        mRecentTasksChangeListener.forEach(l -> l.onAllRecentTasksRemoved(countRemoved));
    }

    /**
     * @return the length of the recent task list
     */
    public int getRecentTasksSize() {
        return mRecentTaskIds.size();
    }

    /**
     * Used to map relative indexes to absolute indexes based on tasks hidden by
     * {@link HiddenTaskProvider}.
     */
    private void mapAbsoluteTasksToShownTasks() {
        List<Integer> recentTaskIds = mDataStore.getRecentTaskIds();
        mRecentTaskIds = new ArrayList<>(recentTaskIds.size());
        for (int taskId : recentTaskIds) {
            ComponentName topComponent = mDataStore.getRecentTaskComponentName(taskId);
            Intent baseIntent = mDataStore.getRecentTaskBaseIntent(taskId);
            boolean isTaskHidden = mHiddenTaskProviders.stream()
                    .anyMatch(p -> p.isTaskHiddenFromRecents(
                            topComponent != null ? topComponent.getPackageName() : null,
                            topComponent != null ? topComponent.getClassName() : null,
                            baseIntent));
            if (isTaskHidden) {
                // skip since it should be hidden
                continue;
            }
            mRecentTaskIds.add(taskId);
        }
    }

    @NonNull
    private Bitmap cropInsets(Bitmap bitmap, Rect insets) {
        return Bitmap.createBitmap(bitmap, insets.left, insets.top,
                /* width= */ bitmap.getWidth() - insets.left - insets.right,
                /* height= */ bitmap.getHeight() - insets.top - insets.bottom);
    }

    /**
     * @return a new {@link Bitmap} with aspect ratio of the current window and the given
     * {@code color}.
     */
    public Bitmap createThumbnail(@ColorInt Integer color) {
        return createThumbnail(mWindowWidth, mWindowHeight, mWindowInsets, color);
    }

    private Bitmap createThumbnail(int width, int height, @NonNull Rect insets,
            @ColorInt Integer color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(color);
        return cropInsets(bitmap, insets);
    }

    private boolean safeCheckIndex(List<?> list, int index) {
        return index >= 0 && index < list.size();
    }

    private void removeTaskWithId(int taskId) {
        mDataStore.removeTaskFromRecents(taskId);
        mTaskIdToCroppedThumbnailMap.remove(taskId);
    }

    /**
     * @param listener listener to send changes in recent task list to.
     */
    public void addRecentTasksChangeListener(RecentTasksChangeListener listener) {
        mRecentTasksChangeListener.add(listener);
    }

    /**
     * @param listener remove the given listener.
     */
    public void removeAllRecentTasksChangeListeners(RecentTasksChangeListener listener) {
        mRecentTasksChangeListener.remove(listener);
    }

    /**
     * @param provider provider of packages to be hidden from recents.
     */
    public void addHiddenTaskProvider(HiddenTaskProvider provider) {
        mHiddenTaskProviders.add(provider);
    }

    /**
     * @param provider remove the given provider.
     */
    public void removeHiddenTaskProvider(HiddenTaskProvider provider) {
        mHiddenTaskProviders.remove(provider);
    }

    /**
     * @param provider provider of packages to be disabled in recents.
     */
    public void setDisabledTaskProvider(DisabledTaskProvider provider) {
        mDisabledTaskProvider = provider;
    }

    /**
     * Listen to changes in the recents.
     */
    public interface RecentTasksChangeListener {
        /**
         * Called when recent tasks have been fetched from the system.
         */
        default void onRecentTasksFetched() {
        }

        /**
         * @param position position whose thumbnail has been changed.
         */
        default void onTaskThumbnailChange(int position) {
        }

        /**
         * @param position position whose icon has been changed.
         */
        default void onTaskIconChange(int position) {
        }

        /**
         * Called when system fails to open a recent task.
         */
        default void onOpenRecentTaskFail() {
        }

        /**
         * Called when system fails to open the top task.
         */
        default void onOpenTopRunningTaskFail() {
        }

        /**
         * @param countRemoved number of recent tasks removed.
         */
        default void onAllRecentTasksRemoved(int countRemoved) {
        }

        /**
         * @param position position at which the recent task was removed.
         */
        default void onRecentTaskRemoved(int position) {
        }
    }

    /**
     * Decides if a task should be hidden from recents.
     * This is necessary to be able to get tasks to be hidden at runtime.
     */
    public interface HiddenTaskProvider {
        /**
         * @return if the task should be hidden from recents.
         */
        boolean isTaskHiddenFromRecents(String packageName, String className, Intent baseIntent);
    }

    /**
     * Decides if a task is disabled in recents.
     * This is necessary to be able to get tasks to be disabled at runtime.
     * Note: Hidden tasks cannot be disabled.
     */
    public interface DisabledTaskProvider {
        /**
         * @return if the task associated with {@code componentName} is disabled in recents.
         */
        boolean isTaskDisabledFromRecents(ComponentName componentName);

        /**
         * @return {@link View.OnClickListener} to be called when user tries to click on
         * disabled task associated with {@code componentName}.
         */
        default View.OnClickListener getDisabledTaskClickListener(ComponentName componentName) {
            return null;
        }
    }
}
