#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/LegacyMode.h>
#include <aidl/android/hardware/wifi/supplicant/WifiTechnology.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ConnectionCapabilities {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::wifi::supplicant::WifiTechnology technology = ::aidl::android::hardware::wifi::supplicant::WifiTechnology(0);
  int32_t channelBandwidth = 0;
  int32_t maxNumberTxSpatialStreams = 0;
  int32_t maxNumberRxSpatialStreams = 0;
  ::aidl::android::hardware::wifi::supplicant::LegacyMode legacyMode = ::aidl::android::hardware::wifi::supplicant::LegacyMode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ConnectionCapabilities& rhs) const {
    return std::tie(technology, channelBandwidth, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams, legacyMode) != std::tie(rhs.technology, rhs.channelBandwidth, rhs.maxNumberTxSpatialStreams, rhs.maxNumberRxSpatialStreams, rhs.legacyMode);
  }
  inline bool operator<(const ConnectionCapabilities& rhs) const {
    return std::tie(technology, channelBandwidth, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams, legacyMode) < std::tie(rhs.technology, rhs.channelBandwidth, rhs.maxNumberTxSpatialStreams, rhs.maxNumberRxSpatialStreams, rhs.legacyMode);
  }
  inline bool operator<=(const ConnectionCapabilities& rhs) const {
    return std::tie(technology, channelBandwidth, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams, legacyMode) <= std::tie(rhs.technology, rhs.channelBandwidth, rhs.maxNumberTxSpatialStreams, rhs.maxNumberRxSpatialStreams, rhs.legacyMode);
  }
  inline bool operator==(const ConnectionCapabilities& rhs) const {
    return std::tie(technology, channelBandwidth, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams, legacyMode) == std::tie(rhs.technology, rhs.channelBandwidth, rhs.maxNumberTxSpatialStreams, rhs.maxNumberRxSpatialStreams, rhs.legacyMode);
  }
  inline bool operator>(const ConnectionCapabilities& rhs) const {
    return std::tie(technology, channelBandwidth, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams, legacyMode) > std::tie(rhs.technology, rhs.channelBandwidth, rhs.maxNumberTxSpatialStreams, rhs.maxNumberRxSpatialStreams, rhs.legacyMode);
  }
  inline bool operator>=(const ConnectionCapabilities& rhs) const {
    return std::tie(technology, channelBandwidth, maxNumberTxSpatialStreams, maxNumberRxSpatialStreams, legacyMode) >= std::tie(rhs.technology, rhs.channelBandwidth, rhs.maxNumberTxSpatialStreams, rhs.maxNumberRxSpatialStreams, rhs.legacyMode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ConnectionCapabilities{";
    os << "technology: " << ::android::internal::ToString(technology);
    os << ", channelBandwidth: " << ::android::internal::ToString(channelBandwidth);
    os << ", maxNumberTxSpatialStreams: " << ::android::internal::ToString(maxNumberTxSpatialStreams);
    os << ", maxNumberRxSpatialStreams: " << ::android::internal::ToString(maxNumberRxSpatialStreams);
    os << ", legacyMode: " << ::android::internal::ToString(legacyMode);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
