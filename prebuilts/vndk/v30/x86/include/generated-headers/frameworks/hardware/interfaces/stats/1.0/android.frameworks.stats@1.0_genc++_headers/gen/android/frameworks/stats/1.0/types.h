#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace stats {
namespace V1_0 {

// Forward declaration for forward reference support:
struct SpeakerImpedance;
struct HardwareFailed;
struct PhysicalDropDetected;
struct ChargeCycles;
struct BatteryHealthSnapshotArgs;
struct SlowIo;
struct BatteryCausedShutdown;
struct UsbPortOverheatEvent;
struct SpeechDspStat;
struct VendorAtom;

/*
 * Represents the detected speaker impedance value.
 */
struct SpeakerImpedance final {
    /*
     * A vendor-specific value identifying a particular speaker on the device.
     * This value is opaque to the server, it must not try to interpret any
     * meaning to this location.
     */
    int32_t speakerLocation __attribute__ ((aligned(4)));
    /*
     * The detected impedance in milliOhms.
     */
    int32_t milliOhms __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::SpeakerImpedance, speakerLocation) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::SpeakerImpedance, milliOhms) == 4, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::SpeakerImpedance) == 8, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::SpeakerImpedance) == 4, "wrong alignment");

/*
 * Represents a failed hardware report.
 */
struct HardwareFailed final {
    // Forward declaration for forward reference support:
    enum class HardwareType : int32_t;
    enum class HardwareErrorCode : int32_t;

    /*
     * HardwareType for reportHardwareFailed
     */
    enum class HardwareType : int32_t {
        UNKNOWN = 0,
        MICROPHONE = 1,
        CODEC = 2,
        SPEAKER = 3,
        FINGERPRINT = 4,
    };

    /**
     * ErrorCodes for reportHardwareFailed.
     * ErrorCode interpretation depends on the hardwareType parameter.
     * Values prefixed by different hardwareTypes overlap.
     * COMPLETE refers to a complete failure e.g. non-responsive microphone.
     * For most components, unless a more specific failure exists, COMPLETE should be used.
     * UNKNOWN refers to an unexpected or unknown error.
     */
    enum class HardwareErrorCode : int32_t {
        UNKNOWN = 0,
        COMPLETE = 1 /* ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode.UNKNOWN implicitly + 1 */,
        SPEAKER_HIGH_Z = 2 /* ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode.COMPLETE implicitly + 1 */,
        SPEAKER_SHORT = 3 /* ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode.SPEAKER_HIGH_Z implicitly + 1 */,
        FINGERPRINT_SENSOR_BROKEN = 4 /* ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode.SPEAKER_SHORT implicitly + 1 */,
        FINGERPRINT_TOO_MANY_DEAD_PIXELS = 5 /* ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode.FINGERPRINT_SENSOR_BROKEN implicitly + 1 */,
        DEGRADE = 6 /* ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode.FINGERPRINT_TOO_MANY_DEAD_PIXELS implicitly + 1 */,
    };

    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType hardwareType __attribute__ ((aligned(4)));
    /**
     * hardware_location allows vendors to differentiate between multiple
     * instances of the same hardware_type. The specific locations are vendor
     * defined integers, referring to board-specific numbering schemes.
     */
    int32_t hardwareLocation __attribute__ ((aligned(4)));
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode errorCode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::HardwareFailed, hardwareType) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::HardwareFailed, hardwareLocation) == 4, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::HardwareFailed, errorCode) == 8, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::HardwareFailed) == 12, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::HardwareFailed) == 4, "wrong alignment");

/*
 * Represents a physical drop detected event.
 */
struct PhysicalDropDetected final {
    /*
     * Confidence that the event was actually a drop, 0 -> 100
     */
    uint8_t confidencePctg __attribute__ ((aligned(1)));
    /*
     * Peak acceleration of the drop, in 1/1000s of a g.
     */
    int32_t accelPeak __attribute__ ((aligned(4)));
    /*
     * Duration of freefall in ms.
     */
    int32_t freefallDuration __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::PhysicalDropDetected, confidencePctg) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::PhysicalDropDetected, accelPeak) == 4, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::PhysicalDropDetected, freefallDuration) == 8, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::PhysicalDropDetected) == 12, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::PhysicalDropDetected) == 4, "wrong alignment");

