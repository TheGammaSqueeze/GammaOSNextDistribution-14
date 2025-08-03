#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CdmaSignalStrength.h>
#include <aidl/android/hardware/radio/network/CellIdentityCdma.h>
#include <aidl/android/hardware/radio/network/EvdoSignalStrength.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellInfoCdma {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::CellIdentityCdma cellIdentityCdma;
  ::aidl::android::hardware::radio::network::CdmaSignalStrength signalStrengthCdma;
  ::aidl::android::hardware::radio::network::EvdoSignalStrength signalStrengthEvdo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellInfoCdma& rhs) const {
    return std::tie(cellIdentityCdma, signalStrengthCdma, signalStrengthEvdo) != std::tie(rhs.cellIdentityCdma, rhs.signalStrengthCdma, rhs.signalStrengthEvdo);
  }
  inline bool operator<(const CellInfoCdma& rhs) const {
    return std::tie(cellIdentityCdma, signalStrengthCdma, signalStrengthEvdo) < std::tie(rhs.cellIdentityCdma, rhs.signalStrengthCdma, rhs.signalStrengthEvdo);
  }
  inline bool operator<=(const CellInfoCdma& rhs) const {
    return std::tie(cellIdentityCdma, signalStrengthCdma, signalStrengthEvdo) <= std::tie(rhs.cellIdentityCdma, rhs.signalStrengthCdma, rhs.signalStrengthEvdo);
  }
  inline bool operator==(const CellInfoCdma& rhs) const {
    return std::tie(cellIdentityCdma, signalStrengthCdma, signalStrengthEvdo) == std::tie(rhs.cellIdentityCdma, rhs.signalStrengthCdma, rhs.signalStrengthEvdo);
  }
  inline bool operator>(const CellInfoCdma& rhs) const {
    return std::tie(cellIdentityCdma, signalStrengthCdma, signalStrengthEvdo) > std::tie(rhs.cellIdentityCdma, rhs.signalStrengthCdma, rhs.signalStrengthEvdo);
  }
  inline bool operator>=(const CellInfoCdma& rhs) const {
    return std::tie(cellIdentityCdma, signalStrengthCdma, signalStrengthEvdo) >= std::tie(rhs.cellIdentityCdma, rhs.signalStrengthCdma, rhs.signalStrengthEvdo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellInfoCdma{";
    os << "cellIdentityCdma: " << ::android::internal::ToString(cellIdentityCdma);
    os << ", signalStrengthCdma: " << ::android::internal::ToString(signalStrengthCdma);
    os << ", signalStrengthEvdo: " << ::android::internal::ToString(signalStrengthEvdo);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
