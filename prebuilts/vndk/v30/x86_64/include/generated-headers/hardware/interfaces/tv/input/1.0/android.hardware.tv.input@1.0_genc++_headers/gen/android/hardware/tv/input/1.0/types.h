#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_TYPES_H

#include <android/hardware/audio/common/2.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class TvInputType : int32_t;
enum class CableConnectionStatus : int32_t;
struct TvInputDeviceInfo;
enum class TvInputEventType : int32_t;
struct TvInputEvent;
struct TvStreamConfig;

enum class Result : int32_t {
    OK = 0,
    UNKNOWN = 1 /* ::android::hardware::tv::input::V1_0::Result.OK implicitly + 1 */,
    NO_RESOURCE = 2 /* ::android::hardware::tv::input::V1_0::Result.UNKNOWN implicitly + 1 */,
    INVALID_ARGUMENTS = 3 /* ::android::hardware::tv::input::V1_0::Result.NO_RESOURCE implicitly + 1 */,
    INVALID_STATE = 4 /* ::android::hardware::tv::input::V1_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
};

/**
 * Type of physical TV input.
 */
enum class TvInputType : int32_t {
    OTHER = 1,
    TUNER = 2,
    COMPOSITE = 3,
    SVIDEO = 4,
    SCART = 5,
    COMPONENT = 6,
    VGA = 7,
    DVI = 8,
    HDMI = 9,
    DISPLAY_PORT = 10,
};

/**
 * Status of cable connection.
 * This status is for devices having availability to detect the cable in a mechanical way,
 * regardless of whether the connected external device is electrically on or not.
 * If the device does not have such capability, you must use UNKNOWN.
 */
enum class CableConnectionStatus : int32_t {
    UNKNOWN = 0,
    CONNECTED = 1,
    DISCONNECTED = 2,
};

struct TvInputDeviceInfo final {
    int32_t deviceId __attribute__ ((aligned(4)));
    ::android::hardware::tv::input::V1_0::TvInputType type __attribute__ ((aligned(4)));
    uint32_t portId __attribute__ ((aligned(4)));
    ::android::hardware::tv::input::V1_0::CableConnectionStatus cableConnectionStatus __attribute__ ((aligned(4)));
    ::android::hardware::audio::common::V2_0::AudioDevice audioType __attribute__ ((aligned(4)));
    ::android::hardware::hidl_array<uint8_t, 32> audioAddress __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo, deviceId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo, type) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo, portId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo, cableConnectionStatus) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo, audioType) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo, audioAddress) == 20, "wrong offset");
static_assert(sizeof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo) == 52, "wrong size");
static_assert(__alignof(::android::hardware::tv::input::V1_0::TvInputDeviceInfo) == 4, "wrong alignment");

enum class TvInputEventType : int32_t {
    /**
     * Hardware notifies the framework that a device is available.
     *
     * Note that DEVICE_AVAILABLE and DEVICE_UNAVAILABLE events do not represent
     * hotplug events (i.e. plugging cable into or out of the physical port).
     * These events notify the framework whether the port is available or not.
     * For a concrete example, when a user plugs in or pulls out the HDMI cable
     * from a HDMI port, it does not generate DEVICE_AVAILABLE and/or
     * DEVICE_UNAVAILABLE events. However, if a user inserts a pluggable USB
     * tuner into the Android device, it must generate a DEVICE_AVAILABLE event
     * and when the port is removed, it must generate a DEVICE_UNAVAILABLE
     * event.
     *
     * For hotplug events, please see STREAM_CONFIGURATION_CHANGED for more
     * details.
     *
     * HAL implementation must register devices by using this event when the
     * device boots up. The framework must recognize device reported via this
     * event only.
     */
    DEVICE_AVAILABLE = 1,
    /**
     * Hardware notifies the framework that a device is unavailable.
     *
     * HAL implementation must generate this event when a device registered
     * by DEVICE_AVAILABLE is no longer available. For example,
     * the event can indicate that a USB tuner is plugged out from the Android
     * device.
     *
     * Note that this event is not for indicating cable plugged out of the port;
     * for that purpose, the implementation must use
     * STREAM_CONFIGURATION_CHANGED event. This event represents the port itself
     * being no longer available.
     */
    DEVICE_UNAVAILABLE = 2,
    /**
     * Stream configurations are changed. Client must regard all open streams
     * at the specific device are closed, and must call
     * getStreamConfigurations() again, opening some of them if necessary.
     *
     * HAL implementation must generate this event when the available stream
     * configurations change for any reason. A typical use case of this event
     * is to notify the framework that the input signal has changed resolution,
     * or that the cable is plugged out so that the number of available streams
     * is 0.
     *
     * The implementation must use this event to indicate hotplug status of the
     * port. the framework regards input devices with no available streams as
     * disconnected, so the implementation can generate this event with no
     * available streams to indicate that this device is disconnected, and vice
     * versa.
     */
    STREAM_CONFIGURATIONS_CHANGED = 3,
};

