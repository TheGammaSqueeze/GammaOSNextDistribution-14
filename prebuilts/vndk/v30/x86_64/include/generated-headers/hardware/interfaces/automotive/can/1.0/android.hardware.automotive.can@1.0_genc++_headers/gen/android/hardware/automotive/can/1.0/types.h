#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {

// Forward declaration for forward reference support:
struct CanMessage;
enum class FilterFlag : uint8_t;
struct CanMessageFilter;
enum class Result : uint8_t;
enum class ErrorEvent : uint8_t;

// Order of inner types was changed for forward reference support.

/**
 * CAN message ID.
 *
 * Does not include any flags like RTR nor ERR, only a plain 11-bit
 * or 29-bit identifier, as defined in CAN 1.x/2.0x.
 *
 * Unused bits must be set to zero.
 */
typedef uint32_t CanMessageId;

/**
 * CAN message being sent or received.
 */
struct CanMessage final {
    uint32_t id __attribute__ ((aligned(4)));
    /**
     * CAN message payload, as defined in CAN 1.x and CAN 2.x standards.
     *
     * The length of the payload vector directly translates to the length
     * of the data frame (i.e. includes any padding bytes), so it may be in
     * a range of:
     *  - 0-8 bytes for standard CAN;
     *  - up to 64 bytes for CAN FD.
     * ISO TP is not supported directly for now.
     */
    ::android::hardware::hidl_vec<uint8_t> payload __attribute__ ((aligned(8)));
    /**
     * Time in nanoseconds since boot.
     *
     * Ignored for outgoing messages.
     */
    uint64_t timestamp __attribute__ ((aligned(8)));
    /**
     * A request to proactively pull certain data from other ECU in CAN network.
     *
     * For details please refer to CAN standard.
     *
     * If this flag is set, payload must be empty.
     */
    bool remoteTransmissionRequest __attribute__ ((aligned(1)));
    /**
     * Flag indicating if the message has an extended ID.
     *
     * Extended ID's are 29 bits long, as opposed to the standard 11 bit ID.
     * It can not simply be inferred from the length of the ID itself, as the
     * message ID 0x00000123 !=  message ID 0x123.
     */
    bool isExtendedId __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessage, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessage, payload) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessage, timestamp) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessage, remoteTransmissionRequest) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessage, isExtendedId) == 33, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::can::V1_0::CanMessage) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::can::V1_0::CanMessage) == 8, "wrong alignment");

/**
 * Types of filter that can be applied to a CanMessageFilter
 */
enum class FilterFlag : uint8_t {
    /**
     * Default, FilterFlag doesn't effect what messages filtered
     */
    DONT_CARE = 0,
    /**
     * This FilterFlag MUST be present in received messages to pass though the filter
     */
    SET = 1 /* ::android::hardware::automotive::can::V1_0::FilterFlag.DONT_CARE implicitly + 1 */,
    /**
     * This FilterFlag must NOT be present in received messages to pass though the filter
     */
    NOT_SET = 2 /* ::android::hardware::automotive::can::V1_0::FilterFlag.SET implicitly + 1 */,
};

/**
 * Single filter rule for CAN messages.
 *
 * A filter is satisfied if:
 * ((receivedId & mask) == (id & mask)) == !exclude
 *
 * In order for set of filters to match, at least one non-exclude filters must match (if there is
 * one) and all exclude filters must match. In other words:
 *  - a single matching non-exclude filter makes the whole set matching;
 *  - a single non-matching excluded filter makes the whole set non-matching.
 */
struct CanMessageFilter final {
    uint32_t id __attribute__ ((aligned(4)));
    uint32_t mask __attribute__ ((aligned(4)));
    /**
     * Remote Transmission Request; another ECU requests <DLC> bytes of data on this message ID
     */
    ::android::hardware::automotive::can::V1_0::FilterFlag rtr __attribute__ ((aligned(1)));
    /**
     * 29 bit message ID is used instead of 11 bits
     */
    ::android::hardware::automotive::can::V1_0::FilterFlag extendedFormat __attribute__ ((aligned(1)));
    /**
     * 'exclude' *DOES* apply to rtr and extendedFormat!
     */
    bool exclude __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessageFilter, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessageFilter, mask) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessageFilter, rtr) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessageFilter, extendedFormat) == 9, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::can::V1_0::CanMessageFilter, exclude) == 10, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::can::V1_0::CanMessageFilter) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::can::V1_0::CanMessageFilter) == 4, "wrong alignment");