/**
 * Represents bucketed battery charge cycles.
 *
 * Each list of buckets represents the number of times
 * the battery has charged past equal-sized fractions of full capacity.
 * For example, given the vector [12, 11, 10, 9, 8, 7, 6, 5], the battery
 * has charged past 1/8th full 12 times, 2/8ths full 11 times, etc.
 */
struct ChargeCycles final {
    ::android::hardware::hidl_vec<int32_t> cycleBucket __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::ChargeCycles, cycleBucket) == 0, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::ChargeCycles) == 16, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::ChargeCycles) == 8, "wrong alignment");

/**
 * Represents parameters for reportBatteryHealthSnapshot.
 */
struct BatteryHealthSnapshotArgs final {
    // Forward declaration for forward reference support:
    enum class BatterySnapshotType : int32_t;

    /*
     * The instance of this snapshot.
     */
    enum class BatterySnapshotType : int32_t {
        MIN_TEMP = 1,
        MAX_TEMP = 2 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MIN_TEMP implicitly + 1 */,
        MIN_RESISTANCE = 3 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MAX_TEMP implicitly + 1 */,
        MAX_RESISTANCE = 4 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MIN_RESISTANCE implicitly + 1 */,
        MIN_VOLTAGE = 5 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MAX_RESISTANCE implicitly + 1 */,
        MAX_VOLTAGE = 6 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MIN_VOLTAGE implicitly + 1 */,
        MIN_CURRENT = 7 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MAX_VOLTAGE implicitly + 1 */,
        MAX_CURRENT = 8 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MIN_CURRENT implicitly + 1 */,
        MIN_BATT_LEVEL = 9 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MAX_CURRENT implicitly + 1 */,
        MAX_BATT_LEVEL = 10 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MIN_BATT_LEVEL implicitly + 1 */,
        AVG_RESISTANCE = 11 /* ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType.MAX_BATT_LEVEL implicitly + 1 */,
    };

    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType type __attribute__ ((aligned(4)));
    /*
     * Temperature Temperature, in 1/10ths of degree C.
     */
    int32_t temperatureDeciC __attribute__ ((aligned(4)));
    /*
     * Voltage Battery Voltage, in microVolts.
     */
    int32_t voltageMicroV __attribute__ ((aligned(4)));
    /*
     * Current Battery current, in microAmps.
     */
    int32_t currentMicroA __attribute__ ((aligned(4)));
    /*
     * OpenCircuitVoltage Battery Open Circuit Voltage, in microVolts.
     */
    int32_t openCircuitVoltageMicroV __attribute__ ((aligned(4)));
    /*
     * Resistance Battery Resistance, in microOhms.
     */
    int32_t resistanceMicroOhm __attribute__ ((aligned(4)));
    /*
     * Level Battery Level, as % of full.
     */
    int32_t levelPercent __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, type) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, temperatureDeciC) == 4, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, voltageMicroV) == 8, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, currentMicroA) == 12, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, openCircuitVoltageMicroV) == 16, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, resistanceMicroOhm) == 20, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs, levelPercent) == 24, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs) == 28, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs) == 4, "wrong alignment");

/**
 * Represents slow I/O operations on the primary flash storage, aggregated.
 */
struct SlowIo final {
    // Forward declaration for forward reference support:
    enum class IoOperation : int32_t;

    /*
     * Classifications of IO Operations for reportSlowIo.
     */
    enum class IoOperation : int32_t {
        UNKNOWN = 0,
        READ = 1 /* ::android::frameworks::stats::V1_0::SlowIo::IoOperation.UNKNOWN implicitly + 1 */,
        WRITE = 2 /* ::android::frameworks::stats::V1_0::SlowIo::IoOperation.READ implicitly + 1 */,
        UNMAP = 3 /* ::android::frameworks::stats::V1_0::SlowIo::IoOperation.WRITE implicitly + 1 */,
        SYNC = 4 /* ::android::frameworks::stats::V1_0::SlowIo::IoOperation.UNMAP implicitly + 1 */,
    };

    ::android::frameworks::stats::V1_0::SlowIo::IoOperation operation __attribute__ ((aligned(4)));
    /*
     * The number of slow IO operations of this type over 24 hours.
     */
    int32_t count __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::SlowIo, operation) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::SlowIo, count) == 4, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::SlowIo) == 8, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::SlowIo) == 4, "wrong alignment");

/**
 * Represents BatteryCausedShutdown with the last recorded voltage.
 */
