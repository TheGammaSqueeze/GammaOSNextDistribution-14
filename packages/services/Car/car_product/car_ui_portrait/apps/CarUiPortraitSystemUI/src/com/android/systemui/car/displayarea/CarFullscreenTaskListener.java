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

package com.android.systemui.car.displayarea;

import android.app.ActivityManager;
import android.view.SurfaceControl;

import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Organizes tasks presented in display area using {@link CarDisplayAreaOrganizer}.
 */
public class CarFullscreenTaskListener extends FullscreenTaskListener {
    private final List<OnTaskChangeListener> mOnTaskChangeListeners;

    @Inject
    public CarFullscreenTaskListener(SyncTransactionQueue syncQueue) {
        super(syncQueue);
        mOnTaskChangeListeners = new ArrayList<>();
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        super.onTaskAppeared(taskInfo, leash);

        for (OnTaskChangeListener listener : mOnTaskChangeListeners) {
            listener.onTaskAppeared(taskInfo);
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskVanished(taskInfo);
        for (OnTaskChangeListener listener : mOnTaskChangeListeners) {
            listener.onTaskVanished(taskInfo);
        }
    }

    /**
     * Register's a {@link OnTaskChangeListener}
     */
    public void registerOnTaskChangeListener(OnTaskChangeListener listener) {
        if (listener == null) {
            throw new RuntimeException("listener cannot be null");
        }
        mOnTaskChangeListeners.add(listener);
    }

    /**
     * UnRegister's a {@link OnTaskChangeListener}
     */
    public boolean unregisterontaskchangelistener(OnTaskChangeListener listener) {
        if (listener == null) {
            throw new RuntimeException("listener cannot be null");
        }
        return mOnTaskChangeListeners.remove(listener);
    }

    /**
     * Interface to give the information regarding task changes
     */
    public interface OnTaskChangeListener {
        /**
         * Gives the information of the task that just appeared
         */
        void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo);

        /**
         * Gives the information of the task that just vanished
         */
        void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo);
    }
}
