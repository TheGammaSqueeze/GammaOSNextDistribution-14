#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class WifiStatusCode : uint32_t;
struct WifiStatus;
enum class IfaceType : uint32_t;
enum class WifiChannelWidthInMhz : uint32_t;
struct WifiChannelInfo;
struct WifiInformationElement;
enum class WifiRatePreamble : uint32_t;
enum class WifiRateNss : uint32_t;
struct WifiRateInfo;
enum class WifiBand : uint32_t;
struct StaApfPacketFilterCapabilities;
struct StaBackgroundScanCapabilities;
enum class StaBackgroundScanBucketEventReportSchemeMask : uint32_t;
enum class StaScanLimits : uint32_t;
struct StaBackgroundScanBucketParameters;
struct StaBackgroundScanParameters;
struct StaLinkLayerIfacePacketStats;
struct StaLinkLayerIfaceStats;
struct StaLinkLayerRadioStats;
struct StaLinkLayerStats;
struct StaScanResult;
enum class StaScanDataFlagMask : int32_t;
struct StaScanData;
struct StaRoamingCapabilities;
struct StaRoamingConfig;
enum class StaRoamingState : uint8_t;
enum class NanParamSizeLimits : uint32_t;
enum class NanStatusType : uint32_t;
enum class NanBandIndex : uint32_t;
struct WifiNanStatus;
enum class NanMatchAlg : uint32_t;
enum class NanPublishType : uint32_t;
enum class NanTxType : uint32_t;
enum class NanSubscribeType : uint32_t;
enum class NanSrfType : uint32_t;
enum class NanDataPathChannelCfg : uint32_t;
enum class NanDataPathSecurityType : uint32_t;
struct NanBandSpecificConfig;
struct NanDebugConfig;
struct NanConfigRequest;
struct NanEnableRequest;
enum class NanCipherSuiteType : uint32_t;
enum class NanRangingIndication : uint32_t;
struct NanDataPathSecurityConfig;
struct NanDiscoveryCommonConfig;
struct NanPublishRequest;
struct NanSubscribeRequest;
struct NanTransmitFollowupRequest;
struct NanInitiateDataPathRequest;
struct NanRespondToDataPathIndicationRequest;
struct NanCapabilities;
struct NanMatchInd;
struct NanFollowupReceivedInd;
enum class NanClusterEventType : uint32_t;
struct NanClusterEventInd;
struct NanDataPathRequestInd;
struct NanDataPathConfirmInd;
enum class RttStatus : uint32_t;
enum class RttPeerType : uint32_t;
enum class RttBw : uint32_t;
enum class RttPreamble : uint32_t;
enum class RttType : uint32_t;
struct RttConfig;
struct RttResult;
struct RttCapabilities;
enum class RttMotionPattern : uint32_t;
struct RttLciInformation;
struct RttLcrInformation;
struct RttResponder;
enum class WifiDebugRingBufferFlags : uint32_t;
struct WifiDebugRingBufferStatus;
enum class WifiDebugRingBufferVerboseLevel : uint32_t;
enum class WifiDebugTxPacketFate : uint32_t;
enum class WifiDebugRxPacketFate : uint32_t;
enum class WifiDebugPacketFateFrameType : uint32_t;
struct WifiDebugPacketFateFrameInfo;
struct WifiDebugTxPacketFateReport;
struct WifiDebugRxPacketFateReport;
struct WifiDebugHostWakeReasonRxPacketDetails;
struct WifiDebugHostWakeReasonRxMulticastPacketDetails;
struct WifiDebugHostWakeReasonRxIcmpPacketDetails;
struct WifiDebugHostWakeReasonStats;

/**
 * Enum values indicating the status of operation.
 */
enum class WifiStatusCode : uint32_t {
    /**
     * No errors.
     */
    SUCCESS = 0u,
    /**
     * Method invoked on an invalid |IWifiChip| object.
     */
    ERROR_WIFI_CHIP_INVALID = 1u /* ::android::hardware::wifi::V1_0::WifiStatusCode.SUCCESS implicitly + 1 */,
    /**
     * Method invoked on an invalid |IWifiIface| object.
     */
    ERROR_WIFI_IFACE_INVALID = 2u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_WIFI_CHIP_INVALID implicitly + 1 */,
    /**
     * Method invoked on an invalid |IWifiRttController| object.
     */
    ERROR_WIFI_RTT_CONTROLLER_INVALID = 3u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_WIFI_IFACE_INVALID implicitly + 1 */,
    ERROR_NOT_SUPPORTED = 4u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_WIFI_RTT_CONTROLLER_INVALID implicitly + 1 */,
    ERROR_NOT_AVAILABLE = 5u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_NOT_SUPPORTED implicitly + 1 */,
    ERROR_NOT_STARTED = 6u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_NOT_AVAILABLE implicitly + 1 */,
    ERROR_INVALID_ARGS = 7u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_NOT_STARTED implicitly + 1 */,
    ERROR_BUSY = 8u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_INVALID_ARGS implicitly + 1 */,
    ERROR_UNKNOWN = 9u /* ::android::hardware::wifi::V1_0::WifiStatusCode.ERROR_BUSY implicitly + 1 */,
};

/**
 * Generic structure to return the status of an operation.
 */
struct WifiStatus final {
    ::android::hardware::wifi::V1_0::WifiStatusCode code __attribute__ ((aligned(4)));
    /**
     * A vendor specific error message from the vendor to provide more
     * information beyond the reason code.
     */
    ::android::hardware::hidl_string description __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiStatus, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiStatus, description) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiStatus) == 8, "wrong alignment");

/**
 * List of Iface types supported.
 */
enum class IfaceType : uint32_t {
    STA = 0u,
    AP = 1u /* ::android::hardware::wifi::V1_0::IfaceType.STA implicitly + 1 */,
    P2P = 2u /* ::android::hardware::wifi::V1_0::IfaceType.AP implicitly + 1 */,
    /**
     * NAN control interface. Datapath support must be queried and created
     * through this interface.
     */
    NAN = 3u /* ::android::hardware::wifi::V1_0::IfaceType.P2P implicitly + 1 */,
};

/**
 * An identifier assigned to every chip on the device.
 */
typedef uint32_t ChipId;

/**
 * An identifier for a mode that the chip can be put in.
 */
typedef uint32_t ChipModeId;

/**
 * A unique handle provided by the client to identify individual invocations of
 * certain API's like |IWifiStaIface.startBackgroundScan|,
 * |IWifiStaIface.installApfPacketFilter|, etc.
 */
typedef uint32_t CommandId;

/**
 * Channel frequency in Mhz.
 */
typedef uint32_t WifiChannelInMhz;

/**
 * Channel operating width in Mhz.
 */
enum class WifiChannelWidthInMhz : uint32_t {
    WIDTH_20 = 0u,
    WIDTH_40 = 1u,
    WIDTH_80 = 2u,
    WIDTH_160 = 3u,
    WIDTH_80P80 = 4u,
    WIDTH_5 = 5u,
    WIDTH_10 = 6u,
    WIDTH_INVALID = 4294967295u /* -1 */,
};

/**
 * Channel information.
 */
struct WifiChannelInfo final {
    /**
     * Channel width (20, 40, 80, 80+80, 160).
     */
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz width __attribute__ ((aligned(4)));
    /**
     * Primary 20 MHz channel.
     */
    uint32_t centerFreq __attribute__ ((aligned(4)));
    /**
     * Center frequency (MHz) first segment.
     */
    uint32_t centerFreq0 __attribute__ ((aligned(4)));
    /**
     * Center frequency (MHz) second segment.
     */
    uint32_t centerFreq1 __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiChannelInfo, width) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiChannelInfo, centerFreq) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiChannelInfo, centerFreq0) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiChannelInfo, centerFreq1) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiChannelInfo) == 16, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiChannelInfo) == 4, "wrong alignment");

/**
 * RSSI information.
 */
typedef int32_t Rssi;

/**
 * Mac Address type. 6 octets representing physical address of a device.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> MacAddress;

/**
 * SSID type. 32 octets representing the network.
 */
typedef ::android::hardware::hidl_array<uint8_t, 32> Ssid;

/**
 * BSSID type. 6 octets representing the physical address of an AP.
 */
typedef ::android::hardware::hidl_array<uint8_t, 6> Bssid;

/**
 * TimeStamp in milliseconds (ms).
 */
typedef uint64_t TimeStampInMs;

/**
 * TimeStamp in microseconds (us).
 */
typedef uint64_t TimeStampInUs;

/**
 * TimeStamp in picoseconds (ps).
 */
typedef uint64_t TimeSpanInPs;

/**
 * Information elements contained within the |ScanResult| structure.
 * These elements correspond to the IEEE_802.11 standard.
 */
struct WifiInformationElement final {
    uint8_t id __attribute__ ((aligned(1)));
    ::android::hardware::hidl_vec<uint8_t> data __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiInformationElement, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiInformationElement, data) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiInformationElement) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiInformationElement) == 8, "wrong alignment");

enum class WifiRatePreamble : uint32_t {
    OFDM = 0u,
    CCK = 1u,
    HT = 2u,
    VHT = 3u,
    RESERVED = 4u,
};

/**
 * Number of spatial streams in VHT/HT.
 */
enum class WifiRateNss : uint32_t {
    NSS_1x1 = 0u,
    NSS_2x2 = 1u,
    NSS_3x3 = 2u,
    NSS_4x4 = 3u,
};

/**
 * Wifi rate info.
 */
struct WifiRateInfo final {
    /**
     * Preamble used for RTT measurements.
     */
    ::android::hardware::wifi::V1_0::WifiRatePreamble preamble __attribute__ ((aligned(4)));
    /**
     * Number of spatial streams.
     */
    ::android::hardware::wifi::V1_0::WifiRateNss nss __attribute__ ((aligned(4)));
    /**
     * Bandwidth of channel.
     */
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz bw __attribute__ ((aligned(4)));
    /**
     * OFDM/CCK rate code would be as per ieee std in the units of 0.5mbps.
     * HT/VHT it would be mcs index.
     */
    uint8_t rateMcsIdx __attribute__ ((aligned(1)));
    /**
     * Bitrate in units of 100 Kbps.
     */
    uint32_t bitRateInKbps __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiRateInfo, preamble) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiRateInfo, nss) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiRateInfo, bw) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiRateInfo, rateMcsIdx) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiRateInfo, bitRateInKbps) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiRateInfo) == 20, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiRateInfo) == 4, "wrong alignment");

/**
 * Wifi bands defined in 80211 spec.
 */
enum class WifiBand : uint32_t {
    BAND_UNSPECIFIED = 0u,
    /**
     * 2.4 GHz.
     */
    BAND_24GHZ = 1u,
    /**
     * 5 GHz without DFS.
     */
    BAND_5GHZ = 2u,
    /**
     * 5 GHz DFS only.
     */
    BAND_5GHZ_DFS = 4u,
    /**
     * 5 GHz with DFS.
     */
    BAND_5GHZ_WITH_DFS = 6u,
    /**
     * 2.4 GHz + 5 GHz; no DFS.
     */
    BAND_24GHZ_5GHZ = 3u,
    /**
     * 2.4 GHz + 5 GHz with DFS
     */
    BAND_24GHZ_5GHZ_WITH_DFS = 7u,
};

/**
 * STA specific types.
 * TODO(b/32159498): Move to a separate sta_types.hal.
 *
 *
 * Parameters to specify the APF capabilities of this iface.
 */
struct StaApfPacketFilterCapabilities final {
    /**
     * Version of the packet filter interpreter supported
     */
    uint32_t version __attribute__ ((aligned(4)));
    /**
     * Maximum size of the filter bytecodes in byte for an iface.
     */
    uint32_t maxLength __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities, version) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities, maxLength) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities) == 8, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities) == 4, "wrong alignment");

/**
 * Parameters to specify the Background Scan capabilities of this iface.
 */
struct StaBackgroundScanCapabilities final {
    /**
     * Maximum number of byte available for cached scan results
     */
    uint32_t maxCacheSize __attribute__ ((aligned(4)));
    /**
     * Maximum number of buckets that can be supplied for a scan
     */
    uint32_t maxBuckets __attribute__ ((aligned(4)));
    /**
     * Maximum number of APs that must be stored for each scan.
     */
    uint32_t maxApCachePerScan __attribute__ ((aligned(4)));
    /**
     * Max reporting number of scans threshold that can be specified in the scan
     * parameters.
     */
    int32_t maxReportingThreshold __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities, maxCacheSize) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities, maxBuckets) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities, maxApCachePerScan) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities, maxReportingThreshold) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities) == 16, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities) == 4, "wrong alignment");

/**
 * Mask of event reporting schemes that can be specified in background scan
 * requests.
 */
enum class StaBackgroundScanBucketEventReportSchemeMask : uint32_t {
    /**
     * Report a scan completion event after scan. If this is not set then scan
     * completion events must be reported if report_threshold_percent or
     * report_threshold_num_scans is reached.
     */
    EACH_SCAN = 1u /* 1 << 0 */,
    /**
     * Forward scan results (beacons/probe responses + IEs) in real time to HAL,
     * in addition to completion events.
     * Note: To keep backward compatibility, fire completion events regardless
     * of REPORT_EVENTS_EACH_SCAN.
     */
    FULL_RESULTS = 2u /* 1 << 1 */,
    /**
     * Controls if scans for this bucket must be placed in the results buffer.
     */
    NO_BATCH = 4u /* 1 << 2 */,
};

/**
 * Max limits for background scan.
 */
enum class StaScanLimits : uint32_t {
    MAX_CHANNELS = 16u,
    MAX_BUCKETS = 16u,
    MAX_AP_CACHE_PER_SCAN = 32u,
};

/**
 * Background Scan parameters per bucket that can be specified in background
 * scan requests.
 */
struct StaBackgroundScanBucketParameters final {
    /**
     * Bucket index. This index is used to report results in
     * |StaScanData.bucketsScanned|.
     */
    uint32_t bucketIdx __attribute__ ((aligned(4)));
    /**
     * Bands to scan or |BAND_UNSPECIFIED| if frequencies list must be used
     * instead.
     */
    ::android::hardware::wifi::V1_0::WifiBand band __attribute__ ((aligned(4)));
    /**
     * Channel frequencies (in Mhz) to scan if |band| is set to
     * |BAND_UNSPECIFIED|.
     * Max length: |StaScanLimits.MAX_CHANNELS|.
     */
    ::android::hardware::hidl_vec<uint32_t> frequencies __attribute__ ((aligned(8)));
    /**
     * Period at which this bucket must be scanned (in milliseconds). Must be an integer
     * multiple of the |basePeriodInMs| specified in the BackgroundScanParameters.
     */
    uint32_t periodInMs __attribute__ ((aligned(4)));
    /**
     * Bitset of |StaBackgroundScanBucketEventReportSchemeMask| values controlling
     * when events for this bucket must be reported.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask> eventReportScheme __attribute__ ((aligned(4)));
    /**
     * For exponential back off. If |exponentialMaxPeriodInMs| is non zero or
     * different than period, then this bucket is an exponential backoff bucket
     * and the scan period must grow exponentially as per formula:
     *   actual_period(N) = period * (base ^ (N/step_count))
     * to this maximum period (in milliseconds).
     */
    uint32_t exponentialMaxPeriodInMs __attribute__ ((aligned(4)));
    /**
     * For exponential back off. multiplier: new_period=old_period * base
     */
    uint32_t exponentialBase __attribute__ ((aligned(4)));
    /**
     * For exponential back off. Number of scans to perform for a given
     * period.
     */
    uint32_t exponentialStepCount __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, bucketIdx) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, band) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, frequencies) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, periodInMs) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, eventReportScheme) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, exponentialMaxPeriodInMs) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, exponentialBase) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters, exponentialStepCount) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters) == 48, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters) == 8, "wrong alignment");

/**
 * Background Scan parameters that can be specified in background scan
 * requests.
 */
struct StaBackgroundScanParameters final {
    /**
     * GCD of all bucket periods (in milliseconds).
     */
    uint32_t basePeriodInMs __attribute__ ((aligned(4)));
    /**
     * Maximum number of APs that must be stored for each scan. If the maximum
     * is reached the highest RSSI results must be returned.
     * Max length: |StaScanLimits.MAX_AP_CACHE_PER_SCAN|.
     */
    uint32_t maxApPerScan __attribute__ ((aligned(4)));
    /**
     * % cache buffer filled threshold at which the host must be notified of
     * batched scan results.
     */
    uint32_t reportThresholdPercent __attribute__ ((aligned(4)));
    /**
     * Threshold at which the AP must be woken up, in number of scans.
     */
    uint32_t reportThresholdNumScans __attribute__ ((aligned(4)));
    /**
     * List of buckets to be scheduled.
     * Max length: |StaScanLimits.MAX_BUCKETS|.
     */
    ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters> buckets __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters, basePeriodInMs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters, maxApPerScan) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters, reportThresholdPercent) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters, reportThresholdNumScans) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters, buckets) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters) == 32, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaBackgroundScanParameters) == 8, "wrong alignment");

/**
 * Packet stats for different traffic categories.
 */
struct StaLinkLayerIfacePacketStats final {
    /**
     * Number of received unicast data packets.
     */
    uint64_t rxMpdu __attribute__ ((aligned(8)));
    /**
     * Number of successfully transmitted unicast data pkts (ACK rcvd).
     */
    uint64_t txMpdu __attribute__ ((aligned(8)));
    /**
     * Number of transmitted unicast data pkt losses (no ACK).
     */
    uint64_t lostMpdu __attribute__ ((aligned(8)));
    /**
     * Number of transmitted unicast data retry pkts.
     */
    uint64_t retries __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats, rxMpdu) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats, txMpdu) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats, lostMpdu) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats, retries) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats) == 32, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats) == 8, "wrong alignment");

/**
 * Iface statistics for the current connection.
 */
struct StaLinkLayerIfaceStats final {
    /**
     * Number beacons received from the connected AP.
     */
    uint32_t beaconRx __attribute__ ((aligned(4)));
    /**
     * Access Point Beacon and Management frames RSSI (averaged).
     */
    int32_t avgRssiMgmt __attribute__ ((aligned(4)));
    /**
     * WME Best Effort Access Category packet counters.
     */
    ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats wmeBePktStats __attribute__ ((aligned(8)));
    /**
     * WME Background Access Category packet counters.
     */
    ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats wmeBkPktStats __attribute__ ((aligned(8)));
    /**
     * WME Video Access Category packet counters.
     */
    ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats wmeViPktStats __attribute__ ((aligned(8)));
    /**
     * WME Voice Access Category packet counters.
     */
    ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats wmeVoPktStats __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats, beaconRx) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats, avgRssiMgmt) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats, wmeBePktStats) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats, wmeBkPktStats) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats, wmeViPktStats) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats, wmeVoPktStats) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats) == 136, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats) == 8, "wrong alignment");

/**
 * Cumulative radio statistics since collection was enabled.
 */
struct StaLinkLayerRadioStats final {
    /**
     * Time for which the radio is awake.
     */
    uint32_t onTimeInMs __attribute__ ((aligned(4)));
    /**
     * Total time for which the radio is in active transmission.
     */
    uint32_t txTimeInMs __attribute__ ((aligned(4)));
    /**
     * Time for which the radio is in active tranmission per tx level.
     */
    ::android::hardware::hidl_vec<uint32_t> txTimeInMsPerLevel __attribute__ ((aligned(8)));
    /**
     * Time for which the radio is in active receive.
     */
    uint32_t rxTimeInMs __attribute__ ((aligned(4)));
    /**
     *  Total time for which the radio is awake due to scan.
     */
    uint32_t onTimeInMsForScan __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats, onTimeInMs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats, txTimeInMs) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats, txTimeInMsPerLevel) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats, rxTimeInMs) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats, onTimeInMsForScan) == 28, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats) == 32, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaLinkLayerRadioStats) == 8, "wrong alignment");

/**
 * Link layer stats retrieved via |getLinkLayerStats|.
 */
struct StaLinkLayerStats final {
    ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats iface __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::StaLinkLayerRadioStats> radios __attribute__ ((aligned(8)));
    /**
     * TimeStamp for each stats sample.
     * This is the absolute milliseconds from boot when these stats were
     * sampled.
     */
    uint64_t timeStampInMs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerStats, iface) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerStats, radios) == 136, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaLinkLayerStats, timeStampInMs) == 152, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaLinkLayerStats) == 160, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaLinkLayerStats) == 8, "wrong alignment");

/**
 * Structure describing all the information about a single access point seen
 * during the scan.
 */
struct StaScanResult final {
    uint64_t timeStampInUs __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<uint8_t> ssid __attribute__ ((aligned(8)));
    ::android::hardware::hidl_array<uint8_t, 6> bssid __attribute__ ((aligned(1)));
    int32_t rssi __attribute__ ((aligned(4)));
    uint32_t frequency __attribute__ ((aligned(4)));
    uint16_t beaconPeriodInMs __attribute__ ((aligned(2)));
    uint16_t capability __attribute__ ((aligned(2)));
    ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::WifiInformationElement> informationElements __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, timeStampInUs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, ssid) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, bssid) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, rssi) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, frequency) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, beaconPeriodInMs) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, capability) == 42, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanResult, informationElements) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaScanResult) == 64, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaScanResult) == 8, "wrong alignment");

/**
 * Mask of flags set in the |ScanData| instance.
 */
enum class StaScanDataFlagMask : int32_t {
    /**
     * Indicates that a scan was interrupted/did not occur so results may be
     * incomplete.
     */
    INTERRUPTED = 1 /* 1 << 0 */,
};

/**
 * Structure describing all the information about all the access points seen during
 * the scan.
 */
struct StaScanData final {
    /**
     * Bitset containing |ScanDataFlagMask| values.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::StaScanDataFlagMask> flags __attribute__ ((aligned(4)));
    /**
     * Bitset where each bit indicates if the bucket with that index (starting at
     * 0) was scanned.
     */
    uint32_t bucketsScanned __attribute__ ((aligned(4)));
    /**
     * List of scan results.
     */
    ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::StaScanResult> results __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanData, flags) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanData, bucketsScanned) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaScanData, results) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaScanData) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaScanData) == 8, "wrong alignment");

/**
 * Structure describing the roaming control capabilities supported.
 */
struct StaRoamingCapabilities final {
    /**
     * Maximum number of BSSID's that may be blacklisted.
     */
    uint32_t maxBlacklistSize __attribute__ ((aligned(4)));
    /**
     * Maximum number of SSID's that may be whitelisted.
     */
    uint32_t maxWhitelistSize __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaRoamingCapabilities, maxBlacklistSize) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaRoamingCapabilities, maxWhitelistSize) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaRoamingCapabilities) == 8, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaRoamingCapabilities) == 4, "wrong alignment");

/**
 * Structure describing the roaming control configuration.
 */
struct StaRoamingConfig final {
    /**
     * List of BSSID's that are blacklisted for roaming.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 6>> bssidBlacklist __attribute__ ((aligned(8)));
    /**
     * List of SSID's that are whitelisted for roaming.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>> ssidWhitelist __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::StaRoamingConfig, bssidBlacklist) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::StaRoamingConfig, ssidWhitelist) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::StaRoamingConfig) == 32, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::StaRoamingConfig) == 8, "wrong alignment");

/**
 * Enum describing the various states to set the roaming
 * control to.
 */
enum class StaRoamingState : uint8_t {
    /**
     * Driver/Firmware must not perform any roaming.
     */
    DISABLED = 0,
    /**
     * Driver/Firmware is allowed to perform roaming respecting
     * the |StaRoamingConfig| parameters set using |configureRoaming|.
     */
    ENABLED = 1,
};

/**
 * NAN specific types.
 * TODO(b/32159498): Move to a separate nan_types.hal.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness
 * Networking (NAN) Technical Specification".
 *
 *
 * Size limits for parameters used in the NAN interface.
 */
enum class NanParamSizeLimits : uint32_t {
    /**
     * Minimum length of Passphrase argument for data-path configuration
     */
    MIN_PASSPHRASE_LENGTH = 8u,
    /**
     * Maximum length of Passphrase argument for data-path configuration
     */
    MAX_PASSPHRASE_LENGTH = 63u,
};

/**
 * A unique short handle provided by the client to identify individual invocations of
 * certain API's like |IWifiNanIface.*|.
 */
typedef uint16_t CommandIdShort;

/**
 * NAN API response codes used in request notifications and events.
 */
enum class NanStatusType : uint32_t {
    SUCCESS = 0u,
    /**
     * NAN Discovery Engine/Host driver failures
     */
    INTERNAL_FAILURE = 1u,
    /**
     * NAN OTA failures
     */
    PROTOCOL_FAILURE = 2u,
    /**
     * The publish/subscribe discovery session id is invalid
     */
    INVALID_SESSION_ID = 3u,
    /**
     * Out of resources to fufill request
     */
    NO_RESOURCES_AVAILABLE = 4u,
    /**
     * Invalid arguments passed
     */
    INVALID_ARGS = 5u,
    /**
     * Invalid peer id
     */
    INVALID_PEER_ID = 6u,
    /**
     * Invalid NAN data-path (ndp) id
     */
    INVALID_NDP_ID = 7u,
    /**
     * Attempting to enable NAN when not available, e.g. wifi is disabled
     */
    NAN_NOT_ALLOWED = 8u,
    /**
     * Over the air ACK not received
     */
    NO_OTA_ACK = 9u,
    /**
     * Attempting to enable NAN when already enabled
     */
    ALREADY_ENABLED = 10u,
    /**
     * Can't queue tx followup message foor transmission
     */
    FOLLOWUP_TX_QUEUE_FULL = 11u,
    /**
     * Unsupported concurrency of NAN and another feature - NAN disabled
     */
    UNSUPPORTED_CONCURRENCY_NAN_DISABLED = 12u,
};

/**
 * The discovery bands supported by NAN.
 */
enum class NanBandIndex : uint32_t {
    NAN_BAND_24GHZ = 0u,
    NAN_BAND_5GHZ = 1u /* ::android::hardware::wifi::V1_0::NanBandIndex.NAN_BAND_24GHZ implicitly + 1 */,
};

/**
 * The status information returned in NAN notifications.
 */
struct WifiNanStatus final {
    /**
     * Status of the command request.
     */
    ::android::hardware::wifi::V1_0::NanStatusType status __attribute__ ((aligned(4)));
    /**
     * Further description of the issue causing a failure.
     */
    ::android::hardware::hidl_string description __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiNanStatus, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiNanStatus, description) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiNanStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiNanStatus) == 8, "wrong alignment");

/**
 * NAN Match indication type: control how often to trigger |IWifiNanIfaceEventCallback.eventMatch|
 * for a single discovery session - i.e. continuously discovering the same publisher with no new
 * data.
 */
enum class NanMatchAlg : uint32_t {
    MATCH_ONCE = 0u,
    MATCH_CONTINUOUS = 1u /* ::android::hardware::wifi::V1_0::NanMatchAlg.MATCH_ONCE implicitly + 1 */,
    MATCH_NEVER = 2u /* ::android::hardware::wifi::V1_0::NanMatchAlg.MATCH_CONTINUOUS implicitly + 1 */,
};

/**
 * NAN publish discovery session types.
 */
enum class NanPublishType : uint32_t {
    UNSOLICITED = 0u,
    SOLICITED = 1u /* ::android::hardware::wifi::V1_0::NanPublishType.UNSOLICITED implicitly + 1 */,
    UNSOLICITED_SOLICITED = 2u /* ::android::hardware::wifi::V1_0::NanPublishType.SOLICITED implicitly + 1 */,
};

/**
 * NAN transmit type used in |NanPublishType.SOLICITED| or |NanPublishType.UNSOLICITED_SOLICITED|
 * publish discovery sessions. Describes the addressing of the packet responding to an ACTIVE
 * subscribe query.
 */
enum class NanTxType : uint32_t {
    BROADCAST = 0u,
    UNICAST = 1u /* ::android::hardware::wifi::V1_0::NanTxType.BROADCAST implicitly + 1 */,
};

/**
 * NAN subscribe discovery session types.
 */
enum class NanSubscribeType : uint32_t {
    PASSIVE = 0u,
    ACTIVE = 1u /* ::android::hardware::wifi::V1_0::NanSubscribeType.PASSIVE implicitly + 1 */,
};

/**
 * NAN Service Response Filter Attribute Bit.
 */
enum class NanSrfType : uint32_t {
    BLOOM_FILTER = 0u,
    PARTIAL_MAC_ADDR = 1u /* ::android::hardware::wifi::V1_0::NanSrfType.BLOOM_FILTER implicitly + 1 */,
};

/**
 * NAN DP (data-path) channel config options.
 */
enum class NanDataPathChannelCfg : uint32_t {
    CHANNEL_NOT_REQUESTED = 0u,
    REQUEST_CHANNEL_SETUP = 1u /* ::android::hardware::wifi::V1_0::NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED implicitly + 1 */,
    FORCE_CHANNEL_SETUP = 2u /* ::android::hardware::wifi::V1_0::NanDataPathChannelCfg.REQUEST_CHANNEL_SETUP implicitly + 1 */,
};

