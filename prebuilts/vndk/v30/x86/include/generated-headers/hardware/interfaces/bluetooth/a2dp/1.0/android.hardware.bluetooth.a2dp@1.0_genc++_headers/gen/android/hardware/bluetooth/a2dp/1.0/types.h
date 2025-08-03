#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace a2dp {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint8_t;
enum class CodecType : uint32_t;
enum class SampleRate : uint32_t;
enum class BitsPerSample : uint8_t;
enum class ChannelMode : uint8_t;
struct CodecConfiguration;

enum class Status : uint8_t {
    SUCCESS = 0,
    FAILURE = 1 /* ::android::hardware::bluetooth::a2dp::V1_0::Status.SUCCESS implicitly + 1 */,
    /**
     * codec configuration not supported by the audio platform
     */
    UNSUPPORTED_CODEC_CONFIGURATION = 2 /* ::android::hardware::bluetooth::a2dp::V1_0::Status.FAILURE implicitly + 1 */,
    /**
     * operation is pending
     */
    PENDING = 3 /* ::android::hardware::bluetooth::a2dp::V1_0::Status.UNSUPPORTED_CODEC_CONFIGURATION implicitly + 1 */,
};

enum class CodecType : uint32_t {
    UNKNOWN = 0u /* 0x00 */,
    SBC = 1u /* 0x01 */,
    AAC = 2u /* 0x02 */,
    APTX = 4u /* 0x04 */,
    APTX_HD = 8u /* 0x08 */,
    LDAC = 16u /* 0x10 */,
};

enum class SampleRate : uint32_t {
    RATE_UNKNOWN = 0u /* 0x00 */,
    RATE_44100 = 1u /* 0x01 */,
    RATE_48000 = 2u /* 0x02 */,
    RATE_88200 = 4u /* 0x04 */,
    RATE_96000 = 8u /* 0x08 */,
    RATE_176400 = 16u /* 0x10 */,
    RATE_192000 = 32u /* 0x20 */,
};

enum class BitsPerSample : uint8_t {
    BITS_UNKNOWN = 0 /* 0x00 */,
    BITS_16 = 1 /* 0x01 */,
    BITS_24 = 2 /* 0x02 */,
    BITS_32 = 4 /* 0x04 */,
};

enum class ChannelMode : uint8_t {
    UNKNOWN = 0 /* 0x00 */,
    MONO = 1 /* 0x01 */,
    STEREO = 2 /* 0x02 */,
};

struct CodecConfiguration final {
    // Forward declaration for forward reference support:
    union CodecSpecific;

    union CodecSpecific final {
        // Forward declaration for forward reference support:
        struct SbcData;
        struct LdacData;

        struct SbcData final {
            /**
             * Block length: 4 bits | Subbands: 2 bits | Allocation Method: 2 bits
             */
            uint8_t codecParameters __attribute__ ((aligned(1)));
            /**
             * Minimum bitpool value
             */
            uint8_t minBitpool __attribute__ ((aligned(1)));
            /**
             * Maximum bitpool value
             */
            uint8_t maxBitpool __attribute__ ((aligned(1)));
        };

        static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData, codecParameters) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData, minBitpool) == 1, "wrong offset");
        static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData, maxBitpool) == 2, "wrong offset");
        static_assert(sizeof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData) == 3, "wrong size");
        static_assert(__alignof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData) == 1, "wrong alignment");

        struct LdacData final {
            /**
             * LDAC bitrate index value:
             * 0x00 - High
             * 0x01 - Mid
             * 0x02 - Low
             * 0x7F - ABR (Adaptive Bit Rate)
             */
            uint8_t bitrateIndex __attribute__ ((aligned(1)));
        };

        static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData, bitrateIndex) == 0, "wrong offset");
        static_assert(sizeof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData) == 1, "wrong alignment");

        /**
         * SBC Codec specific information
         * Refer to SBC Codec specific information elements in A2DP v1.3
         * Profile Specification.
         */
        ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData sbcData __attribute__ ((aligned(1)));
        ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData ldacData __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific, sbcData) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific, ldacData) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific) == 3, "wrong size");
    static_assert(__alignof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific) == 1, "wrong alignment");

    /**
     * Bluetooth A2DP codec
     */
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType codecType __attribute__ ((aligned(4)));
    /**
     * Sampling rate for encoder
     */
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate sampleRate __attribute__ ((aligned(4)));
    /**
     * Bits per sample for encoder
     */
    ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample bitsPerSample __attribute__ ((aligned(1)));
    /**
     * Channel mode for encoder
     */
    ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode channelMode __attribute__ ((aligned(1)));
    /**
     * The encoded audio bitrate in bits / second.
     * 0x00000000 - The audio bitrate is not specified / unused
     * 0x00000001 - 0x00FFFFFF - Encoded audio bitrate in bits/second
     * 0x01000000 - 0xFFFFFFFF - Reserved
     */
    uint32_t encodedAudioBitrate __attribute__ ((aligned(4)));
    /**
     * Peer MTU (in octets)
     */
    uint16_t peerMtu __attribute__ ((aligned(2)));
    ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific codecSpecific __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, codecType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, sampleRate) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, bitsPerSample) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, channelMode) == 9, "wrong offset");