enum class Result : uint8_t {
    OK = 0,
    UNKNOWN_ERROR = 1 /* ::android::hardware::automotive::can::V1_0::Result.OK implicitly + 1 */,
    PAYLOAD_TOO_LONG = 2 /* ::android::hardware::automotive::can::V1_0::Result.UNKNOWN_ERROR implicitly + 1 */,
    INTERFACE_DOWN = 3 /* ::android::hardware::automotive::can::V1_0::Result.PAYLOAD_TOO_LONG implicitly + 1 */,
    TRANSMISSION_FAILURE = 4 /* ::android::hardware::automotive::can::V1_0::Result.INTERFACE_DOWN implicitly + 1 */,
    INVALID_ARGUMENTS = 5 /* ::android::hardware::automotive::can::V1_0::Result.TRANSMISSION_FAILURE implicitly + 1 */,
};

/**
 * @see ICanMessageListener#onError
 */
enum class ErrorEvent : uint8_t {
    UNKNOWN_ERROR = 0,
    /**
     * A problem with CAN interface HW.
     */
    HARDWARE_ERROR = 1 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.UNKNOWN_ERROR implicitly + 1 */,
    /**
     * CAN interface is down.
     */
    INTERFACE_DOWN = 2 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.HARDWARE_ERROR implicitly + 1 */,
    /**
     * TX buffer overflow: client is sending too many packets.
     */
    TX_OVERFLOW = 3 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.INTERFACE_DOWN implicitly + 1 */,
    /**
     * RX buffer overflow: client is not reading packets fast enough.
     */
    RX_OVERFLOW = 4 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.TX_OVERFLOW implicitly + 1 */,
    /**
     * Received malformed input.
     */
    MALFORMED_INPUT = 5 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.RX_OVERFLOW implicitly + 1 */,
    /**
     * Bus overload: there is too much traffic on the bus.
     */
    BUS_OVERLOAD = 6 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.MALFORMED_INPUT implicitly + 1 */,
    /**
     * Bus error: shorted Hi/Lo line, bus off etc.
     */
    BUS_ERROR = 7 /* ::android::hardware::automotive::can::V1_0::ErrorEvent.BUS_OVERLOAD implicitly + 1 */,
};

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::CanMessage& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::CanMessage& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::CanMessage& lhs, const ::android::hardware::automotive::can::V1_0::CanMessage& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::CanMessage& lhs, const ::android::hardware::automotive::can::V1_0::CanMessage& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::automotive::can::V1_0::FilterFlag o);
static inline void PrintTo(::android::hardware::automotive::can::V1_0::FilterFlag o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::FilterFlag lhs, const ::android::hardware::automotive::can::V1_0::FilterFlag rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::FilterFlag rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::FilterFlag lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::FilterFlag lhs, const ::android::hardware::automotive::can::V1_0::FilterFlag rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::FilterFlag rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::FilterFlag lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::FilterFlag e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::FilterFlag e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& o);
static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& lhs, const ::android::hardware::automotive::can::V1_0::CanMessageFilter& rhs);
static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& lhs, const ::android::hardware::automotive::can::V1_0::CanMessageFilter& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::automotive::can::V1_0::Result o);
static inline void PrintTo(::android::hardware::automotive::can::V1_0::Result o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::Result lhs, const ::android::hardware::automotive::can::V1_0::Result rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::Result rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::Result lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::Result lhs, const ::android::hardware::automotive::can::V1_0::Result rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::Result rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::Result lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::Result e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::Result e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::automotive::can::V1_0::ErrorEvent o);
static inline void PrintTo(::android::hardware::automotive::can::V1_0::ErrorEvent o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::ErrorEvent lhs, const ::android::hardware::automotive::can::V1_0::ErrorEvent rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::ErrorEvent rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::automotive::can::V1_0::ErrorEvent lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::ErrorEvent lhs, const ::android::hardware::automotive::can::V1_0::ErrorEvent rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::automotive::can::V1_0::ErrorEvent rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::automotive::can::V1_0::ErrorEvent lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::ErrorEvent e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::automotive::can::V1_0::ErrorEvent e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::CanMessage& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .payload = ";
    os += ::android::hardware::toString(o.payload);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .remoteTransmissionRequest = ";
    os += ::android::hardware::toString(o.remoteTransmissionRequest);
    os += ", .isExtendedId = ";
    os += ::android::hardware::toString(o.isExtendedId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::CanMessage& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::CanMessage& lhs, const ::android::hardware::automotive::can::V1_0::CanMessage& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.payload != rhs.payload) {
        return false;
    }
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    if (lhs.remoteTransmissionRequest != rhs.remoteTransmissionRequest) {
        return false;
    }
    if (lhs.isExtendedId != rhs.isExtendedId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::CanMessage& lhs, const ::android::hardware::automotive::can::V1_0::CanMessage& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::can::V1_0::FilterFlag>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::can::V1_0::FilterFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::can::V1_0::FilterFlag::DONT_CARE) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::FilterFlag::DONT_CARE)) {
        os += (first ? "" : " | ");
        os += "DONT_CARE";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::FilterFlag::DONT_CARE;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::FilterFlag::SET) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::FilterFlag::SET)) {
        os += (first ? "" : " | ");
        os += "SET";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::FilterFlag::SET;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::FilterFlag::NOT_SET) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::FilterFlag::NOT_SET)) {
        os += (first ? "" : " | ");
        os += "NOT_SET";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::FilterFlag::NOT_SET;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::can::V1_0::FilterFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::can::V1_0::FilterFlag::DONT_CARE) {
        return "DONT_CARE";
    }
    if (o == ::android::hardware::automotive::can::V1_0::FilterFlag::SET) {
        return "SET";
    }
    if (o == ::android::hardware::automotive::can::V1_0::FilterFlag::NOT_SET) {
        return "NOT_SET";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::can::V1_0::FilterFlag o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .mask = ";
    os += ::android::hardware::toString(o.mask);
    os += ", .rtr = ";
    os += ::android::hardware::automotive::can::V1_0::toString(o.rtr);
    os += ", .extendedFormat = ";
    os += ::android::hardware::automotive::can::V1_0::toString(o.extendedFormat);
    os += ", .exclude = ";
    os += ::android::hardware::toString(o.exclude);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& lhs, const ::android::hardware::automotive::can::V1_0::CanMessageFilter& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.mask != rhs.mask) {
        return false;
    }
    if (lhs.rtr != rhs.rtr) {
        return false;
    }
    if (lhs.extendedFormat != rhs.extendedFormat) {
        return false;
    }
    if (lhs.exclude != rhs.exclude) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::can::V1_0::CanMessageFilter& lhs, const ::android::hardware::automotive::can::V1_0::CanMessageFilter& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::can::V1_0::Result>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::can::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::can::V1_0::Result::OK) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::Result::OK;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::Result::UNKNOWN_ERROR) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::Result::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::Result::UNKNOWN_ERROR;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::Result::PAYLOAD_TOO_LONG) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::Result::PAYLOAD_TOO_LONG)) {
        os += (first ? "" : " | ");
        os += "PAYLOAD_TOO_LONG";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::Result::PAYLOAD_TOO_LONG;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::Result::INTERFACE_DOWN) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::Result::INTERFACE_DOWN)) {
        os += (first ? "" : " | ");
        os += "INTERFACE_DOWN";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::Result::INTERFACE_DOWN;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::Result::TRANSMISSION_FAILURE) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::Result::TRANSMISSION_FAILURE)) {
        os += (first ? "" : " | ");
        os += "TRANSMISSION_FAILURE";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::Result::TRANSMISSION_FAILURE;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::Result::INVALID_ARGUMENTS) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::Result::INVALID_ARGUMENTS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::can::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::can::V1_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::automotive::can::V1_0::Result::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    if (o == ::android::hardware::automotive::can::V1_0::Result::PAYLOAD_TOO_LONG) {
        return "PAYLOAD_TOO_LONG";
    }
    if (o == ::android::hardware::automotive::can::V1_0::Result::INTERFACE_DOWN) {
        return "INTERFACE_DOWN";
    }
    if (o == ::android::hardware::automotive::can::V1_0::Result::TRANSMISSION_FAILURE) {
        return "TRANSMISSION_FAILURE";
    }
    if (o == ::android::hardware::automotive::can::V1_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::can::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::automotive::can::V1_0::ErrorEvent>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::can::V1_0::ErrorEvent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::UNKNOWN_ERROR) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::UNKNOWN_ERROR;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::HARDWARE_ERROR) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::HARDWARE_ERROR)) {
        os += (first ? "" : " | ");
        os += "HARDWARE_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::HARDWARE_ERROR;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::INTERFACE_DOWN) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::INTERFACE_DOWN)) {
        os += (first ? "" : " | ");
        os += "INTERFACE_DOWN";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::INTERFACE_DOWN;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::TX_OVERFLOW) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::TX_OVERFLOW)) {
        os += (first ? "" : " | ");
        os += "TX_OVERFLOW";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::TX_OVERFLOW;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::RX_OVERFLOW) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::RX_OVERFLOW)) {
        os += (first ? "" : " | ");
        os += "RX_OVERFLOW";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::RX_OVERFLOW;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::MALFORMED_INPUT) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::MALFORMED_INPUT)) {
        os += (first ? "" : " | ");
        os += "MALFORMED_INPUT";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::MALFORMED_INPUT;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_OVERLOAD) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_OVERLOAD)) {
        os += (first ? "" : " | ");
        os += "BUS_OVERLOAD";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_OVERLOAD;
    }
    if ((o & ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_ERROR) == static_cast<uint8_t>(::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_ERROR)) {
        os += (first ? "" : " | ");
        os += "BUS_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::can::V1_0::ErrorEvent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::HARDWARE_ERROR) {
        return "HARDWARE_ERROR";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::INTERFACE_DOWN) {
        return "INTERFACE_DOWN";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::TX_OVERFLOW) {
        return "TX_OVERFLOW";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::RX_OVERFLOW) {
        return "RX_OVERFLOW";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::MALFORMED_INPUT) {
        return "MALFORMED_INPUT";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_OVERLOAD) {
        return "BUS_OVERLOAD";
    }
    if (o == ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_ERROR) {
        return "BUS_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::can::V1_0::ErrorEvent o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace can
}  // namespace automotive
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
template<> inline constexpr std::array<::android::hardware::automotive::can::V1_0::FilterFlag, 3> hidl_enum_values<::android::hardware::automotive::can::V1_0::FilterFlag> = {
    ::android::hardware::automotive::can::V1_0::FilterFlag::DONT_CARE,
    ::android::hardware::automotive::can::V1_0::FilterFlag::SET,
    ::android::hardware::automotive::can::V1_0::FilterFlag::NOT_SET,
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
template<> inline constexpr std::array<::android::hardware::automotive::can::V1_0::Result, 6> hidl_enum_values<::android::hardware::automotive::can::V1_0::Result> = {
    ::android::hardware::automotive::can::V1_0::Result::OK,
    ::android::hardware::automotive::can::V1_0::Result::UNKNOWN_ERROR,
    ::android::hardware::automotive::can::V1_0::Result::PAYLOAD_TOO_LONG,
    ::android::hardware::automotive::can::V1_0::Result::INTERFACE_DOWN,
    ::android::hardware::automotive::can::V1_0::Result::TRANSMISSION_FAILURE,
    ::android::hardware::automotive::can::V1_0::Result::INVALID_ARGUMENTS,
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
template<> inline constexpr std::array<::android::hardware::automotive::can::V1_0::ErrorEvent, 8> hidl_enum_values<::android::hardware::automotive::can::V1_0::ErrorEvent> = {
    ::android::hardware::automotive::can::V1_0::ErrorEvent::UNKNOWN_ERROR,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::HARDWARE_ERROR,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::INTERFACE_DOWN,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::TX_OVERFLOW,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::RX_OVERFLOW,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::MALFORMED_INPUT,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_OVERLOAD,
    ::android::hardware::automotive::can::V1_0::ErrorEvent::BUS_ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_TYPES_H
