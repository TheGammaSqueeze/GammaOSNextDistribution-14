#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class SensorTimeout : int32_t;
enum class EventQueueFlagBits : uint32_t;
enum class WakeLockQueueFlagBits : uint32_t;

enum class SensorTimeout : int32_t {
    /**
     * The maximum number of seconds to wait for a message on the Wake Lock FMQ
     * before automatically releasing any wake_lock held for a WAKE_UP event.
     */
    WAKE_LOCK_SECONDS = 1,
};

enum class EventQueueFlagBits : uint32_t {
    /**
     * Used to notify the Event FMQ that events should be read and processed.
     */
    READ_AND_PROCESS = 1u /* 1 << 0 */,
    /**
     * Used by the framework to signal to the HAL when events have been
     * successfully read from the Event FMQ.
     *
     * If the MessageQueue::writeBlocking function is being used to write sensor
     * events to the Event FMQ, then the readNotification parameter must be set
     * to EVENTS_READ.
     */
    EVENTS_READ = 2u /* 1 << 1 */,
};

enum class WakeLockQueueFlagBits : uint32_t {
    /**
     * Used to notify the HAL that the framework has written data to the Wake
     * Lock FMQ.
     */
    DATA_WRITTEN = 1u /* 1 << 0 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V2_0::SensorTimeout o);
static inline void PrintTo(::android::hardware::sensors::V2_0::SensorTimeout o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V2_0::SensorTimeout lhs, const ::android::hardware::sensors::V2_0::SensorTimeout rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V2_0::SensorTimeout rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V2_0::SensorTimeout lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V2_0::SensorTimeout lhs, const ::android::hardware::sensors::V2_0::SensorTimeout rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V2_0::SensorTimeout rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V2_0::SensorTimeout lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V2_0::SensorTimeout e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V2_0::SensorTimeout e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::sensors::V2_0::EventQueueFlagBits o);
static inline void PrintTo(::android::hardware::sensors::V2_0::EventQueueFlagBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::sensors::V2_0::EventQueueFlagBits lhs, const ::android::hardware::sensors::V2_0::EventQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::sensors::V2_0::EventQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::sensors::V2_0::EventQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V2_0::EventQueueFlagBits lhs, const ::android::hardware::sensors::V2_0::EventQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::sensors::V2_0::EventQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V2_0::EventQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::sensors::V2_0::EventQueueFlagBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::sensors::V2_0::EventQueueFlagBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::sensors::V2_0::WakeLockQueueFlagBits o);
static inline void PrintTo(::android::hardware::sensors::V2_0::WakeLockQueueFlagBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits lhs, const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits lhs, const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::sensors::V2_0::SensorTimeout>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V2_0::SensorTimeout> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V2_0::SensorTimeout::WAKE_LOCK_SECONDS) == static_cast<int32_t>(::android::hardware::sensors::V2_0::SensorTimeout::WAKE_LOCK_SECONDS)) {
        os += (first ? "" : " | ");
        os += "WAKE_LOCK_SECONDS";
        first = false;
        flipped |= ::android::hardware::sensors::V2_0::SensorTimeout::WAKE_LOCK_SECONDS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V2_0::SensorTimeout o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V2_0::SensorTimeout::WAKE_LOCK_SECONDS) {
        return "WAKE_LOCK_SECONDS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V2_0::SensorTimeout o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V2_0::EventQueueFlagBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V2_0::EventQueueFlagBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V2_0::EventQueueFlagBits::READ_AND_PROCESS) == static_cast<uint32_t>(::android::hardware::sensors::V2_0::EventQueueFlagBits::READ_AND_PROCESS)) {
        os += (first ? "" : " | ");
        os += "READ_AND_PROCESS";
        first = false;
        flipped |= ::android::hardware::sensors::V2_0::EventQueueFlagBits::READ_AND_PROCESS;
    }
    if ((o & ::android::hardware::sensors::V2_0::EventQueueFlagBits::EVENTS_READ) == static_cast<uint32_t>(::android::hardware::sensors::V2_0::EventQueueFlagBits::EVENTS_READ)) {
        os += (first ? "" : " | ");
        os += "EVENTS_READ";
        first = false;
        flipped |= ::android::hardware::sensors::V2_0::EventQueueFlagBits::EVENTS_READ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V2_0::EventQueueFlagBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V2_0::EventQueueFlagBits::READ_AND_PROCESS) {
        return "READ_AND_PROCESS";
    }
    if (o == ::android::hardware::sensors::V2_0::EventQueueFlagBits::EVENTS_READ) {
        return "EVENTS_READ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V2_0::EventQueueFlagBits o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V2_0::WakeLockQueueFlagBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V2_0::WakeLockQueueFlagBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits::DATA_WRITTEN) == static_cast<uint32_t>(::android::hardware::sensors::V2_0::WakeLockQueueFlagBits::DATA_WRITTEN)) {
        os += (first ? "" : " | ");
        os += "DATA_WRITTEN";
        first = false;
        flipped |= ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits::DATA_WRITTEN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V2_0::WakeLockQueueFlagBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits::DATA_WRITTEN) {
        return "DATA_WRITTEN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V2_0::WakeLockQueueFlagBits o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_0
}  // namespace sensors
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
template<> inline constexpr std::array<::android::hardware::sensors::V2_0::SensorTimeout, 1> hidl_enum_values<::android::hardware::sensors::V2_0::SensorTimeout> = {
    ::android::hardware::sensors::V2_0::SensorTimeout::WAKE_LOCK_SECONDS,
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
template<> inline constexpr std::array<::android::hardware::sensors::V2_0::EventQueueFlagBits, 2> hidl_enum_values<::android::hardware::sensors::V2_0::EventQueueFlagBits> = {
    ::android::hardware::sensors::V2_0::EventQueueFlagBits::READ_AND_PROCESS,
    ::android::hardware::sensors::V2_0::EventQueueFlagBits::EVENTS_READ,
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
template<> inline constexpr std::array<::android::hardware::sensors::V2_0::WakeLockQueueFlagBits, 1> hidl_enum_values<::android::hardware::sensors::V2_0::WakeLockQueueFlagBits> = {
    ::android::hardware::sensors::V2_0::WakeLockQueueFlagBits::DATA_WRITTEN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_0_TYPES_H
