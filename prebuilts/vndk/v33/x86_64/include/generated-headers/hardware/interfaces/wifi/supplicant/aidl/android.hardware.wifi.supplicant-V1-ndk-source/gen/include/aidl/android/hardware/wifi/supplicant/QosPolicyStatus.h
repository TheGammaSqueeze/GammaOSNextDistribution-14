#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/QosPolicyStatusCode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class QosPolicyStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t policyId = 0;
  ::aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode status = ::aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const QosPolicyStatus& rhs) const {
    return std::tie(policyId, status) != std::tie(rhs.policyId, rhs.status);
  }
  inline bool operator<(const QosPolicyStatus& rhs) const {
    return std::tie(policyId, status) < std::tie(rhs.policyId, rhs.status);
  }
  inline bool operator<=(const QosPolicyStatus& rhs) const {
    return std::tie(policyId, status) <= std::tie(rhs.policyId, rhs.status);
  }
  inline bool operator==(const QosPolicyStatus& rhs) const {
    return std::tie(policyId, status) == std::tie(rhs.policyId, rhs.status);
  }
  inline bool operator>(const QosPolicyStatus& rhs) const {
    return std::tie(policyId, status) > std::tie(rhs.policyId, rhs.status);
  }
  inline bool operator>=(const QosPolicyStatus& rhs) const {
    return std::tie(policyId, status) >= std::tie(rhs.policyId, rhs.status);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "QosPolicyStatus{";
    os << "policyId: " << ::android::internal::ToString(policyId);
    os << ", status: " << ::android::internal::ToString(status);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
