#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V2_0 {

// Forward declaration for forward reference support:
enum class Constants : int32_t;
enum class Result : int32_t;
enum class ConfigFlag : uint32_t;
struct VendorKeyValue;
enum class Rds : uint8_t;
enum class Deemphasis : uint8_t;
struct AmFmRegionConfig;
struct AmFmBandRange;
struct DabTableEntry;
struct Properties;
struct ProgramIdentifier;
struct ProgramSelector;
enum class ProgramInfoFlags : uint32_t;
struct ProgramInfo;
enum class IdentifierType : uint32_t;
enum class MetadataKey : int32_t;
struct Metadata;
struct ProgramListChunk;
struct ProgramFilter;
enum class AnnouncementType : uint8_t;
struct Announcement;

// Order of inner types was changed for forward reference support.

/**
 * Constants used by broadcast radio HAL.
 */
enum class Constants : int32_t {
    /**
     * Invalid identifier for IBroadcastRadio::getImage.
     */
    INVALID_IMAGE = 0,
    /**
     * If the antenna is disconnected from the beginning, the
     * onAntennaStateChange callback must be called within this time.
     */
    ANTENNA_DISCONNECTED_TIMEOUT_MS = 100,
    LIST_COMPLETE_TIMEOUT_MS = 300000,
};

enum class Result : int32_t {
    OK = 0,
    UNKNOWN_ERROR = 1 /* ::android::hardware::broadcastradio::V2_0::Result.OK implicitly + 1 */,
    INTERNAL_ERROR = 2 /* ::android::hardware::broadcastradio::V2_0::Result.UNKNOWN_ERROR implicitly + 1 */,
    INVALID_ARGUMENTS = 3 /* ::android::hardware::broadcastradio::V2_0::Result.INTERNAL_ERROR implicitly + 1 */,
    INVALID_STATE = 4 /* ::android::hardware::broadcastradio::V2_0::Result.INVALID_ARGUMENTS implicitly + 1 */,
    NOT_SUPPORTED = 5 /* ::android::hardware::broadcastradio::V2_0::Result.INVALID_STATE implicitly + 1 */,
    TIMEOUT = 6 /* ::android::hardware::broadcastradio::V2_0::Result.NOT_SUPPORTED implicitly + 1 */,
};

/**
 * Configuration flags to be used with isConfigFlagSet and setConfigFlag methods
 * of ITunerSession.
 */
enum class ConfigFlag : uint32_t {
    /**
     * Forces mono audio stream reception.
     *
     * Analog broadcasts can recover poor reception conditions by jointing
     * stereo channels into one. Mainly for, but not limited to AM/FM.
     */
    FORCE_MONO = 1u,
    /**
     * Forces the analog playback for the supporting radio technology.
     *
     * User may disable digital playback for FM HD Radio or hybrid FM/DAB with
     * this option. This is purely user choice, ie. does not reflect digital-
     * analog handover state managed from the HAL implementation side.
     *
     * Some radio technologies may not support this, ie. DAB.
     */
    FORCE_ANALOG = 2u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.FORCE_MONO implicitly + 1 */,
    /**
     * Forces the digital playback for the supporting radio technology.
     *
     * User may disable digital-analog handover that happens with poor
     * reception conditions. With digital forced, the radio will remain silent
     * instead of switching to analog channel if it's available. This is purely
     * user choice, it does not reflect the actual state of handover.
     */
    FORCE_DIGITAL = 3u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.FORCE_ANALOG implicitly + 1 */,
    /**
     * RDS Alternative Frequencies.
     *
     * If set and the currently tuned RDS station broadcasts on multiple
     * channels, radio tuner automatically switches to the best available
     * alternative.
     */
    RDS_AF = 4u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.FORCE_DIGITAL implicitly + 1 */,
    /**
     * RDS region-specific program lock-down.
     *
     * Allows user to lock to the current region as they move into the
     * other region.
     */
    RDS_REG = 5u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.RDS_AF implicitly + 1 */,
    /**
     * Enables DAB-DAB hard- and implicit-linking (the same content).
     */
    DAB_DAB_LINKING = 6u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.RDS_REG implicitly + 1 */,
    /**
     * Enables DAB-FM hard- and implicit-linking (the same content).
     */
    DAB_FM_LINKING = 7u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.DAB_DAB_LINKING implicitly + 1 */,
    /**
     * Enables DAB-DAB soft-linking (related content).
     */
    DAB_DAB_SOFT_LINKING = 8u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.DAB_FM_LINKING implicitly + 1 */,
    /**
     * Enables DAB-FM soft-linking (related content).
     */
    DAB_FM_SOFT_LINKING = 9u /* ::android::hardware::broadcastradio::V2_0::ConfigFlag.DAB_DAB_SOFT_LINKING implicitly + 1 */,
};

/**
 * A key-value pair for vendor-specific information to be passed as-is through
 * Android framework to the front-end application.
 */
struct VendorKeyValue final {
    /**
     * Key must start with unique vendor Java-style namespace,
     * eg. 'com.somecompany.parameter1'.
     */
    ::android::hardware::hidl_string key __attribute__ ((aligned(8)));
    /**
     * Value must be passed through the framework without any changes.
     * Format of this string can vary across vendors.
     */
    ::android::hardware::hidl_string value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::VendorKeyValue, key) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::VendorKeyValue, value) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::VendorKeyValue) == 32, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::VendorKeyValue) == 8, "wrong alignment");

/**
 * A supported or configured RDS variant.
 *
 * Both might be set for hardware capabilities check (with full=true when
 * calling getAmFmRegionConfig), but only one (or none) for specific
 * region settings.
 */
enum class Rds : uint8_t {
    /**
     * Standard variant, used everywhere except North America.
     */
    RDS = 1 /* 1 << 0 */,
    /**
     * Variant used in North America.
     */
    RBDS = 2 /* 1 << 1 */,
};

/**
 * FM de-emphasis filter supported or configured.
 *
 * Both might be set for hardware capabilities check (with full=true when
 * calling getAmFmRegionConfig), but exactly one for specific region settings.
 */
enum class Deemphasis : uint8_t {
    D50 = 1 /* 1 << 0 */,
    D75 = 2 /* 1 << 1 */,
};

/**
 * Regional configuration for AM/FM.
 *
 * For hardware capabilities check (with full=true when calling
 * getAmFmRegionConfig), HAL implementation fills entire supported range of
 * frequencies and features.
 *
 * When checking current configuration, at most one bit in each bitfield
 * can be set.
 */
struct AmFmRegionConfig final {
    /**
     * All supported or configured AM/FM bands.
     *
     * AM/FM bands are identified by frequency value
     * (see IdentifierType::AMFM_FREQUENCY).
     *
     * With typical configuration, it's expected to have two frequency ranges
     * for capabilities check (AM and FM) and four ranges for specific region
     * configuration (AM LW, AM MW, AM SW, FM).
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::AmFmBandRange> ranges __attribute__ ((aligned(8)));
    /**
     * De-emphasis filter supported/configured.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::Deemphasis> fmDeemphasis __attribute__ ((aligned(1)));
    /**
     * RDS/RBDS variant supported/configured.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::Rds> fmRds __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmRegionConfig, ranges) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmRegionConfig, fmDeemphasis) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmRegionConfig, fmRds) == 17, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::AmFmRegionConfig) == 24, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::AmFmRegionConfig) == 8, "wrong alignment");

/**
 * AM/FM band range for region configuration.
 *
 * Defines channel grid: each possible channel is set at
 * lowerBound + channelNumber * spacing, up to upperBound.
 */
struct AmFmBandRange final {
    /**
     * The frequency (in kHz) of the first channel within the range.
     */
    uint32_t lowerBound __attribute__ ((aligned(4)));
    /**
     * The frequency (in kHz) of the last channel within the range.
     */
    uint32_t upperBound __attribute__ ((aligned(4)));
    /**
     * Channel grid resolution (in kHz), how far apart are the channels.
     */
    uint32_t spacing __attribute__ ((aligned(4)));
    /**
     * Channel spacing (in kHz) used to speed up seeking to the next station
     * via the ITunerSession::scan() operation.
     *
     * It must be a multiple of channel grid resolution.
     *
     * Tuner may first quickly check every n-th channel and if it detects echo
     * from a station, it fine-tunes to find the exact frequency.
     *
     * It's ignored for capabilities check (with full=true when calling
     * getAmFmRegionConfig).
     */
    uint32_t scanSpacing __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmBandRange, lowerBound) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmBandRange, upperBound) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmBandRange, spacing) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::AmFmBandRange, scanSpacing) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::AmFmBandRange) == 16, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::AmFmBandRange) == 4, "wrong alignment");

