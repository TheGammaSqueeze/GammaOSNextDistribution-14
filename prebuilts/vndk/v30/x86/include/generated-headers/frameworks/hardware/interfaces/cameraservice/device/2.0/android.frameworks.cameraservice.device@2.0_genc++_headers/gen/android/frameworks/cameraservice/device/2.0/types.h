#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class ErrorCode : int32_t;
enum class StreamConfigurationMode : uint32_t;
enum class TemplateId : int32_t;
struct FmqSizeOrMetadata;
struct PhysicalCameraSettings;
struct StreamAndWindowId;
struct CaptureRequest;
struct CaptureResultExtras;
struct PhysicalCaptureResultInfo;
struct SubmitInfo;
struct OutputConfiguration;
struct SessionConfiguration;

/**
 * Though this is an opaque blob of vec<uint8_t> and cast on both sides of the
 * HAL to camera_metadata_t, camera_metadata_t is stable. Further
 * through some static compile time checks we can be more confident about it not
 * changing across multiple releases.
 * Ref: system/media/camera/src/camera_metadata.c
 */
typedef ::android::hardware::hidl_vec<uint8_t> CameraMetadata;

/**
 * Error codes for onDeviceError
 */
enum class ErrorCode : int32_t {
    /**
     * To indicate all invalid error codes.
     */
    CAMERA_INVALID_ERROR = -1 /* -1 */,
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
     * A camera device encountered an error occured due to which an output
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
     * support high speed output in NORMAL mode.
     */
    CONSTRAINED_HIGH_SPEED_MODE = 1u,
    /**
     * A set of vendor-defined operating modes, for custom default camera
     * application features that can't be implemented in a fully flexible
     * fashion required for NORMAL_MODE.
     */
    VENDOR_MODE_0 = 32768u /* 0x8000 */,
    VENDOR_MODE_1 = 32769u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_0 implicitly + 1 */,
    VENDOR_MODE_2 = 32770u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_1 implicitly + 1 */,
    VENDOR_MODE_3 = 32771u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_2 implicitly + 1 */,
    VENDOR_MODE_4 = 32772u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_3 implicitly + 1 */,
    VENDOR_MODE_5 = 32773u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_4 implicitly + 1 */,
    VENDOR_MODE_6 = 32774u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_5 implicitly + 1 */,
    VENDOR_MODE_7 = 32775u /* ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode.VENDOR_MODE_6 implicitly + 1 */,
};

/**
 * TemplateId to be used with createDefaultRequest
 */
enum class TemplateId : int32_t {
    /**
     * Create a request suitable for a camera preview window. Specifically, this
     * means that high frame rate is given priority over the highest-quality
     * post-processing.
     */
    PREVIEW = 1,
    /**
     * Create a request suitable for still image capture. Specifically, this
     * means prioritizing image quality over frame rate
     */
    STILL_CAPTURE = 2,
    /**
     * Create a request suitable for video recording. Specifically, this means
     * that a stable frame rate is used, and post-processing is set for
     * recording quality.
     */
    RECORD = 3,
    /**
     * Create a request suitable for still image capture while recording
     * video. Specifically, this means maximizing image quality without
     * disrupting the ongoing recording.
     */
    VIDEO_SNAPSHOT = 4,
    /**
     * Create a request suitable for zero shutter lag still capture. This means
     * means maximizing image quality without compromising preview frame rate.
     */
    ZERO_SHUTTER_LAG = 5,
    /**
     * A basic template for direct application control of capture
     * parameters. All automatic control is disabled (auto-exposure, auto-white
     * balance, auto-focus), and post-processing parameters are set to preview
     * quality. The manual capture parameters (exposure, sensitivity, and so on)
     * are set to reasonable defaults, but should be overriden by the
     * application depending on the intended use case.
     */
    MANUAL = 6,
};

/**
 * Either size of the capture request / result metadata sent through FMQ or
 * the request / result metadata itself.
 * If the server of the metadata choses to use FMQ, it must set the
 * fmqMetadataSize field to the size(> 0) of the metadata held by the FMQ.
 * Otherwise, the metadata field must contain the metadata.
 */
struct FmqSizeOrMetadata final {
    enum class hidl_discriminator : uint8_t {
        fmqMetadataSize = 0,  // uint64_t
        metadata = 1,  // ::android::hardware::hidl_vec<uint8_t>
    };

