#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_TYPES_H

#include <android/hardware/camera/device/3.4/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_6 {

// Forward declaration for forward reference support:
struct OfflineRequest;
struct OfflineStream;
struct CameraOfflineSessionInfo;
struct HalStream;
struct HalStreamConfiguration;

/**
 * OfflineRequest:
 *
 * Information about a capture request being switched to offline mode via the
 * ICameraDeviceSession#switchToOffline method.
 *
 */
struct OfflineRequest final {
    /**
     * Must match a inflight CaptureRequest sent by camera service
     */
    uint32_t frameNumber __attribute__ ((aligned(4)));
    /**
     * Stream IDs for outputs that will be returned via ICameraDeviceCallback.
     * The stream ID must be within one of offline stream listed in
     * CameraOfflineSessionInfo.
     * Camera service will validate these pending buffers are matching camera
     * service's record to make sure no buffers are leaked during the
     * switchToOffline call.
     */
    ::android::hardware::hidl_vec<int32_t> pendingStreams __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_6::OfflineRequest, frameNumber) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_6::OfflineRequest, pendingStreams) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_6::OfflineRequest) == 24, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_6::OfflineRequest) == 8, "wrong alignment");

/**
 * OfflineStream:
 *
 * Information about a stream being switched to offline mode via the
 * ICameraDeviceSession#switchToOffline method.
 *
 */
struct OfflineStream final {
    /**
     * IDs of a stream to be transferred to offline session.
     *
     * For devices that do not support HAL buffer management, this must be
     * one of stream ID listed in streamsToKeep argument of the
     * switchToOffline call.
     * For devices that support HAL buffer management, this could be any stream
     * that was configured right before calling switchToOffline.
     */
    int32_t id __attribute__ ((aligned(4)));
    /**
     * Number of outstanding buffers that will be returned via offline session
     */
    uint32_t numOutstandingBuffers __attribute__ ((aligned(4)));
    /**
     * Buffer ID of buffers currently cached between camera service and this
     * stream, which may or may not be owned by the camera HAL right now.
     * See StreamBuffer#bufferId for more details.
     */
    ::android::hardware::hidl_vec<uint64_t> circulatingBufferIds __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_6::OfflineStream, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_6::OfflineStream, numOutstandingBuffers) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_6::OfflineStream, circulatingBufferIds) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_6::OfflineStream) == 24, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_6::OfflineStream) == 8, "wrong alignment");

/**
 * CameraOfflineSessionInfo:
 *
 * Information about pending outputs that's being transferred to an offline
 * session from an active session using the
 * ICameraDeviceSession#switchToOffline method.
 *
 */
struct CameraOfflineSessionInfo final {
    /**
     * Information on what streams will be preserved in offline session.
     * Streams not listed here will be removed by camera service after
     * switchToOffline call returns.
     */
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_6::OfflineStream> offlineStreams __attribute__ ((aligned(8)));
    /**
     * Information for requests that will be handled by offline session
     * Camera service will validate this matches what camera service has on
     * record.
     */
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_6::OfflineRequest> offlineRequests __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo, offlineStreams) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo, offlineRequests) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo) == 8, "wrong alignment");

/**
 * HalStream:
 *
 * The camera HAL's response to each requested stream configuration.
 *
 * This version extends the @3.4 HalStream with the physicalCameraId
 * field
 */
struct HalStream final {
    /**
     * The definition of HalStream from the prior version.
     */
    ::android::hardware::camera::device::V3_4::HalStream v3_4 __attribute__ ((aligned(8)));
    /**
     * Whether this stream can be switch to offline mode.
     *
     * For devices that does not support the OFFLINE_PROCESSING capability, this
     * fields will always be false.
     *
     * For backward compatible camera devices that support the
     * OFFLINE_PROCESSING capability: any input stream and any output stream
     * that can be output of the input stream must set this field to true. Also
     * any stream of YUV420_888 format or JPEG format, with CPU_READ usage flag,
     * must set this field to true.
     *
     * For depth only camera devices that support the OFFLINE_PROCESSING
     * capability: any DEPTH16 output stream must set this field to true.
     *
     * All other streams are up to camera HAL to advertise support or not,
     * though it is not recommended to list support for streams with
     * hardware composer or video encoder usage flags as these streams tend
     * to be targeted continuously and can lead to long latency when trying to
     * switch to offline.
     *
     */
    bool supportOffline __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_6::HalStream, v3_4) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_6::HalStream, supportOffline) == 56, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_6::HalStream) == 64, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_6::HalStream) == 8, "wrong alignment");

