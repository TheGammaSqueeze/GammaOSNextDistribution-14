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
enum class AudioStandard : int32_t {
  NONE = 0,
  EDID = 1,
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
[[nodiscard]] static inline std::string toString(AudioStandard val) {
  switch(val) {
  case AudioStandard::NONE:
    return "NONE";
  case AudioStandard::EDID:
    return "EDID";
  default:
    return std::to_string(static_cast<int32_t>(val));
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
constexpr inline std::array<aidl::android::media::audio::common::AudioStandard, 2> enum_values<aidl::android::media::audio::common::AudioStandard> = {
  aidl::android::media::audio::common::AudioStandard::NONE,
  aidl::android::media::audio::common::AudioStandard::EDID,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
