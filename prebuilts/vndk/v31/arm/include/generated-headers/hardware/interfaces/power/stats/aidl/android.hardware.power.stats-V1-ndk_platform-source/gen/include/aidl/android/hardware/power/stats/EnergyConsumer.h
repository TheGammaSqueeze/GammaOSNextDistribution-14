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
#include <aidl/android/hardware/power/stats/EnergyConsumerType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class EnergyConsumer {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int32_t ordinal = 0;
  ::aidl::android::hardware::power::stats::EnergyConsumerType type = ::aidl::android::hardware::power::stats::EnergyConsumerType::OTHER;
  std::string name;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EnergyConsumer& rhs) const {
    return std::tie(id, ordinal, type, name) != std::tie(rhs.id, rhs.ordinal, rhs.type, rhs.name);
  }
  inline bool operator<(const EnergyConsumer& rhs) const {
    return std::tie(id, ordinal, type, name) < std::tie(rhs.id, rhs.ordinal, rhs.type, rhs.name);
  }
  inline bool operator<=(const EnergyConsumer& rhs) const {
    return std::tie(id, ordinal, type, name) <= std::tie(rhs.id, rhs.ordinal, rhs.type, rhs.name);
  }
  inline bool operator==(const EnergyConsumer& rhs) const {
    return std::tie(id, ordinal, type, name) == std::tie(rhs.id, rhs.ordinal, rhs.type, rhs.name);
  }
  inline bool operator>(const EnergyConsumer& rhs) const {
    return std::tie(id, ordinal, type, name) > std::tie(rhs.id, rhs.ordinal, rhs.type, rhs.name);
  }
  inline bool operator>=(const EnergyConsumer& rhs) const {
    return std::tie(id, ordinal, type, name) >= std::tie(rhs.id, rhs.ordinal, rhs.type, rhs.name);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EnergyConsumer{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", ordinal: " << ::android::internal::ToString(ordinal);
    os << ", type: " << ::android::internal::ToString(type);
    os << ", name: " << ::android::internal::ToString(name);
    os << "}";
    return os.str();
  }
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