/**
 * NAN DP (data-path) security configuration options.
 */
enum class NanDataPathSecurityType : uint32_t {
    OPEN = 0u,
    PMK = 1u /* ::android::hardware::wifi::V1_0::NanDataPathSecurityType.OPEN implicitly + 1 */,
    PASSPHRASE = 2u /* ::android::hardware::wifi::V1_0::NanDataPathSecurityType.PMK implicitly + 1 */,
};

/**
 * NAN band-specific configuration.
 */
struct NanBandSpecificConfig final {
    /**
     * RSSI values controlling clustering behavior per spec. RSSI values are specified without a sign,
     * e.g. a value of -65dBm would be specified as 65.
     */
    uint8_t rssiClose __attribute__ ((aligned(1)));
    uint8_t rssiMiddle __attribute__ ((aligned(1)));
    /**
     * RSSI value determining whether discovery is near (used if enabled in discovery by
     * |NanDiscoveryCommonConfig.useRssiThreshold|).
     * RSSI values are specified without a sign, e.g. a value of -65dBm would be specified as 65.
     * NAN Spec: RSSI_close_proximity
     */
    uint8_t rssiCloseProximity __attribute__ ((aligned(1)));
    /**
     * Dwell time of each discovery channel in milliseconds. If set to 0 then the firmware determines
     * the dwell time to use.
     */
    uint8_t dwellTimeMs __attribute__ ((aligned(1)));
    /**
     * Scan period of each discovery channel in seconds. If set to 0 then the firmware determines
     * the scan period to use.
     */
    uint16_t scanPeriodSec __attribute__ ((aligned(2)));
    /**
     * Specifies the discovery window interval for Sync beacons and SDF's.
     * Valid values of DW Interval are: 1, 2, 3, 4 and 5 corresponding to 1, 2, 4, 8, and 16 DWs.
     * Value of 0:
     *  - reserved in 2.4GHz band
     *  - no wakeup at all in 5GHz band
     * The publish/subscribe period values don't override this device level configurations if
     * it is specified.
     * Configuration is only used only if |validDiscoveryWindowIntervalVal| is set to true.
     * NAN Spec: Device Capability Attribute / 2.4 GHz DW, Device Capability Attribute / 5 GHz DW
     */
    bool validDiscoveryWindowIntervalVal __attribute__ ((aligned(1)));
    uint8_t discoveryWindowIntervalVal __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, rssiClose) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, rssiMiddle) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, rssiCloseProximity) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, dwellTimeMs) == 3, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, scanPeriodSec) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, validDiscoveryWindowIntervalVal) == 6, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanBandSpecificConfig, discoveryWindowIntervalVal) == 7, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanBandSpecificConfig) == 8, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanBandSpecificConfig) == 2, "wrong alignment");

/**
 * Debug configuration parameters. Many of these allow non-standard-compliant operation and are
 * not intended for normal operational mode.
 */
struct NanDebugConfig final {
    /**
     * Specification of the lower 2 bytes of the cluster ID. The cluster ID is 50-60-9a-01-00-00 to
     * 50-60-9a-01-FF-FF. Configuration of the bottom and top values of the range (which defaults to
     * 0x0000 and 0xFFFF respectively).
     * Configuration is only used if |validClusterIdVals| is set to true.
     */
    bool validClusterIdVals __attribute__ ((aligned(1)));
    uint16_t clusterIdBottomRangeVal __attribute__ ((aligned(2)));
    uint16_t clusterIdTopRangeVal __attribute__ ((aligned(2)));
    /**
     * NAN management interface address, if specified (|validIntfAddrVal| is true) then overrides any
     * other configuration (specifically the default randomization configured by
     * |NanConfigRequest.macAddressRandomizationIntervalSec|).
     */
    bool validIntfAddrVal __attribute__ ((aligned(1)));
    ::android::hardware::hidl_array<uint8_t, 6> intfAddrVal __attribute__ ((aligned(1)));
    /**
     * Combination of the 24 bit Organizationally Unique ID (OUI) and the 8 bit OUI Type.
     * Used if |validOuiVal| is set to true.
     */
    bool validOuiVal __attribute__ ((aligned(1)));
    uint32_t ouiVal __attribute__ ((aligned(4)));
    /**
     * Force the Random Factor to the specified value for all transmitted Sync/Discovery beacons.
     * Used if |validRandomFactorForceVal| is set to true.
     * NAN Spec: Master Indication Attribute / Random Factor
     */
    bool validRandomFactorForceVal __attribute__ ((aligned(1)));
    uint8_t randomFactorForceVal __attribute__ ((aligned(1)));
    /**
     * Forces the hop-count for all transmitted Sync and Discovery Beacons NO matter the real
     * hop-count being received over the air. Used if the |validHopCountForceVal}| flag is set to
     * true.
     * NAN Spec: Cluster Attribute / Anchor Master Information / Hop Count to Anchor Master
     */
    bool validHopCountForceVal __attribute__ ((aligned(1)));
    uint8_t hopCountForceVal __attribute__ ((aligned(1)));
    /**
     * Frequency in MHz to of the discovery channel in the specified band. Indexed by |NanBandIndex|.
     * Used if the |validDiscoveryChannelVal| is set to true.
     */
    bool validDiscoveryChannelVal __attribute__ ((aligned(1)));
    ::android::hardware::hidl_array<uint32_t, 2> discoveryChannelMhzVal __attribute__ ((aligned(4)));
    /**
     * Specifies whether sync/discovery beacons are transmitted in the specified band. Indexed by
     * |NanBandIndex|. Used if the |validUseBeaconsInBandVal| is set to true.
     */
    bool validUseBeaconsInBandVal __attribute__ ((aligned(1)));
    ::android::hardware::hidl_array<bool, 2> useBeaconsInBandVal __attribute__ ((aligned(1)));
    /**
     * Specifies whether SDF (service discovery frames) are transmitted in the specified band. Indexed
     * by |NanBandIndex|. Used if the |validUseSdfInBandVal| is set to true.
     */
    bool validUseSdfInBandVal __attribute__ ((aligned(1)));
    ::android::hardware::hidl_array<bool, 2> useSdfInBandVal __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validClusterIdVals) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, clusterIdBottomRangeVal) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, clusterIdTopRangeVal) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validIntfAddrVal) == 6, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, intfAddrVal) == 7, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validOuiVal) == 13, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, ouiVal) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validRandomFactorForceVal) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, randomFactorForceVal) == 21, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validHopCountForceVal) == 22, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, hopCountForceVal) == 23, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validDiscoveryChannelVal) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, discoveryChannelMhzVal) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validUseBeaconsInBandVal) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, useBeaconsInBandVal) == 37, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, validUseSdfInBandVal) == 39, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDebugConfig, useSdfInBandVal) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanDebugConfig) == 44, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanDebugConfig) == 4, "wrong alignment");

/**
 * Configuration parameters of NAN: used when enabling and re-configuring a NAN cluster.
 */
struct NanConfigRequest final {
    /**
     * Master preference of this device.
     * NAN Spec: Master Indication Attribute / Master Preference
     */
    uint8_t masterPref __attribute__ ((aligned(1)));
    /**
     * Controls whether or not the |IWifiNanIfaceEventCallback.eventClusterEvent| will be delivered
     * for |NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED|.
     */
    bool disableDiscoveryAddressChangeIndication __attribute__ ((aligned(1)));
    /**
     * Controls whether or not the |IWifiNanIfaceEventCallback.eventClusterEvent| will be delivered
     * for |NanClusterEventType.STARTED_CLUSTER|.
     */
    bool disableStartedClusterIndication __attribute__ ((aligned(1)));
    /**
     * Controls whether or not the |IWifiNanIfaceEventCallback.eventClusterEvent| will be delivered
     * for |NanClusterEventType.JOINED_CLUSTER|.
     */
    bool disableJoinedClusterIndication __attribute__ ((aligned(1)));
    /**
     * Control whether publish service IDs are included in Sync/Discovery beacons.
     * NAN Spec: Service ID List Attribute
     */
    bool includePublishServiceIdsInBeacon __attribute__ ((aligned(1)));
    /**
     * If |includePublishServiceIdsInBeacon| is true then specifies the number of publish service IDs
     * to include in the Sync/Discovery beacons:
     *  Value = 0: include as many service IDs as will fit into the maximum allowed beacon frame size.
     *  Value must fit within 7 bits - i.e. <= 127.
     */
    uint8_t numberOfPublishServiceIdsInBeacon __attribute__ ((aligned(1)));
    /**
     * Control whether subscribe service IDs are included in Sync/Discovery beacons.
     * Spec: Subscribe Service ID List Attribute
     */
    bool includeSubscribeServiceIdsInBeacon __attribute__ ((aligned(1)));
    /**
     * If |includeSubscribeServiceIdsInBeacon| is true then specifies the number of subscribe service
     * IDs to include in the Sync/Discovery beacons:
     *  Value = 0: include as many service IDs as will fit into the maximum allowed beacon frame size.
     *  Value must fit within 7 bits - i.e. <= 127.
     */
    uint8_t numberOfSubscribeServiceIdsInBeacon __attribute__ ((aligned(1)));
    /**
     * Number of samples used to calculate RSSI.
     */
    uint16_t rssiWindowSize __attribute__ ((aligned(2)));
    /**
     * Specifies the interval in seconds that the NAN management interface MAC address is randomized.
     * A value of 0 is used to disable the MAC address randomization
     */
    uint32_t macAddressRandomizationIntervalSec __attribute__ ((aligned(4)));
    /**
     * Additional configuration provided per band: indexed by |NanBandIndex|.
     */
    ::android::hardware::hidl_array<::android::hardware::wifi::V1_0::NanBandSpecificConfig, 2> bandSpecificConfig __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, masterPref) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, disableDiscoveryAddressChangeIndication) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, disableStartedClusterIndication) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, disableJoinedClusterIndication) == 3, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, includePublishServiceIdsInBeacon) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, numberOfPublishServiceIdsInBeacon) == 5, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, includeSubscribeServiceIdsInBeacon) == 6, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, numberOfSubscribeServiceIdsInBeacon) == 7, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, rssiWindowSize) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, macAddressRandomizationIntervalSec) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanConfigRequest, bandSpecificConfig) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanConfigRequest) == 32, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanConfigRequest) == 4, "wrong alignment");

/**
 * Enable requests for NAN: start-up configuration |IWifiNanIface.enableRequest|.
 */
struct NanEnableRequest final {
    /**
     * Enable operation in a specific band: indexed by |NanBandIndex|.
     */
    ::android::hardware::hidl_array<bool, 2> operateInBand __attribute__ ((aligned(1)));
    /**
     * Specify extent of cluster by specifying the max hop count.
     */
    uint8_t hopCountMax __attribute__ ((aligned(1)));
    /**
     * Configurations of NAN cluster operation. Can also be modified at run-time using
     * |IWifiNanIface.configRequest|.
     */
    ::android::hardware::wifi::V1_0::NanConfigRequest configParams __attribute__ ((aligned(4)));
    /**
     * Non-standard configurations of NAN cluster operation - useful for debugging operations.
     */
    ::android::hardware::wifi::V1_0::NanDebugConfig debugConfigs __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanEnableRequest, operateInBand) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanEnableRequest, hopCountMax) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanEnableRequest, configParams) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanEnableRequest, debugConfigs) == 36, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanEnableRequest) == 80, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanEnableRequest) == 4, "wrong alignment");

/**
 * Cipher suite flags.
 */
enum class NanCipherSuiteType : uint32_t {
    NONE = 0u,
    SHARED_KEY_128_MASK = 1u /* 1 << 0 */,
    SHARED_KEY_256_MASK = 2u /* 1 << 1 */,
};

/**
 * Ranging in the context of discovery sessions indication controls. Controls the frequency of
 * ranging-driven |IWifiNanIfaceEventCallback.eventMatch|.
 */
enum class NanRangingIndication : uint32_t {
    CONTINUOUS_INDICATION_MASK = 1u /* 1 << 0 */,
    INGRESS_MET_MASK = 2u /* 1 << 1 */,
    EGRESS_MET_MASK = 4u /* 1 << 2 */,
};

/**
 * Configuration of NAN data-path security.
 */
struct NanDataPathSecurityConfig final {
    /**
     * Security configuration of the data-path (NDP). Security is required if not equal to
     * |NanDataPathSecurityType.OPEN|.
     * NAN Spec: Service Discovery Extension Attribute (SDEA) / Control / Security Required
     */
    ::android::hardware::wifi::V1_0::NanDataPathSecurityType securityType __attribute__ ((aligned(4)));
    /**
     * Cipher type for data-paths. If |securityType| is |NanDataPathSecurityType.OPEN| then must
     * be set to |NanCipherSuiteType.NONE|, otherwise a non-|NanCipherSuiteType.NONE| cipher suite
     * must be specified.
     */
    ::android::hardware::wifi::V1_0::NanCipherSuiteType cipherType __attribute__ ((aligned(4)));
    /**
     * Optional Pairwise Master Key (PMK). Must be specified (and is only used) if |securityType| is
     * set to |NanDataPathSecurityType.PMK|.
     * Ref: IEEE 802.11i
     */
    ::android::hardware::hidl_array<uint8_t, 32> pmk __attribute__ ((aligned(1)));
    /**
     * Optional Passphrase. Must be specified (and is only used) if |securityType| is set to
     * |NanDataPathSecurityType.PASSPHRASE|.
     * Min length: |MIN_PASSPHRASE_LENGTH|
     * Max length: |MAX_PASSPHRASE_LENGTH|
     * NAN Spec: Appendix: Mapping pass-phrase to PMK for NCS-SK Cipher Suites
     */
    ::android::hardware::hidl_vec<uint8_t> passphrase __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathSecurityConfig, securityType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathSecurityConfig, cipherType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathSecurityConfig, pmk) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathSecurityConfig, passphrase) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanDataPathSecurityConfig) == 56, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanDataPathSecurityConfig) == 8, "wrong alignment");

/**
 * Configurations of NAN discovery sessions: common to publish and subscribe discovery.
 */
struct NanDiscoveryCommonConfig final {
    /**
     * The ID of the discovery session being configured. A value of 0 specifies a request to create
     * a new discovery session. The new discovery session ID is returned with
     * |IWifiNanIfaceEventCallback.notifyStartPublishResponse| or
     * |IWifiNanIfaceEventCallback.notifyStartSubscribeResponse|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    uint8_t sessionId __attribute__ ((aligned(1)));
    /**
     * The lifetime of the discovery session in seconds. A value of 0 means run forever or until
     * canceled using |IWifiIface.stopPublishRequest| or |IWifiIface.stopSubscribeRequest|.
     */
    uint16_t ttlSec __attribute__ ((aligned(2)));
    /**
     * Indicates the interval between two Discovery Windows in which the device supporting the
     * service is awake to transmit or receive the Service Discovery frames. Valid values of Awake
     * DW Interval are: 1, 2, 4, 8 and 16. A value of 0 will default to 1. Does not override
     * |NanBandSpecificConfig.discoveryWindowIntervalVal| configurations if those are specified.
     */
    uint16_t discoveryWindowPeriod __attribute__ ((aligned(2)));
    /**
     * The lifetime of the discovery session in number of transmitted SDF discovery packets. A value
     * of 0 means forever or until canceled using |IWifiIface.stopPublishRequest| or
     * |IWifiIface.stopSubscribeRequest|.
     */
    uint8_t discoveryCount __attribute__ ((aligned(1)));
    /**
     * UTF-8 encoded string identifying the service.
     * Max length: |NanCapabilities.maxServiceNameLen|.
     * NAN Spec: The only acceptable single-byte UTF-8 symbols for a Service Name are alphanumeric
     * values (A-Z, a-z, 0-9), the hyphen ('-'), and the period ('.'). All valid multi-byte UTF-8
     * characters are acceptable in a Service Name.
     */
    ::android::hardware::hidl_vec<uint8_t> serviceName __attribute__ ((aligned(8)));
    /**
     * Specifies how often to trigger |IWifiNanIfaceEventCallback.eventMatch| when continuously
     * discovering the same discovery session (with no changes).
     */
    ::android::hardware::wifi::V1_0::NanMatchAlg discoveryMatchIndicator __attribute__ ((aligned(4)));
    /**
     * Arbitrary information communicated in discovery packets - there is no semantic meaning to these
     * bytes. They are passed-through from publisher to subscriber as-is with no parsing.
     * Max length: |NanCapabilities.maxServiceSpecificInfoLen|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> serviceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * Arbitrary information communicated in discovery packets - there is no semantic meaning to these
     * bytes. They are passed-through from publisher to subscriber as-is with no parsing.
     * Max length: |NanCapabilities.maxExtendedServiceSpecificInfoLen|.
     * Spec: Service Descriptor Extension Attribute (SDEA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> extendedServiceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * Ordered sequence of <length, value> pairs (|length| uses 1 byte and contains the number of
     * bytes in the |value| field) which specify further match criteria (beyond the service name).
     * The match behavior is specified in details in the NAN spec.
     * Publisher: used in SOLICITED or SOLICITED_UNSOLICITED sessions.
     * Subscriber: used in ACTIVE or PASSIVE sessions.
     * Max length: |NanCapabilities.maxMatchFilterLen|.
     * NAN Spec: matching_filter_rx
     */
    ::android::hardware::hidl_vec<uint8_t> rxMatchFilter __attribute__ ((aligned(8)));
    /**
     * Ordered sequence of <length, value> pairs (|length| uses 1 byte and contains the number of
     * bytes in the |value| field) which specify further match criteria (beyond the service name).
     * The match behavior is specified in details in the NAN spec.
     * Publisher: used if provided.
     * Subscriber: used (if provided) only in ACTIVE sessions.
     * Max length: |NanCapabilities.maxMatchFilterLen|.
     * NAN Spec: matching_filter_tx and Service Descriptor Attribute (SDA) / Matching Filter
     */
    ::android::hardware::hidl_vec<uint8_t> txMatchFilter __attribute__ ((aligned(8)));
    /**
     * Specifies whether or not the discovery session uses the
     * |NanBandSpecificConfig.rssiCloseProximity| value (configured in enable/configure requests) to
     * filter out matched discovered peers.
     * NAN Spec: Service Descriptor Attribute / Service Control / Discovery Range Limited.
     */
    bool useRssiThreshold __attribute__ ((aligned(1)));
    /**
     * Controls whether or not the |IWifiNanIfaceEventCallback.eventPublishTerminated| (for publish
     * discovery sessions) or |IWifiNanIfaceEventCallback.eventSubscribeTerminated| (for subscribe
     * discovery sessions) will be delivered.
     */
    bool disableDiscoveryTerminationIndication __attribute__ ((aligned(1)));
    /**
     * Controls whether or not the |IWifiNanIfaceEventCallback.eventMatchExpired| will be delivered.
     */
    bool disableMatchExpirationIndication __attribute__ ((aligned(1)));
    /**
     * Controls whether or not the |IWifiNanIfaceEventCallback.eventFollowupReceived| will be
     * delivered.
     */
    bool disableFollowupReceivedIndication __attribute__ ((aligned(1)));
    /**
     * Security configuration of data-paths created in the context of this discovery session. Security
     * parameters can be overridden during the actual construction of the data-path - allowing
     * individual data-paths to have unique PMKs or Passphrases.
     */
    ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig securityConfig __attribute__ ((aligned(8)));
    /**
     * Specifies whether or not there is a ranging requirement in this discovery session.
     * Ranging is only performed if all other match criteria with the peer are met. Ranging must
     * be performed if both peers in the discovery session (publisher and subscriber) set this
     * flag to true. Otherwise, if either peer sets this flag to false, ranging must not be performed
     * and must not impact discovery decisions.
     * Note: specifying that ranging is required also implies that this device must automatically
     * accept ranging requests from peers.
     * NAN Spec: Service Discovery Extension Attribute (SDEA) / Control / Ranging Require.
     */
    bool rangingRequired __attribute__ ((aligned(1)));
    /**
     * Interval in msec between two ranging measurements. Only relevant if |rangingRequired| is true.
     * If the Awake DW interval specified either in |discoveryWindowPeriod| or in
     * |NanBandSpecificConfig.discoveryWindowIntervalVal| is larger than the ranging interval then
     * priority is given to Awake DW interval.
     */
    uint32_t rangingIntervalMsec __attribute__ ((aligned(4)));
    /**
     * The type of ranging feedback to be provided by discovery session matches
     * |IWifiNanIfaceEventCallback.eventMatch|. Only relevant if |rangingRequired| is true.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanRangingIndication> configRangingIndications __attribute__ ((aligned(4)));
    /**
     * The ingress and egress distance in cm. If ranging is enabled (|rangingEnabled| is true) then
     * |configRangingIndications| is used to determine whether ingress and/or egress (or neither)
     * are used to determine whether a match has occurred.
     * NAN Spec: Service Discovery Extension Attribute (SDEA) / Ingress & Egress Range Limit
     */
    uint16_t distanceIngressCm __attribute__ ((aligned(2)));
    uint16_t distanceEgressCm __attribute__ ((aligned(2)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, sessionId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, ttlSec) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, discoveryWindowPeriod) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, discoveryCount) == 6, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, serviceName) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, discoveryMatchIndicator) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, serviceSpecificInfo) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, extendedServiceSpecificInfo) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, rxMatchFilter) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, txMatchFilter) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, useRssiThreshold) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, disableDiscoveryTerminationIndication) == 97, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, disableMatchExpirationIndication) == 98, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, disableFollowupReceivedIndication) == 99, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, securityConfig) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, rangingRequired) == 160, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, rangingIntervalMsec) == 164, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, configRangingIndications) == 168, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, distanceIngressCm) == 172, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig, distanceEgressCm) == 174, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig) == 176, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig) == 8, "wrong alignment");

/**
 * Publish request: specifies a publish discovery operation.
 */
struct NanPublishRequest final {
    /**
     * Common configuration of discovery sessions.
     */
    ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig baseConfigs __attribute__ ((aligned(8)));
    /**
     * The type of the publish discovery session.
     */
    ::android::hardware::wifi::V1_0::NanPublishType publishType __attribute__ ((aligned(4)));
    /**
     * For publishType of |NanPublishType.SOLICITED| or |NanPublishType.UNSOLICITED_SOLICITED|
     * specifies the type of transmission used for responding to the probing subscribe discovery
     * peer.
     */
    ::android::hardware::wifi::V1_0::NanTxType txType __attribute__ ((aligned(4)));
    /**
     * Specifies whether data-path requests |IWifiNanIfaceEventCallback.eventDataPathRequest| (in
     * the context of this discovery session) are automatically accepted (if true) - in which case
     * the Responder must not call the |IWifiNanIface.respondToDataPathIndicationRequest| method and
     * the device must automatically accept the data-path request and complete the negotiation.
     */
    bool autoAcceptDataPathRequests __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanPublishRequest, baseConfigs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanPublishRequest, publishType) == 176, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanPublishRequest, txType) == 180, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanPublishRequest, autoAcceptDataPathRequests) == 184, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanPublishRequest) == 192, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanPublishRequest) == 8, "wrong alignment");

/**
 * Subscribe request: specifies a subscribe discovery operation.
 */
struct NanSubscribeRequest final {
    /**
     * Common configuration of discovery sessions.
     */
    ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig baseConfigs __attribute__ ((aligned(8)));
    /**
     * The type of the subscribe discovery session.
     */
    ::android::hardware::wifi::V1_0::NanSubscribeType subscribeType __attribute__ ((aligned(4)));
    /**
     * For |NanSubscribeType.ACTIVE| subscribe discovery sessions specify how the Service Response
     * Filter (SRF) attribute is populated. Relevant only if |shouldUseSrf| is set to true.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Response Filter / SRF Control / SRF Type
     */
    ::android::hardware::wifi::V1_0::NanSrfType srfType __attribute__ ((aligned(4)));
    /**
     * Configure whether inclusion of an address in |intfAddr| indicates that those devices should
     * respond or the reverse. Relevant only if |shouldUseSrf| is set to true and |srfType| is set to
     * |NanSrfType.PARTIAL_MAC_ADDR|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Response Filter / SRF Control / Include
     */
    bool srfRespondIfInAddressSet __attribute__ ((aligned(1)));
    /**
     * Control whether the Service Response Filter (SRF) is used.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Control /
     *           Service Response Filter Present
     */
    bool shouldUseSrf __attribute__ ((aligned(1)));
    /**
     * Control whether the presence of |NanDiscoveryCommonConfig.serviceSpecificInfo| data is needed
     * in the publisher in order to trigger service discovery, i.e. a
     * |IWifiNanIfaceEventCallback.eventMatch|. The test is for presence of data - not for the
     * specific contents of the data.
     */
    bool isSsiRequiredForMatch __attribute__ ((aligned(1)));
    /**
     * NAN Interface Addresses constituting the Service Response Filter (SRF).
     * Max length (number of addresses): |NanCapabilities.maxSubscribeInterfaceAddresses|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Response Filter / Address Set
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 6>> intfAddr __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, baseConfigs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, subscribeType) == 176, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, srfType) == 180, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, srfRespondIfInAddressSet) == 184, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, shouldUseSrf) == 185, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, isSsiRequiredForMatch) == 186, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanSubscribeRequest, intfAddr) == 192, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanSubscribeRequest) == 208, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanSubscribeRequest) == 8, "wrong alignment");

/**
 * Transmit follow up message request.
 */
struct NanTransmitFollowupRequest final {
    /**
     * ID of an active publish or subscribe discovery session. Follow-up message is transmitted in the
     * context of the discovery session.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    uint8_t discoverySessionId __attribute__ ((aligned(1)));
    /**
     * ID of the peer. Obtained as part of an earlier |IWifiNanIfaceEventCallback.eventMatch| or
     * |IWifiNanIfaceEventCallback.eventFollowupReceived|.
     */
    uint32_t peerId __attribute__ ((aligned(4)));
    /**
     * MAC address of the peer. Obtained as part of an earlier |IWifiNanIfaceEventCallback.eventMatch|
     * or |IWifiNanIfaceEventCallback.eventFollowupReceived|.
     */
    ::android::hardware::hidl_array<uint8_t, 6> addr __attribute__ ((aligned(1)));
    /**
     * Should the follow-up message be transmitted with a high priority.
     */
    bool isHighPriority __attribute__ ((aligned(1)));
    /**
     * Should the follow-up message be transmitted in a discovery window (true) or a further
     * availability window (false).
     */
    bool shouldUseDiscoveryWindow __attribute__ ((aligned(1)));
    /**
     * Arbitrary information communicated to the peer - there is no semantic meaning to these
     * bytes. They are passed-through from sender to receiver as-is with no parsing.
     * Max length: |NanCapabilities.maxServiceSpecificInfoLen|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> serviceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * Arbitrary information communicated in discovery packets - there is no semantic meaning to these
     * bytes. They are passed-through from publisher to subscriber as-is with no parsing.
     * Max length: |NanCapabilities.maxExtendedServiceSpecificInfoLen|.
     * Spec: Service Descriptor Extension Attribute (SDEA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> extendedServiceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * Disable |IWifiNanIfaceEventCallback.eventTransmitFollowup| - i.e. do not get indication on
     * whether the follow-up was transmitted and received successfully.
     */
    bool disableFollowupResultIndication __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, discoverySessionId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, peerId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, addr) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, isHighPriority) == 14, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, shouldUseDiscoveryWindow) == 15, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, serviceSpecificInfo) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, extendedServiceSpecificInfo) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest, disableFollowupResultIndication) == 48, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest) == 56, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanTransmitFollowupRequest) == 8, "wrong alignment");

/**
 *  Data Path Initiator requesting a data-path.
 */
struct NanInitiateDataPathRequest final {
    /**
     * ID of the peer. Obtained as part of an earlier |IWifiNanIfaceEventCallback.eventMatch| or
     * |IWifiNanIfaceEventCallback.eventFollowupReceived|.
     */
    uint32_t peerId __attribute__ ((aligned(4)));
    /**
     * NAN management interface MAC address of the peer. Obtained as part of an earlier
     * |IWifiNanIfaceEventCallback.eventMatch| or |IWifiNanIfaceEventCallback.eventFollowupReceived|.
     */
    ::android::hardware::hidl_array<uint8_t, 6> peerDiscMacAddr __attribute__ ((aligned(1)));
    /**
     * Config flag for channel request.
     */
    ::android::hardware::wifi::V1_0::NanDataPathChannelCfg channelRequestType __attribute__ ((aligned(4)));
    /**
     * Channel frequency in MHz to start data-path. Not relevant if |channelRequestType| is
     * |NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED|.
     */
    uint32_t channel __attribute__ ((aligned(4)));
    /**
     * NAN data interface name on which this data-path session is to be initiated.
     * This must be an interface created using |IWifiNanIface.createDataInterfaceRequest|.
     */
    ::android::hardware::hidl_string ifaceName __attribute__ ((aligned(8)));
    /**
     * Security configuration of the requested data-path.
     */
    ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig securityConfig __attribute__ ((aligned(8)));
    /**
     * Arbitrary information communicated to the peer as part of the data-path setup process - there
     * is no semantic meaning to these bytes. They are passed-through from sender to receiver as-is
     * with no parsing.
     * Max length: |NanCapabilities.maxAppInfoLen|.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Specific Info
     */
    ::android::hardware::hidl_vec<uint8_t> appInfo __attribute__ ((aligned(8)));
    /**
     * A service name to be used with |passphrase| to construct a Pairwise Master Key (PMK) for the
     * data-path. Only relevant when a data-path is requested which is not associated with a NAN
     * discovery session - e.g. using out-of-band discovery.
     * Constraints: same as |NanDiscoveryCommonConfig.serviceName|
     * NAN Spec: Appendix: Mapping pass-phrase to PMK for NCS-SK Cipher Suites
     */
    ::android::hardware::hidl_vec<uint8_t> serviceNameOutOfBand __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, peerId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, peerDiscMacAddr) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, channelRequestType) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, channel) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, ifaceName) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, securityConfig) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, appInfo) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest, serviceNameOutOfBand) == 112, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest) == 128, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanInitiateDataPathRequest) == 8, "wrong alignment");

