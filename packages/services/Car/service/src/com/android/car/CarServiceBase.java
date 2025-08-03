/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.util.proto.ProtoOutputStream;

/**
 * Base class for all Car specific services except for {@code VehicleHal} and
 * {@code CarStatsService}.
 */

// Note: VehicleHal and CarStatsService will implement CarSystemService directly.
// All other Car services will implement CarServiceBase which is a "marker" interface that
// extends CarSystemService. This makes it easy for ICarImpl to handle dump differently
// for VehicleHal and CarStatsService.
public interface CarServiceBase extends CarSystemService {
    /** Dumps its state to a proto buffer. */
    // This method should not be called unless FLAG_CAR_DUMP_TO_PROTO is defined.
    void dumpProto(ProtoOutputStream proto);
}
