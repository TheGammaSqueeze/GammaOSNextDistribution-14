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
namespace drm {
enum class SecurityLevel : int32_t {
  UNKNOWN = 0,
  SW_SECURE_CRYPTO = 1,
  SW_SECURE_DECODE = 2,
  HW_SECURE_CRYPTO = 3,
  HW_SECURE_DECODE = 4,
  HW_SECURE_ALL = 5,
  DEFAULT = 6,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(SecurityLevel val) {
  switch(val) {
  case SecurityLevel::UNKNOWN:
    return "UNKNOWN";
  case SecurityLevel::SW_SECURE_CRYPTO:
    return "SW_SECURE_CRYPTO";
  case SecurityLevel::SW_SECURE_DECODE:
    return "SW_SECURE_DECODE";
  case SecurityLevel::HW_SECURE_CRYPTO:
    return "HW_SECURE_CRYPTO";
  case SecurityLevel::HW_SECURE_DECODE:
    return "HW_SECURE_DECODE";
  case SecurityLevel::HW_SECURE_ALL:
    return "HW_SECURE_ALL";
  case SecurityLevel::DEFAULT:
    return "DEFAULT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::drm::SecurityLevel, 7> enum_values<aidl::android::hardware::drm::SecurityLevel> = {
  aidl::android::hardware::drm::SecurityLevel::UNKNOWN,
  aidl::android::hardware::drm::SecurityLevel::SW_SECURE_CRYPTO,
  aidl::android::hardware::drm::SecurityLevel::SW_SECURE_DECODE,
  aidl::android::hardware::drm::SecurityLevel::HW_SECURE_CRYPTO,
  aidl::android::hardware::drm::SecurityLevel::HW_SECURE_DECODE,
  aidl::android::hardware::drm::SecurityLevel::HW_SECURE_ALL,
  aidl::android::hardware::drm::SecurityLevel::DEFAULT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