struct BatteryCausedShutdown final {
    /*
     * The last recorded battery voltage prior to shutdown.
     */
    int32_t voltageMicroV __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::BatteryCausedShutdown, voltageMicroV) == 0, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::BatteryCausedShutdown) == 4, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::BatteryCausedShutdown) == 4, "wrong alignment");

/**
 * Represents USB port overheat event.
 */
struct UsbPortOverheatEvent final {
    /*
     * Temperature of USB port at USB plug event, in 1/10ths of degree C.
     */
    int32_t plugTemperatureDeciC __attribute__ ((aligned(4)));
    /*
     * Maximum temperature of USB port during overheat event, in 1/10ths of degree C.
     */
    int32_t maxTemperatureDeciC __attribute__ ((aligned(4)));
    /*
     * Time between USB plug event and overheat threshold trip, in seconds.
     */
    int32_t timeToOverheat __attribute__ ((aligned(4)));
    /*
     * Time between overheat threshold trip and hysteresis, in seconds.
     */
    int32_t timeToHysteresis __attribute__ ((aligned(4)));
    /*
     * Time between hysteresis and active mitigation ending, in seconds.
     */
    int32_t timeToInactive __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent, plugTemperatureDeciC) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent, maxTemperatureDeciC) == 4, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent, timeToOverheat) == 8, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent, timeToHysteresis) == 12, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent, timeToInactive) == 16, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent) == 20, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::UsbPortOverheatEvent) == 4, "wrong alignment");

/*
 * Represents the speech DSP stat of total uptime, downtime, crash count and recover count.
 */
struct SpeechDspStat final {
    /*
     * Speech DSP total up time in milliseconds.
     */
    int32_t totalUptimeMillis __attribute__ ((aligned(4)));
    /*
     * Speech DSP total down time in milliseconds.
     */
    int32_t totalDowntimeMillis __attribute__ ((aligned(4)));
    /*
     * Speech DSP total crash count.
     */
    int32_t totalCrashCount __attribute__ ((aligned(4)));
    /*
     * Speech DSP recover mechanism trigger count.
     */
    int32_t totalRecoverCount __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::SpeechDspStat, totalUptimeMillis) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::SpeechDspStat, totalDowntimeMillis) == 4, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::SpeechDspStat, totalCrashCount) == 8, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::SpeechDspStat, totalRecoverCount) == 12, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::SpeechDspStat) == 16, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::SpeechDspStat) == 4, "wrong alignment");

/*
 * Generic vendor atom that allows dynamically allocated atoms to be uploaded
 * through statsd.
 *
 * Here's an example that uses this struct:
 *     VendorAtom atom = {
 *         .atomId  = 100000,
 *         .values  = {2, 70000, 5.2, 4, "a"}
 *     };
 *
 * The resulting LogEvent must have the following fields:
 *     Index    Value
 *     0x1      2
 *     0x2      70000
 *     0x3      5.2
 *     0x4      4
 *     0x5      "a"
 */
struct VendorAtom final {
    // Forward declaration for forward reference support:
    struct Value;

    /*
     * Supported field types for this struct.
     */
    struct Value final {
        enum class hidl_discriminator : uint8_t {
            intValue = 0,  // int32_t
            longValue = 1,  // int64_t
            floatValue = 2,  // float
            stringValue = 3,  // ::android::hardware::hidl_string
        };

        Value();
        ~Value();
        Value(Value&&);
        Value(const Value&);
        Value& operator=(Value&&);
        Value& operator=(const Value&);

        void intValue(int32_t);
        int32_t& intValue();
        int32_t intValue() const;

        void longValue(int64_t);
        int64_t& longValue();
        int64_t longValue() const;

        void floatValue(float);
        float& floatValue();
        float floatValue() const;

        void stringValue(const ::android::hardware::hidl_string&);
        void stringValue(::android::hardware::hidl_string&&);
        ::android::hardware::hidl_string& stringValue();
        const ::android::hardware::hidl_string& stringValue() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::frameworks::stats::V1_0::VendorAtom::Value, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            int32_t intValue __attribute__ ((aligned(4)));
            int64_t longValue __attribute__ ((aligned(8)));
            float floatValue __attribute__ ((aligned(4)));
            ::android::hardware::hidl_string stringValue __attribute__ ((aligned(8)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_union) == 16, "wrong size");
        static_assert(__alignof(::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::frameworks::stats::V1_0::VendorAtom::Value) == 24, "wrong size");
    static_assert(__alignof(::android::frameworks::stats::V1_0::VendorAtom::Value) == 8, "wrong alignment");