/**
 * An entry in regional configuration for DAB.
 *
 * Defines a frequency table row for ensembles.
 */
struct DabTableEntry final {
    /**
     * Channel name, i.e. 5A, 7B.
     *
     * It must match the following regular expression:
     * /^[A-Z0-9][A-Z0-9 ]{0,5}[A-Z0-9]$/ (2-7 uppercase alphanumeric characters
     * without spaces allowed at the beginning nor end).
     */
    ::android::hardware::hidl_string label __attribute__ ((aligned(8)));
    /**
     * Frequency, in kHz.
     */
    uint32_t frequency __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::DabTableEntry, label) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::DabTableEntry, frequency) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::DabTableEntry) == 24, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::DabTableEntry) == 8, "wrong alignment");

/**
 * Properties of a given broadcast radio module.
 */
struct Properties final {
    /**
     * A company name who made the radio module. Must be a valid, registered
     * name of the company itself.
     *
     * It must be opaque to the Android framework.
     */
    ::android::hardware::hidl_string maker __attribute__ ((aligned(8)));
    /**
     * A product name. Must be unique within the company.
     *
     * It must be opaque to the Android framework.
     */
    ::android::hardware::hidl_string product __attribute__ ((aligned(8)));
    /**
     * Version of the hardware module.
     *
     * It must be opaque to the Android framework.
     */
    ::android::hardware::hidl_string version __attribute__ ((aligned(8)));
    /**
     * Hardware serial number (for subscription services).
     *
     * It must be opaque to the Android framework.
     */
    ::android::hardware::hidl_string serial __attribute__ ((aligned(8)));
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
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue> vendorInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Properties, maker) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Properties, product) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Properties, version) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Properties, serial) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Properties, supportedIdentifierTypes) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Properties, vendorInfo) == 80, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::Properties) == 96, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::Properties) == 8, "wrong alignment");

/**
 * A single program identifier component, i.e. frequency or channel ID.
 */
struct ProgramIdentifier final {
    /**
     * Maps to IdentifierType enum. The enum may be extended in future versions
     * of the HAL. Values out of the enum range must not be used when writing
     * and ignored when reading.
     */
    uint32_t type __attribute__ ((aligned(4)));
    /**
     * The uint64_t value field holds the value in format described in comments
     * for IdentifierType enum.
     */
    uint64_t value __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramIdentifier, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramIdentifier, value) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::ProgramIdentifier) == 16, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::ProgramIdentifier) == 8, "wrong alignment");

/**
 * A set of identifiers necessary to tune to a given station.
 *
 * This can hold a combination of various identifiers, like:
 * - AM/FM frequency,
 * - HD Radio subchannel,
 * - DAB service ID.
 *
 * The type of radio technology is determined by the primary identifier - if the
 * primary identifier is for DAB, the program is DAB. However, a program of a
 * specific radio technology may have additional secondary identifiers for other
 * technologies, i.e. a satellite program may have FM fallback frequency,
 * if a station broadcasts both via satellite and FM.
 *
 * The identifiers from VENDOR_START..VENDOR_END range have limited
 * serialization capabilities: they are serialized locally, but ignored by the
 * cloud services. If a program has primary id from vendor range, it's not
 * synchronized with other devices at all.
 */
struct ProgramSelector final {
    /**
     * Primary program identifier.
     *
     * This identifier uniquely identifies a station and can be used for
     * equality check.
     *
     * It can hold only a subset of identifier types, one per each
     * radio technology:
     *  - analogue AM/FM: AMFM_FREQUENCY;
     *  - FM RDS: RDS_PI;
     *  - HD Radio: HD_STATION_ID_EXT;
     *  - DAB: DAB_SID_EXT;
     *  - Digital Radio Mondiale: DRMO_SERVICE_ID;
     *  - SiriusXM: SXM_SERVICE_ID;
     *  - vendor-specific: VENDOR_START..VENDOR_END.
     *
     * The list may change in future versions, so the implementation must obey,
     * but not rely on it.
     */
    ::android::hardware::broadcastradio::V2_0::ProgramIdentifier primaryId __attribute__ ((aligned(8)));
    /**
     * Secondary program identifiers.
     *
     * These identifiers are supplementary and can speed up tuning process,
     * but the primary ID must be sufficient (i.e. RDS PI is enough to select
     * a station from the list after a full band scan).
     *
     * Two selectors with different secondary IDs, but the same primary ID are
     * considered equal. In particular, secondary IDs vector may get updated for
     * an entry on the program list (ie. when a better frequency for a given
     * station is found).
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::ProgramIdentifier> secondaryIds __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramSelector, primaryId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramSelector, secondaryIds) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::ProgramSelector) == 32, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::ProgramSelector) == 8, "wrong alignment");

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
    /**
     * Tuned to a program (not playing static).
     *
     * It's the same condition that would stop a seek operation
     * (ie: ITunerSession::scan()).
     *
     * By definition, this flag must be set for all items on the program list.
     */
    TUNED = 16u /* 1 << 4 */,
    /**
     * Audio stream is MONO if this bit is not set.
     */
    STEREO = 32u /* 1 << 5 */,
};

/**
 * Program (channel, station) information.
 *
 * Carries both user-visible information (like station name) and technical
 * details (tuning selector).
 */
