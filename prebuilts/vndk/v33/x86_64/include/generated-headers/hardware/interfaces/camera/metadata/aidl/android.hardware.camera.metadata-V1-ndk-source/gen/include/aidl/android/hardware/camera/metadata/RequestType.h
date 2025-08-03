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
enum class RequestType : int32_t {
  ANDROID_REQUEST_TYPE_CAPTURE = 0,
  ANDROID_REQUEST_TYPE_REPROCESS = 1,
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
[[nodiscard]] static inline std::string toString(RequestType val) {
  switch(val) {
  case RequestType::ANDROID_REQUEST_TYPE_CAPTURE:
    return "ANDROID_REQUEST_TYPE_CAPTURE";
  case RequestType::ANDROID_REQUEST_TYPE_REPROCESS:
    return "ANDROID_REQUEST_TYPE_REPROCESS";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::RequestType, 2> enum_values<aidl::android::hardware::camera::metadata::RequestType> = {
  aidl::android::hardware::camera::metadata::RequestType::ANDROID_REQUEST_TYPE_CAPTURE,
  aidl::android::hardware::camera::metadata::RequestType::ANDROID_REQUEST_TYPE_REPROCESS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
