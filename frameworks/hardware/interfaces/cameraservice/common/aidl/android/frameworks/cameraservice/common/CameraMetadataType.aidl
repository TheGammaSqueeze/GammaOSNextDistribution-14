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

package android.frameworks.cameraservice.common;

/**
 * Camera metadata type.
 */
@VintfStability
@Backing(type="int")
enum CameraMetadataType {
    /**
     * Unsigned 8-bit integer (uint8_t)
     */
    BYTE = 0,
    /**
     * Signed 32-bit integer (int32_t)
     */
    INT32 = 1,
    /**
     * 32-bit float (float)
     */
    FLOAT = 2,
    /**
     * Signed 64-bit integer (int64_t)
     */
    INT64 = 3,
    /**
     * 64-bit float (double)
     */
    DOUBLE = 4,
    /**
     * A 64-bit fraction (camera_metadata_rational_t)
     */
    RATIONAL = 5,
}
