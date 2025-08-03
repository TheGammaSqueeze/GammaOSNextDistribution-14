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
 * All camera service and device AIDL calls may return the following
 * status codes
 */
@VintfStability
@Backing(type="int")
enum Status {
    /**
     * Call succeeded.
     */
    NO_ERROR = 0,
    /**
     * Call failed due to inadequete permissions.
     */
    PERMISSION_DENIED = 1,
    /**
     * Call tried added something that already existed, eg: add a duplicate
     * listener.
     */
    ALREADY_EXISTS = 2,
    /**
     * Call received illegal argument.
     */
    ILLEGAL_ARGUMENT = 3,
    /**
     * The camera device is no longer connected.
     */
    DISCONNECTED = 4,
    /**
     * Request timed out.
     */
    TIMED_OUT = 5,
    /**
     * The device has been disabled by policy.
     */
    DISABLED = 6,
    /**
     * The camera device is currently in use.
     */
    CAMERA_IN_USE = 7,
    /**
     * Too many cameras are connected, more cameras cannot be opened.
     */
    MAX_CAMERAS_IN_USE = 8,
    /**
     * Camera server is using a camera HAL version that does not support
     * the current version of android.frameworks.cameraservice.service.ICameraService
     * and android.frameworks.cameraservice.device.ICameraDeviceUser.
     */
    DEPRECATED_HAL = 9,
    /**
     * An invalid operation was attempted by the client. Eg: a waitUntilIdle()
     * call was made, with active repeating requests.
     */
    INVALID_OPERATION = 10,
    /**
     * An unknown error was encountered by the camera subsystem.
     */
    UNKNOWN_ERROR = 11,
}
