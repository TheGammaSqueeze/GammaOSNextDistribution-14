#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_COMMON_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_COMMON_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace common {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class Status : int32_t;
enum class CameraMetadataType : uint32_t;
enum class TagBoundaryId : uint32_t;
struct VendorTag;
struct VendorTagSection;
struct ProviderIdAndVendorTagSections;

/**
 * All camera service and device HIDL calls may return the following
 * status codes
 */
enum class Status : int32_t {
    /**
     * Call succeeded.
     */
    NO_ERROR = 0,
    /**
     * Call failed due to inadequete permissions.
     */
    PERMISSION_DENIED = 1,
    /**
     * Call tried added something that already existed, eg: add a duplicate
     * listener.
     */
    ALREADY_EXISTS = 2,
    /**
     * Call received illegal argument.
     */
    ILLEGAL_ARGUMENT = 3,
    /**
     * The camera device is no longer connected.
     */
    DISCONNECTED = 4,
    /**
     * Request timed out.
     */
    TIMED_OUT = 5,
    /**
     * The device has been disabled by policy.
     */
    DISABLED = 6,
    /**
     * The camera device is currently in use.
     */
    CAMERA_IN_USE = 7,
    /**
     * Too many cameras are connected, more cameras cannot be opened.
     */
    MAX_CAMERAS_IN_USE = 8,
    /**
     * Camera server is using a camera HAL version that does not support
     * android.frameworks.hardware.interfaces.cameraservice.ICameraService@2.0
     * and android.frameworks.hardware.interfaces.cameraservice.device.ICameraDeviceUser@2.0.
     */
    DEPRECATED_HAL = 9,
    /**
     * An invalid operation was attempted by the client. Eg: a waitUntilIdle()
     * call was made, with active repeating requests.
     */
    INVALID_OPERATION = 10,
    /**
     * An unknown error was encountered by the camera subsystem.
     */
    UNKNOWN_ERROR = 11,
};

/**
 * Camera metadata type.
 */
enum class CameraMetadataType : uint32_t {
    /**
     * Unsigned 8-bit integer (uint8_t)
     */
    BYTE = 0u,
    /**
     * Signed 32-bit integer (int32_t)
     */
    INT32 = 1u,
    /**
     * 32-bit float (float)
     */
    FLOAT = 2u,
    /**
     * Signed 64-bit integer (int64_t)
     */
    INT64 = 3u,
    /**
     * 64-bit float (double)
     */
    DOUBLE = 4u,
    /**
     * A 64-bit fraction (camera_metadata_rational_t)
     */
    RATIONAL = 5u,
};

/**
 * Boundaries for VendorTag tagIds.
 */
enum class TagBoundaryId : uint32_t {
    /**
     * First valid tag id for android-defined tags.
     */
    AOSP = 0u /* 0x0 */,
    /**
     * First valid tag id for vendor extension tags.
     */
    VENDOR = 2147483648u /* 0x80000000u */,
};

/**
 * A single vendor-unique metadata tag.
 * The full name of the tag is <sectionName>.<tagName>
 */
struct VendorTag final {
    /**
     * Tag identifier, must be >= TagBoundaryId::VENDOR.
     */
    uint32_t tagId __attribute__ ((aligned(4)));
    /**
     * Name of tag, not including section name.
     */
    ::android::hardware::hidl_string tagName __attribute__ ((aligned(8)));
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType tagType __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::VendorTag, tagId) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::VendorTag, tagName) == 8, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::VendorTag, tagType) == 24, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::common::V2_0::VendorTag) == 32, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::common::V2_0::VendorTag) == 8, "wrong alignment");

/**
 * A set of related vendor tags.
 */
struct VendorTagSection final {
    /**
     * Section name; must be namespaced within vendor's name.
     */
    ::android::hardware::hidl_string sectionName __attribute__ ((aligned(8)));
    /**
     * List of tags in this section
     */
    ::android::hardware::hidl_vec<::android::frameworks::cameraservice::common::V2_0::VendorTag> tags __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::VendorTagSection, sectionName) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::VendorTagSection, tags) == 16, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::common::V2_0::VendorTagSection) == 32, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::common::V2_0::VendorTagSection) == 8, "wrong alignment");

struct ProviderIdAndVendorTagSections final {
    /**
     * provider id
     */
    uint64_t providerId __attribute__ ((aligned(8)));
    /**
     * List of VendorTagSections provided by this provider
     */
    ::android::hardware::hidl_vec<::android::frameworks::cameraservice::common::V2_0::VendorTagSection> vendorTagSections __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections, providerId) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections, vendorTagSections) == 8, "wrong offset");
