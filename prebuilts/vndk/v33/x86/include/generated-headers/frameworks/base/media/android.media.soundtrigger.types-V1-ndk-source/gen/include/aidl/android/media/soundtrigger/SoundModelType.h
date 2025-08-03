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
namespace soundtrigger {
enum class SoundModelType : int32_t {
  INVALID = -1,
  KEYPHRASE = 0,
  GENERIC = 1,
};

}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
[[nodiscard]] static inline std::string toString(SoundModelType val) {
  switch(val) {
  case SoundModelType::INVALID:
    return "INVALID";
  case SoundModelType::KEYPHRASE:
    return "KEYPHRASE";
  case SoundModelType::GENERIC:
    return "GENERIC";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::media::soundtrigger::SoundModelType, 3> enum_values<aidl::android::media::soundtrigger::SoundModelType> = {
  aidl::android::media::soundtrigger::SoundModelType::INVALID,
  aidl::android::media::soundtrigger::SoundModelType::KEYPHRASE,
  aidl::android::media::soundtrigger::SoundModelType::GENERIC,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
