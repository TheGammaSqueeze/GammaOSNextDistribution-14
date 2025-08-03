#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_3_TYPES_H

#include <android/hardware/soundtrigger/2.0/ISoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.1/ISoundTriggerHw.h>
#include <android/hidl/safe_union/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_3 {

// Forward declaration for forward reference support:
enum class AudioCapabilities : uint32_t;
struct Properties;
struct RecognitionConfig;
enum class ModelParameter : int32_t;
struct ModelParameterRange;
struct OptionalModelParameterRange;

// Order of inner types was changed for forward reference support.

/**
 * AudioCapabilities supported by the implemented HAL
 * driver.
 */
enum class AudioCapabilities : uint32_t {
    /**
     * If set the underlying module supports AEC.
     */
    ECHO_CANCELLATION = 1u /* 1 << 0 */,
    /**
     * If set, the underlying module supports noise suppression.
     */
    NOISE_SUPPRESSION = 2u /* 1 << 1 */,
};

/**
 * Extended implementation properties providing verbose implementation
 * details.
 */
struct Properties final {
    ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties base __attribute__ ((aligned(8)));
    /**
     * String naming the architecture used for running the supported models.
     * (eg. DSP architecture)
     */
    ::android::hardware::hidl_string supportedModelArch __attribute__ ((aligned(8)));
    /**
     * Bit field encoding of the AudioCapabilities
     * supported by the firmware.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_3::AudioCapabilities> audioCapabilities __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::soundtrigger::V2_3::Properties, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_3::Properties, supportedModelArch) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_3::Properties, audioCapabilities) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::soundtrigger::V2_3::Properties) == 112, "wrong size");
static_assert(__alignof(::android::hardware::soundtrigger::V2_3::Properties) == 8, "wrong alignment");

/**
 * Configuration for sound trigger capture session passed to
 * startRecognition_2_1() method.
 */
struct RecognitionConfig final {
    ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::RecognitionConfig base __attribute__ ((aligned(8)));
    /**
     * Bit field encoding of the AudioCapabilities
     * supported by the firmware.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_3::AudioCapabilities> audioCapabilities __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::soundtrigger::V2_3::RecognitionConfig, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_3::RecognitionConfig, audioCapabilities) == 88, "wrong offset");
static_assert(sizeof(::android::hardware::soundtrigger::V2_3::RecognitionConfig) == 96, "wrong size");
static_assert(__alignof(::android::hardware::soundtrigger::V2_3::RecognitionConfig) == 8, "wrong alignment");

/**
 * Model specific parameters to be used with parameter set and get APIs
 */
enum class ModelParameter : int32_t {
    /**
     * Placeholder for invalid model parameter used for returning error or
     * passing an invalid value.
     */
    INVALID = -1 /* -1 */,
    /**
     * Controls the sensitivity threshold adjustment factor for a given model.
     * Negative value corresponds to less sensitive model (high threshold) and
     * a positive value corresponds to a more sensitive model (low threshold).
     * Default value is 0.
     */
    THRESHOLD_FACTOR = 0,
};

/**
 * Model specific range support for a given parameter
 */
struct ModelParameterRange final {
    /**
     * start of supported value range inclusive
     */
    int32_t start __attribute__ ((aligned(4)));
    /**
     * end of supported value range inclusive
     */
    int32_t end __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::soundtrigger::V2_3::ModelParameterRange, start) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_3::ModelParameterRange, end) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::soundtrigger::V2_3::ModelParameterRange) == 8, "wrong size");
static_assert(__alignof(::android::hardware::soundtrigger::V2_3::ModelParameterRange) == 4, "wrong alignment");

/**
 * Safe union wrapping ModelParameterRange.
 * Monostate is used to indicate there is no valid range
 */
struct OptionalModelParameterRange final {
    enum class hidl_discriminator : uint8_t {
        noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
        range = 1,  // ::android::hardware::soundtrigger::V2_3::ModelParameterRange
    };

    OptionalModelParameterRange();
    ~OptionalModelParameterRange();
    OptionalModelParameterRange(OptionalModelParameterRange&&);
    OptionalModelParameterRange(const OptionalModelParameterRange&);
    OptionalModelParameterRange& operator=(OptionalModelParameterRange&&);
    OptionalModelParameterRange& operator=(const OptionalModelParameterRange&);

    void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
    void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
    ::android::hidl::safe_union::V1_0::Monostate& noinit();
    const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

    void range(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange&);
    void range(::android::hardware::soundtrigger::V2_3::ModelParameterRange&&);
    ::android::hardware::soundtrigger::V2_3::ModelParameterRange& range();
    const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& range() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
        ::android::hardware::soundtrigger::V2_3::ModelParameterRange range __attribute__ ((aligned(4)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_union) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_union) == 4, "wrong alignment");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange) == 12, "wrong size");
