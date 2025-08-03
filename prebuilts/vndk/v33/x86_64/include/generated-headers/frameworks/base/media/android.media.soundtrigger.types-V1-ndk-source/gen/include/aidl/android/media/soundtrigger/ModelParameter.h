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
enum class ModelParameter : int32_t {
  INVALID = -1,
  THRESHOLD_FACTOR = 0,
};

}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
[[nodiscard]] static inline std::string toString(ModelParameter val) {
  switch(val) {
  case ModelParameter::INVALID:
    return "INVALID";
  case ModelParameter::THRESHOLD_FACTOR:
    return "THRESHOLD_FACTOR";
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
constexpr inline std::array<aidl::android::media::soundtrigger::ModelParameter, 2> enum_values<aidl::android::media::soundtrigger::ModelParameter> = {
  aidl::android::media::soundtrigger::ModelParameter::INVALID,
  aidl::android::media::soundtrigger::ModelParameter::THRESHOLD_FACTOR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
