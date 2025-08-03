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

package com.android.ondevicepersonalization.services.serviceflow;

import android.content.ComponentName;
import android.os.Bundle;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Interface for various on-device personalization service flows.
 *
 * @param <R> service flow return data type.
 */
public interface ServiceFlow<R> {

    /**
     * Checks pre-conditions for a given service flow and configures the necessary service
     * flow parameters before execution.
     */
    boolean isServiceFlowReady();

    /** Returns the service used for loading/binding. */
    ComponentName getService();

    /** Returns the necessary service parameters. */
    Bundle getServiceParams();

    /** Uploads service flow metrics. */
    default void uploadServiceFlowMetrics(ListenableFuture<Bundle> runServiceFuture) {}

    /** Gets service flow results. */
    ListenableFuture<R> getServiceFlowResultFuture(ListenableFuture<Bundle> runServiceFuture);

    /** Returns the service flow result through a callback. */
    default void returnResultThroughCallback(ListenableFuture<R> serviceFlowResultFuture) {}

    /** Frees up resources used by service. */
    default void cleanUpServiceParams() {}
}
