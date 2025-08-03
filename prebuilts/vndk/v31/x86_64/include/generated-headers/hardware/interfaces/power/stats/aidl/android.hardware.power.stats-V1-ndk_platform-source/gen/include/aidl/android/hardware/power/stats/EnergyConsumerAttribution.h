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
namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class EnergyConsumerAttribution {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t uid = 0;
  int64_t energyUWs = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EnergyConsumerAttribution& rhs) const {
    return std::tie(uid, energyUWs) != std::tie(rhs.uid, rhs.energyUWs);
  }
  inline bool operator<(const EnergyConsumerAttribution& rhs) const {
    return std::tie(uid, energyUWs) < std::tie(rhs.uid, rhs.energyUWs);
  }
  inline bool operator<=(const EnergyConsumerAttribution& rhs) const {
    return std::tie(uid, energyUWs) <= std::tie(rhs.uid, rhs.energyUWs);
  }
  inline bool operator==(const EnergyConsumerAttribution& rhs) const {
    return std::tie(uid, energyUWs) == std::tie(rhs.uid, rhs.energyUWs);
  }
  inline bool operator>(const EnergyConsumerAttribution& rhs) const {
    return std::tie(uid, energyUWs) > std::tie(rhs.uid, rhs.energyUWs);
  }
  inline bool operator>=(const EnergyConsumerAttribution& rhs) const {
    return std::tie(uid, energyUWs) >= std::tie(rhs.uid, rhs.energyUWs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EnergyConsumerAttribution{";
    os << "uid: " << ::android::internal::ToString(uid);
    os << ", energyUWs: " << ::android::internal::ToString(energyUWs);
    os << "}";
    return os.str();
  }
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