struct TvInputEvent final {
    ::android::hardware::tv::input::V1_0::TvInputEventType type __attribute__ ((aligned(4)));
    /**
     * DEVICE_AVAILABLE: all fields are relevant.
     * DEVICE_UNAVAILABLE: only deviceId is relevant.
     * STREAM_CONFIGURATIONS_CHANGED: only deviceId is relevant.
     */
    ::android::hardware::tv::input::V1_0::TvInputDeviceInfo deviceInfo __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputEvent, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvInputEvent, deviceInfo) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::input::V1_0::TvInputEvent) == 56, "wrong size");
static_assert(__alignof(::android::hardware::tv::input::V1_0::TvInputEvent) == 4, "wrong alignment");

struct TvStreamConfig final {
    int32_t streamId __attribute__ ((aligned(4)));
    uint32_t maxVideoWidth __attribute__ ((aligned(4)));
    uint32_t maxVideoHeight __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::input::V1_0::TvStreamConfig, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvStreamConfig, maxVideoWidth) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::input::V1_0::TvStreamConfig, maxVideoHeight) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::input::V1_0::TvStreamConfig) == 12, "wrong size");
static_assert(__alignof(::android::hardware::tv::input::V1_0::TvStreamConfig) == 4, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::input::V1_0::Result o);
static inline void PrintTo(::android::hardware::tv::input::V1_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::Result lhs, const ::android::hardware::tv::input::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::input::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::Result lhs, const ::android::hardware::tv::input::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::input::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::input::V1_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::input::V1_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::input::V1_0::TvInputType o);
static inline void PrintTo(::android::hardware::tv::input::V1_0::TvInputType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::TvInputType lhs, const ::android::hardware::tv::input::V1_0::TvInputType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::input::V1_0::TvInputType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::TvInputType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::TvInputType lhs, const ::android::hardware::tv::input::V1_0::TvInputType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::input::V1_0::TvInputType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::TvInputType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::input::V1_0::TvInputType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::input::V1_0::TvInputType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::input::V1_0::CableConnectionStatus o);
static inline void PrintTo(::android::hardware::tv::input::V1_0::CableConnectionStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::CableConnectionStatus lhs, const ::android::hardware::tv::input::V1_0::CableConnectionStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::input::V1_0::CableConnectionStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::CableConnectionStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::CableConnectionStatus lhs, const ::android::hardware::tv::input::V1_0::CableConnectionStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::input::V1_0::CableConnectionStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::CableConnectionStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::input::V1_0::CableConnectionStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::input::V1_0::CableConnectionStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& o);
static inline void PrintTo(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& lhs, const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& lhs, const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::input::V1_0::TvInputEventType o);
static inline void PrintTo(::android::hardware::tv::input::V1_0::TvInputEventType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::TvInputEventType lhs, const ::android::hardware::tv::input::V1_0::TvInputEventType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::input::V1_0::TvInputEventType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::input::V1_0::TvInputEventType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::TvInputEventType lhs, const ::android::hardware::tv::input::V1_0::TvInputEventType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::input::V1_0::TvInputEventType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::input::V1_0::TvInputEventType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::input::V1_0::TvInputEventType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::input::V1_0::TvInputEventType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::input::V1_0::TvInputEvent& o);
static inline void PrintTo(const ::android::hardware::tv::input::V1_0::TvInputEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::input::V1_0::TvInputEvent& lhs, const ::android::hardware::tv::input::V1_0::TvInputEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::input::V1_0::TvInputEvent& lhs, const ::android::hardware::tv::input::V1_0::TvInputEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::input::V1_0::TvStreamConfig& o);
static inline void PrintTo(const ::android::hardware::tv::input::V1_0::TvStreamConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::input::V1_0::TvStreamConfig& lhs, const ::android::hardware::tv::input::V1_0::TvStreamConfig& rhs);
static inline bool operator!=(const ::android::hardware::tv::input::V1_0::TvStreamConfig& lhs, const ::android::hardware::tv::input::V1_0::TvStreamConfig& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::tv::input::V1_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::input::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::input::V1_0::Result::OK) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::Result::OK;
    }
    if ((o & ::android::hardware::tv::input::V1_0::Result::UNKNOWN) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::Result::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::Result::UNKNOWN;
    }
    if ((o & ::android::hardware::tv::input::V1_0::Result::NO_RESOURCE) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::Result::NO_RESOURCE)) {
        os += (first ? "" : " | ");
        os += "NO_RESOURCE";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::Result::NO_RESOURCE;
    }
    if ((o & ::android::hardware::tv::input::V1_0::Result::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::Result::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::tv::input::V1_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::Result::INVALID_STATE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::input::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::input::V1_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::tv::input::V1_0::Result::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::tv::input::V1_0::Result::NO_RESOURCE) {
        return "NO_RESOURCE";
    }
    if (o == ::android::hardware::tv::input::V1_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::tv::input::V1_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::input::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::input::V1_0::TvInputType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::input::V1_0::TvInputType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::OTHER) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::OTHER)) {
        os += (first ? "" : " | ");
        os += "OTHER";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::OTHER;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::TUNER) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::TUNER)) {
        os += (first ? "" : " | ");
        os += "TUNER";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::TUNER;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::COMPOSITE) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::COMPOSITE)) {
        os += (first ? "" : " | ");
        os += "COMPOSITE";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::COMPOSITE;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::SVIDEO) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::SVIDEO)) {
        os += (first ? "" : " | ");
        os += "SVIDEO";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::SVIDEO;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::SCART) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::SCART)) {
        os += (first ? "" : " | ");
        os += "SCART";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::SCART;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::COMPONENT) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::COMPONENT)) {
        os += (first ? "" : " | ");
        os += "COMPONENT";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::COMPONENT;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::VGA) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::VGA)) {
        os += (first ? "" : " | ");
        os += "VGA";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::VGA;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::DVI) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::DVI)) {
        os += (first ? "" : " | ");
        os += "DVI";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::DVI;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::HDMI) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::HDMI)) {
        os += (first ? "" : " | ");
        os += "HDMI";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::HDMI;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputType::DISPLAY_PORT) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputType::DISPLAY_PORT)) {
        os += (first ? "" : " | ");
        os += "DISPLAY_PORT";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputType::DISPLAY_PORT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::input::V1_0::TvInputType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::OTHER) {
        return "OTHER";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::TUNER) {
        return "TUNER";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::COMPOSITE) {
        return "COMPOSITE";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::SVIDEO) {
        return "SVIDEO";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::SCART) {
        return "SCART";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::COMPONENT) {
        return "COMPONENT";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::VGA) {
        return "VGA";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::DVI) {
        return "DVI";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::HDMI) {
        return "HDMI";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputType::DISPLAY_PORT) {
        return "DISPLAY_PORT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::input::V1_0::TvInputType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::input::V1_0::CableConnectionStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::input::V1_0::CableConnectionStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::input::V1_0::CableConnectionStatus::UNKNOWN) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::CableConnectionStatus::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::CableConnectionStatus::UNKNOWN;
    }
    if ((o & ::android::hardware::tv::input::V1_0::CableConnectionStatus::CONNECTED) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::CableConnectionStatus::CONNECTED)) {
        os += (first ? "" : " | ");
        os += "CONNECTED";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::CableConnectionStatus::CONNECTED;
    }
    if ((o & ::android::hardware::tv::input::V1_0::CableConnectionStatus::DISCONNECTED) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::CableConnectionStatus::DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "DISCONNECTED";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::CableConnectionStatus::DISCONNECTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::input::V1_0::CableConnectionStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::input::V1_0::CableConnectionStatus::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::tv::input::V1_0::CableConnectionStatus::CONNECTED) {
        return "CONNECTED";
    }
    if (o == ::android::hardware::tv::input::V1_0::CableConnectionStatus::DISCONNECTED) {
        return "DISCONNECTED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::input::V1_0::CableConnectionStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".deviceId = ";
    os += ::android::hardware::toString(o.deviceId);
    os += ", .type = ";
    os += ::android::hardware::tv::input::V1_0::toString(o.type);
    os += ", .portId = ";
    os += ::android::hardware::toString(o.portId);
    os += ", .cableConnectionStatus = ";
    os += ::android::hardware::tv::input::V1_0::toString(o.cableConnectionStatus);
    os += ", .audioType = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.audioType);
    os += ", .audioAddress = ";
    os += ::android::hardware::toString(o.audioAddress);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& lhs, const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& rhs) {
    if (lhs.deviceId != rhs.deviceId) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.portId != rhs.portId) {
        return false;
    }
    if (lhs.cableConnectionStatus != rhs.cableConnectionStatus) {
        return false;
    }
    if (lhs.audioType != rhs.audioType) {
        return false;
    }
    if (lhs.audioAddress != rhs.audioAddress) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& lhs, const ::android::hardware::tv::input::V1_0::TvInputDeviceInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::input::V1_0::TvInputEventType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::input::V1_0::TvInputEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_AVAILABLE) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "DEVICE_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_AVAILABLE;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_UNAVAILABLE) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "DEVICE_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_UNAVAILABLE;
    }
    if ((o & ::android::hardware::tv::input::V1_0::TvInputEventType::STREAM_CONFIGURATIONS_CHANGED) == static_cast<int32_t>(::android::hardware::tv::input::V1_0::TvInputEventType::STREAM_CONFIGURATIONS_CHANGED)) {
        os += (first ? "" : " | ");
        os += "STREAM_CONFIGURATIONS_CHANGED";
        first = false;
        flipped |= ::android::hardware::tv::input::V1_0::TvInputEventType::STREAM_CONFIGURATIONS_CHANGED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::input::V1_0::TvInputEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_AVAILABLE) {
        return "DEVICE_AVAILABLE";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_UNAVAILABLE) {
        return "DEVICE_UNAVAILABLE";
    }
    if (o == ::android::hardware::tv::input::V1_0::TvInputEventType::STREAM_CONFIGURATIONS_CHANGED) {
        return "STREAM_CONFIGURATIONS_CHANGED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::input::V1_0::TvInputEventType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::input::V1_0::TvInputEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::tv::input::V1_0::toString(o.type);
    os += ", .deviceInfo = ";
    os += ::android::hardware::tv::input::V1_0::toString(o.deviceInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::input::V1_0::TvInputEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::input::V1_0::TvInputEvent& lhs, const ::android::hardware::tv::input::V1_0::TvInputEvent& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.deviceInfo != rhs.deviceInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::input::V1_0::TvInputEvent& lhs, const ::android::hardware::tv::input::V1_0::TvInputEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::input::V1_0::TvStreamConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .maxVideoWidth = ";
    os += ::android::hardware::toString(o.maxVideoWidth);
    os += ", .maxVideoHeight = ";
    os += ::android::hardware::toString(o.maxVideoHeight);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::input::V1_0::TvStreamConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::input::V1_0::TvStreamConfig& lhs, const ::android::hardware::tv::input::V1_0::TvStreamConfig& rhs) {
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.maxVideoWidth != rhs.maxVideoWidth) {
        return false;
    }
    if (lhs.maxVideoHeight != rhs.maxVideoHeight) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::input::V1_0::TvStreamConfig& lhs, const ::android::hardware::tv::input::V1_0::TvStreamConfig& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace input
}  // namespace tv
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
template<> inline constexpr std::array<::android::hardware::tv::input::V1_0::Result, 5> hidl_enum_values<::android::hardware::tv::input::V1_0::Result> = {
    ::android::hardware::tv::input::V1_0::Result::OK,
    ::android::hardware::tv::input::V1_0::Result::UNKNOWN,
    ::android::hardware::tv::input::V1_0::Result::NO_RESOURCE,
    ::android::hardware::tv::input::V1_0::Result::INVALID_ARGUMENTS,
    ::android::hardware::tv::input::V1_0::Result::INVALID_STATE,
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
template<> inline constexpr std::array<::android::hardware::tv::input::V1_0::TvInputType, 10> hidl_enum_values<::android::hardware::tv::input::V1_0::TvInputType> = {
    ::android::hardware::tv::input::V1_0::TvInputType::OTHER,
    ::android::hardware::tv::input::V1_0::TvInputType::TUNER,
    ::android::hardware::tv::input::V1_0::TvInputType::COMPOSITE,
    ::android::hardware::tv::input::V1_0::TvInputType::SVIDEO,
    ::android::hardware::tv::input::V1_0::TvInputType::SCART,
    ::android::hardware::tv::input::V1_0::TvInputType::COMPONENT,
    ::android::hardware::tv::input::V1_0::TvInputType::VGA,
    ::android::hardware::tv::input::V1_0::TvInputType::DVI,
    ::android::hardware::tv::input::V1_0::TvInputType::HDMI,
    ::android::hardware::tv::input::V1_0::TvInputType::DISPLAY_PORT,
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
template<> inline constexpr std::array<::android::hardware::tv::input::V1_0::CableConnectionStatus, 3> hidl_enum_values<::android::hardware::tv::input::V1_0::CableConnectionStatus> = {
    ::android::hardware::tv::input::V1_0::CableConnectionStatus::UNKNOWN,
    ::android::hardware::tv::input::V1_0::CableConnectionStatus::CONNECTED,
    ::android::hardware::tv::input::V1_0::CableConnectionStatus::DISCONNECTED,
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
template<> inline constexpr std::array<::android::hardware::tv::input::V1_0::TvInputEventType, 3> hidl_enum_values<::android::hardware::tv::input::V1_0::TvInputEventType> = {
    ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_AVAILABLE,
    ::android::hardware::tv::input::V1_0::TvInputEventType::DEVICE_UNAVAILABLE,
    ::android::hardware::tv::input::V1_0::TvInputEventType::STREAM_CONFIGURATIONS_CHANGED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_TYPES_H
