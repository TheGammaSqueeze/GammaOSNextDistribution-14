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
enum class KeyRequestType : int32_t {
  INITIAL = 0,
  RENEWAL = 1,
  RELEASE = 2,
  UNKNOWN = 3,
  NONE = 4,
  UPDATE = 5,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(KeyRequestType val) {
  switch(val) {
  case KeyRequestType::INITIAL:
    return "INITIAL";
  case KeyRequestType::RENEWAL:
    return "RENEWAL";
  case KeyRequestType::RELEASE:
    return "RELEASE";
  case KeyRequestType::UNKNOWN:
    return "UNKNOWN";
  case KeyRequestType::NONE:
    return "NONE";
  case KeyRequestType::UPDATE:
    return "UPDATE";
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
constexpr inline std::array<aidl::android::hardware::drm::KeyRequestType, 6> enum_values<aidl::android::hardware::drm::KeyRequestType> = {
  aidl::android::hardware::drm::KeyRequestType::INITIAL,
  aidl::android::hardware::drm::KeyRequestType::RENEWAL,
  aidl::android::hardware::drm::KeyRequestType::RELEASE,
  aidl::android::hardware::drm::KeyRequestType::UNKNOWN,
  aidl::android::hardware::drm::KeyRequestType::NONE,
  aidl::android::hardware::drm::KeyRequestType::UPDATE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
