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

package android.frameworks.cameraservice.service;

/**
 * The current status of the device.
 */
@VintfStability
@Backing(type="int")
enum CameraDeviceStatus {
    /**
     * Camera is in use by another app and cannot be used exclusively.
     */
    STATUS_NOT_AVAILABLE = -2,
    /**
     * Use to initialize variables only.
     */
    STATUS_UNKNOWN = -1,
    /**
     * Device physically unplugged
     */
    STATUS_NOT_PRESENT = 0,
    /**
     * Device physically has been plugged in and the camera can be used
     * exclusively.
     */
    STATUS_PRESENT = 1,
    /**
     * Device physically has been plugged in but it will not be connect-able
     * until enumeration is complete.
     */
    STATUS_ENUMERATING = 2,
}
