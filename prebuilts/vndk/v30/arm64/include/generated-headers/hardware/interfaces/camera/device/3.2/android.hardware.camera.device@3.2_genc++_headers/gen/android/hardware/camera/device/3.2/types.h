#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_2_TYPES_H

#include <android/hardware/graphics/common/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_2 {

// Forward declaration for forward reference support:
enum class StreamType : uint32_t;
enum class StreamRotation : uint32_t;
enum class StreamConfigurationMode : uint32_t;
struct Stream;
struct StreamConfiguration;
struct HalStream;
struct HalStreamConfiguration;
enum class BufferStatus : uint32_t;
struct StreamBuffer;
enum class CameraBlobId : uint16_t;
struct CameraBlob;
enum class MsgType : uint32_t;
enum class ErrorCode : uint32_t;
struct ErrorMsg;
struct ShutterMsg;
struct NotifyMsg;
enum class RequestTemplate : uint32_t;
struct CaptureRequest;
struct CaptureResult;
struct BufferCache;

typedef ::android::hardware::hidl_vec<uint8_t> CameraMetadata;

typedef ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::BufferUsage> BufferUsageFlags;

typedef ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::Dataspace> DataspaceFlags;

/**
 * StreamType:
 *
 * The type of the camera stream, which defines whether the camera HAL device is
 * the producer or the consumer for that stream, and how the buffers of the
 * stream relate to the other streams.
 */
enum class StreamType : uint32_t {
    /**
     * This stream is an output stream; the camera HAL device must fill buffers
     * from this stream with newly captured or reprocessed image data.
     */
    OUTPUT = 0u,
    /**
     * This stream is an input stream; the camera HAL device must read buffers
     * from this stream and send them through the camera processing pipeline,
     * as if the buffer was a newly captured image from the imager.
     *
     * The pixel format for input stream can be any format reported by
     * android.scaler.availableInputOutputFormatsMap. The pixel format of the
     * output stream that is used to produce the reprocessing data may be any
     * format reported by android.scaler.availableStreamConfigurations. The
     * supported input/output stream combinations depends the camera device
     * capabilities, see android.scaler.availableInputOutputFormatsMap for
     * stream map details.
     *
     * This kind of stream is generally used to reprocess data into higher
     * quality images (that otherwise would cause a frame rate performance
     * loss), or to do off-line reprocessing.
     *
     * The typical use cases are OPAQUE (typically ZSL) and YUV reprocessing,
     * see S8.2, S8.3 and S10 for more details.
     */
    INPUT = 1u,
};

/**
 * StreamRotation:
 *
 * The required counterclockwise rotation of camera stream.
 */
enum class StreamRotation : uint32_t {
    /**
     * No rotation
     */
    ROTATION_0 = 0u,
    /**
     * Rotate by 90 degree counterclockwise
     */
    ROTATION_90 = 1u,
    /**
     * Rotate by 180 degree counterclockwise
     */
    ROTATION_180 = 2u,
    /**
     * Rotate by 270 degree counterclockwise
     */
    ROTATION_270 = 3u,
};

/**
 * StreamConfigurationMode:
 *
 * This defines the general operation mode for the HAL (for a given stream
 * configuration) where modes besides NORMAL have different semantics, and
 * usually limit the generality of the API in exchange for higher performance in
 * some particular area.
 */
enum class StreamConfigurationMode : uint32_t {
    /**
     * Normal stream configuration operation mode. This is the default camera
     * operation mode, where all semantics of HAL APIs and metadata controls
     * apply.
     */
    NORMAL_MODE = 0u,
    /**
     * Special constrained high speed operation mode for devices that can not
     * support high speed output in NORMAL mode. All streams in this
     * configuration are operating at high speed mode and have different
     * characteristics and limitations to achieve high speed output. The NORMAL
     * mode can still be used for high speed output if the HAL can support high
     * speed output while satisfying all the semantics of HAL APIs and metadata
     * controls. It is recommended for the HAL to support high speed output in
     * NORMAL mode (by advertising the high speed FPS ranges in
     * android.control.aeAvailableTargetFpsRanges) if possible.
     *
     * This mode has below limitations/requirements:
     *
     *   1. The HAL must support up to 2 streams with sizes reported by
     *      android.control.availableHighSpeedVideoConfigurations.
     *   2. In this mode, the HAL is expected to output up to 120fps or
     *      higher. This mode must support the targeted FPS range and size
     *      configurations reported by
     *      android.control.availableHighSpeedVideoConfigurations.
     *   3. The HAL must support IMPLEMENTATION_DEFINED output
     *      stream format.
     *   4. To achieve efficient high speed streaming, the HAL may have to
     *      aggregate multiple frames together and send to camera device for
     *      processing where the request controls are same for all the frames in
     *      this batch (batch mode). The HAL must support max batch size and the
     *      max batch size requirements defined by
     *      android.control.availableHighSpeedVideoConfigurations.
     *   5. In this mode, the HAL must override aeMode, awbMode, and afMode to
     *      ON, ON, and CONTINUOUS_VIDEO, respectively. All post-processing
     *      block mode controls must be overridden to be FAST. Therefore, no
     *      manual control of capture and post-processing parameters is
     *      possible. All other controls operate the same as when
     *      android.control.mode == AUTO. This means that all other
     *      android.control.* fields must continue to work, such as
     *
     *      android.control.aeTargetFpsRange
     *      android.control.aeExposureCompensation
     *      android.control.aeLock
     *      android.control.awbLock
     *      android.control.effectMode
     *      android.control.aeRegions
     *      android.control.afRegions
     *      android.control.awbRegions
     *      android.control.afTrigger
     *      android.control.aePrecaptureTrigger
     *
     *      Outside of android.control.*, the following controls must work:
     *
     *      android.flash.mode (TORCH mode only, automatic flash for still
     *          capture must not work since aeMode is ON)
     *      android.lens.opticalStabilizationMode (if it is supported)
     *      android.scaler.cropRegion
     *      android.statistics.faceDetectMode (if it is supported)
     *   6. To reduce the amount of data passed across process boundaries at
     *      high frame rate, within one batch, camera framework only propagates
     *      the last shutter notify and the last capture results (including partial
     *      results and final result) to the app. The shutter notifies and capture
     *      results for the other requests in the batch are derived by
     *      the camera framework. As a result, the HAL can return empty metadata
     *      except for the last result in the batch.
     *
     * For more details about high speed stream requirements, see
     * android.control.availableHighSpeedVideoConfigurations and
     * CONSTRAINED_HIGH_SPEED_VIDEO capability defined in
     * android.request.availableCapabilities.
     *
     * This mode only needs to be supported by HALs that include
     * CONSTRAINED_HIGH_SPEED_VIDEO in the android.request.availableCapabilities
     * static metadata.
     */
    CONSTRAINED_HIGH_SPEED_MODE = 1u,
    /**
     * A set of vendor-defined operating modes, for custom default camera
     * application features that can't be implemented in the fully flexible fashion
     * required for NORMAL_MODE.
     */
    VENDOR_MODE_0 = 32768u /* 0x8000 */,
    VENDOR_MODE_1 = 32769u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_0 implicitly + 1 */,
    VENDOR_MODE_2 = 32770u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_1 implicitly + 1 */,
    VENDOR_MODE_3 = 32771u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_2 implicitly + 1 */,
    VENDOR_MODE_4 = 32772u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_3 implicitly + 1 */,
    VENDOR_MODE_5 = 32773u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_4 implicitly + 1 */,
    VENDOR_MODE_6 = 32774u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_5 implicitly + 1 */,
    VENDOR_MODE_7 = 32775u /* ::android::hardware::camera::device::V3_2::StreamConfigurationMode.VENDOR_MODE_6 implicitly + 1 */,
};

