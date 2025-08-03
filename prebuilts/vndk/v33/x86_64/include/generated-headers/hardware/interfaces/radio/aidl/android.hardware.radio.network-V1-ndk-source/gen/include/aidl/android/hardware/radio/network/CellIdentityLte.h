#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/ClosedSubscriberGroupInfo.h>
#include <aidl/android/hardware/radio/network/EutranBands.h>
#include <aidl/android/hardware/radio/network/OperatorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellIdentityLte {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string mcc;
  std::string mnc;
  int32_t ci = 0;
  int32_t pci = 0;
  int32_t tac = 0;
  int32_t earfcn = 0;
  ::aidl::android::hardware::radio::network::OperatorInfo operatorNames;
  int32_t bandwidth = 0;
  std::vector<std::string> additionalPlmns;
  std::optional<::aidl::android::hardware::radio::network::ClosedSubscriberGroupInfo> csgInfo;
  std::vector<::aidl::android::hardware::radio::network::EutranBands> bands;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellIdentityLte& rhs) const {
    return std::tie(mcc, mnc, ci, pci, tac, earfcn, operatorNames, bandwidth, additionalPlmns, csgInfo, bands) != std::tie(rhs.mcc, rhs.mnc, rhs.ci, rhs.pci, rhs.tac, rhs.earfcn, rhs.operatorNames, rhs.bandwidth, rhs.additionalPlmns, rhs.csgInfo, rhs.bands);
  }
  inline bool operator<(const CellIdentityLte& rhs) const {
    return std::tie(mcc, mnc, ci, pci, tac, earfcn, operatorNames, bandwidth, additionalPlmns, csgInfo, bands) < std::tie(rhs.mcc, rhs.mnc, rhs.ci, rhs.pci, rhs.tac, rhs.earfcn, rhs.operatorNames, rhs.bandwidth, rhs.additionalPlmns, rhs.csgInfo, rhs.bands);
  }
  inline bool operator<=(const CellIdentityLte& rhs) const {
    return std::tie(mcc, mnc, ci, pci, tac, earfcn, operatorNames, bandwidth, additionalPlmns, csgInfo, bands) <= std::tie(rhs.mcc, rhs.mnc, rhs.ci, rhs.pci, rhs.tac, rhs.earfcn, rhs.operatorNames, rhs.bandwidth, rhs.additionalPlmns, rhs.csgInfo, rhs.bands);
  }
  inline bool operator==(const CellIdentityLte& rhs) const {
    return std::tie(mcc, mnc, ci, pci, tac, earfcn, operatorNames, bandwidth, additionalPlmns, csgInfo, bands) == std::tie(rhs.mcc, rhs.mnc, rhs.ci, rhs.pci, rhs.tac, rhs.earfcn, rhs.operatorNames, rhs.bandwidth, rhs.additionalPlmns, rhs.csgInfo, rhs.bands);
  }
  inline bool operator>(const CellIdentityLte& rhs) const {
    return std::tie(mcc, mnc, ci, pci, tac, earfcn, operatorNames, bandwidth, additionalPlmns, csgInfo, bands) > std::tie(rhs.mcc, rhs.mnc, rhs.ci, rhs.pci, rhs.tac, rhs.earfcn, rhs.operatorNames, rhs.bandwidth, rhs.additionalPlmns, rhs.csgInfo, rhs.bands);
  }
  inline bool operator>=(const CellIdentityLte& rhs) const {
    return std::tie(mcc, mnc, ci, pci, tac, earfcn, operatorNames, bandwidth, additionalPlmns, csgInfo, bands) >= std::tie(rhs.mcc, rhs.mnc, rhs.ci, rhs.pci, rhs.tac, rhs.earfcn, rhs.operatorNames, rhs.bandwidth, rhs.additionalPlmns, rhs.csgInfo, rhs.bands);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellIdentityLte{";
    os << "mcc: " << ::android::internal::ToString(mcc);
    os << ", mnc: " << ::android::internal::ToString(mnc);
    os << ", ci: " << ::android::internal::ToString(ci);
    os << ", pci: " << ::android::internal::ToString(pci);
    os << ", tac: " << ::android::internal::ToString(tac);
    os << ", earfcn: " << ::android::internal::ToString(earfcn);
    os << ", operatorNames: " << ::android::internal::ToString(operatorNames);
    os << ", bandwidth: " << ::android::internal::ToString(bandwidth);
    os << ", additionalPlmns: " << ::android::internal::ToString(additionalPlmns);
    os << ", csgInfo: " << ::android::internal::ToString(csgInfo);
    os << ", bands: " << ::android::internal::ToString(bands);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