/**
 * Response to a data-path request from a peer.
 */
struct NanRespondToDataPathIndicationRequest final {
    /**
     * Accept (true) or reject (false) the request.
     * NAN Spec: Data Path Attributes / NDP Attribute / Type and Status
     */
    bool acceptRequest __attribute__ ((aligned(1)));
    /**
     * ID of the data-path (NDP) for which we're responding - obtained as part of the request in
     * |IWifiNanIfaceEventCallback.eventDataPathRequest|.
     */
    uint32_t ndpInstanceId __attribute__ ((aligned(4)));
    /**
     * NAN data interface name on which this data-path session is to be started.
     * This must be an interface created using |IWifiNanIface.createDataInterfaceRequest|.
     */
    ::android::hardware::hidl_string ifaceName __attribute__ ((aligned(8)));
    /**
     * Security configuration of the requested data-path.
     */
    ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig securityConfig __attribute__ ((aligned(8)));
    /**
     * Arbitrary information communicated to the peer as part of the data-path setup process - there
     * is no semantic meaning to these bytes. They are passed-through from sender to receiver as-is
     * with no parsing.
     * Max length: |NanCapabilities.maxAppInfoLen|.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Specific Info
     */
    ::android::hardware::hidl_vec<uint8_t> appInfo __attribute__ ((aligned(8)));
    /**
     * A service name to be used with |passphrase| to construct a Pairwise Master Key (PMK) for the
     * data-path. Only relevant when a data-path is requested which is not associated with a NAN
     * discovery session - e.g. using out-of-band discovery.
     * Constraints: same as |NanDiscoveryCommonConfig.serviceName|
     * NAN Spec: Appendix: Mapping pass-phrase to PMK for NCS-SK Cipher Suites
     */
    ::android::hardware::hidl_vec<uint8_t> serviceNameOutOfBand __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest, acceptRequest) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest, ndpInstanceId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest, ifaceName) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest, securityConfig) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest, appInfo) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest, serviceNameOutOfBand) == 96, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest) == 112, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest) == 8, "wrong alignment");

/**
 * NDP Capabilities response.
 */
struct NanCapabilities final {
    /**
     * Maximum number of clusters which the device can join concurrently.
     */
    uint32_t maxConcurrentClusters __attribute__ ((aligned(4)));
    /**
     * Maximum number of concurrent publish discovery sessions.
     */
    uint32_t maxPublishes __attribute__ ((aligned(4)));
    /**
     * Maximum number of concurrent subscribe discovery sessions.
     */
    uint32_t maxSubscribes __attribute__ ((aligned(4)));
    /**
     * Maximum length (in bytes) of service name.
     */
    uint32_t maxServiceNameLen __attribute__ ((aligned(4)));
    /**
     * Maximum length (in bytes) of individual match filters.
     */
    uint32_t maxMatchFilterLen __attribute__ ((aligned(4)));
    /**
     * Maximum length (in bytes) of aggregate match filters across all active sessions.
     */
    uint32_t maxTotalMatchFilterLen __attribute__ ((aligned(4)));
    /**
     * Maximum length (in bytes) of the service specific info field.
     */
    uint32_t maxServiceSpecificInfoLen __attribute__ ((aligned(4)));
    /**
     * Maximum length (in bytes) of the extended service specific info field.
     */
    uint32_t maxExtendedServiceSpecificInfoLen __attribute__ ((aligned(4)));
    /**
     * Maximum number of data interfaces (NDI) which can be created concurrently on the device.
     */
    uint32_t maxNdiInterfaces __attribute__ ((aligned(4)));
    /**
     * Maximum number of data paths (NDP) which can be created concurrently on the device, across all
     * data interfaces (NDI).
     */
    uint32_t maxNdpSessions __attribute__ ((aligned(4)));
    /**
     * Maximum length (in bytes) of application info field (used in data-path negotiations).
     */
    uint32_t maxAppInfoLen __attribute__ ((aligned(4)));
    /**
     * Maximum number of transmitted followup messages which can be queued by the firmware.
     */
    uint32_t maxQueuedTransmitFollowupMsgs __attribute__ ((aligned(4)));
    /**
     * Maximum number MAC interface addresses which can be specified to a subscribe discovery session.
     */
    uint32_t maxSubscribeInterfaceAddresses __attribute__ ((aligned(4)));
    /**
     * The set of supported Cipher suites. The |NanCipherSuiteType| bit fields are used.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanCipherSuiteType> supportedCipherSuites __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxConcurrentClusters) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxPublishes) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxSubscribes) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxServiceNameLen) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxMatchFilterLen) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxTotalMatchFilterLen) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxServiceSpecificInfoLen) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxExtendedServiceSpecificInfoLen) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxNdiInterfaces) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxNdpSessions) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxAppInfoLen) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxQueuedTransmitFollowupMsgs) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, maxSubscribeInterfaceAddresses) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanCapabilities, supportedCipherSuites) == 52, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanCapabilities) == 56, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanCapabilities) == 4, "wrong alignment");

/**
 * Match indication structure
 */
struct NanMatchInd final {
    /**
     * Publish or subscribe discovery session ID of an existing discovery session.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    uint8_t discoverySessionId __attribute__ ((aligned(1)));
    /**
     * A unique ID of the peer. Can be subsequently used in |IWifiNanIface.transmitFollowupRequest| or
     * to set up a data-path.
     */
    uint32_t peerId __attribute__ ((aligned(4)));
    /**
     * The NAN Discovery (management) MAC address of the peer.
     */
    ::android::hardware::hidl_array<uint8_t, 6> addr __attribute__ ((aligned(1)));
    /**
     * The arbitrary information contained in the |NanDiscoveryCommonConfig.serviceSpecificInfo| of
     * the peer's discovery session configuration.
     * Max length: |NanCapabilities.maxServiceSpecificInfoLen|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> serviceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * Arbitrary information communicated in discovery packets - there is no semantic meaning to these
     * bytes. They are passed-through from publisher to subscriber as-is with no parsing.
     * Max length: |NanCapabilities.maxExtendedServiceSpecificInfoLen|.
     * Spec: Service Descriptor Extension Attribute (SDEA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> extendedServiceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * The match filter from the discovery packet (publish or subscribe) which caused service
     * discovery. Matches the |NanDiscoveryCommonConfig.txMatchFilter| of the peer's Unsolicited
     * publish message or of the local device's Active subscribe message.
     * Max length: |NanCapabilities.maxMatchFilterLen|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Matching Filter
     */
    ::android::hardware::hidl_vec<uint8_t> matchFilter __attribute__ ((aligned(8)));
    /**
     * Indicates the type of discovery: true if match occurred on a Beacon frame, false if the match
     * occurred on a Service Discovery Frames (SDF).
     */
    bool matchOccuredInBeaconFlag __attribute__ ((aligned(1)));
    /**
     * Flag to indicate firmware is out of resource and that it can no longer track this Service Name.
     * Indicates that while |IWifiNanIfaceEventCallback.eventMatch| will be received, the
     * |NanDiscoveryCommonConfig.discoveryMatchIndicator| configuration will not be honored.
     */
    bool outOfResourceFlag __attribute__ ((aligned(1)));
    /**
     * If RSSI filtering was enabled using |NanDiscoveryCommonConfig.useRssiThreshold| in discovery
     * session setup then this field contains the received RSSI value. It will contain 0 if RSSI
     * filtering was not enabled.
     * RSSI values are returned without sign, e.g. -70dBm will be returned as 70.
     */
    uint8_t rssiValue __attribute__ ((aligned(1)));
    /**
     * Cipher type for data-paths constructed in the context of this discovery session. Valid if
     * |peerRequiresSecurityEnabledInNdp| is true.
     */
    ::android::hardware::wifi::V1_0::NanCipherSuiteType peerCipherType __attribute__ ((aligned(4)));
    /**
     * Indicates whether or not the peer requires security enabled in any data-path (NDP) constructed
     * in the context of this discovery session. The |cipherType| specifies the cipher type for such
     * data-paths.
     * NAN Spec: Service Discovery Extension Attribute (SDEA) / Control / Security Required
     */
    bool peerRequiresSecurityEnabledInNdp __attribute__ ((aligned(1)));
    /**
     * Indicates whether or not the peer requires (and hence allows) ranging in the context of this
     * discovery session.
     * Note that ranging is only performed if all other match criteria with the peer are met.
     * NAN Spec: Service Discovery Extension Attribute (SDEA) / Control / Ranging Require.
     */
    bool peerRequiresRanging __attribute__ ((aligned(1)));
    /**
     * Ranging indication supersedes the NanMatchAlg specification.
     * Ex: If NanMatchAlg is MATCH_ONCE, but ranging indications is continuous then continuous
     * match notifications will be received (with ranging information).
     * Ranging indication data is provided if Ranging required is enabled in the discovery
     * specification and:
     *   1) continuous ranging specified.
     *   2) ingress/egress specified and:
     *       - notify once for ingress >= ingress_distance and egress <= egress_distance,
     *       - same for ingress_egress_both
     * If the Awake DW intervals are larger than the ranging intervals then priority is given to the
     * device DW intervals.
     *
     * If ranging was required and executed contains the distance to the peer in CM. The
     * |rangingIndicationType| field specifies the event which triggered ranging.
     */
    uint32_t rangingMeasurementInCm __attribute__ ((aligned(4)));
    /**
     * The ranging event(s) which triggered the ranging. E.g. can indicate that continuous ranging was
     * requested, or else that an ingress event occurred.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanRangingIndication> rangingIndicationType __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, discoverySessionId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, peerId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, addr) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, serviceSpecificInfo) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, extendedServiceSpecificInfo) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, matchFilter) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, matchOccuredInBeaconFlag) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, outOfResourceFlag) == 65, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, rssiValue) == 66, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, peerCipherType) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, peerRequiresSecurityEnabledInNdp) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, peerRequiresRanging) == 73, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, rangingMeasurementInCm) == 76, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanMatchInd, rangingIndicationType) == 80, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanMatchInd) == 88, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanMatchInd) == 8, "wrong alignment");

/**
 * Followup message received from peer indication structure.
 */
struct NanFollowupReceivedInd final {
    /**
     * Discovery session (publish or subscribe) ID of a previously created discovery session. The
     * message is received in the context of this discovery session.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    uint8_t discoverySessionId __attribute__ ((aligned(1)));
    /**
     * A unique ID of the peer. Can be subsequently used in |IWifiNanIface.transmitFollowupRequest| or
     * to set up a data-path.
     */
    uint32_t peerId __attribute__ ((aligned(4)));
    /**
     * The NAN Discovery (management) MAC address of the peer.
     */
    ::android::hardware::hidl_array<uint8_t, 6> addr __attribute__ ((aligned(1)));
    /**
     * Indicates whether received in a further availability window (FAW) if true, or in a discovery
     * window (DW) if false.
     */
    bool receivedInFaw __attribute__ ((aligned(1)));
    /**
     * Received message from the peer - there is no semantic meaning to these bytes. They are
     * passed-through from sender to receiver as-is with no parsing.
     * Max length: |NanCapabilities.maxServiceSpecificInfoLen|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> serviceSpecificInfo __attribute__ ((aligned(8)));
    /**
     * Arbitrary information communicated in discovery packets - there is no semantic meaning to these
     * bytes. They are passed-through from publisher to subscriber as-is with no parsing.
     * Max length: |NanCapabilities.maxExtendedServiceSpecificInfoLen|.
     * Spec: Service Descriptor Extension Attribute (SDEA) / Service Info
     */
    ::android::hardware::hidl_vec<uint8_t> extendedServiceSpecificInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd, discoverySessionId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd, peerId) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd, addr) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd, receivedInFaw) == 14, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd, serviceSpecificInfo) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd, extendedServiceSpecificInfo) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd) == 48, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanFollowupReceivedInd) == 8, "wrong alignment");

/**
 * Event types for a cluster event indication.
 */
enum class NanClusterEventType : uint32_t {
    /**
     * Management/discovery interface MAC address has changed (e.g. due to randomization or at
     * startup).
     */
    DISCOVERY_MAC_ADDRESS_CHANGED = 0u,
    /**
     * A new cluster has been formed by this device.
     */
    STARTED_CLUSTER = 1u /* ::android::hardware::wifi::V1_0::NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED implicitly + 1 */,
    /**
     * This device has joined an existing cluster.
     */
    JOINED_CLUSTER = 2u /* ::android::hardware::wifi::V1_0::NanClusterEventType.STARTED_CLUSTER implicitly + 1 */,
};

/**
 * Cluster event indication structure: triggered on events impacting how this device is
 * visible to peers - cluster forming, joining a new cluster, or changing of the MAC address.
 */
struct NanClusterEventInd final {
    /**
     * Event type causing the cluster event indication to be triggered.
     */
    ::android::hardware::wifi::V1_0::NanClusterEventType eventType __attribute__ ((aligned(4)));
    /**
     * MAC Address associated with the corresponding event.
     */
    ::android::hardware::hidl_array<uint8_t, 6> addr __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanClusterEventInd, eventType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanClusterEventInd, addr) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanClusterEventInd) == 12, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanClusterEventInd) == 4, "wrong alignment");

/**
 * NAN Data path request Indication Message structure.
 * Event indication received by an intended Responder when a Nan Data request initiated by an
 * Initiator.
 */
struct NanDataPathRequestInd final {
    /**
     * ID of an active publish or subscribe discovery session - the data-path request is in the
     * context of this discovery session.
     * NAN Spec: Data Path Attributes / NDP Attribute / Publish ID
     */
    uint8_t discoverySessionId __attribute__ ((aligned(1)));
    /**
     * MAC address of the Initiator peer. This is the MAC address of the peer's management/discovery
     * NAN interface.
     */
    ::android::hardware::hidl_array<uint8_t, 6> peerDiscMacAddr __attribute__ ((aligned(1)));
    /**
     * ID of the data-path - used to identify the data-path in further negotiation/APIs.
     */
    uint32_t ndpInstanceId __attribute__ ((aligned(4)));
    /**
     * Specifies whether or not security is required by the peer for the data-path being created.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Control / Security Present
     */
    bool securityRequired __attribute__ ((aligned(1)));
    /**
     * Arbitrary information communicated from the peer as part of the data-path setup process - there
     * is no semantic meaning to these bytes. They are passed-through from sender to receiver as-is
     * with no parsing.
     * Max length: |NanCapabilities.maxAppInfoLen|.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Specific Info
     */
    ::android::hardware::hidl_vec<uint8_t> appInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathRequestInd, discoverySessionId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathRequestInd, peerDiscMacAddr) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathRequestInd, ndpInstanceId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathRequestInd, securityRequired) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathRequestInd, appInfo) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanDataPathRequestInd) == 32, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanDataPathRequestInd) == 8, "wrong alignment");

/**
 * NAN Data path confirmation Indication structure.
 * Event indication is received on both initiator and responder side when negotiation for a
 * data-path finish: on success or failure.
 */
struct NanDataPathConfirmInd final {
    /**
     * ID of the data-path.
     */
    uint32_t ndpInstanceId __attribute__ ((aligned(4)));
    /**
     * Indicates whether the data-path setup succeeded (true) or failed (false).
     */
    bool dataPathSetupSuccess __attribute__ ((aligned(1)));
    /**
     * MAC address of the peer's data-interface (not it's management/discovery interface).
     */
    ::android::hardware::hidl_array<uint8_t, 6> peerNdiMacAddr __attribute__ ((aligned(1)));
    /**
     * Arbitrary information communicated from the peer as part of the data-path setup process - there
     * is no semantic meaning to these bytes. They are passed-through from sender to receiver as-is
     * with no parsing.
     * Max length: |NanCapabilities.maxAppInfoLen|.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Specific Info
     */
    ::android::hardware::hidl_vec<uint8_t> appInfo __attribute__ ((aligned(8)));
    /**
     * Failure reason if |dataPathSetupSuccess| is false.
     */
    ::android::hardware::wifi::V1_0::WifiNanStatus status __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd, ndpInstanceId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd, dataPathSetupSuccess) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd, peerNdiMacAddr) == 5, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd, appInfo) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd, status) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd) == 56, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::NanDataPathConfirmInd) == 8, "wrong alignment");

/**
 * RTT specific types.
 * TODO(b/32159498): Move to a separate rtt_types.hal.
 *
 *
 * Ranging status.
 */
enum class RttStatus : uint32_t {
    SUCCESS = 0u,
    /**
     * General failure status
     */
    FAILURE = 1u,
    /**
     * Target STA does not respond to request
     */
    FAIL_NO_RSP = 2u,
    /**
     * Request rejected. Applies to 2-sided RTT only
     */
    FAIL_REJECTED = 3u,
    FAIL_NOT_SCHEDULED_YET = 4u,
    /**
     * Timing measurement times out
     */
    FAIL_TM_TIMEOUT = 5u,
    /**
     * Target on different channel, cannot range
     */
    FAIL_AP_ON_DIFF_CHANNEL = 6u,
    /**
     * Ranging not supported
     */
    FAIL_NO_CAPABILITY = 7u,
    /**
     * Request aborted for unknown reason
     */
    ABORTED = 8u,
    /**
     * Invalid T1-T4 timestamp
     */
    FAIL_INVALID_TS = 9u,
    /**
     * 11mc protocol failed
     */
    FAIL_PROTOCOL = 10u,
    /**
     * Request could not be scheduled
     */
    FAIL_SCHEDULE = 11u,
    /**
     * Responder cannot collaborate at time of request
     */
    FAIL_BUSY_TRY_LATER = 12u,
    /**
     * Bad request args
     */
    INVALID_REQ = 13u,
    /**
     * WiFi not enabled.
     */
    NO_WIFI = 14u,
    /**
     * Responder overrides param info, cannot range with new params
     */
    FAIL_FTM_PARAM_OVERRIDE = 15u,
};

/**
 * RTT peer types.
 */
enum class RttPeerType : uint32_t {
    AP = 1u /* 0x1 */,
    STA = 2u /* 0x2 */,
    P2P_GO = 3u /* 0x3 */,
    P2P_CLIENT = 4u /* 0x4 */,
    NAN = 5u /* 0x5 */,
};

/**
 * RTT Measurement Bandwidth.
 */
enum class RttBw : uint32_t {
    BW_5MHZ = 1u /* 0x01 */,
    BW_10MHZ = 2u /* 0x02 */,
    BW_20MHZ = 4u /* 0x04 */,
    BW_40MHZ = 8u /* 0x08 */,
    BW_80MHZ = 16u /* 0x10 */,
    BW_160MHZ = 32u /* 0x20 */,
};

/**
 * RTT Measurement Preamble.
 */
enum class RttPreamble : uint32_t {
    LEGACY = 1u /* 0x1 */,
    HT = 2u /* 0x2 */,
    VHT = 4u /* 0x4 */,
};

/**
 * RTT Types.
 */
enum class RttType : uint32_t {
    ONE_SIDED = 1u /* 0x1 */,
    TWO_SIDED = 2u /* 0x2 */,
};

/**
 * RTT configuration.
 */
struct RttConfig final {
    /**
     * Peer device mac address.
     */
    ::android::hardware::hidl_array<uint8_t, 6> addr __attribute__ ((aligned(1)));
    /**
     * 1-sided or 2-sided RTT.
     */
    ::android::hardware::wifi::V1_0::RttType type __attribute__ ((aligned(4)));
    /**
     * Optional - peer device hint (STA, P2P, AP).
     */
    ::android::hardware::wifi::V1_0::RttPeerType peer __attribute__ ((aligned(4)));
    /**
     * Required for STA-AP mode, optional for P2P, NBD etc.
     */
    ::android::hardware::wifi::V1_0::WifiChannelInfo channel __attribute__ ((aligned(4)));
    /**
     * Time interval between bursts (units: 100 ms).
     * Applies to 1-sided and 2-sided RTT multi-burst requests.
     * Range: 0-31, 0: no preference by initiator (2-sided RTT).
     */
    uint32_t burstPeriod __attribute__ ((aligned(4)));
    /**
     * Total number of RTT bursts to be executed. It will be
     * specified in the same way as the parameter "Number of
     * Burst Exponent" found in the FTM frame format. It
     * applies to both: 1-sided RTT and 2-sided RTT. Valid
     * values are 0 to 15 as defined in 802.11mc std.
     * 0 means single shot
     * The implication of this parameter on the maximum
     * number of RTT results is the following:
     * for 1-sided RTT: max num of RTT results = (2^num_burst)*(num_frames_per_burst)
     * for 2-sided RTT: max num of RTT results = (2^num_burst)*(num_frames_per_burst - 1)
     */
    uint32_t numBurst __attribute__ ((aligned(4)));
    /**
     * Num of frames per burst.
     * Minimum value = 1, Maximum value = 31
     * For 2-sided this equals the number of FTM frames
     * to be attempted in a single burst. This also
     * equals the number of FTM frames that the
     * initiator will request that the responder send
     * in a single frame.
     */
    uint32_t numFramesPerBurst __attribute__ ((aligned(4)));
    /**
     * Number of retries for a failed RTT frame.
     * Applies to 1-sided RTT only. Minimum value = 0, Maximum value = 3
     */
    uint32_t numRetriesPerRttFrame __attribute__ ((aligned(4)));
    /**
     * Following fields are only valid for 2-side RTT.
     *
     *
     * Maximum number of retries that the initiator can
     * retry an FTMR frame.
     * Minimum value = 0, Maximum value = 3
     */
    uint32_t numRetriesPerFtmr __attribute__ ((aligned(4)));
    /**
     * Whether to request location civic info or not.
     */
    bool mustRequestLci __attribute__ ((aligned(1)));
    /**
     * Whether to request location civic records or not.
     */
    bool mustRequestLcr __attribute__ ((aligned(1)));
    /**
     * Applies to 1-sided and 2-sided RTT. Valid values will
     * be 2-11 and 15 as specified by the 802.11mc std for
     * the FTM parameter burst duration. In a multi-burst
     * request, if responder overrides with larger value,
     * the initiator will return failure. In a single-burst
     * request if responder overrides with larger value,
     * the initiator will sent TMR_STOP to terminate RTT
     * at the end of the burst_duration it requested.
     */
    uint32_t burstDuration __attribute__ ((aligned(4)));
    /**
     * RTT preamble to be used in the RTT frames.
     */
    ::android::hardware::wifi::V1_0::RttPreamble preamble __attribute__ ((aligned(4)));
    /**
     * RTT BW to be used in the RTT frames.
     */
    ::android::hardware::wifi::V1_0::RttBw bw __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, addr) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, type) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, peer) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, channel) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, burstPeriod) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, numBurst) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, numFramesPerBurst) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, numRetriesPerRttFrame) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, numRetriesPerFtmr) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, mustRequestLci) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, mustRequestLcr) == 53, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, burstDuration) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, preamble) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttConfig, bw) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::RttConfig) == 68, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::RttConfig) == 4, "wrong alignment");

/**
 * RTT results.
 */
struct RttResult final {
    /**
     * Peer device mac address.
     */
    ::android::hardware::hidl_array<uint8_t, 6> addr __attribute__ ((aligned(1)));
    /**
     * Burst number in a multi-burst request.
     */
    uint32_t burstNum __attribute__ ((aligned(4)));
    /**
     * Total RTT measurement frames attempted.
     */
    uint32_t measurementNumber __attribute__ ((aligned(4)));
    /**
     * Total successful RTT measurement frames.
     */
    uint32_t successNumber __attribute__ ((aligned(4)));
    /**
     * Maximum number of "FTM frames per burst" supported by
     * the responder STA. Applies to 2-sided RTT only.
     * If reponder overrides with larger value:
     * - for single-burst request initiator will truncate the
     * larger value and send a TMR_STOP after receiving as
     * many frames as originally requested.
     * - for multi-burst request, initiator will return
     * failure right away.
     */
    uint8_t numberPerBurstPeer __attribute__ ((aligned(1)));
    /**
     * Ranging status.
     */
    ::android::hardware::wifi::V1_0::RttStatus status __attribute__ ((aligned(4)));
    /**
     * When status == RTT_STATUS_FAIL_BUSY_TRY_LATER,
     * this will be the time provided by the responder as to
     * when the request can be tried again. Applies to 2-sided
     * RTT only. In sec, 1-31sec.
     */
    uint8_t retryAfterDuration __attribute__ ((aligned(1)));
    /**
     * RTT type.
     */
    ::android::hardware::wifi::V1_0::RttType type __attribute__ ((aligned(4)));
    /**
     * Average rssi in 0.5 dB steps e.g. 143 implies -71.5 dB.
     */
    int32_t rssi __attribute__ ((aligned(4)));
    /**
     * Rssi spread in 0.5 dB steps e.g. 5 implies 2.5 dB spread (optional).
     */
    int32_t rssiSpread __attribute__ ((aligned(4)));
    /**
     * 1-sided RTT: TX rate of RTT frame.
     * 2-sided RTT: TX rate of initiator's Ack in response to FTM frame.
     */
    ::android::hardware::wifi::V1_0::WifiRateInfo txRate __attribute__ ((aligned(4)));
    /**
     * 1-sided RTT: TX rate of Ack from other side.
     * 2-sided RTT: TX rate of FTM frame coming from responder.
     */
    ::android::hardware::wifi::V1_0::WifiRateInfo rxRate __attribute__ ((aligned(4)));
    /**
     * Round trip time in picoseconds
     */
    uint64_t rtt __attribute__ ((aligned(8)));
    /**
     * Rtt standard deviation in picoseconds.
     */
    uint64_t rttSd __attribute__ ((aligned(8)));
    /**
     * Difference between max and min rtt times recorded in picoseconds.
     */
    uint64_t rttSpread __attribute__ ((aligned(8)));
    /**
     * Distance in mm (optional).
     */
    int32_t distanceInMm __attribute__ ((aligned(4)));
    /**
     * Standard deviation in mm (optional).
     */
    int32_t distanceSdInMm __attribute__ ((aligned(4)));
    /**
     * Difference between max and min distance recorded in mm (optional).
     */
    int32_t distanceSpreadInMm __attribute__ ((aligned(4)));
    /**
     * Time of the measurement (in microseconds since boot).
     */
    uint64_t timeStampInUs __attribute__ ((aligned(8)));
    /**
     * in ms, actual time taken by the FW to finish one burst
     * measurement. Applies to 1-sided and 2-sided RTT.
     */
    uint32_t burstDurationInMs __attribute__ ((aligned(4)));
    /**
     * Number of bursts allowed by the responder. Applies
     * to 2-sided RTT only.
     */
    uint32_t negotiatedBurstNum __attribute__ ((aligned(4)));
    /**
     * for 11mc only.
     */
    ::android::hardware::wifi::V1_0::WifiInformationElement lci __attribute__ ((aligned(8)));
    /**
     * for 11mc only.
     */
    ::android::hardware::wifi::V1_0::WifiInformationElement lcr __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, addr) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, burstNum) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, measurementNumber) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, successNumber) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, numberPerBurstPeer) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, status) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, retryAfterDuration) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, type) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, rssi) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, rssiSpread) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, txRate) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, rxRate) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, rtt) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, rttSd) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, rttSpread) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, distanceInMm) == 112, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, distanceSdInMm) == 116, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, distanceSpreadInMm) == 120, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, timeStampInUs) == 128, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, burstDurationInMs) == 136, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, negotiatedBurstNum) == 140, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, lci) == 144, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResult, lcr) == 168, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::RttResult) == 192, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::RttResult) == 8, "wrong alignment");

/**
 * RTT Capabilities.
 */
