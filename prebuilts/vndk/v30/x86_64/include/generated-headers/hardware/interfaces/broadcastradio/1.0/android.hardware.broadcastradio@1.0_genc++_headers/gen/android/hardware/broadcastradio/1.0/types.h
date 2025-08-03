#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class Class : uint32_t;
enum class Band : uint32_t;
enum class Rds : uint32_t;
enum class Deemphasis : uint32_t;
enum class Direction : uint32_t;
struct FmBandConfig;
struct AmBandConfig;
struct BandConfig;
struct Properties;
enum class MetadataType : int32_t;
enum class MetadataKey : int32_t;
struct MetaDataClock;
struct MetaData;
struct ProgramInfo;

enum class Result : int32_t {
    OK = 0,
    NOT_INITIALIZED = 1 /* ::android::hardware::broadcastradio::V1_0::Result.OK implicitly + 1 */,
    INVALID_ARGUMENTS = 2 /* ::android::hardware::broadcastradio::V1_0::Result.NOT_INITIALIZED implicitly + 1 */,
    INVALID_STATE = 3 /* ::android::hardware::broadcastradio::V1_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
    TIMEOUT = 4 /* ::android::hardware::broadcastradio::V1_0::Result.INVALID_STATE implicitly + 1 */,
};

/**
 * Radio hardware module class. A given radio hardware module HAL is of one
 * class only. The platform can not have more than one hardware module of
 * each class. Current version of the framework only supports RADIO_CLASS_AM_FM.
 */
enum class Class : uint32_t {
    /**
     * FM (including HD radio) and AM
     */
    AM_FM = 0u,
    /**
     * Satellite Radio
     */
    SAT = 1u,
    /**
     * Digital Radio (DAB)
     */
    DT = 2u,
};

/**
 * value for field "type" of radio band described in struct radio_hal_band_config
 */
enum class Band : uint32_t {
    /**
     * Amplitude Modulation band: LW, MW, SW
     */
    AM = 0u,
    /**
     * Frequency Modulation band: FM
     */
    FM = 1u,
    /**
     * FM HD Radio / DRM (IBOC)
     */
    FM_HD = 2u,
    /**
     * AM HD Radio / DRM (IBOC)
     */
    AM_HD = 3u,
};

/**
 * RDS variant implemented. A struct FmBandConfig can list none or several.
 */
enum class Rds : uint32_t {
    NONE = 0u,
    WORLD = 1u /* (1 << 0) */,
    US = 2u /* (1 << 1) */,
};

/*
 * FM deemphasis variant implemented.
 * A struct FmBandConfig can list one or more.
 */
enum class Deemphasis : uint32_t {
    D50 = 1u /* (1 << 0) */,
    D75 = 2u /* (1 << 1) */,
};

/**
 * Scanning direction for scan() and step() tuner APIs
 */
enum class Direction : uint32_t {
    UP = 0u,
    DOWN = 1u /* ::android::hardware::broadcastradio::V1_0::Direction.UP implicitly + 1 */,
};

/**
 * Unique handle allocated to a radio module
 */
typedef uint32_t Handle;

/**
 * Additional attributes for an FM band configuration
 */
struct FmBandConfig final {
    /**
     * deemphasis variant
     */
    ::android::hardware::broadcastradio::V1_0::Deemphasis deemphasis __attribute__ ((aligned(4)));
    /**
     * stereo supported
     */
    bool stereo __attribute__ ((aligned(1)));
    /**
     * RDS variants supported
     */
    ::android::hardware::broadcastradio::V1_0::Rds rds __attribute__ ((aligned(4)));
    /**
     * Traffic Announcement supported
     */
    bool ta __attribute__ ((aligned(1)));
    /**
     * Alternate Frequency supported
     */
    bool af __attribute__ ((aligned(1)));
    /**
     * Emergency announcements supported
     */
    bool ea __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::FmBandConfig, deemphasis) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::FmBandConfig, stereo) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::FmBandConfig, rds) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::FmBandConfig, ta) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::FmBandConfig, af) == 13, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::FmBandConfig, ea) == 14, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::FmBandConfig) == 16, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::FmBandConfig) == 4, "wrong alignment");

/**
 * Additional attributes for an AM band configuration
 */
