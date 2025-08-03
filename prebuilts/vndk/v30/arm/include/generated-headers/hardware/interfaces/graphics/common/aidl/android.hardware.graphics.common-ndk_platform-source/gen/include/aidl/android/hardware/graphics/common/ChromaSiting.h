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
namespace graphics {
namespace common {
enum class ChromaSiting : int64_t {
  NONE = 0L,
  UNKNOWN = 1L,
  SITED_INTERSTITIAL = 2L,
  COSITED_HORIZONTAL = 3L,
};

static inline std::string toString(ChromaSiting val) {
  switch(val) {
  case ChromaSiting::NONE:
    return "NONE";
  case ChromaSiting::UNKNOWN:
    return "UNKNOWN";
  case ChromaSiting::SITED_INTERSTITIAL:
    return "SITED_INTERSTITIAL";
  case ChromaSiting::COSITED_HORIZONTAL:
    return "COSITED_HORIZONTAL";
  default:
    return std::to_string(static_cast<int64_t>(val));
  }
}
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::common::ChromaSiting, 4> enum_values<aidl::android::hardware::graphics::common::ChromaSiting> = {
  aidl::android::hardware::graphics::common::ChromaSiting::NONE,
  aidl::android::hardware::graphics::common::ChromaSiting::UNKNOWN,
  aidl::android::hardware::graphics::common::ChromaSiting::SITED_INTERSTITIAL,
  aidl::android::hardware::graphics::common::ChromaSiting::COSITED_HORIZONTAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
