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

import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.tools.layoutlib.annotations.NotNull;

import android.os.SystemClock_Delegate;
import android.util.TimeUtils;
import android.view.Choreographer.FrameCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages {@link android.view.Choreographer} callbacks. Keeps track of the currently active
 * callbacks and allows to execute callbacks if their time is due.
 */
public class ChoreographerCallbacks {
    // Simple wrapper around ArrayList to be able to use protected removeRange method
    private static class RangeList<T> extends ArrayList<T> {
        private void removeFrontElements(int n) {
            removeRange(0, n);
        }
    }

    private static class Callback {
        private final Object mAction;
        private final Object mToken;
        private final long mDueTime;

        private Callback(@NotNull Object action, Object token, long dueTime) {
            mAction = action;
            mToken = token;
            mDueTime = dueTime;
        }
    }

    private final RangeList<Callback> mCallbacks = new RangeList<>();

    public void add(Object action, Object token, long delayMillis) {
        synchronized (mCallbacks) {
            int idx = 0;
            final long now = SystemClock_Delegate.uptimeMillis();
            final long dueTime = now + delayMillis;
            while (idx < mCallbacks.size()) {
                if (mCallbacks.get(idx).mDueTime > dueTime) {
                    break;
                } else {
                    ++idx;
                }
            }
            mCallbacks.add(idx, new Callback(action, token, dueTime));
        }
    }

    public void remove(Object action, Object token) {
        synchronized (mCallbacks) {
            mCallbacks.removeIf(el -> (action == null || el.mAction == action)
                    && (token == null || el.mToken == token));
        }
    }

    public void execute(long currentTimeMs, @NotNull ILayoutLog logger) {
        final long currentTimeNanos = currentTimeMs * TimeUtils.NANOS_PER_MS;
        List<Callback> toExecute;
        synchronized (mCallbacks) {
            int idx = 0;
            while (idx < mCallbacks.size()) {
                if (mCallbacks.get(idx).mDueTime > currentTimeMs) {
                    break;
                } else {
                    ++idx;
                }
            }
            toExecute = new ArrayList<>(mCallbacks.subList(0, idx));
            mCallbacks.removeFrontElements(idx);
        }

        // We run the callbacks outside of the synchronized block to avoid deadlocks caused by
        // callbacks calling back into ChoreographerCallbacks.
        toExecute.forEach(p -> executeSafely(p.mAction, currentTimeNanos, logger));
    }

    public void clear() {
        synchronized (mCallbacks) {
            mCallbacks.clear();
        }
    }

    private static void executeSafely(@NotNull Object action, long frameTimeNanos,
            @NotNull ILayoutLog logger) {
        try {
            if (action instanceof FrameCallback) {
                FrameCallback callback = (FrameCallback) action;
                callback.doFrame(frameTimeNanos);
            } else if (action instanceof Runnable) {
                Runnable runnable = (Runnable) action;
                runnable.run();
            } else {
                logger.error(ILayoutLog.TAG_BROKEN,
                        "Unexpected action as Choreographer callback", (Object) null, null);
            }
        } catch (Throwable t) {
            logger.error(ILayoutLog.TAG_BROKEN, "Failed executing Choreographer callback", t,
                    null, null);
        }
    }
}
