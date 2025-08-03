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
 * Error codes for onDeviceError
 */
@VintfStability
@Backing(type="int")
enum ErrorCode {
    /**
     * To indicate all invalid error codes.
     */
    CAMERA_INVALID_ERROR = -1,
    /**
     * Camera operation has failed because the camera device has been closed,
     * possibly because a higher priority client has taken ownership of the
     * device.
     */
    CAMERA_DISCONNECTED = 0,
    /**
     * The camera device has encountered a fatal error and needs to be
     * re-opened to use it again.
     */
    CAMERA_DEVICE = 1,
    /**
     * The camera service has encountered a fatal error.
     */
    CAMERA_SERVICE = 2,
    /**
     * The camera device encountered an error while processing a request.
     * No output will be produced for this request. Subsequent requests are
     * unaffected.
     */
    CAMERA_REQUEST = 3,
    /**
     * The camera device encountered an error while producing an output result
     * metadata buffer for a request. Output stream buffers for it must still
     * be available.
     */
    CAMERA_RESULT = 4,
    /**
     * A camera device encountered an error occurred due to which an output
     * buffer was lost.
     */
    CAMERA_BUFFER = 5,
    /**
     * The camera device has been disabled and cannot be opened.
     */
    CAMERA_DISABLED = 6,
    /**
     * Camera operation has failed due to an unknown cause.
     */
    CAMERA_UNKNOWN_ERROR = 7,
}
