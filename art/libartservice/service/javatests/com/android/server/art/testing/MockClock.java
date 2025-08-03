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

package com.android.server.art.testing;

import android.annotation.NonNull;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MockClock {
    private long mCurrentTimeMs = 0;
    @NonNull private List<ScheduledExecutor> mExecutors = new ArrayList<>();

    @NonNull
    public ScheduledExecutor createScheduledExecutor() {
        var executor = new ScheduledExecutor();
        mExecutors.add(executor);
        return executor;
    }

    public long getCurrentTimeMs() {
        return mCurrentTimeMs;
    }

    public void advanceTime(long timeMs) {
        mCurrentTimeMs += timeMs;
        for (ScheduledExecutor executor : mExecutors) {
            executor.notifyUpdate();
        }
    }

    @NonNull
    public List<ScheduledExecutor> getCreatedExecutors() {
        return mExecutors;
    }

    public class ScheduledExecutor extends ScheduledThreadPoolExecutor {
        // The second element of the pair is the scheduled time.
        @NonNull
        private PriorityQueue<Pair<RunnableScheduledFuture<?>, Long>> tasks = new PriorityQueue<>(
                1 /* initialCapacity */, Comparator.comparingLong(pair -> pair.second));

        public ScheduledExecutor() {
            super(1 /* corePoolSize */);
        }

        @NonNull
        public ScheduledFuture<?> schedule(
                @NonNull Runnable command, long delay, @NonNull TimeUnit unit) {
            // Use `Long.MAX_VALUE` to prevent the task from being automatically run.
            var task = (RunnableScheduledFuture<?>) super.schedule(
                    command, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            tasks.add(Pair.create(task, getCurrentTimeMs() + unit.toMillis(delay)));
            return task;
        }

        public void notifyUpdate() {
            while (!tasks.isEmpty()) {
                Pair<RunnableScheduledFuture<?>, Long> pair = tasks.peek();
                RunnableScheduledFuture<?> task = pair.first;
                long scheduledTimeMs = pair.second;
                if (getCurrentTimeMs() >= scheduledTimeMs) {
                    if (!task.isDone() && !task.isCancelled()) {
                        task.run();
                    }
                    tasks.poll();
                    // Remove the task from the queue of the executor. Terminate the executor if
                    // it's shutdown and the queue is empty.
                    super.remove(task);
                } else {
                    break;
                }
            }
        }
    }
}
