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
namespace camera {
namespace metadata {
enum class SyncFrameNumber : int32_t {
  ANDROID_SYNC_FRAME_NUMBER_CONVERGING = -1,
  ANDROID_SYNC_FRAME_NUMBER_UNKNOWN = -2,
};

}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
[[nodiscard]] static inline std::string toString(SyncFrameNumber val) {
  switch(val) {
  case SyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING:
    return "ANDROID_SYNC_FRAME_NUMBER_CONVERGING";
  case SyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN:
    return "ANDROID_SYNC_FRAME_NUMBER_UNKNOWN";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::metadata::SyncFrameNumber, 2> enum_values<aidl::android::hardware::camera::metadata::SyncFrameNumber> = {
  aidl::android::hardware::camera::metadata::SyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING,
  aidl::android::hardware::camera::metadata::SyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