    FmqSizeOrMetadata();
    ~FmqSizeOrMetadata();
    FmqSizeOrMetadata(FmqSizeOrMetadata&&);
    FmqSizeOrMetadata(const FmqSizeOrMetadata&);
    FmqSizeOrMetadata& operator=(FmqSizeOrMetadata&&);
    FmqSizeOrMetadata& operator=(const FmqSizeOrMetadata&);

    void fmqMetadataSize(uint64_t);
    uint64_t& fmqMetadataSize();
    uint64_t fmqMetadataSize() const;

    void metadata(const ::android::hardware::hidl_vec<uint8_t>&);
    void metadata(::android::hardware::hidl_vec<uint8_t>&&);
    ::android::hardware::hidl_vec<uint8_t>& metadata();
    const ::android::hardware::hidl_vec<uint8_t>& metadata() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        uint64_t fmqMetadataSize __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> metadata __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_union) == 16, "wrong size");
    static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata) == 24, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata) == 8, "wrong alignment");

/**
 * Data structure tying camera id and settings metadata.
 *
 */
struct PhysicalCameraSettings final {
    ::android::hardware::hidl_string id __attribute__ ((aligned(8)));
    ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata settings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings, id) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings, settings) == 16, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings) == 40, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings) == 8, "wrong alignment");

/**
 * Data structure tying stream id and window id for a native window.
 */
struct StreamAndWindowId final {
    /**
     * This must be the stream id corresponding to the native window (the streamId
     * returned from the createStream() method, which took in the
     * OutputConfiguration which contained this native window)
     */
    int32_t streamId __attribute__ ((aligned(4)));
    /**
     * This must be the array index of the of the window handle corresponding to
     * the native window, which was packaged with the OutputConfiguration.
     */
    int32_t windowId __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId, windowId) == 4, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId) == 8, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId) == 4, "wrong alignment");

/**
 * CaptureRequest
 * This must contain the information which needs to be submitted with a capture
 * request, typically to be used with submitRequestList.
 */
struct CaptureRequest final {
    /**
     * The physical camera settings associated with this CaptureRequest.
     */
    ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings> physicalCameraSettings __attribute__ ((aligned(8)));
    /**
     * A list of (streamId, windowId) pairs which uniquely identifies the
     * native windows associated with this CaptureRequest.
     */
    ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId> streamAndWindowIds __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureRequest, physicalCameraSettings) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureRequest, streamAndWindowIds) == 16, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::CaptureRequest) == 32, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::CaptureRequest) == 8, "wrong alignment");

/**
 * CaptureResultExtras
 * Information about a capture, available to a device client on various
 * conditions through ICameraDeviceUserCallback callbacks.
 */
struct CaptureResultExtras final {
    /**
     * An integer to index the request sequence that this result belongs to.
     */
    int32_t requestId __attribute__ ((aligned(4)));
    /**
     * An integer to index this result inside a request sequence, starting from 0.
     */
    int32_t burstId __attribute__ ((aligned(4)));
    /**
     * A 64bit integer to index the frame number associated with this result.
     */
    int64_t frameNumber __attribute__ ((aligned(8)));
    /**
     * The partial result count (index) for this capture result.
     */
    int32_t partialResultCount __attribute__ ((aligned(4)));
    /**
     * For buffer drop errors, the stream ID for the stream that lost a buffer.
     * Otherwise -1.
     */
    int32_t errorStreamId __attribute__ ((aligned(4)));
    /**
     * For capture result errors, the physical camera ID in case the respective request contains
     * a reference to physical camera device.
     * Empty otherwise.
     */
    ::android::hardware::hidl_string errorPhysicalCameraId __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras, burstId) == 4, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras, frameNumber) == 8, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras, partialResultCount) == 16, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras, errorStreamId) == 20, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras, errorPhysicalCameraId) == 24, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras) == 40, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras) == 8, "wrong alignment");

/**
 * PhysicalCaptureResultInfo
 * This ties physical camera metadata to a particular camera device for a
 * particular capture result.
 */
struct PhysicalCaptureResultInfo final {
    ::android::hardware::hidl_string physicalCameraId __attribute__ ((aligned(8)));
    ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata physicalCameraMetadata __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo, physicalCameraId) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo, physicalCameraMetadata) == 16, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo) == 40, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo) == 8, "wrong alignment");

