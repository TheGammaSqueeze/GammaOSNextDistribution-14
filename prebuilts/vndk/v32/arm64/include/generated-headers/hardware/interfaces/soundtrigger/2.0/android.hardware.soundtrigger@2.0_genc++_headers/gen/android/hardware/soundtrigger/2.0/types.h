#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class SoundModelType : int32_t;
enum class RecognitionMode : uint32_t;
struct ConfidenceLevel;
struct PhraseRecognitionExtra;

/**
 * Sound model types modes used in ISoundTriggerHw.SoundModel
 */
enum class SoundModelType : int32_t {
    /**
     * use for unspecified sound model type
     */
    UNKNOWN = -1 /* -1 */,
    /**
     * use for key phrase sound models
     */
    KEYPHRASE = 0,
    /**
     * use for all models other than keyphrase
     */
    GENERIC = 1,
};

typedef int32_t SoundModelHandle;

/**
 * Recognition modes used in ISoundTriggerHw.RecognitionConfig,
 * ISoundTriggerHw.Properties or PhraseRecognitionExtra
 */
enum class RecognitionMode : uint32_t {
    /**
     * simple voice trigger
     */
    VOICE_TRIGGER = 1u /* (1 << 0) */,
    /**
     * trigger only if one user in model identified
     */
    USER_IDENTIFICATION = 2u /* (1 << 1) */,
    /**
     * trigger only if one user in mode authenticated
     */
    USER_AUTHENTICATION = 4u /* (1 << 2) */,
    /**
     * generic sound trigger
     */
    GENERIC_TRIGGER = 8u /* (1 << 3) */,
};

/**
 * Confidence level for each user in structure PhraseRecognitionExtra
 */
struct ConfidenceLevel final {
    /**
     * user ID
     */
    uint32_t userId __attribute__ ((aligned(4)));
    /**
     * confidence level in percent (0 - 100):
     *
     *
     * - min level for recognition configuration
     *
     *
     * - detected level for recognition event
     */
    uint32_t levelPercent __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ConfidenceLevel, userId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ConfidenceLevel, levelPercent) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ConfidenceLevel) == 8, "wrong size");
static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ConfidenceLevel) == 4, "wrong alignment");

/**
 * Specialized recognition event for key phrase detection
 */
struct PhraseRecognitionExtra final {
    /**
     * keyphrase ID
     */
    uint32_t id __attribute__ ((aligned(4)));
    /**
     * recognition modes used for this keyphrase
     */
    uint32_t recognitionModes __attribute__ ((aligned(4)));
    /**
     * confidence level for mode RecognitionMode.VOICE_TRIGGER
     */
    uint32_t confidenceLevel __attribute__ ((aligned(4)));
    /**
     * list of confidence levels per user for
     * RecognitionMode.USER_IDENTIFICATION and
     * RecognitionMode.USER_AUTHENTICATION
     */
    ::android::hardware::hidl_vec<::android::hardware::soundtrigger::V2_0::ConfidenceLevel> levels __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra, recognitionModes) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra, confidenceLevel) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra, levels) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra) == 32, "wrong size");
