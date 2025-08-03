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
namespace bluetooth {
namespace audio {
enum class LatencyMode : int32_t {
  UNKNOWN = 0,
  LOW_LATENCY = 1,
  FREE = 2,
};

}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
[[nodiscard]] static inline std::string toString(LatencyMode val) {
  switch(val) {
  case LatencyMode::UNKNOWN:
    return "UNKNOWN";
  case LatencyMode::LOW_LATENCY:
    return "LOW_LATENCY";
  case LatencyMode::FREE:
    return "FREE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::LatencyMode, 3> enum_values<aidl::android::hardware::bluetooth::audio::LatencyMode> = {
  aidl::android::hardware::bluetooth::audio::LatencyMode::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::LatencyMode::LOW_LATENCY,
  aidl::android::hardware::bluetooth::audio::LatencyMode::FREE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
