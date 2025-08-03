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
namespace radio {
namespace voice {
enum class ClipStatus : int32_t {
  CLIP_PROVISIONED = 0,
  CLIP_UNPROVISIONED = 1,
  UNKNOWN = 2,
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
[[nodiscard]] static inline std::string toString(ClipStatus val) {
  switch(val) {
  case ClipStatus::CLIP_PROVISIONED:
    return "CLIP_PROVISIONED";
  case ClipStatus::CLIP_UNPROVISIONED:
    return "CLIP_UNPROVISIONED";
  case ClipStatus::UNKNOWN:
    return "UNKNOWN";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::voice::ClipStatus, 3> enum_values<aidl::android::hardware::radio::voice::ClipStatus> = {
  aidl::android::hardware::radio::voice::ClipStatus::CLIP_PROVISIONED,
  aidl::android::hardware::radio::voice::ClipStatus::CLIP_UNPROVISIONED,
  aidl::android::hardware::radio::voice::ClipStatus::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
