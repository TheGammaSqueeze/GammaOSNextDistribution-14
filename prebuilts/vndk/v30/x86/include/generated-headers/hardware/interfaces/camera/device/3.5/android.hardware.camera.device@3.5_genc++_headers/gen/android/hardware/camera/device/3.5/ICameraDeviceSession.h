#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_ICAMERADEVICESESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_ICAMERADEVICESESSION_H

#include <android/hardware/camera/common/1.0/types.h>
#include <android/hardware/camera/device/3.2/types.h>
#include <android/hardware/camera/device/3.4/ICameraDeviceSession.h>
#include <android/hardware/camera/device/3.4/types.h>
#include <android/hardware/camera/device/3.5/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_5 {

/**
 * Camera device active session interface.
 *
 * Obtained via ICameraDevice::open(), this interface contains the methods to
 * configure and request captures from an active camera device.
 */
struct ICameraDeviceSession : public ::android::hardware::camera::device::V3_4::ICameraDeviceSession {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.camera.device@3.5::ICameraDeviceSession"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for constructDefaultRequestSettings
     */
    using constructDefaultRequestSettings_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& requestTemplate)>;
    /**
     * constructDefaultRequestSettings:
     *
     * Create capture settings for standard camera use cases.
     *
     * The device must return a settings buffer that is configured to meet the
     * requested use case, which must be one of the CAMERA3_TEMPLATE_*
     * enums. All request control fields must be included.
     *
     * Performance requirements:
     *
     * This must be a non-blocking call. The HAL should return from this call
     * in 1ms, and must return from this call in 5ms.
     *
     * Return values:
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful construction of default settings.
     *     INTERNAL_ERROR:
     *         An unexpected internal error occurred, and the default settings
     *         are not available.
     *     ILLEGAL_ARGUMENT:
     *         The camera HAL does not support the input template type
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     * @return template The default capture request settings for the requested
     *     use case, or an empty metadata structure if status is not OK.
     *
     */
    virtual ::android::hardware::Return<void> constructDefaultRequestSettings(::android::hardware::camera::device::V3_2::RequestTemplate type, constructDefaultRequestSettings_cb _hidl_cb) = 0;

    /**
     * Return callback for configureStreams
     */
    using configureStreams_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& halConfiguration)>;
    /**
     * configureStreams:
     *
     * Reset the HAL camera device processing pipeline and set up new input and
     * output streams. This call replaces any existing stream configuration with
     * the streams defined in the streamList. This method must be called at
     * least once before a request is submitted with processCaptureRequest().
     *
     * The streamList must contain at least one output-capable stream, and may
     * not contain more than one input-capable stream.
     *
     * The streamList may contain streams that are also in the currently-active
     * set of streams (from the previous call to configureStreams()). These
     * streams must already have valid values for usage, maxBuffers, and the
     * private pointer.
     *
     * If the HAL needs to change the stream configuration for an existing
     * stream due to the new configuration, it may rewrite the values of usage
     * and/or maxBuffers during the configure call.
     *
     * The framework must detect such a change, and may then reallocate the
     * stream buffers before using buffers from that stream in a request.
     *
     * If a currently-active stream is not included in streamList, the HAL may
     * safely remove any references to that stream. It must not be reused in a
     * later configureStreams() call by the framework, and all the gralloc
     * buffers for it must be freed after the configureStreams() call returns.
     *
     * If the stream is new, the client must set the consumer usage flags in
     * requestedConfiguration. Upon return, the HAL device must set producerUsage,
     * maxBuffers, and other fields in the configureStreams() return values. These
     * fields are then used by the framework and the platform gralloc module to
     * allocate the gralloc buffers for each stream.
     *
     * Newly allocated buffers may be included in a capture request at any time
     * by the framework. Once a gralloc buffer is returned to the framework
     * with processCaptureResult (and its respective releaseFence has been
     * signaled) the framework may free or reuse it at any time.
     *
     * ------------------------------------------------------------------------
     *
     * Preconditions:
     *
     * The framework must only call this method when no captures are being
     * processed. That is, all results have been returned to the framework, and
     * all in-flight input and output buffers have been returned and their
     * release sync fences have been signaled by the HAL. The framework must not
     * submit new requests for capture while the configureStreams() call is
     * underway.
     *
     * Postconditions:
     *
     * The HAL device must configure itself to provide maximum possible output
     * frame rate given the sizes and formats of the output streams, as
     * documented in the camera device's static metadata.
     *
     * Performance requirements:
     *
     * This call is expected to be heavyweight and possibly take several hundred
     * milliseconds to complete, since it may require resetting and
     * reconfiguring the image sensor and the camera processing pipeline.
     * Nevertheless, the HAL device should attempt to minimize the
     * reconfiguration delay to minimize the user-visible pauses during
     * application operational mode changes (such as switching from still
     * capture to video recording).
     *
     * The HAL should return from this call in 500ms, and must return from this
     * call in 1000ms.
     *
     * @return Status Status code for the operation, one of:
     *     OK:
     *          On successful stream configuration.
     *     INTERNAL_ERROR:
     *         If there has been a fatal error and the device is no longer
     *         operational. Only close() can be called successfully by the
     *         framework after this error is returned.
     *     ILLEGAL_ARGUMENT:
     *         If the requested stream configuration is invalid. Some examples
     *         of invalid stream configurations include:
     *           - Including more than 1 INPUT stream
     *           - Not including any OUTPUT streams
     *           - Including streams with unsupported formats, or an unsupported
     *             size for that format.
     *           - Including too many output streams of a certain format.
     *           - Unsupported rotation configuration
     *           - Stream sizes/formats don't satisfy the
     *             StreamConfigurationMode requirements for non-NORMAL mode, or
     *             the requested operation_mode is not supported by the HAL.
     *           - Unsupported usage flag
     *         The camera service cannot filter out all possible illegal stream
     *         configurations, since some devices may support more simultaneous
     *         streams or larger stream resolutions than the minimum required
     *         for a given camera device hardware level. The HAL must return an
     *         ILLEGAL_ARGUMENT for any unsupported stream set, and then be
     *         ready to accept a future valid stream configuration in a later
     *         configureStreams call.
     * @return finalConfiguration The stream parameters desired by the HAL for
     *     each stream, including maximum buffers, the usage flags, and the
     *     override format.
     *
     */
    virtual ::android::hardware::Return<void> configureStreams(const ::android::hardware::camera::device::V3_2::StreamConfiguration& requestedConfiguration, configureStreams_cb _hidl_cb) = 0;

    /**
     * Return callback for processCaptureRequest
     */
    using processCaptureRequest_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, uint32_t numRequestProcessed)>;
    /**
     * processCaptureRequest:
     *
     * Send a list of capture requests to the HAL. The HAL must not return from
     * this call until it is ready to accept the next set of requests to
     * process. Only one call to processCaptureRequest() must be made at a time
     * by the framework, and the calls must all be from the same thread. The
     * next call to processCaptureRequest() must be made as soon as a new
     * request and its associated buffers are available. In a normal preview
     * scenario, this means the function is generally called again by the
     * framework almost instantly. If more than one request is provided by the
     * client, the HAL must process the requests in order of lowest index to
     * highest index.
     *
     * The cachesToRemove argument contains a list of buffer caches (see
     * StreamBuffer document for more information on buffer cache) to be removed
     * by camera HAL. Camera HAL must remove these cache entries whether or not
     * this method returns OK.
     *
     * The actual request processing is asynchronous, with the results of
     * capture being returned by the HAL through the processCaptureResult()
     * call. This call requires the result metadata to be available, but output
     * buffers may simply provide sync fences to wait on. Multiple requests are
     * expected to be in flight at once, to maintain full output frame rate.
     *
     * The framework retains ownership of the request structure. It is only
     * guaranteed to be valid during this call. The HAL device must make copies
     * of the information it needs to retain for the capture processing. The HAL
     * is responsible for waiting on and closing the buffers' fences and
     * returning the buffer handles to the framework.
     *
     * The HAL must write the file descriptor for the input buffer's release
     * sync fence into input_buffer->release_fence, if input_buffer is not
     * valid. If the HAL returns -1 for the input buffer release sync fence, the
     * framework is free to immediately reuse the input buffer. Otherwise, the
     * framework must wait on the sync fence before refilling and reusing the
     * input buffer.
     *
     * The input/output buffers provided by the framework in each request
     * may be brand new (having never before seen by the HAL).
     *
     * ------------------------------------------------------------------------
     * Performance considerations:
     *
     * Handling a new buffer should be extremely lightweight and there must be
     * no frame rate degradation or frame jitter introduced.
     *
     * This call must return fast enough to ensure that the requested frame
     * rate can be sustained, especially for streaming cases (post-processing
     * quality settings set to FAST). The HAL should return this call in 1
     * frame interval, and must return from this call in 4 frame intervals.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful start to processing the capture request
     *     ILLEGAL_ARGUMENT:
     *         If the input is malformed (the settings are empty when not
     *         allowed, there are 0 output buffers, etc) and capture processing
     *         cannot start. Failures during request processing must be
     *         handled by calling ICameraDeviceCallback::notify(). In case of
     *         this error, the framework retains responsibility for the
     *         stream buffers' fences and the buffer handles; the HAL must not
     *         close the fences or return these buffers with
     *         ICameraDeviceCallback::processCaptureResult().
     *     INTERNAL_ERROR:
     *         If the camera device has encountered a serious error. After this
     *         error is returned, only the close() method can be successfully
     *         called by the framework.
     * @return numRequestProcessed Number of requests successfully processed by
     *     camera HAL. When status is OK, this must be equal to the size of
     *     requests. When the call fails, this number is the number of requests
     *     that HAL processed successfully before HAL runs into an error.
     *
     */
    virtual ::android::hardware::Return<void> processCaptureRequest(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::CaptureRequest>& requests, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::BufferCache>& cachesToRemove, processCaptureRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for getCaptureRequestMetadataQueue
     */
    using getCaptureRequestMetadataQueue_cb = std::function<void(const ::android::hardware::MQDescriptorSync<uint8_t>& queue)>;
    /**
     * getCaptureRequestMetadataQueue:
     *
     * Retrieves the queue used along with processCaptureRequest. If
     * client decides to use fast message queue to pass request metadata,
     * it must:
     * - Call getCaptureRequestMetadataQueue to retrieve the fast message queue;
     * - In each of the requests sent in processCaptureRequest, set
     *   fmqSettingsSize field of CaptureRequest to be the size to read from the
     *   fast message queue; leave settings field of CaptureRequest empty.
     *
     * @return queue the queue that client writes request metadata to.
     */
    virtual ::android::hardware::Return<void> getCaptureRequestMetadataQueue(getCaptureRequestMetadataQueue_cb _hidl_cb) = 0;

    /**
     * Return callback for getCaptureResultMetadataQueue
     */
    using getCaptureResultMetadataQueue_cb = std::function<void(const ::android::hardware::MQDescriptorSync<uint8_t>& queue)>;
    /**
     * getCaptureResultMetadataQueue:
     *
     * Retrieves the queue used along with
     * ICameraDeviceCallback.processCaptureResult.
     *
     * Clients to ICameraDeviceSession must:
     * - Call getCaptureRequestMetadataQueue to retrieve the fast message queue;
     * - In implementation of ICameraDeviceCallback, test whether
     *   .fmqResultSize field is zero.
     *     - If .fmqResultSize != 0, read result metadata from the fast message
     *       queue;
     *     - otherwise, read result metadata in CaptureResult.result.
     *
     * @return queue the queue that implementation writes result metadata to.
     */
    virtual ::android::hardware::Return<void> getCaptureResultMetadataQueue(getCaptureResultMetadataQueue_cb _hidl_cb) = 0;

    /**
     * flush:
     *
     * Flush all currently in-process captures and all buffers in the pipeline
     * on the given device. Generally, this method is used to dump all state as
     * quickly as possible in order to prepare for a configure_streams() call.
     *
     * No buffers are required to be successfully returned, so every buffer
     * held at the time of flush() (whether successfully filled or not) may be
     * returned with CAMERA3_BUFFER_STATUS_ERROR. Note the HAL is still allowed
     * to return valid (CAMERA3_BUFFER_STATUS_OK) buffers during this call,
     * provided they are successfully filled.
     *
     * All requests currently in the HAL are expected to be returned as soon as
     * possible. Not-in-process requests must return errors immediately. Any
     * interruptible hardware blocks must be stopped, and any uninterruptible
     * blocks must be waited on.
     *
     * flush() may be called concurrently to processCaptureRequest(), with the
     * expectation that processCaptureRequest returns quickly and the
     * request submitted in that processCaptureRequest call is treated like
     * all other in-flight requests. Due to concurrency issues, it is possible
     * that from the HAL's point of view, a processCaptureRequest() call may
     * be started after flush has been invoked but has not returned yet. If such
     * a call happens before flush() returns, the HAL must treat the new
     * capture request like other in-flight pending requests (see #4 below).
     *
     * More specifically, the HAL must follow below requirements for various
     * cases:
     *
     * 1. For captures that are too late for the HAL to cancel/stop, and must be
     *    completed normally by the HAL; i.e. the HAL can send shutter/notify
     *    and processCaptureResult and buffers as normal.
     *
     * 2. For pending requests that have not done any processing, the HAL must
     *    call notify CAMERA3_MSG_ERROR_REQUEST, and return all the output
     *    buffers with processCaptureResult in the error state
     *    (CAMERA3_BUFFER_STATUS_ERROR). The HAL must not place the release
     *    fence into an error state, instead, the release fences must be set to
     *    the acquire fences passed by the framework, or -1 if they have been
     *    waited on by the HAL already. This is also the path to follow for any
     *    captures for which the HAL already called notify() with
     *    CAMERA3_MSG_SHUTTER but won't be producing any metadata/valid buffers
     *    for. After CAMERA3_MSG_ERROR_REQUEST, for a given frame, only
     *    processCaptureResults with buffers in CAMERA3_BUFFER_STATUS_ERROR
     *    are allowed. No further notifys or processCaptureResult with
     *    non-empty metadata is allowed.
     *
     * 3. For partially completed pending requests that do not have all the
     *    output buffers or perhaps missing metadata, the HAL must follow
     *    below:
     *
     *    3.1. Call notify with CAMERA3_MSG_ERROR_RESULT if some of the expected
     *         result metadata (i.e. one or more partial metadata) won't be
     *         available for the capture.
     *
     *    3.2. Call notify with CAMERA3_MSG_ERROR_BUFFER for every buffer that
     *         won't be produced for the capture.
     *
     *    3.3. Call notify with CAMERA3_MSG_SHUTTER with the capture timestamp
     *         before any buffers/metadata are returned with
     *         processCaptureResult.
     *
     *    3.4. For captures that will produce some results, the HAL must not
     *         call CAMERA3_MSG_ERROR_REQUEST, since that indicates complete
     *         failure.
     *
     *    3.5. Valid buffers/metadata must be passed to the framework as
     *         normal.
     *
     *    3.6. Failed buffers must be returned to the framework as described
     *         for case 2. But failed buffers do not have to follow the strict
     *         ordering valid buffers do, and may be out-of-order with respect
     *         to valid buffers. For example, if buffers A, B, C, D, E are sent,
     *         D and E are failed, then A, E, B, D, C is an acceptable return
     *         order.
     *
     *    3.7. For fully-missing metadata, calling CAMERA3_MSG_ERROR_RESULT is
     *         sufficient, no need to call processCaptureResult with empty
     *         metadata or equivalent.
     *
     * 4. If a flush() is invoked while a processCaptureRequest() invocation
     *    is active, that process call must return as soon as possible. In
     *    addition, if a processCaptureRequest() call is made after flush()
     *    has been invoked but before flush() has returned, the capture request
     *    provided by the late processCaptureRequest call must be treated
     *    like a pending request in case #2 above.
     *
     * flush() must only return when there are no more outstanding buffers or
     * requests left in the HAL. The framework may call configure_streams (as
     * the HAL state is now quiesced) or may issue new requests.
     *
     * Note that it's sufficient to only support fully-succeeded and
     * fully-failed result cases. However, it is highly desirable to support
     * the partial failure cases as well, as it could help improve the flush
     * call overall performance.
     *
     * Performance requirements:
     *
     * The HAL should return from this call in 100ms, and must return from this
     * call in 1000ms. And this call must not be blocked longer than pipeline
     * latency (see S7 for definition).
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful flush of the camera HAL.
     *     INTERNAL_ERROR:
     *         If the camera device has encountered a serious error. After this
     *         error is returned, only the close() method can be successfully
     *         called by the framework.
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> flush() = 0;

    /**
     * close:
     *
     * Shut down the camera device.
     *
     * After this call, all calls to this session instance must return
     * INTERNAL_ERROR.
     *
     * This method must always succeed, even if the device has encountered a
     * serious error.
     */
    virtual ::android::hardware::Return<void> close() = 0;

    /**
     * Return callback for configureStreams_3_3
     */
    using configureStreams_3_3_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& halConfiguration)>;
    /**
     * configureStreams_3_3:
     *
     * Identical to @3.2::ICameraDeviceSession.configureStreams, except that:
     *
     * - The output HalStreamConfiguration now contains an overrideDataspace
     *   field, to be used by the HAL to select a different dataspace for some
     *   use cases when dealing with the IMPLEMENTATION_DEFINED pixel format.
     *
     * Clients may invoke either this method or
     * @3.2::ICameraDeviceSession.configureStreams() for stream configuration.
     * This method is recommended for clients to use since it provides more
     * flexibility.
     */
    virtual ::android::hardware::Return<void> configureStreams_3_3(const ::android::hardware::camera::device::V3_2::StreamConfiguration& requestedConfiguration, configureStreams_3_3_cb _hidl_cb) = 0;

    /**
     * Return callback for configureStreams_3_4
     */
    using configureStreams_3_4_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::device::V3_4::HalStreamConfiguration& halConfiguration)>;
    /**
     * configureStreams_3_4:
     *
     * Identical to @3.3::ICameraDeviceSession.configureStreams, except that:
     *
     * - The requested configuration includes session parameters.
     *
     * @return Status Status code for the operation, one of:
     *     OK:
     *          On successful stream configuration.
     *     INTERNAL_ERROR:
     *         If there has been a fatal error and the device is no longer
     *         operational. Only close() can be called successfully by the
     *         framework after this error is returned.
     *     ILLEGAL_ARGUMENT:
     *         If the requested stream configuration is invalid. Some examples
     *         of invalid stream configurations include:
     *           - Including more than 1 INPUT stream
     *           - Not including any OUTPUT streams
     *           - Including streams with unsupported formats, or an unsupported
     *             size for that format.
     *           - Including too many output streams of a certain format.
     *           - Unsupported rotation configuration
     *           - Stream sizes/formats don't satisfy the
     *             StreamConfigurationMode requirements
     *             for non-NORMAL mode, or the requested operation_mode is not
     *             supported by the HAL.
     *           - Unsupported usage flag
     *         The camera service cannot filter out all possible illegal stream
     *         configurations, since some devices may support more simultaneous
     *         streams or larger stream resolutions than the minimum required
     *         for a given camera device hardware level. The HAL must return an
     *         ILLEGAL_ARGUMENT for any unsupported stream set, and then be
     *         ready to accept a future valid stream configuration in a later
     *         configureStreams call.
     * @return halConfiguration The stream parameters desired by the HAL for
     *     each stream, including maximum buffers, the usage flags, and the
     *     override format.
     */
    virtual ::android::hardware::Return<void> configureStreams_3_4(const ::android::hardware::camera::device::V3_4::StreamConfiguration& requestedConfiguration, configureStreams_3_4_cb _hidl_cb) = 0;

    /**
     * Return callback for processCaptureRequest_3_4
     */
    using processCaptureRequest_3_4_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, uint32_t numRequestProcessed)>;
    /**
     * processCaptureRequest_3_4:
     *
     * Identical to @3.2::ICameraDeviceSession.processCaptureRequest, except that:
     *
     * - The capture request can include individual settings for physical camera devices
     *   backing a logical multi-camera.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful start to processing the capture request
     *     ILLEGAL_ARGUMENT:
     *         If the input is malformed (the settings are empty when not
     *         allowed, the physical camera settings are invalid, there are 0
     *         output buffers, etc) and capture processing
     *         cannot start. Failures during request processing must be
     *         handled by calling ICameraDeviceCallback::notify(). In case of
     *         this error, the framework retains responsibility for the
     *         stream buffers' fences and the buffer handles; the HAL must not
     *         close the fences or return these buffers with
     *         ICameraDeviceCallback::processCaptureResult().
     *     INTERNAL_ERROR:
     *         If the camera device has encountered a serious error. After this
     *         error is returned, only the close() method can be successfully
     *         called by the framework.
     * @return numRequestProcessed Number of requests successfully processed by
     *     camera HAL. When status is OK, this must be equal to the size of
     *     requests. When the call fails, this number is the number of requests
     *     that HAL processed successfully before HAL runs into an error.
     *
     */
    virtual ::android::hardware::Return<void> processCaptureRequest_3_4(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_4::CaptureRequest>& requests, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::BufferCache>& cachesToRemove, processCaptureRequest_3_4_cb _hidl_cb) = 0;

    /**
     * Return callback for configureStreams_3_5
     */
    using configureStreams_3_5_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::device::V3_4::HalStreamConfiguration& halConfiguration)>;
    /**
     * configureStreams_3_5:
     *
     * Identical to @3.4::ICameraDeviceSession.configureStreams, except that:
     *
     * - a streamConfigCounter counter is provided to check for race condition
     *   between configureStreams_3_5 and signalStreamFlush call.
     * - In case the HAL overrides dataspace or format for
     *   IMPLEMENTATION_DEFINED pixel format, camera framework must use original
     *   dataspace and format in subsequent configureStreams_3_5 calls for the same
     *   stream. HAL is allowed to change the overriding behavior of format or
     *   dataspace for reconfiguration of the same stream depending on the stream
     *   combination.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On successful stream configuration.
     *     INTERNAL_ERROR:
     *         If there has been a fatal error and the device is no longer
     *         operational. Only close() can be called successfully by the
     *         framework after this error is returned.
     *     ILLEGAL_ARGUMENT:
     *         If the requested stream configuration is invalid. Some examples
     *         of invalid stream configurations include:
     *           - Including more than 1 INPUT stream
     *           - Not including any OUTPUT streams
     *           - Including streams with unsupported formats, or an unsupported
     *             size for that format.
     *           - Including too many output streams of a certain format.
     *           - Unsupported rotation configuration
     *           - Stream sizes/formats don't satisfy the
     *             StreamConfigurationMode requirements
     *             for non-NORMAL mode, or the requested operation_mode is not
     *             supported by the HAL.
     *           - Unsupported usage flag
     *         The camera service cannot filter out all possible illegal stream
     *         configurations, since some devices may support more simultaneous
     *         streams or larger stream resolutions than the minimum required
     *         for a given camera device hardware level. The HAL must return an
     *         ILLEGAL_ARGUMENT for any unsupported stream set, and then be
     *         ready to accept a future valid stream configuration in a later
     *         configureStreams call.
     * @return halConfiguration The stream parameters desired by the HAL for
     *     each stream, including maximum buffers, the usage flags, and the
     *     override format.
     */
    virtual ::android::hardware::Return<void> configureStreams_3_5(const ::android::hardware::camera::device::V3_5::StreamConfiguration& requestedConfiguration, configureStreams_3_5_cb _hidl_cb) = 0;

    /**
     * signalStreamFlush:
     *
     * Signaling HAL camera service is about to perform configureStreams_3_5 and
     * HAL must return all buffers of designated streams. HAL must finish
     * inflight requests normally and return all buffers that belongs to the
     * designated streams through processCaptureResult or returnStreamBuffer
     * API in a timely manner, or camera service will run into a fatal error.
     *
     * Note that this call serves as an optional hint and camera service may
     * skip sending this call if all buffers are already returned.
     *
     * @param streamIds The ID of streams camera service need all of its
     *     buffers returned.
     *
     * @param streamConfigCounter Note that due to concurrency nature, it is
     *     possible the signalStreamFlush call arrives later than the
     *     corresponding configureStreams_3_5 call, HAL must check
     *     streamConfigCounter for such race condition. If the counter is less
     *     than the counter in the last configureStreams_3_5 call HAL last
     *     received, the call is stale and HAL should just return this call.
     */
    virtual ::android::hardware::Return<void> signalStreamFlush(const ::android::hardware::hidl_vec<int32_t>& streamIds, uint32_t streamConfigCounter) = 0;

    /**
     * Return callback for isReconfigurationRequired
     */
    using isReconfigurationRequired_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, bool reconfigurationNeeded)>;
    /**
     * isReconfigurationRequired:
     *
     * Check whether complete stream reconfiguration is required for possible new session
     * parameter values.
     *
     * This method must be called by the camera framework in case the client changes
     * the value of any advertised session parameters. Depending on the specific values
     * the HAL can decide whether a complete stream reconfiguration is required. In case
     * the HAL returns false, the camera framework must skip the internal reconfiguration.
     * In case Hal returns true, the framework must reconfigure the streams and pass the
     * new session parameter values accordingly.
     * This call may be done by the framework some time before the request with new parameters
     * is submitted to the HAL, and the request may be cancelled before it ever gets submitted.
     * Therefore, the HAL must not use this query as an indication to change its behavior in any
     * way.
     * ------------------------------------------------------------------------
     *
     * Preconditions:
     *
     * The framework can call this method at any time after active
     * session configuration. There must be no impact on the performance of
     * pending camera requests in any way. In particular there must not be
     * any glitches or delays during normal camera streaming.
     *
     * Performance requirements:
     * HW and SW camera settings must not be changed and there must not be
     * a user-visible impact on camera performance.
     *
     * @param oldSessionParams Before session parameters, usually the current session parameters.
     * @param newSessionParams The new session parameters which may be set by client.
     *
     * @return Status Status code for the operation, one of:
     *     OK:
     *          On successful reconfiguration required query.
     *     METHOD_NOT_SUPPORTED:
     *          The camera device does not support the reconfiguration query.
     *     INTERNAL_ERROR:
     *          The reconfiguration query cannot complete due to internal
     *          error.
     * @return true in case the stream reconfiguration is required, false otherwise.
     */
    virtual ::android::hardware::Return<void> isReconfigurationRequired(const ::android::hardware::hidl_vec<uint8_t>& oldSessionParams, const ::android::hardware::hidl_vec<uint8_t>& newSessionParams, isReconfigurationRequired_cb _hidl_cb) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_4::ICameraDeviceSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_3::ICameraDeviceSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_2::ICameraDeviceSession>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<ICameraDeviceSession> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceSession> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceSession> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDeviceSession> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICameraDeviceSession> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceSession> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDeviceSession> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDeviceSession> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceSession>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::camera::device::V3_5::ICameraDeviceSession::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V3_5
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_ICAMERADEVICESESSION_H
