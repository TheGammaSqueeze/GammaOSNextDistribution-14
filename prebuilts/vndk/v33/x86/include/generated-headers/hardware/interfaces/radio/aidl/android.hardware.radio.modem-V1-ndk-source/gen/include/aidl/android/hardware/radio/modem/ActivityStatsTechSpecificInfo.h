#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/AccessNetwork.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class ActivityStatsTechSpecificInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::AccessNetwork rat = ::aidl::android::hardware::radio::AccessNetwork(0);
  int32_t frequencyRange = 0;
  std::vector<int32_t> txmModetimeMs;
  int32_t rxModeTimeMs = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ActivityStatsTechSpecificInfo& rhs) const {
    return std::tie(rat, frequencyRange, txmModetimeMs, rxModeTimeMs) != std::tie(rhs.rat, rhs.frequencyRange, rhs.txmModetimeMs, rhs.rxModeTimeMs);
  }
  inline bool operator<(const ActivityStatsTechSpecificInfo& rhs) const {
    return std::tie(rat, frequencyRange, txmModetimeMs, rxModeTimeMs) < std::tie(rhs.rat, rhs.frequencyRange, rhs.txmModetimeMs, rhs.rxModeTimeMs);
  }
  inline bool operator<=(const ActivityStatsTechSpecificInfo& rhs) const {
    return std::tie(rat, frequencyRange, txmModetimeMs, rxModeTimeMs) <= std::tie(rhs.rat, rhs.frequencyRange, rhs.txmModetimeMs, rhs.rxModeTimeMs);
  }
  inline bool operator==(const ActivityStatsTechSpecificInfo& rhs) const {
    return std::tie(rat, frequencyRange, txmModetimeMs, rxModeTimeMs) == std::tie(rhs.rat, rhs.frequencyRange, rhs.txmModetimeMs, rhs.rxModeTimeMs);
  }
  inline bool operator>(const ActivityStatsTechSpecificInfo& rhs) const {
    return std::tie(rat, frequencyRange, txmModetimeMs, rxModeTimeMs) > std::tie(rhs.rat, rhs.frequencyRange, rhs.txmModetimeMs, rhs.rxModeTimeMs);
  }
  inline bool operator>=(const ActivityStatsTechSpecificInfo& rhs) const {
    return std::tie(rat, frequencyRange, txmModetimeMs, rxModeTimeMs) >= std::tie(rhs.rat, rhs.frequencyRange, rhs.txmModetimeMs, rhs.rxModeTimeMs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { FREQUENCY_RANGE_UNKNOWN = 0 };
  enum : int32_t { FREQUENCY_RANGE_LOW = 1 };
  enum : int32_t { FREQUENCY_RANGE_MID = 2 };
  enum : int32_t { FREQUENCY_RANGE_HIGH = 3 };
  enum : int32_t { FREQUENCY_RANGE_MMWAVE = 4 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "ActivityStatsTechSpecificInfo{";
    os << "rat: " << ::android::internal::ToString(rat);
    os << ", frequencyRange: " << ::android::internal::ToString(frequencyRange);
    os << ", txmModetimeMs: " << ::android::internal::ToString(txmModetimeMs);
    os << ", rxModeTimeMs: " << ::android::internal::ToString(rxModeTimeMs);
    os << "}";
    return os.str();
  }
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