/**
 * Stream:
 *
 * A descriptor for a single camera input or output stream. A stream is defined
 * by the framework by its buffer resolution and format, and additionally by the
 * HAL with the gralloc usage flags and the maximum in-flight buffer count.
 *
 * If a configureStreams() call returns a non-fatal error, all active streams
 * remain valid as if configureStreams() had not been called.
 *
 */
struct Stream final {
    /**
     * Stream ID - a nonnegative integer identifier for a stream.
     *
     * The identical stream ID must reference the same stream, with the same
     * width/height/format, across consecutive calls to configureStreams.
     *
     * If previously-used stream ID is not used in a new call to
     * configureStreams, then that stream is no longer active. Such a stream ID
     * may be reused in a future configureStreams with a new
     * width/height/format.
     *
     */
    int32_t id __attribute__ ((aligned(4)));
    /**
     * The type of the stream (input vs output, etc).
     */
    ::android::hardware::camera::device::V3_2::StreamType streamType __attribute__ ((aligned(4)));
    /**
     * The width in pixels of the buffers in this stream
     */
    uint32_t width __attribute__ ((aligned(4)));
    /**
     * The height in pixels of the buffers in this stream
     */
    uint32_t height __attribute__ ((aligned(4)));
    /**
     * The pixel format for the buffers in this stream.
     *
     * If IMPLEMENTATION_DEFINED is used, then the platform
     * gralloc module must select a format based on the usage flags provided by
     * the camera device and the other endpoint of the stream.
     *
     */
    ::android::hardware::graphics::common::V1_0::PixelFormat format __attribute__ ((aligned(4)));
    /**
     * The gralloc usage flags for this stream, as needed by the consumer of
     * the stream.
     *
     * The usage flags from the producer and the consumer must be combined
     * together and then passed to the platform gralloc HAL module for
     * allocating the gralloc buffers for each stream.
     *
     * The HAL may use these consumer flags to decide stream configuration. For
     * streamType INPUT, the value of this field is always 0. For all streams
     * passed via configureStreams(), the HAL must set its own
     * additional usage flags in its output HalStreamConfiguration.
     *
     * The usage flag for an output stream may be bitwise combination of usage
     * flags for multiple consumers, for the purpose of sharing one camera
     * stream between those consumers. The HAL must fail configureStreams call
     * with ILLEGAL_ARGUMENT if the combined flags cannot be supported due to
     * imcompatible buffer format, dataSpace, or other hardware limitations.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::BufferUsage> usage __attribute__ ((aligned(8)));
    /**
     * A field that describes the contents of the buffer. The format and buffer
     * dimensions define the memory layout and structure of the stream buffers,
     * while dataSpace defines the meaning of the data within the buffer.
     *
     * For most formats, dataSpace defines the color space of the image data.
     * In addition, for some formats, dataSpace indicates whether image- or
     * depth-based data is requested. See
     * android.hardware.graphics.common@1.0::types for details of formats and
     * valid dataSpace values for each format.
     *
     * The HAL must use this dataSpace to configure the stream to the correct
     * colorspace, or to select between color and depth outputs if
     * supported. The dataspace values are set using the V0 dataspace
     * definitions.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::Dataspace> dataSpace __attribute__ ((aligned(4)));
    /**
     * The required output rotation of the stream.
     *
     * This must be inspected by HAL along with stream width and height. For
     * example, if the rotation is 90 degree and the stream width and height is
     * 720 and 1280 respectively, camera service must supply buffers of size
     * 720x1280, and HAL must capture a 1280x720 image and rotate the image by
     * 90 degree counterclockwise. The rotation field must be ignored when the
     * stream type is input.
     *
     * The HAL must inspect this field during stream configuration and return
     * IllegalArgument if HAL cannot perform such rotation. HAL must always
     * support ROTATION_0, so a configureStreams() call must not fail for
     * unsupported rotation if rotation field of all streams is ROTATION_0.
     *
     */
    ::android::hardware::camera::device::V3_2::StreamRotation rotation __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, streamType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, width) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, height) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, format) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, usage) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, dataSpace) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::Stream, rotation) == 36, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::Stream) == 40, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::Stream) == 8, "wrong alignment");

/**
 * StreamConfiguration:
 *
 * A structure of stream definitions, used by configureStreams(). This
 * structure defines all the output streams and the reprocessing input
 * stream for the current camera use case.
 */
struct StreamConfiguration final {
    /**
     * An array of camera stream pointers, defining the input/output
     * configuration for the camera HAL device.
     *
     * At most one input-capable stream may be defined.
     * At least one output-capable stream must be defined.
     */
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::Stream> streams __attribute__ ((aligned(8)));
    /**
     * The operation mode of streams in this configuration. The HAL can use this
     * mode as an indicator to set the stream property (e.g.,
     * HalStream::maxBuffers) appropriately. For example, if the
     * configuration is
     * CONSTRAINED_HIGH_SPEED_MODE, the HAL may
     * want to set aside more buffers for batch mode operation (see
     * android.control.availableHighSpeedVideoConfigurations for batch mode
     * definition).
     *
     */
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode operationMode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamConfiguration, streams) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamConfiguration, operationMode) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::StreamConfiguration) == 24, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::StreamConfiguration) == 8, "wrong alignment");

/**
 * HalStream:
 *
 * The camera HAL's response to each requested stream configuration.
 *
 * The HAL may specify the desired format, maximum buffers, and
 * usage flags for each stream.
 *
 */
struct HalStream final {
    /**
     * Stream ID - a nonnegative integer identifier for a stream.
     *
     * The ID must be one of the stream IDs passed into configureStreams.
     */
    int32_t id __attribute__ ((aligned(4)));
    /**
     * An override pixel format for the buffers in this stream.
     *
     * The HAL must respect the requested format in Stream unless it is
     * IMPLEMENTATION_DEFINED, in which case the override format here must be
     * used by the client instead, for this stream. This allows cross-platform
     * HALs to use a standard format since IMPLEMENTATION_DEFINED formats often
     * require device-specific information. In all other cases, the
     * overrideFormat must match the requested format.
     *
     * When HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED is used, then the platform
     * gralloc module must select a format based on the usage flags provided by
     * the camera device and the other endpoint of the stream.
     */
    ::android::hardware::graphics::common::V1_0::PixelFormat overrideFormat __attribute__ ((aligned(4)));
    /**
     * The gralloc usage flags for this stream, as needed by the HAL.
     *
     * For output streams, these are the HAL's producer usage flags. For input
     * streams, these are the HAL's consumer usage flags. The usage flags from
     * the producer and the consumer must be combined together and then passed
     * to the platform graphics allocator HAL for allocating the gralloc buffers
     * for each stream.
     *
     * If the stream's type is INPUT, then producerUsage must be 0, and
     * consumerUsage must be set. For other types, producerUsage must be set,
     * and consumerUsage must be 0.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::BufferUsage> producerUsage __attribute__ ((aligned(8)));
    ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::BufferUsage> consumerUsage __attribute__ ((aligned(8)));
    /**
     * The maximum number of buffers the HAL device may need to have dequeued at
     * the same time. The HAL device may not have more buffers in-flight from
     * this stream than this value.
     */
    uint32_t maxBuffers __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::HalStream, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::HalStream, overrideFormat) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::HalStream, producerUsage) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::HalStream, consumerUsage) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::HalStream, maxBuffers) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::HalStream) == 32, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::HalStream) == 8, "wrong alignment");

