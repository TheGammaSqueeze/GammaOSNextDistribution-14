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
namespace camera {
namespace metadata {
enum class QuirksPartialResult : int32_t {
  ANDROID_QUIRKS_PARTIAL_RESULT_FINAL = 0,
  ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL = 1,
};

}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
[[nodiscard]] static inline std::string toString(QuirksPartialResult val) {
  switch(val) {
  case QuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL:
    return "ANDROID_QUIRKS_PARTIAL_RESULT_FINAL";
  case QuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL:
    return "ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::metadata::QuirksPartialResult, 2> enum_values<aidl::android::hardware::camera::metadata::QuirksPartialResult> = {
  aidl::android::hardware::camera::metadata::QuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL,
  aidl::android::hardware::camera::metadata::QuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
