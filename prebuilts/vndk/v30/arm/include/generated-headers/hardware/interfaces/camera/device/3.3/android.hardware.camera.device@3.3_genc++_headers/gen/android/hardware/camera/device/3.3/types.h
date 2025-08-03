#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_TYPES_H

#include <android/hardware/camera/device/3.2/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_3 {

// Forward declaration for forward reference support:
struct HalStream;
struct HalStreamConfiguration;

/**
 * HalStream:
 *
 * The camera HAL's response to each requested stream configuration.
 *
 * This version extends the @3.2 HalStream with the overrideDataspace
 * field
 */
struct HalStream final {
    /**
     * The definition of HalStream from the prior version.
     */
    ::android::hardware::camera::device::V3_2::HalStream v3_2 __attribute__ ((aligned(8)));
    /**
     * An override dataSpace for the buffers in this stream.
     *
     * The HAL must respect the requested dataSpace in Stream unless it is
     * IMPLEMENTATION_DEFINED, in which case the override dataSpace here must be
     * used by the client instead, for this stream. This allows cross-platform
     * HALs to use a specific dataSpace since IMPLEMENTATION_DEFINED formats often
     * require device-specific information for correct selection. In all other cases, the
     * overrideFormat must match the requested format.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::graphics::common::V1_0::Dataspace> overrideDataSpace __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_3::HalStream, v3_2) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::device::V3_3::HalStream, overrideDataSpace) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_3::HalStream) == 40, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_3::HalStream) == 8, "wrong alignment");

/**
 * HalStreamConfiguration:
 *
 * Identical to @3.2::HalStreamConfiguration, except that it contains @3.3::HalStream entries.
 *
 */
struct HalStreamConfiguration final {
    ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_3::HalStream> streams __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::device::V3_3::HalStreamConfiguration, streams) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::camera::device::V3_3::HalStreamConfiguration) == 16, "wrong size");
static_assert(__alignof(::android::hardware::camera::device::V3_3::HalStreamConfiguration) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::camera::device::V3_3::HalStream& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_3::HalStream& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_3::HalStream& lhs, const ::android::hardware::camera::device::V3_3::HalStream& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_3::HalStream& lhs, const ::android::hardware::camera::device::V3_3::HalStream& rhs);

static inline std::string toString(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& o);
static inline void PrintTo(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& rhs);
static inline bool operator!=(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::camera::device::V3_3::HalStream& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v3_2 = ";
    os += ::android::hardware::camera::device::V3_2::toString(o.v3_2);
    os += ", .overrideDataSpace = ";
    os += ::android::hardware::graphics::common::V1_0::toString<::android::hardware::graphics::common::V1_0::Dataspace>(o.overrideDataSpace);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_3::HalStream& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_3::HalStream& lhs, const ::android::hardware::camera::device::V3_3::HalStream& rhs) {
    if (lhs.v3_2 != rhs.v3_2) {
        return false;
    }
    if (lhs.overrideDataSpace != rhs.overrideDataSpace) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_3::HalStream& lhs, const ::android::hardware::camera::device::V3_3::HalStream& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streams = ";
    os += ::android::hardware::toString(o.streams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& rhs) {
    if (lhs.streams != rhs.streams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& lhs, const ::android::hardware::camera::device::V3_3::HalStreamConfiguration& rhs){
    return !(lhs == rhs);
}


}  // namespace V3_3
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_3_TYPES_H
