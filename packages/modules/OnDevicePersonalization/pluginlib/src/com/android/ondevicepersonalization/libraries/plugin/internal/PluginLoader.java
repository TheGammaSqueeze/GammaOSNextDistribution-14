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

import com.android.ondevicepersonalization.libraries.plugin.Plugin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

/** Interface used to load Plugins. */
public interface PluginLoader {
    /**
     * Returns an instance of the plugin.
     *
     * @param className The class name of the plugin
     * @param pluginCode contains FileDescriptors with the plugin apks to be loaded
     * @param classLoader The plugin container's class loader to be isolated and managed
     * @param containerClassesAllowlist Classes allowed to be loaded outside plugin's class loader.
     *     These are usually classes that are accessed inside and outside the sandbox.
     * @param containerPackagesAllowlist Packages allowed to be loaded outside plugin's class
     *     loader.
     */
    @Nullable Plugin loadPlugin(
            String className,
            ImmutableList<PluginCode> pluginCode,
            ClassLoader classLoader,
            ImmutableSet<String> containerClassesAllowlist,
            ImmutableSet<String> containerPackagesAllowlist);
}
