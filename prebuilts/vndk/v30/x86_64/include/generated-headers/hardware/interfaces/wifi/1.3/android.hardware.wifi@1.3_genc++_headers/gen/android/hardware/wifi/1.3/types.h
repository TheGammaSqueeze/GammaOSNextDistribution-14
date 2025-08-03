#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_TYPES_H

#include <android/hardware/wifi/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_3 {

// Forward declaration for forward reference support:
struct WifiChannelStats;
struct StaLinkLayerRadioStats;
struct StaLinkLayerStats;

struct WifiChannelStats final {
    /**
     * Channel information.
     */
    ::android::hardware::wifi::V1_0::WifiChannelInfo channel __attribute__ ((aligned(4)));
    /**
     * Total time for which the radio is awake on this channel.
     */
    uint32_t onTimeInMs __attribute__ ((aligned(4)));
    /**
     * Total time for which CCA is held busy on this channel.
     */
    uint32_t ccaBusyTimeInMs __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::wifi::V1_3::WifiChannelStats, channel) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::WifiChannelStats, onTimeInMs) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::WifiChannelStats, ccaBusyTimeInMs) == 20, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_3::WifiChannelStats) == 24, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_3::WifiChannelStats) == 4, "wrong alignment");

struct StaLinkLayerRadioStats final {
    /**
     * Baseline information as defined in HAL 1.0.
     */
    ::android::hardware::wifi::V1_0::StaLinkLayerRadioStats V1_0 __attribute__ ((aligned(8)));
    /**
     * Total time for which the radio is awake due to NAN scan since boot or crash.
     */
    uint32_t onTimeInMsForNanScan __attribute__ ((aligned(4)));
    /**
     * Total time for which the radio is awake due to background scan since boot or crash.
     */
    uint32_t onTimeInMsForBgScan __attribute__ ((aligned(4)));
    /**
     * Total time for which the radio is awake due to roam scan since boot or crash.
     */
    uint32_t onTimeInMsForRoamScan __attribute__ ((aligned(4)));
    /**
     * Total time for which the radio is awake due to PNO scan since boot or crash.
     */
    uint32_t onTimeInMsForPnoScan __attribute__ ((aligned(4)));
    /**
     * Total time for which the radio is awake due to Hotspot 2.0 scans and GAS exchange since boot
     * or crash.
     */
    uint32_t onTimeInMsForHs20Scan __attribute__ ((aligned(4)));
    /**
     * List of channel stats associated with this radio
     */
    ::android::hardware::hidl_vec<::android::hardware::wifi::V1_3::WifiChannelStats> channelStats __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, V1_0) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, onTimeInMsForNanScan) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, onTimeInMsForBgScan) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, onTimeInMsForRoamScan) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, onTimeInMsForPnoScan) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, onTimeInMsForHs20Scan) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats, channelStats) == 56, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats) == 72, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_3::StaLinkLayerRadioStats) == 8, "wrong alignment");

/**
 * Link layer stats retrieved via |getLinkLayerStats|.
 */
struct StaLinkLayerStats final {
    ::android::hardware::wifi::V1_0::StaLinkLayerIfaceStats iface __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::wifi::V1_3::StaLinkLayerRadioStats> radios __attribute__ ((aligned(8)));
    /**
     * TimeStamp for each stats sample.
     * This is the absolute milliseconds from boot when these stats were
     * sampled.
     */
    uint64_t timeStampInMs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerStats, iface) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerStats, radios) == 136, "wrong offset");
static_assert(offsetof(::android::hardware::wifi::V1_3::StaLinkLayerStats, timeStampInMs) == 152, "wrong offset");
static_assert(sizeof(::android::hardware::wifi::V1_3::StaLinkLayerStats) == 160, "wrong size");
static_assert(__alignof(::android::hardware::wifi::V1_3::StaLinkLayerStats) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::wifi::V1_3::WifiChannelStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_3::WifiChannelStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_3::WifiChannelStats& lhs, const ::android::hardware::wifi::V1_3::WifiChannelStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_3::WifiChannelStats& lhs, const ::android::hardware::wifi::V1_3::WifiChannelStats& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerStats& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerStats& rhs);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::wifi::V1_3::WifiChannelStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".channel = ";
    os += ::android::hardware::wifi::V1_0::toString(o.channel);
    os += ", .onTimeInMs = ";
    os += ::android::hardware::toString(o.onTimeInMs);
    os += ", .ccaBusyTimeInMs = ";
    os += ::android::hardware::toString(o.ccaBusyTimeInMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_3::WifiChannelStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_3::WifiChannelStats& lhs, const ::android::hardware::wifi::V1_3::WifiChannelStats& rhs) {
    if (lhs.channel != rhs.channel) {
        return false;
    }
    if (lhs.onTimeInMs != rhs.onTimeInMs) {
        return false;
    }
    if (lhs.ccaBusyTimeInMs != rhs.ccaBusyTimeInMs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_3::WifiChannelStats& lhs, const ::android::hardware::wifi::V1_3::WifiChannelStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".V1_0 = ";
    os += ::android::hardware::wifi::V1_0::toString(o.V1_0);
    os += ", .onTimeInMsForNanScan = ";
    os += ::android::hardware::toString(o.onTimeInMsForNanScan);
    os += ", .onTimeInMsForBgScan = ";
    os += ::android::hardware::toString(o.onTimeInMsForBgScan);
    os += ", .onTimeInMsForRoamScan = ";
    os += ::android::hardware::toString(o.onTimeInMsForRoamScan);
    os += ", .onTimeInMsForPnoScan = ";
    os += ::android::hardware::toString(o.onTimeInMsForPnoScan);
    os += ", .onTimeInMsForHs20Scan = ";
    os += ::android::hardware::toString(o.onTimeInMsForHs20Scan);
    os += ", .channelStats = ";
    os += ::android::hardware::toString(o.channelStats);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& rhs) {
    if (lhs.V1_0 != rhs.V1_0) {
        return false;
    }
    if (lhs.onTimeInMsForNanScan != rhs.onTimeInMsForNanScan) {
        return false;
    }
    if (lhs.onTimeInMsForBgScan != rhs.onTimeInMsForBgScan) {
        return false;
    }
    if (lhs.onTimeInMsForRoamScan != rhs.onTimeInMsForRoamScan) {
        return false;
    }
    if (lhs.onTimeInMsForPnoScan != rhs.onTimeInMsForPnoScan) {
        return false;
    }
    if (lhs.onTimeInMsForHs20Scan != rhs.onTimeInMsForHs20Scan) {
        return false;
    }
    if (lhs.channelStats != rhs.channelStats) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerRadioStats& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& o) {
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

static inline void PrintTo(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerStats& rhs) {
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

static inline bool operator!=(const ::android::hardware::wifi::V1_3::StaLinkLayerStats& lhs, const ::android::hardware::wifi::V1_3::StaLinkLayerStats& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_3
}  // namespace wifi
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_TYPES_H
