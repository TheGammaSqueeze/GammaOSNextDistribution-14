#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tetheroffload {
namespace control {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class OffloadCallbackEvent : uint32_t;
enum class NetworkProtocol : uint32_t;
struct IPv4AddrPortPair;
struct NatTimeoutUpdate;

enum class OffloadCallbackEvent : uint32_t {
    /**
     * Indicate that a working configuration has been programmed and the
     * hardware management process has begun forwarding traffic.
     */
    OFFLOAD_STARTED = 1u,
    /**
     * Indicate that an error has occurred which has disrupted hardware
     * acceleration.  Software routing may still be attempted; however,
     * statistics may be temporarily unavailable.  Statistics may be recovered
     * after OFFLOAD_SUPPORT_AVAILABLE event is fired.
     */
    OFFLOAD_STOPPED_ERROR = 2u,
    /**
     * Indicate that the device has moved to a RAT on which hardware
     * acceleration is not supported.  Subsequent calls to setUpstreamParameters
     * and add/removeDownstream will likely fail and cannot be presumed to be
     * saved inside of the hardware management process.  Upon receiving
     * OFFLOAD_SUPPORT_AVAIALBLE, the client may reprogram the hardware
     * management process to begin offload again.
     */
    OFFLOAD_STOPPED_UNSUPPORTED = 3u,
    /**
     * Indicate that the hardware management process is willing and able to
     * provide support for hardware acceleration at this time.  If applicable,
     * the client may query for statistics.  If offload is desired, the client
     * must reprogram the hardware management process.
     */
    OFFLOAD_SUPPORT_AVAILABLE = 4u,
    /**
     * Hardware acceleration is no longer in effect and must be reprogrammed
     * in order to resume.  This event is fired when the limit, applied in
     * setDataLimit, has expired.  It is recommended that the client query for
     * statistics immediately after receiving this event.
     */
    OFFLOAD_STOPPED_LIMIT_REACHED = 5u,
};

enum class NetworkProtocol : uint32_t {
    TCP = 6u,
    UDP = 17u,
};

struct IPv4AddrPortPair final {
    /**
     * IPv4 Address and Port
     */
    ::android::hardware::hidl_string addr __attribute__ ((aligned(8)));
    uint16_t port __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair, addr) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair, port) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair) == 8, "wrong alignment");

struct NatTimeoutUpdate final {
    ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair src __attribute__ ((aligned(8)));
    ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair dst __attribute__ ((aligned(8)));
    ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol proto __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate, src) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate, dst) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate, proto) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate) == 56, "wrong size");
static_assert(__alignof(::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent o);
static inline void PrintTo(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent lhs, const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent lhs, const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tetheroffload::control::V1_0::NetworkProtocol o);
static inline void PrintTo(::android::hardware::tetheroffload::control::V1_0::NetworkProtocol o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol lhs, const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol lhs, const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& o);
static inline void PrintTo(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& lhs, const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& rhs);
static inline bool operator!=(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& lhs, const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& rhs);

static inline std::string toString(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& o);
static inline void PrintTo(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& lhs, const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& rhs);
static inline bool operator!=(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& lhs, const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STARTED) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STARTED)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_STARTED";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STARTED;
    }
    if ((o & ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_ERROR) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_ERROR)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_STOPPED_ERROR";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_ERROR;
    }
    if ((o & ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_UNSUPPORTED) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_UNSUPPORTED)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_STOPPED_UNSUPPORTED";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_UNSUPPORTED;
    }
    if ((o & ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_SUPPORT_AVAILABLE) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_SUPPORT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_SUPPORT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_SUPPORT_AVAILABLE;
    }
    if ((o & ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_LIMIT_REACHED) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_LIMIT_REACHED)) {
        os += (first ? "" : " | ");
        os += "OFFLOAD_STOPPED_LIMIT_REACHED";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_LIMIT_REACHED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STARTED) {
        return "OFFLOAD_STARTED";
    }
    if (o == ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_ERROR) {
        return "OFFLOAD_STOPPED_ERROR";
    }
    if (o == ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_UNSUPPORTED) {
        return "OFFLOAD_STOPPED_UNSUPPORTED";
    }
    if (o == ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_SUPPORT_AVAILABLE) {
        return "OFFLOAD_SUPPORT_AVAILABLE";
    }
    if (o == ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_LIMIT_REACHED) {
        return "OFFLOAD_STOPPED_LIMIT_REACHED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tetheroffload::control::V1_0::NetworkProtocol>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tetheroffload::control::V1_0::NetworkProtocol> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::TCP) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::TCP)) {
        os += (first ? "" : " | ");
        os += "TCP";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::TCP;
    }
    if ((o & ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::UDP) == static_cast<uint32_t>(::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::UDP)) {
        os += (first ? "" : " | ");
        os += "UDP";
        first = false;
        flipped |= ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::UDP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tetheroffload::control::V1_0::NetworkProtocol o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::TCP) {
        return "TCP";
    }
    if (o == ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::UDP) {
        return "UDP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tetheroffload::control::V1_0::NetworkProtocol o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".addr = ";
    os += ::android::hardware::toString(o.addr);
    os += ", .port = ";
    os += ::android::hardware::toString(o.port);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& lhs, const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& rhs) {
    if (lhs.addr != rhs.addr) {
        return false;
    }
    if (lhs.port != rhs.port) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& lhs, const ::android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".src = ";
    os += ::android::hardware::tetheroffload::control::V1_0::toString(o.src);
    os += ", .dst = ";
    os += ::android::hardware::tetheroffload::control::V1_0::toString(o.dst);
    os += ", .proto = ";
    os += ::android::hardware::tetheroffload::control::V1_0::toString(o.proto);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& lhs, const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& rhs) {
    if (lhs.src != rhs.src) {
        return false;
    }
    if (lhs.dst != rhs.dst) {
        return false;
    }
    if (lhs.proto != rhs.proto) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& lhs, const ::android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace control
}  // namespace tetheroffload
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
template<> inline constexpr std::array<::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent, 5> hidl_enum_values<::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent> = {
    ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STARTED,
    ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_ERROR,
    ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_UNSUPPORTED,
    ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_SUPPORT_AVAILABLE,
    ::android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent::OFFLOAD_STOPPED_LIMIT_REACHED,
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
template<> inline constexpr std::array<::android::hardware::tetheroffload::control::V1_0::NetworkProtocol, 2> hidl_enum_values<::android::hardware::tetheroffload::control::V1_0::NetworkProtocol> = {
    ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::TCP,
    ::android::hardware::tetheroffload::control::V1_0::NetworkProtocol::UDP,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_TYPES_H
