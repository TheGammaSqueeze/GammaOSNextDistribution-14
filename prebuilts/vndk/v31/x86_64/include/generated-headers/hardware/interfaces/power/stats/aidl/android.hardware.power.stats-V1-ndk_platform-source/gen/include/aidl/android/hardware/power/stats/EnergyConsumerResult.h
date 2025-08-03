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
#include <aidl/android/hardware/power/stats/EnergyConsumerAttribution.h>
namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class EnergyConsumerResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int64_t timestampMs = 0L;
  int64_t energyUWs = 0L;
  std::vector<::aidl::android::hardware::power::stats::EnergyConsumerAttribution> attribution;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EnergyConsumerResult& rhs) const {
    return std::tie(id, timestampMs, energyUWs, attribution) != std::tie(rhs.id, rhs.timestampMs, rhs.energyUWs, rhs.attribution);
  }
  inline bool operator<(const EnergyConsumerResult& rhs) const {
    return std::tie(id, timestampMs, energyUWs, attribution) < std::tie(rhs.id, rhs.timestampMs, rhs.energyUWs, rhs.attribution);
  }
  inline bool operator<=(const EnergyConsumerResult& rhs) const {
    return std::tie(id, timestampMs, energyUWs, attribution) <= std::tie(rhs.id, rhs.timestampMs, rhs.energyUWs, rhs.attribution);
  }
  inline bool operator==(const EnergyConsumerResult& rhs) const {
    return std::tie(id, timestampMs, energyUWs, attribution) == std::tie(rhs.id, rhs.timestampMs, rhs.energyUWs, rhs.attribution);
  }
  inline bool operator>(const EnergyConsumerResult& rhs) const {
    return std::tie(id, timestampMs, energyUWs, attribution) > std::tie(rhs.id, rhs.timestampMs, rhs.energyUWs, rhs.attribution);
  }
  inline bool operator>=(const EnergyConsumerResult& rhs) const {
    return std::tie(id, timestampMs, energyUWs, attribution) >= std::tie(rhs.id, rhs.timestampMs, rhs.energyUWs, rhs.attribution);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EnergyConsumerResult{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", timestampMs: " << ::android::internal::ToString(timestampMs);
    os << ", energyUWs: " << ::android::internal::ToString(energyUWs);
    os << ", attribution: " << ::android::internal::ToString(attribution);
    os << "}";
    return os.str();
  }
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
