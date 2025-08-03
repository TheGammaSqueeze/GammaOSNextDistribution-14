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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;
import com.android.ondevicepersonalization.libraries.plugin.PluginHost;
import com.android.ondevicepersonalization.libraries.plugin.PluginState;

import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Loads and executes plugins in the current process. */
public class PluginExecutor {
    private static final String TAG = "PluginExecutor";
    private final Map<String, Plugin> mPlugins = new HashMap<>();
    private final Map<String, PluginContext> mPluginContexts = new HashMap<>();
    private final Context mContext;
    private final PluginLoader mPluginLoader;

    /** Creates a {@link PluginExecutor}. */
    public static PluginExecutor create(Context context, PluginLoader pluginLoader) {
        return new PluginExecutor(context, pluginLoader);
    }

    /**
     * Loads a plugin.
     *
     * @param info Information describing the Plugin to load, also providing necessary file
     *     descriptors.
     * @param callback Called when the Plugin has been successfully loaded or loading has failed.
     * @param pluginHost Implementation of {@link PluginHost} interface, providing method
     *     createPluginContext() to create a {@link PluginContext} to pass to the Plugin on
     *     execution.
     * @param pluginContextInitData Initialization data created in the main process, which is passed
     *     to the {@link PluginHost}'s createPluginContext() method.
     */
    public void load(
            PluginInfoInternal info,
            PluginCallback callback,
            @Nullable PluginHost pluginHost,
            @Nullable Bundle pluginContextInitData)
            throws RemoteException {

        ImmutableSet<String> allowedClasses = ImmutableSet.of();
        ImmutableSet<String> allowedPackages = ImmutableSet.of();
        if (pluginHost != null) {
            allowedClasses = pluginHost.getClassLoaderAllowedClasses(info.taskName());
            allowedPackages = pluginHost.getClassLoaderAllowedPackages(info.taskName());
        }

        Plugin plugin =
                mPluginLoader.loadPlugin(
                        info.entryPointClassName(),
                        info.pluginCodeList(),
                        mContext.getClassLoader(),
                        allowedClasses,
                        allowedPackages);
        if (plugin == null) {
            callback.onFailure(FailureType.ERROR_LOADING_PLUGIN);
            return;
        }

        // TODO(b/239079452) : Use unique id to identify plugins.
        String pluginId = info.taskName();

        mPlugins.put(pluginId, plugin);
        @Nullable PluginContext pluginContext = null;
        if (pluginHost != null) {
            pluginContext = pluginHost.createPluginContext(pluginId, pluginContextInitData);
        }
        mPluginContexts.put(pluginId, pluginContext);

        // TODO(b/239079143): Add more specific methods to the callback.
        callback.onSuccess(new Bundle());
    }

    /** Executes a plugin. */
    public void execute(Bundle input, String pluginId, PluginCallback callback)
            throws RemoteException {
        if (!mPlugins.containsKey(pluginId)) {
            Log.e(TAG, String.format("Could not find a plugin associated with %s", pluginId));
            callback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
            return;
        }
        if (!mPluginContexts.containsKey(pluginId)) {
            Log.e(TAG, String.format("No PluginContext for plugin with id %s found", pluginId));
        }

        mPlugins.get(pluginId).onExecute(input, callback, mPluginContexts.get(pluginId));
    }

    /** Unloads a plugin. */
    public void unload(String pluginId, PluginCallback callback) throws RemoteException {

        if (!mPlugins.containsKey(pluginId)) {
            Log.e(TAG, String.format("Could not find a plugin associated with %s", pluginId));
            callback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
            return;
        }
        if (!mPluginContexts.containsKey(pluginId)) {
            Log.e(
                    TAG,
                    String.format("Could not find a pluginContext associated with %s", pluginId));
            callback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
            return;
        }
        mPlugins.remove(pluginId);
        mPluginContexts.remove(pluginId);
        callback.onSuccess(new Bundle());
    }

    /** Checks the plugin state and returns it via stateCallback. */
    public void checkPluginState(String pluginId, IPluginStateCallback stateCallback)
            throws RemoteException {
        if (!mPlugins.containsKey(pluginId)) {
            Log.e(TAG, String.format("Could not find a plugin associated with %s", pluginId));
            stateCallback.onState(PluginState.STATE_NOT_LOADED);
            return;
        }
        if (!mPluginContexts.containsKey(pluginId)) {
            Log.e(
                    TAG,
                    String.format("Could not find a pluginContext associated with %s", pluginId));
            stateCallback.onState(PluginState.STATE_NOT_LOADED);
            return;
        }
        stateCallback.onState(PluginState.STATE_LOADED);
    }

    private PluginExecutor(Context context, PluginLoader pluginLoader) {
        this.mContext = context;
        this.mPluginLoader = pluginLoader;
    }
}
