#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/AccessNetwork.h>
#include <aidl/android/hardware/radio/network/RadioAccessSpecifierBands.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class RadioAccessSpecifier {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::AccessNetwork accessNetwork = ::aidl::android::hardware::radio::AccessNetwork(0);
  ::aidl::android::hardware::radio::network::RadioAccessSpecifierBands bands;
  std::vector<int32_t> channels;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RadioAccessSpecifier& rhs) const {
    return std::tie(accessNetwork, bands, channels) != std::tie(rhs.accessNetwork, rhs.bands, rhs.channels);
  }
  inline bool operator<(const RadioAccessSpecifier& rhs) const {
    return std::tie(accessNetwork, bands, channels) < std::tie(rhs.accessNetwork, rhs.bands, rhs.channels);
  }
  inline bool operator<=(const RadioAccessSpecifier& rhs) const {
    return std::tie(accessNetwork, bands, channels) <= std::tie(rhs.accessNetwork, rhs.bands, rhs.channels);
  }
  inline bool operator==(const RadioAccessSpecifier& rhs) const {
    return std::tie(accessNetwork, bands, channels) == std::tie(rhs.accessNetwork, rhs.bands, rhs.channels);
  }
  inline bool operator>(const RadioAccessSpecifier& rhs) const {
    return std::tie(accessNetwork, bands, channels) > std::tie(rhs.accessNetwork, rhs.bands, rhs.channels);
  }
  inline bool operator>=(const RadioAccessSpecifier& rhs) const {
    return std::tie(accessNetwork, bands, channels) >= std::tie(rhs.accessNetwork, rhs.bands, rhs.channels);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RadioAccessSpecifier{";
    os << "accessNetwork: " << ::android::internal::ToString(accessNetwork);
    os << ", bands: " << ::android::internal::ToString(bands);
    os << ", channels: " << ::android::internal::ToString(channels);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
