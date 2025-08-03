#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_TYPES_H

#include <android/hidl/safe_union/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class FrontendType : uint32_t;
enum class FrontendInnerFec : uint64_t;
enum class FrontendAtscModulation : uint32_t;
struct FrontendAtscSettings;
struct FrontendAtscCapabilities;
enum class FrontendAtsc3Modulation : uint32_t;
enum class FrontendAtsc3Bandwidth : uint32_t;
enum class FrontendAtsc3TimeInterleaveMode : uint32_t;
enum class FrontendAtsc3CodeRate : uint32_t;
enum class FrontendAtsc3Fec : uint32_t;
enum class FrontendAtsc3DemodOutputFormat : uint8_t;
struct FrontendAtsc3PlpSettings;
struct FrontendAtsc3Settings;
struct FrontendAtsc3Capabilities;
enum class FrontendDvbsModulation : int32_t;
enum class FrontendDvbsRolloff : uint32_t;
enum class FrontendDvbsPilot : uint32_t;
struct FrontendDvbsCodeRate;
enum class FrontendDvbsStandard : uint8_t;
enum class FrontendDvbsVcmMode : uint32_t;
struct FrontendDvbsSettings;
struct FrontendDvbsCapabilities;
enum class FrontendDvbcModulation : uint32_t;
enum class FrontendDvbcOuterFec : uint32_t;
enum class FrontendDvbcAnnex : uint8_t;
enum class FrontendDvbcSpectralInversion : uint32_t;
struct FrontendDvbcSettings;
struct FrontendDvbcCapabilities;
enum class FrontendDvbtBandwidth : uint32_t;
enum class FrontendDvbtConstellation : uint32_t;
enum class FrontendDvbtHierarchy : uint32_t;
enum class FrontendDvbtCoderate : uint32_t;
enum class FrontendDvbtGuardInterval : uint32_t;
enum class FrontendDvbtTransmissionMode : uint32_t;
enum class FrontendDvbtPlpMode : uint32_t;
enum class FrontendDvbtStandard : uint8_t;
struct FrontendDvbtSettings;
struct FrontendDvbtCapabilities;
enum class FrontendIsdbsRolloff : uint32_t;
enum class FrontendIsdbsModulation : uint32_t;
enum class FrontendIsdbsCoderate : uint32_t;
enum class FrontendIsdbsStreamIdType : uint32_t;
struct FrontendIsdbsSettings;
struct FrontendIsdbsCapabilities;
enum class FrontendIsdbs3Rolloff : uint32_t;
enum class FrontendIsdbs3Modulation : uint32_t;
enum class FrontendIsdbs3Coderate : uint32_t;
struct FrontendIsdbs3Settings;
struct FrontendIsdbs3Capabilities;
enum class FrontendIsdbtMode : uint32_t;
enum class FrontendIsdbtBandwidth : uint32_t;
enum class FrontendIsdbtModulation : uint32_t;
struct FrontendIsdbtSettings;
struct FrontendIsdbtCapabilities;
enum class FrontendAnalogType : uint32_t;
enum class FrontendAnalogSifStandard : uint32_t;
struct FrontendAnalogSettings;
struct FrontendAnalogCapabilities;
struct FrontendSettings;
enum class FrontendScanType : uint32_t;
enum class FrontendScanMessageType : uint32_t;
struct FrontendScanAtsc3PlpInfo;
struct FrontendScanMessage;
enum class FrontendEventType : uint32_t;
enum class FrontendStatusType : uint32_t;
struct FrontendStatusAtsc3PlpInfo;
struct FrontendModulationStatus;
enum class LnbVoltage : uint32_t;
struct FrontendStatus;
struct FrontendInfo;
enum class LnbTone : int32_t;
enum class LnbPosition : int32_t;
enum class LnbEventType : uint32_t;
enum class DemuxFilterMainType : uint32_t;
enum class DemuxTsFilterType : uint32_t;
enum class DemuxMmtpFilterType : uint32_t;
enum class DemuxIpFilterType : uint32_t;
enum class DemuxTlvFilterType : uint32_t;
enum class DemuxAlpFilterType : uint32_t;
struct DemuxFilterType;
struct DemuxPid;
enum class Constant : uint32_t;
enum class DemuxFilterStatus : uint8_t;
enum class DemuxTsIndex : uint32_t;
enum class DemuxScIndex : uint32_t;
enum class DemuxScHevcIndex : uint32_t;
enum class DemuxRecordScIndexType : uint32_t;
struct DemuxFilterRecordSettings;
struct DemuxFilterSectionBits;
struct DemuxFilterSectionSettings;
struct DemuxFilterPesDataSettings;
struct DemuxFilterAvSettings;
struct DemuxFilterDownloadSettings;
struct DemuxIpAddress;
struct DemuxTsFilterSettings;
struct DemuxMmtpFilterSettings;
struct DemuxIpFilterSettings;
struct DemuxTlvFilterSettings;
enum class DemuxAlpLengthType : uint8_t;
struct DemuxAlpFilterSettings;
struct DemuxFilterSettings;
enum class DemuxQueueNotifyBits : uint32_t;
struct DemuxFilterSectionEvent;
struct AudioExtraMetaData;
struct DemuxFilterMediaEvent;
struct DemuxFilterPesEvent;
struct DemuxFilterTsRecordEvent;
struct DemuxFilterTemiEvent;
struct DemuxFilterMmtpRecordEvent;
struct DemuxFilterDownloadEvent;
struct DemuxFilterIpPayloadEvent;
struct DemuxFilterEvent;
enum class DataFormat : uint32_t;
struct RecordSettings;
enum class PlaybackStatus : uint32_t;
struct PlaybackSettings;
enum class DvrType : uint8_t;
struct DvrSettings;
struct DemuxCapabilities;

// Order of inner types was changed for forward reference support.

enum class Result : int32_t {
    SUCCESS = 0,
    UNAVAILABLE = 1 /* ::android::hardware::tv::tuner::V1_0::Result.SUCCESS implicitly + 1 */,
    NOT_INITIALIZED = 2 /* ::android::hardware::tv::tuner::V1_0::Result.UNAVAILABLE implicitly + 1 */,
    INVALID_STATE = 3 /* ::android::hardware::tv::tuner::V1_0::Result.NOT_INITIALIZED implicitly + 1 */,
    INVALID_ARGUMENT = 4 /* ::android::hardware::tv::tuner::V1_0::Result.INVALID_STATE implicitly + 1 */,
    OUT_OF_MEMORY = 5 /* ::android::hardware::tv::tuner::V1_0::Result.INVALID_ARGUMENT implicitly + 1 */,
    UNKNOWN_ERROR = 6 /* ::android::hardware::tv::tuner::V1_0::Result.OUT_OF_MEMORY implicitly + 1 */,
};

/**
 * Frontend ID.
 */
typedef uint32_t FrontendId;

/**
 *  Frontend Types.
 */
enum class FrontendType : uint32_t {
    UNDEFINED = 0u,
    ANALOG = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendType.UNDEFINED implicitly + 1 */,
    /**
     * Advanced Television Systems Committee (ATSC) Standard A/72.
     */
    ATSC = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendType.ANALOG implicitly + 1 */,
    /**
     * Advanced Television Systems Committee (ATSC 3.0) Standard A/300.
     */
    ATSC3 = 3u /* ::android::hardware::tv::tuner::V1_0::FrontendType.ATSC implicitly + 1 */,
    /**
     * Digital Video Broadcasting - Cable
     * DVB Cable Frontend Standard ETSI EN 300 468 V1.15.1.
     */
    DVBC = 4u /* ::android::hardware::tv::tuner::V1_0::FrontendType.ATSC3 implicitly + 1 */,
    /**
     * Digital Video Broadcasting - Satellite
     * DVB Satellite Frontend Standard ETSI EN 300 468 V1.15.1 and
     * ETSI EN 302 307-2 V1.1.1.
     */
    DVBS = 5u /* ::android::hardware::tv::tuner::V1_0::FrontendType.DVBC implicitly + 1 */,
    /**
     * Digital Video Broadcasting - Terrestrial
     * DVB Terrestrial Frontend Standard ETSI EN 300 468 V1.15.1 and
     * ETSI EN 302 755 V1.4.1.
     */
    DVBT = 6u /* ::android::hardware::tv::tuner::V1_0::FrontendType.DVBS implicitly + 1 */,
    /**
     * Integrated Services Digital Broadcasting-Satellite (ISDB-S)
     * ARIB STD-B20 is technical document of ISDB-S.
     */
    ISDBS = 7u /* ::android::hardware::tv::tuner::V1_0::FrontendType.DVBT implicitly + 1 */,
    /**
     * Integrated Services Digital Broadcasting-Satellite (ISDB-S)
     * ARIB STD-B44 is technical document of ISDB-S3.
     */
    ISDBS3 = 8u /* ::android::hardware::tv::tuner::V1_0::FrontendType.ISDBS implicitly + 1 */,
    /**
     * Integrated Services Digital Broadcasting-Terrestrial (ISDB-T or SBTVD)
     * ABNT NBR 15603 is technical document of ISDB-T.
     */
    ISDBT = 9u /* ::android::hardware::tv::tuner::V1_0::FrontendType.ISDBS3 implicitly + 1 */,
};

/**
 *  Inner Forward Error Correction type as specified in ETSI EN 300 468 V1.15.1
 *  and ETSI EN 302 307-2 V1.1.1.
 */
enum class FrontendInnerFec : uint64_t {
    /**
     * Not defined
     */
    FEC_UNDEFINED = 0ull,
    /**
     * hardware is able to detect and set FEC automatically
     */
    AUTO = 1ull /* 1 << 0 */,
    /**
     * 1/2 conv. code rate
     */
    FEC_1_2 = 2ull /* 1 << 1 */,
    /**
     * 1/3 conv. code rate
     */
    FEC_1_3 = 4ull /* 1 << 2 */,
    /**
     * 1/4 conv. code rate
     */
    FEC_1_4 = 8ull /* 1 << 3 */,
    /**
     * 1/5 conv. code rate
     */
    FEC_1_5 = 16ull /* 1 << 4 */,
    /**
     * 2/3 conv. code rate
     */
    FEC_2_3 = 32ull /* 1 << 5 */,
    /**
     * 2/5 conv. code rate
     */
    FEC_2_5 = 64ull /* 1 << 6 */,
    /**
     * 2/9 conv. code rate
     */
    FEC_2_9 = 128ull /* 1 << 7 */,
    /**
     * 3/4 conv. code rate
     */
    FEC_3_4 = 256ull /* 1 << 8 */,
    /**
     * 3/5 conv. code rate
     */
    FEC_3_5 = 512ull /* 1 << 9 */,
    /**
     * 4/5 conv. code rate
     */
    FEC_4_5 = 1024ull /* 1 << 10 */,
    /**
     * 4/15 conv. code rate
     */
    FEC_4_15 = 2048ull /* 1 << 11 */,
    /**
     * 5/6 conv. code rate
     */
    FEC_5_6 = 4096ull /* 1 << 12 */,
    /**
     * 5/9 conv. code rate
     */
    FEC_5_9 = 8192ull /* 1 << 13 */,
    /**
     * 6/7 conv. code rate
     */
    FEC_6_7 = 16384ull /* 1 << 14 */,
    /**
     * 7/8 conv. code rate
     */
    FEC_7_8 = 32768ull /* 1 << 15 */,
    /**
     * 7/9 conv. code rate
     */
    FEC_7_9 = 65536ull /* 1 << 16 */,
    /**
     * 7/15 conv. code rate
     */
    FEC_7_15 = 131072ull /* 1 << 17 */,
    /**
     * 8/9 conv. code rate
     */
    FEC_8_9 = 262144ull /* 1 << 18 */,
    /**
     * 8/15 conv. code rate
     */
    FEC_8_15 = 524288ull /* 1 << 19 */,
    /**
     * 9/10 conv. code rate
     */
    FEC_9_10 = 1048576ull /* 1 << 20 */,
    /**
     * 9/20 conv. code rate
     */
    FEC_9_20 = 2097152ull /* 1 << 21 */,
    /**
     * 11/15 conv. code rate
     */
    FEC_11_15 = 4194304ull /* 1 << 22 */,
    /**
     * 11/20 conv. code rate
     */
    FEC_11_20 = 8388608ull /* 1 << 23 */,
    /**
     * 11/45 conv. code rate
     */
    FEC_11_45 = 16777216ull /* 1 << 24 */,
    /**
     * 13/18 conv. code rate
     */
    FEC_13_18 = 33554432ull /* 1 << 25 */,
    /**
     * 13/45 conv. code rate
     */
    FEC_13_45 = 67108864ull /* 1 << 26 */,
    /**
     * 14/45 conv. code rate
     */
    FEC_14_45 = 134217728ull /* 1 << 27 */,
    /**
     * 23/36 conv. code rate
     */
    FEC_23_36 = 268435456ull /* 1 << 28 */,
    /**
     * 25/36 conv. code rate
     */
    FEC_25_36 = 536870912ull /* 1 << 29 */,
    /**
     * 26/45 conv. code rate
     */
    FEC_26_45 = 1073741824ull /* 1 << 30 */,
    /**
     * 28/45 conv. code rate
     */
    FEC_28_45 = 18446744071562067968ull /* 1 << 31 */,
    /**
     * 29/45 conv. code rate
     */
    FEC_29_45 = 1ull /* 1 << 32 */,
    /**
     * 31/45 conv. code rate
     */
    FEC_31_45 = 2ull /* 1 << 33 */,
    /**
     * 32/45 conv. code rate
     */
    FEC_32_45 = 4ull /* 1 << 34 */,
    /**
     * 77/90 conv. code rate
     */
    FEC_77_90 = 8ull /* 1 << 35 */,
};

/**
 *  Modulation Type for ATSC.
 */
enum class FrontendAtscModulation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set modulation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MOD_8VSB = 4u /* 1 << 2 */,
    MOD_16VSB = 8u /* 1 << 3 */,
};

/**
 *  Signal Settings for an ATSC Frontend.
 */
struct FrontendAtscSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation modulation __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtscSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtscSettings, modulation) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAtscSettings) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAtscSettings) == 4, "wrong alignment");

/**
 *  Capabilities for ATSC Frontend.
 */
struct FrontendAtscCapabilities final {
    /**
     * Modulation capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtscModulation> modulationCap __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities, modulationCap) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities) == 4, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities) == 4, "wrong alignment");

/**
 *  Modulation Type for ATSC3.
 */
enum class FrontendAtsc3Modulation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set modulation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MOD_QPSK = 2u /* 1 << 1 */,
    MOD_16QAM = 4u /* 1 << 2 */,
    MOD_64QAM = 8u /* 1 << 3 */,
    MOD_256QAM = 16u /* 1 << 4 */,
    MOD_1024QAM = 32u /* 1 << 5 */,
    MOD_4096QAM = 64u /* 1 << 6 */,
};

/**
 *  Bandwidth for ATSC3.
 */
enum class FrontendAtsc3Bandwidth : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set bandwidth automatically
     */
    AUTO = 1u /* 1 << 0 */,
    BANDWIDTH_6MHZ = 2u /* 1 << 1 */,
    BANDWIDTH_7MHZ = 4u /* 1 << 2 */,
    BANDWIDTH_8MHZ = 8u /* 1 << 3 */,
};

/**
 *  Time Interleave Mode for ATSC3.
 */
enum class FrontendAtsc3TimeInterleaveMode : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set TimeInterleaveMode automatically
     */
    AUTO = 1u /* 1 << 0 */,
    CTI = 2u /* 1 << 1 */,
    HTI = 4u /* 1 << 2 */,
};

/**
 *  Code Rate for ATSC3.
 */
enum class FrontendAtsc3CodeRate : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Coderate automatically
     */
    AUTO = 1u /* 1 << 0 */,
    CODERATE_2_15 = 2u /* 1 << 1 */,
    CODERATE_3_15 = 4u /* 1 << 2 */,
    CODERATE_4_15 = 8u /* 1 << 3 */,
    CODERATE_5_15 = 16u /* 1 << 4 */,
    CODERATE_6_15 = 32u /* 1 << 5 */,
    CODERATE_7_15 = 64u /* 1 << 6 */,
    CODERATE_8_15 = 128u /* 1 << 7 */,
    CODERATE_9_15 = 256u /* 1 << 8 */,
    CODERATE_10_15 = 512u /* 1 << 9 */,
    CODERATE_11_15 = 1024u /* 1 << 10 */,
    CODERATE_12_15 = 2048u /* 1 << 11 */,
    CODERATE_13_15 = 4096u /* 1 << 12 */,
};

/**
 *  Forward Error Correction (FEC) for ATSC3.
 */
enum class FrontendAtsc3Fec : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set FEC automatically
     */
    AUTO = 1u /* 1 << 0 */,
    BCH_LDPC_16K = 2u /* 1 << 1 */,
    BCH_LDPC_64K = 4u /* 1 << 2 */,
    CRC_LDPC_16K = 8u /* 1 << 3 */,
    CRC_LDPC_64K = 16u /* 1 << 4 */,
    LDPC_16K = 32u /* 1 << 5 */,
    LDPC_64K = 64u /* 1 << 6 */,
};

/**
 *  Demodulator Output Format for an ATSC3 Frontend.
 */
enum class FrontendAtsc3DemodOutputFormat : uint8_t {
    /**
     * Dummy. Scan uses this.
     */
    UNDEFINED = 0,
    /**
     * ALP format. Typically used in US region.
     */
    ATSC3_LINKLAYER_PACKET = 1 /* 1 << 0 */,
    /**
     * BaseBand packet format. Typically used in Korea region.
     */
    BASEBAND_PACKET = 2 /* 1 << 1 */,
};

/**
 *  PLP basis Signal Settings for an ATSC3 Frontend.
 */
struct FrontendAtsc3PlpSettings final {
    uint8_t plpId __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation modulation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode interleaveMode __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate codeRate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec fec __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings, plpId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings, modulation) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings, interleaveMode) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings, codeRate) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings, fec) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings) == 20, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings) == 4, "wrong alignment");

/**
 *  Signal Settings for an ATSC3 Frontend.
 */
struct FrontendAtsc3Settings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    /**
     * Bandwidth of tuning band.
     */
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth bandwidth __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat demodOutputFormat __attribute__ ((aligned(1)));
    ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings> plpSettings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings, bandwidth) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings, demodOutputFormat) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings, plpSettings) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings) == 32, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings) == 8, "wrong alignment");

/**
 *  Capabilities for ATSC3 Frontend.
 */
struct FrontendAtsc3Capabilities final {
    /**
     * Bandwidth capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth> bandwidthCap __attribute__ ((aligned(4)));
    /**
     * Modulation capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation> modulationCap __attribute__ ((aligned(4)));
    /**
     * TimeInterleaveMode capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode> timeInterleaveModeCap __attribute__ ((aligned(4)));
    /**
     * CodeRate capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate> codeRateCap __attribute__ ((aligned(4)));
    /**
     * FEC capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec> fecCap __attribute__ ((aligned(4)));
    /**
     * Demodulator Output Format capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat> demodOutputFormatCap __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities, bandwidthCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities, modulationCap) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities, timeInterleaveModeCap) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities, codeRateCap) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities, fecCap) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities, demodOutputFormatCap) == 20, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities) == 4, "wrong alignment");

/**
 *  Modulation Type for DVBS.
 */
enum class FrontendDvbsModulation : int32_t {
    UNDEFINED = 0,
    /**
     * hardware is able to detect and set Modulation automatically
     */
    AUTO = 1 /* 1 << 0 */,
    MOD_QPSK = 2 /* 1 << 1 */,
    MOD_8PSK = 4 /* 1 << 2 */,
    MOD_16QAM = 8 /* 1 << 3 */,
    MOD_16PSK = 16 /* 1 << 4 */,
    MOD_32PSK = 32 /* 1 << 5 */,
    MOD_ACM = 64 /* 1 << 6 */,
    MOD_8APSK = 128 /* 1 << 7 */,
    MOD_16APSK = 256 /* 1 << 8 */,
    MOD_32APSK = 512 /* 1 << 9 */,
    MOD_64APSK = 1024 /* 1 << 10 */,
    MOD_128APSK = 2048 /* 1 << 11 */,
    MOD_256APSK = 4096 /* 1 << 12 */,
    /**
     * Reserved for Proprietary modulation
     */
    MOD_RESERVED = 8192 /* 1 << 13 */,
};

/**
 *  Roll Off value for DVBS.
 */
enum class FrontendDvbsRolloff : uint32_t {
    UNDEFINED = 0u,
    ROLLOFF_0_35 = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff.UNDEFINED implicitly + 1 */,
    ROLLOFF_0_25 = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff.ROLLOFF_0_35 implicitly + 1 */,
    ROLLOFF_0_20 = 3u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff.ROLLOFF_0_25 implicitly + 1 */,
    ROLLOFF_0_15 = 4u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff.ROLLOFF_0_20 implicitly + 1 */,
    ROLLOFF_0_10 = 5u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff.ROLLOFF_0_15 implicitly + 1 */,
    ROLLOFF_0_5 = 6u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff.ROLLOFF_0_10 implicitly + 1 */,
};

/**
 *  Pilot mode for DVBS.
 */
enum class FrontendDvbsPilot : uint32_t {
    UNDEFINED = 0u,
    ON = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot.UNDEFINED implicitly + 1 */,
    OFF = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot.ON implicitly + 1 */,
    AUTO = 3u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot.OFF implicitly + 1 */,
};

/**
 *  Code Rate for DVBS.
 */
struct FrontendDvbsCodeRate final {
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec fec __attribute__ ((aligned(8)));
    bool isLinear __attribute__ ((aligned(1)));
    /**
     * true if enable short frame
     */
    bool isShortFrames __attribute__ ((aligned(1)));
    /**
     * bits number in 1000 symbol. 0 if use the default.
     */
    uint32_t bitsPer1000Symbol __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate, fec) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate, isLinear) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate, isShortFrames) == 9, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate, bitsPer1000Symbol) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate) == 16, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate) == 8, "wrong alignment");

/**
 *  Sub standards in DVBS.
 */
enum class FrontendDvbsStandard : uint8_t {
    AUTO = 1 /* 1 << 0 */,
    S = 2 /* 1 << 1 */,
    S2 = 4 /* 1 << 2 */,
    S2X = 8 /* 1 << 3 */,
};

/**
 *   VCM mode in DVBS.
 */
enum class FrontendDvbsVcmMode : uint32_t {
    UNDEFINED = 0u,
    AUTO = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode.UNDEFINED implicitly + 1 */,
    MANUAL = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode.AUTO implicitly + 1 */,
};

/**
 *  Signal Settings for an DVBS Frontend.
 */
struct FrontendDvbsSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation modulation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate coderate __attribute__ ((aligned(8)));
    /**
     * Symbols per second
     */
    uint32_t symbolRate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff rolloff __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot pilot __attribute__ ((aligned(4)));
    uint32_t inputStreamId __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard standard __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode vcmMode __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, modulation) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, coderate) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, symbolRate) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, rolloff) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, pilot) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, inputStreamId) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, standard) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings, vcmMode) == 44, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings) == 48, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings) == 8, "wrong alignment");

/**
 *  Capabilities for DVBS Frontend.
 */
struct FrontendDvbsCapabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation> modulationCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendInnerFec> innerfecCap __attribute__ ((aligned(8)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard> standard __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities, modulationCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities, innerfecCap) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities, standard) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities) == 8, "wrong alignment");

/**
 *  Modulation Type for DVBC.
 */
enum class FrontendDvbcModulation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Modulation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MOD_16QAM = 2u /* 1 << 1 */,
    MOD_32QAM = 4u /* 1 << 2 */,
    MOD_64QAM = 8u /* 1 << 3 */,
    MOD_128QAM = 16u /* 1 << 4 */,
    MOD_256QAM = 32u /* 1 << 5 */,
};

/**
 *  Outer Forward Error Correction (FEC) Type for DVBC.
 */
enum class FrontendDvbcOuterFec : uint32_t {
    UNDEFINED = 0u,
    OUTER_FEC_NONE = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec.UNDEFINED implicitly + 1 */,
    OUTER_FEC_RS = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec.OUTER_FEC_NONE implicitly + 1 */,
};

/**
 *   Annex Type for DVBC.
 */
enum class FrontendDvbcAnnex : uint8_t {
    UNDEFINED = 0,
    A = 1 /* 1 << 0 */,
    B = 2 /* 1 << 1 */,
    C = 4 /* 1 << 2 */,
};

/**
 *   Spectral Inversion Type for DVBC.
 */
enum class FrontendDvbcSpectralInversion : uint32_t {
    UNDEFINED = 0u,
    NORMAL = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion.UNDEFINED implicitly + 1 */,
    INVERTED = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion.NORMAL implicitly + 1 */,
};

/**
 *  Signal Settings for an DVBC Frontend.
 */
struct FrontendDvbcSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation modulation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec fec __attribute__ ((aligned(8)));
    /**
     * Symbols per second
     */
    uint32_t symbolRate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec outerFec __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex annex __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion spectralInversion __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, modulation) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, fec) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, symbolRate) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, outerFec) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, annex) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings, spectralInversion) == 28, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings) == 32, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings) == 8, "wrong alignment");

/**
 *  Capabilities for DVBC Frontend.
 */
struct FrontendDvbcCapabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation> modulationCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendInnerFec> fecCap __attribute__ ((aligned(8)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex> annexCap __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities, modulationCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities, fecCap) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities, annexCap) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities) == 8, "wrong alignment");

/**
 *  Bandwidth Type for DVBT.
 */
enum class FrontendDvbtBandwidth : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Bandwidth automatically
     */
    AUTO = 1u /* 1 << 0 */,
    BANDWIDTH_8MHZ = 2u /* 1 << 1 */,
    BANDWIDTH_7MHZ = 4u /* 1 << 2 */,
    BANDWIDTH_6MHZ = 8u /* 1 << 3 */,
    BANDWIDTH_5MHZ = 16u /* 1 << 4 */,
    BANDWIDTH_1_7MHZ = 32u /* 1 << 5 */,
    BANDWIDTH_10MHZ = 64u /* 1 << 6 */,
};

/**
 *  Constellation Type for DVBT.
 */
enum class FrontendDvbtConstellation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Constellation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    CONSTELLATION_QPSK = 2u /* 1 << 1 */,
    CONSTELLATION_16QAM = 4u /* 1 << 2 */,
    CONSTELLATION_64QAM = 8u /* 1 << 3 */,
    CONSTELLATION_256QAM = 16u /* 1 << 4 */,
};

/**
 *  Hierarchy Type for DVBT.
 */
enum class FrontendDvbtHierarchy : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Hierarchy automatically
     */
    AUTO = 1u /* 1 << 0 */,
    HIERARCHY_NON_NATIVE = 2u /* 1 << 1 */,
    HIERARCHY_1_NATIVE = 4u /* 1 << 2 */,
    HIERARCHY_2_NATIVE = 8u /* 1 << 3 */,
    HIERARCHY_4_NATIVE = 16u /* 1 << 4 */,
    HIERARCHY_NON_INDEPTH = 32u /* 1 << 5 */,
    HIERARCHY_1_INDEPTH = 64u /* 1 << 6 */,
    HIERARCHY_2_INDEPTH = 128u /* 1 << 7 */,
    HIERARCHY_4_INDEPTH = 256u /* 1 << 8 */,
};

/**
 *  Hierarchy Type for DVBT.
 */
enum class FrontendDvbtCoderate : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Hierarchy automatically
     */
    AUTO = 1u /* 1 << 0 */,
    CODERATE_1_2 = 2u /* 1 << 1 */,
    CODERATE_2_3 = 4u /* 1 << 2 */,
    CODERATE_3_4 = 8u /* 1 << 3 */,
    CODERATE_5_6 = 16u /* 1 << 4 */,
    CODERATE_7_8 = 32u /* 1 << 5 */,
    CODERATE_3_5 = 64u /* 1 << 6 */,
    CODERATE_4_5 = 128u /* 1 << 7 */,
    CODERATE_6_7 = 256u /* 1 << 8 */,
    CODERATE_8_9 = 512u /* 1 << 9 */,
};

/**
 *  Guard Interval Type for DVBT.
 */
enum class FrontendDvbtGuardInterval : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Guard Interval automatically
     */
    AUTO = 1u /* 1 << 0 */,
    INTERVAL_1_32 = 2u /* 1 << 1 */,
    INTERVAL_1_16 = 4u /* 1 << 2 */,
    INTERVAL_1_8 = 8u /* 1 << 3 */,
    INTERVAL_1_4 = 16u /* 1 << 4 */,
    INTERVAL_1_128 = 32u /* 1 << 5 */,
    INTERVAL_19_128 = 64u /* 1 << 6 */,
    INTERVAL_19_256 = 128u /* 1 << 7 */,
};

/**
 *  Transmission Mode for DVBT.
 */
enum class FrontendDvbtTransmissionMode : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Transmission Mode automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MODE_2K = 2u /* 1 << 1 */,
    MODE_8K = 4u /* 1 << 2 */,
    MODE_4K = 8u /* 1 << 3 */,
    MODE_1K = 16u /* 1 << 4 */,
    MODE_16K = 32u /* 1 << 5 */,
    MODE_32K = 64u /* 1 << 6 */,
};

/**
 *   Physical Layer Pipe (PLP) Mode for DVBT.
 */
enum class FrontendDvbtPlpMode : uint32_t {
    UNDEFINED = 0u,
    AUTO = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode.UNDEFINED implicitly + 1 */,
    MANUAL = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode.AUTO implicitly + 1 */,
};

