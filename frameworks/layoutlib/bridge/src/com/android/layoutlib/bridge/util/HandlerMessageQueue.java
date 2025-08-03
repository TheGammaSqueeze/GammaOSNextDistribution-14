/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.layoutlib.bridge.util;

import com.android.tools.layoutlib.annotations.NotNull;
import com.android.tools.layoutlib.annotations.Nullable;

import android.os.Handler;
import android.util.Pair;

import java.util.LinkedList;
import java.util.WeakHashMap;

/**
 * A queue that stores {@link Runnable}s associated with the corresponding {@link Handler}s.
 * {@link Runnable}s get automatically released when the corresponding {@link Handler} gets
 * collected by the garbage collector. All {@link Runnable}s are queued in a single virtual queue
 * with respect to their corresponding uptime (the time when they should be executed).
 */
public class HandlerMessageQueue {
    private final WeakHashMap<Handler, LinkedList<Pair<Long, Runnable>>> runnablesMap =
            new WeakHashMap<>();

    /**
     * Adds a {@link Runnable} associated with the {@link Handler} to be executed at
     * particular time
     * @param h handler associated with the {@link Runnable}
     * @param uptimeMillis time in milliseconds the {@link Runnable} to be executed
     * @param r {@link Runnable} to be added
     */
    public void add(@NotNull Handler h, long uptimeMillis, @NotNull Runnable r) {
        synchronized (runnablesMap) {
            LinkedList<Pair<Long, Runnable>> runnables = runnablesMap.computeIfAbsent(h,
                    k -> new LinkedList<>());

            int idx = 0;
            while (idx < runnables.size()) {
                if (runnables.get(idx).first <= uptimeMillis) {
                    idx++;
                } else {
                    break;
                }
            }
            runnables.add(idx, Pair.create(uptimeMillis, r));
        }
    }

    private static class HandlerWrapper {
        public Handler handler;
    }

    /**
     * Removes from the queue and returns the {@link Runnable} with the smallest uptime if it
     * is less than the one passed as a parameter or null if such runnable does not exist.
     * @param uptimeMillis
     * @return the {@link Runnable} from the queue
     */
    @Nullable
    public Runnable extractFirst(long uptimeMillis) {
        synchronized (runnablesMap) {
            final HandlerWrapper w = new HandlerWrapper();
            runnablesMap.forEach((h, l) -> {
                if (!l.isEmpty()) {
                    long currentUptime = l.getFirst().first;
                    if (currentUptime <= uptimeMillis) {
                        if (w.handler == null || currentUptime <
                                runnablesMap.get(w.handler).getFirst().first) {
                            w.handler = h;
                        }
                    }
                }
            });
            if (w.handler != null) {
                return runnablesMap.get(w.handler).pollFirst().second;
            }
            return null;
        }
    }

    /**
     * @return true is queue has no runnables left
     */
    public boolean isNotEmpty() {
        synchronized (runnablesMap) {
            return runnablesMap.values().stream().anyMatch(l -> !l.isEmpty());
        }
    }

    /**
     * @return number of runnables in the queue
     */
    public int size() {
        synchronized (runnablesMap) {
            return runnablesMap.values().stream().mapToInt(LinkedList::size).sum();
        }
    }

    /**
     * Completely clears the entire queue
     */
    public void clear() {
        synchronized (runnablesMap) {
            runnablesMap.clear();
        }
    }
}
