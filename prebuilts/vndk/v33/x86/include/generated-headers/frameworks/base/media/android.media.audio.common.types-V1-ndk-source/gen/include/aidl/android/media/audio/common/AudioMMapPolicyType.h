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
enum class AudioMMapPolicyType : int32_t {
  DEFAULT = 1,
  EXCLUSIVE = 2,
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
[[nodiscard]] static inline std::string toString(AudioMMapPolicyType val) {
  switch(val) {
  case AudioMMapPolicyType::DEFAULT:
    return "DEFAULT";
  case AudioMMapPolicyType::EXCLUSIVE:
    return "EXCLUSIVE";
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
constexpr inline std::array<aidl::android::media::audio::common::AudioMMapPolicyType, 2> enum_values<aidl::android::media::audio::common::AudioMMapPolicyType> = {
  aidl::android::media::audio::common::AudioMMapPolicyType::DEFAULT,
  aidl::android::media::audio::common::AudioMMapPolicyType::EXCLUSIVE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
