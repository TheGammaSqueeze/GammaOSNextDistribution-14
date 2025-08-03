#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V2_0_TYPES_H

#include <android/hardware/audio/common/2.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class AudioDrain : int32_t;
struct TimeSpec;
struct ParameterValue;
struct DeviceAddress;
struct MmapBufferInfo;
struct MmapPosition;
enum class MessageQueueFlagBits : uint32_t;

enum class Result : int32_t {
    OK = 0,
    NOT_INITIALIZED = 1 /* ::android::hardware::audio::V2_0::Result.OK implicitly + 1 */,
    INVALID_ARGUMENTS = 2 /* ::android::hardware::audio::V2_0::Result.NOT_INITIALIZED implicitly + 1 */,
    INVALID_STATE = 3 /* ::android::hardware::audio::V2_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
    NOT_SUPPORTED = 4 /* ::android::hardware::audio::V2_0::Result.INVALID_STATE implicitly + 1 */,
};

enum class AudioDrain : int32_t {
    /**
     * drain() returns when all data has been played.
     */
    ALL = 0,
    /**
     * drain() returns a short time before all data from the current track has
     * been played to give time for gapless track switch.
     */
    EARLY_NOTIFY = 1 /* ::android::hardware::audio::V2_0::AudioDrain.ALL implicitly + 1 */,
};

/**
 * A substitute for POSIX timespec.
 */
struct TimeSpec final {
    uint64_t tvSec __attribute__ ((aligned(8)));
    uint64_t tvNSec __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V2_0::TimeSpec, tvSec) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::TimeSpec, tvNSec) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V2_0::TimeSpec) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::V2_0::TimeSpec) == 8, "wrong alignment");

/**
 * IEEE 802 MAC address.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> MacAddress;

struct ParameterValue final {
    ::android::hardware::hidl_string key __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V2_0::ParameterValue, key) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::ParameterValue, value) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V2_0::ParameterValue) == 32, "wrong size");
static_assert(__alignof(::android::hardware::audio::V2_0::ParameterValue) == 8, "wrong alignment");

/**
 * Specifies a device in case when several devices of the same type
 * can be connected (e.g. BT A2DP, USB).
 */
struct DeviceAddress final {
    // Forward declaration for forward reference support:
    union Address;

    union Address final {
        // Forward declaration for forward reference support:
        struct Alsa;

        struct Alsa final {
            int32_t card __attribute__ ((aligned(4)));
            int32_t device __attribute__ ((aligned(4)));
        };

        static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa, card) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa, device) == 4, "wrong offset");
        static_assert(sizeof(::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa) == 8, "wrong size");
        static_assert(__alignof(::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa) == 4, "wrong alignment");

        ::android::hardware::hidl_array<uint8_t, 6> mac __attribute__ ((aligned(1)));
        ::android::hardware::hidl_array<uint8_t, 4> ipv4 __attribute__ ((aligned(1)));
        ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa alsa __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress::Address, mac) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress::Address, ipv4) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress::Address, alsa) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::audio::V2_0::DeviceAddress::Address) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::audio::V2_0::DeviceAddress::Address) == 4, "wrong alignment");

    ::android::hardware::audio::common::V2_0::AudioDevice device __attribute__ ((aligned(4)));
    ::android::hardware::audio::V2_0::DeviceAddress::Address address __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string busAddress __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string rSubmixAddress __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress, device) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress, address) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress, busAddress) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::DeviceAddress, rSubmixAddress) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V2_0::DeviceAddress) == 48, "wrong size");
static_assert(__alignof(::android::hardware::audio::V2_0::DeviceAddress) == 8, "wrong alignment");

/**
 * Mmap buffer descriptor returned by IStream.createMmapBuffer().
 * Used by streams opened in mmap mode.
 */
struct MmapBufferInfo final {
    ::android::hardware::hidl_memory sharedMemory __attribute__ ((aligned(8)));
    int32_t bufferSizeFrames __attribute__ ((aligned(4)));
    int32_t burstSizeFrames __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V2_0::MmapBufferInfo, sharedMemory) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::MmapBufferInfo, bufferSizeFrames) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::MmapBufferInfo, burstSizeFrames) == 44, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V2_0::MmapBufferInfo) == 48, "wrong size");
static_assert(__alignof(::android::hardware::audio::V2_0::MmapBufferInfo) == 8, "wrong alignment");

/**
 * Mmap buffer read/write position returned by IStream.getMmapPosition().
 * Used by streams opened in mmap mode.
 */
struct MmapPosition final {
    int64_t timeNanoseconds __attribute__ ((aligned(8)));
    int32_t positionFrames __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::audio::V2_0::MmapPosition, timeNanoseconds) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::audio::V2_0::MmapPosition, positionFrames) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::audio::V2_0::MmapPosition) == 16, "wrong size");
static_assert(__alignof(::android::hardware::audio::V2_0::MmapPosition) == 8, "wrong alignment");

/**
 * The message queue flags used to synchronize reads and writes from
 * message queues used by StreamIn and StreamOut.
 */