struct ProgramInfo final {
    /**
     * An identifier used to point at the program (primarily to tune to it).
     *
     * This field is required - its type field must not be set to
     * IdentifierType::INVALID.
     */
    ::android::hardware::broadcastradio::V2_0::ProgramSelector selector __attribute__ ((aligned(8)));
    /**
     * Identifier currently used for program selection.
     *
     * It allows to determine which technology is currently used for reception.
     *
     * Some program selectors contain tuning information for different radio
     * technologies (i.e. FM RDS and DAB). For example, user may tune using
     * a ProgramSelector with RDS_PI primary identifier, but the tuner hardware
     * may choose to use DAB technology to make actual tuning. This identifier
     * must reflect that.
     *
     * This field is required for currently tuned program only.
     * For all other items on the program list, its type field must be
     * initialized to IdentifierType::INVALID.
     *
     * Only primary identifiers for a given radio technology are valid:
     *  - AMFM_FREQUENCY for analog AM/FM;
     *  - RDS_PI for FM RDS;
     *  - HD_STATION_ID_EXT;
     *  - DAB_SID_EXT;
     *  - DRMO_SERVICE_ID;
     *  - SXM_SERVICE_ID;
     *  - VENDOR_*;
     *  - more might come in next minor versions of this HAL.
     */
    ::android::hardware::broadcastradio::V2_0::ProgramIdentifier logicallyTunedTo __attribute__ ((aligned(8)));
    /**
     * Identifier currently used by hardware to physically tune to a channel.
     *
     * Some radio technologies broadcast the same program on multiple channels,
     * i.e. with RDS AF the same program may be broadcasted on multiple
     * alternative frequencies; the same DAB program may be broadcast on
     * multiple ensembles. This identifier points to the channel to which the
     * radio hardware is physically tuned to.
     *
     * This field is required for currently tuned program only.
     * For all other items on the program list, its type field must be
     * initialized to IdentifierType::INVALID.
     *
     * Only physical identifiers are valid:
     *  - AMFM_FREQUENCY;
     *  - DAB_ENSEMBLE;
     *  - DRMO_FREQUENCY;
     *  - SXM_CHANNEL;
     *  - VENDOR_*;
     *  - more might come in next minor versions of this HAL.
     */
    ::android::hardware::broadcastradio::V2_0::ProgramIdentifier physicallyTunedTo __attribute__ ((aligned(8)));
    /**
     * Primary identifiers of related contents.
     *
     * Some radio technologies provide pointers to other programs that carry
     * related content (i.e. DAB soft-links). This field is a list of pointers
     * to other programs on the program list.
     *
     * This is not a list of programs that carry the same content (i.e.
     * DAB hard-links, RDS AF). Switching to programs from this list usually
     * require user action.
     *
     * Please note, that these identifiers do not have to exist on the program
     * list - i.e. DAB tuner may provide information on FM RDS alternatives
     * despite not supporting FM RDS. If the system has multiple tuners, another
     * one may have it on its list.
     *
     * This field is optional (can be empty).
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::ProgramIdentifier> relatedContent __attribute__ ((aligned(8)));
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::ProgramInfoFlags> infoFlags __attribute__ ((aligned(4)));
    /**
     * Signal quality measured in 0% to 100% range to be shown in the UI.
     *
     * The purpose of this field is primarily informative, must not be used to
     * determine to which frequency should it tune to.
     */
    uint32_t signalQuality __attribute__ ((aligned(4)));
    /**
     * Program metadata (station name, PTY, song title).
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::Metadata> metadata __attribute__ ((aligned(8)));
    /**
     * Vendor-specific information.
     *
     * It may be used for extra features, not supported by the platform,
     * for example: paid-service=true; bitrate=320kbps.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue> vendorInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, selector) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, logicallyTunedTo) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, physicallyTunedTo) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, relatedContent) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, infoFlags) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, signalQuality) == 84, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, metadata) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramInfo, vendorInfo) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::ProgramInfo) == 120, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::ProgramInfo) == 8, "wrong alignment");

/**
 * Type of program identifier component.
 *
 * Each identifier type corresponds to exactly one radio technology,
 * i.e. DAB_ENSEMBLE is specifically for DAB.
 *
 * VENDOR identifier types must be opaque to the framework.
 *
 * The value format for each (but VENDOR_*) identifier is strictly defined
 * to maintain interoperability between devices made by different vendors.
 *
 * All other values are reserved for future use.
 * Values not matching any enumerated constant must be ignored.
 */
enum class IdentifierType : uint32_t {
    /**
     * Primary/secondary identifier for vendor-specific radio technology.
     * The value format is determined by a vendor.
     *
     * The vendor identifiers have limited serialization capabilities - see
     * ProgramSelector description.
     */
    VENDOR_START = 1000u,
    /**
     * See VENDOR_START
     */
    VENDOR_END = 1999u,
    INVALID = 0u,
    /**
     * Primary identifier for analogue (without RDS) AM/FM stations:
     * frequency in kHz.
     *
     * This identifier also contains band information:
     *  - <500kHz: AM LW;
     *  - 500kHz - 1705kHz: AM MW;
     *  - 1.71MHz - 30MHz: AM SW;
     *  - >60MHz: FM.
     */
    AMFM_FREQUENCY = 1u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.INVALID implicitly + 1 */,
    /**
     * 16bit primary identifier for FM RDS station.
     */
    RDS_PI = 2u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.AMFM_FREQUENCY implicitly + 1 */,
    /**
     * 64bit compound primary identifier for HD Radio.
     *
     * Consists of (from the LSB):
     * - 32bit: Station ID number;
     * - 4bit: HD Radio subchannel;
     * - 18bit: AMFM_FREQUENCY.
     *
     * While station ID number should be unique globally, it sometimes get
     * abused by broadcasters (i.e. not being set at all). To ensure local
     * uniqueness, AMFM_FREQUENCY was added here. Global uniqueness is
     * a best-effort - see HD_STATION_NAME.
     *
     * HD Radio subchannel is a value in range 0-7.
     * This index is 0-based (where 0 is MPS and 1..7 are SPS),
     * as opposed to HD Radio standard (where it's 1-based).
     *
     * The remaining bits should be set to zeros when writing on the chip side
     * and ignored when read.
     */
    HD_STATION_ID_EXT = 3u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.RDS_PI implicitly + 1 */,
    /**
     * 64bit additional identifier for HD Radio.
     *
     * Due to Station ID abuse, some HD_STATION_ID_EXT identifiers may be not
     * globally unique. To provide a best-effort solution, a short version of
     * station name may be carried as additional identifier and may be used
     * by the tuner hardware to double-check tuning.
     *
     * The name is limited to the first 8 A-Z0-9 characters (lowercase letters
     * must be converted to uppercase). Encoded in little-endian ASCII:
     * the first character of the name is the LSB.
     *
     * For example: "Abc" is encoded as 0x434241.
     */
    HD_STATION_NAME = 4u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.HD_STATION_ID_EXT implicitly + 1 */,
    /**
     * 28bit compound primary identifier for Digital Audio Broadcasting.
     *
     * Consists of (from the LSB):
     * - 16bit: SId;
     * - 8bit: ECC code;
     * - 4bit: SCIdS.
     *
     * SCIdS (Service Component Identifier within the Service) value
     * of 0 represents the main service, while 1 and above represents
     * secondary services.
     *
     * The remaining bits should be set to zeros when writing on the chip side
     * and ignored when read.
     */
    DAB_SID_EXT = 5u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.HD_STATION_NAME implicitly + 1 */,
    /**
     * 16bit
     */
    DAB_ENSEMBLE = 6u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.DAB_SID_EXT implicitly + 1 */,
    /**
     * 12bit
     */
    DAB_SCID = 7u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.DAB_ENSEMBLE implicitly + 1 */,
    /**
     * kHz (see AMFM_FREQUENCY)
     */
    DAB_FREQUENCY = 8u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.DAB_SCID implicitly + 1 */,
    /**
     * 24bit primary identifier for Digital Radio Mondiale.
     */
    DRMO_SERVICE_ID = 9u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.DAB_FREQUENCY implicitly + 1 */,
    /**
     * kHz (see AMFM_FREQUENCY)
     */
    DRMO_FREQUENCY = 10u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.DRMO_SERVICE_ID implicitly + 1 */,
    /**
     * 32bit primary identifier for SiriusXM Satellite Radio.
     */
    SXM_SERVICE_ID = 12u /* DRMO_FREQUENCY + 2 */,
    /**
     * 0-999 range
     */
    SXM_CHANNEL = 13u /* ::android::hardware::broadcastradio::V2_0::IdentifierType.SXM_SERVICE_ID implicitly + 1 */,
};

enum class MetadataKey : int32_t {
    /**
     * RDS PS (string)
     */
    RDS_PS = 1,
    /**
     * RDS PTY (uint8_t)
     */
    RDS_PTY = 2 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.RDS_PS implicitly + 1 */,
    /**
     * RBDS PTY (uint8_t)
     */
    RBDS_PTY = 3 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.RDS_PTY implicitly + 1 */,
    /**
     * RDS RT (string)
     */
    RDS_RT = 4 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.RBDS_PTY implicitly + 1 */,
    /**
     * Song title (string)
     */
    SONG_TITLE = 5 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.RDS_RT implicitly + 1 */,
    /**
     * Artist name (string)
     */
    SONG_ARTIST = 6 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.SONG_TITLE implicitly + 1 */,
    /**
     * Album name (string)
     */
    SONG_ALBUM = 7 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.SONG_ARTIST implicitly + 1 */,
    /**
     * Station icon (uint32_t, see IBroadcastRadio::getImage)
     */
    STATION_ICON = 8 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.SONG_ALBUM implicitly + 1 */,
    /**
     * Album art (uint32_t, see IBroadcastRadio::getImage)
     */
    ALBUM_ART = 9 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.STATION_ICON implicitly + 1 */,
    /**
     * Station name.
     *
     * This is a generic field to cover any radio technology.
     *
     * If the PROGRAM_NAME has the same content as DAB_*_NAME or RDS_PS,
     * it may not be present, to preserve space - framework must repopulate
     * it on the client side.
     */
    PROGRAM_NAME = 10 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.ALBUM_ART implicitly + 1 */,
    /**
     * DAB ensemble name (string)
     */
    DAB_ENSEMBLE_NAME = 11 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.PROGRAM_NAME implicitly + 1 */,
    /**
     * DAB ensemble name abbreviated (string).
     *
     * The string must be up to 8 characters long.
     *
     * If the short variant is present, the long (DAB_ENSEMBLE_NAME) one must be
     * present as well.
     */
    DAB_ENSEMBLE_NAME_SHORT = 12 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.DAB_ENSEMBLE_NAME implicitly + 1 */,
    /**
     * DAB service name (string)
     */
    DAB_SERVICE_NAME = 13 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.DAB_ENSEMBLE_NAME_SHORT implicitly + 1 */,
    /**
     * DAB service name abbreviated (see DAB_ENSEMBLE_NAME_SHORT) (string)
     */
    DAB_SERVICE_NAME_SHORT = 14 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.DAB_SERVICE_NAME implicitly + 1 */,
    /**
     * DAB component name (string)
     */
    DAB_COMPONENT_NAME = 15 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.DAB_SERVICE_NAME_SHORT implicitly + 1 */,
    /**
     * DAB component name abbreviated (see DAB_ENSEMBLE_NAME_SHORT) (string)
     */
    DAB_COMPONENT_NAME_SHORT = 16 /* ::android::hardware::broadcastradio::V2_0::MetadataKey.DAB_COMPONENT_NAME implicitly + 1 */,
};

/**
 * An element of metadata vector.
 *
 * Contains one of the entries explained in MetadataKey.
 *
 * Depending on a type described in the comment for a specific key, either the
 * intValue or stringValue field must be populated.
 */
struct Metadata final {
    /**
     * Maps to MetadataKey enum. The enum may be extended in future versions
     * of the HAL. Values out of the enum range must not be used when writing
     * and ignored when reading.
     */
    uint32_t key __attribute__ ((aligned(4)));
    int64_t intValue __attribute__ ((aligned(8)));
    ::android::hardware::hidl_string stringValue __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Metadata, key) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Metadata, intValue) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Metadata, stringValue) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::Metadata) == 32, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::Metadata) == 8, "wrong alignment");

