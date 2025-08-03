#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CellIdentityLte.h>
#include <aidl/android/hardware/radio/network/LteSignalStrength.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellInfoLte {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::CellIdentityLte cellIdentityLte;
  ::aidl::android::hardware::radio::network::LteSignalStrength signalStrengthLte;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellInfoLte& rhs) const {
    return std::tie(cellIdentityLte, signalStrengthLte) != std::tie(rhs.cellIdentityLte, rhs.signalStrengthLte);
  }
  inline bool operator<(const CellInfoLte& rhs) const {
    return std::tie(cellIdentityLte, signalStrengthLte) < std::tie(rhs.cellIdentityLte, rhs.signalStrengthLte);
  }
  inline bool operator<=(const CellInfoLte& rhs) const {
    return std::tie(cellIdentityLte, signalStrengthLte) <= std::tie(rhs.cellIdentityLte, rhs.signalStrengthLte);
  }
  inline bool operator==(const CellInfoLte& rhs) const {
    return std::tie(cellIdentityLte, signalStrengthLte) == std::tie(rhs.cellIdentityLte, rhs.signalStrengthLte);
  }
  inline bool operator>(const CellInfoLte& rhs) const {
    return std::tie(cellIdentityLte, signalStrengthLte) > std::tie(rhs.cellIdentityLte, rhs.signalStrengthLte);
  }
  inline bool operator>=(const CellInfoLte& rhs) const {
    return std::tie(cellIdentityLte, signalStrengthLte) >= std::tie(rhs.cellIdentityLte, rhs.signalStrengthLte);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellInfoLte{";
    os << "cellIdentityLte: " << ::android::internal::ToString(cellIdentityLte);
    os << ", signalStrengthLte: " << ::android::internal::ToString(signalStrengthLte);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
