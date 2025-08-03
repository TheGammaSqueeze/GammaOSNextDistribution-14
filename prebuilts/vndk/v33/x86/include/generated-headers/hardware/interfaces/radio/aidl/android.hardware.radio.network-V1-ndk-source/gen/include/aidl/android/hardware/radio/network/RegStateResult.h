#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/RadioTechnology.h>
#include <aidl/android/hardware/radio/network/AccessTechnologySpecificInfo.h>
#include <aidl/android/hardware/radio/network/CellIdentity.h>
#include <aidl/android/hardware/radio/network/RegState.h>
#include <aidl/android/hardware/radio/network/RegistrationFailCause.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class RegStateResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::RegState regState = ::aidl::android::hardware::radio::network::RegState(0);
  ::aidl::android::hardware::radio::RadioTechnology rat = ::aidl::android::hardware::radio::RadioTechnology(0);
  ::aidl::android::hardware::radio::network::RegistrationFailCause reasonForDenial = ::aidl::android::hardware::radio::network::RegistrationFailCause(0);
  ::aidl::android::hardware::radio::network::CellIdentity cellIdentity;
  std::string registeredPlmn;
  ::aidl::android::hardware::radio::network::AccessTechnologySpecificInfo accessTechnologySpecificInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RegStateResult& rhs) const {
    return std::tie(regState, rat, reasonForDenial, cellIdentity, registeredPlmn, accessTechnologySpecificInfo) != std::tie(rhs.regState, rhs.rat, rhs.reasonForDenial, rhs.cellIdentity, rhs.registeredPlmn, rhs.accessTechnologySpecificInfo);
  }
  inline bool operator<(const RegStateResult& rhs) const {
    return std::tie(regState, rat, reasonForDenial, cellIdentity, registeredPlmn, accessTechnologySpecificInfo) < std::tie(rhs.regState, rhs.rat, rhs.reasonForDenial, rhs.cellIdentity, rhs.registeredPlmn, rhs.accessTechnologySpecificInfo);
  }
  inline bool operator<=(const RegStateResult& rhs) const {
    return std::tie(regState, rat, reasonForDenial, cellIdentity, registeredPlmn, accessTechnologySpecificInfo) <= std::tie(rhs.regState, rhs.rat, rhs.reasonForDenial, rhs.cellIdentity, rhs.registeredPlmn, rhs.accessTechnologySpecificInfo);
  }
  inline bool operator==(const RegStateResult& rhs) const {
    return std::tie(regState, rat, reasonForDenial, cellIdentity, registeredPlmn, accessTechnologySpecificInfo) == std::tie(rhs.regState, rhs.rat, rhs.reasonForDenial, rhs.cellIdentity, rhs.registeredPlmn, rhs.accessTechnologySpecificInfo);
  }
  inline bool operator>(const RegStateResult& rhs) const {
    return std::tie(regState, rat, reasonForDenial, cellIdentity, registeredPlmn, accessTechnologySpecificInfo) > std::tie(rhs.regState, rhs.rat, rhs.reasonForDenial, rhs.cellIdentity, rhs.registeredPlmn, rhs.accessTechnologySpecificInfo);
  }
  inline bool operator>=(const RegStateResult& rhs) const {
    return std::tie(regState, rat, reasonForDenial, cellIdentity, registeredPlmn, accessTechnologySpecificInfo) >= std::tie(rhs.regState, rhs.rat, rhs.reasonForDenial, rhs.cellIdentity, rhs.registeredPlmn, rhs.accessTechnologySpecificInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RegStateResult{";
    os << "regState: " << ::android::internal::ToString(regState);
    os << ", rat: " << ::android::internal::ToString(rat);
    os << ", reasonForDenial: " << ::android::internal::ToString(reasonForDenial);
    os << ", cellIdentity: " << ::android::internal::ToString(cellIdentity);
    os << ", registeredPlmn: " << ::android::internal::ToString(registeredPlmn);
    os << ", accessTechnologySpecificInfo: " << ::android::internal::ToString(accessTechnologySpecificInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
