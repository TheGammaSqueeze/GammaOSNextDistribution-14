#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_TYPES_H

#include <android/hardware/broadcastradio/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {

// Forward declaration for forward reference support:
enum class ProgramListResult : int32_t;
enum class ProgramInfoFlags : uint32_t;
struct VendorKeyValue;
struct Properties;
enum class Modulation : uint32_t;
enum class ProgramType : uint32_t;
enum class IdentifierType : uint32_t;
struct ProgramIdentifier;
struct ProgramSelector;
struct ProgramInfo;

typedef ::android::hardware::broadcastradio::V1_0::Result Result;

enum class ProgramListResult : int32_t {
    OK = 0,
    NOT_INITIALIZED = 1 /* ::android::hardware::broadcastradio::V1_0::Result.OK implicitly + 1 */,
    INVALID_ARGUMENTS = 2 /* ::android::hardware::broadcastradio::V1_0::Result.NOT_INITIALIZED implicitly + 1 */,
    INVALID_STATE = 3 /* ::android::hardware::broadcastradio::V1_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
    TIMEOUT = 4 /* ::android::hardware::broadcastradio::V1_0::Result.INVALID_STATE implicitly + 1 */,
    NOT_READY = 5 /* ::android::hardware::broadcastradio::V1_0::Result.TIMEOUT implicitly + 1 */,
    NOT_STARTED = 6 /* ::android::hardware::broadcastradio::V1_1::ProgramListResult.NOT_READY implicitly + 1 */,
    UNAVAILABLE = 7 /* ::android::hardware::broadcastradio::V1_1::ProgramListResult.NOT_STARTED implicitly + 1 */,
};

/**
 * Extra flags for program information.
 */
enum class ProgramInfoFlags : uint32_t {
    /**
     * Set when the program is currently playing live stream.
     * This may result in a slightly altered reception parameters,
     * usually targetted at reduced latency.
     */
    LIVE = 1u /* 1 << 0 */,
    /**
     * Radio stream is not playing, ie. due to bad reception conditions or
     * buffering. In this state volume knob MAY be disabled to prevent user
     * increasing volume too much.
     */
    MUTED = 2u /* 1 << 1 */,
    /**
     * Station broadcasts traffic information regularly,
     * but not necessarily right now.
     */
    TRAFFIC_PROGRAM = 4u /* 1 << 2 */,
    /**
     * Station is broadcasting traffic information at the very moment.
     */
    TRAFFIC_ANNOUNCEMENT = 8u /* 1 << 3 */,
};

/**
 * A key-value pair for vendor-specific information to be passed as-is through
 * Android framework to the front-end application.
 */
struct VendorKeyValue final {
    /**
     * Key must be prefixed with unique vendor Java-style namespace,
     * eg. 'com.somecompany.parameter1'.
     */
    ::android::hardware::hidl_string key __attribute__ ((aligned(8)));
    /**
     * Value must be passed through the framework without any changes.
     * Format of this string can vary across vendors.
     */
    ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_1::VendorKeyValue, key) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::VendorKeyValue, value) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_1::VendorKeyValue) == 32, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_1::VendorKeyValue) == 8, "wrong alignment");

