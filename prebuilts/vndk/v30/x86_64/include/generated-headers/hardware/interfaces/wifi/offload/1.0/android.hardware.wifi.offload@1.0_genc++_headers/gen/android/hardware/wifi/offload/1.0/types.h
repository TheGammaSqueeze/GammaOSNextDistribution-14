#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_OFFLOAD_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_OFFLOAD_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace offload {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class SecurityMode : uint8_t;
struct NetworkInfo;
enum class Capability : uint16_t;
struct ScanResult;
struct ScanParam;
struct ScanFilter;
struct ScanRecord;
enum class RecordName : uint32_t;
struct LogRecord;
struct ScanStats;
enum class OffloadStatusCode : uint32_t;
struct OffloadStatus;

/**
 * Defines a bitmap of security modes
 */
enum class SecurityMode : uint8_t {
    OPEN = 2 /* 0x1 << 1 */,
    WEP = 4 /* 0x1 << 2 */,
    PSK = 8 /* 0x1 << 3 */,
    EAP = 16 /* 0x1 << 4 */,
};

/**
 * SSID of the Access Point, maximum 32 characters
 */
typedef ::android::hardware::hidl_vec<uint8_t> Ssid;

/**
 * Preferred network information
 * SSID and associated security mode(s)
 */
struct NetworkInfo final {
    ::android::hardware::hidl_vec<uint8_t> ssid __attribute__ ((aligned(8)));
    /*
     * SecurityMode flags that are associated with this SSID
     * More than one security mode can be supported, see SecurityMode
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::offload::V1_0::SecurityMode> flags __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::NetworkInfo, ssid) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::NetworkInfo, flags) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::NetworkInfo) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::NetworkInfo) == 8, "wrong alignment");

/**
 * This is a bit mask describing the capabilities of a BSS.
 * See IEEE Std 802.11: 8.4.1.4
 */
enum class Capability : uint16_t {
    ESS = 1 /* 1 << 0 */,
    IBSS = 2 /* 1 << 1 */,
    CF_POLLABLE = 4 /* 1 << 2 */,
    CF_PLL_REQ = 8 /* 1 << 3 */,
    PRIVACY = 16 /* 1 << 4 */,
    SHORT_PREAMBLE = 32 /* 1 << 5 */,
    PBCC = 64 /* 1 << 6 */,
    CHANNEL_AGILITY = 128 /* 1 << 7 */,
    SPECTURM_MGMT = 256 /* 1 << 8 */,
    QOS = 512 /* 1 << 9 */,
    SHORT_SLOT_TIME = 1024 /* 1 << 10 */,
    APSD = 2048 /* 1 << 11 */,
    RADIO_MEASUREMENT = 4096 /* 1 << 12 */,
    DSSS_OFDM = 8192 /* 1 << 13 */,
    DELAYED_BLOCK_ACK = 16384 /* 1 << 14 */,
    IMMEDIATE_BLOCK_ACK = 32768 /* 1 << 15 */,
};

/**
 * Scan Results returned by the offload Hal
 */
struct ScanResult final {
    /*
     * Information about this BSS
     * SSID and security modes supported
     */
    ::android::hardware::wifi::offload::V1_0::NetworkInfo networkInfo __attribute__ ((aligned(8)));
    /*
     * BSSID of the BSS
     */
    ::android::hardware::hidl_array<uint8_t, 6> bssid __attribute__ ((aligned(1)));
    /*
     * Can have multiple bits set, see Capability
     */
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::offload::V1_0::Capability> capability __attribute__ ((aligned(2)));
    /*
     * Frequency scanned, in mHz
     */
    uint32_t frequency __attribute__ ((aligned(4)));
    /*
     * Signal strength in dBm
     */
    int8_t rssi __attribute__ ((aligned(1)));
    /*
     * TSF found in beacon/probe response
     */
    uint64_t tsf __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanResult, networkInfo) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanResult, bssid) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanResult, capability) == 30, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanResult, frequency) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanResult, rssi) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanResult, tsf) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::ScanResult) == 48, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::ScanResult) == 8, "wrong alignment");

/**
 * Parameters for performing offload scans
 */
struct ScanParam final {
    /*
     * Specify a list of SSIDs to scan, an empty list implies no preferred
     * networks to scan
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_vec<uint8_t>> ssidList __attribute__ ((aligned(8)));
    /*
     * Frequencies to scan, in mHz, an empty frequency list implies a full scan
     */
    ::android::hardware::hidl_vec<uint32_t> frequencyList __attribute__ ((aligned(8)));
    /*
     * Periodicity of the scans to be performed by the offload module
     * A value of zero indicates disable periodic scans. For this revision,
     * where offload module is performing scans in disconnected mode, this value
     * should not be zero. In future versions, periodic scans can be eliminated
     */
    uint32_t disconnectedModeScanIntervalMs __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanParam, ssidList) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanParam, frequencyList) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanParam, disconnectedModeScanIntervalMs) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::ScanParam) == 40, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::ScanParam) == 8, "wrong alignment");

