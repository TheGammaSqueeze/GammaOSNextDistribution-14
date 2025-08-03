#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/LteVopsInfo.h>
#include <aidl/android/hardware/radio/network/NrIndicators.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class EutranRegistrationInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::LteVopsInfo lteVopsInfo;
  ::aidl::android::hardware::radio::network::NrIndicators nrIndicators;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EutranRegistrationInfo& rhs) const {
    return std::tie(lteVopsInfo, nrIndicators) != std::tie(rhs.lteVopsInfo, rhs.nrIndicators);
  }
  inline bool operator<(const EutranRegistrationInfo& rhs) const {
    return std::tie(lteVopsInfo, nrIndicators) < std::tie(rhs.lteVopsInfo, rhs.nrIndicators);
  }
  inline bool operator<=(const EutranRegistrationInfo& rhs) const {
    return std::tie(lteVopsInfo, nrIndicators) <= std::tie(rhs.lteVopsInfo, rhs.nrIndicators);
  }
  inline bool operator==(const EutranRegistrationInfo& rhs) const {
    return std::tie(lteVopsInfo, nrIndicators) == std::tie(rhs.lteVopsInfo, rhs.nrIndicators);
  }
  inline bool operator>(const EutranRegistrationInfo& rhs) const {
    return std::tie(lteVopsInfo, nrIndicators) > std::tie(rhs.lteVopsInfo, rhs.nrIndicators);
  }
  inline bool operator>=(const EutranRegistrationInfo& rhs) const {
    return std::tie(lteVopsInfo, nrIndicators) >= std::tie(rhs.lteVopsInfo, rhs.nrIndicators);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EutranRegistrationInfo{";
    os << "lteVopsInfo: " << ::android::internal::ToString(lteVopsInfo);
    os << ", nrIndicators: " << ::android::internal::ToString(nrIndicators);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