/**
 * HalStreamConfiguration:
 *
 * A structure of stream definitions, returned by configureStreams(). This
 * structure defines the HAL's desired parameters for each stream.
 *
 * All streams that were defined in the input to configureStreams() must have a
 * corresponding entry in this structure when returned by configureStreams().
 */
struct HalStreamConfiguration final {
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::HalStream> streams __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::HalStreamConfiguration, streams) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::HalStreamConfiguration) == 16, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::HalStreamConfiguration) == 8, "wrong alignment");

/**
 * BufferStatus:
 *
 * The current status of a single stream buffer.
 */
enum class BufferStatus : uint32_t {
    /**
     * The buffer is in a normal state, and can be used after waiting on its
     * sync fence.
     */
    OK = 0u,
    /**
     * The buffer does not contain valid data, and the data in it must not be
     * used. The sync fence must still be waited on before reusing the buffer.
     */
    ERROR = 1u,
};

/**
 * StreamBuffer:
 *
 * A single buffer from a camera3 stream. It includes a handle to its parent
 * stream, the handle to the gralloc buffer itself, and sync fences
 *
 * The buffer does not specify whether it is to be used for input or output;
 * that is determined by its parent stream type and how the buffer is passed to
 * the HAL device.
 */
struct StreamBuffer final {
    /**
     * The ID of the stream this buffer is associated with. -1 indicates an
     * invalid (empty) StreamBuffer, in which case buffer must also point to
     * null and bufferId must be 0.
     */
    int32_t streamId __attribute__ ((aligned(4)));
    /**
     * The unique ID of the buffer within this StreamBuffer. 0 indicates this
     * StreamBuffer contains no buffer.
     * For StreamBuffers sent to the HAL in a CaptureRequest, this ID uniquely
     * identifies a buffer. When a buffer is sent to HAL for the first time,
     * both bufferId and buffer handle must be filled. HAL must keep track of
     * the mapping between bufferId and corresponding buffer until the
     * corresponding stream is removed from stream configuration or until camera
     * device session is closed. After the first time a buffer is introduced to
     * HAL, in the future camera service must refer to the same buffer using
     * only bufferId, and keep the buffer handle null.
     */
    uint64_t bufferId __attribute__ ((aligned(8)));
    /**
     * The graphics buffer handle to the buffer.
     *
     * For StreamBuffers sent to the HAL in a CaptureRequest, if the bufferId
     * is not seen by the HAL before, this buffer handle is guaranteed to be a
     * valid handle to a graphics buffer, with dimensions and format matching
     * that of the stream. If the bufferId has been sent to the HAL before, this
     * buffer handle must be null and HAL must look up the actual buffer handle
     * to use from its own bufferId to buffer handle map.
     *
     * For StreamBuffers returned in a CaptureResult, this must be null, since
     * the handle to the buffer is already known to the client (since the client
     * sent it in the matching CaptureRequest), and the handle can be identified
     * by the combination of frame number and stream ID.
     */
    ::android::hardware::hidl_handle buffer __attribute__ ((aligned(8)));
    /**
     * Current state of the buffer. The framework must not pass buffers to the
     * HAL that are in an error state. In case a buffer could not be filled by
     * the HAL, it must have its status set to ERROR when returned to the
     * framework with processCaptureResult().
     */
    ::android::hardware::camera::device::V3_2::BufferStatus status __attribute__ ((aligned(4)));
    /**
     * The acquire sync fence for this buffer. The HAL must wait on this fence
     * fd before attempting to read from or write to this buffer.
     *
     * In a buffer included in a CaptureRequest, the client may set this to null
     * to indicate that no waiting is necessary for this buffer.
     *
     * When the HAL returns an input or output buffer to the framework with
     * processCaptureResult(), the acquireFence must be set to null. If the HAL
     * never waits on the acquireFence due to an error in filling or reading a
     * buffer, when calling processCaptureResult() the HAL must set the
     * releaseFence of the buffer to be the acquireFence passed to it by the
     * client. This allows the client to wait on the fence before reusing the
     * buffer.
     */
    ::android::hardware::hidl_handle acquireFence __attribute__ ((aligned(8)));
    /**
     * The release sync fence for this buffer. The HAL must set this to a valid
     * fence fd when returning the input buffer or output buffers to the client
     * in a CaptureResult, or set it to null to indicate that no waiting is
     * required for this buffer.
     *
     * The client must set this to be null for all buffers included in a
     * processCaptureRequest call.
     *
     * After signaling the releaseFence for this buffer, the HAL
     * must not make any further attempts to access this buffer as the
     * ownership has been fully transferred back to the client.
     *
     * If this is null, then the ownership of this buffer is transferred back
     * immediately upon the call of processCaptureResult.
     */
    ::android::hardware::hidl_handle releaseFence __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamBuffer, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamBuffer, bufferId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamBuffer, buffer) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamBuffer, status) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamBuffer, acquireFence) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::StreamBuffer, releaseFence) == 56, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::StreamBuffer) == 72, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::StreamBuffer) == 8, "wrong alignment");

/**
 * CameraBlob:
 *
 * Transport header for camera blob types; generally compressed JPEG buffers in
 * output streams.
 *
 * To capture JPEG images, a stream is created using the pixel format
 * HAL_PIXEL_FORMAT_BLOB and dataspace HAL_DATASPACE_V0_JFIF. The buffer size
 * for the stream is calculated by the framework, based on the static metadata
 * field android.jpeg.maxSize. Since compressed JPEG images are of variable
 * size, the HAL needs to include the final size of the compressed image using
 * this structure inside the output stream buffer. The camera blob ID field must
 * be set to CameraBlobId::JPEG.
 *
 * The transport header must be at the end of the JPEG output stream
 * buffer. That means the jpegBlobId must start at byte[buffer_size -
 * sizeof(CameraBlob)], where the buffer_size is the size of gralloc
 * buffer. Any HAL using this transport header must account for it in
 * android.jpeg.maxSize. The JPEG data itself starts at the beginning of the
 * buffer and must be blobSize bytes long.
 */
enum class CameraBlobId : uint16_t {
    JPEG = 255 /* 0x00FF */,
};

struct CameraBlob final {
    ::android::hardware::camera::device::V3_2::CameraBlobId blobId __attribute__ ((aligned(2)));
    uint32_t blobSize __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::CameraBlob, blobId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CameraBlob, blobSize) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::CameraBlob) == 8, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::CameraBlob) == 4, "wrong alignment");

/**
 * MsgType:
 *
 * Indicates the type of message sent, which specifies which member of the
 * message union is valid.
 *
 */
enum class MsgType : uint32_t {
    /**
     * An error has occurred. NotifyMsg::Message::Error contains the
     * error information.
     */
    ERROR = 1u,
    /**
     * The exposure of a given request or processing a reprocess request has
     * begun. NotifyMsg::Message::Shutter contains the information
     * the capture.
     */
    SHUTTER = 2u,
};

/**
 * Defined error codes for MsgType::ERROR
 */