/**
 * Instruction on how to filter the scan result before performing network
 * selection and waking up the AP to connect
 */
struct ScanFilter final {
    /*
     * Preferred network List of SSIDs and their security mode of interest
     * The filter will drop the remaining scan results in the scan event.
     * An empty list implies no filtering of scan result based on SSID and
     * security mode.
     */
    ::android::hardware::hidl_vec<::android::hardware::wifi::offload::V1_0::NetworkInfo> preferredNetworkInfoList __attribute__ ((aligned(8)));
    /*
     * Minimum qualifying RSSI to be considered for network selection (dBm)
     */
    int8_t rssiThreshold __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanFilter, preferredNetworkInfoList) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanFilter, rssiThreshold) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::ScanFilter) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::ScanFilter) == 8, "wrong alignment");

struct ScanRecord final {
    /*
     * Amount of time spent scanning
     */
    uint64_t durationMs __attribute__ ((aligned(8)));
    /*
     * Number of channels scanned
     */
    uint32_t numChannelsScanned __attribute__ ((aligned(4)));
    /*
     * Number of entries aggregated into this record
     */
    uint32_t numEntriesAggregated __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanRecord, durationMs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanRecord, numChannelsScanned) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanRecord, numEntriesAggregated) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::ScanRecord) == 16, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::ScanRecord) == 8, "wrong alignment");

/**
 * Enumerates the type of log that is recorded
 */
enum class RecordName : uint32_t {
    CMD_BASE = 4096u /* 0x00001000 */,
    /*
     * Record name corresponding to initialization
     */
    CMD_INT = 4096u /* CMD_BASE + 0 */,
    /*
     * Record name corresponding to configureScans() API
     */
    CMD_CONFIG_SCANS = 4097u /* CMD_BASE + 1 */,
    /*
     * Record name corresponding to subscribeScanResults() API
     */
    CMD_SUBSCRIBE_SCAN_RESULTS = 4098u /* CMD_BASE + 2 */,
    /*
     * Record name corresponding to unsubscribeScanResults() API
     */
    CMD_UNSUBSCRIBE_SCAN_RESULTS = 4099u /* CMD_BASE + 3 */,
    /*
     * Record name corresponding to getScanStats() API
     */
    CMD_GET_SCAN_STATS = 4100u /* CMD_BASE + 4 */,
    /*
     * Record name corresponding to a reset
     */
    CMD_RESET = 4101u /* CMD_BASE + 5 */,
    /*
     * Add new commands here
     */
    EVENT_RECVD_BASE = 8192u /* 0x00002000 */,
    /*
     * Record name corresponding to scan monitor event
     */
    EVENT_RECVD_SCAN_RESULT_ASYNC = 8192u /* EVENT_RECVD_BASE + 0 */,
    /*
     * Record name corresponding to scan response event
     */
    EVENT_RECVD_SCAN_RESULT = 8193u /* EVENT_RECVD_BASE + 1 */,
    /*
     * Add new events received here
     */
    EVENT_SENT_BASE = 12288u /* 0x00003000 */,
    /*
     * Record name corresponding to scan event sent
     */
    EVENT_SENT_SCAN_RESULT = 12288u /* EVENT_SENT_BASE + 0 */,
    /*
     * Record name corresponding to abort event sent
     */
    EVENT_SENT_ABORT = 12289u /* EVENT_SENT_BASE + 1 */,
    /*
     * Record name corresponding to error event sent
     */
    EVENT_SENT_ERROR = 12290u /* EVENT_SENT_BASE + 2 */,
    /*
     * Add new events sent here
     */
    REQ_BASE = 16384u /* 0x00004000 */,
    /*
     * Record name corresponding to scan request sent
     */
    REQ_SCAN = 16384u /* REQ_BASE + 0 */,
};

/**
 * Defines the structure of each log record
 */
struct LogRecord final {
    /*
     * Indicates the log recorded
     */
    ::android::hardware::wifi::offload::V1_0::RecordName recordName __attribute__ ((aligned(4)));
    /*
     * Platform reference time in milliseconds when the log is recorded
     */
    uint64_t logTimeMs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::LogRecord, recordName) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::LogRecord, logTimeMs) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::LogRecord) == 16, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::LogRecord) == 8, "wrong alignment");

