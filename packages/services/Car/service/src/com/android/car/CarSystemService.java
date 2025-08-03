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

package com.android.car;

import com.android.car.internal.util.IndentingPrintWriter;

/**
 * Base class for all Car specific services.
 */

// Note: VehicleHal and CarStatsService will implement CarSystemService directly.
// All other Car services will implement CarServiceBase which is a "marker" interface that
// extends CarSystemService. This makes it easy for ICarImpl to handle dump differently
// for VehicleHal and CarStatsService.
public interface CarSystemService {

    /**
     * Initializes the service.
     * <p>All necessary initialization should be done and service should be
     * functional after this.
     */
    void init();

    /**
     * Called when all other CarSystemService init completes.
     */
    default void onInitComplete() {}

    /** Releases all reources to stop the service. */
    void release();

    /** Dumps its state. */
    void dump(IndentingPrintWriter writer);
}