/**
 * An update packet of the program list.
 *
 * The order of entries in the vectors is unspecified.
 */
struct ProgramListChunk final {
    /**
     * Treats all previously added entries as removed.
     *
     * This is meant to save binder transaction bandwidth on 'removed' vector
     * and provide a clear empty state.
     *
     * If set, 'removed' vector must be empty.
     *
     * The client may wait with taking action on this until it received the
     * chunk with complete flag set (to avoid part of stations temporarily
     * disappearing from the list).
     */
    bool purge __attribute__ ((aligned(1)));
    /**
     * If false, it means there are still programs not transmitted,
     * due for transmission in following updates.
     *
     * Used by UIs that wait for complete list instead of displaying
     * programs while scanning.
     *
     * After the whole channel range was scanned and all discovered programs
     * were transmitted, the last chunk must have set this flag to true.
     * This must happen within Constants::LIST_COMPLETE_TIMEOUT_MS from the
     * startProgramListUpdates call. If it doesn't, client may assume the tuner
     * came into a bad state and display error message.
     */
    bool complete __attribute__ ((aligned(1)));
    /**
     * Added or modified program list entries.
     *
     * Two entries with the same primaryId (ProgramSelector member)
     * are considered the same.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::ProgramInfo> modified __attribute__ ((aligned(8)));
    /**
     * Removed program list entries.
     *
     * Contains primaryId (ProgramSelector member) of a program to remove.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::ProgramIdentifier> removed __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramListChunk, purge) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramListChunk, complete) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramListChunk, modified) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramListChunk, removed) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::ProgramListChunk) == 40, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::ProgramListChunk) == 8, "wrong alignment");

/**
 * Large-grain filter to the program list.
 *
 * This is meant to reduce binder transaction bandwidth, not for fine-grained
 * filtering user might expect.
 *
 * The filter is designed as conjunctive normal form: the entry that passes the
 * filter must satisfy all the clauses (members of this struct). Vector clauses
 * are disjunctions of literals. In other words, there is AND between each
 * high-level group and OR inside it.
 */
struct ProgramFilter final {
    /**
     * List of identifier types that satisfy the filter.
     *
     * If the program list entry contains at least one identifier of the type
     * listed, it satisfies this condition.
     *
     * Empty list means no filtering on identifier type.
     */
    ::android::hardware::hidl_vec<uint32_t> identifierTypes __attribute__ ((aligned(8)));
    /**
     * List of identifiers that satisfy the filter.
     *
     * If the program list entry contains at least one listed identifier,
     * it satisfies this condition.
     *
     * Empty list means no filtering on identifier.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::ProgramIdentifier> identifiers __attribute__ ((aligned(8)));
    /**
     * Includes non-tunable entries that define tree structure on the
     * program list (i.e. DAB ensembles).
     */
    bool includeCategories __attribute__ ((aligned(1)));
    /**
     * Disable updates on entry modifications.
     *
     * If true, 'modified' vector of ProgramListChunk must contain list
     * additions only. Once the program is added to the list, it's not
     * updated anymore.
     */
    bool excludeModifications __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramFilter, identifierTypes) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramFilter, identifiers) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramFilter, includeCategories) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::ProgramFilter, excludeModifications) == 33, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::ProgramFilter) == 40, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::ProgramFilter) == 8, "wrong alignment");

/**
 * Type of an announcement.
 *
 * It maps to different announcement types per each radio technology.
 */
enum class AnnouncementType : uint8_t {
    /**
     * DAB alarm, RDS emergency program type (PTY 31).
     */
    EMERGENCY = 1,
    /**
     * DAB warning.
     */
    WARNING = 2 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.EMERGENCY implicitly + 1 */,
    /**
     * DAB road traffic, RDS TA, HD Radio transportation.
     */
    TRAFFIC = 3 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.WARNING implicitly + 1 */,
    /**
     * Weather.
     */
    WEATHER = 4 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.TRAFFIC implicitly + 1 */,
    /**
     * News.
     */
    NEWS = 5 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.WEATHER implicitly + 1 */,
    /**
     * DAB event, special event.
     */
    EVENT = 6 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.NEWS implicitly + 1 */,
    /**
     * DAB sport report, RDS sports.
     */
    SPORT = 7 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.EVENT implicitly + 1 */,
    /**
     * All others.
     */
    MISC = 8 /* ::android::hardware::broadcastradio::V2_0::AnnouncementType.SPORT implicitly + 1 */,
};

/**
 * A pointer to a station broadcasting active announcement.
 */
struct Announcement final {
    /**
     * Program selector to tune to the announcement.
     */
    ::android::hardware::broadcastradio::V2_0::ProgramSelector selector __attribute__ ((aligned(8)));
    /**
     * Announcement type.
     */
    ::android::hardware::broadcastradio::V2_0::AnnouncementType type __attribute__ ((aligned(1)));
    /**
     * Vendor-specific information.
     *
     * It may be used for extra features, not supported by the platform,
     * for example: com.me.hdradio.urgency=100; com.me.hdradio.certainity=50.
     */
    ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue> vendorInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Announcement, selector) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Announcement, type) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::broadcastradio::V2_0::Announcement, vendorInfo) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::broadcastradio::V2_0::Announcement) == 56, "wrong size");
