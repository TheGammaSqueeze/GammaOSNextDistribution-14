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
enum class OfflineLicenseState : int32_t {
  UNKNOWN = 0,
  USABLE = 1,
  INACTIVE = 2,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(OfflineLicenseState val) {
  switch(val) {
  case OfflineLicenseState::UNKNOWN:
    return "UNKNOWN";
  case OfflineLicenseState::USABLE:
    return "USABLE";
  case OfflineLicenseState::INACTIVE:
    return "INACTIVE";
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
constexpr inline std::array<aidl::android::hardware::drm::OfflineLicenseState, 3> enum_values<aidl::android::hardware::drm::OfflineLicenseState> = {
  aidl::android::hardware::drm::OfflineLicenseState::UNKNOWN,
  aidl::android::hardware::drm::OfflineLicenseState::USABLE,
  aidl::android::hardware::drm::OfflineLicenseState::INACTIVE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