/**
 *  Sub standards in DVBT.
 */
enum class FrontendDvbtStandard : uint8_t {
    AUTO = 1 /* 1 << 0 */,
    T = 2 /* 1 << 1 */,
    T2 = 4 /* 1 << 2 */,
};

/**
 *  Signal Settings for DVBT Frontend.
 */
struct FrontendDvbtSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode transmissionMode __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth bandwidth __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation constellation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy hierarchy __attribute__ ((aligned(4)));
    /**
     * Code Rate for High Priority level
     */
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate hpCoderate __attribute__ ((aligned(4)));
    /**
     * Code Rate for Low Priority level
     */
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate lpCoderate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval guardInterval __attribute__ ((aligned(4)));
    bool isHighPriority __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard standard __attribute__ ((aligned(1)));
    bool isMiso __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode plpMode __attribute__ ((aligned(4)));
    /**
     * Physical Layer Pipe (PLP) Id
     */
    uint8_t plpId __attribute__ ((aligned(1)));
    /**
     * Group Id for Physical Layer Pipe (PLP)
     */
    uint8_t plpGroupId __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, transmissionMode) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, bandwidth) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, constellation) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, hierarchy) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, hpCoderate) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, lpCoderate) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, guardInterval) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, isHighPriority) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, standard) == 33, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, isMiso) == 34, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, plpMode) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, plpId) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings, plpGroupId) == 41, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings) == 44, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings) == 4, "wrong alignment");

/**
 *  Capabilities for DVBT Frontend.
 */
struct FrontendDvbtCapabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode> transmissionModeCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth> bandwidthCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation> constellationCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate> coderateCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy> hierarchyCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval> guardIntervalCap __attribute__ ((aligned(4)));
    bool isT2Supported __attribute__ ((aligned(1)));
    bool isMisoSupported __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, transmissionModeCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, bandwidthCap) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, constellationCap) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, coderateCap) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, hierarchyCap) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, guardIntervalCap) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, isT2Supported) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities, isMisoSupported) == 25, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities) == 28, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities) == 4, "wrong alignment");

/**
 *  Roll Off Type for ISDBS.
 */
enum class FrontendIsdbsRolloff : uint32_t {
    UNDEFINED = 0u,
    ROLLOFF_0_35 = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff.UNDEFINED implicitly + 1 */,
};

/**
 *  Modulation Type for ISDBS.
 */
enum class FrontendIsdbsModulation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Modulation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MOD_BPSK = 2u /* 1 << 1 */,
    MOD_QPSK = 4u /* 1 << 2 */,
    MOD_TC8PSK = 8u /* 1 << 3 */,
};

/**
 *  Code Rate Type for ISDBS.
 */
enum class FrontendIsdbsCoderate : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Code Rate automatically
     */
    AUTO = 1u /* 1 << 0 */,
    CODERATE_1_2 = 2u /* 1 << 1 */,
    CODERATE_2_3 = 4u /* 1 << 2 */,
    CODERATE_3_4 = 8u /* 1 << 3 */,
    CODERATE_5_6 = 16u /* 1 << 4 */,
    CODERATE_7_8 = 32u /* 1 << 5 */,
};

/**
 *  Stream Id Type for ISDBS.
 */
enum class FrontendIsdbsStreamIdType : uint32_t {
    STREAM_ID = 0u,
    RELATIVE_STREAM_NUMBER = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType.STREAM_ID implicitly + 1 */,
};

/**
 *  Signal Settings for ISDBS Frontend.
 */
struct FrontendIsdbsSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    uint16_t streamId __attribute__ ((aligned(2)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType streamIdType __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation modulation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate coderate __attribute__ ((aligned(4)));
    /**
     * Symbols per second
     */
    uint32_t symbolRate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff rolloff __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, streamId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, streamIdType) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, modulation) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, coderate) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, symbolRate) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings, rolloff) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings) == 28, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings) == 4, "wrong alignment");

/**
 *  Capabilities for ISDBS Frontend.
 */
struct FrontendIsdbsCapabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation> modulationCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate> coderateCap __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities, modulationCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities, coderateCap) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities) == 4, "wrong alignment");

/**
 *  Roll of Type for ISDBS3.
 */
enum class FrontendIsdbs3Rolloff : uint32_t {
    UNDEFINED = 0u,
    ROLLOFF_0_03 = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff.UNDEFINED implicitly + 1 */,
};

/**
 *  Modulaltion Type for ISDBS3.
 */
enum class FrontendIsdbs3Modulation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Modulation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MOD_BPSK = 2u /* 1 << 1 */,
    MOD_QPSK = 4u /* 1 << 2 */,
    MOD_8PSK = 8u /* 1 << 3 */,
    MOD_16APSK = 16u /* 1 << 4 */,
    MOD_32APSK = 32u /* 1 << 5 */,
};

/**
 *  Code Rate Type for ISDBS3.
 */
enum class FrontendIsdbs3Coderate : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Code Rate automatically
     */
    AUTO = 1u /* 1 << 0 */,
    CODERATE_1_3 = 2u /* 1 << 1 */,
    CODERATE_2_5 = 4u /* 1 << 2 */,
    CODERATE_1_2 = 8u /* 1 << 3 */,
    CODERATE_3_5 = 16u /* 1 << 4 */,
    CODERATE_2_3 = 32u /* 1 << 5 */,
    CODERATE_3_4 = 64u /* 1 << 6 */,
    CODERATE_7_9 = 128u /* 1 << 7 */,
    CODERATE_4_5 = 256u /* 1 << 8 */,
    CODERATE_5_6 = 512u /* 1 << 9 */,
    CODERATE_7_8 = 1024u /* 1 << 10 */,
    CODERATE_9_10 = 2048u /* 1 << 11 */,
};

/**
 *  Signal Settings for ISDBS3 Frontend.
 */
struct FrontendIsdbs3Settings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    uint16_t streamId __attribute__ ((aligned(2)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType streamIdType __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation modulation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate coderate __attribute__ ((aligned(4)));
    /**
     * Symbols per second
     */
    uint32_t symbolRate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff rolloff __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, streamId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, streamIdType) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, modulation) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, coderate) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, symbolRate) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings, rolloff) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings) == 28, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings) == 4, "wrong alignment");

/**
 *  Capabilities for ISDBS3 Frontend.
 */
struct FrontendIsdbs3Capabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation> modulationCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate> coderateCap __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities, modulationCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities, coderateCap) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities) == 4, "wrong alignment");

/**
 *   Mode for ISDBT.
 */
enum class FrontendIsdbtMode : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Mode automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MODE_1 = 2u /* 1 << 1 */,
    MODE_2 = 4u /* 1 << 2 */,
    MODE_3 = 8u /* 1 << 3 */,
};

/**
 *   Bandwidth for ISDBT.
 */
enum class FrontendIsdbtBandwidth : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Bandwidth automatically
     */
    AUTO = 1u /* 1 << 0 */,
    BANDWIDTH_8MHZ = 2u /* 1 << 1 */,
    BANDWIDTH_7MHZ = 4u /* 1 << 2 */,
    BANDWIDTH_6MHZ = 8u /* 1 << 3 */,
};

/**
 *   Modulation for ISDBT.
 */
enum class FrontendIsdbtModulation : uint32_t {
    UNDEFINED = 0u,
    /**
     * hardware is able to detect and set Modulation automatically
     */
    AUTO = 1u /* 1 << 0 */,
    MOD_DQPSK = 2u /* 1 << 1 */,
    MOD_QPSK = 4u /* 1 << 2 */,
    MOD_16QAM = 8u /* 1 << 3 */,
    MOD_64QAM = 16u /* 1 << 4 */,
};

typedef ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate FrontendIsdbtCoderate;

typedef ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval FrontendIsdbtGuardInterval;

/**
 *  Signal Settings for ISDBT Frontend.
 */
struct FrontendIsdbtSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation modulation __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth bandwidth __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode mode __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate coderate __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval guardInterval __attribute__ ((aligned(4)));
    uint32_t serviceAreaId __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, modulation) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, bandwidth) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, mode) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, coderate) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, guardInterval) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings, serviceAreaId) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings) == 28, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings) == 4, "wrong alignment");

/**
 *  Capabilities for ISDBT Frontend.
 */
struct FrontendIsdbtCapabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode> modeCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth> bandwidthCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation> modulationCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate> coderateCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval> guardIntervalCap __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities, modeCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities, bandwidthCap) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities, modulationCap) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities, coderateCap) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities, guardIntervalCap) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities) == 20, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities) == 4, "wrong alignment");

/**
 *   Signal Type for Analog Frontend.
 */
enum class FrontendAnalogType : uint32_t {
    UNDEFINED = 0u,
    AUTO = 1u /* 1 << 0 */,
    PAL = 2u /* 1 << 1 */,
    PAL_M = 4u /* 1 << 2 */,
    PAL_N = 8u /* 1 << 3 */,
    PAL_60 = 16u /* 1 << 4 */,
    NTSC = 32u /* 1 << 5 */,
    NTSC_443 = 64u /* 1 << 6 */,
    SECAM = 128u /* 1 << 7 */,
};

/**
 *   Standard Interchange Format (SIF) for Analog Frontend.
 */
enum class FrontendAnalogSifStandard : uint32_t {
    UNDEFINED = 0u,
    AUTO = 1u /* 1 << 0 */,
    BG = 2u /* 1 << 1 */,
    BG_A2 = 4u /* 1 << 2 */,
    BG_NICAM = 8u /* 1 << 3 */,
    I = 16u /* 1 << 4 */,
    DK = 32u /* 1 << 5 */,
    DK1_A2 = 64u /* 1 << 6 */,
    DK2_A2 = 128u /* 1 << 7 */,
    DK3_A2 = 256u /* 1 << 8 */,
    DK_NICAM = 512u /* 1 << 9 */,
    L = 1024u /* 1 << 10 */,
    M = 2048u /* 1 << 11 */,
    M_BTSC = 4096u /* 1 << 12 */,
    M_A2 = 8192u /* 1 << 13 */,
    M_EIAJ = 16384u /* 1 << 14 */,
    I_NICAM = 32768u /* 1 << 15 */,
    L_NICAM = 65536u /* 1 << 16 */,
    L_PRIME = 131072u /* 1 << 17 */,
};

/**
 *  Signal Settings for Analog Frontend.
 */
struct FrontendAnalogSettings final {
    /**
     * Signal frequency in Hertz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType type __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard sifStandard __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings, frequency) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings, type) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings, sifStandard) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings) == 12, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings) == 4, "wrong alignment");

/**
 *  Capabilities for Analog Frontend.
 */
struct FrontendAnalogCapabilities final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAnalogType> typeCap __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard> sifStandardCap __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities, typeCap) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities, sifStandardCap) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities) == 4, "wrong alignment");

/**
 *  Signal Settings for Frontend.
 */
struct FrontendSettings final {
    enum class hidl_discriminator : uint8_t {
        analog = 0,  // ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings
        atsc = 1,  // ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings
        atsc3 = 2,  // ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings
        dvbs = 3,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings
        dvbc = 4,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings
        dvbt = 5,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings
        isdbs = 6,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings
        isdbs3 = 7,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings
        isdbt = 8,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings
    };

    FrontendSettings();
    ~FrontendSettings();
    FrontendSettings(FrontendSettings&&);
    FrontendSettings(const FrontendSettings&);
    FrontendSettings& operator=(FrontendSettings&&);
    FrontendSettings& operator=(const FrontendSettings&);

    void analog(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings&);
    void analog(::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& analog();
    const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& analog() const;

    void atsc(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings&);
    void atsc(::android::hardware::tv::tuner::V1_0::FrontendAtscSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& atsc();
    const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& atsc() const;

    void atsc3(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings&);
    void atsc3(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& atsc3();
    const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& atsc3() const;

    void dvbs(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings&);
    void dvbs(::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& dvbs();
    const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& dvbs() const;

    void dvbc(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings&);
    void dvbc(::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& dvbc();
    const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& dvbc() const;

    void dvbt(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings&);
    void dvbt(::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& dvbt();
    const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& dvbt() const;

    void isdbs(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings&);
    void isdbs(::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& isdbs();
    const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& isdbs() const;

    void isdbs3(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings&);
    void isdbs3(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& isdbs3();
    const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& isdbs3() const;

    void isdbt(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings&);
    void isdbt(::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings&&);
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& isdbt();
    const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& isdbt() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::FrontendSettings, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings analog __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings atsc __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings atsc3 __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings dvbs __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings dvbc __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings dvbt __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings isdbs __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings isdbs3 __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings isdbt __attribute__ ((aligned(4)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_union) == 48, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendSettings) == 56, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendSettings) == 8, "wrong alignment");

/**
 *  Scan type for Frontend.
 */
enum class FrontendScanType : uint32_t {
    SCAN_UNDEFINED = 0u,
    SCAN_AUTO = 1u /* 1 << 0 */,
    SCAN_BLIND = 2u /* 1 << 1 */,
};

/**
 *  Scan Message Type for Frontend.
 */
enum class FrontendScanMessageType : uint32_t {
    /**
     * Scan locked the signal.
     */
    LOCKED = 0u,
    /**
     * Scan stopped.
     */
    END = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.LOCKED implicitly + 1 */,
    /**
     * Scan progress report.
     */
    PROGRESS_PERCENT = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.END implicitly + 1 */,
    /**
     * Locked frequency report.
     */
    FREQUENCY = 3u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.PROGRESS_PERCENT implicitly + 1 */,
    /**
     * Locked symbol rate.
     */
    SYMBOL_RATE = 4u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.FREQUENCY implicitly + 1 */,
    /**
     * Locked HIERARCHY for DVBT2 frontend.
     */
    HIERARCHY = 5u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.SYMBOL_RATE implicitly + 1 */,
    ANALOG_TYPE = 6u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.HIERARCHY implicitly + 1 */,
    /**
     * Locked Plp Ids for DVBT2 frontend.
     */
    PLP_IDS = 7u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.ANALOG_TYPE implicitly + 1 */,
    /**
     * Locked group Ids for DVBT2 frontend.
     */
    GROUP_IDS = 8u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.PLP_IDS implicitly + 1 */,
    /**
     * Stream Ids.
     */
    INPUT_STREAM_IDS = 9u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.GROUP_IDS implicitly + 1 */,
    /**
     * Locked signal standard.
     */
    STANDARD = 10u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.INPUT_STREAM_IDS implicitly + 1 */,
    /**
     * PLP status in a tuned frequency band for ATSC3 frontend.
     */
    ATSC3_PLP_INFO = 11u /* ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType.STANDARD implicitly + 1 */,
};

/**
 *  ATSC3.0 PLP information for scan
 */
struct FrontendScanAtsc3PlpInfo final {
    uint8_t plpId __attribute__ ((aligned(1)));
    bool bLlsFlag __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo, plpId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo, bLlsFlag) == 1, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo) == 2, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo) == 1, "wrong alignment");

/**
 *  Scan Message for Frontend.
 */
struct FrontendScanMessage final {
    // Forward declaration for forward reference support:
    struct Standard;

    struct Standard final {
        enum class hidl_discriminator : uint8_t {
            sStd = 0,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard
            tStd = 1,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard
            sifStd = 2,  // ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard
        };

        Standard();
        ~Standard();
        Standard(Standard&&);
        Standard(const Standard&);
        Standard& operator=(Standard&&);
        Standard& operator=(const Standard&);

        void sStd(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard);
        ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard& sStd();
        ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard sStd() const;

        void tStd(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard);
        ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard& tStd();
        ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard tStd() const;

        void sifStd(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard);
        ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard& sifStd();
        ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard sifStd() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard sStd __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard tStd __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard sifStd __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_union) == 4, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_union) == 4, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard) == 4, "wrong alignment");

    enum class hidl_discriminator : uint8_t {
        isLocked = 0,  // bool
        isEnd = 1,  // bool
        /**
         * scan progress percent (0..100)
         */
        progressPercent = 2,  // uint8_t
        /**
         * Signal frequencies in Hertz
         */
        frequencies = 3,  // ::android::hardware::hidl_vec<uint32_t>
        /**
         * Symbols per second
         */
        symbolRates = 4,  // ::android::hardware::hidl_vec<uint32_t>
        hierarchy = 5,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy
        analogType = 6,  // ::android::hardware::tv::tuner::V1_0::FrontendAnalogType
        plpIds = 7,  // ::android::hardware::hidl_vec<uint8_t>
        groupIds = 8,  // ::android::hardware::hidl_vec<uint8_t>
        inputStreamIds = 9,  // ::android::hardware::hidl_vec<uint16_t>
        std = 10,  // ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard
        /**
         * A list of PLP status in a tuned frequency band for ATSC3 frontend.
         */
        atsc3PlpInfos = 11,  // ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo>
    };

    FrontendScanMessage();
    ~FrontendScanMessage();
    FrontendScanMessage(FrontendScanMessage&&);
    FrontendScanMessage(const FrontendScanMessage&);
    FrontendScanMessage& operator=(FrontendScanMessage&&);
    FrontendScanMessage& operator=(const FrontendScanMessage&);

    void isLocked(bool);
    bool& isLocked();
    bool isLocked() const;

    void isEnd(bool);
    bool& isEnd();
    bool isEnd() const;

    void progressPercent(uint8_t);
    uint8_t& progressPercent();
    uint8_t progressPercent() const;

    void frequencies(const ::android::hardware::hidl_vec<uint32_t>&);
    void frequencies(::android::hardware::hidl_vec<uint32_t>&&);
    ::android::hardware::hidl_vec<uint32_t>& frequencies();
    const ::android::hardware::hidl_vec<uint32_t>& frequencies() const;

    void symbolRates(const ::android::hardware::hidl_vec<uint32_t>&);
    void symbolRates(::android::hardware::hidl_vec<uint32_t>&&);
    ::android::hardware::hidl_vec<uint32_t>& symbolRates();
    const ::android::hardware::hidl_vec<uint32_t>& symbolRates() const;

    void hierarchy(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy& hierarchy();
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy hierarchy() const;

    void analogType(::android::hardware::tv::tuner::V1_0::FrontendAnalogType);
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType& analogType();
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType analogType() const;

    void plpIds(const ::android::hardware::hidl_vec<uint8_t>&);
    void plpIds(::android::hardware::hidl_vec<uint8_t>&&);
    ::android::hardware::hidl_vec<uint8_t>& plpIds();
    const ::android::hardware::hidl_vec<uint8_t>& plpIds() const;

    void groupIds(const ::android::hardware::hidl_vec<uint8_t>&);
    void groupIds(::android::hardware::hidl_vec<uint8_t>&&);
    ::android::hardware::hidl_vec<uint8_t>& groupIds();
    const ::android::hardware::hidl_vec<uint8_t>& groupIds() const;

    void inputStreamIds(const ::android::hardware::hidl_vec<uint16_t>&);
    void inputStreamIds(::android::hardware::hidl_vec<uint16_t>&&);
    ::android::hardware::hidl_vec<uint16_t>& inputStreamIds();
    const ::android::hardware::hidl_vec<uint16_t>& inputStreamIds() const;

    void std(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard&);
    void std(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard&&);
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& std();
    const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& std() const;

    void atsc3PlpInfos(const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo>&);
    void atsc3PlpInfos(::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo>&&);
    ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo>& atsc3PlpInfos();
    const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo>& atsc3PlpInfos() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        bool isLocked __attribute__ ((aligned(1)));
        bool isEnd __attribute__ ((aligned(1)));
        uint8_t progressPercent __attribute__ ((aligned(1)));
        ::android::hardware::hidl_vec<uint32_t> frequencies __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint32_t> symbolRates __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy hierarchy __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendAnalogType analogType __attribute__ ((aligned(4)));
        ::android::hardware::hidl_vec<uint8_t> plpIds __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> groupIds __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint16_t> inputStreamIds __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard std __attribute__ ((aligned(4)));
        ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo> atsc3PlpInfos __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_union) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendScanMessage) == 8, "wrong alignment");

/**
 * Frontend Event Type.
 */
enum class FrontendEventType : uint32_t {
    /**
     * The frontend has locked to the signal specified by the tune method.
     */
    LOCKED = 0u,
    /**
     * The frontend is unable to lock to the signal specified by the tune method.
     */
    NO_SIGNAL = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendEventType.LOCKED implicitly + 1 */,
    /**
     * The frontend has lost the lock to the signal specified by the tune method.
     */
    LOST_LOCK = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendEventType.NO_SIGNAL implicitly + 1 */,
};

/**
 * Frontend Status Type.
 */
enum class FrontendStatusType : uint32_t {
    /**
     * Lock status for Demod.
     */
    DEMOD_LOCK = 0u,
    /**
     * Signal to Noise Ratio.
     */
    SNR = 1u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.DEMOD_LOCK implicitly + 1 */,
    /**
     * Bit Error Ratio.
     */
    BER = 2u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.SNR implicitly + 1 */,
    /**
     * Packages Error Ratio.
     */
    PER = 3u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.BER implicitly + 1 */,
    /**
     * Bit Error Ratio before FEC.
     */
    PRE_BER = 4u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.PER implicitly + 1 */,
    /**
     * Signal Quality (0..100). Good data over total data in percent can be
     * used as a way to present Signal Quality.
     */
    SIGNAL_QUALITY = 5u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.PRE_BER implicitly + 1 */,
    /**
     * Signal Strength.
     */
    SIGNAL_STRENGTH = 6u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.SIGNAL_QUALITY implicitly + 1 */,
    /**
     * Symbol Rate.
     */
    SYMBOL_RATE = 7u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.SIGNAL_STRENGTH implicitly + 1 */,
    /**
     * Forward Error Correction Type.
     */
    FEC = 8u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.SYMBOL_RATE implicitly + 1 */,
    /**
     * Modulation Type.
     */
    MODULATION = 9u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.FEC implicitly + 1 */,
    /**
     * Spectral Inversion Type.
     */
    SPECTRAL = 10u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.MODULATION implicitly + 1 */,
    /**
     * LNB Voltage.
     */
    LNB_VOLTAGE = 11u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.SPECTRAL implicitly + 1 */,
    /**
     * Physical Layer Pipe ID.
     */
    PLP_ID = 12u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.LNB_VOLTAGE implicitly + 1 */,
    /**
     * Status for Emergency Warning Broadcasting System.
     */
    EWBS = 13u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.PLP_ID implicitly + 1 */,
    /**
     * Automatic Gain Control.
     */
    AGC = 14u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.EWBS implicitly + 1 */,
    /**
     * Low Noise Amplifier.
     */
    LNA = 15u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.AGC implicitly + 1 */,
    /**
     * Error status by layer.
     */
    LAYER_ERROR = 16u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.LNA implicitly + 1 */,
    /**
     * Moduration Error Ratio.
     */
    MER = 17u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.LAYER_ERROR implicitly + 1 */,
    /**
     * Difference between tuning frequency and actual locked frequency.
     */
    FREQ_OFFSET = 18u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.MER implicitly + 1 */,
    /**
     * Hierarchy for DVBT.
     */
    HIERARCHY = 19u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.FREQ_OFFSET implicitly + 1 */,
    /**
     * Lock status for RF.
     */
    RF_LOCK = 20u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.HIERARCHY implicitly + 1 */,
    /**
     * PLP information in a frequency band for ATSC3.0 frontend.
     */
    ATSC3_PLP_INFO = 21u /* ::android::hardware::tv::tuner::V1_0::FrontendStatusType.RF_LOCK implicitly + 1 */,
};

/**
 * Status for each tuning PLPs
 */
struct FrontendStatusAtsc3PlpInfo final {
    /**
     * PLP Id value.
     */
    uint8_t plpId __attribute__ ((aligned(1)));
    /**
     * Demod Lock/Unlock status of this particular PLP.
     */
    bool isLocked __attribute__ ((aligned(1)));
    /**
     * Uncorrectable Error Counts (UEC) of this particular PLP since last tune operation.
     */
    uint32_t uec __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo, plpId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo, isLocked) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo, uec) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo) == 4, "wrong alignment");

/**
 * Modulation Type for Frontend's status.
 */
struct FrontendModulationStatus final {
    enum class hidl_discriminator : uint8_t {
        dvbc = 0,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation
        dvbs = 1,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation
        isdbs = 2,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation
        isdbs3 = 3,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation
        isdbt = 4,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation
    };

    FrontendModulationStatus();
    ~FrontendModulationStatus();
    FrontendModulationStatus(FrontendModulationStatus&&);
    FrontendModulationStatus(const FrontendModulationStatus&);
    FrontendModulationStatus& operator=(FrontendModulationStatus&&);
    FrontendModulationStatus& operator=(const FrontendModulationStatus&);

    void dvbc(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation& dvbc();
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation dvbc() const;

    void dvbs(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation& dvbs();
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation dvbs() const;

    void isdbs(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation);
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation& isdbs();
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation isdbs() const;

    void isdbs3(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation);
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation& isdbs3();
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation isdbs3() const;

    void isdbt(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation);
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation& isdbt();
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation isdbt() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation dvbc __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation dvbs __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation isdbs __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation isdbs3 __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation isdbt __attribute__ ((aligned(4)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_union) == 4, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_union) == 4, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus) == 4, "wrong alignment");

/**
 *  Power Voltage Type for LNB.
 */
enum class LnbVoltage : uint32_t {
    NONE = 0u,
    VOLTAGE_5V = 1u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.NONE implicitly + 1 */,
    VOLTAGE_11V = 2u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_5V implicitly + 1 */,
    VOLTAGE_12V = 3u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_11V implicitly + 1 */,
    VOLTAGE_13V = 4u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_12V implicitly + 1 */,
    VOLTAGE_14V = 5u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_13V implicitly + 1 */,
    VOLTAGE_15V = 6u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_14V implicitly + 1 */,
    VOLTAGE_18V = 7u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_15V implicitly + 1 */,
    VOLTAGE_19V = 8u /* ::android::hardware::tv::tuner::V1_0::LnbVoltage.VOLTAGE_18V implicitly + 1 */,
};

/**
 *  The status for Frontend.
 */
struct FrontendStatus final {
    enum class hidl_discriminator : uint8_t {
        /**
         * Lock status for Demod in True/False.
         */
        isDemodLocked = 0,  // bool
        /**
         * SNR value measured by 0.001 dB.
         */
        snr = 1,  // int32_t
        /**
         * The number of error bit per 1 billion bits.
         */
        ber = 2,  // uint32_t
        /**
         * The number of error package per 1 billion packages.
         */
        per = 3,  // uint32_t
        /**
         * The number of error bit per 1 billion bits before FEC.
         */
        preBer = 4,  // uint32_t
        /**
         * Signal Quality in percent.
         */
        signalQuality = 5,  // uint32_t
        /**
         * Signal Strength measured by 0.001 dBm.
         */
        signalStrength = 6,  // int32_t
        /**
         * Symbols per second
         */
        symbolRate = 7,  // uint32_t
        innerFec = 8,  // ::android::hardware::tv::tuner::V1_0::FrontendInnerFec
        modulation = 9,  // ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus
        inversion = 10,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion
        lnbVoltage = 11,  // ::android::hardware::tv::tuner::V1_0::LnbVoltage
        plpId = 12,  // uint8_t
        isEWBS = 13,  // bool
        /**
         * AGC value is normalized from 0 to 255.
         */
        agc = 14,  // uint8_t
        isLnaOn = 15,  // bool
        isLayerError = 16,  // ::android::hardware::hidl_vec<bool>
        /**
         * MER value measured by 0.001 dB
         */
        mer = 17,  // int32_t
        /**
         * Frequency difference in Hertz.
         */
        freqOffset = 18,  // int32_t
        hierarchy = 19,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy
        isRfLocked = 20,  // bool
        /**
         * A list of PLP status for tuned PLPs for ATSC3 frontend.
         */
        plpInfo = 21,  // ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo>
    };

    FrontendStatus();
    ~FrontendStatus();
    FrontendStatus(FrontendStatus&&);
    FrontendStatus(const FrontendStatus&);
    FrontendStatus& operator=(FrontendStatus&&);
    FrontendStatus& operator=(const FrontendStatus&);

    void isDemodLocked(bool);
    bool& isDemodLocked();
    bool isDemodLocked() const;

    void snr(int32_t);
    int32_t& snr();
    int32_t snr() const;

    void ber(uint32_t);
    uint32_t& ber();
    uint32_t ber() const;

    void per(uint32_t);
    uint32_t& per();
    uint32_t per() const;

    void preBer(uint32_t);
    uint32_t& preBer();
    uint32_t preBer() const;

    void signalQuality(uint32_t);
    uint32_t& signalQuality();
    uint32_t signalQuality() const;

    void signalStrength(int32_t);
    int32_t& signalStrength();
    int32_t signalStrength() const;

    void symbolRate(uint32_t);
    uint32_t& symbolRate();
    uint32_t symbolRate() const;

    void innerFec(::android::hardware::tv::tuner::V1_0::FrontendInnerFec);
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec& innerFec();
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec innerFec() const;

    void modulation(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus&);
    void modulation(::android::hardware::tv::tuner::V1_0::FrontendModulationStatus&&);
    ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& modulation();
    const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& modulation() const;