static_assert(sizeof(::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections) == 24, "wrong size");
static_assert(__alignof(::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::cameraservice::common::V2_0::Status o);
static inline void PrintTo(::android::frameworks::cameraservice::common::V2_0::Status o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::cameraservice::common::V2_0::Status lhs, const ::android::frameworks::cameraservice::common::V2_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::cameraservice::common::V2_0::Status rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::cameraservice::common::V2_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::common::V2_0::Status lhs, const ::android::frameworks::cameraservice::common::V2_0::Status rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::cameraservice::common::V2_0::Status rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::cameraservice::common::V2_0::Status lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::cameraservice::common::V2_0::Status e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::cameraservice::common::V2_0::Status e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType o);
static inline void PrintTo(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType lhs, const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType lhs, const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::frameworks::cameraservice::common::V2_0::TagBoundaryId o);
static inline void PrintTo(::android::frameworks::cameraservice::common::V2_0::TagBoundaryId o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId lhs, const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId lhs, const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTag& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTag& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& rhs);

static inline std::string toString(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& o);
static inline void PrintTo(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& lhs, const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& rhs);
static inline bool operator!=(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& lhs, const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::frameworks::cameraservice::common::V2_0::Status>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::common::V2_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::NO_ERROR) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::NO_ERROR)) {
        os += (first ? "" : " | ");
        os += "NO_ERROR";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::NO_ERROR;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::PERMISSION_DENIED) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::PERMISSION_DENIED)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_DENIED";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::PERMISSION_DENIED;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::ALREADY_EXISTS) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::ALREADY_EXISTS)) {
        os += (first ? "" : " | ");
        os += "ALREADY_EXISTS";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::ALREADY_EXISTS;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::ILLEGAL_ARGUMENT) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::ILLEGAL_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "ILLEGAL_ARGUMENT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::ILLEGAL_ARGUMENT;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::DISCONNECTED) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "DISCONNECTED";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::DISCONNECTED;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::TIMED_OUT) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::TIMED_OUT)) {
        os += (first ? "" : " | ");
        os += "TIMED_OUT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::TIMED_OUT;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::DISABLED) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::DISABLED;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::CAMERA_IN_USE) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::CAMERA_IN_USE)) {
        os += (first ? "" : " | ");
        os += "CAMERA_IN_USE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::CAMERA_IN_USE;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::MAX_CAMERAS_IN_USE) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::MAX_CAMERAS_IN_USE)) {
        os += (first ? "" : " | ");
        os += "MAX_CAMERAS_IN_USE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::MAX_CAMERAS_IN_USE;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::DEPRECATED_HAL) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::DEPRECATED_HAL)) {
        os += (first ? "" : " | ");
        os += "DEPRECATED_HAL";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::DEPRECATED_HAL;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::INVALID_OPERATION) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::INVALID_OPERATION)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERATION";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::INVALID_OPERATION;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::Status::UNKNOWN_ERROR) == static_cast<int32_t>(::android::frameworks::cameraservice::common::V2_0::Status::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::Status::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::common::V2_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::NO_ERROR) {
        return "NO_ERROR";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::PERMISSION_DENIED) {
        return "PERMISSION_DENIED";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::ALREADY_EXISTS) {
        return "ALREADY_EXISTS";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::ILLEGAL_ARGUMENT) {
        return "ILLEGAL_ARGUMENT";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::DISCONNECTED) {
        return "DISCONNECTED";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::TIMED_OUT) {
        return "TIMED_OUT";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::DISABLED) {
        return "DISABLED";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::CAMERA_IN_USE) {
        return "CAMERA_IN_USE";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::MAX_CAMERAS_IN_USE) {
        return "MAX_CAMERAS_IN_USE";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::DEPRECATED_HAL) {
        return "DEPRECATED_HAL";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::INVALID_OPERATION) {
        return "INVALID_OPERATION";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::Status::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::common::V2_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::frameworks::cameraservice::common::V2_0::CameraMetadataType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::common::V2_0::CameraMetadataType> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::BYTE) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::BYTE)) {
        os += (first ? "" : " | ");
        os += "BYTE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::BYTE;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT32) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT32)) {
        os += (first ? "" : " | ");
        os += "INT32";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT32;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::FLOAT) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::FLOAT)) {
        os += (first ? "" : " | ");
        os += "FLOAT";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::FLOAT;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT64) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT64)) {
        os += (first ? "" : " | ");
        os += "INT64";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT64;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::DOUBLE) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::DOUBLE)) {
        os += (first ? "" : " | ");
        os += "DOUBLE";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::DOUBLE;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::RATIONAL) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::RATIONAL)) {
        os += (first ? "" : " | ");
        os += "RATIONAL";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::RATIONAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::BYTE) {
        return "BYTE";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT32) {
        return "INT32";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::FLOAT) {
        return "FLOAT";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT64) {
        return "INT64";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::DOUBLE) {
        return "DOUBLE";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::RATIONAL) {
        return "RATIONAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::common::V2_0::CameraMetadataType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::frameworks::cameraservice::common::V2_0::TagBoundaryId>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::cameraservice::common::V2_0::TagBoundaryId> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::AOSP) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::AOSP)) {
        os += (first ? "" : " | ");
        os += "AOSP";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::AOSP;
    }
    if ((o & ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::VENDOR) == static_cast<uint32_t>(::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::VENDOR)) {
        os += (first ? "" : " | ");
        os += "VENDOR";
        first = false;
        flipped |= ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::VENDOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::cameraservice::common::V2_0::TagBoundaryId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::AOSP) {
        return "AOSP";
    }
    if (o == ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::VENDOR) {
        return "VENDOR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::cameraservice::common::V2_0::TagBoundaryId o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tagId = ";
    os += ::android::hardware::toString(o.tagId);
    os += ", .tagName = ";
    os += ::android::hardware::toString(o.tagName);
    os += ", .tagType = ";
    os += ::android::frameworks::cameraservice::common::V2_0::toString(o.tagType);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTag& rhs) {
    if (lhs.tagId != rhs.tagId) {
        return false;
    }
    if (lhs.tagName != rhs.tagName) {
        return false;
    }
    if (lhs.tagType != rhs.tagType) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::common::V2_0::VendorTag& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTag& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sectionName = ";
    os += ::android::hardware::toString(o.sectionName);
    os += ", .tags = ";
    os += ::android::hardware::toString(o.tags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& rhs) {
    if (lhs.sectionName != rhs.sectionName) {
        return false;
    }
    if (lhs.tags != rhs.tags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& lhs, const ::android::frameworks::cameraservice::common::V2_0::VendorTagSection& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".providerId = ";
    os += ::android::hardware::toString(o.providerId);
    os += ", .vendorTagSections = ";
    os += ::android::hardware::toString(o.vendorTagSections);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& lhs, const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& rhs) {
    if (lhs.providerId != rhs.providerId) {
        return false;
    }
    if (lhs.vendorTagSections != rhs.vendorTagSections) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& lhs, const ::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
}  // namespace common
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::common::V2_0::Status, 12> hidl_enum_values<::android::frameworks::cameraservice::common::V2_0::Status> = {
    ::android::frameworks::cameraservice::common::V2_0::Status::NO_ERROR,
    ::android::frameworks::cameraservice::common::V2_0::Status::PERMISSION_DENIED,
    ::android::frameworks::cameraservice::common::V2_0::Status::ALREADY_EXISTS,
    ::android::frameworks::cameraservice::common::V2_0::Status::ILLEGAL_ARGUMENT,
    ::android::frameworks::cameraservice::common::V2_0::Status::DISCONNECTED,
    ::android::frameworks::cameraservice::common::V2_0::Status::TIMED_OUT,
    ::android::frameworks::cameraservice::common::V2_0::Status::DISABLED,
    ::android::frameworks::cameraservice::common::V2_0::Status::CAMERA_IN_USE,
    ::android::frameworks::cameraservice::common::V2_0::Status::MAX_CAMERAS_IN_USE,
    ::android::frameworks::cameraservice::common::V2_0::Status::DEPRECATED_HAL,
    ::android::frameworks::cameraservice::common::V2_0::Status::INVALID_OPERATION,
    ::android::frameworks::cameraservice::common::V2_0::Status::UNKNOWN_ERROR,
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::common::V2_0::CameraMetadataType, 6> hidl_enum_values<::android::frameworks::cameraservice::common::V2_0::CameraMetadataType> = {
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::BYTE,
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT32,
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::FLOAT,
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::INT64,
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::DOUBLE,
    ::android::frameworks::cameraservice::common::V2_0::CameraMetadataType::RATIONAL,
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
template<> inline constexpr std::array<::android::frameworks::cameraservice::common::V2_0::TagBoundaryId, 2> hidl_enum_values<::android::frameworks::cameraservice::common::V2_0::TagBoundaryId> = {
    ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::AOSP,
    ::android::frameworks::cameraservice::common::V2_0::TagBoundaryId::VENDOR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_COMMON_V2_0_TYPES_H
