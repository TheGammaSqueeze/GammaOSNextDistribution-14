#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_1 {

// Forward declaration for forward reference support:
struct ModemInfo;
struct PhoneCapability;
struct ModemsConfig;

/**
 * Note: IRadioConfig 1.1 is an intermediate layer between Android P and Android Q.
 * It's specifically designed for CBRS related interfaces. All other interfaces
 * for Q are be added in IRadioConfig 1.2.
 *
 *
 * A field in PhoneCapability that has information of each logical modem.
 */
struct ModemInfo final {
    /**
     * Logical modem ID.
     */
    uint8_t modemId __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::radio::config::V1_1::ModemInfo, modemId) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::radio::config::V1_1::ModemInfo) == 1, "wrong size");
static_assert(__alignof(::android::hardware::radio::config::V1_1::ModemInfo) == 1, "wrong alignment");

/**
 * Phone capability which describes the data connection capability of modem.
 * It's used to evaluate possible phone config change, for example from single
 * SIM device to multi-SIM device.
 */
struct PhoneCapability final {
    /**
     * maxActiveData defines how many logical modems can have
     * PS attached simultaneously. For example, for L+L modem it
     * should be 2.
     */
    uint8_t maxActiveData __attribute__ ((aligned(1)));
    /**
     * maxActiveData defines how many logical modems can have
     * internet PDN connections simultaneously. For example, for L+L
     * DSDS modem it’s 1, and for DSDA modem it’s 2.
     */
    uint8_t maxActiveInternetData __attribute__ ((aligned(1)));
    /**
     * Whether modem supports both internet PDN up so
     * that we can do ping test before tearing down the
     * other one.
     */
    bool isInternetLingeringSupported __attribute__ ((aligned(1)));
    /**
     * List of logical modem information.
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::config::V1_1::ModemInfo> logicalModemList __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::config::V1_1::PhoneCapability, maxActiveData) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_1::PhoneCapability, maxActiveInternetData) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_1::PhoneCapability, isInternetLingeringSupported) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_1::PhoneCapability, logicalModemList) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::radio::config::V1_1::PhoneCapability) == 24, "wrong size");
static_assert(__alignof(::android::hardware::radio::config::V1_1::PhoneCapability) == 8, "wrong alignment");

struct ModemsConfig final {
    /**
     * variable to indicate the number of live modems i.e modems that are enabled
     * and actively working as part of a working connectivity stack
     */
    uint8_t numOfLiveModems __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::radio::config::V1_1::ModemsConfig, numOfLiveModems) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::radio::config::V1_1::ModemsConfig) == 1, "wrong size");
static_assert(__alignof(::android::hardware::radio::config::V1_1::ModemsConfig) == 1, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::radio::config::V1_1::ModemInfo& o);
static inline void PrintTo(const ::android::hardware::radio::config::V1_1::ModemInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::config::V1_1::ModemInfo& lhs, const ::android::hardware::radio::config::V1_1::ModemInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::config::V1_1::ModemInfo& lhs, const ::android::hardware::radio::config::V1_1::ModemInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::config::V1_1::PhoneCapability& o);
static inline void PrintTo(const ::android::hardware::radio::config::V1_1::PhoneCapability& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::config::V1_1::PhoneCapability& lhs, const ::android::hardware::radio::config::V1_1::PhoneCapability& rhs);
static inline bool operator!=(const ::android::hardware::radio::config::V1_1::PhoneCapability& lhs, const ::android::hardware::radio::config::V1_1::PhoneCapability& rhs);

static inline std::string toString(const ::android::hardware::radio::config::V1_1::ModemsConfig& o);
static inline void PrintTo(const ::android::hardware::radio::config::V1_1::ModemsConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::config::V1_1::ModemsConfig& lhs, const ::android::hardware::radio::config::V1_1::ModemsConfig& rhs);
static inline bool operator!=(const ::android::hardware::radio::config::V1_1::ModemsConfig& lhs, const ::android::hardware::radio::config::V1_1::ModemsConfig& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::radio::config::V1_1::ModemInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modemId = ";
    os += ::android::hardware::toString(o.modemId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::config::V1_1::ModemInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::config::V1_1::ModemInfo& lhs, const ::android::hardware::radio::config::V1_1::ModemInfo& rhs) {
    if (lhs.modemId != rhs.modemId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::config::V1_1::ModemInfo& lhs, const ::android::hardware::radio::config::V1_1::ModemInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::config::V1_1::PhoneCapability& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".maxActiveData = ";
    os += ::android::hardware::toString(o.maxActiveData);
    os += ", .maxActiveInternetData = ";
    os += ::android::hardware::toString(o.maxActiveInternetData);
    os += ", .isInternetLingeringSupported = ";
    os += ::android::hardware::toString(o.isInternetLingeringSupported);
    os += ", .logicalModemList = ";
    os += ::android::hardware::toString(o.logicalModemList);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::config::V1_1::PhoneCapability& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::config::V1_1::PhoneCapability& lhs, const ::android::hardware::radio::config::V1_1::PhoneCapability& rhs) {
    if (lhs.maxActiveData != rhs.maxActiveData) {
        return false;
    }
    if (lhs.maxActiveInternetData != rhs.maxActiveInternetData) {
        return false;
    }
    if (lhs.isInternetLingeringSupported != rhs.isInternetLingeringSupported) {
        return false;
    }
    if (lhs.logicalModemList != rhs.logicalModemList) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::config::V1_1::PhoneCapability& lhs, const ::android::hardware::radio::config::V1_1::PhoneCapability& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::config::V1_1::ModemsConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".numOfLiveModems = ";
    os += ::android::hardware::toString(o.numOfLiveModems);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::config::V1_1::ModemsConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::config::V1_1::ModemsConfig& lhs, const ::android::hardware::radio::config::V1_1::ModemsConfig& rhs) {
    if (lhs.numOfLiveModems != rhs.numOfLiveModems) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::config::V1_1::ModemsConfig& lhs, const ::android::hardware::radio::config::V1_1::ModemsConfig& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_1_TYPES_H