    void inversion(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion& inversion();
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion inversion() const;

    void lnbVoltage(::android::hardware::tv::tuner::V1_0::LnbVoltage);
    ::android::hardware::tv::tuner::V1_0::LnbVoltage& lnbVoltage();
    ::android::hardware::tv::tuner::V1_0::LnbVoltage lnbVoltage() const;

    void plpId(uint8_t);
    uint8_t& plpId();
    uint8_t plpId() const;

    void isEWBS(bool);
    bool& isEWBS();
    bool isEWBS() const;

    void agc(uint8_t);
    uint8_t& agc();
    uint8_t agc() const;

    void isLnaOn(bool);
    bool& isLnaOn();
    bool isLnaOn() const;

    void isLayerError(const ::android::hardware::hidl_vec<bool>&);
    void isLayerError(::android::hardware::hidl_vec<bool>&&);
    ::android::hardware::hidl_vec<bool>& isLayerError();
    const ::android::hardware::hidl_vec<bool>& isLayerError() const;

    void mer(int32_t);
    int32_t& mer();
    int32_t mer() const;

    void freqOffset(int32_t);
    int32_t& freqOffset();
    int32_t freqOffset() const;

    void hierarchy(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy);
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy& hierarchy();
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy hierarchy() const;

    void isRfLocked(bool);
    bool& isRfLocked();
    bool isRfLocked() const;

    void plpInfo(const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo>&);
    void plpInfo(::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo>&&);
    ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo>& plpInfo();
    const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo>& plpInfo() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::FrontendStatus, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        bool isDemodLocked __attribute__ ((aligned(1)));
        int32_t snr __attribute__ ((aligned(4)));
        uint32_t ber __attribute__ ((aligned(4)));
        uint32_t per __attribute__ ((aligned(4)));
        uint32_t preBer __attribute__ ((aligned(4)));
        uint32_t signalQuality __attribute__ ((aligned(4)));
        int32_t signalStrength __attribute__ ((aligned(4)));
        uint32_t symbolRate __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendInnerFec innerFec __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus modulation __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion inversion __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::LnbVoltage lnbVoltage __attribute__ ((aligned(4)));
        uint8_t plpId __attribute__ ((aligned(1)));
        bool isEWBS __attribute__ ((aligned(1)));
        uint8_t agc __attribute__ ((aligned(1)));
        bool isLnaOn __attribute__ ((aligned(1)));
        ::android::hardware::hidl_vec<bool> isLayerError __attribute__ ((aligned(8)));
        int32_t mer __attribute__ ((aligned(4)));
        int32_t freqOffset __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy hierarchy __attribute__ ((aligned(4)));
        bool isRfLocked __attribute__ ((aligned(1)));
        ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo> plpInfo __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_union) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendStatus) == 8, "wrong alignment");

/**
 *  Information for the Frontend.
 */
struct FrontendInfo final {
    // Forward declaration for forward reference support:
    struct FrontendCapabilities;

    struct FrontendCapabilities final {
        enum class hidl_discriminator : uint8_t {
            analogCaps = 0,  // ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities
            atscCaps = 1,  // ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities
            atsc3Caps = 2,  // ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities
            dvbsCaps = 3,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities
            dvbcCaps = 4,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities
            dvbtCaps = 5,  // ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities
            isdbsCaps = 6,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities
            isdbs3Caps = 7,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities
            isdbtCaps = 8,  // ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities
        };

        FrontendCapabilities();
        ~FrontendCapabilities();
        FrontendCapabilities(FrontendCapabilities&&);
        FrontendCapabilities(const FrontendCapabilities&);
        FrontendCapabilities& operator=(FrontendCapabilities&&);
        FrontendCapabilities& operator=(const FrontendCapabilities&);

        void analogCaps(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities&);
        void analogCaps(::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& analogCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& analogCaps() const;

        void atscCaps(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities&);
        void atscCaps(::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& atscCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& atscCaps() const;

        void atsc3Caps(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities&);
        void atsc3Caps(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& atsc3Caps();
        const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& atsc3Caps() const;

        void dvbsCaps(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities&);
        void dvbsCaps(::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& dvbsCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& dvbsCaps() const;

        void dvbcCaps(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities&);
        void dvbcCaps(::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& dvbcCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& dvbcCaps() const;

        void dvbtCaps(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities&);
        void dvbtCaps(::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& dvbtCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& dvbtCaps() const;

        void isdbsCaps(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities&);
        void isdbsCaps(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& isdbsCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& isdbsCaps() const;

        void isdbs3Caps(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities&);
        void isdbs3Caps(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& isdbs3Caps();
        const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& isdbs3Caps() const;

        void isdbtCaps(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities&);
        void isdbtCaps(::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities&&);
        ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& isdbtCaps();
        const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& isdbtCaps() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities analogCaps __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities atscCaps __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities atsc3Caps __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities dvbsCaps __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities dvbcCaps __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities dvbtCaps __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities isdbsCaps __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities isdbs3Caps __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities isdbtCaps __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_union) == 32, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities) == 40, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities) == 8, "wrong alignment");

    ::android::hardware::tv::tuner::V1_0::FrontendType type __attribute__ ((aligned(4)));
    /**
     * Frequency in Hertz
     */
    uint32_t minFrequency __attribute__ ((aligned(4)));
    /**
     * Frequency in Hertz
     */
    uint32_t maxFrequency __attribute__ ((aligned(4)));
    /**
     * Minimum symbols per second
     */
    uint32_t minSymbolRate __attribute__ ((aligned(4)));
    /**
     * Maximum symbols per second
     */
    uint32_t maxSymbolRate __attribute__ ((aligned(4)));
    /**
     * Range in Hertz
     */
    uint32_t acquireRange __attribute__ ((aligned(4)));
    /**
     * Frontends are assigned with the same exclusiveGroupId if they can't
     * function at same time. For instance, they share same hardware module.
     */
    uint32_t exclusiveGroupId __attribute__ ((aligned(4)));
    /**
     * A list of supported status types which client can inquiry
     */
    ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusType> statusCaps __attribute__ ((aligned(8)));
    ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities frontendCaps __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, minFrequency) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, maxFrequency) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, minSymbolRate) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, maxSymbolRate) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, acquireRange) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, exclusiveGroupId) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, statusCaps) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::FrontendInfo, frontendCaps) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::FrontendInfo) == 88, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::FrontendInfo) == 8, "wrong alignment");

/*
 * Low-Noise Block downconverter (LNB) ID is used to associate with a hardware
 * LNB module.
 */
typedef uint32_t LnbId;

/**
 *  Tone Type for LNB.
 */
enum class LnbTone : int32_t {
    NONE = 0,
    CONTINUOUS = 1 /* ::android::hardware::tv::tuner::V1_0::LnbTone.NONE implicitly + 1 */,
};

/**
 *  The Position of LNB.
 */
enum class LnbPosition : int32_t {
    UNDEFINED = 0,
    POSITION_A = 1 /* ::android::hardware::tv::tuner::V1_0::LnbPosition.UNDEFINED implicitly + 1 */,
    POSITION_B = 2 /* ::android::hardware::tv::tuner::V1_0::LnbPosition.POSITION_A implicitly + 1 */,
};

/**
 * Lnb Event Type.
 */
enum class LnbEventType : uint32_t {
    DISEQC_RX_OVERFLOW = 0u,
    /**
     * If LNB detect that outgoing Diseqc message isn't delivered on time.
     */
    DISEQC_RX_TIMEOUT = 1u /* ::android::hardware::tv::tuner::V1_0::LnbEventType.DISEQC_RX_OVERFLOW implicitly + 1 */,
    /**
     * If LNB detect that the incoming Diseqc message has parity error.
     */
    DISEQC_RX_PARITY_ERROR = 2u /* ::android::hardware::tv::tuner::V1_0::LnbEventType.DISEQC_RX_TIMEOUT implicitly + 1 */,
    /**
     * If LNB detect that the LNB is overload.
     */
    LNB_OVERLOAD = 3u /* ::android::hardware::tv::tuner::V1_0::LnbEventType.DISEQC_RX_PARITY_ERROR implicitly + 1 */,
};

/*
 * Demux ID is used to associate with a hardware demux resource.
 */
typedef uint32_t DemuxId;

/**
 * Filter Main Type specifies the protocol that the filter use to extract data
 * from input stream.
 */
enum class DemuxFilterMainType : uint32_t {
    /**
     * Transport Stream according to ISO/IEC 13818-1.
     */
    TS = 1u /* 1 << 0 */,
    /**
     * MPEG Media Transport Protocol according to ISO/IEC 23008-1.
     */
    MMTP = 2u /* 1 << 1 */,
    /**
     * Internet Protocol.
     */
    IP = 4u /* 1 << 2 */,
    /**
     * Type Length Value according to ITU-R BT.1869.
     */
    TLV = 8u /* 1 << 3 */,
    /**
     * ATSC Link-Layer Protocol according to A/330 ATSC3.0.
     */
    ALP = 16u /* 1 << 4 */,
};

/**
 * TS Filter Type according to ISO/IEC 13818-1
 */
enum class DemuxTsFilterType : uint32_t {
    UNDEFINED = 0u,
    /**
     * A filter to filter Section data out from input stream, and queue the
     * data to the filter's FMQ (Fast Message Queue).
     */
    SECTION = 1u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.UNDEFINED implicitly + 1 */,
    /**
     * A filter to filter Packetized Elementary Stream data out from input
     * stream, and queue the data to the filter's FMQ.
     */
    PES = 2u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.SECTION implicitly + 1 */,
    /**
     * A filter to filter a Transport Stream out from input stream, and queue
     * the data to the filter's FMQ.
     */
    TS = 3u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.PES implicitly + 1 */,
    /**
     * A filter to filter Audio data out from input stream, and send Audio's
     * Metadata to client through onFilterEvent.
     */
    AUDIO = 4u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.TS implicitly + 1 */,
    /**
     * A filter to filter Video data out from input stream, and send Video's
     * Metadata to client through onFilterEvent.
     */
    VIDEO = 5u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.AUDIO implicitly + 1 */,
    /**
     * A filter to set PCR (Program Clock Reference) channel from input stream.
     */
    PCR = 6u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.VIDEO implicitly + 1 */,
    /**
     * A filter to filter data out from input stream, and queue the data to the
     * buffer of the record.
     */
    RECORD = 7u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.PCR implicitly + 1 */,
    /**
     * A filter to filter out Timed External Media Information (TEMI) according
     * to ISO/IEC 13818-1:2013/ DAM 6 from input stream, and send TEMI event to
     * client through onFilterEvent.
     */
    TEMI = 8u /* ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType.RECORD implicitly + 1 */,
};

/**
 * MMTP Filter Type according to ISO/IEC 23008-1
 */
enum class DemuxMmtpFilterType : uint32_t {
    UNDEFINED = 0u,
    /**
     * A filter to filter signaling data out from input stream, and queue the
     * data to the filter's FMQ (Fast Message Queue).
     */
    SECTION = 1u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.UNDEFINED implicitly + 1 */,
    /**
     * A filter to filter MFU (Media fragment unit) out from input stream, and
     * queue the data to the filter's FMQ.
     */
    PES = 2u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.SECTION implicitly + 1 */,
    /**
     * A filter to filter a MMTP stream out from input stream, and queue the
     * data to the filter's FMQ.
     */
    MMTP = 3u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.PES implicitly + 1 */,
    /**
     * A filter to filter Audio data out from input stream, and send Audio's
     * Metadata to client through onFilterEvent.
     */
    AUDIO = 4u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.MMTP implicitly + 1 */,
    /**
     * A filter to filter Video data out from input stream, and send Video's
     * Metadata to client through onFilterEvent.
     */
    VIDEO = 5u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.AUDIO implicitly + 1 */,
    /**
     * A filter to filter data out from input stream, and queue the data to the
     * buffer of the record.
     */
    RECORD = 6u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.VIDEO implicitly + 1 */,
    /**
     * A filter to filter application data out from input stream, and queue the
     * data to the filter's FMQ.
     */
    DOWNLOAD = 7u /* ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType.RECORD implicitly + 1 */,
};

/**
 * IP Filter Type.
 */
enum class DemuxIpFilterType : uint32_t {
    UNDEFINED = 0u,
    /**
     * A filter to filter section data out from input stream, and queue the
     * data to the filter's FMQ (Fast Message Queue).
     */
    SECTION = 1u /* ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType.UNDEFINED implicitly + 1 */,
    /**
     * A filter to set NTP (Network Time Procotol) channel from input stream.
     */
    NTP = 2u /* ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType.SECTION implicitly + 1 */,
    /**
     * A filter to strip out IP message header and queue the data to the
     * filter's FMQ.
     */
    IP_PAYLOAD = 3u /* ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType.NTP implicitly + 1 */,
    /**
     * A filter to filter a IP stream out from input stream. The output can be
     * either upper stream of another filter or queued to the filter's FMQ.
     */
    IP = 4u /* ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType.IP_PAYLOAD implicitly + 1 */,
    /**
     * A filter to strip out IP message header and be a data source of another
     * filter.
     */
    PAYLOAD_THROUGH = 5u /* ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType.IP implicitly + 1 */,
};

/**
 * TLV Filter Type according to ITU-R BT.1869.
 */
enum class DemuxTlvFilterType : uint32_t {
    UNDEFINED = 0u,
    /**
     * A filter to filter signaling data out from input stream, and queue the
     * data to the filter's FMQ (Fast Message Queue).
     */
    SECTION = 1u /* ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType.UNDEFINED implicitly + 1 */,
    /**
     * A filter to filter a TLV stream out from input stream. The output can be
     * either upper stream of another filter or queued to the filter's FMQ.
     */
    TLV = 2u /* ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType.SECTION implicitly + 1 */,
    /**
     * A filter to strip out TLV message header and be a data source of another
     * filter.
     */
    PAYLOAD_THROUGH = 3u /* ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType.TLV implicitly + 1 */,
};

/**
 * ALP Filter Type according to A/330 ATSC3.0.
 */
enum class DemuxAlpFilterType : uint32_t {
    UNDEFINED = 0u,
    /**
     * A filter to filter signaling data out from input stream, and queue the
     * data to the filter's FMQ (Fast Message Queue).
     */
    SECTION = 1u /* ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType.UNDEFINED implicitly + 1 */,
    /**
     * A filter to set PTP (Precision Time Protocol) channel from input stream.
     */
    PTP = 2u /* ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType.SECTION implicitly + 1 */,
    /**
     * A filter to strip out ALP message header and be a data source of another
     * filter.
     */
    PAYLOAD_THROUGH = 3u /* ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType.PTP implicitly + 1 */,
};

/**
 * Demux Filter Type.
 */
struct DemuxFilterType final {
    // Forward declaration for forward reference support:
    struct DemuxFilterSubType;

    struct DemuxFilterSubType final {
        enum class hidl_discriminator : uint8_t {
            tsFilterType = 0,  // ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType
            mmtpFilterType = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType
            ipFilterType = 2,  // ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType
            tlvFilterType = 3,  // ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType
            alpFilterType = 4,  // ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType
        };

        DemuxFilterSubType();
        ~DemuxFilterSubType();
        DemuxFilterSubType(DemuxFilterSubType&&);
        DemuxFilterSubType(const DemuxFilterSubType&);
        DemuxFilterSubType& operator=(DemuxFilterSubType&&);
        DemuxFilterSubType& operator=(const DemuxFilterSubType&);

        void tsFilterType(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType);
        ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType& tsFilterType();
        ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType tsFilterType() const;

        void mmtpFilterType(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType);
        ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType& mmtpFilterType();
        ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType mmtpFilterType() const;

        void ipFilterType(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType);
        ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType& ipFilterType();
        ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType ipFilterType() const;

        void tlvFilterType(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType);
        ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType& tlvFilterType();
        ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType tlvFilterType() const;

        void alpFilterType(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType);
        ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType& alpFilterType();
        ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType alpFilterType() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType tsFilterType __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType mmtpFilterType __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType ipFilterType __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType tlvFilterType __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType alpFilterType __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_union) == 4, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_union) == 4, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType) == 4, "wrong alignment");

    ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType mainType __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType subType __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterType, mainType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterType, subType) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterType) == 12, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterType) == 4, "wrong alignment");

/*
 * Packet ID is used to specify packets in transport stream.
 */
typedef uint16_t DemuxTpid;

/*
 * Packet ID is used to specify packets in MMTP
 */
typedef uint16_t DemuxMmtpPid;

/**
 * Demux Packet ID.
 */
struct DemuxPid final {
    enum class hidl_discriminator : uint8_t {
        tPid = 0,  // uint16_t
        mmtpPid = 1,  // uint16_t
    };

    DemuxPid();
    ~DemuxPid();
    DemuxPid(DemuxPid&&);
    DemuxPid(const DemuxPid&);
    DemuxPid& operator=(DemuxPid&&);
    DemuxPid& operator=(const DemuxPid&);

    void tPid(uint16_t);
    uint16_t& tPid();
    uint16_t tPid() const;

    void mmtpPid(uint16_t);
    uint16_t& mmtpPid();
    uint16_t mmtpPid() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::DemuxPid, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        uint16_t tPid __attribute__ ((aligned(2)));
        uint16_t mmtpPid __attribute__ ((aligned(2)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_union) == 2, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_union) == 2, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxPid) == 4, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxPid) == 2, "wrong alignment");

enum class Constant : uint32_t {
    /**
     * An invalid packet ID in transport stream according to ISO/IEC 13818-1.
     */
    INVALID_TS_PID = 65535u /* 0xFFFF */,
    /**
     * An invalid Stream ID.
     */
    INVALID_STREAM_ID = 65535u /* 0xFFFF */,
    /**
     * An invalid Filter ID.
     */
    INVALID_FILTER_ID = 4294967295u /* 0xFFFFFFFF */,
    /**
     * An invalid AV sync hardware ID.
     */
    INVALID_AV_SYNC_ID = 4294967295u /* 0xFFFFFFFF */,
};

/**
 * A status of data in the filter's buffer.
 */
enum class DemuxFilterStatus : uint8_t {
    /**
     * The data in the filter buffer is ready to be read.
     */
    DATA_READY = 1 /* 1 << 0 */,
    /**
     * The available data amount in the filter buffer is at low level which is
     * set to 25 percent by default.
     */
    LOW_WATER = 2 /* 1 << 1 */,
    /**
     * The available data amount in the filter buffer is at high level which is
     * set to 75 percent by default.
     */
    HIGH_WATER = 4 /* 1 << 2 */,
    /**
     * The data in the filter buffer is full and newly filtered data is being
     * discarded.
     */
    OVERFLOW = 8 /* 1 << 3 */,
};

/**
 * Indexes can be tagged through TS (Transport Stream) header.
 */
enum class DemuxTsIndex : uint32_t {
    FIRST_PACKET = 1u /* 1 << 0 */,
    PAYLOAD_UNIT_START_INDICATOR = 2u /* 1 << 1 */,
    CHANGE_TO_NOT_SCRAMBLED = 4u /* 1 << 2 */,
    CHANGE_TO_EVEN_SCRAMBLED = 8u /* 1 << 3 */,
    CHANGE_TO_ODD_SCRAMBLED = 16u /* 1 << 4 */,
    DISCONTINUITY_INDICATOR = 32u /* 1 << 5 */,
    RANDOM_ACCESS_INDICATOR = 64u /* 1 << 6 */,
    PRIORITY_INDICATOR = 128u /* 1 << 7 */,
    PCR_FLAG = 256u /* 1 << 8 */,
    OPCR_FLAG = 512u /* 1 << 9 */,
    SPLICING_POINT_FLAG = 1024u /* 1 << 10 */,
    PRIVATE_DATA = 2048u /* 1 << 11 */,
    ADAPTATION_EXTENSION_FLAG = 4096u /* 1 << 12 */,
};

/**
 * Indexes can be tagged by Start Code in PES (Packetized Elementary Stream)
 * according to ISO/IEC 13818-1.
 */
enum class DemuxScIndex : uint32_t {
    /**
     * Start Code is for a new I Frame
     */
    I_FRAME = 1u /* 1 << 0 */,
    /**
     * Start Code is for a new P Frame
     */
    P_FRAME = 2u /* 1 << 1 */,
    /**
     * Start Code is for a new B Frame
     */
    B_FRAME = 4u /* 1 << 2 */,
    /**
     * Start Code is for a new Sequence
     */
    SEQUENCE = 8u /* 1 << 3 */,
};

/**
 * Indexes can be tagged by NAL unit group in HEVC
 * according to ISO/IEC 23008-2.
 */
enum class DemuxScHevcIndex : uint32_t {
    SPS = 1u /* 1 << 0 */,
    AUD = 2u /* 1 << 1 */,
    SLICE_CE_BLA_W_LP = 4u /* 1 << 2 */,
    SLICE_BLA_W_RADL = 8u /* 1 << 3 */,
    SLICE_BLA_N_LP = 16u /* 1 << 4 */,
    SLICE_IDR_W_RADL = 32u /* 1 << 5 */,
    SLICE_IDR_N_LP = 64u /* 1 << 6 */,
    SLICE_TRAIL_CRA = 128u /* 1 << 7 */,
};

/**
 * Start Code Index type to be used in the filter for record
 */
enum class DemuxRecordScIndexType : uint32_t {
    /**
     * Don't use SC index
     */
    NONE = 0u,
    /**
     * Use Start Code index
     */
    SC = 1u /* ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType.NONE implicitly + 1 */,
    /**
     * Use Start Code index for HEVC
     */
    SC_HEVC = 2u /* ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType.SC implicitly + 1 */,
};

/**
 *  Filter Settings for Record data.
 */
struct DemuxFilterRecordSettings final {
    // Forward declaration for forward reference support:
    struct ScIndexMask;

    struct ScIndexMask final {
        enum class hidl_discriminator : uint8_t {
            sc = 0,  // ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex>
            scHevc = 1,  // ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>
        };

        ScIndexMask();
        ~ScIndexMask();
        ScIndexMask(ScIndexMask&&);
        ScIndexMask(const ScIndexMask&);
        ScIndexMask& operator=(ScIndexMask&&);
        ScIndexMask& operator=(const ScIndexMask&);

        void sc(::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex>);
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex>& sc();
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex> sc() const;

        void scHevc(::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>);
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>& scHevc();
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> scHevc() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex> sc __attribute__ ((aligned(4)));
            ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> scHevc __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_union) == 4, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_union) == 4, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask) == 4, "wrong alignment");

    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxTsIndex> tsIndexMask __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType scIndexType __attribute__ ((aligned(4)));
    ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask scIndexMask __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings, tsIndexMask) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings, scIndexType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings, scIndexMask) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings) == 16, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings) == 4, "wrong alignment");

/**
 *  Bits Settings for Section Filter.
 */
struct DemuxFilterSectionBits final {
    /**
     * The bytes are configured for Section Filter
     */
    ::android::hardware::hidl_vec<uint8_t> filter __attribute__ ((aligned(8)));
    /**
     * Active bits in the configured bytes to be used for filtering
     */
    ::android::hardware::hidl_vec<uint8_t> mask __attribute__ ((aligned(8)));
    /**
     * Do positive match at the bit position of the configured bytes when the
     * bit at same position of the mode is 0.
     * Do negative match at the bit position of the configured bytes when the
     * bit at same position of the mode is 1.
     */
    ::android::hardware::hidl_vec<uint8_t> mode __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits, filter) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits, mask) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits, mode) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits) == 48, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits) == 8, "wrong alignment");

/**
 *  Filter Settings for Section data according to ISO/IEC 13818-1.
 */
struct DemuxFilterSectionSettings final {
    // Forward declaration for forward reference support:
    struct Condition;

    struct Condition final {
        // Forward declaration for forward reference support:
        struct TableInfo;

        struct TableInfo final {
            /**
             * Table ID for Section Filter
             */
            uint16_t tableId __attribute__ ((aligned(2)));
            /**
             * Version number for Section Filter
             */
            uint16_t version __attribute__ ((aligned(2)));
        };

        static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo, tableId) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo, version) == 2, "wrong offset");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo) == 4, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo) == 2, "wrong alignment");

        enum class hidl_discriminator : uint8_t {
            sectionBits = 0,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits
            tableInfo = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo
        };

        Condition();
        ~Condition();
        Condition(Condition&&);
        Condition(const Condition&);
        Condition& operator=(Condition&&);
        Condition& operator=(const Condition&);

        void sectionBits(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits&);
        void sectionBits(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& sectionBits();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& sectionBits() const;

        void tableInfo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo&);
        void tableInfo(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& tableInfo();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& tableInfo() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits sectionBits __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo tableInfo __attribute__ ((aligned(2)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_union) == 48, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition) == 8, "wrong alignment");

    ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition condition __attribute__ ((aligned(8)));
    /**
     * true if the filter checks CRC and discards data with wrong CRC
     */
    bool isCheckCrc __attribute__ ((aligned(1)));
    /**
     * true if the filter repeats the data with the same version
     */
    bool isRepeat __attribute__ ((aligned(1)));
    /**
     * true if the filter send onFilterStatus instead of onFilterEvent.
     */
    bool isRaw __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings, condition) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings, isCheckCrc) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings, isRepeat) == 57, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings, isRaw) == 58, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings) == 64, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings) == 8, "wrong alignment");

typedef uint16_t DemuxStreamId;

/**
 *  Filter Settings for a PES Data.
 */
struct DemuxFilterPesDataSettings final {
    uint16_t streamId __attribute__ ((aligned(2)));
    /**
     * true if the filter send onFilterStatus instead of onFilterEvent.
     */
    bool isRaw __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings, isRaw) == 2, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings) == 4, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings) == 2, "wrong alignment");

/**
 *  Filter Settings for a Video and Audio.
 */
struct DemuxFilterAvSettings final {
    /**
     * true if the filter output goes to decoder directly in pass through mode.
     */
    bool isPassthrough __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings, isPassthrough) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings) == 1, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings) == 1, "wrong alignment");

/**
 *  Filter Settings for a Download.
 */
struct DemuxFilterDownloadSettings final {
    uint32_t downloadId __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings, downloadId) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings) == 4, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings) == 4, "wrong alignment");

/**
 *  IP Settings for a IP filter.
 */
struct DemuxIpAddress final {
    // Forward declaration for forward reference support:
    struct SrcIpAddress;
    struct DstIpAddress;

    struct SrcIpAddress final {
        enum class hidl_discriminator : uint8_t {
            /**
             * 0.0.0.0 is invalid. should be ignored.
             */
            v4 = 0,  // ::android::hardware::hidl_array<uint8_t, 4>
            /**
             * 0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0 is invalid. should be ignored.
             */
            v6 = 1,  // ::android::hardware::hidl_array<uint8_t, 16>
        };

        SrcIpAddress();
        ~SrcIpAddress();
        SrcIpAddress(SrcIpAddress&&);
        SrcIpAddress(const SrcIpAddress&);
        SrcIpAddress& operator=(SrcIpAddress&&);
        SrcIpAddress& operator=(const SrcIpAddress&);

        void v4(const ::android::hardware::hidl_array<uint8_t, 4>&);
        void v4(::android::hardware::hidl_array<uint8_t, 4>&&);
        ::android::hardware::hidl_array<uint8_t, 4>& v4();
        const ::android::hardware::hidl_array<uint8_t, 4>& v4() const;

        void v6(const ::android::hardware::hidl_array<uint8_t, 16>&);
        void v6(::android::hardware::hidl_array<uint8_t, 16>&&);
        ::android::hardware::hidl_array<uint8_t, 16>& v6();
        const ::android::hardware::hidl_array<uint8_t, 16>& v6() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::hidl_array<uint8_t, 4> v4 __attribute__ ((aligned(1)));
            ::android::hardware::hidl_array<uint8_t, 16> v6 __attribute__ ((aligned(1)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_union) == 16, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_union) == 1, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress) == 17, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress) == 1, "wrong alignment");

    struct DstIpAddress final {
        enum class hidl_discriminator : uint8_t {
            /**
             * 0.0.0.0 is invalid. should be ignored.
             */
            v4 = 0,  // ::android::hardware::hidl_array<uint8_t, 4>
            /**
             * 0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0 is invalid. should be ignored.
             */
            v6 = 1,  // ::android::hardware::hidl_array<uint8_t, 16>
        };

        DstIpAddress();
        ~DstIpAddress();
        DstIpAddress(DstIpAddress&&);
        DstIpAddress(const DstIpAddress&);
        DstIpAddress& operator=(DstIpAddress&&);
        DstIpAddress& operator=(const DstIpAddress&);

        void v4(const ::android::hardware::hidl_array<uint8_t, 4>&);
        void v4(::android::hardware::hidl_array<uint8_t, 4>&&);
        ::android::hardware::hidl_array<uint8_t, 4>& v4();
        const ::android::hardware::hidl_array<uint8_t, 4>& v4() const;

        void v6(const ::android::hardware::hidl_array<uint8_t, 16>&);
        void v6(::android::hardware::hidl_array<uint8_t, 16>&&);
        ::android::hardware::hidl_array<uint8_t, 16>& v6();
        const ::android::hardware::hidl_array<uint8_t, 16>& v6() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::hidl_array<uint8_t, 4> v4 __attribute__ ((aligned(1)));
            ::android::hardware::hidl_array<uint8_t, 16> v6 __attribute__ ((aligned(1)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_union) == 16, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_union) == 1, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress) == 17, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress) == 1, "wrong alignment");

    ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress srcIpAddress __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress dstIpAddress __attribute__ ((aligned(1)));
    /**
     * 0 is invalid. should be ignored.
     */
    uint16_t srcPort __attribute__ ((aligned(2)));
    /**
     * 0 is invalid. should be ignored.
     */
    uint16_t dstPort __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress, srcIpAddress) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress, dstIpAddress) == 17, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress, srcPort) == 34, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress, dstPort) == 36, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress) == 38, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpAddress) == 2, "wrong alignment");

