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

package com.android.ondevicepersonalization.services.process;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.os.Bundle;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Interface for process runners used to support various request flows. Note that only one type
 * of runner can be used until the process restarts.
 */
public interface ProcessRunner {
    /** Binds to/Loads a service in a (shared) isolated process. */
    @NonNull
    ListenableFuture<IsolatedServiceInfo> loadIsolatedService(
            @NonNull String taskName, @NonNull ComponentName componentName);

    /** Executes a service loaded in a (shared) isolated process. */
    @NonNull
    ListenableFuture<Bundle> runIsolatedService(
            @NonNull IsolatedServiceInfo isolatedProcessInfo,
            int operationCode,
            @NonNull Bundle serviceParams);

    /** Unbinds/Unloads a service loaded in a (shared) isolated process. */
    @NonNull
    ListenableFuture<Void> unloadIsolatedService(
            @NonNull IsolatedServiceInfo isolatedServiceInfo);
}