struct AmBandConfig final {
    /**
     * Stereo supported
     */
    bool stereo __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::AmBandConfig, stereo) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::AmBandConfig) == 1, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::AmBandConfig) == 1, "wrong alignment");

/*
 * Radio band configuration. Describes a given band supported by the radio
 * module. The HAL can expose only one band per type with the the maximum range
 * supported and all options. The framework will derive the actual regions were
 * this module can operate and expose separate band configurations for
 * applications to chose from.
 */
struct BandConfig final {
    // Forward declaration for forward reference support:
    union Ext;

    union Ext final {
        ::android::hardware::broadcastradio::V1_0::FmBandConfig fm __attribute__ ((aligned(4)));
        ::android::hardware::broadcastradio::V1_0::AmBandConfig am __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig::Ext, fm) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig::Ext, am) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::broadcastradio::V1_0::BandConfig::Ext) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::broadcastradio::V1_0::BandConfig::Ext) == 4, "wrong alignment");

    ::android::hardware::broadcastradio::V1_0::Band type __attribute__ ((aligned(4)));
    bool antennaConnected __attribute__ ((aligned(1)));
    uint32_t lowerLimit __attribute__ ((aligned(4)));
    uint32_t upperLimit __attribute__ ((aligned(4)));
    ::android::hardware::hidl_vec<uint32_t> spacings __attribute__ ((aligned(8)));
    ::android::hardware::broadcastradio::V1_0::BandConfig::Ext ext __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig, antennaConnected) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig, lowerLimit) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig, upperLimit) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig, spacings) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::BandConfig, ext) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::BandConfig) == 48, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::BandConfig) == 8, "wrong alignment");

/*
 * Exposes properties of a given hardware radio module.
 * NOTE: current framework implementation supports only one audio source
 * (num_audio_sources = 1). The source corresponds to AUDIO_DEVICE_IN_FM_TUNER.
 * If more than one tuner is supported (num_tuners > 1), only one can be
 * connected to the audio source.
 */
struct Properties final {
    /**
     * Class of this module. E.g AM_FM
     */
    ::android::hardware::broadcastradio::V1_0::Class classId __attribute__ ((aligned(4)));
    /**
     * implementor name
     */
    ::android::hardware::hidl_string implementor __attribute__ ((aligned(8)));
    /**
     * product name
     */
    ::android::hardware::hidl_string product __attribute__ ((aligned(8)));
    /**
     * product version
     */
    ::android::hardware::hidl_string version __attribute__ ((aligned(8)));
    /**
     * serial number (for subscription services)
     */
    ::android::hardware::hidl_string serial __attribute__ ((aligned(8)));
    /**
     * number of tuners controllable independently
     */
    uint32_t numTuners __attribute__ ((aligned(4)));
    /**
     * number of audio sources driven simultaneously
     */
    uint32_t numAudioSources __attribute__ ((aligned(4)));
    /**
     * the hardware supports capture of audio source from audio HAL
     */
    bool supportsCapture __attribute__ ((aligned(1)));
    /**
     * band descriptors
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_0::BandConfig> bands __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, classId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, implementor) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, product) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, version) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, serial) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, numTuners) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, numAudioSources) == 76, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, supportsCapture) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::Properties, bands) == 88, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::Properties) == 104, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::Properties) == 8, "wrong alignment");

enum class MetadataType : int32_t {
    INVALID = -1 /* -1 */,
    /**
     * Signed 32 bit integer
     */
    INT = 0,
    /**
     * String
     */
    TEXT = 1,
    /**
     * Raw binary data (icon or art).
     *
     * The data should be a valid PNG, JPEG, GIF or BMP file.
     * Invalid format must be handled gracefully as if the field was missing.
     */
    RAW = 2,
    /**
     * clock data, see MetaDataClock
     */
    CLOCK = 3,
};

enum class MetadataKey : int32_t {
    INVALID = -1 /* -1 */,
    /**
     * RDS PI                 - int32_t
     */
    RDS_PI = 0,
    /**
     * RDS PS                 - string
     */
    RDS_PS = 1,
    /**
     * RDS PTY                - int32_t
     */
    RDS_PTY = 2,
    /**
     * RBDS PTY               - int32_t
     */
    RBDS_PTY = 3,
    /**
     * RDS RT                 - string
     */
    RDS_RT = 4,
    /**
     * Song title             - string
     */
    TITLE = 5,
    /**
     * Artist name            - string
     */
    ARTIST = 6,
    /**
     * Album name             - string
     */
    ALBUM = 7,
    /**
     * Musical genre          - string
     */
    GENRE = 8,
    /**
     * Station icon           - raw (int32_t for HAL 1.1)
     */
    ICON = 9,
    /**
     * Album art              - raw (int32_t for HAL 1.1)
     */
    ART = 10,
    /**
     * Clock                  - MetaDataClock
     */
    CLOCK = 11,
};

