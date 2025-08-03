#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_TYPES_H

#include <android/hardware/camera/device/3.2/types.h>
#include <android/hardware/camera/device/3.4/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_5 {

// Forward declaration for forward reference support:
struct StreamConfiguration;
enum class StreamBufferRequestError : uint32_t;
struct StreamBuffersVal;
struct StreamBufferRet;
enum class BufferRequestStatus : uint32_t;
struct BufferRequest;
enum class CameraBlobId : uint16_t;
struct CameraBlob;

/**
 * If the result metadata cannot be produced for a physical camera device part of a logical
 * multi-camera, then HAL must invoke the notification callback and pass a message with ERROR_RESULT
 * code and errorStreamId that contains the stream id associated with that physical device. Such
 * callback must be made before the final processCaptureResult() call for the corresponding request.
 * The behavior during absent result metadata remains unchanged for a logical or a non-logical
 * camera device and the errorStreamId must be set to -1.
 *
 *
 * StreamConfiguration:
 *
 * Identical to @3.4::StreamConfiguration, except that it contains streamConfigCounter
 */
struct StreamConfiguration final {
    ::android::hardware::camera::device::V3_4::StreamConfiguration v3_4 __attribute__ ((aligned(8)));
    /**
     * An incrementing counter used for HAL to keep track of the stream
     * configuration and the paired oneway signalStreamFlush call. When the
     * counter in signalStreamFlush call is less than the counter here, that
     * signalStreamFlush call is stale.
     */
    uint32_t streamConfigCounter __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_5::StreamConfiguration, v3_4) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_5::StreamConfiguration, streamConfigCounter) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_5::StreamConfiguration) == 48, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_5::StreamConfiguration) == 8, "wrong alignment");

enum class StreamBufferRequestError : uint32_t {
    /**
     * Get buffer failed due to timeout waiting for an available buffer. This is
     * likely due to the client application holding too many buffers, or the
     * system is under memory pressure.
     * This is not a fatal error. HAL may try to request buffer for this stream
     * later. If HAL cannot get a buffer for certain capture request in time
     * due to this error, HAL can send an ERROR_REQUEST to camera service and
     * drop processing that request.
     */
    NO_BUFFER_AVAILABLE = 1u,
    /**
     * Get buffer failed due to HAL has reached its maxBuffer count. This is not
     * a fatal error. HAL may try to request buffer for this stream again after
     * it returns at least one buffer of that stream to camera service.
     */
    MAX_BUFFER_EXCEEDED = 2u,
    /**
     * Get buffer failed due to the stream is disconnected by client
     * application, has been removed, or not recognized by camera service.
     * This means application is no longer interested in this stream.
     * Requesting buffer for this stream must never succeed after this error is
     * returned. HAL must safely return all buffers of this stream after
     * getting this error. If HAL gets another capture request later targeting
     * a disconnected stream, HAL must send an ERROR_REQUEST to camera service
     * and drop processing that request.
     */
    STREAM_DISCONNECTED = 3u,
    /**
     * Get buffer failed for unknown reasons. This is a fatal error and HAL must
     * send ERROR_DEVICE to camera service and be ready to be closed.
     */
    UNKNOWN_ERROR = 4u,
};

/**
 * Per-stream return value for requestStreamBuffers.
 * For each stream, either an StreamBufferRequestError error code, or all
 * requested buffers for this stream is returned, so buffers.size() must be
 * equal to BufferRequest::numBuffersRequested of corresponding stream.
 */
struct StreamBuffersVal final {
    enum class hidl_discriminator : uint8_t {
        error = 0,  // ::android::hardware::camera::device::V3_5::StreamBufferRequestError
        buffers = 1,  // ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer>
    };

    StreamBuffersVal();
    ~StreamBuffersVal();
    StreamBuffersVal(StreamBuffersVal&&);
    StreamBuffersVal(const StreamBuffersVal&);
    StreamBuffersVal& operator=(StreamBuffersVal&&);
    StreamBuffersVal& operator=(const StreamBuffersVal&);

    void error(::android::hardware::camera::device::V3_5::StreamBufferRequestError);
    ::android::hardware::camera::device::V3_5::StreamBufferRequestError& error();
    ::android::hardware::camera::device::V3_5::StreamBufferRequestError error() const;

    void buffers(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer>&);
    void buffers(::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer>&&);
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer>& buffers();
    const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer>& buffers() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::camera::device::V3_5::StreamBuffersVal, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::camera::device::V3_5::StreamBufferRequestError error __attribute__ ((aligned(4)));
        ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::StreamBuffer> buffers __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::camera::device::V3_5::StreamBuffersVal::hidl_union) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::camera::device::V3_5::StreamBuffersVal::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::camera::device::V3_5::StreamBuffersVal::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::camera::device::V3_5::StreamBuffersVal::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::camera::device::V3_5::StreamBuffersVal) == 24, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_5::StreamBuffersVal) == 8, "wrong alignment");

