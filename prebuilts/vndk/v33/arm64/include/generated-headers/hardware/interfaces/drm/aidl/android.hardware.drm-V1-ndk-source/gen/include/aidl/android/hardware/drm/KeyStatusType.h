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
enum class KeyStatusType : int32_t {
  USABLE = 0,
  EXPIRED = 1,
  OUTPUT_NOT_ALLOWED = 2,
  STATUS_PENDING = 3,
  INTERNAL_ERROR = 4,
  USABLE_IN_FUTURE = 5,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(KeyStatusType val) {
  switch(val) {
  case KeyStatusType::USABLE:
    return "USABLE";
  case KeyStatusType::EXPIRED:
    return "EXPIRED";
  case KeyStatusType::OUTPUT_NOT_ALLOWED:
    return "OUTPUT_NOT_ALLOWED";
  case KeyStatusType::STATUS_PENDING:
    return "STATUS_PENDING";
  case KeyStatusType::INTERNAL_ERROR:
    return "INTERNAL_ERROR";
  case KeyStatusType::USABLE_IN_FUTURE:
    return "USABLE_IN_FUTURE";
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
constexpr inline std::array<aidl::android::hardware::drm::KeyStatusType, 6> enum_values<aidl::android::hardware::drm::KeyStatusType> = {
  aidl::android::hardware::drm::KeyStatusType::USABLE,
  aidl::android::hardware::drm::KeyStatusType::EXPIRED,
  aidl::android::hardware::drm::KeyStatusType::OUTPUT_NOT_ALLOWED,
  aidl::android::hardware::drm::KeyStatusType::STATUS_PENDING,
  aidl::android::hardware::drm::KeyStatusType::INTERNAL_ERROR,
  aidl::android::hardware::drm::KeyStatusType::USABLE_IN_FUTURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
