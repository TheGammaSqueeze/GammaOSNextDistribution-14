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
namespace media {
namespace audio {
namespace common {
enum class AudioGainMode : int8_t {
  JOINT = 0,
  CHANNELS = 1,
  RAMP = 2,
};

}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
[[nodiscard]] static inline std::string toString(AudioGainMode val) {
  switch(val) {
  case AudioGainMode::JOINT:
    return "JOINT";
  case AudioGainMode::CHANNELS:
    return "CHANNELS";
  case AudioGainMode::RAMP:
    return "RAMP";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::media::audio::common::AudioGainMode, 3> enum_values<aidl::android::media::audio::common::AudioGainMode> = {
  aidl::android::media::audio::common::AudioGainMode::JOINT,
  aidl::android::media::audio::common::AudioGainMode::CHANNELS,
  aidl::android::media::audio::common::AudioGainMode::RAMP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