struct StreamBufferRet final {
    int32_t streamId __attribute__ ((aligned(4)));
    ::android::hardware::camera::device::V3_5::StreamBuffersVal val __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_5::StreamBufferRet, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_5::StreamBufferRet, val) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_5::StreamBufferRet) == 32, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_5::StreamBufferRet) == 8, "wrong alignment");

enum class BufferRequestStatus : uint32_t {
    /**
     * Method call succeeded and all requested buffers are returned.
     */
    OK = 0u,
    /**
     * Method call failed for some streams. Check per stream status for each
     * returned StreamBufferRet.
     */
    FAILED_PARTIAL = 1u,
    /**
     * Method call failed for all streams and no buffers are returned at all.
     * Camera service is about to or is performing configureStreams. HAL must
     * wait until next configureStreams call is finished before requesting
     * buffers again.
     */
    FAILED_CONFIGURING = 2u,
    /**
     * Method call failed for all streams and no buffers are returned at all.
     * Failure due to bad BufferRequest input, eg: unknown streamId or repeated
     * streamId.
     */
    FAILED_ILLEGAL_ARGUMENTS = 3u,
    /**
     * Method call failed for all streams and no buffers are returned at all.
     * Failure due to unknown reason, or all streams has individual failing
     * reason. For the latter case, check per stream status for each returned
     * StreamBufferRet.
     */
    FAILED_UNKNOWN = 4u,
};

struct BufferRequest final {
    int32_t streamId __attribute__ ((aligned(4)));
    uint32_t numBuffersRequested __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_5::BufferRequest, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_5::BufferRequest, numBuffersRequested) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_5::BufferRequest) == 8, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_5::BufferRequest) == 4, "wrong alignment");

/**
 * CameraBlob:
 *
 * Identical to @3.2::CameraBlob, except that it also supports transport of JPEG
 * APP segments blob, which contains JPEG APP1 to APPn (Application Marker)
 * segments as specified in JEITA CP-3451.
 *
 * To capture a JPEG APP segments blob, a stream is created using the pixel format
 * HAL_PIXEL_FORMAT_BLOB and dataspace HAL_DATASPACE_JPEG_APP_SEGMENTS. The buffer
 * size for the stream is calculated by the framework, based on the static
 * metadata field android.heic.maxAppSegmentsCount, and is assigned to both
 * @3.2::Stream::width and @3.4::Stream::bufferSize. Camera framework sets
 * @3.2::Stream::height to 1.
 *
 * Similar to JPEG image, the JPEG APP segment images can be of variable size,
 * so the HAL needs to include the final size of all APP segments using this
 * structure inside the output stream buffer. The camera blob ID field must be
 * set to CameraBlobId::JPEG_APP_SEGMENTS.
 *
 * The transport header must be at the end of the JPEG APP segments output stream
 * buffer. That means the blobId must start at byte[buffer_size -
 * sizeof(CameraBlob)], where the buffer_size is the size of gralloc
 * buffer. The JPEG APP segments data itself starts at the beginning of the
 * buffer and must be blobSize bytes long.
 */
enum class CameraBlobId : uint16_t {
    JPEG = 255 /* 0x00FF */,
    JPEG_APP_SEGMENTS = 256 /* 0x100 */,
};

struct CameraBlob final {
    ::android::hardware::camera::device::V3_5::CameraBlobId blobId __attribute__ ((aligned(2)));
    uint32_t blobSize __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_5::CameraBlob, blobId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_5::CameraBlob, blobSize) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_5::CameraBlob) == 8, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_5::CameraBlob) == 4, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::camera::device::V3_5::StreamConfiguration& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_5::StreamConfiguration& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_5::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_5::StreamConfiguration& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_5::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_5::StreamConfiguration& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_5::StreamBufferRequestError o);
static inline void PrintTo(::android::hardware::camera::device::V3_5::StreamBufferRequestError o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_5::StreamBufferRequestError lhs, const ::android::hardware::camera::device::V3_5::StreamBufferRequestError rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_5::StreamBufferRequestError rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_5::StreamBufferRequestError lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_5::StreamBufferRequestError lhs, const ::android::hardware::camera::device::V3_5::StreamBufferRequestError rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_5::StreamBufferRequestError rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_5::StreamBufferRequestError lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_5::StreamBufferRequestError e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_5::StreamBufferRequestError e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_5::StreamBuffersVal& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_5::StreamBuffersVal& o, ::std::ostream*);
// operator== and operator!= are not generated for StreamBuffersVal

