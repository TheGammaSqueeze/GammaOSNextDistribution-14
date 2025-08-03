/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import com.android.systemui.dagger.SysUISingleton;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Central tracker to notify registered listeners of restart lifecycles within the CarSystemBar.
 */
@SysUISingleton
public class CarSystemBarRestartTracker {
    public final List<Listener> mListeners = new ArrayList<>();

    @Inject
    public CarSystemBarRestartTracker() {
    }

    /** Register a restart listener */
    public void addListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    /** Unregister a restart listener */
    public void removeListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    void notifyPendingRestart(boolean recreateWindows, boolean provisionedStateChanged) {
        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                listener.onPendingRestart(recreateWindows, provisionedStateChanged);
            }
        }
    }

    void notifyRestartComplete(boolean windowsRecreated, boolean provisionedStateChanged) {
        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                listener.onRestartComplete(windowsRecreated, provisionedStateChanged);
            }
        }
    }

    public interface Listener {
        /**
         * Notify that a restart is about to occur. This will be called prior to the destruction
         * of any windows or views.
         * @param recreateWindows whether this restart will recreate the system bar windows (in
         *                        addition to the views)
         * @param provisionedStateChanged whether this restart was caused by a provisioned state
         *                                change
         */
        void onPendingRestart(boolean recreateWindows, boolean provisionedStateChanged);
        /**
         * Notify that a restart has just happened and the views (and potentially windows) were
         * recreated
         * @param windowsRecreated whether this restart recreated the system bar windows
         * @param provisionedStateChanged whether this restart was caused by a provisioned state
         *                                change
         */
        void onRestartComplete(boolean windowsRecreated, boolean provisionedStateChanged);
    }
}
