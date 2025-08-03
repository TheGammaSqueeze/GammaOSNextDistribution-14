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
#include <aidl/android/hardware/radio/network/OperatorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellIdentityWcdma {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string mcc;
  std::string mnc;
  int32_t lac = 0;
  int32_t cid = 0;
  int32_t psc = 0;
  int32_t uarfcn = 0;
  ::aidl::android::hardware::radio::network::OperatorInfo operatorNames;
  std::vector<std::string> additionalPlmns;
  std::optional<::aidl::android::hardware::radio::network::ClosedSubscriberGroupInfo> csgInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellIdentityWcdma& rhs) const {
    return std::tie(mcc, mnc, lac, cid, psc, uarfcn, operatorNames, additionalPlmns, csgInfo) != std::tie(rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.psc, rhs.uarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.csgInfo);
  }
  inline bool operator<(const CellIdentityWcdma& rhs) const {
    return std::tie(mcc, mnc, lac, cid, psc, uarfcn, operatorNames, additionalPlmns, csgInfo) < std::tie(rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.psc, rhs.uarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.csgInfo);
  }
  inline bool operator<=(const CellIdentityWcdma& rhs) const {
    return std::tie(mcc, mnc, lac, cid, psc, uarfcn, operatorNames, additionalPlmns, csgInfo) <= std::tie(rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.psc, rhs.uarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.csgInfo);
  }
  inline bool operator==(const CellIdentityWcdma& rhs) const {
    return std::tie(mcc, mnc, lac, cid, psc, uarfcn, operatorNames, additionalPlmns, csgInfo) == std::tie(rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.psc, rhs.uarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.csgInfo);
  }
  inline bool operator>(const CellIdentityWcdma& rhs) const {
    return std::tie(mcc, mnc, lac, cid, psc, uarfcn, operatorNames, additionalPlmns, csgInfo) > std::tie(rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.psc, rhs.uarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.csgInfo);
  }
  inline bool operator>=(const CellIdentityWcdma& rhs) const {
    return std::tie(mcc, mnc, lac, cid, psc, uarfcn, operatorNames, additionalPlmns, csgInfo) >= std::tie(rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.psc, rhs.uarfcn, rhs.operatorNames, rhs.additionalPlmns, rhs.csgInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellIdentityWcdma{";
    os << "mcc: " << ::android::internal::ToString(mcc);
    os << ", mnc: " << ::android::internal::ToString(mnc);
    os << ", lac: " << ::android::internal::ToString(lac);
    os << ", cid: " << ::android::internal::ToString(cid);
    os << ", psc: " << ::android::internal::ToString(psc);
    os << ", uarfcn: " << ::android::internal::ToString(uarfcn);
    os << ", operatorNames: " << ::android::internal::ToString(operatorNames);
    os << ", additionalPlmns: " << ::android::internal::ToString(additionalPlmns);
    os << ", csgInfo: " << ::android::internal::ToString(csgInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