enum class ErrorCode : uint32_t {
    /**
     * A serious failure occured. No further frames or buffer streams must
     * be produced by the device. Device must be treated as closed. The
     * client must reopen the device to use it again. The frameNumber field
     * is unused.
     */
    ERROR_DEVICE = 1u,
    /**
     * An error has occurred in processing a request. No output (metadata or
     * buffers) must be produced for this request. The frameNumber field
     * specifies which request has been dropped. Subsequent requests are
     * unaffected, and the device remains operational.
     */
    ERROR_REQUEST = 2u,
    /**
     * An error has occurred in producing an output result metadata buffer
     * for a request, but output stream buffers for it must still be
     * available. Subsequent requests are unaffected, and the device remains
     * operational. The frameNumber field specifies the request for which
     * result metadata won't be available.
     */
    ERROR_RESULT = 3u,
    /**
     * An error has occurred in placing an output buffer into a stream for a
     * request. The frame metadata and other buffers may still be
     * available. Subsequent requests are unaffected, and the device remains
     * operational. The frameNumber field specifies the request for which the
     * buffer was dropped, and errorStreamId indicates the stream
     * that dropped the frame.
     */
    ERROR_BUFFER = 4u,
};

/**
 * ErrorMsg:
 *
 * Message contents for MsgType::ERROR
 */
struct ErrorMsg final {
    /**
     * Frame number of the request the error applies to. 0 if the frame number
     * isn't applicable to the error.
     */
    uint32_t frameNumber __attribute__ ((aligned(4)));
    /**
     * Pointer to the stream that had a failure. -1 if the stream isn't
     * applicable to the error.
     */
    int32_t errorStreamId __attribute__ ((aligned(4)));
    /**
     * The code for this error.
     */
    ::android::hardware::camera::device::V3_2::ErrorCode errorCode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::ErrorMsg, frameNumber) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::ErrorMsg, errorStreamId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::ErrorMsg, errorCode) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::ErrorMsg) == 12, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::ErrorMsg) == 4, "wrong alignment");

/**
 * ShutterMsg:
 *
 * Message contents for MsgType::SHUTTER
 */
struct ShutterMsg final {
    /**
     * Frame number of the request that has begun exposure or reprocessing.
     */
    uint32_t frameNumber __attribute__ ((aligned(4)));
    /**
     * Timestamp for the start of capture. For a reprocess request, this must
     * be input image's start of capture. This must match the capture result
     * metadata's sensor exposure start timestamp.
     */
    uint64_t timestamp __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::ShutterMsg, frameNumber) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::ShutterMsg, timestamp) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::ShutterMsg) == 16, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::ShutterMsg) == 8, "wrong alignment");

/**
 * NotifyMsg:
 *
 * The message structure sent to ICameraDevice3Callback::notify()
 */
struct NotifyMsg final {
    // Forward declaration for forward reference support:
    union Message;

    union Message final {
        /**
         * Error message contents. Valid if type is MsgType::ERROR
         */
        ::android::hardware::camera::device::V3_2::ErrorMsg error __attribute__ ((aligned(4)));
        /**
         * Shutter message contents. Valid if type is MsgType::SHUTTER
         */
        ::android::hardware::camera::device::V3_2::ShutterMsg shutter __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::camera::device::V3_2::NotifyMsg::Message, error) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::camera::device::V3_2::NotifyMsg::Message, shutter) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::camera::device::V3_2::NotifyMsg::Message) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::camera::device::V3_2::NotifyMsg::Message) == 8, "wrong alignment");

    /**
     * The message type.
     */
    ::android::hardware::camera::device::V3_2::MsgType type __attribute__ ((aligned(4)));
    ::android::hardware::camera::device::V3_2::NotifyMsg::Message msg __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::NotifyMsg, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::NotifyMsg, msg) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::NotifyMsg) == 24, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::NotifyMsg) == 8, "wrong alignment");

/**
 * RequestTemplate:
 *
 * Available template types for
 * ICameraDevice::constructDefaultRequestSettings()
 */
enum class RequestTemplate : uint32_t {
    /**
     * Standard camera preview operation with 3A on auto.
     */
    PREVIEW = 1u,
    /**
     * Standard camera high-quality still capture with 3A and flash on auto.
     */
    STILL_CAPTURE = 2u,
    /**
     * Standard video recording plus preview with 3A on auto, torch off.
     */
    VIDEO_RECORD = 3u,
    /**
     * High-quality still capture while recording video. Applications typically
     * include preview, video record, and full-resolution YUV or JPEG streams in
     * request. Must not cause stuttering on video stream. 3A on auto.
     */
    VIDEO_SNAPSHOT = 4u,
    /**
     * Zero-shutter-lag mode. Application typically request preview and
     * full-resolution data for each frame, and reprocess it to JPEG when a
     * still image is requested by user. Settings must provide highest-quality
     * full-resolution images without compromising preview frame rate. 3A on
     * auto.
     */
    ZERO_SHUTTER_LAG = 5u,
    /**
     * A basic template for direct application control of capture
     * parameters. All automatic control is disabled (auto-exposure, auto-white
     * balance, auto-focus), and post-processing parameters are set to preview
     * quality. The manual capture parameters (exposure, sensitivity, etc.)
     * are set to reasonable defaults, but may be overridden by the
     * application depending on the intended use case.
     */
    MANUAL = 6u,
    /**
     * First value for vendor-defined request templates
     */
    VENDOR_TEMPLATE_START = 1073741824u /* 0x40000000 */,
};

/**
 * CaptureRequest:
 *
 * A single request for image capture/buffer reprocessing, sent to the Camera
 * HAL device by the framework in processCaptureRequest().
 *
 * The request contains the settings to be used for this capture, and the set of
 * output buffers to write the resulting image data in. It may optionally
 * contain an input buffer, in which case the request is for reprocessing that
 * input buffer instead of capturing a new image with the camera sensor. The
 * capture is identified by the frameNumber.
 *
 * In response, the camera HAL device must send a CaptureResult
 * structure asynchronously to the framework, using the processCaptureResult()
 * callback.
 */
struct CaptureRequest final {
    /**
     * The frame number is an incrementing integer set by the framework to
     * uniquely identify this capture. It needs to be returned in the result
     * call, and is also used to identify the request in asynchronous
     * notifications sent to ICameraDevice3Callback::notify().
     */
    uint32_t frameNumber __attribute__ ((aligned(4)));
    /**
     * If non-zero, read settings from request queue instead
     * (see ICameraDeviceSession.getCaptureRequestMetadataQueue).
     * If zero, read settings from .settings field.
     */
    uint64_t fmqSettingsSize __attribute__ ((aligned(8)));
    /**
     * If fmqSettingsSize is zero,
     * the settings buffer contains the capture and processing parameters for
     * the request. As a special case, an empty settings buffer indicates that
     * the settings are identical to the most-recently submitted capture
     * request. A empty buffer cannot be used as the first submitted request
     * after a configureStreams() call.
     *
     * This field must be used if fmqSettingsSize is zero. It must not be used
     * if fmqSettingsSize is non-zero.
     */
    ::android::hardware::hidl_vec<uint8_t> settings __attribute__ ((aligned(8)));
    /**
     * The input stream buffer to use for this request, if any.
     *
     * An invalid inputBuffer is signified by a null inputBuffer::buffer, in
     * which case the value of all other members of inputBuffer must be ignored.
     *
     * If inputBuffer is invalid, then the request is for a new capture from the
     * imager. If inputBuffer is valid, the request is for reprocessing the
     * image contained in inputBuffer, and the HAL must release the inputBuffer
     * back to the client in a subsequent processCaptureResult call.
     *
     * The HAL is required to wait on the acquire sync fence of the input buffer
     * before accessing it.
     *
     */
    ::android::hardware::camera::device::V3_2::StreamBuffer inputBuffer __attribute__ ((aligned(8)));
    /**
     * An array of at least 1 stream buffers, to be filled with image
     * data from this capture/reprocess. The HAL must wait on the acquire fences
     * of each stream buffer before writing to them.
     *
     * The HAL takes ownership of the handles in outputBuffers; the client
     * must not access them until they are returned in a CaptureResult.
     *
     * Any or all of the buffers included here may be brand new in this
     * request (having never before seen by the HAL).
     */
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer> outputBuffers __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureRequest, frameNumber) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureRequest, fmqSettingsSize) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureRequest, settings) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureRequest, inputBuffer) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureRequest, outputBuffers) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::CaptureRequest) == 120, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::CaptureRequest) == 8, "wrong alignment");

