#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace weaver {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class WeaverStatus : uint32_t;
struct WeaverConfig;
enum class WeaverReadStatus : uint32_t;
struct WeaverReadResponse;

enum class WeaverStatus : uint32_t {
    OK = 0u,
    FAILED = 1u /* ::android::hardware::weaver::V1_0::WeaverStatus.OK implicitly + 1 */,
};

struct WeaverConfig final {
    /**
     * The number of slots available.
     */
    uint32_t slots __attribute__ ((aligned(4)));
    /**
     * The number of bytes used for a key.
     */
    uint32_t keySize __attribute__ ((aligned(4)));
    /**
     * The number of bytes used for a value.
     */
    uint32_t valueSize __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::weaver::V1_0::WeaverConfig, slots) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::weaver::V1_0::WeaverConfig, keySize) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::weaver::V1_0::WeaverConfig, valueSize) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::weaver::V1_0::WeaverConfig) == 12, "wrong size");
static_assert(__alignof(::android::hardware::weaver::V1_0::WeaverConfig) == 4, "wrong alignment");

enum class WeaverReadStatus : uint32_t {
    OK = 0u,
    FAILED = 1u /* ::android::hardware::weaver::V1_0::WeaverStatus.OK implicitly + 1 */,
    INCORRECT_KEY = 2u /* ::android::hardware::weaver::V1_0::WeaverStatus.FAILED implicitly + 1 */,
    THROTTLE = 3u /* ::android::hardware::weaver::V1_0::WeaverReadStatus.INCORRECT_KEY implicitly + 1 */,
};

struct WeaverReadResponse final {
    /**
     * The time to wait, in milliseconds, before making the next request.
     */
    uint32_t timeout __attribute__ ((aligned(4)));
    /**
     * The value read from the slot or empty if the value was not read.
     */
    ::android::hardware::hidl_vec<uint8_t> value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::weaver::V1_0::WeaverReadResponse, timeout) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::weaver::V1_0::WeaverReadResponse, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::weaver::V1_0::WeaverReadResponse) == 24, "wrong size");
