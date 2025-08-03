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

package com.android.server.healthconnect;

import android.annotation.NonNull;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A scheduler class to run the tasks in a Round Robin fashion based on client package names.
 *
 * @hide
 */
public final class HealthConnectRoundRobinScheduler {
    private static final String TAG = "HealthConnectScheduler";
    private final ConcurrentSkipListMap<Integer, Queue<Runnable>> mTasks =
            new ConcurrentSkipListMap<>();
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mPauseScheduler;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    @GuardedBy("mLock")
    private Integer mLastKeyUsed;

    void resume() {
        synchronized (mLock) {
            mPauseScheduler = false;
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    void addTask(int uid, Runnable task) {
        synchronized (mLock) {
            // If the scheduler is currently paused (this can happen if the platform is doing a user
            // switch), ignore this request. This most likely means that we won't be able to deliver
            // the result back anyway.
            if (mPauseScheduler) {
                Log.e(TAG, "Unable to schedule task for uid: " + uid);
                return;
            }

            mTasks.putIfAbsent(uid, new LinkedBlockingQueue<>());
            mTasks.get(uid).add(task);
        }
    }

    @NonNull
    Runnable getNextTask() {
        synchronized (mLock) {
            if (mLastKeyUsed == null) {
                mLastKeyUsed = mTasks.firstKey();
            }

            Map.Entry<Integer, Queue<Runnable>> entry = mTasks.higherEntry(mLastKeyUsed);
            while (entry != null && entry.getValue().isEmpty()) {
                mTasks.remove(entry.getKey());
                entry = mTasks.higherEntry(entry.getKey());
            }

            if (entry == null) {
                // Reached the end, no tasks found. Reset to first entry and try again.
                entry = mTasks.firstEntry();
                while (entry != null && entry.getValue().isEmpty()) {
                    mTasks.remove(entry.getKey());
                    entry = mTasks.higherEntry(entry.getKey());
                }
            }

            if (entry != null) {
                mLastKeyUsed = entry.getKey();
                return entry.getValue().poll();
            }

            throw new InternalError("Task scheduled but none found");
        }
    }

    void killTasksAndPauseScheduler() {
        synchronized (mLock) {
            mPauseScheduler = true;
            mTasks.clear();
        }
    }
}
