#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/QosPolicyClassifierParams.h>
#include <aidl/android/hardware/wifi/supplicant/QosPolicyRequestType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class QosPolicyData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t policyId = 0;
  ::aidl::android::hardware::wifi::supplicant::QosPolicyRequestType requestType = ::aidl::android::hardware::wifi::supplicant::QosPolicyRequestType(0);
  int8_t dscp = 0;
  ::aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParams classifierParams;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const QosPolicyData& rhs) const {
    return std::tie(policyId, requestType, dscp, classifierParams) != std::tie(rhs.policyId, rhs.requestType, rhs.dscp, rhs.classifierParams);
  }
  inline bool operator<(const QosPolicyData& rhs) const {
    return std::tie(policyId, requestType, dscp, classifierParams) < std::tie(rhs.policyId, rhs.requestType, rhs.dscp, rhs.classifierParams);
  }
  inline bool operator<=(const QosPolicyData& rhs) const {
    return std::tie(policyId, requestType, dscp, classifierParams) <= std::tie(rhs.policyId, rhs.requestType, rhs.dscp, rhs.classifierParams);
  }
  inline bool operator==(const QosPolicyData& rhs) const {
    return std::tie(policyId, requestType, dscp, classifierParams) == std::tie(rhs.policyId, rhs.requestType, rhs.dscp, rhs.classifierParams);
  }
  inline bool operator>(const QosPolicyData& rhs) const {
    return std::tie(policyId, requestType, dscp, classifierParams) > std::tie(rhs.policyId, rhs.requestType, rhs.dscp, rhs.classifierParams);
  }
  inline bool operator>=(const QosPolicyData& rhs) const {
    return std::tie(policyId, requestType, dscp, classifierParams) >= std::tie(rhs.policyId, rhs.requestType, rhs.dscp, rhs.classifierParams);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "QosPolicyData{";
    os << "policyId: " << ::android::internal::ToString(policyId);
    os << ", requestType: " << ::android::internal::ToString(requestType);
    os << ", dscp: " << ::android::internal::ToString(dscp);
    os << ", classifierParams: " << ::android::internal::ToString(classifierParams);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
