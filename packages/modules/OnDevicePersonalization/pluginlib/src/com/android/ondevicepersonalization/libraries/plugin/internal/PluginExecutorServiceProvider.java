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

import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

/** Interface used to provide a reference to the {@link IPluginExecutorService} */
public interface PluginExecutorServiceProvider {
    /** Returns the {@link IPluginExecutorService}. */
    @Nullable IPluginExecutorService getExecutorService();

    /** Returns the readiness of the executor service. */
    SettableFuture<Boolean> getExecutorServiceReadiness();

    /** Bind to the service. */
    boolean bindService();

    /** Unbind from the service. */
    void unbindService();
}