/**
 * SubmitInfo
 * The status information returned for a successful capture request submission.
 *
 * This includes the request ID for the newly submitted capture request, and the
 * last frame number of either the previous repeating request (for repeating
 * requests), or of the request(s) just submitted (for single-shot capture).
 */
struct SubmitInfo final {
    int32_t requestId __attribute__ ((aligned(4)));
    int64_t lastFrameNumber __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SubmitInfo, requestId) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SubmitInfo, lastFrameNumber) == 8, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::SubmitInfo) == 16, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::SubmitInfo) == 8, "wrong alignment");

/**
 * OutputConfiguration
 * This describes camera output. It has configurations specific to a
 * capture session.
 */
struct OutputConfiguration final {
    // Forward declaration for forward reference support:
    enum class Rotation : uint32_t;
    enum class WindowGroupId : int32_t;

    /**
     * Rotation values for camera output
     */
    enum class Rotation : uint32_t {
        R0 = 0u,
        R90 = 1u,
        R180 = 2u,
        R270 = 3u,
    };

    enum class WindowGroupId : int32_t {
        NONE = -1 /* -1 */,
    };

    /**
     * These must be handles to ANativeWindows owned by AImageReader,
     * obtained by using AImageReader_getWindowHandle. Ref:
     * (frameworks/av/media/ndk/include/media/NdkImageReader.h).
     * When this vector has more than one window handle, native window surface
     * sharing is enabled. Clients may take advantage of this in advanced use
     * cases when they would require create more streams than the limits the
     * camera device imposes [1]. In this case, more than one window must be
     * attached to an OutputConfiguration so that they map to one camera stream.
     * The outputs will share memory buffers whenever possible. Due to buffer
     * sharing, client should be careful while adding native window outputs that
     * modify their input data. If such cases exist, client must have additional
     * mechanisms to synchronize read and write accesses between consumers.
     * [1]: Ref : frameworks/av/camera/ndk/include/camera/NdkCameraDevice.h
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_handle> windowHandles __attribute__ ((aligned(8)));
    /**
     * The rotation value for the camera output for this configuration.
     * Only Rotation::R0 is guaranteed to be supported.
     */
    ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation rotation __attribute__ ((aligned(4)));
    /**
     * A windowGroupId is used to identify which window group this output window belongs to. A
     * window group is a group of output windows that are not intended to receive camera output
     * buffer streams simultaneously. The ICameraDevice may be able to share the buffers used
     * by all the windows from the same window group, therefore may reduce the overall memory
     * footprint. The client must only set the same set id for the streams that are not
     * simultaneously streaming. For OutputConfigurations not belonging to any
     * window group the client must set windowGroupId to WindowGroupId::NONE.
     */
    int32_t windowGroupId __attribute__ ((aligned(4)));
    /**
     * The id of the physical camera id, that this OutputConfiguration is meant
     * for. If the no physical camera id is expected, this must be an empty
     * string.
     */
    ::android::hardware::hidl_string physicalCameraId __attribute__ ((aligned(8)));
    /**
     * The width of the output stream.
     *
     * Note: this must only be used when using deferred streams. Otherwise, it
     *       must be set to 0.
     */
    uint32_t width __attribute__ ((aligned(4)));
    /**
     * The height of the output stream.
     *
     * Note: this must only be used when using deferred streams. Otherwise, it
     *       must be set to 0.
     */
    uint32_t height __attribute__ ((aligned(4)));
    /**
     * This must be set to true, if this OutputConfiguration contains handles to
     * deferred native windows.
     * Ref:frameworks/base/core/java/android/hardware/camera2/params/OutputConfiguration.java
     */
    bool isDeferred __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, windowHandles) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, rotation) == 16, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, windowGroupId) == 20, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, physicalCameraId) == 24, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, width) == 40, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, height) == 44, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration, isDeferred) == 48, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration) == 56, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration) == 8, "wrong alignment");

