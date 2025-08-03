#ifndef HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_STORAGE_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_STORAGE_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace health {
namespace storage {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : uint32_t;

/**
 * Status values for HAL methods.
 */
enum class Result : uint32_t {
    /**
     * Execution of the method is successful.
     */
    SUCCESS = 0u,
    /**
     * An IO error is encountered when the HAL communicates with the device.
     */
    IO_ERROR = 1u /* ::android::hardware::health::storage::V1_0::Result.SUCCESS implicitly + 1 */,
    /**
     * An unknown error is encountered.
     */
    UNKNOWN_ERROR = 2u /* ::android::hardware::health::storage::V1_0::Result.IO_ERROR implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::health::storage::V1_0::Result o);
static inline void PrintTo(::android::hardware::health::storage::V1_0::Result o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::health::storage::V1_0::Result lhs, const ::android::hardware::health::storage::V1_0::Result rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::health::storage::V1_0::Result rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::health::storage::V1_0::Result lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::health::storage::V1_0::Result lhs, const ::android::hardware::health::storage::V1_0::Result rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::health::storage::V1_0::Result rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::health::storage::V1_0::Result lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::health::storage::V1_0::Result e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::health::storage::V1_0::Result e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::health::storage::V1_0::Result>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::health::storage::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::health::storage::V1_0::Result::SUCCESS) == static_cast<uint32_t>(::android::hardware::health::storage::V1_0::Result::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::health::storage::V1_0::Result::SUCCESS;
    }
    if ((o & ::android::hardware::health::storage::V1_0::Result::IO_ERROR) == static_cast<uint32_t>(::android::hardware::health::storage::V1_0::Result::IO_ERROR)) {
        os += (first ? "" : " | ");
        os += "IO_ERROR";
        first = false;
        flipped |= ::android::hardware::health::storage::V1_0::Result::IO_ERROR;
    }
    if ((o & ::android::hardware::health::storage::V1_0::Result::UNKNOWN_ERROR) == static_cast<uint32_t>(::android::hardware::health::storage::V1_0::Result::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::health::storage::V1_0::Result::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::health::storage::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::health::storage::V1_0::Result::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::health::storage::V1_0::Result::IO_ERROR) {
        return "IO_ERROR";
    }
    if (o == ::android::hardware::health::storage::V1_0::Result::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::health::storage::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace storage
}  // namespace health
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
template<> inline constexpr std::array<::android::hardware::health::storage::V1_0::Result, 3> hidl_enum_values<::android::hardware::health::storage::V1_0::Result> = {
    ::android::hardware::health::storage::V1_0::Result::SUCCESS,
    ::android::hardware::health::storage::V1_0::Result::IO_ERROR,
    ::android::hardware::health::storage::V1_0::Result::UNKNOWN_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_STORAGE_V1_0_TYPES_H