static inline std::string toString(const ::android::hardware::camera::device::V3_5::StreamBufferRet& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_5::StreamBufferRet& o, ::std::ostream*);
// operator== and operator!= are not generated for StreamBufferRet

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::device::V3_5::BufferRequestStatus o);
static inline void PrintTo(::android::hardware::camera::device::V3_5::BufferRequestStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_5::BufferRequestStatus lhs, const ::android::hardware::camera::device::V3_5::BufferRequestStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::device::V3_5::BufferRequestStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::device::V3_5::BufferRequestStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_5::BufferRequestStatus lhs, const ::android::hardware::camera::device::V3_5::BufferRequestStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::device::V3_5::BufferRequestStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::device::V3_5::BufferRequestStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::device::V3_5::BufferRequestStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::device::V3_5::BufferRequestStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_5::BufferRequest& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_5::BufferRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_5::BufferRequest& lhs, const ::android::hardware::camera::device::V3_5::BufferRequest& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_5::BufferRequest& lhs, const ::android::hardware::camera::device::V3_5::BufferRequest& rhs);

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::camera::device::V3_5::CameraBlobId o);
static inline void PrintTo(::android::hardware::camera::device::V3_5::CameraBlobId o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::camera::device::V3_5::CameraBlobId lhs, const ::android::hardware::camera::device::V3_5::CameraBlobId rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::camera::device::V3_5::CameraBlobId rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::camera::device::V3_5::CameraBlobId lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::camera::device::V3_5::CameraBlobId lhs, const ::android::hardware::camera::device::V3_5::CameraBlobId rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::camera::device::V3_5::CameraBlobId rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::camera::device::V3_5::CameraBlobId lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::camera::device::V3_5::CameraBlobId e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::camera::device::V3_5::CameraBlobId e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::device::V3_5::CameraBlob& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_5::CameraBlob& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_5::CameraBlob& lhs, const ::android::hardware::camera::device::V3_5::CameraBlob& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_5::CameraBlob& lhs, const ::android::hardware::camera::device::V3_5::CameraBlob& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::camera::device::V3_5::StreamConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v3_4 = ";
    os += ::android::hardware::camera::device::V3_4::toString(o.v3_4);
    os += ", .streamConfigCounter = ";
    os += ::android::hardware::toString(o.streamConfigCounter);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_5::StreamConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_5::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_5::StreamConfiguration& rhs) {
    if (lhs.v3_4 != rhs.v3_4) {
        return false;
    }
    if (lhs.streamConfigCounter != rhs.streamConfigCounter) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_5::StreamConfiguration& lhs, const ::android::hardware::camera::device::V3_5::StreamConfiguration& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_5::StreamBufferRequestError>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_5::StreamBufferRequestError> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_5::StreamBufferRequestError::NO_BUFFER_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::StreamBufferRequestError::NO_BUFFER_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NO_BUFFER_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::StreamBufferRequestError::NO_BUFFER_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::device::V3_5::StreamBufferRequestError::MAX_BUFFER_EXCEEDED) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::StreamBufferRequestError::MAX_BUFFER_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "MAX_BUFFER_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::StreamBufferRequestError::MAX_BUFFER_EXCEEDED;
    }
    if ((o & ::android::hardware::camera::device::V3_5::StreamBufferRequestError::STREAM_DISCONNECTED) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::StreamBufferRequestError::STREAM_DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "STREAM_DISCONNECTED";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::StreamBufferRequestError::STREAM_DISCONNECTED;
    }
    if ((o & ::android::hardware::camera::device::V3_5::StreamBufferRequestError::UNKNOWN_ERROR) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::StreamBufferRequestError::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::StreamBufferRequestError::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_5::StreamBufferRequestError o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_5::StreamBufferRequestError::NO_BUFFER_AVAILABLE) {
        return "NO_BUFFER_AVAILABLE";
    }
    if (o == ::android::hardware::camera::device::V3_5::StreamBufferRequestError::MAX_BUFFER_EXCEEDED) {
        return "MAX_BUFFER_EXCEEDED";
    }
    if (o == ::android::hardware::camera::device::V3_5::StreamBufferRequestError::STREAM_DISCONNECTED) {
        return "STREAM_DISCONNECTED";
    }
    if (o == ::android::hardware::camera::device::V3_5::StreamBufferRequestError::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_5::StreamBufferRequestError o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_5::StreamBuffersVal& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::camera::device::V3_5::StreamBuffersVal::hidl_discriminator::error: {
            os += ".error = ";
            os += toString(o.error());
            break;
        }
        case ::android::hardware::camera::device::V3_5::StreamBuffersVal::hidl_discriminator::buffers: {
            os += ".buffers = ";
            os += toString(o.buffers());
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

static inline void PrintTo(const ::android::hardware::camera::device::V3_5::StreamBuffersVal& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for StreamBuffersVal

static inline std::string toString(const ::android::hardware::camera::device::V3_5::StreamBufferRet& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .val = ";
    os += ::android::hardware::camera::device::V3_5::toString(o.val);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_5::StreamBufferRet& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for StreamBufferRet

template<>
inline std::string toString<::android::hardware::camera::device::V3_5::BufferRequestStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_5::BufferRequestStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_5::BufferRequestStatus::OK) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::BufferRequestStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::BufferRequestStatus::OK;
    }
    if ((o & ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_PARTIAL) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_PARTIAL)) {
        os += (first ? "" : " | ");
        os += "FAILED_PARTIAL";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_PARTIAL;
    }
    if ((o & ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_CONFIGURING) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_CONFIGURING)) {
        os += (first ? "" : " | ");
        os += "FAILED_CONFIGURING";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_CONFIGURING;
    }
    if ((o & ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "FAILED_ILLEGAL_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS;
    }
    if ((o & ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_UNKNOWN) == static_cast<uint32_t>(::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILED_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_5::BufferRequestStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_5::BufferRequestStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_PARTIAL) {
        return "FAILED_PARTIAL";
    }
    if (o == ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_CONFIGURING) {
        return "FAILED_CONFIGURING";
    }
    if (o == ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS) {
        return "FAILED_ILLEGAL_ARGUMENTS";
    }
    if (o == ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_UNKNOWN) {
        return "FAILED_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_5::BufferRequestStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_5::BufferRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .numBuffersRequested = ";
    os += ::android::hardware::toString(o.numBuffersRequested);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_5::BufferRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_5::BufferRequest& lhs, const ::android::hardware::camera::device::V3_5::BufferRequest& rhs) {
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.numBuffersRequested != rhs.numBuffersRequested) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_5::BufferRequest& lhs, const ::android::hardware::camera::device::V3_5::BufferRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::camera::device::V3_5::CameraBlobId>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V3_5::CameraBlobId> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG) == static_cast<uint16_t>(::android::hardware::camera::device::V3_5::CameraBlobId::JPEG)) {
        os += (first ? "" : " | ");
        os += "JPEG";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG;
    }
    if ((o & ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG_APP_SEGMENTS) == static_cast<uint16_t>(::android::hardware::camera::device::V3_5::CameraBlobId::JPEG_APP_SEGMENTS)) {
        os += (first ? "" : " | ");
        os += "JPEG_APP_SEGMENTS";
        first = false;
        flipped |= ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG_APP_SEGMENTS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::device::V3_5::CameraBlobId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG) {
        return "JPEG";
    }
    if (o == ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG_APP_SEGMENTS) {
        return "JPEG_APP_SEGMENTS";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::device::V3_5::CameraBlobId o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_5::CameraBlob& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".blobId = ";
    os += ::android::hardware::camera::device::V3_5::toString(o.blobId);
    os += ", .blobSize = ";
    os += ::android::hardware::toString(o.blobSize);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_5::CameraBlob& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_5::CameraBlob& lhs, const ::android::hardware::camera::device::V3_5::CameraBlob& rhs) {
    if (lhs.blobId != rhs.blobId) {
        return false;
    }
    if (lhs.blobSize != rhs.blobSize) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_5::CameraBlob& lhs, const ::android::hardware::camera::device::V3_5::CameraBlob& rhs){
    return !(lhs == rhs);
}


}  // namespace V3_5
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
template<> inline constexpr std::array<::android::hardware::camera::device::V3_5::StreamBufferRequestError, 4> hidl_enum_values<::android::hardware::camera::device::V3_5::StreamBufferRequestError> = {
    ::android::hardware::camera::device::V3_5::StreamBufferRequestError::NO_BUFFER_AVAILABLE,
    ::android::hardware::camera::device::V3_5::StreamBufferRequestError::MAX_BUFFER_EXCEEDED,
    ::android::hardware::camera::device::V3_5::StreamBufferRequestError::STREAM_DISCONNECTED,
    ::android::hardware::camera::device::V3_5::StreamBufferRequestError::UNKNOWN_ERROR,
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
template<> inline constexpr std::array<::android::hardware::camera::device::V3_5::BufferRequestStatus, 5> hidl_enum_values<::android::hardware::camera::device::V3_5::BufferRequestStatus> = {
    ::android::hardware::camera::device::V3_5::BufferRequestStatus::OK,
    ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_PARTIAL,
    ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_CONFIGURING,
    ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS,
    ::android::hardware::camera::device::V3_5::BufferRequestStatus::FAILED_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::camera::device::V3_5::CameraBlobId, 2> hidl_enum_values<::android::hardware::camera::device::V3_5::CameraBlobId> = {
    ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG,
    ::android::hardware::camera::device::V3_5::CameraBlobId::JPEG_APP_SEGMENTS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_5_TYPES_H