struct SessionConfiguration final {
    /**
     * A vector containing all output configurations
     */
    ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration> outputStreams __attribute__ ((aligned(8)));
    /**
     * Input stream width
     *
     * Note: this must be <= 0 if there is no input stream.
     */
    int32_t inputWidth __attribute__ ((aligned(4)));
    /**
     * Input stream height
     *
     * Note: this must be <= 0 if there is no input stream.
     */
    int32_t inputHeight __attribute__ ((aligned(4)));
    /**
     * Input stream format
     *
     * Note: this must be one of the AIMAGE_FORMATS defined in
     * frameworks/av/media/ndk/include/media/NdkImage.h.
     */
    int32_t inputFormat __attribute__ ((aligned(4)));
    /**
     * Operation mode of camera device
     */
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode operationMode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration, outputStreams) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration, inputWidth) == 16, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration, inputHeight) == 20, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration, inputFormat) == 24, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration, operationMode) == 28, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration) == 32, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::device::V2_0::SessionConfiguration) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::ErrorCode o);
static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::ErrorCode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::ErrorCode lhs, const ::android::frameworks::cameraservice::device::V2_0::ErrorCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::ErrorCode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::ErrorCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::ErrorCode lhs, const ::android::frameworks::cameraservice::device::V2_0::ErrorCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::ErrorCode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::ErrorCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::cameraservice::device::V2_0::ErrorCode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::cameraservice::device::V2_0::ErrorCode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode o);
static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::TemplateId o);
static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::TemplateId o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::TemplateId lhs, const ::android::frameworks::cameraservice::device::V2_0::TemplateId rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::TemplateId rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::TemplateId lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::TemplateId lhs, const ::android::frameworks::cameraservice::device::V2_0::TemplateId rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::TemplateId rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::TemplateId lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::cameraservice::device::V2_0::TemplateId e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::cameraservice::device::V2_0::TemplateId e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& lhs, const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& lhs, const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation o);
static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId o);
static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& o, ::std::ostream*);
// operator== and operator!= are not generated for OutputConfiguration

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& o, ::std::ostream*);
// operator== and operator!= are not generated for SessionConfiguration

//
// type header definitions for package
//