struct Properties final {
    ::android::hardware::broadcastradio::V1_0::Properties base __attribute__ ((aligned(8)));
    /**
     * The hardware supports background scanning in general. At the given time
     * it may not be available though, see startBackgroundScan.
     */
    bool supportsBackgroundScanning __attribute__ ((aligned(1)));
    /**
     * A list of supported ProgramType values.
     *
     * If a program type is supported by radio module, it means it can tune
     * to ProgramSelector of a given type.
     *
     * Support for VENDOR program type does not guarantee compatibility, as
     * other module properties (implementor, product, version) must be checked.
     */
    ::android::hardware::hidl_vec<uint32_t> supportedProgramTypes __attribute__ ((aligned(8)));
    /**
     * A list of supported IdentifierType values.
     *
     * If an identifier is supported by radio module, it means it can use it for
     * tuning to ProgramSelector with either primary or secondary Identifier of
     * a given type.
     *
     * Support for VENDOR identifier type does not guarantee compatibility, as
     * other module properties (implementor, product, version) must be checked.
     */
    ::android::hardware::hidl_vec<uint32_t> supportedIdentifierTypes __attribute__ ((aligned(8)));
    /**
     * Vendor-specific information.
     *
     * It may be used for extra features, not supported by the platform,
     * for example: com.me.preset-slots=6; com.me.ultra-hd-capable=false.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_1::VendorKeyValue> vendorInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_1::Properties, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::Properties, supportsBackgroundScanning) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::Properties, supportedProgramTypes) == 112, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::Properties, supportedIdentifierTypes) == 128, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::Properties, vendorInfo) == 144, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_1::Properties) == 160, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_1::Properties) == 8, "wrong alignment");

/**
 * Type of modulation.
 *
 * Used as a value for DRMO_MODULATION IdentifierType.
 */
enum class Modulation : uint32_t {
    AM = 1u,
    FM = 2u /* ::android::hardware::broadcastradio::V1_1::Modulation.AM implicitly + 1 */,
};

/**
 * Type of a radio technology.
 *
 * VENDOR program types must be opaque to the framework.
 *
 * There are multiple VENDOR program types just to make vendor implementation
 * easier with multiple properitary radio technologies. They are treated the
 * same by the framework.
 *
 * All other values are reserved for future use.
 * Values not matching any enumerated constant must be ignored.
 */
enum class ProgramType : uint32_t {
    AM = 1u,
    FM = 2u /* ::android::hardware::broadcastradio::V1_1::ProgramType.AM implicitly + 1 */,
    AM_HD = 3u /* ::android::hardware::broadcastradio::V1_1::ProgramType.FM implicitly + 1 */,
    FM_HD = 4u /* ::android::hardware::broadcastradio::V1_1::ProgramType.AM_HD implicitly + 1 */,
    DAB = 5u /* ::android::hardware::broadcastradio::V1_1::ProgramType.FM_HD implicitly + 1 */,
    DRMO = 6u /* ::android::hardware::broadcastradio::V1_1::ProgramType.DAB implicitly + 1 */,
    SXM = 7u /* ::android::hardware::broadcastradio::V1_1::ProgramType.DRMO implicitly + 1 */,
    VENDOR_START = 1000u,
    VENDOR_END = 1999u,
};

/**
 * Type of program identifier component.
 *
 * It MUST match the radio technology for primary ID but does not have to match
 * it for secondary IDs. For example, a satellite program may set AM/FM fallback
 * frequency, if a station broadcasts both via satellite and AM/FM.
 *
 * VENDOR identifier types must be opaque to the framework.
 *
 * The value format for each (but VENDOR_PRIMARY) identifier is strictly defined
 * to maintain interoperability between devices made by different vendors.
 *
 * All other values are reserved for future use.
 * Values not matching any enumerated constant must be ignored.
 */
enum class IdentifierType : uint32_t {
    AMFM_FREQUENCY = 1u,
    RDS_PI = 2u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.AMFM_FREQUENCY implicitly + 1 */,
    /**
     * 64bit compound primary identifier for HD Radio.
     *
     * Consists of (from the LSB):
     * - 32bit: Station ID number;
     * - 4bit: HD_SUBCHANNEL;
     * - 18bit: AMFM_FREQUENCY.
     * The remaining bits should be set to zeros when writing on the chip side
     * and ignored when read.
     */
    HD_STATION_ID_EXT = 3u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.RDS_PI implicitly + 1 */,
    /**
     * HD Radio subchannel - a value of range 0-7.
     *
     * The subchannel index is 0-based (where 0 is MPS and 1..7 are SPS),
     * as opposed to HD Radio standard (where it's 1-based).
     */
    HD_SUBCHANNEL = 4u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.HD_STATION_ID_EXT implicitly + 1 */,
    /**
     * 24bit compound primary identifier for DAB.
     *
     * Consists of (from the LSB):
     * - 16bit: SId;
     * - 8bit: ECC code.
     * The remaining bits should be set to zeros when writing on the chip side
     * and ignored when read.
     */
    DAB_SIDECC = 5u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.HD_SUBCHANNEL implicitly + 1 */,
    DAB_ENSEMBLE = 6u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DAB_SIDECC implicitly + 1 */,
    DAB_SCID = 7u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DAB_ENSEMBLE implicitly + 1 */,
    DAB_FREQUENCY = 8u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DAB_SCID implicitly + 1 */,
    DRMO_SERVICE_ID = 9u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DAB_FREQUENCY implicitly + 1 */,
    DRMO_FREQUENCY = 10u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DRMO_SERVICE_ID implicitly + 1 */,
    DRMO_MODULATION = 11u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DRMO_FREQUENCY implicitly + 1 */,
    SXM_SERVICE_ID = 12u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.DRMO_MODULATION implicitly + 1 */,
    SXM_CHANNEL = 13u /* ::android::hardware::broadcastradio::V1_1::IdentifierType.SXM_SERVICE_ID implicitly + 1 */,
    /**
     * Primary identifier for vendor-specific radio technology.
     * The value format is determined by a vendor.
     *
     * It must not be used in any other programType than corresponding VENDOR
     * type between VENDOR_START and VENDOR_END (eg. identifier type 1015 must
     * not be used in any program type other than 1015).
     */
    VENDOR_PRIMARY_START = 1000u /* ProgramType:VENDOR_START */,
    VENDOR_PRIMARY_END = 1999u /* ProgramType:VENDOR_END */,
};

/**
 * A single program identifier component, eg. frequency or channel ID.
 *
 * The uint32_t type field maps to IdentifierType enum. It's not straight,
 * because the enum may be extended in future versions of the HAL. Values out of
 * the enum range must not be used when writing and ignored when reading.
 *
 * The uint64_t value field holds the value in format described in comments for
 * IdentifierType enum.
 */
struct ProgramIdentifier final {
    uint32_t type __attribute__ ((aligned(4)));
    uint64_t value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramIdentifier, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramIdentifier, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_1::ProgramIdentifier) == 16, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_1::ProgramIdentifier) == 8, "wrong alignment");