struct RttCapabilities final {
    /**
     * if 1-sided rtt data collection is supported.
     */
    bool rttOneSidedSupported __attribute__ ((aligned(1)));
    /**
     * if ftm rtt data collection is supported.
     */
    bool rttFtmSupported __attribute__ ((aligned(1)));
    /**
     * if initiator supports LCI request. Applies to 2-sided RTT.
     */
    bool lciSupported __attribute__ ((aligned(1)));
    /**
     * if initiator supports LCR request. Applies to 2-sided RTT.
     */
    bool lcrSupported __attribute__ ((aligned(1)));
    /**
     * if 11mc responder mode is supported.
     */
    bool responderSupported __attribute__ ((aligned(1)));
    /**
     * Bit mask indicates what preamble is supported by initiator.
     * Combination of |RttPreamble| values.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttPreamble> preambleSupport __attribute__ ((aligned(4)));
    /**
     * Bit mask indicates what BW is supported by initiator.
     * Combination of |RttBw| values.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttBw> bwSupport __attribute__ ((aligned(4)));
    /**
     * Draft 11mc spec version supported by chip.
     * For instance, version 4.0 must be 40 and version 4.3 must be 43 etc.
     */
    uint8_t mcVersion __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, rttOneSidedSupported) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, rttFtmSupported) == 1, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, lciSupported) == 2, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, lcrSupported) == 3, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, responderSupported) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, preambleSupport) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, bwSupport) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttCapabilities, mcVersion) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::RttCapabilities) == 20, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::RttCapabilities) == 4, "wrong alignment");

/**
 * Structs for setting LCI/LCR information to be provided to a requestor.
 */
enum class RttMotionPattern : uint32_t {
    /**
     * Not expected to change location.
     */
    NOT_EXPECTED = 0u,
    /**
     * Expected to change location.
     */
    EXPECTED = 1u,
    /**
     * Movement pattern unknown.
     */
    UNKNOWN = 2u,
};

/**
 * Movement pattern unknown.
 */
struct RttLciInformation final {
    /**
     * latitude in degrees * 2^25 , 2's complement.
     */
    int64_t latitude __attribute__ ((aligned(8)));
    /**
     * longitude in degrees * 2^25 , 2's complement.
     */
    int64_t longitude __attribute__ ((aligned(8)));
    /**
     * Altitude in units of 1/256 m.
     */
    int32_t altitude __attribute__ ((aligned(4)));
    /**
     * As defined in Section 2.3.2 of IETF RFC 6225.
     */
    uint8_t latitudeUnc __attribute__ ((aligned(1)));
    /**
     * As defined in Section 2.3.2 of IETF RFC 6225.
     */
    uint8_t longitudeUnc __attribute__ ((aligned(1)));
    /**
     * As defined in Section 2.4.5 from IETF RFC 6225.
     */
    uint8_t altitudeUnc __attribute__ ((aligned(1)));
    /**
     * Following element for configuring the Z subelement.
     *
     *
     * Motion pattern type.
     */
    ::android::hardware::wifi::V1_0::RttMotionPattern motionPattern __attribute__ ((aligned(4)));
    /**
     * Floor in units of 1/16th of floor. 0x80000000 if unknown.
     */
    int32_t floor __attribute__ ((aligned(4)));
    /**
     * in units of 1/64 m.
     */
    int32_t heightAboveFloor __attribute__ ((aligned(4)));
    /**
     * in units of 1/64 m. 0 if unknown
     */
    int32_t heightUnc __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, latitude) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, longitude) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, altitude) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, latitudeUnc) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, longitudeUnc) == 21, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, altitudeUnc) == 22, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, motionPattern) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, floor) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, heightAboveFloor) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLciInformation, heightUnc) == 36, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::RttLciInformation) == 40, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::RttLciInformation) == 8, "wrong alignment");

struct RttLcrInformation final {
    /**
     * Country code symbol.
     */
    ::android::hardware::hidl_array<int8_t, 2> countryCode __attribute__ ((aligned(1)));
    /**
     * Civic info to be copied in FTM frame.
     */
    ::android::hardware::hidl_string civicInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::RttLcrInformation, countryCode) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttLcrInformation, civicInfo) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::RttLcrInformation) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::RttLcrInformation) == 8, "wrong alignment");

/**
 * RTT Responder information
 */
struct RttResponder final {
    ::android::hardware::wifi::V1_0::WifiChannelInfo channel __attribute__ ((aligned(4)));
    ::android::hardware::wifi::V1_0::RttPreamble preamble __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::RttResponder, channel) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::RttResponder, preamble) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::RttResponder) == 20, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::RttResponder) == 4, "wrong alignment");

/**
 * Debug data specific types.
 * TODO(b/32159498): Move to a separate debug_types.hal.
 */
typedef uint32_t WifiRingBufferId;

/**
 * Flags describing each debug ring buffer.
 */
enum class WifiDebugRingBufferFlags : uint32_t {
    HAS_BINARY_ENTRIES = 1u /* 1 << 0 */,
    HAS_ASCII_ENTRIES = 2u /* 1 << 1 */,
    HAS_PER_PACKET_ENTRIES = 4u /* 1 << 2 */,
};

/**
 * Struct describing each debug ring buffer supported by
 * the device.
 */
struct WifiDebugRingBufferStatus final {
    /**
     * Name of this debug ring buffer.
     */
    ::android::hardware::hidl_string ringName __attribute__ ((aligned(8)));
    /**
     * Combination of |WifiDebugRingBufferFlags| values.
     */
    uint32_t flags __attribute__ ((aligned(4)));
    /**
     * Unique integer representing the ring.
     */
    uint32_t ringId __attribute__ ((aligned(4)));
    /**
     * Total memory size allocated for the buffer.
     */
    uint32_t sizeInBytes __attribute__ ((aligned(4)));
    /**
     * Amount of free space in the buffer.
     */
    uint32_t freeSizeInBytes __attribute__ ((aligned(4)));
    /**
     * Verbose level for ring buffer.
     */
    uint32_t verboseLevel __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus, ringName) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus, flags) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus, ringId) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus, sizeInBytes) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus, freeSizeInBytes) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus, verboseLevel) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus) == 40, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus) == 8, "wrong alignment");

/**
 * Verbose logging level to set for each debug ring buffer supported
 * by the device.
 */
enum class WifiDebugRingBufferVerboseLevel : uint32_t {
    /**
     * Level 0 corresponds to no collection, and it makes log handler
     * stop by no more events from driver.
     */
    NONE = 0u,
    /**
     * Level 1 correspond to normal log level, with minimal user impact.
     * This is the default value.
     */
    DEFAULT = 1u,
    /**
     * Level 2 is enabled when user is lazily trying to reproduce a problem,
     * wifi performances and power can be impacted but device should not
     * otherwise be significantly impacted.
     */
    VERBOSE = 2u,
    /**
     * Level 3 is used when trying to actively debug a problem.
     * This will cause sever performance degradation.
     */
    EXCESSIVE = 3u,
};

/**
 * Enum describing the fate of the TX packets.
 */
enum class WifiDebugTxPacketFate : uint32_t {
    /**
     * Sent over air and ACKed.
     */
    ACKED = 0u,
    /**
     * Sent over air but not ACKed. (Normal for broadcast/multicast.)
     */
    SENT = 1u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.ACKED implicitly + 1 */,
    /**
     * Queued within firmware, but not yet sent over air.
     */
    FW_QUEUED = 2u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.SENT implicitly + 1 */,
    /**
     * Dropped by firmware as invalid. E.g. bad source address, bad checksum,
     * or invalid for current state.
     */
    FW_DROP_INVALID = 3u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.FW_QUEUED implicitly + 1 */,
    /**
     * Dropped by firmware due to lack of buffer space.
     */
    FW_DROP_NOBUFS = 4u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.FW_DROP_INVALID implicitly + 1 */,
    /**
     * Dropped by firmware for any other reason. Includes frames that  were sent
     * by driver to firmware, but unaccounted for by  firmware.
     */
    FW_DROP_OTHER = 5u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.FW_DROP_NOBUFS implicitly + 1 */,
    /**
     * Queued within driver, not yet sent to firmware.
     */
    DRV_QUEUED = 6u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.FW_DROP_OTHER implicitly + 1 */,
    /**
     * Dropped by driver as invalid. E.g. bad source address, or invalid for
     * current state.
     */
    DRV_DROP_INVALID = 7u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.DRV_QUEUED implicitly + 1 */,
    /**
     * Dropped by driver due to lack of buffer space.
     */
    DRV_DROP_NOBUFS = 8u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.DRV_DROP_INVALID implicitly + 1 */,
    /**
     * Dropped by driver for any other reason.
     */
    DRV_DROP_OTHER = 9u /* ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate.DRV_DROP_NOBUFS implicitly + 1 */,
};

/**
 * Enum describing the fate of the TX packets.
 */
enum class WifiDebugRxPacketFate : uint32_t {
    /**
     * Valid and delivered to network stack (e.g., netif_rx()).
     */
    SUCCESS = 0u,
    /**
     * Queued within firmware, but not yet sent to driver.
     */
    FW_QUEUED = 1u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.SUCCESS implicitly + 1 */,
    /**
     * Dropped by firmware due to host-programmable filters.
     */
    FW_DROP_FILTER = 2u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.FW_QUEUED implicitly + 1 */,
    /**
     * Dropped by firmware as invalid. E.g. bad checksum, decrypt failed,
     * or invalid for current state.
     */
    FW_DROP_INVALID = 3u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.FW_DROP_FILTER implicitly + 1 */,
    /**
     * Dropped by firmware due to lack of buffer space.
     */
    FW_DROP_NOBUFS = 4u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.FW_DROP_INVALID implicitly + 1 */,
    /**
     * Dropped by firmware for any other reason.
     */
    FW_DROP_OTHER = 5u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.FW_DROP_NOBUFS implicitly + 1 */,
    /**
     * Queued within driver, not yet delivered to network stack.
     */
    DRV_QUEUED = 6u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.FW_DROP_OTHER implicitly + 1 */,
    /**
     * Dropped by driver due to filter rules.
     */
    DRV_DROP_FILTER = 7u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.DRV_QUEUED implicitly + 1 */,
    /**
     * Dropped by driver as invalid. E.g. not permitted in current state.
     */
    DRV_DROP_INVALID = 8u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.DRV_DROP_FILTER implicitly + 1 */,
    /**
     * Dropped by driver due to lack of buffer space.
     */
    DRV_DROP_NOBUFS = 9u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.DRV_DROP_INVALID implicitly + 1 */,
    /**
     * Dropped by driver for any other reason.
     */
    DRV_DROP_OTHER = 10u /* ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate.DRV_DROP_NOBUFS implicitly + 1 */,
};

/**
 * Type of frame transmitted/received.
 */
enum class WifiDebugPacketFateFrameType : uint32_t {
    UNKNOWN = 0u,
    ETHERNET_II = 1u /* ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType.UNKNOWN implicitly + 1 */,
    MGMT_80211 = 2u /* ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType.ETHERNET_II implicitly + 1 */,
};

/**
 * Information regarding the frame transmitted/received.
 */
struct WifiDebugPacketFateFrameInfo final {
    /**
     * The type of MAC-layer frame that this frame_info holds.
     * - For data frames, use FRAME_TYPE_ETHERNET_II.
     * - For management frames, use FRAME_TYPE_80211_MGMT.
     * - If the type of the frame is unknown, use FRAME_TYPE_UNKNOWN.
     */
    ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType frameType __attribute__ ((aligned(4)));
    /**
     * The number of bytes included in |frameContent|.
     * If the frame  contents are missing (e.g. RX frame dropped in firmware),
     * |frameLen| must be set to 0.
     */
    uint64_t frameLen __attribute__ ((aligned(8)));
    /**
     * Host clock when this frame was received by the driver (either  outbound
     * from the host network stack, or inbound from the firmware).
     * - The timestamp must be taken from a clock which includes time the host
     *   spent suspended (e.g. ktime_get_boottime()).
     * - If no host timestamp is available (e.g. RX frame was dropped in firmware),
     *   this field must be set to 0.
     */
    uint64_t driverTimestampUsec __attribute__ ((aligned(8)));
    /**
     * Firmware clock when this frame was received by the firmware
     * (either outbound from the host, or inbound from a remote  station).
     * - The timestamp must be taken from a clock which includes time firmware
     *   spent suspended (if applicable).
     * - If no firmware timestamp is available (e.g. TX frame was dropped by
     *   driver), this field must be set to 0.
     * - Consumers of |frameInfo| must not assume any synchronization between
     *   driver and firmware clocks.
     */
    uint64_t firmwareTimestampUsec __attribute__ ((aligned(8)));
    /**
     * Actual frame content. This is the raw bytes of the corresponding packet.
     * - Should be provided for TX frames originated by the host.
     * - Should be provided for RX frames received by the driver.
     * - Optionally provided for TX frames originated by firmware.
     *   (At discretion of HAL implementation.)
     * - Optionally provided for RX frames dropped in firmware.
     *   (At discretion of HAL implementation.)
     * - If frame content is not provided, |frameLen| must be set to 0.
     */
    ::android::hardware::hidl_vec<uint8_t> frameContent __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo, frameType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo, frameLen) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo, driverTimestampUsec) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo, firmwareTimestampUsec) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo, frameContent) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo) == 48, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo) == 8, "wrong alignment");

/**
 * Struct describing packet fate report for each Tx frame.
 */
struct WifiDebugTxPacketFateReport final {
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate fate __attribute__ ((aligned(4)));
    ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo frameInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport, fate) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport, frameInfo) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport) == 56, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport) == 8, "wrong alignment");

/**
 * Struct describing packet fate report for each Rx frame.
 */
struct WifiDebugRxPacketFateReport final {
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate fate __attribute__ ((aligned(4)));
    ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo frameInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport, fate) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport, frameInfo) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport) == 56, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport) == 8, "wrong alignment");

/**
 * Struct capturing the count of all rx packets that caused
 * host wakeup.
 */
struct WifiDebugHostWakeReasonRxPacketDetails final {
    /**
     * Total rx unicast packet which woke up host.
     */
    uint32_t rxUnicastCnt __attribute__ ((aligned(4)));
    /**
     * Total rx multicast packet which woke up host.
     */
    uint32_t rxMulticastCnt __attribute__ ((aligned(4)));
    /**
     * Total rx broadcast packet which woke up host.
     */
    uint32_t rxBroadcastCnt __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails, rxUnicastCnt) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails, rxMulticastCnt) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails, rxBroadcastCnt) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails) == 12, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails) == 4, "wrong alignment");

/**
 * Struct capturing the count of all rx multicast packets that caused
 * host wakeup.
 */
struct WifiDebugHostWakeReasonRxMulticastPacketDetails final {
    /**
     * Rx wake packet was ipv4 multicast.
     */
    uint32_t ipv4RxMulticastAddrCnt __attribute__ ((aligned(4)));
    /**
     * Rx wake packet was ipv6 multicast.
     */
    uint32_t ipv6RxMulticastAddrCnt __attribute__ ((aligned(4)));
    /**
     * Rx wake packet was non-ipv4 and non-ipv6.
     */
    uint32_t otherRxMulticastAddrCnt __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails, ipv4RxMulticastAddrCnt) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails, ipv6RxMulticastAddrCnt) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails, otherRxMulticastAddrCnt) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails) == 12, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails) == 4, "wrong alignment");

/**
 * Struct capturing the count of all rx ICMP packets that caused
 * host wakeup.
 */
struct WifiDebugHostWakeReasonRxIcmpPacketDetails final {
    /**
     * Wake icmp packet count.
     */
    uint32_t icmpPkt __attribute__ ((aligned(4)));
    /**
     * Wake icmp6 packet count.
     */
    uint32_t icmp6Pkt __attribute__ ((aligned(4)));
    /**
     * Wake icmp6 RA packet count.
     */
    uint32_t icmp6Ra __attribute__ ((aligned(4)));
    /**
     * Wake icmp6 NA packet count.
     */
    uint32_t icmp6Na __attribute__ ((aligned(4)));
    /**
     * Wake icmp6 NS packet count.
     */
    uint32_t icmp6Ns __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails, icmpPkt) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails, icmp6Pkt) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails, icmp6Ra) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails, icmp6Na) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails, icmp6Ns) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails) == 20, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails) == 4, "wrong alignment");

/**
 * Structure capturing the count of all the wireless related host wakeup.
 * This is used to capture all the reasons why the host processor
 * (WLAN driver) was woken up by the WLAN firmware.
 * These stats may be used to debug any power issues caused due to frequent
 * wakeup of the host processor by the WLAN firmware.
 */
struct WifiDebugHostWakeReasonStats final {
    /**
     * Total count of cmd/event wakes.
     * These must account for all wakeups due to WLAN management
     * commands/events received over the air.
     */
    uint32_t totalCmdEventWakeCnt __attribute__ ((aligned(4)));
    /**
     * Vector of wake counts per cmd/event type.
     * The number of command types and their meaning is only understood by the
     * vendor.
     */
    ::android::hardware::hidl_vec<uint32_t> cmdEventWakeCntPerType __attribute__ ((aligned(8)));
    /**
     * Total count of drive/fw wakes.
     * These must account for all wakeups due to local driver/firmware
     * interactions. These include all vendor implementation specific
     * interactions like any heart-beat monitoring, Bus management, etc.
     */
    uint32_t totalDriverFwLocalWakeCnt __attribute__ ((aligned(4)));
    /**
     * Vector of wake counts per driver/firmware interaction type.
     * The number of command types and their meaning is only understood by the
     * vendor.
     */
    ::android::hardware::hidl_vec<uint32_t> driverFwLocalWakeCntPerType __attribute__ ((aligned(8)));
    /**
     * Total data rx packets, that woke up host.
     */
    uint32_t totalRxPacketWakeCnt __attribute__ ((aligned(4)));
    ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails rxPktWakeDetails __attribute__ ((aligned(4)));
    ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails rxMulticastPkWakeDetails __attribute__ ((aligned(4)));
    ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails rxIcmpPkWakeDetails __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, totalCmdEventWakeCnt) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, cmdEventWakeCntPerType) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, totalDriverFwLocalWakeCnt) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, driverFwLocalWakeCntPerType) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, totalRxPacketWakeCnt) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, rxPktWakeDetails) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, rxMulticastPkWakeDetails) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats, rxIcmpPkWakeDetails) == 76, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats) == 96, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiStatusCode o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiStatusCode lhs, const ::android::hardware::wifi::V1_0::WifiStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiStatusCode lhs, const ::android::hardware::wifi::V1_0::WifiStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiStatus& lhs, const ::android::hardware::wifi::V1_0::WifiStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiStatus& lhs, const ::android::hardware::wifi::V1_0::WifiStatus& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::IfaceType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::IfaceType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::IfaceType lhs, const ::android::hardware::wifi::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::IfaceType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::IfaceType lhs, const ::android::hardware::wifi::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::IfaceType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::IfaceType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::IfaceType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::IfaceType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz lhs, const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz lhs, const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiChannelInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiChannelInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiChannelInfo& lhs, const ::android::hardware::wifi::V1_0::WifiChannelInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiChannelInfo& lhs, const ::android::hardware::wifi::V1_0::WifiChannelInfo& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiInformationElement& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiInformationElement& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiInformationElement& lhs, const ::android::hardware::wifi::V1_0::WifiInformationElement& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiInformationElement& lhs, const ::android::hardware::wifi::V1_0::WifiInformationElement& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiRatePreamble o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiRatePreamble o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiRatePreamble lhs, const ::android::hardware::wifi::V1_0::WifiRatePreamble rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiRatePreamble rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiRatePreamble lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiRatePreamble lhs, const ::android::hardware::wifi::V1_0::WifiRatePreamble rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiRatePreamble rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiRatePreamble lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiRatePreamble e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiRatePreamble e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiRateNss o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiRateNss o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiRateNss lhs, const ::android::hardware::wifi::V1_0::WifiRateNss rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiRateNss rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiRateNss lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiRateNss lhs, const ::android::hardware::wifi::V1_0::WifiRateNss rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiRateNss rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiRateNss lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiRateNss e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiRateNss e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiRateInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiRateInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiRateInfo& lhs, const ::android::hardware::wifi::V1_0::WifiRateInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiRateInfo& lhs, const ::android::hardware::wifi::V1_0::WifiRateInfo& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiBand o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiBand o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiBand lhs, const ::android::hardware::wifi::V1_0::WifiBand rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiBand rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiBand lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiBand lhs, const ::android::hardware::wifi::V1_0::WifiBand rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiBand rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiBand lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiBand e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiBand e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask o);
