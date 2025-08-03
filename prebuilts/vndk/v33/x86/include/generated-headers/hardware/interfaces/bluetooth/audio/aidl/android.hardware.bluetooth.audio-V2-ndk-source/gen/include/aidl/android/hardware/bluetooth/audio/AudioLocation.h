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
enum class AudioLocation : int32_t {
  UNKNOWN = 1,
  FRONT_LEFT = 2,
  FRONT_RIGHT = 4,
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
[[nodiscard]] static inline std::string toString(AudioLocation val) {
  switch(val) {
  case AudioLocation::UNKNOWN:
    return "UNKNOWN";
  case AudioLocation::FRONT_LEFT:
    return "FRONT_LEFT";
  case AudioLocation::FRONT_RIGHT:
    return "FRONT_RIGHT";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::AudioLocation, 3> enum_values<aidl::android::hardware::bluetooth::audio::AudioLocation> = {
  aidl::android::hardware::bluetooth::audio::AudioLocation::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::AudioLocation::FRONT_LEFT,
  aidl::android::hardware::bluetooth::audio::AudioLocation::FRONT_RIGHT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