/**
 * A set of identifiers necessary to tune to a given station.
 *
 * This can hold various identifiers, like
 * - AM/FM frequency
 * - HD Radio subchannel
 * - DAB channel info
 *
 * The uint32_t programType field maps to ProgramType enum. It's not straight,
 * because the enum may be extended in future versions of the HAL. Values out of
 * the enum range must not be used when writing and ignored when reading.
 *
 * The primary ID uniquely identifies a station and can be used for equality
 * check. The secondary IDs are supplementary and can speed up tuning process,
 * but the primary ID is sufficient (ie. after a full band scan).
 *
 * Two selectors with different secondary IDs, but the same primary ID are
 * considered equal. In particular, secondary IDs vector may get updated for
 * an entry on the program list (ie. when a better frequency for a given
 * station is found).
 *
 * The primaryId of a given programType MUST be of a specific type:
 * - AM, FM: RDS_PI if the station broadcasts RDS, AMFM_FREQUENCY otherwise;
 * - AM_HD, FM_HD: HD_STATION_ID_EXT;
 * - DAB: DAB_SIDECC;
 * - DRMO: DRMO_SERVICE_ID;
 * - SXM: SXM_SERVICE_ID;
 * - VENDOR: VENDOR_PRIMARY.
 */
struct ProgramSelector final {
    uint32_t programType __attribute__ ((aligned(4)));
    ::android::hardware::broadcastradio::V1_1::ProgramIdentifier primaryId __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_1::ProgramIdentifier> secondaryIds __attribute__ ((aligned(8)));
    /**
     * Opaque vendor-specific identifiers, to be passed to front-end
     * without changes.
     *
     * The order is meaningful, ie. the first element may be defined as
     * frequency, second as the subchannel etc.
     *
     * The vector is not serialized (either locally or to the cloud),
     * unless it's a VENDOR program type.
     */
    ::android::hardware::hidl_vec<uint64_t> vendorIds __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramSelector, programType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramSelector, primaryId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramSelector, secondaryIds) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramSelector, vendorIds) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_1::ProgramSelector) == 56, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_1::ProgramSelector) == 8, "wrong alignment");

/**
 * Radio program information. Returned by the HAL with event RADIO_EVENT_TUNED.
 * Contains information on currently tuned channel.
 */
