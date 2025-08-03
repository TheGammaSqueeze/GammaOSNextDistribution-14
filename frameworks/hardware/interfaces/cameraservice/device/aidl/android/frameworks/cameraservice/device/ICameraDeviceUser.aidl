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

import android.frameworks.cameraservice.device.CameraMetadata;
import android.frameworks.cameraservice.device.CaptureRequest;
import android.frameworks.cameraservice.device.OutputConfiguration;
import android.frameworks.cameraservice.device.SessionConfiguration;
import android.frameworks.cameraservice.device.StreamConfigurationMode;
import android.frameworks.cameraservice.device.SubmitInfo;
import android.frameworks.cameraservice.device.TemplateId;
import android.hardware.common.fmq.MQDescriptor;
import android.hardware.common.fmq.SynchronizedReadWrite;

@VintfStability
interface ICameraDeviceUser {

    /**
     * Begin device configuration.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     */
    void beginConfigure();

    /**
     * Cancel the current repeating request.
     *
     * The current repeating request may be stopped by camera device due to an
     * error.
     *
     * @throws ServiceSpecificException with the following values:
     *      Status::INVALID_OPERATION when there is no active repeating request
     *
     * @return the frame number of the last frame that will be
     *         produced from this repeating request. If there are no inflight
     *         repeating requests, this will return -1 as the frameNumber.
     *         If the status is not NO_ERROR, the frame number should not be
     *         used.
     */
    long cancelRepeatingRequest();

    /**
     * Create a default capture request for capturing an image.
     *
     * @param templateId the type of capture request to be created.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return the settings metadata of the request.
     */
    CameraMetadata createDefaultRequest(in TemplateId templateId);

    /**
     * Create an output stream based on the given output configuration.
     *
     * Note: createStream() must only be called within a beginConfigure() and an
     *       endConfigure() block.
     *
     * @param outputConfiguration size, format, and other parameters for the
     *        stream
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return stream ID of the new stream generated.
     */
    int createStream(in OutputConfiguration outputConfiguration);

    /**
     * delete the stream specified by streamId.
     *
     * Note: deleteStream() must only be called within a beginConfigure() and an
     *       endConfigure() block.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @param streamId the stream id of the stream to be deleted
     */
    void deleteStream(in int streamId);

    /**
     * disconnect from using the camera device.
     * This method must block till in-flight requests are completed and stop
     * all the requests submitted through submitRequestList().
     */
    void disconnect();

    /**
     * End the device configuration.
     *
     * endConfigure must be called after stream configuration is complete
     * (i.e. after a call to beginConfigure and subsequent
     * createStream/deleteStream calls). It must be called before any
     * requests can be submitted.
     *
     * @param operatingMode The kind of session to create; either NORMAL_MODE,
     *        CONSTRAINED_HIGH_SPEED_MODE, or one of the vendor modes.
     * @param sessionParams Session-wide camera parameters. Empty session
     *        parameters are legal inputs.
     * @param startTimeNs indicate the timestamp when session configuration
     *        starts.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     */
    void endConfigure(in StreamConfigurationMode operatingMode, in CameraMetadata sessionParams, in long startTimeNs);

    /**
     * flush all the requests pending on the device.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return the frame number of the last frame flushed.
     */
    long flush();

    /**
     * Retrieve the fast message queue to be optionally used in CaptureRequests,
     * to pass the settings metadata.
     * If the client decides to use FMQ, it must:
     *  - Call getCaptureRequestMetadataQueue to retrieve the fast message queue
     *  - In submitRequestList calls, for each request set the fmqMetadataSize
     *    in the settings field of physicalCameraSettings, to the size of the
     *    metadata.
     *
     * @return the queue that the client writes the request settings
     *         metadata to.
     */
    MQDescriptor<byte, SynchronizedReadWrite> getCaptureRequestMetadataQueue();