static_assert(__alignof(::android::hardware::weaver::V1_0::WeaverReadResponse) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::weaver::V1_0::WeaverStatus o);
static inline void PrintTo(::android::hardware::weaver::V1_0::WeaverStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::weaver::V1_0::WeaverStatus lhs, const ::android::hardware::weaver::V1_0::WeaverStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::weaver::V1_0::WeaverStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::weaver::V1_0::WeaverStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::weaver::V1_0::WeaverStatus lhs, const ::android::hardware::weaver::V1_0::WeaverStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::weaver::V1_0::WeaverStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::weaver::V1_0::WeaverStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::weaver::V1_0::WeaverStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::weaver::V1_0::WeaverStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::weaver::V1_0::WeaverConfig& o);
static inline void PrintTo(const ::android::hardware::weaver::V1_0::WeaverConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::weaver::V1_0::WeaverConfig& lhs, const ::android::hardware::weaver::V1_0::WeaverConfig& rhs);
static inline bool operator!=(const ::android::hardware::weaver::V1_0::WeaverConfig& lhs, const ::android::hardware::weaver::V1_0::WeaverConfig& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::weaver::V1_0::WeaverReadStatus o);
static inline void PrintTo(::android::hardware::weaver::V1_0::WeaverReadStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::weaver::V1_0::WeaverReadStatus lhs, const ::android::hardware::weaver::V1_0::WeaverReadStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::weaver::V1_0::WeaverReadStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::weaver::V1_0::WeaverReadStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::weaver::V1_0::WeaverReadStatus lhs, const ::android::hardware::weaver::V1_0::WeaverReadStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::weaver::V1_0::WeaverReadStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::weaver::V1_0::WeaverReadStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::weaver::V1_0::WeaverReadStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::weaver::V1_0::WeaverReadStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::weaver::V1_0::WeaverReadResponse& o);
static inline void PrintTo(const ::android::hardware::weaver::V1_0::WeaverReadResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::weaver::V1_0::WeaverReadResponse& lhs, const ::android::hardware::weaver::V1_0::WeaverReadResponse& rhs);
static inline bool operator!=(const ::android::hardware::weaver::V1_0::WeaverReadResponse& lhs, const ::android::hardware::weaver::V1_0::WeaverReadResponse& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::weaver::V1_0::WeaverStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::weaver::V1_0::WeaverStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::weaver::V1_0::WeaverStatus::OK) == static_cast<uint32_t>(::android::hardware::weaver::V1_0::WeaverStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::weaver::V1_0::WeaverStatus::OK;
    }
    if ((o & ::android::hardware::weaver::V1_0::WeaverStatus::FAILED) == static_cast<uint32_t>(::android::hardware::weaver::V1_0::WeaverStatus::FAILED)) {
        os += (first ? "" : " | ");
        os += "FAILED";
        first = false;
        flipped |= ::android::hardware::weaver::V1_0::WeaverStatus::FAILED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::weaver::V1_0::WeaverStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::weaver::V1_0::WeaverStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::weaver::V1_0::WeaverStatus::FAILED) {
        return "FAILED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::weaver::V1_0::WeaverStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::weaver::V1_0::WeaverConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".slots = ";
    os += ::android::hardware::toString(o.slots);
    os += ", .keySize = ";
    os += ::android::hardware::toString(o.keySize);
    os += ", .valueSize = ";
    os += ::android::hardware::toString(o.valueSize);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::weaver::V1_0::WeaverConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::weaver::V1_0::WeaverConfig& lhs, const ::android::hardware::weaver::V1_0::WeaverConfig& rhs) {
    if (lhs.slots != rhs.slots) {
        return false;
    }
    if (lhs.keySize != rhs.keySize) {
        return false;
    }
    if (lhs.valueSize != rhs.valueSize) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::weaver::V1_0::WeaverConfig& lhs, const ::android::hardware::weaver::V1_0::WeaverConfig& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::weaver::V1_0::WeaverReadStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::weaver::V1_0::WeaverReadStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::weaver::V1_0::WeaverReadStatus::OK) == static_cast<uint32_t>(::android::hardware::weaver::V1_0::WeaverReadStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::weaver::V1_0::WeaverReadStatus::OK;
    }
    if ((o & ::android::hardware::weaver::V1_0::WeaverReadStatus::FAILED) == static_cast<uint32_t>(::android::hardware::weaver::V1_0::WeaverReadStatus::FAILED)) {
        os += (first ? "" : " | ");
        os += "FAILED";
        first = false;
        flipped |= ::android::hardware::weaver::V1_0::WeaverReadStatus::FAILED;
    }
    if ((o & ::android::hardware::weaver::V1_0::WeaverReadStatus::INCORRECT_KEY) == static_cast<uint32_t>(::android::hardware::weaver::V1_0::WeaverReadStatus::INCORRECT_KEY)) {
        os += (first ? "" : " | ");
        os += "INCORRECT_KEY";
        first = false;
        flipped |= ::android::hardware::weaver::V1_0::WeaverReadStatus::INCORRECT_KEY;
    }
    if ((o & ::android::hardware::weaver::V1_0::WeaverReadStatus::THROTTLE) == static_cast<uint32_t>(::android::hardware::weaver::V1_0::WeaverReadStatus::THROTTLE)) {
        os += (first ? "" : " | ");
        os += "THROTTLE";
        first = false;
        flipped |= ::android::hardware::weaver::V1_0::WeaverReadStatus::THROTTLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::weaver::V1_0::WeaverReadStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::weaver::V1_0::WeaverReadStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::weaver::V1_0::WeaverReadStatus::FAILED) {
        return "FAILED";
    }
    if (o == ::android::hardware::weaver::V1_0::WeaverReadStatus::INCORRECT_KEY) {
        return "INCORRECT_KEY";
    }
    if (o == ::android::hardware::weaver::V1_0::WeaverReadStatus::THROTTLE) {
        return "THROTTLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::weaver::V1_0::WeaverReadStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::weaver::V1_0::WeaverReadResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timeout = ";
    os += ::android::hardware::toString(o.timeout);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::weaver::V1_0::WeaverReadResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::weaver::V1_0::WeaverReadResponse& lhs, const ::android::hardware::weaver::V1_0::WeaverReadResponse& rhs) {
    if (lhs.timeout != rhs.timeout) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::weaver::V1_0::WeaverReadResponse& lhs, const ::android::hardware::weaver::V1_0::WeaverReadResponse& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace weaver
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
template<> inline constexpr std::array<::android::hardware::weaver::V1_0::WeaverStatus, 2> hidl_enum_values<::android::hardware::weaver::V1_0::WeaverStatus> = {
    ::android::hardware::weaver::V1_0::WeaverStatus::OK,
    ::android::hardware::weaver::V1_0::WeaverStatus::FAILED,
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
template<> inline constexpr std::array<::android::hardware::weaver::V1_0::WeaverReadStatus, 4> hidl_enum_values<::android::hardware::weaver::V1_0::WeaverReadStatus> = {
    ::android::hardware::weaver::V1_0::WeaverReadStatus::OK,
    ::android::hardware::weaver::V1_0::WeaverReadStatus::FAILED,
    ::android::hardware::weaver::V1_0::WeaverReadStatus::INCORRECT_KEY,
    ::android::hardware::weaver::V1_0::WeaverReadStatus::THROTTLE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_TYPES_H
