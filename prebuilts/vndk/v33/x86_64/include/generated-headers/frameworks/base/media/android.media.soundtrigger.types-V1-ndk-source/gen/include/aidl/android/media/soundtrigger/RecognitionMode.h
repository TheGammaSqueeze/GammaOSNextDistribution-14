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
enum class RecognitionMode : int32_t {
  VOICE_TRIGGER = 1,
  USER_IDENTIFICATION = 2,
  USER_AUTHENTICATION = 4,
  GENERIC_TRIGGER = 8,
};

}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
[[nodiscard]] static inline std::string toString(RecognitionMode val) {
  switch(val) {
  case RecognitionMode::VOICE_TRIGGER:
    return "VOICE_TRIGGER";
  case RecognitionMode::USER_IDENTIFICATION:
    return "USER_IDENTIFICATION";
  case RecognitionMode::USER_AUTHENTICATION:
    return "USER_AUTHENTICATION";
  case RecognitionMode::GENERIC_TRIGGER:
    return "GENERIC_TRIGGER";
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
constexpr inline std::array<aidl::android::media::soundtrigger::RecognitionMode, 4> enum_values<aidl::android::media::soundtrigger::RecognitionMode> = {
  aidl::android::media::soundtrigger::RecognitionMode::VOICE_TRIGGER,
  aidl::android::media::soundtrigger::RecognitionMode::USER_IDENTIFICATION,
  aidl::android::media::soundtrigger::RecognitionMode::USER_AUTHENTICATION,
  aidl::android::media::soundtrigger::RecognitionMode::GENERIC_TRIGGER,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
