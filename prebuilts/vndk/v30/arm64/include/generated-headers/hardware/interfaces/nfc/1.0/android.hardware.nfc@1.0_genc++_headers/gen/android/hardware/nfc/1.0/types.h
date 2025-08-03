#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class NfcEvent : uint32_t;
enum class NfcStatus : uint32_t;

enum class NfcEvent : uint32_t {
    OPEN_CPLT = 0u,
    CLOSE_CPLT = 1u,
    POST_INIT_CPLT = 2u,
    PRE_DISCOVER_CPLT = 3u,
    REQUEST_CONTROL = 4u,
    RELEASE_CONTROL = 5u,
    ERROR = 6u,
};

enum class NfcStatus : uint32_t {
    OK = 0u,
    FAILED = 1u,
    ERR_TRANSPORT = 2u,
    ERR_CMD_TIMEOUT = 3u,
    REFUSED = 4u,
};

typedef ::android::hardware::hidl_vec<uint8_t> NfcData;

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::nfc::V1_0::NfcEvent o);
static inline void PrintTo(::android::hardware::nfc::V1_0::NfcEvent o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::nfc::V1_0::NfcEvent lhs, const ::android::hardware::nfc::V1_0::NfcEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::nfc::V1_0::NfcEvent rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::nfc::V1_0::NfcEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::nfc::V1_0::NfcEvent lhs, const ::android::hardware::nfc::V1_0::NfcEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::nfc::V1_0::NfcEvent rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::nfc::V1_0::NfcEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::nfc::V1_0::NfcEvent e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::nfc::V1_0::NfcEvent e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::nfc::V1_0::NfcStatus o);
static inline void PrintTo(::android::hardware::nfc::V1_0::NfcStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::nfc::V1_0::NfcStatus lhs, const ::android::hardware::nfc::V1_0::NfcStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::nfc::V1_0::NfcStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::nfc::V1_0::NfcStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::nfc::V1_0::NfcStatus lhs, const ::android::hardware::nfc::V1_0::NfcStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::nfc::V1_0::NfcStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::nfc::V1_0::NfcStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::nfc::V1_0::NfcStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::nfc::V1_0::NfcStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::nfc::V1_0::NfcEvent>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::nfc::V1_0::NfcEvent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::OPEN_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::OPEN_CPLT)) {
        os += (first ? "" : " | ");
        os += "OPEN_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::OPEN_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::CLOSE_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::CLOSE_CPLT)) {
        os += (first ? "" : " | ");
        os += "CLOSE_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::CLOSE_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::POST_INIT_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::POST_INIT_CPLT)) {
        os += (first ? "" : " | ");
        os += "POST_INIT_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::POST_INIT_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::PRE_DISCOVER_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::PRE_DISCOVER_CPLT)) {
        os += (first ? "" : " | ");
        os += "PRE_DISCOVER_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::PRE_DISCOVER_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::REQUEST_CONTROL) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::REQUEST_CONTROL)) {
        os += (first ? "" : " | ");
        os += "REQUEST_CONTROL";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::REQUEST_CONTROL;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::RELEASE_CONTROL) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::RELEASE_CONTROL)) {
        os += (first ? "" : " | ");
        os += "RELEASE_CONTROL";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::RELEASE_CONTROL;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcEvent::ERROR) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcEvent::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcEvent::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::nfc::V1_0::NfcEvent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::OPEN_CPLT) {
        return "OPEN_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::CLOSE_CPLT) {
        return "CLOSE_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::POST_INIT_CPLT) {
        return "POST_INIT_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::PRE_DISCOVER_CPLT) {
        return "PRE_DISCOVER_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::REQUEST_CONTROL) {
        return "REQUEST_CONTROL";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::RELEASE_CONTROL) {
        return "RELEASE_CONTROL";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcEvent::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::nfc::V1_0::NfcEvent o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::nfc::V1_0::NfcStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::nfc::V1_0::NfcStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::nfc::V1_0::NfcStatus::OK) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcStatus::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcStatus::OK;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcStatus::FAILED) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcStatus::FAILED)) {
        os += (first ? "" : " | ");
        os += "FAILED";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcStatus::FAILED;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcStatus::ERR_TRANSPORT) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcStatus::ERR_TRANSPORT)) {
        os += (first ? "" : " | ");
        os += "ERR_TRANSPORT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcStatus::ERR_TRANSPORT;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcStatus::ERR_CMD_TIMEOUT) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcStatus::ERR_CMD_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "ERR_CMD_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcStatus::ERR_CMD_TIMEOUT;
    }
    if ((o & ::android::hardware::nfc::V1_0::NfcStatus::REFUSED) == static_cast<uint32_t>(::android::hardware::nfc::V1_0::NfcStatus::REFUSED)) {
        os += (first ? "" : " | ");
        os += "REFUSED";
        first = false;
        flipped |= ::android::hardware::nfc::V1_0::NfcStatus::REFUSED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::nfc::V1_0::NfcStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::nfc::V1_0::NfcStatus::OK) {
        return "OK";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcStatus::FAILED) {
        return "FAILED";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcStatus::ERR_TRANSPORT) {
        return "ERR_TRANSPORT";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcStatus::ERR_CMD_TIMEOUT) {
        return "ERR_CMD_TIMEOUT";
    }
    if (o == ::android::hardware::nfc::V1_0::NfcStatus::REFUSED) {
        return "REFUSED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::nfc::V1_0::NfcStatus o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace nfc
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
template<> inline constexpr std::array<::android::hardware::nfc::V1_0::NfcEvent, 7> hidl_enum_values<::android::hardware::nfc::V1_0::NfcEvent> = {
    ::android::hardware::nfc::V1_0::NfcEvent::OPEN_CPLT,
    ::android::hardware::nfc::V1_0::NfcEvent::CLOSE_CPLT,
    ::android::hardware::nfc::V1_0::NfcEvent::POST_INIT_CPLT,
    ::android::hardware::nfc::V1_0::NfcEvent::PRE_DISCOVER_CPLT,
    ::android::hardware::nfc::V1_0::NfcEvent::REQUEST_CONTROL,
    ::android::hardware::nfc::V1_0::NfcEvent::RELEASE_CONTROL,
    ::android::hardware::nfc::V1_0::NfcEvent::ERROR,
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
template<> inline constexpr std::array<::android::hardware::nfc::V1_0::NfcStatus, 5> hidl_enum_values<::android::hardware::nfc::V1_0::NfcStatus> = {
    ::android::hardware::nfc::V1_0::NfcStatus::OK,
    ::android::hardware::nfc::V1_0::NfcStatus::FAILED,
    ::android::hardware::nfc::V1_0::NfcStatus::ERR_TRANSPORT,
    ::android::hardware::nfc::V1_0::NfcStatus::ERR_CMD_TIMEOUT,
    ::android::hardware::nfc::V1_0::NfcStatus::REFUSED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_0_TYPES_H
