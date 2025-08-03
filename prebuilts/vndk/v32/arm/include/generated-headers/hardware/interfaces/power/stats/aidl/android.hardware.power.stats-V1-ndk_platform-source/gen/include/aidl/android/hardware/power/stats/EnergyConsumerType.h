#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
enum class EnergyConsumerType : int8_t {
  OTHER = 0,
  BLUETOOTH = 1,
  CPU_CLUSTER = 2,
  DISPLAY = 3,
  GNSS = 4,
  MOBILE_RADIO = 5,
  WIFI = 6,
};

[[nodiscard]] static inline std::string toString(EnergyConsumerType val) {
  switch(val) {
  case EnergyConsumerType::OTHER:
    return "OTHER";
  case EnergyConsumerType::BLUETOOTH:
    return "BLUETOOTH";
  case EnergyConsumerType::CPU_CLUSTER:
    return "CPU_CLUSTER";
  case EnergyConsumerType::DISPLAY:
    return "DISPLAY";
  case EnergyConsumerType::GNSS:
    return "GNSS";
  case EnergyConsumerType::MOBILE_RADIO:
    return "MOBILE_RADIO";
  case EnergyConsumerType::WIFI:
    return "WIFI";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::power::stats::EnergyConsumerType, 7> enum_values<aidl::android::hardware::power::stats::EnergyConsumerType> = {
  aidl::android::hardware::power::stats::EnergyConsumerType::OTHER,
  aidl::android::hardware::power::stats::EnergyConsumerType::BLUETOOTH,
  aidl::android::hardware::power::stats::EnergyConsumerType::CPU_CLUSTER,
  aidl::android::hardware::power::stats::EnergyConsumerType::DISPLAY,
  aidl::android::hardware::power::stats::EnergyConsumerType::GNSS,
  aidl::android::hardware::power::stats::EnergyConsumerType::MOBILE_RADIO,
  aidl::android::hardware::power::stats::EnergyConsumerType::WIFI,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