/**
 * Defines the scan statistics to be returned to the framework
 */
struct ScanStats final {
    /*
     * Incremented everytime a new scan is requested
     */
    uint32_t numScansRequestedByWifi __attribute__ ((aligned(4)));
    /*
     * Incremented everytime the scan is serviced by performing a scan
     */
    uint32_t numScansServicedByWifi __attribute__ ((aligned(4)));
    /*
     * Incremented everytime the scan is serviced by the scan cache
     */
    uint32_t numScansServicedbyCache __attribute__ ((aligned(4)));
    /*
     * The last (CHRE reference) time this data structure is updated
     */
    uint64_t lastUpdated __attribute__ ((aligned(8)));
    /*
     * The last (CHRE reference) time this data structure is read
     */
    uint64_t lastRead __attribute__ ((aligned(8)));
    /*
     * The total time when the Offload module could be performing scans (T2 - T1)
     * T1 - time when the framework subscribes for scan result (includes delayMs)
     * T2 - min (time when the framework unsubscribes for scan result,
     * currentTime)
     */
    uint64_t subscriptionDurationMs __attribute__ ((aligned(8)));
    /*
     * Histograms of the channels scanned, 802.11 and with an 8 bit
     * representation, only 256 channels are available
     */
    ::android::hardware::hidl_array<uint8_t, 256> histogramChannelsScanned __attribute__ ((aligned(1)));
    /*
     * Scan Record for this subscribe duration
     */
    ::android::hardware::hidl_vec<::android::hardware::wifi::offload::V1_0::ScanRecord> scanRecord __attribute__ ((aligned(8)));
    /*
     * Vector of the logRecord entries
     */
    ::android::hardware::hidl_vec<::android::hardware::wifi::offload::V1_0::LogRecord> logRecord __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, numScansRequestedByWifi) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, numScansServicedByWifi) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, numScansServicedbyCache) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, lastUpdated) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, lastRead) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, subscriptionDurationMs) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, histogramChannelsScanned) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, scanRecord) == 296, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::ScanStats, logRecord) == 312, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::ScanStats) == 328, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::ScanStats) == 8, "wrong alignment");

/**
 * Defines a list of return codes to indicate status of Offload HAL
 */
enum class OffloadStatusCode : uint32_t {
    /*
     * No error
     */
    OK = 0u,
    /*
     * No Connection to underlying implementation
     */
    NO_CONNECTION = 1u /* ::android::hardware::wifi::offload::V1_0::OffloadStatusCode.OK implicitly + 1 */,
    /*
     * Operation timeout
     */
    TIMEOUT = 2u /* ::android::hardware::wifi::offload::V1_0::OffloadStatusCode.NO_CONNECTION implicitly + 1 */,
    /*
     * Other errors
     */
    ERROR = 3u /* ::android::hardware::wifi::offload::V1_0::OffloadStatusCode.TIMEOUT implicitly + 1 */,
};

/**
 * Generic structures to return the status of an operation
 */
struct OffloadStatus final {
    ::android::hardware::wifi::offload::V1_0::OffloadStatusCode code __attribute__ ((aligned(4)));
    /*
     * Error message
     */
    ::android::hardware::hidl_string description __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::offload::V1_0::OffloadStatus, code) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::offload::V1_0::OffloadStatus, description) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::offload::V1_0::OffloadStatus) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::offload::V1_0::OffloadStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::offload::V1_0::SecurityMode o);