template<>
inline std::string toString<::android::frameworks::cameraservice::device::V2_0::ErrorCode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::device::V2_0::ErrorCode> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_INVALID_ERROR) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_INVALID_ERROR)) {
        os += (first ? "" : " | ");
        os += "CAMERA_INVALID_ERROR";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_INVALID_ERROR;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISCONNECTED) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "CAMERA_DISCONNECTED";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISCONNECTED;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DEVICE) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DEVICE)) {
        os += (first ? "" : " | ");
        os += "CAMERA_DEVICE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DEVICE;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_SERVICE) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_SERVICE)) {
        os += (first ? "" : " | ");
        os += "CAMERA_SERVICE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_SERVICE;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_REQUEST) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_REQUEST)) {
        os += (first ? "" : " | ");
        os += "CAMERA_REQUEST";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_REQUEST;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_RESULT) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_RESULT)) {
        os += (first ? "" : " | ");
        os += "CAMERA_RESULT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_RESULT;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_BUFFER) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_BUFFER)) {
        os += (first ? "" : " | ");
        os += "CAMERA_BUFFER";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_BUFFER;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISABLED) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISABLED)) {
        os += (first ? "" : " | ");
        os += "CAMERA_DISABLED";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISABLED;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_UNKNOWN_ERROR) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "CAMERA_UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::ErrorCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_INVALID_ERROR) {
        return "CAMERA_INVALID_ERROR";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISCONNECTED) {
        return "CAMERA_DISCONNECTED";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DEVICE) {
        return "CAMERA_DEVICE";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_SERVICE) {
        return "CAMERA_SERVICE";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_REQUEST) {
        return "CAMERA_REQUEST";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_RESULT) {
        return "CAMERA_RESULT";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_BUFFER) {
        return "CAMERA_BUFFER";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISABLED) {
        return "CAMERA_DISABLED";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_UNKNOWN_ERROR) {
        return "CAMERA_UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::ErrorCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::NORMAL_MODE) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::NORMAL_MODE)) {
        os += (first ? "" : " | ");
        os += "NORMAL_MODE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::NORMAL_MODE;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE)) {
        os += (first ? "" : " | ");
        os += "CONSTRAINED_HIGH_SPEED_MODE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_0) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_0)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_0";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_0;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_1) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_1)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_1";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_1;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_2) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_2)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_2";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_2;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_3) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_3)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_3";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_3;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_4) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_4)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_4";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_4;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_5) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_5)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_5";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_5;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_6) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_6)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_6";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_6;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_7) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_7)) {
        os += (first ? "" : " | ");
        os += "VENDOR_MODE_7";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_7;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::NORMAL_MODE) {
        return "NORMAL_MODE";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE) {
        return "CONSTRAINED_HIGH_SPEED_MODE";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_0) {
        return "VENDOR_MODE_0";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_1) {
        return "VENDOR_MODE_1";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_2) {
        return "VENDOR_MODE_2";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_3) {
        return "VENDOR_MODE_3";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_4) {
        return "VENDOR_MODE_4";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_5) {
        return "VENDOR_MODE_5";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_6) {
        return "VENDOR_MODE_6";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_7) {
        return "VENDOR_MODE_7";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::frameworks::cameraservice::device::V2_0::TemplateId>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::device::V2_0::TemplateId> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::device::V2_0::TemplateId::PREVIEW) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::TemplateId::PREVIEW)) {
        os += (first ? "" : " | ");
        os += "PREVIEW";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::TemplateId::PREVIEW;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::TemplateId::STILL_CAPTURE) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::TemplateId::STILL_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "STILL_CAPTURE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::TemplateId::STILL_CAPTURE;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::TemplateId::RECORD) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::TemplateId::RECORD)) {
        os += (first ? "" : " | ");
        os += "RECORD";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::TemplateId::RECORD;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::TemplateId::VIDEO_SNAPSHOT) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::TemplateId::VIDEO_SNAPSHOT)) {
        os += (first ? "" : " | ");
        os += "VIDEO_SNAPSHOT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::TemplateId::VIDEO_SNAPSHOT;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::TemplateId::ZERO_SHUTTER_LAG) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::TemplateId::ZERO_SHUTTER_LAG)) {
        os += (first ? "" : " | ");
        os += "ZERO_SHUTTER_LAG";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::TemplateId::ZERO_SHUTTER_LAG;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::TemplateId::MANUAL) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::TemplateId::MANUAL)) {
        os += (first ? "" : " | ");
        os += "MANUAL";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::TemplateId::MANUAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::TemplateId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::device::V2_0::TemplateId::PREVIEW) {
        return "PREVIEW";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::TemplateId::STILL_CAPTURE) {
        return "STILL_CAPTURE";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::TemplateId::RECORD) {
        return "RECORD";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::TemplateId::VIDEO_SNAPSHOT) {
        return "VIDEO_SNAPSHOT";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::TemplateId::ZERO_SHUTTER_LAG) {
        return "ZERO_SHUTTER_LAG";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::TemplateId::MANUAL) {
        return "MANUAL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::TemplateId o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_discriminator::fmqMetadataSize: {
            os += ".fmqMetadataSize = ";
            os += toString(o.fmqMetadataSize());
            break;
        }
        case ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_discriminator::metadata: {
            os += ".metadata = ";
            os += toString(o.metadata());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& lhs, const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_discriminator::fmqMetadataSize: {
            return (lhs.fmqMetadataSize() == rhs.fmqMetadataSize());
        }
        case ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata::hidl_discriminator::metadata: {
            return (lhs.metadata() == rhs.metadata());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& lhs, const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .settings = ";
    os += ::android::frameworks::cameraservice::device::V2_0::toString(o.settings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.settings != rhs.settings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCameraSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .windowId = ";
    os += ::android::hardware::toString(o.windowId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& rhs) {
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.windowId != rhs.windowId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& lhs, const ::android::frameworks::cameraservice::device::V2_0::StreamAndWindowId& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".physicalCameraSettings = ";
    os += ::android::hardware::toString(o.physicalCameraSettings);
    os += ", .streamAndWindowIds = ";
    os += ::android::hardware::toString(o.streamAndWindowIds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& rhs) {
    if (lhs.physicalCameraSettings != rhs.physicalCameraSettings) {
        return false;
    }
    if (lhs.streamAndWindowIds != rhs.streamAndWindowIds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .burstId = ";
    os += ::android::hardware::toString(o.burstId);
    os += ", .frameNumber = ";
    os += ::android::hardware::toString(o.frameNumber);
    os += ", .partialResultCount = ";
    os += ::android::hardware::toString(o.partialResultCount);
    os += ", .errorStreamId = ";
    os += ::android::hardware::toString(o.errorStreamId);
    os += ", .errorPhysicalCameraId = ";
    os += ::android::hardware::toString(o.errorPhysicalCameraId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.burstId != rhs.burstId) {
        return false;
    }
    if (lhs.frameNumber != rhs.frameNumber) {
        return false;
    }
    if (lhs.partialResultCount != rhs.partialResultCount) {
        return false;
    }
    if (lhs.errorStreamId != rhs.errorStreamId) {
        return false;
    }
    if (lhs.errorPhysicalCameraId != rhs.errorPhysicalCameraId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& lhs, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".physicalCameraId = ";
    os += ::android::hardware::toString(o.physicalCameraId);
    os += ", .physicalCameraMetadata = ";
    os += ::android::frameworks::cameraservice::device::V2_0::toString(o.physicalCameraMetadata);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& rhs) {
    if (lhs.physicalCameraId != rhs.physicalCameraId) {
        return false;
    }
    if (lhs.physicalCameraMetadata != rhs.physicalCameraMetadata) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".requestId = ";
    os += ::android::hardware::toString(o.requestId);
    os += ", .lastFrameNumber = ";
    os += ::android::hardware::toString(o.lastFrameNumber);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& rhs) {
    if (lhs.requestId != rhs.requestId) {
        return false;
    }
    if (lhs.lastFrameNumber != rhs.lastFrameNumber) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& lhs, const ::android::frameworks::cameraservice::device::V2_0::SubmitInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R0) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R0)) {
        os += (first ? "" : " | ");
        os += "R0";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R0;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R90) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R90)) {
        os += (first ? "" : " | ");
        os += "R90";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R90;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R180) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R180)) {
        os += (first ? "" : " | ");
        os += "R180";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R180;
    }
    if ((o & ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R270) == static_cast<uint32_t>(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R270)) {
        os += (first ? "" : " | ");
        os += "R270";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R270;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R0) {
        return "R0";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R90) {
        return "R90";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R180) {
        return "R180";
    }
    if (o == ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R270) {
        return "R270";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId::NONE) == static_cast<int32_t>(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId::NONE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId::NONE) {
        return "NONE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".windowHandles = ";
    os += ::android::hardware::toString(o.windowHandles);
    os += ", .rotation = ";
    os += ::android::frameworks::cameraservice::device::V2_0::toString(o.rotation);
    os += ", .windowGroupId = ";
    os += ::android::hardware::toString(o.windowGroupId);
    os += ", .physicalCameraId = ";
    os += ::android::hardware::toString(o.physicalCameraId);
    os += ", .width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .isDeferred = ";
    os += ::android::hardware::toString(o.isDeferred);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for OutputConfiguration

static inline std::string toString(const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".outputStreams = ";
    os += ::android::hardware::toString(o.outputStreams);
    os += ", .inputWidth = ";
    os += ::android::hardware::toString(o.inputWidth);
    os += ", .inputHeight = ";
    os += ::android::hardware::toString(o.inputHeight);
    os += ", .inputFormat = ";
    os += ::android::hardware::toString(o.inputFormat);
    os += ", .operationMode = ";
    os += ::android::frameworks::cameraservice::device::V2_0::toString(o.operationMode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for SessionConfiguration


}  // namespace V2_0
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::frameworks::cameraservice::device::V2_0::ErrorCode, 9> hidl_enum_values<::android::frameworks::cameraservice::device::V2_0::ErrorCode> = {
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_INVALID_ERROR,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISCONNECTED,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DEVICE,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_SERVICE,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_REQUEST,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_RESULT,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_BUFFER,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_DISABLED,
    ::android::frameworks::cameraservice::device::V2_0::ErrorCode::CAMERA_UNKNOWN_ERROR,
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode, 10> hidl_enum_values<::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode> = {
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::NORMAL_MODE,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_0,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_1,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_2,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_3,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_4,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_5,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_6,
    ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode::VENDOR_MODE_7,
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::device::V2_0::TemplateId, 6> hidl_enum_values<::android::frameworks::cameraservice::device::V2_0::TemplateId> = {
    ::android::frameworks::cameraservice::device::V2_0::TemplateId::PREVIEW,
    ::android::frameworks::cameraservice::device::V2_0::TemplateId::STILL_CAPTURE,
    ::android::frameworks::cameraservice::device::V2_0::TemplateId::RECORD,
    ::android::frameworks::cameraservice::device::V2_0::TemplateId::VIDEO_SNAPSHOT,
    ::android::frameworks::cameraservice::device::V2_0::TemplateId::ZERO_SHUTTER_LAG,
    ::android::frameworks::cameraservice::device::V2_0::TemplateId::MANUAL,
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation, 4> hidl_enum_values<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation> = {
    ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R0,
    ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R90,
    ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R180,
    ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::Rotation::R270,
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId, 1> hidl_enum_values<::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId> = {
    ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration::WindowGroupId::NONE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_TYPES_H
