#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class SecureElementStatus : uint8_t;
struct LogicalChannelResponse;

enum class SecureElementStatus : uint8_t {
    SUCCESS = 0,
    FAILED = 1,
    CHANNEL_NOT_AVAILABLE = 2,
    NO_SUCH_ELEMENT_ERROR = 3,
    UNSUPPORTED_OPERATION = 4,
    IOERROR = 5,
};

struct LogicalChannelResponse final {
    /**
     * Channel number to uniquely identify the channel
     */
    uint8_t channelNumber __attribute__ ((aligned(1)));
    /**
     * Response to SELECT command as per ISO/IEC 7816
     */
    ::android::hardware::hidl_vec<uint8_t> selectResponse __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::secure_element::V1_0::LogicalChannelResponse, channelNumber) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::secure_element::V1_0::LogicalChannelResponse, selectResponse) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::secure_element::V1_0::LogicalChannelResponse) == 24, "wrong size");
static_assert(__alignof(::android::hardware::secure_element::V1_0::LogicalChannelResponse) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::secure_element::V1_0::SecureElementStatus o);
static inline void PrintTo(::android::hardware::secure_element::V1_0::SecureElementStatus o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::secure_element::V1_0::SecureElementStatus lhs, const ::android::hardware::secure_element::V1_0::SecureElementStatus rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::secure_element::V1_0::SecureElementStatus rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::secure_element::V1_0::SecureElementStatus lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::secure_element::V1_0::SecureElementStatus lhs, const ::android::hardware::secure_element::V1_0::SecureElementStatus rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::secure_element::V1_0::SecureElementStatus rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::secure_element::V1_0::SecureElementStatus lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::secure_element::V1_0::SecureElementStatus e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::secure_element::V1_0::SecureElementStatus e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& o);
static inline void PrintTo(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& lhs, const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& rhs);
static inline bool operator!=(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& lhs, const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::secure_element::V1_0::SecureElementStatus>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::secure_element::V1_0::SecureElementStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::secure_element::V1_0::SecureElementStatus::SUCCESS) == static_cast<uint8_t>(::android::hardware::secure_element::V1_0::SecureElementStatus::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::secure_element::V1_0::SecureElementStatus::SUCCESS;
    }
    if ((o & ::android::hardware::secure_element::V1_0::SecureElementStatus::FAILED) == static_cast<uint8_t>(::android::hardware::secure_element::V1_0::SecureElementStatus::FAILED)) {
        os += (first ? "" : " | ");
        os += "FAILED";
        first = false;
        flipped |= ::android::hardware::secure_element::V1_0::SecureElementStatus::FAILED;
    }
    if ((o & ::android::hardware::secure_element::V1_0::SecureElementStatus::CHANNEL_NOT_AVAILABLE) == static_cast<uint8_t>(::android::hardware::secure_element::V1_0::SecureElementStatus::CHANNEL_NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "CHANNEL_NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::secure_element::V1_0::SecureElementStatus::CHANNEL_NOT_AVAILABLE;
    }
    if ((o & ::android::hardware::secure_element::V1_0::SecureElementStatus::NO_SUCH_ELEMENT_ERROR) == static_cast<uint8_t>(::android::hardware::secure_element::V1_0::SecureElementStatus::NO_SUCH_ELEMENT_ERROR)) {
        os += (first ? "" : " | ");
        os += "NO_SUCH_ELEMENT_ERROR";
        first = false;
        flipped |= ::android::hardware::secure_element::V1_0::SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    if ((o & ::android::hardware::secure_element::V1_0::SecureElementStatus::UNSUPPORTED_OPERATION) == static_cast<uint8_t>(::android::hardware::secure_element::V1_0::SecureElementStatus::UNSUPPORTED_OPERATION)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_OPERATION";
        first = false;
        flipped |= ::android::hardware::secure_element::V1_0::SecureElementStatus::UNSUPPORTED_OPERATION;
    }
    if ((o & ::android::hardware::secure_element::V1_0::SecureElementStatus::IOERROR) == static_cast<uint8_t>(::android::hardware::secure_element::V1_0::SecureElementStatus::IOERROR)) {
        os += (first ? "" : " | ");
        os += "IOERROR";
        first = false;
        flipped |= ::android::hardware::secure_element::V1_0::SecureElementStatus::IOERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::secure_element::V1_0::SecureElementStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::secure_element::V1_0::SecureElementStatus::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::secure_element::V1_0::SecureElementStatus::FAILED) {
        return "FAILED";
    }
    if (o == ::android::hardware::secure_element::V1_0::SecureElementStatus::CHANNEL_NOT_AVAILABLE) {
        return "CHANNEL_NOT_AVAILABLE";
    }
    if (o == ::android::hardware::secure_element::V1_0::SecureElementStatus::NO_SUCH_ELEMENT_ERROR) {
        return "NO_SUCH_ELEMENT_ERROR";
    }
    if (o == ::android::hardware::secure_element::V1_0::SecureElementStatus::UNSUPPORTED_OPERATION) {
        return "UNSUPPORTED_OPERATION";
    }
    if (o == ::android::hardware::secure_element::V1_0::SecureElementStatus::IOERROR) {
        return "IOERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::secure_element::V1_0::SecureElementStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".channelNumber = ";
    os += ::android::hardware::toString(o.channelNumber);
    os += ", .selectResponse = ";
    os += ::android::hardware::toString(o.selectResponse);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& lhs, const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& rhs) {
    if (lhs.channelNumber != rhs.channelNumber) {
        return false;
    }
    if (lhs.selectResponse != rhs.selectResponse) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& lhs, const ::android::hardware::secure_element::V1_0::LogicalChannelResponse& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace secure_element
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
template<> inline constexpr std::array<::android::hardware::secure_element::V1_0::SecureElementStatus, 6> hidl_enum_values<::android::hardware::secure_element::V1_0::SecureElementStatus> = {
    ::android::hardware::secure_element::V1_0::SecureElementStatus::SUCCESS,
    ::android::hardware::secure_element::V1_0::SecureElementStatus::FAILED,
    ::android::hardware::secure_element::V1_0::SecureElementStatus::CHANNEL_NOT_AVAILABLE,
    ::android::hardware::secure_element::V1_0::SecureElementStatus::NO_SUCH_ELEMENT_ERROR,
    ::android::hardware::secure_element::V1_0::SecureElementStatus::UNSUPPORTED_OPERATION,
    ::android::hardware::secure_element::V1_0::SecureElementStatus::IOERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_TYPES_H
