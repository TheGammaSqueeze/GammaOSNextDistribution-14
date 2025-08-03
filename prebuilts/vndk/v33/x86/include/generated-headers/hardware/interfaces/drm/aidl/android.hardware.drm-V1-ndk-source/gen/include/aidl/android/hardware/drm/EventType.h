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
enum class EventType : int32_t {
  PROVISION_REQUIRED = 0,
  KEY_NEEDED = 1,
  KEY_EXPIRED = 2,
  VENDOR_DEFINED = 3,
  SESSION_RECLAIMED = 4,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(EventType val) {
  switch(val) {
  case EventType::PROVISION_REQUIRED:
    return "PROVISION_REQUIRED";
  case EventType::KEY_NEEDED:
    return "KEY_NEEDED";
  case EventType::KEY_EXPIRED:
    return "KEY_EXPIRED";
  case EventType::VENDOR_DEFINED:
    return "VENDOR_DEFINED";
  case EventType::SESSION_RECLAIMED:
    return "SESSION_RECLAIMED";
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
constexpr inline std::array<aidl::android::hardware::drm::EventType, 5> enum_values<aidl::android::hardware::drm::EventType> = {
  aidl::android::hardware::drm::EventType::PROVISION_REQUIRED,
  aidl::android::hardware::drm::EventType::KEY_NEEDED,
  aidl::android::hardware::drm::EventType::KEY_EXPIRED,
  aidl::android::hardware::drm::EventType::VENDOR_DEFINED,
  aidl::android::hardware::drm::EventType::SESSION_RECLAIMED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
