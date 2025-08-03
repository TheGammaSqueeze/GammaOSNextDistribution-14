#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CellIdentityTdscdma.h>
#include <aidl/android/hardware/radio/network/TdscdmaSignalStrength.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellInfoTdscdma {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::CellIdentityTdscdma cellIdentityTdscdma;
  ::aidl::android::hardware::radio::network::TdscdmaSignalStrength signalStrengthTdscdma;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellInfoTdscdma& rhs) const {
    return std::tie(cellIdentityTdscdma, signalStrengthTdscdma) != std::tie(rhs.cellIdentityTdscdma, rhs.signalStrengthTdscdma);
  }
  inline bool operator<(const CellInfoTdscdma& rhs) const {
    return std::tie(cellIdentityTdscdma, signalStrengthTdscdma) < std::tie(rhs.cellIdentityTdscdma, rhs.signalStrengthTdscdma);
  }
  inline bool operator<=(const CellInfoTdscdma& rhs) const {
    return std::tie(cellIdentityTdscdma, signalStrengthTdscdma) <= std::tie(rhs.cellIdentityTdscdma, rhs.signalStrengthTdscdma);
  }
  inline bool operator==(const CellInfoTdscdma& rhs) const {
    return std::tie(cellIdentityTdscdma, signalStrengthTdscdma) == std::tie(rhs.cellIdentityTdscdma, rhs.signalStrengthTdscdma);
  }
  inline bool operator>(const CellInfoTdscdma& rhs) const {
    return std::tie(cellIdentityTdscdma, signalStrengthTdscdma) > std::tie(rhs.cellIdentityTdscdma, rhs.signalStrengthTdscdma);
  }
  inline bool operator>=(const CellInfoTdscdma& rhs) const {
    return std::tie(cellIdentityTdscdma, signalStrengthTdscdma) >= std::tie(rhs.cellIdentityTdscdma, rhs.signalStrengthTdscdma);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellInfoTdscdma{";
    os << "cellIdentityTdscdma: " << ::android::internal::ToString(cellIdentityTdscdma);
    os << ", signalStrengthTdscdma: " << ::android::internal::ToString(signalStrengthTdscdma);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
