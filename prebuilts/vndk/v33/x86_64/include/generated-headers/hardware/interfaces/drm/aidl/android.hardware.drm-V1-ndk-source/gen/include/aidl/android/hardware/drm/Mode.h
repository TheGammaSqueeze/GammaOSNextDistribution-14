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
enum class Mode : int32_t {
  UNENCRYPTED = 0,
  AES_CTR = 1,
  AES_CBC_CTS = 2,
  AES_CBC = 3,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(Mode val) {
  switch(val) {
  case Mode::UNENCRYPTED:
    return "UNENCRYPTED";
  case Mode::AES_CTR:
    return "AES_CTR";
  case Mode::AES_CBC_CTS:
    return "AES_CBC_CTS";
  case Mode::AES_CBC:
    return "AES_CBC";
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
constexpr inline std::array<aidl::android::hardware::drm::Mode, 4> enum_values<aidl::android::hardware::drm::Mode> = {
  aidl::android::hardware::drm::Mode::UNENCRYPTED,
  aidl::android::hardware::drm::Mode::AES_CTR,
  aidl::android::hardware::drm::Mode::AES_CBC_CTS,
  aidl::android::hardware::drm::Mode::AES_CBC,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
