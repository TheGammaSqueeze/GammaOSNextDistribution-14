#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace usb {
namespace gadget {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class GadgetFunction : uint64_t;
enum class Status : uint32_t;

enum class GadgetFunction : uint64_t {
    /**
     * Removes all the functions and pulls down the gadget.
     */
    NONE = 0ull,
    /**
     * Android Debug Bridge function.
     */
    ADB = 1ull /* 1 << 0 */,
    /**
     * Android open accessory protocol function.
     */
    ACCESSORY = 2ull /* 1 << 1 */,
    /**
     * Media Transfer protocol function.
     */
    MTP = 4ull /* 1 << 2 */,
    /**
     * Peripheral mode USB Midi function.
     */
    MIDI = 8ull /* 1 << 3 */,
    /**
     * Picture transfer protocol function.
     */
    PTP = 16ull /* 1 << 4 */,
    /**
     * Tethering function.
     */
    RNDIS = 32ull /* 1 << 5 */,
    /**
     * AOAv2.0 - Audio Source function.
     */
    AUDIO_SOURCE = 64ull /* 1 << 6 */,
};

enum class Status : uint32_t {
    SUCCESS = 0u,
    /**
     * Error value when the HAL operation fails for reasons not listed here.
     */
    ERROR = 1u,
    /**
     * USB configuration applied successfully.
     */
    FUNCTIONS_APPLIED = 2u,
    /**
     * USB confgiuration failed to apply.
     */
    FUNCTIONS_NOT_APPLIED = 3u,
    /**
     * USB configuration not supported.
     */
    CONFIGURATION_NOT_SUPPORTED = 4u,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::usb::gadget::V1_0::GadgetFunction o);
static inline void PrintTo(::android::hardware::usb::gadget::V1_0::GadgetFunction o, ::std::ostream* os);
constexpr uint64_t operator|(const ::android::hardware::usb::gadget::V1_0::GadgetFunction lhs, const ::android::hardware::usb::gadget::V1_0::GadgetFunction rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs, const ::android::hardware::usb::gadget::V1_0::GadgetFunction rhs) {
    return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::usb::gadget::V1_0::GadgetFunction lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::usb::gadget::V1_0::GadgetFunction lhs, const ::android::hardware::usb::gadget::V1_0::GadgetFunction rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs, const ::android::hardware::usb::gadget::V1_0::GadgetFunction rhs) {
    return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::usb::gadget::V1_0::GadgetFunction lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t &operator|=(uint64_t& v, const ::android::hardware::usb::gadget::V1_0::GadgetFunction e) {
    v |= static_cast<uint64_t>(e);
    return v;
}
constexpr uint64_t &operator&=(uint64_t& v, const ::android::hardware::usb::gadget::V1_0::GadgetFunction e) {
    v &= static_cast<uint64_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::gadget::V1_0::Status o);
static inline void PrintTo(::android::hardware::usb::gadget::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::gadget::V1_0::Status lhs, const ::android::hardware::usb::gadget::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::gadget::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::gadget::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::gadget::V1_0::Status lhs, const ::android::hardware::usb::gadget::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::gadget::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::gadget::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::gadget::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::gadget::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::usb::gadget::V1_0::GadgetFunction>(uint64_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::gadget::V1_0::GadgetFunction> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::NONE) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::NONE;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::ADB) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::ADB)) {
        os += (first ? "" : " | ");
        os += "ADB";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::ADB;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::ACCESSORY) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::ACCESSORY)) {
        os += (first ? "" : " | ");
        os += "ACCESSORY";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::ACCESSORY;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::MTP) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::MTP)) {
        os += (first ? "" : " | ");
        os += "MTP";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::MTP;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::MIDI) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::MIDI)) {
        os += (first ? "" : " | ");
        os += "MIDI";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::MIDI;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::PTP) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::PTP)) {
        os += (first ? "" : " | ");
        os += "PTP";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::PTP;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::RNDIS) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::RNDIS)) {
        os += (first ? "" : " | ");
        os += "RNDIS";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::RNDIS;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::GadgetFunction::AUDIO_SOURCE) == static_cast<uint64_t>(::android::hardware::usb::gadget::V1_0::GadgetFunction::AUDIO_SOURCE)) {
        os += (first ? "" : " | ");
        os += "AUDIO_SOURCE";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::GadgetFunction::AUDIO_SOURCE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::gadget::V1_0::GadgetFunction o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::ADB) {
        return "ADB";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::ACCESSORY) {
        return "ACCESSORY";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::MTP) {
        return "MTP";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::MIDI) {
        return "MIDI";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::PTP) {
        return "PTP";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::RNDIS) {
        return "RNDIS";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::GadgetFunction::AUDIO_SOURCE) {
        return "AUDIO_SOURCE";
    }
    std::string os;
    os += toHexString(static_cast<uint64_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::gadget::V1_0::GadgetFunction o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::usb::gadget::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::gadget::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::gadget::V1_0::Status::SUCCESS) == static_cast<uint32_t>(::android::hardware::usb::gadget::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::Status::ERROR) == static_cast<uint32_t>(::android::hardware::usb::gadget::V1_0::Status::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::Status::ERROR;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_APPLIED) == static_cast<uint32_t>(::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_APPLIED)) {
        os += (first ? "" : " | ");
        os += "FUNCTIONS_APPLIED";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_APPLIED;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_NOT_APPLIED) == static_cast<uint32_t>(::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_NOT_APPLIED)) {
        os += (first ? "" : " | ");
        os += "FUNCTIONS_NOT_APPLIED";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_NOT_APPLIED;
    }
    if ((o & ::android::hardware::usb::gadget::V1_0::Status::CONFIGURATION_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::usb::gadget::V1_0::Status::CONFIGURATION_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "CONFIGURATION_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::usb::gadget::V1_0::Status::CONFIGURATION_NOT_SUPPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::gadget::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::gadget::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::Status::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_APPLIED) {
        return "FUNCTIONS_APPLIED";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_NOT_APPLIED) {
        return "FUNCTIONS_NOT_APPLIED";
    }
    if (o == ::android::hardware::usb::gadget::V1_0::Status::CONFIGURATION_NOT_SUPPORTED) {
        return "CONFIGURATION_NOT_SUPPORTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::gadget::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace gadget
}  // namespace usb
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
template<> inline constexpr std::array<::android::hardware::usb::gadget::V1_0::GadgetFunction, 8> hidl_enum_values<::android::hardware::usb::gadget::V1_0::GadgetFunction> = {
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::NONE,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::ADB,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::ACCESSORY,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::MTP,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::MIDI,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::PTP,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::RNDIS,
    ::android::hardware::usb::gadget::V1_0::GadgetFunction::AUDIO_SOURCE,
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
template<> inline constexpr std::array<::android::hardware::usb::gadget::V1_0::Status, 5> hidl_enum_values<::android::hardware::usb::gadget::V1_0::Status> = {
    ::android::hardware::usb::gadget::V1_0::Status::SUCCESS,
    ::android::hardware::usb::gadget::V1_0::Status::ERROR,
    ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_APPLIED,
    ::android::hardware::usb::gadget::V1_0::Status::FUNCTIONS_NOT_APPLIED,
    ::android::hardware::usb::gadget::V1_0::Status::CONFIGURATION_NOT_SUPPORTED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_TYPES_H
