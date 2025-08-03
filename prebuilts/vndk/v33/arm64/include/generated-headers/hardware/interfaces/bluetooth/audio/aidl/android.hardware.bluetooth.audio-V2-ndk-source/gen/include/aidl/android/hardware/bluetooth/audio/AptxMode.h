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
enum class AptxMode : int32_t {
  UNKNOWN = 0,
  HIGH_QUALITY = 4096,
  LOW_LATENCY = 8192,
  ULTRA_LOW_LATENCY = 16384,
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
[[nodiscard]] static inline std::string toString(AptxMode val) {
  switch(val) {
  case AptxMode::UNKNOWN:
    return "UNKNOWN";
  case AptxMode::HIGH_QUALITY:
    return "HIGH_QUALITY";
  case AptxMode::LOW_LATENCY:
    return "LOW_LATENCY";
  case AptxMode::ULTRA_LOW_LATENCY:
    return "ULTRA_LOW_LATENCY";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::AptxMode, 4> enum_values<aidl::android::hardware::bluetooth::audio::AptxMode> = {
  aidl::android::hardware::bluetooth::audio::AptxMode::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::AptxMode::HIGH_QUALITY,
  aidl::android::hardware::bluetooth::audio::AptxMode::LOW_LATENCY,
  aidl::android::hardware::bluetooth::audio::AptxMode::ULTRA_LOW_LATENCY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