static_assert(__alignof(::android::hardware::broadcastradio::V2_0::Announcement) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::Constants o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Constants o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V2_0::Constants lhs, const ::android::hardware::broadcastradio::V2_0::Constants rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V2_0::Constants rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V2_0::Constants lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V2_0::Constants lhs, const ::android::hardware::broadcastradio::V2_0::Constants rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V2_0::Constants rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V2_0::Constants lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V2_0::Constants e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V2_0::Constants e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::Result o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V2_0::Result lhs, const ::android::hardware::broadcastradio::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V2_0::Result lhs, const ::android::hardware::broadcastradio::V2_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V2_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V2_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V2_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V2_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::ConfigFlag o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::ConfigFlag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V2_0::ConfigFlag lhs, const ::android::hardware::broadcastradio::V2_0::ConfigFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V2_0::ConfigFlag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V2_0::ConfigFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V2_0::ConfigFlag lhs, const ::android::hardware::broadcastradio::V2_0::ConfigFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V2_0::ConfigFlag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V2_0::ConfigFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V2_0::ConfigFlag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V2_0::ConfigFlag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::Rds o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Rds o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::broadcastradio::V2_0::Rds lhs, const ::android::hardware::broadcastradio::V2_0::Rds rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::broadcastradio::V2_0::Rds rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::broadcastradio::V2_0::Rds lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::broadcastradio::V2_0::Rds lhs, const ::android::hardware::broadcastradio::V2_0::Rds rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::broadcastradio::V2_0::Rds rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::broadcastradio::V2_0::Rds lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::broadcastradio::V2_0::Rds e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::broadcastradio::V2_0::Rds e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::Deemphasis o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Deemphasis o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::broadcastradio::V2_0::Deemphasis lhs, const ::android::hardware::broadcastradio::V2_0::Deemphasis rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::broadcastradio::V2_0::Deemphasis rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::broadcastradio::V2_0::Deemphasis lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::broadcastradio::V2_0::Deemphasis lhs, const ::android::hardware::broadcastradio::V2_0::Deemphasis rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::broadcastradio::V2_0::Deemphasis rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::broadcastradio::V2_0::Deemphasis lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::broadcastradio::V2_0::Deemphasis e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::broadcastradio::V2_0::Deemphasis e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& lhs, const ::android::hardware::broadcastradio::V2_0::DabTableEntry& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& lhs, const ::android::hardware::broadcastradio::V2_0::DabTableEntry& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::Properties& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::Properties& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::Properties& lhs, const ::android::hardware::broadcastradio::V2_0::Properties& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::Properties& lhs, const ::android::hardware::broadcastradio::V2_0::Properties& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfo& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfo& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::IdentifierType o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::IdentifierType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V2_0::IdentifierType lhs, const ::android::hardware::broadcastradio::V2_0::IdentifierType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::broadcastradio::V2_0::IdentifierType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::broadcastradio::V2_0::IdentifierType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V2_0::IdentifierType lhs, const ::android::hardware::broadcastradio::V2_0::IdentifierType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::broadcastradio::V2_0::IdentifierType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::broadcastradio::V2_0::IdentifierType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::broadcastradio::V2_0::IdentifierType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::broadcastradio::V2_0::IdentifierType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::MetadataKey o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::MetadataKey o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V2_0::MetadataKey lhs, const ::android::hardware::broadcastradio::V2_0::MetadataKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::broadcastradio::V2_0::MetadataKey rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::broadcastradio::V2_0::MetadataKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V2_0::MetadataKey lhs, const ::android::hardware::broadcastradio::V2_0::MetadataKey rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::broadcastradio::V2_0::MetadataKey rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::broadcastradio::V2_0::MetadataKey lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::broadcastradio::V2_0::MetadataKey e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::broadcastradio::V2_0::MetadataKey e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::Metadata& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::Metadata& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::Metadata& lhs, const ::android::hardware::broadcastradio::V2_0::Metadata& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::Metadata& lhs, const ::android::hardware::broadcastradio::V2_0::Metadata& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& rhs);

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramFilter& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramFilter& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::broadcastradio::V2_0::AnnouncementType o);
static inline void PrintTo(::android::hardware::broadcastradio::V2_0::AnnouncementType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::broadcastradio::V2_0::AnnouncementType lhs, const ::android::hardware::broadcastradio::V2_0::AnnouncementType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::broadcastradio::V2_0::AnnouncementType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::broadcastradio::V2_0::AnnouncementType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::broadcastradio::V2_0::AnnouncementType lhs, const ::android::hardware::broadcastradio::V2_0::AnnouncementType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::broadcastradio::V2_0::AnnouncementType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::broadcastradio::V2_0::AnnouncementType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::broadcastradio::V2_0::AnnouncementType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::broadcastradio::V2_0::AnnouncementType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::Announcement& o);
static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::Announcement& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::Announcement& lhs, const ::android::hardware::broadcastradio::V2_0::Announcement& rhs);
static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::Announcement& lhs, const ::android::hardware::broadcastradio::V2_0::Announcement& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::Constants>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::Constants> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::Constants::INVALID_IMAGE) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Constants::INVALID_IMAGE)) {
        os += (first ? "" : " | ");
        os += "INVALID_IMAGE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Constants::INVALID_IMAGE;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Constants::ANTENNA_DISCONNECTED_TIMEOUT_MS) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Constants::ANTENNA_DISCONNECTED_TIMEOUT_MS)) {
        os += (first ? "" : " | ");
        os += "ANTENNA_DISCONNECTED_TIMEOUT_MS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Constants::ANTENNA_DISCONNECTED_TIMEOUT_MS;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Constants::LIST_COMPLETE_TIMEOUT_MS) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Constants::LIST_COMPLETE_TIMEOUT_MS)) {
        os += (first ? "" : " | ");
        os += "LIST_COMPLETE_TIMEOUT_MS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Constants::LIST_COMPLETE_TIMEOUT_MS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::Constants o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::Constants::INVALID_IMAGE) {
        return "INVALID_IMAGE";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Constants::ANTENNA_DISCONNECTED_TIMEOUT_MS) {
        return "ANTENNA_DISCONNECTED_TIMEOUT_MS";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Constants::LIST_COMPLETE_TIMEOUT_MS) {
        return "LIST_COMPLETE_TIMEOUT_MS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Constants o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::OK) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::OK;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::UNKNOWN_ERROR) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::UNKNOWN_ERROR;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::INTERNAL_ERROR) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::INTERNAL_ERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNAL_ERROR";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::INTERNAL_ERROR;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::INVALID_ARGUMENTS) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::INVALID_ARGUMENTS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENTS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::INVALID_ARGUMENTS;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Result::TIMEOUT) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::Result::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Result::TIMEOUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Result::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Result::INTERNAL_ERROR) {
        return "INTERNAL_ERROR";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Result::INVALID_ARGUMENTS) {
        return "INVALID_ARGUMENTS";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Result::NOT_SUPPORTED) {
        return "NOT_SUPPORTED";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Result::TIMEOUT) {
        return "TIMEOUT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::ConfigFlag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::ConfigFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_MONO) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_MONO)) {
        os += (first ? "" : " | ");
        os += "FORCE_MONO";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_MONO;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_ANALOG) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_ANALOG)) {
        os += (first ? "" : " | ");
        os += "FORCE_ANALOG";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_ANALOG;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_DIGITAL) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_DIGITAL)) {
        os += (first ? "" : " | ");
        os += "FORCE_DIGITAL";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_DIGITAL;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_AF) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_AF)) {
        os += (first ? "" : " | ");
        os += "RDS_AF";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_AF;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_REG) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_REG)) {
        os += (first ? "" : " | ");
        os += "RDS_REG";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_REG;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_LINKING) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_LINKING)) {
        os += (first ? "" : " | ");
        os += "DAB_DAB_LINKING";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_LINKING;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_LINKING) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_LINKING)) {
        os += (first ? "" : " | ");
        os += "DAB_FM_LINKING";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_LINKING;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_SOFT_LINKING) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_SOFT_LINKING)) {
        os += (first ? "" : " | ");
        os += "DAB_DAB_SOFT_LINKING";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_SOFT_LINKING;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_SOFT_LINKING) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_SOFT_LINKING)) {
        os += (first ? "" : " | ");
        os += "DAB_FM_SOFT_LINKING";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_SOFT_LINKING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::ConfigFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_MONO) {
        return "FORCE_MONO";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_ANALOG) {
        return "FORCE_ANALOG";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_DIGITAL) {
        return "FORCE_DIGITAL";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_AF) {
        return "RDS_AF";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_REG) {
        return "RDS_REG";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_LINKING) {
        return "DAB_DAB_LINKING";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_LINKING) {
        return "DAB_FM_LINKING";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_SOFT_LINKING) {
        return "DAB_DAB_SOFT_LINKING";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_SOFT_LINKING) {
        return "DAB_FM_SOFT_LINKING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::ConfigFlag o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& lhs, const ::android::hardware::broadcastradio::V2_0::VendorKeyValue& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::Rds>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::Rds> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::Rds::RDS) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::Rds::RDS)) {
        os += (first ? "" : " | ");
        os += "RDS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Rds::RDS;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Rds::RBDS) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::Rds::RBDS)) {
        os += (first ? "" : " | ");
        os += "RBDS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Rds::RBDS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::Rds o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::Rds::RDS) {
        return "RDS";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Rds::RBDS) {
        return "RBDS";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Rds o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::Deemphasis>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::Deemphasis> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::Deemphasis::D50) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::Deemphasis::D50)) {
        os += (first ? "" : " | ");
        os += "D50";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Deemphasis::D50;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::Deemphasis::D75) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::Deemphasis::D75)) {
        os += (first ? "" : " | ");
        os += "D75";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::Deemphasis::D75;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::Deemphasis o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::Deemphasis::D50) {
        return "D50";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::Deemphasis::D75) {
        return "D75";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::Deemphasis o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ranges = ";
    os += ::android::hardware::toString(o.ranges);
    os += ", .fmDeemphasis = ";
    os += ::android::hardware::broadcastradio::V2_0::toString<::android::hardware::broadcastradio::V2_0::Deemphasis>(o.fmDeemphasis);
    os += ", .fmRds = ";
    os += ::android::hardware::broadcastradio::V2_0::toString<::android::hardware::broadcastradio::V2_0::Rds>(o.fmRds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& rhs) {
    if (lhs.ranges != rhs.ranges) {
        return false;
    }
    if (lhs.fmDeemphasis != rhs.fmDeemphasis) {
        return false;
    }
    if (lhs.fmRds != rhs.fmRds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmRegionConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".lowerBound = ";
    os += ::android::hardware::toString(o.lowerBound);
    os += ", .upperBound = ";
    os += ::android::hardware::toString(o.upperBound);
    os += ", .spacing = ";
    os += ::android::hardware::toString(o.spacing);
    os += ", .scanSpacing = ";
    os += ::android::hardware::toString(o.scanSpacing);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& rhs) {
    if (lhs.lowerBound != rhs.lowerBound) {
        return false;
    }
    if (lhs.upperBound != rhs.upperBound) {
        return false;
    }
    if (lhs.spacing != rhs.spacing) {
        return false;
    }
    if (lhs.scanSpacing != rhs.scanSpacing) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& lhs, const ::android::hardware::broadcastradio::V2_0::AmFmBandRange& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".label = ";
    os += ::android::hardware::toString(o.label);
    os += ", .frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& lhs, const ::android::hardware::broadcastradio::V2_0::DabTableEntry& rhs) {
    if (lhs.label != rhs.label) {
        return false;
    }
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::DabTableEntry& lhs, const ::android::hardware::broadcastradio::V2_0::DabTableEntry& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::Properties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".maker = ";
    os += ::android::hardware::toString(o.maker);
    os += ", .product = ";
    os += ::android::hardware::toString(o.product);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .serial = ";
    os += ::android::hardware::toString(o.serial);
    os += ", .supportedIdentifierTypes = ";
    os += ::android::hardware::toString(o.supportedIdentifierTypes);
    os += ", .vendorInfo = ";
    os += ::android::hardware::toString(o.vendorInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::Properties& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::Properties& lhs, const ::android::hardware::broadcastradio::V2_0::Properties& rhs) {
    if (lhs.maker != rhs.maker) {
        return false;
    }
    if (lhs.product != rhs.product) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.serial != rhs.serial) {
        return false;
    }
    if (lhs.supportedIdentifierTypes != rhs.supportedIdentifierTypes) {
        return false;
    }
    if (lhs.vendorInfo != rhs.vendorInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::Properties& lhs, const ::android::hardware::broadcastradio::V2_0::Properties& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::toString(o.type);
    os += ", .value = ";
    os += ::android::hardware::toString(o.value);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.value != rhs.value) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramIdentifier& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".primaryId = ";
    os += ::android::hardware::broadcastradio::V2_0::toString(o.primaryId);
    os += ", .secondaryIds = ";
    os += ::android::hardware::toString(o.secondaryIds);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& rhs) {
    if (lhs.primaryId != rhs.primaryId) {
        return false;
    }
    if (lhs.secondaryIds != rhs.secondaryIds) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::ProgramInfoFlags>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::ProgramInfoFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::LIVE) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::LIVE)) {
        os += (first ? "" : " | ");
        os += "LIVE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::LIVE;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::MUTED) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::MUTED)) {
        os += (first ? "" : " | ");
        os += "MUTED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::MUTED;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_PROGRAM) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_PROGRAM)) {
        os += (first ? "" : " | ");
        os += "TRAFFIC_PROGRAM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_PROGRAM;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT)) {
        os += (first ? "" : " | ");
        os += "TRAFFIC_ANNOUNCEMENT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TUNED) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TUNED)) {
        os += (first ? "" : " | ");
        os += "TUNED";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TUNED;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::STEREO) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::STEREO)) {
        os += (first ? "" : " | ");
        os += "STEREO";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::STEREO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::LIVE) {
        return "LIVE";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::MUTED) {
        return "MUTED";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_PROGRAM) {
        return "TRAFFIC_PROGRAM";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT) {
        return "TRAFFIC_ANNOUNCEMENT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TUNED) {
        return "TUNED";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::STEREO) {
        return "STEREO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::ProgramInfoFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".selector = ";
    os += ::android::hardware::broadcastradio::V2_0::toString(o.selector);
    os += ", .logicallyTunedTo = ";
    os += ::android::hardware::broadcastradio::V2_0::toString(o.logicallyTunedTo);
    os += ", .physicallyTunedTo = ";
    os += ::android::hardware::broadcastradio::V2_0::toString(o.physicallyTunedTo);
    os += ", .relatedContent = ";
    os += ::android::hardware::toString(o.relatedContent);
    os += ", .infoFlags = ";
    os += ::android::hardware::broadcastradio::V2_0::toString<::android::hardware::broadcastradio::V2_0::ProgramInfoFlags>(o.infoFlags);
    os += ", .signalQuality = ";
    os += ::android::hardware::toString(o.signalQuality);
    os += ", .metadata = ";
    os += ::android::hardware::toString(o.metadata);
    os += ", .vendorInfo = ";
    os += ::android::hardware::toString(o.vendorInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfo& rhs) {
    if (lhs.selector != rhs.selector) {
        return false;
    }
    if (lhs.logicallyTunedTo != rhs.logicallyTunedTo) {
        return false;
    }
    if (lhs.physicallyTunedTo != rhs.physicallyTunedTo) {
        return false;
    }
    if (lhs.relatedContent != rhs.relatedContent) {
        return false;
    }
    if (lhs.infoFlags != rhs.infoFlags) {
        return false;
    }
    if (lhs.signalQuality != rhs.signalQuality) {
        return false;
    }
    if (lhs.metadata != rhs.metadata) {
        return false;
    }
    if (lhs.vendorInfo != rhs.vendorInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::IdentifierType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::IdentifierType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_START) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_START)) {
        os += (first ? "" : " | ");
        os += "VENDOR_START";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_START;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_END) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_END)) {
        os += (first ? "" : " | ");
        os += "VENDOR_END";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_END;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::INVALID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::INVALID)) {
        os += (first ? "" : " | ");
        os += "INVALID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::INVALID;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::AMFM_FREQUENCY) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::AMFM_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "AMFM_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::AMFM_FREQUENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::RDS_PI) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::RDS_PI)) {
        os += (first ? "" : " | ");
        os += "RDS_PI";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::RDS_PI;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_ID_EXT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_ID_EXT)) {
        os += (first ? "" : " | ");
        os += "HD_STATION_ID_EXT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_ID_EXT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_NAME) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_NAME)) {
        os += (first ? "" : " | ");
        os += "HD_STATION_NAME";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_NAME;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SID_EXT) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SID_EXT)) {
        os += (first ? "" : " | ");
        os += "DAB_SID_EXT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SID_EXT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_ENSEMBLE) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_ENSEMBLE)) {
        os += (first ? "" : " | ");
        os += "DAB_ENSEMBLE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_ENSEMBLE;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SCID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SCID)) {
        os += (first ? "" : " | ");
        os += "DAB_SCID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SCID;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_FREQUENCY) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "DAB_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_FREQUENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_SERVICE_ID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_SERVICE_ID)) {
        os += (first ? "" : " | ");
        os += "DRMO_SERVICE_ID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_SERVICE_ID;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_FREQUENCY) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "DRMO_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_FREQUENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_SERVICE_ID) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_SERVICE_ID)) {
        os += (first ? "" : " | ");
        os += "SXM_SERVICE_ID";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_SERVICE_ID;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_CHANNEL) == static_cast<uint32_t>(::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "SXM_CHANNEL";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_CHANNEL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::IdentifierType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_START) {
        return "VENDOR_START";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_END) {
        return "VENDOR_END";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::INVALID) {
        return "INVALID";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::AMFM_FREQUENCY) {
        return "AMFM_FREQUENCY";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::RDS_PI) {
        return "RDS_PI";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_ID_EXT) {
        return "HD_STATION_ID_EXT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_NAME) {
        return "HD_STATION_NAME";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SID_EXT) {
        return "DAB_SID_EXT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_ENSEMBLE) {
        return "DAB_ENSEMBLE";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SCID) {
        return "DAB_SCID";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_FREQUENCY) {
        return "DAB_FREQUENCY";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_SERVICE_ID) {
        return "DRMO_SERVICE_ID";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_FREQUENCY) {
        return "DRMO_FREQUENCY";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_SERVICE_ID) {
        return "SXM_SERVICE_ID";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_CHANNEL) {
        return "SXM_CHANNEL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::IdentifierType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::MetadataKey>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::MetadataKey> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PS) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PS)) {
        os += (first ? "" : " | ");
        os += "RDS_PS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PS;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PTY) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PTY)) {
        os += (first ? "" : " | ");
        os += "RDS_PTY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PTY;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::RBDS_PTY) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::RBDS_PTY)) {
        os += (first ? "" : " | ");
        os += "RBDS_PTY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::RBDS_PTY;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_RT) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_RT)) {
        os += (first ? "" : " | ");
        os += "RDS_RT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_RT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_TITLE) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_TITLE)) {
        os += (first ? "" : " | ");
        os += "SONG_TITLE";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_TITLE;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ARTIST) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ARTIST)) {
        os += (first ? "" : " | ");
        os += "SONG_ARTIST";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ARTIST;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ALBUM) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ALBUM)) {
        os += (first ? "" : " | ");
        os += "SONG_ALBUM";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ALBUM;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::STATION_ICON) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::STATION_ICON)) {
        os += (first ? "" : " | ");
        os += "STATION_ICON";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::STATION_ICON;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::ALBUM_ART) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::ALBUM_ART)) {
        os += (first ? "" : " | ");
        os += "ALBUM_ART";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::ALBUM_ART;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::PROGRAM_NAME) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::PROGRAM_NAME)) {
        os += (first ? "" : " | ");
        os += "PROGRAM_NAME";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::PROGRAM_NAME;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME)) {
        os += (first ? "" : " | ");
        os += "DAB_ENSEMBLE_NAME";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME_SHORT) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME_SHORT)) {
        os += (first ? "" : " | ");
        os += "DAB_ENSEMBLE_NAME_SHORT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME_SHORT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME)) {
        os += (first ? "" : " | ");
        os += "DAB_SERVICE_NAME";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME_SHORT) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME_SHORT)) {
        os += (first ? "" : " | ");
        os += "DAB_SERVICE_NAME_SHORT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME_SHORT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME)) {
        os += (first ? "" : " | ");
        os += "DAB_COMPONENT_NAME";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME_SHORT) == static_cast<int32_t>(::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME_SHORT)) {
        os += (first ? "" : " | ");
        os += "DAB_COMPONENT_NAME_SHORT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME_SHORT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::MetadataKey o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PS) {
        return "RDS_PS";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PTY) {
        return "RDS_PTY";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::RBDS_PTY) {
        return "RBDS_PTY";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_RT) {
        return "RDS_RT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_TITLE) {
        return "SONG_TITLE";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ARTIST) {
        return "SONG_ARTIST";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ALBUM) {
        return "SONG_ALBUM";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::STATION_ICON) {
        return "STATION_ICON";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::ALBUM_ART) {
        return "ALBUM_ART";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::PROGRAM_NAME) {
        return "PROGRAM_NAME";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME) {
        return "DAB_ENSEMBLE_NAME";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME_SHORT) {
        return "DAB_ENSEMBLE_NAME_SHORT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME) {
        return "DAB_SERVICE_NAME";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME_SHORT) {
        return "DAB_SERVICE_NAME_SHORT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME) {
        return "DAB_COMPONENT_NAME";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME_SHORT) {
        return "DAB_COMPONENT_NAME_SHORT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::MetadataKey o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::Metadata& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".key = ";
    os += ::android::hardware::toString(o.key);
    os += ", .intValue = ";
    os += ::android::hardware::toString(o.intValue);
    os += ", .stringValue = ";
    os += ::android::hardware::toString(o.stringValue);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::Metadata& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::Metadata& lhs, const ::android::hardware::broadcastradio::V2_0::Metadata& rhs) {
    if (lhs.key != rhs.key) {
        return false;
    }
    if (lhs.intValue != rhs.intValue) {
        return false;
    }
    if (lhs.stringValue != rhs.stringValue) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::Metadata& lhs, const ::android::hardware::broadcastradio::V2_0::Metadata& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".purge = ";
    os += ::android::hardware::toString(o.purge);
    os += ", .complete = ";
    os += ::android::hardware::toString(o.complete);
    os += ", .modified = ";
    os += ::android::hardware::toString(o.modified);
    os += ", .removed = ";
    os += ::android::hardware::toString(o.removed);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& rhs) {
    if (lhs.purge != rhs.purge) {
        return false;
    }
    if (lhs.complete != rhs.complete) {
        return false;
    }
    if (lhs.modified != rhs.modified) {
        return false;
    }
    if (lhs.removed != rhs.removed) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".identifierTypes = ";
    os += ::android::hardware::toString(o.identifierTypes);
    os += ", .identifiers = ";
    os += ::android::hardware::toString(o.identifiers);
    os += ", .includeCategories = ";
    os += ::android::hardware::toString(o.includeCategories);
    os += ", .excludeModifications = ";
    os += ::android::hardware::toString(o.excludeModifications);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramFilter& rhs) {
    if (lhs.identifierTypes != rhs.identifierTypes) {
        return false;
    }
    if (lhs.identifiers != rhs.identifiers) {
        return false;
    }
    if (lhs.includeCategories != rhs.includeCategories) {
        return false;
    }
    if (lhs.excludeModifications != rhs.excludeModifications) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& lhs, const ::android::hardware::broadcastradio::V2_0::ProgramFilter& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::broadcastradio::V2_0::AnnouncementType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::broadcastradio::V2_0::AnnouncementType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::EMERGENCY) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::EMERGENCY)) {
        os += (first ? "" : " | ");
        os += "EMERGENCY";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::EMERGENCY;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::WARNING) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::WARNING)) {
        os += (first ? "" : " | ");
        os += "WARNING";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::WARNING;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::TRAFFIC) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::TRAFFIC)) {
        os += (first ? "" : " | ");
        os += "TRAFFIC";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::TRAFFIC;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::WEATHER) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::WEATHER)) {
        os += (first ? "" : " | ");
        os += "WEATHER";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::WEATHER;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::NEWS) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::NEWS)) {
        os += (first ? "" : " | ");
        os += "NEWS";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::NEWS;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::EVENT) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::EVENT)) {
        os += (first ? "" : " | ");
        os += "EVENT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::EVENT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::SPORT) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::SPORT)) {
        os += (first ? "" : " | ");
        os += "SPORT";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::SPORT;
    }
    if ((o & ::android::hardware::broadcastradio::V2_0::AnnouncementType::MISC) == static_cast<uint8_t>(::android::hardware::broadcastradio::V2_0::AnnouncementType::MISC)) {
        os += (first ? "" : " | ");
        os += "MISC";
        first = false;
        flipped |= ::android::hardware::broadcastradio::V2_0::AnnouncementType::MISC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::broadcastradio::V2_0::AnnouncementType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::EMERGENCY) {
        return "EMERGENCY";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::WARNING) {
        return "WARNING";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::TRAFFIC) {
        return "TRAFFIC";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::WEATHER) {
        return "WEATHER";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::NEWS) {
        return "NEWS";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::EVENT) {
        return "EVENT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::SPORT) {
        return "SPORT";
    }
    if (o == ::android::hardware::broadcastradio::V2_0::AnnouncementType::MISC) {
        return "MISC";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::broadcastradio::V2_0::AnnouncementType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::broadcastradio::V2_0::Announcement& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".selector = ";
    os += ::android::hardware::broadcastradio::V2_0::toString(o.selector);
    os += ", .type = ";
    os += ::android::hardware::broadcastradio::V2_0::toString(o.type);
    os += ", .vendorInfo = ";
    os += ::android::hardware::toString(o.vendorInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::broadcastradio::V2_0::Announcement& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::broadcastradio::V2_0::Announcement& lhs, const ::android::hardware::broadcastradio::V2_0::Announcement& rhs) {
    if (lhs.selector != rhs.selector) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.vendorInfo != rhs.vendorInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::broadcastradio::V2_0::Announcement& lhs, const ::android::hardware::broadcastradio::V2_0::Announcement& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_0
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::Constants, 3> hidl_enum_values<::android::hardware::broadcastradio::V2_0::Constants> = {
    ::android::hardware::broadcastradio::V2_0::Constants::INVALID_IMAGE,
    ::android::hardware::broadcastradio::V2_0::Constants::ANTENNA_DISCONNECTED_TIMEOUT_MS,
    ::android::hardware::broadcastradio::V2_0::Constants::LIST_COMPLETE_TIMEOUT_MS,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::Result, 7> hidl_enum_values<::android::hardware::broadcastradio::V2_0::Result> = {
    ::android::hardware::broadcastradio::V2_0::Result::OK,
    ::android::hardware::broadcastradio::V2_0::Result::UNKNOWN_ERROR,
    ::android::hardware::broadcastradio::V2_0::Result::INTERNAL_ERROR,
    ::android::hardware::broadcastradio::V2_0::Result::INVALID_ARGUMENTS,
    ::android::hardware::broadcastradio::V2_0::Result::INVALID_STATE,
    ::android::hardware::broadcastradio::V2_0::Result::NOT_SUPPORTED,
    ::android::hardware::broadcastradio::V2_0::Result::TIMEOUT,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::ConfigFlag, 9> hidl_enum_values<::android::hardware::broadcastradio::V2_0::ConfigFlag> = {
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_MONO,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_ANALOG,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::FORCE_DIGITAL,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_AF,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::RDS_REG,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_LINKING,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_LINKING,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_DAB_SOFT_LINKING,
    ::android::hardware::broadcastradio::V2_0::ConfigFlag::DAB_FM_SOFT_LINKING,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::Rds, 2> hidl_enum_values<::android::hardware::broadcastradio::V2_0::Rds> = {
    ::android::hardware::broadcastradio::V2_0::Rds::RDS,
    ::android::hardware::broadcastradio::V2_0::Rds::RBDS,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::Deemphasis, 2> hidl_enum_values<::android::hardware::broadcastradio::V2_0::Deemphasis> = {
    ::android::hardware::broadcastradio::V2_0::Deemphasis::D50,
    ::android::hardware::broadcastradio::V2_0::Deemphasis::D75,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::ProgramInfoFlags, 6> hidl_enum_values<::android::hardware::broadcastradio::V2_0::ProgramInfoFlags> = {
    ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::LIVE,
    ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::MUTED,
    ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_PROGRAM,
    ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TRAFFIC_ANNOUNCEMENT,
    ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::TUNED,
    ::android::hardware::broadcastradio::V2_0::ProgramInfoFlags::STEREO,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::IdentifierType, 15> hidl_enum_values<::android::hardware::broadcastradio::V2_0::IdentifierType> = {
    ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_START,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::VENDOR_END,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::INVALID,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::AMFM_FREQUENCY,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::RDS_PI,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_ID_EXT,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::HD_STATION_NAME,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SID_EXT,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_ENSEMBLE,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_SCID,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::DAB_FREQUENCY,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_SERVICE_ID,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::DRMO_FREQUENCY,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_SERVICE_ID,
    ::android::hardware::broadcastradio::V2_0::IdentifierType::SXM_CHANNEL,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::MetadataKey, 16> hidl_enum_values<::android::hardware::broadcastradio::V2_0::MetadataKey> = {
    ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PS,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_PTY,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::RBDS_PTY,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::RDS_RT,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_TITLE,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ARTIST,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::SONG_ALBUM,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::STATION_ICON,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::ALBUM_ART,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::PROGRAM_NAME,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_ENSEMBLE_NAME_SHORT,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_SERVICE_NAME_SHORT,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME,
    ::android::hardware::broadcastradio::V2_0::MetadataKey::DAB_COMPONENT_NAME_SHORT,
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
template<> inline constexpr std::array<::android::hardware::broadcastradio::V2_0::AnnouncementType, 8> hidl_enum_values<::android::hardware::broadcastradio::V2_0::AnnouncementType> = {
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::EMERGENCY,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::WARNING,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::TRAFFIC,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::WEATHER,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::NEWS,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::EVENT,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::SPORT,
    ::android::hardware::broadcastradio::V2_0::AnnouncementType::MISC,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_TYPES_H
