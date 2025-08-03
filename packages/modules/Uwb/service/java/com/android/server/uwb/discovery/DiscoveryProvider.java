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
package com.android.server.uwb.discovery;

import android.util.Log;

/** Abstract class for Discovery Provider */
public abstract class DiscoveryProvider {
    private static final String TAG = DiscoveryProvider.class.getSimpleName();

    /* Indicates whether the server has started.
     */
    protected boolean mStarted = false;

    /**
     * Checks if the server has started.
     *
     * @return indicates if the server has started.
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Starts the discovery.
     *
     * @return indicates if successfully started.
     */
    public boolean start() {
        if (isStarted()) {
            Log.i(TAG, "Discovery already started.");
            return false;
        }
        return true;
    }

    /**
     * Stops the discovery.
     *
     * @return indicates if successfully stopped.
     */
    public boolean stop() {
        if (!isStarted()) {
            Log.i(TAG, "Discovery already stopped.");
            return false;
        }
        return true;
    }
}