static inline void PrintTo(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::StaScanLimits o);
static inline void PrintTo(::android::hardware::wifi::V1_0::StaScanLimits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::StaScanLimits lhs, const ::android::hardware::wifi::V1_0::StaScanLimits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::StaScanLimits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::StaScanLimits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::StaScanLimits lhs, const ::android::hardware::wifi::V1_0::StaScanLimits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::StaScanLimits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::StaScanLimits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::StaScanLimits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::StaScanLimits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerStats& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaScanResult& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaScanResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaScanResult& lhs, const ::android::hardware::wifi::V1_0::StaScanResult& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaScanResult& lhs, const ::android::hardware::wifi::V1_0::StaScanResult& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::StaScanDataFlagMask o);
static inline void PrintTo(::android::hardware::wifi::V1_0::StaScanDataFlagMask o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::wifi::V1_0::StaScanDataFlagMask lhs, const ::android::hardware::wifi::V1_0::StaScanDataFlagMask rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::wifi::V1_0::StaScanDataFlagMask rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::wifi::V1_0::StaScanDataFlagMask lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::wifi::V1_0::StaScanDataFlagMask lhs, const ::android::hardware::wifi::V1_0::StaScanDataFlagMask rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::wifi::V1_0::StaScanDataFlagMask rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::wifi::V1_0::StaScanDataFlagMask lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::wifi::V1_0::StaScanDataFlagMask e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::wifi::V1_0::StaScanDataFlagMask e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaScanData& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaScanData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaScanData& lhs, const ::android::hardware::wifi::V1_0::StaScanData& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaScanData& lhs, const ::android::hardware::wifi::V1_0::StaScanData& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaRoamingConfig& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaRoamingConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::StaRoamingConfig& lhs, const ::android::hardware::wifi::V1_0::StaRoamingConfig& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaRoamingConfig& lhs, const ::android::hardware::wifi::V1_0::StaRoamingConfig& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::StaRoamingState o);
static inline void PrintTo(::android::hardware::wifi::V1_0::StaRoamingState o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::V1_0::StaRoamingState lhs, const ::android::hardware::wifi::V1_0::StaRoamingState rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::V1_0::StaRoamingState rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::V1_0::StaRoamingState lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::V1_0::StaRoamingState lhs, const ::android::hardware::wifi::V1_0::StaRoamingState rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::V1_0::StaRoamingState rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::V1_0::StaRoamingState lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::V1_0::StaRoamingState e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::V1_0::StaRoamingState e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanParamSizeLimits o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanParamSizeLimits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanParamSizeLimits lhs, const ::android::hardware::wifi::V1_0::NanParamSizeLimits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanParamSizeLimits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanParamSizeLimits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanParamSizeLimits lhs, const ::android::hardware::wifi::V1_0::NanParamSizeLimits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanParamSizeLimits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanParamSizeLimits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanParamSizeLimits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanParamSizeLimits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanStatusType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanStatusType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanStatusType lhs, const ::android::hardware::wifi::V1_0::NanStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanStatusType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanStatusType lhs, const ::android::hardware::wifi::V1_0::NanStatusType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanStatusType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanStatusType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanStatusType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanStatusType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanBandIndex o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanBandIndex o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanBandIndex lhs, const ::android::hardware::wifi::V1_0::NanBandIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanBandIndex rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanBandIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanBandIndex lhs, const ::android::hardware::wifi::V1_0::NanBandIndex rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanBandIndex rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanBandIndex lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanBandIndex e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanBandIndex e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiNanStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiNanStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiNanStatus& lhs, const ::android::hardware::wifi::V1_0::WifiNanStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiNanStatus& lhs, const ::android::hardware::wifi::V1_0::WifiNanStatus& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanMatchAlg o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanMatchAlg o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanMatchAlg lhs, const ::android::hardware::wifi::V1_0::NanMatchAlg rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanMatchAlg rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanMatchAlg lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanMatchAlg lhs, const ::android::hardware::wifi::V1_0::NanMatchAlg rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanMatchAlg rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanMatchAlg lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanMatchAlg e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanMatchAlg e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanPublishType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanPublishType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanPublishType lhs, const ::android::hardware::wifi::V1_0::NanPublishType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanPublishType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanPublishType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanPublishType lhs, const ::android::hardware::wifi::V1_0::NanPublishType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanPublishType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanPublishType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanPublishType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanPublishType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanTxType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanTxType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanTxType lhs, const ::android::hardware::wifi::V1_0::NanTxType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanTxType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanTxType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanTxType lhs, const ::android::hardware::wifi::V1_0::NanTxType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanTxType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanTxType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanTxType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanTxType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanSubscribeType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanSubscribeType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanSubscribeType lhs, const ::android::hardware::wifi::V1_0::NanSubscribeType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanSubscribeType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanSubscribeType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanSubscribeType lhs, const ::android::hardware::wifi::V1_0::NanSubscribeType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanSubscribeType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanSubscribeType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanSubscribeType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanSubscribeType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanSrfType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanSrfType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanSrfType lhs, const ::android::hardware::wifi::V1_0::NanSrfType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanSrfType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanSrfType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanSrfType lhs, const ::android::hardware::wifi::V1_0::NanSrfType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanSrfType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanSrfType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanSrfType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanSrfType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanDataPathChannelCfg o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanDataPathChannelCfg o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg lhs, const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg lhs, const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanDataPathChannelCfg e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanDataPathSecurityType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanDataPathSecurityType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanDataPathSecurityType lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanDataPathSecurityType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanDataPathSecurityType lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanDataPathSecurityType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanDataPathSecurityType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanDataPathSecurityType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& lhs, const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& lhs, const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDebugConfig& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDebugConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDebugConfig& lhs, const ::android::hardware::wifi::V1_0::NanDebugConfig& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDebugConfig& lhs, const ::android::hardware::wifi::V1_0::NanDebugConfig& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanConfigRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanConfigRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanConfigRequest& lhs, const ::android::hardware::wifi::V1_0::NanConfigRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanConfigRequest& lhs, const ::android::hardware::wifi::V1_0::NanConfigRequest& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanEnableRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanEnableRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanEnableRequest& lhs, const ::android::hardware::wifi::V1_0::NanEnableRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanEnableRequest& lhs, const ::android::hardware::wifi::V1_0::NanEnableRequest& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanCipherSuiteType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanCipherSuiteType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanCipherSuiteType lhs, const ::android::hardware::wifi::V1_0::NanCipherSuiteType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanCipherSuiteType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanCipherSuiteType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanCipherSuiteType lhs, const ::android::hardware::wifi::V1_0::NanCipherSuiteType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanCipherSuiteType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanCipherSuiteType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanCipherSuiteType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanCipherSuiteType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanRangingIndication o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanRangingIndication o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanRangingIndication lhs, const ::android::hardware::wifi::V1_0::NanRangingIndication rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanRangingIndication rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanRangingIndication lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanRangingIndication lhs, const ::android::hardware::wifi::V1_0::NanRangingIndication rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanRangingIndication rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanRangingIndication lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanRangingIndication e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanRangingIndication e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& lhs, const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& lhs, const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanPublishRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanPublishRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanPublishRequest& lhs, const ::android::hardware::wifi::V1_0::NanPublishRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanPublishRequest& lhs, const ::android::hardware::wifi::V1_0::NanPublishRequest& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& lhs, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& lhs, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& lhs, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& lhs, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& lhs, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& lhs, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& lhs, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& lhs, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanCapabilities& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanCapabilities& lhs, const ::android::hardware::wifi::V1_0::NanCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanCapabilities& lhs, const ::android::hardware::wifi::V1_0::NanCapabilities& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanMatchInd& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanMatchInd& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanMatchInd& lhs, const ::android::hardware::wifi::V1_0::NanMatchInd& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanMatchInd& lhs, const ::android::hardware::wifi::V1_0::NanMatchInd& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& lhs, const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& lhs, const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::NanClusterEventType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::NanClusterEventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanClusterEventType lhs, const ::android::hardware::wifi::V1_0::NanClusterEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanClusterEventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::NanClusterEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanClusterEventType lhs, const ::android::hardware::wifi::V1_0::NanClusterEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::NanClusterEventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::NanClusterEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanClusterEventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::NanClusterEventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanClusterEventInd& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanClusterEventInd& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanClusterEventInd& lhs, const ::android::hardware::wifi::V1_0::NanClusterEventInd& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanClusterEventInd& lhs, const ::android::hardware::wifi::V1_0::NanClusterEventInd& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::RttStatus o);
static inline void PrintTo(::android::hardware::wifi::V1_0::RttStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttStatus lhs, const ::android::hardware::wifi::V1_0::RttStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttStatus lhs, const ::android::hardware::wifi::V1_0::RttStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::RttPeerType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::RttPeerType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttPeerType lhs, const ::android::hardware::wifi::V1_0::RttPeerType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttPeerType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttPeerType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttPeerType lhs, const ::android::hardware::wifi::V1_0::RttPeerType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttPeerType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttPeerType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttPeerType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttPeerType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::RttBw o);
static inline void PrintTo(::android::hardware::wifi::V1_0::RttBw o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttBw lhs, const ::android::hardware::wifi::V1_0::RttBw rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttBw rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttBw lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttBw lhs, const ::android::hardware::wifi::V1_0::RttBw rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttBw rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttBw lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttBw e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttBw e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::RttPreamble o);
static inline void PrintTo(::android::hardware::wifi::V1_0::RttPreamble o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttPreamble lhs, const ::android::hardware::wifi::V1_0::RttPreamble rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttPreamble rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttPreamble lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttPreamble lhs, const ::android::hardware::wifi::V1_0::RttPreamble rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttPreamble rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttPreamble lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttPreamble e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttPreamble e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::RttType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::RttType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttType lhs, const ::android::hardware::wifi::V1_0::RttType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttType lhs, const ::android::hardware::wifi::V1_0::RttType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttConfig& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::RttConfig& lhs, const ::android::hardware::wifi::V1_0::RttConfig& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttConfig& lhs, const ::android::hardware::wifi::V1_0::RttConfig& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttResult& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::RttResult& lhs, const ::android::hardware::wifi::V1_0::RttResult& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttResult& lhs, const ::android::hardware::wifi::V1_0::RttResult& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttCapabilities& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttCapabilities& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::RttCapabilities& lhs, const ::android::hardware::wifi::V1_0::RttCapabilities& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttCapabilities& lhs, const ::android::hardware::wifi::V1_0::RttCapabilities& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::RttMotionPattern o);
static inline void PrintTo(::android::hardware::wifi::V1_0::RttMotionPattern o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttMotionPattern lhs, const ::android::hardware::wifi::V1_0::RttMotionPattern rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttMotionPattern rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::RttMotionPattern lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttMotionPattern lhs, const ::android::hardware::wifi::V1_0::RttMotionPattern rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::RttMotionPattern rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::RttMotionPattern lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttMotionPattern e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::RttMotionPattern e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttLciInformation& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttLciInformation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::RttLciInformation& lhs, const ::android::hardware::wifi::V1_0::RttLciInformation& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttLciInformation& lhs, const ::android::hardware::wifi::V1_0::RttLciInformation& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttLcrInformation& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttLcrInformation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::RttLcrInformation& lhs, const ::android::hardware::wifi::V1_0::RttLcrInformation& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttLcrInformation& lhs, const ::android::hardware::wifi::V1_0::RttLcrInformation& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttResponder& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttResponder& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::RttResponder& lhs, const ::android::hardware::wifi::V1_0::RttResponder& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttResponder& lhs, const ::android::hardware::wifi::V1_0::RttResponder& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType o);
static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_CHIP_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_CHIP_INVALID)) {
        os += (first ? "" : " | ");
        os += "ERROR_WIFI_CHIP_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_CHIP_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_IFACE_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_IFACE_INVALID)) {
        os += (first ? "" : " | ");
        os += "ERROR_WIFI_IFACE_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_IFACE_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_RTT_CONTROLLER_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_RTT_CONTROLLER_INVALID)) {
        os += (first ? "" : " | ");
        os += "ERROR_WIFI_RTT_CONTROLLER_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_RTT_CONTROLLER_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "ERROR_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_AVAILABLE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ERROR_NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_AVAILABLE;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_STARTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_STARTED)) {
        os += (first ? "" : " | ");
        os += "ERROR_NOT_STARTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_STARTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_INVALID_ARGS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_INVALID_ARGS)) {
        os += (first ? "" : " | ");
        os += "ERROR_INVALID_ARGS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_INVALID_ARGS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_BUSY) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_BUSY)) {
        os += (first ? "" : " | ");
        os += "ERROR_BUSY";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_BUSY;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ERROR_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_CHIP_INVALID) {
        return "ERROR_WIFI_CHIP_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_IFACE_INVALID) {
        return "ERROR_WIFI_IFACE_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_RTT_CONTROLLER_INVALID) {
        return "ERROR_WIFI_RTT_CONTROLLER_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_SUPPORTED) {
        return "ERROR_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_AVAILABLE) {
        return "ERROR_NOT_AVAILABLE";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_STARTED) {
        return "ERROR_NOT_STARTED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_INVALID_ARGS) {
        return "ERROR_INVALID_ARGS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_BUSY) {
        return "ERROR_BUSY";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_UNKNOWN) {
        return "ERROR_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::wifi::V1_0::toString(o.code);
    os += ", .description = ";
    os += ::android::hardware::toString(o.description);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiStatus& lhs, const ::android::hardware::wifi::V1_0::WifiStatus& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.description != rhs.description) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiStatus& lhs, const ::android::hardware::wifi::V1_0::WifiStatus& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::IfaceType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::IfaceType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::IfaceType::STA) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IfaceType::STA)) {
        os += (first ? "" : " | ");
        os += "STA";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IfaceType::STA;
    }
    if ((o & ::android::hardware::wifi::V1_0::IfaceType::AP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IfaceType::AP)) {
        os += (first ? "" : " | ");
        os += "AP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IfaceType::AP;
    }
    if ((o & ::android::hardware::wifi::V1_0::IfaceType::P2P) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IfaceType::P2P)) {
        os += (first ? "" : " | ");
        os += "P2P";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IfaceType::P2P;
    }
    if ((o & ::android::hardware::wifi::V1_0::IfaceType::NAN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IfaceType::NAN)) {
        os += (first ? "" : " | ");
        os += "NAN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IfaceType::NAN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::IfaceType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::IfaceType::STA) {
        return "STA";
    }
    if (o == ::android::hardware::wifi::V1_0::IfaceType::AP) {
        return "AP";
    }
    if (o == ::android::hardware::wifi::V1_0::IfaceType::P2P) {
        return "P2P";
    }
    if (o == ::android::hardware::wifi::V1_0::IfaceType::NAN) {
        return "NAN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::IfaceType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiChannelWidthInMhz>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiChannelWidthInMhz> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_20) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_20)) {
        os += (first ? "" : " | ");
        os += "WIDTH_20";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_20;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_40) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_40)) {
        os += (first ? "" : " | ");
        os += "WIDTH_40";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_40;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80)) {
        os += (first ? "" : " | ");
        os += "WIDTH_80";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_160) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_160)) {
        os += (first ? "" : " | ");
        os += "WIDTH_160";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_160;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80P80) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80P80)) {
        os += (first ? "" : " | ");
        os += "WIDTH_80P80";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80P80;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_5) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_5)) {
        os += (first ? "" : " | ");
        os += "WIDTH_5";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_5;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_10) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_10)) {
        os += (first ? "" : " | ");
        os += "WIDTH_10";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_10;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_INVALID)) {
        os += (first ? "" : " | ");
        os += "WIDTH_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_INVALID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_20) {
        return "WIDTH_20";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_40) {
        return "WIDTH_40";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80) {
        return "WIDTH_80";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_160) {
        return "WIDTH_160";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80P80) {
        return "WIDTH_80P80";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_5) {
        return "WIDTH_5";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_10) {
        return "WIDTH_10";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_INVALID) {
        return "WIDTH_INVALID";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiChannelWidthInMhz o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiChannelInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::wifi::V1_0::toString(o.width);
    os += ", .centerFreq = ";
    os += ::android::hardware::toString(o.centerFreq);
    os += ", .centerFreq0 = ";
    os += ::android::hardware::toString(o.centerFreq0);
    os += ", .centerFreq1 = ";
    os += ::android::hardware::toString(o.centerFreq1);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiChannelInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiChannelInfo& lhs, const ::android::hardware::wifi::V1_0::WifiChannelInfo& rhs) {
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.centerFreq != rhs.centerFreq) {
        return false;
    }
    if (lhs.centerFreq0 != rhs.centerFreq0) {
        return false;
    }
    if (lhs.centerFreq1 != rhs.centerFreq1) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiChannelInfo& lhs, const ::android::hardware::wifi::V1_0::WifiChannelInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiInformationElement& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiInformationElement& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiInformationElement& lhs, const ::android::hardware::wifi::V1_0::WifiInformationElement& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.data != rhs.data) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiInformationElement& lhs, const ::android::hardware::wifi::V1_0::WifiInformationElement& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiRatePreamble>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiRatePreamble> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiRatePreamble::OFDM) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRatePreamble::OFDM)) {
        os += (first ? "" : " | ");
        os += "OFDM";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRatePreamble::OFDM;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRatePreamble::CCK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRatePreamble::CCK)) {
        os += (first ? "" : " | ");
        os += "CCK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRatePreamble::CCK;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRatePreamble::HT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRatePreamble::HT)) {
        os += (first ? "" : " | ");
        os += "HT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRatePreamble::HT;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRatePreamble::VHT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRatePreamble::VHT)) {
        os += (first ? "" : " | ");
        os += "VHT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRatePreamble::VHT;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRatePreamble::RESERVED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRatePreamble::RESERVED)) {
        os += (first ? "" : " | ");
        os += "RESERVED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRatePreamble::RESERVED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiRatePreamble o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiRatePreamble::OFDM) {
        return "OFDM";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRatePreamble::CCK) {
        return "CCK";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRatePreamble::HT) {
        return "HT";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRatePreamble::VHT) {
        return "VHT";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRatePreamble::RESERVED) {
        return "RESERVED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiRatePreamble o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiRateNss>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiRateNss> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiRateNss::NSS_1x1) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRateNss::NSS_1x1)) {
        os += (first ? "" : " | ");
        os += "NSS_1x1";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRateNss::NSS_1x1;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRateNss::NSS_2x2) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRateNss::NSS_2x2)) {
        os += (first ? "" : " | ");
        os += "NSS_2x2";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRateNss::NSS_2x2;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRateNss::NSS_3x3) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRateNss::NSS_3x3)) {
        os += (first ? "" : " | ");
        os += "NSS_3x3";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRateNss::NSS_3x3;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiRateNss::NSS_4x4) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiRateNss::NSS_4x4)) {
        os += (first ? "" : " | ");
        os += "NSS_4x4";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiRateNss::NSS_4x4;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiRateNss o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiRateNss::NSS_1x1) {
        return "NSS_1x1";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRateNss::NSS_2x2) {
        return "NSS_2x2";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRateNss::NSS_3x3) {
        return "NSS_3x3";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiRateNss::NSS_4x4) {
        return "NSS_4x4";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiRateNss o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiRateInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".preamble = ";
    os += ::android::hardware::wifi::V1_0::toString(o.preamble);
    os += ", .nss = ";
    os += ::android::hardware::wifi::V1_0::toString(o.nss);
    os += ", .bw = ";
    os += ::android::hardware::wifi::V1_0::toString(o.bw);
    os += ", .rateMcsIdx = ";
    os += ::android::hardware::toString(o.rateMcsIdx);
    os += ", .bitRateInKbps = ";
    os += ::android::hardware::toString(o.bitRateInKbps);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiRateInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiRateInfo& lhs, const ::android::hardware::wifi::V1_0::WifiRateInfo& rhs) {
    if (lhs.preamble != rhs.preamble) {
        return false;
    }
    if (lhs.nss != rhs.nss) {
        return false;
    }
    if (lhs.bw != rhs.bw) {
        return false;
    }
    if (lhs.rateMcsIdx != rhs.rateMcsIdx) {
        return false;
    }
    if (lhs.bitRateInKbps != rhs.bitRateInKbps) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiRateInfo& lhs, const ::android::hardware::wifi::V1_0::WifiRateInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiBand>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiBand> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_UNSPECIFIED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "BAND_UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_UNSPECIFIED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ)) {
        os += (first ? "" : " | ");
        os += "BAND_24GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ)) {
        os += (first ? "" : " | ");
        os += "BAND_5GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_DFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_DFS)) {
        os += (first ? "" : " | ");
        os += "BAND_5GHZ_DFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_DFS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_WITH_DFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_WITH_DFS)) {
        os += (first ? "" : " | ");
        os += "BAND_5GHZ_WITH_DFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_WITH_DFS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ)) {
        os += (first ? "" : " | ");
        os += "BAND_24GHZ_5GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ_WITH_DFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ_WITH_DFS)) {
        os += (first ? "" : " | ");
        os += "BAND_24GHZ_5GHZ_WITH_DFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ_WITH_DFS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiBand o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_UNSPECIFIED) {
        return "BAND_UNSPECIFIED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ) {
        return "BAND_24GHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ) {
        return "BAND_5GHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_DFS) {
        return "BAND_5GHZ_DFS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_WITH_DFS) {
        return "BAND_5GHZ_WITH_DFS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ) {
        return "BAND_24GHZ_5GHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ_WITH_DFS) {
        return "BAND_24GHZ_5GHZ_WITH_DFS";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiBand o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .maxLength = ";
    os += ::android::hardware::toString(o.maxLength);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& rhs) {
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.maxLength != rhs.maxLength) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".maxCacheSize = ";
    os += ::android::hardware::toString(o.maxCacheSize);
    os += ", .maxBuckets = ";
    os += ::android::hardware::toString(o.maxBuckets);
    os += ", .maxApCachePerScan = ";
    os += ::android::hardware::toString(o.maxApCachePerScan);
    os += ", .maxReportingThreshold = ";
    os += ::android::hardware::toString(o.maxReportingThreshold);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& rhs) {
    if (lhs.maxCacheSize != rhs.maxCacheSize) {
        return false;
    }
    if (lhs.maxBuckets != rhs.maxBuckets) {
        return false;
    }
    if (lhs.maxApCachePerScan != rhs.maxApCachePerScan) {
        return false;
    }
    if (lhs.maxReportingThreshold != rhs.maxReportingThreshold) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::EACH_SCAN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::EACH_SCAN)) {
        os += (first ? "" : " | ");
        os += "EACH_SCAN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::EACH_SCAN;
    }
    if ((o & ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::FULL_RESULTS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::FULL_RESULTS)) {
        os += (first ? "" : " | ");
        os += "FULL_RESULTS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::FULL_RESULTS;
    }
    if ((o & ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::NO_BATCH) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::NO_BATCH)) {
        os += (first ? "" : " | ");
        os += "NO_BATCH";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::NO_BATCH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::EACH_SCAN) {
        return "EACH_SCAN";
    }
    if (o == ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::FULL_RESULTS) {
        return "FULL_RESULTS";
    }
    if (o == ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::NO_BATCH) {
        return "NO_BATCH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::StaScanLimits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::StaScanLimits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::StaScanLimits::MAX_CHANNELS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::StaScanLimits::MAX_CHANNELS)) {
        os += (first ? "" : " | ");
        os += "MAX_CHANNELS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaScanLimits::MAX_CHANNELS;
    }
    if ((o & ::android::hardware::wifi::V1_0::StaScanLimits::MAX_BUCKETS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::StaScanLimits::MAX_BUCKETS)) {
        os += (first ? "" : " | ");
        os += "MAX_BUCKETS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaScanLimits::MAX_BUCKETS;
    }
    if ((o & ::android::hardware::wifi::V1_0::StaScanLimits::MAX_AP_CACHE_PER_SCAN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::StaScanLimits::MAX_AP_CACHE_PER_SCAN)) {
        os += (first ? "" : " | ");
        os += "MAX_AP_CACHE_PER_SCAN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaScanLimits::MAX_AP_CACHE_PER_SCAN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::StaScanLimits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::StaScanLimits::MAX_CHANNELS) {
        return "MAX_CHANNELS";
    }
    if (o == ::android::hardware::wifi::V1_0::StaScanLimits::MAX_BUCKETS) {
        return "MAX_BUCKETS";
    }
    if (o == ::android::hardware::wifi::V1_0::StaScanLimits::MAX_AP_CACHE_PER_SCAN) {
        return "MAX_AP_CACHE_PER_SCAN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::StaScanLimits o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bucketIdx = ";
    os += ::android::hardware::toString(o.bucketIdx);
    os += ", .band = ";
    os += ::android::hardware::wifi::V1_0::toString(o.band);
    os += ", .frequencies = ";
    os += ::android::hardware::toString(o.frequencies);
    os += ", .periodInMs = ";
    os += ::android::hardware::toString(o.periodInMs);
    os += ", .eventReportScheme = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask>(o.eventReportScheme);
    os += ", .exponentialMaxPeriodInMs = ";
    os += ::android::hardware::toString(o.exponentialMaxPeriodInMs);
    os += ", .exponentialBase = ";
    os += ::android::hardware::toString(o.exponentialBase);
    os += ", .exponentialStepCount = ";
    os += ::android::hardware::toString(o.exponentialStepCount);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& rhs) {
    if (lhs.bucketIdx != rhs.bucketIdx) {
        return false;
    }
    if (lhs.band != rhs.band) {
        return false;
    }
    if (lhs.frequencies != rhs.frequencies) {
        return false;
    }
    if (lhs.periodInMs != rhs.periodInMs) {
        return false;
    }
    if (lhs.eventReportScheme != rhs.eventReportScheme) {
        return false;
    }
    if (lhs.exponentialMaxPeriodInMs != rhs.exponentialMaxPeriodInMs) {
        return false;
    }
    if (lhs.exponentialBase != rhs.exponentialBase) {
        return false;
    }
    if (lhs.exponentialStepCount != rhs.exponentialStepCount) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanBucketParameters& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".basePeriodInMs = ";
    os += ::android::hardware::toString(o.basePeriodInMs);
    os += ", .maxApPerScan = ";
    os += ::android::hardware::toString(o.maxApPerScan);
    os += ", .reportThresholdPercent = ";
    os += ::android::hardware::toString(o.reportThresholdPercent);
    os += ", .reportThresholdNumScans = ";
    os += ::android::hardware::toString(o.reportThresholdNumScans);
    os += ", .buckets = ";
    os += ::android::hardware::toString(o.buckets);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& rhs) {
    if (lhs.basePeriodInMs != rhs.basePeriodInMs) {
        return false;
    }
    if (lhs.maxApPerScan != rhs.maxApPerScan) {
        return false;
    }
    if (lhs.reportThresholdPercent != rhs.reportThresholdPercent) {
        return false;
    }
    if (lhs.reportThresholdNumScans != rhs.reportThresholdNumScans) {
        return false;
    }
    if (lhs.buckets != rhs.buckets) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& lhs, const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rxMpdu = ";
    os += ::android::hardware::toString(o.rxMpdu);
    os += ", .txMpdu = ";
    os += ::android::hardware::toString(o.txMpdu);
    os += ", .lostMpdu = ";
    os += ::android::hardware::toString(o.lostMpdu);
    os += ", .retries = ";
    os += ::android::hardware::toString(o.retries);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& rhs) {
    if (lhs.rxMpdu != rhs.rxMpdu) {
        return false;
    }
    if (lhs.txMpdu != rhs.txMpdu) {
        return false;
    }
    if (lhs.lostMpdu != rhs.lostMpdu) {
        return false;
    }
    if (lhs.retries != rhs.retries) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfacePacketStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".beaconRx = ";
    os += ::android::hardware::toString(o.beaconRx);
    os += ", .avgRssiMgmt = ";
    os += ::android::hardware::toString(o.avgRssiMgmt);
    os += ", .wmeBePktStats = ";
    os += ::android::hardware::wifi::V1_0::toString(o.wmeBePktStats);
    os += ", .wmeBkPktStats = ";
    os += ::android::hardware::wifi::V1_0::toString(o.wmeBkPktStats);
    os += ", .wmeViPktStats = ";
    os += ::android::hardware::wifi::V1_0::toString(o.wmeViPktStats);
    os += ", .wmeVoPktStats = ";
    os += ::android::hardware::wifi::V1_0::toString(o.wmeVoPktStats);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& rhs) {
    if (lhs.beaconRx != rhs.beaconRx) {
        return false;
    }
    if (lhs.avgRssiMgmt != rhs.avgRssiMgmt) {
        return false;
    }
    if (lhs.wmeBePktStats != rhs.wmeBePktStats) {
        return false;
    }
    if (lhs.wmeBkPktStats != rhs.wmeBkPktStats) {
        return false;
    }
    if (lhs.wmeViPktStats != rhs.wmeViPktStats) {
        return false;
    }
    if (lhs.wmeVoPktStats != rhs.wmeVoPktStats) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".onTimeInMs = ";
    os += ::android::hardware::toString(o.onTimeInMs);
    os += ", .txTimeInMs = ";
    os += ::android::hardware::toString(o.txTimeInMs);
    os += ", .txTimeInMsPerLevel = ";
    os += ::android::hardware::toString(o.txTimeInMsPerLevel);
    os += ", .rxTimeInMs = ";
    os += ::android::hardware::toString(o.rxTimeInMs);
    os += ", .onTimeInMsForScan = ";
    os += ::android::hardware::toString(o.onTimeInMsForScan);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& rhs) {
    if (lhs.onTimeInMs != rhs.onTimeInMs) {
        return false;
    }
    if (lhs.txTimeInMs != rhs.txTimeInMs) {
        return false;
    }
    if (lhs.txTimeInMsPerLevel != rhs.txTimeInMsPerLevel) {
        return false;
    }
    if (lhs.rxTimeInMs != rhs.rxTimeInMs) {
        return false;
    }
    if (lhs.onTimeInMsForScan != rhs.onTimeInMsForScan) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".iface = ";
    os += ::android::hardware::wifi::V1_0::toString(o.iface);
    os += ", .radios = ";
    os += ::android::hardware::toString(o.radios);
    os += ", .timeStampInMs = ";
    os += ::android::hardware::toString(o.timeStampInMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerStats& rhs) {
    if (lhs.iface != rhs.iface) {
        return false;
    }
    if (lhs.radios != rhs.radios) {
        return false;
    }
    if (lhs.timeStampInMs != rhs.timeStampInMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_0::StaLinkLayerStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaScanResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timeStampInUs = ";
    os += ::android::hardware::toString(o.timeStampInUs);
    os += ", .ssid = ";
    os += ::android::hardware::toString(o.ssid);
    os += ", .bssid = ";
    os += ::android::hardware::toString(o.bssid);
    os += ", .rssi = ";
    os += ::android::hardware::toString(o.rssi);
    os += ", .frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .beaconPeriodInMs = ";
    os += ::android::hardware::toString(o.beaconPeriodInMs);
    os += ", .capability = ";
    os += ::android::hardware::toString(o.capability);
    os += ", .informationElements = ";
    os += ::android::hardware::toString(o.informationElements);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaScanResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaScanResult& lhs, const ::android::hardware::wifi::V1_0::StaScanResult& rhs) {
    if (lhs.timeStampInUs != rhs.timeStampInUs) {
        return false;
    }
    if (lhs.ssid != rhs.ssid) {
        return false;
    }
    if (lhs.bssid != rhs.bssid) {
        return false;
    }
    if (lhs.rssi != rhs.rssi) {
        return false;
    }
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.beaconPeriodInMs != rhs.beaconPeriodInMs) {
        return false;
    }
    if (lhs.capability != rhs.capability) {
        return false;
    }
    if (lhs.informationElements != rhs.informationElements) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaScanResult& lhs, const ::android::hardware::wifi::V1_0::StaScanResult& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::StaScanDataFlagMask>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::StaScanDataFlagMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::StaScanDataFlagMask::INTERRUPTED) == static_cast<int32_t>(::android::hardware::wifi::V1_0::StaScanDataFlagMask::INTERRUPTED)) {
        os += (first ? "" : " | ");
        os += "INTERRUPTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaScanDataFlagMask::INTERRUPTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::StaScanDataFlagMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::StaScanDataFlagMask::INTERRUPTED) {
        return "INTERRUPTED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::StaScanDataFlagMask o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaScanData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".flags = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::StaScanDataFlagMask>(o.flags);
    os += ", .bucketsScanned = ";
    os += ::android::hardware::toString(o.bucketsScanned);
    os += ", .results = ";
    os += ::android::hardware::toString(o.results);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaScanData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaScanData& lhs, const ::android::hardware::wifi::V1_0::StaScanData& rhs) {
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.bucketsScanned != rhs.bucketsScanned) {
        return false;
    }
    if (lhs.results != rhs.results) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaScanData& lhs, const ::android::hardware::wifi::V1_0::StaScanData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".maxBlacklistSize = ";
    os += ::android::hardware::toString(o.maxBlacklistSize);
    os += ", .maxWhitelistSize = ";
    os += ::android::hardware::toString(o.maxWhitelistSize);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& rhs) {
    if (lhs.maxBlacklistSize != rhs.maxBlacklistSize) {
        return false;
    }
    if (lhs.maxWhitelistSize != rhs.maxWhitelistSize) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& lhs, const ::android::hardware::wifi::V1_0::StaRoamingCapabilities& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::StaRoamingConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bssidBlacklist = ";
    os += ::android::hardware::toString(o.bssidBlacklist);
    os += ", .ssidWhitelist = ";
    os += ::android::hardware::toString(o.ssidWhitelist);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::StaRoamingConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::StaRoamingConfig& lhs, const ::android::hardware::wifi::V1_0::StaRoamingConfig& rhs) {
    if (lhs.bssidBlacklist != rhs.bssidBlacklist) {
        return false;
    }
    if (lhs.ssidWhitelist != rhs.ssidWhitelist) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::StaRoamingConfig& lhs, const ::android::hardware::wifi::V1_0::StaRoamingConfig& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::StaRoamingState>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::StaRoamingState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::StaRoamingState::DISABLED) == static_cast<uint8_t>(::android::hardware::wifi::V1_0::StaRoamingState::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaRoamingState::DISABLED;
    }
    if ((o & ::android::hardware::wifi::V1_0::StaRoamingState::ENABLED) == static_cast<uint8_t>(::android::hardware::wifi::V1_0::StaRoamingState::ENABLED)) {
        os += (first ? "" : " | ");
        os += "ENABLED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::StaRoamingState::ENABLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::StaRoamingState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::StaRoamingState::DISABLED) {
        return "DISABLED";
    }
    if (o == ::android::hardware::wifi::V1_0::StaRoamingState::ENABLED) {
        return "ENABLED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::StaRoamingState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanParamSizeLimits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanParamSizeLimits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanParamSizeLimits::MIN_PASSPHRASE_LENGTH) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanParamSizeLimits::MIN_PASSPHRASE_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MIN_PASSPHRASE_LENGTH";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanParamSizeLimits::MIN_PASSPHRASE_LENGTH;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanParamSizeLimits::MAX_PASSPHRASE_LENGTH) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanParamSizeLimits::MAX_PASSPHRASE_LENGTH)) {
        os += (first ? "" : " | ");
        os += "MAX_PASSPHRASE_LENGTH";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanParamSizeLimits::MAX_PASSPHRASE_LENGTH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanParamSizeLimits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanParamSizeLimits::MIN_PASSPHRASE_LENGTH) {
        return "MIN_PASSPHRASE_LENGTH";
    }
    if (o == ::android::hardware::wifi::V1_0::NanParamSizeLimits::MAX_PASSPHRASE_LENGTH) {
        return "MAX_PASSPHRASE_LENGTH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanParamSizeLimits o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanStatusType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanStatusType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::INTERNAL_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::INTERNAL_FAILURE)) {
        os += (first ? "" : " | ");
        os += "INTERNAL_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::INTERNAL_FAILURE;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::PROTOCOL_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::PROTOCOL_FAILURE)) {
        os += (first ? "" : " | ");
        os += "PROTOCOL_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::PROTOCOL_FAILURE;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::INVALID_SESSION_ID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::INVALID_SESSION_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_SESSION_ID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::INVALID_SESSION_ID;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::NO_RESOURCES_AVAILABLE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::NO_RESOURCES_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NO_RESOURCES_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::NO_RESOURCES_AVAILABLE;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::INVALID_ARGS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::INVALID_ARGS)) {
        os += (first ? "" : " | ");
        os += "INVALID_ARGS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::INVALID_ARGS;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::INVALID_PEER_ID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::INVALID_PEER_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_PEER_ID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::INVALID_PEER_ID;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::INVALID_NDP_ID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::INVALID_NDP_ID)) {
        os += (first ? "" : " | ");
        os += "INVALID_NDP_ID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::INVALID_NDP_ID;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::NAN_NOT_ALLOWED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::NAN_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "NAN_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::NAN_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::NO_OTA_ACK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::NO_OTA_ACK)) {
        os += (first ? "" : " | ");
        os += "NO_OTA_ACK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::NO_OTA_ACK;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::ALREADY_ENABLED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::ALREADY_ENABLED)) {
        os += (first ? "" : " | ");
        os += "ALREADY_ENABLED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::ALREADY_ENABLED;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::FOLLOWUP_TX_QUEUE_FULL) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::FOLLOWUP_TX_QUEUE_FULL)) {
        os += (first ? "" : " | ");
        os += "FOLLOWUP_TX_QUEUE_FULL";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::FOLLOWUP_TX_QUEUE_FULL;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanStatusType::UNSUPPORTED_CONCURRENCY_NAN_DISABLED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanStatusType::UNSUPPORTED_CONCURRENCY_NAN_DISABLED)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_CONCURRENCY_NAN_DISABLED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanStatusType::UNSUPPORTED_CONCURRENCY_NAN_DISABLED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanStatusType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::INTERNAL_FAILURE) {
        return "INTERNAL_FAILURE";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::PROTOCOL_FAILURE) {
        return "PROTOCOL_FAILURE";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::INVALID_SESSION_ID) {
        return "INVALID_SESSION_ID";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::NO_RESOURCES_AVAILABLE) {
        return "NO_RESOURCES_AVAILABLE";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::INVALID_ARGS) {
        return "INVALID_ARGS";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::INVALID_PEER_ID) {
        return "INVALID_PEER_ID";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::INVALID_NDP_ID) {
        return "INVALID_NDP_ID";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::NAN_NOT_ALLOWED) {
        return "NAN_NOT_ALLOWED";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::NO_OTA_ACK) {
        return "NO_OTA_ACK";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::ALREADY_ENABLED) {
        return "ALREADY_ENABLED";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::FOLLOWUP_TX_QUEUE_FULL) {
        return "FOLLOWUP_TX_QUEUE_FULL";
    }
    if (o == ::android::hardware::wifi::V1_0::NanStatusType::UNSUPPORTED_CONCURRENCY_NAN_DISABLED) {
        return "UNSUPPORTED_CONCURRENCY_NAN_DISABLED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanStatusType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanBandIndex>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanBandIndex> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_24GHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_24GHZ)) {
        os += (first ? "" : " | ");
        os += "NAN_BAND_24GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_24GHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_5GHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_5GHZ)) {
        os += (first ? "" : " | ");
        os += "NAN_BAND_5GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_5GHZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanBandIndex o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_24GHZ) {
        return "NAN_BAND_24GHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_5GHZ) {
        return "NAN_BAND_5GHZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanBandIndex o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiNanStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::wifi::V1_0::toString(o.status);
    os += ", .description = ";
    os += ::android::hardware::toString(o.description);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiNanStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiNanStatus& lhs, const ::android::hardware::wifi::V1_0::WifiNanStatus& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.description != rhs.description) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiNanStatus& lhs, const ::android::hardware::wifi::V1_0::WifiNanStatus& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanMatchAlg>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanMatchAlg> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_ONCE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_ONCE)) {
        os += (first ? "" : " | ");
        os += "MATCH_ONCE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_ONCE;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_CONTINUOUS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_CONTINUOUS)) {
        os += (first ? "" : " | ");
        os += "MATCH_CONTINUOUS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_CONTINUOUS;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_NEVER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_NEVER)) {
        os += (first ? "" : " | ");
        os += "MATCH_NEVER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_NEVER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanMatchAlg o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_ONCE) {
        return "MATCH_ONCE";
    }
    if (o == ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_CONTINUOUS) {
        return "MATCH_CONTINUOUS";
    }
    if (o == ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_NEVER) {
        return "MATCH_NEVER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanMatchAlg o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanPublishType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanPublishType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED)) {
        os += (first ? "" : " | ");
        os += "UNSOLICITED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanPublishType::SOLICITED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanPublishType::SOLICITED)) {
        os += (first ? "" : " | ");
        os += "SOLICITED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanPublishType::SOLICITED;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED_SOLICITED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED_SOLICITED)) {
        os += (first ? "" : " | ");
        os += "UNSOLICITED_SOLICITED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED_SOLICITED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanPublishType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED) {
        return "UNSOLICITED";
    }
    if (o == ::android::hardware::wifi::V1_0::NanPublishType::SOLICITED) {
        return "SOLICITED";
    }
    if (o == ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED_SOLICITED) {
        return "UNSOLICITED_SOLICITED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanPublishType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanTxType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanTxType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanTxType::BROADCAST) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanTxType::BROADCAST)) {
        os += (first ? "" : " | ");
        os += "BROADCAST";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanTxType::BROADCAST;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanTxType::UNICAST) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanTxType::UNICAST)) {
        os += (first ? "" : " | ");
        os += "UNICAST";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanTxType::UNICAST;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanTxType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanTxType::BROADCAST) {
        return "BROADCAST";
    }
    if (o == ::android::hardware::wifi::V1_0::NanTxType::UNICAST) {
        return "UNICAST";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanTxType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanSubscribeType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanSubscribeType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanSubscribeType::PASSIVE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanSubscribeType::PASSIVE)) {
        os += (first ? "" : " | ");
        os += "PASSIVE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanSubscribeType::PASSIVE;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanSubscribeType::ACTIVE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanSubscribeType::ACTIVE)) {
        os += (first ? "" : " | ");
        os += "ACTIVE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanSubscribeType::ACTIVE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanSubscribeType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanSubscribeType::PASSIVE) {
        return "PASSIVE";
    }
    if (o == ::android::hardware::wifi::V1_0::NanSubscribeType::ACTIVE) {
        return "ACTIVE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanSubscribeType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanSrfType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanSrfType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanSrfType::BLOOM_FILTER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanSrfType::BLOOM_FILTER)) {
        os += (first ? "" : " | ");
        os += "BLOOM_FILTER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanSrfType::BLOOM_FILTER;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanSrfType::PARTIAL_MAC_ADDR) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanSrfType::PARTIAL_MAC_ADDR)) {
        os += (first ? "" : " | ");
        os += "PARTIAL_MAC_ADDR";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanSrfType::PARTIAL_MAC_ADDR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanSrfType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanSrfType::BLOOM_FILTER) {
        return "BLOOM_FILTER";
    }
    if (o == ::android::hardware::wifi::V1_0::NanSrfType::PARTIAL_MAC_ADDR) {
        return "PARTIAL_MAC_ADDR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanSrfType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanDataPathChannelCfg>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanDataPathChannelCfg> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::CHANNEL_NOT_REQUESTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanDataPathChannelCfg::CHANNEL_NOT_REQUESTED)) {
        os += (first ? "" : " | ");
        os += "CHANNEL_NOT_REQUESTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::CHANNEL_NOT_REQUESTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::REQUEST_CHANNEL_SETUP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanDataPathChannelCfg::REQUEST_CHANNEL_SETUP)) {
        os += (first ? "" : " | ");
        os += "REQUEST_CHANNEL_SETUP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::REQUEST_CHANNEL_SETUP;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::FORCE_CHANNEL_SETUP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanDataPathChannelCfg::FORCE_CHANNEL_SETUP)) {
        os += (first ? "" : " | ");
        os += "FORCE_CHANNEL_SETUP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::FORCE_CHANNEL_SETUP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanDataPathChannelCfg o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::CHANNEL_NOT_REQUESTED) {
        return "CHANNEL_NOT_REQUESTED";
    }
    if (o == ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::REQUEST_CHANNEL_SETUP) {
        return "REQUEST_CHANNEL_SETUP";
    }
    if (o == ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::FORCE_CHANNEL_SETUP) {
        return "FORCE_CHANNEL_SETUP";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanDataPathChannelCfg o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanDataPathSecurityType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanDataPathSecurityType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanDataPathSecurityType::OPEN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanDataPathSecurityType::OPEN)) {
        os += (first ? "" : " | ");
        os += "OPEN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanDataPathSecurityType::OPEN;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PMK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanDataPathSecurityType::PMK)) {
        os += (first ? "" : " | ");
        os += "PMK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PMK;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PASSPHRASE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanDataPathSecurityType::PASSPHRASE)) {
        os += (first ? "" : " | ");
        os += "PASSPHRASE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PASSPHRASE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanDataPathSecurityType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanDataPathSecurityType::OPEN) {
        return "OPEN";
    }
    if (o == ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PMK) {
        return "PMK";
    }
    if (o == ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PASSPHRASE) {
        return "PASSPHRASE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanDataPathSecurityType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rssiClose = ";
    os += ::android::hardware::toString(o.rssiClose);
    os += ", .rssiMiddle = ";
    os += ::android::hardware::toString(o.rssiMiddle);
    os += ", .rssiCloseProximity = ";
    os += ::android::hardware::toString(o.rssiCloseProximity);
    os += ", .dwellTimeMs = ";
    os += ::android::hardware::toString(o.dwellTimeMs);
    os += ", .scanPeriodSec = ";
    os += ::android::hardware::toString(o.scanPeriodSec);
    os += ", .validDiscoveryWindowIntervalVal = ";
    os += ::android::hardware::toString(o.validDiscoveryWindowIntervalVal);
    os += ", .discoveryWindowIntervalVal = ";
    os += ::android::hardware::toString(o.discoveryWindowIntervalVal);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& lhs, const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& rhs) {
    if (lhs.rssiClose != rhs.rssiClose) {
        return false;
    }
    if (lhs.rssiMiddle != rhs.rssiMiddle) {
        return false;
    }
    if (lhs.rssiCloseProximity != rhs.rssiCloseProximity) {
        return false;
    }
    if (lhs.dwellTimeMs != rhs.dwellTimeMs) {
        return false;
    }
    if (lhs.scanPeriodSec != rhs.scanPeriodSec) {
        return false;
    }
    if (lhs.validDiscoveryWindowIntervalVal != rhs.validDiscoveryWindowIntervalVal) {
        return false;
    }
    if (lhs.discoveryWindowIntervalVal != rhs.discoveryWindowIntervalVal) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& lhs, const ::android::hardware::wifi::V1_0::NanBandSpecificConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDebugConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".validClusterIdVals = ";
    os += ::android::hardware::toString(o.validClusterIdVals);
    os += ", .clusterIdBottomRangeVal = ";
    os += ::android::hardware::toString(o.clusterIdBottomRangeVal);
    os += ", .clusterIdTopRangeVal = ";
    os += ::android::hardware::toString(o.clusterIdTopRangeVal);
    os += ", .validIntfAddrVal = ";
    os += ::android::hardware::toString(o.validIntfAddrVal);
    os += ", .intfAddrVal = ";
    os += ::android::hardware::toString(o.intfAddrVal);
    os += ", .validOuiVal = ";
    os += ::android::hardware::toString(o.validOuiVal);
    os += ", .ouiVal = ";
    os += ::android::hardware::toString(o.ouiVal);
    os += ", .validRandomFactorForceVal = ";
    os += ::android::hardware::toString(o.validRandomFactorForceVal);
    os += ", .randomFactorForceVal = ";
    os += ::android::hardware::toString(o.randomFactorForceVal);
    os += ", .validHopCountForceVal = ";
    os += ::android::hardware::toString(o.validHopCountForceVal);
    os += ", .hopCountForceVal = ";
    os += ::android::hardware::toString(o.hopCountForceVal);
    os += ", .validDiscoveryChannelVal = ";
    os += ::android::hardware::toString(o.validDiscoveryChannelVal);
    os += ", .discoveryChannelMhzVal = ";
    os += ::android::hardware::toString(o.discoveryChannelMhzVal);
    os += ", .validUseBeaconsInBandVal = ";
    os += ::android::hardware::toString(o.validUseBeaconsInBandVal);
    os += ", .useBeaconsInBandVal = ";
    os += ::android::hardware::toString(o.useBeaconsInBandVal);
    os += ", .validUseSdfInBandVal = ";
    os += ::android::hardware::toString(o.validUseSdfInBandVal);
    os += ", .useSdfInBandVal = ";
    os += ::android::hardware::toString(o.useSdfInBandVal);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDebugConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDebugConfig& lhs, const ::android::hardware::wifi::V1_0::NanDebugConfig& rhs) {
    if (lhs.validClusterIdVals != rhs.validClusterIdVals) {
        return false;
    }
    if (lhs.clusterIdBottomRangeVal != rhs.clusterIdBottomRangeVal) {
        return false;
    }
    if (lhs.clusterIdTopRangeVal != rhs.clusterIdTopRangeVal) {
        return false;
    }
    if (lhs.validIntfAddrVal != rhs.validIntfAddrVal) {
        return false;
    }
    if (lhs.intfAddrVal != rhs.intfAddrVal) {
        return false;
    }
    if (lhs.validOuiVal != rhs.validOuiVal) {
        return false;
    }
    if (lhs.ouiVal != rhs.ouiVal) {
        return false;
    }
    if (lhs.validRandomFactorForceVal != rhs.validRandomFactorForceVal) {
        return false;
    }
    if (lhs.randomFactorForceVal != rhs.randomFactorForceVal) {
        return false;
    }
    if (lhs.validHopCountForceVal != rhs.validHopCountForceVal) {
        return false;
    }
    if (lhs.hopCountForceVal != rhs.hopCountForceVal) {
        return false;
    }
    if (lhs.validDiscoveryChannelVal != rhs.validDiscoveryChannelVal) {
        return false;
    }
    if (lhs.discoveryChannelMhzVal != rhs.discoveryChannelMhzVal) {
        return false;
    }
    if (lhs.validUseBeaconsInBandVal != rhs.validUseBeaconsInBandVal) {
        return false;
    }
    if (lhs.useBeaconsInBandVal != rhs.useBeaconsInBandVal) {
        return false;
    }
    if (lhs.validUseSdfInBandVal != rhs.validUseSdfInBandVal) {
        return false;
    }
    if (lhs.useSdfInBandVal != rhs.useSdfInBandVal) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDebugConfig& lhs, const ::android::hardware::wifi::V1_0::NanDebugConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanConfigRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".masterPref = ";
    os += ::android::hardware::toString(o.masterPref);
    os += ", .disableDiscoveryAddressChangeIndication = ";
    os += ::android::hardware::toString(o.disableDiscoveryAddressChangeIndication);
    os += ", .disableStartedClusterIndication = ";
    os += ::android::hardware::toString(o.disableStartedClusterIndication);
    os += ", .disableJoinedClusterIndication = ";
    os += ::android::hardware::toString(o.disableJoinedClusterIndication);
    os += ", .includePublishServiceIdsInBeacon = ";
    os += ::android::hardware::toString(o.includePublishServiceIdsInBeacon);
    os += ", .numberOfPublishServiceIdsInBeacon = ";
    os += ::android::hardware::toString(o.numberOfPublishServiceIdsInBeacon);
    os += ", .includeSubscribeServiceIdsInBeacon = ";
    os += ::android::hardware::toString(o.includeSubscribeServiceIdsInBeacon);
    os += ", .numberOfSubscribeServiceIdsInBeacon = ";
    os += ::android::hardware::toString(o.numberOfSubscribeServiceIdsInBeacon);
    os += ", .rssiWindowSize = ";
    os += ::android::hardware::toString(o.rssiWindowSize);
    os += ", .macAddressRandomizationIntervalSec = ";
    os += ::android::hardware::toString(o.macAddressRandomizationIntervalSec);
    os += ", .bandSpecificConfig = ";
    os += ::android::hardware::toString(o.bandSpecificConfig);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanConfigRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanConfigRequest& lhs, const ::android::hardware::wifi::V1_0::NanConfigRequest& rhs) {
    if (lhs.masterPref != rhs.masterPref) {
        return false;
    }
    if (lhs.disableDiscoveryAddressChangeIndication != rhs.disableDiscoveryAddressChangeIndication) {
        return false;
    }
    if (lhs.disableStartedClusterIndication != rhs.disableStartedClusterIndication) {
        return false;
    }
    if (lhs.disableJoinedClusterIndication != rhs.disableJoinedClusterIndication) {
        return false;
    }
    if (lhs.includePublishServiceIdsInBeacon != rhs.includePublishServiceIdsInBeacon) {
        return false;
    }
    if (lhs.numberOfPublishServiceIdsInBeacon != rhs.numberOfPublishServiceIdsInBeacon) {
        return false;
    }
    if (lhs.includeSubscribeServiceIdsInBeacon != rhs.includeSubscribeServiceIdsInBeacon) {
        return false;
    }
    if (lhs.numberOfSubscribeServiceIdsInBeacon != rhs.numberOfSubscribeServiceIdsInBeacon) {
        return false;
    }
    if (lhs.rssiWindowSize != rhs.rssiWindowSize) {
        return false;
    }
    if (lhs.macAddressRandomizationIntervalSec != rhs.macAddressRandomizationIntervalSec) {
        return false;
    }
    if (lhs.bandSpecificConfig != rhs.bandSpecificConfig) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanConfigRequest& lhs, const ::android::hardware::wifi::V1_0::NanConfigRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanEnableRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".operateInBand = ";
    os += ::android::hardware::toString(o.operateInBand);
    os += ", .hopCountMax = ";
    os += ::android::hardware::toString(o.hopCountMax);
    os += ", .configParams = ";
    os += ::android::hardware::wifi::V1_0::toString(o.configParams);
    os += ", .debugConfigs = ";
    os += ::android::hardware::wifi::V1_0::toString(o.debugConfigs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanEnableRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanEnableRequest& lhs, const ::android::hardware::wifi::V1_0::NanEnableRequest& rhs) {
    if (lhs.operateInBand != rhs.operateInBand) {
        return false;
    }
    if (lhs.hopCountMax != rhs.hopCountMax) {
        return false;
    }
    if (lhs.configParams != rhs.configParams) {
        return false;
    }
    if (lhs.debugConfigs != rhs.debugConfigs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanEnableRequest& lhs, const ::android::hardware::wifi::V1_0::NanEnableRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanCipherSuiteType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanCipherSuiteType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanCipherSuiteType::NONE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanCipherSuiteType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanCipherSuiteType::NONE;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_128_MASK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_128_MASK)) {
        os += (first ? "" : " | ");
        os += "SHARED_KEY_128_MASK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_128_MASK;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_256_MASK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_256_MASK)) {
        os += (first ? "" : " | ");
        os += "SHARED_KEY_256_MASK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_256_MASK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanCipherSuiteType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanCipherSuiteType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_128_MASK) {
        return "SHARED_KEY_128_MASK";
    }
    if (o == ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_256_MASK) {
        return "SHARED_KEY_256_MASK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanCipherSuiteType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanRangingIndication>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanRangingIndication> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanRangingIndication::CONTINUOUS_INDICATION_MASK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanRangingIndication::CONTINUOUS_INDICATION_MASK)) {
        os += (first ? "" : " | ");
        os += "CONTINUOUS_INDICATION_MASK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanRangingIndication::CONTINUOUS_INDICATION_MASK;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanRangingIndication::INGRESS_MET_MASK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanRangingIndication::INGRESS_MET_MASK)) {
        os += (first ? "" : " | ");
        os += "INGRESS_MET_MASK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanRangingIndication::INGRESS_MET_MASK;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanRangingIndication::EGRESS_MET_MASK) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanRangingIndication::EGRESS_MET_MASK)) {
        os += (first ? "" : " | ");
        os += "EGRESS_MET_MASK";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanRangingIndication::EGRESS_MET_MASK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanRangingIndication o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanRangingIndication::CONTINUOUS_INDICATION_MASK) {
        return "CONTINUOUS_INDICATION_MASK";
    }
    if (o == ::android::hardware::wifi::V1_0::NanRangingIndication::INGRESS_MET_MASK) {
        return "INGRESS_MET_MASK";
    }
    if (o == ::android::hardware::wifi::V1_0::NanRangingIndication::EGRESS_MET_MASK) {
        return "EGRESS_MET_MASK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanRangingIndication o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".securityType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.securityType);
    os += ", .cipherType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.cipherType);
    os += ", .pmk = ";
    os += ::android::hardware::toString(o.pmk);
    os += ", .passphrase = ";
    os += ::android::hardware::toString(o.passphrase);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& rhs) {
    if (lhs.securityType != rhs.securityType) {
        return false;
    }
    if (lhs.cipherType != rhs.cipherType) {
        return false;
    }
    if (lhs.pmk != rhs.pmk) {
        return false;
    }
    if (lhs.passphrase != rhs.passphrase) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& lhs, const ::android::hardware::wifi::V1_0::NanDataPathSecurityConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sessionId = ";
    os += ::android::hardware::toString(o.sessionId);
    os += ", .ttlSec = ";
    os += ::android::hardware::toString(o.ttlSec);
    os += ", .discoveryWindowPeriod = ";
    os += ::android::hardware::toString(o.discoveryWindowPeriod);
    os += ", .discoveryCount = ";
    os += ::android::hardware::toString(o.discoveryCount);
    os += ", .serviceName = ";
    os += ::android::hardware::toString(o.serviceName);
    os += ", .discoveryMatchIndicator = ";
    os += ::android::hardware::wifi::V1_0::toString(o.discoveryMatchIndicator);
    os += ", .serviceSpecificInfo = ";
    os += ::android::hardware::toString(o.serviceSpecificInfo);
    os += ", .extendedServiceSpecificInfo = ";
    os += ::android::hardware::toString(o.extendedServiceSpecificInfo);
    os += ", .rxMatchFilter = ";
    os += ::android::hardware::toString(o.rxMatchFilter);
    os += ", .txMatchFilter = ";
    os += ::android::hardware::toString(o.txMatchFilter);
    os += ", .useRssiThreshold = ";
    os += ::android::hardware::toString(o.useRssiThreshold);
    os += ", .disableDiscoveryTerminationIndication = ";
    os += ::android::hardware::toString(o.disableDiscoveryTerminationIndication);
    os += ", .disableMatchExpirationIndication = ";
    os += ::android::hardware::toString(o.disableMatchExpirationIndication);
    os += ", .disableFollowupReceivedIndication = ";
    os += ::android::hardware::toString(o.disableFollowupReceivedIndication);
    os += ", .securityConfig = ";
    os += ::android::hardware::wifi::V1_0::toString(o.securityConfig);
    os += ", .rangingRequired = ";
    os += ::android::hardware::toString(o.rangingRequired);
    os += ", .rangingIntervalMsec = ";
    os += ::android::hardware::toString(o.rangingIntervalMsec);
    os += ", .configRangingIndications = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::NanRangingIndication>(o.configRangingIndications);
    os += ", .distanceIngressCm = ";
    os += ::android::hardware::toString(o.distanceIngressCm);
    os += ", .distanceEgressCm = ";
    os += ::android::hardware::toString(o.distanceEgressCm);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& lhs, const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& rhs) {
    if (lhs.sessionId != rhs.sessionId) {
        return false;
    }
    if (lhs.ttlSec != rhs.ttlSec) {
        return false;
    }
    if (lhs.discoveryWindowPeriod != rhs.discoveryWindowPeriod) {
        return false;
    }
    if (lhs.discoveryCount != rhs.discoveryCount) {
        return false;
    }
    if (lhs.serviceName != rhs.serviceName) {
        return false;
    }
    if (lhs.discoveryMatchIndicator != rhs.discoveryMatchIndicator) {
        return false;
    }
    if (lhs.serviceSpecificInfo != rhs.serviceSpecificInfo) {
        return false;
    }
    if (lhs.extendedServiceSpecificInfo != rhs.extendedServiceSpecificInfo) {
        return false;
    }
    if (lhs.rxMatchFilter != rhs.rxMatchFilter) {
        return false;
    }
    if (lhs.txMatchFilter != rhs.txMatchFilter) {
        return false;
    }
    if (lhs.useRssiThreshold != rhs.useRssiThreshold) {
        return false;
    }
    if (lhs.disableDiscoveryTerminationIndication != rhs.disableDiscoveryTerminationIndication) {
        return false;
    }
    if (lhs.disableMatchExpirationIndication != rhs.disableMatchExpirationIndication) {
        return false;
    }
    if (lhs.disableFollowupReceivedIndication != rhs.disableFollowupReceivedIndication) {
        return false;
    }
    if (lhs.securityConfig != rhs.securityConfig) {
        return false;
    }
    if (lhs.rangingRequired != rhs.rangingRequired) {
        return false;
    }
    if (lhs.rangingIntervalMsec != rhs.rangingIntervalMsec) {
        return false;
    }
    if (lhs.configRangingIndications != rhs.configRangingIndications) {
        return false;
    }
    if (lhs.distanceIngressCm != rhs.distanceIngressCm) {
        return false;
    }
    if (lhs.distanceEgressCm != rhs.distanceEgressCm) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& lhs, const ::android::hardware::wifi::V1_0::NanDiscoveryCommonConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanPublishRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".baseConfigs = ";
    os += ::android::hardware::wifi::V1_0::toString(o.baseConfigs);
    os += ", .publishType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.publishType);
    os += ", .txType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.txType);
    os += ", .autoAcceptDataPathRequests = ";
    os += ::android::hardware::toString(o.autoAcceptDataPathRequests);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanPublishRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanPublishRequest& lhs, const ::android::hardware::wifi::V1_0::NanPublishRequest& rhs) {
    if (lhs.baseConfigs != rhs.baseConfigs) {
        return false;
    }
    if (lhs.publishType != rhs.publishType) {
        return false;
    }
    if (lhs.txType != rhs.txType) {
        return false;
    }
    if (lhs.autoAcceptDataPathRequests != rhs.autoAcceptDataPathRequests) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanPublishRequest& lhs, const ::android::hardware::wifi::V1_0::NanPublishRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".baseConfigs = ";
    os += ::android::hardware::wifi::V1_0::toString(o.baseConfigs);
    os += ", .subscribeType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.subscribeType);
    os += ", .srfType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.srfType);
    os += ", .srfRespondIfInAddressSet = ";
    os += ::android::hardware::toString(o.srfRespondIfInAddressSet);
    os += ", .shouldUseSrf = ";
    os += ::android::hardware::toString(o.shouldUseSrf);
    os += ", .isSsiRequiredForMatch = ";
    os += ::android::hardware::toString(o.isSsiRequiredForMatch);
    os += ", .intfAddr = ";
    os += ::android::hardware::toString(o.intfAddr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& lhs, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& rhs) {
    if (lhs.baseConfigs != rhs.baseConfigs) {
        return false;
    }
    if (lhs.subscribeType != rhs.subscribeType) {
        return false;
    }
    if (lhs.srfType != rhs.srfType) {
        return false;
    }
    if (lhs.srfRespondIfInAddressSet != rhs.srfRespondIfInAddressSet) {
        return false;
    }
    if (lhs.shouldUseSrf != rhs.shouldUseSrf) {
        return false;
    }
    if (lhs.isSsiRequiredForMatch != rhs.isSsiRequiredForMatch) {
        return false;
    }
    if (lhs.intfAddr != rhs.intfAddr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanSubscribeRequest& lhs, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".discoverySessionId = ";
    os += ::android::hardware::toString(o.discoverySessionId);
    os += ", .peerId = ";
    os += ::android::hardware::toString(o.peerId);
    os += ", .addr = ";
    os += ::android::hardware::toString(o.addr);
    os += ", .isHighPriority = ";
    os += ::android::hardware::toString(o.isHighPriority);
    os += ", .shouldUseDiscoveryWindow = ";
    os += ::android::hardware::toString(o.shouldUseDiscoveryWindow);
    os += ", .serviceSpecificInfo = ";
    os += ::android::hardware::toString(o.serviceSpecificInfo);
    os += ", .extendedServiceSpecificInfo = ";
    os += ::android::hardware::toString(o.extendedServiceSpecificInfo);
    os += ", .disableFollowupResultIndication = ";
    os += ::android::hardware::toString(o.disableFollowupResultIndication);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& lhs, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& rhs) {
    if (lhs.discoverySessionId != rhs.discoverySessionId) {
        return false;
    }
    if (lhs.peerId != rhs.peerId) {
        return false;
    }
    if (lhs.addr != rhs.addr) {
        return false;
    }
    if (lhs.isHighPriority != rhs.isHighPriority) {
        return false;
    }
    if (lhs.shouldUseDiscoveryWindow != rhs.shouldUseDiscoveryWindow) {
        return false;
    }
    if (lhs.serviceSpecificInfo != rhs.serviceSpecificInfo) {
        return false;
    }
    if (lhs.extendedServiceSpecificInfo != rhs.extendedServiceSpecificInfo) {
        return false;
    }
    if (lhs.disableFollowupResultIndication != rhs.disableFollowupResultIndication) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& lhs, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".peerId = ";
    os += ::android::hardware::toString(o.peerId);
    os += ", .peerDiscMacAddr = ";
    os += ::android::hardware::toString(o.peerDiscMacAddr);
    os += ", .channelRequestType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.channelRequestType);
    os += ", .channel = ";
    os += ::android::hardware::toString(o.channel);
    os += ", .ifaceName = ";
    os += ::android::hardware::toString(o.ifaceName);
    os += ", .securityConfig = ";
    os += ::android::hardware::wifi::V1_0::toString(o.securityConfig);
    os += ", .appInfo = ";
    os += ::android::hardware::toString(o.appInfo);
    os += ", .serviceNameOutOfBand = ";
    os += ::android::hardware::toString(o.serviceNameOutOfBand);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& lhs, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& rhs) {
    if (lhs.peerId != rhs.peerId) {
        return false;
    }
    if (lhs.peerDiscMacAddr != rhs.peerDiscMacAddr) {
        return false;
    }
    if (lhs.channelRequestType != rhs.channelRequestType) {
        return false;
    }
    if (lhs.channel != rhs.channel) {
        return false;
    }
    if (lhs.ifaceName != rhs.ifaceName) {
        return false;
    }
    if (lhs.securityConfig != rhs.securityConfig) {
        return false;
    }
    if (lhs.appInfo != rhs.appInfo) {
        return false;
    }
    if (lhs.serviceNameOutOfBand != rhs.serviceNameOutOfBand) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& lhs, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".acceptRequest = ";
    os += ::android::hardware::toString(o.acceptRequest);
    os += ", .ndpInstanceId = ";
    os += ::android::hardware::toString(o.ndpInstanceId);
    os += ", .ifaceName = ";
    os += ::android::hardware::toString(o.ifaceName);
    os += ", .securityConfig = ";
    os += ::android::hardware::wifi::V1_0::toString(o.securityConfig);
    os += ", .appInfo = ";
    os += ::android::hardware::toString(o.appInfo);
    os += ", .serviceNameOutOfBand = ";
    os += ::android::hardware::toString(o.serviceNameOutOfBand);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& lhs, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& rhs) {
    if (lhs.acceptRequest != rhs.acceptRequest) {
        return false;
    }
    if (lhs.ndpInstanceId != rhs.ndpInstanceId) {
        return false;
    }
    if (lhs.ifaceName != rhs.ifaceName) {
        return false;
    }
    if (lhs.securityConfig != rhs.securityConfig) {
        return false;
    }
    if (lhs.appInfo != rhs.appInfo) {
        return false;
    }
    if (lhs.serviceNameOutOfBand != rhs.serviceNameOutOfBand) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& lhs, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".maxConcurrentClusters = ";
    os += ::android::hardware::toString(o.maxConcurrentClusters);
    os += ", .maxPublishes = ";
    os += ::android::hardware::toString(o.maxPublishes);
    os += ", .maxSubscribes = ";
    os += ::android::hardware::toString(o.maxSubscribes);
    os += ", .maxServiceNameLen = ";
    os += ::android::hardware::toString(o.maxServiceNameLen);
    os += ", .maxMatchFilterLen = ";
    os += ::android::hardware::toString(o.maxMatchFilterLen);
    os += ", .maxTotalMatchFilterLen = ";
    os += ::android::hardware::toString(o.maxTotalMatchFilterLen);
    os += ", .maxServiceSpecificInfoLen = ";
    os += ::android::hardware::toString(o.maxServiceSpecificInfoLen);
    os += ", .maxExtendedServiceSpecificInfoLen = ";
    os += ::android::hardware::toString(o.maxExtendedServiceSpecificInfoLen);
    os += ", .maxNdiInterfaces = ";
    os += ::android::hardware::toString(o.maxNdiInterfaces);
    os += ", .maxNdpSessions = ";
    os += ::android::hardware::toString(o.maxNdpSessions);
    os += ", .maxAppInfoLen = ";
    os += ::android::hardware::toString(o.maxAppInfoLen);
    os += ", .maxQueuedTransmitFollowupMsgs = ";
    os += ::android::hardware::toString(o.maxQueuedTransmitFollowupMsgs);
    os += ", .maxSubscribeInterfaceAddresses = ";
    os += ::android::hardware::toString(o.maxSubscribeInterfaceAddresses);
    os += ", .supportedCipherSuites = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::NanCipherSuiteType>(o.supportedCipherSuites);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanCapabilities& lhs, const ::android::hardware::wifi::V1_0::NanCapabilities& rhs) {
    if (lhs.maxConcurrentClusters != rhs.maxConcurrentClusters) {
        return false;
    }
    if (lhs.maxPublishes != rhs.maxPublishes) {
        return false;
    }
    if (lhs.maxSubscribes != rhs.maxSubscribes) {
        return false;
    }
    if (lhs.maxServiceNameLen != rhs.maxServiceNameLen) {
        return false;
    }
    if (lhs.maxMatchFilterLen != rhs.maxMatchFilterLen) {
        return false;
    }
    if (lhs.maxTotalMatchFilterLen != rhs.maxTotalMatchFilterLen) {
        return false;
    }
    if (lhs.maxServiceSpecificInfoLen != rhs.maxServiceSpecificInfoLen) {
        return false;
    }
    if (lhs.maxExtendedServiceSpecificInfoLen != rhs.maxExtendedServiceSpecificInfoLen) {
        return false;
    }
    if (lhs.maxNdiInterfaces != rhs.maxNdiInterfaces) {
        return false;
    }
    if (lhs.maxNdpSessions != rhs.maxNdpSessions) {
        return false;
    }
    if (lhs.maxAppInfoLen != rhs.maxAppInfoLen) {
        return false;
    }
    if (lhs.maxQueuedTransmitFollowupMsgs != rhs.maxQueuedTransmitFollowupMsgs) {
        return false;
    }
    if (lhs.maxSubscribeInterfaceAddresses != rhs.maxSubscribeInterfaceAddresses) {
        return false;
    }
    if (lhs.supportedCipherSuites != rhs.supportedCipherSuites) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanCapabilities& lhs, const ::android::hardware::wifi::V1_0::NanCapabilities& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanMatchInd& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".discoverySessionId = ";
    os += ::android::hardware::toString(o.discoverySessionId);
    os += ", .peerId = ";
    os += ::android::hardware::toString(o.peerId);
    os += ", .addr = ";
    os += ::android::hardware::toString(o.addr);
    os += ", .serviceSpecificInfo = ";
    os += ::android::hardware::toString(o.serviceSpecificInfo);
    os += ", .extendedServiceSpecificInfo = ";
    os += ::android::hardware::toString(o.extendedServiceSpecificInfo);
    os += ", .matchFilter = ";
    os += ::android::hardware::toString(o.matchFilter);
    os += ", .matchOccuredInBeaconFlag = ";
    os += ::android::hardware::toString(o.matchOccuredInBeaconFlag);
    os += ", .outOfResourceFlag = ";
    os += ::android::hardware::toString(o.outOfResourceFlag);
    os += ", .rssiValue = ";
    os += ::android::hardware::toString(o.rssiValue);
    os += ", .peerCipherType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.peerCipherType);
    os += ", .peerRequiresSecurityEnabledInNdp = ";
    os += ::android::hardware::toString(o.peerRequiresSecurityEnabledInNdp);
    os += ", .peerRequiresRanging = ";
    os += ::android::hardware::toString(o.peerRequiresRanging);
    os += ", .rangingMeasurementInCm = ";
    os += ::android::hardware::toString(o.rangingMeasurementInCm);
    os += ", .rangingIndicationType = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::NanRangingIndication>(o.rangingIndicationType);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanMatchInd& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanMatchInd& lhs, const ::android::hardware::wifi::V1_0::NanMatchInd& rhs) {
    if (lhs.discoverySessionId != rhs.discoverySessionId) {
        return false;
    }
    if (lhs.peerId != rhs.peerId) {
        return false;
    }
    if (lhs.addr != rhs.addr) {
        return false;
    }
    if (lhs.serviceSpecificInfo != rhs.serviceSpecificInfo) {
        return false;
    }
    if (lhs.extendedServiceSpecificInfo != rhs.extendedServiceSpecificInfo) {
        return false;
    }
    if (lhs.matchFilter != rhs.matchFilter) {
        return false;
    }
    if (lhs.matchOccuredInBeaconFlag != rhs.matchOccuredInBeaconFlag) {
        return false;
    }
    if (lhs.outOfResourceFlag != rhs.outOfResourceFlag) {
        return false;
    }
    if (lhs.rssiValue != rhs.rssiValue) {
        return false;
    }
    if (lhs.peerCipherType != rhs.peerCipherType) {
        return false;
    }
    if (lhs.peerRequiresSecurityEnabledInNdp != rhs.peerRequiresSecurityEnabledInNdp) {
        return false;
    }
    if (lhs.peerRequiresRanging != rhs.peerRequiresRanging) {
        return false;
    }
    if (lhs.rangingMeasurementInCm != rhs.rangingMeasurementInCm) {
        return false;
    }
    if (lhs.rangingIndicationType != rhs.rangingIndicationType) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanMatchInd& lhs, const ::android::hardware::wifi::V1_0::NanMatchInd& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".discoverySessionId = ";
    os += ::android::hardware::toString(o.discoverySessionId);
    os += ", .peerId = ";
    os += ::android::hardware::toString(o.peerId);
    os += ", .addr = ";
    os += ::android::hardware::toString(o.addr);
    os += ", .receivedInFaw = ";
    os += ::android::hardware::toString(o.receivedInFaw);
    os += ", .serviceSpecificInfo = ";
    os += ::android::hardware::toString(o.serviceSpecificInfo);
    os += ", .extendedServiceSpecificInfo = ";
    os += ::android::hardware::toString(o.extendedServiceSpecificInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& lhs, const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& rhs) {
    if (lhs.discoverySessionId != rhs.discoverySessionId) {
        return false;
    }
    if (lhs.peerId != rhs.peerId) {
        return false;
    }
    if (lhs.addr != rhs.addr) {
        return false;
    }
    if (lhs.receivedInFaw != rhs.receivedInFaw) {
        return false;
    }
    if (lhs.serviceSpecificInfo != rhs.serviceSpecificInfo) {
        return false;
    }
    if (lhs.extendedServiceSpecificInfo != rhs.extendedServiceSpecificInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& lhs, const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::NanClusterEventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::NanClusterEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::NanClusterEventType::DISCOVERY_MAC_ADDRESS_CHANGED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanClusterEventType::DISCOVERY_MAC_ADDRESS_CHANGED)) {
        os += (first ? "" : " | ");
        os += "DISCOVERY_MAC_ADDRESS_CHANGED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanClusterEventType::DISCOVERY_MAC_ADDRESS_CHANGED;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanClusterEventType::STARTED_CLUSTER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanClusterEventType::STARTED_CLUSTER)) {
        os += (first ? "" : " | ");
        os += "STARTED_CLUSTER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanClusterEventType::STARTED_CLUSTER;
    }
    if ((o & ::android::hardware::wifi::V1_0::NanClusterEventType::JOINED_CLUSTER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::NanClusterEventType::JOINED_CLUSTER)) {
        os += (first ? "" : " | ");
        os += "JOINED_CLUSTER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::NanClusterEventType::JOINED_CLUSTER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::NanClusterEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::NanClusterEventType::DISCOVERY_MAC_ADDRESS_CHANGED) {
        return "DISCOVERY_MAC_ADDRESS_CHANGED";
    }
    if (o == ::android::hardware::wifi::V1_0::NanClusterEventType::STARTED_CLUSTER) {
        return "STARTED_CLUSTER";
    }
    if (o == ::android::hardware::wifi::V1_0::NanClusterEventType::JOINED_CLUSTER) {
        return "JOINED_CLUSTER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::NanClusterEventType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanClusterEventInd& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".eventType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.eventType);
    os += ", .addr = ";
    os += ::android::hardware::toString(o.addr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanClusterEventInd& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanClusterEventInd& lhs, const ::android::hardware::wifi::V1_0::NanClusterEventInd& rhs) {
    if (lhs.eventType != rhs.eventType) {
        return false;
    }
    if (lhs.addr != rhs.addr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanClusterEventInd& lhs, const ::android::hardware::wifi::V1_0::NanClusterEventInd& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".discoverySessionId = ";
    os += ::android::hardware::toString(o.discoverySessionId);
    os += ", .peerDiscMacAddr = ";
    os += ::android::hardware::toString(o.peerDiscMacAddr);
    os += ", .ndpInstanceId = ";
    os += ::android::hardware::toString(o.ndpInstanceId);
    os += ", .securityRequired = ";
    os += ::android::hardware::toString(o.securityRequired);
    os += ", .appInfo = ";
    os += ::android::hardware::toString(o.appInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& rhs) {
    if (lhs.discoverySessionId != rhs.discoverySessionId) {
        return false;
    }
    if (lhs.peerDiscMacAddr != rhs.peerDiscMacAddr) {
        return false;
    }
    if (lhs.ndpInstanceId != rhs.ndpInstanceId) {
        return false;
    }
    if (lhs.securityRequired != rhs.securityRequired) {
        return false;
    }
    if (lhs.appInfo != rhs.appInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ndpInstanceId = ";
    os += ::android::hardware::toString(o.ndpInstanceId);
    os += ", .dataPathSetupSuccess = ";
    os += ::android::hardware::toString(o.dataPathSetupSuccess);
    os += ", .peerNdiMacAddr = ";
    os += ::android::hardware::toString(o.peerNdiMacAddr);
    os += ", .appInfo = ";
    os += ::android::hardware::toString(o.appInfo);
    os += ", .status = ";
    os += ::android::hardware::wifi::V1_0::toString(o.status);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& rhs) {
    if (lhs.ndpInstanceId != rhs.ndpInstanceId) {
        return false;
    }
    if (lhs.dataPathSetupSuccess != rhs.dataPathSetupSuccess) {
        return false;
    }
    if (lhs.peerNdiMacAddr != rhs.peerNdiMacAddr) {
        return false;
    }
    if (lhs.appInfo != rhs.appInfo) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& lhs, const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::RttStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAILURE;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_RSP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_RSP)) {
        os += (first ? "" : " | ");
        os += "FAIL_NO_RSP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_RSP;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_REJECTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_REJECTED)) {
        os += (first ? "" : " | ");
        os += "FAIL_REJECTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_REJECTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_NOT_SCHEDULED_YET) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_NOT_SCHEDULED_YET)) {
        os += (first ? "" : " | ");
        os += "FAIL_NOT_SCHEDULED_YET";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_NOT_SCHEDULED_YET;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_TM_TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_TM_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "FAIL_TM_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_TM_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_AP_ON_DIFF_CHANNEL) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_AP_ON_DIFF_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "FAIL_AP_ON_DIFF_CHANNEL";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_AP_ON_DIFF_CHANNEL;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_CAPABILITY) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_CAPABILITY)) {
        os += (first ? "" : " | ");
        os += "FAIL_NO_CAPABILITY";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_CAPABILITY;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::ABORTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::ABORTED)) {
        os += (first ? "" : " | ");
        os += "ABORTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::ABORTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_INVALID_TS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_INVALID_TS)) {
        os += (first ? "" : " | ");
        os += "FAIL_INVALID_TS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_INVALID_TS;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_PROTOCOL) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_PROTOCOL)) {
        os += (first ? "" : " | ");
        os += "FAIL_PROTOCOL";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_PROTOCOL;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_SCHEDULE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_SCHEDULE)) {
        os += (first ? "" : " | ");
        os += "FAIL_SCHEDULE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_SCHEDULE;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_BUSY_TRY_LATER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_BUSY_TRY_LATER)) {
        os += (first ? "" : " | ");
        os += "FAIL_BUSY_TRY_LATER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_BUSY_TRY_LATER;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::INVALID_REQ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::INVALID_REQ)) {
        os += (first ? "" : " | ");
        os += "INVALID_REQ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::INVALID_REQ;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::NO_WIFI) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::NO_WIFI)) {
        os += (first ? "" : " | ");
        os += "NO_WIFI";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::NO_WIFI;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttStatus::FAIL_FTM_PARAM_OVERRIDE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttStatus::FAIL_FTM_PARAM_OVERRIDE)) {
        os += (first ? "" : " | ");
        os += "FAIL_FTM_PARAM_OVERRIDE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttStatus::FAIL_FTM_PARAM_OVERRIDE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::RttStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::RttStatus::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAILURE) {
        return "FAILURE";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_RSP) {
        return "FAIL_NO_RSP";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_REJECTED) {
        return "FAIL_REJECTED";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_NOT_SCHEDULED_YET) {
        return "FAIL_NOT_SCHEDULED_YET";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_TM_TIMEOUT) {
        return "FAIL_TM_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_AP_ON_DIFF_CHANNEL) {
        return "FAIL_AP_ON_DIFF_CHANNEL";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_CAPABILITY) {
        return "FAIL_NO_CAPABILITY";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::ABORTED) {
        return "ABORTED";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_INVALID_TS) {
        return "FAIL_INVALID_TS";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_PROTOCOL) {
        return "FAIL_PROTOCOL";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_SCHEDULE) {
        return "FAIL_SCHEDULE";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_BUSY_TRY_LATER) {
        return "FAIL_BUSY_TRY_LATER";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::INVALID_REQ) {
        return "INVALID_REQ";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::NO_WIFI) {
        return "NO_WIFI";
    }
    if (o == ::android::hardware::wifi::V1_0::RttStatus::FAIL_FTM_PARAM_OVERRIDE) {
        return "FAIL_FTM_PARAM_OVERRIDE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::RttStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::RttPeerType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttPeerType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::RttPeerType::AP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPeerType::AP)) {
        os += (first ? "" : " | ");
        os += "AP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPeerType::AP;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttPeerType::STA) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPeerType::STA)) {
        os += (first ? "" : " | ");
        os += "STA";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPeerType::STA;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttPeerType::P2P_GO) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPeerType::P2P_GO)) {
        os += (first ? "" : " | ");
        os += "P2P_GO";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPeerType::P2P_GO;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttPeerType::P2P_CLIENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPeerType::P2P_CLIENT)) {
        os += (first ? "" : " | ");
        os += "P2P_CLIENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPeerType::P2P_CLIENT;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttPeerType::NAN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPeerType::NAN)) {
        os += (first ? "" : " | ");
        os += "NAN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPeerType::NAN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::RttPeerType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::RttPeerType::AP) {
        return "AP";
    }
    if (o == ::android::hardware::wifi::V1_0::RttPeerType::STA) {
        return "STA";
    }
    if (o == ::android::hardware::wifi::V1_0::RttPeerType::P2P_GO) {
        return "P2P_GO";
    }
    if (o == ::android::hardware::wifi::V1_0::RttPeerType::P2P_CLIENT) {
        return "P2P_CLIENT";
    }
    if (o == ::android::hardware::wifi::V1_0::RttPeerType::NAN) {
        return "NAN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::RttPeerType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::RttBw>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttBw> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::RttBw::BW_5MHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttBw::BW_5MHZ)) {
        os += (first ? "" : " | ");
        os += "BW_5MHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttBw::BW_5MHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttBw::BW_10MHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttBw::BW_10MHZ)) {
        os += (first ? "" : " | ");
        os += "BW_10MHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttBw::BW_10MHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttBw::BW_20MHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttBw::BW_20MHZ)) {
        os += (first ? "" : " | ");
        os += "BW_20MHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttBw::BW_20MHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttBw::BW_40MHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttBw::BW_40MHZ)) {
        os += (first ? "" : " | ");
        os += "BW_40MHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttBw::BW_40MHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttBw::BW_80MHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttBw::BW_80MHZ)) {
        os += (first ? "" : " | ");
        os += "BW_80MHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttBw::BW_80MHZ;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttBw::BW_160MHZ) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttBw::BW_160MHZ)) {
        os += (first ? "" : " | ");
        os += "BW_160MHZ";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttBw::BW_160MHZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::RttBw o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::RttBw::BW_5MHZ) {
        return "BW_5MHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::RttBw::BW_10MHZ) {
        return "BW_10MHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::RttBw::BW_20MHZ) {
        return "BW_20MHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::RttBw::BW_40MHZ) {
        return "BW_40MHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::RttBw::BW_80MHZ) {
        return "BW_80MHZ";
    }
    if (o == ::android::hardware::wifi::V1_0::RttBw::BW_160MHZ) {
        return "BW_160MHZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::RttBw o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::RttPreamble>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttPreamble> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::RttPreamble::LEGACY) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPreamble::LEGACY)) {
        os += (first ? "" : " | ");
        os += "LEGACY";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPreamble::LEGACY;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttPreamble::HT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPreamble::HT)) {
        os += (first ? "" : " | ");
        os += "HT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPreamble::HT;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttPreamble::VHT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttPreamble::VHT)) {
        os += (first ? "" : " | ");
        os += "VHT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttPreamble::VHT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::RttPreamble o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::RttPreamble::LEGACY) {
        return "LEGACY";
    }
    if (o == ::android::hardware::wifi::V1_0::RttPreamble::HT) {
        return "HT";
    }
    if (o == ::android::hardware::wifi::V1_0::RttPreamble::VHT) {
        return "VHT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::RttPreamble o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::RttType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::RttType::ONE_SIDED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttType::ONE_SIDED)) {
        os += (first ? "" : " | ");
        os += "ONE_SIDED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttType::ONE_SIDED;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttType::TWO_SIDED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttType::TWO_SIDED)) {
        os += (first ? "" : " | ");
        os += "TWO_SIDED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttType::TWO_SIDED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::RttType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::RttType::ONE_SIDED) {
        return "ONE_SIDED";
    }
    if (o == ::android::hardware::wifi::V1_0::RttType::TWO_SIDED) {
        return "TWO_SIDED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::RttType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".addr = ";
    os += ::android::hardware::toString(o.addr);
    os += ", .type = ";
    os += ::android::hardware::wifi::V1_0::toString(o.type);
    os += ", .peer = ";
    os += ::android::hardware::wifi::V1_0::toString(o.peer);
    os += ", .channel = ";
    os += ::android::hardware::wifi::V1_0::toString(o.channel);
    os += ", .burstPeriod = ";
    os += ::android::hardware::toString(o.burstPeriod);
    os += ", .numBurst = ";
    os += ::android::hardware::toString(o.numBurst);
    os += ", .numFramesPerBurst = ";
    os += ::android::hardware::toString(o.numFramesPerBurst);
    os += ", .numRetriesPerRttFrame = ";
    os += ::android::hardware::toString(o.numRetriesPerRttFrame);
    os += ", .numRetriesPerFtmr = ";
    os += ::android::hardware::toString(o.numRetriesPerFtmr);
    os += ", .mustRequestLci = ";
    os += ::android::hardware::toString(o.mustRequestLci);
    os += ", .mustRequestLcr = ";
    os += ::android::hardware::toString(o.mustRequestLcr);
    os += ", .burstDuration = ";
    os += ::android::hardware::toString(o.burstDuration);
    os += ", .preamble = ";
    os += ::android::hardware::wifi::V1_0::toString(o.preamble);
    os += ", .bw = ";
    os += ::android::hardware::wifi::V1_0::toString(o.bw);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::RttConfig& lhs, const ::android::hardware::wifi::V1_0::RttConfig& rhs) {
    if (lhs.addr != rhs.addr) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.peer != rhs.peer) {
        return false;
    }
    if (lhs.channel != rhs.channel) {
        return false;
    }
    if (lhs.burstPeriod != rhs.burstPeriod) {
        return false;
    }
    if (lhs.numBurst != rhs.numBurst) {
        return false;
    }
    if (lhs.numFramesPerBurst != rhs.numFramesPerBurst) {
        return false;
    }
    if (lhs.numRetriesPerRttFrame != rhs.numRetriesPerRttFrame) {
        return false;
    }
    if (lhs.numRetriesPerFtmr != rhs.numRetriesPerFtmr) {
        return false;
    }
    if (lhs.mustRequestLci != rhs.mustRequestLci) {
        return false;
    }
    if (lhs.mustRequestLcr != rhs.mustRequestLcr) {
        return false;
    }
    if (lhs.burstDuration != rhs.burstDuration) {
        return false;
    }
    if (lhs.preamble != rhs.preamble) {
        return false;
    }
    if (lhs.bw != rhs.bw) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttConfig& lhs, const ::android::hardware::wifi::V1_0::RttConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".addr = ";
    os += ::android::hardware::toString(o.addr);
    os += ", .burstNum = ";
    os += ::android::hardware::toString(o.burstNum);
    os += ", .measurementNumber = ";
    os += ::android::hardware::toString(o.measurementNumber);
    os += ", .successNumber = ";
    os += ::android::hardware::toString(o.successNumber);
    os += ", .numberPerBurstPeer = ";
    os += ::android::hardware::toString(o.numberPerBurstPeer);
    os += ", .status = ";
    os += ::android::hardware::wifi::V1_0::toString(o.status);
    os += ", .retryAfterDuration = ";
    os += ::android::hardware::toString(o.retryAfterDuration);
    os += ", .type = ";
    os += ::android::hardware::wifi::V1_0::toString(o.type);
    os += ", .rssi = ";
    os += ::android::hardware::toString(o.rssi);
    os += ", .rssiSpread = ";
    os += ::android::hardware::toString(o.rssiSpread);
    os += ", .txRate = ";
    os += ::android::hardware::wifi::V1_0::toString(o.txRate);
    os += ", .rxRate = ";
    os += ::android::hardware::wifi::V1_0::toString(o.rxRate);
    os += ", .rtt = ";
    os += ::android::hardware::toString(o.rtt);
    os += ", .rttSd = ";
    os += ::android::hardware::toString(o.rttSd);
    os += ", .rttSpread = ";
    os += ::android::hardware::toString(o.rttSpread);
    os += ", .distanceInMm = ";
    os += ::android::hardware::toString(o.distanceInMm);
    os += ", .distanceSdInMm = ";
    os += ::android::hardware::toString(o.distanceSdInMm);
    os += ", .distanceSpreadInMm = ";
    os += ::android::hardware::toString(o.distanceSpreadInMm);
    os += ", .timeStampInUs = ";
    os += ::android::hardware::toString(o.timeStampInUs);
    os += ", .burstDurationInMs = ";
    os += ::android::hardware::toString(o.burstDurationInMs);
    os += ", .negotiatedBurstNum = ";
    os += ::android::hardware::toString(o.negotiatedBurstNum);
    os += ", .lci = ";
    os += ::android::hardware::wifi::V1_0::toString(o.lci);
    os += ", .lcr = ";
    os += ::android::hardware::wifi::V1_0::toString(o.lcr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::RttResult& lhs, const ::android::hardware::wifi::V1_0::RttResult& rhs) {
    if (lhs.addr != rhs.addr) {
        return false;
    }
    if (lhs.burstNum != rhs.burstNum) {
        return false;
    }
    if (lhs.measurementNumber != rhs.measurementNumber) {
        return false;
    }
    if (lhs.successNumber != rhs.successNumber) {
        return false;
    }
    if (lhs.numberPerBurstPeer != rhs.numberPerBurstPeer) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.retryAfterDuration != rhs.retryAfterDuration) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.rssi != rhs.rssi) {
        return false;
    }
    if (lhs.rssiSpread != rhs.rssiSpread) {
        return false;
    }
    if (lhs.txRate != rhs.txRate) {
        return false;
    }
    if (lhs.rxRate != rhs.rxRate) {
        return false;
    }
    if (lhs.rtt != rhs.rtt) {
        return false;
    }
    if (lhs.rttSd != rhs.rttSd) {
        return false;
    }
    if (lhs.rttSpread != rhs.rttSpread) {
        return false;
    }
    if (lhs.distanceInMm != rhs.distanceInMm) {
        return false;
    }
    if (lhs.distanceSdInMm != rhs.distanceSdInMm) {
        return false;
    }
    if (lhs.distanceSpreadInMm != rhs.distanceSpreadInMm) {
        return false;
    }
    if (lhs.timeStampInUs != rhs.timeStampInUs) {
        return false;
    }
    if (lhs.burstDurationInMs != rhs.burstDurationInMs) {
        return false;
    }
    if (lhs.negotiatedBurstNum != rhs.negotiatedBurstNum) {
        return false;
    }
    if (lhs.lci != rhs.lci) {
        return false;
    }
    if (lhs.lcr != rhs.lcr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttResult& lhs, const ::android::hardware::wifi::V1_0::RttResult& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttCapabilities& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rttOneSidedSupported = ";
    os += ::android::hardware::toString(o.rttOneSidedSupported);
    os += ", .rttFtmSupported = ";
    os += ::android::hardware::toString(o.rttFtmSupported);
    os += ", .lciSupported = ";
    os += ::android::hardware::toString(o.lciSupported);
    os += ", .lcrSupported = ";
    os += ::android::hardware::toString(o.lcrSupported);
    os += ", .responderSupported = ";
    os += ::android::hardware::toString(o.responderSupported);
    os += ", .preambleSupport = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::RttPreamble>(o.preambleSupport);
    os += ", .bwSupport = ";
    os += ::android::hardware::wifi::V1_0::toString<::android::hardware::wifi::V1_0::RttBw>(o.bwSupport);
    os += ", .mcVersion = ";
    os += ::android::hardware::toString(o.mcVersion);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttCapabilities& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::RttCapabilities& lhs, const ::android::hardware::wifi::V1_0::RttCapabilities& rhs) {
    if (lhs.rttOneSidedSupported != rhs.rttOneSidedSupported) {
        return false;
    }
    if (lhs.rttFtmSupported != rhs.rttFtmSupported) {
        return false;
    }
    if (lhs.lciSupported != rhs.lciSupported) {
        return false;
    }
    if (lhs.lcrSupported != rhs.lcrSupported) {
        return false;
    }
    if (lhs.responderSupported != rhs.responderSupported) {
        return false;
    }
    if (lhs.preambleSupport != rhs.preambleSupport) {
        return false;
    }
    if (lhs.bwSupport != rhs.bwSupport) {
        return false;
    }
    if (lhs.mcVersion != rhs.mcVersion) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttCapabilities& lhs, const ::android::hardware::wifi::V1_0::RttCapabilities& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::RttMotionPattern>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::RttMotionPattern> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::RttMotionPattern::NOT_EXPECTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttMotionPattern::NOT_EXPECTED)) {
        os += (first ? "" : " | ");
        os += "NOT_EXPECTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttMotionPattern::NOT_EXPECTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttMotionPattern::EXPECTED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttMotionPattern::EXPECTED)) {
        os += (first ? "" : " | ");
        os += "EXPECTED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttMotionPattern::EXPECTED;
    }
    if ((o & ::android::hardware::wifi::V1_0::RttMotionPattern::UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::RttMotionPattern::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::RttMotionPattern::UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::RttMotionPattern o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::RttMotionPattern::NOT_EXPECTED) {
        return "NOT_EXPECTED";
    }
    if (o == ::android::hardware::wifi::V1_0::RttMotionPattern::EXPECTED) {
        return "EXPECTED";
    }
    if (o == ::android::hardware::wifi::V1_0::RttMotionPattern::UNKNOWN) {
        return "UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::RttMotionPattern o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttLciInformation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".latitude = ";
    os += ::android::hardware::toString(o.latitude);
    os += ", .longitude = ";
    os += ::android::hardware::toString(o.longitude);
    os += ", .altitude = ";
    os += ::android::hardware::toString(o.altitude);
    os += ", .latitudeUnc = ";
    os += ::android::hardware::toString(o.latitudeUnc);
    os += ", .longitudeUnc = ";
    os += ::android::hardware::toString(o.longitudeUnc);
    os += ", .altitudeUnc = ";
    os += ::android::hardware::toString(o.altitudeUnc);
    os += ", .motionPattern = ";
    os += ::android::hardware::wifi::V1_0::toString(o.motionPattern);
    os += ", .floor = ";
    os += ::android::hardware::toString(o.floor);
    os += ", .heightAboveFloor = ";
    os += ::android::hardware::toString(o.heightAboveFloor);
    os += ", .heightUnc = ";
    os += ::android::hardware::toString(o.heightUnc);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttLciInformation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::RttLciInformation& lhs, const ::android::hardware::wifi::V1_0::RttLciInformation& rhs) {
    if (lhs.latitude != rhs.latitude) {
        return false;
    }
    if (lhs.longitude != rhs.longitude) {
        return false;
    }
    if (lhs.altitude != rhs.altitude) {
        return false;
    }
    if (lhs.latitudeUnc != rhs.latitudeUnc) {
        return false;
    }
    if (lhs.longitudeUnc != rhs.longitudeUnc) {
        return false;
    }
    if (lhs.altitudeUnc != rhs.altitudeUnc) {
        return false;
    }
    if (lhs.motionPattern != rhs.motionPattern) {
        return false;
    }
    if (lhs.floor != rhs.floor) {
        return false;
    }
    if (lhs.heightAboveFloor != rhs.heightAboveFloor) {
        return false;
    }
    if (lhs.heightUnc != rhs.heightUnc) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttLciInformation& lhs, const ::android::hardware::wifi::V1_0::RttLciInformation& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttLcrInformation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".countryCode = ";
    os += ::android::hardware::toString(o.countryCode);
    os += ", .civicInfo = ";
    os += ::android::hardware::toString(o.civicInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttLcrInformation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::RttLcrInformation& lhs, const ::android::hardware::wifi::V1_0::RttLcrInformation& rhs) {
    if (lhs.countryCode != rhs.countryCode) {
        return false;
    }
    if (lhs.civicInfo != rhs.civicInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttLcrInformation& lhs, const ::android::hardware::wifi::V1_0::RttLcrInformation& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::RttResponder& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".channel = ";
    os += ::android::hardware::wifi::V1_0::toString(o.channel);
    os += ", .preamble = ";
    os += ::android::hardware::wifi::V1_0::toString(o.preamble);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::RttResponder& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::RttResponder& lhs, const ::android::hardware::wifi::V1_0::RttResponder& rhs) {
    if (lhs.channel != rhs.channel) {
        return false;
    }
    if (lhs.preamble != rhs.preamble) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::RttResponder& lhs, const ::android::hardware::wifi::V1_0::RttResponder& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_BINARY_ENTRIES) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_BINARY_ENTRIES)) {
        os += (first ? "" : " | ");
        os += "HAS_BINARY_ENTRIES";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_BINARY_ENTRIES;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_ASCII_ENTRIES) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_ASCII_ENTRIES)) {
        os += (first ? "" : " | ");
        os += "HAS_ASCII_ENTRIES";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_ASCII_ENTRIES;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_PER_PACKET_ENTRIES) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_PER_PACKET_ENTRIES)) {
        os += (first ? "" : " | ");
        os += "HAS_PER_PACKET_ENTRIES";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_PER_PACKET_ENTRIES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_BINARY_ENTRIES) {
        return "HAS_BINARY_ENTRIES";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_ASCII_ENTRIES) {
        return "HAS_ASCII_ENTRIES";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_PER_PACKET_ENTRIES) {
        return "HAS_PER_PACKET_ENTRIES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ringName = ";
    os += ::android::hardware::toString(o.ringName);
    os += ", .flags = ";
    os += ::android::hardware::toString(o.flags);
    os += ", .ringId = ";
    os += ::android::hardware::toString(o.ringId);
    os += ", .sizeInBytes = ";
    os += ::android::hardware::toString(o.sizeInBytes);
    os += ", .freeSizeInBytes = ";
    os += ::android::hardware::toString(o.freeSizeInBytes);
    os += ", .verboseLevel = ";
    os += ::android::hardware::toString(o.verboseLevel);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& rhs) {
    if (lhs.ringName != rhs.ringName) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.ringId != rhs.ringId) {
        return false;
    }
    if (lhs.sizeInBytes != rhs.sizeInBytes) {
        return false;
    }
    if (lhs.freeSizeInBytes != rhs.freeSizeInBytes) {
        return false;
    }
    if (lhs.verboseLevel != rhs.verboseLevel) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::NONE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::NONE;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::DEFAULT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::DEFAULT;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::VERBOSE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::VERBOSE)) {
        os += (first ? "" : " | ");
        os += "VERBOSE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::VERBOSE;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::EXCESSIVE) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::EXCESSIVE)) {
        os += (first ? "" : " | ");
        os += "EXCESSIVE";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::EXCESSIVE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::VERBOSE) {
        return "VERBOSE";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::EXCESSIVE) {
        return "EXCESSIVE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiDebugTxPacketFate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiDebugTxPacketFate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::ACKED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::ACKED)) {
        os += (first ? "" : " | ");
        os += "ACKED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::ACKED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::SENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::SENT)) {
        os += (first ? "" : " | ");
        os += "SENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::SENT;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_QUEUED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_QUEUED)) {
        os += (first ? "" : " | ");
        os += "FW_QUEUED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_QUEUED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_INVALID)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_NOBUFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_NOBUFS)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_NOBUFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_NOBUFS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_OTHER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_OTHER)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_OTHER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_OTHER;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_QUEUED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_QUEUED)) {
        os += (first ? "" : " | ");
        os += "DRV_QUEUED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_QUEUED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_INVALID)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_NOBUFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_NOBUFS)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_NOBUFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_NOBUFS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_OTHER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_OTHER)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_OTHER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_OTHER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::ACKED) {
        return "ACKED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::SENT) {
        return "SENT";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_QUEUED) {
        return "FW_QUEUED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_INVALID) {
        return "FW_DROP_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_NOBUFS) {
        return "FW_DROP_NOBUFS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_OTHER) {
        return "FW_DROP_OTHER";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_QUEUED) {
        return "DRV_QUEUED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_INVALID) {
        return "DRV_DROP_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_NOBUFS) {
        return "DRV_DROP_NOBUFS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_OTHER) {
        return "DRV_DROP_OTHER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugTxPacketFate o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiDebugRxPacketFate>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiDebugRxPacketFate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_QUEUED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_QUEUED)) {
        os += (first ? "" : " | ");
        os += "FW_QUEUED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_QUEUED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_FILTER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_FILTER)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_FILTER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_FILTER;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_INVALID)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_NOBUFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_NOBUFS)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_NOBUFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_NOBUFS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_OTHER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_OTHER)) {
        os += (first ? "" : " | ");
        os += "FW_DROP_OTHER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_OTHER;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_QUEUED) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_QUEUED)) {
        os += (first ? "" : " | ");
        os += "DRV_QUEUED";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_QUEUED;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_FILTER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_FILTER)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_FILTER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_FILTER;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_INVALID) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_INVALID)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_INVALID";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_INVALID;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_NOBUFS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_NOBUFS)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_NOBUFS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_NOBUFS;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_OTHER) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_OTHER)) {
        os += (first ? "" : " | ");
        os += "DRV_DROP_OTHER";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_OTHER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_QUEUED) {
        return "FW_QUEUED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_FILTER) {
        return "FW_DROP_FILTER";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_INVALID) {
        return "FW_DROP_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_NOBUFS) {
        return "FW_DROP_NOBUFS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_OTHER) {
        return "FW_DROP_OTHER";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_QUEUED) {
        return "DRV_QUEUED";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_FILTER) {
        return "DRV_DROP_FILTER";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_INVALID) {
        return "DRV_DROP_INVALID";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_NOBUFS) {
        return "DRV_DROP_NOBUFS";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_OTHER) {
        return "DRV_DROP_OTHER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugRxPacketFate o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::UNKNOWN) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::UNKNOWN;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::ETHERNET_II) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::ETHERNET_II)) {
        os += (first ? "" : " | ");
        os += "ETHERNET_II";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::ETHERNET_II;
    }
    if ((o & ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::MGMT_80211) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::MGMT_80211)) {
        os += (first ? "" : " | ");
        os += "MGMT_80211";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::MGMT_80211;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::ETHERNET_II) {
        return "ETHERNET_II";
    }
    if (o == ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::MGMT_80211) {
        return "MGMT_80211";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frameType = ";
    os += ::android::hardware::wifi::V1_0::toString(o.frameType);
    os += ", .frameLen = ";
    os += ::android::hardware::toString(o.frameLen);
    os += ", .driverTimestampUsec = ";
    os += ::android::hardware::toString(o.driverTimestampUsec);
    os += ", .firmwareTimestampUsec = ";
    os += ::android::hardware::toString(o.firmwareTimestampUsec);
    os += ", .frameContent = ";
    os += ::android::hardware::toString(o.frameContent);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& rhs) {
    if (lhs.frameType != rhs.frameType) {
        return false;
    }
    if (lhs.frameLen != rhs.frameLen) {
        return false;
    }
    if (lhs.driverTimestampUsec != rhs.driverTimestampUsec) {
        return false;
    }
    if (lhs.firmwareTimestampUsec != rhs.firmwareTimestampUsec) {
        return false;
    }
    if (lhs.frameContent != rhs.frameContent) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& lhs, const ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".fate = ";
    os += ::android::hardware::wifi::V1_0::toString(o.fate);
    os += ", .frameInfo = ";
    os += ::android::hardware::wifi::V1_0::toString(o.frameInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& rhs) {
    if (lhs.fate != rhs.fate) {
        return false;
    }
    if (lhs.frameInfo != rhs.frameInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugTxPacketFateReport& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".fate = ";
    os += ::android::hardware::wifi::V1_0::toString(o.fate);
    os += ", .frameInfo = ";
    os += ::android::hardware::wifi::V1_0::toString(o.frameInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& rhs) {
    if (lhs.fate != rhs.fate) {
        return false;
    }
    if (lhs.frameInfo != rhs.frameInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& lhs, const ::android::hardware::wifi::V1_0::WifiDebugRxPacketFateReport& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rxUnicastCnt = ";
    os += ::android::hardware::toString(o.rxUnicastCnt);
    os += ", .rxMulticastCnt = ";
    os += ::android::hardware::toString(o.rxMulticastCnt);
    os += ", .rxBroadcastCnt = ";
    os += ::android::hardware::toString(o.rxBroadcastCnt);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& rhs) {
    if (lhs.rxUnicastCnt != rhs.rxUnicastCnt) {
        return false;
    }
    if (lhs.rxMulticastCnt != rhs.rxMulticastCnt) {
        return false;
    }
    if (lhs.rxBroadcastCnt != rhs.rxBroadcastCnt) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxPacketDetails& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ipv4RxMulticastAddrCnt = ";
    os += ::android::hardware::toString(o.ipv4RxMulticastAddrCnt);
    os += ", .ipv6RxMulticastAddrCnt = ";
    os += ::android::hardware::toString(o.ipv6RxMulticastAddrCnt);
    os += ", .otherRxMulticastAddrCnt = ";
    os += ::android::hardware::toString(o.otherRxMulticastAddrCnt);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& rhs) {
    if (lhs.ipv4RxMulticastAddrCnt != rhs.ipv4RxMulticastAddrCnt) {
        return false;
    }
    if (lhs.ipv6RxMulticastAddrCnt != rhs.ipv6RxMulticastAddrCnt) {
        return false;
    }
    if (lhs.otherRxMulticastAddrCnt != rhs.otherRxMulticastAddrCnt) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxMulticastPacketDetails& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".icmpPkt = ";
    os += ::android::hardware::toString(o.icmpPkt);
    os += ", .icmp6Pkt = ";
    os += ::android::hardware::toString(o.icmp6Pkt);
    os += ", .icmp6Ra = ";
    os += ::android::hardware::toString(o.icmp6Ra);
    os += ", .icmp6Na = ";
    os += ::android::hardware::toString(o.icmp6Na);
    os += ", .icmp6Ns = ";
    os += ::android::hardware::toString(o.icmp6Ns);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& rhs) {
    if (lhs.icmpPkt != rhs.icmpPkt) {
        return false;
    }
    if (lhs.icmp6Pkt != rhs.icmp6Pkt) {
        return false;
    }
    if (lhs.icmp6Ra != rhs.icmp6Ra) {
        return false;
    }
    if (lhs.icmp6Na != rhs.icmp6Na) {
        return false;
    }
    if (lhs.icmp6Ns != rhs.icmp6Ns) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonRxIcmpPacketDetails& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".totalCmdEventWakeCnt = ";
    os += ::android::hardware::toString(o.totalCmdEventWakeCnt);
    os += ", .cmdEventWakeCntPerType = ";
    os += ::android::hardware::toString(o.cmdEventWakeCntPerType);
    os += ", .totalDriverFwLocalWakeCnt = ";
    os += ::android::hardware::toString(o.totalDriverFwLocalWakeCnt);
    os += ", .driverFwLocalWakeCntPerType = ";
    os += ::android::hardware::toString(o.driverFwLocalWakeCntPerType);
    os += ", .totalRxPacketWakeCnt = ";
    os += ::android::hardware::toString(o.totalRxPacketWakeCnt);
    os += ", .rxPktWakeDetails = ";
    os += ::android::hardware::wifi::V1_0::toString(o.rxPktWakeDetails);
    os += ", .rxMulticastPkWakeDetails = ";
    os += ::android::hardware::wifi::V1_0::toString(o.rxMulticastPkWakeDetails);
    os += ", .rxIcmpPkWakeDetails = ";
    os += ::android::hardware::wifi::V1_0::toString(o.rxIcmpPkWakeDetails);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& rhs) {
    if (lhs.totalCmdEventWakeCnt != rhs.totalCmdEventWakeCnt) {
        return false;
    }
    if (lhs.cmdEventWakeCntPerType != rhs.cmdEventWakeCntPerType) {
        return false;
    }
    if (lhs.totalDriverFwLocalWakeCnt != rhs.totalDriverFwLocalWakeCnt) {
        return false;
    }
    if (lhs.driverFwLocalWakeCntPerType != rhs.driverFwLocalWakeCntPerType) {
        return false;
    }
    if (lhs.totalRxPacketWakeCnt != rhs.totalRxPacketWakeCnt) {
        return false;
    }
    if (lhs.rxPktWakeDetails != rhs.rxPktWakeDetails) {
        return false;
    }
    if (lhs.rxMulticastPkWakeDetails != rhs.rxMulticastPkWakeDetails) {
        return false;
    }
    if (lhs.rxIcmpPkWakeDetails != rhs.rxIcmpPkWakeDetails) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& lhs, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace wifi
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiStatusCode, 10> hidl_enum_values<::android::hardware::wifi::V1_0::WifiStatusCode> = {
    ::android::hardware::wifi::V1_0::WifiStatusCode::SUCCESS,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_CHIP_INVALID,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_IFACE_INVALID,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_WIFI_RTT_CONTROLLER_INVALID,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_SUPPORTED,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_AVAILABLE,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_NOT_STARTED,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_INVALID_ARGS,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_BUSY,
    ::android::hardware::wifi::V1_0::WifiStatusCode::ERROR_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::IfaceType, 4> hidl_enum_values<::android::hardware::wifi::V1_0::IfaceType> = {
    ::android::hardware::wifi::V1_0::IfaceType::STA,
    ::android::hardware::wifi::V1_0::IfaceType::AP,
    ::android::hardware::wifi::V1_0::IfaceType::P2P,
    ::android::hardware::wifi::V1_0::IfaceType::NAN,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiChannelWidthInMhz, 8> hidl_enum_values<::android::hardware::wifi::V1_0::WifiChannelWidthInMhz> = {
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_20,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_40,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_160,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_80P80,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_5,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_10,
    ::android::hardware::wifi::V1_0::WifiChannelWidthInMhz::WIDTH_INVALID,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiRatePreamble, 5> hidl_enum_values<::android::hardware::wifi::V1_0::WifiRatePreamble> = {
    ::android::hardware::wifi::V1_0::WifiRatePreamble::OFDM,
    ::android::hardware::wifi::V1_0::WifiRatePreamble::CCK,
    ::android::hardware::wifi::V1_0::WifiRatePreamble::HT,
    ::android::hardware::wifi::V1_0::WifiRatePreamble::VHT,
    ::android::hardware::wifi::V1_0::WifiRatePreamble::RESERVED,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiRateNss, 4> hidl_enum_values<::android::hardware::wifi::V1_0::WifiRateNss> = {
    ::android::hardware::wifi::V1_0::WifiRateNss::NSS_1x1,
    ::android::hardware::wifi::V1_0::WifiRateNss::NSS_2x2,
    ::android::hardware::wifi::V1_0::WifiRateNss::NSS_3x3,
    ::android::hardware::wifi::V1_0::WifiRateNss::NSS_4x4,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiBand, 7> hidl_enum_values<::android::hardware::wifi::V1_0::WifiBand> = {
    ::android::hardware::wifi::V1_0::WifiBand::BAND_UNSPECIFIED,
    ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ,
    ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ,
    ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_DFS,
    ::android::hardware::wifi::V1_0::WifiBand::BAND_5GHZ_WITH_DFS,
    ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ,
    ::android::hardware::wifi::V1_0::WifiBand::BAND_24GHZ_5GHZ_WITH_DFS,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask, 3> hidl_enum_values<::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask> = {
    ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::EACH_SCAN,
    ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::FULL_RESULTS,
    ::android::hardware::wifi::V1_0::StaBackgroundScanBucketEventReportSchemeMask::NO_BATCH,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::StaScanLimits, 3> hidl_enum_values<::android::hardware::wifi::V1_0::StaScanLimits> = {
    ::android::hardware::wifi::V1_0::StaScanLimits::MAX_CHANNELS,
    ::android::hardware::wifi::V1_0::StaScanLimits::MAX_BUCKETS,
    ::android::hardware::wifi::V1_0::StaScanLimits::MAX_AP_CACHE_PER_SCAN,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::StaScanDataFlagMask, 1> hidl_enum_values<::android::hardware::wifi::V1_0::StaScanDataFlagMask> = {
    ::android::hardware::wifi::V1_0::StaScanDataFlagMask::INTERRUPTED,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::StaRoamingState, 2> hidl_enum_values<::android::hardware::wifi::V1_0::StaRoamingState> = {
    ::android::hardware::wifi::V1_0::StaRoamingState::DISABLED,
    ::android::hardware::wifi::V1_0::StaRoamingState::ENABLED,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanParamSizeLimits, 2> hidl_enum_values<::android::hardware::wifi::V1_0::NanParamSizeLimits> = {
    ::android::hardware::wifi::V1_0::NanParamSizeLimits::MIN_PASSPHRASE_LENGTH,
    ::android::hardware::wifi::V1_0::NanParamSizeLimits::MAX_PASSPHRASE_LENGTH,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanStatusType, 13> hidl_enum_values<::android::hardware::wifi::V1_0::NanStatusType> = {
    ::android::hardware::wifi::V1_0::NanStatusType::SUCCESS,
    ::android::hardware::wifi::V1_0::NanStatusType::INTERNAL_FAILURE,
    ::android::hardware::wifi::V1_0::NanStatusType::PROTOCOL_FAILURE,
    ::android::hardware::wifi::V1_0::NanStatusType::INVALID_SESSION_ID,
    ::android::hardware::wifi::V1_0::NanStatusType::NO_RESOURCES_AVAILABLE,
    ::android::hardware::wifi::V1_0::NanStatusType::INVALID_ARGS,
    ::android::hardware::wifi::V1_0::NanStatusType::INVALID_PEER_ID,
    ::android::hardware::wifi::V1_0::NanStatusType::INVALID_NDP_ID,
    ::android::hardware::wifi::V1_0::NanStatusType::NAN_NOT_ALLOWED,
    ::android::hardware::wifi::V1_0::NanStatusType::NO_OTA_ACK,
    ::android::hardware::wifi::V1_0::NanStatusType::ALREADY_ENABLED,
    ::android::hardware::wifi::V1_0::NanStatusType::FOLLOWUP_TX_QUEUE_FULL,
    ::android::hardware::wifi::V1_0::NanStatusType::UNSUPPORTED_CONCURRENCY_NAN_DISABLED,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanBandIndex, 2> hidl_enum_values<::android::hardware::wifi::V1_0::NanBandIndex> = {
    ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_24GHZ,
    ::android::hardware::wifi::V1_0::NanBandIndex::NAN_BAND_5GHZ,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanMatchAlg, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanMatchAlg> = {
    ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_ONCE,
    ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_CONTINUOUS,
    ::android::hardware::wifi::V1_0::NanMatchAlg::MATCH_NEVER,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanPublishType, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanPublishType> = {
    ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED,
    ::android::hardware::wifi::V1_0::NanPublishType::SOLICITED,
    ::android::hardware::wifi::V1_0::NanPublishType::UNSOLICITED_SOLICITED,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanTxType, 2> hidl_enum_values<::android::hardware::wifi::V1_0::NanTxType> = {
    ::android::hardware::wifi::V1_0::NanTxType::BROADCAST,
    ::android::hardware::wifi::V1_0::NanTxType::UNICAST,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanSubscribeType, 2> hidl_enum_values<::android::hardware::wifi::V1_0::NanSubscribeType> = {
    ::android::hardware::wifi::V1_0::NanSubscribeType::PASSIVE,
    ::android::hardware::wifi::V1_0::NanSubscribeType::ACTIVE,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanSrfType, 2> hidl_enum_values<::android::hardware::wifi::V1_0::NanSrfType> = {
    ::android::hardware::wifi::V1_0::NanSrfType::BLOOM_FILTER,
    ::android::hardware::wifi::V1_0::NanSrfType::PARTIAL_MAC_ADDR,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanDataPathChannelCfg, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanDataPathChannelCfg> = {
    ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::CHANNEL_NOT_REQUESTED,
    ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::REQUEST_CHANNEL_SETUP,
    ::android::hardware::wifi::V1_0::NanDataPathChannelCfg::FORCE_CHANNEL_SETUP,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanDataPathSecurityType, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanDataPathSecurityType> = {
    ::android::hardware::wifi::V1_0::NanDataPathSecurityType::OPEN,
    ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PMK,
    ::android::hardware::wifi::V1_0::NanDataPathSecurityType::PASSPHRASE,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanCipherSuiteType, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanCipherSuiteType> = {
    ::android::hardware::wifi::V1_0::NanCipherSuiteType::NONE,
    ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_128_MASK,
    ::android::hardware::wifi::V1_0::NanCipherSuiteType::SHARED_KEY_256_MASK,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanRangingIndication, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanRangingIndication> = {
    ::android::hardware::wifi::V1_0::NanRangingIndication::CONTINUOUS_INDICATION_MASK,
    ::android::hardware::wifi::V1_0::NanRangingIndication::INGRESS_MET_MASK,
    ::android::hardware::wifi::V1_0::NanRangingIndication::EGRESS_MET_MASK,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::NanClusterEventType, 3> hidl_enum_values<::android::hardware::wifi::V1_0::NanClusterEventType> = {
    ::android::hardware::wifi::V1_0::NanClusterEventType::DISCOVERY_MAC_ADDRESS_CHANGED,
    ::android::hardware::wifi::V1_0::NanClusterEventType::STARTED_CLUSTER,
    ::android::hardware::wifi::V1_0::NanClusterEventType::JOINED_CLUSTER,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::RttStatus, 16> hidl_enum_values<::android::hardware::wifi::V1_0::RttStatus> = {
    ::android::hardware::wifi::V1_0::RttStatus::SUCCESS,
    ::android::hardware::wifi::V1_0::RttStatus::FAILURE,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_RSP,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_REJECTED,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_NOT_SCHEDULED_YET,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_TM_TIMEOUT,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_AP_ON_DIFF_CHANNEL,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_NO_CAPABILITY,
    ::android::hardware::wifi::V1_0::RttStatus::ABORTED,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_INVALID_TS,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_PROTOCOL,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_SCHEDULE,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_BUSY_TRY_LATER,
    ::android::hardware::wifi::V1_0::RttStatus::INVALID_REQ,
    ::android::hardware::wifi::V1_0::RttStatus::NO_WIFI,
    ::android::hardware::wifi::V1_0::RttStatus::FAIL_FTM_PARAM_OVERRIDE,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::RttPeerType, 5> hidl_enum_values<::android::hardware::wifi::V1_0::RttPeerType> = {
    ::android::hardware::wifi::V1_0::RttPeerType::AP,
    ::android::hardware::wifi::V1_0::RttPeerType::STA,
    ::android::hardware::wifi::V1_0::RttPeerType::P2P_GO,
    ::android::hardware::wifi::V1_0::RttPeerType::P2P_CLIENT,
    ::android::hardware::wifi::V1_0::RttPeerType::NAN,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::RttBw, 6> hidl_enum_values<::android::hardware::wifi::V1_0::RttBw> = {
    ::android::hardware::wifi::V1_0::RttBw::BW_5MHZ,
    ::android::hardware::wifi::V1_0::RttBw::BW_10MHZ,
    ::android::hardware::wifi::V1_0::RttBw::BW_20MHZ,
    ::android::hardware::wifi::V1_0::RttBw::BW_40MHZ,
    ::android::hardware::wifi::V1_0::RttBw::BW_80MHZ,
    ::android::hardware::wifi::V1_0::RttBw::BW_160MHZ,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::RttPreamble, 3> hidl_enum_values<::android::hardware::wifi::V1_0::RttPreamble> = {
    ::android::hardware::wifi::V1_0::RttPreamble::LEGACY,
    ::android::hardware::wifi::V1_0::RttPreamble::HT,
    ::android::hardware::wifi::V1_0::RttPreamble::VHT,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::RttType, 2> hidl_enum_values<::android::hardware::wifi::V1_0::RttType> = {
    ::android::hardware::wifi::V1_0::RttType::ONE_SIDED,
    ::android::hardware::wifi::V1_0::RttType::TWO_SIDED,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::RttMotionPattern, 3> hidl_enum_values<::android::hardware::wifi::V1_0::RttMotionPattern> = {
    ::android::hardware::wifi::V1_0::RttMotionPattern::NOT_EXPECTED,
    ::android::hardware::wifi::V1_0::RttMotionPattern::EXPECTED,
    ::android::hardware::wifi::V1_0::RttMotionPattern::UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags, 3> hidl_enum_values<::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags> = {
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_BINARY_ENTRIES,
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_ASCII_ENTRIES,
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferFlags::HAS_PER_PACKET_ENTRIES,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel, 4> hidl_enum_values<::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel> = {
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::NONE,
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::DEFAULT,
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::VERBOSE,
    ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel::EXCESSIVE,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiDebugTxPacketFate, 10> hidl_enum_values<::android::hardware::wifi::V1_0::WifiDebugTxPacketFate> = {
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::ACKED,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::SENT,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_QUEUED,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_INVALID,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_NOBUFS,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::FW_DROP_OTHER,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_QUEUED,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_INVALID,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_NOBUFS,
    ::android::hardware::wifi::V1_0::WifiDebugTxPacketFate::DRV_DROP_OTHER,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiDebugRxPacketFate, 11> hidl_enum_values<::android::hardware::wifi::V1_0::WifiDebugRxPacketFate> = {
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::SUCCESS,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_QUEUED,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_FILTER,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_INVALID,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_NOBUFS,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::FW_DROP_OTHER,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_QUEUED,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_FILTER,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_INVALID,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_NOBUFS,
    ::android::hardware::wifi::V1_0::WifiDebugRxPacketFate::DRV_DROP_OTHER,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType, 3> hidl_enum_values<::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType> = {
    ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::UNKNOWN,
    ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::ETHERNET_II,
    ::android::hardware::wifi::V1_0::WifiDebugPacketFateFrameType::MGMT_80211,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_TYPES_H
