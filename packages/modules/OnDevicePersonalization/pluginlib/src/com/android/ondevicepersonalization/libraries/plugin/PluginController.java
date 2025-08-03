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

package com.android.ondevicepersonalization.libraries.plugin;

import android.os.Bundle;
import android.os.RemoteException;

/** A handle for a client to use that allows them to load, unload, and execute the plugin. */
public interface PluginController {
    /**
     * Loads the plugin. Calls the {@link PluginCallback} to indicate success or failure for loading
     * the plugin. Throws RemoteException if the callback fails to run.
     */
    void load(PluginCallback callback) throws RemoteException;

    /**
     * Unloads the loaded plugin. Calls the {@link PluginCallback} to indicate success or failure
     * for unloading the plugin. Throws RemoteException if the callback fails to run.
     */
    void unload(PluginCallback callback) throws RemoteException;

    /**
     * Runs the plugin with input bundle. Calls the {@link PluginCallback} to indicate success or
     * failure for running the plugin. Throws RemoteException if the callback fails to run.
     */
    void execute(Bundle input, PluginCallback callback) throws RemoteException;

    /**
     * Checks and asynchronously returns the current state of the plugin, expressed as a {@link
     * PluginState}.
     */
    void checkPluginState(PluginStateCallback callback);

    /** Returns the taskName of the Plugin inside the PluginController. */
    String getName();
}