/**
 * HalStreamConfiguration:
 *
 * Identical to @3.4::HalStreamConfiguration, except that it contains @3.6::HalStream entries.
 *
 */
struct HalStreamConfiguration final {
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_6::HalStream> streams __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_6::HalStreamConfiguration, streams) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_6::HalStreamConfiguration) == 16, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_6::HalStreamConfiguration) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::camera::device::V3_6::OfflineRequest& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_6::OfflineRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_6::OfflineRequest& lhs, const ::android::hardware::camera::device::V3_6::OfflineRequest& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_6::OfflineRequest& lhs, const ::android::hardware::camera::device::V3_6::OfflineRequest& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_6::OfflineStream& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_6::OfflineStream& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_6::OfflineStream& lhs, const ::android::hardware::camera::device::V3_6::OfflineStream& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_6::OfflineStream& lhs, const ::android::hardware::camera::device::V3_6::OfflineStream& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& lhs, const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& lhs, const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_6::HalStream& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_6::HalStream& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_6::HalStream& lhs, const ::android::hardware::camera::device::V3_6::HalStream& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_6::HalStream& lhs, const ::android::hardware::camera::device::V3_6::HalStream& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::camera::device::V3_6::OfflineRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameNumber = ";
    os += ::android::hardware::toString(o.frameNumber);
    os += ", .pendingStreams = ";
    os += ::android::hardware::toString(o.pendingStreams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_6::OfflineRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_6::OfflineRequest& lhs, const ::android::hardware::camera::device::V3_6::OfflineRequest& rhs) {
    if (lhs.frameNumber != rhs.frameNumber) {
        return false;
    }
    if (lhs.pendingStreams != rhs.pendingStreams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_6::OfflineRequest& lhs, const ::android::hardware::camera::device::V3_6::OfflineRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_6::OfflineStream& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .numOutstandingBuffers = ";
    os += ::android::hardware::toString(o.numOutstandingBuffers);
    os += ", .circulatingBufferIds = ";
    os += ::android::hardware::toString(o.circulatingBufferIds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_6::OfflineStream& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_6::OfflineStream& lhs, const ::android::hardware::camera::device::V3_6::OfflineStream& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.numOutstandingBuffers != rhs.numOutstandingBuffers) {
        return false;
    }
    if (lhs.circulatingBufferIds != rhs.circulatingBufferIds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_6::OfflineStream& lhs, const ::android::hardware::camera::device::V3_6::OfflineStream& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".offlineStreams = ";
    os += ::android::hardware::toString(o.offlineStreams);
    os += ", .offlineRequests = ";
    os += ::android::hardware::toString(o.offlineRequests);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& lhs, const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& rhs) {
    if (lhs.offlineStreams != rhs.offlineStreams) {
        return false;
    }
    if (lhs.offlineRequests != rhs.offlineRequests) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& lhs, const ::android::hardware::camera::device::V3_6::CameraOfflineSessionInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_6::HalStream& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v3_4 = ";
    os += ::android::hardware::camera::device::V3_4::toString(o.v3_4);
    os += ", .supportOffline = ";
    os += ::android::hardware::toString(o.supportOffline);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_6::HalStream& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_6::HalStream& lhs, const ::android::hardware::camera::device::V3_6::HalStream& rhs) {
    if (lhs.v3_4 != rhs.v3_4) {
        return false;
    }
    if (lhs.supportOffline != rhs.supportOffline) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_6::HalStream& lhs, const ::android::hardware::camera::device::V3_6::HalStream& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streams = ";
    os += ::android::hardware::toString(o.streams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& rhs) {
    if (lhs.streams != rhs.streams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_6::HalStreamConfiguration& rhs){
    return !(lhs == rhs);
}


}  // namespace V3_6
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_TYPES_H
