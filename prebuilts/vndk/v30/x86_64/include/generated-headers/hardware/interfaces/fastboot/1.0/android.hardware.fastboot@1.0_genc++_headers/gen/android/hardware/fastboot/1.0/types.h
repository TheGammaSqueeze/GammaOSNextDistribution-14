#ifndef HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace fastboot {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class FileSystemType : uint8_t;
struct Result;

enum class Status : uint32_t {
    /**
     * Operation completed without errors.
     */
    SUCCESS = 0u,
    /**
     * Unsupported operation.
     */
    NOT_SUPPORTED = 1u /* ::android::hardware::fastboot::V1_0::Status.SUCCESS implicitly + 1 */,
    /**
     * Bad argument.
     */
    INVALID_ARGUMENT = 2u /* ::android::hardware::fastboot::V1_0::Status.NOT_SUPPORTED implicitly + 1 */,
    /**
     * Operation failed due to unknown reason.
     */
    FAILURE_UNKNOWN = 3u /* ::android::hardware::fastboot::V1_0::Status.INVALID_ARGUMENT implicitly + 1 */,
};

enum class FileSystemType : uint8_t {
    /**
     * Fourth extended file system.
     */
    EXT4 = 0,
    /**
     * Flash Friendly File System.
     */
    F2FS = 1 /* ::android::hardware::fastboot::V1_0::FileSystemType.EXT4 implicitly + 1 */,
    /**
     * Raw file system.
     */
    RAW = 2 /* ::android::hardware::fastboot::V1_0::FileSystemType.F2FS implicitly + 1 */,
};

struct Result final {
    ::android::hardware::fastboot::V1_0::Status status __attribute__ ((aligned(4)));
    /**
     * Message pertaining to the status. It must be a failure message for
     * Status FAILURE_UNKNOWN/NOT_SUPPORTED or an informative message for
     * Status SUCCESS.
     */
    ::android::hardware::hidl_string message __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::fastboot::V1_0::Result, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::fastboot::V1_0::Result, message) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::fastboot::V1_0::Result) == 24, "wrong size");
static_assert(__alignof(::android::hardware::fastboot::V1_0::Result) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::fastboot::V1_0::Status o);
static inline void PrintTo(::android::hardware::fastboot::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::fastboot::V1_0::Status lhs, const ::android::hardware::fastboot::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::fastboot::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::fastboot::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::fastboot::V1_0::Status lhs, const ::android::hardware::fastboot::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::fastboot::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::fastboot::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::fastboot::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::fastboot::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::fastboot::V1_0::FileSystemType o);
static inline void PrintTo(::android::hardware::fastboot::V1_0::FileSystemType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::fastboot::V1_0::FileSystemType lhs, const ::android::hardware::fastboot::V1_0::FileSystemType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::fastboot::V1_0::FileSystemType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::fastboot::V1_0::FileSystemType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::fastboot::V1_0::FileSystemType lhs, const ::android::hardware::fastboot::V1_0::FileSystemType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::fastboot::V1_0::FileSystemType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::fastboot::V1_0::FileSystemType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::fastboot::V1_0::FileSystemType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::fastboot::V1_0::FileSystemType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::fastboot::V1_0::Result& o);
static inline void PrintTo(const ::android::hardware::fastboot::V1_0::Result& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::fastboot::V1_0::Result& lhs, const ::android::hardware::fastboot::V1_0::Result& rhs);
static inline bool operator!=(const ::android::hardware::fastboot::V1_0::Result& lhs, const ::android::hardware::fastboot::V1_0::Result& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::fastboot::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::fastboot::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::fastboot::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::fastboot::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::fastboot::V1_0::Status::NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::fastboot::V1_0::Status::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::Status::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::fastboot::V1_0::Status::INVALID_ARGUMENT) == static_cast<uint32_t>(::android::hardware::fastboot::V1_0::Status::INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::Status::INVALID_ARGUMENT;
    }
    if ((o & ::android::hardware::fastboot::V1_0::Status::FAILURE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::fastboot::V1_0::Status::FAILURE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "FAILURE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::Status::FAILURE_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::fastboot::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::fastboot::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::fastboot::V1_0::Status::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::fastboot::V1_0::Status::INVALID_ARGUMENT) {
        return "INVALID_ARGUMENT";
    }
    if (o == ::android::hardware::fastboot::V1_0::Status::FAILURE_UNKNOWN) {
        return "FAILURE_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::fastboot::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::fastboot::V1_0::FileSystemType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::fastboot::V1_0::FileSystemType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::fastboot::V1_0::FileSystemType::EXT4) == static_cast<uint8_t>(::android::hardware::fastboot::V1_0::FileSystemType::EXT4)) {
        os += (first ? "" : " | ");
        os += "EXT4";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::FileSystemType::EXT4;
    }
    if ((o & ::android::hardware::fastboot::V1_0::FileSystemType::F2FS) == static_cast<uint8_t>(::android::hardware::fastboot::V1_0::FileSystemType::F2FS)) {
        os += (first ? "" : " | ");
        os += "F2FS";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::FileSystemType::F2FS;
    }
    if ((o & ::android::hardware::fastboot::V1_0::FileSystemType::RAW) == static_cast<uint8_t>(::android::hardware::fastboot::V1_0::FileSystemType::RAW)) {
        os += (first ? "" : " | ");
        os += "RAW";
        first = false;
        flipped |= ::android::hardware::fastboot::V1_0::FileSystemType::RAW;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::fastboot::V1_0::FileSystemType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::fastboot::V1_0::FileSystemType::EXT4) {
        return "EXT4";
    }
    if (o == ::android::hardware::fastboot::V1_0::FileSystemType::F2FS) {
        return "F2FS";
    }
    if (o == ::android::hardware::fastboot::V1_0::FileSystemType::RAW) {
        return "RAW";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::fastboot::V1_0::FileSystemType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::fastboot::V1_0::Result& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::fastboot::V1_0::toString(o.status);
    os += ", .message = ";
    os += ::android::hardware::toString(o.message);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::fastboot::V1_0::Result& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::fastboot::V1_0::Result& lhs, const ::android::hardware::fastboot::V1_0::Result& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.message != rhs.message) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::fastboot::V1_0::Result& lhs, const ::android::hardware::fastboot::V1_0::Result& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace fastboot
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
template<> inline constexpr std::array<::android::hardware::fastboot::V1_0::Status, 4> hidl_enum_values<::android::hardware::fastboot::V1_0::Status> = {
    ::android::hardware::fastboot::V1_0::Status::SUCCESS,
    ::android::hardware::fastboot::V1_0::Status::NOT_SUPPORTED,
    ::android::hardware::fastboot::V1_0::Status::INVALID_ARGUMENT,
    ::android::hardware::fastboot::V1_0::Status::FAILURE_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::fastboot::V1_0::FileSystemType, 3> hidl_enum_values<::android::hardware::fastboot::V1_0::FileSystemType> = {
    ::android::hardware::fastboot::V1_0::FileSystemType::EXT4,
    ::android::hardware::fastboot::V1_0::FileSystemType::F2FS,
    ::android::hardware::fastboot::V1_0::FileSystemType::RAW,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_TYPES_H
