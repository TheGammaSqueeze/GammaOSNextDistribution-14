#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CellIdentityNr.h>
#include <aidl/android/hardware/radio/network/NrSignalStrength.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellInfoNr {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::CellIdentityNr cellIdentityNr;
  ::aidl::android::hardware::radio::network::NrSignalStrength signalStrengthNr;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellInfoNr& rhs) const {
    return std::tie(cellIdentityNr, signalStrengthNr) != std::tie(rhs.cellIdentityNr, rhs.signalStrengthNr);
  }
  inline bool operator<(const CellInfoNr& rhs) const {
    return std::tie(cellIdentityNr, signalStrengthNr) < std::tie(rhs.cellIdentityNr, rhs.signalStrengthNr);
  }
  inline bool operator<=(const CellInfoNr& rhs) const {
    return std::tie(cellIdentityNr, signalStrengthNr) <= std::tie(rhs.cellIdentityNr, rhs.signalStrengthNr);
  }
  inline bool operator==(const CellInfoNr& rhs) const {
    return std::tie(cellIdentityNr, signalStrengthNr) == std::tie(rhs.cellIdentityNr, rhs.signalStrengthNr);
  }
  inline bool operator>(const CellInfoNr& rhs) const {
    return std::tie(cellIdentityNr, signalStrengthNr) > std::tie(rhs.cellIdentityNr, rhs.signalStrengthNr);
  }
  inline bool operator>=(const CellInfoNr& rhs) const {
    return std::tie(cellIdentityNr, signalStrengthNr) >= std::tie(rhs.cellIdentityNr, rhs.signalStrengthNr);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellInfoNr{";
    os << "cellIdentityNr: " << ::android::internal::ToString(cellIdentityNr);
    os << ", signalStrengthNr: " << ::android::internal::ToString(signalStrengthNr);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