enum class MessageQueueFlagBits : uint32_t {
    NOT_EMPTY = 1u /* 1 << 0 */,
    NOT_FULL = 2u /* 1 << 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::V2_0::Result o);
static inline void PrintTo(::android::hardware::audio::V2_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V2_0::Result lhs, const ::android::hardware::audio::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V2_0::Result lhs, const ::android::hardware::audio::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V2_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V2_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::V2_0::AudioDrain o);
static inline void PrintTo(::android::hardware::audio::V2_0::AudioDrain o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V2_0::AudioDrain lhs, const ::android::hardware::audio::V2_0::AudioDrain rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V2_0::AudioDrain rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V2_0::AudioDrain lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V2_0::AudioDrain lhs, const ::android::hardware::audio::V2_0::AudioDrain rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V2_0::AudioDrain rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V2_0::AudioDrain lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V2_0::AudioDrain e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V2_0::AudioDrain e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::V2_0::TimeSpec& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::TimeSpec& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V2_0::TimeSpec& lhs, const ::android::hardware::audio::V2_0::TimeSpec& rhs);
static inline bool operator!=(const ::android::hardware::audio::V2_0::TimeSpec& lhs, const ::android::hardware::audio::V2_0::TimeSpec& rhs);

static inline std::string toString(const ::android::hardware::audio::V2_0::ParameterValue& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::ParameterValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V2_0::ParameterValue& lhs, const ::android::hardware::audio::V2_0::ParameterValue& rhs);
static inline bool operator!=(const ::android::hardware::audio::V2_0::ParameterValue& lhs, const ::android::hardware::audio::V2_0::ParameterValue& rhs);

static inline std::string toString(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& rhs);
static inline bool operator!=(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& rhs);

static inline std::string toString(const ::android::hardware::audio::V2_0::DeviceAddress::Address& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::DeviceAddress::Address& o, ::std::ostream*);
// operator== and operator!= are not generated for Address

static inline std::string toString(const ::android::hardware::audio::V2_0::DeviceAddress& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::DeviceAddress& o, ::std::ostream*);
// operator== and operator!= are not generated for DeviceAddress

static inline std::string toString(const ::android::hardware::audio::V2_0::MmapBufferInfo& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::MmapBufferInfo& o, ::std::ostream*);
// operator== and operator!= are not generated for MmapBufferInfo

static inline std::string toString(const ::android::hardware::audio::V2_0::MmapPosition& o);
static inline void PrintTo(const ::android::hardware::audio::V2_0::MmapPosition& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V2_0::MmapPosition& lhs, const ::android::hardware::audio::V2_0::MmapPosition& rhs);
static inline bool operator!=(const ::android::hardware::audio::V2_0::MmapPosition& lhs, const ::android::hardware::audio::V2_0::MmapPosition& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::audio::V2_0::MessageQueueFlagBits o);
static inline void PrintTo(::android::hardware::audio::V2_0::MessageQueueFlagBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::audio::V2_0::MessageQueueFlagBits lhs, const ::android::hardware::audio::V2_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::audio::V2_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::audio::V2_0::MessageQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::audio::V2_0::MessageQueueFlagBits lhs, const ::android::hardware::audio::V2_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::audio::V2_0::MessageQueueFlagBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::audio::V2_0::MessageQueueFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::audio::V2_0::MessageQueueFlagBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::audio::V2_0::MessageQueueFlagBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::V2_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V2_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V2_0::Result::OK) == static_cast<int32_t>(::android::hardware::audio::V2_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::Result::OK;
    }
    if ((o & ::android::hardware::audio::V2_0::Result::NOT_INITIALIZED) == static_cast<int32_t>(::android::hardware::audio::V2_0::Result::NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::Result::NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::audio::V2_0::Result::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::audio::V2_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::Result::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::audio::V2_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::audio::V2_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::audio::V2_0::Result::NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::audio::V2_0::Result::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::Result::NOT_SUPPORTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V2_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V2_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::audio::V2_0::Result::NOT_INITIALIZED) {
        return "NOT_INITIALIZED";
    }
    if (o == ::android::hardware::audio::V2_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::audio::V2_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::audio::V2_0::Result::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V2_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::V2_0::AudioDrain>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V2_0::AudioDrain> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V2_0::AudioDrain::ALL) == static_cast<int32_t>(::android::hardware::audio::V2_0::AudioDrain::ALL)) {
        os += (first ? "" : " | ");
        os += "ALL";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::AudioDrain::ALL;
    }
    if ((o & ::android::hardware::audio::V2_0::AudioDrain::EARLY_NOTIFY) == static_cast<int32_t>(::android::hardware::audio::V2_0::AudioDrain::EARLY_NOTIFY)) {
        os += (first ? "" : " | ");
        os += "EARLY_NOTIFY";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::AudioDrain::EARLY_NOTIFY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V2_0::AudioDrain o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V2_0::AudioDrain::ALL) {
        return "ALL";
    }
    if (o == ::android::hardware::audio::V2_0::AudioDrain::EARLY_NOTIFY) {
        return "EARLY_NOTIFY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V2_0::AudioDrain o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::V2_0::TimeSpec& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tvSec = ";
    os += ::android::hardware::toString(o.tvSec);
    os += ", .tvNSec = ";
    os += ::android::hardware::toString(o.tvNSec);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::TimeSpec& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V2_0::TimeSpec& lhs, const ::android::hardware::audio::V2_0::TimeSpec& rhs) {
    if (lhs.tvSec != rhs.tvSec) {
        return false;
    }
    if (lhs.tvNSec != rhs.tvNSec) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V2_0::TimeSpec& lhs, const ::android::hardware::audio::V2_0::TimeSpec& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V2_0::ParameterValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::ParameterValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V2_0::ParameterValue& lhs, const ::android::hardware::audio::V2_0::ParameterValue& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V2_0::ParameterValue& lhs, const ::android::hardware::audio::V2_0::ParameterValue& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".card = ";
    os += ::android::hardware::toString(o.card);
    os += ", .device = ";
    os += ::android::hardware::toString(o.device);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& rhs) {
    if (lhs.card != rhs.card) {
        return false;
    }
    if (lhs.device != rhs.device) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& lhs, const ::android::hardware::audio::V2_0::DeviceAddress::Address::Alsa& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V2_0::DeviceAddress::Address& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".mac = ";
    os += ::android::hardware::toString(o.mac);
    os += ", .ipv4 = ";
    os += ::android::hardware::toString(o.ipv4);
    os += ", .alsa = ";
    os += ::android::hardware::audio::V2_0::toString(o.alsa);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::DeviceAddress::Address& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Address

static inline std::string toString(const ::android::hardware::audio::V2_0::DeviceAddress& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".device = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.device);
    os += ", .address = ";
    os += ::android::hardware::audio::V2_0::toString(o.address);
    os += ", .busAddress = ";
    os += ::android::hardware::toString(o.busAddress);
    os += ", .rSubmixAddress = ";
    os += ::android::hardware::toString(o.rSubmixAddress);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::DeviceAddress& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DeviceAddress

static inline std::string toString(const ::android::hardware::audio::V2_0::MmapBufferInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sharedMemory = ";
    os += ::android::hardware::toString(o.sharedMemory);
    os += ", .bufferSizeFrames = ";
    os += ::android::hardware::toString(o.bufferSizeFrames);
    os += ", .burstSizeFrames = ";
    os += ::android::hardware::toString(o.burstSizeFrames);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::MmapBufferInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for MmapBufferInfo

static inline std::string toString(const ::android::hardware::audio::V2_0::MmapPosition& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timeNanoseconds = ";
    os += ::android::hardware::toString(o.timeNanoseconds);
    os += ", .positionFrames = ";
    os += ::android::hardware::toString(o.positionFrames);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V2_0::MmapPosition& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V2_0::MmapPosition& lhs, const ::android::hardware::audio::V2_0::MmapPosition& rhs) {
    if (lhs.timeNanoseconds != rhs.timeNanoseconds) {
        return false;
    }
    if (lhs.positionFrames != rhs.positionFrames) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V2_0::MmapPosition& lhs, const ::android::hardware::audio::V2_0::MmapPosition& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::audio::V2_0::MessageQueueFlagBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V2_0::MessageQueueFlagBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_EMPTY) == static_cast<uint32_t>(::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_EMPTY)) {
        os += (first ? "" : " | ");
        os += "NOT_EMPTY";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_EMPTY;
    }
    if ((o & ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_FULL) == static_cast<uint32_t>(::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_FULL)) {
        os += (first ? "" : " | ");
        os += "NOT_FULL";
        first = false;
        flipped |= ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V2_0::MessageQueueFlagBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_EMPTY) {
        return "NOT_EMPTY";
    }
    if (o == ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_FULL) {
        return "NOT_FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V2_0::MessageQueueFlagBits o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V2_0
}  // namespace audio
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
template<> inline constexpr std::array<::android::hardware::audio::V2_0::Result, 5> hidl_enum_values<::android::hardware::audio::V2_0::Result> = {
    ::android::hardware::audio::V2_0::Result::OK,
    ::android::hardware::audio::V2_0::Result::NOT_INITIALIZED,
    ::android::hardware::audio::V2_0::Result::INVALID_ARGUMENTS,
    ::android::hardware::audio::V2_0::Result::INVALID_STATE,
    ::android::hardware::audio::V2_0::Result::NOT_SUPPORTED,
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
template<> inline constexpr std::array<::android::hardware::audio::V2_0::AudioDrain, 2> hidl_enum_values<::android::hardware::audio::V2_0::AudioDrain> = {
    ::android::hardware::audio::V2_0::AudioDrain::ALL,
    ::android::hardware::audio::V2_0::AudioDrain::EARLY_NOTIFY,
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
template<> inline constexpr std::array<::android::hardware::audio::V2_0::MessageQueueFlagBits, 2> hidl_enum_values<::android::hardware::audio::V2_0::MessageQueueFlagBits> = {
    ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_EMPTY,
    ::android::hardware::audio::V2_0::MessageQueueFlagBits::NOT_FULL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V2_0_TYPES_H
