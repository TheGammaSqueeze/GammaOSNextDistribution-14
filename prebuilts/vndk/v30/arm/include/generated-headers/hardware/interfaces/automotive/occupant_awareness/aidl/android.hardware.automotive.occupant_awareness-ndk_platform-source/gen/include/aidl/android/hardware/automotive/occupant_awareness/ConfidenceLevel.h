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
namespace automotive {
namespace occupant_awareness {
enum class ConfidenceLevel : int8_t {
  NONE = 0,
  LOW = 1,
  HIGH = 2,
  MAX = 3,
};

static inline std::string toString(ConfidenceLevel val) {
  switch(val) {
  case ConfidenceLevel::NONE:
    return "NONE";
  case ConfidenceLevel::LOW:
    return "LOW";
  case ConfidenceLevel::HIGH:
    return "HIGH";
  case ConfidenceLevel::MAX:
    return "MAX";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel, 4> enum_values<aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel> = {
  aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel::NONE,
  aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel::LOW,
  aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel::HIGH,
  aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel::MAX,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