/**
 *  Filter Settings for a TS filter.
 */
struct DemuxTsFilterSettings final {
    // Forward declaration for forward reference support:
    struct FilterSettings;

    struct FilterSettings final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Not additional parameters. it's used by PCR, TS, TEMI subtype
             * filters.
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            section = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings
            av = 2,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings
            pesData = 3,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings
            record = 4,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings
        };

        FilterSettings();
        ~FilterSettings();
        FilterSettings(FilterSettings&&);
        FilterSettings(const FilterSettings&);
        FilterSettings& operator=(FilterSettings&&);
        FilterSettings& operator=(const FilterSettings&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void section(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&);
        void section(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section() const;

        void av(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings&);
        void av(::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& av();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& av() const;

        void pesData(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings&);
        void pesData(::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& pesData();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& pesData() const;

        void record(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings&);
        void record(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& record();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& record() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings section __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings av __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings pesData __attribute__ ((aligned(2)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings record __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_union) == 64, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings) == 8, "wrong alignment");

    uint16_t tpid __attribute__ ((aligned(2)));
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings filterSettings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings, tpid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings, filterSettings) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings) == 80, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings) == 8, "wrong alignment");

/**
 *  Filter Settings for a MMTP filter.
 */
struct DemuxMmtpFilterSettings final {
    // Forward declaration for forward reference support:
    struct FilterSettings;

    struct FilterSettings final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Not additional parameters. it's used by MMTP subtype filters.
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            section = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings
            av = 2,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings
            pesData = 3,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings
            record = 4,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings
            download = 5,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings
        };

        FilterSettings();
        ~FilterSettings();
        FilterSettings(FilterSettings&&);
        FilterSettings(const FilterSettings&);
        FilterSettings& operator=(FilterSettings&&);
        FilterSettings& operator=(const FilterSettings&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void section(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&);
        void section(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section() const;

        void av(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings&);
        void av(::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& av();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& av() const;

        void pesData(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings&);
        void pesData(::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& pesData();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& pesData() const;

        void record(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings&);
        void record(::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& record();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& record() const;

        void download(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings&);
        void download(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& download();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& download() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings section __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings av __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings pesData __attribute__ ((aligned(2)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings record __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings download __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_union) == 64, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings) == 8, "wrong alignment");

    uint16_t mmtpPid __attribute__ ((aligned(2)));
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings filterSettings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings, mmtpPid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings, filterSettings) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings) == 80, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings) == 8, "wrong alignment");

/**
 *  Filter Settings for a IP filter.
 */
struct DemuxIpFilterSettings final {
    // Forward declaration for forward reference support:
    struct FilterSettings;

    struct FilterSettings final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Not additional parameters. it's used by NTP, IP_PAYLOAD,
             * PAYLOAD_THROUGH subtype filters.
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            section = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings
            /**
             * true if the data from IP subtype go to next filter directly
             */
            bPassthrough = 2,  // bool
        };

        FilterSettings();
        ~FilterSettings();
        FilterSettings(FilterSettings&&);
        FilterSettings(const FilterSettings&);
        FilterSettings& operator=(FilterSettings&&);
        FilterSettings& operator=(const FilterSettings&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void section(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&);
        void section(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section() const;

        void bPassthrough(bool);
        bool& bPassthrough();
        bool bPassthrough() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings section __attribute__ ((aligned(8)));
            bool bPassthrough __attribute__ ((aligned(1)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_union) == 64, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings) == 8, "wrong alignment");

    ::android::hardware::tv::tuner::V1_0::DemuxIpAddress ipAddr __attribute__ ((aligned(2)));
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings filterSettings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings, ipAddr) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings, filterSettings) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings) == 112, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings) == 8, "wrong alignment");

/**
 *  Filter Settings for a TLV filter.
 */
struct DemuxTlvFilterSettings final {
    // Forward declaration for forward reference support:
    struct FilterSettings;

    struct FilterSettings final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Not additional parameters. it's used by PAYLOAD_THROUGH subtype
             * filters.
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            section = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings
            /**
             * true if the data from TLV subtype go to next filter directly
             */
            bPassthrough = 2,  // bool
        };

        FilterSettings();
        ~FilterSettings();
        FilterSettings(FilterSettings&&);
        FilterSettings(const FilterSettings&);
        FilterSettings& operator=(FilterSettings&&);
        FilterSettings& operator=(const FilterSettings&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void section(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&);
        void section(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section() const;

        void bPassthrough(bool);
        bool& bPassthrough();
        bool bPassthrough() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings section __attribute__ ((aligned(8)));
            bool bPassthrough __attribute__ ((aligned(1)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_union) == 64, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings) == 8, "wrong alignment");

    uint8_t packetType __attribute__ ((aligned(1)));
    /**
     * true if the filtered data is commpressed ip packet
     */
    bool isCompressedIpPacket __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings filterSettings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings, packetType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings, isCompressedIpPacket) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings, filterSettings) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings) == 80, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings) == 8, "wrong alignment");

/**
 * ALP Length Type
 */
enum class DemuxAlpLengthType : uint8_t {
    UNDEFINED = 0,
    /**
     * Length does NOT include additional header. Used in US region.
     */
    WITHOUT_ADDITIONAL_HEADER = 1 /* ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType.UNDEFINED implicitly + 1 */,
    /**
     * Length includes additional header. Used in Korea region.
     */
    WITH_ADDITIONAL_HEADER = 2 /* ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType.WITHOUT_ADDITIONAL_HEADER implicitly + 1 */,
};

/**
 *  Filter Settings for a ALP filter.
 */
struct DemuxAlpFilterSettings final {
    // Forward declaration for forward reference support:
    struct FilterSettings;

    struct FilterSettings final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Not additional parameters. it's used by PTP, PAYLOAD_THROUGH subtype
             * filters.
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            section = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings
        };

        FilterSettings();
        ~FilterSettings();
        FilterSettings(FilterSettings&&);
        FilterSettings(const FilterSettings&);
        FilterSettings& operator=(FilterSettings&&);
        FilterSettings& operator=(const FilterSettings&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void section(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&);
        void section(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& section() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings section __attribute__ ((aligned(8)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_union) == 64, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings) == 8, "wrong alignment");

    /**
     * 0: IpV4, 2:Compressed Ip, 4:Signaling.
     */
    uint8_t packetType __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType lengthType __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings filterSettings __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings, packetType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings, lengthType) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings, filterSettings) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings) == 80, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings) == 8, "wrong alignment");

/**
 *  Filter Settings.
 */
struct DemuxFilterSettings final {
    enum class hidl_discriminator : uint8_t {
        ts = 0,  // ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings
        mmtp = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings
        ip = 2,  // ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings
        tlv = 3,  // ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings
        alp = 4,  // ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings
    };

    DemuxFilterSettings();
    ~DemuxFilterSettings();
    DemuxFilterSettings(DemuxFilterSettings&&);
    DemuxFilterSettings(const DemuxFilterSettings&);
    DemuxFilterSettings& operator=(DemuxFilterSettings&&);
    DemuxFilterSettings& operator=(const DemuxFilterSettings&);

    void ts(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings&);
    void ts(::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings&&);
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& ts();
    const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& ts() const;

    void mmtp(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings&);
    void mmtp(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings&&);
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& mmtp();
    const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& mmtp() const;

    void ip(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings&);
    void ip(::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings&&);
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& ip();
    const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& ip() const;

    void tlv(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings&);
    void tlv(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings&&);
    ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& tlv();
    const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& tlv() const;

    void alp(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings&);
    void alp(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings&&);
    ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& alp();
    const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& alp() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings ts __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings mmtp __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings ip __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings tlv __attribute__ ((aligned(8)));
        ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings alp __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_union) == 112, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings) == 120, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSettings) == 8, "wrong alignment");

/**
 * The bits of EventFlag in FMQ (Fast message queue) are used by client to
 * notify HAL the status change.
 */
enum class DemuxQueueNotifyBits : uint32_t {
    /**
     * client writes data and notify HAL the data is ready.
     */
    DATA_READY = 1u /* 1 << 0 */,
    /**
     * client reads data and notify HAL the data is consumed.
     */
    DATA_CONSUMED = 2u /* 1 << 1 */,
};

/**
 *  Filter Event for Section Filter.
 */
struct DemuxFilterSectionEvent final {
    /**
     * Table ID of filtered data
     */
    uint16_t tableId __attribute__ ((aligned(2)));
    /**
     * Version number of filtered data
     */
    uint16_t version __attribute__ ((aligned(2)));
    /**
     * Section number of filtered data
     */
    uint16_t sectionNum __attribute__ ((aligned(2)));
    /**
     * Data size in bytes of filtered data
     */
    uint16_t dataLength __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent, tableId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent, version) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent, sectionNum) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent, dataLength) == 6, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent) == 2, "wrong alignment");

/**
 *  Extra Meta Data from AD (Audio Descriptor) according to
 *  ETSI TS 101 154 V2.1.1.
 */
struct AudioExtraMetaData final {
    uint8_t adFade __attribute__ ((aligned(1)));
    uint8_t adPan __attribute__ ((aligned(1)));
    uint8_t versionTextTag __attribute__ ((aligned(1)));
    uint8_t adGainCenter __attribute__ ((aligned(1)));
    uint8_t adGainFront __attribute__ ((aligned(1)));
    uint8_t adGainSurround __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData, adFade) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData, adPan) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData, versionTextTag) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData, adGainCenter) == 3, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData, adGainFront) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData, adGainSurround) == 5, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData) == 6, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData) == 1, "wrong alignment");

/**
 *  Filter Event for Audio or Video Filter.
 */
struct DemuxFilterMediaEvent final {
    // Forward declaration for forward reference support:
    struct ExtraMetaData;

    struct ExtraMetaData final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Not additional parameters. it's used for video.
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            audio = 1,  // ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData
        };

        ExtraMetaData();
        ~ExtraMetaData();
        ExtraMetaData(ExtraMetaData&&);
        ExtraMetaData(const ExtraMetaData&);
        ExtraMetaData& operator=(ExtraMetaData&&);
        ExtraMetaData& operator=(const ExtraMetaData&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void audio(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData&);
        void audio(::android::hardware::tv::tuner::V1_0::AudioExtraMetaData&&);
        ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& audio();
        const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& audio() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData audio __attribute__ ((aligned(1)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_union) == 6, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_union) == 1, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData) == 7, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData) == 1, "wrong alignment");

    uint16_t streamId __attribute__ ((aligned(2)));
    /**
     * true if PTS is present in PES header.
     */
    bool isPtsPresent __attribute__ ((aligned(1)));
    /**
     * Presentation Time Stamp for audio or video frame. It based on 90KHz has
     * the same format as PTS (Presentation Time Stamp).
     */
    uint64_t pts __attribute__ ((aligned(8)));
    /**
     * Data size in bytes of audio or video frame
     */
    uint32_t dataLength __attribute__ ((aligned(4)));
    /**
     *  The offset in the memory block which is shared among multiple
     *  MediaEvents.
     */
    uint32_t offset __attribute__ ((aligned(4)));
    /**
     * A handle associated to the memory where audio or video data stays.
     */
    ::android::hardware::hidl_handle avMemory __attribute__ ((aligned(8)));
    /**
     * True if the avMemory is in secure area, and isn't mappable.
     */
    bool isSecureMemory __attribute__ ((aligned(1)));
    /**
     * An Id is used by HAL to provide additional information for AV data.
     * For secure audio, it's the audio handle used by Audio Track.
     */
    uint64_t avDataId __attribute__ ((aligned(8)));
    /**
     * MPU sequence number of filtered data (only for MMTP)
     */
    uint32_t mpuSequenceNumber __attribute__ ((aligned(4)));
    bool isPesPrivateData __attribute__ ((aligned(1)));
    ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData extraMetaData __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, isPtsPresent) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, pts) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, dataLength) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, offset) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, avMemory) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, isSecureMemory) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, avDataId) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, mpuSequenceNumber) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, isPesPrivateData) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent, extraMetaData) == 61, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent) == 72, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent) == 8, "wrong alignment");

/**
 *  Filter Event for PES data.
 */
struct DemuxFilterPesEvent final {
    uint16_t streamId __attribute__ ((aligned(2)));
    /**
     * Data size in bytes of PES data
     */
    uint16_t dataLength __attribute__ ((aligned(2)));
    /**
     * MPU sequence number of filtered data (only for MMTP)
     */
    uint32_t mpuSequenceNumber __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent, streamId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent, dataLength) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent, mpuSequenceNumber) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent) == 8, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent) == 4, "wrong alignment");

/**
 *  Filter Event for TS Record data.
 */
struct DemuxFilterTsRecordEvent final {
    // Forward declaration for forward reference support:
    struct ScIndexMask;

    struct ScIndexMask final {
        enum class hidl_discriminator : uint8_t {
            sc = 0,  // ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex>
            scHevc = 1,  // ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>
        };

        ScIndexMask();
        ~ScIndexMask();
        ScIndexMask(ScIndexMask&&);
        ScIndexMask(const ScIndexMask&);
        ScIndexMask& operator=(ScIndexMask&&);
        ScIndexMask& operator=(const ScIndexMask&);

        void sc(::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex>);
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex>& sc();
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex> sc() const;

        void scHevc(::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>);
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>& scHevc();
        ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> scHevc() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex> sc __attribute__ ((aligned(4)));
            ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> scHevc __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_union) == 4, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_union) == 4, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask) == 4, "wrong alignment");

    ::android::hardware::tv::tuner::V1_0::DemuxPid pid __attribute__ ((aligned(2)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxTsIndex> tsIndexMask __attribute__ ((aligned(4)));
    /**
     * Indexes of record output
     */
    ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask scIndexMask __attribute__ ((aligned(4)));
    /**
     * Byte number from beginning of the filter's output
     */
    uint64_t byteNumber __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent, pid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent, tsIndexMask) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent, scIndexMask) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent, byteNumber) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent) == 8, "wrong alignment");

/**
 *  Filter Event for Timed External Media Information (TEMI) data.
 */
struct DemuxFilterTemiEvent final {
    /**
     * Presentation Time Stamp for audio or video frame. It based on 90KHz has
     * the same format as PTS (Presentation Time Stamp) in ISO/IEC 13818-1.
     */
    uint64_t pts __attribute__ ((aligned(8)));
    /**
     * TEMI Descriptor Tag
     */
    uint8_t descrTag __attribute__ ((aligned(1)));
    /**
     * TEMI Descriptor
     */
    ::android::hardware::hidl_vec<uint8_t> descrData __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent, pts) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent, descrTag) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent, descrData) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent) == 32, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent) == 8, "wrong alignment");

/**
 *  Filter Event for MMTP Record data.
 */
struct DemuxFilterMmtpRecordEvent final {
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> scHevcIndexMask __attribute__ ((aligned(4)));
    /**
     * Byte number from beginning of the filter's output
     */
    uint64_t byteNumber __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent, scHevcIndexMask) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent, byteNumber) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent) == 16, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent) == 8, "wrong alignment");

/**
 *  Filter Event for Download data.
 */
struct DemuxFilterDownloadEvent final {
    uint32_t itemId __attribute__ ((aligned(4)));
    /**
     * MPU sequence number of filtered data (only for MMTP)
     */
    uint32_t mpuSequenceNumber __attribute__ ((aligned(4)));
    uint32_t itemFragmentIndex __attribute__ ((aligned(4)));
    uint32_t lastItemFragmentIndex __attribute__ ((aligned(4)));
    /**
     * Data size in bytes of filtered data
     */
    uint16_t dataLength __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent, itemId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent, mpuSequenceNumber) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent, itemFragmentIndex) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent, lastItemFragmentIndex) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent, dataLength) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent) == 20, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent) == 4, "wrong alignment");

/**
 *  Filter Event for IP payload data.
 */
struct DemuxFilterIpPayloadEvent final {
    /**
     * Data size in bytes of IP data
     */
    uint16_t dataLength __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent, dataLength) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent) == 2, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent) == 2, "wrong alignment");

/**
 * Filter Event.
 */
struct DemuxFilterEvent final {
    // Forward declaration for forward reference support:
    struct Event;

    struct Event final {
        enum class hidl_discriminator : uint8_t {
            section = 0,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent
            media = 1,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent
            pes = 2,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent
            tsRecord = 3,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent
            mmtpRecord = 4,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent
            download = 5,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent
            ipPayload = 6,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent
            temi = 7,  // ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent
        };

        Event();
        ~Event();
        Event(Event&&);
        Event(const Event&);
        Event& operator=(Event&&);
        Event& operator=(const Event&);

        void section(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent&);
        void section(::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& section();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& section() const;

        void media(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent&);
        void media(::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent& media();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent& media() const;

        void pes(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent&);
        void pes(::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& pes();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& pes() const;

        void tsRecord(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent&);
        void tsRecord(::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& tsRecord();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& tsRecord() const;

        void mmtpRecord(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent&);
        void mmtpRecord(::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& mmtpRecord();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& mmtpRecord() const;

        void download(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent&);
        void download(::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& download();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& download() const;

        void ipPayload(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent&);
        void ipPayload(::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& ipPayload();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& ipPayload() const;

        void temi(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent&);
        void temi(::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent&&);
        ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& temi();
        const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& temi() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent section __attribute__ ((aligned(2)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent media __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent pes __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent tsRecord __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent mmtpRecord __attribute__ ((aligned(8)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent download __attribute__ ((aligned(4)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent ipPayload __attribute__ ((aligned(2)));
            ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent temi __attribute__ ((aligned(8)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_union) == 72, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event) == 80, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event) == 8, "wrong alignment");

    /**
     * An array of events
     */
    ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event> events __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent, events) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent) == 16, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxFilterEvent) == 8, "wrong alignment");

typedef uint32_t AvSyncHwId;

typedef ::android::hardware::hidl_vec<uint8_t> TunerKeyToken;

/**
 * A data format in demux's output or input according to ISO/IEC 13818-1.
 */
enum class DataFormat : uint32_t {
    /**
     * Data is Transport Stream.
     */
    TS = 0u,
    /**
     * Data is Packetized Elementary Stream.
     */
    PES = 1u /* ::android::hardware::tv::tuner::V1_0::DataFormat.TS implicitly + 1 */,
    /**
     * Data is Elementary Stream.
     */
    ES = 2u /* ::android::hardware::tv::tuner::V1_0::DataFormat.PES implicitly + 1 */,
    /**
     * Data is TLV (type-length-value) Stream for JP SHV
     */
    SHV_TLV = 3u /* ::android::hardware::tv::tuner::V1_0::DataFormat.ES implicitly + 1 */,
};

typedef ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus RecordStatus;

/**
 *  The Settings for the record in DVR.
 */
struct RecordSettings final {
    /**
     * Register for interested status events so that the HAL can send these
     * status events back to client.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxFilterStatus> statusMask __attribute__ ((aligned(1)));
    /**
     * Unconsumed data size in bytes in the record. The HAL uses it to trigger
     * OutputStatus::LOW_WATER.
     */
    uint32_t lowThreshold __attribute__ ((aligned(4)));
    /**
     * Unconsumed data size in bytes in the record. The HAL uses it to trigger
     * OutputStatus::High_WATER.
     */
    uint32_t highThreshold __attribute__ ((aligned(4)));
    /**
     * The data format in the record.
     */
    ::android::hardware::tv::tuner::V1_0::DataFormat dataFormat __attribute__ ((aligned(4)));
    /**
     * The packet size in bytes in the record.
     */
    uint8_t packetSize __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::RecordSettings, statusMask) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::RecordSettings, lowThreshold) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::RecordSettings, highThreshold) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::RecordSettings, dataFormat) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::RecordSettings, packetSize) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::RecordSettings) == 20, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::RecordSettings) == 4, "wrong alignment");

/**
 * A status of the playback in DVR.
 */
enum class PlaybackStatus : uint32_t {
    /**
     * The space of the demux's playback is empty.
     */
    SPACE_EMPTY = 1u /* 1 << 0 */,
    /**
     * The spece of the demux's playback is almost empty.
     */
    SPACE_ALMOST_EMPTY = 2u /* 1 << 1 */,
    /**
     * The space of the demux's playback is almost full.
     */
    SPACE_ALMOST_FULL = 4u /* 1 << 2 */,
    /**
     * The space of the demux's playback is full.
     */
    SPACE_FULL = 8u /* 1 << 3 */,
};

/**
 * The Setting for the playback in DVR.
 */
struct PlaybackSettings final {
    /**
     * Register for interested status events so that the HAL can send these
     * status events back to client.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::PlaybackStatus> statusMask __attribute__ ((aligned(4)));
    /**
     * Unused space size in bytes in the playback. The HAL uses it to trigger
     * InputStatus::SPACE_ALMOST_EMPTY.
     */
    uint32_t lowThreshold __attribute__ ((aligned(4)));
    /**
     * Unused space size in bytes in the playback. The HAL uses it to trigger
     * InputStatus::SPACE_ALMOST_FULL.
     */
    uint32_t highThreshold __attribute__ ((aligned(4)));
    /**
     * The data format in the playback.
     */
    ::android::hardware::tv::tuner::V1_0::DataFormat dataFormat __attribute__ ((aligned(4)));
    /**
     * The packet size in bytes in the playback.
     */
    uint8_t packetSize __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::PlaybackSettings, statusMask) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::PlaybackSettings, lowThreshold) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::PlaybackSettings, highThreshold) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::PlaybackSettings, dataFormat) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::PlaybackSettings, packetSize) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::PlaybackSettings) == 20, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::PlaybackSettings) == 4, "wrong alignment");

/**
 * The type of DVR.
 */
enum class DvrType : uint8_t {
    RECORD = 0,
    PLAYBACK = 1 /* ::android::hardware::tv::tuner::V1_0::DvrType.RECORD implicitly + 1 */,
};

/**
 * The Setting for DVR.
 */
struct DvrSettings final {
    enum class hidl_discriminator : uint8_t {
        record = 0,  // ::android::hardware::tv::tuner::V1_0::RecordSettings
        playback = 1,  // ::android::hardware::tv::tuner::V1_0::PlaybackSettings
    };

    DvrSettings();
    ~DvrSettings();
    DvrSettings(DvrSettings&&);
    DvrSettings(const DvrSettings&);
    DvrSettings& operator=(DvrSettings&&);
    DvrSettings& operator=(const DvrSettings&);

    void record(const ::android::hardware::tv::tuner::V1_0::RecordSettings&);
    void record(::android::hardware::tv::tuner::V1_0::RecordSettings&&);
    ::android::hardware::tv::tuner::V1_0::RecordSettings& record();
    const ::android::hardware::tv::tuner::V1_0::RecordSettings& record() const;

    void playback(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings&);
    void playback(::android::hardware::tv::tuner::V1_0::PlaybackSettings&&);
    ::android::hardware::tv::tuner::V1_0::PlaybackSettings& playback();
    const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& playback() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::tv::tuner::V1_0::DvrSettings, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hardware::tv::tuner::V1_0::RecordSettings record __attribute__ ((aligned(4)));
        ::android::hardware::tv::tuner::V1_0::PlaybackSettings playback __attribute__ ((aligned(4)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_union) == 20, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_union) == 4, "wrong alignment");
    static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DvrSettings) == 24, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DvrSettings) == 4, "wrong alignment");

/**
 *  Capabilities for Demux.
 */
