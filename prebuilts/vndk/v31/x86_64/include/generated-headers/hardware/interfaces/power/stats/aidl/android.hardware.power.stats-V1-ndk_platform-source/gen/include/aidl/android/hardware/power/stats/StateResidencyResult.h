#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/power/stats/StateResidency.h>
namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class StateResidencyResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  std::vector<::aidl::android::hardware::power::stats::StateResidency> stateResidencyData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StateResidencyResult& rhs) const {
    return std::tie(id, stateResidencyData) != std::tie(rhs.id, rhs.stateResidencyData);
  }
  inline bool operator<(const StateResidencyResult& rhs) const {
    return std::tie(id, stateResidencyData) < std::tie(rhs.id, rhs.stateResidencyData);
  }
  inline bool operator<=(const StateResidencyResult& rhs) const {
    return std::tie(id, stateResidencyData) <= std::tie(rhs.id, rhs.stateResidencyData);
  }
  inline bool operator==(const StateResidencyResult& rhs) const {
    return std::tie(id, stateResidencyData) == std::tie(rhs.id, rhs.stateResidencyData);
  }
  inline bool operator>(const StateResidencyResult& rhs) const {
    return std::tie(id, stateResidencyData) > std::tie(rhs.id, rhs.stateResidencyData);
  }
  inline bool operator>=(const StateResidencyResult& rhs) const {
    return std::tie(id, stateResidencyData) >= std::tie(rhs.id, rhs.stateResidencyData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StateResidencyResult{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", stateResidencyData: " << ::android::internal::ToString(stateResidencyData);
    os << "}";
    return os.str();
  }
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
