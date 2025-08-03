#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/NgranBands.h>
#include <aidl/android/hardware/radio/network/OperatorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellIdentityNr {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string mcc;
  std::string mnc;
  int64_t nci = 0L;
  int32_t pci = 0;
  int32_t tac = 0;
  int32_t nrarfcn = 0;
  ::aidl::android::hardware::radio::network::OperatorInfo operatorNames;
  std::vector<std::string> additionalPlmns;
  std::vector<::aidl::android::hardware::radio::network::NgranBands> bands;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellIdentityNr& rhs) const {
    return std::tie(mcc, mnc, nci, pci, tac, nrarfcn, operatorNames, additionalPlmns, bands) != std::tie(rhs.mcc, rhs.mnc, rhs.nci, rhs.pci, rhs.tac, rhs.nrarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.bands);
  }
  inline bool operator<(const CellIdentityNr& rhs) const {
    return std::tie(mcc, mnc, nci, pci, tac, nrarfcn, operatorNames, additionalPlmns, bands) < std::tie(rhs.mcc, rhs.mnc, rhs.nci, rhs.pci, rhs.tac, rhs.nrarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.bands);
  }
  inline bool operator<=(const CellIdentityNr& rhs) const {
    return std::tie(mcc, mnc, nci, pci, tac, nrarfcn, operatorNames, additionalPlmns, bands) <= std::tie(rhs.mcc, rhs.mnc, rhs.nci, rhs.pci, rhs.tac, rhs.nrarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.bands);
  }
  inline bool operator==(const CellIdentityNr& rhs) const {
    return std::tie(mcc, mnc, nci, pci, tac, nrarfcn, operatorNames, additionalPlmns, bands) == std::tie(rhs.mcc, rhs.mnc, rhs.nci, rhs.pci, rhs.tac, rhs.nrarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.bands);
  }
  inline bool operator>(const CellIdentityNr& rhs) const {
    return std::tie(mcc, mnc, nci, pci, tac, nrarfcn, operatorNames, additionalPlmns, bands) > std::tie(rhs.mcc, rhs.mnc, rhs.nci, rhs.pci, rhs.tac, rhs.nrarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.bands);
  }
  inline bool operator>=(const CellIdentityNr& rhs) const {
    return std::tie(mcc, mnc, nci, pci, tac, nrarfcn, operatorNames, additionalPlmns, bands) >= std::tie(rhs.mcc, rhs.mnc, rhs.nci, rhs.pci, rhs.tac, rhs.nrarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.bands);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellIdentityNr{";
    os << "mcc: " << ::android::internal::ToString(mcc);
    os << ", mnc: " << ::android::internal::ToString(mnc);
    os << ", nci: " << ::android::internal::ToString(nci);
    os << ", pci: " << ::android::internal::ToString(pci);
    os << ", tac: " << ::android::internal::ToString(tac);
    os << ", nrarfcn: " << ::android::internal::ToString(nrarfcn);
    os << ", operatorNames: " << ::android::internal::ToString(operatorNames);
    os << ", additionalPlmns: " << ::android::internal::ToString(additionalPlmns);
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
