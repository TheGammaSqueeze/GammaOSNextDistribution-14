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
namespace device {
enum class RequestTemplate : int32_t {
  PREVIEW = 1,
  STILL_CAPTURE = 2,
  VIDEO_RECORD = 3,
  VIDEO_SNAPSHOT = 4,
  ZERO_SHUTTER_LAG = 5,
  MANUAL = 6,
  VENDOR_TEMPLATE_START = 1073741824,
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
[[nodiscard]] static inline std::string toString(RequestTemplate val) {
  switch(val) {
  case RequestTemplate::PREVIEW:
    return "PREVIEW";
  case RequestTemplate::STILL_CAPTURE:
    return "STILL_CAPTURE";
  case RequestTemplate::VIDEO_RECORD:
    return "VIDEO_RECORD";
  case RequestTemplate::VIDEO_SNAPSHOT:
    return "VIDEO_SNAPSHOT";
  case RequestTemplate::ZERO_SHUTTER_LAG:
    return "ZERO_SHUTTER_LAG";
  case RequestTemplate::MANUAL:
    return "MANUAL";
  case RequestTemplate::VENDOR_TEMPLATE_START:
    return "VENDOR_TEMPLATE_START";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::device::RequestTemplate, 7> enum_values<aidl::android::hardware::camera::device::RequestTemplate> = {
  aidl::android::hardware::camera::device::RequestTemplate::PREVIEW,
  aidl::android::hardware::camera::device::RequestTemplate::STILL_CAPTURE,
  aidl::android::hardware::camera::device::RequestTemplate::VIDEO_RECORD,
  aidl::android::hardware::camera::device::RequestTemplate::VIDEO_SNAPSHOT,
  aidl::android::hardware::camera::device::RequestTemplate::ZERO_SHUTTER_LAG,
  aidl::android::hardware::camera::device::RequestTemplate::MANUAL,
  aidl::android::hardware::camera::device::RequestTemplate::VENDOR_TEMPLATE_START,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
