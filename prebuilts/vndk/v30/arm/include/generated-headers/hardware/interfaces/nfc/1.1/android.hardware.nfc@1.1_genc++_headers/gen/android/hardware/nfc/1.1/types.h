#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_TYPES_H

#include <android/hardware/nfc/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class NfcEvent : uint32_t;
enum class Constant : uint8_t;
struct ProtocolDiscoveryConfig;
enum class PresenceCheckAlgorithm : uint8_t;
struct NfcConfig;

enum class NfcEvent : uint32_t {
    OPEN_CPLT = 0u,
    CLOSE_CPLT = 1u,
    POST_INIT_CPLT = 2u,
    PRE_DISCOVER_CPLT = 3u,
    REQUEST_CONTROL = 4u,
    RELEASE_CONTROL = 5u,
    ERROR = 6u,
    /**
     * In case of an error, HCI network needs to be re-initialized
     */
    HCI_NETWORK_RESET = 7u,
};

enum class Constant : uint8_t {
    UNSUPPORTED_CONFIG = 255 /* 0xFF */,
};

/**
 * Vendor Specific Proprietary Protocol & Discovery Configuration.
 * Set to UNSUPPORTED_CONFIG if not supported.
 * discovery* fields map to "RF Technology and Mode" in NCI Spec
 * protocol* fields map to "RF protocols" in NCI Spec
 */
struct ProtocolDiscoveryConfig final {
    uint8_t protocol18092Active __attribute__ ((aligned(1)));
    uint8_t protocolBPrime __attribute__ ((aligned(1)));
    uint8_t protocolDual __attribute__ ((aligned(1)));
    uint8_t protocol15693 __attribute__ ((aligned(1)));
    uint8_t protocolKovio __attribute__ ((aligned(1)));
    uint8_t protocolMifare __attribute__ ((aligned(1)));
    uint8_t discoveryPollKovio __attribute__ ((aligned(1)));
    uint8_t discoveryPollBPrime __attribute__ ((aligned(1)));
    uint8_t discoveryListenBPrime __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, protocol18092Active) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, protocolBPrime) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, protocolDual) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, protocol15693) == 3, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, protocolKovio) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, protocolMifare) == 5, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, discoveryPollKovio) == 6, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, discoveryPollBPrime) == 7, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig, discoveryListenBPrime) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig) == 9, "wrong size");
static_assert(__alignof(::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig) == 1, "wrong alignment");

/*
 * Presence Check Algorithm as per ISO/IEC 14443-4
 */
enum class PresenceCheckAlgorithm : uint8_t {
    /**
     * Lets the stack select an algorithm
     */
    DEFAULT = 0,
    /**
     * ISO-DEP protocol's empty I-block
     */
    I_BLOCK = 1,
    /**
     * Type - 4 tag protocol iso-dep nak presence check command is sent waiting for
     * response and notification.
     */
    ISO_DEP_NAK = 2,
};

struct NfcConfig final {
    /**
     * If true, NFCC is using bail out mode for either Type A or Type B poll.
     */
    bool nfaPollBailOutMode __attribute__ ((aligned(1)));
    ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm presenceCheckAlgorithm __attribute__ ((aligned(1)));
    ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig nfaProprietaryCfg __attribute__ ((aligned(1)));
    /**
     * Default off-host route. 0x00 if there aren't any. Refer to NCI spec.
     */
    uint8_t defaultOffHostRoute __attribute__ ((aligned(1)));
    /**
     * Default off-host route for Felica. 0x00 if there aren't any. Refer to
     * NCI spec.
     */
    uint8_t defaultOffHostRouteFelica __attribute__ ((aligned(1)));
    /**
     * Default system code route. 0x00 if there aren't any. Refer NCI spec
     */
    uint8_t defaultSystemCodeRoute __attribute__ ((aligned(1)));
    /**
     * Default power state for system code route. 0x00 if there aren't any. Refer NCI spec
     */
    uint8_t defaultSystemCodePowerState __attribute__ ((aligned(1)));
    /**
     * Default route for all remaining protocols and technology which haven't
     * been configured.
     * Device Host(0x00) is the default. Refer to NCI spec.
     *
     */
    uint8_t defaultRoute __attribute__ ((aligned(1)));
    /**
     * Pipe ID for eSE. 0x00 if there aren't any.
     */
    uint8_t offHostESEPipeId __attribute__ ((aligned(1)));
    /**
     * Pipe ID for UICC. 0x00 if there aren't any.
     */
    uint8_t offHostSIMPipeId __attribute__ ((aligned(1)));
    /**
     * Extended APDU length for ISO_DEP. If not supported default length is 261
     */
    uint32_t maxIsoDepTransceiveLength __attribute__ ((aligned(4)));
    /**
     * list of white listed host ids, as per ETSI TS 102 622
     */
    ::android::hardware::hidl_vec<uint8_t> hostWhitelist __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, nfaPollBailOutMode) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, presenceCheckAlgorithm) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, nfaProprietaryCfg) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, defaultOffHostRoute) == 11, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, defaultOffHostRouteFelica) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, defaultSystemCodeRoute) == 13, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, defaultSystemCodePowerState) == 14, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, defaultRoute) == 15, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, offHostESEPipeId) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, offHostSIMPipeId) == 17, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, maxIsoDepTransceiveLength) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::nfc::V1_1::NfcConfig, hostWhitelist) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::nfc::V1_1::NfcConfig) == 40, "wrong size");
