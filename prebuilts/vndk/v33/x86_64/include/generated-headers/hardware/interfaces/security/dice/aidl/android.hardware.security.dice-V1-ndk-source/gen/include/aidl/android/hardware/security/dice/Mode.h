#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
enum class Mode : int32_t {
  NOT_INITIALIZED = 0,
  NORMAL = 1,
  DEBUG = 2,
  RECOVERY = 3,
};

}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
[[nodiscard]] static inline std::string toString(Mode val) {
  switch(val) {
  case Mode::NOT_INITIALIZED:
    return "NOT_INITIALIZED";
  case Mode::NORMAL:
    return "NORMAL";
  case Mode::DEBUG:
    return "DEBUG";
  case Mode::RECOVERY:
    return "RECOVERY";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::security::dice::Mode, 4> enum_values<aidl::android::hardware::security::dice::Mode> = {
  aidl::android::hardware::security::dice::Mode::NOT_INITIALIZED,
  aidl::android::hardware::security::dice::Mode::NORMAL,
  aidl::android::hardware::security::dice::Mode::DEBUG,
  aidl::android::hardware::security::dice::Mode::RECOVERY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
