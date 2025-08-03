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
 * Information about a capture, available to a device client on various
 * conditions through ICameraDeviceUserCallback callbacks.
 */
@VintfStability
parcelable CaptureResultExtras {
    /**
     * An integer to index the request sequence that this result belongs to.
     */
    int requestId;
    /**
     * An integer to index this result inside a request sequence, starting from 0.
     */
    int burstId;
    /**
     * A 64bit integer to index the frame number associated with this result.
     */
    long frameNumber;
    /**
     * The partial result count (index) for this capture result.
     */
    int partialResultCount;
    /**
     * For buffer drop errors, the stream ID for the stream that lost a buffer.
     * Otherwise -1.
     */
    int errorStreamId;
    /**
     * For capture result errors, the physical camera ID in case the respective request contains
     * a reference to physical camera device. Empty otherwise.
     * When filled, contains one of the values in ACAMERA_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS entry
     * in the static metadata of the logical multicamera to which the request was made.
     */
    String errorPhysicalCameraId;
}