struct MetaDataClock final {
    /**
     * Seconds since epoch at GMT + 0.
     */
    uint64_t utcSecondsSinceEpoch __attribute__ ((aligned(8)));
    /**
     * Minutes offset from the GMT.
     */
    int32_t timezoneOffsetInMinutes __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaDataClock, utcSecondsSinceEpoch) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaDataClock, timezoneOffsetInMinutes) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::MetaDataClock) == 16, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::MetaDataClock) == 8, "wrong alignment");

struct MetaData final {
    ::android::hardware::broadcastradio::V1_0::MetadataType type __attribute__ ((aligned(4)));
    ::android::hardware::broadcastradio::V1_0::MetadataKey key __attribute__ ((aligned(4)));
    /**
     * Value used for type MetadataType.INT
     */
    int32_t intValue __attribute__ ((aligned(4)));
    /**
     * Value used for type MetadataType.CLOCK
     */
    ::android::hardware::broadcastradio::V1_0::MetaDataClock clockValue __attribute__ ((aligned(8)));
    /**
     * Value used for type MetadataType.TEXT
     */
    ::android::hardware::hidl_string stringValue __attribute__ ((aligned(8)));
    /**
     * Value used for type MetadataType.RAW
     */
    ::android::hardware::hidl_vec<uint8_t> rawValue __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaData, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaData, key) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaData, intValue) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaData, clockValue) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaData, stringValue) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::MetaData, rawValue) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::MetaData) == 64, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::MetaData) == 8, "wrong alignment");

/*
 * Radio program information. Returned by the HAL with event RADIO_EVENT_TUNED.
 * Contains information on currently tuned channel.
 */