static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, encodedAudioBitrate) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, peerMtu) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration, codecSpecific) == 18, "wrong offset");
static_assert(sizeof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration) == 24, "wrong size");
static_assert(__alignof(::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration) == 4, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::Status o);
static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::Status o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::Status lhs, const ::android::hardware::bluetooth::a2dp::V1_0::Status rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::Status rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::Status lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::Status lhs, const ::android::hardware::bluetooth::a2dp::V1_0::Status rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::Status rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::Status lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::Status e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::Status e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::CodecType o);
static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::CodecType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::CodecType lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::CodecType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::CodecType lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::CodecType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::CodecType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::CodecType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::SampleRate o);
static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::SampleRate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate lhs, const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate lhs, const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::SampleRate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample o);
static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample lhs, const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample lhs, const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode o);
static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode lhs, const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode lhs, const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& o);
static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& rhs);
static inline bool operator!=(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& rhs);

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& o);
static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& rhs);
static inline bool operator!=(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& rhs);

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific& o);
static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific& o, ::std::ostream*);
// operator== and operator!= are not generated for CodecSpecific

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration& o);
static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration& o, ::std::ostream*);
// operator== and operator!= are not generated for CodecConfiguration

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::bluetooth::a2dp::V1_0::Status>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::bluetooth::a2dp::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::Status::SUCCESS) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::Status::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::Status::SUCCESS;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::Status::FAILURE) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::Status::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::Status::FAILURE;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::Status::UNSUPPORTED_CODEC_CONFIGURATION) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::Status::UNSUPPORTED_CODEC_CONFIGURATION)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_CODEC_CONFIGURATION";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::Status::UNSUPPORTED_CODEC_CONFIGURATION;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::Status::PENDING) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::Status::PENDING)) {
        os += (first ? "" : " | ");
        os += "PENDING";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::Status::PENDING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::Status::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::Status::FAILURE) {
        return "FAILURE";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::Status::UNSUPPORTED_CODEC_CONFIGURATION) {
        return "UNSUPPORTED_CODEC_CONFIGURATION";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::Status::PENDING) {
        return "PENDING";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::bluetooth::a2dp::V1_0::CodecType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::bluetooth::a2dp::V1_0::CodecType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::CodecType::UNKNOWN) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::CodecType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::CodecType::UNKNOWN;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::CodecType::SBC) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::CodecType::SBC)) {
        os += (first ? "" : " | ");
        os += "SBC";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::CodecType::SBC;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::CodecType::AAC) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::CodecType::AAC)) {
        os += (first ? "" : " | ");
        os += "AAC";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::CodecType::AAC;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX)) {
        os += (first ? "" : " | ");
        os += "APTX";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX_HD) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX_HD)) {
        os += (first ? "" : " | ");
        os += "APTX_HD";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX_HD;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::CodecType::LDAC) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::CodecType::LDAC)) {
        os += (first ? "" : " | ");
        os += "LDAC";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::CodecType::LDAC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::CodecType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::CodecType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::CodecType::SBC) {
        return "SBC";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::CodecType::AAC) {
        return "AAC";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX) {
        return "APTX";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX_HD) {
        return "APTX_HD";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::CodecType::LDAC) {
        return "LDAC";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::CodecType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::bluetooth::a2dp::V1_0::SampleRate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::bluetooth::a2dp::V1_0::SampleRate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "RATE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_UNKNOWN;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_44100) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_44100)) {
        os += (first ? "" : " | ");
        os += "RATE_44100";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_44100;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_48000) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_48000)) {
        os += (first ? "" : " | ");
        os += "RATE_48000";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_48000;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_88200) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_88200)) {
        os += (first ? "" : " | ");
        os += "RATE_88200";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_88200;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_96000) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_96000)) {
        os += (first ? "" : " | ");
        os += "RATE_96000";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_96000;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_176400) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_176400)) {
        os += (first ? "" : " | ");
        os += "RATE_176400";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_176400;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_192000) == static_cast<uint32_t>(::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_192000)) {
        os += (first ? "" : " | ");
        os += "RATE_192000";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_192000;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::SampleRate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_UNKNOWN) {
        return "RATE_UNKNOWN";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_44100) {
        return "RATE_44100";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_48000) {
        return "RATE_48000";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_88200) {
        return "RATE_88200";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_96000) {
        return "RATE_96000";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_176400) {
        return "RATE_176400";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_192000) {
        return "RATE_192000";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::SampleRate o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_UNKNOWN) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "BITS_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_UNKNOWN;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_16) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_16)) {
        os += (first ? "" : " | ");
        os += "BITS_16";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_16;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_24) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_24)) {
        os += (first ? "" : " | ");
        os += "BITS_24";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_24;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_32) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_32)) {
        os += (first ? "" : " | ");
        os += "BITS_32";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_32;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_UNKNOWN) {
        return "BITS_UNKNOWN";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_16) {
        return "BITS_16";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_24) {
        return "BITS_24";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_32) {
        return "BITS_32";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::bluetooth::a2dp::V1_0::ChannelMode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::bluetooth::a2dp::V1_0::ChannelMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::UNKNOWN) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::UNKNOWN;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::MONO) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::MONO)) {
        os += (first ? "" : " | ");
        os += "MONO";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::MONO;
    }
    if ((o & ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::STEREO) == static_cast<uint8_t>(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::STEREO)) {
        os += (first ? "" : " | ");
        os += "STEREO";
        first = false;
        flipped |= ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::STEREO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::MONO) {
        return "MONO";
    }
    if (o == ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::STEREO) {
        return "STEREO";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::bluetooth::a2dp::V1_0::ChannelMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".codecParameters = ";
    os += ::android::hardware::toString(o.codecParameters);
    os += ", .minBitpool = ";
    os += ::android::hardware::toString(o.minBitpool);
    os += ", .maxBitpool = ";
    os += ::android::hardware::toString(o.maxBitpool);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& rhs) {
    if (lhs.codecParameters != rhs.codecParameters) {
        return false;
    }
    if (lhs.minBitpool != rhs.minBitpool) {
        return false;
    }
    if (lhs.maxBitpool != rhs.maxBitpool) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::SbcData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bitrateIndex = ";
    os += ::android::hardware::toString(o.bitrateIndex);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& rhs) {
    if (lhs.bitrateIndex != rhs.bitrateIndex) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& lhs, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific::LdacData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sbcData = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.sbcData);
    os += ", .ldacData = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.ldacData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration::CodecSpecific& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for CodecSpecific