/**
 * CaptureResult:
 *
 * The result of a single capture/reprocess by the camera HAL device. This is
 * sent to the framework asynchronously with processCaptureResult(), in
 * response to a single capture request sent to the HAL with
 * processCaptureRequest(). Multiple processCaptureResult() calls may be
 * performed by the HAL for each request.
 *
 * Each call, all with the same frame
 * number, may contain some subset of the output buffers, and/or the result
 * metadata.
 *
 * The result structure contains the output metadata from this capture, and the
 * set of output buffers that have been/will be filled for this capture. Each
 * output buffer may come with a release sync fence that the framework must wait
 * on before reading, in case the buffer has not yet been filled by the HAL.
 *
 * The metadata may be provided multiple times for a single frame number. The
 * framework must accumulate together the final result set by combining each
 * partial result together into the total result set.
 *
 * If an input buffer is given in a request, the HAL must return it in one of
 * the processCaptureResult calls, and the call may be to just return the
 * input buffer, without metadata and output buffers; the sync fences must be
 * handled the same way they are done for output buffers.
 *
 * Performance considerations:
 *
 * Applications receive these partial results immediately, so sending partial
 * results is a highly recommended performance optimization to avoid the total
 * pipeline latency before sending the results for what is known very early on
 * in the pipeline.
 *
 * A typical use case might be calculating the AF state halfway through the
 * pipeline; by sending the state back to the framework immediately, we get a
 * 50% performance increase and perceived responsiveness of the auto-focus.
 *
 */
struct CaptureResult final {
    /**
     * The frame number is an incrementing integer set by the framework in the
     * submitted request to uniquely identify this capture. It is also used to
     * identify the request in asynchronous notifications sent to
     * ICameraDevice3Callback::notify().
     */
    uint32_t frameNumber __attribute__ ((aligned(4)));
    /**
     * If non-zero, read result from result queue instead
     * (see ICameraDeviceSession.getCaptureResultMetadataQueue).
     * If zero, read result from .result field.
     */
    uint64_t fmqResultSize __attribute__ ((aligned(8)));
    /**
     * The result metadata for this capture. This contains information about the
     * final capture parameters, the state of the capture and post-processing
     * hardware, the state of the 3A algorithms, if enabled, and the output of
     * any enabled statistics units.
     *
     * If there was an error producing the result metadata, result must be an
     * empty metadata buffer, and notify() must be called with
     * ErrorCode::ERROR_RESULT.
     *
     * Multiple calls to processCaptureResult() with a given frameNumber
     * may include (partial) result metadata.
     *
     * Partial metadata submitted must not include any metadata key returned
     * in a previous partial result for a given frame. Each new partial result
     * for that frame must also set a distinct partialResult value.
     *
     * If notify has been called with ErrorCode::ERROR_RESULT, all further
     * partial results for that frame are ignored by the framework.
     */
    ::android::hardware::hidl_vec<uint8_t> result __attribute__ ((aligned(8)));
    /**
     * The completed output stream buffers for this capture.
     *
     * They may not yet be filled at the time the HAL calls
     * processCaptureResult(); the framework must wait on the release sync
     * fences provided by the HAL before reading the buffers.
     *
     * The StreamBuffer::buffer handle must be null for all returned buffers;
     * the client must cache the handle and look it up via the combination of
     * frame number and stream ID.
     *
     * The number of output buffers returned must be less than or equal to the
     * matching capture request's count. If this is less than the buffer count
     * in the capture request, at least one more call to processCaptureResult
     * with the same frameNumber must be made, to return the remaining output
     * buffers to the framework. This may only be zero if the structure includes
     * valid result metadata or an input buffer is returned in this result.
     *
     * The HAL must set the stream buffer's release sync fence to a valid sync
     * fd, or to null if the buffer has already been filled.
     *
     * If the HAL encounters an error while processing the buffer, and the
     * buffer is not filled, the buffer's status field must be set to ERROR. If
     * the HAL did not wait on the acquire fence before encountering the error,
     * the acquire fence must be copied into the release fence, to allow the
     * framework to wait on the fence before reusing the buffer.
     *
     * The acquire fence must be set to null for all output buffers.
     *
     * This vector may be empty; if so, at least one other processCaptureResult
     * call must be made (or have been made) by the HAL to provide the filled
     * output buffers.
     *
     * When processCaptureResult is called with a new buffer for a frame,
     * all previous frames' buffers for that corresponding stream must have been
     * already delivered (the fences need not have yet been signaled).
     *
     * Buffers for a frame may be sent to framework before the corresponding
     * SHUTTER-notify call is made by the HAL.
     *
     * Performance considerations:
     *
     * Buffers delivered to the framework are not dispatched to the
     * application layer until a start of exposure timestamp has been received
     * via a SHUTTER notify() call. It is highly recommended to
     * dispatch that call as early as possible.
     */
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer> outputBuffers __attribute__ ((aligned(8)));
    /**
     * The handle for the input stream buffer for this capture, if any.
     *
     * It may not yet be consumed at the time the HAL calls
     * processCaptureResult(); the framework must wait on the release sync fence
     * provided by the HAL before reusing the buffer.
     *
     * The HAL must handle the sync fences the same way they are done for
     * outputBuffers.
     *
     * Only one input buffer is allowed to be sent per request. Similarly to
     * output buffers, the ordering of returned input buffers must be
     * maintained by the HAL.
     *
     * Performance considerations:
     *
     * The input buffer should be returned as early as possible. If the HAL
     * supports sync fences, it can call processCaptureResult to hand it back
     * with sync fences being set appropriately. If the sync fences are not
     * supported, the buffer can only be returned when it is consumed, which
     * may take long time; the HAL may choose to copy this input buffer to make
     * the buffer return sooner.
     */
    ::android::hardware::camera::device::V3_2::StreamBuffer inputBuffer __attribute__ ((aligned(8)));
    /**
     * In order to take advantage of partial results, the HAL must set the
     * static metadata android.request.partialResultCount to the number of
     * partial results it sends for each frame.
     *
     * Each new capture result with a partial result must set
     * this field to a distinct inclusive value between
     * 1 and android.request.partialResultCount.
     *
     * HALs not wishing to take advantage of this feature must not
     * set an android.request.partialResultCount or partial_result to a value
     * other than 1.
     *
     * This value must be set to 0 when a capture result contains buffers only
     * and no metadata.
     */
    uint32_t partialResult __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureResult, frameNumber) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureResult, fmqResultSize) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureResult, result) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureResult, outputBuffers) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureResult, inputBuffer) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::CaptureResult, partialResult) == 120, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::CaptureResult) == 128, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::CaptureResult) == 8, "wrong alignment");