struct DemuxCapabilities final {
    /**
     * The number of Demux to be supported.
     */
    uint32_t numDemux __attribute__ ((aligned(4)));
    /**
     * The number of record to be supported.
     */
    uint32_t numRecord __attribute__ ((aligned(4)));
    /**
     * The number of playback to be supported.
     */
    uint32_t numPlayback __attribute__ ((aligned(4)));
    /**
     * The number of TS Filter to be supported.
     */
    uint32_t numTsFilter __attribute__ ((aligned(4)));
    /**
     * The number of Section Filter to be supported.
     */
    uint32_t numSectionFilter __attribute__ ((aligned(4)));
    /**
     * The number of Audio Filter to be supported.
     */
    uint32_t numAudioFilter __attribute__ ((aligned(4)));
    /**
     * The number of Video Filter to be supported.
     */
    uint32_t numVideoFilter __attribute__ ((aligned(4)));
    /**
     * The number of PES Filter to be supported.
     */
    uint32_t numPesFilter __attribute__ ((aligned(4)));
    /**
     * The number of PCR Filter to be supported.
     */
    uint32_t numPcrFilter __attribute__ ((aligned(4)));
    /**
     * The maximum number of bytes is supported in the mask of Section Filter.
     */
    uint32_t numBytesInSectionFilter __attribute__ ((aligned(4)));
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType> filterCaps __attribute__ ((aligned(4)));
    /**
     * The array has same elements as DemuxFilterMainType. linkCaps[i] presents
     * filter's capability as soource for the ith type in DemuxFilterMainType.
     * The jth bit of linkCaps[i] is 1 if the output of ith type filter can be
     * data source for the filter type j.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType>> linkCaps __attribute__ ((aligned(8)));
    bool bTimeFilter __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numDemux) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numRecord) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numPlayback) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numTsFilter) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numSectionFilter) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numAudioFilter) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numVideoFilter) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numPesFilter) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numPcrFilter) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, numBytesInSectionFilter) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, filterCaps) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, linkCaps) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities, bTimeFilter) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities) == 72, "wrong size");
static_assert(__alignof(::android::hardware::tv::tuner::V1_0::DemuxCapabilities) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::Result o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::Result lhs, const ::android::hardware::tv::tuner::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::Result lhs, const ::android::hardware::tv::tuner::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendInnerFec o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendInnerFec o, ::std::ostream* os);
constexpr uint64_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec rhs) {
    return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec rhs) {
    return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t &operator|=(uint64_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec e) {
    v |= static_cast<uint64_t>(e);
    return v;
}
constexpr uint64_t &operator&=(uint64_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendInnerFec e) {
    v &= static_cast<uint64_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAnalogType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAnalogType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendSettings& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendScanType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendScanType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendScanType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendScanType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendScanType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendScanType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendScanType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendScanType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendEventType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendEventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendEventType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendEventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendEventType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendEventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendEventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendEventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendStatusType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendStatusType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendStatusType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::FrontendStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendStatusType lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::FrontendStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendStatusType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::FrontendStatusType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbVoltage o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbVoltage o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbVoltage lhs, const ::android::hardware::tv::tuner::V1_0::LnbVoltage rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbVoltage rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbVoltage lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbVoltage lhs, const ::android::hardware::tv::tuner::V1_0::LnbVoltage rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbVoltage rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbVoltage lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbVoltage e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbVoltage e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatus& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatus& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbTone o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbTone o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbTone lhs, const ::android::hardware::tv::tuner::V1_0::LnbTone rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbTone rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbTone lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbTone lhs, const ::android::hardware::tv::tuner::V1_0::LnbTone rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbTone rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbTone lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbTone e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbTone e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbPosition o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbPosition o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbPosition lhs, const ::android::hardware::tv::tuner::V1_0::LnbPosition rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbPosition rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbPosition lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbPosition lhs, const ::android::hardware::tv::tuner::V1_0::LnbPosition rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbPosition rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbPosition lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbPosition e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbPosition e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbEventType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbEventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbEventType lhs, const ::android::hardware::tv::tuner::V1_0::LnbEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbEventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::LnbEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbEventType lhs, const ::android::hardware::tv::tuner::V1_0::LnbEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::LnbEventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::LnbEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbEventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::LnbEventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxPid& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxPid& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxPid& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxPid& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxPid& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxPid& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::Constant o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::Constant o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::Constant lhs, const ::android::hardware::tv::tuner::V1_0::Constant rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::Constant rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::Constant lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::Constant lhs, const ::android::hardware::tv::tuner::V1_0::Constant rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::Constant rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::Constant lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::Constant e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::Constant e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxTsIndex o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxTsIndex o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxTsIndex e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxScIndex o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxScIndex o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxScIndex lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScIndex rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxScIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxScIndex lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScIndex rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxScIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxScIndex e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxScIndex e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits lhs, const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits lhs, const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent& o, ::std::ostream*);
// operator== and operator!= are not generated for DemuxFilterMediaEvent

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event& o, ::std::ostream*);
// operator== and operator!= are not generated for Event

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent& o, ::std::ostream*);
// operator== and operator!= are not generated for DemuxFilterEvent

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DataFormat o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DataFormat o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DataFormat lhs, const ::android::hardware::tv::tuner::V1_0::DataFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DataFormat rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::DataFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DataFormat lhs, const ::android::hardware::tv::tuner::V1_0::DataFormat rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::DataFormat rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::DataFormat lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DataFormat e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::DataFormat e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::RecordSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::RecordSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::RecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::RecordSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::RecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::RecordSettings& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::PlaybackStatus o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::PlaybackStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::PlaybackStatus lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::tv::tuner::V1_0::PlaybackStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::PlaybackStatus lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::tv::tuner::V1_0::PlaybackStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::PlaybackStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::tv::tuner::V1_0::PlaybackStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::tv::tuner::V1_0::DvrType o);
static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DvrType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::DvrType lhs, const ::android::hardware::tv::tuner::V1_0::DvrType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::DvrType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::tv::tuner::V1_0::DvrType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::DvrType lhs, const ::android::hardware::tv::tuner::V1_0::DvrType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::tv::tuner::V1_0::DvrType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::tv::tuner::V1_0::DvrType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::DvrType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::tv::tuner::V1_0::DvrType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DvrSettings& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DvrSettings& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DvrSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DvrSettings& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DvrSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DvrSettings& rhs);

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& o);
static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::SUCCESS) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::SUCCESS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::UNAVAILABLE) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::UNAVAILABLE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::NOT_INITIALIZED) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::NOT_INITIALIZED)) {
        os += (first ? "" : " | ");
        os += "NOT_INITIALIZED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::NOT_INITIALIZED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::INVALID_STATE) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::INVALID_STATE)) {
        os += (first ? "" : " | ");
        os += "INVALID_STATE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::INVALID_STATE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::INVALID_ARGUMENT) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::INVALID_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::INVALID_ARGUMENT;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::OUT_OF_MEMORY) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::OUT_OF_MEMORY)) {
        os += (first ? "" : " | ");
        os += "OUT_OF_MEMORY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::OUT_OF_MEMORY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Result::UNKNOWN_ERROR) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::Result::UNKNOWN_ERROR)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_ERROR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Result::UNKNOWN_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::Result::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Result::UNAVAILABLE) {
        return "UNAVAILABLE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Result::NOT_INITIALIZED) {
        return "NOT_INITIALIZED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Result::INVALID_STATE) {
        return "INVALID_STATE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Result::INVALID_ARGUMENT) {
        return "INVALID_ARGUMENT";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Result::OUT_OF_MEMORY) {
        return "OUT_OF_MEMORY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Result::UNKNOWN_ERROR) {
        return "UNKNOWN_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::ANALOG) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::ANALOG)) {
        os += (first ? "" : " | ");
        os += "ANALOG";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::ANALOG;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::ATSC)) {
        os += (first ? "" : " | ");
        os += "ATSC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::ATSC3)) {
        os += (first ? "" : " | ");
        os += "ATSC3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::DVBC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::DVBC)) {
        os += (first ? "" : " | ");
        os += "DVBC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::DVBC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::DVBS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::DVBS)) {
        os += (first ? "" : " | ");
        os += "DVBS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::DVBS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::DVBT) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::DVBT)) {
        os += (first ? "" : " | ");
        os += "DVBT";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::DVBT;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS)) {
        os += (first ? "" : " | ");
        os += "ISDBS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS3)) {
        os += (first ? "" : " | ");
        os += "ISDBS3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBT) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendType::ISDBT)) {
        os += (first ? "" : " | ");
        os += "ISDBT";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::ANALOG) {
        return "ANALOG";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC) {
        return "ATSC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC3) {
        return "ATSC3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::DVBC) {
        return "DVBC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::DVBS) {
        return "DVBS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::DVBT) {
        return "DVBT";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS) {
        return "ISDBS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS3) {
        return "ISDBS3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBT) {
        return "ISDBT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendInnerFec>(uint64_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendInnerFec> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_UNDEFINED) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "FEC_UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::AUTO) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_2) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_2)) {
        os += (first ? "" : " | ");
        os += "FEC_1_2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_3) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_3)) {
        os += (first ? "" : " | ");
        os += "FEC_1_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_4) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_4)) {
        os += (first ? "" : " | ");
        os += "FEC_1_4";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_4;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_5) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_5)) {
        os += (first ? "" : " | ");
        os += "FEC_1_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_3) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_3)) {
        os += (first ? "" : " | ");
        os += "FEC_2_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_5) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_5)) {
        os += (first ? "" : " | ");
        os += "FEC_2_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_9) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_9)) {
        os += (first ? "" : " | ");
        os += "FEC_2_9";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_9;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_4) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_4)) {
        os += (first ? "" : " | ");
        os += "FEC_3_4";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_4;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_5) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_5)) {
        os += (first ? "" : " | ");
        os += "FEC_3_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_5) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_5)) {
        os += (first ? "" : " | ");
        os += "FEC_4_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_15) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_15)) {
        os += (first ? "" : " | ");
        os += "FEC_4_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_6) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_6)) {
        os += (first ? "" : " | ");
        os += "FEC_5_6";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_6;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_9) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_9)) {
        os += (first ? "" : " | ");
        os += "FEC_5_9";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_9;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_6_7) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_6_7)) {
        os += (first ? "" : " | ");
        os += "FEC_6_7";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_6_7;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_8) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_8)) {
        os += (first ? "" : " | ");
        os += "FEC_7_8";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_8;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_9) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_9)) {
        os += (first ? "" : " | ");
        os += "FEC_7_9";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_9;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_15) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_15)) {
        os += (first ? "" : " | ");
        os += "FEC_7_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_9) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_9)) {
        os += (first ? "" : " | ");
        os += "FEC_8_9";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_9;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_15) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_15)) {
        os += (first ? "" : " | ");
        os += "FEC_8_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_10) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_10)) {
        os += (first ? "" : " | ");
        os += "FEC_9_10";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_10;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_20) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_20)) {
        os += (first ? "" : " | ");
        os += "FEC_9_20";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_20;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_15) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_15)) {
        os += (first ? "" : " | ");
        os += "FEC_11_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_20) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_20)) {
        os += (first ? "" : " | ");
        os += "FEC_11_20";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_20;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_45)) {
        os += (first ? "" : " | ");
        os += "FEC_11_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_18) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_18)) {
        os += (first ? "" : " | ");
        os += "FEC_13_18";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_18;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_45)) {
        os += (first ? "" : " | ");
        os += "FEC_13_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_14_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_14_45)) {
        os += (first ? "" : " | ");
        os += "FEC_14_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_14_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_23_36) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_23_36)) {
        os += (first ? "" : " | ");
        os += "FEC_23_36";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_23_36;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_25_36) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_25_36)) {
        os += (first ? "" : " | ");
        os += "FEC_25_36";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_25_36;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_26_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_26_45)) {
        os += (first ? "" : " | ");
        os += "FEC_26_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_26_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_28_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_28_45)) {
        os += (first ? "" : " | ");
        os += "FEC_28_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_28_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_29_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_29_45)) {
        os += (first ? "" : " | ");
        os += "FEC_29_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_29_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_31_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_31_45)) {
        os += (first ? "" : " | ");
        os += "FEC_31_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_31_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_32_45) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_32_45)) {
        os += (first ? "" : " | ");
        os += "FEC_32_45";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_32_45;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_77_90) == static_cast<uint64_t>(::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_77_90)) {
        os += (first ? "" : " | ");
        os += "FEC_77_90";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_77_90;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendInnerFec o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_UNDEFINED) {
        return "FEC_UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_2) {
        return "FEC_1_2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_3) {
        return "FEC_1_3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_4) {
        return "FEC_1_4";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_5) {
        return "FEC_1_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_3) {
        return "FEC_2_3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_5) {
        return "FEC_2_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_9) {
        return "FEC_2_9";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_4) {
        return "FEC_3_4";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_5) {
        return "FEC_3_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_5) {
        return "FEC_4_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_15) {
        return "FEC_4_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_6) {
        return "FEC_5_6";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_9) {
        return "FEC_5_9";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_6_7) {
        return "FEC_6_7";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_8) {
        return "FEC_7_8";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_9) {
        return "FEC_7_9";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_15) {
        return "FEC_7_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_9) {
        return "FEC_8_9";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_15) {
        return "FEC_8_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_10) {
        return "FEC_9_10";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_20) {
        return "FEC_9_20";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_15) {
        return "FEC_11_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_20) {
        return "FEC_11_20";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_45) {
        return "FEC_11_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_18) {
        return "FEC_13_18";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_45) {
        return "FEC_13_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_14_45) {
        return "FEC_14_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_23_36) {
        return "FEC_23_36";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_25_36) {
        return "FEC_25_36";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_26_45) {
        return "FEC_26_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_28_45) {
        return "FEC_28_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_29_45) {
        return "FEC_29_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_31_45) {
        return "FEC_31_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_32_45) {
        return "FEC_32_45";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_77_90) {
        return "FEC_77_90";
    }
    std::string os;
    os += toHexString(static_cast<uint64_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendInnerFec o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtscModulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtscModulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_8VSB) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_8VSB)) {
        os += (first ? "" : " | ");
        os += "MOD_8VSB";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_8VSB;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_16VSB) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_16VSB)) {
        os += (first ? "" : " | ");
        os += "MOD_16VSB";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_16VSB;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_8VSB) {
        return "MOD_8VSB";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_16VSB) {
        return "MOD_16VSB";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtscModulation o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtscModulation>(o.modulationCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& rhs) {
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtscCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_QPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_QPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_QPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_QPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_16QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_16QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_16QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_16QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_64QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_64QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_64QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_64QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_256QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_256QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_256QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_256QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_1024QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_1024QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_1024QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_1024QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_4096QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_4096QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_4096QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_4096QAM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_QPSK) {
        return "MOD_QPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_16QAM) {
        return "MOD_16QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_64QAM) {
        return "MOD_64QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_256QAM) {
        return "MOD_256QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_1024QAM) {
        return "MOD_1024QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_4096QAM) {
        return "MOD_4096QAM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_6MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_6MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_6MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_6MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_7MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_7MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_7MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_7MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_8MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_8MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_8MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_8MHZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_6MHZ) {
        return "BANDWIDTH_6MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_7MHZ) {
        return "BANDWIDTH_7MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_8MHZ) {
        return "BANDWIDTH_8MHZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::CTI) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::CTI)) {
        os += (first ? "" : " | ");
        os += "CTI";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::CTI;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::HTI) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::HTI)) {
        os += (first ? "" : " | ");
        os += "HTI";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::HTI;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::CTI) {
        return "CTI";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::HTI) {
        return "HTI";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_2_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_2_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_2_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_2_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_3_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_3_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_3_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_3_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_4_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_4_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_4_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_4_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_5_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_5_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_5_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_5_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_6_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_6_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_6_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_6_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_7_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_7_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_7_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_7_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_8_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_8_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_8_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_8_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_9_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_9_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_9_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_9_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_10_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_10_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_10_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_10_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_11_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_11_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_11_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_11_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_12_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_12_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_12_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_12_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_13_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_13_15)) {
        os += (first ? "" : " | ");
        os += "CODERATE_13_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_13_15;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_2_15) {
        return "CODERATE_2_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_3_15) {
        return "CODERATE_3_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_4_15) {
        return "CODERATE_4_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_5_15) {
        return "CODERATE_5_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_6_15) {
        return "CODERATE_6_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_7_15) {
        return "CODERATE_7_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_8_15) {
        return "CODERATE_8_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_9_15) {
        return "CODERATE_9_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_10_15) {
        return "CODERATE_10_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_11_15) {
        return "CODERATE_11_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_12_15) {
        return "CODERATE_12_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_13_15) {
        return "CODERATE_13_15";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_16K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_16K)) {
        os += (first ? "" : " | ");
        os += "BCH_LDPC_16K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_16K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_64K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_64K)) {
        os += (first ? "" : " | ");
        os += "BCH_LDPC_64K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_64K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_16K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_16K)) {
        os += (first ? "" : " | ");
        os += "CRC_LDPC_16K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_16K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_64K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_64K)) {
        os += (first ? "" : " | ");
        os += "CRC_LDPC_64K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_64K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_16K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_16K)) {
        os += (first ? "" : " | ");
        os += "LDPC_16K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_16K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_64K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_64K)) {
        os += (first ? "" : " | ");
        os += "LDPC_64K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_64K;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_16K) {
        return "BCH_LDPC_16K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_64K) {
        return "BCH_LDPC_64K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_16K) {
        return "CRC_LDPC_16K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_64K) {
        return "CRC_LDPC_64K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_16K) {
        return "LDPC_16K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_64K) {
        return "LDPC_64K";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::UNDEFINED) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::ATSC3_LINKLAYER_PACKET) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::ATSC3_LINKLAYER_PACKET)) {
        os += (first ? "" : " | ");
        os += "ATSC3_LINKLAYER_PACKET";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::ATSC3_LINKLAYER_PACKET;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::BASEBAND_PACKET) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::BASEBAND_PACKET)) {
        os += (first ? "" : " | ");
        os += "BASEBAND_PACKET";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::BASEBAND_PACKET;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::ATSC3_LINKLAYER_PACKET) {
        return "ATSC3_LINKLAYER_PACKET";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::BASEBAND_PACKET) {
        return "BASEBAND_PACKET";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".plpId = ";
    os += ::android::hardware::toString(o.plpId);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += ", .interleaveMode = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.interleaveMode);
    os += ", .codeRate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.codeRate);
    os += ", .fec = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.fec);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& rhs) {
    if (lhs.plpId != rhs.plpId) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    if (lhs.interleaveMode != rhs.interleaveMode) {
        return false;
    }
    if (lhs.codeRate != rhs.codeRate) {
        return false;
    }
    if (lhs.fec != rhs.fec) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3PlpSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .bandwidth = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.bandwidth);
    os += ", .demodOutputFormat = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.demodOutputFormat);
    os += ", .plpSettings = ";
    os += ::android::hardware::toString(o.plpSettings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.bandwidth != rhs.bandwidth) {
        return false;
    }
    if (lhs.demodOutputFormat != rhs.demodOutputFormat) {
        return false;
    }
    if (lhs.plpSettings != rhs.plpSettings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Settings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bandwidthCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth>(o.bandwidthCap);
    os += ", .modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation>(o.modulationCap);
    os += ", .timeInterleaveModeCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode>(o.timeInterleaveModeCap);
    os += ", .codeRateCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate>(o.codeRateCap);
    os += ", .fecCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec>(o.fecCap);
    os += ", .demodOutputFormatCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat>(o.demodOutputFormatCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& rhs) {
    if (lhs.bandwidthCap != rhs.bandwidthCap) {
        return false;
    }
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    if (lhs.timeInterleaveModeCap != rhs.timeInterleaveModeCap) {
        return false;
    }
    if (lhs.codeRateCap != rhs.codeRateCap) {
        return false;
    }
    if (lhs.fecCap != rhs.fecCap) {
        return false;
    }
    if (lhs.demodOutputFormatCap != rhs.demodOutputFormatCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Capabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::UNDEFINED) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::AUTO) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_QPSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_QPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_QPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_QPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8PSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8PSK)) {
        os += (first ? "" : " | ");
        os += "MOD_8PSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8PSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16QAM) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_16QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16PSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16PSK)) {
        os += (first ? "" : " | ");
        os += "MOD_16PSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16PSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32PSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32PSK)) {
        os += (first ? "" : " | ");
        os += "MOD_32PSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32PSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_ACM) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_ACM)) {
        os += (first ? "" : " | ");
        os += "MOD_ACM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_ACM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8APSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_8APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16APSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_16APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32APSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_32APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_64APSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_64APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_64APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_64APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_128APSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_128APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_128APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_128APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_256APSK) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_256APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_256APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_256APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_RESERVED) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_RESERVED)) {
        os += (first ? "" : " | ");
        os += "MOD_RESERVED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_RESERVED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_QPSK) {
        return "MOD_QPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8PSK) {
        return "MOD_8PSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16QAM) {
        return "MOD_16QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16PSK) {
        return "MOD_16PSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32PSK) {
        return "MOD_32PSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_ACM) {
        return "MOD_ACM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8APSK) {
        return "MOD_8APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16APSK) {
        return "MOD_16APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32APSK) {
        return "MOD_32APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_64APSK) {
        return "MOD_64APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_128APSK) {
        return "MOD_128APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_256APSK) {
        return "MOD_256APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_RESERVED) {
        return "MOD_RESERVED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_35) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_35)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_35";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_35;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_25) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_25)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_25";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_25;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_20) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_20)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_20";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_20;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_15) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_15)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_15";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_15;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_10) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_10)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_10";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_10;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_5) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_5)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_5;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_35) {
        return "ROLLOFF_0_35";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_25) {
        return "ROLLOFF_0_25";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_20) {
        return "ROLLOFF_0_20";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_15) {
        return "ROLLOFF_0_15";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_10) {
        return "ROLLOFF_0_10";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_5) {
        return "ROLLOFF_0_5";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::ON) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::ON;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::OFF) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::OFF)) {
        os += (first ? "" : " | ");
        os += "OFF";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::OFF;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::AUTO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::ON) {
        return "ON";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::OFF) {
        return "OFF";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::AUTO) {
        return "AUTO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".fec = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.fec);
    os += ", .isLinear = ";
    os += ::android::hardware::toString(o.isLinear);
    os += ", .isShortFrames = ";
    os += ::android::hardware::toString(o.isShortFrames);
    os += ", .bitsPer1000Symbol = ";
    os += ::android::hardware::toString(o.bitsPer1000Symbol);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& rhs) {
    if (lhs.fec != rhs.fec) {
        return false;
    }
    if (lhs.isLinear != rhs.isLinear) {
        return false;
    }
    if (lhs.isShortFrames != rhs.isShortFrames) {
        return false;
    }
    if (lhs.bitsPer1000Symbol != rhs.bitsPer1000Symbol) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCodeRate& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::AUTO) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S)) {
        os += (first ? "" : " | ");
        os += "S";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2)) {
        os += (first ? "" : " | ");
        os += "S2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2X) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2X)) {
        os += (first ? "" : " | ");
        os += "S2X";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2X;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S) {
        return "S";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2) {
        return "S2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2X) {
        return "S2X";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::MANUAL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::MANUAL)) {
        os += (first ? "" : " | ");
        os += "MANUAL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::MANUAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::MANUAL) {
        return "MANUAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += ", .coderate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.coderate);
    os += ", .symbolRate = ";
    os += ::android::hardware::toString(o.symbolRate);
    os += ", .rolloff = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.rolloff);
    os += ", .pilot = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.pilot);
    os += ", .inputStreamId = ";
    os += ::android::hardware::toString(o.inputStreamId);
    os += ", .standard = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.standard);
    os += ", .vcmMode = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.vcmMode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    if (lhs.coderate != rhs.coderate) {
        return false;
    }
    if (lhs.symbolRate != rhs.symbolRate) {
        return false;
    }
    if (lhs.rolloff != rhs.rolloff) {
        return false;
    }
    if (lhs.pilot != rhs.pilot) {
        return false;
    }
    if (lhs.inputStreamId != rhs.inputStreamId) {
        return false;
    }
    if (lhs.standard != rhs.standard) {
        return false;
    }
    if (lhs.vcmMode != rhs.vcmMode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation>(o.modulationCap);
    os += ", .innerfecCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendInnerFec>(o.innerfecCap);
    os += ", .standard = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard>(o.standard);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& rhs) {
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    if (lhs.innerfecCap != rhs.innerfecCap) {
        return false;
    }
    if (lhs.standard != rhs.standard) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbsCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_16QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_16QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_16QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_16QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_32QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_32QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_32QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_32QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_64QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_64QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_64QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_64QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_128QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_128QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_128QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_128QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_256QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_256QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_256QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_256QAM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_16QAM) {
        return "MOD_16QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_32QAM) {
        return "MOD_32QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_64QAM) {
        return "MOD_64QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_128QAM) {
        return "MOD_128QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_256QAM) {
        return "MOD_256QAM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_NONE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_NONE)) {
        os += (first ? "" : " | ");
        os += "OUTER_FEC_NONE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_NONE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_RS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_RS)) {
        os += (first ? "" : " | ");
        os += "OUTER_FEC_RS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_RS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_NONE) {
        return "OUTER_FEC_NONE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_RS) {
        return "OUTER_FEC_RS";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::UNDEFINED) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::A) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::A)) {
        os += (first ? "" : " | ");
        os += "A";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::A;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::B) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::B)) {
        os += (first ? "" : " | ");
        os += "B";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::B;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::C) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::C)) {
        os += (first ? "" : " | ");
        os += "C";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::C;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::A) {
        return "A";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::B) {
        return "B";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::C) {
        return "C";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::NORMAL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::NORMAL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::INVERTED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::INVERTED)) {
        os += (first ? "" : " | ");
        os += "INVERTED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::INVERTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::INVERTED) {
        return "INVERTED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += ", .fec = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.fec);
    os += ", .symbolRate = ";
    os += ::android::hardware::toString(o.symbolRate);
    os += ", .outerFec = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.outerFec);
    os += ", .annex = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.annex);
    os += ", .spectralInversion = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.spectralInversion);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    if (lhs.fec != rhs.fec) {
        return false;
    }
    if (lhs.symbolRate != rhs.symbolRate) {
        return false;
    }
    if (lhs.outerFec != rhs.outerFec) {
        return false;
    }
    if (lhs.annex != rhs.annex) {
        return false;
    }
    if (lhs.spectralInversion != rhs.spectralInversion) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation>(o.modulationCap);
    os += ", .fecCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendInnerFec>(o.fecCap);
    os += ", .annexCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex>(o.annexCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& rhs) {
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    if (lhs.fecCap != rhs.fecCap) {
        return false;
    }
    if (lhs.annexCap != rhs.annexCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbcCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_8MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_8MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_8MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_8MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_7MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_7MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_7MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_7MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_6MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_6MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_6MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_6MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_5MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_5MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_5MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_5MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_1_7MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_1_7MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_1_7MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_1_7MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_10MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_10MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_10MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_10MHZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_8MHZ) {
        return "BANDWIDTH_8MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_7MHZ) {
        return "BANDWIDTH_7MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_6MHZ) {
        return "BANDWIDTH_6MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_5MHZ) {
        return "BANDWIDTH_5MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_1_7MHZ) {
        return "BANDWIDTH_1_7MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_10MHZ) {
        return "BANDWIDTH_10MHZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_QPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_QPSK)) {
        os += (first ? "" : " | ");
        os += "CONSTELLATION_QPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_QPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_16QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_16QAM)) {
        os += (first ? "" : " | ");
        os += "CONSTELLATION_16QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_16QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_64QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_64QAM)) {
        os += (first ? "" : " | ");
        os += "CONSTELLATION_64QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_64QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_256QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_256QAM)) {
        os += (first ? "" : " | ");
        os += "CONSTELLATION_256QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_256QAM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_QPSK) {
        return "CONSTELLATION_QPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_16QAM) {
        return "CONSTELLATION_16QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_64QAM) {
        return "CONSTELLATION_64QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_256QAM) {
        return "CONSTELLATION_256QAM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_NATIVE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_NATIVE)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_NON_NATIVE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_NATIVE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_NATIVE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_NATIVE)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_1_NATIVE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_NATIVE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_NATIVE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_NATIVE)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_2_NATIVE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_NATIVE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_NATIVE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_NATIVE)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_4_NATIVE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_NATIVE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_INDEPTH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_INDEPTH)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_NON_INDEPTH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_INDEPTH;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_INDEPTH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_INDEPTH)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_1_INDEPTH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_INDEPTH;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_INDEPTH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_INDEPTH)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_2_INDEPTH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_INDEPTH;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_INDEPTH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_INDEPTH)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY_4_INDEPTH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_INDEPTH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_NATIVE) {
        return "HIERARCHY_NON_NATIVE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_NATIVE) {
        return "HIERARCHY_1_NATIVE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_NATIVE) {
        return "HIERARCHY_2_NATIVE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_NATIVE) {
        return "HIERARCHY_4_NATIVE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_INDEPTH) {
        return "HIERARCHY_NON_INDEPTH";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_INDEPTH) {
        return "HIERARCHY_1_INDEPTH";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_INDEPTH) {
        return "HIERARCHY_2_INDEPTH";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_INDEPTH) {
        return "HIERARCHY_4_INDEPTH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_1_2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_1_2)) {
        os += (first ? "" : " | ");
        os += "CODERATE_1_2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_1_2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_2_3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_2_3)) {
        os += (first ? "" : " | ");
        os += "CODERATE_2_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_2_3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_4) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_4)) {
        os += (first ? "" : " | ");
        os += "CODERATE_3_4";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_4;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_5_6) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_5_6)) {
        os += (first ? "" : " | ");
        os += "CODERATE_5_6";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_5_6;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_7_8) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_7_8)) {
        os += (first ? "" : " | ");
        os += "CODERATE_7_8";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_7_8;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_5) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_5)) {
        os += (first ? "" : " | ");
        os += "CODERATE_3_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_4_5) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_4_5)) {
        os += (first ? "" : " | ");
        os += "CODERATE_4_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_4_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_6_7) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_6_7)) {
        os += (first ? "" : " | ");
        os += "CODERATE_6_7";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_6_7;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_8_9) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_8_9)) {
        os += (first ? "" : " | ");
        os += "CODERATE_8_9";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_8_9;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_1_2) {
        return "CODERATE_1_2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_2_3) {
        return "CODERATE_2_3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_4) {
        return "CODERATE_3_4";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_5_6) {
        return "CODERATE_5_6";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_7_8) {
        return "CODERATE_7_8";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_5) {
        return "CODERATE_3_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_4_5) {
        return "CODERATE_4_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_6_7) {
        return "CODERATE_6_7";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_8_9) {
        return "CODERATE_8_9";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_32) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_32)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_1_32";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_32;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_16) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_16)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_1_16";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_16;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_8) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_8)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_1_8";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_8;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_4) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_4)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_1_4";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_4;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_128) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_128)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_1_128";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_128;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_128) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_128)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_19_128";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_128;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_256) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_256)) {
        os += (first ? "" : " | ");
        os += "INTERVAL_19_256";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_256;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_32) {
        return "INTERVAL_1_32";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_16) {
        return "INTERVAL_1_16";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_8) {
        return "INTERVAL_1_8";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_4) {
        return "INTERVAL_1_4";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_128) {
        return "INTERVAL_1_128";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_128) {
        return "INTERVAL_19_128";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_256) {
        return "INTERVAL_19_256";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_2K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_2K)) {
        os += (first ? "" : " | ");
        os += "MODE_2K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_2K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_8K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_8K)) {
        os += (first ? "" : " | ");
        os += "MODE_8K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_8K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_4K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_4K)) {
        os += (first ? "" : " | ");
        os += "MODE_4K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_4K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_1K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_1K)) {
        os += (first ? "" : " | ");
        os += "MODE_1K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_1K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_16K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_16K)) {
        os += (first ? "" : " | ");
        os += "MODE_16K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_16K;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_32K) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_32K)) {
        os += (first ? "" : " | ");
        os += "MODE_32K";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_32K;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_2K) {
        return "MODE_2K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_8K) {
        return "MODE_8K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_4K) {
        return "MODE_4K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_1K) {
        return "MODE_1K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_16K) {
        return "MODE_16K";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_32K) {
        return "MODE_32K";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::MANUAL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::MANUAL)) {
        os += (first ? "" : " | ");
        os += "MANUAL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::MANUAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::MANUAL) {
        return "MANUAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::AUTO) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T)) {
        os += (first ? "" : " | ");
        os += "T";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T2) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T2)) {
        os += (first ? "" : " | ");
        os += "T2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T2;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T) {
        return "T";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T2) {
        return "T2";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .transmissionMode = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.transmissionMode);
    os += ", .bandwidth = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.bandwidth);
    os += ", .constellation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.constellation);
    os += ", .hierarchy = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.hierarchy);
    os += ", .hpCoderate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.hpCoderate);
    os += ", .lpCoderate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.lpCoderate);
    os += ", .guardInterval = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.guardInterval);
    os += ", .isHighPriority = ";
    os += ::android::hardware::toString(o.isHighPriority);
    os += ", .standard = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.standard);
    os += ", .isMiso = ";
    os += ::android::hardware::toString(o.isMiso);
    os += ", .plpMode = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.plpMode);
    os += ", .plpId = ";
    os += ::android::hardware::toString(o.plpId);
    os += ", .plpGroupId = ";
    os += ::android::hardware::toString(o.plpGroupId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.transmissionMode != rhs.transmissionMode) {
        return false;
    }
    if (lhs.bandwidth != rhs.bandwidth) {
        return false;
    }
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    if (lhs.hierarchy != rhs.hierarchy) {
        return false;
    }
    if (lhs.hpCoderate != rhs.hpCoderate) {
        return false;
    }
    if (lhs.lpCoderate != rhs.lpCoderate) {
        return false;
    }
    if (lhs.guardInterval != rhs.guardInterval) {
        return false;
    }
    if (lhs.isHighPriority != rhs.isHighPriority) {
        return false;
    }
    if (lhs.standard != rhs.standard) {
        return false;
    }
    if (lhs.isMiso != rhs.isMiso) {
        return false;
    }
    if (lhs.plpMode != rhs.plpMode) {
        return false;
    }
    if (lhs.plpId != rhs.plpId) {
        return false;
    }
    if (lhs.plpGroupId != rhs.plpGroupId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".transmissionModeCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode>(o.transmissionModeCap);
    os += ", .bandwidthCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth>(o.bandwidthCap);
    os += ", .constellationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation>(o.constellationCap);
    os += ", .coderateCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate>(o.coderateCap);
    os += ", .hierarchyCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy>(o.hierarchyCap);
    os += ", .guardIntervalCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval>(o.guardIntervalCap);
    os += ", .isT2Supported = ";
    os += ::android::hardware::toString(o.isT2Supported);
    os += ", .isMisoSupported = ";
    os += ::android::hardware::toString(o.isMisoSupported);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& rhs) {
    if (lhs.transmissionModeCap != rhs.transmissionModeCap) {
        return false;
    }
    if (lhs.bandwidthCap != rhs.bandwidthCap) {
        return false;
    }
    if (lhs.constellationCap != rhs.constellationCap) {
        return false;
    }
    if (lhs.coderateCap != rhs.coderateCap) {
        return false;
    }
    if (lhs.hierarchyCap != rhs.hierarchyCap) {
        return false;
    }
    if (lhs.guardIntervalCap != rhs.guardIntervalCap) {
        return false;
    }
    if (lhs.isT2Supported != rhs.isT2Supported) {
        return false;
    }
    if (lhs.isMisoSupported != rhs.isMisoSupported) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendDvbtCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::ROLLOFF_0_35) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::ROLLOFF_0_35)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_35";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::ROLLOFF_0_35;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::ROLLOFF_0_35) {
        return "ROLLOFF_0_35";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_BPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_BPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_BPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_BPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_QPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_QPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_QPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_QPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_TC8PSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_TC8PSK)) {
        os += (first ? "" : " | ");
        os += "MOD_TC8PSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_TC8PSK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_BPSK) {
        return "MOD_BPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_QPSK) {
        return "MOD_QPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_TC8PSK) {
        return "MOD_TC8PSK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_1_2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_1_2)) {
        os += (first ? "" : " | ");
        os += "CODERATE_1_2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_1_2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_2_3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_2_3)) {
        os += (first ? "" : " | ");
        os += "CODERATE_2_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_2_3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_3_4) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_3_4)) {
        os += (first ? "" : " | ");
        os += "CODERATE_3_4";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_3_4;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_5_6) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_5_6)) {
        os += (first ? "" : " | ");
        os += "CODERATE_5_6";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_5_6;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_7_8) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_7_8)) {
        os += (first ? "" : " | ");
        os += "CODERATE_7_8";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_7_8;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_1_2) {
        return "CODERATE_1_2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_2_3) {
        return "CODERATE_2_3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_3_4) {
        return "CODERATE_3_4";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_5_6) {
        return "CODERATE_5_6";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_7_8) {
        return "CODERATE_7_8";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::STREAM_ID) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::STREAM_ID)) {
        os += (first ? "" : " | ");
        os += "STREAM_ID";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::STREAM_ID;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::RELATIVE_STREAM_NUMBER) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::RELATIVE_STREAM_NUMBER)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_STREAM_NUMBER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::RELATIVE_STREAM_NUMBER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::STREAM_ID) {
        return "STREAM_ID";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::RELATIVE_STREAM_NUMBER) {
        return "RELATIVE_STREAM_NUMBER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .streamIdType = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.streamIdType);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += ", .coderate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.coderate);
    os += ", .symbolRate = ";
    os += ::android::hardware::toString(o.symbolRate);
    os += ", .rolloff = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.rolloff);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.streamIdType != rhs.streamIdType) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    if (lhs.coderate != rhs.coderate) {
        return false;
    }
    if (lhs.symbolRate != rhs.symbolRate) {
        return false;
    }
    if (lhs.rolloff != rhs.rolloff) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation>(o.modulationCap);
    os += ", .coderateCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate>(o.coderateCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& rhs) {
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    if (lhs.coderateCap != rhs.coderateCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::ROLLOFF_0_03) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::ROLLOFF_0_03)) {
        os += (first ? "" : " | ");
        os += "ROLLOFF_0_03";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::ROLLOFF_0_03;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::ROLLOFF_0_03) {
        return "ROLLOFF_0_03";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_BPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_BPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_BPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_BPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_QPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_QPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_QPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_QPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_8PSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_8PSK)) {
        os += (first ? "" : " | ");
        os += "MOD_8PSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_8PSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_16APSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_16APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_16APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_16APSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_32APSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_32APSK)) {
        os += (first ? "" : " | ");
        os += "MOD_32APSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_32APSK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_BPSK) {
        return "MOD_BPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_QPSK) {
        return "MOD_QPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_8PSK) {
        return "MOD_8PSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_16APSK) {
        return "MOD_16APSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_32APSK) {
        return "MOD_32APSK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_3)) {
        os += (first ? "" : " | ");
        os += "CODERATE_1_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_5) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_5)) {
        os += (first ? "" : " | ");
        os += "CODERATE_2_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_2)) {
        os += (first ? "" : " | ");
        os += "CODERATE_1_2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_5) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_5)) {
        os += (first ? "" : " | ");
        os += "CODERATE_3_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_3)) {
        os += (first ? "" : " | ");
        os += "CODERATE_2_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_3;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_4) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_4)) {
        os += (first ? "" : " | ");
        os += "CODERATE_3_4";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_4;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_9) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_9)) {
        os += (first ? "" : " | ");
        os += "CODERATE_7_9";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_9;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_4_5) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_4_5)) {
        os += (first ? "" : " | ");
        os += "CODERATE_4_5";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_4_5;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_5_6) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_5_6)) {
        os += (first ? "" : " | ");
        os += "CODERATE_5_6";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_5_6;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_8) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_8)) {
        os += (first ? "" : " | ");
        os += "CODERATE_7_8";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_8;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_9_10) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_9_10)) {
        os += (first ? "" : " | ");
        os += "CODERATE_9_10";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_9_10;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_3) {
        return "CODERATE_1_3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_5) {
        return "CODERATE_2_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_2) {
        return "CODERATE_1_2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_5) {
        return "CODERATE_3_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_3) {
        return "CODERATE_2_3";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_4) {
        return "CODERATE_3_4";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_9) {
        return "CODERATE_7_9";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_4_5) {
        return "CODERATE_4_5";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_5_6) {
        return "CODERATE_5_6";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_8) {
        return "CODERATE_7_8";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_9_10) {
        return "CODERATE_9_10";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .streamIdType = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.streamIdType);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += ", .coderate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.coderate);
    os += ", .symbolRate = ";
    os += ::android::hardware::toString(o.symbolRate);
    os += ", .rolloff = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.rolloff);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.streamIdType != rhs.streamIdType) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    if (lhs.coderate != rhs.coderate) {
        return false;
    }
    if (lhs.symbolRate != rhs.symbolRate) {
        return false;
    }
    if (lhs.rolloff != rhs.rolloff) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Settings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation>(o.modulationCap);
    os += ", .coderateCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate>(o.coderateCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& rhs) {
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    if (lhs.coderateCap != rhs.coderateCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Capabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_1) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_1)) {
        os += (first ? "" : " | ");
        os += "MODE_1";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_1;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_2)) {
        os += (first ? "" : " | ");
        os += "MODE_2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_3) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_3)) {
        os += (first ? "" : " | ");
        os += "MODE_3";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_3;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_1) {
        return "MODE_1";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_2) {
        return "MODE_2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_3) {
        return "MODE_3";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_8MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_8MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_8MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_8MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_7MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_7MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_7MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_7MHZ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_6MHZ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_6MHZ)) {
        os += (first ? "" : " | ");
        os += "BANDWIDTH_6MHZ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_6MHZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_8MHZ) {
        return "BANDWIDTH_8MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_7MHZ) {
        return "BANDWIDTH_7MHZ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_6MHZ) {
        return "BANDWIDTH_6MHZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_DQPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_DQPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_DQPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_DQPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_QPSK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_QPSK)) {
        os += (first ? "" : " | ");
        os += "MOD_QPSK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_QPSK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_16QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_16QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_16QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_16QAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_64QAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_64QAM)) {
        os += (first ? "" : " | ");
        os += "MOD_64QAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_64QAM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_DQPSK) {
        return "MOD_DQPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_QPSK) {
        return "MOD_QPSK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_16QAM) {
        return "MOD_16QAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_64QAM) {
        return "MOD_64QAM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .modulation = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.modulation);
    os += ", .bandwidth = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.bandwidth);
    os += ", .mode = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.mode);
    os += ", .coderate = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.coderate);
    os += ", .guardInterval = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.guardInterval);
    os += ", .serviceAreaId = ";
    os += ::android::hardware::toString(o.serviceAreaId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.modulation != rhs.modulation) {
        return false;
    }
    if (lhs.bandwidth != rhs.bandwidth) {
        return false;
    }
    if (lhs.mode != rhs.mode) {
        return false;
    }
    if (lhs.coderate != rhs.coderate) {
        return false;
    }
    if (lhs.guardInterval != rhs.guardInterval) {
        return false;
    }
    if (lhs.serviceAreaId != rhs.serviceAreaId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".modeCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode>(o.modeCap);
    os += ", .bandwidthCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth>(o.bandwidthCap);
    os += ", .modulationCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation>(o.modulationCap);
    os += ", .coderateCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate>(o.coderateCap);
    os += ", .guardIntervalCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval>(o.guardIntervalCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& rhs) {
    if (lhs.modeCap != rhs.modeCap) {
        return false;
    }
    if (lhs.bandwidthCap != rhs.bandwidthCap) {
        return false;
    }
    if (lhs.modulationCap != rhs.modulationCap) {
        return false;
    }
    if (lhs.coderateCap != rhs.coderateCap) {
        return false;
    }
    if (lhs.guardIntervalCap != rhs.guardIntervalCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendIsdbtCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAnalogType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAnalogType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL)) {
        os += (first ? "" : " | ");
        os += "PAL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_M) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_M)) {
        os += (first ? "" : " | ");
        os += "PAL_M";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_M;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_N) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_N)) {
        os += (first ? "" : " | ");
        os += "PAL_N";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_N;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_60) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_60)) {
        os += (first ? "" : " | ");
        os += "PAL_60";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_60;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC)) {
        os += (first ? "" : " | ");
        os += "NTSC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC_443) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC_443)) {
        os += (first ? "" : " | ");
        os += "NTSC_443";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC_443;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::SECAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogType::SECAM)) {
        os += (first ? "" : " | ");
        os += "SECAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::SECAM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAnalogType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL) {
        return "PAL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_M) {
        return "PAL_M";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_N) {
        return "PAL_N";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_60) {
        return "PAL_60";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC) {
        return "NTSC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC_443) {
        return "NTSC_443";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::SECAM) {
        return "SECAM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAnalogType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::AUTO)) {
        os += (first ? "" : " | ");
        os += "AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG)) {
        os += (first ? "" : " | ");
        os += "BG";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_A2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_A2)) {
        os += (first ? "" : " | ");
        os += "BG_A2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_A2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_NICAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_NICAM)) {
        os += (first ? "" : " | ");
        os += "BG_NICAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_NICAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I)) {
        os += (first ? "" : " | ");
        os += "I";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK)) {
        os += (first ? "" : " | ");
        os += "DK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK1_A2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK1_A2)) {
        os += (first ? "" : " | ");
        os += "DK1_A2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK1_A2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK2_A2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK2_A2)) {
        os += (first ? "" : " | ");
        os += "DK2_A2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK2_A2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK3_A2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK3_A2)) {
        os += (first ? "" : " | ");
        os += "DK3_A2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK3_A2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK_NICAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK_NICAM)) {
        os += (first ? "" : " | ");
        os += "DK_NICAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK_NICAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L)) {
        os += (first ? "" : " | ");
        os += "L";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M)) {
        os += (first ? "" : " | ");
        os += "M";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_BTSC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_BTSC)) {
        os += (first ? "" : " | ");
        os += "M_BTSC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_BTSC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_A2) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_A2)) {
        os += (first ? "" : " | ");
        os += "M_A2";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_A2;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_EIAJ) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_EIAJ)) {
        os += (first ? "" : " | ");
        os += "M_EIAJ";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_EIAJ;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I_NICAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I_NICAM)) {
        os += (first ? "" : " | ");
        os += "I_NICAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I_NICAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_NICAM) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_NICAM)) {
        os += (first ? "" : " | ");
        os += "L_NICAM";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_NICAM;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_PRIME) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_PRIME)) {
        os += (first ? "" : " | ");
        os += "L_PRIME";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_PRIME;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::AUTO) {
        return "AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG) {
        return "BG";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_A2) {
        return "BG_A2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_NICAM) {
        return "BG_NICAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I) {
        return "I";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK) {
        return "DK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK1_A2) {
        return "DK1_A2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK2_A2) {
        return "DK2_A2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK3_A2) {
        return "DK3_A2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK_NICAM) {
        return "DK_NICAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L) {
        return "L";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M) {
        return "M";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_BTSC) {
        return "M_BTSC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_A2) {
        return "M_A2";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_EIAJ) {
        return "M_EIAJ";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I_NICAM) {
        return "I_NICAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_NICAM) {
        return "L_NICAM";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_PRIME) {
        return "L_PRIME";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .type = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.type);
    os += ", .sifStandard = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.sifStandard);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& rhs) {
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.sifStandard != rhs.sifStandard) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".typeCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAnalogType>(o.typeCap);
    os += ", .sifStandardCap = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard>(o.sifStandardCap);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& rhs) {
    if (lhs.typeCap != rhs.typeCap) {
        return false;
    }
    if (lhs.sifStandardCap != rhs.sifStandardCap) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendAnalogCapabilities& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::analog: {
            os += ".analog = ";
            os += toString(o.analog());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::atsc: {
            os += ".atsc = ";
            os += toString(o.atsc());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::atsc3: {
            os += ".atsc3 = ";
            os += toString(o.atsc3());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::dvbs: {
            os += ".dvbs = ";
            os += toString(o.dvbs());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::dvbc: {
            os += ".dvbc = ";
            os += toString(o.dvbc());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::dvbt: {
            os += ".dvbt = ";
            os += toString(o.dvbt());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::isdbs: {
            os += ".isdbs = ";
            os += toString(o.isdbs());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::isdbs3: {
            os += ".isdbs3 = ";
            os += toString(o.isdbs3());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::isdbt: {
            os += ".isdbt = ";
            os += toString(o.isdbt());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::analog: {
            return (lhs.analog() == rhs.analog());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::atsc: {
            return (lhs.atsc() == rhs.atsc());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::atsc3: {
            return (lhs.atsc3() == rhs.atsc3());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::dvbs: {
            return (lhs.dvbs() == rhs.dvbs());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::dvbc: {
            return (lhs.dvbc() == rhs.dvbc());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::dvbt: {
            return (lhs.dvbt() == rhs.dvbt());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::isdbs: {
            return (lhs.isdbs() == rhs.isdbs());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::isdbs3: {
            return (lhs.isdbs3() == rhs.isdbs3());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendSettings::hidl_discriminator::isdbt: {
            return (lhs.isdbt() == rhs.isdbt());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendSettings& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendScanType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendScanType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "SCAN_UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_AUTO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_AUTO)) {
        os += (first ? "" : " | ");
        os += "SCAN_AUTO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_AUTO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_BLIND) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_BLIND)) {
        os += (first ? "" : " | ");
        os += "SCAN_BLIND";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_BLIND;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendScanType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_UNDEFINED) {
        return "SCAN_UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_AUTO) {
        return "SCAN_AUTO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_BLIND) {
        return "SCAN_BLIND";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendScanType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendScanMessageType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendScanMessageType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::LOCKED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::LOCKED)) {
        os += (first ? "" : " | ");
        os += "LOCKED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::LOCKED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::END) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::END)) {
        os += (first ? "" : " | ");
        os += "END";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::END;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PROGRESS_PERCENT) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PROGRESS_PERCENT)) {
        os += (first ? "" : " | ");
        os += "PROGRESS_PERCENT";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PROGRESS_PERCENT;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::FREQUENCY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "FREQUENCY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::FREQUENCY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::SYMBOL_RATE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::SYMBOL_RATE)) {
        os += (first ? "" : " | ");
        os += "SYMBOL_RATE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::SYMBOL_RATE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::HIERARCHY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::HIERARCHY)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::HIERARCHY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ANALOG_TYPE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ANALOG_TYPE)) {
        os += (first ? "" : " | ");
        os += "ANALOG_TYPE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ANALOG_TYPE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PLP_IDS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PLP_IDS)) {
        os += (first ? "" : " | ");
        os += "PLP_IDS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PLP_IDS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::GROUP_IDS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::GROUP_IDS)) {
        os += (first ? "" : " | ");
        os += "GROUP_IDS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::GROUP_IDS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::INPUT_STREAM_IDS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::INPUT_STREAM_IDS)) {
        os += (first ? "" : " | ");
        os += "INPUT_STREAM_IDS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::INPUT_STREAM_IDS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::STANDARD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::STANDARD)) {
        os += (first ? "" : " | ");
        os += "STANDARD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::STANDARD;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ATSC3_PLP_INFO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ATSC3_PLP_INFO)) {
        os += (first ? "" : " | ");
        os += "ATSC3_PLP_INFO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ATSC3_PLP_INFO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::LOCKED) {
        return "LOCKED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::END) {
        return "END";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PROGRESS_PERCENT) {
        return "PROGRESS_PERCENT";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::FREQUENCY) {
        return "FREQUENCY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::SYMBOL_RATE) {
        return "SYMBOL_RATE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::HIERARCHY) {
        return "HIERARCHY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ANALOG_TYPE) {
        return "ANALOG_TYPE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PLP_IDS) {
        return "PLP_IDS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::GROUP_IDS) {
        return "GROUP_IDS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::INPUT_STREAM_IDS) {
        return "INPUT_STREAM_IDS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::STANDARD) {
        return "STANDARD";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ATSC3_PLP_INFO) {
        return "ATSC3_PLP_INFO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendScanMessageType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".plpId = ";
    os += ::android::hardware::toString(o.plpId);
    os += ", .bLlsFlag = ";
    os += ::android::hardware::toString(o.bLlsFlag);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& rhs) {
    if (lhs.plpId != rhs.plpId) {
        return false;
    }
    if (lhs.bLlsFlag != rhs.bLlsFlag) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanAtsc3PlpInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator::sStd: {
            os += ".sStd = ";
            os += toString(o.sStd());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator::tStd: {
            os += ".tStd = ";
            os += toString(o.tStd());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator::sifStd: {
            os += ".sifStd = ";
            os += toString(o.sifStd());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator::sStd: {
            return (lhs.sStd() == rhs.sStd());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator::tStd: {
            return (lhs.tStd() == rhs.tStd());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard::hidl_discriminator::sifStd: {
            return (lhs.sifStd() == rhs.sifStd());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::Standard& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::isLocked: {
            os += ".isLocked = ";
            os += toString(o.isLocked());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::isEnd: {
            os += ".isEnd = ";
            os += toString(o.isEnd());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::progressPercent: {
            os += ".progressPercent = ";
            os += toString(o.progressPercent());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::frequencies: {
            os += ".frequencies = ";
            os += toString(o.frequencies());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::symbolRates: {
            os += ".symbolRates = ";
            os += toString(o.symbolRates());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::hierarchy: {
            os += ".hierarchy = ";
            os += toString(o.hierarchy());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::analogType: {
            os += ".analogType = ";
            os += toString(o.analogType());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::plpIds: {
            os += ".plpIds = ";
            os += toString(o.plpIds());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::groupIds: {
            os += ".groupIds = ";
            os += toString(o.groupIds());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::inputStreamIds: {
            os += ".inputStreamIds = ";
            os += toString(o.inputStreamIds());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::std: {
            os += ".std = ";
            os += toString(o.std());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::atsc3PlpInfos: {
            os += ".atsc3PlpInfos = ";
            os += toString(o.atsc3PlpInfos());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::isLocked: {
            return (lhs.isLocked() == rhs.isLocked());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::isEnd: {
            return (lhs.isEnd() == rhs.isEnd());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::progressPercent: {
            return (lhs.progressPercent() == rhs.progressPercent());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::frequencies: {
            return (lhs.frequencies() == rhs.frequencies());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::symbolRates: {
            return (lhs.symbolRates() == rhs.symbolRates());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::hierarchy: {
            return (lhs.hierarchy() == rhs.hierarchy());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::analogType: {
            return (lhs.analogType() == rhs.analogType());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::plpIds: {
            return (lhs.plpIds() == rhs.plpIds());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::groupIds: {
            return (lhs.groupIds() == rhs.groupIds());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::inputStreamIds: {
            return (lhs.inputStreamIds() == rhs.inputStreamIds());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::std: {
            return (lhs.std() == rhs.std());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendScanMessage::hidl_discriminator::atsc3PlpInfos: {
            return (lhs.atsc3PlpInfos() == rhs.atsc3PlpInfos());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendScanMessage& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendEventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOCKED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendEventType::LOCKED)) {
        os += (first ? "" : " | ");
        os += "LOCKED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOCKED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendEventType::NO_SIGNAL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendEventType::NO_SIGNAL)) {
        os += (first ? "" : " | ");
        os += "NO_SIGNAL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendEventType::NO_SIGNAL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOST_LOCK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendEventType::LOST_LOCK)) {
        os += (first ? "" : " | ");
        os += "LOST_LOCK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOST_LOCK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOCKED) {
        return "LOCKED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendEventType::NO_SIGNAL) {
        return "NO_SIGNAL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOST_LOCK) {
        return "LOST_LOCK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendEventType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::FrontendStatusType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::FrontendStatusType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::DEMOD_LOCK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::DEMOD_LOCK)) {
        os += (first ? "" : " | ");
        os += "DEMOD_LOCK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::DEMOD_LOCK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SNR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::SNR)) {
        os += (first ? "" : " | ");
        os += "SNR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SNR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::BER) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::BER)) {
        os += (first ? "" : " | ");
        os += "BER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::BER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PER) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::PER)) {
        os += (first ? "" : " | ");
        os += "PER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PRE_BER) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::PRE_BER)) {
        os += (first ? "" : " | ");
        os += "PRE_BER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PRE_BER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_QUALITY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_QUALITY)) {
        os += (first ? "" : " | ");
        os += "SIGNAL_QUALITY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_QUALITY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_STRENGTH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "SIGNAL_STRENGTH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_STRENGTH;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SYMBOL_RATE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::SYMBOL_RATE)) {
        os += (first ? "" : " | ");
        os += "SYMBOL_RATE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SYMBOL_RATE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FEC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::FEC)) {
        os += (first ? "" : " | ");
        os += "FEC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FEC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MODULATION) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::MODULATION)) {
        os += (first ? "" : " | ");
        os += "MODULATION";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MODULATION;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SPECTRAL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::SPECTRAL)) {
        os += (first ? "" : " | ");
        os += "SPECTRAL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SPECTRAL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNB_VOLTAGE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNB_VOLTAGE)) {
        os += (first ? "" : " | ");
        os += "LNB_VOLTAGE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNB_VOLTAGE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PLP_ID) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::PLP_ID)) {
        os += (first ? "" : " | ");
        os += "PLP_ID";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PLP_ID;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::EWBS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::EWBS)) {
        os += (first ? "" : " | ");
        os += "EWBS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::EWBS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::AGC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::AGC)) {
        os += (first ? "" : " | ");
        os += "AGC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::AGC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNA) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNA)) {
        os += (first ? "" : " | ");
        os += "LNA";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNA;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LAYER_ERROR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::LAYER_ERROR)) {
        os += (first ? "" : " | ");
        os += "LAYER_ERROR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LAYER_ERROR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MER) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::MER)) {
        os += (first ? "" : " | ");
        os += "MER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FREQ_OFFSET) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::FREQ_OFFSET)) {
        os += (first ? "" : " | ");
        os += "FREQ_OFFSET";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FREQ_OFFSET;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::HIERARCHY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::HIERARCHY)) {
        os += (first ? "" : " | ");
        os += "HIERARCHY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::HIERARCHY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::RF_LOCK) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::RF_LOCK)) {
        os += (first ? "" : " | ");
        os += "RF_LOCK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::RF_LOCK;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::FrontendStatusType::ATSC3_PLP_INFO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::FrontendStatusType::ATSC3_PLP_INFO)) {
        os += (first ? "" : " | ");
        os += "ATSC3_PLP_INFO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::FrontendStatusType::ATSC3_PLP_INFO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::FrontendStatusType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::DEMOD_LOCK) {
        return "DEMOD_LOCK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SNR) {
        return "SNR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::BER) {
        return "BER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PER) {
        return "PER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PRE_BER) {
        return "PRE_BER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_QUALITY) {
        return "SIGNAL_QUALITY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_STRENGTH) {
        return "SIGNAL_STRENGTH";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SYMBOL_RATE) {
        return "SYMBOL_RATE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FEC) {
        return "FEC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MODULATION) {
        return "MODULATION";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SPECTRAL) {
        return "SPECTRAL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNB_VOLTAGE) {
        return "LNB_VOLTAGE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PLP_ID) {
        return "PLP_ID";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::EWBS) {
        return "EWBS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::AGC) {
        return "AGC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNA) {
        return "LNA";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LAYER_ERROR) {
        return "LAYER_ERROR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MER) {
        return "MER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FREQ_OFFSET) {
        return "FREQ_OFFSET";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::HIERARCHY) {
        return "HIERARCHY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::RF_LOCK) {
        return "RF_LOCK";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::FrontendStatusType::ATSC3_PLP_INFO) {
        return "ATSC3_PLP_INFO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::FrontendStatusType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".plpId = ";
    os += ::android::hardware::toString(o.plpId);
    os += ", .isLocked = ";
    os += ::android::hardware::toString(o.isLocked);
    os += ", .uec = ";
    os += ::android::hardware::toString(o.uec);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& rhs) {
    if (lhs.plpId != rhs.plpId) {
        return false;
    }
    if (lhs.isLocked != rhs.isLocked) {
        return false;
    }
    if (lhs.uec != rhs.uec) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatusAtsc3PlpInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::dvbc: {
            os += ".dvbc = ";
            os += toString(o.dvbc());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::dvbs: {
            os += ".dvbs = ";
            os += toString(o.dvbs());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::isdbs: {
            os += ".isdbs = ";
            os += toString(o.isdbs());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::isdbs3: {
            os += ".isdbs3 = ";
            os += toString(o.isdbs3());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::isdbt: {
            os += ".isdbt = ";
            os += toString(o.isdbt());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::dvbc: {
            return (lhs.dvbc() == rhs.dvbc());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::dvbs: {
            return (lhs.dvbs() == rhs.dvbs());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::isdbs: {
            return (lhs.isdbs() == rhs.isdbs());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::isdbs3: {
            return (lhs.isdbs3() == rhs.isdbs3());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus::hidl_discriminator::isdbt: {
            return (lhs.isdbt() == rhs.isdbt());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendModulationStatus& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::LnbVoltage>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::LnbVoltage> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::NONE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::NONE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_5V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_5V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_5V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_5V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_11V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_11V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_11V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_11V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_12V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_12V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_12V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_12V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_13V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_13V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_13V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_13V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_14V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_14V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_14V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_14V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_15V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_15V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_15V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_15V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_18V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_18V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_18V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_18V;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_19V) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_19V)) {
        os += (first ? "" : " | ");
        os += "VOLTAGE_19V";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_19V;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbVoltage o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_5V) {
        return "VOLTAGE_5V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_11V) {
        return "VOLTAGE_11V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_12V) {
        return "VOLTAGE_12V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_13V) {
        return "VOLTAGE_13V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_14V) {
        return "VOLTAGE_14V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_15V) {
        return "VOLTAGE_15V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_18V) {
        return "VOLTAGE_18V";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_19V) {
        return "VOLTAGE_19V";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbVoltage o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isDemodLocked: {
            os += ".isDemodLocked = ";
            os += toString(o.isDemodLocked());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::snr: {
            os += ".snr = ";
            os += toString(o.snr());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::ber: {
            os += ".ber = ";
            os += toString(o.ber());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::per: {
            os += ".per = ";
            os += toString(o.per());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::preBer: {
            os += ".preBer = ";
            os += toString(o.preBer());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::signalQuality: {
            os += ".signalQuality = ";
            os += toString(o.signalQuality());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::signalStrength: {
            os += ".signalStrength = ";
            os += toString(o.signalStrength());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::symbolRate: {
            os += ".symbolRate = ";
            os += toString(o.symbolRate());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::innerFec: {
            os += ".innerFec = ";
            os += toString(o.innerFec());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::modulation: {
            os += ".modulation = ";
            os += toString(o.modulation());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::inversion: {
            os += ".inversion = ";
            os += toString(o.inversion());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::lnbVoltage: {
            os += ".lnbVoltage = ";
            os += toString(o.lnbVoltage());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::plpId: {
            os += ".plpId = ";
            os += toString(o.plpId());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isEWBS: {
            os += ".isEWBS = ";
            os += toString(o.isEWBS());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::agc: {
            os += ".agc = ";
            os += toString(o.agc());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isLnaOn: {
            os += ".isLnaOn = ";
            os += toString(o.isLnaOn());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isLayerError: {
            os += ".isLayerError = ";
            os += toString(o.isLayerError());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::mer: {
            os += ".mer = ";
            os += toString(o.mer());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::freqOffset: {
            os += ".freqOffset = ";
            os += toString(o.freqOffset());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::hierarchy: {
            os += ".hierarchy = ";
            os += toString(o.hierarchy());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isRfLocked: {
            os += ".isRfLocked = ";
            os += toString(o.isRfLocked());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::plpInfo: {
            os += ".plpInfo = ";
            os += toString(o.plpInfo());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatus& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isDemodLocked: {
            return (lhs.isDemodLocked() == rhs.isDemodLocked());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::snr: {
            return (lhs.snr() == rhs.snr());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::ber: {
            return (lhs.ber() == rhs.ber());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::per: {
            return (lhs.per() == rhs.per());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::preBer: {
            return (lhs.preBer() == rhs.preBer());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::signalQuality: {
            return (lhs.signalQuality() == rhs.signalQuality());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::signalStrength: {
            return (lhs.signalStrength() == rhs.signalStrength());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::symbolRate: {
            return (lhs.symbolRate() == rhs.symbolRate());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::innerFec: {
            return (lhs.innerFec() == rhs.innerFec());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::modulation: {
            return (lhs.modulation() == rhs.modulation());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::inversion: {
            return (lhs.inversion() == rhs.inversion());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::lnbVoltage: {
            return (lhs.lnbVoltage() == rhs.lnbVoltage());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::plpId: {
            return (lhs.plpId() == rhs.plpId());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isEWBS: {
            return (lhs.isEWBS() == rhs.isEWBS());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::agc: {
            return (lhs.agc() == rhs.agc());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isLnaOn: {
            return (lhs.isLnaOn() == rhs.isLnaOn());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isLayerError: {
            return (lhs.isLayerError() == rhs.isLayerError());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::mer: {
            return (lhs.mer() == rhs.mer());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::freqOffset: {
            return (lhs.freqOffset() == rhs.freqOffset());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::hierarchy: {
            return (lhs.hierarchy() == rhs.hierarchy());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::isRfLocked: {
            return (lhs.isRfLocked() == rhs.isRfLocked());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendStatus::hidl_discriminator::plpInfo: {
            return (lhs.plpInfo() == rhs.plpInfo());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendStatus& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendStatus& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::analogCaps: {
            os += ".analogCaps = ";
            os += toString(o.analogCaps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::atscCaps: {
            os += ".atscCaps = ";
            os += toString(o.atscCaps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::atsc3Caps: {
            os += ".atsc3Caps = ";
            os += toString(o.atsc3Caps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::dvbsCaps: {
            os += ".dvbsCaps = ";
            os += toString(o.dvbsCaps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::dvbcCaps: {
            os += ".dvbcCaps = ";
            os += toString(o.dvbcCaps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::dvbtCaps: {
            os += ".dvbtCaps = ";
            os += toString(o.dvbtCaps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::isdbsCaps: {
            os += ".isdbsCaps = ";
            os += toString(o.isdbsCaps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::isdbs3Caps: {
            os += ".isdbs3Caps = ";
            os += toString(o.isdbs3Caps());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::isdbtCaps: {
            os += ".isdbtCaps = ";
            os += toString(o.isdbtCaps());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::analogCaps: {
            return (lhs.analogCaps() == rhs.analogCaps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::atscCaps: {
            return (lhs.atscCaps() == rhs.atscCaps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::atsc3Caps: {
            return (lhs.atsc3Caps() == rhs.atsc3Caps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::dvbsCaps: {
            return (lhs.dvbsCaps() == rhs.dvbsCaps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::dvbcCaps: {
            return (lhs.dvbcCaps() == rhs.dvbcCaps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::dvbtCaps: {
            return (lhs.dvbtCaps() == rhs.dvbtCaps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::isdbsCaps: {
            return (lhs.isdbsCaps() == rhs.isdbsCaps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::isdbs3Caps: {
            return (lhs.isdbs3Caps() == rhs.isdbs3Caps());
        }
        case ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities::hidl_discriminator::isdbtCaps: {
            return (lhs.isdbtCaps() == rhs.isdbtCaps());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo::FrontendCapabilities& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.type);
    os += ", .minFrequency = ";
    os += ::android::hardware::toString(o.minFrequency);
    os += ", .maxFrequency = ";
    os += ::android::hardware::toString(o.maxFrequency);
    os += ", .minSymbolRate = ";
    os += ::android::hardware::toString(o.minSymbolRate);
    os += ", .maxSymbolRate = ";
    os += ::android::hardware::toString(o.maxSymbolRate);
    os += ", .acquireRange = ";
    os += ::android::hardware::toString(o.acquireRange);
    os += ", .exclusiveGroupId = ";
    os += ::android::hardware::toString(o.exclusiveGroupId);
    os += ", .statusCaps = ";
    os += ::android::hardware::toString(o.statusCaps);
    os += ", .frontendCaps = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.frontendCaps);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.minFrequency != rhs.minFrequency) {
        return false;
    }
    if (lhs.maxFrequency != rhs.maxFrequency) {
        return false;
    }
    if (lhs.minSymbolRate != rhs.minSymbolRate) {
        return false;
    }
    if (lhs.maxSymbolRate != rhs.maxSymbolRate) {
        return false;
    }
    if (lhs.acquireRange != rhs.acquireRange) {
        return false;
    }
    if (lhs.exclusiveGroupId != rhs.exclusiveGroupId) {
        return false;
    }
    if (lhs.statusCaps != rhs.statusCaps) {
        return false;
    }
    if (lhs.frontendCaps != rhs.frontendCaps) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::FrontendInfo& lhs, const ::android::hardware::tv::tuner::V1_0::FrontendInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::LnbTone>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::LnbTone> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbTone::NONE) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::LnbTone::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbTone::NONE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbTone::CONTINUOUS) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::LnbTone::CONTINUOUS)) {
        os += (first ? "" : " | ");
        os += "CONTINUOUS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbTone::CONTINUOUS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbTone o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::LnbTone::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbTone::CONTINUOUS) {
        return "CONTINUOUS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbTone o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::LnbPosition>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::LnbPosition> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbPosition::UNDEFINED) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::LnbPosition::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbPosition::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_A) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_A)) {
        os += (first ? "" : " | ");
        os += "POSITION_A";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_A;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_B) == static_cast<int32_t>(::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_B)) {
        os += (first ? "" : " | ");
        os += "POSITION_B";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_B;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbPosition o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::LnbPosition::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_A) {
        return "POSITION_A";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_B) {
        return "POSITION_B";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbPosition o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::LnbEventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::LnbEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_OVERFLOW) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_OVERFLOW)) {
        os += (first ? "" : " | ");
        os += "DISEQC_RX_OVERFLOW";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_OVERFLOW;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_TIMEOUT) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "DISEQC_RX_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_TIMEOUT;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_PARITY_ERROR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_PARITY_ERROR)) {
        os += (first ? "" : " | ");
        os += "DISEQC_RX_PARITY_ERROR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_PARITY_ERROR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::LnbEventType::LNB_OVERLOAD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::LnbEventType::LNB_OVERLOAD)) {
        os += (first ? "" : " | ");
        os += "LNB_OVERLOAD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::LnbEventType::LNB_OVERLOAD;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::LnbEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_OVERFLOW) {
        return "DISEQC_RX_OVERFLOW";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_TIMEOUT) {
        return "DISEQC_RX_TIMEOUT";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_PARITY_ERROR) {
        return "DISEQC_RX_PARITY_ERROR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::LnbEventType::LNB_OVERLOAD) {
        return "LNB_OVERLOAD";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::LnbEventType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TS)) {
        os += (first ? "" : " | ");
        os += "TS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::MMTP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::MMTP)) {
        os += (first ? "" : " | ");
        os += "MMTP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::MMTP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::IP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::IP)) {
        os += (first ? "" : " | ");
        os += "IP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::IP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TLV) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TLV)) {
        os += (first ? "" : " | ");
        os += "TLV";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TLV;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::ALP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::ALP)) {
        os += (first ? "" : " | ");
        os += "ALP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::ALP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TS) {
        return "TS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::MMTP) {
        return "MMTP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::IP) {
        return "IP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TLV) {
        return "TLV";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::ALP) {
        return "ALP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxFilterMainType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxTsFilterType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxTsFilterType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::SECTION) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::SECTION)) {
        os += (first ? "" : " | ");
        os += "SECTION";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::SECTION;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PES) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PES)) {
        os += (first ? "" : " | ");
        os += "PES";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PES;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TS)) {
        os += (first ? "" : " | ");
        os += "TS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::AUDIO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::AUDIO)) {
        os += (first ? "" : " | ");
        os += "AUDIO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::AUDIO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::VIDEO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::VIDEO)) {
        os += (first ? "" : " | ");
        os += "VIDEO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::VIDEO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PCR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PCR)) {
        os += (first ? "" : " | ");
        os += "PCR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PCR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::RECORD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::RECORD)) {
        os += (first ? "" : " | ");
        os += "RECORD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::RECORD;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TEMI) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TEMI)) {
        os += (first ? "" : " | ");
        os += "TEMI";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TEMI;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::SECTION) {
        return "SECTION";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PES) {
        return "PES";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TS) {
        return "TS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::AUDIO) {
        return "AUDIO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::VIDEO) {
        return "VIDEO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PCR) {
        return "PCR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::RECORD) {
        return "RECORD";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TEMI) {
        return "TEMI";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxTsFilterType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::SECTION) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::SECTION)) {
        os += (first ? "" : " | ");
        os += "SECTION";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::SECTION;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::PES) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::PES)) {
        os += (first ? "" : " | ");
        os += "PES";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::PES;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::MMTP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::MMTP)) {
        os += (first ? "" : " | ");
        os += "MMTP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::MMTP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::AUDIO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::AUDIO)) {
        os += (first ? "" : " | ");
        os += "AUDIO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::AUDIO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::VIDEO) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::VIDEO)) {
        os += (first ? "" : " | ");
        os += "VIDEO";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::VIDEO;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::RECORD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::RECORD)) {
        os += (first ? "" : " | ");
        os += "RECORD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::RECORD;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::DOWNLOAD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::DOWNLOAD)) {
        os += (first ? "" : " | ");
        os += "DOWNLOAD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::DOWNLOAD;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::SECTION) {
        return "SECTION";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::PES) {
        return "PES";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::MMTP) {
        return "MMTP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::AUDIO) {
        return "AUDIO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::VIDEO) {
        return "VIDEO";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::RECORD) {
        return "RECORD";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::DOWNLOAD) {
        return "DOWNLOAD";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxIpFilterType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxIpFilterType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::SECTION) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::SECTION)) {
        os += (first ? "" : " | ");
        os += "SECTION";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::SECTION;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::NTP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::NTP)) {
        os += (first ? "" : " | ");
        os += "NTP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::NTP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP_PAYLOAD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP_PAYLOAD)) {
        os += (first ? "" : " | ");
        os += "IP_PAYLOAD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP_PAYLOAD;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP)) {
        os += (first ? "" : " | ");
        os += "IP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::PAYLOAD_THROUGH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::PAYLOAD_THROUGH)) {
        os += (first ? "" : " | ");
        os += "PAYLOAD_THROUGH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::PAYLOAD_THROUGH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::SECTION) {
        return "SECTION";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::NTP) {
        return "NTP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP_PAYLOAD) {
        return "IP_PAYLOAD";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP) {
        return "IP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::PAYLOAD_THROUGH) {
        return "PAYLOAD_THROUGH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxIpFilterType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::SECTION) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::SECTION)) {
        os += (first ? "" : " | ");
        os += "SECTION";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::SECTION;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::TLV) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::TLV)) {
        os += (first ? "" : " | ");
        os += "TLV";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::TLV;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::PAYLOAD_THROUGH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::PAYLOAD_THROUGH)) {
        os += (first ? "" : " | ");
        os += "PAYLOAD_THROUGH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::PAYLOAD_THROUGH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::SECTION) {
        return "SECTION";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::TLV) {
        return "TLV";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::PAYLOAD_THROUGH) {
        return "PAYLOAD_THROUGH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::UNDEFINED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::SECTION) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::SECTION)) {
        os += (first ? "" : " | ");
        os += "SECTION";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::SECTION;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PTP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PTP)) {
        os += (first ? "" : " | ");
        os += "PTP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PTP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PAYLOAD_THROUGH) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PAYLOAD_THROUGH)) {
        os += (first ? "" : " | ");
        os += "PAYLOAD_THROUGH";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PAYLOAD_THROUGH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::SECTION) {
        return "SECTION";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PTP) {
        return "PTP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PAYLOAD_THROUGH) {
        return "PAYLOAD_THROUGH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::tsFilterType: {
            os += ".tsFilterType = ";
            os += toString(o.tsFilterType());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::mmtpFilterType: {
            os += ".mmtpFilterType = ";
            os += toString(o.mmtpFilterType());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::ipFilterType: {
            os += ".ipFilterType = ";
            os += toString(o.ipFilterType());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::tlvFilterType: {
            os += ".tlvFilterType = ";
            os += toString(o.tlvFilterType());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::alpFilterType: {
            os += ".alpFilterType = ";
            os += toString(o.alpFilterType());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::tsFilterType: {
            return (lhs.tsFilterType() == rhs.tsFilterType());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::mmtpFilterType: {
            return (lhs.mmtpFilterType() == rhs.mmtpFilterType());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::ipFilterType: {
            return (lhs.ipFilterType() == rhs.ipFilterType());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::tlvFilterType: {
            return (lhs.tlvFilterType() == rhs.tlvFilterType());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType::hidl_discriminator::alpFilterType: {
            return (lhs.alpFilterType() == rhs.alpFilterType());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType::DemuxFilterSubType& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".mainType = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.mainType);
    os += ", .subType = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.subType);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& rhs) {
    if (lhs.mainType != rhs.mainType) {
        return false;
    }
    if (lhs.subType != rhs.subType) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxPid& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_discriminator::tPid: {
            os += ".tPid = ";
            os += toString(o.tPid());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_discriminator::mmtpPid: {
            os += ".mmtpPid = ";
            os += toString(o.mmtpPid());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxPid& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxPid& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxPid& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_discriminator::tPid: {
            return (lhs.tPid() == rhs.tPid());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxPid::hidl_discriminator::mmtpPid: {
            return (lhs.mmtpPid() == rhs.mmtpPid());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxPid& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxPid& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::Constant>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::Constant> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::Constant::INVALID_TS_PID) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::Constant::INVALID_TS_PID)) {
        os += (first ? "" : " | ");
        os += "INVALID_TS_PID";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Constant::INVALID_TS_PID;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Constant::INVALID_STREAM_ID) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::Constant::INVALID_STREAM_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_STREAM_ID";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Constant::INVALID_STREAM_ID;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Constant::INVALID_FILTER_ID) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::Constant::INVALID_FILTER_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_FILTER_ID";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Constant::INVALID_FILTER_ID;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::Constant::INVALID_AV_SYNC_ID) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::Constant::INVALID_AV_SYNC_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_AV_SYNC_ID";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::Constant::INVALID_AV_SYNC_ID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::Constant o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::Constant::INVALID_TS_PID) {
        return "INVALID_TS_PID";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Constant::INVALID_STREAM_ID) {
        return "INVALID_STREAM_ID";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Constant::INVALID_FILTER_ID) {
        return "INVALID_FILTER_ID";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::Constant::INVALID_AV_SYNC_ID) {
        return "INVALID_AV_SYNC_ID";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::Constant o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxFilterStatus>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxFilterStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::DATA_READY) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::DATA_READY)) {
        os += (first ? "" : " | ");
        os += "DATA_READY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::DATA_READY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::LOW_WATER) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::LOW_WATER)) {
        os += (first ? "" : " | ");
        os += "LOW_WATER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::LOW_WATER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::HIGH_WATER) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::HIGH_WATER)) {
        os += (first ? "" : " | ");
        os += "HIGH_WATER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::HIGH_WATER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::OVERFLOW) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::OVERFLOW)) {
        os += (first ? "" : " | ");
        os += "OVERFLOW";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::OVERFLOW;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::DATA_READY) {
        return "DATA_READY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::LOW_WATER) {
        return "LOW_WATER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::HIGH_WATER) {
        return "HIGH_WATER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::OVERFLOW) {
        return "OVERFLOW";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxFilterStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxTsIndex>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxTsIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::FIRST_PACKET) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::FIRST_PACKET)) {
        os += (first ? "" : " | ");
        os += "FIRST_PACKET";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::FIRST_PACKET;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PAYLOAD_UNIT_START_INDICATOR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PAYLOAD_UNIT_START_INDICATOR)) {
        os += (first ? "" : " | ");
        os += "PAYLOAD_UNIT_START_INDICATOR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PAYLOAD_UNIT_START_INDICATOR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_NOT_SCRAMBLED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_NOT_SCRAMBLED)) {
        os += (first ? "" : " | ");
        os += "CHANGE_TO_NOT_SCRAMBLED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_NOT_SCRAMBLED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_EVEN_SCRAMBLED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_EVEN_SCRAMBLED)) {
        os += (first ? "" : " | ");
        os += "CHANGE_TO_EVEN_SCRAMBLED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_EVEN_SCRAMBLED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_ODD_SCRAMBLED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_ODD_SCRAMBLED)) {
        os += (first ? "" : " | ");
        os += "CHANGE_TO_ODD_SCRAMBLED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_ODD_SCRAMBLED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::DISCONTINUITY_INDICATOR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::DISCONTINUITY_INDICATOR)) {
        os += (first ? "" : " | ");
        os += "DISCONTINUITY_INDICATOR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::DISCONTINUITY_INDICATOR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::RANDOM_ACCESS_INDICATOR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::RANDOM_ACCESS_INDICATOR)) {
        os += (first ? "" : " | ");
        os += "RANDOM_ACCESS_INDICATOR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::RANDOM_ACCESS_INDICATOR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIORITY_INDICATOR) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIORITY_INDICATOR)) {
        os += (first ? "" : " | ");
        os += "PRIORITY_INDICATOR";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIORITY_INDICATOR;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PCR_FLAG) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PCR_FLAG)) {
        os += (first ? "" : " | ");
        os += "PCR_FLAG";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PCR_FLAG;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::OPCR_FLAG) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::OPCR_FLAG)) {
        os += (first ? "" : " | ");
        os += "OPCR_FLAG";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::OPCR_FLAG;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::SPLICING_POINT_FLAG) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::SPLICING_POINT_FLAG)) {
        os += (first ? "" : " | ");
        os += "SPLICING_POINT_FLAG";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::SPLICING_POINT_FLAG;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIVATE_DATA) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIVATE_DATA)) {
        os += (first ? "" : " | ");
        os += "PRIVATE_DATA";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIVATE_DATA;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::ADAPTATION_EXTENSION_FLAG) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxTsIndex::ADAPTATION_EXTENSION_FLAG)) {
        os += (first ? "" : " | ");
        os += "ADAPTATION_EXTENSION_FLAG";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::ADAPTATION_EXTENSION_FLAG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxTsIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::FIRST_PACKET) {
        return "FIRST_PACKET";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PAYLOAD_UNIT_START_INDICATOR) {
        return "PAYLOAD_UNIT_START_INDICATOR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_NOT_SCRAMBLED) {
        return "CHANGE_TO_NOT_SCRAMBLED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_EVEN_SCRAMBLED) {
        return "CHANGE_TO_EVEN_SCRAMBLED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_ODD_SCRAMBLED) {
        return "CHANGE_TO_ODD_SCRAMBLED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::DISCONTINUITY_INDICATOR) {
        return "DISCONTINUITY_INDICATOR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::RANDOM_ACCESS_INDICATOR) {
        return "RANDOM_ACCESS_INDICATOR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIORITY_INDICATOR) {
        return "PRIORITY_INDICATOR";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PCR_FLAG) {
        return "PCR_FLAG";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::OPCR_FLAG) {
        return "OPCR_FLAG";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::SPLICING_POINT_FLAG) {
        return "SPLICING_POINT_FLAG";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIVATE_DATA) {
        return "PRIVATE_DATA";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::ADAPTATION_EXTENSION_FLAG) {
        return "ADAPTATION_EXTENSION_FLAG";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxTsIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxScIndex>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScIndex::I_FRAME) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScIndex::I_FRAME)) {
        os += (first ? "" : " | ");
        os += "I_FRAME";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScIndex::I_FRAME;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScIndex::P_FRAME) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScIndex::P_FRAME)) {
        os += (first ? "" : " | ");
        os += "P_FRAME";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScIndex::P_FRAME;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScIndex::B_FRAME) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScIndex::B_FRAME)) {
        os += (first ? "" : " | ");
        os += "B_FRAME";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScIndex::B_FRAME;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScIndex::SEQUENCE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScIndex::SEQUENCE)) {
        os += (first ? "" : " | ");
        os += "SEQUENCE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScIndex::SEQUENCE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxScIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScIndex::I_FRAME) {
        return "I_FRAME";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScIndex::P_FRAME) {
        return "P_FRAME";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScIndex::B_FRAME) {
        return "B_FRAME";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScIndex::SEQUENCE) {
        return "SEQUENCE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxScIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SPS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SPS)) {
        os += (first ? "" : " | ");
        os += "SPS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SPS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::AUD) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::AUD)) {
        os += (first ? "" : " | ");
        os += "AUD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::AUD;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_CE_BLA_W_LP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_CE_BLA_W_LP)) {
        os += (first ? "" : " | ");
        os += "SLICE_CE_BLA_W_LP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_CE_BLA_W_LP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_W_RADL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_W_RADL)) {
        os += (first ? "" : " | ");
        os += "SLICE_BLA_W_RADL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_W_RADL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_N_LP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_N_LP)) {
        os += (first ? "" : " | ");
        os += "SLICE_BLA_N_LP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_N_LP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_W_RADL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_W_RADL)) {
        os += (first ? "" : " | ");
        os += "SLICE_IDR_W_RADL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_W_RADL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_N_LP) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_N_LP)) {
        os += (first ? "" : " | ");
        os += "SLICE_IDR_N_LP";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_N_LP;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_TRAIL_CRA) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_TRAIL_CRA)) {
        os += (first ? "" : " | ");
        os += "SLICE_TRAIL_CRA";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_TRAIL_CRA;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SPS) {
        return "SPS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::AUD) {
        return "AUD";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_CE_BLA_W_LP) {
        return "SLICE_CE_BLA_W_LP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_W_RADL) {
        return "SLICE_BLA_W_RADL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_N_LP) {
        return "SLICE_BLA_N_LP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_W_RADL) {
        return "SLICE_IDR_W_RADL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_N_LP) {
        return "SLICE_IDR_N_LP";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_TRAIL_CRA) {
        return "SLICE_TRAIL_CRA";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::NONE) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::NONE;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC)) {
        os += (first ? "" : " | ");
        os += "SC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC_HEVC) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC_HEVC)) {
        os += (first ? "" : " | ");
        os += "SC_HEVC";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC_HEVC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC) {
        return "SC";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC_HEVC) {
        return "SC_HEVC";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_discriminator::sc: {
            os += ".sc = ";
            os += toString(o.sc());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_discriminator::scHevc: {
            os += ".scHevc = ";
            os += toString(o.scHevc());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_discriminator::sc: {
            return (lhs.sc() == rhs.sc());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask::hidl_discriminator::scHevc: {
            return (lhs.scHevc() == rhs.scHevc());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings::ScIndexMask& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tsIndexMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::DemuxTsIndex>(o.tsIndexMask);
    os += ", .scIndexType = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.scIndexType);
    os += ", .scIndexMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.scIndexMask);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& rhs) {
    if (lhs.tsIndexMask != rhs.tsIndexMask) {
        return false;
    }
    if (lhs.scIndexType != rhs.scIndexType) {
        return false;
    }
    if (lhs.scIndexMask != rhs.scIndexMask) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterRecordSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".filter = ";
    os += ::android::hardware::toString(o.filter);
    os += ", .mask = ";
    os += ::android::hardware::toString(o.mask);
    os += ", .mode = ";
    os += ::android::hardware::toString(o.mode);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& rhs) {
    if (lhs.filter != rhs.filter) {
        return false;
    }
    if (lhs.mask != rhs.mask) {
        return false;
    }
    if (lhs.mode != rhs.mode) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionBits& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tableId = ";
    os += ::android::hardware::toString(o.tableId);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& rhs) {
    if (lhs.tableId != rhs.tableId) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::TableInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_discriminator::sectionBits: {
            os += ".sectionBits = ";
            os += toString(o.sectionBits());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_discriminator::tableInfo: {
            os += ".tableInfo = ";
            os += toString(o.tableInfo());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_discriminator::sectionBits: {
            return (lhs.sectionBits() == rhs.sectionBits());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition::hidl_discriminator::tableInfo: {
            return (lhs.tableInfo() == rhs.tableInfo());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings::Condition& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".condition = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.condition);
    os += ", .isCheckCrc = ";
    os += ::android::hardware::toString(o.isCheckCrc);
    os += ", .isRepeat = ";
    os += ::android::hardware::toString(o.isRepeat);
    os += ", .isRaw = ";
    os += ::android::hardware::toString(o.isRaw);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& rhs) {
    if (lhs.condition != rhs.condition) {
        return false;
    }
    if (lhs.isCheckCrc != rhs.isCheckCrc) {
        return false;
    }
    if (lhs.isRepeat != rhs.isRepeat) {
        return false;
    }
    if (lhs.isRaw != rhs.isRaw) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .isRaw = ";
    os += ::android::hardware::toString(o.isRaw);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& rhs) {
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.isRaw != rhs.isRaw) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesDataSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".isPassthrough = ";
    os += ::android::hardware::toString(o.isPassthrough);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& rhs) {
    if (lhs.isPassthrough != rhs.isPassthrough) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterAvSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".downloadId = ";
    os += ::android::hardware::toString(o.downloadId);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& rhs) {
    if (lhs.downloadId != rhs.downloadId) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_discriminator::v4: {
            os += ".v4 = ";
            os += toString(o.v4());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_discriminator::v6: {
            os += ".v6 = ";
            os += toString(o.v6());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_discriminator::v4: {
            return (lhs.v4() == rhs.v4());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress::hidl_discriminator::v6: {
            return (lhs.v6() == rhs.v6());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::SrcIpAddress& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_discriminator::v4: {
            os += ".v4 = ";
            os += toString(o.v4());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_discriminator::v6: {
            os += ".v6 = ";
            os += toString(o.v6());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_discriminator::v4: {
            return (lhs.v4() == rhs.v4());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress::hidl_discriminator::v6: {
            return (lhs.v6() == rhs.v6());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress::DstIpAddress& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".srcIpAddress = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.srcIpAddress);
    os += ", .dstIpAddress = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.dstIpAddress);
    os += ", .srcPort = ";
    os += ::android::hardware::toString(o.srcPort);
    os += ", .dstPort = ";
    os += ::android::hardware::toString(o.dstPort);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& rhs) {
    if (lhs.srcIpAddress != rhs.srcIpAddress) {
        return false;
    }
    if (lhs.dstIpAddress != rhs.dstIpAddress) {
        return false;
    }
    if (lhs.srcPort != rhs.srcPort) {
        return false;
    }
    if (lhs.dstPort != rhs.dstPort) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpAddress& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::section: {
            os += ".section = ";
            os += toString(o.section());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::av: {
            os += ".av = ";
            os += toString(o.av());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::pesData: {
            os += ".pesData = ";
            os += toString(o.pesData());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::record: {
            os += ".record = ";
            os += toString(o.record());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::section: {
            return (lhs.section() == rhs.section());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::av: {
            return (lhs.av() == rhs.av());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::pesData: {
            return (lhs.pesData() == rhs.pesData());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings::hidl_discriminator::record: {
            return (lhs.record() == rhs.record());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings::FilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tpid = ";
    os += ::android::hardware::toString(o.tpid);
    os += ", .filterSettings = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.filterSettings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& rhs) {
    if (lhs.tpid != rhs.tpid) {
        return false;
    }
    if (lhs.filterSettings != rhs.filterSettings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTsFilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::section: {
            os += ".section = ";
            os += toString(o.section());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::av: {
            os += ".av = ";
            os += toString(o.av());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::pesData: {
            os += ".pesData = ";
            os += toString(o.pesData());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::record: {
            os += ".record = ";
            os += toString(o.record());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::download: {
            os += ".download = ";
            os += toString(o.download());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::section: {
            return (lhs.section() == rhs.section());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::av: {
            return (lhs.av() == rhs.av());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::pesData: {
            return (lhs.pesData() == rhs.pesData());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::record: {
            return (lhs.record() == rhs.record());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings::hidl_discriminator::download: {
            return (lhs.download() == rhs.download());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings::FilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".mmtpPid = ";
    os += ::android::hardware::toString(o.mmtpPid);
    os += ", .filterSettings = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.filterSettings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& rhs) {
    if (lhs.mmtpPid != rhs.mmtpPid) {
        return false;
    }
    if (lhs.filterSettings != rhs.filterSettings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator::section: {
            os += ".section = ";
            os += toString(o.section());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator::bPassthrough: {
            os += ".bPassthrough = ";
            os += toString(o.bPassthrough());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator::section: {
            return (lhs.section() == rhs.section());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings::hidl_discriminator::bPassthrough: {
            return (lhs.bPassthrough() == rhs.bPassthrough());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings::FilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ipAddr = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.ipAddr);
    os += ", .filterSettings = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.filterSettings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& rhs) {
    if (lhs.ipAddr != rhs.ipAddr) {
        return false;
    }
    if (lhs.filterSettings != rhs.filterSettings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxIpFilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator::section: {
            os += ".section = ";
            os += toString(o.section());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator::bPassthrough: {
            os += ".bPassthrough = ";
            os += toString(o.bPassthrough());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator::section: {
            return (lhs.section() == rhs.section());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings::hidl_discriminator::bPassthrough: {
            return (lhs.bPassthrough() == rhs.bPassthrough());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings::FilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".packetType = ";
    os += ::android::hardware::toString(o.packetType);
    os += ", .isCompressedIpPacket = ";
    os += ::android::hardware::toString(o.isCompressedIpPacket);
    os += ", .filterSettings = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.filterSettings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& rhs) {
    if (lhs.packetType != rhs.packetType) {
        return false;
    }
    if (lhs.isCompressedIpPacket != rhs.isCompressedIpPacket) {
        return false;
    }
    if (lhs.filterSettings != rhs.filterSettings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterSettings& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::UNDEFINED) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "UNDEFINED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::UNDEFINED;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITHOUT_ADDITIONAL_HEADER) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITHOUT_ADDITIONAL_HEADER)) {
        os += (first ? "" : " | ");
        os += "WITHOUT_ADDITIONAL_HEADER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITHOUT_ADDITIONAL_HEADER;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITH_ADDITIONAL_HEADER) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITH_ADDITIONAL_HEADER)) {
        os += (first ? "" : " | ");
        os += "WITH_ADDITIONAL_HEADER";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITH_ADDITIONAL_HEADER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::UNDEFINED) {
        return "UNDEFINED";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITHOUT_ADDITIONAL_HEADER) {
        return "WITHOUT_ADDITIONAL_HEADER";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITH_ADDITIONAL_HEADER) {
        return "WITH_ADDITIONAL_HEADER";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_discriminator::section: {
            os += ".section = ";
            os += toString(o.section());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings::hidl_discriminator::section: {
            return (lhs.section() == rhs.section());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings::FilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".packetType = ";
    os += ::android::hardware::toString(o.packetType);
    os += ", .lengthType = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.lengthType);
    os += ", .filterSettings = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.filterSettings);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& rhs) {
    if (lhs.packetType != rhs.packetType) {
        return false;
    }
    if (lhs.lengthType != rhs.lengthType) {
        return false;
    }
    if (lhs.filterSettings != rhs.filterSettings) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::ts: {
            os += ".ts = ";
            os += toString(o.ts());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::mmtp: {
            os += ".mmtp = ";
            os += toString(o.mmtp());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::ip: {
            os += ".ip = ";
            os += toString(o.ip());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::tlv: {
            os += ".tlv = ";
            os += toString(o.tlv());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::alp: {
            os += ".alp = ";
            os += toString(o.alp());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::ts: {
            return (lhs.ts() == rhs.ts());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::mmtp: {
            return (lhs.mmtp() == rhs.mmtp());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::ip: {
            return (lhs.ip() == rhs.ip());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::tlv: {
            return (lhs.tlv() == rhs.tlv());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings::hidl_discriminator::alp: {
            return (lhs.alp() == rhs.alp());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSettings& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_READY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_READY)) {
        os += (first ? "" : " | ");
        os += "DATA_READY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_READY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_CONSUMED) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_CONSUMED)) {
        os += (first ? "" : " | ");
        os += "DATA_CONSUMED";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_CONSUMED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_READY) {
        return "DATA_READY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_CONSUMED) {
        return "DATA_CONSUMED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tableId = ";
    os += ::android::hardware::toString(o.tableId);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .sectionNum = ";
    os += ::android::hardware::toString(o.sectionNum);
    os += ", .dataLength = ";
    os += ::android::hardware::toString(o.dataLength);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& rhs) {
    if (lhs.tableId != rhs.tableId) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.sectionNum != rhs.sectionNum) {
        return false;
    }
    if (lhs.dataLength != rhs.dataLength) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterSectionEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".adFade = ";
    os += ::android::hardware::toString(o.adFade);
    os += ", .adPan = ";
    os += ::android::hardware::toString(o.adPan);
    os += ", .versionTextTag = ";
    os += ::android::hardware::toString(o.versionTextTag);
    os += ", .adGainCenter = ";
    os += ::android::hardware::toString(o.adGainCenter);
    os += ", .adGainFront = ";
    os += ::android::hardware::toString(o.adGainFront);
    os += ", .adGainSurround = ";
    os += ::android::hardware::toString(o.adGainSurround);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& rhs) {
    if (lhs.adFade != rhs.adFade) {
        return false;
    }
    if (lhs.adPan != rhs.adPan) {
        return false;
    }
    if (lhs.versionTextTag != rhs.versionTextTag) {
        return false;
    }
    if (lhs.adGainCenter != rhs.adGainCenter) {
        return false;
    }
    if (lhs.adGainFront != rhs.adGainFront) {
        return false;
    }
    if (lhs.adGainSurround != rhs.adGainSurround) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::AudioExtraMetaData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_discriminator::audio: {
            os += ".audio = ";
            os += toString(o.audio());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData::hidl_discriminator::audio: {
            return (lhs.audio() == rhs.audio());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent::ExtraMetaData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .isPtsPresent = ";
    os += ::android::hardware::toString(o.isPtsPresent);
    os += ", .pts = ";
    os += ::android::hardware::toString(o.pts);
    os += ", .dataLength = ";
    os += ::android::hardware::toString(o.dataLength);
    os += ", .offset = ";
    os += ::android::hardware::toString(o.offset);
    os += ", .avMemory = ";
    os += ::android::hardware::toString(o.avMemory);
    os += ", .isSecureMemory = ";
    os += ::android::hardware::toString(o.isSecureMemory);
    os += ", .avDataId = ";
    os += ::android::hardware::toString(o.avDataId);
    os += ", .mpuSequenceNumber = ";
    os += ::android::hardware::toString(o.mpuSequenceNumber);
    os += ", .isPesPrivateData = ";
    os += ::android::hardware::toString(o.isPesPrivateData);
    os += ", .extraMetaData = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.extraMetaData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMediaEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DemuxFilterMediaEvent

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".streamId = ";
    os += ::android::hardware::toString(o.streamId);
    os += ", .dataLength = ";
    os += ::android::hardware::toString(o.dataLength);
    os += ", .mpuSequenceNumber = ";
    os += ::android::hardware::toString(o.mpuSequenceNumber);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& rhs) {
    if (lhs.streamId != rhs.streamId) {
        return false;
    }
    if (lhs.dataLength != rhs.dataLength) {
        return false;
    }
    if (lhs.mpuSequenceNumber != rhs.mpuSequenceNumber) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterPesEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_discriminator::sc: {
            os += ".sc = ";
            os += toString(o.sc());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_discriminator::scHevc: {
            os += ".scHevc = ";
            os += toString(o.scHevc());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_discriminator::sc: {
            return (lhs.sc() == rhs.sc());
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask::hidl_discriminator::scHevc: {
            return (lhs.scHevc() == rhs.scHevc());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent::ScIndexMask& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".pid = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.pid);
    os += ", .tsIndexMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::DemuxTsIndex>(o.tsIndexMask);
    os += ", .scIndexMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.scIndexMask);
    os += ", .byteNumber = ";
    os += ::android::hardware::toString(o.byteNumber);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& rhs) {
    if (lhs.pid != rhs.pid) {
        return false;
    }
    if (lhs.tsIndexMask != rhs.tsIndexMask) {
        return false;
    }
    if (lhs.scIndexMask != rhs.scIndexMask) {
        return false;
    }
    if (lhs.byteNumber != rhs.byteNumber) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTsRecordEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".pts = ";
    os += ::android::hardware::toString(o.pts);
    os += ", .descrTag = ";
    os += ::android::hardware::toString(o.descrTag);
    os += ", .descrData = ";
    os += ::android::hardware::toString(o.descrData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& rhs) {
    if (lhs.pts != rhs.pts) {
        return false;
    }
    if (lhs.descrTag != rhs.descrTag) {
        return false;
    }
    if (lhs.descrData != rhs.descrData) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterTemiEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".scHevcIndexMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex>(o.scHevcIndexMask);
    os += ", .byteNumber = ";
    os += ::android::hardware::toString(o.byteNumber);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& rhs) {
    if (lhs.scHevcIndexMask != rhs.scHevcIndexMask) {
        return false;
    }
    if (lhs.byteNumber != rhs.byteNumber) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterMmtpRecordEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".itemId = ";
    os += ::android::hardware::toString(o.itemId);
    os += ", .mpuSequenceNumber = ";
    os += ::android::hardware::toString(o.mpuSequenceNumber);
    os += ", .itemFragmentIndex = ";
    os += ::android::hardware::toString(o.itemFragmentIndex);
    os += ", .lastItemFragmentIndex = ";
    os += ::android::hardware::toString(o.lastItemFragmentIndex);
    os += ", .dataLength = ";
    os += ::android::hardware::toString(o.dataLength);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& rhs) {
    if (lhs.itemId != rhs.itemId) {
        return false;
    }
    if (lhs.mpuSequenceNumber != rhs.mpuSequenceNumber) {
        return false;
    }
    if (lhs.itemFragmentIndex != rhs.itemFragmentIndex) {
        return false;
    }
    if (lhs.lastItemFragmentIndex != rhs.lastItemFragmentIndex) {
        return false;
    }
    if (lhs.dataLength != rhs.dataLength) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterDownloadEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".dataLength = ";
    os += ::android::hardware::toString(o.dataLength);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& rhs) {
    if (lhs.dataLength != rhs.dataLength) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxFilterIpPayloadEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::section: {
            os += ".section = ";
            os += toString(o.section());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::media: {
            os += ".media = ";
            os += toString(o.media());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::pes: {
            os += ".pes = ";
            os += toString(o.pes());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::tsRecord: {
            os += ".tsRecord = ";
            os += toString(o.tsRecord());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::mmtpRecord: {
            os += ".mmtpRecord = ";
            os += toString(o.mmtpRecord());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::download: {
            os += ".download = ";
            os += toString(o.download());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::ipPayload: {
            os += ".ipPayload = ";
            os += toString(o.ipPayload());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event::hidl_discriminator::temi: {
            os += ".temi = ";
            os += toString(o.temi());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent::Event& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Event

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".events = ";
    os += ::android::hardware::toString(o.events);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxFilterEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DemuxFilterEvent

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DataFormat>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DataFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DataFormat::TS) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DataFormat::TS)) {
        os += (first ? "" : " | ");
        os += "TS";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DataFormat::TS;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DataFormat::PES) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DataFormat::PES)) {
        os += (first ? "" : " | ");
        os += "PES";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DataFormat::PES;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DataFormat::ES) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DataFormat::ES)) {
        os += (first ? "" : " | ");
        os += "ES";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DataFormat::ES;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DataFormat::SHV_TLV) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::DataFormat::SHV_TLV)) {
        os += (first ? "" : " | ");
        os += "SHV_TLV";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DataFormat::SHV_TLV;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DataFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DataFormat::TS) {
        return "TS";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DataFormat::PES) {
        return "PES";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DataFormat::ES) {
        return "ES";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DataFormat::SHV_TLV) {
        return "SHV_TLV";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DataFormat o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::RecordSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".statusMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::DemuxFilterStatus>(o.statusMask);
    os += ", .lowThreshold = ";
    os += ::android::hardware::toString(o.lowThreshold);
    os += ", .highThreshold = ";
    os += ::android::hardware::toString(o.highThreshold);
    os += ", .dataFormat = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.dataFormat);
    os += ", .packetSize = ";
    os += ::android::hardware::toString(o.packetSize);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::RecordSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::RecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::RecordSettings& rhs) {
    if (lhs.statusMask != rhs.statusMask) {
        return false;
    }
    if (lhs.lowThreshold != rhs.lowThreshold) {
        return false;
    }
    if (lhs.highThreshold != rhs.highThreshold) {
        return false;
    }
    if (lhs.dataFormat != rhs.dataFormat) {
        return false;
    }
    if (lhs.packetSize != rhs.packetSize) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::RecordSettings& lhs, const ::android::hardware::tv::tuner::V1_0::RecordSettings& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::PlaybackStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::PlaybackStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_EMPTY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_EMPTY)) {
        os += (first ? "" : " | ");
        os += "SPACE_EMPTY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_EMPTY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_EMPTY) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_EMPTY)) {
        os += (first ? "" : " | ");
        os += "SPACE_ALMOST_EMPTY";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_EMPTY;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_FULL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_FULL)) {
        os += (first ? "" : " | ");
        os += "SPACE_ALMOST_FULL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_FULL;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_FULL) == static_cast<uint32_t>(::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_FULL)) {
        os += (first ? "" : " | ");
        os += "SPACE_FULL";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::PlaybackStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_EMPTY) {
        return "SPACE_EMPTY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_EMPTY) {
        return "SPACE_ALMOST_EMPTY";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_FULL) {
        return "SPACE_ALMOST_FULL";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_FULL) {
        return "SPACE_FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::PlaybackStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".statusMask = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::PlaybackStatus>(o.statusMask);
    os += ", .lowThreshold = ";
    os += ::android::hardware::toString(o.lowThreshold);
    os += ", .highThreshold = ";
    os += ::android::hardware::toString(o.highThreshold);
    os += ", .dataFormat = ";
    os += ::android::hardware::tv::tuner::V1_0::toString(o.dataFormat);
    os += ", .packetSize = ";
    os += ::android::hardware::toString(o.packetSize);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& rhs) {
    if (lhs.statusMask != rhs.statusMask) {
        return false;
    }
    if (lhs.lowThreshold != rhs.lowThreshold) {
        return false;
    }
    if (lhs.highThreshold != rhs.highThreshold) {
        return false;
    }
    if (lhs.dataFormat != rhs.dataFormat) {
        return false;
    }
    if (lhs.packetSize != rhs.packetSize) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& lhs, const ::android::hardware::tv::tuner::V1_0::PlaybackSettings& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::tv::tuner::V1_0::DvrType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::tv::tuner::V1_0::DvrType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::tv::tuner::V1_0::DvrType::RECORD) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DvrType::RECORD)) {
        os += (first ? "" : " | ");
        os += "RECORD";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DvrType::RECORD;
    }
    if ((o & ::android::hardware::tv::tuner::V1_0::DvrType::PLAYBACK) == static_cast<uint8_t>(::android::hardware::tv::tuner::V1_0::DvrType::PLAYBACK)) {
        os += (first ? "" : " | ");
        os += "PLAYBACK";
        first = false;
        flipped |= ::android::hardware::tv::tuner::V1_0::DvrType::PLAYBACK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::tv::tuner::V1_0::DvrType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::tv::tuner::V1_0::DvrType::RECORD) {
        return "RECORD";
    }
    if (o == ::android::hardware::tv::tuner::V1_0::DvrType::PLAYBACK) {
        return "PLAYBACK";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::tv::tuner::V1_0::DvrType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DvrSettings& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_discriminator::record: {
            os += ".record = ";
            os += toString(o.record());
            break;
        }
        case ::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_discriminator::playback: {
            os += ".playback = ";
            os += toString(o.playback());
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

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DvrSettings& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DvrSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DvrSettings& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_discriminator::record: {
            return (lhs.record() == rhs.record());
        }
        case ::android::hardware::tv::tuner::V1_0::DvrSettings::hidl_discriminator::playback: {
            return (lhs.playback() == rhs.playback());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DvrSettings& lhs, const ::android::hardware::tv::tuner::V1_0::DvrSettings& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".numDemux = ";
    os += ::android::hardware::toString(o.numDemux);
    os += ", .numRecord = ";
    os += ::android::hardware::toString(o.numRecord);
    os += ", .numPlayback = ";
    os += ::android::hardware::toString(o.numPlayback);
    os += ", .numTsFilter = ";
    os += ::android::hardware::toString(o.numTsFilter);
    os += ", .numSectionFilter = ";
    os += ::android::hardware::toString(o.numSectionFilter);
    os += ", .numAudioFilter = ";
    os += ::android::hardware::toString(o.numAudioFilter);
    os += ", .numVideoFilter = ";
    os += ::android::hardware::toString(o.numVideoFilter);
    os += ", .numPesFilter = ";
    os += ::android::hardware::toString(o.numPesFilter);
    os += ", .numPcrFilter = ";
    os += ::android::hardware::toString(o.numPcrFilter);
    os += ", .numBytesInSectionFilter = ";
    os += ::android::hardware::toString(o.numBytesInSectionFilter);
    os += ", .filterCaps = ";
    os += ::android::hardware::tv::tuner::V1_0::toString<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType>(o.filterCaps);
    os += ", .linkCaps = ";
    os += ::android::hardware::toString(o.linkCaps);
    os += ", .bTimeFilter = ";
    os += ::android::hardware::toString(o.bTimeFilter);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& rhs) {
    if (lhs.numDemux != rhs.numDemux) {
        return false;
    }
    if (lhs.numRecord != rhs.numRecord) {
        return false;
    }
    if (lhs.numPlayback != rhs.numPlayback) {
        return false;
    }
    if (lhs.numTsFilter != rhs.numTsFilter) {
        return false;
    }
    if (lhs.numSectionFilter != rhs.numSectionFilter) {
        return false;
    }
    if (lhs.numAudioFilter != rhs.numAudioFilter) {
        return false;
    }
    if (lhs.numVideoFilter != rhs.numVideoFilter) {
        return false;
    }
    if (lhs.numPesFilter != rhs.numPesFilter) {
        return false;
    }
    if (lhs.numPcrFilter != rhs.numPcrFilter) {
        return false;
    }
    if (lhs.numBytesInSectionFilter != rhs.numBytesInSectionFilter) {
        return false;
    }
    if (lhs.filterCaps != rhs.filterCaps) {
        return false;
    }
    if (lhs.linkCaps != rhs.linkCaps) {
        return false;
    }
    if (lhs.bTimeFilter != rhs.bTimeFilter) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& lhs, const ::android::hardware::tv::tuner::V1_0::DemuxCapabilities& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace tuner
}  // namespace tv
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::Result, 7> hidl_enum_values<::android::hardware::tv::tuner::V1_0::Result> = {
    ::android::hardware::tv::tuner::V1_0::Result::SUCCESS,
    ::android::hardware::tv::tuner::V1_0::Result::UNAVAILABLE,
    ::android::hardware::tv::tuner::V1_0::Result::NOT_INITIALIZED,
    ::android::hardware::tv::tuner::V1_0::Result::INVALID_STATE,
    ::android::hardware::tv::tuner::V1_0::Result::INVALID_ARGUMENT,
    ::android::hardware::tv::tuner::V1_0::Result::OUT_OF_MEMORY,
    ::android::hardware::tv::tuner::V1_0::Result::UNKNOWN_ERROR,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendType, 10> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendType::ANALOG,
    ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC,
    ::android::hardware::tv::tuner::V1_0::FrontendType::ATSC3,
    ::android::hardware::tv::tuner::V1_0::FrontendType::DVBC,
    ::android::hardware::tv::tuner::V1_0::FrontendType::DVBS,
    ::android::hardware::tv::tuner::V1_0::FrontendType::DVBT,
    ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS,
    ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBS3,
    ::android::hardware::tv::tuner::V1_0::FrontendType::ISDBT,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendInnerFec, 37> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendInnerFec> = {
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_2,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_3,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_4,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_1_5,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_3,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_5,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_2_9,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_4,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_3_5,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_5,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_4_15,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_6,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_5_9,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_6_7,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_8,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_9,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_7_15,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_9,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_8_15,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_10,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_9_20,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_15,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_20,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_11_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_18,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_13_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_14_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_23_36,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_25_36,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_26_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_28_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_29_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_31_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_32_45,
    ::android::hardware::tv::tuner::V1_0::FrontendInnerFec::FEC_77_90,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtscModulation, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtscModulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_8VSB,
    ::android::hardware::tv::tuner::V1_0::FrontendAtscModulation::MOD_16VSB,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation, 8> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_QPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_16QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_64QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_256QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_1024QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Modulation::MOD_4096QAM,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth, 5> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_6MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_7MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Bandwidth::BANDWIDTH_8MHZ,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::CTI,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3TimeInterleaveMode::HTI,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate, 14> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_2_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_3_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_4_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_5_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_6_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_7_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_8_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_9_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_10_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_11_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_12_15,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3CodeRate::CODERATE_13_15,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec, 8> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_16K,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::BCH_LDPC_64K,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_16K,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::CRC_LDPC_64K,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_16K,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3Fec::LDPC_64K,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::ATSC3_LINKLAYER_PACKET,
    ::android::hardware::tv::tuner::V1_0::FrontendAtsc3DemodOutputFormat::BASEBAND_PACKET,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation, 15> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_QPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8PSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16PSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32PSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_ACM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_8APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_16APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_32APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_64APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_128APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_256APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsModulation::MOD_RESERVED,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff, 7> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_35,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_25,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_20,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_15,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_10,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsRolloff::ROLLOFF_0_5,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::ON,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::OFF,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsPilot::AUTO,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsStandard::S2X,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbsVcmMode::MANUAL,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation, 7> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_16QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_32QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_64QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_128QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcModulation::MOD_256QAM,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_NONE,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcOuterFec::OUTER_FEC_RS,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::A,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::B,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcAnnex::C,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::NORMAL,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbcSpectralInversion::INVERTED,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth, 8> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_8MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_7MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_6MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_5MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_1_7MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtBandwidth::BANDWIDTH_10MHZ,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation, 6> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_QPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_16QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_64QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtConstellation::CONSTELLATION_256QAM,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy, 10> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_NATIVE,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_NATIVE,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_NATIVE,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_NATIVE,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_NON_INDEPTH,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_1_INDEPTH,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_2_INDEPTH,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtHierarchy::HIERARCHY_4_INDEPTH,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate, 11> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_1_2,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_2_3,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_4,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_5_6,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_7_8,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_3_5,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_4_5,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_6_7,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtCoderate::CODERATE_8_9,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval, 9> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_32,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_16,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_8,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_4,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_1_128,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_128,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtGuardInterval::INTERVAL_19_256,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode, 8> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_2K,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_8K,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_4K,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_1K,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_16K,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtTransmissionMode::MODE_32K,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtPlpMode::MANUAL,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard> = {
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T,
    ::android::hardware::tv::tuner::V1_0::FrontendDvbtStandard::T2,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff, 2> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsRolloff::ROLLOFF_0_35,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation, 5> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_BPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_QPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsModulation::MOD_TC8PSK,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate, 7> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_1_2,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_2_3,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_3_4,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_5_6,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsCoderate::CODERATE_7_8,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType, 2> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::STREAM_ID,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbsStreamIdType::RELATIVE_STREAM_NUMBER,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff, 2> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Rolloff::ROLLOFF_0_03,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation, 7> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_BPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_QPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_8PSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_16APSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Modulation::MOD_32APSK,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate, 13> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_3,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_5,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_1_2,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_5,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_2_3,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_3_4,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_9,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_4_5,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_5_6,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_7_8,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbs3Coderate::CODERATE_9_10,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode, 5> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_1,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_2,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtMode::MODE_3,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth, 5> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_8MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_7MHZ,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtBandwidth::BANDWIDTH_6MHZ,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation, 6> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation> = {
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_DQPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_QPSK,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_16QAM,
    ::android::hardware::tv::tuner::V1_0::FrontendIsdbtModulation::MOD_64QAM,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAnalogType, 9> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAnalogType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_M,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_N,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::PAL_60,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::NTSC_443,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogType::SECAM,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard, 19> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard> = {
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_A2,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::BG_NICAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK1_A2,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK2_A2,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK3_A2,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::DK_NICAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_BTSC,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_A2,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::M_EIAJ,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::I_NICAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_NICAM,
    ::android::hardware::tv::tuner::V1_0::FrontendAnalogSifStandard::L_PRIME,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendScanType, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendScanType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_AUTO,
    ::android::hardware::tv::tuner::V1_0::FrontendScanType::SCAN_BLIND,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendScanMessageType, 12> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendScanMessageType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::LOCKED,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::END,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PROGRESS_PERCENT,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::FREQUENCY,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::SYMBOL_RATE,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::HIERARCHY,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ANALOG_TYPE,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::PLP_IDS,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::GROUP_IDS,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::INPUT_STREAM_IDS,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::STANDARD,
    ::android::hardware::tv::tuner::V1_0::FrontendScanMessageType::ATSC3_PLP_INFO,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendEventType, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendEventType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOCKED,
    ::android::hardware::tv::tuner::V1_0::FrontendEventType::NO_SIGNAL,
    ::android::hardware::tv::tuner::V1_0::FrontendEventType::LOST_LOCK,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::FrontendStatusType, 22> hidl_enum_values<::android::hardware::tv::tuner::V1_0::FrontendStatusType> = {
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::DEMOD_LOCK,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SNR,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::BER,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PER,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PRE_BER,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_QUALITY,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SIGNAL_STRENGTH,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SYMBOL_RATE,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FEC,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MODULATION,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::SPECTRAL,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNB_VOLTAGE,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::PLP_ID,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::EWBS,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::AGC,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LNA,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::LAYER_ERROR,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::MER,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::FREQ_OFFSET,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::HIERARCHY,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::RF_LOCK,
    ::android::hardware::tv::tuner::V1_0::FrontendStatusType::ATSC3_PLP_INFO,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::LnbVoltage, 9> hidl_enum_values<::android::hardware::tv::tuner::V1_0::LnbVoltage> = {
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::NONE,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_5V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_11V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_12V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_13V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_14V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_15V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_18V,
    ::android::hardware::tv::tuner::V1_0::LnbVoltage::VOLTAGE_19V,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::LnbTone, 2> hidl_enum_values<::android::hardware::tv::tuner::V1_0::LnbTone> = {
    ::android::hardware::tv::tuner::V1_0::LnbTone::NONE,
    ::android::hardware::tv::tuner::V1_0::LnbTone::CONTINUOUS,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::LnbPosition, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::LnbPosition> = {
    ::android::hardware::tv::tuner::V1_0::LnbPosition::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_A,
    ::android::hardware::tv::tuner::V1_0::LnbPosition::POSITION_B,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::LnbEventType, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::LnbEventType> = {
    ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_OVERFLOW,
    ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_TIMEOUT,
    ::android::hardware::tv::tuner::V1_0::LnbEventType::DISEQC_RX_PARITY_ERROR,
    ::android::hardware::tv::tuner::V1_0::LnbEventType::LNB_OVERLOAD,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType, 5> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxFilterMainType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TS,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::MMTP,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::IP,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::TLV,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterMainType::ALP,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxTsFilterType, 9> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxTsFilterType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::SECTION,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PES,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TS,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::AUDIO,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::VIDEO,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::PCR,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::RECORD,
    ::android::hardware::tv::tuner::V1_0::DemuxTsFilterType::TEMI,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType, 8> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::SECTION,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::PES,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::MMTP,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::AUDIO,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::VIDEO,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::RECORD,
    ::android::hardware::tv::tuner::V1_0::DemuxMmtpFilterType::DOWNLOAD,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxIpFilterType, 6> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxIpFilterType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::SECTION,
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::NTP,
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP_PAYLOAD,
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::IP,
    ::android::hardware::tv::tuner::V1_0::DemuxIpFilterType::PAYLOAD_THROUGH,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::SECTION,
    ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::TLV,
    ::android::hardware::tv::tuner::V1_0::DemuxTlvFilterType::PAYLOAD_THROUGH,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::SECTION,
    ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PTP,
    ::android::hardware::tv::tuner::V1_0::DemuxAlpFilterType::PAYLOAD_THROUGH,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::Constant, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::Constant> = {
    ::android::hardware::tv::tuner::V1_0::Constant::INVALID_TS_PID,
    ::android::hardware::tv::tuner::V1_0::Constant::INVALID_STREAM_ID,
    ::android::hardware::tv::tuner::V1_0::Constant::INVALID_FILTER_ID,
    ::android::hardware::tv::tuner::V1_0::Constant::INVALID_AV_SYNC_ID,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxFilterStatus, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxFilterStatus> = {
    ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::DATA_READY,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::LOW_WATER,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::HIGH_WATER,
    ::android::hardware::tv::tuner::V1_0::DemuxFilterStatus::OVERFLOW,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxTsIndex, 13> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxTsIndex> = {
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::FIRST_PACKET,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PAYLOAD_UNIT_START_INDICATOR,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_NOT_SCRAMBLED,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_EVEN_SCRAMBLED,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::CHANGE_TO_ODD_SCRAMBLED,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::DISCONTINUITY_INDICATOR,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::RANDOM_ACCESS_INDICATOR,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIORITY_INDICATOR,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PCR_FLAG,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::OPCR_FLAG,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::SPLICING_POINT_FLAG,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::PRIVATE_DATA,
    ::android::hardware::tv::tuner::V1_0::DemuxTsIndex::ADAPTATION_EXTENSION_FLAG,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxScIndex, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxScIndex> = {
    ::android::hardware::tv::tuner::V1_0::DemuxScIndex::I_FRAME,
    ::android::hardware::tv::tuner::V1_0::DemuxScIndex::P_FRAME,
    ::android::hardware::tv::tuner::V1_0::DemuxScIndex::B_FRAME,
    ::android::hardware::tv::tuner::V1_0::DemuxScIndex::SEQUENCE,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex, 8> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex> = {
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SPS,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::AUD,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_CE_BLA_W_LP,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_W_RADL,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_BLA_N_LP,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_W_RADL,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_IDR_N_LP,
    ::android::hardware::tv::tuner::V1_0::DemuxScHevcIndex::SLICE_TRAIL_CRA,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::NONE,
    ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC,
    ::android::hardware::tv::tuner::V1_0::DemuxRecordScIndexType::SC_HEVC,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType, 3> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType> = {
    ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::UNDEFINED,
    ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITHOUT_ADDITIONAL_HEADER,
    ::android::hardware::tv::tuner::V1_0::DemuxAlpLengthType::WITH_ADDITIONAL_HEADER,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits, 2> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits> = {
    ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_READY,
    ::android::hardware::tv::tuner::V1_0::DemuxQueueNotifyBits::DATA_CONSUMED,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DataFormat, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DataFormat> = {
    ::android::hardware::tv::tuner::V1_0::DataFormat::TS,
    ::android::hardware::tv::tuner::V1_0::DataFormat::PES,
    ::android::hardware::tv::tuner::V1_0::DataFormat::ES,
    ::android::hardware::tv::tuner::V1_0::DataFormat::SHV_TLV,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::PlaybackStatus, 4> hidl_enum_values<::android::hardware::tv::tuner::V1_0::PlaybackStatus> = {
    ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_EMPTY,
    ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_EMPTY,
    ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_ALMOST_FULL,
    ::android::hardware::tv::tuner::V1_0::PlaybackStatus::SPACE_FULL,
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
template<> inline constexpr std::array<::android::hardware::tv::tuner::V1_0::DvrType, 2> hidl_enum_values<::android::hardware::tv::tuner::V1_0::DvrType> = {
    ::android::hardware::tv::tuner::V1_0::DvrType::RECORD,
    ::android::hardware::tv::tuner::V1_0::DvrType::PLAYBACK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_TYPES_H