    /**
     * Vendor or OEM reverse domain name. Must be less than 50 characters.
     * Ex. "com.google.pixel"
     */
    ::android::hardware::hidl_string reverseDomainName __attribute__ ((aligned(8)));
    /*
     * Atom ID. Must be between 100,000 - 199,999 to indicate non-AOSP field.
     */
    int32_t atomId __attribute__ ((aligned(4)));
    /*
     * Vector of fields in the order that the LogEvent should be filled.
     */
    ::android::hardware::hidl_vec<::android::frameworks::stats::V1_0::VendorAtom::Value> values __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::frameworks::stats::V1_0::VendorAtom, reverseDomainName) == 0, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::VendorAtom, atomId) == 16, "wrong offset");
static_assert(offsetof(::android::frameworks::stats::V1_0::VendorAtom, values) == 24, "wrong offset");
static_assert(sizeof(::android::frameworks::stats::V1_0::VendorAtom) == 40, "wrong size");
static_assert(__alignof(::android::frameworks::stats::V1_0::VendorAtom) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::frameworks::stats::V1_0::SpeakerImpedance& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::SpeakerImpedance& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::SpeakerImpedance& lhs, const ::android::frameworks::stats::V1_0::SpeakerImpedance& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::SpeakerImpedance& lhs, const ::android::frameworks::stats::V1_0::SpeakerImpedance& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType o);
static inline void PrintTo(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode o);
static inline void PrintTo(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::HardwareFailed& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::HardwareFailed& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::HardwareFailed& lhs, const ::android::frameworks::stats::V1_0::HardwareFailed& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::HardwareFailed& lhs, const ::android::frameworks::stats::V1_0::HardwareFailed& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& lhs, const ::android::frameworks::stats::V1_0::PhysicalDropDetected& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& lhs, const ::android::frameworks::stats::V1_0::PhysicalDropDetected& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::ChargeCycles& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::ChargeCycles& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::ChargeCycles& lhs, const ::android::frameworks::stats::V1_0::ChargeCycles& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::ChargeCycles& lhs, const ::android::frameworks::stats::V1_0::ChargeCycles& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType o);
static inline void PrintTo(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::frameworks::stats::V1_0::SlowIo::IoOperation o);
static inline void PrintTo(::android::frameworks::stats::V1_0::SlowIo::IoOperation o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::SlowIo::IoOperation lhs, const ::android::frameworks::stats::V1_0::SlowIo::IoOperation rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::frameworks::stats::V1_0::SlowIo::IoOperation rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::frameworks::stats::V1_0::SlowIo::IoOperation lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::SlowIo::IoOperation lhs, const ::android::frameworks::stats::V1_0::SlowIo::IoOperation rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::frameworks::stats::V1_0::SlowIo::IoOperation rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::frameworks::stats::V1_0::SlowIo::IoOperation lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::frameworks::stats::V1_0::SlowIo::IoOperation e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::frameworks::stats::V1_0::SlowIo::IoOperation e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::SlowIo& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::SlowIo& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::SlowIo& lhs, const ::android::frameworks::stats::V1_0::SlowIo& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::SlowIo& lhs, const ::android::frameworks::stats::V1_0::SlowIo& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& lhs, const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& lhs, const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& lhs, const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& lhs, const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::SpeechDspStat& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::SpeechDspStat& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::SpeechDspStat& lhs, const ::android::frameworks::stats::V1_0::SpeechDspStat& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::SpeechDspStat& lhs, const ::android::frameworks::stats::V1_0::SpeechDspStat& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::VendorAtom::Value& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::VendorAtom::Value& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::VendorAtom::Value& lhs, const ::android::frameworks::stats::V1_0::VendorAtom::Value& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::VendorAtom::Value& lhs, const ::android::frameworks::stats::V1_0::VendorAtom::Value& rhs);