/**
 * BufferCache:
 *
 * A list of cached bufferIds associated with a certain stream.
 * Buffers are passed between camera service and camera HAL via bufferId except
 * the first time a new buffer is being passed to HAL in CaptureRequest. Camera
 * service and camera HAL therefore need to maintain a cached map of bufferId
 * and corresponing native handle.
 *
 */
struct BufferCache final {
    /**
     * The ID of the stream this list is associated with.
     */
    int32_t streamId __attribute__ ((aligned(4)));
    /**
     * A cached buffer ID associated with streamId.
     */
    uint64_t bufferId __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_2::BufferCache, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_2::BufferCache, bufferId) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_2::BufferCache) == 16, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_2::BufferCache) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::StreamType o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::StreamType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::StreamType lhs, const ::android::hardware::camera::device::V3_2::StreamType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::StreamType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::StreamType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::StreamType lhs, const ::android::hardware::camera::device::V3_2::StreamType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::StreamType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::StreamType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::StreamType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::StreamType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::StreamRotation o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::StreamRotation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::StreamRotation lhs, const ::android::hardware::camera::device::V3_2::StreamRotation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::StreamRotation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::StreamRotation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::StreamRotation lhs, const ::android::hardware::camera::device::V3_2::StreamRotation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::StreamRotation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::StreamRotation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::StreamRotation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::StreamRotation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::StreamConfigurationMode o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::StreamConfigurationMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::StreamConfigurationMode lhs, const ::android::hardware::camera::device::V3_2::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::StreamConfigurationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::StreamConfigurationMode lhs, const ::android::hardware::camera::device::V3_2::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::StreamConfigurationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::StreamConfigurationMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::StreamConfigurationMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::Stream& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::Stream& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::Stream& lhs, const ::android::hardware::camera::device::V3_2::Stream& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::Stream& lhs, const ::android::hardware::camera::device::V3_2::Stream& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_2::StreamConfiguration& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::StreamConfiguration& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::StreamConfiguration& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::StreamConfiguration& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_2::HalStream& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::HalStream& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::HalStream& lhs, const ::android::hardware::camera::device::V3_2::HalStream& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::HalStream& lhs, const ::android::hardware::camera::device::V3_2::HalStream& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::BufferStatus o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::BufferStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::BufferStatus lhs, const ::android::hardware::camera::device::V3_2::BufferStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::BufferStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::BufferStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::BufferStatus lhs, const ::android::hardware::camera::device::V3_2::BufferStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::BufferStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::BufferStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::BufferStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::BufferStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::StreamBuffer& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::StreamBuffer& o, ::std::ostream*);
// operator== and operator!= are not generated for StreamBuffer

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::CameraBlobId o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::CameraBlobId o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::camera::device::V3_2::CameraBlobId lhs, const ::android::hardware::camera::device::V3_2::CameraBlobId rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::camera::device::V3_2::CameraBlobId rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::camera::device::V3_2::CameraBlobId lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::camera::device::V3_2::CameraBlobId lhs, const ::android::hardware::camera::device::V3_2::CameraBlobId rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::camera::device::V3_2::CameraBlobId rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::camera::device::V3_2::CameraBlobId lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::camera::device::V3_2::CameraBlobId e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::camera::device::V3_2::CameraBlobId e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::CameraBlob& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::CameraBlob& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::CameraBlob& lhs, const ::android::hardware::camera::device::V3_2::CameraBlob& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::CameraBlob& lhs, const ::android::hardware::camera::device::V3_2::CameraBlob& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::MsgType o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::MsgType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::MsgType lhs, const ::android::hardware::camera::device::V3_2::MsgType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::MsgType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::MsgType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::MsgType lhs, const ::android::hardware::camera::device::V3_2::MsgType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::MsgType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::MsgType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::MsgType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::MsgType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::ErrorCode o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::ErrorCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::ErrorCode lhs, const ::android::hardware::camera::device::V3_2::ErrorCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::ErrorCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::ErrorCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::ErrorCode lhs, const ::android::hardware::camera::device::V3_2::ErrorCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::ErrorCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::ErrorCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::ErrorCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::ErrorCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::ErrorMsg& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::ErrorMsg& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::ErrorMsg& lhs, const ::android::hardware::camera::device::V3_2::ErrorMsg& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::ErrorMsg& lhs, const ::android::hardware::camera::device::V3_2::ErrorMsg& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_2::ShutterMsg& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::ShutterMsg& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::ShutterMsg& lhs, const ::android::hardware::camera::device::V3_2::ShutterMsg& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::ShutterMsg& lhs, const ::android::hardware::camera::device::V3_2::ShutterMsg& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_2::NotifyMsg::Message& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::NotifyMsg::Message& o, ::std::ostream*);
// operator== and operator!= are not generated for Message

static inline std::string toString(const ::android::hardware::camera::device::V3_2::NotifyMsg& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::NotifyMsg& o, ::std::ostream*);
// operator== and operator!= are not generated for NotifyMsg

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_2::RequestTemplate o);
static inline void PrintTo(::android::hardware::camera::device::V3_2::RequestTemplate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::RequestTemplate lhs, const ::android::hardware::camera::device::V3_2::RequestTemplate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::RequestTemplate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_2::RequestTemplate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::RequestTemplate lhs, const ::android::hardware::camera::device::V3_2::RequestTemplate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_2::RequestTemplate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_2::RequestTemplate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_2::RequestTemplate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_2::RequestTemplate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::CaptureRequest& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::CaptureRequest& o, ::std::ostream*);
// operator== and operator!= are not generated for CaptureRequest

static inline std::string toString(const ::android::hardware::camera::device::V3_2::CaptureResult& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::CaptureResult& o, ::std::ostream*);
// operator== and operator!= are not generated for CaptureResult

