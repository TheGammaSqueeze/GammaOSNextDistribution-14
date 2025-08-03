#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CellIdentityWcdma.h>
#include <aidl/android/hardware/radio/network/WcdmaSignalStrength.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellInfoWcdma {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::CellIdentityWcdma cellIdentityWcdma;
  ::aidl::android::hardware::radio::network::WcdmaSignalStrength signalStrengthWcdma;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellInfoWcdma& rhs) const {
    return std::tie(cellIdentityWcdma, signalStrengthWcdma) != std::tie(rhs.cellIdentityWcdma, rhs.signalStrengthWcdma);
  }
  inline bool operator<(const CellInfoWcdma& rhs) const {
    return std::tie(cellIdentityWcdma, signalStrengthWcdma) < std::tie(rhs.cellIdentityWcdma, rhs.signalStrengthWcdma);
  }
  inline bool operator<=(const CellInfoWcdma& rhs) const {
    return std::tie(cellIdentityWcdma, signalStrengthWcdma) <= std::tie(rhs.cellIdentityWcdma, rhs.signalStrengthWcdma);
  }
  inline bool operator==(const CellInfoWcdma& rhs) const {
    return std::tie(cellIdentityWcdma, signalStrengthWcdma) == std::tie(rhs.cellIdentityWcdma, rhs.signalStrengthWcdma);
  }
  inline bool operator>(const CellInfoWcdma& rhs) const {
    return std::tie(cellIdentityWcdma, signalStrengthWcdma) > std::tie(rhs.cellIdentityWcdma, rhs.signalStrengthWcdma);
  }
  inline bool operator>=(const CellInfoWcdma& rhs) const {
    return std::tie(cellIdentityWcdma, signalStrengthWcdma) >= std::tie(rhs.cellIdentityWcdma, rhs.signalStrengthWcdma);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellInfoWcdma{";
    os << "cellIdentityWcdma: " << ::android::internal::ToString(cellIdentityWcdma);
    os << ", signalStrengthWcdma: " << ::android::internal::ToString(signalStrengthWcdma);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
