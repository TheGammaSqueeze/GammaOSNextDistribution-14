/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.carlauncher.taskstack;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import com.android.wm.shell.common.HandlerExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Organizer of many task stack listeners for the car launcher application. */
public class TaskStackChangeListeners {
    private static final String TAG = TaskStackChangeListeners.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final TaskStackChangeListeners INSTANCE = new TaskStackChangeListeners(
            new HandlerExecutor(Handler.getMain()));

    private final Impl mImpl;

    private TaskStackChangeListeners(Executor executor) {
        mImpl = new Impl(executor);
    }

    /** Returns a singleton instance of the {@link TaskStackChangeListeners}. */
    public static TaskStackChangeListeners getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void registerTaskStackListener(TaskStackListener listener) {
        synchronized (mImpl) {
            mImpl.addListener(listener);
        }
        if (DEBUG) {
            Log.d(TAG, "registerTaskStackListener: " + listener);
        }
    }

    /**
     * Unregisters a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void unregisterTaskStackListener(TaskStackListener listener) {
        synchronized (mImpl) {
            mImpl.removeListener(listener);
        }
        if (DEBUG) {
            Log.d(TAG, "unregisterTaskStackListener: " + listener);
        }
    }

    private static class Impl extends TaskStackListener {

        private final List<TaskStackListener> mTaskStackListeners = new ArrayList<>();

        private final Executor mExecutor;
        private boolean mRegistered;

        Impl(Executor executor) {
            mExecutor = executor;
        }

        public void addListener(TaskStackListener listener) {
            synchronized (mTaskStackListeners) {
                mTaskStackListeners.add(listener);
            }
            if (!mRegistered) {
                // Register mTaskStackListener to IActivityManager only once if needed.
                try {
                    ActivityTaskManager.getService().registerTaskStackListener(this);
                    mRegistered = true;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to call registerTaskStackListener", e);
                }
            }
        }

        public void removeListener(TaskStackListener listener) {
            boolean isEmpty;
            synchronized (mTaskStackListeners) {
                mTaskStackListeners.remove(listener);
                isEmpty = mTaskStackListeners.isEmpty();
            }
            if (isEmpty && mRegistered) {
                // Unregister mTaskStackListener once we have no more listeners
                try {
                    ActivityTaskManager.getService().unregisterTaskStackListener(this);
                    mRegistered = false;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to call unregisterTaskStackListener", e);
                }
            }
        }

        @Override
        public void onTaskStackChanged() throws RemoteException {
            mExecutor.execute(() -> {
                synchronized (mTaskStackListeners) {
                    for (int i = 0; i < mTaskStackListeners.size(); i++) {
                        try {
                            mTaskStackListeners.get(i).onTaskStackChanged();
                        } catch (RemoteException e) {
                            Log.e(TAG, "onTaskStackChanged failed", e);
                        }
                    }
                }
            });
        }

        @Override
        public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
            mExecutor.execute(() -> {
                synchronized (mTaskStackListeners) {
                    for (int i = 0; i < mTaskStackListeners.size(); i++) {
                        try {
                            mTaskStackListeners.get(i).onTaskCreated(taskId, componentName);
                        } catch (RemoteException e) {
                            Log.e(TAG, "onTaskCreated failed", e);
                        }
                    }
                }
            });
        }

        @Override
        public void onTaskRemoved(int taskId) throws RemoteException {
            mExecutor.execute(() -> {
                synchronized (mTaskStackListeners) {
                    for (int i = 0; i < mTaskStackListeners.size(); i++) {
                        try {
                            mTaskStackListeners.get(i).onTaskRemoved(taskId);
                        } catch (RemoteException e) {
                            Log.e(TAG, "onTaskRemoved failed", e);
                        }
                    }
                }
            });
        }

        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
                throws RemoteException {
            mExecutor.execute(() -> {
                synchronized (mTaskStackListeners) {
                    for (int i = 0; i < mTaskStackListeners.size(); i++) {
                        try {
                            mTaskStackListeners.get(i).onTaskMovedToFront(taskInfo);
                        } catch (RemoteException e) {
                            Log.e(TAG, "onTaskMovedToFront failed", e);
                        }
                    }
                }
            });
        }

        @Override
        public void onTaskFocusChanged(int taskId, boolean focused) {
            mExecutor.execute(() -> {
                synchronized (mTaskStackListeners) {
                    for (int i = 0; i < mTaskStackListeners.size(); i++) {
                        mTaskStackListeners.get(i).onTaskFocusChanged(taskId, focused);
                    }
                }
            });
        }

        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            mExecutor.execute(() -> {
                synchronized (mTaskStackListeners) {
                    for (TaskStackListener listener : mTaskStackListeners) {
                        try {
                            listener.onActivityRestartAttempt(
                                    task, homeTaskVisible, clearedTask, wasVisible);
                        } catch (RemoteException e) {
                            Log.e(TAG, "onActivityRestartAttempt failed", e);
                        }
                    }
                }
            });
        }
    }
}