static_assert(__alignof(::android::hardware::nfc::V1_1::NfcConfig) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::nfc::V1_1::NfcEvent o);
static inline void PrintTo(::android::hardware::nfc::V1_1::NfcEvent o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::nfc::V1_1::NfcEvent lhs, const ::android::hardware::nfc::V1_1::NfcEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::nfc::V1_1::NfcEvent rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::nfc::V1_1::NfcEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::nfc::V1_1::NfcEvent lhs, const ::android::hardware::nfc::V1_1::NfcEvent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::nfc::V1_1::NfcEvent rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::nfc::V1_1::NfcEvent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::nfc::V1_1::NfcEvent e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::nfc::V1_1::NfcEvent e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::nfc::V1_1::Constant o);
static inline void PrintTo(::android::hardware::nfc::V1_1::Constant o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::nfc::V1_1::Constant lhs, const ::android::hardware::nfc::V1_1::Constant rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::nfc::V1_1::Constant rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::nfc::V1_1::Constant lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::nfc::V1_1::Constant lhs, const ::android::hardware::nfc::V1_1::Constant rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::nfc::V1_1::Constant rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::nfc::V1_1::Constant lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::nfc::V1_1::Constant e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::nfc::V1_1::Constant e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& o);
static inline void PrintTo(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& lhs, const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& rhs);
static inline bool operator!=(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& lhs, const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm o);
static inline void PrintTo(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm lhs, const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm lhs, const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::nfc::V1_1::NfcConfig& o);
static inline void PrintTo(const ::android::hardware::nfc::V1_1::NfcConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::nfc::V1_1::NfcConfig& lhs, const ::android::hardware::nfc::V1_1::NfcConfig& rhs);
static inline bool operator!=(const ::android::hardware::nfc::V1_1::NfcConfig& lhs, const ::android::hardware::nfc::V1_1::NfcConfig& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::nfc::V1_1::NfcEvent>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::nfc::V1_1::NfcEvent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::OPEN_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::OPEN_CPLT)) {
        os += (first ? "" : " | ");
        os += "OPEN_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::OPEN_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::CLOSE_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::CLOSE_CPLT)) {
        os += (first ? "" : " | ");
        os += "CLOSE_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::CLOSE_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::POST_INIT_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::POST_INIT_CPLT)) {
        os += (first ? "" : " | ");
        os += "POST_INIT_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::POST_INIT_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::PRE_DISCOVER_CPLT) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::PRE_DISCOVER_CPLT)) {
        os += (first ? "" : " | ");
        os += "PRE_DISCOVER_CPLT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::PRE_DISCOVER_CPLT;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::REQUEST_CONTROL) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::REQUEST_CONTROL)) {
        os += (first ? "" : " | ");
        os += "REQUEST_CONTROL";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::REQUEST_CONTROL;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::RELEASE_CONTROL) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::RELEASE_CONTROL)) {
        os += (first ? "" : " | ");
        os += "RELEASE_CONTROL";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::RELEASE_CONTROL;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::ERROR) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::ERROR;
    }
    if ((o & ::android::hardware::nfc::V1_1::NfcEvent::HCI_NETWORK_RESET) == static_cast<uint32_t>(::android::hardware::nfc::V1_1::NfcEvent::HCI_NETWORK_RESET)) {
        os += (first ? "" : " | ");
        os += "HCI_NETWORK_RESET";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::NfcEvent::HCI_NETWORK_RESET;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::nfc::V1_1::NfcEvent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::OPEN_CPLT) {
        return "OPEN_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::CLOSE_CPLT) {
        return "CLOSE_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::POST_INIT_CPLT) {
        return "POST_INIT_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::PRE_DISCOVER_CPLT) {
        return "PRE_DISCOVER_CPLT";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::REQUEST_CONTROL) {
        return "REQUEST_CONTROL";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::RELEASE_CONTROL) {
        return "RELEASE_CONTROL";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::ERROR) {
        return "ERROR";
    }
    if (o == ::android::hardware::nfc::V1_1::NfcEvent::HCI_NETWORK_RESET) {
        return "HCI_NETWORK_RESET";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::nfc::V1_1::NfcEvent o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::nfc::V1_1::Constant>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::nfc::V1_1::Constant> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::nfc::V1_1::Constant::UNSUPPORTED_CONFIG) == static_cast<uint8_t>(::android::hardware::nfc::V1_1::Constant::UNSUPPORTED_CONFIG)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_CONFIG";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::Constant::UNSUPPORTED_CONFIG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::nfc::V1_1::Constant o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::nfc::V1_1::Constant::UNSUPPORTED_CONFIG) {
        return "UNSUPPORTED_CONFIG";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::nfc::V1_1::Constant o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".protocol18092Active = ";
    os += ::android::hardware::toString(o.protocol18092Active);
    os += ", .protocolBPrime = ";
    os += ::android::hardware::toString(o.protocolBPrime);
    os += ", .protocolDual = ";
    os += ::android::hardware::toString(o.protocolDual);
    os += ", .protocol15693 = ";
    os += ::android::hardware::toString(o.protocol15693);
    os += ", .protocolKovio = ";
    os += ::android::hardware::toString(o.protocolKovio);
    os += ", .protocolMifare = ";
    os += ::android::hardware::toString(o.protocolMifare);
    os += ", .discoveryPollKovio = ";
    os += ::android::hardware::toString(o.discoveryPollKovio);
    os += ", .discoveryPollBPrime = ";
    os += ::android::hardware::toString(o.discoveryPollBPrime);
    os += ", .discoveryListenBPrime = ";
    os += ::android::hardware::toString(o.discoveryListenBPrime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& lhs, const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& rhs) {
    if (lhs.protocol18092Active != rhs.protocol18092Active) {
        return false;
    }
    if (lhs.protocolBPrime != rhs.protocolBPrime) {
        return false;
    }
    if (lhs.protocolDual != rhs.protocolDual) {
        return false;
    }
    if (lhs.protocol15693 != rhs.protocol15693) {
        return false;
    }
    if (lhs.protocolKovio != rhs.protocolKovio) {
        return false;
    }
    if (lhs.protocolMifare != rhs.protocolMifare) {
        return false;
    }
    if (lhs.discoveryPollKovio != rhs.discoveryPollKovio) {
        return false;
    }
    if (lhs.discoveryPollBPrime != rhs.discoveryPollBPrime) {
        return false;
    }
    if (lhs.discoveryListenBPrime != rhs.discoveryListenBPrime) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& lhs, const ::android::hardware::nfc::V1_1::ProtocolDiscoveryConfig& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::nfc::V1_1::PresenceCheckAlgorithm>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::nfc::V1_1::PresenceCheckAlgorithm> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::DEFAULT) == static_cast<uint8_t>(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::DEFAULT;
    }
    if ((o & ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::I_BLOCK) == static_cast<uint8_t>(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::I_BLOCK)) {
        os += (first ? "" : " | ");
        os += "I_BLOCK";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::I_BLOCK;
    }
    if ((o & ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::ISO_DEP_NAK) == static_cast<uint8_t>(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::ISO_DEP_NAK)) {
        os += (first ? "" : " | ");
        os += "ISO_DEP_NAK";
        first = false;
        flipped |= ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::ISO_DEP_NAK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::I_BLOCK) {
        return "I_BLOCK";
    }
    if (o == ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::ISO_DEP_NAK) {
        return "ISO_DEP_NAK";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::nfc::V1_1::PresenceCheckAlgorithm o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::nfc::V1_1::NfcConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".nfaPollBailOutMode = ";
    os += ::android::hardware::toString(o.nfaPollBailOutMode);
    os += ", .presenceCheckAlgorithm = ";
    os += ::android::hardware::nfc::V1_1::toString(o.presenceCheckAlgorithm);
    os += ", .nfaProprietaryCfg = ";
    os += ::android::hardware::nfc::V1_1::toString(o.nfaProprietaryCfg);
    os += ", .defaultOffHostRoute = ";
    os += ::android::hardware::toString(o.defaultOffHostRoute);
    os += ", .defaultOffHostRouteFelica = ";
    os += ::android::hardware::toString(o.defaultOffHostRouteFelica);
    os += ", .defaultSystemCodeRoute = ";
    os += ::android::hardware::toString(o.defaultSystemCodeRoute);
    os += ", .defaultSystemCodePowerState = ";
    os += ::android::hardware::toString(o.defaultSystemCodePowerState);
    os += ", .defaultRoute = ";
    os += ::android::hardware::toString(o.defaultRoute);
    os += ", .offHostESEPipeId = ";
    os += ::android::hardware::toString(o.offHostESEPipeId);
    os += ", .offHostSIMPipeId = ";
    os += ::android::hardware::toString(o.offHostSIMPipeId);
    os += ", .maxIsoDepTransceiveLength = ";
    os += ::android::hardware::toString(o.maxIsoDepTransceiveLength);
    os += ", .hostWhitelist = ";
    os += ::android::hardware::toString(o.hostWhitelist);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::nfc::V1_1::NfcConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::nfc::V1_1::NfcConfig& lhs, const ::android::hardware::nfc::V1_1::NfcConfig& rhs) {
    if (lhs.nfaPollBailOutMode != rhs.nfaPollBailOutMode) {
        return false;
    }
    if (lhs.presenceCheckAlgorithm != rhs.presenceCheckAlgorithm) {
        return false;
    }
    if (lhs.nfaProprietaryCfg != rhs.nfaProprietaryCfg) {
        return false;
    }
    if (lhs.defaultOffHostRoute != rhs.defaultOffHostRoute) {
        return false;
    }
    if (lhs.defaultOffHostRouteFelica != rhs.defaultOffHostRouteFelica) {
        return false;
    }
    if (lhs.defaultSystemCodeRoute != rhs.defaultSystemCodeRoute) {
        return false;
    }
    if (lhs.defaultSystemCodePowerState != rhs.defaultSystemCodePowerState) {
        return false;
    }
    if (lhs.defaultRoute != rhs.defaultRoute) {
        return false;
    }
    if (lhs.offHostESEPipeId != rhs.offHostESEPipeId) {
        return false;
    }
    if (lhs.offHostSIMPipeId != rhs.offHostSIMPipeId) {
        return false;
    }
    if (lhs.maxIsoDepTransceiveLength != rhs.maxIsoDepTransceiveLength) {
        return false;
    }
    if (lhs.hostWhitelist != rhs.hostWhitelist) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::nfc::V1_1::NfcConfig& lhs, const ::android::hardware::nfc::V1_1::NfcConfig& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
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
template<> inline constexpr std::array<::android::hardware::nfc::V1_1::NfcEvent, 8> hidl_enum_values<::android::hardware::nfc::V1_1::NfcEvent> = {
    ::android::hardware::nfc::V1_1::NfcEvent::OPEN_CPLT,
    ::android::hardware::nfc::V1_1::NfcEvent::CLOSE_CPLT,
    ::android::hardware::nfc::V1_1::NfcEvent::POST_INIT_CPLT,
    ::android::hardware::nfc::V1_1::NfcEvent::PRE_DISCOVER_CPLT,
    ::android::hardware::nfc::V1_1::NfcEvent::REQUEST_CONTROL,
    ::android::hardware::nfc::V1_1::NfcEvent::RELEASE_CONTROL,
    ::android::hardware::nfc::V1_1::NfcEvent::ERROR,
    ::android::hardware::nfc::V1_1::NfcEvent::HCI_NETWORK_RESET,
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
template<> inline constexpr std::array<::android::hardware::nfc::V1_1::Constant, 1> hidl_enum_values<::android::hardware::nfc::V1_1::Constant> = {
    ::android::hardware::nfc::V1_1::Constant::UNSUPPORTED_CONFIG,
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
template<> inline constexpr std::array<::android::hardware::nfc::V1_1::PresenceCheckAlgorithm, 3> hidl_enum_values<::android::hardware::nfc::V1_1::PresenceCheckAlgorithm> = {
    ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::DEFAULT,
    ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::I_BLOCK,
    ::android::hardware::nfc::V1_1::PresenceCheckAlgorithm::ISO_DEP_NAK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_TYPES_H
