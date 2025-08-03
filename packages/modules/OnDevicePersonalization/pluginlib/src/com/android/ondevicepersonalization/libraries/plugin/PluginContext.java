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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Interface for generic context object passed to the {@link Plugin} implementation, provided by the
 * {@link PluginHost}.
 */
public interface PluginContext {
    /**
     * Attempt to fetch an instance of a specified type, e.g. an interface for data-access.
     *
     * <p>There should be an implied contract between a given Plugin implementation and the
     * PluginContext implementation passed to it about what types may be supplied via this method.
     * The types may be entirely specific to the particular Plugin use case.
     */
    default <T> @Nullable T get(Class<T> clazz) {
        return null;
    }
}