static_assert(__alignof(::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange) == 4, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::soundtrigger::V2_3::AudioCapabilities o);
static inline void PrintTo(::android::hardware::soundtrigger::V2_3::AudioCapabilities o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_3::AudioCapabilities lhs, const ::android::hardware::soundtrigger::V2_3::AudioCapabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_3::AudioCapabilities rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_3::AudioCapabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_3::AudioCapabilities lhs, const ::android::hardware::soundtrigger::V2_3::AudioCapabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_3::AudioCapabilities rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_3::AudioCapabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::soundtrigger::V2_3::AudioCapabilities e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::soundtrigger::V2_3::AudioCapabilities e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::Properties& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::Properties& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_3::Properties& lhs, const ::android::hardware::soundtrigger::V2_3::Properties& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_3::Properties& lhs, const ::android::hardware::soundtrigger::V2_3::Properties& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::RecognitionConfig& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::RecognitionConfig& o, ::std::ostream*);
// operator== and operator!= are not generated for RecognitionConfig

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::soundtrigger::V2_3::ModelParameter o);
static inline void PrintTo(::android::hardware::soundtrigger::V2_3::ModelParameter o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::soundtrigger::V2_3::ModelParameter lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameter rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameter rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::soundtrigger::V2_3::ModelParameter lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::soundtrigger::V2_3::ModelParameter lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameter rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameter rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::soundtrigger::V2_3::ModelParameter lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::soundtrigger::V2_3::ModelParameter e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::soundtrigger::V2_3::ModelParameter e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::soundtrigger::V2_3::AudioCapabilities>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_3::AudioCapabilities> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::soundtrigger::V2_3::AudioCapabilities::ECHO_CANCELLATION) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_3::AudioCapabilities::ECHO_CANCELLATION)) {
        os += (first ? "" : " | ");
        os += "ECHO_CANCELLATION";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_3::AudioCapabilities::ECHO_CANCELLATION;
    }
    if ((o & ::android::hardware::soundtrigger::V2_3::AudioCapabilities::NOISE_SUPPRESSION) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_3::AudioCapabilities::NOISE_SUPPRESSION)) {
        os += (first ? "" : " | ");
        os += "NOISE_SUPPRESSION";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_3::AudioCapabilities::NOISE_SUPPRESSION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::soundtrigger::V2_3::AudioCapabilities o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::soundtrigger::V2_3::AudioCapabilities::ECHO_CANCELLATION) {
        return "ECHO_CANCELLATION";
    }
    if (o == ::android::hardware::soundtrigger::V2_3::AudioCapabilities::NOISE_SUPPRESSION) {
        return "NOISE_SUPPRESSION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::soundtrigger::V2_3::AudioCapabilities o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::Properties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.base);
    os += ", .supportedModelArch = ";
    os += ::android::hardware::toString(o.supportedModelArch);
    os += ", .audioCapabilities = ";
    os += ::android::hardware::soundtrigger::V2_3::toString<::android::hardware::soundtrigger::V2_3::AudioCapabilities>(o.audioCapabilities);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::Properties& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_3::Properties& lhs, const ::android::hardware::soundtrigger::V2_3::Properties& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.supportedModelArch != rhs.supportedModelArch) {
        return false;
    }
    if (lhs.audioCapabilities != rhs.audioCapabilities) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_3::Properties& lhs, const ::android::hardware::soundtrigger::V2_3::Properties& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::RecognitionConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::soundtrigger::V2_1::toString(o.base);
    os += ", .audioCapabilities = ";
    os += ::android::hardware::soundtrigger::V2_3::toString<::android::hardware::soundtrigger::V2_3::AudioCapabilities>(o.audioCapabilities);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::RecognitionConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for RecognitionConfig

template<>
inline std::string toString<::android::hardware::soundtrigger::V2_3::ModelParameter>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_3::ModelParameter> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::soundtrigger::V2_3::ModelParameter::INVALID) == static_cast<int32_t>(::android::hardware::soundtrigger::V2_3::ModelParameter::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_3::ModelParameter::INVALID;
    }
    if ((o & ::android::hardware::soundtrigger::V2_3::ModelParameter::THRESHOLD_FACTOR) == static_cast<int32_t>(::android::hardware::soundtrigger::V2_3::ModelParameter::THRESHOLD_FACTOR)) {
        os += (first ? "" : " | ");
        os += "THRESHOLD_FACTOR";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_3::ModelParameter::THRESHOLD_FACTOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::soundtrigger::V2_3::ModelParameter o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::soundtrigger::V2_3::ModelParameter::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::soundtrigger::V2_3::ModelParameter::THRESHOLD_FACTOR) {
        return "THRESHOLD_FACTOR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::soundtrigger::V2_3::ModelParameter o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".start = ";
    os += ::android::hardware::toString(o.start);
    os += ", .end = ";
    os += ::android::hardware::toString(o.end);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& rhs) {
    if (lhs.start != rhs.start) {
        return false;
    }
    if (lhs.end != rhs.end) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::ModelParameterRange& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_discriminator::range: {
            os += ".range = ";
            os += toString(o.range());
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

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange::hidl_discriminator::range: {
            return (lhs.range() == rhs.range());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& lhs, const ::android::hardware::soundtrigger::V2_3::OptionalModelParameterRange& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_3
}  // namespace soundtrigger
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
template<> inline constexpr std::array<::android::hardware::soundtrigger::V2_3::AudioCapabilities, 2> hidl_enum_values<::android::hardware::soundtrigger::V2_3::AudioCapabilities> = {
    ::android::hardware::soundtrigger::V2_3::AudioCapabilities::ECHO_CANCELLATION,
    ::android::hardware::soundtrigger::V2_3::AudioCapabilities::NOISE_SUPPRESSION,
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
template<> inline constexpr std::array<::android::hardware::soundtrigger::V2_3::ModelParameter, 2> hidl_enum_values<::android::hardware::soundtrigger::V2_3::ModelParameter> = {
    ::android::hardware::soundtrigger::V2_3::ModelParameter::INVALID,
    ::android::hardware::soundtrigger::V2_3::ModelParameter::THRESHOLD_FACTOR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_3_TYPES_H