struct ProgramInfo final {
    ::android::hardware::broadcastradio::V1_0::ProgramInfo base __attribute__ ((aligned(8)));
    ::android::hardware::broadcastradio::V1_1::ProgramSelector selector __attribute__ ((aligned(8)));
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_1::ProgramInfoFlags> flags __attribute__ ((aligned(4)));
    /**
     * Vendor-specific information.
     *
     * It may be used for extra features, not supported by the platform,
     * for example: paid-service=true; bitrate=320kbps.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_1::VendorKeyValue> vendorInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramInfo, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramInfo, selector) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramInfo, flags) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_1::ProgramInfo, vendorInfo) == 96, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_1::ProgramInfo) == 112, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_1::ProgramInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_1::ProgramListResult o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_1::ProgramListResult o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_1::ProgramListResult lhs, const ::android::hardware::broadcastradio::V1_1::ProgramListResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V1_1::ProgramListResult rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_1::ProgramListResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_1::ProgramListResult lhs, const ::android::hardware::broadcastradio::V1_1::ProgramListResult rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V1_1::ProgramListResult rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_1::ProgramListResult lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V1_1::ProgramListResult e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V1_1::ProgramListResult e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::Properties& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::Properties& o, ::std::ostream*);
// operator== and operator!= are not generated for Properties

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_1::Modulation o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_1::Modulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::Modulation lhs, const ::android::hardware::broadcastradio::V1_1::Modulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::Modulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::Modulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::Modulation lhs, const ::android::hardware::broadcastradio::V1_1::Modulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::Modulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::Modulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::Modulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::Modulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_1::ProgramType o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_1::ProgramType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::ProgramType lhs, const ::android::hardware::broadcastradio::V1_1::ProgramType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::ProgramType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::ProgramType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::ProgramType lhs, const ::android::hardware::broadcastradio::V1_1::ProgramType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::ProgramType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::ProgramType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::ProgramType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::ProgramType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_1::IdentifierType o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_1::IdentifierType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::IdentifierType lhs, const ::android::hardware::broadcastradio::V1_1::IdentifierType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::IdentifierType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_1::IdentifierType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::IdentifierType lhs, const ::android::hardware::broadcastradio::V1_1::IdentifierType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_1::IdentifierType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_1::IdentifierType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::IdentifierType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_1::IdentifierType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramSelector& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramSelector& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfo& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_1::ProgramListResult>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_1::ProgramListResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::OK) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::OK;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_INITIALIZED) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_STATE) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_STATE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::TIMEOUT) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::TIMEOUT;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_READY) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_READY)) {
        os += (first ? "" : " | ");
        os += "NOT_READY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_READY;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_STARTED) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_STARTED)) {
        os += (first ? "" : " | ");
        os += "NOT_STARTED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_STARTED;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramListResult::UNAVAILABLE) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_1::ProgramListResult::UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramListResult::UNAVAILABLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_1::ProgramListResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::OK) {
        return "OK";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_INITIALIZED) {
        return "NOT_INITIALIZED";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_READY) {
        return "NOT_READY";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_STARTED) {
        return "NOT_STARTED";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramListResult::UNAVAILABLE) {
        return "UNAVAILABLE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_1::ProgramListResult o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_1::ProgramInfoFlags>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_1::ProgramInfoFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::LIVE) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::LIVE)) {
        os += (first ? "" : " | ");
        os += "LIVE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::LIVE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::MUTED) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::MUTED)) {
        os += (first ? "" : " | ");
        os += "MUTED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::MUTED;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_PROGRAM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_PROGRAM)) {
        os += (first ? "" : " | ");
        os += "TRAFFIC_PROGRAM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_PROGRAM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT)) {
        os += (first ? "" : " | ");
        os += "TRAFFIC_ANNOUNCEMENT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::LIVE) {
        return "LIVE";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::MUTED) {
        return "MUTED";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_PROGRAM) {
        return "TRAFFIC_PROGRAM";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT) {
        return "TRAFFIC_ANNOUNCEMENT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_1::ProgramInfoFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V1_1::VendorKeyValue& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::Properties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.base);
    os += ", .supportsBackgroundScanning = ";
    os += ::android::hardware::toString(o.supportsBackgroundScanning);
    os += ", .supportedProgramTypes = ";
    os += ::android::hardware::toString(o.supportedProgramTypes);
    os += ", .supportedIdentifierTypes = ";
    os += ::android::hardware::toString(o.supportedIdentifierTypes);
    os += ", .vendorInfo = ";
    os += ::android::hardware::toString(o.vendorInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::Properties& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Properties

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_1::Modulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_1::Modulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_1::Modulation::AM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::Modulation::AM)) {
        os += (first ? "" : " | ");
        os += "AM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::Modulation::AM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::Modulation::FM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::Modulation::FM)) {
        os += (first ? "" : " | ");
        os += "FM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::Modulation::FM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_1::Modulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_1::Modulation::AM) {
        return "AM";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::Modulation::FM) {
        return "FM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_1::Modulation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_1::ProgramType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_1::ProgramType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::AM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::AM)) {
        os += (first ? "" : " | ");
        os += "AM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::AM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::FM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::FM)) {
        os += (first ? "" : " | ");
        os += "FM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::FM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::AM_HD) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::AM_HD)) {
        os += (first ? "" : " | ");
        os += "AM_HD";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::AM_HD;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::FM_HD) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::FM_HD)) {
        os += (first ? "" : " | ");
        os += "FM_HD";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::FM_HD;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::DAB) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::DAB)) {
        os += (first ? "" : " | ");
        os += "DAB";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::DAB;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::DRMO) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::DRMO)) {
        os += (first ? "" : " | ");
        os += "DRMO";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::DRMO;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::SXM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::SXM)) {
        os += (first ? "" : " | ");
        os += "SXM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::SXM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_START) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_START)) {
        os += (first ? "" : " | ");
        os += "VENDOR_START";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_START;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_END) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_END)) {
        os += (first ? "" : " | ");
        os += "VENDOR_END";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_END;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_1::ProgramType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::AM) {
        return "AM";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::FM) {
        return "FM";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::AM_HD) {
        return "AM_HD";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::FM_HD) {
        return "FM_HD";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::DAB) {
        return "DAB";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::DRMO) {
        return "DRMO";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::SXM) {
        return "SXM";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_START) {
        return "VENDOR_START";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_END) {
        return "VENDOR_END";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_1::ProgramType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_1::IdentifierType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_1::IdentifierType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::AMFM_FREQUENCY) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::AMFM_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "AMFM_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::AMFM_FREQUENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::RDS_PI) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::RDS_PI)) {
        os += (first ? "" : " | ");
        os += "RDS_PI";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::RDS_PI;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_STATION_ID_EXT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::HD_STATION_ID_EXT)) {
        os += (first ? "" : " | ");
        os += "HD_STATION_ID_EXT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_STATION_ID_EXT;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_SUBCHANNEL) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::HD_SUBCHANNEL)) {
        os += (first ? "" : " | ");
        os += "HD_SUBCHANNEL";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_SUBCHANNEL;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SIDECC) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SIDECC)) {
        os += (first ? "" : " | ");
        os += "DAB_SIDECC";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SIDECC;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_ENSEMBLE) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_ENSEMBLE)) {
        os += (first ? "" : " | ");
        os += "DAB_ENSEMBLE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_ENSEMBLE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SCID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SCID)) {
        os += (first ? "" : " | ");
        os += "DAB_SCID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SCID;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_FREQUENCY) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "DAB_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_FREQUENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_SERVICE_ID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_SERVICE_ID)) {
        os += (first ? "" : " | ");
        os += "DRMO_SERVICE_ID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_SERVICE_ID;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_FREQUENCY) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "DRMO_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_FREQUENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_MODULATION) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_MODULATION)) {
        os += (first ? "" : " | ");
        os += "DRMO_MODULATION";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_MODULATION;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_SERVICE_ID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_SERVICE_ID)) {
        os += (first ? "" : " | ");
        os += "SXM_SERVICE_ID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_SERVICE_ID;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_CHANNEL) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "SXM_CHANNEL";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_CHANNEL;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_START) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_START)) {
        os += (first ? "" : " | ");
        os += "VENDOR_PRIMARY_START";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_START;
    }
    if ((o & ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_END) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_END)) {
        os += (first ? "" : " | ");
        os += "VENDOR_PRIMARY_END";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_END;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_1::IdentifierType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::AMFM_FREQUENCY) {
        return "AMFM_FREQUENCY";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::RDS_PI) {
        return "RDS_PI";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_STATION_ID_EXT) {
        return "HD_STATION_ID_EXT";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_SUBCHANNEL) {
        return "HD_SUBCHANNEL";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SIDECC) {
        return "DAB_SIDECC";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_ENSEMBLE) {
        return "DAB_ENSEMBLE";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SCID) {
        return "DAB_SCID";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_FREQUENCY) {
        return "DAB_FREQUENCY";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_SERVICE_ID) {
        return "DRMO_SERVICE_ID";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_FREQUENCY) {
        return "DRMO_FREQUENCY";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_MODULATION) {
        return "DRMO_MODULATION";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_SERVICE_ID) {
        return "SXM_SERVICE_ID";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_CHANNEL) {
        return "SXM_CHANNEL";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_START) {
        return "VENDOR_PRIMARY_START";
    }
    if (o == ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_END) {
        return "VENDOR_PRIMARY_END";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_1::IdentifierType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::toString(o.type);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramIdentifier& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".programType = ";
    os += ::android::hardware::toString(o.programType);
    os += ", .primaryId = ";
    os += ::android::hardware::broadcastradio::V1_1::toString(o.primaryId);
    os += ", .secondaryIds = ";
    os += ::android::hardware::toString(o.secondaryIds);
    os += ", .vendorIds = ";
    os += ::android::hardware::toString(o.vendorIds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramSelector& rhs) {
    if (lhs.programType != rhs.programType) {
        return false;
    }
    if (lhs.primaryId != rhs.primaryId) {
        return false;
    }
    if (lhs.secondaryIds != rhs.secondaryIds) {
        return false;
    }
    if (lhs.vendorIds != rhs.vendorIds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramSelector& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.base);
    os += ", .selector = ";
    os += ::android::hardware::broadcastradio::V1_1::toString(o.selector);
    os += ", .flags = ";
    os += ::android::hardware::broadcastradio::V1_1::toString<::android::hardware::broadcastradio::V1_1::ProgramInfoFlags>(o.flags);
    os += ", .vendorInfo = ";
    os += ::android::hardware::toString(o.vendorInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfo& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.selector != rhs.selector) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.vendorInfo != rhs.vendorInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_1::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_1::ProgramInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_1
}  // namespace broadcastradio
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_1::ProgramListResult, 8> hidl_enum_values<::android::hardware::broadcastradio::V1_1::ProgramListResult> = {
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::OK,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_INITIALIZED,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_ARGUMENTS,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::INVALID_STATE,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::TIMEOUT,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_READY,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::NOT_STARTED,
    ::android::hardware::broadcastradio::V1_1::ProgramListResult::UNAVAILABLE,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_1::ProgramInfoFlags, 4> hidl_enum_values<::android::hardware::broadcastradio::V1_1::ProgramInfoFlags> = {
    ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::LIVE,
    ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::MUTED,
    ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_PROGRAM,
    ::android::hardware::broadcastradio::V1_1::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_1::Modulation, 2> hidl_enum_values<::android::hardware::broadcastradio::V1_1::Modulation> = {
    ::android::hardware::broadcastradio::V1_1::Modulation::AM,
    ::android::hardware::broadcastradio::V1_1::Modulation::FM,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_1::ProgramType, 9> hidl_enum_values<::android::hardware::broadcastradio::V1_1::ProgramType> = {
    ::android::hardware::broadcastradio::V1_1::ProgramType::AM,
    ::android::hardware::broadcastradio::V1_1::ProgramType::FM,
    ::android::hardware::broadcastradio::V1_1::ProgramType::AM_HD,
    ::android::hardware::broadcastradio::V1_1::ProgramType::FM_HD,
    ::android::hardware::broadcastradio::V1_1::ProgramType::DAB,
    ::android::hardware::broadcastradio::V1_1::ProgramType::DRMO,
    ::android::hardware::broadcastradio::V1_1::ProgramType::SXM,
    ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_START,
    ::android::hardware::broadcastradio::V1_1::ProgramType::VENDOR_END,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_1::IdentifierType, 15> hidl_enum_values<::android::hardware::broadcastradio::V1_1::IdentifierType> = {
    ::android::hardware::broadcastradio::V1_1::IdentifierType::AMFM_FREQUENCY,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::RDS_PI,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_STATION_ID_EXT,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::HD_SUBCHANNEL,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SIDECC,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_ENSEMBLE,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_SCID,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DAB_FREQUENCY,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_SERVICE_ID,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_FREQUENCY,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::DRMO_MODULATION,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_SERVICE_ID,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::SXM_CHANNEL,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_START,
    ::android::hardware::broadcastradio::V1_1::IdentifierType::VENDOR_PRIMARY_END,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_TYPES_H
