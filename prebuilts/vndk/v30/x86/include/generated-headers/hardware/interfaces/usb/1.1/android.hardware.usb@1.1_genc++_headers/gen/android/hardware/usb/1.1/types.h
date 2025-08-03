#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_TYPES_H

#include <android/hardware/usb/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class PortMode_1_1 : uint32_t;
struct PortStatus_1_1;

enum class PortMode_1_1 : uint32_t {
    /**
     * Indicates that the port does not have a mode.
     * In case of DRP, the current mode of the port is only resolved
     * when the type-c handshake happens.
     */
    NONE = 0u,
    /**
     * Indicates that port can only act as device for data and sink for power.
     */
    UFP = 1u,
    /**
     * Indicates the port can only act as host for data and source for power.
     */
    DFP = 2u,
    /**
     * Indicates can either act as UFP or DFP at a given point of time.
     */
    DRP = 3u,
    NUM_MODES = 4u,
    /*
     * Indicates that the port supports Audio Accessory mode.
     */
    AUDIO_ACCESSORY = 4u /* 1 << 2 */,
    /*
     * Indicates that the port supports Debug Accessory mode.
     */
    DEBUG_ACCESSORY = 8u /* 1 << 3 */,
    NUM_MODES_1_1 = 16u /* 1 << 4 */,
};

/*
 * Used as the container to report data back to the caller.
 * Represents the current connection status of a single USB port.
 */
struct PortStatus_1_1 final {
    /*
     * The supportedModes and the currentMode fields of the status
     * object should be set to NONE.
     */
    ::android::hardware::usb::V1_0::PortStatus status __attribute__ ((aligned(8)));
    /*
     * Identifies the modes supported by the port.
     * Refer to PortMode_1_1 for the significance of the individual bits.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_1::PortMode_1_1> supportedModes __attribute__ ((aligned(4)));
    /*
     * Indicates the current mode in which the port is operating.
     */
    ::android::hardware::usb::V1_1::PortMode_1_1 currentMode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::usb::V1_1::PortStatus_1_1, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_1::PortStatus_1_1, supportedModes) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::usb::V1_1::PortStatus_1_1, currentMode) == 44, "wrong offset");
static_assert(sizeof(::android::hardware::usb::V1_1::PortStatus_1_1) == 48, "wrong size");
static_assert(__alignof(::android::hardware::usb::V1_1::PortStatus_1_1) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::usb::V1_1::PortMode_1_1 o);
static inline void PrintTo(::android::hardware::usb::V1_1::PortMode_1_1 o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::usb::V1_1::PortMode_1_1 lhs, const ::android::hardware::usb::V1_1::PortMode_1_1 rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::usb::V1_1::PortMode_1_1 rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::usb::V1_1::PortMode_1_1 lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_1::PortMode_1_1 lhs, const ::android::hardware::usb::V1_1::PortMode_1_1 rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::usb::V1_1::PortMode_1_1 rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::usb::V1_1::PortMode_1_1 lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::usb::V1_1::PortMode_1_1 e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::usb::V1_1::PortMode_1_1 e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::usb::V1_1::PortStatus_1_1& o);
static inline void PrintTo(const ::android::hardware::usb::V1_1::PortStatus_1_1& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::usb::V1_1::PortStatus_1_1& lhs, const ::android::hardware::usb::V1_1::PortStatus_1_1& rhs);
static inline bool operator!=(const ::android::hardware::usb::V1_1::PortStatus_1_1& lhs, const ::android::hardware::usb::V1_1::PortStatus_1_1& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::usb::V1_1::PortMode_1_1>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::usb::V1_1::PortMode_1_1> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::NONE) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::NONE;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::UFP) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::UFP)) {
        os += (first ? "" : " | ");
        os += "UFP";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::UFP;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::DFP) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::DFP)) {
        os += (first ? "" : " | ");
        os += "DFP";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::DFP;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::DRP) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::DRP)) {
        os += (first ? "" : " | ");
        os += "DRP";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::DRP;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES)) {
        os += (first ? "" : " | ");
        os += "NUM_MODES";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::AUDIO_ACCESSORY) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::AUDIO_ACCESSORY)) {
        os += (first ? "" : " | ");
        os += "AUDIO_ACCESSORY";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::AUDIO_ACCESSORY;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::DEBUG_ACCESSORY) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::DEBUG_ACCESSORY)) {
        os += (first ? "" : " | ");
        os += "DEBUG_ACCESSORY";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::DEBUG_ACCESSORY;
    }
    if ((o & ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES_1_1) == static_cast<uint32_t>(::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES_1_1)) {
        os += (first ? "" : " | ");
        os += "NUM_MODES_1_1";
        first = false;
        flipped |= ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES_1_1;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::usb::V1_1::PortMode_1_1 o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::UFP) {
        return "UFP";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::DFP) {
        return "DFP";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::DRP) {
        return "DRP";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES) {
        return "NUM_MODES";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::AUDIO_ACCESSORY) {
        return "AUDIO_ACCESSORY";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::DEBUG_ACCESSORY) {
        return "DEBUG_ACCESSORY";
    }
    if (o == ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES_1_1) {
        return "NUM_MODES_1_1";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::usb::V1_1::PortMode_1_1 o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::usb::V1_1::PortStatus_1_1& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::usb::V1_0::toString(o.status);
    os += ", .supportedModes = ";
    os += ::android::hardware::usb::V1_1::toString<::android::hardware::usb::V1_1::PortMode_1_1>(o.supportedModes);
    os += ", .currentMode = ";
    os += ::android::hardware::usb::V1_1::toString(o.currentMode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::usb::V1_1::PortStatus_1_1& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::usb::V1_1::PortStatus_1_1& lhs, const ::android::hardware::usb::V1_1::PortStatus_1_1& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.supportedModes != rhs.supportedModes) {
        return false;
    }
    if (lhs.currentMode != rhs.currentMode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::usb::V1_1::PortStatus_1_1& lhs, const ::android::hardware::usb::V1_1::PortStatus_1_1& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
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
template<> inline constexpr std::array<::android::hardware::usb::V1_1::PortMode_1_1, 8> hidl_enum_values<::android::hardware::usb::V1_1::PortMode_1_1> = {
    ::android::hardware::usb::V1_1::PortMode_1_1::NONE,
    ::android::hardware::usb::V1_1::PortMode_1_1::UFP,
    ::android::hardware::usb::V1_1::PortMode_1_1::DFP,
    ::android::hardware::usb::V1_1::PortMode_1_1::DRP,
    ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES,
    ::android::hardware::usb::V1_1::PortMode_1_1::AUDIO_ACCESSORY,
    ::android::hardware::usb::V1_1::PortMode_1_1::DEBUG_ACCESSORY,
    ::android::hardware::usb::V1_1::PortMode_1_1::NUM_MODES_1_1,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_TYPES_H