static inline std::string toString(const ::android::frameworks::stats::V1_0::VendorAtom& o);
static inline void PrintTo(const ::android::frameworks::stats::V1_0::VendorAtom& o, ::std::ostream*);
static inline bool operator==(const ::android::frameworks::stats::V1_0::VendorAtom& lhs, const ::android::frameworks::stats::V1_0::VendorAtom& rhs);
static inline bool operator!=(const ::android::frameworks::stats::V1_0::VendorAtom& lhs, const ::android::frameworks::stats::V1_0::VendorAtom& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::frameworks::stats::V1_0::SpeakerImpedance& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".speakerLocation = ";
    os += ::android::hardware::toString(o.speakerLocation);
    os += ", .milliOhms = ";
    os += ::android::hardware::toString(o.milliOhms);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::SpeakerImpedance& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::SpeakerImpedance& lhs, const ::android::frameworks::stats::V1_0::SpeakerImpedance& rhs) {
    if (lhs.speakerLocation != rhs.speakerLocation) {
        return false;
    }
    if (lhs.milliOhms != rhs.milliOhms) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::SpeakerImpedance& lhs, const ::android::frameworks::stats::V1_0::SpeakerImpedance& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::frameworks::stats::V1_0::HardwareFailed::HardwareType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::stats::V1_0::HardwareFailed::HardwareType> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::UNKNOWN) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::UNKNOWN;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::MICROPHONE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::MICROPHONE)) {
        os += (first ? "" : " | ");
        os += "MICROPHONE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::MICROPHONE;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::CODEC) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::CODEC)) {
        os += (first ? "" : " | ");
        os += "CODEC";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::CODEC;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::SPEAKER) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::SPEAKER)) {
        os += (first ? "" : " | ");
        os += "SPEAKER";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::SPEAKER;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::FINGERPRINT) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::FINGERPRINT)) {
        os += (first ? "" : " | ");
        os += "FINGERPRINT";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::FINGERPRINT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::MICROPHONE) {
        return "MICROPHONE";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::CODEC) {
        return "CODEC";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::SPEAKER) {
        return "SPEAKER";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::FINGERPRINT) {
        return "FINGERPRINT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::stats::V1_0::HardwareFailed::HardwareType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::UNKNOWN) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::UNKNOWN;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::COMPLETE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::COMPLETE)) {
        os += (first ? "" : " | ");
        os += "COMPLETE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::COMPLETE;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_HIGH_Z) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_HIGH_Z)) {
        os += (first ? "" : " | ");
        os += "SPEAKER_HIGH_Z";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_HIGH_Z;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_SHORT) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_SHORT)) {
        os += (first ? "" : " | ");
        os += "SPEAKER_SHORT";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_SHORT;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_SENSOR_BROKEN) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_SENSOR_BROKEN)) {
        os += (first ? "" : " | ");
        os += "FINGERPRINT_SENSOR_BROKEN";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_SENSOR_BROKEN;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_TOO_MANY_DEAD_PIXELS) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_TOO_MANY_DEAD_PIXELS)) {
        os += (first ? "" : " | ");
        os += "FINGERPRINT_TOO_MANY_DEAD_PIXELS";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_TOO_MANY_DEAD_PIXELS;
    }
    if ((o & ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::DEGRADE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::DEGRADE)) {
        os += (first ? "" : " | ");
        os += "DEGRADE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::DEGRADE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::COMPLETE) {
        return "COMPLETE";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_HIGH_Z) {
        return "SPEAKER_HIGH_Z";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_SHORT) {
        return "SPEAKER_SHORT";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_SENSOR_BROKEN) {
        return "FINGERPRINT_SENSOR_BROKEN";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_TOO_MANY_DEAD_PIXELS) {
        return "FINGERPRINT_TOO_MANY_DEAD_PIXELS";
    }
    if (o == ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::DEGRADE) {
        return "DEGRADE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::HardwareFailed& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".hardwareType = ";
    os += ::android::frameworks::stats::V1_0::toString(o.hardwareType);
    os += ", .hardwareLocation = ";
    os += ::android::hardware::toString(o.hardwareLocation);
    os += ", .errorCode = ";
    os += ::android::frameworks::stats::V1_0::toString(o.errorCode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::HardwareFailed& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::HardwareFailed& lhs, const ::android::frameworks::stats::V1_0::HardwareFailed& rhs) {
    if (lhs.hardwareType != rhs.hardwareType) {
        return false;
    }
    if (lhs.hardwareLocation != rhs.hardwareLocation) {
        return false;
    }
    if (lhs.errorCode != rhs.errorCode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::HardwareFailed& lhs, const ::android::frameworks::stats::V1_0::HardwareFailed& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".confidencePctg = ";
    os += ::android::hardware::toString(o.confidencePctg);
    os += ", .accelPeak = ";
    os += ::android::hardware::toString(o.accelPeak);
    os += ", .freefallDuration = ";
    os += ::android::hardware::toString(o.freefallDuration);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& lhs, const ::android::frameworks::stats::V1_0::PhysicalDropDetected& rhs) {
    if (lhs.confidencePctg != rhs.confidencePctg) {
        return false;
    }
    if (lhs.accelPeak != rhs.accelPeak) {
        return false;
    }
    if (lhs.freefallDuration != rhs.freefallDuration) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& lhs, const ::android::frameworks::stats::V1_0::PhysicalDropDetected& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::ChargeCycles& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cycleBucket = ";
    os += ::android::hardware::toString(o.cycleBucket);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::ChargeCycles& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::ChargeCycles& lhs, const ::android::frameworks::stats::V1_0::ChargeCycles& rhs) {
    if (lhs.cycleBucket != rhs.cycleBucket) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::ChargeCycles& lhs, const ::android::frameworks::stats::V1_0::ChargeCycles& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_TEMP) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_TEMP)) {
        os += (first ? "" : " | ");
        os += "MIN_TEMP";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_TEMP;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_TEMP) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_TEMP)) {
        os += (first ? "" : " | ");
        os += "MAX_TEMP";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_TEMP;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_RESISTANCE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_RESISTANCE)) {
        os += (first ? "" : " | ");
        os += "MIN_RESISTANCE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_RESISTANCE;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_RESISTANCE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_RESISTANCE)) {
        os += (first ? "" : " | ");
        os += "MAX_RESISTANCE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_RESISTANCE;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_VOLTAGE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "MIN_VOLTAGE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_VOLTAGE;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_VOLTAGE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "MAX_VOLTAGE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_VOLTAGE;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_CURRENT) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_CURRENT)) {
        os += (first ? "" : " | ");
        os += "MIN_CURRENT";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_CURRENT;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_CURRENT) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_CURRENT)) {
        os += (first ? "" : " | ");
        os += "MAX_CURRENT";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_CURRENT;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_BATT_LEVEL) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_BATT_LEVEL)) {
        os += (first ? "" : " | ");
        os += "MIN_BATT_LEVEL";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_BATT_LEVEL;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_BATT_LEVEL) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_BATT_LEVEL)) {
        os += (first ? "" : " | ");
        os += "MAX_BATT_LEVEL";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_BATT_LEVEL;
    }
    if ((o & ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::AVG_RESISTANCE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::AVG_RESISTANCE)) {
        os += (first ? "" : " | ");
        os += "AVG_RESISTANCE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::AVG_RESISTANCE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_TEMP) {
        return "MIN_TEMP";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_TEMP) {
        return "MAX_TEMP";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_RESISTANCE) {
        return "MIN_RESISTANCE";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_RESISTANCE) {
        return "MAX_RESISTANCE";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_VOLTAGE) {
        return "MIN_VOLTAGE";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_VOLTAGE) {
        return "MAX_VOLTAGE";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_CURRENT) {
        return "MIN_CURRENT";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_CURRENT) {
        return "MAX_CURRENT";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_BATT_LEVEL) {
        return "MIN_BATT_LEVEL";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_BATT_LEVEL) {
        return "MAX_BATT_LEVEL";
    }
    if (o == ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::AVG_RESISTANCE) {
        return "AVG_RESISTANCE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::frameworks::stats::V1_0::toString(o.type);
    os += ", .temperatureDeciC = ";
    os += ::android::hardware::toString(o.temperatureDeciC);
    os += ", .voltageMicroV = ";
    os += ::android::hardware::toString(o.voltageMicroV);
    os += ", .currentMicroA = ";
    os += ::android::hardware::toString(o.currentMicroA);
    os += ", .openCircuitVoltageMicroV = ";
    os += ::android::hardware::toString(o.openCircuitVoltageMicroV);
    os += ", .resistanceMicroOhm = ";
    os += ::android::hardware::toString(o.resistanceMicroOhm);
    os += ", .levelPercent = ";
    os += ::android::hardware::toString(o.levelPercent);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.temperatureDeciC != rhs.temperatureDeciC) {
        return false;
    }
    if (lhs.voltageMicroV != rhs.voltageMicroV) {
        return false;
    }
    if (lhs.currentMicroA != rhs.currentMicroA) {
        return false;
    }
    if (lhs.openCircuitVoltageMicroV != rhs.openCircuitVoltageMicroV) {
        return false;
    }
    if (lhs.resistanceMicroOhm != rhs.resistanceMicroOhm) {
        return false;
    }
    if (lhs.levelPercent != rhs.levelPercent) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& lhs, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::frameworks::stats::V1_0::SlowIo::IoOperation>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::frameworks::stats::V1_0::SlowIo::IoOperation> flipped = 0;
    bool first = true;
    if ((o & ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNKNOWN) == static_cast<int32_t>(::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNKNOWN;
    }
    if ((o & ::android::frameworks::stats::V1_0::SlowIo::IoOperation::READ) == static_cast<int32_t>(::android::frameworks::stats::V1_0::SlowIo::IoOperation::READ)) {
        os += (first ? "" : " | ");
        os += "READ";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::SlowIo::IoOperation::READ;
    }
    if ((o & ::android::frameworks::stats::V1_0::SlowIo::IoOperation::WRITE) == static_cast<int32_t>(::android::frameworks::stats::V1_0::SlowIo::IoOperation::WRITE)) {
        os += (first ? "" : " | ");
        os += "WRITE";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::SlowIo::IoOperation::WRITE;
    }
    if ((o & ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNMAP) == static_cast<int32_t>(::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNMAP)) {
        os += (first ? "" : " | ");
        os += "UNMAP";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNMAP;
    }
    if ((o & ::android::frameworks::stats::V1_0::SlowIo::IoOperation::SYNC) == static_cast<int32_t>(::android::frameworks::stats::V1_0::SlowIo::IoOperation::SYNC)) {
        os += (first ? "" : " | ");
        os += "SYNC";
        first = false;
        flipped |= ::android::frameworks::stats::V1_0::SlowIo::IoOperation::SYNC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::frameworks::stats::V1_0::SlowIo::IoOperation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::frameworks::stats::V1_0::SlowIo::IoOperation::READ) {
        return "READ";
    }
    if (o == ::android::frameworks::stats::V1_0::SlowIo::IoOperation::WRITE) {
        return "WRITE";
    }
    if (o == ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNMAP) {
        return "UNMAP";
    }
    if (o == ::android::frameworks::stats::V1_0::SlowIo::IoOperation::SYNC) {
        return "SYNC";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::frameworks::stats::V1_0::SlowIo::IoOperation o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::SlowIo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".operation = ";
    os += ::android::frameworks::stats::V1_0::toString(o.operation);
    os += ", .count = ";
    os += ::android::hardware::toString(o.count);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::SlowIo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::SlowIo& lhs, const ::android::frameworks::stats::V1_0::SlowIo& rhs) {
    if (lhs.operation != rhs.operation) {
        return false;
    }
    if (lhs.count != rhs.count) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::SlowIo& lhs, const ::android::frameworks::stats::V1_0::SlowIo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".voltageMicroV = ";
    os += ::android::hardware::toString(o.voltageMicroV);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& lhs, const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& rhs) {
    if (lhs.voltageMicroV != rhs.voltageMicroV) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& lhs, const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".plugTemperatureDeciC = ";
    os += ::android::hardware::toString(o.plugTemperatureDeciC);
    os += ", .maxTemperatureDeciC = ";
    os += ::android::hardware::toString(o.maxTemperatureDeciC);
    os += ", .timeToOverheat = ";
    os += ::android::hardware::toString(o.timeToOverheat);
    os += ", .timeToHysteresis = ";
    os += ::android::hardware::toString(o.timeToHysteresis);
    os += ", .timeToInactive = ";
    os += ::android::hardware::toString(o.timeToInactive);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& lhs, const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& rhs) {
    if (lhs.plugTemperatureDeciC != rhs.plugTemperatureDeciC) {
        return false;
    }
    if (lhs.maxTemperatureDeciC != rhs.maxTemperatureDeciC) {
        return false;
    }
    if (lhs.timeToOverheat != rhs.timeToOverheat) {
        return false;
    }
    if (lhs.timeToHysteresis != rhs.timeToHysteresis) {
        return false;
    }
    if (lhs.timeToInactive != rhs.timeToInactive) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& lhs, const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::SpeechDspStat& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".totalUptimeMillis = ";
    os += ::android::hardware::toString(o.totalUptimeMillis);
    os += ", .totalDowntimeMillis = ";
    os += ::android::hardware::toString(o.totalDowntimeMillis);
    os += ", .totalCrashCount = ";
    os += ::android::hardware::toString(o.totalCrashCount);
    os += ", .totalRecoverCount = ";
    os += ::android::hardware::toString(o.totalRecoverCount);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::SpeechDspStat& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::SpeechDspStat& lhs, const ::android::frameworks::stats::V1_0::SpeechDspStat& rhs) {
    if (lhs.totalUptimeMillis != rhs.totalUptimeMillis) {
        return false;
    }
    if (lhs.totalDowntimeMillis != rhs.totalDowntimeMillis) {
        return false;
    }
    if (lhs.totalCrashCount != rhs.totalCrashCount) {
        return false;
    }
    if (lhs.totalRecoverCount != rhs.totalRecoverCount) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::SpeechDspStat& lhs, const ::android::frameworks::stats::V1_0::SpeechDspStat& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::VendorAtom::Value& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::intValue: {
            os += ".intValue = ";
            os += toString(o.intValue());
            break;
        }
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::longValue: {
            os += ".longValue = ";
            os += toString(o.longValue());
            break;
        }
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::floatValue: {
            os += ".floatValue = ";
            os += toString(o.floatValue());
            break;
        }
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::stringValue: {
            os += ".stringValue = ";
            os += toString(o.stringValue());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::VendorAtom::Value& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::VendorAtom::Value& lhs, const ::android::frameworks::stats::V1_0::VendorAtom::Value& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::intValue: {
            return (lhs.intValue() == rhs.intValue());
        }
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::longValue: {
            return (lhs.longValue() == rhs.longValue());
        }
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::floatValue: {
            return (lhs.floatValue() == rhs.floatValue());
        }
        case ::android::frameworks::stats::V1_0::VendorAtom::Value::hidl_discriminator::stringValue: {
            return (lhs.stringValue() == rhs.stringValue());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::VendorAtom::Value& lhs, const ::android::frameworks::stats::V1_0::VendorAtom::Value& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::frameworks::stats::V1_0::VendorAtom& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".reverseDomainName = ";
    os += ::android::hardware::toString(o.reverseDomainName);
    os += ", .atomId = ";
    os += ::android::hardware::toString(o.atomId);
    os += ", .values = ";
    os += ::android::hardware::toString(o.values);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::frameworks::stats::V1_0::VendorAtom& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::frameworks::stats::V1_0::VendorAtom& lhs, const ::android::frameworks::stats::V1_0::VendorAtom& rhs) {
    if (lhs.reverseDomainName != rhs.reverseDomainName) {
        return false;
    }
    if (lhs.atomId != rhs.atomId) {
        return false;
    }
    if (lhs.values != rhs.values) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::frameworks::stats::V1_0::VendorAtom& lhs, const ::android::frameworks::stats::V1_0::VendorAtom& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace stats
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::frameworks::stats::V1_0::HardwareFailed::HardwareType, 5> hidl_enum_values<::android::frameworks::stats::V1_0::HardwareFailed::HardwareType> = {
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::UNKNOWN,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::MICROPHONE,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::CODEC,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::SPEAKER,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareType::FINGERPRINT,
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
template<> inline constexpr std::array<::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode, 7> hidl_enum_values<::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode> = {
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::UNKNOWN,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::COMPLETE,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_HIGH_Z,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::SPEAKER_SHORT,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_SENSOR_BROKEN,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::FINGERPRINT_TOO_MANY_DEAD_PIXELS,
    ::android::frameworks::stats::V1_0::HardwareFailed::HardwareErrorCode::DEGRADE,
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
template<> inline constexpr std::array<::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType, 11> hidl_enum_values<::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType> = {
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_TEMP,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_TEMP,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_RESISTANCE,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_RESISTANCE,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_VOLTAGE,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_VOLTAGE,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_CURRENT,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_CURRENT,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MIN_BATT_LEVEL,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::MAX_BATT_LEVEL,
    ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs::BatterySnapshotType::AVG_RESISTANCE,
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
template<> inline constexpr std::array<::android::frameworks::stats::V1_0::SlowIo::IoOperation, 5> hidl_enum_values<::android::frameworks::stats::V1_0::SlowIo::IoOperation> = {
    ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNKNOWN,
    ::android::frameworks::stats::V1_0::SlowIo::IoOperation::READ,
    ::android::frameworks::stats::V1_0::SlowIo::IoOperation::WRITE,
    ::android::frameworks::stats::V1_0::SlowIo::IoOperation::UNMAP,
    ::android::frameworks::stats::V1_0::SlowIo::IoOperation::SYNC,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_TYPES_H
