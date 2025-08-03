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
namespace vibrator {
enum class Braking : int32_t {
  NONE = 0,
  CLAB = 1,
};

[[nodiscard]] static inline std::string toString(Braking val) {
  switch(val) {
  case Braking::NONE:
    return "NONE";
  case Braking::CLAB:
    return "CLAB";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::vibrator::Braking, 2> enum_values<aidl::android::hardware::vibrator::Braking> = {
  aidl::android::hardware::vibrator::Braking::NONE,
  aidl::android::hardware::vibrator::Braking::CLAB,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