static inline std::string toString(const ::android::hardware::camera::device::V3_2::BufferCache& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_2::BufferCache& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_2::BufferCache& lhs, const ::android::hardware::camera::device::V3_2::BufferCache& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_2::BufferCache& lhs, const ::android::hardware::camera::device::V3_2::BufferCache& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::StreamType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::StreamType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::StreamType::OUTPUT) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamType::OUTPUT)) {
        os += (first ? "" : " | ");
        os += "OUTPUT";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamType::OUTPUT;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamType::INPUT) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamType::INPUT)) {
        os += (first ? "" : " | ");
        os += "INPUT";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamType::INPUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::StreamType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::StreamType::OUTPUT) {
        return "OUTPUT";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamType::INPUT) {
        return "INPUT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::StreamType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::StreamRotation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::StreamRotation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_0) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_0)) {
        os += (first ? "" : " | ");
        os += "ROTATION_0";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_0;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_90) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_90)) {
        os += (first ? "" : " | ");
        os += "ROTATION_90";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_90;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_180) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_180)) {
        os += (first ? "" : " | ");
        os += "ROTATION_180";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_180;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_270) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_270)) {
        os += (first ? "" : " | ");
        os += "ROTATION_270";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_270;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::StreamRotation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_0) {
        return "ROTATION_0";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_90) {
        return "ROTATION_90";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_180) {
        return "ROTATION_180";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_270) {
        return "ROTATION_270";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::StreamRotation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::StreamConfigurationMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::StreamConfigurationMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::NORMAL_MODE) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::NORMAL_MODE)) {
        os += (first ? "" : " | ");
        os += "NORMAL_MODE";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::NORMAL_MODE;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE)) {
        os += (first ? "" : " | ");
        os += "CONSTRAINED_HIGH_SPEED_MODE";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_0) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_0)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_0";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_0;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_1) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_1)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_1";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_1;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_2) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_2)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_2";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_2;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_3) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_3)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_3";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_3;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_4) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_4)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_4";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_4;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_5) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_5)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_5";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_5;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_6) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_6)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_6";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_6;
    }
    if ((o & ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_7) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_7)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_7";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_7;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::StreamConfigurationMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::NORMAL_MODE) {
        return "NORMAL_MODE";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE) {
        return "CONSTRAINED_HIGH_SPEED_MODE";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_0) {
        return "VENDOR_MODE_0";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_1) {
        return "VENDOR_MODE_1";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_2) {
        return "VENDOR_MODE_2";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_3) {
        return "VENDOR_MODE_3";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_4) {
        return "VENDOR_MODE_4";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_5) {
        return "VENDOR_MODE_5";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_6) {
        return "VENDOR_MODE_6";
    }
    if (o == ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_7) {
        return "VENDOR_MODE_7";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::StreamConfigurationMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::Stream& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .streamType = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.streamType);
    os += ", .width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .format = ";
    os += ::android::hardware::graphics::common::V1_0::toString(o.format);
    os += ", .usage = ";
    os += ::android::hardware::graphics::common::V1_0::toString<::android::hardware::graphics::common::V1_0::BufferUsage>(o.usage);
    os += ", .dataSpace = ";
    os += ::android::hardware::graphics::common::V1_0::toString<::android::hardware::graphics::common::V1_0::Dataspace>(o.dataSpace);
    os += ", .rotation = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.rotation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::Stream& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::Stream& lhs, const ::android::hardware::camera::device::V3_2::Stream& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.streamType != rhs.streamType) {
        return false;
    }
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.format != rhs.format) {
        return false;
    }
    if (lhs.usage != rhs.usage) {
        return false;
    }
    if (lhs.dataSpace != rhs.dataSpace) {
        return false;
    }
    if (lhs.rotation != rhs.rotation) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::Stream& lhs, const ::android::hardware::camera::device::V3_2::Stream& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::StreamConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streams = ";
    os += ::android::hardware::toString(o.streams);
    os += ", .operationMode = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.operationMode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::StreamConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::StreamConfiguration& rhs) {
    if (lhs.streams != rhs.streams) {
        return false;
    }
    if (lhs.operationMode != rhs.operationMode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::StreamConfiguration& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::HalStream& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .overrideFormat = ";
    os += ::android::hardware::graphics::common::V1_0::toString(o.overrideFormat);
    os += ", .producerUsage = ";
    os += ::android::hardware::graphics::common::V1_0::toString<::android::hardware::graphics::common::V1_0::BufferUsage>(o.producerUsage);
    os += ", .consumerUsage = ";
    os += ::android::hardware::graphics::common::V1_0::toString<::android::hardware::graphics::common::V1_0::BufferUsage>(o.consumerUsage);
    os += ", .maxBuffers = ";
    os += ::android::hardware::toString(o.maxBuffers);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::HalStream& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::HalStream& lhs, const ::android::hardware::camera::device::V3_2::HalStream& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.overrideFormat != rhs.overrideFormat) {
        return false;
    }
    if (lhs.producerUsage != rhs.producerUsage) {
        return false;
    }
    if (lhs.consumerUsage != rhs.consumerUsage) {
        return false;
    }
    if (lhs.maxBuffers != rhs.maxBuffers) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::HalStream& lhs, const ::android::hardware::camera::device::V3_2::HalStream& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streams = ";
    os += ::android::hardware::toString(o.streams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& rhs) {
    if (lhs.streams != rhs.streams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_2::HalStreamConfiguration& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::BufferStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::BufferStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::BufferStatus::OK) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::BufferStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::BufferStatus::OK;
    }
    if ((o & ::android::hardware::camera::device::V3_2::BufferStatus::ERROR) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::BufferStatus::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::BufferStatus::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::BufferStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::BufferStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::camera::device::V3_2::BufferStatus::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::BufferStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::StreamBuffer& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .bufferId = ";
    os += ::android::hardware::toString(o.bufferId);
    os += ", .buffer = ";
    os += ::android::hardware::toString(o.buffer);
    os += ", .status = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.status);
    os += ", .acquireFence = ";
    os += ::android::hardware::toString(o.acquireFence);
    os += ", .releaseFence = ";
    os += ::android::hardware::toString(o.releaseFence);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::StreamBuffer& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for StreamBuffer

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::CameraBlobId>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::CameraBlobId> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::CameraBlobId::JPEG) == static_cast<uint16_t>(::android::hardware::camera::device::V3_2::CameraBlobId::JPEG)) {
        os += (first ? "" : " | ");
        os += "JPEG";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::CameraBlobId::JPEG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::CameraBlobId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::CameraBlobId::JPEG) {
        return "JPEG";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::CameraBlobId o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::CameraBlob& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".blobId = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.blobId);
    os += ", .blobSize = ";
    os += ::android::hardware::toString(o.blobSize);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::CameraBlob& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::CameraBlob& lhs, const ::android::hardware::camera::device::V3_2::CameraBlob& rhs) {
    if (lhs.blobId != rhs.blobId) {
        return false;
    }
    if (lhs.blobSize != rhs.blobSize) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::CameraBlob& lhs, const ::android::hardware::camera::device::V3_2::CameraBlob& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::MsgType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::MsgType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::MsgType::ERROR) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::MsgType::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::MsgType::ERROR;
    }
    if ((o & ::android::hardware::camera::device::V3_2::MsgType::SHUTTER) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::MsgType::SHUTTER)) {
        os += (first ? "" : " | ");
        os += "SHUTTER";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::MsgType::SHUTTER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::MsgType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::MsgType::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::camera::device::V3_2::MsgType::SHUTTER) {
        return "SHUTTER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::MsgType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::ErrorCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::ErrorCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_DEVICE) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::ErrorCode::ERROR_DEVICE)) {
        os += (first ? "" : " | ");
        os += "ERROR_DEVICE";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_DEVICE;
    }
    if ((o & ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_REQUEST) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::ErrorCode::ERROR_REQUEST)) {
        os += (first ? "" : " | ");
        os += "ERROR_REQUEST";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_REQUEST;
    }
    if ((o & ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_RESULT) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::ErrorCode::ERROR_RESULT)) {
        os += (first ? "" : " | ");
        os += "ERROR_RESULT";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_RESULT;
    }
    if ((o & ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_BUFFER) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::ErrorCode::ERROR_BUFFER)) {
        os += (first ? "" : " | ");
        os += "ERROR_BUFFER";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_BUFFER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::ErrorCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_DEVICE) {
        return "ERROR_DEVICE";
    }
    if (o == ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_REQUEST) {
        return "ERROR_REQUEST";
    }
    if (o == ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_RESULT) {
        return "ERROR_RESULT";
    }
    if (o == ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_BUFFER) {
        return "ERROR_BUFFER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::ErrorCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::ErrorMsg& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameNumber = ";
    os += ::android::hardware::toString(o.frameNumber);
    os += ", .errorStreamId = ";
    os += ::android::hardware::toString(o.errorStreamId);
    os += ", .errorCode = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.errorCode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::ErrorMsg& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::ErrorMsg& lhs, const ::android::hardware::camera::device::V3_2::ErrorMsg& rhs) {
    if (lhs.frameNumber != rhs.frameNumber) {
        return false;
    }
    if (lhs.errorStreamId != rhs.errorStreamId) {
        return false;
    }
    if (lhs.errorCode != rhs.errorCode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::ErrorMsg& lhs, const ::android::hardware::camera::device::V3_2::ErrorMsg& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::ShutterMsg& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameNumber = ";
    os += ::android::hardware::toString(o.frameNumber);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::ShutterMsg& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::ShutterMsg& lhs, const ::android::hardware::camera::device::V3_2::ShutterMsg& rhs) {
    if (lhs.frameNumber != rhs.frameNumber) {
        return false;
    }
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::ShutterMsg& lhs, const ::android::hardware::camera::device::V3_2::ShutterMsg& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::NotifyMsg::Message& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".error = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.error);
    os += ", .shutter = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.shutter);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::NotifyMsg::Message& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Message

static inline std::string toString(const ::android::hardware::camera::device::V3_2::NotifyMsg& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.type);
    os += ", .msg = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.msg);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::NotifyMsg& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for NotifyMsg

template<>
inline std::string toString<::android::hardware::camera::device::V3_2::RequestTemplate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_2::RequestTemplate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::PREVIEW) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::PREVIEW)) {
        os += (first ? "" : " | ");
        os += "PREVIEW";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::PREVIEW;
    }
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::STILL_CAPTURE) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::STILL_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "STILL_CAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::STILL_CAPTURE;
    }
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_RECORD) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_RECORD)) {
        os += (first ? "" : " | ");
        os += "VIDEO_RECORD";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_RECORD;
    }
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_SNAPSHOT) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_SNAPSHOT)) {
        os += (first ? "" : " | ");
        os += "VIDEO_SNAPSHOT";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_SNAPSHOT;
    }
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::ZERO_SHUTTER_LAG) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::ZERO_SHUTTER_LAG)) {
        os += (first ? "" : " | ");
        os += "ZERO_SHUTTER_LAG";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::ZERO_SHUTTER_LAG;
    }
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::MANUAL) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::MANUAL)) {
        os += (first ? "" : " | ");
        os += "MANUAL";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::MANUAL;
    }
    if ((o & ::android::hardware::camera::device::V3_2::RequestTemplate::VENDOR_TEMPLATE_START) == static_cast<uint32_t>(::android::hardware::camera::device::V3_2::RequestTemplate::VENDOR_TEMPLATE_START)) {
        os += (first ? "" : " | ");
        os += "VENDOR_TEMPLATE_START";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_2::RequestTemplate::VENDOR_TEMPLATE_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_2::RequestTemplate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::PREVIEW) {
        return "PREVIEW";
    }
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::STILL_CAPTURE) {
        return "STILL_CAPTURE";
    }
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_RECORD) {
        return "VIDEO_RECORD";
    }
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_SNAPSHOT) {
        return "VIDEO_SNAPSHOT";
    }
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::ZERO_SHUTTER_LAG) {
        return "ZERO_SHUTTER_LAG";
    }
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::MANUAL) {
        return "MANUAL";
    }
    if (o == ::android::hardware::camera::device::V3_2::RequestTemplate::VENDOR_TEMPLATE_START) {
        return "VENDOR_TEMPLATE_START";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_2::RequestTemplate o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_2::CaptureRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameNumber = ";
    os += ::android::hardware::toString(o.frameNumber);
    os += ", .fmqSettingsSize = ";
    os += ::android::hardware::toString(o.fmqSettingsSize);
    os += ", .settings = ";
    os += ::android::hardware::toString(o.settings);
    os += ", .inputBuffer = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.inputBuffer);
    os += ", .outputBuffers = ";
    os += ::android::hardware::toString(o.outputBuffers);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::CaptureRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for CaptureRequest

static inline std::string toString(const ::android::hardware::camera::device::V3_2::CaptureResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameNumber = ";
    os += ::android::hardware::toString(o.frameNumber);
    os += ", .fmqResultSize = ";
    os += ::android::hardware::toString(o.fmqResultSize);
    os += ", .result = ";
    os += ::android::hardware::toString(o.result);
    os += ", .outputBuffers = ";
    os += ::android::hardware::toString(o.outputBuffers);
    os += ", .inputBuffer = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.inputBuffer);
    os += ", .partialResult = ";
    os += ::android::hardware::toString(o.partialResult);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::CaptureResult& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for CaptureResult

static inline std::string toString(const ::android::hardware::camera::device::V3_2::BufferCache& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .bufferId = ";
    os += ::android::hardware::toString(o.bufferId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_2::BufferCache& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_2::BufferCache& lhs, const ::android::hardware::camera::device::V3_2::BufferCache& rhs) {
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.bufferId != rhs.bufferId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_2::BufferCache& lhs, const ::android::hardware::camera::device::V3_2::BufferCache& rhs){
    return !(lhs == rhs);
}


}  // namespace V3_2
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::StreamType, 2> hidl_enum_values<::android::hardware::camera::device::V3_2::StreamType> = {
    ::android::hardware::camera::device::V3_2::StreamType::OUTPUT,
    ::android::hardware::camera::device::V3_2::StreamType::INPUT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::StreamRotation, 4> hidl_enum_values<::android::hardware::camera::device::V3_2::StreamRotation> = {
    ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_0,
    ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_90,
    ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_180,
    ::android::hardware::camera::device::V3_2::StreamRotation::ROTATION_270,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::StreamConfigurationMode, 10> hidl_enum_values<::android::hardware::camera::device::V3_2::StreamConfigurationMode> = {
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::NORMAL_MODE,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_0,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_1,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_2,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_3,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_4,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_5,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_6,
    ::android::hardware::camera::device::V3_2::StreamConfigurationMode::VENDOR_MODE_7,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::BufferStatus, 2> hidl_enum_values<::android::hardware::camera::device::V3_2::BufferStatus> = {
    ::android::hardware::camera::device::V3_2::BufferStatus::OK,
    ::android::hardware::camera::device::V3_2::BufferStatus::ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::CameraBlobId, 1> hidl_enum_values<::android::hardware::camera::device::V3_2::CameraBlobId> = {
    ::android::hardware::camera::device::V3_2::CameraBlobId::JPEG,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::MsgType, 2> hidl_enum_values<::android::hardware::camera::device::V3_2::MsgType> = {
    ::android::hardware::camera::device::V3_2::MsgType::ERROR,
    ::android::hardware::camera::device::V3_2::MsgType::SHUTTER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::ErrorCode, 4> hidl_enum_values<::android::hardware::camera::device::V3_2::ErrorCode> = {
    ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_DEVICE,
    ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_REQUEST,
    ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_RESULT,
    ::android::hardware::camera::device::V3_2::ErrorCode::ERROR_BUFFER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::device::V3_2::RequestTemplate, 7> hidl_enum_values<::android::hardware::camera::device::V3_2::RequestTemplate> = {
    ::android::hardware::camera::device::V3_2::RequestTemplate::PREVIEW,
    ::android::hardware::camera::device::V3_2::RequestTemplate::STILL_CAPTURE,
    ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_RECORD,
    ::android::hardware::camera::device::V3_2::RequestTemplate::VIDEO_SNAPSHOT,
    ::android::hardware::camera::device::V3_2::RequestTemplate::ZERO_SHUTTER_LAG,
    ::android::hardware::camera::device::V3_2::RequestTemplate::MANUAL,
    ::android::hardware::camera::device::V3_2::RequestTemplate::VENDOR_TEMPLATE_START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_2_TYPES_H
