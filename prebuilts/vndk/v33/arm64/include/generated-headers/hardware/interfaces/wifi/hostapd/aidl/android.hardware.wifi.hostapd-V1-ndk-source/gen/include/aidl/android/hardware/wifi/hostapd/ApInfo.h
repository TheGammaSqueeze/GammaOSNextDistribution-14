#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/hostapd/ChannelBandwidth.h>
#include <aidl/android/hardware/wifi/hostapd/Generation.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class ApInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string ifaceName;
  std::string apIfaceInstance;
  int32_t freqMhz = 0;
  ::aidl::android::hardware::wifi::hostapd::ChannelBandwidth channelBandwidth = ::aidl::android::hardware::wifi::hostapd::ChannelBandwidth(0);
  ::aidl::android::hardware::wifi::hostapd::Generation generation = ::aidl::android::hardware::wifi::hostapd::Generation(0);
  std::vector<uint8_t> apIfaceInstanceMacAddress;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ApInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, freqMhz, channelBandwidth, generation, apIfaceInstanceMacAddress) != std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.freqMhz, rhs.channelBandwidth, rhs.generation, rhs.apIfaceInstanceMacAddress);
  }
  inline bool operator<(const ApInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, freqMhz, channelBandwidth, generation, apIfaceInstanceMacAddress) < std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.freqMhz, rhs.channelBandwidth, rhs.generation, rhs.apIfaceInstanceMacAddress);
  }
  inline bool operator<=(const ApInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, freqMhz, channelBandwidth, generation, apIfaceInstanceMacAddress) <= std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.freqMhz, rhs.channelBandwidth, rhs.generation, rhs.apIfaceInstanceMacAddress);
  }
  inline bool operator==(const ApInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, freqMhz, channelBandwidth, generation, apIfaceInstanceMacAddress) == std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.freqMhz, rhs.channelBandwidth, rhs.generation, rhs.apIfaceInstanceMacAddress);
  }
  inline bool operator>(const ApInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, freqMhz, channelBandwidth, generation, apIfaceInstanceMacAddress) > std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.freqMhz, rhs.channelBandwidth, rhs.generation, rhs.apIfaceInstanceMacAddress);
  }
  inline bool operator>=(const ApInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, freqMhz, channelBandwidth, generation, apIfaceInstanceMacAddress) >= std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.freqMhz, rhs.channelBandwidth, rhs.generation, rhs.apIfaceInstanceMacAddress);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ApInfo{";
    os << "ifaceName: " << ::android::internal::ToString(ifaceName);
    os << ", apIfaceInstance: " << ::android::internal::ToString(apIfaceInstance);
    os << ", freqMhz: " << ::android::internal::ToString(freqMhz);
    os << ", channelBandwidth: " << ::android::internal::ToString(channelBandwidth);
    os << ", generation: " << ::android::internal::ToString(generation);
    os << ", apIfaceInstanceMacAddress: " << ::android::internal::ToString(apIfaceInstanceMacAddress);
    os << "}";
    return os.str();
  }
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
