#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/hostapd/BandMask.h>
#include <aidl/android/hardware/wifi/hostapd/FrequencyRange.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class ChannelParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::wifi::hostapd::BandMask bandMask = ::aidl::android::hardware::wifi::hostapd::BandMask(0);
  std::vector<::aidl::android::hardware::wifi::hostapd::FrequencyRange> acsChannelFreqRangesMhz;
  bool enableAcs = false;
  bool acsShouldExcludeDfs = false;
  int32_t channel = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ChannelParams& rhs) const {
    return std::tie(bandMask, acsChannelFreqRangesMhz, enableAcs, acsShouldExcludeDfs, channel) != std::tie(rhs.bandMask, rhs.acsChannelFreqRangesMhz, rhs.enableAcs, rhs.acsShouldExcludeDfs, rhs.channel);
  }
  inline bool operator<(const ChannelParams& rhs) const {
    return std::tie(bandMask, acsChannelFreqRangesMhz, enableAcs, acsShouldExcludeDfs, channel) < std::tie(rhs.bandMask, rhs.acsChannelFreqRangesMhz, rhs.enableAcs, rhs.acsShouldExcludeDfs, rhs.channel);
  }
  inline bool operator<=(const ChannelParams& rhs) const {
    return std::tie(bandMask, acsChannelFreqRangesMhz, enableAcs, acsShouldExcludeDfs, channel) <= std::tie(rhs.bandMask, rhs.acsChannelFreqRangesMhz, rhs.enableAcs, rhs.acsShouldExcludeDfs, rhs.channel);
  }
  inline bool operator==(const ChannelParams& rhs) const {
    return std::tie(bandMask, acsChannelFreqRangesMhz, enableAcs, acsShouldExcludeDfs, channel) == std::tie(rhs.bandMask, rhs.acsChannelFreqRangesMhz, rhs.enableAcs, rhs.acsShouldExcludeDfs, rhs.channel);
  }
  inline bool operator>(const ChannelParams& rhs) const {
    return std::tie(bandMask, acsChannelFreqRangesMhz, enableAcs, acsShouldExcludeDfs, channel) > std::tie(rhs.bandMask, rhs.acsChannelFreqRangesMhz, rhs.enableAcs, rhs.acsShouldExcludeDfs, rhs.channel);
  }
  inline bool operator>=(const ChannelParams& rhs) const {
    return std::tie(bandMask, acsChannelFreqRangesMhz, enableAcs, acsShouldExcludeDfs, channel) >= std::tie(rhs.bandMask, rhs.acsChannelFreqRangesMhz, rhs.enableAcs, rhs.acsShouldExcludeDfs, rhs.channel);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ChannelParams{";
    os << "bandMask: " << ::android::internal::ToString(bandMask);
    os << ", acsChannelFreqRangesMhz: " << ::android::internal::ToString(acsChannelFreqRangesMhz);
    os << ", enableAcs: " << ::android::internal::ToString(enableAcs);
    os << ", acsShouldExcludeDfs: " << ::android::internal::ToString(acsShouldExcludeDfs);
    os << ", channel: " << ::android::internal::ToString(channel);
    os << "}";
    return os.str();
  }
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
