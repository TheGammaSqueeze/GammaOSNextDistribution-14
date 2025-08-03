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
enum class KeyType : int32_t {
  OFFLINE = 0,
  STREAMING = 1,
  RELEASE = 2,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(KeyType val) {
  switch(val) {
  case KeyType::OFFLINE:
    return "OFFLINE";
  case KeyType::STREAMING:
    return "STREAMING";
  case KeyType::RELEASE:
    return "RELEASE";
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
constexpr inline std::array<aidl::android::hardware::drm::KeyType, 3> enum_values<aidl::android::hardware::drm::KeyType> = {
  aidl::android::hardware::drm::KeyType::OFFLINE,
  aidl::android::hardware::drm::KeyType::STREAMING,
  aidl::android::hardware::drm::KeyType::RELEASE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