static inline void PrintTo(::android::hardware::wifi::offload::V1_0::SecurityMode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::offload::V1_0::SecurityMode lhs, const ::android::hardware::wifi::offload::V1_0::SecurityMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::offload::V1_0::SecurityMode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::offload::V1_0::SecurityMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::offload::V1_0::SecurityMode lhs, const ::android::hardware::wifi::offload::V1_0::SecurityMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::offload::V1_0::SecurityMode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::offload::V1_0::SecurityMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::offload::V1_0::SecurityMode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::offload::V1_0::SecurityMode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& lhs, const ::android::hardware::wifi::offload::V1_0::NetworkInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& lhs, const ::android::hardware::wifi::offload::V1_0::NetworkInfo& rhs);

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::wifi::offload::V1_0::Capability o);
static inline void PrintTo(::android::hardware::wifi::offload::V1_0::Capability o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::wifi::offload::V1_0::Capability lhs, const ::android::hardware::wifi::offload::V1_0::Capability rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::wifi::offload::V1_0::Capability rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::wifi::offload::V1_0::Capability lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::wifi::offload::V1_0::Capability lhs, const ::android::hardware::wifi::offload::V1_0::Capability rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::wifi::offload::V1_0::Capability rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::wifi::offload::V1_0::Capability lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::wifi::offload::V1_0::Capability e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::wifi::offload::V1_0::Capability e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanResult& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanResult& lhs, const ::android::hardware::wifi::offload::V1_0::ScanResult& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanResult& lhs, const ::android::hardware::wifi::offload::V1_0::ScanResult& rhs);

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanParam& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanParam& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanParam& lhs, const ::android::hardware::wifi::offload::V1_0::ScanParam& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanParam& lhs, const ::android::hardware::wifi::offload::V1_0::ScanParam& rhs);

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanFilter& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanFilter& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanFilter& lhs, const ::android::hardware::wifi::offload::V1_0::ScanFilter& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanFilter& lhs, const ::android::hardware::wifi::offload::V1_0::ScanFilter& rhs);

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanRecord& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanRecord& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanRecord& lhs, const ::android::hardware::wifi::offload::V1_0::ScanRecord& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanRecord& lhs, const ::android::hardware::wifi::offload::V1_0::ScanRecord& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::offload::V1_0::RecordName o);
static inline void PrintTo(::android::hardware::wifi::offload::V1_0::RecordName o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::offload::V1_0::RecordName lhs, const ::android::hardware::wifi::offload::V1_0::RecordName rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::offload::V1_0::RecordName rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::offload::V1_0::RecordName lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::offload::V1_0::RecordName lhs, const ::android::hardware::wifi::offload::V1_0::RecordName rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::offload::V1_0::RecordName rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::offload::V1_0::RecordName lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::offload::V1_0::RecordName e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::offload::V1_0::RecordName e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::LogRecord& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::LogRecord& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::LogRecord& lhs, const ::android::hardware::wifi::offload::V1_0::LogRecord& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::LogRecord& lhs, const ::android::hardware::wifi::offload::V1_0::LogRecord& rhs);

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanStats& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanStats& lhs, const ::android::hardware::wifi::offload::V1_0::ScanStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanStats& lhs, const ::android::hardware::wifi::offload::V1_0::ScanStats& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::offload::V1_0::OffloadStatusCode o);
static inline void PrintTo(::android::hardware::wifi::offload::V1_0::OffloadStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::offload::V1_0::OffloadStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& o);
static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatus& rhs);
static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::offload::V1_0::SecurityMode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::offload::V1_0::SecurityMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::offload::V1_0::SecurityMode::OPEN) == static_cast<uint8_t>(::android::hardware::wifi::offload::V1_0::SecurityMode::OPEN)) {
        os += (first ? "" : " | ");
        os += "OPEN";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::SecurityMode::OPEN;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::SecurityMode::WEP) == static_cast<uint8_t>(::android::hardware::wifi::offload::V1_0::SecurityMode::WEP)) {
        os += (first ? "" : " | ");
        os += "WEP";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::SecurityMode::WEP;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::SecurityMode::PSK) == static_cast<uint8_t>(::android::hardware::wifi::offload::V1_0::SecurityMode::PSK)) {
        os += (first ? "" : " | ");
        os += "PSK";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::SecurityMode::PSK;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::SecurityMode::EAP) == static_cast<uint8_t>(::android::hardware::wifi::offload::V1_0::SecurityMode::EAP)) {
        os += (first ? "" : " | ");
        os += "EAP";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::SecurityMode::EAP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::offload::V1_0::SecurityMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::offload::V1_0::SecurityMode::OPEN) {
        return "OPEN";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::SecurityMode::WEP) {
        return "WEP";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::SecurityMode::PSK) {
        return "PSK";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::SecurityMode::EAP) {
        return "EAP";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::offload::V1_0::SecurityMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ssid = ";
    os += ::android::hardware::toString(o.ssid);
    os += ", .flags = ";
    os += ::android::hardware::wifi::offload::V1_0::toString<::android::hardware::wifi::offload::V1_0::SecurityMode>(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& lhs, const ::android::hardware::wifi::offload::V1_0::NetworkInfo& rhs) {
    if (lhs.ssid != rhs.ssid) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::NetworkInfo& lhs, const ::android::hardware::wifi::offload::V1_0::NetworkInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::offload::V1_0::Capability>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::offload::V1_0::Capability> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::ESS) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::ESS)) {
        os += (first ? "" : " | ");
        os += "ESS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::ESS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::IBSS) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::IBSS)) {
        os += (first ? "" : " | ");
        os += "IBSS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::IBSS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::CF_POLLABLE) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::CF_POLLABLE)) {
        os += (first ? "" : " | ");
        os += "CF_POLLABLE";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::CF_POLLABLE;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::CF_PLL_REQ) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::CF_PLL_REQ)) {
        os += (first ? "" : " | ");
        os += "CF_PLL_REQ";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::CF_PLL_REQ;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::PRIVACY) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::PRIVACY)) {
        os += (first ? "" : " | ");
        os += "PRIVACY";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::PRIVACY;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::SHORT_PREAMBLE) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::SHORT_PREAMBLE)) {
        os += (first ? "" : " | ");
        os += "SHORT_PREAMBLE";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::SHORT_PREAMBLE;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::PBCC) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::PBCC)) {
        os += (first ? "" : " | ");
        os += "PBCC";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::PBCC;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::CHANNEL_AGILITY) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::CHANNEL_AGILITY)) {
        os += (first ? "" : " | ");
        os += "CHANNEL_AGILITY";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::CHANNEL_AGILITY;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::SPECTURM_MGMT) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::SPECTURM_MGMT)) {
        os += (first ? "" : " | ");
        os += "SPECTURM_MGMT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::SPECTURM_MGMT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::QOS) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::QOS)) {
        os += (first ? "" : " | ");
        os += "QOS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::QOS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::SHORT_SLOT_TIME) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::SHORT_SLOT_TIME)) {
        os += (first ? "" : " | ");
        os += "SHORT_SLOT_TIME";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::SHORT_SLOT_TIME;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::APSD) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::APSD)) {
        os += (first ? "" : " | ");
        os += "APSD";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::APSD;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::RADIO_MEASUREMENT) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::RADIO_MEASUREMENT)) {
        os += (first ? "" : " | ");
        os += "RADIO_MEASUREMENT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::RADIO_MEASUREMENT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::DSSS_OFDM) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::DSSS_OFDM)) {
        os += (first ? "" : " | ");
        os += "DSSS_OFDM";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::DSSS_OFDM;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::DELAYED_BLOCK_ACK) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::DELAYED_BLOCK_ACK)) {
        os += (first ? "" : " | ");
        os += "DELAYED_BLOCK_ACK";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::DELAYED_BLOCK_ACK;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::Capability::IMMEDIATE_BLOCK_ACK) == static_cast<uint16_t>(::android::hardware::wifi::offload::V1_0::Capability::IMMEDIATE_BLOCK_ACK)) {
        os += (first ? "" : " | ");
        os += "IMMEDIATE_BLOCK_ACK";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::Capability::IMMEDIATE_BLOCK_ACK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::offload::V1_0::Capability o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::ESS) {
        return "ESS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::IBSS) {
        return "IBSS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::CF_POLLABLE) {
        return "CF_POLLABLE";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::CF_PLL_REQ) {
        return "CF_PLL_REQ";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::PRIVACY) {
        return "PRIVACY";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::SHORT_PREAMBLE) {
        return "SHORT_PREAMBLE";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::PBCC) {
        return "PBCC";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::CHANNEL_AGILITY) {
        return "CHANNEL_AGILITY";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::SPECTURM_MGMT) {
        return "SPECTURM_MGMT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::QOS) {
        return "QOS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::SHORT_SLOT_TIME) {
        return "SHORT_SLOT_TIME";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::APSD) {
        return "APSD";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::RADIO_MEASUREMENT) {
        return "RADIO_MEASUREMENT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::DSSS_OFDM) {
        return "DSSS_OFDM";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::DELAYED_BLOCK_ACK) {
        return "DELAYED_BLOCK_ACK";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::Capability::IMMEDIATE_BLOCK_ACK) {
        return "IMMEDIATE_BLOCK_ACK";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::offload::V1_0::Capability o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".networkInfo = ";
    os += ::android::hardware::wifi::offload::V1_0::toString(o.networkInfo);
    os += ", .bssid = ";
    os += ::android::hardware::toString(o.bssid);
    os += ", .capability = ";
    os += ::android::hardware::wifi::offload::V1_0::toString<::android::hardware::wifi::offload::V1_0::Capability>(o.capability);
    os += ", .frequency = ";
    os += ::android::hardware::toString(o.frequency);
    os += ", .rssi = ";
    os += ::android::hardware::toString(o.rssi);
    os += ", .tsf = ";
    os += ::android::hardware::toString(o.tsf);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanResult& lhs, const ::android::hardware::wifi::offload::V1_0::ScanResult& rhs) {
    if (lhs.networkInfo != rhs.networkInfo) {
        return false;
    }
    if (lhs.bssid != rhs.bssid) {
        return false;
    }
    if (lhs.capability != rhs.capability) {
        return false;
    }
    if (lhs.frequency != rhs.frequency) {
        return false;
    }
    if (lhs.rssi != rhs.rssi) {
        return false;
    }
    if (lhs.tsf != rhs.tsf) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanResult& lhs, const ::android::hardware::wifi::offload::V1_0::ScanResult& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanParam& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ssidList = ";
    os += ::android::hardware::toString(o.ssidList);
    os += ", .frequencyList = ";
    os += ::android::hardware::toString(o.frequencyList);
    os += ", .disconnectedModeScanIntervalMs = ";
    os += ::android::hardware::toString(o.disconnectedModeScanIntervalMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanParam& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanParam& lhs, const ::android::hardware::wifi::offload::V1_0::ScanParam& rhs) {
    if (lhs.ssidList != rhs.ssidList) {
        return false;
    }
    if (lhs.frequencyList != rhs.frequencyList) {
        return false;
    }
    if (lhs.disconnectedModeScanIntervalMs != rhs.disconnectedModeScanIntervalMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanParam& lhs, const ::android::hardware::wifi::offload::V1_0::ScanParam& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanFilter& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".preferredNetworkInfoList = ";
    os += ::android::hardware::toString(o.preferredNetworkInfoList);
    os += ", .rssiThreshold = ";
    os += ::android::hardware::toString(o.rssiThreshold);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanFilter& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanFilter& lhs, const ::android::hardware::wifi::offload::V1_0::ScanFilter& rhs) {
    if (lhs.preferredNetworkInfoList != rhs.preferredNetworkInfoList) {
        return false;
    }
    if (lhs.rssiThreshold != rhs.rssiThreshold) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanFilter& lhs, const ::android::hardware::wifi::offload::V1_0::ScanFilter& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanRecord& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".durationMs = ";
    os += ::android::hardware::toString(o.durationMs);
    os += ", .numChannelsScanned = ";
    os += ::android::hardware::toString(o.numChannelsScanned);
    os += ", .numEntriesAggregated = ";
    os += ::android::hardware::toString(o.numEntriesAggregated);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanRecord& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanRecord& lhs, const ::android::hardware::wifi::offload::V1_0::ScanRecord& rhs) {
    if (lhs.durationMs != rhs.durationMs) {
        return false;
    }
    if (lhs.numChannelsScanned != rhs.numChannelsScanned) {
        return false;
    }
    if (lhs.numEntriesAggregated != rhs.numEntriesAggregated) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanRecord& lhs, const ::android::hardware::wifi::offload::V1_0::ScanRecord& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::offload::V1_0::RecordName>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::offload::V1_0::RecordName> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_BASE) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_BASE)) {
        os += (first ? "" : " | ");
        os += "CMD_BASE";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_BASE;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_INT) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_INT)) {
        os += (first ? "" : " | ");
        os += "CMD_INT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_INT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_CONFIG_SCANS) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_CONFIG_SCANS)) {
        os += (first ? "" : " | ");
        os += "CMD_CONFIG_SCANS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_CONFIG_SCANS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_SUBSCRIBE_SCAN_RESULTS) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_SUBSCRIBE_SCAN_RESULTS)) {
        os += (first ? "" : " | ");
        os += "CMD_SUBSCRIBE_SCAN_RESULTS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_SUBSCRIBE_SCAN_RESULTS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_UNSUBSCRIBE_SCAN_RESULTS) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_UNSUBSCRIBE_SCAN_RESULTS)) {
        os += (first ? "" : " | ");
        os += "CMD_UNSUBSCRIBE_SCAN_RESULTS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_UNSUBSCRIBE_SCAN_RESULTS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_GET_SCAN_STATS) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_GET_SCAN_STATS)) {
        os += (first ? "" : " | ");
        os += "CMD_GET_SCAN_STATS";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_GET_SCAN_STATS;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::CMD_RESET) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::CMD_RESET)) {
        os += (first ? "" : " | ");
        os += "CMD_RESET";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::CMD_RESET;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_BASE) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_BASE)) {
        os += (first ? "" : " | ");
        os += "EVENT_RECVD_BASE";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_BASE;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT_ASYNC) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT_ASYNC)) {
        os += (first ? "" : " | ");
        os += "EVENT_RECVD_SCAN_RESULT_ASYNC";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT_ASYNC;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT)) {
        os += (first ? "" : " | ");
        os += "EVENT_RECVD_SCAN_RESULT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_BASE) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_BASE)) {
        os += (first ? "" : " | ");
        os += "EVENT_SENT_BASE";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_BASE;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_SCAN_RESULT) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_SCAN_RESULT)) {
        os += (first ? "" : " | ");
        os += "EVENT_SENT_SCAN_RESULT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_SCAN_RESULT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ABORT) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ABORT)) {
        os += (first ? "" : " | ");
        os += "EVENT_SENT_ABORT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ABORT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ERROR) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ERROR)) {
        os += (first ? "" : " | ");
        os += "EVENT_SENT_ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ERROR;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::REQ_BASE) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::REQ_BASE)) {
        os += (first ? "" : " | ");
        os += "REQ_BASE";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::REQ_BASE;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::RecordName::REQ_SCAN) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::RecordName::REQ_SCAN)) {
        os += (first ? "" : " | ");
        os += "REQ_SCAN";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::RecordName::REQ_SCAN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::offload::V1_0::RecordName o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_BASE) {
        return "CMD_BASE";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_INT) {
        return "CMD_INT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_CONFIG_SCANS) {
        return "CMD_CONFIG_SCANS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_SUBSCRIBE_SCAN_RESULTS) {
        return "CMD_SUBSCRIBE_SCAN_RESULTS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_UNSUBSCRIBE_SCAN_RESULTS) {
        return "CMD_UNSUBSCRIBE_SCAN_RESULTS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_GET_SCAN_STATS) {
        return "CMD_GET_SCAN_STATS";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::CMD_RESET) {
        return "CMD_RESET";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_BASE) {
        return "EVENT_RECVD_BASE";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT_ASYNC) {
        return "EVENT_RECVD_SCAN_RESULT_ASYNC";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT) {
        return "EVENT_RECVD_SCAN_RESULT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_BASE) {
        return "EVENT_SENT_BASE";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_SCAN_RESULT) {
        return "EVENT_SENT_SCAN_RESULT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ABORT) {
        return "EVENT_SENT_ABORT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ERROR) {
        return "EVENT_SENT_ERROR";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::REQ_BASE) {
        return "REQ_BASE";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::RecordName::REQ_SCAN) {
        return "REQ_SCAN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::offload::V1_0::RecordName o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::LogRecord& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".recordName = ";
    os += ::android::hardware::wifi::offload::V1_0::toString(o.recordName);
    os += ", .logTimeMs = ";
    os += ::android::hardware::toString(o.logTimeMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::LogRecord& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::LogRecord& lhs, const ::android::hardware::wifi::offload::V1_0::LogRecord& rhs) {
    if (lhs.recordName != rhs.recordName) {
        return false;
    }
    if (lhs.logTimeMs != rhs.logTimeMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::LogRecord& lhs, const ::android::hardware::wifi::offload::V1_0::LogRecord& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::ScanStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".numScansRequestedByWifi = ";
    os += ::android::hardware::toString(o.numScansRequestedByWifi);
    os += ", .numScansServicedByWifi = ";
    os += ::android::hardware::toString(o.numScansServicedByWifi);
    os += ", .numScansServicedbyCache = ";
    os += ::android::hardware::toString(o.numScansServicedbyCache);
    os += ", .lastUpdated = ";
    os += ::android::hardware::toString(o.lastUpdated);
    os += ", .lastRead = ";
    os += ::android::hardware::toString(o.lastRead);
    os += ", .subscriptionDurationMs = ";
    os += ::android::hardware::toString(o.subscriptionDurationMs);
    os += ", .histogramChannelsScanned = ";
    os += ::android::hardware::toString(o.histogramChannelsScanned);
    os += ", .scanRecord = ";
    os += ::android::hardware::toString(o.scanRecord);
    os += ", .logRecord = ";
    os += ::android::hardware::toString(o.logRecord);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::ScanStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::ScanStats& lhs, const ::android::hardware::wifi::offload::V1_0::ScanStats& rhs) {
    if (lhs.numScansRequestedByWifi != rhs.numScansRequestedByWifi) {
        return false;
    }
    if (lhs.numScansServicedByWifi != rhs.numScansServicedByWifi) {
        return false;
    }
    if (lhs.numScansServicedbyCache != rhs.numScansServicedbyCache) {
        return false;
    }
    if (lhs.lastUpdated != rhs.lastUpdated) {
        return false;
    }
    if (lhs.lastRead != rhs.lastRead) {
        return false;
    }
    if (lhs.subscriptionDurationMs != rhs.subscriptionDurationMs) {
        return false;
    }
    if (lhs.histogramChannelsScanned != rhs.histogramChannelsScanned) {
        return false;
    }
    if (lhs.scanRecord != rhs.scanRecord) {
        return false;
    }
    if (lhs.logRecord != rhs.logRecord) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::ScanStats& lhs, const ::android::hardware::wifi::offload::V1_0::ScanStats& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::offload::V1_0::OffloadStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::offload::V1_0::OffloadStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::OK) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::OffloadStatusCode::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::OK;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::NO_CONNECTION) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::OffloadStatusCode::NO_CONNECTION)) {
        os += (first ? "" : " | ");
        os += "NO_CONNECTION";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::NO_CONNECTION;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::OffloadStatusCode::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::ERROR) == static_cast<uint32_t>(::android::hardware::wifi::offload::V1_0::OffloadStatusCode::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::offload::V1_0::OffloadStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::OK) {
        return "OK";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::NO_CONNECTION) {
        return "NO_CONNECTION";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::offload::V1_0::OffloadStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".code = ";
    os += ::android::hardware::wifi::offload::V1_0::toString(o.code);
    os += ", .description = ";
    os += ::android::hardware::toString(o.description);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatus& rhs) {
    if (lhs.code != rhs.code) {
        return false;
    }
    if (lhs.description != rhs.description) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::offload::V1_0::OffloadStatus& lhs, const ::android::hardware::wifi::offload::V1_0::OffloadStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace offload
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
template<> inline constexpr std::array<::android::hardware::wifi::offload::V1_0::SecurityMode, 4> hidl_enum_values<::android::hardware::wifi::offload::V1_0::SecurityMode> = {
    ::android::hardware::wifi::offload::V1_0::SecurityMode::OPEN,
    ::android::hardware::wifi::offload::V1_0::SecurityMode::WEP,
    ::android::hardware::wifi::offload::V1_0::SecurityMode::PSK,
    ::android::hardware::wifi::offload::V1_0::SecurityMode::EAP,
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
template<> inline constexpr std::array<::android::hardware::wifi::offload::V1_0::Capability, 16> hidl_enum_values<::android::hardware::wifi::offload::V1_0::Capability> = {
    ::android::hardware::wifi::offload::V1_0::Capability::ESS,
    ::android::hardware::wifi::offload::V1_0::Capability::IBSS,
    ::android::hardware::wifi::offload::V1_0::Capability::CF_POLLABLE,
    ::android::hardware::wifi::offload::V1_0::Capability::CF_PLL_REQ,
    ::android::hardware::wifi::offload::V1_0::Capability::PRIVACY,
    ::android::hardware::wifi::offload::V1_0::Capability::SHORT_PREAMBLE,
    ::android::hardware::wifi::offload::V1_0::Capability::PBCC,
    ::android::hardware::wifi::offload::V1_0::Capability::CHANNEL_AGILITY,
    ::android::hardware::wifi::offload::V1_0::Capability::SPECTURM_MGMT,
    ::android::hardware::wifi::offload::V1_0::Capability::QOS,
    ::android::hardware::wifi::offload::V1_0::Capability::SHORT_SLOT_TIME,
    ::android::hardware::wifi::offload::V1_0::Capability::APSD,
    ::android::hardware::wifi::offload::V1_0::Capability::RADIO_MEASUREMENT,
    ::android::hardware::wifi::offload::V1_0::Capability::DSSS_OFDM,
    ::android::hardware::wifi::offload::V1_0::Capability::DELAYED_BLOCK_ACK,
    ::android::hardware::wifi::offload::V1_0::Capability::IMMEDIATE_BLOCK_ACK,
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
template<> inline constexpr std::array<::android::hardware::wifi::offload::V1_0::RecordName, 16> hidl_enum_values<::android::hardware::wifi::offload::V1_0::RecordName> = {
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_BASE,
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_INT,
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_CONFIG_SCANS,
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_SUBSCRIBE_SCAN_RESULTS,
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_UNSUBSCRIBE_SCAN_RESULTS,
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_GET_SCAN_STATS,
    ::android::hardware::wifi::offload::V1_0::RecordName::CMD_RESET,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_BASE,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT_ASYNC,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_RECVD_SCAN_RESULT,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_BASE,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_SCAN_RESULT,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ABORT,
    ::android::hardware::wifi::offload::V1_0::RecordName::EVENT_SENT_ERROR,
    ::android::hardware::wifi::offload::V1_0::RecordName::REQ_BASE,
    ::android::hardware::wifi::offload::V1_0::RecordName::REQ_SCAN,
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
template<> inline constexpr std::array<::android::hardware::wifi::offload::V1_0::OffloadStatusCode, 4> hidl_enum_values<::android::hardware::wifi::offload::V1_0::OffloadStatusCode> = {
    ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::OK,
    ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::NO_CONNECTION,
    ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::TIMEOUT,
    ::android::hardware::wifi::offload::V1_0::OffloadStatusCode::ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_OFFLOAD_V1_0_TYPES_H
