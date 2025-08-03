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

import android.frameworks.cameraservice.device.CaptureResultExtras;
import android.frameworks.cameraservice.device.ErrorCode;
import android.frameworks.cameraservice.device.CaptureMetadataInfo;
import android.frameworks.cameraservice.device.PhysicalCaptureResultInfo;

@VintfStability
oneway interface ICameraDeviceCallback {
    /**
     *  Callback called when capture starts.
     *
     *  @param resultExtras data structure containing information about the
     *         frame number, request id, etc, of the request.
     *  @param timestamp corresponding to the start (in nanoseconds)
     */
    void onCaptureStarted(in CaptureResultExtras resultExtras, in long timestamp);

    /**
     *  Callback called when the device encounters an error.
     *
     *  @param errorCode the error code corresponding to the error.
     *  @param resultExtras data structure containing information about the
     *         frame number, request id, etc, of the request on which the device
     *         error occurred, in case the errorCode was CAMERA_BUFFER.
     */
    void onDeviceError(in ErrorCode errorCode, in CaptureResultExtras resultExtras);

    /**
     *  Callback called when the device is idle.
     */
    void onDeviceIdle();

    /**
     *  Callback called when the surfaces corresponding to the stream with stream id 'streamId'
     *  have been prepared.
     *
     *  This callback will only be called as a response to the ICameraDeviceUser.prepare() call.
     *
     *  @param streamId the stream id of the stream on which ICameraDeviceUser.prepare() was called.
     */
    void onPrepared(in int streamId);

    /**
     * Repeating request encountered an error and was stopped.
     *
     * @param lastFrameNumber Frame number of the last frame of the streaming
     *        request.
     * @param repeatingRequestId the ID of the repeating request
     *        being stopped
     */
    void onRepeatingRequestError(in long lastFrameNumber, in int repeatingRequestId);

    /**
     * Callback called when a capture request is completed.
     *
     * Note: The framework must call this callback serially if it opts to
     *       utilize an fmq for either the result metadata and/or any of the
     *       physicalCaptureResultInfo.physicalCameraMetadata values.
     *
     * @param result result metadata
     * @param resultExtras data structure containing information about the
     *        frame number, request id, etc of the request.
     * @param physicalCaptureResultInfos a list of physicalCaptureResultInfo,
     *        which contains the camera id and metadata related to the physical
     *        cameras involved for the particular capture request, if any.
     */
    void onResultReceived(in CaptureMetadataInfo result, in CaptureResultExtras resultExtras,
        in PhysicalCaptureResultInfo[] physicalCaptureResultInfos);
}
