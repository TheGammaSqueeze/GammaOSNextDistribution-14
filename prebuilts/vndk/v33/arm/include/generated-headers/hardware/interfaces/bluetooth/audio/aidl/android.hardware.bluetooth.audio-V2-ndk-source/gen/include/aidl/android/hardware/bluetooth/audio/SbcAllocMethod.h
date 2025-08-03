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
enum class SbcAllocMethod : int8_t {
  ALLOC_MD_S = 0,
  ALLOC_MD_L = 1,
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
[[nodiscard]] static inline std::string toString(SbcAllocMethod val) {
  switch(val) {
  case SbcAllocMethod::ALLOC_MD_S:
    return "ALLOC_MD_S";
  case SbcAllocMethod::ALLOC_MD_L:
    return "ALLOC_MD_L";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::SbcAllocMethod, 2> enum_values<aidl::android::hardware::bluetooth::audio::SbcAllocMethod> = {
  aidl::android::hardware::bluetooth::audio::SbcAllocMethod::ALLOC_MD_S,
  aidl::android::hardware::bluetooth::audio::SbcAllocMethod::ALLOC_MD_L,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