static inline std::string toString(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".codecType = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.codecType);
    os += ", .sampleRate = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.sampleRate);
    os += ", .bitsPerSample = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.bitsPerSample);
    os += ", .channelMode = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.channelMode);
    os += ", .encodedAudioBitrate = ";
    os += ::android::hardware::toString(o.encodedAudioBitrate);
    os += ", .peerMtu = ";
    os += ::android::hardware::toString(o.peerMtu);
    os += ", .codecSpecific = ";
    os += ::android::hardware::bluetooth::a2dp::V1_0::toString(o.codecSpecific);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for CodecConfiguration


}  // namespace V1_0
}  // namespace a2dp
}  // namespace bluetooth
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
template<> inline constexpr std::array<::android::hardware::bluetooth::a2dp::V1_0::Status, 4> hidl_enum_values<::android::hardware::bluetooth::a2dp::V1_0::Status> = {
    ::android::hardware::bluetooth::a2dp::V1_0::Status::SUCCESS,
    ::android::hardware::bluetooth::a2dp::V1_0::Status::FAILURE,
    ::android::hardware::bluetooth::a2dp::V1_0::Status::UNSUPPORTED_CODEC_CONFIGURATION,
    ::android::hardware::bluetooth::a2dp::V1_0::Status::PENDING,
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
template<> inline constexpr std::array<::android::hardware::bluetooth::a2dp::V1_0::CodecType, 6> hidl_enum_values<::android::hardware::bluetooth::a2dp::V1_0::CodecType> = {
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType::UNKNOWN,
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType::SBC,
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType::AAC,
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX,
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType::APTX_HD,
    ::android::hardware::bluetooth::a2dp::V1_0::CodecType::LDAC,
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
template<> inline constexpr std::array<::android::hardware::bluetooth::a2dp::V1_0::SampleRate, 7> hidl_enum_values<::android::hardware::bluetooth::a2dp::V1_0::SampleRate> = {
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_UNKNOWN,
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_44100,
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_48000,
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_88200,
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_96000,
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_176400,
    ::android::hardware::bluetooth::a2dp::V1_0::SampleRate::RATE_192000,
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
template<> inline constexpr std::array<::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample, 4> hidl_enum_values<::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample> = {
    ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_UNKNOWN,
    ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_16,
    ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_24,
    ::android::hardware::bluetooth::a2dp::V1_0::BitsPerSample::BITS_32,
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
template<> inline constexpr std::array<::android::hardware::bluetooth::a2dp::V1_0::ChannelMode, 3> hidl_enum_values<::android::hardware::bluetooth::a2dp::V1_0::ChannelMode> = {
    ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::UNKNOWN,
    ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::MONO,
    ::android::hardware::bluetooth::a2dp::V1_0::ChannelMode::STEREO,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_TYPES_H