    /**
     * Retrieve the fast message queue used along with
     * ICameraDeviceCallback.onResultReceived.
     *
     * Note: The client's use of this function does not affect the hidl
     * service's decision to use / not use FMQ to pass result metadata to the
     * client.
     *
     * Clients implementing the callback must:
     *  - Retrieve the queue using getCaptureResultMetadataQueue.
     *  - In the implementation of ICameraDeviceCallback.onResultReceived, if
     *    PhysicalCaptureResultInfo.physicalCameraMetadata has a valid
     *    fmqMetadataSize (which is > 0), the metadata must be read from the FMQ,
     *    else, it must be read from the metadata field.
     *    The same applies to resultMetadata.
     *
     * @return the queue that the client reads the result metadata from.
     */
    MQDescriptor<byte, SynchronizedReadWrite> getCaptureResultMetadataQueue();

    /**
     * Check whether a particular session configuration has camera device
     * support.
     *
     * @param sessionConfiguration Specific session configuration to be verified.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return true  - in case the stream combination is supported.
     *         false - in case there is no device support.
     */
    boolean isSessionConfigurationSupported(in SessionConfiguration sessionConfiguration);

    /**
     *
     * <p>Pre-allocate buffers for a stream.</p>
     *
     * <p>Normally, the image buffers for a given stream are allocated on-demand,
     * to minimize startup latency and memory overhead.</p>
     *
     * <p>However, in some cases, it may be desirable for the buffers to be allocated before
     * any requests targeting the window are actually submitted to the device. Large buffers
     * may take some time to allocate, which can result in delays in submitting requests until
     * sufficient buffers are allocated to reach steady-state behavior. Such delays can cause
     * bursts to take longer than desired, or cause skips or stutters in preview output.</p>
     *
     * <p>The prepare() call can be used by clients to perform this pre-allocation.
     * It may only be called for a given output stream before that stream is used as a target for a
     * request. The number of buffers allocated is the sum of the count needed by the consumer
     * providing the output stream, and the maximum number needed by the camera device to fill its
     * pipeline.
     * Since this may be a larger number than what is actually required for steady-state operation,
     * using this call may result in higher memory consumption than the normal on-demand behavior
     * results in. This method will also delay the time to first output to a given stream,
     * in exchange for smoother frame rate once the allocation is complete.</p>
     *
     * <p>For example, a client that creates an
     * {@link AImageReader} with a maxImages argument of 10,
     * but only uses 3 simultaneous {@link AImage}s at once, would normally only cause those 3
     * images to be allocated (plus what is needed by the camera device for smooth operation).
     * But using prepare() on the {@link AImageReader}'s window will result in all 10
     * {@link AImage}s being allocated. So clients using this method should exercise caution
     * while using this call.</p>
     *
     * <p>Once allocation is complete, ICameraDeviceCallback.onPrepared
     * will be invoked with the stream provided to this method. Between the prepare call and the
     * ICameraDeviceCallback.onPrepared() call, the output provided to prepare must not be used as
     * a target of a capture qequest submitted
     * to this session.</p>
     *
     * @param streamId the stream id of the stream for which buffer pre-allocation is to be done.
     */
    void prepare(in int streamId);

    /**
     * Submit a list of capture requests.
     *
     * Note: Clients must call submitRequestList() serially if they opt
     *       to utilize an fmq (obtained by calling getCaptureRequestMetadataQueue)
     *       for any CaptureRequest's physicalCameraSettings metadata.
     *
     * @param requestList The list of CaptureRequests
     * @param isRepeating Whether the set of requests repeats indefinitely.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return SubmitInfo data structure containing the request id of the
     *         capture request and the frame number of the last frame that will
     *         be produced(In case the request is not repeating. Otherwise it
     *         contains the frame number of the last request, of the previus
     *         batch of repeating requests, if any. If there is no previous
     *         batch, the frame number returned will be -1.)
     */
    SubmitInfo submitRequestList(in CaptureRequest[] requestList, in boolean isRepeating);

    /**
     * Update a previously set output configuration.
     *
     * Note: It is legal to call this method outside of
     *       beginConfigure()/endConfigure() blocks and also when the device
     *       is not idle.
     *
     * @param streamId the stream id whose output configuration needs to be
     *        updated.
     * @param outputConfiguration the new outputConfiguration.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     */
    void updateOutputConfiguration(in int streamId, in OutputConfiguration outputConfiguration);

    /**
     * Block until the device is idle.
     *
     * Note: This method will not block if there are active repeating requests.
     *
     * @throws ServiceSpecificException with the following values:
     *      Status::INVALID_OPERATION if there are active repeating requests.
     */
    void waitUntilIdle();
}
