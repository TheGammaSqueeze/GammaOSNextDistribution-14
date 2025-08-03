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
enum class SyncMaxLatency : int32_t {
  ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL = 0,
  ANDROID_SYNC_MAX_LATENCY_UNKNOWN = -1,
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
[[nodiscard]] static inline std::string toString(SyncMaxLatency val) {
  switch(val) {
  case SyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL:
    return "ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL";
  case SyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN:
    return "ANDROID_SYNC_MAX_LATENCY_UNKNOWN";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SyncMaxLatency, 2> enum_values<aidl::android::hardware::camera::metadata::SyncMaxLatency> = {
  aidl::android::hardware::camera::metadata::SyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL,
  aidl::android::hardware::camera::metadata::SyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
