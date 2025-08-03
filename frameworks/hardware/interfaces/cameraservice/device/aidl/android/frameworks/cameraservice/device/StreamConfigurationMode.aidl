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

package android.frameworks.cameraservice.device;

/**
 * This defines the general operation mode for the HAL (for a given stream
 * configuration) where modes besides NORMAL have different semantics, and
 * usually limit the generality of the API in exchange for higher performance in
 * some particular area.
 */
@VintfStability
@Backing(type="int")
enum StreamConfigurationMode {
    /**
     * Normal stream configuration operation mode. This is the default camera
     * operation mode, where all semantics of HAL APIs and metadata controls
     * apply.
     */
    NORMAL_MODE = 0,
    /**
     * Special constrained high speed operation mode for devices that can not
     * support high speed output in NORMAL mode.
     */
    CONSTRAINED_HIGH_SPEED_MODE = 1,
    /**
     * A set of vendor-defined operating modes, for custom default camera
     * application features that can't be implemented in a fully flexible
     * fashion required for NORMAL_MODE.
     */
    VENDOR_MODE_0 = 0x8000,
    VENDOR_MODE_1,
    VENDOR_MODE_2,
    VENDOR_MODE_3,
    VENDOR_MODE_4,
    VENDOR_MODE_5,
    VENDOR_MODE_6,
    VENDOR_MODE_7,
}