struct ProgramInfo final {
    uint32_t channel __attribute__ ((aligned(4)));
    /**
     * current channel. (e.g kHz for band type AM_FM)
     */
    uint32_t subChannel __attribute__ ((aligned(4)));
    /**
     * current sub channel. (FM_HD)
     *
     *
     * Tuned to a program (not a noise). It's the same condition that would
     * stop scan operation.
     */
    bool tuned __attribute__ ((aligned(1)));
    bool stereo __attribute__ ((aligned(1)));
    /**
     * program is stereo or not
     */
    bool digital __attribute__ ((aligned(1)));
    /**
     * digital program or not (e.g HD Radio program)
     *
     *
     * Signal quality measured in 0% to 100% range.
     *
     * Despite the name, this is not a signal strength.
     * The purpose of this field is primarily informative.
     */
    uint32_t signalStrength __attribute__ ((aligned(4)));
    /**
     * Metadata: PTY, song title etc.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V1_0::MetaData> metadata __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, channel) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, subChannel) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, tuned) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, stereo) == 9, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, digital) == 10, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, signalStrength) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V1_0::ProgramInfo, metadata) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V1_0::ProgramInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V1_0::ProgramInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::Result o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_0::Result lhs, const ::android::hardware::broadcastradio::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_0::Result lhs, const ::android::hardware::broadcastradio::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V1_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V1_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::Class o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Class o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Class lhs, const ::android::hardware::broadcastradio::V1_0::Class rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Class rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Class lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Class lhs, const ::android::hardware::broadcastradio::V1_0::Class rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Class rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Class lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Class e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Class e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::Band o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Band o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Band lhs, const ::android::hardware::broadcastradio::V1_0::Band rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Band rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Band lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Band lhs, const ::android::hardware::broadcastradio::V1_0::Band rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Band rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Band lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Band e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Band e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::Rds o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Rds o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Rds lhs, const ::android::hardware::broadcastradio::V1_0::Rds rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Rds rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Rds lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Rds lhs, const ::android::hardware::broadcastradio::V1_0::Rds rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Rds rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Rds lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Rds e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Rds e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::Deemphasis o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Deemphasis o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Deemphasis lhs, const ::android::hardware::broadcastradio::V1_0::Deemphasis rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Deemphasis rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Deemphasis lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Deemphasis lhs, const ::android::hardware::broadcastradio::V1_0::Deemphasis rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Deemphasis rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Deemphasis lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Deemphasis e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Deemphasis e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::Direction o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Direction o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Direction lhs, const ::android::hardware::broadcastradio::V1_0::Direction rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Direction rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V1_0::Direction lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Direction lhs, const ::android::hardware::broadcastradio::V1_0::Direction rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V1_0::Direction rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V1_0::Direction lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Direction e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V1_0::Direction e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::FmBandConfig& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::FmBandConfig& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::AmBandConfig& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::AmBandConfig& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::BandConfig::Ext& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::BandConfig::Ext& o, ::std::ostream*);
// operator== and operator!= are not generated for Ext

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::BandConfig& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::BandConfig& o, ::std::ostream*);
// operator== and operator!= are not generated for BandConfig

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::Properties& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::Properties& o, ::std::ostream*);
// operator== and operator!= are not generated for Properties

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::MetadataType o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::MetadataType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_0::MetadataType lhs, const ::android::hardware::broadcastradio::V1_0::MetadataType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V1_0::MetadataType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_0::MetadataType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_0::MetadataType lhs, const ::android::hardware::broadcastradio::V1_0::MetadataType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V1_0::MetadataType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_0::MetadataType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V1_0::MetadataType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V1_0::MetadataType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V1_0::MetadataKey o);
static inline void PrintTo(::android::hardware::broadcastradio::V1_0::MetadataKey o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_0::MetadataKey lhs, const ::android::hardware::broadcastradio::V1_0::MetadataKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V1_0::MetadataKey rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V1_0::MetadataKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_0::MetadataKey lhs, const ::android::hardware::broadcastradio::V1_0::MetadataKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V1_0::MetadataKey rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V1_0::MetadataKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V1_0::MetadataKey e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V1_0::MetadataKey e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& lhs, const ::android::hardware::broadcastradio::V1_0::MetaDataClock& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& lhs, const ::android::hardware::broadcastradio::V1_0::MetaDataClock& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::MetaData& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::MetaData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::MetaData& lhs, const ::android::hardware::broadcastradio::V1_0::MetaData& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::MetaData& lhs, const ::android::hardware::broadcastradio::V1_0::MetaData& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_0::ProgramInfo& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_0::ProgramInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::Result::OK) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Result::OK;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Result::NOT_INITIALIZED) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::Result::NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Result::NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Result::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Result::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Result::TIMEOUT) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::Result::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Result::TIMEOUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Result::NOT_INITIALIZED) {
        return "NOT_INITIALIZED";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Result::TIMEOUT) {
        return "TIMEOUT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::Class>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::Class> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::Class::AM_FM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Class::AM_FM)) {
        os += (first ? "" : " | ");
        os += "AM_FM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Class::AM_FM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Class::SAT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Class::SAT)) {
        os += (first ? "" : " | ");
        os += "SAT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Class::SAT;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Class::DT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Class::DT)) {
        os += (first ? "" : " | ");
        os += "DT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Class::DT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::Class o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::Class::AM_FM) {
        return "AM_FM";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Class::SAT) {
        return "SAT";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Class::DT) {
        return "DT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Class o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::Band>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::Band> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::Band::AM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Band::AM)) {
        os += (first ? "" : " | ");
        os += "AM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Band::AM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Band::FM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Band::FM)) {
        os += (first ? "" : " | ");
        os += "FM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Band::FM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Band::FM_HD) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Band::FM_HD)) {
        os += (first ? "" : " | ");
        os += "FM_HD";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Band::FM_HD;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Band::AM_HD) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Band::AM_HD)) {
        os += (first ? "" : " | ");
        os += "AM_HD";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Band::AM_HD;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::Band o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::Band::AM) {
        return "AM";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Band::FM) {
        return "FM";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Band::FM_HD) {
        return "FM_HD";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Band::AM_HD) {
        return "AM_HD";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Band o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::Rds>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::Rds> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::Rds::NONE) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Rds::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Rds::NONE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Rds::WORLD) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Rds::WORLD)) {
        os += (first ? "" : " | ");
        os += "WORLD";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Rds::WORLD;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Rds::US) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Rds::US)) {
        os += (first ? "" : " | ");
        os += "US";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Rds::US;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::Rds o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::Rds::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Rds::WORLD) {
        return "WORLD";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Rds::US) {
        return "US";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Rds o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::Deemphasis>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::Deemphasis> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::Deemphasis::D50) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Deemphasis::D50)) {
        os += (first ? "" : " | ");
        os += "D50";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Deemphasis::D50;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Deemphasis::D75) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Deemphasis::D75)) {
        os += (first ? "" : " | ");
        os += "D75";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Deemphasis::D75;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::Deemphasis o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::Deemphasis::D50) {
        return "D50";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Deemphasis::D75) {
        return "D75";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Deemphasis o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::Direction>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::Direction> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::Direction::UP) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Direction::UP)) {
        os += (first ? "" : " | ");
        os += "UP";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Direction::UP;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::Direction::DOWN) == static_cast<uint32_t>(::android::hardware::broadcastradio::V1_0::Direction::DOWN)) {
        os += (first ? "" : " | ");
        os += "DOWN";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::Direction::DOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::Direction o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::Direction::UP) {
        return "UP";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::Direction::DOWN) {
        return "DOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::Direction o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".deemphasis = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.deemphasis);
    os += ", .stereo = ";
    os += ::android::hardware::toString(o.stereo);
    os += ", .rds = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.rds);
    os += ", .ta = ";
    os += ::android::hardware::toString(o.ta);
    os += ", .af = ";
    os += ::android::hardware::toString(o.af);
    os += ", .ea = ";
    os += ::android::hardware::toString(o.ea);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::FmBandConfig& rhs) {
    if (lhs.deemphasis != rhs.deemphasis) {
        return false;
    }
    if (lhs.stereo != rhs.stereo) {
        return false;
    }
    if (lhs.rds != rhs.rds) {
        return false;
    }
    if (lhs.ta != rhs.ta) {
        return false;
    }
    if (lhs.af != rhs.af) {
        return false;
    }
    if (lhs.ea != rhs.ea) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::FmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::FmBandConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".stereo = ";
    os += ::android::hardware::toString(o.stereo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::AmBandConfig& rhs) {
    if (lhs.stereo != rhs.stereo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::AmBandConfig& lhs, const ::android::hardware::broadcastradio::V1_0::AmBandConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::BandConfig::Ext& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".fm = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.fm);
    os += ", .am = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.am);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::BandConfig::Ext& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Ext

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::BandConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.type);
    os += ", .antennaConnected = ";
    os += ::android::hardware::toString(o.antennaConnected);
    os += ", .lowerLimit = ";
    os += ::android::hardware::toString(o.lowerLimit);
    os += ", .upperLimit = ";
    os += ::android::hardware::toString(o.upperLimit);
    os += ", .spacings = ";
    os += ::android::hardware::toString(o.spacings);
    os += ", .ext = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.ext);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::BandConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for BandConfig

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::Properties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".classId = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.classId);
    os += ", .implementor = ";
    os += ::android::hardware::toString(o.implementor);
    os += ", .product = ";
    os += ::android::hardware::toString(o.product);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .serial = ";
    os += ::android::hardware::toString(o.serial);
    os += ", .numTuners = ";
    os += ::android::hardware::toString(o.numTuners);
    os += ", .numAudioSources = ";
    os += ::android::hardware::toString(o.numAudioSources);
    os += ", .supportsCapture = ";
    os += ::android::hardware::toString(o.supportsCapture);
    os += ", .bands = ";
    os += ::android::hardware::toString(o.bands);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::Properties& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Properties

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::MetadataType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::MetadataType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataType::INVALID) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataType::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataType::INVALID;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataType::INT) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataType::INT)) {
        os += (first ? "" : " | ");
        os += "INT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataType::INT;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataType::TEXT) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataType::TEXT)) {
        os += (first ? "" : " | ");
        os += "TEXT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataType::TEXT;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataType::RAW) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataType::RAW)) {
        os += (first ? "" : " | ");
        os += "RAW";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataType::RAW;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataType::CLOCK) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataType::CLOCK)) {
        os += (first ? "" : " | ");
        os += "CLOCK";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataType::CLOCK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::MetadataType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataType::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataType::INT) {
        return "INT";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataType::TEXT) {
        return "TEXT";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataType::RAW) {
        return "RAW";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataType::CLOCK) {
        return "CLOCK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::MetadataType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V1_0::MetadataKey>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V1_0::MetadataKey> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::INVALID) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::INVALID;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PI) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PI)) {
        os += (first ? "" : " | ");
        os += "RDS_PI";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PI;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PS) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PS)) {
        os += (first ? "" : " | ");
        os += "RDS_PS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PS;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PTY) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PTY)) {
        os += (first ? "" : " | ");
        os += "RDS_PTY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PTY;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::RBDS_PTY) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::RBDS_PTY)) {
        os += (first ? "" : " | ");
        os += "RBDS_PTY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::RBDS_PTY;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_RT) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_RT)) {
        os += (first ? "" : " | ");
        os += "RDS_RT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_RT;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::TITLE) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::TITLE)) {
        os += (first ? "" : " | ");
        os += "TITLE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::TITLE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::ARTIST) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::ARTIST)) {
        os += (first ? "" : " | ");
        os += "ARTIST";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::ARTIST;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::ALBUM) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::ALBUM)) {
        os += (first ? "" : " | ");
        os += "ALBUM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::ALBUM;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::GENRE) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::GENRE)) {
        os += (first ? "" : " | ");
        os += "GENRE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::GENRE;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::ICON) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::ICON)) {
        os += (first ? "" : " | ");
        os += "ICON";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::ICON;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::ART) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::ART)) {
        os += (first ? "" : " | ");
        os += "ART";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::ART;
    }
    if ((o & ::android::hardware::broadcastradio::V1_0::MetadataKey::CLOCK) == static_cast<int32_t>(::android::hardware::broadcastradio::V1_0::MetadataKey::CLOCK)) {
        os += (first ? "" : " | ");
        os += "CLOCK";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V1_0::MetadataKey::CLOCK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V1_0::MetadataKey o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PI) {
        return "RDS_PI";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PS) {
        return "RDS_PS";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PTY) {
        return "RDS_PTY";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::RBDS_PTY) {
        return "RBDS_PTY";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_RT) {
        return "RDS_RT";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::TITLE) {
        return "TITLE";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::ARTIST) {
        return "ARTIST";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::ALBUM) {
        return "ALBUM";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::GENRE) {
        return "GENRE";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::ICON) {
        return "ICON";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::ART) {
        return "ART";
    }
    if (o == ::android::hardware::broadcastradio::V1_0::MetadataKey::CLOCK) {
        return "CLOCK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V1_0::MetadataKey o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".utcSecondsSinceEpoch = ";
    os += ::android::hardware::toString(o.utcSecondsSinceEpoch);
    os += ", .timezoneOffsetInMinutes = ";
    os += ::android::hardware::toString(o.timezoneOffsetInMinutes);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& lhs, const ::android::hardware::broadcastradio::V1_0::MetaDataClock& rhs) {
    if (lhs.utcSecondsSinceEpoch != rhs.utcSecondsSinceEpoch) {
        return false;
    }
    if (lhs.timezoneOffsetInMinutes != rhs.timezoneOffsetInMinutes) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::MetaDataClock& lhs, const ::android::hardware::broadcastradio::V1_0::MetaDataClock& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::MetaData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.type);
    os += ", .key = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.key);
    os += ", .intValue = ";
    os += ::android::hardware::toString(o.intValue);
    os += ", .clockValue = ";
    os += ::android::hardware::broadcastradio::V1_0::toString(o.clockValue);
    os += ", .stringValue = ";
    os += ::android::hardware::toString(o.stringValue);
    os += ", .rawValue = ";
    os += ::android::hardware::toString(o.rawValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::MetaData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::MetaData& lhs, const ::android::hardware::broadcastradio::V1_0::MetaData& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.intValue != rhs.intValue) {
        return false;
    }
    if (lhs.clockValue != rhs.clockValue) {
        return false;
    }
    if (lhs.stringValue != rhs.stringValue) {
        return false;
    }
    if (lhs.rawValue != rhs.rawValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::MetaData& lhs, const ::android::hardware::broadcastradio::V1_0::MetaData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".channel = ";
    os += ::android::hardware::toString(o.channel);
    os += ", .subChannel = ";
    os += ::android::hardware::toString(o.subChannel);
    os += ", .tuned = ";
    os += ::android::hardware::toString(o.tuned);
    os += ", .stereo = ";
    os += ::android::hardware::toString(o.stereo);
    os += ", .digital = ";
    os += ::android::hardware::toString(o.digital);
    os += ", .signalStrength = ";
    os += ::android::hardware::toString(o.signalStrength);
    os += ", .metadata = ";
    os += ::android::hardware::toString(o.metadata);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_0::ProgramInfo& rhs) {
    if (lhs.channel != rhs.channel) {
        return false;
    }
    if (lhs.subChannel != rhs.subChannel) {
        return false;
    }
    if (lhs.tuned != rhs.tuned) {
        return false;
    }
    if (lhs.stereo != rhs.stereo) {
        return false;
    }
    if (lhs.digital != rhs.digital) {
        return false;
    }
    if (lhs.signalStrength != rhs.signalStrength) {
        return false;
    }
    if (lhs.metadata != rhs.metadata) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V1_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V1_0::ProgramInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::Result, 5> hidl_enum_values<::android::hardware::broadcastradio::V1_0::Result> = {
    ::android::hardware::broadcastradio::V1_0::Result::OK,
    ::android::hardware::broadcastradio::V1_0::Result::NOT_INITIALIZED,
    ::android::hardware::broadcastradio::V1_0::Result::INVALID_ARGUMENTS,
    ::android::hardware::broadcastradio::V1_0::Result::INVALID_STATE,
    ::android::hardware::broadcastradio::V1_0::Result::TIMEOUT,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::Class, 3> hidl_enum_values<::android::hardware::broadcastradio::V1_0::Class> = {
    ::android::hardware::broadcastradio::V1_0::Class::AM_FM,
    ::android::hardware::broadcastradio::V1_0::Class::SAT,
    ::android::hardware::broadcastradio::V1_0::Class::DT,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::Band, 4> hidl_enum_values<::android::hardware::broadcastradio::V1_0::Band> = {
    ::android::hardware::broadcastradio::V1_0::Band::AM,
    ::android::hardware::broadcastradio::V1_0::Band::FM,
    ::android::hardware::broadcastradio::V1_0::Band::FM_HD,
    ::android::hardware::broadcastradio::V1_0::Band::AM_HD,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::Rds, 3> hidl_enum_values<::android::hardware::broadcastradio::V1_0::Rds> = {
    ::android::hardware::broadcastradio::V1_0::Rds::NONE,
    ::android::hardware::broadcastradio::V1_0::Rds::WORLD,
    ::android::hardware::broadcastradio::V1_0::Rds::US,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::Deemphasis, 2> hidl_enum_values<::android::hardware::broadcastradio::V1_0::Deemphasis> = {
    ::android::hardware::broadcastradio::V1_0::Deemphasis::D50,
    ::android::hardware::broadcastradio::V1_0::Deemphasis::D75,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::Direction, 2> hidl_enum_values<::android::hardware::broadcastradio::V1_0::Direction> = {
    ::android::hardware::broadcastradio::V1_0::Direction::UP,
    ::android::hardware::broadcastradio::V1_0::Direction::DOWN,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::MetadataType, 5> hidl_enum_values<::android::hardware::broadcastradio::V1_0::MetadataType> = {
    ::android::hardware::broadcastradio::V1_0::MetadataType::INVALID,
    ::android::hardware::broadcastradio::V1_0::MetadataType::INT,
    ::android::hardware::broadcastradio::V1_0::MetadataType::TEXT,
    ::android::hardware::broadcastradio::V1_0::MetadataType::RAW,
    ::android::hardware::broadcastradio::V1_0::MetadataType::CLOCK,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V1_0::MetadataKey, 13> hidl_enum_values<::android::hardware::broadcastradio::V1_0::MetadataKey> = {
    ::android::hardware::broadcastradio::V1_0::MetadataKey::INVALID,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PI,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PS,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_PTY,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::RBDS_PTY,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::RDS_RT,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::TITLE,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::ARTIST,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::ALBUM,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::GENRE,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::ICON,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::ART,
    ::android::hardware::broadcastradio::V1_0::MetadataKey::CLOCK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_TYPES_H