static_assert(__alignof(::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::soundtrigger::V2_0::SoundModelType o);
static inline void PrintTo(::android::hardware::soundtrigger::V2_0::SoundModelType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::soundtrigger::V2_0::SoundModelType lhs, const ::android::hardware::soundtrigger::V2_0::SoundModelType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::soundtrigger::V2_0::SoundModelType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::soundtrigger::V2_0::SoundModelType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::soundtrigger::V2_0::SoundModelType lhs, const ::android::hardware::soundtrigger::V2_0::SoundModelType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::soundtrigger::V2_0::SoundModelType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::soundtrigger::V2_0::SoundModelType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::soundtrigger::V2_0::SoundModelType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::soundtrigger::V2_0::SoundModelType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::soundtrigger::V2_0::RecognitionMode o);
static inline void PrintTo(::android::hardware::soundtrigger::V2_0::RecognitionMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_0::RecognitionMode lhs, const ::android::hardware::soundtrigger::V2_0::RecognitionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_0::RecognitionMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_0::RecognitionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_0::RecognitionMode lhs, const ::android::hardware::soundtrigger::V2_0::RecognitionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_0::RecognitionMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_0::RecognitionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::soundtrigger::V2_0::RecognitionMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::soundtrigger::V2_0::RecognitionMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& lhs, const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& lhs, const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& lhs, const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& lhs, const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::soundtrigger::V2_0::SoundModelType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_0::SoundModelType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::soundtrigger::V2_0::SoundModelType::UNKNOWN) == static_cast<int32_t>(::android::hardware::soundtrigger::V2_0::SoundModelType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::SoundModelType::UNKNOWN;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::SoundModelType::KEYPHRASE) == static_cast<int32_t>(::android::hardware::soundtrigger::V2_0::SoundModelType::KEYPHRASE)) {
        os += (first ? "" : " | ");
        os += "KEYPHRASE";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::SoundModelType::KEYPHRASE;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::SoundModelType::GENERIC) == static_cast<int32_t>(::android::hardware::soundtrigger::V2_0::SoundModelType::GENERIC)) {
        os += (first ? "" : " | ");
        os += "GENERIC";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::SoundModelType::GENERIC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::soundtrigger::V2_0::SoundModelType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::soundtrigger::V2_0::SoundModelType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::SoundModelType::KEYPHRASE) {
        return "KEYPHRASE";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::SoundModelType::GENERIC) {
        return "GENERIC";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::soundtrigger::V2_0::SoundModelType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::soundtrigger::V2_0::RecognitionMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_0::RecognitionMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::soundtrigger::V2_0::RecognitionMode::VOICE_TRIGGER) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::RecognitionMode::VOICE_TRIGGER)) {
        os += (first ? "" : " | ");
        os += "VOICE_TRIGGER";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::RecognitionMode::VOICE_TRIGGER;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_IDENTIFICATION) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_IDENTIFICATION)) {
        os += (first ? "" : " | ");
        os += "USER_IDENTIFICATION";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_IDENTIFICATION;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_AUTHENTICATION) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_AUTHENTICATION)) {
        os += (first ? "" : " | ");
        os += "USER_AUTHENTICATION";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_AUTHENTICATION;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::RecognitionMode::GENERIC_TRIGGER) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::RecognitionMode::GENERIC_TRIGGER)) {
        os += (first ? "" : " | ");
        os += "GENERIC_TRIGGER";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::RecognitionMode::GENERIC_TRIGGER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::soundtrigger::V2_0::RecognitionMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::soundtrigger::V2_0::RecognitionMode::VOICE_TRIGGER) {
        return "VOICE_TRIGGER";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_IDENTIFICATION) {
        return "USER_IDENTIFICATION";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_AUTHENTICATION) {
        return "USER_AUTHENTICATION";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::RecognitionMode::GENERIC_TRIGGER) {
        return "GENERIC_TRIGGER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::soundtrigger::V2_0::RecognitionMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".userId = ";
    os += ::android::hardware::toString(o.userId);
    os += ", .levelPercent = ";
    os += ::android::hardware::toString(o.levelPercent);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& lhs, const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& rhs) {
    if (lhs.userId != rhs.userId) {
        return false;
    }
    if (lhs.levelPercent != rhs.levelPercent) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& lhs, const ::android::hardware::soundtrigger::V2_0::ConfidenceLevel& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .recognitionModes = ";
    os += ::android::hardware::toString(o.recognitionModes);
    os += ", .confidenceLevel = ";
    os += ::android::hardware::toString(o.confidenceLevel);
    os += ", .levels = ";
    os += ::android::hardware::toString(o.levels);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& lhs, const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.recognitionModes != rhs.recognitionModes) {
        return false;
    }
    if (lhs.confidenceLevel != rhs.confidenceLevel) {
        return false;
    }
    if (lhs.levels != rhs.levels) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& lhs, const ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
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
template<> inline constexpr std::array<::android::hardware::soundtrigger::V2_0::SoundModelType, 3> hidl_enum_values<::android::hardware::soundtrigger::V2_0::SoundModelType> = {
    ::android::hardware::soundtrigger::V2_0::SoundModelType::UNKNOWN,
    ::android::hardware::soundtrigger::V2_0::SoundModelType::KEYPHRASE,
    ::android::hardware::soundtrigger::V2_0::SoundModelType::GENERIC,
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
template<> inline constexpr std::array<::android::hardware::soundtrigger::V2_0::RecognitionMode, 4> hidl_enum_values<::android::hardware::soundtrigger::V2_0::RecognitionMode> = {
    ::android::hardware::soundtrigger::V2_0::RecognitionMode::VOICE_TRIGGER,
    ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_IDENTIFICATION,
    ::android::hardware::soundtrigger::V2_0::RecognitionMode::USER_AUTHENTICATION,
    ::android::hardware::soundtrigger::V2_0::RecognitionMode::GENERIC_TRIGGER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_TYPES_H
