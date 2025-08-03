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

import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interface that provides {@link PluginContext} to {@link Plugin} implementation. Typically
 * implemented by the Sandbox Developer.
 */
public interface PluginHost {
    /**
     * Create Bundle of initialization data used to create the PluginContext.
     *
     * <p>This method is called outside the sandbox process. The Bundle is passed over AIDL to the
     * sandbox service.
     */
    default @Nullable Bundle createPluginContextInitData(String pluginId) {
        return null;
    }

    /**
     * Create {@link PluginContext} data to be used by the {@link Plugin} implementation.
     *
     * <p>This method is called inside the sandbox process. The {@link Bundle} returned from
     * createPluginContextInitData() is passed as the initData argument. The PluginContext is passed
     * to onExecute().
     */
    default @Nullable PluginContext createPluginContext(
            String pluginId, @Nullable Bundle initData) {
        return null;
    }

    /** Get set of classes that the sandbox class loader is allowed to load. */
    default ImmutableSet<String> getClassLoaderAllowedClasses(String pluginId) {
        return ImmutableSet.of();
    }

    /** Get set of packages that the sandbox class loader is allowed to load classes from. */
    default ImmutableSet<String> getClassLoaderAllowedPackages(String pluginId) {
        return ImmutableSet.of();
    }
}
