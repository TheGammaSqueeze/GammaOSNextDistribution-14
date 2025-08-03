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

import org.checkerframework.checker.nullness.qual.Nullable;

/** Interface of a Plugin to be executed in an {@link ExecutionEnvironment} */
public interface Plugin {
    /**
     * Process a request in an {@link ExecutionEnvironment} (for example a sandbox process) and
     * reply via callback.
     *
     * @param input A {@link Bundle} containing data from external apps as the input to
     *     the plugin code
     * @param callback to reply with resulting data in {@link Bundle} or an error in
     *     {@link PluginCallback.FailureType} back, see also
     *     the contract {@link PluginCallback}
     * @param context data a plugin needs in order to run
     */
    void onExecute(
            Bundle input, PluginCallback callback, @Nullable PluginContext context);

    /**
     * Set the class loader that that plugin would use to load child classes.
     *
     * @param classLoader A {@link ClassLoader} to be used to load child classes.
     */
    default void setClassLoader(ClassLoader classLoader) {}
}
